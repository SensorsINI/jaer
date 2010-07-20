/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.Random;
import javax.media.opengl.*;
import java.nio.FloatBuffer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;

/**
 * OpenGL display of 2d data as color image. See the main method for example of use.
 *
 *<pre>
    public static void main(String[] args) {
        final ImageDisplay disp = ImageDisplay.createOpenGLCanvas();
        JFrame frame = new JFrame("ImageFrame");
        frame.setPreferredSize(new Dimension(400, 400));
        Random r = new Random();
        frame.getContentPane().add(disp, BorderLayout.CENTER);
        int size = 200;
        disp.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                System.out.println(e.toString());
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_X) {
                    System.exit(0);
                } else if (k == KeyEvent.VK_UP) {
                    disp.setSizeY(disp.getSizeY() * 2);
                } else if (k == KeyEvent.VK_DOWN) {
                    disp.setSizeY(disp.getSizeY() / 2);
                } else if (k == KeyEvent.VK_RIGHT) {
                    disp.setSizeX(disp.getSizeX() * 2);
                } else if (k == KeyEvent.VK_LEFT) {
                    disp.setSizeX(disp.getSizeX() / 2);
                } else if (k == KeyEvent.VK_G) {
                    disp.resetFrame(.5f);
                }
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        disp.setxLabel("x label");
        disp.setyLabel("y label");

        disp.setSizeX(size);
        disp.setSizeY(size);

        int frameCounter = 0;
        while (true) {
            disp.checkPixmapAllocation();
            int n = size * size;
            float[] f = disp.getPixmapArray();
            int sx = disp.getSizeX(), sy = disp.getSizeY();
//                for (int x = 0; x < sx; x++) {
//                    for (int y = 0; y < sy; y++) {
//                        int ind = imageDisplay.getPixMapIndex(x, y);
//                        f[ind + 0] = r.nextFloat();
//                        f[ind + 1] = r.nextFloat();
//                        f[ind + 2] = r.nextFloat();
//                    }
//                }
            // randomly select one color of one pixel to change
            synchronized (disp) {
                int ind = disp.getPixMapIndex(r.nextInt(disp.getSizeX()), r.nextInt(disp.getSizeY())) + r.nextInt(3);
                f[ind] = r.nextFloat();
            }
            disp.setTitleLabel("Frame " + (frameCounter++));
            disp.repaint();

        }
    }
    </pre>
 * @author Tobi Delbruck
 */
public class ImageDisplay extends GLCanvas implements GLEventListener {

    protected Preferences prefs = Preferences.userNodeForPackage(ImageDisplay.class);
    protected Logger log = Logger.getLogger("ImageDisplay");
    private int fontSize = 20;
    private int sizeX = 0, sizeY = 0;
    protected float grayValue = 0;
    /** The rendered pixel map, ordered by rgb/row/col. The first 3 elements are the RBB float values of the LL pixel (x=0,y=0). The next 3 are
     * the RGB of the second pixel from the left in the bottom row (x=1,y=0). Pixel (0,1) is at position starting at 3*(chip.getSizeX()).
     */
    protected FloatBuffer pixmap;
    private FloatBuffer grayBuffer;
    private GLU glu = new GLU();
    private TextRenderer textRenderer;
    private final int BORDER_SPACE_PIXELS = 40;
    private boolean fillsVertically;
    private boolean fillsHorizontally;
    private final float ZCLIP = 1;
    /** The actual borders in model space around the chip area. */
    private Borders borders = new Borders();
    private String xLabel = null, yLabel = null, titleLabel = null;
    boolean reshapePending = false;

    /** Creates a new ImageDisplay, given some Open GL capabilities.
     *
     * @param caps the capabilities desired. See factory method.
     * @see #createOpenGLCanvas() for factory method with predefined capabilities.
     */
    public ImageDisplay(GLCapabilities caps) {
        super(caps);

        this.setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

        // this.setSize(300,200);
        this.setVisible(true);

        addGLEventListener(this);

    }

    /** Factory method for creating an ImageDisplay with following capabilities:
     * <pre>
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);
</pre>
     *
     * @return a new ImageDisplay
     */
    public static ImageDisplay createOpenGLCanvas() {
        // design capabilities of opengl canvas
        GLCapabilities caps = new GLCapabilities();
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);

        ImageDisplay trackDisplay = new ImageDisplay(caps);
        return trackDisplay;
    }

    @Override
    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
    }

    /** Called when the canvas is updated. To update the display, call either
     * <code>repaint()</code> or <code>display()</code>.
     * @param drawable the Open GL context.
     */
    @Override
    public synchronized void display(GLAutoDrawable drawable) {

        GL gl = this.getGL();
        if (reshapePending) {
            reshapePending = false;
            reshape(drawable,  0, 0,getWidth(), getHeight());
        }
        try {
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            displayPixmap(drawable);
            drawText(gl);
            gl.glFlush();
        } catch (GLException e) {
            log.warning(e.toString());
        }
    }

    /** Called on initialization
     */
    @Override
    public void init(GLAutoDrawable drawable) {

        log.info("init");

        GL gl = getGL();

        gl.setSwapInterval(1);
        gl.glShadeModel(GL.GL_FLAT);

        // clears to black
        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        // color set to white, raster to LL  corner
        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);
    }

    /** Displays the pixmap of pixel values.
     *
     * @param drawable
     */
    synchronized private void displayPixmap(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        if (gl == null) {
            return;
        }

        checkGLError(gl, "before pixmap");
        final int wi = drawable.getWidth(), hi = drawable.getHeight();
        float scale = 1;
        final float border = BORDER_SPACE_PIXELS;
        if (fillsVertically) {// tall chip, use chip height
            scale = ((float) hi - 2 * border) / getSizeY();
        } else if (fillsHorizontally) {
            scale = ((float) wi - 2 * border) / getSizeX();
        }

        gl.glPixelZoom(scale, scale);
        gl.glRasterPos2f(-.5f, -.5f); // to LL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {

        checkPixmapAllocation();
        {
            try {
                pixmap.position(0);
                gl.glDrawPixels(sizeX, sizeY, GL.GL_RGB, GL.GL_FLOAT, pixmap);
            } catch (IndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }
//        FloatBuffer minMax=FloatBuffer.allocate(6);
//        gl.glGetMinmax(GL.GL_MINMAX, true, GL.GL_RGB, GL.GL_FLOAT, minMax);
//        gl.glDisable(GL.GL_MINMAX);
        checkGLError(gl, "after rendering image");


        // outline frame
        gl.glColor4f(0, 0, 1f, 0f);
        gl.glLineWidth(1f);
        {
            gl.glBegin(GL.GL_LINE_LOOP);
            final float o = .5f;
            final float w = sizeX - 1;
            final float h = sizeY - 1;
            gl.glVertex2f(-o, -o);
            gl.glVertex2f(w + o, -o);
            gl.glVertex2f(w + o, h + o);
            gl.glVertex2f(-o, h + o);
            gl.glEnd();
        }
        checkGLError(gl, "after rendering frame");
    }

    /** Utility method to check for GL errors. Prints stacked up errors up to a limit.
    @param g the GL context
    @param glu the GLU used to obtain the error strings
    @param msg an error message to log to e.g., show the context
     */
    private void checkGLError(GL g, String msg) {
        int error = g.glGetError();
        int nerrors = 3;
        while (error != GL.GL_NO_ERROR && nerrors-- != 0) {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace.length > 1) {
                String className = trace[2].getClassName();
                String methodName = trace[2].getMethodName();
                int lineNumber = trace[2].getLineNumber();
                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg + " at " + className + "." + methodName + " (line " + lineNumber + ")");
            } else {
                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg);
            }
            error = g.glGetError();
        }
    }

    /**
     * The rendered pixel map, ordered by rgb/row/col. The first 3 elements are the RBB float values of the LL pixel (x=0,y=0). The next 3 are
     * the RGB of the second pixel from the left in the bottom row (x=1,y=0). Pixel (0,1) is at position starting at 3*(chip.getSizeX()).
     *
     * @return the pixmap
     * @see #getPixmapArray()  to return a float[] array
     */
    public FloatBuffer getPixmap() {
        return pixmap;
    }

//    public void setPixmapRGB(int x, int y, float[] rgb) {
//        setPixmapPosition(x, y);
//        pixmap.put(rgb);
//    }
//    private float[] rgb = new float[3];
//    public float[] getPixmapRGB(int x, int y) {
//        setPixmapPosition(x, y);
//        pixmap.get(rgb);
//        return rgb;
//    }
    /** Returns an int that can be used to index to a particular pixel's RGB start location in the pixmap.
     * The successive 3 entries are the float (0-1) RGB values.
     *
     * @param x pixel x, 0 is left side.
     * @param y pixel y, 0 is bottom.
     * @return index into pixmap.
     * @see #getPixmapArray()
     */
    public int getPixMapIndex(int x, int y) {
        return 3 * (x + y * sizeX);
    }

    /** Returns the pixmap 1-d array of pixel RGB values.
     *
     * @return the array.
     * @see #getPixMapIndex(int, int)
     */
    public float[] getPixmapArray() {
        return pixmap.array();
    }

//    public void setPixmapPosition(int x, int y) {
//        pixmap.position(3 * (x + y * sizeX));
//    }
//    private float pixmapGrayValue = 0;
    
    /** Sets full pixmap to some gray level. An internal buffer is created if needed
     * so that gray level can be set back quickly using System.arraycopy.
     *
     * @param value the gray level.
     */
    private void resetPixmapGrayLevel(float value) {
        checkPixmapAllocation();
        final int n = 3 * sizeX * sizeY;
        boolean madebuffer = false;
        if (grayBuffer == null || grayBuffer.capacity() != n) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            madebuffer = true;
        }
        if (madebuffer || value != grayValue) {
            grayBuffer.rewind();
            for (int i = 0; i < n; i++) {
                grayBuffer.put(value);
            }
            grayBuffer.rewind();
        }
        System.arraycopy(grayBuffer.array(), 0, pixmap.array(), 0, n);
        pixmap.rewind();
        pixmap.limit(n);
    }

    /** Resets the pixmap frame buffer to a given gray level; can be used at the
     * start of rendering to achieve a particular starting grey level.
     *
     * @param value gray level, 0-1 range.
     */
    synchronized public void resetFrame(float value) {
        resetPixmapGrayLevel(value);
        grayValue = value;
    }

    /** Subclasses should call checkPixmapAllocation to
     * make sure the pixmap FloatBuffer is allocated before accessing it.
     *
     */
    public void checkPixmapAllocation() {
        final int n = 3 * sizeX * sizeY;
        if (pixmap == null || pixmap.capacity() != n) {
            pixmap = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
        }
    }

    /** Indicates that image fills drawable horizontally.
     * @return the fillsHorizontally
     */
    public boolean isFillsHorizontally() {
        return fillsHorizontally;
    }

    /** Indicates that image fills drawable vertically.
     * @return the fillsVertically
     */
    public boolean isFillsVertically() {
        return fillsVertically;
    }

    /** Called on reshape of canvas.
     * Determines which way the chip fits into the display area optimally and calls
     * for a new orthographic projection to achieve this filling.
     * Finally sets the viewport to the entire drawable area.
     */
    @Override
    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        super.reshape(x, y, width, height);
        log.info("reshape ");
        GL gl = drawable.getGL();
        gl.glLoadIdentity();
        float newscale;
        final float border = 0; // getBorderSpacePixels()*(float)height/width;
        if (sizeY > sizeX) {
            // chip is tall and skinny, so set j2dScale by frame height/chip height
            newscale = (float) (height - border) / sizeY;
            fillsVertically = true;
            fillsHorizontally = false;
            if (newscale * sizeX > width) { // unless it runs into left/right, then set to fill width
                newscale = (float) (width - border) / sizeX;
                fillsHorizontally = true;
                fillsVertically = false;
            }
        } else {
            // chip is square or squat, so set j2dScale by frame width / chip width
            newscale = (float) (width - border) / sizeX;
            fillsHorizontally = true;
            fillsVertically = false;
            if (newscale * sizeY > height) {// unless it runs into top/bottom, then set to fill height
                newscale = (float) (height - border) / sizeY;
                fillsVertically = true;
                fillsHorizontally = false;
            }
        }
        setDefaultProjection(gl, drawable); // this sets orthographic projection so that chip pixels are scaled to the drawable area
        gl.glViewport(0, 0, width, height);
        repaint();
    }

    /** Sets the projection matrix so that we get an
     * orthographic projection that is the size of the
    canvas with z volume -ZCLIP to ZCLIP padded with
     * extra space around the sides.

    @param g the GL context
    @param d the GLAutoDrawable canvas
     */
    protected void setDefaultProjection(GL g, GLAutoDrawable d) {
        /*
        void glOrtho(GLdouble left,
        GLdouble right,
        GLdouble bottom,
        GLdouble top,
        GLdouble near,
        GLdouble far)
         */
        final int w = getWidth(), h = getHeight(); // w,h of screen
        final float border = getBorderSpacePixels(); // desired smallest border in screen pixels
        float glScale;
        checkGLError(g, "before setDefaultProjection");
        g.glMatrixMode(GL.GL_PROJECTION);
        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        // now we set the clipping volume so that the volume is clipped according to whether the window is tall (ar>1) or wide (ar<1).

        if (isFillsHorizontally()) { //tall
            glScale = (float) (w - 2 * border) / sizeX; // chip pix to screen pix scaling in horizontal&vert direction
            float b = border / glScale; // l,r border in model coordinates
            if (b <= 0) {
                b = 1;
            }
            float bb = (h / glScale - sizeY) / 2; // leftover y in model coordinates that makes up vertical border
            if (bb <= 0) {
                bb = 1;
            }
            clipArea.left = -b;
            clipArea.right = sizeX + b;
            clipArea.bottom = -bb;
            clipArea.top = sizeY + bb;
            borders.leftRight = b;
            borders.bottomTop = bb;
            g.glOrtho(-b, sizeX + b, -bb, (sizeY + bb), ZCLIP, -ZCLIP); // clip area has same aspect ratio as screen!
        } else {
            glScale = (float) (h - 2 * border) / sizeY;
            float b = border / glScale;
            if (b <= .5f) {
                b = 1;
            }
            float bb = (w / glScale - sizeX) / 2; // leftover y in model coordinates that makes up vertical border
            if (bb <= 0) {
                bb = 1;
            }
            clipArea.left = -bb;
            clipArea.right = sizeX + bb;
            clipArea.bottom = -b;
            clipArea.top = sizeY + b;
            borders.leftRight = bb;
            borders.bottomTop = b;
            g.glOrtho(-bb, (sizeX + bb), -b, sizeY + b, ZCLIP, -ZCLIP);
        }
        g.glMatrixMode(GL.GL_MODELVIEW);
    }

    private float getBorderSpacePixels() {
        return BORDER_SPACE_PIXELS;
    }

    /**  Returns the horizontal dimension of image.
     * @return the sizeX
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * Sets the image horizontal dimension.
     *
     * @param sizeX the sizeX to set
     */
    synchronized public void setSizeX(int sizeX) {
        if (sizeX != this.sizeX) {
            this.sizeX = sizeX;
            checkPixmapAllocation();
            reshapePending=true;
            invalidate();
            repaint();
        }
    }

    /**
     * Returns the image height.
     *
     * @return the sizeY
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * Sets the image height.
     *
     * @param sizeY the sizeY to set
     */
    synchronized public void setSizeY(int sizeY) {
        if (sizeY != this.sizeY) {
            this.sizeY = sizeY;
            checkPixmapAllocation();
            reshapePending=true;
            invalidate();
            repaint();
        }
    }

    /**
     * Returns the x-axis label.
     *
     * @return the xLabel
     */
    public String getxLabel() {
        return xLabel;
    }

    /**
     * Sets the x-axis label.
     * @param xLabel the xLabel to set
     */
    public void setxLabel(String xLabel) {
        if (this.xLabel == xLabel) {
            return;
        }
        this.xLabel = xLabel;
        invalidate();
        repaint();
    }

    /**
     * Returns the y-axis label.
     *
     * @return the yLabel
     */
    public String getyLabel() {
        return yLabel;
    }

    /**
     * Sets the y-axis label.
     *
     * @param yLabel the yLabel to set
     */
    public void setyLabel(String yLabel) {
        if (this.yLabel == yLabel) {
            return;
        }
        this.yLabel = yLabel;
        invalidate();
        repaint();
    }

    /**
     * Returns the title label.
     *
     * @return the titleLabel
     */
    public String getTitleLabel() {
        return titleLabel;
    }

    /**
     * Sets the title label.
     *
     * @param titleLabel the titleLabel to set
     */
    public void setTitleLabel(String titleLabel) {
        if (this.titleLabel == titleLabel) {
            return;
        }
        this.titleLabel = titleLabel;
        invalidate();
        repaint();
    }

    /** Orthographic projection clipping area. */
    private class ClipArea {

        float left = 0, right = 0, bottom = 0, top = 0;

        ClipArea(float l, float r, float b, float t) {
            left = l;
            right = r;
            bottom = b;
            top = t;
        }
    }
    /** The actual clipping box bounds for the default orthographic projection. */
    private ClipArea clipArea = new ClipArea(0, 0, 0, 0);

    /** Border around chip in clipping area. */
    private class Borders {

        float leftRight = 0, bottomTop = 0;
    }

    /** Draws the labels.
     *
     * @param gl
     */
    private void drawText(GL gl) {
        if (textRenderer == null && (xLabel != null || yLabel != null || titleLabel != null)) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, fontSize), true, true);
        }
        textRenderer.setColor(1, 1, 1, 1);
        textRenderer.beginRendering(getWidth(), getHeight());
        float s = (clipArea.top - clipArea.bottom) / getHeight();  // TODO mysterious scalling of text

        if (xLabel != null) {
            Rectangle2D r = textRenderer.getBounds(xLabel);
            textRenderer.draw(xLabel, (int) (getWidth() - r.getWidth()) / 2, (int)((-clipArea.bottom/s)-r.getHeight()));
        }
        if (titleLabel != null) {
            Rectangle2D r = textRenderer.getBounds(titleLabel);
            textRenderer.draw(titleLabel, (int) (-r.getWidth() + getWidth()) / 2, (int) (getHeight() - r.getHeight())); // +(float)(-r.getHeight())
        }
        textRenderer.endRendering();

        if (yLabel != null) { // TODO fix rendering of y axis label to be constant screen size.  rotatef doesn't work with draw(), must use draw3D but don't understand scalling
            textRenderer.begin3DRendering();
            gl.glMatrixMode(GL.GL_MODELVIEW);
           gl.glPushMatrix();

            Rectangle2D r = textRenderer.getBounds(yLabel);
            gl.glTranslated(   -r.getHeight()*s, sizeY/2-r.getWidth()/2*s,           0);
            gl.glRotatef(90, 0, 0, 1);

            textRenderer.draw3D(yLabel, 0, 0,0,s);
            textRenderer.end3DRendering();

            gl.glPopMatrix();

        }
        checkGLError(gl, "after text");
    }

    /////////////////////////////////////////////////////////////////////
    /** Displays some random noise.
     *
     * @param args - no effect.
     */
    public static void main(String[] args) {

        final ImageDisplay disp = ImageDisplay.createOpenGLCanvas();


        JFrame frame = new JFrame("ImageFrame");
        frame.setPreferredSize(new Dimension(400, 400));
        Random r = new Random();
        frame.getContentPane().add(disp, BorderLayout.CENTER);
        int size = 200;

        disp.addKeyListener(new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e) {
                System.out.println(e.toString());
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_X) {
                    System.exit(0);
                } else if (k == KeyEvent.VK_UP) {
                    disp.setSizeY(disp.getSizeY() * 2);
                } else if (k == KeyEvent.VK_DOWN) {
                    disp.setSizeY(disp.getSizeY() / 2);
                } else if (k == KeyEvent.VK_RIGHT) {
                    disp.setSizeX(disp.getSizeX() * 2);
                } else if (k == KeyEvent.VK_LEFT) {
                    disp.setSizeX(disp.getSizeX() / 2);
                } else if (k == KeyEvent.VK_G) {
                    disp.resetFrame(.5f);
                }
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        disp.setxLabel("x label");
        disp.setyLabel("y label");

        disp.setSizeX(size);
        disp.setSizeY(size);


        int frameCounter = 0;
        while (true) {
            disp.checkPixmapAllocation();
            int n = size * size;
            float[] f = disp.getPixmapArray();
            int sx = disp.getSizeX(), sy = disp.getSizeY();
//                for (int x = 0; x < sx; x++) {
//                    for (int y = 0; y < sy; y++) {
//                        int ind = imageDisplay.getPixMapIndex(x, y);
//                        f[ind + 0] = r.nextFloat();
//                        f[ind + 1] = r.nextFloat();
//                        f[ind + 2] = r.nextFloat();
//                    }
//                }
            // randomly select one color of one pixel to change
            synchronized (disp) {
                int ind = disp.getPixMapIndex(r.nextInt(disp.getSizeX()), r.nextInt(disp.getSizeY())) + r.nextInt(3);
                f[ind] = r.nextFloat();
            }
            disp.setTitleLabel("Frame " + (frameCounter++));
            disp.repaint();

        }
    }
}
