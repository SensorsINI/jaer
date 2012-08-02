/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011.head6axis;

import gnu.io.UnsupportedCommOperationException;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Float;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.serial.HWP_UART;

/**
 * Controls 6-DOF head from the group of Jorg Conradt at TUM. Modified July 2012
 * for the new 6DOF servo controller built for the new 3d-printed head with two
 * DVS pan-tilt and neck pan-tilt.
 *
 * The new servo controller has the following protocol:
 * <pre>
 * For communication with the robot head, configure your serial port to 4,000,000 bps (4mbps), 8N1, no hardware handshaking (no RTS/CTS)
 * The current protocol is very simplistic:
 * SxSy<return>      set servo x [0..5] desired position to y[1000..2000]  (center close to 1500)
 * SxR<return>       request current angular position of servo x
 * </pre>
 *
 * @author tobi
 */
@Description("Controls 6-DOF head from the group of Jorg Conradt at TUM")
public class Head6DOF_ServoController extends EventFilter2D { // extends EventFilter only to allow enclosing in filter

    int warningCount = 10;
    HWP_UART serialPort;
    final int NSERVOS = 6;
    float[] servoValues = new float[NSERVOS];
    Head6DOF_GUI gui = null;
    final int EYE_LEFT_PAN = 2, EYE_LEFT_TILT = 3, EYE_RIGHT_PAN = 4, EYE_RIGHT_TILT = 5,
            HEAD_PAN = 0, HEAD_TILT = 1;
    final String CMD_RESET = "!R", CMD_CYCLE = "!C", CMD_SERVO = "!S";
    final int TIME_UNIT_NS = 250;  // unit of time in ns for servo controller times
    private boolean connected = false;
    private float headPanOffset = getFloat("headPanOffset", 0);
    private float headTiltOffset = getFloat("headTiltOffset", 0);
    private float vergenceOffset = getFloat("vergenceOffset", 0);
    final float VERGENCE_LIMIT = .2f;
    final float PAN_LIMIT = .4f, TILT_LIMIT = .3f, HEAD_LIMIT = .25f;
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm", 4.5f);
    private float servoRangeDeg = getFloat("servoRangeDeg", 160);
    ServoCommandWriter servoCommandWriter = null; // this worker thread asynchronously writes to device
    private volatile ArrayBlockingQueue<String> servoQueue; // this queue is used for holding servo commands that must be sent out.
    /**
     * number of servo commands that can be queued up. It is set to a small
     * number so that commands do not pile up. If the queue is full when a
     * command is given, then the old commands are discarded so that the latest
     * command is next to be processed. Note that this policy can have drawbacks
     * - if commands are sent to different servos successively, then new
     * commands can wipe out commands to older commands to set other servos to
     * some position.
     */
    public static final int SERVO_QUEUE_LENGTH = 20;

    public class GazeDirection implements Cloneable, Serializable {

        /**
         * monocular gaze direction accounting for head pan/tilt and average eye
         * direction, 0,0 is centered, limits +-1
         */
        Point2D.Float gazeDirection = new Point2D.Float(0, 0);
        Point2D.Float leftEyeGazeDirection = new Point2D.Float();
        Point2D.Float rightEyeGazeDirection = new Point2D.Float();
        Point2D.Float[] eyeGazeDirections = {leftEyeGazeDirection, rightEyeGazeDirection};
        float vergence = 0; // 0 is infinity vergence, >0 is cross-eyed
        Point2D.Float headDirection = new Point2D.Float();

        @Override
        protected Object clone() throws CloneNotSupportedException {
            GazeDirection newGaze = (GazeDirection) (super.clone());
            newGaze.gazeDirection.setLocation(gazeDirection);
            newGaze.leftEyeGazeDirection.setLocation(leftEyeGazeDirection);
            newGaze.rightEyeGazeDirection.setLocation(rightEyeGazeDirection);
            newGaze.eyeGazeDirections[0] = newGaze.leftEyeGazeDirection;
            newGaze.eyeGazeDirections[1] = newGaze.rightEyeGazeDirection;
            newGaze.headDirection.setLocation(headDirection);
            return newGaze;
        }

        public void writeObject() {
        }
    }
    GazeDirection gazeDirection = new GazeDirection(), memorizedGazeDirection = new GazeDirection();

    public enum COM_PORT {

        COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8
    }
    private COM_PORT comPort = COM_PORT.valueOf(getString("comPort", COM_PORT.COM5.toString()));  // com5 chosen on tobi's virgin FTDI install
    private int baudRate = getInt("baudRate", 460800); // 460800, RTS=true for servo board for head

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
        setPropertyTooltip("centerGaze", "centers all servos");
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
            oos.writeObject(memorizedGazeDirection.gazeDirection);
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
            setEyeGazeDirection(memorizedGazeDirection.gazeDirection.x, memorizedGazeDirection.gazeDirection.y);
            // TODO implement Head when head is fixed
            setVergence(memorizedGazeDirection.vergence);

            log.info("returned to memorized gaze direction");

        } catch (Exception e) {
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
            memorizedGazeDirection.gazeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.headDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.leftEyeGazeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.rightEyeGazeDirection = (Point2D.Float) ois.readObject();
            memorizedGazeDirection.vergence = ((java.lang.Float) ois.readObject());
            ois.close();
            bis.close();
            log.info("loaded memorizedGazeDirection from preferences");
        } catch (Exception e) {
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
        if (gui == null) {
            gui = new Head6DOF_GUI(this);
        }
        gui.setVisible(true);
    }

    public void doCenterGaze() {
        try {
            setEyeGazeDirection(.5f, .5f);
            //        float[] f = new float[NSERVOS];
            //        Arrays.fill(f, .5f);
            //        try {
            //            setAllServoValues(f);
            //        } catch (Exception ex) {
            //        }
            //        }
        } catch (Exception e) {
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
        setConnected(false);
    }

    public void doConnect() {
        setConnected(true);
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
                serialPort.open(comPort.toString(), baudRate);
                serialPort.setHardwareFlowControl(false); // was true for jorg's original
//                serialPort.writeLn(CMD_RESET);// reset board
//                Thread.sleep(200);
//                serialPort.writeLn(CMD_CYCLE + "=40000"); // sets servo cycle time to 10ms; time unit is .25us
//                Thread.sleep(200);
                serialPort.purgeInput();
                log.info("opened serial port " + serialPort);
                servoQueue=new ArrayBlockingQueue<String>(30);
                servoCommandWriter=new ServoCommandWriter();
                servoCommandWriter.start();
                this.connected = true;
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        } else if (!connected && this.connected) {
            try {
                serialPort.close();
                log.info("disconnected serial port");
                this.connected = false;
            } catch (Exception ex) {
                log.warning(ex.toString());
            }

        }
        getSupport().firePropertyChange("connected", old, this.connected);
    }

    private int float2servo(float f) {
//        return 4000 + (int) (f * 4000); // sets from 4000=.25us*4000=1000us=1ms to 2ms which is range for hobby servos
        return 1000 + (int) (f * 1000); // sets from 1000 to 2000 us
    }

    public void setServoValue(int servo, float value) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        if (servoCommandWriter == null || !servoCommandWriter.isAlive()) {
            return;
        }

        servoValues[servo] = value;
//        serialPort.writeLn(String.format("%s%d=%d", CMD_SERVO, servo, float2servo(value))); // eg !S3=6000
        String cmd = String.format("s%ds%d", servo, float2servo(value));
        if (!servoQueue.offer(cmd)) {
            log.warning("command \"" + cmd + "\" could not be queued for transmission");
        }

//        serialPort.writeLn(cmd);
//        try {
//            Thread.sleep(5);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Head6DOF_ServoController.class.getName()).log(Level.SEVERE, null, ex);
//        }
        //        if(!serialPort.sendCommand(cmd,cmd)){
        //            log.warning("cmd=\""+cmd+"\" did not echo back properly");
        //        }
        //        serialPort.purgeInput();
        //        serialPort.purgeInput();   
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

    void setHeadDirection(float pan, float tilt) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        pan = clip(pan + headPanOffset, HEAD_LIMIT);
        setServoValue(HEAD_PAN, gaze2servo(pan));
        gazeDirection.headDirection.x = pan;
        tilt = clip(tilt + headTiltOffset, HEAD_LIMIT);
        setServoValue(HEAD_TILT, gaze2servo(tilt));
        gazeDirection.headDirection.y = tilt;
        log.info("headDirection pan=" + pan + " tilt=" + tilt);
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
    }

    float clip(float in, float limit) {
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
        pan = clip(pan, PAN_LIMIT);
        tilt = clip(tilt, TILT_LIMIT);
        setServoValue(EYE_LEFT_PAN, gaze2servo(pan + gazeDirection.vergence));
        gazeDirection.leftEyeGazeDirection.x = pan;
        setServoValue(EYE_RIGHT_PAN, gaze2servo(pan - gazeDirection.vergence));
        gazeDirection.rightEyeGazeDirection.x = pan;
        setServoValue(EYE_LEFT_TILT, gaze2servo(tilt)); // servo is flipped over
        gazeDirection.leftEyeGazeDirection.y = tilt;
        setServoValue(EYE_RIGHT_TILT, gaze2servo(-tilt));
        gazeDirection.rightEyeGazeDirection.y = tilt;
        gazeDirection.gazeDirection.setLocation(pan, tilt);
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
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
        } catch (Exception e) {
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
        setEyeGazeDirection(gazeDirection.gazeDirection.x, gazeDirection.gazeDirection.y);
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
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

        final int CMD_DELAY_MS = 5;
        volatile boolean stop=false;
        
        public void stopThread(){
            stop=true;
            interrupt();
        }

        public void run() {
            while (!stop) {
                try {
                    String cmd = servoQueue.take();  // wait forever until there is a command
                    try {
                        serialPort.writeLn(cmd);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Head6DOF_ServoController.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(Head6DOF_ServoController.class.getName()).log(Level.SEVERE, null, ex);
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
