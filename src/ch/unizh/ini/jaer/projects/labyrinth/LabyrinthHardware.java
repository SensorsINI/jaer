/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import ch.unizh.ini.jaer.hardware.pantilt.*;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;

/**
 * This filter enables controlling the tracked labyrinth ball.
 * 
 * @author Tobi Delbruck
 */
public class LabyrinthHardware extends EventFilter2D implements  PanTiltInterface, PropertyChangeListener {

    public static String getDescription(){ return "Low level hardware interface for Labyrinth game";}
    private PanTilt panTiltHardware;
    private boolean jitterEnabled=getBoolean("jitterEnabled",false);
    private float jitterFreqHz=prefs().getFloat("jitterFreqHz",1);
    private float jitterAmplitude=prefs().getFloat("jitterAmplitude",.02f);
    private float panValue=prefs().getFloat("panValue",.5f);
   private float tiltValue=prefs().getFloat("tiltValue",.5f);
   private int panServoNumber=getInt("panServoNumber",1);
   private int tiltServoNumber=getInt("tiltServoNumber",2);
   private boolean invertPan=getBoolean("invertPan",false);
   private boolean invertTilt=getBoolean("invertTilt",false);
     private float panTiltLimit=getFloat("panTiltLimit",0.1f);

 
    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public LabyrinthHardware(AEChip chip) {
        super(chip);

        panTiltHardware = new PanTilt();
        panTiltHardware.setPanServoNumber(panServoNumber);
        panTiltHardware.setTiltServoNumber(tiltServoNumber);
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
        panTiltHardware.setJitterEnabled(jitterEnabled);
        panTiltHardware.setPanInverted(invertPan);
        panTiltHardware.setTiltInverted(invertTilt);

        String servo="Servos", control="Control";
        setPropertyTooltip("controlTilts","shows GUI for controlling table tilts with mouse");
        setPropertyTooltip("disableServos","disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("center","centers pan and tilt controls");
        
        setPropertyTooltip(servo,"jitterAmplitude","Jitter of pantilt amplitude for circular motion");
        setPropertyTooltip(servo,"jitterFreqHz","Jitter frequency in Hz of circular motion");
        setPropertyTooltip(servo,"jitterEnabled","enables servo jitter to produce microsaccadic movement");
        setPropertyTooltip(servo,"panServoNumber","servo channel for pan (0-3)");
        setPropertyTooltip(servo,"tiltServoNumber","servo channel for tilt (0-3)");
        setPropertyTooltip(servo,"tiltInverted","flips the tilt");
        setPropertyTooltip(servo,"panInverted","flips the pan");
        setPropertyTooltip(servo,"panTiltLimit","limits pan and tilt around 0.5 by this amount to protect hardware");
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        return in; // only handles control commands, no event processing
    }
 
    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        resetFilter();
    }


    synchronized public void doCenter(){
         if(panTiltHardware!=null && panTiltHardware.getServoInterface()!=null){
            try {
             panTiltHardware.setPanTiltValues(0.5f, 0.5f);
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    synchronized public void doDisableServos(){
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
        float old = panTiltHardware.getJitterAmplitude();
        getSupport().firePropertyChange("jitterAmplitude", jitterAmplitude, old);
        return old;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    @Override
    public void setJitterAmplitude(float jitterAmplitude) {
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
        putFloat("jitterAmplitude",jitterAmplitude);
    }

    @Override
    public float getJitterFreqHz() {
        if(panTiltHardware==null) return 0;
        return panTiltHardware.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        if(panTiltHardware==null) return;
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
        putFloat("jitterFreqHz",jitterFreqHz);
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
    synchronized public void startJitter() {
        getPanTiltHardware().startJitter();
    }

    @Override
    synchronized public void stopJitter() {
        getPanTiltHardware().stopJitter();
    }

    /** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
     */
    @Override
    synchronized public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        float[] old=getPanTiltHardware().getPanTiltValues();
        getPanTiltHardware().setPanTiltValues(pan, tilt);
        getSupport().firePropertyChange("panValue", old[0], panValue);
        getSupport().firePropertyChange("tiltValue",old[1], tiltValue);
        prefs().putFloat("PanTiltAimer.panValue",pan);
        prefs().putFloat("PanTiltAimer.tiltValue",tilt);
    }

    @Override
    public void setServoInterface(ServoInterface servo) {
       getPanTiltHardware().setServoInterface(servo);
    }

    @Override
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
    synchronized public void setJitterEnabled(boolean jitterEnabled) {
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
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet.");
    }




  

}
