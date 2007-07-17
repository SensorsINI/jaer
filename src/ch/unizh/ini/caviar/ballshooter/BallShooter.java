/*
 * BallShooter.java
 *
 * Created on July 14, 2007, 11:20 AM
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
 * This filter controls the ball shooter setup developed at Telluride 2007.
 */
public class BallShooter extends EventFilter2D implements FrameAnnotater{
    private RectangularClusterTracker firstClusterFinder;
    private XYTypeFilter xyfilter;
    private RectangularClusterTracker secondClusterFinder;
    private FilterChain filterchain;
    private TargetDetector targetDetect;
    private ShooterControl control;
    protected AEChip chip;
    static Logger log=Logger.getLogger("BallShooter");
    
    //class representing the bounding box
    private class Bbox{
        public float startx,starty,endx,endy;
        public Bbox() {
            startx=starty=endx=endy=0;
        }
    }
    private class TargetDetector extends EventFilter2D implements PropertyChangeListener {
        //private RectangularClusterTracker firstClusterFinder;
        //private XYTypeFilter xyfilter;
        //private FilterChain filterchain;
        String temp=this.getClass().getSimpleName()+".XReduceFactor";
       /* private double XReduceFactor=getPrefs().getDouble(temp,0.8);
    {setPropertyTooltip("XReduceFactor","Set the X range for the XY filter box");}
        public double getXReduceFactor()
        {
            return XReduceFactor;
        }
        public void setXReduceFactor(double XReduceFactor)
        {
            this.XReduceFactor=XReduceFactor;
        }*/
        public TargetDetector(AEChip chip){
            super(chip);
            firstClusterFinder=new RectangularClusterTracker(chip);
            xyfilter=new XYTypeFilter(chip);
            secondClusterFinder=new RectangularClusterTracker(chip);
            filterchain=new FilterChain(chip);
            
            firstClusterFinder.setEnclosed(true,this);
            xyfilter.setEnclosed(true,this);
            
            xyfilter.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            firstClusterFinder.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            secondClusterFinder.getPropertyChangeSupport().addPropertyChangeListener("filterEnabled",this);
            
            filterchain.add(firstClusterFinder);
            filterchain.add(xyfilter);
            filterchain.add(secondClusterFinder);
            
            setEnclosedFilterEnabledAccordingToPref(xyfilter,null);
            setEnclosedFilterEnabledAccordingToPref(firstClusterFinder,null);
            setEnclosedFilterEnabledAccordingToPref(secondClusterFinder,null);
            
            xyfilter.setXEnabled(true);
            xyfilter.setYEnabled(true);
            
            setEnclosedFilterChain(filterchain);
        }
        public void propertyChange(PropertyChangeEvent evt) {
            if(!evt.getPropertyName().equals("filterEnabled")) return;
            try{
                setEnclosedFilterEnabledAccordingToPref((EventFilter)(evt.getSource()),(Boolean)(evt.getNewValue()));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        public EventPacket<?> filterPacket(EventPacket<?> in) {
            if(!isFilterEnabled()) return in;
            //log.info("Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
            return filterchain.filterPacket(in);
        }
        private void setEnclosedFilterEnabledAccordingToPref(EventFilter filter, Boolean enb){
            String key="TargetDetector."+filter.getClass().getSimpleName()+".filterEnabled";
            if(enb==null){
                // set according to preference
                boolean en=getPrefs().getBoolean(key,true); // default enabled
                filter.setFilterEnabled(en);
            }else{
                boolean en=enb.booleanValue();
                getPrefs().putBoolean(key,en);
            }
        }
        public void resetFilter() {
            filterchain.reset();
        }
        
        public void initFilter() {
            filterchain.reset();
        }
        public Object getFilterState() {
            return null;
        }
        
        /** Overrides to avoid setting preferences for the enclosed filters */
        @Override public void setFilterEnabled(boolean yes){
            this.filterEnabled=yes;
            getPrefs().putBoolean("filterEnabled",yes);
        }
        
        @Override public boolean isFilterEnabled(){
            return true; // force active
        }
    }
    private class ShooterControl {
        void control(EventPacket in) {
            //first detect the target
            detectTarget();

        }
        
        //detects target. We have to somehow tell controlling function that target is detected.
        boolean detectTarget() {
            boolean found=false;
            List<RectangularClusterTracker.Cluster> clusterList=firstClusterFinder.getClusters();
            //the size will basically be either 1 or 2
            for(int ctr=0;ctr<clusterList.size();ctr++) {
                //for each cluster get location raius and aspect ratio
                Cluster clst=clusterList.get(ctr);
                float radius=(float)0.6*clst.getRadius();
                java.awt.geom.Point2D.Float location=clst.getLocation();
                float aspectRatio=clst.getAspectRatio();
                Bbox box=getBox(radius,location,aspectRatio);
                double XReduceFactor=1;
                double YReduceFactor=1;
                //log.info(" Before Box info "+box.startx+" "+box.starty+" "+box.endx+" "+box.endy+"\n ");
                xyfilter.setStartX((int)(XReduceFactor*box.startx));
                xyfilter.setStartY((int)(YReduceFactor*box.starty));
                xyfilter.setEndX((int)(XReduceFactor*box.endx));
                xyfilter.setEndY((int)(YReduceFactor*box.endy));
                //log.info("After Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
            }
            return found;
        }
        private Bbox getBox(float radius, java.awt.geom.Point2D.Float location, float aspectRatio) {
            Bbox box=new Bbox();
            float radiusX=radius/aspectRatio;
            float radiusY=radius*aspectRatio;
            
            box.startx=(float)location.getX()-radiusX;
            box.starty=(float)location.getY()-radiusY;
            box.endx=(float)location.getX()+radiusX;
            box.endy=(float)location.getY()+radiusY;
            return box;
            
        }
    }
    
    /** Creates a new instance of BallShooter */
    public BallShooter(AEChip chip) {
        super(chip);
        this.chip=chip;
        chip.getCanvas().addAnnotator(this);
        control=new ShooterControl();
        targetDetect=new TargetDetector(chip);
        initFilter();
        //firstClusterFinder=new RectangularClusterTracker(chip);
        //xyfilter=new XYTypeFilter(chip);
        //filterchain=new FilterChain(chip);
        //setEnclosedFilter(firstClusterFinder,this);
        //setEnclosedFilter(xyfilter,this);
        //firstClusterFinder.setEnclosed(true,this);
        //xyfilter.setEnclosed(true,this);
        
        //filterchain.add(firstClusterFinder);
        //filterchain.add(xyfilter);
        
        //setEnclosedFilterChain(filterchain);
        
        //firstClusterFinder.setMaxNumClusters(2);
    }
    
    public void initFilter() {
        setEnclosedFilter(targetDetect);
    }
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket out;
        if(in==null) return null;
        if(!filterEnabled) return in;
        out= getEnclosedFilter().filterPacket(in);
        control.control(out);
                    //test code
            ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
            if(Q==null)
            {
                
                Tmpdiff128CochleaCommunication.initBlockingQ();
                log.info("q was null");
            }
            CommunicationObject co=new CommunicationObject();
            co.setForCochlea(true);
            co.setIsCochleaEnabled(true);
            try
            {
            //Q.put(co);
            System.out.println("Size before "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
            Tmpdiff128CochleaCommunication.putBlockingQ(co);
            log.info("Wrote into queue");
            System.out.println("Size after "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
            }
            catch(Exception e)
            {
                log.info("Problem putting packet for cochlea in retina");
                e.printStackTrace();
            }
        return out;
    }
    
    /*this function detects if events in packet are from retina or cochlea. Boolean retina is true if from
     *retina and false otherwise. depending on choice only the packets represented of choice are sent.*/
    
    private EventPacket<?> getSeperatePackets(EventPacket<?> in, boolean retina) {
        log.info("Function seperator called");
        Iterator inItr=in.inputIterator();
        //loop through events in EventPacket.
        while(inItr.hasNext()) {
            BasicEvent e =(BasicEvent)inItr.next();
            
            if(retina) //if we are checking for retina
            {
                log.info("In while loop"+e.x);
                
                if(e.x>100) //if not retina address
                {
                    log.info("Found a cochlea packet size");
                    inItr.remove(); //remove the current packet
                }
            }
        }
        return in;
    }
    public Object getFilterState() {
        return null;
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

