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
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;

import org.apache.commons.lang3.SystemUtils;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawableFactory;
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
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

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
 * This canvas can use OpenGL to render, and it can either actively or passively
 * render.
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

    protected Preferences prefs = Preferences.userNodeForPackage(ChipCanvas.class);
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
    protected GLCanvas drawable;
    /**
     * fr is the rendered event data that we draw. X is the first dimenion, Y is
     * the second dimension, RGB 3 vector is the last dimension
     */
    protected float[][][] fr; // this is legacy data that we render here to the screen
    protected RenderingFrame frameData; // this is new form of pixel data to render
    protected GLU glu; // instance this if we need glu calls on context
    protected GLUT glut = null;
    protected Logger log = Logger.getLogger("Graphics");
    private float origin3dx;
    private float origin3dy;
    protected int pheight;
    protected int[] pixels;
    /**
     * defines the minimum canvas size in pixels; used when chip size has not
     * been set to non zero value
     */
    public static final int MIN_DIMENSION = 70;
    /**
     * width and height of pixel array in canvas in screen pixels. these are
     * different than the actual canvas size
     */
    protected int pwidth = prefs.getInt("ChipCanvas.pwidth", 512);

    /**
     * the number of screen pixels for one chip pixel.
     *
     * @see #getScale()
     */
    protected float scaleChipPixels2ScreenPixels = 1;
    protected static final Color selectedPixelColor = Color.blue;
    protected GLUquadric selectedQuad;
    public BufferStrategy strategy;
    /**
     * the translation of the actual chip drawing area in the canvas, in screen
     * pixels
     */
    protected int xt, yt;
    private ChipCanvas.Zoom zoom = new Zoom();
    protected boolean zoomMode = false; // true while user is dragging zoom box
    // reused imageOpenGL for OpenGL image grab
    private BufferedImage imageOpenGL;
    // reused by canvas to hold bytes of image when writing java Image
    ByteBuffer byteBuffer = null;
    // boolean to flag that present opengl display call should write imageOpenGL
    volatile boolean grabNextImageEnabled = false;
    /**
     * border around drawn pixel array in screen pixels
     */
    private int borderSpacePixels = prefs.getInt("borderSpacePixels", 20);
    /**
     * border in screen pixels when in 3d space-time rendering mode
     */
    protected final int BORDER3D = 70;
    /**
     * Insets of the drawn chip canvas in the window
     */
    private Insets insets = new Insets(borderSpacePixels, borderSpacePixels, borderSpacePixels, borderSpacePixels);
    private boolean fillsHorizontally = false, fillsVertically = false; // filled in in the reshape method to show how
    // chip fills drawable space
    private double ZCLIP = 1;
    private TextRenderer renderer = null;

    /**
     * Creates a new instance of ChipCanvas
     */
    public ChipCanvas(final Chip2D chip) {
        this.chip = chip;
        this.prefs = chip.getPrefs();
        anglex = prefs.getFloat("ChipCanvas.anglex", 5);
        angley = prefs.getFloat("ChipCanvas.angley", 10);
        origin3dx = prefs.getInt("ChipCanvas.origin3dx", 0);
        origin3dy = prefs.getInt("ChipCanvas.origin3dy", 0);
        pheight = prefs.getInt("ChipCanvas.pheight", 512);
        prefs.getInt("borderSpacePixels", 20);

        // GraphicsEnvironment ge=GraphicsEnvironment.getLocalGraphicsEnvironment();
        // GraphicsDevice[] gs=ge.getScreenDevices(); // TODO it could be that remote session doesn't show screen that
        // used to be used. Should check that we are not offscreen. Otherwise registy edit is required to show window!
        //
        // if(gs!=null&&gs.length>0) {
        // if(gs.length>1){
        // log.info("There are "+gs.length+" GraphicsDevice's found; using first one which is "+gs[0].getIDstring());
        // }
        // GraphicsDevice gd=gs[0];
        // GraphicsConfiguration[] gc=gd.getConfigurations(); // TODO this call takes >10s on windows 7 x64 w510 lenovo
        // with nvidia graphics!
        // if(gc!=null&&gc.length>0) {
        // if(gc.length>1){
        // log.info("There are "+gc.length+" GraphicsConfiguration's found; using first one which is "+gc[0].toString());
        // }
        // }
        // }
        //
        // // design capabilities of opengl canvas
        // GLCapabilities caps = new GLCapabilities();
        //
        // caps.setAlphaBits(8);
        /*
         * caps.setDoubleBuffered(true);
         * caps.setHardwareAccelerated(true);
         * caps.setRedBits(8);
         * caps.setGreenBits(8);
         * caps.setBlueBits(8);
         */
        // make the canvas
        try {
            final GLProfile glp = GLProfile.getDefault();
            final GLCapabilities caps = new GLCapabilities(glp);
            drawable = new GLCanvas(caps);
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
//                drawable = new GLCanvas(chosenGLCaps);
//            } else {
//                drawable = new GLCanvas();
//            }

            if (drawable == null) {
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
        drawable.setLocale(Locale.US); // to avoid problems with other language support in JOGL
        drawable.setVisible(true);
        // will always getString invalid operation here
        // checkGLError(drawable.getGL(),glu,"before add event listener");
        // add us as listeners for the canvas. then when the display wants to redraw display() will be called. or we can
        // call drawable.display();
        drawable.addGLEventListener(this);
        // same here, canvas not yet valid
        // checkGLError(drawable.getGL(),glu,"after add event listener");

        // Dimension ss=Toolkit.getDefaultToolkit().getScreenSize();
        scaleChipPixels2ScreenPixels = prefs.getFloat(scalePrefsKey(), 4); // if scaleChipPixels2ScreenPixels comes from
        // prefs, then scaleChipPixels2ScreenPixels
        // gets smaller and smaller with each window
        // that is opened
        drawable.setSize(200, 200); // set a size explicitly here
        initComponents();
        chip.addObserver(this);

        // if this canvas was constructed from a chip, then fill the display methods from that chip's ChipCanvas, if it
        // exists and has them
        if (displayMethods.isEmpty() && (chip.getCanvas() != null) && (chip.getCanvas().getDisplayMethod() != null)) {
            displayMethods.add(chip.getCanvas().getDisplayMethod());
        }

//        if (System.getProperty("os.name").startsWith("Windows")) {
//            // TODO tobi added to try to use shared context on windows, to address problems with opening multiple canvases (e.g. multiple AEViewers)
//            // opening 2nd instance, or even creating live file preview in AE file open dialog, often causes JOGL to bomb with
//            // complaints about non availablility of the desired GLProfile.
//            // On Linux systems, using the shared context below with Intel graphics causes an immediate native exception.
//            if (drawable != null && JAERViewer.sharedDrawable != null) {
//                drawable.setSharedAutoDrawable(JAERViewer.sharedDrawable); // drawable might be null if AEChip is created outside of normal context of AEViewer
//            }
//        }
        // between all viewers and file open dialog
        // previews.
        // TODO we now get under windows this exception:
        // com.jogamp.opengl.GLException: AWT-EventQueue-0: WindowsWGLContex.createContextImpl ctx !ARB but ARB is
        // used, profile > GL2 requested (OpenGL >= 3.0.1). Requested: GLProfile[GL4bc/GL4bc.hw], current: 1.1 (Compat
        // profile, hardware) - 1.1.0

        if (drawable != null) {
            log.info("GLCanvas=" + drawable.toString());
            if (drawable.getContext() != null) {
                log.info("GLCanvas has GLContext=" + drawable.getContext().toString());
            }
        }

        drawable.setFocusable(true);
        // for debugging bug where context was grabbed by getMousePoint and not released
//                drawable.addFocusListener(new FocusListener() {
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
     * the list of display methods for this canvas
     */
    private ArrayList<DisplayMethod> displayMethods = new ArrayList<DisplayMethod>();

    /**
     * adds a display method to this canvas
     *
     * @param m the method
     */
    public void addDisplayMethod(final DisplayMethod m) {
        if (getDisplayMethods().contains(m)) {
            return;
        }
        getDisplayMethods().add(m);
        displayMethodMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to
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
    int currentDisplayMethodIndex = 0;

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
        if (++currentDisplayMethodIndex >= getDisplayMethods().size()) {
            currentDisplayMethodIndex = 0;
        }
        setDisplayMethod(currentDisplayMethodIndex);
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
                log.info("no menu item for display method " + m
                        + " cannot set it as the selected display method in the menu");
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
        checkGLError(gl, glu, "start of display");

        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        gl.glLoadIdentity();
        // log.info("display");

        checkGLError(gl, glu, "before setting projection");
        // gl.glPushMatrix(); // don't push so that mouse selection has correct matrices
        {
            if (getZoom().isZoomEnabled()) {
                getZoom().setProjection(gl);

            } else {
                setDefaultProjection(gl, drawable);
            }
            checkGLError(gl, glu, "after setting projection, before displayMethod");
            final DisplayMethod m = getDisplayMethod();
            if (m == null) {
                log.warning("null display method for chip " + getChip());
            } else {
                m.display(drawable);
            }
            // checkGLError(gl, glu, "after " + getDisplayMethod() + ".display()");
            checkGLError(gl, glu, "after DisplayMethod.display()");
            showSpike(gl);
            annotate(drawable);
            checkGLError(gl, glu, "after FrameAnnotator (EventFilter) annotations");
        }
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
        // drawable.swapBuffers(); // don't use, very slow and autoswapbuffers is set
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

    // TODO javadoc
    public float getAnglex() {
        return anglex;
    }

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
        return drawable;
    }

    /**
     * Pixel drawing scale. 1 pixel is rendered to getScale() screen pixels. To
     * obtain chip pixel units from screen pixels, divide screen pixels by
     * <code>getScale()</code>. Conversely, to scale chip pixels to screen
     * pixels, multiply by <code>getScale()</code>.
     *
     * @return scale in screen pixels/chip pixel.
     */
    public float getScale() {
        return scaleChipPixels2ScreenPixels;
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
     * Finds the chip pixel from a ChipCanvas point. From
     * <a href="http://processing.org/discourse/yabb_beta/YaBB.cgi?board=OpenGL;action=display;num=1176483247">this
     * forum link</a>.
     *
     * @param mp a Point in ChipCanvas pixels.
     * @return the AEChip pixel, clipped to the bounds of the AEChip.
     */
    public Point getPixelFromPoint(final Point mp) {
        final double wcoord[] = new double[3];// wx, wy, wz;// returned xyz coords
        // this method depends on current GL context being the one that is used for rendering.
        // the display method should not push/pop the matrix stacks!!
        if (mp == null) {
            // log.warning("null Point (outside entire canvas?), returning center pixel");
            return new Point(chip.getSizeX() / 2, chip.getSizeY() / 2);
        }
//        synchronized (drawable.getTreeLock()) {
        try {
            if (hasAppleRetinaDisplay()) {
                mp.x *= 2;
                mp.y *= 2;
            }
            final int ret = drawable.getContext().makeCurrent();
            if (ret != GLContext.CONTEXT_CURRENT) {
                throw new GLException("couldn't make context current");
            }

            final int viewport[] = new int[4];
            final double mvmatrix[] = new double[16];
            final double projmatrix[] = new double[16];
            int realy = 0;// GL y coord pos
            // set up a floatbuffer to get the depth buffer value of the mouse position
            final FloatBuffer fb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            final GL2 gl = drawable.getContext().getGL().getGL2();
            checkGLError(gl, glu, "before getting mouse point");
            gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
            gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX, mvmatrix, 0);
            gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, projmatrix, 0);
            /* note viewport[3] is height of window in pixels */
            realy = viewport[3] - (int) mp.getY() - 1;
            // Get the depth buffer value at the mouse position. have to do height-mouseY, as GL puts 0,0 in the bottom
            // left, not top left.
            checkGLError(gl, glu, "after getting modelview and projection matrices in getMousePoint");
//                gl.glReadPixels(mp.x, realy, 1, 1, GL2ES2.GL_DEPTH_COMPONENT, GL.GL_FLOAT, fb);
//                checkGLError(gl, glu, "after readPixels in getMousePoint");
            final float z = 0; // fb.getString(0); // assume we want z=0 value of mouse point
            glu.gluUnProject(mp.getX(), realy, z, mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
            checkGLError(gl, glu, "after gluUnProject in getting mouse point");
        } catch (final GLException e) {
            log.warning("couldn't make GL context current, mouse position meaningless: " + e.toString());
        } finally {
            if (drawable.getContext().isCurrent()) {
                drawable.getContext().release();
            }
        }
//        }
        final Point p = new Point();
        p.x = (int) Math.round(wcoord[0]);
        p.y = (int) Math.round(wcoord[1]);
        if ((p.x < 0) || (p.x > (chip.getSizeX() - 1)) || ((p.y < 0) | (p.y > (chip.getSizeY() - 1)))) {
            mouseWasInsideChipBounds = false;
        } else {
            mouseWasInsideChipBounds = true;
        }
        clipPoint(p);

        // log.info("Mouse xyz=" + mp.getX() + "," + realy + "," + z + "   Pixel x,y=" + p.x + "," + p.y);
        return p;
    }

    /**
     * Finds the current AEChip pixel mouse position, or center of array if not
     * inside.
     *
     * @return the AEChip pixel, clipped to the bounds of the AEChip
     */
    public Point getMousePixel() {
        final Point mp = getCanvas().getMousePosition();
        return getPixelFromPoint(mp);
    }

    /**
     * Returns state of mouse from last call to getPixelFromPoint; true if mouse
     * inside bounds of chip drawing area.
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
     * scaling and borders of chip display area
     */
    public Point getPixelFromMouseEvent(final MouseEvent evt) {
        final Point mp = evt.getPoint();
        return getPixelFromPoint(mp);
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
        // drawable.setGL(new DebugGL(drawable.getGL()));
        drawable.setAutoSwapBufferMode(true);
        final GL2 gl = drawable.getGL().getGL2();

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

        gl.setSwapInterval(1);
        gl.glShadeModel(GLLightingFunc.GL_FLAT);

        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "Initialized display");
        checkGLError(gl, glu, "after init");

    }

    private Point mouseDragStartPoint = new Point(0, 0);
    private Point origin3dMouseDragStartPoint = new Point(0, 0);

    protected void initComponents() {
        unzoom();
        if (getRenderer() != null) {
            drawable.addMouseListener(new MouseAdapter() {

                @Override
                public void mousePressed(final MouseEvent evt) {
                    mouseDragStartPoint.setLocation(evt.getPoint());
                    origin3dMouseDragStartPoint.setLocation(origin3dx, origin3dy);

                    final Point p = getPixelFromMouseEvent(evt);
                    // if (!isZoomMode()) {

                    // System.out.println("evt="+evt);
                    if (evt.getButton() == 1) {
                        getRenderer().setXsel((short) -1);
                        getRenderer().setYsel((short) -1);
                        // System.out.println("cleared pixel selection");
                    } else if (evt.getButton() == 3) {
                        // we want mouse click location in chip pixel location
                        // don't forget that there is a borderSpacePixels on the orthographic viewport projection
                        // this border means that the pixels are actually drawn on the screen in a viewport that has a
                        // borderSpacePixels sized edge on all sides
                        // for simplicity because i can't figure this out, i have set the borderSpacePixels to zero

                        // log.info(" width=" + drawable.getWidth() + " height=" + drawable.getHeight() + " mouseX=" +
                        // evt.getX() + " mouseY=" + evt.getY() + " xt=" + xt + " yt=" + yt);
                        // renderer.setXsel((short)((0+((evt.x-xt-borderSpacePixels)/j2dScale))));
                        // renderer.setYsel((short)(0+((getPheight()-evt.y+yt-borderSpacePixels)/j2dScale)));
                        getRenderer().setXsel((short) p.x);
                        getRenderer().setYsel((short) p.y);
                        log.info("Selected pixel x,y=" + getRenderer().getXsel() + "," + getRenderer().getYsel());
                    }
                    // } else if (isZoomMode()) { // zoom startZoom
                    // zoom.startZoom(p);
                    // }
                }

                @Override
                public void mouseReleased(final MouseEvent e) {
                    if (is3DEnabled()) {
                        log.info("3d rotation: angley=" + angley + " anglex=" + anglex + " 3d origin: x="
                                + getOrigin3dx() + " y=" + getOrigin3dy());
                    }
                    // else
                    // if (isZoomMode()) {
                    // Point p = getPixelFromMouseEvent(e);
                    // zoom.endZoom(p);
                    // // zoom.endX=p.x;
                    // // zoom.endY=p.y;
                    // // setZoomMode(false);
                    // }
                }
            });
        } // renderer!=null

        drawable.addMouseMotionListener(new MouseMotionListener() {

            @Override
            public void mouseDragged(final MouseEvent e) {
//                                Point p=getPixelFromMouseEvent(e);
                final int x = e.getX();
                final int y = e.getY();
                final int but1mask = InputEvent.BUTTON1_DOWN_MASK, but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx() & but1mask) == but1mask) {
                    if (is3DEnabled()) {
                        final float maxAngle = 180f;
                        setAngley((maxAngle * (x - (drawable.getWidth() / 2))) / drawable.getWidth());
                        setAnglex((maxAngle * (y - (drawable.getHeight() / 2))) / drawable.getHeight());
                    } else if (isZoomMode()) {
                        // System.out.print("z");
                    }
                } else if ((e.getModifiersEx() & but3mask) == but3mask) {
                    if (is3DEnabled()) {
                        // mouse position x,y is in pixel coordinates in window canvas, but later on, the events will be
                        // drawn in
                        // chip coordinates (transformation applied). therefore here we set origin in pixel coordinates
                        // based on mouse
                        // position in window.
                        float dx = e.getX() - mouseDragStartPoint.x;
                        float dy = e.getY() - mouseDragStartPoint.y;
                        origin3dx = origin3dMouseDragStartPoint.x + Math.round((getChip().getMaxSize() * ((float) dx)) / drawable.getWidth());
                        origin3dy = origin3dMouseDragStartPoint.y + Math.round((getChip().getMaxSize() * ((float) -dy)) / drawable.getHeight());
                    }
                }
                repaint(100);
                // log.info("repaint called for");
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
            }
        });
    }

    public boolean is3DEnabled() {
        return displayMethod instanceof DisplayMethod3D;
    }

    public boolean isZoomMode() {
        return zoomMode;
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
//        synchronized (drawable.getTreeLock()) {
        try {
//                drawable.getContext().makeCurrent();
            drawable.display(); // we call the drawable's display method that ends up calling us back via our local
            // display(GLAutoDrawable)!! very important to get this right
        } catch (final GLException e) {
            if (!(e.getCause() instanceof InterruptedException)) {
                log.warning(e.toString());
            }
        } catch (final RuntimeException ie) {
            if (!(ie.getCause() instanceof InterruptedException)) {
                log.warning(ie.toString());
            }
        } finally {
//                drawable.getContext().release();
        }
//        }
    }

    public void removeGLEventListener(final GLEventListener listener) {
    }

    /**
     * calls repaint on the drawable
     */
    public void repaint() {
        drawable.repaint();
    }

    /**
     * calls repaint on the drawable
     *
     * @param tm time to repaint within, in ms
     */
    public void repaint(final long tm) {
        drawable.repaint(tm);
    }

    /**
     * Called on reshape of canvas. Determines which way the chip fits into the
     * display area optimally and calls for a new orthographic projection to
     * achieve this filling. Finally sets the viewport to the entire drawable
     * area.
     */
    @Override
    public void reshape(final GLAutoDrawable drawable, final int x, final int y, final int width,
            final int height) {
        final GL2 gl = drawable.getGL().getGL2();
        checkGLError(gl, glu, "at start of reshape");

        gl.glLoadIdentity();
        final int chipSizeX = chip.getSizeX();
        final int chipSizeY = chip.getSizeY();
        float newscale;
        final float border = 0; // getBorderSpacePixels()*(float)height/width;
        if (chipSizeY > chipSizeX) {
            // chip is tall and skinny, so set scaleChipPixels2ScreenPixels by frame height/chip height
            newscale = (height - border) / chipSizeY;
            fillsVertically = true;
            fillsHorizontally = false;
            if ((newscale * chipSizeX) > width) { // unless it runs into left/right, then set to fill width
                newscale = (width - border) / chipSizeX;
                fillsHorizontally = true;
                fillsVertically = false;
            }
        } else {
            // chip is square or squat, so set scaleChipPixels2ScreenPixels by frame width / chip width
            newscale = (width - border) / chipSizeX;
            fillsHorizontally = true;
            fillsVertically = false;
            if ((newscale * chipSizeY) > height) {// unless it runs into top/bottom, then set to fill height
                newscale = (height - border) / chipSizeY;
                fillsVertically = true;
                fillsHorizontally = false;
            }
        }
        checkGLError(gl, glu, "before setDefaultProjection in reshape");
        setDefaultProjection(gl, drawable); // this sets orthographic projection so that chip pixels are scaled to the
        // drawable area
        gl.glViewport(0, 0, width, height);
        repaint();
    }

    protected String scalePrefsKey() {
        if (chip == null) {
            return "ChipCanvas.j2dScale";
        } else {
            return "ChipCanvas.j2dScale" + chip.getClass().getSimpleName();
        }
    }

    protected void set3dOrigin(final int x, final int y) {
        setOrigin3dy(y);
        setOrigin3dx(x);
        prefs.putInt("ChipCanvas.origin3dx", x);
        prefs.putInt("ChipCanvas.origin3dy", y);
    }

    public void setAnglex(final float anglex) {
        this.anglex = anglex;
        prefs.putFloat("ChipCanvas.anglex", anglex);
    }

    public void setAngley(final float angley) {
        this.angley = angley;
        prefs.putFloat("ChipCanvas.angley", angley);
    }

    /**
     * Orthographic projection clipping area.
     */
    public class ClipArea {

        /**
         * This clipping area is around the chip size by a negative amount for
         * left and bottom and a quantity larger than the chip size for the
         * right and top.
         */
        public float left = 0, right = 0, bottom = 0, top = 0;

        ClipArea(final float l, final float r, final float b, final float t) {
            left = l;
            right = r;
            bottom = b;
            top = t;
        }
    }

    /**
     * The actual clipping box bounds for the default orthographic projection.
     */
    private final ClipArea clipArea = new ClipArea(0, 0, 0, 0);

    /**
     * Returns the actual clipping area in model space (chip pixel) coordinates.
     * This clipping area is around the chip size by a negative amount for left
     * and bottom and a quantity larger than the chip size for the right and
     * top.
     *
     * This method is not synchronized.
     *
     * @return the clip area; only valid after display has been called.
     * @see #setDefaultProjection(com.jogamp.opengl.GL,
     * com.jogamp.opengl.GLAutoDrawable)
     */
    public ClipArea getClipArea() {
        return clipArea;
    }

    /**
     * Border around chip in model space coordinate (chip pixels).
     *
     * @see #setDefaultProjection(com.jogamp.opengl.GL,
     * com.jogamp.opengl.GLAutoDrawable)
     * @see #getScale()
     */
    public class Borders {

        /**
         * Border space in chip pixel units around left/right and bottom/top of
         * displayed chip pixel array. The total border is twice the individual
         * <code>leftRight</code> or <code>bottomTop</code>.
         */
        public float leftRight = 0, bottomTop = 0;
    };

    /**
     * The actual borders in model space around the chip area.
     */
    private final Borders borders = new Borders();

    /**
     * Returns the actual borders in model space (chip pixels) around the drawn
     * pixel array.
     *
     * This method is not synchronized.
     *
     * @return the borders; only valid after display has been called.
     * @see #setDefaultProjection(com.jogamp.opengl.GL,
     * com.jogamp.opengl.GLAutoDrawable)
     */
    public Borders getBorders() {
        return borders;
    }

    /**
     * Sets the projection matrix so that we get an orthographic projection that
     * is the size of the canvas with z volume -ZCLIP to ZCLIP padded with extra
     * space around the sides.
     *
     * @param g the GL context
     * @param d the GLAutoDrawable canvas
     */
    protected void setDefaultProjection(final GL2 g, final GLAutoDrawable d) {
        /*
         * void glOrtho(GLdouble left,
         * GLdouble right,
         * GLdouble bottom,
         * GLdouble top,
         * GLdouble near,
         * GLdouble far)
         */
        checkGLError(g, glu, "at start of setDefaultProjection");
        final int w = drawable.getWidth(), h = drawable.getHeight(); // w,h of screen

        final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size

        if (sx == 0 || sy == 0) {
            log.warning("zero sized chip for sizeX or sizeY; something is wrong. Did you forget to set the chip size in pixels in its constructor?");
            return;
        }
        final float border = getBorderSpacePixels(); // desired smallest border in screen pixels
        float glScale;
        checkGLError(g, glu, "before setDefaultProjection");
        g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        // now we set the clipping volume so that the volume is clipped according to whether the window is tall (ar>1)
        // or wide (ar<1).

        if (isFillsHorizontally()) { // tall
            glScale = (w - (2 * border)) / sx; // chip pix to screen pix scaling in horizontal&vert direction
            float b = border / glScale; // l,r border in model coordinates
            if (b <= 0) {
                b = 1;
            }
            float bb = ((h / glScale) - sy) / 2; // leftover y in model coordinates that makes up vertical border
            if (bb <= 0) {
                bb = 1;
            }
            clipArea.left = -b;
            clipArea.right = sx + b;
            clipArea.bottom = -bb;
            clipArea.top = sy + bb;
            borders.leftRight = b;
            borders.bottomTop = bb;
            g.glOrtho(-b, sx + b, -bb, (sy + bb), ZCLIP, -ZCLIP); // clip area has same ar as screen!
        } else {
            glScale = (h - (2 * border)) / sy;
            float b = border / glScale;
            if (b <= .5f) {
                b = 1;
            }
            float bb = ((w / glScale) - sx) / 2; // leftover y in model coordinates that makes up vertical border
            if (bb <= 0) {
                bb = 1;
            }
            clipArea.left = -bb;
            clipArea.right = sx + bb;
            clipArea.bottom = -b;
            clipArea.top = sy + b;
            borders.leftRight = bb;
            borders.bottomTop = b;
            g.glOrtho(-bb, (sx + bb), -b, sy + b, ZCLIP, -ZCLIP);
        }
        setScale(glScale);
        g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    }

    /**
     * This method sets the pixel drawing scale so that e.g. s=2 means a chip
     * pixel occupies 2 screen pixels.
     *
     * @param s size of chip pixel in screen pixels.
     */
    private void setScale(final float s) {
        scaleChipPixels2ScreenPixels = s;
    }

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
        gl.glTranslatef(x, y, -1);
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

    public void zoomIn() {
        getZoom().zoomin();
    }

    public void zoomOut() {
        getZoom().zoomout();
    }

    public void unzoom() {
        getZoom().unzoom();
    }

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
     * Sets the border around the drawn pixel canvas.
     *
     * @param borderSpacePixels in screen pixels.
     */
    public void setBorderSpacePixels(final int borderSpacePixels) {
        this.borderSpacePixels = borderSpacePixels;
        insets.bottom = borderSpacePixels;
        insets.top = borderSpacePixels;
        insets.left = borderSpacePixels;
        insets.right = borderSpacePixels;
        prefs.putInt("borderSpacePixels", this.borderSpacePixels);
    }

    /**
     * Clips a Point to within the chip size. The point is modified to fit
     * within the size of the Chip2D.
     *
     * @param p a Point
     */
    void clipPoint(final Point p) {
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

    void zoomCenter() {
        getZoom().zoomcenter();
    }

    public ChipCanvas.Zoom getZoom() {
        return zoom;
    }

    public void setZoom(final ChipCanvas.Zoom zoom) {
        this.zoom = zoom;
    }

    /**
     * Chip fills drawable horizontally.
     *
     * @return the fillsHorizontally
     */
    public boolean isFillsHorizontally() {
        return fillsHorizontally;
    }

    /**
     * Chip fills drawable vertically.
     *
     * @return the fillsVertically
     */
    public boolean isFillsVertically() {
        return fillsVertically;
    }

    /**
     * Encapsulates zooming the view
     */
    public class Zoom {

        final float zoomStepRatio = 1.3f;
        private Point startPoint = new Point();
        private Point endPoint = new Point();
        private Point centerPoint = new Point();
        float zoomFactor = 1;
        private boolean zoomEnabled = false;
        Point tmpPoint = new Point();
        double projectionLeft, projectionRight, projectionBottom, projectionTop; // projection rect points, computed on
        // zoom

        private void setProjection(final GL2 gl) {
            // define a new projection matrix so that the clipping volume is centered on the centerPoint
            // and the size of the rectangle zooms up by a factor of zoomFactor on the original
            // scaleChipPixels2ScreenPixels
            gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            gl.glLoadIdentity();
            final int w = drawable.getWidth(), h = drawable.getHeight(); // w,h of screen
            final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
            if (isFillsHorizontally()) { // tall
                final float wx2 = sx / zoomFactor / 2;
                clipArea.left = centerPoint.x - wx2;
                clipArea.right = centerPoint.x + wx2;
                final float wy2 = (wx2 * h) / w;
                clipArea.bottom = centerPoint.y - wy2;
                clipArea.top = centerPoint.y + wy2;
                borders.leftRight = 0;
                borders.bottomTop = 0;
                gl.glOrtho(clipArea.left, clipArea.right, clipArea.bottom, clipArea.top, ZCLIP, -ZCLIP); // clip area
                // has same
                // ar as
                // screen!
            } else {
                final float wy2 = sy / zoomFactor / 2;
                clipArea.bottom = centerPoint.y - wy2;
                clipArea.top = centerPoint.y + wy2;
                final float wx2 = (wy2 * w) / h;
                clipArea.left = centerPoint.x - wx2;
                clipArea.right = centerPoint.x + wx2;
                borders.leftRight = 0;
                borders.bottomTop = 0;
                gl.glOrtho(clipArea.left, clipArea.right, clipArea.bottom, clipArea.top, ZCLIP, -ZCLIP); // clip area
                // has same
                // ar as
                // screen!
            }
        }

        public Point getStartPoint() {
            return startPoint;
        }

        public void setStartPoint(final Point startPoint) {
            this.startPoint = startPoint;
        }

        public Point getEndPoint() {
            return endPoint;
        }

        public void setEndPoint(final Point endPoint) {
            this.endPoint = endPoint;
        }

        private void unzoom() {
            setZoomEnabled(false);
            zoomFactor = 1;
            getZoom().setStartPoint(new Point(0, 0));
            final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
            centerPoint.setLocation(sx / 2, sy / 2);
            set3dOrigin(0, 0);
            // getZoom().setEndPoint(new Point(getChip().getSizeX(), getChip().getSizeY()));
            // if (!System.getProperty("os.name").contains("Mac")) {//crashes on mac os x 10.5
            // GL g = drawable.getGL();
            // g.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
            // g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
            // g.glOrtho(-getBorderSpacePixels(), drawable.getWidth() + getBorderSpacePixels(), -getBorderSpacePixels(),
            // drawable.getHeight() + getBorderSpacePixels(), ZCLIP, -ZCLIP);
            // g.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
            // }
        }

        private void zoomcenter() {
            centerPoint = getMousePixel();
            setZoomEnabled(true);
        }

        private void zoomin() {
            centerPoint = getMousePixel();
            zoomFactor *= zoomStepRatio;
            setZoomEnabled(true);
        }

        private void zoomout() {
            centerPoint = getMousePixel();
            zoomFactor /= zoomStepRatio;
            setZoomEnabled(true);
        }

        public boolean isZoomEnabled() {
            return zoomEnabled;
        }

        public void setZoomEnabled(final boolean zoomEnabled) {
            this.zoomEnabled = zoomEnabled;
        }
    }

    public void addGLEventListener(final GLEventListener listener) {
        System.out.println("addGLEventListener(" + listener + ")");
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
            if (aeViewer != null && aeViewer.getPlayerControls() != null && aeViewer.getPlayerControls().isSliderBeingAdjusted()) {
                return;
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
     * @param d the drawable we are drawing in.
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
        // TODO Auto-generated method stub

    }

    // /**
    // * Returns the insets in pixels for the chip canvas inside the GLCanvas
    // * @return the insets
    // */
    // public Insets getInsets() {
    // return insets;
    // }
    //
    // /**
    // * Allows setting the insets for the canvas inside the GLCanvas
    // *
    // *
    // * @param insets the insets to set, in pixels
    // */
    // public void setInsets(Insets insets) {
    // this.insets = insets;
    // }
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

}
