/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import com.sun.opengl.util.GLUT;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;

/**
 * A specialized AE chip rendering method that handles chips with intensity output.
 *
 * @author tobi
 */
public class DVSWithIntensityDisplayMethod extends ChipRendererDisplayMethod {

    private HasIntensity hasIntensity = null;

    public DVSWithIntensityDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    /** Sets the source of the "intensity" value.
     *
     * @param hasIntensity
     */
    public void setIntensitySource(HasIntensity hasIntensity) {
        this.hasIntensity = hasIntensity;
    }

    /** Displays intensity along with normal 2d histograms.
     *
     * @param drawable
     */
    @Override
    public void display(GLAutoDrawable drawable) {
        super.display(drawable);
        if (hasIntensity != null) {
            GL gl = drawable.getGL();
            gl.glLineWidth(10);
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(-1, 0);
            float f = hasIntensity.getIntensity();
            f = f * getChipCanvas().getChip().getSizeY();
            gl.glVertex2f(-1, f);
            gl.glEnd();
            {
                gl.glPushMatrix();
                gl.glRotatef(90,0,0,1);
                gl.glScalef(.1f, .1f, .1f);
                gl.glLineWidth(.1f);
                gl.glTranslatef(0, 50, 0);
                glut.glutStrokeString(GLUT.STROKE_ROMAN, "Intensity");
                gl.glPopMatrix();
            }

        }
    }
}
