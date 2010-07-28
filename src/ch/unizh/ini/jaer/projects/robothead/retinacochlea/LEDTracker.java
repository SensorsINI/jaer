/*
 * RetinaCochleaFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.retinacochlea;


import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.GL;
import net.sf.jaer.util.EngineeringFormat;


/**
 * 
 * This class divides the incoming Events into Retina and Cochlea Events. 
 * The Retina Events are tracked by an enclosed RectangularClusterTracker, and 
 * the Cluster information is provided.
 * the cochlea Events are converted to normal Cochlea Events (y value-64) and
 * passed on to the next filter...
 *
 * @author jaeckeld
 */

public class LEDTracker extends EventFilter2D implements Observer, FrameAnnotater{
   
    private float minEventRate=getPrefs().getFloat("RetinaCochleaFilter.minEventRate",0.2f);
    private int minLifeTime=getPrefs().getInt("RetinaCochleaFilter.minLifeTime",10);
    private float maxSizeChange=getPrefs().getFloat("RetinaCochleaFilter.maxSizeChange",0.5f);
    
    
    RectangularClusterTracker tracker;
    public static RectangularClusterTracker.Cluster LED=null;
    RectangularClusterTracker.Cluster oneCluster;
    
    volatile private boolean doTracking;
    volatile private boolean LEDRecognized;
    
    private float oldSize;
    
    
    /**
     * Creates a new instance of RetinaCochleaFilter
     */
    public LEDTracker(AEChip chip) {
        super(chip);
        
        setPropertyTooltip("minEventRate", "Minimum Event rate to recognize LED");
        setPropertyTooltip("minLifeTime", "Minimum Lifetime to recognize LED");
        setPropertyTooltip("maxSizeChange", "Minimum Lifetime to recognize LED");
         
        tracker=new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker);
        
        doTracking=true;
        
        initFilter();
        
    }
    
        
    public EventPacket<?> filterPacket(EventPacket<?> in) {
       
        if(in==null) return null;
        if(!filterEnabled) return in;
        
        if(isDoTracking()){
            tracker.filterPacket(in);     // track retina Events, but first rotates them 90 deg (with enclosed rotator)!!
            oneCluster=getLEDCluster();
            
            if (oneCluster!=null){
                float sizeChange=oneCluster.getMeasuredSizeCorrectedByPerspective()-oldSize;
                
                if(oneCluster.getAvgEventRate()>this.minEventRate && oneCluster.getLifetime()>minLifeTime*1000 && sizeChange<maxSizeChange && oneCluster.getLocation().getX()<64){     // TODO experiment with lifetime etc. to ensure safe detection...
                    LED=oneCluster;
                    LEDRecognized=true;
                    //System.out.println(LED.getLocation().toString()+"     "+LED.getMeasuredSizeCorrectedByPerspective());
                    
                }else{
                    LED=null;
                    LEDRecognized=false;
                }

                oldSize=oneCluster.getMeasuredSizeCorrectedByPerspective();
                
            }
        }
        //LED=oneCluster;
            
        // location.x = Höhe und y = Winkel bzw. horizontale Komponente, die ich betrachten muss...
        
        return in;
        
    }
    
    private RectangularClusterTracker.Cluster getLEDCluster(){
        if(tracker.getNumClusters()==0) return null;
        return tracker.getClusters().get(0);
    }
    
    EngineeringFormat fmt=new EngineeringFormat();
    
    public void annotate(GLAutoDrawable drawable) {
        
        if(!isFilterEnabled()) return;
            GL gl=drawable.getGL();
            //gl.glPushMatrix();
            final GLUT glut=new GLUT();
            gl.glColor3f(1,1,1);
            gl.glRasterPos3f(0,10,10);
        if(LEDRecognized){
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.format(" LED Detected: x = %s",fmt.format(LED.getLocation().getX())));
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12,String.format(" y = %s",fmt.format(LED.getLocation().getY())));
        }
    }
    
    /** not used */
    public void annotate(float[][][] frame) {
    }

    /** not used */
    public void annotate(Graphics2D g) {
    }
    public Object getFilterState() {
        return null;
    }
    
    public float getMinEventRate(){
        return this.minEventRate;
    }
    
    public void setMinEventRate(float minEventRate){       
        getPrefs().putFloat("RetinaCochleaFilter.minEventRate",minEventRate);
        getSupport().firePropertyChange("minEventRate",this.minEventRate,minEventRate);
        this.minEventRate=minEventRate;
    }
    public int getMinLifeTime(){
        return this.minLifeTime;
    }
    
    public void setMinLifeTime(int minLifeTime){       
        getPrefs().putInt("RetinaCochleaFilter.minLifeTime",minLifeTime);
        getSupport().firePropertyChange("minLifeTime",this.minLifeTime,minLifeTime);
        this.minLifeTime=minLifeTime;
    }
    
    public float getMaxSizeChange(){
        return this.maxSizeChange;
    }
    
    public void setMaxSizeChange(float maxSizeChange){       
        getPrefs().putFloat("RetinaCochleaFilter.maxSizeChange",maxSizeChange);
        getSupport().firePropertyChange("maxSizeChange",this.maxSizeChange,maxSizeChange);
        this.maxSizeChange=maxSizeChange;
    }
    
    public void resetFilter(){
//        System.out.println("reset!");
        
        
    }
    public void initFilter(){
        System.out.println("init!");
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    public boolean isLEDRecognized(){
        return LEDRecognized;
    }
    
    
    public void setDoTracking(boolean set){
        doTracking=set;
    }
    public boolean isDoTracking(){
        return doTracking;
    }
    
    public RectangularClusterTracker.Cluster getLED(){
        if(this.isLEDRecognized())   return LED;
        else return null;
    }
    
}
    
