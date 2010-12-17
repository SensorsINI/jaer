/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.vlccontrol;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.net.telnet.*;
import org.apache.commons.net.telnet.TelnetClient;

/**
 *  Exposes control of VLC media player (videolan.org) from java. VLC must be set up to open a telnet control on localhost:4444.
 * <p>
 * For VLC 1.1.5, use the following setup to expose the remote control (rc) interrface for telnet control:
 * <p>
 * This setting is in VLC Tools/Preferences/Show settings (All)/Interface/Main interfaces. Select the "Remote Control Interface" and replace "oldrc" with "rc" in the text field.
 * In VLC Tools/Preferences/Show settings (All)/Interface/Main interfaces/RC/TCP command input, put the string "localhost:4444" in the text field.
 *
 * @author Tobi
 */
public class VLCControl extends TelnetClient implements Runnable, TelnetNotificationHandler {

    /** VLC should be started with as "vlc --rc-host=localhost:4444" */
    public static final int VLC_PORT = 4444;
    static final Logger log = Logger.getLogger("VLCControl");
    private CharBuffer cbuf = CharBuffer.allocate(1024);
    private static VLCControl staticInstance = null; // used to communicate among instances the active client
    private PropertyChangeSupport support = new PropertyChangeSupport(this);  // listeners get informed by output from VLC strings

    public VLCControl() {
    }

    @Override
    public void disconnect() throws IOException {
        sendCommand("quit");
        super.disconnect();
    }

    public void connect() throws IOException {
        if(isConnected()) {
            log.warning("already connected");
            return;
        }
        staticInstance = this; // used by reader to get input stream
        try {
            staticInstance.connect("localhost", VLC_PORT);
            Thread thread = new Thread(new VLCControl()); // starts the thread to get the text sent back from VLC
            thread.start();
            staticInstance.registerNotifHandler(this); // notifications call back to logger
            Runtime.getRuntime().addShutdownHook(new Thread() { // shutdown hook here makes sure to disconnect cleanly, as long as we are not terminated

                @Override
                public void run() {
                    try {
                        if (isConnected()) {
                            disconnect();
                        }
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                    }
                }
            });
        } catch (IOException e) {
            log.warning("couldn't connect to VLC - you may need to start VLC with command line \"vlc --rc-host=localhost:4444\"");
            throw new IOException(e);
        }
    }

    /** Sends a string command.  Commands do not need to be terminated with a newline.
     * Sending too many commands too quickly can cause VLC to crash.
    <p>
    <pre>
    +----[ Remote control commands ]
    | add XYZ  . . . . . . . . . . . . . . . . . . . . add XYZ to playlist
    | enqueue XYZ  . . . . . . . . . . . . . . . . . queue XYZ to playlist
    | playlist . . . . . . . . . . . . . .show items currently in playlist
    | search [string]  . .  search for items in playlist (or reset search)
    | sort key . . . . . . . . . . . . . . . . . . . . . sort the playlist
    | sd [sd]  . . . . . . . . . . . . . show services discovery or toggle
    | play . . . . . . . . . . . . . . . . . . . . . . . . . . play stream
    | stop . . . . . . . . . . . . . . . . . . . . . . . . . . stop stream
    | next . . . . . . . . . . . . . . . . . . . . . .  next playlist item
    | prev . . . . . . . . . . . . . . . . . . . .  previous playlist item
    | goto . . . . . . . . . . . . . . . . . . . . . .  goto item at index
    | repeat [on|off]  . . . . . . . . . . . . . .  toggle playlist repeat
    | loop [on|off]  . . . . . . . . . . . . . . . .  toggle playlist loop
    | random [on|off]  . . . . . . . . . . . . . .  toggle playlist random
    | clear  . . . . . . . . . . . . . . . . . . . . . .clear the playlist
    | status . . . . . . . . . . . . . . . . . . . current playlist status
    | title [X]  . . . . . . . . . . . . . . set/get title in current item
    | title_n  . . . . . . . . . . . . . . . .  next title in current item
    | title_p  . . . . . . . . . . . . . .  previous title in current item
    | chapter [X]  . . . . . . . . . . . . set/get chapter in current item
    | chapter_n  . . . . . . . . . . . . . .  next chapter in current item
    | chapter_p  . . . . . . . . . . . .  previous chapter in current item
    |
    | seek X . . . . . . . . . . . seek in seconds, for instance `seek 12'
    | pause  . . . . . . . . . . . . . . . . . . . . . . . .  toggle pause
    | fastforward  . . . . . . . . . . . . . . . . . . set to maximum rate
    | rewind . . . . . . . . . . . . . . . . . . . . . set to minimum rate
    | faster . . . . . . . . . . . . . . . . . .  faster playing of stream
    | slower . . . . . . . . . . . . . . . . . .  slower playing of stream
    | normal . . . . . . . . . . . . . . . . . .  normal playing of stream
    | rate [playback rate] . . . . . . . . . .  set playback rate to value
    | frame  . . . . . . . . . . . . . . . . . . . . . play frame by frame
    | fullscreen, f, F [on|off]  . . . . . . . . . . . . toggle fullscreen
    | info . . . . . . . . . . . . . .information about the current stream
    | stats  . . . . . . . . . . . . . . . .  show statistical information
    | get_time . . . . . . . . . .seconds elapsed since stream's beginning
    | is_playing . . . . . . . . . . . .  1 if a stream plays, 0 otherwise
    | get_title  . . . . . . . . . . . . . the title of the current stream
    | get_length . . . . . . . . . . . .  the length of the current stream
    |
    | volume [X] . . . . . . . . . . . . . . . . . .  set/get audio volume
    | volup [X]  . . . . . . . . . . . . . . . .raise audio volume X steps
    | voldown [X]  . . . . . . . . . . . . . .  lower audio volume X steps
    | adev [X] . . . . . . . . . . . . . . . . . . . .set/get audio device
    | achan [X]  . . . . . . . . . . . . . . . . . .set/get audio channels
    | atrack [X] . . . . . . . . . . . . . . . . . . . set/get audio track
    | vtrack [X] . . . . . . . . . . . . . . . . . . . set/get video track
    | vratio [X] . . . . . . . . . . . . . . . .set/get video aspect ratio
    | vcrop, crop [X]  . . . . . . . . . . . . . . . .  set/get video crop
    | vzoom, zoom [X]  . . . . . . . . . . . . . . . .  set/get video zoom
    | snapshot . . . . . . . . . . . . . . . . . . . . take video snapshot
    | strack [X] . . . . . . . . . . . . . . . . . set/get subtitles track
    | hotkey, key [hotkey name]  . . . . . . . . . . simulate hotkey press
    | menu [on|off|up|down|left|right|select]  . . . . . . . . . .use menu
    |
    | set [var [value]]  . . . . . . . . . . . . . . . . . set/get env var
    | save_env . . . . . . . . . . . .  save env vars (for future clients)
    | alias [cmd]  . . . . . . . . . . . . . . . . set/get command aliases
    | description  . . . . . . . . . . . . . . . . . .describe this module
    | license  . . . . . . . . . . . . . . . . print VLC's license message
    | help, ? [pattern]  . . . . . . . . . . . . . . . . . .a help message
    | longhelp [pattern] . . . . . . . . . . . . . . a longer help message
    | logout . . . . . . . . . . . . . .  exit (if in a socket connection)
    | quit . . . . . . . .  quit VLC (or logout if in a socket connection)
    | shutdown . . . . . . . . . . . . . . . . . . . . . . . .shutdown VLC
    +----[ end of help ]
    </pre>
     * @param s command to be sent, e.g. "pause", "seek 300" (seeks to 300 seconds)
     * @return the same command string

     */
    public String sendCommand(String s) throws IOException {
        if (!isConnected()) {
            connect();
        }
        if (s == null) {
            return null;
        }
        if (!s.endsWith("\n")) {
            s = s + "\n";
        }
        getOutputStream().write(s.getBytes());
        getOutputStream().flush();
        return s;
    }

    /** Sends a string, and returns the response from VLC. This is an expensive thread-creating blocking call that should not be used in time-critical code.
     * @param s
     * @return
     * @throws IOException
     */
    public String query(String s) throws IOException {
        class Listen extends Thread implements PropertyChangeListener {

            Listen() {
                setName("VLCQueryListener");
            }
            String s = null;

            @Override
            public void run() {
                try {
                    synchronized(this){wait();}
                } catch (InterruptedException ex) {
                    log.info("done waiting");
                }
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() != null) {
                    s = (String) evt.getNewValue();
                }
                synchronized(this){notify();}
            }
        }

        if (!isConnected()) {
            connect(); // make sure we have the static instance
        }
        final Listen listen = new Listen();
        staticInstance.getSupport().addPropertyChangeListener(CLIENT_MESSAGE, listen);

        listen.start();
        sendCommand(s);
        try {
            synchronized (listen) {
                listen.wait();
            }
        } catch (InterruptedException e) {
            return null;
        }finally{
            staticInstance.getSupport().removePropertyChangeListener(listen);
        }
        return listen.s;
    }
    public static String PAUSE = "pause", PLAY = "play", STOP = "stop", NEXT = "next", PREV = "prev", VOLUP = "volup 1", VOLDOWN = "voldown 1";
    public static final String CLIENT_MESSAGE = "ClientMessage";

    /***
     * Reader thread.
     * Reads lines from the TelnetClient and echoes them
     * on the logger.
     * PropertyChangeListeners are called with CLIENT_MESSAGE and String sent from VLC.
     ***/
    @Override
    public void run() {
        InputStream instr = staticInstance.getInputStream();

        byte[] buff = new byte[1024];
        int ret_read = 0;

        try {
            do {
                ret_read = instr.read(buff);
                if (ret_read > 0) {
                    String s = new String(buff, 0, ret_read);
                    log.info(s);
                    staticInstance.getSupport().firePropertyChange(CLIENT_MESSAGE, null, s); // listener on static instance that actually is connected gets the message
                }
            } while (ret_read >= 0);
        } catch (Exception e) {
            log.log(Level.WARNING, "Reader ending - Exception while reading socket:{0}", e.getMessage());
        }
    }

    /***
     * Callback method called when TelnetClient receives an option
     * negotiation command.
     * <p>
     * @param negotiation_code - type of negotiation command received
     * (RECEIVED_DO, RECEIVED_DONT, RECEIVED_WILL, RECEIVED_WONT)
     * <p>
     * @param option_code - code of the option negotiated
     * <p>
     ***/
    @Override
    public void receivedNegotiation(int negotiation_code, int option_code) {
        String command = null;
        if (negotiation_code == TelnetNotificationHandler.RECEIVED_DO) {
            command = "DO";
        } else if (negotiation_code == TelnetNotificationHandler.RECEIVED_DONT) {
            command = "DONT";
        } else if (negotiation_code == TelnetNotificationHandler.RECEIVED_WILL) {
            command = "WILL";
        } else if (negotiation_code == TelnetNotificationHandler.RECEIVED_WONT) {
            command = "WONT";
        }
        log.log(Level.INFO, "Received {0} for option code {1}", new Object[]{command, option_code});
    }

    /**
     * @return the support. Listeners can get the stuff sent back from VLC with CLIENT_MESSAGE events.
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }
}
