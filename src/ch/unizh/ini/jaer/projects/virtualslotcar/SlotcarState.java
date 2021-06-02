/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.geom.Point2D;

/**
 * A class for storing the state of the car
 * @author Michael Pfeiffer
 */
public class SlotcarState implements java.io.Serializable {

    /** Position of the car on the curve. */
    public float pos;

    /** Position of the car in xy-space. */
    public Point2D XYpos;

    /** Absolute orientation vector in xy-space. */
    public Point2D absoluteOrientation;

    /** Track segment in which the car is. */
    public int segmentIdx;

    /** Orientation of the car relative to the track. */
    public float relativeOrientation;

    /** Angular rotation velocity of the car */
    public float angularVelocity;

    /** Speed of the car in track units per second. */
    public float speed;

    /** Outward force of the car in track units per second squared. */
    public float outwardForce;

    /** Still on track or has flown off? */
    public boolean onTrack;

    public String toString(){
        return String.format("SlotcarState: pos= %f XYPos= %f %f absoluteOrientation= %f %f segmentIdx= %d relativeOrientation= %f angularVelocity= %f speed= %f outwardForce= %f onTrack= %s",
                pos,
                XYpos==null? Float.NaN: XYpos.getX(), XYpos==null? Float.NaN:XYpos.getY(),
                absoluteOrientation==null? Float.NaN: absoluteOrientation.getX(), absoluteOrientation==null? Float.NaN: absoluteOrientation.getY(),
                segmentIdx,
                relativeOrientation,
                angularVelocity,
                speed,
                outwardForce,
                onTrack);
    }

    /**
     * Copy constructor for the state object
     * @param oldState Previous state to copy
     */
    public SlotcarState(SlotcarState oldState) {
        onTrack = oldState.onTrack;
        outwardForce = oldState.outwardForce;
        speed = oldState.speed;
        angularVelocity = oldState.angularVelocity;
        relativeOrientation = oldState.relativeOrientation;
        segmentIdx = oldState.segmentIdx;
        if (oldState.absoluteOrientation == null)
            absoluteOrientation = null;
        else {
            absoluteOrientation = new Point2D.Double(oldState.absoluteOrientation.getX(),
                oldState.absoluteOrientation.getY());
        }
        if (oldState.XYpos == null) {
            XYpos = null;
        }
        else {
            XYpos = new Point2D.Double(oldState.XYpos.getX(), oldState.XYpos.getY());
        }
        pos = oldState.pos;
    }

    /** Default constructor for state objects */
    public SlotcarState() {
        onTrack = true;
        outwardForce = 0;
        speed = 0;
        angularVelocity = 0;
        relativeOrientation = 0;
        segmentIdx = 0;
        absoluteOrientation = null;
        XYpos = null;
        pos = 0;
    }
}

