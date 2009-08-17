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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
public class PanTiltControlDynamixel extends PanTiltControl {

    public OutputStream out;
    public InputStream in;
    private int speed = 10;

    public PanTiltControlDynamixel() {
        super();
    }

    public void setLogResponses(boolean aLogResponses) {
        SerialReader.logResponses = aLogResponses;
    }
     
    public void setWaitPeriod(int WaitPeriod) {
        PanTiltControlDynamixel.waitPeriod = WaitPeriod;
        SerialReader.timer.stop();
    }

    void connect(String destination) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(destination);
        if (portIdentifier.isCurrentlyOwned()) {
            log.warning("Error: Port for Dynamixel-Communication is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(57600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
                connected = true;
                log.info("Connected to Dynamixel!");
            } else {
                log.warning("Error: Cannot connect to Dynamixel!");
            }
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
    
    public void move (byte ID, short pos, short speed) {
        try {
            ByteBuffer bbPos = ByteBuffer.allocate(2);
            bbPos.order(ByteOrder.LITTLE_ENDIAN);
            bbPos.putShort(pos);
            byte posH = bbPos.get(0);
            byte posL = bbPos.get(1);
            ByteBuffer bbSpeed = ByteBuffer.allocate(2);
            bbSpeed.order(ByteOrder.LITTLE_ENDIAN);
            bbSpeed.putShort(speed);
            byte speedH = bbSpeed.get(0);
            byte speedL = bbSpeed.get(1);
            byte Length = 7;
            byte Instruction = 3;
            byte WriteAddress = 30;
            byte sum = (byte) (ID + posH + posL + speedH + speedL + Length + Instruction + WriteAddress);
            String checksum = java.lang.Integer.toHexString(~sum);
            int index = checksum.length()-2;
            if (index<0)
                index = 0;
            String strPanTilt = "HEX FF FF" +
                    " " + java.lang.Integer.toHexString(ID) +
                    " " + java.lang.Integer.toHexString(Length) +
                    " " + java.lang.Integer.toHexString(Instruction) +
                    " " + java.lang.Integer.toHexString(WriteAddress) +
                    " " + java.lang.Integer.toHexString(posH) +
                    " " + java.lang.Integer.toHexString(posL) +
                    " " + java.lang.Integer.toHexString(speedH) +
                    " " + java.lang.Integer.toHexString(speedL) +
                    " " + checksum.substring(index) +
                    "\n";
            this.out.write(strPanTilt.getBytes());
            //String strPanTilt = "CID 1\n";
            //this.out.write(strPanTilt.getBytes());
            //strPanTilt = "GO " + pos + " 20\n";
            //this.out.write(strPanTilt.getBytes());
            //PanTiltControlDynamixel.moving = true;
            //PanTiltControlDynamixel.wasMoving = true;
            this.panPos = pos;
        } catch (IOException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
    }
    
    public void setPanPos(int pos) {
        move((byte)1,(short)pos,(short)speed);
    }

    void setTiltPos(int pos) {
        move((byte)2,(short)pos,(short)speed);
    }
    
    public void setPanSpeed(int speed) {
        this.speed = speed;
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
