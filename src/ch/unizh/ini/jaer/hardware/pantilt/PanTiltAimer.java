/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.hardware.pantilt;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;

/**
 * This filter enables aiming the pan-tilt using a GUI and allows controlling jitter of the pan-tilt when not moving it.
 * 
 * @author Tobi Delbruck
 */
@Description("Allows control of pan-tilt using a panel to aim it and parameters to control the jitter")
public class PanTiltAimer extends EventFilter2D implements  PanTiltInterface, LaserOnOffControl, PropertyChangeListener {

    private PanTilt panTiltHardware;
    private PanTiltAimerGUI gui;
    private boolean jitterEnabled=getBoolean("jitterEnabled",false);
    private float jitterFreqHz=getFloat("jitterFreqHz",1);
    private float jitterAmplitude=getFloat("jitterAmplitude",.02f);
    private float panValue=getFloat("panValue",.5f);
   private float tiltValue=getFloat("tiltValue",.5f);
   private int panServoNumber=getInt("panServoNumber",1);
   private int tiltServoNumber=getInt("tiltServoNumber",2);
   private boolean invertPan=getBoolean("invertPan",false);
   private boolean invertTilt=getBoolean("invertTilt",false);
     private float limitOfPan=getFloat("limitOfPan",0.25f);
     private float limitOfTilt=getFloat("limitOfTilt",0.25f);
     private PropertyChangeSupport support=new PropertyChangeSupport(this);

    Trajectory trajectory;

 

    class Trajectory extends ArrayList<TrajectoryPoint>{
        void add(long millis, float pan, float tilt){
            add(new TrajectoryPoint(millis, pan, tilt));
        }
    }

    class TrajectoryPoint{
        long timeMillis;
        float pan, tilt;

        public TrajectoryPoint(long timeMillis, float pan, float tilt) {
            this.timeMillis = timeMillis;
            this.pan=pan;
            this.tilt=tilt;
        }
    }
    
    private boolean recordingEnabled=false; // not used

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName()==Message.SetRecordingEnabled.name()){
            recordingEnabled=(Boolean)evt.getNewValue();
        }else if(evt.getPropertyName()==Message.AbortRecording.name()){
            recordingEnabled=false;
            if(trajectory!=null) trajectory.clear();
        } else if(evt.getPropertyName()==Message.ClearRecording.name()){
            if(trajectory!=null) trajectory.clear();
        } else if(evt.getPropertyName()==Message.PanTiltSet.name()){
            support.firePropertyChange(evt);
        }
    }

   public enum Message {

        AbortRecording,
        ClearRecording,
        SetRecordingEnabled,
        PanTiltSet
    }

    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public PanTiltAimer(AEChip chip) {
        super(chip);
        panTiltHardware = new PanTilt();
        panTiltHardware.setPanServoNumber(panServoNumber);
        panTiltHardware.setTiltServoNumber(tiltServoNumber);
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
        panTiltHardware.setJitterEnabled(jitterEnabled);
        panTiltHardware.setPanInverted(invertPan);
        panTiltHardware.setTiltInverted(invertTilt);

        
        setPropertyTooltip("jitterAmplitude","Jitter of pantilt amplitude for circular motion");
        setPropertyTooltip("jitterFreqHz","Jitter frequency in Hz of circular motion");
        setPropertyTooltip("doAim","shows GUI for aiming pan-tilt");
        setPropertyTooltip("doDisableServos","disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("jitterEnabled","enables servo jitter to produce microsaccadic movement");
        setPropertyTooltip("panServoNumber","servo channel for pan (0-3)");
        setPropertyTooltip("tiltServoNumber","servo channel for tilt (0-3)");
        setPropertyTooltip("tiltInverted","flips the tilt");
        setPropertyTooltip("panInverted","flips the pan");
        setPropertyTooltip("limitOfPan","limits pan around 0.5 by this amount to protect hardware");
        setPropertyTooltip("limitOfTilt","limits tilt around 0.5 by this amount to protect hardware");
        setPropertyTooltip("center","centers pan and tilt");
        setPropertyTooltip("disableServos","disables servo PWM output. Servos should relax but digital servos may store last value and hold it.");
        setPropertyTooltip("aim","show GUI for controlling pan and tilt");
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }
 
    @Override
    public void resetFilter() {
        panTiltHardware.close();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }


    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doAim() {
        if (getGui() == null) {
            gui = new PanTiltAimerGUI(panTiltHardware);
            getGui().getSupport().addPropertyChangeListener(this);
            getGui().setPanTiltLimit(limitOfPan, limitOfTilt);
        }
        getGui().setVisible(true);
    }

    public void doCenter(){
         if(panTiltHardware!=null && panTiltHardware.getServoInterface()!=null){
            try {
             panTiltHardware.setPanTiltValues(0.5f, 0.5f);
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public void doDisableServos(){
        if(panTiltHardware!=null && panTiltHardware.getServoInterface()!=null){
            try {
             panTiltHardware.stopJitter();
               panTiltHardware.getServoInterface().disableAllServos();
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public PanTilt getPanTiltHardware() {
        return panTiltHardware;
    }

    public void setPanTiltHardware(PanTilt panTilt) {
        this.panTiltHardware=panTilt;
    }
    
    @Override
    public float getJitterAmplitude() {
        return jitterAmplitude;
//        float old = panTiltHardware.getJitterAmplitude();
//        getSupport().firePropertyChange("jitterAmplitude", jitterAmplitude, old);
//        return old;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    @Override
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude=jitterAmplitude;
        putFloat("jitterAmplitude",jitterAmplitude);
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
    }

    @Override
    public float getJitterFreqHz() {
        return jitterFreqHz;
//        if(panTiltHardware==null) return 0;
//        return panTiltHardware.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz=jitterFreqHz;
        putFloat("jitterFreqHz",jitterFreqHz);
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
    }

    @Override
    public void acquire() {
        getPanTiltHardware().acquire();
    }

    @Override
    public float[] getPanTiltValues() {
        return getPanTiltHardware().getPanTiltValues();
    }

    @Override
    public boolean isLockOwned() {
        return getPanTiltHardware().isLockOwned();
    }

    @Override
    public void release() {
        getPanTiltHardware().release();
    }

    @Override
    public void startJitter() {
        getPanTiltHardware().startJitter();
    }

    @Override
    public void stopJitter() {
        getPanTiltHardware().stopJitter();
    }

    /** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
     */
    @Override
    public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        float[] old=getPanTiltHardware().getPanTiltValues();
        getPanTiltHardware().setPanTiltValues(pan, tilt);
        getSupport().firePropertyChange("panValue", old[0], panValue);
        getSupport().firePropertyChange("tiltValue",old[1], tiltValue);
        putFloat("panValue",pan);
        putFloat("tiltValue",tilt);
    }

    @Override
    public void setServoInterface(ServoInterface servo) {
       getPanTiltHardware().setServoInterface(servo);
    }

    @Override
    public ServoInterface getServoInterface() {
        return getPanTiltHardware().getServoInterface();
    }

    @Override
    public void setLaserEnabled(boolean yes) {
        getPanTiltHardware().setLaserEnabled(yes);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            panTiltHardware.setPanServoNumber(panServoNumber);
            panTiltHardware.setTiltServoNumber(tiltServoNumber);
            panTiltHardware.setJitterAmplitude(jitterAmplitude);
            panTiltHardware.setJitterFreqHz(jitterFreqHz);
            panTiltHardware.setJitterEnabled(jitterEnabled);
            panTiltHardware.setPanInverted(invertPan);
            panTiltHardware.setTiltInverted(invertTilt);
        } else {
            try {
                panTiltHardware.stopJitter();
                panTiltHardware.getServoInterface().disableAllServos();
                panTiltHardware.close();
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
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
        putBoolean("jitterEnabled", jitterEnabled);
        panTiltHardware.setJitterEnabled(jitterEnabled);
    }

    public void setPanServoNumber(int panServoNumber) {
        if(panServoNumber<0)panServoNumber=0; else if(panServoNumber>3)panServoNumber=3;
        panTiltHardware.setPanServoNumber(panServoNumber);
        putInt("panServoNumber",panServoNumber);
    }


    public void setTiltServoNumber(int tiltServoNumber) {
        if(tiltServoNumber<0) tiltServoNumber=0; else if(tiltServoNumber>3) tiltServoNumber=3;
        panTiltHardware.setTiltServoNumber(tiltServoNumber);
        putInt("tiltServoNumber",tiltServoNumber);
    }

    public void setTiltInverted(boolean tiltInverted) {
        panTiltHardware.setTiltInverted(tiltInverted);
        putBoolean("invertTilt",tiltInverted);
    }

   public void setPanInverted(boolean panInverted) {
        panTiltHardware.setPanInverted(panInverted);
        putBoolean("invertPan",panInverted);
   }

    public boolean isTiltInverted() {
        return panTiltHardware.isTiltInverted();
    }

    public boolean isPanInverted() {
        return panTiltHardware.isPanInverted();
    }

    public int getTiltServoNumber() {
        return panTiltHardware.getTiltServoNumber();
    }

    public int getPanServoNumber() {
        return panTiltHardware.getPanServoNumber();
    }


    /**
     * @return the panTiltLimit
     */
    public float getLimitOfPan() {
        return limitOfPan;
    }

    /**
     * @param panTiltLimit the panTiltLimit to set
     */
    public void setLimitOfPan(float panTiltLimit) {
        if(panTiltLimit<0) panTiltLimit=0; else if(panTiltLimit>0.5f)panTiltLimit=0.5f;
        this.limitOfPan = panTiltLimit;
        putFloat("limitOfPan",panTiltLimit);
        if(getGui()!=null){
            getGui().setPanTiltLimit(limitOfPan,limitOfTilt);
        }

    }
    /**
     * @return the panTiltLimit
     */
    public float getLimitOfTilt() {
        return limitOfTilt;
    }

    /**
     * @param panTiltLimit the panTiltLimit to set
     */
    public void setLimitOfTilt(float panTiltLimit) {
        if(panTiltLimit<0) panTiltLimit=0; else if(panTiltLimit>0.5f)panTiltLimit=0.5f;
        this.limitOfTilt = panTiltLimit;
        putFloat("limitOfTilt",panTiltLimit);
        if(getGui()!=null){
            getGui().setPanTiltLimit(limitOfPan,limitOfTilt);
        }

    }
   /**
     * @return the gui
     */
    public PanTiltAimerGUI getGui() {
        return gui;
    }

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }



}
