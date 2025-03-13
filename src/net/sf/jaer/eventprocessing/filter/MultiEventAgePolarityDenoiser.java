/* MultiEventAgePolarityDenoiser.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

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

/**
 * A BA noise filter that uses multiple recent events in NNb to score past
 * events by their relative age and only counts events of same polarity
 *
 * @author Tobi Delbruck 2025
 */
@Description("Denoises uncorrelated noise events that scores multiple events from each pixel's past events by"
        + " * their relative age and polarity")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MultiEventAgePolarityDenoiser extends SpatioTemporalCorrelationFilter {

    @Preferred
    private float correlationThreshold = getFloat("correlationThreshold", 5);
    @Preferred
    private int numPastEvents = getInt("numPastEvents", 3);

    int[][][] timestampImages; // timestamp images, last coordinate is backwards in time
    byte[][][] polImages; // -1 is OFF +1 is ON, last event polarities according to getPolaritySignum
    byte[][] eventPointers;

    private int tauUs = 0; // buffers the correlationTimeS  in us for efficiency

    public MultiEventAgePolarityDenoiser(AEChip chip) {
        super(chip);
        hideProperty("numMustBeCorrelated");
        hideProperty("antiCasualEnabled");
        hideProperty("filterAlternativePolarityShotNoiseEnabled");
        setPropertyTooltip(TT_FILT_CONTROL, "correlationThreshold", "threshold value of aged correlation score to classify as signal event");
        setPropertyTooltip(TT_FILT_CONTROL, "numPastEvents", "how many timestamp/polarity planes/imagers to use");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
    }

    /**
     * Returns relative age
     *
     * @param dt the negative delta time in us
     * @return the age, 1 for simultaneous, 0 for times =>tauUs
     */
    private float age(final int dt) {
        if (dt < tauUs && dt > 0) {
            final float age = 1 - ((float) (dt)) / tauUs;  // if dt is 0, then linearDt is 1, if dt=-tauUs, then linearDt=0
            return age;
        } else {
            return 0;
        }
    }

    @Override
    public String infoString() {
        String s = super.infoString() + " corrThr="
                + eng.format(correlationThreshold);
        return s;
    }

    @Override
    protected void allocateMaps(AEChip chip) {
        timestampImages = new int[chip.getSizeX()][chip.getSizeY()][numPastEvents]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
        polImages = new byte[chip.getSizeX()][chip.getSizeY()][numPastEvents]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
        eventPointers = new byte[chip.getSizeX()][chip.getSizeY()]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
    }

    @Override
    public synchronized void resetFilter() {
        getNoiseFilterControl().resetFilter();
        if (timestampImages == null) {
            log.warning("tried to clear lastTimesMap but it is null");
            return;
        }
        for (int[][] arrayRow : timestampImages) {
            for (int[] arrayCol : arrayRow) {
                Arrays.fill(arrayCol, DEFAULT_TIMESTAMP);
            }
        }
        for (byte[] row : eventPointers) {
            Arrays.fill(row, (byte) 0);
        }
        resetShotNoiseTestStats();
    }

    private int getTimestamp(int x, int y, int num) {
        byte ptr = getPtr(x, y, num);
        return timestampImages[x][y][ptr];
    }

    private byte getPolarity(int x, int y, int num) {
        byte ptr = getPtr(x, y, num);
        return polImages[x][y][ptr];
    }

    @Override
    protected void storeTimestampPolarity(final int x, final int y, BasicEvent e) {
        byte ptr = incPtr(x, y);
        timestampImages[x][y][ptr] = e.timestamp;
        if (e instanceof PolarityEvent polarityEvent) {
            polImages[x][y][ptr] = (byte) polarityEvent.getPolaritySignum();
        }
    }

    private byte incPtr(final int x, final int y) {
        byte ptr = getPtr(x, y);
        ptr++;
        if (ptr >= numPastEvents) {
            ptr = 0;
        }
        eventPointers[x][y] = ptr;
        return ptr;
    }

    private byte getPtr(final int x, final int y) {
        byte ptr = eventPointers[x][y];
        return ptr;
    }

    private byte getPtr(final int x, final int y, int num) {
        byte ptr = eventPointers[x][y];
        ptr += num;
        if (ptr >= numPastEvents) {
            ptr -= numPastEvents;
        }
        return ptr;
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
                if (getTimestamp(x, y, 0) == DEFAULT_TIMESTAMP) {
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
                nnbRange.compute(x, y, ssx, ssy);
                outerloop:
                for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                    final int[][] col = timestampImages[xx];
                    final byte[][] polCol = polImages[xx];
                    final byte[] ptrCol = eventPointers[xx];
                    for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                        if (fhp && xx == x && yy == y) {
                            continue; // like BAF, don't correlate with ourself
                        }
                        final int[] times = col[yy];
                        final byte[] pols = polCol[yy];
                        byte lastPtr = ptrCol[0];
                        for (int i = lastPtr; i < lastPtr + numPastEvents; i++) {
                            final byte p1 = (byte) (i % numPastEvents);
                            final int lastT = times[p1];
                            final int deltaT = (ts - lastT); // note deltaT will be very negative for DEFAULT_TIMESTAMP because of overflow

                            if (deltaT < tauUs && lastT != DEFAULT_TIMESTAMP) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                                if (!polaritiesMustMatch || !hasPolarites) {
                                    score += age(deltaT);
                                } else {
                                    PolarityEvent pe = (PolarityEvent) e;
                                    if (pe.getPolaritySignum() == pols[p1]) {
                                        score += age(deltaT);
                                    }
                                }
                            }
                        }
                    }
                }
                if (score < correlationThreshold) {
                    filterOut(e);
                } else {
                    filterIn(e);
                }
                storeTimestampPolarity(x, y, e);
            } // event packet loop
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    /**
     * Fills timestampImage with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        Random random = new Random();
        double noiseIntervalS = 1 / noiseRateHz;
        for (final int[][] arrayRow : timestampImages) {
            for (final int[] col : arrayRow) {
                int lastTUs = 0;  // increament so each past event is sampled indpendently from previous one, TODO check if really correct for Poisson noise
                for (int i = 0; i < numPastEvents; i++) {
                    final double p = random.nextDouble();
                    final double t = -noiseIntervalS * Math.log(1 - p); // TODO evnets are not sorted correctly
                    int tUs = (int) (1000000 * t);
                    final int tmpUs = tUs;
                    tUs = tUs + lastTUs;
                    col[i] = lastTimestampUs - tUs;
                    lastTUs = tmpUs;
                }
            }
        }
        for (final byte[][] arrayRow : polImages) {
            for (final byte[] col : arrayRow) {
                for (int i = 0; i < col.length; i++) {
                    final boolean b = random.nextBoolean();
                    col[i] = b ? (byte) 1 : (byte) -1;
                }
            }
        }
    }

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
        float old = this.correlationThreshold;
        if (correlationThreshold > getNumNeighbors()) {
            correlationThreshold = getNumNeighbors();
        }
        this.correlationThreshold = correlationThreshold;
        putFloat("correlationThreshold", correlationThreshold);
        getSupport().firePropertyChange("correlationThreshold", old, this.correlationThreshold);
    }

    public float getMaxCorrelationThreshold() {
        return getNumNeighbors();
    }

    public float getMinCorrelationThreshold() {
        return 0;
    }

    /**
     * @return the numPastEvents
     */
    public int getNumPastEvents() {
        return numPastEvents;
    }

    /**
     * @param numPastEvents the numPastEvents to set
     */
    synchronized public void setNumPastEvents(int numPastEvents) {
        int old = this.numPastEvents;
        if (numPastEvents < 1) {
            numPastEvents = 1;
        } else if (numPastEvents > 5) {
            numPastEvents = 5;
        }
        this.numPastEvents = numPastEvents;
        getSupport().firePropertyChange("numPastEvents", old, numPastEvents);
        putInt("numPastEvents", numPastEvents);
        allocateMaps(chip);
    }
}
