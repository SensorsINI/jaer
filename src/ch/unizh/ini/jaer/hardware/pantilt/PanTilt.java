/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import java.util.Random;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;

/**
 * Encapsulates a pan tilt controller based on using SiLabsC8051F320_USBIO_ServoController.
 * Currently assumes that there is only one controller attached and that the pan and tilt servos are 
 * tied to the panServoNumber and DEFAULT_TILT_SERVO servo output channels on the board.
 * Port 2 of the ServoUSB board is used to power a laser pointer that can be activated.
 * PanTilt directly controls the servo settings, but does not implement a calbration that maps from 
 * visual coordinates to pan tilt settings. To control the pan tilt to aim at a particular visual location
 * in the field of view of a silicon retina, see PanTiltTracker.
 * 
 * @author tobi
 * @see #DEFAULT_PAN_SERVO
 * @see #DEFAULT_TILT_SERVO
 * @see ch.unizh.ini.jaer.hardware.pantilt.PanTiltTracker
 */
public class PanTilt implements PanTiltInterface, LaserOnOffControl {

    private static Logger log = Logger.getLogger("PanTilt");
    ServoInterface servo;
    /** Servo output number on SiLabsC8051F320_USBIO_ServoController, 0 based. */
    public final int DEFAULT_PAN_SERVO = 1,  DEFAULT_TILT_SERVO = 2; // number of servo output on controller
    volatile boolean lockAcquired = false;
    java.util.Timer timer;
    private float pan=.5f, tilt=.5f;
    private float previousPan=pan, previousTilt=tilt;
    
    private float jitterAmplitude=.01f;
    private float jitterFreqHz=10f;
    private boolean jitterEnabled=false;
    private boolean panInverted=false, tiltInverted=false;
    private int panServoNumber=DEFAULT_PAN_SERVO, tiltServoNumber=DEFAULT_TILT_SERVO;


    public PanTilt() {
         Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
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

    private int checkServoCount=0;
    private int CHECK_SERVO_INTERVAL=100;
    
    private void checkServos() throws HardwareInterfaceException {
        if (checkServoCount++ % CHECK_SERVO_INTERVAL == 0) {
            if (servo == null) {
                try {
                    servo = (ServoInterface) ServoInterfaceFactory.instance().getFirstAvailableInterface();
                } catch (ClassCastException cce) {
                    throw new HardwareInterfaceException("Wrong type of interface: " + cce.getMessage());
                }
            }
        }
        if (servo == null) {
            throw new HardwareInterfaceException("no servo controller found");
        }
        if (!servo.isOpen()) {
            servo.open();
            float pwmFreq=servo.setServoPWMFrequencyHz(180);
            log.log(Level.INFO, "opened servo interface and set PWM frequency to {0}", pwmFreq);
        }
    }
    
    /** Simultaneously sets pan and tilt values. The directions here depend on the servo polarities, which could vary.
     * These values apply to HiTec digital servos.
     * 
     * @param newPan the pan value from 0 to 1 inclusive, 0.5f is the center position. 1 is full right.
     * @param newTilt the tilt value from 0 to 1. 1 is full down.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException.
     If this exception is thrown, the interface should be closed. The next attempt to set the pan/tilt values will reopen
     the interface.
     * @see #panServoNumber
     * @see #tiltServoNumber
     */
    @Override
    synchronized public void setPanTiltValues(float newPan, float newTilt) throws HardwareInterfaceException {
        checkServos();
        previousPan=this.pan;
        previousTilt=this.tilt;
         this.pan=newPan;
        this.tilt=newTilt; //efferent copy
        if(panInverted)newPan=1-newPan;
        if(tiltInverted)newTilt=1-newTilt;
//       float[] lastValues = servo.getLastServoValues();
//        lastValues[panServoNumber] = pan;
//        lastValues[tiltServoNumber] = tilt;
//        for(int i=0;i<4;i++){
//            System.out.print(lastValues[i]+", ");
//        }
//        System.out.println("");
        servo.setServoValue(panServoNumber,newPan);
        servo.setServoValue(tiltServoNumber,newTilt);
//        servo.setAllServoValues(lastValues);
        setLaserOn(true);
    }

    /** A method can set this flag to tell other objects that the servo is "owned" */
    @Override
    public void acquire() {
        lockAcquired = true;
    }

    /** Releases the "acquired" flag */
    @Override
    public void release() {
        lockAcquired = false;
    }

    /** A method can check this to see if it can use the servo */
    @Override
    public boolean isLockOwned() {
        return lockAcquired;
    }

    /** Returns the last value set, even if the servo interface is not functional. 
     * The servo could still be moving to this location. 
     * 
     * @return a float[] array with the 0 component being the pan value, and the 1 component being the tilt 
     * 
     */
    @Override
    public float[] getPanTiltValues(){
        float[] r={pan,tilt};
        return r;
    }
    
    /** Returns previous pan and tilt values. */
    public float[] getPreviousPanTiltValues(){
        float[] r={previousPan,previousTilt};
        return r;
    }
    
    /** Returns last change of pan and tilt values.*/
       public float[] getPreviousPanTiltChange(){
        float[] r={pan-previousPan,tilt-previousTilt};
        return r;
    }

    @Override
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    @Override
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
    }

    @Override
    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /** Sets the frequency of the jitter.
     * 
     * @param jitterFreqHz in Hz.
     */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }

    /**
     * @return the panInverted
     */
    public boolean isPanInverted() {
        return panInverted;
    }

    /**
     * @param panInverted the panInverted to set
     */
    public void setPanInverted(boolean panInverted) {
        this.panInverted = panInverted;
    }

    /**
     * @return the tiltInverted
     */
    public boolean isTiltInverted() {
        return tiltInverted;
    }

    /**
     * @param tiltInverted the tiltInverted to set
     */
    public void setTiltInverted(boolean tiltInverted) {
        this.tiltInverted = tiltInverted;
    }

    /**
     * @return the panServoNumber
     */
    public int getPanServoNumber() {
        return panServoNumber;
    }

    /**
     * @param panServoNumber the panServoNumber to set
     */
    public void setPanServoNumber(int panServoNumber) {
        this.panServoNumber = panServoNumber;
    }

    /**
     * @return the tiltServoNumber
     */
    public int getTiltServoNumber() {
        return tiltServoNumber;
    }

    /**
     * @param tiltServoNumber the tiltServoNumber to set
     */
    public void setTiltServoNumber(int tiltServoNumber) {
        this.tiltServoNumber = tiltServoNumber;
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

        @Override
        public void run() {
            long t=System.currentTimeMillis()-startTime;
            double phase=Math.PI*2*(double)t/1000*jitterFreqHz;
            float dx=(float)(jitterAmplitude*Math.sin(phase));
            float dy=(float)(jitterAmplitude*Math.cos(phase));
            try {
                setPanTiltValues(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
//                setPanTiltValues(pantiltvalues[0] + Math.signum(dx)*jitterAmplitude, pantiltvalues[1] + Math.signum(dy)*jitterAmplitude);
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
    @Override
    public void startJitter() {
        if(timer!=null) stopJitter(); //  running, must stop to get new position correct
        timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new JittererTask(getPanTiltValues()), 0, 20); // 40 ms delay
    }

    /** Stops the jittering */
    @Override
    public void stopJitter() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * @return the jitterEnabled
     */
    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    /**
     * @param jitterEnabled the jitterEnabled to set
     */
    public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
        if(jitterEnabled) startJitter(); else stopJitter();
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
