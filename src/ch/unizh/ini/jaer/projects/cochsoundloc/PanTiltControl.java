package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
//import gnu.io.SerialPortEvent;
//import gnu.io.SerialPortEventListener;

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
public class PanTiltControl {

    private Logger log = Logger.getLogger("PanTiltControl");
    public OutputStream out;
    public InputStream in;
    private static volatile boolean moving = false;
    private static volatile boolean wasMoving = false;
    private boolean connected = false;
    private int panPosTransformed = 0;
    private int panPos = 0;
    private int OldMinPanPos = -800;
    private int OldMaxPanPos = 800;
    private int NewMinPanPos = -1800;
    private int NewMaxPanPos = 1800;
    private boolean invert = false;
    private int tiltPos = 0;
    private static int waitPeriod = 0;
    private static PanTiltListener panTiltListener;

    public PanTiltControl() {
        super();
    }

    void connect(String portName) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            log.warning("Error: Port for Pan-Tilt-Communication is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();

                //(new Thread(new SerialWriter(out))).start();
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
                connected = true;

                log.info("Connected to Pan-Tilt-Unit!");

            } else {
                log.warning("Error: Cannot connect to Pan-Tilt-Unit!");
            }
        }
    }

    /**
     * @param WaitPeriod is the time how long the response of a finished movement will be delayed.
     */
    public static void setWaitPeriod(int WaitPeriod) {
        waitPeriod=WaitPeriod;
        SerialReader.timer.stop();
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

    /**
     *
     * @param pos the Position to set.
     */
    public void setPanPos(int pos) {
        String strPanTilt = "PP" + pos + "\nA\n";
        try {
            this.out.write(strPanTilt.getBytes());
            PanTiltControl.moving = true;
            PanTiltControl.wasMoving = true;
            this.panPos = pos;
        } catch (IOException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
    }

    void setTiltPos(int pos) {
        String strPanTilt = "TP" + pos + "\nA\n";
        try {
            this.out.write(strPanTilt.getBytes());
            PanTiltControl.moving = true;
            PanTiltControl.wasMoving = true;
            this.tiltPos = pos;
        } catch (IOException ex) {
            log.warning("In setTiltPos(position) caught IOexception " + ex);
        }
    }

    /**
     *
     * @param pos The transformed Position to set.
     */
    public void setPanPosTransformed(int pos) { //pos between -800 to 800
        int newpos = 0;

        if (isInvert() == true) {
            newpos = (-pos - getOldMinPanPos()) * (getNewMaxPanPos() - getNewMinPanPos()) / (getOldMaxPanPos() - getOldMinPanPos()) + getNewMinPanPos();
        } else {
            newpos = (pos - getOldMinPanPos()) * (getNewMaxPanPos() - getNewMinPanPos()) / (getOldMaxPanPos() - getOldMinPanPos()) + getNewMinPanPos();
        }
        //log.info("newpos: " + newpos);
        panPosTransformed = pos;
        setPanPos(newpos);
    }

    /**
     * Sets the linear transofrmation for the Pan Positions.
     * 
     * @param OldMinPanPos
     * @param OldMaxPanPos
     * @param NewMinPanPos
     * @param NewMaxPanPos
     * @param invert
     */
    public void setTransformation(int OldMinPanPos, int OldMaxPanPos, int NewMinPanPos, int NewMaxPanPos, boolean invert) {
        this.setOldMinPanPos(OldMinPanPos);
        this.setOldMaxPanPos(OldMaxPanPos);
        this.setNewMinPanPos(NewMinPanPos);
        this.setNewMaxPanPos(NewMaxPanPos);
        this.setInvert(invert);
    }

    /**
     * Set the speed.
     * 
     * @param speed
     */
    public void setPanSpeed(int speed) {
        String strSpeed = "PS" + speed + "\n";
        try {
            this.out.write(strSpeed.getBytes());
            PanTiltControl.moving = false;
        } catch (IOException ex) {
            log.warning("In setPanSpeed caught IOexception " + ex);
        }
    }

    /**
     *
     * @param command The command to execute.
     */
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
     */
    public void halt() {
        String strHalt = "H\n";
        try {
            this.out.write(strHalt.getBytes());
        } catch (IOException ex) {
            log.warning("In halt() caught IOexception " + ex);
        }
    }

    /**
     * @return the isMoving
     */
    public boolean isMoving() {
        return moving;
    }

    /**
     * @return the isConnected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @return the panPosTransformed
     */
    public int getPanPosTransformed() {
        return panPosTransformed;
    }

    int getTiltPos() {
        return tiltPos;
    }

    /**
     * @return the panPos
     */
    public int getPanPos() {
        return panPos;
    }

    /**
     * @return the OldMinPanPos
     */
    public int getOldMinPanPos() {
        return OldMinPanPos;
    }

    /**
     * @param OldMinPanPos the OldMinPanPos to set
     */
    public void setOldMinPanPos(int OldMinPanPos) {
        this.OldMinPanPos = OldMinPanPos;
    }

    /**
     * @return the OldMaxPanPos
     */
    public int getOldMaxPanPos() {
        return OldMaxPanPos;
    }

    /**
     * @param OldMaxPanPos the OldMaxPanPos to set
     */
    public void setOldMaxPanPos(int OldMaxPanPos) {
        this.OldMaxPanPos = OldMaxPanPos;
    }

    /**
     * @return the NewMinPanPos
     */
    public int getNewMinPanPos() {
        return NewMinPanPos;
    }

    /**
     * @param NewMinPanPos the NewMinPanPos to set
     */
    public void setNewMinPanPos(int NewMinPanPos) {
        this.NewMinPanPos = NewMinPanPos;
    }

    /**
     * @return the NewMaxPanPos
     */
    public int getNewMaxPanPos() {
        return NewMaxPanPos;
    }

    /**
     * @param NewMaxPanPos the NewMaxPanPos to set
     */
    public void setNewMaxPanPos(int NewMaxPanPos) {
        this.NewMaxPanPos = NewMaxPanPos;
    }

    /**
     * @return the invert
     */
    public boolean isInvert() {
        return invert;
    }

    /**
     * @param invert the invert to set
     */
    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    /**
     * @return the wasMoving
     */
    public boolean isWasMoving() {
        return wasMoving;
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

    /**
     * @param aLogResponses the logResponses to set
     */
    public static void setLogResponses(boolean aLogResponses) {
        SerialReader.logResponses = aLogResponses;
    }

    void addPanTiltListener(PanTiltListener panTiltListener) {
        PanTiltControl.panTiltListener=panTiltListener;
    }

}
