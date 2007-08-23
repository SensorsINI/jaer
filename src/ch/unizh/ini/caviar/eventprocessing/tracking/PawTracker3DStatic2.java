/*
 * PawTracker3DStatic2.java
 
 *
 * Paul Rogister, Created on August, 2007
 *
 */



package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.InputEvent;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import java.io.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.media.opengl.glu.GLU;


import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

//import ch.unizh.ini.caviar.eventprocessing.tracking.PawTracker4.EventPoint;
//import ch.unizh.ini.caviar.eventprocessing.tracking.PawTracker4.EventPoint.*;

/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class PawTracker3DStatic2 { //extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 0;
    protected final int LEFT = 1;
    
    public final static PawTracker3DStatic2 INSTANCE = new PawTracker3DStatic2();
    
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
    
    private float planeAngle;
    private float intensity;
    private float alpha;
    private int ray_length;
    
    
    
    
    private int door_z;
    private int door_xa;
    private int door_xb;
    private int door_ya;
    private int door_yb;
    
    private int retinaSize=128;
    private int retina3DSize = 182; //sqrt((128*128)*2)
    private int linkSize;
    private int segSize;
    
    private int maxSegSize;
    private int minZeroes;
    private int maxZeroes;
    private int in_length;
    private int in_test_length;
    
    private float line_threshold;
    private int score_threshold;
    private float score_in_threshold;
    private float score_sup_threshold;
    private float line2shape_thresh;
    private int boneSize;
    private int score_range;
    private int line_range;
    private int lines_n_avg;
    
    
    
    private int cluster_lifetime;
    private int intensityZoom=4;
    
    private float brightness=1;
    private float shapeLimit;
    private float shapeThreshold;
    
    private float shapeDLimit;
    private float shapeDThreshold;
    
    private float doorMinDiff;
    private float doorMaxDiff;
    private float finger_cluster_range;
    private float finger_ori_variance;
    
    private float node_range;
    
    private float palmback_below;
    private float palmback_value;
    private int palmback_distance;
    private int finger_start_threshold;
    
    private int finger_length;
    private float finger_mv_smooth;
    private float finger_sensitivity;
    private float tracker_time_bin;
    private float contour_act_thresh;
    private float contour_min_thresh;
    private int contour_range;
    private int densityMinIndex;
    private int densityMaxIndex;
    private boolean showDensity=false;
    private boolean scaleInDoor=false;
    private boolean showSegments=false;
    private boolean showFingers=false;
    private boolean showFingerTips=false;
    private boolean showClusters=false;
    
    
    private boolean showZones=false;
    private boolean showAll=false;
    // hide activity inside door
    private boolean hideInside=false;
    // show intensity inside shape
    private boolean useIntensity=false;
    
    private boolean showAcc=true;
    private boolean showOnlyAcc=true;
    private boolean showDecay=false;
    
    
    
    private boolean scaleIntensity=false;
    private boolean scaleAcc=false;
    
    
    
    private boolean showCage=true;
    private boolean showFrame=true;
    private boolean showCorner=true;
    private boolean showWindow=true;
    private boolean showScore=false;
    
    
    private boolean useSimpleContour=false;
    private boolean useFingerDistanceSmooth=false;
    private boolean showShapePoints=false;
    private boolean showFingerPoints=false;
    
    private boolean correctY=false;
    
    
    
    private boolean showRLColors=false;
    private boolean showAxes=true;
    
    
    private boolean showShape=false;
    
    private boolean smoothShape=false;
    
    private int lowFilter_radius;
    private int lowFilter_density;
    private float lowFilter_threshold;
    
    private int lowFilter_radius2;
    private int lowFilter_density2;
    
    private boolean useDualFilter=false;
    private boolean useLowFilter=false;
    
    
    private boolean thinning=false;
    private float thin_threshold;
    private boolean showThin=false;
    private boolean showPalm=false;
    private boolean showSkeletton=false;
    private boolean showSecondFilter=false;
    private boolean showTopography=false;
    
    private boolean resetPawTracking=false;
    private boolean validateParameters=false;
    
    private float event_strength;
    private float intensity_strength;
    private float intensity_threshold;
    private float in_value_threshold;
    private float sk_threshold;
    
    private float sk_radius_min;
    private float sk_radius_max;
    
    private float finger_border_mix;
    private float finger_tip_mix;
    private int finger_surround;
    private int finger_creation_surround;
    
    private int intensity_range;
    
    private int decayTimeLimit;
    private boolean decayOn=false;
    
    
    private boolean showCorrectionMatrix;
    
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // Global variables
    
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    
    // warning!!, nullified :
    protected float scoresFrame[][] = null;//new float[retinaSize][retinaSize];
    protected FingerCluster[] fingers = null; //new FingerCluster[MAX_NB_FINGERS];
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    int currentTime = 0;
    int leftTime = 0;
    int rightTime = 0;
    
    private int adjustTime = 0;
    private float valueThreshold = 0.0f;
    private float stereoThreshold = 0.5f;
    private int availableID=0;
    
    
    private int cage_depth = 30;
    
    private int obj_xa = 0;
    private int obj_ya = 0;
    private int obj_xb = 0;
    private int obj_yb = 0;
    private int obj_sizea = 0;
    private int obj_sizeb = 0;
    
    private int cube_size = 1;
    private int zFactor = 1;
    
    
    
    private float valueMargin = 0.3f;
    
    
    private float shadowFactor = 0.3f;
    private float colorizeFactor = 0.1f;
    private int colorizePeriod = 183;
    
    private boolean showXColor = false;
    private boolean showYColor = false;
    private boolean showZColor = false;
    
    private boolean showShadows = false;
    
    
    
    
    private float yCurveFactor = 0;
    
    private int yRightCorrection = 0;
    private int yLeftCorrection = 0;
    
    boolean windowDeleted = true;
    
    boolean resetEnabled = true;
    
    
    private int redBlueShown=0;
    
    Event3DPoint[][][] points3D = new Event3DPoint[retina3DSize][retinaSize][retina3DSize];
    
    /** additional classes */
    
    /** EventPoint : all data about a point in retina space */
    public class EventPoint{
        
        
        // 3D variables, to rename
        // left
        // int count;
        int disparityLink = -1;
        int prevDisparityLink = -1;
        // right
        // int accumulation;
        float free;
        int attachedTo = -1;
        
        
        int updateTime; // time of current update
        int previousUpdate; // time of previous update
        
        
        float previousShortFilteredValue = 0;
        float previousDecayedFilteredValue = 0;
        
        float decayedFilteredValue;         // third low pass filter with decaying values
        float previousValue=0;             // last contract value
        float lastValue=0;                // last contract value
        float accValue=0;                // accumulated contrast value
        boolean linkPawBack = false;    // when finger is attached to paw back at this point
        boolean onFingerLine = false;  // if is part of a finger line
        boolean isSkeletton = false;  // true if part of the skeletton of paw obtained by thinnning
        boolean isEndNode = false;   // true if end of segment of skeletton points
        float shortFilteredValue;   // short range topographic filter
        float largeFilteredValue;  // large range topographic filter
        boolean border;           // true if point is on the paw's border
        //    Integer group;           // contour group to which this point belongs, group 1 is touching door
        float intensity;        // value of intensity of point, projected from border point toward inside, to detect
        // convex borders and possible finger points
        float fingerScore;    // likelyhood score of this point being a fingertip, obtained by template matching
        int zerosAround;     // number of low value neighbours for border matching
        int zerosDAround;   // number of low value neighbours for border matching for decayed filter
        
        float gcx;        // float coordinates for gc of zeroes around when point is border
        float gcy;
        
        float prev_gcx;        // previous float coordinates for gc of zeroes around when point is border
        float prev_gcy;
        
        boolean decayedBorder=false; // is a border computed on decayed filtered value
        
        int skValue;     // Skeletton value (?)
        //Finger finger; // finger
        int x;
        int y;
        int z;
        int z0;
        
        public EventPoint(  ){
            
        }
        
        
        public EventPoint( TypedEvent e ){
            previousValue = lastValue;
            int type=e.getType();
            float step = event_strength / (colorScale + 1);
            lastValue = step * (type - grayValue);
            
            accValue +=  lastValue;
            
            // keep in range [0-1]
            if(accValue<0)accValue=0;
            else if(accValue>1)accValue=1;
            
            updateTime = e.timestamp;
            x = e.x;
            y = e.y;
        }
        
        public EventPoint( int x, int y ){
            this.x = x;
            this.y = y;
        }
        
        public void updateFrom( TypedEvent e, int x, int y ){
            previousValue = lastValue;
            int type=e.getType();
            float step = event_strength / (colorScale + 1);
            lastValue = step * (type - grayValue);
            
            //  accValue +=  lastValue;
            if (decayOn) accValue =  lastValue + getAccValue(e.timestamp);
            else accValue +=  lastValue;
            
            if(scaleAcc){
                // keep in range [0-1]
                
                if(accValue<0)accValue=0;
                else if(accValue>1)accValue=1;
            }
            
            
            updateTime = e.timestamp;
            this.x = x;
            this.y = y;
        }
        
        
        public float getValue( int currentTime ){
            if(useDualFilter){
                if (decayOn) return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
                else return shortFilteredValue;
            } else {
                if (decayOn) return accValue-decayedValue(accValue,currentTime-updateTime);
                else return accValue;
            }
        }
        
        
        public float getAccValue( int currentTime ){
            if (decayOn) return accValue-decayedValue(accValue,currentTime-updateTime);
            else return accValue;
        }
        
        public float getFreeValue( int currentTime ){
            if (decayOn) return free-decayedValue(free,currentTime-updateTime);
            else return free;
        }
        
        public float getShortFilteredValue( int currentTime ){
            return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
        }
        
        public float getDecayedFilteredValue( int currentTime ){
            return decayedFilteredValue-decayedValue(decayedFilteredValue,currentTime-updateTime);
        }
        
        public float getPreviousShortFilteredValue( int currentTime ){
            return previousShortFilteredValue-decayedValue(previousShortFilteredValue,currentTime-previousUpdate);
        }
        
//
//          float decayedValue( float value, int time ){
//              float res=0;
//              float dt = (float)time/(float)decayTimeLimit;
//              if(dt<1){
//                  res = value * dt;
//              }
//              return res;
//          }
        
        
        void addToGc(int x, int y){
            if(zerosAround>0){
                float z = 1/zerosAround;
                gcx = (1-z)*gcx + z*x;
                gcy = (1-z)*gcy + z*y;
                //gcx = x;
                // gcy = y;
            } else {
                // System.out.println("addToGc zerosAround: "+zerosAround);
                gcx = x;
                gcy = y;
            }
        }
        
        void remFromGc( int x, int y){
            if(zerosAround>0){
                float z = 1/zerosAround;
                //  gcx += z*(gcx - x);
                //   gcy += z*(gcy - y);
            } else {
                // gcx = 0;
                //  gcy = 0;
            }
        }
        
        void resetGc(){
            gcx = 0;
            gcy = 0;
            prev_gcx = 0;
            prev_gcy = 0;
        }
        
        void setGc( EventPoint[][] eventPoints){
            // for all neighbours
            int i;
            int j;
            int nb = 0;
            for (i=x-1; i<x+2;i++){
                if(i>=0&&i<retinaSize){
                    for (j=y-1; j<y+2;j++){
                        if(j>=0&&j<retinaSize){
                            EventPoint surroundPoint = eventPoints[i][j];
                            
                            if(surroundPoint.shortFilteredValue>shapeLimit){
                                gcx += i;
                                gcy += j;
                                nb++;
                            }
                        }
                    }
                }
            }
            
            
            gcx = gcx/nb;
            gcy = gcy/nb;
        }
        
        
        void setPrevGc(){
            prev_gcx = gcx;
            prev_gcy = gcy;
        }
        
    }
    
    
    
    /** Event3DPoint : all data about a point in 3D space */
    
    
    
    private class Event3DPoint {
        int lx;
        int ly;
        int rx;
        int ry;
        int x;
        int y;
        int z;
        float lvalue;
        float rvalue;
        float intensity;
        //  float value;
        int rightTime;
        int leftTime;
        
        boolean activated = false;
        
        public Event3DPoint( int x, int y, int z ){
            
            
            this.x = x;
            this.y = y;
            this.z = z;
            
            lvalue = 0;
            rvalue = 0;
            rightTime = 0;
            leftTime = 0;
            intensity = 0;
        }
        
        
        public Event3DPoint( int x, int y, int z, int ox, int oy, boolean left ){
            activated = true;
            if(left){
                lx = ox;
                ly = oy;
                lvalue=0.5f;
            } else {
                rx = ox;
                ry = oy;
                rvalue=0.5f;
            }
            
            this.x = x;
            this.y = y;
            this.z = z;
            
            rightTime = 0;
            leftTime = 0;
            
        }
        
        public void reset( boolean left){
            if(left) {
                lvalue=0;
                lx = 0; ly = 0;
                
            } else {
                rvalue=0;
                rx = 0; ry = 0;
                
            }
            rightTime = 0;
            leftTime = 0;
            intensity = 0;
        }
        
    } //end class Event3DPoint
    
    
    
    private class FingerCluster{
        
        int tip_x_end = 0; //end tip
        int tip_y_end = 0;
        
        int x=0;
        int y=0;
        
        int x1=0;
        int y1=0;
        
        int x2=0;
        int y2=0;
        
        
        boolean activated = false;
        
       /*
        int tip_x_start = 0; // beginning  tip
        int tip_y_start = 0;
        int base_x_start = 0; // beginning first joint
        int base_y_start = 0;
        int base_x_end = 0; // end first joint
        int base_y_end = 0;
        
        
        int length_tip=0;
        int length_base=0;
        float orientation_tip=0;
        float orientation_base=0;
        float direction_tip=0;
        float direction_base=0;
        int lifetime;
        
        boolean assigned = false;
        
        
        
        FingerCluster neighbour1 = null;
        FingerCluster neighbour2 = null;
        
        **/
        
        public FingerCluster(  ){
            
            
        }
        
        
        public FingerCluster( int x, int y ){
            activated = true;
            
            tip_x_end = x; //end tip
            tip_y_end = y;
            this.x = x;
            this.y = y;
        }
        
        public void reset(){
            
            activated = false;
            
            
            
            tip_x_end = 0; //end tip
            tip_y_end = 0;
        }
        
        public void add( int x, int y, float mix){
            
            // memorize previous values
            
            // System.out.println("add ["+x+","+y+"] to ["+tip_x_end+","+tip_y_end+"] range: "+finger_surround);
            
            tip_x_end = Math.round(tip_x_end*mix + x*(1-mix));
            tip_y_end = Math.round(tip_y_end*mix + y*(1-mix));
            // if not previously assigned this grasp :
            
            
            this.x = Math.round(tip_x_end*0.1f + this.x*(0.9f));
            this.y = Math.round(tip_y_end*0.1f + this.y*(0.9f));
            
            
            //   System.out.println("new pos: ["+tip_x_end+","+tip_y_end+"] mix: "+mix);
            
            
           /*
            assigned = true;
            activated = true;
            lifetime = cluster_lifetime;
            **/
        }
        
        
        void updateLine(EventPoint[][] eventPoints){
            
            Vector somelines = longestLines(x,y,finger_length,lines_n_avg, eventPoints);
            
            
            // x1 = ((Line)somelines.elementAt(0)).x1;
            //  y1 = ((Line)somelines.elementAt(0)).y1;
            
            
            // compute mean line
            Line meanLine = meanLineOf(somelines);
            // lines.addAll(somelines);
            if (meanLine!=null) {
                if(x1==0) x1 = meanLine.x1; else
                    x1 = Math.round(x1*finger_mv_smooth + meanLine.x1*(1-finger_mv_smooth));
                if(y1==0) y1 = meanLine.y1; else
                    y1 = Math.round(y1*finger_mv_smooth + meanLine.y1*(1-finger_mv_smooth));
                
                Line line2 = shortestLineToValue(x1,y1, palmback_distance, palmback_below, palmback_value,eventPoints);
                
                
                if(line2.x1==0){
                    x2 = x1;
                } else {
                    if(x2==0) x2 = line2.x1; else
                        x2 = Math.round(x2*finger_mv_smooth + line2.x1*(1-finger_mv_smooth));
                }
                if(line2.y1==0){
                    y2 = y1;
                } else {
                    if(y2==0) y2 = line2.y1; else
                        y2 = Math.round(y2*finger_mv_smooth + line2.y1*(1-finger_mv_smooth));
                }
                
                
                
            } else {
                x1 = x; y1 = y;
                x2 = x; y2 = y;
            }
            
            
        }
        
        
        void updateLine2(EventPoint[][] eventPoints){
            Line line1 = null;
            if(eventPoints[x][y].shortFilteredValue>shapeLimit){
                // if(eventPoints[tip_x_end][tip_y_end].shortFilteredValue>shapeLimit){
                
                
                // finger cluster attached
                // trace line to skeletton
                
                line1 = mostValuableLine(x,y,finger_length,eventPoints);
                
            } else {
                
                if(eventPoints[x1][y1].shortFilteredValue>shapeLimit){
                    // finger cluster a bit lost
                    line1 = lineFromShapeBorder(x1,y1, finger_length, lineDirection(new Line(x1,y1,x,y)), line2shape_thresh,eventPoints);
                    if(line1.length>0){
                        tip_x_end = line1.x0;
                        tip_y_end = line1.y0;
                        x = line1.x0;
                        y = line1.y0;
                    }
                } else {
                    line1 = lineFromShapeBorder(x2,y2, finger_length, lineDirection(new Line(x2,y2,x1,y1)), line2shape_thresh,eventPoints);
                    if(line1.length>0){
                        tip_x_end = line1.x0;
                        tip_y_end = line1.y0;
                        x = line1.x0;
                        y = line1.y0;
                        // x1 = x;
                        // y1 = y;
                    }
                }
                
                
            }
            
            if (line1!=null) {
                if(x1==0) x1 = line1.x1; else
                    x1 = Math.round(x1*finger_mv_smooth + line1.x1*(1-finger_mv_smooth));
                if(y1==0) y1 = line1.y1; else
                    y1 = Math.round(y1*finger_mv_smooth + line1.y1*(1-finger_mv_smooth));
                
                Line line2 = shortestLineToValue(x1,y1, palmback_distance, palmback_below, palmback_value,eventPoints);
                
                
                if(line2.x1==0){
                    // x2 = x1;
                } else {
                    if(x2==0) x2 = line2.x1; else
                        x2 = Math.round(x2*finger_mv_smooth + line2.x1*(1-finger_mv_smooth));
                }
                if(line2.y1==0){
                    // y2 = y1;
                } else {
                    if(y2==0) y2 = line2.y1; else
                        y2 = Math.round(y2*finger_mv_smooth + line2.y1*(1-finger_mv_smooth));
                }
                
                
                
            } else {
                //  x1 = x; y1 = y;
                // x2 = x; y2 = y;
            }
            
            
            
            
        }//end updateline2
        
    } // end class FingerCluster
    
    
    // array of event points for all computation
    protected EventPoint leftPoints[][];
    protected EventPoint rightPoints[][];
    
    protected int correctionMatrix[][];
    //   protected EventPoint eventPoints[][];
    
    
    
    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    float[] densities = new float[lowFilter_density];
    float[] densities2 = new float[lowFilter_density2];
    
    float largeRangeTotal;
    float shortRangeTotal;
    
    float shortFRadius;
    float largeFRadius;
    int shortRadiusSq;
    int largeRadiusSq;
    
    float invDensity1;
    float invDensity2;
    
    
    /** old PawTracker2 parameters : */
    
    protected int nbFingersActive = 0;
    
    protected float scoresFrameMax;
    
    private boolean activity_started = false;
    private boolean grasp_started = false;
    //protected float startPoint = 0f;
    // protected float stopPoint = 0f;
    
    
    /** Creates a new instance of PawTracker */
    public PawTracker3DStatic2() {
        
        // this.chip=chip;
        //renderer=(AEChipRenderer)chip.getRenderer();
        
        //  initFilter();
        //resetPawTracker();
        //  validateParameterChanges();
        System.out.println("Build PawTracker3DStatic2");
        // reset(points3D);
        System.out.println("End of building PawTracker3DStatic2");
    }
    
    void reset(Event3DPoint[][][] points3D){
        for(int x=0;x<retina3DSize;x++){
            for(int y=0;y<retinaSize;y++){
                for(int z=0;z<retina3DSize;z++){
                    points3D[x][y][z]= new Event3DPoint(x,y,z);
                    
                }
            }
        }
    }
    
    
    synchronized public int register( AEChip chip){
        this.chip=chip;
        availableID++;
        if(availableID>2)availableID=1;
        return availableID;
    }
    
    public void initFilter() {
        initDefaults();
    }
    
    private void initDefaults(){
        //System.out.println("initDefaults");
        
//        initDefault("PawTracker3.","");
    }
    
    
    private void resetCorrectionMatrix(){
        correctionMatrix = new int[retinaSize][retinaSize];
        for (int i=0; i<correctionMatrix.length; i++){
            for (int j=0; j<correctionMatrix[i].length; j++){
                correctionMatrix[i][j] = -1;
            }
        }
    }
    
    private void createCorrectionMatrix(){
        resetCorrectionMatrix();
        for(int y=0;y<retinaSize;y++){
            for(int i=0;i<retinaSize;i++){
                for(int j=0;j<retinaSize;j++){
                    if (correctionMatrix[i][j]==-1){
                        int jh = j - retinaSize/2;
                        if(jh<=yParabolic(y,i)){
                            // if(y==64&&jh==0) System.out.println("then yparabolic jh:"+jh);
                            correctionMatrix[i][j]=y;
                        }
                    }
                }
            }
            
        }
    }
    
    // return yc of point xc,yc on parabolic of factor y
    private int yParabolic(int y, int xc){
        
        int yh = y-retinaSize/2;
        int xh = xc-retinaSize/2;
        float yc = yCurveFactor/10*(yh)*xh*xh + yh;
        //   if(yh==0) System.out.println("yparabolic y:"+y+" xc:"+xc+" yh:"+yh+" xh:"+xh+" yc:"+yc);
        return Math.round((float)yc);
    }
    
    synchronized public void reset(){
        if(resetEnabled){
            // reset(points3D);
            resetEnabled = false;
        }
        // grasp_started = false;
        //  activity_started = false;
        
        
        //System.out.println("resetPawTracker");
        leftPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<leftPoints.length; i++){
            for (int j=0; j<leftPoints[i].length; j++){
                leftPoints[i][j] = new EventPoint(i,j);
            }
        }
        rightPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<rightPoints.length; i++){
            for (int j=0; j<rightPoints[i].length; j++){
                rightPoints[i][j] = new EventPoint(i,j);
            }
        }
        
        validateParameterChanges();
        
        //   resetCorrectionMatrix();
        createCorrectionMatrix();
        
        // reset group labels (have a vector of them or.. ?
        
        // scoresFrame = new float[retinaSize][retinaSize];
        // fingers = new FingerCluster[MAX_NB_FINGERS];
        //  nbFingers = 0;
        //   setResetPawTracking(false);//this should also update button in panel but doesn't'
        
        
        
    }
    
    
    private void initDefault(String key, String value){
        // if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
    
    public void validateParameterChanges(){
        
        setValidateParameters(false); //should update gui
        // recompute densities
        densities = resetDensities(lowFilter_density);
        densities2 = resetDensities(lowFilter_density2);
        
        shortRangeTotal = computeRangeTotal(lowFilter_radius);
        largeRangeTotal = computeRangeTotal(lowFilter_radius2);
        
        shortFRadius = (float)lowFilter_radius;
        largeFRadius = (float)lowFilter_radius2;
        
        shortRadiusSq = lowFilter_radius*lowFilter_radius;
        largeRadiusSq = lowFilter_radius2*lowFilter_radius2;
        
        
        invDensity1 = 1/(float)lowFilter_density;
        invDensity2 = 1/(float)lowFilter_density2;
        
        
    }
    
    // there will be a border effect as this function will overestimate the total when total applied to retina's border points
    public float computeRangeTotal( int radius ){
        float total = 0;
        float f;
        float dr;
        float dist;
        float radiusSq = radius*radius;
        float fradius = (float)radius;
        for (int is=-radius; is<radius+1;is++){
            for (int js=-radius; js<radius+1;js++){
                
                dist = (is*is) + (js*js);
                // if circle uncomment: // if(dist<radius2Sq){
                
                if(dist<radiusSq){
                    f = 1;
                    dr = (float)Math.sqrt(dist)/fradius;
                    
                    if (dr!=0) f = 1/dr;
                    total+=f;
                }
            }
        }
        return total;
    }
    
    
    private void left_add( int x, int y ){
        //leftPoints[x][y].count++; // change to
    }
    
    private void left_rem( int x, int y ){
        int yl = y;// + yLeftCorrection;
        int yr = y;// + yRightCorrection;
        
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        int ax = leftPoints[x][yl].disparityLink;
        if (ax>-1) {
            // rightPoints[ax][yr].free+=leftPoints[ax][yl].getAccValue(leftTime); //?
            
            //  if(rightPoints[ax][yr].getFreeValue(rightTime)>=rightPoints[ax][yr].getAccValue(rightTime)){
            //    rightPoints[ax][yr].attachedTo = -1;
            //  }
            rightPoints[ax][yr].attachedTo = -1;
       //     rightPoints[ax][yr].free=rightPoints[ax][yr].getValue(rightTime);
            
            leftPoints[x][yl].prevDisparityLink = leftPoints[x][yl].disparityLink;
            leftPoints[x][yl].disparityLink = -2;
        } else {
            leftPoints[x][y].disparityLink = -2; //delete
        }
    }
    
    private void right_add( int x, int y ){
        int yr = y;// + yRightCorrection;
        if(yr<0||yr>=retinaSize)return;
     //   rightPoints[x][yr].free=rightPoints[x][yr].getValue(rightTime);
        //rightPoints[x][y].accumulation=rightPoints[ax][y].getCurrentValue(;
    }
    
    private void right_rem( int x, int y ){
        int yl = y;// + yLeftCorrection;
        int yr = y;//+ yRightCorrection;
        
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        
        // if (rightPoints[x][yr].free>0)
        
       // rightPoints[x][yr].free=rightPoints[x][yr].getValue(rightTime);
        //      if (rightPoints[x][y].getCurrentValue()>0) rightPoints[x][y].accumulation--;
        if (rightPoints[x][yr].getValue(rightTime)<=valueThreshold){
            int ax = rightPoints[x][yr].attachedTo;
            if(ax>-1){
                leftPoints[ax][yl].prevDisparityLink = x;
                leftPoints[ax][yl].disparityLink = -2; //delete
                rightPoints[x][yr].attachedTo = -1;
            }
            
        }
    }
    
    private void right_check( int x, int y ){
        
        int yl = y;// + yLeftCorrection;
        int yr = y;// + yRightCorrection;
        
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        
        
        boolean done  = false;
        int ax = 0;
        for(int i=0;i<rightPoints.length;i++){
            if(done) break;
            
            if((rightPoints[i][yr].getValue(rightTime)>valueThreshold)&&
                    ((rightPoints[i][yr].getValue(rightTime)<=leftPoints[x][yl].getValue(leftTime)+valueMargin)
                    &&(rightPoints[i][yr].getValue(rightTime)>=leftPoints[x][yl].getValue(leftTime)-valueMargin))){
                ax = rightPoints[i][yr].attachedTo;
                if(ax>-1){
                    if(leftPoints[ax][yl].getValue(leftTime)<valueThreshold){
                        leftPoints[ax][yl].prevDisparityLink = i;
                        leftPoints[ax][yl].disparityLink = -2;
                        rightPoints[i][yr].attachedTo = -1;
                        ax = -1;
                        rightPoints[i][yr].attachedTo = -1;
                    }
                }
                if(ax>x||ax==-1){
                    rightPoints[i][yr].attachedTo = x;
                    
                    
                    if(leftPoints[x][yl].disparityLink>-1){
                        rightPoints[leftPoints[x][yl].disparityLink][yr].attachedTo = -1;
                    }
                    leftPoints[x][yl].disparityLink = i;
                    //   if(rightPoints[i][yr].getFreeValue(rightTime)>0){
                    //rightPoints[i][y].free-=leftPoints[ax][y].getCurrentValue();
                    //   rightPoints[i][yr].free=0;
                    // detach previous left
                    
                    //  }
                    if (ax>0){
                        leftPoints[ax][yl].prevDisparityLink = i;
                        leftPoints[ax][yl].disparityLink = -2;
                    }
                    done = true;
                }
            }
        } // end for
        
        if(done&&ax!=-1) right_check(ax,y);
        
    }
    
    
    private void left_check( int y ){
        int yl = y;// + yLeftCorrection;
        
        if(yl<0||yl>=retinaSize)return; //to remove
        
        // look for unassigned left events
        boolean done  = false;
        for(int i=0;i<leftPoints.length;i++){
            if(done) break;
            if ((leftPoints[i][yl].getValue(leftTime)>valueThreshold)){//&&(leftPoints[i][yl].disparityLink<0)){
                
                right_check(i,y);
                done = true;
            }
            
        }
        
        
    }
    
    
    private void processDisparity( int leftOrRight, int x, int y, float value, float previous_value ){ // + event
        int type = 0;
        if(previous_value<value){
            type = 1;
        }
        if(leftOrRight==LEFT){
            //  if(showShadows)  System.out.println("3D Static track called from left "+leftOrRight);
            
            // intput
            
            // dy = yLeftCorrection;
            
            //   leftPoints[e.x][cy].updateFrom(e); // to change
            
            //  if (leftPoints[e.x][e.y].accValue>0)
            // System.out.println("3D Static track called from left "+leftOrRight+" :"+leftPoints[e.x][e.y].accValue);
            //    System.out.println("3D Static left event typed: "+type);
            boolean change = false;
            
            //  value = leftPoints[e.x][cy].getAccValue(leftTime);
            
            if(type==1){
                // increase lx count
                if(value>valueThreshold){
                    
                    change = true;
                }
                
            } else { //remove event
                if(value<=valueThreshold){
                    left_rem( x, y );
                    change = true;
                }
                
            }
            // call right check
            if (change) right_check(x,y);
            
        } else {
            //   if(showShadows) System.out.println("3D Static track called from right "+leftOrRight);
            
            //dy = yRightCorrection;
            
            
            
            //if (rightPoints[e.x][e.y].accValue>0)
            //     System.out.println("3D Static track called from left "+leftOrRight+" :"+rightPoints[e.x][e.y].accValue);
            //    System.out.println("3D Static right event typed: "+type);
            boolean change = false;
            //     value = rightPoints[e.x][cy].getAccValue(rightTime);
            
            if(type==1){
                
                // increase lx count
                right_add( x, y );
                if(value>valueThreshold){
                    
                    change = true;
                }
            } else { //remove event
                if(value<=valueThreshold){
                    right_rem( x, y );
                    change = true;
                }
                
            }
            if (change) left_check(y);
        }
        
        
        
    }
    
    
    // the method that actually does the tracking
    synchronized protected void track(TypedEvent e, int leftOrRight){
        
        // resetEnabled = true;
        
        
        //  System.out.println("3D Static track called from "+leftOrRight);
        
        int type=e.getType();
        
        
        int dy = e.y;
        if(leftOrRight==LEFT){
            dy += yLeftCorrection;
            leftTime = e.timestamp;
        } else {
            dy += yRightCorrection;
            rightTime = e.timestamp;
        }
        
        //int cy = e.y;
        int cy = dy;
        if(dy>0&&dy<retinaSize){
            if(correctY){
                cy = correctionMatrix[e.x][cy];
            }
        } else return;
        
        // call
        float value = 0;
        if(leftOrRight==LEFT){
            leftPoints[e.x][cy].updateFrom(e,e.x,cy);
            
            // filter
            if(useDualFilter){
                fastDualLowFilterFrame(leftPoints[e.x][cy],leftPoints, leftOrRight);
            } else {
                value = leftPoints[e.x][cy].getAccValue(leftTime);
                processDisparity( leftOrRight, e.x, cy,  value, type );
            }
        } else {
            rightPoints[e.x][cy].updateFrom(e,e.x,cy);
            
            // filter
            if(useDualFilter){
                fastDualLowFilterFrame(rightPoints[e.x][cy],rightPoints, leftOrRight);
            } else {
                value = rightPoints[e.x][cy].getAccValue(rightTime);
                processDisparity( leftOrRight, e.x, cy,  value, type );
            }
        }
        
        
        // end call
        
        if(rightTime>leftTime){
            this.currentTime = rightTime;
        } else {
            this.currentTime = leftTime;
        }
        
    }
    
    // the method that actually does the tracking
     /*
    synchronized protected void track(EventPoint[][] eventPoints, int currentTime, int leftOrRight){
      
       resetEnabled = true;
       checkInsideIntensityFrame();
       check3DFrame();
      
     //  System.out.println("3D Static track called from "+leftOrRight);
      
       if(leftOrRight==LEFT){
         //   System.out.println("3D Static track called from left "+leftOrRight+" :"+leftPoints);
      
      
      
           leftPoints = eventPoints;
      
      
            leftTime = currentTime;
            // commented out to try predefined shapes
            increase3DIntensityFrom(leftPoints,leftOrRight,leftTime);
      
        //    shape3DIntensity( obj_xa,obj_ya, obj_sizea, leftOrRight, leftTime);
      
       } else { // RIGHT
         //   System.out.println("3D Static track called from right "+leftOrRight+" :"+rightPoints);
      
            rightPoints = eventPoints;
      
            rightTime = currentTime;
            increase3DIntensityFrom(rightPoints,leftOrRight,rightTime);
      
            // to test with a predefined shape
           // shape3DIntensity( obj_xb,obj_yb, obj_sizeb, leftOrRight, rightTime);
      
       }
      
       if(rightTime>leftTime){
          this.currentTime = rightTime;
       } else {
          this.currentTime = leftTime;
       }
      
      
       if (showWindow) insideIntensityCanvas.repaint();
       if (showWindow&&!windowDeleted) a3DCanvas.repaint();
      
      
      
    }
      
      
      
      */
    
    
    
    synchronized protected void display( ){
        // reconstruct 3D image from arrays and display
        checkInsideIntensityFrame();
        check3DFrame();
        
        //   if(showShadows)
        //   System.out.println("rightTime: "+rightTime+" leftTime:"+leftTime+" currentTime:"+currentTime);
        
        if (showWindow) insideIntensityCanvas.repaint();
        if (showWindow&&!windowDeleted) a3DCanvas.repaint();
    }
    
    
    
    
    
    private void shape3DIntensity( int x0, int y0, int size, int leftOrRight, int time){
        if(leftOrRight==1){
            
            for(int x=x0-size/2; x<x0+size/2;x++){
                for(int y=y0-size/2; y<y0+size/2;y++){
                    // increase value of line along z axis
                    
                    increaseValueOf3DLine(x, y, 0.5f, 0, 0.5f,time,LEFT);
                }
            }
        } else {
            // increase value of line along rotated z axis
            for(int x=x0-size/2; x<x0+size/2;x++){
                for(int y=y0-size/2; y<y0+size/2;y++){
                    // increase value of line along z axis
                    
                    increaseValueOf3DLine(x, y, 0.5f, planeAngle, 0.5f,time,RIGHT);
                }
            }
            
            
        }
        
    }
    
    private void increase3DIntensityFrom( EventPoint[][] points, int leftOrRight, int time){
        if(leftOrRight==1){
            
            for(EventPoint[] eps:points){
                for(EventPoint ep:eps){
                    // increase value of line along z axis
                    if(ep.accValue>valueThreshold)
                        increaseValueOf3DLine(ep.x, ep.y, ep.accValue, 0, ep.accValue,time,LEFT);
                }
            }
        } else {
            // increase value of line along rotated z axis
            for(EventPoint[] eps:points){
                for(EventPoint ep:eps){
                    // increase value of line along z axis
                    if(ep.accValue>valueThreshold)
                        increaseValueOf3DLine(ep.x, ep.y,  ep.accValue, planeAngle, ep.accValue,time,RIGHT);
                }
            }
        }
        
    }
    
    
// cold optimize by only processing events if accumulated value above some threshold
    public void processTracking( EventPoint ep, EventPoint[][] eventPoints  ){
        
        
        
        
        
        // do a maximum in one pass :
        // for all neighbouring event poinrs in large range
        // update values from filter computation
        // when also in short range: apply low filter update
        //  fastDualLowFilterFrame(ep,eventPoints);
        
        
        
        
        
        
    }
    
    
    public String toString(){
        String s="PawTracker3";
        return s;
    }
    
    
    
    
    
    
    
    
    
    private class Line{
        
        int length;
        int x0;
        int y0;
        int x1;
        int y1;
        
        float orientation=0;
        
        public boolean touchesDoor = false;
        
        public Line(){
            length = 0;
            x0 = 0;
            y0 = 0;
            x1 = 0;
            y1 = 0;
            
        }
        public Line( int x0, int y0, int x1, int y1){
            
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }
        public Line( int x0, int y0, int x1, int y1, int length){
            this.length = length;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }
        
    }
    
    
    
    
    private class LineLengthComparer implements Comparator {
        public int compare(Object obj1, Object obj2) {
            int l1 = ((Line)obj1).length;
            int l2 = ((Line)obj2).length;
            
            return l2 - l1;
        }
    }
    
    
    protected float distanceBetween( int x1, int y1, int x2, int y2){
        
        double dx = (double)(x1-x2);
        double dy = (double)(y1-y2);
        
        float dist = (float)Math.sqrt((dy*dy)+(dx*dx));
        
        
        return dist;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    private int testFingerTipPattern( EventPoint ep, EventPoint[][] eventPoints ){
        int score = 0;
        
        int xInside = 2;
        int yInside = 2;
        int xOutside = 3;
        int yOutside = 3;
        int distance1 = 3;
        int distance2 = 7;
        int x = ep.x;
        int y = ep.y;
        
        // look for positive value around x,y
        //test points in an area around
        for (int i = x-xInside; i<x+xInside; i++){
            if(i>=0&&i<eventPoints.length){
                for (int j = y-yInside; j<y+yInside; j++){
                    if(j>=0&&j<eventPoints[i].length){
                        
                        if(eventPoints[i][j].intensity>score_sup_threshold){
                            score++;
                        }
                    }
                }
            }
        }
        
        // look for negative lines around
        for (int i = x-xOutside; i<x+xOutside; i++){
            if(i>=0&&i<eventPoints.length){
                
                if(y-distance1>=0){
                    if(eventPoints[i][y-distance1].intensity<score_in_threshold){
                        score++;
                    }
                }
                if(y+distance1<eventPoints.length){
                    if(eventPoints[i][y+distance1].intensity<score_in_threshold){
                        score++;
                    }
                    
                }
                
                if(y-distance2>=0){
                    if(eventPoints[i][y-distance2].intensity<score_in_threshold){
                        score++;
                    }
                }
                if(y+distance2<eventPoints.length){
                    if(eventPoints[i][y+distance2].intensity<score_in_threshold){
                        score++;
                    }
                    
                }
                
            }
        }
        for (int j = y-yOutside; j<y+yOutside; j++){
            if(j>=0&&j<eventPoints.length){
                
                if(x-distance1>=0){
                    if(eventPoints[x-distance1][j].intensity<score_in_threshold){
                        score++;
                    }
                }
                if(x+distance1<eventPoints.length){
                    if(eventPoints[x+distance1][j].intensity<score_in_threshold){
                        score++;
                    }
                    
                }
                if(x-distance2>=0){
                    if(eventPoints[x-distance2][j].intensity<score_in_threshold){
                        score++;
                    }
                }
                if(x+distance2<eventPoints.length){
                    if(eventPoints[x+distance2][j].intensity<score_in_threshold){
                        score++;
                    }
                    
                }
            }
            
        }
        // remove points with no support from actual real accumulated image
//        if(eventPoints[x][y].intensity<=score_sup_threshold){
//            score = 0;
//        }
        
        return score;
        //return (int) (score * fr[x][y][0] * 10);
    }
    
    
    
    
    
    
    
    
    // optimized to compute two borders, for shortFiltered and decayedFiltered
    public void isOnPawShape(EventPoint ep, int currentTime, EventPoint[][] eventPoints){
        
        int x = ep.x;
        int y = ep.y;
        
        ep.zerosAround = 0;
        ep.zerosDAround = 0;
        
        //ep.resetGc();
        boolean changedToLow = false;
        boolean changedToHigh = false;
        boolean changedDToLow = false;
        boolean changedDToHigh = false;
        boolean changed = false;
        boolean toThin = false;
        
        if(ep.previousDecayedFilteredValue<shapeDLimit){
            if(ep.getDecayedFilteredValue(currentTime)>=shapeDLimit){
                changedDToHigh = true;
            }
        } else {
            if(ep.getDecayedFilteredValue(currentTime)<shapeDLimit){
                changedDToLow = true;
            }
        }
        if(ep.previousShortFilteredValue<shapeLimit){
            if(ep.shortFilteredValue>=shapeLimit){
                changedToHigh = true;
            }
        } else {
            if(ep.shortFilteredValue<shapeLimit){
                changedToLow = true;
            }
        }
        
        // thinning
        // if(ep.shortFilteredValue>sk_threshold){
        //toThin = true;
        // non optimised
        thin_a(ep,eventPoints);
        thin_surround_a(ep,eventPoints);
        thin_b(ep,eventPoints);
        thin_surround_b(ep,eventPoints);
        
        
        //   }
        
        
        if(changedToHigh||changedToLow||changedDToHigh||changedDToLow){
            changed = true;
        }
        
        int i;
        int j;
        for (i=x-1; i<x+2;i++){
            if(i>=0&&i<retinaSize){
                for (j=y-1; j<y+2;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j];
                        if(surroundPoint.getDecayedFilteredValue(currentTime)<shapeDLimit){
                            ep.zerosDAround++;
                        }
                        if(surroundPoint.shortFilteredValue<shapeLimit){
                            ep.zerosAround++;
                            // update ep gc
                            //  ep.addToGc(i,j);
                        }
                        // here we update also the surroundPoint border status
                        // if too costly in time: maybe avoid
                        if(changedDToLow){
                            surroundPoint.zerosDAround++;
                            /// checkBorderTemplate(surroundPoint,currentTime);
                        } else if(changedDToHigh){
                            surroundPoint.zerosDAround--;
                            // checkBorderTemplate(surroundPoint,currentTime);
                        }
                        
                        if(changedToLow){
                            surroundPoint.zerosAround++;
                            
                            // surroundPoint.addToGc(x,y);
                            
                            // checkBorderTemplate(surroundPoint,currentTime);
                        } else if(changedToHigh){
                            surroundPoint.zerosAround--;
                            //surroundPoint.remFromGc(x,y);
                            // checkBorderTemplate(surroundPoint,currentTime);
                        }
                        
                        if(changed){
                            checkBorderTemplate(surroundPoint,currentTime, eventPoints);
                        }
                        
                        
                    }
                }
            }
        }
        
        
        checkBorderTemplate(ep,currentTime,eventPoints);
        
        
        // create new label if no neighbouring label
        // for this collect all neigbouring labels, and if any choose the one
        // with lowest value to replace the others by assignment
        
        // intensity : update neigbour intensity before?
        
        
        
    }
    
    
    void thin_a( EventPoint ep, EventPoint[][] eventPoints){
        if(ep.shortFilteredValue>sk_threshold){
            // thin
            int[] a = new int[8];
            int[] arbr = new int[2];
            ep.skValue = 1;
            
            arbr = t1a( ep.x, ep.y, a,retinaSize,retinaSize, sk_threshold,eventPoints);
            
            int p1 = a[0]*a[2]*a[4];
            int p2 = a[2]*a[4]*a[6];
            if ( (arbr[0] == 1) && ((arbr[1]>=2) && (arbr[1]<=6)) &&
                    (p1 == 0) && (p2 == 0) )  {
                ep.skValue = 0;
            }
        } else {
            ep.skValue = 0;
        }
        
    }
    
    void thin_surround_a( EventPoint ep, EventPoint[][] eventPoints){
        
        for (int i=ep.x-2; i<ep.x+3;i++){
            if(i>=0&&i<retinaSize){
                for (int j=ep.y-2; j<ep.y+3;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j];
                        thin_a(surroundPoint,eventPoints);
                        
                    }
                }
            }
        }
        
    }
    
    void thin_surround_b( EventPoint ep, EventPoint[][] eventPoints){
        for (int i=ep.x-2; i<ep.x+3;i++){
            if(i>=0&&i<retinaSize){
                for (int j=ep.y-2; j<ep.y+3;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j];
                        thin_b(surroundPoint,eventPoints);
                        
                    }
                }
            }
        }
        
    }
    
    void thin_b( EventPoint ep, EventPoint[][] eventPoints){
        // if(ep.skValue==1){
        // thin
        int[] a = new int[8];
        int[] arbr = new int[2];
        // ep.skValue = 1;
        
        arbr = t1b( ep.x, ep.y, a,retinaSize,retinaSize, sk_threshold, eventPoints);
        
        int p1 = a[0]*a[2]*a[4];
        int p2 = a[2]*a[4]*a[6];
        if ( (arbr[0] == 1) && ((arbr[1]>=2) && (arbr[1]<=6)) &&
                (p1 == 0) && (p2 == 0) )  {
            ep.skValue = 0;
        }
        // }
        
    }
    
    
    void setPrevGCIntensityLine( EventPoint ep, float value, EventPoint[][] eventPoints ){
        
        //  if(ep.prev_gcx==0&&ep.prev_gcy==0)return;
        
        // find dir of line
        float dir = direction(ep.x,ep.y,ep.prev_gcx,ep.prev_gcy);
        
        int xi = ep.x + Math.round((float)Math.cos(Math.toRadians(dir))*intensity_range); // +0.5 for rounding, maybe not
        int yi = ep.y + Math.round((float)Math.sin(Math.toRadians(dir))*intensity_range); // +0.5 for rounding, maybe not
        
        
        //System.out.println("setGCIntensityLine ["+ep.x+","+ep.y+"] to ["+xi+","+yi+"] of value : "+value);
        // increase by value on line for max length
        increaseIntentisyOfLine(ep.x,ep.y,xi,yi,value,eventPoints);
        
    }
    
    void setGCIntensityLine( EventPoint ep, float value, EventPoint[][] eventPoints ){
        
        //  if(ep.prev_gcx==0&&ep.prev_gcy==0)return;
        
        // find dir of line
        float dir = direction(ep.x,ep.y,ep.gcx,ep.gcy);
        
        int xi = ep.x + Math.round((float)Math.cos(Math.toRadians(dir))*intensity_range+0.5f); // +0.5 for rounding, maybe not
        int yi = ep.y + Math.round((float)Math.sin(Math.toRadians(dir))*intensity_range+0.5f); // +0.5 for rounding, maybe not
        
        
        //System.out.println("setGCIntensityLine ["+ep.x+","+ep.y+"] to ["+xi+","+yi+"] of value : "+value);
        // increase by value on line for max length
        // increaseIntentisyOfLine(ep.x,ep.y,xi,yi,value);
        
        increaseIntentisyOfLine(ep.x,ep.y,xi-2,yi+2,value,eventPoints);
        increaseIntentisyOfLine(ep.x,ep.y,xi-2,yi-2,value,eventPoints);
        increaseIntentisyOfLine(ep.x,ep.y,xi+2,yi+2,value,eventPoints);
        increaseIntentisyOfLine(ep.x,ep.y,xi+2,yi-2,value,eventPoints);
        
    }
    
    protected float direction( float x0, float y0, float x1, float y1 ){
        double dx = (double)(x1-x0);
        double dy = (double)(y1-y0);
        double size = Math.sqrt((dy*dy)+(dx*dx));
        double orientation = Math.toDegrees(Math.acos(dx/size));
        
        if (y0>y1){
            orientation = 360-orientation;
        }
        return (float)orientation;
    }
    
    void checkBorderTemplate(EventPoint ep, int currentTime, EventPoint[][] eventPoints ){
        boolean becomesBorder = false;
        boolean becomesDBorder = false;
        
        
        if(ep.shortFilteredValue>shapeThreshold){
            
            if(ep.zerosAround>minZeroes&&ep.zerosAround<maxZeroes){
                becomesBorder = true;
            }
        }
        
        
        if(ep.getDecayedFilteredValue(currentTime)>shapeDThreshold){
            
            if(ep.zerosDAround>minZeroes&&ep.zerosDAround<maxZeroes){
                becomesDBorder = true;
            }
        }
        
        
        
        
        float intensityChange = 0;
        
        if(ep.border&&!becomesBorder){
            intensityChange = -intensity_strength;
        } else if(becomesBorder&&!ep.border) {
            intensityChange = intensity_strength;
        }
        int x = ep.x;
        int y = ep.y;
        int i;
        int j;
        //  int range = intensity_range;
        int score = 0;
        int score_max = 0;
        int imax = 0;
        int jmax = 0;
        int radiusSq = intensity_range*intensity_range;
        
        float dist = 0;
        
        
        // redraw gc intensity line
        
        if(!ep.border&&becomesBorder){//&&!ep.border){ // actually becomes a border
            // decrease previous gc line
            // System.out.println("decrease previous setPrevGCIntensityLine");
            // setPrevGCIntensityLine(ep,-intensity_strength);
            
            
            //ep.setPrevGc();
            ep.setGc(eventPoints);
            // increase current gc line
            //   System.out.println("increase current setPrevGCIntensityLine");
            setGCIntensityLine(ep,intensity_strength,eventPoints);
        } else if(ep.border&&!becomesBorder){ //was a border but is not anymore
            // decrease previous gc line
            //    System.out.println("remove previous setPrevGCIntensityLine");
            setGCIntensityLine(ep,-intensity_strength,eventPoints);
            ep.resetGc();
        }
        /*
         
         if((ep.border&&!becomesBorder)||(becomesBorder&&!ep.border)){
             for (i=x-intensity_range; i<x+intensity_range+1;i++){
                 if(i>=0&&i<retinaSize){
                     for (j=y-intensity_range; j<y+intensity_range+1;j++){
                         if(j>=0&&j<retinaSize){
                             EventPoint surroundPoint = eventPoints[i][j];
         
         
                             dist = ((i-x)*(i-x)) + ((j-y)*(j-y));
                             if(dist<radiusSq&&dist>1){
                                 // if current ep point is a border, increase intensity around
                                 surroundPoint.intensity += intensityChange;
                                 // check if finger point
                                 if(surroundPoint.intensity>intensity_threshold
                                        &&surroundPoint.shortFilteredValue>in_value_threshold){
         
                                       addTipToFingerTracker(eventPoints[i][j]);
                                 }
                             }
                         }
                     }
                 }
             }
         }
         
         */
        
        
        ep.border =  becomesBorder;
        ep.decayedBorder =  becomesDBorder;
        
        // with decayedBorder can do more, like checking when ep.border and ep.decayedBorder are simultaneously true
        // to find finger tip
        
        // check if drives finger cluster
        //if (ep.border&&ep.decayedBorder) {
        if (ep.border&&ep.decayedBorder) {
            // System.out.println("new event at "+ep.x+","+ep.y);
            addBorderToFingerTracker(ep);
        }
        
        // deals with possible finger
        //   if (score_max>0) System.out.println(">>>>>>>>>>> score max for ["+imax+","+jmax+"] : "+score_max);
//        if(score_max>score_threshold){
//            scoresFrame[imax][jmax]=1;
//            // do something with eventPoints[imax][jmax]
//            // like add to finger tracker
//            addToFingerTracker(eventPoints[imax][jmax]);
//        }
        
    }
    
    
    int[] t1a( int i, int j, int[] a, int nn, int mm, float threshold, EventPoint[][] eventPoints ){
        int[] arbr = new int[2];
        //...
          /*	Return the number of 01 patterns in the sequence of pixels
        P2 p3 p4 p5 p6 p7 p8 p9.					*/
        
        int n,m;
        int b;
        for (n=0; n<8; n++) a[n] = 0;
        if (i-1 >= 0) {
            if (eventPoints[i-1][j].shortFilteredValue>threshold) a[0] = 1;
            if (j+1 < mm) if (eventPoints[i-1][j+1].shortFilteredValue>threshold) a[1] = 1;
            if (j-1 >= 0) if (eventPoints[i-1][j-1].shortFilteredValue>threshold) a[7] = 1;
        }
        if (i+1 < nn) {
            if (eventPoints[i+1][j].shortFilteredValue>threshold) a[4] = 1;
            if (j+1 < mm) if (eventPoints[i+1][j+1].shortFilteredValue>threshold) a[3] = 1;
            if (j-1 >= 0) if (eventPoints[i+1][j-1].shortFilteredValue>threshold) a[5] = 1;
        }
        if (j+1 < mm) if (eventPoints[i][j+1].shortFilteredValue>threshold) a[2] = 1;
        if (j-1 >= 0) if (eventPoints[i][j-1].shortFilteredValue>threshold) a[6] = 1;
        
        m= 0;	b = 0;
        for (n=0; n<7; n++) {
            if ((a[n]==0) && (a[n+1]==1)) m++;
            b = b + a[n];
        }
        if ((a[7] == 0) && (a[0] == 1)) m++;
        b = b + a[7];
        
        arbr[0] = m;
        arbr[1] = b;
        return arbr;
    }
    
    int[] t1b( int i, int j, int[] a, int nn, int mm, float threshold, EventPoint[][] eventPoints ){
        int[] arbr = new int[2];
        //...
          /*	Return the number of 01 patterns in the sequence of pixels
        P2 p3 p4 p5 p6 p7 p8 p9.					*/
        
        int n,m;
        int b;
        for (n=0; n<8; n++) a[n] = 0;
        if (i-1 >= 0) {
            a[0] = eventPoints[i-1][j].skValue;
            if (j+1 < mm)  a[1] = eventPoints[i-1][j+1].skValue;
            if (j-1 >= 0)  a[7] = eventPoints[i-1][j-1].skValue;
        }
        if (i+1 < nn) {
            if (eventPoints[i+1][j].skValue>threshold) a[4] = 1;
            if (j+1 < mm)  a[3] = eventPoints[i+1][j+1].skValue;
            if (j-1 >= 0)  a[5] = eventPoints[i+1][j-1].skValue;
        }
        if (j+1 < mm) a[2] = eventPoints[i][j+1].skValue;
        if (j-1 >= 0) a[6] = eventPoints[i][j-1].skValue;
        
        m= 0;	b = 0;
        for (n=0; n<7; n++) {
            if ((a[n]==0) && (a[n+1]==1)) m++;
            b = b + a[n];
        }
        if ((a[7] == 0) && (a[0] == 1)) m++;
        b = b + a[7];
        
        arbr[0] = m;
        arbr[1] = b;
        return arbr;
    }
    
    void addTipToFingerTracker( EventPoint ep ){
        
        /**
         * check additional constraints to be deemed a finger:
         *    find if end node of skeletton in range
         * find closer fingertracker
         * if none, create new fingertracker
         *
         * call fingerTracker.add
         *     which mix previous position with current eventPoint position
         *
         *
         */
        
        
        
        // find nearest
        FingerCluster fc = getNearestFinger(ep,finger_creation_surround);
        if(fc==null){
            if(nbFingers<MAX_NB_FINGERS){
                // maybe create new finger
                // if out door
                if((ep.x<door_xa)||(ep.x>door_xb)||(ep.y<door_ya)||(ep.y>door_yb)){
                    
                    fc = getNearestFinger(ep,finger_creation_surround);
                    
                    if(fc==null){
                        fingers[nbFingers] = new FingerCluster(ep.x,ep.y);
                        //  System.out.println("create finger at: ["+ep.x+","+ep.y+"] fc: "+fc);
                        
                        nbFingers++;
                    }
                }
            }
        } else {
            fc.add(ep.x,ep.y,finger_tip_mix);
        }
    }
    
    void addBorderToFingerTracker( EventPoint ep ){
        
        /**
         * check additional constraints to be deemed a finger:
         *    find if end node of skeletton in range
         * find closer fingertracker
         * if none, create new fingertracker
         *
         * call fingerTracker.add
         *     which mix previous position with current eventPoint position
         *
         *
         */
        
        // find nearest
        FingerCluster fc = getNearestFinger(ep,finger_surround);
        if(fc!=null){
            //  fc.add(ep.x,ep.y,finger_border_mix);
        }
    }
    
    private FingerCluster getNearestFinger( EventPoint ep, int surround ){
        float min_dist=Float.MAX_VALUE;
        FingerCluster closest=null;
        // float currentDistance=0;
        int surroundSq = surround*surround;
        for(FingerCluster fc:fingers){
            if(fc!=null){
                if(fc.activated){
                    
                    float dist = (ep.x-fc.tip_x_end)*(ep.x-fc.tip_x_end) + (ep.y-fc.tip_y_end)*(ep.y-fc.tip_y_end);
                    if(dist<surroundSq){
                        if(dist<min_dist){
                            closest = fc;
                            min_dist = dist;
                        }
                    }
                }
            }
        }
        return closest;
    }
    
    
    
    
    /**
     * protected Line lineToShape(int x, int y, int range, float orientation, float min_threshold, float[][][] frame ){
     * Line result = new Line();
     *
     * // compute x1s and y1s based on range
     * // for all points in a square outline centered on x0,y0 with side size range+1/2
     * // find length of line
     * // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
     *
     * int x0 = x;
     * int y0 = y;
     * int xEnd = 0;
     * int yEnd = 0;
     *
     *
     * // find target point
     * int x1 = x0 + (int)(Math.cos(Math.toRadians(orientation))*range);
     * int y1 = y0 + (int)(Math.sin(Math.toRadians(orientation))*range);
     * // System.out.println("lineToShape start: ["+x0+","+y0+"] target: ["+x1+","+y1+"]");
     *
     *
     * int dy = y1 - y0;
     * int dx = x1 - x0;
     * int stepx, stepy;
     * int length = 0;
     *
     * if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
     * if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
     * dy <<= 1;                                                  // dy is now 2*dy
     * dx <<= 1;                                                  // dx is now 2*dx
     *
     *
     * if (dx > dy) {
     * int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
     * while (x0 != x1) {
     * if (fraction >= 0) {
     * y0 += stepy;
     * fraction -= dx;                                // same as fraction -= 2*dx
     * }
     * x0 += stepx;
     * fraction += dy;                                    // same as fraction -= 2*dy
     * if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
     * if(frame[y0][x0][0]>min_threshold){
     * if (contour.eventsArray[x0][y0]==null){
     * length++;
     * } else {
     * if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
     * ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
     *
     * length++;
     *
     * } else {
     * break;
     * }
     * }
     * } else {
     * break;
     * }
     *
     * } else {
     * break;
     * }
     *
     *
     * }
     * } else {
     * int fraction = dx - (dy >> 1);
     * while (y0 != y1) {
     * if (fraction >= 0) {
     * x0 += stepx;
     * fraction -= dy;
     * }
     * y0 += stepy;
     * fraction += dx;
     * if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
     * if(frame[y0][x0][0]>min_threshold){
     * if (contour.eventsArray[x0][y0]==null){
     * length++;
     * } else {
     * if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
     * ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
     * length++;
     *
     * } else {
     * break;
     * }
     * }
     * } else {
     * break;
     * }
     * } else {
     * break;
     * }
     * }
     * }
     * // end computing line, end point in x0,y0
     *
     * xEnd = x0;
     * yEnd = y0;
     * result = new Line(x,y,xEnd,yEnd,length);
     * // System.out.println("lineToShape result start: ["+x+","+y+"] target: ["+xEnd+","+yEnd+"]");
     * return result;
     *
     * }
     */
    
    
    protected Line shortestLineToValue(int x, int y, int range, float short_threshold,
            float large_threshold, EventPoint[][] eventPoints ){
        Line result = new Line();
        
        // compute x1s and y1s based on range
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        // find length of line
        // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
        
        
        int x1 = 0;
        int y1 = 0;
        int x0 = 0;
        int y0 = 0;
        
        int lengthMin = 1000;
        boolean valueFound = false;
        
        
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        for(int i=x-range;i<x+range+1;i++){
            
            for(int j=y-range;j<y+range+1;j++){
                if(((i<=x-range)||(i>=x+range))||((j<=y-range)||(j>=y+range))){
                    // on the square outline
                    // if within demanded orientation acceptance
                    
                    
                    x1 = i;
                    y1 = j;
                    x0 = x;
                    y0 = y;
                    
                    
                    
                    int dy = y1 - y0;
                    int dx = x1 - x0;
                    int stepx, stepy;
                    int length = 0;
                    valueFound = false;
                    
                    if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
                    if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
                    dy <<= 1;                                                  // dy is now 2*dy
                    dx <<= 1;                                                  // dx is now 2*dx
                    
                    
                    if (dx > dy) {
                        int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
                        while (x0 != x1) {
                            if (fraction >= 0) {
                                y0 += stepy;
                                fraction -= dx;                                // same as fraction -= 2*dx
                            }
                            x0 += stepx;
                            fraction += dy;                                    // same as fraction -= 2*dy
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                if(eventPoints[x0][y0].shortFilteredValue>short_threshold){
                                    
                                    if((length+1<3)||!eventPoints[x0][y0].border){//and check if within shape only after a few timestep
                                        
                                        length++;
                                        if(eventPoints[x0][y0].largeFilteredValue>large_threshold){
                                            valueFound=true;
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                    
                                } else {
                                    break;
                                }
                            } else {
                                break;
                            }
                            
                            
                        }
                    } else {
                        int fraction = dx - (dy >> 1);
                        while (y0 != y1) {
                            if (fraction >= 0) {
                                x0 += stepx;
                                fraction -= dy;
                            }
                            y0 += stepy;
                            fraction += dx;
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                if(eventPoints[x0][y0].shortFilteredValue>short_threshold){
                                    
                                    if((length+1<3)||!eventPoints[x0][y0].border){//and check if within shape only after a few timestep
                                        
                                        length++;
                                        if(eventPoints[x0][y0].largeFilteredValue>large_threshold){
                                            valueFound=true;
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                    
                                } else {
                                    break;
                                }
                            } else {
                                break;
                                
                            }
                        }
                    }
                    // end computing line, end point in x0,y0
                    
                    if(valueFound){
                        // if min length memorize line
                        if(length<lengthMin){
                            lengthMin=length;
                            
                            result = new Line(x,y,x0,y0,length);
                        }
                    }
                    
                    
                    
                    
                } // end if on outline
                
            }
        } //end for all points on square's outline
        
        return result;
    }
    
    
    
    
    // stops if touching shape or pixel below threshold
    // global parameters : line_threshold, eventPoints
    protected Vector longestLines(int x, int y, int range, int nb_lines_avg, EventPoint[][] eventPoints ){
        Vector lines = new Vector();
        
        //Line line = new Line();
        // compute x1s and y1s based on range
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        // find length of line
        // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
        float threshold = line_threshold;
        
        int x1 = 0;
        int y1 = 0;
        int x0 = 0;
        int y0 = 0;
        
        int lengthMax = 0;
        
        
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        for(int i=x-range;i<x+range+1;i++){
            for(int j=y-range;j<y+range+1;j++){
                if(((i<=x-range)||(i>=x+range))||((j<=y-range)||(j>=y+range))){
                    // on the square outline
                    x1 = i;
                    y1 = j;
                    x0 = x;
                    y0 = y;
                    //int midx = 0;
                    //int midy = 0;
                    //boolean touching = false;
                    
                    int dy = y1 - y0;
                    int dx = x1 - x0;
                    int stepx, stepy;
                    int length = 0;
                    
                    if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
                    if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
                    dy <<= 1;                                                  // dy is now 2*dy
                    dx <<= 1;                                                  // dx is now 2*dx
                    
                    
                    if (dx > dy) {
                        int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
                        while (x0 != x1) {
                            if (fraction >= 0) {
                                y0 += stepy;
                                fraction -= dx;                                // same as fraction -= 2*dx
                            }
                            x0 += stepx;
                            fraction += dy;                                    // same as fraction -= 2*dy
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                if(eventPoints[x0][y0].shortFilteredValue>threshold){
                                    
                                    if((length<2)||(!eventPoints[x0][y0].border&&!eventPoints[x0][y0].decayedBorder)){//and check if within shape only after a few timestep
                                        length++;
                                        
                                        
                                        
                                    } else { //reached shape border
                                        break;
                                    }
                                    
                                } else { //reached value below threshold
                                    break;
                                }
                            } else { //outside retina's limit
                                break;
                            }
                            
                        }
                    } else {
                        int fraction = dx - (dy >> 1);
                        while (y0 != y1) {
                            if (fraction >= 0) {
                                x0 += stepx;
                                fraction -= dy;
                            }
                            y0 += stepy;
                            fraction += dx;
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                
                                if(eventPoints[x0][y0].shortFilteredValue>threshold){
                                    
                                    if((length<2)||(!eventPoints[x0][y0].border&&!eventPoints[x0][y0].decayedBorder)){//and check if within shape only after a few timestep
                                        length++;
                                        
                                        
                                        
                                    } else { //reached shape border
                                        break;
                                    }
                                    
                                } else { //reached value below threshold
                                    break;
                                }
                                
                                
                            } else {
                                break;
                                
                            }
                        }
                    }
                    // end computing line, end point in x0,y0
                    
                    // memorize max length
                    if(length>lengthMax){
                        lengthMax=length;
                    }
                    
                    
                    // store all lines
                    Line line = new Line(x,y,x0,y0,length);
                    
                    
                    lines.add(line);
                    
                    
                    
                } // end if on outline
            }
        } //end for all points on square's outline
        
        
        if(lengthMax>0){
            // got some lines, select the N longest
            if(lines.size()>nb_lines_avg){
                Collections.sort(lines, new LineLengthComparer());
                for(int k=lines.size()-1; k>=lines_n_avg; k--){
                    lines.remove(k);
                }
            }
        }
        
        
        return lines;
    }
    
    
    protected Line mostValuableLine(int x, int y, int range, EventPoint[][] eventPoints ){
        Line result = null;
        // compute x1s and y1s based on range
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        // find length of line
        // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
        float threshold = line_threshold;
        
        int x1 = 0;
        int y1 = 0;
        int x0 = 0;
        int y0 = 0;
        
        int lengthMax = 0;
        int valueMax = 0;
        
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        for(int i=x-range;i<x+range+1;i++){
            for(int j=y-range;j<y+range+1;j++){
                if(((i<=x-range)||(i>=x+range))||((j<=y-range)||(j>=y+range))){
                    // on the square outline
                    x1 = i;
                    y1 = j;
                    x0 = x;
                    y0 = y;
                    //int midx = 0;
                    //int midy = 0;
                    //boolean touching = false;
                    
                    int dy = y1 - y0;
                    int dx = x1 - x0;
                    int stepx, stepy;
                    int length = 0;
                    
                    int last_x = x;
                    int last_y = y;
                    int value = 0;
                    
                    if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
                    if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
                    dy <<= 1;                                                  // dy is now 2*dy
                    dx <<= 1;                                                  // dx is now 2*dx
                    
                    
                    if (dx > dy) {
                        int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
                        while (x0 != x1) {
                            if (fraction >= 0) {
                                y0 += stepy;
                                fraction -= dx;                                // same as fraction -= 2*dx
                            }
                            x0 += stepx;
                            fraction += dy;                                    // same as fraction -= 2*dy
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                if(eventPoints[x0][y0].shortFilteredValue>threshold){
                                    
                                    if((length<2)||(!eventPoints[x0][y0].border)){//&&!eventPoints[x0][y0].decayedBorder)){//and check if within shape only after a few timestep
                                        length++;
                                        if(eventPoints[x0][y0].skValue==1){
                                            last_x = x0;
                                            last_y = y0;
                                            value++;
                                        }
                                    } else { //reached shape border
                                        break;
                                    }
                                    
                                } else { //reached value below threshold
                                    break;
                                }
                            } else { //outside retina's limit
                                break;
                            }
                            
                        }
                    } else {
                        int fraction = dx - (dy >> 1);
                        while (y0 != y1) {
                            if (fraction >= 0) {
                                x0 += stepx;
                                fraction -= dy;
                            }
                            y0 += stepy;
                            fraction += dx;
                            if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                                
                                if(eventPoints[x0][y0].shortFilteredValue>threshold){
                                    
                                    if((length<2)||(!eventPoints[x0][y0].border)){//&&!eventPoints[x0][y0].decayedBorder)){//and check if within shape only after a few timestep
                                        length++;
                                        if(eventPoints[x0][y0].skValue==1){
                                            last_x = x0;
                                            last_y = y0;
                                            value++;
                                        }
                                        
                                    } else { //reached shape border
                                        break;
                                    }
                                    
                                } else { //reached value below threshold
                                    break;
                                }
                                
                                
                            } else {
                                break;
                                
                            }
                        }
                    }
                    // end computing line, end point in x0,y0
                    
                    // memorize max length
                    if(length>lengthMax){
                        lengthMax=length;
                    }
                    
                    if(value>valueMax){
                        valueMax = value;
                        
                        // Line line = new Line(x,y,xEnd,yEnd,length);
                        result = new Line(x,y,last_x,last_y,length);
                        
                        
                        
                    }
                    
                    
                    
                    
                    
                } // end if on outline
            }
        } //end for all points on square's outline
        
        
        
        
        
        return result;
    }
    
    
    
    protected Line lineFromShapeBorder(int x, int y, int range, float orientation,
            float min_threshold, EventPoint[][] eventPoints ){
        Line result = new Line();
        
        // compute x1s and y1s based on range
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        // find length of line
        // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
        
        int x0 = x;
        int y0 = y;
        
        
        // find target point
        int x1 = x0 + Math.round((float)Math.cos(Math.toRadians(orientation))*range);
        int y1 = y0 + Math.round((float)Math.sin(Math.toRadians(orientation))*range);
        // System.out.println("lineToShape start: ["+x0+","+y0+"] target: ["+x1+","+y1+"]");
        
        
        int dy = y1 - y0;
        int dx = x1 - x0;
        int stepx, stepy;
        int length = 0;
        
        if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
        if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
        dy <<= 1;                                                  // dy is now 2*dy
        dx <<= 1;                                                  // dx is now 2*dx
        
        
        if (dx > dy) {
            int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
            while (x0 != x1) {
                if (fraction >= 0) {
                    y0 += stepy;
                    fraction -= dx;                                // same as fraction -= 2*dx
                }
                x0 += stepx;
                fraction += dy;                                    // same as fraction -= 2*dy
                if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                    
                    if(eventPoints[x0][y0].shortFilteredValue>min_threshold){
                        
                        if((length<2)||(!eventPoints[x0][y0].border)){//&&!eventPoints[x0][y0].decayedBorder)){// check if within shape only after a few timestep
                            length++;
                            
                            
                        } else { //reached shape border
                            break;
                        }
                        
                    } else { //reached value below threshold
                        break;
                    }
                    
                } else {
                    break;
                }
                
                
            }
        } else {
            int fraction = dx - (dy >> 1);
            while (y0 != y1) {
                if (fraction >= 0) {
                    x0 += stepx;
                    fraction -= dy;
                }
                y0 += stepy;
                fraction += dx;
                if(x0>0&&x0<retinaSize&&y0>0&&y0<retinaSize){
                    if(eventPoints[x0][y0].shortFilteredValue>min_threshold){
                        
                        if((length<2)||(!eventPoints[x0][y0].border)){//&&!eventPoints[x0][y0].decayedBorder)){// check if within shape only after a few timestep
                            length++;
                            
                            
                        } else { //reached shape border
                            break;
                        }
                        
                    } else { //reached value below threshold
                        break;
                    }
                    
                    
                } else {
                    break;
                }
            }
        }
        // end computing line, end point in x0,y0
        
        
        result = new Line(x0,y0,x,y,length);
        // System.out.println("lineToShape result start: ["+x+","+y+"] target: ["+xEnd+","+yEnd+"]");
        return result;
        
    }
    
    
    protected Line meanLineOf( Vector lines ){
        Line result = null;
        
        // return mean line from lines with same start point
        float avgOrientation = 0;
        float avgLength = 0;
        // for all lines find mean orientation
        Line line = null;
        for( Object o:lines ){
            line = (Line)o;
            avgOrientation += lineDirection(line);
            avgLength += line.length;
        }
        avgOrientation = avgOrientation/lines.size();
        avgLength = avgLength/lines.size();
        
        if(avgLength>finger_length){
            avgLength=finger_length;
        }
        // then create end point at mean length or < if specified (?)
        if(line!=null){
            
            int xb = line.x0 + (int)(Math.cos(Math.toRadians(avgOrientation))*avgLength);
            int yb = line.y0 + (int)(Math.sin(Math.toRadians(avgOrientation))*avgLength);
            
            
            result = new Line(line.x0,line.y0,xb,yb,(int)avgLength);
            result.orientation = avgOrientation;
        }
        
        return result;
        
    }
    
    
    protected float orientation( int x0, int y0, int x1, int y1 ){
        double dx = (double)(x1-x0);
        double dy = (double)(y1-y0);
        double size = Math.sqrt((dy*dy)+(dx*dx));
        double orientation = Math.toDegrees(Math.acos(dx/size));
        
        if (y0>y1){
            orientation = 180-orientation;
        }
        return (float)orientation;
    }
    
    protected float lineOrientation( Line line ){
        double dx = (double)(line.x1-line.x0);
        double dy = (double)(line.y1-line.y0);
        double size = Math.sqrt((dy*dy)+(dx*dx));
        double orientation = Math.toDegrees(Math.acos(dx/size));
        
        if (line.y0>line.y1){
            orientation = 180-orientation;
        }
        return (float)orientation;
    }
    
    protected float lineDirection( Line line ){
        double dx = (double)(line.x1-line.x0);
        double dy = (double)(line.y1-line.y0);
        double size = Math.sqrt((dy*dy)+(dx*dx));
        double orientation = Math.toDegrees(Math.acos(dx/size));
        
        if (line.y0>line.y1){
            orientation = 360-orientation;
        }
        return (float)orientation;
    }
    
    
    
       /*
    protected int traceLine(int x0, int y0, int x1, int y1, int maxrange){
        
        int length = 0;
        
        int x = x0;
        int y = y0;
        
        int dy = y1 - y0;
        int dx = x1 - x0;
        int stepx, stepy;
        
        if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
        if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
        dy <<= 1;                                                  // dy is now 2*dy
        dx <<= 1;                                                  // dx is now 2*dx
        
        if (dx > dy) {
            int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
            while (x0 != x1) {
                if (fraction >= 0) {
                    y0 += stepy;
                    fraction -= dx;                                // same as fraction -= 2*dx
                }
                x0 += stepx;
                fraction += dy;                                    // same as fraction -= 2*dy
        
                if((x0>0&&x0<retinaSize)&&(y0>0&&y0<retinaSize)){
                    if (contour.eventsArray[x0][y0]!=null){
                        if (contour.eventsArray[x0][y0].on==1){
                            if (contour.eventsArray[x0][y0].label==1){
                                // point on shape
                                // stop
                                break;
                            }
                        }
                    }
        
                    length++;
                    if(length>maxrange){
                        break;
                    }
                } else {
                    break;
                }
        
            }
        } else {
            int fraction = dx - (dy >> 1);
            while (y0 != y1) {
                if (fraction >= 0) {
                    x0 += stepx;
                    fraction -= dy;
                }
                y0 += stepy;
                fraction += dx;
        
                if((x0>0&&x0<retinaSize)&&(y0>0&&y0<retinaSize)){
                    if (contour.eventsArray[x0][y0]!=null){
                        if (contour.eventsArray[x0][y0].on==1){
                            if (contour.eventsArray[x0][y0].label==1){
                                // point on shape
                                // stop
                                break;
                            }
                        }
        
                        length++;
                        if(length>maxrange){
                            break;
                        }
                    }
                } else {
                    break;
                }
        
        
        
            }
        }
        
        
        return length;
        
    }
        */
    
    /*
    protected float meanValueOfLine(int x0, int y0, int x1, int y1, float[][][] accEvents){
     
        float meanValue = 0;
        int nbValues = 0;
     
        int x = x0;
        int y = y0;
     
        int dy = y1 - y0;
        int dx = x1 - x0;
        int stepx, stepy;
     
        if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
        if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
        dy <<= 1;                                                  // dy is now 2*dy
        dx <<= 1;                                                  // dx is now 2*dx
     
        try {
     
        meanValue += accEvents[y0][x0][0];
        nbValues++;
     
     
        if (dx > dy) {
            int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
            while (x0 != x1) {
                if (fraction >= 0) {
                    y0 += stepy;
                    fraction -= dx;                                // same as fraction -= 2*dx
                }
                x0 += stepx;
                fraction += dy;                                    // same as fraction -= 2*dy
                meanValue += accEvents[y0][x0][0];
                nbValues++;
     
     
            }
        } else {
            int fraction = dx - (dy >> 1);
            while (y0 != y1) {
                if (fraction >= 0) {
                    x0 += stepx;
                    fraction -= dy;
                }
                y0 += stepy;
                fraction += dx;
     
                meanValue += accEvents[y0][x0][0];
                nbValues++;
     
     
     
            }
        }
     
        } catch (Exception e ){
            //System.out.println("meanValueOfLine error x0="+x0+" y0="+y0+" x1="+x1+" y1="+y1+" x="+x+" y="+y);
            //e.printStackTrace();
     
        }
        return meanValue/nbValues;
    }
     */
    
    /*
     // rectangle defined by two sides: x0,y0 to x1,y1 and x0,y0 to x2,y2
    protected void increaseIntensityOfRectangle(int x0, int y0, int x1, int y1, int x2, int y2){
        // initial values
        int x = x0;
        int y = y0;
     
     
        int dy = y1 - y0;
        int dx = x1 - x0;
        int stepx, stepy;
     
        if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
        if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
        dy <<= 1;                                                  // dy is now 2*dy
        dx <<= 1;                                                  // dx is now 2*dx
     
     
        // create line here
        increaseIntentisyOfLine(x0,y0,x2,y2);
     
        if (dx > dy) {
            int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
            while (x0 != x1) {
                if (fraction >= 0) {
                    y0 += stepy;
                    fraction -= dx;                                // same as fraction -= 2*dx
                }
                x0 += stepx;
                fraction += dy;                                    // same as fraction -= 2*dy
     
                increaseIntentisyOfLine(x0,y0,x2+x0-x,y2+y0-y);
            }
        } else {
            int fraction = dx - (dy >> 1);
            while (y0 != y1) {
                if (fraction >= 0) {
                    x0 += stepx;
                    fraction -= dy;
                }
                y0 += stepy;
                fraction += dx;
                increaseIntentisyOfLine(x0,y0,x2+x0-x,y2+y0-y);
            }
        }
    }
     */
    
    // increase intentisy along a discrete line between two points
    synchronized protected void increaseValueOf3DLine(int x, int y, float intensity, float angle, float increase, int time , int leftOrRight){
        
        int x0 = 0;
        int z0 = 0;
        
        int half = retinaSize/2;
        int halfDepth = retina3DSize/2;
        int shift = (retina3DSize-retinaSize)/2;
        
        int yc = y;
        if(leftOrRight==LEFT) {
            yc += yLeftCorrection;
            
        } else {
            yc += yRightCorrection;
        }
        
        if(yc<0||yc>=retinaSize){
            return;
        }
        
        for(int z1=-150;z1<150;z1++){
            // for(int z1=-half;z1<half;z1++){
            //    for(int z1=0;z1<1;z1++){
            // compute next point
            x0 = Math.round((float) ( (Math.cos(Math.toRadians(angle))*(x-half)) -
                    (Math.cos(Math.toRadians(90-angle))*z1) )) + half+shift;
            z0 = Math.round((float) ( (Math.sin(Math.toRadians(angle))*(x-half)) +
                    (Math.cos(Math.toRadians(angle))*z1) )) + half;//+halfDepth;
            
            // if within bound, increase value.
            
            if(z0>0&&x0>0&&z0<retina3DSize&&x0<retina3DSize&&y>0&&y<retinaSize){
                //  increase
                //  System.out.println("1. points3D["+x0+"]["+y+"]["+z0+"].value : "+points3D[x0][y][z0].value);
                
                if(leftOrRight==LEFT) {
                    points3D[x0][yc][z0].lvalue = increase;
                    points3D[x0][yc][z0].rightTime = time;
                    
                } else {
                    points3D[x0][yc][z0].leftTime = time;
                    points3D[x0][yc][z0].rvalue = increase;
                }
                
                
                //   System.out.println("2. points3D["+x0+"]["+y+"]["+z0+"].value : "+points3D[x0][y][z0].value+" increase:"+increase);
                // if(points3D[x0][y][z0].intensity<intensity)
                points3D[x0][yc][z0].intensity = intensity;
                
            }
        }
    }
    
    
    // follow Bresenham algorithm to increase intentisy along a discrete line between two points
    protected void increaseIntentisyOfLine(int x0, int y0, int x1, int y1, float intensityIncrease, EventPoint[][] eventPoints){
        
        int x = x0;
        int y = y0;
        
        int dy = y1 - y0;
        int dx = x1 - x0;
        int stepx, stepy;
        
        if (dy < 0) { dy = -dy;  stepy = -1; } else { stepy = 1; }
        if (dx < 0) { dx = -dx;  stepx = -1; } else { stepx = 1; }
        dy <<= 1;                                                  // dy is now 2*dy
        dx <<= 1;                                                  // dx is now 2*dx
        
        try {
            
            
            //  increase
            eventPoints[x0][y0].intensity += intensityIncrease;
            if(eventPoints[x0][y0].intensity>intensity_threshold
                    &&eventPoints[x0][y0].shortFilteredValue>in_value_threshold){
                
                addTipToFingerTracker(eventPoints[x0][y0]);
            }
            
            if (dx > dy) {
                int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
                while (x0 != x1) {
                    if (fraction >= 0) {
                        y0 += stepy;
                        fraction -= dx;                                // same as fraction -= 2*dx
                    }
                    x0 += stepx;
                    fraction += dy;                                    // same as fraction -= 2*dy
                    eventPoints[x0][y0].intensity += intensityIncrease;
                    if(eventPoints[x0][y0].intensity>intensity_threshold
                            &&eventPoints[x0][y0].shortFilteredValue>in_value_threshold){
                        
                        addTipToFingerTracker(eventPoints[x0][y0]);
                    }
                }
            } else {
                int fraction = dx - (dy >> 1);
                while (y0 != y1) {
                    if (fraction >= 0) {
                        x0 += stepx;
                        fraction -= dy;
                    }
                    y0 += stepy;
                    fraction += dx;
                    eventPoints[x0][y0].intensity += intensityIncrease;
                    if(eventPoints[x0][y0].intensity>intensity_threshold
                            &&eventPoints[x0][y0].shortFilteredValue>in_value_threshold){
                        
                        addTipToFingerTracker(eventPoints[x0][y0]);
                    }
                }
            }
        } catch (Exception e ){
            //System.out.println("increaseIntentisyOfLine error x0="+x0+" y0="+y0+" x1="+x1+" y1="+y1+" x="+x+" y="+y);
            //e.printStackTrace();
            
        }
        
    }
    
    
    
    float[] resetDensities( int density ){
        float[] densities = new float[density];
        for (int k=0;k<density;k++){
            densities[k] = (float)k/density;
        }
        return densities;
    }
    
    
    
    
    float obtainedDensity( float value, float density, float inverseDensity, float[] densities){
        int cat = (int)(value / inverseDensity);
        float res = 0;
        if (cat>0){
            if(cat>=density){
                res = 1;
            } else {
                res = densities[cat];
            }
        }
        return res;
    }
    
    // for optimization, radius2 must be > radius1
    // uses global densities and densities2 arrays
    void fastDualLowFilterFrame( EventPoint ep, EventPoint[][] eventPoints, int leftOrRight ){
        
        float dist = 0;
        float f = 0;
        float dr = 0;
        float sdr = 0;
        int cat = 0;
        float bn = 0;
        float sn = 0;
        float val1 = 0;
        float val2 = 0;
        
        
        // for all points in square around
        // if point within circle
        //     add value by distance
        //     number++
        // end if
        // end for
        // average on number
        // add to res
        
        int i=0;
        int j=0;
        
        int x = ep.x;
        int y = ep.y;
        
        
        for (i=x-lowFilter_radius2; i<x+lowFilter_radius2+1;i++){
            if(i>=0&&i<retinaSize){
                for (j=y-lowFilter_radius2; j<y+lowFilter_radius2+1;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint influencedPoint = eventPoints[i][j];
                        // big filter first :
                        // if within circle
                        dist = ((i-x)*(i-x)) + ((j-y)*(j-y));
                        
                        if(dist<largeRadiusSq){
                            f = 1;
                            dr = (float)Math.sqrt(dist)/largeFRadius;
                            
                            if (dr!=0) f = 1/dr;
                            
                            // update point value : influence on neighbour
                            
                            influencedPoint.largeFilteredValue += (ep.lastValue * f)/largeRangeTotal;
                            if (influencedPoint.largeFilteredValue<0) influencedPoint.largeFilteredValue = 0;
                            // could use topographic terrasses here, calling obtainedDensity( ), or not
                            
                            // smaller range filter influence on neighbour
                            if(dist<shortRadiusSq){
                                f = 1;
                                sdr = (float)Math.sqrt(dist)/shortFRadius;
                                if (sdr!=0) f = 1/sdr;
                                // do not decay value here : we want to know what was brute value of last update
                                influencedPoint.previousShortFilteredValue = influencedPoint.shortFilteredValue;
                                influencedPoint.previousDecayedFilteredValue = influencedPoint.decayedFilteredValue;
                                
                                //influencedPoint.previousUpdate = influencedPoint.updateTime;
                                
                                influencedPoint.shortFilteredValue += (ep.lastValue * f)/shortRangeTotal;
                                // use get..Value(time) to decay value
                                influencedPoint.decayedFilteredValue = influencedPoint.getDecayedFilteredValue(ep.updateTime) + (ep.lastValue * f)/shortRangeTotal;
                                
                                //influencedPoint.updateTime = ep.updateTime;
                                if (influencedPoint.shortFilteredValue<0) {
                                    influencedPoint.shortFilteredValue = 0;
                                    influencedPoint.decayedFilteredValue = 0;
                                }
                                
                                // update border status
                                // isOnPawShape(influencedPoint,ep.updateTime,eventPoints);
                                
                                // compute 3D correspondances
                                processDisparity( leftOrRight, influencedPoint.x, influencedPoint.y,
                                        influencedPoint.shortFilteredValue,
                                        influencedPoint.previousShortFilteredValue );
                                
                                
                            }
                        }
                    }
                }
            }
        }
        
    }
    
    
    
    protected float decayedValue( float value, int time ){
        float res=value;
        
        float dt = (float)time/(float)decayTimeLimit;
        if(dt<0)dt = -dt;
        if(dt<1){
            res = value * dt;
        }
        return res;
    }
    
    
    
    void checkInsideIntensityFrame(){
        if(showWindow && insideIntensityFrame==null) createInsideIntensityFrame();
    }
    
    JFrame insideIntensityFrame=null;
    GLCanvas insideIntensityCanvas=null;
    
    
    private static final GLU glu = new GLU();
    
    int highlight_x = 0;
    int highlight_y = 0;
    int highlight_xR = 0;
    
    boolean highlight = false;
    float rotation = 0;
//    GLUT glut=null;
    void createInsideIntensityFrame(){
        insideIntensityFrame=new JFrame("Combined Frame");
        insideIntensityFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        insideIntensityCanvas=new GLCanvas();
        
        
        
       insideIntensityCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            public void keyPressed(KeyEvent e) {
             
            }
            
            public void keyReleased(KeyEvent e) {
                if(e.getKeyCode()==KeyEvent.VK_C){
                    // show color, red&blue, onlyred, only blue
                    redBlueShown++;
                    if(redBlueShown>3)redBlueShown=0;
                    switch(redBlueShown){
                        case 0: System.out.println("show red and blue"); break;
                        case 1: System.out.println("show only red (Right)"); break;
                        case 2: System.out.println("show only blue (Left)"); break;
                        case 3: System.out.println("show only correspondances"); break;
                        default:;
                    }
                    insideIntensityCanvas.display();
                    
                }
                if(e.getKeyCode()==KeyEvent.VK_F){
                    showOnlyAcc=!showOnlyAcc;
                    insideIntensityCanvas.display();
                    
                }
                if(e.getKeyCode()==KeyEvent.VK_SPACE){
                    // displaytime
                    if(chip.getAeViewer().aePlayer.isPaused()){
                        chip.getAeViewer().aePlayer.resume();
                    } else {
                        chip.getAeViewer().aePlayer.pause();
                    }
                    
                }
            }
            
       });
        
        insideIntensityCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                
                int dx=insideIntensityCanvas.getWidth()-1;
                int dy=insideIntensityCanvas.getHeight()-1;
                
                // 4 is window's border width
//                    int x = Math.round((evt.getX()-4)  / intensityZoom);
//                    int y = Math.round((dy-evt.getY())  / intensityZoom);
                
                int x = (int)((evt.getX()-3)  / intensityZoom);
                int y = (int)((dy-evt.getY())  / intensityZoom);
                
                
                //   System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");
                //  System.out.println("width=" + dx + " heigt="+dy);
                
                
                
                highlight_x = x;
                highlight_y = y;
                
                
                if (evt.getButton()==1||evt.getButton()==2){
                    highlight = true;
                    // GL gl=insideIntensityCanvas.getGL();
                    
                    
                } else {
                    highlight = false;
                }
                
                System.out.println("Selected pixel x,y=" + x + "," + y);
                
                int jr = y;// - yRightCorrection;
                int jl = y;//- yLeftCorrection;
                if(jr>=0&&jr<retinaSize&&jl>=0&&jl<retinaSize&&x>=0&&x<retinaSize){
                    EventPoint epL = leftPoints[x][jl];
                    EventPoint epR = rightPoints[x][jr];
                    
                    float flr=-1;
                    float link=-1;
                    if (evt.getButton()==1){
                       highlight_xR = epL.disparityLink;
                         if(highlight_xR>0&&highlight_xR<retinaSize){
                           EventPoint epLR = rightPoints[highlight_xR][jr];
                           
                           flr = epLR.getValue(rightTime+adjustTime);
                           
                         }                      
                    } else if (evt.getButton()==2){
                        highlight_xR = epR.attachedTo;
                        
                        if(highlight_xR>0&&highlight_xR<retinaSize){
                            EventPoint epLR = leftPoints[highlight_xR][jr];
                            
                            flr = epLR.getValue(leftTime);
                            link = epLR.disparityLink;
                        }
                    }
                                                         
                    float fr=0;
                    float fl=0;
                    
                    
                    fr = epR.getValue(rightTime+adjustTime);
                    fl = epL.getValue(leftTime);
                    
                    
                    if (flr>-1) {
                        if (evt.getButton()==1){
                              System.out.println("Left("+x+","+jl+")=" + fl + " linked to right("+highlight_xR+","+jr+")="+flr);
                              System.out.println("with z:"+leftPoints[x][y].z+" and z0:"+leftPoints[x][y].z0);
                        } else if (evt.getButton()==2){
                              System.out.println("Right("+x+","+jr+")=" + fr + " linked to right("+highlight_xR+","+jl+")="+flr+" dlink:"+link);
                              System.out.println("with z:"+leftPoints[highlight_xR][y].z+" and z0:"+leftPoints[highlight_xR][y].z0);
                     
                        }
                        
                    } else {
                        System.out.println("Left("+x+","+jl+")=" + fl + " not linked");
                    }
                    
                    
                    System.out.println("+ Right("+x+","+jr+")="+fr);
                }
                
                if(showCorrectionMatrix){
                    if(y>=0&&y<retinaSize&&x>=0&&x<retinaSize){
                        
                        if(correctionMatrix==null){
                            System.out.println("correctionMatrix==null");
                        } else {
                            System.out.println("correctionMatrix value="+correctionMatrix[x][y]);
                        }
                    } else {
                        System.out.println("out of correctionMatrix bound");
                    }
                }
                
                
                
                
                insideIntensityCanvas.display();
                
            }
            public void mouseReleased(MouseEvent e){
                
                
            }
        });
        
        
        insideIntensityCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            
            private void  drawIntMatrix( int[][] intMatrix, GL gl) {
                for (int i = 0; i<intMatrix.length; i++){
                    for (int j = 0; j<intMatrix[i].length; j++){
                        float f = (float)intMatrix[i][j];// /(float)retinaSize;
                        if(f>0){
                            //gl.glColor3f(f,f,f);
                            if (f%2==0)
                                gl.glColor3f(1,1,1);
                            else
                                gl.glColor3f(0,0,0);
                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                        }
                    }
                }
                
            }
            
            
            
            private void drawEventPoints( EventPoint[][] eventPoints, GL gl, int currentTime, boolean left, int yCorrection ){
                for (int i = 0; i<eventPoints.length; i++){
                    for (int j = 0; j<eventPoints[i].length; j++){
                        EventPoint ep = eventPoints[i][j];
                        if(ep==null)break;
                        float f=0;
                        float b=0;
                        float g=0;
                        float r=0;
                        
                        
                        // f = ep.accValue - decayedValue(ep.accValue,currentTime-ep.updateTime);
                        if (showOnlyAcc) {
                            f = ep.getAccValue(currentTime);
                        } else {
                            f = ep.getValue(currentTime);
                        }
                        
                        //   f = ep.accValue;
                        
                        
                        // if(!left&&showShadows){
                        //     System.out.println("display drawEventPoints f:"+f+" time: "+currentTime);
                        // }
                        
                        //    if (f>0) System.out.println("display drawEventPoints f:"+f);
//                            if(showSecondFilter){
//                               // if(showTopography)
//                               //     f = obtainedDensity(ep.largeFilteredValue,lowFilter_density2,invDensity2,densities2);
//                               // else  f = ep.largeFilteredValue;
//
//                            } else if(showOnlyAcc){
//                               // f = ep.accValue;
//                                           } else if(showDecay){
//
//                               // f = ep.getDecayedFilteredValue(currentTime);
//
//                            } else {
//                                //f = ep.shortFilteredValue;
//                              //  if(showTopography)
//                              //  f = obtainedDensity(ep.shortFilteredValue,lowFilter_density,invDensity1,densities);
//                              //  else f = ep.shortFilteredValue;
//                            }
//
//                            if(showShape){
//                              //  if(ep.border) b = 1-f;
//
//                            }
//                            if(showShapePoints){
//                               // if(ep.decayedBorder) r = 1-f;
//                            }
//
//                            if(showFingerPoints){
//                               // if(ep.decayedBorder&&ep.border){
//                               //     r = 1-f;
//                               //     b = 1-f;
//                               // }
//
//
//                            }
//
//                            if(ep.shortFilteredValue>in_value_threshold){
//                            if(showSkeletton){
//                                //if(ep.skValue>sk_threshold){
//                                //   g = ep.skValue;
//                               // }
//                            }
//                            }
                        
                        
                        
                        //    if(i==72&&j==65) System.out.println("left:"+left+" value("+i+","+j+"):"+f);
                        
                        
                        if(f>valueThreshold){
                            f = f*brightness;
                            if(!showRLColors){
                                gl.glColor3f(f,f,f);
                                gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                            } else {
                                if(left){
                                    if(redBlueShown==0||redBlueShown==2){
                                        if(ep.disparityLink>-1){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        } else {
                                            gl.glColor3f(0,0,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        }
                                    } else if(redBlueShown==3){
                                        if(ep.disparityLink>-1){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                      //  gl.glColor3f(0,0,f);
                                      //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                    } 
                                } else {
                                    if(redBlueShown==0||redBlueShown==1){
                                        
                                        if(ep.attachedTo>-1){
                                            gl.glColor3f(f,0.5f,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        } else {
                                            gl.glColor3f(f,0,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        }
                                    } else if(redBlueShown==3){  
                                        if(ep.attachedTo>-1){
                                            gl.glColor3f(f,0.5f,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                      //gl.glColor3f(f,0,0);
                                      //gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                    } 
                                }
                            }
                            //  gl.glRectf(i*intensityZoom,(j+yCorrection)*intensityZoom,(i+1)*intensityZoom,(j+yCorrection+1)*intensityZoom);
                          //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                        }
                        
                    }
                }
                
            }
            
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
                
                
                // display inside intensity
                
                // System.out.println("display left - right");
                if(showCorrectionMatrix){
                    drawIntMatrix(correctionMatrix,gl);
                    
                    
                } else if(showAcc){
                    //  System.out.println("display left - right  showAcc");
                    if(leftPoints!=null){
                        // System.out.println("display left ");
                        
                        drawEventPoints(leftPoints,gl,leftTime,true,yLeftCorrection);
                    } else {
                        System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                    }
                    if(rightPoints!=null){
                        //  System.out.println("display right ");
                        
                        drawEventPoints(rightPoints,gl,rightTime+adjustTime,false,yRightCorrection);
                    } else {
                        System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                    }
                    
                } else {
                    
                    // System.out.println("display left - right not showAcc");
                 /*
                float max = 1;
                if(scaleIntensity) max = findArrayMax(insideIntensities);
                for(int i=0;i<insideIntensities.length;i++){
                    for(int j=0;j<insideIntensities[i].length;j++){
                        float f = 0f;
                        if(scaleIntensity){
                            f = insideIntensities[i][j][0]/max;
                        } else {
                            f = insideIntensities[i][j][0];
                            if (f<in_threshold){
                                f = 0;
                            }
                        }
                        // f is scaled between 0-1
                        gl.glColor3f(f,f,f);
                        gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                  
                    }
                }
                  */
                }
                if(showZones){
                    // draw door
                    //gl.glColor3f(0,1,0);
                    //  drawBox(gl,door_xa*intensityZoom,door_xb*intensityZoom,door_ya*intensityZoom,door_yb*intensityZoom);
                }
                
                
                if(showThin){
                    /*
                    for(int i=0;i<thinned.length;i++){
                        for(int j=0;j<thinned[i].length;j++){
                            float f = (float)thinned[i][j][0];
                     
                            if(f>0){
                                gl.glColor3f(0,0,f);
                                gl.glRectf(j*intensityZoom,i*intensityZoom,(j+1)*intensityZoom,(i+1)*intensityZoom);
                            }
                        }
                    }
                     **/
                }
                
                if(showPalm){
                    /*
                    // replace nbFingerAttached by something else
                      gl.glColor3f(1,0,1);
                     
                      for(int i=0;i<MAX_NB_FINGERS;i++){
                        // trace all fingers
                        FingerCluster f = palm.fingers[i];
                        if(f!=null){ // &&f.activated
                            if(f.length_tip!=0){
                     
                                gl.glRectf(f.tip_x_start*intensityZoom,f.tip_y_start*intensityZoom,(f.tip_x_start+1)*intensityZoom,(f.tip_y_start+1)*intensityZoom);
                     
                     
                                gl.glBegin(GL.GL_LINES);
                                {
                                    gl.glVertex2i(f.tip_x_start*intensityZoom,f.tip_y_start*intensityZoom);
                                    gl.glVertex2i(f.tip_x_end*intensityZoom,f.tip_y_end*intensityZoom);
                                }
                                gl.glEnd();
                     
                                gl.glRasterPos3f((f.tip_x_start+3)*intensityZoom,f.tip_y_start*intensityZoom,0);
                                chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f", f.direction_tip ));
                     
                            }
                            if(f.length_base!=0){
                                gl.glBegin(GL.GL_LINES);
                                {
                                    gl.glVertex2i(f.base_x_start*intensityZoom,f.base_y_start*intensityZoom);
                                    gl.glVertex2i(f.base_x_end*intensityZoom,f.base_y_end*intensityZoom);
                     
                                }
                                gl.glEnd();
                     
                                gl.glRasterPos3f((f.base_x_start+3)*intensityZoom,f.base_y_start*intensityZoom,0);
                                chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f", f.direction_base ));
                            }
                        }
                     
                    }
                     */
                }
                
                if(showSkeletton){
                    /*
                    gl.glColor3f(0,0,1);
                    for(int i=0;i<nbBones;i++){
                        Segment o = bones[i];
                     
                        //  draw line here and
                        gl.glBegin(GL.GL_LINES);
                        {
                     
                     
                            gl.glVertex2i(o.x1*intensityZoom,o.y1*intensityZoom);
                            gl.glVertex2i(o.x2*intensityZoom,o.y2*intensityZoom);
                     
                     
                        }
                        gl.glEnd();
                    }
                     
                    for(Object o:nodes){
                        Node n = (Node)o;
                         if(n.type==0) gl.glColor3f(1,1,0);
                         if(n.type==1) gl.glColor3f(0,1,1);
                         gl.glRectf(n.x*intensityZoom,n.y*intensityZoom,(n.x+1)*intensityZoom,(n.y+1)*intensityZoom);
                     
                         gl.glRasterPos3f((n.x+3)*intensityZoom,n.y*intensityZoom,0);
                     
                         chip.getCanvas().getGlut().glutBitmapString(font, String.format("%d", n.support ));
                    }
                     
                     **/
                }
                
                if(grasp_started ){
                    if(showSegments){
                        /*
                        gl.glColor3f(0,1,0);
                        for(int i=0;i<nbSegments;i++){
                            Segment o = segments[i];
                            int midx = o.midx();
                            int midy = o.midy();
                            //  draw line here and
                            gl.glBegin(GL.GL_LINES);
                            {
                         
                         
                                gl.glVertex2i(o.x1*intensityZoom,o.y1*intensityZoom);
                                gl.glVertex2i(o.x2*intensityZoom,o.y2*intensityZoom);
                         
                        // gl.glColor3f(1,0,0);
                        // gl.glVertex2i(midx*intensityZoom,midy*intensityZoom);
                        // gl.glVertex2i(o.xi*intensityZoom,o.yi*intensityZoom);
                        // gl.glColor3f(0,0,1);
                        // gl.glVertex2i(midx*intensityZoom,midy*intensityZoom);
                       //  gl.glVertex2i(o.xo*intensityZoom,o.yo*intensityZoom);
                         
                         
                            }
                            gl.glEnd();
                         
                        }
                         */
                    }
                    if(showShapePoints){
                /*
                    for (int i=0;i<contour.getMaxX();i++){
                        for (int j=0;j<contour.getMaxY();j++){
                            if (contour.eventsArray[i][j]!=null){
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
                                            gl.glVertex2i(i*intensityZoom,j*intensityZoom);
                                            gl.glVertex2i((i+1)*intensityZoom,j*intensityZoom);
                                            gl.glVertex2i((i+1)*intensityZoom,(j+1)*intensityZoom);
                                            gl.glVertex2i(i*intensityZoom,(j+1)*intensityZoom);
                                        }
                                        gl.glEnd();
                                    }
                                }
                            }
                        }
                    }
                 **/
                    }
                }
                
                
                
                if(highlight){
                    gl.glColor3f(1,1,0);
                    gl.glRectf(highlight_x*intensityZoom,highlight_y*intensityZoom,(highlight_x+1)*intensityZoom,(highlight_y+1)*intensityZoom);
                    gl.glColor3f(0,1,0);
                    gl.glRectf(highlight_xR*intensityZoom,highlight_y*intensityZoom,(highlight_xR+1)*intensityZoom,(highlight_y+1)*intensityZoom);
                    
                }
                
                /**
                 * if(showClusters){
                 * gl.glColor3f(0,0,1);
                 * for(int i=0;i<fingerTipClusters.length;i++){
                 * FingerCluster fc = fingerTipClusters[i];
                 * gl.glRectf(fc.x*intensityZoom,(fc.y+1)*intensityZoom,(fc.x+1)*intensityZoom,(fc.y+2)*intensityZoom);
                 *
                 *
                 * // finger lines
                 * if(fc.finger_base_x!=0&&fc.finger_base_y!=0){
                 * gl.glBegin(GL.GL_LINES);
                 * {
                 * //gl.glColor3f(0,0,1);
                 * gl.glVertex2i(fc.x*intensityZoom,fc.y*intensityZoom);
                 * gl.glVertex2f(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                 *
                 * }
                 * gl.glEnd();
                 * }
                 *
                 * // knuckles polygon
                 * if(knuckle_polygon_created){
                 * gl.glBegin(GL.GL_LINES);
                 * {
                 * //gl.glColor3f(0,0,1);
                 * if(fc.neighbour1!=null){
                 * gl.glVertex2i(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                 * gl.glVertex2f(fc.neighbour1.finger_base_x*intensityZoom,fc.neighbour1.finger_base_y*intensityZoom);
                 * }
                 * }
                 * gl.glEnd();
                 * gl.glBegin(GL.GL_LINES);
                 * {
                 * //gl.glColor3f(0,0,1);
                 * if(fc.neighbour2!=null){
                 * gl.glVertex2i(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                 * gl.glVertex2f(fc.neighbour2.finger_base_x*intensityZoom,fc.neighbour2.finger_base_y*intensityZoom);
                 * }
                 * }
                 * gl.glEnd();
                 *
                 *
                 *
                 * }
                 * }
                 * }
                 */
                
                //                gl.glColor3f(1,0,0);
//                for(int i=0;i<fingerTips.size();i++){
//                    Point sc = (Point)fingerTips.elementAt(i);
//                    gl.glRectf(sc.x*intensityZoom,sc.y*intensityZoom,(sc.x+1)*intensityZoom,(sc.y+1)*intensityZoom);
////                    int range=70;
////                    for(int k=sc.x-range;k<sc.x+range+1;k++){
////                      for(int j=sc.y-range;j<sc.y+range+1;j++){
////                        if(((k<=sc.x-range)||(k>=sc.x+range))||((j<=sc.y-range)||(j>=sc.y+range))){
////                        gl.glRectf(k*intensityZoom,j*intensityZoom,(k+1)*intensityZoom,(j+1)*intensityZoom);
////
////
////                        }
////                      }
////                    }
//
//                }
                
               /*
                for(int i=0;i<MAX_ORIENTATIONS;i++){
                    for(int j=0;j<MAX_DISTGC;j++){
                        //
                        int acc = segments[i][j];
                        float f = (float)acc/(float)accOrientationMax;
                        //int size = new Float(((float)acc/(float)accOrientationMax)*10).intValue();
                        //float f=acc;///accOrientationMax;
                        gl.glColor3f(0,f,f);
                        //gl.glRecti(j,i,j+1,i+1);
                        gl.glRecti(j,i,j+1,i+1);
//                        gl.glRectf(new Double(o.distGC*10).floatValue(),new Double(o.orientation).floatValue(),
//                                new Double(o.distGC*10+1).floatValue(),new Double(o.orientation+1).floatValue());
//
                
                
                        //if (acc>0)System.out.println("add point ("+i+","+j+") acc: "+acc+" accmax: "+accOrientationMax+" size: "+size);
                    }
                }
                */
                
                //gl.glPointSize(6);
                //gl.glColor3f(1,0,0);
                //gl.glBegin(GL.GL_POINTS);
                //gl.glVertex2f(thetaMaxIndex, rhoMaxIndex);
                //gl.glEnd();
//                if(glut==null) glut=new GLUT();
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    // if(glu==null) glu=new GLU();
                    //log.warning("GL error number "+error+" "+glu.gluErrorString(error));
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
    
    
    
    void check3DFrame(){
        if(showWindow && a3DFrame==null||showWindow && windowDeleted) {
            windowDeleted = false;
            create3DFrame();
        }
    }
    
    JFrame a3DFrame=null;
    GLCanvas a3DCanvas=null;
    int dragOrigX =0;
    int dragOrigY =0;
    int dragDestX =0;
    int dragDestY =0;
    boolean leftDragged = false;
    // boolean rightDragreleased = false;
    float tx =0;
    float ty =0;
    float origX=0;
    float origY=0;
    
    float tz = 1;
    
    int rdragOrigX =0;
    int rdragOrigY =0;
    int rdragDestX =0;
    int rdragDestY =0;
    boolean rightDragged = false;
    float rtx =0;
    float rty =0;
    float rOrigX=0;
    float rOrigY=0;
    // keyboard rotation
    
    float krx = 0;
    float kry = 0;;
    
//    GLUT glut=null;
    void create3DFrame(){
        a3DFrame=new JFrame("3D Static Frame");
        a3DFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        
        a3DFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                Frame frame = (Frame)evt.getSource();
                
                // Hide the frame
                frame.setVisible(false);
                
                // If the frame is no longer needed, call dispose
                frame.dispose();
                
                windowDeleted = true;
                showWindow = false;
            }
        });
        
        
        a3DCanvas=new GLCanvas();
        
        
        
        a3DCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK,  but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    
                    if(e.getClickCount()==2){
                        // reset
                        tx =0;
                        ty =0;
                        origX=0;
                        origY=0;
                        rtx =0;
                        rty =0;
                        rOrigX=0;
                        rOrigY=0;
                        tz = 1;
                        
                    } else {
                        // get final x,y for translation
                        dragOrigX = x;
                        dragOrigY = y;
                        
                    }
                    //   System.out.println(" x:"+x+" y:"+y);
                    // System.out.println("Left mousePressed tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                    
                } else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // get final x,y for translation
                    rdragOrigX = x;
                    rdragOrigY = y;
                    //   System.out.println(" x:"+x+" y:"+y);
                    // System.out.println("Right mousePressed rtx:"+rtx+" rty:"+rty+" rOrigX:"+rOrigX+" rOrigY:"+rOrigY);
                    
                }
                
                
//
//                    a3DCanvas.display();
//
            }
            
            public void mouseReleased(MouseEvent e){
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK,  but3mask = InputEvent.BUTTON3_DOWN_MASK;
                
                if(e.getButton()==MouseEvent.BUTTON1){
                    origX += tx;
                    origY += ty;
                    tx = 0;
                    ty = 0;
                    leftDragged = false;
                    // dragreleased = true;
                    a3DCanvas.display();
                    //System.out.println("Left mouseReleased tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                } else  if(e.getButton()==MouseEvent.BUTTON3){
                    rOrigX += rtx;
                    rOrigY += rty;
                    rtx = 0;
                    rty = 0;
                    rightDragged = false;
                    // dragreleased = true;
                    a3DCanvas.display();
                    // System.out.println("Right mouseReleased tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                }
            }
        });
        
        
        a3DCanvas.addMouseMotionListener(new MouseMotionListener(){
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK,  but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    // get final x,y for translation
                    
                    dragDestX = x;
                    dragDestY = y;
                    
                    leftDragged = true;
                    a3DCanvas.display();
                    
                    
                } else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // get final x,y for translation
                    
                    rdragDestX = x;
                    rdragDestY = y;
                    
                    rightDragged = true;
                    a3DCanvas.display();
                    
                    
                }
            }
            
            public void mouseMoved(MouseEvent e) {
            }
        });
        
        
        a3DCanvas.addMouseWheelListener(new MouseWheelListener(){
            
            public void mouseWheelMoved(MouseWheelEvent e) {
                int notches = e.getWheelRotation();
                tz += notches;
                //System.out.println("mouse wheeled tz:"+tz);
                a3DCanvas.display();
            }
            
            
            
        });
        
        a3DCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            /** Handle the key-pressed event from the text field. */
            public void keyPressed(KeyEvent e) {
                //System.out.println("event time: "+e.getWhen()+ " system time:"+	System.currentTimeMillis());
                
                if(System.currentTimeMillis()-e.getWhen()<100){
                    if(e.getKeyCode()==KeyEvent.VK_LEFT){
                        // move
                        krx-=3;//speed
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_RIGHT){
                        // move
                        krx+=3;
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_DOWN){
                        // move
                        kry-=3;
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_UP){
                        // move
                        kry+=3;
                        a3DCanvas.display();
                    }
                }
            }
            
            /** Handle the key-released event from the text field. */
            public void keyReleased(KeyEvent e) {
                if(e.getKeyCode()==KeyEvent.VK_T){
                    // displaytime
                    System.out.println("current time: "+currentTime);
                }
                if(e.getKeyCode()==KeyEvent.VK_SPACE){
                    // displaytime
                    if(chip.getAeViewer().aePlayer.isPaused()){
                        chip.getAeViewer().aePlayer.resume();
                    } else {
                        chip.getAeViewer().aePlayer.pause();
                    }
                    
                }
                
//                 if(e.getKeyCode()==KeyEvent.VK_LEFT){
//                    // move
//                    krx = 0;
//                    a3DCanvas.display();
//                }
//                if(e.getKeyCode()==KeyEvent.VK_RIGHT){
//                    // move
//                    krx = 0;
//                    a3DCanvas.display();
//                }
//                 if(e.getKeyCode()==KeyEvent.VK_DOWN){
//                    // move
//                    kry = 0;
//                    a3DCanvas.display();
//                }
//                 if(e.getKeyCode()==KeyEvent.VK_UP){
//                    // move
//                    kry = 0;
//                    a3DCanvas.display();
//                }
            }
            
            
            
        });
        
        
        
        a3DCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            
            
       private void draw3DDisparityPoints( GL gl ){
                int z = 0;
                float fx = 0;
                float fy = 0;
                float fz = 0;
                float dl = 0;
                float dr = 0;
                int dx = -1;
                int z0 = 0;
                int x0 = 0;
                
                //    System.out.println("draw3DDisparityPoints");
                
                int half = retinaSize/2;
                
                for(int x=0;x<leftPoints.length;x++){
                    for(int y=0;y<leftPoints[x].length;y++){
                        if(leftPoints[x][y]==null){
                            //  System.out.println("leftpoints["+x+"]["+y+"] null");
                            break;
                        }
                        
                        
                        
                        
                        dx = leftPoints[x][y].disparityLink;
                        if(dx>-1){
                            z = (dx - x); // * -zFactor;
                            
                            //debug
                            leftPoints[x][y].z=z;
//                       x0 = Math.round((float) ( (Math.cos(Math.toRadians(planeAngle))*(x-half)) -
//                               (Math.cos(Math.toRadians(90-planeAngle))*(z-half)) ))+half;
//                       z0 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(x-half)) +
//                               (Math.cos(Math.toRadians(planeAngle))*(z-half)) ))+half;
//
//                            x0 = Math.round((float) (   -
//                                    (Math.sin(Math.toRadians(planeAngle))*(z)) ))+x;
//                            z0 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(x)) +
//                                    (Math.cos(Math.toRadians(planeAngle))*(z-x)) ))+x;
//                            
                               // rotate point x=x-x z=z-x if angle = 0. translation x
                            x0 = Math.round((float) ( -
                                    (Math.sin(Math.toRadians(planeAngle))*(z)) ))+x;
                            z0 = Math.round((float) (
                                    (Math.cos(Math.toRadians(planeAngle))*(z)) ));

                            
                            //debug
                            leftPoints[x][y].z0=z0;
                            boolean  highlighted = false;
                            if(highlight){
                                //if(x==highlight_x&&y+yLeftCorrection==highlight_y){
                                if(x==highlight_x&&y==highlight_y){
                                    shadowCube(gl, x0, y, z0, cube_size, 1, 1, 0, 1, shadowFactor);
                                    highlighted = true;
                                }
                            }
                            
                            if (!highlighted){
                                
                                if(showShadows){
                                    shadowCube(gl, x0, y, z0, cube_size, 1, 1, 1, alpha, shadowFactor);
                                    //   System.out.println("draw3DDisparityPoints shadowCube "+1);
                                } else {
                                    //gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                    //gl.glEnable(gl.GL_BLEND);
                                    
                                    dl = leftPoints[x][y].getValue(leftTime); // or getAccValue()?
                                    dr = rightPoints[dx][y].getValue(rightTime);
                                    
                                    float f = (dl+dr)/2;
                                    //f = f*brightness;
                                    if(showXColor){
                                        fx = colorizeFactor*(x%colorizePeriod);
                                    } else {
                                        fx = f;
                                    }
                                    if(showYColor){
                                        fy = colorizeFactor*(y%colorizePeriod);
                                    } else {
                                        fy = f;
                                    }
                                    if(showZColor){
                                        fz = colorizeFactor*((retina3DSize-z)%colorizePeriod);
                                    } else {
                                        fz = f;
                                    }
                                    float b = 0.1f*brightness;
                                    //   System.out.println("draw3DDisparityPoints shadowCube "+f);
                                    shadowCube(gl, x0, y, z0, cube_size, fz+b, fy*+b, fx+b, alpha, shadowFactor);
                                    
                                    //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                    //cube(gl,x,y,z,cube_size);
                                    
                                    
                                }
                            }
                            
                        } else if (dx==-2){ // if just removed
                            leftPoints[x][y].disparityLink = -1;
                            if(leftPoints[x][y].prevDisparityLink>-1){
                                
                                z = (leftPoints[x][y].prevDisparityLink - x);// * -zFactor;
                                
//                           x0 = Math.round((float) ( (Math.cos(Math.toRadians(planeAngle))*(x)) -
//                                   (Math.cos(Math.toRadians(90-planeAngle))*z) ));
//                           z0 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(x)) +
//                                   (Math.cos(Math.toRadians(planeAngle))*z) ));
//                             x0 = Math.round((float) ( (Math.cos(Math.toRadians(planeAngle))*(x-half)) -
//                               (Math.cos(Math.toRadians(90-planeAngle))*(z-half)) ))+half;
//                             z0 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(x-half)) +
//                               (Math.cos(Math.toRadians(planeAngle))*(z-half)) ))+half;
//                                
//                                x0 = Math.round((float) (  -
//                                        (Math.sin(Math.toRadians(planeAngle))*(z-x)) ))+x;
//                                z0 = Math.round((float) ( 
//                                        (Math.cos(Math.toRadians(planeAngle))*(z-x)) ))+x;
//                                
                                
//                                 x0 = Math.round((float) (   -
//                                    (Math.sin(Math.toRadians(planeAngle))*(z)) ))+x;
//                                 z0 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(x)) +
//                                    (Math.cos(Math.toRadians(planeAngle))*(z-x)) ))+x;
//                            
                                x0 = Math.round((float) ( -
                                        (Math.sin(Math.toRadians(planeAngle))*(z)) ))+x;
                                z0 = Math.round((float) (
                                        (Math.cos(Math.toRadians(planeAngle))*(z)) ));

                                // erase
                                
                                gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                gl.glEnable(gl.GL_BLEND);
                                
                                gl.glColor4f(0,0,0,0);
                                
                                //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                cube(gl,x0,y,z0,1);
                                //    System.out.println("draw3DDisparityPoints black cube");
                                gl.glDisable(gl.GL_BLEND);
                                
                            }
                        }
                    }
                }
                
            }
            
            
            
            
            
            private void draw3DEventPoints( GL gl ){
                float vl = 0;
                float vr = 0;
                float dl = 0;
                float dr = 0;
                
                
                float fx = 0;
                float fy = 0;
                float fz = 0;
                
                //    int size_adjust = cube_size/2;
                
                for(int x=0;x<retina3DSize;x++){
                    for(int y=0;y<retinaSize;y++){
                        for(int z=0;z<retina3DSize;z++){
                            
                            vl = points3D[x][y][z].lvalue;
                            dl = vl - decayedValue(vl,leftTime-points3D[x][y][z].leftTime);
                            
                            vr = points3D[x][y][z].rvalue;
                            dr = vr - decayedValue(vr,rightTime+adjustTime-points3D[x][y][z].rightTime);
                            
                            //float f = points3D[x][y][z].intensity;
                            
                            
                            //    if (x==114) System.out.println("draw3DEventPoints "+x+" "+y+" "+z+ " v:"+v);
                            if(dl+dr>stereoThreshold){
                                
                                float f = (dl+dr)/2;
                                // System.out.println("draw3DEventPoints "+x+" "+y+" "+z+ " v:"+v+" d:"+d);
                                
                                
                                if(showShadows){
                                    shadowCube(gl, x, y, z, cube_size, 1, 1, 1, alpha, shadowFactor);
                                    
                                } else {
                                    //gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                    //gl.glEnable(gl.GL_BLEND);
                                    
                                    if(showXColor){
                                        fx = colorizeFactor*(x%colorizePeriod);
                                    } else {
                                        fx = f;
                                    }
                                    if(showYColor){
                                        fy = colorizeFactor*(y%colorizePeriod);
                                    } else {
                                        fy = f;
                                    }
                                    if(showZColor){
                                        fz = colorizeFactor*((retina3DSize-z)%colorizePeriod);
                                    } else {
                                        fz = f;
                                    }
                                    
                                    shadowCube(gl, x, y, z, cube_size, fz, fy, fx, alpha, shadowFactor);
                                    
                                    //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                    //cube(gl,x,y,z,cube_size);
                                    
                                    
                                }
                                
                            } else if(vl+vr>stereoThreshold){
                                
                                // reset // not nice to do it here...
                                // points3D[x][y][z].lvalue = 0;
                                // points3D[x][y][z].rvalue = 0;
                                
                                gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                gl.glEnable(gl.GL_BLEND);
                                
                                gl.glColor4f(0,0,0,0);
                                
                                //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                cube(gl,x,y,z,1);
                                
                                gl.glDisable(gl.GL_BLEND);
                            }
                            
                            
                            
                        }
                    }
                }
            }
            
            
            
            private void draw3DColorPoints( GL gl , int time ){
                
                for(int x=0;x<retina3DSize;x++){
                    for(int y=0;y<retinaSize;y++){
                        for(int z=0;z<retina3DSize;z++){
                            float lv = points3D[x][y][z].lvalue;
                            float rv = points3D[x][y][z].rvalue;
                            float f = points3D[x][y][z].intensity;
                            
                            //  float d = v - decayedValue(v,currentTime-points3D[x][y][z].time);
                            //    if (x==114) System.out.println("draw3DEventPoints "+x+" "+y+" "+z+ " v:"+v);
                            if(f>0){
                                //   if(rv>0){
                                
                                
                                gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                gl.glEnable(gl.GL_BLEND);
                                
                                gl.glColor4f(rv,0,lv,alpha);
                                
                                //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                cube(gl,x,y,z,1);
                                
                                gl.glDisable(gl.GL_BLEND);
                                
                                
                            }
                            
                            
                        }
                    }
                }
                
                
                
            }
            
            
            private void draw3DAxes( GL gl ){
                // gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                // gl.glEnable(gl.GL_BLEND);
                //  gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//x
                gl.glColor3f(0.0f,1.0f,0.0f);
                line3D( gl,  0,  0,  0,  200 ,0 ,0);
                //   gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//y
                gl.glColor3f(0.0f,1.0f,0.0f);
                line3D( gl,  0,  0,  0,  0 , 200 ,0);
                //   line3D ( gl,  retinaSize/2, 5, 0,  retinaSize/2 , 5 ,0);
                //  gl.glColor4f(1.0f,0.0f,0.0f,0.2f);	//z
                gl.glColor3f(1.0f,0.0f,0.0f);
                line3D( gl,  0,  0,  0,  0 ,0 ,200);
                //   gl.glDisable(gl.GL_BLEND);
                
            }
            
            private void draw3DFrames( GL gl ){
                int half = retinaSize/2;
                //   int shift = (retina3DSize-retinaSize)/2;
                //    int half3D = 0; //retina3DSize/2;
              //  float frameAngle = planeAngle/2;
                //  int x0 =  shift;
                //  int z0 = half+half3D;
                
                
                
//                  int x0 = Math.round((float) ( (Math.cos(Math.toRadians(frameAngle))*(-half)) -
//                               (Math.cos(Math.toRadians(90-frameAngle))*(-half)) ))+half;
//                  int z0 = Math.round((float) ( (Math.sin(Math.toRadians(frameAngle))*(-half)) +
//                               (Math.cos(Math.toRadians(frameAngle))*(-half)) ))+half;
//
                
//                  int x1 = Math.round((float) ( (Math.cos(Math.toRadians(frameAngle))*(retinaSize-half)) -
//                               (Math.cos(Math.toRadians(90-frameAngle))*(-half)) ))+half;
//                  int z1 = Math.round((float) ( (Math.sin(Math.toRadians(frameAngle))*(retinaSize-half)) +
//                               (Math.cos(Math.toRadians(frameAngle))*(-half)) ))+half;
//
                
//                  int x2 = Math.round((float) ( (Math.cos(Math.toRadians(planeAngle))*(-half)) -
//                               (Math.cos(Math.toRadians(90-planeAngle))*(-retinaSize-half)) ))+half;
//                  int z2 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(-half)) +
//                               (Math.cos(Math.toRadians(planeAngle))*(-retinaSize-half)) ))+half;
//
                // rotate point x=0. z=retinaSize if angle = 0. translation 0
                int x2 = Math.round((float) ( -
                        (Math.sin(Math.toRadians(planeAngle))*(retinaSize)) ));
                int z2 = Math.round((float) (
                        (Math.cos(Math.toRadians(planeAngle))*(retinaSize)) ));
                
                // rotate point x=retinaSize-retinaSize z=retinaSize if angle = 0. translation retinaSize
                int x2b = Math.round((float) ( - 
                        (Math.sin(Math.toRadians(planeAngle))*(-retinaSize)) ))+retinaSize;
                int z2b = Math.round((float) ( 
                        (Math.cos(Math.toRadians(planeAngle))*(-retinaSize)) ));
                
                // obtain orthogonal direction to 0-x2
                float middleAngle = orientation(half,0,x2,z2) +90;
                
              
                                                                                
//                int x0 = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(-half)) -
//                        (Math.sin(Math.toRadians(middleAngle))*(-door_z-half)) ))+half;
//                int z0 = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(-half)) +
//                        (Math.cos(Math.toRadians(middleAngle))*(-door_z-half)) ))+half;
//                
//                
//                int x1 = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(retinaSize-half)) -
//                        (Math.sin(Math.toRadians(middleAngle))*(-door_z-half)) ))+half;
//                int z1 = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(retinaSize-half)) +
//                        (Math.cos(Math.toRadians(middleAngle))*(-door_z-half)) ))+half;
//                
               // rotate point x=0-half z=door_z if angle = 0. translation half
                
                int x0 = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(-half)) -
                        (Math.sin(Math.toRadians(middleAngle))*(door_z)) ))+half;
                int z0 = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(-half)) +
                        (Math.cos(Math.toRadians(middleAngle))*(door_z)) ));
                
                // rotate point x=retinaSize-half z=door_z if angle = 0. translation half
                int x1 = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(retinaSize-half)) -
                        (Math.sin(Math.toRadians(middleAngle))*(door_z)) ))+half;
                int z1 = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(retinaSize-half)) +
                        (Math.cos(Math.toRadians(middleAngle))*(door_z)) ));
                
                
                
                // blue frame
                
                if(showCage){
                    
                    gl.glColor3f(0.0f,0.0f,1.0f);	// blue color
                    line3D( gl,  x0,  0,  z0,  x1 ,0 ,z1);
                    line3D( gl,  x0,  0,  z0,  x0 , retinaSize ,z0);
                    line3D( gl,  x1,  0,  z1,  x1 , retinaSize ,z1);
                    line3D( gl,  x0,  retinaSize,  z0,  x1 ,retinaSize ,z1);
                    
                    
                    // cage
                    // rotate point x=door_xa-half z=door_z if angle = 0. translation half
                    int x0b = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(door_xa-half)) -
                            (Math.sin(Math.toRadians(middleAngle))*(door_z)) ))+half;
                    int z0b = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(door_xa-half)) +
                            (Math.cos(Math.toRadians(middleAngle))*(door_z)) ));
                    
                     // rotate point x=door_xb-half z=door_z if angle = 0. translation half
                    int x1b = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(door_xb-half)) -
                            (Math.sin(Math.toRadians(middleAngle))*(door_z)) ))+half;
                    int z1b = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(door_xb-half)) +
                            (Math.cos(Math.toRadians(middleAngle))*(door_z)) ));
                    
                    
                    line3D( gl,  x0b,  door_ya,  z0b,  x1b ,door_ya ,z1b);
                    line3D( gl,  x0b,  door_ya,  z0b,  x0b , retinaSize ,z0b);
                    line3D( gl,  x1b,  door_ya,  z1b,  x1b , retinaSize ,z1b);
                    line3D( gl,  x0b,  retinaSize,  z0b,  x1b ,retinaSize ,z1b);
                    
//                    int x0c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(-half)) -
//                            (Math.cos(Math.toRadians(90-frameAngle))*(-door_z-cage_depth-half)) ))+half;
//                    int z0c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(-half)) +
//                            (Math.cos(Math.toRadians(frameAngle))*(-door_z-cage_depth-half)) ))+half;
//                    
//                    
//                    int x1c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(retinaSize-half)) -
//                            (Math.cos(Math.toRadians(90-frameAngle))*(-door_z-cage_depth-half)) ))+half;
//                    int z1c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(retinaSize-half)) +
//                            (Math.cos(Math.toRadians(frameAngle))*(-door_z-cage_depth-half)) ))+half;
//                    
                    
//                    int x0c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle-90))*(-cage_depth-half)) -
//                            (Math.sin(Math.toRadians(middleAngle-90))*(-half)) ))+x0+half;
//                    int z0c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle-90))*(-cage_depth-half)) +
//                            (Math.cos(Math.toRadians(middleAngle-90))*(-half)) ))+z0+half;
//                    
//                    
//                    int x1c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle-90))*(-cage_depth-half)) -
//                            (Math.sin(Math.toRadians(middleAngle-90))*(-half)) ))+x1+half;
//                    int z1c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle-90))*(-cage_depth-half)) +
//                            (Math.cos(Math.toRadians(middleAngle-90))*(-half)) ))+z1+half;
//                    
                      // rotate point x=0-half z=door_z if angle = 0. translation half
                
                int x0c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(-half)) -
                        (Math.sin(Math.toRadians(middleAngle))*(door_z+cage_depth)) ))+half;
                int z0c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(-half)) +
                        (Math.cos(Math.toRadians(middleAngle))*(door_z+cage_depth)) ));
                
                // rotate point x=retinaSize-half z=door_z if angle = 0. translation half
                int x1c = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(retinaSize-half)) -
                        (Math.sin(Math.toRadians(middleAngle))*(door_z+cage_depth)) ))+half;
                int z1c = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(retinaSize-half)) +
                        (Math.cos(Math.toRadians(middleAngle))*(door_z+cage_depth)) ));
                
                    
                    
                    line3D( gl,  x0,  door_ya,  z0,  x1 ,door_ya ,z1);
                    line3D( gl,  x0c,  door_ya,  z0c,  x1c ,door_ya ,z1c);
                    line3D( gl,  x0,  door_ya,  z0,  x0c ,door_ya ,z0c);
                    line3D( gl,  x1,  door_ya,  z1,  x1c ,door_ya ,z1c);

                }
                
                gl.glFlush();
                
                if(showFrame){
                    // losange area
                    
                    gl.glColor3f(1.0f,0.0f,1.0f);
                    line3D( gl,  0,  0,  0,  x2 ,0 ,z2);
                    line3D( gl,  0,  retinaSize,  0,  x2 ,retinaSize ,z2);
                    line3D( gl,  x2,  0,  z2,  x2 ,retinaSize , z2);
                    line3D( gl,  x2,  0,  z2,  retinaSize ,0 , 0);
                    line3D( gl,  x2,  retinaSize,  z2,  retinaSize ,retinaSize , 0);
                    
                    gl.glColor3f(1.0f,0.5f,1.0f);
                    line3D( gl,  0,  0,  0,  x2b ,0 ,z2b);
                    line3D( gl,  0,  retinaSize,  0,  x2b ,retinaSize ,z2b);
                    line3D( gl,  x2b,  0,  z2b,  x2b ,retinaSize , z2b);
                    line3D( gl,  x2b,  0,  z2b,  retinaSize ,0 , 0);
                    line3D( gl,  x2b,  retinaSize,  z2b,  retinaSize ,retinaSize , 0);

                
                }
                
                if(showCorner){
                    
                    int x3 = Math.round((float) ( (Math.cos(Math.toRadians(middleAngle))*(10-half)) -
                            (Math.sin(Math.toRadians(middleAngle))*(door_z)) ))+half;
                    int z3 = Math.round((float) ( (Math.sin(Math.toRadians(middleAngle))*(10-half)) +
                            (Math.cos(Math.toRadians(middleAngle))*(door_z)) ));
//
//                       int x4 = Math.round((float) ( (Math.cos(Math.toRadians(planeAngle))*(-half)) -
//                               (Math.cos(Math.toRadians(90-planeAngle))*(-10-half)) ))+half;
//                       int z4 = Math.round((float) ( (Math.sin(Math.toRadians(planeAngle))*(-half)) +
//                               (Math.cos(Math.toRadians(planeAngle))*(-10-half)) ))+half;
                    int x4 = Math.round((float) (  -
                            (Math.sin(Math.toRadians(planeAngle))*(10)) ));
                    int z4 = Math.round((float) (
                            (Math.cos(Math.toRadians(planeAngle))*(10)) ));
                    
                    
                    gl.glColor3f(1.0f,1.0f,0.0f);	// blue color
                    line3D( gl,  x0,  0,  z0,  x0 ,10 ,z0);
                    line3D( gl,  x0,  0,  z0,  x3 , 0 ,z3);
                    
                    gl.glColor3f(1.0f,1.0f,1.0f);
                    line3D( gl,  0,  0,  0,  0 ,10 ,0);
                    line3D( gl,  0,  0,  0,  x4 , 0 ,z4);
                    
                    
                    
                }
                
            }
            
       
            
            private void draw3DEventPoints( EventPoint[][] eventPoints, int time, int timeCorrection, GL gl ){
                
                
                float half = retinaSize/2;
                boolean border = false;
                for (int i = 0; i<eventPoints.length; i++){
                    for (int j = 0; j<eventPoints[i].length; j++){
                        border = false;
                        EventPoint ep = eventPoints[i][j];
                        if(ep==null)break;
                        float f=0;
                        float b=0;
                        float g=0;
                        float r=0;
                        
                        if(showSecondFilter){
                            if(showTopography)
                                f = obtainedDensity(ep.largeFilteredValue,lowFilter_density2,invDensity2,densities2);
                            else  f = ep.largeFilteredValue;
                            
                        } else if(showOnlyAcc){
                            f = ep.accValue;
                            
                        } else if(showDecay){
                            
                            f = ep.getDecayedFilteredValue(time+timeCorrection);
                            
                        } else {
                            //f = ep.shortFilteredValue;
                            if(showTopography)
                                f = obtainedDensity(ep.shortFilteredValue,lowFilter_density,invDensity1,densities);
                            else f = ep.shortFilteredValue;
                        }
                        
                        if(showShape){
                            if(ep.border) b = 1-f;
                            
                        }
                        if(showShapePoints){
                            if(ep.decayedBorder) r = 1-f;
                        }
                        
                        if(showFingerPoints){
                            if(ep.decayedBorder&&ep.border){
                                r = 1-f;
                                b = 1-f;
                            }
                            
                            
                        }
                        
                        if(ep.shortFilteredValue>in_value_threshold){
                            if(showSkeletton){
                                //if(ep.skValue>sk_threshold){
                                g = ep.skValue;
                                // }
                            }
                        }
                        
                        f = f*brightness;
                        
                        
                        if(showFrame){
                            if (i==0){
                                f = 0;
                                r = 1;
                                b = 0;
                                g = 0;
                                border = true;
                            } else if(j==eventPoints[i].length-1){
                                f = 0;
                                g = 1;
                                r= 0;
                                b = 0;
                                border = true;
                                
                            } else if(i==0||j==0||i==eventPoints.length-1||j==eventPoints[i].length-1){
                                f = 0;
                                b = 1;
                                r = 0;
                                g = 0;
                                border = true;
                            }
                            
                        }
                        
                        
                        
                        if(border&&showFrame){
                            gl.glColor3f(r,g,b);
                            cube(gl,i-half,j,0,1);
                            
                        } else if(f>0){
                            
                            // gl.glColor3f(f+r,f+g,f+b);
                            gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                            gl.glEnable(gl.GL_BLEND);
                            f = f*intensity;
                            gl.glColor4f(f+r,f+g,f+b,alpha);
                            
                            //gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                            //cube(gl,i-half,j,1);
                            cubicRay(gl,i-half,j,1);
                            gl.glDisable(gl.GL_BLEND);
                            
                        } else {
                            // delete points by making them transparent
                            gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                            gl.glEnable(gl.GL_BLEND);
                            gl.glColor4f(0.0f,0.0f,0.0f,0.0f);
                            cube(gl,i-half,j,0,1);
                            cubicRay(gl,i-half,j,1);
                            gl.glDisable(gl.GL_BLEND);
                        }
                        
                    }
                }
                
            }
            
            private void line3D(GL gl, float x, float y, float z, float x2,  float y2,  float z2) {
                gl.glBegin(gl.GL_LINES);
                gl.glVertex3f( x,y,z);
                gl.glVertex3f( x2,y2,z2);
                gl.glEnd();
            }
            
            private void shadowCube(GL gl, float x, float y, float z, float size, float r, float g, float b, float alpha, float shadow) {
                
                
                gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                gl.glEnable(gl.GL_BLEND);
                
                
                
                gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
                // light
                gl.glColor4f(r,g,b,alpha);
                
                gl.glVertex3f( x+size, y+size,z-size);	// Top Right Of The Quad (Top)
                gl.glVertex3f(x-size, y+size,z-size);	// Top Left Of The Quad (Top)
                gl.glVertex3f(x-size, y+size, z+size);	// Bottom Left Of The Quad (Top)
                gl.glVertex3f( x+size, y+size, z+size);	// Bottom Right Of The Quad (Top)
                
                
                gl.glVertex3f( x+size, y+size,z-size);	// Top Right Of The Quad (Right)
                gl.glVertex3f( x+size, y+size, z+size);	// Top Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size, z+size);	// Bottom Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size,z-size);	// Bottom Right Of The Quad (Right)
                
                
                gl.glVertex3f( x+size, y+size, z+size);	// Top Right Of The Quad (Front)
                gl.glVertex3f(x-size, y+size, z+size);	// Top Left Of The Quad (Front)
                gl.glVertex3f(x-size,y-size, z+size);	// Bottom Left Of The Quad (Front)
                gl.glVertex3f( x+size,y-size, z+size);	// Bottom Right Of The Quad (Front)
                
                
                // shade
                gl.glColor4f(r-shadow,g-shadow,b-shadow,alpha);
                
                gl.glVertex3f( x+size,y-size, z+size);	// Top Right Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size, z+size);	// Top Left Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size,z-size);	// Bottom Left Of The Quad (Bottom)
                gl.glVertex3f( x+size,y-size,z-size);	// Bottom Right Of The Quad (Bottom)
                
                
                gl.glVertex3f( x+size,y-size,z-size);	// Top Right Of The Quad (Back)
                gl.glVertex3f(x-size,y-size,z-size);	// Top Left Of The Quad (Back)
                gl.glVertex3f(x-size, y+size,z-size);	// Bottom Left Of The Quad (Back)
                gl.glVertex3f( x+size, y+size,z-size);	// Bottom Right Of The Quad (Back)
                
                gl.glVertex3f(x-size, y+size, z+size);	// Top Right Of The Quad (Left)
                gl.glVertex3f(x-size, y+size,z-size);	// Top Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size,z-size);	// Bottom Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size, z+size);	// Bottom Right Of The Quad (Left)
                
                gl.glEnd();
                gl.glDisable(gl.GL_BLEND);
            }
            
            private void cube(GL gl, float x, float y, float z, float size) {
                // gl.glTranslatef(100.0f, 100.0f,0.0f);
                
                //  gl.glRotatef(rotation,0.0f,1.0f,0.0f);	// Rotate The cube around the Y axis
                //  gl.glRotatef(rotation,1.0f,1.0f,1.0f);
                gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
                //   gl.glColor3f(0.0f,1.0f,0.0f);	// Color Blue
                gl.glVertex3f( x+size, y+size,z-size);	// Top Right Of The Quad (Top)
                gl.glVertex3f(x-size, y+size,z-size);	// Top Left Of The Quad (Top)
                gl.glVertex3f(x-size, y+size, z+size);	// Bottom Left Of The Quad (Top)
                gl.glVertex3f( x+size, y+size, z+size);	// Bottom Right Of The Quad (Top)
                //   gl.glColor3f(1.0f,0.5f,0.0f);	// Color Orange
                gl.glVertex3f( x+size,y-size, z+size);	// Top Right Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size, z+size);	// Top Left Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size,z-size);	// Bottom Left Of The Quad (Bottom)
                gl.glVertex3f( x+size,y-size,z-size);	// Bottom Right Of The Quad (Bottom)
                //  gl.glColor3f(1.0f,0.0f,0.0f);	// Color Red
                gl.glVertex3f( x+size, y+size, z+size);	// Top Right Of The Quad (Front)
                gl.glVertex3f(x-size, y+size, z+size);	// Top Left Of The Quad (Front)
                gl.glVertex3f(x-size,y-size, z+size);	// Bottom Left Of The Quad (Front)
                gl.glVertex3f( x+size,y-size, z+size);	// Bottom Right Of The Quad (Front)
                //  gl.glColor3f(1.0f,1.0f,0.0f);	// Color Yellow
                gl.glVertex3f( x+size,y-size,z-size);	// Top Right Of The Quad (Back)
                gl.glVertex3f(x-size,y-size,z-size);	// Top Left Of The Quad (Back)
                gl.glVertex3f(x-size, y+size,z-size);	// Bottom Left Of The Quad (Back)
                gl.glVertex3f( x+size, y+size,z-size);	// Bottom Right Of The Quad (Back)
                //  gl.glColor3f(0.0f,0.0f,1.0f);	// Color Blue
                gl.glVertex3f(x-size, y+size, z+size);	// Top Right Of The Quad (Left)
                gl.glVertex3f(x-size, y+size,z-size);	// Top Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size,z-size);	// Bottom Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size, z+size);	// Bottom Right Of The Quad (Left)
                //  gl.glColor3f(1.0f,0.0f,1.0f);	// Color Violet
                gl.glVertex3f( x+size, y+size,z-size);	// Top Right Of The Quad (Right)
                gl.glVertex3f( x+size, y+size, z+size);	// Top Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size, z+size);	// Bottom Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size,z-size);	// Bottom Right Of The Quad (Right)
                gl.glEnd();
                
                // rotation += 0.9f;
                
            }
            
            
            private void cubicRay(GL gl, float x, float y, float size) {
                // gl.glTranslatef(100.0f, 100.0f,0.0f);
                float zsize = size*ray_length;
                //  gl.glRotatef(rotation,0.0f,1.0f,0.0f);	// Rotate The cube around the Y axis
                //  gl.glRotatef(rotation,1.0f,1.0f,1.0f);
                gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
                //   gl.glColor3f(0.0f,1.0f,0.0f);	// Color Blue
                gl.glVertex3f( x+size, y+size,-zsize);	// Top Right Of The Quad (Top)
                gl.glVertex3f(x-size, y+size,-zsize);	// Top Left Of The Quad (Top)
                gl.glVertex3f(x-size, y+size, zsize);	// Bottom Left Of The Quad (Top)
                gl.glVertex3f( x+size, y+size, zsize);	// Bottom Right Of The Quad (Top)
                //   gl.glColor3f(1.0f,0.5f,0.0f);	// Color Orange
                gl.glVertex3f( x+size,y-size, zsize);	// Top Right Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size, zsize);	// Top Left Of The Quad (Bottom)
                gl.glVertex3f(x-size,y-size,-zsize);	// Bottom Left Of The Quad (Bottom)
                gl.glVertex3f( x+size,y-size,-zsize);	// Bottom Right Of The Quad (Bottom)
                //  gl.glColor3f(1.0f,0.0f,0.0f);	// Color Red
                gl.glVertex3f( x+size, y+size, zsize);	// Top Right Of The Quad (Front)
                gl.glVertex3f(x-size, y+size, zsize);	// Top Left Of The Quad (Front)
                gl.glVertex3f(x-size,y-size, zsize);	// Bottom Left Of The Quad (Front)
                gl.glVertex3f( x+size,y-size, zsize);	// Bottom Right Of The Quad (Front)
                //  gl.glColor3f(1.0f,1.0f,0.0f);	// Color Yellow
                gl.glVertex3f( x+size,y-size,-zsize);	// Top Right Of The Quad (Back)
                gl.glVertex3f(x-size,y-size,-zsize);	// Top Left Of The Quad (Back)
                gl.glVertex3f(x-size, y+size,-zsize);	// Bottom Left Of The Quad (Back)
                gl.glVertex3f( x+size, y+size,-zsize);	// Bottom Right Of The Quad (Back)
                //  gl.glColor3f(0.0f,0.0f,1.0f);	// Color Blue
                gl.glVertex3f(x-size, y+size, zsize);	// Top Right Of The Quad (Left)
                gl.glVertex3f(x-size, y+size,-zsize);	// Top Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size,-zsize);	// Bottom Left Of The Quad (Left)
                gl.glVertex3f(x-size,y-size, zsize);	// Bottom Right Of The Quad (Left)
                //  gl.glColor3f(1.0f,0.0f,1.0f);	// Color Violet
                gl.glVertex3f( x+size, y+size,-zsize);	// Top Right Of The Quad (Right)
                gl.glVertex3f( x+size, y+size, zsize);	// Top Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size, zsize);	// Bottom Left Of The Quad (Right)
                gl.glVertex3f( x+size,y-size,-zsize);	// Bottom Right Of The Quad (Right)
                gl.glEnd();
                
            }
            
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
                
                //System.out.println("display: system time:"+System.currentTimeMillis());
                
                
                if(leftDragged){
                    leftDragged = false;
                    tx = dragDestX-dragOrigX;
                    ty = dragOrigY-dragDestY;
                    
                }
                if(rightDragged){
                    rightDragged = false;
                    //    rtx = rdragDestX-rdragOrigX;
                    rtx = rdragOrigX-rdragDestX;
                    rty = rdragOrigY-rdragDestY;
                    
                }
                
                float ox = origX+tx;
                float oy = origY+ty;
                
                gl.glTranslatef(ox,oy,0.0f);
                // gl.glTranslatef(0.0f,0.0f,-tz*10);
                
                if(tz<1)tz=1;
                gl.glScalef(tz,tz,-tz);
                
                //  glu.gluLookAt(0.0, 0.0, tx, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
                
                
                float rx = rOrigX+rtx;
                float ry = rOrigY+rty;
                
                gl.glRotatef(ry+kry,1.0f,0.0f,0.0f);
                
                gl.glRotatef(rx+krx,0.0f,1.0f,0.0f);
                
                // keyboard rotation :
                rOrigY += kry;
                rOrigX += krx;
                kry = 0;
                krx = 0;
                // System.out.println("translate to origX:"+origX+" +tx:"+tx+"="+ox+" origY:"+origY+" +ty:"+ty+"="+oy+"   tz:"+tz);
                
                // cube(gl,10.0f);
                
                if(showAxes){
                    draw3DAxes(gl);
                }
                
                
                if(showAcc){
                    
                    draw3DDisparityPoints( gl );
                    
                    // draw3DEventPoints(gl);
                    
                    
                    //
//
//                    if(leftPoints!=null){
//                        // place plane
//
//                        draw3DEventPoints(leftPoints,leftTime,adjustTime,gl);
//                    } else {
//                        System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
//                    }
//                    if(rightPoints!=null){
//                        gl.glRotatef(planeAngle,0.0f,1.0f,0.0f);
//                        draw3DEventPoints(rightPoints,rightTime,0,gl);
//                    } else {
//                        System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
//                    }
                    
                }
                
            //    if(showFrame){
                    draw3DFrames(gl);
             //   }
                
                
                
                
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    //if(glu==null) glu=new GLU();
                    //log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                    System.out.println("GL error number "+error+" "+glu.gluErrorString(error));
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
        a3DFrame.getContentPane().add(a3DCanvas);
        a3DFrame.pack();
        a3DFrame.setVisible(true);
    }
    
    
    
    private boolean tooClose(int x1, int y1, int x2, int y2, int range){
        if((Math.abs(x2-x1)<range)&&(Math.abs(y2-y1)<range)){
            return true;
        }
        return false;
    }
    
    protected boolean nearDoor(int x, int y, int range){
        
        if((y>door_ya-range)&&(y<door_yb+range)){
            if((door_xa-x)<range&&(x-door_xb)<range){
                return true;
                
            }
            
        }
        return false;
        
    }
    
    protected boolean insideDoor(int x, int y){
        
        if((y>door_ya)&&(y<door_yb)){
            if((x>door_xa)&&(x<door_xb)){
                return true;
                
            }
            
        }
        return false;
        
    }
    
    
    
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
        reset();
    }
    
    
    
    
    
    
    
//    public float getMixingFactor() {
//        return mixingFactor;
//    }
//
//    public void setMixingFactor(float mixingFactor) {
//        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
//        this.mixingFactor = mixingFactor;
//        getPrefs().putFloat("PawTracker3.mixingFactor",mixingFactor);
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
//        getPrefs().putFloat("PawTracker3.surround",surround);
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
    
    
    
    
    
    
    
    
    
    
    
    
    //*** set get methods *************************************************8888
    
    
    
    synchronized public void setLine_threshold(float line_threshold) {
        this.line_threshold = line_threshold;
        //getPrefs().putFloat("PawTracker3.line_threshold",line_threshold);
    }
    synchronized public float getLine_threshold() {
        return line_threshold;
    }
    
    synchronized public void setLine_range(int line_range) {
        this.line_range = line_range;
        //getPrefs().putInt("PawTracker3.line_range",line_range);
    }
    synchronized public int getLine_range() {
        return line_range;
    }
    
    synchronized public void setLines_n_avg(int lines_n_avg) {
        this.lines_n_avg = lines_n_avg;
        //getPrefs().putInt("PawTracker3.lines_n_avg",lines_n_avg);
    }
    synchronized public int getLines_n_avg() {
        return lines_n_avg;
    }
    
    
    
    
    
    
    
    synchronized public void setCluster_lifetime(int cluster_lifetime) {
        this.cluster_lifetime = cluster_lifetime;
        //getPrefs().putInt("PawTracker3.cluster_lifetime",cluster_lifetime);
    }
    synchronized public int getCluster_lifetime() {
        return cluster_lifetime;
    }
    
    synchronized public void setScore_range(int score_range) {
        this.score_range = score_range;
        //getPrefs().putInt("PawTracker3.score_range",score_range);
    }
    synchronized public int getScore_range() {
        return score_range;
    }
    
    synchronized public void setScore_threshold(int score_threshold) {
        this.score_threshold = score_threshold;
        //getPrefs().putInt("PawTracker3.score_threshold",score_threshold);
    }
    synchronized public int getScore_threshold() {
        return score_threshold;
    }
    
    synchronized public void setScore_in_threshold(float score_in_threshold) {
        this.score_in_threshold = score_in_threshold;
        //getPrefs().putFloat("PawTracker3.score_in_threshold",score_in_threshold);
    }
    synchronized public float getScore_in_threshold() {
        return score_in_threshold;
    }
    
    synchronized public void setScore_sup_threshold(float score_sup_threshold) {
        this.score_sup_threshold = score_sup_threshold;
        //getPrefs().putFloat("PawTracker3.score_sup_threshold",score_sup_threshold);
    }
    synchronized public float getScore_sup_threshold() {
        return score_sup_threshold;
    }
    
    
    
    synchronized public void setLine2shape_thresh(float line2shape_thresh) {
        this.line2shape_thresh = line2shape_thresh;
        //getPrefs().putFloat("PawTracker3.line2shape_thresh",line2shape_thresh);
    }
    synchronized public float getLine2shape_thresh() {
        return line2shape_thresh;
    }
    
    
    
    
    
    synchronized public void setShapeLimit(float shapeLimit) {
        this.shapeLimit = shapeLimit;
        //getPrefs().putFloat("PawTracker3.shapeLimit",shapeLimit);
    }
    synchronized public float getShapeLimit() {
        return shapeLimit;
    }
    
    synchronized public void setShapeDLimit(float shapeDLimit) {
        this.shapeDLimit = shapeDLimit;
        //getPrefs().putFloat("PawTracker3.shapeDLimit",shapeDLimit);
    }
    synchronized public float getShapeDLimit() {
        return shapeDLimit;
    }
    
    synchronized public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;
        //getPrefs().putInt("PawTracker3.decayTimeLimit",decayTimeLimit);
    }
    synchronized public int getDecayTimeLimit() {
        return decayTimeLimit;
    }
    
    
    
    
    synchronized public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        //getPrefs().putInt("PawTracker3.intensityZoom",intensityZoom);
    }
    
    synchronized public int getIntensityZoom() {
        return intensityZoom;
    }
    
    
    
    synchronized public void setIn_length(int in_length) {
        this.in_length = in_length;
        //getPrefs().putInt("PawTracker3.in_length",in_length);
    }
    
    synchronized public int getIn_length() {
        return in_length;
    }
    
    synchronized public void setIn_test_length(int in_test_length) {
        this.in_test_length = in_test_length;
        //getPrefs().putInt("PawTracker3.in_test_length",in_test_length);
    }
    
    synchronized public int getIn_test_length() {
        return in_test_length;
    }
    
     synchronized public void setDoor_z(int door_z) {
        this.door_z = door_z;
       
    }
    
    synchronized public int getDoor_z() {
        return door_z;
    }
    
    synchronized public void setDoor_xa(int door_xa) {
        this.door_xa = door_xa;
        //getPrefs().putInt("PawTracker3.door_xa",door_xa);
    }
    
    synchronized public int getDoor_xa() {
        return door_xa;
    }
    
    synchronized public void setDoor_xb(int door_xb) {
        this.door_xb = door_xb;
        //getPrefs().putInt("PawTracker3.door_xb",door_xb);
    }
    
    synchronized public int getDoor_xb() {
        return door_xb;
    }
    
    synchronized public void setDoor_ya(int door_ya) {
        this.door_ya = door_ya;
        //getPrefs().putInt("PawTracker3.door_ya",door_ya);
    }
    
    synchronized public int getDoor_ya() {
        return door_ya;
    }
    
    synchronized public void setDoor_yb(int door_yb) {
        this.door_yb = door_yb;
        //getPrefs().putInt("PawTracker3.door_yb",door_yb);
    }
    
    synchronized public int getDoor_yb() {
        return door_yb;
    }
    
    
    synchronized public void setNode_range(float node_range) {
        this.node_range = node_range;
        //getPrefs().putFloat("PawTracker3.node_range",node_range);
    }
    synchronized public float getNode_range() {
        return node_range;
    }
    
    
    
    
    synchronized public void setFinger_sensitivity(float finger_sensitivity) {
        this.finger_sensitivity = finger_sensitivity;
        //getPrefs().putFloat("PawTracker3.finger_sensitivity",finger_sensitivity);
    }
    synchronized public float getFinger_sensitivity() {
        return finger_sensitivity;
    }
    
    synchronized public void setFinger_mv_smooth(float finger_mv_smooth) {
        this.finger_mv_smooth = finger_mv_smooth;
        //getPrefs().putFloat("PawTracker3.finger_mv_smooth",finger_mv_smooth);
    }
    synchronized public float getFinger_mv_smooth() {
        return finger_mv_smooth;
    }
    
    synchronized public void setFinger_length(int finger_length) {
        this.finger_length = finger_length;
        //getPrefs().putInt("PawTracker3.finger_length",finger_length);
    }
    synchronized public int getFinger_length() {
        return finger_length;
    }
    
    synchronized public void setFinger_start_threshold(int finger_start_threshold) {
        this.finger_start_threshold = finger_start_threshold;
        //getPrefs().putInt("PawTracker3.finger_start_threshold",finger_start_threshold);
    }
    synchronized public int getFinger_start_threshold() {
        return finger_start_threshold;
    }
    
    
    
    synchronized public void setFinger_cluster_range(float finger_cluster_range) {
        this.finger_cluster_range = finger_cluster_range;
        //getPrefs().putFloat("PawTracker3.finger_cluster_range",finger_cluster_range);
    }
    synchronized public float getFinger_cluster_range() {
        return finger_cluster_range;
    }
    
    synchronized public void setFinger_ori_variance(float finger_ori_variance) {
        this.finger_ori_variance = finger_ori_variance;
        //getPrefs().putFloat("PawTracker3.finger_ori_variance",finger_ori_variance);
    }
    synchronized public float getFinger_ori_variance() {
        return finger_ori_variance;
    }
    
    
    synchronized public void setPalmback_below(float palmback_below) {
        this.palmback_below = palmback_below;
        //getPrefs().putFloat("PawTracker3.palmback_below",palmback_below);
    }
    synchronized public float getPalmback_below() {
        return palmback_below;
    }
    synchronized public void setPalmback_value(float palmback_value) {
        this.palmback_value = palmback_value;
        //getPrefs().putFloat("PawTracker3.palmback_value",palmback_value);
    }
    synchronized public float getPalmback_value() {
        return palmback_value;
    }
    synchronized public void setPalmback_distance(int palmback_distance) {
        this.palmback_distance = palmback_distance;
        //getPrefs().putInt("PawTracker3.palmback_distance",palmback_distance);
    }
    synchronized public int getPalmback_distance() {
        return palmback_distance;
    }
    
    synchronized public void setIntensity_range(int intensity_range) {
        this.intensity_range = intensity_range;
        //getPrefs().putInt("PawTracker3.intensity_range",intensity_range);
    }
    synchronized public int getIntensity_range() {
        return intensity_range;
    }
    
    
    
    synchronized public void setIn_value_threshold(float in_value_threshold) {
        this.in_value_threshold = in_value_threshold;
        //getPrefs().putFloat("PawTracker3.in_value_threshold",in_value_threshold);
    }
    synchronized public float getIn_value_threshold() {
        return in_value_threshold;
    }
    
    synchronized public void setIntensity_threshold(float intensity_threshold) {
        this.intensity_threshold = intensity_threshold;
        //getPrefs().putFloat("PawTracker3.intensity_threshold",intensity_threshold);
    }
    synchronized public float getIntensity_threshold() {
        return intensity_threshold;
    }
    
    synchronized public void setIntensity_strength(float intensity_strength) {
        this.intensity_strength = intensity_strength;
        //getPrefs().putFloat("PawTracker3.intensity_strength",intensity_strength);
    }
    synchronized public float getIntensity_strength() {
        return intensity_strength;
    }
    
    
    synchronized public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
        //getPrefs().putFloat("PawTracker3.event_strength",event_strength);
    }
    synchronized public float getEvent_strength() {
        return event_strength;
    }
    
    synchronized public void setContour_min_thresh(float contour_min_thresh) {
        this.contour_min_thresh = contour_min_thresh;
        //getPrefs().putFloat("PawTracker3.contour_min_thresh",contour_min_thresh);
    }
    synchronized public float getContour_min_thresh() {
        return contour_min_thresh;
    }
    
    synchronized public void setContour_act_thresh(float contour_act_thresh) {
        this.contour_act_thresh = contour_act_thresh;
        //getPrefs().putFloat("PawTracker3.contour_act_thresh",contour_act_thresh);
    }
    synchronized public float getContour_act_thresh() {
        return contour_act_thresh;
    }
    
    synchronized public void setContour_range(int contour_range) {
        this.contour_range = contour_range;
        //getPrefs().putInt("PawTracker3.contour_range",contour_range);
    }
    synchronized public int getContour_range() {
        return contour_range;
    }
    
    
    
    synchronized public void setTracker_time_bin(float tracker_time_bin) {
        this.tracker_time_bin = tracker_time_bin;
        //getPrefs().putFloat("PawTracker3.tracker_time_bin",tracker_time_bin);
    }
    synchronized public float getTracker_time_bin() {
        return tracker_time_bin;
    }
    
    synchronized public void setShapeThreshold(float shapeThreshold) {
        this.shapeThreshold = shapeThreshold;
        //getPrefs().putFloat("PawTracker3.shapeThreshold",shapeThreshold);
    }
    synchronized public float getShapeThreshold() {
        return shapeThreshold;
    }
    
    synchronized public void setShapeDThreshold(float shapeDThreshold) {
        this.shapeDThreshold = shapeDThreshold;
        //getPrefs().putFloat("PawTracker3.shapeDThreshold",shapeDThreshold);
    }
    synchronized public float getShapeDThreshold() {
        return shapeDThreshold;
    }
    
    synchronized public void setDoorMinDiff(float doorMinDiff) {
        this.doorMinDiff = doorMinDiff;
        //getPrefs().putFloat("PawTracker3.doorMinDiff",doorMinDiff);
    }
    synchronized public float getDoorMinDiff() {
        return doorMinDiff;
    }
    
    synchronized public void setDoorMaxDiff(float doorMaxDiff) {
        this.doorMaxDiff = doorMaxDiff;
        //getPrefs().putFloat("PawTracker3.doorMaxDiff",doorMaxDiff);
    }
    synchronized public float getDoorMaxDiff() {
        return doorMaxDiff;
    }
    
    synchronized public void setMinZeroes(int minZeroes) {
        this.minZeroes = minZeroes;
        //getPrefs().putInt("PawTracker3.minZeroes",minZeroes);
    }
    synchronized public int getMinZeroes() {
        return minZeroes;
    }
    
    synchronized public void setMaxZeroes(int maxZeroes) {
        this.maxZeroes = maxZeroes;
        //getPrefs().putInt("PawTracker3.maxZeroes",maxZeroes);
    }
    
    synchronized public int getMaxZeroes() {
        return maxZeroes;
    }
    
    
    
    
    
//    synchronized public void setDoorMinZeroes(int doorMinZeroes) {
//        this.doorMinZeroes = doorMinZeroes;
//        //getPrefs().putInt("PawTracker3.doorMinZeroes",doorMinZeroes);
//    }
//    synchronized public int getDoorMinZeroes() {
//        return doorMinZeroes;
//    }
//
//    synchronized public void setDoorMaxZeroes(int doorMaxZeroes) {
//        this.doorMaxZeroes = doorMaxZeroes;
//        //getPrefs().putInt("PawTracker3.doorMaxZeroes",doorMaxZeroes);
//    }
//    synchronized public int getDoorMaxZeroes() {
//        return doorMaxZeroes;
//    }
    
    
    
    
    
    
    
    
    
    
    
//    synchronized public int getRetinaSize() {
//        return retinaSize;
//    }
//
//    synchronized public void setRetinaSize(int retinaSize) {
//        this.retinaSize = retinaSize;
//        //getPrefs().putInt("PawTracker3.retinaSize",retinaSize);
//
//    }
    
    synchronized public int getLinkSize() {
        return linkSize;
    }
    
    synchronized public void setLinkSize(int linkSize) {
        this.linkSize = linkSize;
        //getPrefs().putInt("PawTracker3.linkSize",linkSize);
    }
    
    
    synchronized public int getBoneSize() {
        return boneSize;
    }
    
    synchronized public void setBoneSize(int boneSize) {
        this.boneSize = boneSize;
        //getPrefs().putInt("PawTracker3.boneSize",boneSize);
    }
    
    synchronized public int getSegSize() {
        return segSize;
    }
    
    synchronized public void setSegSize(int segSize) {
        this.segSize = segSize;
        //getPrefs().putInt("PawTracker3.segSize",segSize);
    }
    
    synchronized public int getMaxSegSize() {
        return maxSegSize;
    }
    
    synchronized public void setMaxSegSize(int maxSegSize) {
        this.maxSegSize = maxSegSize;
        //getPrefs().putInt("PawTracker3.maxSegSize",maxSegSize);
    }
    
    
    
    
    
    synchronized public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    synchronized public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        //getPrefs().putBoolean("PawTracker3.resetPawTracking",resetPawTracking);
        
    }
    
    synchronized public boolean isValidateParameters() {
        return validateParameters;
    }
    synchronized public void setValidateParameters(boolean validateParameters) {
        this.validateParameters = validateParameters;
        //getPrefs().putBoolean("PawTracker3.validateParameters",validateParameters);
        
    }
    
    
    
    
    synchronized public void setCorrectY(boolean correctY){
        this.correctY = correctY;
        
    }
    synchronized public void setUseFingerDistanceSmooth(boolean useFingerDistanceSmooth){
        this.useFingerDistanceSmooth = useFingerDistanceSmooth;
        //getPrefs().putBoolean("PawTracker3.useFingerDistanceSmooth",useFingerDistanceSmooth);
    }
    synchronized public boolean isUseFingerDistanceSmooth(){
        return useFingerDistanceSmooth;
    }
    
    synchronized public void setUseSimpleContour(boolean useSimpleContour){
        this.useSimpleContour = useSimpleContour;
        //getPrefs().putBoolean("PawTracker3.useSimpleContour",useSimpleContour);
    }
    synchronized public boolean isUseSimpleContour(){
        return useSimpleContour;
    }
    
    
    
    synchronized public void setShowCorrectionMatrix(boolean showCorrectionMatrix){
        this.showCorrectionMatrix = showCorrectionMatrix;
        
    }
    
    synchronized public void setShowSkeletton(boolean showSkeletton){
        this.showSkeletton = showSkeletton;
        //getPrefs().putBoolean("PawTracker3.showSkeletton",showSkeletton);
    }
    synchronized public boolean isShowSkeletton(){
        return showSkeletton;
    }
    
    synchronized public void setShowSecondFilter(boolean showSecondFilter){
        this.showSecondFilter = showSecondFilter;
        //getPrefs().putBoolean("PawTracker3.showSecondFilter",showSecondFilter);
    }
    synchronized public boolean isShowSecondFilter(){
        return showSecondFilter;
    }
    
    synchronized public void setShowTopography(boolean showTopography){
        this.showTopography = showTopography;
        //getPrefs().putBoolean("PawTracker3.showTopography",showTopography);
    }
    synchronized public boolean isShowTopography(){
        return showTopography;
    }
    
    
    
    
    synchronized public void setShowPalm(boolean showPalm){
        this.showPalm = showPalm;
        //getPrefs().putBoolean("PawTracker3.showPalm",showPalm);
    }
    synchronized public boolean isShowPalm(){
        return showPalm;
    }
    
    synchronized public void setShowThin(boolean showThin){
        this.showThin = showThin;
        //getPrefs().putBoolean("PawTracker3.showThin",showThin);
    }
    synchronized public boolean isShowThin(){
        return showThin;
    }
    synchronized public void setThinning(boolean thinning){
        this.thinning = thinning;
        //getPrefs().putBoolean("PawTracker3.thinning",thinning);
    }
    synchronized public boolean isThinning(){
        return thinning;
    }
    
    synchronized public float getThin_Threshold() {
        return thin_threshold;
    }
    
    synchronized public void setThin_Threshold(float thin_threshold) {
        this.thin_threshold = thin_threshold;
        //getPrefs().putFloat("PawTracker3.thin_threshold",thin_threshold);
    }
    
    
    
    synchronized public void setShowSegments(boolean showSegments){
        this.showSegments = showSegments;
        //getPrefs().putBoolean("PawTracker3.showSegments",showSegments);
    }
    synchronized public boolean isShowSegments(){
        return showSegments;
    }
    
    
    synchronized public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        //getPrefs().putBoolean("PawTracker3.scaleAcc",scaleAcc);
    }
    synchronized public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    synchronized public void setScaleIntensity(boolean scaleIntensity){
        this.scaleIntensity = scaleIntensity;
        //getPrefs().putBoolean("PawTracker3.scaleIntensity",scaleIntensity);
    }
    synchronized public boolean isScaleIntensity(){
        return scaleIntensity;
    }
    
    synchronized public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        //getPrefs().putBoolean("PawTracker3.showAcc",showAcc);
    }
    synchronized public boolean isShowAcc(){
        return showAcc;
    }
    
    synchronized public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
        //getPrefs().putBoolean("PawTracker3.showOnlyAcc",showOnlyAcc);
    }
    synchronized public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
    synchronized public void setShowDecay(boolean showDecay){
        this.showDecay = showDecay;
        //getPrefs().putBoolean("PawTracker3.showDecay",showDecay);
    }
    synchronized public boolean isShowDecay(){
        return showDecay;
    }
    
    
    synchronized public int getDensityMinIndex() {
        return densityMinIndex;
    }
    
    synchronized public void setDensityMinIndex(int densityMinIndex) {
        this.densityMinIndex = densityMinIndex;
        //getPrefs().putInt("PawTracker3.densityMinIndex",densityMinIndex);
    }
    
    synchronized public int getDensityMaxIndex() {
        return densityMaxIndex;
    }
    
    synchronized public void setDensityMaxIndex(int densityMaxIndex) {
        this.densityMaxIndex = densityMaxIndex;
        //getPrefs().putInt("PawTracker3.densityMaxIndex",densityMaxIndex);
    }
    
    synchronized public void setShowDensity(boolean showDensity){
        this.showDensity = showDensity;
        //getPrefs().putBoolean("PawTracker3.showDensity",showDensity);
    }
    synchronized public boolean isShowDensity(){
        return showDensity;
    }
    
    synchronized public void setScaleInDoor(boolean scaleInDoor){
        this.scaleInDoor = scaleInDoor;
        //getPrefs().putBoolean("PawTracker3.scaleInDoor",scaleInDoor);
    }
    synchronized public boolean isScaleInDoor(){
        return scaleInDoor;
    }
    
    
    
    
    
    synchronized public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
        //getPrefs().putBoolean("PawTracker3.decayOn",decayOn);
    }
    synchronized public boolean isDecayOn(){
        return decayOn;
    }
    
    synchronized public void setShowFrame(boolean showFrame){
        this.showFrame = showFrame;
        //getPrefs().putBoolean("PawTracker3.showFrame",showFrame);
    }
    synchronized public boolean isShowFrame(){
        return showFrame;
    }
    
     synchronized public void setShowCage(boolean showCage){
        this.showCage = showCage;
       
    }
    synchronized public boolean isShowCage(){
        return showCage;
    }
    
    synchronized public void setShowCorner(boolean showCorner){
        this.showCorner = showCorner;
        
    }
    synchronized public boolean isShowCorner(){
        return showCorner;
    }
    
    
    synchronized public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        //getPrefs().putBoolean("PawTracker3.showWindow",showWindow);
    }
    synchronized public boolean isShowWindow(){
        return showWindow;
    }
    
    
    synchronized public void setShowScore(boolean showScore){
        this.showScore = showScore;
        //getPrefs().putBoolean("PawTracker3.showScore",showScore);
    }
    synchronized public boolean isShowScore(){
        return showScore;
    }
    
    
    synchronized public void setUseIntensity(boolean useIntensity){
        this.useIntensity = useIntensity;
        //getPrefs().putBoolean("PawTracker3.useIntensity",useIntensity);
    }
    synchronized public boolean isUseIntensity(){
        return useIntensity;
    }
    
    synchronized public void setHideInside(boolean hideInside){
        this.hideInside = hideInside;
        //getPrefs().putBoolean("PawTracker3.hideInside",hideInside);
    }
    synchronized public boolean isHideInside(){
        return hideInside;
    }
    
    synchronized public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        //getPrefs().putBoolean("PawTracker3.showFingers",showFingers);
    }
    synchronized public boolean isShowFingers(){
        return showFingers;
    }
    
    synchronized public void setShowClusters(boolean showClusters){
        this.showClusters = showClusters;
        //getPrefs().putBoolean("PawTracker3.showClusters",showClusters);
    }
    synchronized public boolean isShowClusters(){
        return showClusters;
    }
    
    synchronized public void setShowFingerTips(boolean showFingerTips){
        this.showFingerTips = showFingerTips;
        //getPrefs().putBoolean("PawTracker3.showFingerTips",showFingerTips);
    }
    synchronized public boolean isShowFingerTips(){
        return showFingerTips;
    }
    
    synchronized public void setShowZones(boolean showZones){
        this.showZones = showZones;
        //getPrefs().putBoolean("PawTracker3.showZones",showZones);
    }
    synchronized public boolean isShowZones(){
        return showZones;
    }
    synchronized public void setShowAll(boolean showAll){
        this.showAll = showAll;
        //getPrefs().putBoolean("PawTracker3.showAll",showAll);
    }
    synchronized public boolean isShowAll(){
        return showAll;
    }
    
    
    
//    synchronized public void setShowSequences(boolean showSequences){
//        this.showSequences = showSequences;
//        //getPrefs().putBoolean("PawTracker3.showSequences",showSequences);
//    }
//    synchronized public boolean isShowSequences(){
//        return showSequences;
//    }
    
    synchronized public void setShowShape(boolean showShape){
        this.showShape = showShape;
        //getPrefs().putBoolean("PawTracker3.showShape",showShape);
    }
    synchronized public boolean isShowShape(){
        return showShape;
    }
    
    
    synchronized public void setShowShapePoints(boolean showShapePoints){
        this.showShapePoints = showShapePoints;
        //getPrefs().putBoolean("PawTracker3.showShapePoints",showShapePoints);
    }
    synchronized public boolean isShowShapePoints(){
        return showShapePoints;
    }
    synchronized public void setSmoothShape(boolean smoothShape){
        this.smoothShape = smoothShape;
        //getPrefs().putBoolean("PawTracker3.smoothShape",smoothShape);
    }
    synchronized public boolean isSmoothShape(){
        return smoothShape;
    }
    
    
    
    synchronized public void setShowRLColors(boolean showRLColors){
        this.showRLColors = showRLColors;
        
    }
    
    synchronized public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
    }
    synchronized public void setShowFingerPoints(boolean showFingerPoints){
        this.showFingerPoints = showFingerPoints;
        //getPrefs().putBoolean("PawTracker3.showFingerPoints",showFingerPoints);
    }
    synchronized public boolean isShowFingerPoints(){
        return showFingerPoints;
    }
    
    synchronized public void setUseDualFilter(boolean useDualFilter){
        this.useDualFilter = useDualFilter;
        //getPrefs().putBoolean("PawTracker3.useDualFilter",useDualFilter);
    }
    synchronized public boolean isUseDualFilter(){
        return useDualFilter;
    }
    
    synchronized public void setUseLowFilter(boolean useLowFilter){
        this.useLowFilter = useLowFilter;
        //getPrefs().putBoolean("PawTracker3.useLowFilter",useLowFilter);
    }
    synchronized public boolean isUseLowFilter(){
        return useLowFilter;
    }
    
    synchronized public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    synchronized public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        //getPrefs().putInt("PawTracker3.lowFilter_radius",lowFilter_radius);
    }
    
    synchronized public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    synchronized public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        //getPrefs().putInt("PawTracker3.lowFilter_density",lowFilter_density);
    }
    
    synchronized public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    synchronized public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
        //getPrefs().putFloat("PawTracker3.lowFilter_threshold",lowFilter_threshold);
    }
    
    synchronized public int getLowFilter_radius2() {
        return lowFilter_radius2;
    }
    
    synchronized public void setLowFilter_radius2(int lowFilter_radius2) {
        this.lowFilter_radius2 = lowFilter_radius2;
        //getPrefs().putInt("PawTracker3.lowFilter_radius2",lowFilter_radius2);
    }
    
    synchronized public int getLowFilter_density2() {
        return lowFilter_density2;
    }
    
    synchronized public void setLowFilter_density2(int lowFilter_density2) {
        this.lowFilter_density2 = lowFilter_density2;
        //getPrefs().putInt("PawTracker3.lowFilter_density2",lowFilter_density2);
    }
    
    synchronized public float getFinger_border_mix() {
        return finger_border_mix;
    }
    
    synchronized public void setFinger_border_mix(float finger_border_mix) {
        this.finger_border_mix = finger_border_mix;
        //getPrefs().putFloat("PawTracker3.finger_border_mix",finger_border_mix);
    }
    
    synchronized public float getFinger_tip_mix() {
        return finger_tip_mix;
    }
    
    synchronized public void setFinger_tip_mix(float finger_tip_mix) {
        this.finger_tip_mix = finger_tip_mix;
        //getPrefs().putFloat("PawTracker3.finger_tip_mix",finger_tip_mix);
    }
    
    synchronized public int getFinger_surround() {
        return finger_surround;
    }
    
    synchronized public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        //getPrefs().putInt("PawTracker3.finger_surround",finger_surround);
    }
    
    
    synchronized public int getFinger_creation_surround() {
        return finger_creation_surround;
    }
    
    synchronized public void setFinger_creation_surround(int finger_creation_surround) {
        this.finger_creation_surround = finger_creation_surround;
        //getPrefs().putInt("PawTracker3.finger_creation_surround",finger_creation_surround);
    }
    
    
    
    synchronized public float getBrightness() {
        return brightness;
    }
    
    synchronized public void setBrightness(float brightness) {
        this.brightness = brightness;
        //getPrefs().putFloat("PawTracker3.brightness",brightness);
    }
    
    
    synchronized public float getSk_threshold() {
        return sk_threshold;
    }
    
    synchronized public void setSk_threshold(float sk_threshold) {
        this.sk_threshold = sk_threshold;
        //getPrefs().putFloat("PawTracker3.sk_threshold",sk_threshold);
    }
    
    synchronized public float getSk_radius_min() {
        return sk_radius_min;
    }
    
    synchronized public void setSk_radius_min(float sk_radius_min) {
        this.sk_radius_min = sk_radius_min;
        //getPrefs().putFloat("PawTracker3.sk_radius_min",sk_radius_min);
    }
    
    synchronized public float getSk_radius_max() {
        return sk_radius_max;
    }
    
    synchronized public void setSk_radius_max(float sk_radius_max) {
        this.sk_radius_max = sk_radius_max;
        //getPrefs().putFloat("PawTracker3.sk_radius_max",sk_radius_max);
    }
    
    synchronized public void setPlaneAngle(float planeAngle) {
        this.planeAngle = planeAngle;
        
    }
    
    synchronized public void setAlpha(float alpha) {
        this.alpha = alpha;
        
    }
    
    synchronized public void setIntensity(float intensity) {
        this.intensity = intensity;
        
    }
    synchronized public void setRay_length(int ray_length) {
        this.ray_length = ray_length;
        
    }
    
    synchronized public void setAdjustTime(int adjustTime) {
        this.adjustTime = adjustTime;
        
    }
    synchronized public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        
    }
    synchronized public void setStereoThreshold(float stereoThreshold) {
        this.stereoThreshold = stereoThreshold;
        
    }
    
    synchronized public void setObj_xa(int obj_xa) {
        this.obj_xa = obj_xa;
        
    }
    synchronized public void setObj_ya(int obj_ya) {
        this.obj_ya = obj_ya;
        
    }
    synchronized public void setObj_xb(int obj_xb) {
        this.obj_xb = obj_xb;
        
    }
    synchronized public void setObj_yb(int obj_yb) {
        this.obj_yb = obj_yb;
        
    }
    synchronized public void setObj_sizea(int obj_sizea) {
        this.obj_sizea = obj_sizea;
        
    }
    synchronized public void setObj_sizeb(int obj_sizeb) {
        this.obj_sizeb = obj_sizeb;
        
    }
    
    synchronized public void setYLeftCorrection(int yLeftCorrection) {
        this.yLeftCorrection = yLeftCorrection;
        
    }
    
    synchronized public void setYRightCorrection(int yRightCorrection) {
        this.yRightCorrection = yRightCorrection;
        
    }
    
    synchronized public void setYCurveFactor(float yCurveFactor) {
        this.yCurveFactor = yCurveFactor;
        // refresh correctioMatrix
        createCorrectionMatrix();
        //maybe pause and resume also
        
    }
    
    
    synchronized public void setShowYColor(boolean showYColor){
        this.showYColor = showYColor;
        
    }
    
    synchronized public void setShowZColor(boolean showZColor){
        this.showZColor = showZColor;
        
    }
    
    synchronized public void setShowXColor(boolean showXColor){
        this.showXColor = showXColor;
        
    }
    
    
    synchronized public void setShowShadows(boolean showShadows){
        this.showShadows = showShadows;
        
    }
    synchronized public void setColorizePeriod(int colorizePeriod) {
        this.colorizePeriod = colorizePeriod;
        
    }
    synchronized public void setColorizeFactor(float colorizeFactor) {
        this.colorizeFactor = colorizeFactor;
        
    }
    synchronized public void setShadowFactor(float shadowFactor) {
        this.shadowFactor = shadowFactor;
        
    }
    synchronized public void setZFactor(int zFactor) {
        this.zFactor = zFactor;
        
    }
    
    
    synchronized public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
        
    }
    
    synchronized public void setValueMargin(float valueMargin) {
        this.valueMargin = valueMargin;
        
    }
    
    
}
