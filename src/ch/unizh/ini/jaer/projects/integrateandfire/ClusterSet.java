/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */




package ch.unizh.ini.jaer.projects.integrateandfire;


// JAER Stuff
//import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader;
import com.sun.opengl.util.GLUT;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;


import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;

/**
 * @description An extension of RectangularClusterTracker for generating typed 
 * events, with new coordinates, based on cluster membership.
 * 
 *
 * @author Peter
 */
public class ClusterSet extends RectangularClusterTracker {

/* Centers the image by taking a moving average.  Note: it'd be nice to transform 
 * this to a moving median, to make it more noise tolerant, but it's a little 
 * trickier to program. */
    
    float desiredScale=getFloat("desiredScale",45);
    boolean scaleIt=getBoolean("scaleIt",true);
    boolean centerIt=getBoolean("centerIt",true);
    boolean transformPoints=getBoolean("transformPoints",false);
    
    boolean showClusterIndex=getBoolean("showClusterIndex",false);
    
    boolean[] indexOccupied;
    
    protected void initIndexOccupied()
    {
        indexOccupied=new boolean[super.getMaxNumClusters()]; // Should be initialized to false by default
        
    }
    
    
    
    public class ExtCluster extends RectangularClusterTracker.Cluster{
    
        float velx=0;
        float vely=0;
        
        int index=-1;          // Index associated with cluster.  Designed such that clusters have unique indeces.  Index never changes
        
        String tag="";
        
        // Inconvenience of wrapping constructors (thanks Java!)
        public ExtCluster(){super();}
        public ExtCluster(BasicEvent ev){ super(ev);}
        public ExtCluster(ExtCluster ein, ExtCluster dwei){super(ein,dwei);}
        protected ExtCluster(BasicEvent ev, OutputEventIterator outItr) {super(ev,outItr);}
        
        @Override
        protected void addEvent(BasicEvent ev, OutputEventIterator outItr) {
            addEvent(ev);
            if (!isVisible()) {
                return;
            }
            ClusterEvent oe = (ClusterEvent) outItr.nextOutput();
            oe.copyFrom(ev);
            //oe.clusterid=(byte)(this.index % chip.getRenderer().getTypeColors().length);
            oe.clusterid=(byte)(Math.abs(this.index) % 4); // TODO: Do this properly
            oe.nclusters=getMaxNumClusters();
            //oe.type=oe.clusterid;
            oe.setCluster(this);
        }
        
        
        @Override
        public void draw(GLAutoDrawable drawable) {
            super.draw(drawable);
            final int font = GLUT.BITMAP_HELVETICA_18;
            if (showClusterIndex) {
                chip.getCanvas().getGlut().glutBitmapString(font, String.format("ix=%d  ", this.index));
            }
            
            if (!tag.isEmpty())
                    chip.getCanvas().getGlut().glutBitmapString(font, this.tag);
        }
        
        public void annotate(String text){
            final int font = GLUT.BITMAP_HELVETICA_18;
            chip.getCanvas().getGlut().glutBitmapString(font, text);
        }
    };
    
    
    @Override
    public ExtCluster createCluster() {
        return new ExtCluster();
    }

    @Override
    public ExtCluster createCluster(BasicEvent ev) {
        return new ExtCluster(ev);
    }

    @Override
    public ExtCluster createCluster(RectangularClusterTracker.Cluster one, RectangularClusterTracker.Cluster two) {
        return new ExtCluster((ExtCluster)one, (ExtCluster)two);
    }

    @Override
    public ExtCluster createCluster(BasicEvent ev, OutputEventIterator itr) {
        return new ExtCluster(ev, itr);
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in.getSize() == 0 || !filterEnabled) {
            return in; // added so that packets don't use a zero length packet to set last timestamps, etc, which can purge clusters for no reason
        }
        
        checkOutputPacketEventType(ClusterEvent.class);
        out = track((EventPacket<BasicEvent>) in);
        
        if (!filterEventsEnabled) return out; 
        
        updateIndexOccupied(); // Update the cluster indeces list
        
        for(Object e:out)
        {   // iterate over the input packet**
                        
            //BasicEvent tmp=(BasicEvent)e;
            ClusterEvent E=(ClusterEvent)e; // cast the object to basic event to get timestamp, x and y**
            
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
        }
        
        return out;
    }
    
    protected void updateIndexOccupied()
    {   // Alright surely ther's a more efficient way than this, but for now, this'll have to do.
        
        // Clear indeces
        for (int i=0; i<indexOccupied.length; i++)
            indexOccupied[i]=false;
        
        // Mark still-used places
        for (Cluster c:clusters)
            if (((ExtCluster)c).index!=-1)
                indexOccupied[((ExtCluster)c).index]=true;
                
        // Occupy empty spaces with unused clusters
        for (Cluster ci:clusters)
            if (((ExtCluster)ci).index==-1) // If cluster has no index, search for first free one.
                for (int i=0; i<indexOccupied.length; i++)
                    if (!indexOccupied[i])
                    {   ((ExtCluster)ci).index=i;
                        indexOccupied[i]=true;
                        break;
                    }
                    else if (i==indexOccupied.length-1)
                    {   System.out.println("Error-- Should never get here");
                        i=i+1;
                        break;
                    }
        // By this point no cluster should have an index of -1.
    }
    
    
    
    //==========================================================================
    // Initialization and Startup

    @Override public void initFilter(){
        super.initFilter();
        
        resetFilter();
    }
    
    // Read the Network File on filter Reset
    @Override public void resetFilter(){
        
        // Gimme some default properties
        super.setFilterEventsEnabled(true);
        super.setSurroundInhibitionEnabled(true);
        super.setSmoothMove(true);
        this.setMaxNumClusters(4);
        super.setClusterLifetimeWithoutSupportUs(500000);
    }

    //  Initialize the filter
    public  ClusterSet(AEChip  chip){
        super(chip);

        /*
        NeuronMapFilter NM=new NeuronMapFilter(chip);
        NM.initFilter();
        this.setEnclosedFilter(NM);
        this.filterEnabled=true;
        */
        
        setPropertyTooltip("Normalization","centerIt", "Chose whether to center the image");
        setPropertyTooltip("Normalization","scaleIt", "Chose whether to scale the image to the desired Scale");
        setPropertyTooltip("Normalization","desiredScale", "How big to make the normalized number");
        setPropertyTooltip("Normalization","transformPoints", "Should the event locations actually be transformed?");
        setPropertyTooltip("Display","showClusterIndex", "Show the index of the cluster");
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
    
    
    @Override
    public void setMaxNumClusters(int maxNumClusters)
    {       
        super.setMaxNumClusters(maxNumClusters);
        initIndexOccupied();
    }
    
    public void setShowClusterIndex(boolean value)
    {   showClusterIndex=value;
        getPrefs().putBoolean("ClusterSet.showClusterIndex",value);
        support.firePropertyChange("showClusterIndex",this.showClusterIndex,value);
    }
    
    public boolean getShowClusterIndex()
    {   return showClusterIndex;
    }

}
