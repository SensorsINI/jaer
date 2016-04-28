/* AbstractOrientationFilter.java
 *
 * Created on November 2, 2005, 8:24 PM */
package net.sf.jaer.eventprocessing.label;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.DvsOrientationEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.VectorHistogram;

/**
 * Computes simple-type orientation-tuned cells.                           <br>
 * multiOriOutputEnabled - boolean switch: WTA mode {false} only max 1
 * orientation per event or many event {true} any orientation that passes
 * coincidence threshold. Another switch allows contour enhancement by using
 * previous output orientation events to make it easier to make events along the
 * same orientation. Another switch decides whether to use max delay or average
 * delay as the coincidence measure.
 * <p>
 * Orientation type output takes values 0-3;                    <br>
 * 0 is a horizontal edge (0 deg),                              <br>
 * 1 is an edge tilted up and to right (rotated CCW 45 deg),    <br>
 * 2 is a vertical edge (rotated 90 deg),                       <br>
 * 3 is tilted up and to left (rotated 135 deg from horizontal edge).
 * <p>
 * The filter takes either PolarityEvents or BinocularEvents to create
 * DvsOrientationEvent or BinocularEvents.
 *
 * @author tobi/phess
 */
@Description("Abstract superclass for labelers that detect local orientation by spatio-temporal correlation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
abstract public class AbstractOrientationFilter extends EventFilter2D implements Observer, FrameAnnotater {

    public static final int MAX_LENGTH = 6;
    /**
     * the number of cell output types
     */
    public final int NUM_TYPES = 4;

    protected boolean showGlobalEnabled = getBoolean("showGlobalEnabled", false);
    private boolean showLegendEnabled = getBoolean("showLegendEnabled", true);
    protected boolean showVectorsEnabled = getBoolean("showVectorsEnabled", true);
    protected boolean showRawInputEnabled = getBoolean("showRawInputEnabled", false);
    protected boolean multiOriOutputEnabled = getBoolean("multiOriOutputEnabled", false);
    protected boolean jitterVectorLocations = getBoolean("jitterVectorLocations", true);
    protected boolean passAllEvents = getBoolean("passAllEvents", false);
    protected float jitterAmountPixels = getFloat("jitterAmountPixels", .5f);
    protected boolean oriHistoryEnabled = getBoolean("oriHistoryEnabled", false);
    protected float oriHistoryMixingFactor = getFloat("oriHistoryMixingFactor", 0.1f);
    protected float oriHistoryDiffThreshold = getFloat("oriHistoryDiffThreshold", 0.5f);
    protected int subSampleShift = getInt("subSampleShift", 0);
    protected int length = getInt("length", 3);
    protected int width = getInt("width", 0);
    protected Random random = new Random();
 
    /**
     * events must occur within this time along orientation in us to generate an
     * event
     */
    protected int minDtThresholdUs = getInt("minDtThresholdUs", 100000);
    /**
     * We reject delta times that are larger than minDtThresholdUs by this
     * factor, to rule out very old events
     */
    protected int dtRejectMultiplier = getInt("dtRejectMultiplier", 5);
    /**
     * set true to use min of average time to neighbors. Set false to use max
     * time to neighbors (reduces # events)
     */
    protected boolean useAverageDtEnabled = getBoolean("useAverageDtEnabled", true);
    protected int dtRejectThreshold = minDtThresholdUs * dtRejectMultiplier;
    protected int rfSize;
    protected Random r;

    /**
     * Times of most recent input events: [x][y][polarity]
     */
    protected int[][][] lastTimesMap; // x,y,polarity
    /**
     * Scalar map of past orientation values: [x][y]
     */
    protected float[][] oriHistoryMap;  // scalar orientation value x,y
    /**
     * Delta times to neighbors in each direction.
     */
    protected int[][] dts = null; // new int[NUM_TYPES][length*2+1]; // delta times to neighbors in each direction
    /**
     * Max times to neighbors in each dir.
     */
    protected int[] oridts = new int[NUM_TYPES]; // max times to neighbors in each dir
    protected int[] oriDecideHelper = new int[NUM_TYPES];
    /**
     * Historical orientation values.
     */
    protected VectorHistogram oriHist = new VectorHistogram(NUM_TYPES);

    // takes about 350ns/event on tobi's t43p laptop at max performance (2.1GHz Pentium M, 1GB RAM)
    /**
     * A vector direction object used for iterating over neighborhood.
     */
    protected final class Dir {

        int x, y;

        Dir(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("%d,%d", x, y);
        }
    }
    /**
     * Offsets from a pixel to pixels forming the receptive field (RF) for an
     * orientation response. They are computed whenever the RF size changes.
     * First index is orientation 0-NUM_TYPES, second is index over offsets.
     */
    protected Dir[][] offsets = null;
    /**
     * The basic offsets for each orientation. You getString the perpendicular
     * orientation to i by indexing (i+2)%NUM_TYPES.
     */
    protected final Dir[] baseOffsets = {
        new Dir(1, 0), // right
        new Dir(1, 1), // 45 up right
        new Dir(0, 1), // up
        new Dir(-1, 1), // up left
    };

    /**
     * Creates a new instance of SimpleOrientationFilter
     *
     * @param chip
     */
    public AbstractOrientationFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        // properties, tips and groups
        final String size = "Size", tim = "Timing", disp = "Display", hist = "ori history";

        setPropertyTooltip(disp, "showLegendEnabled", "shows a legend for orientation colors");
        setPropertyTooltip(disp, "showGlobalEnabled", "shows line of average orientation");
        setPropertyTooltip(disp, "showVectorsEnabled", "shows local orientation segments");
        setPropertyTooltip(disp, "jitterAmountPixels", "how much to jitter vector origins by in pixels");
        setPropertyTooltip(disp, "jitterVectorLocations", "whether to jitter vector location to see overlapping vectors more easily");
        setPropertyTooltip(disp, "passAllEvents", "Passes all events, even those that do not get labled with orientation");
        setPropertyTooltip(disp, "showRawInputEnabled", "shows the input events, instead of the direction types");
        setPropertyTooltip(size, "subSampleShift", "Shift subsampled timestamp map stores by this many bits");
        setPropertyTooltip(size, "width", "width of RF, total is 2*width+1");
        setPropertyTooltip(size, "length", "length of half of RF, total length is length*2+1");
        setPropertyTooltip(tim, "minDtThresholdUs", "Coincidence time, events that pass this coincidence test are considerd for orientation output");
        setPropertyTooltip(tim, "dtRejectMultiplier", "<html>reject delta times more than this factor times <em>minDtThresholdUs</em> to reduce noise");
        setPropertyTooltip(tim, "dtRejectThreshold", "reject delta times more than this time in us to reduce effect of very old events");
        setPropertyTooltip(tim, "useAverageDtEnabled", "Use averarge delta time instead of minimum");
        setPropertyTooltip(tim, "multiOriOutputEnabled", "Enables multiple event output for all events that pass test");
        setPropertyTooltip(hist, "oriHistoryEnabled", "enable use of prior orientation values to filter out events not consistent with history");
        setPropertyTooltip(hist, "oriHistoryMixingFactor", "mixing factor for history of local orientation, increase to learn new orientations more quickly");
        setPropertyTooltip(hist, "oriHistoryDiffThreshold", "detected orientation must be within this value of historical value to pass. Value of 0.5 corresponds to 45degree with 4 directions.");
    }

    public Object getFilterState() {
        return lastTimesMap;
    }

    public boolean isGeneratingFilter() {
        return true;
    }

    @Override
    synchronized public void resetFilter() {
        if (!isFilterEnabled()) {
            return;
        }

//        allocateMaps(); // will allocate even if filter is enclosed and enclosing is not enabled
        oriHist.reset();
        if (lastTimesMap != null) {
            for (int[][] element : lastTimesMap) {
                for (int[] element2 : element) {
                    Arrays.fill(element2, 0);
                }
            }
        }
        if (oriHistoryMap != null) {
            for (float[] element : oriHistoryMap) {
                Arrays.fill(element, -1f);
            }
        }
    }

    /**
     * overrides super method to allocate or free local memory
     */
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            resetFilter();
        } else {
            lastTimesMap = null;
            oriHistoryMap = null;
        }
    }

    protected void checkMaps(EventPacket packet) {
        if ((lastTimesMap == null)
                || (lastTimesMap.length != chip.getSizeX())
                || (lastTimesMap[0].length != chip.getSizeY())
                || (lastTimesMap[0][0].length != 2)) { // changed to 2 for PolarityEvents
            allocateMaps();
        }
    }

    synchronized protected void allocateMaps() {
        if (!isFilterEnabled()) {
            return;
        }

        if (chip != null) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()][2]; // fixed to 2 for PolarityEvents
            oriHistoryMap = new float[chip.getSizeX()][chip.getSizeY()];
            for (float[] element : oriHistoryMap) {
                Arrays.fill(element, -1f);
            }
            log.info(String.format("allocated int[%d][%d][%d] array for last event times and float[%d][%d] array for orientation history", chip.getSizeX(), chip.getSizeY(), 2, chip.getSizeX(), chip.getSizeY()));
        }
        computeRFOffsets();
    }

    /**
     * @return the average orientation vector based on counts. A unit vector
     * pointing along each orientation is multiplied by the count of local
     * orientation events of that orientation. The vector sum of these weighted
     * unit vectors is returned. The angle theta increases CCW and starts along
     * x axis: 0 degrees is along x axis, 90 deg is up along y axis. This
     * resulting vector can be rendered by duplicating it pointing in the
     * opposite direction to show a "global" orientation. The total length then
     * represents the number and dominance of a particular type of orientation
     * event.
     */
    Point2D.Float computeGlobalOriVector() {
        final float scale = .1f;
        java.awt.geom.Point2D.Float p = new Point2D.Float();
        int[] counts = oriHist.getCounts();
        for (int i = 0; i < NUM_TYPES; i++) {
            double theta = ((Math.PI * i) / NUM_TYPES); // theta starts vertical up, type 0 is for vertical ori -Math.PI/2
            float wx = (float) Math.cos(theta);
            float wy = (float) Math.sin(theta);
            p.x += counts[i] * wx; // multiply unit vector by count of ori events
            p.y += counts[i] * wy;
        }
        p.x *= scale;
        p.y *= scale;
        return p;
    }

    /**
     * precomputes offsets for iterating over neighborhoods
     */
    protected void computeRFOffsets() {
        // compute array of Dir for each orientation
        rfSize = 2 * length * ((2 * width) + 1);
        offsets = new Dir[NUM_TYPES][rfSize];
        for (int ori = 0; ori < NUM_TYPES; ori++) {
            Dir d = baseOffsets[ori];
            Dir pd = baseOffsets[(ori + 2) % NUM_TYPES]; // this is offset in perpindicular direction
            int ind = 0;
            for (int s = -length; s <= length; s++) {
                if (s == 0) {
                    continue;
                }

                for (int w = -width; w <= width; w++) {
                    // for each line of RF
                    offsets[ori][ind] = new Dir((s * d.x) + (w * pd.x), (s * d.y) + (w * pd.y));
                    ind++;
                }
            }
        }
        dts = new int[NUM_TYPES][rfSize]; // delta times to neighbors in each direction
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        initFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) {
            return;
        }

        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        if (isShowGlobalEnabled()) {
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(6f);
            Point2D.Float p = computeGlobalOriVector();
            gl.glBegin(GL.GL_LINES);
            gl.glColor3f(1, 1, 1);
            gl.glVertex2f(-p.x, -p.y);
            gl.glVertex2f(p.x, p.y);
            gl.glEnd();
            gl.glPopMatrix();
        }
        if (isShowVectorsEnabled() && (getOutputPacket() != null)) {
            // draw individual orientation vectors
            gl.glPushMatrix();
            EventPacket outputPacket = getOutputPacket();
            for (Object o : outputPacket) {
                OrientationEventInterface e = (OrientationEventInterface) o;
                drawOrientationVector(gl, e);
            }
            gl.glPopMatrix();
        }

        if (isShowLegendEnabled()) {
            gl.glPushMatrix();
            gl.glTranslatef(-chip.getSizeX() * .1f, chip.getSizeY() * 0.5f, 0);
            gl.glScalef(3, 3, 1);
            DvsOrientationEvent e = new DvsOrientationEvent();
            e.setHasOrientation(true);
            for (int i = 0; i < NUM_TYPES; i++) {
                e.setOrientation((byte) i);
                drawOrientationVector(gl, e);
            }
            gl.glPopMatrix();
        }
    }

    // plots a single motion vector which is the number of pixels per second times scaling
    protected void drawOrientationVector(GL2 gl, OrientationEventInterface e) {
        if (!e.isHasOrientation()) {
            return;
        }

        byte ori = e.getOrientation();
        OrientationEventInterface.UnitVector d = OrientationEventInterface.unitVectors[ori];
        float jx = 0, jy = 0;
        if (jitterVectorLocations) {
            jx = (random.nextFloat() - .5f) * jitterAmountPixels;
            jy = (random.nextFloat() - .5f) * jitterAmountPixels;
        }
        gl.glLineWidth(3f);
        float[] c = chip.getRenderer().makeTypeColors(e.getNumCellTypes())[ori];
        gl.glColor3fv(c, 0);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f((e.getX() - (d.x * length)) + jx, (e.getY() - (d.y * length)) + jy);
        gl.glVertex2f((e.getX() + (d.x * length)) + jx, (e.getY() + (d.y * length)) + jy);
        gl.glEnd();
    }

    /**
     * Abstract method that filters in to out packet. If filtering is enabled,
     * the number of getOutputPacket() may be less than the number in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number.
     */
    @Override
    abstract public EventPacket<?> filterPacket(EventPacket<?> in);

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --length--">
    public int getLength() {
        return length;
    }

    /**
     * @param lengthToSet the length of the RF, actual length is twice this
     * because we search on each side of pixel by length
     */
    synchronized public void setLength(int lengthToSet) {
        int setValue = (lengthToSet < 1) ? 1 : lengthToSet;
        setValue = (lengthToSet > MAX_LENGTH) ? MAX_LENGTH : setValue;

        this.length = setValue;
        allocateMaps();
        putInt("length", setValue);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --getWidthInPixels--">
    public int getWidth() {
        return width;
    }

    /**
     * @param width the getWidthInPixels of the RF, 0 for a single line of
     * pixels, 1 for 3 lines, etc
     */
    synchronized public void setWidth(final int width) {
        int setValue = (width < 0) ? 0 : width;
        setValue = (width > (length - 1)) ? length - 1 : setValue;

        this.width = setValue;
        allocateMaps();
        putInt("width", setValue);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MinDtThreshold--">
    public int getMinDtThresholdUs() {
        return this.minDtThresholdUs;
    }

    public void setMinDtThresholdUs(final int minDtThreshold) {
        this.minDtThresholdUs = minDtThreshold;
        putInt("minDtThresholdUs", minDtThreshold);
        dtRejectThreshold = minDtThreshold * dtRejectMultiplier;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseAverageDtEnabled--">
    public boolean isUseAverageDtEnabled() {
        return useAverageDtEnabled;
    }

    public void setUseAverageDtEnabled(boolean useAverageDtEnabled) {
        this.useAverageDtEnabled = useAverageDtEnabled;
        putBoolean("useAverageDtEnabled", useAverageDtEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MultiOriOutputEnabled--">
    synchronized public boolean isMultiOriOutputEnabled() {
        return multiOriOutputEnabled;
    }

    synchronized public void setMultiOriOutputEnabled(boolean multiOriOutputEnabled) {
        this.multiOriOutputEnabled = multiOriOutputEnabled;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PassAllEvents--">
    public boolean isPassAllEvents() {
        return passAllEvents;
    }

    /**
     * Set this to true to pass all events even if they don't satisfy the
     * orientation test. These passed events have no orientation set.
     *
     * @param passAllEvents true to pass all events, false to pass only events
     * that pass coincidence test.
     */
    public void setPassAllEvents(boolean passAllEvents) {
        this.passAllEvents = passAllEvents;
        putBoolean("passAllEvents", passAllEvents);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SubSampleShift--">
    public int getSubSampleShift() {
        return subSampleShift;
    }

    /**
     * Sets the number of spatial bits to subsample events times by. Setting
     * this equal to 1, for example, subsamples into an event time map with
     * halved spatial resolution, aggregating over more space at coarser
     * resolution but increasing the search range by a factor of two at no
     * additional cost.
     *
     * @param subSampleShift the number of bits, 0 means no subsampling
     */
    public void setSubSampleShift(int subSampleShift) {
        if (subSampleShift < 0) {
            subSampleShift = 0;
        } else if (subSampleShift > 4) {
            subSampleShift = 4;
        }
        this.subSampleShift = subSampleShift;
        putInt("subSampleShift", subSampleShift);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --DtRejectMultiplier--">
    public int getDtRejectMultiplier() {
        return dtRejectMultiplier;
    }

    public void setDtRejectMultiplier(int dtRejectMultiplier) {
        if (dtRejectMultiplier < 2) {
            dtRejectMultiplier = 2;
        } else if (dtRejectMultiplier > 128) {
            dtRejectMultiplier = 128;
        }
        this.dtRejectMultiplier = dtRejectMultiplier;
        dtRejectThreshold = minDtThresholdUs * dtRejectMultiplier;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --OriHist--">
    public VectorHistogram getOriHist() {
        return oriHist;
    }

    public void setOriHist(VectorHistogram oriHist) {
        this.oriHist = oriHist;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --OriHistoryMixingFactor--">
    public float getOriHistoryMixingFactor() {
        return oriHistoryMixingFactor;
    }

    public void setOriHistoryMixingFactor(float oriHistoryMixingFactor) {
        if (oriHistoryMixingFactor > 1) {
            oriHistoryMixingFactor = 1;
        } else if (oriHistoryMixingFactor < 0) {
            oriHistoryMixingFactor = 0;
        }
        this.oriHistoryMixingFactor = oriHistoryMixingFactor;
        putFloat("oriHistoryMixingFactor", oriHistoryMixingFactor);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --OriHistoryEnabled--">
    public boolean isOriHistoryEnabled() {
        return oriHistoryEnabled;
    }

    public void setOriHistoryEnabled(boolean oriHistoryEnabled) {
        this.oriHistoryEnabled = oriHistoryEnabled;
        putBoolean("oriHistoryEnabled", oriHistoryEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --OriHistoryDiffThreshold--">
    public float getOriHistoryDiffThreshold() {
        return oriHistoryDiffThreshold;
    }

    public void setOriHistoryDiffThreshold(float oriHistoryDiffThreshold) {
        if (oriHistoryDiffThreshold > NUM_TYPES) {
            oriHistoryDiffThreshold = NUM_TYPES;
        }
        this.oriHistoryDiffThreshold = oriHistoryDiffThreshold;
        putFloat("oriHistoryDiffThreshold", oriHistoryDiffThreshold);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterVectorLocations--">
    /**
     * @return the jitterVectorLocations
     */
    public boolean isJitterVectorLocations() {
        return jitterVectorLocations;
    }

    /**
     * @param jitterVectorLocations the jitterVectorLocations to set
     */
    public void setJitterVectorLocations(boolean jitterVectorLocations) {
        this.jitterVectorLocations = jitterVectorLocations;
        putBoolean("jitterVectorLocations", jitterVectorLocations);
        getChip().getAeViewer().interruptViewloop();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmountPixels--">
    /**
     * @return the jitterAmountPixels
     */
    public float getJitterAmountPixels() {
        return jitterAmountPixels;
    }

    /**
     * @param jitterAmountPixels the jitterAmountPixels to set
     */
    public void setJitterAmountPixels(float jitterAmountPixels) {
        this.jitterAmountPixels = jitterAmountPixels;
        putFloat("jitterAmountPixels", jitterAmountPixels);
        getChip().getAeViewer().interruptViewloop();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowRawInputEnable--">
    public boolean isShowRawInputEnabled() {
        return showRawInputEnabled;
    }

    public void setShowRawInputEnabled(boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        putBoolean("showRawInputEnabled", showRawInputEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowVectorsEnabled--">
    public boolean isShowVectorsEnabled() {
        return showVectorsEnabled;
    }

    public void setShowVectorsEnabled(boolean showVectorsEnabled) {
        this.showVectorsEnabled = showVectorsEnabled;
        putBoolean("showVectorsEnabled", showVectorsEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowGlobalEnabled--">
    public boolean isShowGlobalEnabled() {
        return showGlobalEnabled;
    }

    public void setShowGlobalEnabled(boolean showGlobalEnabled) {
        this.showGlobalEnabled = showGlobalEnabled;
        putBoolean("showGlobalEnabled", showGlobalEnabled);
    }
    // </editor-fold>

    /**
     * @return the showLegendEnabled
     */
    public boolean isShowLegendEnabled() {
        return showLegendEnabled;
    }

    /**
     * @param showLegendEnabled the showLegendEnabled to set
     */
    public void setShowLegendEnabled(boolean showLegendEnabled) {
        this.showLegendEnabled = showLegendEnabled;
        putBoolean("showLegendEnabled", showLegendEnabled);
    }
}
