/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.geom.Point2D;

/**
 * Timestamped image-plane transform applied to events (and optionally APS
 * rendering) by {@link Steadicam}.
 * <p>
 * Signs match the IMU gyros ({@link eu.seebetter.ini.chips.davis.imu.IMUSample}):
 * they describe <em>camera</em> motion from the camera viewpoint. The warp
 * uses the same sign so a static scene stays put (camera CW roll makes the
 * image appear to roll CCW; a CW event warp derotates it). Chip coordinates
 * are x right, y up.
 *
 * @author tobi
 */
public class TransformAtTime {

    /**
     * Translation in pixels (x right, y up). Positive x is camera pan right
     * (image shifts left). Positive y is camera tilt up (image shifts down).
     */
    public Point2D.Float translationPixels;
    int timestamp;
    /**
     * Rotation in radians, clockwise from the camera viewpoint (same sign as
     * {@link eu.seebetter.ini.chips.davis.imu.IMUSample#getGyroRollZ()}).
     * Positive camera roll CW makes the image appear to roll CCW; this angle
     * is the CW warp applied to events.
     */
    public float rotationRad;
    /** {@code cos(rotationRad)} for the clockwise event warp. */
    public float cosAngle;
    /** {@code sin(rotationRad)} for the clockwise event warp. */
    public float sinAngle;

    /**
     * Constructs a new TransformAtTime.
     *
     * @param timestamp in us
     * @param translation in pixels; x right / pan-right, y up / tilt-up
     * @param rotation in radians, clockwise from the camera viewpoint
     */
    public TransformAtTime(int timestamp, Point2D.Float translation, float rotation) {
        this.translationPixels=translation;
        set(timestamp, translation.x, translation.y, rotation);
    }

   /**
     * Sets the transform.
     *
     * @param timestamp in us
     * @param translationX pixels, x right (camera pan right / image left)
     * @param translationY pixels, y up (camera tilt up / image down)
     * @param rotation in radians, clockwise from the camera viewpoint
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
