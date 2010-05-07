/*
 * SimpleOrientationFilter.java
 *
 * Created on November 2, 2005, 8:24 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing.label;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.VectorHistogram;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
/**
 * Computes simple-type orientation-tuned cells.
 *A switch allows WTA mode (only max 1 event generated) or many event (any orientation that passes coincidence threshold.
 *Another switch allows contour enhancement by using previous output orientation events to make it easier to make events along the same orientation.
 *Another switch decides whether to use max delay or average delay as the coincidence measure.
 * <p>
 *Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
 * 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
 *
 * The filter takes either PolarityEvents or BinocularEvents to create OrientationEvents or BinocularOrientationEvents.
 * @author tobi/phess
 */
public class SimpleOrientationFilter extends EventFilter2D implements Observer,FrameAnnotater{
    public static String getDescription (){
        return "Local orientation by spatio-temporal correlation";
    }

    public boolean isGeneratingFilter (){
        return true;
    }
    private boolean showGlobalEnabled = getPrefs().getBoolean("SimpleOrientationFilter.showGlobalEnabled",false);
    /** events must occur within this time along orientation in us to generate an event */
    protected int minDtThreshold = getPrefs().getInt("SimpleOrientationFilter.minDtThreshold",100000);
    /** We reject delta times that are larger than minDtThreshold by this factor, to rule out very old events */
    private int dtRejectMultiplier = getPrefs().getInt("SimpleOrientationFilter.dtRejectMultiplier",5);
    private int dtRejectThreshold = minDtThreshold * dtRejectMultiplier;
    private boolean multiOriOutputEnabled = getPrefs().getBoolean("SimpleOrientationFilter.multiOriOutputEnabled",false);
    /** set true to use min of average time to neighbors. Set false to use max time to neighbors (reduces # events) */
    private boolean useAverageDtEnabled = getPrefs().getBoolean("SimpleOrientationFilter.useAverageDtEnabled",true);
    private boolean contouringEnabled = getPrefs().getBoolean("SimpleOrientationFilter.contouringEnabled",false);
    private boolean passAllEvents = getPrefs().getBoolean("SimpleOrientationFilter.passAllEvents",false);
    private int subSampleShift = getPrefs().getInt("SimpleOrientationFilter.subSampleShift",0);
    private final int SUBSAMPLING_SHIFT = 1;
    private int length = getPrefs().getInt("SimpleOrientationFilter.searchDistance",3);
    private int width = getPrefs().getInt("SimpleOrientationFilter.width",0);
    private boolean oriHistoryEnabled = getPrefs().getBoolean("SimpleOrientationFilter.oriHistoryEnabled",false);
    private boolean showVectorsEnabled = getPrefs().getBoolean("SimpleOrientationFilter.showVectorsEnabled",false);
    private float oriHistoryMixingFactor = getPrefs().getFloat("SimpleOrientationFilter.oriHistoryMixingFactor",0.1f);
    private float oriDiffThreshold = getPrefs().getFloat("SimpleOrientationFilter.oriDiffThreshold",0.5f);
    /** Times of most recent input events: [x][y][polarity] */
    protected int[][][] lastTimesMap; // x,y,polarity
    /** Scalar map of past orientation values: [x][y] */
    protected float[][] oriHistoryMap;  // scalar orientation value x,y
    /** holds the times of the last output orientation events that have been generated */
//    int[][][][] lastOutputTimesMap;
    /** the number of cell output types */
    public final int NUM_TYPES = 4;
    protected int rfSize;

    /** Creates a new instance of SimpleOrientationFilter */
    public SimpleOrientationFilter (AEChip chip){
        super(chip);
        chip.addObserver(this);
        // properties, tips and groups
        final String size = "Size", tim = "Timing", disp = "Display";

        setPropertyTooltip(disp,"showGlobalEnabled","shows line of average orientation");
        setPropertyTooltip(tim,"minDtThreshold","Coincidence time, events that pass this coincidence test are considerd for orientation output");
        setPropertyTooltip(tim,"dtRejectMultiplier","reject delta times more than this factor times minDtThreshold to reduce noise");
        setPropertyTooltip(tim,"dtRejectThreshold","reject delta times more than this time in us to reduce effect of very old events");
        setPropertyTooltip("multiOriOutputEnabled","Enables multiple event output for all events that pass test");
        setPropertyTooltip(tim,"useAverageDtEnabled","Use averarge delta time instead of minimum");
        setPropertyTooltip(disp,"passAllEvents","Passes all events, even those that do not get labled with orientation");
        setPropertyTooltip(size,"subSampleShift","Shift subsampled timestamp map stores by this many bits");
        setPropertyTooltip(size,"width","width of RF, total is 2*width+1");
        setPropertyTooltip(size,"length","length of half of RF, total length is length*2+1");
        setPropertyTooltip(tim,"oriHistoryEnabled","enable use of prior orientation values to filter out events not consistent with history");
        setPropertyTooltip(disp,"showVectorsEnabled","shows local orientation segments");
        setPropertyTooltip(tim,"oriHistoryMixingFactor","mixing factor for history of local orientation, increase to learn new orientations more quickly");
        setPropertyTooltip(tim,"oriDiffThreshold","orientation must be within this value of historical value to pass");
    }

    public Object getFilterState (){
        return lastTimesMap;
    }

    synchronized public void resetFilter (){
        if ( !isFilterEnabled() ){
            return;
        }
//        allocateMaps(); // will allocate even if filter is enclosed and enclosing is not enabled
        oriHist.reset();
        if ( lastTimesMap != null ){
            for ( int i = 0 ; i < lastTimesMap.length ; i++ ){
                for ( int j = 0 ; j < lastTimesMap[i].length ; j++ ){
                    Arrays.fill(lastTimesMap[i][j],0);
                }
            }
        }
        if ( oriHistoryMap != null ){
            for ( int i = 0 ; i < oriHistoryMap.length ; i++ ){
                Arrays.fill(oriHistoryMap[i],0f);
            }
        }

    }

    /** overrides super method to allocate or free local memory */
    @Override
    synchronized public void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        if ( yes ){
            resetFilter();
        } else{
            lastTimesMap = null;
//            lastOutputTimesMap=null;
            out = null;
        }
    }

    private void checkMaps (){
        if ( lastTimesMap == null || lastTimesMap.length != chip.getSizeX() || lastTimesMap[0].length != chip.getSizeY() || lastTimesMap[0][0].length != chip.getNumCellTypes() ){
            allocateMaps();
        }
    }

    synchronized private void allocateMaps (){
        if ( !isFilterEnabled() ){
            return;
        }
//        log.info("SimpleOrientationFilter.allocateMaps()");
        if ( chip != null ){
            lastTimesMap = new int[ chip.getSizeX() ][ chip.getSizeY() ][ chip.getNumCellTypes() ];
            oriHistoryMap = new float[ chip.getSizeX() ][ chip.getSizeY() ];
            //lastOutputTimesMap=new int[chip.getSizeX()][chip.getSizeY()][NUM_TYPES][2];
        }

        computeRFOffsets();
    }
    /** Historical orientation values. */
    protected VectorHistogram oriHist = new VectorHistogram(NUM_TYPES);

    /** @return the average orientation vector based on counts. A unit vector pointing along each orientation
     * is multiplied by the count of local orientation events of that orientation. The vector sum of these weighted unit
     * vectors is returned.
     * The angle theta increases CCW and starts along x axis: 0 degrees is along x axis, 90 deg is up along y axis.
     * This resulting vector can be rendered by duplicating it pointing in the opposite direction to show a "global" orientation.
     * The total length then represents the number and dominance of a particular type of orientation event.
     */
    Point2D.Float computeGlobalOriVector (){
        final float scale = .1f;
        java.awt.geom.Point2D.Float p = new Point2D.Float();
        int[] counts = oriHist.getCounts();
        for ( int i = 0 ; i < NUM_TYPES ; i++ ){
            double theta = ( Math.PI * (double)i / NUM_TYPES ); // theta starts vertical up, type 0 is for vertical ori -Math.PI/2
            float wx = (float)Math.cos(theta);
            float wy = (float)Math.sin(theta);
            p.x += counts[i] * wx; // multiply unit vector by count of ori events
            p.y += counts[i] * wy;
        }
        p.x *= scale;
        p.y *= scale;
        return p;
    }
    /** Delta times to neighbors in each direction. */
    protected int[][] dts = null; // new int[NUM_TYPES][length*2+1]; // delta times to neighbors in each direction
    /** Max times to neighbors in each dir. */
    protected int[] maxdts = new int[ NUM_TYPES ]; // max times to neighbors in each dir
    // takes about 350ns/event on tobi's t43p laptop at max performance (2.1GHz Pentium M, 1GB RAM)
    /** A vector direction object used for iterating over neighborhood. */
    protected final class Dir{
        int x, y;

        Dir (int x,int y){
            this.x = x;
            this.y = y;
        }

        public String toString (){
            return String.format("%d,%d",x,y);
        }
    }
    /** 
     * Offsets from a pixel to pixels forming the receptive field (RF) for an orientation response.
     * They are computed whenever the RF size changes.
     * First index is orientation 0-NUM_TYPES, second is index over offsets.
     */
    protected Dir[][] offsets = null;
    /** The basic offsets for each orientation.
    You get the perpindicular orientation to i by indexing (i+2)%NUM_TYPES.
     */
    protected final Dir[] baseOffsets = {
        new Dir(1,0), // right
        new Dir(1,1), // 45 up right
        new Dir(0,1), // up
        new Dir(-1,1), // up left
    };

    /** precomputes offsets for iterating over neighborhoods */
    private void computeRFOffsets (){
        // compute array of Dir for each orientation
        rfSize = 2 * length * ( 2 * width + 1 );
        offsets = new Dir[ NUM_TYPES ][ rfSize ];
        for ( int ori = 0 ; ori < NUM_TYPES ; ori++ ){
            Dir d = baseOffsets[ori];
            int ind = 0;
            for ( int s = -length ; s <= length ; s++ ){
                if ( s == 0 ){
                    continue;
                }
                Dir pd = baseOffsets[( ori + 2 ) % NUM_TYPES]; // this is offset in perpindicular direction
                for ( int w = -width ; w <= width ; w++ ){
                    // for each line of RF
                    offsets[ori][ind++] = new Dir(s * d.x + w * pd.x,s * d.y + w * pd.y);
                }
            }
        }
        dts = new int[ NUM_TYPES ][ rfSize ]; // delta times to neighbors in each direction
    }

    public int getMinDtThreshold (){
        return this.minDtThreshold;
    }

    public void setMinDtThreshold (final int minDtThreshold){
        this.minDtThreshold = minDtThreshold;
        getPrefs().putInt("SimpleOrientationFilter.minDtThreshold",minDtThreshold);
        dtRejectThreshold = minDtThreshold * dtRejectMultiplier;
    }

    public VectorHistogram getOriHist (){
        return oriHist;
    }

    public void setOriHist (VectorHistogram oriHist){
        this.oriHist = oriHist;
    }

    public boolean isUseAverageDtEnabled (){
        return useAverageDtEnabled;
    }

    public void setUseAverageDtEnabled (boolean useAverageDtEnabled){
        this.useAverageDtEnabled = useAverageDtEnabled;
        getPrefs().putBoolean("SimpleOrientationFilter.useAverageDtEnabled",useAverageDtEnabled);
    }

    synchronized public boolean isMultiOriOutputEnabled (){
        return multiOriOutputEnabled;
    }

    synchronized public void setMultiOriOutputEnabled (boolean multiOriOutputEnabled){
        this.multiOriOutputEnabled = multiOriOutputEnabled;
    }

    public void initFilter (){
        resetFilter();
    }

    public void update (Observable o,Object arg){
        initFilter();
    }

    public int getLength (){
        return length;
    }
    public static final int MAX_LENGTH = 6;

    /** @param searchDistance the length of the RF, actual length is twice this because we search on each side of pixel by length
     */
    synchronized public void setLength (int searchDistance){
        if ( searchDistance > MAX_LENGTH ){
            searchDistance = MAX_LENGTH;
        } else if ( searchDistance < 1 ){
            searchDistance = 1; // limit size
        }
        this.length = searchDistance;
        allocateMaps();
        getPrefs().putInt("SimpleOrientationFilter.searchDistance",searchDistance);
    }

    public int getWidth (){
        return width;
    }

    /** @param width the width of the RF, 0 for a single line of pixels, 1 for 3 lines, etc
     */
    synchronized public void setWidth (int width){
        if ( width < 0 ){
            width = 0;
        }
        if ( width > length - 1 ){
            width = length - 1;
        }
        this.width = width;
        allocateMaps();
        getPrefs().putInt("SimpleOrientationFilter.width",width);
    }

    public void annotate (Graphics2D g){
    }

    public void annotate (GLAutoDrawable drawable){
        if ( !isAnnotationEnabled() ){
            return;
        }
        GL gl = drawable.getGL();

        if ( isShowGlobalEnabled() ){
            if ( gl == null ){
                return;
            }
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX() / 2,chip.getSizeY() / 2,0);
            gl.glLineWidth(6f);
            Point2D.Float p = computeGlobalOriVector();
            gl.glBegin(GL.GL_LINES);
            gl.glColor3f(1,1,1);
            gl.glVertex2f(-p.x,-p.y);
            gl.glVertex2f(p.x,p.y);
            gl.glEnd();
            gl.glPopMatrix();
        }
        if ( isShowVectorsEnabled() ){
            // draw individual orientation vectors
            gl.glPushMatrix();
            gl.glColor3f(1,1,1);
            gl.glLineWidth(1f);
            gl.glBegin(GL.GL_LINES);
            for ( Object o:out ){
                OrientationEvent e = (OrientationEvent)o;
                drawOrientationVector(gl,e);
            }
            gl.glEnd();
            gl.glPopMatrix();
        }
    }

    // plots a single motion vector which is the number of pixels per second times scaling
    private void drawOrientationVector (GL gl,OrientationEvent e){
        if ( !e.hasOrientation ){
            return;
        }
        OrientationEvent.UnitVector d = OrientationEvent.unitVectors[e.orientation];
        gl.glVertex2f(e.x - d.x * length,e.y - d.y * length);
        gl.glVertex2f(e.x + d.x * length,e.y + d.y * length);
    }

    public boolean isShowVectorsEnabled (){
        return showVectorsEnabled;
    }

    public void setShowVectorsEnabled (boolean showVectorsEnabled){
        this.showVectorsEnabled = showVectorsEnabled;
        getPrefs().putBoolean("SimpleOrientationFilter.showVectorsEnabled",showVectorsEnabled);
    }

    public void annotate (float[][][] frame){
//        if(!isAnnotationEnabled()) return;
    }

    public boolean isShowGlobalEnabled (){
        return showGlobalEnabled;
    }

    public void setShowGlobalEnabled (boolean showGlobalEnabled){
        this.showGlobalEnabled = showGlobalEnabled;
        getPrefs().putBoolean("SimpleOrientationFilter.showGlobalEnabled",showGlobalEnabled);
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number.
     */
    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        if ( in == null ){
            return null;
        }
        if ( enclosedFilter != null ){
            in = enclosedFilter.filterPacket(in);
        }

        int n = in.getSize();
        if ( n == 0 ){
            return in;
        }

        Class inputClass = in.getEventClass();
        if ( !( inputClass == PolarityEvent.class || inputClass == BinocularEvent.class ) ){
            log.warning("wrong input event type " + in.getEventClass() + ", disabling filter");
            setFilterEnabled(false);
            return in;
        }

        //check for binocular input
        boolean isBinocular;
        if ( in.getEventClass() == BinocularEvent.class ){
            isBinocular = true;
            checkOutputPacketEventType(BinocularOrientationEvent.class);
        } else{
            isBinocular = false;
            checkOutputPacketEventType(OrientationEvent.class);
        }

        OutputEventIterator outItr = out.outputIterator();

        int sizex = chip.getSizeX() - 1;
        int sizey = chip.getSizeY() - 1;

        oriHist.reset();
        checkMaps();

        // for each event write out an event of an orientation type if there have also been events within past dt along this type's orientation of the
        // same retina polarity
        for ( Object ein:in ){
            PolarityEvent e = (PolarityEvent)ein;
            int type = e.getType();
            int x = e.x >>> subSampleShift;
            int y = e.y >>> subSampleShift;

            /* (Peter Hess) monocular events use eye = 0 as standard. therefore some arrays will waste memory, because eye will never be 1 for monocular events
             * in terms of performance this may not be optimal. but as long as this filter is not final, it makes rewriting code much easier, because
             * monocular and binocular events may be handled the same way.
             */
            int eye = 0;
            if ( isBinocular ){
                if ( ( (BinocularEvent)ein ).eye == BinocularEvent.Eye.RIGHT ){
                    eye = 1;
                }
            }
            if ( eye == 1 ){
                type = type << 1;
            }
            lastTimesMap[x][y][type] = e.timestamp;

            // get times to neighbors in all directions
            // check if search distance has been changed before iterating - for some reason the synchronized doesn't work
            int xx, yy;
            for ( int ori = 0 ; ori < NUM_TYPES ; ori++ ){
                // for each orienation, compute the dts to each previous event in RF of this cell
                int[] dtsThisOri = dts[ori]; // this is ref to array of delta times
                Dir[] d = offsets[ori]; // this is vector of spatial offsets for this orientation from lookup table
                for ( int i = 0 ; i < d.length ; i++ ){ //=-length;s<=length;s++){ // now we march along both sides of this pixel
                    xx = x + d[i].x;
                    if ( xx < 0 || xx > sizex ){
                        continue;
                    }
                    yy = y + d[i].y;
                    if ( yy < 0 || yy > sizey ){
                        continue; // indexing out of array
                    }
                    dtsThisOri[i] = e.timestamp - lastTimesMap[xx][yy][type]; // the offsets are multiplied by the search distance
                    // x and y are already offset so that the index into the padded map, avoding ArrayAccessViolations
                }
            }

            if ( useAverageDtEnabled ){
                // now get sum of dts to neighbors in each direction
                for ( int j = 0 ; j < NUM_TYPES ; j++ ){
                    maxdts[j] = 0; // this is sum here despite name maxdts
                    int m = dts[j].length;
                    int count = 0;
                    for ( int k = 0 ; k < m ; k++ ){
                        int dt = dts[j][k];
                        if ( dt > dtRejectThreshold ){
                            continue; // we're averaging delta times; this rejects outliers
                        }
                        maxdts[j] += dt; // average dt
                        count++;
                    }
                    if ( count > 0 ){
                        maxdts[j] /= count; // normalize by RF size
                    }
                    if ( count == 0 ){
                        // no samples, all outside outlier rejection threshold
                        maxdts[j] = Integer.MAX_VALUE;
                    }
                }
            } else{ // use max dt
                // now get maxdt to neighbors in each directoin
                for ( int j = 0 ; j < NUM_TYPES ; j++ ){
                    maxdts[j] = Integer.MIN_VALUE;
                    int m = dts[j].length;
                    for ( int k = 0 ; k < m ; k++ ){
                        maxdts[j] = dts[j][k] > maxdts[j] ? dts[j][k] : maxdts[j]; // max dt to neighbor
                    }
                }
            }

            if ( !multiOriOutputEnabled ){
                // here we do a WTA, only 1 event max gets generated in optimal orienation IFF is also satisfies coincidence timing requirement

                // now find min of these, this is most likely orientation, iff this time is also less than minDtThreshold
                int mindt = minDtThreshold, dir = -1;
                for ( int k = 0 ; k < NUM_TYPES ; k++ ){
                    if ( maxdts[k] < mindt ){
                        mindt = maxdts[k];
                        dir = k;
                    }
                }



                if ( dir == -1 ){ // didn't find a good orientation
                    if ( passAllEvents ){
                        if ( !isBinocular ){
                            OrientationEvent eout = (OrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.hasOrientation = false;
                        } else{
                            BinocularOrientationEvent eout = (BinocularOrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.hasOrientation = false;
                        }
                    }
                    // no dt was < threshold
                    continue;
                }
                if ( oriHistoryEnabled ){
                    // update lowpass orientation map
                    float f = oriHistoryMap[x][y];
                    f = ( 1 - oriHistoryMixingFactor ) * f + oriHistoryMixingFactor * dir;
                    oriHistoryMap[x][y] = f;

                    float fd = f - dir;
                    final int halfTypes = NUM_TYPES / 2;
                    if ( fd > halfTypes ){
                        fd = fd - NUM_TYPES;
                    } else if ( fd < -halfTypes ){
                        fd = fd + NUM_TYPES;
                    }
                    if ( Math.abs(fd) > oriDiffThreshold ){
                        continue;
                    }
                }
                // now write output cell iff all events along dir occur within minDtThreshold
                if ( isBinocular ){
                    BinocularOrientationEvent eout = (BinocularOrientationEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                    eout.orientation = (byte)dir;
                    eout.hasOrientation = true;
                } else{
                    OrientationEvent eout = (OrientationEvent)outItr.nextOutput();
                    eout.copyFrom(e);
                    eout.orientation = (byte)dir;
                    eout.hasOrientation = true;
                }
//                lastOutputTimesMap[e.x][e.y][dir][eye]=e.timestamp;
                oriHist.add(dir);
            } else{

                // here events are generated in oris that satisfy timing; there is no WTA
                for ( int k = 0 ; k < NUM_TYPES ; k++ ){
                    if ( maxdts[k] < minDtThreshold ){
                        if ( isBinocular ){
                            BinocularOrientationEvent eout = (BinocularOrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation = (byte)k;
                            eout.hasOrientation = true;
                        } else{
                            OrientationEvent eout = (OrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation = (byte)k;
                            eout.hasOrientation = true;
                        }
//                        lastOutputTimesMap[e.x][e.y][k][eye]=e.timestamp;
                        oriHist.add(k);
                    }
                }

            }
        }
        return out;
    }

    public boolean isPassAllEvents (){
        return passAllEvents;
    }

    /** Set this to true to pass all events even if they don't satisfy orientation test. These passed events have no orientation set.
    @param passAllEvents true to pass all events, false to pass only events that pass coicidence test.
     */
    public void setPassAllEvents (boolean passAllEvents){
        this.passAllEvents = passAllEvents;
        getPrefs().putBoolean("SimpleOrientationFilter.passAllEvents",passAllEvents);
    }

    public int getSubSampleShift (){
        return subSampleShift;
    }

    /** Sets the number of spatial bits to subsample events times by. Setting this equal to 1, for example,
    subsamples into an event time map with halved spatial resolution, aggreating over more space at coarser resolution
    but increasing the search range by a factor of two at no additional cost
    @param subSampleShift the number of bits, 0 means no subsampling
     */
    public void setSubSampleShift (int subSampleShift){
        if ( subSampleShift < 0 ){
            subSampleShift = 0;
        } else if ( subSampleShift > 4 ){
            subSampleShift = 4;
        }
        this.subSampleShift = subSampleShift;
        getPrefs().putInt("SimpleOrientationFilter.subSampleShift",subSampleShift);
    }

    public int getDtRejectMultiplier (){
        return dtRejectMultiplier;
    }

    public void setDtRejectMultiplier (int dtRejectMultiplier){
        if ( dtRejectMultiplier < 2 ){
            dtRejectMultiplier = 2;
        } else if ( dtRejectMultiplier > 128 ){
            dtRejectMultiplier = 128;
        }
        this.dtRejectMultiplier = dtRejectMultiplier;
        dtRejectThreshold = minDtThreshold * dtRejectMultiplier;
    }

    public float getOriHistoryMixingFactor (){
        return oriHistoryMixingFactor;
    }

    public void setOriHistoryMixingFactor (float oriHistoryMixingFactor){
        if ( oriHistoryMixingFactor > 1 ){
            oriHistoryMixingFactor = 1;
        } else if ( oriHistoryMixingFactor < 0 ){
            oriHistoryMixingFactor = 0;
        }
        this.oriHistoryMixingFactor = oriHistoryMixingFactor;
        getPrefs().putFloat("SimpleOrientationFilter.oriHistoryMixingFactor",oriHistoryMixingFactor);
    }

    public float getOriDiffThreshold (){
        return oriDiffThreshold;
    }

    public void setOriDiffThreshold (float oriDiffThreshold){
        if ( oriDiffThreshold > NUM_TYPES ){
            oriDiffThreshold = NUM_TYPES;
        }
        this.oriDiffThreshold = oriDiffThreshold;
        getPrefs().putFloat("SimpleOrientationFilter.oriDiffThreshold",oriDiffThreshold);
    }

    public boolean isOriHistoryEnabled (){
        return oriHistoryEnabled;
    }

    public void setOriHistoryEnabled (boolean oriHistoryEnabled){
        this.oriHistoryEnabled = oriHistoryEnabled;
        getPrefs().putBoolean("SimpleOrientationFilter.oriHistoryEnabled",oriHistoryEnabled);
    }
}
