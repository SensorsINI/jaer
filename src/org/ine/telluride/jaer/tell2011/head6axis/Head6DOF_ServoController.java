/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011.head6axis;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.UnsupportedCommOperationException;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.serial.HWP_UART;

/**
 * Controls 6-DOF head from the group of Jorg Conradt at TUM. Modified November
 * 2014 for the new 6DOF servo controller board built for the new 3d-printed
 * head with two DVS pan-tilt and neck pan-tilt.
 *
 * The new servo controller has the following protocol:
 * <pre>
 * For communication with the robot head, configure your serial port to 1,000,000 bps (1mbps), 8N1, hardware handshaking (RTS/CTS)
 * The current protocol is very simplistic:
 * !Px,y<return>      set servo x [0..5] desired position to y[1000..2000]  (center close to 1500)
 * ?Px<return>       request current angular position of servo x
 * </pre>
 *
 * @author tobi
 * @editor philipp
 */
/**
 * servos: 0 = head pan; offset = 1479 1 = head tilt; offset = 1890 2 = left eye
 * pan; offset = 1943 3 = left eye tilt; offset = 1040 4 = right eye pan; offset
 * = 1048 5 = right eye tilt; offset = 1890
 *
 * @author Philipp
 */
@Description("Controls 6-DOF head from the group of Jorg Conradt at TUM")
public class Head6DOF_ServoController extends EventFilter2D { // extends EventFilter only to allow enclosing in filter

    protected static final Logger log = Logger.getLogger("Head6DOF_ServoController");
    int warningCount = 10;
    HWP_UART serialPort;
    final int NSERVOS = 6;
    float[] servoValues = new float[NSERVOS];
    Head6DOF_GUI gui = null;
    final int EYE_LEFT_PAN = 2, EYE_LEFT_TILT = 3, EYE_RIGHT_PAN = 4, EYE_RIGHT_TILT = 5,
            HEAD_PAN = 0, HEAD_TILT = 1;
    final String CMD_RESET = "!R", CMD_CYCLE = "!C", CMD_SERVO = "!S";
    final int TIME_UNIT_NS = 250;  // unit of time in ns for servo controller times
    public final int[] servoOffsets = {1479, 1890, 1943, 1050, 1048, 1911}; // offsets for each servo
    private boolean connected = false;
    private float headPanOffset = getFloat("headPanOffset", 0);
    private float headTiltOffset = getFloat("headTiltOffset", 0);
    private float vergenceOffset = getFloat("vergenceOffset", 0);
    public final float VERGENCE_LIMIT = .18f;   // .2f for tobi's version
    public final float EYE_PAN_LIMIT = .35f, EYE_TILT_LIMIT = .225f, HEAD_PAN_LIMIT = .9f, HEAD_TILT_LIMIT = .5f;   // .4f .3f .25f .25f for tobi's version
    public float lensFocalLengthMm = getFloat("lensFocalLengthMm", 8f);
    private float servoRangeDeg = getFloat("servoRangeDeg", 160);
    ServoCommandWriter servoCommandWriter = null; // this worker thread asynchronously writes to device
    private volatile ArrayBlockingQueue<String> servoQueue; // this queue is used for holding servo commands that must be sent out.
    java.util.Timer testTimer = null;
    boolean newBoard = false; //define if you use the new board from Jörg

    /**
     * number of servo commands that can be queued up. It is set to a small
     * number so that commands do not pile up. If the queue is full when a
     * command is given, then the old commands are discarded so that the latest
     * command is next to be processed. Note that this policy can have drawbacks
     * - if commands are sent to different servos successively, then new
     * commands can wipe out commands to older commands to set other servos to
     * some position.
     */
    public static final int SERVO_QUEUE_LENGTH = 30; // was 20 originally

    /**
     * @return the EYE_PAN_LIMIT
     */
    public float getEYE_PAN_LIMIT() {
        return EYE_PAN_LIMIT;
    }

    /**
     * @return the EYE_TILT_LIMIT
     */
    public float getEYE_TILT_LIMIT() {
        return EYE_TILT_LIMIT;
    }

    /**
     * @return the HEAD_PAN_LIMIT
     */
    public float getHEAD_PAN_LIMIT() {
        return HEAD_PAN_LIMIT;
    }

    /**
     * @return the HEAD_TILT_LIMIT
     */
    public float getHEAD_TILT_LIMIT() {
        return HEAD_TILT_LIMIT;
    }

    public class GazeDirection implements Cloneable, Serializable {

        /**
         * monocular gaze direction accounting for head pan/tilt and average eye
         * direction, 0,0 is centered, limits +-1
         */
        Point2D.Float eyeDirection = new Point2D.Float(0, 0);
        Point2D.Float leftEyeGazeDirection = new Point2D.Float();
        Point2D.Float rightEyeGazeDirection = new Point2D.Float();
        Point2D.Float[] eyeGazeDirections = {leftEyeGazeDirection, rightEyeGazeDirection};
        float vergence = 0; // 0 is infinity vergence, >0 is cross-eyed
        Point2D.Float headDirection = new Point2D.Float();

        @Override
        protected Object clone() throws CloneNotSupportedException {
            GazeDirection newGaze = (GazeDirection) (super.clone());
            newGaze.eyeDirection.setLocation(eyeDirection);
            newGaze.leftEyeGazeDirection.setLocation(leftEyeGazeDirection);
            newGaze.rightEyeGazeDirection.setLocation(rightEyeGazeDirection);
            newGaze.eyeGazeDirections[0] = newGaze.leftEyeGazeDirection;
            newGaze.eyeGazeDirections[1] = newGaze.rightEyeGazeDirection;
            newGaze.headDirection.setLocation(headDirection);
            return newGaze;
        }

        public void writeObject() {
        }

        public Point2D.Float getHeadDirection() {
            return headDirection;
        }

        public Point2D.Float getEyeDirection() {
            return eyeDirection;
        }
    }
    public GazeDirection gazeDirection = new GazeDirection(), memorizedGazeDirection = new GazeDirection();

    public enum COM_PORT {

        COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8
    }
    private COM_PORT comPort = COM_PORT.valueOf(getString("comPort", COM_PORT.COM5.toString()));  // com5 chosen on tobi's virgin FTDI install
//    private int baudRate = getInt("baudRate", 1000000); // 1000000, RTS=true for new servo board for head
    private int baudRate = getInt("baudRate", 4000000); // 1000000, RTS=true for old servo board for head

    public Head6DOF_ServoController(AEChip chip) {
        super(chip);
        serialPort = new HWP_UART();
        Arrays.fill(servoValues, .5f);
        setPropertyTooltip("connect", "connect to serial port");
        setPropertyTooltip("disconnect", "disconnect from serial port");
        setPropertyTooltip("comPort", "serial port for first FTDI interface");
        setPropertyTooltip("baudRate", "baud rate; 460800 is default for the head controller");
        setPropertyTooltip("connected", "shows connected status and can also be used to connect/disconnect");
        setPropertyTooltip("showGUI", "shows GUI to manually control head");
        setPropertyTooltip("reset", "sends a reset command to the controller");
        setPropertyTooltip("centerGaze", "centers the eye servos");
        setPropertyTooltip("centerHead", "centers the head servos");
        setPropertyTooltip("ResetVergence", "resets the vergence to 0 = infinity");
        setPropertyTooltip("headPanOffset", "offset for pan of head");
        setPropertyTooltip("headTiltOffset", "offset for tilt of head");
        setPropertyTooltip("vergenceOffset", "offset for vergence");
        setPropertyTooltip("memorizeGaze", "memorize this gaze direction");
        setPropertyTooltip("returnToMemorizedGaze", "return to memorized gaze");
        setPropertyTooltip("lensFocalLengthMm", "focal length of lenses on two eyes in mm");
        setPropertyTooltip("servoRangeDeg", "total range of servos used in eye pan tilt units");
        loadMemorizedGazeDirection();
    }

    public void doMemorizeGaze() {
        try {
            memorizedGazeDirection = (GazeDirection) gazeDirection.clone();
        } catch (CloneNotSupportedException ex) {
            log.warning("couldn't clone gaze: " + ex.toString());
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(memorizedGazeDirection.eyeDirection);
            oos.writeObject(memorizedGazeDirection.headDirection);
            oos.writeObject(memorizedGazeDirection.leftEyeGazeDirection);
            oos.writeObject(memorizedGazeDirection.rightEyeGazeDirection);
            oos.writeObject(memorizedGazeDirection.vergence);
            getPrefs().putByteArray("Head6DOF_ServoController.memorizedGazeDirection", bos.toByteArray());
            oos.close();
            bos.close();
            log.info("memorized gaze direction");
        } catch (IOException ex) {
            log.warning("couldn't save gaze direction to preferences: " + ex.toString());
        }
    }

    public void doReturnToMemorizedGaze() {
        try {
            loadMemorizedGazeDirection();
            setVergence(memorizedGazeDirection.vergence);
            setEyeGazeDirection(memorizedGazeDirection.eyeDirection.x, memorizedGazeDirection.eyeDirection.y);
            setHeadDirection(memorizedGazeDirection.headDirection.x, memorizedGazeDirection.headDirection.y);
            // TODO implement Head when head is fixed

            log.info("returned to memorized gaze direction");

        } catch (IOException | HardwareInterfaceException e) {
            log.warning("couldn't return to memorized location: " + e.toString());
        }
    }

    private void loadMemorizedGazeDirection() {
        try {
            byte[] b = getPrefs().getByteArray("Head6DOF_ServoController.memorizedGazeDirection", null);
            if (b == null) {
                log.info("no memorizedGazeDirection");
                return;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            memorizedGazeDirection.eyeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.headDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.leftEyeGazeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.rightEyeGazeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.vergence = ((java.lang.Float) ois.readObject());
            ois.close();
            bis.close();
            log.info("loaded memorizedGazeDirection from preferences");
        } catch (IOException | ClassNotFoundException e) {
            log.warning("couldn't load gaze direction: " + e.toString());
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doShowGUI() {
        if (isConnected() == true) {
            if (gui == null) {
                gui = new Head6DOF_GUI(this);
            }
            if (gui.isVisible() == false) {
                gui.setVisible(true);
            }
        } else {
            log.info("Not connected to robothead. Connect to robothead first");
        }

    }

    public void doCenterGaze() {
        try {
            setEyeGazeDirection(.0f, .0f);
            gui.repaint();
            //        float[] f = new float[NSERVOS];
            //        Arrays.fill(f, .5f);
            //        try {
            //            setAllServoValues(f);
            //        } catch (Exception ex) {
            //        }
            //        }
        } catch (IOException | HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    public void doCenterHead() {
        try {
            setHeadDirection(.0f, .0f);      // centers the head looking straight forward
            gui.repaint();
        } catch (IOException | HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    public void doResetVergence() {
        try {
            setVergence(.0f);                // resets the vergence to 0 = infinity
            gui.resetVergenceSlider();
            gui.repaint();
        } catch (IOException | HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    public void doReset() {
        log.warning("no effect on this controller");
//        try {
//            serialPort.writeLn(CMD_RESET);// reset board
//            Thread.sleep(200);
//            serialPort.purgeInput();
//            log.info("reset");
//        } catch (Exception ex) {
//            log.warning(ex.toString());
//        }
    }

    public void doDisconnect() {
        if (this.isConnected() == true) {
            setConnected(false);
        } else {
            log.info("not connected to robothead");
        }
    }

    public void doConnect() {
        if (this.isConnected() == false) {
            setConnected(true);
        } else {
            log.info("already connected to robothead");
        }
    }

    /**
     * @return the connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @param connected the connected to set
     */
    public void setConnected(boolean connected) {
        boolean old = this.connected;
        if (!this.connected && connected) {
            try {
                CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(comPort.toString());
                if (portIdentifier.isCurrentlyOwned()) {
                    log.warning("Error: Port for Pan-Tilt-Communication is currently in use");
                } else {
                    try {
                        serialPort.open(comPort.toString(), baudRate);
                        if (newBoard == true) {
                            serialPort.setHardwareFlowControl(true);
                        } else {
                            serialPort.setHardwareFlowControl(false);
                        }
                        serialPort.purgeInput();
                        log.info("opened serial port " + serialPort);
                        servoQueue = new ArrayBlockingQueue<String>(SERVO_QUEUE_LENGTH); // Variable "SERVO_QUEUE_LENGTH" wasn't used
                        servoCommandWriter = new ServoCommandWriter();
                        servoCommandWriter.start();
                        this.connected = true;
                        doShowGUI();
                    } catch (UnsupportedCommOperationException | IOException ex) {
                        log.warning(ex.toString());
                    }
                }
            } catch (NoSuchPortException ex) {
                log.severe("no serial Port detected: " + ex.toString());
            }
        } else if (!connected && this.connected) {
            try {
                serialPort.close();
                servoCommandWriter.stopThread();
                servoCommandWriter = null;
                gui.setVisible(false);
                gui.dispose();
                gui = null;
                this.connected = false;
                log.info("disconnected from serial port");
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
        getSupport().firePropertyChange("connected", old, this.connected);
    }

    private int float2servo(int servo, float f) {
        return servoOffsets[servo] - 500 + (int) (f * 1000);  // PWMs for each servo according to its offset
    }

    public void setServoValue(int servo, float value) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        if (servoCommandWriter == null || !servoCommandWriter.isAlive()) {
            return;
        }
        servoValues[servo] = value;
        String cmd = "";
        if (newBoard == true) {
            cmd = String.format("!P%d,%d", servo, float2servo(servo, value)); //"servo" defines which servo is addressed; "float2servo" transforms the desired position into a PWM value 
        } else {
            cmd = String.format("s%ds%d", servo, float2servo(servo, value)); //"servo" defines which servo is addressed; "float2servo" transforms the desired position into a PWM value 
        }
        if (!servoQueue.offer(cmd)) {
            log.warning("command \"" + cmd + "\" could not be queued for transmission");
        }
    }

    public void disableServo(int servo) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        log.warning("has no effect on this servo controller");
//        serialPort.writeLn(String.format("%s%d=%d", CMD_SERVO, servo, 0)); // eg !S0=0
//        serialPort.flushOutput();
//        serialPort.purgeInput();
    }

    public int getNumServos() {
        return NSERVOS;
    }

    public void disableAllServos() throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        log.warning("has no effect on this servo controller");
//        for (int i = 0; i < NSERVOS; i++) {
//            disableServo(i);
//        }
    }

    public void setAllServoValues(float[] values) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        for (int i = 0; i < NSERVOS; i++) {
            setServoValue(i, values[i]);
        }
    }

    public float[] getLastServoValues() {
        return servoValues;
    }

    public String getTypeName() {
        return "Head6DOF_ServoController";
    }

    public void close() {
        serialPort.close();
        servoCommandWriter.stopThread();
    }

    /**
     * @return the comPort
     */
    public COM_PORT getComPort() {
        return comPort;
    }

    /**
     * @param comPort the comPort to set
     */
    public void setComPort(COM_PORT comPort) {
        this.comPort = comPort;
        putString("comPort", comPort.toString());
    }

    /**
     * @return the baudRate
     */
    public int getBaudRate() {
        return baudRate;
    }

    /**
     * @param baudRate the baudRate to set
     */
    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
        putInt("baudRate", baudRate);
    }

    // converts from -1:+1 to 0:1 range
    float gaze2servo(float gaze) {
        return (1 + gaze) / 2;
    }

    public void setHeadDirection(float pan, float tilt) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        pan = clip(pan + headPanOffset, getHEAD_PAN_LIMIT());
        setServoValue(HEAD_PAN, gaze2servo(pan));
        gazeDirection.headDirection.x = pan;
        tilt = clip(tilt + headTiltOffset, getHEAD_TILT_LIMIT());
        setServoValue(HEAD_TILT, gaze2servo(-tilt));  // set -tilt for not inverted gui control
        gazeDirection.headDirection.y = tilt;
        //    log.info("headDirection pan=" + pan + " tilt=" + tilt);
//        getSupport().firePropertyChange("eyeDirection", null, eyeDirection);
    }

    public float clip(float in, float limit) {
        if (in < -limit) {
            in = -limit;
        } else if (in > limit) {
            in = limit;
        }
        return in;
    }

    /**
     * Aims the eyes in servo space
     *
     * @param pan 0-1 value
     * @param tilt 0-1 value
     * @throws HardwareInterfaceException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void setEyeGazeDirection(float pan, float tilt) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        pan = clip(pan, getEYE_PAN_LIMIT());
        tilt = clip(tilt, getEYE_TILT_LIMIT());
        setServoValue(EYE_LEFT_PAN, gaze2servo(pan + gazeDirection.vergence));
        gazeDirection.leftEyeGazeDirection.x = pan;
        setServoValue(EYE_RIGHT_PAN, gaze2servo(pan - gazeDirection.vergence));
        gazeDirection.rightEyeGazeDirection.x = pan;
        setServoValue(EYE_LEFT_TILT, gaze2servo(tilt));
        gazeDirection.leftEyeGazeDirection.y = tilt;
        setServoValue(EYE_RIGHT_TILT, gaze2servo(-tilt)); // servo is flipped over
        gazeDirection.rightEyeGazeDirection.y = tilt;
        gazeDirection.eyeDirection.setLocation(pan, tilt);
        //      log.info("eyeDirection pan=" + pan + " tilt=" + tilt);
//        getSupport().firePropertyChange("eyeDirection", null, eyeDirection);
    }

    /**
     * Aims the eyes in pixel space
     *
     * @param x x of pixel
     * @param y y of pixel
     */
    public void setEyeGazeDirectionPixels(float x, float y) {
        Point2D.Float aim = pixelToPanTilt(x, y);
        try {
            setEyeGazeDirection(aim.x, aim.y);
        } catch (IOException | HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    private Point2D.Float pixelToPanTilt(float x, float y) {
        float pixWidth = chip.getPixelWidthUm();
        float pixHeight = chip.getPixelHeightUm();
        float xpixoff = x - chip.getSizeX();
        float ypixoff = y - chip.getSizeY();
        float pan = 0.5f + 1e-3f * xpixoff * pixWidth / lensFocalLengthMm * 180 / (float) Math.PI / servoRangeDeg;
        float tilt = 0.5f + 1e-3f * ypixoff * pixHeight / lensFocalLengthMm * 180 / (float) Math.PI / servoRangeDeg;
        Point2D.Float eyeGazePoint = new Point2D.Float(pan, tilt);
        return eyeGazePoint;
    }

    public void setVergence(float vergence) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        vergence = clip(vergence + vergenceOffset, VERGENCE_LIMIT);
        gazeDirection.vergence = vergence;
        setEyeGazeDirection(gazeDirection.eyeDirection.x, gazeDirection.eyeDirection.y);
        //log.info("eyeDirection vergence=" + vergence);
        //getSupport().firePropertyChange("eyeDirection", null, eyeDirection);
    }

    public GazeDirection getGazeDirection() {
        return gazeDirection;
    }

    /**
     * @return the headPanOffset
     */
    public float getHeadPanOffset() {
        return headPanOffset;
    }

    /**
     * @param headPanOffset the headPanOffset to set
     */
    public void setHeadPanOffset(float headPanOffset) {
        this.headPanOffset = headPanOffset;
        putFloat("headPanOffset", headPanOffset);
    }

    /**
     * @return the headTiltOffset
     */
    public float getHeadTiltOffset() {
        return headTiltOffset;
    }

    /**
     * @param headTiltOffset the headTiltOffset to set
     */
    public void setHeadTiltOffset(float headTiltOffset) {
        this.headTiltOffset = headTiltOffset;
        putFloat("headTiltOffset", headTiltOffset);
    }

    /**
     * @return the vergenceOffset
     */
    public float getVergenceOffset() {
        return vergenceOffset;
    }

    /**
     * @param vergenceOffset the vergenceOffset to set
     */
    public void setVergenceOffset(float vergenceOffset) {
        this.vergenceOffset = vergenceOffset;
        putFloat("vergenceOffset", vergenceOffset);
    }

    /**
     * @return the lensFocalLengthMm
     */
    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    /**
     * @param lensFocalLengthMm the lensFocalLengthMm to set
     */
    public void setLensFocalLengthMm(float lensFocalLengthMm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        putFloat("lensFocalLengthMm", lensFocalLengthMm);
    }

    /**
     * @return the servoRangeDeg
     */
    public float getServoRangeDeg() {
        return servoRangeDeg;
    }

    /**
     * @param servoRangeDeg the servoRangeDeg to set
     */
    public void setServoRangeDeg(float servoRangeDeg) {
        this.servoRangeDeg = servoRangeDeg;
        putFloat("servoRangeDeg", servoRangeDeg);
    }

    private class ServoCommandWriter extends Thread {

        final int CMD_DELAY_MS = 0;  // no delay needed
        volatile boolean stop = false;

        ServoCommandWriter() {
            setName("ServoCommandWriter");
        }

        public void stopThread() {
            stop = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!stop) {
                try {
                    String cmd = servoQueue.take();  // wait forever until there is a command
                    try {
                        serialPort.writeLn(cmd);
                        if (newBoard == true) {
                            serialPort.getAllData();
                        }
                    } catch (UnsupportedEncodingException ex) {
                        log.severe("unable to write to serial Port: " + ex.toString());
                    } catch (IOException ex) {
                        log.severe("unable to write to serial Port: " + ex.toString());
                    }
                    try {
                        Thread.sleep(CMD_DELAY_MS);
                    } catch (InterruptedException ex) {
                        log.info("Servo writer interrupted: " + ex.toString());
                    }
                } catch (InterruptedException e) {
                    log.info("servo queue wait interrupted");
                    interrupt(); // important to call again so that isInterrupted in run loop see that thread should terminate
                }
            }

        }
    }
}
