/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.StateMachineStates;
import net.sf.jaer.util.TobiLogger;

import com.jogamp.opengl.util.awt.TextRenderer;
import net.sf.jaer.event.BasicEvent;

/**
 * Learns the throttle at different part of the track.
 * <p>
 After a discussion with Garrick and Tobias Glassmachers we decided to go for
 coding up a learning approach that saves successful ThrottleSetting profiles
 if the car makes it around the track twice, and then randomly perturbs the
 throttleValues to increase the throttle smoothly somewhere along the track.
 If the change causes a crash, we go back to the saved throttleValues and
 perturb again, using a random bump of throttle increase somewhere on the
 track. This approach will guarantee increase in speed and will always
 eventually cause a crash but we can appendCopy a button to go back to the last
 successful throttleValues. The track model is the basis of this because it
 tells us where we are.
 *
 *
 * @author Juston, Tobi
 */
@Description("Evolution-based slot car throttle controller")
public class EvolutionaryThrottleController extends AbstractSlotCarController implements SlotCarControllerInterface, FrameAnnotater, MouseListener, MouseMotionListener, PropertyChangeListener {

    // prefs
    private int numSegmentsToBrakeBeforeCrash = getInt("numSegmentsToBrakeBeforeCrash", 2);
    private int numSegmentsSpacingFromCrashToBrakingPoint = getInt("numSegmentsSpacingFromCrashToBrakingPoint", 4);
    private float fractionOfTrackToSpeedUp = getFloat("fractionOfTrackToSpeedUp", 0.3f);
    private float fractionOfTrackToSlowDownPreCrash = getFloat("fractionOfTrackToSlowDownPreCrash", .15f);
    private float defaultThrottleValue = getFloat("defaultThrottle", .1f); // default throttle setting if no car is detected
    private ThrottleBrake defaultThrottle = new ThrottleBrake(defaultThrottleValue, false);
    private boolean learningEnabled = getBoolean("learningEnabled", false);
    private float throttleChange = getFloat("throttleChange", 0.03f);
    private float editThrottleChange = getFloat("editThrottleChange", 0.2f);
    private int numSuccessfulLapsToReward = getInt("numSuccessfulLapsToReward", 2);
    private float startingThrottleValue = getFloat("startingThrottleValue", .1f);
    private boolean showThrottleProfile = getBoolean("showThrottleProfile", true);

    private int lastTimestamp = 0;

    /**
     * possible states,
     * <ol>
     * <li> STARTING means no car is tracked or tracker has not found a car
     * cluster near the track model,
     * <li> RUNNING is the active state,
     * <li> CRASHED is the state if we were RUNNING and the car tracker has
     * tracked the car sufficiently far away from the track model,
     * <li> STALLED is the state if the car has stopped being tracked but the
     * last tracked position was on the track because it has stalled out and
     * stopped moving. is after there have not been any definite balls for a
     * while and we are waiting for a clear ball directed
     * </ol>
     */
    public enum State {

        OVERRIDDEN, STARTING, RUNNING, CRASHED
    }

    protected class RacerState extends StateMachineStates {

        State state = State.STARTING;

        @Override
        public Enum getInitial() {
            return State.STARTING;
        }
    }
    private RacerState state = new RacerState();
    // vars
    private ThrottleBrake throttle = new ThrottleBrake(); // last output throttle setting
    private int currentTrackPos; // position in spline parameter of track
    private int lastRewardLap = 0;
    private ThrottleProfile currentProfile, lastSuccessfulProfile, lastSuccessfulProfileEvenOlder;
    private Random random = new Random();
    LapTimer lapTimer = null;
    private int lapTime;
    private int prevLapTime;
    private TrackDefineFilter trackDefineFilter;
    private FilterChain filterChain;
    private CarTracker carTracker;
    private CarTracker.CarCluster car = null;
    private boolean showedMissingTrackWarning = false;
    private SlotCarSoundSample sounds = null;
    private int lastCrashLocation = -1;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    TobiLogger learningLogger = new TobiLogger("EvolutionaryThrottleController", "throttle settings log for slot car racer during learning");

    public EvolutionaryThrottleController(AEChip chip) {
        super(chip);
        final String s = "EvolutionaryThrottleController";
        setPropertyTooltip(s, "defaultThrottle", "default throttle setting if no car is detected; also starting throttle after resetting learning and minimum allowed throttle");
        setPropertyTooltip(s, "fractionOfTrackToPunish", "fraction of track to reduce throttle and mark for no reward");
        setPropertyTooltip(s, "learningEnabled", "enable evolution - successful profiles are sped up, crashes cause reversion to last successful profile");
        setPropertyTooltip(s, "throttleChange", "max amount to increase throttle for perturbation");
        setPropertyTooltip(s, "editThrottleChange", "amount to change throttle for mouse edits of the throttle profile");
        setPropertyTooltip(s, "numSuccessfulLapsToReward", "number of successful (no crash) laps between rewards");
        setPropertyTooltip(s, "numSegmentsToBrakeBeforeCrash", "number track segments to brake for just prior to crash location");
        setPropertyTooltip(s, "numSegmentsSpacingFromCrashToBrakingPoint", "number track segments before crash that braking segments start");
        setPropertyTooltip(s, "fractionOfTrackToSpeedUp", "fraction of track spline points to increase throttle on after successful laps");
        setPropertyTooltip(s, "fractionOfTrackToSlowDownPreCrash", "fraction of track spline points before crash point to reduce throttle on");
        setPropertyTooltip(s, "startingThrottleValue", "throttle value when starting (no car cluster detected)");
        setPropertyTooltip(s, "showThrottleProfile", "displays the throttle profile, with dot size reprenting the throttle value");

        // do methods
        setPropertyTooltip(s, "guessThrottleFromTrackModel", "guess initial throttle profile from track model");
        setPropertyTooltip(s, "resetAllThrottleValues", "reset all profile points to defaultThrottle");
        setPropertyTooltip(s, "loadThrottleSettings", "load profile from preferences");
        setPropertyTooltip(s, "saveThrottleSettings", "save profile to preferences");
        setPropertyTooltip(s, "revertToLastSuccessfulProfile", "explicitly revert profile to last one that made it around the track at least numSuccessfulLapsToReward");
        setPropertyTooltip(s, "slowDown", "reduce all profile point throttle settings");
        setPropertyTooltip(s, "speedUp", "increase all profile point throttle settings");
        setPropertyTooltip("enableLearning", "turns on learning so the successful laps store the throttle profile, and crash laps remove the last change in profile");
        setPropertyTooltip("disableLearning", "turns off learning");

        doLoadThrottleSettings();

        filterChain = new FilterChain(chip);

        trackDefineFilter = new TrackDefineFilter(chip);
        trackDefineFilter.setEnclosed(true, this);
        carTracker = new CarTracker(chip);
        carTracker.setTrack(trackDefineFilter.getTrack());
        carTracker.setEnclosed(true, this);

        filterChain.add(trackDefineFilter);
        filterChain.add(carTracker);

        trackDefineFilter.getSupport().addPropertyChangeListener(SlotcarTrack.EVENT_TRACK_CHANGED, this);
        trackDefineFilter.getSupport().addPropertyChangeListener(SlotcarTrack.EVENT_TRACK_CHANGED, carTracker);

        lapTimer = new LapTimer(getTrack());
        trackDefineFilter.getSupport().addPropertyChangeListener(SlotcarTrack.EVENT_TRACK_CHANGED, lapTimer);

        setEnclosedFilterChain(filterChain);
        try {
            sounds = new SlotCarSoundSample("wipeout2.wav");
        } catch (Exception ex) {
            log.warning("No sound effects available: " + ex.toString());
        }

        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }

    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {

        if (in.getSize() > 0) {
            lastTimestamp = in.getLastTimestamp(); // for logging
        }

        if ((trackDefineFilter.getTrack() != null) && ((currentProfile == null) || (currentProfile.getNumPoints() != getTrack().getNumPoints()))) {
            currentProfile = new ThrottleProfile(getTrack().getNumPoints());
            currentProfile.log();
            log.info("made a new ThrottleProfile :" + currentProfile);
        }

        out = getEnclosedFilterChain().filterPacket(in); // does cartracker and maybe trackdefinefilter

        car = carTracker.findCarCluster();
        if (car != null) {
            currentTrackPos = car.getSegmentIdx();
        }

	// choose state & throttle
        if (state.get() == State.OVERRIDDEN) {

        } else if (state.get() == State.STARTING) {
            //            throttle.throttle = getStartingThrottleValue();
            if ((car != null) && car.isRunning()) {
                state.set(State.RUNNING);
            }
        } else if (state.get() == State.RUNNING) {
            if (trackDefineFilter.getTrack() == null) {
                if (!showedMissingTrackWarning) {
                    log.warning("Track not defined yet. Use the TrackdefineFilter to extract the slot car track or load the track from a file.");
                }
                showedMissingTrackWarning = true;
            }  else {
                if((car==null) || !car.isVisible() || car.isCrashed()){
                    state.set(State.STARTING);
                }else if ((car != null) && car.isVisible() && !car.isCrashed()) {
                    // did we lap?
                    boolean lapped = lapTimer.update(currentTrackPos, car.getLastEventTimestamp());

                    if (lapped) {
                        lapTime = lapTimer.getCurrentLap().laptimeUs;
                        int dt = lapTime - prevLapTime;
                        if (dt < 0) {
                            log.info("lap time improved by " + (dt / 1000) + " ms");
                        } else if (dt > 0) {
                            log.info("lap time worsened by " + (dt / 1000) + " ms");
                        }
                        prevLapTime = lapTime;
                    }
                    if (learningEnabled && ((lapTimer.lapCounter - lastRewardLap) > numSuccessfulLapsToReward)) {
                        try {
                            log.info("successfully drove " + lapTimer.lapCounter + " laps; cloning this profile and rewarding currentProfile");
                            if (lastSuccessfulProfile != null) {
                                lastSuccessfulProfileEvenOlder = (ThrottleProfile) lastSuccessfulProfile.clone(); // save backup copy of last successfull
                            }
                            if (currentProfile != null) {
                                lastSuccessfulProfile = (ThrottleProfile) currentProfile.clone(); // save current as successful
                            }
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException("couldn't clone the current throttle profile: " + e);
                        }
                        currentProfile.addBump();
                        currentProfile.log();
                        lastRewardLap = lapTimer.lapCounter;
                    }
                }
                if (carTracker.getCrashedCar() != null) {
                    state.set(State.CRASHED);
                    lastCrashLocation = carTracker.getCrashedCar().crashSegment;
                    //                    throttle.throttle = getStartingThrottleValue(); // don't actually change profile, starting comes from getThrottle
                    if (sounds != null) {
                        sounds.play();
                    } else {
                        log.warning("null sounds object");
                    }
                    if (learningEnabled) {
                        if ((lastSuccessfulProfile != null) && (currentProfile != lastSuccessfulProfile)) {
                            log.info("crashed at segment" + lastCrashLocation + ", switching back to previous profile");
                            currentProfile = lastSuccessfulProfile;
                            currentProfile.log();
                        }
                        if (numSegmentsToBrakeBeforeCrash > 0) {
                            currentProfile.addBrake(carTracker.getCrashedCar().crashSegment);
                            currentProfile.log();
                        } else {
                            currentProfile.subtractBump(carTracker.getCrashedCar().crashSegment);
                            currentProfile.log();
                        }
                    }
                    lastRewardLap = lapTimer.lapCounter; // don't reward until we make some laps from here
                } else if (car != null) {
                    throttle = currentProfile.getThrottle(car.getSegmentIdx());
                }
            }
        } else if (state.get() == State.CRASHED) {
            //            throttle.throttle = getStartingThrottleValue();
            state.set(State.STARTING);
        }

        setBigStatusText(state.toString(), bigStatusColor);

        return out;
    }
    private TextRenderer statusRenderer = null;
    private Color bigStatusColor = new Color(1, 0, 0, .4f);
    private String bigStatusText = null;

    synchronized private void setBigStatusText(String s, Color c) {
        bigStatusText = s;
        bigStatusColor = c;
    }

    synchronized private void renderBigStatusText(GLAutoDrawable drawable) {
        if (bigStatusText == null) {
            return;
        }
        if (statusRenderer == null) {
            statusRenderer = new TextRenderer(new Font("Serif", Font.BOLD, 50));
        }
        statusRenderer.setColor(bigStatusColor);
        Rectangle2D bounds = statusRenderer.getBounds(bigStatusText);
        statusRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        statusRenderer.draw(bigStatusText, (int) ((drawable.getSurfaceWidth() / 2) - (bounds.getWidth() / 2)), (int) ((drawable.getSurfaceHeight() / 2) - (bounds.getHeight() / 2)));
        statusRenderer.endRendering();
    }

    /**
     * Computes throttle using tracker output and ThrottleProfile.
     *
     * @param tracker
     * @param track
     * @return the throttle from 0-1.
     */
    @Override
	synchronized public ThrottleBrake computeControl(CarTrackerInterface tracker, SlotcarTrack track) {
        return throttle;

    }

    synchronized public void doResetAllThrottleValues() {
        if (currentProfile == null) {
            log.warning("cannot reset until profile exists");
            return;
        }
        currentProfile.reset();
        currentProfile.log();
    }

    synchronized public void doGuessThrottleFromTrackModel() {
        if (currentProfile == null) {
            log.warning("cannot guess until profile exists");
            return;
        }
        currentProfile.guessThrottleFromTrackModel();
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
            oos.writeObject(currentProfile.throttleValues);
            putByteArray("throttleProfile", bos.toByteArray());
            oos.close();
            bos.close();
            log.info("throttle settings saveed to preferences");
        } catch (Exception e) {
            log.warning("couldn't save profile: " + e);
        }

    }

    public void doEnableLearning() {
        setLearningEnabled(true);
    }

    public void doDisableLearning() {
        setLearningEnabled(false);
    }

    public final synchronized void doLoadThrottleSettings() {
        try {

            byte[] b = getByteArray("throttleProfile", null);
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
            ThrottleBrake[] f = (ThrottleBrake[]) o;
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
            currentProfile.log();
        }
    }

    synchronized public void doSpeedUp() {
        if (currentProfile != null) {
            currentProfile.speedUp();
            log.info("speeded up current profile to " + currentProfile);
            currentProfile.log();
        }
    }

    synchronized public void doRevertToLastSuccessfulProfile() {
        if (lastSuccessfulProfileEvenOlder != null) {
            currentProfile = lastSuccessfulProfileEvenOlder;
            log.info("reverted to " + lastSuccessfulProfileEvenOlder);
            currentProfile.log();
        } else {
            log.info("cannot revert - no lastSuccessfulProfileEvenOlder stored yet");
        }
    }

    private float clipThrottle(float t) {
        if (t > 1) {
            t = 1;
        } else if (t < defaultThrottleValue) {
            t = defaultThrottleValue;
        }
        return t;
    }

    final ThrottleBrake startingThrottle = new ThrottleBrake(startingThrottleValue, false);

    @Override
    public ThrottleBrake getThrottle() {
        Enum s = state.get();
        if (s == State.RUNNING) {
            return throttle;
        } else if ((s == State.CRASHED) || (s == State.STARTING)) {
            startingThrottle.throttle = startingThrottleValue;
            return startingThrottle;
        } else if (s == State.OVERRIDDEN) {
            startingThrottle.throttle = defaultThrottleValue;
            return startingThrottle;
        } else {
            throw new Error("state not found for RacerState, shouldn't happen");
        }
    }

    @Override
    public String logControllerState() {
        return String.format("%d\t%s\t%d\t%s\t%s", lastTimestamp, state, currentTrackPos, throttle, car);
    }

    @Override
    public String logContents() {
        return "state currentTrackPos throttle car ";
    }

    @Override
    public void resetFilter() {
        state.set(State.STARTING);
        lapTimer.reset();
        getEnclosedFilterChain().reset();
        lastCrashLocation = -1;
        if (currentProfile != null) {
            currentProfile.resetMarkedSegments();
        }
        lastRewardLap = 0;
        state.set(State.STARTING);
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        trackDefineFilter.setFilterEnabled(false); // don't enable by default
    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == SlotcarTrack.EVENT_TRACK_CHANGED) {
            SlotcarTrack track = (SlotcarTrack) evt.getNewValue();
            if ((currentProfile == null) || (track.getNumPoints() != currentProfile.getNumPoints())) {
                log.warning("new track has different number of points than current throttle profile, making a new default profile");
                currentProfile = new ThrottleProfile(track.getNumPoints());
                currentProfile.log();
            }
        }
    }

    /**
     * @return the defaultThrottle
     */
    public float getDefaultThrottle() {
        return defaultThrottleValue;
    }

    /**
     * @param defaultThrottle the defaultThrottle to copyFrom
     */
    public void setDefaultThrottle(float defaultThrottle) {
        defaultThrottleValue = defaultThrottle;
        putFloat("defaultThrottle", defaultThrottle);
    }
    GLU glu = new GLU();
    GLUquadric quad = null;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        String s=null;
        chip.getCanvas().checkGLError(drawable.getGL().getGL2(), glu, "in TrackdefineFilter.drawThrottleProfile");
        if (isTextEnabled()) {
            if (getTrack() == null) {
                s = "Null track";
            } else {
                s = String.format("EvolutionaryThrottleController\nMouse+Shift: Increase throttle\nMouse+Ctl: Decrease Throttle\nMouse+Alt: Add braking point\nMouse+Shfit+Alt: Remove braking point\nState: %s\nLearning %s\ncurrentTrackPos: %d/%d\nThrottle: %8.3f\n%s", state.toString(), learningEnabled ? "Enabled" : "Disabled", currentTrackPos, getTrack().getNumPoints(), throttle.throttle, lapTimer.toString());
            }
//       if(state.getString()==State.CRASHED){
            //
            //       }else if(state.getString()==State.RUNNING){
            //
            //       }else{
            //       }

            MultilineAnnotationTextRenderer.renderMultilineString(s);
        }
        if (showThrottleProfile) {
            drawThrottleProfile(drawable.getGL().getGL2());
        }
        drawCurrentTrackPoint(drawable.getGL().getGL2());
        drawLastCrashLocation(drawable.getGL().getGL2());

        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
        drawThrottlePainter(drawable);
        renderBigStatusText(drawable);

    }

    /**
     * Displays the extracted track points
     */
    private void drawThrottleProfile(GL2 gl) {
        if ((getTrack() != null) && (getTrack().getPointList() != null) && (currentProfile != null)) {

            chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawThrottleProfile");
            // Plot lines
            gl.glColor4f(.5f, 0, 0, .5f);
            gl.glLineWidth(.5f);
            gl.glBegin(GL.GL_LINE_STRIP);
            for (Point2D p : getTrack().getPointList()) {
                gl.glVertex2d(p.getX(), p.getY());
            }
            gl.glEnd();
            chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawThrottleProfile");

            // plot throttle values and braking locations
            gl.glColor4f(.5f, 0, 0, .5f);
            // Draw extracted points
            float maxSize = 40f;
            int idx = 0;
            for (Point2D p : getTrack().getPointList()) {
                float size = maxSize * currentProfile.getThrottle(idx).throttle;
                if (size < 1) {
                    size = 1;
                }
                if (currentProfile.getBrake(idx)) {
                    // if braking segment, we draw X there, in orange
                    gl.glColor4f(.5f, .25f, 0, .5f);
                    gl.glPushMatrix();
                    gl.glTranslatef((float) p.getX(), (float) p.getY(), 0);
                    final int scale = 2;
                    gl.glLineWidth(3);
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(-scale, -scale);
                    gl.glVertex2f(scale, scale);
                    gl.glVertex2f(scale, -scale);
                    gl.glVertex2f(-scale, scale);
                    gl.glEnd();
                    gl.glPopMatrix();

                } else {
                    // throttle value and if sped up or slowed down
                    gl.glPointSize(size);
                    float rgb[] = {0, 0, .5f};
                    if (currentProfile.spedUpSegments[idx]) {
                        rgb[1] = 1; // green was sped up
                    }
                    if (currentProfile.slowedDownSegments[idx]) {
                        rgb[0] = 1; // red was slowed down
                    }
                    gl.glColor3fv(rgb, 0);
                    gl.glBegin(GL.GL_POINTS);
                    gl.glVertex2d(p.getX(), p.getY());
                    gl.glEnd();

                }
                idx++;
            }
        }

        chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawThrottleProfile");

    }
    private TextRenderer textRenderer = null;

    private void drawCurrentTrackPoint(GL2 gl) {
        if ((currentTrackPos == -1) || (getTrack() == null)) {
            return;
        }
        gl.glColor4f(1, 0, 0, .5f);
        Point2D p = getTrack().getPoint(currentTrackPos);
        gl.glRectd(p.getX() - 1, p.getY() - 1, p.getX() + 1, p.getY() + 1);
    }

    private void drawLastCrashLocation(GL2 gl) {
        if (lastCrashLocation == -1) {
            return;
        }
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        }
        textRenderer.setColor(Color.yellow);
        textRenderer.begin3DRendering();
        Point2D p = getTrack().getPoint(lastCrashLocation);
        textRenderer.draw3D("last crash", (float) p.getX(), (float) p.getY(), 0, .2f);
        textRenderer.end3DRendering();
        gl.glPointSize(10);
        gl.glColor3f(1, 0, 0);
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2d(p.getX(), p.getY());
        gl.glEnd();
    }

    /**
     * @return the showThrottleProfile
     */
    public boolean isShowThrottleProfile() {
        return showThrottleProfile;
    }

    /**
     * @param showThrottleProfile the showThrottleProfile to copyFrom
     */
    public void setShowThrottleProfile(boolean showThrottleProfile) {
        this.showThrottleProfile = showThrottleProfile;
    }

    /**
     * @return the learning
     */
    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    /**
     * @param learning the learning to copyFrom
     */
    public void setLearningEnabled(boolean learning) {
        boolean old=this.learningEnabled;
        learningEnabled = learning;
        putBoolean("learningEnabled", learningEnabled);
        getSupport().firePropertyChange("learningEnabled", old, this.learningEnabled);
    }

    /**
     * @return the throttlePunishment
     */
    public float getThrottleChange() {
        return throttleChange;
    }

    /**
     * @param change the throttlePunishment to copyFrom
     */
    public void setThrottleChange(float change) {
        if (change > 1) {
            change = 1;
        } else if (change < 0) {
            change = 0;
        }
        throttleChange = change;
        putFloat("throttleChange", throttleChange);
    }

    /**
     * @return the fractionOfTrackToPunish
     */
    public float getFractionOfTrackToSpeedUp() {
        return fractionOfTrackToSpeedUp;
    }

    /**
     * @param fractionOfTrackToSpeedUp the fractionOfTrackToPunish to copyFrom
     */
    synchronized public void setFractionOfTrackToSpeedUp(float fractionOfTrackToSpeedUp) {
        if (fractionOfTrackToSpeedUp < 0) {
            fractionOfTrackToSpeedUp = 0;
        } else if (fractionOfTrackToSpeedUp > 1) {
            fractionOfTrackToSpeedUp = 1;
        }
        this.fractionOfTrackToSpeedUp = fractionOfTrackToSpeedUp;
        putFloat("fractionOfTrackToSpeedUp", fractionOfTrackToSpeedUp);
    }

    /**
     * @return the numSegmentsToBrakeBeforeCrash
     */
    public int getNumSegmentsToBrakeBeforeCrash() {
        return numSegmentsToBrakeBeforeCrash;
    }

    /**
     * @return the numSegmentsSpacingFromCrashToBrakingPoint
     */
    public int getNumSegmentsSpacingFromCrashToBrakingPoint() {
        return numSegmentsSpacingFromCrashToBrakingPoint;
    }

    /**
     * @param numSegmentsSpacingFromCrashToBrakingPoint the
     * numSegmentsSpacingFromCrashToBrakingPoint to copyFrom
     */
    public void setNumSegmentsSpacingFromCrashToBrakingPoint(int numSegmentsSpacingFromCrashToBrakingPoint) {
        if (numSegmentsSpacingFromCrashToBrakingPoint < 0) {
            numSegmentsSpacingFromCrashToBrakingPoint = 0;
        }
        int old = this.numSegmentsSpacingFromCrashToBrakingPoint;
        this.numSegmentsSpacingFromCrashToBrakingPoint = numSegmentsSpacingFromCrashToBrakingPoint;
        putInt("numSegmentsSpacingFromCrashToBrakingPoint", numSegmentsSpacingFromCrashToBrakingPoint);
        getSupport().firePropertyChange("numSegmentsSpacingFromCrashToBrakingPoint", old, numSegmentsSpacingFromCrashToBrakingPoint);
    }

    /**
     * @param numSegmentsToBrakeBeforeCrash the numSegmentsToBrakeBeforeCrash to
     * copyFrom
     */
    public void setNumSegmentsToBrakeBeforeCrash(int numSegmentsToBrakeBeforeCrash) {
        if (numSegmentsToBrakeBeforeCrash < 0) {
            numSegmentsToBrakeBeforeCrash = 0;
        }
        int old = this.numSegmentsToBrakeBeforeCrash;
        this.numSegmentsToBrakeBeforeCrash = numSegmentsToBrakeBeforeCrash;
        putInt("numSegmentsToBrakeBeforeCrash", numSegmentsToBrakeBeforeCrash);
        getSupport().firePropertyChange("numSegmentsToBrakeBeforeCrash", old, numSegmentsToBrakeBeforeCrash);
    }

    /**
     * @return the numSuccessfulLapsToReward
     */
    public int getNumSuccessfulLapsToReward() {
        return numSuccessfulLapsToReward;
    }

    /**
     * @param numSuccessfulLapsToReward the numSuccessfulLapsToReward to
     * copyFrom
     */
    public void setNumSuccessfulLapsToReward(int numSuccessfulLapsToReward) {
        if (numSuccessfulLapsToReward < 1) {
            numSuccessfulLapsToReward = 1;
        }
        this.numSuccessfulLapsToReward = numSuccessfulLapsToReward;
        putInt("numSuccessfulLapsToReward", numSuccessfulLapsToReward);
    }

    /**
     * @return the track
     */
    public SlotcarTrack getTrack() {
        return trackDefineFilter.getTrack();
    }

    /**
     * @return the fractionOfTrackToSlowDownPreCrash
     */
    public float getFractionOfTrackToSlowDownPreCrash() {
        return fractionOfTrackToSlowDownPreCrash;
    }

    /**
     * @param fractionOfTrackToSlowDownPreCrash the
     * fractionOfTrackToSlowDownPreCrash to copyFrom
     */
    public void setFractionOfTrackToSlowDownPreCrash(float fractionOfTrackToSlowDownPreCrash) {
        if (fractionOfTrackToSlowDownPreCrash < 0) {
            fractionOfTrackToSlowDownPreCrash = 0;
        } else if (fractionOfTrackToSlowDownPreCrash > 1) {
            fractionOfTrackToSlowDownPreCrash = 1;
        }
        this.fractionOfTrackToSlowDownPreCrash = fractionOfTrackToSlowDownPreCrash;
    }

    /**
     * @return the startingThrottleValue
     */
    public float getStartingThrottleValue() {
        return startingThrottleValue;
    }

    /**
     * @return the editThrottleChange
     */
    public float getEditThrottleChange() {
        return editThrottleChange;
    }

    /**
     * @param editThrottleChange the editThrottleChange to copyFrom
     */
    public void setEditThrottleChange(float editThrottleChange) {
        if (editThrottleChange < .001f) {
            editThrottleChange = .001f;
        } else if (editThrottleChange > 1) {
            editThrottleChange = 1;
        }
        this.editThrottleChange = editThrottleChange;
        putFloat("editThrottleChange", editThrottleChange);
    }

    /**
     * @param startingThrottleValue the startingThrottleValue to copyFrom
     */
    public void setStartingThrottleValue(float startingThrottleValue) {
        if (startingThrottleValue < 0) {
            startingThrottleValue = 0;
        } else if (startingThrottleValue > 1) {
            startingThrottleValue = 1;
        }
        this.startingThrottleValue = startingThrottleValue;
        putFloat("startingThrottleValue", startingThrottleValue);
    }

    /**
     * Profile of throttle values around track.
     */
    private class ThrottleProfile implements Cloneable, Serializable {

        ThrottleBrake[] throttleValues;
        boolean[] spedUpSegments, slowedDownSegments;
        int numPoints = 0;

        /**
         * Creates a new ThrottleProfile using existing array of throttle
         * settings.
         *
         * @param throttleValues array of throttle points.
         */
        public ThrottleProfile(ThrottleBrake[] throttleSettings) {
            throttleValues = throttleSettings;
            numPoints = throttleSettings.length;
            spedUpSegments = new boolean[numPoints];
            slowedDownSegments = new boolean[numPoints];
        }

        /**
         * Creates a new ThrottleProfile with numPoints points.
         *
         * @param numPoints number of throttle points.
         */
        public ThrottleProfile(int numPoints) {
            super();
            this.numPoints = numPoints;
            throttleValues = new ThrottleBrake[numPoints];
            for (int i = 0; i < numPoints; i++) {
                throttleValues[i] = new ThrottleBrake(defaultThrottleValue, false);
            }
            spedUpSegments = new boolean[numPoints];
            slowedDownSegments = new boolean[numPoints];
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            ThrottleProfile newProfile = (ThrottleProfile) super.clone();
            newProfile.throttleValues = new ThrottleBrake[numPoints];
            for (int i = 0; i < numPoints; i++) {
                newProfile.throttleValues[i] = new ThrottleBrake(throttleValues[i].throttle, throttleValues[i].brake);
            }
            return newProfile;
        }

        public ThrottleBrake getThrottle(int section) {
            if (section == -1) {
                return defaultThrottle;
            }
            return throttleValues[section];
        }

        public boolean getBrake(int section) {
            if (section == -1) {
                return false;
            }
            return throttleValues[section].brake;
        }

        /**
         * Number of points in the throttleValues (same as number of spline
         * points in the track).
         */
        public int getNumPoints() {
            return numPoints;
        }

        public ThrottleBrake[] getProfile() {
            return throttleValues;
        }

        /**
         * Adds a throttle bump at a random location.
         */
        public void addBump() {
            Arrays.fill(spedUpSegments, false);
            // increase throttle settings around randomly around some track point
            int center = getNextThrottleBumpPoint();
            int m = (int) (numPoints * getFractionOfTrackToSpeedUp());
            log.info("speeding up " + m + " of " + numPoints + " throttle settings around track point " + center);
            for (int i = 0; i < m; i++) {
                float dist = Math.abs(i - (m / 2));
                float factor = ((m / 2) - dist) / (m / 2);
                int ind = getIndexFrom(center, i);
                throttleValues[ind].throttle = clipThrottle(throttleValues[ind].throttle + (throttleChange * factor)); // increase throttle by tent around random center point
                throttleValues[ind].brake = false;
                spedUpSegments[ind] = true;
            }
        }

        /**
         * Subtracts a rectangle of throttle starting at segment and continuing
         * back for fractionOfTrackToPunish. The amount subtracted is a fraction
         * of the throttleChange.
         *
         * @param segment the starting point of the subtraction, e.g. the
         * location just before the last crash.
         */
        public void subtractBump(int segment) {
            Arrays.fill(slowedDownSegments, false);
            int n = (int) (numPoints * fractionOfTrackToSlowDownPreCrash);
            log.info("reducing throttle starting from segment " + segment);
            try {
                for (int i = 0; i < n; i++) {
                    int seg = (segment - i);
                    if (seg < 0) { // if segment=1, then reduce 1, 0,
                        seg = numPoints + seg;
                    }
                    //                System.out.println("reducing "+seg);
                    throttleValues[seg].throttle = clipThrottle(throttleValues[seg].throttle - (throttleChange / 2));
                    slowedDownSegments[seg] = true;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }

        private void addBrake(int segment) {
            int n = numSegmentsToBrakeBeforeCrash;
            int s = segment - numSegmentsSpacingFromCrashToBrakingPoint;
            if (s < 0) {
                s = numPoints + s;
            }
            segment = s;
            log.info("braking for " + numSegmentsToBrakeBeforeCrash + " starting from segment " + segment);
            try {
                for (int i = 0; i < n; i++) {
                    int seg = (segment - i);
                    if (seg < 0) { // if segment=1, then reduce 1, 0,
                        seg = numPoints + seg;
                    }
                    //                System.out.println("reducing "+seg);
                    throttleValues[seg].brake = true;
                    //                    slowedDownSegments[seg] = true;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }

        public void resetMarkedSegments() {
            Arrays.fill(slowedDownSegments, false);
            Arrays.fill(spedUpSegments, false);
        }

        /**
         * Reduces speed on current throttleValues uniformly by throttleChange/3
         */
        public void slowDown() {
            for (int i = 0; i < numPoints; i++) {
                throttleValues[i].throttle = clipThrottle(throttleValues[i].throttle - (throttleChange / 3));
            }
        }

        /**
         * Increases speed on current throttleValues uniformly by
         * throttleChange/3
         */
        public void speedUp() {
            for (int i = 0; i < numPoints; i++) {
                throttleValues[i].throttle = clipThrottle(throttleValues[i].throttle + (throttleChange / 3));
            }
        }

        /**
         * returns the segment at distance from center.
         *
         * @param center the center segment index of the computation.
         * @param distance the distance; positive to advance, negative to
         * retard.
         * @return the segment index.
         */
        private int getIndexFrom(int center, int distance) {
            int index = center + distance;
            if (index > (numPoints - 1)) {
                index = index - numPoints;
            } else if (index < 0) {
                index = index + numPoints;
            }
            return index;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ThrottleProfile: ");
            for (int i = 0; i < numPoints; i++) {
                sb.append(String.format(" %.2f", throttleValues[i].brake ? -1 : throttleValues[i].throttle));
            }
            return sb.toString();
        }

        /**
         * Resets all throttle values to defaultThrottleValue, unsets all brake
         * segments.
         */
        private void reset() {
            log.info("reset all throttle settings to defaultThrottle=" + defaultThrottleValue);
            for (ThrottleBrake t : throttleValues) {
                t.set(defaultThrottleValue, false);
            }
            resetMarkedSegments();
        }

		// chooses next spot to appendCopy throttle, based on previous throttle throttleValues.
        // The higher the previous throttle, the less likely to choose it.
        private int getNextThrottleBumpPoint() {
			// do accept/reject sampling to getString next throttle bump center point, such that
            // the higher the throttle is now, the smaller the chance we increase the throttle there.
            // So, we treat (1-throttleValues[i]) as a likehood of choosing a new throttle.
            // We uniformly pick a bin from B in 1:numPoints and a value V in 0:1 and see if that particular
            // throttleValues[B]<V then we select it as the center. That way, the higher the throttle,
            // the less the chance to selecting that location to be the center of the next bump.

            int tries = numPoints * 3;
            while (tries-- > 0) {
                float v = random.nextFloat();
                int b = random.nextInt(numPoints);
                if (throttleValues[b].throttle < v) {
                    return b;
                }
            }
            return random.nextInt(numPoints); //. give up and just choose one uniformly
        }

        private void editToggleBrake(int idx) {
            if ((idx < 0) || (idx >= numPoints)) {
                return;
            }
            throttleValues[idx].brake = !throttleValues[idx].brake;
        }

        private void editSetBrake(int idx) {
            if ((idx < 0) || (idx >= numPoints)) {
                return;
            }
            throttleValues[idx].brake = true;
        }

        private void editClearBrake(int idx) {
            if ((idx < 0) || (idx >= numPoints)) {
                return;
            }
            throttleValues[idx].brake = false;
        }

        private void editIncreaseThrottle(int idx) {
            if ((idx < 0) || (idx >= numPoints)) {
                return;
            }
            throttleValues[idx].throttle = min(throttleValues[idx].throttle + editThrottleChange, 1);
        }

        private void editDecreaseThrottle(int idx) {
            if ((idx < 0) || (idx >= numPoints)) {
                return;
            }
            throttleValues[idx].throttle = max(throttleValues[idx].throttle - editThrottleChange, 0);
        }

        private void guessThrottleFromTrackModel() {
            if (getTrack() == null) {
                log.warning("null track");
                return;
            }
            getTrack().updateCurvature();
            float[] curvatures = getTrack().getCurvatureAtPoints();
            for (int i = 0; i < curvatures.length; i++) {
                curvatures[i] = Math.abs(curvatures[i]);
            }
            final int nfilt = numPoints / 30;

            float[] smoothed = new float[curvatures.length];

            for (int i = nfilt - 1; i < curvatures.length; i++) {
                float s = 0;
                for (int j = 0; j < nfilt; j++) {
                    s += curvatures[i - j];
                }
                s /= nfilt;
                smoothed[i] = s;
            }
            for (int i = 0; i < (nfilt - 1); i++) {
                smoothed[i] = curvatures[i]; // TODO no filter here yet
            }

            float minCurv = Float.MAX_VALUE;
            for (float c : smoothed) {
                if (c < minCurv) {
                    minCurv = c;
                }
            }
            float maxCurv = Float.MIN_VALUE;
            for (float c : smoothed) {
                if (c > maxCurv) {
                    maxCurv = c;
                }
            }

            for (int idx = 0; idx < numPoints; idx++) {
                int shiftedIdx = idx - nfilt;
                if (shiftedIdx < 0) {
                    shiftedIdx = numPoints + shiftedIdx;
                }
                throttleValues[shiftedIdx].throttle = min(1, startingThrottleValue * 2 * (float) Math.pow((smoothed[idx] / maxCurv), .15));
            }
        }

        private void log() {
            if (!isLoggingEnabled()) {
                return;
            }
            learningLogger.log(lastTimestamp + " " + toString());
        }

    } // ThrottleProfile

    private float min(float a, float b) {
        return a < b ? a : b;
    }

    private float max(float a, float b) {
        return a > b ? a : b;
    }

    // mouse control of throttle throttleValues
    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes);
        if (glCanvas == null) {
            return;
        }
        if (yes) {
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    // helpers
    private Point getPixel(MouseEvent e) {
        if (canvas == null) {
            return null;
        }
        Point p = canvas.getPixelFromMouseEvent(e);
        if (canvas.wasMousePixelInsideChipBounds()) {
            return p;
        } else {
            return null;
        }
    }

    private boolean isShift(MouseEvent e) {
        if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isControl(MouseEvent e) {
        if (!e.isShiftDown() && e.isControlDown() && !e.isAltDown()) {
            return true;
        } else {
            return false;
        }
    }

    private int getIndex(MouseEvent e) {
        if (getTrack() == null) {
            log.warning("null track model");
            return -1;
        }
        Point p = getPixel(e);
        if (p == null) {
            return -1;
        }
        return getTrack().findClosestIndex(p, 0, true);
    }
    private int lastEditIdx = -1;

    enum EditState {

        Increae, Decrease, None
    };
    volatile EditState editState = EditState.None;

    @Override
    public void mouseDragged(MouseEvent e) {
        if (currentProfile == null) {
            return;
        }
        int idx = -1;
        if ((idx = getIndex(e)) == -1) {
            return;
        }
        if (idx != lastEditIdx) {
            if (e.isAltDown() && e.isShiftDown()) {
                // brake point
                currentProfile.editClearBrake(idx);
                currentProfile.log();
                editState = EditState.None;
                glCanvas.repaint();
            } else if (e.isAltDown() && !e.isShiftDown()) {
                // brake point
                currentProfile.editSetBrake(idx);
                currentProfile.log();
                editState = EditState.None;
                glCanvas.repaint();
            } else if (isShift(e)) {
                currentProfile.editIncreaseThrottle(idx);
                currentProfile.log();
                editState = EditState.Increae;
                glCanvas.repaint();
            } else if (isControl(e)) {
                currentProfile.editDecreaseThrottle(idx);
                currentProfile.log();
                editState = EditState.Decrease;
                glCanvas.repaint();
            } else {
                editState = EditState.None;
            }
        }
        lastEditIdx = idx;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (isShift(e)) {
            editState = EditState.Increae;
        } else if (isControl(e)) {
            editState = EditState.Decrease;
        } else {
            editState = EditState.None;
        }
    }
    private boolean hasBlendChecked = false;
    private boolean hasBlend = false;

    //    GLUT glut=new GLUT();
    /**
     * Displays the extracted track points
     */
    private void drawThrottlePainter(GLAutoDrawable drawable) {
        if (isSelected() && (getTrack() != null) && (getTrack().getPointList() != null) && (currentProfile != null)) {
            Point mp = glCanvas.getMousePosition();
            Point p = canvas.getChipPixelFromMousePoint(mp);
            if (p == null) {
                return;
            }
            GL2 gl = drawable.getGL().getGL2();
            checkBlend(gl);
            switch (editState) {
                case None:
                    gl.glColor4f(.25f, .25f, 0, .3f);
                    break;
                case Increae:
                    gl.glColor4f(0, .45f, 0, .5f);
                    break;
                case Decrease:
                    gl.glColor4f(.45f, .0f, 0, .5f);

            }
            gl.glPushMatrix();
            gl.glTranslatef(p.x, p.y, 0);
            if (quad == null) {
                quad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            glu.gluDisk(quad, 0, 5, 32, 1);
            gl.glPopMatrix();

            chip.getCanvas().checkGLError(gl, glu, "in drawThrottlePainterk");

        }
    }

    @Override
    public void setLoggingEnabled(boolean loggingEnabled) {
        super.setLoggingEnabled(loggingEnabled);
        learningLogger.setEnabled(loggingEnabled);
    }

}
