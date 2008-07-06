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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates a pan tilt controller based on using SiLabsC8051F320_USBIO_ServoController.
 * Currently assumes that there is only one controller attached and that the pan and tilt servos are 
 * tied to the PAN and TILT servo output channels on the board.
 * @author tobi
 * @see #PAN
 * #see TILT
 */
public class PanTilt {

    private static Logger log = Logger.getLogger("PanTilt");
    SiLabsC8051F320_USBIO_ServoController servo;
    /** Servo output number on SiLabsC8051F320_USBIO_ServoController, 0 based. */
    public final int PAN = 1,  TILT = 2; // number of servo output on controller
    volatile boolean lockAcquired = false;
    java.util.Timer timer;
    private float pan, tilt;
    private float jitterAmplitude=.01f;
    private float jitterFreqHz=10f;

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
    
    /** Simultaneously sets pan and tilt values. The directions here depend on the servo polarities, which could vary.
     * These values apply to HiTec digital servos.
     * 
     * @param pan the pan value from 0 to 1 inclusive, 0.5f is the center position. 1 is full right.
     * @param tilt the tilt value from 0 to 1. 1 is full down.
     * @throws ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException
     */
    synchronized public void setPanTilt(float pan, float tilt) throws HardwareInterfaceException {
        checkServos();
        this.pan=pan;
        this.tilt=tilt; //efferent copy
        float[] lastValues = servo.getLastServoValues();
        lastValues[PAN] = pan;
        lastValues[TILT] = tilt;
//        for(int i=0;i<4;i++){
//            System.out.print(lastValues[i]+", ");
//        }
//        System.out.println("");
        servo.setAllServoValues(lastValues);
        setLaserOn(true);
    }

    /** A method can set this flag to tell other objects that the servo is "owned" */
    public void acquire() {
        lockAcquired = true;
    }

    /** Releases the "acquired" flag */
    public void release() {
        lockAcquired = false;
    }

    /** A method can check this to see if it can use the servo */
    public boolean isLockOwned() {
        return lockAcquired;
    }

    /** Returns the last value set, even if the servo interface is not functional. The servo could still be moving to this location. 
     * 
     * @return a float[] array with the 0 component being the pan value, and the 1 component being the tilt 
     * 
     */
    public float[] getPanTilt(){
        float[] r={pan,tilt};
        return r;
    }

    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
    }

    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    
    
    
    private class JittererTask extends TimerTask {

        int delayMs = 1000;
        float low = 0;
        float high = 1;
        Random r = new Random();
        float[] pantiltvalues;
        long startTime=System.currentTimeMillis();

        JittererTask(float[] ptv) {
            super();
            pantiltvalues=ptv;
        }

        public void run() {
            long t=System.currentTimeMillis()-startTime;
            double phase=Math.PI*2*(double)t/1000*jitterFreqHz;
            float dx=(float)(jitterAmplitude*Math.sin(phase));
            float dy=(float)(jitterAmplitude*Math.cos(phase));
            try {
                setPanTilt(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
            } catch (HardwareInterfaceException ex) {
            }
//            try {
//                setPanTilt(pantiltvalues[0]+(r.nextFloat()-0.5f)*JIT, pantiltvalues[1]+(r.nextFloat()-0.5f)*JIT);
//            } catch (HardwareInterfaceException ex) {
//            }
        }
    }

    /** Starts the servo jittering around its set position at a frequency of 50 Hz with an amplitude of 0.02f
     @eee #setJitterAmplitude
     */
    public void startJitter() {
        timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new JittererTask(getPanTilt()), 0, 20); // 40 ms delay
    }

    /** Stops the jittering */
    public void stopJitter() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    /** Hack to control laser pointer power through pin 2 opendrain pulldown pins (multiple to share laser
     * current of about 200mA
     * @param yes to turn on laser 
     */
      public  void setLaserOn(boolean yes){
        if(servo!=null){
            servo.setPort2(yes? 0:0xff);
        }
    }

}
