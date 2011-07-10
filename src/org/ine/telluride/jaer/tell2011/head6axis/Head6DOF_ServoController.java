/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011.head6axis;

import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import gnu.io.UnsupportedCommOperationException;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Float;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.serial.HWP_UART;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

/**
 * Controls 6-DOF head from the group of Jorg Conradt at TUM.
 * @author tobi
 */
@Description("Controls 6-DOF head from the group of Jorg Conradt at TUM")
public class Head6DOF_ServoController extends EventFilter2D { // extends EventFilter only to allow enclosing in filter

    int warningCount = 10;
    HWP_UART serialPort;
    final int NSERVOS = 6;
    float[] servoValues = new float[NSERVOS];
    Head6DOF_GUI gui = null;
    final int EYE_LEFT_PAN = 0, EYE_LEFT_TILT = 1, EYE_RIGHT_PAN = 2, EYE_RIGHT_TILT = 3,
            HEAD_PAN = 4, HEAD_TILT = 5;
    final String CMD_RESET = "!R", CMD_CYCLE = "!C", CMD_SERVO = "!S";
    final int TIME_UNIT_NS = 250;  // unit of time in ns for servo controller times
    private boolean connected = false;
    final float PAN_LIMIT = .4f, TILT_LIMIT = .4f, HEAD_LIMIT=.25f;

    public class GazeDirection {

        /** monocular gaze direction accounting for head pan/tilt and average eye direction, 0,0 is centered, limits +-1 */
        Point2D.Float gazeDirection = new Point2D.Float(0, 0);
        Point2D.Float leftEyeGazeDirection = new Point2D.Float();
        Point2D.Float rightEyeGazeDirection = new Point2D.Float();
        Point2D.Float[] eyeGazeDirections = {leftEyeGazeDirection, rightEyeGazeDirection};
        float vergence = 0; // 0 is infinity vergence, >0 is cross-eyed
        Point2D.Float headDirection = new Point2D.Float();
    }
    GazeDirection gazeDirection = new GazeDirection();

    public enum COM_PORT {

        COM1, COM2, COM3, COM4, COM5, COM6, COM7, COM8
    }
    private COM_PORT comPort = COM_PORT.valueOf(getString("comPort", COM_PORT.COM5.toString()));  // com5 chosen on tobi's virgin FTDI install
    private int baudRate = getInt("baudRate", 460800); // 460800, RTS=true for servo board for head
    boolean servosAvailable = false;

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

    boolean checkServos() {
        if (servosAvailable) {
            return true;
        }
        if (!serialPort.isOpen()) {
            try {
                int ret = serialPort.open(getComPort().toString(), getBaudRate());
                serialPort.setHardwareFlowControl(true);
            } catch (UnsupportedCommOperationException ex) {
                log.warning(ex.toString());
                return false;
            } catch (IOException ex) {
                log.warning(ex.toString());
                return false;
            }
        }
        servosAvailable = true;
        return true;
    }

    public void doShowGUI() {
        if (gui == null) {
            gui = new Head6DOF_GUI(this);
        }
        gui.setVisible(true);
    }

    public void doCenterGaze() {
        float[] f = new float[NSERVOS];
        Arrays.fill(f, .5f);
        try {
            setAllServoValues(f);
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }

    public void doReset() {
        try {
            serialPort.writeLn(CMD_RESET);// reset board
            Thread.sleep(200);
            serialPort.purgeInput();
            log.info("reset");
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
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
                serialPort.setHardwareFlowControl(true);
                serialPort.writeLn(CMD_RESET);// reset board
                Thread.sleep(200);
                serialPort.writeLn(CMD_CYCLE + "=40000"); // sets servo cycle time to 10ms; time unit is .25us
                Thread.sleep(200);
                serialPort.purgeInput();
                log.info("opened serial port " + serialPort);
                this.connected=true;
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        } else if (!connected && this.connected) {
            try {
                serialPort.close();
                log.info("disconnected serial port");
                this.connected=false;
            } catch (Exception ex) {
                log.warning(ex.toString());
            }

        }
        getSupport().firePropertyChange("connected", null, this.connected);
    }

    private int float2servo(float f) {
        return 4000 + (int) (f * 4000); // sets from 4000=.25us*4000=1000us=1ms to 2ms which is range for hobby servos
    }

    public void setServoValue(int servo, float value) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        servoValues[servo] = value;
        serialPort.writeLn(String.format("%s%d=%d", CMD_SERVO, servo, float2servo(value))); // eg !S3=6000
        serialPort.flushOutput();
        serialPort.purgeInput();
    }

    public void disableServo(int servo) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        serialPort.writeLn(String.format("%s%d=%d", CMD_SERVO, servo, 0)); // eg !S0=0
        serialPort.flushOutput();
        serialPort.purgeInput();
    }

    public int getNumServos() {
        return NSERVOS;
    }

    public void disableAllServos() throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        for (int i = 0; i < NSERVOS; i++) {
            disableServo(i);
        }
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
        servosAvailable = false;
        serialPort.close();
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
        pan=clip(pan,HEAD_LIMIT);
        setServoValue(HEAD_PAN, gaze2servo(pan));
        gazeDirection.headDirection.x = pan;
        tilt=clip(tilt,HEAD_LIMIT);
        setServoValue(HEAD_TILT, gaze2servo(tilt));
        gazeDirection.headDirection.y = tilt;
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
    final float VERGENCE_LIMIT = .2f;

    public void setVergence(float vergence) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        vergence = clip(vergence, VERGENCE_LIMIT);
        gazeDirection.vergence = vergence;
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
        setEyeGazeDirection(gazeDirection.gazeDirection.x, gazeDirection.gazeDirection.y);
    }

    public GazeDirection getGazeDirection() {
        return gazeDirection;
    }
}
