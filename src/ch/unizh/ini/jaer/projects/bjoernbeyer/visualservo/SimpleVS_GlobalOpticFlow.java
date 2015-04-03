
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import ch.unizh.ini.jaer.hardware.pantilt.PanTiltAimerGUI;
import ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration.CalibrationPanTiltScreen;
import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.label.DvsDirectionSelectiveFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.Vector2D;

/**
 *
 * @author Bjoern
 */
@Description("Moves the DVS towards a target based on the global averaged motion estimation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_GlobalOpticFlow extends EventFilter2D implements FrameAnnotater, SimpleVStrackerInterface {
    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    private float   ThreshDir      = getFloat("threshDir", 10f);
    private float   mixingFactor   = getFloat("mixingFactor",0.1f);
    private boolean TrackerEnabled = getBoolean("trackerEnabled", false);
    private final PanTilt panTilt;
    private final CalibrationPanTiltScreen retinaPTCalib;
    
    private final Vector2D velocityPPS = new Vector2D(),
                           smoothVelocityPPS = new Vector2D(),
                           ptSpeedPerSecond = new Vector2D();
    private final float[] retChange = new float[3];
    private float[] curPos;
    
    
    DvsDirectionSelectiveFilter DirFilter;
    
    public SimpleVS_GlobalOpticFlow(AEChip chip) {
        super(chip);
        
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        DirFilter = new DvsDirectionSelectiveFilter(chip);
            DirFilter.setAnnotationEnabled(false);
            DirFilter.setShowRawInputEnabled(false);
            DirFilter.setShowGlobalEnabled(true);
        panTilt = PanTilt.getInstance(0);
            panTilt.setLimitOfPan(retinaPTCalib.getLimitOfPan());
            panTilt.setLimitOfTilt(retinaPTCalib.getLimitOfTilt());
            panTilt.setLinearSpeedEnabled(true); //we need the speed to be linear so that we are able to set a target speed instead of a fraction of the distance that should be covered.
        
        setEnclosedFilter(DirFilter);

        setPropertyTooltip("threshDir", "Threshold when a global motion should be taken into account");
        setPropertyTooltip("mixingFactor", "mixing factor for taking older velocities into account");
        setPropertyTooltip("trackerEnabled", "Move the PanTilt to the target");
        setPropertyTooltip("switchTracking","switches the tracking of the pantilt.");
    }
    
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        
        DirFilter.filterPacket(in);
        
        velocityPPS.setLocation(DirFilter.getTranslationVector());

        if(velocityPPS.length() > getThreshDir()) { //distance to origin gives absolut value which needs to be larger threshold for the direction to register
            //smoothing the velocity with a mixing factor from the last averaged value
            smoothVelocityPPS.mult(getMixingFactor());
            smoothVelocityPPS.addFraction(velocityPPS, (1-getMixingFactor()));
            //As the retina-PanTilt calibration is a differential calibration we
            // need to give it the change vector in retinal coordinates and it will
            // give the corresponding change vector in panTilt coordinates.
            // We want to set the maximum Speed the panTilt can move to the speed
            // in panTiltPerSecond and set the target far in the direction of the 
            // global opticFlow velocity. This should make the panTilt move with
            // the speed of the optic flow in the direction of the optic flow
            // Hence if a object where to start in the center of the retina and
            // where the only thing visible and opticflow estimation would be
            // perfect it would stay centered.
            retChange[0] = -smoothVelocityPPS.x;
            retChange[1] = -smoothVelocityPPS.y;
            retChange[2] = 0f;
            
            //calculate the speed of the optic flow in panTilt Value change per Second
            // by transforming the detected retinal optic flow velocity with the 
            // calibration.
            ptSpeedPerSecond.setLocation(retinaPTCalib.makeTransform(retChange)); 
            if(isTrackerEnabled()) {
                //By dividing the length with the update frequency of the panTilt 
                panTilt.setMaxMovePerUpdate((float) ptSpeedPerSecond.length()/panTilt.getMoveUpdateFreqHz());
                //We want the target to be far out into the direction of the 
                // optic flow. Hence we just set the speed very high here as 
                // it is in the direction of the flow. For the next event it will
                // be reset again.
                ptSpeedPerSecond.unify();
                ptSpeedPerSecond.mult(1000);
                curPos = panTilt.getPanTiltValues();
                panTilt.setTarget(curPos[0]+ptSpeedPerSecond.x, curPos[1]+ptSpeedPerSecond.y);
            }
        }
        return in;
    }
    
    @Override public void resetFilter() {
        DirFilter.resetFilter();
    }

    @Override public void initFilter() {
        resetFilter();
    }

    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;

        DirFilter.annotate(drawable);
     }
    
    public void doAim() {
        PanTiltAimerGUI aimerGui = new PanTiltAimerGUI(PanTilt.getInstance(0));
        aimerGui.setVisible(true);
    }
    
    /** Switch the tracking mode
     * Sets the tracking mode to the opposite of the current mode */
    public void doSwitchTracking() {
        setTrackerEnabled(!isTrackerEnabled());
    }

    
    @Override public void doCenterPT() {
        setTrackerEnabled(false);
        panTilt.setTarget(.5f, .5f);
        System.out.println(panTilt.getMaxMovePerUpdate());
    }
    
    public void doCheckCalibration() {
        if(!retinaPTCalib.isCalibrated()){
            System.out.println("No calibration found!");
        } else {
            retinaPTCalib.displayCalibration(10);
        }
    }
    
    @Override public void doDisableServos() {
        setTrackerEnabled(false);
        try {
            panTilt.disableAllServos();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
    }
    
    @Override public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FactorDec--">
    public float getMixingFactor(){
        return mixingFactor;
    }
    
    public void setMixingFactor(float FactorDec) {
        putFloat("mixingFactor",FactorDec);
        float OldValue = this.mixingFactor;
        this.mixingFactor = FactorDec;
        support.firePropertyChange("mixingFactor",OldValue,FactorDec);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --ThreshDir--">
    public float getThreshDir(){
        return ThreshDir;
    }
    
    public void setThreshDir(float TreshDir) {
        putFloat("threshDir",ThreshDir);
        float OldValue = this.ThreshDir;
        this.ThreshDir = TreshDir;
        support.firePropertyChange("threshDir",OldValue,ThreshDir);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="checker-setter for --TrackerEnabled--">
    @Override public boolean isTrackerEnabled() {
        return TrackerEnabled;
    }

    /**
     * @param EnableTracker
     */
    @Override public void setTrackerEnabled(boolean EnableTracker) {
        putBoolean("trackerEnabled", EnableTracker);
        boolean OldValue = this.TrackerEnabled;
        this.TrackerEnabled = EnableTracker;
        support.firePropertyChange("trackerEnabled",OldValue,EnableTracker);
    }
    // </editor-fold>  
}
