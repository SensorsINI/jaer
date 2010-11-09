/*
 * Binocular3DDisplay2.java
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 *  use SVD to perform triangulation
 *
 * Paul Rogister, Created on October, 2010
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


import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**

import java.text.*;

/**
 * Binocular3DDisplay:
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 * @author rogister
 */
public class Binocular3DDisplay2 extends EventFilter2D implements Observer /*, PreferenceChangeListener*/ {
    
    
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
  
    Matrix p1T_left = new Matrix(1,4);
    Matrix p2T_left = new Matrix(1,4);
    Matrix p3T_left = new Matrix(1,4);
    Matrix p1T_right = new Matrix(1,4);
    Matrix p2T_right = new Matrix(1,4);
    Matrix p3T_right = new Matrix(1,4);



    private Point left_focal;
    private Point right_focal;
    int scaleFactor = 1;
    private DVS3DModel retina3DLeft;
    private DVS3DModel retina3DRight;



    // Parameters appearing in the GUI
 
//    
//    
//    private int parameter2=getPrefs().getInt("Binocular3DDisplay2.paramter2",10);
//     {setPropertyTooltip("parameter2","useless parameter2");}
//  
//    
    private int intensityZoom = getPrefs().getInt("Binocular3DDisplay2.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for display window");}
   
    private int decayTimeLimit=getPrefs().getInt("Binocular3DDisplay2.decayTimeLimit",10000);
    {setPropertyTooltip("decayTimeLimit","[microsec (us)] for decaying accumulated events");}
  
    
 //   private int minEvents = getPrefs().getInt("Binocular3DDisplay2.minEvents",100);
//    {setPropertyTooltip("minEvents","min events to create GC");}
  
       
    private boolean showWindow = getPrefs().getBoolean("Binocular3DDisplay2.showWindow",true);

    private boolean showCamera = getPrefs().getBoolean("Binocular3DDisplay2.showRetina",true);
    private boolean showGrid = getPrefs().getBoolean("Binocular3DDisplay2.showGrid",true);
    private boolean showAxes = getPrefs().getBoolean("Binocular3DDisplay2.showAxes",true);

    boolean showVGrid = false;
   // hidden
    private float y_rotation_dist=getPrefs().getFloat("Binocular3DDisplay2.y_rotation_dist",1000);
    private float pixel_size=getPrefs().getFloat("Binocular3DDisplay2.pixel_size",0.04f);
    {setPropertyTooltip("pixel_size","pixel size of retina in mm");}
    private float retina_tilt_angle=getPrefs().getFloat("Binocular3DDisplay2.retina_tilt_angle",0.0f);
    {setPropertyTooltip("retina_tilt_angle","forward tilt angle of the retinae, in degrees");}

    private int cube_size=getPrefs().getInt("Binocular3DDisplay2.cube_size",10);
    private float shadowFactor=getPrefs().getFloat("Binocular3DDisplay2.shadowFactor",0.3f);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
     private boolean invert = getPrefs().getBoolean("Binocular3DDisplay2.invert",false);
  
     private int scale=getPrefs().getInt("Binocular3DDisplay2.scale",10);

    // global variables
    
   
  
   
   protected float grayValue = 0.5f;
   float step = 0.33334f;
   
   boolean firstRun = true;
   
   public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
   
        
   

    
    /** Creates a new instance of GravityCentersImageDumper */
    public Binocular3DDisplay2(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
                        
        //initFilter();
        
        chip.addObserver(this);
        
       
    }

      private void reset3DComputation(){

      // System.out.println("reset3DComputation");

        // pb with reset and display : null pointer if happens at the same time

       

        TrackerisSet = true;
        compute3DParameters();

    }


    public void initFilter() {
        
    }
     
   
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularXYDisparityEvent> ae){
                      
        int n=ae.getSize();
        if(n==0) return;

         if(firstRun){
            // reset
            reset3DComputation();
            firstRun=false;
           // return; //maybe continue then
       }

  
        currentTime = ae.getLastTimestamp();

      
        for(BinocularXYDisparityEvent e:ae){
             int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
          if(eye==LEFT){
           store3DCoordinatesFor(e);
          }
        }

       // decayStoreStream(streamOf3DPoints);

    
                
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

    protected void store3DCoordinatesFor(BinocularXYDisparityEvent e){
        // store in some array or vector after computing 3D coodinates
       
        if (e.disparity == 0) {//ok since it is used by funmantal7 to validate disp
            return;
        }

        int xdisparity = e.xdisparity;
        int ydisparity = e.ydisparity;
        if(invert){
            xdisparity = -xdisparity;
            ydisparity = -ydisparity;
        }
         if(e.x-xdisparity>0&&e.x-xdisparity<retinaSize&&e.y-ydisparity>0&&e.y-ydisparity<retinaSize){

            // compute real world coordinate from x,y and d


            Point pr = get3DPointFor(e.x,e.y,e.x-xdisparity,e.y-ydisparity);
            //test            Point pr = get3DPointFor(81,39,55,45);

            pr.time = e.timestamp;
            pr.type = e.polarity==BinocularEvent.Polarity.Off? 0: 1;
            windowOn3DPoints[e.x][e.y] = pr;
           // streamOf3DPoints.add(pr);
         }

    }
 

 
    private void loadCalibrationFromFile(String filename ) {
        try {
            //use buffering, reading one line at a time
            //FileReader always assumes default encoding is OK!
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null; //not declared within while loop

               // int x = 0;
               // int y = 0;
                float[] data = new float[24];
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

                // store P matrices

                for (int c = 0; c < 4; c++) {
                    p1T_left.set(0, c, data[c]);
                }
                for (int c = 0; c < 4; c++) {
                    p2T_left.set(0, c, data[c + 4]);
                }
                for (int c = 0; c < 4; c++) {
                    p3T_left.set(0, c, data[c + 8]);
                }
                for (int c = 0; c < 4; c++) {
                    p1T_right.set(0, c, data[c + 12]);
                }
                for (int c = 0; c < 4; c++) {
                    p2T_right.set(0, c, data[c + 16]);
                }
                for (int c = 0; c < 4; c++) {
                    p3T_right.set(0, c, data[c + 20]);
                }


            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }




    // here implement call to SVD
    protected Point get3DPointFor( int xleft, int yleft, int xright, int yright){
       
        // X = Px and X = P'x' so with Ax = 0 and A = UDVt (from SVD decomposition) we get X = last column of V



        Matrix A = new Matrix(4,4);
        //     [ xp3T  - p1T  ]
        // A = [ yp3T  - p2T  ]
        //     [ x'p'3T - p'1T ]
        //     [ y'p'3T - p'2T ]
   
        int norm = scale;

        A.setMatrix( 0,0,0,3, p3T_left.times(xleft).minus(p1T_left));
        A.setMatrix( 1,1,0,3, p3T_left.times(yleft).minus(p2T_left));
        A.setMatrix( 2,2,0,3, p3T_right.times(xright).minus(p1T_right));
        A.setMatrix( 3,3,0,3, p3T_right.times(yright).minus(p2T_right));

        // SVD
        SingularValueDecomposition s = A.svd();
        Matrix V = s.getV();
        
        double x = V.get(0,V.getColumnDimension()-1);
        double y = V.get(1,V.getColumnDimension()-1);
        double z = V.get(2,V.getColumnDimension()-1);
        double w = V.get(3,V.getColumnDimension()-1);
        
        
        Point res = new Point((float)(x/w)*norm,(float)(y/w)*norm,(float)(z/w)*norm);

        return res;

    }



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

        loadCalibrationFromFile("pmatrix.txt");


    







        computed3D = true;

    } //end compute3DParameters

    
    public String toString(){
        String s="Binocular3DDisplay";
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
       // System.out.println ("Binocular3DDisplay resetFilter ");


          //  streamOf3DPoints = new Vector<Point>();
            windowOn3DPoints = new Point[retinaSize][retinaSize];
        }
    }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularXYDisparityEvent)) {
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
/*
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
*/

                if(showGrid){

                     gl.glColor3f(0.2f,0.2f,0.2f);
                     //x
                     for (int xi=-25000;xi<25001;xi+=1000){
                         line3D( gl,  xi, -1000,  -25000,  xi, -1000, 25000);
                       //  line3D( gl,  xi, (-retina_height+cage_door_height)*scaleFactor,  cage_distance*scaleFactor-2500,  xi, (-retina_height+cage_door_height)*scaleFactor, cage_distance*scaleFactor+2500);
                     }
                     // z
                      for (int zi=-25000;zi<25001;zi+=1000){
                         line3D( gl, -25000, -1000, zi, 25000, -1000, zi);
                        // line3D( gl,  -2500, (-retina_height+cage_door_height)*scaleFactor,  zi+cage_distance*scaleFactor, 2500, (-retina_height+cage_door_height)*scaleFactor, cage_distance*scaleFactor+zi);

                     }

                }
/*
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
*/
                /*
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
                 * */
                 

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
                    tx = -(dragDestX - dragOrigX);
                    ty = (dragDestY - dragOrigY);
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
         int lenseSize = 60;
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




    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("Binocular3DDisplay2.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("Binocular3DDisplay2.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
     public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        
        getPrefs().putBoolean("Binocular3DDisplay2.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
    
     public void setDecayTimeLimit(int decayTimeLimit) {
        this.decayTimeLimit = decayTimeLimit;

        getPrefs().putInt("Binocular3DDisplay2.decayTimeLimit",decayTimeLimit);
    }
    public int getDecayTimeLimit() {
        return decayTimeLimit;
    }
      public void setShowCamera(boolean showCamera){
        this.showCamera = showCamera;
        getPrefs().putBoolean("Binocular3DDisplay2.showCamera",showCamera);
    }
    public boolean isShowCamera(){
        return showCamera;
    }
    public void setShowGrid(boolean showGrid){
        this.showGrid = showGrid;
        getPrefs().putBoolean("Binocular3DDisplay2.showGrid",showGrid);
    }
    public boolean isShowGrid(){
        return showGrid;
    }

     public void setInvert(boolean invert){
        this.invert = invert;
        getPrefs().putBoolean("Binocular3DDisplay2.invert",invert);
    }
    public boolean isInvert(){
        return invert;
    }
  


     public void setScale(int scale) {
        this.scale = scale;

        getPrefs().putInt("Binocular3DDisplay2.scale",scale);
    }
    public int getScale() {
        return scale;
    }



     public void setCube_size(int cube_size) {
        this.cube_size = cube_size;

        getPrefs().putInt("Binocular3DDisplay2.cube_size",cube_size);
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
        getPrefs().putFloat("Binocular3DDisplay2.retina_tilt_angle",retina_tilt_angle);
    }
     public float getY_rotation_dist() {
        return y_rotation_dist;
    }

    public void setY_rotation_dist(float y_rotation_dist) {
        this.y_rotation_dist = y_rotation_dist;

       getPrefs().putFloat("Binocular3DDisplay2.y_rotation_dist",y_rotation_dist);
    }
}
