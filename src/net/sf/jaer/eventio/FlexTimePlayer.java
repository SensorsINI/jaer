/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import sun.awt.AWTAccessor;

/**
 * Plays DAVIS recordings with APS frames at constant DVS event number per
 * returned packet
 *
 * @author tobid
 */
@Description("Plays DAVIS recordings with APS frames at constant DVS event number per returned packet")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class FlexTimePlayer extends EventFilter2D {

    private int nEventsPerPacket = getInt("nEventsPerPacket", 10000);
    private ApsDvsEventPacket<ApsDvsEvent> out = new ApsDvsEventPacket(ApsDvsEvent.class), leftOverEvents=new ApsDvsEventPacket<ApsDvsEvent>(ApsDvsEvent.class);
    OutputEventIterator<ApsDvsEvent> outItr = out.outputIterator();
    private int nEventsCollected = 0;
    private boolean resetPacket = true;

    public FlexTimePlayer(AEChip chip) {
        super(chip);
        setPropertyTooltip("nEventsPerPacket", "Number of DVS per packet.");
        out.allocate(nEventsPerPacket);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        ApsDvsEventPacket in2 = (ApsDvsEventPacket) in;
        Iterator<ApsDvsEvent> i = in2.fullIterator();
        if (resetPacket) {
            outItr = out.outputIterator();
            resetPacket=false;
        }
        Iterator<ApsDvsEvent> leftOverIterator=leftOverEvents.fullIterator();
        while(leftOverIterator.hasNext()){
            ApsDvsEvent e=leftOverIterator.next();
            ApsDvsEvent eout = outItr.nextOutput();
            eout.copyFrom(e);
        }
        leftOverEvents.clear();
        while (i.hasNext()) {
            ApsDvsEvent e = i.next();
            ApsDvsEvent eout = outItr.nextOutput();
            eout.copyFrom(e);
            if (e.isDVSEvent()) {
                nEventsCollected++;
                if (nEventsCollected >= nEventsPerPacket) {
                    resetPacket = true;
                    nEventsCollected = 0;
                    OutputEventIterator<ApsDvsEvent> iLeftOver=leftOverEvents.outputIterator();
                    while(i.hasNext()){
                        ApsDvsEvent eLeftOver=i.next();
                        ApsDvsEvent outputLeftOverEvent=iLeftOver.nextOutput();
                        outputLeftOverEvent.copyFrom(eLeftOver);
                    }
                    return out;
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
     * @return the nEventsPerPacket
     */
    public int getnEventsPerPacket() {
        return nEventsPerPacket;
    }

    /**
     * @param nEventsPerPacket the nEventsPerPacket to set
     */
    synchronized public void setnEventsPerPacket(int nEventsPerPacket) {
        this.nEventsPerPacket = nEventsPerPacket;
        putInt("nEventsPerPacket", nEventsPerPacket);
        out.allocate(nEventsPerPacket);
        if (isFilterEnabled() && chip.getAeViewer() != null && chip.getAeViewer().getAePlayer() != null) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            player.setFlexTimeEnabled();
            player.setPacketSizeEvents(nEventsPerPacket); // ensure that we don't get more DVS events than can be returned in one of our out packets
            log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (chip.getAeViewer() != null && chip.getAeViewer().getAePlayer() != null) {
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            if (yes) {
                player.setFlexTimeEnabled();
                player.setPacketSizeEvents(nEventsPerPacket); // ensure that we don't get more DVS events than can be returned in one of our out packets
                log.info("set player to flex time mode and set packet size to match nEventsPerPacket");
           } else {
                player.setFixedTimesliceEnabled();
            }
        }
    }

}
