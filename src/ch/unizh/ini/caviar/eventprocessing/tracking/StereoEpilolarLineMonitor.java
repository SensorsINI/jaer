/*
 * StereoEpilolarLineMonitor.java
 * Monitor one epilolar line and plot it in time-bins to check its evolution over time
 * on two retina, left and right
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

import ch.unizh.ini.stereo3D.EpipolarLine;

/**
 * EventTimingMonitor:
 * Monitor incoming events in defined area, output mean time of packet and min/max recorded time
 *
 * @author rogister
 */
public class StereoEpilolarLineMonitor extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
     
    protected final int ON = 1;
    protected final int OFF = 0;
   
    protected float grayValue = 0.5f;
    float step = 0.33334f;
   
    // retina size, should be coded in hard, should come from chip
    private int retinaSize=128;//getPrefs().getInt("EventTimingMonitor.retinaSize",128);
   
     
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
    
   
    private int epipolar_y = getPrefs().getInt("StereoEpilolarLineMonitor.epipolar_y",0);
    {setPropertyTooltip("epipolar_y","monitored epipolar line");}
   
       
    private int frameTimeBin = getPrefs().getInt("StereoEpilolarLineMonitor.frameTimeBin",1000);
    {setPropertyTooltip("frameTimeBin","duration of frame in us");}
  
     private int maxFrames = getPrefs().getInt("StereoEpilolarLineMonitor.maxFrames",100);
    {setPropertyTooltip("maxFrames","max number of frames");}
  
    
        protected final int RIGHT = 1;
    protected final int LEFT = 0;
 //   private boolean left = getPrefs().getBoolean("StereoEpilolarLineMonitor.left", true);
 //   private boolean right = getPrefs().getBoolean("StereoEpilolarLineMonitor.right", true);
  
    
    public static DateFormat loggingFilenameDateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
   
  
    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
   boolean logPNG=false;
   boolean imageLogged=false;
   
 
  
 //  Vector[] leftEpipolarFrames = new Vector[2];
  Vector leftEpipolarFrames = new Vector();
  Vector rightEpipolarFrames = new Vector();
  
   
   
    int[] startFrameTime = new int[2];

   
    
    EpipolarLine[] frame = new EpipolarLine[2];
    
    /** Creates a new instance of GravityCentersImageDumper */
    public StereoEpilolarLineMonitor(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        
        
        
        initFilter();
        
      
        
        
        chip.addObserver(this);
        
       
    }
    
    public void initFilter() {
        resetFilter();
    }

   
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<TypedEvent> ae){
                      
        int n=ae.getSize();
        if(n==0) return;
                            
   //     if( !chip.getAeViewer().isSingleStep()){
   //         chip.getAeViewer().aePlayer.pause();
   //     }
        
       
        for(TypedEvent e:ae){

             processEvent(e);
                             
        }
        
     //   if( !chip.getAeViewer().isSingleStep()){
     //       chip.getAeViewer().aePlayer.resume();
     //   }
                
    }
    
     // processing one event
    protected void processEvent( TypedEvent e ){
       
       
         if (e instanceof BinocularEvent){
             
           BinocularEvent be = (BinocularEvent)e;
           int leftOrRight = be.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here        
           
            if(startFrameTime[leftOrRight]==0){
              //create initial frame
              
              startFrameTime[leftOrRight] = e.timestamp;
              frame[leftOrRight] = new EpipolarLine(retinaSize);
              if(leftOrRight==LEFT){               
                  leftEpipolarFrames.add(frame);
              } else {
                  rightEpipolarFrames.add(frame);
              }
              //epipolarFrames[leftOrRight].add(frame);
          } else if(e.timestamp>startFrameTime[leftOrRight]+frameTimeBin){
              
              // create another new frame
              startFrameTime[leftOrRight] = be.timestamp;
              frame[leftOrRight] = new EpipolarLine(retinaSize);
              
                if (leftOrRight == LEFT) {
                    if (leftEpipolarFrames.size() > maxFrames) {
                        leftEpipolarFrames.clear();
                    }
                    leftEpipolarFrames.add(frame[leftOrRight]);
                } else {
                    if (rightEpipolarFrames.size() > maxFrames) {
                        rightEpipolarFrames.clear();
                    }
                    rightEpipolarFrames.add(frame[leftOrRight]);
                }
         //     if(epipolarFrames[leftOrRight].size()>maxFrames){
        //          epipolarFrames[leftOrRight].clear();
        //      }
        //      epipolarFrames[leftOrRight].add(frame[leftOrRight]);
          }
           
          if(e.y==epipolar_y){
              //  frame.value[e.x] += step * (e.type - grayValue);
              frame[leftOrRight].value[e.x] += 2*(e.type - grayValue);
             
          }  
           
          
       } 
       
    }
    
    
    private void saveToFile(Vector frames, String label, String sideLabel) {

        try {
            String dateString = loggingFilenameDateFormat.format(new Date());
            String fileName = label + dateString + sideLabel + ".txt";
            fileName = fileName.replace('+','-');
            fileName = fileName.replaceAll("-","");
            
            
            FileWriter fileWriter = new FileWriter(fileName);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            // bufferedWriter.write("[");
        //   for (Frame f : frames) {
              for (int j = 1; j < frames.size(); j++) {
                EpipolarLine f = (EpipolarLine)frames.elementAt(j);
                for (int i = 1; i < f.value.length; i++) {
                    float v = f.value[i];
                    bufferedWriter.write(" " + v);
                    
                }
                bufferedWriter.newLine();
            }
            //         bufferedWriter.write("]");


            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    
     private void saveTotalToFile(Vector frames, String label, String sideLabel) {

        try {
            String dateString = loggingFilenameDateFormat.format(new Date());
            String fileName = label + dateString + sideLabel + ".txt";
            fileName = fileName.replace('+','-');
            fileName = fileName.replaceAll("-","");
            
            
            FileWriter fileWriter = new FileWriter(fileName);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            // bufferedWriter.write("[");
            float[] acc = new float[((EpipolarLine)frames.elementAt(0)).value.length];
          //  float[][] accCircular = new float[frames.elementAt(0).value.length][100];
         //   int k = 0;
            for (int y=1;y<frames.size();y++){
                EpipolarLine f = (EpipolarLine)frames.elementAt(y);
              
                for (int i = 1; i < f.value.length; i++) {
                    
                    acc[i] += f.value[i];
                   
            //        if(k>99)k=0;
            //        accCircular[i][k] += f.value[i];
            //        k+=1;
                     
            //        if(sum(accCircular[i])==0){
            //            acc[i] = 0;
            //        }
                    bufferedWriter.write(" " + acc[i]);

                }
                bufferedWriter.newLine();
            }
            //         bufferedWriter.write("]");


            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private int sum(float[] f ){
        int res = 0;
    
         for (int i = 0; i < f.length; i++) {
            res += f[i];
         }
        return res;
    }
    public String toString(){
        String s="EpipolarLineMonitor";
        return s;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    synchronized public void resetFilter() {
       startFrameTime = new int[2];
       leftEpipolarFrames = new Vector();
       rightEpipolarFrames = new Vector();
       
       logPNG = false;
       imageLogged = false;
    }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
          
        
           
        
         if(imageLogged){
            logPNG=false;
            imageLogged=false;
           
            writePNG(image3DOpenGL,"AccLeft");
            saveToFile(leftEpipolarFrames,"epipolarLineData","Left");
        //    saveDiffToFile(epipolarFrames,"diffEpipolarLineData");
            saveToFile(rightEpipolarFrames,"totalEpipolarLineData","Right");
        }
        
        checkDisplayFrame();
        
        
        track(in);
        
        
        displayCanvas.repaint();
        
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
         
    
     // show 2D view
    
    void checkDisplayFrame(){
        if(displayFrame==null) createDisplayFrame();
    }
    
    JFrame displayFrame=null;
    GLCanvas displayCanvas=null;
    BufferedImage image3DOpenGL=null;
    
    private static final GLU glu = new GLU();
    
  
//    GLUT glut=null;
    void createDisplayFrame(  ){
        displayFrame=new JFrame("Display  Frame");
        displayFrame.setPreferredSize(new Dimension(retinaSize*2,retinaSize));
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
                    logPNG = true;
                     System.out.println("DisplayFrame:  logPNG: "+logPNG);
                    //displayCanvas.display();
                    
                }
                
               
            
            }
        });
        
       
        
        
        displayCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
     
            private void drawAllFrames(Vector frames, GL gl, int xOffset) {
                
                    //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);

                    gl.glClearColor(0, 0, 0, 0);
                    
                    int y = 0;
                   
                  //  for (Frame f:frames) {
                    for (int n = 0; n < frames.size(); n++) {
                        System.out.println(frames);
                        EpipolarLine f = (EpipolarLine)frames.elementAt(n);
                        for (int i = 0; i < f.value.length; i++) {
                            
                            gl.glColor3f(f.value[i], f.value[i], f.value[i]);
                            gl.glRectf(xOffset+i,y,xOffset+i+1,y+1);
                        }
                        y++;
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
        // buffer in reverse - (0,0) is at bottom  in OpenGL.
        
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
 
                
                
                synchronized (leftEpipolarFrames) {
                    drawAllFrames(leftEpipolarFrames,gl,0);
                }
                synchronized (rightEpipolarFrames) {
                    drawAllFrames(rightEpipolarFrames,gl,retinaSize+1);
                }
                
    
                
                if(logPNG){
                    grabImage(drawable);
                    logPNG=false;
                    imageLogged = true;
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
        //final float LINE_WIDTH=5f; // in pixels
        if(!isFilterEnabled()) return;
        
        
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in GravityCentersImageDumper.annotate");
            return;
        }
       // float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
      
            // like draw door
               gl.glColor3f(0,0.5f,0);
               
               drawBox(gl,0,retinaSize,epipolar_y-1,epipolar_y+1);
      
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
    
   
    
  
 
    public void setEpipolar_y(int epipolar_y) {
        this.epipolar_y = epipolar_y;      
       getPrefs().putInt("StereoEpilolarLineMonitor.epipolar_y",epipolar_y);
    }
    public int getEpipolar_y() {
        return epipolar_y;
    }
    
     
   
    
     
    public void setMaxFrames(int maxFrames) {
       this.maxFrames = maxFrames;      
       getPrefs().putInt("StereoEpilolarLineMonitor.maxFrames",maxFrames);
    }
    public int getMaxFrames() {
        return maxFrames;
    }
    
    public void setFrameTimeBin(int frameTimeBin) {
       this.frameTimeBin = frameTimeBin;      
       getPrefs().putInt("StereoEpilolarLineMonitor.frameTimeBin",frameTimeBin);
    }
    public int getFrameTimeBin() {
        return frameTimeBin;
    }

    
    
}
