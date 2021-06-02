/*
ClusterEvent *

 */


package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;

/**
 * Typed event, used to tag a cluster identifier to events.  xp and yp indicate
 * coordinates within the cluster.
 * 
 * @author Peter
 */
public class ClusterEvent extends RectangularClusterTrackerEvent {
    
    public short xp;
    public short yp;
    
    /** The orientation value. */
    public byte clusterid=0;
    
    public int nclusters=4;
        
    @Override
    public ClusterSet.ExtCluster getCluster()
    {   return (ClusterSet.ExtCluster)super.getCluster();
    }
    
    @Override public int getType(){
        return (byte)(Math.abs(clusterid)%4);
    }
    
    @Override public String toString(){
        return super.toString()+" cluster="+cluster;
    }
    
    @Override public int getNumCellTypes() {
        return nclusters;
    }
    
    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    public void copyFrom(ClusterEvent src){
        //RectangularClusterTrackerEvent e=(RectangularClusterTrackerEvent)src;
        super.copyFrom(src);
//        if(e instanceof ClusterEvent) 
//        {   
            this.clusterid=((ClusterEvent)src).clusterid;
            this.nclusters=((ClusterEvent)src).nclusters;
            this.setCluster(src.getCluster());
            this.xp=src.xp;
            this.yp=src.yp;
//        }
        }
    
    
    
    }
    