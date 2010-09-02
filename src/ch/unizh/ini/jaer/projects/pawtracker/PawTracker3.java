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


package ch.unizh.ini.jaer.projects.pawtracker;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.*;
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


/**
 * Tracks Rat's Paw
 *<p>
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
   
    private int door_xa=getPrefs().getInt("PawTracker3.door_xa",52);
    {setPropertyTooltip("door_xa","lower x bound of the cage door");}
    private int door_xb=getPrefs().getInt("PawTracker3.door_xb",88);
    {setPropertyTooltip("door_xb","higher x bound of the cage door");}
    private int door_ya=getPrefs().getInt("PawTracker3.door_ya",50);
    {setPropertyTooltip("door_ya","lower y bound of the cage door");}
    private int door_yb=getPrefs().getInt("PawTracker3.door_yb",127);
    {setPropertyTooltip("door_yb","higher y bound of the cage door");}
    
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
    
    
    private float brightness=getPrefs().getFloat("PawTracker3.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
           
    
    private float shapeLimit=getPrefs().getFloat("PawTracker3.shapeLimit",2f);
    {setPropertyTooltip("shapeLimit","[acc value] fingertip pattern matching: surrouding points are seen as zeroes under this value");}
    private float shapeThreshold=getPrefs().getFloat("PawTracker3.shapeThreshold",2f);
    {setPropertyTooltip("shapeThreshold","[acc value] fingertip pattern matching: center points must be above this value");}
 
    
    private float shapeDLimit=getPrefs().getFloat("PawTracker3.shapeDLimit",2f);
    {setPropertyTooltip("shapeDLimit","[acc value] fingertip pattern matching: surrouding points are seen as zeroes under this value");}
    private float shapeDThreshold=getPrefs().getFloat("PawTracker3.shapeDThreshold",2f);
    {setPropertyTooltip("shapeDThreshold","[acc value] fingertip pattern matching: center points must be above this value");}
 
    
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
  
    
    private int finger_length=getPrefs().getInt("PawTracker3.finger_length",10);
    {setPropertyTooltip("finger_length","[nb pixels] max length of finger segment");} 
  
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
    private boolean showDecay = getPrefs().getBoolean("PawTracker3.showDecay",false);
    
    
    
    private boolean scaleIntensity = getPrefs().getBoolean("PawTracker3.scaleIntensity",false);
    private boolean scaleAcc = getPrefs().getBoolean("PawTracker3.scaleAcc",true);
    
    
    private boolean showWindow = getPrefs().getBoolean("PawTracker3.showWindow",true);
    private boolean showScore = getPrefs().getBoolean("PawTracker3.showScore",false);
   
              
    private boolean useSimpleContour = getPrefs().getBoolean("PawTracker3.useSimpleContour",true);
    private boolean useFingerDistanceSmooth = getPrefs().getBoolean("PawTracker3.useFingerDistanceSmooth",true);
              
    private boolean showShapePoints = getPrefs().getBoolean("PawTracker3.showShapePoints",true);
    private boolean showFingerPoints = getPrefs().getBoolean("PawTracker3.showFingerPoints",true);
  
    
    
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
    private float sk_threshold=getPrefs().getFloat("PawTracker3.sk_threshold",0.2f);
 
    private float sk_radius_min=getPrefs().getFloat("PawTracker3.sk_radius_min",4f);
    private float sk_radius_max=getPrefs().getFloat("PawTracker3.sk_radius_max",5f);
 
    private float finger_border_mix=getPrefs().getFloat("PawTracker3.finger_border_mix",0.1f);
    private float finger_tip_mix=getPrefs().getFloat("PawTracker3.finger_tip_mix",0.5f);
    private int finger_surround=getPrefs().getInt("PawTracker3.finger_surround",10);
    private int finger_creation_surround=getPrefs().getInt("PawTracker3.finger_creation_surround",30);
             
    private int intensity_range=getPrefs().getInt("PawTracker3.intensity_range",2);
       
    private int decayTimeLimit=getPrefs().getInt("PawTracker3.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
    private boolean decayOn = getPrefs().getBoolean("PawTracker3.decayOn",false); 
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}       
    
    
    // do not forget to add a set and a getString/is method for each new parameter, at the end of this .java file
    
    
    // Global variables
    
    private int nbFingers = 0; //number of fingers tracked, maybe putString it somewhere else
    
    protected float scoresFrame[][] = new float[retinaSize][retinaSize];
    protected FingerCluster[] fingers = new FingerCluster[MAX_NB_FINGERS];
  
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
   
    int currentTime = 0;
    
   
    /** additional classes */
    /** EventPoint : all data about a point in retina space */
    private class EventPoint{
         
        
          
          
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
          Integer group;           // contour group to which this point belongs, group 1 is touching door        
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
          
          public EventPoint(  ){
              
          }
          public EventPoint( int x, int y ){
              this.x = x;
              this.y = y;
          }
          
          public float getAccValue( int currentTime ){
              return accValue-decayedValue(accValue,currentTime-updateTime);
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
          
          
          float decayedValue( float value, int time ){
              float res=0;
              float dt = (float)time/(float)decayTimeLimit;
              if(dt<1){
                  res = value * dt;
              }
              return res;             
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
              gcx = 0;
              gcy = 0;
              prev_gcx = 0;
              prev_gcy = 0;
          }
          
          void setGc(){
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
        
        
        void updateLine(){                         
                    
            Vector somelines = longestLines(x,y,finger_length,lines_n_avg);
            
            
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
                
                Line line2 = shortestLineToValue(x1,y1, palmback_distance, palmback_below, palmback_value);
                
                
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
      
    
     void updateLine2(){
         Line line1 = null;
         if(eventPoints[x][y].shortFilteredValue>shapeLimit){
         // if(eventPoints[tip_x_end][tip_y_end].shortFilteredValue>shapeLimit){
            
             
             // finger cluster attached
             // trace line to skeletton
             
             line1 = mostValuableLine(x,y,finger_length);
             
         } else { 
             
             if(eventPoints[x1][y1].shortFilteredValue>shapeLimit){
            // finger cluster a bit lost 
                 line1 = lineFromShapeBorder(x1,y1, finger_length, lineDirection(new Line(x1,y1,x,y)), line2shape_thresh);
                 if(line1.length>0){
                     tip_x_end = line1.x0;
                     tip_y_end = line1.y0;
                     x = line1.x0;
                     y = line1.y0;
                 }
             } else {
                 line1 = lineFromShapeBorder(x2,y2, finger_length, lineDirection(new Line(x2,y2,x1,y1)), line2shape_thresh);
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
             
             Line line2 = shortestLineToValue(x1,y1, palmback_distance, palmback_below, palmback_value);
             
             
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
        
       scoresFrame = new float[retinaSize][retinaSize];
       fingers = new FingerCluster[MAX_NB_FINGERS];
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
            
            ep.updateTime = e.timestamp;
            ep.x = e.x;   
            ep.y = e.y;
            processTracking(ep);                                              
            
        }//end for all incoming events
          
        currentTime = ae.getLastTimestamp();
        
        // now, if there are any fingertracker, update their properties (lines)
        
        for(FingerCluster fc:fingers){
            if (fc!=null){
                if(fc.activated){
                    fc.updateLine2();
                    
                    
                }
            }
        }
        
    }
    

// cold optimize by only processing events if accumulated value above some threshold
    public void processTracking( EventPoint ep  ){
       
        
       
        
        
        // do a maximum in one pass :
        // for all neighbouring event poinrs in large range 
        // update values from filter computation
        // when also in short range: apply low filter update
        fastDualLowFilterFrame(ep);
        
        
        
         
            
  
    }
    
    
    public String toString(){
        String s="PawTracker3";
        return s;
    }
    
  
   
   
    
    
  /*  
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
      */
    
    
    /**
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
**/
     /**  
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
      
    **/  
  

  
      
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
      
  
    
    
    
   

    
    
  
    
     
    
   
   
    
 
  
    private int testFingerTipPattern( EventPoint ep ){
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
    public void isOnPawShape(EventPoint ep, int currentTime){
              
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
            thin_a(ep);
            thin_surround_a(ep);
            thin_b(ep);
            thin_surround_b(ep);
           
            
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
                            checkBorderTemplate(surroundPoint,currentTime);
                       }
                        
                        
                    }
                }
            }
        }
        
        
       checkBorderTemplate(ep,currentTime);
         
        
         // create new label if no neighbouring label
        // for this collect all neigbouring labels, and if any choose the one
        // with lowest value to replace the others by assignment

        // intensity : update neigbour intensity before?
        
        
                      
    }
    
    
    void thin_a( EventPoint ep){
        if(ep.shortFilteredValue>sk_threshold){
         // thin
            int[] a = new int[8];
            int[] arbr = new int[2];
            ep.skValue = 1;
            
            arbr = t1a ( ep.x, ep.y, a,retinaSize,retinaSize, sk_threshold);
            
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
    
    void thin_surround_a( EventPoint ep){
        
        for (int i=ep.x-2; i<ep.x+3;i++){
            if(i>=0&&i<retinaSize){
                for (int j=ep.y-2; j<ep.y+3;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j]; 
                        thin_a(surroundPoint);
                        
                    }
                }
            }
        }
        
    }
    
     void thin_surround_b( EventPoint ep){
        for (int i=ep.x-2; i<ep.x+3;i++){
            if(i>=0&&i<retinaSize){
                for (int j=ep.y-2; j<ep.y+3;j++){
                    if(j>=0&&j<retinaSize){
                        EventPoint surroundPoint = eventPoints[i][j]; 
                        thin_b(surroundPoint);
                        
                    }
                }
            }
        }
        
    }
    
    void thin_b( EventPoint ep){
       // if(ep.skValue==1){
         // thin
            int[] a = new int[8];
            int[] arbr = new int[2];
           // ep.skValue = 1;
            
            arbr = t1b ( ep.x, ep.y, a,retinaSize,retinaSize, sk_threshold);
            
            int p1 = a[0]*a[2]*a[4];
            int p2 = a[2]*a[4]*a[6];
            if ( (arbr[0] == 1) && ((arbr[1]>=2) && (arbr[1]<=6)) &&
                    (p1 == 0) && (p2 == 0) )  {
                ep.skValue = 0;
            } 
       // }
        
    }
    
    
    void setPrevGCIntensityLine( EventPoint ep, float value ){
        
     //  if(ep.prev_gcx==0&&ep.prev_gcy==0)return;
        
        // find dir of line
        float dir = direction(ep.x,ep.y,ep.prev_gcx,ep.prev_gcy);
        
        int xi = ep.x + Math.round((float)Math.cos(Math.toRadians(dir))*intensity_range); // +0.5 for rounding, maybe not
        int yi = ep.y + Math.round((float)Math.sin(Math.toRadians(dir))*intensity_range); // +0.5 for rounding, maybe not
                
        
        //System.out.println("setGCIntensityLine ["+ep.x+","+ep.y+"] to ["+xi+","+yi+"] of value : "+value);
        // increase by value on line for max length
        increaseIntentisyOfLine(ep.x,ep.y,xi,yi,value);
        
    }
    
   void setGCIntensityLine( EventPoint ep, float value ){
        
     //  if(ep.prev_gcx==0&&ep.prev_gcy==0)return;
        
        // find dir of line
        float dir = direction(ep.x,ep.y,ep.gcx,ep.gcy);
        
        int xi = ep.x + Math.round((float)Math.cos(Math.toRadians(dir))*intensity_range+0.5f); // +0.5 for rounding, maybe not
        int yi = ep.y + Math.round((float)Math.sin(Math.toRadians(dir))*intensity_range+0.5f); // +0.5 for rounding, maybe not
                
        
        //System.out.println("setGCIntensityLine ["+ep.x+","+ep.y+"] to ["+xi+","+yi+"] of value : "+value);
        // increase by value on line for max length
       // increaseIntentisyOfLine(ep.x,ep.y,xi,yi,value);
        
        increaseIntentisyOfLine(ep.x,ep.y,xi-2,yi+2,value);
        increaseIntentisyOfLine(ep.x,ep.y,xi-2,yi-2,value);
        increaseIntentisyOfLine(ep.x,ep.y,xi+2,yi+2,value);
        increaseIntentisyOfLine(ep.x,ep.y,xi+2,yi-2,value);
        
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
    
    void checkBorderTemplate(EventPoint ep, int currentTime ){
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
             ep.setGc();
             // increase current gc line
            //   System.out.println("increase current setPrevGCIntensityLine");
             setGCIntensityLine(ep,intensity_strength);
         } else if(ep.border&&!becomesBorder){ //was a border but is not anymore
             // decrease previous gc line
           //    System.out.println("remove previous setPrevGCIntensityLine");
             setGCIntensityLine(ep,-intensity_strength);
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
    
    
     int[] t1a( int i, int j, int[] a, int nn, int mm, float threshold ){
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
     
      int[] t1b( int i, int j, int[] a, int nn, int mm, float threshold ){
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
    
    
    protected Line shortestLineToValue(int x, int y, int range, float short_threshold, float large_threshold ){
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
    protected Vector longestLines(int x, int y, int range, int nb_lines_avg ){
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
   
    
    protected Line mostValuableLine(int x, int y, int range ){
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
 
    
      
    protected Line lineFromShapeBorder(int x, int y, int range, float orientation, float min_threshold ){
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
   
    

    // follow Bresenham algorithm to incrase intentisy along a discrete line between two points
     protected void increaseIntentisyOfLine(int x0, int y0, int x1, int y1, float intensityIncrease){
        
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
                                // do not decay value here : we want to know what was brute value of last update
                                influencedPoint.previousShortFilteredValue = influencedPoint.shortFilteredValue;
                                influencedPoint.previousDecayedFilteredValue = influencedPoint.decayedFilteredValue;
                                
                                //influencedPoint.previousUpdate = influencedPoint.updateTime;
                               
                                influencedPoint.shortFilteredValue += (ep.lastValue * f)/shortRangeTotal;  
                                 // use getString..Value(time) to decay value
                                influencedPoint.decayedFilteredValue = influencedPoint.getDecayedFilteredValue(ep.updateTime) + (ep.lastValue * f)/shortRangeTotal;
                                
                                //influencedPoint.updateTime = ep.updateTime;
                                if (influencedPoint.shortFilteredValue<0) {
                                    influencedPoint.shortFilteredValue = 0;
                                    influencedPoint.decayedFilteredValue = 0;
                                }
                                
                                // update border status
                                isOnPawShape(influencedPoint,ep.updateTime);
                                                                
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
                            val = ep.intensity*ep.shortFilteredValue;
                            System.out.println("Selected pixel x,y=" + x + "," + y + " intensity*value="+val+" intensity"+ep.intensity);
                           
                        } else {
                         
                        
                         if(showAcc){
                            
                            if(showOnlyAcc){
                                val = ep.accValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " acc value="+val);
                              
                            } else if(showSecondFilter){
                                val = ep.largeFilteredValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " 2d filter value="+val);
                              
                            } else if(showDecay){
                                
                                val = ep.getDecayedFilteredValue(currentTime);
                                System.out.println("Selected pixel x,y=" + x + "," + y + " decayed filter value="+val);
                              
                                
                            } else {
                                val = ep.shortFilteredValue;
                                System.out.println("Selected pixel x,y=" + x + "," + y + " 1st filter value="+val);
                               
                            }
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
                         
                         
                         //gl.glColor3f(0,0,0);
                         //gl.glRectf(ep.prev_gcx*intensityZoom,ep.prev_gcy*intensityZoom,(ep.prev_gcx+1)*intensityZoom,(ep.prev_gcy+1)*intensityZoom);
                       
                        
                        
                         float f=0;
                         float r = 0; //scoresFrame[i][j];
                         //scoresFrame[i][j] = 0;//hack, scoresFrame should be used only for this display
                         float g = 0;
                         float b = 0;
                         
                         if(ep.shortFilteredValue>in_value_threshold){
                             //if(ep.intensity*ep.shortFilteredValue>intensity_threshold){
                             if(ep.intensity>intensity_threshold){
                                 g = ep.intensity;
                             } else {
                                 f = ep.intensity; //*ep.shortFilteredValue;
                             }
                             
                             if(showSkeletton){
                                if(ep.skValue>sk_threshold){
                                 g = ep.skValue;
                                }
                             }
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
                         
                        // f is scaled between 0-1
                        gl.glColor3f(f+r,g,f+b);
                        gl.glRectf(i*intensityZoom,j*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                        
                        gl.glColor3f(1,0,0);
                        gl.glRectf(ep.gcx*intensityZoom,ep.gcy*intensityZoom,(ep.gcx+1)*intensityZoom,(ep.gcy+1)*intensityZoom);
                       
                    }
                }
                
             } else if(showAcc){
                    
                   // float max = findArrayMax(insideIntensities);
                    
                     for (int i = 0; i<eventPoints.length; i++){
                        for (int j = 0; j<eventPoints[i].length; j++){
                            EventPoint ep = eventPoints[i][j];
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
                                
                                f = ep.getDecayedFilteredValue(currentTime);
                                
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
                            
                            gl.glColor3f(f+r,f+g,f+b);
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
                drawBox(gl,door_xa*intensityZoom,door_xb*intensityZoom,door_ya*intensityZoom,door_yb*intensityZoom);
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
                    for(FingerCluster fc:fingers){
                        if(fc!=null){
                            if(fc.activated){
                                
                                gl.glBegin(GL.GL_LINES);
                                {
                                    if(fc.x1!=0){
                                        gl.glColor3f(1,1,0);
                                        gl.glVertex2i(fc.x*intensityZoom,fc.y*intensityZoom);
                                        gl.glVertex2f(fc.x1*intensityZoom,fc.y1*intensityZoom);
                                    }
                                    if(fc.x1!=0&&fc.x2!=0){
                                        gl.glColor3f(0,1,0);
                                        gl.glVertex2i(fc.x1*intensityZoom,fc.y1*intensityZoom);
                                        gl.glVertex2f(fc.x2*intensityZoom,fc.y2*intensityZoom);
                                    }
                                }
                                gl.glEnd();
                                
                            }
                        }
                    }
                }
                    
                    
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
                //}   // end if show fingers  
                if(showFingerTips){
                   
                        //gl.glColor3f(1,1,0);
                       
                        for(FingerCluster fc:fingers){
                            if(fc!=null){
                                if(fc.activated){
                                    gl.glColor3f(1,1,0);
                                     gl.glRectf(fc.tip_x_end*intensityZoom,fc.tip_y_end*intensityZoom,
                                             (fc.tip_x_end+1)*intensityZoom,(fc.tip_y_end+1)*intensityZoom);
                                     
                                    
//                                     gl.glRectf((fc.tip_x_end-finger_surround)*intensityZoom,(fc.tip_y_end-finger_surround)*intensityZoom,
//                                             (fc.tip_x_end+finger_surround)*intensityZoom,(fc.tip_y_end+finger_surround)*intensityZoom);
//                            
                                     
                                     gl.glBegin(GL.GL_LINE_LOOP);
                                     {
                                         gl.glVertex2i((fc.tip_x_end-finger_surround)*intensityZoom,(fc.tip_y_end-finger_surround)*intensityZoom);
                                         gl.glVertex2i((fc.tip_x_end+finger_surround)*intensityZoom,(fc.tip_y_end-finger_surround)*intensityZoom);
                                         gl.glVertex2i((fc.tip_x_end+finger_surround)*intensityZoom,(fc.tip_y_end+finger_surround)*intensityZoom);
                                         gl.glVertex2i((fc.tip_x_end-finger_surround)*intensityZoom,(fc.tip_y_end+finger_surround)*intensityZoom);
                                     }
                                     gl.glEnd();
                                     
//                                     gl.glColor3f(0,1,0);
//                                     
//                                     gl.glBegin(GL.GL_LINE_LOOP);
//                                     {
//                                         gl.glVertex2i((fc.tip_x_end-finger_creation_surround)*intensityZoom,(fc.tip_y_end-finger_creation_surround)*intensityZoom);
//                                         gl.glVertex2i((fc.tip_x_end+finger_creation_surround)*intensityZoom,(fc.tip_y_end-finger_creation_surround)*intensityZoom);
//                                         gl.glVertex2i((fc.tip_x_end+finger_creation_surround)*intensityZoom,(fc.tip_y_end+finger_creation_surround)*intensityZoom);
//                                         gl.glVertex2i((fc.tip_x_end-finger_creation_surround)*intensityZoom,(fc.tip_y_end+finger_creation_surround)*intensityZoom);
//                                     }
//                                     gl.glEnd();
                                     
                                     
                                     gl.glColor3f(1,0,0);
                                     gl.glRectf(fc.x*intensityZoom,fc.y*intensityZoom,
                                             (fc.x+1)*intensityZoom,(fc.y+1)*intensityZoom);
                                     
                                     
                                     gl.glColor3f(0.58f,0,0.38f);
                                     gl.glRectf(fc.x1*intensityZoom,fc.y1*intensityZoom,
                                             (fc.x1+1)*intensityZoom,(fc.y1+1)*intensityZoom);
                                     
                                }
                            }
                        }  
                         //   gl.glRasterPos3f((fc.tip_x_end+3)*intensityZoom,fc.tip_y_end*intensityZoom,0);
                            
                           // chip.getCanvas().getGlut().glutBitmapString(font, String.format("%d", sc.score ));
                        
                    
                     
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
        
        
        GL gl=drawable.getGL(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTracker3.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            
            
            //for all fingers
            // for(Cluster c:clusters){
            
            // getString finger location
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
    
    public void setShapeDLimit(float shapeDLimit) {
        this.shapeDLimit = shapeDLimit;
        getPrefs().putFloat("PawTracker3.shapeDLimit",shapeDLimit);
    }
    public float getShapeDLimit() {
        return shapeDLimit;
    }
    
    public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;
        getPrefs().putInt("PawTracker3.decayTimeLimit",decayTimeLimit);
    }
    public int getDecayTimeLimit() {
        return decayTimeLimit;
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
    
    public void setFinger_length(int finger_length) {
        this.finger_length = finger_length;
        getPrefs().putInt("PawTracker3.finger_length",finger_length);
    }
    public int getFinger_length() {
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
    
    public void setShapeDThreshold(float shapeDThreshold) {
        this.shapeDThreshold = shapeDThreshold;
        getPrefs().putFloat("PawTracker3.shapeDThreshold",shapeDThreshold);
    }
    public float getShapeDThreshold() {
        return shapeDThreshold;
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
    
    public void setShowDecay(boolean showDecay){
        this.showDecay = showDecay;
        getPrefs().putBoolean("PawTracker3.showDecay",showDecay);
    }
    public boolean isShowDecay(){
        return showDecay;
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
    
    
    
     public void setShowFingerPoints(boolean showFingerPoints){
        this.showFingerPoints = showFingerPoints;
        getPrefs().putBoolean("PawTracker3.showFingerPoints",showFingerPoints);
    }
    public boolean isShowFingerPoints(){
        return showFingerPoints;
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
   
     public float getFinger_border_mix() {
        return finger_border_mix;
    }
    
    public void setFinger_border_mix(float finger_border_mix) {
        this.finger_border_mix = finger_border_mix;
        getPrefs().putFloat("PawTracker3.finger_border_mix",finger_border_mix);
    }
    
     public float getFinger_tip_mix() {
        return finger_tip_mix;
    }
    
    public void setFinger_tip_mix(float finger_tip_mix) {
        this.finger_tip_mix = finger_tip_mix;
        getPrefs().putFloat("PawTracker3.finger_tip_mix",finger_tip_mix);
    }
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        getPrefs().putInt("PawTracker3.finger_surround",finger_surround);
    }
    
        
    public int getFinger_creation_surround() {
        return finger_creation_surround;
    }
    
    public void setFinger_creation_surround(int finger_creation_surround) {
        this.finger_creation_surround = finger_creation_surround;
        getPrefs().putInt("PawTracker3.finger_creation_surround",finger_creation_surround);
    }
    
    
    
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        getPrefs().putFloat("PawTracker3.brightness",brightness);
    }
    
    
    public float getSk_threshold() {
        return sk_threshold;
    }
    
    public void setSk_threshold(float sk_threshold) {
        this.sk_threshold = sk_threshold;
        getPrefs().putFloat("PawTracker3.sk_threshold",sk_threshold);
    }
    
    public float getSk_radius_min() {
        return sk_radius_min;
    }
    
    public void setSk_radius_min(float sk_radius_min) {
        this.sk_radius_min = sk_radius_min;
        getPrefs().putFloat("PawTracker3.sk_radius_min",sk_radius_min);
    }
    
     public float getSk_radius_max() {
        return sk_radius_max;
    }
    
    public void setSk_radius_max(float sk_radius_max) {
        this.sk_radius_max = sk_radius_max;
        getPrefs().putFloat("PawTracker3.sk_radius_max",sk_radius_max);
    }
    
    
    
}
