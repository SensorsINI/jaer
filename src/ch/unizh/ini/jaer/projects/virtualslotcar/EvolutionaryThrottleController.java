/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Learns the throttle at different part of the track.
 * <p>
 * After a discussion with Garrick and Tobias Glassmachers we decided to go
 * for coding up a learning approach that saves successful
 * ThrottleSetting profiles if the car makes it around the track twice, and then randomly perturbs the
 * profile to increase the throttle smoothly somewhere along the track. If the change causes a
 * crash, we go back to the saved profile and perturb again, using a random bump 
 * of throttle increase somewhere on the track. This approach will guarantee increase in
 * speed and will always eventually cause a crash but we can add a button to go back to the last
 * successful profile. The track model is the basis of this because it tells us where we are.

 *
 * @author Juston, Tobi
 */
public class EvolutionaryThrottleController extends AbstractSlotCarController implements SlotCarControllerInterface, FrameAnnotater {

    private float fractionOfTrackToPerturb = prefs().getFloat("EvolutionaryThrottleController.fractionOfTrackToPunish", 0.2f);
    private float defaultThrottle = prefs().getFloat("EvolutionaryThrottleController.defaultThrottle", .1f); // default throttle setting if no car is detected
    private float maxDistanceFromTrackPoint = prefs().getFloat("CurvatureBasedController.maxDistanceFromTrackPoint", 15); // pixels - need to set in track model
    private boolean learningEnabled = prefs().getBoolean("EvolutionaryThrottleController.learningEnabled", false);
    private float throttleChange = prefs().getFloat("EvolutionaryThrottleController.throttleChange", 0.05f);
    private int numSuccessfulLapsToReward = prefs().getInt("EvolutionaryThrottleController.numSuccessfulLapsToReward", 2);
    private float throttle = 0; // last output throttle setting
    private float measuredSpeedPPS; // the last measured speed
    private Point2D.Float measuredLocation;
    private SlotcarTrack track;
    private int currentTrackPos; // position in spline parameter of track
    private int lastTrackPos; // last position in spline parameter of track
    private boolean crash;
    private int successfulLapCounter = 0, lastRewardLap = 0;
    private ThrottleProfile currentProfile, lastSuccessfulProfile;
    Random random = new Random();

    public EvolutionaryThrottleController(AEChip chip) {
        super(chip);
        setPropertyTooltip("defaultThrottle", "default throttle setting if no car is detected");
        setPropertyTooltip("maxDistanceFromTrackPoint", "Maximum allowed distance in pixels from track spline point to find nearest spline point; if currentTrackPos=-1 increase maxDistanceFromTrackPoint");
        setPropertyTooltip("fractionOfTrackToPunish", "fraction of track to reduce throttle and mark for no reward");
        setPropertyTooltip("learningEnabled", "enable evolution - successful profiles are sped up, crashes cause reversion to last successful profile");
        setPropertyTooltip("throttleChange", "max amount to increase throttle for perturbation");
        setPropertyTooltip("numSuccessfulLapsToReward", "number of successful (no crash) laps between rewards");
        setPropertyTooltip("fractionOfTrackToPerturb", "fraction of track spline points to increase throttle on after successful laps");
        doLoadThrottleSettings();
    }

    /** Computes throttle using tracker output and upcoming curvature, using methods from SlotcarTrack.
     *
     * @param tracker
     * @param track
     * @return the throttle from 0-1.
     */
    @Override
    synchronized public float computeControl(CarTracker tracker, SlotcarTrack track) {
        // find the csar, pass it to the track if there is one to get it's location, the use the UpcomingCurvature to compute the curvature coming up,
        // then compute the throttle to get our speed at the limit of traction.
        ClusterInterface car = tracker.getCarCluster();
        boolean passedStartLine;
        {
            if (track == null) {
                log.warning("null track model - can't compute control");
                return getThrottle();
            }
            this.track = track; // set track for logging
            track.setPointTolerance(maxDistanceFromTrackPoint);

            measuredSpeedPPS = car == null ? Float.NaN : (float) car.getSpeedPPS();
            measuredLocation = car == null ? null : car.getLocation();

            if (currentProfile == null || currentProfile.getTrack() != track) {
                currentProfile = new ThrottleProfile(track);
                log.info("made a new ThrottleProfile :" + currentProfile);
            }

            SlotcarState carState = track.updateSlotcarState(measuredLocation, measuredSpeedPPS);
            crash = !carState.onTrack;
            if (!crash) {
                // did we lap?
                currentTrackPos = carState.segmentIdx;
                if (lastTrackPos > currentTrackPos) {
                    successfulLapCounter++;
//                    log.info("successfulLapCounter=" + successfulLapCounter);
                }
                lastTrackPos = currentTrackPos;
                if (learningEnabled && successfulLapCounter - lastRewardLap > numSuccessfulLapsToReward) {
                    try {
                        log.info("drove " + numSuccessfulLapsToReward + " laps (successfulLapCounter=" + successfulLapCounter + "); saving profile and rewarding currentProfile");
                        lastSuccessfulProfile = (ThrottleProfile) currentProfile.clone();
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException("couldn't clone the current throttle profile: " + e);
                    }
                    currentProfile.reward();
                    lastRewardLap = successfulLapCounter;
                }
            } else { // crashed, go back to last successful profile if we are not already using it
                if (successfulLapCounter > 0) {
                    log.info("crashed after " + (successfulLapCounter - 1) + " laps");
                }
                successfulLapCounter = 0;
                lastRewardLap = 0;
                if (learningEnabled && lastSuccessfulProfile != null && currentProfile != lastSuccessfulProfile) {
                    log.info("crashed, switching back to " + lastSuccessfulProfile);
                    currentProfile = lastSuccessfulProfile;
                }
            }


            throttle = currentProfile.getThrottle(currentTrackPos);
            return throttle;
        }
    }

    synchronized public void doResetAllThrottleValues() {
        if (currentProfile == null) {
            log.warning("cannot reset until profile exists");
            return;
        }
        currentProfile.reset();
    }

    synchronized public void doSaveThrottleSettings() {

        if (currentProfile == null) {
            log.warning("no profile to save");
            return;
        }
        try {

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(currentProfile.n);
            oos.writeObject(currentProfile.throttleSettings);
            prefs().putByteArray("EvolutionaryThrottleController.throttleProfile", bos.toByteArray());
            oos.close();
            bos.close();
            log.info("throttle settings saveed to preferences");
        } catch (Exception e) {
            log.warning("couldn't save profile: " + e);
        }

    }

    public final synchronized void doLoadThrottleSettings() {
        try {

            byte[] b = prefs().getByteArray("EvolutionaryThrottleController.throttleProfile", null);
            if (b == null) {
                log.info("no throttle settings saved in preferences, can't load them");
                return;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            currentProfile.n = ((Integer)ois.readObject()).intValue();
            currentProfile.throttleSettings=(float[])ois.readObject();
            ois.close();
            bis.close();
            log.info("loaded throttle profile from preferencdes");
        } catch (Exception e) {
            log.warning("couldn't load throttle profile: " + e);
        }

    }

    private float clipThrottle(float t) {
        if (t > 1) {
            t = 1;
        } else if (t < defaultThrottle) {
            t = defaultThrottle;
        }
        return t;
    }

    @Override
    public float getThrottle() {
        return throttle;
    }

    @Override
    public String logControllerState() {
        return String.format("%f\t%f\t%s", measuredSpeedPPS, throttle, track == null ? null : track.getCarState());
    }

    @Override
    public String getLogContentsHeader() {
        return "upcomingCurvature, lateralAccelerationLimitPPS2, desiredSpeedPPS, measuredSpeedPPS, throttle, slotCarState";
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
        prefs().putFloat("CurvatureBasedController.defaultThrottle", defaultThrottle);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        String s = String.format("EvolutionaryThrottleController\ncurrentTrackPos: %d\nThrottle: %8.3f", currentTrackPos, throttle);
        MultilineAnnotationTextRenderer.renderMultilineString(s);
        drawThrottleProfile(drawable.getGL());
    }
    GLU glu = new GLU();

    /** Displays the extracted track points */
    private void drawThrottleProfile(GL gl) {
        if (track != null && track.getPointList() != null && currentProfile != null) {

            gl.glColor4f(.5f, 0, 0, .5f);
            // Draw extracted points
            Point2D startPoint = null, selectedPoint = null;
            float maxSize = 40f;
            int idx = 0;
            for (Point2D p : track.getPointList()) {
                float size = maxSize * currentProfile.getThrottle(idx);
                gl.glPointSize(size);
                gl.glBegin(gl.GL_POINTS);
                gl.glVertex2d(p.getX(), p.getY());
                gl.glEnd();
                idx++;
            }


            // Plot lines
            gl.glLineWidth(.5f);
            gl.glBegin(gl.GL_LINE_STRIP);
            for (Point2D p : track.getPointList()) {
                gl.glVertex2d(p.getX(), p.getY());
            }
            gl.glEnd();
        }

        chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawExtractedTrack");

    }

    /**
     * @return the maxDistanceFromTrackPoint
     */
    public float getMaxDistanceFromTrackPoint() {
        return maxDistanceFromTrackPoint;
    }

    /**
     * @param maxDistanceFromTrackPoint the maxDistanceFromTrackPoint to set
     */
    public void setMaxDistanceFromTrackPoint(float maxDistanceFromTrackPoint) {
        this.maxDistanceFromTrackPoint = maxDistanceFromTrackPoint;
        prefs().putFloat("CurvatureBasedController.maxDistanceFromTrackPoint", maxDistanceFromTrackPoint);
        // Define tolerance for track model
        track.setPointTolerance(maxDistanceFromTrackPoint);
    }

    /**
     * @return the learning
     */
    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    /**
     * @param learning the learning to set
     */
    public void setLearningEnabled(boolean learning) {
        this.learningEnabled = learning;
        prefs().putBoolean("EvolutionaryThrottleController.learningEnabled", learningEnabled);
    }

    /**
     * @return the throttlePunishment
     */
    public float getThrottleChange() {
        return throttleChange;
    }

    /**
     * @param throttlePunishment the throttlePunishment to set
     */
    public void setThrottleChange(float throttlePunishment) {
        this.throttleChange = throttlePunishment;
        prefs().putFloat("EvolutionaryThrottleController.throttleChange", throttleChange);
    }

    /**
     * @return the fractionOfTrackToPunish
     */
    public float getFractionOfTrackToPerturb() {
        return fractionOfTrackToPerturb;
    }

    /**
     * @param fractionOfTrackToPunish the fractionOfTrackToPunish to set
     */
    synchronized public void setFractionOfTrackToPerturb(float fractionOfTrackToPunish) {
        this.fractionOfTrackToPerturb = fractionOfTrackToPunish;
        prefs().putFloat("EvolutionaryThrottleController.fractionOfTrackToPunish", fractionOfTrackToPunish);
    }

    /**
     * @return the numSuccessfulLapsToReward
     */
    public int getNumSuccessfulLapsToReward() {
        return numSuccessfulLapsToReward;
    }

    /**
     * @param numSuccessfulLapsToReward the numSuccessfulLapsToReward to set
     */
    public void setNumSuccessfulLapsToReward(int numSuccessfulLapsToReward) {
        if (numSuccessfulLapsToReward < 2) {
            numSuccessfulLapsToReward = 2;
        }
        this.numSuccessfulLapsToReward = numSuccessfulLapsToReward;
        prefs().putInt("EvolutionaryThrottleController.numSuccessfulLapsToReward", numSuccessfulLapsToReward);
    }

    private class ThrottleProfile implements Cloneable, Serializable {

        float[] throttleSettings;
        volatile private SlotcarTrack track;
        int n;

        public ThrottleProfile(SlotcarTrack track) {
            super();
            this.track = track;
            n = track.getNumPoints();
            throttleSettings = new float[n];
            Arrays.fill(throttleSettings, getDefaultThrottle());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            ThrottleProfile newProfile = (ThrottleProfile) super.clone();
            newProfile.throttleSettings = new float[n];
            for (int i = 0; i < n; i++) {
                newProfile.throttleSettings[i] = throttleSettings[i];
            }
            return newProfile;
        }

        public float getThrottle(int section) {
            return throttleSettings[section];
        }

        public void reward() {
            // increase throttle settings around randomly around some track point
            int center = random.nextInt(n);
            int m = (int) (n * getFractionOfTrackToPerturb());
            log.info("rewarding " + m + " of " + n + " throttle settings around track point " + center);
            for (int i = 0; i < m; i++) {
                float dist = (float) Math.abs(i - m / 2);
                float factor = (m / 2 - dist) / (m / 2);
                int ind = getIndexFrom(center, i);
                throttleSettings[ind] = clipThrottle(throttleSettings[ind] + (float) throttleChange * factor); // increase throttle by tent around random center point
            }
        }

        private int getIndexFrom(int center, int distance) {
            int index = center + distance;
            if (index > n - 1) {
                index = index - n;
            } else if (index < 0) {
                index = index + n;
            }
            return index;
        }

        /**
         * @return the track
         */
        public SlotcarTrack getTrack() {
            return track;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("ThrottleProfile: ");
            for (int i = 0; i < n; i++) {
                sb.append(String.format(" %d:%.2f", i, throttleSettings[i]));
            }
            return sb.toString();
        }

        private void reset() {
            Arrays.fill(throttleSettings, defaultThrottle);
            log.info("reset all throttle settings to defaultThrottle=" + defaultThrottle);
        }
    }
}
