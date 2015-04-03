/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
/**
 * Controls speed of car to a constant chosen level by feedback from tracker speed onto throttle.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SimpleSpeedController extends AbstractSlotCarController implements SlotCarControllerInterface, FrameAnnotater {

    private ThrottleBrake throttle=new ThrottleBrake(); // last output throttle setting
    private float desiredSpeedPPS=prefs().getFloat("SimpleSpeedController.desiredSpeedPPS",0); // desired speed of card in pixels per second
    private float defaultThrottle=prefs().getFloat("SimpleSpeedController.defaultThrottle",.1f); // default throttle setting if no car is detected
    private float gain=prefs().getFloat("SimpleSpeedController.gain", 1e-5f); // gain of proportional controller
    private float measuredSpeedPPS; // the last measured speed
    private boolean useModelEnabled=prefs().getBoolean("SimpleSpeedController.useModelEnabled", false);
    private SpeedVsThrottleModel throttleModel=new SpeedVsThrottleModel(); // used for FF control when useModelEnabled=true


    SlotCarRacer racer;
 
    public SimpleSpeedController(AEChip chip) {
        super(chip);
        setPropertyTooltip("desiredSpeedPPS", "desired speed of card in pixels per second");
        setPropertyTooltip("defaultThrottle", "default throttle setting if no car is detected");
        setPropertyTooltip("gain", "gain of proportional controller");
        setPropertyTooltip("useModelEnabled", "set true to use feedforward throttle, false to use feedback control from error signal desired-measured speed");
        final String ff="Feedforward throttle model";
        setPropertyTooltip(ff, "thresholdThrottle", "threshold throttle for feedforward throttle model");
        setPropertyTooltip(ff, "fullThrottle",  "full throttle for feedforward throttle model");
    }

    @Override
    public ThrottleBrake computeControl(CarTrackerInterface tracker, SlotcarTrack track) {
        ClusterInterface car = tracker.findCarCluster();
        if (useModelEnabled) {
            throttle.throttle=throttleModel.computeThrottle(desiredSpeedPPS);
        } else {
            if (car == null) {
                throttle.throttle=defaultThrottle;
                return throttle;
            } else {
                measuredSpeedPPS = (float) car.getSpeedPPS();
                float error = measuredSpeedPPS - desiredSpeedPPS;
                float newThrottle = throttle.throttle - gain * error;
                if (newThrottle < 0) {
                    newThrottle = defaultThrottle;
                } else if (newThrottle > 1) {
                    newThrottle = 1;
                }
                throttle.throttle = newThrottle;
            }
        }
        return throttle;
    }

    @Override
    public ThrottleBrake getThrottle (){
        return throttle;
    }

 
    @Override
    public String logControllerState() {
        return String.format("%f\t%f\t%f",desiredSpeedPPS, measuredSpeedPPS, throttle);
    }

    @Override
    public String logContents() {
        return "desiredSpeedPPS, measuredSpeedPPS, throttle";
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

    /**
     * @return the gain
     */
    public float getGain (){
        return gain;
    }

    /**
     * @param gain the gain to copyFrom
     */
    public void setGain (float gain){
        this.gain = gain;
        prefs().putFloat("SimpleSpeedController.gain",gain);
    }

//    /**
//     * @return the desiredSpeedPPS
//     */
//    public float getDesiredSpeedPPS (){
//        return desiredSpeedPPS;
//    }
//
    /**
     * Sets the desired speed but does NOT store the preferred value in the Preferences (for speed).
     *
     * @param desiredSpeedPPS the desiredSpeedPPS to copyFrom
     */
    public void setDesiredSpeedPPS (float desiredSpeedPPS){
        float old=this.desiredSpeedPPS;
        this.desiredSpeedPPS = desiredSpeedPPS;
//        support.firePropertyChange("desiredSpeedPPS", old, desiredSpeedPPS);  // updates the GUI with the new value
    }


    /**
     * @return the defaultThrottle
     */
    public float getDefaultThrottle() {
        return defaultThrottle;
    }

    /**
     * @param defaultThrottle the defaultThrottle to copyFrom
     */
    public void setDefaultThrottle(float defaultThrottle) {
        this.defaultThrottle = defaultThrottle;
        prefs().putFloat("SimpleSpeedController.defaultThrottle",defaultThrottle);
    }

    public void annotate(GLAutoDrawable drawable) {
        String s=String.format("SimpleSpeedController\nDesired speed: %8.1f\nMeasured: %8.1f\nError: %8.0f\nThrottle: %s",desiredSpeedPPS, measuredSpeedPPS, (measuredSpeedPPS-desiredSpeedPPS),throttle.toString());
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    /**
     * @return the useModelEnabled
     */
    public boolean isUseModelEnabled() {
        return useModelEnabled;
    }

    /**
     * @param useModelEnabled the useModelEnabled to copyFrom
     */
    public void setUseModelEnabled(boolean useModelEnabled) {
        this.useModelEnabled = useModelEnabled;
        prefs().putBoolean("SimpleSpeedController.useModelEnabled", useModelEnabled);
    }



    //////////////////////////  ff throttle model delegated methods

      public void setThresholdThrottle(float thresholdThrottle) {
        throttleModel.setThresholdThrottle(thresholdThrottle);
    }

    public void setFullThrottle(float fullThrottle) {
        throttleModel.setFullThrottle(fullThrottle);
    }

    public float getThresholdThrottle() {
        return throttleModel.getThresholdThrottle();
    }

    public float getFullThrottle() {
        return throttleModel.getFullThrottle();
    }
 

}
