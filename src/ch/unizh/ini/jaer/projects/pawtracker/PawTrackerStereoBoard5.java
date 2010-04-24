/*
 * PawTrackerStereoBoard5.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * Data must be recorded via stereoboard
 * version 5 : track events on 2D Lines and match trackers
*
 * Paul Rogister, Created on September, 2008
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
public class PawTrackerStereoBoard5 extends EventFilter2D implements FrameAnnotater, Observer /*, AE3DPlayerInterface, AE3DRecorderInterface*/ /*, PreferenceChangeListener*/ {
    
    boolean firstRun = true;
    
    //debug
    int minrightZ = 3000;
    int minleftZ = 3000;
    
   
    // recorder /player variables
    
    int INITIAL_PACKET_SIZE = 1000;
    
    public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
    

    boolean dopause = false;
 
    
    // log data
    File logFile;
    BufferedWriter logWriter;
    volatile boolean logEnabled = false;
    
    volatile boolean pngRecordEnabled = false;
    int frameNumber = 0;
    
       
    // to display epopilar tracker behaviour over time
    Vector leftEpipolarFrames = new Vector();
    Vector rightEpipolarFrames = new Vector();
  
    int startLeftFrameTime = 0;
    int startRightFrameTime = 0;
    int frameTimeBin = 1000;
    int maxFrames = 500;
   
    
    public class EpipolarFrame{
        public float[] value;
        
        public EpipolarFrame( int size){
            value = new float[size];
        }
    }
    
    EpipolarFrame leftFrame;
    EpipolarFrame rightFrame;
    
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
            
            
            // get filename from current dat
       
    //   System.out.println("trying initlog: ");
       
            if(chip.getAeViewer().getAePlayer().getAEInputStream()==null) return;
           
            String filename = chip.getAeViewer().getAePlayer().getAEInputStream().getFile().getPath();//   .getName();
            // what kind of possibleerrors here?
     //       System.out.println("initlog: "+filename);
            int idat = filename.indexOf(AEDataFile.DATA_FILE_EXTENSION);
            
       //     int logTime = currentTime;
        //    if(logTime==0) 
             int logTime = chip.getAeViewer().getAePlayer().getAEInputStream().getCurrentStartTimestamp();           
            
            filename = new String(filename.substring(0,idat) + "-" + logTime + ".txt");
            
            
            
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
    
    protected final int NO_LINK = -1;
    protected final int DELETE_LINK = -2;

    // end of in stereoarrays
    
    // Global constant values
    
    protected int labelNumber = 0;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    
  //  private final int MAX_NB_FINGERS=5;
    
    
    
    private Point3D left_focal;
   
    private Point3D right_focal;
 
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
               
   float[] retina_rotation = new float[9];
   float[] world_rotation = new float[9];
                
    
    
   Point3D[][] leftRetinaCoordinates; 
   Point3D[][] rightRetinaCoordinates; 
    
   Vector<EventTracker>[] leftOnTrackers;
   Vector<EventTracker>[] leftOffTrackers;
   Vector<EventTracker>[] rightOnTrackers;
   Vector<EventTracker>[] rightOffTrackers;
   
   int maxTrackerLines = 0;
   
   Color leftOnColor;
   Color leftOffColor;
   Color rightOnColor;
   Color rightOffColor;
   
   
   int[][] nbEventTracker;
   
    
    Vector current3DEvents = new Vector(); // current 3D events, to diplay or consumme
  
    // debug
    Vector highlighted3DEvents = new Vector(); // current 3D events, to diplay or consumme
  
    
    // Parameters appearing in the GUI
    // to put in get/set
   
   
    private float timeDelay=getPrefs().getFloat("PawTrackerStereoBoard5.timeDelay",0);
    {setPropertyTooltip("timeDelay","timedelay between DVS right and DVS left, +100 means rigt is 100ms late, in ms");}
  
  
   // max number of 2D tracker per epipolar line
    private int maxNbEventTracker=getPrefs().getInt("PawTrackerStereoBoard5.maxNbEventTracker",10);
 
     private int buffer_size=getPrefs().getInt("PawTrackerStereoBoard5.buffer_size",10000);
 
    // take calibration values as input
    // change all this!
    // use final calibration for these :
    private float retina_tilt_angle=getPrefs().getFloat("PawTrackerStereoBoard5.retina_tilt_angle",-40.0f);
    {setPropertyTooltip("retina_tilt_angle","forward tilt angle of the retinae, in degrees");}
    private float retina_height=getPrefs().getFloat("PawTrackerStereoBoard5.retina_height",30.0f);
    {setPropertyTooltip("retina_height","height of retina lens, in mm");}
    
    // cage , to keep
    private float cage_distance=getPrefs().getFloat("PawTrackerStereoBoard5.cage_distance",100.0f);
    {setPropertyTooltip("cage_distance","distance between stereoboard center and cage door, in mm");}
    private float cage_door_height=getPrefs().getFloat("PawTrackerStereoBoard5.cage_door_height",30.0f);
    {setPropertyTooltip("cage_door_height","height of the cage door, in mm");}
    private float cage_height=getPrefs().getFloat("PawTrackerStereoBoard5.cage_height",400.0f);
    {setPropertyTooltip("cage_height","height of the cage, in mm");}
    private float cage_width=getPrefs().getFloat("PawTrackerStereoBoard5.cage_width",200.0f);
    {setPropertyTooltip("cage_width","width of the cage, in mm");}
    private float cage_platform_length=getPrefs().getFloat("PawTrackerStereoBoard5.cage_platform_length",30.0f);
    {setPropertyTooltip("cage_platform_length","length toward retina of cage's platform, in mm");}
    private float cage_door_width=getPrefs().getFloat("PawTrackerStereoBoard5.cage_door_width",10.0f);
    {setPropertyTooltip("cage_door_width","width of the cage's door, in mm");}
    // add get/set for these above
    
    
    

    private float focal_length=getPrefs().getFloat("PawTrackerStereoBoard5.focal_length",6.0f);
   {setPropertyTooltip("focal_length","focal length in mm");}
    private float retinae_distance=getPrefs().getFloat("PawTrackerStereoBoard5.retinae_distance",100.0f);
   {setPropertyTooltip("retinae_distance","distance between the two retinae in mm");}
    private float retina_angle=getPrefs().getFloat("PawTrackerStereoBoard5.retina_angle",10.0f);
   {setPropertyTooltip("retina_angle","angle of rotation of retina in degrees");}
  
     
    // keep, important :
    private float pixel_size=getPrefs().getFloat("PawTrackerStereoBoard5.pixel_size",0.04f);
   {setPropertyTooltip("pixel_size","pixel size of retina in mm");}
   
    
    
    private int max_finger_clusters=getPrefs().getInt("PawTrackerStereoBoard5.max_finger_clusters",10);
    private int grasp_max_elevation=getPrefs().getInt("PawTrackerStereoBoard5.grasp_max_elevation",15);
 
    
    // transparency
    private float alpha=getPrefs().getFloat("PawTrackerStereoBoard5.alpha",0.1f);
    private float intensity=getPrefs().getFloat("PawTrackerStereoBoard5.intensity",1);
    private float valueThreshold=getPrefs().getFloat("PawTrackerStereoBoard5.valueThreshold",0);
    
    
    private float shadowFactor=getPrefs().getFloat("PawTrackerStereoBoard5.shadowFactor",0.3f);
    private float colorizeFactor=getPrefs().getFloat("PawTrackerStereoBoard5.colorizeFactor",0.1f);
    private int colorizePeriod=getPrefs().getInt("PawTrackerStereoBoard5.colorizePeriod",183);
    
    
   // private float valueMargin=getPrefs().getFloat("PawTrackerStereoBoard5.valueMargin",0.3f);
    
    private int cube_size=getPrefs().getInt("PawTrackerStereoBoard5.cube_size",1);
    
   
   
    private int retinaSize=getPrefs().getInt("PawTrackerStereoBoard5.retinaSize",128);
    
    private int intensityZoom = getPrefs().getInt("PawTrackerStereoBoard5.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    
    private float brightness=getPrefs().getFloat("PawTrackerStereoBoard5.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
    
    
    private boolean showYColor = getPrefs().getBoolean("PawTrackerStereoBoard5.showYColor",false);
    private boolean showXColor = getPrefs().getBoolean("PawTrackerStereoBoard5.showXColor",false);
    private boolean showZColor = getPrefs().getBoolean("PawTrackerStereoBoard5.showZColor",false);
    private boolean showScore = getPrefs().getBoolean("PawTrackerStereoBoard5.showScore",false);
 
    
  //  private boolean highlightDecay = getPrefs().getBoolean("PawTrackerStereoBoard5.highlightDecay",false);
    
    private boolean showFingers = getPrefs().getBoolean("PawTrackerStereoBoard5.showFingers",true);
    private boolean showFingersRange = getPrefs().getBoolean("PawTrackerStereoBoard5.showFingersRange",false);
    
 //   private boolean showZones = getPrefs().getBoolean("PawTrackerStereoBoard5.showZones",true);
    private boolean showAll = getPrefs().getBoolean("PawTrackerStereoBoard5.showAll",true);
    // show intensity inside shape
    
 //   private boolean showAcc = getPrefs().getBoolean("PawTrackerStereoBoard5.showAcc",false);
 //   private boolean showOnlyAcc = getPrefs().getBoolean("PawTrackerStereoBoard5.showOnlyAcc",false);
 //   private boolean showDecay = getPrefs().getBoolean("PawTrackerStereoBoard5.showDecay",false);
    
    
 //   private boolean scaleAcc = getPrefs().getBoolean("PawTrackerStereoBoard5.scaleAcc",true);
    
    private boolean showEvenTrackers = getPrefs().getBoolean("PawTrackerStereoBoard5.showEvenTrackers",true);
    private boolean showVelocity = getPrefs().getBoolean("PawTrackerStereoBoard5.showVelocity",true);
   
    private boolean showCage = getPrefs().getBoolean("PawTrackerStereoBoard5.showCage",true);
    private boolean show2DWindow = getPrefs().getBoolean("PawTrackerStereoBoard5.show2DWindow",true);
    private boolean show3DWindow = getPrefs().getBoolean("PawTrackerStereoBoard5.show3DWindow",true);
    private boolean showRetina = getPrefs().getBoolean("PawTrackerStereoBoard5.showRetina",false);
  
    private boolean trackFingers = getPrefs().getBoolean("PawTrackerStereoBoard5.trackFingers",false);
 //   private boolean showRLColors = getPrefs().getBoolean("PawTrackerStereoBoard5.showRLColors",false);
    private boolean showAxes = getPrefs().getBoolean("PawTrackerStereoBoard5.showAxes",true);
        
 //   private boolean showRight = getPrefs().getBoolean("PawTrackerStereoBoard5.showRight",false);
    
    
    private boolean restart=getPrefs().getBoolean("PawTrackerStereoBoard5.restart",false);
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTrackerStereoBoard5.resetPawTracking",false);
    private boolean resetClusters=getPrefs().getBoolean("PawTrackerStereoBoard5.resetClusters",false);
      
 //   private float event_strength=getPrefs().getFloat("PawTrackerStereoBoard5.event_strength",2f);
    
 //   private float decayTimeLimit=getPrefs().getFloat("PawTrackerStereoBoard5.decayTimeLimit",10000);
 //   {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
 //   private boolean decayOn = getPrefs().getBoolean("PawTrackerStereoBoard5.decayOn",false);
 //   {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}
        
    
    private float paw_mix_on_evts=getPrefs().getFloat("PawTrackerStereoBoard5.paw_mix_on_evts",0.2f);
    private float paw_mix_off_evts=getPrefs().getFloat("PawTrackerStereoBoard5.paw_mix_off_evts",0.2f);
    private float finger_mix=getPrefs().getFloat("PawTrackerStereoBoard5.finger_mix",0.2f);
    private float eventTrackerMix=getPrefs().getFloat("PawTrackerStereoBoard5.eventTrackerMix",0.2f);
   
    
    
    private float x_finger_dist_min=getPrefs().getFloat("PawTrackerStereoBoard5.x_finger_dist_min",10f);
    private float y_finger_dist_min=getPrefs().getFloat("PawTrackerStereoBoard5.y_finger_dist_min",10f);
    private float z_finger_dist_min=getPrefs().getFloat("PawTrackerStereoBoard5.z_finger_dist_min",10f);
  
    
    private int y_monitor=getPrefs().getInt("PawTrackerStereoBoard5.y_monitor",10);
       
    
    private int paw_surround=getPrefs().getInt("PawTrackerStereoBoard5.paw_surround",100);
    private int finger_surround=getPrefs().getInt("PawTrackerStereoBoard5.finger_surround",10);
   
 //   private boolean useGroups = getPrefs().getBoolean("PawTrackerStereoBoard5.useGroups",false);
    
    private int tracker_lifeTime=getPrefs().getInt("PawTrackerStereoBoard5.tracker_lifeTime",10000);
    private int tracker_prelifeTime=getPrefs().getInt("PawTrackerStereoBoard5.tracker_prelifeTime",1000);
    private int tracker_viable_nb_events=getPrefs().getInt("PawTrackerStereoBoard5.tracker_viable_nb_events",100);
   
    private float velocity_mix=getPrefs().getFloat("PawTrackerStereoBoard5.velocity_mix",0.01f);
    private float velocityThreshold=getPrefs().getFloat("PawTrackerStereoBoard5.velocityThreshold",1.0f);
    private float velocityMin=getPrefs().getFloat("PawTrackerStereoBoard5.velocityMin",0.1f);
   
    
    private boolean useVelocity = getPrefs().getBoolean("PawTrackerStereoBoard5.useVelocity",false);
 
    
    
    private float expansion_mix=getPrefs().getFloat("PawTrackerStereoBoard5.expansion_mix",0.5f);
    private float trackerSubsamplingDistance=getPrefs().getFloat("PawTrackerStereoBoard5.trackerSubsamplingDistance",10);//cm?
    private boolean recordTrackerData = getPrefs().getBoolean("PawTrackerStereoBoard5.recordTrackerData",false);
    private boolean logDataEnabled = getPrefs().getBoolean("PawTrackerStereoBoard5.logDataEnabled",false);
 
    
    private float velocityFactor=getPrefs().getFloat("PawTrackerStereoBoard5.velocityFactor",1e2f);
  
  
    private int start_min_events=getPrefs().getInt("PawTrackerStereoBoard5.start_min_events",10000);
  
 
    private int recordUpTo=0; //getPrefs().getInt("PawTrackerStereoBoard5.recordUpTo",0);
 
  
    private int trackerLineWidth=getPrefs().getInt("PawTrackerStereoBoard5.trackerLineWidth",3);
    private int eventTrackerSize=getPrefs().getInt("PawTrackerStereoBoard5.eventTrackerSize",3);
 
     
  
     
     
    /** additional classes */
     public class Cage{
         Point3D p1;
         Point3D p2;
         Point3D p3;
         Point3D p4;
         Point3D p5;
         Point3D p6;
         Point3D p7;
         Point3D p8;
         Point3D p9;
         Point3D p10;
         Point3D p11;
         Point3D p12;
        
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
         Point3D p1;
         Point3D p2;
         Point3D p3;
         Point3D p4;
         Point3D p5;
         Point3D p6;
         Point3D p7;
         Point3D p8;
         Point3D rp1;
         Point3D rp2;
         Point3D rp3;
         Point3D rp4;
         Point3D rp5;
         Point3D rp6;
         Point3D rp7;
         Point3D rp8;
        
        
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
    
    
     /** Point3D : all data about a point in opengl 3D space */
    public class Point3D{
        float x;
        float y;
        float z;
        
        public Point3D(){
            x = 0;
            y = 0;
            z = 0;
        }
        
        public Point3D(float x, float y, float z){
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public Point3D minus( Point3D p){
            Point3D r = new Point3D();
            r.x = x - p.x; 
            r.y = y - p.y; 
            r.z = z - p.z; 
            return r;
        }
        public Point3D plus( Point3D p){
            Point3D r = new Point3D();
            r.x = x + p.x; 
            r.y = y + p.y; 
            r.z = z + p.z; 
            return r;
        }
        
        public void rotate( float[] rotationMatrix){
            
               x = x*rotationMatrix[0]+y*rotationMatrix[1]+z*rotationMatrix[2];
               y = x*rotationMatrix[3]+y*rotationMatrix[4]+z*rotationMatrix[5];
               z = x*rotationMatrix[6]+y*rotationMatrix[7]+z*rotationMatrix[8];
            
        }
        
        public Point3D rotateOnX( float yRotationCenter, float zRotationCenter, float angle){
              float yr = rotateYonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              float zr = rotateZonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              return new Point3D(x,yr,zr);
        }
        
        public Point3D rotateOnY( float xRotationCenter, float zRotationCenter, float angle){
              float xr = rotateXonY( x, z,  xRotationCenter,  zRotationCenter,  angle);
              float zr = rotateZonY( x, z,  xRotationCenter,  zRotationCenter,  angle);              
              return new Point3D(xr,y,zr);
        }
      
            
        
    }
    
    
    /** EventPoint : all data about a point in retina space */
    
    
   
    
    ///###################### Trackers #############################################
    
    private class PawCluster{
        int id = 0;
        int x=0;
        int y=0;
        int z = 0;
        int time = 0;
       
        int nbEvents = 0;
        
        //for ball tracker, hacked here
        float x_size = 0;
        float y_size = 0;
        float z_size = 0;
      //  boolean activated = false;
        int max_fingers = 5;
    
        
        Vector<FingerCluster> fingers = new Vector<FingerCluster>();
        
        public PawCluster(  ){
            //for ball tracker, hacked here
            x_size = 50; //finger_surround;
            y_size = 50;
            z_size = 50;
            
        }
        
        public PawCluster( int x, int y, int z, int time ){
          //  activated = true;
            this.time = time;
            
            id = idPawClusters++;
            this.x = x;
            this.y = y;
            this.z = z;
            
            //for ball tracker, hacked here
            x_size = 50;
            y_size = 50;
            z_size = 50;
            nbEvents = 1;
        }
        
        public void reset(){
            
         //   activated = false;
            //for ball tracker, hacked here
            x_size = 50;
            y_size = 50;
            z_size = 50;
            
            x = 0; //end tip
            y = 0;
            z = 0;
            nbEvents = 0;
            
            fingers = new Vector<FingerCluster>();
        }
        
        public void add( int x, int y, int z, float mix, float value, int time){
                      
            x_size = x_size*0.99f;
            y_size = y_size*0.99f;
            z_size = z_size*0.99f;
            float xd = Math.abs(this.x-x);
            if(xd>x_size){
               x_size = x_size*(1-expansion_mix) + xd*expansion_mix;
            } 
           
            float yd = Math.abs(this.y-y);
            if(yd>y_size){
               y_size = y_size*(1-expansion_mix) + yd*expansion_mix;
            } 
            
            float zd = Math.abs(this.z-z);
            if(zd>z_size){
               z_size = z_size*(1-expansion_mix) + zd*expansion_mix;
            } 
           
            
        //    mix = mix/100;

            if(mix>1) mix=1;
            if(mix<0) mix = 0;
         
            this.time = time;
            this.x = Math.round(x*mix + this.x*(1-mix));
            this.y = Math.round(y*mix + this.y*(1-mix));
            this.z = Math.round(z*mix + this.z*(1-mix));
            
  
            nbEvents++;
         //   System.out.println("-- nb events: "+nbEvents);
            
            
           // find fingers?
            // if events is in correct range
           if((xd>x_finger_dist_min)||(yd>y_finger_dist_min)||(zd>z_finger_dist_min)){
               FingerCluster fc = getNearestFingerClusters(x-this.x,y-this.y,z-this.z,finger_surround);
               // add event to small finger tracker
               // get nearest finger tracker
               if(fc!=null){
                   // minus center of paw cluster to get relative position
                   fc.add(x-this.x,y-this.y,z-this.z,finger_mix,time);
               } else if(fingers.size()<max_fingers){
                   fc = new FingerCluster(x-this.x,y-this.y,z-this.z,time);
                   fingers.add(fc);
               }
               // add to it
               
           }
            
        }
        
       
      private FingerCluster getNearestFingerClusters( int x, int y, int z, int surround ){
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
        
        
        
        
        
    }
    // end class PawCluster
    
      
    private class FingerCluster{
        int id = 0;
        int x = 0;
        int y = 0;
        int z = 0;
        int time = 0;
       
     //   int nbEvents = 0;
        
      
        
        public FingerCluster(  ){
           
            
        }
        
        public FingerCluster( int x, int y, int z, int time ){
          //  activated = true;
            this.time = time;
            
         //   id = idPawClusters++;
            this.x = x;
            this.y = y;
            this.z = z;
            
          
        }
        
        public void reset(){
            
        
            x = 0; //end tip
            y = 0;
            z = 0;
           
        }
        
        public void add( int x, int y, int z, float mix, int time){
                                 
        //    mix = mix/100;

            if(mix>1) mix=1;
            if(mix<0) mix = 0;
         
            this.time = time;
            this.x = Math.round(x*mix + this.x*(1-mix));
            this.y = Math.round(y*mix + this.y*(1-mix));
            this.z = Math.round(z*mix + this.z*(1-mix));
                                  
        }
            
        
    }
    // end class FingerCluster
    
    
     private class EventTracker{
         
          float x=0;
          float y=0;
          int time=0;
          int type=0;
          // velocity
          float vx=0;
          float vy=0;
          
          int nbEvents=0;
          int line=0;
          int side=0;
          float match=retinaSize;
          int index=0;
          EventTracker matchedTracker;
                  
          public EventTracker(){
              
          }
          
          public EventTracker( int x, int y, int time, int side, int type, int line){
              this.x = x;
              this.y = y;
              this.time = time;
              this.side = side;
              this.line = line;
              if(type==0){
                 this.type = -1; 
              } else {
                  this.type = 1;
              }
              nbEvents=0;
          }
         
          public void add( int x, int y, float mix, int time){
                                      
            if(mix>1) mix=1;
            if(mix<0) mix=0;
            float m1 = 1-mix;
           
            if(velocity_mix>1) velocity_mix=1;
            if(velocity_mix<0) velocity_mix=0;
            float vm1 = 1-velocity_mix;
            
            float oldx = this.x;
            float oldy = this.y;
            // for velocity computation
            
            
            this.x = x*mix + this.x*m1;
            this.y = y*mix + this.y*m1;
               
            
            int dt = time-this.time;//check if time=0?
            if(dt>0){
              this.vx = vx*velocity_mix + (this.x-oldx)/dt*vm1;
              this.vy = vy*velocity_mix + (this.y-oldy)/dt*vm1;
            }
            
            this.time = time;
            nbEvents++;
         

            if(nbEvents>tracker_viable_nb_events&&(!useVelocity||vx+vy>velocityMin)){
                // try stereomatch
                // tracker should know about line number, side and its type (ON/OFF)
                
                matchEventTracker();
                
            }
        }
          
        private void matchEventTracker(){
         
            // get access to same type
            Vector<EventTracker> matchingTrackers = getEventTrackersFor(opposite(side), type, line);

            if (useVelocity) {
                //compute minimum relative difference between two epipolar lines clusters
                float min = velocityThreshold;
                EventTracker mt1 = null;
                 for (EventTracker mt : matchingTrackers) {
                   float d = distanceBetween(mt.vx, mt.vy, vx, vy);
                   if (distanceBetween(mt.vx, mt.vy, vx, vy) < min) {
                         min = d;
                         mt1 = mt;        
                   }                       
                 }
                 if(mt1!=null){
                     doMatch(this,mt1);
                 }
                
            } else {

                for (EventTracker mt : matchingTrackers) {

                    if (mt.match > x) {

                        doMatch(this,mt);

  
                        // stop there unless needing to recompute other trackers after change
                        return;
                    }
                }
            }

        }
        
        private void doMatch(EventTracker et, EventTracker mt){
             // match
                        // clear previous match
                        if (mt.matchedTracker != null) {
                            mt.matchedTracker.match = retinaSize;
                        }
                        // need link to tracker ..
                        mt.matchedTracker = et;
                        et.matchedTracker = mt;

                        // set match
                        mt.match = et.x;
                        et.match = mt.x;

                        // create new 3D event
                        // triangulate x,y,z position from precomputed table
                        Point3D p = triangulatePoint(et.side, Math.round(et.x), Math.round(et.y), Math.round(mt.x), Math.round(mt.y));
                        Event3D newEvent = new Event3D(Math.round(p.x), Math.round(p.y), Math.round(p.z), et.nbEvents * et.type, et.time);

                        //to do : check on neighbours to score event

                        current3DEvents.add(newEvent);
                        new3DEvent(newEvent);
            
        }

     } //end class EventTracker
       
     Point3D triangulatePoint(int side1, int x1, int y1, int x2, int y2){
         Point3D p;
         if(side1==LEFT){
            p = triangulate(leftRetinaCoordinates[x1][y1],left_focal,rightRetinaCoordinates[x2][y2],right_focal);
         } else {
            p = triangulate(leftRetinaCoordinates[x2][y2],left_focal,rightRetinaCoordinates[x1][y1],right_focal);
        
         }
         // should access lookup table of precomputed triangulation
         // slower : triangulate now :
         
         return p;
                  
     }
          
      
    private class PlaneTracker{
        int id = 0;
   //     int x=0;
     //   int y=0;
        int z = 0;
        int z0 = 0;
     //   int time = 0;
        
        //for ball tracker, hacked here
        int x_size = 0;
        int y_size = 0;
      //  int z_size = 0;
      //  boolean activated = false;
        
        public PlaneTracker(  ){
            
            
        }
        
        public PlaneTracker( int z  ){
          
            id = nbPawClusters++;
          
            this.z = z;
            z0 = z;
           
        }
        
        public void reset(){
            
         
            z = z0;
        }
        
        public void add( int x, int y, int z, float mix){
            
            if(isInSearchSpace(searchSpace,x,y,z)){  
       
                float zsp = zUnRotated(x,y,z);
 
                if(zsp<this.z){                    
                    mix = mix/100;                    
                    if(mix>1) mix = 1;
                    if(mix<0) mix = 0;                    
                //    this.time = time;
                    
                    this.z = Math.round(zsp*mix + this.z*(1-mix));
                }
            }            
        }
    }
    // end class PlaneTracker
    
    
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // Global variables

 //   private boolean logDataEnabled=false;//previously used for ball tracker
    private PrintStream logStream=null;
    
    int currentTime = 0;
    

    protected float grayValue = 0.5f;
    protected int colorScale = 2;
    
    private int redBlueShown=0;
    private int method=0;
    private int display3DChoice=0;
    private int displaySign=0;
    
    private int testChoice=0;
    

    private boolean searchSpaceMode = false;
    private boolean clearSpaceMode = false;
    private boolean showDisparity = false;
    private boolean testDrawing = false;
    private boolean showLabels = false;
    
    
    boolean windowDeleted = true;
    
  
    
    
    private int nbClusters = 0; //number of fingers tracked, maybe put it somewhere else
    private int nbPawClusters = 1;//number of created trackers
    private int idPawClusters = 1;//id of created trackers
    
//    protected PawCluster[] fingers = new PawCluster[MAX_NB_FINGERS];
    protected Vector<PawCluster> paws = new Vector();
    
    protected PlaneTracker planeTracker;
    
    
    // a bit hacked, for subsampling record of tracker data
    int prev_tracker_x = 0;
    int prev_tracker_y = 0;
    int prev_tracker_z = 0;
    
    /** Creates a new instance of PawTracker */
    public PawTrackerStereoBoard5(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        
        
        //     System.out.println("build resetPawTracker4 "+trackerID);
        
        initFilter();
        
        //   System.out.println("---------->>>>>> filter trackedID: "+trackerID);
        //resetPawTracker();
        
      
        
        
        chip.addObserver(this);
        
        //   System.out.println("End build resetPawTrackerStereoBoard");
        
    }
    
    public void initFilter() {
        current3DEvents.clear();
        highlighted3DEvents.clear();
    }
    
    
    
    private void resetPawTracker(){
        
       // y_monitor = maxTrackerLines/5;
        //   doReset = false;
       
        //  System.out.println("reset PawTrackerStereoBoard5 reset");
       
    
       resetEventTrackers();
        
        
        if(!firstRun){
            String dateString=loggingFilenameDateFormat.format(new Date());
            System.out.print("reset PawTrackerStereoBoard5 "+dateString);
            current3DEvents.clear();
            highlighted3DEvents.clear();
            compute3DParameters();
            validateParameterChanges();
            resetClusterTrackers();
            System.out.println(" done");System.out.flush();
        }
        
       
        
        setResetPawTracking(false);//this should also update button in panel but doesn't'
       
        // System.out.println("End of resetPawTrackerStereoBoard");
    }
    
    
     private void resetClusterTrackers(){
        paws.clear(); // = new PawCluster[MAX_NB_FINGERS];
        nbClusters = 0;
        //   setResetPawTracking(false);//this should also update button in panel but doesn't'
        
        int z = (int)Math.round((cage_distance-0.1)*scaleFactor);
        planeTracker = new PlaneTracker(z);
        
         
     }
     
     private void resetEventTrackers(){
         maxTrackerLines = retinaSize/trackerLineWidth +1;
         System.out.println("resetEventTrackers nbLines "+maxTrackerLines);
         
         
          leftOnTrackers = new Vector[maxTrackerLines];
          leftOffTrackers = new Vector[maxTrackerLines];
          rightOnTrackers = new Vector[maxTrackerLines];
          rightOffTrackers = new Vector[maxTrackerLines];

          nbEventTracker = new int[4][maxTrackerLines];
          
          leftOnColor = Color.getHSBColor(.62f,1f,1f);
          leftOffColor = Color.getHSBColor(.7f,1f,1f);
          rightOnColor = Color.getHSBColor(0.0f,1f,1f);
          rightOffColor = Color.getHSBColor(.83f,1f,1f);
          
          for(int i=0;i<maxTrackerLines;i++){
              leftOnTrackers[i] = new Vector<EventTracker>();
              leftOffTrackers[i] = new Vector<EventTracker>();
              rightOnTrackers[i] = new Vector<EventTracker>();
              rightOffTrackers[i] = new Vector<EventTracker>();
              nbEventTracker[0][i] = 0;
              nbEventTracker[1][i] = 0;
              nbEventTracker[2][i] = 0;
              nbEventTracker[3][i] = 0;
          }   
         
     }
     
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
   
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){
        
       if(firstRun){
           firstRun = false;
           resetPawTracker();
            
       }
        
         // clear previous 3D events
        // or rotate as ring buffer
        //current3DEvents.clear();
        int csize = current3DEvents.size();
        if(csize>buffer_size){
            
            Vector toRemove = new Vector(current3DEvents.subList(0,Math.round(csize/10)));
            current3DEvents.removeAll(toRemove);
            
        }
        
    //    csize = highlighted3DEvents.size();
    //    if(csize>buffer_size){
            
    //        Vector toRemove = new Vector(highlighted3DEvents.subList(0,Math.round(csize/10)));
    //        highlighted3DEvents.removeAll(toRemove);
            
     //   }
        
        
        int n=ae.getSize();
        if(n==0) return;
        
       
        
      //  float step = event_strength / (colorScale + 1);
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
        currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
            // BinocularEvent be=(BinocularEvent)e;
            
            processEvent(e);
            
            
        }
        
      
        clearDeadTrackers(currentTime);
        
        
        printClusterData();
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }
        
        
    }
    
    public void printClusterData(){
         if(recordTrackerData){
                 
             
             if(recordUpTo!=0&&currentTime>recordUpTo*10000){
                 // stop logging, pause, ask for next recordupto?
                 recordTrackerData = !recordTrackerData;
                 
             } else {
             
                 // extract x,y,z
             int n = 0;
             for(PawCluster fc:paws){
                 n++;
                 if(fc.nbEvents>tracker_viable_nb_events){ // >=
                     //if((detectGrasp&&fc.isAGrasp)||!detectGrasp){
                         // if(!paws.isEmpty()) {
                         //     PawCluster fc = fingers.firstElement();
                         
                         // subsampling based on distance with previous record
                         if(distanceBetween(prev_tracker_x,prev_tracker_y,prev_tracker_z,fc.x,fc.y,fc.z)>trackerSubsamplingDistance*scaleFactor){
                             // update prev
                             prev_tracker_x = fc.x;
                             prev_tracker_y = fc.y;
                             prev_tracker_z = fc.z;
                             
                             float xsp = xUnRotated(fc.x,fc.y,fc.z);
                             float ysp = yUnRotated(fc.x,fc.y,fc.z);
                             float zsp = zUnRotated(fc.x,fc.y,fc.z);
                             
                             //rotation to correct?
                             float sizex = fc.x_size;
                             float sizey = fc.y_size;
                             float sizez = fc.z_size;//*2;
                             
                             //System.out.println(fc.x+" "+fc.y+" "+fc.z+" "+xsp+" "+ysp+" "+zsp+" "+sizex+" "+sizey+" "+sizez);
                             //System.out.println(currentTime+" "+xsp+" "+ysp+" "+zsp+" "+n);
                             String slog = new String(currentTime+" "+xsp+" "+ysp+" "+zsp+" "+fc.id+"\n");
                             logData(slog);
                         }
                    // }
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
        //firstRun= true;
        startLeftFrameTime = 0;
       startRightFrameTime = 0;
       leftEpipolarFrames.clear();
       rightEpipolarFrames.clear();
       
        if(!firstRun) resetEventTrackers();
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
        
        // if recording
       
        if(logDataEnabled){
            doLog();
        }
        
       
        
        if(pngRecordEnabled){
            
            writeMovieFrame();
        }
        
        
        
      
        if (show2DWindow&&insideIntensityCanvas!=null) insideIntensityCanvas.repaint();
        if (show3DWindow&&!windowDeleted) a3DCanvas.repaint();
        return in;
    }
    
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    
    // computing 3D parameters for resolution of 3D location of matched Point3Ds
    private synchronized void compute3DParameters(){
        
        leftRetinaCoordinates = new Point3D[retinaSize][retinaSize]; 
        rightRetinaCoordinates = new Point3D[retinaSize][retinaSize]; 
    
        
        
        // from pixel size, focal length, distance between retinas and angle of rotation of retina
        // we can determine x,y,z of focal point, and of all retina pixels
        // to have lighter computation when computing x,y,z of matched point
        
        // WE CHOOSE SCALE AS : 1 3D-pixel per retina pixel size
        scaleFactor = 1/pixel_size;
        
        loadCalibrationFromFile("calibstereo.txt");
       
         
        // to center retina on middle point
        int halfRetinaSize = Math.round(retinaSize/2); 
          
        // for left retina, by default 0,0,0 is location of left focal point
        // find focal point
       
        left_focal = new Point3D(0,0,0);
       
        //update real 3D coordinat of all pixels
        // replace both  by stereopair.updateRealCoordinates(rd,ld=0,retina_angle)
        
        for (int i=0; i<leftRetinaCoordinates.length; i++){
            for (int j=0; j<leftRetinaCoordinates[i].length; j++){
                leftRetinaCoordinates[i][j] = new Point3D();
                leftRetinaCoordinates[i][j].x = i-halfRetinaSize;
                leftRetinaCoordinates[i][j].y = j-halfRetinaSize;
                leftRetinaCoordinates[i][j].z = fc_left_dist;
                
                // rotate
                leftRetinaCoordinates[i][j].rotate(world_rotation);
               
               leftRetinaCoordinates[i][j].z *= -1;
            }
        }
      
        
       
        right_focal = new Point3D(-retina_translation[0],-retina_translation[1],-retina_translation[2]);
 //       System.out.println("\nright_focal "+right_focal.x+" "+right_focal.y+" "+right_focal.z);
        right_focal.rotate(retina_rotation);
 //       System.out.println("retina rotation\n"+retina_rotation[0]+" "+retina_rotation[1]+" "+retina_rotation[2]);
 //     System.out.println(retina_rotation[3]+" "+retina_rotation[4]+" "+retina_rotation[5]);
 //     System.out.println(retina_rotation[6]+" "+retina_rotation[7]+" "+retina_rotation[8]);
     
  //        System.out.println("right_focal after retina "+right_focal.x+" "+right_focal.y+" "+right_focal.z);
     
        right_focal.rotate(world_rotation); 
        
 //          System.out.println("world rotation\n"+retina_rotation[0]+" "+retina_rotation[1]+" "+retina_rotation[2]);
 //     System.out.println(retina_rotation[3]+" "+retina_rotation[4]+" "+retina_rotation[5]);
 //     System.out.println(retina_rotation[6]+" "+retina_rotation[7]+" "+retina_rotation[8]);    
  //        System.out.println("right_focal after world "+right_focal.x+" "+right_focal.y+" "+right_focal.z);
     
       right_focal.z *= -1;
        
        
         //update real 3D coordinat of all pixels
        
        for (int i=0; i<rightRetinaCoordinates.length; i++){
            for (int j=0; j<rightRetinaCoordinates[i].length; j++){
                rightRetinaCoordinates[i][j] = new Point3D();
                rightRetinaCoordinates[i][j].x = i-halfRetinaSize-retina_translation[0];
                rightRetinaCoordinates[i][j].y = j-halfRetinaSize-retina_translation[1];
                rightRetinaCoordinates[i][j].z = fc_right_dist-retina_translation[2];
                
                if(i==0&&j==0)System.out.println("\n 1. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);
 
                rightRetinaCoordinates[i][j].rotate(retina_rotation);
                if(i==0&&j==0)System.out.println("2. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);
 
                rightRetinaCoordinates[i][j].rotate(world_rotation);
           
                rightRetinaCoordinates[i][j].z *= -1;
                
                if(i==0&&j==0)System.out.println("3. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);
 
               
            }
        }
        
        
        
        // compute cage's position
        int x_mid = Math.round(left_focal.x+(right_focal.x-left_focal.x)/2);
        int z = Math.round(cage_distance*scaleFactor);
        int base_height = Math.round(retina_height*scaleFactor - halfRetinaSize);
        cage = new Cage();
        cage.p1 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(-base_height),z);
        cage.p2 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(-base_height),z);
        cage.p3 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p4 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p5 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p6 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p7 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p8 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p9 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p10 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p11 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
        cage.p12 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
        cage.tilt(retina_tilt_angle);
        
        //
        searchSpace = new Zone();
        int z2 = Math.round((cage_distance-cage_platform_length)*scaleFactor);
        int z3 = Math.round((cage_distance+5)*scaleFactor);
        searchSpace.p1 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z2);
        searchSpace.p2 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z2);
        searchSpace.p3 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z2);
        searchSpace.p4 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z2);
        searchSpace.p5 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        searchSpace.p6 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        searchSpace.p7 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        searchSpace.p8 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
  
        
        searchSpace.tilt(retina_tilt_angle);
        
          //
        startSpace = new Zone();
        int z4 = Math.round((cage_distance-2)*scaleFactor);

        startSpace.p1 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        startSpace.p2 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        startSpace.p3 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        startSpace.p4 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        startSpace.p5 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        startSpace.p6 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        startSpace.p7 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        startSpace.p8 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);

        startSpace.tilt(retina_tilt_angle);

        
          //
        endSpace = new Zone();
      
        z4 = Math.round((cage_distance-5)*scaleFactor);
        endSpace.p1 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        endSpace.p2 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z4);
        endSpace.p3 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        endSpace.p4 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z4);
        endSpace.p5 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        endSpace.p6 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height)+1,z3);
        endSpace.p7 = new Point3D(x_mid-Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
        endSpace.p8 = new Point3D(x_mid+Math.round(cage_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height)*scaleFactor-base_height),z3);
  
        
        endSpace.tilt(retina_tilt_angle);
        
        
        noseSpace = new Zone();
      
        z4 = Math.round((cage_distance-3)*scaleFactor);
        noseSpace.p1 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z4);
        noseSpace.p2 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z4);
        noseSpace.p3 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z4);
        noseSpace.p4 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z4);
        noseSpace.p5 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z3);
        noseSpace.p6 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((cage_door_height+10)*scaleFactor-base_height)+1,z3);
        noseSpace.p7 = new Point3D(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z3);
        noseSpace.p8 = new Point3D(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round((grasp_max_elevation+cage_door_height+5)*scaleFactor-base_height),z3);
  
        
        noseSpace.tilt(retina_tilt_angle);
        
      //  if(correctEpipolar) computeEpipolarDistance();
        
        
    } //end compute3DParameters
    
     
      
    // load pixel -> voxels correpondances from file and put them all into VoxelTable
    private void loadCalibrationFromFile(String filename ) {
        try {
            //use buffering, reading one line at a time
            //FileReader always assumes default encoding is OK!
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null; //not declared within while loop
                
                int x = 0;
                int y = 0;
                float[] data = new float[23];
                int d=0;
                while((line = input.readLine()) != null) {
                    String[] result = line.split("\\s");
                       
                    for (int i = 1; i < result.length; i++) {
                        // store variables
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
        
        current3DEvents.clear();
        highlighted3DEvents.clear();
        // reset voxel array
      
        
       
    }
   
    
    
    float[] generateColor(float i, int max){
        float[] res = new float[3];
        
        // color start blue
        // color end red
        res[0] = (1.0f * i/max);
        res[1] = 0;
        res[2] = 1 * (1 - (i/max));
        
        return res;
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
    
    // new  event function
    void new3DEvent( Event3D ep ){
        
       // System.out.println("new event x:"+ep.x+" y:"+ep.y+" v:"+ep.accValue+" t:"+ep.updateTime);
        
        if(trackFingers){
             addToTracker(ep);
             
             
           /*  if(recordTrackerData){
                 
                 // extract x,y,z
                 if(!fingers.isEmpty()) {
                     PawCluster fc = fingers.firstElement();
                     
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
   
     // finger cluster functions
    void addToTracker( Event3D ep ){
        addToTracker(ep.x0,ep.y0,ep.z0,ep.value,ep.timestamp);
    }
         // cluster tracker functions
  
    // cluster functions
    void addToTracker( int x, int y, int z, float value, int time ){
        
        
        
        /**
         * check additional constraints to be tracked:
         *    find if end node of skeletton in range
         * find closest tracker
         * if none, create new tracker
         *
         * call clusterTracker.add
         *     which mix previous position with current eventPoint position
         *
         *
         */
        
       // if(isInSearchSpace(searchSpace,x,y,z)){
          //  if(!checkNose||!isInSearchSpace(noseSpace,x,y,z)){
                // find nearest
               
                Vector<PawCluster>  fcv = getNearestPawClusters(x,y,z,paw_surround,method);
                //  if(fc==null){
                if(fcv.isEmpty()){
                    if(nbClusters<max_finger_clusters){
                        
                        PawCluster fc = new PawCluster(x,y,z,time);
                        paws.add(fc);
                        //       System.out.println(currentTime+" create cluster at: ["+ep.getX0(method)+","
                        //             +ep.getY0(method)+","+ep.getZ0(method)+"] with updateTime:"+ep.updateTime);
                        
                        
                       
                        nbClusters++;
                        
                        
                        
                    }// else {
                    //    System.out.println(currentTime+" cannot create new tracker: nbClusters="+nbClusters);
                    //  }
                } else {
                    //fc.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),finger_mix,ep.updateTime);
                    PawCluster fc = fcv.firstElement();
                    int prev_x = fc.x;
                    int prev_y = fc.y;
                    int prev_z = fc.z;
                    float fmix = paw_mix_on_evts;
                    if(value<0){
                        fmix = paw_mix_off_evts;
                    }
                    fc.add(x,y,z,fmix,value,time);
                    
                   
                    //fcv.remove(fc);
                    // push close neighbouring clusters away
                    //int surroundSq = paw_surround*paw_surround+16;
                    //for(PawCluster fa:fcv){
                    // pushCloseCluster(fa,fc,surroundSq);
                    //}
                    
                    // rebounce on too close neighbouring clusters
                    fcv = getNearestPawClusters(fc.x,fc.y,fc.z,paw_surround);
                    if(fcv.size()>1){
                        // recursive
                        fcv.remove(fc);
                        rebounceOnCloseClusters(prev_x,prev_y,prev_z,fc,fcv.firstElement(),1);
                    }
                    
                }
                
                
           // }
       // }
    }
    
    // recursive
    private void  pushClusters( EventTracker et, EventTracker obstacle, Vector<EventTracker> trackers, float distance ){
        // move obstacle cluster if too close
     //    System.out.println("start pushClusters "+et.index+" ("+et.x+","+et.y+")("+obstacle.x+","+obstacle.y+")");
               
         if(distance<1) return; //anti stack overflow and gradient push
         
         float dx = obstacle.x-et.x;
       
         if(Math.abs(dx)<distance){
             // push
             obstacle.x += dx;
             if(obstacle.x<0) { 
                 obstacle.x=0;
                  return;
             }
            
             if(obstacle.x>=retinaSize) {
                 obstacle.x=retinaSize-1;
                 return;
             }
             
             if (dx>0){
                 // continue push toward right
                 int i = obstacle.index+1;
             //    System.out.println("pushClusters "+obstacle.index+" ("+obstacle.x+","+obstacle.y+"), next "+i+" dx:"+dx);
                 if(i>=0&&i<trackers.size()){
                     EventTracker next = trackers.get(i);
                     pushClusters(obstacle,next,trackers,distance*0.9f);
                 }
             } else {
                 // continue push toward left
                 int i = obstacle.index-1;
               //    System.out.println("pushClusters "+obstacle.index+" , next "+i+" dx:"+dx);
                
                 if(i>=0&&i<trackers.size()){
                     EventTracker next = trackers.get(i);
                     pushClusters(obstacle,next,trackers,distance*0.9f);
                 }
             }
             
         }
        
    }
  
    
    private void  rebounceOnCloseClusters( EventTracker et, EventTracker obstacle){
        
        float size = eventTrackerSize*2;
        float surroundSq = size*size+16;
       
        // compute dist ob-fc 
        float dx = obstacle.x-et.x;
        float dy = obstacle.y-et.y;
      
        float  dist = dx*dx + dy*dy;
        if(dist>surroundSq) return;
        
        float  distx = dx*dx;
        float  disty = dy*dy;
       
        float px = (distx/surroundSq)*eventTrackerSize;
        float py = (disty/surroundSq)*eventTrackerSize;            
        
        if(et.x-obstacle.x>0){
            obstacle.x += px;
        } else {
            obstacle.x -= px;
        }
         if(et.y-obstacle.y>0){
            obstacle.y += py;
        } else {
            obstacle.y -= py;
        }
        
      
        
        

    }
         
         
    private void rebounceOnCloseClusters( int x, int y, int z, PawCluster fc, PawCluster obstacle, int n ){
        //
        if(n>10) return; //safety on recusrivity
               
        float surroundSq = paw_surround*paw_surround+16;
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
        
        float px = (distx/surroundSq)*paw_surround;
        float py = (disty/surroundSq)*paw_surround;
        float pz = (distz/surroundSq)*paw_surround;
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
        
        
        Vector<PawCluster> fcv = getNearestPawClusters(fc.x,fc.y,fc.z,paw_surround);
        if(fcv.size()>1){
            // recursive
            fcv.remove(fc);
            rebounceOnCloseClusters(prev_x,prev_y,prev_z,fc,fcv.firstElement(),++n);
        }
        

    }
    
      private Vector<PawCluster> getNearestPawClusters( int x, int y, int z, int surround, int method ){
          
         return  getNearestPawClusters(x,y,z,surround);
      }
    
     
      
      private Vector<PawCluster> getNearestPawClusters( int x, int y, int z, int surround ){
        float min_dist=Float.MAX_VALUE;
        Vector<PawCluster> closest=new Vector();
        // float currentDistance=0;
        int surroundSq = surround*surround;
        float dist = min_dist;
        int dx = 0;
        int dy = 0;
        int dz  =0;
     //  StringBuffer sb = new StringBuffer();
        try {
        for(PawCluster fc:paws){
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
            System.out.println("Warning: getNearestPawClusters: ConcurrentModificationException caught");
        }
    //   if(closest==null){
    //   if(closest.isEmpty()){
    //       System.out.println(sb+" paws.size:"+paws.size());           
   //     }
        
        return closest;
    }
   
      synchronized private void clearDeadTrackers(int time){
          clearDeadPawTrackers(time);
          
          for (int i=0;i<maxTrackerLines;i++){
              clearDeadEventTrackers(time,leftOnTrackers[i],0,i);
              clearDeadEventTrackers(time,leftOffTrackers[i],1,i);
              clearDeadEventTrackers(time,rightOnTrackers[i],2,i);
              clearDeadEventTrackers(time,rightOffTrackers[i],3,i);
          }
          
      }
    
   synchronized private void clearDeadPawTrackers(int time){
       Vector<PawCluster> toRemove = new Vector();
        for(PawCluster fc:paws){
           // if(fc!=null){
               if(fc.nbEvents<tracker_viable_nb_events){
                   if(time-fc.time>tracker_prelifeTime){
                       toRemove.add(fc);
                       nbClusters--;
                   }
               } else {
                   if(time-fc.time>tracker_lifeTime){
                       toRemove.add(fc);
                       nbClusters--;
                   }
               }

           // }
        }
     //  paws.removeAll(toRemove);
       for(PawCluster fc:toRemove){
           paws.remove(fc);
           fc=null;
          // System.out.println("clearDeadTrackers delete Tracker");
       }
        
     //  System.out.println("clearDeadTrackers "+nbClusters);
   }
      
    synchronized private void clearDeadEventTrackers(int time, Vector<EventTracker> tracker, int type, int line){
       Vector<EventTracker> toRemove = new Vector();
        for(EventTracker et:tracker){
           
                   if(time-et.time>tracker_lifeTime){
                       toRemove.add(et);
                       
                   }
               

        }
     //paws.removeAll(toRemove);
       for(EventTracker fc:toRemove){
           tracker.remove(fc);
           nbEventTracker[type][line]--;
           fc=null;
          // System.out.println("clearDeadTrackers delete Tracker");
       }
        
     //  System.out.println("clearDeadTrackers "+nbClusters);
   }
  
    
   
    int opposite( int leftOrRight ){
        
         if(leftOrRight==RIGHT){
             return LEFT;
         } else {
             return RIGHT;
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
    
    
    
   
    
    
    // processing one event
    protected void processEvent(BinocularEvent e){
        
       
        
       int side = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
     
       int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
      
        //   System.out.println("processEvent leftOrRight:"+leftOrRight+" e.eye:"+e.eye+" type:"+e.getType());
       
       
       // add event to tracker depending on its ratina (left/right), polarity and position
        
       checkEpipolarFrame( side, e.timestamp);
       
       // find corresponding epipolarLine
       int trackerLine = getTrackerLine(e.y);
        
    //    if(trackerLine!=maxTrackerLines/5) return; //hack to debug
       
        if(trackerLine>=maxTrackerLines){
            System.out.println("trackerLine "+trackerLine+" > "+maxTrackerLines+" for "+e.y);
        
            return;
        }
            
       
       Vector<EventTracker> eventTrackers = getEventTrackersFor(side,type,trackerLine);
       
       
       addToNearestTracker(e,eventTrackers,side,type,trackerLine);
            
      
    }
    
    int getIndexFromSideAndType( int side, int type){
         if(side==LEFT){
            //  left
            if(type==0){
                //  left off
                return 1;
            } else {
                 //  left on
                 return 0;
              
            }
        } else {
            //  right
            if(type==0){
                //  right off
                 return 3;
                
            } else {
                 //  right on
                 return 2;
               
            }
        }
    }
    
    
    Vector<EventTracker> getEventTrackersFor(int side, int type, int line){
        
         if(side==LEFT){
            //  left
            if(type==0){
                //  left off
                return leftOffTrackers[line];
            } else {
                 //  left on
                 return leftOnTrackers[line];
              
            }
        } else {
            //  right
            if(type==0){
                //  right off
                 return rightOffTrackers[line];
                
            } else {
                 //  right on
                 return rightOnTrackers[line];
               
            }
        }
        
        
    }
    
    
    void addToNearestTracker(BinocularEvent e, Vector<EventTracker> eventTrackers, int side, int type, int line){
        // get nearest tracker
        Vector<EventTracker> closeTrackers = getNearestEventTracker(e.x,e.y,eventTrackers,eventTrackerSize);
        // add to it
       // if(closeTrackers!=null){
     //   if(line!=retinaSize/trackerLineWidth/2) {
            //System.out.println("line: "+line); 
    //    return; // hack to test (Paul)
    //    }
        int i = getIndexFromSideAndType(side,type);
        
        EventTracker eventTracker=null;
        
        if(closeTrackers.size()>0){
             eventTracker = closeTrackers.firstElement();
             eventTracker.add(e.x,e.y,eventTrackerMix,e.timestamp);
             
           
            
           // }
        } else {
            //create new tracker // check maximum here
            if(nbEventTracker[i][line]<maxNbEventTracker){
                nbEventTracker[i][line]++;
                eventTracker = new EventTracker(e.x,e.y,e.timestamp,side,type,line);

                // sort trackers, left most first
                // find proper index of trackers in this vector
                int index = 0;
                for (EventTracker et:eventTrackers){
                    if(e.x<et.x) break;
                    index++;
                }
                // sorting left-most when adding
                if(index<eventTrackers.size()){
                   eventTrackers.add(index,eventTracker);
                   eventTracker.index = index;
                } else {
                    eventTrackers.add(eventTracker);
                    eventTracker.index = eventTrackers.size()-1;
                }
                
            
          
            
                //! if we sort rackers in vector, then we should make sure the order is recomputed when
                // the trackers move, but to start with we consider it cannot happen that they cross
            }
        }
        
        if(eventTracker!=null){
          if(i<2){ //left
                addtoEpipolarFrame( LEFT,  Math.round(eventTracker.x), Math.round(eventTracker.y), i );
             } else {
                addtoEpipolarFrame( RIGHT,  Math.round(eventTracker.x), Math.round(eventTracker.y), i );
          
             }  
        }
        
        if(eventTracker!=null){
            float distance = eventTrackerSize+eventTrackerSize/2;
            closeTrackers = getNearestEventTracker(e.x,e.y,eventTrackers,distance);
             for( EventTracker ct:closeTrackers){                
                   
                   if(ct!=eventTracker) pushClusters(eventTracker,ct,eventTrackers,distance);
                  
            }
        }
           
    }
    
    int getTrackerLine( int y){
       
        return y/trackerLineWidth;
    }
    
    
    void checkEpipolarFrame( int side, int time){
         
         EpipolarFrame f;
         int startFrameTime;
         if(side==LEFT){
            
             startFrameTime = startLeftFrameTime;
         } else {
             
             startFrameTime = startRightFrameTime;
         }
         if(startFrameTime==0){
              //create initial frame
             if(side==LEFT){
                 startLeftFrameTime = time;
                 leftFrame = new EpipolarFrame(retinaSize);
                 leftEpipolarFrames.add(leftFrame);
             } else {
                 startRightFrameTime = time;
                 rightFrame = new EpipolarFrame(retinaSize);
                 rightEpipolarFrames.add(rightFrame);
             }
            
             
             
          } else if(time>startFrameTime+frameTimeBin){
              
              // create another new frame
              if(side==LEFT){
                 startLeftFrameTime = time;
                 leftFrame = new EpipolarFrame(retinaSize);
                 if(leftEpipolarFrames.size()>maxFrames){
                  leftEpipolarFrames.clear();
                 }
                 leftEpipolarFrames.add(leftFrame);
             } else {
                 startRightFrameTime = time;
                 rightFrame = new EpipolarFrame(retinaSize);
                 if(rightEpipolarFrames.size()>maxFrames){
                  rightEpipolarFrames.clear();
                 }
                 rightEpipolarFrames.add(rightFrame);
             }
            
            
             
          }
    }
    
    void addtoEpipolarFrame( int side , int x, int y, int type ){
         
         EpipolarFrame f;
         
         if(side==LEFT){
            
             f = leftFrame;
             
         } else {
            
             f = rightFrame;
             
         }
        
         if(f==null){
             if(side==LEFT){
            
                 System.out.println("addtoEpipolarFrame null frame for LEFT time: "+startLeftFrameTime);
             
             } else {

                 System.out.println("addtoEpipolarFrame null frame for RIGHT time: "+startRightFrameTime);

             }
             
         }
          if(y==y_monitor){
              //  frame.value[e.x] += step * (e.type - grayValue);
              f.value[x] = type+1;
              
          }     
        
    }
    
    private Vector<EventTracker> getNearestEventTracker(int x, int y, Vector<EventTracker> eventTrackers, float surround){
        float min_dist=Float.MAX_VALUE;
        Vector<EventTracker> closest=new Vector();
        float surroundSq = surround*surround;
        float dist = min_dist;
        float dx = 0;
        float dy = 0;
      
        try {
        for(EventTracker et:eventTrackers){
            if(et!=null){
               
                    dx = x-et.x;
                    dy = y-et.y;
                   
                    dist = dx*dx + dy*dy;
                
                    if(dist<=surroundSq){
                        if(dist<min_dist){
                            closest.add(0,et);
                            min_dist = dist;
                        } else {
                            closest.add(et);
                        }
                    }
               
            }
        }
        } catch (java.util.ConcurrentModificationException cme ){
            System.out.println("Warning: getNearestEventTracker: ConcurrentModificationException caught");
        }
   
       
          return closest;
      
    }
        
    
    
    public Point3D triangulate( Point3D p2, Point3D p1, Point3D p4, Point3D p3  ){
        // result
        int xt = 0;
        int yt = 0;
        int zt = 0;
        
        double mua = 0;
        double mub = 0;
        
        
        Point3D p13 = p1.minus(p3);
        Point3D p43 = p4.minus(p3);
        
        // should check if solution exists here
        Point3D p21 = p2.minus(p1);
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
        
        Point3D p5 = new Point3D(xt,yt,zt);
        
        return p5;
        
    }
     
    private synchronized float xUnRotated(int x, int y, int z){
        return x;
        
    }
    private synchronized float yUnRotated(int x, int y, int z){
         float y_rx = rotateYonX( y, z, 0, 0, -retina_tilt_angle);
         return y_rx;
        
    }
    private synchronized float zUnRotated(int x, int y, int z){
         float z_rx = rotateZonX( y, z, 0, 0, -retina_tilt_angle);
         return z_rx;
    }

    
    private synchronized boolean isInSearchSpace( Zone zone, int x, int y, int z){
        boolean res = true;
        
        // Paul: to modify since we are now in real 3D, no need for rotation
        
        float y_rx = rotateYonX( y, z, 0, 0, -retina_tilt_angle);
        float z_rx = rotateZonX( y, z, 0, 0, -retina_tilt_angle);
        
     //   int z_rx2 = rotateZonX( y_rx, z_rx, door_ya, -door_z, platformAngle);
    //    int y_rx2 = rotateYonX( y_rx, z_rx, door_ya, -door_z, platformAngle);
        
    //    int half = retinaSize/2;
        
        
        //   int x_ry = rotateXonY( x, z_rx, half, 180-middleAngle);
    //    int z_ry = rotateZonY( x, z_rx, half, 0, 180-middleAngle);
        
        
        
        // point must be in front of cage door and above cage's platform
        
        //  if(z_ry*zDirection<=-door_z){
        
        
        if(z_rx>zone.p8.z){
            res = false;
        }
        if(z_rx<zone.p1.z){
            res = false;
        }
        //if(y_rx<-(retina_height-cage_door_height)*scaleFactor + retinaSize/2){
        if(y_rx<zone.p1.y){
            res = false;
        }
        if(y_rx>zone.p3.y){
            res = false;
        }
       //   if(y>door_yc&&z>-door_z-5){
          //   res = false;
       //   }
        
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
    
    
  /** 
    protected float decayedValue( float value, int time ){
        float res=value;
        
        float dt = (float)time/decayTimeLimit;
        if(dt<0)dt = -dt;
        if(dt<1){
            res = value * dt;
        }
        return res;
    }
    **/
    
  
    
    
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
    boolean showNegative = false;
    
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
                
          //      if(e.getKeyCode()==KeyEvent.VK_F){
           //         showOnlyAcc=!showOnlyAcc;
           //         insideIntensityCanvas.display();
                    
           //     }
                
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
                
             
                // TO DO : highlight voxels in pixel line maybe
                
                insideIntensityCanvas.display();
                
            }
            public void mouseReleased(MouseEvent e){
                
                
            }
        });
        
        
        insideIntensityCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            
           
            
            
            
            
            float redFromLabel( int label ){
                if (label==0)
                    return 0f;
                
                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 0.5f;
                if (label==1)
                    return 0.25f;
                if (label==2)
                    return 0.5f;
                if (label==3)
                    return 0.75f;
                if (label==4)
                    return 1f;
                return 0;
            }
            float greenFromLabel( int label ){
                if (label==0)
                    return 0f;
                
                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 0.2f;
                if (label==1)
                    return 0f;
                if (label==2)
                    return 0.5f;
                if (label==3)
                    return 0.5f;
                if (label==4)
                    return 0f;
                return 0;
            }
            float blueFromLabel( int label ){
                if (label==0)
                    return 0f;
                
                while(label>4){
                    label-=4;
                }
                if (label==0)
                    return 1f;
                if (label==1)
                    return 0.75f;
                if (label==2)
                    return 0.75f;
                if (label==3)
                    return 0.5f;
                if (label==4)
                    return 0f;
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
               
                synchronized (leftEpipolarFrames) {
                    drawAllFrames(0,leftEpipolarFrames,gl);
                }
                synchronized (rightEpipolarFrames) {
                    drawAllFrames(retinaSize,rightEpipolarFrames,gl);
                
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
            
            
             private void drawAllFrames(int shift, Vector<EpipolarFrame> frames, GL gl) {
                
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);
                    
                    int y = 0;
                    for (EpipolarFrame f:frames) {
                        
                        for (int i = 0; i < f.value.length; i++) {
                            if(f.value[i]==1||f.value[i]==3){
                               gl.glColor3f(f.value[i], f.value[i], f.value[i]);
                               gl.glRectf(i+shift,y,i+1+shift,y+1);
                            } else {
                                if(f.value[i]==2||f.value[i]==4){
                                   gl.glColor3f(0,0, f.value[i]);
                                   gl.glRectf(i+shift,y,i+1+shift,y+1);
                                }
                            }
                            
                            
                            
                        }
                        y++;
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
                        // get final x,y for translation
                        dragOrigX = x;
                        dragOrigY = y;
                        
                    }
                    //   System.out.println(" x:"+x+" y:"+y);
                    // System.out.println("Left mousePressed tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);
                    
                }  else if ((e.getModifiersEx()&but2mask)==but2mask){
                    // get final x,y for depth translation
                
                    zdragOrigY = y;
                    //   System.out.println(" x:"+x+" y:"+y);
                  //   System.out.println("Middle mousePressed y:"+y+" zty:"+zty+" zOrigY:"+zOrigY);
                    
                }else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // get final x,y for rotation
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
                    // get final x,y for translation
                    
                    dragDestX = x;
                    dragDestY = y;
                    
                    leftDragged = true;
                    a3DCanvas.display();
                    
                    
                } else if ((e.getModifiersEx()&but2mask)==but2mask){
                    // get final x,y for translation
                    
                   
                    zdragDestY = y;
                    
                    middleDragged = true;
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
                     if(e.getKeyCode()==KeyEvent.VK_H){
                        
                        highlight = !highlight;
                        a3DCanvas.display();
                    }
                    
                     if(e.getKeyCode()==KeyEvent.VK_N){
                        
                        showNegative = !showNegative;
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
                   dopause = true; 
//                    if(chip.getAeViewer().aePlayer.isPaused()){
//                        chip.getAeViewer().aePlayer.resume();
//                    } else {
//                        chip.getAeViewer().aePlayer.pause();
//                    }
                    
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
                
                 if(e.getKeyCode()==KeyEvent.VK_M){
                   showScore = !showScore;
                   a3DCanvas.display();
                }
                
                
                
                
            }
        });
        
        a3DCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            
            
//            private void draw3DDisparityPacket(GL gl, AEPacket3D packet){
//                
//                // for all events in packet, 
//                // display
//                int n=packet.getNumEvents();
//                float b = 0.1f*brightness;
//                float fx = 0;
//                float fy = 0;
//                float fz = 0;
//                int x = 0;
//                int y = 0;
//                int z = 0;
//                for(int i=0;i<n;i++){
//                    float f = packet.getValues()[i];
//                    x = packet.getCoordinates3D_x()[i];
//                    y  = packet.getCoordinates3D_y()[i];
//                    z =  packet.getCoordinates3D_z()[i];
//                    if(showXColor){
//                        fx = colorizeFactor*(x%colorizePeriod);
//                    } else {
//                        fx = f;
//                    }
//                    if(showYColor){
//                        fy = colorizeFactor*(y%colorizePeriod);
//                    } else {
//                        fy = f;
//                    }
//                    if(showZColor){
//                        fz = colorizeFactor*((retinaSize-z)%colorizePeriod);
//                    } else {
//                        fz = f;
//                    }
//                    shadowCube(gl, x, y, z*zFactor,
//                            cube_size, fz+b, fy+b, fx+b, alpha, shadowFactor);
//             //       shadowCube(gl, x, y, z*zFactor,
//             //               cube_size, 1, 1, 1, 1, 0);
//                    
//
//                } // end for
//                
//                packetDisplayed = true;
//                
//            }

         
         
             /** draw points in 3D from the two retina arrays and the information about matched disparities
             **/
            
            synchronized private void drawCurrent3DEvents( GL gl, Vector active3DEvents, float size ){
               
                
                float fx = 0;
                float fy = 0;
                float fz = 0;
               
                
               //     System.out.println("drawCurrent3DEvents active3DEvents.size() "+active3DEvents.size());
                //     System.out.println("drawCurrent3DEvents at "+leadTime);
                
             //   int half = retinaSize/2;
                
                for(int i=0;i<active3DEvents.size();i++){
                    Event3D ev = (Event3D)active3DEvents.get(i);
                    
                    float[] color; 
                    float f = ev.value;
                    fy = f;
                    fx = f;
                    fz = f;
                    
                    
                    if(colorizePeriod<1)colorizePeriod=1;
                    
                    if (showXColor) {
                       // float vx = ev.x0 * pixel_size / voxel_size - voxels_x0 + (nb_x_voxels * voxel_size / 2);
                        //   System.out.println("1. "+ev.x0+" vx: "+vx);
                        //    vx = ev.x0*pixel_size;
                        //   System.out.println("2. "+ev.x0+" vx: "+vx);
                        //    vx = ev.x0*pixel_size/voxel_size;
                        //   System.out.println("3. "+ev.x0+" vx: "+vx);
                        color = generateColor(ev.x0, colorizePeriod);
                        fy = color[0];
                        fx = color[1];
                        fz = color[2];
                    } 
                    if (showYColor) {
                      //  float vy = ev.y0 * pixel_size / voxel_size - voxels_y0 + (nb_y_voxels * voxel_size / 2);
                        color = generateColor(ev.y0, colorizePeriod);
                        fy = color[0];
                        fx = color[1];
                        fz = color[2];
                    }
                    if (showZColor) {
                      //  float vz = ev.z0 * pixel_size / voxel_size - voxels_z0 + (nb_z_voxels * voxel_size / 2);
                        color = generateColor(ev.z0, colorizePeriod);
                        fy = color[0];
                        fx = color[1];
                        fz = color[2];
                    } else {
                        fz = f;
                        if (f <= 0 && showNegative) {
                            fz = -f;
                        }
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
                     shadowCube(gl, ev.x0, ev.y0, ev.z0, size, fy+b, fx+b, fz+b, alpha, shadowFactor);
                                                              
                }                     
               
                
            }
          
              private void draw3VoxelsVolume( GL gl ){
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
               // int half = retinaSize/2;
                
           
                //  System.out.println("middleAngle for planeAngle("+planeAngle+")= "+middleAngle);
                
                
                if(showRetina){
                    
                    if(recordTrackerData){
                        gl.glColor3f(1.0f,0.0f,0.0f);
                    } else {
                        gl.glColor3f(0.0f,0.0f,1.0f);
                    }
                   
                    line3D( gl,  leftRetinaCoordinates[0][0].x,  leftRetinaCoordinates[0][0].y,  leftRetinaCoordinates[0][0].z,   
                            leftRetinaCoordinates[0][retinaSize-1].x,  leftRetinaCoordinates[0][retinaSize-1].y,  leftRetinaCoordinates[0][retinaSize-1].z);
                    line3D( gl,  leftRetinaCoordinates[0][0].x,  leftRetinaCoordinates[0][0].y,  leftRetinaCoordinates[0][0].z,   
                            leftRetinaCoordinates[retinaSize-1][0].x,  leftRetinaCoordinates[retinaSize-1][0].y,  leftRetinaCoordinates[retinaSize-1][0].z);
                    line3D( gl,  leftRetinaCoordinates[0][retinaSize-1].x,  leftRetinaCoordinates[0][retinaSize-1].y,  leftRetinaCoordinates[0][retinaSize-1].z,   
                            leftRetinaCoordinates[retinaSize-1][retinaSize-1].x,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].y,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  leftRetinaCoordinates[retinaSize-1][0].x,  leftRetinaCoordinates[retinaSize-1][0].y,  leftRetinaCoordinates[retinaSize-1][0].z,   
                            leftRetinaCoordinates[retinaSize-1][retinaSize-1].x,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].y,  leftRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                    
                    gl.glColor3f(1.0f,0.0f,0.0f);
                    line3D( gl,  rightRetinaCoordinates[0][0].x,  rightRetinaCoordinates[0][0].y,  rightRetinaCoordinates[0][0].z,   
                            rightRetinaCoordinates[0][retinaSize-1].x,  rightRetinaCoordinates[0][retinaSize-1].y,  rightRetinaCoordinates[0][retinaSize-1].z);
                    line3D( gl,  rightRetinaCoordinates[0][0].x,  rightRetinaCoordinates[0][0].y,  rightRetinaCoordinates[0][0].z,   
                            rightRetinaCoordinates[retinaSize-1][0].x,  rightRetinaCoordinates[retinaSize-1][0].y,  rightRetinaCoordinates[retinaSize-1][0].z);
                    line3D( gl,  rightRetinaCoordinates[0][retinaSize-1].x,  rightRetinaCoordinates[0][retinaSize-1].y,  rightRetinaCoordinates[0][retinaSize-1].z,   
                            rightRetinaCoordinates[retinaSize-1][retinaSize-1].x,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].y,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  rightRetinaCoordinates[retinaSize-1][0].x,  rightRetinaCoordinates[retinaSize-1][0].y,  rightRetinaCoordinates[retinaSize-1][0].z,   
                            rightRetinaCoordinates[retinaSize-1][retinaSize-1].x,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].y,  rightRetinaCoordinates[retinaSize-1][retinaSize-1].z);
                      
                    
                    shadowCube(gl, left_focal.x, left_focal.y, left_focal.z, 10, 1, 1, 0, 0.5f, 0);
                    shadowCube(gl, right_focal.x, right_focal.y, right_focal.z, 10, 1, 1, 0, 0.5f, 0);                        
                    
                }
                
                
                // blue frame
                
                if(showCage){
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
                
//                  if(showZones){
//                    gl.glColor3f(1.0f,1.0f,0.0f);	// yellow color
//                                                                                                                 
//                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,   searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z);                        
//                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z);
//                        line3D( gl,  searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z);                        
//                        line3D( gl,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z);    
//                        line3D( gl,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z);
//                        line3D( gl,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z);
//                        line3D( gl,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z); 
//                        
//                        line3D( gl,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z);             
//                        line3D( gl,  searchSpace.rp1.x,  searchSpace.rp1.y,  searchSpace.rp1.z,  searchSpace.rp5.x,  searchSpace.rp5.y,  searchSpace.rp5.z);
//                        line3D( gl,  searchSpace.rp2.x,  searchSpace.rp2.y,  searchSpace.rp2.z,  searchSpace.rp6.x,  searchSpace.rp6.y,  searchSpace.rp6.z);       
//                        line3D( gl,  searchSpace.rp3.x,  searchSpace.rp3.y,  searchSpace.rp3.z,  searchSpace.rp7.x,  searchSpace.rp7.y,  searchSpace.rp7.z);       
//                        line3D( gl,  searchSpace.rp4.x,  searchSpace.rp4.y,  searchSpace.rp4.z,  searchSpace.rp8.x,  searchSpace.rp8.y,  searchSpace.rp8.z);       
//                        
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
//                        if(checkNose){
//                            
//                          gl.glColor3f(1.0f,0.5f,0.0f);	
//                                                                                                                 
//                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,   noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z);                        
//                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z);
//                        line3D( gl,  noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z);                        
//                        line3D( gl,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z);    
//                        line3D( gl,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z);
//                        line3D( gl,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z);
//                        line3D( gl,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z); 
//                        
//                        line3D( gl,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z);             
//                        line3D( gl,  noseSpace.rp1.x,  noseSpace.rp1.y,  noseSpace.rp1.z,  noseSpace.rp5.x,  noseSpace.rp5.y,  noseSpace.rp5.z);
//                        line3D( gl,  noseSpace.rp2.x,  noseSpace.rp2.y,  noseSpace.rp2.z,  noseSpace.rp6.x,  noseSpace.rp6.y,  noseSpace.rp6.z);       
//                        line3D( gl,  noseSpace.rp3.x,  noseSpace.rp3.y,  noseSpace.rp3.z,  noseSpace.rp7.x,  noseSpace.rp7.y,  noseSpace.rp7.z);       
//                        line3D( gl,  noseSpace.rp4.x,  noseSpace.rp4.y,  noseSpace.rp4.z,  noseSpace.rp8.x,  noseSpace.rp8.y,  noseSpace.rp8.z);       
//                        
//                        }
//                        
//                }
                
                
           //     gl.glFlush();
                
             
                
                
            }
            
            
            
            
            private void line3D(GL gl, float x, float y, float z, float x2,  float y2,  float z2) {
                gl.glBegin(gl.GL_LINES);
                gl.glVertex3f( x,y,z);
                gl.glVertex3f( x2,y2,z2);
                gl.glEnd();
            }
            
           private void shadowCube(GL gl, int x, int y, int z, int size, float r, float g, float b, float alpha, float shadow) {
               shadowCube( gl, (float) x, (float) y, (float) z, (float) size,  r,  g,  b,  alpha,  shadow);
                
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

                GL gl = drawable.getGL();
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glPushMatrix();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;


                //     gl.glEnable(GL.GL_DEPTH_TEST); //enable depth testing
                //     gl.glDepthFunc(GL.GL_LEQUAL); //Type of depth function

                //  glu.gluLookAt(-200,-200,50,-200,-200,0,0.0,1.0,0.0);

                //System.out.println("display: system time:"+System.currentTimeMillis());

                if (leftDragged) {
                    leftDragged = false;
                    tx = dragDestX - dragOrigX;
                    ty = dragOrigY - dragDestY;

                }
                if (middleDragged) {
                    middleDragged = false;

                    zty = zdragOrigY - zdragDestY;
                    zty = zty * 20;
                }
                if (rightDragged) {
                    rightDragged = false;
                    //    rtx = rdragDestX-rdragOrigX;
                    rtx = rdragOrigX - rdragDestX;
                    rty = rdragOrigY - rdragDestY;

                }

                float ox = origX + tx;
                float oy = origY + ty;
                float oz = zOrigY + zty;




                float translation_x = 0;
                float translation_y = 0;
                float translation_z = 0;


                // parametrize this in function of pixel size and distance between retinae and open gl angle of vision
                gl.glTranslatef(ox - 1000, oy - 25, -oz + tz - 15000);
                translation_x = ox - 1000;
                translation_y = oy - 25;
                translation_z = -oz + tz - 15000;


                float rx = rOrigX + rtx;
                float ry = rOrigY + rty;


                gl.glRotatef(ry + kry, 1.0f, 0.0f, 0.0f);

                gl.glRotatef(rx + krx, 0.0f, 1.0f, 0.0f);


                // keyboard rotation :
                rOrigY += kry;
                rOrigX += krx;
                kry = 0;
                krx = 0;

                if (showAxes) {
                    draw3DAxes(gl);
                }




              //  if (showAcc) {

           
               
               //  drawCurrent3DEvents(gl, current3DEvents, eventTrackerSize);
              drawCurrent3DEvents(gl, current3DEvents, cube_size);

                  
               


              //  }

                //   }

                if (showFingers || showFingersRange) {
                    //     float colorFinger = 0;
                    //     float colorFinger2 = 0;
                    ///     Color trackerColor = null;
                    try {
                        for (PawCluster fc : paws) {


                            if (fc != null) {
                                //     trackerColor = new Color(fc.id*4);
                                //     System.out.println("trackerColor id:"+fc.id
                                //            +" color r:"+(float)trackerColor.getRed()/255
                                //            +" color g:"+(float)trackerColor.getGreen()/255
                                //            +" color b:"+(float)trackerColor.getBlue()/255
                                //             +" at x:"+fc.x+" y:"+fc.y+" z:"+fc.z
                                //            );
                                //  if(fc.activated){
                                //  gl.glColor3f((float)trackerColor.getRed()/32+0.5f,
                                //         (float)trackerColor.getGreen()/32,
                                //        (float)trackerColor.getBlue()/32);
                                if (showFingers) {
                                    gl.glColor3f(1.0f, 0, 0);
                                    //colorFinger,colorFinger2);
                                    //cube(gl,fc.x,fc.y,fc.z,paw_surround);
                                    rectangle3D(gl, fc.x, fc.y, fc.z, fc.x_size / 2, fc.y_size / 2, fc.z_size / 2);
                                    
                                    gl.glColor3f(1.0f, 0, 1.0f);
                                    for (FingerCluster ff : fc.fingers) {
                                       rectangle3D(gl, fc.x+ff.x, fc.y+ff.y, fc.z+ff.z, finger_surround / 2, finger_surround / 2, finger_surround / 2);
                                     
                                    }
                                }
                                if (showFingersRange) {
                                    if (fc.nbEvents < tracker_viable_nb_events) {
                                        gl.glColor3f(1.0f, 1.0f, 0.0f);
                                    } else {
                                        gl.glColor3f(1.0f, 0, 1.0f);
                                    }
                                    //colorFinger,colorFinger2);
                                    cube(gl, fc.x, fc.y, fc.z, paw_surround / 2);
                                // rectangle3D(gl,fc.x,fc.y,fc.z,fc.x_size*2,fc.y_size*2,fc.z_size*2);  
                                }

                            //  }
                            }
                        }
                    } catch (java.util.ConcurrentModificationException e) {
                    }
                }



              



                //    if(showFrame){
                draw3DFrames(gl);
                //   }






                gl.glPopMatrix();
                gl.glFlush();

                if (pngRecordEnabled) {
                    grabImage(drawable);
                }



                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    //if(glu==null) glu=new GLU();
                    //log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                    System.out.println("GL error number " + error + " " + glu.gluErrorString(error));
                }
            }

            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl = drawable.getGL();
                final int B = 10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!

                glu.gluPerspective(10.0, (double) width / (double) height, 0.5, 100000.0);

                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);

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
        
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in PawTrackerStereoBoard5.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
   
            
             if (showEvenTrackers) displayAllEventTrackers(gl);
            
             // draw epipolar monitored line
               gl.glColor3f(0,0.5f,0);
               
               drawBox(gl,0,retinaSize,y_monitor-1,y_monitor+1);
               
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
    
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        if(!isFilterEnabled()) return;
        // disable for now TODO
       
        
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        
        
        
    }
    
    public synchronized void displayAllEventTrackers( GL gl ){
        
      //  int nbLines = retinaSize/trackerLineWidth;         
          for(int i=0;i<maxTrackerLines;i++){
              displayEventTrackers(gl,leftOnTrackers[i],leftOnColor);
              displayEventTrackers(gl,leftOffTrackers[i],leftOffColor);
              displayEventTrackers(gl,rightOnTrackers[i],rightOnColor);
              displayEventTrackers(gl,rightOffTrackers[i],rightOffColor);
          }   
     
    }
    
     private synchronized void displayEventTrackers(GL gl,  Vector<EventTracker> trackers, Color color ){
        
    
        float[] rgb=color.getRGBColorComponents(null);
        gl.glColor3f(rgb[0], rgb[1], rgb[2]);
        float c = 0;
        for(EventTracker et:trackers){
              gl.glColor3f(rgb[0]-c, rgb[1]-c, rgb[2]-c);
              drawEventTracker(gl, et );
              c+=0.2f;
              if(c>1)c=0;
        }   
     
    }
    
       final void drawEventTracker(GL gl, final EventTracker c ){
        int x=Math.round(c.x);
        int y=Math.round(c.y);
        
        
        int sy=eventTrackerSize;
        int sx=sy;
    
       
       drawBoxCentered(gl, x, y, sx, sy);
      
       if(showVelocity){
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2i(x,y);
                gl.glVertex2f(x+c.vx*velocityFactor,y+c.vy*velocityFactor);
            }
            gl.glEnd();
        }
       
        
    }
 
  
    public synchronized boolean isUseVelocity() {
        return useVelocity;
    }
    
    public synchronized void setUseVelocity(boolean useVelocity) {
        this.useVelocity = useVelocity;
        getPrefs().putBoolean("PawTrackerStereoBoard5.useVelocity",useVelocity);
    } 
       
    public synchronized boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    public synchronized void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        getPrefs().putBoolean("PawTrackerStereoBoard5.logDataEnabled",logDataEnabled);
        
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
    
    
   
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("PawTrackerStereoBoard5.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    
 
  
    
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.resetPawTracking",resetPawTracking);
        
        if(resetPawTracking){
            resetPawTracker();
        }
        
    }
    
      public boolean isResetClusters() {
        return resetClusters;
    }
    public void setResetClusters(boolean resetClusters) {
        this.resetClusters = resetClusters;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.resetClusters",resetClusters);
        
        if(resetClusters){
            resetClusterTrackers();
        }
        
    }
    
    public boolean isRestart() {
        return restart;
    }
    public void setRestart(boolean restart) {
        this.restart = restart;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.restart",restart);
        
    }
    

    
   
    
    
//    
//    public void setShowFrame(boolean showFrame){
//        this.showFrame = showFrame;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard5.showFrame",showFrame);
//    }
//    public boolean isShowFrame(){
//        return showFrame;
//    }
    
    
    public void setShowEvenTrackers(boolean showEvenTrackers){
        this.showEvenTrackers = showEvenTrackers;
        getPrefs().putBoolean("PawTrackerStereoBoard5.showEvenTrackers",showEvenTrackers);
    }
    public boolean isShowEvenTrackers(){
        return showEvenTrackers;
    }   
      
    public void setShowVelocity(boolean showVelocity){
        this.showVelocity = showVelocity;
        getPrefs().putBoolean("PawTrackerStereoBoard5.showVelocity",showVelocity);
    }
    public boolean isShowVelocity(){
        return showVelocity;
    }   
      
    public void setShowScore(boolean showScore){
        this.showScore = showScore;
        getPrefs().putBoolean("PawTrackerStereoBoard5.showScore",showScore);
    }
    public boolean isShowScore(){
        return showScore;
    }     
    
    public void setShowCage(boolean showCage){
        this.showCage = showCage;
        getPrefs().putBoolean("PawTrackerStereoBoard5.showCage",showCage);
    }
    public boolean isShowCage(){
        return showCage;
    }
    
    public void setShowRetina(boolean showRetina){
        this.showRetina = showRetina;
        getPrefs().putBoolean("PawTrackerStereoBoard5.showRetina",showRetina);
    }
    public boolean isShowRetina(){
        return showRetina;
    }
    
    
    public void setShow2DWindow(boolean show2DWindow){
        this.show2DWindow = show2DWindow;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.show2DWindow",show2DWindow);
    }
    public boolean isShow2DWindow(){
        return show2DWindow;
    }
      public void setShow3DWindow(boolean show3DWindow){
        this.show3DWindow = show3DWindow;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.show3DWindow",show3DWindow);
    }
    public boolean isShow3DWindow(){
        return show3DWindow;
    }
    
    
  
            
//    
//    public void setShowScore(boolean showScore){
//        this.showScore = showScore;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard5.showScore",showScore);
//    }
//    public boolean isShowScore(){
//        return showScore;
//    }
//    
   
    
    
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
     public void setShowFingersRange(boolean showFingersRange){
        this.showFingersRange = showFingersRange;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showFingersRange",showFingersRange);
    }
    public boolean isShowFingersRange(){
        return showFingersRange;
    }
//    
//    public void setShowFingerTips(boolean showFingerTips){
//        this.showFingerTips = showFingerTips;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard5.showFingerTips",showFingerTips);
//    }
//    public boolean isShowFingerTips(){
//        return showFingerTips;
//    }
    
  
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
  
    
   
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
 
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.brightness",brightness);
    }
             
    public void setAlpha(float alpha) {
        this.alpha = alpha;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.alpha",alpha);
    }
    public float getAlpha() {
        return alpha;
    }
    public void setIntensity(float intensity) {
        this.intensity = intensity;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.intensity",intensity);
    }
    public float getIntensity() {
        return intensity;
    }               
    
    public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.valueThreshold",valueThreshold);
    }
    public float getValueThreshold() {
        return valueThreshold;
    }
    
    
    
    public void setGrasp_max_elevation(int grasp_max_elevation) {
        this.grasp_max_elevation = grasp_max_elevation;
        compute3DParameters();
        getPrefs().putInt("PawTrackerStereoBoard5.grasp_max_elevation",grasp_max_elevation);
    }
    public int getGrasp_max_elevation() {
        return grasp_max_elevation;
    }
          
    public void setMax_finger_clusters(int max_finger_clusters) {
        this.max_finger_clusters = max_finger_clusters;
        
        getPrefs().putInt("PawTrackerStereoBoard5.max_finger_clusters",max_finger_clusters);
    }
    public int getMax_finger_clusters() {
        return max_finger_clusters;
    }
    public void setCage_distance(float cage_distance) {
        this.cage_distance = cage_distance;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_distance",cage_distance);
    }
    public float getCage_distance() {
        return cage_distance;
    }
    
     public float getRetina_tilt_angle() {
        return retina_tilt_angle;
    }
    public void setRetina_tilt_angle(float retina_tilt_angle) {
        this.retina_tilt_angle = retina_tilt_angle;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.retina_tilt_angle",retina_tilt_angle);
    }
    
    public void setRetina_height(float retina_height) {
        this.retina_height = retina_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.retina_height",retina_height);
    }
    public float getRetina_height() {
        return retina_height;
    }
    public void setCage_door_height(float cage_door_height) {
        this.cage_door_height = cage_door_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_door_height",cage_door_height);
    }
    public float getCage_door_height() {
        return cage_door_height;
    }
    
    public void setCage_height(float cage_height) {
        this.cage_height = cage_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_height",cage_height);
    }
    public float getCage_height() {
        return cage_height;
    }
    public void setCage_width(float cage_width) {
        this.cage_width = cage_width;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_width",cage_width);
    }
    public float getCage_width() {
        return cage_width;
    }
    public void setCage_platform_length(float cage_platform_length) {
        this.cage_platform_length = cage_platform_length;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_platform_length",cage_platform_length);
    }
    public float getCage_platform_length() {
        return cage_platform_length;
    }
    public void setCage_door_width(float cage_door_width) {
        this.cage_door_width = cage_door_width;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard5.cage_door_width",cage_door_width);
    }
    public float getCage_door_width() {
        return cage_door_width;
    }
     
    public void setColorizeFactor(float colorizeFactor) {
        this.colorizeFactor = colorizeFactor;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.colorizeFactor",colorizeFactor);
    }
    public float getColorizeFactor() {
        return colorizeFactor;
    }
    
    public void setShadowFactor(float shadowFactor) {
        this.shadowFactor = shadowFactor;
        
        getPrefs().putFloat("PawTrackerStereoBoard5.shadowFactor",shadowFactor);
    }
    public float getShadowFactor() {
        return shadowFactor;
    }
     
    
    
    public void setColorizePeriod(int colorizePeriod) {
        this.colorizePeriod = colorizePeriod;
        
        getPrefs().putInt("PawTrackerStereoBoard5.colorizePeriod",colorizePeriod);
    }
    public int getColorizePeriod() {
        return colorizePeriod;
    }
    
    
   
    
    
    public void setShowZColor(boolean showZColor){
        this.showZColor = showZColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showZColor",showZColor);
    }
    public boolean isShowZColor(){
        return showZColor;
    }
    
    public void setShowYColor(boolean showYColor){
        this.showYColor = showYColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showYColor",showYColor);
    }
    public boolean isShowYColor(){
        return showYColor;
    }
    
    public void setShowXColor(boolean showXColor){
        this.showXColor = showXColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.showXColor",showXColor);
    }
    public boolean isShowXColor(){
        return showXColor;
    }
    
    
    public void setTrackFingers(boolean trackFingers){
        this.trackFingers = trackFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.trackFingers",trackFingers);
    }
    public boolean isTrackFingers(){
        return trackFingers;
    }

    public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
        
        getPrefs().putInt("PawTrackerStereoBoard5.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }
    
    
    
    public float getEventTrackerMix() {
        return eventTrackerMix;
    }
    
    public void setEventTrackerMix(float eventTrackerMix) {
        this.eventTrackerMix = eventTrackerMix;
        getPrefs().putFloat("PawTrackerStereoBoard5.eventTrackerMix",eventTrackerMix);
    }
            
    public float getFinger_mix() {
        return finger_mix;
    }
    
    public void setFinger_mix(float finger_mix) {
        this.finger_mix = finger_mix;
        getPrefs().putFloat("PawTrackerStereoBoard5.finger_mix",finger_mix);
    }
      
    
    public float getPaw_mix_on_evts() {
        return paw_mix_on_evts;
    }
    
    public void setPaw_mix_on_evts(float paw_mix_on_evts) {
        this.paw_mix_on_evts = paw_mix_on_evts;
        getPrefs().putFloat("PawTrackerStereoBoard5.paw_mix_on_evts",paw_mix_on_evts);
    }
    
    
    public float getPaw_mix_off_evts() {
        return paw_mix_off_evts;
    }
    
    public void setPaw_mix_off_evts(float paw_mix_off_evts) {
        this.paw_mix_off_evts = paw_mix_off_evts;
        getPrefs().putFloat("PawTrackerStereoBoard5.paw_mix_off_evts",paw_mix_off_evts);
    }
    
    public float getExpansion_mix() {
        return expansion_mix;
    }
    
    public void setExpansion_mix(float expansion_mix) {
        this.expansion_mix = expansion_mix;
        getPrefs().putFloat("PawTrackerStereoBoard5.expansion_mix",expansion_mix);
    }
    
    public float getVelocity_mix() {
        return velocity_mix;
    }
    
    public void setVelocity_mix(float velocity_mix) {
        this.velocity_mix = velocity_mix;
        getPrefs().putFloat("PawTrackerStereoBoard5.velocity_mix",velocity_mix);
    }
    
    public float getVelocityFactor() {
        return velocityFactor;
    }
    
    public void setVelocityFactor(float velocityFactor) {
        this.velocityFactor = velocityFactor;
        getPrefs().putFloat("PawTrackerStereoBoard5.velocityFactor",velocityFactor);
    }
    
    
    
    public float getVelocityMin() {
        return velocityMin;
    }
    
    public void setVelocityMin(float velocityMin) {
        this.velocityMin = velocityMin;
        getPrefs().putFloat("PawTrackerStereoBoard5.velocityMin",velocityMin);
    }
    
    public float getVelocityThreshold() {
        return velocityThreshold;
    }
    
    public void setVelocityThreshold(float velocityThreshold) {
        this.velocityThreshold = velocityThreshold;
        getPrefs().putFloat("PawTrackerStereoBoard5.velocityThreshold",velocityThreshold);
    }
   
    
    
    public float getTrackerSubsamplingDistance() {
        return trackerSubsamplingDistance;
    }
    
    public void setTrackerSubsamplingDistance(float trackerSubsamplingDistance) {
        this.trackerSubsamplingDistance = trackerSubsamplingDistance;
        getPrefs().putFloat("PawTrackerStereoBoard5.trackerSubsamplingDistance",trackerSubsamplingDistance);
    }
    
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        getPrefs().putInt("PawTrackerStereoBoard5.finger_surround",finger_surround);
    }
    
    public int getPaw_surround() {
        return paw_surround;
    }
    
    public void setPaw_surround(int paw_surround) {
        this.paw_surround = paw_surround;
        getPrefs().putInt("PawTrackerStereoBoard5.paw_surround",paw_surround);
    }
    
    public int getY_monitor() {
        return y_monitor;
    }
    
    public void setY_monitor(int y_monitor) {
        this.y_monitor = y_monitor;
        getPrefs().putInt("PawTrackerStereoBoard5.y_monitor",y_monitor);
    }
    
    
      
    public int getTracker_lifeTime() {
        return tracker_lifeTime;
    }
    
    public void setTracker_lifeTime(int tracker_lifeTime) {
        this.tracker_lifeTime = tracker_lifeTime;
        getPrefs().putInt("PawTrackerStereoBoard5.tracker_lifeTime",tracker_lifeTime);
    }
    
    public int getTracker_prelifeTime() {
        return tracker_prelifeTime;
    }
    
    public void setTracker_prelifeTime(int tracker_prelifeTime) {
        this.tracker_prelifeTime = tracker_prelifeTime;
        getPrefs().putInt("PawTrackerStereoBoard5.tracker_prelifeTime",tracker_prelifeTime);
    }
    
     public int getTracker_viable_nb_events() {
        return tracker_viable_nb_events;
    }
    
    public void setTracker_viable_nb_events(int tracker_viable_nb_events) {
        this.tracker_viable_nb_events = tracker_viable_nb_events;
        getPrefs().putInt("PawTrackerStereoBoard5.tracker_viable_nb_events",tracker_viable_nb_events);
    }
         
   
    
    public float getFocal_length() {
        return focal_length;
    }
    
    public void setFocal_length(float focal_length) {
        this.focal_length = focal_length;
        getPrefs().putFloat("PawTrackerStereoBoard5.focal_length",focal_length);
        compute3DParameters();
    }

    public void setRetinae_distance(float retinae_distance) {
        this.retinae_distance = retinae_distance;
        getPrefs().putFloat("PawTrackerStereoBoard5.retinae_distance",retinae_distance);
        compute3DParameters();
    }
     public float getRetinae_distance() {
        return retinae_distance;
    }
    
    public void setPixel_size(float pixel_size) {
        this.pixel_size = pixel_size;
        getPrefs().putFloat("PawTrackerStereoBoard5.pixel_size",pixel_size);
        compute3DParameters();
    }
     public float getPixel_size() {
        return pixel_size;
    }
    
    public void setRetina_angle(float retina_angle) {
        this.retina_angle = retina_angle;
        getPrefs().putFloat("PawTrackerStereoBoard5.retina_angle",retina_angle);
        compute3DParameters();
    }
    public float getRetina_angle() {
        return retina_angle;
    }
    
    public void setRecordTrackerData(boolean recordTrackerData){
        this.recordTrackerData = recordTrackerData;
        
        getPrefs().putBoolean("PawTrackerStereoBoard5.recordTrackerData",recordTrackerData);
    }
    public boolean isRecordTrackerData(){
        return recordTrackerData;
    }
    
   
    
    public int getEventTrackerSize() {
        return eventTrackerSize;
    }
    
    public void setEventTrackerSize(int eventTrackerSize) {
        if(eventTrackerSize<=0)eventTrackerSize=1;
        this.eventTrackerSize = eventTrackerSize;
        getPrefs().putInt("PawTrackerStereoBoard5.eventTrackerSize",eventTrackerSize);
    } 
            
    public int getTrackerLineWidth() {
        return trackerLineWidth;
    }
    
    public void setTrackerLineWidth(int trackerLineWidth) {
        if(trackerLineWidth<=0)trackerLineWidth=1;
        this.trackerLineWidth = trackerLineWidth;
        getPrefs().putInt("PawTrackerStereoBoard5.trackerLineWidth",trackerLineWidth);
    }
            
  
    
    public int getStart_min_events() {
        return start_min_events;
    }
    
    public void setStart_min_events(int start_min_events) {
        this.start_min_events = start_min_events;
        getPrefs().putInt("PawTrackerStereoBoard5.start_min_events",start_min_events);
    }
    
   
 
    public void setRecordUpTo(int recordUpTo) {
        this.recordUpTo = recordUpTo;
        // no need to save from on session to the other
       // getPrefs().putInt("PawTrackerStereoBoard5.recordUpTo",recordUpTo);
    }
      
    public int getRecordUpTo() {
        return recordUpTo;
    }
    
   
    public void setTimeDelay(float timeDelay) {
        this.timeDelay = timeDelay;
        getPrefs().putFloat("PawTrackerStereoBoard5.timeDelay",timeDelay);
        
    }
     public float getTimeDelay() {
        return timeDelay;
    }
    
    public void setX_finger_dist_min(float x_finger_dist_min) {
        this.x_finger_dist_min = x_finger_dist_min;
        getPrefs().putFloat("PawTrackerStereoBoard5.x_finger_dist_min",x_finger_dist_min);
        
    }
     public float getX_finger_dist_min() {
        return x_finger_dist_min;
    }
    
    
     public void setY_finger_dist_min(float y_finger_dist_min) {
        this.y_finger_dist_min = y_finger_dist_min;
        getPrefs().putFloat("PawTrackerStereoBoard5.y_finger_dist_min",y_finger_dist_min);
        
    }
     public float getY_finger_dist_min() {
        return y_finger_dist_min;
    }
     
      public void setZ_finger_dist_min(float z_finger_dist_min) {
        this.z_finger_dist_min = z_finger_dist_min;
        getPrefs().putFloat("PawTrackerStereoBoard5.z_finger_dist_min",z_finger_dist_min);
        
    }
     public float getZ_finger_dist_min() {
        return z_finger_dist_min;
    }
     
    
    public int getBuffer_size() {
        return buffer_size;
    }
    
    public void setBuffer_size(int buffer_size) {
        this.buffer_size = buffer_size;
        
       getPrefs().putInt("PawTrackerStereoBoard5.buffer_size",buffer_size);
    }
    
    public int getMaxNbEventTracker() {
        return maxNbEventTracker;
    }
    
    public void setMaxNbEventTracker(int maxNbEventTracker) {
        this.maxNbEventTracker = maxNbEventTracker;
        
       getPrefs().putInt("PawTrackerStereoBoard5.maxNbEventTracker",maxNbEventTracker);
    }
    
    
}
