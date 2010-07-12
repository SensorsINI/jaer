/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2010.cardplayer;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import org.ine.telluride.jaer.tell2010.spinningcardclassifier.CardNamePlayer;

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
    private boolean sayCard = prefs().getBoolean("ClusterBasedPipCounter.sayCard", true);
    private int minSayCardIntervalMs = prefs().getInt("ClusterBasedPipCounter.minSayCardIntervalMs", 400);
    private int maxLikliehoodCardValue = 0;
    private CardNamePlayer[] namePlayers = new CardNamePlayer[13];
    private long lastTimeSaidCar = 0;

    public ClusterBasedPipCounter(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        tracker = new RectangularClusterTracker(chip);
        filterChain.add(tracker);
        setEnclosedFilterChain(filterChain);
        msgSender = new CardStatsMessageSender();
        tracker.addObserver(this);
        for (int i = 0; i < 13; i++) {
            try {
                namePlayers[i] = new CardNamePlayer(i);
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
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
        } else {
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
            // card has gone past, say peak value of hist
            if (sayCard && cardHist.getMaxValueBin() != 0 && System.currentTimeMillis() - lastTimeSaidCar > minSayCardIntervalMs) {
                int val = cardHist.getMaxValueBin();
                if (val > 0 && val <= 13 && namePlayers[val] != null) {
                    lastTimeSaidCar = System.currentTimeMillis();
                    namePlayers[val - 1].play(); // 0=ace, 12=king
                }
            }
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

    /**
     * Get the value of sayCard
     *
     * @return the value of sayCard
     */
    public boolean isSayCard() {
        return sayCard;
    }

    /**
     * Set the value of sayCard
     *
     * @param sayCard new value of sayCard
     */
    public void setSayCard(boolean sayCard) {
        this.sayCard = sayCard;
        prefs().putBoolean("ClusterBasedPipCounter.sayCard", sayCard);
    }

    /**
     * @return the minSayCardIntervalMs
     */
    public int getMinSayCardIntervalMs() {
        return minSayCardIntervalMs;
    }

    /**
     * @param minSayCardIntervalMs the minSayCardIntervalMs to set
     */
    public void setMinSayCardIntervalMs(int minSayCardIntervalMs) {
        this.minSayCardIntervalMs = minSayCardIntervalMs;
        prefs().putInt("ClusterBasedPipCounter.minSayCardIntervalMs", minSayCardIntervalMs);
    }
}
