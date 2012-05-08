/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2012.vor;

import java.awt.geom.Point2D;

/**
 * Holds timestamped transform information including translation and rotation,
 * intended for application to the event stream.
 *
 * @author tobi
 */
class TransformAtTime {

    /** Inn pixel units */
    public Point2D.Float translation;
    int timestamp;
    /** In radians, CW from right unit vector. */
    public float rotation;
    public float cosAngle;
    public float sinAngle;

    /** Constructs a new TransformAtTime.
     * 
     * @param timestamp in us
     * @param translation in pixels x and y directions, increasing up and to rightwards
     * @param rotation in radians, clockwise from zero to right
     */
    public TransformAtTime(int timestamp, Point2D.Float translation, float rotation) {
        this.translation=translation;
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
        translation.x=translationX;
        translation.y=translationY;
        this.timestamp = timestamp;
        this.rotation = rotation;
        cosAngle = (float) Math.cos(rotation);
        sinAngle = (float) Math.sin(rotation);
    }

    @Override
    public String toString() {
        return String.format("timestamp=%.1f ms translation=(%.1f,%.1f) rotation=%.1f", (float) timestamp / 1000, translation.x, translation.y, rotation);
    }
}
