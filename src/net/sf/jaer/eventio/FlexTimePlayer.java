/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import ch.unizh.ini.jaer.projects.minliu.PatchMatchFlow;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import java.awt.Color;
import java.util.Iterator;

import com.jogamp.opengl.GLAutoDrawable;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;

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

    private int constantEventNumber = getInt("constantEventNumber", 10000);
    protected int constantFrameDurationUs = getInt("constantFrameDurationUs", 30000);
    protected int maxPacketDurationUs = getInt("maxPacketDurationUs", 0);
    protected int minPacketDurationUs = getInt("minPacketDurationUs", 0);
    private ApsDvsEventPacket<ApsDvsEvent> out = new ApsDvsEventPacket(ApsDvsEvent.class), leftOverEvents = new ApsDvsEventPacket<ApsDvsEvent>(ApsDvsEvent.class);
    OutputEventIterator<ApsDvsEvent> outItr = out.outputIterator();
    private int eventCounter = 0;
    private boolean resetPacket = true;
    private int firstEventTimestamp = 0, packetDurationUs = 0, packetEventCount = 0; // for actual packet
    private EngineeringFormat engFmt = new EngineeringFormat();
    // counting events into subsampled areas, when count exceeds the threshold in any area, the slices are rotated

    // AreaEventCount stuff
    protected int areaEventNumberSubsampling = getInt("areaEventNumberSubsampling", 5);
    private int[][] areaCounts = null;
    private int numAreas = 1;
    private int sx, sy;
    private volatile boolean showAreaCountAreasTemporarily = false;
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;

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
        engFmt.setPrecision(2);
        out.allocate(constantEventNumber);
        try {
            method = Method.valueOf(getString("method", Method.ConstantEventNumber.toString()));
        } catch (IllegalArgumentException e) {
            method = Method.ConstantEventNumber;
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        Iterator<BasicEvent> i = null, leftOverIterator = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
            leftOverIterator = ((ApsDvsEventPacket) leftOverEvents).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
            leftOverIterator = ((EventPacket) leftOverEvents).inputIterator();
        }
        if (resetPacket) {
            outItr = out.outputIterator();
            resetPacket = false;
        }
        while (leftOverIterator.hasNext()) {
            BasicEvent e = leftOverIterator.next();
            if (!(e.isFilteredOut())) {
                BasicEvent eout = outItr.nextOutput();
                eout.copyFrom(e);
                if ((!(in instanceof ApsDvsEventPacket)) || ((ApsDvsEvent) e).isDVSEvent()) {
                    ++eventCounter;
                }
            }
        }
        // if leftOverEvents is growing, slow down playback, otherwise speed it up
        if ((chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            if (leftOverEvents.getSize() > 1000) {
                player.slowerAction.actionPerformed(null);
            } else {
                player.fasterAction.actionPerformed(null);
            }
        }

        leftOverEvents.clear();
        while (i.hasNext()) {
            BasicEvent e = i.next();
            if ((e.isFilteredOut())) {
                continue;
            }
            BasicEvent eout = outItr.nextOutput();
            eout.copyFrom(e);
            if ((!(in instanceof ApsDvsEventPacket)) || (((ApsDvsEvent) e).isDVSEvent())) {
                ++eventCounter;
                switch (method) {
                    case ConstantEventNumber:
                    case AreaEventCount:
                        boolean frameDone = false;
                        if (method == Method.ConstantEventNumber) {
                            if (eventCounter >= constantEventNumber) {
                                frameDone = true;
                            }
                        } else if (method == Method.AreaEventCount) {
                            if (areaCounts == null) {
                                clearAreaCounts();
                            }
                            int c = ++areaCounts[e.x >> areaEventNumberSubsampling][e.y >> areaEventNumberSubsampling];
                            if (c >= constantEventNumber) {
                                frameDone = true;
                                clearAreaCounts();
                            }
                        }
                        packetDurationUs = eout.getTimestamp() - firstEventTimestamp;
                        if ((frameDone || (minPacketDurationUs > 0 && packetDurationUs > minPacketDurationUs))
                                || (maxPacketDurationUs > 0 && (packetDurationUs >= maxPacketDurationUs))) {
                            resetPacket = true;
                            packetEventCount = eventCounter;
                            eventCounter = 0;

                            // Store duration and event count to file if logging is enabled
                            if (actualPacketDurationLogger != null && actualPacketDurationLogger.isEnabled()) {
                                actualPacketDurationLogger.log(String.format("%d\t%d\t%d", sliceDurationPacketCount++, packetDurationUs, packetEventCount));
                            }
                            
                            OutputEventIterator<ApsDvsEvent> iLeftOver = leftOverEvents.outputIterator();
                            while (i.hasNext()) {
                                BasicEvent eLeftOver = i.next();
                                if (resetPacket) {
                                    firstEventTimestamp = eLeftOver.getTimestamp();
                                }
                                ApsDvsEvent outputLeftOverEvent = iLeftOver.nextOutput();
                                outputLeftOverEvent.copyFrom(eLeftOver);
                            }
                            return out;
                        }
                }
            }
        }
        return null;
    }

    private void clearAreaCounts() {
        if (method != Method.AreaEventCount) {
            return;
        }
        if (areaCounts == null || areaCounts.length != 1 + (sx >> getAreaEventNumberSubsampling())) {
            int nax = 1 + (sx >> getAreaEventNumberSubsampling()), nay = 1 + (sy >> getAreaEventNumberSubsampling());
            numAreas = nax * nay;
            areaCounts = new int[nax][nay];
        } else {
            for (int[] i : areaCounts) {
                Arrays.fill(i, 0);
            }
        }
    }

    @Override
    public void resetFilter() {
        eventCounter = 0;
        resetPacket = true;
        firstEventTimestamp = 0;
        packetDurationUs = 0;
        packetEventCount = 0;
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
        out.allocate(constantEventNumber);
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
//        if ((chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
//            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
//            if (yes) {
//                player.setFlexTimeEnabled();
//                player.setPacketSizeEvents(constantEventNumber); // ensure that we don't get more DVS events than can be returned in one of our out packets
//                log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
//            } else {
//                player.setFixedTimesliceEnabled();
//            }
//        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        MultilineAnnotationTextRenderer.setColor(Color.CYAN);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
        MultilineAnnotationTextRenderer.setScale(.5f);
        MultilineAnnotationTextRenderer.renderMultilineString(String.format("%d events, %10ss", packetEventCount, engFmt.format(1e-6 * packetDurationUs)));

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
                actualPacketDurationLogger.setHeaderLine("systemTimeMs\tpacketNumber\tsliceDurationUs\tsliceEventCount");
                actualPacketDurationLogger.setSeparator("\t");
            }
        }
        actualPacketDurationLogger.setEnabled(enableImuTimesliceLogging);
    }    
}
