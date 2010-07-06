/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
/**
 * Controls speed of car to a constant chosen level by feedback from tracker speed onto throttle.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SimpleSpeedController extends EventFilter2D implements SlotCarController {

    private float throttle=0; // last output throttle setting
    private float desiredSpeedPPS=prefs().getFloat("SimpleSpeedController.desiredSpeedPPS",0); // desired speed of card in pixels per second
    private float defaultThrottle=prefs().getFloat("SimpleSpeedController.defaultThrottle",.3f); // default throttle setting if no car is detected
    private float gain=prefs().getFloat("SimpleSpeedController.gain", 1); // gain of proportional controller
    private float measuredSpeedPPS; // the last measured speed

    SlotCarRacer racer;

    public SimpleSpeedController(AEChip chip) {
        super(chip);
        setPropertyTooltip("desiredSpeedPPS", "desired speed of card in pixels per second");
        setPropertyTooltip("defaultThrottle", "default throttle setting if no car is detected");
        setPropertyTooltip("gain", "gain of proportional controller");
    }

    public float computeControl (CarTracker tracker,SlotcarTrack track){
        ClusterInterface car=tracker.getCarCluster();
        if(car==null){
            return defaultThrottle;
        }else{
             measuredSpeedPPS=(float)car.getVelocityPPS().distance(0,0);
            float error=measuredSpeedPPS-desiredSpeedPPS;
            float newThrottle=throttle-gain*error;
            if(newThrottle<0) newThrottle=defaultThrottle; else if(newThrottle>1) newThrottle=1;
            throttle=newThrottle;
            return throttle;
        }
    }

    public float getThrottle (){
        return throttle;
    }

 
    public String logControllerState() {
        return String.format("%f\t%f\t%f",desiredSpeedPPS, measuredSpeedPPS, throttle);
    }

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
     * @param gain the gain to set
     */
    public void setGain (float gain){
        this.gain = gain;
        prefs().putFloat("SimpleSpeedController.gain",gain);
    }

    /**
     * @return the desiredSpeedPPS
     */
    public float getDesiredSpeedPPS (){
        return desiredSpeedPPS;
    }

    /**
     * @param desiredSpeedPPS the desiredSpeedPPS to set
     */
    public void setDesiredSpeedPPS (float desiredSpeedPPS){
        this.desiredSpeedPPS = desiredSpeedPPS;
        prefs().putFloat("SimpleSpeedController.desiredSpeedPPS", desiredSpeedPPS);
    }


    /**
     * @return the defaultThrottle
     */
    public float getDefaultThrottle() {
        return defaultThrottle;
    }

    /**
     * @param defaultThrottle the defaultThrottle to set
     */
    public void setDefaultThrottle(float defaultThrottle) {
        this.defaultThrottle = defaultThrottle;
        prefs().putFloat("SimpleSpeedController.defaultThrottle",defaultThrottle);
    }



}
