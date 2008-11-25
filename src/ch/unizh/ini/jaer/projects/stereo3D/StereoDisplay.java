/*
 * StereoDisplay.java
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
//import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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


import java.text.*;

/**
 * StereoDisplay:
 * Open two Display Frame that show the accumulated value of the pixels activation
 * one for the left and one for the right retina when mounted on the stereoboard
 *
 * @author rogister
 */
public class StereoDisplay extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
   
    int maxGCs = 100;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
    
   private float radius=getPrefs().getFloat("StereoDisplay.radius",10.0f);
  {setPropertyTooltip("radius","GC area radius in pixels");}
   
     private float ratio=getPrefs().getFloat("StereoDisplay.ratio",0.8f);
  {setPropertyTooltip("ratio","GC inertia ratio");}
//    
//    
//    private int parameter2=getPrefs().getInt("StereoDisplay.paramter2",10);
//     {setPropertyTooltip("parameter2","useless parameter2");}
//  
//    
    private int intensityZoom = getPrefs().getInt("StereoDisplay.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for display window");}
   
    
     private boolean scaleAcc = getPrefs().getBoolean("StereoDisplay.scaleAcc",false);
    {setPropertyTooltip("scaleAcc","when true: accumulated value cannot go below zero");}
   
    
 //   private int minEvents = getPrefs().getInt("StereoDisplay.minEvents",100);
//    {setPropertyTooltip("minEvents","min events to create GC");}
  
       
    private boolean showWindow = getPrefs().getBoolean("StereoDisplay.showWindow",true);
    private boolean showAxes = getPrefs().getBoolean("StereoDisplay.showAxes",true);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
    private int retinaSize=128;//getPrefs().getInt("GravityCentersImageDumper.retinaSize",128);
    
   
   float accLeftPoints[][] = new float[retinaSize][retinaSize];
   float accRightPoints[][] = new float[retinaSize][retinaSize];
  
   
   protected float grayValue = 0.5f;
   float step = 0.33334f;
   
   boolean firstRun = true;
   
   boolean logLeftAccPNG=false;
   boolean accLeftLogged=false;
   
   boolean logRightAccPNG=false;
   boolean accRightLogged=false;
    
//    private boolean condition = getPrefs().getBoolean("GravityCentersImageDumper.condition",false);
 //   {setPropertyTooltip("condition","true or not?");}
    
     public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
   
        
   

    
    /** Creates a new instance of GravityCentersImageDumper */
    public StereoDisplay(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        
        
        
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
                            
     //   if( !chip.getAeViewer().isSingleStep()){
     //       chip.getAeViewer().aePlayer.pause();
     //   }
        
    //    int currentTime = ae.getLastTimestamp();
        
        for(BinocularEvent e:ae){
            
            processEvent(e);
                        
        }
        
    //    if( !chip.getAeViewer().isSingleStep()){
    //        chip.getAeViewer().aePlayer.resume();
    //    }
                
    }
    
    
    public String toString(){
        String s="StereoDisplay";
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
        System.out.println ("StereoDisplay resetFilter ");
        logLeftAccPNG = false;
        logRightAccPNG = false;
        
        accLeftLogged = false;
        accRightLogged = false;
        
        accLeftPoints = new float[retinaSize][retinaSize];
        accRightPoints = new float[retinaSize][retinaSize];
        }
    }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
       
      
        
        if(accLeftLogged&&logLeftAccPNG){
            logLeftAccPNG=false;
            accLeftLogged=false;
           
            writePNG(leftImage3DOpenGL,"AccLeft");
            
        }
        
        if(accRightLogged&&logRightAccPNG){
            logRightAccPNG=false;
            accRightLogged=false;
           
            writePNG(rightImage3DOpenGL,"AccRight");
            
        }
        
        checkLeftDisplayFrame();
        checkRightDisplayFrame();
        
        track(in);
        
        
        
        if (showWindow) leftdisplayCanvas.repaint();
        if (showWindow) rightdisplayCanvas.repaint();
        
        return in;
    }
    
   
    
     protected void writePNG( BufferedImage Image3D, String label){
        try {
            String dateString = loggingFilenameDateFormat.format(new Date());
            String filename = "stereopic-" + label + "-" + dateString + ".png";

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
     
   
     
     
     
     
     

    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    
    // processing one event
    protected void processEvent(BinocularEvent e){
              
        if(firstRun){
            firstRun = false;
            resetFilter();
        }
        int leftOrRight = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here
        
        //   System.out.println("processEvent leftOrRight:"+leftOrRight+" e.eye:"+e.eye+" type:"+e.getType());
        
        int type=e.polarity==BinocularEvent.Polarity.Off? 0: 1;
        
     //   int dy = e.y;
     //   int dx = e.x;
        
      //  System.out.println("StereoDisplay processEvent "+e.x+" "+e.y);
       
    
        if (leftOrRight == LEFT) {
            // add to acc
           
            accLeftPoints[e.x][e.y] += step * (e.type - grayValue);
            if (scaleAcc) {
                // keep in range [0-1]
                if (accLeftPoints[e.x][e.y] < 0) {
                    accLeftPoints[e.x][e.y] = 0;
                }
            }

        } else {
            // add to acc

            accRightPoints[e.x][e.y] += step * (e.type - grayValue);
            if (scaleAcc) {
                // keep in range [0-1]
                if (accRightPoints[e.x][e.y] < 0) {
                    accRightPoints[e.x][e.y] = 0;
                }
            }
        }


          
                        
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
        leftdisplayFrame.setSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        leftdisplayFrame.setMaximumSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
       
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
                    logLeftAccPNG = true;
                     System.out.println("LeftDisplayFrame:  logLeftAccPNG: "+logLeftAccPNG);
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
     
            private void drawAllPoints(float[][] points, GL gl) {
                
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);
                    
                    for (int i = 0; i < retinaSize; i++) {
                        for (int j = 0; j < retinaSize; j++) {
                            

                            gl.glColor3f(points[i][j], points[i][j], points[i][j]);
                            gl.glRectf(i , j , (i + 1) , (j + 1) );
                            //gl.glRectf(i * intensityZoom, j * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);


                        }
                    }
                   if (showAxes) {
                        gl.glColor3f(1, 0, 0);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (10) * intensityZoom, (1) * intensityZoom);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (1) * intensityZoom, (10) * intensityZoom);
                    }


                
            }
             
            
            
      void grabImage( GLAutoDrawable d ) {
          
          System.out.println("grab left image :  logLeftAccPNG: "+logLeftAccPNG);
          
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
 
                
                
                synchronized (accLeftPoints) {
                    drawAllPoints(accLeftPoints,gl);
                }
                
    
                
                if(logLeftAccPNG){
                    grabImage(drawable);
                    accLeftLogged=true;
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
                gl.glOrtho(0,drawable.getWidth(),0,drawable.getHeight(),0,1);
               // gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
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
        rightdisplayFrame.setSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        rightdisplayFrame.setMaximumSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
      
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
                    logRightAccPNG = true;
                     System.out.println("LeftDisplayFrame:  logRightAccPNG: "+logRightAccPNG);
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
                    
            
               private void drawAllPoints(float[][] points, GL gl) {
                
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);
                    
                    for (int i = 0; i < retinaSize; i++) {
                        for (int j = 0; j < retinaSize; j++) {
                            gl.glColor3f(points[i][j], points[i][j], points[i][j]);
                            gl.glRectf(i * intensityZoom, j * intensityZoom, (i + 1) * intensityZoom, (j + 1) * intensityZoom);


                        }
                    }
                   if (showAxes) {
                        gl.glColor3f(0, 0, 1);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (10) * intensityZoom, (1) * intensityZoom);
                        gl.glRectf(0 * intensityZoom, (0) * intensityZoom, (1) * intensityZoom, (10) * intensityZoom);
                    }


                
            }
               
           
            
         
      void grabImage( GLAutoDrawable d ) {
          
           System.out.println("grab right image :  logRightAccPNG: "+logRightAccPNG);
           
           
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
 
                synchronized (accRightPoints) {
                    drawAllPoints(accRightPoints,gl);
                }
    
                if(logRightAccPNG){
                    grabImage(drawable);
                    accRightLogged=true;
                }
                
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    // if(glu==null) glu=new GLU();
                    //log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }
            
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
               // final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
             //   gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glOrtho(0,drawable.getWidth(),0,drawable.getHeight(),0,1);
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
        
        getPrefs().putInt("StereoDisplay.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
  
    
    
    public void setShowAxes(boolean showAxes){
        this.showAxes = showAxes;
        
        getPrefs().putBoolean("StereoDisplay.showAxes",showAxes);
    }
    public boolean isShowAxes(){
        return showAxes;
    }
    
     public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        
        getPrefs().putBoolean("StereoDisplay.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
 
    public void setScaleAcc(boolean scaleAcc){
        this.scaleAcc = scaleAcc;
        
        getPrefs().putBoolean("StereoDisplay.scaleAcc",scaleAcc);
    }
    public boolean isScaleAcc(){
        return scaleAcc;
    }
    
    
    
}
