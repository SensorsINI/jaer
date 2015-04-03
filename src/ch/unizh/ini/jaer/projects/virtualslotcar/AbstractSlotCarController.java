/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Base class for slot car controllers where all methods are Unsupported.
 *
 * @author tobi
 */
abstract public class AbstractSlotCarController extends EventFilter2D implements FrameAnnotater{

    private boolean loggingEnabled=false;
    private boolean textEnabled=getBoolean("textEnabled", true);

    public AbstractSlotCarController(AEChip chip) {
        super(chip);
        setPropertyTooltip("loggingEnabled","enables logging of state of this controller to log file in startup folder");
        setPropertyTooltip("textEnabled","enables rendering of laptimer and other text annotation");
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

    public void annotate(GLAutoDrawable drawable) {
    }

      /** Computes the control signal given the car tracker and the track model.
     *
     * @param tracker
     * @param track
     * @return the throttle setting ranging from 0 to 1.
     */
    abstract public ThrottleBrake computeControl(CarTrackerInterface tracker, SlotcarTrack track);
    

   /** Returns the last computed throttle setting.
     *
     * @return the throttle setting.
     */
    abstract public  ThrottleBrake getThrottle();

     /** Implement this method to return a string logging the state of the controller, e.g. throttle, measured speed, and curvature.
     *
     * @return string to log, by default the empty string.
     */
    public String logControllerState() {
        return "";
    }

     /** Returns a string that says what are the contents of the log, e.g. throttle, desired speed, measured speed.
     *
     * @return the string description of the log contents - by default empty
     */
    public String logContents(){
        return "";
    }

    /**
     * @return the loggingEnabled
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @param loggingEnabled the loggingEnabled to set
     */
    public void setLoggingEnabled(boolean loggingEnabled) {
        boolean old=this.loggingEnabled;
        this.loggingEnabled = loggingEnabled;
        getSupport().firePropertyChange("loggingEnabled",old, loggingEnabled);
    }

    /**
     * @return the textEnabled
     */
    public boolean isTextEnabled() {
        return textEnabled;
    }

    /**
     * @param textEnabled the textEnabled to set
     */
    public void setTextEnabled(boolean textEnabled) {
        this.textEnabled = textEnabled;
    }

}
