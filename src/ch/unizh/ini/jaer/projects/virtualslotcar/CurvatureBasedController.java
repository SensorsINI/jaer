/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.geom.Point2D;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.StateMachineStates;
/**
 * Controls speed of car based on upcoming curvature of track, using measured speed of car and track model.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class CurvatureBasedController extends AbstractSlotCarController implements SlotCarControllerInterface, FrameAnnotater {

    private float throttle=0; // last output throttle setting
    private float desiredSpeedPPS=prefs().getFloat("CurvatureBasedController.desiredSpeedPPS",0); // desired speed of card in pixels per second
    private float defaultThrottle=prefs().getFloat("CurvatureBasedController.defaultThrottle",.3f); // default throttle setting if no car is detected
    private float gain=prefs().getFloat("CurvatureBasedController.gain", 1); // gain of proportional controller
    private float measuredSpeedPPS; // the last measured speed
    private Point2D.Float measuredLocation;
    private TextRenderer renderer;
    private float throttleDelayMs=prefs().getFloat("CurvatureBasedController.throttleDelayMs", 30);
    private int numUpcomingCurvaturePoints=prefs().getInt("CurvatureBasedController.numUpcomingCurvaturePoints",3);

 

    private float maxDistanceFromTrackPoint=prefs().getFloat("CurvatureBasedController.maxDistanceFromTrackPoint",15);
    private float curvatureDeltaTimeMs=prefs().getFloat("CurvatureBasedController.curvatureDeltaTimeMs",1);

    public CurvatureBasedController(AEChip chip) {
        super(chip);
        setPropertyTooltip("desiredSpeedPPS", "desired speed of card in pixels per second");
        setPropertyTooltip("defaultThrottle", "default throttle setting if no car is detected");
        setPropertyTooltip("gain", "gain of proportional controller");
        setPropertyTooltip("throttleDelayMs", "delay time constant of throttle change on speed; same as look-ahead time for estimation of track curvature");
        setPropertyTooltip("", "");
    }

    /** Computes throttle using tracker output and upcoming curvature, using methods from SlotcarTrack.
     *
     * @param tracker
     * @param track
     * @return the throttle from 0-1.
     */
    public float computeControl (CarTracker tracker,SlotcarTrack track){
        // find the csar, pass it to the track if there is one to get it's location, the use the UpcomingCurvature to compute the curvature coming up,
        // then compute the throttle to get our speed at the limit of traction.
        ClusterInterface car=tracker.getCarCluster();
        if(car==null){
            // lost car tracking or just starting out
            return getThrottle();  // return last throttle
        }else{

            /*
             * during the normal running of the car, the steps would be as follows (which are computed in the controller, given the CarTracker and SlotcarTrack)

   1. Get the car position from the tracker.
   2. Ask the track model for the nearest spline point which is an int indexing into the list of track points.
   3. Update the car state SlotcarState of the track model - not clear how this should be done from the CarTracker data.
   4. Ask the track model for the list of upcoming curvatures.
   5. From the parameter throttleDelayMs, find the curvature at this time in the future.
   6. Compute the throttle needed to get us to a speed at this future time that puts us at the limit of traction.


This still requires us to have an estimated relation between throttle and resulting speed. We don't have any such model yet.
             */
             measuredSpeedPPS=(float)car.getSpeedPPS();
             measuredLocation=car.getLocation();
            int trackPos= track.findClosest(measuredLocation, maxDistanceFromTrackPoint);
            track.getCarState().pos=trackPos;
            track.getCarState().XYpos=measuredLocation; // update car state
            track.getCarState().speed=measuredSpeedPPS;
// TODO nothing below is correct!!!!
            UpcomingCurvature curvature=track.getCurvature(numUpcomingCurvaturePoints, curvatureDeltaTimeMs, measuredSpeedPPS);
            int neededCurvatureIdx=0;
            float upcomingLateralAcceleration=measuredSpeedPPS*measuredSpeedPPS*curvature.getCurvature(neededCurvatureIdx);
            return throttle;
        }
    }

    public float getThrottle (){
        return throttle;
    }

 
    public String logControllerState() {
        return String.format("%f\t%f\t%f",desiredSpeedPPS, measuredSpeedPPS, throttle);
    }

    public String getLogContentsHeader() {
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
        prefs().putFloat("CurvatureBasedController.gain",gain);
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
        prefs().putFloat("CurvatureBasedController.defaultThrottle",defaultThrottle);
    }

    public void annotate(GLAutoDrawable drawable) {
         if ( renderer == null ){
            renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,24),true,true);
        }
        renderer.begin3DRendering();
        String s=String.format("Desired speed: %8.1f, Measured %8.1f",desiredSpeedPPS, measuredSpeedPPS);
        final float scale=.25f;
        renderer.draw3D(s,0,8,0,scale);
//        Rectangle2D bounds=renderer.getBounds(s);
        renderer.end3DRendering();   }



}
