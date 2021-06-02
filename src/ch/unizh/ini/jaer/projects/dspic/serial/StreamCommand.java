/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich

This file is part of dsPICserial.

dsPICserial is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dsPICserial is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dsPICserial.  If not, see <http://www.gnu.org/licenses/>.
*/



package ch.unizh.ini.jaer.projects.dspic.serial;


/**
 * this is the platform independent interface for accessing serial lines.
 * download rxtx from http://rxtx.qbang.org.
 */
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronously sends commands and receives a continuous message stream to a.
 * dsPIC33F microcontroller over a USB tunneled (via FT232R) serial communication line.
 * <br /><br />
 * Once the communication channel is opened + initialized, a stream of messages
 * is received from the microcontroller and forwarded to the registered
 * StreamCommandListener. The exact content of messages has to be interpreted
 * by the listening class. See <code>getInSync()</code> for documentation of the message
 * format (apart from messages containing answers to commands).
 * <br /><br />
 * In case the messages get out of sync (which can happen when data bytes get
 * lost in the host-sided receive buffer), this class tries to re-establish
 * the message stream with the help of the streamed message start marker and
 * message length. The StreamCommandListener is notified if this happens.
 * <br /><br />
 * Arbitrary ASCII commands can be sent at any moment, regardless of whether
 * the device is streaming or not. Because this design can in principle also be
 * used to stream data to the microchip (not implemented yet), the commands have
 * to be sent separately to guarantee that they are executed without delay. This
 * is done by asserting the ready-to-send (RTS) line on the computer side. The
 * microcontroller has an interrupt service routine that parses the command
 * and reacts accordingly.
 * <br /><br />
 * The answer to this command is buffered on the microcontroller side and will
 * be sent once the message being currently sent (e.g. a frame of pixels) is
 * transmitted. This results in a short delay. Therefore, the answers to commands
 * are also handled completely asynchronously. If no answer is sent, this results
 * in a time out of which the listener is also notified.
 * <br /><br />
 * Because this class communicates over a UART interface, there is absolutely
 * <b>no guarantee that the received data is correct</b>; actually it happens not that
 * un-frequently that some bits of the transmission stream are lost. This class
 * then simply re-establishes sync and notifies the listener of the occured
 * streaming error; it is entirely up to the application to perform content
 * integrity checking and error correction if this is desired.
 * <br /><br />
 * The firmware to be used in conjunction with this package can be found in the
 * jAER project under
 * <a href="https://sourceforge.net/p/jaer/code/HEAD/tree/devices/firmware/dsPICserial_MDC2D/">https://sourceforge.net/p/jaer/code/HEAD/tree/devices/firmware/dsPICserial_MDC2D/</a>
 * <br /><br /><br />
 *
 * <b>DEPENDENCIES</b>
 * </ul>
 *   <li> this class uses rxtx for platform independant serial communication
 *        which can be downloaded from http://rxtx.qbang.org </li>
 * </ul>
 * <br /><br />
 *
 * <b>KNOWN ISSUES</b>
 * <ul>
 *   <li> some versions of libSerial.dll (native interface of rxtx on windows)
 *        cannot set arbitrary baud rates (despite of what the documentation says)
 *        this software should be shipped with a version that works (tested in
 *        windows XP SP3) -- make sure that your java.exe process actually links
 *        to the provided .dll (use ProcessExplorer or something similar to verify
 *        this); during startup the following message should display
 *
 *     <pre>"Native lib Version = RXTX-2.1-7.mw1"</pre>
 *
 *     if the <b>correct</b> .dll was loaded </li>
 *
 *   <li> Linux does not support arbitrary baud rates; stick to the 115.2 kbaud
 *        and don't forget to adapt the speed in the firmware </li>
 *
 *   <li> The callbacks of <code>StreamCommandListener</code> are directly called
 *        called from within the serial-event handling thread; it is the listener's
 *        responsibility to immediately return and not to call <code>close()</code>
 *        or similar methods; ideally, a separate thread would do this job...</li>
 * </ul>

 *
 * @see StreamCommandListener
 * @see StreamCommandMessage
 * @see StreamCommandTest
 * @author andstein
 * @version 1.0
 */


public class StreamCommand implements SerialPortEventListener
{
    private static final Logger log= Logger.getLogger(StreamCommand.class.getName());

    /** this is the <code>type</code> of the message that contains answers to commands */
    public static final int MSG_ANSWER= 0x2020;
    /** maximum length of commands (size of the command parsing buffer in the firmware) */
    public static final int CMDBUFSIZE= 128;

    /** time (in milliseconds) after which a time-out is generated if a command
        has not resulted in an answer; this should be long enough for the largest
        message sent by the firmware to be transmitted through the serial line
        and all the host-side input buffers; a larger value will not result in
        any harm... */
    public static final long CMD_TIMEOUT= 500;
    /** time (in milliseconds) RTS should be asserted when a command is sent.
        if set too low commands might not get parsed properly and firmware
        buffers can get into undefined blocking states */
    public static final long RTS_TIMEOUT= 20;

    // keeping track of ongoing commands
    private String currentCommand;
    private ArrayList<String> cmdPipe;
    TimerTask cmdWatchdog, rtsWatchdog;

    // communication object and i/o streams
    private SerialPort port= null;
    private InputStream is= null;
    private OutputStream os= null;
    private boolean registered= false;

    // this flag indicates that no more serial events should be handled
    private boolean closing= false;

    // serial line settings -- only baudRate can/must be specified
    private int baudRate,dataBits,stopBits,paritiyFlags;

    // only one listener can be registered
    private StreamCommandListener listener;

    private boolean synced;
    // set while scanning for MARKER
    private boolean outOfSync;

    // there is only ONE message that will be used over and over
    private StreamCommandMessage message;
    // pos= -6,-5 : waiting for message marker
    // pos= -4,-3 : waiting for message size
    // pos= -2,-1 : waiting for message type
    private int pos,len,type;

    // keep track of received messages
    private ArrayList<Long> messageTimes;
    // keep track of commands sent / timeouts
    private int cmdsSent,cmdsTimeouts;


    private boolean debugOutOfSync= false;


    /**
     * Creates a new instance for serial communication.
     *
     * @param listener receiving all messages/answers/errors asynchronously;
     *      there can only be one registered listener
     * @param baudRate for the serial line
     *
     * @see #setBaudRate
     */
    public StreamCommand(StreamCommandListener listener,int baudRate)
    {
        this.listener= listener;

        this.baudRate= baudRate;
        log.info("serial communication settings : "
                +baudRate+" baud, 8 data- 1 stop-bit, no flow control");
        dataBits= SerialPort.DATABITS_8;
        stopBits= SerialPort.STOPBITS_1;
        paritiyFlags= SerialPort.PARITY_NONE;

        port= null;
        is= null;
        os= null;

        synced= true;
        currentCommand= null;
        cmdPipe= new ArrayList<String>();
        cmdWatchdog= null;
        rtsWatchdog= null;
        message= new StreamCommandMessage();

        messageTimes= new ArrayList<Long>();
        cmdsSent= 0;
        cmdsTimeouts= 0;
    }

    /**
     * gets a list of all available serial port names
     *
     * @return a platform dependent list of strings representing all the
     *      available serial ports -- it is the application's responsibility
     *      to identify the right port to which the device is actually connected
     */
    public String[] getPortNames()
    {
        ArrayList<String> ret= new ArrayList<String>();
        Enumeration<CommPortIdentifier> portsEnum= CommPortIdentifier.getPortIdentifiers();

        while(portsEnum.hasMoreElements())
        {
            CommPortIdentifier pid= portsEnum.nextElement();
            if (pid.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				ret.add(pid.getName());
			}
        }

        return ret.toArray(new String[ret.size()]);
    }



    /**
     * Connects to the specified port.
     *
     * This method will <b>not</b> check whether the device is actually set up
     * correctly; it only establishes a serial connection; the caller is
     * invited to initiate a "version" command and parse it's answer in order
     * to make sure the right device is connected.
     *
     * @param portName as returned by getPortNames()
     * @return boolean indicating whether the connection could be established
     *
     * @throws IOException among other things in case the <code>baudRate</code>
     *      is not supported
     * @throws TooManyListenersException in case the port is already open by
     *      another application
     *
     * @see #getPortNames()
     * @see #setBaudRate
     */
    public synchronized void connect(String portName)
            throws IOException,
            NoSuchPortException,
            UnsupportedCommOperationException,
            TooManyListenersException,
            PortInUseException
    {
        if (isConnected()) {
			close();
		}

        try
        {
            CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(portName);
            port = cpi.open("dsPICserial", 1000);

            port.setSerialPortParams(
                    baudRate,
                    dataBits,
                    stopBits,
                    paritiyFlags);
            port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

            is= port.getInputStream();
            os= port.getOutputStream();


            // register to collect serial events (data received etc)
            port.addEventListener(this);
            registered= true;
            port.notifyOnDataAvailable(true);
            port.notifyOnCTS(true); // not actually used

            // initialize message (reset pos etc)
            resetMessage();

//            log.info("connected to port " + portName);

        // clean-up then report exception
        } catch (IOException ex) {
            abortConnect();
            throw ex;
        } catch (NoSuchPortException ex) {
            abortConnect();
            throw ex;
        } catch (UnsupportedCommOperationException ex) {
            abortConnect();
            throw ex;
        } catch (TooManyListenersException ex) {
            abortConnect();
            throw ex;
        } catch (PortInUseException ex) {
            abortConnect();
            throw ex;
        } catch (UnsatisfiedLinkError ex) {
            //TODO discover how this strange exception gets generated
            //     -- cannot be caught ?
            //     -- occurs when another application is connected to port...
            log.warning("UnsatisfiedLinkError while connecting : " + ex.getMessage());
            abortConnect();
            throw new TooManyListenersException("[strange] unsatisfied linker error : " + ex +
                    " -- this is probably due to another java instance running and using the same port...");
        }
    }

    // gets called from within connect()
    // once the connection is established, close() should be called...
    private void abortConnect()
    {
        if (registered) {
			port.removeEventListener();
		}
        if (os != null) {
			try {
			    os.close();
			} catch (IOException ex) {
			    Logger.getLogger(StreamCommand.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
        if (is != null) {
			try {
			    is.close();
			} catch (IOException ex) {
			    Logger.getLogger(StreamCommand.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
        if (port != null) {
			port.close();
		}

        os= null;
        is= null;
        port= null;
        registered= false;
    }


    /**
     * set the baud rate used for communication; if the connection is
     * already established, the baud rate will be changed on the fly
     *
     * @param baudRate the new baud rate
     * @throws UnsupportedCommOperationException
     */
    public void setBaudRate(int baudRate) throws UnsupportedCommOperationException
    {
        this.baudRate= baudRate;
        if (isConnected()) {
			port.setSerialPortParams(
                    baudRate,
                    dataBits,
                    stopBits,
                    paritiyFlags);
		}
    }

    /**
     * @return the baud rate of this connection
     */
    public int getBaudRate() { return baudRate; }

    /**
     * @return true if connection is established
     * @see #connect
     * @see #close
     */
    public boolean isConnected() {
        return port != null;
    }

    @Override
    public void serialEvent(SerialPortEvent spe)
    {
        // we're only interested in streamed data
        if (spe.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try
            {
                if (synced) {
					// either synced data (messages), or...
                    continueMessage();
				}
				else {
					// ...some unsynced characters; see setSynced()
                    while(true)
                    {
                        int i= saveRead();
                        if (i == -1) {
							break;
						}
                        listener.unsyncedChar((char) i);
                    }
				}
            } catch (IOException ex) {
                listener.streamingError(StreamCommandListener.STREAMING_IO_EXCEPTION,
                        "IOException while receiving (see log) : " + ex);
            }
		}
    }


    /**
     * Tries to parse message marker / message length
     *
     * @return false if input stream returned no new bytes
     * @throws IOException
     */
    private boolean getInSync() throws IOException
    {
        int i= saveRead();
        if (i == -1) {
			return false;
		}

        byte b= (byte) i;

        switch(pos)
        {
            // every message starts with a marker
            case -6:
                if (b == (StreamCommandMessage.MARKER & 0xFF))       // LS byte first
                {
                    pos++;
                }
                else
                {
                    if (debugOutOfSync) {
						listener.unsyncedChar((char) b);
					}

                    if (!outOfSync) {
                        outOfSync= true;
                        listener.streamingError(StreamCommandListener.STREAMING_OUT_OF_SYNC,
                                "out of sync (pos="+pos+"); scanning for MARKER...");
                    }
                }
                break;
            case -5:
                if (b == ((StreamCommandMessage.MARKER>>8) & 0xFF))  // MS byte second
                {
                    if (outOfSync) {
//                        listener.streamingError("found 2nd byte; back in sync (?)");
                        outOfSync= false; // be optimistic
                    }
                    pos++;
                }
                else
                {
                    if (debugOutOfSync) {
						listener.unsyncedChar((char) b);
					}

                    if (!outOfSync) {
                        outOfSync= true;
                        listener.streamingError(StreamCommandListener.STREAMING_OUT_OF_SYNC,
                                "out of sync (pos="+pos+"); scanning for MARKER...");
                    }
                    pos= -4;
                }
                break;

            // length of message
            case -4:
                len= b&0xff; // LS byte first
                pos++;
                break;
            case -3:
                len|= (b&0xff)<<8;
                if (len > StreamCommandMessage.BUFSIZE) {
                    listener.streamingError(StreamCommandListener.MESSAGE_TOO_LARGE, len + " exceeds buffer size and is therefore ignored");
                    pos= -6;
                    outOfSync= true;
                    break;
                }
                message.setLength(len);
                pos++;
                break;

            // type of message
            case -2:
                type= b&0xff; // LS byte first
                pos++;
                break;
            case -1:
                type|= (b&0xff)<<8;
                message.setType(type);
                pos++;
                break;

            // (this should not happen)
            default:
                log.log(Level.WARNING,"invalid value : pos="+pos);
                break;
        }

        return true;
    }


    /**
     * performs a <code>.read(b,off,len)</code> on the input stream of the
     * serial connection; this wrap makes the <code>close()</code> operation
     * somewhat more likely to succeed...
     */
    protected int saveRead(byte[] b, int off, int len) throws IOException
    {
        if (closing) {
			return 0;
		}
        return is.read(b,off,len);
    }

    /**
     * performs a <code>.read()</code> on the input stream of the
     * serial connection; this wrap makes the <code>close()</code> operation
     * somewhat more likely to succeed...
     */
    protected int saveRead() throws IOException
    {
        if (closing) {
			return -1;
		}
        return is.read();
    }

    /**
     * reads as much data as available from the hostside receive buffers and
     * puts it into the current message; returns if no more data available or
     * if message has finished
     */
    private void continueMessage()
    {
        try {
            while(true)
            {
                // DO NOT USE InputStreamReader :
                // somehow returns 0xFFFD whenever bit7 is set...

                // first synchronize with message stream
                if (pos<0) {
                    // for simplicity, scanning of message header is done
                    // bytewise...
                    if (getInSync()) {
						// header & message length could be read -> continue
                        continue;
					}
					else {
						// no more chars, not yet in sync -> simply return
                        break;
					}
                }

                // full message received ?
                if (pos == message.getLength())
                {
                    // whole message received
                    messageTimes.add(new Long(System.currentTimeMillis()));

                    if (message.getType() == MSG_ANSWER)
                    {
                        // save for later use
                        String cmd= currentCommand;
                        String answer= message.getAsString();

                        // proceed with command pipe
                        terminateCurrentCommand();
                        sendNextCommand();

                        // notify listener
                        listener.answerReceived(cmd, answer);
                    }
					else {
						// all other messages are processed by listener...
                        listener.messageReceived(message);
					}


                    // prepare for next message
                    resetMessage();
                }

                // transfer chunk of data into message buffer
                int n= saveRead(message.getBuffer(),pos,message.getLength()-pos);
                if (n<=0) {
					break;
				}
                pos+=n;
            }
        } catch (IOException ex) {
//            log.log(Level.WARNING, null, ex);
            listener.streamingError(StreamCommandListener.STREAMING_IO_EXCEPTION,
                    "IOException while receiving (see log)");
        }
    }

    protected synchronized void terminateCurrentCommand()
    {
        // clear current command
        if (cmdWatchdog != null) {
			cmdWatchdog.cancel();
		}
        cmdWatchdog= null;
        currentCommand= null;

        // notify waitForCommands()
        notify();
    }

    /**
     * resets internal counters; call before a new message should be received
     */
    public void resetMessage()
    {
        pos=-6;
        message.setLength(0);
        outOfSync= false;   // be optimistic...
        //? evtl discard oldest frame times
    }

    /**
     * waits for all commands in command queue to be sent to the device
     * before returning to the caller; use this command if you want to make
     * sure that commands are sent before closing the connection
     *
     * @throws InterruptedException
     */
    public synchronized void waitForCommands()
            throws InterruptedException
    {
        while ((cmdPipe.size() > 0) || (currentCommand != null)) {
			wait();
		}
    }


    /**
     * close the serial connection to the device. does not finish sending
     * the commands currently queued.
     *
     * <br /><br />
     * <b>KNOWN ISSUES</b>
     * <ul>
     *  <li>    sometimes stops the execution flow in <code>removeEventListener()</code>... </li>
     * </ul>
     *
     * @see #waitForCommands
     */
    public synchronized void close()
    {
        if (!isConnected()) {
			return;
		}

        closing= true;

        if (cmdWatchdog != null) {
            cmdWatchdog.cancel();
            cmdWatchdog= null;
        }

        if (currentCommand != null) {
            log.warning("close() called while waiting for answer (cmd="+currentCommand+")");
            currentCommand= null;
        }

        if (! cmdPipe.isEmpty()) {
            log.warning("close() called while still commands scheduled (num=" + cmdPipe.size() + ")");
            cmdPipe.clear();
        }

        try {
            is.close();
            os.close();
        } catch (IOException ex) {
            log.warning("could not close streams : " + ex);
        }

        try {
            port.removeEventListener();
        } catch (NullPointerException ex) {}

        registered= false;
        port.close();

        // setting port = null indicates closed state
        is = null;
        os = null;
        port = null;

        closing= false;

//        log.log(Level.INFO, "closed SerialPort");
    }

    /**
     * this method is called in case no answer was received in a suitable
     * delay after the command has been issued
     */
    protected void commandTimeout()
    {
        String cmd= currentCommand;

        terminateCurrentCommand();
        sendNextCommand(); // be optimistic
        listener.commandTimeout(cmd);

        // update stats
        cmdsTimeouts+= 1;
    }


    /**
     * send a command to the device; in case a command is being sent (i.e.
     * the class is waiting for an answer for an already sent command) the
     * command is queued and sent as soon as the answer to the previous command
     * has been received. the registered listener will be notified asynchronously
     * on the result of the command
     *
     * @param cmd command to be sent; must not exceed <code>CMDBUFSIZE</code>
     *      a "multiple command" can be sent : simply separate different commands
     *      by newlines; by design, no newlines can be contained <b>in</b> the
     *      command itself
     *
     * @see #CMDBUFSIZE
     * @see StreamCommandListener#answerReceived
     * @see StreamCommandListener#commandTimeout
     */
    public synchronized void sendCommand(String cmd)
    {
        if (port == null) {
            log.warning("could not send command ("+cmd+") on closed port");
            return;
        }

        if (currentCommand != null)
        {
            cmdPipe.add(cmd);
            return;
        }


        if (cmd.contains("\n"))
        {
            for(String x : cmd.split("\n")) {
				sendCommand(x);
			}
            return;
        }

        if (cmd.length() > CMDBUFSIZE) {
			log.warning("command exceeds buffer-size ("+CMDBUFSIZE+")!");
		}

        // this indicates that we are waiting for an answer
        currentCommand= cmd;

        // assert RTS to indicate that we want to send a command
        // the dsPIC will immediately enter the ISR and execute the
        // command
        port.setRTS(true);

        // actually send the command
        PrintWriter pw= new PrintWriter(os);
        pw.print(cmd + "\n");
        pw.flush();

        // this should *not* happen...
        if (cmdWatchdog != null) {
			log.warning("setting new watchdog while old watchdog still running; cmd="+cmd);
		}

        // set the timeout for receiving an answer
        cmdWatchdog= new SendCommandTimer(this, cmd);
        new Timer("cmdWachdog (" + cmd + ")").schedule(cmdWatchdog, CMD_TIMEOUT);

        // end command-mode after some ms
        if (rtsWatchdog != null) {
			rtsWatchdog.cancel();
		}

        rtsWatchdog= new TimerTask() {
            @Override
            public void run() {
                rtsWatchdog = null;
                port.setRTS(false);
            }
        };
        new Timer("rtsWatchdog").schedule(rtsWatchdog, RTS_TIMEOUT);

        // update stats
        cmdsSent+= 1;

    }


    public synchronized void sendNextCommand()
    {
        if ((currentCommand == null) && (cmdPipe.size() > 0)) {
			sendCommand(cmdPipe.remove(0));
		}
    }

    /**
     * stops watchdog fur current command and cancels all further scheduled
     * commands.
     */
    public synchronized void clearCommandPipe()
    {
        terminateCurrentCommand();
        cmdPipe.clear();
    }

    /**
     * @return true if messages are being received; false if unsynched
     *      characters are being received
     * @see #setSynced
     */
    public boolean getSynced() {
        return synced;
    }

    /**
     * normally, this class is used in <i>synced</i> mode where all incoming
     * bytes are assembled together to form messages that are then relayed to
     * the registered <code>StreamCommandListener</code>; for debugging purposes
     * it might be handy to "directly see" the received bytes, one by one
     *
     * @param b true (=default) for receiving messages (via <code>messageReceived</code>)
     *          false (for debugging) to get received bytes one by one (via
     *          <code>unsyncedChar</code>
     *
     * @see StreamCommandListener#messageReceived
     * @see StreamCommandListener#unsyncedChar
     */
    public void setSynced(boolean b) {
        synced= b;
    }

    /**
     * @see #setDebug
     */
    public boolean getDebug() {
        return debugOutOfSync;
    }
    /**
     * when this flag is set, the registered <code>StreamCommandListener</code>
     * gets called (via <code>unsyncedChar</code>) when out-of-sync bytes are
     * received
     *
     * @param flag default is false
     * @see StreamCommandListener#unsyncedChar
     */
    public void setDebug(boolean flag) {
        debugOutOfSync= flag;
    }


    /**
     * calculates the how many messages are received per second
     *
     * @return the number of messages received in the last second
     */
    public double getMps()
    {
        //TODO make more accurate
        long now= System.currentTimeMillis();
        int n= messageTimes.size();

        for(int i=n-1; i>=0; i--)
        {
            if (messageTimes.get(i).longValue() < (now-1000)) {
				return (double) n-1  -i;
			}

        }
        // no messages received
        return 0.;
    }


    /**
     * @return some simple diagnostic information
     */
    public String dump()
    {
        return String.format("pos=%d; message=%s; currentCommand=%s; cmdPipe.size=%d; cmdsSent=%d; cmdsTimeouts=%d",
                pos,message.toString(),currentCommand,cmdPipe.size(),cmdsSent,cmdsTimeouts);
    }



    /**
     * Timer task for command sending. The sole purpose of this class is to
     * generate a timeout if no answer is received.
     */
    private class SendCommandTimer extends TimerTask
    {
        private StreamCommand streamer;
        private String cmd;

        /**
         * @param streamer
         * @param cmd
         * @param cmdSent indicates with method should be called !
         */
        public SendCommandTimer(StreamCommand streamer,String cmd)
        {
            super();
            this.streamer= streamer;
            this.cmd= cmd;
        }

        @Override
        public void run()
        {
            streamer.commandTimeout();
        }
    }

}
