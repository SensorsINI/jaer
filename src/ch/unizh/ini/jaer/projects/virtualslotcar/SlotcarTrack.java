/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Iterator;
import java.awt.geom.Point2D;

/**
 * Class for storing race tracks for slot cars. 
 * The SlotcarTrack also holds a SlotcarState that models the cars current state (e.g. pos) on this SlotcarTrack.
 * The track model has a list of track points, a PeriodicSpline that interpolates smoothly betweeen these points,
 * and a SlotcarPhysics that models the car's dynamics.  The track model can be queried for the upcoming curvature that
 * the car will see. The car's state is updated
 * 
 * @author Michael Pfeiffer
 */
public class SlotcarTrack implements java.io.Serializable {

    private static final long serialVersionUID = 8769462155491049760L; // define so that rebuilds don't cause load failure


    /** All points of the track added by the user */
    LinkedList<Point2D.Float> trackPoints;

    /** The spline object for smooth approximation */
    PeriodicSpline smoothTrack = null;

    /** Tolerance for finding nearby spline points */
    private float pointTolerance = 5.0f;

    /** State of the slot car. */
    private SlotcarState carState;

    /** Physics object */
    private SlotcarPhysics physics;

    /** Integration step for arc-length calculations */
    private float integrationStep = 0.1f;

    /** Creates a new track. */
    public SlotcarTrack() {
        trackPoints = new LinkedList<Point2D.Float>();

        smoothTrack = new PeriodicSpline();

        carState = new SlotcarState();

        physics = new SlotcarPhysics();
    }

    /** Adds a Point2D2D to the end of the track */
    public void addPoint(Point2D.Float newPoint) {
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
    public void addPoint(int i, Point2D.Float newPoint) {
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

    /** Inserts a Point after the given index */
    public int insertPoint(int idx, Point2D.Float p) {
        if ((idx < 0) || (idx > trackPoints.size())) {
            return -1;
        } else if (idx == trackPoints.size()) {
            addPoint(p);
            return trackPoints.size();
        } else {
            trackPoints.add(idx, p);
            if (trackPoints.size() >= 3)
                updateSpline();
            return trackPoints.size();
        }
    }

    /** Updates the spline coefficients for this track */
    public void updateSpline() {
        if (trackPoints.size() > 2) {
            smoothTrack.computeCoefficients(trackPoints);
        }
    }

    /** Find the closest point on the track.
     * @param pos Point in x,y Cartesian space for which to search closest track point.
     * @return Index of closest point on track or -1 if no track point is <= maxDist from pos.
     */
    public int findClosest(Point2D pos, float maxDist) {
        if(pos==null) return -1;
        if (trackPoints.size() > 0) {
            ListIterator<Point2D.Float> it = trackPoints.listIterator();
            int idx = 0;
            int closestIdx = -1;
            float closestDist = Float.MAX_VALUE;
            while (it.hasNext()) {
                Point2D p = it.next();
                float d = (float)p.distance(pos);
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
    public void setPoint(int idx, Point2D.Float newPoint){
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
    public float getTrackLength() {
        if (trackPoints.size() <= 1)
            return 0;
        else if (trackPoints.size() == 2)
            return 2f * (float)trackPoints.getFirst().distance(trackPoints.getLast());
        else
            return smoothTrack.getLength();
    }

    /** Returns the list of points */
    public LinkedList<Point2D.Float> getPointList() {
        return trackPoints;
    }

    /** Returns the iterator of the track points */
    public ListIterator<Point2D.Float> getIterator() {
        return trackPoints.listIterator();
    }

    /** Returns the list of smooth points with given step size */
    public LinkedList<Point2D.Float> getSmoothPoints(float stepSize) {
        return smoothTrack.allPoints(stepSize);
    }

    /**
     * Gets the smooth spline position at the parameter value
     * @param t Spline parameter ? what is this?
     * @return Point on 2D spline curve
     */
    public Point2D getPosition(float t) {
        return smoothTrack.getPosition(t);
    }

    /**
     * Returns the position and orientation of the spline at the given position
     * @param T Spline parameter
     * @param pos Point in which to store the position
     * @param orient Point in which to store the orientation vector
     * @return 0 if successful, -1 if not.
     */
    public int getPositionAndOrientation(float t, Point2D pos, Point2D orient) {
        return smoothTrack.getPositionAndOrientation(t,pos,orient);
    }

    /**
     * Returns the osculating circle at the given position of the track
     * @param t Spline parameter
     * @param center Point in which to store the center of the circle.
     * @return The radius of the circle
     */
    public float getOsculatingCircle(float t, Point2D center) {
        return smoothTrack.getOsculatingCircle(t,center);
    }

    /**
     * Returns the osculating circle at the given position of the track
     * @param t Spline parameter
     * @param center Point in which to store the center of the circle.
     * @param idx Spline interval in which this point lies.
     * @return The radius of the circle
     */
    public float getOsculatingCircle(float t, Point2D center, int idx) {
        return smoothTrack.getOsculatingCircle(t,idx,center);
    }


    /**
     * Returns the upcoming curvature for the next timesteps given the spline-parameter
     * position of the car. Approximates the arc-length along the track by assuming that
     * straight line distance and spline-parameter distance are equal (this holds for
     * tracks with many spline points). This method does not use advance(), but does require that the car advances
     * in the direction of increasing indices.
     * @param pos Current spline-parameter position of the car.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @param closestIdx Index of the closest track point.
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getApproxCurvature(float pos, int numPoints, float dt, float speed, int closestIdx) {
        float startPos=pos;
        float[] curvature = new float[numPoints];
        int curIdx = closestIdx;

        for (int i=0; i<numPoints; i++) {
            curvature[i] = (float) getOsculatingCircle(pos, null, curIdx);
            pos += speed*dt;
            int prevIdx=curIdx;
            curIdx = smoothTrack.newInterval(pos, curIdx); // update the index?   This increases index and wraps around to 0. Car must be driving towards increasing index.
//            if(curIdx==-1){
//                throw new RuntimeException(
//                        String.format("could not find curvature, ran out of segments: startPos=%.1f numPoints=%d dt=%f speed=%.1f closestIdx=%d; currentPos=%f curIdx=%d prevIdx=%d ; should you reverse the track diretion",
//                        startPos, numPoints,dt,speed,closestIdx,pos,curIdx,prevIdx ));
//            }
            if (pos > smoothTrack.getLength())
                pos -= smoothTrack.getLength();
        }

        UpcomingCurvature uc = new UpcomingCurvature(curvature); // TODO reuse a one-time alloated object here
        return uc;
    }
    /**
     * Returns the upcoming curvature for the next timesteps given the XY-position of the car on the screen.
     * Approximates the arc-length along the track by assuming that
     * straight line distance and spline-parameter distance are equal (this holds for
     * tracks with many spline points). This method does not use advance().
     * @param XYpos Current position of the car on the screen.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getApproxCurvature(Point2D XYpos, int numPoints, float dt, float speed) {

        int closestIdx = findClosest(XYpos, pointTolerance);
        float pos = smoothTrack.getParam(closestIdx);
        return getApproxCurvature(pos, numPoints, dt, speed, closestIdx);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the index of the closest
     * spline point. Approximates the arc-length along the track by assuming that
     * straight line distance and spline-parameter distance are equal (this holds for
     * tracks with many spline points). This method does not use advance().
     * @param closestIdx Index of the currently closest spline point.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getApproxCurvature(int closestIdx, int numPoints, float dt, float speed) {

        float pos = smoothTrack.getParam(closestIdx);
        return getApproxCurvature(pos, numPoints, dt, speed, closestIdx);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the spline-parameter
     * position of the car.
     * @param pos Current spline-parameter position of the car.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(float pos, int numPoints, float dt, float speed) {
        float[] curvature = new float[numPoints];

        for (int i=0; i<numPoints; i++) {
            curvature[i] = (float) getOsculatingCircle(pos, null);
            pos = smoothTrack.advance(pos, speed*dt, integrationStep);
            if (pos > smoothTrack.getLength())
                pos -= smoothTrack.getLength();
        }

        UpcomingCurvature uc = new UpcomingCurvature(curvature);
        return uc;
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the XY-position of the car on the screen.
     * @param XYpos Current position of the car on the screen.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(Point2D XYpos, int numPoints, float dt, float speed) {

        int closestIdx = findClosest(XYpos, pointTolerance);
        float pos = smoothTrack.getParam(closestIdx);
        return getCurvature(pos, numPoints, dt, speed);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the index of the closest
     * spline point.
     * @param closestIdx Index of the currently closest spline point.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(int closestIdx, int numPoints, float dt, float speed) {

        float pos = smoothTrack.getParam(closestIdx);
        return getCurvature(pos, numPoints, dt, speed);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the track's current SlotcarState.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(int numPoints, float dt, float speed) {
        float pos = carState.pos;
        return getCurvature(pos, numPoints, dt, speed);
    }

    /**
     * Advances the car on the track.
     * @param throttle Current throttle position
     * @param time Time to advance
     * @return New state of the car
     */
    public SlotcarState advance(float throttle, float time) {
        if (carState.onTrack) {

            // Compute curvature radius and direction
            float radius = smoothTrack.osculatingCircle(carState.pos, carState.segmentIdx, null);

            // Compute physics
            carState = physics.nextState(carState, throttle, radius, Math.signum(radius), time);

            // Advance car on track
            if (carState.onTrack) {
                carState.pos = smoothTrack.advance(carState.pos, carState.speed*time, integrationStep);
                if (carState.pos > smoothTrack.getLength()) {
                    // Wrap around at end of track
                    carState.pos -= smoothTrack.getLength();
                }
                carState.segmentIdx = smoothTrack.getInterval(carState.pos);

                // Compute absolute position and orientation of car
                smoothTrack.getPositionAndOrientation(carState.pos, carState.XYpos, carState.absoluteOrientation);
            } else {
                System.out.println("Car flew off track!!!");
                System.out.println("Critical radius was " + Math.abs(radius));
                System.out.println("Outward force was " + Math.abs(carState.outwardForce));
                System.out.println("Maximal force allowed is " + physics.getMaxOutwardForce());
            }

            return carState;
        }
        else {
            // Return old state if car off track
            return carState;
        }
    }

    /**
     * Returns the current state of the car on the track
     * @return Current state of the car on the track
     */
    public SlotcarState getCarState() {
        return carState;
    }

    /** Initializes the state of the car on the track */
    public void initCarState() {
        carState = new SlotcarState();

        // Calculate absolute positions and orientations
        carState.XYpos = new Point2D.Float();
        carState.absoluteOrientation = new Point2D.Float();
        getPositionAndOrientation(0, carState.XYpos, carState.absoluteOrientation);
    }

    public void initPhysics(float friction, float carMass, float carLength,
            float comHeight, float momentInertia,
            float orientationCorrectForce, float engineForce) {

        physics.setCarMass(carMass);
        physics.setCarLength(carLength);
        physics.setComHeight(comHeight);
        physics.setEngineForce(engineForce);
        physics.setFriction(friction);
        physics.setOrientationCorrectFactor(orientationCorrectForce);
        physics.setMomentInertia(momentInertia);
    }

    /**
     * Refines the spline by introducing new intermediate points.
     * @param step Step size
    */
    public void refine(float step) {
        smoothTrack = smoothTrack.refine(step);
        trackPoints = smoothTrack.getSplinePoints();
    }

    /**
     * Creates a new track from a list of points.
     * @param allPoints The list of track points.
     */
    public void create(LinkedList<Point2D.Float> allPoints) {
        if (allPoints != null) {
            clear();
            for (Point2D.Float p: allPoints) {
                addPoint(p);
            }
        }

        updateSpline();
    }


    /**
     * Updates the internal slotcar state by the observed XY-position of the car
     * and the speed estimated from events.
     * @param XYpos The observed position of the car
     * @param speed The estimated speed of the car
     * @param onTrack Is the car still on the track tqken out becquse closest<idx is used to set it
     * @return The current state of the car
     */
    public SlotcarState updateSlotcarState(Point2D XYpos, float speed) {
        int closestIdx = findClosest(XYpos, pointTolerance);

        carState.pos = smoothTrack.getParam(closestIdx);
        carState.segmentIdx = closestIdx;
        carState.onTrack = closestIdx!=-1;
        carState.speed = speed;
        carState.XYpos = XYpos;

        return carState;
    }

    /**
     * Reverses the direction of the track.
     */
    public void reverseTrack() {
        LinkedList<Point2D.Float> reversePoints = new LinkedList<Point2D.Float>();
        Iterator<Point2D.Float> it = trackPoints.descendingIterator();
        while (it.hasNext()) {
            reversePoints.add(it.next());
        }
        trackPoints = reversePoints;

        updateSpline();
    }

    public float getPointTolerance() {
        return pointTolerance;
    }

    public void setPointTolerance(float pointTolerance) {
        this.pointTolerance = pointTolerance;
    }

    public float getIntegrationStep() {
        return integrationStep;
    }

    public void setIntegrationStep(float integrationStep) {
        this.integrationStep = integrationStep;
    }

}
