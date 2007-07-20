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
import java.awt.geom.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.FilterChain;
import ch.unizh.ini.caviar.eventprocessing.tracking.*;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import ch.unizh.ini.caviar.eventprocessing.filter.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.usb.Shooter;
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
    //private XYTypeFilter xyfilter;
    private MultipleXYTypeFilter xyfilter;
    private RectangularClusterTracker secondClusterFinder, ballTracker;
    private FilterChain filterchain,filterchainMain;
    private TargetDetector targetDetect;
    private ShooterControl control;
    protected AEChip chip;
    private Shooter shooter=null;
    static Logger log=Logger.getLogger("BallShooter");
    private float upEventRateThreshold=getPrefs().getFloat("BallShooter.upEventRateThreshold",0.2f);
    private float dnEventRateThreshold=getPrefs().getFloat("BallShooter.dnEventRateThreshold",0.05f);
    private float azmScale=getPrefs().getFloat("BallShooter.azmScale",0.5f);
    private float azmoffset=getPrefs().getFloat("BallShooter.azmoffset",0f);
    private float reduceXYfactor=getPrefs().getFloat("BallShooter.reduceXYfactor",0.8f);
    {setPropertyTooltip("radiusReduceFactor","Reduce the radius to detect inner box");}
    private float shooterStopVal=getPrefs().getFloat("BallShooter.shooterStopVal",0.8f);
    private Bbox[] tbox; //coordinates of final targets
    //private float radiusReduceFactor=getPrefs().getFloat("BallShooter.radiusReduceFactor",0.8f);
    
    
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
        
        
        public TargetDetector(AEChip chip){
            super(chip);
            firstClusterFinder=new RectangularClusterTracker(chip);
            xyfilter=new MultipleXYTypeFilter(chip);
            
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
            
            setEnclosedFilterChain(filterchain);
            
            initFilter();
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
            if(!isFilterEnabled())
                return in;
            //log.info("Box info "+xyfilter.getStartX()+" "+xyfilter.getStartY()+" "+xyfilter.getEndX()+" "+xyfilter.getEndY()+"\n ");
            return filterchain.filterPacket(in);
        }
        private void setEnclosedFilterEnabledAccordingToPref(EventFilter filter, Boolean enb){
            String key="TargetDetector."+filter.getClass().getSimpleName()+".filterEnabled";
            if(enb==null){
                // set according to preference
                boolean en=getPrefs().getBoolean(key,false); // default disabled
                filter.setFilterEnabled(en);
            }else{
                boolean en=enb.booleanValue();
                getPrefs().putBoolean(key,en);
            }
        }
        public void resetFilter() {
            //filterchain.reset();
            initFilter();
        }
        
        public void initFilter() {
            xyfilter.setXEnabled(true);
            xyfilter.setYEnabled(true);
            firstClusterFinder.setFilterEnabled(false);
            xyfilter.setFilterEnabled(false);
            secondClusterFinder.setFilterEnabled(false);
            firstClusterFinder.setDynamicSizeEnabled(false);
            secondClusterFinder.setDynamicSizeEnabled(false);
            xyfilter.setMaxBoxNum(firstClusterFinder.getMaxNumClusters());
            
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
        
        @Override public boolean isFilterEnabled(){
            return this.filterEnabled; // force active
        }
        
        
    }
    private class ShooterControl {
        boolean targetSeen; // checks if target was seen atleast once
        boolean targetDetected;
        boolean msgSentToCochlea;
        boolean servoSuccess;
        float itdValue;
        boolean boardHit;
        Cluster c;
        List<RectangularClusterTracker.Cluster> cluster;
        CommunicationObject co;
        public ShooterControl() {
            initControl();
            
        }
        void initControl() {
            targetSeen=false; // checks if target was seen atleast once
            targetDetected=false;
            msgSentToCochlea=false;
            servoSuccess=false;
            itdValue=0;
            boardHit=false;
            
            co=null;
            if(shooter==null)//if shooting first time
            {
                shooter=new Shooter(true); //no gui
                log.info("servo init");
                servoSuccess=shooter.initServo();
            } else {
                shooter.sendShooterServoVals();
                servoSuccess=true;
                shooter.setStopVal(shooterStopVal);
            }
            
            
        }
        void control(EventPacket in) {
            //first detect the target
            if(targetDetected) {
                if(!msgSentToCochlea) //send message to cochlea only if it was not sent
                {
                    log.info("Target Detected finally");
                    targetSeen=false;
                    targetDetect.setFilterEnabled(false); //stop the detector filter.
                    //System.out.println(targetDetect.isFilterEnabled());
                    sendMsgToCochlea(true);
                    msgSentToCochlea=true;//message for ITD enable was sent
                    //log.info(tbox[0].startx+" "+tbox[0].starty+" "+tbox[0].endx+" "+tbox[0].endy+"\n ");
                } else // wait to see if cochlea responded.
                {
                    co=getITDValueFromCochlea();
                    if(co!=null)//if cochlea did respond
                    {
                        log.info("Cochlea Responded with ITD value "+this.itdValue);
                        
                        servoSuccess=true;
                        if(servoSuccess) {
                            float aim=0.5f;
                            int dir=0;
                            if(itdValue>0) //get the direction of box
                                aim=Math.max((tbox[0].startx+tbox[0].endx),((tbox[1].startx+tbox[1].endx)))/(2*chip.getSizeX());
                            else
                                aim=Math.min((tbox[0].startx+tbox[0].endx),((tbox[1].startx+tbox[1].endx)))/(2*chip.getSizeX());
                            
                            //aim=(azmScale*(aim+0.5)) + azmoffset; //linear map
                            aim=azmScale*(aim-0.5f)+0.5f +azmoffset;
                            log.info("Final Aim"+Float.toString(aim));
                            shooter.setAimVal(aim);
                            shooter.delayMs(1000);
                            shooter.shoot();
                        }
                    }
//                    cluster=ballTracker.getClusters();
//                    if(cluster.size()>0) {
//                        c=cluster.get(0);
//                        java.awt.geom.Point2D.Float  velocity=c.getVelocity();
//                        //System.out.println(cluster.size()+" "+velocity.getX()+" "+velocity.getY());
//                        double temp=(velocity.getY()/velocity.getX());
//                        System.out.println(Math.atan(temp));
//                    }
                }
            } else {
                targetDetected=detectTarget();
            }
            //log.info("Target Detected is "+Boolean.toString(targetDetected));
        
    }
    private void sendMsgToCochlea(boolean enbITD) {
        ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
        if(Q==null) {
            
            Tmpdiff128CochleaCommunication.initBlockingQ();
            log.info("q was null");
        }
        CommunicationObject co=new CommunicationObject();
        co.setForCochlea(true);
        co.setItdFilterEnabled(enbITD);//ask cochlea to start ITD
        try {
            //Q.put(co);
            //System.out.println("Size before "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
            Tmpdiff128CochleaCommunication.putBlockingQ(co);
            log.info("Asked Cochlea to start ITD");
            //System.out.println("Size after "+Tmpdiff128CochleaCommunication.sizeBlockingQ());
        } catch(Exception e) {
            log.info("Problem putting packet for cochlea in retina");
            e.printStackTrace();
        }
    }
    private CommunicationObject getITDValueFromCochlea() {
        CommunicationObject co=null;
        ArrayBlockingQueue Q=Tmpdiff128CochleaCommunication.getBlockingQ();
        if(Q==null) //this should not happen! coz retina would have written before coming here
        {
            
            //Tmpdiff128CochleaCommunication.initBlockingQ();
            log.warning("q was null! when reading from cochlea");
            return co;
        }
        if(Q.size()>0)//there is some message present
        {
            co=(CommunicationObject)Tmpdiff128CochleaCommunication.peekBlockingQ();
            if(co.isForRetina())//if packet is for cochlea
            {
                try{
                    co=(CommunicationObject)Q.poll();
                    if(co.getControllerMsgFromCochleaType()==CommunicationObject.ITDVAL)//message is about ITDVAL
                        this.itdValue=co.getItdValue();
                    else if(co.getControllerMsgFromCochleaType()==CommunicationObject.TARTGETHIT)//if board was hit
                        this.boardHit=co.isBoardHit();
                } catch (Exception e) {
                    log.info("Problem in Cochlea while polling");
                    e.printStackTrace();
                }
            } else
                co=null; //set co to null as packet was not for retina. means cochlea didnt read yet
        }
        
        
        return co;
    }
    //detects target. We have to somehow tell controlling function that target is detected.
    boolean detectTarget() {
        boolean found=false;
        
        List<RectangularClusterTracker.Cluster> clusterList=firstClusterFinder.getClusters();
        //the size will basically be either 1 or 2
        //first calculate average eventrate
        float rateSum=0;
        for(int ctr=0;ctr<clusterList.size();ctr++) {
            rateSum+=clusterList.get(ctr).getAvgEventRate();
        }
        float avgRate=rateSum/clusterList.size();
        //System.out.println(avgRate);
        if(!targetSeen ) //if the target was not seen previously
        {
            if(!firstClusterFinder.isFilterEnabled())
                firstClusterFinder.setFilterEnabled(true);
            if(avgRate>upEventRateThreshold) //check target seen
            {
                //firstClusterFinder.setDynamicSizeEnabled(true);
                
                xyfilter.setFilterEnabled(true);
                xyfilter.setMaxBoxNum(firstClusterFinder.getMaxNumClusters());
                secondClusterFinder.setFilterEnabled(true);
                secondClusterFinder.setMaxNumClusters(firstClusterFinder.getMaxNumClusters());
                secondClusterFinder.setDynamicSizeEnabled(true);
                targetSeen=true;
                log.info("Target Seen!");
                
            } else  //the target is not seen
                return found;
        } else //target was seen previously
        {
            if(avgRate>upEventRateThreshold && clusterList.size()==firstClusterFinder.getMaxNumClusters())//storing co-ordinates
            {
                List<RectangularClusterTracker.Cluster> SecondClusterList=firstClusterFinder.getClusters();
                for(int ctr=0;ctr<SecondClusterList.size();ctr++) {
                    Cluster clst=SecondClusterList.get(ctr);
                    float radius=(float)clst.getRadius();
                    java.awt.geom.Point2D.Float location=clst.getLocation();
                    float aspectRatio=clst.getAspectRatio();
                    tbox[ctr]=getBox(radius,location,aspectRatio);
                    //log.info(tbox[ctr].startx+" "+tbox[ctr].starty+" "+tbox[ctr].endx+" "+tbox[ctr].endy+"\n ");
                }
            }
            
            if(avgRate<getDnEventRateThreshold()) //came here as target was seen and now check if it dissappeared
            {
                found=true;
                return found;
            }
        }
        
        for(int ctr=0;ctr<clusterList.size();ctr++) {
            
            
            //for each cluster get location raius and aspect ratio
            Cluster clst=clusterList.get(ctr);
            float radius=reduceXYfactor*clst.getRadius();
            java.awt.geom.Point2D.Float location=clst.getLocation();
            float aspectRatio=clst.getAspectRatio();
            Bbox box=getBox(radius,location,aspectRatio);
            
            //log.info(" Before Box info "+box.startx+" "+box.starty+" "+box.endx+" "+box.endy+"\n ");
            xyfilter.setStartX((int)(box.startx),ctr);
            xyfilter.setStartY((int)(box.starty),ctr);
            xyfilter.setEndX((int)(box.endx),ctr);
            xyfilter.setEndY((int)(box.endy),ctr);
            //log.info("After Box info "+ctr+" "+xyfilter.getStartX(ctr)+" "+xyfilter.getStartY(ctr)+" "+xyfilter.getEndX(ctr)+" "+xyfilter.getEndY(ctr)+"\n ");
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
    public void reset() {
        initControl();
    }
}
//******************************************************************************
/** Creates a new instance of BallShooter */
public BallShooter(AEChip chip) {
    super(chip);
    this.chip=chip;
    chip.getCanvas().addAnnotator(this);
    control=new ShooterControl();
    filterchainMain = new FilterChain(chip);
    targetDetect=new TargetDetector(chip);
    ballTracker=new RectangularClusterTracker(chip);
    tbox=new Bbox[2];
    
    
    filterchainMain.add(targetDetect);
    filterchainMain.add(ballTracker);
    
    targetDetect.setEnclosed(true,this);
    ballTracker.setEnclosed(true,this);
    
    setEnclosedFilterChain(filterchainMain);
    
    
    initFilter();
}


public void initFilter() {
    //setEnclosedFilter(targetDetect);
    targetDetect.setFilterEnabled(false);
    
    ballTracker.setFilterEnabled(false);
    firstClusterFinder.setDynamicSizeEnabled(false);
    
    ballTracker.setFilterEnabled(true);
    ballTracker.setAspectRatio(1.0f);
    ballTracker.setClusterSize(0.058f);
    ballTracker.setMaxNumClusters(1);
    ballTracker.setPathsEnabled(true);
    ballTracker.setClusterLifetimeWithoutSupportUs(3000);
    ballTracker.setThresholdEventsForVisibleCluster(45);
}

public EventPacket<?> filterPacket(EventPacket<?> in) {
    EventPacket out;
    if(in==null) return null;
    if(!filterEnabled) return in;
    
    //out= getEnclosedFilter().filterPacket(in);
    out=filterchainMain.filterPacket(in);
    control.control(out);
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
    initFilter();
    targetDetect.resetFilter();
    control.reset();
    
}
public void annotate(GLAutoDrawable drawable) {
    //((FrameAnnotater)clusterFinder).annotate(drawable);
}
public void annotate(Graphics2D g) {
}
public void annotate(float[][][] frame) {
}

   /* public double getRadiusReduceFactor() {
        return radiusReduceFactor;
    }
    
    public void setRadiusReduceFactor(float radiusReduceFactor) {
        this.radiusReduceFactor = radiusReduceFactor;
        getPrefs().putFloat("BallShooter.radiusReduceFactor",radiusReduceFactor);
    }*/

public float getUpEventRateThreshold() {
    return upEventRateThreshold;
}

public void setUpEventRateThreshold(float upEventRateThreshold) {
    this.upEventRateThreshold = upEventRateThreshold;
    getPrefs().putFloat("BallShooter.upEventRateThreshold",upEventRateThreshold);
}

public float getDnEventRateThreshold() {
    return dnEventRateThreshold;
}

public void setDnEventRateThreshold(float dnEventRateThreshold) {
    this.dnEventRateThreshold = dnEventRateThreshold;
    getPrefs().putFloat("BallShooter.dnEventRateThreshold",dnEventRateThreshold);
}

public float getReduceXYfactor() {
    return reduceXYfactor;
}

public void setReduceXYfactor(float reduceXYfactor) {
    this.reduceXYfactor = reduceXYfactor;
    getPrefs().putFloat("BallShooter.reduceXYfactor",reduceXYfactor);
}



public float getAzmoffset() {
    return azmoffset;
}

public void setAzmoffset(float azmoffset) {
    this.azmoffset = azmoffset;
    getPrefs().putFloat("BallShooter.azmoffset",azmoffset);
}

public float getAzmScale() {
    return azmScale;
}

public void setAzmScale(float azmScale) {
    this.azmScale = azmScale;
    getPrefs().putFloat("BallShooter.azmScale",azmScale);
}

public float getShooterStopVal() {
    return shooterStopVal;
}

public void setShooterStopVal(float shooterStopVal) {
    this.shooterStopVal = shooterStopVal;
    getPrefs().putFloat("BallShooter.shooterStopVal",shooterStopVal);
}

}

