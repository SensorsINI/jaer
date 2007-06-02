/*
 * PawTracker2.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * ( different approach than PawTracker '1' )
 *
 * This class finds the contour of the paw in the accumulation of incoming events
 * After low-pass filter
 * Then detection of fingers tips by template matching
 * + alternative algorithms trials
 *
 * work under (a lot of) progress
 *
 * Paul Rogister, Created on May, 2007
 *
 */


/** to do : */
/* try decaying accumulation */


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
public class PawTracker2 extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    private static Preferences prefs=Preferences.userNodeForPackage(PawTracker2.class);
    
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    /** the number of classes of objects */
    private final int NUM_CLASSES=2;
    // max number of orientations
    private final int MAX_ORIENTATIONS=10000;
    private final int MAX_DISTGC=300;//depends
    private final int MAX_SEQ_LENGTH=50;
    private final int MAX_NB_SEQ=310;
    
    private double maxOrientation = 180;
    private double maxDistGC = 200;
            
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    
    
    /** scaling can't make cluster bigger or smaller than this ratio to default cluster size */
    //  public static final float MAX_SCALE_RATIO=2;
    
    //   private float classSizeRatio=prefs.getFloat("PawTracker2.classSizeRatio",2);
    //   private boolean sizeClassificationEnabled=prefs.getBoolean("PawTracker2.sizeClassificationEnabled",true);
    
    
    private int entry_x=prefs.getInt("PawTracker2.entry_x",30);
    private int entry_y=prefs.getInt("PawTracker2.entry_y",76);
    private int entry_z=prefs.getInt("PawTracker2.entry_z",80);
    
    private int door_xa=prefs.getInt("PawTracker2.door_xa",50);
    private int door_xb=prefs.getInt("PawTracker2.door_xb",127);
    private int door_ya=prefs.getInt("PawTracker2.door_ya",52);
    private int door_yb=prefs.getInt("PawTracker2.door_yb",88);
    
    private int retinaSize=prefs.getInt("PawTracker2.retinaSize",128);
    private int linkSize=prefs.getInt("PawTracker2.linkSize",2);
    private int segSize=prefs.getInt("PawTracker2.segSize",2);
    private int maxSegSize=prefs.getInt("PawTracker2.maxSegSize",4);
    
    private int minZeroes=prefs.getInt("PawTracker2.minZeroes",2);
    private int maxZeroes=prefs.getInt("PawTracker2.maxZeroes",6);
    private int doorMinZeroes=prefs.getInt("PawTracker2.doorMinZeroes",2);
    private int doorMaxZeroes=prefs.getInt("PawTracker2.doorMaxZeroes",6);
    
    private int in_length=prefs.getInt("PawTracker2.in_length",3);
    private int in_test_length=prefs.getInt("PawTracker2.in_test_length",3);
    private float in_threshold=prefs.getFloat("PawTracker2.in_threshold",0.4f);
    private float line_threshold=prefs.getFloat("PawTracker2.line_threshold",0f);
    private int score_threshold=prefs.getInt("PawTracker2.score_threshold",37);
    private float score_in_threshold=prefs.getFloat("PawTracker2.score_in_threshold",0.2f);
    private int score_range=prefs.getInt("PawTracker2.score_range",3);
    private int line_range=prefs.getInt("PawTracker2.line_range",8);
    private int fing_maxRange=prefs.getInt("PawTracker2.fing_maxRange",5);
    private int fing_minRange=prefs.getInt("PawTracker2.fing_minRange",3);
   
    private int intensityZoom = prefs.getInt("PawTracker2.intensityZoom",2);
            
    
    private int subzone_xa=prefs.getInt("PawTracker2.subzone_xa",30);
    private int subzone_xb=prefs.getInt("PawTracker2.subzone_xb",127);
    private int subzone_ya=prefs.getInt("PawTracker2.subzone_ya",110);
    private int subzone_yb=prefs.getInt("PawTracker2.subzone_yb",127);
    
    private int fingertip_size=prefs.getInt("PawTracker2.fingertip_size",4);
    
    // private int pellet_size=prefs.getInt("PawTracker2.pellet_size",24);
    
    // private int entryZone_threshold=prefs.getInt("PawTracker2.entryZone_threshold",100);
    // private int entryZone_side_threshold=prefs.getInt("PawTracker2.entryZone_side_threshold",25);
    
    
    
    private int max_fingers=prefs.getInt("PawTracker2.max_fingers",4);
    
    private int minSeqLength=prefs.getInt("PawTracker2.minSeqLength",4);
    
            
    private float seqTolerance=prefs.getFloat("PawTracker2.seqTolerance",10f);
    
    private float minDiff=prefs.getFloat("PawTracker2.minDiff",2f);
    private float maxDiff=prefs.getFloat("PawTracker2.maxDiff",2f);
    private float doorMinDiff=prefs.getFloat("PawTracker2.doorMinDiff",2f);
    private float doorMaxDiff=prefs.getFloat("PawTracker2.doorMaxDiff",2f);
    private float minAngle=prefs.getFloat("PawTracker2.minAngle",90f);
    //   private float distance_threshold=prefs.getFloat("PawTracker2.distance_threshold",5.0f);
    
    //   private float change_ratio=prefs.getFloat("PawTracker2.change_ratio",0.5f); //% of opposite type events above which cluster change to opp type
    
    //    private int min_events_per_cluster=prefs.getInt("PawTracker2.min_events_per_cluster",1);
    
    private boolean showSegments = prefs.getBoolean("PawTracker2.showSegments",true);
    private boolean showFingers = prefs.getBoolean("PawTracker2.showFingers",true);
    private boolean showZones = prefs.getBoolean("PawTracker2.showZones",true);
    private boolean showAll = prefs.getBoolean("PawTracker2.showAll",true);
    // hide activity inside door
    private boolean hideInside = prefs.getBoolean("PawTracker2.hideInside",false);
    // show intensity inside shape
    private boolean showIntensity = prefs.getBoolean("PawTracker2.showIntensity",false);
    //private boolean opposite = prefs.getBoolean("PawTracker2.opposite",false);
    private boolean showAcc = prefs.getBoolean("PawTracker2.showAcc",false);
    private boolean scaleIntensity = prefs.getBoolean("PawTracker2.scaleIntensity",false);
   
    private boolean clean = prefs.getBoolean("PawTracker2.clean",false);
    private int clean_threshold = prefs.getInt("PawTracker2.clean_threshold",0);
   
            
    private boolean showShape = prefs.getBoolean("PawTracker2.showShape",true);
    private boolean showSequences = prefs.getBoolean("PawTracker2.showSequences",true);
    private boolean smoothShape = prefs.getBoolean("PawTracker2.smoothShape",true);
    //   private boolean showKnuckles = prefs.getBoolean("PawTracker2.showKnuckles",true);
    private boolean lowFilter = prefs.getBoolean("PawTracker2.lowFilter",true);
    private int lowFilter_radius=prefs.getInt("PawTracker2.lowFilter_radius",10);
    private int lowFilter_density=prefs.getInt("PawTracker2.lowFilter_density",17);
    
    protected float defaultClusterRadius;
    //   protected float mixingFactorClusterForce=prefs.getFloat("PawTracker2.mixingFactorClusterForce",0.01f);
    protected float mixingFactor=prefs.getFloat("PawTracker2.mixingFactor",0.01f); // amount each event moves COM of cluster towards itself
    protected float velocityMixingFactor=prefs.getFloat("PawTracker2.velocityMixingFactor",0.01f); // mixing factor for velocity computation
    
    private float surround=prefs.getFloat("PawTracker2.surround",2f);
    private boolean dynamicSizeEnabled=prefs.getBoolean("PawTracker2.dynamicSizeEnabled", false);
    private boolean dynamicAspectRatioEnabled=prefs.getBoolean("PawTracker2.dynamicAspectRatioEnabled",false);
    private boolean pathsEnabled=prefs.getBoolean("PawTracker2.pathsEnabled", true);
    private boolean colorClustersDifferentlyEnabled=prefs.getBoolean("PawTracker2.colorClustersDifferentlyEnabled",false);
    private boolean useOnPolarityOnlyEnabled=prefs.getBoolean("PawTracker2.useOnPolarityOnlyEnabled",false);
    private boolean useOffPolarityOnlyEnabled=prefs.getBoolean("PawTracker2.useOffPolarityOnlyEnabled",false);
    private float aspectRatio=prefs.getFloat("PawTracker2.aspectRatio",1f);
    private float clusterSize=prefs.getFloat("PawTracker2.clusterSize",.2f);
    protected boolean growMergedSizeEnabled=prefs.getBoolean("PawTracker2.growMergedSizeEnabled",false);
    private boolean measureVelocity=prefs.getBoolean("PawTracker2.showVelocity",true); // enabling this enables both computation and rendering of cluster velocities
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    private boolean classifierEnabled=prefs.getBoolean("PawTracker2.classifierEnabled",false);
    private float classifierThreshold=prefs.getFloat("PawTracker2.classifierThreshold",0.2f);
    private boolean showAllClusters=prefs.getBoolean("PawTracker2.showAllClusters",false);
    private boolean useNearestCluster=prefs.getBoolean("PawTracker2.useNearestCluster",false); // use the nearest cluster to an event, not the first containing it
    
    
    
    
    
    private final float VELOCITY_VECTOR_SCALING=1e5f; // to scale rendering of cluster velocity vector
    private int predictiveVelocityFactor=1;// making this M=10, for example, will cause cluster to substantially lead the events, then slow down, speed up, etc.
    
    /** maximum and minimum allowed dynamic aspect ratio */
    public static final float ASPECT_RATIO_MAX=5, ASPECT_RATIO_MIN=0.2f;
    
    protected boolean pawIsDetected = false;
    protected boolean showDetectedClusters = false;
    
    
    //private Zone mainZone = new Zone("main",128,128,0); // +subzone
    protected Contour contour = new Contour();
    protected Segment segments[];
    protected int nbSegments = 0;
    
    protected Sequence sequences[];
    protected int sequenceSize = 0;
    protected int tooManySequences = 0;
    
    protected ContourPoint gc = new ContourPoint();
   
    protected int accOrientationMax = 1;
    
    /** accumulate events as would do the accumulate view mode 'P' from the gui */
    // could be that x,y axis are swap to y,x in accEvents 
    protected float accEvents[][][]; 
    protected float filteredEvents[][][]; 
    
    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    /** intensity inside the paw shape as accumulated value of projections from border segments */
    protected float insideIntensities[][][];
    protected float intensityIncrease = 0.1f;
    
    protected Vector fingerTips = new Vector();
    protected Vector fingerLines = new Vector();
  
    /** Creates a new instance of PawTracker */
    public PawTracker2(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
        initFilter();
        resetPawTracker();
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
        
        
    }
    
    public void initFilter() {
        initDefaults();
        // defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
    }
    
    private void initDefaults(){
        //System.out.println("initDefaults");
        initDefault("PawTracker2.clusterLifetimeWithoutSupport","10000");
        initDefault("PawTracker2.maxNumClusters","10");
        initDefault("PawTracker2.clusterSize","0.15f");
        initDefault("PawTracker2.numEventsStoredInCluster","100");
        initDefault("PawTracker2.thresholdEventsForVisibleCluster","30");
        initDefault("PawTracker2.thresholdISIForVisibleCluster","2.0f");
        
//        initDefault("PawTracker2.","");
    }
    
    private void resetPawTracker(){
        pawIsDetected = false;
        showDetectedClusters= false;
        
        //System.out.println("resetPawTracker");
        accEvents = new float[retinaSize][retinaSize][3]; 
        resetArray(accEvents,0);
        //resetArray(accEvents,grayValue);
        
        
        filteredEvents = new float[retinaSize][retinaSize][3]; 
        resetArray(filteredEvents,0);
                
                
        insideIntensities = new float[retinaSize][retinaSize][1]; // should remove last dimension
        resetArray(insideIntensities,0);        
       
        
       
        sequences = new Sequence[MAX_NB_SEQ];
        sequenceSize = 0;
        segments = new Segment[MAX_ORIENTATIONS];//[MAX_DISTGC];
        
        
        nbSegments = 0;
        accOrientationMax = 1;
        contour.reset();
       
        // set subzone parameters here
        
        nbFingers = 0;
        setResetPawTracking(false);//this should also update button in panel
    }
    
    private void initDefault(String key, String value){
        if(prefs.get(key,null)==null) prefs.put(key,value);
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
        
        
        
        // for each event, accumulate into main zone
        
        // reset
        contour.reset();
        resetArray(insideIntensities,0);
        //resetArray(filteredEvents,0);
        
        float step = 2f / (colorScale + 1);
       
        // accumulate the events
        for(TypedEvent e:ae){
            int type=e.getType();
            float a = (accEvents[e.y][e.x][0]);
            a += step * (type - grayValue);
            accEvents[e.y][e.x][0] = a;
            accEvents[e.y][e.x][1] = a;
            accEvents[e.y][e.x][2] = a;
        }
        // keep in range [0-1]
        scale(accEvents);

        
        // tmp : try low filter
        if(lowFilter){
            filteredEvents = lowFilterFrame(accEvents);
            // apply border filter to find contours
            contour = findContour(filteredEvents);
        } else {
            // apply border filter to find contours
            contour = findContour(accEvents);
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
        
        // if we need to know where is the gravity center of the border points forming all contours
        // we have to initialize it here. (a bit messy)
        gc = new ContourPoint();
        sequenceSize = 0;
        
        
        
        // this is actually about finding segments, method to change
        if(showSegments){
            // detectSegments also fills insideIntensities array
            if(lowFilter){
                nbSegments = detectSegments( contour, 1, segSize, segments, filteredEvents );
            } else {
                nbSegments = detectSegments( contour, 1, segSize, segments, accEvents );
            }
        } else {
            nbSegments = computeLocalOrientations( contour, 1, linkSize, segments );
        }
       
        
       
        //
        if(lowFilter){
            // finding finger tip by template matching
            fingerTips = detectFingersTips(insideIntensities,filteredEvents);            
            // detect fingers lines, use global variable contour           
            fingerLines = detectFingersLines(fingerTips,insideIntensities,filteredEvents);
            
        } else {
            // finding finger tip by template matching
            fingerTips = detectFingersTips(insideIntensities,accEvents);            
            // detect fingers lines, use global variable contour            
            fingerLines = detectFingersLines(fingerTips,insideIntensities,accEvents);
        }
//         if(lowFilter){
//            insideIntensities = lowFilterFrame(accEvents);
//         }
        
        
        // cleaning
        if(clean&&(nbSegments<clean_threshold)){ 
            resetArray(accEvents,0);
            
        }
        
        
        
    }
    
    
    
    public String toString(){
        String s="PawTracker2";
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
    
      protected void scale(float[][][] fr ){
          // just clip values to 0..1 
          for (int i = 0; i<fr.length; i++){
              for (int j = 0; j<fr[i].length; j++){
                  for (int k = 0; k<3; k++){
                      float f = fr[i][j][k];
                      if(f<0) f=0; else if(f>1) f=1;
                      fr[i][j][k]=f;
                  }
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
      
    private class Score{
        
        int score;
        int x;
        int y;
        
        public Score(){
            score = 0;
            x = 0;
            y = 0;
        }
        public Score( int x, int y, int score){
            this.score = score;
            this.x = x;
            this.y = y;
        }
        
        
    }
    
    private class ScoreComparer implements Comparator {
                public int compare(Object obj1, Object obj2)
                {
                        int i1 = ((Score)obj1).score;
                        int i2 = ((Score)obj2).score;
        
                        return i1 - i2;
                }
    }

     //detect finger lines in array of inside intensity and array of accumulated contrast change
    // after use of finger tips are detected
    private Vector detectFingersLines( Vector tips, float[][][] ir, float[][][] fr ){
        Vector lines = new Vector();
        
        // for each points in tips        
        // find longest line with acc values > threshold, and stopping if touching door
        // at tip distance, if pt.x,y too close to shape, put it to mindistance
        // if too close from both sides, put it at the middle
        
        
        for(int i=0;i<tips.size();i++){
             // find longest line with acc values > threshold, and stopping if touching door
            Score sc = (Score)tips.elementAt(i);
            int range = 70;
            int midlength = 13;
            Vector somelines = longestLine(sc.x,sc.y,range,midlength,fr);
//            if(line.length>0){
//                
//                lines.add(line);
//            }
            lines.addAll(somelines);
            
        }
        
        
        // line = startpt, midpt, endpt
        // if endpt not touching door, try finding a new longest line to door
        
        // then remove duplicate.
        
        // to put back, paul :
       
//        
//        if(lines.size()>4){
//            
//            for(int i=0;i<lines.size();i++){
//               Line l1 = (Line)lines.elementAt(i);
//               for(int j=i+1;j<lines.size();j++){
//                    Line l2 = (Line)lines.elementAt(j);
//                    if(tooClose(l1.midx,l1.midy,l2.midx,l2.midy,line_range)){
//                        if(l1.length>l2.length){
//                            // remove l2
//                            lines.remove(j);
//                            j--;
//                        } else {
//                            // remove l1
//                            lines.remove(i);
//                            i--;
//                            j = lines.size();
//                        }
//                    }
//               } 
//            }
//        }
        // if mid pt too close to another line, keep longest touching door
        
        
        return lines;
    }
    
   
    //detect finger tip in array of inside intensity and array of accumulated contrast change
    private Vector detectFingersTips( float[][][] ir, float[][][] fr ){
          
        Vector scores = new Vector();
          // look at intensities, (or another array if not define?)
          // for each point store x,y and score if score < threshold
        // score is matching score, 1 pts for right matching per pixel, -1 for wrong(?)
          int score = 0;
          for (int i = 0; i<ir.length; i++){
              for (int j = 0; j<ir.length; j++){
                 if(ir[i][j][0]>0){
                     score = testFingerTipPattern(fr,j,i); //careful with x,y or y,x
                     if(score>score_threshold){
                          // store
                          Score sc = new Score(i,j,score);
                          scores.addElement(sc);
                          //System.out.println("add scores "+sc.x+","+sc.y+" : "+score);
                     }
                 }
              }
          }
          
          
        
        // sort score and points
        Collections.sort(scores, new ScoreComparer());  
        int range = score_range;  
        // for all best scores
        // then remove duplicate, for each best score define area
        // for all other points
        // if x,y in area of previous best, delete
        // then next best score
        
        /* comment to show finger nails :) */
        for(int i=0;i<scores.size();i++){
            Score sc = (Score)scores.elementAt(i);
            for(int j=i+1;j<scores.size();){
                Score sc2 = (Score)scores.elementAt(j);
                // if sc2 in area of sc1, delete
                if(((sc2.x<sc.x+range)&&(sc2.x>sc.x-range))&&
                       ((sc2.y<sc.y+range)&&(sc2.y>sc.y-range)) ){
                    //delete
                    scores.remove(j);
                   // j++; //if no remove for debug
                } else {
                // increase only if no deletion, to avoid jumping scores
                    j++;
                }
            }
            
        }
        /* */
        // then return all best score in order, into pawtippoints array or such
          
          return scores;
          
    }
      
    private int testFingerTipPattern( float[][][] fr, int x, int y){
        int score = 0;
        float threshold = score_in_threshold; // to adapt
        
        int xInside = 2;
        int yInside = 2;
        int xOutside = 3;
        int yOutside = 3;
        int distance1 = 3;
        int distance2 = 7;
        
        // look for positive value around x,y
        //test points in an area around
        for (int i = x-xInside; i<x+xInside; i++){
              if(i>=0&&i<fr.length){
                  for (int j = y-yInside; j<y+yInside; j++){
                      if(j>=0&&j<fr[i].length){
                      
                        if(fr[i][j][0]>threshold){
                            score++;
                         }
                      }
                  }
              }
        }
              
        // look for negative lines around 
        for (int i = x-xOutside; i<x+xOutside; i++){
              if(i>=0&&i<fr.length){
                 
                      if(y-distance1>=0){
                         if(fr[i][y-distance1][0]<threshold){
                            score++;
                         }  
                      }
                      if(y+distance1<fr.length){
                         if(fr[i][y+distance1][0]<threshold){
                            score++;
                         }
                       
                      }
                   
                      if(y-distance2>=0){
                         if(fr[i][y-distance2][0]<threshold){
                            score++;
                         }  
                      }
                      if(y+distance2<fr.length){
                         if(fr[i][y+distance2][0]<threshold){
                            score++;
                         }
                       
                      }
                  
              }
        }
        for (int j = y-yOutside; j<y+yOutside; j++){
              if(j>=0&&j<fr.length){
                 
                      if(x-distance1>=0){
                         if(fr[x-distance1][j][0]<threshold){
                            score++;
                         }  
                      }
                      if(x+distance1<fr.length){
                         if(fr[x+distance1][j][0]<threshold){
                            score++;
                         }
                       
                      }
                      if(x-distance2>=0){
                         if(fr[x-distance2][j][0]<threshold){
                            score++;
                         }  
                      }
                      if(x+distance2<fr.length){
                         if(fr[x+distance2][j][0]<threshold){
                            score++;
                         }
                       
                      }
              }
              
        }
                 
        
        
        return score;
    }
      
    // check if point is on paw's shape's border
    private boolean isOnPawShape(int x, int sx, int y, int sy, float min, float max, float mean, int minz, int maxz, float[][][] f){
        
        int zerosAround = 0;
        
        // only positive point above 0.6
//        if((x+1>=retinaSize)||(y+1>=retinaSize)){
//            return false;
//        }
       
        if((f[x+1][y+1][0])<max){//||(f[x+1][y+1][0])==mean){
            return false;
        }
        
        for(int i=x;i<=sx;i++){
            for(int j=y;j<=sy;j++){
                
                if((i>0)&&(j>0)&&(i<retinaSize)&&(j<retinaSize)){
                    //if (x==entry_x-1&&y==entry_y-1) System.out.println("events at "+i+","+j+" ="+f[i][j][0]+" "+f[i][j][1]+" "+f[i][j][2]);
                    if((f[i][j][0])<min||(f[i][j][0])==mean){
                        zerosAround++;
                    }
                    
                }
            }
        }
        
        
        if(zerosAround>minz&&zerosAround<maxz){
            return true;
        }
        
        
        return false;
    }
    
    public Contour findContour(float[][][] f){
        // return contour
        Contour contour = new Contour();
        
        // for all positive points look if surrounded by at least 3 neg and 3 pos
        for (int i=1;i<retinaSize;i++){
            for (int j=1;j<retinaSize;j++){
                // if inside door, correction
                boolean onShape = false;
//                if (i>door_xa&&i<door_xb&&j>door_ya&&j<door_yb){
//                    onShape = isOnPawShape(i-1,i+1,j-1,j+1,doorMinDiff,doorMaxDiff,0.4f,doorMinZeroes,doorMaxZeroes,f);
//                    //onShape = isOnPawShape(i-1,i+1,j-1,j+1,minDiff,maxDiff,0.5f,minZeroes,maxZeroes,f);
//                } else {
                    onShape = isOnPawShape(i-1,i+1,j-1,j+1,minDiff,maxDiff,0.5f,minZeroes,maxZeroes,f);
              //  }
                if(onShape){
                    contour.add(j,i);
                }
                
            }
        }
        
        
        return contour;
        
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
    
      // find the segments surrouding an event (i,j) (segments are called Segment)
    // return size (indexNeighbour) of Segment[]
    
    public int findSurroundingSegments( Segment neighbouringSegments[], Contour contour, int i, int j, int around, int label ){
       
        
        int indexNeighbour = 0;
        int maxX = contour.getMaxX();
        int maxY = contour.getMaxY();
        
        for (int x=i-around;x<i+around;x++){
            //System.out.println("looking at neighbour "+x+"<"+maxX+" ?");
            if (x>0&&x<maxX){
                for (int y=j-around;y<j+around;y++){
                    //System.out.println("looking at neighbour "+y+"<"+maxY+" ?");
                    if (y>0&&y<maxY){
                        //System.out.println("looking at neighbour "+x+","+y);
                        if(i==x&&j==y){
                            // do nothing
                        } else {
                            //if(contour.eventsArray[x][y].used!=1){
                            if (contour.eventsArray[x][y].on==1&&contour.eventsArray[x][y].label==label){
                                
                                
                                // found a segment
                                // compute orientation
                                Segment o = new Segment();
                                o.x1 = i;
                                o.y1 = j;
                                o.x2 = x;
                                o.y2 = y;
                                double dy = (double)(y-j);
                                double dx = (double)(x-i);
                               
                                double segmentSize = Math.sqrt((dy*dy)+(dx*dx));
                                
                             
                                double segOrientation = Math.toDegrees(Math.acos(dx/segmentSize));
                                if (o.y1>o.y2){
                                    segOrientation = 180-segOrientation;
                                }
                                // hum hum to look if the angle is computed as we want
                                
//                                double midx = (o.x1+o.x2)/2;
//                                double midy = (o.y1+o.y2)/2;
//                                
//                                double dgcx = (double)(midx-gc.x);
//                                double dgcy = (double)(midy-gc.y);
//                                
//                                double segTogc = Math.sqrt((dgcy*dgcy)+(dgcx*dgcx));
//                                // replace by min to door
//                                double minx = Math.abs(door_xa - midx);
//                                double miny = Math.abs(door_ya - midy);
//                                if (minx<miny){
//                                    o.distGC = minx;
//                                } else {
//                                    o.distGC = miny;
//                                }
//                                
//                                double gcOrientation = Math.toDegrees(Math.acos(dgcx/segTogc));
//                                if (gc.y>midy){
//                                    gcOrientation = 180-gcOrientation;
//                                }
                                // compute relative orientation of segment to gc-mid
                                o.orientation = segOrientation;// + 180 - gcOrientation;
//                                                    if (o.orientation>=180){
//                                                        o.orientation = o.orientation - 180;
//                                                    }
                                // compute orientation gc-mid
                                
                                
                                
//                                                    System.out.println(x+","+y+" "+i+","+j+" : "+o.orientation+
//                                                            " size: "+segmentSize+" : "
//                                                            +o.distGC+" gc x: "+gc.x+" y: "+gc.y+", mid x: "+midx+" y: "+midy);
//                                                if (o.distGC>maxDistGC){
//                                                    maxDistGC = o.distGC;
//                                                }
//                                                if (o.orientation>maxOrientation){
//                                                    maxOrientation = o.orientation;
//                                                }
                                //add to array
                                // should test on max
                                
                                //if(smoothShape){
                                neighbouringSegments[indexNeighbour] = o;
                                indexNeighbour++;
                                //} 
                            }
                                    
                                
                            } //end found segment
                            
                            // }
                        }
                    }
                }
            
        }//end looking at all neighbours
        
        return indexNeighbour;
        
    }
    
    // find the gravity center of a labelled contour
    public ContourPoint findContourGC( Contour contour, int label ){
        float x = 0f;
        float y = 0f;
        int n = 0;
        ContourPoint gc = new ContourPoint();
        // for all contour points
        
        for (int i=0;i<contour.getMaxX();i++){
            for (int j=0;j<contour.getMaxY();j++){
                if (contour.eventsArray[i][j].on==1){
                    if (contour.eventsArray[i][j].label==label){
                        x += contour.eventsArray[i][j].x;
                        y += contour.eventsArray[i][j].y;
                        n++;
                    }
                }
            }
        }
        x = x/n;
        y = y/n;
        gc.x = new Float(x).intValue();
        gc.y = new Float(y).intValue();
        return gc;
    }
    
    protected void resetDoubleArray( int[][] array, int x_max, int y_max ){
        for(int i=0;i<x_max;i++){
                    for(int j=0;j<y_max;j++){                     
                        array[i][j] = 0;
                    }
        }
    }
    
  //  public void computeLocalOrientations( Contour contour, int label, int around, int[][] orientations ){
    public int computeLocalOrientations( Contour contour, int label, int around, Segment[] orientations ){
        gc = findContourGC(contour,label);
        
      //  gc.x = 71;
      //  gc.y = 62;
       // resetDoubleArray(orientations,MAX_ORIENTATIONS,MAX_DISTGC);
//        maxOrientation = 0;
//        maxDistGC = 0;
        //System.out.println("computeLocalOrientations");
        
        tooManySequences = 0;
        
        int indexOrientation = 0;
        int overMaxOrientation = 0;
        int indexSeq = 0;
        accOrientationMax = 1;
        // for all points find orientation with closest neighbours
        int maxX = contour.getMaxX();
        int maxY = contour.getMaxY();
        for (int i=0;i<maxX;i++){
            for (int j=0;j<maxY;j++){
                if (contour.eventsArray[i][j].on==1){
                    if (contour.eventsArray[i][j].label==label){
                        //System.out.println("looking at "+i+","+j+" label: "+contour.eventsArray[i][j].label+"=="+label+" ?");
                        // for all neighbours
                        Segment neighbouringSegments[];
                        neighbouringSegments = new Segment[100];// size should depends on linkSize
                        int indexNeighbour = findSurroundingSegments(neighbouringSegments,contour,i,j,around,label );
                        // disable point
                        contour.eventsArray[i][j].used = 1;
                        
                        if(smoothShape){
                            // store only segment with 'smoother' orientations, ie closer to 180degrees
                            // for all neighbouring segments, find the two which angles are closer to 180
                            Segment o1 = null;
                            Segment o2 = null;
                            double angle = 0;
                            //double max = 0;
                            double min = minAngle;
                            for(int m=0;m<indexNeighbour-1;m++){
                                for(int n=m+1;n<indexNeighbour;n++){
                                    angle = neighbouringSegments[m].orientation - neighbouringSegments[n].orientation;
                                    if (angle>180) angle = 360 - angle;
                                    if (angle<min){
                                        min = angle;
                                        o1 = neighbouringSegments[m];
                                        o2 = neighbouringSegments[n];
                                        
                                    }
                                    
                                }
                            }
                            if(min<minAngle){ // some neighbours found
                                if (o1!=null&&o2!=null){
                                    orientations[indexOrientation] = o1;
                                    indexOrientation++;                                
                                    orientations[indexOrientation] = o2;
                                    indexOrientation++;
                                    
                                    // recursive creation of sequences
                                    // should be watchful for infinite loop
                                    Sequence s1 = createSequence(o1,o2,contour,min,2);
                                    Sequence s2 = createSequence(o2,o1,contour,180+min,2);
                                    s1.add(o1);
                                    s1.add(o2);
                                    s1.mergeWith(s2);
                                    // store s1
                                    if(indexSeq<MAX_NB_SEQ){
                                        sequences[indexSeq]=s1;
                                        indexSeq++;
                                    } else {
                                        tooManySequences++;
                                        //System.out.println("computeLocalOrientations: too many sequences "+tooManySequences);
                                    }
                                }
                            }
                            
                            
                        } else { // if no smooth
                           for(int m=0;m<indexNeighbour;m++){
                               if(indexOrientation<MAX_ORIENTATIONS){
                                   
                                   //int ori = new Double(o.orientation).intValue();
                                   //int disti = new Double(o.distGC).intValue();
                                   
                                   //orientations[ori][disti] += 1;
                                   
                                   //if (orientations[ori][disti]>accOrientationMax)accOrientationMax=orientations[ori][disti];
                                   
                                                        /*
                                                        System.out.println(x+","+y+" "+i+","+j+" : "+o.orientation+" -> "+ori
                                                                  +" dist "+o.distGC+" -> "+disti+ "acc: "+orientations[ori][disti]+" accmax: "+accOrientationMax);
                                                         */
                                   //System.out.println("orientation["+indexOrientation+"].x1="+o.x1+" .y1="+o.y1+
                                   //        " .x2="+o.x2+" .y2="+o.y2+" distGC="+o.distGC+" ori="+o.orientation);
                                   // for all neighbours
                                   
                                   orientations[indexOrientation] = neighbouringSegments[m];
                                   indexOrientation++;
                                   //}
                               } else {
                                   //System.out.println("computeLocalOrientations error: more than max orientations "+indexOrientation);
                                   overMaxOrientation++;
                               }
                           }
                        }
                        
                        
                    }
                }
            }
        }
        if (overMaxOrientation>0) {
            int exceed = overMaxOrientation+indexOrientation;
            System.out.println("computeLocalOrientations orientations exceed max: "+exceed+" > "+MAX_ORIENTATIONS);
            
        }
        sequenceSize = indexSeq;
        return indexOrientation;
    }
    
    
    /** detectSegments : new method to detect segments, replaces computeLocalOrientations
     *  create segments out of points from the contour labelled 1 (touching door)
     *  
     *
     **/
     public int detectSegments( Contour contour, int label, int range, Segment[] segments, float[][][] fr ){
        //gc = findContourGC(contour,label);
        
      //  gc.x = 71;
      //  gc.y = 62;
       // resetDoubleArray(orientations,MAX_ORIENTATIONS,MAX_DISTGC);
//        maxOrientation = 0;
//        maxDistGC = 0;
        //System.out.println("computeLocalOrientations");
        
       
        int indexSegment = 0;
        int overMaxNbSegments = 0;
        int indexSeq = 0;
        accOrientationMax = 1;
        // for each point find segment it belongs to
        int maxX = contour.getMaxX();
        int maxY = contour.getMaxY();
        for (int i=0;i<maxX;i++){
            for (int j=0;j<maxY;j++){
                if (contour.eventsArray[i][j].on==1){ //could optimize this by putting al on points into a specific array
                    if (contour.eventsArray[i][j].label==label){ 
                        
                        
                        //System.out.println("looking at "+i+","+j+" label: "+contour.eventsArray[i][j].label+"=="+label+" ?");
                        // for all neighbours
                        
                        // remove point from further search
                        contour.eventsArray[i][j].used = 1;
                        
                        // find random neighbour in range
                        ContourPoint neighbour = contour.getNeighbour(i,j,range);
                        
                        if(neighbour!=null){
                            // remove point from further search
                            neighbour.used = 1;
                            
                            //create segment for this two points, to be extended
                            Segment segment = new Segment(contour.eventsArray[i][j],neighbour);
                            
                            // determine policy
                             
                            int x_min_policy = 0;
                            int x_max_policy = 0;
                            int y_min_policy = 0;
                            int y_max_policy = 0;
                            
                            if(neighbour.x<i){
                                x_min_policy = -range;
                                if(neighbour.y==j) x_max_policy = -1;
                                // x_max_policy = 0;
                            }
                            if(neighbour.x==i){
                                x_min_policy = -1;
                                x_max_policy = 1;
                            }
                            if(neighbour.x>i){
                                if(neighbour.y==j) x_min_policy = 1;
                                //x_min_policy = 0;
                                x_max_policy = range;
                            }
                            //same for y
                            if(neighbour.y<j){
                                y_min_policy = -range;
                                if(neighbour.x==i) y_max_policy = -1;
                                // x_max_policy = 0;
                            }
                            if(neighbour.y==j){
                                y_min_policy = -1;
                                y_max_policy = 1;
                            }
                            if(neighbour.y>j){
                                if(neighbour.x==i) y_min_policy = 1;
                                //x_min_policy = 0;
                                y_max_policy = range;
                            }
                            
                            
                           
                            
                           
                           
                            
                            // recursive call for finding further points of the segment
                            contour.extendSegmentFollowingPolicy(segment,neighbour,x_min_policy,x_max_policy,y_min_policy,y_max_policy,1);
                            //after building a segment, we free its start point so that other segment can link to it
                            contour.eventsArray[i][j].used = 0;
                            
                            // now we have final segment
                            // compute its orientation
                            double dx = (double)(segment.x2-segment.x1);
                            double dy = (double)(segment.y2-segment.y1);
                                                  
                            segment.size = Math.sqrt((dy*dy)+(dx*dx));
                                                     
                            segment.orientation = Math.toDegrees(Math.acos(dx/segment.size));
//                            if (segment.y1>segment.y2){
//                                segment.orientation = 180-segment.orientation;
//                            }
                            
                            if(showIntensity){
                                // if paint inside
                                // for all points in inside array
                                // increase intensity by x
                                // along inside axe perpendicular to orientation
                                // function :
                                double insideDir = findInsideDirOfSegment(segment,fr);
                                
                                if(insideDir!=400){
                                    // then find start point of rectangle
                                    //           xi = cos(inside_dir)(degreetorad) * length
                                    //           yi = sin(inside_dir)(degreetorad) * length
                                    
                                    int xi = segment.x1 + (int)(Math.cos(Math.toRadians(insideDir))*in_length+0.5); // +0.5 for rounding, maybe not
                                    int yi = segment.y1 + (int)(Math.sin(Math.toRadians(insideDir))*in_length+0.5); // +0.5 for rounding, maybe not
                                    
                                    //  then increase intensity in rectangle
                                    increaseIntensityOfRectangle(segment.x1,segment.y1,xi,yi,segment.x2,segment.y2);
                                } 
                            }
                            
                            // store segment
                            if(indexSegment<MAX_ORIENTATIONS){
                               segments[indexSegment] = segment;
                               indexSegment++;
                            } else {
                                overMaxNbSegments++;
                            }
                            
                            
                        } //end if neighbour is null
                    }

                }
            }
        }
        if (overMaxNbSegments>0) {
            int exceed = overMaxNbSegments+indexSegment;
            System.out.println("detectSegments segments exceed max: "+exceed+" > "+MAX_ORIENTATIONS);
            
        }
       
        return indexSegment;
    }
    
     // detect inside direction
//           test both orthogonal directions to segment orientation
//                   first orthogonal direction:
//                   point a : xa = cos(ori+90)(degreetorad) * testlength
//                   ya = sin(ori+90)(degreetorad) * testlength
//                   then second :
//                   point b : xb = cos(ori-90)(degreetorad) * testlength
//                   yb = sin(ori-90)(degreetorad) * testlength
//
//                   va = testline(segment.midx,midy,xa,ya)
//                   vb = testline(segment.midx,midy,xb,yb)
//                   if va>vb inside_dir = ori+90 else = ori-90

    protected double findInsideDirOfSegment( Segment s, float[][][] fr){
        double dir = 0;
        double testLength = in_test_length; // 
        
        int midx = s.midx(); //int)((s.x1+s.x2)/2); //+0.5?
        int midy = s.midy(); //int)((s.y1+s.y2)/2);
        
        // points defining lines at the two orthogonal directions to segment orientation
        int xa = midx + (int)(Math.cos(Math.toRadians(s.orientation+90))*testLength); // +0.5 for rounding, maybe not
        int ya = midy + (int)(Math.sin(Math.toRadians(s.orientation+90))*testLength); // +0.5 for rounding, maybe not
        int xb = midx + (int)(Math.cos(Math.toRadians(s.orientation-90))*testLength); // +0.5 for rounding, maybe not
        int yb = midy + (int)(Math.sin(Math.toRadians(s.orientation-90))*testLength); // +0.5 for rounding, maybe not
 
        
        if(xa<0||xa>=retinaSize){           
           
            //System.out.println("findInsideDirOfSegment error xa="+xa+" for midx="+midx+" and ori="+s.orientation+"+90");
            return 400;
        }
        if(xa<0||xa>=retinaSize){
         
            //System.out.println("findInsideDirOfSegment error ya="+ya+" for midy="+midy+" and ori="+s.orientation+"+90");
            return 400;
        }
         if(xb<0||xb>=retinaSize){           
          
            //System.out.println("findInsideDirOfSegment error xb="+xb+" for midx="+midx+" and ori="+s.orientation+"-90");
            return 400;
        }
        if(yb<0||yb>=retinaSize){          
        
            //System.out.println("findInsideDirOfSegment error yb="+yb+" for midy="+midy+" and ori="+s.orientation+"-90");
            return 400;
        }
        float va = meanValueOfLine(midx,midy,xa,ya,fr);
        float vb = meanValueOfLine(midx,midy,xb,yb,fr);
        
        if(va>vb){
            
                dir = s.orientation+90;
                s.xi = xa;
                s.yi = ya;
                s.xo = xb;
                s.yo = yb;
                
            
        } else { //va<vb
           
                dir = s.orientation-90;
                s.xi = xb;
                s.yi = yb;
                s.xo = xa;
                s.yo = ya;
            
        }
        
        return dir;
    }
   
    // stops if touching door or pixel below threshold
    protected Vector longestLine(int x, int y, int range, int midlength, float[][][] accEvents){
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
                                if(accEvents[y0][x0][0]>threshold){//should check if within shape
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
                            } else {
                                break;
                                
                            }
                        }
                    }
                   // end computing line, end point in x0,y0
                    
                   //if(length>lengthMax){
                        lengthMax=length;
                        xEnd = x0;
                        yEnd = y0;
                                   
                        xMid = midx;
                        yMid = midy;
                        touchesDoor = touching;
                        
                        //
                        
                        Line line = new Line(x,y,xEnd,yEnd,lengthMax);
                         line.touchesDoor = touchesDoor;
                         line.midx = xMid;
                         line.midy = yMid;
                         lines.add(line);
                   //} 
                    
                    
                } // end if on outline                                   
              }          
           } //end for all points on square's outline
               
           
           if(lengthMax>0){
               // got a line, store it
                // compute mid length points
               
//               line = new Line(x,y,xEnd,yEnd,lengthMax);
//               line.touchesDoor = touchesDoor;
//               line.midx = xMid;
//               line.midy = yMid;
               // to put back, paul
               //int[] res = computeMidShift(line);
               //line.midx = res[0];
               //line.midy = res[1];
               
           }
           
       
      
        return lines;
    }
    
    
    // shift point to middle of finger width
    protected int[] computeMidShift( Line line ){
        int[] res = new int[2];
        int maxRange = fing_maxRange; // to parametrize in gui
        int minRange = fing_minRange; // to parametrize in gui
        // compute orientation of line
          double dx = (double)(line.x1-line.x0);
          double dy = (double)(line.y1-line.y0);                                                  
          double size = Math.sqrt((dy*dy)+(dx*dx));                                                     
          double orientation = Math.toDegrees(Math.acos(dx/size));
        
          int xa = line.midx + (int)(Math.cos(Math.toRadians(orientation+90))*maxRange); // +0.5 for rounding, maybe not
          int ya = line.midy + (int)(Math.sin(Math.toRadians(orientation+90))*maxRange); // +0.5 for rounding, maybe not
          int xb = line.midx + (int)(Math.cos(Math.toRadians(orientation-90))*maxRange); // +0.5 for rounding, maybe not
          int yb = line.midy + (int)(Math.sin(Math.toRadians(orientation-90))*maxRange); // +0.5 for rounding, maybe not

          // now trace segment lines until shape or max
          int lengthA = traceLine(line.midx,line.midy,xa,ya,maxRange);
          int lengthB = traceLine(line.midx,line.midy,xb,yb,maxRange);
        
          if((lengthA<minRange)&&(lengthB<minRange)){
              res[0] = (int)(((float)xa+xb)/2);
              res[1] = (int)(((float)ya+yb)/2);
              
          } else if(lengthA<minRange){
              res[0] = xa + (int)(Math.cos(Math.toRadians(orientation-90))*minRange);
              res[1] = ya + (int)(Math.cos(Math.toRadians(orientation-90))*minRange);
          
          } else if(lengthB<minRange){
              res[0] = xb + (int)(Math.cos(Math.toRadians(orientation+90))*minRange);
              res[1] = yb + (int)(Math.cos(Math.toRadians(orientation+90))*minRange);
          } else {
              // at middle of max range
              res[0] = (int)(((float)xa+xb)/2);
              res[1] = (int)(((float)ya+yb)/2);
          }
          
         
          
        return res;
    }
       
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
                } else {
                    break;                    
                }
                
                
        
            }
        }
        
       
        return length;
         
    }
    
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
     
     
    protected Sequence createSequence( Segment o1, Segment o2, Contour contour, double angle, int size){
        Sequence s = new Sequence();
        s.reset();
        if (size>MAX_SEQ_LENGTH) return s;
        // select extremity of o2 opposed to o1
        int x,y=0;
        if ((o2.x1==o1.x2)||(o2.x1==o1.x1)) {
            x=o2.x2; 
            y=o2.y2;
        } else {
            x=o2.x1; 
            y=o2.y1;
        }
        // from this x,y event location, find neighbours as before
        // for all its neighbours, create Segment
         Segment neighbouringSegments[];
         neighbouringSegments = new Segment[100];// size should depends on linkSize
         int around = linkSize;
         int indexNeighbour = findSurroundingSegments(neighbouringSegments,contour,x,y,around,1 );
                        
        
        
        // select the one closest in angle to o2 orientation +- angle
         double currentAngle = 0;
         double tolerance = seqTolerance;                   
         double min = 180;
         Segment o3 = null;
         
         for(int m=0;m<indexNeighbour;m++){
                 if((neighbouringSegments[m].x1!=o2.x1)&&(neighbouringSegments[m].y1!=o2.y1)&&
                         (neighbouringSegments[m].x2!=o2.x2)&&(neighbouringSegments[m].y2!=o2.y2)){
                     // only if segment o2 and neighbour are different
                     
                     currentAngle = o2.orientation - neighbouringSegments[m].orientation;
                     if (currentAngle>180) currentAngle = 360 - currentAngle;
                     double diffAngle = Math.abs(currentAngle-angle);
                     if (diffAngle<tolerance){
                         if (diffAngle<min){
                             min = diffAngle;
                             o3 = neighbouringSegments[m];
                         }
                     }   
                 }
         }
         
         
        // if none then return empty seq
       // return s;
        // if one then continue chain
        if (o3!=null){
            
            s.add(o3);
            //System.out.println("recursive call "+size);
            s.mergeWith(createSequence(o2,o3,contour,angle,size+1));
        } 
             
        return s;
        
        
       
        
    }
    
    /**
     * filterFrame : low pass filter, apply some kind of gaussian noise on pixel of frame
     */
    float[][][] lowFilterFrame( float[][][] frame ){
        float[][][] res = new float[retinaSize][retinaSize][frame[0][0].length];
        int radius = lowFilter_radius;
        int density = lowFilter_density; // >0
        float invDensity = 1/(float)density;
        int radiusSq = radius*radius;
        // for all points of frame
        // for all points in square around 
        // if point within circle
        //     add value by distance
        //     number++
        // end if
        // end for
        // average on number
        // add to res
        // end for
        float[] terraces = new float[density];
        for (int k=0;k<density;k++){
            terraces[k] = (float)k/density;
        }
        for (int i=0; i<frame.length;i++){
          for (int j=0; j<frame.length;j++){ //square matrix
              //if(frame[i][j][0]>0){
                  // square inside
                  float n = 0;
                  float val = 0;
                  for (int is=i-radius; is<i+radius+1;is++){
                      if(is>=0&&is<frame.length){
                          for (int js=j-radius; js<j+radius+1;js++){
                              if(js>=0&&js<frame.length){
                                  // if within circle
                                  float dist = ((is-i)*(is-i)) + ((js-j)*(js-j));
                                  //System.out.println("dist:"+dist+" i:"+i+" j:"+j+" is:"+is+" js:"+js);
                                  if(dist<radiusSq){
                                      float f = 1;
                                      float dr = (float)Math.sqrt(dist)/(float)radius;
                                      if (dr!=0) f = 1/dr;
                                      
                                      val += frame[is][js][0] * f;
                                      n+=f;
                                      //System.out.println("dr:"+dr+" val:"+val+" n:"+n);
                                  }
                              }
                          }
                      }
                  }
                  // avg point
                  val = val/n;
                  int cat = (int)(val / invDensity);
                 //System.out.println("cat:"+cat+" val:"+val+" terraces["+cat+"]:"+terraces[cat]);
                  if(cat<0){
                      res[i][j][0] = 0;
                  } else if(cat>=density){
                      res[i][j][0] = 1;
                  } else {
                      res[i][j][0] = terraces[cat];
                      //res[j][i][0] = terraces[cat]; //inverted, alas, for insideIntensities
                  }
             // } else {
             //     res[j][i][0] = 0;
             // }
          }  
        }
        
        
        return res;
    }
    
    
    
    void checkInsideIntensityFrame(){
        if(showIntensity && insideIntensityFrame==null) createInsideIntensityFrame();
    }
     
    JFrame insideIntensityFrame=null;
    GLCanvas insideIntensityCanvas=null;
    GLU glu=null;
//    GLUT glut=null;
    void createInsideIntensityFrame(){
        insideIntensityFrame=new JFrame("Inside intensity");
        insideIntensityFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        insideIntensityCanvas=new GLCanvas();
        insideIntensityCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            synchronized public void display(GLAutoDrawable drawable) {
                if(segments==null) return;
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                //System.out.println("max point "+maxDistGC*100+" "+maxOrientation*100);
                
//                gl.glColor3f(1,0,0);
//                gl.glRectf(10,90,11,91);
//                gl.glColor3f(0,1,0);
//                gl.glRectf(20,90,21,91);
//                 gl.glColor3f(0,0,1);
//                gl.glRectf(10,45,11,46);
//              
                /*
               int size = 2;
                
               for (int k=0;k<10;k++){
                   gl.glColor3f(1,0,0);
                   gl.glRecti(90,k,90+size,k+size);
                   gl.glColor3f(0,0,1);
                   gl.glRecti(k,0,k+size,0+size);
               }
                 */
               /*
                gl.glColor3f(1,0,0);
                gl.glRecti(90,15,90+size,15+size);
                gl.glColor3f(0,1,0);
                gl.glRecti(45,3,45+size,3+size);
                gl.glColor3f(0,0,1);
                gl.glRecti(45,15,45+size,15+size);
                gl.glColor3f(0,1,1);
                gl.glRecti(45,35,45+size,35+size);
                */
                
                // display inside intensity
                
                
                        
                        
                        
               if(showAcc){
                    float max = findArrayMax(insideIntensities);
                     for (int i = 0; i<accEvents.length; i++){
                        for (int j = 0; j<accEvents[i].length; j++){
                            float f;
                            if(lowFilter){                           
                               f = filteredEvents[i][j][0];
                            } else {
                               f = accEvents[i][j][0]; 
                            }
                            float g = 0; //insideIntensities[j][i][0]/max;
                            //if(g<0)g=0;
                            gl.glColor3f(f,f+g,f);
                            gl.glRectf(j*intensityZoom,i*intensityZoom,(j+1)*intensityZoom,(i+1)*intensityZoom);
                            
                        }
                     }                                        
                    
                    
               } else {
                    
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
                
               }
                
                
                 
                 for(int i=0;i<nbSegments;i++){
                     Segment o = segments[i];
                     int midx = o.midx();
                     int midy = o.midy();
                     //  draw line here and
                     gl.glBegin(GL.GL_LINES);
                     {
                         
                         gl.glColor3f(0,1,0);
                         gl.glVertex2i(o.x1*intensityZoom,o.y1*intensityZoom);
                         gl.glVertex2i(o.x2*intensityZoom,o.y2*intensityZoom);
                         /*
                         gl.glColor3f(1,0,0);
                         gl.glVertex2i(midx*intensityZoom,midy*intensityZoom);
                         gl.glVertex2i(o.xi*intensityZoom,o.yi*intensityZoom);
                         gl.glColor3f(0,0,1);
                         gl.glVertex2i(midx*intensityZoom,midy*intensityZoom);
                         gl.glVertex2i(o.xo*intensityZoom,o.yo*intensityZoom);
                          **/
                                           
                     }
                     gl.glEnd();
                     
                     /*
                     // draw text
                     int font = GLUT.BITMAP_HELVETICA_12;
                     gl.glColor3f(1,1,1);
                     gl.glRasterPos3f(midx*intensityZoom,midy*intensityZoom,0);
                     // annotate
                     
                     chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f", (float)o.orientation ));
                     */
                     
                 }
                 
//                gl.glColor3f(1,0,0);
//                for(int i=0;i<fingerTips.size();i++){
//                    Score sc = (Score)fingerTips.elementAt(i);
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
                
                if(showFingers){
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
                    //gl.glBlendFunc(GL.GL_ONE, GL.GL_ZERO);
                }   // end if show fingers  
               

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
    
    public class Sequence{
         public Segment array[];
         public int length;
         public double angle;
         public int label;
         
         public Sequence(){
             //reset();
         }
         
         public void reset(){
            label = 0;
            angle = 0;
            length = 0;
            array = new Segment[MAX_SEQ_LENGTH];
         }
         
         public void add( Segment o ){
             if (length<MAX_SEQ_LENGTH){
                array[length]=o;
                length++;
             }        
         }
         
         public void mergeWith(Sequence s){
             if(length+s.length<MAX_SEQ_LENGTH){
                 length+=s.length;
                 
                 int j = 0;
                 for (int i=length;i<length+s.length;i++){
                     if (i<MAX_SEQ_LENGTH){
                         array[i] = s.array[j];
                         j++;
                     }
                 }
             }
         }
         
    } 
   
    public class ContourPoint{
        protected int maxX=128,maxY=128;
        public int label;
        public int x;
        public int y;
        public int on;
        public int used;
        
        public ContourPoint(){
            reset();
        }
        
        
        
        public void reset(){
            label = 0;
            on = 0;
            used = 0;
        }
        
    }
    
    
    public class Contour{
        ContourPoint eventsArray[][];
        protected int maxX=retinaSize,maxY=retinaSize;
        public int nbLabels=0;
        
        public Contour(){
            
            reset();
            
        }
        
        public void reset(){
            // should optimize that
            eventsArray = new ContourPoint[maxX][maxY];
            
            
            
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    eventsArray[i][j] = new ContourPoint();
                    
                }
            }
            
        }
        
        
        public void add(int i, int j){
            eventsArray[i][j].on = 1;
            eventsArray[i][j].x = i;
            eventsArray[i][j].y = j;
            
        }
        
        /**
         * Set label of points to 1 if group touching door zone
         */
        public void highlightTouchingDoor(){
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    if(eventsArray[i][j].on == 1){
                        // if touch door
                        if (nearDoor(eventsArray[i][j].y,eventsArray[i][j].x,linkSize)){
                            // set label to 1
                            changeLabel(eventsArray[i][j].label,1);
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
            nbLabels = n;
        }
        
       protected void extendSegmentFollowingPolicy( Segment segment, ContourPoint p , int min_x_policy, int max_x_policy, int min_y_policy, int max_y_policy, int curSegSize ){         
           
           // look for neighbours in range allowed by policy
           ContourPoint neighbour = contour.getNeighbour(p.x,p.y,min_x_policy,max_x_policy,min_y_policy,max_y_policy);
                  
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
           
           extendSegmentFollowingPolicy(segment,neighbour,min_x_policy,max_x_policy,min_y_policy,max_y_policy,curSegSize+1);
           
          
           
       }
        
        
        
        // return one neighbour
       
       protected ContourPoint getNeighbour(int x, int y, int range ){
           return getNeighbour(x,y,range,range,range,range);
           
           
       }
         
       protected ContourPoint getNeighbour(int x, int y, int low_xrange, int high_xrange, int low_yrange, int high_yrange){
             for (int i=x-low_xrange;i<x+high_xrange;i++){
                if (i>0&&i<maxX){                  
                    for (int j=y-low_yrange;j<y+high_yrange;j++){
                        if (j>0&&j<maxY){
                            if(i==x&&j==y){
                                
                            } else {
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
             return null;            
         }
                
        
        // find all neighbours and label points as neighbours
        protected void findNeighbours(int x, int y, int around){
            for (int i=x-around;i<x+around;i++){
                if (i>0&&i<maxX){
                    
                    for (int j=y-around;j<y+around;j++){
                        if (j>0&&j<maxY){
                            if(i==x&&j==y){
                                
                            } else {
                                if(eventsArray[i][j].on == 1){
                                    addAsNeighbours(eventsArray[x][y],eventsArray[i][j]);
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
                    if(eventsArray[i][j].label==l2){
                        eventsArray[i][j].label = l1;
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
    
    
    
    /** A zone getting events in which we want to find specific clusters */
    // to add : subzone, and contrast correction
    public class Zone{
        String name = "";
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        protected int type; // polarity: On or Off
        protected int numEvents;
        protected int timestamp;
        protected int xa=0,xb=0,ya=0,yb=0;
        protected int xa2=0,xb2=0,ya2=0,yb2=0; //subzone
        protected int maxX=128,maxY=128;
        // here add an array of coordinates
        int eventsArray[][];
        int initValue = 0;
        
        
        public Zone(){
            
            reset();
            type=1; // white?
            
        }
        
        public Zone(String name, int maxX, int maxY, int initValue){
            this.initValue = initValue;
            this.name = name;
            this.maxX = maxX;
            this.maxY = maxY;
            reset();
            type=1; // white?
            
        }
        
        
        
        
        public void reset(){
            
            eventsArray = new int[maxX][maxY];
            
            numEvents = 0;
            
            for (int i=0;i<maxX;i++){
                for (int j=0;j<maxY;j++){
                    eventsArray[i][j] = initValue;
                    
                }
            }
            
        }
        
        
        
        public void setCoordinates( int xa, int xb, int ya, int yb){
            this.xa = xa;
            this.xb = xb;
            this.ya = ya;
            this.yb = yb;
        }
        
        public void setSubZone( int xa, int xb, int ya, int yb){
            this.xa2 = xa;
            this.xb2 = xb;
            this.ya2 = ya;
            this.yb2 = yb;
        }
        
        
        
        public void addEvent(TypedEvent event){
            
            
            if(event.x>=xa&&event.x<xb&&event.y>=ya&&event.y<yb){
                
                if(event.type==0){
                    eventsArray[event.x-xa][event.y-ya]--;
                } else {
                    eventsArray[event.x-xa][event.y-ya]++;
                }
                //timestamp = event.timestamp;
                numEvents++;
            }
            
            
        }
        
        
        // findFingerTip
        //        find tip near door then move inside paw contour until cannot go further
        //        repeat fo reach finger, assign probability to each and in the end keep only
        //        those with high enouh proba
        // end findFingerTip
        
        
        public int findFingers( int pixel_threshold){
            int nbFingers=0;
            
            
            // while fingers not all found and still positive pixels
            //        find closest positive pixel to door
            //        find finger tip (orientation of search depends on door frame side)
            //        then move finger tip along contour , erase pixels behind on its path
            //             stop when not enough positive pixels around it (cannot turn more than angle a)
            //                   (notive where angle is quite sharp, could be articulations?)
            //             assign probability (depends on closeness to door and other finger tips,..)
            //             store
            // end while
            
            
            return nbFingers;
        } //end findFingers
        
        
        
        
        
        
        
    } // end class Zone
    
    
    
    
    
    
    
    
    
    
    
    
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
        if (showIntensity) insideIntensityCanvas.repaint();
        return in;
    }
    
    
    
    //  private boolean highwayPerspectiveEnabled=prefs.getBoolean("PawTracker2.highwayPerspectiveEnabled",false);
    
//    public boolean isHighwayPerspectiveEnabled() {
//        return highwayPerspectiveEnabled;
//    }
//
//    public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled) {
//        this.highwayPerspectiveEnabled = highwayPerspectiveEnabled;
//        prefs.putBoolean("PawTracker2.highwayPerspectiveEnabled",highwayPerspectiveEnabled);
//    }
    
    public float getMixingFactor() {
        return mixingFactor;
    }
    
    public void setMixingFactor(float mixingFactor) {
        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
        this.mixingFactor = mixingFactor;
        prefs.putFloat("PawTracker2.mixingFactor",mixingFactor);
    }
    
    
    
    
    
    /** @see #setSurround */
    public float getSurround() {
        return surround;
    }
    
    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
     * @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
     */
    public void setSurround(float surround){
        if(surround < 1) surround = 1;
        this.surround = surround;
        prefs.putFloat("PawTracker2.surround",surround);
    }
    
    /** @see #setPathsEnabled
     */
//    public boolean isPathsEnabled() {
//        return pathsEnabled;
//    }
//
//    /** @param pathsEnabled true to show the history of the cluster locations on each packet */
//    public void setPathsEnabled(boolean pathsEnabled) {
//        this.pathsEnabled = pathsEnabled;
//        prefs.putBoolean("PawTracker2.pathsEnabled",pathsEnabled);
//    }
    
    /** @see #setDynamicSizeEnabled
     */
//    public boolean getDynamicSizeEnabled(){
//        return dynamicSizeEnabled;
//    }
    

    
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }
    
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
//    public boolean isUseOnePolarityOnlyEnabled() {
//        return useOnPolarityOnlyEnabled;
//    }
//
//    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
//        this.useOnPolarityOnlyEnabled = useOnPolarityOnlyEnabled;
//        prefs.putBoolean("PawTracker2.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
//    }
    
//    public boolean isUseOffPolarityOnlyEnabled() {
//        return useOffPolarityOnlyEnabled;
//    }
//
//    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
//        this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
//        prefs.putBoolean("PawTracker2.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
//    }
    
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
            log.warning("null GL in PawTracker2.annotate");
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
            
            int sy=fingertip_size; // sx sy are (half) size of rectangle
            int sx=fingertip_size;
            
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
                for (int i=0;i<contour.getMaxX();i++){
                    for (int j=0;j<contour.getMaxY();j++){
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
                
            } else { //show shape
                if(showSequences){
                    // for all sequences, draw orientations points if length > threshold
                    
                    for (int i=0;i<sequenceSize;i++){
                        Sequence s = sequences[i];
//                        if (s==null){
//                                
//                                System.out.println("s["+i+"] null but size "+sequenceSize);
//                            }
                        // test length and show only if above threshold
                        if(s.length>minSeqLength){
                            for(int j=0;j<s.length;j++){
//                            if (s.array==null){
//
//                                System.out.println("s["+i+"].array null but size "+sequenceSize);
//                            }
                                Segment o = s.array[j];
                                if (o==null){
                                    
                                    // System.out.println(s+" s["+i+"].array["+j+"] null but length "+s.length);
                                } else {
                                    
                                    gl.glBegin(GL.GL_LINES);
                                    {
                                        gl.glVertex2i(o.x1,o.y1);
                                        gl.glVertex2i(o.x2,o.y2);
                                    }
                                    gl.glEnd();
                                }
                            }
                        }
                    }
                    // continue here
                } else { //do not show sequences but segments
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
                }
            } // end showShape
            
            gl.glColor3f(1,0,0);
            // draw gc
            int i = gc.x;
            int j = gc.y;
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2i(i,j);
                gl.glVertex2i(i+1,j);
                gl.glVertex2i(i+1,j+1);
                gl.glVertex2i(i,j+1);
            }
            gl.glEnd();
            
            
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
    
    
    
    public void setEntry_x(int entry_x) {
        this.entry_x = entry_x;
        prefs.putInt("PawTracker2.entry_x",entry_x);
    }
    
    public int getEntry_x() {
        return entry_x;
    }
    
    public void setEntry_y(int entry_y) {
        this.entry_y = entry_y;
        prefs.putInt("PawTracker2.entry_y",entry_y);
    }
    public int getEntry_y() {
        return entry_y;
    }
    
    public void setEntry_z(int entry_z) {
        this.entry_z = entry_z;
        prefs.putInt("PawTracker2.entry_z",entry_z);
    }
    public int getEntry_z() {
        return entry_z;
    }
    
    
    public void setClean(boolean clean){
        this.clean = clean;
        prefs.putBoolean("PawTracker2.clean",clean);
    }
    public boolean isClean(){
        return clean;
    }
            
    public void setClean_threshold(int clean_threshold) {
        this.clean_threshold = clean_threshold;
        prefs.putInt("PawTracker2.clean_threshold",clean_threshold);
    }
    
    public int getClean_threshold() {
        return clean_threshold;
    }
    public void setIn_threshold(float in_threshold) {
        this.in_threshold = in_threshold;
        prefs.putFloat("PawTracker2.in_threshold",in_threshold);
    }
    public float getIn_threshold() {
        return in_threshold;
    }
    
    public void setLine_threshold(float line_threshold) {
        this.line_threshold = line_threshold;
        prefs.putFloat("PawTracker2.line_threshold",line_threshold);
    }
    public float getLine_threshold() {
        return line_threshold;
    }
    
    public void setLine_range(int line_range) {
        this.line_range = line_range;
        prefs.putInt("PawTracker2.line_range",line_range);
    }
    public int getLine_range() {
        return line_range;
    }
    
    public void getFing_maxRange(int fing_maxRange) {
        this.fing_maxRange = fing_maxRange;
        prefs.putInt("PawTracker2.fing_maxRange",fing_maxRange);
    }
    public int getFing_maxRange() {
        return fing_maxRange;
    }
     
    public void setFing_minRange(int fing_minRange) {
        this.fing_minRange = fing_minRange;
        prefs.putInt("PawTracker2.fing_minRange",fing_minRange);
    }
    public int getFing_minRange() {
        return fing_minRange;
    }
     
    
            
            
            
    public void setScore_range(int score_range) {
        this.score_range = score_range;
        prefs.putInt("PawTracker2.score_range",score_range);
    }
    public int getScore_range() {
        return score_range;
    }
    
    public void setScore_threshold(int score_threshold) {
        this.score_threshold = score_threshold;
        prefs.putInt("PawTracker2.score_threshold",score_threshold);
    }
    public int getScore_threshold() {
        return score_threshold;
    }
    
     public void setScore_in_threshold(float score_in_threshold) {
        this.score_in_threshold = score_in_threshold;
        prefs.putFloat("PawTracker2.score_in_threshold",score_in_threshold);
    }
    public float getScore_in_threshold() {
        return score_in_threshold;
    }
    
    
    public void setSeqTolerance(float seqTolerance) {
        this.seqTolerance = seqTolerance;
        prefs.putFloat("PawTracker2.seqTolerance",seqTolerance);
    }
    public float getSeqTolerance() {
        return seqTolerance;
    }
    
    public void setMinDiff(float minDiff) {
        this.minDiff = minDiff;
        prefs.putFloat("PawTracker2.minDiff",minDiff);
    }
    public float getMinDiff() {
        return minDiff;
    }
    
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        prefs.putInt("PawTracker2.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }    
    
    
            
    public void setIn_length(int in_length) {
        this.in_length = in_length;
        prefs.putInt("PawTracker2.in_length",in_length);
    }
    
    public int getIn_length() {
        return in_length;
    }   
    
    public void setIn_test_length(int in_test_length) {
        this.in_test_length = in_test_length;
        prefs.putInt("PawTracker2.in_test_length",in_test_length);
    }
    
    public int getIn_test_length() {
        return in_test_length;
    }   
    
    public void setDoor_xa(int door_xa) {
        this.door_xa = door_xa;
        prefs.putInt("PawTracker2.door_xa",door_xa);
    }
    
    public int getDoor_xa() {
        return door_xa;
    }
    
    public void setDoor_xb(int door_xb) {
        this.door_xb = door_xb;
        prefs.putInt("PawTracker2.door_xb",door_xb);
    }
    
    public int getDoor_xb() {
        return door_xb;
    }
    
    public void setDoor_ya(int door_ya) {
        this.door_ya = door_ya;
        prefs.putInt("PawTracker2.door_ya",door_ya);
    }
    
    public int getDoor_ya() {
        return door_ya;
    }
    
    public void setDoor_yb(int door_yb) {
        this.door_yb = door_yb;
        prefs.putInt("PawTracker2.door_yb",door_yb);
    }
    
    public int getDoor_yb() {
        return door_yb;
    }
    
    public void setMaxDiff(float maxDiff) {
        this.maxDiff = maxDiff;
        prefs.putFloat("PawTracker2.maxDiff",maxDiff);
    }
    public float getMaxDiff() {
        return maxDiff;
    }
    
    public void setDoorMinDiff(float doorMinDiff) {
        this.doorMinDiff = doorMinDiff;
        prefs.putFloat("PawTracker2.doorMinDiff",doorMinDiff);
    }
    public float getDoorMinDiff() {
        return doorMinDiff;
    }
    
    public void setDoorMaxDiff(float doorMaxDiff) {
        this.doorMaxDiff = doorMaxDiff;
        prefs.putFloat("PawTracker2.doorMaxDiff",doorMaxDiff);
    }
    public float getDoorMaxDiff() {
        return doorMaxDiff;
    }
    
    public void setMinZeroes(int minZeroes) {
        this.minZeroes = minZeroes;
        prefs.putInt("PawTracker2.minZeroes",minZeroes);
    }
    public int getMinZeroes() {
        return minZeroes;
    }
    
    public void setMaxZeroes(int maxZeroes) {
        this.maxZeroes = maxZeroes;
        prefs.putInt("PawTracker2.maxZeroes",maxZeroes);
    }
    
    public int getMaxZeroes() {
        return maxZeroes;
    }
    
    public void setMinAngle(float minAngle) {
        this.minAngle = minAngle;
        prefs.putFloat("PawTracker2.minAngle",minAngle);
    }
    public float getMinAngle() {
        return minAngle;
    }
    
  
    
    public void setDoorMinZeroes(int doorMinZeroes) {
        this.doorMinZeroes = doorMinZeroes;
        prefs.putInt("PawTracker2.doorMinZeroes",doorMinZeroes);
    }
    public int getDoorMinZeroes() {
        return doorMinZeroes;
    }
    
    public void setDoorMaxZeroes(int doorMaxZeroes) {
        this.doorMaxZeroes = doorMaxZeroes;
        prefs.putInt("PawTracker2.doorMaxZeroes",doorMaxZeroes);
    }
    public int getDoorMaxZeroes() {
        return doorMaxZeroes;
    }
    
    public void setFingertip_size(int fingertip_size) {
        this.fingertip_size = fingertip_size;
        prefs.putInt("PawTracker2.fingertip_size",fingertip_size);
    }
    public int getFingertip_size() {
        return fingertip_size;
    }
    
    
//    public void setMainZone_initValue( int mainZone_initValue ){
//        this.mainZone_initValue = mainZone_initValue;
//        prefs.putInt("PawTracker2.mainZone_initValue",mainZone_initValue);
//    }
//
//    public int getMainZone_initValue() {
//        return mainZone_initValue;
//    }
//
//
//    public void setMainZone_pixel_threshold(int mainZone_pixel_threshold) {
//        this.mainZone_pixel_threshold = mainZone_pixel_threshold;
//        prefs.putInt("PawTracker2.mainone_pixel_threshold",mainZone_pixel_threshold);
//    }
//
//    public int getMainZone_pixel_threshold() {
//        return mainZone_pixel_threshold;
//    }
//
    
    
            
    public int getMinSeqLength() {
        return minSeqLength;
    }
    
    public void setMinSeqLength(int minSeqLength) {
        this.minSeqLength = minSeqLength;
        
    }     
            
    public int getMax_fingers() {
        return max_fingers;
    }
    
    public void setMax_fingers(int max_fingers) {
        this.max_fingers = max_fingers;
        
    }
    
//    public int getRetinaSize() {
//        return retinaSize;
//    }
//    
//    public void setRetinaSize(int retinaSize) {
//        this.retinaSize = retinaSize;
//        prefs.putInt("PawTracker2.retinaSize",retinaSize);
//        
//    }
    
    public int getLinkSize() {
        return linkSize;
    }
    
    public void setLinkSize(int linkSize) {
        this.linkSize = linkSize;
        prefs.putInt("PawTracker2.linkSize",linkSize);
    }
    
    public int getSegSize() {
        return segSize;
    }
    
    public void setSegSize(int segSize) {
        this.segSize = segSize;
        prefs.putInt("PawTracker2.segSize",segSize);
    }
    
    public int getMaxSegSize() {
        return maxSegSize;
    }
    
    public void setMaxSegSize(int maxSegSize) {
        this.maxSegSize = maxSegSize;
        prefs.putInt("PawTracker2.maxSegSize",maxSegSize);
    }
    
            
    private boolean resetPawTracking=prefs.getBoolean("PawTracker2.resetPawTracking",false);
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        prefs.putBoolean("PawTracker2.resetPawTracking",resetPawTracking);
        
    }
    
    
    
    
    
    public void setShowSegments(boolean showSegments){
        this.showSegments = showSegments;
        prefs.putBoolean("PawTracker2.showSegments",showSegments);
    }
    public boolean isShowSegments(){
        return showSegments;
    }
    
    
    
      
    public void setScaleIntensity(boolean scaleIntensity){
        this.scaleIntensity = scaleIntensity;
        prefs.putBoolean("PawTracker2.scaleIntensity",scaleIntensity);
    }
    public boolean isScaleIntensity(){
        return scaleIntensity;
    }
    
    public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        prefs.putBoolean("PawTracker2.showAcc",showAcc);
    }
    public boolean isShowAcc(){
        return showAcc;
    }
    /*
    public void setOpposite(boolean opposite){
        this.opposite = opposite;
        prefs.putBoolean("PawTracker2.opposite",opposite);
    }
    public boolean isOpposite(){
        return opposite;
    }
    */
    public void setShowIntensity(boolean showIntensity){
        this.showIntensity = showIntensity;
        prefs.putBoolean("PawTracker2.showIntensity",showIntensity);
    }
    public boolean isShowIntensity(){
        return showIntensity;
    } 
    
    public void setHideInside(boolean hideInside){
        this.hideInside = hideInside;
        prefs.putBoolean("PawTracker2.hideInside",hideInside);
    }
    public boolean isHideInside(){
        return hideInside;
    }      
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        prefs.putBoolean("PawTracker2.showfingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
        prefs.putBoolean("PawTracker2.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
        prefs.putBoolean("PawTracker2.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
    
       
    public void setShowSequences(boolean showSequences){
        this.showSequences = showSequences;
        prefs.putBoolean("PawTracker2.showSequences",showSequences);
    }
    public boolean isShowSequences(){
        return showSequences;
    }
    
    public void setShowShape(boolean showShape){
        this.showShape = showShape;
        prefs.putBoolean("PawTracker2.showShape",showShape);
    }
    public boolean isShowShape(){
        return showShape;
    }
    
    
    
    public void setSmoothShape(boolean smoothShape){
        this.smoothShape = smoothShape;
        prefs.putBoolean("PawTracker2.smoothShape",smoothShape);
    }
    public boolean isSmoothShape(){
        return smoothShape;
    }  
    
    public void setLowFilter(boolean lowFilter){
        this.lowFilter = lowFilter;
        prefs.putBoolean("PawTracker2.lowFilter",lowFilter);
    }
    public boolean isLowFilter(){
        return lowFilter;
    } 
    
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        prefs.putInt("PawTracker2.lowFilter_radius",lowFilter_radius);
    }
    
      public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        prefs.putInt("PawTracker2.lowFilter_density",lowFilter_density);
    }
            
}
