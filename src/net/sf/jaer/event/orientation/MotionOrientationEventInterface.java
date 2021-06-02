/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.event.orientation;

import java.awt.geom.Point2D;
import net.sf.jaer.event.BasicEvent;

/**
 * Interface for motion vector events.
 * @author tobi
 */
public interface MotionOrientationEventInterface extends OrientationEventInterface {

   /** represents a direction. The x and y fields represent relative 
     * offsets in the x and y directions by these amounts. */
    public static final class Dir{
        public int x, y;
        Dir(int x, int y){
            this.x=x;
            this.y=y;
        }
    }

    /** An array of 8 nearest-neighbor unitDirs going CCW from down (south) direction.
     * <p>
     * these unitDirs are indexed by inputType, then by (inputType+4)%8 (opposite direction)
     * when input type is orientation, then input type 0 is 0 deg horiz edge, so first index could be to down, second to up
     * so list should start with down
     * IMPORTANT, this order *depends* on DirectionSelectiveFilter order of orientations */
    public static final Dir[] unitDirs={
        new Dir( 0,-1), // down
        new Dir( 1,-1), // down right
        new Dir( 1, 0), // right
        new Dir( 1, 1), // up right
        new Dir( 0, 1), // up
        new Dir(-1, 1), // up left
        new Dir(-1, 0), // left
        new Dir(-1,-1), // down left
    };
    
    /**
     * Returns the direction object.
     * 
     * @return the dir
     */
    Dir getDir();

    /**
     * Returns the direction as a byte value.
     * @return the direction
     */
    byte getDirection();

    /**
     * Returns the magnitude of velocity, in pixels/sec
     * @return the speed in pixels/sec
     */
    float getSpeed();

    /**
     * @return the velocity
     */
    Point2D.Float getVelocity();

    /**
     * @return the hasDirection
     */
    boolean isHasDirection();

    /**
     * Sets the direction object.
     * @param dir the dir to set
     */
    void setDir(Dir dir);
    
    byte getDistance();
    void setDistance(byte distance);

    int getDelay();
    void setDelay(int delay);
    /**
     * Sets the byte value of the direction.
     * 
     * @param direction the direction to set
     */
    void setDirection(byte direction);

    /**
     * @param hasDirection the hasDirection to set
     */
    void setHasDirection(boolean hasDirection);

    /**
     * @param speed the speed to set
     */
    void setSpeed(float speed);

    /**
     * Sets the velocity vector value. Not all velocities may be possible given the finite direction angles. 
     * @param velocity the velocity to set
     */
    void setVelocity(Point2D.Float velocity);
    void setVelocity(float x, float y);
    
    @Override public void copyFrom(BasicEvent src);
}
