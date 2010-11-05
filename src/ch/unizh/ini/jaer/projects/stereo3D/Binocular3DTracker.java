/*
 * Binocular3DTracker.java
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 *
 * Paul Rogister, Created on June, 2008
 *
 */


package ch.unizh.ini.jaer.projects.stereo3D;


import net.sf.jaer.chip.*;
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

import java.text.*;

/**
 * Binocular3DTracker:
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 * @author rogister
 */
public class Binocular3DTracker extends EventFilter2D implements Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
   
    int maxGCs = 100;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;

    private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);


    boolean windowDeleted = true;
    int currentTime = 0;
    private int displaySign=0;
    volatile boolean pngRecordEnabled = false;
    int frameNumber = 0;
    boolean TrackerisSet = false;
    boolean computed3D = false;
 //   Vector<Point> streamOf3DPoints = new Vector<Point>();

    Point[][] windowOn3DPoints = new Point[retinaSize][retinaSize];

    // coordinates of cameras pixel in the 3D world
    protected Point leftCameraPoints[][];
    protected Point rightCameraPoints[][];
    float[] retina_rotation = new float[9];
    float[] world_rotation = new float[9];
    float[] retina_translation = new float[3];
    float fc_left_dist = 0;
    float fc_right_dist = 0;
    private Point left_focal;
    private Point right_focal;
    int scaleFactor = 25; // pixels/mm
    private DVS3DModel retina3DLeft;
    private DVS3DModel retina3DRight;



    // Parameters appearing in the GUI
 
//    
//    
//    private int parameter2=getPrefs().getInt("Binocular3DTracker.paramter2",10);
//     {setPropertyTooltip("parameter2","useless parameter2");}
//  
//    
    private int intensityZoom = getPrefs().getInt("Binocular3DTracker.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for display window");}
   
    private int decayTimeLimit=getPrefs().getInt("Binocular3DTracker.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
  
    
 //   private int minEvents = getPrefs().getInt("Binocular3DTracker.minEvents",100);
//    {setPropertyTooltip("minEvents","min events to create GC");}
  
       
    private boolean showWindow = getPrefs().getBoolean("Binocular3DTracker.showWindow",true);

    private boolean showCamera = getPrefs().getBoolean("Binocular3DTracker.showRetina",true);
    private boolean showGrid = getPrefs().getBoolean("Binocular3DTracker.showGrid",true);
    private boolean showAxes = getPrefs().getBoolean("Binocular3DTracker.showAxes",true);
    private boolean showRange = getPrefs().getBoolean("Binocular3DTracker.showRange",true);

    boolean showVGrid = false;
   // hidden
    private float y_rotation_dist=getPrefs().getFloat("Binocular3DTracker.y_rotation_dist",25000);
    private float pixel_size=getPrefs().getFloat("Binocular3DTracker.pixel_size",0.04f);
    {setPropertyTooltip("pixel_size","pixel size of retina in mm");}
    private float retina_tilt_angle=getPrefs().getFloat("Binocular3DTracker.retina_tilt_angle",0.0f);
    {setPropertyTooltip("retina_tilt_angle","forward tilt angle of the retinae, in degrees");}
    private float retina_height=getPrefs().getFloat("Binocular3DTracker.retina_height",0.0f);
    {setPropertyTooltip("retina_height","height of retina lenses, in mm");}

    private int cube_size=getPrefs().getInt("Binocular3DTracker.cube_size",10);
    private float shadowFactor=getPrefs().getFloat("Binocular3DTracker.shadowFactor",0.3f);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
     private boolean invert = getPrefs().getBoolean("Binocular3DTracker.invert",false);
     private boolean ignoreZero = getPrefs().getBoolean("Binocular3DTracker.ignoreZero",false);



     // tracker variable



   private int tracker_surround=getPrefs().getInt("Binocular3DTracker.tracker_surround",50);
   private float expansion_mix=getPrefs().getFloat("Binocular3DTracker.expansion_mix",0.3f);
   private float track_mix=getPrefs().getFloat("Binocular3DTracker.track_mix",0.9f);

   private int tracker_lifeTime=getPrefs().getInt("Binocular3DTracker.tracker_lifeTime",10000);
   private int tracker_prelifeTime=getPrefs().getInt("Binocular3DTracker.tracker_prelifeTime",1000);
   private int tracker_viable_nb_events=getPrefs().getInt("Binocular3DTracker.tracker_viable_nb_events",20);

   private int nbMaxClusters=getPrefs().getInt("Binocular3DTracker.nbMaxClusters",1);

 //  private int nbMaxClusters = 1;
   //Cluster3DTracker[] trackers = new Cluster3DTracker[nbMaxClusters];
   Vector<Cluster3DTracker> trackers = new Vector();

   private int nbClusters = 0;
   private int idClusters = 0;

   boolean recording = false;
   int filecounter = 0;
   TrackerLogger tracklog = new TrackerLogger();
    // global variables
    
   
  
   
   protected float grayValue = 0.5f;
   float step = 0.33334f;
   
   boolean firstRun = true;
   
   public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
   
        
   

    
    /** Creates a new instance of GravityCentersImageDumper */
    public Binocular3DTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
                        
        //initFilter();
        
        chip.addObserver(this);
        
       
    }

      private void reset3DComputation(){

       System.out.println("reset3DComputation");

        // pb with reset and display : null pointer if happens at the same time

        leftCameraPoints = new Point[retinaSize][retinaSize];
        for (int i=0; i<leftCameraPoints.length; i++){
            for (int j=0; j<leftCameraPoints[i].length; j++){
                leftCameraPoints[i][j] = new Point();

            }
        }
        rightCameraPoints = new Point[retinaSize][retinaSize];
        for (int i=0; i<rightCameraPoints.length; i++){
            for (int j=0; j<rightCameraPoints[i].length; j++){
                rightCameraPoints[i][j] = new Point();

            }
        }

        TrackerisSet = true;
        compute3DParameters();

    }


    public void initFilter() {
        
    }
     
   
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularDisparityEvent> ae){
                      
        int n=ae.getSize();
        if(n==0) return;

         if(firstRun){
            // reset
            reset3DComputation();
            firstRun=false;
           // return; //maybe continue then
       }

  
        currentTime = ae.getLastTimestamp();

      
        for(BinocularDisparityEvent e:ae){
             int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          if(eye==LEFT){
           track3DCoordinatesFor(e);
          }
        }


         clearDeadTrackers(currentTime);
       
         logTrackerData();

                
    }

      public void logTrackerData(){
         if(recording){
             // extract x,y,z

             for (Cluster3DTracker tk : trackers) {

                 if (tk.nbEvents > tracker_viable_nb_events) { // >=

                     String slog = new String(currentTime + " " + tk.x + " " + tk.y + " " + tk.z + " " + tk.id + "\n");
                     tracklog.log(slog);

                 }
             }
         }

    }

    protected String generateTrackerFilename(){
        filecounter++;

        String filename = "livelog.txt";
        if(chip.getAeViewer().getAePlayer().getAEInputStream()!=null){

        filename = chip.getAeViewer().getAePlayer().getAEInputStream().getFile().getPath();//   .getName();

        int idat = filename.indexOf(".");
        int logTime = chip.getAeViewer().getAePlayer().getAEInputStream().getCurrentStartTimestamp();
        filename = new String(filename.substring(0,idat) + "-" + logTime + "-" + filecounter + ".txt");
        }
        return filename;
    }

    protected void decayStoreStream( Vector<Point> instream ) {
        Vector<Point> toBeRemoved = new Vector<Point>();
        for (Point p : instream) {

            if (currentTime - p.time > decayTimeLimit) {
                // remove
                toBeRemoved.add(p);

            }

        }
        // maybe do not remove here but in main loop, that would be neater
        instream.removeAll(toBeRemoved);
    }

    protected void track3DCoordinatesFor(BinocularDisparityEvent e){
        // store in some array or vector after computing 3D coodinates
        if(ignoreZero){
            if(e.disparity==0){
                return;
            }
        }
        int disparity = e.disparity;
        if(invert){
            disparity = -disparity;
        }
         if(e.x-disparity>0&&e.x-disparity<retinaSize){

            Point p1 = new Point(leftCameraPoints[e.x][e.y].x,leftCameraPoints[e.x][e.y].y,leftCameraPoints[e.x][e.y].z);
            // maybe change disparity sign if camera inverted, actually check if cameras are inverted in ryad's setup
            Point p3 = new Point(rightCameraPoints[e.x-disparity][e.y].x,rightCameraPoints[e.x-disparity][e.y].y,rightCameraPoints[e.x-disparity][e.y].z);


            // p3 is left focal point
            Point p2 = new Point(left_focal.x,left_focal.y,left_focal.z);
            // p4 is right focal point
            Point p4 = new Point(right_focal.x,right_focal.y,right_focal.z);

            Point pr = triangulate(p1,p2,p3,p4); //,x,y);
            pr.time = e.timestamp;
            pr.type = e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            windowOn3DPoints[e.x][e.y] = pr;
           // streamOf3DPoints.add(pr);

            // tracking
            Cluster3DTracker tracker = getNearestCluster(pr.x,pr.y,pr.z,tracker_surround);
            if(tracker!=null){
                tracker.add(pr);
            } else if(nbClusters<nbMaxClusters){
                // create new cluster
                tracker = new Cluster3DTracker();
                tracker.add(pr);
                //trackers[nbClusters] = tracker;
                trackers.add(tracker);
                nbClusters++;
            }

         }

    }

     protected Point getPointFor(int x, int y, int d, int time, int type ){
        // store in some array or vector after computing 3D coodinates
             if(x+d>0&&x+d<retinaSize){

                Point p1 = new Point(leftCameraPoints[x][y].x,leftCameraPoints[x][y].y,leftCameraPoints[x][y].z);
                Point p3 = new Point(rightCameraPoints[x+d][y].x,rightCameraPoints[x+d][y].y,rightCameraPoints[x+d][y].z);


                // p3 is left focal point
                Point p2 = new Point(left_focal.x,left_focal.y,left_focal.z);
                // p4 is right focal point
                Point p4 = new Point(right_focal.x,right_focal.y,right_focal.z);

                Point pr = triangulate(p1,p2,p3,p4); //,x,y);
                pr.time = time;
                pr.type = type;
                return pr;
             }
             return null;

    }


    public Point triangulate( Point p1, Point p2, Point p3, Point p4){ //, int x, int y  ){
        // result
        float xt = 0;
        float yt = 0;
        float zt = 0;

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

        xt = (float)(p1.x + mua * p21.x);
        yt = (float)(p1.y + mua * p21.y);
        zt = (float)(p1.z + mua * p21.z);

        Point p5 = new Point(xt,yt,zt);

    //    if(y<48&&y>43){
    //        System.out.println("["+x+","+y+"] xl "+p1.x+" yl "+p1.y+" zl "+p1.z+" with "+
   //                 "xr "+p3.x+" yr "+p3.y+" zr "+p3.z+" gives z "+zt);
   //     }

        return p5;

    }


          // load pixel -> voxels correpondances from file and put them all into VoxelTable
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

//
//    public Point findRealCoordinateFromRetina(float x, float y, int side){
//        Point res = new Point();
//        int halfRetinaSize = Math.round(retinaSize/2);
//        if(side==LEFT){
//            res.x = (x-halfRetinaSize);
//                res.y = (y-halfRetinaSize);
//                res.z = Math.round(fc_left_dist);
//                // rotate with world angle
//                res = res.rotateOnX(  0,  0,  -retina_tilt_angle);
//
//        } else {
//              res.x = Math.round(x-halfRetinaSize-retina_translation[0]);
//              res.y = Math.round(y-halfRetinaSize-retina_translation[1]);
//              res.z = Math.round(fc_right_dist-retina_translation[2]);
//              res.rotate(retina_rotation);
//              res = res.rotateOnX(  0,  0,  -retina_tilt_angle);
//
//        }
//
//        return res;
//    }

        // computing 3D parameters for resolution of 3D location of matched points
    private synchronized void compute3DParameters(){
        if(!TrackerisSet)return;
        // from pixel size, focal length, distance between retinas and angle of rotation of retina
        // we can determine x,y,z of focal point, and of all retina pixels
        // to have lighter computation when computing x,y,z of matched point

        // WE CHOOSE SCALE AS : 1 3D-pixel per retina pixel size
        scaleFactor = Math.round(1/pixel_size);

        // to center retina on middle point
        int halfRetinaSize = Math.round(retinaSize/2);

        // for left retina, by default 0,0,0 is location of center of left retina
        // find focal point
      //  int z = Math.round(focal_length*scaleFactor);

        loadCalibrationFromFile("calibstereo.txt");


    //    projDirection = new Point(0,0,1);
   //     projDirection = projDirection.rotateOnX(  0,  0,  -retina_tilt_angle);

        left_focal = new Point(0,0,0);

        Point lp = new Point();
        //update real 3D coordinat of all pixels
        // replace both  by stereopair.updateRealCoordinates(rd,ld=0,retina_angle)
         for (int i=0; i<leftCameraPoints.length; i++){
            for (int j=0; j<leftCameraPoints[i].length; j++){

                leftCameraPoints[i][j].x = (i-halfRetinaSize);
                leftCameraPoints[i][j].y = (j-halfRetinaSize);
                leftCameraPoints[i][j].z = Math.round(fc_left_dist);
                // rotate with world angle
                lp.x = leftCameraPoints[i][j].x;
                lp.y = leftCameraPoints[i][j].y;
                lp.z = leftCameraPoints[i][j].z;
                lp = lp.rotateOnX(  0,  0,  -retina_tilt_angle);
                leftCameraPoints[i][j].x = Math.round(lp.x); //still round?
                leftCameraPoints[i][j].y = Math.round(lp.y);
                leftCameraPoints[i][j].z = Math.round(lp.z);

               // leftCameraPoints[i][j].rotate(world_rotation);
            }
         }

       right_focal = new Point(-retina_translation[0],-retina_translation[1],-retina_translation[2]);
     //  right_focal = new Point(-retina_translation[0],-retina_translation[1],-retina_translation[2]);
       right_focal.rotate(retina_rotation);
       Point rp = new Point();

        rp.x = right_focal.x;
        rp.y = right_focal.y;
        rp.z = right_focal.z;
        rp = rp.rotateOnX(0, 0, -retina_tilt_angle);
        right_focal.x = rp.x;
        right_focal.y = rp.y;
        right_focal.z = rp.z;

         for (int i=0; i<rightCameraPoints.length; i++){
            for (int j=0; j<rightCameraPoints[i].length; j++){

                rightCameraPoints[i][j].x = Math.round(i-halfRetinaSize-retina_translation[0]);
                rightCameraPoints[i][j].y = Math.round(j-halfRetinaSize-retina_translation[1]);
                rightCameraPoints[i][j].z = Math.round(fc_right_dist-retina_translation[2]);

             //   if(i==0&&j==0)System.out.println("\n 1. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);

                rp.x = rightCameraPoints[i][j].x;
                rp.y = rightCameraPoints[i][j].y;
                rp.z = rightCameraPoints[i][j].z;
                rp.rotate(retina_rotation);
                rp = rp.rotateOnX(  0,  0,  -retina_tilt_angle);
                rightCameraPoints[i][j].x = Math.round(rp.x);
                rightCameraPoints[i][j].y = Math.round(rp.y);
                rightCameraPoints[i][j].z = Math.round(rp.z);


            //    if(i==0&&j==0)System.out.println("2. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);

              //  rightCameraPoints[i][j].rotate(world_rotation);

             //   rightRetinaCoordinates[i][j].z *= -1;

           //     if(i==0&&j==0)System.out.println("3. rightRetinaCoordinates "+rightRetinaCoordinates[i][j].x+" "+rightRetinaCoordinates[i][j].y+" "+rightRetinaCoordinates[i][j].z);


            }
        }

        // compute retina box
        retina3DLeft = new DVS3DModel();
        int boxsize = Math.round(retina3DLeft.size*scaleFactor/2);
        int lenssize = Math.round(retina3DLeft.lenseWidth*scaleFactor/2);
        int minzbox = Math.round(retina3DLeft.width*scaleFactor/2);

        retina3DLeft.p1 = new Point(-boxsize,-boxsize,-fc_left_dist-minzbox);
        retina3DLeft.p2 = new Point(-boxsize,boxsize,-fc_left_dist-minzbox);
        retina3DLeft.p3 = new Point(boxsize,-boxsize,-fc_left_dist-minzbox);
        retina3DLeft.p4 = new Point(boxsize,boxsize,-fc_left_dist-minzbox);
        retina3DLeft.p5 = new Point(-boxsize,-boxsize,-fc_left_dist+minzbox);
        retina3DLeft.p6 = new Point(-boxsize,boxsize,-fc_left_dist+minzbox);
        retina3DLeft.p7 = new Point(boxsize,-boxsize,-fc_left_dist+minzbox);
        retina3DLeft.p8 = new Point(boxsize,boxsize,-fc_left_dist+minzbox);
        retina3DLeft.p9 = new Point(-lenssize,-lenssize,-fc_left_dist+minzbox+retina3DLeft.lenseSize*scaleFactor);
        retina3DLeft.p10 = new Point(-lenssize,lenssize,-fc_left_dist+minzbox+retina3DLeft.lenseSize*scaleFactor);
        retina3DLeft.p11 = new Point(lenssize,-lenssize,-fc_left_dist+minzbox+retina3DLeft.lenseSize*scaleFactor);
        retina3DLeft.p12 = new Point(lenssize,lenssize,-fc_left_dist+minzbox+retina3DLeft.lenseSize*scaleFactor);

        retina3DLeft.tilt(-retina_tilt_angle);

        retina3DRight = new DVS3DModel();
        int xminboxsize = Math.round(-(retina3DLeft.size*scaleFactor/2) - retina_translation[0]);
        int xmaxboxsize = Math.round((retina3DLeft.size*scaleFactor/2) - retina_translation[0]);
        int yminboxsize = Math.round(-(retina3DLeft.size*scaleFactor/2) - retina_translation[1]);
        int ymaxboxsize = Math.round((retina3DLeft.size*scaleFactor/2) - retina_translation[1]);
        minzbox = Math.round(-retina3DLeft.width*scaleFactor/2-retina_translation[2]);
        int maxzbox = Math.round(retina3DLeft.width*scaleFactor/2-retina_translation[2]);

        retina3DRight.p1 = new Point(xminboxsize,yminboxsize,-fc_right_dist+minzbox);
        retina3DRight.p2 = new Point(xminboxsize,ymaxboxsize,-fc_right_dist+minzbox);
        retina3DRight.p3 = new Point(xmaxboxsize,yminboxsize,-fc_right_dist+minzbox);
        retina3DRight.p4 = new Point(xmaxboxsize,ymaxboxsize,-fc_right_dist+minzbox);
        retina3DRight.p5 = new Point(xminboxsize,yminboxsize,-fc_right_dist+maxzbox);
        retina3DRight.p6 = new Point(xminboxsize,ymaxboxsize,-fc_right_dist+maxzbox);
        retina3DRight.p7 = new Point(xmaxboxsize,yminboxsize,-fc_right_dist+maxzbox);
        retina3DRight.p8 = new Point(xmaxboxsize,ymaxboxsize,-fc_right_dist+maxzbox);
        retina3DRight.p9 = new Point(Math.round(-lenssize- retina_translation[0]),Math.round(-lenssize - retina_translation[1]),-fc_right_dist+maxzbox+retina3DRight.lenseSize*scaleFactor);
        retina3DRight.p10 = new Point(Math.round(-lenssize- retina_translation[0]),Math.round(lenssize - retina_translation[1]),-fc_right_dist+maxzbox+retina3DRight.lenseSize*scaleFactor);
        retina3DRight.p11 = new Point(Math.round(lenssize- retina_translation[0]),Math.round(-lenssize - retina_translation[1]),-fc_right_dist+maxzbox+retina3DRight.lenseSize*scaleFactor);
        retina3DRight.p12 = new Point(Math.round(lenssize- retina_translation[0]),Math.round(lenssize - retina_translation[1]),-fc_right_dist+maxzbox+retina3DRight.lenseSize*scaleFactor);

        retina3DRight.rotate(retina_rotation);
        retina3DRight.tilt(-retina_tilt_angle);






        computed3D = true;

    } //end compute3DParameters

    
    public String toString(){
        String s="Binocular3DTracker";
        return s;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    synchronized public void resetFilter() {
        if(!firstRun){
       // System.out.println ("Binocular3DTracker resetFilter ");


          //  streamOf3DPoints = new Vector<Point>();
            windowOn3DPoints = new Point[retinaSize][retinaSize];
            resetClusterTrackers();
        }
    }

      private void resetClusterTrackers(){
        trackers.clear(); // = new PawCluster[MAX_NB_FINGERS];
        nbClusters = 0;
      }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularDisparityEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
       
      
        
        
        check3DFrame();
        
        track(in);


         if(pngRecordEnabled){

            writeMovieFrame();
        }

        
         if (showWindow) a3DCanvas.repaint();
    //    if (showWindow) displayCanvas.repaint();



        return in;
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


       // show 3D view


    void check3DFrame(){
        if(showWindow && a3DFrame==null ) {
          
            create3DFrame();
        }
    }

    private static final GLU glu = new GLU();

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

    private boolean showRotationCube = false;


    private int fogMode;

    OpenGLCamera camera = new OpenGLCamera(0,-25,-25000);

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
                int but1mask = InputEvent.BUTTON1_DOWN_MASK;
                int but2mask = InputEvent.BUTTON2_DOWN_MASK;
                int but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    showRotationCube = false;
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
                        camera.setAt(0,-25,-25000);
                    } else {
                        // get final x,y for translation
                        dragOrigX = x;
                        dragOrigY = y;

                    }
                    //   System.out.println(" x:"+x+" y:"+y);
                    // System.out.println("Left mousePressed tx:"+tx+" ty:"+ty+" origX:"+origX+" origY:"+origY);

                }  else if ((e.getModifiersEx()&but2mask)==but2mask){
                    // get final x,y for depth translation
                    showRotationCube = false;
                    zdragOrigY = y;
                       System.out.println(" x:"+x+" y:"+y);
                  //   System.out.println("Middle mousePressed y:"+y+" zty:"+zty+" zOrigY:"+zOrigY);

                }else if ((e.getModifiersEx()&but3mask)==but3mask){
                    // get final x,y for rotation
                    camera.setRotationCenter(y_rotation_dist);
                    showRotationCube = true;
                    rdragOrigX = x;
                    rdragOrigY = y;
                     // System.out.println(" x:"+x+" y:"+y);
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
                y_rotation_dist -= notches*100;
                camera.setRotationCenter(y_rotation_dist);
                showRotationCube = true;
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
                        krx-=300;//speed
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_RIGHT){

                        // move
                        krx+=300;
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_DOWN){

                        // move
                        kry-=300;
                        a3DCanvas.display();
                    }
                    if(e.getKeyCode()==KeyEvent.VK_UP){

                        // move
                        kry+=300;
                        a3DCanvas.display();
                    }
                    if (e.getKeyCode() == KeyEvent.VK_PLUS) {

                        y_rotation_dist -= 100;
                        camera.setRotationCenter(y_rotation_dist);
                        showRotationCube = true;
                        tz += 1;
                        //System.out.println("mouse wheeled tz:"+tz);
                        a3DCanvas.display();
                    }
                      if (e.getKeyCode() == KeyEvent.VK_MINUS) {

                        y_rotation_dist += 100;
                        camera.setRotationCenter(y_rotation_dist);
                        showRotationCube = true;
                        tz -= 1;
                        //System.out.println("mouse wheeled tz:"+tz);
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
             //   if(e.getKeyCode()==KeyEvent.VK_SPACE){
                    // displaytime
               //     dopause = true;
//                    if(chip.getAeViewer().aePlayer.isPaused()){
//                        chip.getAeViewer().aePlayer.resume();
//                    } else {
//                        chip.getAeViewer().aePlayer.pause();
//                    }

            //    }

              

                 if(e.getKeyCode()==KeyEvent.VK_I){
                    // show color, red&blue, onlyred, only blue
                    displaySign++;
                    if(displaySign>2)displaySign=0;
                 }
               
                 if(e.getKeyCode()==KeyEvent.VK_P){
                    pngRecordEnabled = !pngRecordEnabled;
                    frameNumber = 0;
                    System.out.println("png recording: "+pngRecordEnabled);
                    //a3DCanvas.display();

                }


                if(e.getKeyCode()==KeyEvent.VK_W){
                   System.out.println("position : "+camera.toString());
                   System.out.println("y_rotation_dist: "+y_rotation_dist);
                }

                 if(e.getKeyCode()==KeyEvent.VK_G){
                   showVGrid = !showVGrid;
                }

                if(e.getKeyCode()==KeyEvent.VK_L){
                   if(recording){
                      tracklog.close();
                      recording = false;
                       System.out.println("Stop recording tracker positions");
                   } else {
                      String trackfilename = generateTrackerFilename();
                      tracklog.init(trackfilename);
                      recording = true;
                      System.out.println("Start recording tracker positions in "+trackfilename);
                   }
                }
            
                 if(e.getKeyCode()==KeyEvent.VK_1){
                   camera.setAt( 221.0f,-3164.2935f,4335.7695f);
                   //-287.0f,-3011.2935f,4335.7695f

                   a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_2){
                   camera.setAt(7482.0146f,-3002.6992f,8489.37f,-1.0f,-268.0f,0.0f);
                   a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_3){
                   camera.setAt(240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }
                 if(e.getKeyCode()==KeyEvent.VK_4){
                   camera.setAt(2279.035f,3250.848f,-8704.351f,-19.0f,7.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }

                 if(e.getKeyCode()==KeyEvent.VK_5){
                   camera.setAt(51954.676f,10383.381f,-32617.758f,-9.0f,54.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }
                
                if(e.getKeyCode()==KeyEvent.VK_6){
                   camera.setAt(94592.0f,14793.882f,11742.685f,-9.0f,91.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }
                
                 if(e.getKeyCode()==KeyEvent.VK_7){
                   camera.setAt(240.09888f,95144.164f,8396.5205f,-90.0f,0.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }

                 if(e.getKeyCode()==KeyEvent.VK_8){
                   camera.setAt(83086.59f,-995.6658f,18129.322f,0.0f,90.0f,0.0f);
                   //240.09888f,6644.1655f,8396.5205f,-90.0f,0.0f,0.0f
                   a3DCanvas.display();
                }

            
           //     94592.0 14793.882 11742.685 orientation: -9.0 91.0 0.0

//240.09888 6644.1655 8396.5205 orientation: -90.0 0.0 0.0
                //221.0 -3164.2935 4335.7695
              //  -1024.0 -2743.2935 4335.7695
                // position: -14386.751 -2873.724 8386.877 orientation: -1.0 -91.0 0.0

                //-287.0 -3011.2935 4335.7695 orientation: 0.0 0.0 0.0

                //-224.5466 6644.1655 8429.481 orientation: -90.0 1.0 0.0
//7482.0146f,-3002.6992f,8489.37f,-1.0f,-268.0f,0.0f

                // camera view  position: 2279.035 3250.848 -8704.351 orientation: -19.0 7.0 0.0

            }
        });

        a3DCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
                GL gl = drawable.getGL();
                gl.glEnable(GL.GL_DEPTH_TEST);
           

            }

            // draw ellipse in open gl, from http://courses.washington.edu/tcss458/winter2006/Chap3Ellipse.java
            void ellipseMidpoint( GL gl, float xCenter, float yCenter, float zCenter, float Rx, float Rz) {
                float Rx2 = Rx * Rx;
                float Rz2 = Rz * Rz;
                float twoRx2 = 2 * Rx2;
                float twoRz2 = 2 * Rz2;
                float p;
                float x = 0;
                float z = Rz;
                float px = 0;     // Initial value of midpoint parameter.
                float pz = twoRx2 * z;


                /* Plot the initial point in each Ellipse quadrant. */
                ellipsePlotPoints(gl,xCenter, yCenter, zCenter, x,z);

                /* Region 1 */
                p =  (Rz2 - (Rx2 * Rz) + (0.25f * Rx2));
                while (px < pz) {
                    x = x + 1;
                    px = px + twoRz2;
                    if (p < 0) {
                        p = p + Rz2 + px;
                    } else {
                        z = z - 1;
                        pz = pz - twoRx2;
                        p = p + Rz2 + px - pz;
                    }
                    ellipsePlotPoints(gl,xCenter, yCenter, zCenter, x,z);
                }

                /* Region 2 */
                p = (Rz2 * (x + 0.5f) * (x + 0.5f) + Rx2 * (z - 1) * (z - 1) - Rx2 * Rz2);
                while (z > 0) {
                    z = z - 1;
                    pz = pz - twoRx2;
                    if (p > 0) {
                        p = p + Rx2 - pz;
                    } else {
                        x = x + 1;
                        px = px + twoRz2;
                        p = p + Rx2 - pz + px;
                    }
                    ellipsePlotPoints(gl,xCenter, yCenter, zCenter, x,z);
                }
            }

            void ellipsePlotPoints( GL gl,float xCenter, float yCenter, float zCenter, float x, float z) {
                setPixel(gl,xCenter + x, yCenter,zCenter + z);
                setPixel(gl,xCenter - x, yCenter,zCenter + z);
                setPixel(gl,xCenter + x, yCenter,zCenter - z);
                setPixel(gl,xCenter - x, yCenter,zCenter - z);
            }

            void setPixel ( GL gl, float x, float y, float z){
               // System.out.println("setpixel "+x+", "+y+", "+z);
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex3f(x, y, z);// we want the ellipse parallel to ground
                gl.glEnd();
            }


             /** draw points in 3D from the two retina arrays and the information about matched disparities
             **/
            private void draw3DPoints( GL gl, Point[][] arrayOfPoints  ){
                
                
                for (int i = 0; i < retinaSize; i++) {
                    for (int j = 0; j < retinaSize; j++) {
                        Point p = arrayOfPoints[i][j];
                        if(p!=null){
                        if (currentTime - p.time < decayTimeLimit) {

                            // could add decay of visual intensity here
                            if(p.type==1){
                              shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 1, 0, 1, shadowFactor);
                            } else {
                               shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 0, 1, 1, shadowFactor);
                            }


                        } else { //erase
                            shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 0, 0, 0, 0);

                        }
                        }
                    }
                }
            }


        private void draw3DPoint( GL gl, Point p  ){
                if (p != null) {
                    if (currentTime - p.time < decayTimeLimit) {

                        // could add decay of visual intensity here
                        if (p.type == 1) {
                            shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 1, 0, 1, shadowFactor);
                        } else {
                            shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 0, 1, 1, shadowFactor);
                        }


                    } else { //erase
                        shadowCube(gl, p.x, p.y, p.z, cube_size, 0, 0, 0, 0, shadowFactor);

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

           private void draw3DAxes( GL gl, float x, float y, float z, float size ){
               //if(size<10)size=10;
              // if(size>200)size=200;
                // gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
                // gl.glEnable(gl.GL_BLEND);
                //  gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//x
                gl.glColor3f(0.0f,1.0f,0.0f);
                line3D( gl,  x,  y,  z,  x+size ,y ,z);
                //   gl.glColor4f(0.0f,1.0f,0.0f,0.2f);	//y
                gl.glColor3f(1.0f,1.0f,0.0f);
                line3D( gl,  x, y, z ,x, y+size ,z);
                //   line3D ( gl,  retinaSize/2, 5, 0,  retinaSize/2 , 5 ,0);
                //  gl.glColor4f(1.0f,0.0f,0.0f,0.2f);	//z
                gl.glColor3f(1.0f,0.0f,0.0f);
                line3D( gl,  x,  y,  z,  x ,y ,z+ size);
                //   gl.glDisable(gl.GL_BLEND);

            }



            private void draw3DFrames( GL gl ){
               // int half = retinaSize/2;


                //  System.out.println("middleAngle for planeAngle("+planeAngle+")= "+middleAngle);

                if(!computed3D)return;

                if(showCamera){

                   
                    gl.glColor3f(0.0f,0.0f,1.0f);
                    

                    line3D( gl,  leftCameraPoints[0][0].x,  leftCameraPoints[0][0].y,  leftCameraPoints[0][0].z,
                            leftCameraPoints[0][retinaSize-1].x,  leftCameraPoints[0][retinaSize-1].y,  leftCameraPoints[0][retinaSize-1].z);
                    line3D( gl,  leftCameraPoints[0][0].x,  leftCameraPoints[0][0].y,  leftCameraPoints[0][0].z,
                            leftCameraPoints[retinaSize-1][0].x,  leftCameraPoints[retinaSize-1][0].y,  leftCameraPoints[retinaSize-1][0].z);
                    line3D( gl,  leftCameraPoints[0][retinaSize-1].x,  leftCameraPoints[0][retinaSize-1].y,  leftCameraPoints[0][retinaSize-1].z,
                            leftCameraPoints[retinaSize-1][retinaSize-1].x,  leftCameraPoints[retinaSize-1][retinaSize-1].y,  leftCameraPoints[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  leftCameraPoints[retinaSize-1][0].x,  leftCameraPoints[retinaSize-1][0].y,  leftCameraPoints[retinaSize-1][0].z,
                            leftCameraPoints[retinaSize-1][retinaSize-1].x,  leftCameraPoints[retinaSize-1][retinaSize-1].y,  leftCameraPoints[retinaSize-1][retinaSize-1].z);

                    line3D( gl,  rightCameraPoints[0][0].x,  rightCameraPoints[0][0].y,  rightCameraPoints[0][0].z,
                            rightCameraPoints[0][retinaSize-1].x,  rightCameraPoints[0][retinaSize-1].y,  rightCameraPoints[0][retinaSize-1].z);
                    line3D( gl,  rightCameraPoints[0][0].x,  rightCameraPoints[0][0].y,  rightCameraPoints[0][0].z,
                            rightCameraPoints[retinaSize-1][0].x,  rightCameraPoints[retinaSize-1][0].y,  rightCameraPoints[retinaSize-1][0].z);
                    line3D( gl,  rightCameraPoints[0][retinaSize-1].x,  rightCameraPoints[0][retinaSize-1].y,  rightCameraPoints[0][retinaSize-1].z,
                            rightCameraPoints[retinaSize-1][retinaSize-1].x,  rightCameraPoints[retinaSize-1][retinaSize-1].y,  rightCameraPoints[retinaSize-1][retinaSize-1].z);
                    line3D( gl,  rightCameraPoints[retinaSize-1][0].x,  rightCameraPoints[retinaSize-1][0].y,  rightCameraPoints[retinaSize-1][0].z,
                            rightCameraPoints[retinaSize-1][retinaSize-1].x,  rightCameraPoints[retinaSize-1][retinaSize-1].y,  rightCameraPoints[retinaSize-1][retinaSize-1].z);


                    shadowCube(gl, left_focal.x, left_focal.y, left_focal.z, 10, 0, 1, 0, 0.5f, 0);
                    shadowCube(gl, right_focal.x, right_focal.y, right_focal.z, 10, 1, 0, 0, 0.5f, 0);


                }

                if(showGrid){

                     gl.glColor3f(0.2f,0.2f,0.2f);
                     //x
                     for (int xi=-25000;xi<25001;xi+=1000){
                         line3D( gl,  xi, (-retina_height)*scaleFactor-1000,  -25000,  xi, (-retina_height)*scaleFactor-1000, 25000);
                       //  line3D( gl,  xi, (-retina_height+cage_door_height)*scaleFactor,  cage_distance*scaleFactor-2500,  xi, (-retina_height+cage_door_height)*scaleFactor, cage_distance*scaleFactor+2500);
                     }
                     // z
                      for (int zi=-25000;zi<25001;zi+=1000){
                         line3D( gl, -25000, (-retina_height)*scaleFactor-1000, zi, 25000, (-retina_height)*scaleFactor-1000, zi);
                        // line3D( gl,  -2500, (-retina_height+cage_door_height)*scaleFactor,  zi+cage_distance*scaleFactor, 2500, (-retina_height+cage_door_height)*scaleFactor, cage_distance*scaleFactor+zi);

                     }

                }

                 if(showVGrid){

                     gl.glColor3f(1.0f, 0.0f, 0.0f);
                     int depth = -44;
                     Point g1 = getPointFor(90, 57, depth, currentTime, 1);
                     Point g2 = getPointFor(90, 5, depth, currentTime, 1);
                     //-60

                     line3D(gl, g1.x, g1.y, g1.z, g2.x, g2.y, g2.z);
                     depth = -38;
                     g1 = getPointFor(40, 90, depth, currentTime, 1);
                     g2 = getPointFor(40, 127, depth, currentTime, 1);
                     line3D(gl, g1.x, g1.y, g1.z, g2.x, g2.y, g2.z);

                     depth = -21;
                     g1 = getPointFor(105, 70, depth, currentTime, 1);
                     g2 = getPointFor(105, 90, depth, currentTime, 1);
                     line3D(gl, g1.x, g1.y, g1.z, g2.x, g2.y, g2.z);

                }

                if(showCamera){
                       gl.glColor3f(0.0f,0.0f,1.0f);
                        line3D( gl,  retina3DLeft.p1.x,  retina3DLeft.p1.y,  retina3DLeft.p1.z,  retina3DLeft.p2.x,  retina3DLeft.p2.y,  retina3DLeft.p2.z);
                        line3D( gl,  retina3DLeft.p1.x,  retina3DLeft.p1.y,  retina3DLeft.p1.z,  retina3DLeft.p3.x,  retina3DLeft.p3.y,  retina3DLeft.p3.z);
                        line3D( gl,  retina3DLeft.p2.x,  retina3DLeft.p2.y,  retina3DLeft.p2.z,  retina3DLeft.p4.x,  retina3DLeft.p4.y,  retina3DLeft.p4.z);
                        line3D( gl,  retina3DLeft.p3.x,  retina3DLeft.p3.y,  retina3DLeft.p3.z,  retina3DLeft.p4.x,  retina3DLeft.p4.y,  retina3DLeft.p4.z);

                        line3D( gl,  retina3DLeft.p5.x,  retina3DLeft.p5.y,  retina3DLeft.p5.z,  retina3DLeft.p6.x,  retina3DLeft.p6.y,  retina3DLeft.p6.z);
                        line3D( gl,  retina3DLeft.p5.x,  retina3DLeft.p5.y,  retina3DLeft.p5.z,  retina3DLeft.p7.x,  retina3DLeft.p7.y,  retina3DLeft.p7.z);
                        line3D( gl,  retina3DLeft.p6.x,  retina3DLeft.p6.y,  retina3DLeft.p6.z,  retina3DLeft.p8.x,  retina3DLeft.p8.y,  retina3DLeft.p8.z);
                        line3D( gl,  retina3DLeft.p7.x,  retina3DLeft.p7.y,  retina3DLeft.p7.z,  retina3DLeft.p8.x,  retina3DLeft.p8.y,  retina3DLeft.p8.z);

                        line3D( gl,  retina3DLeft.p1.x,  retina3DLeft.p1.y,  retina3DLeft.p1.z,  retina3DLeft.p5.x,  retina3DLeft.p5.y,  retina3DLeft.p5.z);
                        line3D( gl,  retina3DLeft.p2.x,  retina3DLeft.p2.y,  retina3DLeft.p2.z,  retina3DLeft.p6.x,  retina3DLeft.p6.y,  retina3DLeft.p6.z);
                        line3D( gl,  retina3DLeft.p3.x,  retina3DLeft.p3.y,  retina3DLeft.p3.z,  retina3DLeft.p7.x,  retina3DLeft.p7.y,  retina3DLeft.p7.z);
                        line3D( gl,  retina3DLeft.p4.x,  retina3DLeft.p4.y,  retina3DLeft.p4.z,  retina3DLeft.p8.x,  retina3DLeft.p8.y,  retina3DLeft.p8.z);


                        line3D( gl,  retina3DLeft.p5.x,  retina3DLeft.p5.y,  retina3DLeft.p5.z,  retina3DLeft.p9.x,  retina3DLeft.p9.y,  retina3DLeft.p9.z);
                        line3D( gl,  retina3DLeft.p6.x,  retina3DLeft.p6.y,  retina3DLeft.p6.z,  retina3DLeft.p10.x,  retina3DLeft.p10.y,  retina3DLeft.p10.z);
                        line3D( gl,  retina3DLeft.p7.x,  retina3DLeft.p7.y,  retina3DLeft.p7.z,  retina3DLeft.p11.x,  retina3DLeft.p11.y,  retina3DLeft.p11.z);
                        line3D( gl,  retina3DLeft.p8.x,  retina3DLeft.p8.y,  retina3DLeft.p8.z,  retina3DLeft.p12.x,  retina3DLeft.p12.y,  retina3DLeft.p12.z);

                        line3D( gl,  retina3DLeft.p9.x,  retina3DLeft.p9.y,  retina3DLeft.p9.z,  retina3DLeft.p11.x,  retina3DLeft.p11.y,  retina3DLeft.p11.z);
                        line3D( gl,  retina3DLeft.p9.x,  retina3DLeft.p9.y,  retina3DLeft.p9.z,  retina3DLeft.p10.x,  retina3DLeft.p10.y,  retina3DLeft.p10.z);
                        line3D( gl,  retina3DLeft.p12.x,  retina3DLeft.p12.y,  retina3DLeft.p12.z,  retina3DLeft.p11.x,  retina3DLeft.p11.y,  retina3DLeft.p11.z);
                        line3D( gl,  retina3DLeft.p12.x,  retina3DLeft.p12.y,  retina3DLeft.p12.z,  retina3DLeft.p10.x,  retina3DLeft.p10.y,  retina3DLeft.p10.z);

                        line3D( gl,  retina3DRight.p1.x,  retina3DRight.p1.y,  retina3DRight.p1.z,  retina3DRight.p2.x,  retina3DRight.p2.y,  retina3DRight.p2.z);
                        line3D( gl,  retina3DRight.p1.x,  retina3DRight.p1.y,  retina3DRight.p1.z,  retina3DRight.p3.x,  retina3DRight.p3.y,  retina3DRight.p3.z);
                        line3D( gl,  retina3DRight.p2.x,  retina3DRight.p2.y,  retina3DRight.p2.z,  retina3DRight.p4.x,  retina3DRight.p4.y,  retina3DRight.p4.z);
                        line3D( gl,  retina3DRight.p3.x,  retina3DRight.p3.y,  retina3DRight.p3.z,  retina3DRight.p4.x,  retina3DRight.p4.y,  retina3DRight.p4.z);

                        line3D( gl,  retina3DRight.p5.x,  retina3DRight.p5.y,  retina3DRight.p5.z,  retina3DRight.p6.x,  retina3DRight.p6.y,  retina3DRight.p6.z);
                        line3D( gl,  retina3DRight.p5.x,  retina3DRight.p5.y,  retina3DRight.p5.z,  retina3DRight.p7.x,  retina3DRight.p7.y,  retina3DRight.p7.z);
                        line3D( gl,  retina3DRight.p6.x,  retina3DRight.p6.y,  retina3DRight.p6.z,  retina3DRight.p8.x,  retina3DRight.p8.y,  retina3DRight.p8.z);
                        line3D( gl,  retina3DRight.p7.x,  retina3DRight.p7.y,  retina3DRight.p7.z,  retina3DRight.p8.x,  retina3DRight.p8.y,  retina3DRight.p8.z);

                        line3D( gl,  retina3DRight.p1.x,  retina3DRight.p1.y,  retina3DRight.p1.z,  retina3DRight.p5.x,  retina3DRight.p5.y,  retina3DRight.p5.z);
                        line3D( gl,  retina3DRight.p2.x,  retina3DRight.p2.y,  retina3DRight.p2.z,  retina3DRight.p6.x,  retina3DRight.p6.y,  retina3DRight.p6.z);
                        line3D( gl,  retina3DRight.p3.x,  retina3DRight.p3.y,  retina3DRight.p3.z,  retina3DRight.p7.x,  retina3DRight.p7.y,  retina3DRight.p7.z);
                        line3D( gl,  retina3DRight.p4.x,  retina3DRight.p4.y,  retina3DRight.p4.z,  retina3DRight.p8.x,  retina3DRight.p8.y,  retina3DRight.p8.z);

                        line3D( gl,  retina3DRight.p5.x,  retina3DRight.p5.y,  retina3DRight.p5.z,  retina3DRight.p9.x,  retina3DRight.p9.y,  retina3DRight.p9.z);
                        line3D( gl,  retina3DRight.p6.x,  retina3DRight.p6.y,  retina3DRight.p6.z,  retina3DRight.p10.x,  retina3DRight.p10.y,  retina3DRight.p10.z);
                        line3D( gl,  retina3DRight.p7.x,  retina3DRight.p7.y,  retina3DRight.p7.z,  retina3DRight.p11.x,  retina3DRight.p11.y,  retina3DRight.p11.z);
                        line3D( gl,  retina3DRight.p8.x,  retina3DRight.p8.y,  retina3DRight.p8.z,  retina3DRight.p12.x,  retina3DRight.p12.y,  retina3DRight.p12.z);

                        line3D( gl,  retina3DRight.p9.x,  retina3DRight.p9.y,  retina3DRight.p9.z,  retina3DRight.p11.x,  retina3DRight.p11.y,  retina3DRight.p11.z);
                        line3D( gl,  retina3DRight.p9.x,  retina3DRight.p9.y,  retina3DRight.p9.z,  retina3DRight.p10.x,  retina3DRight.p10.y,  retina3DRight.p10.z);
                        line3D( gl,  retina3DRight.p12.x,  retina3DRight.p12.y,  retina3DRight.p12.z,  retina3DRight.p11.x,  retina3DRight.p11.y,  retina3DRight.p11.z);
                        line3D( gl,  retina3DRight.p12.x,  retina3DRight.p12.y,  retina3DRight.p12.z,  retina3DRight.p10.x,  retina3DRight.p10.y,  retina3DRight.p10.z);


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


            private void drawTrackers( GL gl ){

                    try {
                        for (Cluster3DTracker tc : trackers) {


                            if (tc != null) {

                                    gl.glColor3f(1.0f, 0, 1.0f);

                                    rectangle3D(gl, tc.x, tc.y, tc.z, tc.x_size / 2, tc.y_size / 2, tc.z_size / 2);

                                    gl.glColor3f(1.0f, 0, 1.0f);


                            if(showRange){
                                    if (tc.nbEvents < tracker_viable_nb_events) {
                                        gl.glColor3f(1.0f, 1.0f, 0.0f);
                                    } else {
                                        gl.glColor3f(1.0f, 0.0f, 1.0f);
                                    }

                                    cube(gl, tc.x, tc.y, tc.z, tracker_surround / 2);
                            }


                            }
                        }
                    } catch (java.util.ConcurrentModificationException e) {
                    }


            }

            synchronized public void display(GLAutoDrawable drawable) {

                GL gl=drawable.getGL();
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glPushMatrix();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
                gl.glScalef(-1,1,1);

//                if (fogMode == GL.GL_EXP2) {
//                    gl.glFogf(GL.GL_FOG_START, 1.0f);
//                    gl.glFogf(GL.GL_FOG_END, 5.0f);
//                }
//                gl.glFogi(GL.GL_FOG_MODE, fogMode);

                if(leftDragged){
                    leftDragged = false;
                    tx = -(dragDestX - dragOrigX)*10;
                    ty = (dragDestY - dragOrigY)*10;
                    dragOrigX = dragDestX;
                    dragOrigY = dragDestY;
                  //  System.out.println("dragDestX: "+dragDestX+" dragOrigX: "+dragOrigX);

                  //  System.out.println("tx: "+tx+" ty: "+ty);
                    camera.move(glu,(float)tx,(float)ty,0.0f);

                }

                camera.move(glu,(float)krx,0.0f,(float)kry);

                if(middleDragged){
                    middleDragged = false;

                    zty = (zdragOrigY-zdragDestY)*10;
                    zdragOrigY = zdragDestY;
                   // zty = zty * 20;

                  //  System.out.println("Zty: "+zty);
                    camera.move(glu,0,0,(float)-zty);
                }
                if(rightDragged){
                    rightDragged = false;
                    //    rtx = rdragDestX-rdragOrigX;
                    rtx = (rdragOrigX-rdragDestX);
                    rdragOrigX = rdragDestX;
                    rty = (rdragOrigY-rdragDestY);
                    rdragOrigY = rdragDestY;
                  //  System.out.println("rtx: "+rtx+" rty: "+rty);
                    // pb, how to rotate around
                    camera.rotateAround(-rty,rtx);

                  //  camera.
                }

                camera.view(glu);

                // keyboard rotation :
                rOrigY += kry;
                rOrigX += krx;
                kry = 0;
                krx = 0;

                if (showAxes) {
                    draw3DAxes(gl);
                }
                synchronized (windowOn3DPoints) {
                    draw3DPoints(gl, windowOn3DPoints);
                }
                synchronized (trackers) {
                   drawTrackers( gl );
                }

                draw3DFrames(gl);

                 if(showRotationCube){
                   gl.glColor3f(1.0f,0,0);
                   draw3DAxes(gl,camera.getRotationCenterX(),camera.getRotationCenterY(),camera.getRotationCenterZ(),20);
                   //cube(gl,camera.getRotationCenterX(),camera.getRotationCenterY(),camera.getRotationCenterZ(),20);
                }

                    gl.glPopMatrix();
                    gl.glFlush();

                   if(pngRecordEnabled){
                        grabImage(drawable);
                   }

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
              // if(goThroughMode) {
                    glu.gluPerspective(10.0,(double)width/(double)height,0.5,100000.0);

            //   } else {
            //      gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
            //   }
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




     
     

    public void update(Observable o, Object arg) {
        initFilter();
    }
    

    protected float rotateYonX( float y, float z, float yRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)) +
                (Math.cos(Math.toRadians(angle))*(y-yRotationCenter)) )+yRotationCenter) ;
    }
    protected float rotateZonX( float y, float z, float yRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.cos(Math.toRadians(angle))*(z-zRotationCenter)) -
                (Math.sin(Math.toRadians(angle))*(y-yRotationCenter)) )+zRotationCenter );
    }
    protected float rotateXonY( float x, float z, float xRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.cos(Math.toRadians(angle))*(x-xRotationCenter)) -
                (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)) )+xRotationCenter );

    }
    protected float rotateZonY( float x, float z, float xRotationCenter, float zRotationCenter, float angle){
        return( (float) ( (Math.sin(Math.toRadians(angle))*(x-xRotationCenter)) +
                (Math.cos(Math.toRadians(angle))*(z-zRotationCenter))) +zRotationCenter );
    }

    /** Point : all data about a point in opengl 3D space */
    public class Point{
        float x;
        float y;
        float z;
        int time;
        int type;

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

       public void selfRotateOnX( float yRotationCenter, float zRotationCenter, float angle){
              float yr = rotateYonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              float zr = rotateZonX( y, z,  yRotationCenter,  zRotationCenter,  angle);
              y = yr;
              z = zr;

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



     public class DVS3DModel{
         int size = 40; //mm
         int width = 20;
         int lenseSize = 30;
         int lenseWidth = 30;
         // nox
         Point p1;
         Point p2;
         Point p3;
         Point p4;
         Point p5;
         Point p6;
         Point p7;
         Point p8;
         // lens
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

         public void rotate( float[] rotationMAtrix){

             // rotate all points
             p1.rotate( rotationMAtrix);
             p2.rotate( rotationMAtrix);
             p3.rotate( rotationMAtrix);
             p4.rotate( rotationMAtrix);
             p5.rotate( rotationMAtrix);
             p6.rotate( rotationMAtrix);
             p7.rotate( rotationMAtrix);
             p8.rotate( rotationMAtrix);
             p9.rotate( rotationMAtrix);
             p10.rotate( rotationMAtrix);
             p11.rotate( rotationMAtrix);
             p12.rotate( rotationMAtrix);



         }

     }


       ///###################### Trackers #############################################

    private class Cluster3DTracker{
        int id = 0;
        int x = 0;
        int y = 0;
        int z = 0;
        int time = 0;

        int nbEvents = 0;

        //for ball tracker, hacked here
        float x_size = 0;
        float y_size = 0;
        float z_size = 0;
      //  boolean activated = false;

        public Cluster3DTracker(  ){
            //for ball tracker, hacked here
            x_size = tracker_surround;
            y_size = tracker_surround;
            z_size = tracker_surround;
            id = idClusters++;

        }

        public Cluster3DTracker( int x, int y, int z, int time ){
          //  activated = true;
            this.time = time;

            id = idClusters++;
            this.x = x;
            this.y = y;
            this.z = z;


            x_size = tracker_surround;
            y_size = tracker_surround;
            z_size = tracker_surround;
            nbEvents = 1;
        }

        public void reset(){

         //   activated = false;

            x_size = tracker_surround;
            y_size = tracker_surround;
            z_size = tracker_surround;

            x = 0; //end tip
            y = 0;
            z = 0;
            nbEvents = 0;


        }

        // test
        public void log( int time){
             if(recording){

                 if (nbEvents > tracker_viable_nb_events) {

                     String slog = new String(time + " " + x + " " + y + " " + z + " " + id + "\n");
                     tracklog.log(slog);

                 }
             }
        }

        public void add( Point p){
            add(p.x, p.y, p.z, track_mix, p.type, p.time);
           // log(p.time);
        }

        public void add( float x, float y, float z, float mix, float value, int time){

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
            if (mix < 0) {
                mix = 0;
            }

            this.time = time;
            if (this.x != 0) {
                this.x = Math.round(x * mix + this.x * (1 - mix));
            } else {
                this.x = Math.round(x);
            }
            if (this.y != 0) {
                this.y = Math.round(y * mix + this.y * (1 - mix));
            } else {
                this.y = Math.round(y);
            }
            if (this.z != 0) {
                this.z = Math.round(z * mix + this.z * (1 - mix));
            } else {
                this.z = Math.round(z);
            }
            
            


            nbEvents++;
         //   System.out.println("-- nb events: "+nbEvents);



        }


    }


   synchronized private void clearDeadTrackers(int time){
       Vector<Cluster3DTracker> toRemove = new Vector();
       int prevNbClusters = nbClusters;
        for(Cluster3DTracker fc:trackers){
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
       for(Cluster3DTracker fc:toRemove){
           trackers.remove(fc);
           fc=null;
           //System.out.println("clearDeadTrackers delete Tracker at t = "+time);
       }

     //  System.out.println("clearDeadTrackers "+nbClusters);
   }



    private Cluster3DTracker getNearestCluster( float x, float y, float z, int surround ){
        float min_dist=Float.MAX_VALUE;
        Vector<Cluster3DTracker> closest=new Vector();
        // float currentDistance=0;
        int surroundSq = surround*surround;
        float dist = min_dist;
        float dx = 0;
        float dy = 0;
        float dz  =0;
     //  StringBuffer sb = new StringBuffer();
        try {
        for(Cluster3DTracker fc:trackers){
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
            System.out.println("Warning: getNearestClusters: ConcurrentModificationException caught");
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





    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("Binocular3DTracker.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("Binocular3DTracker.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
     public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        
        getPrefs().putBoolean("Binocular3DTracker.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
    
     public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;

        getPrefs().putInt("Binocular3DTracker.decayTimeLimit",decayTimeLimit);
    }
    public int getDecayTimeLimit() {
        return decayTimeLimit;
    }
      public void setShowCamera(boolean showCamera){
        this.showCamera = showCamera;
        getPrefs().putBoolean("Binocular3DTracker.showCamera",showCamera);
    }
    public boolean isShowCamera(){
        return showCamera;
    }
    public void setShowGrid(boolean showGrid){
        this.showGrid = showGrid;
        getPrefs().putBoolean("Binocular3DTracker.showGrid",showGrid);
    }
    public boolean isShowGrid(){
        return showGrid;
    }

    public void setShowRange(boolean showRange){
        this.showRange = showRange;
        getPrefs().putBoolean("Binocular3DTracker.showRange",showRange);
    }
    public boolean isShowRange(){
        return showRange;
    }


     public void setInvert(boolean invert){
        this.invert = invert;
        getPrefs().putBoolean("Binocular3DTracker.invert",invert);
    }
    public boolean isInvert(){
        return invert;
    }
      public void setIgnoreZero(boolean ignoreZero){
        this.ignoreZero = ignoreZero;
        getPrefs().putBoolean("Binocular3DTracker.ignoreZero",ignoreZero);
    }
    public boolean isIgnoreZero(){
        return ignoreZero;
    }





     public void setCube_size(int cube_size) {
        this.cube_size = cube_size;

        getPrefs().putInt("Binocular3DTracker.cube_size",cube_size);
    }
    public int getCube_size() {
        return cube_size;
    }


     public float getRetina_tilt_angle() {
        return retina_tilt_angle;
    }
    public void setRetina_tilt_angle(float retina_tilt_angle) {
        this.retina_tilt_angle = retina_tilt_angle;
        compute3DParameters();
        getPrefs().putFloat("Binocular3DTracker.retina_tilt_angle",retina_tilt_angle);
    }
     public float getY_rotation_dist() {
        return y_rotation_dist;
    }

    public void setY_rotation_dist(float y_rotation_dist) {
        this.y_rotation_dist = y_rotation_dist;

       getPrefs().putFloat("Binocular3DTracker.y_rotation_dist",y_rotation_dist);
    }

    public int getTracker_surround() {
        return tracker_surround;
    }

    public void setTracker_surround(int tracker_surround) {
        this.tracker_surround = tracker_surround;

       getPrefs().putInt("Binocular3DTracker.tracker_surround",tracker_surround);
    }

    public float getExpansion_mix() {
        return expansion_mix;
    }

    public void setExpansion_mix(float expansion_mix) {
        this.expansion_mix = expansion_mix;

       getPrefs().putFloat("Binocular3DTracker.expansion_mix",expansion_mix);
    }

    public float getTrack_mix() {
        return track_mix;
    }

    public void setTrack_mix(float track_mix) {
        this.track_mix = track_mix;

       getPrefs().putFloat("Binocular3DTracker.track_mix",track_mix);
    }


    public int getTracker_lifeTime() {
        return tracker_lifeTime;
    }

    public void setTracker_lifeTime(int tracker_lifeTime) {
        this.tracker_lifeTime = tracker_lifeTime;
        getPrefs().putInt("Binocular3DTracker.tracker_lifeTime",tracker_lifeTime);
    }

    public int getTracker_prelifeTime() {
        return tracker_prelifeTime;
    }

    public void setTracker_prelifeTime(int tracker_prelifeTime) {
        this.tracker_prelifeTime = tracker_prelifeTime;
        getPrefs().putInt("Binocular3DTracker.tracker_prelifeTime",tracker_prelifeTime);
    }

     public int getTracker_viable_nb_events() {
        return tracker_viable_nb_events;
    }

    public void setTracker_viable_nb_events(int tracker_viable_nb_events) {
        this.tracker_viable_nb_events = tracker_viable_nb_events;
        getPrefs().putInt("Binocular3DTracker.tracker_viable_nb_events",tracker_viable_nb_events);
    }

        public int getNbMaxClusters() {
        return nbMaxClusters;
    }

    public void setNbMaxClusters(int nbMaxClusters) {
        this.nbMaxClusters = nbMaxClusters;
        getPrefs().putInt("Binocular3DTracker.nbMaxClusters",nbMaxClusters);
    }
   public void setRetina_height(float retina_height) {
        this.retina_height = retina_height;
        compute3DParameters();
        getPrefs().putFloat("Binocular3DTracker.retina_height",retina_height);
    }
    public float getRetina_height() {
        return retina_height;
    }
}
