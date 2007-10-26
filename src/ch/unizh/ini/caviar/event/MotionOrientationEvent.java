/*
 * MotionOrientationEvent.java
 *
 * Created on May 28, 2006, 4:09 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.event;

import java.awt.geom.*;

/**
 * Represents an event with direction of motion and delay
 * 
 * @author tobi
 */
public class MotionOrientationEvent extends OrientationEvent {
    
    /** the direciton of motion, a quantized value indexing into Dir */
    public byte direction=0;
    
    /** unit vector of direction of motion */
    public Dir dir=null;
    
    /** the 'delay' value of this cell, an analog (but quantized) quantity that signals the time delay assoicated with this event.
     Smaller time delays signal larger speed */
    public short delay=0;
    
    /** the distance associated with this motion event. This is the distance in pixels to the prior event that signaled this direction */
    public byte distance=0;
    
    /** speed in pixels per second */
    public float speed=0;
    
    /** stores computed velocity (DirectionSelectiveFilter computes it). 
     This vector points in the direction of motion and has units of pixels per second (PPS).
     */
    public Point2D.Float velocity=new Point2D.Float();
    
    
    /** Creates a new instance of MotionOrientationEvent */
    public MotionOrientationEvent() {
    }
    
    @Override public int getType(){
        return direction;
    }
    
    @Override public String toString(){
        return super.toString()+" direction="+direction+" distance="+distance+" delay="+delay+" speed="+speed;
    }
    
    public int getNumCellTypes() {
        return 8;
    }
    
    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        OrientationEvent e=(OrientationEvent)src;
        super.copyFrom(src);
        if(e instanceof MotionOrientationEvent){
            this.direction=((MotionOrientationEvent)e).direction;
            this.delay=((MotionOrientationEvent)e).delay;
        }
    }
    
    public static float computeSpeedPPS(MotionOrientationEvent e){
        if(e.delay==0) return 0;
        else return 1e6f*(float)e.distance/e.delay;
    }
    
    private static Point2D.Float motionVector=new Point2D.Float();
    /** @return the newly-computed motion vector that uses the distance of the event and the delay.
     The speed is the distance divided by the delay and is in pixels per second.
     */
    static public Point2D.Float computeMotionVector(MotionOrientationEvent e){
        Dir d=unitDirs[e.direction];
        int dist=e.distance;
        int delay=e.delay; if(delay==0) delay=1;
        float speed=( (float)dist / ((float)delay*1e-6f) );
        motionVector.setLocation(d.x*speed,d.y*speed);
        return motionVector;
    }
    
    /** represents a direction. The x and y fields represent relative offsets in the x and y directions by these amounts. */
    public static final class Dir{
        public int x, y;
        Dir(int x, int y){
            this.x=x;
            this.y=y;
        }
    }
    
    /**
     *     An array of 8 nearest-neighbor unitDirs going CCW from down (south) direction.
     *     <p>
     *    these unitDirs are indexed by inputType, then by (inputType+4)%8 (opposite direction)
     *    when input type is orientation, then input type 0 is 0 deg horiz edge, so first index could be to down, second to up
     *    so list should start with down
     *    IMPORTANT, this order *depends* on DirectionSelectiveFilter order of orientations
     */
    public static final Dir[] unitDirs={
        new Dir(0,-1), // down
        new Dir(1,-1), // down right
        new Dir(1,0), // right
        new Dir(1,1), // 45 up right
        new Dir(0,1), // up
        new Dir(-1,1), // up left
        new Dir(-1,0), // left
        new Dir(-1,-1), // down left
    };

}
