/*
 * PawTracker3.java
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
import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.lang.Math.*;
import javax.swing.*;
import javax.media.opengl.glu.GLU;


/**
 * Tracks Rat's Paw
 *<p>
 * New angle of view.
 * Accumulate event then try to find fingers by moving finger tip sized object inside paw contour
 * </p>
 *
 * @author rogister
 */
public class PawTracker3 extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
   // private static Preferences getPrefs()=Preferences.userNodeForPackage(PawTracker3.class);
    
    
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
   
    private int door_xa=getPrefs().getInt("PawTracker3.door_xa",50);
    {setPropertyTooltip("door_xa","lower x bound of the cage door (x,y inverted)");}
    private int door_xb=getPrefs().getInt("PawTracker3.door_xb",127);
    {setPropertyTooltip("door_xb","higher x bound of the cage door (x,y inverted)");}
    private int door_ya=getPrefs().getInt("PawTracker3.door_ya",52);
    {setPropertyTooltip("door_ya","lower y bound of the cage door (x,y inverted)");}
    private int door_yb=getPrefs().getInt("PawTracker3.door_yb",88);
    {setPropertyTooltip("door_yb","higher y bound of the cage door (x,y inverted)");}
    
    private int retinaSize=getPrefs().getInt("PawTracker3.retinaSize",128);
    {setPropertyTooltip("retinaSize","[nb pixels of side] resolution of the retina");}
    private int linkSize=getPrefs().getInt("PawTracker3.linkSize",2);
    {setPropertyTooltip("linkSize","[nb pixels] Neighbourhood range for linking contours");}
    private int segSize=getPrefs().getInt("PawTracker3.segSize",2);
    {setPropertyTooltip("segSize","[nb pixels] Neighbourhood range for shape segment creation");}
    
    private int maxSegSize=getPrefs().getInt("PawTracker3.maxSegSize",4);
    {setPropertyTooltip("segSize","[nb pixels] Maximum size of a shape segment");}
    
    private int minZeroes=getPrefs().getInt("PawTracker3.minZeroes",2);
    {setPropertyTooltip("minZeroes","Minimum number of low value around point for border template matching");}
    private int maxZeroes=getPrefs().getInt("PawTracker3.maxZeroes",6);
    {setPropertyTooltip("maxZeroes","Maximum number of low value around point for border template matching");}
   // private int doorMinZeroes=getPrefs().getInt("PawTracker3.doorMinZeroes",2);
   // private int doorMaxZeroes=getPrefs().getInt("PawTracker3.doorMaxZeroes",6);
    
    private int in_length=getPrefs().getInt("PawTracker3.in_length",3);
    {setPropertyTooltip("in_length","[nb pixels] Length of inward intensity increase, perpendicular to border segment, prior to score computation");}
    private int in_test_length=getPrefs().getInt("PawTracker3.in_test_length",3);
    {setPropertyTooltip("in_test_length","[nb pixels] Length of probe perpendicular to border segment to find inside of shape");}
   
    //private float in_threshold=getPrefs().getFloat("PawTracker3.in_threshold",0.4f);
    
    private float line_threshold=getPrefs().getFloat("PawTracker3.line_threshold",0f);
    {setPropertyTooltip("line_threshold","unused, planned for finding parallel lines");}
    private int score_threshold=getPrefs().getInt("PawTracker3.score_threshold",37);
    {setPropertyTooltip("score_threshold","[intensity 0-100] threshold on intensity score for fingertips detection");}
    private float score_in_threshold=getPrefs().getFloat("PawTracker3.score_in_threshold",0.2f);
    {setPropertyTooltip("score_in_threshold","[intensity 0-100] prethreshold on intensity score for filtering out possible fingertips");}
    private float score_sup_threshold=getPrefs().getFloat("PawTracker3.score_sup_threshold",0.1f);
    {setPropertyTooltip("score_sup_threshold","[acc value] threshold on contrast value for fingertips");}  
    private float line2shape_thresh=getPrefs().getFloat("PawTracker3.line2shape_thresh",0.3f);
    {setPropertyTooltip("line2shape_thresh","[acc value] finger without fingertip stops at shape or below this value");} 
    
    private int boneSize=getPrefs().getInt("PawTracker3.boneSize",2);
    {setPropertyTooltip("boneSize","[nb pixels] max size of segment forming extracted skeletton of paw");} 
        
    private int score_range=getPrefs().getInt("PawTracker3.score_range",3);
    {setPropertyTooltip("score_range","[nb pixels] min distance between two detected fingertip");} 
    
    private int line_range=getPrefs().getInt("PawTracker3.line_range",8);
    {setPropertyTooltip("line_range","(unused) min distance between two finger lines");}
    private int lines_n_avg=getPrefs().getInt("PawTracker3.lines_n_avg",8);
    {setPropertyTooltip("lines_n_avg","nb of possible lines used for averaging most likely finger line");}
    
    
    
     
    private int cluster_lifetime=getPrefs().getInt("PawTracker3.cluster_lifetime",10);
    {setPropertyTooltip("cluster_lifetime","(unused)lifetime of cluster tracking finger");}
    
   
    private int intensityZoom = getPrefs().getInt("PawTracker3.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    private int decayLimit = getPrefs().getInt("PawTracker3.decayLimit",2);
    {setPropertyTooltip("decayLimit","[microsec (us)] for decaying accumulated image");}
    private boolean decayOn = getPrefs().getBoolean("PawTracker3.decayOn",false); 
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}       
    
               
    
    private float shapeLimit=getPrefs().getFloat("PawTracker3.shapeLimit",2f);
    {setPropertyTooltip("shapeLimit","[acc value] fingertip pattern matching: surrouding points are seen as zeroes under this value");}
    private float shapeThreshold=getPrefs().getFloat("PawTracker3.shapeThreshold",2f);
    {setPropertyTooltip("shapeThreshold","[acc value] fingertip pattern matching: center points must be above this value");}
 
    private float doorMinDiff=getPrefs().getFloat("PawTracker3.doorMinDiff",2f);
    {setPropertyTooltip("doorMinDiff","(unused) fingertip pattern matching: same as shapeLimit but inside door zone");}
    private float doorMaxDiff=getPrefs().getFloat("PawTracker3.doorMaxDiff",2f);
    {setPropertyTooltip("doorMaxDiff","(unused) fingertip pattern matching: same as shapeThreshold but inside door zone");}        
    
    private float finger_cluster_range=getPrefs().getFloat("PawTracker3.finger_cluster_range",2);
    {setPropertyTooltip("finger_cluster_range","[nb pixels] tracking range for cluster tracking fingertips");}        
    
    private float finger_ori_variance=getPrefs().getFloat("PawTracker3.finger_ori_variance",60);
    {setPropertyTooltip("finger_ori_variance","[degrees] angle variability allowance for fingerbase, from fingertip direction");}        
    
    
    private float node_range=getPrefs().getFloat("PawTracker3.node_range",2);
    {setPropertyTooltip("node_range","[nb pixels] min dist between fingertips and end node of skeletton, for detecting finger");}        
    
    
    private float palmback_below = getPrefs().getFloat("PawTracker3.palmback_below",0.3f);
    {setPropertyTooltip("palmback_below","[acc value] (to detect when not on palm) min value of points belonging to palm's backin small range low pass filter");}        
    
    private float palmback_value = getPrefs().getFloat("PawTracker3.palmback_value",0.6f);
    {setPropertyTooltip("palmback_value","[acc value] (to detect when on palm) min value of points belonging to palm's back in large range low pass filter");} 
    private int palmback_distance = getPrefs().getInt("PawTracker3.palmback_distance",30);
    {setPropertyTooltip("palmback_distance","[nb pixels] max length between finger base point and palm back");} 
  
    private int finger_start_threshold=getPrefs().getInt("PawTracker3.finger_start_threshold",45);
    {setPropertyTooltip("finger_start_threshold","[intensity 0-100] threshold for first time fingertip detection");} 
  
    
    private float finger_length=getPrefs().getFloat("PawTracker3.finger_length",10.0f);
    {setPropertyTooltip("finger_length","[nb pixels] max length of pixels");} 
  
    private float finger_mv_smooth=getPrefs().getFloat("PawTracker3.finger_mv_smooth",0.1f);
  
    private float finger_sensitivity=getPrefs().getFloat("PawTracker3.finger_sensitivity",2.0f);
         
    private float tracker_time_bin=getPrefs().getFloat("PawTracker3.tracker_time_bin",1000);
 
    private float contour_act_thresh=getPrefs().getFloat("PawTracker3.contour_act_thresh",0);
    private float contour_min_thresh=getPrefs().getFloat("PawTracker3.contour_min_thresh",0);
    private int contour_range=getPrefs().getInt("PawTracker3.contour_range",1);
   
    private int densityMinIndex=getPrefs().getInt("PawTracker3.densityMinIndex",0);
    private int densityMaxIndex=getPrefs().getInt("PawTracker3.densityMaxIndex",0);
    private boolean showDensity = getPrefs().getBoolean("PawTracker3.showDensity",false);
    
    private boolean scaleInDoor = getPrefs().getBoolean("PawTracker3.scaleInDoor",false);
       
    private boolean showSegments = getPrefs().getBoolean("PawTracker3.showSegments",true);
    private boolean showFingers = getPrefs().getBoolean("PawTracker3.showFingers",true);
    private boolean showFingerTips = getPrefs().getBoolean("PawTracker3.showFingerTips",true);
    private boolean showClusters = getPrefs().getBoolean("PawTracker3.showClusters",true);
 
  
    private boolean showZones = getPrefs().getBoolean("PawTracker3.showZones",true);
    private boolean showAll = getPrefs().getBoolean("PawTracker3.showAll",true);
    // hide activity inside door
    private boolean hideInside = getPrefs().getBoolean("PawTracker3.hideInside",false);
    // show intensity inside shape
    private boolean useIntensity = getPrefs().getBoolean("PawTracker3.useIntensity",false);
  
    private boolean showAcc = getPrefs().getBoolean("PawTracker3.showAcc",false);
    private boolean showOnlyAcc = getPrefs().getBoolean("PawTracker3.showOnlyAcc",false);
    
    private boolean scaleIntensity = getPrefs().getBoolean("PawTracker3.scaleIntensity",false);
    private boolean scaleAcc = getPrefs().getBoolean("PawTracker3.scaleAcc",true);
    
    
    private boolean showWindow = getPrefs().getBoolean("PawTracker3.showWindow",true);
    private boolean showScore = getPrefs().getBoolean("PawTracker3.showScore",false);
   
              
    private boolean useSimpleContour = getPrefs().getBoolean("PawTracker3.useSimpleContour",true);
    private boolean useFingerDistanceSmooth = getPrefs().getBoolean("PawTracker3.useFingerDistanceSmooth",true);
              
    private boolean showShapePoints = getPrefs().getBoolean("PawTracker3.showShapePoints",true);
  
    private boolean showShape = getPrefs().getBoolean("PawTracker3.showShape",true);
  
    private boolean smoothShape = getPrefs().getBoolean("PawTracker3.smoothShape",true);
    //   private boolean showKnuckles = getPrefs().getBoolean("PawTracker3.showKnuckles",true);
      
    private int lowFilter_radius=getPrefs().getInt("PawTracker3.lowFilter_radius",3);
    private int lowFilter_density=getPrefs().getInt("PawTracker3.lowFilter_density",17);
    private float lowFilter_threshold=getPrefs().getFloat("PawTracker3.lowFilter_threshold",0);   
    
    private int lowFilter_radius2=getPrefs().getInt("PawTracker3.lowFilter_radius2",10);
    private int lowFilter_density2=getPrefs().getInt("PawTracker3.lowFilter_density2",5);
   
    private boolean useDualFilter = getPrefs().getBoolean("PawTracker3.useDualFilter",false);
    private boolean useLowFilter = getPrefs().getBoolean("PawTracker3.useLowFilter",true);
  
    
    private boolean thinning = getPrefs().getBoolean("PawTracker3.thinning",true);
    private float thin_threshold=getPrefs().getFloat("PawTracker3.thin_threshold",0);
    private boolean showThin = getPrefs().getBoolean("PawTracker3.showThin",false);
    private boolean showPalm = getPrefs().getBoolean("PawTracker3.showPalm",false);   
    
    private boolean showSkeletton = getPrefs().getBoolean("PawTracker3.showSkeletton",false);
    private boolean showSecondFilter = getPrefs().getBoolean("PawTracker3.showSecondFilter",false);
    private boolean showTopography = getPrefs().getBoolean("PawTracker3.showTopography",false);
  
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTracker3.resetPawTracking",false);
    private boolean validateParameters=getPrefs().getBoolean("PawTracker3.validateParameters",false);
  
    private float event_strength=getPrefs().getFloat("PawTracker3.event_strength",2f);
    private float intensity_strength=getPrefs().getFloat("PawTracker3.intensity_strength",0.5f);
    private float intensity_threshold=getPrefs().getFloat("PawTracker3.intensity_threshold",0.5f);
    private float in_value_threshold=getPrefs().getFloat("PawTracker3.in_value_threshold",0.2f);
 
    
    private int intensity_range=getPrefs().getInt("PawTracker3.intensity_range",2);
 
    
    
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // Global variables
    
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    
     
  
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
   
   
    /** additional classes */
    /** EventPoint : all data about a point in retina space */
    private class EventPoint{
         
          float currentTime; // time of current update
          float lastUpdate; // time of last update
          
          
          float previousShortFilteredValue = 0;
          float previousValue=0;             // last contract value         
          float lastValue=0;                // last contract value
          float accValue=0;                // accumulated contrast value  
           boolean linkPawBack = false;   // when finger is attached to paw back at this point
          boolean onFingerLine = false;  // if is part of a finger line
          boolean isSkeletton = false;  // true if part of the skeletton of paw obtained by thinnning
          boolean isEndNode = false;   // true if end of segment of skeletton points
          float shortFilteredValue;   // short range topographic filter
          float largeFilteredValue;  // large range topographic filter
          boolean border;           // true if point is on the paw's border              
          Integer group;           // contour group to which this point belongs, group 1 is touching door        
          float intensity;        // value of intensity of point, projected from border point toward inside, to detect
                                 // convex borders and possible finger points          
          float fingerScore;    // likelyhood score of this point being a fingertip, obtained by template matching       
          int zerosAround;     // number of low value neighbours for border matching
          
          //Finger finger; // finger 
          int x;
          int y; 
          
          public EventPoint(  ){
              
          }
          public EventPoint( int x, int y ){
              this.x = x;
              this.y = y;
          }
      
    }
      
      
    
    
    // array of event points for all computation
    protected EventPoint eventPoints[][]; 
        
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
    protected float intensityIncrease = 0.1f;  
    protected float scoresFrameMax;
    
    private boolean activity_started = false;
    private boolean grasp_started = false;
   //protected float startPoint = 0f;
   // protected float stopPoint = 0f;
    
    
    /** Creates a new instance of PawTracker */
    public PawTracker3(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
        initFilter();
        resetPawTracker();
        validateParameterChanges();
        chip.addObserver(this);
      
        
    }
    
    public void initFilter() {
        initDefaults();
     }
    
    private void initDefaults(){
        //System.out.println("initDefaults");
      
//        initDefault("PawTracker3.","");
    }
    
    private void resetPawTracker(){
       
        grasp_started = false;
        //  activity_started = false;
     
     
        //System.out.println("resetPawTracker");
        eventPoints = new EventPoint[retinaSize][retinaSize]; 
        for (int i=0; i<eventPoints.length; i++){
            for (int j=0; j<eventPoints[i].length; j++){
                eventPoints[i][j] = new EventPoint(i,j);
            }
        }
        // reset group labels (have a vector of them or.. ?
        
   
        nbFingers = 0;
        setResetPawTracking(false);//this should also update button in panel but doesn't'
    }
    
     
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
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
            EventPoint ep = eventPoints[e.x][e.y];
            // make sure to initialize all ep before
            
            ep.previousValue = ep.lastValue;
            ep.lastValue = step * (type - grayValue);
           
            ep.accValue +=  ep.lastValue;
            
             // keep in range [0-1]
            if(ep.accValue<0)ep.accValue=0;
            else if(ep.accValue>1)ep.accValue=1;
            
            ep.currentTime = (float)e.timestamp;
            ep.x = e.x;   
            ep.y = e.y;
            processTracking(ep);                                              
            
        }//end for all incoming events
               
    }
    

// cold optimize by only processing events if accumulated value above some threshold
    public void processTracking( EventPoint ep  ){
       
        
       
        
        
        // do a maximum in one pass :
        // for all neighbouring event poinrs in large range 
        // update values from filter computation
        // when also in short range: apply low filter update
        fastDualLowFilterFrame(ep);
        
        
    /*   
        // apply border filter to find contours
        contour = findContour(filteredEvents);
     */
        
        
                /*
        if(thinning){
            //thresholdImage(filteredEvents,thin_threshold);
            thinned = thin(thresholdImage(filteredEvents,thin_threshold));
            // create contour out of thinned
            skeletton = new Contour(thinned,1);
            //nbBones = detectSegments( skeletton, 1, boneSize, bones, filteredEvents, false );
            nodes = detectNodes(skeletton,1,boneSize);
            
        }
         
            
            //
            
            //contour = findContour(renderer.getFr());
            
            // actually contour is made of several contours, (should rename contour to contours)
            // and here we label each contour with a number starting at 2
            // label 1 is reserved for the contours that touches the door
            // as the paw ocntour will always come though the door of the cage, which coordinates
            // must be well defined
            contour.link();
            // here we change to 1 the label of contours touching the door to identify them
            contour.highlightTouchingDoor();
            
          
            
            
            //  finding shape segments
            nbSegments = detectSegments( contour, 1, segSize, segments, filteredEvents, useIntensity );
        
            // finding finger tip by template matching
            fingerTips = detectFingersTips(insideIntensities,filteredEvents);
         
          // starts but also stops
           // if no activity
            if(!grasp_started&&checkActivity()) {
                grasp_started = checkGraspStart(fingerTips);
            } else {
                if(!checkActivity()&&!checkGraspStart(fingerTips)){
                 
                 grasp_started = false;
                 // reset
                 //resetFingerClusters();
                }
            }
           
            
           if(grasp_started){ 
            
                
            
            // here drives finger tip clusters!
            //driveFingerClusters(fingerTips,finger_cluster_range,finger_start_range,finger_start_threshold);

            // detect fingers lines from finger tips cluster(to change to), use global variable contour
            //fingerLines = detectFingersLines(fingerTips,insideIntensities,filteredEvents);
            //fingerLines = detectFingersLines(filteredEvents);


            // now try to attach model
            if(thinning&&useLowFilter){
            
                
                palm.updateModel(fingerTips,nodes,finger_cluster_range,node_range);
                
                if(useDualFilter){
                   palm.attachModelWithBack(nodes,skeletton,fingerTips,filteredEvents,secondFilteredEvents);
                    
                } else {
                   palm.attachModel(nodes,skeletton,fingerTips,filteredEvents);
                }
                
            }
                            
        } 
          
      
        if(decayOn){
                float decay = (float)decayLimit;
                //System.out.println("decay is on");
                // decay onloy inside door
                //decayArray(accEvents,currentTime,door_xa,door_xb,door_ya,door_yb,decay);
                // decay all
                decayArray(accEvents,currentTime,0,retinaSize-1,0,retinaSize-1,decay);
        }
     **/
    }
    
    
    public String toString(){
        String s="PawTracker3";
        return s;
    }
    
    public void resetArray( float[][][] array, float value ){
        
        // more efficient to just set all elements to value, instead of allocating new array of zeros
        // profiling shows that copying array back to matlab takes most cycles!!!!
        for (int i = 0; i<array.length; i++)
            for (int j = 0; j < array[i].length; j++){
            float[] f = array[i][j];
                for (int k = 0; k < f.length; k++){
                    f[k]=value;            
                }
            }
    }
    
    // reset float[][][k] array with k specified
    public void resetArray( float[][][] array, float value , int k){
        
        for (int i = 0; i<array.length; i++)
            for (int j = 0; j < array[i].length; j++){
            float[] f = array[i][j];
                if(k>0&&k<f.length){
                    f[k]=value;            
                }
            }
    }
    
    // copy float[][][k1] array into float[][][k2]
    public void emptyIntoSameArray( float[][][] array, int k1 , int k2){
         for (int i = 0; i<array.length; i++)
            for (int j = 0; j < array[i].length; j++){
            float[] f = array[i][j];
                if((k1>=0&&k1<f.length)&&(k2>=0&&k2<f.length)){
                    f[k2]=f[k1];  
                    f[k1]=0;
                }
            }
    }
    
      // copy float[][][] array to float[][][], no emptying, and dest must be initialized before, save time
    public float[][][] copyFrame( float[][][] source, float[][][] dest){
      // should be same size
      //  if(dest.length==source.length&&dest[0].length==source[0].length&&dest[0][0].length==source[0][0].length){
         for (int i = 0; i<source.length; i++)
            for (int j = 0; j < source[i].length; j++){
            float[] f = source[i][j];
                 for (int k = 0; k < f.length; k++){
                    dest[i][j][k] = f[k];            
                }
            }
        
        return dest;
    }
    
    
    
    // here not scaling but clipping values to [0..1]
      protected void scale(float[][][] fr ){
          // just clip values to 0..1 
          for (int i = 0; i<fr.length; i++){
              for (int j = 0; j<fr[i].length; j++){
//                  for (int k = 0; k<3; k++){
//                      float f = fr[i][j][k];
//                      if(f<0) f=0; else if(f>1) f=1;
//                      fr[i][j][k]=f;
//                  }
                  float f = fr[i][j][0];
                  if(f<0) f=0; else if(f>1) f=1;
                  fr[i][j][0]=f;
              }
          }
      }
      
      // scale, but only on the first of n float value of a float[][][n] array
       protected void scale(float[][][] fr, int x1, int y1, int x2, int y2 ){
           
          if(x1>=fr.length)return;
          if(x2>=fr.length)return;
          if(y1>=fr.length)y1=fr.length-1;
          if(y2>=fr[0].length)y2=fr[0].length-1;
          //find max
          float max = 0;
          
          for (int i = x1; i<y1; i++){
              for (int j = x2; j<y2; j++){
                  
                      float v = fr[i][j][0];
                      if(v>max)max=v;
                      
                  
              }
          }
          // scale only positive values, neg value to zero
          for (int i = x1; i<y1; i++){
              for (int j = x2; j<y2; j++){
                  
                      float f = fr[i][j][0];
                      if(f<0) f=0; else {
                          f=f/max;
                          
                      }
                      fr[i][j][0]=f;
                  
              }
          }
      }

       
      public void decayArray(float[][][] array , float currentTime, int xa, int xb, int ya, int yb, float decayLimit){
          
          // more efficient to just set all elements to value, instead of allocating new array of zeros
          // profiling shows that copying array back to matlab takes most cycles!!!!
          if(xb>=array.length) xa = array.length;
          if(yb>=array[0].length) ya = array[0].length;
          if(xa<0)xa=0;
          if(ya<0)ya=0;
          for (int i = xa; i<xb; i++){
              for (int j = ya; j < yb; j++){
                  float[] f = array[i][j];
                  float diff = currentTime-f[2];
                  //System.out.println("f.length:"+f.length+" currentTime-f[2]:"+diff+" < "+decayLimit);
                  if(f.length==3&&f[0]>0){// hack, i know
                      
                      // store initial value before decay, important to compute future updated values
                      if(f[1]==0)f[1] = f[0];
                      
                      // decay proportionnally
                      if(diff>=decayLimit) {
                          f[0] = 0;
                      } else {
                        f[0] = f[0] * (decayLimit-diff)/decayLimit;
                      }
//                      if(currentTime-f[2]<decayLimit) {
//                         //System.out.println("< true, remove point "+i+","+j);
//                          
//                              f[0]-=0.1;
//                              if(f[0]<=0){
//                                  f[0]=0;
//                                  f[1]=0;
//                                  f[2]=0;
//                              }
//                              
//                      }
                  }
              }
          }
      }
      
      
    
      
      
      
  
      
      
  
    
    
      
      private class Finger{
          // two segments: base finger and forefinger
          int x0,y0;
          int x1,y1;
          int x2,y2; //4 coordinates, finger segments could be detached??
          int x3,y3;
          float length1;
          float length2;
          float orientation1;
          float orientation2;
          
          public Finger( Line line1, Line line2 ){
              x0 = line1.x0;
              y0 = line1.y0;
              x1 = line1.x1;
              y1 = line1.y1;
              x2 = line2.x0;
              y2 = line2.y0;
              x3 = line2.x1;
              y3 = line2.y1;
              length1 = line1.length;
              length2 = line2.length;
              orientation1 = lineOrientation(line1);
              orientation2 = lineOrientation(line2);
          }
      
      }
      
      // Class Palm
      private class Palm{
          //Finger fingers[];
          FingerCluster[] fingers = new FingerCluster[MAX_NB_FINGERS];
          
          //int nbFingerAttached = 0;
          int nbFingersActive = 0;
          float maxDistance;
          float halfFingerLength = 10;
          
          public Palm(){
            //fingers = new Finger[MAX_NB_FINGERS];
             
          }
          
          public Palm(float maxDistance, float halfFingerLength){
              
              this.maxDistance = maxDistance; // to remove
              this.halfFingerLength = halfFingerLength;
          }
          
          public void updateParameters(float maxDistance, float halfFingerLength){
              
              this.maxDistance = maxDistance; // to remove
              this.halfFingerLength = halfFingerLength;
          }
          
          
          protected void updateModel( Vector fingerTips, Vector nodes, float range, float node_range ){
              
              // resest assignment
              for (int i=0;i<fingers.length;i++){
                  if(fingers[i]==null)fingers[i]=new FingerCluster();
                  fingers[i].assigned = false;
                  
              }
                            
              // for all active fingers while not all assigned
              int nbActiveAssigned=0;
              boolean outOfRange = false;
              while(nbActiveAssigned<nbFingersActive&&!outOfRange){
                  
                  
                  float min = 1000;
                  Point minPoint = new Point();
                  int minCluster = 0;
                  // for all unassigned activated finger
                  for (int i=0;i<fingers.length;i++){
                      if(fingers[i].activated){

                          // find for each finger the closest point
                          if(!fingers[i].assigned){
                              // for all points
                              for (Object o:fingerTips){
                                  Point p = (Point)o;
                                  if(p.disabled!=1){// if point not already assigned
                                      float distance = distanceBetween(p.x,p.y,fingers[i].tip_x_end,fingers[i].tip_y_end);
                                      if(distance<range){
                                          if(distance<min){
                                              min = distance;
                                              minPoint = p;
                                              minCluster = i;
                                          }
                                          
                                      }
                                  }
                              }
                          } // end for each finger find the closest point

                         
                          
                      }
                  }
                  // assign min, remove p and c from further computation
                  if(min<1000){
                      nbActiveAssigned++;
                      
                      fingers[minCluster].addTip(minPoint); // which set its "assigned" to true
                      
                      //System.out.println("update finger cluster tip at"+minPoint.x+","+minPoint.y);
                      // disable point
                      minPoint.disabled = 1;
                  } else {
                      outOfRange = true; //break loop because no point found in range
                  }
              } // end while
                      
               // for all active fingers while not all assigned
              int nbNewlyAssigned=0;
              outOfRange = false;
              int nbYetToAssign = MAX_NB_FINGERS-nbFingersActive;
              while(nbNewlyAssigned<nbYetToAssign&&!outOfRange){
                  
                  
                  float min = 1000;
                  Point minPoint = new Point();
                  int minCluster = 0;
                  // for all unassigned and not yet activated finger
                  for (int i=0;i<fingers.length;i++){
                      if(!fingers[i].activated){ // only for not yet activated clusters
                          
                          // find for each finger the closest point
                          if(!fingers[i].assigned){
                              // for all points
                              for (Object o:fingerTips){
                                  Point p = (Point)o;
                                  if(p.score>score_threshold&&p.disabled!=1){ // thus checking if p already used
                                      
                                      // find node in range with finger tip
                                      float min_dist_node = 1000;
                                      
                                      for (Object no:nodes){
                                          Node n = (Node)no;
                                          float dist_node = distanceBetween(p.x,p.y,n.x,n.y);
                                          if(dist_node<node_range){
                                              // found a potential finger
                                              if(dist_node<min_dist_node){
                                                  min_dist_node = dist_node;
                                                  break; // if any below range then it is ok so exit "for (Object no:nodes)"
                                              }
                                          }
                                      }
                                      // if any in range
                                      if(min_dist_node<1000){
                                          min = 0; //we select this one for being a new cluster
                                          // no need to see if another finger cluster nearer
                                          minPoint = p;
                                          minCluster = i;
                                          break; // found node in range so we exit loop "for (Object o:fingerTips)" to continue
                                      }
                                      
                                      
                                  }
                              } // end for each finger find the closest point
                              
                              
                              
                          }
                      }
                  }
                  // assign min, remove p and c from further computation
                  if(min<1000){
                      nbNewlyAssigned++;
                      if(!fingers[minCluster].activated) nbFingersActive++;
                      fingers[minCluster].addTip(minPoint); // which set its "assigned" to true
                      
                      //System.out.println("add new finger cluster tip at"+minPoint.x+","+minPoint.y);
                      // disable point
                      minPoint.disabled = 1;
                  } else {
                      outOfRange = true; //break loop because no point found in range
                  }
              }     // end while
          }
              
          
              
           
   
       /**
          public void assignNeigbours(){
                  // if all assigned for first time this grasp, create knucle polygon by assigning closest neighbours
              if(!knuckle_polygon_created){
                  //System.out.println("nb assigned: "+nbFingersAssigned+"<"+MAX_NB_FINGERS);
                  if(nbFingersAssigned==MAX_NB_FINGERS){
                      knuckle_polygon_created = true;
                      boolean endFoundBefore = false;
                      FingerCluster endCluster = null;
                      // assign closest neighbours
                      // for all clusters find closest neighbours
                      for (int i=0;i<fingers.length;i++){
                          
                          int x = fingers[i].x;
                          int y = fingers[i].y;
                          Vector minima = new Vector();
                          for (int j=0;j<fingers.length;j++){
                              if(j!=i){
                                  // compute distance
                                  float distance = distanceBetween(x,y,fingers[j].x,fingers[j].y);
                                  // add to list of minima
                                  minima.add(new Minimum((int)distance,j));
                                  
                              }
                              
                          }
                          // all values in minima
                          // sort minima in ascending order
                          Collections.sort(minima, new MinimaComparer());
                          // add first neighbour to cluster
                          if(minima.size()>0){
                              fingers[i].neighbour1 = fingers[((Minimum)minima.elementAt(0)).index];
                              
                          }
                          if(minima.size()>1){ // another one, must be in different plane
                              int xb = fingers[i].finger_base_x;
                              int yb = fingers[i].finger_base_y;
                              // compute separation line equation
                              float b = ((float)(y*xb) - (float)(x*yb))  / (float)(xb-x);
                              float a = (y-b)/(float)x;
                              boolean above = false;
                              // in what plane is the first neighbour?
                              if(fingers[i].neighbour1.y>(a*fingers[i].neighbour1.x+b)){
                                  above = true;
                              }
                              
                              // while minima left, not assigned
                              // check if minima is in other plane from previous one
                              // if no, assign and quit
                              // else continue
                              boolean found = false;
                              int k=1;
                              while(!found&&k<minima.size()){
                                  int index = ((Minimum)minima.elementAt(k)).index;
                                  int x2 = fingers[index].x;
                                  int y2 = fingers[index].y;
                                  
                                  // check if different plane
                                  if(y2>(a*x2+b)){
                                      if(!above){
                                          // ok this one is above but before was below
                                          found = true;
                                          fingers[i].neighbour2 = fingers[index];
                                      }
                                  } else {
                                      if(above){
                                          // ok this one is below but before was above
                                          found = true;
                                          fingers[i].neighbour2 = fingers[index];
                                      }
                                  }
                                  
                                  
                                  k++;
                              }//end while not found second minimum
                              if(!found){
                                  if(endFoundBefore){
                                      fingers[i].neighbour2 = endCluster;
                                      endCluster.neighbour2 = fingers[i];
                                  } else {
                                      endCluster = fingers[i];
                                      endFoundBefore = true;
                                  }
                              }
                              
                          }//end if more than one minimum
                          
                      }// end for all clusters find closest neighbours
                  }//end if nb assigned == nb max fingers
              }// end if need create knuckle polygon
          }
         */ 
          
         /* 
          public void attachModel(Vector nodes, Contour thin, Vector fingerTips, float[][][] frame ){
              // if end node near fingertip for the first time and less than four fingers attached do :
              // find two consecutive best line of length L starting from end node or finger tip
              maxDistance = finger_sensitivity;
              halfFingerLength = finger_length/2;
                      
              // for all fingers
              for (Object o:fingers){
                  
                  FingerCluster f = (FingerCluster)o;
                  
                  if(f.activated){
                                        
                      // for each finger, find the line that fit best the thinned skeletton image
                     // mostValuableLine uses global variable: contour
                      Line line1 = mostValuableLine(f.tip_x_end,f.tip_y_end, (int)halfFingerLength, thin , frame);
                      Line line2 = mostValuableLine(line1.x1,line1.y1, (int)halfFingerLength, lineDirection(line1), finger_ori_variance, thin , frame);
                     // System.out.println("For tip "+f.tip_x_end+","+f.tip_y_end+" lines found: "+line1.length+" "+line2.length);
                     // System.out.println("Line 1 ["+line1.x0+","+line1.y0+"] to ["+line1.x1+","+line1.y1+"]");
                     // System.out.println("Line 2 ["+line2.x0+","+line2.y0+"] to ["+line2.x1+","+line2.y1+"]");
              
                      // updateLines is quite important
                      // it defines the coordinates of the two segments composing a finger
                      // but also, if these segments were previously defined, it apply a chosen
                      // policy for merging them so as to preserv ekinetic constraints or others
                  
                      f.updateLines(line1,line2);                                                
                  }                  
              }          
          } // end attachModel
          */
          
          /*
          public void attachModelWithBack(Vector nodes, Contour thin, Vector fingerTips, float[][][] frame, float[][][] frame2 ){
              // if end node near fingertip for the first time and less than four fingers attached do :
              // find two consecutive best line of length L starting from end node or finger tip
              maxDistance = finger_sensitivity;
              halfFingerLength = finger_length/2;
              
//              System.out.println("attachModelWithBack starts");
                      
                      
              // for all fingers
              for (Object o:fingers){
                  
                  FingerCluster f = (FingerCluster)o;
//                  System.out.println("attachModelWithBack FingerCluster ["+f.tip_x_end+","
//                          +f.tip_y_end+" actif:"
//                          +f.activated+" assigned:"+f.assigned);
                  if(f.activated){
                      Line line1;
                      Line line2;
                      boolean updateEndTip = false;
                      if(f.assigned){            
                          // for each finger, find the line that fit best the thinned skeletton image
                          // mostValuableLine uses global variable: contour
                          line1 = mostValuableLine(f.tip_x_end,f.tip_y_end, (int)halfFingerLength, thin , frame);
                          
                          
                          // here find shortest line to palm's back defined by points above palmback_threshold in frame2
                          // which works only if frame2 is topographically filtered with a large radius and a small density
                          // shortestLineToValue uses global variable: contour
                          line2 = shortestLineToValue(line1.x1,line1.y1, palmback_distance, lineDirection(line1), finger_ori_variance, palmback_below, palmback_value, frame, frame2);
                          // System.out.println("For tip "+f.tip_x_end+","+f.tip_y_end+" lines found: "+line1.length+" "+line2.length);
                          // System.out.println("Line 1 ["+line1.x0+","+line1.y0+"] to ["+line1.x1+","+line1.y1+"]");
                          // System.out.println("Line 2 ["+line2.x0+","+line2.y0+"] to ["+line2.x1+","+line2.y1+"]");
                      } else { // for finger not assigned
                          // from start of tip, find line same orientation as before but stopping at shape edge
                           Line line = lineToShape(f.tip_x_start,f.tip_y_start, (int)halfFingerLength, lineDirection(new Line(f.tip_x_start,f.tip_y_start,f.tip_x_end,f.tip_y_end)), line2shape_thresh, frame);
                           line1 = new Line(line.x1,line.y1,line.x0,line.y0,line.length);// to invert direction
                           line2 = shortestLineToValue(line1.x1,line1.y1, palmback_distance, lineDirection(line1), finger_ori_variance, palmback_below, palmback_value, frame, frame2);
                           updateEndTip = true;
//                           System.out.println("attachModelWithBack using lineToShape for tip end ["+f.tip_x_end+","
//                          +f.tip_y_end+"] actif:"
//                          +f.activated+" assigned:"+f.assigned
//                          +"\n result: line length:"+line.length
//                          +" from ["+line.x0+","+line.y0+"]"+" to ["+line.x1+","+line.y1+"]");
                      }
                      // updateLines is quite important
                      // it defines the coordinates of the two segments composing a finger
                      // but also, if these segments were previously defined, it apply a chosen
                      // policy for merging them so as to preserv ekinetic constraints or others
                  
                      f.updateLines(line1,line2,updateEndTip);                                                
                  }                  
              }          
          } // end attachModel
       */
                 
            /*      
          public void driveModel(Vector nodes, int [][][] image, Vector fingerTips ){
              
             
              //  move cluster and check constraint like
              // if too far from other finger, move toward..
              // or delete if life time set and reached ...
              
//              for (int i=0;i<fingers.length;i++){
//                  // if not assigned
//                  if(!fingers[i].assigned){
//                      fingers[i].updatePosition(fingers,i);
//                  }
//              }
              
              
              
          }
            */  
              
      } // end Class Palm 
         
 
          
      
      
      // Class FingerCluster
      private class FingerCluster{
          // int[2] for storing previous value also
          int tip_x_end = 0; //end tip
          int tip_y_end = 0;
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
          boolean activated = false;
         
         
          FingerCluster neighbour1 = null;
          FingerCluster neighbour2 = null;
          
          public FingerCluster(  ){
             
              reset();
          }
          
          
          
          public void reset(){
              activated = false;
              assigned = false;
              lifetime = 0;
              neighbour1 = null;
              neighbour2 = null;
              
              length_base = 0;
              length_tip = 0; 
              orientation_tip=0;
              orientation_base=0;
              direction_tip=0;
              direction_base=0;
              
              tip_x_end = 0; //end tip
              tip_y_end =0;
              tip_x_start = 0; // beginning  tip
              tip_y_start = 0;
              base_x_start = 0; // beginning first joint
              base_y_start = 0;
              base_x_end = 0; // end first joint
              base_y_end = 0;
          }   
          
          public void addTip( Point p){
             
              // memorize previous values
              
              tip_x_end = p.x;
              tip_y_end = p.y;
              // if not previously assigned this grasp : 
              
              
              assigned = true;
              activated = true;
              lifetime = cluster_lifetime;         
          }
          
          public void updateLines( Line line1, Line line2 ){
              updateLines( line1, line2, false );
              
          }
          
          public void updateLines( Line line1, Line line2, boolean updateEndTip ){
              // memorize previous values
               // memorize previous values
              // tip_x0 and tip_y0 already saved
              
              // if line to palm's back too long, then not a finger (?)
//              if(line2.length<=0){ // here to do
//                  System.out.println("finger ending at ["+tip_x_end+","+tip_y_end+"] doesn't reach paw back");
//                //activated=false;
//                //assigned=false;
//              } //else {
             
              if(updateEndTip){
                tip_x_end = line1.x0;
                tip_y_end = line1.y0;     
              }
              if(length_tip!=0){ 
                  if(line1.length>0){
                     // tip_x_end = (int)(tip_x_end*finger_mv_smooth + line1.x1*(1-finger_mv_smooth));
                    // tip_y_end = (int)(tip_y_end*finger_mv_smooth + line1.y1*(1-finger_mv_smooth));
                      tip_x_start = (int)(tip_x_start*finger_mv_smooth + line1.x1*(1-finger_mv_smooth));
                      tip_y_start = (int)(tip_y_start*finger_mv_smooth + line1.y1*(1-finger_mv_smooth));
                      orientation_tip = lineOrientation(new Line(tip_x_end,tip_y_end,tip_x_start,tip_y_start));
                      length_tip = line1.length;
                  }
              } else {
                  if(line1.length>0){
                    tip_x_start = line1.x1;
                    tip_y_start = line1.y1;
                    length_tip = line1.length;
                    orientation_tip = lineOrientation(line1);
                  }
              }
              
              if(length_base!=0){ 
                  if(line2.length>0){
                      base_x_start = (int)(base_x_start*finger_mv_smooth + line2.x1*(1-finger_mv_smooth));
                      base_y_start = (int)(base_y_start*finger_mv_smooth + line2.y1*(1-finger_mv_smooth));
                      base_x_end = (int)(base_x_end*finger_mv_smooth + line2.x0*(1-finger_mv_smooth));
                      base_y_end = (int)(base_y_end*finger_mv_smooth + line2.y0*(1-finger_mv_smooth));
                      
                      orientation_base = lineOrientation(new Line(base_x_end,base_y_end,base_x_start,base_y_start));
                      
                      // compute new length also.. to do
                      length_base = line2.length;
                  } else if(line1.length>0){
                      // update one end touching updated line
                      base_x_end = (int)(base_x_end*finger_mv_smooth + tip_x_start*(1-finger_mv_smooth));
                      base_y_end = (int)(base_y_end*finger_mv_smooth + tip_y_start*(1-finger_mv_smooth));
                      
                      orientation_base = lineOrientation(new Line(base_x_end,base_y_end,base_x_start,base_y_start));
                      
                  }
              } else {
                  if(line2.length>0){
                       base_x_start = line2.x1;
                       base_y_start = line2.y1;
                       base_x_end = line2.x0;
                       base_y_end = line2.y0;
                       length_base = line2.length;
                       orientation_base = lineOrientation(line2);
                  } else if(line1.length>0){ // keep connected
                       base_x_end = tip_x_start;
                       base_y_end = tip_y_start;
                  }
                  
                  
              }
              
              if(length_tip!=0){ 
                  if(line1.length<=0&&line2.length>0){
                      tip_x_start = (int)(tip_x_start*finger_mv_smooth + base_x_end*(1-finger_mv_smooth));
                      tip_y_start = (int)(tip_y_start*finger_mv_smooth + base_y_end*(1-finger_mv_smooth));
                      
                      
                  }
              } else {
                 
                 
                  if(line1.length<=0&&line2.length>0){
                      tip_x_start = base_x_end;
                      tip_y_start = base_x_end;
                  }
                  
                  
                  
              }     
             
              direction_tip = lineDirection(new Line(tip_x_start,tip_y_start,tip_x_end,tip_y_end)); 
              direction_base = lineDirection(new Line(base_x_start,base_y_start,base_x_end,base_y_end)); 
                 
           //   } //end if length not too long
              
              
              
          }
                          
          
          // update finger position every time bin, do nothing new if finger alive but
          // if no assigned fingertip for too long then pull by other fingers tips if
          // they are activated and alive
          public void updatePosition( FingerCluster[] allClusters, int ownIndex ){
              // if activated and lifetime > zero
              if(activated){
                 if (lifetime>0){
                    lifetime--;
                 } else {
                     // if activated but lifetime at zero
                     // allow pulling by closest neighbours
                     
                     // find closest neighbours that are activated and alive
                     // if one or two found, apply pull to nearest valid zone
                     // or detect possible best location (like if range too short but
                     // some good fingertips are available
                 }
              } else {
                  lifetime=0;
              }
        
          }
          
      } // end Class FingerCluster
     
      
       
      // thinning picture
      // Thinning algorithm: CACM 1984 march (Zhang and Suen)
      
      int[][][] thresholdImage( float[][][] image, float threshold ){
          int[][][] img = new int[image.length][image[0].length][image[0][0].length];
         
          for(int i=0;i<image.length;i++){
              for(int j=0;j<image[0].length;j++){
                 if(image[i][j][0]<=threshold){
                     img[i][j][0]=0;
                 } else {
                    img[i][j][0]=1; 
                 }
                  
              }
          }
          
          return img;
          
      }
      
      // destructive and on previously thresholded image
      int[][][] thin( int[][][] image ){
          int i=0,j=0,n=0,m=0,k=0, br=0,ar=0,p1=0,p2=0;
          int nn = image.length;
          int mm = image[0].length;
          boolean cont = true;
          int[] a = new int[8];
          int[] arbr = new int[2];
          int[][][] y = new int[image.length][image[0].length][image[0][0].length];
          
          while(cont){
              cont  = false;
              /*	Sub-iteration 1: */
		for (i=0; i<nn; i++)
		  for (j=0; j<mm; j++) {		/* Scan the entire image */
			if (image[i][j][0] == 0 ) {
				y[i][j][0] = 0;
				continue;
			}
			arbr = t1a (image, i, j, a, br,nn,mm);	/* Function A */
                        ar = arbr[0];
                        br = arbr[1];
                        
			p1 = a[0]*a[2]*a[4];
			p2 = a[2]*a[4]*a[6];
			if ( (ar == 1) && ((br>=2) && (br<=6)) &&
				(p1 == 0) && (p2 == 0) )  {
					y[i][j][0] = 1;
					cont = true;
			}
			else y[i][j][0] = 0;
		}
		subtr (y, image,nn,mm);
              
                /* Sub iteration 2: */
		for (i=0; i<nn; i++)
		  for (j=0; j<mm; j++) {		/* Scan the entire image */
			if (image[i][j][0] == 0 ) {
				y[i][j][0] = 0;
				continue;
			}
			arbr = t1a (image, i, j, a, br,nn,mm);	/* Function A */
                        ar = arbr[0];
                        br = arbr[1];
			p1 = a[0]*a[2]*a[6];
			p2 = a[0]*a[4]*a[6];
			if ( (ar == 1) && ((br>=2) && (br<=6)) &&
				(p1 == 0) && (p2 == 0) )  {
					y[i][j][0] = 1;
					cont = true;
			}
			else y[i][j][0] = 0;
		}
		subtr (y, image,nn,mm);
              
          }
         return image;
      }
      
      int[] t1a( int[][][] image, int i, int j, int[] a, int br, int nn, int mm ){
          int[] arbr = new int[2];
          //...
          /*	Return the number of 01 patterns in the sequence of pixels
	P2 p3 p4 p5 p6 p7 p8 p9.					*/

	int n,m;
        int b;
	for (n=0; n<8; n++) a[n] = 0;
	if (i-1 >= 0) {
		a[0] = image[i-1][j][0];
		if (j+1 < mm) a[1] = image[i-1][j+1][0];
		if (j-1 >= 0) a[7] = image[i-1][j-1][0];
	}
	if (i+1 < nn) {
		 a[4] = image[i+1][j][0]; 
		if (j+1 < mm) a[3] = image[i+1][j+1][0];
		if (j-1 >= 0) a[5] = image[i+1][j-1][0];
	}
	if (j+1 < mm) a[2] = image[i][j+1][0];
	if (j-1 >= 0) a[6] = image[i][j-1][0];

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
      
      void subtr( int[][][] a, int[][][] b, int n, int m){
          
            for (int i=0; i<n; i++){
		for (int j=0; j<m; j++) {
			b[i][j][0] -= a[i][j][0];
                }
            }
      }
      
      private class Line{
        
        int length;
        int x0;
        int y0;
        int x1;
        int y1;
        int midx;
        int midy;
        float orientation=0;
        
        public boolean touchesDoor = false;
        
        public Line(){
            length = 0;
            x0 = 0;
            y0 = 0;
            x1 = 0;
            y1 = 0;
            midx = 0;
            midy = 0;
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
        public Line( int x0, int y0, int midx, int midy, int x1, int y1, int length){
            this.length = length;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.midx = midx;
            this.midy = midy;
        }                     
    }
    
      
      
      
    private class LineLengthComparer implements Comparator {
                public int compare(Object obj1, Object obj2)
                {
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
      
    private class Point{
        
        int score;
        int disabled;
        int x;
        int y;
        
        public Point(){
            score = 0;
            disabled = 0;
            x = 0;
            y = 0;
        }
        public Point( int x, int y){
            this.score = 0;
            this.disabled = 0;
            this.x = x;
            this.y = y;
        }
        public Point( int x, int y, int score){
            this.score = score;
            disabled = 0;
            this.x = x;
            this.y = y;
        }
        
        
    }
    
    
    
    private class ScoreComparer implements Comparator {
                public int compare(Object obj1, Object obj2)
                {
                        int i1 = ((Point)obj1).score;
                        int i2 = ((Point)obj2).score;
        
                        return i2 - i1;
                }
    }

       private class Minimum{
        
        int value;
        int index;
       
        public Minimum(){
            value = 0;
            index = 0;
            
        }
        public Minimum( int value, int index){
            this.value = value;
            this.index = index;
            
        }
        
        
    }
    
    
    private class MinimaComparer implements Comparator {
                public int compare(Object obj1, Object obj2)
                {
                        int i1 = ((Minimum)obj1).value;
                        int i2 = ((Minimum)obj2).value;
        
                        return i1 - i2;
                }
    }
    
       //detect finger lines in array of inside intensity and array of accumulated contrast change
    // after use of finger tips are detected
    /**
    private Vector detectFingersLines(  float[][][] fr ){
        Vector lines = new Vector();
        
        // for each points in tips        
        // find longest line with acc values > threshold, and stopping if touching door
        // at tip distance, if pt.x,y too close to shape, put it to mindistance
        // if too close from both sides, put it at the middle
        
        
        for(int i=0;i<fingerTipClusters.length;i++){
             // find longest line with acc values > threshold, and stopping if touching door
            FingerCluster fc = fingerTipClusters[i];
            if(fc.activated){
                int range = 70; //to parametrize or #define
                int midlength = 13;//to parametrize although it is unused yet
                Vector somelines = longestLines(fc.x,fc.y,range,midlength,lines_n_avg,fr);

                // compute mean line
                Line meanLine = meanLineOf(somelines);
                // lines.addAll(somelines);
                if (meanLine!=null) {
                    lines.add(meanLine);
                    // add finger information to cluster
                    fc.addFinger(meanLine,fr);
                    
                }
            }
        }
      
        return lines;
    }
    */
    
   
   
    
 
  
    
    
    private int testFingerTipPattern( EventPoint ep ){
        int score = 0;
        float threshold = score_in_threshold; // to adapt
        
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
                      
                        if(eventPoints[i][j].intensity>threshold){
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
                         if(eventPoints[i][y-distance1].intensity<threshold){
                            score++;
                         }  
                      }
                      if(y+distance1<eventPoints.length){
                         if(eventPoints[i][y+distance1].intensity<threshold){
                            score++;
                         }
                       
                      }
                   
                      if(y-distance2>=0){
                         if(eventPoints[i][y-distance2].intensity<threshold){
                            score++;
                         }  
                      }
                      if(y+distance2<eventPoints.length){
                         if(eventPoints[i][y+distance2].intensity<threshold){
                            score++;
                         }
                       
                      }
                  
              }
        }
        for (int j = y-yOutside; j<y+yOutside; j++){
              if(j>=0&&j<eventPoints.length){
                 
                      if(x-distance1>=0){
                         if(eventPoints[x-distance1][j].intensity<threshold){
                            score++;
                         }  
                      }
                      if(x+distance1<eventPoints.length){
                         if(eventPoints[x+distance1][j].intensity<threshold){
                            score++;
                         }
                       
                      }
                      if(x-distance2>=0){
                         if(eventPoints[x-distance2][j].intensity<threshold){
                            score++;
                         }  
                      }
                      if(x+distance2<eventPoints.length){
                         if(eventPoints[x+distance2][j].intensity<threshold){
                            score++;
                         }
                       
                      }
              }
              
        }
        // remove points with no support from actual real accumulated image
        if(eventPoints[x][y].intensity<=score_sup_threshold){
            score = 0;
        }        
        
        return score;
        //return (int) (score * fr[x][y][0] * 10);
    }
      
   
    
  
    
    
    
    
    
    public void isOnPawShape(EventPoint ep){
              
        int x = ep.x;
        int y = ep.y;
        
        ep.zerosAround = 0;
        boolean changedToLow = false;
        boolean changedToHigh = false;
        
        
        if(ep.previousShortFilteredValue<shapeLimit){
            if(ep.shortFilteredValue>=shapeLimit){
                changedToHigh = true;
            }
        } else {
             if(ep.shortFilteredValue<shapeLimit){
                changedToLow = true;
            }
        }
        
       
       
        int i;
        int j;
        for (i=x-1; i<x+2;i++){
            if(i>=0&&i<retinaSize){
                for (j=y-1; j<y+2;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j];                                           
                        if(surroundPoint.shortFilteredValue<shapeLimit){
                            ep.zerosAround++;
                        }
                        // here we update also the surroundPoint border status
                        // if too costly in time: maybe avoid
                        
                        if(changedToLow){
                            surroundPoint.zerosAround++;
                            checkBorderTemplate(surroundPoint);
                        } else if(changedToHigh){
                            surroundPoint.zerosAround--;
                            checkBorderTemplate(surroundPoint);
                        }
                        
                      
                        
                        
                    }
                }
            }
        }
        
         
        checkBorderTemplate(ep);
         
        
         // create new label if no neighbouring label
        // for this collect all neigbouring labels, and if any choose the one
        // with lowest value to replace the others by assignment

        // intensity : update neigbour intensity before?
        
        
                      
    }
    
    void checkBorderTemplate(EventPoint ep){
        boolean becomesBorder = false;
        
        if(ep.shortFilteredValue>shapeThreshold){
            
            if(ep.zerosAround>minZeroes&&ep.zerosAround<maxZeroes){
                becomesBorder = true;
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
         int range = intensity_range;
         int score = 0;
         int score_max = 0;
         int imax = 0;
         int jmax = 0;
         
         if((ep.border&&!becomesBorder)||(becomesBorder&&!ep.border)){
             for (i=x-range; i<x+range+1;i++){
                 if(i>=0&&i<retinaSize){
                     for (j=y-range; j<y+range+1;j++){
                         if(j>=0&&j<retinaSize){
                             EventPoint surroundPoint = eventPoints[i][j];
                             // if current ep point is a border, increase intensity around                             
                             surroundPoint.intensity += intensityChange;
                             // check if finger point
                             if(surroundPoint.intensity>intensity_threshold
                                     &&surroundPoint.shortFilteredValue>in_value_threshold){
                                 // check for finger
                                 score = testFingerTipPattern(surroundPoint);
                                 if(score>score_max){
                                     score_max = score;
                                     imax = i;
                                     jmax = j;
                                 }
                             }
                         }
                     }
                 }
             }
         }
                        
        ep.border =  becomesBorder;
        
        // deals with possible finger
        
        if(score_max>0){
            // do something with eventPoints[imax][jmax]
            // like add to finger tracker
        }
        
    }
    
    public class Segment{
        protected int maxX=128,maxY=128;
        
        public int x1;
        public int y1;
        public int x2;
        public int y2;
        
        // point inside paw        
        public int xi =0;
        public int yi =0;
        // point outside paw        
        public int xo =0;
        public int yo =0;
        
        public int acc; //number of identical values //to get rid of
        public double distGC;
        public double orientation;
        public double size;
        
        public Segment(){
            reset();
        }
        public Segment(ContourPoint c1, ContourPoint c2 ){
            x1 = c1.x;
            y1 = c1.y;
            x2 = c2.x;
            y2 = c2.y;
        }
        
        
        public void reset(){
            
            
        }
        
        public int midx(){
            return (int)((x1+x2)/2); //+0.5?
        }
        public int midy(){ 
            return (int)((y1+y2)/2);
        }
        
        
    }
    
  
    
    protected void resetDoubleArray( int[][] array, int x_max, int y_max ){
        for(int i=0;i<x_max;i++){
                    for(int j=0;j<y_max;j++){                     
                        array[i][j] = 0;
                    }
        }
    }
    
    // test if no points in a vector are touching  
    protected boolean testSeparation( Vector points ){
        for (Object o1:points){
            ContourPoint cp1 = (ContourPoint)o1;
            for (Object o2:points){
                ContourPoint cp2 = (ContourPoint)o2;
                if(o1!=o2){
                    if(distanceBetween(cp1.x,cp1.y,cp2.x,cp2.y)<=1){
                        return false;
                    }                    
                }
            }
            
        }
        return true;
    }
    
    public Vector detectNodes( Contour contour, int label, int range  ) {
        int nbNodes = 0;
        Vector nodes = new Vector();
        // for each point, look at neighbours. if only one, end node
        // if two, if not "aligned" maybe joint node
        // if three, cross node
        
        Contour contourCopy = new Contour(contour);
        
        int maxX = contour.getMaxX();
        int maxY = contour.getMaxY();
        for (int i=0;i<maxX;i++){
            for (int j=0;j<maxY;j++){
                if (contour.eventsArray[i][j]!=null){
                if (contour.eventsArray[i][j].on==1){ //could optimize this by putting al on points into a specific array
                    if (contour.eventsArray[i][j].label==label){ 
                        contour.eventsArray[i][j].used = 0;
                        Vector neighbours = contour.getNeighbours(i,j,range);
                        if(neighbours.size()==1){
                            // end node
                            Node endNode = new Node(i,j,0); // 0 for end node, to define somewhere
                            // measure node value (high if end of long segment)
                            //endNode.measureSupport(contour);
                            endNode.measureSupport(contourCopy);
                            nodes.add(endNode);
                        } else if(neighbours.size()>2){
                          //  if(testSeparation(neighbours)){
                           //     Node crossNode = new Node(i,j,1); // 1 for cross node, to define somewhere
                           //     nodes.add(crossNode);
                          //}
                        } //else if(neighbours.size()==2){
                            // could be joint node
                            
                       // }
                        
                    }                                             
                }
                }
            }
        }
        
        
        return nodes;       
    }
    
  
   
   
    /*
      // stops if touching door or pixel below threshold
    protected Line longestLine(int x, int y, int range, float orientation, float[][][] accEvents){
           float threshold = line_threshold;
           int shape_min_distance = 1;
           // find x1,y1 at range long orientation
           int x1 = x + (int)(Math.cos(Math.toRadians(orientation))*range);
           int y1 = y + (int)(Math.sin(Math.toRadians(orientation))*range);
          
           int x0 = x;
           int y0 = y;
          
             
                    
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
                                if(accEvents[y0][x0][0]>threshold){
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                    } else {
                                    if((length+1<shape_min_distance)||((contour.eventsArray[x0][y0].label!=1)
                                      ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                    length++;
                                                                     
                                    } else {
                                        break;
                                    }
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
                                if(accEvents[y0][x0][0]>threshold){
                                    // 3 : here is the area around the start point in which we dont check fo shape border, in case
                                    // of border noise // should parametrize
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                    } else {
                                 if((length+1<shape_min_distance)||((contour.eventsArray[x0][y0].label!=1)
                                      ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                              
                                    length++;
                                   
                                    } else {
                                        break;
                                    }
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
                    
                 
                   // store  line
                    Line line = new Line(x,y,x0,y0,length);
            
        return line; 
    }
    */
    
    /*
    protected Line lineToShape(int x, int y, int range, float orientation, float min_threshold, float[][][] frame ){
        Line result = new Line();
        
        // compute x1s and y1s based on range
        // for all points in a square outline centered on x0,y0 with side size range+1/2
        // find length of line
        // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
        
        int x0 = x;
        int y0 = y;
        int xEnd = 0;
        int yEnd = 0;
        
        
        // find target point
        int x1 = x0 + (int)(Math.cos(Math.toRadians(orientation))*range);
        int y1 = y0 + (int)(Math.sin(Math.toRadians(orientation))*range);
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
                    if(frame[y0][x0][0]>min_threshold){
                        if (contour.eventsArray[x0][y0]==null){
                            length++;
                        } else {
                            if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                            ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                
                                length++;
                                
                            } else {
                                break;
                            }
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
                    if(frame[y0][x0][0]>min_threshold){
                        if (contour.eventsArray[x0][y0]==null){
                            length++;
                        } else {
                            if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                            ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                length++;
                                
                            } else {
                                break;
                            }
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
      
        xEnd = x0;
        yEnd = y0;
        result = new Line(x,y,xEnd,yEnd,length);
       // System.out.println("lineToShape result start: ["+x+","+y+"] target: ["+xEnd+","+yEnd+"]");
        return result;
              
    }
        */  
    
    /**
    protected Line shortestLineToValue(int x, int y, int range, float orientation, float variation, float min_threshold, float value_threshold,  float[][][] highResFrame, float[][][] lowResFrame){
          Line result = new Line();
        
           // compute x1s and y1s based on range
           // for all points in a square outline centered on x0,y0 with side size range+1/2
           // find length of line
           // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
          
          
           int x1 = 0;
           int y1 = 0;
           int x0 = 0;
           int y0 = 0;
           int xEnd = 0;
           int yEnd = 0;
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
                    
                    float targetOrientation = lineDirection(new Line(x0,y0,x1,y1));
                    // if within wanted angle of research
                    if((targetOrientation>orientation-variation)&&(targetOrientation<orientation+variation)){
                        
                    
                    
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
                                if(highResFrame[y0][x0][0]>min_threshold){
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(lowResFrame[y0][x0][0]>value_threshold){
                                            valueFound=true;
                                            break;
                                        }
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            
                                            length++;
                                            if(lowResFrame[y0][x0][0]>value_threshold){
                                                valueFound=true;
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
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
                                if(highResFrame[y0][x0][0]>min_threshold){
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(lowResFrame[y0][x0][0]>value_threshold){
                                            valueFound=true;
                                            break;
                                        }
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            length++;
                                            if(lowResFrame[y0][x0][0]>value_threshold){
                                                valueFound=true;
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
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
                            xEnd = x0;
                            yEnd = y0;
                            result = new Line(x,y,xEnd,yEnd,length);
                         }
                    }
                    
                   
                    
                    
                } // end if on outline     
                }// end if within accepted orientation
              }          
           } //end for all points on square's outline
      
        return result;                
    }
    */
    
    
    /**
     // stops if touching shape or pixel below threshold and value increase if touching point in image
    protected Line mostValuableLine(int x, int y, int range, Contour thin , float[][][] accEvents){
     
        return mostValuableLine(x, y, range, 180, 360, thin , accEvents);
    }
     // stops if touching shape or pixel below threshold and value increase if touching point in image
    // uses global variable: contour 
    protected Line mostValuableLine(int x, int y, int range, float orientation, float variation, Contour thin , float[][][] accEvents){
        
        
        Line result = new Line();
        
           // compute x1s and y1s based on range
           // for all points in a square outline centered on x0,y0 with side size range+1/2
           // find length of line
           // if above max, x,y dest = x1,y1 and max = length, touchingdoor= true/false accordingly
           float threshold = line_threshold;
           
           int x1 = 0;
           int y1 = 0;
           int x0 = 0;
           int y0 = 0;
           int xEnd = 0;
           int yEnd = 0;
           int lengthMax = 0;
           int valueMax = 0;
           Vector visited = new Vector();
           
           // should replace by to all points in range and on the skeletton (?)
           
           // for all points in a square outline centered on x0,y0 with side size range+1/2
           for(int i=x-range;i<x+range+1;i++){
              Vector onTheLine = new Vector();
              for(int j=y-range;j<y+range+1;j++){
                if(((i<=x-range)||(i>=x+range))||((j<=y-range)||(j>=y+range))){
                    // on the square outline
                    // if within demanded orientation acceptance
                    
                    
                    x1 = i; 
                    y1 = j;
                    x0 = x;
                    y0 = y;                                      
                    
                    int last_x = x;
                    int last_y = y;
                    
                    float targetOrientation = lineDirection(new Line(x0,y0,x1,y1));
                    
                    if((targetOrientation>orientation-variation)&&(targetOrientation<orientation+variation)){
                        
                    
                    
                    int dy = y1 - y0;
                    int dx = x1 - x0;
                    int stepx, stepy;
                    int length = 0;
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
                                if(accEvents[y0][x0][0]>threshold){
                                    // if length+1<3 : so as not to stop at shape border if too early to check because of noise in shape
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(thin.eventsArray[x0][y0]!=null){ 
                                        //value+=thin.eventsArray[x0][y0].on; // should check dimension
                                            if(thin.eventsArray[x0][y0].on==1){
                                                last_x = x0;
                                                last_y = y0;
                                                value++;
                                            }
                                        
                                        }
                                        onTheLine.add(new Point(x0,y0));
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            length++;
                                            if(thin.eventsArray[x0][y0]!=null){ 
                                            //value+=thin.eventsArray[x0][y0].on; // should check dimension
                                                if(thin.eventsArray[x0][y0].on==1){
                                                    last_x = x0;
                                                    last_y = y0;
                                                    value++;
                                                }
                                            }
                                            onTheLine.add(new Point(x0,y0));
                                            // System.out.println("value line: for img["+x0+"]["+y0+"]="+image[x0][y0][0]+" and img["+y0+"]["+x0+"]="+image[y0][x0][0]);
                                            
                                        } else {
                                            break;
                                        }
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
                                if(accEvents[y0][x0][0]>threshold){
                                    // 3 : here is the area around the start point in which we dont check fo shape border, in case
                                    // of border noise // should parametrize
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(thin.eventsArray[x0][y0]!=null){    
                                           //value+=thin.eventsArray[x0][y0].on; // should check dimension
                                              if(thin.eventsArray[x0][y0].on==1){
                                                  last_x = x0;
                                                  last_y = y0;
                                                  value++;
                                              }
                                        }
                                        onTheLine.add(new Point(x0,y0));
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            
                                            length++;
                                            if(thin.eventsArray[x0][y0]!=null){ 
                                                //value+=thin.eventsArray[x0][y0].on; // should check on image dimension before
                                                if(thin.eventsArray[x0][y0].on==1){
                                                    last_x = x0;
                                                    last_y = y0;
                                                    value++;
                                                }
                                            }  
                                            onTheLine.add(new Point(x0,y0));
                                        } else {
                                            break;
                                        }
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
                    
                    // memorize max length
                     if(length>lengthMax){
                        lengthMax=length;
                    }
                    
                 //   xEnd = x0;
                 //   yEnd = y0;
                  //  xEnd = last_x;
                  //  yEnd = last_y;

                    
                    // if value above max, store
                    if(value>valueMax){
                        valueMax = value;
                    
                       // Line line = new Line(x,y,xEnd,yEnd,length);
                        Line line = new Line(x,y,last_x,last_y,length);
                    
                   
                        result = line;
                        visited = onTheLine;
                   } 
                    
                    
                } // end if on outline     
                }// end if within accepted orientation
              }          
           } //end for all points on square's outline
       
           // delete points if chosen so that further search will not trace the same line again
           for (Object v:visited){
               Point p = (Point)v;
               if(contour.eventsArray[p.x][p.y]!=null){
                     contour.eventsArray[p.x][p.y].on = 0;
               }
               
           }
          
       
      
        return result;
    }
    */
    
    /**
    // stops if touching shape or pixel below threshold
    protected Vector longestLines(int x, int y, int range, int midlength, int nb_lines_avg, float[][][] accEvents){
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
           int xEnd = 0;
           int yEnd = 0;
           int lengthMax = 0;
           int xMid = 0;
           int yMid = 0;
           boolean touchesDoor = false;
           // for all points in a square outline centered on x0,y0 with side size range+1/2
           for(int i=x-range;i<x+range+1;i++){
              for(int j=y-range;j<y+range+1;j++){
                if(((i<=x-range)||(i>=x+range))||((j<=y-range)||(j>=y+range))){
                    // on the square outline
                    x1 = i; 
                    y1 = j;
                    x0 = x;
                    y0 = y;                                      
                    int midx = 0;
                    int midy = 0;
                    boolean touching = false;
                    
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
                                if(accEvents[y0][x0][0]>threshold){
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(length==midlength){
                                            midx = x0;
                                            midy = y0;
                                            
                                        }
                                        // touching door?
                                        if(nearDoor(y0,x0,2)){
                                            touching = true;
                                            //break;
                                        }
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            length++;
                                            if(length==midlength){
                                                midx = x0;
                                                midy = y0;
                                                
                                            }
                                            // touching door?
                                            if(nearDoor(y0,x0,2)){
                                                touching = true;
                                                //break;
                                            }
                                        } else {
                                            break;
                                        }
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
                                if(accEvents[y0][x0][0]>threshold){
                                    // 3 : here is the area around the start point in which we dont check fo shape border, in case
                                    // of border noise // should parametrize
                                    if (contour.eventsArray[x0][y0]==null){
                                        length++;
                                        if(length==midlength){
                                            midx = x0;
                                            midy = y0;
                                            
                                        }
                                        // touching door?
                                        if(nearDoor(y0,x0,2)){
                                            touching = true;
                                            //break;
                                        }
                                    } else {
                                        if((length+1<3)||((contour.eventsArray[x0][y0].label!=1)
                                        ||(contour.eventsArray[x0][y0].on!=1))){//and check if within shape only after a few timestep
                                            
                                            length++;
                                            if(length==midlength){
                                                midx = x0;
                                                midy = y0;
                                            }
                                            // touching door?
                                            if(nearDoor(y0,x0,2)){
                                                touching = true;
                                                // break;
                                            }
                                        } else {
                                            break;
                                        }
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
                    
                    // memorize max length
                     if(length>lengthMax){
                        lengthMax=length;
                    }
                    
                    xEnd = x0;
                    yEnd = y0;

                    xMid = midx;
                    yMid = midy;
                    touchesDoor = touching;

                    //
                    // store all lines
                    Line line = new Line(x,y,xEnd,yEnd,length);
                    line.touchesDoor = touchesDoor;
                    line.midx = xMid;
                    line.midy = yMid;
                    lines.add(line);
                 //  } 
                    
                    
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
               
               
//               Line line = new Line(x,y,xEnd,yEnd,lengthMax);
//               line.touchesDoor = touchesDoor;
//               line.midx = xMid;
//               line.midy = yMid;
//               
//               lines.add(line);
//              
              
               
           }
           
          
       
      
        return lines;
    }
    
     */
    
    
    /*
    protected Line meanLineOf( Vector lines ){
        Line result = null;
    
        // return mean line from lines with same start point
        float avgOrientation = 0;
        float avgLength = 0;
        // for all lines find mean orientation
        Line line = null;
        for( Object o:lines ){
            line = (Line)o;
            avgOrientation += lineOrientation(line);
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
    */
    
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
    // only looking at first value, should change it to look at float[][] because last [] is not used actually
    protected float findArrayMax( float[][][] array ){
        float max = 0;
        for(int i=0;i<array.length;i++){
            for(int j=0;j<array[i].length;j++){
                float f = array[i][j][0];
                if (f>max) max = f;
                
            }
        }
        return max;
    }
    
    /*
    // follow Bresenham algorithm to incrase intentisy along a discrete line between two points
     protected void increaseIntentisyOfLine(int x0, int y0, int x1, int y1){
        
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
            
            
            // only increase r channel
            insideIntensities[x0][y0][0] = insideIntensities[x0][y0][0]+intensityIncrease;
            
            if (dx > dy) {
                int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
                while (x0 != x1) {
                    if (fraction >= 0) {
                        y0 += stepy;
                        fraction -= dx;                                // same as fraction -= 2*dx
                    }
                    x0 += stepx;
                    fraction += dy;                                    // same as fraction -= 2*dy
                    insideIntensities[x0][y0][0] = insideIntensities[x0][y0][0]+intensityIncrease;
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
                    insideIntensities[x0][y0][0] = insideIntensities[x0][y0][0]+intensityIncrease;
                }
            }
        } catch (Exception e ){
            //System.out.println("increaseIntentisyOfLine error x0="+x0+" y0="+y0+" x1="+x1+" y1="+y1+" x="+x+" y="+y);
            //e.printStackTrace();
            
        }
        
    }
     */
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
      void fastDualLowFilterFrame( EventPoint ep ){
            
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
                                influencedPoint.previousShortFilteredValue = influencedPoint.shortFilteredValue;
                                influencedPoint.shortFilteredValue += (ep.lastValue * f)/shortRangeTotal;
                                if (influencedPoint.shortFilteredValue<0) influencedPoint.shortFilteredValue = 0;
                                
                                // update border status
                                isOnPawShape(influencedPoint);
                                                                
                            }                            
                        }              
                    }
                }        
            }
        }              
          
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
        insideIntensityFrame=new JFrame("Inside intensity");
        insideIntensityFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        insideIntensityCanvas=new GLCanvas();
        
        insideIntensityCanvas.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent evt) {
                    
                    int dx=insideIntensityCanvas.getWidth()-1;
                    int dy=insideIntensityCanvas.getHeight()-1;
        
                    // 4 is window's border width
//                    int x = Math.round((evt.getX()-4)  / intensityZoom);
//                    int y = Math.round((dy-evt.getY())  / intensityZoom);
                    
                    int x = (int)((evt.getX()-3)  / intensityZoom);
                    int y = (int)((dy-evt.getY())  / intensityZoom);
                     
                  
                    System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");
                    System.out.println("width=" + dx + " heigt="+dy);
                    
               
                    
                    highlight_x = x;
                    highlight_y = y;
                    if (evt.getButton()==1){
                        highlight = true;
                       // GL gl=insideIntensityCanvas.getGL();
                       
                       
                    } else {
                        highlight = false;
                    }
               
                    if(x>=0&&x<retinaSize&&y>=0&&y<retinaSize){
                        float val = 0;
                       
                        EventPoint ep = eventPoints[x][y];
                        
                        if(showScore){
                            //val = scoresFrame[x][y][0]/scoresFrameMax;
                            val = ep.intensity;//*ep.shortFilteredValue;
                            System.out.println("Selected pixel x,y=" + x + "," + y + " score value="+val);
                           
                        } else 
                         
                        
                         if(showAcc){
                            
                            if(showOnlyAcc){
                                val = ep.accValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " acc value="+val);
                              
                            } else if(showSecondFilter){
                                val = ep.largeFilteredValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " 2d filter value="+val);
                              
                            } else {
                                val = ep.shortFilteredValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " 1st filter value="+val);
                               
                            }
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
            
            synchronized public void display(GLAutoDrawable drawable) {
               
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
              
                
                // display inside intensity
                
                
              if(showScore){  
                    
                //float max = findArrayMax(scoresFrame);
               for (int i = 0; i<eventPoints.length; i++){
                        for (int j = 0; j<eventPoints[i].length; j++){
                         EventPoint ep = eventPoints[i][j];
                         float f=0;
                         float r = 0;
                         
                       if(ep.shortFilteredValue>in_value_threshold){
                         if(ep.intensity*ep.shortFilteredValue>intensity_threshold){
                            r = 1;
                         } else {
                            f = ep.intensity*ep.shortFilteredValue; 
                         }
                       }
                        
                        // f is scaled between 0-1
                        gl.glColor3f(f+r,0,f);
                        gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                        
                        
                    }
                }
                
             } else if(showAcc){
                    
                   // float max = findArrayMax(insideIntensities);
                    
                     for (int i = 0; i<eventPoints.length; i++){
                        for (int j = 0; j<eventPoints[i].length; j++){
                            EventPoint ep = eventPoints[i][j];
                            float f=0;
                            float g=0;
                            
                           
                               
                            if(showSecondFilter){
                                if(showTopography)
                                f = obtainedDensity(ep.largeFilteredValue,lowFilter_density2,invDensity2,densities2);
                                else  f = ep.largeFilteredValue;
                                
                            } else if(showOnlyAcc){
                                f = ep.accValue;
                            } else {
                                //f = ep.shortFilteredValue;
                                if(showScore)
                                f = obtainedDensity(ep.shortFilteredValue,lowFilter_density,invDensity1,densities);
                                else f = ep.shortFilteredValue;
                            }
                             
                            if(showShapePoints){
                                if(ep.border) g = 1-f;
                                
                            }
                           
                            
                            gl.glColor3f(f,f,f+g);
                            gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                            
                        }
                     }                                        
                    
                    
               } else {
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
                  gl.glColor3f(0,1,0);
                drawBox(gl,door_ya*intensityZoom,door_yb*intensityZoom,door_xa*intensityZoom,door_xb*intensityZoom);
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

               
                
                if(showFingers){
                    /*
                    // draw fingers here
                   gl.glColor3f(1,0,0);
                    //gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
                    
                    for(int i=0; i<fingerLines.size(); i++){
                        try {
                        Line line = (Line)fingerLines.elementAt(i);
                        gl.glBegin(GL.GL_LINES);
                        {
                            if(line.midx==0&&line.midy==0){
                                gl.glVertex2i(line.x0*intensityZoom,line.y0*intensityZoom);
                                gl.glVertex2f(line.x1*intensityZoom,line.y1*intensityZoom);
                            } else {
                                gl.glVertex2i(line.x0*intensityZoom,line.y0*intensityZoom);
                                gl.glVertex2f(line.midx*intensityZoom,line.midy*intensityZoom);
                                gl.glVertex2i(line.midx*intensityZoom,line.midy*intensityZoom);
                                gl.glVertex2f(line.x1*intensityZoom,line.y1*intensityZoom);
                            }
                        }
                        gl.glEnd();
                        } catch (ArrayIndexOutOfBoundsException e){
                            // it's ok do nothing, problem of thred sync maybe but no consequences
                        }
                    }  
                    
                   
                     */
                   
                    //gl.glBlendFunc(GL.GL_ONE, GL.GL_ZERO);
                }   // end if show fingers  
                if(showFingerTips){
                    /*
                    if(grasp_started){
                        
                        gl.glColor3f(1,0,0);
                        for(int i=0;i<fingerTips.size();i++){
                            Point sc = (Point)fingerTips.elementAt(i);
                            gl.glRectf(sc.x*intensityZoom,sc.y*intensityZoom,(sc.x+1)*intensityZoom,(sc.y+1)*intensityZoom);
                            
                            
                            
                            gl.glRasterPos3f((sc.x+3)*intensityZoom,sc.y*intensityZoom,0);
                            
                            chip.getCanvas().getGlut().glutBitmapString(font, String.format("%d", sc.score ));
                        }
                    }
                     **/
                }
                
                if(highlight){
                    gl.glColor3f(1,1,0);
                    gl.glRectf(highlight_x*intensityZoom,highlight_y*intensityZoom,(highlight_x+1)*intensityZoom,(highlight_y+1)*intensityZoom);

                }
                
                /**
                if(showClusters){
                     gl.glColor3f(0,0,1);
                         for(int i=0;i<fingerTipClusters.length;i++){
                             FingerCluster fc = fingerTipClusters[i];
                             gl.glRectf(fc.x*intensityZoom,(fc.y+1)*intensityZoom,(fc.x+1)*intensityZoom,(fc.y+2)*intensityZoom);
                             
                             
                             // finger lines
                             if(fc.finger_base_x!=0&&fc.finger_base_y!=0){
                                 gl.glBegin(GL.GL_LINES);
                                 {
                                     //gl.glColor3f(0,0,1);
                                     gl.glVertex2i(fc.x*intensityZoom,fc.y*intensityZoom);
                                     gl.glVertex2f(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                                     
                                 }
                                 gl.glEnd();
                             }
                             
                             // knuckles polygon
                             if(knuckle_polygon_created){
                                 gl.glBegin(GL.GL_LINES);
                                 {
                                     //gl.glColor3f(0,0,1);
                                    if(fc.neighbour1!=null){
                                     gl.glVertex2i(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                                     gl.glVertex2f(fc.neighbour1.finger_base_x*intensityZoom,fc.neighbour1.finger_base_y*intensityZoom);
                                    }
                                 }
                                 gl.glEnd();
                                 gl.glBegin(GL.GL_LINES);
                                 {
                                     //gl.glColor3f(0,0,1);
                                     if(fc.neighbour2!=null){
                                     gl.glVertex2i(fc.finger_base_x*intensityZoom,fc.finger_base_y*intensityZoom);
                                     gl.glVertex2f(fc.neighbour2.finger_base_x*intensityZoom,fc.neighbour2.finger_base_y*intensityZoom);
                                     }
                                 }
                                 gl.glEnd();
                                 
                                 
                                 
                             }
                         }      
                }
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
    
    // class Node
    public class Node{
        public int x;
        public int y;
        public int type; // change to enum?
        public int support=0;
        // + connectivity?
        
        public Node(){}
        
        public Node( int x, int y, int type){
            this.x = x;
            this.y = y;
            this.type = type;
            
        }
        
        public Node( ContourPoint cp, int type){
            if(cp!=null){
                this.x = cp.x;
                this.y = cp.y;
            
                this.type = type;
            }
        }
          
      void measureSupport( Contour image ){
            //Contour image = new Contour(contour);
            int max_support = 20;
            support=0;
            int prev_support=-1;
            int cur_x = x;
            int cur_y = y;
            while(support<max_support&&support>prev_support){
              // check neighbours
              prev_support++;
              search:
              for(int i=cur_x-1;i<=cur_x+1;i++){
                  if(i>0&&i<image.maxX){
                      for(int j=cur_y-1;j<=cur_y+1;j++){
                            if(j>0&&j<image.maxY){
                                if (image.eventsArray[i][j]!=null){
                                if(image.eventsArray[i][j].on==1){
                                    support++;
                                    image.eventsArray[i][j].on=0;
                                    cur_x = i;
                                    cur_y = j;
                                    break search;
                                }
                                }
                            }
                      }
                  } 
              } // search
            
            }    // end while      
        } // end measureSupport
  
    
    }
    
    // class ContourPoint
    public class ContourPoint{
        public int maxX=retinaSize,maxY=retinaSize;
        public int label;
        public int x=0;
        public int y=0;
        public int on=0;
        public int used=0;
        
       
        
        public ContourPoint(){
           // reset();
        }
        
        public ContourPoint( ContourPoint cp ){
            if(cp!=null){
                this.label = cp.label;
                this.x = cp.x;
                this.y = cp.y;
                this.on = cp.on;
            }
            //this.used = cp.used; maybe unwanted
        }
        
        
        
//        public void reset(){
//            label = 0;
//            on = 0;
//            used = 0;
//        }
        
    }
    // end class ContourPoint
    
    // class Contour
    public class Contour{
        ContourPoint eventsArray[][];
        protected int maxX=retinaSize,maxY=retinaSize;
        public int nbLabels=0;
        
//        public int max_inside_x=0;
//        public int max_inside_y=0;
//        public int min_inside_x=0;
//        public int min_inside_y=0;
//        
        public Contour(){
            eventsArray = new ContourPoint[maxX][maxY];
            ////reset();
            
        }
        
        public Contour( Contour contour ){ // only if contour same size, but all contour share global size (ugly)
            eventsArray = new ContourPoint[maxX][maxY];
             
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    if(contour.eventsArray[i][j]!=null){
                        eventsArray[i][j] = new ContourPoint(contour.eventsArray[i][j]);
                    }
                    
                }
            }
           
        }
        
        public Contour( int[][][] img, int label){
            eventsArray = new ContourPoint[maxX][maxY];
            //reset();
            // should check on img.length < maxX ...
            for (int i=0;i<img.length;i++){
                for (int j=0;j<img[i].length;j++){
                    if(img[i][j][0]==1){
                        // inverted x,y (? why?)
                        add(j,i,label);
                    }         
                }
            }    
        }
        
        
        public void reset(){
            // should optimize that
            
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    eventsArray[i][j] = new ContourPoint();
                    
                }
            }
            
        }
        
        
        public void add(int i, int j){
            eventsArray[i][j] = new ContourPoint();
            eventsArray[i][j].on = 1;
            eventsArray[i][j].x = i;
            eventsArray[i][j].y = j;
            
        }
        
        public void add(int i, int j, int label){
            eventsArray[i][j] = new ContourPoint();
            eventsArray[i][j].on = 1;
            eventsArray[i][j].x = i;
            eventsArray[i][j].y = j;
             eventsArray[i][j].label = label;
        }
        
        /**
         * Set label of points to 1 if group touching door zone
         */
        public void highlightTouchingDoor(){
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    if(eventsArray[i][j]!=null){
                    if(eventsArray[i][j].on == 1){
                        // if touch door
                        if (nearDoor(eventsArray[i][j].y,eventsArray[i][j].x,linkSize)){
                            // set label to 1
                            changeLabel(eventsArray[i][j].label,1);
                            //set max/min x,y
//                            if(max_inside_x<i) max_inside_x=i;
//                            if(min_inside_x==0||min_inside_x>i) min_inside_x=i;
//                            if(max_inside_y<j) max_inside_y=j;
//                            if(min_inside_y==0||min_inside_y>j) min_inside_y=j;
//                            
                            //eventsArray[i][j].label = 1;
                            //System.out.println("near door "+eventsArray[i][j].x+","+eventsArray[i][j].y);
                        } else {
                           // System.out.println("not near door "+eventsArray[i][j].x+","+eventsArray[i][j].y);
                        }
                        
                        if (hideInside){
                        // if disable points inside door
                         if (insideDoor(eventsArray[i][j].y,eventsArray[i][j].x)){
                            eventsArray[i][j].on = 0;
                         }
                        }
                        
                    }
                    }
                }
            }
            
        }
        
      
        
        /** link, group contour points into linked groups by
         * assigning group label to each point
         *
         */
        public void link(){
            // for all points
            //    System.out.println("################### Link");
            int n = 1;
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    if(eventsArray[i][j]!=null){
                        if(eventsArray[i][j].on == 1){
                            
                            // look for neihgbour
                            // System.out.println("findNeighbours("+i+","+j+","+linkSize+")");
                            if (eventsArray[i][j].label==0){
                                n++; //increase label
                                eventsArray[i][j].label=n;
                            }
                            findNeighbours(i,j,linkSize);
                            
                        }
                    }
                    
                }
            }
            nbLabels = n;
        }
        
       protected void extendSegmentFollowingPolicy( Segment segment, ContourPoint p , int min_x_policy, int max_x_policy, int min_y_policy, int max_y_policy, int curSegSize, boolean large ){         
           
           // look for neighbours in range allowed by policy
           //ContourPoint neighbour = contour.getNeighbour(p.x,p.y,min_x_policy,max_x_policy,min_y_policy,max_y_policy,large);
           ContourPoint neighbour = new  ContourPoint();
           
           // if none return             
           if(neighbour==null) return;
           if(curSegSize>=maxSegSize) return;
       
           // else 
           
           
           // add point to segment
           //neighbour.used = 1;
           p.used = 1; //thus do not set last point as used, it will be the start of another segment
           segment.x2 = neighbour.x;
           segment.y2 = neighbour.y;
           
           // recursive call
           
           extendSegmentFollowingPolicy(segment,neighbour,min_x_policy,max_x_policy,min_y_policy,max_y_policy,curSegSize+1,large);
           
          
           
       }
        
        
        
        // return one neighbour
       
       protected ContourPoint getNeighbour(int x, int y, int range ){
           return getNeighbour(x,y,range,range,range,range,false);
                 
       }
       
       protected ContourPoint getNeighbour(int x, int y, int range, boolean large ){
           return getNeighbour(x,y,range,range,range,range,large);
                 
       }
        
       protected ContourPoint getNeighbour(int x, int y, int low_xrange, int high_xrange, int low_yrange, int high_yrange){
            return getNeighbour(x,y,low_xrange,high_xrange,low_yrange,high_yrange,false);
       }
       protected ContourPoint getNeighbour(int x, int y, int low_xrange, int high_xrange, int low_yrange, int high_yrange, boolean large){
           int a = 0;
           if(large) a = 1;
           
           for (int i=x-low_xrange;i<x+high_xrange+a;i++){ //shortend range
                if (i>0&&i<maxX){                  
                    for (int j=y-low_yrange;j<y+high_yrange+a;j++){
                        if (j>0&&j<maxY){
                            if(i==x&&j==y){
                                
                            } else {
                                if(eventsArray[i][j]!=null){
                                if(eventsArray[i][j].on == 1){
                                    if(eventsArray[i][j].used == 0){
                                        return eventsArray[i][j];
                                    }
                                }
                                }
                            }
                        }
                    }
                }
             }             
             return null;            
         }
       
       protected Vector getNeighbours(int x, int y, int range ){
           return getNeighbours(x,y,range,range,range,range);
           
           
       }
        protected Vector getNeighbours(int x, int y, int low_xrange, int high_xrange, int low_yrange, int high_yrange){
            Vector neighbours = new Vector();
            
            for (int i=x-low_xrange;i<=x+high_xrange;i++){
                if (i>0&&i<maxX){                  
                    for (int j=y-low_yrange;j<=y+high_yrange;j++){
                        if (j>0&&j<maxY){
                            if(i==x&&j==y){
                                
                            } else {
                                if(eventsArray[i][j]!=null){
                                if(eventsArray[i][j].on == 1){
                                    if(eventsArray[i][j].used == 0){
                                        neighbours.add(eventsArray[i][j]);
                                    }
                                }
                                }
                            }
                        }
                    }
                }
             }             
             return neighbours;            
         }
                
        
        // find all neighbours and label points as neighbours
        protected void findNeighbours(int x, int y, int around){
            for (int i=x-around;i<=x+around;i++){
                if (i>0&&i<maxX){
                    
                    for (int j=y-around;j<=y+around;j++){
                        if (j>0&&j<maxY){
                            if(i==x&&j==y){
                                
                            } else {
                              if(eventsArray[i][j]!=null){
                                if(eventsArray[i][j].on == 1){
                                    addAsNeighbours(eventsArray[x][y],eventsArray[i][j]);
                                }
                              }
                            }
                            
                        }
                        
                    }//end for j
                }
            }//end for i
        }
        
        public void addAsNeighbours( ContourPoint c1, ContourPoint c2 ){
            
            if (c1.label!=c2.label){
                
                if(c2.label==0){
                    c2.label = c1.label;
                } else {
                    int lc2 = c2.label;
                    // System.out.println("change all "+c2.label+" labels into "+c1.label);
                    changeLabel(lc2,c1.label);
//                       for (int i=0;i<maxX;i++){
//                            for (int j=0;j<maxY;j++){
//
//                                    if(eventsArray[i][j].label==lc2){
//                                         eventsArray[i][j].label = c1.label;
//                                    }
//
//                            }
//                       }
                    //c2.label = c1.label;
                }
                
                
            }
            
        }
        
        protected void changeLabel( int l2, int l1){
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    //if(i==x&&j==y){
                    //} else {
                    if(eventsArray[i][j]!=null){
                    if(eventsArray[i][j].label==l2){
                        eventsArray[i][j].label = l1;
                    }
                    }
                    // }
                }
            }
        }
        
        public int getMaxX(){
            return maxX;
        }
        public int getMaxY(){
            return maxY;
        }
        
        
        
    }
    // end class Contour
    
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
        checkInsideIntensityFrame();
        track(in);
        if (showWindow) insideIntensityCanvas.repaint();
        return in;
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
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTracker3.annotate");
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
                drawBox(gl,door_ya,door_yb,door_xa,door_xb);
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
//        getPrefs().putFloat("PawTracker3.in_threshold",in_threshold);
//    }
//    public float getIn_threshold() {
//        return in_threshold;
//    }
    
    public void setLine_threshold(float line_threshold) {
        this.line_threshold = line_threshold;
        getPrefs().putFloat("PawTracker3.line_threshold",line_threshold);
    }
    public float getLine_threshold() {
        return line_threshold;
    }
    
    public void setLine_range(int line_range) {
        this.line_range = line_range;
        getPrefs().putInt("PawTracker3.line_range",line_range);
    }
    public int getLine_range() {
        return line_range;
    }
    
    public void setLines_n_avg(int lines_n_avg) {
        this.lines_n_avg = lines_n_avg;
        getPrefs().putInt("PawTracker3.lines_n_avg",lines_n_avg);
    }
    public int getLines_n_avg() {
        return lines_n_avg;
    }
    
            
            
   
     
    
            
    public void setCluster_lifetime(int cluster_lifetime) {
        this.cluster_lifetime = cluster_lifetime;
        getPrefs().putInt("PawTracker3.cluster_lifetime",cluster_lifetime);
    }
    public int getCluster_lifetime() {
        return cluster_lifetime;
    }      
            
    public void setScore_range(int score_range) {
        this.score_range = score_range;
        getPrefs().putInt("PawTracker3.score_range",score_range);
    }
    public int getScore_range() {
        return score_range;
    }
    
    public void setScore_threshold(int score_threshold) {
        this.score_threshold = score_threshold;
        getPrefs().putInt("PawTracker3.score_threshold",score_threshold);
    }
    public int getScore_threshold() {
        return score_threshold;
    }
    
    public void setScore_in_threshold(float score_in_threshold) {
        this.score_in_threshold = score_in_threshold;
        getPrefs().putFloat("PawTracker3.score_in_threshold",score_in_threshold);
    }
    public float getScore_in_threshold() {
        return score_in_threshold;
    }
    
    public void setScore_sup_threshold(float score_sup_threshold) {
        this.score_sup_threshold = score_sup_threshold;
        getPrefs().putFloat("PawTracker3.score_sup_threshold",score_sup_threshold);
    }
    public float getScore_sup_threshold() {
        return score_sup_threshold;
    }
       
   
    
   public void setLine2shape_thresh(float line2shape_thresh) {
        this.line2shape_thresh = line2shape_thresh;
        getPrefs().putFloat("PawTracker3.line2shape_thresh",line2shape_thresh);
    }
    public float getLine2shape_thresh() {
        return line2shape_thresh;
    }
    
    
           
   
    
    public void setShapeLimit(float shapeLimit) {
        this.shapeLimit = shapeLimit;
        getPrefs().putFloat("PawTracker3.shapeLimit",shapeLimit);
    }
    public float getShapeLimit() {
        return shapeLimit;
    }
    
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        getPrefs().putInt("PawTracker3.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }    
    
    
            
    public void setIn_length(int in_length) {
        this.in_length = in_length;
        getPrefs().putInt("PawTracker3.in_length",in_length);
    }
    
    public int getIn_length() {
        return in_length;
    }   
    
    public void setIn_test_length(int in_test_length) {
        this.in_test_length = in_test_length;
        getPrefs().putInt("PawTracker3.in_test_length",in_test_length);
    }
    
    public int getIn_test_length() {
        return in_test_length;
    }   
    
    public void setDoor_xa(int door_xa) {
        this.door_xa = door_xa;
        getPrefs().putInt("PawTracker3.door_xa",door_xa);
    }
    
    public int getDoor_xa() {
        return door_xa;
    }
    
    public void setDoor_xb(int door_xb) {
        this.door_xb = door_xb;
        getPrefs().putInt("PawTracker3.door_xb",door_xb);
    }
    
    public int getDoor_xb() {
        return door_xb;
    }
    
    public void setDoor_ya(int door_ya) {
        this.door_ya = door_ya;
        getPrefs().putInt("PawTracker3.door_ya",door_ya);
    }
    
    public int getDoor_ya() {
        return door_ya;
    }
    
    public void setDoor_yb(int door_yb) {
        this.door_yb = door_yb;
        getPrefs().putInt("PawTracker3.door_yb",door_yb);
    }
    
    public int getDoor_yb() {
        return door_yb;
    }
    
    
    public void setNode_range(float node_range) {
        this.node_range = node_range;
        getPrefs().putFloat("PawTracker3.node_range",node_range);
    }
    public float getNode_range() {
        return node_range;
    } 
    
    
           
            
    public void setFinger_sensitivity(float finger_sensitivity) {
        this.finger_sensitivity = finger_sensitivity;
        getPrefs().putFloat("PawTracker3.finger_sensitivity",finger_sensitivity);
    }
    public float getFinger_sensitivity() {
        return finger_sensitivity;
    } 
    
    public void setFinger_mv_smooth(float finger_mv_smooth) {
        this.finger_mv_smooth = finger_mv_smooth;
        getPrefs().putFloat("PawTracker3.finger_mv_smooth",finger_mv_smooth);
    }
    public float getFinger_mv_smooth() {
        return finger_mv_smooth;
    } 
    
    public void setFinger_length(float finger_length) {
        this.finger_length = finger_length;
        getPrefs().putFloat("PawTracker3.finger_length",finger_length);
    }
    public float getFinger_length() {
        return finger_length;
    } 
    
    public void setFinger_start_threshold(int finger_start_threshold) {
        this.finger_start_threshold = finger_start_threshold;
        getPrefs().putInt("PawTracker3.finger_start_threshold",finger_start_threshold);
    }
    public int getFinger_start_threshold() {
        return finger_start_threshold;
    }       
            
  
    
    public void setFinger_cluster_range(float finger_cluster_range) {
        this.finger_cluster_range = finger_cluster_range;
        getPrefs().putFloat("PawTracker3.finger_cluster_range",finger_cluster_range);
    }
    public float getFinger_cluster_range() {
        return finger_cluster_range;
    }  
    
    public void setFinger_ori_variance(float finger_ori_variance) {
        this.finger_ori_variance = finger_ori_variance;
        getPrefs().putFloat("PawTracker3.finger_ori_variance",finger_ori_variance);
    }
    public float getFinger_ori_variance() {
        return finger_ori_variance;
    }  
    
   
    public void setPalmback_below(float palmback_below) {
        this.palmback_below = palmback_below;
        getPrefs().putFloat("PawTracker3.palmback_below",palmback_below);
    }
    public float getPalmback_below() {
        return palmback_below;
    }
    public void setPalmback_value(float palmback_value) {
        this.palmback_value = palmback_value;
        getPrefs().putFloat("PawTracker3.palmback_value",palmback_value);
    }
    public float getPalmback_value() {
        return palmback_value;
    }
    public void setPalmback_distance(int palmback_distance) {
        this.palmback_distance = palmback_distance;
        getPrefs().putInt("PawTracker3.palmback_distance",palmback_distance);
    }
    public int getPalmback_distance() {
        return palmback_distance;
    }
    
    public void setIntensity_range(int intensity_range) {
        this.intensity_range = intensity_range;
        getPrefs().putInt("PawTracker3.intensity_range",intensity_range);
    }
    public int getIntensity_range() {
        return intensity_range;
    }
    
    
      
    public void setIn_value_threshold(float in_value_threshold) {
        this.in_value_threshold = in_value_threshold;
        getPrefs().putFloat("PawTracker3.in_value_threshold",in_value_threshold);
    }
     public float getIn_value_threshold() {
        return in_value_threshold;
    }
    
    public void setIntensity_threshold(float intensity_threshold) {
        this.intensity_threshold = intensity_threshold;
        getPrefs().putFloat("PawTracker3.intensity_threshold",intensity_threshold);
    }
    public float getIntensity_threshold() {
        return intensity_threshold;
    }
            
    public void setIntensity_strength(float intensity_strength) {
        this.intensity_strength = intensity_strength;
        getPrefs().putFloat("PawTracker3.intensity_strength",intensity_strength);
    }
    public float getIntensity_strength() {
        return intensity_strength;
    }
    
    
    public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
        getPrefs().putFloat("PawTracker3.event_strength",event_strength);
    }
    public float getEvent_strength() {
        return event_strength;
    }

    public void setContour_min_thresh(float contour_min_thresh) {
        this.contour_min_thresh = contour_min_thresh;
        getPrefs().putFloat("PawTracker3.contour_min_thresh",contour_min_thresh);
    }
    public float getContour_min_thresh() {
        return contour_min_thresh;
    }  
    
    public void setContour_act_thresh(float contour_act_thresh) {
        this.contour_act_thresh = contour_act_thresh;
        getPrefs().putFloat("PawTracker3.contour_act_thresh",contour_act_thresh);
    }
    public float getContour_act_thresh() {
        return contour_act_thresh;
    }  
      
    public void setContour_range(int contour_range) {
        this.contour_range = contour_range;
        getPrefs().putInt("PawTracker3.contour_range",contour_range);
    }
    public int getContour_range() {
        return contour_range;
    }
    
    
            
    public void setTracker_time_bin(float tracker_time_bin) {
        this.tracker_time_bin = tracker_time_bin;
        getPrefs().putFloat("PawTracker3.tracker_time_bin",tracker_time_bin);
    }
    public float getTracker_time_bin() {
        return tracker_time_bin;
    }         
            
    public void setShapeThreshold(float shapeThreshold) {
        this.shapeThreshold = shapeThreshold;
        getPrefs().putFloat("PawTracker3.shapeThreshold",shapeThreshold);
    }
    public float getShapeThreshold() {
        return shapeThreshold;
    }
    
    public void setDoorMinDiff(float doorMinDiff) {
        this.doorMinDiff = doorMinDiff;
        getPrefs().putFloat("PawTracker3.doorMinDiff",doorMinDiff);
    }
    public float getDoorMinDiff() {
        return doorMinDiff;
    }
    
    public void setDoorMaxDiff(float doorMaxDiff) {
        this.doorMaxDiff = doorMaxDiff;
        getPrefs().putFloat("PawTracker3.doorMaxDiff",doorMaxDiff);
    }
    public float getDoorMaxDiff() {
        return doorMaxDiff;
    }
    
    public void setMinZeroes(int minZeroes) {
        this.minZeroes = minZeroes;
        getPrefs().putInt("PawTracker3.minZeroes",minZeroes);
    }
    public int getMinZeroes() {
        return minZeroes;
    }
    
    public void setMaxZeroes(int maxZeroes) {
        this.maxZeroes = maxZeroes;
        getPrefs().putInt("PawTracker3.maxZeroes",maxZeroes);
    }
    
    public int getMaxZeroes() {
        return maxZeroes;
    }
    
   
    
  
    
//    public void setDoorMinZeroes(int doorMinZeroes) {
//        this.doorMinZeroes = doorMinZeroes;
//        getPrefs().putInt("PawTracker3.doorMinZeroes",doorMinZeroes);
//    }
//    public int getDoorMinZeroes() {
//        return doorMinZeroes;
//    }
//    
//    public void setDoorMaxZeroes(int doorMaxZeroes) {
//        this.doorMaxZeroes = doorMaxZeroes;
//        getPrefs().putInt("PawTracker3.doorMaxZeroes",doorMaxZeroes);
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
//        getPrefs().putInt("PawTracker3.retinaSize",retinaSize);
//        
//    }
    
    public int getLinkSize() {
        return linkSize;
    }
    
    public void setLinkSize(int linkSize) {
        this.linkSize = linkSize;
        getPrefs().putInt("PawTracker3.linkSize",linkSize);
    }
    
    
    public int getBoneSize() {
        return boneSize;
    }
    
    public void setBoneSize(int boneSize) {
        this.boneSize = boneSize;
        getPrefs().putInt("PawTracker3.boneSize",boneSize);
    }
    
    public int getSegSize() {
        return segSize;
    }
    
    public void setSegSize(int segSize) {
        this.segSize = segSize;
        getPrefs().putInt("PawTracker3.segSize",segSize);
    }
    
    public int getMaxSegSize() {
        return maxSegSize;
    }
    
    public void setMaxSegSize(int maxSegSize) {
        this.maxSegSize = maxSegSize;
        getPrefs().putInt("PawTracker3.maxSegSize",maxSegSize);
    }
    
            
   
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        getPrefs().putBoolean("PawTracker3.resetPawTracking",resetPawTracking);
        
    }
    
    public boolean isValidateParameters() {
        return validateParameters;
    }
    public void setValidateParameters(boolean validateParameters) {
        this.validateParameters = validateParameters;
        getPrefs().putBoolean("PawTracker3.validateParameters",validateParameters);
        
    }
    
    
    
    public void setUseFingerDistanceSmooth(boolean useFingerDistanceSmooth){
        this.useFingerDistanceSmooth = useFingerDistanceSmooth;
        getPrefs().putBoolean("PawTracker3.useFingerDistanceSmooth",useFingerDistanceSmooth);
    }
    public boolean isUseFingerDistanceSmooth(){
        return useFingerDistanceSmooth;
    }
    
    public void setUseSimpleContour(boolean useSimpleContour){
        this.useSimpleContour = useSimpleContour;
        getPrefs().putBoolean("PawTracker3.useSimpleContour",useSimpleContour);
    }
    public boolean isUseSimpleContour(){
        return useSimpleContour;
    }
    
    
    public void setShowSkeletton(boolean showSkeletton){
        this.showSkeletton = showSkeletton;
        getPrefs().putBoolean("PawTracker3.showSkeletton",showSkeletton);
    }
    public boolean isShowSkeletton(){
        return showSkeletton;
    }
    
    public void setShowSecondFilter(boolean showSecondFilter){
        this.showSecondFilter = showSecondFilter;
        getPrefs().putBoolean("PawTracker3.showSecondFilter",showSecondFilter);
    }
    public boolean isShowSecondFilter(){
        return showSecondFilter;
    }
    
    public void setShowTopography(boolean showTopography){
        this.showTopography = showTopography;
        getPrefs().putBoolean("PawTracker3.showTopography",showTopography);
    }
    public boolean isShowTopography(){
        return showTopography;
    }
    
    
    
    
    public void setShowPalm(boolean showPalm){
        this.showPalm = showPalm;
        getPrefs().putBoolean("PawTracker3.showPalm",showPalm);
    }
    public boolean isShowPalm(){
        return showPalm;
    }       
            
    public void setShowThin(boolean showThin){
        this.showThin = showThin;
        getPrefs().putBoolean("PawTracker3.showThin",showThin);
    }
    public boolean isShowThin(){
        return showThin;
    }
     public void setThinning(boolean thinning){
        this.thinning = thinning;
        getPrefs().putBoolean("PawTracker3.thinning",thinning);
    }
    public boolean isThinning(){
        return thinning;
    }
    
     public float getThin_Threshold() {
        return thin_threshold;
    }
    
    public void setThin_Threshold(float thin_threshold) {
        this.thin_threshold = thin_threshold;
        getPrefs().putFloat("PawTracker3.thin_threshold",thin_threshold);
    }
    
    
    
    public void setShowSegments(boolean showSegments){
        this.showSegments = showSegments;
        getPrefs().putBoolean("PawTracker3.showSegments",showSegments);
    }
    public boolean isShowSegments(){
        return showSegments;
    }
    
    
     public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        getPrefs().putBoolean("PawTracker3.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
      
    public void setScaleIntensity(boolean scaleIntensity){
        this.scaleIntensity = scaleIntensity;
        getPrefs().putBoolean("PawTracker3.scaleIntensity",scaleIntensity);
    }
    public boolean isScaleIntensity(){
        return scaleIntensity;
    }
    
    public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        getPrefs().putBoolean("PawTracker3.showAcc",showAcc);
    }
    public boolean isShowAcc(){
        return showAcc;
    }
 
    public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
        getPrefs().putBoolean("PawTracker3.showOnlyAcc",showOnlyAcc);
    }
    public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
    public int getDensityMinIndex() {
        return densityMinIndex;
    }
    
    public void setDensityMinIndex(int densityMinIndex) {
        this.densityMinIndex = densityMinIndex;
        getPrefs().putInt("PawTracker3.densityMinIndex",densityMinIndex);
    }
    
    public int getDensityMaxIndex() {
        return densityMaxIndex;
    }
    
    public void setDensityMaxIndex(int densityMaxIndex) {
        this.densityMaxIndex = densityMaxIndex;
        getPrefs().putInt("PawTracker3.densityMaxIndex",densityMaxIndex);
    }
    
    public void setShowDensity(boolean showDensity){
        this.showDensity = showDensity;
        getPrefs().putBoolean("PawTracker3.showDensity",showDensity);
    }
    public boolean isShowDensity(){
        return showDensity;
    } 
    
    public void setScaleInDoor(boolean scaleInDoor){
        this.scaleInDoor = scaleInDoor;
        getPrefs().putBoolean("PawTracker3.scaleInDoor",scaleInDoor);
    }
    public boolean isScaleInDoor(){
        return scaleInDoor;
    } 
    
            
            
    public int getDecayLimit() {
        return decayLimit;
    }
    
    public void setDecayLimit(int decayLimit) {
        this.decayLimit = decayLimit;
        getPrefs().putInt("PawTracker3.decayLimit",decayLimit);
    }
    
    public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
        getPrefs().putBoolean("PawTracker3.decayOn",decayOn);
    }
    public boolean isDecayOn(){
        return decayOn;
    } 
    
    public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        getPrefs().putBoolean("PawTracker3.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
    
    
    public void setShowScore(boolean showScore){
        this.showScore = showScore;
        getPrefs().putBoolean("PawTracker3.showScore",showScore);
    }
    public boolean isShowScore(){
        return showScore;
    }       
    
    
    public void setUseIntensity(boolean useIntensity){
        this.useIntensity = useIntensity;
        getPrefs().putBoolean("PawTracker3.useIntensity",useIntensity);
    }
    public boolean isUseIntensity(){
        return useIntensity;
    } 
    
    public void setHideInside(boolean hideInside){
        this.hideInside = hideInside;
        getPrefs().putBoolean("PawTracker3.hideInside",hideInside);
    }
    public boolean isHideInside(){
        return hideInside;
    }      
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        getPrefs().putBoolean("PawTracker3.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
    public void setShowClusters(boolean showClusters){
        this.showClusters = showClusters;
        getPrefs().putBoolean("PawTracker3.showClusters",showClusters);
    }
    public boolean isShowClusters(){
        return showClusters;
    }      
            
    public void setShowFingerTips(boolean showFingerTips){
        this.showFingerTips = showFingerTips;
        getPrefs().putBoolean("PawTracker3.showFingerTips",showFingerTips);
    }
    public boolean isShowFingerTips(){
        return showFingerTips;
    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
        getPrefs().putBoolean("PawTracker3.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
        getPrefs().putBoolean("PawTracker3.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
    
       
//    public void setShowSequences(boolean showSequences){
//        this.showSequences = showSequences;
//        getPrefs().putBoolean("PawTracker3.showSequences",showSequences);
//    }
//    public boolean isShowSequences(){
//        return showSequences;
//    }
    
    public void setShowShape(boolean showShape){
        this.showShape = showShape;
        getPrefs().putBoolean("PawTracker3.showShape",showShape);
    }
    public boolean isShowShape(){
        return showShape;
    }
    
    
    public void setShowShapePoints(boolean showShapePoints){
        this.showShapePoints = showShapePoints;
        getPrefs().putBoolean("PawTracker3.showShapePoints",showShapePoints);
    }
    public boolean isShowShapePoints(){
        return showShapePoints;
    }
    public void setSmoothShape(boolean smoothShape){
        this.smoothShape = smoothShape;
        getPrefs().putBoolean("PawTracker3.smoothShape",smoothShape);
    }
    public boolean isSmoothShape(){
        return smoothShape;
    }  
    
    
   
    public void setUseDualFilter(boolean useDualFilter){
        this.useDualFilter = useDualFilter;
        getPrefs().putBoolean("PawTracker3.useDualFilter",useDualFilter);
    }
    public boolean isUseDualFilter(){
        return useDualFilter;
    }
 
    public void setUseLowFilter(boolean useLowFilter){
        this.useLowFilter = useLowFilter;
        getPrefs().putBoolean("PawTracker3.useLowFilter",useLowFilter);
    }
    public boolean isUseLowFilter(){
        return useLowFilter;
    }
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        getPrefs().putInt("PawTracker3.lowFilter_radius",lowFilter_radius);
    }
    
    public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        getPrefs().putInt("PawTracker3.lowFilter_density",lowFilter_density);
    }
    
     public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
        getPrefs().putFloat("PawTracker3.lowFilter_threshold",lowFilter_threshold);
    }
    
      public int getLowFilter_radius2() {
        return lowFilter_radius2;
    }
    
    public void setLowFilter_radius2(int lowFilter_radius2) {
        this.lowFilter_radius2 = lowFilter_radius2;
        getPrefs().putInt("PawTracker3.lowFilter_radius2",lowFilter_radius2);
    }
    
    public int getLowFilter_density2() {
        return lowFilter_density2;
    }
    
    public void setLowFilter_density2(int lowFilter_density2) {
        this.lowFilter_density2 = lowFilter_density2;
        getPrefs().putInt("PawTracker3.lowFilter_density2",lowFilter_density2);
    }
   
}
