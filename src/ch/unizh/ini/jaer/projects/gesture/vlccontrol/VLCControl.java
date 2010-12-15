/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.vlccontrol;

import java.io.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.net.telnet.*;
import org.apache.commons.net.telnet.TelnetClient;

/**
 *  Exposes control of VLC media player (videolan.org) from java.
 *
 * @author Tobi
 */
public class VLCControl extends TelnetClient implements Runnable, TelnetNotificationHandler {

    /** VLC should be started with as "vlc --rc-host=localhost:4444" */
    public static final int VLC_PORT = 4444;
    static final Logger log = Logger.getLogger("VLCControl");
    private BufferedReader reader = null;
    private OutputStreamWriter writer = null;
    CharBuffer cbuf = CharBuffer.allocate(1024);
    private static boolean addedHandlers = false;
    private static TelnetClient tc = null; // used to communicate among instances the active client

    public VLCControl() {
    }

    @Override
    public void disconnect() throws IOException {
        sendCommand("quit");
        super.disconnect();
    }

    public void connect() throws IOException {
        tc = this; // used by reader to get input stream
        try {
//            if (!addedHandlers) {
//                try {
//                    TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
//                    EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
//                    SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);
//                    tc.addOptionHandler(ttopt);
//                    tc.addOptionHandler(echoopt);
//                    tc.addOptionHandler(gaopt);
//                    addedHandlers = true;
//                } catch (InvalidTelnetOptionException e) {
//                    log.warning("Error registering option handlers: " + e.getMessage());
//                }
//            }
            tc.connect("localhost", VLC_PORT);
            Thread thread = new Thread(new VLCControl());
            thread.start();
            tc.registerNotifHandler(this);
            Runtime.getRuntime().addShutdownHook(new Thread() {

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
//            setSoTimeout(500);
//            reader = new BufferedReader(new InputStreamReader(getInputStream()));
            writer = (new OutputStreamWriter(getOutputStream()));
        } catch (IOException e) {
            log.warning("couldn't connect to VLC - you may need to start VLC with command line \"vlc --rc-host=localhost:4444\"");
            throw new IOException(e);
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
        log.info("Received " + command + " for option code " + option_code);
    }

    /** Sends a string and reads the response line.
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

     */
    public String sendCommand(String s) throws IOException {
        if (!isConnected()) {
//            throw new IOException("not connected yet");
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
//        writer.write(s);
//        writer.flush();
//        try {
//            Thread.sleep(20);
//        } catch (InterruptedException ex) {
//        }
//        cbuf.clear();
//        reader.read(cbuf);
//        String r = cbuf.flip().toString();
//        log.info("sent line: " + s + "read line: " + r);
        return "sent " + s;
    }

    /***
     * Reader thread.
     * Reads lines from the TelnetClient and echoes them
     * on the screen.
     ***/
    public void run() {
        InputStream instr = tc.getInputStream();

        byte[] buff = new byte[1024];
        int ret_read = 0;

        try {
            do {
                ret_read = instr.read(buff);
                if (ret_read > 0) {
                    log.info(new String(buff, 0, ret_read));
                }
            } while (ret_read >= 0);
        } catch (Exception e) {
            log.warning("Reader ending - Exception while reading socket:" + e.getMessage());
        }
    }
}
