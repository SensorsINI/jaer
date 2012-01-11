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
    
    short xp;
    short yp;
    
    /** The orientation value. */
    public byte clusterid=0;
    
    public int nclusters=4;
        
    @Override public int getType(){
        return clusterid;
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
    /*
    @Override public void copyFrom(BasicEvent src){
        RectangularClusterTrackerEvent e=(RectangularClusterTrackerEvent)src;
        super.copyFrom(e);
        if(e instanceof ClusterEvent) 
        {   this.clusterid=((ClusterEvent)e).clusterid;
            this.nclusters=((ClusterEvent)e).nclusters;
        }
        }*/
    
    
    
    }
    