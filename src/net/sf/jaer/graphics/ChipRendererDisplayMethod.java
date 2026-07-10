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

import java.nio.ByteBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
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

    private int histogramTextureId;
    private byte[] textureUploadScratch;

    /**
     * Creates a new instance of ChipRendererDisplayMethod
     */
    public ChipRendererDisplayMethod(ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    @Override
    public void onDeregistration() {
        histogramTextureId = 0;
        textureUploadScratch = null;
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
        if (ncol <= 0 || nrow <= 0) {
            return;
        }
        renderer.ensurePixmapReadyForDisplay();
        final int requiredFloats = ncol * nrow * 3;
        getChipCanvas().checkGLError(gl, glu, "before pixmap");
        if (!getChipCanvas().getZoom().isZoomed()) {
            synchronized (renderer) {
                float[] rgb = renderer.getPixmapArray();
                if (rgb != null && rgb.length >= requiredFloats) {
                    drawHistogramTexture(gl, rgb, ncol, nrow);
                }
            }
        } else {
            float[] f = renderer.getPixmapArray();
            if (f == null || f.length < requiredFloats) {
                return;
            }
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
        getChipCanvas().checkGLError(gl, glu, "after rendering histogram rectangles");
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

    /**
     * Uploads the RGB float pixmap as an RGBA byte texture and draws a chip-sized
     * quad. {@link GL2#glDrawPixels} is unreliable on HiDPI Windows; byte textures
     * match the path used by {@link ChipRendererDisplayMethodRGBA}.
     */
    private void drawHistogramTexture(GL2 gl, float[] rgb, int width, int height) {
        final int pixelCount = width * height;
        final int byteLen = pixelCount * 4;
        if (textureUploadScratch == null || textureUploadScratch.length < byteLen) {
            textureUploadScratch = new byte[byteLen];
        }
        int di = 0;
        final int rgbEnd = pixelCount * 3;
        for (int si = 0; si < rgbEnd; si += 3) {
            textureUploadScratch[di++] = floatToByte(rgb[si]);
            textureUploadScratch[di++] = floatToByte(rgb[si + 1]);
            textureUploadScratch[di++] = floatToByte(rgb[si + 2]);
            textureUploadScratch[di++] = (byte) 0xFF;
        }

        if (histogramTextureId == 0) {
            final int[] ids = new int[1];
            gl.glGenTextures(1, ids, 0);
            histogramTextureId = ids[0];
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, histogramTextureId);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_REPLACE);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0,
                GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(textureUploadScratch, 0, byteLen));

        gl.glColor4f(1f, 1f, 1f, 1f);
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glBegin(GL2.GL_POLYGON);
        gl.glTexCoord2f(0f, 0f);
        gl.glVertex2f(0f, 0f);
        gl.glTexCoord2f(1f, 0f);
        gl.glVertex2f(width, 0f);
        gl.glTexCoord2f(1f, 1f);
        gl.glVertex2f(width, height);
        gl.glTexCoord2f(0f, 1f);
        gl.glVertex2f(0f, height);
        gl.glEnd();
        gl.glDisable(GL.GL_TEXTURE_2D);
    }

    private static byte floatToByte(float v) {
        if (v <= 0f) {
            return 0;
        }
        if (v >= 1f) {
            return (byte) 0xFF;
        }
        return (byte) (v * 255f + 0.5f);
    }
}
