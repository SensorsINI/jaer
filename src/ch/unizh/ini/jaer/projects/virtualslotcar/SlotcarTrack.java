/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.LinkedList;
import java.util.ListIterator;
import java.awt.geom.Point2D;

/**
 * Class for storing race tracks for slot cars
 * @author Michael Pfeiffer
 */
public class SlotcarTrack {

    /** All points of the track added by the user */
    LinkedList<Point2D> trackPoints;

    /** The spline object for smooth approximation */
    PeriodicSpline smoothTrack = null;

    /** Integration step for arc-length calculations */
    public final double INTEGRATION_STEP = 0.001;

    /** Creates a new track */
    public SlotcarTrack() {
        trackPoints = new LinkedList<Point2D>();

        smoothTrack = new PeriodicSpline();
    }

    /** Adds a Point2D2D to the end of the track */
    public void addPoint(Point2D newPoint) {
        trackPoints.addLast(newPoint);
        updateSpline();
    }

    /** Deletes the last Point2D of the track */
    public void deleteEndPoint() {
        trackPoints.removeLast();
        if (trackPoints.size() >= 3)
            updateSpline();
        else
            smoothTrack = new PeriodicSpline();
    }

    /** Inserts a Point2D at the given index */
    public void addPoint(int i, Point2D newPoint) {
        trackPoints.add(i, newPoint);
        updateSpline();
    }

    /** Deletes a Point2D according to index */
    public int deletePoint(int i) {
        if ((i < 0) || (i >= trackPoints.size())) {
            return -1;
        }
        else {
            trackPoints.remove(i);
            if (trackPoints.size() >= 3)
                updateSpline();
            else
                smoothTrack = new PeriodicSpline();
            return trackPoints.size();
        }
    }

    /** Updates the spline coefficients for this track */
    public void updateSpline() {
        if (trackPoints.size() > 2) {
            smoothTrack.computeCoefficients(trackPoints);
        }
    }

    /** Find the closest point on the track
     * @param pos Point for which to search closest track point
     * @return Index of closest point on track
     */
    public int findClosest(Point2D pos, double maxDist) {
        if (trackPoints.size() > 0) {
            ListIterator<Point2D> it = trackPoints.listIterator();
            int idx = 0;
            int closestIdx = -1;
            double closestDist = Double.MAX_VALUE;
            while (it.hasNext()) {
                Point2D p = it.next();
                double d = p.distance(pos);
                if ((d < closestDist) && (d <= maxDist)) {
                    closestIdx = idx;
                    closestDist = d;
                }
                idx++;
            }
            return closestIdx;
        }
        else
            return -1;
    }

    /** Returns the point with the given index */
    public Point2D getPoint(int idx) {
        if ((idx >= 0) && (idx < trackPoints.size()))
            return trackPoints.get(idx);
        else
            return null;
    }

    /** Changes the point with the given index to a new value */
    public void setPoint(int idx, Point2D newPoint){
        if ((idx >= 0) && (idx < trackPoints.size())) {
            trackPoints.set(idx, newPoint);
            updateSpline();
        }
    }

    /** Clears the whole track */
    public void clear() {
        trackPoints.clear();
        smoothTrack = new PeriodicSpline();
    }

    /** Number of points on the track */
    public int getNumPoints() {
        return trackPoints.size();
    }

    /** Return length of the track */
    public double getTrackLength() {
        if (trackPoints.size() <= 1)
            return 0;
        else if (trackPoints.size() == 2)
            return 2.0 * trackPoints.getFirst().distance(trackPoints.getLast());
        else
            return smoothTrack.getLength();
    }

    /** Returns the list of points */
    public LinkedList<Point2D> getPointList() {
        return trackPoints;
    }

    /** Returns the iterator of the track points */
    public ListIterator<Point2D> getIterator() {
        return trackPoints.listIterator();
    }

    /** Returns the list of smooth points with given step size */
    public LinkedList<Point2D> getSmoothPoints(double stepSize) {
        return smoothTrack.allPoints(stepSize);
    }

    /**
     * Gets the smooth spline position at the parameter value
     * @param t Spline parameter
     * @return Point on 2D spline curve
     */
    public Point2D getPosition(double t) {
        return smoothTrack.getPosition(t);
    }

    /**
     * Returns the position and orientation of the spline at the given position
     * @param T Spline parameter
     * @param pos Point in which to store the position
     * @parma orient Point in which to store the orientation vector
     * @return 0 if successful, -1 if not.
     */
    public int getPositionAndOrientation(double t, Point2D pos, Point2D orient) {
        return smoothTrack.getPositionAndOrientation(t,pos,orient);
    }

    /**
     * Advances the car on the track.
     * @param t Current parameter position
     * @param speed Current speed of car
     * @param time Time to advance
     * @return New parameter position (NaN if car leaves track)
     */
    public double advance(double t, double speed, double time) {
        return smoothTrack.advance(t, speed*time, INTEGRATION_STEP);
    }
}
