/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich
*/

package ch.unizh.ini.jaer.projects.opticalflow.usbinterface;

import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import ch.unizh.ini.jaer.projects.dspic.serial.*;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.GlobalOpticalFlowAnalyser;

import gnu.io.PortInUseException;
import java.util.Calendar;
import javax.swing.JPanel;


/**
 * Communicates with a <code>dsPIC33F</code> over a serial interface.
 * <br /><br />
 *
 * This hardware interface can be used in conjunction with the <code>MDC2Dv2</code>
 * test-board that features a <code>MDC2D</code> chip that is controlled by a
 * <code>dsPIC33FJ128MC804</code> and communicates with the computer via a <code>FTDI FT232RL</code>
 * (UART over USB) interface
 * <br /><br />
 * 
 * this class depends on <code>ch.unizh.ini.jaer.projects.dspic.serial.StreamCommand</code>
 * that should be distributed with the jAER project
 * <br /><br />
 * 
 * use the bias file <code>MDC2D_dsPIC.xml</code> with this platform (can be found in
 * <code>jAER/biasgenSettings</code> subdirectory of the jAER subversion trunk)
 * <br /><br />
 * 
 * in case something is not working, please try to debug the hardware with
 * the class <code>ch.unizh.ini.projects.jaer.dspic.serial.StreamCommandTest</code>,
 * read the javadoc of the class <code>ch.unizh.ini.projects.jaer.dspic.serial.StreamCommand</code>
 * and read the documentation (particularly the "usage" section in the appendix) of 
 * the semester project for which this interface was written; the report for
 * that project can be downloaded at 
 * <a href="http://n.ethz.ch/~andstein/MDC2Dsrinivasan.pdf">http://n.ethz.ch/~andstein/MDC2Dsrinivasan.pdf</a> 
 * <br /><br />
 *
 * @see ch.unizh.ini.jaer.projects.dspic.serial.StreamCommand
 * @author andstein
 */

public class dsPIC33F_COM_OpticalFlowHardwareInterface
        implements MotionChipInterface,StreamCommandListener {

    static final Logger log=Logger.getLogger(dsPIC33F_COM_OpticalFlowHardwareInterface.class.getName());
    
    /** set this boolean to true to enable some more diagnostic messages to be
        shown in the log; may be turned of to avoid too many noisy messages */
    public boolean debugging= true;

    // we use Chip2DMotion even though we don't provide all the data...
    private Chip2DMotion chip;
    
    // a 'version conflict' will be displayed until the firmware matches this
    // version in its major number
    public final static int PROTOCOL_VERSION= 6;

    // the first word (16bit) of a message identifies its contents
    public final static int MESSAGE_RESET=              0x0000;
    public final static int MESSAGE_BYTES=              0x0001; // legacy
    public final static int MESSAGE_WORDS=              0x0003; // streaming frames without motion vector
    public final static int MESSAGE_WORDS_DXDY=         0x0004; // streaming frames with motion vector
    public final static int MESSAGE_DXDY=               0x0005; // streaming only motion vector

    // some hardware settings
    public final static int DSPIC_FCY= 39613750; // clock speed of microcontroller
    public final static int DSPIC_BRGVAL= 3; // used to calculate the baudRate
    public final static float DSPIC_DAC_SCALEFACTOR = 1.25f; // to get the exact displayed voltage values...
    
    // this delta is used to calculate the new voltage biases
    // (the original biases were estimated for a aVDD of 3.33V, but this board
    //  features a TPS79328 that provides an aVDD at 3.28V)
    private final static float VDD_SHOULD_BE= 3.33f;
    private final static float VDD_IS= 3.28f;
    
    // specify two different delays for image capture
    protected int delay1_us = 20000;
    protected int delay2_us = 20000;

    
    private int[] ipotValues=null; // cache of pot values used for checking which ones to send
    private int[] vpotValues=null; // cache of pot values used for checking which ones to send
    static final int MAX_POTS=64;
    private int channel = MotionDataMDC2D.LMC1;
    private boolean onChipADC = false; // see setChannel()
    private int pd=1; // see setPowerdown()

    // states of opening procedure; see open() and setPortName()
    private boolean triedOpening= false;
    private boolean triedReset= false;
    private boolean verified= false;
    private boolean hardwareError= false;

    private StreamCommand serial;
    private dsPIC33F_COM_ConfigurationPanel configPanel;
    private String portName;

    // these are used for buffering the data between the different threads
    // see use of DataConverter for details
    private DataConverter converter;
    private Thread converterThread;
    private MotionData currentBuffer; // this buffer will be exchanged back and forth with the DataConverter thread
    private int sequenceNumber;
    
    // for removing fixed pattern noise
    boolean doRemoveFPN= false;
    
    // for collecting & analysing global motion calculation data
    protected GlobalOpticalFlowAnalyser analyser= null;
    protected boolean analysing= false;

    /**
     * creates a new hardware interface instance; will <i>not yet connect</i> to
     * the device
     * @see #open
     */
    public dsPIC33F_COM_OpticalFlowHardwareInterface()
    {
        chip= new MDC2D();
        
        serial= new StreamCommand(this,DSPIC_FCY/(16*(DSPIC_BRGVAL+1)));
        portName= null; // will be initialized in panel
        configPanel= new dsPIC33F_COM_ConfigurationPanel(this);
        configPanel.setStatus("not connected");

        // the actual thread will be created in startStreaming()
        currentBuffer= chip.getEmptyMotionData();
        sequenceNumber= 0;
        converter= new DataConverter(new Exchanger<MotionData>(), chip.getEmptyMotionData());
        converterThread= null;
    }

    
    /**
     * sends a command to the device if it is connected and verified
     * @param cmd command to send
     */
    public void sendCommand(String cmd)
    {
        if (serial.isConnected())
            // reset & version command can be sent on non-verified port
            if (cmd.equals("version") || cmd.equals("reset") || verified) {
                serial.sendCommand(cmd);
        
            if (debugging)
                System.err.format("%s : (%s)>\n", exactTimeString(), cmd);
            }
    }

    
    
    /**
     * is called upon completed opening and synchronizes device configuration
     * with class variables; also resets configPanel according to these values
     */
    public void initializeDevice()
    {
        if (!isOpen())
            return;
        
        // set on-chip bias generator, ADC
        sendCommand("onchip " + (onChipADC?"1":"0"));
        sendCommand("pd " + pd);
        configPanel.setOnChipBias(pd == 0);
        
        // set capturing delays
        sendDelayCommands();
        // set acquisition time :
        // timer ISR called every 100 cycles -> 2.5us/pixel -> 1ms/frame
        sendCommand("set timer_cycles 0064");
        
        // choose channel
        if (channel == MotionDataMDC2D.PHOTO)
            sendCommand("channel recep");
        if (channel == MotionDataMDC2D.LMC1)
            sendCommand("channel lmc1");
        if (channel == MotionDataMDC2D.LMC2)
            sendCommand("channel lmc2");

        // resend all biases
        ipotValues= null;
        vpotValues= null;
        try {
            sendConfiguration(chip.getBiasgen());
        } catch (HardwareInterfaceException ex) {
            log.warning("could not send configuration : " + ex);
        }
        
        // restart streaming if was streaming before
        if (isStreaming()) {
            stopStreaming();
            startStreaming();
        } else
            configPanel.setStreaming(false);
    }

    @Override
    public int getRawDataIndex(int bit) {
        if (bit != channel)
            log.warning("channel not currently selected");
        // we only got one channel...
        return 0;
    }

    @Override
    public void commandTimeout(String cmd) {
        
        // it is of course perfectly normal not to get an answer when the
        // device is reset
        if (cmd.equals("reset"))
            return;
        
        // if this happens all the time, you should eventually try to change
        // the timeout settings in the StreamCommand class...
        log.warning(exactTimeString() + " command time-out ("+cmd+")");
        
        // if a command hase timed out, something bad has probably happened
        // and we lost connection; setting verified to false will prevent
        // further commands to be sent and MotionViewer will later try to
        // re-open the device
        verified= false;
        
        if (cmd.equals("version"))
            configPanel.setStatus("no device attached");
    }

    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        pd= powerDown?1:0;
        if (!isOpen())
            return;
        
        sendCommand("pd " + pd); // e.g. '0' or '1'

        log.info("set pd=" + pd);
    }

    @Override
    public void setChannel(int bit, boolean onChip) throws HardwareInterfaceException {
        channel  = bit;
        onChipADC= onChip;
        
        if (isOpen()) {
            sendCommand("onchip " + (onChipADC?"1":"0"));

            if (channel == MotionDataMDC2D.PHOTO)
                sendCommand("channel recep");
            if (channel == MotionDataMDC2D.LMC1)
                sendCommand("channel lmc1");
            if (channel == MotionDataMDC2D.LMC2)
                sendCommand("channel lmc2");
        }
    }

    /**
     * sets the delay between frames according to this instances variables
     */
    protected void sendDelayCommands() {
        sendCommand("set main_us1 " + String.format("%04X",delay1_us) 
                     + " main_us2 " + String.format("%04X",delay2_us));
    }
            

    /**
     * call this to set the <code>dt</code> between two adjacent frames.
     * <ul>
     * <li>If the delay specified is longer than the time it takes to send
     *     a frame via the serial line, then the acquired frames will be
     *     taken at exactly the rate specified; this delay will be much more
     *     precise than the difference of the <code>getTimeCapturedMs()</code>
     *     because there is a variable delay in the emptying of the serial
     *     buffers...</li>
     * <li>In case the delay specified is shorter than the time necessary to
     *     transmit a frame, then the UART will dictate the delay between
     *     adjacent frame acquisitions</li>
     * </ul>
     * 
     * @param delayMs delay between frames ("<code>dt</code>") in milliseconds
     * @see ch.unizh.ini.jaer.projects.opticalflow.MotionData#getTimeCapturedMs
     */
    public void setDelayMs(int delayMs) {
        delay1_us= 1000 * delayMs;
        delay2_us= 1000 * delayMs;
        sendDelayCommands();
    }


    /**
     * all data gets piped through this class; coming in as <code>StreamCommandMessage</code>
     * on one side and getting out as <code>MotionData</code> on the other side
     * <br /><br />
     * 
     * the MotionData is <i>exchanged</i> in a blocking way so that the data converter
     * stops until its last unpacked frame is actually used by the motion viewer
     * and the motion viewer blocks in the exchange until the data converter
     * got a new message that could be converted into a frame
     * <br /><br />
     * 
     * this exchanging scheme ensures that if all the returned <code>MotionData</code>
     * are put into a queue, the last two of them are always references to
     * different objects (with different internal buffers) and can thereby
     * be used for motion calculation
     * <br /><br />
     * 
     * @see ch.unizh.ini.jaer.projects.dspic.serial.StreamCommandMessage
     * @see ch.unizh.ini.jaer.projects.opticalflow.MotionData
     */
    protected class DataConverter implements Runnable
    {
        private Exchanger<MotionData> dataOut;
        private LinkedList<StreamCommandMessage> messages;
        private MotionData frame;
        private boolean stopped;
        private int width;
        private int messagesPushed, messagesParsed;
        
        /** timeout for getting data from device -- <code>MotionViewer</code> calls close() when this occurs ! */
        private static final long DATA_TIMEOUT_MS=  20000;
        private static final long DATA_EXCHANGE_MS=     5; // if application is not reading data, just skip...
        
        /** messages can be stored in a queue; normally, the <code>MotionViewer</code> is slower
         * than the message stream and therefore messages are lost; use the <code>ConfigurationPanel</code>
         * to set a different timeout that results in messages being sent more
         * slowly if it is important to get every message (e.g. for comparing
         * motion calculations on firmware/computer side)
         * @see dsPIC33F_COM_ConfigurationPanel
         */
        public final static int MAX_QUEUED_MESSAGES= 2; 


        /**
         * creates a new instance of this class; the thread must later be
         * started via a call to <code>start()</code> of a <code>Thread</code>
         * instance based on this runnable
         * 
         * @param dst an <code>Exchanger</code> that will be used to swap the
         *      <code>MotionData</code> with the <code>MotionViewer</code>
         *      thread
         * @param motionBuffer an object that will be swapped with another
         *      MotionData in the .exchange() (and must therefore not be the
         *      same object that is used for the .exchange() call !)
         * @see java.lang.Thread
         */
        public DataConverter(Exchanger<MotionData> dst,MotionData motionBuffer)
        {
            dataOut= dst;
            messages= new LinkedList<StreamCommandMessage>();
            frame= motionBuffer;
            width= frame.chip.getSizeX();
            messagesPushed = messagesParsed = 0;

            stopped= false;
        }

        /**
         * call this method when the <code>MotionData</code> changes (could be the case
         * if that communicates with this hardware interface is changed...)
         * 
         * @param newFrame a new motion data to be used for swapping with 
         *      <code>MotionViewer</code> thread
         */
        public void setMotionData(MotionData newFrame) {
            frame= newFrame;
            width= frame.chip.getSizeX();
        }

        /**
         * indicates the thread to stop; <code>interrupt()</code> it after setting this flag
         * @see java.lang.Thread#interrupt
         */
        public void stop() {
            stopped= true;
        }

        /**
         * converts an unsigned 10bit value as returned by the microcontroller's
         * ADC to a float as used inside jAER
         */
        protected float convertPixelValue(int i)
        {
            return ((float) i) / 1024.f;
        }

        /**
         * converts a 16bit encoded motion value as calculated by the firmware
         * into a float in units of pixels/dt
         */
        protected float convertMotionValue(int i)
        {
            if ((i & 0x8000) == 0) {
                // positive number
                return ((float) i) / 32768f;
            } else {
                // negative number
                i ^= 0xFFFF;
                i += 1;
                return - ((float) i) / 32768f;
            }
        }

        /**
         * extracts the pixel value (as digitized by the microcontroller's
         * ADC) at a specific position form a message, depending on the messages
         * <code>type</code> field.
         * 
         * @see #convertPixelValue
         */
        protected int getFramePixel(StreamCommandMessage message, int x,int y)
        {
            if (message.getType() == dsPIC33F_COM_OpticalFlowHardwareInterface.MESSAGE_WORDS ||
                message.getType() == dsPIC33F_COM_OpticalFlowHardwareInterface.MESSAGE_WORDS_DXDY)
                return message.getUnsignedWordAt(y*width+x);
            else
                // show obvious debug image if no frames are streamed...
                return 1023 * ((y+x)%2);
        }
        
        /**
         * @return true if this message contains a global motion vector
         */
        protected boolean hasGlobalMotion(StreamCommandMessage msg)
        {
            return msg.getType()==MESSAGE_DXDY || msg.getType()==MESSAGE_WORDS_DXDY;
        }
        
        /**
         * @return true if this message should contain a global motion vector
         *      but some error occurred while calculating the motion vector value
         */
        protected boolean globalMotionError(StreamCommandMessage msg)
        {
            if (!hasGlobalMotion(msg))
                return false;
            if (msg.getType()==MESSAGE_DXDY)
                return msg.getUnsignedWordAt(  0)==0xFFFF;
            else
                return msg.getUnsignedWordAt(400)==0xFFFF;
        }
        
        /**
         * @return the error-code; 0x01 and 0x02 indicate <code>dx</code> respectively
         *      <code>dy</code> overflow (see firmware source code for signification 
         *      of the other errors that are much rarer)
         */
        protected int globalMotionErrorCode(StreamCommandMessage msg)
        {
            if (msg.getType()==MESSAGE_DXDY)
                return msg.getUnsignedWordAt(  1);
            else
                return msg.getUnsignedWordAt(401);
        }
        
        /**
         * @return the x component of the global motion vector contained in
         *      the specified message (in units of pixels/dt)
         */
        protected float globalMotionX(StreamCommandMessage msg)
        {
            if (msg.getType()==MESSAGE_DXDY)
                return msg.getSignedFloatAt(  0) *2;
            else
                return msg.getSignedFloatAt(400) *2;
        }

        /**
         * @return the y component of the global motion vector contained in
         *      the specified message (in units of pixels/dt)
         */
        protected float globalMotionY(StreamCommandMessage msg)
        {
            if (msg.getType()==MESSAGE_DXDY)
                return msg.getSignedFloatAt(  1) *2;
            else
                return msg.getSignedFloatAt(401) *2;
        }
        
        /**
         * @return sequence number of the frame contained in the message;
         *      -1 if the message contains to sequence number
         */
        protected int getSequenceNumber(StreamCommandMessage msg)
        {
            if (msg.getType()==MESSAGE_WORDS_DXDY)
                return msg.getUnsignedWordAt(402);
            if (msg.getType()==MESSAGE_DXDY)
                return msg.getUnsignedWordAt(2);
            return -1;
        }


        /**
         * blocks execution until a new message is pushed
         * 
         * @throws InterruptedException 
         */
        synchronized protected void waitForNextMessage()
                throws InterruptedException
        {
            while (messages.size() == 0)
                wait();
        }
        
        /**
         * this method should be implemented reasonably fast in order not to
         * loose any more message that are already lost by the relatively
         * infrequent polling by the <code>MotionViewer</code>
         * 
         * @return true if frame could be unpacked, false if an error occurred
         */
        protected boolean unpackFrame()
        {
            try
            {
                StreamCommandMessage message= messages.poll();
                // init motion data
                frame.setSequenceNumber(sequenceNumber++);
                // set firmware sequence number if one is provided
                if (getSequenceNumber(message) >0)
                    frame.setSequenceNumber(getSequenceNumber(message));
                // we use this method for consistency with the other hardware
                // interfaces; the actual delay is the one set via setDelayMs()
                // which is much more precise than the delay estimated here...
                frame.setTimeCapturedMs(System.currentTimeMillis());

                // set the raw pixel data -- for compatibility with the other
                // code we indicate presence of all kind of data that is merely
                // copied -- use setChannel() to change the transmitted pixel
                // data...
                frame.setContents( MotionData.PHOTO |
                                   MotionDataMDC2D.LMC1 |
                                   MotionDataMDC2D.LMC2 |
                                   MotionDataMDC2D.ON_CHIP_ADC );
                float[][][] rawData= frame.getRawDataPixel();
                
                // extract the transmitted channel into the first array...
                for(int y=0; y<chip.getSizeY(); y++)
                    for(int x=0; x<chip.getSizeX(); x++) 
                    {
                        int pixelValue= getFramePixel(message,x,y);
                        if (pixelValue > 1024)
                            // apparently, we lost our reading frame (the ADC
                            // of the dsPIC is 10bit only...)
                            return false;
                        
                        rawData[0][y][x]= convertPixelValue(pixelValue);
                    }
                
                // ...and simply copy this data into all the other channels
                for(int channel=1; channel<rawData.length; channel++)
                    for(int y=0; y<chip.getSizeY(); y++)
                        System.arraycopy(rawData[0][y], 0, rawData[channel][y], 0, chip.getSizeX());

                
                // we need at least 2 frames for motion...
                if (frame.getPastMotionData() != null && frame.getPastMotionData().length>0)
                {
                    // delay from this frame to last frame (ms)
                    long dt=frame.getTimeCapturedMs()-frame.getPastMotionData()[0].getTimeCapturedMs();
                    
                    // first calculate motion via MotionDataMDC2D
                    // (skip this step if no frames are being streamed)
                    if (message.getType() != MESSAGE_DXDY)
                        frame.collectMotionInfo();
                
                    // if the firmware sent some global motion values, also save them to the
                    // frame -- else set them to zero
                    if (hasGlobalMotion(message)) {
                        // in case the message contains global-x/global-y values, fill in these
                        
                        if (globalMotionError(message)) {
                            // this indicates an error
                            log.warning("firmware could not calculate Srinivasan : code=0x" + 
                                    Integer.toHexString(globalMotionErrorCode(message)));
                            
                            if (isAnalysing())
                                analyser.addErroneousCalculations(globalMotionErrorCode(message),frame);
                            
                            // set motion values to zero -> can easily be filtered out
                            frame.setGlobalX2(0);
                            frame.setGlobalY2(0);
                            
                        } else {
                            // no error : get values from message...
                            float dx= globalMotionX(message);
                            float dy= globalMotionY(message);
                            
                            // ... and fill into globalX2/Y2 (scaled same way as MotionDataMDC2D)
                            frame.setGlobalX2(dx/dt * MotionDataMDC2D.globalScaleFactor);
                            frame.setGlobalY2(dy/dt * MotionDataMDC2D.globalScaleFactor);

                        }

                        
                        if (isAnalysing())
                            analyser.analyseMotionData(frame);
                        
                    } else {
                       frame.setGlobalX2(0);
                       frame.setGlobalY2(0);
                    }
                    
                }

            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning("Out of Bounds while unpacking message : " + e.getMessage());
                return false;

            } catch (NoSuchElementException ex) {
                // undocumented exception ?
                log.warning("messages.poll() : " + ex.getMessage());
                messages.clear();
                return false;

            } catch (NullPointerException ex) {
                // this is common during debugging because past motion data gets lost
                log.warning("caught null pointer exception");
                return false;
            }
            
            return true;
                        
        }

        @Override
        public void run() {
            if (debugging) System.err.println("converter started");
            
            while(!stopped) {
                try
                {
                    // convert next message to frame
                    waitForNextMessage();
                    
                    if (unpackFrame())
                    {
                        messagesParsed++;
    //                    if (messagesParsed%300==0) System.err.println("messages : " + messagesPushed + " pushed -> " + messagesParsed + " parsed"); //DBG

                        // exchange buffer, make history
                        MotionData last= frame.clone(); // a *shallow* copy
                        frame= dataOut.exchange(frame); // switch frame with other thread
                        frame.setLastMotionData(last); // therefore, pixel buffers in pastMotionData *alternate*
                    } else {
                        log.warning("discarded bogus frame");
                        
                        if (isAnalysing())
                            analyser.addBogusFrame(frame);
                    }

                } catch(InterruptedException e) {
                    // interrupted by main thread after calling stop()
//                    log.info("interrupted : e="+e);
                }
            }

            if (debugging) System.err.println("converter stopped");
            stopped= false;
        }

        /**
         * push a new message to be converted; returns immediately
         * 
         * @param msg the next message to convert (will be copied)
         */
        synchronized public void pushMessage(StreamCommandMessage msg) {
            if (messages.size() > MAX_QUEUED_MESSAGES) {
                if (isAnalysing())
                    analyser.addLostMessage(getSequenceNumber(msg));
//                if (debugging)
//                    System.err.println("lost message : seq=" + getSequenceNumber(msg));
                return; // keep queue shallow...
            }
            
            StreamCommandMessage message= new StreamCommandMessage();
            message.copy(msg); // we HAVE to copy msg
            messages.add(message);
            messagesPushed++;
            notify();
        }

        /**
         * use this method to exchange a <code>MotionData</code> object from the motion
         * viewer loop
         * <br /><br />
         * 
         * it will block until new data arrived and is unpacked into the
         * provided <code>MotionData</code> object
         * 
         * @param data to exchange, must be a different object from the one
         *      exchanged last time
         * @return the object that was exchanged in the last call to this
         *      method
         * @throws InterruptedException
         * @throws TimeoutException indicates that the streaming has stopped
         */
        public MotionData exchangeMotionData(MotionData data)
                throws InterruptedException,TimeoutException {
            return dataOut.exchange(data,DATA_TIMEOUT_MS,TimeUnit.MILLISECONDS);
        }

    }

    /**
     * starts a recording using the class <code>GlobalOpticalFlowAnalyser</code>
     * 
     * @param name of the directory where to save the 
     * @param saveRate 2 out of <code>saveRate</code> frames will be stored with
     *      all their pixel values
     * @see GlobalOpticalFlowAnalyser
     */
    public void startAnalysis(String name,int saveRate) { 
        analyser= new GlobalOpticalFlowAnalyser(name,saveRate); 
        analysing= true;
    }
    /**
     * @return if <code>startAnalysis()</code> has already been called but
     *      not yet <code>stopAnalysis()</code>
     * @see #stopAnalysis
     * @see #startAnalysis
     */
    public boolean isAnalysing() { return analysing; }
    /**
     * stops a recording previously started via <code>startAnalysis()</code>
     * and writes data to disk -- depending on the amount of data recorded,
     * it might take some time before this method returns
     * 
     * @see #startAnalysis
     */
    public void  stopAnalysis() { 
        analyser.finish(); 
        analyser= null; 
        analysing= false;
    }
    
    
    /**
     * <b>NOT WORKING YET</b>
     */
    public void setRemoveFPN(boolean removeFPN)
    {
        // only set the flag
        doRemoveFPN= removeFPN;
        // it is critical that the FPN is removed "in the right place"
        // which means in the unpackFrame() method just before the motion
        // is calculated
    }



    @Override
    public MotionData getData() throws TimeoutException {
        try{
            currentBuffer= converter.exchangeMotionData(currentBuffer);
            return currentBuffer;
        }catch(InterruptedException e){
            return null;
        }catch(java.util.concurrent.TimeoutException to){
            throw new TimeoutException("timeout when exchanging MotionBuffer with ConverterThread");
        }
    }

    /**
     * list the system's serial ports to which the device might be connected
     * 
     * @return a system dependent list of names of serial ports available
     */
    public String[] getAvailablePortNames() {
        return serial.getPortNames();
    }

    /**
     * sets the serial communication port to use
     * <br /><br />
     *
     * because the different serial ports look the same to the hardware
     * interface (since it does not use a OS specific driver), the port to
     * which the device is attached needs be set manually; for this purpose,
     * the <code>dsPIC33F_COM_ConfigurationPanel</code> can be used
     * <br /><br />
     * 
     * if the port is changed and the hardware interface is currently connected
     * to a serial port, this connection is closed. a connection will be re-established
     * on the new port; this method should therefore not be called from within
     * a serial i/o thread.
     *
     * @param portName OS specific
     * @see dsPIC33F_COM_ConfigurationPanel
     * @see #open
     */
    public void setPortName(String portName) throws HardwareInterfaceException {
        if (portName == null) {
            if (this.portName == null)
                return;
        } else {
            if (portName.equals(this.portName))
                return;
        }
        
        configPanel.setStatus("");

        if (serial.isConnected())
            close();
        
        this.portName= portName; // prevents race condition while debugging .close()
        this.triedOpening= false;
        this.triedReset= false;
        this.hardwareError= false;
                
        log.info("portName set to " + portName);
        
        // auto-open
        if (portName != null)
            open();
    }
    public String getPortName() { return portName; }



    /**
     * this method is called by the Biasgen whenever something in the observables
     * (mainly IPots and VPots) has changed -- this class keeps track of
     * values that were already set and only transmits the delta of the
     * configuration
     * 
     * @param biasgen
     * @throws HardwareInterfaceException 
     * @see net.sf.jaer.biasgen.Biasgen
     */
    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {

        // TODO fix values of physical representation : we should calculate
        // values relative to (ca) 4630mV instead of 5000mV when using the
        // AD5391

        // cache DAC values
        if(vpotValues==null){
            vpotValues=new int[MAX_POTS];
            for(int i=0;i<vpotValues.length;i++){
                vpotValues[i]=-1; // make sure to send 1st time
            }
        }


        // send the DAC values
        
        for(int i=0;i<biasgen.getNumPots();i++)
        {
            VPot vpot=(VPot)biasgen.getPotArray().getPotByNumber(i);
            int chan=vpot.getChannel(); // DAC channel for pot
            int mv= (int) (1000*vpot.getVoltage());
            if (mv > 2500)
                mv += (int) (1000*(VDD_IS-VDD_SHOULD_BE)); // adjust pFETs for TPS79328

            // send the new value (in mV) only if it has changed
            if(vpotValues[chan] != mv) {
                vpotValues[chan] = mv;

                if (debugging)
                    System.err.println("setting DAC channel " + i + " ("+vpot.getName()+") to " + mv + " mV");
                
                sendCommand("DAC " + Integer.toHexString(i) +" "+ Integer.toHexString((int)(mv*DSPIC_DAC_SCALEFACTOR)));
            }
        }


        // cache on-chip bias currents
        if(ipotValues==null){
            ipotValues=new int[12];
            for(int i=0;i<ipotValues.length;i++){
                ipotValues[i]=-1; // init values to value that will generate a vendor request for it automatically.
            }
        }

        // iterate through on-chip bias currents
        Iterator<IPot> ipotsIt= (Iterator<IPot> ) ((MDC2D.MDC2DBiasgen) biasgen).getShiftRegisterIterator();
        boolean allEqual= true;
        byte[] allbin= new byte[36];
        for(int i=0; ipotsIt.hasNext(); i++)
        {
            IPot ipot= ipotsIt.next();
            int sr= ipot.getShiftRegisterNumber();

            if(ipotValues[sr]!=ipot.getBitValue()){
                // new value or not sent yet, send it
                ipotValues[sr]=ipot.getBitValue();
                allEqual= false;

                if (debugging)
                    System.err.println("set IPot '"+ipot.getName()+"' value "+ipot.getBitValue()+" ("+ipot.getPhysicalValue()+ipot.getPhysicalValueUnits()+") into SR pos " +sr);
            }

            byte[] bin =ipot.getBinaryRepresentation();
            // that's how it will be shifted into the chip's shift reg
            allbin[sr*3+0] = bin[0];
            allbin[sr*3+1] = bin[1];
            allbin[sr*3+2] = bin[2];
        }

        // send the new shift register content if at least one IPot has changed
        if (!allEqual) {
            StringBuilder allValues= new StringBuilder();
            for(int i=0; i<allbin.length; i++)
                allValues.append(String.format("%02X", allbin[i]));
            sendCommand("biases " + allValues.toString());
        }
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        log.warning("cannot flash configuration on this hardware");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return null; // each bias is handled independently for this kind of off-chip, channel-addressable DAC
    }

    @Override
    public String getTypeName() {
        return "dsPIC33F_COMx";
    }

    /**
     * call this to issue before closing the device; wait for commands to
     * finish before closing using <code>waitForCommands</code>
     * @see #close
     * @see ch.unizh.ini.jaer.projects.dspic.serial.StreamCommand#waitForCommands
     */
    protected void shutdownDevice() {
        sendCommand("stop");
    }
    
    /**
     * closes the serial port; <b>do not call from within serial i/o thread</b>
     */
    @Override
    public void close() {

        if (isOpen()) {
            shutdownDevice();
            try {
                serial.waitForCommands();
            } catch (InterruptedException ex) {
                // at least, we tried...
            }
        }

        if(!serial.isConnected()) {
            log.info("close : no connection !");
            return;
        }

        serial.close();
        configPanel.setStatus("not connected");

        log.info("interface closed");
    }
    
    /**
     * sends a reset command to the device if it is already opened; else,
     * tries to open the device and eventually sends a reset command once
     * its opened
     * 
     * @throws HardwareInterfaceException 
     * @see #open
     */
    public void doReset() throws HardwareInterfaceException {
        if (isOpen()) {
            sendCommand("reset");
        } else {
            triedReset= false;
            open();
        }
    }

    /**
     * tries to open the hardware interface; opening the interface consists
     * in two distinct steps : first, a serial connection over which commands
     * can be sent must be established; second, a <code>"version"</code> command
     * is issued to make sure that a valid device with a valid firmware is connected
     * to that serial port; if this second step does not succeed, this class also
     * tries to reset the device via a <code>"reset"</code> command; if a valid
     * answer to the <code>"version"</code> is recieved, the hardware interface
     * is considered open.
     * 
     * @throws HardwareInterfaceException 
     * @see #setPortName
     * @see #isOpen
     * @see #doReset
     */
    @Override
    public void open() throws HardwareInterfaceException {
        if (isOpen())
            return;

        // we cannot even try to open the device as long as we haven't got
        // a port name provided via the control panel
        // we do not try to open if we already tried to send a reset, either...
        if (portName == null || triedReset || hardwareError)
            // this will display "WAITING for device..." in MotionViewer until isOpen()==true
            return; 

        if (!serial.isConnected()) try {
            // try to establish a serial connection
            serial.connect(portName);
        } catch (PortInUseException ex) {
            configPanel.setStatus("port in use");
            log.warning("port " + portName + " is already in use by another application");
            hardwareError= true;
            return;
        } catch (Exception ex) {
            // this happens if a non-compatible port is selected (baud rate etc)
            configPanel.setStatus("I/O error");
            log.warning("I/O error while opening port " + portName + " : " + ex);
            hardwareError= true;
            return;
        }

        // serial connection is established at this point, bot not yet
        // verified
        verified= false;

        // there are two figures :
        if (!triedOpening) {
            // it's our first try so we simply send a version command
            // and parse the output asynchronously...
            sendCommand("version");
            configPanel.setStatus("verifying...");
            triedOpening= true;
        } else {
            // we already tried once and did not get a valid answer
            // this can be because the firmware crashed and needs a reset
            // the command ISR should be responding in any case
            sendCommand("reset");
            configPanel.setStatus("tried reset...");
            triedReset= true;
        }
    }

    /**
     * checks whether frames are currently streamed by this interface
     * @return whether frames are currently being streamed
     */
    public boolean isStreaming() {
        return converterThread != null && converterThread.isAlive();
    }

    /**
     * starts streaming via of frames via the interface
     * <br /><br />
     * this is automatically called at the device's initialization and also
     * sets the corresponding GUI element
     */
    public void startStreaming() {
        //TODO check whether this can be removed (should always be open!)
        if (!isOpen())
            try {
                open();
            } catch (HardwareInterfaceException ex) {
                log.warning("could not start streaming because could not open interface");
                return;
            }

        configPanel.setStreaming(true);

        if (isStreaming())
            return;

        converterThread= new Thread(converter, "converter");
        converterThread.start();

        sendCommand("stream frames srinivasan");
        sendCommand("start");
    }

    /**
     * stops streaming without closing the connection
     */
    public void stopStreaming() {
        configPanel.setStreaming(false);

        if (isOpen())
            sendCommand("stop");

        if (!isStreaming())
            return;

        try {
            converter.stop();
            converterThread.interrupt();
            converterThread.join();
            converterThread= null;
        } catch (InterruptedException ex) {
            log.warning("interrupted while waiting for converterThread");
        }
    }

    /**
     * this method indicates, whether the device is open <b>and ready</b> for use
     * (i.e. a valid device with a valid firmware ready to stream data and
     * receive commands)
     * 
     * @return whether a connection to the device is established;
     *      can return false even when a connection to the serial port is
     *      established, but no device could be found or an error has occured
     */
    @Override
    public boolean isOpen() {
        return serial.isConnected() && verified;
    }

    // gets called immediately after creation by MotionViewer
    @Override
    public void setChip(Chip2DMotion chip) {
//        log.info("setting chip");
        this.chip= chip;
        converter.setMotionData(chip.getEmptyMotionData());
    }

    @Override
    public JPanel getConfigPanel() {
        return configPanel;
    }
    
    public Chip2DMotion getChip() { return chip; }


    @Override
    public void setCaptureMode(int mask) throws HardwareInterfaceException {
        /**
         * we only transmit ONE CHANNEL -- setting the capture mode does not
         * influence this; call instead setChannel() if you want different
         * data to be transmitted over the interface
         */

        // but evtl the MotionData has changes its structure...
        converter.setMotionData(chip.getEmptyMotionData());
    }

    /**
     * for debugging where exact timing is relevant
     * @return human readable string of 'now' down to a ms resolution
     */
    private String exactTimeString() {
        Calendar x= Calendar.getInstance();
        return String.format("%02d:%02d:%02d.%03d",
                x.get(Calendar.HOUR_OF_DAY),x.get(Calendar.MINUTE),
                x.get(Calendar.SECOND),x.get(Calendar.MILLISECOND));
    }

    
    /**
     * @return -1 in case of an error; the major part of the version number
     *      (that determines backward-compatability) in all other cases
     */
    protected int getMajorVersion(String answer) {
        if (answer.charAt(0) == '!' ||
            !answer.contains("version"))
            return -1;
        
        try {
            String version= answer.substring(answer.lastIndexOf(' ') +1);
            String major= version.substring(0, version.indexOf('.'));
            return Integer.parseInt(major);
            
        } catch (IndexOutOfBoundsException ex) {
            // version string could not be parsed
            return -1;
        }
    }

    @Override
    public void answerReceived(String cmd,String answer) {

        if (debugging)
            System.err.format("%s : (%s)<%s\n", exactTimeString(), cmd, answer);
        
        configPanel.answerReceived(cmd, answer);

        if (cmd.equals("version"))
        {
            if (getMajorVersion(answer) == PROTOCOL_VERSION) {

                verified= true;
                configPanel.setStatus("connected");

                // once the connection is established and the device verified,
                // it still needs to be initialized with biases etc
                // this will also start streaming
                initializeDevice();

            } else {
                // clean up, indicate error
                configPanel.setStatus("version conflict : " + answer);
                log.warning("version conflict : " + answer);
            }
        }

        // this always indicates an error
        if (answer.charAt(0) == '!') {
            configPanel.setStatus("error : " + cmd);
            log.warning("command error (" + cmd + ") : " + answer);
        }
    }

    @Override
    public void unsyncedChar(char c) {
        // used for debugging the transmitted byte stream...
        return;
    }

    @Override
    public void messageReceived(StreamCommandMessage message) {

        if (message.getType() == MESSAGE_RESET)
        {
            // device start-up; -> re-initialize device...
            log.info("device reset message received");
            if (getMajorVersion(message.getAsString()) == PROTOCOL_VERSION) {

                // we're up and running
                verified= true;
                configPanel.setStatus("connected");
                // always initialize device after reset -- this synchronizes
                // the device's state with this instance's
                initializeDevice();
                
                // it is NOT allowed to try another reset after a successful one
//                triedReset= false;

            } else {
                // clean up, indicate error
                configPanel.setStatus("version conflict : " + message.getAsString());
                log.warning("version conflict : " + message.getAsString());
            }
            initializeDevice();
            return;
        }

        if (!isStreaming())
            return;

        if (message.getType() == MESSAGE_WORDS ||
            message.getType() == MESSAGE_WORDS_DXDY ||
            message.getType() == MESSAGE_DXDY )
            converter.pushMessage(message);
        else
            log.warning("unknown message type : " + message.getType());

    }

    @Override
    public void streamingError(int error,String msg) {
        
        if (isAnalysing())
            analyser.incrementStreamingErrors();

        
        if (error == StreamCommandListener.STREAMING_OUT_OF_SYNC) {
            // having lost some bytes is not that much of an issue,
            // StreamCommand will re-synchronize automatically...
            if (debugging)
                log.info("lost sync : " + msg);
        } else
            // this should not happen in normal operation
            log.warning("streaming error : " + msg);
    }

}
