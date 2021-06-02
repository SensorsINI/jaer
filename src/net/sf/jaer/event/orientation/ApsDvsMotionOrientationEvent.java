/*MotionOrientationEvent.java
 *
 * Created on May 28, 2006, 4:09 PM
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.event.orientation;

import net.sf.jaer.event.BasicEvent;
import java.awt.geom.Point2D;

/**
 * Represents an event with direction of motion and delay for DVS sensors.
 *
 * @author tobi
 */
public class ApsDvsMotionOrientationEvent extends ApsDvsOrientationEvent implements MotionOrientationEventInterface {

    /**
     * the direction of motion, a quantized value indexing into Dir
     */
    public byte direction = 0;

    /**
     * Defaults to true; set to false to indicate unknown direction.
     */
    public boolean hasDirection = true;

    /**
     * unit vector of direction of motion
     */
    public Dir dir = null;

    /**
     * the 'delay' value of this cell in us (unit of timestamps), an analog (but
     * quantized) quantity that signals the time delay associated with this
     * event. Smaller time delays signal larger speed
     */
    public int delay = 0;

    /**
     * the distance associated with this motion event. This is the distance in
     * pixels to the prior event that signaled this direction
     */
    public byte distance = 0;

    /**
     * speed in pixels per second
     */
    public float speed = 0;

    /**
     * stores computed velocity. (implementations of
     * AbstractDirectionSelectiveFilter compute it). This vector points in the
     * direction of motion and has units of pixels per second (PPS).
     */
    public Point2D.Float velocity = new Point2D.Float();

    private static final Point2D.Float motionVector = new Point2D.Float();

    /**
     * Creates a new instance of event
     */
    public ApsDvsMotionOrientationEvent() {
    }

    @Override
    public int getType() {
        return direction;
    }

    @Override
    public String toString() {
        return super.toString() + " direction=" + direction + " distance=" + distance + " delay=" + delay + " speed=" + speed;
    }

    @Override
    public int getNumCellTypes() {
        return 8;
    }

    /**
     * copies fields from source event src to this event
     *
     * @param src the event to copy from
     */
    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if (src instanceof ApsDvsMotionOrientationEvent) {
            this.direction = ((ApsDvsMotionOrientationEvent) src).direction;
            this.hasDirection = ((ApsDvsMotionOrientationEvent) src).hasDirection;
            this.dir = ((ApsDvsMotionOrientationEvent) src).dir;
            this.delay = ((ApsDvsMotionOrientationEvent) src).delay;
            this.distance = ((ApsDvsMotionOrientationEvent) src).distance;
            this.speed = ((ApsDvsMotionOrientationEvent) src).speed;
            this.velocity = ((ApsDvsMotionOrientationEvent) src).velocity;
        }
    }

    public static float computeSpeedPPS(ApsDvsMotionOrientationEvent e) {
        if (e.delay == 0) {
            return 0;
        } else {
            return 1e6f * (float) e.distance / e.delay;
        }
    }

    /**
     * computes the motionVectors for a given Event
     *
     * @param e a MotionOrientationEvent to compute MotionVectors from
     * @return The newly-computed motion vector that uses the distance of the
     * event and the delay. The speed is the distance divided by the delay and
     * is in pixels per second.
     */
    static public Point2D.Float computeMotionVector(ApsDvsMotionOrientationEvent e) {
        Dir d = unitDirs[e.direction];
        int dist = e.distance;
        int delay = e.delay;
        if (delay == 0) {
            delay = 1;
        }
        float speed = ((float) dist / ((float) delay * 1e-6f));
        motionVector.setLocation(d.x * speed, d.y * speed);
        return motionVector;
    }

    @Override
    public Dir getDir() {
        return dir;
    }

    @Override
    public byte getDirection() {
        return direction;
    }

    /** Return speed in pixels per second
     * 
     * @return PPS speed
     */
    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public Point2D.Float getVelocity() {
        return velocity;
    }

    @Override
    public boolean isHasDirection() {
        return hasDirection;
    }

    @Override
    public void setDir(Dir dir) {
        this.dir = dir;
    }

    @Override
    public void setDirection(byte direction) {
        this.direction = direction;
    }

    @Override
    public void setHasDirection(boolean hasDirection) {
        this.hasDirection = hasDirection;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    @Override
    public void setVelocity(Point2D.Float velocity) {
        this.velocity.setLocation(velocity.x, velocity.y);
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

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    public void setDelay(int delay) {
        this.delay = delay;
    }
}
