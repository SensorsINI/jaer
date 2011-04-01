/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Float;

/**
 *
 * @author junhaeng2.lee
 */
public class Gesture {
    /**
     * gesture name
     */
    private String name;

    /**
     * start time of the gesture in us
     */
    private int startTimeUs;

    /**
     * end time of the gesture in us
     */
    private int endTimeUs;

    /**
     * endTimeUs - startTimeUs
     */
    private int durationUs;

    /**
     * length in pixels
     */
    private float length;

    /**
     * mean angle; useful for left, right, up down, slashUp, slashDown
     */
    private float meanAngle;

    /**
     * starting point
     */
    private Point2D.Float startPoint = new Point2D.Float();

    /**
     * end point
     */
    private Point2D.Float endPoint = new Point2D.Float();

    /**
     * constructor
     * @param name
     * @param startTimeUs
     * @param endTimeUs
     */
    Gesture(String name, int startTimeUs, int endTimeUs, float length, float meanAngle, Point2D.Float startPoint, Point2D.Float endPoint){
        this.name = name;
        this.startTimeUs = startTimeUs;
        this.endTimeUs = endTimeUs;
        this.durationUs = endTimeUs - startTimeUs;
        this.length = length;
        this.meanAngle = meanAngle;
        this.startPoint.setLocation(startPoint);
        this.endPoint.setLocation(endPoint);
    }

    /**
     * returns gesture's name
     * @return
     */
    public String getGestureName(){
        return name;
    }

    /**
     * returns the start time of the gesture in us
     * @return
     */
    public int getStartTimeUs(){
        return startTimeUs;
    }

    /**
     * returns the end time of the gesture in us
     * @return
     */
    public int getEndTimeUs() {
        return endTimeUs;
    }

    /**
     * returns duration of the gesture in us (endTimeUs - startTimeUs)
     * @return
     */
    public int getDurationUs() {
        return durationUs;
    }

    /**
     * returns the end point of the gesture
     * @return
     */
    public Float getEndPoint() {
        return endPoint;
    }

    /**
     * returns the length of the gesture in pixels
     * @return
     */
    public float getLength() {
        return length;
    }

    /**
     * returns the mean angle of the gesture
     * @return
     */
    public float getMeanAngle() {
        return meanAngle;
    }

    /**
     * returns the starting point of the gesture
     * @return
     */
    public Float getStartPoint() {
        return startPoint;
    }

}
