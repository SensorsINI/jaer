/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import ch.unizh.ini.jaer.projects.virtualslotcar.SlotcarTrack;
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
import java.io.File;
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
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.StateMachineStates;
import net.sf.jaer.util.TobiLogger;

import com.jogamp.opengl.util.awt.TextRenderer;
import net.sf.jaer.event.BasicEvent;

/**
 * Controls slot car adaptively according to speed of humanMask controlled car
 *
 * @author Tobi
 */
@Description("Controls slot car adaptively according to speed of human controlled car")
public class HumanVsComputerThrottleController extends AbstractSlotCarController implements SlotCarControllerInterface, FrameAnnotater, MouseListener, MouseMotionListener, PropertyChangeListener {

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
    private boolean showTrack = getBoolean("showTrack", true);
    private float minSegsPerSecToAllowBraking=getFloat("minSegsPerSecToAllowBraking",2);

    // racing, speed controller
    private boolean racingEnabled = false; // user enables explicitly to start race getBoolean("racingEnabled", false);
    private float raceControllerSegmentsAheadForConstantThrottle = getFloat("raceControllerSegmentsAheadForConstantThrottle", 40);
    private boolean raceFeedbackThrottleControlEnabled = getBoolean("raceFeedbackThrottleControlEnabled", true);
    private boolean onlyRunWhenHumanRunning = getBoolean("onlyRunWhenHumanRunning", false);


    // racing
    private boolean soundEffectsEnabled = getBoolean("soundEffectsEnabled", true);
    private int raceLengthLaps = getInt("raceLengthLaps", 5);
    private int raceLapsRemaining = raceLengthLaps;
    private int RACE_SETUP_TIME_MS = 4000, RACE_GO_TIME_MS = 1500, REACTION_TIME_MS=500;
    private long prepareToRaceStartedTimeMs = 0;

    // logging
    private int lastTimestamp = 0;
    private TobiLogger computerLapTimeLogger=null, humanLapTimeLogger=null;
    

    /**
     * @return the onlyRunWhenHumanRunning
     */
    public boolean isOnlyRunWhenHumanRunning() {
        return onlyRunWhenHumanRunning;
    }

    /**
     * @param onlyRunWhenHumanRunning the onlyRunWhenHumanRunning to set
     */
    public void setOnlyRunWhenHumanRunning(boolean onlyRunWhenHumanRunning) {
        this.onlyRunWhenHumanRunning = onlyRunWhenHumanRunning;
        putBoolean("onlyRunWhenHumanRunning", onlyRunWhenHumanRunning);
    }

    /**
     * possible states,
     * <ol>
     * <li> STARTING means no car is tracked or tracker has not found a car
     * cluster near the track model,
     * <li> PRACTICE is the active state,
 <li> CRASHED is the state if we were PRACTICE and the car tracker has tracked
 the car sufficiently far away from the track model,
 </ol>
     */
    public enum State {

        OVERRIDDEN, STARTING, PRACTICE, CRASHED, RACING, STOPPED, PREPARE_TO_RACE, WAITING_FOR_GO, WAITING_ADDITIONAL_REACTION_TIME
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
    private int computerTrackPosition, humanTrackPosition; // position in spline parameter of track
    private int lastRewardLap = 0;
    private ThrottleProfile currentProfile, lastSuccessfulProfile, lastSuccessfulProfileEvenOlder;
    private Random random = new Random();
    private LapTimer computerLapTimer = null, humanLapTimer = null;
    private int lapTime;
    private int prevLapTime;
    private FilterChain filterChain;
    private CarTracker computerCarTracker, humanCarTracker;
    private CarTracker.CarCluster computerCar = null, humanCar = null;
    private boolean showedMissingTrackWarning = false;
    private SlotCarSoundSample sound_crash = null, sound_computer_winner = null, sound_human_winner = null, sound_go = null, sound_get_ready_to_race = null, sound_laps_to_go[] = null, sound_tie_race = null, sound_on_your_marks = null;
    private int lastCrashLocation = -1;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    TobiLogger learningLogger = new TobiLogger("HumanVsComputerThrottleController", "throttle settings log for slot car racer during learning");
    private SlotcarTrack track;
    private String trackFileName = getString("trackFileName", null);

    Histogram2DFilter computerMask = null, humanMask = null; // later refer to enclosed filters in CarTrackers
    private String computerTrackHistogramFilePath = getString("computerTrackHistogramFilePath", "computerTrackHistogramMask.dat");
    private String humanTrackHistogramFilePath = getString("humanTrackHistogramFilePath", "humanTrackHistogramMask.dat");
    BackgroundActivityFilter backgroundActivityFilter = null;

    public HumanVsComputerThrottleController(AEChip chip) {
        super(chip);
        track = new SlotcarTrack(chip);

        final String s = "EvolutionaryThrottleController";
        setPropertyTooltip(s, "defaultThrottle", "default throttle setting if no car is detected; also starting throttle after resetting learning and minimum allowed throttle");
        setPropertyTooltip(s, "fractionOfTrackToPunish", "fraction of track to reduce throttle and mark for no reward");
        setPropertyTooltip(s, "learningEnabled", "enable evolution - successful profiles are sped up, crashes cause reversion to last successful profile");
        setPropertyTooltip(s, "throttleChange", "max amount to increase throttle for learning perturbation");
        setPropertyTooltip(s, "editThrottleChange", "amount to change throttle for mouse edits of the throttle profile");
        setPropertyTooltip(s, "numSuccessfulLapsToReward", "number of successful (no crash) laps between rewards");
        setPropertyTooltip(s, "numSegmentsToBrakeBeforeCrash", "number track segments to brake for just prior to crash location");
        setPropertyTooltip(s, "numSegmentsSpacingFromCrashToBrakingPoint", "number track segments before crash that braking segments start");
        setPropertyTooltip(s, "fractionOfTrackToSpeedUp", "fraction of track spline points to increase throttle on after successful laps");
        setPropertyTooltip(s, "fractionOfTrackToSlowDownPreCrash", "fraction of track spline points before crash point to reduce throttle on");
        setPropertyTooltip(s, "minSegsPerSecToAllowBraking", "Electronic braking is disabled when car speed drops below this value in track segments per second to avoid stalling cars.  Set to 0 to always use braking.");

        String misc = "Misc. control";
        setPropertyTooltip(misc, "showThrottleProfile", "displays the throttle profile, with dot size reprenting the throttle value");
        setPropertyTooltip(misc, "showTrack", "displays the track");
        setPropertyTooltip(misc, "displayClosestPointMap", "displays the 2d matrix of distances to track points in a separate JFrame");
        setPropertyTooltip(misc, "loggingEnabled", "enables logging of learning progress and other detailed information to a file HumanVsComputerThrottleController.txt in startup folder");
        setPropertyTooltip(misc, "stop", "stop computer car by setting throttle to zero");
        setPropertyTooltip(misc, "start", "start comuputer car (stop any existing race also) with existing profile; set state to State.STARTING");

        final String hvc = "HumanVsComputer + Throttle config.";
        setPropertyTooltip(hvc, "startingThrottleValue", "throttle value when starting (no car cluster detected)");
        setPropertyTooltip(hvc, "humanTrackHistogramFilePath", "path to histogram mask for human track");
        setPropertyTooltip(hvc, "computerTrackHistogramFilePath", "path to histogram mask for computer track");
        setPropertyTooltip(hvc, "trackFileName", "file name (full path) to track file produced by TrackDefineFilter");
        // do methods
        setPropertyTooltip(hvc, "loadComputerTrackHistogramMask", "load the histogram mask for computer track");
        setPropertyTooltip(hvc, "loadHumanTrackHistogramMask", "load the histogram mask for human track");
        // racing
        setPropertyTooltip(hvc, "raceLengthLaps", "number of laps for a race");
        setPropertyTooltip(hvc, "startRace", "Reset lap timers and start race");
        setPropertyTooltip(hvc, "raceControllerGain", "gain applied to modulation of computer throttle profile relative to fastest profile");
        setPropertyTooltip(hvc, "racingEnabled", "an actual racw with laps is happening now");
        setPropertyTooltip(hvc, "raceControllerSegmentsAheadForConstantThrottle", "number of segments computer is ahead by to reduce speed of computer car to starting throttle value; otherwise if computer is tied or behind it drives as fast as possible");
        setPropertyTooltip(hvc, "stopRace", "stop race");
        setPropertyTooltip(hvc, "raceFeedbackThrottleControlEnabled", "enable adaptive control of computer throttle to slow down computer car if ahead of human");
        setPropertyTooltip(hvc, "soundEffectsEnabled", "play sound effects for racing");
        setPropertyTooltip(hvc, "onlyRunWhenHumanRunning", "only run the computer car when human car is running");

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
        setPropertyTooltip("loadTrack", "Load a track from disk that was extracted by TrackDefineFilter");

        doLoadThrottleSettings();

        filterChain = new FilterChain(chip);
        backgroundActivityFilter = new BackgroundActivityFilter(chip);
        filterChain.add(backgroundActivityFilter);

        // load existing last track if it is in preferences
        if (trackFileName != null) {
            try {
                SlotcarTrack newTrack = SlotcarTrack.loadFromFile(new File(trackFileName));
                track = newTrack;
            } catch (Exception e) {
                log.warning(e.toString());
            }
        }

        computerCarTracker = new CarTracker(chip);
        computerMask = computerCarTracker.getTrackHistogramFilter();
        computerCarTracker.setTrack(track);
        computerCarTracker.setEnclosed(true, this);
        computerCarTracker.setColorClustersDifferentlyEnabled(true);

        humanCarTracker = new CarTracker(chip);
        humanMask = humanCarTracker.getTrackHistogramFilter();
        humanCarTracker.setTrack(track);
        humanCarTracker.setEnclosed(true, this);
        humanCarTracker.setColorClustersDifferentlyEnabled(true);

        computerMask.loadHistogramFromFile(new File(computerTrackHistogramFilePath));
        humanMask.loadHistogramFromFile(new File(humanTrackHistogramFilePath));

        filterChain.add(computerCarTracker);
        filterChain.add(humanCarTracker);

        computerLapTimer = new LapTimer(getTrack());
        humanLapTimer = new LapTimer(getTrack());

        setEnclosedFilterChain(filterChain);
        try {
            sound_crash = new SlotCarSoundSample("wipeout2.wav"); // whoops
            sound_computer_winner = new SlotCarSoundSample("computerWon2.wav");
            sound_human_winner = new SlotCarSoundSample("congratsHuman3.wav");
            sound_tie_race = new SlotCarSoundSample("tie.wav");
            sound_get_ready_to_race = new SlotCarSoundSample("getReadyToRace2.wav");
            sound_go = new SlotCarSoundSample("go2.wav");
            sound_on_your_marks = new SlotCarSoundSample("onYourMarks2.wav");
            sound_laps_to_go = new SlotCarSoundSample[10];
            sound_laps_to_go[1] = new SlotCarSoundSample("finalLap.wav");
            for (int k = 1; k <= 8; k++) {
                sound_laps_to_go[k + 1] = new SlotCarSoundSample((k + 1) + "toGo1.wav"); // we don't use 1 to go sound, this is same as final lap
            }

        } catch (Exception ex) {
            log.warning("Sound effect not available: " + ex.toString());
        }

        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }

        computerLapTimeLogger=new TobiLogger("SlotCarRacerLapTimesComputer","Computer HumanVsComputerThrottleController lap times");
        humanLapTimeLogger=new TobiLogger("SlotCarRacerLapTimesHuman","Human HumanVsComputerThrottleController lap times");
   }

    public void doLoadComputerTrackHistogramMask() {
        computerMask.doLoadHistogram();
        setComputerTrackHistogramFilePath(computerMask.getFilePath());
    }

    public void doLoadHumanTrackHistogramMask() {
        humanMask.doLoadHistogram();
        setHumanTrackHistogramFilePath(humanMask.getFilePath());
    }

    public void doLoadTrack() {
        track = SlotcarTrack.doLoadTrack();
        if (track != null) {
            setTrackFileName(track.getTrackName());
        }
    }

    private static enum Winner {

        Computer, Human, Tie, NoneSoFar
    };
    Winner winner = Winner.NoneSoFar;

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        setBigStatusText(state.toString(), bigStatusColor);
        if (in.getSize() > 0) {
            lastTimestamp = in.getLastTimestamp(); // for logging
        }

        if ((getTrack() != null) && ((currentProfile == null) || (currentProfile.getNumPoints() != getTrack().getNumPoints()))) {
            currentProfile = new ThrottleProfile(getTrack().getNumPoints());
            currentProfile.log();
            log.info("made a new ThrottleProfile :" + currentProfile);
        }

        EventPacket filtered = backgroundActivityFilter.filterPacket(in);
        if (track == null) {
            if (!showedMissingTrackWarning) {
                log.warning("Track not defined yet. Use the TrackdefineFilter to extract the slot car track or load the track from a file.");
            }
            showedMissingTrackWarning = true;
            return in;
        }
        computerCarTracker.filterPacket(in);
        humanCarTracker.filterPacket(in);

//        out = getEnclosedFilterChain().filterPacket(in); // does cartracker and maybe trackdefinefilter
        computerCar = computerCarTracker.findCarCluster();
        if (computerCar != null) {
            computerTrackPosition = computerCar.getSegmentIdx();
        }
        humanCar = humanCarTracker.findCarCluster();
        if (humanCar != null) {
            humanTrackPosition = humanCar.getSegmentIdx();
        }
        boolean computerCarCompletedLap = false, humanCarCompletedLap = false;
        if (humanCar != null) {
            humanCarCompletedLap = humanLapTimer.update(humanTrackPosition, humanCar.getLastEventTimestamp());
            if(humanCarCompletedLap && isLoggingEnabled()&&humanLapTimer.getPreviousLap()!=null){
                humanLapTimeLogger.log(String.format("HumanLap %d %s",humanLapTimer.lapCounter, humanLapTimer.getPreviousLap().toString()));
            }
        }
        if (computerCar != null) {
            computerCarCompletedLap = computerLapTimer.update(computerTrackPosition, computerCar.getLastEventTimestamp());
            if(computerCarCompletedLap && isLoggingEnabled() && computerLapTimer.getPreviousLap()!=null){
                computerLapTimeLogger.log(String.format("ComputerLap %d %s",computerLapTimer.lapCounter, computerLapTimer.getPreviousLap().toString()));
            }
       }
        // choose state & copyFrom throttle
        if (state.get() == State.OVERRIDDEN) {
            //            throttle.throttle = getStartingThrottleValue();

        } else if (state.get() == State.STARTING) {
            //            throttle.throttle = getStartingThrottleValue();
            if ((computerCar != null) && computerCar.isRunning()) {
                if (racingEnabled && (humanCar != null) && humanCar.isRunning()) {
                    state.set(State.RACING);
                } else {
                    state.set(State.PRACTICE);
                }
            }
        } else if (state.get() == State.PRACTICE) {
            if ((computerCar != null) && !computerCar.isCrashed()) {
                computeLearning(computerCarCompletedLap);
                throttle = currentProfile.getThrottle(computerCar.getSegmentIdx());
                if ((humanCar != null) && racingEnabled && humanCar.isRunning()) {
                    state.set(State.RACING);
                }
            }
             int computerLead=computerLapTimer.computeLeadInSegmentsNotCountingLaps(humanLapTimer);
             computeRacingThrottle(computerLead);

        } else if (state.get() == State.CRASHED) {
            state.set(State.STARTING);
        } else if (state.get() == State.PREPARE_TO_RACE) {
            if (state.timeSinceChanged() < RACE_SETUP_TIME_MS) {
                state.set(State.PREPARE_TO_RACE);
            } else {
                if (soundEffectsEnabled && (sound_on_your_marks != null)) {
                    sound_on_your_marks.play();
                }
                state.set(State.WAITING_FOR_GO);
            }
            throttle = stoppedThrottle;
        } else if (state.get() == State.WAITING_FOR_GO) {
            throttle = stoppedThrottle;
            if (state.timeSinceChanged() < RACE_GO_TIME_MS) {
                state.set(State.WAITING_FOR_GO);
            } else {
                if (soundEffectsEnabled && (sound_go != null)) {
                    sound_go.play();
                }
                state.set(State.WAITING_ADDITIONAL_REACTION_TIME);
            }
            throttle = stoppedThrottle;
        }else if(state.get()==State.WAITING_ADDITIONAL_REACTION_TIME){
            if(state.timeSinceChanged()<REACTION_TIME_MS){
                state.set(State.WAITING_ADDITIONAL_REACTION_TIME);
            }else{
                state.set(State.RACING);
            }
            throttle = stoppedThrottle;
        } else if (state.get() == State.RACING) {
            int computerLead = computerLapTimer.computeLeadInTotalSegments(humanLapTimer);
            if ((computerCar != null) && computerCar.isRunning()) {
                computeRacingThrottle(computerLead);
            } else {
                throttle = startingThrottle;
            }
            if (((computerLead <= 0) && humanCarCompletedLap) || ((computerLead >= 0) && computerCarCompletedLap)) {
                raceLapsRemaining--;
                if (soundEffectsEnabled && (raceLapsRemaining < 9) && (raceLapsRemaining > 0)) {
                    if (sound_laps_to_go[raceLapsRemaining] != null) {
                        sound_laps_to_go[raceLapsRemaining].play();
                    }
                }
                if (raceLapsRemaining == 0) { // winner!
                    if (computerLead > 0) {
                        winner = Winner.Computer;
                        if (soundEffectsEnabled && (sound_computer_winner != null)) {
                            sound_computer_winner.play();
                        }
                    } else if (computerLead < 0) {
                        winner = Winner.Human;
                        if (soundEffectsEnabled && (sound_human_winner != null)) {
                            sound_human_winner.play();
                        }
                    } else {
                        winner = Winner.Tie;
                        if (soundEffectsEnabled && (sound_tie_race != null)) {
                            sound_tie_race.play();
                        }
                    }
                    log.info("Winner is " + winner.toString() + " by " + Math.abs(computerLead));

                    setRacingEnabled(false);
                    state.set(State.STOPPED);
                }
            }
            if (computerLead > 0) {
                setBigStatusText(String.format("%s: Computer ahead by %d segments", state.toString(), Math.abs(computerLead)), bigStatusColor);
            } else if (computerLead < 0) {
                setBigStatusText(String.format("%s:  Human   ahead by %d segments", state.toString(), Math.abs(computerLead)), bigStatusColor);
            } else {
                setBigStatusText(String.format("%s:  Tied                        ", state.toString()), bigStatusColor);
            }
        }

        switch ((State)state.get()) {
            case PRACTICE:
            case RACING:
                if (onlyRunWhenHumanRunning && ((humanCar == null) || !humanCar.isVisible())) {
                    throttle = stoppedThrottle;
                }else  if ((computerCar == null) || !computerCar.isVisible() || (computerCar.getSegmentSpeedSPS()<getMinSegsPerSecToAllowBraking())) {
                    throttle = startingThrottle;
                }
                break;
            case CRASHED:
            case STARTING:
                throttle = startingThrottle;
                break;
            case OVERRIDDEN:
                throttle = defaultThrottle;
                break;

            case STOPPED:
            case PREPARE_TO_RACE:
            case WAITING_FOR_GO:
            case WAITING_ADDITIONAL_REACTION_TIME:
                throttle = stoppedThrottle;
                break;
            default:
                throw new Error("state " + state.state + " not found for RacerState, shouldn't happen: cannot set ThrottleBrake");
        }

        return in;
    }

    private float throttleReductionFactor=1;

    private void computeRacingThrottle(int computerLead) {
        if(computerCar==null) {
            throttle=startingThrottle;
            return;
        }
         ThrottleBrake maxThrottle = currentProfile.getThrottle(computerCar.getSegmentIdx());
        if(!raceFeedbackThrottleControlEnabled){
            throttle=maxThrottle;
            return;
        }
        throttle = new ThrottleBrake();
        throttle.copyFrom(maxThrottle);
        if (!throttle.brake && (throttle.throttle>startingThrottleValue) && (humanCar != null) && humanCar.isRunning()) {
            if (computerLead > 0) { // computer ahead, slow down computer car
                float reductionFactor = computerLead / raceControllerSegmentsAheadForConstantThrottle;
                if (reductionFactor > 1) {
                    reductionFactor = 1;
                }
                throttle.throttle *= (1 - reductionFactor);
                if (throttle.throttle < startingThrottleValue) {
                    throttle.throttle = startingThrottleValue;
                } else if (throttle.throttle > maxThrottle.throttle) {
                    throttle.throttle = maxThrottle.throttle;
                }
            }
        }
        throttleReductionFactor=throttle.throttle/maxThrottle.throttle;
    }

    private void computeLearning(boolean completedLap) throws RuntimeException {
        if ((computerCar == null) || (completedLap == false)) {
            return;
        }
        // did we lap?
        lapTime = computerLapTimer.getCurrentLap().laptimeUs;
        int dt = lapTime - prevLapTime;
        if (dt < 0) {
            log.info("lap time improved by " + (dt / 1000) + " ms");
        } else if (dt > 0) {
            log.info("lap time worsened by " + (dt / 1000) + " ms");
        }
        prevLapTime = lapTime;
        if (learningEnabled && ((computerLapTimer.lapCounter - lastRewardLap) > numSuccessfulLapsToReward)) {
            try {
                log.info("successfully drove " + computerLapTimer.lapCounter + " laps; cloning this profile and rewarding currentProfile");
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
            lastRewardLap = computerLapTimer.lapCounter;
        }
        if (computerCarTracker.getCrashedCar() != null) {
            state.set(State.CRASHED);
            lastCrashLocation = computerCarTracker.getCrashedCar().crashSegment;
            //                    throttle.throttle = getStartingThrottleValue(); // don't actually change profile, starting comes from getThrottle
            if (soundEffectsEnabled && (sound_crash != null)) {
                sound_crash.play();
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
                    currentProfile.addBrake(computerCarTracker.getCrashedCar().crashSegment);
                    currentProfile.log();
                } else {
                    currentProfile.subtractBump(computerCarTracker.getCrashedCar().crashSegment);
                    currentProfile.log();
                }
            }
            lastRewardLap = computerLapTimer.lapCounter; // don't reward until we make some laps from here
        }
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
            log.info("current throttle profile is "+currentProfile.toString());
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

    synchronized public void doStartRace() {
        if (soundEffectsEnabled && (sound_get_ready_to_race != null)) {
            sound_get_ready_to_race.play();
        }
        resetRace();
        state.set(State.PREPARE_TO_RACE);
        setRacingEnabled(true);
    }

    synchronized public void doStopRace() {
        setRacingEnabled(false);
        state.set(State.STARTING);
    }

    synchronized public void doStop() {
        state.set(State.STOPPED);
        setRacingEnabled(false);
    }

    synchronized public void doStart() {
        doStopRace();
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

    final ThrottleBrake startingThrottle = new ThrottleBrake(startingThrottleValue, false), stoppedThrottle = new ThrottleBrake(0, false);

    /** Returns the throttle value based on mixture of which state we are in and other criteria
     *
     * @return current throttle/brake settings
     */
    @Override
    public ThrottleBrake getThrottle() {
        return throttle;
    }

    @Override
    public String logControllerState() {
        return String.format("lastTimeStamp=%d\tstate=%s\tthrottle=%f\tbrake=%s\tcomputerCar=%s\thumanCar=%s", lastTimestamp, state, throttle.throttle, throttle.brake, computerCar, humanCar);
    }

    @Override
    public String logContents() {
        return "state currentTrackPos throttle car ";
    }

    @Override
    public void resetFilter() {
        state.set(State.STARTING);
        computerLapTimer.reset();
        getEnclosedFilterChain().reset();
        lastCrashLocation = -1;
        if (currentProfile != null) {
            currentProfile.resetMarkedSegments();
        }
        lastRewardLap = 0;
        state.set(State.STARTING);
        resetRace();
    }

    private void resetRace() {
        humanLapTimer.reset();
        computerLapTimer.reset();
        raceLapsRemaining = raceLengthLaps;
        winner = Winner.NoneSoFar;
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
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
        GL2 gl = drawable.getGL().getGL2();
        chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawThrottleProfile");
        computerCarTracker.setCarColor(Color.RED);
        humanCarTracker.setCarColor(Color.GREEN);

        if (isTextEnabled()) {
            String s;
            s = String.format("HumanVsComputerThrottleController\nDefine track with TrackDefineFilter and load that track here.\nState: %s\nLearning %s\ncomputer/human trackPosition: %d|%d/%d\nThrottle: %8.3f\nLast throttle reduction factor: %.2f\nComputer %s\nHuman %s\n%s\n%d laps remaining\nLast Winner: %s", state.toString(), learningEnabled ? "Enabled" : "Disabled", computerTrackPosition, humanTrackPosition, track == null ? 0 : getTrack().getNumPoints(), throttle.throttle, throttleReductionFactor, computerLapTimer.toString(), humanLapTimer.toString(), standings(), raceLapsRemaining, winner.toString());
            MultilineAnnotationTextRenderer.setFontSize(5);
            MultilineAnnotationTextRenderer.renderMultilineString(s);
        }
        if (showTrack && (track != null)) {
            track.draw(drawable);
        }
        if (showThrottleProfile) {
            drawThrottleProfile(drawable.getGL().getGL2());
        }
        highLightTrackPoint(gl, computerTrackPosition, Color.RED, 3, "computer");
        highLightTrackPoint(gl, humanTrackPosition, Color.GREEN, 3, "human");
        highLightTrackPoint(gl, lastCrashLocation, Color.PINK, 3, "last computer crash");

        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
        drawThrottlePainter(drawable);
        renderBigStatusText(drawable);

    }

    private String standings() {
        int lead = computerLapTimer.computeLeadInSegmentsNotCountingLaps(humanLapTimer);
        if (lead >= 0) {
            return String.format("Computer   --\nHuman     %d", -lead);
        } else {
            return String.format("Human      --\nComputer  %d", lead);
        }
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

    private void highLightTrackPoint(GL2 gl, int segment, Color color, float size, String label) {
        if ((getTrack() == null) || (segment < 0) || (segment >= getTrack().getNumPoints())) {
            return;
        }

        float[] rgb = color.getRGBComponents(null);
        gl.glColor3fv(rgb, 0);
        Point2D p = getTrack().getPoint(segment);
        if (p == null) {
            return;
        }
        final float h = size / 2;
        gl.glRectd(p.getX() - h, p.getY() - h, p.getX() + h, p.getY() + h);
        if (label != null) {
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
            }
            textRenderer.setColor(color);
            textRenderer.begin3DRendering();
            final int offset = 5;
            textRenderer.draw3D(label, (float) p.getX() + offset, (float) p.getY() + offset, 0, .2f);
            textRenderer.end3DRendering();
        }
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
        learningEnabled = learning;
        putBoolean("learningEnabled", learningEnabled);
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
        return track;
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

        // chooses next spot to append throttle, based on previous throttle throttleValues.
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
            Point p = canvas.getMousePixel();
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
        computerLapTimeLogger.setEnabled(loggingEnabled);
        humanLapTimeLogger.setEnabled(loggingEnabled);
    }

    /**
     * @return the computerTrackHistogramFilePath
     */
    public String getComputerTrackHistogramFilePath() {
        return computerTrackHistogramFilePath;
    }

    /**
     * @param path the computerTrackHistogramFilePath to set
     */
    public void setComputerTrackHistogramFilePath(String path) {
        String old = computerTrackHistogramFilePath;
        this.computerTrackHistogramFilePath = path;
        putString("computerTrackHistogramFilePath", computerTrackHistogramFilePath);
        getSupport().firePropertyChange("computerTrackHistogramFilePath", old, computerTrackHistogramFilePath);
    }

    /**
     * @return the humanTrackHistogramFilePath
     */
    public String getHumanTrackHistogramFilePath() {
        return humanTrackHistogramFilePath;
    }

    /**
     * @param path the humanTrackHistogramFilePath to set
     */
    public void setHumanTrackHistogramFilePath(String path) {
        String old = humanTrackHistogramFilePath;
        this.humanTrackHistogramFilePath = path;
        putString("humanTrackHistogramFilePath", humanTrackHistogramFilePath);
        getSupport().firePropertyChange("humanTrackHistogramFilePath", old, humanTrackHistogramFilePath);
    }

    // delegated
    public void setDisplayClosestPointMap(boolean displayClosestPointMap) {
        track.setDisplayClosestPointMap(displayClosestPointMap);
    }

    public boolean isDisplayClosestPointMap() {
        return track.isDisplayClosestPointMap();
    }

    /**
     * @return the trackFileName
     */
    public String getTrackFileName() {
        return trackFileName;
    }

    /**
     * @param trackFileName the trackFileName to set
     */
    public void setTrackFileName(String trackFileName) {
        String old = this.trackFileName;
        this.trackFileName = trackFileName;
        putString("trackFileName", trackFileName);
        getSupport().firePropertyChange("trackFileName", old, this.trackFileName);

    }

    /**
     * @return the showTrack
     */
    public boolean isShowTrack() {
        return showTrack;
    }

    /**
     * @param showTrack the showTrack to set
     */
    public void setShowTrack(boolean showTrack) {
        this.showTrack = showTrack;
        putBoolean("showTrack", showTrack);
    }

    /**
     * @return the racingEnabled
     */
    public boolean isRacingEnabled() {
        return racingEnabled;
    }

    /**
     * @param racingEnabled the racingEnabled to set
     */
    public void setRacingEnabled(boolean racingEnabled) {
        boolean old = this.racingEnabled;
        this.racingEnabled = racingEnabled;
//        putBoolean("racingEnabled", racingEnabled);
        getSupport().firePropertyChange("racingEnabled", old, this.racingEnabled);
    }

    /**
     * @return the raceControllerSegmentsAheadForConstantThrottle
     */
    public float getRaceControllerSegmentsAheadForConstantThrottle() {
        return raceControllerSegmentsAheadForConstantThrottle;
    }

    /**
     * @param raceControllerSegmentsAheadForConstantThrottle the
     * raceControllerSegmentsAheadForConstantThrottle to set
     */
    public void setRaceControllerSegmentsAheadForConstantThrottle(float raceControllerSegmentsAheadForConstantThrottle) {
        this.raceControllerSegmentsAheadForConstantThrottle = raceControllerSegmentsAheadForConstantThrottle;
        if (raceControllerSegmentsAheadForConstantThrottle < 1) {
            raceControllerSegmentsAheadForConstantThrottle = 1;
        }
        putFloat("raceControllerSegmentsAheadForConstantThrottle", raceControllerSegmentsAheadForConstantThrottle);
    }

    /**
     * @return the raceLengthLaps
     */
    public int getRaceLengthLaps() {
        return raceLengthLaps;
    }

    /**
     * @param raceLengthLaps the raceLengthLaps to set
     */
    public void setRaceLengthLaps(int raceLengthLaps) {
        if (raceLengthLaps < 1) {
            raceLengthLaps = 1;
        }
        this.raceLengthLaps = raceLengthLaps;
        putInt("raceLengthLaps", raceLengthLaps);
    }

    /**
     * @return the raceFeedbackThrottleControlEnabled
     */
    public boolean isRaceFeedbackThrottleControlEnabled() {
        return raceFeedbackThrottleControlEnabled;
    }

    /**
     * @param raceFeedbackThrottleControlEnabled the
     * raceFeedbackThrottleControlEnabled to set
     */
    public void setRaceFeedbackThrottleControlEnabled(boolean raceFeedbackThrottleControlEnabled) {
        this.raceFeedbackThrottleControlEnabled = raceFeedbackThrottleControlEnabled;
        putBoolean("raceFeedbackThrottleControlEnabled", raceFeedbackThrottleControlEnabled);
    }

    /**
     * @return the soundEffectsEnabled
     */
    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }

    /**
     * @param soundEffectsEnabled the soundEffectsEnabled to set
     */
    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        this.soundEffectsEnabled = soundEffectsEnabled;
        putBoolean("soundEffectsEnabled", soundEffectsEnabled);
    }

    /**
     * @return the minSegsPerSecToAllowBraking
     */
    public float getMinSegsPerSecToAllowBraking() {
        return minSegsPerSecToAllowBraking;
    }

    /**
     * @param minSegsPerSecToAllowBraking the minSegsPerSecToAllowBraking to set
     */
    public void setMinSegsPerSecToAllowBraking(float minSegsPerSecToAllowBraking) {
        this.minSegsPerSecToAllowBraking = minSegsPerSecToAllowBraking;
        putFloat("minSegsPerSecToAllowBraking",minSegsPerSecToAllowBraking);
    }
}
