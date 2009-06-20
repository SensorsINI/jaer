/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

//import ch.unizh.ini.caviar.aemonitor.EventXYType;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.*;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.*;

/**
 * Shows events from stereo cochlea as a rastergram. Time increases to the right and covers one time slice of events as passed to the rendering.
 * Channel increases upwards. 
 * 
 * @author tobi
 */
public class CochleaGramDisplayMethod extends DisplayMethod implements DisplayMethod2D {

    /**
     * Creates a new instance of CochleaGramDisplayMethod
     *
     * @param c the canvas we are drawing on
     */
    public CochleaGramDisplayMethod(ChipCanvas c) {
        super(c);
    }
    final float rasterWidth = 0.003f; // width of screen;
    final int BORDER = 50; // pixels

    /** displays individual events as cochleagram
     * @param drawable the drawable passed in by OpenGL
     */
    public void display(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        gl.glOrtho(-BORDER, drawable.getWidth() + BORDER, -BORDER, drawable.getHeight() + BORDER, 10000, -10000);
        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();
        // translate origin to this point
        gl.glTranslatef(0, 0, 0);
        // scale everything by rastergram scale
        float ys = (drawable.getHeight()) / (float) chip.getSizeX();// scale vertical is draableHeight/numPixels
        float xs = (drawable.getWidth()); // scale horizontal is draw
        gl.glScalef(xs, ys, 1);
        // make sure we're drawing back buffer (this is probably true anyhow)
//        gl.glDrawBuffer(GL.GL_BACK);

        // draw axes
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(1f);
        int len = chip.getSizeX() - 3;
        gl.glBegin(GL.GL_LINES);
        {
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, len, 0); // taps axis
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(1, 0, 0); // time axis
        }
        gl.glEnd();
        // draw axes labels x,y,t. See tutorial at http://jerome.jouvie.free.fr/OpenGl/Tutorials/Tutorial18.php
        int font = GLUT.BITMAP_HELVETICA_18;
        gl.glPushMatrix();
        {
            final int FS = 1; // distance in pixels of text from endZoom of axis
            gl.glRasterPos3f(0, chip.getSizeX(), 0);
            glut.glutBitmapString(font, "Channel");
            gl.glRasterPos3f(1, 0, 0);
            glut.glutBitmapString(font, "Time");
        }
        gl.glPopMatrix();

        // render events

        EventPacket ae = (EventPacket) chip.getLastData();
        if (ae == null || ae.isEmpty()) {
            return;
        }
        int n = ae.getSize();
        int t0 = ae.getFirstTimestamp();
        int dt = ae.getLastTimestamp() - t0 + 1;
        float z;
        float[][] typeColors = ((AEChipRenderer) chip.getRenderer()).getTypeColorRGBComponents();
        try {
            for (Object o : ae) {
                TypedEvent ev = (TypedEvent) o;
                gl.glColor3fv(typeColors[ev.type], 0);// FIXME depends on these colors having been created by a rendering cycle...
//            CochleaGramDisplayMethod.typeColor(gl, ev.type);
//            if(ev.type==0) gl.glColor4f(1,0,0,alpha); else gl.glColor4f(0,1,0,alpha); // red right
                z = (float) (ev.timestamp - t0) / dt; // z goes from 0 (oldest) to 1 (youngest)
                gl.glRectf(z, ev.x - 1, z + rasterWidth, ev.x + 1); // taps increse upwards
            }
        } catch (ClassCastException e) {
            log.warning("while rendering events caught " + e + ", some filter is casting events to BasicEvent?");
        }

        chipCanvas.checkGLError(gl, glu, "after CochleaGramDisplayMethod");

    }

    /** Sets the gl color depending on cochlea cell type
    @param gl the GL context
    @param type the cell type 0-3
     */
    public static void typeColor(GL gl, int type) {
        final float alpha = 0.5f;
        switch (type) {
            case 0:
                gl.glColor4f(1, 0, 0, alpha);
                break;
            case 2:
                gl.glColor4f(0, 1, 0, alpha);
                break;
            case 1:
                gl.glColor4f(1, 1, 0, alpha);
                break;
            case 3:
                gl.glColor4f(0, 0, 1, alpha);
            default:
                gl.glColor4f(.5f, .5f, .5f, alpha);
        }
    }
}
