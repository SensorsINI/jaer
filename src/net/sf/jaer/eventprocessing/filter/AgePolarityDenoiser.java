/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * A BA noise filter derived from SpatioTemporalCorrelationFilter that scores
 * past events by their relative age and only counts events of same polarity
 *
 * @author Tobi Delbruck 2025
 */
@Description("Denoises uncorrelated noise events that scores past events by"
        + " * their relative age and polarity")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class AgePolarityDenoiser extends SpatioTemporalCorrelationFilter {

    @Preferred
    private float correlationThreshold = getFloat("correlationThreshold", 5);

    private int tauUs = 0; // buffers the correlationTimeS  in us for efficiency

    public AgePolarityDenoiser(AEChip chip) {
        super(chip);
        hideProperty("numMustBeCorrelated");
        hideProperty("antiCasualEnabled");
        setPropertyTooltip(TT_FILT_CONTROL, "correlationThreshold", "threshold value of aged correlation score to classify as signal event");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
    }

    /**
     * Returns relative age
     *
     * @param dt the negative delta time in us
     * @return the age, 1 for simultaneous, 0 for times =>tauUs
     */
    private float age(final int dt) {
        if (dt < tauUs && dt>0) {
            final float age = 1 - ((float) (dt)) / tauUs;  // if dt is 0, then linearDt is 1, if dt=-tauUs, then linearDt=0
            return age;
        } else {
            return 0;
        }
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        resetCountsAndNegativeEvents();
        if (timestampImage == null) {
            allocateMaps(chip);
        }
        tauUs = (int) Math.round(getCorrelationTimeS() * 1e6f);
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        final boolean record = recordFilteredOutEvents; // to speed up loop, maybe
        final boolean fhp = filterHotPixels;
        final NnbRange nnbRange = new NnbRange();

        final boolean hasPolarites = in.getEventPrototype() instanceof PolarityEvent;

        if (record) { // branch here to save a tiny bit if not instrumenting denoising
            for (BasicEvent e : in) {
                if (e == null) {
                    continue;
                }
                // comment out to support special "noise" events that are labeled special for denoising study
//                if (e.isSpecial()) {
//                    continue;
//                }
                totalEventCount++;
                final int ts = e.timestamp;
                final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy); // subsampling address
                if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) { // out of bounds, discard (maybe bad USB or something)
                    filterOut(e);
                    continue;
                }
                if (timestampImage[x][y] == DEFAULT_TIMESTAMP) {
                    storeTimestampPolarity(x, y, e);
                    if (letFirstEventThrough) {
                        filterIn(e);
                        continue;
                    } else {
                        filterOut(e);
                        continue;
                    }
                }

                // finally the real denoising starts here
                float score = 0;
                byte nnb = 0;
                int bit = 0;
                nnbRange.compute(x, y, ssx, ssy);
                outerloop:
                for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                    final int[] col = timestampImage[xx];
                    final byte[] polCol = polImage[xx];
                    for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                        if (fhp && xx == x && yy == y) {
                            continue; // like BAF, don't correlate with ourself
                        }
                        final int lastT = col[yy];
                        final int deltaT = (ts - lastT); // note deltaT will be very negative for DEFAULT_TIMESTAMP because of overflow

                        boolean occupied = false;
                        if (deltaT < tauUs && lastT != DEFAULT_TIMESTAMP) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                            if (!polaritiesMustMatch || !hasPolarites) {
                                score += age(deltaT);
                                occupied = true;
                            } else {
                                PolarityEvent pe = (PolarityEvent) e;
                                if (pe.getPolaritySignum() == polCol[yy]) {
                                    score += age(deltaT);
                                    occupied = true;
                                }
                            }
                        }
                        if (occupied) {
                            // nnb bits are like this
                            // 0 3 5
                            // 1 x 6
                            // 2 4 7
                            nnb |= (0xff & (1 << bit));
                        }
                        bit++;
                    }
                }
                if (score < correlationThreshold) {
                    filterOut(e);
                } else {
                    // correlated, but might be shot noise event with opposite polarity to recent event from this same pixel
                    if (testIsShotNoiseOppositePolarity(x, y, e)) {
                        filterOut(e);
                    } else {
                        filterIn(e);
                    }
                }
                storeTimestampPolarity(x, y, e);
            } // event packet loop
        } else { // not keep stats
            for (BasicEvent e : in) {
                if (e == null) {
                    continue;
                }
//                if (e.isSpecial()) {
//                    continue;
//                }

                totalEventCount++;
                int ts = e.timestamp;
                final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy); // subsampling address
                if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) { // out of bounds, discard (maybe bad USB or something)
                    filterOut(e);
                    continue;
                }
                if (timestampImage[x][y] == DEFAULT_TIMESTAMP) {
                    storeTimestampPolarity(x, y, e);
                    if (letFirstEventThrough) {
                        filterIn(e);
                        continue;
                    } else {
                        filterOut(e);
                        continue;
                    }
                }

                // finally the real denoising starts here
                float score = 0;
                nnbRange.compute(x, y, ssx, ssy);
                outerloop:
                for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                    final int[] col = timestampImage[xx];
                    final byte[] polCol = polImage[xx];
                    for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                        if (fhp && xx == x && yy == y) {
                            continue; // like BAF, don't correlate with ourself
                        }
                        final int lastT = col[yy];
                        final int deltaT = (ts - lastT); // note deltaT will be very negative for DEFAULT_TIMESTAMP because of overflow

                        if (deltaT < tauUs && lastT != DEFAULT_TIMESTAMP) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                            if (!polaritiesMustMatch || !hasPolarites) {
                                score += age(deltaT);
                            } else {
                                PolarityEvent pe = (PolarityEvent) e;
                                if (pe.getPolaritySignum() == polCol[yy]) {
                                    score += age(deltaT);
                                }
                            }
                            if (score >= correlationThreshold) {
                                break outerloop; // csn stop checking now
                            }
                        }

                    }
                }
                if (score < correlationThreshold) {
                    filterOut(e);
                } else {
                    if (testIsShotNoiseOppositePolarity(x, y, e)) {
                        filterOut(e);
                    } else {
                        filterIn(e);
                    }
                }
                storeTimestampPolarity(x, y, e);
            }
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    // </editor-fold>

    /**
     * @return the correlationThreshold
     */
    public float getCorrelationThreshold() {
        return correlationThreshold;
    }

    /**
     * @param correlationThreshold the correlationThreshold to set
     */
    public void setCorrelationThreshold(float correlationThreshold) {
        if (correlationThreshold > getNumNeighbors()) {
            correlationThreshold = getNumNeighbors();
        }
        this.correlationThreshold = correlationThreshold;
        putFloat("correlationThreshold", correlationThreshold);
    }

    public float getMaxCorrelationThreshold() {
        return getNumNeighbors();
    }
    
    public float getMinCorrelationThreshold(){
        return 0;
    }
}
