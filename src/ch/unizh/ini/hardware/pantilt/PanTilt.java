/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.hardware.pantilt;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoInterfaceFactory;
import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import java.util.Random;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Encapsulates a pan tilt controller based on using ServoController.
 * @author tobi
 */
public class PanTilt {

    private static Logger log = Logger.getLogger("PanTilt");
    SiLabsC8051F320_USBIO_ServoController servo;
    /** Servo output number on SiLabsC8051F320_USBIO_ServoController, 0 based. */
    public final int PAN = 1,  TILT = 2; // number of servo output on controller
    volatile boolean lockAcquired = false;
    java.util.Timer timer;
    private float pan, tilt;

    public PanTilt() {
    }

    private void checkServos() throws HardwareInterfaceException {
        if (servo == null) {
            try {
                servo = (SiLabsC8051F320_USBIO_ServoController) ServoInterfaceFactory.instance().getFirstAvailableInterface();
            } catch (ClassCastException cce) {
                throw new HardwareInterfaceException("Wrong type of interface: " + cce.getMessage());
            }
        }
        if (servo == null) {
            throw new HardwareInterfaceException("no servo controller found");
        }
        if (!servo.isOpen()) {
            servo.open();
        }
    }

//    public void setPan(float f) throws HardwareInterfaceException {
//        checkServos();
//        servo.setServoValue(PAN, f);
//    }
//
//    public void setTilt(float f) throws HardwareInterfaceException {
//        checkServos();
//        servo.setServoValue(TILT, f);
//    }
    synchronized public void setPanTilt(float pan, float tilt) throws HardwareInterfaceException {
        checkServos();
        this.pan=pan;
        this.tilt=tilt; //efferent copy
        float[] lastValues = servo.getLastServoValues();
        lastValues[PAN] = pan;
        lastValues[TILT] = tilt;
        servo.setAllServoValues(lastValues);
    }

    public void acquire() {
        lockAcquired = true;
    }

    public void release() {
        lockAcquired = false;
    }

    public boolean isLockOwned() {
        return lockAcquired;
    }

    public float[] getPanTilt(){
        float[] r={pan,tilt};
        return r;
    }
    
    class JittererTask extends TimerTask {

        int delayMs = 1000;
        float low = 0;
        float high = 1;
        Random r = new Random();
        float[] pantiltvalues;
        final float JIT=0.02f;

        JittererTask(float[] ptv) {
            super();
            pantiltvalues=ptv;
        }

        public void run() {
            try {
                setPanTilt(pantiltvalues[0]+(r.nextFloat()-0.5f)*JIT, pantiltvalues[1]+(r.nextFloat()-0.5f)*JIT);
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public void startJitter() {
        timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new JittererTask(getPanTilt()), 0, 40); // 40 ms delay
    }

    public void stopJitter() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
