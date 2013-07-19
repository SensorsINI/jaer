package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;


/**
 * Controls the Dynamixel Servos
 * 
 * @author Holger
 */
public class PanTiltControlDynamixel extends PanTiltControl {

    public OutputStream out;
    public InputStream in;
    private int speed = 10;
    SerialReader serialReader;

    public PanTiltControlDynamixel() {
        super();
    }

    public void setLogResponses(boolean aLogResponses) {
        serialReader.logResponses = aLogResponses;
    }
     
    public void setWaitPeriod(int WaitPeriod) {
        PanTiltControlDynamixel.waitPeriod = WaitPeriod;
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
                serialReader = new SerialReader(in);
                serialPort.addEventListener(serialReader);
                serialPort.notifyOnDataAvailable(true);
                connected = true;
                log.info("Connected to Dynamixel!");
            } else {
                log.warning("Error: Cannot connect to Dynamixel!");
            }

        }
    }

    @Override
    void setPanPos(double panPos) {
        move((byte)1,(short)panPos,(short)speed);
    }

    @Override
    void setTiltPos(double tiltPos) {
        move((byte)2,(short)tiltPos,(short)speed);
    }

    /**
     *
     */
    public class SerialReader implements gnu.io.SerialPortEventListener
    {
        private InputStream in;
        private byte[] buffer = new byte[1024];
        private volatile boolean logResponses = false;
        private Logger loggerResponses = Logger.getLogger("Pan-Tilt-Responses");
        public boolean askedIfMoving = false;

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
                if (askedIfMoving && response.startsWith(" <")) {
                    if (response.substring(31,32).matches("0")) {
                        askedIfMoving = false;
                        moving = false;
                        if (panTiltListener != null) {
                            panTiltListener.panTiltAction(new PanTiltEvent(this, 0));
                        }
                    }
                    if (response.substring(31,32).matches("1")) {
                        askedIfMoving = false;
                        moving = true;
                        if (panTiltListener != null) {
                            panTiltListener.panTiltAction(new PanTiltEvent(this, 1));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    public void checkIfMoving(byte ID) {
        serialReader.askedIfMoving = true;
        String strPanTilt;
        if (ID == 1) {
            strPanTilt = "HEX FF FF 01 04 02 2E 01 C9\n";
        } else {
            strPanTilt = "HEX FF FF 02 04 02 2E 01 C8\n";
        }
        try {
            this.out.write(strPanTilt.getBytes());
        } catch (IOException ex) {
            log.warning("In checkIfMoving caught IOexception " + ex);
        }
    }

    public void move(byte ID, short pos, short speed) {
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
            this.setPanPos(pos);
        } catch (IOException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
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
