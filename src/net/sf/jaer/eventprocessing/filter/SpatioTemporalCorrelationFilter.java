/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Observable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An filter derived from BackgroundActivityFilter that only passes events that
 * are supported by at least some fraction of neighbors in the past
 * {@link #setDt dt} in the immediate spatial neighborhood, defined by a
 * subsampling bit shift.
 *
 * @author tobi, with discussion with Moritz Milde, Dave Karpul, Elisabetta
 * Chicca, Chiara Bartolozzi Telluride 2017
 */
@Description("Filters out uncorrelated noise events based on work at Telluride 2017 with discussion with Moritz Milde, Dave Karpul, Elisabetta\n"
        + " * Chicca, and Chiara Bartolozzi ")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class SpatioTemporalCorrelationFilter extends AbstractNoiseFilter {

    private final int MAX_DT = 100000, MIN_DT = 10;
    private final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    protected int dt = getInt("dt", 30000);
    private boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);
    private int numMustBeCorrelated = getInt("numMustBeCorrelated", 5);

    private int activityBinDimBits = getInt("activityBinDimBits", 4);
    private int[][] activityHistInput, activityHistFiltered;
    private int binDim, nBinsX, nBinsY, nBinsTotal;
    private float entropyInput = 0, entropyFiltered = 0;
    private float entropyReduction;
    private boolean adaptiveFilteringEnabled = getBoolean("adaptiveFilteringEnabled", false);
    private float entropyReductionHighLimit = getFloat("entropyReductionHighLimit", .4f);
    private float entropyReductionLowLimit = getFloat("entropyReductionLowLimit", .1f);
    private float dtChangeFraction = getFloat("dtChangeFraction", 0.01f);

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getInt("subsampleBy", 0);

    int[][] lastTimesMap;
    private int ts = 0, lastTimestamp = DEFAULT_TIMESTAMP; // used to reset filter

    public SpatioTemporalCorrelationFilter(AEChip chip) {
        super(chip);
        initFilter();
        String filt = "1. basic params", adap = "2. AdaptiveFiltering", disp = "Display";
        setPropertyTooltip(filt, "dt", "Events with less than this delta time in us to neighbors pass through");
        setPropertyTooltip(filt, "subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip(filt, "letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
        setPropertyTooltip(filt, "numMustBeCorrelated", "At least this number of 9 (3x3) neighbors (including our own event location) must have had event within past dt");
        setPropertyTooltip(adap, "activityBinDimBits", "2^this is the size of rectangular blocks that histogram event activity for measuring entropy (structure) to evaluate effectiveness of filtering");
        setPropertyTooltip(adap, "adaptiveFilteringEnabled", "enables adaptive control of dt to achieve a target entropyReduction between two limits");
        setPropertyTooltip(adap, "entropyReductionLowLimit", "if entropy reduction from filtering is below this limit, decrease dt");
        setPropertyTooltip(adap, "entropyReductionHighLimit", "if entropy reduction from filtering is above this limit, increase dt");
        setPropertyTooltip(adap, "dtChangeFraction", "fraction by which dt is increased/decreased per packet if entropyReduction is too low/high");
        setPropertyTooltip(disp, "showFilteringStatistics", "annotate display with statistics");
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
    synchronized public EventPacket filterPacket(EventPacket in) {
        totalEventCount = 0;
        filteredOutEventCount = 0;
        final int sx = chip.getSizeX() >> subsampleBy;
        final int sy = chip.getSizeY() >> subsampleBy;
        if (lastTimesMap == null || lastTimesMap.length != sx || lastTimesMap[0].length != sy) {
            allocateMaps(chip);
        }
        resetActivityHistograms();

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }
            totalEventCount++;
            int ts = e.timestamp;
            if (ts < lastTimestamp) {
                resetFilter(); // handle rewind TODO check if this breaks with nonmonotonic timestamps
            }
            lastTimestamp = ts;
            final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy);
            if ((x < 0) || (x >= sx) || (y < 0) || (y >= sy)) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
                continue;
            }
            int ax = x >> activityBinDimBits, ay = y >> activityBinDimBits;
            activityHistInput[ax][ay]++;
            if (lastTimesMap[x][y] == DEFAULT_TIMESTAMP) {
                lastTimesMap[x][y] = ts;
                if (letFirstEventThrough) {
                    activityHistFiltered[ax][ay]++;
                    continue;
                } else {
                    e.setFilteredOut(true);
                    filteredOutEventCount++;
                    continue;
                }
            }
            int ncorrelated = 0;
            for (int xx = x - 1; xx <= x + 1; xx++) {
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    if ((xx < 0) || (xx >= sx) || (yy < 0) || (yy >= sy)) {
                        continue;
                    }
                    final int lastT = lastTimesMap[xx][yy];
                    final int deltaT = (ts - lastT);
                    if (deltaT < dt && lastT != DEFAULT_TIMESTAMP) {
                        ncorrelated++;
                    }
                }
            }
            if (ncorrelated < numMustBeCorrelated) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
            } else {
                activityHistFiltered[ax][ay]++;
            }
            lastTimesMap[x][y] = ts;
        }
        if (totalEventCount > 0) { // don't adjust if there were no DVS events (i.e. only APS turned on)
            adaptFiltering();
        }
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showFilteringStatistics) {
            return;
        }
        findUnusedDawingY();
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, statisticsDrawingPosition, 0);
        final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
        String s = null;
        if (adaptiveFilteringEnabled) {
            s = String.format("%s: dt=%.1fms, filteredOutPercent=%%%.1f, entropyReduction=%.1f",
                    getClass().getSimpleName(), dt * 1e-3f, filteredOutPercent, entropyReduction);
        } else {
            s = String.format("%s: filtered out %%%6.1f",
                    getClass().getSimpleName(),
                    filteredOutPercent);
        }
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX() >> subsampleBy][chip.getSizeY() >> subsampleBy];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
        }
        binDim = 1 << activityBinDimBits;
        nBinsX = chip.getSizeX() / binDim;
        nBinsY = chip.getSizeY() / binDim;
        nBinsTotal = nBinsX * nBinsY;
        activityHistInput = new int[nBinsX + 1][nBinsY + 1];
        activityHistFiltered = new int[nBinsX + 1][nBinsY + 1];
        lastTimestamp = DEFAULT_TIMESTAMP;
    }

    public Object getFilterState() {
        return lastTimesMap;
    }

    // <editor-fold defaultstate="collapsed" desc="getter-setter / Min-Max for --Dt--">
    /**
     * gets the background allowed delay in us
     *
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getDt() {
        return this.dt;
    }

    /**
     * sets the background delay in us. If param is larger then getMaxDt() or
     * smaller getMinDt() the boundary value are used instead of param.
     * <p>
     * Fires a PropertyChangeEvent "dt"
     *
     * @see #getDt
     * @param dt delay in us
     */
    public void setDt(final int dt) {
        int setValue = dt;
        if (dt < getMinDt()) {
            setValue = getMinDt();
        }
        if (dt > getMaxDt()) {
            setValue = getMaxDt();
        }

        putInt("dt", setValue);
        getSupport().firePropertyChange("dt", this.dt, setValue);
        this.dt = setValue;
    }

    public int getMinDt() {
        return MIN_DT;
    }

    public int getMaxDt() {
        return MAX_DT;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     */
    synchronized public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
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
        if (numMustBeCorrelated < 1) {
            numMustBeCorrelated = 1;
        } else if (numMustBeCorrelated > 9) {
            numMustBeCorrelated = 9;
        }
        
        getSupport().firePropertyChange("numMustBeCorrelated", this.numMustBeCorrelated, numMustBeCorrelated);
        putInt("numMustBeCorrelated", numMustBeCorrelated);
        this.numMustBeCorrelated = numMustBeCorrelated;
    }

    private void adaptFiltering() {
        if (!adaptiveFilteringEnabled) {
            return;
        }
        // compute entropies of activities in input and filtered activity histograms
        int sumInput = 0;
        for (int[] i : activityHistInput) {
            for (int ii : i) {
                sumInput += ii;
            }
        }
        int sumFiltered = 0;
        for (int[] i : activityHistFiltered) {
            for (int ii : i) {
                sumFiltered += ii;
            }
        }
        entropyInput = 0;
        if (sumInput > 0) {
            for (int[] i : activityHistInput) {
                for (int ii : i) {
                    final float p = (float) ii / sumInput;
                    if (p > 0) {
                        entropyInput += p * (float) Math.log(p);
                    }
                }
            }
        }
        entropyFiltered = 0;
        if (sumFiltered > 0) {
            for (int[] i : activityHistFiltered) {
                for (int ii : i) {
                    final float p = (float) ii / sumFiltered;
                    if (p > 0) {
                        entropyFiltered += p * (float) Math.log(p);
                    }
                }
            }
        }
        entropyFiltered = -entropyFiltered;
        entropyInput = -entropyInput;
        entropyReduction = entropyInput - entropyFiltered;
        int olddt = getDt();
        if (entropyReduction > entropyReductionHighLimit) {
            int newdt = (int) (getDt() * (1 + dtChangeFraction));
            if (newdt == olddt) {
                newdt++;
            }
            setDt(newdt); // increase dt to force less correlation

        } else if (entropyReduction < entropyReductionLowLimit) {
            int newdt = (int) (getDt() * (1 - dtChangeFraction));
            if (newdt == olddt) {
                newdt--;
            }
            setDt(newdt); // decrease dt to force more correlation

        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        }
    }

    private void resetActivityHistograms() {
        for (int[] i : activityHistInput) {
            Arrays.fill(i, 0);
        }
        for (int[] i : activityHistFiltered) {
            Arrays.fill(i, 0);
        }
    }

    /**
     * @return the activityBinDimBits
     */
    public int getActivityBinDimBits() {
        return activityBinDimBits;
    }

    /**
     * @param activityBinDimBits the activityBinDimBits to set
     */
    synchronized public void setActivityBinDimBits(int activityBinDimBits) {
        int old = this.activityBinDimBits;
        this.activityBinDimBits = activityBinDimBits;
        if (old != activityBinDimBits) {
            allocateMaps(chip);
        }
        putInt("activityBinDimBits", activityBinDimBits);
    }

    /**
     * @return the adaptiveFilteringEnabled
     */
    public boolean isAdaptiveFilteringEnabled() {
        return adaptiveFilteringEnabled;
    }

    /**
     * @param adaptiveFilteringEnabled the adaptiveFilteringEnabled to set
     */
    public void setAdaptiveFilteringEnabled(boolean adaptiveFilteringEnabled) {
        this.adaptiveFilteringEnabled = adaptiveFilteringEnabled;
        putBoolean("adaptiveFilteringEnabled", adaptiveFilteringEnabled);
    }

    /**
     * @return the entropyReductionHighLimit
     */
    public float getEntropyReductionHighLimit() {
        return entropyReductionHighLimit;
    }

    /**
     * @param entropyReductionHighLimit the entropyReductionHighLimit to set
     */
    public void setEntropyReductionHighLimit(float entropyReductionHighLimit) {
        if (entropyReductionHighLimit < entropyReductionLowLimit) {
            entropyReductionHighLimit = entropyReductionLowLimit;
        }
        this.entropyReductionHighLimit = entropyReductionHighLimit;
        putFloat("entropyReductionHighLimit", entropyReductionHighLimit);
    }

    /**
     * @return the entropyReductionLowLimit
     */
    public float getEntropyReductionLowLimit() {
        return entropyReductionLowLimit;
    }

    /**
     * @param entropyReductionLowLimit the entropyReductionLowLimit to set
     */
    public void setEntropyReductionLowLimit(float entropyReductionLowLimit) {
        if (entropyReductionLowLimit > entropyReductionHighLimit) {
            entropyReductionLowLimit = entropyReductionHighLimit;
        }
        this.entropyReductionLowLimit = entropyReductionLowLimit;
        putFloat("entropyReductionLowLimit", entropyReductionLowLimit);
    }

    /**
     * @return the dtChangeFraction
     */
    public float getDtChangeFraction() {
        return dtChangeFraction;
    }

    /**
     * @param dtChangeFraction the dtChangeFraction to set
     */
    public void setDtChangeFraction(float dtChangeFraction) {
        this.dtChangeFraction = dtChangeFraction;
        putFloat("dtChangeFraction", dtChangeFraction);
    }

    private String USAGE = "SpatioTemporalFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx numMustBeCorrelated xx\n";

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
                        setDt(Integer.parseInt(tok[i + 1]));
                    }
                    else if (tok[i].equals("numMustBeCorrelated")) {
                        setNumMustBeCorrelated(Integer.parseInt(tok[i + 1]));
                    }                    
                }
                String out = "successfully set SpatioTemporalFilter parameters dt " + String.valueOf(dt) + " and numMustBeCorrelated " + String.valueOf(numMustBeCorrelated);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol" + e.toString() + "\n";
        }
    }

}
