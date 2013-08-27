/*
 * ChipRendererDisplayMethod.java
 *
 * Created on May 4, 2006, 9:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.graphics;

import java.nio.FloatBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2ES1;
import javax.media.opengl.GLAutoDrawable;

/**
 * Renders using OpenGL the RGB histogram values from Chip2DRenderer.

 * @author Christian Brandli
 * @see net.sf.jaer.graphics.Chip2DRenderer
 * @see net.sf.jaer.graphics.AEChipRenderer
 */
public class ChipRendererDisplayMethodRGBA extends DisplayMethod implements DisplayMethod2D {


    public final float SPECIAL_BAR_LOCATION_X=-5;
    public final float SPECIAL_BAR_LOCATION_Y=0;
    public final float SPECIAL_BAR_LINE_WIDTH=8;


    /**
     * Creates a new instance of ChipRendererDisplayMethod
     */
    public ChipRendererDisplayMethodRGBA(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    /** called by ChipCanvas.display(GLAutoDrawable) to draw the RGB fr histogram values. The GL context is assumed to already be
    transformed so that chip pixel x,y values can be used for coordinate values, with 0,0 at LL corner.
     */
    @Override
    public void display(GLAutoDrawable drawable) {
//        displayPixmap(drawable);
        displayQuad(drawable);
    }

    private float clearDisplay(Chip2DRenderer renderer, GL2 gl) {
        float gray = renderer.getGrayValue();
        gl.glClearColor(gray, gray, gray, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        return gray;
    }

    private void displayQuad(GLAutoDrawable drawable){
        Chip2DRenderer renderer = getChipCanvas().getRenderer();
        FloatBuffer pixmap = renderer.getPixmap();
        FloatBuffer onMap = null;
        FloatBuffer offMap = null;
        boolean displayEvents = false;
        boolean displayFrames = true;
        if(renderer instanceof AEFrameChipRenderer){
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            onMap = frameRenderer.getOnMap();
            offMap = frameRenderer.getOffMap();
            displayFrames = frameRenderer.isDisplayFrames();
            displayEvents = frameRenderer.isDisplayEvents();
        }
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        if((width == 0) || (height == 0)) {
			return;
		}

        GL2 gl = drawable.getGL().getGL2();
        clearDisplay(renderer, gl);

        getChipCanvas().checkGLError(gl, glu, "before quad");

        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBlendFunc (GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable (GL.GL_BLEND);

        final int nearestFilter=GL.GL_NEAREST; // tobi changed to GL_NEAREST so that pixels are not intepolated but rather are rendered exactly as they come from data not matter what zoom.
        final int linearFilter=GL.GL_LINEAR; // tobi changed to GL_NEAREST so that pixels are not intepolated but rather are rendered exactly as they come from data not matter what zoom.
        if(displayFrames){
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, linearFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, linearFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL.GL_REPLACE);
            gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA,
                            GL.GL_FLOAT, pixmap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture (GL.GL_TEXTURE_2D, 0);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
        }

        if((onMap != null) && displayEvents){
            gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL.GL_REPLACE);
            gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA,
                            GL.GL_FLOAT, onMap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture (GL.GL_TEXTURE_2D, 1);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
        }

        if(offMap != null){
            gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL.GL_REPLACE);
            gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA,
                            GL.GL_FLOAT, offMap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture (GL.GL_TEXTURE_2D, 1);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
        }

        gl.glDisable (GL.GL_BLEND);

        getChipCanvas().checkGLError(gl, glu, "after rendering histogram quad");

        // outline frame
        gl.glColor3f(0, 0, 1f);
        gl.glLineWidth(1f);
        {
            gl.glBegin(GL.GL_LINE_LOOP);
            final float o = 0f;
            final float w = chip.getSizeX();
            final float h = chip.getSizeY();
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

    private void drawPolygon(GL2 gl, int width, int height){
        double xRatio = (double)chip.getSizeX()/(double)width;
        double yRatio = (double)chip.getSizeY()/(double)height;
        gl.glBegin (GL2.GL_POLYGON);

        gl.glTexCoord2d (0, 0);
        gl.glVertex2d (0,0);
        gl.glTexCoord2d(xRatio, 0);
        gl.glVertex2d (xRatio*width, 0);
        gl.glTexCoord2d(xRatio, yRatio);
        gl.glVertex2d (xRatio*width, yRatio*height);
        gl.glTexCoord2d(0, yRatio);
        gl.glVertex2d (0, yRatio*height);

        gl.glEnd ();
    }
}
