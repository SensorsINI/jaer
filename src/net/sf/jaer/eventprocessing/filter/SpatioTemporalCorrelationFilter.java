/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * An filter derived from BackgroundActivityFilter that only passes events that
 * are supported by at least some fraction of neighbors in the past
 * {@link #setDt dt} in the immediate spatial neighborhood, defined by a
 * subsampling bit shift.
 *
 * @author tobi
 */
@Description("Filters out uncorrelated noise events")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SpatioTemporalCorrelationFilter extends EventFilter2D implements Observer, FrameAnnotater {

    private final int MAX_DT = 100000, MIN_DT = 10;
    private final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    protected int dt = getInt("dt", 30000);
    private boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);
    private int numMustBeCorrelated = getInt("numMustBeCorrelated", 5);

    private int totalEventCount = 0;
    private int filteredOutEventCount = 0;
    private int activityBinDimBits = getInt("activityBinDimBits", 4);
    private int[][] activityHistInput, activityHistFiltered;
    private int binDim, nBinsX, nBinsY, nBinsTotal;
    float entropyInput = 0, entropyFiltered = 0;

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getInt("subsampleBy", 0);

    int[][] lastTimesMap;
    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;

    public SpatioTemporalCorrelationFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        setPropertyTooltip("dt", "Events with less than this delta time in us to neighbors pass through");
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip("letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
        setPropertyTooltip("numMustBeCorrelated", "At least this number of 9 (3x3) neighbors (including our own event location) must have had event within past dt");
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
        if (lastTimesMap == null) {
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

            final int x = (e.x >>> subsampleBy), y = (e.y >>> subsampleBy);
            if ((x < 0) || (x >= sx) || (y < 0) || (y >= sy)) {
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
                    continue;
                }
            }
            int ncorrelated = 0;
            for (int xx = x - 1; xx <= x + 1; xx++) {
                for (int yy = y - 1; yy <= y + 1; yy++) {
                    if ((xx < 0) || (xx > sx) || (yy < 0) || (yy > sy)) {
                        continue;
                    }
                    final int lastT = lastTimesMap[xx][yy];
                    final int deltaT = (ts - lastT);
                    if (deltaT < dt && lastT!=DEFAULT_TIMESTAMP) {
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

            // Bounds checking here to avoid throwing expensive exceptions.
            lastTimesMap[x][y] = ts;
        }

        adaptFiltering();

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
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
    public void setSubsampleBy(int subsampleBy) {
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

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, 0, 0);
        final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                String.format("filteredOutPercent=%%%.1f, entropyInput=%.1f, entropyFiltered=%.1f",
                        filteredOutPercent, entropyInput, entropyFiltered));
        gl.glPopMatrix();
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
        this.numMustBeCorrelated = numMustBeCorrelated;
        putInt("numMustBeCorrelated", numMustBeCorrelated);
    }

    private void adaptFiltering() {
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
    }

}
