/*
 * ChipCanvas.java
 *
 * Created on May 2, 2006, 1:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import com.jogamp.common.nio.Buffers;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.util.logging.Level;
import net.sf.jaer.graphics.AEViewer.PlayMode;

/**
 * Superclass for classes that paint rendered AE data to graphics devices.
 * <p>
 * This is Canvas (AWT component) for rendering retina events. Note this is a
 * heavyweight component and doesn't understand Swing layering. Thus care must
 * be taken to ensure Swing components show over it. This component also uses a
 * page-flipping BufferStrategy to eliminate tearing by flipping pages on
 * monitor refresh.
 * <p>
 * The graphics context is obtained here in the component and its origin is the
 * LL corner. The context is translated to center the rendering in the Canvas.
 * This glCanvas can use OpenGL to render, and it can either actively or
 * passively render.
 * <p>
 * If 3-d event rendering is enabled, the raw events are painted out in
 * space-time, with time axis defined by duration from first to last event in
 * the packet.
 * <p>
 * If 3-d histogramming is enabled, then the image frame from the ChipRenderer
 * is rendered as a 3-d surface that the user can rotate as desired.
 *
 * @author tobi
 */
public class ChipCanvas implements GLEventListener, Observer {

    protected Preferences prefs; // set in constructor to be chip prefs
    /**
     * Default scaling from chip pixel to screen pixels
     */
    protected static final float SCALE_DEFAULT = 4f;
    protected AEViewer aeViewer;
    /**
     * angle in degrees around x axis of 3d display
     */
    protected float anglex;
    /**
     * angle in degrees around y axis of 3d display
     */
    protected float angley;
    private Chip2D chip;
    protected final int colorScale = 255;
    protected GLCanvas glCanvas;
    protected GLU glu; // instance this if we need glu calls on context
    protected GLUT glut = null;
    protected Logger log = Logger.getLogger("net.sf.jaer");
    private float origin3dx;
    private float origin3dy;
    /**
     * defines the minimum glCanvas size in pixels; used when chip size has not
     * been set to non zero value
     */
    public static final int MIN_DIMENSION = 70;
    /**
     * width and height of pixel array in canvas in screen pixels. these are
     * different than the actual canvas size
     */
    protected int pwidth;

    protected static final Color selectedPixelColor = Color.blue;
    protected GLUquadric selectedQuad;
    public BufferStrategy strategy;
    /**
     * the translation of the actual chip drawing area in the glCanvas, in
     * screen pixels
     */
    protected int xt,
            /**
             * the translation of the actual chip drawing area in the glCanvas,
             * in screen pixels
             */
            yt;
    private ChipCanvas.Zoom zoom = createZoom();
    // reused imageOpenGL for OpenGL image grab
    private BufferedImage imageOpenGL;
    // boolean to flag that present opengl display call should write imageOpenGL
    volatile boolean grabNextImageEnabled = false;
    /**
     * border around drawn pixel array in screen pixels
     */
    private int borderSpacePixels;
    /**
     * border in screen pixels when in 3d space-time rendering mode
     */
    protected final int BORDER3D = 70;
    /**
     * Insets of the drawn chip glCanvas in the window in screen (not chip)
     * pixels
     */
    private Insets insets = new Insets(borderSpacePixels, borderSpacePixels, borderSpacePixels, borderSpacePixels),
            insetsZero = new Insets(0, 0, 0, 0);
    private boolean fillsHorizontally = false, fillsVertically = false; // filled in in the reshape method to show how
    // chip fills glCanvas space
    private float ZCLIP = 1;
    private TextRenderer renderer = null;

    private Point mdStPt = null; // start point of drag in screen coordinates
    private Vec drStPx = null; // start point of drag in px, arb origin
    private Point origin3dMouseDragStartPoint = new Point(0, 0);

    /**
     * Flag to disable annotation for methods such as data file preview in file
     * choosers
     */
    protected boolean annotationEnabled = true;

    /**
     * Creates a new instance of ChipCanvas
     */
    public ChipCanvas(final Chip2D chip) {
        this.chip = chip;
        this.prefs = chip.getPrefs();
        setBorderSpacePixels(prefs.getInt("borderSpacePixels", 20));
        anglex = prefs.getFloat("ChipCanvas.anglex", 5);
        angley = prefs.getFloat("ChipCanvas.angley", 10);
        origin3dx = prefs.getInt("ChipCanvas.origin3dx", 0);
        origin3dy = prefs.getInt("ChipCanvas.origin3dy", 0);
        pwidth = prefs.getInt("ChipCanvas.pwidth", 512);

        // make the glCanvas
        try {
//            final GLProfile glp = GLProfile.getMaxProgrammable(true);//GLProfile.getDefault(); //getGL2ES1(); // getMaxProgrammable(true);// FixedFunc(true);
//            final GLProfile glp = GLProfile.getGL2ES1(); // getMaxProgrammable(true);// FixedFunc(true);
//            final GLProfile glp = GLProfile.get(GLProfile.GL2); // getMaxProgrammable(true);// FixedFunc(true);
//            final GLProfile glp = GLProfile.get(GLProfile.GL3bc); // getMaxProgrammable(true);// FixedFunc(true);
            log.info("""
                     Getting GLProfile with GLProfile.getDefault()
                      If this throws access violotion outside the JVM, and in atio6axx.dll driver, then it may be your AMD graphics driver.
                      If you are running dual display on laptop, try starting with only main laptop display.
                      Try to enable use of discrete Nvidia GPU.""");
            final GLProfile glp = GLProfile.getDefault();
//            final GLProfile glp = GLProfile.get(GLProfile.GL2ES2);
            final GLCapabilities caps = new GLCapabilities(glp);
            caps.setDoubleBuffered(true);
            glCanvas = new GLCanvas(caps);
            glCanvas.setAutoSwapBufferMode(true);
//            if (SystemUtils.IS_OS_WINDOWS) {
//                List<GLCapabilitiesImmutable> capsAvailable = GLDrawableFactory.getDesktopFactory()
//                        .getAvailableCapabilities(null);
//                GLCapabilitiesImmutable chosenGLCaps = null;
//                int listnum = 0;
//                if (capsAvailable != null) {
//                    for (GLCapabilitiesImmutable cap : capsAvailable) {
//                        log.info("GLCapabilitiesImmutable #" + listnum + " is " + cap.toString());
//                        if (chosenGLCaps == null) {
//                            chosenGLCaps = cap;
//                        }
//                        if (listnum++ >= 0) {
//                            break;
//                        }
//                    }
//                }
//
//                glCanvas = new GLCanvas(chosenGLCaps);
//            } else {
//                glCanvas = new GLCanvas();
//            }

            if (glCanvas == null) {
                // Failed to init OpenGL, exit system!
                System.exit(1);
            }

            /*
             * got following under VirtualBox Ubuntu guest running on tobi's W510 Win7x64SP1 machine
             * INFO: OpenGL implementation is: com.sun.opengl.impl.GLImpl
             * GL_VENDOR: Humper
             * GL_RENDERER: Chromium
             * GL_VERSION: 2.1 Chromium 1.9
             * OpenGL Warning: No pincher, please call crStateSetCurrentPointers() in your SPU
             */
        } catch (final UnsatisfiedLinkError e) {
            e.printStackTrace();
            System.err.println("java.libary.path=" + System.getProperty("java.library.path"));
            System.err.println("user.dir=" + System.getProperty("user.dir"));
            System.exit(1);
        }
        try {
            glut = new GLUT();
            glu = new GLU();
        } catch (final NoClassDefFoundError err) {
            log.warning("Could not construct GLUT object as the OpenGL utilities helper. There is some problem with your JOGL libraries."
                    + "It could be that you are trying to run a 64-bit JVM under linux. Try using a 32-bit Java Virtual Machine (JVM) instead, because"
                    + "the jAER JOGL native libraries are built for 32-bit JVMs.");
            throw err;
        }
        glCanvas.setLocale(Locale.US); // to avoid problems with other language support in JOGL
        glCanvas.setVisible(true);
        // will always getString invalid operation here
        // checkGLError(glCanvas.getGL(),glu,"before add event listener");
        // add us as listeners for the glCanvas. then when the display wants to redraw display() will be called. or we can
        // call glCanvas.display();
        glCanvas.addGLEventListener(this);
        glCanvas.setSize(200, 200); // set a size explicitly here
        initComponents();
        chip.addObserver(this);

        // if this glCanvas was constructed from a chip, then fill the display methods from that chip's ChipCanvas, if it
        // exists and has them
        if (displayMethods.isEmpty() && (chip.getCanvas() != null) && (chip.getCanvas().getDisplayMethod() != null)) {
            displayMethods.add(chip.getCanvas().getDisplayMethod());
        }

        if (glCanvas != null) {
            log.info("GLCanvas=" + glCanvas.toString());
            if (glCanvas.getContext() != null) {
                log.info("GLCanvas has GLContext=" + glCanvas.getContext().toString());
            }
        }

        glCanvas.setFocusable(true);
        // for debugging bug where context was grabbed by getMousePoint and not released
//                glCanvas.addFocusListener(new FocusListener() {
//
//                    @Override
//                    public void focusGained(FocusEvent e) {
//                        log.info("focus gained");
//
//                    }
//
//                    @Override
//                    public void focusLost(FocusEvent e) {
//                        log.info("focus lost");
//                    }
//                });
    }

    /**
     * call this method so that next open gl rendering by
     * display(GLAutoDrawable) writes imageOpenGL
     */
    public void grabNextImage() {
        grabNextImageEnabled = true;
    } // here are methods for toggling display method and building menu for AEViewer

    private final JMenu displayMethodMenu = new JMenu("Display method");
    private final ButtonGroup menuButtonGroup = new ButtonGroup();

    public JMenu getDisplayMethodMenu() {
        return displayMethodMenu;
    }

    /**
     * the list of display methods for this glCanvas
     */
    private ArrayList<DisplayMethod> displayMethods = new ArrayList<DisplayMethod>();

    /**
     * adds a display method to this glCanvas
     *
     * @param m the method
     */
    public void addDisplayMethod(final DisplayMethod m) {
        if (getDisplayMethods().contains(m)) {
            return;
        }
        getDisplayMethods().add(m);
        displayMethodMenu.getPopupMenu().setLightWeightPopupEnabled(false); // glCanvas is heavyweight so we need this to
        // make menu popup show
        final JRadioButtonMenuItem mi = new JRadioButtonMenuItem(m.getDescription());
        m.setMenuItem(mi);
        mi.setActionCommand(m.getDescription());
        mi.putClientProperty("displayMethod", m); // add display method as client property
        mi.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                final DisplayMethod m = (DisplayMethod) (((JComponent) e.getSource())
                        .getClientProperty("displayMethod"));
                if (chip != null) {
                    chip.setPreferredDisplayMethod(m.getClass()); // only set preference on user selection via GUI
                }
                setDisplayMethod(m);
            }
        });
        displayMethodMenu.add(mi);
        menuButtonGroup.add(mi);
        // if there is no default method yet, set one to be sure that a method is set, to avoid null pointer exceptions
        if (displayMethod == null) {
            setDisplayMethod(m);
        }
    }

    /**
     * Removes a DisplayMethod for this ChipCanvas
     *
     * @param m the method. If null or not in list, warning is logged.
     */
    public void removeDisplayMethod(final DisplayMethod m) {
        if ((m == null) || !getDisplayMethods().contains(m)) {
            log.warning("Cannot remove DisplayMethod " + m + ": no such DisplayMethod " + m
                    + " in the getDisplayMethods() list");
            return;
        }
        displayMethods.remove(m);
        displayMethodMenu.remove(m.getMenuItem());
    }

    protected DisplayMethod displayMethod;

    /**
     * returns the current display method
     *
     * @return the current display method
     */
    public DisplayMethod getDisplayMethod() {
        return displayMethod;
    }

    /**
     * cycle to the next display method
     */
    public void cycleDisplayMethod() {
        // find index of current display method
        int idx = 0;
        for (DisplayMethod m : getDisplayMethods()) {
            if (m == getDisplayMethod()) {
                break;
            }
            idx++;
        }
        idx++;
        if (idx >= getDisplayMethods().size()) {
            idx = 0;
        }
        setDisplayMethod(idx);
    }

    /**
     * sets the display method
     *
     * @param m the method to use
     */
    public void setDisplayMethod(final DisplayMethod m) {
        // System.out.println("set display method="+m);
        // Thread.currentThread().dumpStack();

        if ((displayMethod != null) && (m != null) && (m != displayMethod)) {
            displayMethod.onDeregistration();
        }
        displayMethod = m;
        if (m != null) {
            if (m.getMenuItem() == null) {
                // TODO comes here when preferred display method is constructed, which is not yet assigned a menu item
                // like the others
                // which are constructed by default
//                log.info("no menu item for display method " + m
//                        + " cannot set it as the selected display method in the menu");
            } else {
                // log.info("setting display method to " + m.getDescription());
                m.getMenuItem().setSelected(true);
            }
            m.onRegistration();
        }
        repaint();
    }

    /**
     * sets the display method using the menu name
     *
     * @param description the name, fully qualified class name or simple class
     * name; only the last part is used, after the final ".".
     */
    public void setDisplayMethod(final String description) {
        for (final DisplayMethod method : getDisplayMethods()) {
            String s = description;
            final int indDot = s.lastIndexOf('.');
            final int indDollar = s.lastIndexOf('$');
            String sDot = s.substring(indDot + 1);
            String sDollar = s.substring(indDollar + 1);
            if (method.getDescription().equals(sDot) || method.getDescription().equalsIgnoreCase(sDollar)) {
                // log.info("setting display method=" + m);
                setDisplayMethod(method);
                return;
            }
        }
        final StringBuilder sb = new StringBuilder("couldn't set display method to " + description
                + ", not in list of methods which are as follows:\n");
        for (final DisplayMethod method : getDisplayMethods()) {
            sb.append(method.getDescription() + "\n");
        }
        log.warning(sb.toString());
    }

    /**
     * sets the current display method to a particular item
     *
     * @param index the index of the method in the list of methods
     */
    protected void setDisplayMethod(final int index) {
        setDisplayMethod(getDisplayMethods().get(index));
    }

    /**
     * OpenGL calls this when it wants to redraw, and we call it when we
     * actively render.
     *
     * @param drawable the surface from which we can get the context with getGL
     * @see net.sf.jaer.graphics.DisplayMethod#setupGL which sets up GL context
     * for display methods
     */
    @Override
    public void display(final GLAutoDrawable drawable) {
        final GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());

        checkGLError(gl, glu, "start of display");

        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();
        // log.info("display");

        checkGLError(gl, glu, "before setting projection");
        // gl.glPushMatrix(); // don't push so that mouse selection has correct matrices

        getZoom().applyProjection(gl);
        checkGLError(gl, glu, "after setting projection, before displayMethod");
        final DisplayMethod m = getDisplayMethod();
        if (m == null) {
            log.warning("null display method for chip " + getChip());
        } else {
            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
            gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            m.display(drawable);
//                glCanvas.swapBuffers();
//                glCanvas.swapBuffers();
        }
        // checkGLError(gl, glu, "after " + getDisplayMethod() + ".display()");
        checkGLError(gl, glu, "after DisplayMethod.display()");
        showSpike(gl);
        annotate(drawable);
        checkGLError(gl, glu, "after FrameAnnotator (EventFilter) annotations");
        if ((getChip() instanceof AEChip) && (((AEChip) chip).getFilterChain() != null)
                && (((AEChip) chip).getFilterChain().getProcessingMode() == FilterChain.ProcessingMode.ACQUISITION)) {
            if (renderer == null) {
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
                renderer.setUseVertexArrays(false);
            }
            renderer.begin3DRendering();
            renderer.setColor(0, 0, 1, 0.8f);
            final String s = "Real-time mode - raw data shown here";
            final Rectangle2D r = renderer.getBounds(s);
            renderer.draw3D(s, 1f, 1f, 0f, (float) (chip.getSizeX() / 2 / r.getWidth()));
            renderer.end3DRendering();
        }
        gl.glFlush();
        // gl.glPopMatrix();
        checkGLError(gl, glu, "after display");
        // glCanvas.swapBuffers(); // don't use, very slow and autoswapbuffers is set
        // zoom.drawZoomBox(gl);
        if (grabNextImageEnabled) {
            grabImage(drawable);
            grabNextImageEnabled = false;
        }
    }

    float[] rgbVec = new float[3];

    public void displayChanged(final GLAutoDrawable drawable, final boolean modeChanged, final boolean deviceChanged) {
        log.info("display changed");
//		 should be empty according to jogl user guide.
    }

    /**
     * angle in degrees around x axis of 3d display, negative when rotated
     * upwards
     */
    public float getAnglex() {
        return anglex;
    }

    /**
     * angle in degrees around y axis of 3d display, negative when rotated
     * leftwards
     */
    public float getAngley() {
        return angley;
    }

    /**
     * The actual drawing surface is a Canvas and this method returns a
     * reference to it.
     *
     * @return the actual drawing Canvas.
     */
    public Canvas getCanvas() {
        return glCanvas;
    }

    /**
     * Pixel drawing scale. 1 chip pixel is rendered to getScale() screen
     * pixels. To obtain chip pixel units from screen pixels, divide screen
     * pixels by <code>getScale()</code>. Conversely, to scale chip pixels to
     * screen pixels, multiply by <code>getScale()</code>.
     *
     * @return scale in screen pixels/chip pixel.
     */
    public float getScale() {
        return getZoom().getClipArea().getScale();
    }

    /**
     * A utility method that returns an AWT Color from float rgb values
     */
    protected final Color getPixelColor(final float red, final float green, final float blue) {
        final int r = (int) (colorScale * red);
        final int g = (int) (colorScale * green);
        final int b = (int) (colorScale * blue);
        final int value = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0);
        return new Color(value);
    }

    private boolean mouseWasInsideChipBounds = true;

    /**
     * Finds the current AEChip pixel mouse position, or center of array if not
     * inside.
     *
     * @return the AEChip pixel, clipped to the bounds of the AEChip
     */
    public Point getMousePixel() {
        final Point mp = getCanvas().getMousePosition();
        if (mp == null) {
            return null;
        }
        return getPixelFromMousePoint(mp);
    }

    /**
     * Returns state of mouse from last call to getChipPixelFromMousePoint; true
     * if mouse inside bounds of chip drawing area.
     *
     * @return true if was inside, false otherwise.
     */
    public boolean wasMousePixelInsideChipBounds() {
        return mouseWasInsideChipBounds;
    }

    /**
     * Takes a MouseEvent and returns the AEChip pixel.
     *
     * @return pixel x,y location (integer point) from MouseEvent. Accounts for
     * scaling and borders of chip display area. Always returns nearest pixel.
     */
    public Point getPixelFromMouseEvent(final MouseEvent evt) {
        final Point mp = evt.getPoint();
        if (mp == null) {
            return null;
        }
        Point p = getPixelFromMousePoint(mp);
        clipPoint(p);
        return p;
    }

    /**
     * Takes a MouseEvent and returns the AEChip pixel.
     *
     * @return pixel x,y location (integer point) from MouseEvent. Accounts for
     * scaling and borders of chip display area. Can be outside chip boundaries.
     */
    private Point getPixelFromMouseEventUnclipped(final MouseEvent evt) {
        final Point mp = evt.getPoint();
        if (mp == null) {
            return null;
        }
        Point p = getPixelFromMousePoint(mp);
        return p;
    }

    /**
     * Finds the chip pixel from a ChipCanvas point. From
     * <a href="http://processing.org/discourse/yabb_beta/YaBB.cgi?board=OpenGL;action=display;num=1176483247">this
     * forum link</a>.
     *
     * @param mp a Point in ChipCanvas (i.e. screen) pixels.
     * @return the AEChip pixel (not clipped to chip boundaries) or null if not
     * valid.
     */
    private Point getPixelFromMousePoint(final Point mp) {
        getZoom().computeMouseFrac(mp);

        if (mp == null) {
            return null;
        }
//        // May 2021, Tobi changed to use simpler clipArea object along with chip size.
//        // Former method using all the matrices was just too cryptic to understand
        int x = (int) ((mp.getX() / getScale()) + getClipArea().getLeft());
        int y = (int) (((getCanvas().getHeight() - mp.getY()) / getScale()) + getClipArea().getBottom());
//
        final Point p = new Point(x, y);
        mouseWasInsideChipBounds = !((p.x < 0) || (p.x > (chip.getSizeX() - 1)) || ((p.y < 0) | (p.y > (chip.getSizeY() - 1))));
        log.fine(String.format("chip pixel=%s mouseFrac=%s", p, getZoom().mouseFrac));
        return p;

//        final double wcoord[] = new double[3];// wx, wy, wz;// returned xyz coords
//        // this method depends on current GL context being the one that is used for rendering.
//        // the display method should not push/pop the matrix stacks!!
////        synchronized (glCanvas.getTreeLock()) {
//        try {
//            if (hasAppleRetinaDisplay()) {
//                mp.x *= 2;
//                mp.y *= 2;
//            }
//            final int ret = glCanvas.getContext().makeCurrent();
//            if (ret != GLContext.CONTEXT_CURRENT) {
//                throw new GLException("couldn't make context current");
//            }
//
//            final int viewport[] = new int[4];
//            final double mvmatrix[] = new double[16];
//            final double projmatrix[] = new double[16];
//            int realy = 0;// GL y coord pos
//            // set up a floatbuffer to get the depth buffer value of the mouse position
//            final GL2 gl = glCanvas.getContext().getGL().getGL2();
//            checkGLError(gl, glu, "before getting mouse point");
//            gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
//            gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX, mvmatrix, 0);
//            gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, projmatrix, 0);
//            /* note viewport[3] is height of window in pixels */
//            realy = viewport[3] - (int) mp.getY() - 1;
//            // Get the depth buffer value at the mouse position. have to do height-mouseY, as GL puts 0,0 in the bottom
//            // left, not top left.
//            checkGLError(gl, glu, "after getting modelview and projection matrices in getMousePoint");
//            final FloatBuffer fb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
//            gl.glReadPixels(mp.x, realy, 1, 1, GL2.GL_DEPTH_COMPONENT, GL.GL_FLOAT, fb);
////                checkGLError(gl, glu, "after readPixels in getMousePoint");
//            final float z = fb.get(0); // assume we want z=0 value of mouse point
//            glu.gluUnProject(mp.getX(), realy, z, mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
//            checkGLError(gl, glu, "after gluUnProject in getting mouse point");
//            final Point p = new Point();
//            p.x = (int) Math.round(wcoord[0]);
//            p.y = (int) Math.round(wcoord[1]);
//            if ((p.x < 0) || (p.x > (chip.getSizeX() - 1)) || ((p.y < 0) | (p.y > (chip.getSizeY() - 1)))) {
//                mouseWasInsideChipBounds = false;
//            } else {
//                mouseWasInsideChipBounds = true;
//            }
//            log.info("Mouse xy=" + mp.getX() + "," + mp.getY() + "  realy=" + realy + "   Pixel x,y=" + p.x + "," + p.y + " mouseWasInsideChipBounds=" + mouseWasInsideChipBounds);
////            clipPoint(p);
//
//            return p;
//        } catch (final GLException e) {
//            log.warning("couldn't make GL context current, mouse position meaningless: " + e.toString());
//        } finally {
//            if (glCanvas.getContext().isCurrent()) {
//                glCanvas.getContext().release();
//            }
//        }
//        return null;
//   
    }

    protected final int getPixelRGB(final float red, final float green, final float blue) {
        final int r = (int) (colorScale * red);
        final int g = (int) (colorScale * green);
        final int b = (int) (colorScale * blue);
        final int value = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0);
        return value;
    }

    @Override
    public void init(final GLAutoDrawable drawable) {
        // glCanvas.setGL(new DebugGL(glCanvas.getGL()));
        GL2 gl = null;
        try {
            gl = drawable.getGL().getGL2();
        } catch (com.jogamp.opengl.GLException e) {
            log.log(Level.SEVERE, "Cannot make a frame that is GL2 capable; you might need to update your graphics driver", e);
            return;
        }

        log.info("OpenGL implementation is: " + gl.getClass().getName() + "\nGL_VENDOR: "
                + gl.glGetString(GL.GL_VENDOR) + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER) + "\nGL_VERSION: "
                + gl.glGetString(GL.GL_VERSION) // + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
        );
        final float glVersion = Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0, 3));
        if (glVersion < 1.3f) {
            log.warning("\n\n*******************\nOpenGL version "
                    + glVersion
                    + " < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
        }
        // System.out.println("GLU_EXTENSIONS: "+glu.gluGetString(GLU.GLU_EXTENSIONS));

//        gl.setSwapInterval(0);
        gl.glShadeModel(GLLightingFunc.GL_FLAT);

        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "Initialized display");
        checkGLError(gl, glu, "after init");
    }

    protected void initComponents() {
        if (getRenderer() != null) {
            final ZoomMouseAdaptor zoomMouseAdaptor = new ZoomMouseAdaptor();
            glCanvas.addMouseListener(zoomMouseAdaptor);
            glCanvas.addMouseMotionListener(zoomMouseAdaptor);
        }
    }

    public boolean is3DEnabled() {
        return displayMethod instanceof DisplayMethod3D;
    }

    /**
     * Used for active rendering. You call this when you want to actively render
     * the frame. Internally, this calls the display() method of the drawable,
     * which by callback to display(GLAutoDrawable). If openGL is disabled, then
     * it calls paint() directly.
     *
     * @see #display(com.jogamp.opengl.GLAutoDrawable)
     */
    public void paintFrame() {
//        synchronized (glCanvas.getTreeLock()) {
        try {
//                glCanvas.getContext().makeCurrent();
            glCanvas.display(); // we call the glCanvas's display method that ends up calling us back via our local
            // display(GLAutoDrawable)!! very important to get this right
        } catch (final GLException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                log.warning(e.toString());
            }
        } catch (final RuntimeException ie) {
            if (!(ie.getCause() instanceof InterruptedException)) {
                log.warning(ie.toString());
                ie.printStackTrace();
            }
        } finally {
//                glCanvas.getContext().release();
        }
//        }
    }

    public void removeGLEventListener(final GLEventListener listener) {
    }

    public void addGLEventListener(final GLEventListener listener) {
        System.out.println("addGLEventListener(" + listener + ")");
    }

    /**
     * calls repaint on the glCanvas
     */
    public void repaint() {
        glCanvas.repaint();
    }

    /**
     * calls repaint on the glCanvas
     *
     * @param tm time to repaint within, in ms
     */
    public void repaint(final long tm) {
        glCanvas.repaint(tm);
    }

    /**
     * Called on reshape of canvas. Determines which way the chip fits into the
     * display area optimally and calls for a new orthographic projection to
     * achieve this filling. Finally sets the viewport to the entire drawable
     * area.
     */
    @Override
    public void reshape(final GLAutoDrawable drawable, final int x, final int y, final int width, final int height) {
        final GL2 gl = drawable.getGL().getGL2();
        getClipArea().setDirty();
        getZoom().applyProjection(gl);
        gl.glViewport(0, 0, width, height);
//        checkGLError(gl, glu, "at start of reshape");
//
//        gl.glLoadIdentity();
//        final int chipSizeX = chip.getSizeX();
//        final int chipSizeY = chip.getSizeY();
//        float newscale;
//        final float border = 0; // getBorderSpacePixels()*(float)height/width;
//        if (chipSizeY > chipSizeX) {
//            // chip is tall and skinny, so set scaleChipPixels2ScreenPixels by frame height/chip height
//            newscale = (height - border) / chipSizeY;
//            fillsVertically = true;
//            fillsHorizontally = false;
//            if ((newscale * chipSizeX) > width) { // unless it runs into left/right, then set to fill width
////                newscale = (width - border) / chipSizeX;
//                fillsHorizontally = true;
//                fillsVertically = false;
//            }
//        } else {
//            // chip is square or squat, so set scaleChipPixels2ScreenPixels by frame width / chip width
//            newscale = (width - border) / chipSizeX;
//            fillsHorizontally = true;
//            fillsVertically = false;
//            if ((newscale * chipSizeY) > height) {// unless it runs into top/bottom, then set to fill height
////                newscale = (height - border) / chipSizeY;
//                fillsVertically = true;
//                fillsHorizontally = false;
//            }
//        }
//        checkGLError(gl, glu, "before setDefaultProjection in reshape");
        repaint();
    }

    protected void set3dOrigin(final int x, final int y) {
        setOrigin3dy(y);
        setOrigin3dx(x);
        prefs.putInt("ChipCanvas.origin3dx", x);
        prefs.putInt("ChipCanvas.origin3dy", y);
    }

    /**
     * angle in degrees around x axis of 3d display
     *
     * @param anglex
     */
    public void setAnglex(final float anglex) {
        this.anglex = anglex;
        prefs.putFloat("ChipCanvas.anglex", anglex);
    }

    /**
     * angle in degrees around y axis of 3d display
     *
     * @param angley
     */
    public void setAngley(final float angley) {
        this.angley = angley;
        prefs.putFloat("ChipCanvas.angley", angley);
    }

    /**
     * Returns the actual graphics output view clipping area in model space
     * (chip pixel) coordinates. This clipping area is around the chip size by a
     * negative amount for left and bottom and a quantity larger than the chip
     * size for the right and top.
     *
     * This method is not synchronized.
     *
     * @return the clip area; only valid after display has been called.
     * @see #setDefaultProjection(com.jogamp.opengl.GL,
     * com.jogamp.opengl.GLAutoDrawable)
     */
    public Zoom.ClipArea getClipArea() {
        return getZoom().getClipArea();
    }

//    /**
//     * Border around chip in model space coordinate (chip pixels).
//     *
//     * @see #setDefaultProjection(com.jogamp.opengl.GL,
//     * com.jogamp.opengl.GLAutoDrawable)
//     * @see #getScale()
//     */
//    public class Borders {
//
//        /**
//         * Border space in chip pixel units around left/right and bottom/top of
//         * displayed chip pixel array. The total border is twice the individual
//         * <code>leftRight</code> or <code>bottomTop</code>.
//         */
//        public float leftRight = 0, bottomTop = 0;
//
//        public void set(float leftRight, float bottomTop) {
//            this.leftRight = leftRight;
//            this.bottomTop = bottomTop;
//        }
//    };
//
//    /**
//     * The actual borders in model space around the chip area.
//     */
//    private final Borders borders = new Borders();
//
//    /**
//     * Returns the actual borders in model space (chip pixels) around the drawn
//     * pixel array.
//     *
//     * This method is not synchronized.
//     *
//     * @return the borders; only valid after display has been called.
//     * @see #setDefaultProjection(com.jogamp.opengl.GL,
//     * com.jogamp.opengl.GLAutoDrawable)
//     */
//    public Borders getBorders() {
//        return borders;
//    }
//    /**
//     * Sets the projection matrix so that we get an orthographic projection that
//     * is the size of the glCanvas with z volume -ZCLIP to ZCLIP padded with extra
//     * space around the sides.
//     *
//     * @param g the GL context
//     * @param d the GLAutoDrawable glCanvas
//     */
//    protected void applyProjection(final GL2 g, final GLAutoDrawable d) {
//        /*
//         * void glOrtho(GLdouble left,
//         * GLdouble right,
//         * GLdouble bottom,
//         * GLdouble top,
//         * GLdouble near,
//         * GLdouble far)
//         */
//        checkGLError(g, glu, "at start of setDefaultProjection");
//        final int w = glCanvas.getWidth(), h = glCanvas.getHeight(); // w,h of screen
//
//        final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
//
//        if (sx == 0 || sy == 0) {
//            log.warning("zero sized chip for sizeX or sizeY; something is wrong. Did you forget to set the chip size in pixels in its constructor?");
//            return;
//        }
//        getZoom().applyZoomProjection(g);
////        checkGLError(g, glu, "before applyDefaultProjection");
////        g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
////        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
////        g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
////        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
////
////        getClipArea().computeUnzoomedClipAreaAndScale();
////        g.glOrthof(getClipArea().left, getClipArea().right, getClipArea().bottom, getClipArea().top, ZCLIP, -ZCLIP);
////
////        g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
////        log.info("set defaultprojection with clipArea=" + clipArea);
//    }
    /**
     * Shows selected pixel spike count by drawn circle
     */
    protected void showSpike(final GL2 gl) { // show selected pixel that user can hear
        if ((getRenderer() != null) && (getRenderer().getXsel() != -1) && (getRenderer().getYsel() != -1)) {
            showSpike(gl, getRenderer().getXsel(), getRenderer().getYsel(), getRenderer().getSelectedPixelEventCount());
        }
    }

    /**
     * draws a circle at pixel x,y of size+.5 radius. size is used to indicate
     * number of spikes in this 'frame'
     */
    protected void showSpike(final GL2 gl, final int x, final int y, int size) {
        // circle
        gl.glPushMatrix();
        gl.glColor4f(0, 0, 1f, 0f);
        if (size > (chip.getMinSize() / 3)) {
            size = chip.getMinSize() / 3;
        }
        gl.glTranslatef(x + .5f, y + .5f, -1);
        selectedQuad = glu.gluNewQuadric();
        glu.gluQuadricDrawStyle(selectedQuad, GLU.GLU_FILL);
        glu.gluDisk(selectedQuad, size, size + 1, 16, 1);
        glu.gluDeleteQuadric(selectedQuad);
        gl.glPopMatrix();

        final int font = GLUT.BITMAP_HELVETICA_18;

        gl.glPushMatrix();

        final int FS = 1; // distance in pixels of text from selected pixel

        gl.glRasterPos3f(x + FS, y + FS, 0);
        glut.glutBitmapString(font, x + "," + y);

        gl.glPopMatrix();

        checkGLError(gl, glu, "showSpike");

    }

    /**
     * Zoom around mouseChipPixel with absolute zoom factor
     *
     * @param chipPixel
     * @param zfac the absolute zoom factor
     */
    public void zoomAroundPoint(Point chipPixel, float zfac) {
        getZoom().setZoomAroundPoint(chipPixel, zfac);
    }

    /**
     * Zoom up so that chipPixel is centered in the view by some factor.
     *
     * @param chipPixel
     * @param zfac
     */
    public void zoomToCenter(Point chipPixel, float zfac) {
        getZoom().zoomToCenter(chipPixel, zfac);
    }

    /**
     * Zoom in around a point p so that this point remains at same position in
     * the screen canvas
     *
     * @param p
     */
    public void zoomInAround(Point p) {
        getZoom().zoomInAround(p);
        repaint(100);
    }

    /**
     * Zoom out around a point p so that this point remains at same position in
     * the screen canvas
     *
     * @param p
     */
    public void zoomOutAround(Point p) {
        getZoom().zoomOutAround(p);
        repaint(100);
    }

    /**
     * Clear all zooms to go to default view, with borders around the chip.
     *
     */
    public void unzoom() {
        getZoom().unzoom();
        repaint(100);
    }

    /**
     * Returns true if the view has been changed from default view
     * magnification.
     *
     * @return
     */
    public boolean isZoomed() {
        return getZoom().isZoomed();
    }

    /**
     * Updates the chip renderer and other internal parameters of the chip.
     *
     * @param o the source chip
     * @param arg the update argument, used to respond to Chip2D.EVENT_SIZEX
     * etc.
     */
    @Override
    public void update(final Observable o, final Object arg) {
        // if observable is a chip object and arg is a string size property then set a new scaleChipPixels2ScreenPixels
        if ((o == chip) && (arg instanceof String)) {
            if (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY)) {
                ZCLIP = chip.getMaxSize();
                unzoom();
            }
        }
        if (o instanceof Chip2D) {
            final Chip2D c = (Chip2D) o;
            setRenderer(c.getRenderer());
        }
    }

    public Chip2DRenderer getRenderer() {
        return chip.getRenderer();
    }

    public void setRenderer(final Chip2DRenderer renderer) {
        chip.setRenderer(renderer);
    }

    public ArrayList<DisplayMethod> getDisplayMethods() {
        return displayMethods;
    }

    public void setDisplayMethods(final ArrayList<DisplayMethod> displayMethods) {
        this.displayMethods = displayMethods;
    }

    /**
     * Returns minimum space around pixel array in screen pixels
     *
     * @return screen pixel size border
     */
    public int getBorderSpacePixels() {
        if (!is3DEnabled()) {
            return borderSpacePixels;
        } else {
            return BORDER3D;
        }
    }

    /**
     * Sets the border around the drawn pixel glCanvas. Adds more space at top
     * for DAVIS frame update bar.
     *
     * @param borderSpacePixels in screen pixels.
     */
    public void setBorderSpacePixels(final int borderSpacePixels) {
        this.borderSpacePixels = borderSpacePixels;
        insets.bottom = borderSpacePixels;
        int extratop = 0;
        if (chip != null && chip instanceof DavisBaseCamera) { // leave room for stats label for DAVIS
            extratop += (int) (chip.getSizeY() * 0.1f);
//            log.finest(String.format("adding extra %,d pixels top inset for DAVIS chip", extratop));
        }
        insets.top = borderSpacePixels + extratop;
        insets.left = borderSpacePixels;
        insets.right = borderSpacePixels;
        prefs.putInt("borderSpacePixels", this.borderSpacePixels);
    }

    /**
     * Returns the border insets around the chip display, These insets are in
     * screen pixels.
     *
     * @return the Insets
     */
    public Insets getBorderInsets() {
        return insets;
    }

    /**
     * Clips a Point to within the chip size. The point is modified to fit
     * within the size of the Chip2D.
     *
     * @param p a Point
     */
    public void clipPoint(final Point p) {
        if (p.x < 0) {
            p.x = 0;
        } else if (p.x > (getChip().getSizeX() - 1)) {
            p.x = getChip().getSizeX() - 1;
        }
        if (p.y < 0) {
            p.y = 0;
        } else if (p.y > (getChip().getSizeY() - 1)) {
            p.y = getChip().getSizeY() - 1;
        }
    }

    public ChipCanvas.Zoom getZoom() {
        return zoom;
    }

    public void setZoom(final ChipCanvas.Zoom zoom) {
        this.zoom = zoom;
    }

    /**
     * Chip fills glCanvas horizontally.
     *
     * @return the fillsHorizontally
     */
    public boolean isFillsHorizontally() {
        return fillsHorizontally;
    }

    /**
     * Chip fills glCanvas vertically.
     *
     * @return the fillsVertically
     */
    public boolean isFillsVertically() {
        return fillsVertically;
    }

    /**
     * Constructs a new Zoom for this instance. Used by DisplayMethods to create
     * their own Zoom view that is preserved.
     */
    public Zoom createZoom() {
        return new Zoom();
    }

    private class Vec extends Point2D.Float {

        public Vec(float x, float y) {
            super(x, y);
        }

        public Vec(Vec v) {
            super(v.x, v.y);
        }

        public Vec(Point p) {
            super(p.x, p.y);
        }

        public Vec add(Vec v) {
            return new Vec(this.x + v.x, this.y + v.y);
        }

        public Vec add(float dx, float dy) {
            return new Vec(this.x + dx, this.y + dy);
        }

        public Vec subtract(Vec v) {
            return new Vec(this.x - v.x, this.y - v.y);
        }

        public boolean isNonZero() {
            return Math.abs(x) >= 1 || Math.abs(y) >= 1;
        }

        public Vec clear() {
            setLocation(0, 0);
            return this;
        }

        @Override
        public String toString() {
            return String.format("Vec [%.2f,%.2f]", x, y);
        }

        private Vec negate() {
            return new Vec(-this.x, -this.y);
        }

    }

    /**
     * Encapsulates zooming the view
     */
    public class Zoom {

        final float ZOOM_STEP_RATIO = (float) Math.pow(2, 1. / 8);
        private float zoomFactor = 1, previousZoomFactor = 1; // >1 for zoom in, magnified, <1 but >0 for zoom out

        /**
         * The actual clipping box bounds for the default orthographic
         * projection.
         */
        final ClipArea clipArea = new ClipArea(); // invalid at first, need to run computeUnzoomedClipAreaAndScale before using
//        ClipArea zoomedClipArea = new ClipArea(); // invalid at first, need to run computeUnzoomedClipAreaAndScale before using

        /**
         * The translation from center point currently applied
         */
        private Vec panPx = new Vec(0, 0);

        /**
         * Temporary drag vector applied during drag
         */
        private Vec dragPx = new Vec(0, 0);

        /**
         * The most recent mouse location for zoom as fraction of drawable
         * screen width and height. Computed on mouse event to get the current
         * value from screen pixel location
         */
        private final Vec mouseFrac = new Vec(.5f, .5f);

        /**
         * Flag set true to freeze the pan into the zoom clip window, so that
         * zooming around a point makes sense
         */
        private boolean erasePan = false;

        /**
         * Center point of view in pixels
         */
        private final Point centerChipPixel = new Point();  // where in chip pixels the view is centered, e.g. could be corner of chip is center of display.

        private Point currentZoomChipPixel = null; // last chip pixel we zoomed around, this can be different from centerChipPixel
        // when we zoom, we want the pixel under mouse to not move

        private void zoomToCenter(Point chipPixel, float zfac) {
            unzoom();
            setZoomAroundPoint(getZoom().centerChipPixel, zfac);
            getZoom().getClipArea().computeBounds();
            Vec chPx = new Vec(chipPixel);
            Vec cenPx = new Vec(getZoom().centerChipPixel);
            Vec diff = cenPx.subtract(chPx);
            getZoom().getClipArea().panFromCurrentBy(diff);
        }

        /**
         * Orthographic projection clipping area. The left, right, bottom, top
         * are the chip pixel coordinates at the corresponding edges of the
         * display. If there is space outside the chip on the left, for example,
         * left will be negative. If there is space on the right, then right
         * will be greater than the chip dim. If the view is zoomed up on part
         * of the chip, the l,r,b,t values will be inside the chip dimensions.
         * The aspect ratio of width/height of ClipArea must match the AR of the
         * drawable area.
         *
         * If the view is unzoomed, there is default insets space on each
         * border.
         */
        public class ClipArea {

            private boolean dirty = true;

            private void setDirty() {
                dirty = true;
            }

            private boolean isDirty() {
                return dirty;
            }

            private void clearDirty() {
                dirty = false;
            }

            /**
             * the number of screen pixels for one chip pixel.
             *
             * @see #getScale()
             */
            private float zoomedScreenPixelsPerChipPixelScale = 1;
            /**
             * The unzoomed scale, multiplied by scaling of zoom to result in
             * zoomedScreenPixelsPerChipPixelScale.
             */
            private float unzoomedScreenPixelsPerChipPixelScale = 1;

            /**
             * This clipping area is around the chip size by a negative amount
             * for left and bottom and a quantity larger than the chip size for
             * the right and top.
             */
            private float left = 0;
            /**
             * This clipping area is around the chip size by a negative amount
             * for left and bottom and a quantity larger than the chip size for
             * the right and top.
             */
            private float right = 0;
            /**
             * This clipping area is around the chip size by a negative amount
             * for left and bottom and a quantity larger than the chip size for
             * the right and top.
             */
            private float bot = 0;
            /**
             * This clipping area is around the chip size by a negative amount
             * for left and bottom and a quantity larger than the chip size for
             * the right and top.
             */
            private float top = 0;

            @Override
            public String toString() {
                return String.format("ClipArea  px=%s (w=%.1f h=%.1f, l=%.1f, r=%.1f, b=%.1f, t=%.1f)",
                        currentZoomChipPixel,
                        getWidth(), getHeight(), getLeft(), getRight(), getBottom(), getTop()
                );
            }

            void set(float left, float right, float bottom, float top) {
                this.left = left;
                this.right = right;
                this.bot = bottom;
                this.top = top;
            }

            void panFromCurrentBy(float dx, float dy) {
                this.setLeft(this.getLeft() + dx);
                this.setRight(this.getRight() + dx);
                this.setBottom(this.getBottom() + dy);
                this.setTop(this.getTop() + dy);
            }

            void panFromCurrentBy(Vec p) {
                ClipArea.this.panFromCurrentBy(-p.x, -p.y);
            }

            public float getWidth() {
                return right - left;
            }

            public float getHeight() {
                return top - bot;
            }

            public float getArea() {
                return getWidth() * getHeight();
            }

            private float getScale() {
                return isZoomed() ? zoomedScreenPixelsPerChipPixelScale : unzoomedScreenPixelsPerChipPixelScale;
            }

            /**
             * Computes the tricky orthographic clip area for the view for
             * unzoomed or zoomed projection
             */
            private void computeBounds() {
                if (glCanvas == null || chip == null) {
                    log.severe("null drawable or chip");
                    return;
                }
                if (chip.getNumPixels() == 0) {
                    log.warning("chip is not initialized yet");
                    return;
                }
                if (currentZoomChipPixel == null) {
                    currentZoomChipPixel = new Point(chip.getCenterPixel());
                }

                if (getClipArea().isDirty()) {
                    Vec totalPan=panPx.add(dragPx);
                    panFromCurrentBy(totalPan.negate());
                    if (!isZoomed()) {
                        computeUnzoomedBounds();
                    } else {
                        computeAdditionalZoom();
                    }
                    panFromCurrentBy(totalPan);
                    computeCenterPixel();
                    final float scalex = (float) glCanvas.getWidth() / (getWidth());
                    final float scaley = (float) glCanvas.getHeight() / getHeight();
                    zoomedScreenPixelsPerChipPixelScale = (scalex + scaley) / 2;
                    clearDirty();
                }
                log.fine(getZoom().toString());
            }

            private void computeAdditionalZoom() {
                final float zoomChangedFactor = zoomFactor / previousZoomFactor;
                if (Math.abs(zoomChangedFactor - 1) < 1e-2) {
                    return;
                }
                // zoomed view Inset no longer applied to view
                // find the CURRENT relative fraction of clip area that the
                // mouse position is in chip pixels and zoom clip area aournd that point
                // clip area width and height in chip pixels
                // mouse location as fraction of clip/display area x and y.
                // **** Important to do this before we manipulate the clip window!
                // mouseFrac is computed in getPixelFromMousePoint
                float fx = mouseFrac.x, fy = mouseFrac.y;

// the view should be zoomed so that after zoom fx and fy remain the same
                if (getArea() == 0) {
                    log.warning("clip area has zera area, cannot zoom");
                    return;
                }
                // recompute width and height after unzoom above

                // compute the actual (unzoomed) clip width and height including insets.
                float clipw = getWidth() + insets.left + insets.right;
                float cliph = getHeight() + insets.bottom + insets.top;

//                float zfac1 = (zoomFactor - 1) / zoomFactor; // zfac=2 gives .5, zfac=.5 gives -1
                float zfac1 = (zoomChangedFactor - 1) / zoomChangedFactor; // zfac=2 gives .5, zfac=.5 gives -1

                // Now tricky part, change the boundaries so that 
                // the mouseFrac pixel stays at same fractional position on screen
                left = left + fx * clipw * zfac1;  // move left more positive to zoom in (inout>0), more negative to zoom out (input<0)
                right = right - (1 - fx) * clipw * zfac1;
                bot = bot + fy * cliph * zfac1;
                top = top - (1 - fy) * cliph * zfac1;

                previousZoomFactor = zoomFactor;
                log.fine(String.format("zoomed around frac pos [%.2f, %.2f]", fx, fy));
//                    panFromCurrentBy(panPx.add(dragPx));
            }

            private void computeUnzoomedBounds() {
                final int canw = glCanvas.getWidth(), canh = glCanvas.getHeight(); // w,h of screen
                final int chw = chip.getSizeX(), chh = chip.getSizeY(); // chip size
                // compute the unzoomed with insets clip window first
                float newscale;
                if (chh > chw) {
                    // chip is tall and skinny, so set scaleChipPixels2ScreenPixels by frame height/chip height
                    newscale = (float) (canh - insets.top - insets.bottom) / chh;
                    fillsVertically = true;
                    fillsHorizontally = false;
                    if ((newscale * chw) > canw) { // unless it runs into left/right, then set to fill width
//                newscale = (width - border) / chipSizeX;
                        fillsHorizontally = true;
                        fillsVertically = false;
                    }
                } else {
                    // chip is square or squat, so set scaleChipPixels2ScreenPixels by frame width / chip width
                    newscale = (float) (canw - insets.left - insets.right) / chw;
                    fillsHorizontally = true;
                    fillsVertically = false;
                    if ((newscale * chh) > canh) {// unless it runs into top/bottom, then set to fill height
//                newscale = (height - border) / chipSizeY;
                        fillsVertically = true;
                        fillsHorizontally = false;
                    }
                }
                // sets the clipping volume so that the volume is clipped
                // according to whether the window is tall (ar>1)
                // or wide (ar<1).
                float glScale;
                if (isFillsHorizontally()) { // tall, chip fills horizontally
                    glScale = ((float) canw - (insets.left + insets.right)) / chw; // chip pix to screen pix scaling in horizontal&vert direction,
                    //  i.e. one glScale is one chip pixel in screen pixels
                    float lrborder = (insets.left + insets.right) / 2 / glScale; // l,r border in model coordinates
                    if (lrborder <= 0) {
                        lrborder = 1;
                    }
                    float leftoverY = ((canh / glScale) - chh) / 2; // leftover y in model coordinates that makes up vertical border
                    if (leftoverY <= 0) {
                        leftoverY = 1;
                    }
                    set(-lrborder, chw + lrborder, -leftoverY, chh + leftoverY);
                } else { // wide, chip fill vertically
                    glScale = ((float) canh - (insets.top + insets.bottom)) / chh; // total scale from screen pixels chip pixels, one glScale is one chip pixel in screen pixels
                    float btop = insets.top / glScale; // b is generic border in chip pixels
                    float bbot = insets.bottom / glScale; // b is generic border in chip pixels
                    if (bbot <= .5f) {
                        bbot = 1;
                    }
                    if (btop <= .5f) {
                        btop = 1;
                    }
                    float leftoverX = ((canw / glScale) - chw) / 2; // total leftover x in model coordinates that makes up horizontal border
                    if (leftoverX <= 0) {
                        leftoverX = 1;
                    }
                    set(-leftoverX, chw + leftoverX, -bbot, chh + btop);
                }
                unzoomedScreenPixelsPerChipPixelScale = glScale;
                // at this point the clip window is exactly the default unzoomed view, it has borders either on l/r or b/t
            }

            /**
             * Zoom around chipPixel with absolute zoom factor zfac
             *
             * @param chipPixel, if null zoom around middle of chip
             * @param zfac the absolute zoom factor
             */
            private void setZoomPointAndScale(Point chipPixel, float zfac) {
                if (chipPixel == null) {
                    chipPixel = centerChipPixel;
                }
                currentZoomChipPixel = chipPixel;
                setZoomFactor(zfac);
                getClipArea().setDirty();
            }

            private void computeCenterPixel() {
                centerChipPixel.setLocation((getRight() + getLeft()) / 2, (getTop() + getBottom()) / 2);
            }

            /**
             * @return the left
             */
            public float getLeft() {
                return left;
            }

            /**
             * @param left the left to set
             */
            public void setLeft(float left) {
                this.left = left;
            }

            /**
             * @return the right
             */
            public float getRight() {
                return right;
            }

            /**
             * @param right the right to set
             */
            public void setRight(float right) {
                this.right = right;
            }

            /**
             * @return the bottom
             */
            public float getBottom() {
                return bot;
            }

            /**
             * @param bottom the bottom to set
             */
            public void setBottom(float bottom) {
                this.bot = bottom;
            }

            /**
             * @return the top
             */
            public float getTop() {
                return top;
            }

            /**
             * @param top the top to set
             */
            public void setTop(float top) {
                this.top = top;
            }

        }

        public String toString() {
            return String.format("Zoom: fact=%.2f, pan=%s, drag=%s, clipArea=%s", getZoomFactor(), panPx, dragPx, getClipArea());
        }

        private void applyProjection(final GL2 gl) {
            if (getClipArea().isDirty()) {
                getClipArea().computeBounds();
            }
            checkGLError(gl, glu, "before applyZoomProjection");

            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            gl.glLoadIdentity();
            // project applied depends on zoomed being set or not
            gl.glOrtho(getClipArea().getLeft(),
                    getClipArea().getRight(),
                    getClipArea().getBottom(),
                    getClipArea().getTop(),
                    ZCLIP, -ZCLIP); // clip area
            gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);

            checkGLError(gl, glu, "after applyZoomProjection");
        }

        /**
         * Sets the point and scale for subsequent zoom around mouseChipPixel
         * with absolute zoom factor zfac
         *
         * @param chipPixel
         * @param zfac the absolute zoom factor
         *
         */
        private void setZoomAroundPoint(Point chipPixel, float zfac) {
            if (chipPixel == null) {
                log.warning("null mouse point, will not zoom");
                return;
            }
            log.fine(String.format("zooming on %s with zoom factor %.1f", chipPixel, zfac));

            getClipArea().setZoomPointAndScale(chipPixel, zfac);
        }

        private void clearPan() {
            panPx.clear();
            dragPx.clear();
        }

        /**
         * zoom in out by depending on inout sign, >1 for in/magnify, <0 for
         * out, 0 to pan
         *
         * @param inout + for in - for out 0 to pan with mouse, value does not
         * matter, only sign
         */
        private void changeZoomFactorByFactor(float factor) {
            setZoomFactor(zoomFactor * factor);
            log.fine(String.format("new zoomFactor=%.2f", zoomFactor));
        }

        private void zoomInAround(Point p) {
            changeZoomFactorByFactor(ZOOM_STEP_RATIO);
            if (p != null) {
                getZoom().setZoomAroundPoint(getPixelFromMousePoint(p), getZoomFactor());
            } else {
                getZoom().setZoomAroundPoint(getZoom().centerChipPixel, getZoomFactor());
            }
        }

        private void zoomOutAround(Point p) {
            changeZoomFactorByFactor(1 / ZOOM_STEP_RATIO);
            if (p != null) {
                getZoom().setZoomAroundPoint(getPixelFromMousePoint(p), getZoomFactor());
            } else {
                getZoom().setZoomAroundPoint(getZoom().centerChipPixel, getZoomFactor());
            }
        }

        private void unzoom() {
            log.fine("unzooming");
            clearPan();
            setZoomFactor(1);
            getZoom().previousZoomFactor=1;
            set3dOrigin(0, 0);
            clipArea.setDirty();
            clipArea.computeBounds();
//            zoomedClipArea.computeBounds();
        }

        /**
         * If zoomed, then the border insets do not apply to view
         *
         * @return true if zoomFactor!=1
         */
        public boolean isZoomed() {
            return Math.abs(zoomFactor - 1) > 1e-3; // account for possible rounding during zoom in/out
        }

        /**
         * @return the clipArea
         */
        public ClipArea getClipArea() {
            ClipArea area = clipArea; //isZoomed() ? zoomedClipArea : clipArea;
            return area;
        }

        private void computeMouseFrac(Point p) {
            mouseFrac.setLocation((float) p.x / glCanvas.getWidth(), 1 - (float) p.y / glCanvas.getHeight());
        }

        /**
         * @return the zoomFactor
         */
        public float getZoomFactor() {
            return zoomFactor;
        }

        /**
         * @param zoomFactor the zoomFactor to set, rounds close to 1 to 1
         */
        public void setZoomFactor(float zoomFactor) {
            if (Math.abs(zoomFactor - 1) < 1e-3) {
                zoomFactor = 1; // deal with rounding
            }
            this.zoomFactor = zoomFactor;
        }

    }

    /**
     * Draws the annotations for a filter including the annotations for enclosed
     * filters
     *
     * @param f the filter to annotate
     * @param a the corresponding annotator (usually the same filter)
     * @param drawable the drawing context
     */
    private void drawAnnotationsIncludingEnclosed(final EventFilter f, final FrameAnnotater a,
            final GLAutoDrawable drawable) {
        try {
            a.annotate(drawable);
        } catch (final RuntimeException e) {
            log.warning("caught " + e + " when annotating with " + a);
            e.printStackTrace();
        }
        if ((f.getEnclosedFilter() != null) && (f.getEnclosedFilter() instanceof FrameAnnotater)) {
            final EventFilter f2 = f.getEnclosedFilter();
            final FrameAnnotater a2 = (FrameAnnotater) f2;
            if (a2.isAnnotationEnabled()) {
                drawAnnotationsIncludingEnclosed(f2, (FrameAnnotater) f2, drawable);
            }
        }
        if (f.getEnclosedFilterChain() != null) {
            for (final EventFilter f2 : f.getEnclosedFilterChain()) {
                if ((f2 != null) && (f2 instanceof FrameAnnotater)) {
                    final FrameAnnotater a2 = (FrameAnnotater) f2;
                    if (a2.isAnnotationEnabled()) {
                        drawAnnotationsIncludingEnclosed(f2, a2, drawable);
                    }
                }
            }
        }
    }

    /**
     * First, calls annotate(GLAutoDrawable) for all FrameAnnotators that have
     * been added explicitly to the current DisplayMethod. Then it calls
     * annotate on all FilterChain filters with that implement FrameAnnotator
     * and that are enabled for the Chip2D.
     *
     * @param drawable the context
     */
    protected void annotate(final GLAutoDrawable drawable) {

        if (!annotationEnabled) {
            return;
        }
        if (getDisplayMethod() == null) {
            return;
        }
        if (getDisplayMethod().getAnnotators() == null) {
            return;
        }

        for (final FrameAnnotater a : getDisplayMethod().getAnnotators()) {
            a.annotate(drawable);
        }

        if (chip instanceof AEChip) {
            if (((AEChip) chip).getAeViewer() != null) {
                aeViewer = ((AEChip) chip).getAeViewer();
            }
            if (aeViewer != null && aeViewer.getPlayerControls() != null
                    && (aeViewer.getPlayerControls().isSliderBeingAdjusted()
                    || ((aeViewer.getPlayMode() == PlayMode.PLAYBACK) && !(aeViewer.getAePlayer().isPlayingForwards())))) {
                return;
            }
        }
        final FilterChain chain = ((AEChip) chip).getFilterChain();
        if (chain != null) {
            for (final EventFilter f : chain) {
                if (!f.isAnnotationEnabled()) {
                    continue;
                }
                final FrameAnnotater a = (FrameAnnotater) f;
                drawAnnotationsIncludingEnclosed(f, a, drawable);
            }
        }

    }

    /**
     * Utility method to check for GL errors. Prints stacked up errors up to a
     * limit.
     *
     * @param g the GL context
     * @param glu the GLU used to obtain the error strings
     * @param msg an error message to log to e.g., show the context
     */
    public void checkGLError(final GL2 g, final GLU glu, final String msg) {
        if (g == null) {
            log.warning("called checkGLError with null graphics");
            return;
        }
        if (glu == null) {
            log.warning("called checkGLError with null glu");
            return;
        }
        if (g.getContext() == null) {
            log.warning("GL context for graphics is null, cannot check error");
            return;
        }
        int error = g.glGetError();
        int nerrors = 3;
        while ((error != GL.GL_NO_ERROR) && (nerrors-- != 0)) {
            final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace.length > 1) {
                final String className = trace[2].getClassName();
                final String methodName = trace[2].getMethodName();
                final int lineNumber = trace[2].getLineNumber();
                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg + " at "
                        + className + "." + methodName + " (line " + lineNumber + ")");
            } else {
                log.warning("GL error number " + error + " " + glu.gluErrorString(error) + " : " + msg);
            }
            // Thread.dumpStack();
            error = g.glGetError();
        }
    }

    public float getOrigin3dx() {
        return origin3dx;
    }

    public void setOrigin3dx(final float origin3dx) {
        this.origin3dx = origin3dx;
    }

    public float getOrigin3dy() {
        return origin3dy;
    }

    public void setOrigin3dy(final float origin3dy) {
        this.origin3dy = origin3dy;
    }

    /*
     * Copy the frame buffer into the BufferedImage. The data needs to
     * be flipped top to bottom because the origin is the lower left in
     * OpenGL, but is the upper right in Java's BufferedImage format.
     * This method should be called inside the rendering display method.
     *
     * @param d the glCanvas we are drawing in.
     */
    void grabImage(final GLAutoDrawable d) {
        final GL2 gl = d.getGL().getGL2();
        final int width = d.getSurfaceWidth();
        final int height = d.getSurfaceHeight();

        // Allocate a buffer for the pixels
        final ByteBuffer rgbData = Buffers.newDirectByteBuffer(width * height * 3);

        // Set up the OpenGL state.
        gl.glReadBuffer(GL.GL_FRONT);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

        // Read the pixels into the ByteBuffer
        gl.glReadPixels(0, 0, width, height, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, rgbData);

        // Allocate space for the converted pixels
        final int[] pixelInts = new int[width * height];

        // Convert RGB bytes to ARGB ints with no transparency. Flip
        // imageOpenGL vertically by reading the rows of pixels in the byte
        // buffer in reverse - (0,0) is at bottom left in OpenGL.
        int p = width * height * 3; // Points to first byte (red) in each row.
        int q; // Index into ByteBuffer
        int i = 0; // Index into target int[]
        final int bytesPerRow = width * 3; // Number of bytes in each row

        for (int row = height - 1; row >= 0; row--) {
            p = row * bytesPerRow;
            q = p;
            for (int col = 0; col < width; col++) {
                final int iR = rgbData.get(q++);
                final int iG = rgbData.get(q++);
                final int iB = rgbData.get(q++);

                pixelInts[i++] = ((0xFF000000) | ((iR & 0xFF) << 16) | ((iG & 0xFF) << 8) | (iB & 0xFF));
            }
        }

        // Set the data for the BufferedImage
        if ((getImageOpenGL() == null) || (getImageOpenGL().getWidth() != width)
                || (getImageOpenGL().getHeight() != height)) {
            imageOpenGL = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        getImageOpenGL().setRGB(0, 0, width, height, pixelInts, 0, width);
    }

    public BufferedImage getImageOpenGL() {
        return imageOpenGL;
    }

    public GLUT getGlut() {
        return glut;
    }

    /**
     * gets the chip we are rendering for. Subclasses or display methods can use
     * this to access the chip object.
     *
     * @return the chip
     */
    public Chip2D getChip() {
        return chip;
    }

    public void setChip(final Chip2D chip) {
        this.chip = chip;
    }

    @Override
    public void dispose(final GLAutoDrawable arg0) {
        log.fine("disposing " + arg0);
    }

    private static boolean checkedRetinaDisplay = false;
    private static boolean hasRetinaDisplayTrue = false;

    /**
     * hack from
     * https://bulenkov.com/2013/06/23/retina-support-in-oracle-jdk-1-7/ used to
     * multiply mouse coordinates by two in case of retina display; see
     * http://forum.lwjgl.org/index.php?topic=5084.0
     *
     * @return true if running on platform with retina display. Value is cached
     * in VM to avoid runtime cost penalty of multiple calls.
     */
    public static boolean hasAppleRetinaDisplay() {
        if (checkedRetinaDisplay) {
            return hasRetinaDisplayTrue;
        }
        checkedRetinaDisplay = true;
        //other OS and JVM specific checks...
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice device = env.getDefaultScreenDevice();

        try {
            Field field = device.getClass().getDeclaredField("scale");

            if (field != null) {
                field.setAccessible(true);
                Object scale = field.get(device);

                if (scale instanceof Integer && ((Integer) scale).intValue() == 2) {
                    hasRetinaDisplayTrue = true;
                }
            }
        } catch (Exception ignore) {
        }
        //...
        return hasRetinaDisplayTrue;
    }

    /**
     * @return the annotationEnabled
     */
    public boolean isAnnotationEnabled() {
        return annotationEnabled;
    }

    /**
     * @param annotationEnabled the annotationEnabled to set
     */
    public void setAnnotationEnabled(boolean annotationEnabled) {
        this.annotationEnabled = annotationEnabled;
    }

    private class ZoomMouseAdaptor extends MouseAdapter {

        boolean dragging = false;

        @Override
        public void mouseClicked(MouseEvent evt) {
            final Point p = getPixelFromMouseEvent(evt);
            if (evt.getButton() == MouseEvent.BUTTON3) {
                if (wasMousePixelInsideChipBounds()) {
                    getRenderer().setXsel((short) p.x);
                    getRenderer().setYsel((short) p.y);
                } else {
                    getRenderer().setXsel((short) -1);
                    getRenderer().setYsel((short) -1);
                }
                log.info("Selected pixel x,y=" + getRenderer().getXsel() + "," + getRenderer().getYsel());
            } else {
                log.fine("clicked pixel " + getPixelFromMouseEventUnclipped(evt));
            }
        }

        @Override
        public void mousePressed(final MouseEvent evt) {
            dragging = false;
            if (evt.getButton() == MouseEvent.BUTTON3) {
                dragging = true;
                origin3dMouseDragStartPoint.setLocation(origin3dx, origin3dy);

                if (mdStPt == null) {
                    mdStPt = new Point(evt.getPoint());
                } else {
                    mdStPt.setLocation(evt.getPoint());
                }
                drStPx = new Vec(mdStPt.x / getScale(),
                        -mdStPt.y / getScale()); //getPixelUnclippedFromMouseEvent(evt);
                getZoom().dragPx.clear();
                log.fine(getMousePixel().toString());
            }
            log.fine("pressed pixel " + getPixelFromMouseEventUnclipped(evt));
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
            dragging=false;
            if (is3DEnabled()) {
                log.fine("3d rotation: angley=" + angley + " deg anglex=" + anglex + " deg 3d origin: x="
                        + getOrigin3dx() + " y=" + getOrigin3dy());
            } else if (dragging) {
                // drag end
                getZoom().panPx = new Vec(getZoom().panPx.add(getZoom().dragPx));
                getZoom().dragPx.clear();
                getClipArea().setDirty();
                log.fine(String.format("pan=%s, zoom=%s", getZoom().panPx, zoom));
            }
            log.fine("released pixel " + getPixelFromMouseEventUnclipped(evt));
            repaint();
        }

        @Override
        public void mouseDragged(final MouseEvent e) { // handle panning
            final int screenX = e.getX();
            final int screenY = e.getY();
            final int but1mask = InputEvent.BUTTON1_DOWN_MASK, but3mask = InputEvent.BUTTON3_DOWN_MASK;
            if ((e.getModifiersEx() & but1mask) == but1mask) {
                if (is3DEnabled()) {
                    final float maxAngle = 180f;
                    setAngley((maxAngle * (screenX - (glCanvas.getWidth() / 2))) / glCanvas.getWidth());
                    setAnglex((maxAngle * (screenY - (glCanvas.getHeight() / 2))) / glCanvas.getHeight());
                    log.fine(String.format("angleX=%6.1f deg, angleY=%6.1f", anglex, angley));
                    getClipArea().setDirty();
                }
            } else if ((e.getModifiersEx() & but3mask) == but3mask) { // right mouse button drag
                if (is3DEnabled()) {
                    // mouse position x,y is in pixel coordinates in window glCanvas, but later on, the events will be
                    // drawn in
                    // chip coordinates (transformation applied). therefore here we set origin in pixel coordinates
                    // based on mouse
                    // position in window.
                    float dx = screenX - mdStPt.x;
                    float dy = screenY - mdStPt.y;
                    origin3dx = origin3dMouseDragStartPoint.x + Math.round((getChip().getMaxSize() * ((float) dx)) / glCanvas.getWidth());
                    origin3dy = origin3dMouseDragStartPoint.y + Math.round((getChip().getMaxSize() * ((float) -dy)) / glCanvas.getHeight());
                } else { // normal pan
                    Point mPt = e.getPoint();
//                    Point2D.Float mdpx = new Point2D.Float(mPt.x / getScale(),
//                            -mPt.y / getScale()); // current px, arbitrary origin
                    Vec dr=new Vec(mPt.x/getScale(),-mPt.y/getScale()); // drag in chip px is flipped vertically from screen drag
                    
//                    float pxDx = mdpx.x - dragLastPx.x, pxDy = mdpx.y - dragLastPx.y;
//                    getZoom().dragPx = new Vec(pxDx, pxDy);
                    getZoom().dragPx = dr.subtract(drStPx);
                    getClipArea().setDirty();
                }
            }
            repaint();
        }

    }
}
