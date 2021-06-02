/*MotionOrientationEvent.java
 *
 * Created on May 28, 2006, 4:09 PM
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.event.orientation;

import java.awt.geom.Point2D;
import net.sf.jaer.event.BasicEvent;

/** Represents an event with direction of motion and delay for DVS sensors.
 * @author tobi */
public class DvsMotionOrientationEvent extends DvsOrientationEvent implements MotionOrientationEventInterface {
    
    /** the direction of motion, a quantized value indexing into Dir */
    protected byte direction=0;
    
    /** Defaults to true; set to false to indicate unknown direction. */
    protected boolean hasDirection = true;
     
    /** unit vector of direction of motion */
    protected Dir dir=null;
    
    /** the 'delay' value of this cell in us (unit of timestamps), an analog 
     * (but quantized) quantity that signals the time delay associated with this event.
     * Smaller time delays signal larger speed */
    protected int delay=0;
    
    /** the distance associated with this motion event. This is the 
     * distance in pixels to the prior event that signaled this direction 
     * Note: This distance is neither euclidian not taxicab. Its the distance
     *       in the receptive field of the current orientation. This means that
     *       the pixel at location (1,1) relative to current location has a 
     *       distance of '1' (in a diagonal direction event) as has the pixel
     *       at (1,0) a distance of '1' (in a horizontal direction event) */
    protected byte distance=0;
    
    /** speed in pixels per second */
    protected float speed=0;
    
    /** stores computed velocity. 
     * (implementations of AbstractDirectionSelectiveFilter compute it). 
     * This vector points in the direction of motion and has 
     * units of pixels per second (PPS). */
    protected Point2D.Float velocity=new Point2D.Float();
    
    protected static final Point2D.Float motionVector=new Point2D.Float();
    
    /** Creates a new instance of event */
    public DvsMotionOrientationEvent() { }
    
    @Override public int getType(){
        return getDirection();
    }
    
    @Override public String toString(){
        return super.toString()+" direction="+getDirection()+" distance="+getDistance()+" delay="+getDelay()+" speed="+getSpeed();
    }
    
    @Override public int getNumCellTypes() {
        return 8;
    }
    
    /** copies fields from source event src to this event
     * @param src the event to copy from */
    @Override public void copyFrom(BasicEvent src){
        super.copyFrom(src);
        if(src instanceof DvsMotionOrientationEvent){
            this.setDirection(((DvsMotionOrientationEvent)src).getDirection());
            this.setHasDirection(((DvsMotionOrientationEvent)src).isHasDirection());
            this.setDir(((DvsMotionOrientationEvent)src).getDir());
            this.setDelay(((DvsMotionOrientationEvent)src).getDelay());
            this.setDistance(((DvsMotionOrientationEvent)src).getDistance());
            this.setSpeed(((DvsMotionOrientationEvent)src).getSpeed());
            this.setVelocity(((DvsMotionOrientationEvent)src).getVelocity());
        }
    }
    
    public static float computeSpeedPPS(DvsMotionOrientationEvent e){
        if(e.delay==0) return 0;
        else return 1e6f*(float)e.distance/e.delay;
    }
    

    /** computes the motionVectors for a given Event
     * @param e a MotionOrientationEvent to compute MotionVectors from
     * @return The newly-computed motion vector that uses the 
     *         distance of the event and the delay. The speed is the 
     *         distance divided by the delay and is in pixels per second. */
    static public Point2D.Float computeMotionVector(DvsMotionOrientationEvent e){
        MotionOrientationEventInterface.Dir d=unitDirs[e.getDirection()];
        int dist=e.distance;
        int delay=e.delay; 
        if(delay==0) delay=1;
        float speed=( (float)dist / ((float)delay*1e-6f) );
        motionVector.setLocation(d.x*speed,d.y*speed);
        return motionVector;
    }

    /**
     * @return the direction */
    @Override public byte getDirection() {
        return direction;
    }

    /**
     * @param direction the direction to set */
    @Override public void setDirection(byte direction) {
        this.direction = direction;
    }

    /**
     * @return the hasDirection */
    @Override public boolean isHasDirection() {
        return hasDirection;
    }

    /**
     * @param hasDirection the hasDirection to set */
    @Override public void setHasDirection(boolean hasDirection) {
        this.hasDirection = hasDirection;
    }

    /**
     * Returns the magnitude of velocity in pixels/sec
     * @return the speed in pixel/second
     */
    @Override public float getSpeed() {
        return speed;
    }

    /**
     * @param speed the speed to set */
    @Override public void setSpeed(float speed) {
        this.speed = speed;
    }

    /**
     * @return the dir */
    @Override public Dir getDir() {
        return dir;
    }

    /**
     * @param dir the dir to set */
    @Override public void setDir(Dir dir) {
        this.dir = dir;
    }

    /**
     * @return the velocity */
    @Override public Point2D.Float getVelocity() {
        return velocity;
    }

    /**
     * @param velocity the velocity to set */
    @Override public void setVelocity(Point2D.Float velocity) {
        this.velocity = velocity;
    }
    
    public void setVelocity(float x, float y) {
        this.velocity.x = x;
        this.velocity.y = y;
    }

    @Override public byte getDistance() {
        return distance;
    }

    @Override public void setDistance(byte distance) {
        this.distance = distance;
    }

    @Override public int getDelay() {
        return delay;
    }

    @Override public void setDelay(int delay) {
        this.delay = delay;
    }
    
}
