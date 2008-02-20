/*
 * RetinaCochleaFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.retinacochlea;


import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import java.util.*;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import javax.swing.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.io.*;


/**
 * 
 * This class divides the incoming Events into Retina and Cochlea Events. 
 * The Retina Events are tracked by an enclosed RectangularClusterTracker, and 
 * the Cluster information is provided (somehow?).
 * the cochlea Events are converted to normal Cochlea Events (y value-64) and
 * passed on to the next filter...
 *
 * @author jaeckeld
 */

public class RetinaCochleaFilter extends EventFilter2D implements Observer, FrameAnnotater{
   
    private float minEventRate=getPrefs().getFloat("RetinaCochleaFilter.minEventRate",0.2f);
    
    RectangularClusterTracker tracker;
    RectangularClusterTracker.Cluster LED=null;
    RectangularClusterTracker.Cluster oneCluster;
    
    public boolean LEDRecognized;
    
    
    /**
     * Creates a new instance of RetinaCochleaFilter
     */
    public RetinaCochleaFilter(AEChip chip) {
        super(chip);
        
        setPropertyTooltip("minEventRate", "Minimum Event rate to recognize LED");
        
        tracker=new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker);
        
        chip.getCanvas().addAnnotator(this);
        initFilter();
        
    }
    
    public EventPacket retinaEvents;
        
    public EventPacket<?> filterPacket(EventPacket<?> in) {
       
        if(in==null) return null;
        if(!filterEnabled) return in;
        
        OutputEventIterator outItr=out.outputIterator();
        
        retinaEvents=new EventPacket();                 // new eventPacket to store retinaEvents
        OutputEventIterator retinaItr=retinaEvents.outputIterator();
        
        for(Object e:in){
        
             BasicEvent i =(BasicEvent)e;
             //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            
             if (i.y>63){       // in that case it is a CochleaEvent !!
                
                 BasicEvent o=(BasicEvent)outItr.nextOutput();   // put all CochleaEvents into output
                 
                 o.copyFrom(i);
                 o.setY((short)(i.getY()-64));      // now they are lixe normal cochleaEvents
             }
             else{
                 BasicEvent o=(BasicEvent)retinaItr.nextOutput();   //Put Retina Events into EventPacket retinaEvents
                 o.copyFrom(i);
             }
        }
        tracker.filterPacket(retinaEvents);     // track retina Events !!
        oneCluster=getLEDCluster();
        if (oneCluster!=null){
            if(oneCluster.getAvgEventRate()>this.minEventRate){
                LED=oneCluster;
                LEDRecognized=false;
            }else{
                LED=null;
                LEDRecognized=false;
            }
        }
        //if(LED !=null)  System.out.println("Cluster Velocity: "+LED.velocity+" "+LED.location.x+" "+ LED.location.y +" EventRate: "+LED.getAvgEventRate());
        
        // location.x = Höhe und y = Winkel bzw. horizontale Komponente, die ich betrachten muss...
        
        return out;
        
    }
    
    private RectangularClusterTracker.Cluster getLEDCluster(){
        if(tracker.getNumClusters()==0) return null;
        return tracker.getClusters().get(0);
    }
    
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled() ) return;
        tracker.annotate(drawable);
        GL gl=drawable.getGL();
        gl.glPushMatrix();
        final GLUT glut=new GLUT();
        if(LEDRecognized){
            gl.glColor3f(1,1,1);
            gl.glRasterPos3f(0,1,1);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("LED detected"));
            
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
        support.firePropertyChange("minEventRate",this.minEventRate,minEventRate);
        this.minEventRate=minEventRate;
    }
    
    public void resetFilter(){
        System.out.println("reset!");
        
        
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
    
}
    
