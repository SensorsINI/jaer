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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Adds a mouse adaptor to the basic EventFilter2D to let subclasses more easily
 * integrate mouse events into their functionality.
 * <p>
 * For example, {@link ch.unizh.ini.jaer.projects.minliu.Speedometer} uses it
 * like this:
 * <pre>
 * protected TimePoint startPoint;
 * protected TimePoint endPoint;
 * private boolean firstPoint = true;
 *
 *
 * synchronized public void mouseClicked(MouseEvent e)
 * {
 * if (!isSelected())
 * {
 * return;
 * }
 * if (e == null || e.getPoint() == null || getMousePixel(e) == null)
 * {
 * firstPoint = true;
 * setStartPoint(null);
 * setEndPoint(null);
 * log.info("reset to first point");
 * return; // handle out of bounds, which should reset
 * }
 * if (firstPoint)
 * {
 * setStartPoint(new TimePoint(getMousePixel(e), currentTimestamp));
 * setEndPoint(null);
 * }
 * else
 * {
 * setEndPoint(new TimePoint(getMousePixel(e), currentTimestamp));
 * }
 * computeVelocity();
 * firstPoint = !firstPoint;
 * }
 *
 * synchronized public void annotate(GLAutoDrawable drawable)
 * {
 * if (isSelected())
 * {
 * if (firstPoint)
 * {
 * setCursorColor(START_COLOR);
 * }
 * else
 * {
 * setCursorColor(END_COLOR);
 * }
 * }
 * GL2 gl = drawable.getGL().getGL2();
 * gl.glPushMatrix();
 * drawCursor(gl, getStartPoint(), START_COLOR);
 * drawCursor(gl, getEndPoint(), END_COLOR);
 * gl.glPopMatrix(); // must push pop since drawCursor translates?
 * if (getStartPoint() != null && getEndPoint() != null)
 * {
 * gl.glPushMatrix();
 * gl.glColor3f(1, 1, 0);
 * gl.glBegin(GL.GL_LINES);
 * gl.glVertex2f(getStartPoint().x, getStartPoint().y);
 * gl.glVertex2f(getEndPoint().x, getEndPoint().y);
 * gl.glEnd();
 *
 * String s = String.format("Speed: %s pps (%.0fpix/%ss), dx/dy=%d/%d", engFmt.format(speedPps), distance, engFmt.format(1e-6f * ltaTimestamp), (int) Math.abs(endPoint.x - startPoint.x), (int) Math.abs(endPoint.y - startPoint.y));
 * drawString(drawable, s);
 * gl.glPopMatrix(); // must push pop since drawCursor translates?
 * }
 * else if (getStartPoint() != null)
 * {
 * String s = String.format("Left click for end point. Time from IN mark: %ss", engFmt.format(1e-6f * (currentTimestamp - tStartPoint().t)));
 * gl.glPushMatrix();
 * drawString(drawable, s);
 * gl.glPopMatrix(); // must push pop since drawCursor translates?
 *
 * }
 * else
 * {
 * String s = "Left click for start point";
 * gl.glPushMatrix();
 * drawString(drawable, s);
 * gl.glPopMatrix(); // must push pop since drawCursor translates?
 * }
 * }
 *
 * private void drawString(GLAutoDrawable drawable, String s) throws GLException
 * {
 * DrawGL.drawString(drawable, 24, 0.5f, .2f, .5f, Color.yellow, s);
 * }
 *
 * private void drawCursor(GL2 gl, Point p, float[] color)
 * {
 * if (p == null)
 * {
 * return;
 * }
 * gl.glPushMatrix();
 * gl.glColor4fv(color, 0);
 * gl.glLineWidth(3f);
 * gl.glTranslatef(p.x, p.y, 0);
 * gl.glBegin(GL2.GL_LINES);
 * gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
 * gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
 * gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
 * gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
 * gl.glEnd();
 * gl.glTranslatef(.5f, -.5f, 0);
 * gl.glColor4f(0, 0, 0, 1);
 * gl.glBegin(GL2.GL_LINES);
 * gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
 * gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
 * gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
 * gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
 * gl.glEnd();
 * gl.glPopMatrix();
 * }
 * </pre>
 *
 * @author Tobi
 * @see ch.unizh.ini.jaer.projects.minliu.Speedometer
 */
abstract public class EventFilter2DMouseAdaptor extends EventFilter2D implements MouseListener, MouseMotionListener, MouseWheelListener, FrameAnnotater {

    protected GLCanvas glCanvas;
    protected ChipCanvas chipCanvas;
    float[] cursorColor = null;

    /**
     * Cursor size for drawn mouse cursor when filter is selected.
     */
    protected final float CURSOR_SIZE_CHIP_PIXELS = 7;
    protected GLU glu = new GLU();
    protected GLUquadric quad = null;
    private boolean hasBlendChecked = false, hasBlend = false;
    protected boolean showCrossHairCursor = true;

    public EventFilter2DMouseAdaptor(AEChip chip) {
        super(chip);
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
    }

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
        if (isSelected() && showCrossHairCursor) {
            Point p = chipCanvas.getMousePixel();
            if (p == null) {
                return;
            }
//            checkBlend(gl);
            gl.glPushMatrix();
            if (cursorColor != null && cursorColor.length == 4) {
                gl.glColor4fv(cursorColor, 0);
            } else {
                gl.glColor4f(1f, 1f, 1f, 1);
            }
            gl.glLineWidth(3f);
            gl.glTranslatef(p.x, p.y, 0);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glEnd();
            gl.glTranslatef(.5f, -.5f, 0);
            gl.glColor4f(0, 0, 0, 1);
            gl.glBegin(GL2.GL_LINES);
            gl.glVertex2f(0, -CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(0, +CURSOR_SIZE_CHIP_PIXELS / 2);
            gl.glVertex2f(-CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glVertex2f(+CURSOR_SIZE_CHIP_PIXELS / 2, 0);
            gl.glEnd();
//            if (quad == null) {
//                quad = glu.gluNewQuadric();
//            }
//            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
//            glu.gluDisk(quad, 0, 3, 32, 1);
            gl.glPopMatrix();
        }

        chip.getCanvas().checkGLError(gl, glu, "in annotate");

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
        addRemoveMouseListeners(yes);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        addRemoveMouseListeners(yes);
    }

    private void addRemoveMouseListeners(boolean yes) {
        if (glCanvas == null) {
            return;
        }
        if (yes) {
            MouseWheelListener[] listeners=glCanvas.getMouseWheelListeners();
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.removeMouseWheelListener(this);
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);
//            glCanvas.addMouseWheelListener(this);  // tobi this seems somehow to remove effect of mouse wheel in the main AEViewer ImagePanel

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
//            glCanvas.removeMouseWheelListener(this);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    /**
     * Returns the chip pixel position from the MouseEvent. Note that any calls
     * that modify the GL model matrix (or viewport, etc) will make the location
     * meaningless. Make sure that your graphics rendering code wraps transforms
     * inside pushMatrix and popMatrix calls.
     *
     * @param e the mouse event
     * @return the pixel position in the chip object, origin 0,0 in lower left
     * corner. Or null if outside chip bounds.
     */
    protected Point getMousePixel(MouseEvent e) {
        if (getChip().getCanvas() == null) {
            return null;
        }
        Point p = getChip().getCanvas().getPixelFromMouseEvent(e);
        if (getChip().getCanvas().wasMousePixelInsideChipBounds()) {
            return p;
        } else {
            return null;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        getChip().getCanvas().repaint(100);
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
     * By default a cross hair selection cursor is drawn. This method prevent
     * drawing the cross hair.
     *
     * @param showCrossHairCursor the showCrossHairCursor to set
     */
    protected void setShowCrossHairCursor(boolean showCrossHairCursor) {
        this.showCrossHairCursor = showCrossHairCursor;
    }

}
