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

import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL2ES1;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import org.bytedeco.javacpp.opencv_core.Mat;

/**
 * Renders using OpenGL the RGB histogram values from Chip2DRenderer.

 * @author Christian Brandli
 * @see net.sf.jaer.graphics.Chip2DRenderer
 * @see net.sf.jaer.graphics.AEChipRenderer
 */
public class ChipRendererDisplayMethodRGBA extends DisplayMethod implements DisplayMethod2D {

    private TextRenderer textRenderer = null;
    public final float SPECIAL_BAR_LOCATION_X = -5;
    public final float SPECIAL_BAR_LOCATION_Y = 0;
    public final float SPECIAL_BAR_LINE_WIDTH=8;
    private boolean renderSpecialEvents=true;
    

    /**
     * Creates a new instance of ChipRendererDisplayMethodRGBA
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

    private float clearDisplay(Chip2DRenderer renderer, GL gl) {
        float gray = renderer.getGrayValue();
        gl.glClearColor(gray, gray, gray, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        return gray;
    }

    private boolean isValidRasterPosition(GL gl) {
        boolean validRaster;
        ByteBuffer buf = ByteBuffer.allocate(1);
        gl.glGetBooleanv(GL2.GL_CURRENT_RASTER_POSITION_VALID, buf);
        buf.rewind();
        byte b = buf.get();
        validRaster = b != 0;
        return validRaster;
    }

    public boolean isUndistortEnabled = false;
    public Mat cameraMatrix;
    public Mat distCoeffs;
     
    private void displayQuad(GLAutoDrawable drawable){
        Chip2DRenderer renderer = getChipCanvas().getRenderer();
        FloatBuffer pixmap = renderer.getPixmap();     
        FloatBuffer onMap = null;
        FloatBuffer offMap = null;
        FloatBuffer annotateMap = null;
        boolean displayEvents = false;
        boolean displayFrames = true;
        boolean displayAnnotation = false;
        if(renderer instanceof AEFrameChipRenderer){
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            onMap = frameRenderer.getOnMap();
            offMap = frameRenderer.getOffMap();
            annotateMap = frameRenderer.getAnnotateMap();
            displayFrames = frameRenderer.isDisplayFrames();
            displayEvents = frameRenderer.isDisplayEvents();
            displayAnnotation = frameRenderer.isDisplayAnnotation();
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
        final int linearFilter=GL.GL_LINEAR; // does linear interpolation of value, to make image that looks smoother
        if(displayFrames){
            gl.glPushMatrix();
            if(imageTransform!=null){
                int sx=chip.getSizeX()/2, sy=chip.getSizeY()/2;
//                gl.glTranslatef(chip.getSizeX()/2, chip.getSizeY()/2, 0);
                gl.glTranslatef(sx,sy, 0);
                gl.glRotatef((float)(imageTransform.rotationRad*180/Math.PI), 0,0, 1);
                gl.glTranslatef(imageTransform.translationPixels.x-sx, imageTransform.translationPixels.y-sy, 0);
            }
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL.GL_REPLACE);
            gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA,
                            GL.GL_FLOAT, pixmap);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture (GL.GL_TEXTURE_2D, 0);
            drawPolygon(gl, width, height);
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glPopMatrix();
            getChipCanvas().checkGLError(gl, glu, "after frames");
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

        if((offMap != null) && displayEvents){
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
        
        if(displayAnnotation){
            gl.glBindTexture(GL.GL_TEXTURE_2D, 1);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, nearestFilter);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, nearestFilter);
            gl.glTexEnvf(GL2ES1.GL_TEXTURE_ENV, GL2ES1.GL_TEXTURE_ENV_MODE, GL.GL_REPLACE);
            gl.glTexImage2D (GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_RGBA,
                            GL.GL_FLOAT, annotateMap);

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

        // show special event count on left of array as white bar
        if (renderSpecialEvents && (renderer instanceof AEChipRenderer)) {
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
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glTranslatef(SPECIAL_BAR_LOCATION_X-3, SPECIAL_BAR_LOCATION_Y, 0);
            gl.glRotated(90, 0, 0, 1);
            if(textRenderer==null){
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
//                textRenderer.setUseVertexArrays(false);
            }
            getChipCanvas().checkGLError(gl, glu, "after transforms and possibily allocating text renderer rendering special event count");
            textRenderer.begin3DRendering();
            textRenderer.setColor(1, 1, 1, 1);
            String s=String.format("%d special events", n);
            textRenderer.draw3D(s, 0, 0, 0, .15f); // x,y,z, scale factor
            textRenderer.end3DRendering();
            getChipCanvas().checkGLError(gl, glu, "after text renderer end3DRendering special event count");
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glPopMatrix();
            getChipCanvas().checkGLError(gl, glu, "after rendering special event count");

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

    /** @deprecated replaced by displayQuad */
    @Deprecated
	private void displayPixmap(GLAutoDrawable drawable) {
        Chip2DRenderer renderer = getChipCanvas().getRenderer();
        GL2 gl=drawable.getGL().getGL2();
        if(gl==null) {
			return;
		}

        clearDisplay(renderer, gl);
        final int ncol = chip.getSizeX();
        final int nrow = chip.getSizeY();
//        final int n = 3 * nrow * ncol;
        getChipCanvas().checkGLError(gl, glu, "before pixmap");
//        Zoom zoom = chip.getCanvas().getZoom();
        if (!zoom.isZoomEnabled()) {
            final int wi = drawable.getSurfaceWidth(),  hi = drawable.getSurfaceHeight();
            float scale = 1;
//            float ar=(float)hi/wi;
            final float border=chip.getCanvas().getBorderSpacePixels();
            if (chip.getCanvas().isFillsVertically()) {// tall chip, use chip height
                scale = (hi - (2 * border)) / (chip.getSizeY());
            } else if (chip.getCanvas().isFillsHorizontally()) {
                scale = (wi - (2 * border)) / (chip.getSizeX() );
            }

            gl.glPixelZoom(scale, scale);
            gl.glRasterPos2f(-.5f, -.5f); // to LL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {
            {
                try {
                    synchronized (renderer) {
                        FloatBuffer pixmap = renderer.getPixmap();
                        if (pixmap != null) {
                            pixmap.position(0);
                            gl.glDrawPixels(ncol, nrow, GL.GL_RGBA, GL.GL_FLOAT, pixmap);
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
                    if((f[ind]!=gray) || (f[ind+1]!=gray) || (f[ind+2]!=gray)) {
                        gl.glColor3fv(f,ind);
                        gl.glRectf(x-.5f, y-.5f, x+.5f, y+.5f);
                    }
                    ind+=4;
                }
            }
        }
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

        if (renderSpecialEvents && (renderer instanceof AEChipRenderer)) {
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
     * @return the renderSpecialEvents
     */
    public boolean isRenderSpecialEvents() {
        return renderSpecialEvents;
    }

    /**
     * @param renderSpecialEvents the renderSpecialEvents to set
     */
    public void setRenderSpecialEvents(boolean renderSpecialEvents) {
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
    public void setImageTransform(ImageTransform imageTransform) {
        this.imageTransform = imageTransform;
    }

    public void setImageTransform(Point2D.Float translationPixels, float rotationRad) {
        this.imageTransform = new ImageTransform(translationPixels, rotationRad);
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

        public ImageTransform(Point2D.Float translationPixels, float rotationRad) {
            this.translationPixels = translationPixels;
            this.rotationRad = rotationRad;
        }

        public ImageTransform(float translationXPixels, float translationYPixels, float rotationRad) {
            this.translationPixels.x = translationXPixels;
            this.translationPixels.y = translationYPixels;
            this.rotationRad = rotationRad;
        }

    }

}
