/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.poseestimation;

import java.awt.geom.Point2D;

/**
 * Object indicating transformation of rendering screen at a given timestamp
 * 
 * @author tobi
 */
public class Transformation {
    
    // In pixel units
    public Point2D.Float translation;
    int timestamp;
    // In radians
    public float rotation;
    // Helper calculations
    public float cosAngle;
    public float sinAngle;

    /** 
     * Constructor
     * 
     * @param timestamp in us
     * @param translation in pixels x and y directions, increasing up and to rightwards
     * @param rotation in radians, clockwise from zero to right
     */
    public Transformation(int timestamp, Point2D.Float translation, float rotation) {
        set(timestamp, translation.x, translation.y, rotation);
    }

   /** 
    * Sets the transform
    * 
    * @param timestamp in us
    * @param translationX in pixels x and y directions, increasing up and to rightwards
    * @param translationY in pixels x and y directions, increasing up and to rightwards
    * @param rotation in radians, clockwise from zero to right
    */
    final public void set(int timestamp, float translationX, float translationY, float rotation) {
        translation.x=translationX;
        translation.y=translationY;
        this.timestamp = timestamp;
        this.rotation = rotation;
        cosAngle = (float) Math.cos(rotation);
        sinAngle = (float) Math.sin(rotation);
    }

    @Override
    public String toString() {
        return String.format("Timestamp=%.1f ms translation=(%.1f,%.1f) rotation=%.1f", (float) timestamp / 1000, translation.x, translation.y, rotation);
    }
}
