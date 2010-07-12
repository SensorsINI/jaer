/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2010.cardplayer;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.networking.UDPMesssgeSender;

/**
 * Uses RectangularCluterTracker to send estimates of card value to the card player.
 *
 * @author tobi
 */
public class ClusterBasedPipCounter extends EventFilter2D implements FrameAnnotater, Observer {

    public static String getDescription() {
        return "Simple card pip (value) counter for the card player project";
    }
    RectangularClusterTracker tracker;
    FilterChain filterChain;
    CardStatsMessageSender msgSender;
    CardHistogram cardHist = new CardHistogram();

    public ClusterBasedPipCounter(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        tracker = new RectangularClusterTracker(chip);
        filterChain.add(tracker);
        setEnclosedFilterChain(filterChain);
        msgSender = new CardStatsMessageSender();
        tracker.addObserver(this);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            try {
                msgSender.open();
            } catch (IOException ex) {
                log.warning("couldn't open the UDPMesssgeSender to send messages about card stats: " + ex);
            }
        }else{
//            msgSender.close(); // don't close or else receiever may have bound to the port and will not receive messages on setting filter enabled again
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = filterChain.filterPacket(in);

        return out;
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() - 2);
        MultilineAnnotationTextRenderer.renderMultilineString("ClusterBasedPipCounter\n" + cardHist.toString().substring(0, 50));
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o != tracker) {
            return;
        }
        int npips = 0;
        if ((npips = tracker.getNumVisibleClusters()) == 0) {
            cardHist.reset();
            cardHist.incValue(0);
        } else {
            cardHist.incValue(npips);
        }
        try {
            msgSender.sendMessage(cardHist.toString());
        } catch (IOException ex) {
            log.warning("couldn't send CardHistogram: " + ex);
        }
    }
}
