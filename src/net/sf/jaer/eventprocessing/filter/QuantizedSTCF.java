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
 * A quantized version of STCF
 */
@Description("Quantized SpatioTemporalCorrelation denoising noise filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class QuantizedSTCF extends SpatioTemporalCorrelationFilter {

    @Preferred
    private int timestampBits = getInt("timestampBits", 14);
    @Preferred
    private int timestampRightShiftDividerBits = getInt("timestampRightShiftDividerBits", 10);
    String QUAN_GROUP = "Quantization";

    // computed vals in computeBitMasksAndTooltips
    int timestampResUs ;
    int timestampBitmask;
    int maxTimestamp;

    public QuantizedSTCF(AEChip chip) {
        super(chip);
        setPropertyTooltip(QUAN_GROUP, "timestampBits", "Number of bits for timestamp");
        setPropertyTooltip(QUAN_GROUP, "timestampRighShiftDividerBits", "Number of bits timestamp is rightshifted to divide it. A right shift of 10 bits makes the timestamps approx 1ms");
        computeBitMasksAndTooltips();
    }

    private void computeBitMasksAndTooltips() {
        timestampResUs = 1 << timestampRightShiftDividerBits;
        timestampBitmask = ((1 << timestampBits) - 1);
        maxTimestamp = timestampResUs * (1 << timestampBits);

        String quantizationInfo = String.format("Timestamp resolution is %,d us, max value is %,d us", timestampResUs, maxTimestamp);
        log.info(quantizationInfo);
        setPropertyTooltip(QUAN_GROUP, "timestampBits", "<html>Number of bits for timestamp;<br> " + quantizationInfo);
        setPropertyTooltip(QUAN_GROUP, "timestampRightShiftDividerBits", "<html>Number of bits timestamp is rightshifted to divide it;<br>" + quantizationInfo);
    }

    /** Applies quantization to timestamp image fill 32-bit timestamp
     * 
     * @param ts
     * @return the quantized timestamp, right-shifted and masked
     */
    private int quantizeTimestamp(int ts){
        return ts>>>(timestampRightShiftDividerBits)&timestampBitmask;
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
        // code from AbstractNoiseFilter filterPacket()
        resetCountsAndNegativeEvents();
        // end code from super.super
        if (timestampImage == null) {
            allocateMaps(chip);
        }
        final int dt = quantizeTimestamp(((int) Math.round(getCorrelationTimeS() * 1e6f)));
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        final boolean record = recordFilteredOutEvents; // to speed up loop, maybe
        final boolean fhp = filterHotPixels;
        final NnbRange nnbRange = new NnbRange();

        final boolean hasPolarites = in.getEventPrototype() instanceof PolarityEvent;

        if (record) { // this branch is for NoiseTesterFilter; 2nd branch saves a tiny bit if not instrumenting denoising in NoiseTesterFilter
            for (BasicEvent e : in) {
                if (e == null) {
                    continue;
                }
                // comment out to support special "noise" events that are labeled special for denoising study
//                if (e.isSpecial()) {
//                    continue;
//                }
                totalEventCount++;
                final int ts = quantizeTimestamp(e.timestamp);
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
                int ncorrelated = 0;
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
                        
                        int lastTsFull=col[yy];
                        boolean isDefaultTs=lastTsFull==DEFAULT_TIMESTAMP;
                        final int lastT = quantizeTimestamp(lastTsFull);
                        final int deltaT = (ts - lastT);

                        boolean occupied = false;
                        if (deltaT < dt && deltaT>0 && !isDefaultTs) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                            if (!polaritiesMustMatch || !hasPolarites) {
                                ncorrelated++;
                                occupied = true;
                            } else {
                                PolarityEvent pe = (PolarityEvent) e;
                                if (pe.getPolaritySignum() == polCol[yy]) {
                                    ncorrelated++;
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
                if (ncorrelated < numMustBeCorrelated) {
                    filterOut(e);
                } else {
                    filterIn(e);
                }
                storeTimestampPolarity(x, y, e);
            } // event packet loop
        } else { // standalone filtering, not keep stats, not in NoiseTesterFilter
            for (BasicEvent e : in) {
                if (e == null) {
                    continue;
                }
//                if (e.isSpecial()) {
//                    continue;
//                }
                totalEventCount++;
                final int ts = quantizeTimestamp(e.timestamp);
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
                int ncorrelated = 0;
                nnbRange.compute(x, y, ssx, ssy);
                outerloop:
                for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                    final int[] col = timestampImage[xx];
                    final byte[] polCol = polImage[xx];
                    for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                        if (fhp && xx == x && yy == y) {
                            continue; // like BAF, don't correlate with ourself
                        }
                        int lastTsFull=col[yy];
                        boolean isDefaultTs=lastTsFull==DEFAULT_TIMESTAMP;
                        final int lastT = quantizeTimestamp(lastTsFull);
                        final int deltaT = (ts - lastT);


                        if (deltaT < dt && deltaT>0 && !isDefaultTs) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                            if (!polaritiesMustMatch || !hasPolarites) {
                                ncorrelated++;
                            } else {
                                PolarityEvent pe = (PolarityEvent) e;
                                if (pe.getPolaritySignum() == polCol[yy]) {
                                    ncorrelated++;
                                }
                            }
                            if (ncorrelated >= numMustBeCorrelated) {
                                break outerloop; // csn stop checking now
                            }
                        }

                    }
                }
                if (ncorrelated < numMustBeCorrelated) {
                    filterOut(e);
                } else {
                    filterIn(e);
                }
                storeTimestampPolarity(x, y, e);
            }
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    /**
     * @return the timestampBits
     */
    public int getTimestampBits() {
        return timestampBits;
    }

    /**
     * @param timestampBits the timestampBits to set
     */
    public void setTimestampBits(int timestampBits) {
        if (timestampBits > 32) {
            timestampBits = 32;
        } else if (timestampBits < 3) {
            timestampBits = 3;
        }
        this.timestampBits = timestampBits;
        putInt("timestampBits", timestampBits);
        computeBitMasksAndTooltips();
    }

    /**
     * @return the timestampRightShiftDividerBits
     */
    public int getTimestampRightShiftDividerBits() {
        return timestampRightShiftDividerBits;
    }

    /**
     * @param timestampRightShiftDividerBits the timestampRightShiftDividerBits to
 set
     */
    public void setTimestampRightShiftDividerBits(int timestampRightShiftDividerBits) {
        if (timestampRightShiftDividerBits > 20) {
            timestampRightShiftDividerBits = 20;
        } else if (timestampRightShiftDividerBits < 0) {
            timestampRightShiftDividerBits = 0;
        }
        this.timestampRightShiftDividerBits = timestampRightShiftDividerBits;
        putInt("timestampRightShiftDividerBits", timestampRightShiftDividerBits);
        computeBitMasksAndTooltips();
    }

}
