/*
ClusterEvent *

 */


package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTrackerEvent;

/**
 * Represents an event with an orientation that can take 4 values.
 <p>
 Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
 
 * @author tobi
 */
public class ClusterEvent extends RectangularClusterTrackerEvent {
    
    short xp;
    short yp;
    
    /** The orientation value. */
    public byte clusterid=0;
    
    public int nclusters=4;
    
    /** Defaults to true; set to false to indicate unknown orientation. */
    
    /** Creates a new instance of OrientationEvent */
    public ClusterEvent() {
        
        
    }
    
    /**
     Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
     * 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
     @see #hasOrientation
     */
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
    
    /** represents a unit orientation.
     The x and y fields represent relative offsets in the x and y directions by these amounts. 
    public static final class UnitVector{
        public float x, y;
        UnitVector(float x, float y){
            float l=(float)Math.sqrt(x*x+y*y);
            x=x/l;
            y=y/l;
            this.x=x;
            this.y=y;
        }
    }
    */
    /**
     *     An array of 4 nearest-neighbor unit vectors going CCW from horizontal.
     *     <p>
     *    these unitDirs are indexed by inputType, then by (inputType+4)%4 (opposite direction)
     *    when input type is orientation, then input type 0 is 0 deg horiz edge, so first index could be to down, second to up
     *    so list should start with down
     
    public static final UnitVector[] unitVectors={
        new UnitVector(1,0), 
        new UnitVector(1,1), 
        new UnitVector(0,1), 
        new UnitVector(-1,1), 
    };
  */  
    
