package net.sf.jaer.hardwareinterface.serial;

import gnu.io.UnsupportedCommOperationException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Basic IO thread for serial eDVS communication.
 * 
 * @author Jorg Conradt
 */
public class IOThread extends Thread {
    
    private static Logger log=Logger.getLogger("IOThread");
     
    private volatile boolean threadRunning;
    private HWP_UART port = null;
    private boolean echoOutputOnScreen = false;
    private String rs232Input;

    public void sendCommand(String cmd) throws UnsupportedEncodingException, IOException {
        port.writeLn(cmd);
    }

    public IOThread(HWP_UART port) {
        this.port = port;
        threadRunning = true;
        this.setPriority(MAX_PRIORITY);
    }

    public void run() {
           try {
                port.setHardwareFlowControl(true);
            } catch (UnsupportedCommOperationException ex) {
                log.warning(ex.toString());
            }
        while (threadRunning) {
 
//            port.purgeInput();

            yield();

            try {
                rs232Input = port.getAllData();

                if (rs232Input != null) {

                    if (echoOutputOnScreen) {
                        System.out.print(rs232Input);
                    }

                    parseNewInput(rs232Input);

                }
            } catch (Exception e) { //
                System.out.println("Exception! " + e);
                e.printStackTrace();
            }

        }

    }

    public void terminate() {
        threadRunning = false;
    }
    private int pixelX, pixelY, pixelP;
    private int inputProcessingIndex = 0;
    private String specialData;

    public void parseNewInput(String input) {

        for (int n = 0; n < input.length(); n++) {

            int c = (int) (input.charAt(n));
            switch (inputProcessingIndex) {
                case 0:
                    if ((c & 0x80) == 0) {		// check if valid "high byte"
                        pixelX = c;
                        inputProcessingIndex = 1;
                    } else {
                        if ((c & 0xF0) == 0x80) {
                            inputProcessingIndex = 100 + (c & 0x0F) - 1;	// remember start of special data sequence
                            //System.out.println("Start Special Sequence of length : " +inputProcessingIndex);
                            specialData = "";
                        } else {
                            System.out.println("Data transfer hickup at " + System.currentTimeMillis());
                        }
                        // otherwise ignore and assume next is high byte
                        // System.out.println("flip error " + System.currentTimeMillis());
                    }
                    break;

                case 1:
                    pixelY = c & 0x7F;
                    pixelP = (c & 0x80) >> 7;
                    inputProcessingIndex = 0;

                    // TOBI: HERE we have the NEW EVENT  TODO
                    // processNewEvent(pixelX, pixelY, pixelP);
                    System.out.printf("new Event at %3d/%3d, Polarity %1d\n", pixelX, pixelY, pixelP);

                    break;

                case 100:
                    specialData = specialData + input.charAt(n);
                    inputProcessingIndex = 0;

                    // TOBI: Here we have special information, such as bias values, etc
                    // iv.processSpecialData(specialData);
                    System.out.printf("received special data: %s\n", specialData);

                    break;

                default:
                    specialData = specialData + input.charAt(n);
                    inputProcessingIndex--;
            }
        }
    }
}
