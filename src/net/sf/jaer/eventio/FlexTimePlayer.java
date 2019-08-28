/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.awt.Color;
import java.util.Iterator;

import com.jogamp.opengl.GLAutoDrawable;

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
        ConstantEventNumber, ConstantFrameDuration
    };
    protected Method method = Method.valueOf(getString("method", Method.ConstantEventNumber.toString()));

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

    public FlexTimePlayer(AEChip chip) {
        super(chip);
        setPropertyTooltip("constantEventNumber", "Number of DVS events per packet");
        setPropertyTooltip("constantFrameDurationUs", "Duration of packet in us");
        setPropertyTooltip("method", "Method used to determine returned packet");
        setPropertyTooltip("maxPacketDurationUs", "Maximum duration of packet in us; set to 0 to disable");
        setPropertyTooltip("minPacketDurationUs", "Minimum duration of packet in us; set to 0 to disable");
        engFmt.setPrecision(2);
        out.allocate(constantEventNumber);
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
            }
        }
        leftOverEvents.clear();
        while (i.hasNext()) {
            BasicEvent e = i.next();
            if ((e.isFilteredOut())) continue;
            BasicEvent eout = outItr.nextOutput();
            eout.copyFrom(e);

            if (!(in instanceof ApsDvsEventPacket) || ((ApsDvsEvent) e).isDVSEvent()) {
                switch (method) {
                    case ConstantEventNumber:
                        eventCounter++;
                        packetDurationUs = eout.getTimestamp() - firstEventTimestamp;
                        if ((eventCounter >= constantEventNumber || (minPacketDurationUs > 0 && packetDurationUs > minPacketDurationUs))
                                || (maxPacketDurationUs > 0 && (packetDurationUs >= maxPacketDurationUs))) {
                            resetPacket = true;
                            packetEventCount = eventCounter;
                            eventCounter = 0;
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
                        break;
                    case ConstantFrameDuration:
                        throw new RuntimeException("ConstantFrameDuration not yet implemented, sorry :-(");
                }
            }
        }
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
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
        if (isFilterEnabled() && (chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            player.setFlexTimeEnabled();
            player.setPacketSizeEvents(constantEventNumber); // ensure that we don't get more DVS events than can be returned in one of our out packets
            log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if ((chip.getAeViewer() != null) && (chip.getAeViewer().getAePlayer() != null)) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            if (yes) {
                player.setFlexTimeEnabled();
                player.setPacketSizeEvents(constantEventNumber); // ensure that we don't get more DVS events than can be returned in one of our out packets
                log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
            } else {
                player.setFixedTimesliceEnabled();
            }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        MultilineAnnotationTextRenderer.setColor(Color.CYAN);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
        MultilineAnnotationTextRenderer.setScale(.2f);
        MultilineAnnotationTextRenderer.renderMultilineString(String.format("%d events, %ss", packetEventCount, engFmt.format(1e-6 * packetDurationUs)));

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

}
