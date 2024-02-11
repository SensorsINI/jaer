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

import java.awt.Font;
import java.awt.geom.Point2D;
import java.nio.FloatBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.util.awt.TextRenderer;
//import com.jogamp.opengl.util.awt.TextRenderer;

import net.sf.jaer.util.TextRendererScale;

/**
 * Displays using OpenGL the RGB histogram values from Chip2DRenderer. The DVS
 * cameras use this class directly, while DAVIS cameras subclass it to add frame
 * and other (e.g. IMU) rendering)
 *
 * @author Christian Brandli
 * @see net.sf.jaer.graphics.Chip2DRenderer
 * @see net.sf.jaer.graphics.AEChipRenderer
 */
public class ChipRendererDisplayMethodRGBA extends DisplayMethod implements DisplayMethod2D {

    private TextRenderer textRenderer = null;
    public final float SPECIAL_BAR_LOCATION_X = -5;
    public final float SPECIAL_BAR_LOCATION_Y = 0;
    public final float SPECIAL_BAR_LINE_WIDTH = 8;
    private boolean renderSpecialEvents = true;

    /**
     * Creates a new instance of ChipRendererDisplayMethodRGBA
     *
     * @param chipCanvas
     */
    public ChipRendererDisplayMethodRGBA(final ChipCanvas chipCanvas) {
        super(chipCanvas);
    }

    /**
     * called by ChipCanvas.display(GLAutoDrawable) to draw the RGB fr histogram
     * values. The GL context is assumed to already be transformed so that chip
     * pixel x,y values can be used for coordinate values, with 0,0 at LL
     * corner.
     * @param drawable
     */
    @Override
    public void display(final GLAutoDrawable drawable) {
        displayQuad(drawable);
        displayStatusChangeText(drawable);
    }

    private static float clearDisplay(final Chip2DRenderer renderer, final GL gl) {
        final float gray = renderer.getGrayValue();
        gl.glClearColor(gray, gray, gray, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        return gray;
    }

    private void displayQuad(final GLAutoDrawable drawable) {
        final Chip2DRenderer renderer = getChipCanvas().getRenderer();
        final FloatBuffer pixmap = renderer.getPixmap();
        FloatBuffer dvsEventsMap = null;
//		FloatBuffer offMap = null;
        FloatBuffer annotateMap = null;
        boolean displayEvents = false;
        boolean displayFrames = true;
        boolean displayAnnotation = false;

        if (renderer instanceof DavisRenderer) {
            final DavisRenderer frameRenderer = (DavisRenderer) renderer;
            dvsEventsMap = frameRenderer.getDvsEventsMap();
//			offMap = frameRenderer.getOffMap();
            annotateMap = frameRenderer.getAnnotateMap();
            displayFrames = frameRenderer.isDisplayFrames();
            displayEvents = frameRenderer.isDisplayEvents();
            displayAnnotation = frameRenderer.isDisplayAnnotation();
        }

        final int width = renderer.getWidth();
        final int height = renderer.getHeight();
        if ((width == 0) || (height == 0)) {
            return;
        }

        final GL2 gl = drawable.getGL().getGL2();
        ChipRendererDisplayMethodRGBA.clearDisplay(renderer, gl);

        getChipCanvas().checkGLError(gl, glu, "before quad");

        gl.glDisable(GL.GL_DEPTH_TEST);
        final int nearestFilter = GL.GL_NEAREST;
        // Tobi: changed to GL_NEAREST so that pixels are not interpolated but
        // rather are rendered exactly as they come from data no matter
        // what zoom.

//        gl.glEnable(GL.GL_BLEND);
        if (pixmap != null && displayFrames) {
            gl.glPushMatrix();
            if (imageTransform != null) {
                final int sx = chip.getSizeX() / 2, sy = chip.getSizeY() / 2;
                gl.glTranslatef(sx, sy, 0);
                gl.glRotatef((float) ((imageTransform.rotationRad * 180) / Math.PI), 0, 0, 1);
                gl.glTranslatef(imageTransform.translationPixels.x - sx, imageTransform.translationPixels.y - sy, 0);
            }
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_REPLACE);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA, GL.GL_FLOAT, pixmap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glPopMatrix();
            getChipCanvas().checkGLError(gl, glu, "after frames");
        }

        if ((dvsEventsMap != null) && displayEvents) {
            // DVS event histograms are written with Alpha=0 when the frame is cleared, same for annotation maps. When events occur, they replace the APS values
            gl.glPushMatrix();
            gl.glAlphaFunc(GL2.GL_GREATER, 0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_REPLACE);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA, GL.GL_FLOAT, dvsEventsMap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glEnable(GL2.GL_ALPHA_TEST);
            //  gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glDisable(GL2.GL_ALPHA_TEST);
            gl.glPopMatrix();

            getChipCanvas().checkGLError(gl, glu, "after dvs events");

        }

//                // see // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/xhtml/glTexEnv.xml for API
//                // see https://www.khronos.org/opengl/wiki/Texture_Combiners for combining textures
//                // we want to simply add RGB from ON and OFF events so that for example ON and OFF produces yellow from green and red
//		if ((offMap != null) && displayEvents) {
//			gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
//			gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
//			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
//			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
//			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
//			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
//			// rgb
//                        gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE); 
//                        gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD); 
//			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE0_RGB, GL2.GL_PREVIOUS);
//			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE1_RGB, GL2.GL_TEXTURE);
//			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
//			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND1_RGB, GL2.GL_SRC_COLOR);
//                        // alpha
////                        gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_ADD); 
////			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE0_ALPHA, GL2.GL_PREVIOUS);
////			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE1_ALPHA, GL2.GL_TEXTURE);
////			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND0_ALPHA, GL2.GL_SRC_ALPHA);
////			gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND1_ALPHA, GL2.GL_SRC_ALPHA);
//                        
//                        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA, GL.GL_FLOAT, offMap);
//
//			gl.glEnable(GL.GL_TEXTURE_2D);
//			gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
//			drawPolygon(gl, width, height);
//			gl.glDisable(GL.GL_TEXTURE_2D);
//		}
        if (annotateMap != null && displayAnnotation) {
            gl.glEnable(GL2.GL_ALPHA_TEST); // only draw annotation when alpha>0
            gl.glAlphaFunc(GL2.GL_GREATER, 0f);
//            gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
//            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
//            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
//            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
//            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
//            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL2ES1.GL_ADD);
//            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA, GL.GL_FLOAT, annotateMap);

            gl.glBindTexture(GL.GL_TEXTURE_2D, 2);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            // rgb
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD_SIGNED);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE0_RGB, GL2.GL_PREVIOUS);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE1_RGB, GL2.GL_TEXTURE);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND1_RGB, GL2.GL_SRC_COLOR);
            // alpha
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_ADD); 
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE0_ALPHA, GL2.GL_PREVIOUS);
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_SOURCE1_ALPHA, GL2.GL_TEXTURE);
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND0_ALPHA, GL2.GL_SRC_ALPHA);
//            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2.GL_OPERAND1_ALPHA, GL2.GL_SRC_ALPHA);

            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA, GL.GL_FLOAT, annotateMap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            //gl.glBindTexture(GL.GL_TEXTURE_2D, 2);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glDisable(GL2.GL_ALPHA_TEST);
        }

        gl.glDisable(GL.GL_BLEND);

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

        // show special event count on left of array as white bar
        if (renderSpecialEvents && (renderer instanceof AEChipRenderer)) {
            final AEChipRenderer r = (AEChipRenderer) renderer;
            float n = (float) r.getSpecialCount(); // In June 2023 tobi add capability of v2e to label noise events as "special events"
            if (n > 0) {
                n = (float) Math.sqrt(n); // a bit of comprression for high counts
                gl.glColor3f(1, 1, 1);
                gl.glLineWidth(SPECIAL_BAR_LINE_WIDTH);
                gl.glBegin(GL.GL_LINE_STRIP);
                gl.glVertex2f(SPECIAL_BAR_LOCATION_X, SPECIAL_BAR_LOCATION_Y);
                gl.glVertex2f(SPECIAL_BAR_LOCATION_X, (float) SPECIAL_BAR_LOCATION_Y + n);
                gl.glEnd();
                getChipCanvas().checkGLError(gl, glu, "after rendering special events");
            }
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glTranslatef(SPECIAL_BAR_LOCATION_X - 3, SPECIAL_BAR_LOCATION_Y, 0);
            gl.glRotated(90, 0, 0, 1);
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
                // textRenderer.setUseVertexArrays(false);
            }
            getChipCanvas().checkGLError(gl, glu, "after transforms and possibily allocating text renderer rendering special event count");
            textRenderer.begin3DRendering();
            textRenderer.setColor(1, 1, 1, 1);
            final String s = String.format("%,d special events", r.getSpecialCount());
            final float textScale = TextRendererScale.draw3dScale(textRenderer, s, getChipCanvas().getScale(), chip.getSizeY(), .2f);

            textRenderer.draw3D(s, 0, 0, 0, textScale); // x,y,z, scale factor
            textRenderer.end3DRendering();
            getChipCanvas().checkGLError(gl, glu, "after text renderer end3DRendering special event count");
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glPopMatrix();
            getChipCanvas().checkGLError(gl, glu, "after rendering special event count");
        }
    }

    private void drawPolygon(final GL2 gl, final int width, final int height) {
        final double xRatio = (double) chip.getSizeX() / (double) width;
        final double yRatio = (double) chip.getSizeY() / (double) height;
        gl.glBegin(GL2.GL_POLYGON);

        gl.glTexCoord2d(0, 0);
        gl.glVertex2d(0, 0);
        gl.glTexCoord2d(xRatio, 0);
        gl.glVertex2d(xRatio * width, 0);
        gl.glTexCoord2d(xRatio, yRatio);
        gl.glVertex2d(xRatio * width, yRatio * height);
        gl.glTexCoord2d(0, yRatio);
        gl.glVertex2d(0, yRatio * height);

        gl.glEnd();
    }

    /**
     * @return the renderSpecialEvents
     */
    public boolean isRenderSpecialEvents() {
        return renderSpecialEvents;
    }

    /**
     * @param renderSpecialEvents the renderSpecialEvents to set
     */
    public void setRenderSpecialEvents(final boolean renderSpecialEvents) {
        this.renderSpecialEvents = renderSpecialEvents;
    }

    /**
     * @return the imageTransform
     */
    public ImageTransform getImageTransform() {
        return imageTransform;
    }

    /**
     * @param imageTransform the imageTransform to set
     */
    public void setImageTransform(final ImageTransform imageTransform) {
        this.imageTransform = imageTransform;
    }

    public void setImageTransform(final Point2D.Float translationPixels, final float rotationRad) {
        imageTransform = new ImageTransform(translationPixels, rotationRad);
    }

    private ImageTransform imageTransform = null;

    public class ImageTransform {

        /**
         * In pixels
         */
        public Point2D.Float translationPixels = new Point2D.Float(0, 0);

        /**
         * In radians, CW from right unit vector.
         */
        public float rotationRad = 0;

        public ImageTransform() {
        }

        public ImageTransform(final Point2D.Float translationPixels, final float rotationRad) {
            this.translationPixels = translationPixels;
            this.rotationRad = rotationRad;
        }

        public ImageTransform(final float translationXPixels, final float translationYPixels, final float rotationRad) {
            translationPixels.x = translationXPixels;
            translationPixels.y = translationYPixels;
            this.rotationRad = rotationRad;
        }
    }
}
