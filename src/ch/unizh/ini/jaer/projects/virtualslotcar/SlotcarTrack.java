/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import net.sf.jaer.graphics.ImageDisplay;

/**
 * Class for storing race tracks for slot cars. The SlotcarTrack also holds a
 * SlotcarState that models the cars current state (e.g. pos) on this
 * SlotcarTrack. The track model has a list of track points, a PeriodicSpline
 * that interpolates smoothly betweeen these points, and a SlotcarPhysics that
 * models the car's dynamics. The track model can be queried for the upcoming
 * curvature that the car will see. The car's state is updated
 *
 * @author Michael Pfeiffer
 */
public class SlotcarTrack implements java.io.Serializable {

    private static Logger log = Logger.getLogger("SlotcarTrack");
    private static final long serialVersionUID = 8769462155491049760L; // define so that rebuilds don't cause load failure
    private static Preferences prefs = Preferences.userNodeForPackage(SlotcarTrack.class);
    /**
     * All points of the track added by the user
     */
    LinkedList<Point2D.Float> trackPoints = new LinkedList<Point2D.Float>();
    /**
     * Vectors pointing from each segment point to the next one. The last
     * element points back to the first.
     */
    ArrayList<Point2D.Float> segmentVectors = new ArrayList<Point2D.Float>();
    /**
     * The spline object for smooth approximation
     */
    PeriodicSpline smoothTrack = new PeriodicSpline();
    /**
     * Tolerance for finding nearby spline points
     */
    private float pointTolerance = 5.0f;
    /**
     * State of the slot car.
     */
    private SlotcarState carState = new SlotcarState();
    /**
     * Physics object
     */
    private SlotcarPhysics physics = new SlotcarPhysics();
    /**
     * Integration step for arc-length calculations
     */
    private float integrationStep = 0.1f;
    /**
     * Curvature at track points. The curvature is the radius of curvature, so
     * the straighter the track, the larger the curvature.
     */
    private float[] curvatureAtPoints = null;
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    /**
     * PropertyChangeEvent that is fired when track is changed, e.g. by loading
     * from file.
     */
    public static final String EVENT_TRACK_CHANGED = "trackChanged";
    private String trackName = null;

    /**
     * Set to display the closest point map when rendering track
     */
    public void setDisplayClosestPointMap(boolean displayClosestPointMap) {
        closestPointComputer.setDisplayClosestPointMap(displayClosestPointMap);
        if (displayClosestPointMap) {
            updateTrack();
        }
    }

    public boolean isDisplayClosestPointMap() {
        return closestPointComputer.isDisplayClosestPointMap();
    }

    transient private ClosestPointLookupTable closestPointComputer = new ClosestPointLookupTable(); // transient so we don't try to serialize it, since it must be rebuilt after deserialization in any case.
    transient JFrame closestPointFrame = null;
    transient ImageDisplay closestPointImage = null;

    /**
     * Creates a new track with trackName set to null.
     */
    public SlotcarTrack() {
    }

    /**
     * Creates a new track with specified trackName.
     */
    public SlotcarTrack(String trackName) {
        this.trackName = trackName;
    }

    private Point2D.Float vectorA2B(Point2D.Float a, Point2D.Float b) {
        if ((a == null) || (b == null)) {
            return null;
        }
        return new Point2D.Float(b.x - a.x, b.y - a.y);
    }

    @Override
    public String toString() {
        String s=String.format("%s numVertices=%d totalLengthPixels=%.1f",super.toString(),getNumPoints(),getTrackLength());
        return s; //To change body of generated methods, choose Tools | Templates.
    }

    
    
    /**
     * Adds a Point2D2D to the end of the track
     */
    public void addPoint(Point2D.Float newPoint) {
        trackPoints.addLast(newPoint);
        //        updateTrack(); // don't update for every point or else it takes forvever when extracting track
    }

    /**
     * Deletes the last Point2D of the track
     */
    public void deleteEndPoint() {
        trackPoints.removeLast();
        if (trackPoints.size() >= 3) {
            updateTrack();
        } else {
            smoothTrack = new PeriodicSpline();
        }
    }

    /**
     * Inserts a Point2D at the given index
     */
    public void addPoint(int i, Point2D.Float newPoint) {
        trackPoints.add(i, newPoint);
        updateTrack();
    }

    /**
     * Deletes a Point2D according to index
     */
    public int deletePoint(int i) {
        if ((i < 0) || (i >= trackPoints.size())) {
            return -1;
        } else {
            trackPoints.remove(i);
            if (trackPoints.size() >= 3) {
                updateTrack();
            } else {
                smoothTrack = new PeriodicSpline();
            }
            return trackPoints.size();
        }
    }

    /**
     * Inserts a Point after the given index
     */
    public int insertPoint(int idx, Point2D.Float p) {
        if ((idx < 0) || (idx > trackPoints.size())) {
            return -1;
        } else if (idx == trackPoints.size()) {
            addPoint(p);
            return trackPoints.size();
        } else {
            trackPoints.add(idx, p);
            if (trackPoints.size() >= 3) {
                updateTrack();
            }
            return trackPoints.size();
        }
    }

    /**
     * Returns an array of curvatures at spline points
     */
    public float[] getCurvatureAtPoints() {
        if (curvatureAtPoints == null) {
            updateCurvature();
        }

        return curvatureAtPoints;
    }

    /**
     * Updates the spline coefficients for this track and other statistics such
     * as the segment vectors. Also checks for and corrects duplicate successive
     * points.
     */
    public void updateTrack() {
        if (trackPoints.size() > 2) {
            removeDuplicatePoints();
            smoothTrack.computeCoefficients(trackPoints);
            updateCurvature();
            updateSegmentVectors();
            updateClosestPointComputer();
            getSupport().firePropertyChange(EVENT_TRACK_CHANGED, null, this);
        }
    }

    protected void updateSegmentVectors() {
        Point2D.Float a;
        Point2D.Float b;
        if (segmentVectors == null) {
            segmentVectors = new ArrayList<Point2D.Float>();
        }
        segmentVectors.clear();
        for (int i = 0; i < (getNumPoints() - 1); i++) {
            a = trackPoints.get(i);
            b = trackPoints.get(i + 1);
            if (a.equals(b)) {
                log.warning("tried to add a zero length segment vector from identical track points at position " + i);
            }
            segmentVectors.add(vectorA2B(a, b));
        }
        segmentVectors.add(vectorA2B(trackPoints.getLast(), trackPoints.getFirst()));
    }

    private void removeDuplicatePoints() { // TODO may not remove multiple successive identical points
        if (trackPoints.getFirst().equals(trackPoints.getLast())) {
            Object o = trackPoints.removeLast();
            log.info("first and last track point were identical, removed last point " + o);
        }
        int n = getNumPoints();
        for (int i = 0; i < (n - 1); i++) {
            Point2D.Float a = trackPoints.get(i), b = trackPoints.get(i + 1);
            if (a.equals(b)) {
                Object o = trackPoints.remove(i + 1);
                log.warning("Points " + i + " and " + (i + 1) + " were identical, removed second point " + o);
                n--;
            } else {
                segmentVectors.add(vectorA2B(a, b));
            }
        }

    }

    /**
     * Computes the curvatures at every spline point. The curvature is the
     * radius of curvature, so the straighter the track, the larger the
     * curvature.
     */
    public void updateCurvature() {
        if (trackPoints == null) {
            return;
        }
        int numPoints = trackPoints.size();
        if (numPoints > 0) {
            curvatureAtPoints = new float[numPoints];
            for (int i = 0; i < numPoints; i++) {
                float pos = smoothTrack.getParam(i);
                curvatureAtPoints[i] = getOsculatingCircle(pos, null);
            }
        } else {
            curvatureAtPoints = null;
        }

    }

    /**
     * Finds the nearest two track points to a Point2D double point. This method
     * uses the fast local minimum search to find the nearest point, then checks
     * in each direction for the next nearest point.
     *
     * @param p the Point2D in x,y space.
     * @return a Point with the two nearest track points as x (nearest) and y
     * (next nearest).
     */
    public Point findClosestTwoIndices(Point2D p) {
        Point idxP = new Point(-1, -1);

        if (trackPoints == null) {
            return idxP;
        }

        int idx1 = findClosestIndex(p, Float.MAX_VALUE, true); // true=fast search, starting where last one ended.
        if (idx1 >= 0) {
            // Find which one of the neighbors is closest
            int idx2 = idx1 - 1;
            if (idx2 < 0) {
                idx2 = getNumPoints() - 1;
            }

            int idx3 = idx1 + 1;
            if (idx3 >= getNumPoints()) {
                idx3 = 0;
            }

            Point2D p2 = getPoint(idx2);
            Point2D p3 = getPoint(idx3);

            double dist2 = p2.distance(p);
            double dist3 = p3.distance(p);

            if (dist2 < dist3) {
                idxP.setLocation(max(idx1, idx2), min(idx1, idx2));
            } else {
                idxP.setLocation(max(idx1, idx3), min(idx1, idx3));
            }
        }
        return idxP;
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    /**
     * Moves the start index to the new point index. Track parameters are
     * updated.
     *
     * @param newStartIndex
     * @throws IllegalArgumentException if newStartIndex is invalid
     */
    public void moveStartIndexToIndex(int newStartIndex) throws IllegalArgumentException {
        if ((newStartIndex < 0) || (newStartIndex >= getNumPoints())) {
            throw new IllegalArgumentException("invalid new starting index " + newStartIndex + "; only " + getNumPoints() + " in track");
        }
        LinkedList<Point2D.Float> newTrackPoints = new LinkedList<Point2D.Float>();
        int idx = newStartIndex;
        for (int i = 0; i < getNumPoints(); i++) {
            newTrackPoints.add(trackPoints.get(idx));
            idx++;
            if (idx >= getNumPoints()) {
                idx = 0;
            }
        }
        trackPoints = newTrackPoints;
        updateTrack();
    }

    /**
     * Returns the approximate normal distance of a point from the track. From
     * <a
     * href="http://www.tpub.com/math2/8.htm">http://www.tpub.com/math2/8.htm</a>
     * and <a
     * href="http://softsurfer.com/Archive/algorithm_0102/algorithm_0102.htm#Distance%20to%202-Point%20Line">http://softsurfer.com/Archive/algorithm_0102/algorithm_0102.htm#Distance%20to%202-Point%20Line</a>
     *
     * @param pos the xy position of interest.
     * @return the signed distance from the track in pixels using the nearest
     * two track points.
     */
    public float findDistanceToTrack(Point2D pos) {
        Point twoPointIndices = findClosestTwoIndices(pos);
        Point2D a = getPoint(twoPointIndices.x);
        if (a == null) {
            return Float.NaN;
        }
        Point2D b = getPoint(twoPointIndices.y);
        if (b == null) {
            return Float.NaN;
        }
        Point2D vl = new Point2D.Double(b.getX() - a.getX(), b.getY() - a.getY());  // vector pointing from a to b
        Point2D w = new Point2D.Double(pos.getX() - a.getX(), pos.getY() - a.getY()); // vector pointing from a to pos
        double vlnorm = vl.distance(0, 0); // length of vl
        double cross = ((vl.getX() * w.getY()) - (vl.getY() * w.getX()));
        double dist = cross / vlnorm;
        return (float) dist;
    }

    /**
     * Returns the approximate normal distance of a point from the track. From
     * <a
     * href="http://www.tpub.com/math2/8.htm">http://www.tpub.com/math2/8.htm</a>
     * and <a
     * href="http://softsurfer.com/Archive/algorithm_0102/algorithm_0102.htm#Distance%20to%202-Point%20Line">http://softsurfer.com/Archive/algorithm_0102/algorithm_0102.htm#Distance%20to%202-Point%20Line</a>
     *
     * @param pos the x,y position of interest.
     * @param closestIdx the closest track point, found previously.
     * @return the distance from the track in pixels using the nearest two track
     * points.
     */
    public float findDistanceToTrack(Point2D pos, int closestIdx) {

        int aIdx = -1, bIdx = -1;
        if (closestIdx >= 0) {
            // Find which one of the neighbors is closest
            int idx2 = closestIdx - 1;
            if (idx2 < 0) {
                idx2 = getNumPoints() - 1;
            }

            int idx3 = closestIdx + 1;
            if (idx3 >= getNumPoints()) {
                idx3 = 0;
            }

            Point2D p2 = getPoint(idx2);
            Point2D p3 = getPoint(idx3);

            double dist2 = p2.distance(pos);
            double dist3 = p3.distance(pos);

            if (dist2 < dist3) {
                aIdx = max(closestIdx, idx2);
                bIdx = min(closestIdx, idx2);
            } else {
                aIdx = max(closestIdx, idx3);
                bIdx = min(closestIdx, idx3);
            }
        }
        Point2D a = getPoint(aIdx);
        Point2D b = getPoint(bIdx);
        Point2D vl = new Point2D.Double(b.getX() - a.getX(), b.getY() - a.getY());  // vector pointing from a to b
        Point2D w = new Point2D.Double(pos.getX() - a.getX(), pos.getY() - a.getY()); // vector pointing from a to pos
        double vlnorm = vl.distance(0, 0); // length of vl
        double cross = Math.abs((vl.getX() * w.getY()) - (vl.getY() * w.getX()));
        double dist = cross / vlnorm;
        return (float) dist;
    }
    private int lastFindIdx = 0;  // cache last starting point to make search cheaper
    private int lastNumberIterantions = 0;  // statistics on search

    private void updateClosestPointComputer() {
        if (closestPointComputer == null) {
            closestPointComputer = new ClosestPointLookupTable();
        } else {
            closestPointComputer.init();
        }
    }

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        if (support == null) {
            support = new PropertyChangeSupport(this);
        }
        return support;
    }

    void saveTrackToFile(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        oos.close();
        fos.close();
    }

    /**
     * Computes nearest track point using lookup from table
     */
    class ClosestPointLookupTable {

        private int size = 64;
        private int[] map = new int[size * size];
        int sx, sy;
        float xPixPerUnit, yPixPerUnit = 1;
        Rectangle2D.Float bounds = new Rectangle2D.Float();
        private boolean displayClosestPointMap = false;

        public ClosestPointLookupTable() {
            init();
        }

        final void init() {
            computeBounds();
            xPixPerUnit = (float) bounds.getWidth() / size;
            yPixPerUnit = (float) bounds.getHeight() / size;
            Arrays.fill(map, -1);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    // for each grid entry, locate point at center of grid point, then find nearest track vertex and put in map
                    Point2D.Float pos = new Point2D.Float((x * xPixPerUnit) + (xPixPerUnit / 2) + (float) bounds.getX(), (y * yPixPerUnit) + (yPixPerUnit / 2) + (float) bounds.getY());
                    int idx = findClosestIndex(pos, getPointTolerance(), false);
                    setMapEntry(idx, x, y);
                }
            }
            if (displayClosestPointMap) {
                if (closestPointFrame == null) {
                    closestPointFrame = new JFrame("Closest Point Map");
                    closestPointFrame.setPreferredSize(new Dimension(200, 200));

                    closestPointImage = ImageDisplay.createOpenGLCanvas();
                    closestPointImage.setFontSize(10);
                    closestPointImage.setImageSize(size, size);
                    closestPointImage.setxLabel("x");
                    closestPointImage.setyLabel("y");

                    closestPointImage.addGLEventListener(new GLEventListener() {

                        @Override
                        public void init(GLAutoDrawable drawable) {
                        }

                        @Override
                        public void display(GLAutoDrawable drawable) {
                            closestPointImage.checkPixmapAllocation();
                            for (int x = 0; x < size; x++) {
                                for (int y = 0; y < size; y++) {
                                    int point = getMapEntry(x, y);
                                    if (point == -1) {
                                        closestPointImage.setPixmapGray(x, y, 0);
                                    } else {
                                        Color c = Color.getHSBColor((float) point / getNumPoints(), .5f, .5f);
                                        float[] rgb = c.getColorComponents(null);
                                        closestPointImage.setPixmapRGB(x, y, rgb);
                                        closestPointImage.drawCenteredString(x, y, Integer.toString(point));
                                    }
                                }
                            }
                            //                            GL2 gl=drawable.getGL().getGL2();
                            //                            gl.glPushMatrix();
                            //                            gl.glLineWidth(.5f);
                            //                            gl.glColor3f(0,0,1);
                            //                            gl.glBegin(GL2.GL_LINE_LOOP);
                            //                            for(Point2D.Float p:trackPoints){
                            //                                gl.glVertex2f(p.x, p.y);
                            //                            }
                            //                            gl.glEnd();
                            //                            gl.glBegin
                            //                            gl.glPopMatrix();  // TODO needs coordinate transform to ImageDisplay pixels to draw track
                        }

                        @Override
                        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                        }

                        @Override
                        public void dispose(GLAutoDrawable arg0) {
                            // TODO Auto-generated method stub

                        }
                    });

                    closestPointFrame.getContentPane().add(closestPointImage, BorderLayout.CENTER);
                    closestPointFrame.setVisible(true);

                }
                closestPointImage.repaint();
            }
        }

        int getMapEntry(int x, int y) {
            final int idx = x + (size * y);
            if ((idx < 0) || (idx >= map.length)) {
                return -1;
            }
            return map[idx];
        }

        void setMapEntry(int val, int x, int y) {
            final int idx = x + (size * y);
            if ((idx < 0) || (idx > map.length)) {
                return;
            }
            map[x + (size * y)] = val;
        }

        int findPoint(Point2D.Float pos) {
            int x = (int) ((size * (pos.x - bounds.getX() /*- xPixPerUnit / 2*/)) / bounds.getWidth());
            int y = (int) ((size * (pos.y - bounds.getY() /*- yPixPerUnit / 2*/)) / bounds.getHeight());
            return getMapEntry(x, y);
        }

        int findPoint(double x, double y) {
            int xx = (int) ((size * (x - bounds.getX() /*- xPixPerUnit / 2*/)) / bounds.getWidth());
            int yy = (int) ((size * (y - bounds.getY() /*- yPixPerUnit / 2*/)) / bounds.getHeight());
            return getMapEntry(xx, yy);
        }

        private void computeBounds() {
            final float extraFraction = .25f;
            if (trackPoints == null) {
                return;
            }
            float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = Float.MIN_VALUE, maxy = Float.MIN_VALUE;
            for (Point2D.Float p : trackPoints) {
                if (p.x < minx) {
                    minx = p.x;
                }
                if (p.y < miny) {
                    miny = p.y;
                }
                if (p.x > maxx) {
                    maxx = p.x;
                }
                if (p.y > maxy) {
                    maxy = p.y;
                }
            }
            final float w = maxx - minx, h = maxy - miny;
            bounds.setRect(minx - (w * extraFraction), miny - (h * extraFraction), w * (1 + (2 * extraFraction)), h * (1 + (2 * extraFraction)));
        }

        /**
         * @return the displayClosestPointMap
         */
        public boolean isDisplayClosestPointMap() {
            return displayClosestPointMap;
        }

        /**
         * @param displayClosestPointMap the displayClosestPointMap to set
         */
        public void setDisplayClosestPointMap(boolean displayClosestPointMap) {
            this.displayClosestPointMap = displayClosestPointMap;
        }
    }

    /**
     * Find the closest point on the track with option for local minimum
     * distance or global minimum distance search.
     *
     * @param pos Point in x,y Cartesian space for which to search closest track
     * point.
     * @param maxDist the maximum allowed distance.
     * @param fastLocalSearchEnabled if true, the search uses the
     * ClosestPointLookupTable lookup and maxDist is ignored.
     * @return Index of closest point on track or -1 if no track point is <=
     * maxDist from pos or is not found in the ClosestPointLookupTable.
     */
    public int findClosestIndex(Point2D pos, float maxDist, boolean fastLocalSearchEnabled) {
        if (pos == null) {
            return -1;
        }
        int n = getNumPoints();
        if (n == 0) {
            return -1;
        }

        if (fastLocalSearchEnabled) {
            lastFindIdx = closestPointComputer.findPoint(pos.getX(), pos.getY());
            return lastFindIdx;
        } else {
            int idx = 0, closestIdx = -1;
            float closestDist = Float.MAX_VALUE;
            for (Point2D.Float p : trackPoints) {
                float d = (float) p.distance(pos);
                if (d <= maxDist) {
                    if (d < closestDist) {
                        closestDist = d;
                        closestIdx = idx;
                    }
                }
                idx++;
            }
            return closestIdx;
        }
    }

    /**
     * Returns the point with the given index
     */
    public Point2D getPoint(int idx) {
        if ((idx >= 0) && (idx < trackPoints.size())) {
            return trackPoints.get(idx);
        } else {
            return null;
        }
    }

    /**
     * Changes the point with the given index to a new value
     */
    public void setPoint(int idx, Point2D.Float newPoint) {
        if ((idx >= 0) && (idx < trackPoints.size())) {
            trackPoints.set(idx, newPoint);
            updateTrack();
        }
    }

    /**
     * Clears the whole track
     */
    public void clear() {
        trackPoints.clear();
        smoothTrack = new PeriodicSpline();
    }

    /**
     * Number of points on the track
     */
    public int getNumPoints() {
        return trackPoints.size();
    }

    /**
     * Return length of the track
     */
    public float getTrackLength() {
        if (trackPoints.size() <= 1) {
            return 0;
        } else if (trackPoints.size() == 2) {
            return 2f * (float) trackPoints.getFirst().distance(trackPoints.getLast());
        } else {
            return smoothTrack.getLength();
        }
    }

    /**
     * Returns the list of points
     */
    final public LinkedList<Point2D.Float> getPointList() {
        return trackPoints;
    }

    /**
     * Returns the iterator of the track points
     */
    public ListIterator<Point2D.Float> getIterator() {
        return trackPoints.listIterator();
    }

    /**
     * Returns the list of smooth points with given step size
     */
    public LinkedList<Point2D.Float> getSmoothPoints(float stepSize) {
        return smoothTrack.allPoints(stepSize);
    }

    /**
     * Gets the smooth spline position at the parameter value
     *
     * @param t Spline parameter ? what is this?
     * @return Point on 2D spline curve
     */
    public Point2D getPosition(float t) {
        return smoothTrack.getPosition(t);
    }

    /**
     * Returns the position and orientation of the spline at the given position
     *
     * @param t Spline parameter
     * @param pos Point in which to store the position
     * @param orient Point in which to store the orientation vector
     * @return 0 if successful, -1 if not.
     */
    public int getPositionAndOrientation(float t, Point2D pos, Point2D orient) {
        return smoothTrack.getPositionAndOrientation(t, pos, orient);
    }

    /**
     * Returns the osculating circle at the given position of the track
     *
     * @param t Spline parameter
     * @param center Point in which to store the center of the circle.
     * @return The radius of the circle
     */
    public float getOsculatingCircle(float t, Point2D center) {
        return smoothTrack.getOsculatingCircle(t, center);
    }

    /**
     * Returns the osculating circle at the given position of the track
     *
     * @param t Spline parameter
     * @param center Point in which to store the center of the circle.
     * @param idx Spline interval in which this point lies.
     * @return The radius of the circle
     */
    public float getOsculatingCircle(float t, Point2D center, int idx) {
        return smoothTrack.getOsculatingCircle(t, idx, center);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the
     * spline-parameter position of the car. Approximates the arc-length along
     * the track by assuming that straight line distance and spline-parameter
     * distance are equal (this holds for tracks with many spline points). This
     * method does not use advance(), but does require that the car advances in
     * the direction of increasing indices.
     *
     * @param pos Current spline-parameter position of the car.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @param closestIdx Index of the closest track point.
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getApproxCurvature(float pos, int numPoints, float dt, float speed, int closestIdx) {
        float startPos = pos;
        float[] curvature = new float[numPoints];
        int curIdx = closestIdx;

        for (int i = 0; i < numPoints; i++) {
            curvature[i] = getOsculatingCircle(pos, null, curIdx);
            pos += speed * dt;
            int prevIdx = curIdx;
            curIdx = smoothTrack.newInterval(pos, curIdx); // update the index?   This increases index and wraps around to 0. Car must be driving towards increasing index.
            //            if(curIdx==-1){
            //                throw new RuntimeException(
            //                        String.format("could not find curvature, ran out of segments: startPos=%.1f numPoints=%d dt=%f speed=%.1f closestIdx=%d; currentPos=%f curIdx=%d prevIdx=%d ; should you reverse the track diretion",
            //                        startPos, numPoints,dt,speed,closestIdx,pos,curIdx,prevIdx ));
            //            }
            if (pos > smoothTrack.getLength()) {
                pos -= smoothTrack.getLength();
            }
        }

        UpcomingCurvature uc = new UpcomingCurvature(curvature); // TODO reuse a one-time alloated object here
        return uc;
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the
     * XY-position of the car on the screen. Approximates the arc-length along
     * the track by assuming that straight line distance and spline-parameter
     * distance are equal (this holds for tracks with many spline points). This
     * method does not use advance().
     *
     * @param XYpos Current position of the car on the screen.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getApproxCurvature(Point2D XYpos, int numPoints, float dt, float speed) {

        int closestIdx = findClosestIndex(XYpos, pointTolerance, true);
        float pos = smoothTrack.getParam(closestIdx);
        return getApproxCurvature(pos, numPoints, dt, speed, closestIdx);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the index of
     * the closest spline point. Approximates the arc-length along the track by
     * assuming that straight line distance and spline-parameter distance are
     * equal (this holds for tracks with many spline points). This method does
     * not use advance().
     *
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
     * Returns the upcoming curvature for the next timesteps given the
     * spline-parameter position of the car.
     *
     * @param pos Current spline-parameter position of the car.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(float pos, int numPoints, float dt, float speed) {
        float[] curvature = new float[numPoints];

        for (int i = 0; i < numPoints; i++) {
            curvature[i] = getOsculatingCircle(pos, null);
            pos = smoothTrack.advance(pos, speed * dt, integrationStep);
            if (pos > smoothTrack.getLength()) {
                pos -= smoothTrack.getLength();
            }
        }

        UpcomingCurvature uc = new UpcomingCurvature(curvature);
        return uc;
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the
     * XY-position of the car on the screen.
     *
     * @param XYpos Current position of the car on the screen.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(Point2D XYpos, int numPoints, float dt, float speed) {

        int closestIdx = findClosestIndex(XYpos, pointTolerance, false);
        float pos = smoothTrack.getParam(closestIdx);
        return getCurvature(pos, numPoints, dt, speed);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the index of
     * the closest spline point.
     *
     * @param closestIdx Index of the currently closest spline point.
     * @param numPoints Number of curvature points to look ahead
     * @param dt Time interval between steps
     * @param speed Current speed of the car
     * @return Curvatures of the time points ahead.
     */
    public UpcomingCurvature getCurvature(int closestIdx, int numPoints, float dt, float speed) {

        float pos = smoothTrack.getParam(closestIdx);
        //            System.out.println("S: " + speed + "|| DT: " + dt);
        //            System.out.println("DS: " + speed*dt + " // " + getTrackLength());
        return getCurvature(pos, numPoints, dt, speed);
    }

    /**
     * Returns the upcoming curvature for the next timesteps given the track's
     * current SlotcarState.
     *
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
     *
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
                carState.pos = smoothTrack.advance(carState.pos, carState.speed * time, integrationStep);
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
        } else {
            // Return old state if car off track
            return carState;
        }
    }

    /**
     * Returns the current state of the car on the track
     *
     * @return Current state of the car on the track
     */
    public SlotcarState getCarState() {
        return carState;
    }

    /**
     * Initializes the state of the car on the track
     */
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
     *
     * @param step Step size
     */
    public void refine(float step) {
        smoothTrack = smoothTrack.refine(step);
        trackPoints = smoothTrack.getSplinePoints();
        updateCurvature();
    }

    /**
     * Creates a new track from a list of points.
     *
     * @param allPoints The list of track points.
     */
    public void create(LinkedList<Point2D.Float> allPoints) {
        if (allPoints != null) {
            clear();
            for (Point2D.Float p : allPoints) {
                addPoint(p);
            }
        }

        updateTrack();
    }

    /**
     * Updates the internal slotcar state by the observed XY-position of the car
     * and the speed estimated from events.
     *
     * @param XYpos The observed position of the car
     * @param speed The estimated speed of the car
     * @return The current state of the car
     */
    public SlotcarState updateSlotcarState(Point2D XYpos, float speed) {
        int closestIdx = findClosestIndex(XYpos, pointTolerance, true);

        carState.pos = smoothTrack.getParam(closestIdx);
        int lastIdx = carState.segmentIdx;
        boolean lastOnTrack = carState.onTrack;
        carState.segmentIdx = closestIdx;
        carState.onTrack = closestIdx != -1;
        //        if (carState.onTrack != lastOnTrack) {
        //            System.out.println("carState.onTrack changed to " + carState.onTrack + " when changing from segment " + lastIdx + " to segment " + carState.segmentIdx);
        //        }
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

        updateTrack();
    }

    /**
     * Tolerance for finding nearby spline points
     */
    public float getPointTolerance() {
        return pointTolerance;
    }

    /**
     * Tolerance for finding nearby spline points
     */
    public void setPointTolerance(float pointTolerance) {
        float old = this.pointTolerance;
        this.pointTolerance = pointTolerance;
        if (this.pointTolerance != old) {
            updateClosestPointComputer();
            getSupport().firePropertyChange(EVENT_TRACK_CHANGED, null, this);
        }
    }

    public float getIntegrationStep() {
        return integrationStep;
    }

    public void setIntegrationStep(float integrationStep) {
        this.integrationStep = integrationStep;
    }

    /**
     * @return the trackName
     */
    public String getTrackName() {
        return trackName;
    }

    /**
     * @param trackName the trackName to set
     */
    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }


    /**
     * Displays the extracted track points
     */
    public void draw(GLAutoDrawable drawable) {
        if (trackPoints == null) {
            return;
        }
        // Draw extracted points

        float[] curvatureAtPoints;
        GL2 gl = drawable.getGL().getGL2();
        gl.glLineWidth(1f);
        int numPoints = trackPoints.size();
        gl.glColor3d(1.0f, 1.0f, 1.0f);
        Point2D startPoint = null, selectedPoint = null;
        float startSize = 10.0f, selectedSize = 10.0f;
        float minSize = 3.0f;
        int idx = 0;
        for (Point2D p : trackPoints) {

            gl.glPointSize(minSize);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2d(p.getX(), p.getY());
            gl.glEnd();
            idx++;
        }

        // Plot lines
        gl.glPointSize(1.0f);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (Point2D p : trackPoints) {
            gl.glVertex2d(p.getX(), p.getY());
        }
        gl.glEnd();
    }

    static private void putLastFilePrefs(File file) {
        if (file == null) {
            return;
        }
        prefs.put("SlotcarTrack.lastFile", file.toString());
    }

    static private File getLastFilePrefs() {
        return new File(prefs.get("SlotcarTrack.lastFile", System.getProperty("user.dir")));
    }

    /**
     * Saves the extracted track to an external file.
     */
    static public SlotcarTrack doLoadTrack() {

        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(getLastFilePrefs());  // defaults to startup runtime folder
        fc.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory()
                        || f.getName().toLowerCase().endsWith(".track");
            }

            @Override
            public String getDescription() {
                return "Track files";
            }
        });

        final int[] state = new int[1];
        state[0] = Integer.MIN_VALUE;

        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setSelectedFile(new File("test.track"));
        state[0] = fc.showOpenDialog(null);
        if (state[0] == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            putLastFilePrefs(file);
            try {
                return loadFromFile(file);
            } catch (Exception e) {
                log.warning(e.toString());
                JOptionPane.showMessageDialog(fc, "Couldn't load track from file " + file + ", caught exception " + e, "Track file warning", JOptionPane.WARNING_MESSAGE);
                return null;
            }
        } else {
            log.info("Cancelled saving!");
            return null;
        }
    }

    static public SlotcarTrack loadFromFile(File file) throws HeadlessException, IOException, ClassNotFoundException {
        if (file == null) {
            throw new IOException("null filename, can't load track from file - track needs to be saved first");
        }
        log.info("loading track data from " + file);
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        SlotcarTrack track = (SlotcarTrack) ois.readObject();
        ois.close();
        fis.close();
        track.updateTrack(); // update other internal vars of track
        track.setTrackName(file.getPath());
        return track;
    }

}
