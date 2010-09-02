/*
 * PawTrackerStereoBoard3Label.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * Data must be recorded via stereoboard
 * version 2 differs from 1 by rendering 3D in real coordinates
 * version 3 differs from 2 by adding player/recorder interface
 * version 3 'Label' removes player/recorder interface and match groups of neighboors events
 * instead of just events
 *
 * Paul Rogister, Created on October, 2007
 *
 */


package ch.unizh.ini.jaer.projects.pawtracker;

import ch.unizh.ini.jaer.projects.stereo3D.*;

import net.sf.jaer.chip.*;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.*;
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

import javax.imageio.*;
import java.awt.image.*;
import java.nio.ByteBuffer;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.text.*;
/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class PawTrackerStereoBoard3Label extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    //debug
    int minrightZ = 3000;
    int minleftZ = 3000;
    
    boolean parameterComputed = false;
    
    // recorder /player variables
    
    int INITIAL_PACKET_SIZE = 1000;
    
    public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
    
    AE3DOutputStream loggingOutputStream;
    AE3DFileInputStream inputStream;
    
    AE3DPlayerRecorderFrame playerRecorderFrame;
  
 //   File recordFile;
//    File playFile;
 //   float playTimeBin = 20000; // micro s
 //   boolean forward = true;
 //   boolean pauseEnabled = false;
 //   boolean playEnabled = false;
 //   boolean displayPlay = false;
 //   boolean recordEnabled = false;
  //  boolean dopause = false;
    volatile boolean toclear = true;
//    AEPacket3D loggingPacket;
    AEPacket3D inputPacket;
//    AEPacket3D displayPacket = null;
//    Hashtable direct3DEvents = new Hashtable();
 //   volatile boolean displayDirect3D = false;
 //   volatile boolean recordPure3D = false;


    Hashtable groupLabels = new Hashtable();

    // log data
    File logFile;
    BufferedWriter logWriter;
    volatile boolean logEnabled = false;
    
    volatile boolean pngRecordEnabled = false;
    int frameNumber = 0;
    
     //****************************************************
     // player interface
   
  
    
   synchronized public void clear(){
       
       toclear = true;
   }
 
    void clearEventPoints(){
      //  leftPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<leftPoints.length; i++){
            for (int j=0; j<leftPoints[i].length; j++){
                leftPoints[i][j].shortFilteredValue = 0;
            }
        }
     //   rightPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<rightPoints.length; i++){
            for (int j=0; j<rightPoints[i].length; j++){
                rightPoints[i][j].shortFilteredValue = 0;
                // + accValue .. ?
            }
        }
        
        
    }
  
    //****************************************************
    // recorder interface
        
    
    
   

    
    /***************** log ********************/
    public void doLog(  ){
        if(recordTrackerData&&!logEnabled){
            initLogData();
        } else if(!recordTrackerData&&logEnabled){
            closeLogData();
            
            if(recordUpTo!=0){
                // pause
                chip.getAeViewer().aePlayer.pause();
            }
        }            
    }
    
    public void doPause(){
        if(chip.getAeViewer().aePlayer.isPaused()){
            chip.getAeViewer().aePlayer.resume();
        } else {
            chip.getAeViewer().aePlayer.pause();
        }
             
    }
    
    public void initLogData(  ){
        
        try {
            
            
            // getString filename from current dat
       
    //   System.out.println("trying initlog: ");
       
            if(chip.getAeViewer().getAePlayer().getAEInputStream()==null) return;
           
            String filename = chip.getAeViewer().getAePlayer().getAEInputStream().getFile().getPath();//   .getName();
            // what kind of possibleerrors here?
         //  System.out.println("initlog1: "+filename);
            int idat = filename.indexOf(AEDataFile.DATA_FILE_EXTENSION);
            
       //     int logTime = currentTime;
        //    if(logTime==0) 
             int logTime = chip.getAeViewer().getAePlayer().getAEInputStream().getCurrentStartTimestamp();           
            
            filename = new String(filename.substring(0,idat) + "-" + logTime + ".txt");
            System.out.println("initLogData save log to : "+filename);
            
            
            logFile=new File(filename);
            logWriter = new BufferedWriter(new FileWriter(logFile));
          //  loggingPacket = new AEPacket3D(INITIAL_PACKET_SIZE,type);
            logEnabled=true;
       
          //  loggingOutputStream=new AE3DOutputStream(new BufferedOutputStream(new FileOutputStream(recordFile)),type);
            
        }catch(IOException e){
            
            e.printStackTrace();
        } 
    }
    
     public void logData( String s ){
         
         if(logEnabled&&logWriter!=null){
             try {
                 logWriter.write( s );
             }catch(IOException e){
                 
                 e.printStackTrace();
             }
         }

     }
     
     public void closeLogData(  ){
         logEnabled = false;
         if(logWriter!=null){
             try {
                 logWriter.close( );
             }catch(IOException e){
                 
                 e.printStackTrace();
             }
             logWriter = null;
         }
         
         
     }
     
     
     
     synchronized void writeMovieFrame(){
         try {
             
             String homeDir=System.getProperty("user.dir");
             Container container=a3DFrame.getContentPane();
             
             Rectangle r = a3DCanvas.getBounds();
             Image image = a3DCanvas.createImage(r.width, r.height);
             Graphics g = image.getGraphics();
             String filename = new String("image"+frameNumber+".png");
             synchronized(container){
                 container.paintComponents(g);
                // if(!isOpenGLRenderingEnabled()){
                 //    chip.getCanvas().paint(g);
                 //    ImageIO.write((RenderedImage)image, "png", new File(sequenceDir,getFilename()));
                // }else if(chip.getCanvas().getImageOpenGL()!=null){
                // a3DCanvas.paint(g);
                 //    ImageIO.write((RenderedImage)image, "png", new File(homeDir,filename));
                 if(image3DOpenGL!=null){   
                     ImageIO.write(image3DOpenGL, "png", new File(homeDir,filename));
                 }
                     
                     
                // }
             }
             frameNumber++;
         } catch (IOException ioe) {
             ioe.printStackTrace();
         }
     }
     
     
    
    // eps is square root of machine precision
    protected double EPS =  1.095e-8; //sqrt(1.2e-16);
    
    // in stereoarrays?
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
    protected final int RIGHT_MOST_METHOD = 1;
    protected final int LEFT_MOST_METHOD = 0;
    protected final int BIGGEST_METHOD = 2;


    
    protected final int NO_LINK = -1;
    protected final int DELETE_LINK = -2;
    
    
    
    // end of in stereoarrays
    
    // Global constant values
    
    protected int labelNumber = 0;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    /** the number of classes of objects */
  //  private final int NUM_CLASSES=2;
    // max number of orientations
 //   private final int MAX_SEGMENTS=10000;
    //private final int MAX_DISTGC=300;//depends
    //private final int MAX_SEQ_LENGTH=50;
    //private final int MAX_NB_SEQ=310;
    
  //  private final int MAX_NB_FINGERS=5;
    
 //   private double maxOrientation = 180;
 //   private double maxDistGC = 200;
    
 //   private float middleAngle;
    
    
    private int left_focal_x = 0;
    private int left_focal_y = 0;
    private int left_focal_z = 0;
    private int right_focal_x = 0;
    private int right_focal_y = 0;
    private int right_focal_z = 0;
    private Cage cage;
    private Zone searchSpace;
    
    private Zone startSpace;
    private Zone endSpace;
    private Zone noseSpace;
    
    
    float scaleFactor = 25;
    
    float step = 0.33334f;

   float[] retina_translation = new float[3];
   float fc_left_dist = 0;
   float fc_right_dist = 0;
   private Point left_focal;
   private Point right_focal;
   float[] retina_rotation = new float[9];
   float[] world_rotation = new float[9];


 //  Event3D[][] leftRetina3D;
//   Event3D[][] rightRetina3D;


   Point[][] leftRetinaCoordinates;
   Point[][] rightRetinaCoordinates;

  

    float epipolar_distance = 0;
    double epipolar_angle_min;
    double epipolar_angle_max;
    double epipolar_angle_span;
    double epipolar_distance_span;
    Point left_epipole = new Point();
    Point right_epipole = new Point();;
    // Parameters appearing in the GUI
    
  //  private float planeAngle=getPrefs().getFloat("PawTrackerStereoBoard3Label.planeAngle",-30.0f);
 //   private float platformAngle=getPrefs().getFloat("PawTrackerStereoBoard3Label.platformAngle",-20.0f);
    
    private float retina_tilt_angle=getPrefs().getFloat("PawTrackerStereoBoard3Label.retina_tilt_angle",0.0f);
    {setPropertyTooltip("retina_tilt_angle","forward tilt angle of the retinae, in degrees");}
    private float retina_height=getPrefs().getFloat("PawTrackerStereoBoard3Label.retina_height",30.0f);
    {setPropertyTooltip("retina_height","height of retina lens, in mm");}
    private float cage_distance=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_distance",100.0f);
    {setPropertyTooltip("cage_distance","distance between stereoboard center and cage door, in mm");}
    private float cage_door_height=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_door_height",30.0f);
    {setPropertyTooltip("cage_door_height","height of the cage door, in mm");}
    private float cage_height=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_height",400.0f);
    {setPropertyTooltip("cage_height","height of the cage, in mm");}
    private float cage_width=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_width",200.0f);
    {setPropertyTooltip("cage_width","width of the cage, in mm");}
    private float cage_platform_length=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_platform_length",30.0f);
    {setPropertyTooltip("cage_platform_length","length toward retina of cage's platform, in mm");}
    private float cage_door_width=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_door_width",10.0f);
    {setPropertyTooltip("cage_door_width","width of the cage's door, in mm");}
    // add getString/set for these above
    private float max_distance=getPrefs().getFloat("PawTrackerStereoBoard3Label.max_distance",100.0f);
    {setPropertyTooltip("max_distance","do not display events beyong this distance to camera, in mm");}

    private float cage_base_height=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_base_height",0.0f);
    {setPropertyTooltip("cage_base_height","ground eleveation, compared to camera, of the cage, in mm");}
    private float cage_door_offset=getPrefs().getFloat("PawTrackerStereoBoard3Label.cage_door_offset",0.0f);
    {setPropertyTooltip("cage_door_offset","offset of the cage's door toward left, in mm");}




     private int zoom3D=getPrefs().getInt("PawTrackerStereoBoard3Label.zoom3D",1);
    

 //   private float focal_length=getPrefs().getFloat("PawTrackerStereoBoard3Label.focal_length",6.0f);
//   {setPropertyTooltip("focal_length","focal length in mm");}
//    private float retinae_distance=getPrefs().getFloat("PawTrackerStereoBoard3Label.retinae_distance",100.0f);
//   {setPropertyTooltip("retinae_distance","distance between the two retinae in mm");}
    private float pixel_size=getPrefs().getFloat("PawTrackerStereoBoard3Label.pixel_size",0.04f);
   {setPropertyTooltip("pixel_size","pixel size of retina in mm");}
 //   private float retina_angle=getPrefs().getFloat("PawTrackerStereoBoard3Label.retina_angle",10.0f);
 //  {setPropertyTooltip("retina_angle","angle of rotation of retina in degrees");}
  
    
    
    private int max_finger_clusters=getPrefs().getInt("PawTrackerStereoBoard3Label.max_finger_clusters",10);
    private int grasp_max_elevation=getPrefs().getInt("PawTrackerStereoBoard3Label.grasp_max_elevation",15);
 
    
    
    private float alpha=getPrefs().getFloat("PawTrackerStereoBoard3Label.alpha",0.1f);
    private float intensity=getPrefs().getFloat("PawTrackerStereoBoard3Label.intensity",1);
    
    
 //   private int dispAvgRange=getPrefs().getInt("PawTrackerStereoBoard3Label.dispAvgRange",1);
    
    private int yLeftCorrection=getPrefs().getInt("PawTrackerStereoBoard3Label.yLeftCorrection",0);
    private int yRightCorrection=getPrefs().getInt("PawTrackerStereoBoard3Label.yRightCorrection",0);
    
//    private float yCurveFactor=getPrefs().getFloat("PawTrackerStereoBoard3Label.yCurveFactor",0.1f);
    
    private float valueThreshold=getPrefs().getFloat("PawTrackerStereoBoard3Label.valueThreshold",0);
    private float accValueThreshold=getPrefs().getFloat("PawTrackerStereoBoard3Label.accValueThreshold",0);
 
    
    private float shadowFactor=getPrefs().getFloat("PawTrackerStereoBoard3Label.shadowFactor",0.3f);
//    private float colorizeFactor=getPrefs().getFloat("PawTrackerStereoBoard3Label.colorizeFactor",0.1f);
 //   private int colorizePeriod=getPrefs().getInt("PawTrackerStereoBoard3Label.colorizePeriod",183);
    
    
//    private int zFactor=getPrefs().getInt("PawTrackerStereoBoard3Label.zFactor",1);
 //   private float valueMargin=getPrefs().getFloat("PawTrackerStereoBoard3Label.valueMargin",0.3f);
    
 //   private int disparity_range=getPrefs().getInt("PawTrackerStereoBoard3Label.disparity_range",50);
    
    private int cube_size=getPrefs().getInt("PawTrackerStereoBoard3Label.cube_size",1);
    
   
   
    private int retinaSize=getPrefs().getInt("PawTrackerStereoBoard3Label.retinaSize",128);
    
    private int intensityZoom = getPrefs().getInt("PawTrackerStereoBoard3Label.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    
    private float brightness=getPrefs().getFloat("PawTrackerStereoBoard3Label.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
    
    
  //  private float correctLeftAngle=getPrefs().getFloat("PawTrackerStereoBoard3Label.correctLeftAngle",0.0f);
  //  private float correctRightAngle=getPrefs().getFloat("PawTrackerStereoBoard3Label.correctRightAngle",0.0f);
    
    
 //   private boolean useFastMatching = getPrefs().getBoolean("PawTrackerStereoBoard3Label.useFastMatching",true);
 //   private boolean showYColor = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showYColor",false);
 //   private boolean showXColor = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showXColor",false);
 //   private boolean showZColor = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showZColor",false);
 //   private boolean showShadows = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showShadows",false);
 //   private boolean showCorner = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showCorner",false);
    
 //   private boolean highlightDecay = getPrefs().getBoolean("PawTrackerStereoBoard3Label.highlightDecay",false);
    
    
    
//    private boolean correctY = getPrefs().getBoolean("PawTrackerStereoBoard3Label.correctY",false);
 //   private boolean useFilter = getPrefs().getBoolean("PawTrackerStereoBoard3Label.useFilter",false);
    
 //   private boolean useLarge = getPrefs().getBoolean("PawTrackerStereoBoard3Label.useLarge",false);
    
    
    private boolean showFingers = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showFingers",true);
    private boolean showFingersRange = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showFingersRange",false);

 //   private boolean showFingerTips = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showFingerTips",true);
    
    private boolean showZones = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showZones",false);
//    private boolean showAll = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showAll",true);
    // show intensity inside shape
    
 //   private boolean showAcc = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showAcc",false);
   private boolean showOnlyAcc = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showOnlyAcc",false);
 //   private boolean showDecay = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showDecay",false);
    
    
    private boolean scaleAcc = getPrefs().getBoolean("PawTrackerStereoBoard3Label.scaleAcc",true);
    
    private boolean showCage = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showCage",true);
  //  private boolean showFrame = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showFrame",true);
    private boolean show2DWindow = getPrefs().getBoolean("PawTrackerStereoBoard3Label.show2DWindow",true);
    private boolean show3DWindow = getPrefs().getBoolean("PawTrackerStereoBoard3Label.show3DWindow",true);
  //  private boolean showPlayer = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showPlayer",true);
  //  private boolean showScore = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showScore",false);
    private boolean showRetina = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showRetina",false);
  
    private boolean trackFingers = getPrefs().getBoolean("PawTrackerStereoBoard3Label.trackFingers",false);
  
    
    //  private boolean showShapePoints = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showShapePoints",true);
    //   private boolean showFingerPoints = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showFingerPoints",true);
    
    
    
    //   private boolean showShape = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showShape",true);
    private boolean showRLColors = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showRLColors",false);
    private boolean showAxes = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showAxes",true);
    
    
    private int lowFilter_radius=getPrefs().getInt("PawTrackerStereoBoard3Label.lowFilter_radius",3);
    private int lowFilter_density=getPrefs().getInt("PawTrackerStereoBoard3Label.lowFilter_density",17);
    private float lowFilter_threshold=getPrefs().getFloat("PawTrackerStereoBoard3Label.lowFilter_threshold",0);
    
  //  private int lowFilter_radius2=getPrefs().getInt("PawTrackerStereoBoard3Label.lowFilter_radius2",10);
 //   private int lowFilter_density2=getPrefs().getInt("PawTrackerStereoBoard3Label.lowFilter_density2",5);
    
//    private boolean showCorrectionMatrix = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showCorrectionMatrix",false);
//    private boolean showCorrectionGradient = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showCorrectionGradient",false);
    
//    private boolean showRight = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showRight",false);
    
//    private boolean showPalm = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showPalm",false);
    
//    private boolean showSkeletton = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showSkeletton",false);
//    private boolean showSecondFilter = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showSecondFilter",false);
//    private boolean showTopography = getPrefs().getBoolean("PawTrackerStereoBoard3Label.showTopography",false);
    
    
//    private boolean restart=getPrefs().getBoolean("PawTrackerStereoBoard3Label.restart",false);
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTrackerStereoBoard3Label.resetPawTracking",false);
    private boolean resetClusters=getPrefs().getBoolean("PawTrackerStereoBoard3Label.resetClusters",false);
  
    
    
//    private boolean validateParameters=getPrefs().getBoolean("PawTrackerStereoBoard3Label.validateParameters",false);
    
    private float event_strength=getPrefs().getFloat("PawTrackerStereoBoard3Label.event_strength",2f);
    
    private float decayTimeLimit=getPrefs().getFloat("PawTrackerStereoBoard3Label.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
    private boolean decayOn = getPrefs().getBoolean("PawTrackerStereoBoard3Label.decayOn",false);
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}
    
    
 //   private boolean notCrossing = getPrefs().getBoolean("PawTrackerStereoBoard3Label.notCrossing",false);
    
    
    private float finger_mix=getPrefs().getFloat("PawTrackerStereoBoard3Label.finger_mix",0.5f);
    private int finger_surround=getPrefs().getInt("PawTrackerStereoBoard3Label.finger_surround",10);
    
    private boolean useGroups = getPrefs().getBoolean("PawTrackerStereoBoard3Label.useGroups",true);
    
  //  private boolean goThroughMode = getPrefs().getBoolean("PawTrackerStereoBoard3Label.goThroughMode",false);
//    private boolean useCorrections = getPrefs().getBoolean("PawTrackerStereoBoard3Label.useCorrections",true);
    private int tracker_lifeTime=getPrefs().getInt("PawTrackerStereoBoard3Label.tracker_lifeTime",10000);
    private int tracker_prelifeTime=getPrefs().getInt("PawTrackerStereoBoard3Label.tracker_prelifeTime",1000);
    private int tracker_viable_nb_events=getPrefs().getInt("PawTrackerStereoBoard3Label.tracker_viable_nb_events",100);
   
       
    private float expansion_mix=getPrefs().getFloat("PawTrackerStereoBoard3Label.expansion_mix",0.5f);
    private float trackerSubsamplingDistance=getPrefs().getFloat("PawTrackerStereoBoard3Label.trackerSubsamplingDistance",10);//cm?
    private boolean recordTrackerData = getPrefs().getBoolean("PawTrackerStereoBoard3Label.recordTrackerData",false);
    private boolean logDataEnabled = getPrefs().getBoolean("PawTrackerStereoBoard3Label.logDataEnabled",false);
 
    
 //   private float plane_tracker_mix=getPrefs().getFloat("PawTrackerStereoBoard3Label.plane_tracker_mix",0.5f);
 //   private boolean trackZPlane = getPrefs().getBoolean("PawTrackerStereoBoard3Label.trackZPlane",false);
 
    private boolean detectGrasp = getPrefs().getBoolean("PawTrackerStereoBoard3Label.detectGrasp",false);
    private boolean checkNose = getPrefs().getBoolean("PawTrackerStereoBoard3Label.checkNose",false);
 
    private int start_min_events=getPrefs().getInt("PawTrackerStereoBoard3Label.start_min_events",10000);
    private int start_z_displacement=getPrefs().getInt("PawTrackerStereoBoard3Label.start_z_displacement",100);
    private int grasp_timelength_min=getPrefs().getInt("PawTrackerStereoBoard3Label.grasp_timelength_min",200000);
 
    // epipolar
  //  private boolean correctEpipolar = getPrefs().getBoolean("PawTrackerStereoBoard3Label.correctEpipolar",true);
    private int recordUpTo=0; //getPrefs().getInt("PawTrackerStereoBoard3Label.recordUpTo",0);
 
    private boolean autoRecord = getPrefs().getBoolean("PawTrackerStereoBoard3Label.autoRecord",false);


    private float event3DLifeTime=getPrefs().getFloat("PawTrackerStereoBoard3Label.event3DLifeTime",100);
    {setPropertyTooltip("event3DLifeTime","life time of event for 3D display, in ms");}


    private int max3DEvents=getPrefs().getInt("PawTrackerStereoBoard3Label.max3DEvents",10000);

//    private boolean throwAwayEvents = getPrefs().getBoolean("PawTrackerStereoBoard3Label.throwAwayEvents",false);
 
     // maybe initialize later

    int currentIndex = 0;
    int currentStart = 0;
    Event3D[] current3DEvents; // = new Event3D[max3DEvents];


    
    /** additional classes */
     public class Cage{
         Point p1;
         Point p2;
         Point p3;
         Point p4;
         Point p5;
         Point p6;
         Point p7;
         Point p8;
         Point p9;
         Point p10;
         Point p11;
         Point p12;
        
         public void tilt( float angle){
             
             // rotate all points
             p1 = p1.rotateOnX(  0,  0,  angle);
             p2 = p2.rotateOnX(  0,  0,  angle);
             p3 = p3.rotateOnX(  0,  0,  angle);
             p4 = p4.rotateOnX(  0,  0,  angle);
             p5 = p5.rotateOnX(  0,  0,  angle);
             p6 = p6.rotateOnX(  0,  0,  angle);
             p7 = p7.rotateOnX(  0,  0,  angle);
             p8 = p8.rotateOnX(  0,  0,  angle);
             p9 = p9.rotateOnX(  0,  0,  angle);
             p10 = p10.rotateOnX(  0,  0,  angle);
             p11 = p11.rotateOnX(  0,  0,  angle);
             p12 = p12.rotateOnX(  0,  0,  angle);
                                    
         }
         
     }
    
       /** additional classes */
     public class Zone{
         Point p1;
         Point p2;
         Point p3;
         Point p4;
         Point p5;
         Point p6;
         Point p7;
         Point p8;
         Point rp1;
         Point rp2;
         Point rp3;
         Point rp4;
         Point rp5;
         Point rp6;
         Point rp7;
         Point rp8;
        
        
         public void tilt( float angle){
             
             // rotate all points
             rp1 = p1.rotateOnX(  0,  0,  angle);
             rp2 = p2.rotateOnX(  0,  0,  angle);
             rp3 = p3.rotateOnX(  0,  0,  angle);
             rp4 = p4.rotateOnX(  0,  0,  angle);
             rp5 = p5.rotateOnX(  0,  0,  angle);
             rp6 = p6.rotateOnX(  0,  0,  angle);
             rp7 = p7.rotateOnX(  0,  0,  angle);
             rp8 = p8.rotateOnX(  0,  0,  angle);
            
                                    
         }
         
     }
    
    
     /** Point : all data about a point in opengl 3D space */
    public class Point{
        float x;
        float y;
        float z;

        public Point(){
            x = 0;
            y = 0;
            z = 0;
        }
        
        public Point(float x, float y, float z){
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public Point minus( Point p){
            Point r = new Point();
            r.x = x - p.x; 
            r.y = y - p.y; 
            r.z = z - p.z; 
            return r;
        }
        public Point plus( Point p){
            Point r = new Point();
            r.x = x + p.x; 
            r.y = y + p.y; 
            r.z = z + p.z; 
            return r;
        }

          public void rotate( float[] rotationMatrix){

               x = x*rotationMatrix[0]+y*rotationMatrix[1]+z*rotationMatrix[2];
               y = x*rotationMatrix[3]+y*rotationMatrix[4]+z*rotationMatrix[5];
               z = x*rotationMatrix[6]+y*rotationMatrix[7]+z*rotationMatrix[8];
//               x = x*-1;
//               y = y*-1;
//               z = z*-1;


        }
        
       public Point rotateOnX( float yRotationCenter, float zRotationCenter, float angle){
              float yr = rotateYonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              float zr = rotateZonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              return new Point(x,yr,zr);
        }

        public Point rotateOnY( float xRotationCenter, float zRotationCenter, float angle){
              float xr = rotateXonY( x, z,  xRotationCenter,  zRotationCenter,  angle);
              float zr = rotateZonY( x, z,  xRotationCenter,  zRotationCenter,  angle);
              return new Point(xr,y,zr);
        }
            
        
    }
    
    public class GroupLabel {
        
        int value;
        int time;
        int y;
        int xmin;
        int xmax;
        int count;
        int disparity = -1;

        public GroupLabel( int value){
            this.value = value;
        }

        public void add( EventPoint ep){
           // if(ep.x<xmin)xmin = ep.x;
           // if(ep.x>xmax)xmax = ep.x;
            y = ep.y;
            time = ep.updateTime;
            count++;
        }
        public void remove( EventPoint ep){
           // if(ep.x-xmin<xmax-ep.x){
          //      xmin = ep.x+1;
          //  } else {
           //     xmax = ep.x-1;
           // }
            count--;

            // remove from hashtable
            if(count<=0){
                synchronized (groupLabels){
                   groupLabels.remove(new Integer(value));
                }
                disparity = -1;
            }
            // change time??
        }

    }
    
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
        
        GroupLabel groupLabel = new GroupLabel(0);
        GroupLabel groupLabel2 = new GroupLabel(0);;
        
        
        // right
        // int accumulation;
        //float free;
        int attachedTo = NO_LINK;
        int attachedTo2 = NO_LINK;
        
        int updateTime; // time of current update
        int previousUpdate; // time of previous update
        
     
        float previousShortFilteredValue = 0;
      //  float previousDecayedFilteredValue = 0;
        
        //float decayedFilteredValue;         // third low pass filter with decaying values
        float previousValue=0;             // last contract value
        float lastValue=0;                // last contract value
        float accValue=0;                // accumulated contrast value
  //      boolean linkPawBack = false;    // when finger is attached to paw back at this point
   //     boolean onFingerLine = false;  // if is part of a finger line
  //      boolean isSkeletton = false;  // true if part of the skeletton of paw obtained by thinnning
  //      boolean isEndNode = false;   // true if end of segment of skeletton points
        float shortFilteredValue;   // short range topographic filter
     //   float largeFilteredValue;  // large range topographic filter
  //      boolean border;           // true if point is on the paw's border
        //    Integer group;           // contour group to which this point belongs, group 1 is touching door
  //      float intensity;        // value of intensity of point, projected from border point toward inside, to detect
        // convex borders and possible finger points
 //       float fingerScore;    // likelyhood score of this point being a fingertip, obtained by template matching
 //       int zerosAround;     // number of low value neighbours for border matching
  //      int zerosDAround;   // number of low value neighbours for border matching for decayed filter
        
  //      float gcx;        // float coordinates for gc of events around when point is border
   //     float gcy;
   //     float gcz;
        
   //     float prev_gcx;        // previous float coordinates for gc of zeroes around when point is border
   //     float prev_gcy;
    //    float prev_gcz;
        
 //       float ggcx;        // float coordinates for gc of gc around when point is border
 //       float ggcy;
  //      float ggcz;
        
  ///      float prev_ggcx;        // previous float coordinates for gc of zeroes around when point is border
  //      float prev_ggcy;
  //      float prev_ggcz;
        
  //      boolean decayedBorder=false; // is a border computed on decayed filtered value
        
  //      int skValue;     // Skeletton value (?)
        //Finger finger; // finger
        int x;
        int y;
       // int z;
        
        // real world coordinates in 3D
        int xr;
        int yr;
        int zr;
        
        // matched point coordinates for left method
        int x0;
        int y0;
        int z0;
        // for right method
        int x0r;
        int y0r;
        int z0r;
        
        int side = LEFT;
        int zDirection = 1;
        
        int sign; // -1, 0 not initialized, 1
        
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
            // paul opposite test
            // int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            
      //      float step = event_strength / (colorScale + 1);
            lastValue = step * (type - grayValue);
            
            
            // important test: to use also negative events
            // test 1: invert value: fails
            // test 2 : gives sign to event
           // if (lastValue<0) sign = -1;
            if (lastValue<valueThreshold) sign = -1;
            else sign = 1;
            
            accValue +=  lastValue*sign;
            
             
             if(scaleAcc){
            // keep in range [0-1]
            if(accValue<0)accValue=0;
            else if(accValue>1)accValue=1;
             }
            
            updateTime = e.timestamp;
            x = e.x;
            y = e.y;
        }
        
        public EventPoint( int x, int y ){
            changed = true;
            this.x = x;
            this.y = y;
        }
        
        public void setChanged(boolean changed){
            this.changed = changed;
        }
        
        public void updateFrom( BinocularEvent e, int x, int y, int side ){
            changed = true;
   //         boolean updated = true;
            previousValue = lastValue;
            //  int type=e.getType();
            sign=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            // paul test opposite polariry
            //  int type=e.polarity==BinocularEvent.Polarity.Off? 1: 0;
            
            
            // optimize: could be moved somewhere else :
         //   float step = event_strength / (colorScale + 1);
            lastValue = step * (sign - grayValue);
            
            //    System.out.println("type "+type);
                        
            //  accValue +=  lastValue;
          //  if (decayOn) accValue =  lastValue + getAccValue(e.timestamp);
          //  else 
            
            
              // important test: to use also negative events
            // test 1: invert value
             // test 2 : gives sign to event
        //    if(sign==0){
                
                //update sign
             //   if (lastValue<0) sign = -1;
       //         if (lastValue<valueThreshold) sign = -1;
       //         else sign = 1;
       //     }
            
            
            float change = lastValue*sign;
            
//            if(accValue<valueThreshold){
//                if(change<0){
//                    updated = false;
//                }
//            } else if(accValue>1){
//                if(change>0){
//                    updated = false;
//                }
//            }
//            
//            
//            if(updated){
            accValue +=  change;
            
            // when to reset sign?
                    
            if(accValue<0) sign=0;
            
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
            
//            }
//            
//            return updated;
            
        }
        
        
        public void updateLabel(  ){
            
            EventPoint[][] eventPoints = null;
            if(side==LEFT){
                eventPoints = leftPoints;
            } else {
                eventPoints = rightPoints;
            }
            
            if(getValue(currentTime)>valueThreshold){
                
                
                if(x-1>=0){
                    updateLabelFrom(eventPoints[x-1][y]);
                } else {
                    newLabel(this);
                }
                if(x+1<retinaSize){
                    eventPoints[x+1][y].updateLabelFrom(this);
                }
                // RIGHT_MOST_METHOD
                if(x+1<retinaSize){
                    updateLabel2From(eventPoints[x+1][y]);
                } else {
                    newLabel2(this);
                }
                if(x-1>=0){
                    eventPoints[x-1][y].updateLabel2From(this);
                }
                
            } else {
                noLabel(this);
            }
            
        }

        
        
        public void updateLabelFrom( EventPoint ep ){
         
            if(getValue(currentTime)>valueThreshold){
                if(ep.getValue(currentTime)>valueThreshold){
                    if(groupLabel.value == 0){
                        if( ep.groupLabel.value==0) {
                            newLabel(this);
                        } else {     
                            groupLabel.remove(this);
                            groupLabel = ep.groupLabel;
                            groupLabel.add(this);
                        }
                    } else {
                        if(ep.groupLabel.value != 0){                                                        
                            if(groupLabel.value>ep.groupLabel.value){
                                groupLabel.remove(this);
                                groupLabel = ep.groupLabel;
                                groupLabel.add(this);
                            } else {
                                ep.groupLabel.remove(ep);
                                ep.groupLabel = groupLabel;
                                groupLabel.add(ep);
                            }
                        }
                    }
                } else {
                    if(groupLabel.value == 0){
                        newLabel(this);
                    }
                }
            } else {
//                if(groupLabel.intValue() == 0){
//                    newLabel();
//                }
                noLabel(this);
            }
           
        }
        
        
            public void updateLabel2From( EventPoint ep ){
         
            if(getValue(currentTime)>valueThreshold){
                if(ep.getValue(currentTime)>valueThreshold){
                    if(groupLabel2.value == 0){
                        if( ep.groupLabel2.value==0) {
                            newLabel2(this);
                        } else {     
                            groupLabel2.remove(this);
                            groupLabel2 = ep.groupLabel;
                            groupLabel2.add(this);
                        }
                    } else {
                        if(ep.groupLabel2.value != 0){                                                        
                            if(groupLabel2.value>ep.groupLabel.value){
                                groupLabel2.remove(this);
                                groupLabel2 = ep.groupLabel;
                                groupLabel2.add(this);
                            } else {
                                ep.groupLabel2.remove(ep);
                                ep.groupLabel2 = groupLabel2;
                                groupLabel2.add(ep);
                            }
                        }
                    }
                } else {
                    if(groupLabel2.value == 0){
                        newLabel2(this);
                    }
                }
            } else {
//                if(groupLabel.intValue() == 0){
//                    newLabel();
//                }
                noLabel(this);
            }
           
        }
        
        /**
           public void updateLabel2From( EventPoint ep ){
            
         
            if(getValue(currentTime)>valueThreshold){
                if(ep.getValue(currentTime)>valueThreshold){
                    if(groupLabel2.intValue() == 0){
                        if( ep.groupLabel2.intValue()==0) {
                            newLabel2();
                        } else {                           
                            groupLabel2 = ep.groupLabel2;
                        }
                    } else {
                        if(ep.groupLabel2.intValue() != 0){                                                        
                            if(groupLabel2.intValue()>ep.groupLabel2.intValue()){
                                groupLabel2 = ep.groupLabel2;
                            } else {
                                ep.groupLabel2 = groupLabel2;
                            }
                        }                      
                    }
                } else {
                    if(groupLabel2.intValue() == 0){
                        newLabel2();
                    }
                }
            } else {
//                if(groupLabel2.intValue() == 0){
//                    newLabel2();
//                }
                noLabel();
            }
        }
           
           */
                   
        /*
        public void updateLabel2From( EventPoint ep ){
            if(getValue(currentTime)>valueThreshold){
                if(ep.getValue(currentTime)>valueThreshold){
                    if( ep.groupLabel2==0) {
                        newLabel2();
                    } else {
                        if(groupLabel2 == 0 || groupLabel2>ep.groupLabel2){
                            groupLabel2 = ep.groupLabel2;
                        } else {
                            ep.groupLabel2 = groupLabel2;
                        }
                    }
                } else {
                    newLabel2();
                }
            }
        }
        */
        public void newLabel( EventPoint ep){ 
            labelNumber++;
            groupLabel = new GroupLabel(labelNumber);
            groupLabel.add(ep);
            synchronized (groupLabels){
               groupLabels.put(new Integer(labelNumber), groupLabel);
            }
        }
        public void newLabel2(EventPoint ep){
            labelNumber++;

            groupLabel2 = new GroupLabel(labelNumber);
            groupLabel2.add(ep);
            synchronized (groupLabels){
             groupLabels.put(new Integer(labelNumber), groupLabel2);
            }
        }
        
        public void noLabel(EventPoint ep){
            groupLabel.remove(ep);
            groupLabel2.remove(ep);
            groupLabel = new GroupLabel(0);
            groupLabel2 = new GroupLabel(0);
        }
        
        // simpler version ofr otpimization ,no decay
//          public float getValue( int currentTime ){
//              if(useFilter){
//                 return shortFilteredValue;
//              } else {
//                  return accValue;
//              }
//              
//            //  return accValue;
//        }
        // commented out for optimization
        public float getValue( int currentTime ){
            
            return shortFilteredValue; //-decayedValue(shortFilteredValue,currentTime-updateTime);
            
            /* removed for temporary optimization
            if(useFilter){
                
                
                if (decayOn) return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
                else return shortFilteredValue;
                
            } else {
                if (decayOn) return accValue-decayedValue(accValue,currentTime-updateTime);
                else return accValue;
            }
             **/
        }
        
        
        public float getAccValue( int currentTime ){
            if (decayOn) return accValue-decayedValue(accValue,currentTime-updateTime);
            else return accValue;
        }
        
        //     public float getFreeValue( int currentTime ){
        //           if (decayOn) return free-decayedValue(free,currentTime-updateTime);
        //           else return free;
        //      }
        
        public float getShortFilteredValue( int currentTime ){
            if (decayOn) return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
            else return shortFilteredValue;
        }
        
   //     public float getDecayedFilteredValue( int currentTime ){
  //          return decayedFilteredValue-decayedValue(decayedFilteredValue,currentTime-updateTime);
  //      }
        
        public float getPreviousShortFilteredValue( int currentTime ){
            return previousShortFilteredValue-decayedValue(previousShortFilteredValue,currentTime-previousUpdate);
        }
        
//        public int getX0( int method ){
//            if(changed)  {
//                computeXYZ0( LEFT_MOST_METHOD );
//                computeXYZ0( RIGHT_MOST_METHOD );
//            }
//            if (method==LEFT_MOST_METHOD)
//                return x0;
//            else return x0r;
//
//        }
//
//        public int getY0( int method ){
//            if(changed)  {
//                computeXYZ0( LEFT_MOST_METHOD );
//                computeXYZ0( RIGHT_MOST_METHOD );
//            }
//            if (method==LEFT_MOST_METHOD)
//                return y0;
//            else return y0r;
//        }
//
//        public int getZ0( int method ){
//            if(changed)  {
//                computeXYZ0( LEFT_MOST_METHOD );
//                computeXYZ0( RIGHT_MOST_METHOD );
//            }
//            if (method==LEFT_MOST_METHOD){
//
//                return z0;
//            } else {
//
//                return z0r;
//            }
//        }
//
//          public void computeXYZ0( int method ){
//            changed = false;
//
//            int dx = disparityLink;
//            if(method==RIGHT_MOST_METHOD) dx = disparityLink2;
//
//
//
//            if(dx>NO_LINK){ // if matched point exists
//                // dx is coordinate of matched pixel in other retina
//
//                /** http://local.wasp.uwa.edu.au/~pbourke/geometry/lineline3d/
//                 * Calculate the line segment PaPb that is the shortest route between
//                 * two lines P1P2 and P3P4. Calculate also the values of mua and mub where
//                 * Pa = P1 + mua (P2 - P1)
//                 * Pb = P3 + mub (P4 - P3)
//                 * Return FALSE if no solution exists.
//                 */
//
//                // result
//                long xt = 0;
//                long yt = 0;
//                long zt = 0;
//
//                // P1
//                Point p1;
//                Point p3;
//
//                if(side==LEFT){
//                    p1 = new Point(leftPoints[x][y].xr,leftPoints[x][y].yr,leftPoints[x][y].zr);
//                    p3 = new Point(rightPoints[dx][y].xr,rightPoints[dx][y].yr,rightPoints[dx][y].zr);
//                } else {
//                    p1 = new Point(leftPoints[dx][y].xr,leftPoints[dx][y].yr,leftPoints[dx][y].zr);
//                    p3 = new Point(rightPoints[x][y].xr,rightPoints[x][y].yr,rightPoints[x][y].zr);
//                }
//
//                // p3 is left focal point
//                Point p2 = new Point(left_focal_x,left_focal_y,left_focal_z);
//                // p4 is right focal point
//                Point p4 = new Point(right_focal_x,right_focal_y,right_focal_z);
//
//                Point pr = triangulate(p1,p2,p3,p4);
//
//                // store results for both methods
//
//                if(method==RIGHT_MOST_METHOD){
//                    x0r = pr.x;
//                    y0r = pr.y;
//                    z0r = pr.z;
//                } else {
//                    x0 = pr.x;
//                    y0 = pr.y;
//                    z0 = pr.z;
//                }
//
//
////                if(side==LEFT){
////                    if(method==RIGHT_MOST_METHOD){
////                        if(z0r<minleftZ){
////                            minleftZ = z0r;
////                            System.out.println("minleftZ: "+minleftZ);
////                        }
////                    } else {
////                        if(z0<minleftZ){
////                            minleftZ = z0;
////
////                            System.out.println("minleftZ: "+minleftZ);
////                        }
////                    }
////                } else {
////                    if(method==RIGHT_MOST_METHOD){
////                        if(z0r<minrightZ){
////                            minrightZ = z0r;
////                            System.out.println("minrightZ: "+minrightZ);
////                        }
////                    } else {
////                        if(z0<minrightZ){
////                            minrightZ = z0;
////
////                            System.out.println("minrightZ: "+minrightZ);
////                        }
////                    }
////
////                }
//            }
//
//
//        }
    } // end class EventPoint
    
    
   
    
    ///###################### Trackers #############################################
    
    private class FingerCluster{
        int id = 0;
        int x=0;
        int y=0;
        int z = 0;
        int time = 0;
       
        int nbEvents = 0;
        
        //for ball tracker, hacked here
        int x_size = 0;
        int y_size = 0;
        int z_size = 0;
      //  boolean activated = false;
        
        boolean startingGrasp = false;
        boolean endingGrasp = false;
        public boolean isAGrasp = false;
        int z_orig = 0;
        int time_start = 0;
        
        public FingerCluster(  ){
            //for ball tracker, hacked here
            x_size = 10; //finger_surround;
            y_size = 10;
            z_size = 10;
            
        }
        
        public FingerCluster( int x, int y, int z, int time ){
          //  activated = true;
            this.time = time;
            
            id = idFingerClusters++;
            this.x = x;
            this.y = y;
            this.z = z;
            
            //for ball tracker, hacked here
            x_size = 10;
            y_size = 10;
            z_size = 10;
            nbEvents = 1;
        }
        
        public void reset(){
            
         //   activated = false;
            //for ball tracker, hacked here
            x_size = 10;
            y_size = 10;
            z_size = 10;
            
            x = 0; //end tip
            y = 0;
            z = 0;
            nbEvents = 0;
        }
        
        public void add( int x, int y, int z, float mix, int time){
           // mix = mix/(finger_surround*finger_surround); // /100
            
            mix = mix/100;
//            int timeDiff = this.time - time;
//            double logDiff = Math.log10(timeDiff);
//            mix = mix/(float)logDiff;
//            
//            System.out.println("mix "+mix+" logDiff"+logDiff);
//            
           // float exp_mix = expansion_mix;// /100;
            if(mix>1) mix=1;
            if(mix<0) mix = 0;
          //  if(exp_mix>1) exp_mix=1;
          //  if(exp_mix<0) exp_mix = 0;
            this.time = time;
            this.x = Math.round(x*mix + this.x*(1-mix));
            this.y = Math.round(y*mix + this.y*(1-mix));
            this.z = Math.round(z*mix + this.z*(1-mix));
            
       //     int max_size = finger_surround * scaleFactor;
            //for ball tracker, hacked here
            // take remaining difference after cluster has moved             
//            int diffx = Math.abs(this.x - x);// half size needed to incorporate new event
//          
//            float x_dist_weight = Math.round((float)Math.abs(x_size - diffx)/x_size);
//         //   x_dist_weight = x_dist_weight * x_dist_weight;
//            int diffy = Math.abs(this.y - y);// half size needed to incorporate new event
//            float y_dist_weight = Math.round((float)Math.abs(y_size - diffy)/y_size);
//         //   y_dist_weight = y_dist_weight * y_dist_weight;
//            int diffz = Math.abs(this.z - z);// half size needed to incorporate new event
//            float z_dist_weight = Math.round((float)Math.abs(z_size - diffz)/z_size);
//        //    z_dist_weight = z_dist_weight * z_dist_weight;
//            // need to weight size change by inverse of neasrest to cluster center?
//            if(x_dist_weight>1) x_dist_weight=0;
//         //   if(x_dist_weight<0) x_dist_weight = 0;
//            if(y_dist_weight>1) y_dist_weight=0;
//        //    if(y_dist_weight<0) y_dist_weight = 0;
//            if(z_dist_weight>1) z_dist_weight=0;
//        //    if(z_dist_weight<0) z_dist_weight = 0;
//            float exp_mix_x = exp_mix*(1-x_dist_weight);
//            float exp_mix_y = exp_mix*(1-y_dist_weight);
//            float exp_mix_z = exp_mix*(1-z_dist_weight);
//            x_size = Math.round(diffx*exp_mix_x + x_size*(1-exp_mix_x));
//            y_size = Math.round(diffy*exp_mix_y + y_size*(1-exp_mix_y));
//            z_size = Math.round(diffz*exp_mix_z + z_size*(1-exp_mix_z));
//            if(x_size>500||x_size<0){
//                int i = 0;
//            }
//             if(y_size>500||y_size<0){
//                int j = 0;
//            }
//             if(z_size>500||z_size<0){
//                int k = 0;
//            }
            
            
            nbEvents++;
         //   System.out.println("-- nb events: "+nbEvents);
            // end
            
            if(detectGrasp){
                if(endingGrasp){
                    // if not moving anymore..
                    //  if(time>time_start+grasp_timelength_min){
                    isAGrasp = false;
                    endingGrasp = false;
                    System.out.println("-- endingGrasp at "+time);
                    // close logging file
                    recordTrackerData = false;
                    
                    // }
                }
                
                // start
                if(startingGrasp){
                    if(nbEvents>start_min_events){
                        //    System.out.println("z:"+z+" z_orig:"+z_orig+" start_z_displacement:"+start_z_displacement);
                        if(z<z_orig-start_z_displacement){
                            isAGrasp = true;
                            time_start = time;
                            startingGrasp = false;
                            // create logging file
                            recordTrackerData = true;
                            
                            System.out.println("-- startingGrasp at "+time);
                        }
                        
                    }
                }
            }//end if detect grasp
            
        }
        
        // tell cluster to look if its movement is the start of a grasp
        void startLookingForGrasp(){
            startingGrasp = true;
            z_orig = z;
            
        }
        void endLookingForGrasp( int time){
            if(isAGrasp){
                if(time>time_start+grasp_timelength_min){                    
                    endingGrasp = true;                    
                    startingGrasp = false;
                }
            }
        }
        
    }
    // end class FingerCluster
    
    
 
    
    
    // do not forget to add a set and a getString/is method for each new parameter, at the end of this .java file
    
    
    // Global variables
    
    // array of event points for all computation
    protected EventPoint leftPoints[][];
    protected EventPoint rightPoints[][];
    
 //   protected EventPoint leftPoints2[][];
  //  protected EventPoint rightPoints2[][];
    
    protected int correctionMatrix[][];
    
 //   private boolean logDataEnabled=false;//previously used for ball tracker
    private PrintStream logStream=null;
    
    int currentTime = 0;
    
    float[] densities = new float[lowFilter_density];
//    float[] densities2 = new float[lowFilter_density2];
    
   // float largeRangeTotal;
    float shortRangeTotal;
    
    float shortFRadius;
   // float largeFRadius;
    int shortRadiusSq;
   // int largeRadiusSq;
    
    float invDensity1;
    float invDensity2;
    
    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    private int redBlueShown=0;
    private int method2D=0;
    private int display3DChoice=0;
    private int displaySign=0;
    
    private int testChoice=0;
    
//    private boolean averageMode = false;
//    private boolean fuseMode = false;
//    private boolean cutMode = false;
//    private boolean probaMode = false;
 //   private boolean veryCompactMode = false;
 //   private boolean compactMode = false;
    private boolean searchSpaceMode = false;
    private boolean clearSpaceMode = false;
    private boolean showDisparity = false;
    private boolean testDrawing = false;
    private boolean showLabels = false;
    private boolean showBehindCage = true;
    private boolean showMaxMinLabel = false;
    private boolean showAll3D = false;
    private boolean drawGroupCentered = false;
    private boolean drawGroupFlat = false;

    
    boolean windowDeleted = true;
    
  
    
    
    private int nbFingers = 0; //number of fingers tracked, maybe putString it somewhere else
    private int nbFingerClusters = 1;//number of created trackers
    private int idFingerClusters = 1;//id of created trackers
    
//    protected FingerCluster[] fingers = new FingerCluster[MAX_NB_FINGERS];
    protected Vector<FingerCluster> fingers = new Vector();
    
   
    
    // a bit hacked, for subsampling record of tracker data
    int prev_tracker_x = 0;
    int prev_tracker_y = 0;
    int prev_tracker_z = 0;
    
    boolean firstRun = true;
    
    /** Creates a new instance of PawTracker */
    public PawTrackerStereoBoard3Label(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        
        
        //     System.out.println("build resetPawTracker4 "+trackerID);
        
        initFilter();
        
        //   System.out.println("---------->>>>>> filter trackedID: "+trackerID);
       // resetPawTracker();
        
        //  validateParameterChanges();
        chip.addObserver(this);
        
        //   System.out.println("End build resetPawTrackerStereoBoard");
        
    }
    
    public void initFilter() {
        
    }
    
    
    
    private void resetPawTracker(){
        
        
        
        //   doReset = false;
        
        
        
        //   allEvents.clear();
        
     //   System.out.println("reset PawTrackerStereoBoard3Label reset");
       
        // pb with reset and display : null pointer if happens at the same time
        
        leftPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<leftPoints.length; i++){
            for (int j=0; j<leftPoints[i].length; j++){
                leftPoints[i][j] = new EventPoint(i,j);
                leftPoints[i][j].side = LEFT;
            }
        }
        rightPoints = new EventPoint[retinaSize][retinaSize];
        for (int i=0; i<rightPoints.length; i++){
            for (int j=0; j<rightPoints[i].length; j++){
                rightPoints[i][j] = new EventPoint(i,j);
                rightPoints[i][j].side = RIGHT;
            }
        }
        
//        leftPoints2 = new EventPoint[retinaSize][retinaSize];
//        for (int i=0; i<leftPoints2.length; i++){
//            for (int j=0; j<leftPoints2[i].length; j++){
//                leftPoints2[i][j] = new EventPoint(i,j);
//            }
//        }
//        rightPoints2 = new EventPoint[retinaSize][retinaSize];
//        for (int i=0; i<rightPoints2.length; i++){
//            for (int j=0; j<rightPoints2[i].length; j++){
//                rightPoints2[i][j] = new EventPoint(i,j);
//            }
//        }

        groupLabels = new Hashtable();
        
        compute3DParameters();
        
        // to remove:
        validateParameterChanges();
        
        //   resetCorrectionMatrix();
        // to remove:
       // createCorrectionMatrix();
        
        // reset group labels (have a vector of them or.. ?
        
        // scoresFrame = new float[retinaSize][retinaSize];
       
        resetClusterTrackers();
        
        setResetPawTracking(false);//this should also update button in panel but doesn't'
       
        // System.out.println("End of resetPawTrackerStereoBoard");
    }
    
    
     private void resetClusterTrackers(){
           fingers.clear(); // = new FingerCluster[MAX_NB_FINGERS];
        nbFingers = 0;
        //   setResetPawTracking(false);//this should also update button in panel but doesn't'
        
        int z = (int)Math.round((cage_distance-0.1)*scaleFactor);
        
        
         
     }
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
    
    
    
    
    
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){
        
       if(firstRun){
            // reset
            resetPawTracker();
            firstRun=false;
           // return; //maybe continue then
       }
        
        int n=ae.getSize();
        if(n==0) return;
        
   //     if(validateParameters){
    //        validateParameterChanges();
            
    //    }
        
        step = event_strength / (colorScale + 1);
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
        currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
            // BinocularEvent be=(BinocularEvent)e;
            
            processEvent(e);
            
            
        }

        generate3DEventsFromGroups(LEFT_MOST_METHOD);
   //     generate3DEventsFromGroups(RIGHT_MOST_METHOD);
        
        clearDeadFingerTrackers(currentTime);

        // clear groupLAbels hashtable from old elements
       //create fucntion clearGroupLabels(currentTime)
        synchronized (groupLabels){
            clearGroupLabels(currentTime);
        }

        printClusterData();




        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }
        
        //System.out.println("labelNumber: "+labelNumber);
    }


    private void generate3DEventsFromGroups( int method){
            if (method == LEFT_MOST_METHOD) {
            // for all epipolar lines
            for (int y = 0; y < retinaSize; y++) {
                // getString next group


                int xg = nextGroupIndexLeftMostMethod(leftPoints, 0, y, 0);
                GroupLabel nextGroup = null;

                if (xg != -1) {
                    nextGroup = leftPoints[xg][y].groupLabel;
                }

                while (nextGroup != null) {

                    // find matched group
                    if (nextGroup.disparity != -1) {


                        GroupLabel matchedGroup = (GroupLabel) groupLabels.get(new Integer(nextGroup.disparity));

                        // triangulate and display 3d group (segment)
                        if (currentTime - nextGroup.time < decayTimeLimit) {
                           // draw3DGroup(gl, nextGroup, matchedGroup);
                            new3DEvent(nextGroup, matchedGroup);

                        }

                    } // end if nextGroup has valid disparity

                    // next
                    xg = nextGroupIndexLeftMostMethod(leftPoints, xg, y, nextGroup.value);
                    if (xg != -1) {
                        nextGroup = leftPoints[xg][y].groupLabel;
                    } else {
                        // no more groups on this epipolar line
                        nextGroup = null;
                    }
                }
            } // end for all epipolar lines

        } else { //right most method
            // for all epipolar lines
            for (int y = 0; y < retinaSize; y++) {
                // getString next group


                int xg = nextGroupIndexRightMostMethod(leftPoints, retinaSize - 1, y, 0);
                GroupLabel nextGroup = null;

                if (xg != -1) {
                    nextGroup = leftPoints[xg][y].groupLabel2;
                }

                while (nextGroup != null) {

                    // find matched group
                    if (nextGroup.disparity != -1) {


                        GroupLabel matchedGroup = (GroupLabel) groupLabels.get(new Integer(nextGroup.disparity));

                        // triangulate and display 3d group (segment)
                        if (currentTime - nextGroup.time > decayTimeLimit) {
                            //draw3DGroup(gl, nextGroup, matchedGroup);
                            new3DEvent(nextGroup, matchedGroup);
                        }

                    } // end if nextGroup has valid disparity

                    // next
                    xg = nextGroupIndexRightMostMethod(leftPoints, xg, y, nextGroup.value);
                    if (xg != -1) {
                        nextGroup = leftPoints[xg][y].groupLabel2;
                    } else {
                        // no more groups on this epipolar line
                        nextGroup = null;
                    }
                }
            } // end for all epipolar lines
        } // end if method



    }

    public void printClusterData(){
         if(recordTrackerData){
                 
             
             if(recordUpTo!=0&&currentTime>recordUpTo*10000){
                 // stop logging, pause, ask for next recordupto?
                 recordTrackerData = !recordTrackerData;
                 
             } else {
             
                 // extract x,y,z
             int n = 0;
             for(FingerCluster fc:fingers){
                 n++;
                 if(fc.nbEvents>tracker_viable_nb_events){ // >=
                     if((detectGrasp&&fc.isAGrasp)||!detectGrasp){
                         // if(!fingers.isEmpty()) {
                         //     FingerCluster fc = fingers.firstElement();
                         
                         String slog = new String(currentTime+" "+fc.x+" "+fc.y+" "+fc.z+" "+fc.id+"\n");
                         logData(slog);
                     }
                 }
             }
             }
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
        firstRun=true;
        resetPawTracker();
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
    //    checkPlayerRecorderFrame();
        
        track(in);
        
        // if recording
       
        if(logDataEnabled){
            doLog();
        }
        
       
        
        if(pngRecordEnabled){
            
            writeMovieFrame();
        }
        
        
        
  //      if (showPlayer) playerRecorderFrame.repaint();
        if (show2DWindow&&insideIntensityCanvas!=null) insideIntensityCanvas.repaint();
        if (show3DWindow&&!windowDeleted) a3DCanvas.repaint();
        return in;
    }
    
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    
    // computing 3D parameters for resolution of 3D location of matched points
    private synchronized void compute3DParameters(){
        // from pixel size, focal length, distance between retinas and angle of rotation of retina
        // we can determine x,y,z of focal point, and of all retina pixels
        // to have lighter computation when computing x,y,z of matched point
        
        // WE CHOOSE SCALE AS : 1 3D-pixel per retina pixel size


         currentIndex = 0;
         currentStart = 0;
         current3DEvents = new Event3D[max3DEvents];



        //scaleFactor = Math.round(1/pixel_size);
         scaleFactor = 1/(pixel_size*zoom3D);

        // to center retina on middle point
        int halfRetinaSize = Math.round(retinaSize/2); 


       
        leftRetinaCoordinates = new Point[retinaSize][retinaSize];
        rightRetinaCoordinates = new Point[retinaSize][retinaSize];


        loadCalibrationFromFile("calibstereo.txt");

        left_focal = new Point(0,0,0);

        //update real 3D coordinate of all pixels
        // replace both  by stereopair.updateRealCoordinates(rd,ld=0,retina_angle)

        for (int i=0; i<leftRetinaCoordinates.length; i++){
            for (int j=0; j<leftRetinaCoordinates[i].length; j++){
                leftRetinaCoordinates[i][j] = new Point();
                leftRetinaCoordinates[i][j].x = (i-halfRetinaSize)/zoom3D;
                leftRetinaCoordinates[i][j].y = (j-halfRetinaSize)/zoom3D;
                leftRetinaCoordinates[i][j].z = fc_left_dist/zoom3D;

                // rotate
                //  leftRetinaCoordinates[i][j].rotate(world_rotation);
                //   leftRetinaCoordinates[i][j].z *= -1;
            }
        }

       right_focal = new Point(-retina_translation[0]/zoom3D,-retina_translation[1]/zoom3D,-retina_translation[2]/zoom3D);
     //  right_focal = new Point(-retina_translation[0],-retina_translation[1],-retina_translation[2]);
       right_focal.rotate(retina_rotation);

         for (int i=0; i<rightRetinaCoordinates.length; i++){
            for (int j=0; j<rightRetinaCoordinates[i].length; j++){
                rightRetinaCoordinates[i][j] = new Point();
                rightRetinaCoordinates[i][j].x = (i-halfRetinaSize-retina_translation[0])/zoom3D;
                rightRetinaCoordinates[i][j].y = (j-halfRetinaSize-retina_translation[1])/zoom3D;
                rightRetinaCoordinates[i][j].z = (fc_right_dist-retina_translation[2])/zoom3D;

             //   if(i==0&&j==0)System.out.println("\n 1. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);

                rightRetinaCoordinates[i][j].rotate(retina_rotation);
            //    if(i==0&&j==0)System.out.println("2. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);

            //    rightRetinaCoordinates[i][j].rotate(world_rotation);

             //   rightRetinaCoordinates[i][j].z *= -1;

           //     if(i==0&&j==0)System.out.println("3. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);


            }
        }

         compute3DZones();
      
        
       // if(correctEpipolar) computeEpipolarDistance();
         parameterComputed = true;
        
    } //end compute3DParameters

      // computing 3D parameters for resolution of 3D location of matched points
    private synchronized void compute3DZones(){
        int halfRetinaSize = Math.round(retinaSize/2);

          // compute cage's position
       // int x_mid = Math.round(retinae_distance*scaleFactor/2);
        int x_mid = Math.round(left_focal.x+(right_focal.x-left_focal.x)/2);
        int z = Math.round(cage_distance*scaleFactor);
        float base_height = (retina_height*scaleFactor - halfRetinaSize)+cage_base_height*scaleFactor;
        float door_offset = cage_door_offset*scaleFactor;
        cage = new Cage();
        cage.p1 = new Point(x_mid-Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(-base_height),z);
        cage.p2 = new Point(x_mid+Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(-base_height),z);
        cage.p3 = new Point(x_mid-Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_height*scaleFactor-base_height),z);
        cage.p4 = new Point(x_mid+Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_height*scaleFactor-base_height),z);
        cage.p5 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p6 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p7 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2)-door_offset,Math.round(cage_height*scaleFactor-base_height),z);
        cage.p8 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2)-door_offset,Math.round(cage_height*scaleFactor-base_height),z);
        cage.p9 = new Point(x_mid-Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p10 = new Point(x_mid+Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p11 = new Point(x_mid-Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
        cage.p12 = new Point(x_mid+Math.round(cage_width*scaleFactor/2)-door_offset,Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
        cage.tilt(retina_tilt_angle);

        //
        searchSpace = new Zone();
        int z2 = Math.round((cage_distance-cage_platform_length)*scaleFactor);
        int z3 = Math.round((cage_distance+5)*scaleFactor);
        searchSpace.p1 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z2);
        searchSpace.p2 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z2);
        searchSpace.p3 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z2);
        searchSpace.p4 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z2);
        searchSpace.p5 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        searchSpace.p6 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        searchSpace.p7 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        searchSpace.p8 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);


        searchSpace.tilt(retina_tilt_angle);

          //
        startSpace = new Zone();
        int z4 = Math.round((cage_distance-2)*scaleFactor);

        startSpace.p1 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        startSpace.p2 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        startSpace.p3 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        startSpace.p4 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        startSpace.p5 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        startSpace.p6 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        startSpace.p7 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        startSpace.p8 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);

        startSpace.tilt(retina_tilt_angle);


          //
        endSpace = new Zone();

        z4 = Math.round((cage_distance-5)*scaleFactor);
        endSpace.p1 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        endSpace.p2 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        endSpace.p3 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        endSpace.p4 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        endSpace.p5 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        endSpace.p6 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        endSpace.p7 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        endSpace.p8 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);


        endSpace.tilt(retina_tilt_angle);


        noseSpace = new Zone();

        z4 = Math.round((cage_distance-3)*scaleFactor);
        noseSpace.p1 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z4);
        noseSpace.p2 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z4);
        noseSpace.p3 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z4);
        noseSpace.p4 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z4);
        noseSpace.p5 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z3);
        noseSpace.p6 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z3);
        noseSpace.p7 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z3);
        noseSpace.p8 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z3);


        noseSpace.tilt(retina_tilt_angle);


    }


     // load pixel -> voxels correpondances from file and putString them all into VoxelTable
    private void loadCalibrationFromFile(String filename ) {
        try {
            //use buffering, reading one line at a time
            //FileReader always assumes default encoding is OK!
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null; //not declared within while loop

               // int x = 0;
               // int y = 0;
                float[] data = new float[23];
                int d=0;
                while((line = input.readLine()) != null) {
                    String[] result = line.split("\\s");

                    for (int i = 1; i < result.length; i++) {
                        // store variables
                       // System.out.println("parsing input: "+i);
                        data[d] = Float.parseFloat(result[i]);
                        d++;
                    }
                }

                // store
                retina_translation[0] = data[0]*scaleFactor;
                retina_translation[1] = data[1]*scaleFactor;
                retina_translation[2] = data[2]*scaleFactor;
                fc_left_dist = data[3];
                fc_right_dist = data[4];
                for(int i=0;i<9;i++){
                   retina_rotation[i] = data[i+5];
                   world_rotation[i] = data[i+14];
                }
                world_rotation[1] = -world_rotation[1];
                world_rotation[4] = -world_rotation[4];
                world_rotation[7] = -world_rotation[7];

            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }



   
    
  
    
    public void validateParameterChanges(){
        
//        setValidateParameters(false); //should update gui
        // recompute densities
        densities = resetDensities(lowFilter_density);
    //    densities2 = resetDensities(lowFilter_density2);
        
        shortRangeTotal = computeRangeTotal(lowFilter_radius);
     //   largeRangeTotal = computeRangeTotal(lowFilter_radius2);
        
        shortFRadius = (float)lowFilter_radius;
     //   largeFRadius = (float)lowFilter_radius2;
        
        shortRadiusSq = lowFilter_radius*lowFilter_radius;
    //    largeRadiusSq = lowFilter_radius2*lowFilter_radius2;
        
        
        invDensity1 = 1/(float)lowFilter_density;
   //     invDensity2 = 1/(float)lowFilter_density2;
        
        
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


    void new3DEvent( GroupLabel nextGroup, GroupLabel matchedGroup ){
        Event3D ev3d = create3DEvent(nextGroup,matchedGroup);

        if(trackFingers&&ev3d!=null){

            addToFingerTracker(ev3d);
        }


    }




    // new  event function
    void new3DEvent( int x1, int y1, int x2, int y2, int time, int type ){
        
       // System.out.println("new event x:"+ep.x+" y:"+ep.y+" v:"+ep.accValue+" t:"+ep.updateTime);
      //  if(trackZPlane){
      //    ep.setChanged(true); //maybe not
         
       //   planeTracker.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),plane_tracker_mix);
      //  }
        Event3D ev3d = create3DEvent(x1,y1,x2,y2,time,type);

        if(trackFingers){

            addToFingerTracker(ev3d);
             
             
           /*  if(recordTrackerData){
                 
                 // extract x,y,z
                 if(!fingers.isEmpty()) {
                     FingerCluster fc = fingers.firstElement();
                     
                     // subsampling based on distance with previous record
                     if(distanceBetween(prev_tracker_x,prev_tracker_y,prev_tracker_z,fc.x,fc.y,fc.z)>trackerSubsamplingDistance*scaleFactor){
                         // update prev
                         prev_tracker_x = fc.x;
                         prev_tracker_y = fc.y;
                         prev_tracker_z = fc.z;
                         
                         int xsp = xFromSearchSpace(fc.x,fc.y,fc.z);
                         int ysp = yFromSearchSpace(fc.x,fc.y,fc.z);
                         int zsp = zFromSearchSpace(fc.x,fc.y,fc.z);
           
                         //rotation to correct?
                         int sizex = fc.x_size;
                         int sizey = fc.y_size;
                         int sizez = fc.z_size;//*2;
                         
                         //System.out.println(fc.x+" "+fc.y+" "+fc.z+" "+xsp+" "+ysp+" "+zsp+" "+sizex+" "+sizey+" "+sizez);
                         System.out.println(xsp+" "+ysp+" "+zsp);
                         
                     }
                 }                 
             }*/
        }
       
        
    }

     Point triangulatePoint(int side1, int x1, int y1, int x2, int y2){
         Point p;
         if(side1==LEFT){
            p = triangulate(leftRetinaCoordinates[x1][y1],left_focal,rightRetinaCoordinates[x2][y2],right_focal);
         } else {
            p = triangulate(leftRetinaCoordinates[x2][y2],left_focal,rightRetinaCoordinates[x1][y1],right_focal);

         }
         // should access lookup table of precomputed triangulation
         // slower : triangulate now :

         return p;

     }


   protected Event3D create3DEvent( GroupLabel nextGroup, GroupLabel matchedGroup){
     // create new 3D event
     Event3D newEvent = null;
     

     
     if(nextGroup==null || matchedGroup==null) return null;

    // Point p = triangulatePoint(LEFT, (nextGroup.xmin+nextGroup.xmax)/2, nextGroup.y, (matchedGroup.xmin+matchedGroup.xmax)/2, matchedGroup.y);

     Point p = triangulatePoint(LEFT, nextGroup.xmin, nextGroup.y, matchedGroup.xmin, matchedGroup.y);



     if(p.z<(max_distance*scaleFactor)){
         newEvent = current3DEvents[currentIndex];
         int time = nextGroup.time;
         int type = 1;
         
         if (newEvent==null){
           newEvent = new Event3D(Math.round(p.x), Math.round(p.y), Math.round(p.z), type, time);
           current3DEvents[currentIndex] = newEvent;
         } else {
           newEvent.changeTo(Math.round(p.x), Math.round(p.y), Math.round(p.z), type, time);
         }
         newEvent.d=nextGroup.value;

         currentIndex++;
         // check ring buffer
         if(currentIndex>=max3DEvents){
               currentIndex=0;
           }
           if(currentIndex==currentStart){
               currentStart++;
               if(currentIndex==max3DEvents){
                  currentStart=0;
               }
               // we are filling the buffer from the other side  so we push the start
           }
     }

         return newEvent;
   }
    
  protected Event3D create3DEvent( int x1, int y1, int x2, int y2, int time, int type ){
     // create new 3D event
     Event3D newEvent = null;
     if(type==0) type = -1;
     // triangulate x,y,z position from precomputed table
     Point p = triangulatePoint(LEFT, x1, y1, x2, y2);
   //  if(p.z>0){
         newEvent = current3DEvents[currentIndex];

         if (newEvent==null){
           newEvent = new Event3D(Math.round(p.x), Math.round(p.y), Math.round(p.z), type, time);
           current3DEvents[currentIndex] = newEvent;
         } else {
           newEvent.changeTo(Math.round(p.x), Math.round(p.y), Math.round(p.z), type, time);
         }

         currentIndex++;
         // check ring buffer
         if(currentIndex>=max3DEvents){
               currentIndex=0;
           }
           if(currentIndex==currentStart){
               currentStart++;
               if(currentIndex==max3DEvents){
                  currentStart=0;
               }
               // we are filling the buffer from the other side  so we push the start
           }

        


         //leftRetina3D[x1][y1] = newEvent;
         //rightRetina3D[x2][y2] = newEvent;


// use ring buffer
         //current3DEvents.add(newEvent);
         //new3DEvent(newEvent);

   //  }
     return newEvent;
}


    // delete event function

    
    // finger cluster functions
    void addToFingerTracker( Event3D ep ){
        addToFingerTracker(ep.x0,ep.y0,ep.z0,ep.value,ep.timestamp);
    }

    void addToFingerTracker( int x, int y, int z, float value, int time ){
        
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

        if(!isInZone(searchSpace,x,y,z)){
            return; //?
        }
        if(isInZone(noseSpace,x,y,z)){
            return; //?
        }

             Vector<FingerCluster>  fcv = getNearestFingerClusters(x,y,z,finger_surround);
                //  if(fc==null){
                if(fcv.isEmpty()){
                    if(nbFingers<max_finger_clusters){

                        FingerCluster fc = new FingerCluster(x,y,z,time);
                        fingers.add(fc);
                        //       System.out.println(currentTime+" create cluster at: ["+ep.getX0(method)+","
                        //             +ep.getY0(method)+","+ep.getZ0(method)+"] with updateTime:"+ep.updateTime);



                        nbFingers++;

                        // new cluster created, if automatic recording start record
                        if(autoRecord){
                            recordTrackerData = true;
                        }


                    }// else {
                    //    System.out.println(currentTime+" cannot create new tracker: nbClusters="+nbClusters);
                    //  }
                } else {
                    //fc.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),finger_mix,ep.updateTime);
                    FingerCluster fc = fcv.firstElement();
                    int prev_x = fc.x;
                    int prev_y = fc.y;
                    int prev_z = fc.z;
                    float fmix = finger_mix; //finger_mix_on_evts;
                    if(value<0){
                        fmix = finger_mix; //finger_mix_off_evts;
                    }
                    fc.add(x,y,z,fmix,time); //no value


                    //fcv.remove(fc);
                    // push close neighbouring clusters away
                    //int surroundSq = paw_surround*paw_surround+16;
                    //for(PawCluster fa:fcv){
                    // pushCloseCluster(fa,fc,surroundSq);
                    //}

                    // rebounce on too close neighbouring clusters
                    fcv = getNearestFingerClusters(fc.x,fc.y,fc.z,finger_surround);
                    if(fcv.size()>1){
                        // recursive
                        fcv.remove(fc);
                        rebounceOnCloseClusters(prev_x,prev_y,prev_z,fc,fcv.firstElement(),1);
                    }

                }





     
    }
    
    
    private void rebounceOnCloseClusters( int x, int y, int z, FingerCluster fc, FingerCluster obstacle, int n ){
        //
        if(n>10) return; //safety on recusrivity
               
        float surroundSq = finger_surround*finger_surround+16;
        int prev_x = fc.x;
        int prev_y = fc.y;
        int prev_z = fc.z;
        // compute dist ob-fc 
        float dx = obstacle.x-fc.x;
        float dy = obstacle.y-fc.y;
        float dz = obstacle.z-fc.z;
        float  dist = dx*dx + dy*dy + dz*dz;
        if(dist>surroundSq) return;
        
        float  distx = dx*dx;
        float  disty = dy*dy;
        float  distz = dz*dz;
        
        float px = (distx/surroundSq)*finger_surround;
        float py = (disty/surroundSq)*finger_surround;
        float pz = (distz/surroundSq)*finger_surround;
    //    float rebounceStrength =   (float)Math.sin(Math.acos(cos_fc))*surroundSq;   
        
        // pushing vectors
   //     int push_x = Math.round((obstacle.x-x)*rebounceStrength);
   //     int push_y = Math.round((obstacle.y-y)*rebounceStrength);
   //     int push_z = Math.round((obstacle.z-z)*rebounceStrength);
    //      fc.x += push_x;
   //     fc.y += push_y;
   //     fc.z += push_z;
        
        if(x-obstacle.x>0){
            fc.x += px;
        } else {
            fc.x -= px;
        }
         if(y-obstacle.y>0){
            fc.y += py;
        } else {
            fc.y -= py;
        }
         if(z-obstacle.z>0){
            fc.z += pz;
        } else {
            fc.z -= pz;
        }
       
      
        
        if(fc.x>4000||fc.x<-100){
            int h = 0;
        }
        if(fc.y>4000||fc.y<-100){
            int h = 0;
        }
        if(fc.z>4000||fc.z<-100){
            int h = 0;
        }
        
        
        Vector<FingerCluster> fcv = getNearestFingerClusters(fc.x,fc.y,fc.z,finger_surround);
        if(fcv.size()>1){
            // recursive
            fcv.remove(fc);
            rebounceOnCloseClusters(prev_x,prev_y,prev_z,fc,fcv.firstElement(),++n);
        }
        

    }

     private FingerCluster getNearestFinger( int x, int y, int z, int surround ){
        float min_dist=Float.MAX_VALUE;
        Vector<FingerCluster> closest=new Vector();
        // float currentDistance=0;
        int surroundSq = surround*surround;
        float dist = min_dist;
        int dx = 0;
        int dy = 0;
        int dz  =0;
     //  StringBuffer sb = new StringBuffer();
        try {
        for(FingerCluster fc:fingers){
            if(fc!=null){
               // if(fc.activated){
                    dx = x-fc.x;
                    dy = y-fc.y;
                    dz = z-fc.z;
                    dist = dx*dx + dy*dy + dz*dz;

                    if(dist<=surroundSq){
                        if(dist<min_dist){
                            closest.add(0,fc);
                            min_dist = dist;
                        } else {
                            closest.add(fc);
                        }
                    }
                //     sb.append(currentTime+" getNearestFinger ep: ["+ep.getX0(method)+","+ep.getY0(method)+","+ep.getZ0(method)+
               //      "] fc: ["+fc.x+","+fc.y+","+fc.z+"] dist="+dist+" surroundsq="+surroundSq+" mindist="+min_dist+"\n");

              //  }
            }
        }
        } catch (java.util.ConcurrentModificationException cme ){
            System.out.println("Warning: getNearestFingerClusters: ConcurrentModificationException caught");
        }
    //   if(closest==null){
    //   if(closest.isEmpty()){
    //       System.out.println(sb+" paws.size:"+paws.size());
   //     }
        if(closest.size()>0){
          return closest.firstElement();
        } else {
            return null;
        }
    }

//
//    private FingerCluster getNearestFinger( EventPoint ep, int surround, int method ){
//        float min_dist=Float.MAX_VALUE;
//        FingerCluster closest=null;
//        // float currentDistance=0;
//        int surroundSq = surround*surround;
//        float dist = min_dist;
//        int dx = 0;
//        int dy = 0;
//        int dz  =0;
//        StringBuffer sb = new StringBuffer();
//        for(FingerCluster fc:fingers){
//            if(fc!=null){
//               // if(fc.activated){
//                    dx = ep.getX0(method)-fc.x;
//                    dy = ep.getY0(method)-fc.y;
//                    dz = ep.getZ0(method)-fc.z;
//                    dist = dx*dx + dy*dy + dz*dz;
//
//                    if(dist<surroundSq){
//                        if(dist<min_dist){
//                            closest = fc;
//                            min_dist = dist;
//                        }
//                    }
//                     sb.append("getNearestFinger ep: ["+ep.getX0(method)+","+ep.getY0(method)+","+ep.getZ0(method)+
//                      "] fc: ["+fc.x+","+fc.y+","+fc.z+"] dist="+dist+" surroundsq="+surroundSq+" mindist="+min_dist+"\n");
//
//              //  }
//            }
//        }
//        if(closest==null){
//            System.out.println(sb);
//
//        }
//        return closest;
//    }
//
//
    
     
      
      
      private Vector<FingerCluster> getNearestFingerClusters( int x, int y, int z, int surround ){
        float min_dist=Float.MAX_VALUE;
        Vector<FingerCluster> closest=new Vector();
        // float currentDistance=0;
        int surroundSq = surround*surround;
        float dist = min_dist;
        int dx = 0;
        int dy = 0;
        int dz  =0;
     //  StringBuffer sb = new StringBuffer();
        try {
        for(FingerCluster fc:fingers){
            if(fc!=null){
               // if(fc.activated){
                    dx = x-fc.x;
                    dy = y-fc.y;
                    dz = z-fc.z;
                    dist = dx*dx + dy*dy + dz*dz;
                
                    if(dist<=surroundSq){
                        if(dist<min_dist){
                            closest.add(0,fc);
                            min_dist = dist;
                        } else {
                            closest.add(fc);
                        }
                    }
                //     sb.append(currentTime+" getNearestFinger ep: ["+ep.getX0(method)+","+ep.getY0(method)+","+ep.getZ0(method)+
               //      "] fc: ["+fc.x+","+fc.y+","+fc.z+"] dist="+dist+" surroundsq="+surroundSq+" mindist="+min_dist+"\n");
               
              //  }
            }
        }
        } catch (java.util.ConcurrentModificationException cme ){
            System.out.println("Warning: getNearestFingerClusters: ConcurrentModificationException caught");
        }
    //   if(closest==null){
    //   if(closest.isEmpty()){
    //       System.out.println(sb+" fingers.size:"+fingers.size());           
   //     }
        
        return closest;
    }

   synchronized private void clearGroupLabels(int time){
    // go through all group labels, remove old one
       GroupLabel gp;
       Iterator it = groupLabels.keySet().iterator();

       while (it.hasNext()) {
           gp = (GroupLabel) groupLabels.get((Integer) it.next());
           if (gp.time-time >=  decayTimeLimit) {
              groupLabels.remove(new Integer(gp.value));
              gp = null;
           }

       }

       
   }

   synchronized private void clearDeadFingerTrackers(int time){
       Vector<FingerCluster> toRemove = new Vector();
        for(FingerCluster fc:fingers){
           // if(fc!=null){
               if(fc.nbEvents<tracker_viable_nb_events){
                   if(time-fc.time>tracker_prelifeTime){
                       toRemove.add(fc);
                       nbFingers--;
                   }
               } else {
                   if(time-fc.time>tracker_lifeTime){
                       toRemove.add(fc);
                       nbFingers--;
                   }
               }

           // }
        }
     //  fingers.removeAll(toRemove);
       for(FingerCluster fc:toRemove){
           fingers.remove(fc);
           fc=null;
          // System.out.println("clearDeadFingerTrackers delete Tracker");
       }
        
     //  System.out.println("clearDeadFingerTrackers "+nbFingers);
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
                //resetGCAround(leadPoints,x,yl);
                
                
            } else {
                leadPoints[x][y].disparityLink = DELETE_LINK; //delete
            }
             //#### Removing point
             //remove3DEvent(leadPoints[x][y],method,lead_side);
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
               // resetGCAround(leadPoints,x,yl);
                
                
            } else {
                leadPoints[x][y].disparityLink2 = DELETE_LINK; //delete
            }
            //#### Removing point
            //remove3DEvent(leadPoints[x][y],method,lead_side);
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
                   //  remove3DEvent(leadPoints[ax][y],method,lead_side);
                    // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                   // resetGCAround(leadPoints,ax,yl);
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
                     //remove3DEvent(leadPoints[ax][y],method,lead_side);
                    
                    // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                   // resetGCAround(leadPoints,ax,yl); // add leftMost method as parameter here?
                }
                
            }
        }
        
    }

// return and also hack set xmin and xmax...
     private int nextGroupIndexLeftMostMethod( EventPoint points[][], int x, int y, int value ){
         int xg = -1;
         if(x<0) return xg;
         int currentvg = -1;
         boolean found = false;
         for(int i=x;i<points.length;i++){
             int vg = points[i][y].groupLabel.value;
             if(vg!=value&&vg>0&&!found){
                 xg = i;
//                 if(points[i][y].groupLabel.xmin==0){
//                    points[i][y].groupLabel.xmin = i;
//                 } else {
//                     points[i][y].groupLabel.xmin = (points[i][y].groupLabel.xmin + i)/2;
//                 }
                 points[i][y].groupLabel.xmin = i;
                 currentvg = vg;
                 found = true;
                // break;
             }
             // find xmax
             if(found&&vg!=currentvg){
//                 if(points[i-1][y].groupLabel.xmax==0){
//                    points[i-1][y].groupLabel.xmax = i-1;
//                 } else {
//                     points[i-1][y].groupLabel.xmax = (points[i-1][y].groupLabel.xmax + i-1)/2;
//                 }
                 points[i-1][y].groupLabel.xmax = i-1;
                 break;
             }
         }


         return xg;
     }

      private int nextGroupIndexRightMostMethod( EventPoint points[][], int x, int y, int value ){
         int xg = -1;
         if(x<0) return xg;
         int currentvg = -1;
         boolean found = false;
         for(int i=x;i>=0;i--){
             int vg = points[i][y].groupLabel2.value;
             if(vg!=value&&vg>0&&!found){
                 xg = i;
//                  if(points[i][y].groupLabel.xmax==0){
//                    points[i][y].groupLabel.xmax = i;
//                 } else {
//                     points[i][y].groupLabel.xmax = (points[i][y].groupLabel.xmax + i)/2;
//                 }
                 points[i][y].groupLabel2.xmax = i;
                 currentvg = vg;
                 found = true;
             }
              // find xmax
             if(found&&vg!=currentvg){
//                  if(points[i+1][y].groupLabel.xmin==0){
//                    points[i+1][y].groupLabel.xmin = i+1;
//                 } else {
//                     points[i+1][y].groupLabel.xmin = (points[i+1][y].groupLabel.xmin + i+1)/2;
//                 }
                 points[i+1][y].groupLabel2.xmin = i+1;
                 break;
             }
         }


         return xg;
     }



     private GroupLabel getGroupIndexBiggestMethod( EventPoint points[][], int y, int time ){

         GroupLabel currentLabel = null;
         GroupLabel groupMax = null;
         int value = 0;
         int groupSize = 0;
         int groupMaxSize = 1;

         currentLabel = points[0][y].groupLabel2;
         for(int i=0;i<points.length;i++){

             if(points[i][y].groupLabel2.value!=value){

                 // if there was a group before, set its max now that we found another one
                 if(currentLabel.value!=0) {
                     currentLabel.xmax = i-1;

                    // if(time-currentLabel.time<decayTimeLimit){


                          groupSize = currentLabel.xmax-currentLabel.xmin;
                          if(groupSize>groupMaxSize){
                           groupMaxSize = groupSize;
                           groupMax = currentLabel;
                        }
                   //  }
                 }

                 if(points[i][y].groupLabel2.value!=0){
                 // new group
                 currentLabel = points[i][y].groupLabel2;
                 currentLabel.xmin = i;
                 }

             }
         }
         int last = points.length-1;
         if(points[last][y].groupLabel2.value!=0){
             points[last][y].groupLabel2.xmax = last;
            // if (time - points[last][y].groupLabel.time < decayTimeLimit) {

                 groupSize = points[last][y].groupLabel2.xmax - points[last][y].groupLabel2.xmin;
                 if (groupSize > groupMaxSize) {
                     groupMaxSize = groupSize;
                     groupMax = currentLabel;
                 }
            // }
         }


         return groupMax;
     }

      private void group_check(  int y, int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){

   
          EventPoint leadPoints[][] = rightPoints;
          EventPoint slavePoints[][] = leftPoints;
          //  int leadTime;
          //   int slaveTime;

          if (lead_side == LEFT) {
              leadPoints = leftPoints;
              slavePoints = rightPoints;
          }

          if (method == LEFT_MOST_METHOD) {

              int xg = nextGroupIndexLeftMostMethod(leadPoints, 0, y, 0);
              GroupLabel nextGroup = null;
              GroupLabel nextOtherGroup = null;
              if (xg != -1) {
                  nextGroup = leadPoints[xg][y].groupLabel;
              }
              int xgs = 0;
              int svalue = 0;
              boolean doNext = true;
              while (nextGroup != null) {
                  doNext = true;
                  // match with group from other retina
                  xgs = nextGroupIndexLeftMostMethod(slavePoints, xgs, y, svalue);
                  if (xgs != -1) {
                      nextOtherGroup = slavePoints[xgs][y].groupLabel;
                      svalue = nextOtherGroup.value;

                      // matching
                      if((currentTime-nextGroup.time<decayTimeLimit)&&
                      (currentTime-nextOtherGroup.time<decayTimeLimit)
                              &&nextGroup.count>1 &&nextOtherGroup.count>1){


                          nextGroup.disparity = nextOtherGroup.value;
                          nextOtherGroup.disparity = nextGroup.value; //?
                      } else { // match cannot occur because other group too old

                         // we skip this other group and continue the next loop
                         // with the same curent group
                          doNext = false;
                      }

                  } else {
                      // no more group to match in the other retina epipolar line
                      nextOtherGroup = null;
                      nextGroup.disparity = -1;
                     
                  }
                  // next
                  if(doNext){ // getString next group only if current group has been matched
                      xg = nextGroupIndexLeftMostMethod(leadPoints, xg, y, nextGroup.value);
                      if (xg != -1) {
                          nextGroup = leadPoints[xg][y].groupLabel;
                      } else {
                          // no more groups on this epipolar line
                          nextGroup = null;
                      }
                  }
              }



          //    new3DEvent(x,y,x,i,leadPoints[x][y].updateTime,leadPoints[x][y].sign);


          } else if (method == RIGHT_MOST_METHOD) { // right most method

              int xg = nextGroupIndexRightMostMethod(leadPoints, retinaSize-1, y, 0);
              GroupLabel nextGroup = null;
              GroupLabel nextOtherGroup = null;
              if (xg != -1) {
                  nextGroup = leadPoints[xg][y].groupLabel;
              }
              int xgs = retinaSize-1;
              int svalue = 0;
              while (nextGroup != null) {

                  // match with group from other retina
                  xgs = nextGroupIndexRightMostMethod(slavePoints, xgs, y, svalue);
                  if (xgs != -1) {
                      nextOtherGroup = slavePoints[xgs][y].groupLabel2;
                      svalue = nextOtherGroup.value;

                      // matching
                      nextGroup.disparity = nextOtherGroup.value;
                      nextOtherGroup.disparity = nextGroup.value; //?

                  } else {
                      // no more group to match in the other retina epipolar line
                      nextOtherGroup = null;
                      nextGroup = null;
                      break;
                  }
                  // next
                  xg = nextGroupIndexRightMostMethod(leadPoints, xg, y, nextGroup.value);
                  if (xg != -1) {
                      nextGroup = leadPoints[xg][y].groupLabel2;
                  } else {
                      // no more groups on this epipolar line
                      nextGroup = null;
                  }
              }

          } else if (method == BIGGEST_METHOD) { // biggest group method

              GroupLabel nextGroup = null;
              GroupLabel nextOtherGroup = null;
              nextGroup = getGroupIndexBiggestMethod(leadPoints, y, currentTime);
              if (nextGroup != null) {

                   nextOtherGroup = getGroupIndexBiggestMethod(slavePoints, y, currentTime);
                   if (nextOtherGroup != null) {

                       nextGroup.disparity = nextOtherGroup.value;
                       nextOtherGroup.disparity = nextGroup.value;


                   } else {
                       nextGroup.disparity = -1;
                   }

              }


          } // end if  biggest group method



    }


    private void slave_check( int x, int y, int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){
        
//        int yl = y;// + yLeftCorrection;
//        int yr = y;// + yRightCorrection;
//        
//        if(yl<0||yl>=retinaSize)return;
//        if(yr<0||yr>=retinaSize)return;
//                        
        boolean done  = false;
        int ax = 0;
        
        EventPoint leadPoints[][] = rightPoints;
        EventPoint slavePoints[][] = leftPoints;
      //  int leadTime;
     //   int slaveTime;
        
        if (lead_side==LEFT){
            leadPoints = leftPoints;
            slavePoints = rightPoints;
       //     leadTime = currentTime;
     //       slaveTime = currentTime;
            
        } 
        //else {
          //  leadPoints = rightPoints;
           // slavePoints = leftPoints;
      //      leadTime = currentTime; //to uniformise
       //     slaveTime = currentTime;
            
       // }
       // float checkit = leadPoints[x][y].getValue(currentTime);
        if (leadPoints[x][y].getValue(currentTime)<=valueThreshold){
            return;
            
        }
        
        
        if(method==LEFT_MOST_METHOD){
            
            for(int i=0;i<slavePoints.length;i++){
                if(done) break;
                
                if((slavePoints[i][y].getValue(currentTime)>valueThreshold)
                     //   &&isInRange(i,x,disparity_range)
                    //    &&((slavePoints[i][y].getValue(currentTime)<=leadPoints[x][y].getValue(currentTime)+valueMargin)
                    //    &&(slavePoints[i][y].getValue(currentTime)>=leadPoints[x][y].getValue(currentTime)-valueMargin))
                ){
                    ax = slavePoints[i][y].attachedTo;
                    if(ax>NO_LINK){
                        if(leadPoints[ax][y].getValue(currentTime)<valueThreshold){
                            leadPoints[ax][y].prevDisparityLink = i;
                            leadPoints[ax][y].disparityLink = DELETE_LINK;
                            slavePoints[i][y].attachedTo = NO_LINK;
                            
                            
                            // points had a depth but no more, update neighbours average depth
                            
                            //#### Removing point
                           // remove3DEvent(leadPoints[ax][y],method,lead_side);
                            //updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                            //resetGCAround(leadPoints,ax,yl);
                            ax = NO_LINK;
                            // slavePoints[i][yr].attachedTo = -1;
                        }
                        
                        //should check here if group label is "different" (from...?) and if yes reset ax to NO_LINK
                    }
                    if(ax>x||ax==NO_LINK){
                        boolean doLink = true;
                        if(useGroups){
                            if(x-1>=0){
                                if(leadPoints[x-1][y].getValue(currentTime)>valueThreshold){
                                    if(leadPoints[x-1][y].disparityLink>NO_LINK){
                                        if(slavePoints[i][y].groupLabel!=slavePoints[leadPoints[x-1][y].disparityLink][y].groupLabel){
                                              doLink = false;
                                        }
                                    } else {
                                            doLink = false;
                                    }
                                }
                            }
                            if(i-1>=0){
                                if(slavePoints[i-1][y].getValue(currentTime)>valueThreshold){
                                    if(slavePoints[i-1][y].attachedTo>NO_LINK){
                                        if(leadPoints[x][y].groupLabel!=leadPoints[slavePoints[i-1][y].attachedTo][y].groupLabel){
                                            doLink = false;
                                        }
                                    }  else {
                                        doLink = false;
                                    }
                                }
                            }
                        }
                        
                        if(doLink){
                            
                            
                            slavePoints[i][y].attachedTo = x;
                            
                            
                            if(leadPoints[x][y].disparityLink>NO_LINK){
                                slavePoints[leadPoints[x][y].disparityLink][y].attachedTo = NO_LINK;
                            }
                            leadPoints[x][y].disparityLink = i;
                            
                            // points now has a depth, update neighbours average depth
                            
                            //#### Adding point
                            
                            //   updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                            //addToGCAround(leadPoints,x,yl,dispAvgRange);
                            leadPoints[x][y].changed = true;
                            
                            // commented out to check speed of matching
                            // to putString back!
                            new3DEvent(x,y,x,i,leadPoints[x][y].updateTime,leadPoints[x][y].sign);
                          //  addToFingerTracker(leadPoints[x][y],method);
                            
                            //   if(rightPoints[i][yr].getFreeValue(rightTime)>0){
                            //rightPoints[i][y].free-=leftPoints[ax][y].getCurrentValue();
                            //   rightPoints[i][yr].free=0;
                            // detach previous left
                            
                            //  }
                            if (ax>NO_LINK){
                                leadPoints[ax][y].prevDisparityLink = i;
                                leadPoints[ax][y].disparityLink = DELETE_LINK;
                                
                                //#### Removing point
                              //  remove3DEvent(leadPoints[ax][y],method,lead_side);
                                // points had a depth but no more, update neighbours average depth
                                // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                              //  resetGCAround(leadPoints,ax,yl);
                            }
                            done = true;
                        } // end if doLink
                    }
                } //else {
                    
                    // debug
                    //    System.out.println("");
                    
              //  }
            } // end for
            
        } else { // right most method
            
            for(int i=slavePoints.length-1;i>=0;i--){
                if(done) break;
                
                if((slavePoints[i][y].getValue(currentTime)>valueThreshold)
                      //  &&((slavePoints[i][y].getValue(currentTime)<=leadPoints[x][y].getValue(leadTime)+valueMargin)
                      //  &&(slavePoints[i][y].getValue(currentTime)>=leadPoints[x][y].getValue(leadTime)-valueMargin))
                        ){
                    ax = slavePoints[i][y].attachedTo2;
                    if(ax>NO_LINK){
                        if(leadPoints[ax][y].getValue(currentTime)<valueThreshold){
                            leadPoints[ax][y].prevDisparityLink2 = i;
                            leadPoints[ax][y].disparityLink2 = DELETE_LINK;
                            slavePoints[i][y].attachedTo2 = NO_LINK;
                            
                            // points had a depth but no more, update neighbours average depth
                            //updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                            
                            //#### Removing point
                          //  remove3DEvent(leadPoints[ax][y],method,lead_side);
                            
                           // resetGCAround(leadPoints,ax,yl);
                            ax = NO_LINK;
                            //  slavePoints[i][yr].attachedTo = -1;
                        }
                    }
                    if(ax<x||ax==NO_LINK){
                        
                        // group condition
                        boolean doLink = true;
                        if(useGroups){
                            if(x+1<retinaSize){
                                if(leadPoints[x+1][y].getValue(currentTime)>valueThreshold){
                                    if(leadPoints[x+1][y].disparityLink2>NO_LINK){
                                        if(slavePoints[i][y].groupLabel2!=slavePoints[leadPoints[x+1][y].disparityLink2][y].groupLabel2){
                                             doLink = false;
                                        }
                                    } else {
                                            doLink = false;
                                    }
                                }
                            }
                            if(i+1<retinaSize){
                                if(slavePoints[i+1][y].getValue(currentTime)>valueThreshold){
                                    if(slavePoints[i+1][y].attachedTo2>NO_LINK){
                                        if(leadPoints[x][y].groupLabel2!=leadPoints[slavePoints[i+1][y].attachedTo2][y].groupLabel2){
                                            doLink = false;
                                        }
                                    } else {
                                        doLink = false; // this removes all events exept borders
                                        // because new events are not linked?
                                        // to avoid old unlinked events
                                        // we shoud do otherwise
                                        
                                    }
                                }
                            }
                            
                        }
                        
                        if(doLink){
                            slavePoints[i][y].attachedTo2 = x;
                            
                            
                            if(leadPoints[x][y].disparityLink2>NO_LINK){
                                slavePoints[leadPoints[x][y].disparityLink2][y].attachedTo2 = NO_LINK;
                            }
                            leadPoints[x][y].disparityLink2 = i;
                            // points now has a depth, update neighbours average depth
                            
                            //#### Adding point
                            leadPoints[x][y].changed = true;
                            
                             // new3DEvent(x,y,x,i,method,lead_side);
                              new3DEvent(x,y,x,i,leadPoints[x][y].updateTime,leadPoints[x][y].sign);
                            
                            // updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                           // addToGCAround(leadPoints,x,yl,dispAvgRange);
                            
                             // commented out to check speed of matching
                            // to putString back!
                         //   addToFingerTracker(leadPoints[x][y],method);
                            
                            
                            //   if(rightPoints[i][yr].getFreeValue(rightTime)>0){
                            //rightPoints[i][y].free-=leftPoints[ax][y].getCurrentValue();
                            //   rightPoints[i][yr].free=0;
                            // detach previous left
                            
                            //  }
                            if (ax>NO_LINK){
                                leadPoints[ax][y].prevDisparityLink2 = i;
                                leadPoints[ax][y].disparityLink2 = DELETE_LINK;
                                // points had a depth but no more, update neighbours average depth
                                
                                //#### Removing point
                                // remove3DEvent(leadPoints[ax][y],method,lead_side);
                                // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                                //resetGCAround(leadPoints,ax,yl);
                            }
                            done = true;
                        }
                    } // end if doLink
                }
            } // end for
            
            
        }
        
        
        
        
        if(done&&ax!=NO_LINK) slave_check(ax,y,lead_side,method,leftPoints,rightPoints);
        
    }
    
    
    private void lead_check( int y , int lead_side, int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){
     //   int yl = y;// + yLeftCorrection;
        
     //   if(yl<0||yl>=retinaSize)return; //to remove
        
        // look for unassigned left events
        boolean done  = false;
        
        EventPoint leadPoints[][] = rightPoints;
        
        
        if (lead_side==LEFT){
            leadPoints = leftPoints;
        }
       
        
                
        if(method==LEFT_MOST_METHOD){
            for(int i=0;i<leadPoints.length;i++){
                //if(done) break; //for fast matching
                
              
                    if ((leadPoints[i][y].getValue(currentTime)>valueThreshold)){//&&(leftPoints[i][yl].disparityLink<0)){
                        
                        slave_check(i,y,lead_side,method,leftPoints,rightPoints);
                        //  if(useFastMatching)
                        // done = true; // speed problem when commented out
                    }
                
            }
        } else {
            // add test on label for limiting scope?
            for(int i=leadPoints.length-1;i>=0;i--){
              //  if(done) break; //for fast matching
                if ((leadPoints[i][y].getValue(currentTime)>valueThreshold)){//&&(leftPoints[i][yl].disparityLink<0)){
                    
                    slave_check(i,y,lead_side,method,leftPoints,rightPoints);
                 //   if(useFastMatching) 
                    //done = true;
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
                    
                     // new event
                    
                }
            } else { //remove event
                if(value<=valueThreshold){
                    slave_rem( x, y, lead_side,method,leftPoints,rightPoints );
                    change = true;
                    
                       // remove event
                   
                }
                
            }
            if (change) lead_check(y,lead_side,method,leftPoints,rightPoints);
        }
    }
    


    private void processGroupDisparity( int leftOrRight, int y, int lead_side,
            int method, EventPoint leftPoints[][], EventPoint rightPoints[][] ){ // + event
       

        // group disparity, unlike event disparity, we go through all groups of one line depending of method
        // edit here paul 29-05-09

        group_check(y,lead_side,method,leftPoints,rightPoints);

        group_check(y,lead_side,2,leftPoints,rightPoints);

      
    }


    // processing one event
    protected void processEvent(BinocularEvent e){
        
        // resetEnabled = true;
        //int leftOrRight = e.side;
        int leftOrRight = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
        
        //   System.out.println("processEvent leftOrRight:"+leftOrRight+" e.eye:"+e.eye+" type:"+e.getType());
        
     //  int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
        // paul test opposite polariry
        //  int type=e.polarity==BinocularEvent.Polarity.Off? 1: 0;
     
        int dy = e.y;
        int dx = e.x;
       
        
//        if(useCorrections){
//            // shift y
//            if(leftOrRight==LEFT){
//                dy += yLeftCorrection;
//                //  leftTime = e.timestamp;
//            } else {
//                dy += yRightCorrection;
//                //   rightTime = e.timestamp;
//            }
//
//            // to add: shift x
//        }

        
//        if(correctEpipolar){
//            // then compute angle instead of y, there should be retinaSize * "min angle-max angle" windows
//            // and distance to epipole - min dist to be x, max dit - min dist fitting into retinaSize*classes
//
//            // 1. compute angle for x,y
//            // 2. find category for angle
//            // 3. dy = category(angle)
//            int cy = epipolarAngleFor(dx,dy,leftOrRight);
//
//            // 4. compute dist for x,y
//            // 5. find category for dist-mindist (left or right)
//            // 6. dx = category(dist)
//            int cx = epipolarDistanceFor(dx,dy,leftOrRight);
//
//            dx = cx;
//            dy = cy;
//        }
        
      //  float value = 0;
        
   //     if(dx==-1||dy==-1){
     //       return;
    //    }
        
        if(leftOrRight==LEFT){
            
            // if(type==1)  System.out.println("processEvent leftPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
            
          //  boolean toUpdate = leftPoints[dx][dy].updateFrom(e,dx,dy,LEFT);
            leftPoints[e.x][e.y].updateFrom(e,e.x,e.y,LEFT);
            
         //   if(toUpdate||!throwAwayEvents){
          
            // filter
          //  if(useFilter){
                fastDualLowFilterFrame(leftPoints[dx][dy], leftPoints, rightPoints );
                //      fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
                //       fastDualLowFilterFrame(leftPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
                //     fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
//            } else {
//
                // update group label
//                leftPoints[dx][dy].updateLabel();
//
//                value = leftPoints[dx][dy].getAccValue(currentTime);
//                float previousValue = 0;
//                if(type==0){
//                    previousValue = value+1;
//                }
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                // processDisparity( leftOrRight, dx, dy,  value, type, RIGHT, true );
//            }
           // }
        } else {
            
            // System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type);
            //  if(type==1) System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
            
            
            
          //  boolean toUpdate = 
            rightPoints[dx][dy].updateFrom(e,dx,dy,RIGHT);
            
          //  if(toUpdate||!throwAwayEvents){
           
            // filter
          //  if(useFilter){
                fastDualLowFilterFrame(rightPoints[dx][dy], leftPoints, rightPoints );
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, LEFT, true, leftPoints, rightPoints);
                //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, RIGHT, true, leftPoints, rightPoints);
                //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
//            } else {
//                // update group label
//                rightPoints[dx][dy].updateLabel();
//
//                value = rightPoints[dx][dy].getAccValue(currentTime);
//                float previousValue = 0;
//                if(type==0){
//                    previousValue = value+1;
//                }
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                //   processDisparity( leftOrRight, dx, dy,  value, type, RIGHT, true );
//            }
           // }
        }
    }
    
    
    
    private int epipolarAngleFor( int x, int y, int leftOrRight){
        double dx = 1;
        double dy = 1;
        //
        if(leftOrRight==LEFT){
            dx  = x + epipolar_distance;
            dy  = y - (retinaSize/2);
        } else {
            
            dx  = retinaSize - x + epipolar_distance;
            dy  = y - (retinaSize/2);
            
        }
        
        double a = Math.atan(dy/dx);
        int ac = angleCategory(a);
        if(ac<0){
            System.out.println("Problem in epipolarAngleFor("+x+","+y+","+leftOrRight+") : angle = "+a+" category="+ac);
            ac = -1;
        }
        if(ac>=retinaSize){
            System.out.println("Problem in epipolarAngleFor("+x+","+y+","+leftOrRight+") : angle = "+a+" category="+ac);
            ac = retinaSize-1;
        }
        return ac;
        
    }
    
    private int angleCategory( double rad_angle){
        return (int)Math.round(((rad_angle - epipolar_angle_min)/ epipolar_angle_span)*retinaSize);
    }
    
     private int distCategory( double dist){
                        
        int r = (int)Math.round(((dist - epipolar_distance)/ epipolar_distance_span)*retinaSize);
        
        if(r>retinaSize||r<0){
            int j=0;
        }
        return r;
    }
    
    public Point triangulate( Point p1, Point p2, Point p3, Point p4  ){
        // result
        int xt = 0;
        int yt = 0;
        int zt = 0;
        
        double mua = 0;
        double mub = 0;
        
        
        Point p13 = p1.minus(p3);
        Point p43 = p4.minus(p3);
        
        // should check if solution exists here
        Point p21 = p2.minus(p1);
        // should check if solution exists here
        
        
        double d1343 = p13.x * p43.x + p13.y * p43.y + p13.z * p43.z;
        double d4321 = p43.x * p21.x + p43.y * p21.y + p43.z * p21.z;
        double d1321 = p13.x * p21.x + p13.y * p21.y + p13.z * p21.z;
        double d4343 = p43.x * p43.x + p43.y * p43.y + p43.z * p43.z;
        double d2121 = p21.x * p21.x + p21.y * p21.y + p21.z * p21.z;
        
        double denom = d2121 * d4343 - d4321 * d4321;
        
        //  if (ABS(denom) < EPS) return(FALSE);
        double numer = d1343 * d4321 - d1321 * d4343;
        
        mua = numer / denom;
        mub = (d1343 + d4321 * (mua)) / d4343;
        
        xt = (int)Math.round(p1.x + mua * p21.x);
        yt = (int)Math.round(p1.y + mua * p21.y);
        zt = (int)Math.round(p1.z + mua * p21.z);
        
        Point p5 = new Point(xt,yt,zt);
        
        return p5;
        
    }
     
 
 





    private synchronized boolean isInZone( Zone zone, int x, int y, int z){
        boolean res = true;



        if(z>zone.rp8.z){
            res = false;
        }
        if(z<zone.rp1.z){
            res = false;
        }

        if(y<zone.rp1.y){
            res = false;
        }
        if(y>zone.rp3.y){
            res = false;
        }

        return res;

    }


    protected float rotateYonX( float y, float z, float yRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)) +
                (Math.cos(Math.toRadians(angle))*(y-yRotationCenter)) )+yRotationCenter);
    }
    protected float rotateZonX( float y, float z, float yRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.cos(Math.toRadians(angle))*(z-zRotationCenter)) -
                (Math.sin(Math.toRadians(angle))*(y-yRotationCenter)) )+zRotationCenter );
    }
    protected float rotateXonY( float x, float z, float xRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.cos(Math.toRadians(angle))*(x-xRotationCenter)) -
                (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)))+xRotationCenter );

    }
    protected float rotateZonY( float x, float z, float xRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.sin(Math.toRadians(angle))*(x-xRotationCenter)) +
                (Math.cos(Math.toRadians(angle))*(z-zRotationCenter))) +zRotationCenter );
    }

    protected float distanceBetween( int x1, int y1, int x2, int y2){

        double dx = (double)(x1-x2);
        double dy = (double)(y1-y2);

        float dist = (float)Math.sqrt((dy*dy)+(dx*dx));


        return dist;
    }

   protected float distanceBetween( float x1, float y1, float x2, float y2){

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
     //   float dr = 0;
        float sdr = 0;
        //  int cat = 0;
        //  float bn = 0;
        // float sn = 0;
        
        
        EventPoint[][] eventPoints;
     //   int leftOrRight = ep.zDirection;
    //    EventPoint[][] leadPoints;
    //    EventPoint[][] slavePoints;
        
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
        
        
        for (i=x-lowFilter_radius; i<x+lowFilter_radius+1;i++){
            if(i>=0&&i<eventPoints.length){
                for (j=y-lowFilter_radius; j<y+lowFilter_radius+1;j++){
                    if(j>=0&&j<eventPoints[i].length){
                        EventPoint influencedPoint = eventPoints[i][j];
                      
                        // if within circle
                        dist = ((i-x)*(i-x)) + ((j-y)*(j-y));
                        
                        
                        // smaller range filter influence on neighbour
                        if(dist<shortRadiusSq){
                            f = 1;
                            sdr = (float)Math.sqrt(dist)/shortFRadius;
                            if (sdr!=0) f = 1/sdr;
                            // do not decay value here : we want to know what was brute value of last update
                            influencedPoint.previousShortFilteredValue = influencedPoint.shortFilteredValue;
                    //        influencedPoint.previousDecayedFilteredValue = influencedPoint.decayedFilteredValue;
                            
                            //influencedPoint.previousUpdate = influencedPoint.updateTime;
                            
                            influencedPoint.shortFilteredValue += (ep.lastValue * f)/shortRangeTotal;
                            // use getString..Value(time) to decay value
                      //      influencedPoint.decayedFilteredValue = influencedPoint.getDecayedFilteredValue(ep.updateTime) + (ep.lastValue * f)/shortRangeTotal;
                            influencedPoint.updateTime = ep.updateTime;
                            //influencedPoint.updateTime = ep.updateTime;
                            if (influencedPoint.shortFilteredValue<0) {
                                influencedPoint.shortFilteredValue = 0;
                              //  influencedPoint.decayedFilteredValue = 0;
                            }
                            
                           
                            // update group label status
                            influencedPoint.updateLabel();
                            
                            // compute 3D correspondances
                         
                            processGroupDisparity( ep.side, influencedPoint.y,
                                    LEFT, LEFT_MOST_METHOD,
                                    leftPoints, rightPoints);
                      //      processGroupDisparity( ep.side, influencedPoint.y,
                         //           LEFT, RIGHT_MOST_METHOD,
                        //            leftPoints, rightPoints);



//                             processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
//                                    influencedPoint.shortFilteredValue,
//                                    influencedPoint.previousShortFilteredValue, LEFT, LEFT_MOST_METHOD,
//                                    leftPoints, rightPoints);
//                             processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
//                                    influencedPoint.shortFilteredValue,
//                                    influencedPoint.previousShortFilteredValue, LEFT, RIGHT_MOST_METHOD,
//                                    leftPoints, rightPoints);

//                            processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
//                                    influencedPoint.shortFilteredValue,
//                                    influencedPoint.previousShortFilteredValue, RIGHT, LEFT_MOST_METHOD,
//                                    leftPoints, rightPoints);
//                            processDisparity( ep.side, influencedPoint.x, influencedPoint.y,
//                                    influencedPoint.shortFilteredValue,
//                                    influencedPoint.previousShortFilteredValue, RIGHT, RIGHT_MOST_METHOD,
//                                    leftPoints, rightPoints);
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
    
    protected float decayedValue( float value, int time ){
        float res=value;
        
        float dt = (float)time/decayTimeLimit;
        if(dt<0)dt = -dt;
        if(dt<1){
            res = value * dt;
        }
        return res;
    }
    
 
    
    
    // show 2D view
    
    void checkInsideIntensityFrame(){
        if(show2DWindow && insideIntensityFrame==null) createInsideIntensityFrame();
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
                    method2D++;
                    if(method2D>6)method2D=0;
                    System.out.println("show method:"+method2D);
//                    switch(method2D){
//                        case 0: System.out.println("show left left-most"); break;
//                        case 1: System.out.println("show left right-most"); break;
//                        case 2: System.out.println("show right left-most"); break;
//                        case 3: System.out.println("show onright right-most"); break;
//                        default:;
//                    }
                    insideIntensityCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_F){
                    //showOnlyAcc=!showOnlyAcc;
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
                if(e.getKeyCode()==KeyEvent.VK_G){
                    showLabels=!showLabels;
                    insideIntensityCanvas.display();
                    
                }

               if(e.getKeyCode()==KeyEvent.VK_H){
                    showMaxMinLabel=!showMaxMinLabel;
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
                
                
                
              //  highlight_x = x;
              //  highlight_y = y;
                
                
                if (evt.getButton()==1||evt.getButton()==2){
                    highlight = true;
                    // GL gl=insideIntensityCanvas.getGL();
                    
                    
                } else {
                    highlight = false;
                }



                //   System.out.println("Selected pixel x,y=" + x + "," + y);
                
                int jr = y;// - yRightCorrection;
                int jl = y;//- yLeftCorrection;
                if(x>=0&&x<retinaSize){
                if(jr>=0&&jr<leftPoints[x].length&&jl>=0&&jl<leftPoints[x].length&&x>=0&&x<leftPoints[x].length){
                    EventPoint epL = leftPoints[x][jl];
                    EventPoint epR = rightPoints[x][jr];
                   // EventPoint epL2 = leftPoints[x][jl];
                  //  EventPoint epR2 = rightPoints[x][jr];
                    
                  //  float flr=-1;
                  //  float link=NO_LINK;
                    if (evt.getButton()==1){
//                        switch(method2D){
//                            case 0: highlight_xR = epL.disparityLink; break;
//                            case 1: highlight_xR = epL.disparityLink2; break;
//                            case 2: highlight_xR = epR.disparityLink; break;
//                            case 3: highlight_xR = epR.disparityLink2; break;
//                            default:;
//                        }
                        highlight_x = epL.groupLabel.value;
                        highlight_xR = epL.groupLabel.disparity;

                        System.out.println("highlight_x="+highlight_x+" highlight_xR="+highlight_xR);
                        //    if(highlight_xR>0&&highlight_xR<retinaSize){
                        //     EventPoint epLR = rightPoints[highlight_xR][jr];
                        
                        //      flr = epLR.getValue(currentTime);
                        
                        //   }
                    } else if (evt.getButton()==2){

                        highlight_x = epR.groupLabel2.value;
                        highlight_xR = epR.groupLabel2.disparity;
                        System.out.println("highlight_x="+highlight_x+" highlight_xR="+highlight_xR);

//                        switch(method2D){
//                            case 0: highlight_xR = epR.attachedTo; break;
//                            case 1: highlight_xR = epR.attachedTo2; break;
//                            case 2: highlight_xR = epL.attachedTo; break;
//                            case 3: highlight_xR = epL.attachedTo2; break;
//                            default:;
//                        }
                        //    highlight_xR = epR.attachedTo;
                        
                        //    if(highlight_xR>0&&highlight_xR<retinaSize){
                        //          EventPoint epLR = leftPoints[highlight_xR][jr];
                        //
                        //          flr = epLR.getValue(currentTime);
                        //         link = epLR.disparityLink;
                        //     }
                    }
                    
                    
//
//
//                    float fr=0;
//                    float fl=0;
//
//
//                    fr = epR.getValue(currentTime);
//                    fl = epL.getValue(currentTime);
//
//
//                    float vll = 0;
//                    float vlr = 0;
//                    float vrl = 0;
//                    float vrr = 0;
//
//                    int gll = 0;
//                    int glr = 0;
//                    int grl = 0;
//                    int grr = 0;
//
//                    int dll = NO_LINK,dlr = NO_LINK,drl = NO_LINK,drr = NO_LINK;
//                    if(epL.disparityLink>NO_LINK){
//                        dll = epL.disparityLink - x;
//                        EventPoint epLk = rightPoints[epL.disparityLink][jl];
//                        vll = epLk.getValue(currentTime);
//                        gll = epLk.groupLabel.value;
//                    }
//                    if(epL2.disparityLink2>NO_LINK){
//                        dlr = epL2.disparityLink2 - x;
//                        EventPoint epL2k = rightPoints[epL2.disparityLink2][jl];
//                        vlr = epL2k.getValue(currentTime);
//                        glr = epL2k.groupLabel2.value;
//                    }
//                    if(epR.disparityLink>NO_LINK){
//                        drl = epR.disparityLink - x;
//                        EventPoint epRk = leftPoints[epR.disparityLink][jr];
//                        vrl = epRk.getValue(currentTime);
//                        grl = epRk.groupLabel.value;
//                    }
//                    if(epR2.disparityLink2>NO_LINK){
//                        drr = epR2.disparityLink2 - x;
//                        EventPoint epR2k = leftPoints[epR2.disparityLink2][jr];
//                        vrr = epR2k.getValue(currentTime);
//                        grr = epR2k.groupLabel2.value;
//                    }
                    
                    //    if (flr>-1) {
//                    if (evt.getButton()==1){
//                        System.out.println("LL("+x+","+jl+")="+fl+" z:"+dll+" linked to ("+epL.disparityLink+","+jl+")="+vll
//                                +" label:"+epL.groupLabel+" to label:"+gll);
//                        System.out.println("LR("+x+","+jl+")="+fl+" z:"+dlr+" linked to ("+epL2.disparityLink2+","+jl+")="+vlr
//                                +" label2:"+epL2.groupLabel2+" to label2:"+glr);
//                        System.out.println("RL("+x+","+jr+")="+fr+" z:"+drl+" linked to ("+epR.disparityLink+","+jl+")="+vrl
//                                +" label:"+epR.groupLabel+" to label:"+grl);
//                        System.out.println("RR("+x+","+jr+")="+fr+" z:"+drr+" linked to ("+epR2.disparityLink2+","+jl+")="+vrr
//                                +" label2:"+epR2.groupLabel2+" to label2:"+grr);
//
//                        //   System.out.println("Left("+x+","+jl+")=" + fl + " linked to right("+highlight_xR+","+jr+")="+flr);
//                        //    System.out.println("with z:"+leftPoints[x][y].z+" and z0:"+leftPoints[x][y].z0);
//                    } else if (evt.getButton()==2){
//                        //  System.out.println("Right("+x+","+jr+")=" + fr + " linked to right("+highlight_xR+","+jl+")="+flr+" dlink:"+link);
//                        //  System.out.println("with z:"+leftPoints[highlight_xR][y].z+" and z0:"+leftPoints[highlight_xR][y].z0);
//
//                        System.out.println("LL("+x+","+jl+"):"+dll );
//                        System.out.println("LR("+x+","+jl+"):"+dlr );
//                        System.out.println("RL("+x+","+jr+"):"+drl );
//                        System.out.println("RR("+x+","+jr+"):"+drr );
//
//                    }
                    
                    //    } else {
                    //        System.out.println("Left("+x+","+jl+")=" + fl + " not linked");
                    //    }
                    
                    
                    //System.out.println("+ label:"+epL.groupLabel+" label2:"+epL.groupLabel2);
                    
                    //   float rt = epR.updateTime;
                    //   float lt = epL.updateTime;
                    
                    //      System.out.println("left event time:"+lt+" lefttime: "+currentTime);
                    //      System.out.println("right event time:"+rt+" righttime: "+currentTime);
                    
//                    if(testDrawing){
//                        if (evt.getButton()==1){
//                            leftPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
//                         //   leftPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
//
//                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//
//                        } else if (evt.getButton()==2){
//                            rightPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
//                         //   rightPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
//
//                            processDisparity( RIGHT, x, jl,  1.0f, 0, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( RIGHT, x, jl,  1.0f, 0, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( RIGHT, x, jl,  1.0f, 0, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
//                            processDisparity( RIGHT, x, jl,  1.0f, 0, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
//
//
//                        } else if (evt.getButton()==0){
//                            leftPoints[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
//                            // leftPoints2[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
//                            rightPoints[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
//                            //  rightPoints2[x][jl] = new EventPoint(x,jl,0.0f,epR.updateTime);
//
//                        }
//                    }
                    
                    
                }
                }
                
//                if(showCorrectionMatrix){
//                    if(y>=0&&y<correctionMatrix[0].length&&x>=0&&x<correctionMatrix.length){
//
//                        if(correctionMatrix==null){
//                            System.out.println("correctionMatrix==null");
//                        } else {
//                            System.out.println("correctionMatrix value="+correctionMatrix[x][y]);
//                        }
//                    } else {
//                        System.out.println("out of correctionMatrix bound");
//                    }
//                }
                
                
                
                
                insideIntensityCanvas.display();
                if(a3DCanvas!=null)a3DCanvas.display();
                
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
                       // if(showCorrectionGradient){
                            // to getString it in gradient
                         //   f = (float)intMatrix[i][j]/(float)retinaSize; // to getString it in gradient
                         //   gl.glColor3f(f,f,f);
                       // } else {
                            
                            f = (float)intMatrix[i][j];
                            if(f>0){
                                
                                if (f%2==0)
                                    gl.glColor3f(1,1,1);
                                else
                                    gl.glColor3f(0,0,0);
                            }
                       // }
                        gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                    }
                    
                }
                
            }
            
            
            
            private void drawEventPoints( EventPoint[][] eventPoints, GL gl, int currentTime, boolean left, int yCorrection, boolean useFirst ){
                
                //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);
                int dLink = NO_LINK;
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
                            f = -ep.getAccValue(currentTime);
                          //  if(f<accValueThreshold){
                            //        f = 0;
                             //   }
                        } else {
                          //  if(showSecondFilter){
                         //       f = ep.largeFilteredValue;
                         //   } else {
                                
                                //  f = ep.getValue(currentTime);
                                //   f = ep.getShortFilteredValue(currentTime);
                                f = ep.getValue(currentTime);
                                
                           // }
                        }
                        
                        
                        
                        if(f>valueThreshold){
                            f = f*brightness;
                            if(!showRLColors){
                                gl.glColor3f(f,f,f);
                                gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                
                            } else {
                                if(left){
                                    dLink = NO_LINK;
                                    if(useFirst) dLink = ep.disparityLink;
                                    else dLink = ep.disparityLink2;
                                    
                                    //   System.out.println("left:"+left+" value("+i+","+j+"):"+f);
                                    
                                    if(redBlueShown==0||redBlueShown==2){
                                        if(dLink>NO_LINK&&showDisparity){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        } else {
                                            gl.glColor3f(0,0,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                      
                                    } else if(redBlueShown==3){
                                        if(dLink>NO_LINK){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                        //  gl.glColor3f(0,0,f);
                                        //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        
                                    }
                                    
                                    
                                    
                                } else { // right
                                    dLink = NO_LINK;
                                    if(useFirst) dLink = ep.attachedTo;
                                    else dLink = ep.attachedTo2;
                                    
                                    if(redBlueShown==0||redBlueShown==1){
                                        
                                        if(dLink>NO_LINK&&showDisparity){
                                            gl.glColor3f(f,0.5f,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        } else {
                                            gl.glColor3f(f,0,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                        
                                    } else if(redBlueShown==3){
                                        if(dLink>NO_LINK){
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
                            
                            
                            
                           if (showLabels) {
                            float red = redFromLabel(ep.groupLabel.value,left);
                            float green = greenFromLabel(ep.groupLabel.value,left);
                            float blue = blueFromLabel(ep.groupLabel.value,left);

                            gl.glColor3f(red, green, blue);

                            if (highlight) {
                               if (ep.groupLabel.value == highlight_x) {
                                   gl.glColor3f(1, 1, 0);
                               } else if (ep.groupLabel.value == highlight_xR) {
                                   gl.glColor3f(0, 1, 0);
                               }

                            }

                            
                            //      gl.glColor3f(1,1,1);
                            gl.glRectf(i * intensityZoom, (j) * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);

                            if (showMaxMinLabel&&currentTime-ep.groupLabel.time<decayTimeLimit
                                    &&ep.groupLabel.disparity!=-1
                                    &&ep.groupLabel.count>1) {
                                int xmin = ep.groupLabel.xmin;
                                int xmax = ep.groupLabel.xmax;



                                gl.glColor3f(1, 1, 0);
                                gl.glRectf(xmin * intensityZoom, (j) * intensityZoom, (xmin + 1) * intensityZoom, (j + 1) * intensityZoom);

                                gl.glColor3f(0, 1, 0);
                                gl.glRectf(xmax * intensityZoom, (j) * intensityZoom, (xmax + 1) * intensityZoom, (j + 1) * intensityZoom);
                            }


                        }
                            
                        } // end if value > threshold


                       
                        
                    }
                }
                
            }
            
            float redFromLabel( int label, boolean fromLeft ){

                if (label==0)
                    return 0f;

                float d = 0;
                if(fromLeft) d = 0.2f;

                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 0.5f+d;
                if (label==1)
                    return 0.25f+d;
                if (label==2)
                    return 0.5f+d;
                if (label==3)
                    return 0.75f+d;
                if (label==4)
                    return 1f;
                return 0;
            }
            float greenFromLabel( int label, boolean fromLeft ){

                if (label==0)
                    return 0f;
                
                float d = 0;
                if(fromLeft) d = 0.2f;

                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 0.2f+d;
                if (label==1)
                    return 0f+d;
                if (label==2)
                    return 0.5f+d;
                if (label==3)
                    return 0.5f+d;
                if (label==4)
                    return 0f+d;
                return 0;
            }
            float blueFromLabel( int label, boolean fromLeft ){
                if (label==0)
                    return 0f;

                float d = 0;
                if(fromLeft) d = 0.2f;


                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 1f;
                if (label==1)
                    return 0.75f+d;
                if (label==2)
                    return 0.75f+d;
                if (label==3)
                    return 0.5f+d;
                if (label==4)
                    return 0f+d;
                return 0;
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
             //   if(showCorrectionMatrix){
              //      drawIntMatrix(correctionMatrix,gl);
                    
                    
//                } else { //if(showAcc){
                    //  System.out.println("display left - right  showAcc");
                    
                    
                    //     drawEventPoints(leftPoints,gl,currentTime,true,0);
                    
                    
                    
                    if(method2D==0||method2D==4||method2D==6){
                        if(leftPoints!=null){
                            // System.out.println("display left ");
                            drawEventPoints(leftPoints,gl,currentTime,true,yLeftCorrection,true);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                        }
                        if(rightPoints!=null){
                            //  System.out.println("display right ");
                            
                            drawEventPoints(rightPoints,gl,currentTime,false,yRightCorrection,true);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                        }
                    }
                    if(method2D==1||method2D==4||method2D==6){
                        if(leftPoints!=null){
                            // System.out.println("display left ");
                            drawEventPoints(leftPoints,gl,currentTime,true,yLeftCorrection,false);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                        }
                        if(rightPoints!=null){
                            //  System.out.println("display right ");
                            
                            drawEventPoints(rightPoints,gl,currentTime,false,yRightCorrection,false);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                        }
                    }
                    if(method2D==2||method2D==5||method2D==6){
                        if(leftPoints!=null){
                            // System.out.println("display left ");
                            drawEventPoints(leftPoints,gl,currentTime,false,yLeftCorrection,true);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                        }
                        if(rightPoints!=null){
                            //  System.out.println("display right ");
                            
                            drawEventPoints(rightPoints,gl,currentTime,true,yRightCorrection,true);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                        }
                    }
                    if(method2D==3||method2D==5||method2D==6){
                        if(leftPoints!=null){
                            // System.out.println("display left ");
                            drawEventPoints(leftPoints,gl,currentTime,false,yLeftCorrection,false);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: leftPoints is null");
                        }
                        if(rightPoints!=null){
                            //  System.out.println("display right ");
                            
                            drawEventPoints(rightPoints,gl,currentTime,true,yRightCorrection,false);
                        } else {
                            System.out.println("ERROR: 3DSTatic Display: rightPoints is null");
                        }
                    }
                    
                    
                    
             //   }
                
             
//                if(highlight){
//                    gl.glColor3f(1,1,0);
//                    gl.glRectf(highlight_x*intensityZoom,highlight_y*intensityZoom,(highlight_x+1)*intensityZoom,(highlight_y+1)*intensityZoom);
//                    gl.glColor3f(0,1,0);
//                    gl.glRectf(highlight_xR*intensityZoom,highlight_y*intensityZoom,(highlight_xR+1)*intensityZoom,(highlight_y+1)*intensityZoom);
//
//                }
                
                
                
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
        if(show3DWindow && a3DFrame==null||show3DWindow && windowDeleted) {
            windowDeleted = false;
            create3DFrame();
        }
    }
    
    JFrame a3DFrame=null;
    GLCanvas a3DCanvas=null;
    
    BufferedImage image3DOpenGL=null;
            
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
    float origZ=0;
    
    float tz = 0;
    
    int rdragOrigX =0;
    int rdragOrigY =0;
    int rdragDestX =0;
    int rdragDestY =0;
    boolean rightDragged = false;
    float rtx =0;
    float rty =0;
    float rOrigX=0;
    float rOrigY=0;
    
    boolean middleDragged = false;
    float zOrigY=0;
    int zdragOrigY =0;
    int zdragDestY =0;
    float zty =0;
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
                show3DWindow = false;
            }
        });
        
        a3DCanvas=new GLCanvas();
        
        a3DCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK; 
                int but2mask = InputEvent.BUTTON2_DOWN_MASK;
                int but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    
                    if(e.getClickCount()==2){
                        // reset
                        tx =0;
                        ty =0;
                        origX=0;
                        origY=0;
                        origZ=0;
                        rtx =0;
                        rty =0;
                        rOrigX=0;
                        rOrigY=0;
                        tz = 0;
                        zty = 0;
                        zOrigY=0;
                        
                    } else {
                        // getString final x,y for translation
                        dragOrigX = x;
                        dragOrigY = y;
                        
                    }
                    //   System.out.println(" x:"+x+" y:"+y);
                    // System.out.println("Left mousePressed tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                    
                }  else if ((e.getModifiersEx()&but2mask)==but2mask){
                    // getString final x,y for depth translation
                
                    zdragOrigY = y;
                    //   System.out.println(" x:"+x+" y:"+y);
                  //   System.out.println("Middle mousePressed y:"+y+" zty:"+zty+" zOrigY:"+zOrigY);
                    
                }else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // getString final x,y for rotation
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
            //    int but1mask = InputEvent.BUTTON1_DOWN_MASK,  but3mask = InputEvent.BUTTON3_DOWN_MASK;
                
                if(e.getButton()==MouseEvent.BUTTON1){
                    origX += tx;
                    origY += ty;
                    tx = 0;
                    ty = 0;
                    leftDragged = false;
                    // dragreleased = true;
                    a3DCanvas.display();
                    //System.out.println("Left mouseReleased tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                } else  if(e.getButton()==MouseEvent.BUTTON2){
                  
                    zOrigY += zty;
                   
                    zty = 0;
                    middleDragged = false;
                   
                    a3DCanvas.display();
                  //   System.out.println("Middle mouseReleased zty:"+zty+" zOrigY:"+zOrigY);
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
                int but1mask = InputEvent.BUTTON1_DOWN_MASK;
                int but2mask = InputEvent.BUTTON2_DOWN_MASK;
                int but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    // getString final x,y for translation
                    
                    dragDestX = x;
                    dragDestY = y;
                    
                    leftDragged = true;
                    a3DCanvas.display();
                    
                    
                } else if ((e.getModifiersEx()&but2mask)==but2mask){
                    // getString final x,y for translation
                    
                   
                    zdragDestY = y;
                    
                    middleDragged = true;
                    a3DCanvas.display();
                } else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // getString final x,y for translation
                    
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
               
                
                if(e.getKeyCode()==KeyEvent.VK_C){
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
               
                 if(e.getKeyCode()==KeyEvent.VK_I){
                    // show color, red&blue, onlyred, only blue
                    displaySign++;
                    if(displaySign>2)displaySign=0;
                 }
                if(e.getKeyCode()==KeyEvent.VK_S){
                    searchSpaceMode = !searchSpaceMode;
                    
                    a3DCanvas.display();
                }
            
                if(e.getKeyCode()==KeyEvent.VK_D){
                    clearSpaceMode = !clearSpaceMode;
                    
                    a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_H){
                    showBehindCage = !showBehindCage;

                    a3DCanvas.display();
                }

                  if(e.getKeyCode()==KeyEvent.VK_G){
                    showAll3D = !showAll3D;

                    a3DCanvas.display();
                }


                
                if(e.getKeyCode()==KeyEvent.VK_B){
                    // show color, red&blue, onlyred, only blue
                    testChoice++;
                    if(testChoice>7)testChoice=0;
                    System.out.println("testChoice=="+testChoice);
                    a3DCanvas.display();
                }
                
                if(e.getKeyCode()==KeyEvent.VK_L){
                    recordTrackerData = !recordTrackerData;
                    System.out.println("logging: "+recordTrackerData);
                    a3DCanvas.display();
                    
                }
                
                 if(e.getKeyCode()==KeyEvent.VK_P){
                    pngRecordEnabled = !pngRecordEnabled;
                    frameNumber = 0;
                    System.out.println("png recording: "+pngRecordEnabled);
                    //a3DCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_EQUALS){
                    max_finger_clusters++;
                    System.out.println("increase cluster number to: "+max_finger_clusters);
                   // a3DCanvas.display();
                }
                if(e.getKeyCode()==KeyEvent.VK_MINUS){
                    max_finger_clusters--;
                    System.out.println("decrease cluster number to: "+max_finger_clusters);
                   // a3DCanvas.display();
                }
                if(e.getKeyCode()==KeyEvent.VK_0){
                   resetClusterTrackers();
                   System.out.println("reset clusters");
                   a3DCanvas.display();
                }
                
                if(e.getKeyCode()==KeyEvent.VK_R){
                   resetPawTracker();
                   System.out.println("reset all");
                   a3DCanvas.display();
                }
                
                 if(e.getKeyCode()==KeyEvent.VK_N){
                   checkNose = !checkNose;
                   a3DCanvas.display();
                }

                if(e.getKeyCode()==KeyEvent.VK_H){
                   drawGroupFlat = !drawGroupFlat;
                   a3DCanvas.display();
                }
                if(e.getKeyCode()==KeyEvent.VK_M){
                   drawGroupCentered = !drawGroupCentered;
                   a3DCanvas.display();
                }
                
                
            }
        });
        
        a3DCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            


    /** draw 3d points based on group matching
     *
     *
     */
    synchronized private void draw3DDisparityGroups(GL gl, EventPoint leadPoints[][], int method) {



        //    System.out.println("draw3DDisparityPoints");
        //     System.out.println("draw3DDisparityPoints at "+leadTime);

        if (method == LEFT_MOST_METHOD) {
            // for all epipolar lines
            for (int y = 0; y < retinaSize; y++) {
                // getString next group


                int xg = nextGroupIndexLeftMostMethod(leadPoints, 0, y, 0);
                GroupLabel nextGroup = null;

                if (xg != -1) {
                    nextGroup = leadPoints[xg][y].groupLabel;
                }

                while (nextGroup != null) {

                    // find matched group
                    if (nextGroup.disparity != -1) {


                        GroupLabel matchedGroup = (GroupLabel) groupLabels.get(new Integer(nextGroup.disparity));

                        // triangulate and display 3d group (segment)
                        if (currentTime - nextGroup.time < decayTimeLimit) {
                            if (!drawGroupFlat){
                                draw3DGroup(gl, nextGroup, matchedGroup);
                            } else {
                                draw3DGroup2(gl, nextGroup, matchedGroup);
                            }
                        }

                    } // end if nextGroup has valid disparity

                    // next
                    xg = nextGroupIndexLeftMostMethod(leadPoints, xg, y, nextGroup.value);
                    if (xg != -1) {
                        nextGroup = leadPoints[xg][y].groupLabel;
                    } else {
                        // no more groups on this epipolar line
                        nextGroup = null;
                    }
                }
            } // end for all epipolar lines

        } else { //right most method
            // for all epipolar lines
            for (int y = 0; y < retinaSize; y++) {
                // getString next group


                int xg = nextGroupIndexRightMostMethod(leadPoints, retinaSize - 1, y, 0);
                GroupLabel nextGroup = null;

                if (xg != -1) {
                    nextGroup = leadPoints[xg][y].groupLabel2;
                }

                while (nextGroup != null) {

                    // find matched group
                    if (nextGroup.disparity != -1) {


                        GroupLabel matchedGroup = (GroupLabel) groupLabels.get(new Integer(nextGroup.disparity));

                        // triangulate and display 3d group (segment)
                        if (currentTime - nextGroup.time > decayTimeLimit) {
                            if (!drawGroupFlat){
                                draw3DGroup(gl, nextGroup, matchedGroup,1,0,0);
                            } else {
                                draw3DGroup2(gl, nextGroup, matchedGroup,1,0,0);
                            }
                        }

                    } // end if nextGroup has valid disparity

                    // next
                    xg = nextGroupIndexRightMostMethod(leadPoints, xg, y, nextGroup.value);
                    if (xg != -1) {
                        nextGroup = leadPoints[xg][y].groupLabel2;
                    } else {
                        // no more groups on this epipolar line
                        nextGroup = null;
                    }
                }
            } // end for all epipolar lines
        } // end if method

    }


        /* draw triangulaled 3d segement coresponding to group matching
         *
         */
       private void draw3DGroup( GL gl, GroupLabel nextGroup, GroupLabel matchedGroup){
            if(nextGroup==null || matchedGroup==null) return;
            Point p1 = triangulatePoint(LEFT, nextGroup.xmin, nextGroup.y, matchedGroup.xmin, matchedGroup.y);
            Point p2 = triangulatePoint(LEFT, nextGroup.xmax, nextGroup.y, matchedGroup.xmax, matchedGroup.y);

            shadowCubeLine(gl, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, cube_size, 1, 1, 1, alpha, shadowFactor);


       }
      private void draw3DGroup2( GL gl, GroupLabel nextGroup, GroupLabel matchedGroup){
            if(nextGroup==null || matchedGroup==null) return;
            Point p0 = triangulatePoint(LEFT, (nextGroup.xmin+nextGroup.xmax)/2, nextGroup.y, (matchedGroup.xmin+matchedGroup.xmax)/2, matchedGroup.y);
            Point p1 = triangulatePoint(LEFT, nextGroup.xmin, nextGroup.y, matchedGroup.xmin, matchedGroup.y);
            Point p2 = triangulatePoint(LEFT, nextGroup.xmax, nextGroup.y, matchedGroup.xmax, matchedGroup.y);

            shadowCubeLine(gl, p1.x, p1.y, p0.z, p2.x, p2.y, p0.z, cube_size, 1, 1, 1, alpha, shadowFactor);


       }
       private void draw3DGroup( GL gl, GroupLabel nextGroup, GroupLabel matchedGroup,float r, float g, float b){
            if(nextGroup==null || matchedGroup==null) return;
            Point p1 = triangulatePoint(LEFT, nextGroup.xmin, nextGroup.y, matchedGroup.xmin, matchedGroup.y);
            Point p2 = triangulatePoint(LEFT, nextGroup.xmax, nextGroup.y, matchedGroup.xmax, matchedGroup.y);

            shadowCubeLine(gl, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, cube_size, r, g, b, alpha, shadowFactor);


       }
      private void draw3DGroup2( GL gl, GroupLabel nextGroup, GroupLabel matchedGroup,float r, float g, float b){
            if(nextGroup==null || matchedGroup==null) return;
            Point p0 = triangulatePoint(LEFT, (nextGroup.xmin+nextGroup.xmax)/2, nextGroup.y, (matchedGroup.xmin+matchedGroup.xmax)/2, matchedGroup.y);
            Point p1 = triangulatePoint(LEFT, nextGroup.xmin, nextGroup.y, matchedGroup.xmin, matchedGroup.y);
            Point p2 = triangulatePoint(LEFT, nextGroup.xmax, nextGroup.y, matchedGroup.xmax, matchedGroup.y);

            shadowCubeLine(gl, p1.x, p1.y, p0.z, p2.x, p2.y, p0.z, cube_size, r, g, b, alpha, shadowFactor);


       }
       
            /** draw points in 3D from the two retina arrays and the information about matched disparities
             **/

             synchronized private void draw3DDisparityPoints( GL gl, EventPoint leadPoints[][], int method, int leadTime, EventPoint slavePoints[][], int slaveTime, int zDirection ){




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
                float z0 = 0;
                float x0 = 0;
                float y0 = 0;

                //    System.out.println("draw3DDisparityPoints");
                //     System.out.println("draw3DDisparityPoints at "+leadTime);

             //   int half = retinaSize/2;

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
                            if(displaySign>0){
                                if(displaySign==1&&leadPoints[x][y].sign==-1){
                                    go = false;
                                } else  if(displaySign==2&&leadPoints[x][y].sign==1){
                                    go = false;
                                }

                            }
                        }

                        if(go){

                            if(method==LEFT_MOST_METHOD)      {
                                dx = leadPoints[x][y].disparityLink;
                            } else {
                                dx = leadPoints[x][y].disparityLink2;
                            }

                            if(dx>NO_LINK){

                             //   x0 = leadPoints[x][y].getX0(method);
                              //  y1 = leadPoints[x][y].getY0(method);
                              //  z0 = leadPoints[x][y].getZ0(method);

                           Point p = triangulatePoint(LEFT, x, y, dx, y);
                                    x0 = p.x;
                                    y0 = p.y;
                                    z0 = p.z;


                                    try {
                                        dl = leadPoints[x][y].getValue(leadTime); // or getAccValue()?

                                        dr = slavePoints[dx][y].getValue(slaveTime);

                                        float f = (dl+dr)/2;
                                        //f = f*brightness;

                                            fx = f;

                                            fy = f;

                                            fz = f;

                                        float b = 0.1f*brightness;

                                        float db = 0;
                                        // float dt = leadTime - leadPoints[x][y].updateTime;
                                        // db = 1 - (decayTimeLimit - dt);
                                        //  if(db<0) db = 0;

                                        int tt = leadTime - leadPoints[x][y].updateTime;
                                        //   System.out.println("> draw3DDisparityPoints diff "+tt);

                                        //   System.out.println("draw3DDisparityPoints shadowCube "+f);




                                            // display test with sign //? to test again
                                            int sign = leadPoints[x][y].sign;
                                            if(sign>0)
                                            shadowCube(gl, x0, y0, z0, cube_size, fz+b+db, fy+b+db, fx+b, alpha, shadowFactor);
                                            else
                                            shadowCube(gl, x0, y0, z0, cube_size, fy+b+db, fx+b+db, fz+b, alpha, shadowFactor);


                                    } catch(java.lang.NullPointerException ne){
                                        System.err.println(ne.getMessage()+" x:"+x+" y:"+y+" dx:"+dx);
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



           synchronized private void drawCurrent3DEvents( GL gl, Event3D[] active3DEvents, float size ){


                float fx = 0;
                float fy = 0;
                float fz = 0;


               //     System.out.println("drawCurrent3DEvents active3DEvents.size() "+active3DEvents.size());
               //     System.out.println("drawCurrent3DEvents at "+leadTime);

               //   int half = retinaSize/2;
               boolean allBufferRead = false;
               int i = currentStart;
               while (!allBufferRead) {
                   //  for(int i=currentStart;i<current;i++){
                   if (i == currentIndex) {
                       allBufferRead = true;
                       break;
                   }

                   if (i >= max3DEvents) {
                       i = 0;
                   }
                   Event3D ev = active3DEvents[i];
                   i++;


                    if(ev==null)break;
                   // int cti = currentTime-ev.timestamp;
                    if (currentTime-ev.timestamp<event3DLifeTime){
                     //   System.out.println("drawCurrent3DEvents currentTime: "+currentTime+" ev.timestamp: "+ev.timestamp+" cti: "+cti+" event3DLifeTime:"+event3DLifeTime);

                       // float[] color;
                        float f = ev.value;
                        fy = f;
                        fx = f;
                        fz = f;


                      //  if(colorizePeriod<1)colorizePeriod=1;

                        fz = f;
                        if (f <= 0){ // && showNegative) {
                            fz = -f;
                        }
                        float b = 0.1f*brightness;

                        // highlight hack
                        if(ev.value==-2){
                            fx = 1;
                            fy = 1;
                            fz = 0;
                        } else if(ev.value==-3){
                            fx = 0;
                            fy = 1;
                            fz = 1;
                        }
                        //shadowCube(gl, ev.x0, ev.y0, ev.z0, size/pixel_size, fy+b, fx+b, fz+b, alpha, shadowFactor);
                        if(ev.z0<(max_distance*scaleFactor)||showBehindCage){
                            if(ev.d==highlight_x||ev.d==highlight_xR){
                               shadowCube(gl, ev.x0, ev.y0, ev.z0, size, 1, 1, 0, 1, shadowFactor);

                            } else {
                               shadowCube(gl, ev.x0, ev.y0, ev.z0, size, fy+b, fx+b, fz+b, alpha, shadowFactor);
                            }
                        }
                    }// else {
                     //     System.err.println("drawCurrent3DEvents currentTime: "+currentTime+" ev.timestamp: "+ev.timestamp+" cti: "+cti+" event3DLifeTime:"+event3DLifeTime);

                   // }
                }


            }
   
          private void draw3DAxes( GL gl ){
                // gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                // gl.glEnable(gl.GL_BLEND);
                //  gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//x
                gl.glColor3f(0.0f,1.0f,0.0f);
                line3D( gl,  0,  0,  0,  200/zoom3D ,0 ,0);
                //   gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//y
                gl.glColor3f(0.0f,1.0f,0.0f);
                line3D( gl,  0,  0,  0,  0 , 200/zoom3D ,0);
                //   line3D ( gl,  retinaSize/2, 5, 0,  retinaSize/2 , 5 ,0);
                //  gl.glColor4f(1.0f,0.0f,0.0f,0.2f);	//z
                gl.glColor3f(1.0f,0.0f,0.0f);
                line3D( gl,  0,  0,  0,  0 ,0 ,200/zoom3D);
                //   gl.glDisable(gl.GL_BLEND);

            }
            
           
           
            private void draw3DFrames( GL gl ){
               // int half = retinaSize/2;
                
           
                //  System.out.println("middleAngle for planeAngle("+planeAngle+")= "+middleAngle);
                
                
                 if(showRetina&&parameterComputed){
                    
                    if(recordTrackerData){
                        gl.glColor3f(1.0f,0.0f,0.0f);
                    } else {
                        gl.glColor3f(0.0f,0.0f,1.0f);
                    }
                    
                    if(leftRetinaCoordinates[0][0]!=null){
                    line3D( gl,  leftRetinaCoordinates[0][0].x,  leftRetinaCoordinates[0][0].y,  leftRetinaCoordinates[0][0].z,
                            leftRetinaCoordinates[0][retinaSize-1].x,  leftRetinaCoordinates[0][retinaSize-1].y,  leftRetinaCoordinates[0][retinaSize-1].z);
                    line3D( gl,  leftRetinaCoordinates[0][0].x,  leftRetinaCoordinates[0][0].y,  leftRetinaCoordinates[0][0].z,
                            leftRetinaCoordinates[retinaSize-1][0].x,  leftRetinaCoordinates[retinaSize-1][0].y,  leftRetinaCoordinates[retinaSize-1][0].z);
                    line3D( gl,  leftRetinaCoordinates[0][retinaSize-1].x,  leftRetinaCoordinates[0][retinaSize-1].y,  leftRetinaCoordinates[0][retinaSize-1].z,
                            leftRetinaCoordinates[retinaSize-1][retinaSize-1].x,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].y,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  leftRetinaCoordinates[retinaSize-1][0].x,  leftRetinaCoordinates[retinaSize-1][0].y,  leftRetinaCoordinates[retinaSize-1][0].z,
                            leftRetinaCoordinates[retinaSize-1][retinaSize-1].x,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].y,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].z);

                    shadowCube(gl, leftRetinaCoordinates[0][0].x,  leftRetinaCoordinates[0][0].y,  leftRetinaCoordinates[0][0].z, 10, 0, 1, 0, 0.5f, 0);
                    shadowCube(gl, leftRetinaCoordinates[0][retinaSize-1].x,  leftRetinaCoordinates[0][retinaSize-1].y,  leftRetinaCoordinates[0][retinaSize-1].z, 10, 0, 0, 1, 0.5f, 0);
                    shadowCube(gl, leftRetinaCoordinates[retinaSize-1][0].x,  leftRetinaCoordinates[retinaSize-1][0].y,  leftRetinaCoordinates[retinaSize-1][0].z, 10, 1, 0, 1, 0.5f, 0);
                    shadowCube(gl, leftRetinaCoordinates[retinaSize-1][retinaSize-1].x,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].y,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].z, 10, 1, 0, 0, 0.5f, 0);

     //                   System.out.println("b leftRetinaCoordinates[0][0] "+leftRetinaCoordinates[0][0].x+" "+ leftRetinaCoordinates[0][0].y+" "+leftRetinaCoordinates[0][0].z);
     //   System.out.println("b leftRetinaCoordinates[0][127] "+leftRetinaCoordinates[0][127].x+" "+ leftRetinaCoordinates[0][127].y+" "+leftRetinaCoordinates[0][127].z);
    //    System.out.println("b leftRetinaCoordinates[127][0] "+leftRetinaCoordinates[127][0].x+" "+ leftRetinaCoordinates[127][0].y+" "+leftRetinaCoordinates[127][0].z);
    //    System.out.println("b leftRetinaCoordinates[127][127] "+leftRetinaCoordinates[127][127].x+" "+ leftRetinaCoordinates[127][127].y+" "+leftRetinaCoordinates[127][127].z);


                   }
                    gl.glColor3f(0.0f,0.0f,1.0f);
                    if(rightRetinaCoordinates[0][0]!=null&&rightRetinaCoordinates[0][retinaSize-1]!=null){
                    line3D( gl,  rightRetinaCoordinates[0][0].x,  rightRetinaCoordinates[0][0].y,  rightRetinaCoordinates[0][0].z,
                            rightRetinaCoordinates[0][retinaSize-1].x,  rightRetinaCoordinates[0][retinaSize-1].y,  rightRetinaCoordinates[0][retinaSize-1].z);
                    line3D( gl,  rightRetinaCoordinates[0][0].x,  rightRetinaCoordinates[0][0].y,  rightRetinaCoordinates[0][0].z,
                            rightRetinaCoordinates[retinaSize-1][0].x,  rightRetinaCoordinates[retinaSize-1][0].y,  rightRetinaCoordinates[retinaSize-1][0].z);
                    line3D( gl,  rightRetinaCoordinates[0][retinaSize-1].x,  rightRetinaCoordinates[0][retinaSize-1].y,  rightRetinaCoordinates[0][retinaSize-1].z,
                            rightRetinaCoordinates[retinaSize-1][retinaSize-1].x,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].y,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  rightRetinaCoordinates[retinaSize-1][0].x,  rightRetinaCoordinates[retinaSize-1][0].y,  rightRetinaCoordinates[retinaSize-1][0].z,
                            rightRetinaCoordinates[retinaSize-1][retinaSize-1].x,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].y,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].z);


                      shadowCube(gl, rightRetinaCoordinates[0][0].x,  rightRetinaCoordinates[0][0].y,  rightRetinaCoordinates[0][0].z, 10, 0, 1, 0, 0.5f, 0);
                    shadowCube(gl, rightRetinaCoordinates[0][retinaSize-1].x,  rightRetinaCoordinates[0][retinaSize-1].y,  rightRetinaCoordinates[0][retinaSize-1].z, 10, 0, 0, 1, 0.5f, 0);
                    shadowCube(gl, rightRetinaCoordinates[retinaSize-1][0].x,  rightRetinaCoordinates[retinaSize-1][0].y,  rightRetinaCoordinates[retinaSize-1][0].z, 10, 1, 0, 1, 0.5f, 0);
                    shadowCube(gl, rightRetinaCoordinates[retinaSize-1][retinaSize-1].x,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].y,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].z, 10, 1, 0, 0, 0.5f, 0);


                    }
                    shadowCube(gl, left_focal.x, left_focal.y, left_focal.z, 10, 1, 1, 0, 0.5f, 0);
                    shadowCube(gl, right_focal.x, right_focal.y, right_focal.z, 10, 0, 1, 1, 0.5f, 0);

                
                }
                
                
                // blue frame
                if(showCage&&parameterComputed){
               // if(showCage){
                    gl.glColor3f(0.0f,0.0f,1.0f);	// blue color
                      
                        line3D( gl,  cage.p1.x,  cage.p1.y,  cage.p1.z,   cage.p2.x,  cage.p2.y,  cage.p2.z);
                        
                        line3D( gl,  cage.p1.x,  cage.p1.y,  cage.p1.z,  cage.p3.x,  cage.p3.y,  cage.p3.z);
                        line3D( gl,  cage.p2.x,  cage.p2.y,  cage.p2.z,  cage.p4.x,  cage.p4.y,  cage.p4.z);
                        
                        line3D( gl,  cage.p3.x,  cage.p3.y,  cage.p3.z,  cage.p4.x,  cage.p4.y,  cage.p4.z);
                        
                        // cage
                        
                        
                        
                        line3D( gl,  cage.p5.x,  cage.p5.y,  cage.p5.z,  cage.p6.x,  cage.p6.y,  cage.p6.z);
                        line3D( gl,  cage.p5.x,  cage.p5.y,  cage.p5.z,  cage.p7.x,  cage.p7.y,  cage.p7.z);
                        line3D( gl,  cage.p6.x,  cage.p6.y,  cage.p6.z,  cage.p8.x,  cage.p8.y,  cage.p8.z);
                        
                        
                        
                        line3D( gl,  cage.p9.x,  cage.p9.y,  cage.p9.z,  cage.p10.x,  cage.p10.y,  cage.p10.z);
                        line3D( gl,  cage.p9.x,  cage.p9.y,  cage.p9.z,  cage.p11.x,  cage.p11.y,  cage.p11.z);
                        line3D( gl,  cage.p10.x,  cage.p10.y,  cage.p10.z,  cage.p12.x,  cage.p12.y,  cage.p12.z);
                        line3D( gl,  cage.p11.x,  cage.p11.y,  cage.p11.z,  cage.p12.x,  cage.p12.y,  cage.p12.z);
                                                                                                            
                    
                }
                  
                  if(showZones&&parameterComputed){
                    gl.glColor3f(1.0f,1.0f,0.0f);	// yellow color
                                                                                                                 
                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,   searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z);
                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z);
                        line3D( gl,  searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z);
                        line3D( gl,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z);
                        line3D( gl,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z);
                        line3D( gl,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z);
                        line3D( gl,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z);

                        line3D( gl,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z);
                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z);
                        line3D( gl,  searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z);
                        line3D( gl,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z);
                        line3D( gl,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z);

//                         gl.glColor3f(0.5f,1.0f,0.5f);	// yellow color
//                                                                                                                 
//                        line3D( gl,  startSpace.rp1.x,  startSpace.rp1.y,  startSpace.rp1.z,   startSpace.rp2.x,  startSpace.rp2.y,  startSpace.rp2.z);                        
//                        line3D( gl,  startSpace.rp1.x,  startSpace.rp1.y,  startSpace.rp1.z,  startSpace.rp3.x,  startSpace.rp3.y,  startSpace.rp3.z);
//                        line3D( gl,  startSpace.rp2.x,  startSpace.rp2.y,  startSpace.rp2.z,  startSpace.rp4.x,  startSpace.rp4.y,  startSpace.rp4.z);                        
//                        line3D( gl,  startSpace.rp3.x,  startSpace.rp3.y,  startSpace.rp3.z,  startSpace.rp4.x,  startSpace.rp4.y,  startSpace.rp4.z);    
//                        line3D( gl,  startSpace.rp5.x,  startSpace.rp5.y,  startSpace.rp5.z,  startSpace.rp6.x,  startSpace.rp6.y,  startSpace.rp6.z);
//                        line3D( gl,  startSpace.rp5.x,  startSpace.rp5.y,  startSpace.rp5.z,  startSpace.rp7.x,  startSpace.rp7.y,  startSpace.rp7.z);
//                        line3D( gl,  startSpace.rp6.x,  startSpace.rp6.y,  startSpace.rp6.z,  startSpace.rp8.x,  startSpace.rp8.y,  startSpace.rp8.z); 
//                        
//                        line3D( gl,  startSpace.rp7.x,  startSpace.rp7.y,  startSpace.rp7.z,  startSpace.rp8.x,  startSpace.rp8.y,  startSpace.rp8.z);             
//                        line3D( gl,  startSpace.rp1.x,  startSpace.rp1.y,  startSpace.rp1.z,  startSpace.rp5.x,  startSpace.rp5.y,  startSpace.rp5.z);
//                        line3D( gl,  startSpace.rp2.x,  startSpace.rp2.y,  startSpace.rp2.z,  startSpace.rp6.x,  startSpace.rp6.y,  startSpace.rp6.z);       
//                        line3D( gl,  startSpace.rp3.x,  startSpace.rp3.y,  startSpace.rp3.z,  startSpace.rp7.x,  startSpace.rp7.y,  startSpace.rp7.z);       
//                        line3D( gl,  startSpace.rp4.x,  startSpace.rp4.y,  startSpace.rp4.z,  startSpace.rp8.x,  startSpace.rp8.y,  startSpace.rp8.z);       
//                        
//                         gl.glColor3f(1.0f,1.0f,0.0f);	// yellow color
//                                                                                                                 
//                        line3D( gl,  endSpace.rp1.x,  endSpace.rp1.y,  endSpace.rp1.z,   endSpace.rp2.x,  endSpace.rp2.y,  endSpace.rp2.z);                        
//                        line3D( gl,  endSpace.rp1.x,  endSpace.rp1.y,  endSpace.rp1.z,  endSpace.rp3.x,  endSpace.rp3.y,  endSpace.rp3.z);
//                        line3D( gl,  endSpace.rp2.x,  endSpace.rp2.y,  endSpace.rp2.z,  endSpace.rp4.x,  endSpace.rp4.y,  endSpace.rp4.z);                        
//                        line3D( gl,  endSpace.rp3.x,  endSpace.rp3.y,  endSpace.rp3.z,  endSpace.rp4.x,  endSpace.rp4.y,  endSpace.rp4.z);    
//                        line3D( gl,  endSpace.rp5.x,  endSpace.rp5.y,  endSpace.rp5.z,  endSpace.rp6.x,  endSpace.rp6.y,  endSpace.rp6.z);
//                        line3D( gl,  endSpace.rp5.x,  endSpace.rp5.y,  endSpace.rp5.z,  endSpace.rp7.x,  endSpace.rp7.y,  endSpace.rp7.z);
//                        line3D( gl,  endSpace.rp6.x,  endSpace.rp6.y,  endSpace.rp6.z,  endSpace.rp8.x,  endSpace.rp8.y,  endSpace.rp8.z); 
//                        
//                        line3D( gl,  endSpace.rp7.x,  endSpace.rp7.y,  endSpace.rp7.z,  endSpace.rp8.x,  endSpace.rp8.y,  endSpace.rp8.z);             
//                        line3D( gl,  endSpace.rp1.x,  endSpace.rp1.y,  endSpace.rp1.z,  endSpace.rp5.x,  endSpace.rp5.y,  endSpace.rp5.z);
//                        line3D( gl,  endSpace.rp2.x,  endSpace.rp2.y,  endSpace.rp2.z,  endSpace.rp6.x,  endSpace.rp6.y,  endSpace.rp6.z);       
//                        line3D( gl,  endSpace.rp3.x,  endSpace.rp3.y,  endSpace.rp3.z,  endSpace.rp7.x,  endSpace.rp7.y,  endSpace.rp7.z);       
//                        line3D( gl,  endSpace.rp4.x,  endSpace.rp4.y,  endSpace.rp4.z,  endSpace.rp8.x,  endSpace.rp8.y,  endSpace.rp8.z);       
//                        
                        if(checkNose){
                            
                          gl.glColor3f(1.0f,0.5f,0.0f);	
                                                                                                                 
                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,   noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z);                        
                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z);
                        line3D( gl,  noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z);                        
                        line3D( gl,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z);    
                        line3D( gl,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z);
                        line3D( gl,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z);
                        line3D( gl,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z); 
                        
                        line3D( gl,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z);             
                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z);
                        line3D( gl,  noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z);       
                        line3D( gl,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z);       
                        line3D( gl,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z);       
                        
                        }
                        
                }
                
                
           //     gl.glFlush();
                
             
                
                
            }
            
            
            
            
            private void line3D(GL gl, float x, float y, float z, float x2,  float y2,  float z2) {
                gl.glBegin(gl.GL_LINES);
                gl.glVertex3f( x,y,z);
                gl.glVertex3f( x2,y2,z2);
                gl.glEnd();
            }

            private void shadowCubeLine(GL gl, float x, float y, float z, float x2,  float y2,  float z2, float size, float r, float g, float b, float alpha, float shadow) {
                  gl.glLineWidth(size);
                  gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                  gl.glEnable(gl.GL_BLEND);
                  gl.glColor4f(r,g,b,alpha);

                   gl.glBegin(gl.GL_LINES);
                   gl.glVertex3f( x,y,z);
                   gl.glVertex3f( x2,y2,z2);
                   gl.glEnd();

                  gl.glDisable(gl.GL_BLEND);
                  gl.glLineWidth(1);
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
            
            private void rectangle3D(GL gl, float x, float y, float z, float xsize, float ysize, float zsize) {
                // gl.glTranslatef(100.0f, 100.0f,0.0f);
                
                //  gl.glRotatef(rotation,0.0f,1.0f,0.0f);	// Rotate The cube around the Y axis
                //  gl.glRotatef(rotation,1.0f,1.0f,1.0f);
                //    gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
                gl.glBegin(gl.GL_LINES);
                //   gl.glColor3f(0.0f,1.0f,0.0f);	// Color Blue
                gl.glVertex3f( x-xsize, y-ysize,z-zsize);
                gl.glVertex3f(x-xsize, y+ysize,z-zsize);
                gl.glVertex3f(x-xsize, y-ysize, z-zsize);
                gl.glVertex3f( x+xsize, y-ysize, z-zsize);
                //   gl.glColor3f(1.0f,0.5f,0.0f);	// Color Orange
                gl.glVertex3f( x-xsize,y+ysize, z-zsize);
                gl.glVertex3f(x+xsize,y+ysize, z-zsize);
                gl.glVertex3f(x+xsize,y+ysize,z-zsize);
                gl.glVertex3f( x+xsize,y-ysize,z-zsize);
                //  gl.glColor3f(1.0f,0.0f,0.0f);	// Color Red
                gl.glVertex3f( x-xsize, y-ysize, z-zsize);
                gl.glVertex3f(x-xsize, y-ysize, z+zsize);
                gl.glVertex3f(x-xsize,y+ysize, z-zsize);
                gl.glVertex3f( x-xsize,y+ysize, z+zsize);
                //  gl.glColor3f(1.0f,1.0f,0.0f);	// Color Yellow
                gl.glVertex3f( x+xsize,y+ysize,z-zsize);
                gl.glVertex3f(x+xsize,y+ysize,z+zsize);
                gl.glVertex3f(x+xsize, y-ysize,z-zsize);
                gl.glVertex3f( x+xsize, y-ysize,z+zsize);
                //  gl.glColor3f(0.0f,0.0f,1.0f);	// Color Blue
                gl.glVertex3f(x-xsize, y-ysize, z+zsize);
                gl.glVertex3f(x-xsize, y+ysize,z+zsize);
                gl.glVertex3f(x-xsize,y-ysize,z+zsize);
                gl.glVertex3f(x+xsize,y-ysize, z+zsize);
                //  gl.glColor3f(1.0f,0.0f,1.0f);	// Color Violet
                gl.glVertex3f( x-xsize, y+ysize,z+zsize);
                gl.glVertex3f( x+xsize, y+ysize, z+zsize);
                gl.glVertex3f( x+xsize,y-ysize, z+zsize);
                gl.glVertex3f( x+xsize,y+ysize,z+zsize);
                gl.glEnd();
                
                // rotation += 0.9f;
                
            }
           
            
            
           private void rotatedRectangle2DFilled(GL gl, float x1, float x2, float y1, float y2, float z1, float z2){                // gl.glTranslatef(100.0f, 100.0f,0.0f);
                
                //  gl.glRotatef(rotation,0.0f,1.0f,0.0f);	// Rotate The cube around the Y axis
                //  gl.glRotatef(rotation,1.0f,1.0f,1.0f);
                //    gl.glBegin(gl.GL_QUADS);		// Draw The Cube Using quads
               
                gl.glBegin(gl.GL_POLYGON);
              
              
                gl.glVertex3f(x1, y1, z1);
                gl.glVertex3f( x2, y1, z1);
             
            
                gl.glVertex3f( x1, y1, z1);             
                gl.glVertex3f( x1,y2, z2);
          
        
                gl.glVertex3f( x1, y2,z2);
                gl.glVertex3f( x2, y2, z2);
                gl.glVertex3f( x2,y1, z1);
                gl.glVertex3f( x2,y2,z2);
                gl.glEnd();
                
                // rotation += 0.9f;
                
            }
            
            
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glPushMatrix();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);                                
                int font = GLUT.BITMAP_HELVETICA_12;
                
             
             //System.out.println("display: system time:"+System.currentTimeMillis());
                
                if(leftDragged){
                    leftDragged = false;
                    tx = (dragDestX - dragOrigX)*3;
                    ty = (dragOrigY - dragDestY)*3;
                    
                }
                if(middleDragged){
                    middleDragged = false;
                   
                    zty = zdragOrigY-zdragDestY;
                    zty = zty * 20;
                }
                if(rightDragged){
                    rightDragged = false;
                    //    rtx = rdragDestX-rdragOrigX;
                    rtx = rdragOrigX-rdragDestX;
                    rty = rdragOrigY-rdragDestY;
                    
                }
                
                float ox = origX+tx;
                float oy = origY+ty;
                float oz = zOrigY+zty;
               // origZ = oz;
              //  origZ+=1.0f;
              
                
               
                
           //       gl.glTranslatef(ox,oz,oy);
           //   gl.glTranslatef(ox,oy,oz*10);
                float translation_x = 0;
                float translation_y = 0;
                float translation_z = 0;


                
                translation_x = ox; //-4000;
                translation_y = oy-25;
                translation_z = -oz+tz-25000;

                gl.glTranslatef(translation_x,translation_y,translation_z);
             
                
                float rx = rOrigX+rtx;
                float ry = rOrigY+rty;

       //          gl.glTranslatef(-translation_x,-translation_y,-translation_z);
   //             gl.glTranslatef(-translation_x,-translation_y,0);
                
                //gl.glTranslatef(0, 0, -1000);
                
                gl.glRotatef(rx+krx,0.0f,1.0f,0.0f);
                gl.glRotatef(ry+kry,1.0f,0.0f,0.0f);
                
              //  gl.glTranslatef(0, 0, 1000);
     //           gl.glTranslatef(-translation_x,-translation_y,-translation_z);
      //          gl.glTranslatef(translation_x,translation_y,translation_z);
     //        gl.glTranslatef(translation_x,translation_y,0);
            
                // keyboard rotation :
                rOrigY += kry;
                rOrigX += krx;
                kry = 0;
                krx = 0;
           
                if(showAxes){
                    draw3DAxes(gl);
                }
                           
                if(parameterComputed){
                    synchronized (current3DEvents) {
                       drawCurrent3DEvents(gl, current3DEvents, cube_size);
                    }
                    if(showAll3D){
                        synchronized (groupLabels) {
                            if(drawGroupCentered){
                              draw3DDisparityGroups( gl , leftPoints, LEFT_MOST_METHOD);
                            } else {
                              draw3DDisparityGroups( gl , leftPoints, BIGGEST_METHOD);
                            }
                        }
                    }
//                       switch(display3DChoice){
//
//                                case 0:
//                                    draw3DDisparityGroups( gl , leftPoints, LEFT_MOST_METHOD);
//                                    draw3DDisparityGroups( gl , leftPoints, RIGHT_MOST_METHOD);
//                                    break;
//
//                                case 1:
//                                    draw3DDisparityGroups( gl , leftPoints, LEFT_MOST_METHOD);
//                                    break;
//                                case 2:
//                                    draw3DDisparityGroups( gl , leftPoints, RIGHT_MOST_METHOD);
//                                    break;

//                                case 0:
//                                    draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
//                                    draw3DDisparityPoints( gl , leftPoints, RIGHT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
//                                    break;
//
//                                case 1:
//                                    draw3DDisparityPoints( gl , leftPoints, LEFT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
//                                    break;
//                                case 2:
//                                    draw3DDisparityPoints( gl , leftPoints, RIGHT_MOST_METHOD, currentTime, rightPoints, currentTime, 1);
//                                    break;
//
//                                default:
//                            }
                   
                }
                  
                
                if(showFingers||showFingersRange){
               //     float colorFinger = 0;
               //     float colorFinger2 = 0;
               ///     Color trackerColor = null;
                    try{
                        for(FingerCluster fc:fingers){
                            
                            
                            if(fc!=null){
                       
                                if(showFingers){
                                     gl.glColor3f(1.0f,0,0);
                                            //colorFinger,colorFinger2);
                                    //cube(gl,fc.x,fc.y,fc.z,finger_surround);
                                    rectangle3D(gl,fc.x,fc.y,fc.z,fc.x_size/2,fc.y_size/2,fc.z_size/2);  
                                }
                                if(showFingersRange){
                                    if(fc.nbEvents<tracker_viable_nb_events){
                                        gl.glColor3f(1.0f,1.0f,0.0f);
                                    } else {
                                        gl.glColor3f(1.0f,0,1.0f);
                                    }
                                            //colorFinger,colorFinger2);
                                    cube(gl,fc.x,fc.y,fc.z,finger_surround/2);
                                   // rectangle3D(gl,fc.x,fc.y,fc.z,fc.x_size*2,fc.y_size*2,fc.z_size*2);  
                                }    
                                    
                              //  }
                            }
                        }                        
                    } catch(java.util.ConcurrentModificationException e){
                    }
                }
                             
                //    if(showFrame){
                draw3DFrames(gl);
                //   }
            
                    gl.glPopMatrix();
                    gl.glFlush();
                
               //    if(pngRecordEnabled){
              //          grabImage(drawable);
               //    }
                                   
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
                
             //   glu.gluPerspective(40.0,(double)x/(double)y,0.5,10.0);
             //  if(goThroughMode) {
              //  glu.gluPerspective(10.0,(double)width/(double)height,0.5,100000.0);
                glu.gluPerspective(10.0, (double)width/(double)height,0.5,100000.0);
             //   glu.gluPerspective(10.0, 2,0.5,100000.0);
             //  } else {
                //  gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
             //  }
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
                
            }
            
             /* copied from Tobi's ChipCanvas class, hack
     * Copy the frame buffer into the BufferedImage.  The data needs to
     * be flipped top to bottom because the origin is the lower left in
     * OpenGL, but is the upper right in Java's BufferedImage format.
     This method should be called inside the rendering display method.
     @param d the drawable we are drawing in.
     */
    void grabImage( GLAutoDrawable d ) {
        GL gl = d.getGL();
        int width = d.getWidth();
        int height = d.getHeight();
        
        // Allocate a buffer for the pixels
        ByteBuffer rgbData = BufferUtil.newByteBuffer(width * height * 3);
        
        // Set up the OpenGL state.
        gl.glReadBuffer(GL.GL_FRONT);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        
        // Read the pixels into the ByteBuffer
        gl.glReadPixels(0,
                0,
                width,
                height,
                GL.GL_RGB,
                GL.GL_UNSIGNED_BYTE,
                rgbData);
        
        // Allocate space for the converted pixels
        int[] pixelInts = new int[width * height];
        
        // Convert RGB bytes to ARGB ints with no transparency. Flip
        // imageOpenGL vertically by reading the rows of pixels in the byte
        // buffer in reverse - (0,0) is at bottom left in OpenGL.
        
        int p = width * height * 3; // Points to first byte (red) in each row.
        int q;                  	// Index into ByteBuffer
        int i = 0;                 // Index into target int[]
        int bytesPerRow = width*3; // Number of bytes in each row
        
        for (int row = height - 1; row >= 0; row--) {
            p = row * bytesPerRow;
            q = p;
            for (int col = 0; col < width; col++) {
                int iR = rgbData.get(q++);
                int iG = rgbData.get(q++);
                int iB = rgbData.get(q++);
                
                pixelInts[i++] = ( (0xFF000000)
                | ((iR & 0xFF) << 16)
                | ((iG & 0xFF) << 8)
                | (iB & 0xFF) );
            }
        }
        
        // Set the data for the BufferedImage
        if(image3DOpenGL==null || image3DOpenGL.getWidth()!=width || image3DOpenGL.getHeight()!=height) {
            image3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
        }
        image3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
    }
    
    
            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        a3DFrame.getContentPane().add(a3DCanvas);
        a3DFrame.pack();
        a3DFrame.setVisible(true);
    }
    
    
    
    
    
    
    
    /***********************************************************************************
     * // drawing on player window
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
        
        
        GL gl=drawable.getGL(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTrackerStereoBoard3Label.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            
            
            
            
            
//            if(showZones){
//                // draw door
//                gl.glColor3f(0,1,0);
//                drawBox(gl,door_xa,door_xb,door_ya,door_yb);
//            }
            
            
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
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.logDataEnabled",logDataEnabled);
        
        /*
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
         **/
    }
    
    
    public void setDecayTimeLimit(float decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.decayTimeLimit",decayTimeLimit);
    }
    public float getDecayTimeLimit() {
        return decayTimeLimit;
    }
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("PawTrackerStereoBoard3Label.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    
 
    
    public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.event_strength",event_strength);
    }
    public float getEvent_strength() {
        return event_strength;
    }
    
    
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.resetPawTracking",resetPawTracking);
        
        if(resetPawTracking){
            resetPawTracker();
        }
        
    }
    
      public boolean isResetClusters() {
        return resetClusters;
    }
    public void setResetClusters(boolean resetClusters) {
        this.resetClusters = resetClusters;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.resetClusters",resetClusters);
        
        if(resetClusters){
            resetClusterTrackers();
        }
        
    }
    
//    public boolean isRestart() {
//        return restart;
//    }
//    public void setRestart(boolean restart) {
//        this.restart = restart;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.restart",restart);
//
//    }
    
//    public boolean isValidateParameters() {
//        return validateParameters;
//    }
//    public void setValidateParameters(boolean validateParameters) {
//        this.validateParameters = validateParameters;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.validateParameters",validateParameters);
//        
//    }
    
    
//
//
//    public void setShowCorrectionGradient(boolean showCorrectionGradient){
//        this.showCorrectionGradient = showCorrectionGradient;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showCorrectionGradient",showCorrectionGradient);
//    }
//    public boolean getshowCorrectionGradient(){
//        return showCorrectionGradient;
//    }
//    public void setShowCorrectionMatrix(boolean showCorrectionMatrix){
//        this.showCorrectionMatrix = showCorrectionMatrix;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showCorrectionMatrix",showCorrectionMatrix);
//    }
//    public boolean getShowCorrectionMatrix(){
//        return showCorrectionMatrix;
//    }
//
//
//
//    
//    public void setShowSecondFilter(boolean showSecondFilter){
//        this.showSecondFilter = showSecondFilter;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showSecondFilter",showSecondFilter);
//    }
//    public boolean isShowSecondFilter(){
//        return showSecondFilter;
//    }
//    
    
    
    public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    
//
//    public void setShowAcc(boolean showAcc){
//        this.showAcc = showAcc;
//
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showAcc",showAcc);
//    }
//    public boolean isShowAcc(){
//        return showAcc;
//    }
    
    public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showOnlyAcc",showOnlyAcc);
    }
    public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
//    public void setShowDecay(boolean showDecay){
//        this.showDecay = showDecay;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showDecay",showDecay);
//    }
//    public boolean isShowDecay(){
//        return showDecay;
//    }
//
    
//    public void setUseFilter(boolean useFilter){
//        this.useFilter = useFilter;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.useFilter",useFilter);
//    }
//    public boolean isUseFilter(){
//        return useFilter;
//    }
    
   
    
    
    public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.decayOn",decayOn);
    }
    public boolean isDecayOn(){
        return decayOn;
    }
    
//    
//    public void setShowFrame(boolean showFrame){
//        this.showFrame = showFrame;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showFrame",showFrame);
//    }
//    public boolean isShowFrame(){
//        return showFrame;
//    }
    
    public void setShowCage(boolean showCage){
        this.showCage = showCage;
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showCage",showCage);
    }
    public boolean isShowCage(){
        return showCage;
    }
    
    public void setShowRetina(boolean showRetina){
        this.showRetina = showRetina;
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showRetina",showRetina);
    }
    public boolean isShowRetina(){
        return showRetina;
    }
    

    public void setShow2DWindow(boolean show2DWindow){
        this.show2DWindow = show2DWindow;

        getPrefs().putBoolean("PawTrackerStereoBoard3Label.show2DWindow",show2DWindow);
    }
    public boolean isShow2DWindow(){
        return show2DWindow;
    }
      public void setShow3DWindow(boolean show3DWindow){
        this.show3DWindow = show3DWindow;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.show3DWindow",show3DWindow);
    }
    public boolean isShow3DWindow(){
        return show3DWindow;
    }
    
//
//    public void setShowPlayer(boolean showPlayer){
//        this.showPlayer = showPlayer;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showPlayer",showPlayer);
//    }
//    public boolean isShowPlayer(){
//        return showPlayer;
//    }
//
            
//    
//    public void setShowScore(boolean showScore){
//        this.showScore = showScore;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showScore",showScore);
//    }
//    public boolean isShowScore(){
//        return showScore;
//    }
////
//    public void setShowRight(boolean showRight){
//        this.showRight = showRight;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showRight",showRight);
//    }
//    public boolean isShowRight(){
//        return showRight;
//    }
//
    
    
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
     public void setShowFingersRange(boolean showFingersRange){
        this.showFingersRange = showFingersRange;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showFingersRange",showFingersRange);
    }
    public boolean isShowFingersRange(){
        return showFingersRange;
    }
//    
//    public void setShowFingerTips(boolean showFingerTips){
//        this.showFingerTips = showFingerTips;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showFingerTips",showFingerTips);
//    }
//    public boolean isShowFingerTips(){
//        return showFingerTips;
//    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
//    public void setShowAll(boolean showAll){
//        this.showAll = showAll;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showAll",showAll);
//    }
//    public boolean isShowAll(){
//        return showAll;
//    }
//
//    public void setUseFastMatching(boolean useFastMatching){
//        this.useFastMatching = useFastMatching;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.useFastMatching",useFastMatching);
//    }
//    public boolean isUseFastMatching(){
//        return useFastMatching;
//    }
    
    public void setShowRLColors(boolean showRLColors){
        this.showRLColors = showRLColors;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showRLColors",showRLColors);
    }
    public boolean isShowRLColors(){
        return showRLColors;
    }
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
    
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        
        getPrefs().putInt("PawTrackerStereoBoard3Label.lowFilter_radius",lowFilter_radius);
    }
    
    public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        
        getPrefs().putInt("PawTrackerStereoBoard3Label.lowFilter_density",lowFilter_density);
    }
    
    public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.lowFilter_threshold",lowFilter_threshold);
    }
//    
//    public int getLowFilter_radius2() {
//        return lowFilter_radius2;
//    }
//    
//    public void setLowFilter_radius2(int lowFilter_radius2) {
//        this.lowFilter_radius2 = lowFilter_radius2;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3Label.lowFilter_radius2",lowFilter_radius2);
//    }
//    
//    public int getLowFilter_density2() {
//        return lowFilter_density2;
//    }
//    
//    public void setLowFilter_density2(int lowFilter_density2) {
//        this.lowFilter_density2 = lowFilter_density2;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3Label.lowFilter_density2",lowFilter_density2);
//    }
    
    
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.brightness",brightness);
    }
    
    
    
    
//    
//    public float getPlaneAngle() {
//        return planeAngle;
//    }
//    public void setPlaneAngle(float planeAngle) {
//        this.planeAngle = planeAngle;
//        
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.planeAngle",planeAngle);
//    }
   
    
//    
//    public float getPlatformAngle() {
//        return platformAngle;
//    }
//    public void setPlatformAngle(float platformAngle) {
//        this.platformAngle = platformAngle;
//        
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.platformAngle",platformAngle);
//    }
    
    
    
//    public void setAlpha(float alpha) {
//        this.alpha = alpha;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.alpha",alpha);
//    }
//    public float getAlpha() {
//        return alpha;
//    }
    public void setIntensity(float intensity) {
        this.intensity = intensity;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.intensity",intensity);
    }
    public float getIntensity() {
        return intensity;
    }
    
    
    
//    public void setDispAvgRange(int dispAvgRange) {
//        this.dispAvgRange = dispAvgRange;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3Label.dispAvgRange",dispAvgRange);
//    }
//    public int getDispAvgRange() {
//        return dispAvgRange;
//    }
    
    
    
    
    public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        
        getPrefs().putFloat("PawTrackerStereoBoard3Label.valueThreshold",valueThreshold);
    }
    public float getValueThreshold() {
        return valueThreshold;
    }
    
//    public void setAccValueThreshold(float accValueThreshold) {
//        this.accValueThreshold = accValueThreshold;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.accValueThreshold",accValueThreshold);
//    }
//    public float getAccValueThreshold() {
//        return accValueThreshold;
//    }
    
    
    
    public void setGrasp_max_elevation(int grasp_max_elevation) {
        this.grasp_max_elevation = grasp_max_elevation;
        compute3DZones();
        getPrefs().putInt("PawTrackerStereoBoard3Label.grasp_max_elevation",grasp_max_elevation);
    }
    public int getGrasp_max_elevation() {
        return grasp_max_elevation;
    }
          
    public void setMax_finger_clusters(int max_finger_clusters) {
        this.max_finger_clusters = max_finger_clusters;
        
        getPrefs().putInt("PawTrackerStereoBoard3Label.max_finger_clusters",max_finger_clusters);
    }
    public int getMax_finger_clusters() {
        return max_finger_clusters;
    }
    public void setCage_distance(float cage_distance) {
        this.cage_distance = cage_distance;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_distance",cage_distance);
    }
    public float getMax_distance() {
        return max_distance;
    }
      public void setMax_distance(float max_distance) {
        this.max_distance = max_distance;

        getPrefs().putFloat("PawTrackerStereoBoard3Label.max_distance",max_distance);
    }
    public float getCage_distance() {
        return cage_distance;
    }
     public float getRetina_tilt_angle() {
        return retina_tilt_angle;
    }
    public void setRetina_tilt_angle(float retina_tilt_angle) {
        this.retina_tilt_angle = retina_tilt_angle;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.retina_tilt_angle",retina_tilt_angle);
    }
    
    public void setRetina_height(float retina_height) {
        this.retina_height = retina_height;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.retina_height",retina_height);
    }
    public float getRetina_height() {
        return retina_height;
    }




    public void setCage_base_height(float cage_base_height) {
        this.cage_base_height = cage_base_height;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_base_height",cage_base_height);
    }
    public float getCage_base_height() {
        return cage_base_height;
    }
     public void setCage_door_offset(float cage_door_offset) {
        this.cage_door_offset = cage_door_offset;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_door_offset",cage_door_offset);
    }
    public float getCage_door_offset() {
        return cage_door_offset;
    }


    public void setCage_door_height(float cage_door_height) {
        this.cage_door_height = cage_door_height;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_door_height",cage_door_height);
    }
    public float getCage_door_height() {
        return cage_door_height;
    }
    
    public void setCage_height(float cage_height) {
        this.cage_height = cage_height;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_height",cage_height);
    }
    public float getCage_height() {
        return cage_height;
    }
    public void setCage_width(float cage_width) {
        this.cage_width = cage_width;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_width",cage_width);
    }
    public float getCage_width() {
        return cage_width;
    }
    public void setCage_platform_length(float cage_platform_length) {
        this.cage_platform_length = cage_platform_length;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_platform_length",cage_platform_length);
    }
    public float getCage_platform_length() {
        return cage_platform_length;
    }
    public void setCage_door_width(float cage_door_width) {
        this.cage_door_width = cage_door_width;
        compute3DZones();
        getPrefs().putFloat("PawTrackerStereoBoard3Label.cage_door_width",cage_door_width);
    }
    public float getCage_door_width() {
        return cage_door_width;
    }

//
//    public void setYLeftCorrection(int yLeftCorrection) {
//        this.yLeftCorrection = yLeftCorrection;
//
//        getPrefs().putInt("PawTrackerStereoBoard3Label.yLeftCorrection",yLeftCorrection);
//    }
//    public int getYLeftCorrection() {
//        return yLeftCorrection;
//    }
//    public void setYRightCorrection(int yRightCorrection) {
//        this.yRightCorrection = yRightCorrection;
//
//        getPrefs().putInt("PawTrackerStereoBoard3Label.yRightCorrection",yRightCorrection);
//    }
//    public int getYRightCorrection() {
//        return yRightCorrection;
//    }
//
//    public void setYCurveFactor(float yCurveFactor) {
//        this.yCurveFactor = yCurveFactor;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.yCurveFactor",yCurveFactor);
//    }
//    public float getYCurveFactor() {
//        return yCurveFactor;
//    }
    
    
//
//
//    public void setColorizeFactor(float colorizeFactor) {
//        this.colorizeFactor = colorizeFactor;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.colorizeFactor",colorizeFactor);
//    }
//    public float getColorizeFactor() {
//        return colorizeFactor;
//    }
//
//    public void setShadowFactor(float shadowFactor) {
//        this.shadowFactor = shadowFactor;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.shadowFactor",shadowFactor);
//    }
//    public float getShadowFactor() {
//        return shadowFactor;
//    }
//
//    public void setZFactor(int zFactor) {
//        this.zFactor = zFactor;
//
//        getPrefs().putInt("PawTrackerStereoBoard3Label.zFactor",zFactor);
//    }
//    public int getZFactor() {
//        return zFactor;
//    }
//
//    public void setValueMargin(float valueMargin) {
//        this.valueMargin = valueMargin;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.valueMargin",valueMargin);
//    }
//    public float getValueMargin() {
//        return valueMargin;
//    }
//
//    public void setCorrectLeftAngle(float correctLeftAngle) {
//        this.correctLeftAngle = correctLeftAngle;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.correctLeftAngle",correctLeftAngle);
//    }
//    public float getCorrectLeftAngle() {
//        return correctLeftAngle;
//    }
//
//    public void setCorrectRightAngle(float correctRightAngle) {
//        this.correctRightAngle = correctRightAngle;
//
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.correctRightAngle",correctRightAngle);
//    }
//    public float getCorrectRightAngle() {
//        return correctRightAngle;
//    }
//
    
    
    
//
//    public void setColorizePeriod(int colorizePeriod) {
//        this.colorizePeriod = colorizePeriod;
//
//        getPrefs().putInt("PawTrackerStereoBoard3Label.colorizePeriod",colorizePeriod);
//    }
//    public int getColorizePeriod() {
//        return colorizePeriod;
//    }
//
//
//    public void setHighlightDecay(boolean highlightDecay){
//        this.highlightDecay = highlightDecay;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.highlightDecay",highlightDecay);
//    }
//    public boolean isHighlightDecay(){
//        return highlightDecay;
//    }
//
    
//    public void setShowZColor(boolean showZColor){
//        this.showZColor = showZColor;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showZColor",showZColor);
//    }
//    public boolean isShowZColor(){
//        return showZColor;
//    }
//
//    public void setShowYColor(boolean showYColor){
//        this.showYColor = showYColor;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showYColor",showYColor);
//    }
//    public boolean isShowYColor(){
//        return showYColor;
//    }
//
//    public void setShowXColor(boolean showXColor){
//        this.showXColor = showXColor;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showXColor",showXColor);
//    }
//    public boolean isShowXColor(){
//        return showXColor;
//    }
//
    
    public void setTrackFingers(boolean trackFingers){
        this.trackFingers = trackFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.trackFingers",trackFingers);
    }
    public boolean isTrackFingers(){
        return trackFingers;
    }
    
//    public void setShowShadows(boolean showShadows){
//        this.showShadows = showShadows;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showShadows",showShadows);
//    }
//    public boolean isShowShadows(){
//        return showShadows;
//    }
//    public void setShowCorner(boolean showCorner){
//        this.showCorner = showCorner;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.showCorner",showCorner);
//    }
//    public boolean isShowCorner(){
//        return showCorner;
//    }
    
//    public void setCorrectY(boolean correctY){
//        this.correctY = correctY;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.correctY",correctY);
//    }
//    public boolean isCorrectY(){
//        return correctY;
//    }
//
    public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
        
        getPrefs().putInt("PawTrackerStereoBoard3Label.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }
//    
//    public void setDisparity_range(int disparity_range) {
//        this.disparity_range = disparity_range;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3Label.disparity_range",disparity_range);
//    }
//    public int getDisparity_range() {
//        return disparity_range;
//    }
    
//    public void setNotCrossing(boolean notCrossing){
//        this.notCrossing = notCrossing;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.notCrossing",notCrossing);
//    }
//    public boolean isNotCrossing(){
//        return notCrossing;
//    }
    
    public float getFinger_mix() {
        return finger_mix;
    }
    
    public void setFinger_mix(float finger_mix) {
        this.finger_mix = finger_mix;
        getPrefs().putFloat("PawTrackerStereoBoard3Label.finger_mix",finger_mix);
    }
    
    
    public float getExpansion_mix() {
        return expansion_mix;
    }
    
    public void setExpansion_mix(float expansion_mix) {
        this.expansion_mix = expansion_mix;
        getPrefs().putFloat("PawTrackerStereoBoard3Label.expansion_mix",expansion_mix);
    }
    
//    public float getPlane_tracker_mix() {
//        return plane_tracker_mix;
//    }
//    
//    public void setPlane_tracker_mix(float plane_tracker_mix) {
//        this.plane_tracker_mix = plane_tracker_mix;
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.plane_tracker_mix",plane_tracker_mix);
//    }
    
    
    public float getTrackerSubsamplingDistance() {
        return trackerSubsamplingDistance;
    }
    
    public void setTrackerSubsamplingDistance(float trackerSubsamplingDistance) {
        this.trackerSubsamplingDistance = trackerSubsamplingDistance;
        getPrefs().putFloat("PawTrackerStereoBoard3Label.trackerSubsamplingDistance",trackerSubsamplingDistance);
    }
    
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        getPrefs().putInt("PawTrackerStereoBoard3Label.finger_surround",finger_surround);
    }
    
      
    public int getTracker_lifeTime() {
        return tracker_lifeTime;
    }
    
    public void setTracker_lifeTime(int tracker_lifeTime) {
        this.tracker_lifeTime = tracker_lifeTime;
        getPrefs().putInt("PawTrackerStereoBoard3Label.tracker_lifeTime",tracker_lifeTime);
    }
    
    public int getTracker_prelifeTime() {
        return tracker_prelifeTime;
    }
    
    public void setTracker_prelifeTime(int tracker_prelifeTime) {
        this.tracker_prelifeTime = tracker_prelifeTime;
        getPrefs().putInt("PawTrackerStereoBoard3Label.tracker_prelifeTime",tracker_prelifeTime);
    }
    
     public int getTracker_viable_nb_events() {
        return tracker_viable_nb_events;
    }
    
    public void setTracker_viable_nb_events(int tracker_viable_nb_events) {
        this.tracker_viable_nb_events = tracker_viable_nb_events;
        getPrefs().putInt("PawTrackerStereoBoard3Label.tracker_viable_nb_events",tracker_viable_nb_events);
    }
    
    
            
    public void setUseGroups(boolean useGroups){
        this.useGroups = useGroups;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.useGroups",useGroups);
    }
    public boolean isUseGroups(){
        return useGroups;
    }
    
//    public void setGoThroughMode(boolean goThroughMode){
//        this.goThroughMode = goThroughMode;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.goThroughMode",goThroughMode);
//    }
//    public boolean isGoThroughMode(){
//        return goThroughMode;
//    }
    
//    public void setUseCorrections(boolean useCorrections){
//        this.useCorrections = useCorrections;
//
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.useCorrections",useCorrections);
//    }
//    public boolean isUseCorrections(){
//        return useCorrections;
//    }
//
//     public float getFocal_length() {
//        return focal_length;
//    }
//
//    public void setFocal_length(float focal_length) {
//        this.focal_length = focal_length;
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.focal_length",focal_length);
//        compute3DParameters();
//    }
//
    
 
//
//    public void setRetinae_distance(float retinae_distance) {
//        this.retinae_distance = retinae_distance;
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.retinae_distance",retinae_distance);
//        compute3DParameters();
//    }
//     public float getRetinae_distance() {
//        return retinae_distance;
//    }
//
//    public void setPixel_size(float pixel_size) {
//        this.pixel_size = pixel_size;
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.pixel_size",pixel_size);
//        compute3DParameters();
//    }
//     public float getPixel_size() {
//        return pixel_size;
//    }
    
//    public void setRetina_angle(float retina_angle) {
//        this.retina_angle = retina_angle;
//        getPrefs().putFloat("PawTrackerStereoBoard3Label.retina_angle",retina_angle);
//        compute3DParameters();
//    }
//    public float getRetina_angle() {
//        return retina_angle;
//    }
    
    public void setRecordTrackerData(boolean recordTrackerData){
        this.recordTrackerData = recordTrackerData;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.recordTrackerData",recordTrackerData);
    }
    public boolean isRecordTrackerData(){
        return recordTrackerData;
    }
    
//    public void setTrackZPlane(boolean trackZPlane){
//        this.trackZPlane = trackZPlane;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.trackZPlane",trackZPlane);
//    }
//    public boolean isTrackZPlane(){
//        return trackZPlane;
//    }
    
    public void setDetectGrasp(boolean detectGrasp){
        this.detectGrasp = detectGrasp;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.detectGrasp",detectGrasp);
    }
    public boolean isDetectGrasp(){
        return detectGrasp;
    }
    
    public void setCheckNose(boolean checkNose){
        this.checkNose = checkNose;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3Label.checkNose",checkNose);
    }
    public boolean isCheckNose(){
        return checkNose;
    }

   public void setMax3DEvents(int max3DEvents) {
        this.max3DEvents = max3DEvents;
        compute3DParameters();
        getPrefs().putInt("PawTrackerStereoBoard3Label.max3DEvents",max3DEvents);
    }
    
    
    public int getMax3DEvents() {
        return max3DEvents;
    }
    
    public void setStart_z_displacement(int start_z_displacement) {
        this.start_z_displacement = start_z_displacement;
        getPrefs().putInt("PawTrackerStereoBoard3Label.start_z_displacement",start_z_displacement);
    }
    
    public int getStart_min_events() {
        return start_min_events;
    }
    
    public void setStart_min_events(int start_min_events) {
        this.start_min_events = start_min_events;
        getPrefs().putInt("PawTrackerStereoBoard3Label.start_min_events",start_min_events);
    }
    
      public int getGrasp_timelength_min() {
        return grasp_timelength_min;
    }
    
    public void setGrasp_timelength_min(int grasp_timelength_min) {
        this.grasp_timelength_min = grasp_timelength_min;
        getPrefs().putInt("PawTrackerStereoBoard3Label.grasp_timelength_min",grasp_timelength_min);
    }
  
    
    public void setRecordUpTo(int recordUpTo) {
        this.recordUpTo = recordUpTo;
        // no need to save from on session to the other
       // getPrefs().putInt("PawTrackerStereoBoard3Label.recordUpTo",recordUpTo);
    }
    public int getRecordUpTo() {
        return recordUpTo;
    }

    public void setAutoRecord(boolean autoRecord){
        this.autoRecord = autoRecord;

        getPrefs().putBoolean("PawTrackerStereoBoard3Label.autoRecord",autoRecord);
    }
    public boolean isAutoRecord(){
        return autoRecord;
    }

    public void setEvent3DLifeTime(float event3DLifeTime) {
        this.event3DLifeTime = event3DLifeTime;
        getPrefs().putFloat("PawTrackerStereoBoard3Label.event3DLifeTime",event3DLifeTime);

    }
    public float getEvent3DLifeTime() {
       return event3DLifeTime;
    }
//    public synchronized boolean isThrowAwayEvents() {
//        return throwAwayEvents;
//    }
//    
//    public synchronized void setThrowAwayEvents(boolean throwAwayEvents) {
//        this.throwAwayEvents = throwAwayEvents;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3Label.throwAwayEvents",throwAwayEvents);
//    }
     public int getzoom3D() {
        return zoom3D;
    }

    public void setzoom3D(int zoom3D) {
        this.zoom3D = zoom3D;
        compute3DParameters();
       getPrefs().putInt("PawTrackerStereoBoard3Label.zoom3D",zoom3D);
    }

    
}
