package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;



import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.Timer;


/**
 * Controls the Pan-Tilt-Unit PTU-D46 via RS-232 and logs the responses.
 * 
 * @author Holger
 */
public class PanTiltControlPTU extends PanTiltControl {

    public OutputStream out;
    public InputStream in;

    public PanTiltControlPTU() {
        super();
    }

    public void setLogResponses(boolean aLogResponses) {
        SerialReader.logResponses = aLogResponses;
        if(!aLogResponses) {
            log.info("log responses turned off");
        }
    }
     
    public void setWaitPeriod(int WaitPeriod) {
        PanTiltControlPTU.waitPeriod = WaitPeriod;
        SerialReader.timer.stop();
    }

    void connect(String destination) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(destination);
        if (portIdentifier.isCurrentlyOwned()) {
            log.warning("Error: Port for Pan-Tilt-Communication is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
                connected = true;
                log.info("Connected to Pan-Tilt-Unit!");
            } else {
                log.warning("Error: Cannot connect to Pan-Tilt-Unit!");
            }
        }
    }

    @Override
    void setPanPos(double panPos) {
        String strPanTilt = "PP" + (int)panPos + "\nA\n";
        super.panPos = panPos;
        try {
            this.out.write(strPanTilt.getBytes());
            PanTiltControlPTU.moving = true;
            PanTiltControlPTU.wasMoving = true;
        } catch (IOException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
    }

    @Override
    void setTiltPos(double tiltPos) {
        String strPanTilt = "TP" + (int)tiltPos + "\nA\n";
        super.tiltPos = tiltPos;
        try {
            this.out.write(strPanTilt.getBytes());
            PanTiltControlPTU.moving = true;
            PanTiltControlPTU.wasMoving = true;
        } catch (IOException ex) {
            log.warning("In setTiltPos(position) caught IOexception " + ex);
        }
    }

    /**
     *
     */
    public static class SerialReader implements gnu.io.SerialPortEventListener
    {
        private InputStream in;
        private byte[] buffer = new byte[1024];
        private static volatile boolean logResponses = false;
        private boolean waitingForStarResponse = false;
        private static Logger loggerResponses = Logger.getLogger("Pan-Tilt-Responses");
        static ActionListener taskPerformer = new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                timer.stop();
                if (logResponses) {
                    loggerResponses.info("TimerAction: Wait done! Restart Filters!");
                }
                if (panTiltListener != null) {
                        panTiltListener.panTiltAction(new PanTiltEvent(this, 0));
                    }
                wasMoving = false;
            }
        };
        private static Timer timer = new Timer(waitPeriod, taskPerformer);

        /**
         *
         * @param in
         */
        public SerialReader(InputStream in) {
            this.in = in;
        }

        /**
         *
         * @param arg0
         */
        public void serialEvent(gnu.io.SerialPortEvent arg0) {
            int data;
            String response = "";
            try {
                int len = 0;
                while ((data = in.read()) > -1) {
                    if (data == '\n') {
                        break;
                    }
                    buffer[len++] = (byte) data;
                }
                response = new String(buffer, 0, len);
                if (logResponses) {
                    loggerResponses.info(response);
                }
                if (waitingForStarResponse == true && response.contains("*")) {
                    timer.start();
                    moving = false;
                    wasMoving = true;
                    waitingForStarResponse = false;
                    if (logResponses) {
                        loggerResponses.info("Movement is done!");
                    }
                    if (panTiltListener != null) {
                        panTiltListener.panTiltAction(new PanTiltEvent(this, 2));
                    }
                } else if (response.equalsIgnoreCase("A")) {
                    waitingForStarResponse = true;
                    if (logResponses) {
                        loggerResponses.info("Is Moving!");
                    }
                    if (panTiltListener != null) {
                        panTiltListener.panTiltAction(new PanTiltEvent(this, 1));
                    }
                }
//                if (waitingForQuery)
//                {
//                    int indMaxTilt=response.lastIndexOf("Maximum Tilt position is ");
//                    if (indMaxTilt!=-1) {
//                        queryMaxTilt=response.substring(indMaxTilt+1, response.length());
//                    }
//                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    public void setPanSpeed(int speed) {
        String strSpeed = "PS" + speed + "\n";
        try {
            this.out.write(strSpeed.getBytes());
            PanTiltControlPTU.moving = false;
        } catch (IOException ex) {
            log.warning("In setPanSpeed caught IOexception " + ex);
        }
    }

    public void executeCommand(String command) {
        String strCommand = command + "\n";
        try {
            this.out.write(strCommand.getBytes());
        } catch (IOException ex) {
            log.warning("In executeCommand caught IOexception " + ex);
        }
    }

    /**
     *
     * @return list of COM ports
     */
    public List<String> getPortList() {
        List<String> ports = new ArrayList<String>(0);
        Enumeration pList = CommPortIdentifier.getPortIdentifiers();
        while (pList.hasMoreElements()) {
            CommPortIdentifier cpi = (CommPortIdentifier) pList.nextElement();
            if (cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                ports.add(cpi.getName());
            }
        }
        return ports;
    }

    public void halt() {
        String strHalt = "H\n";
        try {
            this.out.write(strHalt.getBytes());
        } catch (IOException ex) {
            log.warning("In halt() caught IOexception " + ex);
        }
    }
}
