/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import java.util.Random;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Encapsulates a pan tilt controller based on using SiLabsC8051F320_USBIO_ServoController.
 * Currently assumes that there is only one controller attached and that the pan and tilt servos are 
 * tied to the PAN and TILT servo output channels on the board.
 * Port 2 of the ServoUSB board is used to power a laser pointer that can be activated.
 * PanTilt directly controls the servo settings, but does not implement a calbration that maps from 
 * visual coordinates to pan tilt settings. To control the pan tilt to aim at a particular visual location
 * in the field of view of a silicon retina, see PanTiltTracker.
 * 
 * @author tobi
 * @see #PAN
 * @see #TILT
 * @see ch.unizh.ini.jaer.hardware.pantilt.PanTiltTracker
 */
public class PanTilt implements PanTiltInterface, LaserOnOffControl {

    private static Logger log = Logger.getLogger("PanTilt");
    ServoInterface servo;
    /** Servo output number on SiLabsC8051F320_USBIO_ServoController, 0 based. */
    public final int PAN = 1,  TILT = 2; // number of servo output on controller
    volatile boolean lockAcquired = false;
    java.util.Timer timer;
    private float pan, tilt;
    private float jitterAmplitude=.01f;
    private float jitterFreqHz=10f;

    public PanTilt() {
         Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                log.info("disabling laser");
                setLaserEnabled(false);
            }
        });
   }
    
    /** Constructs instance with previously constructed SiLabsC8051F320_USBIO_ServoController */
    public PanTilt(ServoInterface servo){
        this();
        this.servo=servo;
    }

    public void close(){
        if(getServoInterface()!=null) getServoInterface().close();
    }

    private void checkServos() throws HardwareInterfaceException {
        if (servo == null) {
            try {
                servo =  (ServoInterface)ServoInterfaceFactory.instance().getFirstAvailableInterface();
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
    
    /** Simultaneously sets pan and tilt values. The directions here depend on the servo polarities, which could vary.
     * These values apply to HiTec digital servos.
     * 
     * @param pan the pan value from 0 to 1 inclusive, 0.5f is the center position. 1 is full right.
     * @param tilt the tilt value from 0 to 1. 1 is full down.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException.
     If this exception is thrown, the interface should be closed. The next attempt to set the pan/tilt values will reopen
     the interface.
     * @see #PAN
     * @see #TILT
     */
    synchronized public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
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
        servo.setServoValue(PAN,pan);
        servo.setServoValue(TILT,tilt);
//        servo.setAllServoValues(lastValues);
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
    public float[] getPanTiltValues(){
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

    /** Sets the frequency of the jitter.
     * 
     * @param jitterFreqHz in Hz.
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
                setPanTiltValues(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
            } catch (HardwareInterfaceException ex) {
            }
//            try {
//                setPanTiltValues(pantiltvalues[0]+(r.nextFloat()-0.5f)*JIT, pantiltvalues[1]+(r.nextFloat()-0.5f)*JIT);
//            } catch (HardwareInterfaceException ex) {
//            }
        }
    }

    /** Starts the servo jittering around its set position at a frequency of 50 Hz with an amplitude of 0.02f
     @see #setJitterAmplitude
     */
    public void startJitter() {
        timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new JittererTask(getPanTiltValues()), 0, 20); // 40 ms delay
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

    public void setServoInterface(ServoInterface servo) {
        this.servo=servo;
    }

    public ServoInterface getServoInterface() {
        return servo;
    }

    public void setLaserEnabled(boolean yes) {
        setLaserOn(yes);
    }

}
