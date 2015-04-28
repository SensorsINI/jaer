/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.geom.Point2D;

/**
 * Holds timestamped transform information including translationPixels and rotationRad,
 intended for application to the event stream.
 *
 * @author tobi
 */
public class TransformAtTime {

    /** In pixels */
    public Point2D.Float translationPixels;
    int timestamp;
    /** In radians, CW from right unit vector. */
    public float rotationRad;
    public float cosAngle;
    public float sinAngle;

    /** Constructs a new TransformAtTime.
     * 
     * @param timestamp in us
     * @param translation in pixels x and y directions, increasing up and to rightwards
     * @param rotation in radians, clockwise from zero to right
     */
    public TransformAtTime(int timestamp, Point2D.Float translation, float rotation) {
        this.translationPixels=translation;
        set(timestamp, translation.x, translation.y, rotation);
    }

   /** Sets the transform
     * 
     * @param timestamp in us
     * @param translationX in pixels x and y directions, increasing up and to rightwards
     * @param translationY in pixels x and y directions, increasing up and to rightwards
     * @param rotation in radians, clockwise from zero to right
     */
    final public void set(int timestamp, float translationX, float translationY, float rotation) {
        translationPixels.x=translationX;
        translationPixels.y=translationY;
        this.timestamp = timestamp;
        this.rotationRad = rotation;
        cosAngle = (float) Math.cos(rotation);
        sinAngle = (float) Math.sin(rotation);
    }

    @Override
    public String toString() {
        return String.format("timestamp=%.1f ms translation=(%.1f,%.1f) rotation=%.1f", (float) timestamp / 1000, translationPixels.x, translationPixels.y, rotationRad);
    }
}
