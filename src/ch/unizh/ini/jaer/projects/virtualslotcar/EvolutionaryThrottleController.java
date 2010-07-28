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
import java.util.LinkedList;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
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

    // prefs
    private float fractionOfTrackToPerturb = prefs().getFloat("EvolutionaryThrottleController.fractionOfTrackToPunish", 0.2f);
    private float defaultThrottle = prefs().getFloat("EvolutionaryThrottleController.defaultThrottle", .1f); // default throttle setting if no car is detected
    private float maxDistanceFromTrackPoint = prefs().getFloat("EvolutionaryThrottleController.maxDistanceFromTrackPoint", 15); // pixels - need to set in track model
    private boolean learningEnabled = prefs().getBoolean("EvolutionaryThrottleController.learningEnabled", false);
    private float throttleChange = prefs().getFloat("EvolutionaryThrottleController.throttleChange", 0.1f);
    private int numSuccessfulLapsToReward = prefs().getInt("EvolutionaryThrottleController.numSuccessfulLapsToReward", 3);
    // vars
    private float throttle = 0; // last output throttle setting
//    private float measuredSpeedPPS; // the last measured speed
//    private Point2D.Float measuredLocation;
    private SlotcarTrack track;
    private int currentTrackPos; // position in spline parameter of track
//    private int lastTrackPos; // last position in spline parameter of track
    private boolean crash;
    private int successfulLapCounter = 0, lastRewardLap = 0;
    private ThrottleProfile currentProfile, lastSuccessfulProfile, lastSuccessfulProfileEvenOlder;
    private Random random = new Random();
    LapTimer lapTimer = new LapTimer();
    private int lapTime;
    private int prevLapTime;
    private float distanceFromTrack;

    public EvolutionaryThrottleController(AEChip chip) {
        super(chip);
        setPropertyTooltip("defaultThrottle", "default throttle setting if no car is detected; also starting throttle after resetting learning and minimum allowed throttle");
        setPropertyTooltip("maxDistanceFromTrackPoint", "Maximum allowed distance in pixels from track spline point to find nearest spline point; if currentTrackPos=-1 increase maxDistanceFromTrackPoint");
        setPropertyTooltip("fractionOfTrackToPunish", "fraction of track to reduce throttle and mark for no reward");
        setPropertyTooltip("learningEnabled", "enable evolution - successful profiles are sped up, crashes cause reversion to last successful profile");
        setPropertyTooltip("throttleChange", "max amount to increase throttle for perturbation");
        setPropertyTooltip("numSuccessfulLapsToReward", "number of successful (no crash) laps between rewards");
        setPropertyTooltip("fractionOfTrackToPerturb", "fraction of track spline points to increase throttle on after successful laps");
        doLoadThrottleSettings();
    }

    /** Computes throttle using tracker output and ThrottleProfile.
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
        {
            if (track == null) {
                log.warning("null track model - can't compute control");
                return getThrottle();
            }
            this.setTrack(track); // set track for logging
            track.setPointTolerance(maxDistanceFromTrackPoint);

            if (currentProfile == null || currentProfile.getNumPoints() != track.getNumPoints()) {
                currentProfile = new ThrottleProfile(track.getNumPoints());
                log.info("made a new ThrottleProfile :" + currentProfile);
            }

            SlotcarState carState = track.updateSlotcarState(car == null ? null : car.getLocation(), car == null ? 0 : car.getSpeedPPS());
            crash = !carState.onTrack;
            if (!crash) {
                currentTrackPos=carState.segmentIdx;
                distanceFromTrack=track.findDistanceToTrack(carState.XYpos, currentTrackPos);
                // did we lap?
                boolean lapped = lapTimer.update(currentTrackPos, car.getLastEventTimestamp());

                if (lapped) {
                    lapTime = lapTimer.getLastLap().laptimeUs;
                    int dt = lapTime - prevLapTime;
                    if (dt < 0) {
                        log.info("lap time improved by " + dt / 1000 + " ms");
                    } else {
                        log.info("lap time worsened by " + dt / 1000 + " ms");
                    }
                    prevLapTime = lapTime;
                }
                if (learningEnabled && lapTimer.lapCounter - lastRewardLap >= numSuccessfulLapsToReward) {
                    try {
                        log.info("successfully drove " + lapTimer.lapCounter + " laps; cloning this profile and rewarding currentProfile");
                        if(lastSuccessfulProfile!=null) lastSuccessfulProfileEvenOlder=(ThrottleProfile)lastSuccessfulProfile.clone(); // save backup copy of last successfull
                        if(currentProfile!=null) lastSuccessfulProfile = (ThrottleProfile) currentProfile.clone(); // save current as successful
                    } catch (CloneNotSupportedException e) {
                        throw new RuntimeException("couldn't clone the current throttle profile: " + e);
                    }
                    currentProfile.addBump();
                    lastRewardLap = lapTimer.lapCounter;
                }
            } else { // crashed, go back to last successful profile if we are not already using it
                if (lapTimer.lapCounter > 0) {
                    log.info("crashed after " + (lapTimer.lapCounter) + " laps");
                }
                if (learningEnabled && lastSuccessfulProfile != null && currentProfile != lastSuccessfulProfile) {
                    log.info("crashed, switching back to " + lastSuccessfulProfile);
                    currentProfile = lastSuccessfulProfile;
                    currentProfile.subtractBump(currentTrackPos);
                }
                lapTimer.reset();
                lastRewardLap = 0;
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
            oos.writeObject(currentProfile.numPoints);
            oos.writeObject(currentProfile.profile);
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
            Object o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read Integer number of throttle points from preferences");
            }
            int n = ((Integer) o).intValue();
            o = ois.readObject();
            if (o == null) {
                throw new NullPointerException("Couldn't read float array of throttle points from preferences");
            }
            float[] f = (float[]) o;
            currentProfile = new ThrottleProfile(f);
            ois.close();
            bis.close();
            log.info("loaded throttle profile from preferencdes: " + currentProfile);
        } catch (Exception e) {
            log.warning("couldn't load throttle profile: " + e);
        }
    }

    synchronized public void doSlowDown() {
        if (currentProfile != null) {
            currentProfile.slowDown();
            log.info("slowed down current profile to " + currentProfile);
        }
    }

    synchronized public void doSpeedUp() {
        if (currentProfile != null) {
            currentProfile.speedUp();
            log.info("speeded up current profile to " + currentProfile);
        }
    }

    synchronized public void doRevertToLastSuccessfulProfile(){
        if(lastSuccessfulProfileEvenOlder!=null){
            currentProfile=lastSuccessfulProfileEvenOlder;
            log.info("reverted to "+lastSuccessfulProfileEvenOlder);
        }else{
            log.info("cannot revert - no lastSuccessfulProfileEvenOlder stored yet");
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
        return String.format("%d\t%f\t%s", currentTrackPos, throttle, getTrack() == null ? null : getTrack().getCarState());
    }

    @Override
    public String getLogContentsHeader() {
        return " currentTrackPos throttle carState ";
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
        lapTimer.reset();
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
        prefs().putFloat("EvolutionaryThrottleController.defaultThrottle", defaultThrottle);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        String s = String.format("EvolutionaryThrottleController\ncurrentTrackPos: %d\nDistance from track: %.1f\nThrottle: %8.3f", currentTrackPos, distanceFromTrack, throttle);
        MultilineAnnotationTextRenderer.renderMultilineString(s);
        drawThrottleProfile(drawable.getGL());
        drawCurrentTrackPoint(drawable.getGL());
    }
    GLU glu = new GLU();

    /** Displays the extracted track points */
    private void drawThrottleProfile(GL gl) {
        if (getTrack() != null && getTrack().getPointList() != null && currentProfile != null) {

            gl.glColor4f(.5f, 0, 0, .5f);
            // Draw extracted points
            Point2D startPoint = null, selectedPoint = null;
            float maxSize = 40f;
            int idx = 0;
            for (Point2D p : getTrack().getPointList()) {
                float size = maxSize * currentProfile.getThrottle(idx);
                gl.glPointSize(size);
                if (currentProfile.spedUpSegments[idx]) {
                    gl.glColor3f(0, 1, 0);
                } else if (currentProfile.slowedDownSegments[idx]) {
                    gl.glColor3f(1, 0, 0);
                } else {
                    gl.glColor3f(0, 0, 1);
                }
                gl.glBegin(gl.GL_POINTS);
                gl.glVertex2d(p.getX(), p.getY());
                gl.glEnd();
                idx++;
            }


            // Plot lines
            gl.glLineWidth(.5f);
            gl.glBegin(gl.GL_LINE_STRIP);
            for (Point2D p : getTrack().getPointList()) {
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
        prefs().putFloat("EvolutionaryThrottleController.maxDistanceFromTrackPoint", maxDistanceFromTrackPoint);
        // Define tolerance for track model
        getTrack().setPointTolerance(maxDistanceFromTrackPoint);
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
     * @param change the throttlePunishment to set
     */
    public void setThrottleChange(float change) {
        if(change>1) change=1; else if(change<0) change=0;
        this.throttleChange = change;
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
        if (numSuccessfulLapsToReward < 1) {
            numSuccessfulLapsToReward = 1;
        }
        this.numSuccessfulLapsToReward = numSuccessfulLapsToReward;
        prefs().putInt("EvolutionaryThrottleController.numSuccessfulLapsToReward", numSuccessfulLapsToReward);
    }

    private void drawCurrentTrackPoint(GL gl) {
        if (currentTrackPos == -1 || getTrack() == null) {
            return;
        }
        gl.glColor4f(1, 0, 0, .5f);
        Point2D p = getTrack().getPoint(currentTrackPos);
        gl.glRectd(p.getX() - 1, p.getY() - 1, p.getX() + 1, p.getY() + 1);
    }

    /**
     * @return the track
     */
    public SlotcarTrack getTrack() {
        return track;
    }

    /**
     * @param track the track to set
     */
    public void setTrack(SlotcarTrack track) {
        this.track = track;
    }

    private class ThrottleProfile implements Cloneable, Serializable {

        float[] profile;
        boolean[] spedUpSegments, slowedDownSegments;
        int numPoints=0;

        /** Creates a new ThrottleProfile using existing array of throttle settngs.
         *
         * @param profile array of throttle points.
         */
        public ThrottleProfile(float[] throttleSettings) {
            this.profile = throttleSettings;
            this.numPoints = throttleSettings.length;
            spedUpSegments=new boolean[numPoints];
            slowedDownSegments=new boolean[numPoints];
        }

        /** Creates a new ThrottleProfile with numPoints points.
         *
         * @param numPoints number of throttle points.
         */
        public ThrottleProfile(int numPoints) {
            super();
            this.numPoints = numPoints;
            profile = new float[numPoints];
            spedUpSegments=new boolean[numPoints];
            slowedDownSegments=new boolean[numPoints];
            Arrays.fill(profile, getDefaultThrottle());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            ThrottleProfile newProfile = (ThrottleProfile) super.clone();
            newProfile.profile = new float[numPoints];
            for (int i = 0; i < numPoints; i++) {
                newProfile.profile[i] = profile[i];
            }
            return newProfile;
        }

        public float getThrottle(int section) {
            if (section == -1) {
                return defaultThrottle;
            }
            return profile[section];
        }

        /** Number of points in the profile (same as number of spline points in the track). */
        public int getNumPoints() {
            return numPoints;
        }

        public float[] getProfile() {
            return profile;
        }

        /** Adds a throttle bump at a random location. */
        public void addBump() {
            Arrays.fill(spedUpSegments,false);
            Arrays.fill(slowedDownSegments,false);
            // increase throttle settings around randomly around some track point
            int center = getNextThrottleBumpPoint();
            int m = (int) (numPoints * getFractionOfTrackToPerturb());
            log.info("rewarding " + m + " of " + numPoints + " throttle settings around track point " + center);
            for (int i = 0; i < m; i++) {
                float dist = (float) Math.abs(i - m / 2);
                float factor = (m / 2 - dist) / (m / 2);
                int ind = getIndexFrom(center, i);
                profile[ind] = clipThrottle(profile[ind] + (float) throttleChange * factor); // increase throttle by tent around random center point
                spedUpSegments[ind]=true;
            }
        }

        /** Subtracts a rectangle of throttle starting at segment and continuing back for fractionOfTrackToPunish.
         * The amount subtracted is a fraction of the throttleChange.
         * @param segment the starting point of the subtraction, e.g. the location just before the last crash.
         */
        public void subtractBump(int segment){
            Arrays.fill(slowedDownSegments,false);
            Arrays.fill(spedUpSegments,false);
            log.info("reducing throttle starting from segment "+segment);
            int n = (int) (numPoints * fractionOfTrackToPerturb);
            for (int i = 0; i < n; i++) {
                int seg = (segment - i);
                if (seg < 0) {
                    seg = seg + numPoints;
                }
//                System.out.println("reducing "+seg);
                profile[seg] = clipThrottle(profile[seg] - throttleChange / 2);
                slowedDownSegments[seg] = true;
            }
        }

        /** Reduces speed on current profile uniformly by throttleChange/3 */
        public void slowDown() {
            for (int i = 0; i < numPoints; i++) {
                profile[i] = clipThrottle(profile[i] - throttleChange/3);
            }
        }

       /** Increases speed on current profile uniformly by throttleChange/3 */
        public void speedUp() {
            for (int i = 0; i < numPoints; i++) {
                profile[i] = clipThrottle(profile[i] + throttleChange/3);
            }
        }

        /** returns the segment at distance from center.
         *
         * @param center the center segment index of the computation.
         * @param distance the distance; positive to advance, negative to retard.
         * @return the segment index.
         */
        private int getIndexFrom(int center, int distance) {
            int index = center + distance;
            if (index > numPoints - 1) {
                index = index - numPoints;
            } else if (index < 0) {
                index = index + numPoints;
            }
            return index;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("ThrottleProfile: ");
            for (int i = 0; i < numPoints; i++) {
                sb.append(String.format(" %d:%.2f", i, profile[i]));
            }
            return sb.toString();
        }

        private void reset() {
            Arrays.fill(profile, defaultThrottle);
            log.info("reset all throttle settings to defaultThrottle=" + defaultThrottle);
        }

        // chooses next spot to add throttle, based on previous throttle profile.
        // The higher the previous throttle, the less likely to choose it.
        private int getNextThrottleBumpPoint() {
            // do accept/reject sampling to get next throttle bump center point, such that
            // the higher the throttle is now, the smaller the chance we increase the throttle there.
            // So, we treat (1-profile[i]) as a likehood of choosing a new throttle.
            // We uniformly pick a bin from B in 1:numPoints and a value V in 0:1 and see if that particular
            // profile[B]<V then we select it as the center. That way, the higher the throttle,
            // the less the chance to selecting that location to be the center of the next bump.

            int tries=numPoints*3;
            while (tries-->0) {
                float v = random.nextFloat();
                int b = random.nextInt(numPoints);
                if (profile[b] < v) {
                    return b;
                }
            }
            return random.nextInt(numPoints); //. give up and just choose one uniformly
        }
    }
}
