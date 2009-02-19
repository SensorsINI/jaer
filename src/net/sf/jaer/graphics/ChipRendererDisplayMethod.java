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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * Renders using OpenGL the RGB histogram values from Chip2DRenderer. 

 * @author tobi
 * @see net.sf.jaer.graphics.Chip2DRenderer
 * @see net.sf.jaer.graphics.AEChipRenderer
 */
public class ChipRendererDisplayMethod extends DisplayMethod implements DisplayMethod2D {

    /**
     * Creates a new instance of ChipRendererDisplayMethod
     */
    public ChipRendererDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    /** called by ChipCanvas.display(GLAutoDrawable) to draw the RGB fr histogram values. The GL context is assumed to already be
    transformed so that chip pixel x,y values can be used for coordinate values, with 0,0 at LL corner.
     */
    public void display(GLAutoDrawable drawable) {
        displayPixmap(drawable);
    }

    private float clearDisplay(Chip2DRenderer renderer, GL gl) {
        float gray = renderer.getGrayValue();
        gl.glClearColor(gray, gray, gray, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

        return gray;
    }

    private boolean isValidRasterPosition(GL gl) {
        boolean validRaster;
        ByteBuffer buf = ByteBuffer.allocate(1);
        gl.glGetBooleanv(GL.GL_CURRENT_RASTER_POSITION_VALID, buf);
        buf.rewind();
        byte b = buf.get();
        validRaster = b != 0;
        return validRaster;
    }

    private void displayPixmap(GLAutoDrawable drawable) {
        Chip2DRenderer renderer = chipCanvas.getRenderer();
        GL gl=drawable.getGL();
        if(gl==null) return;

        clearDisplay(renderer, gl);
        final int ncol = chip.getSizeX();
        final int nrow = chip.getSizeY();
//        final int n = 3 * nrow * ncol;
        chipCanvas.checkGLError(gl, glu, "before pixmap");
//        Zoom zoom = chip.getCanvas().getZoom();
        if (!zoom.isZoomEnabled()) {
            final int wi = drawable.getWidth(),  hi = drawable.getHeight();
            float scale = 1;
//            float ar=(float)hi/wi;
            final float border=chip.getCanvas().getBorderSpacePixels();
            if (chip.getCanvas().isFillsVertically()) {// tall chip, use chip height
                scale = ((float) hi - 2 * border) / (chip.getSizeY());
            } else if (chip.getCanvas().isFillsHorizontally()) {
                scale = ((float) wi - 2 * border) / (chip.getSizeX() );
            }

            gl.glPixelZoom(scale, scale);
            gl.glRasterPos2f(-.5f, -.5f); // to LL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {

//        gl.glMinmax(GL.GL_MINMAX,GL.GL_RGB,false);
//        gl.glEnable(GL.GL_MINMAX);
//            chipCanvas.checkGLError(gl, glu, "after minmax");
            {
                try {
                    synchronized (renderer) {
                        FloatBuffer pixmap = renderer.getPixmap();
                        if (pixmap != null) {
                            pixmap.position(0);
                            gl.glDrawPixels(ncol, nrow, GL.GL_RGB, GL.GL_FLOAT, pixmap);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    log.warning(e.toString());
                }
            }
        } else { // zoomed in, easiest to drawRect the pixels
//            float scale = zoom.zoomFactor * chip.getCanvas().getScale();
            float[] f=renderer.getPixmapArray();
            int sx=chip.getSizeX(), sy=chip.getSizeY();
            float gray=renderer.getGrayValue();
            int ind=0;
            for(int y=0;y<sy;y++){
                for(int x=0;x<sx;x++){
                    if(f[ind]!=gray || f[ind+1]!=gray || f[ind+2]!=gray) {
                        gl.glColor3fv(f,ind);
                        gl.glRectf(x-.5f, y-.5f, x+.5f, y+.5f);
                    }
                    ind+=3;
                }
            }
        }
//        FloatBuffer minMax=FloatBuffer.allocate(6);
//        gl.glGetMinmax(GL.GL_MINMAX, true, GL.GL_RGB, GL.GL_FLOAT, minMax);
//        gl.glDisable(GL.GL_MINMAX);
        chipCanvas.checkGLError(gl, glu, "after rendering histogram rectangles");
        // outline frame
        gl.glColor4f(0, 0, 1f, 0f);
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
        chipCanvas.checkGLError(gl, glu, "after rendering frame of chip");
    }
}
