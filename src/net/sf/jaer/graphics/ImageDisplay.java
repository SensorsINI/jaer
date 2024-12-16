/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.util.WindowSaver;

import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Date;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import static net.sf.jaer.graphics.ImageDisplay.log;

/**
 * OpenGL display of 2d data as color image. See the main method for example of
 * use.
 *
 * ImageDisplay should be OK for displaying the spectrogram and similar 2d
 * arrays of float info.
 *
 * You create a new ImageDisplay by using the static factory method
 * createImageDisplay, set the x,y size on it, and then you access the frame
 * pixmap directly by setting float (0-1 range) RGB or gray values at chosen x,
 * y locations. It takes care of scaling, centering, etc for you. When you are
 * ready to show the frame, call display() or repaint() on the ImageDisplay.
 *
 *
 *
 * <pre>
 *
 *     public static void main(String[] args) {
 *
 * final ImageDisplay disp = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
 * JFrame frame = new JFrame("ImageFrame");  // make a JFrame to hold it
 * frame.setPreferredSize(new Dimension(400, 400));  // set the window size
 * frame.getContentPane().add(disp, BorderLayout.CENTER); // add the GLCanvas to the center of the window
 * int size = 200;  // used later to define image size
 *
 *      disp.setSize(size,size); // set dimensions of image
 *
 *      disp.addKeyListener(new KeyAdapter() { // add some key listeners to the ImageDisplay
 *
 *      public void keyReleased(KeyEvent e) {
 * System.out.println(e.toString());
 * int k = e.getKeyCode();
 * if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_X) {
 * System.exit(0);
 * } else if (k == KeyEvent.VK_UP) {
 * disp.setSizeY(disp.getSizeY() * 2); // UP arrow incrases vertical dimension
 * } else if (k == KeyEvent.VK_DOWN) {
 * disp.setSizeY(disp.getSizeY() / 2);
 * } else if (k == KeyEvent.VK_RIGHT) {
 * disp.setSizeX(disp.getSizeX() * 2);
 * } else if (k == KeyEvent.VK_LEFT) {
 * disp.setSizeX(disp.getSizeX() / 2);
 * } else if (k == KeyEvent.VK_G) { // 'g' resets the frame to gray level 0.5f
 * disp.resetFrame(.5f);
 * }
 * }
 * });
 * frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // closing the frame exits
 * frame.pack(); // otherwise it wont fill up the display
 * frame.setVisible(true); // make the frame visible
 *
 * disp.setxLabel("x label"); // add xaxis label and some tick markers
 * disp.addXTick(0, "0");
 * disp.addXTick(size, Integer.toString(size));
 * disp.addXTick(size / 2, Integer.toString(size / 2));
 *
 * disp.setyLabel("y label"); // same for y axis
 * disp.addYTick(0, "0");
 * disp.addYTick(size, Integer.toString(size));
 * disp.addYTick(size / 2, Integer.toString(size / 2));
 *
 *
 * Random r = new Random();  // will use to fill display with noise
 *
 * int frameCounter = 0; // iterate over frame updates
 * while (true) {
 * disp.checkPixmapAllocation(); // make sure we have a pixmaps (not resally necessary since setting size will allocate pixmap
 * int n = size * size;
 * float[] f = disp.getPixmapArray(); // get reference to pixmap array so we can set pixel values
 * int sx = disp.getSizeX(), sy = disp.getSizeY();
 * // randomly update all pixels
 * //                for (int x = 0; x < sx; x++) {
 * //                    for (int y = 0; y < sy; y++) {
 * //                        int ind = imageDisplay.getPixMapIndex(x, y);
 * //                        f[ind + 0] = r.nextFloat();
 * //                        f[ind + 1] = r.nextFloat();
 * //                        f[ind + 2] = r.nextFloat();
 * //                    }
 * //                }
 * // randomly select one color of one pixel to change
 * //                int ind = disp.getPixMapIndex(r.nextInt(disp.getSizeX()), r.nextInt(disp.getSizeY())) + r.nextInt(3);
 * //                f[ind] = r.nextFloat();
 * //
 * // randomly set one pixel to some gray level
 * //            disp.resetFrame(0);
 * //            int xx = r.nextInt(disp.getSizeX());
 * //            int yy = r.nextInt(disp.getSizeY());
 * //            disp.setPixmapGray(xx, yy, r.nextFloat());
 *
 * // clear frame to black
 * //            disp.resetFrame(0);
 *
 * // randomly set a pixel to some RGB value
 * int xx = r.nextInt(disp.getSizeX());
 * int yy = r.nextInt(disp.getSizeY());
 * disp.setPixmapRGB(xx, yy, r.nextFloat(), r.nextFloat(), r.nextFloat());
 *
 * // randomly change axes font size
 * //            if(frameCounter%1000==0){
 * //                disp.setFontSize(r.nextInt(60));
 * //            }
 *
 *
 * disp.setTitleLabel("Frame " + (frameCounter++));
 *
 * // ask for a repaint
 * disp.repaint();
 *
 * }
 * }
 *
 *
 *
 * </pre> @author Tobi Delbruck
 */
public class ImageDisplay extends GLJPanel implements GLEventListener {

    private static final Preferences prefs = Preferences.userNodeForPackage(ImageDisplay.class);
    static final Logger log = Logger.getLogger("net.sf.jaer");
    private int fontSize = 20;
    private int sizeX = 1, sizeY = 1;
    /**
     * The gray value. Default is 0.
     */
    private float grayValue = 0;
    /**
     * The rendered pixel map, ordered by rgb/row/col. The first 3 elements are
     * the RBB float values of the LL pixel (x=0,y=0). The next 3 are the RGB of
     * the second pixel from the left in the bottom row (x=1,y=0). Pixel (0,1)
     * is at position starting at 3*(chip.getSizeX()).
     */
    protected FloatBuffer pixmap;
    private FloatBuffer grayBuffer;
    private GLU glu = new GLU();
    private TextRenderer textRenderer;
    public final int BORDER_SPACE_PIXELS_DEFAULT = 40;
    private float borderPixels = BORDER_SPACE_PIXELS_DEFAULT;
    private boolean fillsVertically;
    private boolean fillsHorizontally;
    private float glScale = 1; // set in setDefaultProjection

    private final float ZCLIP = 1;
    /**
     * The actual borders in model space around the chip area.
     */
    private Borders borders = new Borders();
    private String xLabel = null, yLabel = null, titleLabel = null;
    boolean reshapePending = false;
    private HashMap<Integer, String> xticks, yticks;
    private float[] textColor = new float[]{1, 1, 1};
    private ArrayList<Legend> legends = new ArrayList();

    /**
     * Creates a new ImageDisplay, given some Open GL capabilities.
     *
     * @param caps the capabilities desired. See factory method.
     * @see #createOpenGLCanvas() for factory method with predefined
     * capabilities.
     */
    public ImageDisplay(GLCapabilitiesImmutable caps) {
        super(caps);

        setLocale(java.util.Locale.US); // to avoid problems with other language support in JOGL

        // this.setSize(300,200);
        setVisible(true);
//        setAutoSwapBufferMode(true);

        addGLEventListener(this);
        try {
            if (JAERViewer.sharedDrawable != null) {
                setSharedContext(JAERViewer.sharedDrawable.getContext());
            }
        } catch (GLException e) {
            log.log(Level.WARNING, "While trying to set the shared context to the JAERViewer.sharedDrawable context, caught exception: {0}", e.toString());
        }

    }

    /**
     * Factory method for creating an ImageDisplay with following capabilities:
     * <pre>
     * caps.setDoubleBuffered(true);
     * caps.setHardwareAccelerated(true);
     * caps.setAlphaBits(8);
     * caps.setRedBits(8);
     * caps.setGreenBits(8);
     * caps.setBlueBits(8);
     * </pre>
     *
     * @return a new ImageDisplay
     */
    public static ImageDisplay createOpenGLCanvas() {
        // design capabilities of opengl canvas
        GLCapabilities caps = new GLCapabilities(null);
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);
//        caps.setSampleBuffers(true);
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);
        ImageDisplay trackDisplay = new ImageDisplay(caps);
        return trackDisplay;
    }

    /**
     * Called when the canvas is updated. To update the display, call either
     * <code>repaint()</code> or <code>display()</code>.
     *
     * @param drawable the Open GL context.
     */
    @Override
    public void display(GLAutoDrawable drawable) {

        GL2 gl = getGL().getGL2();

//        gl.getContext().makeCurrent();
        log.log(Level.FINER, "drawable={0}", drawable.toString());
        checkGLError(gl, "before display in ID");
        if (reshapePending) {
            reshapePending = false;
            reshape(drawable, 0, 0, getWidth(), getHeight());
        }
        try {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, getFontSize()), true, true);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            drawPixmap(drawable);
            drawText(gl);
            gl.glFlush();
        } catch (GLException e) {
            log.warning(e.toString());
        }
        checkGLError(gl, "after setDefaultProjection in ID");
    }

    /**
     * Called on initialization
     *
     * @param drawable
     */
    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

//        log.info("OpenGL implementation is: " + gl.getClass().getName() + "\nGL_VENDOR: "
//                + gl.glGetString(GL.GL_VENDOR) + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER) + "\nGL_VERSION: "
//                + gl.glGetString(GL.GL_VERSION) // + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
//        );
        final float glVersion = Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0, 3));
        if (glVersion < 1.3f) {
            log.warning("\n\n*******************\nOpenGL version "
                    + glVersion
                    + " < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
        }

        gl.setSwapInterval(1);
        gl.glShadeModel(GLLightingFunc.GL_FLAT);

        // clears to black
        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        // color set to white, raster to LL  corner
        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);

        checkGLError(gl, "ImageDisplay, after init");
    }

    /**
     * Displays the pixmap of pixel values.
     *
     * @param drawable
     */
    synchronized private void drawPixmap(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }
        if (pixmap == null) {
            log.warning("null pixmap, cannot display");
            return;
        }

        checkGLError(gl, "before pixmap");
        
        {
            gl.glPushMatrix();
            gl.glPixelZoom(glScale, glScale);
            //        gl.glRasterPos2f(-.5f, -.5f); // to LL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {
            gl.glRasterPos2f(0, 0); // to LL corner of chip, but must be inside viewport or else it is ignored, breaks on zoom     if (zoom.isZoomEnabled() == false) {

            checkPixmapAllocation();
            {
                try {
                    pixmap.rewind();
                    gl.glDrawPixels(sizeX, sizeY, GL.GL_RGB, GL.GL_FLOAT, pixmap);
                } catch (IndexOutOfBoundsException e) {
                    log.warning(e.toString());
                }
            }
            gl.glPopMatrix();
        }
        //        FloatBuffer minMax=FloatBuffer.allocate(6);
        //        gl.glGetMinmax(GL.GL_MINMAX, true, GL.GL_RGB, GL.GL_FLOAT, minMax);
        //        gl.glDisable(GL.GL_MINMAX);
        checkGLError(gl, "after rendering image");

        // outline frame
        gl.glColor4f(0, 0, 1f, 0f);
        gl.glLineWidth(2f);
        {
            gl.glBegin(GL.GL_LINE_LOOP);
            final float o = 0;
            final float w = sizeX;
            final float h = sizeY;
            gl.glVertex2f(-o, -o);
            gl.glVertex2f(w + o, -o);
            gl.glVertex2f(w + o, h + o);
            gl.glVertex2f(-o, h + o);
            gl.glEnd();
        }
        checkGLError(gl, "after rendering frame");
    }

    /**
     * Utility method to check for GL errors. Prints stacked up errors up to a
     * limit.
     *
     * @param g the GL context
     * @param glu the GLU used to obtain the error strings
     * @param msg an error message to log to e.g., show the context
     */
    private void checkGLError(GL2 g, String msg) {
        int error = g.glGetError();
        int nerrors = 3;
        while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace.length > 1) {
                String className = trace[2].getClassName();
                String methodName = trace[2].getMethodName();
                int lineNumber = trace[2].getLineNumber();
                log.log(Level.WARNING, "GL error number {0} {1} : {2} at {3}.{4} (line {5})", new Object[]{error, glu.gluErrorString(error), msg, className, methodName, lineNumber});
            } else {
                log.log(Level.WARNING, "GL error number {0} {1} : {2}", new Object[]{error, glu.gluErrorString(error), msg});
            }
            error = g.glGetError();
        }
    }

    /**
     * The rendered pixel map, ordered by rgb/row/col. The first 3 elements are
     * the RBB float values of the LL pixel (x=0,y=0). The next 3 are the RGB of
     * the second pixel from the left in the bottom row (x=1,y=0). Pixel (0,1)
     * is at position starting at 3*(chip.getSizeX()).
     *
     * @return pixmap
     * @see #getPixmapArray() to return a float[] array
     */
    public FloatBuffer getPixmap() {
        return pixmap;
    }

    /**
     * The rendered pixel map FloatBuffer, ordered by rgb/row/col. The first 3
     * elements are the RBB float values of the LL pixel (x=0,y=0). The next 3
     * are the RGB of the second pixel from the left in the bottom row
     * (x=1,y=0). Pixel (0,1) is at position starting at 3*(chip.getSizeX()).
     *
     *
     * @param pixmap
     */
    public void setPixmap(FloatBuffer pixmap) {
        this.pixmap = pixmap;
    }

    /**
     * Sets the pixel RGB value.
     *
     * @param x row
     * @param y column
     * @param rgb an array of RGB values in range 0-1.
     */
    synchronized public void setPixmapRGB(int x, int y, float[] rgb) {
        setPixmapPosition(x, y);
        pixmap.put(rgb);
    }

    /**
     * Sets the pixel RGB value.
     *
     * @param x row
     * @param y column
     * @param r red value, 0-1 range.
     * @param g green value, 0-1 range.
     * @param b blue value, 0-1 range.
     */
    synchronized public void setPixmapRGB(int x, int y, float r, float g, float b) {
        setPixmapPosition(x, y);
        pixmap.put(r);
        pixmap.put(g);
        pixmap.put(b);
    }

    /**
     * Sets a gray value in range 0-1 for pixel x,y. Convenience wrapper around
     * setPixmapRGB.
     *
     * @param x
     * @param y
     * @param gray the gray value, in range 0-1.
     */
    public void setPixmapGray(int x, int y, float gray) {
        if ((x < 0) || (x > sizeX) || (y < 0) || (y > sizeY)) {
            return;
        }
        setPixmapPosition(x, y);
        pixmap.put(new float[]{gray, gray, gray});
    }

    /**
     * Sets a gray value for pixel x,y. Convenience wrapper around setPixmapRGB.
     *
     * @param x
     * @param y
     * @param grayChange the gray change value, in range 0-1.
     */
    synchronized public void changePixmapGrayValueBy(int x, int y, float grayChange) {
        setPixmapPosition(x, y);
        float[] rgb = new float[3];
        pixmap.get(rgb);
        rgb[0] += grayChange;
        rgb[1] += grayChange;
        rgb[2] += grayChange;
        setPixmapPosition(x, y);
        pixmap.put(rgb);
    }

    private float[] rgb = new float[3];

    /**
     * Returns a re-used float[] of pixmap RGB values at location x,y.
     *
     * @param x column
     * @param y row
     * @return float[3] array of RGB values.
     */
    synchronized public float[] getPixmapRGB(int x, int y) {
        setPixmapPosition(x, y);
        pixmap.get(rgb);
        return rgb;
    }

    /**
     * Returns an int that can be used to index to a particular
     * pixel'legendString RGB start location in the pixmap. The successive 3
     * entries are the float (0-1) RGB values.
     *
     * @param x pixel x, 0 is left side.
     * @param y pixel y, 0 is bottom.
     * @return index into pixmap.
     * @see #getPixmapArray()
     */
    public int getPixMapIndex(int x, int y) {
        return 3 * (x + (y * sizeX));
    }

    /**
     * Returns the pixmap 1-d array of pixel RGB values.
     *
     * @return the array.
     * @see #getPixMapIndex(int, int)
     */
    public float[] getPixmapArray() {
        return pixmap.array();
    }

    /**
     * Sets the whole pixmap array - a float[] of the kind R1,G1,B1,R2,G2,...
     *
     * @param array
     */
    public void setPixmapArray(float[] array) {
        checkPixmapAllocation();
        System.arraycopy(array, 0, pixmap.array(), 0, array.length);
        pixmap.rewind();
        pixmap.limit(array.length);
    }

    /**
     * Updates pixmap values with the values in array only in the region that
     * comes within the blurring kernel, centered at x, y.
     *
     * @param x
     * @param y
     * @param kernelextent is the half the width of the kernel
     * @param chipsize
     * @param array
     */
    public void setEventPixmapNbd(int x, int y, int kernelextent, int chipsize, float[] array) {
        checkPixmapAllocation();

        for (int i = -kernelextent; i < (kernelextent + 1); i++) {
            System.arraycopy(array, (3 * ((x - kernelextent) + ((y + i) * chipsize))), pixmap.array(), (3 * ((x - kernelextent) + ((y + i) * chipsize))), (3 * ((2 * kernelextent) + 1)));
        }

        pixmap.rewind();
    }

    /**
     * Sets the whole pixmap array in gray values - a float[] of the kind gray1,
     * gray2,...
     *
     * @param src the array of gray values, indexed as in setPixmapGray()
     * @see #setPixmapGray(int, int, float)
     */
    public void setPixmapFromGrayArray(float[] src) {
        checkPixmapAllocation();
        final int n = 3 * src.length;
        float[] dest = pixmap.array();
        for (int i = 0; i < src.length; i++) {
            float s = src[i];
            for (int j = 0; j < 3; j++) {
                dest[3 * i + j] = s;
            }
        }
        pixmap.rewind();
        pixmap.limit(n);
    }

    /**
     * Sets the whole pixmap array in gray values - a float[x][y] of gray values
     *
     * @param src the array of gray values
     * @see #setPixmapGray(int, int, float)
     */
    public void setPixmapFromGrayArray(float[][] src) {
        checkPixmapAllocation();
        float[] dest = pixmap.array();
        int nx = src.length, ny = src[0].length;
        for (int x = 0; x < nx; x++) {
            for (int y = 0; y < ny; y++) {
                float s = src[x][y];
                setPixmapGray(x, y, s);
            }
            pixmap.rewind();
        }
    }

    /**
     * Position the pixmap buffer at pixel x,y, i.e. at
     * <code> 3 * (x + (y * sizeX))</code>.
     *
     * @param x row
     * @param y column
     */
    synchronized private void setPixmapPosition(int x, int y) {
        pixmap.position(3 * (x + (y * sizeX)));
    }

    //    private float pixmapGrayValue = 0;
    /**
     * Sets full pixmap to some gray level. An internal buffer is created if
     * needed so that gray level can be set back quickly using System.arraycopy.
     *
     * @param value the gray level.
     */
    private void resetPixmapGrayLevel(float value) {
        checkPixmapAllocation();
        final int n = 3 * sizeX * sizeY;
        boolean madebuffer = false;
        if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
            grayBuffer = FloatBuffer.allocate(n); // Buffers.newDirectFloatBuffer(n);
            madebuffer = true;
        }
        if (madebuffer || (value != grayValue)) {
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

    /**
     * Resets the pixmap frame buffer to a given gray level; can be used at the
     * start of rendering to achieve a particular starting grey level. Also sets
     * the gray value.
     *
     * @param value gray level, 0-1 range.
     */
    synchronized public void resetFrame(float value) {
        resetPixmapGrayLevel(value);
        grayValue = value;
    }

    /**
     * Subclasses should call checkPixmapAllocation to make sure the pixmap
     * FloatBuffer is allocated before accessing it.
     *
     */
    public void checkPixmapAllocation() {
        final int n = 3 * sizeX * sizeY;
        if (n == 0) {
            log.warning("tried to set pixmap with 0 pixels in it; ignoring");
            return;
        }
        if ((pixmap == null) || (pixmap.capacity() != n)) {
            if (pixmap != null) {
                pixmap = null;
            }
            System.gc();
            if (n > 0) {
                log.log(Level.FINE, "allocating {0} floats for pixmap", n);
                pixmap = FloatBuffer.allocate(n); // Buffers.newDirectFloatBuffer(n);
                pixmap.rewind();
                pixmap.limit(n);
            }
        }

    }

    /**
     * Indicates that image fills drawable horizontally.
     *
     * @return the fillsHorizontally
     */
    public boolean isFillsHorizontally() {
        return fillsHorizontally;
    }

    /**
     * Indicates that image fills drawable vertically.
     *
     * @return the fillsVertically
     */
    public boolean isFillsVertically() {
        return fillsVertically;
    }

    /**
     * Called on reshape of canvas. Determines which way the chip fits into the
     * display area optimally and calls for a new orthographic projection to
     * achieve this filling. Finally sets the viewport to the entire drawable
     * area.
     */
    @Override
    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
//        super.reshape(x, y, width, height); // makes reshape loop that result in infinite size, reason unknown
        log.fine(String.format("reshape x=%d, y=%d, width=%d, height=%d", x, y, width, height));
        GL2 gl = drawable.getGL().getGL2();
        gl.glLoadIdentity();
        float newscale;
        final float border = 0; // getBorderSpacePixels()*(float)height/width;
        if (sizeY > sizeX) {
            // chip is tall and skinny, so set j2dScale by frame height/chip height
            newscale = (height - border) / sizeY;
            fillsVertically = true;
            fillsHorizontally = false;
            if ((newscale * sizeX) > width) { // unless it runs into left/right, then set to fill width
//                newscale = (width - border) / sizeX;
                fillsHorizontally = true;
                fillsVertically = false;
            }
        } else {
            // chip is square or squat, so set j2dScale by frame width / chip width
            newscale = (width - border) / sizeX;
            fillsHorizontally = true;
            fillsVertically = false;
            if ((newscale * sizeY) > height) {// unless it runs into top/bottom, then set to fill height
//                newscale = (height - border) / sizeY;
                fillsVertically = true;
                fillsHorizontally = false;
            }
        }
//        log.info("height=" + height + " width=" + width + " fillsHorizontally=" + fillsHorizontally + " fillsVertically=" + fillsVertically + " newscale=" + newscale);
        setDefaultProjection(gl, drawable); // this sets orthographic projection so that chip pixels are scaled to the drawable area
        gl.glViewport(0, 0, width, height); // already done before reshape according to GL javadoc
        repaint(1000);
    }

    /**
     * Sets the projection matrix so that we get an orthographic projection that
     * is the size of the canvas with z volume -ZCLIP to ZCLIP padded with extra
     * space around the sides.
     *
     * @param g the GL context
     * @param d the GLAutoDrawable canvas
     */
    protected void setDefaultProjection(GL2 g, GLAutoDrawable d) {
        /*
         void glOrtho(GLdouble left,
         GLdouble right,
         GLdouble bottom,
         GLdouble top,
         GLdouble near,
         GLdouble far)
         */
        final int w = getWidth(), h = getHeight(); // w,h of screen
        checkGLError(g, "before setDefaultProjection in ImageDisplay");
        g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
//        g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        // now we set the clipping volume so that the volume is clipped according to whether the window is tall (ar>1) or wide (ar<1).

        if (isFillsHorizontally()) { //tall window, chip/array fills window horizontally
            glScale = (w - (2 * borderPixels)) / sizeX; // chip pix to screen pix scaling in horizontal&vert direction
            float b = borderPixels / glScale; // l,r border in model coordinates
            //            if (b <= 0.5f) {
            //                b = 1;
            //            }
            // vertical border comprises left over screen region
            float bb = ((h / glScale) - sizeY) / 2; // leftover y in model coordinates that makes up vertical border
            //            if (bb <= .5f) {
            //                bb = 1;
            //            }
            clipArea.left = -b;
            clipArea.right = sizeX + b;
            clipArea.bottom = -bb;
            clipArea.top = sizeY + bb;
            borders.leftRight = b;
            borders.bottomTop = bb;
            g.glOrtho(-b, sizeX + b, -bb, (sizeY + bb), ZCLIP, -ZCLIP); // clip area has same aspect ratio as screen!
//            log.info("b border=" + b + " pixels, bb border=" + bb + " pixels");
        } else { //wide window, chip/array fills window vertically
            glScale = (h - (2 * borderPixels)) / sizeY; // number of screen pixels in drawing area (not counting borders) per chip/array pixels
            float b = borderPixels / glScale; // equiv number of chip/array pixels occupied by each horizontal border
            //            if (b <= .5f) { // if border is less than .5 chip pixel, then set it to at least a pixel
            //                b = 1;
            //            }
            // horizontal border region for this case comprises leftover screen pixels
            float bb = ((w / glScale) - sizeX) / 2; // leftover y in model coordinates that makes up each horizontal border
            //            if (bb <= .5f) {
            //                bb = 1; // at least 1 chip/array pixel border
            //            }
            clipArea.left = -bb;
            clipArea.right = sizeX + bb;
            clipArea.bottom = -b;
            clipArea.top = sizeY + b;
            borders.leftRight = bb;
            borders.bottomTop = b;
            g.glOrtho(-bb, (sizeX + bb), -b, sizeY + b, ZCLIP, -ZCLIP);
//            log.info("b border=" + b + " pixels, bb border=" + bb + " pixels");
        }
        g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        
               final int swi = getSurfaceWidth(), shi = getSurfaceHeight();
        final int wi = getWidth(), hi = getHeight();
        // running on HighDPI display screen gives more surface pixels that canvas pixels.
        // use this trick to scale the drawPixels correctly.
        float highDPIScale = swi / wi;

        float scale = glScale;
        glScale=scale*highDPIScale;
        log.fine(String.format("(surf width)/width=%.3f, scale=%.3f, final glScale=%.3f",highDPIScale, scale, glScale));
        checkGLError(g, "after setDefaultProjection in ImageDisplay");
    }

    /**
     * @see #setBorderSpacePixels(int)
     * @return image being displayed pixels border
     */
    public float getBorderSpacePixels() {
        return borderPixels;
    }

    /**
     * Sets the border outside the image frame in pixels (this space is in
     * screen pixels)
     *
     * @param border
     */
    public void setBorderSpacePixels(int border) {
        borderPixels = border;
    }

    /**
     * Returns the horizontal dimension of image; note this is source image
     * size, not screen pixels which can be much larger.
     *
     * @return the sizeX
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * Sets the image horizontal dimension; note this is source image size, not
     * screen pixels which can be much larger..
     *
     * @param sizeX the sizeX to set
     */
    synchronized public void setSizeX(int sizeX) {
        if (sizeX != this.sizeX) {
            this.sizeX = sizeX;
            checkPixmapAllocation();
            reshapePending = true;
            invalidate();
            repaint();
        }
    }

    /**
     * Sets both horizontal and vertical dimensions of image in source pixels.
     *
     * @param sizeX columns
     * @param sizeY rows
     */
    synchronized public void setImageSize(int sizeX, int sizeY) {
        if ((sizeX != this.sizeX) || (sizeY != this.sizeY)) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            checkPixmapAllocation();
            reshapePending = true;
            invalidate();
            repaint();

        }
    }

    /**
     * Returns the image height; note this is source image size, not screen
     * pixels which can be much larger.
     *
     * @return the sizeY
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * Sets the image height; note this is source image size, not screen pixels
     * which can be much larger.
     *
     * @param sizeY the sizeY to set
     */
    synchronized public void setSizeY(int sizeY) {
        if (sizeY != this.sizeY) {
            this.sizeY = sizeY;
            checkPixmapAllocation();
            reshapePending = true;
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
     *
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
    }

    /**
     * Clears all x axis tick labels.
     *
     */
    synchronized public void clearXTicks() {
        xticks = null;
    }

    /**
     * Add an xtick at axis location location.
     *
     * @param location
     * @param tickLabel
     */
    synchronized public void addXTick(int location, String tickLabel) {
        if (xticks == null) {
            xticks = new HashMap();
        }
        xticks.put(location, tickLabel);
    }

    /**
     * Clears all y axis tick labels.
     *
     */
    synchronized public void clearYTicks() {
        yticks = null;
    }

    /**
     * Add an ytick at axis location location.
     *
     * @param location
     * @param tickLabel
     */
    synchronized public void addYTick(int location, String tickLabel) {
        if (yticks == null) {
            yticks = new HashMap();
        }
        yticks.put(location, tickLabel);
    }

    /**
     * Returns the current labeling font size.
     *
     * @return the fontSize
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * Sets a new font size for the axes labels and title.
     *
     * @param fontSize the fontSize to set, e.g 12. This is in points.
     */
    synchronized public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    //    /**
    //     * @return the grayValue
    //     */
    //    public float getGrayValue() {
    //        return grayValue;
    //    }
    //
    //    /**
    //     * Sets the gray value that image is cleared to by clearImage().
    //     *
    //     * @param grayValue the grayValue to set
    //     */
    //    public void setGrayValue(float grayValue) {
    //        this.grayValue = grayValue;
    //        clearImage(); // TODO needed to get this gray value to apply
    //    }
    /**
     * Orthographic projection clipping area.
     */
    private class ClipArea {

        float left = 0, right = 0, bottom = 0, top = 0;

        ClipArea(float l, float r, float b, float t) {
            left = l;
            right = r;
            bottom = b;
            top = t;
        }
    }

    /**
     * Sets the text color.
     *
     * @param rgb
     */
    public void setTextColor(float[] rgb) {
        textColor = rgb;
    }

    /**
     * Returns the current text color.
     *
     * @return an RGB array for all the text colors.
     */
    public float[] getTextColor() {
        return textColor;
    }
    /**
     * The actual clipping box bounds for the default orthographic projection.
     */
    private ClipArea clipArea = new ClipArea(0, 0, 0, 0);

    /**
     * Border around chip in clipping area.
     */
    private class Borders {

        float leftRight = 0, bottomTop = 0;
    }

    /**
     * Draws a string centered at x,y.
     *
     * @param x horizontal coordinate in image units.
     * @param y coordinate in image units.
     * @param string the string to draw.
     */
    public void drawCenteredString(float x, float y, String string) {
        if ((string == null) || (textRenderer == null)) {
            return;
        }
        textRenderer.beginRendering(getWidth(), getHeight());
        float scale = (clipArea.top - clipArea.bottom) / getHeight();  // TODO mysterious scalling of text

        Rectangle2D r = textRenderer.getBounds(string);
        textRenderer.draw(string, (int) ((x / scale) - (clipArea.left / scale)), Math.round((y / scale) - (clipArea.bottom / scale)));
        textRenderer.endRendering();
    }

    /**
     * Draws the labels and tick marks, if they exist.
     *
     * @param gl
     */
    private void drawText(GL2 gl) {
        if ((xLabel == null) && (yLabel == null) && (titleLabel == null) && (xticks == null) && (yticks == null) && legends.isEmpty()) {
            return;
        }
        gl.glPushMatrix();
        textRenderer.setColor(textColor[0], textColor[1], textColor[2], 1);

        textRenderer.beginRendering(getWidth(), getHeight());
        float s = (clipArea.top - clipArea.bottom) / getHeight();  // TODO mysterious scalling of text

        if (xLabel != null) {
            Rectangle2D r = textRenderer.getBounds(xLabel);
            textRenderer.draw(xLabel, (int) (getWidth() - r.getWidth()) / 2, (int) ((-clipArea.bottom / s) - ((xticks != null ? 2 : 1) * r.getHeight())));  // shift text down if there are tick markers
        }
        if (titleLabel != null) {
            Rectangle2D r = textRenderer.getBounds(titleLabel);
            textRenderer.draw(titleLabel, (int) (-r.getWidth() + getWidth()) / 2, (int) (getHeight() - r.getHeight())); // +(float)(-r.getHeight())
        }
        textRenderer.endRendering();

        if (yLabel != null) {
            textRenderer.begin3DRendering();
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glPushMatrix();

            Rectangle2D r = textRenderer.getBounds(yLabel);
            gl.glTranslated((-r.getHeight() * s) - ((yticks != null ? 10 : 0) * s), (sizeY / 2) - ((r.getWidth() / 2) * s), 0); // shift label left if there are ytick markers
            gl.glRotatef(90, 0, 0, 1);

            textRenderer.draw3D(yLabel, 0, 0, 0, s);
            textRenderer.end3DRendering();

            gl.glPopMatrix();

        }

        if (xticks != null) {
            textRenderer.beginRendering(getWidth(), getHeight());
            for (int tickLocation : xticks.keySet()) {
                String tickLabel = xticks.get(tickLocation);
                Rectangle2D r = textRenderer.getBounds(tickLabel);
                textRenderer.draw(tickLabel, (int) (((-clipArea.left / s) + (tickLocation / s)) - (r.getWidth() / 2)), (int) ((-clipArea.bottom / s) - r.getHeight()));
            }
            textRenderer.endRendering();
        }

        if (yticks != null) {
            textRenderer.beginRendering(getWidth(), getHeight());
            for (int tickLocation : yticks.keySet()) {
                String tickLabel = yticks.get(tickLocation);
                Rectangle2D r = textRenderer.getBounds(tickLabel);
                textRenderer.draw(tickLabel, (int) ((-clipArea.left / s) - r.getWidth()), (int) (((-clipArea.bottom / s) + (tickLocation / s)) - (r.getHeight() / 2)));
            }
            textRenderer.endRendering();
        }

        for (Legend legend : legends) {
            drawMultilineLegend(legend);
        }
        gl.glPopMatrix();
        checkGLError(gl, "after text");
    }

    /**
     * This object is the legend drawn starting at position x,y.
     */
    public class Legend {

        /**
         * The rendered multi-line string.
         */
        protected String legendString;
        /**
         * The location starting at 0,0 at lower left corner of image.
         */
        public float x, y;
        //        /** The drawn font size. */
        //        public int fontSize=ImageDisplay.this.fontSize;
        /**
         * The Legend font color.
         */
        protected float[] color = textColor;

        public Legend(String s, float x, float y) {
            this.legendString = s;
            this.x = x;
            this.y = y;
        }

        /**
         * @return the legendString
         */
        public String getLegendString() {
            return legendString;
        }

        /**
         * @param legendString the legendString to set
         */
        public void setLegendString(String legendString) {
            this.legendString = legendString;
        }

        public void setXY(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public void setPoint(Point2D.Float p) {
            this.x = p.x;
            this.y = p.y;
        }

        /**
         * @return the color
         */
        public float[] getColor() {
            return color;
        }

        /**
         * @param color the color to set
         */
        public void setColor(float[] color) {
            this.color = color;
        }
    }

    /**
     * Adds a multi-line string starting with UL corner at x,y image position,
     * using the text renderer font size. Each embedded '\n' starts a new line
     * of text. Each successive line of text after the first is indented with
     * two spaces.
     *
     * @param s the string to render; embed '\n' to start each new line of text.
     * @param x the x location in the image, 0 is the left of the image.
     * @param y the y location in the image, 0 is the bottom of the image.
     * @return the Legend object, which may be modified as desired.
     *
     */
    synchronized public Legend addLegend(String s, float x, float y) {
        Legend legend = new Legend(s, x, y);
        legends.add(legend);
        return legend;
    }

    /**
     * Removes Legend from the internal list
     *
     * @param legend the Legend to remove. If it is null or not in the list
     * nothing happens
     */
    synchronized void removeLegend(Legend legend) {
        if (legend == null) {
            return;
        }
        legends.remove(legend);
    }

    /**
     * Clears all the legend strings.
     *
     */
    synchronized public void clearLegends() {
        legends.clear();
    }

    /**
     * Resets the image to the gray value.
     */
    synchronized public void clearImage() {
        resetFrame(grayValue);
    }

    /**
     * Renders the string with newlines ('\n') starting at x,y image position
     * (with origin at lower left corner), using the text renderer font size.
     * Each embedded '\n' starts a new line of text. Each successive line of
     * text is indented with two spaces.
     *
     * @param s the string to render.
     * @param x the x location in the image, 0 is the left of the image.
     * @param y the y location in the image, 0 is the bottom of the image.
     */
    private void drawMultilineLegend(Legend legend) {
        if (textRenderer == null) {
            return;  // not visible yet, not displayed yet
        }
        final int additionalSpace = 2;
        String[] lines = legend.getLegendString().split("\n");
        if (lines == null) {
            return;
        }
        float yshift = 0;
        float scale = (clipArea.top - clipArea.bottom) / getHeight();  // TODO mysterious scaling of text

        try {
            textRenderer.beginRendering(getWidth(), getHeight());
            boolean first = true;
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                Rectangle2D r = textRenderer.getBounds(line);
                yshift -= r.getHeight(); // shifts down because origin is UL and y increases downwards according to Java 2D convention
                //                if (!first) { //these indents are not so cool
                //                    line = "  " + line;
                //                }
                first = false;
                textRenderer.setColor(legend.getColor()[0], legend.getColor()[1], legend.getColor()[2], 1);
                textRenderer.draw(line, (int) ((legend.x / scale) - (clipArea.left / scale)), Math.round(((legend.y / scale) - (clipArea.bottom / scale)) + yshift));
            }
            textRenderer.endRendering();
        } catch (GLException e) {
            log.warning("caught " + e + " when trying to render text into the current OpenGL context");
        }
        yshift -= additionalSpace;  // add additional space between multiline strings
    }

    /**
     * Returns the image position of a mouse event, translating back from OpenGL
     * coordinates to image location.
     *
     * @param evt a mouse event
     * @return the image location, with 0,0 at the lower left corner.
     *
     */
    public Point2D.Float getMouseImagePosition(MouseEvent evt) {
        Point2D.Float p = new Point2D.Float(evt.getPoint().x, evt.getPoint().y);
        float scale = (clipArea.top - clipArea.bottom) / getHeight();  // TODO mysterious scalling of text
        p.x = (p.x * scale) + clipArea.left;
        p.y = sizeY - ((p.y * scale) + clipArea.bottom);
        return p;
    }

    /**
     * Save image as PNG file
     *
     * @param filePath the File to save to
     */
    public void savePng(String filePath) throws IOException {
        final BufferedImage theImage = new BufferedImage(getSizeX(), getSizeY(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < getSizeY(); y++) {
            for (int x = 0; x < getSizeX(); x++) {
                float[] rgb = getPixmapRGB(x, y);
                final int value = ((int) (256 * rgb[0]) << 16) | ((int) (256 * rgb[1]) << 8) | (int) (256 * rgb[2]);
                theImage.setRGB(x, y, value);
            }
        }
        final Date d = new Date();
        final String PNG = ".png";
        if (!filePath.toLowerCase().endsWith(PNG)) {
            log.warning(String.format("Appending .png to %s", filePath));
            filePath = filePath + PNG;
        }

        File outputfile = new File(filePath);
//        boolean done = false;
//        while (!done) {
//            JFileChooser fd = new JFileChooser(outputfile);
//            fd.setApproveButtonText("Save as");
//            fd.setSelectedFile(outputfile);
//            fd.setVisible(true);
//            final int ret = fd.showOpenDialog(null);
//            if (ret != JFileChooser.APPROVE_OPTION) {
//                return;
//            }
//            outputfile = fd.getSelectedFile();
//            if (!FilenameUtils.isExtension(outputfile.getAbsolutePath(), PNG)) {
//                String ext = FilenameUtils.getExtension(outputfile.toString());
//                String newfile = outputfile.getAbsolutePath();
//                if (ext != null && !ext.isEmpty() && !ext.equals(PNG)) {
//                    newfile = outputfile.getAbsolutePath().replace(ext, PNG);
//                } else {
//                    newfile = newfile + "." + PNG;
//                }
//                outputfile = new File(newfile);
//            }
//            if (outputfile.exists()) {
//                int overwrite = JOptionPane.showConfirmDialog(fd, outputfile.toString() + " exists, overwrite?");
//                switch (overwrite) {
//                    case JOptionPane.OK_OPTION:
//                        done = true;
//                        break;
//                    case JOptionPane.CANCEL_OPTION:
//                        return;
//                    case JOptionPane.NO_OPTION:
//                        break;
//                }
//            } else {
//                done = true;
//            }
//        }
        ImageIO.write(theImage, "png", outputfile);
        log.info("wrote PNG " + outputfile);
//            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Wrote "+userDir+File.separator+fn, "Saved PNG image", JOptionPane.INFORMATION_MESSAGE);
    }

    private static int windowCount = 0;

    /**
     * Static method to make a test display
     */
    public static void makeAndRunNewTestImageDisplay() {

        Thread t = new Thread() {

            @Override
            public void run() {
                JFrame frame = new JFrame("ImageFrame " + windowCount);  // make a JFrame to hold it
                frame.setPreferredSize(new Dimension(400, 400));  // set the window size
                frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.X_AXIS));

                final ImageDisplay disp = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
//                final ImageDisplay disp2 = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
                int s = 10;
                disp.setPreferredSize(new Dimension(s, s));
//                disp2.setPreferredSize(new Dimension(s, s));
//                JPanel p1=new JPanel(), p2=new JPanel();
//                p1.add(disp); p2.add(disp2);
//                Dimension d=new Dimension(legendString,legendString);
//                p1.setPreferredSize(d);p2.setPreferredSize(d);

                frame.getContentPane().add(disp); // add the GLCanvas to the center of the window
//                frame.getContentPane().add(disp2); // add the GLCanvas to the center of the window
                frame.pack(); // otherwise it wont fill up the display

                final Point2D.Float mousePoint = new Point2D.Float();

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // closing the frame exits
                int sizex = 3, sizey = 3;  // used later to define image size
                disp.setImageSize(sizex, sizey); // set dimensions of image		disp.setxLabel("x label"); // add xaxis label and some tick markers
//                disp2.setImageSize(sizex, sizey); // set dimensions of image		disp.setxLabel("x label"); // add xaxis label and some tick markers
                disp.addXTick(0, "0");
                disp.addXTick(sizex, Integer.toString(sizex));
                disp.addXTick(sizey / 2, Integer.toString(sizey / 2));

                disp.setyLabel("y label"); // same for y axis
                disp.addYTick(0, "0");
                disp.addYTick(sizey, Integer.toString(sizey));
                disp.addYTick(sizey / 2, Integer.toString(sizey / 2));

                // Add a 3 line legend starting at pixel x=1, y=size (near UL corner).
                String mls = "Use mouse to position this multiline label\nUse arrow keys to resize array\nF/shift-F change font size\n'g' sets the array gray\n'n' makes a new display\nctl-W closes display, ESC/x exits";
                Legend legend = disp.addLegend(mls, 1, sizey);  // drawa a multiline string - only do this once!  Or clear the list each time.
                legend.setColor(new float[]{1, 0, 0});

                disp.setTextColor(new float[]{.8f, 1, 1});
//                disp2.setTextColor(new float[]{.8f, 1, 1});
                new ImageDisplayTestKeyMouseHandler(disp, mousePoint, this);

                Random r = new Random();  // will use to fill display with noise

                int frameCounter = 0; // iterate over frame updates
                int n;
                float[] f; // get reference to pixmap array so we can set pixel values
                int sx, sy, xx, yy;
                frame.setVisible(true); // make the frame visible
                log.fine("starting main loop");
                while (!isInterrupted()) {
                    log.log(Level.FINER, "frame counter {0}", frameCounter);
                    disp.checkPixmapAllocation(); // make sure we have a pixmaps (not resally necessary since setting size will allocate pixmap
//                    disp2.checkPixmapAllocation(); // make sure we have a pixmaps (not resally necessary since setting size will allocate pixmap
                    n = sizex * sizey;
                    f = disp.getPixmapArray(); // get reference to pixmap array so we can set pixel values
                    sx = disp.getSizeX();
                    sy = disp.getSizeY();
                    // randomly update all pixels
                    //                for (int x = 0; x < sx; x++) {
                    //                    for (int y = 0; y < sy; y++) {
                    //                        int ind = imageDisplay.getPixMapIndex(x, y);
                    //                        f[ind + 0] = r.nextFloat();
                    //                        f[ind + 1] = r.nextFloat();
                    //                        f[ind + 2] = r.nextFloat();
                    //                    }
                    //                }
                    // randomly select one color of one pixel to change
                    //                int ind = disp.getPixMapIndex(r.nextInt(disp.getSizeX()), r.nextInt(disp.getSizeY())) + r.nextInt(3);
                    //                f[ind] = r.nextFloat();
                    //
                    // randomly set one pixel to some gray level
                    //            disp.resetFrame(0);
                    //            int xx = r.nextInt(disp.getSizeX());
                    //            int yy = r.nextInt(disp.getSizeY());
                    //            disp.setPixmapGray(xx, yy, r.nextFloat());

                    // clear frame to black
                    //            disp.resetFrame(0);
                    // randomly set a pixel to some RGB value
                    xx = r.nextInt(disp.getSizeX());
                    yy = r.nextInt(disp.getSizeY());
                    float red = r.nextFloat(), green = r.nextFloat(), blue = r.nextFloat();
                    if (xx == 0 && yy == 0) {
                        red = 1;
                        green = 1;
                        blue = 1;
                    }
                    disp.setPixmapRGB(xx, yy, red, green, blue);
//                    disp2.setPixmapRGB(xx, yy, r.nextFloat(), r.nextFloat(), r.nextFloat());

                    // move the legend around sinusoidally
                    //            double phase = Math.PI * 2 * (frameCounter % 1000000) / 1000000;
                    //            legend.x = (int) (disp.getSizeX() * .5 * (1 + Math.cos(phase)));
                    //            legend.y = (int) (disp.getSizeX() * .5 * (1 + Math.sin(phase)));
                    // mouse the legend to the mouse point
                    legend.x = (mousePoint.x);
                    legend.y = (mousePoint.y);

                    disp.setTitleLabel("Frame " + (frameCounter++));
//                    disp2.setTitleLabel("Frame " + (frameCounter++));

                    // ask for a repaint
                    disp.repaint();
//                    disp2.repaint(100);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        interrupt();
                    }

                }
                frame.dispose();
                windowCount--;
                if (windowCount == 0) {
                    System.exit(0);
                }
            }

        };
        t.start();
        windowCount++;

    }

    /////////////////////////////////////////////////////////////////////
    /**
     * Displays some random noise.
     *
     * @param args - no effect.
     */
    public static void main(String[] args) {
        final WindowSaver windowSaver = new WindowSaver(null, prefs);
        Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver, AWTEvent.WINDOW_EVENT_MASK); // adds windowSaver as JVM-wide event handler for window events
        makeAndRunNewTestImageDisplay();
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                log.info("shutdown hook - saving window settings");
                if (windowSaver != null) {
                    try {
                        windowSaver.saveSettings();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void dispose(GLAutoDrawable arg0) {
        // TODO Auto-generated method stub

    }

    /**
     * @return the grayValue
     */
    public float getGrayValue() {
        return grayValue;
    }

    /**
     * Sets the value that image is cleared to as a gray value
     *
     * @param grayValue the grayValue to set
     */
    public void setGrayValue(float grayValue) {
        this.grayValue = grayValue;
    }

}
