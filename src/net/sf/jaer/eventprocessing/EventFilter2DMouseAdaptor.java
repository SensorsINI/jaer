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
 * integrate mouse events into their functionality
 *
 * @author Tobi
 */
abstract public class EventFilter2DMouseAdaptor extends EventFilter2D implements MouseListener, MouseMotionListener, MouseWheelListener, FrameAnnotater {

    protected GLCanvas glCanvas;
    protected ChipCanvas chipCanvas;
    /**
     * Cursor size for drawn mouse cursor when filter is selected.
     */
    protected final float CURSOR_SIZE_CHIP_PIXELS = 7;
    protected GLU glu = new GLU();
    protected GLUquadric quad = null;
    private boolean hasBlendChecked = false, hasBlend = false;

    public EventFilter2DMouseAdaptor(AEChip chip) {
        super(chip);
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
    }
    float[] cursorColor = null;

    /**
     * Sets a cursor color vector 
     * @param color a 4-vector. Set it to null to return to default white shadowed cursor.
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
        if (isSelected()) {
            Point mp = glCanvas.getMousePosition();
            Point p = chipCanvas.getPixelFromPoint(mp);
            if (p == null) {
                return;
            }
            checkBlend(gl);
            if (cursorColor != null && cursorColor.length == 4) {
                gl.glColor4fv(cursorColor, 0);
            } else {
                gl.glColor4f(1f, 1f, 1f, 1);
            }
            gl.glLineWidth(3f);
            gl.glPushMatrix();
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

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        chip.getCanvas().repaint(100);
    }

    /**
     * Handles wheel event. Empty by default
     *
     * @param mwe the mouse wheel roll event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {

    }

}
