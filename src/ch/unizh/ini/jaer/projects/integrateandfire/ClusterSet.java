/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */




package ch.unizh.ini.jaer.projects.integrateandfire;


// JAER Stuff
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;


import java.awt.geom.Point2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;


/**
 * @description This is a handy class for 
 *
 * @author tobi
 */
public class ClusterSet extends RectangularClusterTracker {
/* Centers the image by taking a moving average.  Note: it'd be nice to transform 
 * this to a moving median, to make it more noise tolerant, but it's a little 
 * trickier to program. */
    
    float desiredScale=getFloat("desiredScale",45);
    boolean scaleIt=getBoolean("scaleIt",true);
    boolean centerIt=getBoolean("centerIt",true);
    boolean transformPoints=getBoolean("transformPoints",false);
    /*
    public class Cluster extends RectangularClusterTracker.Cluster{
    
        float velx=0;
        float vely=0;
        
        @Override
        protected void addEvent(BasicEvent ev, OutputEventIterator outItr) {
            addEvent(ev);
            if (!isVisible()) {
                return;
            }
            ClusterEvent oe = (ClusterEvent) outItr.nextOutput();
            oe.copyFrom(ev);
            
            oe.clusterid=(byte)this.getClusterNumber();
            
            oe.type=oe.clusterid;
//            oe.setX((short) getLocation().x);
//            oe.setY((short) getLocation().y);
            
            //oe.setCluster(this);
        }
        
        @Override
        protected void updatePosition(BasicEvent event, float m)
        {
            float m1 = 1 - m;
            //location.x = (m1 * location.x + m * event.x);
            //location.y = (m1 * location.y + m * event.y);

            velx = (m1 * velx + m * (event.x-location.x));
            vely = (m1 * velx + m * (event.y-location.y));
            
            location.x=location.x+velx;
            location.y=location.y+vely;
            
        };
    
    };
    */
    
    
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in.getSize() == 0) {
            return in; // added so that packets don't use a zero length packet to set last timestamps, etc, which can purge clusters for no reason
        }//        EventPacket out; // TODO check use of out packet here, doesn't quite make sense
        /*
        checkOutputPacketEventType(ClusterEvent.class);
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
            out = track((EventPacket<BasicEvent>) in);
        } else {
            out = track((EventPacket<BasicEvent>) in);
        }*/
        checkOutputPacketEventType(ClusterEvent.class);
        out = track((EventPacket<BasicEvent>) in);
        
        if (!filterEventsEnabled) return out; 
        
        for(Object e:out)
        {   // iterate over the input packet**
                        
            //BasicEvent tmp=(BasicEvent)e;
            ClusterEvent E=(ClusterEvent)e; // cast the object to basic event to get timestamp, x and y**
            
            
            E.nclusters=super.getMaxNumClusters();
            E.clusterid=(byte)(E.getCluster().getClusterNumber() % E.nclusters);
            //E.type=E.clusterid; E.
            
            
            //E.type=(byte) (E.getCluster().hashCode() % super.getMaxNumClusters()); // TODO: replace this
            
            Point2D loc=E.getCluster().location;
            float dis=E.getCluster().getAverageEventDistance();
            
            E.xp=E.x;
            E.yp=E.y;
            
            if (centerIt)
            {   E.xp-=loc.getX();
                E.yp-=loc.getY();
            }
            if (scaleIt)
            {   E.xp*=desiredScale/dis;
                E.yp*=desiredScale/dis;
            }
            
            if (centerIt || scaleIt)
            {
                E.xp=(short)Math.min(127,Math.max(E.xp+64, 0));
                E.yp=(short)Math.min(127,Math.max(E.yp+64, 0));         
                
                if (transformPoints)
                {   E.x=E.xp;
                    E.y=E.yp;
                    
                }
            }
            
            //E.y+=64-loc.getY();
            
            //E.getCluster().hashCode();
        }
        
        
        /**/
        return out;
        
    }
    
    //==========================================================================
    // Initialization and Startup

    @Override public void initFilter(){
        super.initFilter();
        
        // Gimme some default properties
        super.setFilterEventsEnabled(true);
        super.setSurroundInhibitionEnabled(true);
        super.setSmoothMove(true);
        super.setMaxNumClusters(4);
    }
    
    // Read the Network File on filter Reset
    @Override public void resetFilter(){
        
    }

    
    //  Initialize the filter
    public  ClusterSet(AEChip  chip){
        super(chip);

        NeuronMapFilter NM=new NeuronMapFilter(chip);
        NM.initFilter();
        this.setEnclosedFilter(NM);
        this.filterEnabled=true;
        
        // setPropertyTooltip("N", "Weight");
        setPropertyTooltip("Normalization","centerIt", "Chose whether to center the image");
        setPropertyTooltip("Normalization","scaleIt", "Chose whether to scale the image to the desired Scale");
        setPropertyTooltip("Normalization","desiredScale", "How big to make the normalized number");
        setPropertyTooltip("Normalization","transformPoints", "Should the event locations actually be transformed?");
    }
    
    public boolean getCenterIt()
    {   return centerIt;
    }
    
    public void setCenterIt(boolean value)
    {   centerIt=value;
        getPrefs().putBoolean("ClusterSet.CenterIt",value);
        support.firePropertyChange("centerIt",this.centerIt,value);
    }

    public boolean getScaleIt()
    {   return scaleIt;
    }
    
    public void setScaleIt(boolean value)
    {   scaleIt=value;
        getPrefs().putBoolean("ClusterSet.ScaleIt",value);
        support.firePropertyChange("scaleIt",this.scaleIt,value);
    }
    
    public float getDesiredScale()
    {   return desiredScale;
    }
    
    public void setDesiredScale(float value)
    {   desiredScale=value;
        getPrefs().putFloat("ClusterSet.DesiredScale",value);
        support.firePropertyChange("desiredState",this.desiredScale,value);
    }
    
    public boolean getTransformPoints()
    {   return transformPoints;
    }
    
    public void setTransformPoints(boolean value)
    {   transformPoints=value;
        getPrefs().putBoolean("ClusterSet.showScaled",value);
        support.firePropertyChange("showScaled",this.transformPoints,value);
    }

}
