/*
 * Copyright (C) 2022 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.ine.telluride.jaer.tell2022;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import gnu.io.NRSerialPort;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import javafx.geometry.Point2D;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseROI;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.SignedNumber;
import net.sf.jaer.eventprocessing.filter.CircularConvolutionFilter;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.SoundWavFilePlayer;
import net.sf.jaer.util.TobiLogger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Going fishing game from Under the sea Lets go Fishing from Pressman. It uses
 * an Arduino Nano to generate the PWM servo output for the rod pan tilt and to
 * read the ADC connected to the current sense amplifier reading the FSR402
 * (https://www.amazon.com/dp/B074QLDCXQ?psc=1&ref=ppx_yo2ov_dt_b_product_details)
 * conductance that senses the fish hanging on the hook. An LP38841T-1.5
 * regulator
 * (https://www.ti.com/general/docs/suppproductinfo.tsp?distId=26&gotoUrl=https://www.ti.com/lit/gpn/lp38841)
 * controls when the table pond turns.
 *
 * See https://youtu.be/AgESLgcEE7o for video of Gone Fishing robot.
 *
 * @author tobid, Julie Hasler (juliehsler)
 */
@Description("Let's go fishing (going fishing) game from Telluride 2022 with Tobi Delbruck and Julie Hasler")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class GoingFishing extends EventFilter2DMouseROI implements FrameAnnotater {

    FilterChain chain = null;
    RectangularClusterTracker tracker = null;
    Random random = new Random();

    // serial port stuff
    NRSerialPort serialPort = null;
    private String serialPortName = getString("serialPortName", "COM3");
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;
    private AdcReader adcReader = null;
    private boolean disableServos = false;
    private boolean enableFishing = getBoolean("enableFishing", false);
    private final int SERIAL_WARNING_INTERVAL = 100;
    private int serialWarningCount = 0;
    private boolean runPond = false;

    // ADC for FSR that might detect caught fish
    private volatile int lastAdcValue = -1; // make it volatile since adcValues are recieved in ADCReader thread and used in other threads
    private int caughtFishDetectorThreshold = getInt("caughtFishDetectorThreshold", 550);

    // PropertyChange events
    public static final String EVENT_ROD_POSITION = "rodPosition",
            EVENT_ROD_SEQUENCE = "rodSequence",
            EVENT_CLEAR_SEQUENCES = "clearSequences",
            EVENT_DIP_ROD = "dipRod",
            EVENT_SHAKE_OFF_FISH = "shakeOffFish",
            EVENT_RETURN_TO_START = "returnToStart",
            EVENT_MARK_COLLECTOR_LOCATION = "markCollector";

    public static final String SEQ_HOLE_0 = "hole0", SEQ_HOLE_1 = "hole1", SEQ_FISH_REMOVER = "fishRemover";
    private final String[] sequenceNames = {SEQ_HOLE_0, SEQ_HOLE_1, SEQ_FISH_REMOVER};

    // rod control
    private int zMin = getInt("zMin", 80);
    private int zMax = getInt("zMax", 100);

    // fishing rod dips
    RodDipper rodDipper = null;
    HashMap<String, RodSequence> rodSequences = null;
    private int currentRodsequenceIdx = 0;
    private float fishingHoleSwitchProbability = getFloat("fishingHoleSwitchProbability", 0.1f);
    private int fishingAttemptHoldoffMs = getInt("fishingAttemptHoldoffMs", 3000);
    private long lastFishingAttemptTimeMs = 0;
    private int rodReturnDurationMs = getInt("rodReturnDurationMs", 3000);
    private int rodRaiseDurationMs = getInt("rodRaiseDurationMs", 500);
    private float rodDipSpeedUpFactor = getFloat("rodDipSpeedUpFactor", 1f);
    // fish remover
    private RodSequence fishRemoverSequence = null;

    // marking rod tip
    private boolean markRodTip = false;
    private Point2D rodTipLocation;
    private boolean markFishingPoleCenter = false;
    private Point2D fishingPoolCenterLocation;

    // measuring speed of fish
    DescriptiveStatistics fishSpeedStats;

    // reinforcement learning
    private float learnedRodThetaOffsetDeg = getFloat("learnedRodThetaOffsetDeg", 0);
    private int learnedRodDipLeadLagMs = getInt("learnedRodDipLeadLagMs", 0);
    private float rodThetaSamplingSigmaDeg = getFloat("rodThetaSamplingSigmaDeg", 2);
    private int rodDipDelaySamplingSigmaMs = getInt("rodDipDelaySamplingSigmaMs", 100);
    private float nextRandomRodThetaOffsetDeg = 0; // we need to store the next random theta so we can initialize the angle to this angle to prevent swinging on initial movement
    private boolean zeroSamplingNoise = getBoolean("zeroSamplingNoise", false);

    private int rodDipDelayFixedOffset = getInt("rodDipDelayFixedOffset", 0);
    private float rodThetaFixedOffsetDeg = getFloat("rodThetaFixedOffsetDeg", 0);
    private boolean zeroOffsets = false;

    // state
    private long lastManualRodControlTime = 0;
    private static final long HOLDOFF_AFTER_MANUAL_CONTROL_MS = 1000;
    private float lastRodThetaDeg = -1;
    private int lastRodZDeg = -1;
    private long lastFishingRodMovementTime = 0; // for disabling servos automatically after some inactivity time
    private static final long TURNOFF_TIMEOUT_S = 120;

    // warnings
    private final int WARNING_INTERVAL = 100;
    private int missingInfoWarningCounter = 0;
    private int missingMarkedLocationsWarningCounter = 0;
    volatile private boolean fishingResultsNotSaved = false; // flag set when we get a new FishingResult, to warn to save

    // fishing results statistics
    private FishingResults fishingResults = new FishingResults();
    private TobiLogger fishingLogger = new TobiLogger("GoingFishing.csv", "GoingFishing detailed log of fishing attempts");
    private boolean fishWasCaught = false; // for graphics output for most recent fishing attempt

    // sounds
    SoundWavFilePlayer beepFishDetectedPlayer = null, beepDipStartedPlayer = null, beepFailurePlayer = null, beepSucessPlayer = null;

    public GoingFishing(AEChip chip) {
        super(chip);
        chain = new FilterChain(chip);
        chain.add(new XYTypeFilter(chip));
//        chain.add(new RefractoryFilter(chip));
        chain.add(new SpatioTemporalCorrelationFilter(chip));
//        chain.add(new CircularConvolutionFilter(chip));
        tracker = new RectangularClusterTracker(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        String ser = "Serial port", rod = "Rod control", ler = "Learning", enb = "Enable/Disable";
        setPropertyTooltip(ser, "serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip(ser, "serialBaudRate", "Baud rate (default 115200), upper limit 12000000");

        setPropertyTooltip(rod, "showFishingRodControlPanel", "show control panel for fishing rod");
        setPropertyTooltip(rod, "dipRod", "make a fishing movement");
        setPropertyTooltip(rod, "shakeOffFish", "run the fish shaker sequence");
        setPropertyTooltip(rod, "abortDip", "abort rod dipping if active");
        setPropertyTooltip(rod, "resetLearning", "reset learned theta and delay parameters");
        setPropertyTooltip(rod, "rodDipSpeedUpFactor", "factor by which to speed up rod dip sequence over recorded speed");
        setPropertyTooltip(rod, "markRodTipLocation", "Mark the location of rod tip and hook with next left mouse click");
        setPropertyTooltip(rod, "markFishingPoolCenter", "Mark the location of center of fishing pool with next left mouse click");
        setPropertyTooltip(rod, "zMin", "min rod tilt angle in deg");
        setPropertyTooltip(rod, "zMax", "max rod tilt angle in deg");
        setPropertyTooltip(rod, "fishingAttemptHoldoffMs", "holdoff time in ms between automatic fishing attempts");
        setPropertyTooltip(rod, "rodReturnDurationMs", "duration in ms of minimum-jerk movement back to starting point of fishing rod sequence");
        setPropertyTooltip(rod, "rodRaiseDurationMs", "duration in ms of minimum-jerk raising rod after hooking fish");

        setPropertyTooltip(enb, "runPond", "Turn on the pond motor via 1.5V regulator");
        setPropertyTooltip(enb, "disableServos", "disable servos");
        setPropertyTooltip(enb, "enableFishing", "enable automatic fishing");

        setPropertyTooltip(ler, "fishingHoleSwitchProbability", "chance of switching spots after each attempt");
        setPropertyTooltip(ler, "caughtFishDetectorThreshold", "threshold ADC count from FSR to detect that we caught a fish");
        setPropertyTooltip(ler, "rodDipDelaySamplingSigmaMs", "sigma of Gaussian-sampled delay in ms to sample new rod dip starting delay; 100ms is about 1cm for outer fish.");
        setPropertyTooltip(ler, "rodThetaSamplingSigmaDeg", "sigma in deg to sample new rod dip pan offsets; each deg is about 2.6mm and the fish mouth is about 20mm");
        setPropertyTooltip(ler, "zeroSamplingNoise", "Zero the sampling noise to see nominal behavior");
        setPropertyTooltip(ler, "zeroOffsets", "Zero the offsets to see nominal behavior");
        setPropertyTooltip(ler, "plotFishingResults", "(needs python and matplotlib installed) Plot the fishing results as scatter plot.");
        setPropertyTooltip(ler, "saveFishingResults", "Saves fishing results to " + FISHING_RESULTS_FILENAME_BASE);
        setPropertyTooltip(ler, "loadFishingResults", "Loads previous results from " + FISHING_RESULTS_FILENAME_BASE);
        setPropertyTooltip(ler, "rodDipDelayFixedOffset", "Apply a fixed offset in ms to the dip (- for lead, + for lag); 100ms is about 1cm for outer fish.");
        setPropertyTooltip(ler, "rodThetaFixedOffsetDeg", "Apply a fixed offset in deg to the dip (- for inwards, + for outwards)");
        rodSequences = new HashMap();
        for (String name : sequenceNames) {
            try {
                RodSequence seq = RodSequence.load(name);
                rodSequences.put(name, seq);
            } catch (Exception e) {
                log.warning("Could not load fishing rod movement sequence " + name + ": Caught " + e.toString());
            }
        }
        log.info("loaded " + rodSequences.toString());
        Object o = null;
        o = getObject("rodTipLocation", null);
        if (o instanceof java.awt.Point) {
            Point p = (java.awt.Point) o;
            rodTipLocation = new Point2D(p.x, p.y);
        } else {
            rodTipLocation = null;
        }
        o = getObject("fishingPoolCenterLocation", null);
        if (o instanceof java.awt.Point) {
            Point p = (java.awt.Point) o;
            fishingPoolCenterLocation = new Point2D(p.x, p.y);
        } else {
            fishingPoolCenterLocation = null;
        }
        try {
            String s;
            // https://www.soundjay.com/beep-sounds-1.html#google_vignette
            s = "src/org/ine/telluride/jaer/tell2022/beeps/beep-07a.wav";
            beepFishDetectedPlayer = new SoundWavFilePlayer(s);
            s = "src/org/ine/telluride/jaer/tell2022/beeps/beep-08b.wav";
            beepDipStartedPlayer = new SoundWavFilePlayer(s);
            s = "src/org/ine/telluride/jaer/tell2022/beeps/beep-03-fail.wav";
            beepFailurePlayer = new SoundWavFilePlayer(s);
            s = "src/org/ine/telluride/jaer/tell2022/beeps/success.wav";
            beepSucessPlayer = new SoundWavFilePlayer(s);
        } catch (Exception e) {
            log.warning("Could not load beep sound: " + e.toString());

        }
        fishSpeedStats = new DescriptiveStatistics(100);
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        in = getEnclosedFilterChain().filterPacket(in);
        checkForFishingAttempt();
        checkForTurningOffServosAndPond();

        return in;
    }

    private void checkForFishingAttempt() {
        long currentTimeMs = System.currentTimeMillis();
        if (roiRects == null || roiRects.isEmpty()) {
            if (missingInfoWarningCounter % WARNING_INTERVAL == 0) {
                log.warning("draw ROI for detecting fish before it comes to rod tip");
            }
            missingInfoWarningCounter++;
            return;
        }
        if (rodDipper != null && rodDipper.isAlive()) {
            return;
        }

        RodSequence dipSeq = rodSequences.get(SEQ_HOLE_0);
        if (currentTimeMs - lastManualRodControlTime > HOLDOFF_AFTER_MANUAL_CONTROL_MS) {
            LinkedList<Cluster> clusterList = tracker.getVisibleClusters();
            for (Cluster c : clusterList) {
                Point p = new Point((int) c.getLocation().x, (int) c.getLocation().y);
                int roi = isInsideWhichROI(p);
                long delay = 0;

                if (roi == 0) {
                    // The ROI entered matches the current fishing rod sequence ROI
                    // this ROI we drew contains a fish cluster
                    if (rodTipLocation == null || fishingPoolCenterLocation == null) {
                        if (missingMarkedLocationsWarningCounter % WARNING_INTERVAL == 0) {
                            log.warning("Set rod tip and center of pool locations to estimate delay for fishing motion");
                        }
                        missingMarkedLocationsWarningCounter++;
                    } else {
                        float fishSpeedPps = c.getSpeedPPS();
                        fishSpeedStats.addValue(fishSpeedPps);  // filter
                        fishSpeedPps = (float) fishSpeedStats.getPercentile(50); // get median value of speed to filter outliers
                        final Point2D clusterLoc = new Point2D(c.getLocation().x, c.getLocation().y);
                        Point2D rodTipRay = rodTipLocation.subtract(fishingPoolCenterLocation);
                        final float radius = (float) rodTipRay.magnitude();
                        Point2D clusterRay = clusterLoc.subtract(fishingPoolCenterLocation);
                        final double angleDegFishToTip = rodTipRay.angle(clusterRay);
                        final double angularSpeedDegPerS = (180 / Math.PI) * (fishSpeedPps / radius);
                        final double rotationPeriodS=360./angularSpeedDegPerS;
                        final int msForFishToReachRodTip = (int) (1000 * angleDegFishToTip / angularSpeedDegPerS);
                        final long timeToMinZMs = Math.round(dipSeq.timeToMinZMs / rodDipSpeedUpFactor);
                        if (msForFishToReachRodTip < timeToMinZMs) {
                            log.warning(String.format("msForFishToReachRodTip=%,d ms is less than rod sequence timeToMinZMs=%,d ms;\n"
                                    + "median speed=%.1f px/s, radius=%.1f px, angularSpeed=%.1f deg/s angleDegFishToTip=%.1f deg", msForFishToReachRodTip, timeToMinZMs,
                                    fishSpeedPps, radius, angularSpeedDegPerS, angleDegFishToTip));
                        } else {
                            delay = msForFishToReachRodTip - timeToMinZMs;
                            log.info(String.format("msForFishToReachRodTip=%,d ms is OK; less than rod sequence timeToMinZMs=%,d ms;\n"
                                    + "median speed=%.1f px/s, radius=%.1f px, angularSpeed=%.1f deg/s (rotationPeriodS=%.1fs) angleDegFishToTip=%.1f deg", msForFishToReachRodTip, timeToMinZMs,
                                    fishSpeedPps, radius, angularSpeedDegPerS, rotationPeriodS, angleDegFishToTip));
                        }
                    }
                    if (rodDipper == null || !rodDipper.isAlive()) {
                        if (rodSequences.get(SEQ_HOLE_0) == null) {
                            if (missingInfoWarningCounter % WARNING_INTERVAL == 0) {
                                log.warning("record a fishing rod dip sequence to try fishing with");
                            }
                            missingInfoWarningCounter++;
                            return;
                        }
//                        log.info(String.format("Detected fish in ROI at location %s becoming contained by ROI rectangle %s", c.getLocation(), roiRects.get(roi)));
                        if (!disableServos && enableFishing) {
                            dipRodWithHoldoffAndDelay(delay);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Annotates the display with GoingFishing stuff.
     *
     * @param drawable
     */
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        if (rodTipLocation != null) {
            gl.glPushMatrix();
            gl.glColor3f(1, 0, 0);
            DrawGL.drawCross(gl, (float) rodTipLocation.getX(), (float) rodTipLocation.getY(), 6, 0);
            DrawGL.drawStrinDropShadow(gl, 12, 0, 0, 0, Color.red, "Tip");
            gl.glPopMatrix();
        }
        if (fishingPoolCenterLocation != null) {
            gl.glPushMatrix();
            gl.glColor3f(1, 0, 0);
            DrawGL.drawCross(gl, (float) fishingPoolCenterLocation.getX(), (float) fishingPoolCenterLocation.getY(), 6, 0);
            DrawGL.drawStrinDropShadow(gl, 12, 0, 0, 0, Color.red, "Center");
            gl.glPopMatrix();
        }
        if (!isDisableServos()) { // draw rod pan/tilt
            gl.glLineWidth(2);
            gl.glColor3f(1, 1, 1);
            float len = 50;
            gl.glPushMatrix();
            DrawGL.drawLine(gl, 300, 220, -len, (float) (len * Math.sin((lastRodZDeg - 90) * Math.PI / 180f)), 1);
            gl.glPopMatrix();
            gl.glPushMatrix();
            DrawGL.drawLine(gl, 300, 200, -len, (float) (len * Math.sin((lastRodThetaDeg - 90) * Math.PI / 180f)), 1);
            gl.glPopMatrix();
        }
        final float adcY = chip.getSizeY() / 10;
        final float adcStartX = 2;
        if (lastAdcValue != -1) {
            gl.glLineWidth(5);
            float adcLen1 = 0, adcLen2 = 0;
            gl.glColor3f(0, 0, 1);
            gl.glPushMatrix();
            DrawGL.drawLine(gl, adcStartX, adcY, adcLen1, 0, 1);
            gl.glPopMatrix();
            if (lastAdcValue < caughtFishDetectorThreshold) {
                adcLen1 = .9f * chip.getSizeX() * ((lastAdcValue) / 1024f);
                adcLen2 = 0;
            } else {
                adcLen1 = .9f * chip.getSizeX() * ((caughtFishDetectorThreshold) / 1024f);
                adcLen2 = .9f * chip.getSizeX() * ((lastAdcValue - caughtFishDetectorThreshold) / 1024f);

            }
            gl.glColor3f(1, 0, 0);
            gl.glPushMatrix();
            DrawGL.drawLine(gl, adcStartX, adcY, adcLen1, 0, 1);
            gl.glPopMatrix();
            if (adcLen2 > 0) {
                gl.glColor3f(1, 1, 0);
                gl.glPushMatrix();
                DrawGL.drawLine(gl, adcStartX + adcLen1, adcY, adcLen2, 0, 1);
                gl.glPopMatrix();
            }
            gl.glPushMatrix();
            DrawGL.drawStrinDropShadow(gl, 12, adcStartX + adcLen1 + adcLen2 + 2, adcY, 0, Color.white, String.format("ADC: %d", lastAdcValue));
            gl.glPopMatrix();
        } else {
            gl.glPushMatrix();
            DrawGL.drawStrinDropShadow(gl, 12, adcStartX, adcY, 0, Color.white, "ADC: N/A");
            gl.glPopMatrix();
        }
        gl.glPushMatrix();
        if (fishWasCaught) {
            gl.glColor3f(1, 1, 1);
            DrawGL.drawStrinDropShadow(gl, 36, chip.getSizeX() / 2, chip.getSizeY() / 2, .5f, Color.white, "Caught!");
        }
        final float statsY = 2 * chip.getSizeY() / 10;
        String s = String.format("Tries: %d, Success: %d (%.1f%%); thetaOffset=%.1f deg, delayMs=%d ms",
                fishingResults.rodDipTotalCount, fishingResults.rodDipSuccessCount,
                (100 * (float) fishingResults.rodDipSuccessCount / fishingResults.rodDipTotalCount),
                learnedRodThetaOffsetDeg, learnedRodDipLeadLagMs);
        DrawGL.drawStrinDropShadow(gl, 10, adcStartX, statsY, 0, Color.white, s);

        gl.glPopMatrix();
    }

    @Override
    public void resetFilter() {
        fishingResults.clear();
        fishSpeedStats.clear();
    }

    @Override
    public void initFilter() {

    }

    public void doDipRod() {
        dipRodNow(0);
    }

    public void doShakeOffFish() {
        shakeOffFish();
    }

    public void doAbortDip() {
        if (rodDipper != null) {
            rodDipper.abort();
        }
    }

    public void doToggleOnRunPond() {
        setRunPond(true);
    }

    public void doToggleOffRunPond() {
        setRunPond(false);
    }

    public void doToggleOnEnableFishing() {
        setEnableFishing(true);
    }

    public void doToggleOffEnableFishing() {
        setEnableFishing(false);
    }

    public void doPlotFishingResults() {
        try {
            fishingResults.plot();
        } catch (Exception ex) {
            log.warning("cannot show the plot with pyplot - did you install python and matplotlib on path? " + ex.toString());
            showWarningDialogInSwingThread("<html>Cannot show the plot with pyplot - did you install python and matplotlib on path? <p>" + ex.toString(), "Cannot plot");
        }
    }

    private final String FISHING_RESULTS_FILENAME_BASE = "GoingFishingResults";

    private class SerializedFileFilter extends FileFilter {

        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            if (f.toString().toLowerCase().endsWith(FishingResults.SERIALIZED_SUFFIX)) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public String getDescription() {
            return "Serialized FishingResult (.ser files)";
        }
    }

    synchronized public void doSaveFishingResults() {
        try {
            File f = new File(getString(FISHING_RESULTS_FILENAME_BASE, FISHING_RESULTS_FILENAME_BASE + FishingResults.SERIALIZED_SUFFIX));
            JFileChooser chooser = new JFileChooser(f);
            chooser.setFileFilter(new SerializedFileFilter());
            chooser.setSelectedFile(f);
            int ret = chooser.showDialog(chip.getFilterFrame(), "Save serialized FishingResults to file");
            if (ret == JFileChooser.APPROVE_OPTION) {
                f = chooser.getSelectedFile();
                String base = FilenameUtils.removeExtension(f.toString());
                FishingResults.save(fishingResults, base);
                fishingResultsNotSaved = false;
                putString(FISHING_RESULTS_FILENAME_BASE, f.toString());
            }
        } catch (IOException ex) {
            showWarningDialogInSwingThread(ex.toString(), "Error loading results");
        }
    }

    synchronized public void doLoadFishingResults() {
        try {
            File f = new File(getString(FISHING_RESULTS_FILENAME_BASE, FISHING_RESULTS_FILENAME_BASE + FishingResults.SERIALIZED_SUFFIX));
            JFileChooser chooser = new JFileChooser(f);
            chooser.setFileFilter(new SerializedFileFilter());
            chooser.setSelectedFile(f);
            int ret = chooser.showDialog(chip.getFilterFrame(), "Select serialized FishingResults file");
            if (ret == JFileChooser.APPROVE_OPTION) {
                FishingResults results = FishingResults.load(chooser.getSelectedFile().toString());
                this.fishingResults = results;
            }
        } catch (IOException ex) {
            showWarningDialogInSwingThread(ex.toString(), "Error loading results");
        } catch (ClassNotFoundException ex) {
            showWarningDialogInSwingThread(ex.toString(), "Error loading results");
        }
    }

    private void enablePond(boolean enable) {
        if (!checkSerialPort()) {
            log.warning("serial port not open");
            return;
        }
        byte[] bytes = new byte[4];
        bytes[0] = enable ? (byte) 2 : (byte) 3; // send 2 to run, 3 to stop
        synchronized (serialPortOutputStream) {
            try {
                serialPortOutputStream.write(bytes);
                serialPortOutputStream.flush();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
        log.info(enable ? "Turned on pong" : "Turned off pond");
    }

    public void doResetLearning() {
        learnedRodDipLeadLagMs = 0;
        learnedRodThetaOffsetDeg = 0;
        putInt("learnedRodDipLeadLagMs", 0);
        putFloat("learnedRodThetaOffsetDeg", 0);

        fishingResults.clear();
    }

    public void doMarkRodTipLocation() {
        markRodTip = true;
        markFishingPoleCenter = false;
    }

    public void doMarkFishingPoolCenter() {
        markFishingPoleCenter = true;
        markRodTip = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        if (markRodTip) {
            markRodTip = false;
            rodTipLocation = new Point2D(clickedPoint.x, clickedPoint.y);
            putObject("rodTipLocation", new Point((int) rodTipLocation.getX(), (int) rodTipLocation.getY()));
        } else if (markFishingPoleCenter) {
            markFishingPoleCenter = false;
            fishingPoolCenterLocation = new Point2D(clickedPoint.x, clickedPoint.y);
            putObject("fishingPoolCenterLocation", new Point((int) fishingPoolCenterLocation.getX(), (int) fishingPoolCenterLocation.getY()));
        }
    }

    private void openSerial() throws IOException {
        if (serialPort != null && !serialPort.isConnected()) {
            closeSerial();
        }
        checkBaudRate(serialBaudRate);
        StringBuilder sb = new StringBuilder("List of all available serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        if (availableSerialPorts.isEmpty()) {
            sb.append("\nNo ports found, sorry.  If you are on linux, serial port support may suffer");
        } else {
            for (String s : availableSerialPorts) {
                sb.append(s).append(" ");
            }
        }
        if (!availableSerialPorts.contains(serialPortName)) {
            final String warningString = serialPortName + " is not in avaiable " + sb.toString();
            throw new IOException(warningString);
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            final String warningString = "null serial port returned when trying to open " + serialPortName + "; available " + sb.toString();
            throw new IOException(warningString);
        }
        if (serialPort.connect()) {
            if (serialPort.getOutputStream() == null) {
                log.warning("Serial port connected, but getOutputStream() still returned null");
                throw new IOException("no output stream");
            }
            serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
            serialPortInputStream = new DataInputStream(serialPort.getInputStream());
            // seems to desync ADC reads if we do following
//            while (serialPortInputStream.available() > 0) {
//                serialPortInputStream.read();
//            }
            adcReader = new AdcReader(serialPortInputStream);
            adcReader.start();
            log.info("opened serial port " + serialPortName + " with baud rate=" + serialBaudRate);
        } else {
            log.warning("cannot connect serial port" + serialPortName);
        }
    }

    private void closeSerial() {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) '0'); // rest; turn off servos
                serialPortOutputStream.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            serialPortOutputStream = null;
        }
        if (adcReader != null && adcReader.isAlive()) {
            adcReader.shutdown();
            try {
                adcReader.join(200);
            } catch (InterruptedException e) {
            }
        }

        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
        }
        lastRodThetaDeg = -1;
        lastRodZDeg = -1;
        lastAdcValue = -1;
        serialPort = null;
    }

    synchronized private boolean checkSerialPort() {
        if (serialPort == null || serialPortOutputStream == null || serialPortInputStream == null) {
            try {
                openSerial();
            } catch (IOException ex) {
                if (serialWarningCount++ == SERIAL_WARNING_INTERVAL) {
                    log.warning("couldn't open serial port " + serialPortName);
                    serialWarningCount = 0;
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void setFilterEnabled(boolean yes) {
        boolean old = this.filterEnabled;
        super.setFilterEnabled(yes);
        if (!yes && old) { // only turn off serial port if it was previously on
            disableServos();
            closeSerial();
        } else {
            checkSerialPort();
        }
    }

    @Override
    public void cleanup() { // called from swing thread
        if (fishingResultsNotSaved) {
            doSaveFishingResults();
        }
        disableServos();
        doToggleOffRunPond();
        closeSerial();
    }

    /**
     * @return the serialBaudRate
     */
    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    /**
     * @param serialBaudRate the serialBaudRate to set
     */
    public void setSerialBaudRate(int serialBaudRate) {
        try {
            checkBaudRate(serialBaudRate);
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    private void checkBaudRate(int serialBaudRate1) {
        if (serialBaudRate1 != 9600 && serialBaudRate1 != 115200) {
            showWarningDialogInSwingThread(String.format("Selected baud rate of %,d baud is neither 9600 or 115200, please check it", serialBaudRate1), "Invalid baud rate?");
            log.warning(String.format("Possible invalid baud rate %,d", serialBaudRate1));
        }
    }

    /**
     * @return the serialPortName
     */
    public String getSerialPortName() {
        return serialPortName;
    }

    /**
     * @param serialPortName the serialPortName to set
     */
    public void setSerialPortName(String serialPortName) {
        try {
            this.serialPortName = serialPortName;
            putString("serialPortName", serialPortName);
            openSerial();
        } catch (IOException ex) {
            log.warning("couldn't open serial port: " + ex.toString());
        }
    }

    /**
     * Either turns off servos or sets servo positions
     *
     * @param disable true to turn off servos
     * @param theta the angle of pan (in degrees, 0-180 as float, converted here
     * to 100-2000 us)
     * @param z and tilt (in degrees, 0-180)
     */
    private void sendRodPosition(boolean disable, float theta, int z) {
        if (checkSerialPort()) {
            if (disableServos) {
                enableServos();
            }
            lastFishingRodMovementTime = System.currentTimeMillis();

            int thetaUs = 800 + Math.round(2400f * theta / 180f);
            int zSent = (int) Math.floor(zMin + (((float) (zMax - zMin)) / 180) * z);
            try {

                // write theta (pan) and z (tilt) of fishing pole as two unsigned byte servo angles and degrees
                byte[] bytes = new byte[4]; // cmd, theta (2 bytes us pulse width), z (1 byte angle)
                bytes[0] = disable ? (byte) 1 : (byte) 0; // cmd
                bytes[1] = (byte) ((thetaUs >> 8) & 0xff);
                bytes[2] = (byte) (thetaUs & 0xff);
                bytes[3] = (byte) (zSent);
                synchronized (serialPortOutputStream) {
                    serialPortOutputStream.write(bytes);
                    serialPortOutputStream.flush();
                }
                this.lastRodThetaDeg = theta;
                this.lastRodZDeg = z;
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void disableServos() {
        setDisableServos(true);
    }

    private void enableServos() {
        setDisableServos(false);
    }

    private void checkForTurningOffServosAndPond() {
        long inactiveTimeS = (System.currentTimeMillis() - lastFishingRodMovementTime) / 1000;
        if ((isRunPond() || !isDisableServos()) && inactiveTimeS >= TURNOFF_TIMEOUT_S) {
            disableServos();
            setRunPond(false);
            log.info(String.format("Turned off servos and pond after %d s of inactivity", inactiveTimeS));
        }
    }

    public void doShowFishingRodControlPanel() {
        showFishingRodControPanel();
    }

    private JFrame fishingRodControlFrame = null;

    private void showFishingRodControPanel() {
        if (fishingRodControlFrame == null) {
            fishingRodControlFrame = new GoingFishingFishingRodControlFrame();
            fishingRodControlFrame.addPropertyChangeListener(this);
        }
        fishingRodControlFrame.setVisible(true);
    }

    /**
     * Do the fishing move
     *
     * @param holdoff set true to enforce a holdoff of fishingAttemptHoldoffMs,
     * false to dip unconditionally if servos are enabled and dip is not already
     * running since the end of the last attempt
     */
    private void dipRodWithHoldoffAndDelay(long delayMs) {

        final long dt = System.currentTimeMillis() - lastFishingAttemptTimeMs;

        if (dt > fishingAttemptHoldoffMs) {
            if (!beepFishDetectedPlayer.isPlaying()) {
                beepFishDetectedPlayer.play();
            }
            dipRodNow(delayMs);
        }
    }

    /**
     * Do the fishing move
     *
     * @param delayMs delay to set at start of dip in ms
     */
    private void dipRodNow(long delayMs) {
        enableServos();
        if (rodDipper != null && rodDipper.isAlive()) {
            log.warning("aborting running rod sequence");
            rodDipper.abort();
            try {
                rodDipper.join(100);
            } catch (InterruptedException e) {
            }
        }

        rodDipper = new RodDipper(EVENT_DIP_ROD, delayMs, false);

        rodDipper.start();
    }

    private boolean isFishCaught() {
//        log.info(String.format("lastADC=%,d, caughtFishDetectorThreshold=%,d", lastAdcValue, caughtFishDetectorThreshold));
        return lastAdcValue > caughtFishDetectorThreshold;

    }

    private class AdcReader extends Thread {

        DataInputStream stream;

        public AdcReader(DataInputStream stream) {
            this.stream = stream;
        }

        synchronized public void shutdown() {
            if (serialPortInputStream != null) {
                try {
                    serialPortInputStream.close();
                } catch (IOException e) {

                }
            }
            serialPortInputStream = null;
            return;

        }

        public void run() {
            if (serialPortInputStream == null) {
                return;
            }
            byte[] tmp = new byte[256];
            try {
                int n = serialPortInputStream.read(tmp);
                log.info(String.format("Cleared %d bytes from serial input buffer before reading ADC starts", n));
            } catch (IOException ex) {
                log.warning(ex.toString());
                return;
            }
            while (serialPortInputStream != null) {
                try {
                    byte[] buf = new byte[2];
                    serialPortInputStream.readFully(buf);
                    lastAdcValue = (int) ((buf[0] & 0xff) * 256 + (0xff & buf[1]));
//                    System.out.println(String.format("ADC: available=%d val=%d", navail, lastAdcValue));
                } catch (IOException ex) {
                    log.warning("serial port error while reading ADC value: " + ex.toString());
                    closeSerial();
                }
            }
        }
    }

    private class RodDipper extends Thread {

        RodSequence rodSequence = null;
        volatile boolean aborted = false;
        private long initialDelayMs = 0;
        private boolean returnToStart = false;
        private String command = null;

        /**
         * Make a new thread for dipping rod
         *
         * @param command what the thread should do
         * @param initialDelayMs some initial delay in ms before starting the
         * actual dipping motion (playback of RodSequence)
         * @param returnToStart whether to skip dip and just return smoothly to
         * starting point of sequence from current location
         */
        public RodDipper(String command, long initialDelayMs, boolean returnToStart) {
            setName("RodDipper");
            this.command = command;
            this.initialDelayMs = initialDelayMs;
            this.returnToStart = returnToStart;
            setPriority(Thread.MAX_PRIORITY-1);
        }

        // Samples an angle variation with normal dist sigma of rodThetaSamplingSigmaDeg centeted on learnedRodThetaOffsetDeg
        private float sampleRandomRodAngleOffsetDeg() {
            final double thetaSample = rodThetaSamplingSigmaDeg * (zeroSamplingNoise ? 0 : random.nextGaussian());
            float randomThetaOffsetDeg = (float) (learnedRodThetaOffsetDeg + thetaSample);
            return randomThetaOffsetDeg;
        }

        // samples a random lead/lag for dipping rod
        private int sampleRandomRodDipLeadLagMs() {
            final int delaySamp = (int) Math.round(rodDipDelaySamplingSigmaMs * (zeroSamplingNoise ? 0 : (random.nextGaussian())) + (zeroOffsets ? 0 : rodDipDelayFixedOffset));
            return delaySamp;
        }

        public void run() {
            switch (command) {
                case EVENT_DIP_ROD:
                    catchFish();
                    break;

                case EVENT_SHAKE_OFF_FISH:
                    shakeOffFish();
                    break;
                case EVENT_RETURN_TO_START:
                    returnToStart();
                    return;
                default:
                    log.warning("Do not know what to do with command " + command);

            }

        }

        private boolean catchFish() {

            rodSequence = rodSequences.get(SEQ_HOLE_0);
            if (rodSequence == null) {
                log.warning("No sequence recorded, please record one to fish for a fish at this fishing hole");
                return false;
            }

            if (rodSequence.size() == 0) {
                log.warning("sequence " + rodSequence + " is zero length");
                return false;
            }
            fishWasCaught = false;
            // Samples a delay around 0 with Gaussian spread of rodDipDelaySamplingSigmaMs around 0.
            // This delay reduces or increases time we wait to dip the rod after detecting fish and computing
            // the time it will take the fish to reach the rod tip location
            int randomDelayMs = sampleRandomRodDipLeadLagMs();
            // delay before starting including the random variation and the fixed one we try to learn away
            long actualInitialDelayMs = initialDelayMs + randomDelayMs + (zeroOffsets ? 0 : rodDipDelayFixedOffset);
            String delayString = "";
            if (actualInitialDelayMs > 0) {
                delayString = String.format("Initial delay is %,d ms from initialDelayMs=%,d ms and randomDelayMs=%,d ms", actualInitialDelayMs, initialDelayMs, randomDelayMs);
            } else {
                log.warning(String.format("Initial delay of %,d ms is negative because initialDelayMs=%,d ms and randomDelayMs=%,d ms", actualInitialDelayMs, initialDelayMs, randomDelayMs));
            }
            log.info(String.format("Rod dip %s, with rod angle Theta offset learned+sample=%.1f + %.1f deg",
                    delayString, learnedRodThetaOffsetDeg, nextRandomRodThetaOffsetDeg));
            beepDipStartedPlayer.play();
            if (actualInitialDelayMs > 0) {
                try {
                    Thread.sleep(actualInitialDelayMs);
                    beepDipStartedPlayer.play();
                } catch (InterruptedException e) {
                    log.info("Interrupted rod sequence during initial delay");
                    return true;
                }
            }
            boolean fishCaught = false; // flag set if we ever detect we caught a fish
            int counter = 0;
            // create an offset that applies to entire sequence including the fixed offset that we try to learn away
            final float angleOffsetDeg = nextRandomRodThetaOffsetDeg + (zeroOffsets ? 0 : rodThetaFixedOffsetDeg);
            for (RodPosition p : rodSequence) {
                if (aborted) {
                    log.info("rod sequence aborted");
                    break;
                }
                if (p.delayMsToNext > 0) {
                    try {
                        long delMs = Math.round(p.delayMsToNext / rodDipSpeedUpFactor);
                        sleep(delMs); // sleep before move, first sleep is zero ms
                    } catch (InterruptedException e) {
                        log.info("rod sequence interrupted");
                        break;
                    }
                }
                sendRodPosition(false, (int) Math.round(p.thetaDeg + angleOffsetDeg), p.zDeg);
                if (!fishCaught && isFishCaught()) {
                    fishCaught = true;
                    log.info(String.format("***** Detected fish caught at rod position # %d", counter));
                    break;  // break out of loop here so we can raise the fish up
                }
                counter++;
            }
            // evaluate if we caught the fish
            if (fishCaught) {
                fishWasCaught = true;
                beepSucessPlayer.play();
                fishingResults.add(true, angleOffsetDeg, randomDelayMs);
                log.info(String.format("***** Success! %s\n Fishing disabled until fish removed", fishingResults.toString()));
                learnedRodThetaOffsetDeg = fishingResults.rodThetaOffset;
                learnedRodDipLeadLagMs = fishingResults.rodDipDelayMs;
                putFloat("learnedRodThetaOffsetDeg", angleOffsetDeg);
                putInt("learnedRodDipLeadLagMs", randomDelayMs);
                setEnableFishing(false);
                raiseRod();
                shakeOffFish();
                return true;
            } else {
                fishWasCaught = false;
                fishingResults.add(false, angleOffsetDeg, randomDelayMs);
                beepFailurePlayer.play();
            }
            fishingResultsNotSaved = true;
            if (aborted) {
                return true;
            }
            nextRandomRodThetaOffsetDeg = sampleRandomRodAngleOffsetDeg();
            returnToStart();
            lastFishingAttemptTimeMs = System.currentTimeMillis();
            return false;
        }

        private void moveSmoothly(RodPosition startingPosition, RodPosition endingPosition, int durationMs) {
            // move smoothly with minimum jerk back to starting position
            final Point2D start = new Point2D(startingPosition.thetaDeg, startingPosition.zDeg);
            final Point2D end = new Point2D(endingPosition.thetaDeg, endingPosition.zDeg);
            final double sampleRateHz = 25;
            final double dtS = 1 / sampleRateHz;
            final int dtMs = (int) Math.floor(dtS * 1000);
            final int dtRemNs = (int) (1e9 * (dtS - dtMs / 1000.));
            final ArrayList<Point2D> traj = minimumJerkTrajectory(start, end, sampleRateHz, durationMs);
//                log.info(String.format("returning to starting position in %,d ms with minimum jerk trajectory of %d points", rodReturnDurationMs, traj.size()));
            for (Point2D p : traj) {
                int theta = (int) p.getX();
                int z = (int) p.getY();
                sendRodPosition(false, theta, z);
                try {
                    Thread.sleep(dtMs, dtRemNs);
                } catch (InterruptedException ex) {
                    return;
                }
            }
            sendRodPosition(false, endingPosition.thetaDeg, endingPosition.zDeg);
        }

        // returns smoothly to the next starting position for rod including the next random theta angle offset
        private void returnToStart() {
            final RodPosition positionNow = new RodPosition(0, lastRodThetaDeg, lastRodZDeg);
            final RodSequence seq = rodSequences.get(SEQ_HOLE_0);
            if (seq == null || seq.isEmpty()) {
                return;
            }
            RodPosition startingPosition=seq.get(0);
            final RodPosition nextStartingPosition = new RodPosition(0, startingPosition.thetaDeg, startingPosition.zDeg);
            nextStartingPosition.thetaDeg += nextRandomRodThetaOffsetDeg;
            moveSmoothly(positionNow, nextStartingPosition, rodReturnDurationMs);
        }

        private void shakeOffFish() {
            if (!isFishCaught()) {
                log.info("fish is not caught, will not try to shake it off");
                returnToStart();
                return;
            }
            RodSequence rodSequence = rodSequences.get(SEQ_FISH_REMOVER);
            if (rodSequence == null || rodSequence.size() == 0) {
                log.warning("Cannot shake off the fish until there is a fish remover sequence");
                return;
            }

            final RodPosition curPos = new RodPosition(0, lastRodThetaDeg, lastRodZDeg);
            moveSmoothly(curPos, rodSequence.get(0), rodReturnDurationMs);
            // now shake rod (play shake sequence), then check ADC if fish gone, repeat a few times unti we give up
            int triesLeft = 3;
            while (triesLeft-- > 0 && isFishCaught()) {
                log.info("shaking off fish, " + triesLeft + " tries left");
                for (RodPosition p : rodSequence) {
                    if (aborted) {
                        log.info("rod sequence aborted");
                        break;
                    }
                    if (p.delayMsToNext > 0) {
                        try {
                            long delMs = Math.round(p.delayMsToNext / rodDipSpeedUpFactor);
                            sleep(delMs); // sleep before move, first sleep is zero ms
                        } catch (InterruptedException e) {
                            log.info("rod sequence interrupted");
                            break;
                        }
                    }
                    sendRodPosition(false, (int) Math.round(p.thetaDeg), p.zDeg);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.info("interrupted sleep at the end of shaking off sequence");

                }
            }
            if (isFishCaught()) {
                log.info("*** Sucessfully shook off the caught fish!!!");
            } else {
                log.warning("Could not shake off fish, please remove it for me");
            }
        }

        private void raiseRod() {
            final RodPosition curPos = new RodPosition(0, lastRodThetaDeg, lastRodZDeg);
            final RodPosition endingPosition = new RodPosition(0, lastRodThetaDeg, 180);

            moveSmoothly(curPos, endingPosition, rodRaiseDurationMs);
        }

        private void abort() {
            aborted = true;
            interrupt();
        }

        /**
         * https://mika-s.github.io/python/control-theory/trajectory-generation/2017/12/06/trajectory-generation-with-a-minimum-jerk-trajectory.html
         */
        private ArrayList<Point2D> minimumJerkTrajectory(Point2D start, Point2D end,
                double sampleRateHz, int moveDurationMs) {
            int nPoints = (int) ((moveDurationMs / 1000.) * sampleRateHz);
            ArrayList<Point2D> trajectory = new ArrayList<Point2D>(nPoints);
            Point2D diff = end.subtract(start);
            for (int i = 1; i <= nPoints; i++) {
                double f = (double) i / nPoints;
                double fac = 10.0 * Math.pow(f, 3)
                        - 15.0 * Math.pow(f, 4)
                        + 6.0 * Math.pow(f, 5);
                Point2D d = diff.multiply(fac);
                Point2D nextPoint = start.add(d);
                trajectory.add(nextPoint);
            }
            return trajectory;
        }

    }// end of rod dipper thread

    /**
     * @return the zMin
     */
    public int getzMin() {
        return zMin;
    }

    /**
     * @param zMin the zMin to set
     */
    public void setzMin(int zMin) {
        if (zMin < 0) {
            zMin = 0;
        } else if (zMin > 180) {
            zMin = 180;
        }
        this.zMin = zMin;
        putInt("zMin", zMin);
        resendRodPosition();
    }

    private void resendRodPosition() {
        if (lastRodThetaDeg != -1 && lastRodZDeg != -1) {
            sendRodPosition(false, lastRodThetaDeg, lastRodZDeg);
        }
    }

    /**
     * @return the zMax
     */
    public int getzMax() {
        return zMax;
    }

    /**
     * @param zMax the zMax to set
     */
    public void setzMax(int zMax) {
        if (zMax < 0) {
            zMax = 0;
        } else if (zMax > 180) {
            zMax = 180;
        }
        this.zMax = zMax;
        putInt("zMax", zMax);
        resendRodPosition();
    }

    /**
     * Base method to handle PropertyChangeEvent. It call super.propertyChange()
     * and then initFilter() when the AEChip size is changed, allowing filters
     * to allocate memory or do other initialization. Subclasses can override to
     * add more PropertyChangeEvent handling e.g from AEViewer or
     * AEFileInputStream.
     *
     * @param evt the PropertyChangeEvent, by jAER convention it is a constant
     * starting with EVENT_
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        switch (evt.getPropertyName()) {
            case EVENT_ROD_POSITION:
                RodPosition rodPosition = (RodPosition) evt.getNewValue();
                log.info("rodPosition=" + rodPosition.toString());
                lastManualRodControlTime = System.currentTimeMillis();
                sendRodPosition(false, rodPosition.thetaDeg, rodPosition.zDeg);
                break;
            case EVENT_ROD_SEQUENCE:
                RodSequence newSeq = (RodSequence) evt.getNewValue();
                newSeq.save();
                rodSequences.put(newSeq.getName(), newSeq);
                log.info("got new " + rodSequences);
                showPlainMessageDialogInSwingThread(newSeq.toString(), "New rod sequence");
                try {
                    newSeq.plot();
                } catch (Exception e) {
                    log.warning("Cannot plot the sequence: " + e.toString());
                }
                break;
            case EVENT_CLEAR_SEQUENCES:
                rodSequences.clear();
                log.info("Sequences cleared");
                break;
            case EVENT_DIP_ROD:
                dipRodNow(0);
                break;
            case EVENT_SHAKE_OFF_FISH:
                shakeOffFish();
            default:
        }
    }

    private void shakeOffFish() {
        RodDipper dipper = new RodDipper(EVENT_SHAKE_OFF_FISH, 0, true);
        dipper.start();
    }

    /**
     * @return the disableServos
     */
    public boolean isDisableServos() {
        return disableServos;
    }

    /**
     * @param disableServos the disableServos to set
     */
    public void setDisableServos(boolean disableServos) {
        boolean old = this.disableServos;
        if (disableServos) {
            if (rodDipper != null) {
                rodDipper.abort();
            }
            sendRodPosition(true, 0, 0);
        }
        this.disableServos = disableServos;
        getSupport().firePropertyChange("disableServos", old, disableServos);
    }

    /**
     * @return the enableFishing
     */
    public boolean isEnableFishing() {
        return enableFishing;
    }

    /**
     * @param enableFishing the enableFishing to set
     */
    public void setEnableFishing(boolean enableFishing) {
        boolean old = this.enableFishing;
        this.enableFishing = enableFishing;
        putBoolean("enableFishing", enableFishing);
        getSupport().firePropertyChange("enableFishing", old, this.enableFishing);
        if (enableFishing && rodSequences.get(SEQ_HOLE_0) != null) {
            rodDipper = new RodDipper(EVENT_RETURN_TO_START, 0, true);
            log.info("moving rod to starting position");
            rodDipper.start();
        }
        setRunPond(enableFishing); // TODO check if we always want this
    }

    /**
     * @return the fishingHoleSwitchProbability
     */
    public float getFishingHoleSwitchProbability() {
        return fishingHoleSwitchProbability;
    }

    /**
     * @param fishingHoleSwitchProbability the fishingHoleSwitchProbability to
     * set
     */
    public void setFishingHoleSwitchProbability(float fishingHoleSwitchProbability) {
        if (fishingHoleSwitchProbability > 1) {
            fishingHoleSwitchProbability = 1;
        } else if (fishingHoleSwitchProbability < 0) {
            fishingHoleSwitchProbability = 0;
        }
        this.fishingHoleSwitchProbability = fishingHoleSwitchProbability;
        putFloat("fishingHoleSwitchProbability", fishingHoleSwitchProbability);
    }

    /**
     * @return the fishingAttemptHoldoffMs
     */
    public int getFishingAttemptHoldoffMs() {
        return fishingAttemptHoldoffMs;
    }

    /**
     * @param fishingAttemptHoldoffMs the fishingAttemptHoldoffMs to set
     */
    public void setFishingAttemptHoldoffMs(int fishingAttemptHoldoffMs) {
        this.fishingAttemptHoldoffMs = fishingAttemptHoldoffMs;
        putInt("fishingAttemptHoldoffMs", fishingAttemptHoldoffMs);
    }

    /**
     * @return the rodReturnDurationMs
     */
    public int getRodReturnDurationMs() {
        return rodReturnDurationMs;
    }

    /**
     * @param rodReturnDurationMs the rodReturnDurationMs to set
     */
    public void setRodReturnDurationMs(int rodReturnDurationMs) {
        this.rodReturnDurationMs = rodReturnDurationMs;
        putInt("rodReturnDurationMs", rodReturnDurationMs);
    }

    /**
     * @return the caughtFishDetectorThreshold
     */
    public int getCaughtFishDetectorThreshold() {
        return caughtFishDetectorThreshold;
    }

    /**
     * @param caughtFishDetectorThreshold the caughtFishDetectorThreshold to set
     */
    public void setCaughtFishDetectorThreshold(int caughtFishDetectorThreshold) {
        this.caughtFishDetectorThreshold = caughtFishDetectorThreshold;
        putInt("caughtFishDetectorThreshold", caughtFishDetectorThreshold);
    }

    /**
     * @return the rodThetaSamplingSigmaDeg
     */
    public float getRodThetaSamplingSigmaDeg() {
        return rodThetaSamplingSigmaDeg;
    }

    /**
     * @param rodThetaSamplingSigmaDeg the rodThetaSamplingSigmaDeg to set
     */
    public void setRodThetaSamplingSigmaDeg(float rodThetaSamplingSigmaDeg) {
        this.rodThetaSamplingSigmaDeg = rodThetaSamplingSigmaDeg;
        putFloat("rodThetaSamplingSigmaDeg", rodThetaSamplingSigmaDeg);
    }

    /**
     * @return the rodDipDelaySamplingSigmaMs
     */
    public int getRodDipDelaySamplingSigmaMs() {
        return rodDipDelaySamplingSigmaMs;
    }

    /**
     * @param rodDipDelaySamplingSigmaMs the rodDipDelaySamplingSigmaMs to set
     */
    @SignedNumber
    public void setRodDipDelaySamplingSigmaMs(int rodDipDelaySamplingSigmaMs) {
        this.rodDipDelaySamplingSigmaMs = rodDipDelaySamplingSigmaMs;
        putInt("rodDipDelaySamplingSigmaMs", rodDipDelaySamplingSigmaMs);
    }

    /**
     * @return the zeroSamplingNoise
     */
    public boolean isZeroSamplingNoise() {
        return zeroSamplingNoise;
    }

    /**
     * @param zeroSamplingNoise the zeroSamplingNoise to set
     */
    public void setZeroSamplingNoise(boolean zeroSamplingNoise) {
        this.zeroSamplingNoise = zeroSamplingNoise;
    }

    /**
     * @return the rodDipSpeedUpFactor
     */
    public float getRodDipSpeedUpFactor() {
        return rodDipSpeedUpFactor;
    }

    /**
     * @param rodDipSpeedUpFactor the rodDipSpeedUpFactor to set
     */
    public void setRodDipSpeedUpFactor(float rodDipSpeedUpFactor) {
        this.rodDipSpeedUpFactor = rodDipSpeedUpFactor;
        putFloat("rodDipSpeedUpFactor", rodDipSpeedUpFactor);
    }

    /**
     * @return the runPond
     */
    public boolean isRunPond() {
        return runPond;
    }

    /**
     * @param runPond the runPond to set
     */
    public void setRunPond(boolean runPond) {
        boolean old = this.runPond;
        this.runPond = runPond;
        enablePond(runPond);
        getSupport().firePropertyChange("runPond", old, runPond);
    }

    /**
     * @return the rodRaiseDurationMs
     */
    public int getRodRaiseDurationMs() {
        return rodRaiseDurationMs;
    }

    /**
     * @param rodRaiseDurationMs the rodRaiseDurationMs to set
     */
    public void setRodRaiseDurationMs(int rodRaiseDurationMs) {
        this.rodRaiseDurationMs = rodRaiseDurationMs;
        putInt("rodRaiseDurationMs", rodRaiseDurationMs);
    }

    /**
     * @return the rodDipDelayFixedOffset
     */
    public int getRodDipDelayFixedOffset() {
        return rodDipDelayFixedOffset;
    }

    /**
     * @param rodDipDelayFixedOffset the rodDipDelayFixedOffset to set
     */
    @SignedNumber
    public void setRodDipDelayFixedOffset(int rodDipDelayFixedOffset) {
        this.rodDipDelayFixedOffset = rodDipDelayFixedOffset;
        putInt("rodDipDelayFixedOffset", rodDipDelayFixedOffset);
    }

    /**
     * @return the rodThetaFixedOffsetDeg
     */
    public float getRodThetaFixedOffsetDeg() {
        return rodThetaFixedOffsetDeg;
    }

    /**
     * @param rodThetaFixedOffsetDeg the rodThetaFixedOffsetDeg to set
     */
    @SignedNumber
    public void setRodThetaFixedOffsetDeg(float rodThetaFixedOffsetDeg) {
        this.rodThetaFixedOffsetDeg = rodThetaFixedOffsetDeg;
        putFloat("rodThetaFixedOffsetDeg", rodThetaFixedOffsetDeg);
    }

    /**
     * @return the zeroOffsets
     */
    public boolean isZeroOffsets() {
        return zeroOffsets;
    }

    /**
     * @param zeroOffsets the zeroOffsets to set
     */
    public void setZeroOffsets(boolean zeroOffsets) {
        this.zeroOffsets = zeroOffsets;
    }

}
