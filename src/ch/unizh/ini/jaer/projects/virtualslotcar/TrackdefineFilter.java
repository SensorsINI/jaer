/*
 * TrackdefineFilter.java
 *
 * Created on July 1, 2010, 01:05 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import javax.media.opengl.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import javax.media.opengl.glu.GLU;
import javax.swing.SwingUtilities;
// import java.lang.reflect.InvocationTargetException;

/**
 * An AE filter that first creates a histogram of incoming pixels, and then lets the user define
 * the detected track.

 * * <p>
 * I have implemented two new buttons for the TrackDefineFilter, which allow you to refine the currently extracted
track. So first you would use the filter as before, move your spline points around, delete and maybe insert points,
and then you click on refineTrack. As a result new spline points will be inserted along the smooth track curve with
a distance "refineDistance" that you can set in the filter. This allows us to see the track more or less as a
sequence of linear segments, which facilitates and hopefully speeds up the computation for curvature extraction. I
have also tried to optimize evaluateSpline a little bit, so that we can get rid of the Math.pow() calls and use
less multiplications, this should also help us, as this is called very frequently.

With the refined track you can use the function getApproxCurvature() instead of getCurvature(). This function will
not use PeriodicSpline.advance to compute future track points, but approximates spline-parameter distances by
straight-line distances, which should be quite accurate in the case where we have many spline points (i.e. after
pressing refineTrack). I have also tried to optimize the curvature methods a little bit, but this requires that the
direction in which you race with your cars (clockwise or counter-clockwise) matches the order of points along the
spline curve. I have therefore added a "reverseTrack" button with which you can change the order of spline points,
and marked the first point in red instead of magenta (and there is no line drawn between the last and first spline
point).
 *
 * 
 * @author Michael Pfeiffer
 */
public class TrackdefineFilter extends EventFilter2D implements FrameAnnotater, Observer, MouseListener, MouseMotionListener, PropertyChangeListener {

    public static String getDescription() {
        return "Detects a track from incoming pixels and user input";
    }
    // Variables declared in XYTypeFilter
    public short x = 0, y = 0;
    public byte type = 0;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private Point currentMousePoint = null;
    private int currentPointIdx;
    private Point currentInsertMarkPoints = null;
    // Start here with new variable declarations
    // Dimensions of the pixel array
    int numX;
    int numY;
    int numPix;
    // Histogram data
    private float[][] pixData = null;
    // Total sum of histogram points
    float totalSum;
    // draw accumulated tracker locations
    private boolean drawTrackerPoints = getBoolean("drawTrackerPoints", true);
    // Draw histogram in annotate or not
    private boolean drawHist = prefs().getBoolean("TrackdefineFilter.drawHist", false);
    // Threshold for accepting points as track points
    private float histThresh = prefs().getFloat("TrackdefineFilter.histThresh", 0.0001f);
    // Size of erosion mask
    private int erosionSize = prefs().getInt("TrackdefineFilter.erosionSize", 1);
    // Minimum distance between track points
    private float minDistance = prefs().getFloat("TrackdefineFilter.minDistance", 10.0f);
    // Maximum distance between track points
    private float maxDistance = prefs().getFloat("TrackdefineFilter.maxDistance", 50.0f);
    // Interpolation step size for spline curve
    private float stepSize = prefs().getFloat("TrackdefineFilter.stepSize", 0.05f);
    // Whether to draw smooth interpolated track
    private boolean drawSmooth = prefs().getBoolean("TrackdefineFilter.drawSmooth", false);
    // Whether to use curvature to scale the size of vertices
    private boolean scalePointsCurvature = prefs().getBoolean("TrackdefineFilter.scalePointsCurvature", true);
    // Delete or move track points on mouse click
    private boolean deleteOnClick = false; // start always that mouse clicks do not mess up track // prefs().getBoolean("TrackdefineFilter.deleteOnClick", false);
    // Delete or move track points on mouse click
    private boolean insertOnClick = false; // prefs().getBoolean("TrackdefineFilter.insertOnClick", false);
    // Tolerance for mouse clicks
    private float clickTolerance = prefs().getFloat("TrackdefineFilter.clickTolerance", 5.0f);
    // Distance between refined spline points
    private float refineDistance = prefs().getFloat("TrackdefineFilter.refineDistance", 5.0f);
    private int counter = 0;
    // List of extracted track points
    private LinkedList<Point2D.Float> extractPoints;
    // The extracted slotcar track
    private SlotcarTrack extractedTrack;
    // Smooth display points of the interpolated track
    private LinkedList<Point2D.Float> smoothPoints;
    // Display extracted Points
    private boolean displayTrack = prefs().getBoolean("TrackdefineFilter.displayTrack", true);
    private LinkedList<Point2D.Float> trackerPositions = new LinkedList();
    static final private int MAX_TRACKER_POINTS = 1000;  // max points to accumulate from tracker

    public TrackdefineFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);

        resetFilter();
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        // final String y = "y", x = "x", t = "type";
        String disp = "Display";
        String mod = "Modify Track";
        String extr = "Extraction Parameters";
        setPropertyTooltip(disp, "drawHist", "Draw Histogram");
        setPropertyTooltip(disp, "drawTrackerPoints", "Draw tracker points accumulated");
        setPropertyTooltip(extr, "histThresh", "Threshold of histogram points to display");
        setPropertyTooltip(disp, "drawSmooth", "Draw smooth track or only points");
        setPropertyTooltip(disp, "displayTrack", "Display extracted Track Points");
        setPropertyTooltip(extr, "minDistance", "Minimum distance between extracted track points");
        setPropertyTooltip(extr, "maxDistance", "Maximum distance between extracted track points");
        setPropertyTooltip(extr, "erosionSize", "Size of the erosion kernel");
        setPropertyTooltip(extr, "refineDistance", "Distance between refined spline points");
        setPropertyTooltip(disp, "stepSize", "Interpolation step size for spline curve");
        setPropertyTooltip(mod, "deleteOnClick", "Delete track points on mouse click (otherwise move)");
        setPropertyTooltip(mod, "insertOnClick", "Insert track points on mouse click (otherwise move)");
        setPropertyTooltip(mod, "clickTolerance", "Tolerance for mouse clicks (deleting, dragging)");
        setPropertyTooltip(disp, "scalePointsCurvature", "Scale the size of a vertex by the curvature at that point");
        setPropertyTooltip("extractTrack", "Extracts track model from accumulated histogram data");
        setPropertyTooltip("reverseTrack", "Reverse the path numbering so that car increases point number - required for most algorithms");
        setPropertyTooltip("extractTrackFromTrackerPoints", "extract track model from accumlated CarTracker points");

        // New in TrackdefineFilter
        // Initialize histogram
        // numX = chip.getSizeX();
        // numY = chip.getSizeY();
        numX = numY = -1;

        currentPointIdx = -1;

        resetFilter();

        extractPoints = null;
        extractedTrack = null;
        smoothPoints = null;

        File f = getLastFilePrefs();
        try {
            loadTrackFromFile(f);
        } catch (Exception ex) {
            log.warning("couldn't load track information from file " + f + ", caught " + ex + "; save a track to put preferences for track");
        }
        setStepSize(stepSize); // init smooth points too

    }

    /**
     * Constructs the histogram
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        int i;

        int n = in.getSize();
        if (n == 0) {
            return in;
        }
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();

        // for each event only write it to the tmp buffers if it matches
        for (Object obj : in) {
            BasicEvent e = (BasicEvent) obj;
            if ((e.x >= 0) && (e.y >= 0)
                    && (e.x < numX) && (e.y < numY)) {
                // Increase histogram count
                pixData[e.y][e.x] += 1.0f;
                totalSum += 1.0f;
            }
            //       if ((pixData[e.y][e.x] / totalSum) > histThresh)
            pass(outItr, e);
        }

        return out;
    }

    private void pass(OutputEventIterator outItr, BasicEvent e) {
        outItr.nextOutput().copyFrom(e);
    }

    private void pass(OutputEventIterator outItr, TypedEvent te) {
        outItr.nextOutput().copyFrom(te);
    }

    @Override
    synchronized final public void resetFilter() {
//        startX=0; endX=chip.getSizeX();
//        startY=0; endY=chip.getSizeY();
//        startType=0; endType=chip.getNumCellTypes();
        trackerPositions.clear();

        int oldNumX = numX;
        int oldNumY = numY;
        numX = chip.getSizeX();
        numY = chip.getSizeY();
        numPix = numX * numY;

        if ((oldNumX != numX) || (oldNumY != numY)) {
            pixData = new float[numY][numX];

            for (int i = 0; i < numY; i++) {
                for (int j = 0; j < numX; j++) {
                    pixData[i][j] = 0.0f;
                }
            }

            totalSum = 0.0f;
        }
    }

    public void initFilter() {
        resetFilter();
        extractPoints = null;
        extractedTrack = null;
        smoothPoints = null;

    }

    private int clip(int val, int limit) {
        if (val >= limit && limit != 0) {
            return limit;
        } else if (val < 0) {
            return 0;
        }
        return val;
    }

    // Morphological erosion of track histogram
    private boolean[][] erosion() {
        boolean[][] bitmap = new boolean[numY][numX];
        int erSize = erosionSize;
        if (erSize <= 0) {
            // Return original image
            for (int i = 0; i < numY; i++) {
                for (int j = 0; j < numX; j++) {
                    if ((pixData[i][j] * numPix / totalSum) > histThresh) {
                        bitmap[i][j] = true;
                    } else {
                        bitmap[i][j] = false;
                    }
                }
            }
            return bitmap;
        }


        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                boolean keep = true;
                for (int k = -erSize; k <= erSize; k++) {
                    for (int l = -erSize; l <= erSize; l++) {
                        int pixY = clip(i + k, numY - 1); // limit to size-1 to avoid arrayoutofbounds exceptions
                        int pixX = clip(j + l, numX - 1);
                        if ((pixData[pixY][pixX] * numPix / totalSum) < histThresh) {
                            keep = false;
                            break;
                        }
                    }
                    if (keep == false) {
                        break;
                    }
                }
                bitmap[i][j] = keep;
            }
        }

        return bitmap;
    }

    // Draws the histogram (only points above threshold)
    private void drawHistogram(GL gl) {
        // System.out.println("Drawing histogram..." + gl);
//        try {
        boolean[][] bitmap = erosion();
        gl.glColor3f(1.0f, 1.0f, 0);
        gl.glBegin(gl.GL_POINTS);
        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                // if ((pixData[i][j] / totalSum) > histThresh) {
                if (bitmap[i][j]) {
                    gl.glVertex2i(j, i);
                    // gl.glRecti(i, j, i+1, j+1);
                }
            }
        }
//        } catch (ArrayIndexOutOfBoundsException e) {
//            log.warning(e.toString());
//        } finally {
        gl.glEnd();
//        }
//        chip.getCanvas().checkGLError(gl, glu , "in TrackdefineFilter.drawExtractedTrack");

    }

    private void histStatistics() {
        float maxH = 0;
        int count = 0;
        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                float cur = pixData[i][j] / totalSum;
                if (cur > maxH) {
                    maxH = cur;
                }
                if (cur > histThresh) {
                    count++;
                }
            }
        }

        System.out.println("Max: " + maxH + " / Count: " + count);
    }
    GLU glu = new GLU();

    /** Displays the extracted track points */
    private void drawExtractedTrack(GL gl) {
        if (extractedTrack != null && extractPoints != null) {
            // Draw extracted points

            float[] curvatureAtPoints;
            if (scalePointsCurvature) {
                // Use curvature to set size of points
                curvatureAtPoints = extractedTrack.getCurvatureAtPoints();
                if (curvatureAtPoints == null) {
                    return;
                }
            } else {
                int numPoints = extractPoints.size();
                curvatureAtPoints = new float[numPoints];
                for (int i = 0; i < numPoints; i++) {
                    curvatureAtPoints[i] = 5.0f;
                }
            }
            //gl.glColor3d(1.0f, 0.0f, 1.0f);
            //gl.glPointSize(5.0f);
            gl.glColor3d(1.0f, 1.0f, 1.0f);
            Point2D startPoint = null, selectedPoint = null;
            float startSize = 10.0f, selectedSize = 10.0f;
            float minSize = 3.0f;
            int idx = 0;
            for (Point2D p : extractPoints) {
                if (idx == this.currentPointIdx) {
                    selectedPoint = p;
                    selectedSize = minSize + Math.min(200.0f, Math.abs(curvatureAtPoints[idx])) / 10.0f;

                } else if (idx == 0) {
                    // Plot first point of the track in special color, but we cannot call setColor inside glBegin/glEnd
                    startPoint = p;
                    startSize = minSize + Math.min(200.0f, Math.abs(curvatureAtPoints[idx])) / 10.0f;
                } else {
                    float curveSize = minSize + Math.min(200.0f, Math.abs(curvatureAtPoints[idx])) / 10.0f;
                    gl.glPointSize(curveSize);
                    gl.glBegin(gl.GL_POINTS);
                    gl.glVertex2d(p.getX(), p.getY());
                    gl.glEnd();
                }
                idx++;
            }

            if (startPoint != null) {
                gl.glColor3d(1.0f, 0.0f, 0.0f);
                gl.glPointSize(startSize);
                gl.glBegin(gl.GL_POINTS);
                gl.glVertex2d(startPoint.getX(), startPoint.getY());
                gl.glEnd();
            }

            if (selectedPoint != null) {
                // Plot selected point in special color
                gl.glColor3d(1.0f, 0.0f, 1.0f);
                gl.glPointSize(selectedSize);
                gl.glBegin(gl.GL_POINTS);
                gl.glVertex2d(selectedPoint.getX(), selectedPoint.getY());
                gl.glEnd();
            }

            // Plot lines
            gl.glPointSize(1.0f);
            gl.glBegin(gl.GL_LINE_STRIP);
            for (Point2D p : extractPoints) {
                gl.glVertex2d(p.getX(), p.getY());
            }
            gl.glEnd();

            // Plot lines to mark insert points
            if (insertOnClick) {
                if (currentInsertMarkPoints != null) {
                    if ((currentInsertMarkPoints.getX() >= 0)
                            && (currentInsertMarkPoints.getY() >= 0)) {
                        Point2D startP = extractPoints.get((int) currentInsertMarkPoints.getX());
                        Point2D endP = extractPoints.get((int) currentInsertMarkPoints.getY());
                        gl.glColor3d(1.0f, 1.0f, 1.0f);
                        gl.glLineWidth(5.0f);
                        gl.glBegin(gl.GL_LINE);
                        gl.glVertex2d(startP.getX(), startP.getY());
                        gl.glVertex2d(endP.getX(), endP.getY());
                        gl.glEnd();
                        gl.glLineWidth(1.0f);
                    }
                }
            }

            if (drawSmooth && smoothPoints != null) {
                // Draw smooth interpolated track

                gl.glColor3f(0.0f, 1.0f, 1.0f);
                gl.glBegin(gl.GL_LINE_LOOP);
                for (Point2D p : smoothPoints) {
                    gl.glVertex2d(p.getX(), p.getY());
                }
                gl.glEnd();
            }
        }
        chip.getCanvas().checkGLError(gl, glu, "in TrackdefineFilter.drawExtractedTrack");

    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (counter < 1) {
            counter++;
        } else {
            if (drawable == null || chip.getCanvas() == null) {
                System.out.println("Null drawable or chip.getCanvas(), why?");
                return;
            }
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) canvas.getCanvas();
            GL gl = drawable.getGL();

            // histStatistics();

            gl.glPushMatrix();
            if (drawHist) {
//                try {
                drawHistogram(gl);
//                } catch (Exception e) {
//                    log.warning(e.toString());
//                }
            }
            gl.glPopMatrix();

            if (displayTrack) {
//                try {
                drawExtractedTrack(gl);
//                } catch (Exception e) {
//                    log.warning(e.toString());
//                }
            }

            if (drawTrackerPoints) {
                drawTrackerPoints(gl);
            }

            counter = 0;
        }

    }
    private Point2D.Float lastTrackerPosition = null;

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            resetFilter();
        } else if (isFilterEnabled() && o instanceof TwoCarTracker && arg instanceof UpdateMessage) {
            TwoCarTracker tracker = (TwoCarTracker) o;

            CarCluster car = tracker.findCarCluster();
            if (car != null) {
                Point2D.Float carPoint = car.getLocation();
                if (lastTrackerPosition == null || lastTrackerPosition.distance(carPoint) > minDistance) {
                    Point2D.Float newPoint = (Point2D.Float) carPoint.clone();
                    trackerPositions.add(newPoint);
                    lastTrackerPosition = newPoint;
                    if (trackerPositions.size() > MAX_TRACKER_POINTS) {
                        trackerPositions.removeFirst();
                    }
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if ((currentPointIdx < 0) || (extractedTrack == null)) {
            return;
        }

        // Move point if selected

        currentMousePoint = canvas.getPixelFromMouseEvent(e);

        if ((currentMousePoint.getX() >= 0) && (currentMousePoint.getY() >= 0)
                && (currentMousePoint.getX() < numX) && (currentMousePoint.getY() < numY)) {

            // Move point 
            extractedTrack.setPoint(currentPointIdx, new Point2D.Float(currentMousePoint.x, currentMousePoint.y));
            extractedTrack.updateTrack();
            extractPoints = extractedTrack.getPointList();
            smoothPoints = extractedTrack.getSmoothPoints(stepSize);
        }

        currentPointIdx = -1;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (insertOnClick) {
            currentMousePoint = canvas.getPixelFromMouseEvent(e);
            currentInsertMarkPoints = findClosestTwoPointsForInsert(currentMousePoint);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // System.out.println("Dragging " + currentPointIdx);
        currentMousePoint = canvas.getPixelFromMouseEvent(e);
        if (!deleteOnClick) {
            if (extractedTrack == null) {
                return;
            }

            // Select point for dragging
            if (currentPointIdx < 0) {
                int idx = extractedTrack.findClosestIndex(currentMousePoint, clickTolerance, false);
                // System.out.println("New drag " + idx + " / " + clickTolerance);
                if (idx >= 0) {
                    currentPointIdx = idx;
                }
            } else {
                extractedTrack.setPoint(currentPointIdx, new Point2D.Float(currentMousePoint.x, currentMousePoint.y));
                extractPoints = extractedTrack.getPointList();
            }
        }
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    /** Finds the nearest two track points to a point for insertion of a new track point.
     *
     * @param p the Point to test for. This Point has int x and y values.
     * @return a Point with the indices of the two points as x and y values.
     */
    private Point findClosestTwoPointsForInsert(Point p) {
        Point idxP = new Point(-1, -1);

        if (extractedTrack == null) {
            return idxP;
        }

        int idx1 = extractedTrack.findClosestIndex(p, Float.MAX_VALUE, false);
        if (idx1 >= 0) {
            // Find which one of the neighbors is closest
            int idx2 = idx1 - 1;
            if (idx2 < 0) {
                idx2 = extractedTrack.getNumPoints() - 1;
            }

            int idx3 = idx1 + 1;
            if (idx3 >= extractedTrack.getNumPoints()) {
                idx3 = 0;
            }

            Point2D p2 = extractedTrack.getPoint(idx2);
            Point2D p3 = extractedTrack.getPoint(idx3);

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

    @Override
    public void mouseClicked(MouseEvent e) {
        // System.out.println("Click " + currentPointIdx);
        Point p = canvas.getPixelFromMouseEvent(e);
        if (deleteOnClick) {
            // Delete point
            if (extractedTrack != null) {
                int idx = extractedTrack.findClosestIndex(p, clickTolerance, false);
                if (idx >= 0) {
                    extractedTrack.deletePoint(idx);
                    extractedTrack.updateTrack();
                    extractPoints = extractedTrack.getPointList();
                    smoothPoints = extractedTrack.getSmoothPoints(stepSize);
                }
            }
        } else if (insertOnClick) {
            // Insert point between existing points
            if (extractedTrack != null) {
                Point idxP = findClosestTwoPointsForInsert(p);
                if ((idxP.getX() >= 0) && (idxP.getY() >= 0)) {
                    if ((idxP.getX() == extractedTrack.getNumPoints() - 1) && (idxP.getY() == 0)) {
                        extractedTrack.addPoint(new Point2D.Float(p.x, p.y));
                    } else {
                        extractedTrack.insertPoint((int) idxP.getX(), new Point2D.Float(p.x, p.y));
                    }
                }
                extractedTrack.updateTrack();
                extractPoints = extractedTrack.getPointList();
                smoothPoints = extractedTrack.getSmoothPoints(stepSize);
            }
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (glCanvas == null) {
            return;
        }
        if (yes) {
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    public boolean isDrawHist() {
        return drawHist;
    }

    public void setDrawHist(boolean drawHist) {
        this.drawHist = drawHist;
        getPrefs().putBoolean("TrackdefineFilter.drawHist", drawHist);
    }

    public float getHistThresh() {
        return histThresh;
    }

    public void setHistThresh(float histThresh) {
        this.histThresh = histThresh;
        getPrefs().putFloat("TrackdefineFilter.histThresh", histThresh);
    }

    public int getErosionSize() {
        return erosionSize;
    }

    public void setErosionSize(int erosionSize) {
        this.erosionSize = erosionSize;
        getPrefs().putInt("TrackdefineFilter.erosionSize", erosionSize);
    }

    public float getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(float minDistance) {
        this.minDistance = minDistance;
        prefs().putFloat("TrackdefineFilter.minDistance", minDistance);
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
        prefs().putFloat("TrackdefineFilter.maxDistance", maxDistance);
    }

    public boolean isDisplayTrack() {
        return displayTrack;
    }

    public void setDisplayTrack(boolean displayTrack) {
        this.displayTrack = displayTrack;
        prefs().putBoolean("TrackdefineFilter.displayTrack", displayTrack);
    }

    public float getStepSize() {
        return stepSize;
    }

    final public void setStepSize(float stepSize) {
        this.stepSize = stepSize;
        if (extractedTrack != null) {
            smoothPoints = extractedTrack.getSmoothPoints(stepSize);
        }
        prefs().putFloat("TrackdefineFilter.stepSize", stepSize);
    }

    public boolean isDrawSmooth() {
        return drawSmooth;
    }

    public void setDrawSmooth(boolean drawSmooth) {
        this.drawSmooth = drawSmooth;
        prefs().putBoolean("TrackdefineFilter.drawSmooth", drawSmooth);
        setStepSize(stepSize); // init smooth points too
    }

    public boolean isDeleteOnClick() {
        return deleteOnClick;
    }

    public void setDeleteOnClick(boolean deleteOnClick) {
        boolean old = this.deleteOnClick;
        this.deleteOnClick = deleteOnClick;
        getSupport().firePropertyChange("deleteOnClick", old, deleteOnClick);
        if (deleteOnClick) {
            if (isInsertOnClick()) {
                setInsertOnClick(false);
            }
        }
    }

    public boolean isInsertOnClick() {
        return insertOnClick;
    }

    public void setInsertOnClick(boolean insertOnClick) {
        boolean old = this.insertOnClick;
        this.insertOnClick = insertOnClick;
        getSupport().firePropertyChange("insertOnClick", old, insertOnClick);

        if (insertOnClick) {
            if (isDeleteOnClick()) {
                setDeleteOnClick(false);
            }
        } else {
            currentInsertMarkPoints = null;
        }
    }

    public float getClickTolerance() {
        return clickTolerance;
    }

    public void setClickTolerance(float clickTolerance) {
        this.clickTolerance = clickTolerance;
        prefs().putFloat("TrackdefineFilter.clickTolerance", clickTolerance);
    }

    public float getRefineDistance() {
        return refineDistance;
    }

    public void setRefineDistance(float refineDistance) {
        this.refineDistance = refineDistance;
        prefs().putFloat("TrackdefineFilter.refineDistance", refineDistance);
    }

    public boolean isScalePointsCurvature() {
        return scalePointsCurvature;
    }

    public void setScalePointsCurvature(boolean scalePointsCurvature) {
        this.scalePointsCurvature = scalePointsCurvature;
        prefs().putBoolean("TrackdefineFilter.scalePointsCurvature", scalePointsCurvature);
    }

    /**
     * Re-initializes the histogram of events.
     */
    synchronized public void doInitHistogram() {
        pixData = new float[numY][numX];
        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                pixData[i][j] = 0.0f;
            }
        }

        totalSum = 0.0f;
    }

    /** Invalidates all points within minDistance of (x,y) in the queue */
    private void invalidateQueue(PriorityQueue<TrackPoint> pq, float x, float y) {
        Point2D.Float pos = new Point2D.Float(x, y);

        Iterator<TrackPoint> it = pq.iterator();
        LinkedList<TrackPoint> toAdd = new LinkedList<TrackPoint>();

        while (it.hasNext()) {
            TrackPoint p = it.next();
            float distance = (float) pos.distance(p.x, p.y);
            it.remove();
            if (distance > minDistance) {
                p.minDistance = distance;
                toAdd.add(p);
            }
        }

        // Add all updated points
        pq.addAll(toAdd);
    }

    /**
     * Extracts the track from the histogram of events.
     */
    synchronized public void doExtractTrack() {
        boolean[][] trackPoints = erosion();

        // Find starting point
        int maxX = -1, maxY = -1;
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                if (pixData[i][j] > maxVal) {
                    maxVal = pixData[i][j];
                    maxX = j;
                    maxY = i;
                }
            }
        }

        // Insert starting point
        extractPoints = new LinkedList<Point2D.Float>();
        extractPoints.add(new Point2D.Float((float) maxX, (float) maxY));
        boolean trackFinished = false;


        // Prepare queue of potential track points
        PriorityQueue<TrackPoint> pq = new PriorityQueue<TrackPoint>();

        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                if (trackPoints[i][j]) {
                    // Insert into queue
                    float dist = (float) Point.distance(maxX, maxY, j, i);
                    if (dist > minDistance) {
                        TrackPoint p = new TrackPoint(j, i, dist);
                        pq.add(p);
                    }
                }
            }
        }

        float curX = maxX;
        float curY = maxY;
        while (!trackFinished) {
            // Delete track point which are too close to current points
            invalidateQueue(pq, curX, curY);

            if (pq.isEmpty()) {
                trackFinished = true;
            } else {
                // Add new track point
                TrackPoint nextPoint = pq.remove();
                if (Point2D.distance(nextPoint.x, nextPoint.y, curX, curY) < maxDistance) {
                    extractPoints.add(new Point2D.Float(nextPoint.x, nextPoint.y));
                    curX = nextPoint.x;
                    curY = nextPoint.y;
                } else {
                    System.out.println("No more points in distance!");
                    trackFinished = true;
                }
            }
        }

        System.out.println("Extracted " + extractPoints.size() + " track points!");

        // Create track object and spline
        extractedTrack = new SlotcarTrack();
        extractedTrack.getSupport().addPropertyChangeListener(this);
        extractedTrack.create(extractPoints);

        smoothPoints = extractedTrack.getSmoothPoints(stepSize);
        extractedTrack.getSupport().addPropertyChangeListener(SlotcarTrack.EVENT_TRACK_CHANGED, this);
        extractedTrack.updateTrack();
    } // doExtractTrack

    /**
     * Extracts the track from the histogram of events.
     */
    synchronized public void doExtractTrackFromTrackerPoints() {
        if (trackerPositions == null) {
            log.warning("no trackerPositions to use");
            return;
        }
        // Find starting point


        Point2D.Float firstPoint = trackerPositions.getFirst();
        float firstX = firstPoint.x, firstY = firstPoint.y;


        // Insert starting point
        extractPoints = new LinkedList<Point2D.Float>();
        extractPoints.add(new Point2D.Float(firstPoint.x, firstPoint.y));
        boolean trackFinished = false;


        // Prepare queue of potential track points
        PriorityQueue<TrackPoint> pq = new PriorityQueue<TrackPoint>();
        Point2D.Float lastPoint = firstPoint;
        for (Point2D.Float p : trackerPositions) {
            // Insert into queue, maybe
            float dist;
            if ((dist = (float) p.distance(lastPoint)) > minDistance) {
                TrackPoint tp = new TrackPoint(p.x, p.y, dist);
                pq.add(tp);
            }
        }

        float curX = firstX;
        float curY = firstY;
        while (!trackFinished) {
            // Delete track point which are too close to current points
            invalidateQueue(pq, curX, curY);

            if (pq.isEmpty()) {
                trackFinished = true;
            } else {
                // Add new track point
                TrackPoint nextPoint = pq.remove();
                if (Point2D.distance(nextPoint.x, nextPoint.y, curX, curY) < maxDistance) {
                    extractPoints.add(new Point2D.Float(nextPoint.x, nextPoint.y));
                    curX = nextPoint.x;
                    curY = nextPoint.y;
                } else {
                    log.info("No more points within maxDistance=" + maxDistance);
                    trackFinished = true;
                }
            }
        }

        log.info("Extracted " + extractPoints.size() + " track points!");

        // Create track object and spline
        extractedTrack = new SlotcarTrack();
        extractedTrack.getSupport().addPropertyChangeListener(this);
        extractedTrack.create(extractPoints);

        smoothPoints = extractedTrack.getSmoothPoints(stepSize);
        extractedTrack.getSupport().addPropertyChangeListener(SlotcarTrack.EVENT_TRACK_CHANGED, this); // TODO should fire property change
        extractedTrack.updateTrack();
    } // doExtractTrack

    /**
     * Saves the extracted track to an external file.
     */
    public void doSaveTrack() {
        if (extractedTrack == null) {
            // No track defined
            return;
        }

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

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setSelectedFile(new File("test.track"));
                state[0] = fc.showSaveDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
                if (state[0] == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    putLastFilePrefs(file);

                    try {
                        saveTrackToFile(file);
                    } catch (IOException ex) {
                        log.warning("couldn't save track to file, caught: " + ex);
                        JOptionPane.showMessageDialog(fc, "couldn't save track to file, caught: " + ex, "Saving track failed", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    log.info("Cancelled saving!");
                }
            }
        });
    }

    private void putLastFilePrefs(File file) {
        prefs().put("TrackdefineFilter.lastFile", file.toString());
    }

    private File getLastFilePrefs() {
        return new File(prefs().get("TrackdefineFilter.lastFile", System.getProperty("user.dir")));
    }

    private void saveTrackToFile(File file) throws IOException {
        if (extractedTrack == null) {
            throw new IOException("null extractedTrack, can't save track");
        }
        if (extractPoints == null) {
            throw new IOException("null extractPoints, can't save track");
        }
        log.info("Saving track data to " + file.getName());
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(extractedTrack);
        oos.writeObject(extractPoints);
        oos.close();
        fos.close();
    }

    /**
     * Saves the extracted track to an external file.
     */
    public void doLoadTrack() {

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

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setSelectedFile(new File("test.track"));
                state[0] = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
                if (state[0] == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    putLastFilePrefs(file);
                    try {
                        loadTrackFromFile(file);
                    } catch (Exception e) {
                        log.warning(e.toString());
                        JOptionPane.showMessageDialog(fc, "Couldn't load track from file " + file + ", caught exception " + e, "Track file warning", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    log.info("Cancelled saving!");
                }
            }
        });
    }

    private void loadTrackFromFile(File file) throws HeadlessException, IOException, ClassNotFoundException {
        Object old = extractedTrack;
        if (file == null) {
            throw new IOException("null filename, can't load track from file - track needs to be saved first");
        }
        log.info("loading track data from " + file);
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        extractedTrack = (SlotcarTrack) ois.readObject();
        // extractPoints = (LinkedList<Point2D.Float>) ois.readObject();  // unchecked cast exception
        LinkedList loadPoints = (LinkedList<?>) ois.readObject();
        extractPoints = new LinkedList<Point2D.Float>();
        for (Object o : loadPoints) {
            extractPoints.add((Point2D.Float) o);
        }
        ois.close();
        fis.close();
        extractedTrack.getSupport().addPropertyChangeListener(this);
        extractedTrack.updateTrack(); // update other internal vars of track
    }

    /**
     * Returns the extracted track.
     * @return The extracted track
     */
    public SlotcarTrack getTrack() {
        return this.extractedTrack;
    }

    /**
     * Refines the extracted track by inserting intermediate spline points.
     */
    synchronized public void doRefineTrack() {
        if (extractedTrack != null) {
            extractedTrack.refine(refineDistance);
            extractPoints = extractedTrack.getPointList();
        }
    }

    /**
     * Reverses the direction of the track.
     */
    synchronized public void doReverseTrack() {
        if (extractedTrack != null) {
            extractedTrack.reverseTrack();
            extractPoints = extractedTrack.getPointList();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        getSupport().firePropertyChange(evt); // pass on event from SlotcarTrack
    }

    /**
     * @return the drawTrackerPoints
     */
    public boolean isDrawTrackerPoints() {
        return drawTrackerPoints;
    }

    /**
     * @param drawTrackerPoints the drawTrackerPoints to set
     */
    public void setDrawTrackerPoints(boolean drawTrackerPoints) {
        this.drawTrackerPoints = drawTrackerPoints;
    }

    private void drawTrackerPoints(GL gl) {
        if (trackerPositions == null) {
            return;
        }
        for (Point2D p : trackerPositions) {
            final float size = 2;
            gl.glPointSize(size);
            float rgb[] = {0, 0, .5f};
            gl.glColor3fv(rgb, 0);
            gl.glBegin(gl.GL_POINTS);
            gl.glVertex2d(p.getX(), p.getY());
            gl.glEnd();
        }
    }

    /**
     * A utility class for points in a priority queue, ordered by their distance to
     * other points in the queue.
     */
    class TrackPoint implements Comparable<TrackPoint>, Observer { // Observer to handle chip changes - to get chip size after construction

        public float x;
        public float y;
        public float minDistance;

        public TrackPoint(float x, float y, float minDistance) {
            this.x = x;
            this.y = y;
            this.minDistance = minDistance;
        }

        public int compareTo(TrackPoint p) {
            if (p.minDistance < minDistance) {
                return +1;
            } else if (p.minDistance == minDistance) {
                return 0;
            } else {
                return -1;
            }
        }

        public void update(Observable o, Object arg) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
