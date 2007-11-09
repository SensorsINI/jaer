/*
 * TemplateTracker.java
 * Template for trackers, shows also how to set an additional display window
 *
 * Paul Rogister, Created on November, 2007
 *
 */


package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
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
import java.awt.event.WindowListener;

/**
 * Tracks Rat's Paw
 *<p>
 * </p>
 *
 * @author rogister
 */
public class TemplateTracker extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    
   
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
    
    private float parameter1=getPrefs().getFloat("TemplateTracker.parameter1",0.1f);
  {setPropertyTooltip("parameter1","important parameter1");}
    
    
    private int parameter2=getPrefs().getInt("TemplateTracker.paramter2",10);
     {setPropertyTooltip("parameter2","useless parameter2");}
  
    
    private int intensityZoom = getPrefs().getInt("TemplateTracker.intensityZoom",2);
    {setPropertyTooltip("intensityZoom","zoom for display window");}
    
    private boolean showWindow = getPrefs().getBoolean("TemplateTracker.showWindow",true);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
    private int retinaSize=128;//getPrefs().getInt("TemplateTracker.retinaSize",128);
    
   
   CustomPoint[][] points= new CustomPoint[retinaSize][retinaSize];
    
    
    private boolean condition = getPrefs().getBoolean("TemplateTracker.condition",false);
    {setPropertyTooltip("condition","true or not?");}
    
    
        
    /** additional internal classes */
    
    /** CustomPoint : all your data about a point in retina space */
    public class CustomPoint{
       
        float accValue=0;                // accumulated contrast value
       int x = 0;
       int y = 0;
            
       
        public CustomPoint(  ){
            
        }
        
        public CustomPoint( int x, int y, float value ){
            this.x = x;
            this.y = y;
            accValue = value;
           
            
        }
       
        
    } // end class CustomPoint
    
    
    
  
    
 
    
    
    
    /** Creates a new instance of PawTracker */
    public TemplateTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw on the viewer screen
        chip.getCanvas().addAnnotator(this);
        
        
        
        initFilter();
        
        resetTracker();
        
        
        chip.addObserver(this);
        
       
    }
    
    public void initFilter() {
        
    }
     
    private void resetTracker(){
       // whatever to reset but not too much as it is called often
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
        String s="TemplateTracker";
        return s;
    }
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
        
        // resetTracker();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
        
        checkDisplayFrame();
        
        track(in);
        
        if (showWindow) displayCanvas.repaint();
      
        return in;
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
        
        
          
         //   leftPoints[e.x][cy].updateFrom(e,e.x,cy,LEFT);
         // fastDualLowFilterFrame(leftPoints[e.x][cy], leftPoints, rightPoints );
           
          //      processDisparity( leftOrRight, e.x, cy,  value, previousValue, LEFT, LEFT_MOST_METHOD,leftPoints, rightPoints );
            
                        
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
    
   
    
    
    // for optimization, radius2 must be > radius1
    // uses global densities and densities2 arrays
    // leftMost is policy of disparity matching
    void fastDualLowFilterFrame( CustomPoint ep, CustomPoint[][] leftPoints, CustomPoint[][] rightPoints ){
        
      
     
                
        // for all points in square of side "range" around
        // if point within circle of diameter "range"
        //     add value to 8 neighbours
        //          for each neighbours, possible new event!
        //                     so call followign stage: here processDisparity(..)
        //          end for
        //     
        // end if
        // end for
        
       
        
    }
    
   
    // show 2D view
    
    void checkDisplayFrame(){
        if(showWindow && displayFrame==null) createDisplayFrame();
    }
    
    JFrame displayFrame=null;
    GLCanvas displayCanvas=null;
    
    private static final GLU glu = new GLU();
    
  
//    GLUT glut=null;
    void createDisplayFrame(){
        displayFrame=new JFrame("Display Frame");
        displayFrame.setPreferredSize(new Dimension(retinaSize*intensityZoom,retinaSize*intensityZoom));
        displayCanvas=new GLCanvas();
        
        displayCanvas.addKeyListener( new KeyListener(){
            /** Handle the key typed event from the text field. */
            public void keyTyped(KeyEvent e) {
                
            }
            
            public void keyPressed(KeyEvent e) {
                
            }
            
            public void keyReleased(KeyEvent e) {
               
                
        
                
                if(e.getKeyCode()==KeyEvent.VK_C){
                    condition=!condition;
                    displayCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_D){
                   // showDisparity=!showDisparity;
                    displayCanvas.display();
                    
                }
                
                if(e.getKeyCode()==KeyEvent.VK_T){
                   // testDrawing=!testDrawing;
                    displayCanvas.display();
                    
                }
                if(e.getKeyCode()==KeyEvent.VK_G){
                   // showLabels=!showLabels;
                    displayCanvas.display();
                    
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
        
        
        displayCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
                                                           
            private void drawPoints( CustomPoint[][] eventPoints, GL gl ){
                
                //    System.out.println("1. display drawEventPoints  time: "+currentTime+" length: "+eventPoints.length);
                
                for (int i = 0; i<eventPoints.length; i++){
                    for (int j = 0; j<eventPoints[i].length; j++){
                        CustomPoint ep = eventPoints[i][j];
                        if(ep==null)break;
                        float f=ep.accValue;
                        
                        gl.glColor3f(f,f,f);
                        
                    }
                }
                
            }
            
         
          
            
            synchronized public void display(GLAutoDrawable drawable) {
                
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                //gl.glScalef(drawable.getWidth()/2000,drawable.getHeight()/180,1);//dist to gc, orientation?
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                int font = GLUT.BITMAP_HELVETICA_12;
 
                drawPoints(points,gl);
    
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
            log.warning("null GL in TemplateTracker.annotate");
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
        
        getPrefs().putInt("TemplateTracker.intensityZoom",intensityZoom);
    }
    
    public int getIntensityZoom() {
        return intensityZoom;
    }
    
    
   
    
    public void setParameter1(float parameter1) {
        this.parameter1 = parameter1;
        
        getPrefs().putFloat("TemplateTracker.parameter1",parameter1);
    }
    
    public float getParameter1() {
        return parameter1;
    }
    
    public void setParameter2(int parameter2) {
        this.parameter2 = parameter2;
        
        getPrefs().putInt("TemplateTracker.parameter2",parameter2);
    }
    
    public int getParameter2() {
        return parameter2;
    }
    
    
   
    
    public boolean isCondition() {
        return condition;
    }
    public void setCondition(boolean condition) {
        this.condition = condition;
        
        getPrefs().putBoolean("TemplateTracker.condition",condition);
        
    }
    
  
    
    
    public void setShowWindow(boolean showWindow){
        this.showWindow = showWindow;
        
        getPrefs().putBoolean("TemplateTracker.showWindow",showWindow);
    }
    public boolean isShowWindow(){
        return showWindow;
    }
 
    
}
