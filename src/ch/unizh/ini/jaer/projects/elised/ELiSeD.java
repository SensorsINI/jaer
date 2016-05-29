package ch.unizh.ini.jaer.projects.elised;

// authors: much copy-paste from Jonas Strubels' c++ code; Susi; Christian

import ch.unizh.ini.jaer.projects.elised.dynamicBuffer.RemovalFunction;
import ch.unizh.ini.jaer.projects.elised.dynamicBuffer.BufferSizeEstimator;
import ch.unizh.ini.jaer.projects.elised.dynamicBuffer.ResizeableRingbuffer;
import ch.unizh.ini.jaer.projects.util.ATanHelper;
import ch.unizh.ini.jaer.projects.util.ColorHelper;
import ch.unizh.ini.jaer.projects.util.TrailingRingBuffer;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.gl2.GLUT;
import java.io.IOException;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;

//  A line segment detector that finds regions of similar timestamp-gradient
//  and fits lines to those regions.

/*When a new event is processed, it is stored as a level line pixel in the pixelmap
  (a 2d-map of all pixels in the ringbuffer without those overwritten by a newer 
  pixel with the same coordinates) and is added to the ringbuffer. A level line pixel has
  a level line angle, this is the angle orthogonal to its gradient. This angle lies between 
  +-180°. Adding a pixel to a line segment works on the basis of level line 
  angles. Each segment store the average l.l.angle of their support, and a pixel 
  can only be added if modulo 90, (difference mod 180,) its l.l.angle is similar to this average.

  DIFFERENCE TO OLDER VERSION: 
Changed: - delete pixel from old support, if neccessary. Note: Leaving it if it still fits 
    (removing and re-adding) promotes false positives.
    - new logging option for R
    - reinserted the option to use a timestamp gradient
    - property 'maxAge...', reintroduced
        
to do at cleanup:
    - remove property "predictTimestamps", this can be set true; not much difference
        but a few more pixels with gradient.
    - same for "orAddIfDensityIncreases" -- always switch on
        
*/

@Description("Event based line segment detection at described in [1] C. Brandli, J. Strubel, S. Keller, D. Scaramuzza, and T. Delbruck, “ELiSeD – An Event-Based Line Segment Detector,” in IEEE Conf. on Event Based Communication, Control and Signal Processing 2016 (EBCCSP2016), Krakow, Poland, 2016, p. (accepted). ")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ELiSeD extends EventFilter2D implements FrameAnnotater {

    //=== GLOBAL VARIABLES ===
    private int sx, sy;                         //width and heigth of the dvs, as well as of the pixelmap and timestampMap
    private int sobelWidth;
    int[] onTimestampMap;   
    int[] offTimestampMap;
    private float[] eventrateMap;     // first index: to which resolution the map belongs; second index: the actual map
    //private float[][] offEventrateMap;
    private int[] filterX, filterY;     //sobel filters: small or big
    
    private long lineSegmentID;
    private long eventCountLogging;
    private long eventCountDecay;
    private long latestTS;

    //private HashMap<LineSupport, HashSet<LevelLinePixel>> lineSupport;
    private HashSet<LineSupport> lineSupportRegions;             
    private HashSet<LineSupport> splitCandidates;
    
    private ResizeableRingbuffer<Integer> indexbuffer;
    private RemovalFunction<Integer> removalFun;        //called at resize for every invalidated pixel
    //private TrailingRingBuffer<Integer> indexbuffer;
    private LevelLinePixel[] pixelmap;                   // "LevelLinePixel[i][x][y]" = LevelLinePixel_i[x+sx*y]; use  mapIndex(int x, int y)
    
    public ATanHelper atanHelper;
    private FilterChain filterChain;
    private final BackgroundActivityFilter backgroundFilter;
    private final BufferSizeEstimator bufferSizeEstimator;
    static final private TobiLogger logger = new TobiLogger("ELiSeD-Log", "Log of the support regions");
    private AEChipRenderer renderer;

    //=== GLOBAL SETTINGS ===
    private final boolean useATanTable = true;
    private final float resolutionATan = 1.0f;
    //private static int maxAge = 100000;   //only used together with the timestamp gradient; maximum time interval between current event and event in 3x3/5x5 neighbourhood for the latter to be used in gradient calculations
    private static final float KERNEL_SCALING_FACTOR3 = 1.0f / 8.0f;   //assuming a 3^2 sobel filter; no theory behind this (yet)
    private static final float KERNEL_SCALING_FACTOR5 = 1.0f / 96.0f;
    
    //filter kernels
    public static int[] sobel3X = {1, 0, -1, 2, 0, -2, 1, 0, -1};
    public static int[] sobel3Y = {1, 2, 1, 0, 0, 0, -1, -2, -1};
    public static int[] sobel5X = {1, 2, 0, -2, -1, 4, 8, 0, -8, -4, 6, 12, 0, -12, -6, 4, 8, 0, -8, -4, 1, 2, 0, -2, -1};
    public static int[] sobel5Y = {1, 4, 6, 4, 1, 2, 8, 12, 8, 2, 0, 0, 0, 0, 0, -2, -8, -12, -8, -2, -1, -4, -6, -4, -1};

    //=== USER CONTROLS ===
    private int bufferSize = getInt("bufferSize", 8000);  //set this automatically when buffer is resized; at setting this; if this is changed and indexbuffer has a different size, resize
    private int maxAgeGradientCalculation = getInt("maxAge", 40000);
    public int minLineSupport = getInt("minLineSupport", 5);
    private int minNeighbors = getInt("minNeighbors", 1);
    private float toleranceAngle = getFloat("toleranceAngle", 22.5f);       //was 20.0f; how much a level line can deviate from another for both pixels (segments?) to still be combined to one segment
    private boolean splittingActive = getBoolean("splittingActive", true);
    private float widthToSplit = getFloat("widthToSplit", 15.0f);
    private boolean mergeOnlyIfLinesAligned = getBoolean("mergeOnlyIfLinesAligned", true);
    private float maxDistance = getFloat("maxDistance", 3.5f);    // for deciding if lines are to be merged if this is false, angle90 and llAngle90 of pixels and segments, respectively, counted up/down in LineSupport.  Those lie in the range +-90°.   Also, if this is false, angles have to lie only modulo 180 within a certain range to each other so that pixels or segments are seen as similar oriented
    private float decayFactor = getFloat("decayFactor", 0.96f);
    private boolean addOnlyIfDensityHigh = getBoolean("mergeOnlyIfDensityHigh", true);
    private boolean orAddIfDensityIncreases = getBoolean("orAddIfDensityIncreases", false);
    private float minDensity = getFloat("minDensity", 0.9f);   //this one is a criterion for both merging and adding one pixel
    private int minSupportForDensityTest = getInt("minSupportForDensityTest", 7);
    private boolean distinguishOpposingGradients = getBoolean("distinguishOpposingGradients", true);
    private boolean linesDistinguishOnAndOff = getBoolean("linesDistinguishOnAndOff", true);
    private boolean useWideSobelKernel = getBoolean("useWideSobelKernel", true);
    private boolean useTimestampGradient = getBoolean("useTimestampGradient", true);
    private boolean predictTimestamps = getBoolean("predictTimestamps", true);
    
    private boolean blockEvents = getBoolean("blockEvents", false);
    private boolean annotateAngles = getBoolean("annotateAngles", false);
    private boolean annotateWidth = getBoolean("annotateWidth", false);
    private boolean annotateLineSegments = getBoolean("annotateLineSegments", true);
    private RenderingSource renderSource = RenderingSource.valueOf(getString("renderSource","SEGMENTS"));
    private RenderingColor renderColor = RenderingColor.valueOf(getString("renderColor","ID")); 
    private float annotateAlpha = getFloat("annotateAlpha",0.5f);

    private boolean loggingEnabled = getBoolean("logingEnabled", false);
    private int loggingEventCount = getInt("loggingEventCount", 5000);
    private boolean timestampIncludedInEachLog = getBoolean("timestampIncludedInEachLog", false);
    private boolean dynamicBuffer = getBoolean("dynamicBuffer", false);    
    private final int mapDecayEventCount = getInt("mapDecayEventCount", 200);


    public static enum RenderingSource{BUFFER, ARRAY, SUPPORT, SEGMENTS, NONE};
    public static enum RenderingColor{ORIENTATION, ID};
    
    public ELiSeD(AEChip chip) {
        super(chip);
        this.chip = chip;
        renderer = (AEChipRenderer) chip.getRenderer();
        
        filterChain = new FilterChain(chip);
        backgroundFilter = new BackgroundActivityFilter(chip);
        bufferSizeEstimator = new BufferSizeEstimator(this.chip);
        filterChain.add(backgroundFilter);
        filterChain.add(bufferSizeEstimator);
        setEnclosedFilterChain(filterChain);
        initFilter();
        
        setPropertyTooltip("basic", "mapDecayEventCount", "The number of events processed between \n"
                + "two times of decay of the gradient maps.");
        setPropertyTooltip("basic", "minLineSupport", "All line segments with a support bigger than this will"
                + " be logged as long segments and will be shown on the screen.");
        setPropertyTooltip("basic", "toleranceAngle", "Maximum angle difference for joining pixels/segments in "
                + "the growth step. Difference between angles of pixels or between a pixels"
                + " angle and the average angle in a segment.");
        setPropertyTooltip("basic", "useWideSobelKernel", "Determines wheter the 5x5 Sobel kernel should be used"
                + "filter used in gradient calculation.");
        setPropertyTooltip("basic", "bufferSize", "size of buffer");
        setPropertyTooltip("basic", "minLineSupport", "minimal line support to be displayed");
        setPropertyTooltip("basic", "linesDistinguishOnAndOff", "If ticked, there are separate on and off support regions.");
        setPropertyTooltip("basic", "distinguishOpposingGradients", "If this is on, level lines are compared modulo 360°, otherwise just modulo 180°."
                + " If this is on, edges moving in opposite direction are never merged. Bad if the image is shaking.");
        setPropertyTooltip("basic", "minNeighbors", "minimal number of neighbors to create new support region");

        setPropertyTooltip("annotation", "annotateAngles", "Write the segments orientations next to them.");
        setPropertyTooltip("annotation", "annotateWidth", "Write the segments widths next to them.");
        setPropertyTooltip("annotation", "annotateLineSegments", "Annotate actual line segment lines");
        setPropertyTooltip("annotation", "blockEvents", "Blocks all events");
        setPropertyTooltip("annotation", "renderColor", "Determines the method by which the pixels are colored");
        setPropertyTooltip("annotation", "renderSource", "Determines which pixels should be rendered");
        setPropertyTooltip("annotation", "annotateAlpha", "Sets the transparency for the annotated pixels. Does not work for DVS128");
        
        setPropertyTooltip("merging / splitting", "splittingActive", "Split segments that are no real segments; e.g. when two support regions merge where they"
                + " shouldn't. Iterates over all segments once per packet.");
        setPropertyTooltip("merging / splitting", "widthToSplit", "If the ellipse fit through a support region is wider than this, split the segment.");
        setPropertyTooltip("merging / splitting", "mergeOnlyIfLinesAligned", "If the distance between the center of the smaller segment and the line "
                + "through the bigger segment is bigger than maxDistance, don't merge the segments.");
        setPropertyTooltip("merging / splitting", "maxDistance", "Only important if mergeOnlyIfLinesAligned is true.");
        
        setPropertyTooltip("extras", "addOnlyIfDensityHigh", "If pixel density per ellipse area would "
                 + "be below minDensity afterwards, the pixel will not be added.");
        setPropertyTooltip("extras", "minDensity", "Only important if addOnlyIfDensityHigh is true.");
        setPropertyTooltip("extras", "minSupportForDensityTest", "Density criterion is considered only if a segment is bigger than this.");
        setPropertyTooltip("extras", "dynamicBuffer", "Resizes the buffed according to the spatial contrast");
        setPropertyTooltip("extras", "decayFactor", "For calculating the rate gradient. 0.95 to 0.96 seem ok.");
        setPropertyTooltip("extras", "useTimestampGradient", "Use a pixel gradient calculated on timestamp values. Otherwise: Gradient on nr of recent events.");
        
        setPropertyTooltip("logging", "loggingEnabled", "Enables logging of the line segments for analysis in Matlab");
        setPropertyTooltip("logging", "loggingEventCount", "Sets the logging frequency. After given number of events, the segments are logged");
        setPropertyTooltip("logging", "timestampIncludedInEachLog", "All logged rows have the same format, except for header. Good for use with R.");
        setPropertyTooltip("logging", "doLogNow", "Manually trigger the log of the present line segments");
    
        setPropertyTooltip("experimenting", "predictTimestamps", "if one timestamp doesn't exist yet, take the "
                + "timestamp from the opposite site.");
        setPropertyTooltip("experimenting", "orAddIfDensityIncreases", "add pixel also if density is not high enough anymore but would increase");
        setPropertyTooltip("experimenting", "logTimePerEvent", "Log the time spent in ELiSeD-related functions per event. Not compatible with "
                + "other logging functions/ e.g. a time-log will now always be opened when user klicks 'do log now'. To be removed after use.");
        setPropertyTooltip("experimenting", "maxAge", "The maximum age of which pixels are still used in gradient calculation. Should not be too"
                + " big, but also not too low. Maybe make this depend on the lowest timestamp buffered.");
    }

    @Override
    synchronized public void resetFilter() {
        renderer = (AEChipRenderer) chip.getRenderer();
        renderer.setExternalRenderer(false);
        setAnnotateAlpha(annotateAlpha);
        
        filterChain.reset();

        sx = chip.getSizeX();
        sy = chip.getSizeY();
        
        lineSegmentID = 0;
        eventCountLogging = 0;
        eventCountDecay = 0;
        latestTS = 0;
        indexbuffer = new ResizeableRingbuffer(Integer.class, bufferSizeEstimator.getMaxSize(), bufferSize);
        //indexbuffer = new TrailingRingBuffer(Integer.class,bufferSize);
        if(useTimestampGradient){
            onTimestampMap = new int[sx*sy];        
            offTimestampMap = new int[sx*sy];}
        else{
            eventrateMap = new float[sx*sy];}
            //offEventrateMap = new float[resolutions.length][];
        pixelmap = new LevelLinePixel[sx*sy];
        for(int y = 0; y<sy; y++){
                for(int x = 0; x<sx; x++){
                    int idx = x + sx * y;
                    pixelmap[idx] = new LevelLinePixel(x,y);
                }
            }
        
        if (!(lineSupportRegions==null))
            lineSupportRegions.clear();
        
        removalFun = new RemovalFunction<Integer>(){
                @Override
                public void remove(Integer index) {
                   removeEvent(index);}
            };

        if (useWideSobelKernel) {
            filterX = sobel5X;
            filterY = sobel5Y;
            sobelWidth = 5;
        } else {
            filterX = sobel3X;
            filterY = sobel3Y;
            sobelWidth = 3;
        }

    }

    @Override
    public void initFilter() {
//        filterChain = new FilterChain(chip); //deadly; please delete
        atanHelper = new ATanHelper(resolutionATan, useATanTable);
//        lineSupport = new HashMap();
        lineSupportRegions = new HashSet();
        splitCandidates = new HashSet();
        
        resetFilter();
        
        try {
            setupLogging();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void setupLogging() throws IOException {
        if(loggingEnabled){
            logger.setEnabled(true);
            logger.setAbsoluteTimeEnabled(true);
            if(timestampIncludedInEachLog)
                {logger.setHeaderLine(" ts + ID + creation + cX + cY + mas + length + width + orientation + eX1 + eY1 + eX2 + eY2");}
            else{
                logger.setHeaderLine(" ID + creation + cX + cY + mas + length + width + orientation + eX1 + eY1 + eX2 + eY2");}
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        
        if (getEnclosedFilter() != null) {
            in = getEnclosedFilter().filterPacket(in);
        }
        if (getEnclosedFilterChain() != null) {
            in = getEnclosedFilterChain().filterPacket(in);
        }

        if(dynamicBuffer){
            int newBufferSize = bufferSizeEstimator.getBufferSizeEstimate(bufferSize);
            if (newBufferSize != getBufferSize())
                setBufferSize(newBufferSize);
        }
        
        for (BasicEvent e : in) {
            PolarityEvent ev = (PolarityEvent) e;
            if (e.isSpecial() || e.isFilteredOut() || e.x >= sx || e.y >= sy) {
                continue;
            }
            latestTS = ev.timestamp;
            addEvent(ev);
            if (blockEvents) {
                e.setFilteredOut(true);
            }
            if(eventCountDecay >= mapDecayEventCount){
                if(!useTimestampGradient){
                    decayMap(eventrateMap, getDecayFactor());}
                //decayMap(offEventrateMap[i], getDecayFactor());
                eventCountDecay = 0;
            }
            eventCountDecay++;
            
            if(loggingEnabled){
                if(eventCountLogging >= loggingEventCount){ // :)
                    if(timestampIncludedInEachLog)
                        writeRLog(logger, ev.timestamp);
                    else
                        writeLog(logger, ev.timestamp);
                    eventCountLogging = 0;
                }
                eventCountLogging++;
            }
        }
        if(splittingActive)checkSupport();

        drawPixels();
        //if(loggingEnabled)writeLog(in.getLastTimestamp());
        return in;
    }
    
    public void writeLog(TobiLogger logr, long ts){
        logr.addComment("*packetTS: "+ts);
        for (LineSupport ls : lineSupportRegions) {
            if (ls == null || !ls.isLineSegment()) {
                continue;
            }
             logSupport(logr, ls);
        }
    }
    
    public void logSupport(TobiLogger logr, LineSupport ls){
        ls.updateEndpoints();
        String msgR = ls.getId()+" "+ ls.getCreationTime() + " " +ls.getCenterX() + " " 
                + ls.getCenterY() + " " + ls.getMass() + " " + ls.getLength() + " " + ls.getWidth() + " "+ ls.getOrientation()
                +" "+ls.getEndpointX1()+" "+ls.getEndpointY1()+" "+ls.getEndpointX2()+" "+ls.getEndpointY2()+"\n";
        logr.log(msgR);       
    }
    
    // For reading out with R; R doesn't stomach well single lines with timestamps.
    public void writeRLog(TobiLogger logr, long ts){
        for (LineSupport ls : lineSupportRegions) {
            if (ls == null || !ls.isLineSegment()) {
                continue;
            }
            ls.updateEndpoints();
            String msgR = ts+" "+ls.getId()+" "+ ls.getCreationTime() + " " +ls.getCenterX() + " " 
                + ls.getCenterY() + " " + ls.getMass() + " " + ls.getLength() + " " + ls.getWidth() + " "+ ls.getOrientation()
                +" "+ls.getEndpointX1()+" "+ls.getEndpointY1()+" "+ls.getEndpointX2()+" "+ls.getEndpointY2()+"\n";
            logr.log(msgR); 
        }
    }
    
    
    public void stopLogging(TobiLogger logr) {
        logr.setEnabled(false);}
        
    private void checkSupport() {
        splitCandidates.clear();
        for (LineSupport ls : lineSupportRegions) {
            if (ls.getWidth() >= getWidthToSplit()) {
                splitCandidates.add(ls);
            }
        }
        // check for lines with big smaller main axis and split those, if splitting active
        //to avoid concurrent modification exceptions splitting is outsourced to a separate list
        for (LineSupport ls : splitCandidates) {
            split(ls);
        }
    }
    
    private void decayMap(float[] arr, float factor){
        for(int j = 0; j < arr.length; j++){
            arr[j] = arr[j]*factor;
        }
    }

    public <c extends PolarityEvent> boolean addEvent(c e) {
        if(useTimestampGradient){
            if (e.getType() == 0){offTimestampMap[mapIndex(e.x, e.y)] = e.timestamp;}
            else{onTimestampMap[mapIndex(e.x, e.y)] = e.timestamp;}}
        else{
             //if (e.getType() == 0) {   //off
            eventrateMap[mapIndex(e.x, e.y)] += (float)e.getPolaritySignum();}
            //} else {
            //        onEventrateMap[i][mapIndex(e.x, e.y)] += 1.0f;
            //}
        LevelLinePixel llp;
        llp = bufferEvent(e);
        assignSupportRegion(llp);
        return llp.isBuffered();
    }

    /*  Gradient calculation takes place here.
     Also deletes the oldest buffered pixel from segments and the pixelmap. Returns the new pixel.    
     This and the function below this use the timestampMap to calculate gradient 
     orientation; this is to make the maximum age of pixels used for gradient 
     calculation independent of the rate with which events come in.
    
     The oldest pixel is the one that came in indexbuffer.size events before.
    
     Current possible level line angles: )-180°,180°]
    */
    
    public LevelLinePixel bufferEvent(PolarityEvent e) {
        int radius = sobelWidth / 2;
        // removes all references to the oldest buffered pixel; llp is overwritten later on
        if(indexbuffer.isFull()){
            //int idx = (int) indexbuffer.get();
            int idx = (int) indexbuffer.take();
            removeEvent(idx);
        }
        int index = mapIndex(e.x, e.y);
        indexbuffer.add(index);
        LevelLinePixel llp = pixelmap[index];
        llp.addEvent(e);
        if (!isBoundaryPixel(sx, sy, e.x, e.y, radius)) {
            // calculate gradient and delete llp from its old support, if the angle doesn't fit anymore
            if(llp.assigned()){
//                LineSupport oldSup = llp.getSupport();
                removePixelFromSupport(llp);
                assignGradient(llp, e.getType(), e.timestamp, e.x, e.y);
//                if(!(oldSup==null || oldSup.getMass() <= 0) && llp.hasLevelLine()){
//                    float angleDiff = (llp.getAngle() - oldSup.getLLAngle());
//                    if(angleDiff < 0) 
//                        angleDiff = -angleDiff;
//                    if(angleDiff < getToleranceAngle() || angleDiff > (360.0f - getToleranceAngle())
//                       || ((!distinguishOpposingGradients) && (angleDiff % 180 < getToleranceAngle() 
//                       || angleDiff % 180 > 180 - getToleranceAngle()))){
//                         // do addPixelToSupport(llp,oldSup);
//                    }
//                    }
            } else{
                assignGradient(llp, e.getType(), e.timestamp, e.x, e.y);
            }
        }
        else {
            //stops here and returns pixel without orientation (llp is boundary pixel and can't have a support)
        }
        return llp;
    }

    /* Tests for neighbouring pixels and lines of similar orientation and merges them.
     Best segment: the oldest segment of those found.
     */
    public void assignSupportRegion(LevelLinePixel pixel) {

        int radius = sobelWidth / 2;
        if (!pixel.hasLevelLine()) {
            return;
        }
        if (isBoundaryPixel(sx, sy, pixel.getX(), pixel.getY(), radius)) {
            return;
        }
        LevelLinePixel[] candidates = new LevelLinePixel[(sobelWidth * sobelWidth) - 1];
        LineSupport bestFit = null;
        int nrCandidates = 0;

        // iterate through neighbourhood; make a list of all potential candidates and find best fit for a segment
        for (int v = pixel.getY() - radius; v < pixel.getY() + radius; v++) {
            for (int u = pixel.getX() - radius; u < pixel.getX() + radius; u++) {
                if (!isBoundaryPixel(sx, sy, u, v, radius)) {
                    LevelLinePixel neighbor = pixelmap[mapIndex(u, v)];
                    if (neighbor.hasLevelLine() && !(linesDistinguishOnAndOff && neighbor.getPolarity() != pixel.getPolarity())) {
                        float angleDiff;
                        if (neighbor.assigned()) {
                            angleDiff = (pixel.getAngle() - neighbor.getSupport().getLLAngle());
                        } else {
                            angleDiff = (pixel.getAngle() - neighbor.getAngle());
                        }
                        if (angleDiff < 0) {
                            angleDiff = -angleDiff;
                        }
                        if (neighbor != pixel && angleDiff < getToleranceAngle() || angleDiff > (360.0f - getToleranceAngle())
                                || ((!distinguishOpposingGradients) && (angleDiff % 180 < getToleranceAngle() || angleDiff % 180 > 180 - getToleranceAngle()))) {
                            candidates[nrCandidates] = neighbor;
                            nrCandidates++;
                            if(neighbor.assigned()){
                                if (bestFit == null) {
                                    bestFit = neighbor.getSupport();
                                } else if (bestFit.getCreationTime() > neighbor.getSupport().getCreationTime()) {
                                    bestFit = neighbor.getSupport();
                                }
                            }
                        }
                    }
                }
            }
        }
        if(nrCandidates == 0){
            //if there is not at least one candidate in the neighborhood of the pixel, no line segment should be created
            return;
        }
        if(bestFit == null){
            //if none of the neighboring pixels is the best, the pixel becomes the bestFit
            if(pixel.assigned()){
                bestFit = pixel.getSupport();
            }else{
                if(minNeighbors <= nrCandidates){
                    bestFit = newSupport(pixel);
                }else{
                    return;
                }
            }
        }else{
            //assign pixel to bestFit
            if(pixel.assigned()){
                LineSupport other = pixel.getSupport();
                float newDen = bestFit.getDensityCombined(pixel.getSupport());
                if(!(mergeOnlyIfLinesAligned && distanceSegmentToSegment(bestFit, pixel.getSupport()) > maxDistance)){
                    if (!addOnlyIfDensityHigh || Math.max(bestFit.getMass(), other.getMass()) < minSupportForDensityTest 
                            || newDen > minDensity || (orAddIfDensityIncreases && newDen > Math.max(bestFit.getDensity(), other.getDensity())) ){
                        merge(bestFit, pixel.getSupport());
                    }
                }
            }else{
                float newDen = bestFit.getDensityAdded(pixel);
                if((!addOnlyIfDensityHigh) || !(bestFit.getMass() >= minSupportForDensityTest) || 
                        newDen >= minDensity || (orAddIfDensityIncreases && newDen > bestFit.getDensity()) )
                    addPixelToSupport(pixel, bestFit);
            }
        }
        
        //iterate over all candidates and assign segment
        for (int i = 0; i < nrCandidates; i++) {
            if(bestFit != candidates[i].getSupport()){
                if (candidates[i].assigned()) {
                    //merge two support regions
                    LineSupport other = candidates[i].getSupport();
                    float newDen = bestFit.getDensityCombined(candidates[i].getSupport());
                    if(!(mergeOnlyIfLinesAligned && distanceSegmentToSegment(bestFit, candidates[i].getSupport()) > maxDistance)){
                        if (!addOnlyIfDensityHigh || Math.max(bestFit.getMass(), other.getMass()) < minSupportForDensityTest 
                            || newDen > minDensity || (orAddIfDensityIncreases && newDen > Math.max(bestFit.getDensity(), other.getDensity())) )
                            merge(bestFit, candidates[i].getSupport());
                    }
                    
                } else {
                    float newDen = bestFit.getDensityAdded(candidates[i]);
                    if((!addOnlyIfDensityHigh) || !(bestFit.getMass() >= minSupportForDensityTest) || 
                        newDen >= minDensity || (orAddIfDensityIncreases && newDen > bestFit.getDensity()))
                        addPixelToSupport(candidates[i], bestFit);
                }
            }
        }
    }
    
        /* Calculate and assign a gradient to llp.
            Changed: angle, angle90 and magnitude of llp. */ 
            //timestamp only used if timestamp gradient is in use
    public void assignGradient(LevelLinePixel llp, int eventPolarity, int timestamp, int x, int y){
        int radius = sobelWidth / 2;
        float sumAbsSobelXFieldsUsed = 0.01f;  //mustn't be zero, else if nothing is
        float sumAbsSobelYFieldsUsed = 0.01f;  //added later, divide by zero
        float gx = 0.0f;
        float gy = 0.0f;
        
        if(useTimestampGradient){
            int neighbourTime;
            int[] mapForGradientCalc;
            int deltaT;
            if (eventPolarity == 1){mapForGradientCalc = onTimestampMap;}
            else{mapForGradientCalc = offTimestampMap;}
            for (int h = 0; h < sobelWidth; h++) {
                for (int w = 0; w < sobelWidth; w++) {
                    neighbourTime = mapForGradientCalc[mapIndex((x - radius + w), (y - radius + h))];
                    if(neighbourTime == 0 || Math.abs(timestamp-neighbourTime) > getMaxAge()){
                        if(predictTimestamps){
                            neighbourTime = mapForGradientCalc[(x + radius - w) + sx * (y + radius - h)];
                            if (neighbourTime == 0 || Math.abs(timestamp-neighbourTime) > getMaxAge()){
                                deltaT = 0;
                            } else {
                                deltaT = timestamp - neighbourTime;
                                sumAbsSobelXFieldsUsed += Math.abs(filterX[w + h * sobelWidth]);
                                sumAbsSobelYFieldsUsed += Math.abs(filterY[w + h * sobelWidth]);
                            }
                        }else deltaT = 0;
                    } else {
                        deltaT = neighbourTime - timestamp;
                        sumAbsSobelXFieldsUsed += Math.abs(filterX[w + h * sobelWidth]);
                        sumAbsSobelYFieldsUsed += Math.abs(filterY[w + h * sobelWidth]);
                    }    
                    gx += deltaT * (float) filterX[w + h * sobelWidth];
                    gy += deltaT * (float) filterY[w + h * sobelWidth];
                }
            }
        }
        else{        
            if (sobelWidth==3){
                sumAbsSobelXFieldsUsed = 8.0f;
                sumAbsSobelYFieldsUsed = 8.0f;}
            else{
                sumAbsSobelXFieldsUsed = 96.0f;
                sumAbsSobelYFieldsUsed = 96.0f;}
            float neighbourRate;
            //if (eventPolarity == 1) {
            float[] mapForGradientCalc;
            mapForGradientCalc = eventrateMap;
    //        } else {
    //            rateMap = offEventrateMap[resIdx];
    //        }
            for (int h = 0; h < sobelWidth; h++) {
                for (int w = 0; w < sobelWidth; w++) {
                    neighbourRate = mapForGradientCalc[mapIndex((x - radius + w), (y - radius + h))];
                    //neighbourRate = rateMap[(x - radius + w) + sx * (y - radius + h)];
                    gx += neighbourRate * (float) filterX[w + h * sobelWidth];
                    gy += neighbourRate * (float) filterY[w + h * sobelWidth];
                }
            }
        }

        gx *= 1.0f / (float) sumAbsSobelXFieldsUsed;
        gy *= 1.0f / (float) sumAbsSobelYFieldsUsed;

        // calculate level line degree
        // (l. l. angle: degree between x-axis and the line orthogonal to the gradient)
        // gx/y -> vx/y can be left out, the v's aren't necessary
        /* Two possibilities exist to define the level line angle: between + 180° and  -180° 
         or between +- 90°. In the latter, gradients of opposite direction are treated as
         the same, which can be inconvenient. A situation where there are
         pixels of opposite gradients next to each other would be two lines crossing.
         When the +-90° version is used, they join, those lines. (I checked that.) So +- 180° will be used. */
        float vx = -gy;   //rotate couterclockwise by 90°
        float vy = gx;
        //float theta = (float) Math.toDegrees(Math.atan(vy/vx)); // atan2 would also take into account in which quadrant (x,y) lies, but it is too expensive.  
        float theta = atanHelper.atan2(vx, vy);
        float mag = Math.abs(gx)+Math.abs(gy);
        llp.setLevelLine(mag,theta);
        
    }
    
    
        //resIdx: resolution index
    public void merge(LineSupport supA, LineSupport supB){
        if(supA==supB || !lineSupportRegions.contains(supA) || !lineSupportRegions.contains(supA)){
            return;
        }
        if(supA.getCreationTime()<supB.getCreationTime()){
            supA.merge(supB);
            lineSupportRegions.remove(supB);
        }else{
            supB.merge(supA);
            lineSupportRegions.remove(supA);
        }
        
    }

    /*  Delete line, but try growing at each of the old support pixels. If a 
     line has fallen apart, this will find multiple smaller lines instead.*/
    public void split(LineSupport ls) {
        HashSet<LevelLinePixel> candidates = (HashSet<LevelLinePixel>) ls.getSupportPixels().clone();
        for (LevelLinePixel p : candidates) {
            removePixelFromSupport(p);
        }
        for (LevelLinePixel p : candidates) {
            assignSupportRegion(p);
        }
    }
    
    // called whenever a pixel is removed from the ringbuffer
    public void removeEvent(int i) {
        LevelLinePixel llp = pixelmap[i];
        llp.removeEvent();
        if(!llp.isBuffered() && llp.assigned())removePixelFromSupport(llp);
    }
        
    private LineSupport newSupport(LevelLinePixel pixel) {
        LineSupport newSupport = new LineSupport(lineSegmentID, this);
        lineSegmentID++;
        lineSupportRegions.add(newSupport);
        addPixelToSupport(pixel, newSupport);
        return newSupport;
    }
    
    private void addPixelToSupport(LevelLinePixel llp, LineSupport sup){
        if(!lineSupportRegions.contains(sup)) return;
        if(!llp.hasLevelLine()){
            System.out.println("cannot add pixel at  x: "+llp.getX()+", y: "+llp.getY()+" to support id: "+sup.getId());
        }
        if(llp.assigned()){
            //dereference before reassign
            LineSupport oldSup = llp.getSupport();
            oldSup.remove(llp);
        }
        sup.add(llp);
    }
    
    private void removePixelFromSupport(LevelLinePixel llp){
        LineSupport sup = llp.getSupport();
        if(!llp.assigned()){
            System.out.println("cannot remove pixel from Null support");
        }
        sup.remove(llp);
        if(sup.getSupportSize()==0){
            lineSupportRegions.remove(sup);
        }
    }
    
    // calculates the distance of the center of the segment with smaller support from the line through the other segment
    private float distanceSegmentToSegment(LineSupport first, LineSupport second) {
        LineSupport smaller, bigger;
        if (first.getMass() < second.getMass()) {
            smaller = second;
            bigger = first;
        } else {
            smaller = first;
            bigger = second;
        }
        float dy = (smaller.getCenterY() - bigger.getCenterY());
        float dx = smaller.getCenterX() - bigger.getCenterX();
        float alpha = bigger.getOrientation() - atanHelper.atan(dy / dx);
        return (float) Math.sin(alpha) * (Math.abs(dx) + Math.abs(dy));
    }

    private float distancePointToSegment(float x, float y, LineSupport ls) {
        float dy = (y - ls.getCenterY());
        float dx = x - ls.getCenterX();
        float alpha = ls.getOrientation() - atanHelper.atan(dy / dx);
        return (float) Math.sin(alpha) * (Math.abs(dx) + Math.abs(dy));
    }
    
    // field is ranging from coordinates (0,0) to coordinates (w-1,h-1) if I am not mistaken.
    public boolean isBoundaryPixel(int w, int h, int x, int y, int margin) {
        return !(x >= margin && y >= margin && x < w - margin && y < h - margin);
    }
    
    public int mapIndex(int x, int y) {
        if(x >= sx || y >= sy){
            return 0;
        } 
        return x + sx * y;
    }
    
    /** Resets the filter
     * @param yes true to reset */
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            clearOutputPacket();
        } else {
            renderer.setExternalRenderer(false);
            out = null; // garbage collect
        }
    }

    private GLCanvas glCanvas;
    private ChipCanvas canvas;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        drawLineSegments(drawable);
    }
    
    public void drawPixels(){
        switch(renderSource){
            case BUFFER:
                drawBuffer();
                break;
            case ARRAY: 
                drawArray();      
                break;
            case SUPPORT: 
                drawLineSupport();
                break;
            case SEGMENTS:
                drawLineSegmentSupport();
                break;
            case NONE:
                renderer.resetAnnotationFrame(0.0f);
                renderer.setExternalRenderer(false);
                break;
        }
    }

    public void drawLineSupport() {
        renderer.setExternalRenderer(true);
        renderer.resetAnnotationFrame(0.0f);
        float[] colors = new float[3];
        for (LineSupport ls : lineSupportRegions) {
            if (ls == null) {
                continue;
            }
            for (LevelLinePixel p : ls.getSupportPixels()) {
                switch (renderColor) {
                    case ORIENTATION:
                        if (p.hasLevelLine()) {
                            float hue = (float) (p.getAngle() + 180.0f) / 60.0f;
                            colors = ColorHelper.HSVtoRGB(hue, 1.0f, 1.0f);
                        } else {
                            float[] c = {0.2f, 0.2f, 0.2f};
                            colors = c;
                        }
                        break;
                    case ID:
                        colors = ls.getColor();   
                        break;
                }
                renderer.setAnnotateColorRGB(p.getX(), p.getY(), colors);
            }
        }
    }
    
    public void drawLineSegments(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel, at LL corner
        GLUT cGLUT = chip.getCanvas().getGlut();
        if (gl == null) {
            log.warning("null GL in ELiSeD.annotate");
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
        gl.glPointSize(5.0f);
        for (LineSupport ls : lineSupportRegions) {
            if (ls == null || !ls.isLineSegment()) {
                continue;
            }
            gl.glColor3f(0.0f, 0.0f, 0.0f);
            if (annotateLineSegments) {
                gl.glLineWidth(2.0f);
                gl.glColor3f(0.0f, 0.0f, 0.0f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(ls.getEndpointX1(), ls.getEndpointY1());
                gl.glVertex2f(ls.getEndpointX2(), ls.getEndpointY2());
                gl.glEnd();
                gl.glColor3f(ls.getColor()[0], ls.getColor()[1], ls.getColor()[2]);
                gl.glRasterPos3f(ls.getCenterX(), ls.getCenterY(), 0);
            }
            if (annotateAngles) {
                cGLUT.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_10, String.format("orientation:=%f", ls.getOrientation()));
            }
            if (annotateWidth) {
                gl.glColor3f(0.0f, 0.0f, 0.0f);
                gl.glRasterPos3f(ls.getCenterX(), ls.getCenterY(), 0);          // according to http://stackoverflow.com/questions/3818173/opengl-set-text-color , color changes are only effective if the position is set afterwards
                cGLUT.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_10, String.format("width:=%f", ls.getWidth()));
                gl.glColor3f(ls.getColor()[0], ls.getColor()[1], ls.getColor()[2]);
                gl.glRasterPos3f(ls.getCenterX(), ls.getCenterY(), 0);
            }
        }
    }
    
    public void drawLineSegmentSupport() {
        renderer.setExternalRenderer(true);
        renderer.resetAnnotationFrame(0.0f);
    float[] colors = new float[3];
        for (LineSupport ls : lineSupportRegions) {
            if (ls == null || !ls.isLineSegment()) {
                continue;
            }
            switch(renderColor){
                case ORIENTATION:
                    float hue = (float) (ls.getLLAngle() + 180.0f) / 60.0f;
                    colors = ColorHelper.HSVtoRGB(hue, 1.0f, 1.0f);                
                    break;
                case ID:
                    colors = ls.getColor();
                    break;
            }
            for (LevelLinePixel p : ls.getSupportPixels()) {
                if(!p.assigned()){
                    float[] c = new float[3];
                    if(!p.assigned()){
                        c[0] = colors[0] / 2.0f;
                        c[1] = colors[1] / 2.0f;
                        c[2] = colors[2] / 2.0f;
                    }
                    renderer.setAnnotateColorRGB(p.getX(), p.getY(), c);
                    //System.out.println("Strange");
                }else{
                    renderer.setAnnotateColorRGB(p.getX(), p.getY(), colors);
                }
            }
        }
    }

    public void drawArray() {
        renderer.setExternalRenderer(true);
        renderer.resetAnnotationFrame(0.0f);
        float[] colors = new float[3];
        LevelLinePixel llp;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                llp = pixelmap[mapIndex(x,y)];
                if (!llp.isBuffered()) {
                    float[] c = {0.4f, 0.4f, 0.4f};
                    colors = c;
                }
                switch(renderColor){
                    case ORIENTATION:
                        if(llp.hasLevelLine()){
                            float hue = (float) (llp.getAngle() + 180.0f) / 60.0f;
                            colors = ColorHelper.HSVtoRGB(hue, 1.0f, 1.0f);
                        }else if(llp.assigned()){
                            float[] c = {0.6f, 0.6f, 0.6f};
                            colors = c;
                        }else if(llp.isBuffered()){
                            float[] c = {0.3f, 0.3f, 0.3f};
                            colors = c;
                        }
                        break;
                    case ID:
                        if(llp.assigned()){
                            colors = llp.getSupport().getColor();
                        }else if(llp.isBuffered()){
                            float[] c = {0.4f, 0.4f, 0.4f};
                            colors = c;
                        }else{
                            float[] c = {0.2f, 0.2f, 0.2f};
                            colors = c;
                        }
                        break;
                }
                if(!llp.assigned()){
                    colors[0] = colors[0] / 2.0f;
                    colors[1] = colors[1] / 2.0f;
                    colors[2] = colors[2] / 2.0f;
                }
                renderer.setAnnotateColorRGB(x, y, colors);
            }
        }

    }
    
    public void drawBuffer() {
        renderer.setExternalRenderer(true);
        renderer.resetAnnotationFrame(0.0f);
        float[] colors = new float[3];
        LevelLinePixel llp;
        for(int i = 0; i<sx*sy; i++) {
            llp = pixelmap[i];
            if (!llp.isBuffered()) {
                continue;
            }
            switch(renderColor){
                case ORIENTATION:
                    if(llp.hasLevelLine()){
                        float hue = (float) (llp.getAngle() + 180.0f) / 60.0f;
                        colors = ColorHelper.HSVtoRGB(hue, 1.0f, 1.0f);
                    }else{
                        float[] c = {0.0f, 0.0f, 0.0f};
                        colors = c;     
                    }
                    break;
                case ID:
                    if(llp.assigned()){
                        colors = llp.getSupport().getColor();
                    }
                    break;
            }
            if(!llp.assigned()){
                colors[0] = colors[0] / 2.0f;
                colors[1] = colors[1] / 2.0f;
                colors[2] = colors[2] / 2.0f;
            }
            renderer.setAnnotateColorRGB(llp.getX(), llp.getY(), colors);
        }
    }

    /**
     * @return the minLineSupport
     */
    public int getMinLineSupport() {
        return minLineSupport;
    }

    /**
     * @param minLineSupport the minLineSupport to set
     */
    public void setMinLineSupport(int minLineSupport) {
        this.minLineSupport = minLineSupport;
        putInt("minLineSupport", minLineSupport);
    }

    /**
     * @return the toleranceAngle
     */
    public float getToleranceAngle() {
        return toleranceAngle;
    }

    /**
     * @param toleranceAngle the toleranceAngle to set
     */
    public void setToleranceAngle(float toleranceAngle) {
        this.toleranceAngle = toleranceAngle;
        putFloat("toleranceAngle", toleranceAngle);
    }


    /**
     * @return the annotateAngles
     */
    public boolean isAnnotateAngles() {
        return annotateAngles;
    }

    /**
     * @param annotateAngles the annotateAngles to set
     */
    public void setAnnotateAngles(boolean annotateAngles) {
        this.annotateAngles = annotateAngles;
        putBoolean("annotateAngles", annotateAngles);
    }

    /**
     * @return the distinguishOpposingGradients
     */
    public boolean isDistinguishOpposingGradients() {
        return distinguishOpposingGradients;
    }

    /**
     * @param distinguishOpposingGradients the distinguishOpposingGradients to
     * set
     */
    public void setDistinguishOpposingGradients(boolean distinguishOpposingGradients) {
        this.distinguishOpposingGradients = distinguishOpposingGradients;
        putBoolean("distinguishOpposingGradients", distinguishOpposingGradients);
    }

    /**
     * @return the linesDistinguishOnAndOff
     */
    public boolean isLinesDistinguishOnAndOff() {
        return linesDistinguishOnAndOff;
    }

    /**
     * @param linesDistinguishOnAndOff the linesDistinguishOnAndOff to set
     */
    public void setLinesDistinguishOnAndOff(boolean linesDistinguishOnAndOff) {
        this.linesDistinguishOnAndOff = linesDistinguishOnAndOff;
        putBoolean("linesDistinguishOnAndOff", linesDistinguishOnAndOff);
    }

    /**
     * @return the annotateWidth
     */
    public boolean isAnnotateWidth() {
        return annotateWidth;
    }

    /**
     * @param annotateWidth the annotateWidth to set
     */
    public void setAnnotateWidth(boolean annotateWidth) {
        this.annotateWidth = annotateWidth;
        putBoolean("annotateWidth", annotateWidth);
    }
    
        /**
     * @return the annotateAlpha
     */
    public float getAnnotateAlpha() {
        return annotateAlpha;
    }

    /**
     * @param annotateAlpha the annotateAlpha to set
     */
    public void setAnnotateAlpha(float annotateAlpha) {
        if(annotateAlpha > 1.0) annotateAlpha = 1.0f;
        if(annotateAlpha < 0.0) annotateAlpha = 0.0f;
        this.annotateAlpha = annotateAlpha;
        if(renderer instanceof AEFrameChipRenderer){
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            frameRenderer.setAnnotateAlpha(annotateAlpha);
        }
    }

    /**
     * @return the mergeOnlyIfLinesAligned
     */
    public boolean isMergeOnlyIfLinesAligned() {
        return mergeOnlyIfLinesAligned;
    }

    /**
     * @param mergeOnlyIfLinesAligned the mergeOnlyIfLinesAligned to set
     */
    public void setMergeOnlyIfLinesAligned(boolean mergeOnlyIfLinesAligned) {
        this.mergeOnlyIfLinesAligned = mergeOnlyIfLinesAligned;
        putBoolean("mergeOnlyIfLinesAligned", mergeOnlyIfLinesAligned);
    }

    /**
     * @return the maxDistance
     */
    public float getMaxDistance() {
        return maxDistance;
    }

    /**
     * @param maxDistance the maxDistance to set
     */
    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
        putFloat("maxDistance", maxDistance);
    }

    /**
     * @return the splittingActive
     */
    public boolean isSplittingActive() {
        return splittingActive;
    }

    /**
     * @param splittingActive the splittingActive to set
     */
    public void setSplittingActive(boolean splittingActive) {
        this.splittingActive = splittingActive;
        putBoolean("splittingActive", splittingActive);
    }
    
        /**
     * @return the addOnlyIfDensityHigh
     */
    public boolean isAddOnlyIfDensityHigh() {
        return addOnlyIfDensityHigh;
    }

    /**
     * @param addOnlyIfDensityHigh the addOnlyIfDensityHigh to set
     */
    public void setAddOnlyIfDensityHigh(boolean addOnlyIfDensityHigh) {
        this.addOnlyIfDensityHigh = addOnlyIfDensityHigh;
        putBoolean("addOnlyIfDensityHigh", addOnlyIfDensityHigh);
    }

        /**
     * @return the minDensity
     */
    public float getMinDensity() {
        return minDensity;
    }

    /**
     * @param minDensity the minDensity to set
     */
    public void setMinDensity(float minDensity) {
        this.minDensity = minDensity;
        putFloat("minDensity", minDensity);
    }

     /**
     * @return the minSupportForDensityTest
     */
    public int getMinSupportForDensityTest() {
        return minSupportForDensityTest;
    }

    /**
     * @param minSupportForDensityTest the minSupportForDensityTest to set
     */
    public void setMinSupportForDensityTest(int minSupportForDensityTest) {
        this.minSupportForDensityTest = minSupportForDensityTest;
        putInt("minSupportForDensityTest", minSupportForDensityTest);
    }

    
    /**
     * @return the currentBufferSize
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * @param currentBufferSize the currentBufferSize to set - set bufferSize
     * manually ->resize - buffer is resized -> bufferSize = newSize (in
     * resize(), not in resize(int); won't be called if buffer is set manually;
     * and no call to set, but putInt(...) is called)
     */
//    public synchronized void setBufferSize(int currentBufferSize) {
//        int old = this.bufferSize;
//        this.bufferSize = currentBufferSize;
//        putInt("bufferSize", currentBufferSize);
//        support.firePropertyChange("bufferSize", old, this.bufferSize);
//        if(bufferSize>currentBufferSize){
//            for(int i = 0; i < bufferSize-currentBufferSize; i++){
//                int idx = (int) indexbuffer.get();
//                removeEvent(idx);
//            }
//        }
//        indexbuffer = (TrailingRingBuffer)indexbuffer.resizeCopy(currentBufferSize);
//        resetFilter();
//    }
    
    public synchronized void setBufferSize(int currentBufferSize) {
        int old = this.bufferSize;
        this.bufferSize = currentBufferSize;
        putInt("bufferSize", currentBufferSize);
        support.firePropertyChange("bufferSize", old, this.bufferSize);
        
        indexbuffer.resize(currentBufferSize, removalFun);
    }

    
    

    /**
     * @return the widthToSplit
     */
    public float getWidthToSplit() {
        return widthToSplit;
    }

    /**
     * @param widthToSplit the widthToSplit to set
     */
    public void setWidthToSplit(float widthToSplit) {
        this.widthToSplit = widthToSplit;
        putFloat("widthToSplit", widthToSplit);
    }

    /**
     * @return the blockEvents
     */
    public boolean isBlockEvents() {
        return blockEvents;
    }

    /**
     * @param blockEvents the blockEvents to set
     */
    public void setBlockEvents(boolean blockEvents) {
        this.blockEvents = blockEvents;
        putBoolean("blockEvents", blockEvents);
    }

    /**
     * @return the annotateLineSegments
     */
    public boolean isAnnotateLineSegments() {
        return annotateLineSegments;
    }

    /**
     * @param annotateLineSegments the annotateLineSegments to set
     */
    public void setAnnotateLineSegments(boolean annotateLineSegments) {
        this.annotateLineSegments = annotateLineSegments;
        putBoolean("annotateLineSegments", annotateLineSegments);
    }
    
    
    /**
     * @return the renderColor
     */
    public RenderingColor getRenderColor() {
        return renderColor;
    }

    /**
     * @param renderColor the renderColor to set
     */
    public void setRenderColor(RenderingColor renderColor) {
        this.renderColor = renderColor;
        putString("renderColor", renderColor.name());
    }

    /**
     * @return the renderSource
     */
    public RenderingSource getRenderSource() {
        return renderSource;
    }

    /**
     * @param renderSource the renderSource to set
     */
    public void setRenderSource(RenderingSource renderSource) {
        this.renderSource = renderSource;
        putString("renderSource", renderSource.name());
        renderer.setExternalRenderer(true);
    }

    /**
     * @return the useWideSobelKernel
     */
    public boolean isUseWideSobelKernel() {
        return useWideSobelKernel;
    }

    /**
     * @param useWideSobelKernel the useWideSobelKernel to set
     */
    public void setUseWideSobelKernel(boolean useWideSobelKernel) {
        this.useWideSobelKernel = useWideSobelKernel;
        putBoolean("useWideSobelKernel", useWideSobelKernel);
    }

    /**
     * @return the dynamicResize
     */
    public boolean isDynamicBuffer() {
        return dynamicBuffer;
    }

    /**
     * @param dynamicBuffer the dynamicBuffer to set
     */
    public void setDynamicBuffer(boolean dynamicBuffer) {
        this.dynamicBuffer = dynamicBuffer;
        putBoolean("dynamicBuffer",dynamicBuffer);
    }

    /**
     * @return the loggingEnabled
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @param loggingEnabled the loggingEnabled to set
     */
    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
        if(loggingEnabled){
            try {
                setupLogging();
            } catch (IOException ex) {
                Logger.getLogger(ELiSeD.class.getName()).log(Level.SEVERE, null, ex);
            }
        }else{
            stopLogging(logger);
        }
        putBoolean("loggingEnabled",loggingEnabled);
    }
    
    /**
     * @return the minNeighbors
     */
    public int getMinNeighbors() {
        return minNeighbors;
    }

    /**
     * @param minNeighbors the minNeighbors to set
     */
    public void setMinNeighbors(int minNeighbors) {
        this.minNeighbors = minNeighbors;
        putInt("minNeighbors",minNeighbors);
    }

        /**
     * @return the decayFactor
     */
    public float getDecayFactor() {
        return decayFactor;
    }

    /**
     * @param decayFactor the decayFactor to set
     */
    public void setDecayFactor(float decayFactor) {
        this.decayFactor = decayFactor;
        putFloat("decayFactor",decayFactor);
    }
    
    /**
     * @param logLines the logLines to set
     */
    public synchronized void doLogNow() {
        TobiLogger logr = new TobiLogger("ELiSeD-Snapshot-Log", "Snapshot log of the support regions");
        logr.setEnabled(true);
        logr.setAbsoluteTimeEnabled(true);
        logr.setHeaderLine(" ID + creation + cX + cY + mas + length + width + orientation + eX1 + eY1 + eX2 + eY2");
        writeLog(logr, latestTS);
        stopLogging(logr);
        getSupport().firePropertyChange("logCurrentLines", null, null);
    }
    
    
    /**
     * @return the loggingEventCount
     */
    public int getLoggingEventCount() {
        return loggingEventCount;
    }

    /**
     * @param loggingEventCount the loggingEventCount to set
     */
    public void setLoggingEventCount(int loggingEventCount) {
        this.loggingEventCount = loggingEventCount;
        putInt("loggingEventCount",loggingEventCount);
    }
    
    
    /**
     * @return the timestampIncludedInEachLog
     */
    public boolean isTimestampIncludedInEachLog() {
        return timestampIncludedInEachLog;
    }

    /**
     * @param timestampIncludedInEachLog the timestampIncludedInEachLog to set
     */
    public void setTimestampIncludedInEachLog(boolean timestampIncludedInEachLog) {
        this.timestampIncludedInEachLog = timestampIncludedInEachLog;
        putBoolean("timestampIncludedInEachLog", timestampIncludedInEachLog);
        stopLogging(logger);
        try {
            setupLogging();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
     /**
     * @return the useTimestampGradient
     */
    public boolean isUseTimestampGradient() {
        return useTimestampGradient;
    }

    /**
     * @param useTimestampGradient the useTimestampGradient to set
     */
    public void setUseTimestampGradient(boolean useTimestampGradient) {
        this.useTimestampGradient = useTimestampGradient;
        putBoolean("useTimestampGradient", useTimestampGradient);
        resetFilter();
    }
    
    /**
     * @return the predictTimestamps
     */
    public boolean isPredictTimestamps() {
        return predictTimestamps;
    }

    /**
     * @param predictTimestamps the predictTimestamps to set
     */
    public void setPredictTimestamps(boolean predictTimestamps) {
        this.predictTimestamps = predictTimestamps;
        putBoolean("predictTimestamps", predictTimestamps);
    }
    
        /**
     * @return the orAddIfDensityIncreases
     */
    public boolean isOrAddIfDensityIncreases() {
        return orAddIfDensityIncreases;
    }

    /**
     * @param orAddIfDensityIncreases the orAddIfDensityIncreases to set
     */
    public void setOrAddIfDensityIncreases(boolean orAddIfDensityIncreases) {
        this.orAddIfDensityIncreases = orAddIfDensityIncreases;
        putBoolean("orAddIfDensityIncreases", orAddIfDensityIncreases);
    }
    
        /**
     * @return the maxAge
     */
    public int getMaxAge() {
        return maxAgeGradientCalculation;
    }

    /**
     * @param aMaxAge the maxAge to set
     */
    public void setMaxAge(int aMaxAge) {
        maxAgeGradientCalculation = aMaxAge;
        putInt("maxAge", aMaxAge);
    }
    
    
    
    
    
} 
