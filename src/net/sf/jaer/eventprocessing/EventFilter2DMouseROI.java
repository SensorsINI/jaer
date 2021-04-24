/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Adds a mouse adaptor to enable ROI (region of interest) roiRect with mouse.
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
    protected boolean freezeRoi = getBoolean("freezeRoi", false);

    // roiRect stuff
    protected int roiStartx, roiStarty, roiEndx, roiEndy;
    protected Point roiStartPoint = null, roiEndPoint = null, clickedPoint = null;
    protected Rectangle roiRect = null;

    protected volatile boolean roiSelecting = false;
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};
    private Point currentMousePoint = null;

    public EventFilter2DMouseROI(AEChip chip) {
        super(chip);
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
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
     * mouse is being used. Subclasses can override this functionality.
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
        Rectangle chipRect = new Rectangle(sx, sy);
        if (roiRect != null && chipRect.intersects(roiRect)) {
            drawRoi(gl, roiRect, SELECT_COLOR);
        }

        chip.getCanvas().checkGLError(gl, glu, "in annotate");

    }

    private void drawRoi(GL2 gl, Rectangle r, float[] c) {
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(3);
        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(roiRect.x, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y + roiRect.height);
        gl.glVertex2f(roiRect.x, roiRect.y + roiRect.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    /**
     * When this is selected in the FilterPanel GUI, the mouse listeners will be
     * added. When this is unselected, the listeners will be removed.
     *
     */
    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes);
        chipCanvas = chip.getCanvas();
        if (chipCanvas == null) {
            log.warning("null chip canvas, can't add mouse listeners");
            return;
        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            log.warning("null chip canvas GL drawable, can't add mouse listeners");
            return;
        }
        if (yes) {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.removeMouseWheelListener(this);
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);
            glCanvas.addMouseWheelListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.removeMouseWheelListener(this);
        }
    }

    /**
     * Returns the chip pixel position from the MouseEvent. Note that any calls
     * that modify the GL model matrix (or viewport, etc) will make the location
     * meaningless. Make sure that your graphics rendering code wraps transforms
     * inside pushMatrix and popMatrix calls.
     *
     * @param e the mouse event
     * @return the pixel position in the chip object, origin 0,0 in lower left
     * corner.
     */
    protected Point getMousePixel(MouseEvent e) {
        if (chipCanvas == null) {
            return null;
        }
        Point p = chipCanvas.getPixelFromMouseEvent(e);
        if (chipCanvas.wasMousePixelInsideChipBounds()) {
            return p;
        } else {
            return null;
        }
    }

    /**
     * Handles wheel event. Empty by default
     *
     * @param mwe the mouse wheel roll event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {

    }

    /**
     * @return the showCrossHairCursor
     */
    protected boolean isShowCrossHairCursor() {
        return showCrossHairCursor;
    }

    /**
     * By default a cross hair roiRect cursor is drawn. This method prevent
     * drawing the cross hair.
     *
     * @param showCrossHairCursor the showCrossHairCursor to set
     */
    protected void setShowCrossHairCursor(boolean showCrossHairCursor) {
        this.showCrossHairCursor = showCrossHairCursor;
    }

    // ROI roiRect stuff
    public void doClearSelection() {
        clearSelection();
    }

    private void clearSelection() {
        roiRect = null;
    }

    private void getSelection(MouseEvent e) {
        Point p = chip.getCanvas().getPixelFromMouseEvent(e);
        roiEndPoint = p;
        roiStartx = min(roiStartPoint.x, roiEndPoint.x);
        roiStarty = min(roiStartPoint.y, roiEndPoint.y);
        roiEndx = max(roiStartPoint.x, roiEndPoint.x) + 1;
        roiEndy = max(roiStartPoint.y, roiEndPoint.y) + 1;
        int w = roiEndx - roiStartx;
        int h = roiEndy - roiStarty;
        roiRect = new Rectangle(roiStartx, roiStarty, w, h);
    }

    private boolean inSelection(BasicEvent e) {
        if (roiRect == null || roiRect.isEmpty() || roiRect.contains(e.x, e.y)) {
            return true;
        }
        return false;
    }

    private void drawSelection(GL2 gl, Rectangle r, float[] c) {
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(3);
        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(roiRect.x, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y + roiRect.height);
        gl.glVertex2f(roiRect.x, roiRect.y + roiRect.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = chip.getCanvas().getPixelFromMouseEvent(e);
        roiStartPoint = p;
        if (!freezeRoi) {
            roiSelecting = true;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        currentMousePoint = chip.getCanvas().getPixelFromMouseEvent(e);
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
        if (freezeRoi || roiStartPoint == null) {
            return;
        }
        getSelection(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = chip.getCanvas().getPixelFromMouseEvent(e);
        clickedPoint = p;
    }

    /**
     * @return the freezeSelection
     */
    public boolean isFreezeSelection() {
        return freezeRoi;
    }

    /**
     * @param freezeSelection the freezeSelection to set
     */
    public void setFreezeSelection(boolean freezeRoi) {
        this.freezeRoi = freezeRoi;
        putBoolean("freezeRoi", freezeRoi);
    }

}
