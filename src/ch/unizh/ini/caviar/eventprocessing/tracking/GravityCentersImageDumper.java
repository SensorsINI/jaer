/*
 * GravityCentersImageDumper.java

 *
 * Paul Rogister, Created on June, 2008
 *
 */


package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
//import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.InputEvent;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import javax.imageio.*;
import java.awt.image.*;
import java.nio.ByteBuffer;

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
import java.text.*;

/**
 * Compute gravity centers in in circular zones of radius r
 * Display results, press L to dump x,y into txt file
 *<p>
 * </p>
 *
 * @author rogister
 */
public class GravityCentersImageDumper extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
   
    int maxGCs = 100;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
    
   private float radius=getPrefs().getFloat("GravityCentersImageDumper.radius",10.0f);
  {setPropertyTooltip("radius","GC area radius in pixels");}
   
     private float ratio=getPrefs().getFloat("GravityCentersImageDumper.ratio",0.8f);
  {setPropertyTooltip("ratio","GC inertia ratio");}
//    
//    
//    private int parameter2=getPrefs().getInt("GravityCentersImageDumper.paramter2",10);
//     {setPropertyTooltip("parameter2","useless parameter2");}
//  
//    
    private int intensityZoom = getPrefs().getInt("GravityCentersImageDumper.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for display window");}
    
    
    private int minEvents = getPrefs().getInt("GravityCentersImageDumper.minEvents",100);
    {setPropertyTooltip("minEvents","min events to create GC");}
  
       
    private boolean showWindow = getPrefs().getBoolean("GravityCentersImageDumper.showWindow",true);
    private boolean showAxes = getPrefs().getBoolean("GravityCentersImageDumper.showAxes",true);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
    private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);
    
   
   float accPoints[][] = new float[retinaSize][retinaSize];
    
   
   protected float grayValue = 0.5f;
   float step = 0.33334f;
   
   Vector<GravityCenter> leftGCs= new Vector();
   Vector<GravityCenter> rightGCs= new Vector();
    
   boolean logPNG=false;
   boolean leftLogged=false;
   boolean rightLogged=false;
   
   boolean logAccPNG=false;
   boolean accurateLogged=false;
    
//    private boolean condition = getPrefs().getBoolean("GravityCentersImageDumper.condition",false);
 //   {setPropertyTooltip("condition","true or not?");}
    
     public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
   
        
    /** additional internal classes */
    
    /** CustomPoint : all your data about a point in retina space */
    public class GravityCenter{
      // private float radiusX=10, radiusY=10;
                      
       float x = 0;
       float y = 0;
            
       int nbEvents = 0;
       
        public GravityCenter(  ){
            nbEvents = 0;
        }
        
        public GravityCenter( int x, int y ){
            this.x = x;
            this.y = y;
            nbEvents = 1;
            
        }
       
        public void add(BasicEvent event){
            nbEvents++;
           // x = (x + event.x)/nbEvents;
          //  y = (y + event.y)/nbEvents;
            float a = ratio;
            x = x*a + event.x*(1-a);
            y = y*a + event.y*(1-a);
        }
        
        public int getX(){
            return Math.round(x);
        }
        
        public int getY(){
            return Math.round(y);
        }
        
         /** @return distance in x direction of this cluster to the event */
        private float distanceToX(BasicEvent event){
            float distance=Math.abs(event.x-this.x);
            return distance;
        }
        
        /** @return distance in y direction of this cluster to the event */
        private float distanceToY(BasicEvent event){
            float distance=Math.abs(event.y-this.y);
            return distance;
        }
        
    } // end class GravityCenter
    
    private GravityCenter getNearestCluster(BasicEvent event, Vector<GravityCenter> gravityCenters){
        float minDistance=Float.MAX_VALUE;
        GravityCenter closest=null;
        float currentDistance=0;
        for(GravityCenter c:gravityCenters){
           
            float dx,dy;
            if((dx=c.distanceToX(event))<radius && (dy=c.distanceToY(event))<radius){
                currentDistance=dx+dy;
                
                if(currentDistance<minDistance){
                    closest=c;
                    minDistance=currentDistance;
                   // c.distanceToLastEvent=minDistance;
                }
            }
        }
        return closest;
    }
    

    
    /** Creates a new instance of GravityCentersImageDumper */
    public GravityCentersImageDumper(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw on the viewer screen
        chip.getCanvas().addAnnotator(this);
        
        
        
        initFilter();
        
      
        
        
        chip.addObserver(this);
        
       
    }
    
    public void initFilter() {
        
    }
     
   
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<BinocularEvent> ae){
                      
        int n=ae.getSize();
        if(n==0) return;
                            
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
        int currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
            
            processEvent(e);
                        
        }
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }
                
    }
    
    
    public String toString(){
        String s="GravityCentersImageDumper";
        return s;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    synchronized public void resetFilter() {
        
      //   System.out.println ("GravityCentersImageDumper resetFilter ");
         
        leftGCs = new Vector();
        rightGCs = new Vector();

        logPNG = false;
        logAccPNG = false;
        leftLogged = false;
        rightLogged = false;
        accurateLogged = false;
        
        accPoints = new float[retinaSize][retinaSize];
    }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
       
        if(leftLogged&&rightLogged&logPNG){
            logPNG=false;
            leftLogged=false;
            rightLogged=false;
            writePNG(leftImage3DOpenGL,"LEFT");
            writePNG(rightImage3DOpenGL,"RIGHT");
        }
        
        if(accurateLogged&logAccPNG){
            logAccPNG=false;
            accurateLogged=false;
           
            writePNG(image3DOpenGL,"ACC");
            dumpGCCoordinates(leftGCs,"left gcs");
            dumpGCCoordinates(rightGCs,"right gcs");
        }
        
        checkLeftDisplayFrame();
        checkRightDisplayFrame();
        checkDisplayFrame();
        track(in);
        
        
        clearFalseGCs(leftGCs);
        clearFalseGCs(rightGCs);
        
        if (showWindow) leftdisplayCanvas.repaint();
        if (showWindow) rightdisplayCanvas.repaint();
        if (showWindow) displayCanvas.repaint();
      
        return in;
    }
    
    protected void clearFalseGCs( Vector<GravityCenter> gcPoints){
        Vector<GravityCenter> gcPointsToRemove = new Vector();
        for (GravityCenter gc : gcPoints) {
            if (gc == null) {
                break;
            }
            if (gc.nbEvents <= minEvents) {
                gcPointsToRemove.add(gc);
            }

        }  
        for (GravityCenter gc : gcPointsToRemove) {
            if (gc == null) {
                break;
            }
           
            gcPoints.remove(gc);
            

        }   
    }
    
     protected void writePNG( BufferedImage Image3D, String label){
        try {
            String dateString = loggingFilenameDateFormat.format(new Date());
            String filename = "dataGC-" + label + "-" + dateString + ".png";

            String homeDir = System.getProperty("user.dir");
            if (Image3D != null) {
                ImageIO.write(Image3D, "png", new File(homeDir, filename));
                System.out.println("logged: " + homeDir + " " + filename);
            } else {
                System.out.println("null: not logged: " + homeDir + " " + filename);
            }



        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
     
    protected void dumpGCCoordinates(Vector<GravityCenter> gcPoints, String label){
         
         
        System.out.println(label);
        for (GravityCenter gc : gcPoints) {
            if (gc == null) {
                break;
            }
            System.out.println(gc.x+" "+gc.y);

        }  
         
    }
     
     
     
     
     

    public void update(Observable o, Object arg) {
        initFilter();
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
        
        
        // add to gravityCenter from relevant retina
       
        Vector<GravityCenter> relevantGCs;
        if (leftOrRight==LEFT){
            relevantGCs =  leftGCs;
        } else {
            relevantGCs = rightGCs;
        }
        GravityCenter gc = getNearestCluster(e, relevantGCs );
        // find closest gravity center
        if (gc == null) {
            if (relevantGCs.size() < maxGCs) {
                gc = new GravityCenter(e.x, e.y);
                relevantGCs.add(gc);
            }
        } else {
            gc.add(e);
        }

        // add to acc
        
        accPoints[e.x][e.y]+=step * (e.type - grayValue);
        
          
                        
    }
    
    
    
  
        
     
    
    
  
  
    
    protected float distanceBetween( int x1, int y1, int x2, int y2 ){
        
        double dx = (double)(x1-x2);
        double dy = (double)(y1-y2);
        
        float dist = (float)Math.sqrt((dy*dy)+(dx*dx));
        
        
        return dist;
    }
    
  
    
   
   
    
    
   
    
   
    // show 2D view
    
    void checkLeftDisplayFrame(){
        if(showWindow && leftdisplayFrame==null) createLeftDisplayFrame();
    }
    
    JFrame leftdisplayFrame=null;
    GLCanvas leftdisplayCanvas=null;
    BufferedImage leftImage3DOpenGL=null;
    
    private static final GLU glu = new GLU();
    
  
//    GLUT glut=null;
    void createLeftDisplayFrame(  ){
        leftdisplayFrame=new JFrame("Display LEFT Frame");
        leftdisplayFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        leftdisplayCanvas=new GLCanvas();
        
        leftdisplayCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            public void keyPressed(KeyEvent e) {
                
            }
            
            public void keyReleased(KeyEvent e) {
               
                
        
                
                if(e.getKeyCode()==KeyEvent.VK_L){
                    // log all grativity points to a png image
                    logPNG = true;
                     System.out.println("LeftDisplayFrame:  logPNG: "+logPNG);
                    //leftdisplayCanvas.display();
                    
                }
                
               
            
            }
        });
        
        leftdisplayCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                
                int dx=leftdisplayCanvas.getWidth()-1;
                int dy=leftdisplayCanvas.getHeight()-1;
                
                // 3 is window's border width
               
                int x = (int)((evt.getX()-3)  / intensityZoom);
                int y = (int)((dy-evt.getY())  / intensityZoom);
                
               
                //   System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");
         
                if (evt.getButton()==1){
                    // if distinguishing button is important ...
                }
                       
                leftdisplayCanvas.display();
                
            }
            public void mouseReleased(MouseEvent e){
                
                
            }
        });
        
        
        leftdisplayCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
     
            private void drawPoints(Vector<GravityCenter> gcPoints, GL gl) {
                synchronized (gcPoints) {
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);
                    gl.glClearColor(0, 0, 0, 0);
                    if (showAxes) {
                        gl.glColor3f(1, 1, 1);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (10) * intensityZoom, (0 + 1) * intensityZoom);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (1) * intensityZoom, (10) * intensityZoom);
                    }

                    gl.glColor3f(1, 0, 0);
                    for (GravityCenter gc : gcPoints) {


                        if (gc == null) {
                            break;
                        }
                        if (gc.nbEvents > minEvents) {
                            
                            gl.glRectf(gc.x * intensityZoom, (gc.y) * intensityZoom, (gc.x + 1) * intensityZoom, (gc.y + 1) * intensityZoom);
                        }

                    }
                }

            }
            
      void grabImage( GLAutoDrawable d ) {
          
          System.out.println("grab left image :  logPNG: "+logPNG);
          
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
        if(leftImage3DOpenGL==null || leftImage3DOpenGL.getWidth()!=width || leftImage3DOpenGL.getHeight()!=height) {
            leftImage3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
        }
        leftImage3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
    }
  
          
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
 
                drawPoints(leftGCs,gl);
    
                
                if(logPNG){
                    grabImage(drawable);
                    leftLogged=true;
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
        leftdisplayFrame.getContentPane().add(leftdisplayCanvas);
        leftdisplayFrame.pack();
        leftdisplayFrame.setVisible(true);
    }
    

      // show 2D view right
    
    void checkRightDisplayFrame(){
        if(showWindow && rightdisplayFrame==null) createRightDisplayFrame();
    }
    
    JFrame rightdisplayFrame=null;
    GLCanvas rightdisplayCanvas=null;
    BufferedImage rightImage3DOpenGL=null;
 //   private static final GLU glu = new GLU();
    
  
//    GLUT glut=null;
    void createRightDisplayFrame(  ){
        rightdisplayFrame=new JFrame("Display RIGHT Frame");
        rightdisplayFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        rightdisplayCanvas=new GLCanvas();
        
        rightdisplayCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            public void keyPressed(KeyEvent e) {
                
            }
            
            public void keyReleased(KeyEvent e) {
               
                
        
                
                  if(e.getKeyCode()==KeyEvent.VK_L){
                    // log all grativity points to a png image
                    logPNG = true;
                     System.out.println("LeftDisplayFrame:  logPNG: "+logPNG);
                    //leftdisplayCanvas.display();
                    
                }
                
               
            
            }
        });
        
        rightdisplayCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                
                int dx=rightdisplayCanvas.getWidth()-1;
                int dy=rightdisplayCanvas.getHeight()-1;
                
                // 3 is window's border width
               
                int x = (int)((evt.getX()-3)  / intensityZoom);
                int y = (int)((dy-evt.getY())  / intensityZoom);
                
               
                //   System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");
         
                if (evt.getButton()==1){
                    // if distinguishing button is important ...
                }
                       
                rightdisplayCanvas.display();
                
            }
            public void mouseReleased(MouseEvent e){
                
                
            }
        });
        
        
        rightdisplayCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
                                                           
            private void drawPoints( Vector<GravityCenter> gcPoints, GL gl ){
                synchronized(gcPoints){
                //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);
                gl.glClearColor(0,0,0,0);
                if(showAxes){
                 gl.glColor3f(1, 1, 1);
                     gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (10) * intensityZoom, (0 + 1) * intensityZoom);
                     gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (1) * intensityZoom, (10) * intensityZoom);
                }
                gl.glColor3f(1, 0, 0);
                for(GravityCenter gc:gcPoints){
                   
                       
                    if(gc==null)break;
                    if (gc.nbEvents > minEvents) {
                        
                        gl.glRectf(gc.getX() * intensityZoom, (gc.getY()) * intensityZoom, (gc.getX() + 1) * intensityZoom, (gc.getY() + 1) * intensityZoom);
                    }

                }
                }
        
            }
            
         
      void grabImage( GLAutoDrawable d ) {
          
           System.out.println("grab right image :  logPNG: "+logPNG);
           
           
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
        if(rightImage3DOpenGL==null || rightImage3DOpenGL.getWidth()!=width || rightImage3DOpenGL.getHeight()!=height) {
            rightImage3DOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
        }
        rightImage3DOpenGL.setRGB(0, 0, width, height, pixelInts, 0, width);
    }
            
    synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
 
                drawPoints(rightGCs,gl);
    
                if(logPNG){
                    grabImage(drawable);
                    rightLogged=true;
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
        rightdisplayFrame.getContentPane().add(rightdisplayCanvas);
        rightdisplayFrame.pack();
        rightdisplayFrame.setVisible(true);
    }
    
    // accurate pixel frame
    
      // show 2D view right
    
    void checkDisplayFrame(){
        if(showWindow && displayFrame==null) createDisplayFrame();
    }
    
    JFrame displayFrame=null;
    GLCanvas displayCanvas=null;
    BufferedImage image3DOpenGL=null;
 //   private static final GLU glu = new GLU();
    
  
//    GLUT glut=null;
    void createDisplayFrame(  ){
        displayFrame=new JFrame("Acc Frame");
        displayFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        displayCanvas=new GLCanvas();
        
        displayCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            public void keyPressed(KeyEvent e) {
                
            }
            
            public void keyReleased(KeyEvent e) {
               
                
        
                
                  if(e.getKeyCode()==KeyEvent.VK_L){
                    // log all grativity points to a png image
                    logAccPNG = true;
                    logPNG = true;
                     System.out.println("Acc DisplayFrame:  logAccPNG: "+logAccPNG);
                    //leftdisplayCanvas.display();
                    
                }
                
               
            
            }
        });
        
        displayCanvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                
                int dx=displayCanvas.getWidth()-1;
                int dy=displayCanvas.getHeight()-1;
                
                // 3 is window's border width
               
                int x = (int)((evt.getX()-3)  / intensityZoom);
                int y = (int)((dy-evt.getY())  / intensityZoom);
                
               
                //   System.out.println("got x:"+x+" y:"+y+" from ["+evt.getX()+","+evt.getY()+"]");
         
                if (evt.getButton()==1){
                    // if distinguishing button is important ...
                }
                       
                displayCanvas.display();
                
            }
            public void mouseReleased(MouseEvent e){
                
                
            }
        });
        
        
        displayCanvas.addGLEventListener(new GLEventListener() {

            public void init(GLAutoDrawable drawable) {
            }

            private void drawAllPoints(float[][] points, GL gl) {
                synchronized (accPoints) {
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);
                    
                    for (int i = 0; i < retinaSize; i++) {
                        for (int j = 0; j < retinaSize; j++) {
                            gl.glColor3f(points[i][j], points[i][j], points[i][j]);
                            gl.glRectf(i * intensityZoom, j * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);


                        }
                    }
                   if (showAxes) {
                        gl.glColor3f(1, 0, 0);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (10) * intensityZoom, (1) * intensityZoom);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (1) * intensityZoom, (10) * intensityZoom);
                    }


                }
            }
            
         
      void grabImage( GLAutoDrawable d ) {
          
           System.out.println("grab  image :  logPNG: "+logPNG);
           
           
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
            
    synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
 
                drawAllPoints(accPoints,gl);
    
                if(logAccPNG){
                    grabImage(drawable);
                    accurateLogged=true;
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
        displayFrame.getContentPane().add(displayCanvas);
        displayFrame.pack();
        displayFrame.setVisible(true);
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
            log.warning("null GL in GravityCentersImageDumper.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
      
            // like draw door
            //    gl.glColor3f(0,1,0);
            //    drawBox(gl,door_xa,door_xb,door_ya,door_yb);

      
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
    
    
   
    
    public void setIntensityZoom(int intensityZoom) {
        this.intensityZoom = intensityZoom;
        
        getPrefs().putInt("GravityCentersImageDumper.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
     public void setMinEvents(int minEvents) {
        this.minEvents = minEvents;
        
        getPrefs().putInt("GravityCentersImageDumper.minEvents",minEvents);
    }
    
    public int getMinEvents() {
        return minEvents;
    }
    
     public void setRadius( float radius) {
        this.radius = radius;
        
        getPrefs().putFloat("GravityCentersImageDumper.radius",radius);
    }
    
    public float getRadius() {
        return radius;
    }
    
     public void setRatio( float ratio) {
        this.ratio = ratio;
        
        getPrefs().putFloat("GravityCentersImageDumper.ratio",ratio);
    }
    
    public float getRatio() {
        return ratio;
    }
    
    
   
    
    
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("GravityCentersImageDumper.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
     public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        
        getPrefs().putBoolean("GravityCentersImageDumper.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
 
    
}
