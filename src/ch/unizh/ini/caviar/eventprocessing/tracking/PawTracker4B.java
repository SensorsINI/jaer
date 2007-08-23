/*
 * PawTracker4B.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * ( similar approach ass PawTracker '2' but without ime binning)
 *
 * This class finds the contour of the paw in the accumulation of incoming events
 * After low-pass filter
 * Then detection of fingers tips by template matching
 * + alternative algorithms trials
 *
 * work under (a lot of) progress
 *
 * Paul Rogister, Created on June, 2007
 *
 */


/** to do : */


package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.media.opengl.glu.GLU;

import ch.unizh.ini.caviar.eventprocessing.tracking.PawTracker3DStatic2;

/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class PawTracker4B extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    //private static Preferences getPrefs()=Preferences.userNodeForPackage(PawTracker4B.class);
    
    
    // Global constant values
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    /** the number of classes of objects */
    private final int NUM_CLASSES=2;
    // max number of orientations
    private final int MAX_SEGMENTS=10000;
    //private final int MAX_DISTGC=300;//depends
    //private final int MAX_SEQ_LENGTH=50;
    //private final int MAX_NB_SEQ=310;
    
    private final int MAX_NB_FINGERS=4;
    
    private double maxOrientation = 180;
    private double maxDistGC = 200;
    
    
    
    // Parameters appearing in the GUI
    
    
    private float planeAngle=getPrefs().getFloat("PawTracker4B.planeAngle",30.0f);
    private float alpha=getPrefs().getFloat("PawTracker4B.alpha",0.1f);
    private float intensity=getPrefs().getFloat("PawTracker4B.intensity",1);
    
    private int ray_length=getPrefs().getInt("PawTracker4B.ray_length",50);
    private int adjustTime=getPrefs().getInt("PawTracker4B.adjustTime",0);
    
    private int yLeftCorrection=getPrefs().getInt("PawTracker4B.yLeftCorrection",0);
    private int yRightCorrection=getPrefs().getInt("PawTracker4B.yRightCorrection",0);
    
    private float yCurveFactor=getPrefs().getFloat("PawTracker4B.yCurveFactor",0.1f);
    
    private float valueThreshold=getPrefs().getFloat("PawTracker4B.valueThreshold",0);
    private float stereoThreshold=getPrefs().getFloat("PawTracker4B.stereoThreshold",0.5f);
    
    
    private float shadowFactor=getPrefs().getFloat("PawTracker4B.shadowFactor",0.3f);
    private float colorizeFactor=getPrefs().getFloat("PawTracker4B.colorizeFactor",0.1f);
    private int colorizePeriod=getPrefs().getInt("PawTracker4B.colorizePeriod",183);
    
    
    private int zFactor=getPrefs().getInt("PawTracker4B.zFactor",1);
    private float valueMargin=getPrefs().getFloat("PawTracker4B.valueMargin",0.3f);
    
    
    
    private int obj_xa=getPrefs().getInt("PawTracker4B.obj_xa",15);
    private int obj_ya=getPrefs().getInt("PawTracker4B.obj_ya",15);
    private int obj_sizea=getPrefs().getInt("PawTracker4B.obj_sizea",10);
    private int obj_xb=getPrefs().getInt("PawTracker4B.obj_xb",15);
    private int obj_yb=getPrefs().getInt("PawTracker4B.obj_yb",15);
    private int obj_sizeb=getPrefs().getInt("PawTracker4B.obj_sizeb",10);
    
    private int cube_size=getPrefs().getInt("PawTracker4B.cube_size",1);
    
     private int door_z=getPrefs().getInt("PawTracker4B.door_z",50);
    {setPropertyTooltip("door_z","estimated z of the cage door");}
    
    private int door_xa=getPrefs().getInt("PawTracker4B.door_xa",52);
    {setPropertyTooltip("door_xa","lower x bound of the cage door");}
    private int door_xb=getPrefs().getInt("PawTracker4B.door_xb",88);
    {setPropertyTooltip("door_xb","higher x bound of the cage door");}
    private int door_ya=getPrefs().getInt("PawTracker4B.door_ya",50);
    {setPropertyTooltip("door_ya","lower y bound of the cage door");}
    private int door_yb=getPrefs().getInt("PawTracker4B.door_yb",127);
    {setPropertyTooltip("door_yb","higher y bound of the cage door");}
    
    private int retinaSize=getPrefs().getInt("PawTracker4B.retinaSize",128);
    {setPropertyTooltip("retinaSize","[nb pixels of side] resolution of the retina");}
    
    
    private int minZeroes=getPrefs().getInt("PawTracker4B.minZeroes",2);
    {setPropertyTooltip("minZeroes","Minimum number of low value around point for border template matching");}
    private int maxZeroes=getPrefs().getInt("PawTracker4B.maxZeroes",6);
    {setPropertyTooltip("maxZeroes","Maximum number of low value around point for border template matching");}
    // private int doorMinZeroes=getPrefs().getInt("PawTracker4B.doorMinZeroes",2);
    // private int doorMaxZeroes=getPrefs().getInt("PawTracker4B.doorMaxZeroes",6);
    
    
    private float line_threshold=getPrefs().getFloat("PawTracker4B.line_threshold",0f);
    {setPropertyTooltip("line_threshold","unused, planned for finding parallel lines");}
    private int score_threshold=getPrefs().getInt("PawTracker4B.score_threshold",37);
    {setPropertyTooltip("score_threshold","[intensity 0-100] threshold on intensity score for fingertips detection");}
    private float score_in_threshold=getPrefs().getFloat("PawTracker4B.score_in_threshold",0.2f);
    {setPropertyTooltip("score_in_threshold","[intensity 0-100] prethreshold on intensity score for filtering out possible fingertips");}
    private float score_sup_threshold=getPrefs().getFloat("PawTracker4B.score_sup_threshold",0.1f);
    {setPropertyTooltip("score_sup_threshold","[acc value] threshold on contrast value for fingertips");}
    private float line2shape_thresh=getPrefs().getFloat("PawTracker4B.line2shape_thresh",0.3f);
    {setPropertyTooltip("line2shape_thresh","[acc value] finger without fingertip stops at shape or below this value");}
    
    
    private int score_range=getPrefs().getInt("PawTracker4B.score_range",3);
    {setPropertyTooltip("score_range","[nb pixels] min distance between two detected fingertip");}
    
    private int line_range=getPrefs().getInt("PawTracker4B.line_range",8);
    {setPropertyTooltip("line_range","(unused) min distance between two finger lines");}
    private int lines_n_avg=getPrefs().getInt("PawTracker4B.lines_n_avg",8);
    {setPropertyTooltip("lines_n_avg","nb of possible lines used for averaging most likely finger line");}
    
    
    
    
    
    
    private int intensityZoom = getPrefs().getInt("PawTracker4B.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    
    private float brightness=getPrefs().getFloat("PawTracker4B.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
    
    
    private float shapeLimit=getPrefs().getFloat("PawTracker4B.shapeLimit",2f);
    {setPropertyTooltip("shapeLimit","[acc value] fingertip pattern matching: surrouding points are seen as zeroes under this value");}
    private float shapeThreshold=getPrefs().getFloat("PawTracker4B.shapeThreshold",2f);
    {setPropertyTooltip("shapeThreshold","[acc value] fingertip pattern matching: center points must be above this value");}
    
    
    private float shapeDLimit=getPrefs().getFloat("PawTracker4B.shapeDLimit",2f);
    {setPropertyTooltip("shapeDLimit","[acc value] fingertip pattern matching: surrouding points are seen as zeroes under this value");}
    private float shapeDThreshold=getPrefs().getFloat("PawTracker4B.shapeDThreshold",2f);
    {setPropertyTooltip("shapeDThreshold","[acc value] fingertip pattern matching: center points must be above this value");}
    
    
    private float doorMinDiff=getPrefs().getFloat("PawTracker4B.doorMinDiff",2f);
    {setPropertyTooltip("doorMinDiff","(unused) fingertip pattern matching: same as shapeLimit but inside door zone");}
    private float doorMaxDiff=getPrefs().getFloat("PawTracker4B.doorMaxDiff",2f);
    {setPropertyTooltip("doorMaxDiff","(unused) fingertip pattern matching: same as shapeThreshold but inside door zone");}
    
    private float finger_cluster_range=getPrefs().getFloat("PawTracker4B.finger_cluster_range",2);
    {setPropertyTooltip("finger_cluster_range","[nb pixels] tracking range for cluster tracking fingertips");}
    
    private float finger_ori_variance=getPrefs().getFloat("PawTracker4B.finger_ori_variance",60);
    {setPropertyTooltip("finger_ori_variance","[degrees] angle variability allowance for fingerbase, from fingertip direction");}
    
    
    private float node_range=getPrefs().getFloat("PawTracker4B.node_range",2);
    {setPropertyTooltip("node_range","[nb pixels] min dist between fingertips and end node of skeletton, for detecting finger");}
    
    
    private float palmback_below = getPrefs().getFloat("PawTracker4B.palmback_below",0.3f);
    {setPropertyTooltip("palmback_below","[acc value] (to detect when not on palm) min value of points belonging to palm's backin small range low pass filter");}
    
    private float palmback_value = getPrefs().getFloat("PawTracker4B.palmback_value",0.6f);
    {setPropertyTooltip("palmback_value","[acc value] (to detect when on palm) min value of points belonging to palm's back in large range low pass filter");}
    private int palmback_distance = getPrefs().getInt("PawTracker4B.palmback_distance",30);
    {setPropertyTooltip("palmback_distance","[nb pixels] max length between finger base point and palm back");}
    
    private int finger_start_threshold=getPrefs().getInt("PawTracker4B.finger_start_threshold",45);
    {setPropertyTooltip("finger_start_threshold","[intensity 0-100] threshold for first time fingertip detection");}
    
    
    private int finger_length=getPrefs().getInt("PawTracker4B.finger_length",10);
    {setPropertyTooltip("finger_length","[nb pixels] max length of finger segment");}
    
    private float finger_mv_smooth=getPrefs().getFloat("PawTracker4B.finger_mv_smooth",0.1f);
    
    private float finger_sensitivity=getPrefs().getFloat("PawTracker4B.finger_sensitivity",2.0f);
    
    
    
    
    private boolean track = getPrefs().getBoolean("PawTracker4B.track",true);
    private boolean showYColor = getPrefs().getBoolean("PawTracker4B.showYColor",false);
    private boolean showXColor = getPrefs().getBoolean("PawTracker4B.showXColor",false);
    private boolean showZColor = getPrefs().getBoolean("PawTracker4B.showZColor",false);
    private boolean showShadows = getPrefs().getBoolean("PawTracker4B.showShadows",false);
    private boolean showCorner = getPrefs().getBoolean("PawTracker4B.showCorner",false);
    
    private boolean correctY = getPrefs().getBoolean("PawTracker4B.correctY",false);
    private boolean useDualFilter = getPrefs().getBoolean("PawTracker4B.useDualFilter",false);
 
    
    
    private boolean showFingers = getPrefs().getBoolean("PawTracker4B.showFingers",true);
    private boolean showFingerTips = getPrefs().getBoolean("PawTracker4B.showFingerTips",true);
    
    private boolean showZones = getPrefs().getBoolean("PawTracker4B.showZones",true);
    private boolean showAll = getPrefs().getBoolean("PawTracker4B.showAll",true);
    // show intensity inside shape
    
    private boolean showAcc = getPrefs().getBoolean("PawTracker4B.showAcc",false);
    private boolean showOnlyAcc = getPrefs().getBoolean("PawTracker4B.showOnlyAcc",false);
    private boolean showDecay = getPrefs().getBoolean("PawTracker4B.showDecay",false);
    
    
    private boolean scaleAcc = getPrefs().getBoolean("PawTracker4B.scaleAcc",true);
    
    private boolean showCage = getPrefs().getBoolean("PawTracker4B.showCage",true);
    private boolean showFrame = getPrefs().getBoolean("PawTracker4B.showFrame",true);
    private boolean showWindow = getPrefs().getBoolean("PawTracker4B.showWindow",true);
    private boolean showScore = getPrefs().getBoolean("PawTracker4B.showScore",false);
    
    
    
    private boolean showShapePoints = getPrefs().getBoolean("PawTracker4B.showShapePoints",true);
    private boolean showFingerPoints = getPrefs().getBoolean("PawTracker4B.showFingerPoints",true);
    
    
    
    private boolean showShape = getPrefs().getBoolean("PawTracker4B.showShape",true);
    private boolean showRLColors = getPrefs().getBoolean("PawTracker4B.showRLColors",false);
    private boolean showAxes = getPrefs().getBoolean("PawTracker4B.showAxes",true);
    
    
    
    
    private int lowFilter_radius=getPrefs().getInt("PawTracker4B.lowFilter_radius",3);
    private int lowFilter_density=getPrefs().getInt("PawTracker4B.lowFilter_density",17);
    private float lowFilter_threshold=getPrefs().getFloat("PawTracker4B.lowFilter_threshold",0);
    
    private int lowFilter_radius2=getPrefs().getInt("PawTracker4B.lowFilter_radius2",10);
    private int lowFilter_density2=getPrefs().getInt("PawTracker4B.lowFilter_density2",5);
    
    
    
    
    private boolean showCorrectionMatrix = getPrefs().getBoolean("PawTracker4B.showCorrectionMatrix",false);
    
    
    private boolean showPalm = getPrefs().getBoolean("PawTracker4B.showPalm",false);
    
    private boolean showSkeletton = getPrefs().getBoolean("PawTracker4B.showSkeletton",false);
    private boolean showSecondFilter = getPrefs().getBoolean("PawTracker4B.showSecondFilter",false);
    private boolean showTopography = getPrefs().getBoolean("PawTracker4B.showTopography",false);
    
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTracker4B.resetPawTracking",false);
    private boolean validateParameters=getPrefs().getBoolean("PawTracker4B.validateParameters",false);
    
    private float event_strength=getPrefs().getFloat("PawTracker4B.event_strength",2f);
    private float intensity_strength=getPrefs().getFloat("PawTracker4B.intensity_strength",0.5f);
    private float intensity_threshold=getPrefs().getFloat("PawTracker4B.intensity_threshold",0.5f);
    private float in_value_threshold=getPrefs().getFloat("PawTracker4B.in_value_threshold",0.2f);
    private float sk_threshold=getPrefs().getFloat("PawTracker4B.sk_threshold",0.2f);
    
    
    private float finger_border_mix=getPrefs().getFloat("PawTracker4B.finger_border_mix",0.1f);
    private float finger_tip_mix=getPrefs().getFloat("PawTracker4B.finger_tip_mix",0.5f);
    private int finger_surround=getPrefs().getInt("PawTracker4B.finger_surround",10);
    private int finger_creation_surround=getPrefs().getInt("PawTracker4B.finger_creation_surround",30);
    
    private int intensity_range=getPrefs().getInt("PawTracker4B.intensity_range",2);
    
    private int decayTimeLimit=getPrefs().getInt("PawTracker4B.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
    private boolean decayOn = getPrefs().getBoolean("PawTracker4B.decayOn",false);
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}
    
    
    
    
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    PawTracker3DStatic2 paw3DTracker;
    int trackerID;
    
    // Global variables
    
    
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    int currentTime = 0;
    
    
    /** additional classes */
    /** EventPoint : all data about a point in retina space */
    /**
     * public class EventPoint{
     *
     *
     *
     *
     * int updateTime; // time of current update
     * int previousUpdate; // time of previous update
     *
     *
     * float previousShortFilteredValue = 0;
     * float previousDecayedFilteredValue = 0;
     *
     * float decayedFilteredValue;         // third low pass filter with decaying values
     * float previousValue=0;             // last contract value
     * float lastValue=0;                // last contract value
     * float accValue=0;                // accumulated contrast value
     * boolean linkPawBack = false;    // when finger is attached to paw back at this point
     * boolean onFingerLine = false;  // if is part of a finger line
     * boolean isSkeletton = false;  // true if part of the skeletton of paw obtained by thinnning
     * boolean isEndNode = false;   // true if end of segment of skeletton points
     * float shortFilteredValue;   // short range topographic filter
     * float largeFilteredValue;  // large range topographic filter
     * boolean border;           // true if point is on the paw's border
     * Integer group;           // contour group to which this point belongs, group 1 is touching door
     * float intensity;        // value of intensity of point, projected from border point toward inside, to detect
     * // convex borders and possible finger points
     * float fingerScore;    // likelyhood score of this point being a fingertip, obtained by template matching
     * int zerosAround;     // number of low value neighbours for border matching
     * int zerosDAround;   // number of low value neighbours for border matching for decayed filter
     *
     * float gcx;        // float coordinates for gc of zeroes around when point is border
     * float gcy;
     *
     * float prev_gcx;        // previous float coordinates for gc of zeroes around when point is border
     * float prev_gcy;
     *
     * boolean decayedBorder=false; // is a border computed on decayed filtered value
     *
     * int skValue;     // Skeletton value (?)
     * //Finger finger; // finger
     * int x;
     * int y;
     *
     * public EventPoint(  ){
     *
     * }
     * public EventPoint( int x, int y ){
     * this.x = x;
     * this.y = y;
     * }
     *
     * public float getAccValue( int currentTime ){
     * return accValue-decayedValue(accValue,currentTime-updateTime);
     * }
     *
     * public float getShortFilteredValue( int currentTime ){
     * return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
     * }
     *
     * public float getDecayedFilteredValue( int currentTime ){
     * return decayedFilteredValue-decayedValue(decayedFilteredValue,currentTime-updateTime);
     * }
     *
     * public float getPreviousShortFilteredValue( int currentTime ){
     * return previousShortFilteredValue-decayedValue(previousShortFilteredValue,currentTime-previousUpdate);
     * }
     *
     *
     * float decayedValue( float value, int time ){
     * float res=0;
     * float dt = (float)time/(float)decayTimeLimit;
     * if(dt<1){
     * res = value * dt;
     * }
     * return res;
     * }
     *
     *
     * void addToGc(int x, int y){
     * if(zerosAround>0){
     * float z = 1/zerosAround;
     * gcx = (1-z)*gcx + z*x;
     * gcy = (1-z)*gcy + z*y;
     * //gcx = x;
     * // gcy = y;
     * } else {
     * // System.out.println("addToGc zerosAround: "+zerosAround);
     * gcx = x;
     * gcy = y;
     * }
     * }
     *
     * void remFromGc( int x, int y){
     * if(zerosAround>0){
     * float z = 1/zerosAround;
     * //  gcx += z*(gcx - x);
     * //   gcy += z*(gcy - y);
     * } else {
     * // gcx = 0;
     * //  gcy = 0;
     * }
     * }
     *
     * void resetGc(){
     * gcx = 0;
     * gcy = 0;
     * prev_gcx = 0;
     * prev_gcy = 0;
     * }
     *
     * void setGc(){
     * // for all neighbours
     * int i;
     * int j;
     * int nb = 0;
     * for (i=x-1; i<x+2;i++){
     * if(i>=0&&i<retinaSize){
     * for (j=y-1; j<y+2;j++){
     * if(j>=0&&j<retinaSize){
     * EventPoint surroundPoint = eventPoints[i][j];
     *
     * if(surroundPoint.shortFilteredValue>shapeLimit){
     * gcx += i;
     * gcy += j;
     * nb++;
     * }
     * }
     * }
     * }
     * }
     *
     *
     * gcx = gcx/nb;
     * gcy = gcy/nb;
     * }
     *
     *
     * void setPrevGc(){
     * prev_gcx = gcx;
     * prev_gcy = gcy;
     * }
     *
     * }
     **/
    
    
    
    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    
    
    /** old PawTracker2 parameters : */
    
    /** Creates a new instance of PawTracker */
    public PawTracker4B(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
        paw3DTracker = PawTracker3DStatic2.INSTANCE;
        
        
        System.out.println("build resetPawTracker4");
        
        trackerID = paw3DTracker.register(chip);
        initFilter();
        
        //resetPawTracker();
        
        //  validateParameterChanges();
        chip.addObserver(this);
        
        System.out.println("End build resetPawTracker4B");
        
    }
    
    public void initFilter() {
        initPawTracker3DStatic();
    }
    
    
    
    private void resetPawTracker(){
        
        
        
        //  activity_started = false;
        
        
        System.out.println("resetPawTracker4B");
        
        
        paw3DTracker.reset();
        
        setResetPawTracking(false);//this should also update button in panel but doesn't'
        
        System.out.println("End of resetPawTracker4B");
    }
    
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
    
    public void validateParameterChanges(){
        
        setValidateParameters(false); //should update gui
        // recompute densities
        
        
    }
    
    
    
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<TypedEvent> ae){
        
        
        if(isResetPawTracking()){
            // reset
            resetPawTracker();
            return; //maybe continue then
        }
        
        int n=ae.getSize();
        if(n==0) return;
        
        if(validateParameters){
            validateParameterChanges();
            
        }
        
        float step = event_strength / (colorScale + 1);
        
        // accumulate the events
        
        for(TypedEvent e:ae){
            int type=e.getType();
            
            paw3DTracker.track(e,trackerID);
            
            
            
        }//end for all incoming events
        
        
        //  chip.getAeViewer().aePlayer.pause();
        //  System.out.println("trackerID: "+trackerID);
        if(trackerID==1)
            paw3DTracker.display();
        //  chip.getAeViewer().aePlayer.resume();
        
    }
    
    
    
    
    public String toString(){
        String s="PawTracker4B";
        return s;
    }
    
    
    
    
    
    
    
    
    void checkInsideIntensityFrame(){
        if(showWindow && insideIntensityFrame==null) createInsideIntensityFrame();
    }
    
    JFrame insideIntensityFrame=null;
    GLCanvas insideIntensityCanvas=null;
    GLU glu=null;
    int highlight_x = 0;
    int highlight_y = 0;
    boolean highlight = false;
    
//    GLUT glut=null;
    void createInsideIntensityFrame(){
        if(trackerID==1){
            insideIntensityFrame=new JFrame("Left View");
        } else {
            insideIntensityFrame=new JFrame("Right View");
        }
        insideIntensityFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        insideIntensityCanvas=new GLCanvas();
        
        
        
        
        insideIntensityCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
                
                
                // display inside intensity
                
                
                
                
                if(highlight){
                    gl.glColor3f(1,1,0);
                    gl.glRectf(highlight_x*intensityZoom,highlight_y*intensityZoom,(highlight_x+1)*intensityZoom,(highlight_y+1)*intensityZoom);
                    
                }
                
                
                
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    if(glu==null) glu=new GLU();
                    log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }
            
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
                final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
            }
            
            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        insideIntensityFrame.getContentPane().add(insideIntensityCanvas);
        insideIntensityFrame.pack();
        insideIntensityFrame.setVisible(true);
    }
    
    
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
        resetPawTracker();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(track) checkInsideIntensityFrame();
        track(in);
        if (track&&showWindow) insideIntensityCanvas.repaint();
        return in;
    }
    
    
    
    
    
//    public float getMixingFactor() {
//        return mixingFactor;
//    }
//
//    public void setMixingFactor(float mixingFactor) {
//        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
//        this.mixingFactor = mixingFactor;
//        getPrefs().putFloat("PawTracker4B.mixingFactor",mixingFactor);
//    }
    
    
    
    
    
//    /** @see #setSurround */
//    public float getSurround() {
//        return surround;
//    }
//
//    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
//     * @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
//     */
//    public void setSurround(float surround){
//        if(surround < 1) surround = 1;
//        this.surround = surround;
//        getPrefs().putFloat("PawTracker4B.surround",surround);
//    }
    
    
    
    
    
//    public boolean isColorClustersDifferentlyEnabled() {
//        return colorClustersDifferentlyEnabled;
//    }
    
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBoxCentered(GL gl, int x, int y, int sx, int sy){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx,y-sy);
            gl.glVertex2i(x+sx,y+sy);
            gl.glVertex2i(x-sx,y+sy);
        }
        gl.glEnd();
    }
    
    protected void drawBox(GL gl, int x, int x2, int y, int y2){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x,y);
            gl.glVertex2i(x2,y);
            gl.glVertex2i(x2,y2);
            gl.glVertex2i(x,y2);
        }
        gl.glEnd();
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTracker4B.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            
            
            //for all fingers
            // for(Cluster c:clusters){
            
            // get finger location
            //int x=(int)c.getLocation().x;
            // int y=(int)c.getLocation().y;
            
            
            // int sy=(int)c.radiusY; // sx sy are (half) size of rectangle
            // int sx=(int)c.radiusX;
            
            // int sy=fingertip_size; // sx sy are (half) size of rectangle
            // int sx=fingertip_size;
            
            // set color and line width of cluster annotation
            // to adapt
            // c.setColorAutomatically();
            // c.getColor().getRGBComponents(rgb);
            
            
            
            // paul: draw finger lines experimantal
//            if(showFingers){
//                // draw fingers here
//
//
//               for(int i=0; i<fingerLines.size(); i++){
//                   Line line = (Line)fingerLines.elementAt(i);
//                   gl.glBegin(GL.GL_LINES);
//                   {
//                       if(line.midx==0&&line.midy==0){
//                        gl.glVertex2i(line.x0,line.y0);
//                        gl.glVertex2f(line.x1,line.y1);
//                       } else {
//                         gl.glVertex2i(line.x0,line.y0);
//                         gl.glVertex2f(line.midx,line.midy);
//                         gl.glVertex2i(line.midx,line.midy);
//                         gl.glVertex2f(line.x1,line.y1);
//                       }
//                   }
//                   gl.glEnd();
//               }
//
//
//            }
            
            if(!showShape){
                if(showShapePoints){
                    /*
                    for (int i=0;i<contour.getMaxX();i++){
                        for (int j=0;j<contour.getMaxY();j++){
                            if(contour.eventsArray[i][j]!=null){
                                if (contour.eventsArray[i][j].on==1){
                                    if (contour.eventsArray[i][j].label==1||showAll){
                     
                                        //float colorPoint = ((float)(contour.nbLabels-contour.eventsArray[i][j].label-1))/contour.nbLabels;
                                        // System.out.println("colorPoint "+colorPoint+" "+contour.eventsArray[i][j].label);
                                        // if(colorPoint>1)colorPoint=1f;
                                        // draw point
                                        // gl.glColor3f(colorPoint,0,1-colorPoint);
                                        // if (contour.eventsArray[i][j].label==1){
                                        gl.glColor3f(0,1,0);
                                        // }
                     
                                        gl.glBegin(GL.GL_LINE_LOOP);
                                        {
                                            gl.glVertex2i(i,j);
                                            gl.glVertex2i(i+1,j);
                                            gl.glVertex2i(i+1,j+1);
                                            gl.glVertex2i(i,j+1);
                                        }
                                        gl.glEnd();
                                    }
                                }
                            }
                        }
                    }
                     **/
                }
                
            } else { //show shape
               /*
                gl.glColor3f(0,1,0);
                for(int i=0;i<nbSegments;i++){
                    Segment o = segments[i];
                    //  draw line here and
                    gl.glBegin(GL.GL_LINES);
                    {
                        gl.glVertex2i(o.x1,o.y1);
                        gl.glVertex2i(o.x2,o.y2);
                    }
                    gl.glEnd();
                
                }
                **/
            } // end showShape
//
//            gl.glColor3f(1,0,0);
//            // draw gc
//            int i = gc.x;
//            int j = gc.y;
//            gl.glBegin(GL.GL_LINE_LOOP);
//            {
//                gl.glVertex2i(i,j);
//                gl.glVertex2i(i+1,j);
//                gl.glVertex2i(i+1,j+1);
//                gl.glVertex2i(i,j+1);
//            }
//            gl.glEnd();
//
            
            // draw text avgEventRate
            // int font = GLUT.BITMAP_HELVETICA_12;
            // gl.glColor3f(1,0,0);
            // gl.glRasterPos3f(c.location.x,c.location.y,0);
            // annotate the cluster with the event rate computed as 1/(avg ISI) in keps
            //  float keps=1e3f / (c.getAvgISI()*AEConstants.TICK_DEFAULT_US);
            
            //  chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f", c.prevNbEvents/keps ));
            
            if(showZones){
                // draw door
                gl.glColor3f(0,1,0);
                drawBox(gl,door_xa,door_xb,door_ya,door_yb);
            }
            
            
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
//    void drawGLCluster(int x1, int y1, int x2, int y2)
    
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
//            for(Cluster c:clusters){
//                if(c.isVisible()){
//                    drawCluster(c, frame);
//                }
//            }
    }
    
    
    
    
    public synchronized boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    public synchronized void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("PawTrackerData.txt"))));
                logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    
//    public void setIn_threshold(float in_threshold) {
//        this.in_threshold = in_threshold;
//        getPrefs().putFloat("PawTracker4B.in_threshold",in_threshold);
//    }
//    public float getIn_threshold() {
//        return in_threshold;
//    }
    
    public void setLine_threshold(float line_threshold) {
        this.line_threshold = line_threshold;
        paw3DTracker.setLine_threshold(line_threshold);
        getPrefs().putFloat("PawTracker4B.line_threshold",line_threshold);
    }
    public float getLine_threshold() {
        return line_threshold;
    }
    
    public void setLine_range(int line_range) {
        this.line_range = line_range;
        paw3DTracker.setLine_range( line_range);
        getPrefs().putInt("PawTracker4B.line_range",line_range);
    }
    public int getLine_range() {
        return line_range;
    }
    
    public void setLines_n_avg(int lines_n_avg) {
        this.lines_n_avg = lines_n_avg;
        paw3DTracker.setLines_n_avg( lines_n_avg);
        getPrefs().putInt("PawTracker4B.lines_n_avg",lines_n_avg);
    }
    public int getLines_n_avg() {
        return lines_n_avg;
    }
    
    
    
    
    
    
    
    public void setScore_range(int score_range) {
        this.score_range = score_range;
        paw3DTracker.setScore_range( score_range);
        getPrefs().putInt("PawTracker4B.score_range",score_range);
    }
    public int getScore_range() {
        return score_range;
    }
    
    public void setScore_threshold(int score_threshold) {
        this.score_threshold = score_threshold;
        paw3DTracker.setScore_threshold( score_threshold);
        getPrefs().putInt("PawTracker4B.score_threshold",score_threshold);
    }
    public int getScore_threshold() {
        return score_threshold;
    }
    
    public void setScore_in_threshold(float score_in_threshold) {
        this.score_in_threshold = score_in_threshold;
        paw3DTracker.setScore_in_threshold( score_in_threshold);
        getPrefs().putFloat("PawTracker4B.score_in_threshold",score_in_threshold);
    }
    public float getScore_in_threshold() {
        return score_in_threshold;
    }
    
    public void setScore_sup_threshold(float score_sup_threshold) {
        this.score_sup_threshold = score_sup_threshold;
        paw3DTracker.setScore_sup_threshold( score_sup_threshold);
        getPrefs().putFloat("PawTracker4B.score_sup_threshold",score_sup_threshold);
    }
    public float getScore_sup_threshold() {
        return score_sup_threshold;
    }
    
    
    
    public void setLine2shape_thresh(float line2shape_thresh) {
        this.line2shape_thresh = line2shape_thresh;
        paw3DTracker.setLine2shape_thresh( line2shape_thresh);
        getPrefs().putFloat("PawTracker4B.line2shape_thresh",line2shape_thresh);
    }
    public float getLine2shape_thresh() {
        return line2shape_thresh;
    }
    
    
    
    
    
    public void setShapeLimit(float shapeLimit) {
        this.shapeLimit = shapeLimit;
        paw3DTracker.setShapeLimit( shapeLimit);
        getPrefs().putFloat("PawTracker4B.shapeLimit",shapeLimit);
    }
    public float getShapeLimit() {
        return shapeLimit;
    }
    
    public void setShapeDLimit(float shapeDLimit) {
        this.shapeDLimit = shapeDLimit;
        paw3DTracker.setShapeDLimit( shapeDLimit);
        getPrefs().putFloat("PawTracker4B.shapeDLimit",shapeDLimit);
    }
    public float getShapeDLimit() {
        return shapeDLimit;
    }
    
    public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;
        paw3DTracker.setDecayTimeLimit( decayTimeLimit);
        getPrefs().putInt("PawTracker4B.decayTimeLimit",decayTimeLimit);
    }
    public int getDecayTimeLimit() {
        return decayTimeLimit;
    }
    
    
    
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        paw3DTracker.setIntensityZoom( intensityZoom);
        getPrefs().putInt("PawTracker4B.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    public void setObj_xa(int obj_xa) {
        this.obj_xa = obj_xa;
        paw3DTracker.setObj_xa( obj_xa);
        getPrefs().putInt("PawTracker4B.obj_xa",obj_xa);
    }
    
    public int getObj_xa() {
        return obj_xa;
    }
    public void setObj_ya(int obj_ya) {
        this.obj_ya = obj_ya;
        paw3DTracker.setObj_ya( obj_ya);
        getPrefs().putInt("PawTracker4B.obj_ya",obj_ya);
    }
    
    public int getObj_ya() {
        return obj_ya;
    }
    public void setObj_xb(int obj_xb) {
        this.obj_xb = obj_xb;
        paw3DTracker.setObj_xb( obj_xb);
        getPrefs().putInt("PawTracker4B.obj_xb",obj_xb);
    }
    
    public int getObj_xb() {
        return obj_xb;
    }
    public void setObj_yb(int obj_yb) {
        this.obj_yb = obj_yb;
        paw3DTracker.setObj_yb( obj_yb);
        getPrefs().putInt("PawTracker4B.obj_yb",obj_yb);
    }
    
    public int getObj_yb() {
        return obj_yb;
    }
    
    public void setObj_sizea(int obj_sizea) {
        this.obj_sizea = obj_sizea;
        paw3DTracker.setObj_sizea( obj_sizea);
        getPrefs().putInt("PawTracker4B.obj_sizea",obj_sizea);
    }
    
    public int getObj_sizea() {
        return obj_sizea;
    }
    
    public void setObj_sizeb(int obj_sizeb) {
        this.obj_sizeb = obj_sizeb;
        paw3DTracker.setObj_sizeb( obj_sizeb);
        getPrefs().putInt("PawTracker4B.obj_sizeb",obj_sizeb);
    }
    
    public int getObj_sizeb() {
        return obj_sizeb;
    }
    
    public void setDoor_z(int door_z) {
        this.door_z = door_z;
        paw3DTracker.setDoor_z( door_z);
        getPrefs().putInt("PawTracker4B.door_z",door_z);
    }
    
    public int getDoor_z() {
        return door_z;
    }
    
    public void setDoor_xa(int door_xa) {
        this.door_xa = door_xa;
        paw3DTracker.setDoor_xa( door_xa);
        getPrefs().putInt("PawTracker4B.door_xa",door_xa);
    }
    
    public int getDoor_xa() {
        return door_xa;
    }
    
    public void setDoor_xb(int door_xb) {
        this.door_xb = door_xb;
        paw3DTracker.setDoor_xb( door_xb);
        getPrefs().putInt("PawTracker4B.door_xb",door_xb);
    }
    
    public int getDoor_xb() {
        return door_xb;
    }
    
    public void setDoor_ya(int door_ya) {
        this.door_ya = door_ya;
        paw3DTracker.setDoor_ya( door_ya);
        getPrefs().putInt("PawTracker4B.door_ya",door_ya);
    }
    
    public int getDoor_ya() {
        return door_ya;
    }
    
    public void setDoor_yb(int door_yb) {
        this.door_yb = door_yb;
        paw3DTracker.setDoor_yb( door_yb);
        getPrefs().putInt("PawTracker4B.door_yb",door_yb);
    }
    
    public int getDoor_yb() {
        return door_yb;
    }
    
    
    public void setNode_range(float node_range) {
        this.node_range = node_range;
        paw3DTracker.setNode_range( node_range);
        getPrefs().putFloat("PawTracker4B.node_range",node_range);
    }
    public float getNode_range() {
        return node_range;
    }
    
    
    
    
    public void setFinger_sensitivity(float finger_sensitivity) {
        this.finger_sensitivity = finger_sensitivity;
        paw3DTracker.setFinger_sensitivity( finger_sensitivity);
        getPrefs().putFloat("PawTracker4B.finger_sensitivity",finger_sensitivity);
    }
    public float getFinger_sensitivity() {
        return finger_sensitivity;
    }
    
    public void setFinger_mv_smooth(float finger_mv_smooth) {
        this.finger_mv_smooth = finger_mv_smooth;
        paw3DTracker.setFinger_mv_smooth( finger_mv_smooth);
        getPrefs().putFloat("PawTracker4B.finger_mv_smooth",finger_mv_smooth);
    }
    public float getFinger_mv_smooth() {
        return finger_mv_smooth;
    }
    
    public void setFinger_length(int finger_length) {
        this.finger_length = finger_length;
        paw3DTracker.setFinger_length( finger_length);
        getPrefs().putInt("PawTracker4B.finger_length",finger_length);
    }
    public int getFinger_length() {
        return finger_length;
    }
    
    public void setFinger_start_threshold(int finger_start_threshold) {
        this.finger_start_threshold = finger_start_threshold;
        paw3DTracker.setFinger_start_threshold( finger_start_threshold);
        getPrefs().putInt("PawTracker4B.finger_start_threshold",finger_start_threshold);
    }
    public int getFinger_start_threshold() {
        return finger_start_threshold;
    }
    
    
    
    public void setFinger_cluster_range(float finger_cluster_range) {
        this.finger_cluster_range = finger_cluster_range;
        paw3DTracker.setFinger_cluster_range( finger_cluster_range);
        getPrefs().putFloat("PawTracker4B.finger_cluster_range",finger_cluster_range);
    }
    public float getFinger_cluster_range() {
        return finger_cluster_range;
    }
    
    public void setFinger_ori_variance(float finger_ori_variance) {
        this.finger_ori_variance = finger_ori_variance;
        paw3DTracker.setFinger_ori_variance( finger_ori_variance);
        getPrefs().putFloat("PawTracker4B.finger_ori_variance",finger_ori_variance);
    }
    public float getFinger_ori_variance() {
        return finger_ori_variance;
    }
    
    
    public void setPalmback_below(float palmback_below) {
        this.palmback_below = palmback_below;
        paw3DTracker.setPalmback_below( palmback_below);
        getPrefs().putFloat("PawTracker4B.palmback_below",palmback_below);
    }
    public float getPalmback_below() {
        return palmback_below;
    }
    public void setPalmback_value(float palmback_value) {
        this.palmback_value = palmback_value;
        paw3DTracker.setPalmback_value( palmback_value);
        getPrefs().putFloat("PawTracker4B.palmback_value",palmback_value);
    }
    public float getPalmback_value() {
        return palmback_value;
    }
    public void setPalmback_distance(int palmback_distance) {
        this.palmback_distance = palmback_distance;
        paw3DTracker.setPalmback_distance( palmback_distance);
        getPrefs().putInt("PawTracker4B.palmback_distance",palmback_distance);
    }
    public int getPalmback_distance() {
        return palmback_distance;
    }
    
    public void setIntensity_range(int intensity_range) {
        this.intensity_range = intensity_range;
        paw3DTracker.setIntensity_range( intensity_range);
        getPrefs().putInt("PawTracker4B.intensity_range",intensity_range);
    }
    public int getIntensity_range() {
        return intensity_range;
    }
    
    
    
    public void setIn_value_threshold(float in_value_threshold) {
        this.in_value_threshold = in_value_threshold;
        paw3DTracker.setIn_value_threshold( in_value_threshold);
        getPrefs().putFloat("PawTracker4B.in_value_threshold",in_value_threshold);
    }
    public float getIn_value_threshold() {
        return in_value_threshold;
    }
    
    public void setIntensity_threshold(float intensity_threshold) {
        this.intensity_threshold = intensity_threshold;
        paw3DTracker.setIntensity_threshold( intensity_threshold);
        getPrefs().putFloat("PawTracker4B.intensity_threshold",intensity_threshold);
    }
    public float getIntensity_threshold() {
        return intensity_threshold;
    }
    
    public void setIntensity_strength(float intensity_strength) {
        this.intensity_strength = intensity_strength;
        paw3DTracker.setIntensity_strength( intensity_strength);
        getPrefs().putFloat("PawTracker4B.intensity_strength",intensity_strength);
    }
    public float getIntensity_strength() {
        return intensity_strength;
    }
    
    
    public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
        paw3DTracker.setEvent_strength( event_strength);
        getPrefs().putFloat("PawTracker4B.event_strength",event_strength);
    }
    public float getEvent_strength() {
        return event_strength;
    }
    
    
    
    
    
    
    
    public void setShapeThreshold(float shapeThreshold) {
        this.shapeThreshold = shapeThreshold;
        paw3DTracker.setShapeThreshold( shapeThreshold);
        getPrefs().putFloat("PawTracker4B.shapeThreshold",shapeThreshold);
    }
    public float getShapeThreshold() {
        return shapeThreshold;
    }
    
    public void setShapeDThreshold(float shapeDThreshold) {
        this.shapeDThreshold = shapeDThreshold;
        paw3DTracker.setShapeDThreshold( shapeDThreshold);
        getPrefs().putFloat("PawTracker4B.shapeDThreshold",shapeDThreshold);
    }
    public float getShapeDThreshold() {
        return shapeDThreshold;
    }
    
    public void setDoorMinDiff(float doorMinDiff) {
        this.doorMinDiff = doorMinDiff;
        paw3DTracker.setDoorMinDiff( doorMinDiff);
        getPrefs().putFloat("PawTracker4B.doorMinDiff",doorMinDiff);
    }
    public float getDoorMinDiff() {
        return doorMinDiff;
    }
    
    public void setDoorMaxDiff(float doorMaxDiff) {
        this.doorMaxDiff = doorMaxDiff;
        paw3DTracker.setDoorMaxDiff( doorMaxDiff);
        getPrefs().putFloat("PawTracker4B.doorMaxDiff",doorMaxDiff);
    }
    public float getDoorMaxDiff() {
        return doorMaxDiff;
    }
    
    public void setMinZeroes(int minZeroes) {
        this.minZeroes = minZeroes;
        paw3DTracker.setMinZeroes( minZeroes);
        getPrefs().putInt("PawTracker4B.minZeroes",minZeroes);
    }
    public int getMinZeroes() {
        return minZeroes;
    }
    
    public void setMaxZeroes(int maxZeroes) {
        this.maxZeroes = maxZeroes;
        paw3DTracker.setMaxZeroes( maxZeroes);
        getPrefs().putInt("PawTracker4B.maxZeroes",maxZeroes);
    }
    
    public int getMaxZeroes() {
        return maxZeroes;
    }
    
    
    
    
    
//    public void setDoorMinZeroes(int doorMinZeroes) {
//        this.doorMinZeroes = doorMinZeroes;
//        paw3DTracker.;getPrefs().putInt("PawTracker4B.doorMinZeroes",doorMinZeroes);
//    }
//    public int getDoorMinZeroes() {
//        return doorMinZeroes;
//    }
//
//    public void setDoorMaxZeroes(int doorMaxZeroes) {
//        this.doorMaxZeroes = doorMaxZeroes;
//        paw3DTracker.;getPrefs().putInt("PawTracker4B.doorMaxZeroes",doorMaxZeroes);
//    }
//    public int getDoorMaxZeroes() {
//        return doorMaxZeroes;
//    }
    
    
    
    
    
    
    
    
    
    
    
//    public int getRetinaSize() {
//        return retinaSize;
//    }
//
//    public void setRetinaSize(int retinaSize) {
//        this.retinaSize = retinaSize;
//        getPrefs().putInt("PawTracker4B.retinaSize",retinaSize);
//
//    }
    
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        paw3DTracker.setResetPawTracking( resetPawTracking);
        getPrefs().putBoolean("PawTracker4B.resetPawTracking",resetPawTracking);
        
    }
    
    public boolean isValidateParameters() {
        return validateParameters;
    }
    public void setValidateParameters(boolean validateParameters) {
        this.validateParameters = validateParameters;
        paw3DTracker.setValidateParameters( validateParameters);
        getPrefs().putBoolean("PawTracker4B.validateParameters",validateParameters);
        
    }
    
    
    public void setShowCorrectionMatrix(boolean showCorrectionMatrix){
        this.showCorrectionMatrix = showCorrectionMatrix;
        paw3DTracker.setShowCorrectionMatrix( showCorrectionMatrix);
        getPrefs().putBoolean("PawTracker4B.showCorrectionMatrix",showCorrectionMatrix);
    }
    public boolean getShowCorrectionMatrix(){
        return showCorrectionMatrix;
    }
    
    
    public void setShowSkeletton(boolean showSkeletton){
        this.showSkeletton = showSkeletton;
        paw3DTracker.setShowSkeletton( showSkeletton);
        getPrefs().putBoolean("PawTracker4B.showSkeletton",showSkeletton);
    }
    public boolean isShowSkeletton(){
        return showSkeletton;
    }
    
    public void setShowSecondFilter(boolean showSecondFilter){
        this.showSecondFilter = showSecondFilter;
        paw3DTracker.setShowSecondFilter( showSecondFilter);
        getPrefs().putBoolean("PawTracker4B.showSecondFilter",showSecondFilter);
    }
    public boolean isShowSecondFilter(){
        return showSecondFilter;
    }
    
    public void setShowTopography(boolean showTopography){
        this.showTopography = showTopography;
        paw3DTracker.setShowTopography( showTopography);
        getPrefs().putBoolean("PawTracker4B.showTopography",showTopography);
    }
    public boolean isShowTopography(){
        return showTopography;
    }
    
    
    
    
    public void setShowPalm(boolean showPalm){
        this.showPalm = showPalm;
        paw3DTracker.setShowPalm( showPalm);
        getPrefs().putBoolean("PawTracker4B.showPalm",showPalm);
    }
    public boolean isShowPalm(){
        return showPalm;
    }
    
    
    
    
    
    
    
    
    public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        paw3DTracker.setScaleAcc( scaleAcc);
        getPrefs().putBoolean("PawTracker4B.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    
    
    public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        
        paw3DTracker.setShowAcc(showAcc);
        getPrefs().putBoolean("PawTracker4B.showAcc",showAcc);
    }
    public boolean isShowAcc(){
        return showAcc;
    }
    
    public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
        paw3DTracker.setShowOnlyAcc(showOnlyAcc);
        getPrefs().putBoolean("PawTracker4B.showOnlyAcc",showOnlyAcc);
    }
    public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
    public void setShowDecay(boolean showDecay){
        this.showDecay = showDecay;
        paw3DTracker.setShowDecay(showDecay);
        getPrefs().putBoolean("PawTracker4B.showDecay",showDecay);
    }
    public boolean isShowDecay(){
        return showDecay;
    }
    
    
      public void setUseDualFilter(boolean useDualFilter){
        this.useDualFilter = useDualFilter;
        paw3DTracker.setUseDualFilter( useDualFilter);
        getPrefs().putBoolean("PawTracker4B.useDualFilter",useDualFilter);
    }
    public boolean isUseDualFilter(){
        return useDualFilter;
    }
    
    
    
    
    public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
        paw3DTracker.setDecayOn( decayOn);
        getPrefs().putBoolean("PawTracker4B.decayOn",decayOn);
    }
    public boolean isDecayOn(){
        return decayOn;
    }
    
    
    public void setShowFrame(boolean showFrame){
        this.showFrame = showFrame;
        paw3DTracker.setShowFrame( showFrame);
        getPrefs().putBoolean("PawTracker4B.showFrame",showFrame);
    }
    public boolean isShowFrame(){
        return showFrame;
    }
    
    public void setShowCage(boolean showCage){
        this.showCage = showCage;
        paw3DTracker.setShowCage( showCage);
        getPrefs().putBoolean("PawTracker4B.showCage",showCage);
    }
    public boolean isShowCage(){
        return showCage;
    }
    
    
    public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        paw3DTracker.setShowWindow( showWindow);
        getPrefs().putBoolean("PawTracker4B.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
    
    
    public void setShowScore(boolean showScore){
        this.showScore = showScore;
        paw3DTracker.setShowScore( showScore);
        getPrefs().putBoolean("PawTracker4B.showScore",showScore);
    }
    public boolean isShowScore(){
        return showScore;
    }
    
    
    
    
    
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        paw3DTracker.setShowFingers( showFingers);
        getPrefs().putBoolean("PawTracker4B.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
    
    
    public void setShowFingerTips(boolean showFingerTips){
        this.showFingerTips = showFingerTips;
        paw3DTracker.setShowFingerTips( showFingerTips);
        getPrefs().putBoolean("PawTracker4B.showFingerTips",showFingerTips);
    }
    public boolean isShowFingerTips(){
        return showFingerTips;
    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
        paw3DTracker.setShowZones( showZones);
        getPrefs().putBoolean("PawTracker4B.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
        paw3DTracker.setShowAll( showAll);
        getPrefs().putBoolean("PawTracker4B.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
    public void setTrack(boolean track){
        this.track = track;
        
        getPrefs().putBoolean("PawTracker4B.track",track);
    }
    public boolean isTrack(){
        return track;
    }
    
//    public void setShowSequences(boolean showSequences){
//        this.showSequences = showSequences;
//        paw3DTracker.;getPrefs().putBoolean("PawTracker4B.showSequences",showSequences);
//    }
//    public boolean isShowSequences(){
//        return showSequences;
//    }
    
    
    
    public void setShowRLColors(boolean showRLColors){
        this.showRLColors = showRLColors;
        paw3DTracker.setShowRLColors( showRLColors);
        getPrefs().putBoolean("PawTracker4B.showRLColors",showRLColors);
    }
    public boolean isShowRLColors(){
        return showRLColors;
    }
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        paw3DTracker.setShowAxes( showAxes);
        getPrefs().putBoolean("PawTracker4B.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
    public void setShowShape(boolean showShape){
        this.showShape = showShape;
        paw3DTracker.setShowShape( showShape);
        getPrefs().putBoolean("PawTracker4B.showShape",showShape);
    }
    public boolean isShowShape(){
        return showShape;
    }
    
    
    public void setShowShapePoints(boolean showShapePoints){
        this.showShapePoints = showShapePoints;
        paw3DTracker.setShowShapePoints( showShapePoints);
        getPrefs().putBoolean("PawTracker4B.showShapePoints",showShapePoints);
    }
    public boolean isShowShapePoints(){
        return showShapePoints;
    }
    
    
    
    
    public void setShowFingerPoints(boolean showFingerPoints){
        this.showFingerPoints = showFingerPoints;
        paw3DTracker.setShowFingerPoints( showFingerPoints);
        getPrefs().putBoolean("PawTracker4B.showFingerPoints",showFingerPoints);
    }
    public boolean isShowFingerPoints(){
        return showFingerPoints;
    }
    
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        paw3DTracker.setLowFilter_radius( lowFilter_radius) ;
        getPrefs().putInt("PawTracker4B.lowFilter_radius",lowFilter_radius);
    }
    
    public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        paw3DTracker.setLowFilter_density( lowFilter_density);
        getPrefs().putInt("PawTracker4B.lowFilter_density",lowFilter_density);
    }
    
    public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
        paw3DTracker.setLowFilter_threshold( lowFilter_threshold);
        getPrefs().putFloat("PawTracker4B.lowFilter_threshold",lowFilter_threshold);
    }
    
    public int getLowFilter_radius2() {
        return lowFilter_radius2;
    }
    
    public void setLowFilter_radius2(int lowFilter_radius2) {
        this.lowFilter_radius2 = lowFilter_radius2;
        paw3DTracker.setLowFilter_radius2( lowFilter_radius2);
        getPrefs().putInt("PawTracker4B.lowFilter_radius2",lowFilter_radius2);
    }
    
    public int getLowFilter_density2() {
        return lowFilter_density2;
    }
    
    public void setLowFilter_density2(int lowFilter_density2) {
        this.lowFilter_density2 = lowFilter_density2;
        paw3DTracker.setLowFilter_density2( lowFilter_density2);
        getPrefs().putInt("PawTracker4B.lowFilter_density2",lowFilter_density2);
    }
    
    public float getFinger_border_mix() {
        return finger_border_mix;
    }
    
    public void setFinger_border_mix(float finger_border_mix) {
        this.finger_border_mix = finger_border_mix;
        paw3DTracker.setFinger_border_mix( finger_border_mix);
        getPrefs().putFloat("PawTracker4B.finger_border_mix",finger_border_mix);
    }
    
    public float getFinger_tip_mix() {
        return finger_tip_mix;
    }
    
    public void setFinger_tip_mix(float finger_tip_mix) {
        this.finger_tip_mix = finger_tip_mix;
        paw3DTracker.setFinger_tip_mix( finger_tip_mix);
        getPrefs().putFloat("PawTracker4B.finger_tip_mix",finger_tip_mix);
    }
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        paw3DTracker.setFinger_surround( finger_surround);
        getPrefs().putInt("PawTracker4B.finger_surround",finger_surround);
    }
    
    
    public int getFinger_creation_surround() {
        return finger_creation_surround;
    }
    
    public void setFinger_creation_surround(int finger_creation_surround) {
        this.finger_creation_surround = finger_creation_surround;
        paw3DTracker.setFinger_creation_surround( finger_creation_surround);
        getPrefs().putInt("PawTracker4B.finger_creation_surround",finger_creation_surround);
    }
    
    
    
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        paw3DTracker.setBrightness( brightness);
        getPrefs().putFloat("PawTracker4B.brightness",brightness);
    }
    
    
    public float getSk_threshold() {
        return sk_threshold;
    }
    
    public void setSk_threshold(float sk_threshold) {
        this.sk_threshold = sk_threshold;
        paw3DTracker. setSk_threshold( sk_threshold);
        getPrefs().putFloat("PawTracker4B.sk_threshold",sk_threshold);
    }
    
    
    
    public float getPlaneAngle() {
        return planeAngle;
    }
    public void setPlaneAngle(float planeAngle) {
        this.planeAngle = planeAngle;
        paw3DTracker.setPlaneAngle( planeAngle);
        getPrefs().putFloat("PawTracker4B.planeAngle",planeAngle);
    }
    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
        paw3DTracker.setAlpha( alpha);
        getPrefs().putFloat("PawTracker4B.alpha",alpha);
    }
    public float getAlpha() {
        return alpha;
    }
    public void setIntensity(float intensity) {
        this.intensity = intensity;
        paw3DTracker.setIntensity( intensity);
        getPrefs().putFloat("PawTracker4B.intensity",intensity);
    }
    public float getIntensity() {
        return intensity;
    }
    
    public void setRay_length(int ray_length) {
        this.ray_length = ray_length;
        paw3DTracker.setRay_length( ray_length);
        getPrefs().putInt("PawTracker4B.ray_length",ray_length);
    }
    public int getRay_length() {
        return ray_length;
    }
    
    public void setAdjustTime(int adjustTime) {
        this.adjustTime = adjustTime;
        paw3DTracker.setAdjustTime( adjustTime);
        getPrefs().putInt("PawTracker4B.adjustTime",adjustTime);
    }
    public int getAdjustTime() {
        return adjustTime;
    }
    
    public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        paw3DTracker.setValueThreshold( valueThreshold);
        getPrefs().putFloat("PawTracker4B.valueThreshold",valueThreshold);
    }
    public float getValueThreshold() {
        return valueThreshold;
    }
    
    
    public void setStereoThreshold(float stereoThreshold) {
        this.stereoThreshold = stereoThreshold;
        paw3DTracker.setStereoThreshold( stereoThreshold);
        getPrefs().putFloat("PawTracker4B.stereoThreshold",stereoThreshold);
    }
    public float getStereoThreshold() {
        return stereoThreshold;
    }
    
    public void setYLeftCorrection(int yLeftCorrection) {
        this.yLeftCorrection = yLeftCorrection;
        paw3DTracker.setYLeftCorrection( yLeftCorrection);
        getPrefs().putInt("PawTracker4B.yLeftCorrection",yLeftCorrection);
    }
    public int getYLeftCorrection() {
        return yLeftCorrection;
    }
    public void setYRightCorrection(int yRightCorrection) {
        this.yRightCorrection = yRightCorrection;
        paw3DTracker.setYRightCorrection( yRightCorrection);
        getPrefs().putInt("PawTracker4B.yRightCorrection",yRightCorrection);
    }
    public int getYRightCorrection() {
        return yRightCorrection;
    }
    
    public void setYCurveFactor(float yCurveFactor) {
        this.yCurveFactor = yCurveFactor;
        paw3DTracker.setYCurveFactor( yCurveFactor);
        getPrefs().putFloat("PawTracker4B.yCurveFactor",yCurveFactor);
    }
    public float getYCurveFactor() {
        return yCurveFactor;
    }
    
    
    
    
    public void setColorizeFactor(float colorizeFactor) {
        this.colorizeFactor = colorizeFactor;
        paw3DTracker.setColorizeFactor( colorizeFactor);
        getPrefs().putFloat("PawTracker4B.colorizeFactor",colorizeFactor);
    }
    public float getColorizeFactor() {
        return colorizeFactor;
    }
    
    public void setShadowFactor(float shadowFactor) {
        this.shadowFactor = shadowFactor;
        paw3DTracker.setShadowFactor( shadowFactor);
        getPrefs().putFloat("PawTracker4B.shadowFactor",shadowFactor);
    }
    public float getShadowFactor() {
        return shadowFactor;
    }
    
    public void setZFactor(int zFactor) {
        this.zFactor = zFactor;
        paw3DTracker.setZFactor( zFactor);
        getPrefs().putInt("PawTracker4B.zFactor",zFactor);
    }
    public int getZFactor() {
        return zFactor;
    }
    
    public void setValueMargin(float valueMargin) {
        this.valueMargin = valueMargin;
        paw3DTracker.setValueMargin( valueMargin);
        getPrefs().putFloat("PawTracker4B.valueMargin",valueMargin);
    }
    public float getValueMargin() {
        return valueMargin;
    }
    
    
    public void setColorizePeriod(int colorizePeriod) {
        this.colorizePeriod = colorizePeriod;
        paw3DTracker.setColorizePeriod( colorizePeriod);
        getPrefs().putInt("PawTracker4B.colorizePeriod",colorizePeriod);
    }
    public int getColorizePeriod() {
        return colorizePeriod;
    }
    
    
    
    public void setShowZColor(boolean showZColor){
        this.showZColor = showZColor;
        paw3DTracker.setShowZColor( showZColor);
        getPrefs().putBoolean("PawTracker4B.showZColor",showZColor);
    }
    public boolean isShowZColor(){
        return showZColor;
    }
    
    public void setShowYColor(boolean showYColor){
        this.showYColor = showYColor;
        paw3DTracker.setShowYColor( showYColor);
        getPrefs().putBoolean("PawTracker4B.showYColor",showYColor);
    }
    public boolean isShowYColor(){
        return showYColor;
    }
    
    public void setShowXColor(boolean showXColor){
        this.showXColor = showXColor;
        paw3DTracker.setShowXColor( showXColor);
        getPrefs().putBoolean("PawTracker4B.showXColor",showXColor);
    }
    public boolean isShowXColor(){
        return showXColor;
    }
    
    public void setShowShadows(boolean showShadows){
        this.showShadows = showShadows;
        paw3DTracker.setShowShadows( showShadows);
        getPrefs().putBoolean("PawTracker4B.showShadows",showShadows);
    }
    public boolean isShowShadows(){
        return showShadows;
    }
    public void setShowCorner(boolean showCorner){
        this.showCorner = showCorner;
        paw3DTracker.setShowCorner( showCorner);
        getPrefs().putBoolean("PawTracker4B.showCorner",showCorner);
    }
    public boolean isShowCorner(){
        return showCorner;
    }
    
      public void setCorrectY(boolean correctY){
        this.correctY = correctY;
        paw3DTracker.setCorrectY( correctY);
        getPrefs().putBoolean("PawTracker4B.correctY",correctY);
    }
    public boolean isCorrectY(){
        return correctY;
    }
    
    
    
    public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
        paw3DTracker.setCube_size( cube_size);
        getPrefs().putInt("PawTracker4B.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }
    
    private void initPawTracker3DStatic(){
        paw3DTracker.setPlaneAngle( planeAngle);
        paw3DTracker.setAlpha( alpha );
        paw3DTracker.setIntensity( intensity);
        paw3DTracker.setRay_length( ray_length);
        paw3DTracker.setDecayTimeLimit( decayTimeLimit );
        paw3DTracker.setValueThreshold( valueThreshold);
        
        paw3DTracker.setShowSkeletton( showSkeletton );
        paw3DTracker.setShowSecondFilter(showSecondFilter );
        paw3DTracker.setShowTopography( showTopography);
        paw3DTracker.setShowPalm( showPalm);
        
        paw3DTracker.setShowAcc( showAcc);
        paw3DTracker.setShowOnlyAcc( showOnlyAcc);
        paw3DTracker.setShowDecay( showDecay);
        
        paw3DTracker.setShowCage( showCage );
        paw3DTracker.setShowFrame( showFrame);
        paw3DTracker.setShowCorner(showCorner);
        paw3DTracker.setShowWindow( showWindow);
        paw3DTracker.setShowScore( showScore);
        paw3DTracker.setShowFingers( showFingers);
        
        paw3DTracker.setShowFingerTips( showFingerTips);
        paw3DTracker.setShowZones( showZones);
        paw3DTracker.setShowAll( showAll);
        paw3DTracker.setShowShape(showShape );
        paw3DTracker.setShowShapePoints( showShapePoints);
        paw3DTracker.setShowFingerPoints( showFingerPoints);
        
        
        paw3DTracker.setShowRLColors( showRLColors);
        paw3DTracker.setShowAxes( showAxes);
        paw3DTracker.setShowShadows( showShadows);
        
        paw3DTracker.setObj_xa( obj_xa);
        paw3DTracker.setObj_ya( obj_ya);
        paw3DTracker.setObj_xb( obj_xb);
        paw3DTracker.setObj_yb( obj_yb);
        
        paw3DTracker.setObj_sizea( obj_sizea);
        paw3DTracker.setObj_sizeb( obj_sizeb);
        
        paw3DTracker.setYLeftCorrection( yLeftCorrection);
        paw3DTracker.setYRightCorrection( yRightCorrection);
        
        paw3DTracker.setBrightness( brightness );
        
        paw3DTracker.setShowXColor( showXColor );
        paw3DTracker.setShowYColor( showYColor );
        paw3DTracker.setShowZColor( showZColor );
        
        paw3DTracker.setColorizeFactor( colorizeFactor );
        paw3DTracker.setCube_size( cube_size);
        
        paw3DTracker.setShadowFactor( shadowFactor);
        paw3DTracker.setZFactor( zFactor);
        
        paw3DTracker.setEvent_strength( event_strength);
        paw3DTracker.setDecayOn( decayOn);
        paw3DTracker.setValueMargin( valueMargin);
        paw3DTracker.setScaleAcc( scaleAcc);
        
        paw3DTracker.setDoor_xa( door_xa);
        paw3DTracker.setDoor_xb( door_xb);
        paw3DTracker.setDoor_ya( door_ya);
        paw3DTracker.setDoor_yb( door_yb);
        paw3DTracker.setDoor_z( door_z);
        
        paw3DTracker.setYCurveFactor( yCurveFactor);
        paw3DTracker.setShowCorrectionMatrix( showCorrectionMatrix);
        paw3DTracker.setCorrectY( correctY);
        
        paw3DTracker.setUseDualFilter( useDualFilter);
        
        paw3DTracker.setLowFilter_density( lowFilter_density);
        paw3DTracker.setLowFilter_density2( lowFilter_density2);
        paw3DTracker.setLowFilter_radius( lowFilter_radius);
        paw3DTracker.setLowFilter_radius2( lowFilter_radius2);
    }
    
}
