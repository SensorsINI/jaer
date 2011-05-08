/*
 * DirectionSelectiveFilter.java
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
import net.sf.jaer.util.filter.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.*;
import java.awt.geom.AffineTransform;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import net.sf.jaer.Description;

/**
 * Computes motion based nearest event (in past time) in neighboring pixels.
 *<p>
 *Output cells type has values 0-7,
 * 0 being upward motion, increasing by 45 deg CCW to 7 being motion up and to right.
 *
 *
 * @author tobi
 */
@Description("Local motion by time-of-travel of orientation events")
public class DirectionSelectiveFilter extends EventFilter2D implements Observer, FrameAnnotater {
    public boolean isGeneratingFilter(){ return true;}
    final int NUM_INPUT_TYPES=8; // 4 orientations * 2 polarities
    private int sizex,sizey; // chip sizes
    private boolean showGlobalEnabled=getPrefs().getBoolean("DirectionSelectiveFilter.showGlobalEnabled",false);
    {setPropertyTooltip("showGlobalEnabled","shows global tranlational, rotational, and expansive motion");}
    private boolean showVectorsEnabled=getPrefs().getBoolean("DirectionSelectiveFilter.showVectorsEnabled",false);
    {setPropertyTooltip("showVectorsEnabled","shows local motion vectors");}
    
    /** event must occur within this time in us to generate a motion event */
    private int maxDtThreshold=getPrefs().getInt("DirectionSelectiveFilter.maxDtThreshold",100000); // default 100ms
    {setPropertyTooltip("maxDtThreshold","max delta time (us) that is considered");}
    private int minDtThreshold=getPrefs().getInt("DirectionSelectiveFilter.minDtThreshold",100); // min 100us to filter noise or multiple spikes 
    {setPropertyTooltip("minDtThreshold","min delta time (us) for past events allowed for selecting a particular direction");}
    
    private int searchDistance=getPrefs().getInt("DirectionSelectiveFilter.searchDistance",1);
    {setPropertyTooltip("searchDistance","search distance perpindicular to orientation, 1 means search 1 to each side");}
    private float ppsScale=getPrefs().getFloat("DirectionSelectiveFilter.ppsScale",.05f);
    {setPropertyTooltip("ppsScale","scale of pixels per second to draw local and global motion vectors");}
    
//    private float maxSpeedPPS=prefs.getFloat("DirectionSelectiveFilter.maxSpeedPPS",100);
    
    private boolean speedControlEnabled=getPrefs().getBoolean("DirectionSelectiveFilter.speedControlEnabled", false);
    {setPropertyTooltip("speedControlEnabled","enables filtering of excess speeds");}
    private float speedMixingFactor=getPrefs().getFloat("DirectionSelectiveFilter.speedMixingFactor",.05f);
    {setPropertyTooltip("speedMixingFactor","speeds computed are mixed with old values with this factor");}
    private int excessSpeedRejectFactor=getPrefs().getInt("DirectionSelectiveFilter.excessSpeedRejectFactor",3);
    {setPropertyTooltip("excessSpeedRejectFactor","local speeds this factor higher than average are rejected as non-physical");}
    
    private boolean showRawInputEnabled=getPrefs().getBoolean("DirectionSelectiveFilter.showRawInputEnabled",false);
    {setPropertyTooltip("showRawInputEnabled","shows the input events, instead of the motion types");}
    
    private boolean useAvgDtEnabled=getPrefs().getBoolean("DirectionSelectiveFilter.useAvgDtEnabled",false);
    {setPropertyTooltip("useAvgDtEnabled","uses average delta time over search instead of minimum");}

    // taulow sets time const of lowpass filter, limiting max frequency
    private int tauLow=getPrefs().getInt("DirectionSelectiveFilter.tauLow",100);
    {setPropertyTooltip("tauLow","time constant in ms of lowpass filters for global motion signals");}

    private int subSampleShift=getPrefs().getInt("DirectionSelectiveFilter.subSampleShift",0);
    {setPropertyTooltip("subSampleShift","Shift subsampled timestamp map stores by this many bits");}
    
    
    private EventPacket oriPacket=null;
    
    int[][][] lastTimesMap; // map of input orientation event times, [x][y][type] where type is mixture of orienation and polarity
    
    /** the number of cell output types */
//    public final int NUM_TYPES=8;
    int PADDING=2; // padding around array that holds previous orientation event timestamps to prevent arrayoutofbounds errors and need for checking
    int P=1; // PADDING/2
    int lastNumInputCellTypes=2;
    SimpleOrientationFilter oriFilter;
    private MotionVectors motionVectors;
//    private LowpassFilter speedFilter=new LowpassFilter();
    float avgSpeed=0;
    
    /**
     * Creates a new instance of DirectionSelectiveFilter
     */
    public DirectionSelectiveFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
        setFilterEnabled(false);
        oriFilter=new SimpleOrientationFilter(chip);
        oriFilter.setAnnotationEnabled(false);
        setEnclosedFilter(oriFilter);
        motionVectors=new MotionVectors();
    }
    
    public Object getFilterState() {
        return lastTimesMap;
    }
    
    synchronized public void resetFilter() {
        setPadding(getSearchDistance()); // make sure to set padding
        sizex=chip.getSizeX();
        sizey=chip.getSizeY();
    }
    
    void checkMap(){
        if(lastTimesMap==null || lastTimesMap.length!=chip.getSizeX()+PADDING || lastTimesMap[0].length!=chip.getSizeY()+PADDING || lastTimesMap[0][0].length!=NUM_INPUT_TYPES){
            allocateMap();
        }
    }
    
    private void allocateMap() {
        if(!isFilterEnabled()) return;
//        log.info("DirectionSelectiveFilter: allocateMap");
        lastTimesMap=new int[chip.getSizeX()+PADDING][chip.getSizeY()+PADDING][NUM_INPUT_TYPES];
    }
    
    public void annotate(Graphics2D g) {
        if(!isAnnotationEnabled() || !isShowGlobalEnabled()) return;
//        float[] v=hist.getNormalized();
//        float south=chip.getMaxSize();
//        AffineTransform tstart=g.getTransform();
        AffineTransform tsaved=g.getTransform();
        g.translate(chip.getSizeX()/2, chip.getSizeY()/2);
//        g.setStroke(new BasicStroke(.3f));
//        g.setColor(Color.white);
//        for(int i=0;i<NUM_TYPES;i++){
//            //draw a star, ala the famous video game, from middle showing each dir component
//            float l=south*v[i];
//            int lx=-(int)Math.round(l*Math.sin(2*Math.PI*i/NUM_TYPES));
//            int ly=-(int)Math.round(l*Math.cos(2*Math.PI*i/NUM_TYPES));
//            g.drawLine(0,0,lx,ly);
//        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.white);
        Translation trans=motionVectors.translation;
//        g.drawLine(0,0,Math.round(p.x),Math.round(p.y));  // cast truncates, this rounds to nearest int
        g.setTransform(tsaved);
    }
    
    GLU glu=null;
    GLUquadric expansionQuad;
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled() ) return;
        GL gl=drawable.getGL();
        if(gl==null) return;
        
        if(isShowGlobalEnabled()){
            // draw global translation vector
            gl.glPushMatrix();
            gl.glColor3f(1,1,1);
            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0,0);
            Translation t=motionVectors.translation;
            int mult=chip.getMaxSize()/4;
            gl.glVertex2f(t.xFilter.getValue()*ppsScale*mult,t.yFilter.getValue()*ppsScale*mult);
            gl.glEnd();
            gl.glPopMatrix();
            
            // draw global rotation vector as line left/right
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY()*3)/4,0);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2i(0,0);
            Rotation r=motionVectors.rotation;
            int multr=chip.getMaxSize()*10;
            gl.glVertex2f(-r.filter.getValue()*multr*ppsScale,0);
            gl.glEnd();
            gl.glPopMatrix();
            
            // draw global expansion as circle with radius proportional to expansion metric, smaller for contraction, larger for expansion
            if(glu==null) glu=new GLU();
            if(expansionQuad==null) expansionQuad = glu.gluNewQuadric();
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())/2,0);
            gl.glLineWidth(6f);
            Expansion e=motionVectors.expansion;
            int multe=chip.getMaxSize()*4;
            glu.gluQuadricDrawStyle(expansionQuad,GLU.GLU_FILL);
            double rad=(1+e.filter.getValue())*ppsScale*multe;
            glu.gluDisk(expansionQuad,rad,rad+1,16,1);
            gl.glPopMatrix();
            
            
//            // draw expansion compass vectors as arrows pointing out from origin
//            gl.glPushMatrix();
//            gl.glTranslatef(chip.getSizeX()/2, (chip.getSizeY())/2,0);
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
            
            
        }
        
        if(isShowVectorsEnabled()){
            // draw individual motion vectors
            gl.glPushMatrix();
            gl.glColor3f(1,1,1);
            gl.glLineWidth(1f);
            gl.glBegin(GL.GL_LINES);
            int frameDuration=out.getDurationUs();
            for(Object o:out){
                MotionOrientationEvent e=(MotionOrientationEvent)o;
                drawMotionVector(gl,e,frameDuration);
            }
            gl.glEnd();
            gl.glPopMatrix();
        }
        
    }
    
//    final int MIN_DELAY=100;
//    final float SPEED_RATIO_ALLOWED=5;
    
    // plots a single motion vector which is the number of pixels per second times scaling
    void drawMotionVector(GL gl, MotionOrientationEvent e, int frameDuration){
        gl.glVertex2s(e.x,e.y);
        MotionOrientationEvent.Dir d=MotionOrientationEvent.unitDirs[e.direction];
        float speed=e.speed*ppsScale;
//        Point2D.Float vector=MotionOrientationEvent.computeMotionVector(e);
//        float xcomp=(float)(vector.getX()*ppsScale);
//        float ycomp=(float)(vector.getY()*ppsScale);
//        gl.glVertex2d(e.x+xcomp, e.y+ycomp);
        // motion vector points in direction of motion, *from* dir value (minus sign) which points in direction from prevous event
        gl.glVertex2f(e.x-d.x*speed,e.y-d.y*speed);
    }
    
    public void annotate(float[][][] frame) {
//        if(!isAnnotationEnabled()) return;
    }
    
//    /** overrides super to ensure that preceeding DirectionSelectiveFilter is also enabled */
//    @Override
//    synchronized public void setFilterEnabled(boolean yes){
//        super.setFilterEnabled(yes);
////        if(yes){
////            out=new EventPacket(MotionOrientationEvent.class);
////        }else{
////            out=null;
////        }
//    }
    
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled || in==null) return in;
        oriPacket=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(MotionOrientationEvent.class);
        checkMap();
        // filter
        lastNumInputCellTypes=in.getNumCellTypes();
        
        
        int n=oriPacket.getSize();
        if(n==0) return out;
        
        // if the input is ON/OFF type, then motion detection doesn't make much sense because you are likely to detect
        // the nearest event from along the same edge, not from where the edge moved from.
        // therefore, this filter only really makes sense to use with an oriented input.
        //
        // when the input is oriented (e.g. the events have an orientation type) then motion estimation consists
        // of just checking in a direction *perpindicular to the edge* for the nearest event of the same input orientation type.
        
        // for each event write out an event of type according to the direction of the most recent previous event in neighbors
        // only write the event if the delta time is within two-sided threshold
        
//        hist.reset();


        try{
//            long stime=System.nanoTime();
//            if(timeLimitEnabled) timeLimiter.start(getTimeLimitMs()); // ns from us by *1024
            OutputEventIterator outItr=out.outputIterator();
            for(Object ein:oriPacket){

                OrientationEvent e=(OrientationEvent)ein;
                int x=((e.x>>>subSampleShift)+P); // x and y are offset inside our timestamp storage array to avoid array access violations
                int y=((e.y>>>subSampleShift)+P);
                int polValue=((e.polarity==PolarityEvent.Polarity.On?1:2));
                byte type=(byte)(e.orientation*polValue); // type information here is mixture of input orientation and polarity, in order to match both characteristics
                int ts=e.timestamp;  // getString event x,y,type,timestamp of *this* event
                // update the map here - this is ok because we never refer to ourselves anyhow in computing motion
                lastTimesMap[x][y][type]=ts;
                
                // for each output cell type (which codes a direction of motion), find the dt
                // between the orientation cell type perdindicular
                // to this direction in this pixel and in the neighbor - but only find the dt in that single direction.
                
                // also, only find time to events of the same *polarity* and orientation. otherwise we will falsely match opposite polarity
                // orientation events which arise from two sides of edges
                
                // find the time of the most recent event in a neighbor of the same type as the present input event
                // but only in the two directions perpindiclar to this orientation. Each of these codes for motion but in opposite directions.
                
                // ori input has type 0 for horizontal (red), 1 for 45 deg (blue), 2 for vertical (cyan), 3 for 135 deg (green)
                // for each input type, check in the perpindicular directions, ie, (dir+2)%numInputCellTypes and (dir+4)%numInputCellTypes
                
                // this computation only makes sense for ori type input
                
                // neighbors are of same type
                // they are in direction given by unitDirs in lastTimesMap
                // the input type tells us which offset to use, e.g. for type 0 (0 deg horiz ori), we offset first in neg vert direction, then in positive vert direction
                // thus the unitDirs used here *depend* on orientation assignments in DirectionSelectiveFilter
                
                int dt1=0,dt2=0;
                int mindt1=Integer.MAX_VALUE, mindt2=Integer.MAX_VALUE;
                MotionOrientationEvent.Dir d;
                byte outType = e.orientation; // set potential output type to be same as type to start
                
                d=MotionOrientationEvent.unitDirs[e.orientation];
                
                int dist=1, dist1=1, dist2=1, dt=0, mindt=Integer.MAX_VALUE;
                
                if(!useAvgDtEnabled){
                    // now iterate over search distance to find minimum delay between this input orientation event and previous orientiation input events in
                    // offset direction
                    for(int s=1;s<=searchDistance;s++){
                        dt=ts-lastTimesMap[x+s*d.x][y+s*d.y][type]; // this is time between this event and previous
                        if(dt<mindt1){
                            dist1=s; // dist is distance we found min dt
                            mindt1=dt;
                        }
                    }
                    d=MotionOrientationEvent.unitDirs[e.orientation+4];
                    for(int s=1;s<=searchDistance;s++){
                        dt=ts-lastTimesMap[x+s*d.x][y+s*d.y][type];
                        if(dt<mindt2){
                            dist2=s; // dist is still the distance we have the global mindt
                            mindt2=dt;
                        }
                    }
                    if(mindt1<mindt2){ // if summed dt1 < summed dt2 the average delay in this direction is smaller
                        dt=mindt1;
                        outType=e.orientation;
                        dist=dist1;
                    }else{
                        dt=mindt2;
                        outType=(byte)(e.orientation+4);
                        dist=dist2;
                    }
                    // if the time between us and the most recent neighbor event lies within the interval, write an output event
                    if(dt<maxDtThreshold && dt>minDtThreshold){
                        float speed=1e6f*(float)dist/dt;
                        avgSpeed=(1-speedMixingFactor)*avgSpeed+speedMixingFactor*speed;
                        if(speedControlEnabled && speed>avgSpeed*excessSpeedRejectFactor) continue; // don't store event if speed too high compared to average
                        MotionOrientationEvent eout=(MotionOrientationEvent)outItr.nextOutput();
                        eout.copyFrom((OrientationEvent)ein);
                        eout.direction=outType;
                        eout.delay=(short)dt; // this is a actually the average dt for this direction
//                    eout.delay=(short)mindt; // this is the mindt found
                        eout.distance=(byte)dist;
                        eout.speed=speed;
                        eout.dir=MotionOrientationEvent.unitDirs[outType];
                        eout.velocity.x=-speed*eout.dir.x; // these have minus sign because dir vector points towards direction that previous event occurred
                        eout.velocity.y=-speed*eout.dir.y;
//                    avgSpeed=speedFilter.filter(MotionOrientationEvent.computeSpeedPPS(eout),eout.timestamp);
                        motionVectors.addEvent(eout);
//                    hist.add(outType);
                    }
                }else{
                    // use average time to previous ori events
                    // iterate over search distance to find average delay between this input orientation event and previous orientiation input events in
                    // offset direction. only count event if it falls in acceptable delay bounds
                    int n1=0, n2=0; // counts of passing matches, each direction
                    float speed1=0, speed2=0; // summed speeds
                    for(int s=1;s<=searchDistance;s++){
                        dt=ts-lastTimesMap[x+s*d.x][y+s*d.y][type]; // this is time between this event and previous
                        if(pass(dt)){
                            n1++;
                            speed1+=(float)s/dt; // sum speed in pixels/us
                        }
                    }
                    
                    d=MotionOrientationEvent.unitDirs[e.orientation+4];
                    for(int s=1;s<=searchDistance;s++){
                        dt=ts-lastTimesMap[x+s*d.x][y+s*d.y][type];
                        if(pass(dt)){
                            n2++;
                            speed2+=(float)s/dt;
                        }
                    }

                    if(n1==0 && n2==0) continue; // no pass
                    
                    float speed=0;
                    dist=searchDistance/2;
                    if(n1>n2){
                        speed=speed1/n1;
                        outType=e.orientation;
                    }else if(n2>n1){
                        speed=speed2/n2;
                        outType=(byte)(e.orientation+4);
                    }else{
                        if(speed1/n1<speed2/n2){
                            speed=speed1/n1;
                            outType=e.orientation;
                        }else{
                            speed=speed2/n2;
                            outType=(byte)(e.orientation+4);
                        }
                    }
//                    dt/= (searchDistance); // dt is normalized by search disance because we summed over the whole search distance
                    
                    // if the time between us and the most recent neighbor event lies within the interval, write an output event
                    if(n1>0 || n2>0){
                        speed=1e6f*speed;
                        avgSpeed=(1-speedMixingFactor)*avgSpeed+speedMixingFactor*speed;
                        if(speedControlEnabled && speed>avgSpeed*excessSpeedRejectFactor) continue; // don't output event if speed too high compared to average
                        MotionOrientationEvent eout=(MotionOrientationEvent)outItr.nextOutput();
                        eout.copyFrom((OrientationEvent)ein);
                        eout.direction=outType;
                        eout.delay=(short)(dist*speed); 
                        eout.distance=(byte)dist;
                        eout.speed=speed;
                        eout.dir=MotionOrientationEvent.unitDirs[outType];
                        eout.velocity.x=-speed*eout.dir.x; // these have minus sign because dir vector points towards direction that previous event occurred
                        eout.velocity.y=-speed*eout.dir.y;
                        motionVectors.addEvent(eout);
                    }
                }
            }
        }catch(ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
//            System.err.println("DirectionSelectiveFilter caught exception "+e+" probably caused by change of input cell type, reallocating lastTimesMap");
            checkMap();
        }
        
        if(isShowRawInputEnabled()) return in;
//        getMotionVectors().compute();
        return out;
    }
    
    private boolean pass(int dt){
        return (dt<maxDtThreshold && dt>minDtThreshold);
    }
    
    public int getMaxDtThreshold() {
        return this.maxDtThreshold;
    }
    
    public void setMaxDtThreshold(final int maxDtThreshold) {
        this.maxDtThreshold = maxDtThreshold;
        getPrefs().putInt("DirectionSelectiveFilter.maxDtThreshold",maxDtThreshold);
    }
    
    public int getMinDtThreshold() {
        return this.minDtThreshold;
    }
    
    public void setMinDtThreshold(final int minDtThreshold) {
        this.minDtThreshold = minDtThreshold;
        getPrefs().putInt("DirectionSelectiveFilter.minDtThreshold", minDtThreshold);
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    public int getSearchDistance() {
        return searchDistance;
    }
    
    private Point2D.Float translationVector=new Point2D.Float();
    
    /** Returns the 2-vector of global translational average motion 
     @return translational motion in pixels per second, as computed and filtered by Translation 
     */
    public Point2D.Float getTranslationVector(){
        translationVector.x=motionVectors.translation.xFilter.getValue();
        translationVector.y=motionVectors.translation.yFilter.getValue();
        return translationVector;
    }
    
    
    /** @return rotational motion of image around center of chip in rad/sec as computed from the global motion vector integration */
    public float getRotationRadPerSec(){
        float rot=motionVectors.rotation.filter.getValue();
        return rot;
    }
    
    public static final int MAX_SEARCH_DISTANCE=12;
    
    synchronized public void setSearchDistance(int searchDistance) {
        if(searchDistance>MAX_SEARCH_DISTANCE) searchDistance=MAX_SEARCH_DISTANCE; else if(searchDistance<1) searchDistance=1; // limit size
        this.searchDistance = searchDistance;
        setPadding(searchDistance);
        allocateMap();
        getPrefs().putInt("DirectionSelectiveFilter.searchDistance",searchDistance);
    }
    
//    public VectorHistogram getHist() {
//        return hist;
//    }
    
    /** The motion vectors are the global motion components */
    public MotionVectors getMotionVectors() {
        return motionVectors;
    }
    
    public boolean isSpeedControlEnabled() {
        return speedControlEnabled;
    }
    
    public void setSpeedControlEnabled(boolean speedControlEnabled) {
        this.speedControlEnabled = speedControlEnabled;
        getPrefs().putBoolean("DirectionSelectiveFilter.speedControlEnabled",speedControlEnabled);
    }
    
    
    public boolean isShowGlobalEnabled() {
        return showGlobalEnabled;
    }
    
    public void setShowGlobalEnabled(boolean showGlobalEnabled) {
        this.showGlobalEnabled = showGlobalEnabled;
        getPrefs().putBoolean("DirectionSelectiveFilter.showGlobalEnabled",showGlobalEnabled);
    }

    private void setPadding (int searchDistance){
        PADDING = 2 * searchDistance;
        P = ( PADDING / 2 );
    }
    
    
    /** global translatory motion, pixels per second */
    public class Translation{
        LowpassFilter xFilter=new LowpassFilter(), yFilter=new LowpassFilter();
        Translation(){
            xFilter.setTauMs(tauLow);
            yFilter.setTauMs(tauLow);
        }
        void addEvent(MotionOrientationEvent e){
            int t=e.timestamp;
            xFilter.filter(e.velocity.x,t);
            yFilter.filter(e.velocity.y,t);
        }
    }
    
    /** rotation around center, positive is CCW, radians per second 
     @see MotionVectors
     */
    public class Rotation{
        LowpassFilter filter=new LowpassFilter();
        Rotation(){
            filter.setTauMs(tauLow);
        }
        void addEvent(MotionOrientationEvent e){
            // each event implies a certain rotational motion. the larger the radius, the smaller the effect of a given local motion vector on rotation.
            // the contribution to rotational motion is computed by dot product between tangential vector (which is closely related to radial vector)
            // and local motion vector.
            // if vx,vy is the local motion vector, rx,ry the radial vector (from center of rotation), and tx,ty the tangential
            // *unit* vector, then the tagential velocity is comnputed as v.t=rx*tx+ry*ty.
            // the tangential vector is given by dual of radial vector: tx=-ry/r, ty=rx/r, where r is length of radial vector
            // thus tangential comtribution is given by v.t/r=(-vx*ry+vy*rx)/r^2.
            
            int rx=e.x-sizex/2, ry=e.y-sizey/2;
            if(rx==0 && ry==0) return; // don't add singular event at origin
//            float phi=(float)Math.atan2(ry,rx); // angle of event in rad relative to center, 0 is rightwards
//            float r=(float)Math.sqrt(rx*rx+ry*ry); // radius of event from center
            float r2=(float)(rx*rx+ry*ry); // radius of event from center
            float dphi=( -e.velocity.x*ry + e.velocity.y*rx )/r2;
            int t=e.timestamp;
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
        void addEvent(MotionOrientationEvent e){
            // each event implies a certain expansion contribution. Velocity components in the radial direction are weighted
            // by radius; events that are close to the origin contribute more to expansion metric than events that are near periphery.
            // the contribution to expansion is computed by dot product between radial vector
            // and local motion vector.
            // if vx,vy is the local motion vector, rx,ry the radial vector (from center of rotation)
            // then the radial velocity is comnputed as v.r/r.r=(vx*rx+vy*ry)/(rx*rx+ry*ry), where r is radial vector.
            // thus in scalar units, each motion event contributes v/r to the metric.
            // this metric is exactly 1/Tcoll with Tcoll=time to collision.
            
            int rx=e.x-sizex/2, ry=e.y-sizey/2;
            final int f=2; // singular region
//            if(rx==0 && ry==0) return; // don't add singular event at origin
            if((rx>-f && rx<f) && (ry>-f && ry<f)) return; // don't add singular event at origin
            float r2=(float)(rx*rx+ry*ry); // radius of event from center
            float dradial=( e.velocity.x*rx + e.velocity.y*ry )/r2;
            int t=e.timestamp;
            filter.filter(dradial,t);
            if(rx>0 && rx>ry && rx>-ry) east.filter(dradial,t);
            else if(ry>0 && ry>rx && ry>-rx) north.filter(dradial,t);
            else if(rx<0 && rx<ry && rx<-ry) west.filter(dradial,t);
            else south.filter(dradial,t);
        }
    }
    
    /** represents the global motion metrics from statistics of dir selective and simple cell events.
     The Translation is the global translational average motion vector (2 components). 
     Rotation is the global rotation scalar around the center of the
     sensor. Expansion is the expansion or contraction scalar around center.
     */
    public class MotionVectors{
        
        public Translation translation=new Translation();
        public Rotation rotation=new Rotation();
        public Expansion expansion=new Expansion();
        
        public void addEvent(MotionOrientationEvent e){
            translation.addEvent(e);
            rotation.addEvent(e);
            expansion.addEvent(e);
        }
    }
    
    public boolean isShowVectorsEnabled() {
        return showVectorsEnabled;
    }
    
    public void setShowVectorsEnabled(boolean showVectorsEnabled) {
        this.showVectorsEnabled = showVectorsEnabled;
        getPrefs().putBoolean("DirectionSelectiveFilter.showVectorsEnabled",showVectorsEnabled);
    }
    
    public float getPpsScale() {
        return ppsScale;
    }
    
    /** scale for drawn motion vectors, pixels per second per pixel */
    public void setPpsScale(float ppsScale) {
        this.ppsScale = ppsScale;
        getPrefs().putFloat("DirectionSelectiveFilter.ppsScale",ppsScale);
    }
    
    public float getSpeedMixingFactor() {
        return speedMixingFactor;
    }
    
    public void setSpeedMixingFactor(float speedMixingFactor) {
        if(speedMixingFactor>1)
            speedMixingFactor=1;
        else if(speedMixingFactor<Float.MIN_VALUE)
            speedMixingFactor=Float.MIN_VALUE;
        this.speedMixingFactor = speedMixingFactor;
        getPrefs().putFloat("DirectionSelectiveFilter.speedMixingFactor",speedMixingFactor);
        
    }
    
    public int getExcessSpeedRejectFactor() {
        return excessSpeedRejectFactor;
    }
    
    public void setExcessSpeedRejectFactor(int excessSpeedRejectFactor) {
        this.excessSpeedRejectFactor = excessSpeedRejectFactor;
        getPrefs().putInt("DirectionSelectiveFilter.excessSpeedRejectFactor",excessSpeedRejectFactor);
    }
    
    public int getTauLow() {
        return tauLow;
    }
    
    public void setTauLow(int tauLow) {
        this.tauLow = tauLow;
        getPrefs().putInt("DirectionSelectiveFilter.tauLow",tauLow);
        motionVectors.translation.xFilter.setTauMs(tauLow);
        motionVectors.translation.yFilter.setTauMs(tauLow);
        motionVectors.rotation.filter.setTauMs(tauLow);
        motionVectors.expansion.filter.setTauMs(tauLow);
    }
    
    public boolean isShowRawInputEnabled() {
        return showRawInputEnabled;
    }
    
    public void setShowRawInputEnabled(boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        getPrefs().putBoolean("DirectionSelectiveFilter.showRawInputEnabled",showRawInputEnabled);
    }
    
    public boolean isUseAvgDtEnabled() {
        return useAvgDtEnabled;
    }
    
    public void setUseAvgDtEnabled(boolean useAvgDtEnabled) {
        this.useAvgDtEnabled = useAvgDtEnabled;
        getPrefs().putBoolean("DirectionSelectiveFilter.useAvgDtEnabled",useAvgDtEnabled);
    }
    
    public int getSubSampleShift() {
        return subSampleShift;
    }
    
    /** Sets the number of spatial bits to subsample events times by. Setting this equal to 1, for example,
     subsamples into an event time map with halved spatial resolution, aggreating over more space at coarser resolution
     but increasing the search range by a factor of two at no additional cost
     @param subSampleShift the number of bits, 0 means no subsampling
     */
    synchronized public void setSubSampleShift(int subSampleShift) {
        if(subSampleShift<0) subSampleShift=0; else if(subSampleShift>4) subSampleShift=4;
        this.subSampleShift = subSampleShift;
        getPrefs().putInt("DirectionSelectiveFilter.subSampleShift",subSampleShift);
    }

   
    
}
