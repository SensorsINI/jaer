/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import ch.unizh.ini.jaer.hardware.pantilt.*;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;

/**
 * This filter enables controlling the tracked labyrinth ball.
 * 
 * @author Tobi Delbruck
 */
public class LabyrinthBallController extends EventFilter2D implements  PanTiltInterface, PropertyChangeListener {

    public static final String getDescription(){ return "Allows control of pan-tilt using a panel to aim it and parameters to control the jitter";}
    private PanTilt panTiltHardware;
    private LabyrinthBallControllerGUI gui;
    private boolean jitterEnabled=getBoolean("jitterEnabled",false);
    private float jitterFreqHz=prefs().getFloat("jitterFreqHz",1);
    private float jitterAmplitude=prefs().getFloat("jitterAmplitude",.02f);
    private float panValue=prefs().getFloat("panValue",.5f);
   private float tiltValue=prefs().getFloat("tiltValue",.5f);
   private int panServoNumber=getInt("panServoNumber",1);
   private int tiltServoNumber=getInt("tiltServoNumber",2);
   private boolean invertPan=getBoolean("invertPan",false);
   private boolean invertTilt=getBoolean("invertTilt",false);
     private boolean recordingEnabled=false;
     private float panTiltLimit=getFloat("panTiltLimit",0.25f);

     LabyrinthBallTracker tracker=null;

     private FilterChain filterChain;

 
    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public LabyrinthBallController(AEChip chip) {
        super(chip);

        filterChain=new FilterChain(chip);
        filterChain.add((tracker=new LabyrinthBallTracker(chip)));
        setEnclosedFilterChain(filterChain);

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
        setPropertyTooltip("panTiltLimit","limits pan and tilt around 0.5 by this amount to protect hardware");
        setPropertyTooltip("center","centers pan and tilt controls");
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out=getEnclosedFilterChain().filterPacket(in);
        
        return out;
    }
 
    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        resetFilter();
    }


    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doAim() {
        if (gui == null) {
            gui = new LabyrinthBallControllerGUI(panTiltHardware);
            gui.addPropertyChangeListener(this);
            gui.setPanTiltLimit(panTiltLimit);

        }
        gui.setVisible(true);
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
    
    public float getJitterAmplitude() {
        float old = panTiltHardware.getJitterAmplitude();
        getSupport().firePropertyChange("jitterAmplitude", jitterAmplitude, old);
        return old;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
        putFloat("jitterAmplitude",jitterAmplitude);
    }

    public float getJitterFreqHz() {
        if(panTiltHardware==null) return 0;
        return panTiltHardware.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
        putFloat("jitterFreqHz",jitterFreqHz);
    }

    public void acquire() {
        getPanTiltHardware().acquire();
    }

    public float[] getPanTiltValues() {
        return getPanTiltHardware().getPanTiltValues();
    }

    public boolean isLockOwned() {
        return getPanTiltHardware().isLockOwned();
    }

    public void release() {
        getPanTiltHardware().release();
    }

    public void startJitter() {
        getPanTiltHardware().startJitter();
    }

    public void stopJitter() {
        getPanTiltHardware().stopJitter();
    }

    /** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
     */
    public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        float[] old=getPanTiltHardware().getPanTiltValues();
        getPanTiltHardware().setPanTiltValues(pan, tilt);
        getSupport().firePropertyChange("panValue", old[0], panValue);
        getSupport().firePropertyChange("tiltValue",old[1], tiltValue);
        prefs().putFloat("PanTiltAimer.panValue",pan);
        prefs().putFloat("PanTiltAimer.tiltValue",tilt);
    }

    public void setServoInterface(ServoInterface servo) {
       getPanTiltHardware().setServoInterface(servo);
    }

    public ServoInterface getServoInterface() {
        return getPanTiltHardware().getServoInterface();
    }

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
                if(panTiltHardware.getServoInterface()!=null) panTiltHardware.getServoInterface().disableAllServos();
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
    public float getPanTiltLimit() {
        return panTiltLimit;
    }

    /**
     * @param panTiltLimit the panTiltLimit to set
     */
    public void setPanTiltLimit(float panTiltLimit) {
        if(panTiltLimit<0) panTiltLimit=0; else if(panTiltLimit>0.5f)panTiltLimit=0.5f;
        this.panTiltLimit = panTiltLimit;
        putFloat("panTiltLimit",panTiltLimit);
        if(gui!=null){
            gui.setPanTiltLimit(panTiltLimit);
        }

    }




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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName()==Message.SetRecordingEnabled.name()){
            recordingEnabled=(Boolean)evt.getNewValue();
        }else if(evt.getPropertyName()==Message.AbortRecording.name()){
            recordingEnabled=false;
            trajectory.clear();
        } else if(evt.getPropertyName()==Message.ClearRecording.name()){
            trajectory.clear();
        }
    }

   public enum Message {

        AbortRecording,
        ClearRecording,
        SetRecordingEnabled
    }


}
