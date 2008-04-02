/*
 * PawTrackerStereoBoard3.java
 * Tracks the paw of a rat in the grasping task experiment. see [ref]
 * Data must be recorded via stereoboard
 * version 2 differs from 1 by rendering 3D in real coordinates
 * version 3 differs from 2 by adding player/recorder interface
 *
 * Paul Rogister, Created on October, 2007
 *
 */


package ch.unizh.ini.caviar.eventprocessing.tracking;

import ch.unizh.ini.stereo3D.*;

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

import java.text.*;
/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class PawTrackerStereoBoard3 extends EventFilter2D implements FrameAnnotater, Observer, AE3DPlayerInterface, AE3DRecorderInterface /*, PreferenceChangeListener*/ {
    
    
    //debug
    int minrightZ = 3000;
    int minleftZ = 3000;
    
    
    // recorder /player variables
    
    int INITIAL_PACKET_SIZE = 1000;
    
    public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
    
    AE3DOutputStream loggingOutputStream;
    AE3DFileInputStream inputStream;
    
    AE3DPlayerRecorderFrame playerRecorderFrame;
  
    File recordFile;
    File playFile;
    float playTimeBin = 20000; // micro s
    boolean forward = true;
    boolean pauseEnabled = false;
    boolean playEnabled = false;
    boolean displayPlay = false;
    boolean recordEnabled = false;
    volatile boolean toclear = true;
    AEPacket3D loggingPacket;
    AEPacket3D inputPacket;
    AEPacket3D displayPacket = null;
    Hashtable direct3DEvents = new Hashtable();
    volatile boolean displayDirect3D = false;
    volatile boolean recordPure3D = false;
    
    // log data
    File logFile;
    BufferedWriter logWriter;
    volatile boolean logEnabled = false;
    
     //****************************************************
     // player interface
   
    synchronized public void openFile( String filename ){
        playFile = new File(filename);
        toclear = true;
    }
    
    synchronized public void openFile( File file ){          
           playFile = file;
           toclear = true;
           
    }

   synchronized public void pause(){
       
       pauseEnabled = true;
   }
    
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
        direct3DEvents.clear();
        
    }
    
    
    synchronized public void play(){
        // start runnable to play file with desired speed and direction
        
        if(pauseEnabled){
            pauseEnabled = false;
            return;
        }
        // for direct 3D display
        direct3DEvents.clear();
        
        playEnabled = true;
        
         new Thread(new Runnable(){
            public void run(){
                startPlay();
            }                                  
        }).start();
      
    }

    
     void startPlay(){
         
        // System.out.println("startPlay playFile :"+playFile);
                             
        if(playFile==null) return;
        
         if(toclear){
            clearEventPoints();
            
            toclear = false;
                    
            try {
                inputStream = new AE3DFileInputStream(playFile);
                inputStream.getSupport().addPropertyChangeListener(playerRecorderFrame);
            } catch (FileNotFoundException e){
                // log that no file found
                System.out.println("problem opening file");
                // disable all playing buttons in gui?
                return;
            }
         }
              
        while(playEnabled){
      
             /*
            try {
              Thread.sleep(1000);
              System.out.println("playing");
            } catch (java.lang.InterruptedException e ){
              
            }
              */

            if(!pauseEnabled){
                try {
                    if(forward){
                        inputPacket = inputStream.readPacketByTime((int)playTimeBin);
                      
                    } else {
                        inputPacket = inputStream.readPacketByTime(-(int)playTimeBin);
                       
                    }
               //     System.out.println("new inputPacket :"+inputPacket);
                    if(toclear){
                        clearEventPoints();
                        
                        toclear = false;
                    }
                    if (inputPacket!=null){
                        if(inputPacket.getType()==Event3D.DIRECT3D){
                            // direct display. how? to work there, paul 080208
                            // switch display flag
                            displayPacket = inputPacket.getPrunedCopy();
                            synchronized(displayPacket){
                                
                                storeDisplayPacket();
                            }
                            // it is here that events shoud be added to hashtable, not in display
                            
                            displayDirect3D = true;
//                            try {
//                                Thread.sleep(100);
//                                // System.out.println("playing");
//                            } catch (java.lang.InterruptedException e ){
//                                
//                            }
                        } else {
                            // switch display flag
                            displayDirect3D = false;
                            processPacket(inputPacket);
                        }
                    }
  
                }catch(EOFException e){
                    System.err.println("EOF exception: "+e.getMessage());
                    try{
                        //pauseEnabled = true;
                        Thread.sleep(10);
                        clearEventPoints();
                        inputStream.rewind();
                        
                    }catch(Exception ee){
                        System.err.println("rewind exception: "+ee.getMessage());
                        ee.printStackTrace();
                    }
                

                    
                } catch (IOException e){
                    playEnabled = false;
                    e.printStackTrace();
                }
            } //end if not pause
            
            // display all events in last packet
            // for all events, add to right and left
                                     
             displayPlay = true;
             if (show3DWindow&&!windowDeleted) a3DCanvas.repaint();
             
          
      //       pause thread
             try {
              Thread.sleep(40);
             // System.out.println("playing");
            } catch (java.lang.InterruptedException e ){
                
            }

        }
        
        // end without disabling playing, press stop to come back to normal display mode
        
    }
    
     private void storeDisplayPacket(){
         // fill hashtable with current events
            // storing events in hashtable where key is x-y-z
                int n=inputPacket.getNumEvents();
                //         System.out.println("processPacket inputPacket n:"+n);
                
                
                if(inputPacket.getType()==Event3D.DIRECT3D){
                    for(int i=0;i<n;i++){
                        Event3D ev = new Event3D(inputPacket.getEvent(i));
                        //   if(ev.type==Event3D.DIRECT3D){
                        String key = new String(ev.x0+"-"+ev.y0+"-"+ev.z0);
                        synchronized(direct3DEvents){
                            if(ev.value>valueThreshold){
                                direct3DEvents.put(key,ev);
                            } else { // deletion?
                                direct3DEvents.remove(key);
                            }
                        }
                        // }
                        
                    }
                }
     }
     
     
     public float getFractionalPosition() {
         if(inputStream==null){
            // log.warning("AEViewer.AEPlayer.getFractionalPosition: null fileAEInputStream, returning 0");
             return 0;
         }
         return inputStream.getFractionalPosition();
     }

     public void setFractionalPosition(float frac) {
            inputStream.setFractionalPosition(frac);
     }
     
     public AE3DFileInputStream getInputStream(){
         return inputStream;
     }
     
    synchronized public void processPacket( AEPacket3D inputPacket ){
                 
            int x = 0;
            int y = 0;
            int d = 0;
            float f = 0;
            int method = 0;
            int lead_side = 0;
            EventPoint leadPoints[][];
            EventPoint slavePoints[][];
            
            int n=inputPacket.getNumEvents();
   //         System.out.println("processPacket inputPacket n:"+n);
            
            for(int i=0;i<n;i++){
                f = inputPacket.getValues()[i];
                x = inputPacket.getCoordinates_x()[i];
                y  = inputPacket.getCoordinates_y()[i];
                d =  inputPacket.getDisparities()[i];
                
                
                method = inputPacket.getMethods()[i];
                lead_side = inputPacket.getLead_sides()[i];
                
                if(x>=retinaSize||y>=retinaSize||d>=retinaSize){
                    // error message
                    System.out.println("processPacket i:"+i+" n:"+n+" error: x:"+x+" y:"+y+" d:"+d
                            +" f:"+f+" m:"+method+" ls:"+lead_side+" ts:"+inputPacket.getTimestamps()[i]);
                } else {
                    
                    if (lead_side==LEFT){
                        leadPoints = leftPoints;
                        slavePoints = rightPoints;
                    } else {
                        leadPoints = rightPoints;
                        slavePoints = leftPoints;
                    }
                    
                    if (d<0){
                        //remove event
                        int xs = d*-1;
                        if (method==LEFT_MOST_METHOD){
                            leadPoints[x][y].prevDisparityLink = xs;
                            leadPoints[x][y].disparityLink = DELETE_LINK; //delete
                            slavePoints[xs][y].attachedTo = NO_LINK;
                        } else {
                            leadPoints[x][y].prevDisparityLink2 = xs;
                            leadPoints[x][y].disparityLink2 = DELETE_LINK; //delete
                            slavePoints[xs][y].attachedTo2 = NO_LINK;
                        }
                        remove3DEvent(leadPoints[x][y],method,lead_side);
                        
                    } else {
                        // add event
                        leadPoints[x][y].previousValue = leadPoints[x][y].lastValue;
                        leadPoints[x][y].lastValue = f;
                        leadPoints[x][y].accValue = f;
                        leadPoints[x][y].shortFilteredValue = f;
                        
                        if (method==LEFT_MOST_METHOD){
                            
                            
                            int ax = slavePoints[d][y].attachedTo;
                            slavePoints[d][y].attachedTo = x;
                            
                            
                            if(leadPoints[x][y].disparityLink>NO_LINK){
                                slavePoints[leadPoints[x][y].disparityLink][y].attachedTo = NO_LINK;
                            }
                            leadPoints[x][y].disparityLink = d;
                            
                            
                            leadPoints[x][y].changed = true;
                            
                            
                            new3DEvent(leadPoints[x][y],method,lead_side);
                            
                            if (ax>NO_LINK){
                                leadPoints[ax][y].prevDisparityLink = d;
                                leadPoints[ax][y].disparityLink = DELETE_LINK;
                                
                                //#### Removing point
                                remove3DEvent(leadPoints[ax][y],method,lead_side);
                                
                            }
                        } else {
                            int ax = slavePoints[d][y].attachedTo2;
                            slavePoints[d][y].attachedTo2 = x;
                            
                            
                            if(leadPoints[x][y].disparityLink2>NO_LINK){
                                slavePoints[leadPoints[x][y].disparityLink2][y].attachedTo2 = NO_LINK;
                            }
                            leadPoints[x][y].disparityLink2 = d;
                            
                            
                            leadPoints[x][y].changed = true;
                            
                            
                            new3DEvent(leadPoints[x][y],method,lead_side);
                            
                            if (ax>NO_LINK){
                                leadPoints[ax][y].prevDisparityLink2 = d;
                                leadPoints[ax][y].disparityLink2 = DELETE_LINK;
                                
                                //#### Removing point
                                remove3DEvent(leadPoints[ax][y],method,lead_side);
                                
                            }
                        }
                    }
                }
            }//end for
    }  
     
    synchronized public void revert(){
        forward = !forward;
       
    }
   
    synchronized public boolean isForward(){
         return forward;
     }
     
    synchronized public void stop(){
        pauseEnabled = false;
        playEnabled = false;
        displayPlay = false;
        toclear = true;
        displayDirect3D = false;
        // stop runnable
    }
    
    synchronized public void speedUp(){
        // increase time-bin by factor 2
          playTimeBin = playTimeBin*2;
          
    }
    synchronized public void slowDown(){
        // decrease
        playTimeBin = playTimeBin/2;
    }
    
    public float getSpeed(){
        return playTimeBin;
    }
    
    //****************************************************
    // recorder interface
        
    
    
   

    public void record( int type ){
        
        try {
            String dateString=loggingFilenameDateFormat.format(new Date());
            String filename = "data3D-"+dateString+".dat";
            recordFile=new File(filename);
            loggingPacket = new AEPacket3D(INITIAL_PACKET_SIZE,type);
            recordEnabled=true;
            if(type==Event3D.DIRECT3D){
                recordPure3D = true;
            } else {
                recordPure3D = false;
            }
            loggingOutputStream=new AE3DOutputStream(new BufferedOutputStream(new FileOutputStream(recordFile)),type);
            
        }catch(IOException e){
            
            e.printStackTrace();
        }
      
       
       
    }
 
     public void doRecording(  ){
        
        
        try {
          if(loggingOutputStream!=null){
          
                loggingOutputStream.writePacket(loggingPacket);
          
          }
          int recordType = Event3D.INDIRECT3D;
          if(recordPure3D) recordType = Event3D.DIRECT3D;
          
          loggingPacket = new AEPacket3D(INITIAL_PACKET_SIZE,recordType);
          
        }catch(IOException e){
           
            e.printStackTrace();
        }
        
        // set record to false
    }
    
    public File stopRecording(  ){
        recordEnabled = false;
        
        
        try {
         
            // last writing
          loggingOutputStream.writePacket(loggingPacket);
          loggingOutputStream.close();
          
                
          
        }catch(IOException e){
           
            e.printStackTrace();
        }
        
       
        
        return recordFile;
    }
    
    
    /***************** log ********************/
    public void doLog(  ){
        if(recordTrackerData&&!logEnabled){
            initLogData();
        } else if(!recordTrackerData&&logEnabled){
            closeLogData();
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
            int idat = filename.indexOf(".dat");
            
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
    
    /** the number of classes of objects */
    private final int NUM_CLASSES=2;
    // max number of orientations
    private final int MAX_SEGMENTS=10000;
    //private final int MAX_DISTGC=300;//depends
    //private final int MAX_SEQ_LENGTH=50;
    //private final int MAX_NB_SEQ=310;
    
  //  private final int MAX_NB_FINGERS=5;
    
    private double maxOrientation = 180;
    private double maxDistGC = 200;
    
    private float middleAngle;
    
    
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
    
    
    int scaleFactor = 1;
    
    float step = 0.33334f;
    float epipolar_distance = 0;
    double epipolar_angle_min;
    double epipolar_angle_max;
    double epipolar_angle_span;
    double epipolar_distance_span;
    Point left_epipole = new Point();
    Point right_epipole = new Point();;
    // Parameters appearing in the GUI
    
  //  private float planeAngle=getPrefs().getFloat("PawTrackerStereoBoard3.planeAngle",-30.0f);  
 //   private float platformAngle=getPrefs().getFloat("PawTrackerStereoBoard3.platformAngle",-20.0f);
    
    private float retina_tilt_angle=getPrefs().getFloat("PawTrackerStereoBoard3.retina_tilt_angle",-40.0f);
    {setPropertyTooltip("retina_tilt_angle","forward tilt angle of the retinae, in degrees");}
    private float retina_height=getPrefs().getFloat("PawTrackerStereoBoard3.retina_height",30.0f);
    {setPropertyTooltip("retina_height","height of retina lens, in mm");}
    private float cage_distance=getPrefs().getFloat("PawTrackerStereoBoard3.cage_distance",100.0f);
    {setPropertyTooltip("cage_distance","distance between stereoboard center and cage door, in mm");}
    private float cage_door_height=getPrefs().getFloat("PawTrackerStereoBoard3.cage_door_height",30.0f);
    {setPropertyTooltip("cage_door_height","height of the cage door, in mm");}
    private float cage_height=getPrefs().getFloat("PawTrackerStereoBoard3.cage_height",400.0f);
    {setPropertyTooltip("cage_height","height of the cage, in mm");}
    private float cage_width=getPrefs().getFloat("PawTrackerStereoBoard3.cage_width",200.0f);
    {setPropertyTooltip("cage_width","width of the cage, in mm");}
    private float cage_platform_length=getPrefs().getFloat("PawTrackerStereoBoard3.cage_platform_length",30.0f);
    {setPropertyTooltip("cage_platform_length","length toward retina of cage's platform, in mm");}
    private float cage_door_width=getPrefs().getFloat("PawTrackerStereoBoard3.cage_door_width",10.0f);
    {setPropertyTooltip("cage_door_width","width of the cage's door, in mm");}
    // add get/set for these above
    
    
    

    private float focal_length=getPrefs().getFloat("PawTrackerStereoBoard3.focal_length",6.0f);
   {setPropertyTooltip("focal_length","focal length in mm");}
    private float retinae_distance=getPrefs().getFloat("PawTrackerStereoBoard3.retinae_distance",100.0f);
   {setPropertyTooltip("retinae_distance","distance between the two retinae in mm");}
    private float pixel_size=getPrefs().getFloat("PawTrackerStereoBoard3.pixel_size",0.04f);
   {setPropertyTooltip("pixel_size","pixel size of retina in mm");}
    private float retina_angle=getPrefs().getFloat("PawTrackerStereoBoard3.retina_angle",10.0f);
   {setPropertyTooltip("retina_angle","angle of rotation of retina in degrees");}
  
    
    
    private int max_finger_clusters=getPrefs().getInt("PawTrackerStereoBoard3.max_finger_clusters",10);
    private int grasp_max_elevation=getPrefs().getInt("PawTrackerStereoBoard3.grasp_max_elevation",15);
 
    
    
    private float alpha=getPrefs().getFloat("PawTrackerStereoBoard3.alpha",0.1f);
    private float intensity=getPrefs().getFloat("PawTrackerStereoBoard3.intensity",1);
    
    
 //   private int dispAvgRange=getPrefs().getInt("PawTrackerStereoBoard3.dispAvgRange",1);
    
    private int yLeftCorrection=getPrefs().getInt("PawTrackerStereoBoard3.yLeftCorrection",0);
    private int yRightCorrection=getPrefs().getInt("PawTrackerStereoBoard3.yRightCorrection",0);
    
    private float yCurveFactor=getPrefs().getFloat("PawTrackerStereoBoard3.yCurveFactor",0.1f);
    
    private float valueThreshold=getPrefs().getFloat("PawTrackerStereoBoard3.valueThreshold",0);
    
    
    private float shadowFactor=getPrefs().getFloat("PawTrackerStereoBoard3.shadowFactor",0.3f);
    private float colorizeFactor=getPrefs().getFloat("PawTrackerStereoBoard3.colorizeFactor",0.1f);
    private int colorizePeriod=getPrefs().getInt("PawTrackerStereoBoard3.colorizePeriod",183);
    
    
    private int zFactor=getPrefs().getInt("PawTrackerStereoBoard3.zFactor",1);
    private float valueMargin=getPrefs().getFloat("PawTrackerStereoBoard3.valueMargin",0.3f);
    
 //   private int disparity_range=getPrefs().getInt("PawTrackerStereoBoard3.disparity_range",50);
    
    private int cube_size=getPrefs().getInt("PawTrackerStereoBoard3.cube_size",1);
    
   
   
    private int retinaSize=getPrefs().getInt("PawTrackerStereoBoard3.retinaSize",128);
    
    private int intensityZoom = getPrefs().getInt("PawTrackerStereoBoard3.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for tracker window");}
    
    
    private float brightness=getPrefs().getFloat("PawTrackerStereoBoard3.brightness",1f);
    {setPropertyTooltip("brightness","brightness or increase of display for accumulated values");}
    
    
    private float correctLeftAngle=getPrefs().getFloat("PawTrackerStereoBoard3.correctLeftAngle",0.0f);
    private float correctRightAngle=getPrefs().getFloat("PawTrackerStereoBoard3.correctRightAngle",0.0f);
    
    
    private boolean useFastMatching = getPrefs().getBoolean("PawTrackerStereoBoard3.useFastMatching",true);
    private boolean showYColor = getPrefs().getBoolean("PawTrackerStereoBoard3.showYColor",false);
    private boolean showXColor = getPrefs().getBoolean("PawTrackerStereoBoard3.showXColor",false);
    private boolean showZColor = getPrefs().getBoolean("PawTrackerStereoBoard3.showZColor",false);
 //   private boolean showShadows = getPrefs().getBoolean("PawTrackerStereoBoard3.showShadows",false);
 //   private boolean showCorner = getPrefs().getBoolean("PawTrackerStereoBoard3.showCorner",false);
    
    private boolean highlightDecay = getPrefs().getBoolean("PawTrackerStereoBoard3.highlightDecay",false);
    
    
    
    private boolean correctY = getPrefs().getBoolean("PawTrackerStereoBoard3.correctY",false);
    private boolean useFilter = getPrefs().getBoolean("PawTrackerStereoBoard3.useFilter",false);
    
 //   private boolean useLarge = getPrefs().getBoolean("PawTrackerStereoBoard3.useLarge",false);
    
    
    private boolean showFingers = getPrefs().getBoolean("PawTrackerStereoBoard3.showFingers",true);
    private boolean showFingersRange = getPrefs().getBoolean("PawTrackerStereoBoard3.showFingersRange",false);

 //   private boolean showFingerTips = getPrefs().getBoolean("PawTrackerStereoBoard3.showFingerTips",true);
    
    private boolean showZones = getPrefs().getBoolean("PawTrackerStereoBoard3.showZones",true);
    private boolean showAll = getPrefs().getBoolean("PawTrackerStereoBoard3.showAll",true);
    // show intensity inside shape
    
    private boolean showAcc = getPrefs().getBoolean("PawTrackerStereoBoard3.showAcc",false);
    private boolean showOnlyAcc = getPrefs().getBoolean("PawTrackerStereoBoard3.showOnlyAcc",false);
    private boolean showDecay = getPrefs().getBoolean("PawTrackerStereoBoard3.showDecay",false);
    
    
    private boolean scaleAcc = getPrefs().getBoolean("PawTrackerStereoBoard3.scaleAcc",true);
    
    private boolean showCage = getPrefs().getBoolean("PawTrackerStereoBoard3.showCage",true);
  //  private boolean showFrame = getPrefs().getBoolean("PawTrackerStereoBoard3.showFrame",true);
    private boolean show2DWindow = getPrefs().getBoolean("PawTrackerStereoBoard3.show2DWindow",true);
    private boolean show3DWindow = getPrefs().getBoolean("PawTrackerStereoBoard3.show3DWindow",true);
    private boolean showPlayer = getPrefs().getBoolean("PawTrackerStereoBoard3.showPlayer",true);
  //  private boolean showScore = getPrefs().getBoolean("PawTrackerStereoBoard3.showScore",false);
    private boolean showRetina = getPrefs().getBoolean("PawTrackerStereoBoard3.showRetina",false);
  
    private boolean trackFingers = getPrefs().getBoolean("PawTrackerStereoBoard3.trackFingers",false);
  
    
    //  private boolean showShapePoints = getPrefs().getBoolean("PawTrackerStereoBoard3.showShapePoints",true);
    //   private boolean showFingerPoints = getPrefs().getBoolean("PawTrackerStereoBoard3.showFingerPoints",true);
    
    
    
    //   private boolean showShape = getPrefs().getBoolean("PawTrackerStereoBoard3.showShape",true);
    private boolean showRLColors = getPrefs().getBoolean("PawTrackerStereoBoard3.showRLColors",false);
    private boolean showAxes = getPrefs().getBoolean("PawTrackerStereoBoard3.showAxes",true);
    
    
    private int lowFilter_radius=getPrefs().getInt("PawTrackerStereoBoard3.lowFilter_radius",3);
    private int lowFilter_density=getPrefs().getInt("PawTrackerStereoBoard3.lowFilter_density",17);
    private float lowFilter_threshold=getPrefs().getFloat("PawTrackerStereoBoard3.lowFilter_threshold",0);
    
  //  private int lowFilter_radius2=getPrefs().getInt("PawTrackerStereoBoard3.lowFilter_radius2",10);
 //   private int lowFilter_density2=getPrefs().getInt("PawTrackerStereoBoard3.lowFilter_density2",5);
    
    private boolean showCorrectionMatrix = getPrefs().getBoolean("PawTrackerStereoBoard3.showCorrectionMatrix",false);
    private boolean showCorrectionGradient = getPrefs().getBoolean("PawTrackerStereoBoard3.showCorrectionGradient",false);
    
    private boolean showRight = getPrefs().getBoolean("PawTrackerStereoBoard3.showRight",false);
    
//    private boolean showPalm = getPrefs().getBoolean("PawTrackerStereoBoard3.showPalm",false);
    
//    private boolean showSkeletton = getPrefs().getBoolean("PawTrackerStereoBoard3.showSkeletton",false);
//    private boolean showSecondFilter = getPrefs().getBoolean("PawTrackerStereoBoard3.showSecondFilter",false);
//    private boolean showTopography = getPrefs().getBoolean("PawTrackerStereoBoard3.showTopography",false);
    
    
    private boolean restart=getPrefs().getBoolean("PawTrackerStereoBoard3.restart",false);
    
    private boolean resetPawTracking=getPrefs().getBoolean("PawTrackerStereoBoard3.resetPawTracking",false);
    private boolean resetClusters=getPrefs().getBoolean("PawTrackerStereoBoard3.resetClusters",false);
  
    
    
//    private boolean validateParameters=getPrefs().getBoolean("PawTrackerStereoBoard3.validateParameters",false);
    
    private float event_strength=getPrefs().getFloat("PawTrackerStereoBoard3.event_strength",2f);
    
    private float decayTimeLimit=getPrefs().getFloat("PawTrackerStereoBoard3.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
    private boolean decayOn = getPrefs().getBoolean("PawTrackerStereoBoard3.decayOn",false);
    {setPropertyTooltip("decayOn","switch on/off decaying accumulated image");}
    
    
    private boolean notCrossing = getPrefs().getBoolean("PawTrackerStereoBoard3.notCrossing",false);
    
    
    private float finger_mix=getPrefs().getFloat("PawTrackerStereoBoard3.finger_mix",0.5f);
    private int finger_surround=getPrefs().getInt("PawTrackerStereoBoard3.finger_surround",10);
    
    private boolean useGroups = getPrefs().getBoolean("PawTrackerStereoBoard3.useGroups",false);
    
    private boolean goThroughMode = getPrefs().getBoolean("PawTrackerStereoBoard3.goThroughMode",false);
    private boolean useCorrections = getPrefs().getBoolean("PawTrackerStereoBoard3.useCorrections",true);
    private int tracker_lifeTime=getPrefs().getInt("PawTrackerStereoBoard3.tracker_lifeTime",10000);
    private int tracker_prelifeTime=getPrefs().getInt("PawTrackerStereoBoard3.tracker_prelifeTime",1000);
    private int tracker_viable_nb_events=getPrefs().getInt("PawTrackerStereoBoard3.tracker_viable_nb_events",100);
   
       
    private float expansion_mix=getPrefs().getFloat("PawTrackerStereoBoard3.expansion_mix",0.5f);
    private float trackerSubsamplingDistance=getPrefs().getFloat("PawTrackerStereoBoard3.trackerSubsamplingDistance",10);//cm?
    private boolean recordTrackerData = getPrefs().getBoolean("PawTrackerStereoBoard3.recordTrackerData",false);
    private boolean logDataEnabled = getPrefs().getBoolean("PawTrackerStereoBoard3.logDataEnabled",false);
 
    
    private float plane_tracker_mix=getPrefs().getFloat("PawTrackerStereoBoard3.plane_tracker_mix",0.5f);
    private boolean trackZPlane = getPrefs().getBoolean("PawTrackerStereoBoard3.trackZPlane",false);
 
    private boolean detectGrasp = getPrefs().getBoolean("PawTrackerStereoBoard3.detectGrasp",false);
    private boolean checkNose = getPrefs().getBoolean("PawTrackerStereoBoard3.checkNose",false);
 
    private int start_min_events=getPrefs().getInt("PawTrackerStereoBoard3.start_min_events",10000);
    private int start_z_displacement=getPrefs().getInt("PawTrackerStereoBoard3.start_z_displacement",100);
    private int grasp_timelength_min=getPrefs().getInt("PawTrackerStereoBoard3.grasp_timelength_min",200000);
 
    // epipolar
    private boolean correctEpipolar = getPrefs().getBoolean("PawTrackerStereoBoard3.correctEpipolar",true);
 
    
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
        int x;
        int y;
        int z;
        
        public Point(){
            x = 0;
            y = 0;
            z = 0;
        }
        
        public Point(int x, int y, int z){
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
        
        public Point rotateOnX( int yRotationCenter, int zRotationCenter, float angle){
              int yr = rotateYonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              int zr = rotateZonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              return new Point(x,yr,zr);
        }
        
        public Point rotateOnY( int xRotationCenter, int zRotationCenter, float angle){
              int xr = rotateXonY( x, z,  xRotationCenter,  zRotationCenter,  angle);
              int zr = rotateZonY( x, z,  xRotationCenter,  zRotationCenter,  angle);              
              return new Point(xr,y,zr);
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
        
        Integer groupLabel = new Integer(0);
        Integer groupLabel2 = new Integer(0);;
        
        
        // right
        // int accumulation;
        //float free;
        int attachedTo = NO_LINK;
        int attachedTo2 = NO_LINK;
        
        int updateTime; // time of current update
        int previousUpdate; // time of previous update
        
     
        float previousShortFilteredValue = 0;
        float previousDecayedFilteredValue = 0;
        
        float decayedFilteredValue;         // third low pass filter with decaying values
        float previousValue=0;             // last contract value
        float lastValue=0;                // last contract value
        float accValue=0;                // accumulated contrast value
  //      boolean linkPawBack = false;    // when finger is attached to paw back at this point
   //     boolean onFingerLine = false;  // if is part of a finger line
  //      boolean isSkeletton = false;  // true if part of the skeletton of paw obtained by thinnning
  //      boolean isEndNode = false;   // true if end of segment of skeletton points
        float shortFilteredValue;   // short range topographic filter
        float largeFilteredValue;  // large range topographic filter
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
            previousValue = lastValue;
            //  int type=e.getType();
            int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            // paul test opposite polariry
            //  int type=e.polarity==BinocularEvent.Polarity.Off? 1: 0;
            
            
            // optimize: could be moved somewhere else :
         //   float step = event_strength / (colorScale + 1);
            lastValue = step * (type - grayValue);
            
            //    System.out.println("type "+type);
                        
            //  accValue +=  lastValue;
          //  if (decayOn) accValue =  lastValue + getAccValue(e.timestamp);
          //  else 
            
            
              // important test: to use also negative events
            // test 1: invert value
             // test 2 : gives sign to event
            if(sign==0){
                
                //update sign
             //   if (lastValue<0) sign = -1;
                if (lastValue<valueThreshold) sign = -1;
                else sign = 1;
            }
            accValue +=  lastValue*sign;
            
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
                    newLabel();
                }
                if(x+1<retinaSize){
                    eventPoints[x+1][y].updateLabelFrom(this);
                }
                // RIGHT_MOST_METHOD
                if(x+1<retinaSize){
                    updateLabel2From(eventPoints[x+1][y]);
                } else {
                    newLabel2();
                }
                if(x-1>=0){
                    eventPoints[x-1][y].updateLabel2From(this);
                }
                
            } else {
                noLabel();
            }
            
        }

        
        
        public void updateLabelFrom( EventPoint ep ){
         
            if(getValue(currentTime)>valueThreshold){
                if(ep.getValue(currentTime)>valueThreshold){
                    if(groupLabel.intValue() == 0){
                        if( ep.groupLabel.intValue()==0) {
                            newLabel();
                        } else {                           
                            groupLabel = ep.groupLabel;
                        }
                    } else {
                        if(ep.groupLabel.intValue() != 0){                                                        
                            if(groupLabel.intValue()>ep.groupLabel.intValue()){
                                groupLabel = ep.groupLabel;
                            } else {
                                ep.groupLabel = groupLabel;
                            }
                        }
                    }
                } else {
                    if(groupLabel.intValue() == 0){
                        newLabel();
                    }
                }
            } else {
//                if(groupLabel.intValue() == 0){
//                    newLabel();
//                }
                noLabel();
            }
        }
        
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
        public void newLabel(){
            labelNumber++;
            groupLabel = new Integer(labelNumber);
        }
        public void newLabel2(){
            labelNumber++;
            groupLabel2 = new Integer(labelNumber);
        }
        
        public void noLabel(){
            
            groupLabel = new Integer(0);
            groupLabel2 = new Integer(0);
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
            
            return shortFilteredValue-decayedValue(shortFilteredValue,currentTime-updateTime);
            
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
        
        public float getDecayedFilteredValue( int currentTime ){
            return decayedFilteredValue-decayedValue(decayedFilteredValue,currentTime-updateTime);
        }
        
        public float getPreviousShortFilteredValue( int currentTime ){
            return previousShortFilteredValue-decayedValue(previousShortFilteredValue,currentTime-previousUpdate);
        }
        
        public int getX0( int method ){
            if(changed)  {
                computeXYZ0( LEFT_MOST_METHOD );
                computeXYZ0( RIGHT_MOST_METHOD );
            }
            if (method==LEFT_MOST_METHOD)
                return x0;
            else return x0r;
            
        }
        
        public int getY0( int method ){
            if(changed)  {
                computeXYZ0( LEFT_MOST_METHOD );
                computeXYZ0( RIGHT_MOST_METHOD );
            }
            if (method==LEFT_MOST_METHOD)
                return y0;
            else return y0r;
        }
        
        public int getZ0( int method ){
            if(changed)  {
                computeXYZ0( LEFT_MOST_METHOD );
                computeXYZ0( RIGHT_MOST_METHOD );
            }
            if (method==LEFT_MOST_METHOD){
                
                return z0;
            } else {
                
                return z0r;
            }
        }
        
          public void computeXYZ0( int method ){
            changed = false;
            
            int dx = disparityLink;
            if(method==RIGHT_MOST_METHOD) dx = disparityLink2;
            
          
            
            if(dx>NO_LINK){ // if matched point exists
                // dx is coordinate of matched pixel in other retina
                
                /** http://local.wasp.uwa.edu.au/~pbourke/geometry/lineline3d/
                 * Calculate the line segment PaPb that is the shortest route between
                 * two lines P1P2 and P3P4. Calculate also the values of mua and mub where
                 * Pa = P1 + mua (P2 - P1)
                 * Pb = P3 + mub (P4 - P3)
                 * Return FALSE if no solution exists.
                 */
                
                // result
                long xt = 0;
                long yt = 0;
                long zt = 0;
                
                // P1
                Point p1;
                Point p3;
                                                                                            
                if(side==LEFT){
                    p1 = new Point(leftPoints[x][y].xr,leftPoints[x][y].yr,leftPoints[x][y].zr);
                    p3 = new Point(rightPoints[dx][y].xr,rightPoints[dx][y].yr,rightPoints[dx][y].zr);                                       
                } else {
                    p1 = new Point(leftPoints[dx][y].xr,leftPoints[dx][y].yr,leftPoints[dx][y].zr);
                    p3 = new Point(rightPoints[x][y].xr,rightPoints[x][y].yr,rightPoints[x][y].zr);                   
                }
                
                // p3 is left focal point
                Point p2 = new Point(left_focal_x,left_focal_y,left_focal_z);
                // p4 is right focal point
                Point p4 = new Point(right_focal_x,right_focal_y,right_focal_z);
                
                Point pr = triangulate(p1,p2,p3,p4);
                        
                // store results for both methods
                
                if(method==RIGHT_MOST_METHOD){
                    x0r = pr.x;
                    y0r = pr.y;
                    z0r = pr.z;                                                           
                } else {
                    x0 = pr.x;
                    y0 = pr.y;
                    z0 = pr.z;      
                }
                
                
//                if(side==LEFT){
//                    if(method==RIGHT_MOST_METHOD){
//                        if(z0r<minleftZ){
//                            minleftZ = z0r;
//                            System.out.println("minleftZ: "+minleftZ);
//                        }
//                    } else {
//                        if(z0<minleftZ){
//                            minleftZ = z0;
//                            
//                            System.out.println("minleftZ: "+minleftZ);
//                        }
//                    }                                        
//                } else {
//                    if(method==RIGHT_MOST_METHOD){
//                        if(z0r<minrightZ){
//                            minrightZ = z0r;
//                            System.out.println("minrightZ: "+minrightZ);
//                        }
//                    } else {
//                        if(z0<minrightZ){
//                            minrightZ = z0;
//                            
//                            System.out.println("minrightZ: "+minrightZ);
//                        }
//                    }
//                    
//                }
            }
      
            
        }     
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
            float exp_mix = expansion_mix;// /100;
            if(mix>1) mix=1;
            if(mix<0) mix = 0;
            if(exp_mix>1) exp_mix=1;
            if(exp_mix<0) exp_mix = 0;
            this.time = time;
            this.x = Math.round(x*mix + this.x*(1-mix));
            this.y = Math.round(y*mix + this.y*(1-mix));
            this.z = Math.round(z*mix + this.z*(1-mix));
            
       //     int max_size = finger_surround * scaleFactor;
            //for ball tracker, hacked here
            // take remaining difference after cluster has moved             
            int diffx = Math.abs(this.x - x);// half size needed to incorporate new event
          
            float x_dist_weight = Math.round((float)Math.abs(x_size - diffx)/x_size);
         //   x_dist_weight = x_dist_weight * x_dist_weight;
            int diffy = Math.abs(this.y - y);// half size needed to incorporate new event
            float y_dist_weight = Math.round((float)Math.abs(y_size - diffy)/y_size);
         //   y_dist_weight = y_dist_weight * y_dist_weight;
            int diffz = Math.abs(this.z - z);// half size needed to incorporate new event
            float z_dist_weight = Math.round((float)Math.abs(z_size - diffz)/z_size);
        //    z_dist_weight = z_dist_weight * z_dist_weight;
            // need to weight size change by inverse of neasrest to cluster center?
            if(x_dist_weight>1) x_dist_weight=0;
         //   if(x_dist_weight<0) x_dist_weight = 0;
            if(y_dist_weight>1) y_dist_weight=0;
        //    if(y_dist_weight<0) y_dist_weight = 0;
            if(z_dist_weight>1) z_dist_weight=0;
        //    if(z_dist_weight<0) z_dist_weight = 0;
            float exp_mix_x = exp_mix*(1-x_dist_weight);
            float exp_mix_y = exp_mix*(1-y_dist_weight);
            float exp_mix_z = exp_mix*(1-z_dist_weight);
            x_size = Math.round(diffx*exp_mix_x + x_size*(1-exp_mix_x));
            y_size = Math.round(diffy*exp_mix_y + y_size*(1-exp_mix_y));
            z_size = Math.round(diffz*exp_mix_z + z_size*(1-exp_mix_z));
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
            //for ball tracker, hacked here
          //  x_size = 10; //plane_tracker_size;
          //  y_size = 10;
          //  z_size = 10;
            
        }
        
        public PlaneTracker( int z  ){
          //  activated = true;
       //     this.time = time;
            id = nbFingerClusters++;
           // this.x = x;
           // this.y = y;
            this.z = z;
            z0 = z;
            //for ball tracker, hacked here
         //   x_size = 10;
         //   y_size = 10;
          //  z_size = 10;
        }
        
        public void reset(){
            
         //   activated = false;
            //for ball tracker, hacked here
          //  x_size = 10;
          //  y_size = 10;
          //  z_size = 10;
            
          //  x = 0; //end tip
        //    y = 0;
            z = z0;
        }
        
        public void add( int x, int y, int z, float mix){
            
            if(isInSearchSpace(searchSpace,x,y,z)){  
       
                int zsp = zUnRotated(x,y,z);
 
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
    private int method=0;
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
    
    
    boolean windowDeleted = true;
    
  
    
    
    private int nbFingers = 0; //number of fingers tracked, maybe put it somewhere else
    private int nbFingerClusters = 1;//number of created trackers
    private int idFingerClusters = 1;//id of created trackers
    
//    protected FingerCluster[] fingers = new FingerCluster[MAX_NB_FINGERS];
    protected Vector<FingerCluster> fingers = new Vector();
    
    protected PlaneTracker planeTracker;
    
    
    // a bit hacked, for subsampling record of tracker data
    int prev_tracker_x = 0;
    int prev_tracker_y = 0;
    int prev_tracker_z = 0;
    
    /** Creates a new instance of PawTracker */
    public PawTrackerStereoBoard3(AEChip chip) {
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
        
        //  System.out.println("reset PawTrackerStereoBoard3 reset");
       
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
        
        compute3DParameters();
        
        // to remove:
        validateParameterChanges();
        
        //   resetCorrectionMatrix();
        // to remove:
        createCorrectionMatrix();
        
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
        planeTracker = new PlaneTracker(z);
        
         
     }
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
    
    
    
    
    
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){
        
       // if(isResetPawTracking()){
            // reset
          //  resetPawTracker();
           // return; //maybe continue then
      //  }
        
        int n=ae.getSize();
        if(n==0) return;
        
   //     if(validateParameters){
    //        validateParameterChanges();
            
    //    }
        
        float step = event_strength / (colorScale + 1);
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
        currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
            // BinocularEvent be=(BinocularEvent)e;
            
            processEvent(e);
            
            
        }
        
        clearDeadFingerTrackers(currentTime);
        
        
        printClusterData();
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }
        
        
    }
    
    public void printClusterData(){
         if(recordTrackerData){
                 
                 // extract x,y,z
             int n = 0;
             for(FingerCluster fc:fingers){
                 n++;
                 if(fc.nbEvents>tracker_viable_nb_events){ // >=
                     if((detectGrasp&&fc.isAGrasp)||!detectGrasp){
                         // if(!fingers.isEmpty()) {
                         //     FingerCluster fc = fingers.firstElement();
                         
                         // subsampling based on distance with previous record
                         if(distanceBetween(prev_tracker_x,prev_tracker_y,prev_tracker_z,fc.x,fc.y,fc.z)>trackerSubsamplingDistance*scaleFactor){
                             // update prev
                             prev_tracker_x = fc.x;
                             prev_tracker_y = fc.y;
                             prev_tracker_z = fc.z;
                             
                             int xsp = xUnRotated(fc.x,fc.y,fc.z);
                             int ysp = yUnRotated(fc.x,fc.y,fc.z);
                             int zsp = zUnRotated(fc.x,fc.y,fc.z);
                             
                             //rotation to correct?
                             int sizex = fc.x_size;
                             int sizey = fc.y_size;
                             int sizez = fc.z_size;//*2;
                             
                             //System.out.println(fc.x+" "+fc.y+" "+fc.z+" "+xsp+" "+ysp+" "+zsp+" "+sizex+" "+sizey+" "+sizez);
                             //System.out.println(currentTime+" "+xsp+" "+ysp+" "+zsp+" "+n);
                             String slog = new String(currentTime+" "+xsp+" "+ysp+" "+zsp+" "+fc.id+"\n");
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
        checkPlayerRecorderFrame();
        
        track(in);
        
        // if recording
        if(recordEnabled){
            doRecording();
            // write packet
            // create new packet
        }
        if(logDataEnabled){
            doLog();
        }
        
        if (showPlayer) playerRecorderFrame.repaint();
        if (show2DWindow) insideIntensityCanvas.repaint();
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
        scaleFactor = Math.round(1/pixel_size);
        
        // to center retina on middle point
        int halfRetinaSize = Math.round(retinaSize/2); 
          
        // for left retina, by default 0,0,0 is location of center of left retina
        // find focal point
        int z = Math.round(focal_length*scaleFactor);
        left_focal_x =  rotateXonY(0,z,0,0,-retina_angle);
        left_focal_z =  rotateZonY(0,z,0,0,-retina_angle);
        left_focal_y = halfRetinaSize;
        //update real 3D coordinat of all pixels
        // replace both  by stereopair.updateRealCoordinates(rd,ld=0,retina_angle)
        for (int i=0; i<leftPoints.length; i++){
            for (int j=0; j<leftPoints[i].length; j++){                
                leftPoints[i][j].xr = rotateXonY(halfRetinaSize-i-1,0,0,0,-retina_angle);
                leftPoints[i][j].yr = retinaSize-j-1;
                leftPoints[i][j].zr = rotateZonY(halfRetinaSize-i-1,0,0,0,-retina_angle);
            }
        }
      
        
         // for right retina, by default d,0,0 is location of center of right retina
        // find focal point
        int rd = Math.round(retinae_distance*scaleFactor);
        right_focal_x =  rotateXonY(rd,z,rd,0,retina_angle);
        right_focal_z =  rotateZonY(rd,z,rd,0,retina_angle);
        right_focal_y = halfRetinaSize;
        
         //update real 3D coordinat of all pixels
        for (int i=0; i<rightPoints.length; i++){
            for (int j=0; j<rightPoints[i].length; j++){
                rightPoints[i][j].xr = rotateXonY(rd+halfRetinaSize-i-1,0,rd,0,retina_angle);
                rightPoints[i][j].yr = retinaSize-j-1;
                rightPoints[i][j].zr = rotateZonY(rd+halfRetinaSize-i-1,0,rd,0,retina_angle);
            }
        }
        
        // compute cage's position
        int x_mid = Math.round(retinae_distance*scaleFactor/2);
        z = Math.round(cage_distance*scaleFactor);
        int base_height = Math.round(retina_height*scaleFactor - halfRetinaSize);
        cage = new Cage();
        cage.p1 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(-base_height),z);
        cage.p2 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(-base_height),z);
        cage.p3 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p4 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p5 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p6 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p7 = new Point(x_mid-Math.round(cage_door_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p8 = new Point(x_mid+Math.round(cage_door_width*scaleFactor/2),Math.round(cage_height*scaleFactor-base_height),z);
        cage.p9 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p10 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),z);
        cage.p11 = new Point(x_mid-Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
        cage.p12 = new Point(x_mid+Math.round(cage_width*scaleFactor/2),Math.round(cage_door_height*scaleFactor-base_height),Math.round((cage_distance-cage_platform_length)*scaleFactor));
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
        
        if(correctEpipolar) computeEpipolarDistance();
        
        
    } //end compute3DParameters
    
    
     private synchronized void computeEpipolarDistance(){
         
         int halfRetinaSize = Math.round(retinaSize/2); 
         
         // for left retina :
         // find most left point z : leftPoints[0][leftPoints[0].length].zr
         
         // get Oz : 
         int z = Math.round(focal_length*scaleFactor);
      
         // inverse focal point
         
         int left_o_x =  rotateXonY(0,-z,0,0,-retina_angle);
         int left_o_z =  rotateZonY(0,-z,0,0,-retina_angle);
         int left_o_y = halfRetinaSize;
         
         
         Point p2 = new Point(leftPoints[0][halfRetinaSize].xr,leftPoints[0][halfRetinaSize].yr,leftPoints[0][halfRetinaSize].zr);
         Point p1 = new Point(leftPoints[retinaSize-1][halfRetinaSize].xr,
                 leftPoints[retinaSize-1][halfRetinaSize].yr,
                 leftPoints[retinaSize-1][halfRetinaSize].zr);
         
         
         // p3 is left focal point
         Point p3 = new Point(left_o_x,left_o_y,left_o_z);
         // p4 is right focal point
         Point p4 = new Point(left_o_x+1,left_o_y,left_o_z);
         

        // now find E :
        left_epipole = triangulate(p1,p2,p3,p4);
        
        // find distance between e and p2
        
        epipolar_distance = distanceBetween(left_epipole.x,left_epipole.y,left_epipole.z,p2.x,p2.y,p2.z);
        
        Point p_far = new Point(leftPoints[retinaSize-1][retinaSize-1].xr,
                 leftPoints[retinaSize-1][retinaSize-1].yr,
                 leftPoints[retinaSize-1][retinaSize-1].zr);
        float epipolar_distance_max = distanceBetween(left_epipole.x,left_epipole.y,left_epipole.z,p_far.x,p_far.y,p_far.z);
        epipolar_distance_span = epipolar_distance_max - epipolar_distance;
       
        epipolar_angle_min = Math.atan(-halfRetinaSize/epipolar_distance);
        epipolar_angle_max = Math.atan(halfRetinaSize/epipolar_distance);
        epipolar_angle_span = epipolar_angle_max-epipolar_angle_min;
        
        // should be the same value for right retina
        // 
        
        int rd = Math.round(retinae_distance*scaleFactor);
        int right_o_x =  rotateXonY(rd,-z,rd,0,retina_angle);
        int right_o_z =  rotateZonY(rd,-z,rd,0,retina_angle);
        int right_o_y = halfRetinaSize;
        
         p1 = new Point(rightPoints[0][halfRetinaSize].xr,rightPoints[0][halfRetinaSize].yr,rightPoints[0][halfRetinaSize].zr);
         p2 = new Point(rightPoints[retinaSize-1][halfRetinaSize].xr,
                 rightPoints[retinaSize-1][halfRetinaSize].yr,
                 rightPoints[retinaSize-1][halfRetinaSize].zr);
         
         
         // p3 is left focal point
         p4 = new Point(right_o_x,right_o_y,right_o_z);
         // p4 is right focal point
         p3 = new Point(right_o_x-1,right_o_y,right_o_z);
         
            // now find E :
        right_epipole = triangulate(p1,p2,p3,p4);
//        
         
     }
    
    
    
    // to remove:
    
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
    
    // new  event function
    void new3DEvent( EventPoint ep, int method, int lead_side ){
        
       // System.out.println("new event x:"+ep.x+" y:"+ep.y+" v:"+ep.accValue+" t:"+ep.updateTime);
        if(trackZPlane){
          ep.setChanged(true); //maybe not
         
          planeTracker.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),plane_tracker_mix);
        }
        if(trackFingers){
             addToFingerTracker(ep,method);
             
             
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
        
        if(recordEnabled){
            boolean recordEvent = true;
           
            
            // record only visible events, if clearSpaceMode, only those inside area of interest
            ep.setChanged(true);
            if(clearSpaceMode){
                if(!isInSearchSpace(searchSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
                    recordEvent = false;
                }
            }
            if(recordEvent){
                
              
                
                
                int disp = -1;
                if (method==LEFT_MOST_METHOD){
                    disp = ep.disparityLink;
                } else {
                    disp = ep.disparityLink2;
                }
                
                if(recordPure3D){
                    
                    if(ep.getZ0(method)!=0) { //empty event for some reason, to look into
                        Event3D e = new Event3D(ep.getX0(method),ep.getY0(method),ep.getZ0(method),
                                ep.getValue(currentTime),currentTime);
                        loggingPacket.addEvent(e);
                    }
                } else {
                    // create 3d event, add to logging packet
                    
                    
//            Event3D e = new Event3D(ep.x,ep.y,disp,method,lead_side,
//                    ep.getValue(currentTime),ep.updateTime);
//
                    Event3D e = new Event3D(ep.x,ep.y,disp,method,lead_side,
                            ep.getValue(currentTime),currentTime);
                    loggingPacket.addEvent(e);
                }
                
            }
        }
        
    }
    
    // delete event function
     void remove3DEvent( EventPoint ep, int method, int lead_side ){
         
         if(recordEnabled){
             ep.setChanged(true);
             boolean recordEvent = true;
           //  if(ep.getZ0(method)==0) recordEvent = false; //empty event for some reason, to look into
             
             // record only visible events, if clearSpaceMode, only those inside area of interest
             if(clearSpaceMode){
                 if(!isInSearchSpace(searchSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
                     recordEvent = false;
                 }
             }
             if(recordEvent){
                 int disp = -1;
                 if (method==LEFT_MOST_METHOD){
                     disp = ep.prevDisparityLink*-1;
                 } else {
                     disp = ep.prevDisparityLink2*-1;
                 }
                 // create 3d event with zero value , add to logging packet
                 if(recordPure3D){
                    
                     if(ep.getZ0(method)!=0) { //empty event for some reason, to look into
                         Event3D e = new Event3D(ep.getX0(method),ep.getY0(method),ep.getZ0(method),
                                 0,currentTime);
                         loggingPacket.addEvent(e);
                     }
                 } else {
                     Event3D e = new Event3D(ep.x,ep.y,disp,method,lead_side,
                             0,currentTime); //currentTime?
                     loggingPacket.addEvent(e);
                 }
             }
         }
         
         
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
        
        if(isInSearchSpace(searchSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
            if(!checkNose||!isInSearchSpace(noseSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
                // find nearest
                //   FingerCluster fc = getNearestFinger(ep,finger_surround,method);
                Vector<FingerCluster>  fcv = getNearestFingerClusters(ep,finger_surround,method);
                //  if(fc==null){
                if(fcv.isEmpty()){
                    if(nbFingers<max_finger_clusters){
                        
                        FingerCluster fc = new FingerCluster(ep.getX0(method),ep.getY0(method),ep.getZ0(method),ep.updateTime);
                        fingers.add(fc);
                        //       System.out.println(currentTime+" create finger at: ["+ep.getX0(method)+","
                        //             +ep.getY0(method)+","+ep.getZ0(method)+"] with updateTime:"+ep.updateTime);
                        
                        
                        if(detectGrasp){
                            if(isInSearchSpace(startSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
                                fc.startLookingForGrasp();
                            }
                        }
                        nbFingers++;
                        
                        
                        
                    }// else {
                    //    System.out.println(currentTime+" cannot create new tracker: nbFingers="+nbFingers);
                    //  }
                } else {
                    //fc.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),finger_mix,ep.updateTime);
                    FingerCluster fc = fcv.firstElement();
                    int prev_x = fc.x;
                    int prev_y = fc.y;
                    int prev_z = fc.z;
                    fc.add(ep.getX0(method),ep.getY0(method),ep.getZ0(method),finger_mix,ep.updateTime);
                    
                    if(detectGrasp){
                        if(isInSearchSpace(endSpace,ep.getX0(method),ep.getY0(method),ep.getZ0(method))){
                            fc.endLookingForGrasp(ep.updateTime);
                        }
                    }
                    //fcv.remove(fc);
                    // push close neighbouring clusters away
                    //int surroundSq = finger_surround*finger_surround+16;
                    //for(FingerCluster fa:fcv){
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
    
    private FingerCluster getNearestFinger( EventPoint ep, int surround, int method ){
        float min_dist=Float.MAX_VALUE;
        FingerCluster closest=null;
        // float currentDistance=0;
        int surroundSq = surround*surround;
        float dist = min_dist;
        int dx = 0;
        int dy = 0;
        int dz  =0;
        StringBuffer sb = new StringBuffer();
        for(FingerCluster fc:fingers){
            if(fc!=null){
               // if(fc.activated){
                    dx = ep.getX0(method)-fc.x;
                    dy = ep.getY0(method)-fc.y;
                    dz = ep.getZ0(method)-fc.z;
                    dist = dx*dx + dy*dy + dz*dz;
                
                    if(dist<surroundSq){
                        if(dist<min_dist){
                            closest = fc;
                            min_dist = dist;
                        }
                    }
                     sb.append("getNearestFinger ep: ["+ep.getX0(method)+","+ep.getY0(method)+","+ep.getZ0(method)+
                      "] fc: ["+fc.x+","+fc.y+","+fc.z+"] dist="+dist+" surroundsq="+surroundSq+" mindist="+min_dist+"\n");
               
              //  }
            }
        }
        if(closest==null){
            System.out.println(sb);
           
        }
        return closest;
    }
    
    
    
      private Vector<FingerCluster> getNearestFingerClusters( EventPoint ep, int surround, int method ){
          
         return  getNearestFingerClusters(ep.getX0(method),ep.getY0(method),ep.getZ0(method),surround);
      }
      
      
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
             remove3DEvent(leadPoints[x][y],method,lead_side);
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
            remove3DEvent(leadPoints[x][y],method,lead_side);
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
                     remove3DEvent(leadPoints[ax][y],method,lead_side);
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
                     remove3DEvent(leadPoints[ax][y],method,lead_side);
                    
                    // updateAverageDepthAround(leadPoints,ax,yl,dispAvgRange);
                   // resetGCAround(leadPoints,ax,yl); // add leftMost method as parameter here?
                }
                
            }
        }
        
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
                            remove3DEvent(leadPoints[ax][y],method,lead_side);
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
                            // to put back!
                            new3DEvent(leadPoints[x][y],method,lead_side);
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
                                remove3DEvent(leadPoints[ax][y],method,lead_side);
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
                            remove3DEvent(leadPoints[ax][y],method,lead_side);
                            
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
                            
                              new3DEvent(leadPoints[x][y],method,lead_side);
                            
                            // updateAverageDepthAround(leadPoints,x,yl,dispAvgRange);
                           // addToGCAround(leadPoints,x,yl,dispAvgRange);
                            
                             // commented out to check speed of matching
                            // to put back!
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
                                 remove3DEvent(leadPoints[ax][y],method,lead_side);
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
    
    
    // processing one event
    protected void processEvent(BinocularEvent e){
        
        // resetEnabled = true;
        //int leftOrRight = e.side;
        int leftOrRight = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
        
        //   System.out.println("processEvent leftOrRight:"+leftOrRight+" e.eye:"+e.eye+" type:"+e.getType());
        
       int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
        // paul test opposite polariry
        //  int type=e.polarity==BinocularEvent.Polarity.Off? 1: 0;
     
        int dy = e.y;
        int dx = e.x;
       
        
        if(useCorrections){
            // shift y
            if(leftOrRight==LEFT){
                dy += yLeftCorrection;
                //  leftTime = e.timestamp;
            } else {
                dy += yRightCorrection;
                //   rightTime = e.timestamp;
            }
            
            // to add: shift x                   
        }

        
        if(correctEpipolar){
            // then compute angle instead of y, there should be retinaSize * "min angle-max angle" windows
            // and distance to epipole - min dist to be x, max dit - min dist fitting into retinaSize*classes
            
            // 1. compute angle for x,y            
            // 2. find category for angle
            // 3. dy = category(angle)
            int cy = epipolarAngleFor(dx,dy,leftOrRight);
            
            // 4. compute dist for x,y
            // 5. find category for dist-mindist (left or right)
            // 6. dx = category(dist)
            int cx = epipolarDistanceFor(dx,dy,leftOrRight);
            
            dx = cx;
            dy = cy;
        }
        
        float value = 0;
        
        if(dx==-1||dy==-1){
            return;
        }
        
        if(leftOrRight==LEFT){
            
            // if(type==1)  System.out.println("processEvent leftPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
            
            leftPoints[dx][dy].updateFrom(e,dx,dy,LEFT);
            
         
          
            // filter
            if(useFilter){
                fastDualLowFilterFrame(leftPoints[dx][dy], leftPoints, rightPoints );
                //      fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
                //       fastDualLowFilterFrame(leftPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
                //     fastDualLowFilterFrame(leftPoints2[e.x][cy], leftPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
            } else {
                
                // update group label
                leftPoints[dx][dy].updateLabel();
                
                value = leftPoints[dx][dy].getAccValue(currentTime);
                float previousValue = 0;
                if(type==0){
                    previousValue = value+1;
                }
                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                // processDisparity( leftOrRight, dx, dy,  value, type, RIGHT, true );
            }
        } else {
            
            // System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type);
            //  if(type==1) System.out.println("processEvent rightPoints add("+e.x+","+cy+") type:"+type+" etype1:"+e.getType()+" etype2:"+e.getType());
            
            
            
            rightPoints[dx][dy].updateFrom(e,dx,dy,RIGHT);
            
           
            // filter
            if(useFilter){
                fastDualLowFilterFrame(rightPoints[dx][dy], leftPoints, rightPoints );
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], leftPoints, rightPoints, RIGHT );
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, LEFT, true, leftPoints, rightPoints);
                //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, LEFT, false, leftPoints2, rightPoints2);
                //   fastDualLowFilterFrame(rightPoints[e.x][cy], rightPoints, leftOrRight, RIGHT, true, leftPoints, rightPoints);
                //   fastDualLowFilterFrame(rightPoints2[e.x][cy], rightPoints2, leftOrRight, RIGHT, false, leftPoints2, rightPoints2);
            } else {
                // update group label
                rightPoints[dx][dy].updateLabel();
                
                value = rightPoints[dx][dy].getAccValue(currentTime);
                float previousValue = 0;
                if(type==0){
                    previousValue = value+1;
                }
                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                processDisparity( leftOrRight, dx, dy,  value, previousValue, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                //   processDisparity( leftOrRight, dx, dy,  value, type, RIGHT, true );
            }
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
    
     private int epipolarDistanceFor( int x, int y, int leftOrRight){
       
        
       float d;
       int dc;
        // to do : create epipoles points in computeEpipoleDistance
        if(leftOrRight==LEFT){
            d = distanceBetween(leftPoints[x][y].xr,leftPoints[x][y].yr,leftPoints[x][y].zr,left_epipole.x,left_epipole.y,left_epipole.z);
            dc = distCategory(d);
            
        } else {
            d = distanceBetween(rightPoints[x][y].xr,rightPoints[x][y].yr,rightPoints[x][y].zr,right_epipole.x,right_epipole.y,right_epipole.z);
            
            dc = retinaSize - distCategory(d);
           
        }
          
    
       if(dc<0){
            System.out.println("Problem in epipolarDistanceFor("+x+","+y+","+leftOrRight+") : dist = "+d+" category="+dc);
            System.out.println("epipolar_distance = "+epipolar_distance+" , epipolar_distance_span = "+epipolar_distance_span);
            
            dc = -1;
        }
       if(dc>=retinaSize){
            System.out.println("Problem in epipolarDistanceFor("+x+","+y+","+leftOrRight+") : dist = "+d+" category="+dc);
            System.out.println("epipolar_distance = "+epipolar_distance+" , epipolar_distance_span = "+epipolar_distance_span);
            dc = -1;
        }
        
        return dc;
        
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
     
    private synchronized int xUnRotated(int x, int y, int z){
        return x;
        
    }
    private synchronized int yUnRotated(int x, int y, int z){
         int y_rx = rotateYonX( y, z, 0, 0, -retina_tilt_angle);
         return y_rx;
        
    }
    private synchronized int zUnRotated(int x, int y, int z){
         int z_rx = rotateZonX( y, z, 0, 0, -retina_tilt_angle);
         return z_rx;
    }

    
    private synchronized boolean isInSearchSpace( Zone zone, int x, int y, int z){
        boolean res = true;
        
        // Paul: to modify since we are now in real 3D, no need for rotation
        
        int y_rx = rotateYonX( y, z, 0, 0, -retina_tilt_angle);
        int z_rx = rotateZonX( y, z, 0, 0, -retina_tilt_angle);
        
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
     
        
        // get rid of nose // hack, to parametrize
          // if(y_rx>(grasp_max_elevation-(retina_height-cage_door_height))*scaleFactor){
        if(y_rx>zone.p3.y){
            res = false;
        }
       //   if(y>door_yc&&z>-door_z-5){
          //   res = false;
       //   }
        
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
     //   float dr = 0;
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
                            influencedPoint.previousDecayedFilteredValue = influencedPoint.decayedFilteredValue;
                            
                            //influencedPoint.previousUpdate = influencedPoint.updateTime;
                            
                            influencedPoint.shortFilteredValue += (ep.lastValue * f)/shortRangeTotal;
                            // use get..Value(time) to decay value
                            influencedPoint.decayedFilteredValue = influencedPoint.getDecayedFilteredValue(ep.updateTime) + (ep.lastValue * f)/shortRangeTotal;
                            influencedPoint.updateTime = ep.updateTime;
                            //influencedPoint.updateTime = ep.updateTime;
                            if (influencedPoint.shortFilteredValue<0) {
                                influencedPoint.shortFilteredValue = 0;
                                influencedPoint.decayedFilteredValue = 0;
                            }
                            
                            // update border status
                            // isOnPawShape(influencedPoint,ep.updateTime,eventPoints);
                            
                            // update group label status
                            influencedPoint.updateLabel();
                            
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
    
    protected float decayedValue( float value, int time ){
        float res=value;
        
        float dt = (float)time/decayTimeLimit;
        if(dt<0)dt = -dt;
        if(dt<1){
            res = value * dt;
        }
        return res;
    }
    
    
    void checkPlayerRecorderFrame(){
        if(showPlayer && playerRecorderFrame==null) createPlayerRecorderFrame();
    }
    
   
    void createPlayerRecorderFrame(){
       
         playerRecorderFrame = new AE3DPlayerRecorderFrame();
         playerRecorderFrame.setPlayer(this);
         playerRecorderFrame.setRecorder(this);
     //    dialogFrame.setPreferredSize(playerRecorderDialog.getPreferredSize());
      
     
       
      
        playerRecorderFrame.pack();
        playerRecorderFrame.setVisible(true);
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
                
                int jr = y;// - yRightCorrection;
                int jl = y;//- yLeftCorrection;
                if(jr>=0&&jr<leftPoints[x].length&&jl>=0&&jl<leftPoints[x].length&&x>=0&&x<leftPoints[x].length){
                    EventPoint epL = leftPoints[x][jl];
                    EventPoint epR = rightPoints[x][jr];
                    EventPoint epL2 = leftPoints[x][jl];
                    EventPoint epR2 = rightPoints[x][jr];
                    
                    float flr=-1;
                    float link=NO_LINK;
                    if (evt.getButton()==1){
                        switch(method){
                            case 0: highlight_xR = epL.disparityLink; break;
                            case 1: highlight_xR = epL.disparityLink2; break;
                            case 2: highlight_xR = epR.disparityLink; break;
                            case 3: highlight_xR = epR.disparityLink2; break;
                            default:;
                        }
                        // highlight_xR = epL.disparityLink;
                        //    if(highlight_xR>0&&highlight_xR<retinaSize){
                        //     EventPoint epLR = rightPoints[highlight_xR][jr];
                        
                        //      flr = epLR.getValue(currentTime);
                        
                        //   }
                    } else if (evt.getButton()==2){
                        switch(method){
                            case 0: highlight_xR = epR.attachedTo; break;
                            case 1: highlight_xR = epR.attachedTo2; break;
                            case 2: highlight_xR = epL.attachedTo; break;
                            case 3: highlight_xR = epL.attachedTo2; break;
                            default:;
                        }
                        //    highlight_xR = epR.attachedTo;
                        
                        //    if(highlight_xR>0&&highlight_xR<retinaSize){
                        //          EventPoint epLR = leftPoints[highlight_xR][jr];
                        //
                        //          flr = epLR.getValue(currentTime);
                        //         link = epLR.disparityLink;
                        //     }
                    }
                    
                    
                    
                    
                    float fr=0;
                    float fl=0;
                    
                    
                    fr = epR.getValue(currentTime);
                    fl = epL.getValue(currentTime);
                    
                    
                    float vll = 0;
                    float vlr = 0;
                    float vrl = 0;
                    float vrr = 0;
                    
                    int gll = 0;
                    int glr = 0;
                    int grl = 0;
                    int grr = 0;
                    
                    int dll = NO_LINK,dlr = NO_LINK,drl = NO_LINK,drr = NO_LINK;
                    if(epL.disparityLink>NO_LINK){
                        dll = epL.disparityLink - x;
                        EventPoint epLk = rightPoints[epL.disparityLink][jl];
                        vll = epLk.getValue(currentTime);
                        gll = epLk.groupLabel;
                    }
                    if(epL2.disparityLink2>NO_LINK){
                        dlr = epL2.disparityLink2 - x;
                        EventPoint epL2k = rightPoints[epL2.disparityLink2][jl];
                        vlr = epL2k.getValue(currentTime);
                        glr = epL2k.groupLabel2;
                    }
                    if(epR.disparityLink>NO_LINK){
                        drl = epR.disparityLink - x;
                        EventPoint epRk = leftPoints[epR.disparityLink][jr];
                        vrl = epRk.getValue(currentTime);
                        grl = epRk.groupLabel;
                    }
                    if(epR2.disparityLink2>NO_LINK){
                        drr = epR2.disparityLink2 - x;
                        EventPoint epR2k = leftPoints[epR2.disparityLink2][jr];
                        vrr = epR2k.getValue(currentTime);
                        grr = epR2k.groupLabel2;
                    }
                    
                    //    if (flr>-1) {
                    if (evt.getButton()==1){
                        System.out.println("LL("+x+","+jl+")="+fl+" z:"+dll+" linked to ("+epL.disparityLink+","+jl+")="+vll
                                +" label:"+epL.groupLabel+" to label:"+gll);
                        System.out.println("LR("+x+","+jl+")="+fl+" z:"+dlr+" linked to ("+epL2.disparityLink2+","+jl+")="+vlr
                                +" label2:"+epL2.groupLabel2+" to label2:"+glr);
                        System.out.println("RL("+x+","+jr+")="+fr+" z:"+drl+" linked to ("+epR.disparityLink+","+jl+")="+vrl
                                +" label:"+epR.groupLabel+" to label:"+grl);
                        System.out.println("RR("+x+","+jr+")="+fr+" z:"+drr+" linked to ("+epR2.disparityLink2+","+jl+")="+vrr
                                +" label2:"+epR2.groupLabel2+" to label2:"+grr);
                        
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
                    
                    
                    //System.out.println("+ label:"+epL.groupLabel+" label2:"+epL.groupLabel2);
                    
                    //   float rt = epR.updateTime;
                    //   float lt = epL.updateTime;
                    
                    //      System.out.println("left event time:"+lt+" lefttime: "+currentTime);
                    //      System.out.println("right event time:"+rt+" righttime: "+currentTime);
                    
                    if(testDrawing){
                        if (evt.getButton()==1){
                            leftPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                         //   leftPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            
                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, LEFT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, LEFT_MOST_METHOD,leftPoints, rightPoints );
                            processDisparity( LEFT, x, jl,  1.0f, 0, RIGHT, RIGHT_MOST_METHOD,leftPoints, rightPoints );
                            
                        } else if (evt.getButton()==2){
                            rightPoints[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                         //   rightPoints2[x][jl] = new EventPoint(x,jl,1.0f,epR.updateTime);
                            
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
                    if(y>=0&&y<correctionMatrix[0].length&&x>=0&&x<correctionMatrix.length){
                        
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
                            f = ep.getAccValue(currentTime);
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
                                        if(showLabels){
                                            float red = redFromLabel(ep.groupLabel.intValue());
                                            float green = greenFromLabel(ep.groupLabel.intValue());
                                            float blue = blueFromLabel(ep.groupLabel.intValue());
                                            gl.glColor3f(red,green,blue);
                                            //      gl.glColor3f(1,1,1);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        }
                                    } else if(redBlueShown==3){
                                        if(dLink>NO_LINK){
                                            gl.glColor3f(0.117f,0.565f,f);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                        //  gl.glColor3f(0,0,f);
                                        //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        if(showLabels){
                                            float red = redFromLabel(ep.groupLabel.intValue());
                                            float green = greenFromLabel(ep.groupLabel.intValue());
                                            float blue = blueFromLabel(ep.groupLabel.intValue());
                                            gl.glColor3f(red,green,blue);
                                            //      gl.glColor3f(1,1,1);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        }
                                    }
                                    
                                    
                                    
                                } else {
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
                                        if(showLabels){
                                            float red = redFromLabel(ep.groupLabel.intValue());
                                            float green = greenFromLabel(ep.groupLabel.intValue());
                                            float blue = blueFromLabel(ep.groupLabel.intValue());
                                            gl.glColor3f(red,green,blue);
                                            //      gl.glColor3f(1,1,1);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        }
                                    } else if(redBlueShown==3){
                                        if(dLink>NO_LINK){
                                            gl.glColor3f(f,0.5f,0);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                            
                                        }
                                        //gl.glColor3f(f,0,0);
                                        //gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        if(showLabels){
                                            float red = redFromLabel(ep.groupLabel.intValue());
                                            float green = greenFromLabel(ep.groupLabel.intValue());
                                            float blue = blueFromLabel(ep.groupLabel.intValue());
                                            gl.glColor3f(red,green,blue);
                                            //      gl.glColor3f(1,1,1);
                                            gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                                        }
                                    }
                                    
                                    
                                    
                                }
                            }
                            //  gl.glRectf(i*intensityZoom,(j+yCorrection)*intensityZoom,(i+1)*intensityZoom,(j+yCorrection+1)*intensityZoom);
                            //  gl.glRectf(i*intensityZoom,(j)*intensityZoom,(i+1)*intensityZoom,(j+1)*intensityZoom);
                            
                            
                            
                            
                            
                        }
                        
                    }
                }
                
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
                if(showCorrectionMatrix){
                    drawIntMatrix(correctionMatrix,gl);
                    
                    
                } else { //if(showAcc){
                    //  System.out.println("display left - right  showAcc");
                    
                    
                    //     drawEventPoints(leftPoints,gl,currentTime,true,0);
                    
                    
                    
                    if(method==0||method==4||method==6){
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
                    if(method==1||method==4||method==6){
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
                    if(method==2||method==5||method==6){
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
                    if(method==3||method==5||method==6){
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
        if(show3DWindow && a3DFrame==null||show3DWindow && windowDeleted) {
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

            /** draw points in 3D directly from preprocessed recorded data
             **/
            private void draw3DDisplayPacket( GL gl, int currentTime ){
                int x = 0;
                int y = 0;
                int z = 0;
                float f = 0;
                float fx = 0;
                float fy = 0;
                float fz = 0;
                
                
                // for all events in hashtable where key is x-y-z
                synchronized(direct3DEvents){
                    Event3D ev;
                    Iterator it = direct3DEvents.keySet().iterator();
                    try {
                        while(it.hasNext()){
                            //if(subsamplingPlayBackOfPure3D){
                            // implement it, by zapping some events
                            
                            
                            ev = (Event3D)direct3DEvents.get((String)it.next());
                            
                            x = ev.x0;
                            y = ev.y0;
                            z = ev.z0;
                            f = ev.value;
                            
                     /*       
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
                      **/
                            float b = 0.1f*brightness;
                            
                            
                            // display
                            if(clearSpaceMode){
                                if(isInSearchSpace(searchSpace,x,y,z)){
                                    if(searchSpaceMode){
                                        int xsp = xUnRotated(x,y,z);
                                        int ysp = yUnRotated(x,y,z);
                                        int zsp = zUnRotated(x,y,z);
                                        
                                        x = xsp;
                                        y = ysp;
                                        z = zsp;
                                        
                                    }
                                    
                                  //  shadowCube(gl, x, y, z*zFactor, cube_size, fz+b, fy+b, fx+b, alpha, shadowFactor);
                                     shadowCube(gl, x, y, z*zFactor, cube_size, f+b, f+b, f+b, alpha, shadowFactor);
                                   
                                }
                                
                            } else {
                              //   shadowCube(gl, x, y, z*zFactor, cube_size, fz+b, fy+b, fx+b, alpha, shadowFactor);
                                shadowCube(gl, x, y, z*zFactor, cube_size, f+b, f+b, f+b, alpha, shadowFactor);
                            }
                        }
                    } catch (java.util.ConcurrentModificationException e){
                        // hashtable updated at same time, exit
                        System.err.println(e.getMessage());
                        return;
                    }
                }
            }
            
            /** draw points in 3D from the two retina arrays and the information about matched disparities
             **/
            
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
                                                                                              
                                x0 = leadPoints[x][y].getX0(method);
                                y1 = leadPoints[x][y].getY0(method);
                                z0 = leadPoints[x][y].getZ0(method);
                                
//                                if(x0==0&&y1==0&&z0==0){
//                                    System.out.println("zero for x:"+x+" y:"+y+" dx:"+dx);
//                                }
                                
                                
                      //          if(searchSpaceMode){
//                                    int x0sp = xFromSearchSpace(x0,y,z0,zDirection);
//                                    int ysp = yFromSearchSpace(x0,y,z0,zDirection);
//                                    int z0sp = zFromSearchSpace(x0,y,z0,zDirection);
//                                    
//                                    x0 = x0sp;
//                                    y1 = ysp;
//                                    z0 = z0sp;
                                    
                         //       }
                                
                                //debug
                                //    leftPoints[x][y].z0=z0;
                                
                                boolean  highlighted = false;
                                if(highlight){
                                    //if(x==highlight_x&&y+yLeftCorrection==highlight_y){
                                    if(x==highlight_x&&y==highlight_y){
                                        
                                        shadowCube(gl, x0, y1, z0*zFactor, cube_size, 1, 1, 0, 1, shadowFactor);
                                        
                                        // + rays
                                        gl.glColor3f(1.0f,1.0f,0.0f);
                    
                                        line3D( gl,  leadPoints[x][y].xr,  leadPoints[x][y].yr,  leadPoints[x][y].zr,
                                                x0,  y1,  z0*zFactor);
                                        line3D( gl,  slavePoints[dx][y].xr,  slavePoints[dx][y].yr,  slavePoints[dx][y].zr,
                                                x0,  y1,  z0*zFactor);
                                    
                                    
                                         
                                        highlighted = true;
                                    }
                                }
                                
                                if (!highlighted){
                                    
                                   
                                        //gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                                        //gl.glEnable(gl.GL_BLEND);
                                       
                                    try {
                                        dl = leadPoints[x][y].getValue(leadTime); // or getAccValue()?
                                        
                                        dr = slavePoints[dx][y].getValue(slaveTime);
                                        
                                        float f = (dl+dr)/2;
                                        //f = f*brightness;
                                        if(colorizePeriod<1)colorizePeriod=1;
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
                                            if(isInSearchSpace(searchSpace,x0,y1,z0)){
                                                if(searchSpaceMode){
                                                    int x0sp = xUnRotated(x0,y1,z0);
                                                    int ysp = yUnRotated(x0,y1,z0);
                                                    int z0sp = zUnRotated(x0,y1,z0);

                                                    x0 = x0sp;
                                                    y1 = ysp;
                                                    z0 = z0sp;

                                                }
                                                
                                                shadowCube(gl, x0, y1, z0*zFactor, cube_size, fz+b+db, fy+b+db, fx+b, alpha, shadowFactor);
                                                
                                            }
                                            
                                        } else {
                                            
//                                            if(searchSpaceMode){
//                                                int x0sp = xFromSearchSpace(x0,y,z0,zDirection);
//                                                int ysp = yFromSearchSpace(x0,y,z0,zDirection);
//                                                int z0sp = zFromSearchSpace(x0,y,z0,zDirection);
//
//                                                x0 = x0sp;
//                                                y1 = ysp;
//                                                z0 = z0sp;
//
//                                            }
                                            
                                            // display test with sign //? to test again
                                            int sign = leadPoints[x][y].sign;
                                            if(sign>0)
                                            shadowCube(gl, x0, y1, z0*zFactor, cube_size, fz+b+db, fy+b+db, fx+b, alpha, shadowFactor);
                                            else
                                            shadowCube(gl, x0, y1, z0*zFactor, cube_size, fy+b+db, fx+b+db, fz+b, alpha, shadowFactor);
                                           
                                        }
                                    } catch(java.lang.NullPointerException ne){
                                        System.err.println(ne.getMessage()+" x:"+x+" y:"+y+" dx:"+dx);
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
                    
                    line3D( gl,  leftPoints[0][0].xr,  leftPoints[0][0].yr,  leftPoints[0][0].zr,   
                            leftPoints[0][retinaSize-1].xr,  leftPoints[0][retinaSize-1].yr,  leftPoints[0][retinaSize-1].zr);
                    line3D( gl,  leftPoints[0][0].xr,  leftPoints[0][0].yr,  leftPoints[0][0].zr,   
                            leftPoints[retinaSize-1][0].xr,  leftPoints[retinaSize-1][0].yr,  leftPoints[retinaSize-1][0].zr);
                    line3D( gl,  leftPoints[0][retinaSize-1].xr,  leftPoints[0][retinaSize-1].yr,  leftPoints[0][retinaSize-1].zr,   
                            leftPoints[retinaSize-1][retinaSize-1].xr,  leftPoints[retinaSize-1][retinaSize-1].yr,  leftPoints[retinaSize-1][retinaSize-1].zr);
                    line3D( gl,  leftPoints[retinaSize-1][0].xr,  leftPoints[retinaSize-1][0].yr,  leftPoints[retinaSize-1][0].zr,   
                            leftPoints[retinaSize-1][retinaSize-1].xr,  leftPoints[retinaSize-1][retinaSize-1].yr,  leftPoints[retinaSize-1][retinaSize-1].zr);
                    
                    line3D( gl,  rightPoints[0][0].xr,  rightPoints[0][0].yr,  rightPoints[0][0].zr,   
                            rightPoints[0][retinaSize-1].xr,  rightPoints[0][retinaSize-1].yr,  rightPoints[0][retinaSize-1].zr);
                    line3D( gl,  rightPoints[0][0].xr,  rightPoints[0][0].yr,  rightPoints[0][0].zr,   
                            rightPoints[retinaSize-1][0].xr,  rightPoints[retinaSize-1][0].yr,  rightPoints[retinaSize-1][0].zr);
                    line3D( gl,  rightPoints[0][retinaSize-1].xr,  rightPoints[0][retinaSize-1].yr,  rightPoints[0][retinaSize-1].zr,   
                            rightPoints[retinaSize-1][retinaSize-1].xr,  rightPoints[retinaSize-1][retinaSize-1].yr,  rightPoints[retinaSize-1][retinaSize-1].zr);
                    line3D( gl,  rightPoints[retinaSize-1][0].xr,  rightPoints[retinaSize-1][0].yr,  rightPoints[retinaSize-1][0].zr,   
                            rightPoints[retinaSize-1][retinaSize-1].xr,  rightPoints[retinaSize-1][retinaSize-1].yr,  rightPoints[retinaSize-1][retinaSize-1].zr);
                      
                    
                    shadowCube(gl, left_focal_x, left_focal_y, left_focal_z, 10, 0, 1, 0, 0.5f, 0);
                    shadowCube(gl, right_focal_x, right_focal_y, right_focal_z, 10, 0, 1, 0, 0.5f, 0);                        
                    
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
                
                  if(showZones){
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
                
             
           //     gl.glEnable(GL.GL_DEPTH_TEST); //enable depth testing
           //     gl.glDepthFunc(GL.GL_LEQUAL); //Type of depth function

              //  glu.gluLookAt(-200,-200,50,-200,-200,0,0.0,1.0,0.0);
                
                //System.out.println("display: system time:"+System.currentTimeMillis());
                
                if(leftDragged){
                    leftDragged = false;
                    tx = dragDestX-dragOrigX;
                    ty = dragOrigY-dragDestY;
                    
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
                if(goThroughMode) {
                 // gl.glTranslatef(ox-65,oy-25,-oz+tz-1250);
                    // parametrize this in function of pixel size and distance between retinae and open gl angle of vision
                    gl.glTranslatef(ox-1000,oy-25,-oz+tz-15000);
                    translation_x = ox-1000;
                    translation_y = oy-25;
                    translation_z = -oz+tz-15000;
                } else {
                    gl.glTranslatef(ox,oy,0.0f);
                    
                    translation_x = ox;
                    translation_y = oy;
                    translation_z = 0;
                    if(tz<1)tz=1;
                    gl.glScalef(tz,tz,-tz);
                }
                
                float rx = rOrigX+rtx;
                float ry = rOrigY+rty;
                              
             //   gl.glTranslatef(-translation_x,-translation_y,-translation_z);
   //             gl.glTranslatef(-translation_x,-translation_y,0);
                
                gl.glRotatef(ry+kry,1.0f,0.0f,0.0f);
                
                gl.glRotatef(rx+krx,0.0f,1.0f,0.0f);
                
          //      gl.glTranslatef(translation_x,translation_y,translation_z);
     //        gl.glTranslatef(translation_x,translation_y,0);
            
                // keyboard rotation :
                rOrigY += kry;
                rOrigX += krx;
                kry = 0;
                krx = 0;
           
                if(showAxes){
                    draw3DAxes(gl);
                }
                
             //   if(displayPlay){
               //     draw3DDisparityPacket(gl,inputPacket);
                   
              //  } else {
                    
                    
                    if(showAcc){
                    
                        if(displayDirect3D){
                            // display displayPacket
                            draw3DDisplayPacket( gl, currentTime );
                        } else {
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
                        
                    }
                    
             //   }
                
                if(showFingers||showFingersRange){
               //     float colorFinger = 0;
               //     float colorFinger2 = 0;
               ///     Color trackerColor = null;
                    try{
                        for(FingerCluster fc:fingers){
                            
                            
                            if(fc!=null){
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
                
                
                
                 if(trackZPlane){
                    
                    gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
               
                    gl.glColor4f(1.0f,0.0f,1.0f,0.9f);
                    // draw z plane at tracker's z
                    int x_mid = Math.round(retinae_distance*scaleFactor/2);
                    
                    int base_height = Math.round((cage_door_height-retina_height)*scaleFactor + retinaSize/2);
      
                    
                    int y1 = base_height;
                    int y2 = base_height+1000;
                    int z1 = planeTracker.z;
                    
                    int y1_rx = rotateYonX( y1, z1, 0, 0, retina_tilt_angle);
                    int y2_rx = rotateYonX( y2, z1, 0, 0, retina_tilt_angle);
                    int z1_rx = rotateZonX( y1, z1, 0, 0, retina_tilt_angle);
                    int z2_rx = rotateZonX( y2, z1, 0, 0, retina_tilt_angle);
    
                    rotatedRectangle2DFilled(gl,x_mid-500,x_mid+500,y1_rx,y2_rx,z1_rx,z2_rx);
                    
                    
                    gl.glDisable(gl.GL_BLEND);
                 }
                      
                
                
                //    if(showFrame){
                draw3DFrames(gl);
                //   }
             
                
                
                
                
                
                    gl.glPopMatrix();
                    gl.glFlush();
                
                    
                    
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
               if(goThroughMode) {
                    glu.gluPerspective(10.0,(double)width/(double)height,0.5,100000.0);
               
               } else {
                  gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
               }
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
            log.warning("null GL in PawTrackerStereoBoard3.annotate");
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
        getPrefs().putBoolean("PawTrackerStereoBoard3.logDataEnabled",logDataEnabled);
        
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
        
        getPrefs().putFloat("PawTrackerStereoBoard3.decayTimeLimit",decayTimeLimit);
    }
    public float getDecayTimeLimit() {
        return decayTimeLimit;
    }
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("PawTrackerStereoBoard3.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    
 
    
    public void setEvent_strength(float event_strength) {
        this.event_strength = event_strength;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.event_strength",event_strength);
    }
    public float getEvent_strength() {
        return event_strength;
    }
    
    
    
    
    public boolean isResetPawTracking() {
        return resetPawTracking;
    }
    public void setResetPawTracking(boolean resetPawTracking) {
        this.resetPawTracking = resetPawTracking;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.resetPawTracking",resetPawTracking);
        
        if(resetPawTracking){
            resetPawTracker();
        }
        
    }
    
      public boolean isResetClusters() {
        return resetClusters;
    }
    public void setResetClusters(boolean resetClusters) {
        this.resetClusters = resetClusters;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.resetClusters",resetClusters);
        
        if(resetClusters){
            resetClusterTrackers();
        }
        
    }
    
    public boolean isRestart() {
        return restart;
    }
    public void setRestart(boolean restart) {
        this.restart = restart;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.restart",restart);
        
    }
    
//    public boolean isValidateParameters() {
//        return validateParameters;
//    }
//    public void setValidateParameters(boolean validateParameters) {
//        this.validateParameters = validateParameters;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.validateParameters",validateParameters);
//        
//    }
    
    
    
    
    public void setShowCorrectionGradient(boolean showCorrectionGradient){
        this.showCorrectionGradient = showCorrectionGradient;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showCorrectionGradient",showCorrectionGradient);
    }
    public boolean getshowCorrectionGradient(){
        return showCorrectionGradient;
    }
    public void setShowCorrectionMatrix(boolean showCorrectionMatrix){
        this.showCorrectionMatrix = showCorrectionMatrix;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showCorrectionMatrix",showCorrectionMatrix);
    }
    public boolean getShowCorrectionMatrix(){
        return showCorrectionMatrix;
    }
    
    
    
//    
//    public void setShowSecondFilter(boolean showSecondFilter){
//        this.showSecondFilter = showSecondFilter;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showSecondFilter",showSecondFilter);
//    }
//    public boolean isShowSecondFilter(){
//        return showSecondFilter;
//    }
//    
    
    
    public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    
    
    public void setShowAcc(boolean showAcc){
        this.showAcc = showAcc;
        
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showAcc",showAcc);
    }
    public boolean isShowAcc(){
        return showAcc;
    }
    
    public void setShowOnlyAcc(boolean showOnlyAcc){
        this.showOnlyAcc = showOnlyAcc;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showOnlyAcc",showOnlyAcc);
    }
    public boolean isShowOnlyAcc(){
        return showOnlyAcc;
    }
    
    public void setShowDecay(boolean showDecay){
        this.showDecay = showDecay;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showDecay",showDecay);
    }
    public boolean isShowDecay(){
        return showDecay;
    }
    
    
    public void setUseFilter(boolean useFilter){
        this.useFilter = useFilter;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.useFilter",useFilter);
    }
    public boolean isUseFilter(){
        return useFilter;
    }
    
   
    
    
    public void setDecayOn(boolean decayOn){
        this.decayOn = decayOn;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.decayOn",decayOn);
    }
    public boolean isDecayOn(){
        return decayOn;
    }
    
//    
//    public void setShowFrame(boolean showFrame){
//        this.showFrame = showFrame;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showFrame",showFrame);
//    }
//    public boolean isShowFrame(){
//        return showFrame;
//    }
    
    public void setShowCage(boolean showCage){
        this.showCage = showCage;
        getPrefs().putBoolean("PawTrackerStereoBoard3.showCage",showCage);
    }
    public boolean isShowCage(){
        return showCage;
    }
    
    public void setShowRetina(boolean showRetina){
        this.showRetina = showRetina;
        getPrefs().putBoolean("PawTrackerStereoBoard3.showRetina",showRetina);
    }
    public boolean isShowRetina(){
        return showRetina;
    }
    
    
    public void setShow2DWindow(boolean show2DWindow){
        this.show2DWindow = show2DWindow;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.show2DWindow",show2DWindow);
    }
    public boolean isShow2DWindow(){
        return show2DWindow;
    }
      public void setShow3DWindow(boolean show3DWindow){
        this.show3DWindow = show3DWindow;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.show3DWindow",show3DWindow);
    }
    public boolean isShow3DWindow(){
        return show3DWindow;
    }
    
    
    public void setShowPlayer(boolean showPlayer){
        this.showPlayer = showPlayer;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showPlayer",showPlayer);
    }
    public boolean isShowPlayer(){
        return showPlayer;
    }
    
            
//    
//    public void setShowScore(boolean showScore){
//        this.showScore = showScore;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showScore",showScore);
//    }
//    public boolean isShowScore(){
//        return showScore;
//    }
//    
    public void setShowRight(boolean showRight){
        this.showRight = showRight;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showRight",showRight);
    }
    public boolean isShowRight(){
        return showRight;
    }
    
    
    
    
    public void setShowFingers(boolean showFingers){
        this.showFingers = showFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showFingers",showFingers);
    }
    public boolean isShowFingers(){
        return showFingers;
    }
    
     public void setShowFingersRange(boolean showFingersRange){
        this.showFingersRange = showFingersRange;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showFingersRange",showFingersRange);
    }
    public boolean isShowFingersRange(){
        return showFingersRange;
    }
//    
//    public void setShowFingerTips(boolean showFingerTips){
//        this.showFingerTips = showFingerTips;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showFingerTips",showFingerTips);
//    }
//    public boolean isShowFingerTips(){
//        return showFingerTips;
//    }
    
    public void setShowZones(boolean showZones){
        this.showZones = showZones;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showZones",showZones);
    }
    public boolean isShowZones(){
        return showZones;
    }
    public void setShowAll(boolean showAll){
        this.showAll = showAll;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showAll",showAll);
    }
    public boolean isShowAll(){
        return showAll;
    }
    
    public void setUseFastMatching(boolean useFastMatching){
        this.useFastMatching = useFastMatching;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.useFastMatching",useFastMatching);
    }
    public boolean isUseFastMatching(){
        return useFastMatching;
    }
    
    public void setShowRLColors(boolean showRLColors){
        this.showRLColors = showRLColors;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showRLColors",showRLColors);
    }
    public boolean isShowRLColors(){
        return showRLColors;
    }
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
    
    
    public int getLowFilter_radius() {
        return lowFilter_radius;
    }
    
    public void setLowFilter_radius(int lowFilter_radius) {
        this.lowFilter_radius = lowFilter_radius;
        
        getPrefs().putInt("PawTrackerStereoBoard3.lowFilter_radius",lowFilter_radius);
    }
    
    public int getLowFilter_density() {
        return lowFilter_density;
    }
    
    public void setLowFilter_density(int lowFilter_density) {
        this.lowFilter_density = lowFilter_density;
        
        getPrefs().putInt("PawTrackerStereoBoard3.lowFilter_density",lowFilter_density);
    }
    
    public float getLowFilter_threshold() {
        return lowFilter_threshold;
    }
    
    public void setLowFilter_threshold(float lowFilter_threshold) {
        this.lowFilter_threshold = lowFilter_threshold;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.lowFilter_threshold",lowFilter_threshold);
    }
//    
//    public int getLowFilter_radius2() {
//        return lowFilter_radius2;
//    }
//    
//    public void setLowFilter_radius2(int lowFilter_radius2) {
//        this.lowFilter_radius2 = lowFilter_radius2;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3.lowFilter_radius2",lowFilter_radius2);
//    }
//    
//    public int getLowFilter_density2() {
//        return lowFilter_density2;
//    }
//    
//    public void setLowFilter_density2(int lowFilter_density2) {
//        this.lowFilter_density2 = lowFilter_density2;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3.lowFilter_density2",lowFilter_density2);
//    }
    
    
    public float getBrightness() {
        return brightness;
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.brightness",brightness);
    }
    
    
    
    
//    
//    public float getPlaneAngle() {
//        return planeAngle;
//    }
//    public void setPlaneAngle(float planeAngle) {
//        this.planeAngle = planeAngle;
//        
//        getPrefs().putFloat("PawTrackerStereoBoard3.planeAngle",planeAngle);
//    }
   
    
//    
//    public float getPlatformAngle() {
//        return platformAngle;
//    }
//    public void setPlatformAngle(float platformAngle) {
//        this.platformAngle = platformAngle;
//        
//        getPrefs().putFloat("PawTrackerStereoBoard3.platformAngle",platformAngle);
//    }
    
    
    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.alpha",alpha);
    }
    public float getAlpha() {
        return alpha;
    }
    public void setIntensity(float intensity) {
        this.intensity = intensity;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.intensity",intensity);
    }
    public float getIntensity() {
        return intensity;
    }
    
    
    
//    public void setDispAvgRange(int dispAvgRange) {
//        this.dispAvgRange = dispAvgRange;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3.dispAvgRange",dispAvgRange);
//    }
//    public int getDispAvgRange() {
//        return dispAvgRange;
//    }
    
    
    
    
    public void setValueThreshold(float valueThreshold) {
        this.valueThreshold = valueThreshold;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.valueThreshold",valueThreshold);
    }
    public float getValueThreshold() {
        return valueThreshold;
    }
    
    
    
    public void setGrasp_max_elevation(int grasp_max_elevation) {
        this.grasp_max_elevation = grasp_max_elevation;
        compute3DParameters();
        getPrefs().putInt("PawTrackerStereoBoard3.grasp_max_elevation",grasp_max_elevation);
    }
    public int getGrasp_max_elevation() {
        return grasp_max_elevation;
    }
            
    public void setMax_finger_clusters(int max_finger_clusters) {
        this.max_finger_clusters = max_finger_clusters;
        
        getPrefs().putInt("PawTrackerStereoBoard3.max_finger_clusters",max_finger_clusters);
    }
    public int getMax_finger_clusters() {
        return max_finger_clusters;
    }
    public void setCage_distance(float cage_distance) {
        this.cage_distance = cage_distance;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_distance",cage_distance);
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
        getPrefs().putFloat("PawTrackerStereoBoard3.retina_tilt_angle",retina_tilt_angle);
    }
    
    public void setRetina_height(float retina_height) {
        this.retina_height = retina_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.retina_height",retina_height);
    }
    public float getRetina_height() {
        return retina_height;
    }
    public void setCage_door_height(float cage_door_height) {
        this.cage_door_height = cage_door_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_door_height",cage_door_height);
    }
    public float getCage_door_height() {
        return cage_door_height;
    }
    
    public void setCage_height(float cage_height) {
        this.cage_height = cage_height;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_height",cage_height);
    }
    public float getCage_height() {
        return cage_height;
    }
    public void setCage_width(float cage_width) {
        this.cage_width = cage_width;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_width",cage_width);
    }
    public float getCage_width() {
        return cage_width;
    }
    public void setCage_platform_length(float cage_platform_length) {
        this.cage_platform_length = cage_platform_length;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_platform_length",cage_platform_length);
    }
    public float getCage_platform_length() {
        return cage_platform_length;
    }
    public void setCage_door_width(float cage_door_width) {
        this.cage_door_width = cage_door_width;
        compute3DParameters();
        getPrefs().putFloat("PawTrackerStereoBoard3.cage_door_width",cage_door_width);
    }
    public float getCage_door_width() {
        return cage_door_width;
    }

    
    public void setYLeftCorrection(int yLeftCorrection) {
        this.yLeftCorrection = yLeftCorrection;
        
        getPrefs().putInt("PawTrackerStereoBoard3.yLeftCorrection",yLeftCorrection);
    }
    public int getYLeftCorrection() {
        return yLeftCorrection;
    }
    public void setYRightCorrection(int yRightCorrection) {
        this.yRightCorrection = yRightCorrection;
        
        getPrefs().putInt("PawTrackerStereoBoard3.yRightCorrection",yRightCorrection);
    }
    public int getYRightCorrection() {
        return yRightCorrection;
    }
    
    public void setYCurveFactor(float yCurveFactor) {
        this.yCurveFactor = yCurveFactor;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.yCurveFactor",yCurveFactor);
    }
    public float getYCurveFactor() {
        return yCurveFactor;
    }
    
    
    
    
    public void setColorizeFactor(float colorizeFactor) {
        this.colorizeFactor = colorizeFactor;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.colorizeFactor",colorizeFactor);
    }
    public float getColorizeFactor() {
        return colorizeFactor;
    }
    
    public void setShadowFactor(float shadowFactor) {
        this.shadowFactor = shadowFactor;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.shadowFactor",shadowFactor);
    }
    public float getShadowFactor() {
        return shadowFactor;
    }
    
    public void setZFactor(int zFactor) {
        this.zFactor = zFactor;
        
        getPrefs().putInt("PawTrackerStereoBoard3.zFactor",zFactor);
    }
    public int getZFactor() {
        return zFactor;
    }
    
    public void setValueMargin(float valueMargin) {
        this.valueMargin = valueMargin;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.valueMargin",valueMargin);
    }
    public float getValueMargin() {
        return valueMargin;
    }
    
    public void setCorrectLeftAngle(float correctLeftAngle) {
        this.correctLeftAngle = correctLeftAngle;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.correctLeftAngle",correctLeftAngle);
    }
    public float getCorrectLeftAngle() {
        return correctLeftAngle;
    }
    
    public void setCorrectRightAngle(float correctRightAngle) {
        this.correctRightAngle = correctRightAngle;
        
        getPrefs().putFloat("PawTrackerStereoBoard3.correctRightAngle",correctRightAngle);
    }
    public float getCorrectRightAngle() {
        return correctRightAngle;
    }
    
    
    
    
    
    public void setColorizePeriod(int colorizePeriod) {
        this.colorizePeriod = colorizePeriod;
        
        getPrefs().putInt("PawTrackerStereoBoard3.colorizePeriod",colorizePeriod);
    }
    public int getColorizePeriod() {
        return colorizePeriod;
    }
    
    
    public void setHighlightDecay(boolean highlightDecay){
        this.highlightDecay = highlightDecay;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.highlightDecay",highlightDecay);
    }
    public boolean isHighlightDecay(){
        return highlightDecay;
    }
    
    
    public void setShowZColor(boolean showZColor){
        this.showZColor = showZColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showZColor",showZColor);
    }
    public boolean isShowZColor(){
        return showZColor;
    }
    
    public void setShowYColor(boolean showYColor){
        this.showYColor = showYColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showYColor",showYColor);
    }
    public boolean isShowYColor(){
        return showYColor;
    }
    
    public void setShowXColor(boolean showXColor){
        this.showXColor = showXColor;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.showXColor",showXColor);
    }
    public boolean isShowXColor(){
        return showXColor;
    }
    
    
    public void setTrackFingers(boolean trackFingers){
        this.trackFingers = trackFingers;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.trackFingers",trackFingers);
    }
    public boolean isTrackFingers(){
        return trackFingers;
    }
    
//    public void setShowShadows(boolean showShadows){
//        this.showShadows = showShadows;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showShadows",showShadows);
//    }
//    public boolean isShowShadows(){
//        return showShadows;
//    }
//    public void setShowCorner(boolean showCorner){
//        this.showCorner = showCorner;
//        
//        getPrefs().putBoolean("PawTrackerStereoBoard3.showCorner",showCorner);
//    }
//    public boolean isShowCorner(){
//        return showCorner;
//    }
    
    public void setCorrectY(boolean correctY){
        this.correctY = correctY;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.correctY",correctY);
    }
    public boolean isCorrectY(){
        return correctY;
    }
    
    public void setCube_size(int cube_size) {
        this.cube_size = cube_size;
        
        getPrefs().putInt("PawTrackerStereoBoard3.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }
//    
//    public void setDisparity_range(int disparity_range) {
//        this.disparity_range = disparity_range;
//        
//        getPrefs().putInt("PawTrackerStereoBoard3.disparity_range",disparity_range);
//    }
//    public int getDisparity_range() {
//        return disparity_range;
//    }
    
    public void setNotCrossing(boolean notCrossing){
        this.notCrossing = notCrossing;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.notCrossing",notCrossing);
    }
    public boolean isNotCrossing(){
        return notCrossing;
    }
    
    public float getFinger_mix() {
        return finger_mix;
    }
    
    public void setFinger_mix(float finger_mix) {
        this.finger_mix = finger_mix;
        getPrefs().putFloat("PawTrackerStereoBoard3.finger_mix",finger_mix);
    }
    
    
    public float getExpansion_mix() {
        return expansion_mix;
    }
    
    public void setExpansion_mix(float expansion_mix) {
        this.expansion_mix = expansion_mix;
        getPrefs().putFloat("PawTrackerStereoBoard3.expansion_mix",expansion_mix);
    }
    
    public float getPlane_tracker_mix() {
        return plane_tracker_mix;
    }
    
    public void setPlane_tracker_mix(float plane_tracker_mix) {
        this.plane_tracker_mix = plane_tracker_mix;
        getPrefs().putFloat("PawTrackerStereoBoard3.plane_tracker_mix",plane_tracker_mix);
    }
    
    
    public float getTrackerSubsamplingDistance() {
        return trackerSubsamplingDistance;
    }
    
    public void setTrackerSubsamplingDistance(float trackerSubsamplingDistance) {
        this.trackerSubsamplingDistance = trackerSubsamplingDistance;
        getPrefs().putFloat("PawTrackerStereoBoard3.trackerSubsamplingDistance",trackerSubsamplingDistance);
    }
    
    
    public int getFinger_surround() {
        return finger_surround;
    }
    
    public void setFinger_surround(int finger_surround) {
        this.finger_surround = finger_surround;
        getPrefs().putInt("PawTrackerStereoBoard3.finger_surround",finger_surround);
    }
    
      
    public int getTracker_lifeTime() {
        return tracker_lifeTime;
    }
    
    public void setTracker_lifeTime(int tracker_lifeTime) {
        this.tracker_lifeTime = tracker_lifeTime;
        getPrefs().putInt("PawTrackerStereoBoard3.tracker_lifeTime",tracker_lifeTime);
    }
    
    public int getTracker_prelifeTime() {
        return tracker_prelifeTime;
    }
    
    public void setTracker_prelifeTime(int tracker_prelifeTime) {
        this.tracker_prelifeTime = tracker_prelifeTime;
        getPrefs().putInt("PawTrackerStereoBoard3.tracker_prelifeTime",tracker_prelifeTime);
    }
    
     public int getTracker_viable_nb_events() {
        return tracker_viable_nb_events;
    }
    
    public void setTracker_viable_nb_events(int tracker_viable_nb_events) {
        this.tracker_viable_nb_events = tracker_viable_nb_events;
        getPrefs().putInt("PawTrackerStereoBoard3.tracker_viable_nb_events",tracker_viable_nb_events);
    }
    
    
            
    public void setUseGroups(boolean useGroups){
        this.useGroups = useGroups;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.useGroups",useGroups);
    }
    public boolean isUseGroups(){
        return useGroups;
    }
    
    public void setGoThroughMode(boolean goThroughMode){
        this.goThroughMode = goThroughMode;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.goThroughMode",goThroughMode);
    }
    public boolean isGoThroughMode(){
        return goThroughMode;
    }
    
    public void setUseCorrections(boolean useCorrections){
        this.useCorrections = useCorrections;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.useCorrections",useCorrections);
    }
    public boolean isUseCorrections(){
        return useCorrections;
    }
    
     public float getFocal_length() {
        return focal_length;
    }
    
    public void setFocal_length(float focal_length) {
        this.focal_length = focal_length;
        getPrefs().putFloat("PawTrackerStereoBoard3.focal_length",focal_length);
        compute3DParameters();
    }
    
    
 

    public void setRetinae_distance(float retinae_distance) {
        this.retinae_distance = retinae_distance;
        getPrefs().putFloat("PawTrackerStereoBoard3.retinae_distance",retinae_distance);
        compute3DParameters();
    }
     public float getRetinae_distance() {
        return retinae_distance;
    }
    
    public void setPixel_size(float pixel_size) {
        this.pixel_size = pixel_size;
        getPrefs().putFloat("PawTrackerStereoBoard3.pixel_size",pixel_size);
        compute3DParameters();
    }
     public float getPixel_size() {
        return pixel_size;
    }
    
    public void setRetina_angle(float retina_angle) {
        this.retina_angle = retina_angle;
        getPrefs().putFloat("PawTrackerStereoBoard3.retina_angle",retina_angle);
        compute3DParameters();
    }
    public float getRetina_angle() {
        return retina_angle;
    }
    
    public void setRecordTrackerData(boolean recordTrackerData){
        this.recordTrackerData = recordTrackerData;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.recordTrackerData",recordTrackerData);
    }
    public boolean isRecordTrackerData(){
        return recordTrackerData;
    }
    
    public void setTrackZPlane(boolean trackZPlane){
        this.trackZPlane = trackZPlane;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.trackZPlane",trackZPlane);
    }
    public boolean isTrackZPlane(){
        return trackZPlane;
    }
    
    public void setDetectGrasp(boolean detectGrasp){
        this.detectGrasp = detectGrasp;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.detectGrasp",detectGrasp);
    }
    public boolean isDetectGrasp(){
        return detectGrasp;
    }
    
    public void setCheckNose(boolean checkNose){
        this.checkNose = checkNose;
        
        getPrefs().putBoolean("PawTrackerStereoBoard3.checkNose",checkNose);
    }
    public boolean isCheckNose(){
        return checkNose;
    }
    
    
    
    public int getStart_z_displacement() {
        return start_z_displacement;
    }
    
    public void setStart_z_displacement(int start_z_displacement) {
        this.start_z_displacement = start_z_displacement;
        getPrefs().putInt("PawTrackerStereoBoard3.start_z_displacement",start_z_displacement);
    }
    
    public int getStart_min_events() {
        return start_min_events;
    }
    
    public void setStart_min_events(int start_min_events) {
        this.start_min_events = start_min_events;
        getPrefs().putInt("PawTrackerStereoBoard3.start_min_events",start_min_events);
    }
    
      public int getGrasp_timelength_min() {
        return grasp_timelength_min;
    }
    
    public void setGrasp_timelength_min(int grasp_timelength_min) {
        this.grasp_timelength_min = grasp_timelength_min;
        getPrefs().putInt("PawTrackerStereoBoard3.grasp_timelength_min",grasp_timelength_min);
    }
    
      public synchronized boolean isCorrectEpipolar() {
        return correctEpipolar;
    }
    
    public synchronized void setCorrectEpipolar(boolean correctEpipolar) {
        this.correctEpipolar = correctEpipolar;
        if(correctEpipolar) computeEpipolarDistance();
        getPrefs().putBoolean("PawTrackerStereoBoard3.correctEpipolar",correctEpipolar);
    }
}
