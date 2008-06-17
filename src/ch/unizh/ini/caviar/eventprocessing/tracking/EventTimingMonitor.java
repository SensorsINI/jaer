/*
 * EventTimingMonitor.java
 * Monitor incoming events in defined area, output mean time of packet and min/max recorded time
 *
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

//import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;
import java.awt.*;

import java.io.*;
import java.util.*;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;


/**
 * EventTimingMonitor:
 * Monitor incoming events in defined area, output mean time of packet and min/max recorded time
 *
 * @author rogister
 */
public class EventTimingMonitor extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
    private boolean left = getPrefs().getBoolean("EyeFilter.left", true);
    private boolean right = getPrefs().getBoolean("EyeFilter.right", true);
    
    protected final int ON = 1;
    protected final int OFF = 0;
   
    
    // retina size, should be coded in hard, should come from chip
    //private int retinaSize=128;//getPrefs().getInt("EventTimingMonitor.retinaSize",128);
   
     
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private boolean logDataEnabled=false;
    private PrintStream logStream=null;
    
    // Parameters appearing in the GUI
    
   
    private int x_min = getPrefs().getInt("EventTimingMonitor.x_min",0);
    {setPropertyTooltip("x_min","monitored area x_min in pixel coordinates");}
   private int x_max = getPrefs().getInt("EventTimingMonitor.x_max",0);
    {setPropertyTooltip("x_max","monitored area x_max in pixel coordinates");}
   private int y_min = getPrefs().getInt("EventTimingMonitor.y_min",0);
    {setPropertyTooltip("y_min","monitored area y_min in pixel coordinates");}
   private int y_max = getPrefs().getInt("EventTimingMonitor.y_max",0);
    {setPropertyTooltip("y_max","monitored area y_max in pixel coordinates");}
  
       
    private int timeWindowLength = getPrefs().getInt("EventTimingMonitor.timeWindowLength",0);
    {setPropertyTooltip("timeWindowLength","duration of time window in us");}
  
    private int minEvents = getPrefs().getInt("EventTimingMonitor.minEvents",100);
    {setPropertyTooltip("minEvents","min events to log results");}
  
   
    private boolean showZone = getPrefs().getBoolean("EventTimingMonitor.showZone",true);

    // do not forget to add a set and a get/is method for each new parameter, at the end of this .java file
    
    
    // global variables
    
    
   int startTime = 0;
   int endTime = 0;
   int meanTime = 0;
   boolean restartMeanComputation = true;
   Vector timings = new Vector();
   Vector offtimings = new Vector();
  

    
    /** Creates a new instance of GravityCentersImageDumper */
    public EventTimingMonitor(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw on the viewer screen
        chip.getCanvas().addAnnotator(this);
        
        
        
        initFilter();
        
      
        
        
        chip.addObserver(this);
        
       
    }
    
    public void initFilter() {
        timings = new Vector();
        offtimings = new Vector();
        startTime = 0;
        endTime = 0;
        meanTime = 0;
        restartMeanComputation = true;
    }

   
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
     
     // the method that actually does the tracking
    synchronized private void track(EventPacket<TypedEvent> ae){
                      
        int n=ae.getSize();
        if(n==0) return;
                            
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.pause();
        }
        
       
        
        for(TypedEvent e:ae){
            
            
         
            if(e.x>x_min&&e.x<x_max&&e.y>y_min&&e.y<y_max){
             processEvent(e);
            }
                        
        }
        
        if( !chip.getAeViewer().isSingleStep()){
            chip.getAeViewer().aePlayer.resume();
        }
                
    }
    
     // processing one event
    protected void processEvent(TypedEvent e){
       
       if (e instanceof BinocularEvent){
           BinocularEvent be = (BinocularEvent)e;
           int leftOrRight = be.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here        
           if (left && (leftOrRight != LEFT))  {
                return;
            }
           if (right && (leftOrRight != RIGHT))  {
                return;
            }
       } 
        
    //    if (leftOrRight == LEFT) {
           
        if(restartMeanComputation){
            startTime = e.timestamp;
            restartMeanComputation = false;
        }
        
        if(e.timestamp>startTime+timeWindowLength){
             // end mean computation
            if(timings.size()>minEvents){
                    meanTime = mean(timings);
                  chip.getAeViewer().aePlayer.pause();
                    System.out.println("meanTime "+meanTime);
                    System.out.println("startTime "+startTime);
                    System.out.println("endTime "+endTime);
                    System.out.println("nb events "+timings.size()+" left? "+left);
                    System.out.println("Vector");
                    System.out.println(timings);
                    System.out.println("end Vector");
                    chip.getAeViewer().aePlayer.resume();
            }
//           if(offtimings.size()>minEvents){
//               System.out.println("nb events "+offtimings.size()+" left? "+left);
//                    System.out.println("Off Vector");
//                    System.out.println(offtimings);
//                    System.out.println("end Off Vector");
//               
//           }
            // restart mean computation
            restartMeanComputation = true;
            timings.clear();
           // offtimings.clear();
         }     
        
        endTime = e.timestamp;
        if(e.type == ON){
            timings.add(new Integer(endTime));
        } else {
           // offtimings.add(new Integer(endTime));
        }
                        
    }
    
    
    private int mean(Vector<Integer> v){
        float res = 0;
        for( Integer i:v){
            res += i.floatValue();
        }
        return Math.round(res/v.size());
        
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
       
        
    }

    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) {
            // System.out.println("not a binocular event!");
            return in;
        }
          
        track(in);
           
        return in;
    }           

    public void update(Observable o, Object arg) {
        initFilter();
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
            if(showZone){
               gl.glColor3f(0,1,0);
               if(left) gl.glColor3f(1,0,0);
               else if(right) gl.glColor3f(0,0,1);
               drawBox(gl,x_min,x_max,y_min,y_max);
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
    
   
    
    public void setShowZone(boolean showZone){
        this.showZone = showZone;
        
        getPrefs().putBoolean("EventTimingMonitor.showZone",showZone);
    }
    public boolean isShowZone(){
        return showZone;
    }
 
    public void setX_min(int x_min) {
        this.x_min = x_min;      
       getPrefs().putInt("EventTimingMonitor.x_min",x_min);
    }
    public int getX_min() {
        return x_min;
    }
    
     
    public void setX_max(int x_max) {
        this.x_max = x_max;      
       getPrefs().putInt("EventTimingMonitor.x_max",x_max);
    }
    public int getX_max() {
        return x_max;
    }
    
     
    public void setY_min(int y_min) {
        this.y_min = y_min;      
       getPrefs().putInt("EventTimingMonitor.y_min",y_min);
    }
    public int getY_min() {
        return y_min;
    }
    
     
    public void setY_max(int y_max) {
       this.y_max = y_max;      
       getPrefs().putInt("EventTimingMonitor.y_max",y_max);
    }
    public int getY_max() {
        return y_max;
    }
    
    public void setTimeWindowLength(int timeWindowLength) {
       this.timeWindowLength = timeWindowLength;      
       getPrefs().putInt("EventTimingMonitor.timeWindowLength",timeWindowLength);
    }
    public int getTimeWindowLength() {
        return timeWindowLength;
    }
    
      public void setMinEvents(int minEvents) {
       this.minEvents = minEvents;      
       getPrefs().putInt("EventTimingMonitor.minEvents",minEvents);
    }
    public int getMinEvents() {
        return minEvents;
    }
    
     public void setLeft(boolean left){
        this.left = left;
        
        getPrefs().putBoolean("EyeFilter.left",left);
    }
    public boolean isLeft(){
        return left;
    }
    
     public void setRight(boolean right){
        this.right = right;
        
        getPrefs().putBoolean("EyeFilter.right",right);
    }
    public boolean isRight(){
        return right;
    }
    
    
}
