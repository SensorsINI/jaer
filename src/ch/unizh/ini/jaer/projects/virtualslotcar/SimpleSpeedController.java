/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
/**
 * Controls speed of car to a constant chosen level by feedback from tracker speed onto throttle.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
class SimpleSpeedController implements SlotCarController {

    private float throttle=0; // last output throttle setting
    private float desiredSpeedPPS=0; // desired speed of card in pixels per second
    final float defaultThrottle=.3f; // default throttle setting if no car is detected
    private float gain=1; // gain of proportional controller

    SlotCarRacer racer;

    public SimpleSpeedController (SlotCarRacer aThis){
        racer=aThis;
    }

    public float computeControl (CarTracker tracker,SlotcarTrack track){
        ClusterInterface car=tracker.getCarCluster();
        if(car==null){
            return defaultThrottle;
        }else{
            float measuredSpeedPPS=(float)car.getVelocityPPS().distance(0,0);
            float error=measuredSpeedPPS-desiredSpeedPPS;
            float newThrottle=throttle-gain*error;
            throttle=newThrottle;
            return throttle;
        }
    }

    public float getThrottle (){
        return throttle;
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
    }

}
