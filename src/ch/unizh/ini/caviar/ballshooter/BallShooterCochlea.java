/*
 * BallShooterCochlea.java
 *
 * Created on July 16, 2007, 10:38 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.ballshooter;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import ch.unizh.ini.caviar.eventprocessing.filter.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import java.awt.Graphics2D;
import java.beans.*;
import java.util.logging.*;
import java.util.prefs.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
/**
 *
 * @author Vaibhav Garg
 */
public class BallShooterCochlea extends EventFilter2D implements FrameAnnotater{
    private ArrayBlockingQueue Q;
    private ch.unizh.ini.caviar.chip.cochlea.CochleaCrossCorrelator itd;
    public BallShooterCochlea(AEChip chip){
        super(chip);
        itd=new ch.unizh.ini.caviar.chip.cochlea.CochleaCrossCorrelator(chip);
        
        itd.setEnclosed(true, this);
        itd.setFilterEnabled(false); //false when starting
        setEnclosedFilter(itd);
    }
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        //log.info("Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
        ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
        if(Q==null)
            {
                
                Tmpdiff128CochleaCommunication.initBlockingQ();
                log.info("q was null in cochlea");
            }
        if(Tmpdiff128CochleaCommunication.sizeBlockingQ()>0)//if something there in queue
        {
            CommunicationObject co=(CommunicationObject)Tmpdiff128CochleaCommunication.peekBlockingQ();
            if(co.isForCochlea())//if packet is for cochlea
            {
                try{
                    co=(CommunicationObject)Q.poll();
                    if(co.isIsCochleaEnabled())
                        itd.setFilterEnabled(true);//enable itd filter
                } catch (Exception e) {
                    log.info("Problem in Cochlea while polling");
                    e.printStackTrace();
                }
            }
        }
        else
            log.info("Queue Empty!");
        
        
        return in;
    }
    public void initFilter() {
        //filterchain.reset();
    }
    public Object getFilterState() {
        return null;
    }
    
    /** Overrides to avoid setting preferences for the enclosed filters */
    @Override public void setFilterEnabled(boolean yes){
        this.filterEnabled=yes;
        getPrefs().putBoolean("filterEnabled",yes);
    }
    
    public void resetFilter() {
    }
    public void annotate(GLAutoDrawable drawable) {
        //((FrameAnnotater)clusterFinder).annotate(drawable);
    }
    public void annotate(Graphics2D g) {
    }
    public void annotate(float[][][] frame) {
    }
}
