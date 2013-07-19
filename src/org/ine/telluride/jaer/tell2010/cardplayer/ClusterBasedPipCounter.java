/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2010.cardplayer;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

import org.ine.telluride.jaer.tell2010.spinningcardclassifier.CardNamePlayer;

/**
 * Uses RectangularCluterTracker to send estimates of card value to the card player.
 *
 * @author tobi
 */
@Description("Simple card pip (value) counter for the card player project")
public final class ClusterBasedPipCounter extends EventFilter2D implements FrameAnnotater, Observer {

    RectangularClusterTracker pipCounter, cardTracker;
    FilterChain filterChain;
    CardStatsMessageSender msgSender;
    CardHistogram cardHist = new CardHistogram();
    private boolean sayCard = prefs().getBoolean("ClusterBasedPipCounter.sayCard", true);
    private int minSayCardIntervalMs = prefs().getInt("ClusterBasedPipCounter.minSayCardIntervalMs", 400);
    private int maxLikliehoodCardValue = 0;
    private CardNamePlayer[] namePlayers = new CardNamePlayer[13];
    private long lastTimeSaidCar = 0;
    private float pipSize = prefs().getFloat("ClusterBasedPipCounter.pipSize", .08f);
    private float cardSize = prefs().getFloat("ClusterBasedPipCounter.cardSize", .3f);
    private float cardAspectRatio = 1.1f;
    private int lastCardValue=0;

    public ClusterBasedPipCounter(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);

        pipCounter = new RectangularClusterTracker(chip);
        setPipSize(pipSize);
        pipCounter.setAspectRatio(1);

        cardTracker = new RectangularClusterTracker(chip);
        setCardSize(cardSize);
        cardTracker.setAspectRatio(cardAspectRatio);


        filterChain.add(cardTracker);
        filterChain.add(pipCounter);
        setEnclosedFilterChain(filterChain);
        msgSender = new CardStatsMessageSender();
        pipCounter.addObserver(this);   //  we getString called back from pipCounter to upate()
        // during tracking
        for (int i = 0; i < 13; i++) {
            try {
                namePlayers[i] = new CardNamePlayer(i);
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
        setPropertyTooltip("cardSize", "card size as fraction of max chip dimension");
        setPropertyTooltip("pipSize", "pip size as fraction of max chip dimension");
        setPropertyTooltip("sayCard", "enables audio output of card");
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
        out = filterChain.filterPacket(in);  // the RCT calls us back every updateIntervalMs to update()

        return out;
    }

    @Override
    public void resetFilter() {
        pipCounter.resetFilter();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() - 2);
        String s="ClusterBasedPipCounter\n" + cardHist.toString().substring(0, 50);
        if(lastCardValue>1 && lastCardValue<14) s+="\nCard value: "+cardNameFromValue(lastCardValue);
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o != pipCounter) {
            return;
        }

        // determine if the pipCounter has a valid ML estimate of card.
        // if so, say it
        int npips = 0;
        if(cardTracker.getNumVisibleClusters()==1 ){
            RectangularClusterTracker.Cluster cluster=cardTracker.getClusters().get(0);
            if(cluster.isOverlappingBorder()) return;
        }
        if ((npips = pipCounter.getNumVisibleClusters()) == 0) {
            // card has gone past, getString peak value of hist
            if (sayCard && cardHist.getMaxValueBin() != 0 && System.currentTimeMillis() - lastTimeSaidCar > minSayCardIntervalMs) {
                int val = cardHist.getMaxValueBin();
                if (val > 0 && val <= 13 && namePlayers[val] != null) {
                    lastTimeSaidCar = System.currentTimeMillis();
                    lastCardValue=val;
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

    /**
     * @return the pipSize
     */
    public float getPipSize() {
        return pipSize;
    }

    /**
     * @param pipSize the pipSize to set
     */
    public final void setPipSize(float pipSize) {
        this.pipSize = pipSize;
        prefs().putFloat("ClusterBasedPipCounter.pipSize", pipSize);
        pipCounter.setClusterSize(pipSize);
    }

    /**
     * @return the cardSize
     */
    public float getCardSize() {
        return cardSize;
    }

    /**
     * @param cardSize the cardSize to set
     */
    public void setCardSize(float cardSize) {
        this.cardSize = cardSize;
        prefs().putFloat("ClusterBasedPipCounter.cardSize", cardSize);
        cardTracker.setClusterSize(cardSize);
    }

    private String cardNameFromValue(final int value){
        if(value==0) return "";
        if(value==1) return "ace";
        if(value>13) return "";
        if(value<11) return Integer.toString(value);
        switch(value){
            case 11: return "jack";
            case 12: return "queen";
            case 13: return "king";
            default: return "?";
        }
    }
}
