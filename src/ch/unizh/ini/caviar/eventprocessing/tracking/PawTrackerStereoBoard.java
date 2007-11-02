/*
 * PawTrackerStereoBoard.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * Data must be recorded via stereoboard
 *
 * Paul Rogister, Created on October, 2007
 *
 */


package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
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

/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class PawTrackerStereoBoard extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
   
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
    protected final int RIGHT_MOST_METHOD = 1;
    protected final int LEFT_MOST_METHOD = 0;
    
    protected final int NO_LINK = -1;
    protected final int DELETE_LINK = -2;
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
    
    private final int MAX_NB_FINGERS=20;
    
    private double maxOrientation = 180;
    private double maxDistGC = 200;
    
    private float middleAngle;
    
    
    
    // Parameters appearing in the GUI
        
    private float planeAngle=getPrefs().getFloat("PawTrackerStereoBoard.planeAngle",-30.0f);
    private float viewAngle=getPrefs().getFloat("PawTrackerStereoBoard.viewAngle",-40.0f);
    private float platformAngle=getPrefs().getFloat("PawTrackerStereoBoard.platformAngle",-20.0f);
       
    
    
    private int cage_depth=getPrefs().getInt("PawTrackerStereoBoard.cage_depth",-120);
                  
    private float alpha=getPrefs().getFloat("PawTrackerStereoBoard.alpha",0.1f);
    private float intensity=getPrefs().getFloat("PawTrackerStereoBoard.intensity",1);
    
 
    private int dispAvgRange=getPrefs().getInt("PawTrackerStereoBoard.dispAvgRange",1);
           
    private int yLeftCorrection=getPrefs().getInt("PawTrackerStereoBoard.yLeftCorrection",0);
    private int yRightCorrection=getPrefs().getInt("PawTrackerStereoBoard.yRightCorrection",0);
    
    private float yCurveFactor=getPrefs().getFloat("PawTrackerStereoBoard.yCurveFactor",0.1f);
    
    private float valueThreshold=getPrefs().getFloat("PawTrackerStereoBoard.valueThreshold",0);
    
    
    private float shadowFactor=getPrefs().getFloat("PawTrackerStereoBoard.shadowFactor",0.3f);
    private float colorizeFactor=getPrefs().getFloat("PawTrackerStereoBoard.colorizeFactor",0.1f);
    private int colorizePeriod=getPrefs().getInt("PawTrackerStereoBoard.colorizePeriod",183);
    
    
    private int zFactor=getPrefs().getInt("PawTrackerStereoBoard.zFactor",1);
    private float valueMargin=getPrefs().getFloat("PawTrackerStereoBoard.valueMargin",0.3f);
    
    private int disparity_range=getPrefs().getInt("PawTrackerStereoBoard.disparity_range",50);
   
    private int cube_size=getPrefs().getInt("PawTrackerStereoBoard.cube_size",1);
    
     private int door_z=getPrefs().getInt("PawTrackerStereoBoard.door_z",50);
    {setPropertyTooltip("door_z","estimated z of the cage door");}
    
    private int door_xa=getPrefs().getInt("PawTrackerStereoBoard.door_xa",52);
    {setPropertyTooltip("door_xa","lower x bound of the cage door");}
    private int door_xb=getPrefs().getInt("PawTrackerStereoBoard.door_xb",88);
    {setPropertyTooltip("door_xb","higher x bound of the cage door");}
    private int door_ya=getPrefs().getInt("PawTrackerStereoBoard.door_ya",50);
    {setPropertyTooltip("door_ya","lower y bound of the cage door");}
    private int door_yb=getPrefs().getInt("PawTrackerStereoBoard.door_yb",127);
    {setPropertyTooltip("door_yb","higher y bound of the cage door");}
    
    private int retinaSize=128;//getPrefs().getInt("PawTrackerStereoBoard.retinaSize",128);

    private int intensityZoom = getPrefs().getInt("PawTrackerStereoBoard.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    
    private float brightness=getPrefs().getFloat("PawTrackerStereoBoard.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
    
  
    private float correctLeftAngle=getPrefs().getFloat("PawTrackerStereoBoard.correctLeftAngle",0.0f);
    private float correctRightAngle=getPrefs().getFloat("PawTrackerStereoBoard.correctRightAngle",0.0f);
   
   
    private boolean track = getPrefs().getBoolean("PawTrackerStereoBoard.track",true);
    private boolean showYColor = getPrefs().getBoolean("PawTrackerStereoBoard.showYColor",false);
    private boolean showXColor = getPrefs().getBoolean("PawTrackerStereoBoard.showXColor",false);
    private boolean showZColor = getPrefs().getBoolean("PawTrackerStereoBoard.showZColor",false);
    private boolean showShadows = getPrefs().getBoolean("PawTrackerStereoBoard.showShadows",false);
    private boolean showCorner = getPrefs().getBoolean("PawTrackerStereoBoard.showCorner",false);
    
    private boolean highlightDecay = getPrefs().getBoolean("PawTrackerStereoBoard.highlightDecay",false);
   
    
    
    private boolean correctY = getPrefs().getBoolean("PawTrackerStereoBoard.correctY",false);
    private boolean useDualFilter = getPrefs().getBoolean("PawTrackerStereoBoard.useDualFilter",false);
 
    private boolean useLarge = getPrefs().getBoolean("PawTrackerStereoBoard.useLarge",false);
 
    
    private boolean showFingers = getPrefs().getBoolean("PawTrackerStereoBoard.showFingers",true);
    private boolean showFingerTips = getPrefs().getBoolean("PawTrackerStereoBoard.showFingerTips",true);
    
    private boolean showZones = getPrefs().getBoolean("PawTrackerStereoBoard.showZones",true);
    private boolean showAll = getPrefs().getBoolean("PawTrackerStereoBoard.showAll",true);
    // show intensity inside shape
    
    private boolean showAcc = getPrefs().getBoolean("PawTrackerStereoBoard.showAcc",false);
    private boolean showOnlyAcc = getPrefs().getBoolean("PawTrackerStereoBoard.showOnlyAcc",false);
    private boolean showDecay = getPrefs().getBoolean("PawTrackerStereoBoard.showDecay",false);
    
    
    private boolean scaleAcc = getPrefs().getBoolean("PawTrackerStereoBoard.scaleAcc",true);
    
    private boolean showCage = getPrefs().getBoolean("PawTrackerStereoBoard.showCage",true);
    private boolean showFrame = getPrefs().getBoolean("PawTrackerStereoBoard.showFrame",true);
    private boolean showWindow = getPrefs().getBoolean("PawTrackerStereoBoard.showWindow",true);
    private boolean showScore = getPrefs().getBoolean("PawTrackerStereoBoard.showScore",false);
    
    
    
  //  private boolean showShapePoints = getPrefs().getBoolean("PawTrackerStereoBoard.showShapePoints",true);
 //   private boolean showFingerPoints = getPrefs().getBoolean("PawTrackerStereoBoard.showFingerPoints",true);
    
    
    
 //   private boolean showShape = getPrefs().getBoolean("PawTrackerStereoBoard.showShape",true);
    private boolean showRLColors = getPrefs().getBoolean("PawTrackerStereoBoard.showRLColors",false);
    private boolean showAxes = getPrefs().getBoolean("PawTrackerStereoBoard.showAxes",true);
    
   
    private int lowFilter_radius=getPrefs().getInt("PawTrackerStereoBoard.lowFilter_radius",3);
    private int lowFilter_density=getPrefs().getInt("PawTrackerStereoBoard.lowFilter_density",17);
    private float lowFilter_threshold=getPrefs().getFloat("PawTrackerStereoBoard.lowFilter_threshold",0);
    
    private int lowFilter_radius2=getPrefs().getInt("PawTrackerStereoBoard.lowFilter_radius2",10);
    private int lowFilter_density2=getPrefs().getInt("PawTrackerStereoBoard.lowFilter_density2",5);
   
    private boolean showCorrectionMatrix = getPrefs().getBoolean("PawTrackerStereoBoard.showCorrectionMatrix",false);
    private boolean showCorrectionGradient = getPrefs().getBoolean("PawTrackerStereoBoard.showCorrectionGradient",false);
   
    private boolean showRight = getPrefs().getBoolean("PawTrackerStereoBoard.showRight",false);
   
//    private boolean showPalm = getPrefs().getBoolean("PawTrackerStereoBoard.showPalm",false);
    
//    private boolean showSkeletton = getPrefs().getBoolean("PawTrackerStereoBoard.showSkeletton",false);
    private boolean showSecondFilter = getPrefs().getBoolean("PawTrackerStereoBoard.showSecondFilter",false);
//    private boolean showTopography = getPrefs().getBoolean("PawTrackerStereoBoard.showTopography",false);
    
    
    private boolean restart=getPrefs().getBoolean("PawTrackerStereoBoard.restart",false);
        
    private boolean resetPawTracking=getPrefs().getBoolean("PawTrackerStereoBoard.resetPawTracking",false);
    private boolean validateParameters=getPrefs().getBoolean("PawTrackerStereoBoard.validateParameters",false);
    
    private float event_strength=getPrefs().getFloat("PawTrackerStereoBoard.event_strength",2f);

    private int decayTimeLimit=getPrefs().getInt("PawTrackerStereoBoard.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
    private boolean decayOn = getPrefs().getBoolean("PawTrackerStereoBoard.decayOn",false);
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}
    
    
    private boolean notCrossing = getPrefs().getBoolean("PawTrackerStereoBoard.notCrossing",false);
    
    
    private float finger_mix=getPrefs().getFloat("PawTrackerStereoBoard.finger_mix",0.5f);
    private int finger_surround=getPrefs().getInt("PawTrackerStereoBoard.finger_surround",10);
    
    
      /** additional classes */
    
    /** EventPoint : all data about a point in retina space */
    public class EventPoint{                
        // 3D variables, to rename
        // left
        // int count;
        int disparityLink = NO_LINK;
        int prevDisparityLink = NO_LINK;
        int disparityLink2 = NO_LINK;
        int prevDisparityLink2 = NO_LINK;
        int disparityAvg = 0;
        // right
        // int accumulation;
        float free;
        int attachedTo = NO_LINK;
        int attachedTo2 = NO_LINK;
         
        int updateTime; // time of current update
        int previousUpdate; // time of previous update
        
        float previousLargeFilteredValue = 0;
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
        
        float gcx;        // float coordinates for gc of events around when point is border
        float gcy;
        float gcz;
        
        float prev_gcx;        // previous float coordinates for gc of zeroes around when point is border
        float prev_gcy;
        float prev_gcz;
        
        float ggcx;        // float coordinates for gc of gc around when point is border
        float ggcy;
        float ggcz;
        
        float prev_ggcx;        // previous float coordinates for gc of zeroes around when point is border
        float prev_ggcy;
        float prev_ggcz;
         
        boolean decayedBorder=false; // is a border computed on decayed filtered value
        
        int skValue;     // Skeletton value (?)
        //Finger finger; // finger
        int x;
        int y;
        int z;
        int x0;
        int y0;
        int z0;
        int x0r;
        int y0r;
        int z0r;
        
        int side = LEFT;
        int zDirection = 1;
        
        boolean changed = true;
        
        public EventPoint(  ){
            
        }
        
        public EventPoint( int x, int y, float value, int updateTime ){
            this.x = x;
            this.y = y;
            accValue = value;
            this.updateTime = updateTime;
            changed = true;
            
        }
        
        public EventPoint( BinocularEvent e ){
            changed = true;
            previousValue = lastValue;
           // int type=e.getType();
            int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
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
            changed = true;
            this.x = x;
            this.y = y;
        }
        
        public void updateFrom( BinocularEvent e, int x, int y, int side ){
            changed = true;
            previousValue = lastValue;
          //  int type=e.getType();
            int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            
            float step = event_strength / (colorScale + 1);
            lastValue = step * (type - grayValue);
            
        //    System.out.println("type "+type);
            
            
            //  accValue +=  lastValue;
            if (decayOn) accValue =  lastValue + getAccValue(e.timestamp);
            else accValue +=  lastValue;
            
            
            
            
            if(scaleAcc){
                // keep in range [0-1]
                
                if(accValue<0)accValue=0;
                else if(accValue>1)accValue=1;
            }
            
            
        //      System.out.println("type "+e.type+" accValue="+accValue+" step="+step+" colorScale="+colorScale
        //              +" grayValue="+grayValue+" event_strength="+event_strength);
            
            updateTime = e.timestamp;
            this.x = x;
            this.y = y;
            this.side = side;
            if(side==LEFT) zDirection = 1;
            else zDirection = -1;
          
        }
        
        
        public float getValue( int currentTime ){
            if(useDualFilter){
             
                if(useLarge){
                    // uncomment when trying to extract 3D from large filter
                    if (decayOn) return largeFilteredValue-decayedValue(largeFilteredValue,currentTime-updateTime);
                    else return largeFilteredValue;
                } else {
                    if (decayOn) return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
                    else return shortFilteredValue;
                }
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
            if (decayOn) return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
            else return shortFilteredValue;
        }
        
        public float getDecayedFilteredValue( int currentTime ){
            return decayedFilteredValue-decayedValue(decayedFilteredValue,currentTime-updateTime);
        }
        
        public float getPreviousShortFilteredValue( int currentTime ){
            return previousShortFilteredValue-decayedValue(previousShortFilteredValue,currentTime-previousUpdate);
        }
                
        public int getX0( int method ){
            if(changed)  computeXYZ0( method );
            if (method==LEFT)
                return x0;
            else return x0r;
           
        }
        
        public int getY0( int method ){
            if(changed)  computeXYZ0( method );
            if (method==LEFT)
                return y0;
            else return y0r;
        }
                             
        public int getZ0( int method ){
            if(changed)  computeXYZ0( method );
            if (method==LEFT)
            return z0;
            else return z0r;
        }
       
        public void computeXYZ0( int method ){
            changed = false;
                              
            int dx = disparityLink;
            if(method==RIGHT) dx = disparityLink2;
            
            if(dx>NO_LINK){
                              
                int z1 = (dx - x); // * -zFactor;                                
                int x1 = x;
                
                y0 = y;
                //debug
                //   leftPoints[x][y].z=z;
                if(compactMode){
                    x1 = Math.round(gcx);
                    y0 = Math.round(gcy);
                    z1 = Math.round(gcz);
                } else  if(veryCompactMode){
                    x1 = Math.round(ggcx);
                    y0 = Math.round(ggcy);
                    z1 = Math.round(ggcz);
                } 
                                
                if(notCrossing){ //to improve
                    if (z1>0) z1 = -z1;
                }
                                                
                x0 = Math.round((float) ( -
                        (Math.sin(Math.toRadians(planeAngle))*(z1)) ))+x1;
                z0 = Math.round((float) (
                        (Math.cos(Math.toRadians(planeAngle))*(z1*zDirection)) ));
                
                //  z0 =  z0*zDirection;
                
                // symetrical
                if(zDirection==-1){
                    int x2 = Math.round((float) ( -
                            (Math.sin(Math.toRadians(planeAngle))*(retinaSize)) ));
                    int z2 = Math.round((float) (
                            (Math.cos(Math.toRadians(planeAngle))*(retinaSize)) ));
                    
                    // obtain orthogonal direction to 0-x2
                    int half = retinaSize/2;
                    float correctMiddleAngle = (orientation(half,0,x2,z2)-90)*2;
                    
                    
                    
                    //   System.out.println("middleAngle 1 : "+middleAngle);
                    
                    // rotate points
                    int x3= Math.round((float) ( (Math.cos(Math.toRadians(correctMiddleAngle))*(x0-half)) -
                            (Math.sin(Math.toRadians(correctMiddleAngle))*(z0)) ))+half;
                    int z3 = Math.round((float) ( (Math.sin(Math.toRadians(correctMiddleAngle))*(x0-half)) +
                            (Math.cos(Math.toRadians(correctMiddleAngle))*(z0)) ));
                    x0 = x3;
                    z0 = z3;
                    
                }                                                                             
            }
        }
             
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
            changed = true;
            gcx = 0;
            gcy = 0;
            prev_gcx = 0;
            prev_gcy = 0;
        }
        
        void setGc( EventPoint[][] eventPoints){
            changed = true;
            // for all neighbours
            int i;
            int j;
            int nb = 0;
            for (i=x-1; i<x+2;i++){
                if(i>=0&&i<retinaSize){
                    for (j=y-1; j<y+2;j++){
                        if(j>=0&&j<retinaSize){
                            EventPoint surroundPoint = eventPoints[i][j];
                            
                            if(surroundPoint.shortFilteredValue>valueThreshold){
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
        
    } // end class EventPoint
    
    
    
  private class FingerCluster{
              
        int x=0;
        int y=0;
        int z = 0;
              
        boolean activated = false;
                      
        public FingerCluster(  ){
            
            
        }
             
        public FingerCluster( int x, int y, int z ){
            activated = true;
            
           
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public void reset(){
            
            activated = false;
            
           
            
            x = 0; //end tip
            y = 0;
            z = 0;
        }
        
        public void add( int x, int y, int z, float mix){
            
           
            this.x = Math.round(x*mix + this.x*(1-mix));
            this.y = Math.round(y*mix + this.y*(1-mix));
            this.z = Math.round(z*mix + this.z*(1-mix));
                                 
        }
  }
     // end class FingerCluster
    
    
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
   
    // Global variables
    
      // array of event points for all computation
    protected EventPoint leftPoints[][];
    protected EventPoint rightPoints[][];
    
    protected EventPoint leftPoints2[][];
    protected EventPoint rightPoints2[][];
    
    protected int correctionMatrix[][];
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    int currentTime = 0;
    
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
            
    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    private int redBlueShown=0;
    private int method=0;
    private int display3DChoice=0;
    private int testChoice=0;
          
    private boolean averageMode = false;
    private boolean fuseMode = false;
    private boolean cutMode = false;
    private boolean probaMode = false;
    private boolean veryCompactMode = false;
    private boolean compactMode = false;
    private boolean searchSpaceMode = false;
    private boolean clearSpaceMode = false;
    private boolean showDisparity = false;
    private boolean testDrawing = false;
               
    boolean windowDeleted = true;
    
    
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    
    protected FingerCluster[] fingers = new FingerCluster[MAX_NB_FINGERS];
  
    
    
    
  
    /** Creates a new instance of PawTracker */
    public PawTrackerStereoBoard(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
      
        
    //     System.out.println("build resetPawTracker4 "+trackerID);
         
        initFilter();
        
        //   System.out.println("---------->>>>>> filter trackedID: "+trackerID);
        resetPawTracker();
        
        //  validateParameterChanges();
        chip.addObserver(this);
        
     //   System.out.println("End build resetPawTrackerStereoBoard");
        
    }
    
    public void initFilter() {
 
    }
    
    
    
    private void resetPawTracker(){
        
    
        
     //   doReset = false;
        
      
     
     //   allEvents.clear();                
        
     //  System.out.println("reset PawTrackerStereoBoard reset");
        
        
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
        
        leftPoints2 = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<leftPoints2.length; i++){
            for (int j=0; j<leftPoints2[i].length; j++){
                leftPoints2[i][j] = new EventPoint(i,j);
            }
        }
        rightPoints2 = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<rightPoints2.length; i++){
            for (int j=0; j<rightPoints2[i].length; j++){
                rightPoints2[i][j] = new EventPoint(i,j);
            }
        }
        
        validateParameterChanges();
        
        //   resetCorrectionMatrix();
        createCorrectionMatrix();
        
        // reset group labels (have a vector of them or.. ?
        
        // scoresFrame = new float[retinaSize][retinaSize];
        fingers = new FingerCluster[MAX_NB_FINGERS];
        nbFingers = 0;
        //   setResetPawTracking(false);//this should also update button in panel but doesn't'
        
        
        
    
        
        setResetPawTracking(false);//this should also update button in panel but doesn't'
        
       // System.out.println("End of resetPawTrackerStereoBoard");
    }
    
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
    
  
    
    
    
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){
                     
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
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
        currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
           // BinocularEvent be=(BinocularEvent)e; 
            
            processEvent(e);
          
            
        }
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }

        
    }
     
    public String toString(){
        String s="PawTrackerStereoBoard";
        return s;
    }
   
      
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
       
        // resetPawTracker();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
           // System.out.println("not a binocular event!");
            return in;
        }
        checkInsideIntensityFrame();
        check3DFrame();
        
        track(in);
        
        if (showWindow) insideIntensityCanvas.repaint();
        if (showWindow&&!windowDeleted) a3DCanvas.repaint();
        return in;
    }
      
   
    public void update(Observable o, Object arg) {
        initFilter();
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
                       
                        if(jh<=yParabolic2(y,i)){
                       // if(i<=yParabolic(y,j)){
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
    
      // return yc of point xc,yc on parabolic of factor y
    private int yParabolic2(int y, int xc){
        int yh = y-retinaSize/2;
        int y2 = 0;
        if (yh<0) y2 = yh+retinaSize/2;
        else y2 = (retinaSize/2)-yh;
        
        int xh = xc-retinaSize/2;
        float yc = -yCurveFactor/10*(yh)*xh*xh + yh;
        //   if(yh==0) System.out.println("yparabolic y:"+y+" xc:"+xc+" yh:"+yh+" xh:"+xh+" yc:"+yc);
        return Math.round((float)yc);
     
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
    
    
    // finger cluster functions
      void addToFingerTracker( EventPoint ep, int method ){
        
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
        
          if(isInSearchSpace(ep.getX0(method),ep.getY0(method),ep.getZ0(method),ep.zDirection)){
              
              // find nearest
              FingerCluster fc = getNearestFinger(ep,finger_surround,method);
              if(fc==null){
                  if(nbFingers<MAX_NB_FINGERS){
                      
                      fingers[nbFingers] = new FingerCluster(ep.getX0(method),ep.getY0(method),ep.getZ0(method));
                      //  System.out.println("create finger at: ["+ep.x+","+ep.y+"] fc: "+fc);
                      
                      nbFingers++;
                      
                      
                  }
              } else {
                  fc.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),finger_mix);
              }
          }
      }
    
      private FingerCluster getNearestFinger( EventPoint ep, int surround, int method ){
        float min_dist=Float.MAX_VALUE;
        FingerCluster closest=null;
       // float currentDistance=0;
        int surroundSq = surround*surround;
        for(FingerCluster fc:fingers){
            if(fc!=null){
                if(fc.activated){
                    
                    float dist = (ep.getX0(method)-fc.x)*(ep.getX0(method)-fc.x) + (ep.getY0(method)-fc.y)*(ep.getY0(method)-fc.y)                    
                            + (ep.getZ0(method)-fc.z)*(ep.getZ0(method)-fc.z);
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
    
    
    
    
    
    
    
    
        private void resetGCAround(EventPoint leadPoints[][] , int x, int y){
          
           leadPoints[x][y].prev_gcx = leadPoints[x][y].gcx;
           leadPoints[x][y].prev_gcx = leadPoints[x][y].gcy;
           leadPoints[x][y].prev_gcx = leadPoints[x][y].gcz;
           leadPoints[x][y].gcx = 0;
           leadPoints[x][y].gcy = 0;
           leadPoints[x][y].gcz = 0;
           
           leadPoints[x][y].prev_ggcx = leadPoints[x][y].ggcx;
           leadPoints[x][y].prev_ggcx = leadPoints[x][y].ggcy;
           leadPoints[x][y].prev_ggcx = leadPoints[x][y].ggcz;
           leadPoints[x][y].ggcx = 0;
           leadPoints[x][y].ggcy = 0;
           leadPoints[x][y].ggcz = 0;
          
       
       }

          private int addToGCAroundFrom(EventPoint leadPoints[][] , int x, int y , EventPoint somePoints[][], 
             int i, int j, int range, int zDirection){
            int total = 0;
            if(somePoints[i][j].disparityLink>NO_LINK){
                if(leadPoints[x][y].disparityLink>somePoints[i][j].disparityLink-range
                        &&leadPoints[x][y].disparityLink<somePoints[i][j].disparityLink+range+1){
                    
                    int z = (somePoints[i][j].disparityLink - i)*zDirection;
                    // x,y,z point in range!
                    total++;
                    
                    
                    
                    leadPoints[x][y].gcx += i;
                    leadPoints[x][y].gcy += j;
                    leadPoints[x][y].gcz += z;
                    
                      // experimental
                    if(i!=x||j!=y){
                        leadPoints[x][y].ggcx += somePoints[i][j].gcx;
                        leadPoints[x][y].ggcy += somePoints[i][j].gcy;
                        leadPoints[x][y].ggcz += somePoints[i][j].gcz;
                    }
                }
            }
            
           return total;
       }
          
              
       private void addToGCAround(EventPoint leadPoints[][] , int x, int y, int range){
        // for all neighbours recompute avg
        // not taking into accound border effect
        boolean toRem = false;
        boolean toAdd = false;
        int value = 0;
      //  int z = leadPoints[x][y].disparityLink - x;
       
        leadPoints[x][y].gcx = 0;
        leadPoints[x][y].gcy = 0;
        leadPoints[x][y].gcz = 0;
        leadPoints[x][y].prev_gcx = 0;
        leadPoints[x][y].prev_gcx = 0;
        leadPoints[x][y].prev_gcx = 0;
        leadPoints[x][y].ggcx = 0;
        leadPoints[x][y].ggcy = 0;
        leadPoints[x][y].ggcz = 0;
        leadPoints[x][y].prev_ggcx = 0;
        leadPoints[x][y].prev_ggcx = 0;
        leadPoints[x][y].prev_ggcx = 0;
        
        int total = 0;
        // apply effect on neighbourhood
        // x range
        for (int i=x-range;i<x+range+1;i++){
            for (int j=y-1;j<y+2;j++){
                
                // y range
                if(i>=0&&i<retinaSize&&j>=0&&j<retinaSize) {
                  
                        // z range
                    // for all 4 representations
                    total += addToGCAroundFrom(leadPoints,x,y,leftPoints,i,j,range,1);
                    total += addToGCAroundFrom(leadPoints,x,y,leftPoints2,i,j,range,1); 
                    total += addToGCAroundFrom(leadPoints,x,y,rightPoints,i,j,range,1);
                    total += addToGCAroundFrom(leadPoints,x,y,rightPoints2,i,j,range,1);
                

                }                
            } 
        }
        
        if(total>0){
          leadPoints[x][y].gcx = leadPoints[x][y].gcx/total;
          leadPoints[x][y].gcy = leadPoints[x][y].gcy/total;
          leadPoints[x][y].gcz = leadPoints[x][y].gcz/total;
          
          // exp
          leadPoints[x][y].ggcx = leadPoints[x][y].ggcx/(total-1);
          leadPoints[x][y].ggcy = leadPoints[x][y].ggcy/(total-1);
          leadPoints[x][y].ggcz = leadPoints[x][y].ggcz/(total-1);
          
        }
       }
    
       
    private void lead_add( int x, int y ){
        //leftPoints[x][y].count++; // change to
    }
         
             
    private void lead_rem( int x, int y, int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][]){
        int yl = y;// + yLeftCorrection;
        int yr = y;// + yRightCorrection;
                               
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        
        EventPoint leadPoints[][];
        EventPoint slavePoints[][];
      
        if (lead_side==LEFT){
            leadPoints = leftPoints;
            slavePoints = rightPoints;                       
        } else {
            leadPoints = rightPoints;
            slavePoints = leftPoints;                        
        }
                
        if(method==LEFT_MOST_METHOD){                        
            int ax = leadPoints[x][yl].disparityLink;
            if (ax>NO_LINK) {
                // rightPoints[ax][yr].free+=leftPoints[ax][yl].getAccValue(leftTime); //?
                
                //  if(rightPoints[ax][yr].getFreeValue(rightTime)>=rightPoints[ax][yr].getAccValue(rightTime)){
                //    rightPoints[ax][yr].attachedTo = -1;
                //  }
                slavePoints[ax][yr].attachedTo = NO_LINK;
                //     rightPoints[ax][yr].free=rightPoints[ax][yr].getValue(rightTime);
                
                leadPoints[x][yl].prevDisparityLink = leadPoints[x][yl].disparityLink;
                leadPoints[x][yl].disparityLink = DELETE_LINK;
                
                // points had a depth but no more, update neighbours average depth
                
                //updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                resetGCAround(leadPoints,x,yl);
                
            } else {
                leadPoints[x][y].disparityLink = DELETE_LINK; //delete
            }
            
        } else {
            int ax = leadPoints[x][yl].disparityLink2;
            if (ax>NO_LINK) {
                // rightPoints[ax][yr].free+=leftPoints[ax][yl].getAccValue(leftTime); //?
                
                //  if(rightPoints[ax][yr].getFreeValue(rightTime)>=rightPoints[ax][yr].getAccValue(rightTime)){
                //    rightPoints[ax][yr].attachedTo = -1;
                //  }
                slavePoints[ax][yr].attachedTo2 = NO_LINK;
                //     rightPoints[ax][yr].free=rightPoints[ax][yr].getValue(rightTime);
                
                leadPoints[x][yl].prevDisparityLink2 = leadPoints[x][yl].disparityLink2;
                leadPoints[x][yl].disparityLink2 = DELETE_LINK;
                
                // points had a depth but no more, update neighbours average depth
                //updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                resetGCAround(leadPoints,x,yl);
                
            } else {
                leadPoints[x][y].disparityLink2 = DELETE_LINK; //delete
            }
        }
        
    }
    
    private void slave_add( int x, int y ){
     //   int yr = y;// + yRightCorrection;
      //  if(yr<0||yr>=retinaSize)return;
     //   rightPoints[x][yr].free=rightPoints[x][yr].getValue(rightTime);
        //rightPoints[x][y].accumulation=rightPoints[ax][y].getCurrentValue(;
    }
    
    private void slave_rem( int x, int y, int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][]){
        int yl = y;// + yLeftCorrection;
        int yr = y;//+ yRightCorrection;
        
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        
        EventPoint leadPoints[][];
        EventPoint slavePoints[][];
        int leadTime;
        int slaveTime;
        
        if (lead_side==LEFT){
            leadPoints = leftPoints;
            slavePoints = rightPoints;
            leadTime = currentTime;
            slaveTime = currentTime;
            
        } else {
            leadPoints = rightPoints;
            slavePoints = leftPoints;
            leadTime = currentTime;
            slaveTime = currentTime;
            
        }
        
        // if (rightPoints[x][yr].free>0)
        
       // rightPoints[x][yr].free=rightPoints[x][yr].getValue(rightTime);
        //      if (rightPoints[x][y].getCurrentValue()>0) rightPoints[x][y].accumulation--;
        
       if(method==LEFT_MOST_METHOD){
            if (slavePoints[x][yr].getValue(slaveTime)<=valueThreshold){
                int ax = slavePoints[x][yr].attachedTo;
                if(ax>NO_LINK){
                    leadPoints[ax][yl].prevDisparityLink = x;
                    leadPoints[ax][yl].disparityLink = DELETE_LINK; //delete
                    slavePoints[x][yr].attachedTo = NO_LINK;
                    
                    // points had a depth but no more, update neighbours average depth
                    
                    //#### Removing point
                    
                   // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                    resetGCAround(leadPoints,ax,yl);
                }
                
            }
       } else {
             if (slavePoints[x][yr].getValue(slaveTime)<=valueThreshold){
                int ax = slavePoints[x][yr].attachedTo2;
                if(ax>NO_LINK){
                    leadPoints[ax][yl].prevDisparityLink2 = x;
                    leadPoints[ax][yl].disparityLink2 = DELETE_LINK; //delete
                    slavePoints[x][yr].attachedTo2 = NO_LINK;
                    
                    // points had a depth but no more, update neighbours average depth
                    
                     //#### Removing point
                    
                    
                   // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                    resetGCAround(leadPoints,ax,yl); // add leftMost method as parameter here?
                }
                
            }
       }
     
    }
    
    private void slave_check( int x, int y, int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){
        
        int yl = y;// + yLeftCorrection;
        int yr = y;// + yRightCorrection;
        
        if(yl<0||yl>=retinaSize)return;
        if(yr<0||yr>=retinaSize)return;
        
        
        
        boolean done  = false;
        int ax = 0;
        
        EventPoint leadPoints[][];
        EventPoint slavePoints[][];
        int leadTime;
        int slaveTime;
        
        if (lead_side==LEFT){
            leadPoints = leftPoints;
            slavePoints = rightPoints;
            leadTime = currentTime;
            slaveTime = currentTime;
            
        } else {
            leadPoints = rightPoints;
            slavePoints = leftPoints;
            leadTime = currentTime; //to uniformise
            slaveTime = currentTime;
            
        }
        
       if(method==LEFT_MOST_METHOD){
            
            for(int i=0;i<slavePoints.length;i++){
                if(done) break;
                
                if((slavePoints[i][yr].getValue(slaveTime)>valueThreshold)&&
                        isInRange(i,x,disparity_range)&&
                        ((slavePoints[i][yr].getValue(slaveTime)<=leadPoints[x][yl].getValue(leadTime)+valueMargin)
                        &&(slavePoints[i][yr].getValue(slaveTime)>=leadPoints[x][yl].getValue(leadTime)-valueMargin))){
                    ax = slavePoints[i][yr].attachedTo;
                    if(ax>NO_LINK){
                        if(leadPoints[ax][yl].getValue(leadTime)<valueThreshold){
                            leadPoints[ax][yl].prevDisparityLink = i;
                            leadPoints[ax][yl].disparityLink = DELETE_LINK;
                            slavePoints[i][yr].attachedTo = NO_LINK;
                            
                        
                             // points had a depth but no more, update neighbours average depth
                            
                             //#### Removing point
                            
                            //updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                            resetGCAround(leadPoints,ax,yl);
                            ax = NO_LINK;
                           // slavePoints[i][yr].attachedTo = -1;
                        }
                    }
                    if(ax>x||ax==NO_LINK){
                        slavePoints[i][yr].attachedTo = x;
                        
                        
                        if(leadPoints[x][yl].disparityLink>NO_LINK){
                            slavePoints[leadPoints[x][yl].disparityLink][yr].attachedTo = NO_LINK;
                        }
                        leadPoints[x][yl].disparityLink = i;
                        
                        // points now has a depth, update neighbours average depth
                        
                         //#### Adding point
                        
                     //   updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                        addToGCAround(leadPoints,x,yl,dispAvgRange);  
                        
                        
                        addToFingerTracker(leadPoints[x][yl],method);
                                
                        //   if(rightPoints[i][yr].getFreeValue(rightTime)>0){
                        //rightPoints[i][y].free-=leftPoints[ax][y].getCurrentValue();
                        //   rightPoints[i][yr].free=0;
                        // detach previous left
                        
                        //  }
                        if (ax>NO_LINK){
                            leadPoints[ax][yl].prevDisparityLink = i;
                            leadPoints[ax][yl].disparityLink = DELETE_LINK;
                            
                             //#### Removing point
                            
                            // points had a depth but no more, update neighbours average depth
                           // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                            resetGCAround(leadPoints,ax,yl);
                        }
                        done = true;
                    }
                } else {
                    
                    // debug
                //    System.out.println("");
                    
                }
            } // end for
            
       } else { // right most method
            
               for(int i=slavePoints.length-1;i>=0;i--){
                if(done) break;
                
                if((slavePoints[i][yr].getValue(slaveTime)>valueThreshold)&&
                        ((slavePoints[i][yr].getValue(slaveTime)<=leadPoints[x][yl].getValue(leadTime)+valueMargin)
                        &&(slavePoints[i][yr].getValue(slaveTime)>=leadPoints[x][yl].getValue(leadTime)-valueMargin))){
                    ax = slavePoints[i][yr].attachedTo2;
                    if(ax>NO_LINK){
                        if(leadPoints[ax][yl].getValue(leadTime)<valueThreshold){
                            leadPoints[ax][yl].prevDisparityLink2 = i;
                            leadPoints[ax][yl].disparityLink2 = DELETE_LINK;
                            slavePoints[i][yr].attachedTo2 = NO_LINK;
                            
                             // points had a depth but no more, update neighbours average depth
                            //updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                            
                             //#### Removing point
                            
                            resetGCAround(leadPoints,ax,yl);
                            ax = NO_LINK;
                          //  slavePoints[i][yr].attachedTo = -1;
                        }
                    }
                    if(ax<x||ax==NO_LINK){
                        slavePoints[i][yr].attachedTo2 = x;
                        
                        
                        if(leadPoints[x][yl].disparityLink2>NO_LINK){
                            slavePoints[leadPoints[x][yl].disparityLink2][yr].attachedTo2 = NO_LINK;
                        }
                        leadPoints[x][yl].disparityLink2 = i;
                         // points now has a depth, update neighbours average depth
                        
                         //#### Adding point
                        
                       // updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                        addToGCAround(leadPoints,x,yl,dispAvgRange);  
                        
                        addToFingerTracker(leadPoints[x][yl],method);
                                
                                
                        //   if(rightPoints[i][yr].getFreeValue(rightTime)>0){
                        //rightPoints[i][y].free-=leftPoints[ax][y].getCurrentValue();
                        //   rightPoints[i][yr].free=0;
                        // detach previous left
                        
                        //  }
                        if (ax>NO_LINK){
                            leadPoints[ax][yl].prevDisparityLink2 = i;
                            leadPoints[ax][yl].disparityLink2 = DELETE_LINK;
                             // points had a depth but no more, update neighbours average depth
                            
                             //#### Removing point
                            
                           // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                             resetGCAround(leadPoints,ax,yl);
                        }
                        done = true;
                    }
                }
            } // end for
            
            
       }
            
            
            
    
          if(done&&ax!=NO_LINK) slave_check(ax,y,lead_side,method,leftPoints,rightPoints);
        
    }
    
    
    private void lead_check( int y , int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){
        int yl = y;// + yLeftCorrection;
        
        if(yl<0||yl>=retinaSize)return; //to remove
        
        // look for unassigned left events
        boolean done  = false;
        
        EventPoint leadPoints[][];
        
        int leadTime = currentTime;
        
        
        if (lead_side==LEFT){
            leadPoints = leftPoints;
            
          
            
            
        } else {
            leadPoints = rightPoints;
            
          
            
            
        }
        
        
        if(method==LEFT_MOST_METHOD){
            for(int i=0;i<leadPoints.length;i++){
                if(done) break;
                if ((leadPoints[i][yl].getValue(leadTime)>valueThreshold)){//&&(leftPoints[i][yl].disparityLink<0)){
                    
                    slave_check(i,y,lead_side,method,leftPoints,rightPoints);
                    done = true;
                }
            }
        } else {
            for(int i=leadPoints.length-1;i>=0;i--){
                if(done) break;
                if ((leadPoints[i][yl].getValue(leadTime)>valueThreshold)){//&&(leftPoints[i][yl].disparityLink<0)){
                    
                    slave_check(i,y,lead_side,method,leftPoints,rightPoints);
                    done = true;
                }
            }
            
        }

                
    }
    
    
    private boolean isInRange( int x, int y , int range){
        if(x>y){
            if(x-y>range) return false;
            return true;
        } else {
            if(y-x>range) return false;
            return true;
        }                       
    }
     
    private void processDisparity( int leftOrRight, int x, int y, float value, float previous_value, int lead_side,
                                   int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){ // + event
        int type = 0;
        if(previous_value<value){
            type = 1;
        }
        
     
        
        if(leftOrRight==lead_side){
           
            boolean change = false;
            
            //  value = leftPoints[e.x][cy].getAccValue(leftTime);
            
            if(type==1){
                // increase lx count
                if(value>valueThreshold){
                    
                    change = true;
                }
                
            } else { //remove event
                if(value<=valueThreshold){
                    lead_rem( x, y, lead_side,method,leftPoints,rightPoints);
                    change = true;
                }
                
            }
            // call right check
            if (change) slave_check(x,y,lead_side,method,leftPoints,rightPoints);
            
        } else {
          
            boolean change = false;
            //     value = rightPoints[e.x][cy].getAccValue(rightTime);
            
            if(type==1){
                
                // increase lx count
            
                if(value>valueThreshold){
                    
                    change = true;
                }
            } else { //remove event
                if(value<=valueThreshold){
                    slave_rem( x, y, lead_side,method,leftPoints,rightPoints );
                    change = true;
                }
                
            }
            if (change) lead_check(y,lead_side,method,leftPoints,rightPoints);
        }                        
    }
    
  
        // processing one event
    protected void processEvent(BinocularEvent e){
        
        // resetEnabled = true;
        //int leftOrRight = e.side;
        int leftOrRight = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
        
       //   System.out.println("processEvent leftOrRight:"+leftOrRight+" e.eye:"+e.eye+" type:"+e.getType());
        
        int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
                
        int dy = e.y;
        int dx = e.x;
        
        // shift y
        if(leftOrRight==LEFT){
            dy += yLeftCorrection;
          //  leftTime = e.timestamp;
        } else {
            dy += yRightCorrection;
         //   rightTime = e.timestamp;
        }
        
        // to add: shift x
                
        // to add : rotate around center
        // for any x,y, find new xr,yr after rotation around center
        // center of picture is 64,64
        int half = retinaSize/2;
        float correctAngle = 0;
        if (leftOrRight==LEFT){
            correctAngle = correctLeftAngle;
        } else {
            correctAngle = correctRightAngle;
        }
        if (correctAngle!=0){
            int xr = Math.round((float) ( (Math.cos(Math.toRadians(correctAngle))*(dx-half)) -
                    (Math.sin(Math.toRadians(correctAngle))*(dy-half)) )) + half;
            int yr = Math.round((float) ( (Math.sin(Math.toRadians(correctAngle))*(dx-half)) +
                    (Math.cos(Math.toRadians(correctAngle))*(dy-half)) )) + half;
 
            dy = yr;
            dx = xr;
        }
        
        //int cy = e.y;
        
        if(dx<0||dx>retinaSize){
            return;
        
        }
        
        // correct y curvature
        int cy = dy;
        if(dy>=0&&dy<retinaSize){
            if(correctY){
                cy = correctionMatrix[e.x][cy];
            }
        } else return;
        
        if(cy<0||cy>=retinaSize){
            return;
        }
        
         // uncomment to try resolve time tou exception but slow and is it really working?
     // chip.getAeViewer().aePlayer.pause();
        
        // call
        float value = 0;
        
        
        if(leftOrRight==LEFT){
           
        // if(type==1)  System.out.println("processEvent leftPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
        
            leftPoints[e.x][cy].updateFrom(e,e.x,cy,LEFT);
            //leftPoints2[e.x][cy].updateFrom(e,e.x,cy,LEFT);
            // filter
            if(useDualFilter){
                fastDualLowFilterFrame(leftPoints[e.x][cy], leftPoints, rightPoints );
          //      fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
         //       fastDualLowFilterFrame(leftPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
           //     fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
            } else {
                value = leftPoints[e.x][cy].getAccValue(currentTime);
                float previousValue = 0;
                if(type==0){
                    previousValue = value+1;
                }
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
               // processDisparity( leftOrRight, e.x, cy,  value, type, RIGHT, true );
            }
        } else {
            
             // System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type);
     //  if(type==1) System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
        
            
            rightPoints[e.x][cy].updateFrom(e,e.x,cy,RIGHT);
            //rightPoints2[e.x][cy].updateFrom(e,e.x,cy,-1);
            // filter
            if(useDualFilter){
                 fastDualLowFilterFrame(rightPoints[e.x][cy], leftPoints, rightPoints );
              //   fastDualLowFilterFrame(rightPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
             //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, LEFT, true, leftPoints, rightPoints);
             //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
             //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, RIGHT, true, leftPoints, rightPoints);
             //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
            } else { 
                value = rightPoints[e.x][cy].getAccValue(currentTime);
                float previousValue = 0;
                if(type==0){
                    previousValue = value+1;
                }
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, e.x, cy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
             //   processDisparity( leftOrRight, e.x, cy,  value, type, RIGHT, true );
            }
        }                 
    }
  
       
    
   
      private int xFromSearchSpace( int x, int y, int z, int zDirection){
          int y_rx = 0;
          int z_rx = rotateZonX( y, z, 0,0, viewAngle);
          int half = retinaSize/2;
          
          
          
          int x_ry = rotateXonY( x, z_rx, half,0, 180-middleAngle);
          int z_ry = rotateZonY( x, z_rx, half,0, 180-middleAngle);

         
          
           
           return x_ry;
      }
      
        private int yFromSearchSpace( int x, int y, int z, int zDirection){
          int y_rx = rotateYonX( y, z, 0, 0, viewAngle);
         
         
          
           
           return y_rx;
      }
      
      
    private int zFromSearchSpace( int x, int y, int z, int zDirection){
     
        int z_rx = 0;
        int half = retinaSize/2;
        int x_ry ;
        int z_ry ;
        
        
        z_rx = rotateZonX( y, z, 0, 0, viewAngle);
        z_ry = rotateZonY( x, z_rx, half, 0, 180-middleAngle);
        
        
      
           
           return z_ry;
      }
      
     private boolean isInSearchSpace( int x, int y, int z, int zDirection){
          boolean res = true;
         
         
          int y_rx = rotateYonX( y, z, 0, 0, viewAngle);
          int z_rx = rotateZonX( y, z, 0, 0, viewAngle);
          
          int z_rx2 = rotateZonX( y_rx, z_rx, door_ya, -door_z, platformAngle);
          int y_rx2 = rotateYonX( y_rx, z_rx, door_ya, -door_z, platformAngle);
          
          int half = retinaSize/2;
          
                    
       //   int x_ry = rotateXonY( x, z_rx, half, 180-middleAngle);
          int z_ry = rotateZonY( x, z_rx, half, 0, 180-middleAngle);

       
         
        // point must be in front of cage door and above cage's platform
     
      //  if(z_ry*zDirection<=-door_z){
        if(z_ry>-door_z){
            res = false;
        }     
        if(y_rx2<door_ya){
            res = false;
        } 
      //  if(y_rx2>door_ya+20){
      //      res = false;
      //  } 
          
        return res;
    
      }
    
     
     protected int rotateYonX( int y, int z, int yRotationCenter, int zRotationCenter, float angle){
         return( Math.round((float) ( (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)) +
                 (Math.cos(Math.toRadians(angle))*(y-yRotationCenter)) ))+yRotationCenter );
     }
     protected int rotateZonX( int y, int z, int yRotationCenter, int zRotationCenter, float angle){
         return( Math.round((float) ( (Math.cos(Math.toRadians(angle))*(z-zRotationCenter)) -
                 (Math.sin(Math.toRadians(angle))*(y-yRotationCenter)) ))+zRotationCenter );
     }
     protected int rotateXonY( int x, int z, int xRotationCenter, int zRotationCenter, float angle){
         return( Math.round((float) ( (Math.cos(Math.toRadians(angle))*(x-xRotationCenter)) -
                 (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)) ))+xRotationCenter );
         
     }
     protected int rotateZonY( int x, int z, int xRotationCenter, int zRotationCenter, float angle){
         return( Math.round((float) ( (Math.sin(Math.toRadians(angle))*(x-xRotationCenter)) +
                 (Math.cos(Math.toRadians(angle))*(z-zRotationCenter))) )+zRotationCenter );
     }
    
      protected float distanceBetween( int x1, int y1, int x2, int y2){
        
        double dx = (double)(x1-x2);
        double dy = (double)(y1-y2);
        
        float dist = (float)Math.sqrt((dy*dy)+(dx*dx));
        
        
        return dist;
    }
    
     protected float distanceBetween( int x1, int y1, int z1, int x2, int y2, int z2){
        
        double dx = (double)(x1-x2);
        double dy = (double)(y1-y2);
        double dz = (double)(z1-z2);
        
        float dist = (float)Math.sqrt((dy*dy)+(dx*dx)+(dz*dz));
        
        
        return dist;
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
    // leftMost is policy of disparity matching 
    void fastDualLowFilterFrame( EventPoint ep, EventPoint[][] leftPoints, EventPoint[][] rightPoints ){
        
        float dist = 0;
        float f = 0;
        float dr = 0;
        float sdr = 0;
      //  int cat = 0;
      //  float bn = 0;
      // float sn = 0;
     
        
        EventPoint[][] eventPoints;
        int leftOrRight = ep.zDirection;
        EventPoint[][] leadPoints;
        EventPoint[][] slavePoints;
        
        if(ep.side==LEFT){
            eventPoints = leftPoints;
          
        } else {
            eventPoints = rightPoints;
          
        }

                
                
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
                             influencedPoint.previousLargeFilteredValue = influencedPoint.largeFilteredValue;
                                
                            
                            influencedPoint.largeFilteredValue += (ep.lastValue * f)/largeRangeTotal;
                            if (influencedPoint.largeFilteredValue<0) influencedPoint.largeFilteredValue = 0;
                            // could use topographic terrasses here, calling obtainedDensity( ), or not
                            
                            // uncomment when try to extract 3D from large filtered value
                            // but then modify also EventPoint.getValue()
                            /*
                            if(useLarge){
                                if(influencedPoint.largeFilteredValue >0){
                                    
                                    processDisparity( leftOrRight, influencedPoint.x, influencedPoint.y,
                                            influencedPoint.largeFilteredValue,
                                            influencedPoint.previousLargeFilteredValue, lead_side, leftMost,
                                            leadPoints, slavePoints);
                                }
                            }
                             **/
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
                            //    if(!useLarge){
                                    processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
                                            influencedPoint.shortFilteredValue,
                                            influencedPoint.previousShortFilteredValue, LEFT, LEFT_MOST_METHOD,
                                            leftPoints, rightPoints);
                                    processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
                                            influencedPoint.shortFilteredValue,
                                            influencedPoint.previousShortFilteredValue, LEFT, RIGHT_MOST_METHOD,
                                            leftPoints, rightPoints);
                                     processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
                                            influencedPoint.shortFilteredValue,
                                            influencedPoint.previousShortFilteredValue, RIGHT, LEFT_MOST_METHOD,
                                            leftPoints, rightPoints);
                                    processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
                                            influencedPoint.shortFilteredValue,
                                            influencedPoint.previousShortFilteredValue, RIGHT, RIGHT_MOST_METHOD,
                                            leftPoints, rightPoints);
                           //     }
//                                processDisparity( leftOrRight, influencedPoint.x, influencedPoint.y,
//                                        influencedPoint.shortFilteredValue,
//                                        influencedPoint.previousShortFilteredValue, RIGHT, true,leftPoints, rightPoints );
                            
                                
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
    
    
    // show 2D view
      
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
                
                 if(e.getKeyCode()==KeyEvent.VK_M){
                    // show method
                    method++;
                    if(method>6)method=0;
                    System.out.println("show method:"+method);
//                    switch(method){
//                        case 0: System.out.println("show left left-most"); break;
//                        case 1: System.out.println("show left right-most"); break;
//                        case 2: System.out.println("show right left-most"); break;
//                        case 3: System.out.println("show onright right-most"); break;
//                        default:;
//                    }
                    insideIntensityCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_F){
                    showOnlyAcc=!showOnlyAcc;
                    insideIntensityCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_D){
                    showDisparity=!showDisparity;
                    insideIntensityCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_T){
                    testDrawing=!testDrawing;
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
                
             //   System.out.println("Selected pixel x,y=" + x + "," + y);
                
                int jr = y;// - yRightCorrection;
                int jl = y;//- yLeftCorrection;
                if(jr>=0&&jr<retinaSize&&jl>=0&&jl<retinaSize&&x>=0&&x<retinaSize){
                    EventPoint epL = leftPoints[x][jl];
                    EventPoint epR = rightPoints[x][jr];
                    EventPoint epL2 = leftPoints2[x][jl];
                    EventPoint epR2 = rightPoints2[x][jr];
                    
                    float flr=-1;
                    float link=NO_LINK;
                    if (evt.getButton()==1){
                       highlight_xR = epL.disparityLink;
                         if(highlight_xR>0&&highlight_xR<retinaSize){
                           EventPoint epLR = rightPoints[highlight_xR][jr];
                           
                           flr = epLR.getValue(currentTime);
                           
                         }                      
                    } else if (evt.getButton()==2){
                        highlight_xR = epR.attachedTo;
                        
                        if(highlight_xR>0&&highlight_xR<retinaSize){
                            EventPoint epLR = leftPoints[highlight_xR][jr];
                            
                            flr = epLR.getValue(currentTime);
                            link = epLR.disparityLink;
                        }
                    }
                                                         
                    float fr=0;
                    float fl=0;
                    
                    
                    fr = epR.getValue(currentTime);
                    fl = epL.getValue(currentTime);
                    
                    
                    float vll = 0;
                    float vlr = 0;
                    float vrl = 0;
                    float vrr = 0;
    
                    int dll = NO_LINK,dlr = NO_LINK,drl = NO_LINK,drr = NO_LINK;
                    if(epL.disparityLink>NO_LINK){
                        dll = epL.disparityLink - x;
                        EventPoint epLk = rightPoints[epL.disparityLink][jl];
                        vll = epLk.getValue(currentTime);
                    }
                    if(epL2.disparityLink>NO_LINK){
                        dlr = epL2.disparityLink - x;
                        EventPoint epL2k = rightPoints2[epL2.disparityLink][jl];
                        vlr = epL2k.getValue(currentTime);
                    }
                    if(epR.disparityLink>NO_LINK){
                        drl = epR.disparityLink - x;
                        EventPoint epRk = leftPoints[epR.disparityLink][jr];
                        vrl = epRk.getValue(currentTime);
                    }
                    if(epR2.disparityLink>NO_LINK){
                        drr = epR2.disparityLink - x;
                        EventPoint epR2k = leftPoints2[epR2.disparityLink][jr];
                        vrr = epR2k.getValue(currentTime);
                    }
                    
                //    if (flr>-1) {
                        if (evt.getButton()==1){
                             System.out.println("LL("+x+","+jl+")="+fl+" z:"+dll+" linked to ("+epL.disparityLink+","+jl+")="+vll );
                             System.out.println("LR("+x+","+jl+")="+fl+" z:"+dlr+" linked to ("+epL2.disparityLink+","+jl+")="+vlr );
                             System.out.println("RL("+x+","+jr+")="+fr+" z:"+drl+" linked to ("+epR.disparityLink+","+jl+")="+vrl );
                             System.out.println("RR("+x+","+jr+")="+fr+" z:"+drr+" linked to ("+epR2.disparityLink+","+jl+")="+vrr );

                            
                            
                           //   System.out.println("Left("+x+","+jl+")=" + fl + " linked to right("+highlight_xR+","+jr+")="+flr);
                          //    System.out.println("with z:"+leftPoints[x][y].z+" and z0:"+leftPoints[x][y].z0);
                        } else if (evt.getButton()==2){
                            //  System.out.println("Right("+x+","+jr+")=" + fr + " linked to right("+highlight_xR+","+jl+")="+flr+" dlink:"+link);
                            //  System.out.println("with z:"+leftPoints[highlight_xR][y].z+" and z0:"+leftPoints[highlight_xR][y].z0);
                     
                             System.out.println("LL("+x+","+jl+"):"+dll );
                             System.out.println("LR("+x+","+jl+"):"+dlr );
                             System.out.println("RL("+x+","+jr+"):"+drl );
                             System.out.println("RR("+x+","+jr+"):"+drr );

                        }
                        
                //    } else {
                //        System.out.println("Left("+x+","+jl+")=" + fl + " not linked");
                //    }
                    
                    
              //      System.out.println("+ Right("+x+","+jr+")="+fr);
                    
                 //   float rt = epR.updateTime;
                 //   float lt = epL.updateTime;
                    
              //      System.out.println("left event time:"+lt+" lefttime: "+currentTime);
              //      System.out.println("right event time:"+rt+" righttime: "+currentTime);
                    
                    if(testDrawing){
                        if (evt.getButton()==1){
                            leftPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            leftPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            
                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
        
                        } else if (evt.getButton()==2){
                            rightPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            rightPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            
                            processDisparity( RIGHT, x, jl,  1.0f, 0, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( RIGHT, x, jl,  1.0f, 0, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( RIGHT, x, jl,  1.0f, 0, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( RIGHT, x, jl,  1.0f, 0, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
        
                            
                        } else if (evt.getButton()==0){
                            leftPoints[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
                           // leftPoints2[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
                            rightPoints[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
                          //  rightPoints2[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
                            
                        }
                    }
                    
                    
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
                        float f = 0;
                        if(showCorrectionGradient){
                            // to get it in gradient
                            f = (float)intMatrix[i][j]/(float)retinaSize; // to get it in gradient
                            gl.glColor3f(f,f,f);
                        } else {
                            
                            f = (float)intMatrix[i][j];
                            if(f>0){
                                
                                if (f%2==0)
                                    gl.glColor3f(1,1,1);
                                else
                                    gl.glColor3f(0,0,0);
                            }
                        }
                        gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                    }
                    
                }
                
            }
            
            
            
            private void drawEventPoints( EventPoint[][] eventPoints, GL gl, int currentTime, boolean left, int yCorrection ){
                
            //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);
                
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
                            if(showSecondFilter){
                               f = ep.largeFilteredValue;
                            } else {
                            
                              //  f = ep.getValue(currentTime);
                             //   f = ep.getShortFilteredValue(currentTime);
                                f = ep.getValue(currentTime);
                            }
                        }
                        
                        //   f = ep.accValue;
                        
                     //   System.out.println("2. display drawEventPoints f:"+f+" time: "+currentTime);
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
                                    
                                 //   System.out.println("left:"+left+" value("+i+","+j+"):"+f);
                                    
                                    if(redBlueShown==0||redBlueShown==2){
                                        if(ep.disparityLink>NO_LINK&&showDisparity){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        } else {
                                            gl.glColor3f(0,0,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        }
                                    } else if(redBlueShown==3){
                                        if(ep.disparityLink>NO_LINK){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                      //  gl.glColor3f(0,0,f);
                                      //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                    } 
                                } else {
                                    if(redBlueShown==0||redBlueShown==1){
                                        
                                        if(ep.attachedTo>NO_LINK&&showDisparity){
                                            gl.glColor3f(f,0.5f,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        } else {
                                            gl.glColor3f(f,0,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                       
                                        }
                                    } else if(redBlueShown==3){  
                                        if(ep.attachedTo>NO_LINK){
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
                    
                    
                } else { //if(showAcc){
                    //  System.out.println("display left - right  showAcc");
                    
                   
               //     drawEventPoints(leftPoints,gl,currentTime,true,0);
                 
                  
                
                      if(method==0||method==4||method==6){
                            if(leftPoints!=null){
                                // System.out.println("display left ");                                
                                drawEventPoints(leftPoints,gl,currentTime,true,yLeftCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                            }
                            if(rightPoints!=null){
                                //  System.out.println("display right ");
                                
                                drawEventPoints(rightPoints,gl,currentTime,false,yRightCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                            }                                                        
                        }
                       if(method==1||method==4||method==6){
                            if(leftPoints2!=null){
                                // System.out.println("display left ");                                
                                drawEventPoints(leftPoints2,gl,currentTime,true,yLeftCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                            }
                            if(rightPoints2!=null){
                                //  System.out.println("display right ");
                                
                                drawEventPoints(rightPoints2,gl,currentTime,false,yRightCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                            }                                                        
                        }
                        if(method==2||method==5||method==6){
                            if(leftPoints!=null){
                                // System.out.println("display left ");                                
                                drawEventPoints(leftPoints,gl,currentTime,false,yLeftCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                            }
                            if(rightPoints!=null){
                                //  System.out.println("display right ");
                                
                                drawEventPoints(rightPoints,gl,currentTime,true,yRightCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                            }                                                        
                        }
                          if(method==3||method==5||method==6){
                            if(leftPoints2!=null){
                                // System.out.println("display left ");                                
                                drawEventPoints(leftPoints2,gl,currentTime,false,yLeftCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                            }
                            if(rightPoints2!=null){
                                //  System.out.println("display right ");
                                
                                drawEventPoints(rightPoints2,gl,currentTime,true,yRightCorrection);
                            } else {
                                System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                            }                                                        
                        }
                                      
       
                    
                }
                
              
               
                
              
            
                
                                         
                if(highlight){
                    gl.glColor3f(1,1,0);
                    gl.glRectf(highlight_x*intensityZoom,highlight_y*intensityZoom,(highlight_x+1)*intensityZoom,(highlight_y+1)*intensityZoom);
                    gl.glColor3f(0,1,0);
                    gl.glRectf(highlight_xR*intensityZoom,highlight_y*intensityZoom,(highlight_xR+1)*intensityZoom,(highlight_y+1)*intensityZoom);
                    
                }
                
              
                        
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
    
    // show 3D view
    
         
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
    float kry = 0;
    
//    GLUT glut=null;
    void create3DFrame(){
        a3DFrame=new JFrame("3D Frame");
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
                
                 if(e.getKeyCode()==KeyEvent.VK_R){
                    // show color, red&blue, onlyred, only blue
                    display3DChoice++;
                    if(display3DChoice>9)display3DChoice=0;
                    switch(display3DChoice){
                        case 0: System.out.println("show all"); break; //not bad with average mode on and fuse mode off
                        case 1: System.out.println("show left leader, left most and right most"); break;
                        case 2: System.out.println("show left leader, left most"); break;
                        case 3: System.out.println("show left leader, right most"); break;
                        case 4: System.out.println("show right leader, left most and right most"); break;
                        case 5: System.out.println("show right leader, left most"); break;
                        case 6: System.out.println("show right leader, right most"); break;
                        case 7: System.out.println("show average of all"); break;//inefficient yet
                        case 8: System.out.println("show left and right, left most"); break;
                        case 9: System.out.println("show left and right, right most"); break;
                        default:;
                    }
                    a3DCanvas.display();
                    
                }
                if(e.getKeyCode()==KeyEvent.VK_A){
                    averageMode = !averageMode;
                    a3DCanvas.display();
                }
                if(e.getKeyCode()==KeyEvent.VK_Z){
                    fuseMode = !fuseMode;
                    a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_C){
                    cutMode = !cutMode;
                    a3DCanvas.display();
                }
                if(e.getKeyCode()==KeyEvent.VK_P){
                    probaMode = !probaMode;
                    a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_O){
                    compactMode = !compactMode;
                    a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_V){
                    veryCompactMode = !veryCompactMode;
                    a3DCanvas.display();
                 }
                 if(e.getKeyCode()==KeyEvent.VK_S){
                    searchSpaceMode = !searchSpaceMode;
                    a3DCanvas.display();
                 }
                if(e.getKeyCode()==KeyEvent.VK_D){
                    clearSpaceMode = !clearSpaceMode;
                    a3DCanvas.display();   
                }
                
                
                 if(e.getKeyCode()==KeyEvent.VK_B){
                    // show color, red&blue, onlyred, only blue
                    testChoice++;
                    if(testChoice>7)testChoice=0;
                    System.out.println("testChoice=="+testChoice);
                   a3DCanvas.display();
                }
                
            }                                    
        });
                        
        a3DCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
                                    
       private void draw3DDisparityPoints( GL gl, EventPoint leadPoints[][], int method, int leadTime, EventPoint slavePoints[][], int slaveTime, int zDirection ){
                      
                int z = 0;
                float fx = 0;
                float fy = 0;
                float fz = 0;
                float dl = 0;
                float dr = 0;
                int dx = NO_LINK;
                int dx1 = NO_LINK;
                int dx2 = NO_LINK;
                int dxL = NO_LINK;
                int z0 = 0;
                int x0 = 0;
                int z1 = 0;
                int x1 = 0;
                int y1 = 0;
                
                //    System.out.println("draw3DDisparityPoints");
              //     System.out.println("draw3DDisparityPoints at "+leadTime);
                   
                int half = retinaSize/2;
                                
                for(int x=0;x<leadPoints.length;x++){
                    for(int y=0;y<leadPoints[x].length;y++){
                        
                        boolean go = true;
                        if(leadPoints[x][y]==null){
                            //  System.out.println("leftpoints["+x+"]["+y+"] null");
                            go = false;
                        } else {
                            
                            if(leadPoints[x][y].getValue(leadTime)<valueThreshold){
                                //    System.out.println("leadpoints["+x+"]["+y+"] value:"+leadPoints[x][y].getValue(leadTime)+" threshold:"+valueThreshold);
                                // break;
                                go = false;
                            }
                        }
                        
                        if(go){
                        if(method==LEFT_MOST_METHOD)      {             
                          dx = leadPoints[x][y].disparityLink;
                        } else {
                          dx = leadPoints[x][y].disparityLink2;   
                        }
                            
                        if(dx>NO_LINK){
                            //  dx = 10;
                           /**                            
                            z = (dx - x); // * -zFactor;
                            
                                                                                    
                            if(compactMode){
                                 x1 = Math.round(leadPoints[x][y].gcx);
                                 y1 = Math.round(leadPoints[x][y].gcy);
                                 z1 = Math.round(leadPoints[x][y].gcz);
                            } else  if(veryCompactMode){
                                 x1 = Math.round(leadPoints[x][y].ggcx);
                                 y1 = Math.round(leadPoints[x][y].ggcy);
                                 z1 = Math.round(leadPoints[x][y].ggcz);
                            } else {
                                 x1 = x;
                                 y1 = y;
                                 z1 = z;
                            }
                                                                                       
                      
                            if(notCrossing){
                                if (z1>0) z1 = -z1;
                            }
                            
                            
                            
                            x0 = Math.round((float) ( -
                                    (Math.sin(Math.toRadians(planeAngle))*(z1)) ))+x1;                                                                                 
                            z0 = Math.round((float) (
                                    (Math.cos(Math.toRadians(planeAngle))*(z1*zDirection)) ));
                           
                          //  z0 =  z0*zDirection;
                            
                            // symetrical
                            if(zDirection==-1){
                                int x2 = Math.round((float) ( -
                                        (Math.sin(Math.toRadians(planeAngle))*(retinaSize)) ));
                                int z2 = Math.round((float) (
                                        (Math.cos(Math.toRadians(planeAngle))*(retinaSize)) ));
                                                           
                                // obtain orthogonal direction to 0-x2
                                float correctMiddleAngle = (orientation(half,0,x2,z2)-90)*2;
                                    
                              
                                
                             //   System.out.println("middleAngle 1 : "+middleAngle);
                              
                                // rotate points
                               int x3= Math.round((float) ( (Math.cos(Math.toRadians(correctMiddleAngle))*(x0-half)) -
                               (Math.sin(Math.toRadians(correctMiddleAngle))*(z0)) ))+half;
                               int z3 = Math.round((float) ( (Math.sin(Math.toRadians(correctMiddleAngle))*(x0-half)) +
                              (Math.cos(Math.toRadians(correctMiddleAngle))*(z0)) ));
                               x0 = x3;
                               z0 = z3;
                           
                             }    
                            **/
                            
                            x0 = leadPoints[x][y].getX0(method);
                            y1 = leadPoints[x][y].getY0(method);
                            z0 = leadPoints[x][y].getZ0(method);

                            
                            //debug
                        //    leftPoints[x][y].z0=z0;
                            
                            boolean  highlighted = false;
                            if(highlight){
                                //if(x==highlight_x&&y+yLeftCorrection==highlight_y){
                                if(x==highlight_x&&y==highlight_y){
                                    shadowCube(gl, x0, y1, z0, cube_size, 1, 1, 0, 1, shadowFactor);
                                    highlighted = true;
                                }
                            }
                            
                            if (!highlighted){
                                
                                if(showShadows){
                                    shadowCube(gl, x0, y1, z0, cube_size, 1, 1, 1, alpha, shadowFactor);
                                    //   System.out.println("draw3DDisparityPoints shadowCube "+1);
                                } else {
                                    //gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                    //gl.glEnable(gl.GL_BLEND);
                                    
                                    dl = leadPoints[x][y].getValue(leadTime); // or getAccValue()?
                                   
                                    dr = slavePoints[dx][y].getValue(slaveTime);
                                   
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
                                        fz = colorizeFactor*((retinaSize-z)%colorizePeriod);
                                    } else {
                                        fz = f;
                                    }
                                    float b = 0.1f*brightness;
                                    
                                    float db = 0;
                                   // float dt = leadTime - leadPoints[x][y].updateTime;
                                   // db = 1 - (decayTimeLimit - dt);
                                  //  if(db<0) db = 0;
                                    if(highlightDecay){
                                       db = 1 - decayedValue(1,leadTime - leadPoints[x][y].updateTime);
                                    }
                                    int tt = leadTime - leadPoints[x][y].updateTime;
                                 //   System.out.println("> draw3DDisparityPoints diff "+tt);
                                    
                                    //   System.out.println("draw3DDisparityPoints shadowCube "+f);
                                    
                                 
                                    if(clearSpaceMode){
                                        if(isInSearchSpace(x0,y,z0,zDirection)){
                                            if(searchSpaceMode){
                                                int x0sp = xFromSearchSpace(x0,y,z0,zDirection);
                                                int ysp = yFromSearchSpace(x0,y,z0,zDirection);
                                                int z0sp = zFromSearchSpace(x0,y,z0,zDirection);
                                                
                                                x0 = x0sp;
                                                y1 = ysp;
                                                z0 = z0sp;
                                                
                                            }
                                            shadowCube(gl, x0, y1, z0, cube_size, fz+b+db, fy*+b+db, fx+b, alpha, shadowFactor);
                                            
                                        }
                                        
                                    } else {
                                        
                                        if(searchSpaceMode){
                                            int x0sp = xFromSearchSpace(x0,y,z0,zDirection);
                                            int ysp = yFromSearchSpace(x0,y,z0,zDirection);
                                            int z0sp = zFromSearchSpace(x0,y,z0,zDirection);
                                            
                                            x0 = x0sp;
                                            y1 = ysp;
                                            z0 = z0sp;
                                            
                                        }
                                    shadowCube(gl, x0, y1, z0, cube_size, fz+b+db, fy*+b+db, fx+b, alpha, shadowFactor);
                                    
                                    }
                                   
                                    
                                }
                            }
                            
                        } else if (dx==DELETE_LINK){ // if just removed
                            
                            
                         
                           if(method==LEFT_MOST_METHOD){
                              leadPoints[x][y].disparityLink = NO_LINK;
                           } else {
                              leadPoints[x][y].disparityLink2 = NO_LINK;
                           }
                 
                        }
                    }
                        
                    } // end if go
                }
                
            }
            
            
        private void draw3DAverageDisparityPoints( GL gl, EventPoint leadPoints[][], EventPoint leadPoints2[][], int leadTime, EventPoint slavePoints[][], EventPoint slavePoints2[][], int slaveTime, int zDirection ){
                      
                int z = 0;
                float fx = 0;
                float fy = 0;
                float fz = 0;
                float dl = 0;
                float dr = 0;
                int dx = -1;
                int dx1 = -1;
                int dx2 = -1;
                int dx3 = -1;
                int dx4 = -1;
                int z0 = 0;
                int x0 = 0;
                
                //    System.out.println("draw3DDisparityPoints");
                
                int half = retinaSize/2;
                
                for(int x=0;x<leadPoints.length;x++){
                    for(int y=0;y<leadPoints[x].length;y++){
                        if(leadPoints[x][y]==null){
                            //  System.out.println("leftpoints["+x+"]["+y+"] null");
                            break;
                        }
                            dx = NO_LINK;                                                                                            
                            dx1 = leadPoints[x][y].disparityLink;
                            dx2 = leadPoints[x][y].attachedTo;
                          //  dx3 = leadPoints2[x][y].disparityLink;
                        //    dx4 = leadPoints2[x][y].attachedTo;
                            
                             if(dx1== DELETE_LINK) { 
                               dx = DELETE_LINK;
                            } else if(dx1==NO_LINK) { 
                                dx = NO_LINK;
                            } else {
                            
//                            if(dx1==-2||dx3==-2) { 
//                                dx = -2;
//                            } else if(dx1==-1||dx3==-1) { 
//                                dx =-1;
//                            } else {
//                              
                               
//                                dx = dx1+dx3;
//                                int c = 2;
//                                if(dx2>0){
//                                    dx += dx2;
//                                    c++;
//                                }
//                                if(dx4>0){
//                                    dx += dx4;
//                                    c++;
//                                } 
//                                dx = dx/c;
                              
                          
                                int z1 = 0;
                                int z2 = 0;
                               // int z3 = 0;
                              //  int z4 = 0;
                                if(dx1>NO_LINK){
                                    z1 = (dx1 - x);
                                    z1 = Math.round((float) (
                                            (Math.cos(Math.toRadians(planeAngle))*(z1)) ));
                                }
                                if(dx2>NO_LINK){
                                    z2 = (dx2 - x);
                                    z2 = Math.round((float) (
                                            (Math.cos(Math.toRadians(planeAngle))*(z2)) ));
                                }
//                                if(dx3>-1){
//                                    z3 = (dx3 - x);
//                                    z3 = Math.round((float) (
//                                            (Math.cos(Math.toRadians(planeAngle))*(z3)) ));
//                                }
//                                if(dx4>-1){
//                                    z4 = (dx4 - x);
//                                    z4 = Math.round((float) (
//                                            (Math.cos(Math.toRadians(planeAngle))*(z4)) ));
//                                }
                                
                                if(z1<z2+1&&z1>z2-1){
                                 //   dx = (dx1+dx2)/2;
                                    if(dx1>NO_LINK&&dx2>NO_LINK){
                                        dx = dx1;
                                       // System.out.println("x:"+x+" dx:"+dx+" dx1:"+dx1+" z1:"+z1+" dx2:"+dx2+" z2:"+z2);
                                    } else dx = NO_LINK;
                                    
//                                } else if(z1<z3+3&&z1>z3-3){
//                                    dx = (dx1+dx3)/2;
//                                    
//                                } else if(z1<z4+3&&z1>z4-3){
//                                    dx =(dx1+dx4)/2;
//                                    
//                                } else if(z2<z3+3&&z2>z3-3){
//                                    dx = (dx2+dx3)/2;
//                                    
//                                } else if(z2<z4+3&&z2>z4-3){
//                                    dx = (dx2+dx4)/2;
//                                    
//                                } else if(z3<z4+3&&z3>z4-3){
//                                    if(dx3>-1){
//                                        dx = (dx3+dx4)/2;
//                                  } else {
//                                    dx = -1;
//                                  }
                                    
                                } else {
                                    dx = NO_LINK;
                                }
                            }
                        
                            
                            // from large
                            if(useLarge){
                                if(cutMode){
                                    if(dx>NO_LINK){
                                        if(leadPoints[x][y].getShortFilteredValue(leadTime)<valueThreshold){
                                            dx = DELETE_LINK;
                                        } else {
                                            if(slavePoints[dx1][y].getShortFilteredValue(leadTime)<valueThreshold){
                                                dx = DELETE_LINK;
                                            }
                                        }
                                    }
                                }
                            }
                            
                        if (dx>NO_LINK){
                            
                            z = (dx - x); 
                            
                            x0 = Math.round((float) ( -
                                    (Math.sin(Math.toRadians(planeAngle))*(z)) ))+x;
                            z0 = Math.round((float) (
                                    (Math.cos(Math.toRadians(planeAngle))*(z)) ));

                            z0 =  z0*zDirection;
                            
                            
                            
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
                                    
                                    dl = leadPoints[x][y].getValue(leadTime); // or getAccValue()?
                                    
                                    if(dx1>NO_LINK)
                                        dr = slavePoints[dx1][y].getValue(slaveTime);
                                    else dr = 0;

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
                                        fz = colorizeFactor*((retinaSize-z)%colorizePeriod);
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
                            
                        } else if (dx==DELETE_LINK){ // if just removed
                            leadPoints[x][y].disparityLink = NO_LINK;
                            if(leadPoints[x][y].prevDisparityLink>NO_LINK){
                                
                                z = (leadPoints[x][y].prevDisparityLink - x);// * -zFactor;
             
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
          
                // rotate point x=0. z=retinaSize if angle = 0. translation 0
                int xf = Math.round((float) ( -
                        (Math.sin(Math.toRadians(planeAngle))*(retinaSize)) ));
                int zf = Math.round((float) (
                        (Math.cos(Math.toRadians(planeAngle))*(retinaSize)) ));
                
                // rotate point x=retinaSize-retinaSize z=retinaSize if angle = 0. translation retinaSize
                int xfb = Math.round((float) ( - 
                        (Math.sin(Math.toRadians(planeAngle))*(-retinaSize)) ))+retinaSize;
                int zfb = Math.round((float) ( 
                        (Math.cos(Math.toRadians(planeAngle))*(-retinaSize)) ));
                
                // obtain orthogonal direction to 0-x2
                middleAngle = orientation(half,0,xf,zf)+90;
                      
            //    middleAngle = 180 - (middleAngle-180);
               
             //  System.out.println("middleAngle for planeAngle("+planeAngle+")= "+middleAngle);
        
            
                                
                // blue frame
                
                if(showCage){                                                                                                    
                    gl.glColor3f(0.0f,0.0f,1.0f);	// blue color
                                                         
                    //compute all x,y,z for all points
                                                        
                    int x1 = 0;
                    int x2 = retinaSize;
                    int x3 = 0;
                    int x4 = retinaSize;
                    int x5 = door_xa;
                    int x6 = door_xb;
                    int x7 = door_xa;
                    int x8 = door_xb;
                    int x9 = 0;
                    int x10 = retinaSize;
                    int x11 = 0;
                    int x12 = retinaSize;
                    int y1 = 0;
                    int y2 = 0;
                    int y3 = retinaSize;
                    int y4 = retinaSize;
                    int y5 = door_ya;
                    int y6 = door_ya;
                    int y7 = retinaSize;
                    int y8 = retinaSize;
                    int y9 = door_ya;
                    int y10 = door_ya;
                    int y11 = door_ya;
                    int y12 = door_ya;
                    int z1 = door_z;
                    int z2 = door_z;
                    int z3 = door_z;
                    int z4 = door_z;
                    int z5 = door_z;
                    int z6 = door_z;
                    int z7 = door_z;
                    int z8 = door_z;
                    int z9 = door_z;
                    int z10 = door_z;
                    int z11 = door_z-cage_depth;
                    int z12 = door_z-cage_depth;
                    
                    // 1) tilt, rotate on x
                    int y1_rx = rotateYonX( y1, z1, 0, 0,viewAngle);
                    int z1_rx = rotateZonX( y1, z1, 0, 0,viewAngle);
                    int y2_rx = rotateYonX( y2, z2, 0, 0,viewAngle);
                    int z2_rx = rotateZonX( y2, z2, 0, 0,viewAngle);
                    int y3_rx = rotateYonX( y3, z3, 0, 0,viewAngle);
                    int z3_rx = rotateZonX( y3, z3, 0, 0,viewAngle);
                    int y4_rx = rotateYonX( y4, z4, 0, 0,viewAngle);
                    int z4_rx = rotateZonX( y4, z4, 0, 0,viewAngle);
                    int y5_rx = rotateYonX( y5, z5, 0, 0, viewAngle);
                    int z5_rx = rotateZonX( y5, z5, 0, 0, viewAngle);
                    int y6_rx = rotateYonX( y6, z6, 0, 0, viewAngle);
                    int z6_rx = rotateZonX( y6, z6, 0, 0, viewAngle);
                    int y7_rx = rotateYonX( y7, z7, 0, 0, viewAngle);
                    int z7_rx = rotateZonX( y7, z7, 0, 0, viewAngle);
                    int y8_rx = rotateYonX( y8, z8, 0, 0, viewAngle);
                    int z8_rx = rotateZonX( y8, z8, 0, 0, viewAngle);
                    int y9_rx = rotateYonX( y9, z9, 0, 0, viewAngle);
                    int z9_rx = rotateZonX( y9, z9, 0, 0, viewAngle);
                    int y10_rx = rotateYonX( y10, z10, 0, 0, viewAngle);
                    int z10_rx = rotateZonX( y10, z10, 0, 0, viewAngle);
                    
                    int y11_rxp = rotateYonX( y11, z11, y9, z9, platformAngle);
                    int z11_rxp = rotateZonX( y11, z11, y9, z9, platformAngle);
                    int y12_rxp = rotateYonX( y12, z12, y10, z10, platformAngle);
                    int z12_rxp = rotateZonX( y12, z12, y10, z10, platformAngle);   
                      
                    
                    int y11_rx = rotateYonX( y11_rxp, z11_rxp, 0, 0, viewAngle);
                    int z11_rx = rotateZonX( y11_rxp, z11_rxp, 0, 0, viewAngle);
                    int y12_rx = rotateYonX( y12_rxp, z12_rxp, 0, 0, viewAngle);
                    int z12_rx = rotateZonX( y12_rxp, z12_rxp, 0, 0, viewAngle);
                                       
                    // 2) rotate on y                                 
                           
                    if(!searchSpaceMode){
                    
                        
                        
                    int x1_ry = rotateXonY( x1, z1_rx, half, 0, middleAngle);
                    int z1_ry = rotateZonY( x1, z1_rx, half, 0, middleAngle);
                    int x2_ry = rotateXonY( x2, z2_rx, half, 0, middleAngle);
                    int z2_ry = rotateZonY( x2, z2_rx, half, 0, middleAngle);
                    int x3_ry = rotateXonY( x3, z3_rx, half, 0, middleAngle);
                    int z3_ry = rotateZonY( x3, z3_rx, half, 0, middleAngle);
                    int x4_ry = rotateXonY( x4, z4_rx, half, 0, middleAngle);
                    int z4_ry = rotateZonY( x4, z4_rx, half, 0, middleAngle);
                    int x5_ry = rotateXonY( x5, z5_rx, half, 0, middleAngle);
                    int z5_ry = rotateZonY( x5, z5_rx, half, 0, middleAngle);
                    int x6_ry = rotateXonY( x6, z6_rx, half, 0, middleAngle);
                    int z6_ry = rotateZonY( x6, z6_rx, half, 0, middleAngle);
                    int x7_ry = rotateXonY( x7, z7_rx, half, 0, middleAngle);
                    int z7_ry = rotateZonY( x7, z7_rx, half, 0, middleAngle);
                    int x8_ry = rotateXonY( x8, z8_rx, half, 0, middleAngle);
                    int z8_ry = rotateZonY( x8, z8_rx, half, 0, middleAngle);
                    int x9_ry = rotateXonY( x9, z9_rx, half, 0, middleAngle);
                    int z9_ry = rotateZonY( x9, z9_rx, half, 0, middleAngle);
                    int x10_ry = rotateXonY( x10, z10_rx, half, 0, middleAngle);
                    int z10_ry = rotateZonY( x10, z10_rx, half, 0, middleAngle);
                    int x11_ry = rotateXonY( x11, z11_rx, half, 0, middleAngle);
                    int z11_ry = rotateZonY( x11, z11_rx, half, 0, middleAngle);
                    int x12_ry = rotateXonY( x12, z12_rx, half, 0, middleAngle);
                    int z12_ry = rotateZonY( x12, z12_rx, half, 0, middleAngle);
                    
           
                    line3D( gl,  x1_ry,  y1_rx,  z1_ry,   x2_ry,  y2_rx,  z2_ry);
              
                    line3D( gl,  x1_ry,  y1_rx,  z1_ry,  x3_ry,  y3_rx,  z3_ry);
                    line3D( gl,  x2_ry,  y2_rx,  z2_ry,  x4_ry,  y4_rx,  z4_ry);
                      
                    line3D( gl,  x3_ry,  y3_rx,  z3_ry,  x4_ry,  y4_rx,  z4_ry);
            
                    // cage
                    
                    
                     
                    line3D( gl,  x5_ry,  y5_rx,  z5_ry,  x6_ry,  y6_rx,  z6_ry);
                    line3D( gl,  x5_ry,  y5_rx,  z5_ry,  x7_ry,  y7_rx,  z7_ry);
                    line3D( gl,  x6_ry,  y6_rx,  z6_ry,  x8_ry,  y8_rx,  z8_ry);
                   
           
   
                    line3D( gl,  x9_ry,  y9_rx,  z9_ry,  x10_ry,  y10_rx,  z10_ry);
                    line3D( gl,  x9_ry,  y9_rx,  z9_ry,  x11_ry,  y11_rx,  z11_ry);
                    line3D( gl,  x10_ry,  y10_rx,  z10_ry,  x12_ry,  y12_rx,  z12_ry);
                    line3D( gl,  x11_ry,  y11_rx,  z11_ry,  x12_ry,  y12_rx,  z12_ry);
                       
                    
                     
                     } else {
                        
                      y11_rx = rotateYonX( y11, z11, y9, z9, platformAngle);
                      z11_rx = rotateZonX( y11, z11, y9, z9, platformAngle);
                      y12_rx = rotateYonX( y12, z12, y10, z10, platformAngle);
                      z12_rx = rotateZonX( y12, z12, y10, z10, platformAngle);   
                        
                     int x1_ry = rotateXonY( x1, z1, half, 0, middleAngle);
                     int z1_ry = rotateZonY( x1, z1, half, 0, middleAngle);
                     int x2_ry = rotateXonY( x2, z2, half, 0, middleAngle);
                     int z2_ry = rotateZonY( x2, z2, half, 0, middleAngle);
                     int x3_ry = rotateXonY( x3, z3, half, 0, middleAngle);
                     int z3_ry = rotateZonY( x3, z3, half, 0, middleAngle);
                     int x4_ry = rotateXonY( x4, z4, half, 0, middleAngle);
                     int z4_ry = rotateZonY( x4, z4, half, 0, middleAngle);
                     int x5_ry = rotateXonY( x5, z5, half, 0, middleAngle);
                     int z5_ry = rotateZonY( x5, z5, half, 0, middleAngle);
                     int x6_ry = rotateXonY( x6, z6, half, 0, middleAngle);
                     int z6_ry = rotateZonY( x6, z6, half, 0, middleAngle);
                     int x7_ry = rotateXonY( x7, z7, half, 0, middleAngle);
                     int z7_ry = rotateZonY( x7, z7, half, 0, middleAngle);
                     int x8_ry = rotateXonY( x8, z8, half, 0, middleAngle);
                     int z8_ry = rotateZonY( x8, z8, half, 0, middleAngle);
                     int x9_ry = rotateXonY( x9, z9, half, 0, middleAngle);
                     int z9_ry = rotateZonY( x9, z9, half, 0, middleAngle);
                     int x10_ry = rotateXonY( x10, z10, half, 0, middleAngle);
                     int z10_ry = rotateZonY( x10, z10, half, 0, middleAngle);
                     int x11_ry = rotateXonY( x11, z11_rx, half, 0, middleAngle);
                     int z11_ry = rotateZonY( x11, z11_rx, half, 0, middleAngle);
                     int x12_ry = rotateXonY( x12, z12_rx, half, 0, middleAngle);
                     int z12_ry = rotateZonY( x12, z12_rx, half, 0, middleAngle);
                     
                     gl.glColor3f(0.0f,0.0f,1.0f);
           
                    line3D( gl,  x1_ry,  y1,  z1_ry,   x2_ry,  y2,  z2_ry);
              
                    line3D( gl,  x1_ry,  y1,  z1_ry,  x3_ry,  y3,  z3_ry);
                    line3D( gl,  x2_ry,  y2,  z2_ry,  x4_ry,  y4,  z4_ry);
                      
                    line3D( gl,  x3_ry,  y3,  z3_ry,  x4_ry,  y4,  z4_ry);
            
                    // cage
                    
                    
                     
                    line3D( gl,  x5_ry,  y5,  z5_ry,  x6_ry,  y6,  z6_ry);
                    line3D( gl,  x5_ry,  y5,  z5_ry,  x7_ry,  y7,  z7_ry);
                    line3D( gl,  x6_ry,  y6,  z6_ry,  x8_ry,  y8,  z8_ry);
                   
           
   
                    line3D( gl,  x9_ry,  y9,  z9_ry,  x10_ry,  y10,  z10_ry);
                    line3D( gl,  x9_ry,  y9,  z9_ry,  x11_ry,  y11_rx,  z11_ry);
                    line3D( gl,  x10_ry,  y10,  z10_ry,  x12_ry,  y12_rx,  z12_ry);
                    line3D( gl,  x11_ry,  y11_rx,  z11_ry,  x12_ry,  y12_rx,  z12_ry);
                       
                     } 
                    
                }
                
                gl.glFlush();
                
                if(showFrame){
                    // losange area
                    
                    gl.glColor3f(1.0f,0.0f,1.0f);
                    line3D( gl,  0,  0,  0,  xf ,0 ,zf);
                    line3D( gl,  0,  retinaSize,  0,  xf ,retinaSize ,zf);
                    line3D( gl,  xf,  0,  zf,  xf ,retinaSize , zf);
                    line3D( gl,  xf,  0,  zf,  retinaSize ,0 , 0);
                    line3D( gl,  xf,  retinaSize,  zf,  retinaSize ,retinaSize , 0);
                    
                    gl.glColor3f(1.0f,0.5f,1.0f);
                    line3D( gl,  0,  0,  0,  xfb ,0 ,zfb);
                    line3D( gl,  0,  retinaSize,  0,  xfb ,retinaSize ,zfb);
                    line3D( gl,  xfb,  0,  zfb,  xfb ,retinaSize , zfb);
                    line3D( gl,  xfb,  0,  zfb,  retinaSize ,0 , 0);
                    line3D( gl,  xfb,  retinaSize,  zfb,  retinaSize ,retinaSize , 0);

                
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
            //    gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
                gl.glBegin(gl.GL_LINES);
                //   gl.glColor3f(0.0f,1.0f,0.0f);	// Color Blue
                gl.glVertex3f( x-size, y-size,z-size);	
                gl.glVertex3f(x-size, y+size,z-size);	
                gl.glVertex3f(x-size, y-size, z-size);	
                gl.glVertex3f( x+size, y-size, z-size);
                //   gl.glColor3f(1.0f,0.5f,0.0f);	// Color Orange
                gl.glVertex3f( x-size,y+size, z-size);	
                gl.glVertex3f(x+size,y+size, z-size);	
                gl.glVertex3f(x+size,y+size,z-size);	
                gl.glVertex3f( x+size,y-size,z-size);	
                //  gl.glColor3f(1.0f,0.0f,0.0f);	// Color Red
                gl.glVertex3f( x-size, y-size, z-size);	
                gl.glVertex3f(x-size, y-size, z+size);	
                gl.glVertex3f(x-size,y+size, z-size);	
                gl.glVertex3f( x-size,y+size, z+size);	
                //  gl.glColor3f(1.0f,1.0f,0.0f);	// Color Yellow
                gl.glVertex3f( x+size,y+size,z-size);	
                gl.glVertex3f(x+size,y+size,z+size);	
                gl.glVertex3f(x+size, y-size,z-size);	
                gl.glVertex3f( x+size, y-size,z+size);	
                //  gl.glColor3f(0.0f,0.0f,1.0f);	// Color Blue
                gl.glVertex3f(x-size, y-size, z+size);	
                gl.glVertex3f(x-size, y+size,z+size);	
                gl.glVertex3f(x-size,y-size,z+size);	
                gl.glVertex3f(x+size,y-size, z+size);	
                //  gl.glColor3f(1.0f,0.0f,1.0f);	// Color Violet
                gl.glVertex3f( x-size, y+size,z+size);	
                gl.glVertex3f( x+size, y+size, z+size);	
                gl.glVertex3f( x+size,y-size, z+size);	
                gl.glVertex3f( x+size,y+size,z+size);	
                gl.glEnd();
                
                // rotation += 0.9f;
                
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
                
              
         //       System.out.println("main draw3DDisparityPoints at "+currentTime);
                                
                if(showAcc){
                    switch(display3DChoice){
                        case 0:
                          //    System.out.println("main draw3DDisparityPoints leftPoints at "+leftTime);
                            draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                          //  System.out.println("main draw3DDisparityPoints leftPoints2 at "+leftTime);
                            draw3DDisparityPoints( gl , leftPoints, RIGHT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                         //   System.out.println("main draw3DDisparityPoints rightPoints at "+rightTime);
                            draw3DDisparityPoints( gl , rightPoints, LEFT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                         //   System.out.println("main draw3DDisparityPoints rightPoints2 at "+rightTime);
                            draw3DDisparityPoints( gl , rightPoints, RIGHT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            break;
                        case 1:
                            draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            draw3DDisparityPoints( gl , leftPoints, RIGHT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            break;
                        case 2:
                            draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            break;
                        case 3:
                            draw3DDisparityPoints( gl , leftPoints, RIGHT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            break;
                        case 4:
                            draw3DDisparityPoints( gl , rightPoints, LEFT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            draw3DDisparityPoints( gl , rightPoints, RIGHT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            break;
                        case 5:
                            draw3DDisparityPoints( gl , rightPoints, LEFT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            
                            break;
                        case 6:
                            
                            draw3DDisparityPoints( gl , rightPoints, RIGHT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            break;
                        case 7:
                          //  draw3DAverageDisparityPoints( gl, leftPoints, LEFT_MOST_METHOD, leftPoints2, currentTime, rightPoints, rightPoints2, currentTime, 1);
                          //  draw3DAverageDisparityPoints( gl, leftPoints2, leftPoints2, currentTime, rightPoints2, rightPoints2, currentTime, 1);
                            break;
                            
                        case 8:
                            
                            draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            draw3DDisparityPoints( gl , rightPoints, RIGHT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);
                            break;
                        case 9:
                            draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
                            draw3DDisparityPoints( gl , rightPoints, RIGHT_MOST_METHOD, currentTime, leftPoints, currentTime, -1);                                                    
                            break;
                        
                        default:
                    }
                    
                    
     
                    
                }
                
                 if(showFingers){
                    float colorFinger = 0;
                    float colorFinger2 = 0;
                    for(FingerCluster fc:fingers){
                        colorFinger+=0.1;
                        if(colorFinger>1){
                            colorFinger=1;
                            colorFinger2+=0.1;
                        }
                        if(fc!=null){
                            if(fc.activated){
                                gl.glColor3f(1-colorFinger,colorFinger,colorFinger2);
                                cube(gl,fc.x,fc.y,fc.z,finger_surround/2);
                                
                               
                                        
                                
                            }
                        }
                    }
                }
                
                gl.glFlush();
                
                
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
    
   
    
    
    
    
    
    /***********************************************************************************
    // drawing on player window
    ********************************************************************************/
       
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
            log.warning("null GL in PawTrackerStereoBoard.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            
 
            
         
      
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

    
    public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;

        getPrefs().putInt("PawTrackerStereoBoard.decayTimeLimit",decayTimeLimit);
    }
    public int getDecayTimeLimit() {
        return decayTimeLimit;
    }
     
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
    
        getPrefs().putInt("PawTrackerStereoBoard.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
   
    
    public void setDoor_z(int door_z) {
        this.door_z = door_z;
   
        getPrefs().putInt("PawTrackerStereoBoard.door_z",door_z);
    }
    
    public int getDoor_z() {
        return door_z;
    }
    
    public void setDoor_xa(int door_xa) {
        this.door_xa = door_xa;
       
        getPrefs().putInt("PawTrackerStereoBoard.door_xa",door_xa);
    }
    
    public int getDoor_xa() {
        return door_xa;
    }
    
    public void setDoor_xb(int door_xb) {
        this.door_xb = door_xb;
    
        getPrefs().putInt("PawTrackerStereoBoard.door_xb",door_xb);
    }
    
    public int getDoor_xb() {
        return door_xb;
    }
    
    public void setDoor_ya(int door_ya) {
        this.door_ya = door_ya;
        
        getPrefs().putInt("PawTrackerStereoBoard.door_ya",door_ya);
    }
    
    public int getDoor_ya() {
        return door_ya;
    }
    
    public void setDoor_yb(int door_yb) {
        this.door_yb = door_yb;
     
        getPrefs().putInt("PawTrackerStereoBoard.door_yb",door_yb);
    }
    
    public int getDoor_yb() {
        return door_yb;
    }
    
    
  
    
    public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
 
        getPrefs().putFloat("PawTrackerStereoBoard.event_strength",event_strength);
    }
    public float getEvent_strength() {
        return event_strength;
    }
  
  
   
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
       
        getPrefs().putBoolean("PawTrackerStereoBoard.resetPawTracking",resetPawTracking);
        
    }
    
   public boolean isRestart() {
        return restart;
    }
    public void setRestart(boolean restart) {
        this.restart = restart;
 
        getPrefs().putBoolean("PawTrackerStereoBoard.restart",restart);
        
    }
    
    public boolean isValidateParameters() {
        return validateParameters;
    }
    public void setValidateParameters(boolean validateParameters) {
        this.validateParameters = validateParameters;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.validateParameters",validateParameters);
        
    }
    
      public void setShowCorrectionGradient(boolean showCorrectionGradient){
        this.showCorrectionGradient = showCorrectionGradient;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showCorrectionGradient",showCorrectionGradient);
    }
    public boolean getshowCorrectionGradient(){
        return showCorrectionGradient;
    }
    public void setShowCorrectionMatrix(boolean showCorrectionMatrix){
        this.showCorrectionMatrix = showCorrectionMatrix;
   
        getPrefs().putBoolean("PawTrackerStereoBoard.showCorrectionMatrix",showCorrectionMatrix);
    }
    public boolean getShowCorrectionMatrix(){
        return showCorrectionMatrix;
    }
    
    
  
    
    public void setShowSecondFilter(boolean showSecondFilter){
        this.showSecondFilter = showSecondFilter;
    
        getPrefs().putBoolean("PawTrackerStereoBoard.showSecondFilter",showSecondFilter);
    }
    public boolean isShowSecondFilter(){
        return showSecondFilter;
    }
    
  
    
    public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    
    
    public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showAcc",showAcc);
    }
    public boolean isShowAcc(){
        return showAcc;
    }
    
    public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.showOnlyAcc",showOnlyAcc);
    }
    public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
    public void setShowDecay(boolean showDecay){
        this.showDecay = showDecay;
   
        getPrefs().putBoolean("PawTrackerStereoBoard.showDecay",showDecay);
    }
    public boolean isShowDecay(){
        return showDecay;
    }
    
    
      public void setUseDualFilter(boolean useDualFilter){
        this.useDualFilter = useDualFilter;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.useDualFilter",useDualFilter);
    }
    public boolean isUseDualFilter(){
        return useDualFilter;
    }
    
    public void setUseLarge(boolean useLarge){
        this.useLarge = useLarge;
   
        getPrefs().putBoolean("PawTrackerStereoBoard.useLarge",useLarge);
    }
    public boolean isUseLarge(){
        return useLarge;
    }

    
    public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.decayOn",decayOn);
    }
    public boolean isDecayOn(){
        return decayOn;
    }
    
    
    public void setShowFrame(boolean showFrame){
        this.showFrame = showFrame;
    
        getPrefs().putBoolean("PawTrackerStereoBoard.showFrame",showFrame);
    }
    public boolean isShowFrame(){
        return showFrame;
    }
    
    public void setShowCage(boolean showCage){
        this.showCage = showCage;
       getPrefs().putBoolean("PawTrackerStereoBoard.showCage",showCage);
    }
    public boolean isShowCage(){
        return showCage;
    }
    
    
    public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
   
        getPrefs().putBoolean("PawTrackerStereoBoard.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
    
    
    public void setShowScore(boolean showScore){
        this.showScore = showScore;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.showScore",showScore);
    }
    public boolean isShowScore(){
        return showScore;
    }
    
     public void setShowRight(boolean showRight){
        this.showRight = showRight;

        getPrefs().putBoolean("PawTrackerStereoBoard.showRight",showRight);
    }
    public boolean isShowRight(){
        return showRight;
    }
    
    
    
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
    
    
    public void setShowFingerTips(boolean showFingerTips){
        this.showFingerTips = showFingerTips;
        
        getPrefs().putBoolean("PawTrackerStereoBoard.showFingerTips",showFingerTips);
    }
    public boolean isShowFingerTips(){
        return showFingerTips;
    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
       
        getPrefs().putBoolean("PawTrackerStereoBoard.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
   
        getPrefs().putBoolean("PawTrackerStereoBoard.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
    public void setTrack(boolean track){
        this.track = track;
        
        getPrefs().putBoolean("PawTrackerStereoBoard.track",track);
    }
    public boolean isTrack(){
        return track;
    }
    
    public void setShowRLColors(boolean showRLColors){
        this.showRLColors = showRLColors;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showRLColors",showRLColors);
    }
    public boolean isShowRLColors(){
        return showRLColors;
    }
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
   
    
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
      
        getPrefs().putInt("PawTrackerStereoBoard.lowFilter_radius",lowFilter_radius);
    }
    
    public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
      
        getPrefs().putInt("PawTrackerStereoBoard.lowFilter_density",lowFilter_density);
    }
    
    public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
     
        getPrefs().putFloat("PawTrackerStereoBoard.lowFilter_threshold",lowFilter_threshold);
    }
    
    public int getLowFilter_radius2() {
        return lowFilter_radius2;
    }
    
    public void setLowFilter_radius2(int lowFilter_radius2) {
        this.lowFilter_radius2 = lowFilter_radius2;
    
        getPrefs().putInt("PawTrackerStereoBoard.lowFilter_radius2",lowFilter_radius2);
    }
    
    public int getLowFilter_density2() {
        return lowFilter_density2;
    }
    
    public void setLowFilter_density2(int lowFilter_density2) {
        this.lowFilter_density2 = lowFilter_density2;
     
        getPrefs().putInt("PawTrackerStereoBoard.lowFilter_density2",lowFilter_density2);
    }
                
    
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
      
        getPrefs().putFloat("PawTrackerStereoBoard.brightness",brightness);
    }
    
    
  
    
    
    public float getPlaneAngle() {
        return planeAngle;
    }
    public void setPlaneAngle(float planeAngle) {
        this.planeAngle = planeAngle;
      
        getPrefs().putFloat("PawTrackerStereoBoard.planeAngle",planeAngle);
    }
    public float getViewAngle() {
        return viewAngle;
    }
    public void setViewAngle(float viewAngle) {
        this.viewAngle = viewAngle;
        
        getPrefs().putFloat("PawTrackerStereoBoard.viewAngle",viewAngle);
    }
    
    
    public float getPlatformAngle() {
        return platformAngle;
    }
    public void setPlatformAngle(float platformAngle) {
        this.platformAngle = platformAngle;
        
        getPrefs().putFloat("PawTrackerStereoBoard.platformAngle",platformAngle);
    }
    

    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
   
        getPrefs().putFloat("PawTrackerStereoBoard.alpha",alpha);
    }
    public float getAlpha() {
        return alpha;
    }
    public void setIntensity(float intensity) {
        this.intensity = intensity;
       
        getPrefs().putFloat("PawTrackerStereoBoard.intensity",intensity);
    }
    public float getIntensity() {
        return intensity;
    }
    
   
    
    public void setDispAvgRange(int dispAvgRange) {
        this.dispAvgRange = dispAvgRange;
   
        getPrefs().putInt("PawTrackerStereoBoard.dispAvgRange",dispAvgRange);
    }
    public int getDispAvgRange() {
        return dispAvgRange;
    }
    
    
    
    
    public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
     
        getPrefs().putFloat("PawTrackerStereoBoard.valueThreshold",valueThreshold);
    }
    public float getValueThreshold() {
        return valueThreshold;
    }
    
    
   
    
    
    public void setCage_depth(int cage_depth) {
        this.cage_depth = cage_depth;
       
        getPrefs().putInt("PawTrackerStereoBoard.cage_depth",cage_depth);
    }
    public int getCage_depth() {
        return cage_depth;
    }
    
    public void setYLeftCorrection(int yLeftCorrection) {
        this.yLeftCorrection = yLeftCorrection;
   
        getPrefs().putInt("PawTrackerStereoBoard.yLeftCorrection",yLeftCorrection);
    }
    public int getYLeftCorrection() {
        return yLeftCorrection;
    }
    public void setYRightCorrection(int yRightCorrection) {
        this.yRightCorrection = yRightCorrection;
      
        getPrefs().putInt("PawTrackerStereoBoard.yRightCorrection",yRightCorrection);
    }
    public int getYRightCorrection() {
        return yRightCorrection;
    }
    
    public void setYCurveFactor(float yCurveFactor) {
        this.yCurveFactor = yCurveFactor;
   
        getPrefs().putFloat("PawTrackerStereoBoard.yCurveFactor",yCurveFactor);
    }
    public float getYCurveFactor() {
        return yCurveFactor;
    }
    
    
    
    
    public void setColorizeFactor(float colorizeFactor) {
        this.colorizeFactor = colorizeFactor;
    
        getPrefs().putFloat("PawTrackerStereoBoard.colorizeFactor",colorizeFactor);
    }
    public float getColorizeFactor() {
        return colorizeFactor;
    }
    
    public void setShadowFactor(float shadowFactor) {
        this.shadowFactor = shadowFactor;
     
        getPrefs().putFloat("PawTrackerStereoBoard.shadowFactor",shadowFactor);
    }
    public float getShadowFactor() {
        return shadowFactor;
    }
    
    public void setZFactor(int zFactor) {
        this.zFactor = zFactor;
  
        getPrefs().putInt("PawTrackerStereoBoard.zFactor",zFactor);
    }
    public int getZFactor() {
        return zFactor;
    }
    
    public void setValueMargin(float valueMargin) {
        this.valueMargin = valueMargin;
      
        getPrefs().putFloat("PawTrackerStereoBoard.valueMargin",valueMargin);
    }
    public float getValueMargin() {
        return valueMargin;
    }
    
    public void setCorrectLeftAngle(float correctLeftAngle) {
        this.correctLeftAngle = correctLeftAngle;
    
        getPrefs().putFloat("PawTrackerStereoBoard.correctLeftAngle",correctLeftAngle);
    }
    public float getCorrectLeftAngle() {
        return correctLeftAngle;
    }
    
    public void setCorrectRightAngle(float correctRightAngle) {
        this.correctRightAngle = correctRightAngle;
        
        getPrefs().putFloat("PawTrackerStereoBoard.correctRightAngle",correctRightAngle);
    }
    public float getCorrectRightAngle() {
        return correctRightAngle;
    }
    
   
    
    
    
    public void setColorizePeriod(int colorizePeriod) {
        this.colorizePeriod = colorizePeriod;
      
        getPrefs().putInt("PawTrackerStereoBoard.colorizePeriod",colorizePeriod);
    }
    public int getColorizePeriod() {
        return colorizePeriod;
    }
    
    
   public void setHighlightDecay(boolean highlightDecay){
        this.highlightDecay = highlightDecay;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.highlightDecay",highlightDecay);
    }
    public boolean isHighlightDecay(){
        return highlightDecay;
    }
     
    
    public void setShowZColor(boolean showZColor){
        this.showZColor = showZColor;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.showZColor",showZColor);
    }
    public boolean isShowZColor(){
        return showZColor;
    }
    
    public void setShowYColor(boolean showYColor){
        this.showYColor = showYColor;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showYColor",showYColor);
    }
    public boolean isShowYColor(){
        return showYColor;
    }
    
    public void setShowXColor(boolean showXColor){
        this.showXColor = showXColor;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.showXColor",showXColor);
    }
    public boolean isShowXColor(){
        return showXColor;
    }
    
    public void setShowShadows(boolean showShadows){
        this.showShadows = showShadows;
     
        getPrefs().putBoolean("PawTrackerStereoBoard.showShadows",showShadows);
    }
    public boolean isShowShadows(){
        return showShadows;
    }
    public void setShowCorner(boolean showCorner){
        this.showCorner = showCorner;
       
        getPrefs().putBoolean("PawTrackerStereoBoard.showCorner",showCorner);
    }
    public boolean isShowCorner(){
        return showCorner;
    }
    
    public void setCorrectY(boolean correctY){
        this.correctY = correctY;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.correctY",correctY);
    }
    public boolean isCorrectY(){
        return correctY;
    }
            
    public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
       
        getPrefs().putInt("PawTrackerStereoBoard.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }
          
    public void setDisparity_range(int disparity_range) {
        this.disparity_range = disparity_range;
       
        getPrefs().putInt("PawTrackerStereoBoard.disparity_range",disparity_range);
    }
    public int getDisparity_range() {
        return disparity_range;
    }
    
    public void setNotCrossing(boolean notCrossing){
        this.notCrossing = notCrossing;
      
        getPrefs().putBoolean("PawTrackerStereoBoard.notCrossing",notCrossing);
    }
    public boolean isNotCrossing(){
        return notCrossing;
    }
    
      public float getFinger_mix() {
        return finger_mix;
    }
    
    public void setFinger_mix(float finger_mix) {
        this.finger_mix = finger_mix;
        getPrefs().putFloat("PawTrackerStereoBoard.finger_mix",finger_mix);
    }
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        getPrefs().putInt("PawTrackerStereoBoard.finger_surround",finger_surround);
    }
    
  
}
