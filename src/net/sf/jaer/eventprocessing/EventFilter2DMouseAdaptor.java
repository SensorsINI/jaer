/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.awt.Point;
import java.awt.event.*;
import javax.media.opengl.*;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.glu.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *  Adds a mouse adaptor to the basic EventFilter2D to let subclasses more easily integrate mouse events into their functionality
 * @author Tobi
 */
abstract public class EventFilter2DMouseAdaptor extends EventFilter2D implements MouseListener, MouseMotionListener, FrameAnnotater {

    private GLCanvas glCanvas;
    private ChipCanvas canvas;

    public EventFilter2DMouseAdaptor(AEChip chip) {
        super(chip);
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
    }
    protected GLU glu = new GLU();
    protected GLUquadric quad = null;
    private boolean hasBlendChecked = false, hasBlend = false;

    /** Annotates the display with the current mouse position to indicate that mouse is being used.
     * Subclasses can override this functionality.
     * 
     * @param drawable 
     */
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        canvas = chip.getCanvas();
        if(canvas==null) return;
        glCanvas = (GLCanvas) canvas.getCanvas();
        if(glCanvas==null) return;
        if (isSelected()) {
            Point mp = glCanvas.getMousePosition();
            Point p = canvas.getPixelFromPoint(mp);
            if (p == null) {
                return;
            }
            if (!hasBlendChecked) {
                hasBlendChecked = true;
                String glExt = gl.glGetString(GL.GL_EXTENSIONS);
                if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                    hasBlend = true;
                }
            }
            if (hasBlend) {
                try {
                    gl.glEnable(GL.GL_BLEND);
                    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                    gl.glBlendEquation(GL.GL_FUNC_ADD);
                } catch (GLException e) {
                    e.printStackTrace();
                    hasBlend = false;
                }
            }
            gl.glColor4f(.25f, .25f, 0, .3f);
            gl.glPushMatrix();
            gl.glTranslatef(p.x, p.y, 0);
            if (quad == null) {
                quad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            glu.gluDisk(quad, 0, 3, 32, 1);
            gl.glPopMatrix();
        }

        chip.getCanvas().checkGLError(gl, glu, "in annotate");

    }

    /** When this is selected in the FilterPanel GUI, the mouse listeners will be added.
     * When this is unselected, the listeners will be removed.
     * 
     */
    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes);
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

    /** Returns the chip pixel position from the MouseEvent.
     * 
     * @param e the mouse event
     * @return the pixel position in the chip object, origin 0,0 in lower left corner.
     */
    protected Point getMousePixel(MouseEvent e) {
        if (canvas == null) {
            return null;
        }
        Point p = canvas.getPixelFromMouseEvent(e);
        if (canvas.wasMousePixelInsideChipBounds()) {
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
    }
}
