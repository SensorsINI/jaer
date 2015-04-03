/* AbstractDirectionSelectiveFilter.java
 *
 * Created on November 2, 2005, 8:24 PM */
package net.sf.jaer.eventprocessing.label;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.MotionOrientationEventInterface;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.filter.LowpassFilter;

/** Computes motion based nearest event (in past time) in neighboring pixels. <p>
 * Output cells type has values 0-7,
 * 0 being upward motion, increasing by 45 deg CCW to 7 being motion up and to right.
 * @author tobi */
// No need to have @Description and @DevelopmentStatus here, as the abstract
// class needs to be implemented, which is where the desc. and status. are used.
@Description("Abstract base class for local motion optical flow by time-of-travel of orientation events")
@DevelopmentStatus(DevelopmentStatus.Status.Abstract)
abstract public class AbstractDirectionSelectiveFilter extends EventFilter2D implements Observer, FrameAnnotater {
    protected static final int NUM_INPUT_TYPES = 8; // 4 orientations * 2 polarities
    protected static final int MAX_SEARCH_DISTANCE = 12;

    protected boolean showGlobalEnabled   = getBoolean("showGlobalEnabled",true);
    protected boolean showVectorsEnabled  = getBoolean("showVectorsEnabled",true);
    protected boolean showRawInputEnabled = getBoolean("showRawInputEnabled",true);
    protected boolean passAllEvents       = getBoolean("passAllEvents",false);

    /** event must occur within this time in us to generate a motion event */
    protected int maxDtThreshold = getInt("maxDtThreshold",100000); // default 100ms
    protected int minDtThreshold = getInt("minDtThreshold",100); // min 100us to filter noise or multiple spikes

    protected int searchDistance = getInt("searchDistance",3);
    protected int subSampleShift = getInt("subSampleShift",0);
    protected float ppsScale     = getFloat("ppsScale",.03f);
    protected boolean useAvgDtEnabled = getBoolean("useAvgDtEnabled",true);
    
    protected boolean jitterVectorLocations = getBoolean("jitterVectorLocations", true);
    protected float jitterAmountPixels      = getFloat("jitterAmountPixels",.5f);
    
    protected boolean speedControlEnabled = getBoolean("speedControlEnabled", true);
    protected float speedMixingFactor     = getFloat("speedMixingFactor",.001f);
    protected int excessSpeedRejectFactor = getInt("excessSpeedRejectFactor",2);

    /** taulow sets time constant of low-pass filter, limiting max frequency */
    protected int tauLow = getInt("tauLow",100);

    protected EventPacket oriPacket = null; // holds orientation events
    protected EventPacket dirPacket = null; // the output events, also used for rendering output events

    protected int[][][] lastTimesMap; // map of input orientation event times, [x][y][type] where type is mixture of orienation and polarity

    protected int sizex,sizey; // chip sizes

    protected AbstractOrientationFilter oriFilter;
    protected MotionVectors motionVectors;
    protected float avgSpeed = 0;

    boolean hasBlendChecked = false;
    boolean hasBlend = false;
    Random r = new Random();
    
    /** Creates a new instance of DirectionSelectiveFilter
     * @param chip */
    public AbstractDirectionSelectiveFilter(AEChip chip) {
        super(chip);

        resetFilter();

        motionVectors = new MotionVectors();

        chip.addObserver(this);
        final String disp="Display";
        setPropertyTooltip(disp,"ppsScale", "scale of pixels per second to draw local and global motion vectors");
        setPropertyTooltip(disp,"showVectorsEnabled", "shows local motion vectors");
        setPropertyTooltip(disp,"showGlobalEnabled", "shows global tranlational, rotational, and expansive motion");
        setPropertyTooltip(disp,"jitterAmountPixels", "how much to jitter vector origins by in pixels");
        setPropertyTooltip(disp,"jitterVectorLocations","whether to jitter vector location to see overlapping vectors more easily");
        setPropertyTooltip(disp,"showRawInputEnabled", "shows the input events, instead of the motion types");
        setPropertyTooltip(disp,"passAllEvents","Passes all events, even those that do not get labled with direction");
        setPropertyTooltip("subSampleShift", "Shift subsampled timestamp map stores by this many bits");
        setPropertyTooltip("tauLow", "time constant in ms of lowpass filters for global motion signals");
        setPropertyTooltip("useAvgDtEnabled", "uses average delta time over search instead of minimum");
        setPropertyTooltip("speedControlEnabled", "enables filtering of excess speeds");
        setPropertyTooltip("speedControl_ExcessSpeedRejectFactor", "local speeds this factor higher than average are rejected as non-physical");
        setPropertyTooltip("speedControl_speedMixingFactor", "speeds computed are mixed with old values with this factor");
        setPropertyTooltip("searchDistance", "search distance perpindicular to orientation, 1 means search 1 to each side");
        setPropertyTooltip("minDtThreshold", "min delta time (us) for past events allowed for selecting a particular direction");
        setPropertyTooltip("maxDtThreshold", "max delta time (us) that is considered");
    }

    // The Method that needs to be implemented.
    // This will be different for DVS and DAVIS, hence the abstract filter
    @Override public abstract EventPacket filterPacket(EventPacket in);

    @Override public synchronized final void resetFilter() {
        sizex=chip.getSizeX();
        sizey=chip.getSizeY();
    }

    protected void checkMap(){
        if((lastTimesMap==null) ||
           (lastTimesMap.length      !=(chip.getSizeX()+(2*getSearchDistance()))) ||
           (lastTimesMap[0].length   !=(chip.getSizeY()+(2*getSearchDistance()))) ||
           (lastTimesMap[0][0].length!=NUM_INPUT_TYPES)){
                allocateMap();
        }
    }

    protected void allocateMap() {
        if(!isFilterEnabled()) return;

        lastTimesMap=new int[chip.getSizeX()+(2*getSearchDistance())][chip.getSizeY()+(2*getSearchDistance())][NUM_INPUT_TYPES];
        log.info(String.format("allocated int[%d][%d][%d] array for last event times",chip.getSizeX(),chip.getSizeY(),NUM_INPUT_TYPES));
    }

    @Override public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) return;

        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) return;

        // The ChipSizes are needed several times
        int cX = chip.getSizeX(), cY = chip.getSizeY();
        
        // text annoations on clusters, setup
        final int font = GLUT.BITMAP_HELVETICA_18;
        
        checkBlend(gl);

        if (isShowGlobalEnabled()) {
            gl.glLineWidth(4f);
            gl.glColor3f(1, 1, 1);
            
            // <editor-fold defaultstate="collapsed" desc="-- draw global translation vector --">
            Translation t=motionVectors.translation;
            
            gl.glPushMatrix();
                DrawGL.drawVector(gl, cX/2, cY/2, t.xFilter.getValue(), t.yFilter.getValue(), 4, ppsScale*(chip.getMaxSize()/4));
                
                gl.glRasterPos2i(2,10);
                float vel = (float) Math.sqrt(Math.pow(t.xFilter.getValue(), 2)+Math.pow(t.yFilter.getValue(), 2));
                chip.getCanvas().getGlut().glutBitmapString(font, String.format("glob. speed=%.2f ", vel));
            gl.glPopMatrix();
            // </editor-fold>

            // <editor-fold defaultstate="collapsed" desc="-- draw global rotation vector as line left/right --">
            Rotation rot=motionVectors.rotation;
                
            gl.glPushMatrix();
                DrawGL.drawLine(gl, cX/2, (cY*3)/4, -rot.filter.getValue(), 0, ppsScale*(chip.getMaxSize()*10));
            gl.glPopMatrix();
            // </editor-fold>

            // <editor-fold defaultstate="collapsed" desc="-- draw global expansion as circle with radius proportional to expansion metric, smaller for contraction, larger for expansion --"> 
            gl.glPushMatrix();
                Expansion expan = motionVectors.expansion;
                float rad = (1+expan.filter.getValue())*ppsScale*(chip.getMaxSize()*4);
                
                DrawGL.drawCircle(gl, cX/2, cY/2, rad, 15);
            gl.glPopMatrix();
            // </editor-fold>

            // <editor-fold defaultstate="collapsed" desc="-- draw expansion compass vectors as arrows pointing in.getOutputPacket() from origin --">
//            gl.glPushMatrix();
//            gl.glTranslatef(cX/2, cY/2,0);
//            gl.glLineWidth(6f);
//            gl.glBegin(GL.GL_LINES);
//            gl.glVertex2i(0,0);
//            gl.glVertex2f(0, (1+e.north.getValue())*multe*ppsScale);
//            gl.glVertex2i(0,0);
//            gl.glVertex2f(0, (-1-e.south.getValue())*multe*ppsScale);
//            gl.glVertex2i(0,0);
//            gl.glVertex2f((-1-e.west.getValue())*multe*ppsScale,0);
//            gl.glVertex2i(0,0);
//            gl.glVertex2f((1+e.east.getValue())*multe*ppsScale,0);
//            gl.glEnd();
//            gl.glPopMatrix();
            // </editor-fold>
        }

        if((dirPacket!=null) && isShowVectorsEnabled()){
            // <editor-fold defaultstate="collapsed" desc="-- draw individual motion vectors --">
            float[][] c;
            gl.glLineWidth(2f);
            for(Object o : dirPacket){
                MotionOrientationEventInterface e=(MotionOrientationEventInterface)o;
                c=chip.getRenderer().makeTypeColors(e.getNumCellTypes());
                if(e.isHasDirection()) drawMotionVector(gl,e,c); //If we passAllEvents then the check is needed to not annotate the events without a real direction
            }
            // </editor-fold>
        }
    }


    /** plots a single motion vector which is the number of pixels per second times scaling
     * 
     * @param gl the OpenGL context
     * @param e the event
     * @param c a 2D array of colors vectors; the first dimension is the event type, and the second is a 3-vector of RGB colors
     */
    protected void drawMotionVector(GL2 gl, MotionOrientationEventInterface e,float[][] c) {
        float jx = 0, jy = 0;
        MotionOrientationEventInterface.Dir d = MotionOrientationEventInterface.unitDirs[e.getDirection()];

        if (jitterVectorLocations) {
            jx = (r.nextFloat() - .5f) * jitterAmountPixels;
            jy = (r.nextFloat() - .5f) * jitterAmountPixels;
        }
        // motion vector points in direction of motion, *from* dir value (minus sign) which points in direction from prevous event
        gl.glPushMatrix();
            gl.glColor3fv(c[e.getType()],0);
            DrawGL.drawVector(gl, e.getX()+jx, e.getY()+jy, -d.x, -d.y, 1, e.getSpeed() * ppsScale);
        gl.glPopMatrix();
        
    }

    @Override public void initFilter() {
        resetFilter();
    }

    @Override public void update(Observable o, Object arg) {
        initFilter();
    }

    /** Returns the 2-vector of global translational average motion.
     * @return translational motion in pixels per second,
     * as computed and filtered by Translation */
    public Point2D.Float getTranslationVector(){
        Point2D.Float translationVector=new Point2D.Float();
        translationVector.x=motionVectors.translation.xFilter.getValue();
        translationVector.y=motionVectors.translation.yFilter.getValue();
        return translationVector;
    }

    /** @return rotational motion of image around center of chip in rad/sec
     * as computed from the global motion vector integration */
    public float getRotationRadPerSec(){
        float rot=motionVectors.rotation.filter.getValue();
        return rot;
    }

    /** The motion vectors are the global motion components
     * @return  */
    public MotionVectors getMotionVectors() {
        return motionVectors;
    }

    /** global translatory motion, pixels per second */
    public class Translation{
        LowpassFilter xFilter=new LowpassFilter(), yFilter=new LowpassFilter();
        Translation(){
            xFilter.setTauMs(tauLow);
            yFilter.setTauMs(tauLow);
        }
        void addEvent(MotionOrientationEventInterface e){
            int t=e.getTimestamp();
            xFilter.filter(e.getVelocity().x,t);
            yFilter.filter(e.getVelocity().y,t);
        }
    }

    /** rotation around center, positive is CCW, radians per second
     * @see MotionVectors */
    public class Rotation{
        LowpassFilter filter=new LowpassFilter();
        Rotation(){
            filter.setTauMs(tauLow);
        }
        void addEvent(MotionOrientationEventInterface e){
            // each event implies a certain rotational motion. The larger the
            // radius, the smaller the effect of a given local motion vector on
            // rotation. The contribution to rotational motion is computed by
            // dot product between tangential vector (which is closely related
            // to radial vector) and local motion vector.
            // If (vx,vy) is the local motion vector, (rx,ry) the radial vector
            // (from center of rotation), and (tx,ty) the tangential *unit*
            // vector, then the tagential velocity is comnputed as v.t=rx*tx+ry*ty.
            // the tangential vector is given by dual of radial vector:
            // tx=-ry/r, ty=rx/r, where r is length of radial vector
            // thus tangential comtribution is given by v.t/r=(-vx*ry+vy*rx)/r^2.

            int rx=e.getX()-(sizex/2), ry=e.getY()-(sizey/2);
            if((rx==0) && (ry==0)) return; // don't add singular event at origin

            float r2=(rx*rx)+(ry*ry); // radius of event from center
            float dphi=( (-e.getVelocity().x*ry) + (e.getVelocity().y*rx) )/r2;
            int t=e.getTimestamp();
            filter.filter(dphi,t);
        }
    }

    /** @see MotionVectors */
    public class Expansion{
        // global expansion
        LowpassFilter filter=new LowpassFilter();
        // compass quadrants
        LowpassFilter north=new LowpassFilter(),south=new LowpassFilter(),east=new LowpassFilter(),west=new LowpassFilter();
        Expansion(){
            filter.setTauMs(tauLow);
        }
        void addEvent(MotionOrientationEventInterface e){
            // each event implies a certain expansion contribution.
            // Velocity components in the radial direction are weighted by radius;
            // events that are close to the origin contribute more to expansion
            // metric than events that are near periphery. The contribution to
            // expansion is computed by dot product between radial vector
            // and local motion vector.
            // if vx,vy is the local motion vector, rx,ry the radial vector
            // (from center of rotation) then the radial velocity is comnputed
            // as v.r/r.r=(vx*rx+vy*ry)/(rx*rx+ry*ry), where r is radial vector.
            // thus in scalar units, each motion event contributes v/r to the metric.
            // this metric is exactly 1/Tcoll with Tcoll=time to collision.

            int rx=e.getX()-(sizex/2), ry=e.getY()-(sizey/2);
            final int f=2; // singular region
            if(((rx>-f) && (rx<f)) && ((ry>-f) && (ry<f))) return; // don't add singular event at origin

            float r2=(rx*rx)+(ry*ry); // radius of event from center
            float dradial=( (e.getVelocity().x*rx) + (e.getVelocity().y*ry) )/r2;
            int t=e.getTimestamp();
            filter.filter(dradial,t);
            if((rx>0) && (rx>ry) && (rx>-ry)) {
                east.filter(dradial,t);
            } else if((ry>0) && (ry>rx) && (ry>-rx)) {
                north.filter(dradial,t);
            } else if((rx<0) && (rx<ry) && (rx<-ry)) {
                west.filter(dradial,t);
            } else {
                south.filter(dradial,t);
            }
        }
    }

    /** represents the global motion metrics from statistics of dir selective and simple cell events.
     * The Translation is the global translational average motion vector (2 components).
     * Rotation is the global rotation scalar around the center of the sensor.
     * Expansion is the expansion or contraction scalar around center. */
    public class MotionVectors{

        public Translation translation=new Translation();
        public Rotation rotation=new Rotation();
        public Expansion expansion=new Expansion();

        public void addEvent(MotionOrientationEventInterface e){
            translation.addEvent(e);
            rotation.addEvent(e);
            expansion.addEvent(e);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SearchDistance--">
    public int getSearchDistance() {
        return searchDistance;
    }

    synchronized public void setSearchDistance(int searchDistance) {
        if(searchDistance > MAX_SEARCH_DISTANCE) {
            searchDistance = MAX_SEARCH_DISTANCE;
        } else if(searchDistance<1) {
            searchDistance = 1;
        } // limit size
        this.searchDistance = searchDistance;
        allocateMap();
        putInt("searchDistance",searchDistance);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowGlobalEnabled--">
    public boolean isShowGlobalEnabled() {
        return showGlobalEnabled;
    }

    public void setShowGlobalEnabled(boolean showGlobalEnabled) {
        this.showGlobalEnabled = showGlobalEnabled;
        putBoolean("showGlobalEnabled",showGlobalEnabled);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --passAllEvents--">
    public boolean isPassAllEvents() {
        return passAllEvents;
    }

    public void setPassAllEvents(boolean passAllEvents) {
        this.passAllEvents = passAllEvents;
        putBoolean("passAllEvents",passAllEvents);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MaxDtTreshold--">
    public int getMaxDtThreshold() {
        return this.maxDtThreshold;
    }

    public void setMaxDtThreshold(final int maxDtThreshold) {
        this.maxDtThreshold = maxDtThreshold;
        putInt("maxDtThreshold",maxDtThreshold);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MinDtThreshold--">
    public int getMinDtThreshold() {
        return this.minDtThreshold;
    }

    public void setMinDtThreshold(final int minDtThreshold) {
        this.minDtThreshold = minDtThreshold;
        putInt("minDtThreshold", minDtThreshold);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowVectorsEnabled--">
    public boolean isShowVectorsEnabled() {
        return showVectorsEnabled;
    }

    public void setShowVectorsEnabled(boolean showVectorsEnabled) {
        this.showVectorsEnabled = showVectorsEnabled;
        putBoolean("showVectorsEnabled",showVectorsEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PpsScale--">
    public float getPpsScale() {
        return ppsScale;
    }

    /** scale for drawn motion vectors, pixels per second per pixel */
    public void setPpsScale(float ppsScale) {
        this.ppsScale = ppsScale;
        putFloat("ppsScale",ppsScale);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TauLow--">
    public int getTauLow() {
        return tauLow;
    }

    public void setTauLow(int tauLow) {
        motionVectors.translation.xFilter.setTauMs(tauLow);
        motionVectors.translation.yFilter.setTauMs(tauLow);
        motionVectors.rotation.filter.setTauMs(tauLow);
        motionVectors.expansion.filter.setTauMs(tauLow);
        this.tauLow = tauLow;
        putInt("tauLow",tauLow);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowRawInputEnable--">
    public boolean isShowRawInputEnabled() {
        return showRawInputEnabled;
    }

    public void setShowRawInputEnabled(boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        putBoolean("showRawInputEnabled",showRawInputEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseAvgDtEnabled--">
    public boolean isUseAvgDtEnabled() {
        return useAvgDtEnabled;
    }

    public void setUseAvgDtEnabled(boolean useAvgDtEnabled) {
        this.useAvgDtEnabled = useAvgDtEnabled;
        putBoolean("useAvgDtEnabled",useAvgDtEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SubSampleShift--">
    public int getSubSampleShift() {
        return subSampleShift;
    }

    /** Sets the number of spatial bits to subsample events times by.
     * Setting this equal to 1, for example, subsamples into an event time map
     * with halved spatial resolution, aggregating over more space at coarser
     * resolution but increasing the search range by a factor of two at no additional cost
     * @param subSampleShift the number of bits, 0 means no subsampling */
    synchronized public void setSubSampleShift(int subSampleShift) {
        if(subSampleShift < 0) {
            subSampleShift = 0;
        } else if(subSampleShift > 4) {
            subSampleShift = 4;
        }
        this.subSampleShift = subSampleShift;
        putInt("subSampleShift",subSampleShift);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterVectorLocations--">
    /**
     * @return the jitterVectorLocations */
    public boolean isJitterVectorLocations() {
        return jitterVectorLocations;
    }

    /**
     * @param jitterVectorLocations the jitterVectorLocations to set */
    public void setJitterVectorLocations(boolean jitterVectorLocations) {
        this.jitterVectorLocations = jitterVectorLocations;
        putBoolean("jitterVectorLocations", jitterVectorLocations);
        getChip().getAeViewer().interruptViewloop();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmountPixels--">
    /**
     * @return the jitterAmountPixels */
    public float getJitterAmountPixels() {
        return jitterAmountPixels;
    }

    /**
     * @param jitterAmountPixels the jitterAmountPixels to set */
    public void setJitterAmountPixels(float jitterAmountPixels) {
        this.jitterAmountPixels = jitterAmountPixels;
        putFloat("jitterAmountPixels",jitterAmountPixels);
        getChip().getAeViewer().interruptViewloop();
    }
    // </editor-fold>
    
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SpeedControlEnabled--">
    public boolean isSpeedControlEnabled() {
        return speedControlEnabled;
    }

    public void setSpeedControlEnabled(boolean speedControlEnabled) {
        this.speedControlEnabled = speedControlEnabled;
        putBoolean("speedControlEnabled",speedControlEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SpeedControl_SpeedMixingFactor--">
    public float getSpeedControl_SpeedMixingFactor() {
        return speedMixingFactor;
    }

    public void setSpeedControl_SpeedMixingFactor(float speedMixingFactor) {
        if(speedMixingFactor > 1) {
            speedMixingFactor=1;
        } else if(speedMixingFactor < Float.MIN_VALUE) {
            speedMixingFactor = Float.MIN_VALUE;
        }
        this.speedMixingFactor = speedMixingFactor;
        putFloat("speedMixingFactor",speedMixingFactor);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SpeedControl_ExcessSpeedRejectFactor--">
    public int getSpeedControl_ExcessSpeedRejectFactor() {
        return excessSpeedRejectFactor;
    }

    public void setSpeedControl_ExcessSpeedRejectFactor(int excessSpeedRejectFactor) {
        this.excessSpeedRejectFactor = excessSpeedRejectFactor;
        putInt("excessSpeedRejectFactor",excessSpeedRejectFactor);
    }
    // </editor-fold>
}
