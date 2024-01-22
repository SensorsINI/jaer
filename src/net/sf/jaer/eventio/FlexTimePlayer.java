/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import java.awt.Color;
import java.util.Iterator;

import com.jogamp.opengl.GLAutoDrawable;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Plays DVS & DAVIS recordings (with APS frames) at either constant time per
 * rendered frame or constant DVS event number per returned frame. Also allows
 * setting limits on frame duration to avoid ultra fast playback.
 *
 * @author tobid
 */
@Description("Plays DVS/DAVIS recordings (with APS frames) at constant frame duration or constant DVS event count or time interval per frame")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class FlexTimePlayer extends EventFilter2D implements FrameAnnotater {

    public enum Method {
        ConstantEventNumber, AreaEventCount //, ConstantFrameDuration
    };
    protected Method method = null;

    private boolean showStatistics = getBoolean("showStatistics", true);
    private int constantEventNumber = getInt("constantEventNumber", 10000);
    protected int constantFrameDurationUs = getInt("constantFrameDurationUs", 30000);
    protected int maxPacketDurationUs = getInt("maxPacketDurationUs", 0);
    protected int minPacketDurationUs = getInt("minPacketDurationUs", 0);
    // packets to hold output events and leftover events
    private ApsDvsEventPacket<ApsDvsEvent> outputPacket = new ApsDvsEventPacket(ApsDvsEvent.class),
            leftOverEvents = new ApsDvsEventPacket<>(ApsDvsEvent.class),
            leftOverEventsTmp = new ApsDvsEventPacket<>(ApsDvsEvent.class); // buffer used to copy leftover and input back to leftover
    // iterators for output packet that only is reset after packet is full and new one is started
    OutputEventIterator<ApsDvsEvent> outItr = outputPacket.outputIterator();
    private int firstEventTimestamp = 0, packetDurationUs = 0, packetEventCount = 0, renderedEventCount = 0; // for actual packet

    private ApsDvsEventPacket<ApsDvsEvent> emptyPacket = new ApsDvsEventPacket(ApsDvsEvent.class); // empty packet to return if there is no output packet yet

    private boolean packetWasFinished = true; // flag set when output packet is finished so output can be reset on next input packet

    private EngineeringFormat engFmt = new EngineeringFormat();

    // AreaEventCount stuff
    // counting events into subsampled areas, when count exceeds the threshold in any area, the slices are rotated
    protected int areaEventNumberSubsampling = getInt("areaEventNumberSubsampling", 5);
    private int[][] areaCounts = null;
    private int sx, sy;
    private volatile boolean showAreaCountAreasTemporarily = false;
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;

    private AEPlayer.PlaybackMode playbackModeOriginal = null;

    private boolean automaticallyControlInputRate = getBoolean("automaticallySetInputRate", true);
    private static final int LEFTOVER_EVENT_COUNT_LOW_THRESHOLD = 10000;
    private static final int LEFTOVER_EVENT_COUNT_HIGH_THRESHOLD = 100000;
    private static final int EVENT_COUNT_FILTER_TAU_MS = 1000;
    private LowpassFilter eventCountFilter = new LowpassFilter(EVENT_COUNT_FILTER_TAU_MS);

    private boolean enablePacketDurationLogging = false;
    private TobiLogger actualPacketDurationLogger = null;
    private int sliceDurationPacketCount = 0;

    public FlexTimePlayer(AEChip chip) {
        super(chip);
        setPropertyTooltip("constantEventNumber", "Number of DVS events per packet");
        setPropertyTooltip("constantFrameDurationUs", "Duration of packet in us");
        setPropertyTooltip("method", "<html>Method used to determine completed expoosure of DVS frame:"
                + "<ul>"
                + "<li> <i>ConstantEventNumber</i>: each DVS frame has the same total number of ON+OFF events</li>"
                + "<li> <i>AreaEventCount</i>: DVS frames are completed when any area is fulled with some total count of DVS events. The total area is subdivided into about 20 regions.</li>"
                + "</ul>");
        setPropertyTooltip("maxPacketDurationUs", "Maximum duration of packet in us; set to 0 to disable");
        setPropertyTooltip("minPacketDurationUs", "Minimum duration of packet in us; set to 0 to disable");
        setPropertyTooltip("areaEventNumberSubsampling", "How many bits to shift x and y addresses for AreaEventCount method; determines the size of the areas.");
        setPropertyTooltip("automaticallyControlInputRate", "Automatically set input packet duration or time to control the number of left over events.");
        setPropertyTooltip("showStatistics", "Displays statistics: event count, packet duration and FPS and effective slomo or speedup factor");
        engFmt.setPrecision(2);
        outputPacket.allocate(constantEventNumber);
        try {
            method = Method.valueOf(getString("method", Method.ConstantEventNumber.toString()));
        } catch (IllegalArgumentException e) {
            method = Method.ConstantEventNumber;
        }
        engFmt.setPrecision(1);
    }

    /**
     * Adds event to output and checks if enough events have been accumulated or
     * any of the other conditions have been satisfied
     *
     * @param e the event
     * @return true if packet is full
     */
    private boolean addEvent(ApsDvsEvent e) {
        if ((e.isFilteredOut())) {
            if (e.isApsData()) {
                log.severe("APS event is filtered, should not happen");
            } else if (e.isImuSample()) {
                log.severe("IMU event is filtered, should not happen");
            }
            return false; // filter out filtered events
        }
        ApsDvsEvent eout = outItr.nextOutput();
        eout.copyFrom(e); // copy the event to the output
        if (e.isDVSEvent()) {
            packetEventCount++;
            packetDurationUs = eout.getTimestamp() - firstEventTimestamp;
            if (method == Method.ConstantEventNumber) {

                // then packet is done
                if (isPacketDone(packetEventCount)) {
//                    log.fine(String.format("packet done with %,d DVS events", packetEventCount));
                    return true;
                }
            } else if (method == Method.AreaEventCount) {
                if (areaCounts == null) {
                    clearAreaCounts();
                }
                int areaCount = ++areaCounts[e.x >> areaEventNumberSubsampling][e.y >> areaEventNumberSubsampling];
                if (isPacketDone(areaCount)) {
//                    log.fine(String.format("packet done with %,d areaCounts DVS events", packetEventCount));
                    clearAreaCounts();
                    return true;
                }
            }
            return false;
        }
        return false; // cannot finish packet on APS or IMU
    }

    /**
     * Packet is done when either 1: enough events AND packet long enough, OR 2:
     * packet too long
     *
     * @param count
     * @return true if done
     */
    private boolean isPacketDone(int count) {
        if (maxPacketDurationUs > 0 && packetDurationUs >= maxPacketDurationUs) {
            // if packet gets too long, return unconditionally
            return true;
        }
        // now we need sufficient events
        if (count >= constantEventNumber) {
            if (minPacketDurationUs > 0 && packetDurationUs < minPacketDurationUs) {
                // if we have enough events but packet is not long enough, return false
                return false;
            }
            return true;
        }
        return false; // if no condition satisfied, we are not done
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!(in instanceof ApsDvsEventPacket)) {
            String s = String.format("FlexTimePlayer only works with DAVIS input packets\nActual input packet is %s", in.toString());
            log.severe(s);
            showWarningDialogInSwingThread(s, "Wrong input packet type");
            throw new RuntimeException(s);
        }
        ApsDvsEventPacket inPacket = (ApsDvsEventPacket) in;
        Iterator<ApsDvsEvent> inItr = inPacket.fullIterator();
        Iterator<ApsDvsEvent> leftoverItr = leftOverEvents.fullIterator();
        if (packetWasFinished) {
            outItr = outputPacket.outputIterator(); // reset output iterator to start a new output packet
            packetWasFinished = false;
            packetEventCount = 0;
        }

//        log.fine(String.format("%,d leftover, %,d input events", leftOverEvents.getSize(), inPacket.getSize()));
        while (leftoverItr.hasNext() || inItr.hasNext()) {
            // first take leftover events
            ApsDvsEvent e = leftoverItr.hasNext() ? (ApsDvsEvent) leftoverItr.next() : (ApsDvsEvent) inItr.next(); // first take leftover events
//            if (e.getReadoutType() == ApsDvsEvent.ReadoutType.SOF
//                    || e.getReadoutType() == ApsDvsEvent.ReadoutType.EOF
//                    || e.getReadoutType() == ApsDvsEvent.ReadoutType.SOE
//                    || e.getReadoutType() == ApsDvsEvent.ReadoutType.EOE) {
//                log.fine(e.toString());
//            }

            boolean packetDone = addEvent(e);

            if (packetDone) {
                finishPacket(leftoverItr, inItr);
                controlInputDataRate();
                return outputPacket; // should not come here often, only if there are no events in this period
            } // packetDone block
        } // while loop
        return emptyPacket; // if packet not complete, return empty packet
    }

    private void controlInputDataRate() {
        if (automaticallyControlInputRate) {
            // if leftOverEvents is growing,tAe slow down playback, otherwise speed it up
            if ((chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
                AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
                final int leftOverCount = leftOverEvents.getSize();
                if (leftOverCount < chip.getNumPixels() * 3 && player.getPlaybackMode() == AbstractAEPlayer.PlaybackMode.FixedPacketSize) {
                    player.setPlaybackMode(AbstractAEPlayer.PlaybackMode.FixedTimeSlice);
                    log.fine(String.format("Set play mode to fixed time slice %,d us to fill buffer", player.getTimesliceUs()));
                } else if (leftOverCount > chip.getNumPixels() * 10 && player.getPlaybackMode() == AbstractAEPlayer.PlaybackMode.FixedTimeSlice) {
                    player.setPlaybackMode(AbstractAEPlayer.PlaybackMode.FixedPacketSize);
                    player.setPacketSizeEvents(constantEventNumber / 2);
                    log.fine(String.format("Automatically set input packet size to constantEventNumber/2=%,d", constantEventNumber / 2));
                }
            }
        }
    }

    private void finishPacket(Iterator<ApsDvsEvent> leftoverItr, Iterator<ApsDvsEvent> inItr) {
        // this packet is done so store some state about it
        firstEventTimestamp = outputPacket.getLastTimestamp(); // to determine when to finish next packet
        renderedEventCount = packetEventCount;
        // Store duration and event count to file if logging is enabled
        if (actualPacketDurationLogger != null && actualPacketDurationLogger.isEnabled()) {
            actualPacketDurationLogger.log(String.format("%d\t%d\t%d", sliceDurationPacketCount++, packetDurationUs, packetEventCount));
        }

        // there could be events left in both input and leftover packets
        // we want to append the leftover input events to the end of the leftoverEvents packet
        // copy left over events from input packet to end of leftOverEvents packet
        OutputEventIterator tmpItr = leftOverEventsTmp.outputIterator();
        int nLeftOver = 0;
        while (leftoverItr.hasNext()) {
            ApsDvsEvent ev = leftoverItr.next();
            tmpItr.nextOutput().copyFrom(ev);
            nLeftOver++;
        }

        int nInLeftOver = 0;
        while (inItr.hasNext()) {
            ApsDvsEvent ev = inItr.next();

            tmpItr.nextOutput().copyFrom(ev);
//                    tmpItr.nextOutput().copyFrom(inItr.next());
            nInLeftOver++;
        }
        ApsDvsEventPacket tmp = leftOverEvents;
        leftOverEvents = leftOverEventsTmp;
        leftOverEventsTmp = tmp;
        if (nLeftOver > 0 || nInLeftOver > 0) {
            log.fine(String.format("Copied %,d leftover and %,d input events to buffer", nLeftOver, nInLeftOver));
        }
        packetWasFinished = true;
    }

    private void clearAreaCounts() {
        if (method != Method.AreaEventCount) {
            return;
        }
        if (areaCounts == null || areaCounts.length != 1 + (sx >> getAreaEventNumberSubsampling())) {
            int nax = 1 + (sx >> getAreaEventNumberSubsampling()), nay = 1 + (sy >> getAreaEventNumberSubsampling());
            areaCounts = new int[nax][nay];
        } else {
            for (int[] i : areaCounts) {
                Arrays.fill(i, 0);
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
        firstEventTimestamp = 0;
        packetDurationUs = 0;
        packetEventCount = 0;
        leftOverEvents.clear();
        outputPacket.clear();
    }

    @Override
    public void initFilter() {
        sx = getChip().getSizeX();
        sy = getChip().getSizeY();
        resetFilter();
    }

    /**
     * @return the constantEventNumber
     */
    public int getConstantEventNumber() {
        return constantEventNumber;
    }

    /**
     * @param constantEventNumber the constantEventNumber to set
     */
    synchronized public void setConstantEventNumber(int constantEventNumber) {
        this.constantEventNumber = constantEventNumber;
        putInt("constantEventNumber", constantEventNumber);
        outputPacket.allocate(constantEventNumber);
//        if (isFilterEnabled() && (chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
//            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
//            player.setFlexTimeEnabled();
//            player.setPacketSizeEvents(constantEventNumber); // ensure that we don't get more DVS events than can be returned in one of our out packets
//            log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
//        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if ((chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            if (yes) {
                playbackModeOriginal = player.getPlaybackMode();
                player.setFlexTimeEnabled();
                player.setPacketSizeEvents(constantEventNumber); // ensure that we don't get more DVS events than can be returned in one of our out packets
                log.info(String.format("set player to flex time mode with event count %,d", constantEventNumber));
            } else {
                if (playbackModeOriginal != null) {
                    player.setPlaybackMode(playbackModeOriginal);
                }
            }
        }
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
//        MultilineAnnotationTextRenderer.setColor(Color.CYAN);
//        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
//        MultilineAnnotationTextRenderer.setScale(.4f);
//        MultilineAnnotationTextRenderer.renderMultilineString(
//                String.format("%,10d events, %10ss",
//                        renderedEventCount,
//                        engFmt.format(1e-6 * packetDurationUs))
//        );
        if (showStatistics) {
            float packetDurationS = 1e-6f * packetDurationUs;
            final String durStr = engFmt.format(packetDurationS);
            float packetFPS = 1 / packetDurationS;
            final String fpsStr = engFmt.format(packetFPS);
            final float averageFPS = chip.getAeViewer().getFrameRater().getAverageFPS();
            final float sloMoFactor = packetFPS / averageFPS; // 500Hz/50Hz is 10X slomo
            final String sloMoFactorStr = engFmt.format(sloMoFactor);
            String sloMoString = sloMoFactor > 1 ? "X slomo" : "X speedup";
            String s = String.format("FlexTime %,7d events, %5ss, %7sFPS, %5s%s", renderedEventCount, durStr, fpsStr, sloMoFactorStr, sloMoString);

            DrawGL.drawStringDropShadow(drawable.getGL().getGL2(), 10, 0f, .5f, 0, Color.white, s);
        }
        if (method == Method.AreaEventCount && showAreaCountAreasTemporarily) {
            GL2 gl = drawable.getGL().getGL2();
            int d = 1 << getAreaEventNumberSubsampling();
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINES);
            for (int x = 0; x <= sx; x += d) {
                gl.glVertex2f(x, 0);
                gl.glVertex2f(x, sy);
            }
            for (int y = 0; y <= sy; y += d) {
                gl.glVertex2f(0, y);
                gl.glVertex2f(sx, y);
            }
            gl.glEnd();
        }

    }

    private void showAreasForAreaCountsTemporarily() {
        if (stopShowingStuffTask != null) {
            stopShowingStuffTask.cancel();
        }
        stopShowingStuffTask = new TimerTask() {
            @Override
            public void run() {
                showAreaCountAreasTemporarily = false;
            }
        };
        Timer showAreaCountsAreasTimer = new Timer();
        showAreaCountAreasTemporarily = true;
        showAreaCountsAreasTimer.schedule(stopShowingStuffTask, SHOW_STUFF_DURATION_MS);
    }

    /**
     * @return the method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(Method method) {
        this.method = method;
        putString("method", method.toString());
    }

    /**
     * @return the constantFrameDurationUs
     */
    public int getConstantFrameDurationUs() {
        return constantFrameDurationUs;
    }

    /**
     * @param constantFrameDurationUs the constantFrameDurationUs to set
     */
    public void setConstantFrameDurationUs(int constantFrameDurationUs) {
        this.constantFrameDurationUs = constantFrameDurationUs;
        putInt("constantFrameDurationUs", constantFrameDurationUs);
    }

    /**
     * @return the maxPacketDurationUs
     */
    public int getMaxPacketDurationUs() {
        return maxPacketDurationUs;
    }

    /**
     * @param maxPacketDurationUs the maxPacketDurationUs to set
     */
    public void setMaxPacketDurationUs(int maxPacketDurationUs) {
        if (maxPacketDurationUs < 0) {
            maxPacketDurationUs = 0;
        }
        this.maxPacketDurationUs = maxPacketDurationUs;
        putInt("maxPacketDurationUs", maxPacketDurationUs);
    }

    /**
     * @return the minPacketDurationUs
     */
    public int getMinPacketDurationUs() {
        return minPacketDurationUs;
    }

    /**
     * @param minPacketDurationUs the minPacketDurationUs to set
     */
    public void setMinPacketDurationUs(int minPacketDurationUs) {
        if (minPacketDurationUs < 0) {
            minPacketDurationUs = 0;
        }
        this.minPacketDurationUs = minPacketDurationUs;
        putInt("minPacketDurationUs", minPacketDurationUs);
    }

    /**
     * @return the areaEventNumberSubsampling
     */
    public int getAreaEventNumberSubsampling() {
        return areaEventNumberSubsampling;
    }

    /**
     * @param areaEventNumberSubsampling the areaEventNumberSubsampling to set
     */
    synchronized public void setAreaEventNumberSubsampling(int areaEventNumberSubsampling) {
        int old = this.areaEventNumberSubsampling;
        this.areaEventNumberSubsampling = areaEventNumberSubsampling;
        if (old != this.areaEventNumberSubsampling) {
            clearAreaCounts();
        }
        putInt("areaEventNumberSubsampling", areaEventNumberSubsampling);
        showAreasForAreaCountsTemporarily();
    }

    /**
     * @return the isEnablePacketDurationLogging
     */
    public boolean isEnablePacketDurationLogging() {
        return enablePacketDurationLogging;
    }

    /**
     * @param enableImuTimesliceLogging the enableImuTimesliceLogging to set
     */
    public void setEnablePacketDurationLogging(boolean enableImuTimesliceLogging) {
        this.enablePacketDurationLogging = enableImuTimesliceLogging;
        if (enableImuTimesliceLogging) {
            if (actualPacketDurationLogger == null) {
                actualPacketDurationLogger = new TobiLogger("FlexTimePlayer-ActualPacketDuration", "slice duration and event count logging");
                actualPacketDurationLogger.setColumnHeaderLine("systemTimeMs\tpacketNumber\tsliceDurationUs\tsliceEventCount");
                actualPacketDurationLogger.setSeparator("\t");
            }
        }
        actualPacketDurationLogger.setEnabled(enableImuTimesliceLogging);
    }

    /**
     * @return the automaticallyControlInputRate
     */
    public boolean isAutomaticallyControlInputRate() {
        return automaticallyControlInputRate;
    }

    /**
     * @param automaticallyControlInputRate the automaticallyControlInputRate to
     * set
     */
    public void setAutomaticallyControlInputRate(boolean automaticallyControlInputRate) {
        this.automaticallyControlInputRate = automaticallyControlInputRate;
        putBoolean("automaticallySetInputRate", automaticallyControlInputRate);
    }

    /**
     * @return the showStatistics
     */
    public boolean isShowStatistics() {
        return showStatistics;
    }

    /**
     * @param showStatistics the showStatistics to set
     */
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
        putBoolean("showStatistics", showStatistics);
    }
}
