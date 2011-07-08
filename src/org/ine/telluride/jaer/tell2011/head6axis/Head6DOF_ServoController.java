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
    final int EYE_LEFT_PAN = 0, EYE_LEFT_TILT = 1, EYE_RIGHT_PAN = 2, EYE_RIGHT_TILT = 3, HEAD_PAN = 4, HEAD_TILT = 5;

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
    private int baudRate = getInt("baudRate", 4000000);
    boolean servosAvailable = false;

    public Head6DOF_ServoController(AEChip chip) {
        super(chip);
        serialPort = new HWP_UART();
        Arrays.fill(servoValues, .5f);
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
                serialPort.open(getComPort().toString(), getBaudRate());
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

    public void doDisconnect() {
        try {
            serialPort.close();
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }

    public void doConnect() {
        try {
            serialPort.open(comPort.toString(), baudRate);
            serialPort.writeLn("r");// reset board
            Thread.sleep(200);
            serialPort.writeLn("!C=40000"); // TODO ??
            Thread.sleep(200);
            serialPort.purgeInput();
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }
    final String S = "!s "; // set command header

    private int float2servo(float f) {
        return 10000 + (int) (f * 20000);
    }

    public void setServoValue(int servo, float value) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        servoValues[servo] = value;
        serialPort.writeLn(String.format("%s %d=%d", S, servo, float2servo(value)));
    }

    public void disableServo(int servo) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        serialPort.writeLn(String.format("%s %d=%d", S, servo, 0));
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
        setServoValue(HEAD_PAN, gaze2servo(pan));
        gazeDirection.headDirection.x = pan;
        setServoValue(HEAD_TILT, gaze2servo(tilt));
        gazeDirection.headDirection.y = tilt;
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
    }
    final float PAN_LIMIT = .5f, TILT_LIMIT = .5f;

    float clip(float in, float limit) {
        if (in < -limit) {
            in = -limit;
        }else if (in > limit) {
            in = limit;
        }
        return in;
    }

    public void setEyeGazeDirection(float pan, float tilt) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        pan=clip(pan, PAN_LIMIT);
        tilt=clip(tilt,TILT_LIMIT);
        setServoValue(EYE_LEFT_PAN, gaze2servo(pan + gazeDirection.vergence));
        gazeDirection.leftEyeGazeDirection.x = pan;
        setServoValue(EYE_RIGHT_PAN, gaze2servo(pan - gazeDirection.vergence));
        gazeDirection.rightEyeGazeDirection.x = pan;
        setServoValue(EYE_LEFT_TILT, gaze2servo(tilt));
        gazeDirection.leftEyeGazeDirection.y = tilt;
        setServoValue(EYE_RIGHT_TILT, gaze2servo(tilt));
        gazeDirection.rightEyeGazeDirection.y = tilt;
        gazeDirection.gazeDirection.setLocation(pan, tilt);
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
    }
    final float VERGENCE_LIMIT = .2f;

    public void setVergence(float vergence) throws HardwareInterfaceException, UnsupportedEncodingException, IOException {
        vergence=clip(vergence,VERGENCE_LIMIT);
        gazeDirection.vergence = vergence;
        getSupport().firePropertyChange("gazeDirection", null, gazeDirection);
        setEyeGazeDirection(gazeDirection.gazeDirection.x, gazeDirection.gazeDirection.y);
    }

    public GazeDirection getGazeDirection() {
        return gazeDirection;
    }
}
