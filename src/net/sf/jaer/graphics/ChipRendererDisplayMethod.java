/*
 * ChipRendererDisplayMethod.java
 *
 * Created on May 4, 2006, 9:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * Copyright May 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.graphics;

import java.nio.FloatBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

/**
 * Renders using OpenGL the RGB histogram values from Chip2DRenderer.
 *
 * @author tobi
 * @see net.sf.jaer.graphics.Chip2DRenderer
 * @see net.sf.jaer.graphics.AEChipRenderer
 */
public class ChipRendererDisplayMethod extends DisplayMethod implements DisplayMethod2D {

    public final float SPECIAL_BAR_LOCATION_X = -5;
    public final float SPECIAL_BAR_LOCATION_Y = 0;
    public final float SPECIAL_BAR_LINE_WIDTH = 8;

    /**
     * Creates a new instance of ChipRendererDisplayMethod
     */
    public ChipRendererDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    /**
     * called by ChipCanvas.display(GLAutoDrawable) to draw the RGB fr histogram
     * values. The GL context is assumed to already be transformed so that chip
     * pixel x,y values can be used for coordinate values, with 0,0 at LL
     * corner.
     */
    @Override
    public void display(GLAutoDrawable drawable) {
        displayPixmap(drawable);
        displayStatusChangeText(drawable);
    }

    private static float clearDisplay(Chip2DRenderer renderer, GL2 gl) {
        float gray = renderer.getGrayValue();
        gl.glClearColor(gray, gray, gray, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        return gray;
    }

    private void displayPixmap(GLAutoDrawable drawable) {
        Chip2DRenderer renderer = getChipCanvas().getRenderer();
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        clearDisplay(renderer, gl);
        final int ncol = chip.getSizeX();
        final int nrow = chip.getSizeY();
        // final int n = 3 * nrow * ncol;
        getChipCanvas().checkGLError(gl, glu, "before pixmap");
        // Zoom zoom = chip.getCanvas().getZoom();
        if (!getChipCanvas().getZoom().isZoomed()) {
            final int wi = drawable.getSurfaceWidth(), hi = drawable.getSurfaceHeight();
            float scale = 1;
            // float ar=(float)hi/wi;
            final float border = chip.getCanvas().getBorderSpacePixels();
            if (chip.getCanvas().isFillsVertically()) {// tall chip, use chip height
                scale = (hi - (2 * border)) / (chip.getSizeY());
            } else if (chip.getCanvas().isFillsHorizontally()) {
                scale = (wi - (2 * border)) / (chip.getSizeX());
            }

            gl.glPixelZoom(scale, scale);
            gl.glRasterPos2f(-.5f, -.5f); // to LL corner of chip, but must be inside viewport or else it is ignored,
            // breaks on zoom if (zoom.isZoomEnabled() == false) {

            // gl.glMinmax(GL.GL_MINMAX,GL.GL_RGB,false);
            // gl.glEnable(GL.GL_MINMAX);
            // chipCanvas.checkGLError(gl, glu, "after minmax");
            {
                try {
                    synchronized (renderer) {
                        FloatBuffer pixmap = renderer.getPixmap();
                        if (pixmap != null) {
                            pixmap.position(0);
                            // gl.glPixelTransferf(GL.GL_RED_SCALE, 2); // TODO to try out
                            // gl.glPixelTransferf(GL.GL_RED_BIAS, .3f); // TODO to try out
                            gl.glDrawPixels(ncol, nrow, GL.GL_RGB, GL.GL_FLOAT, pixmap);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    log.warning(e.toString());
                }
            }
        } else { // zoomed in, easiest to drawRect the pixels
            // float scale = zoom.zoomFactor * chip.getCanvas().getScale();
            float[] f = renderer.getPixmapArray();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            float gray = renderer.getGrayValue();
            int ind = 0;
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    if ((f[ind] != gray) || (f[ind + 1] != gray) || (f[ind + 2] != gray)) {
                        gl.glColor3fv(f, ind);
                        gl.glRectf(x - .5f, y - .5f, x + .5f, y + .5f);
                    }
                    ind += 3;
                }
            }
        }
        // FloatBuffer minMax=FloatBuffer.allocate(6);
        // gl.glGetMinmax(GL.GL_MINMAX, true, GL.GL_RGB, GL.GL_FLOAT, minMax);
        // gl.glDisable(GL.GL_MINMAX);
        getChipCanvas().checkGLError(gl, glu, "after rendering histogram rectangles");
        // outline frame
        gl.glColor3f(0, 0, 1f);
        gl.glLineWidth(1f);
        {
            gl.glBegin(GL.GL_LINE_LOOP);
            final float o = .5f;
            final float w = chip.getSizeX() - 1;
            final float h = chip.getSizeY() - 1;
            gl.glVertex2f(-o, -o);
            gl.glVertex2f(w + o, -o);
            gl.glVertex2f(w + o, h + o);
            gl.glVertex2f(-o, h + o);
            gl.glEnd();
        }
        getChipCanvas().checkGLError(gl, glu, "after rendering frame of chip");

        if (renderer instanceof AEChipRenderer) {
            AEChipRenderer r = (AEChipRenderer) renderer;
            int n = r.getSpecialCount();
            if (n > 0) {
                gl.glColor3f(1, 1, 1);
                gl.glLineWidth(SPECIAL_BAR_LINE_WIDTH);
                gl.glBegin(GL.GL_LINE_STRIP);
                gl.glVertex2f(SPECIAL_BAR_LOCATION_X, SPECIAL_BAR_LOCATION_Y);
                gl.glVertex2f(SPECIAL_BAR_LOCATION_X, SPECIAL_BAR_LOCATION_Y + n);
                gl.glEnd();
                getChipCanvas().checkGLError(gl, glu, "after rendering special events");
            }
        }
    }
}
