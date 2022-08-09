/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Point;
import java.awt.event.MouseEvent;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.DrawGL;

/**
 * Adds a mouse adaptor to enable ROI (region of interest) roiRects with mouse.
 *
 * @author Tobi
 */
abstract public class EventFilter2DMouseROI extends EventFilter2DMouseAdaptor {

    protected GLCanvas glCanvas;
    protected ChipCanvas chipCanvas;
    /**
     * Cursor size for drawn mouse cursor when filter is selected.
     */
    protected final float CURSOR_SIZE_CHIP_PIXELS = 7;
    protected GLU glu = new GLU();
    protected GLUquadric quad = null;
    private boolean hasBlendChecked = false, hasBlend = false;
    protected boolean showCrossHairCursor = true;

    /**
     * Flag that freezes ROI selection
     */
    protected boolean freezeRoi = getBoolean("freezeRoi", false);
    private boolean multiROI = getBoolean("multiROI", false);

    // roiRects stuff
    /**
     * ROI start/end corner index
     */
    protected int roiStartx, roiStarty, roiEndx, roiEndy;
    /**
     * ROI start/end corners and last clicked mouse point
     */
    protected Point roiStartPoint = null, roiEndPoint = null, clickedPoint = null;
    /**
     * ROI rectangle(s)
     */
    protected ArrayList<Rectangle> roiRects = new ArrayList();

    /**
     * Boolean that indicates ROI is being selected currently
     */
    protected volatile boolean roiSelecting = false;
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f}, ROI_COLOR = {.5f, 0, 0, .5f};

    /**
     * The current mouse point in chip pixels, updated by mouseMoved
     */
    protected Point currentMousePoint = null;

    public EventFilter2DMouseROI(AEChip chip) {
        super(chip);
        String roi = "Region of interest";
        setPropertyTooltip(roi, "freezeRoi", "Freezes ROI(s) (region of interest) selection");
        setPropertyTooltip(roi, "clearROI", "Clears ROI(s) (region of interest)");
        setPropertyTooltip(roi, "multiROI", "Enable multiple ROIs");
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        try {
            roiRects = (ArrayList<Rectangle>) getObject("roiRects", null);
        } catch (Exception e) {
            log.warning("cannot load existing ROIs: " + e.toString());
        }
    }
    float[] cursorColor = null;

    /**
     * Sets a cursor color vector
     *
     * @param color a 4-vector. Set it to null to return to default white
     * shadowed cursor.
     */
    protected void setCursorColor(float[] color) {
        if (color == null) {
            return;
        }
        this.cursorColor = color;
    }

    /**
     * Annotates the display with the current mouse position to indicate that
     * mouse is being used and shows the ROI if there is one. Subclasses can
     * override this functionality.
     *
     * @param drawable
     */
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        chipCanvas = chip.getCanvas();
        if (chipCanvas == null) {
            return;
        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            return;
        }
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        drawRois(gl, ROI_COLOR);

        if (roiSelecting) {
            gl.glPushMatrix();
            gl.glColor3fv(SELECT_COLOR, 0);
            gl.glLineWidth(3);
//        gl.glTranslatef(-.5f, -.5f, 0);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(roiStartPoint.x, roiStartPoint.y);
            gl.glVertex2f(roiStartPoint.x, currentMousePoint.y);
            gl.glVertex2f(currentMousePoint.x, currentMousePoint.y);
            gl.glVertex2f(currentMousePoint.x, roiStartPoint.y);
            gl.glEnd();
            gl.glPopMatrix();
        }

        chip.getCanvas().checkGLError(gl, glu, "in annotate");

    }

    private void drawRois(GL2 gl, float[] c) {
        if (roiRects == null || roiRects.isEmpty()) {
            return;
        }
        gl.glPushMatrix();
//        gl.glTranslatef(-.5f, -.5f, 0);
        int i = 0;
        for (Rectangle r : roiRects) {
            gl.glColor3fv(c, 0);
            gl.glLineWidth(3);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(r.x, r.y);
            gl.glVertex2f(r.x + r.width, r.y);
            gl.glVertex2f(r.x + r.width, r.y + r.height);
            gl.glVertex2f(r.x, r.y + r.height);
            gl.glEnd();
            gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT);
            DrawGL.drawString(gl, 10, r.x, r.y, 0, Color.RED.darker(), Integer.toString(i));
            gl.glPopAttrib();
            i++;
        }
        gl.glPopMatrix();

    }

    /**
     * @return the showCrossHairCursor
     */
    protected boolean isShowCrossHairCursor() {
        return showCrossHairCursor;
    }

    /**
     * By default a cross hair roiRects cursor is drawn. This method prevent
     * drawing the cross hair.
     *
     * @param showCrossHairCursor the showCrossHairCursor to set
     */
    protected void setShowCrossHairCursor(boolean showCrossHairCursor) {
        this.showCrossHairCursor = showCrossHairCursor;
    }

    // ROI roiRects stuff
    synchronized public void doClearROI() {
        if (freezeRoi) {
            showWarningDialogInSwingThread("Are you sure you want to clear ROI? Uncheck freezeROI if you want to clear the ROI.", "ROI frozen");
            return;
        }
        clearSelection();
    }

    private void clearSelection() {
        roiRects.clear();
    }

    synchronized private void startRoiSelection(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            roiRects = null;
            return;
        }
        roiStartPoint = p;
        log.info("ROI start point = " + p);
        roiSelecting = true;
    }

    synchronized private void finishRoiSelection(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            roiRects = null;
            return;
        }

        roiEndPoint = p;
        roiStartx = min(roiStartPoint.x, roiEndPoint.x);
        roiStarty = min(roiStartPoint.y, roiEndPoint.y);
        roiEndx = max(roiStartPoint.x, roiEndPoint.x) + 1;
        roiEndy = max(roiStartPoint.y, roiEndPoint.y) + 1;
        int w = roiEndx - roiStartx;
        int h = roiEndy - roiStarty;
        Rectangle newRoi = new Rectangle(roiStartx, roiStarty, w, h);
        if (!isMultiROI() || roiRects == null) {
            roiRects = new ArrayList<Rectangle>();
        }
        roiRects.add(newRoi);
        putObject("roiRects", roiRects);
    }

    /**
     * Returns true if the event is inside (or on border) of any of the ROI(s)
     *
     * @param e an event
     * @return true if on or inside any ROI, false if no ROI or outside
     */
    protected boolean isInsideAnyROI(BasicEvent e) {
        Point p = new Point(e.x, e.y);
        return isInsideAnyROI(p);
    }

    /**
     * Returns number of ROI (0 based) if the event is inside (or on border) of
     * any of the ROI(s), otherwise returns -1. If no ROIs are defined, returns
     * 0.
     *
     * @param e an event
     * @return number of ROI if on or inside an ROI, -1 if no ROI or outside
     */
    protected int isInsideWhichROI(BasicEvent e) {
        Point p = new Point(e.x, e.y);
        return isInsideWhichROI(p);
    }

    /**
     * Returns true if the Point is inside (or on border) of any of the ROI(s)
     *
     * @param p a Point
     * @return true if on or inside any ROI, false if no ROI or outside
     */
    protected boolean isInsideAnyROI(Point p) {
        if (roiRects == null || roiRects.isEmpty()) {
            return true;
        }
        for (Rectangle r : roiRects) {
            if (r.contains(p.x, p.y)) {
                return true;
            }
        }
        return false;

    }

    /**
     * Returns number of ROI (0 based) if the point is inside (or on border) of
     * any of the ROI(s), otherwise returns -1. If no ROIs are defined, returns
     * 0.
     *
     * @param p a Point
     * @return number of ROI if on or inside an ROI, -1 if no ROI or outside
     */
    protected int isInsideWhichROI(Point p) {
        if (roiRects == null || roiRects.isEmpty()) {
            return 0;
        }
        int i = 0;
        for (Rectangle r : roiRects) {
            if (r.contains(p.x, p.y)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Returns either size of chip array if there are no ROIs, or the sum of all
     * the ROI rectangles
     *
     * @return number of pixels
     */
    protected int getNumRoiPixels() {
        if (roiRects == null || roiRects.isEmpty()) {
            return chip.getNumPixels();
        }
        int sum = 0;
        for (Rectangle r : roiRects) {
            sum += (r.width) * (r.height);
        }
        return sum;
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = getMousePixel(e);
        if (!freezeRoi) {
            startRoiSelection(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (freezeRoi || roiStartPoint == null) {
            return;
        }
        finishRoiSelection(e);
        roiSelecting = false;

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        currentMousePoint = getMousePixel(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        roiSelecting = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (roiStartPoint == null) {
            return;
        }
        if (freezeRoi) {
            log.warning("disable freezeRoi if you want to select a region of interest");
            return;
        }
        currentMousePoint = getMousePixel(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = getMousePixel(e);
        clickedPoint = p;
    }

    /**
     * @return the freezeSelection
     */
    public boolean isFreezeRoi() {
        return freezeRoi;
    }

    /**
     * @param freezeSelection the freezeSelection to set
     */
    public void setFreezeRoi(boolean freezeRoi) {
        this.freezeRoi = freezeRoi;
        putBoolean("freezeRoi", freezeRoi);
    }

    /**
     * @return the multiROI
     */
    public boolean isMultiROI() {
        return multiROI;
    }

    /**
     * @param multiROI the multiROI to set
     */
    public void setMultiROI(boolean multiROI) {
        this.multiROI = multiROI;
    }

}
