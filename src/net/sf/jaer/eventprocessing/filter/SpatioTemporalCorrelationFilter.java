/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeEvent;
import static java.lang.Math.random;
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
 * A BA noise filter derived from BackgroundActivityFilter that only passes
 * events that are supported by at least some fraction of neighbors in the past
 * {@link #setDt dt} in the immediate spatial neighborhood, defined by a
 * subsampling bit shift.
 *
 * @author Tobi Delbruck and Shasha Guo, with discussion with Moritz Milde, Dave
 * Karpul, Elisabetta Chicca, Chiara Bartolozzi Telluride 2017
 */
@Description("<html>Denoises uncorrelated noise events by multiple events in spatiotemporal neighborhood."
        + "<p>Published in Guo & Delbruck, T-PAMI 2022 <a href=\"http://dx.doi.org/10.1109/TPAMI.2022.3152999\">10.1109/TPAMI.2022.3152999</a>")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class SpatioTemporalCorrelationFilter extends AbstractNoiseFilter {

    @Preferred
    protected int numMustBeCorrelated = getInt("numMustBeCorrelated", 2);
    @Preferred
    protected boolean polaritiesMustMatch = getBoolean("polaritiesMustMatch", true);
    private boolean filterAlternativePolarityShotNoiseEnabled = getBoolean("filterAlternativePolarityShotNoiseEnabled", false);
//    protected boolean favorLines = getBoolean("favorLines", false);
    protected float shotNoiseCorrelationTimeS = getFloat("shotNoiseCorrelationTimeS", 1e-3f);
    protected int numShotNoiseTests = 0, numAlternatingPolarityShotNoiseEventsFilteredOut = 0;

    protected int sxm1; // size of chip minus 1
    protected int sym1;
    protected int ssx; // size of subsampled timestamp map
    protected int ssy;

    int[][] timestampImage; // timestamp image
    byte[][] polImage; // -1 is OFF +1 is ON, last event polarities according to getPolaritySignum

    public SpatioTemporalCorrelationFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip(TT_FILT_CONTROL, "numMustBeCorrelated", "At least this number of 9 (3x3) neighbors (including our own event location) must have had event within past dt");
        setPropertyTooltip(TT_FILT_CONTROL, "polaritiesMustMatch", "the correlating events must have the same polarity");
//        setPropertyTooltip(TT_FILT_CONTROL, "favorLines", "add condition that events in 8-NNb must lie along line crossing pixel to pass");
        setPropertyTooltip(TT_FILT_CONTROL, "filterAlternativePolarityShotNoiseEnabled", "filter out events where ON follows OFF or vice versa within the time shotNoiseCorrelationTimeS, which is true for pure thermal noise with short refractory period. This test is applied after the correlation test.");
        setPropertyTooltip(TT_FILT_CONTROL, "shotNoiseCorrelationTimeS", "The correlation time in seconds for shot noise test; see filterAlternativePolarityShotNoiseEnabled");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
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
        super.filterPacket(in);
        final int mapSx = ssxSize();
        final int mapSy = ssySize();
        if (timestampImage == null || timestampImage.length != mapSx
                || timestampImage[0].length != mapSy) {
            allocateMaps(chip);
        }
        final int dt = (int) Math.round(getCorrelationTimeS() * 1e6f);
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        final boolean fhp = filterHotPixels;
        final NnbRange nnbRange = new NnbRange();
        final boolean checkPolarity = polaritiesMustMatch && (in.getEventPrototype() instanceof PolarityEvent);
        final int kNeed = numMustBeCorrelated;
        final boolean shotNoiseEnabled = filterAlternativePolarityShotNoiseEnabled;

        for (BasicEvent e : in) {
            if (e == null) {
                continue;
            }
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
                } else {
                    filterOut(e);
                }
                continue;
            }

            // finally the real denoising starts here
            int ncorrelated = 0;
            nnbRange.compute(x, y, ssx, ssy);
            // Hoist polarity out of the NNb loop (was casting PolarityEvent per neighbor)
            final byte eventPol = checkPolarity ? (byte) ((PolarityEvent) e).getPolaritySignum() : 0;
            outerloop:
            for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                final int[] col = timestampImage[xx];
                final byte[] polCol = checkPolarity ? polImage[xx] : null;
                for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                    if (fhp && xx == x && yy == y) {
                        continue; // like BAF, don't correlate with ourself
                    }
                    final int lastT = col[yy];
                    // note: (ts - lastT) is very negative for DEFAULT_TIMESTAMP because of overflow
                    if (lastT != DEFAULT_TIMESTAMP && (ts - lastT) < dt) {
                        if (!checkPolarity || eventPol == polCol[yy]) {
                            if (++ncorrelated >= kNeed) {
                                break outerloop;
                            }
                        }
                    }
                }
            }
            if (ncorrelated < kNeed) {
                filterOut(e);
            } else if (shotNoiseEnabled && testIsShotNoiseOppositePolarity(x, y, e)) {
                filterOut(e);
            } else {
                filterIn(e);
            }

            storeTimestampPolarity(x, y, e);
        }
        // debug SignalNoisePacket
//        if (in instanceof SignalNoisePacket signalNoisePacket) {
//            signalNoisePacket.countClassifications(false);
//        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    protected void storeTimestampPolarity(final int x, final int y, BasicEvent e) {
        timestampImage[x][y] = e.timestamp;
        if (e instanceof PolarityEvent) {
            polImage[x][y] = (byte) ((PolarityEvent) e).getPolaritySignum();
        }
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
//        log.info("resetting SpatioTemporalCorrelationFilter");
        if (timestampImage == null) {
//            log.warning("tried to clear lastTimesMap but it is null");
            return;
        }
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
        resetShotNoiseTestStats();
    }

    protected void resetShotNoiseTestStats() {
        numShotNoiseTests = 0;
        numAlternatingPolarityShotNoiseEventsFilteredOut = 0;
    }

    @Override
    public void initFilter() {
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        allocateMaps(chip);
        resetFilter();
    }

    /** Subsampled map width (number of columns), matching indices 0..ssx inclusive. */
    private int ssxSize() {
        return sxm1 < 0 ? 1 : (sxm1 >> subsampleBy) + 1;
    }

    /** Subsampled map height (number of rows), matching indices 0..ssy inclusive. */
    private int ssySize() {
        return sym1 < 0 ? 1 : (sym1 >> subsampleBy) + 1;
    }

    protected void allocateMaps(AEChip chip) {
        if (chip == null || chip.getNumCells() <= 0) {
            return;
        }
        // Size maps to subsampled address space used in filterPacket (x>>subsampleBy).
        // Previously allocated full chip size while checking length against subsampled
        // width, which reallocated on every init when subsampleBy>0 and hurt cache locality.
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;
        final int mapSx = ssxSize();
        final int mapSy = ssySize();
        if (timestampImage == null || timestampImage.length != mapSx
                || timestampImage[0].length != mapSy) {
            timestampImage = new int[mapSx][mapSy];
            polImage = new byte[mapSx][mapSy];
        }
    }

    /**
     * Fills timestampImage and polImage with waiting times drawn from Poisson
     * process with rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        fill2dTimestampAndPolarityImagesWithNoiseEvents(noiseRateHz, lastTimestampUs, timestampImage, polImage);
    }

    // </editor-fold>
    /**
     * @return the letFirstEventThrough
     */
    public boolean isLetFirstEventThrough() {
        return letFirstEventThrough;
    }

    /**
     * @param letFirstEventThrough the letFirstEventThrough to set
     */
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        this.letFirstEventThrough = letFirstEventThrough;
        putBoolean("letFirstEventThrough", letFirstEventThrough);
    }

    /**
     * @return the numMustBeCorrelated
     */
    public int getNumMustBeCorrelated() {
        return numMustBeCorrelated;
    }

    /**
     * @param numMustBeCorrelated the numMustBeCorrelated to set
     */
    public void setNumMustBeCorrelated(int numMustBeCorrelated) {
        int old = this.numMustBeCorrelated;
        if (numMustBeCorrelated < 1) {
            numMustBeCorrelated = 1;
        } else if (numMustBeCorrelated > getNumNeighbors()) {
            numMustBeCorrelated = getNumNeighbors();
        }
        putInt("numMustBeCorrelated", numMustBeCorrelated);
        this.numMustBeCorrelated = numMustBeCorrelated;
        getSupport().firePropertyChange("numMustBeCorrelated", old, this.numMustBeCorrelated);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        }
    }

    private String USAGE = "SpatioTemporalFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx numMustBeCorrelated xx\n";

    // remote control for experiments e.g. with python / UDP remote control 
    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");

        if (tok.length < 3) {
            return USAGE;
        }
        try {

            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("dt")) {
                        setCorrelationTimeS(1e-6f * Integer.parseInt(tok[i + 1]));
                    } else if (tok[i].equals("num")) {
                        setNumMustBeCorrelated(Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set SpatioTemporalFilter parameters dt " + String.valueOf(getCorrelationTimeS()) + " and numMustBeCorrelated " + String.valueOf(numMustBeCorrelated);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol" + e.toString() + "\n";
        }
    }

    @Override
    public String infoString() {
        if (filterAlternativePolarityShotNoiseEnabled) {
            final float shotFilteredOut = 100 * (float) numAlternatingPolarityShotNoiseEventsFilteredOut / numShotNoiseTests;
            String s = super.infoString() + String.format(" k=%d onOffShot=%s onOffFilt=%6.1f", numMustBeCorrelated, filterAlternativePolarityShotNoiseEnabled, shotFilteredOut);
            return s;
        } else {
            return super.infoString() + String.format(" k=%d usePol=%s", numMustBeCorrelated, isPolaritiesMustMatch());
        }
    }

    /**
     * Tests if event is perhaps a shot noise event that is very recently after
     * a previous event from this pixel and is the opposite polarity.
     *
     * @param x x address of event
     * @param y y address of event
     * @param e event
     *
     * @return true if noise event, false if signal
     */
    protected boolean testIsShotNoiseOppositePolarity(int x, int y, BasicEvent e) {
        // Caller normally gates on filterAlternativePolarityShotNoiseEnabled; keep check for subclasses
        if (!filterAlternativePolarityShotNoiseEnabled) {
            return false;
        }
        if (!(e instanceof PolarityEvent)) {
            return false;
        }
        numShotNoiseTests++;
        PolarityEvent p = (PolarityEvent) e;
        if (p.getPolaritySignum() == polImage[x][y]) {
            return false; // if same polarity, don't filter out
        }
        int prevT = timestampImage[x][y];
        if (prevT == DEFAULT_TIMESTAMP) {
            return false; // if there is no previous event, treat as signal event
        }
        // Compare in microseconds to avoid float multiply per candidate event
        if ((e.timestamp - prevT) > (int) (shotNoiseCorrelationTimeS * 1e6f)) {
            return false; // if the previous event was too far in past, treat as signal event
        }
        numAlternatingPolarityShotNoiseEventsFilteredOut++;
        return true; // opposite polarity and follows closely after previous event, filter out
    }

    /**
     * @return the filterAlternativePolarityShotNoiseEnabled
     */
    public boolean isFilterAlternativePolarityShotNoiseEnabled() {
        return filterAlternativePolarityShotNoiseEnabled;
    }

    /**
     * @param filterAlternativePolarityShotNoiseEnabled the
     * filterAlternativePolarityShotNoiseEnabled to set
     */
    public void setFilterAlternativePolarityShotNoiseEnabled(boolean filterAlternativePolarityShotNoiseEnabled) {
        this.filterAlternativePolarityShotNoiseEnabled = filterAlternativePolarityShotNoiseEnabled;
        putBoolean("filterAlternativePolarityShotNoiseEnabled", filterAlternativePolarityShotNoiseEnabled);
        resetShotNoiseTestStats();
    }

    /**
     * @return the shotNoiseCorrelationTimeS
     */
    public float getShotNoiseCorrelationTimeS() {
        return shotNoiseCorrelationTimeS;
    }

    /**
     * @param shotNoiseCorrelationTimeS the shotNoiseCorrelationTimeS to set
     */
    public void setShotNoiseCorrelationTimeS(float shotNoiseCorrelationTimeS) {
        this.shotNoiseCorrelationTimeS = shotNoiseCorrelationTimeS;
        putFloat("shotNoiseCorrelationTimeS", shotNoiseCorrelationTimeS);
        resetShotNoiseTestStats();
    }

    /**
     * @return the polaritiesMustMatch
     */
    public boolean isPolaritiesMustMatch() {
        return polaritiesMustMatch;
    }

    /**
     * @param polaritiesMustMatch the polaritiesMustMatch to set
     */
    public void setPolaritiesMustMatch(boolean polaritiesMustMatch) {
        this.polaritiesMustMatch = polaritiesMustMatch;
    }

}
