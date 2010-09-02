/*
 * ChipCanvas.java
 *
 * Created on May 2, 2006, 1:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.*;
import com.sun.opengl.util.*;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.BasicStroke;
import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.*;

/**
 * Superclass for classes that paint rendered AE data to graphics devices.
 *<p>
 * This is Canvas (AWT component) for rendering retina events.
 * Note this is a heavyweight componnent and doesn't understand Swing layering. Thus care must be
 *taken to ensure Swing components show over it. This component also
 * uses a page-flipping BufferStrategy to eliminate tearing by flipping pages on
 *monitor refresh.
 * <p>
 * The graphics context is obtained here in the component and its
 * origin is the UL corner of the component when using Java2D rendering, or LL corner
 * if using OpenGL rendering. The context is translated
 *to center the rendering in the Canvas.
 * This canvas can either use Java2D or OpenGL to render, and it can either actively or passively render.
 *<p>
 *If 3-d event rendering is enabled, the raw events are painted out in space-time, with time axis defined by duration from first to last event
 *in the packet.
 *<p>
 *If 3-d histogramming is enabled, then the image frame from the ChipRenderer is rendered as a 3-d surface that the user can rotate as desired.
 *
 * @author tobi
 */
public class ChipCanvas implements GLEventListener, Observer {

    protected Preferences prefs = Preferences.userNodeForPackage(ChipCanvas.class);
    /** Default scaling from chip pixel to screen pixels */
    protected static final float SCALE_DEFAULT = 4f;
    protected AEViewer aeViewer;
    protected float anglex = prefs.getFloat("ChipCanvas.anglex", 5);
    protected float angley = prefs.getFloat("ChipCanvas.angley", 10);
    private Chip2D chip;
    protected final int colorScale = 255;
    protected GLCanvas drawable;
    /** fr is the rendered event data that we draw. X is the first dimenion, Y is the second dimension, RGB 3 vector is the last dimension */
    protected float[][][] fr; // this is legacy data that we render here to the screen
    protected RenderingFrame frameData; // this is new form of pixel data to render
    protected GLU glu; // instance this if we need glu calls on context
    protected GLUT glut = new GLUT();
    protected Logger log = Logger.getLogger("Graphics");
    protected boolean openGLEnabled = prefs.getBoolean("ChipCanvas.openGLEnabled", false);
    private float origin3dx = prefs.getInt("ChipCanvas.origin3dx", 0);
    private float origin3dy = prefs.getInt("ChipCanvas.origin3dy", 0);
    protected int pheight = prefs.getInt("ChipCanvas.pheight", 512);
    protected int[] pixels;
    /** defines the minimum canvas size in pixels; used when chip size has not been set to non zero value
     */
    public static final int MIN_DIMENSION = 70;
    /** width and height of pixel array in canvas in screen pixels. these are different than the actual canvas size */
    protected int pwidth = prefs.getInt("ChipCanvas.pwidth", 512);
    // the number of screen pixels for one retina pixel
    protected float j2dScale = prefs.getFloat("ChipCanvas.j2dScale", 3f);
    protected static final Color selectedPixelColor = Color.blue;
    protected GLUquadric selectedQuad;
    public BufferStrategy strategy;
    /** the translation of the actual chip drawing area in the canvas, in screen pixels */
    protected int xt, yt;
    private ChipCanvas.Zoom zoom = new Zoom();
    protected boolean zoomMode = false; // true while user is dragging zoom box
    // reused imageOpenGL for OpenGL image grab
    private BufferedImage imageOpenGL;
    // reused by canvas to hold bytes of image when writing java Image
    ByteBuffer byteBuffer = null;
    // boolean to flag that present opengl display call should write imageOpenGL
    volatile boolean grabNextImageEnabled = false;
    /** border around drawn pixel array in screen pixels */
    private int borderSpacePixels = 20;
    /** border in screen pixels when in 3d space-time rendering mode */
    protected final int BORDER3D = 70;
    /** Insets of the drawn chip canvas in the window */
    protected Insets insets = new Insets(borderSpacePixels, borderSpacePixels, borderSpacePixels, borderSpacePixels);
    private boolean fillsHorizontally = false, fillsVertically = false; // filled in in the reshape method to show how chip fills drawable space
    private double ZCLIP = 1;
    private TextRenderer renderer=null;

    /** Creates a new instance of ChipCanvas */
    public ChipCanvas(Chip2D chip) {
        this.chip = chip;

        // design capabilities of opengl canvas
        GLCapabilities caps = new GLCapabilities();
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);
        glu = new GLU();

        // make the canvas
        try {
            drawable = new GLCanvas(caps);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            System.err.println("java.libary.path=" + System.getProperty("java.library.path"));
            System.err.println("user.dir=" + System.getProperty("user.dir"));
            System.exit(1);
        }
        drawable.setLocale(Locale.US); // to avoid problems with other language support in JOGL

        // will always getString invalid operation here
        // checkGLError(drawable.getGL(),glu,"before add event listener");
        // add us as listeners for the canvas. then when the display wants to redraw display() will be called. or we can call drawable.display();
        drawable.addGLEventListener(this);
        // same here, canvas not yet valid
        // checkGLError(drawable.getGL(),glu,"after add event listener");

//        Dimension ss=Toolkit.getDefaultToolkit().getScreenSize();
        j2dScale = prefs.getFloat(scalePrefsKey(), 4); // if j2dScale comes from prefs, then j2dScale gets smaller and smaller with each window that is opened
        setScale(getScale());
        drawable.setSize(200, 200);  // set a size explicitly here
        initComponents();
        chip.addObserver(this);

        // if this canvas was constructed from a chip, then fill the display methods from that chip's ChipCanvas, if it exists and has them
        if (displayMethods.isEmpty() && chip.getCanvas() != null && chip.getCanvas().getDisplayMethod() != null) {
            displayMethods.add(chip.getCanvas().getDisplayMethod());
        }
    }

    /** call this method so that next open gl rendering by display(GLAutoDrawable) writes imageOpenGL */
    public void grabNextImage() {
        grabNextImageEnabled = true;
    }    // here are methods for toggling display method and building menu for AEViewer
    private JMenu displayMethodMenu = new JMenu("Display method");
    private ButtonGroup menuButtonGroup = new ButtonGroup();

    public JMenu getDisplayMethodMenu() {
        return displayMethodMenu;
    }
    /** the list of display methods for this canvas*/
    private ArrayList<DisplayMethod> displayMethods = new ArrayList<DisplayMethod>();

    /** adds a display method to this canvas
    @param m the method
     */
    public void addDisplayMethod(DisplayMethod m) {
        if (getDisplayMethods().contains(m)) {
            return;
        }
        getDisplayMethods().add(m);
        displayMethodMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        JRadioButtonMenuItem mi = new JRadioButtonMenuItem(m.getDescription());
        m.setMenuItem(mi);
        mi.setActionCommand(m.getDescription());
        mi.putClientProperty("displayMethod", m); // add display method as client property
        mi.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                DisplayMethod m = (DisplayMethod) (((JComponent) e.getSource()).getClientProperty("displayMethod"));
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

    /** Removes a DisplayMethod for this ChipCanvas
     *
     * @param m the method. If null or not in list, warning is logged.
     */
    public void removeDisplayMethod(DisplayMethod m) {
        if (m == null || !getDisplayMethods().contains(m)) {
            log.warning("Cannot remove displayMethod: no such DisplayMethod " + m + " in the getDisplayMethods() list");
            return;
        }
        displayMethods.remove(m);
        displayMethodMenu.remove(m.getMenuItem());
    }
    protected DisplayMethod displayMethod;
    int currentDisplayMethodIndex = 0;

    /** returns the current display method
    @return the current display method
     */
    public DisplayMethod getDisplayMethod() {
        return displayMethod;
    }

    /** cycle to the next display method */
    public void cycleDisplayMethod() {
        if (++currentDisplayMethodIndex >= getDisplayMethods().size()) {
            currentDisplayMethodIndex = 0;
        }
        setDisplayMethod(currentDisplayMethodIndex);
    }

    /** sets the display method
    @param m the method to use
     */
    public void setDisplayMethod(DisplayMethod m) {
//        System.out.println("set display method="+m);
//        Thread.currentThread().dumpStack();

        this.displayMethod = m;
        if (m != null) {
            if (m.getMenuItem() == null) {
                // TODO comes here when preferred display method is constructed, which is not yet assigned a menu item like the others
                // which are constructed by default
                log.warning("no menu item for display method " + m + " cannot set it as the display method");
            } else {
//            log.info("setting display method to " + m.getDescription());
                m.getMenuItem().setSelected(true);
            }
        }
        repaint();
    }

    /** sets the display method using the menu name
    @param description the name, fully qualified class name or simple class name; only the last part is used, after the final ".".
     */
    public void setDisplayMethod(String description) {
        for (DisplayMethod method : getDisplayMethods()) {
            String s = description;
            int ind = s.lastIndexOf('.');
            s = s.substring(ind + 1);
            if (method.getDescription().equals(s)) {
//                log.info("setting display method=" + m);
                setDisplayMethod(method);
                return;
            }
        }
        StringBuilder sb = new StringBuilder("couldn't set display method to " + description + ", not in list of methods which are as follows:\n");
        for (DisplayMethod method : getDisplayMethods()) {
            sb.append(method.getDescription() + "\n");
        }
        log.warning(sb.toString());
    }

    /** sets the current display method to a particular item
    @param index the index of the method in the list of methods
     */
    protected void setDisplayMethod(int index) {
        setDisplayMethod(getDisplayMethods().get(index));
    }

    /** opengl calls this when it wants to redraw, and we call it when we actively render.
     * @param drawable the surface from which we can getString the context with getGL
    @see net.sf.jaer.graphics.DisplayMethod#setupGL which sets up GL context for display methods
     */
    public synchronized void display(GLAutoDrawable drawable) {
        if (!isOpenGLEnabled()) {
            paint(null);
        } else {
//            if(drawable.getContext().makeCurrent()!=GLContext.CONTEXT_CURRENT){
//                log.warning("current drawing context not current, skipping");
//                return;
//            }
            GL gl = drawable.getGL();
            gl.glMatrixMode(GL.GL_MODELVIEW);
            gl.glLoadIdentity();

            checkGLError(gl, glu, "before setting projection");
//            gl.glPushMatrix(); // don't push so that mouse selection has correct matrices
            {
                if (getZoom().isZoomEnabled()) {
                    getZoom().setProjection(gl);

                } else {
                    setDefaultProjection(gl, drawable);
                }
                checkGLError(gl, glu, "after setting projection, before displayMethod");
                DisplayMethod m = getDisplayMethod();
                if (m == null) {
                    log.warning("null display method for chip " + getChip());
                } else {
                    m.display(drawable);
                }
//                checkGLError(gl, glu, "after " + getDisplayMethod() + ".display()");
                checkGLError(gl, glu, "after DisplayMethod.display()");
                showSpike(gl);
                annotate(drawable);
                checkGLError(gl, glu, "after FrameAnnotator (EventFilter) annotations");
            }
            if(getChip() instanceof AEChip && ((AEChip)chip).getFilterChain()!=null && ((AEChip)chip).getFilterChain().getProcessingMode()==FilterChain.ProcessingMode.ACQUISITION){
                if ( renderer == null ){
                    renderer = new TextRenderer(new Font("SansSerif",Font.PLAIN,10),true,true);
                }
                renderer.begin3DRendering();
                renderer.setColor(0,0,1,0.8f);
                final String s = "Real-time mode - raw data shown here";
                Rectangle2D r = renderer.getBounds(s);
                renderer.draw3D(s,1f,1f,0f,(float)(chip.getSizeX() / r.getWidth()));
                renderer.end3DRendering();
            }
            gl.glFlush();
//            gl.glPopMatrix();
            checkGLError(gl, glu, "after display");
//            drawable.swapBuffers(); // don't use, very slow and autoswapbuffers is set
//            zoom.drawZoomBox(gl);
            if (grabNextImageEnabled) {
                grabImage(drawable);
                grabNextImageEnabled = false;
            }
        }
    }
    float[] rgbVec = new float[3];

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
//    log.info("display changed");
        // should be empty according to jogl user guide.
//        System.out.println("displayChanged");
    }

    // TODO javadoc
    public float getAnglex() {
        return anglex;
    }

    public float getAngley() {
        return angley;
    }

    /** The actual drawing surface is a Canvas and this method returns a reference to it.
     *
     * @return the actual drawing Canvas.
     */
    public Canvas getCanvas() {
        return drawable;
    }

    /** Pixel drawing j2dScale. 1 pixel is rendered to getScale screen pixels.
     *
     * @return j2dScale in screen pixels/chip pixel.
     */
    public float getScale() {
        return this.j2dScale;
    }

    /** A utility method that returns an AWT Color from float rgb values */
    protected final Color getPixelColor(float red, float green, float blue) {
        int r = (int) (colorScale * red);
        int g = (int) (colorScale * green);
        int b = (int) (colorScale * blue);
        int value = ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                ((b & 0xFF) << 0);
        return new Color(value);
    }

    private boolean mouseWasInsideChipBounds=true;

    /** Finds the chip pixel from a ChipCanvas point.
     * From <a href="http://processing.org/discourse/yabb_beta/YaBB.cgi?board=OpenGL;action=display;num=1176483247">this forum link</a>.
     *
     * @param mp a Point in ChipCanvas pixels.
     * @return the AEChip pixel, clipped to the bounds of the AEChip.
     */
    public Point getPixelFromPoint(Point mp) {
        // this method depends on current GL context being the one that is used for rendering.
        // the display method should not push/pop the matrix stacks!!
        if (mp == null) {
//            log.warning("null Point (outside entire canvas?), returning center pixel");
            return new Point(chip.getSizeX() / 2, chip.getSizeY() / 2);
        }
        try {
            int ret = drawable.getContext().makeCurrent();
            if (ret != GLContext.CONTEXT_CURRENT) {
                throw new GLException("couldn't make context current");
            }
        } catch (GLException e) {
            log.warning("couldn't make GL context current, mouse position meaningless: " + e.toString());
        }
        int viewport[] = new int[4];
        double mvmatrix[] = new double[16];
        double projmatrix[] = new double[16];
        int realy = 0;// GL y coord pos
        double wcoord[] = new double[4];// wx, wy, wz;// returned xyz coords
        //set up a floatbuffer to getString the depth buffer value of the mouse position
        FloatBuffer fb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        GL gl = drawable.getContext().getGL();
        gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
        gl.glGetDoublev(GL.GL_MODELVIEW_MATRIX, mvmatrix, 0);
        gl.glGetDoublev(GL.GL_PROJECTION_MATRIX, projmatrix, 0);
        /* note viewport[3] is height of window in pixels */
        realy = viewport[3] - (int) mp.getY() - 1;
        //Get the depth buffer value at the mouse position. have to do height-mouseY, as GL puts 0,0 in the bottom left, not top left.
        gl.glReadPixels(mp.x, realy, 1, 1, GL.GL_DEPTH_COMPONENT, GL.GL_FLOAT, fb);
        float z = 0; //fb.getString(0);
        glu.gluUnProject((double) mp.getX(), (double) realy, z,
                mvmatrix, 0,
                projmatrix, 0,
                viewport, 0,
                wcoord, 0);
        Point p = new Point();
        p.x = (int) Math.round(wcoord[0]);
        p.y = (int) Math.round(wcoord[1]);
        if(p.x<0 || p.x>chip.getSizeX()-1 || p.y<0|p.y>chip.getSizeY()-1) mouseWasInsideChipBounds=false; else mouseWasInsideChipBounds=true;
        clipPoint(p);
//        log.info("Mouse xyz=" + mp.getX() + "," + realy + "," + z + "   Pixel x,y=" + p.x + "," + p.y);
        return p;
    }

    /** Finds the current AEChip pixel mouse position, or center of array if not inside.
     *
     * @return the AEChip pixel, clipped to the bounds of the AEChip
     */
    public Point getMousePixel() {
        Point mp = getCanvas().getMousePosition();
        return getPixelFromPoint(mp);
    }

    /** Returns state of mouse from last call to getPixelFromPoint; true if mouse inside bounds of chip drawing area.
     *
     * @return true if was inside, false otherwise.
     */
    public boolean wasMousePixelInsideChipBounds (){
        return mouseWasInsideChipBounds;
    }

    /** Takes a MouseEvent and returns the AEChip pixel.
     * @return pixel x,y location (integer point) from MouseEvent. Accounts for scaling and borders of chip display area */
    public Point getPixelFromMouseEvent(MouseEvent evt) {
        Point mp = evt.getPoint();
        return getPixelFromPoint(mp);
    }

    protected final int getPixelRGB(float red, float green, float blue) {
        int r = (int) (colorScale * red);
        int g = (int) (colorScale * green);
        int b = (int) (colorScale * blue);
        int value = ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                ((b & 0xFF) << 0);
        return value;
    }

    public void init(GLAutoDrawable drawable) {
        // drawable.setGL(new DebugGL(drawable.getGL()));
        drawable.setAutoSwapBufferMode(true);
        GL gl = drawable.getGL();

        log.info(
                "OpenGL implementation is: " + gl.getClass().getName() + "\nGL_VENDOR: " + gl.glGetString(GL.GL_VENDOR) + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER) + "\nGL_VERSION: " + gl.glGetString(GL.GL_VERSION) //                + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
                );
        float glVersion = Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0, 3));
        if (glVersion < 1.3f) {
            log.warning("\n\n*******************\nOpenGL version " + glVersion + " < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
        }
//        System.out.println("GLU_EXTENSIONS: "+glu.gluGetString(GLU.GLU_EXTENSIONS));

        gl.setSwapInterval(1);
        gl.glShadeModel(GL.GL_FLAT);

        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();

        gl.glRasterPos3f(0, 0, 0);
        gl.glColor3f(1, 1, 1);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "Initialized display");
        checkGLError(gl, glu, "after init");

    }

    protected void initComponents() {
        unzoom();
        if (getRenderer() != null) {
            drawable.addMouseListener(new MouseAdapter() {

                public void mousePressed(MouseEvent evt) {
                    Point p = getPixelFromMouseEvent(evt);
//                    if (!isZoomMode()) {

                    //                System.out.println("evt="+evt);
                    if (evt.getButton() == 1) {
                        getRenderer().setXsel((short) -1);
                        getRenderer().setYsel((short) -1);
//            System.out.println("cleared pixel selection");
                    } else if (evt.getButton() == 3) {
                        // we want mouse click location in chip pixel location
                        // don't forget that there is a borderSpacePixels on the orthographic viewport projection
                        // this border means that the pixels are actually drawn on the screen in a viewport that has a borderSpacePixels sized edge on all sides
                        // for simplicity because i can't figure this out, i have set the borderSpacePixels to zero

//                        log.info(" width=" + drawable.getWidth() + " height=" + drawable.getHeight() + " mouseX=" + evt.getX() + " mouseY=" + evt.getY() + " xt=" + xt + " yt=" + yt);

                        //                        renderer.setXsel((short)((0+((evt.x-xt-borderSpacePixels)/j2dScale))));
                        //                        renderer.setYsel((short)(0+((getPheight()-evt.y+yt-borderSpacePixels)/j2dScale)));
                        getRenderer().setXsel((short) p.x);
                        getRenderer().setYsel((short) p.y);
                        log.info("Selected pixel x,y=" + getRenderer().getXsel() + "," + getRenderer().getYsel());
                    }
//                    } else if (isZoomMode()) { // zoom startZoom
//                        zoom.startZoom(p);
//                    }
                }

                public void mouseReleased(MouseEvent e) {
                    if (is3DEnabled()) {
                        log.info("3d rotation: angley=" + angley + " anglex=" + anglex + " 3d origin: x=" + getOrigin3dx() + " y=" + getOrigin3dy());
                    }
                    //else
//                        if (isZoomMode()) {
//                        Point p = getPixelFromMouseEvent(e);
//                        zoom.endZoom(p);
////                    zoom.endX=p.x;
////                    zoom.endY=p.y;
////                    setZoomMode(false);
//                    }
                }
            });
        } // renderer!=null

        drawable.addMouseMotionListener(new MouseMotionListener() {

            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK, but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx() & but1mask) == but1mask) {
                    if (is3DEnabled()) {
                        final float maxAngle = 180f;
                        setAngley(maxAngle * (x - drawable.getWidth() / 2) / (float) drawable.getWidth());
                        setAnglex(maxAngle * (y - drawable.getHeight() / 2) / (float) drawable.getHeight());
                    } else if (isZoomMode()) {
//                        System.out.print("z");
                    }
                } else if ((e.getModifiersEx() & but3mask) == but3mask) {
                    if (is3DEnabled()) {
                        // mouse position x,y is in pixel coordinates in window canvas, but later on, the events will be drawn in
                        // chip coordinates (transformation applied). therefore here we set origin in pixel coordinates based on mouse
                        // position in window.
                        set3dOrigin(Math.round(getChip().getMaxSize() * ((float) x - drawable.getWidth() / 2) / drawable.getWidth()), Math.round(getChip().getMaxSize() * (drawable.getHeight() / 2 - (float) y) / drawable.getHeight()));
                    }
                }
            }

            public void mouseMoved(MouseEvent e) {
            }
        });
    }

    public boolean is3DEnabled() {
        return displayMethod instanceof DisplayMethod3D;
    }

    public boolean isOpenGLEnabled() {
        return openGLEnabled;
    }

    public boolean isZoomMode() {
        return zoomMode;
    }

    /** use to paint with Java2D using BufferStrategy */
    protected void paint() {
        paint(null);
    }

    /** used for active and passive rendering by Java2D.
     * @param g null to use BufferStrategy graphics (created on demand), non-null to paint to some other rendering context, e.g. an Image that can be written to a file
     */
    public synchronized void paint(Graphics g) {
//        log.info("paint with graphics="+g);

        Graphics2D g2 = null;
        if (strategy == null) {
            try {
                drawable.createBufferStrategy(2);
                strategy = drawable.getBufferStrategy();
                BufferCapabilities cap = strategy.getCapabilities();
//                ImageCapabilities imCapFront = cap.getFrontBufferCapabilities();
//                ImageCapabilities imCapBack = cap.getBackBufferCapabilities();
                boolean isPageFlip = cap.isPageFlipping();
                boolean isMultiBufferAvailable = cap.isMultiBufferAvailable();
                log.info("isPageFlipping=" + isPageFlip + " isMultiBufferAvailable=" + isMultiBufferAvailable);
            } catch (Exception e) {
                log.warning("couldn't create BufferStrategy yet: " + e.getMessage());
            }
        }
        if (g == null) {
            //            System.out.println("**********paint("+g+")");
            if (strategy == null) {
                return;
            }
            g2 = (Graphics2D) strategy.getDrawGraphics();
        } else {
            g2 = (Graphics2D) g;
        }
        try {
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g2.setColor(Color.black);
            g2.fill(drawable.getBounds());
            // translate to center view; depends on how chip fills display
            if (isFillsHorizontally()) {
                // translate vertically downwards by (height-sizeY*j2dScale)/2
                g2.translate(0, (getCanvas().getHeight() - j2dScale * chip.getSizeY()) / 2);
            } else {
                // fills canvas vertically, translate right by (width-sizeX*j2dScale)/2
                g2.translate((getCanvas().getWidth() - j2dScale * chip.getSizeX()) / 2, 0);
            }
            g2.scale(j2dScale, j2dScale);// AffineTransform.getScaleInstance(j2dScale,j2dScale));
            g2.setFont(g2.getFont().deriveFont(8f));


            //            g2.translate(.5,-.5);
            //            g2.transform(new AffineTransform(1,0,0,-1,0,0)); // flip y
            float gray = (float) getRenderer().getGrayValue();
            g2.setColor(new Color(gray, gray, gray));
            g2.fill(new Rectangle(0, 0, chip.getSizeX(), chip.getSizeY()));

            int sizex = chip.getSizeX(), sizey = chip.getSizeY();
            float[] fr = getRenderer().getPixmapArray();
            if (fr == null) {
                return;
            }
            int ind = 0;
            for (int y = 0; y < sizey; y++) {
                for (int x = 0; x < sizex; x++) {
                    if (fr[ind] != gray || fr[ind + 1] != gray || fr[ind + 2] != gray) {
                        g2.setColor(getPixelColor(fr[ind], fr[ind + 1], fr[ind + 2]));
                        g2.fillRect(x, sizey - y - 1, 1, 1);
                    }
                    ind += 3;
                }
            }


            int xsel = getRenderer().getXsel(), ysel = getRenderer().getYsel();
            if (xsel != -1 && ysel != -1) {
                g2.setColor(selectedPixelColor);
                //                g2.fillRect(j2dScale*renderer.xsel,pheight-j2dScale*(renderer.ysel+1),j2dScale, j2dScale);
                int radius = 1 + getRenderer().getSelectedPixelEventCount();
                g2.setStroke(new BasicStroke(.2f / getScale()));


                int xs = xsel - radius, ys = sizey - (ysel + radius);
                g2.drawOval(xs, ys, 2 * radius, 2 * radius);
                g2.drawString(xsel + "," + ysel, xsel, sizey - ysel);
            }
            // call all the annotators that have registered themselves. these are java2d annotators
//            annotate(g2);
            strategy.show();
        } catch (Exception e) {
            log.warning("ChipCanvas.display(): Graphics context error " + e);
            e.printStackTrace();
        }
    }

    /** Used for active rendering.
     * You call this when you want to actively render the frame.
     * Internally, this calls the display() method of the drawable, which by callback to display(GLAutoDrawable).
     * If openGL is disabled, then it calls paint() directly.
     */
    public void paintFrame (){
        if ( isOpenGLEnabled() ){
            try{
                drawable.display(); // we call the drawable's display method that ends up calling us back via our local display(GLAutoDrawable)!! very important to getString this right
            } catch ( GLException e ){
                if ( !( e.getCause() instanceof InterruptedException ) ){
                    log.warning(e.toString());
                }
            }
        } else{
            paint();
        }
    }

    public void removeGLEventListener(GLEventListener listener) {
    }

    /** calls repaint on the drawable */
    public synchronized void repaint() {
        drawable.repaint();
    }

    /** calls repaint on the drawable */
    public synchronized void repaint(long tm) {
        drawable.repaint(tm);
    }

    /** Called on reshape of canvas. Determines which way the chip fits into the display area optimally and calls
     * for a new orthographic projection to achieve this filling. Finally sets the viewport to the entire drawable area.
     */
    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL gl = drawable.getGL();
        gl.glLoadIdentity();
        final int chipSizeX = chip.getSizeX();
        final int chipSizeY = chip.getSizeY();
        float newscale;
        final float border = 0; // getBorderSpacePixels()*(float)height/width;
        if (chipSizeY > chipSizeX) {
            // chip is tall and skinny, so set j2dScale by frame height/chip height
            newscale = (float) (height - border) / chipSizeY;
            fillsVertically = true;
            fillsHorizontally = false;
            if (newscale * chipSizeX > width) { // unless it runs into left/right, then set to fill width
                newscale = (float) (width - border) / chipSizeX;
                fillsHorizontally = true;
                fillsVertically = false;
            }
        } else {
            // chip is square or squat, so set j2dScale by frame width / chip width
            newscale = (float) (width - border) / chipSizeX;
            fillsHorizontally = true;
            fillsVertically = false;
            if (newscale * chipSizeY > height) {// unless it runs into top/bottom, then set to fill height
                newscale = (float) (height - border) / chipSizeY;
                fillsVertically = true;
                fillsHorizontally = false;
            }
        }
        setScale(newscale); // TODO j2dScale meaningless now for opengl but not for java2d rendering
        setDefaultProjection(gl, drawable); // this sets orthographic projection so that chip pixels are scaled to the drawable area
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

    protected void set3dOrigin(int x, int y) {
        setOrigin3dy(y);
        setOrigin3dx(x);
        prefs.putInt("ChipCanvas.origin3dx", x);
        prefs.putInt("ChipCanvas.origin3dy", y);
    }

    public void setAnglex(float anglex) {
        this.anglex = anglex;
        prefs.putFloat("ChipCanvas.anglex", anglex);
    }

    public void setAngley(float angley) {
        this.angley = angley;
        prefs.putFloat("ChipCanvas.angley", angley);
    }

    public void setOpenGLEnabled(boolean openGLEnabled) {
        this.openGLEnabled = openGLEnabled;
        prefs.putBoolean("ChipCanvas.openGLEnabled", openGLEnabled);
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
    };
    /** The actual borders in model space around the chip area. */
    private Borders borders = new Borders();

    /** Sets the projection matrix so that we getString an orthographic projection that is the size of the
    canvas with z volume -ZCLIP to ZCLIP padded with extra space around the sides.

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
        final int w = drawable.getWidth(), h = drawable.getHeight(); // w,h of screen
        final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
        final float border = getBorderSpacePixels(); // desired smallest border in screen pixels
        float glScale;
        checkGLError(g, glu, "before setDefaultProjection");
        g.glMatrixMode(GL.GL_PROJECTION);
        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        // now we set the clipping volume so that the volume is clipped according to whether the window is tall (ar>1) or wide (ar<1).

        if (isFillsHorizontally()) { //tall
            glScale = (float) (w - 2 * border) / sx; // chip pix to screen pix scaling in horizontal&vert direction
            float b = border / glScale; // l,r border in model coordinates
            if (b <= 0) {
                b = 1;
            }
            float bb = (h / glScale - sy) / 2; // leftover y in model coordinates that makes up vertical border
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
            glScale = (float) (h - 2 * border) / sy;
            float b = border / glScale;
            if (b <= .5f) {
                b = 1;
            }
            float bb = (w / glScale - sx) / 2; // leftover y in model coordinates that makes up vertical border
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
        g.glMatrixMode(GL.GL_MODELVIEW);
    }

    /** This method sets the pixel drawing j2dScale so that
     * e.g. s=2 means a chip pixel occupies 2 screen pixels.
     * Only used for java2d rendering now.
     * @param s size of chip pixel in screen pixels.
     */
    public void setScale(float s) {
        prefs.putFloat(scalePrefsKey(), (float) Math.round(s)); // if we don't round, window gets smaller each time we open a new one...
        this.j2dScale = s;
    }

    /** Shows selected pixel spike count by drawn circle */
    protected void showSpike(GL gl) {        // show selected pixel that user can hear
        if (getRenderer() != null && getRenderer().getXsel() != -1 && getRenderer().getYsel() != -1) {
            showSpike(gl, getRenderer().getXsel(), getRenderer().getYsel(), getRenderer().getSelectedPixelEventCount());
        }
    }

    /** draws a circle at pixel x,y of size+.5 radius. size is used to indicate number of spikes in this 'frame'
     */
    protected void showSpike(GL gl, int x, int y, int size) {
        // circle
        gl.glPushMatrix();
        gl.glColor4f(0, 0, 1f, 0f);
        if (size > chip.getMinSize() / 3) {
            size = chip.getMinSize() / 3;
        }
        gl.glTranslatef(x, y, -1);
        selectedQuad = glu.gluNewQuadric();
        glu.gluQuadricDrawStyle(selectedQuad, GLU.GLU_FILL);
        glu.gluDisk(selectedQuad, size, size + 1, 16, 1);
        glu.gluDeleteQuadric(selectedQuad);
        gl.glPopMatrix();

        int font = GLUT.BITMAP_HELVETICA_18;

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

    public void update(Observable o, Object arg) {
        // if observable is a chip object and arg is a string size property then set a new j2dScale
        if (o == chip && arg instanceof String) {
            if (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY)) {
                setScale(prefs.getFloat(scalePrefsKey(), SCALE_DEFAULT));
                ZCLIP = chip.getMaxSize();
                unzoom();
            }
        }
        if (o instanceof Chip2D) {
            Chip2D c = (Chip2D) o;
            setRenderer(c.getRenderer());
        }
    }

    public Chip2DRenderer getRenderer() {
        return chip.getRenderer();
    }

    public void setRenderer(Chip2DRenderer renderer) {
        chip.setRenderer(renderer);
    }

    public ArrayList<DisplayMethod> getDisplayMethods() {
        return displayMethods;
    }

    public void setDisplayMethods(ArrayList<DisplayMethod> displayMethods) {
        this.displayMethods = displayMethods;
    }

    /** Returns minimum space around pixel array in screen pixels
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

    /** Sets the border around the drawn pixel canvas.
     *
     * @param borderSpacePixels in screen pixels.
     */
    public void setBorderSpacePixels(int borderSpacePixels) {
        this.borderSpacePixels = borderSpacePixels;
        insets.bottom = borderSpacePixels;
        insets.top = borderSpacePixels;
        insets.left = borderSpacePixels;
        insets.right = borderSpacePixels;
    }

    /** Clips a Point to within the chip size. The point is modified to fit within the size of the Chip2D.
     *
     * @param p a Point
     */
    void clipPoint(Point p) {
        if (p.x < 0) {
            p.x = 0;
        } else if (p.x > getChip().getSizeX() - 1) {
            p.x = getChip().getSizeX() - 1;
        }
        if (p.y < 0) {
            p.y = 0;
        } else if (p.y > getChip().getSizeY() - 1) {
            p.y = getChip().getSizeY() - 1;
        }
    }

    void zoomCenter() {
        getZoom().zoomcenter();
    }

    public ChipCanvas.Zoom getZoom() {
        return zoom;
    }

    public void setZoom(ChipCanvas.Zoom zoom) {
        this.zoom = zoom;
    }

    /** Chip fills drawable horizontally.
     * @return the fillsHorizontally
     */
    public boolean isFillsHorizontally() {
        return fillsHorizontally;
    }

    /** Chip fills drawable vertically.
     * @return the fillsVertically
     */
    public boolean isFillsVertically() {
        return fillsVertically;
    }

    /** Encapsulates zooming the view */
    public class Zoom {

        final float zoomStepRatio = 1.3f;
        private Point startPoint = new Point();
        private Point endPoint = new Point();
        private Point centerPoint = new Point();
        float zoomFactor = 1;
        private boolean zoomEnabled = false;
        Point tmpPoint = new Point();
        double projectionLeft, projectionRight, projectionBottom, projectionTop; // projection rect points, computed on zoom

        private void setProjection(GL gl) {
            float glScale;
            // define a new projection matrix so that the clipping volume is centered on the centerPoint
            // and the size of the rectangle zooms up by a factor of zoomFactor on the original j2dScale
            gl.glMatrixMode(GL.GL_PROJECTION);
            gl.glLoadIdentity();
            final int w = drawable.getWidth(), h = drawable.getHeight(); // w,h of screen
            final int sx = chip.getSizeX(), sy = chip.getSizeY(); // chip size
            if (isFillsHorizontally()) { //tall
                glScale = (float) (w * zoomFactor) / sx; // chip pix to screen pix scaling in horizontal&vert direction
                final float wx2 = sx / zoomFactor / 2;
                clipArea.left = centerPoint.x - wx2;
                clipArea.right = centerPoint.x + wx2;
                final float wy2 = wx2 * h / w;
                clipArea.bottom = centerPoint.y - wy2;
                clipArea.top = centerPoint.y + wy2;
                borders.leftRight = 0;
                borders.bottomTop = 0;
                gl.glOrtho(clipArea.left, clipArea.right, clipArea.bottom, clipArea.top, ZCLIP, -ZCLIP); // clip area has same ar as screen!
            } else {
                glScale = (float) (h * zoomFactor) / sy; // chip pix to screen pix scaling in horizontal&vert direction
                final float wy2 = sy / zoomFactor / 2;
                clipArea.bottom = centerPoint.y - wy2;
                clipArea.top = centerPoint.y + wy2;
                final float wx2 = wy2 * w / h;
                clipArea.left = centerPoint.x - wx2;
                clipArea.right = centerPoint.x + wx2;
                borders.leftRight = 0;
                borders.bottomTop = 0;
                gl.glOrtho(clipArea.left, clipArea.right, clipArea.bottom, clipArea.top, ZCLIP, -ZCLIP); // clip area has same ar as screen!
            }
        }

        public Point getStartPoint() {
            return startPoint;
        }

        public void setStartPoint(Point startPoint) {
            this.startPoint = startPoint;
        }

        public Point getEndPoint() {
            return endPoint;
        }

        public void setEndPoint(Point endPoint) {
            this.endPoint = endPoint;
        }

        private void unzoom() {
            setZoomEnabled(false);
            zoomFactor = 1;
            getZoom().setStartPoint(new Point(0, 0));
            getZoom().setEndPoint(new Point(getChip().getSizeX(), getChip().getSizeY()));
            if (!System.getProperty("os.name").contains("Mac")) {//crashes on mac os x 10.5
                GL g = drawable.getGL();
                g.glMatrixMode(GL.GL_PROJECTION);
                g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                g.glOrtho(-getBorderSpacePixels(), drawable.getWidth() + getBorderSpacePixels(), -getBorderSpacePixels(), drawable.getHeight() + getBorderSpacePixels(), ZCLIP, -ZCLIP);
                g.glMatrixMode(GL.GL_MODELVIEW);
            }
        }

        private void zoomcenter() {
            centerPoint = getMousePixel();
            setZoomEnabled(true);
        }

        private void zoomin() {
            if (!isOpenGLEnabled()) {
                JOptionPane.showMessageDialog(getCanvas(), "<html>You are using Java2D rendering.<br>To enabling zooming, enable OpenGL graphics (View/Graphics options)", "Can't zoom", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
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

        public void setZoomEnabled(boolean zoomEnabled) {
            this.zoomEnabled = zoomEnabled;
        }
    }

    public void addGLEventListener(GLEventListener listener) {
//        System.out.println("addGLEventListener("+listener+")");
    }

    /** Draws the annotations for a filter including the annotations for enclosed filters
    @param f the filter to annotate
    @param a the corresponding annotator (usually the same filter)
    @param drawable the drawing context
     */
    private void drawAnnotationsIncludingEnclosed(EventFilter f, FrameAnnotater a, GLAutoDrawable drawable) {
        try{
            a.annotate(drawable);
        }catch(RuntimeException e){
            log.warning("caught "+e+" when annotating with "+a);
        }
        if (f.getEnclosedFilter() != null && f.getEnclosedFilter() instanceof FrameAnnotater) {
            EventFilter f2 = f.getEnclosedFilter();
            FrameAnnotater a2 = (FrameAnnotater) f2;
            if (a2.isAnnotationEnabled()) {
                drawAnnotationsIncludingEnclosed(f2, (FrameAnnotater) f2, drawable);
            }
        }
        if (f.getEnclosedFilterChain() != null) {
            for (EventFilter f2 : f.getEnclosedFilterChain()) {
                if (f2 != null && f2 instanceof FrameAnnotater) {
                    FrameAnnotater a2 = (FrameAnnotater) f2;
                    if (a2.isAnnotationEnabled()) {
                        drawAnnotationsIncludingEnclosed(f2, a2, drawable);
                    }
                }
            }
        }
    }

    /**First, calls annotate(GLAutoDrawable) for all FrameAnnotators that have been added explicitly to the current DisplayMethod.
     * Then it calls annotate on all FilterChain filters with that implement FrameAnnotator and that are enabled for the Chip2D.
     *
    @param drawable the context
     */
    protected void annotate(GLAutoDrawable drawable) {
        if (getDisplayMethod() == null) {
            return;
        }
        if (getDisplayMethod().getAnnotators() == null) {
            return;
        }

        for (FrameAnnotater a : getDisplayMethod().getAnnotators()) {
            a.annotate(drawable);
        }

        if (chip instanceof AEChip) {
            FilterChain chain = ((AEChip) chip).getFilterChain();
            if (chain != null) {
                for (EventFilter f : chain) {
                    if (!f.isAnnotationEnabled()) {
                        continue;
                    }
                    FrameAnnotater a = (FrameAnnotater) f;
                    drawAnnotationsIncludingEnclosed(f, a, drawable);
                }
            }

        }
    }

//    /** Iterates through the FilterChain associated with the AEChip to call all the enabled filter annotations
//    @param g2 the graphics context passed to the EventFilter annotators
//     */
//    protected void annotate(Graphics2D g2) {
//        FilterChain fc = null;
//        if (chip instanceof AEChip) {
//            fc = ((AEChip) chip).getFilterChain();
//            if (fc != null) {
//                for (EventFilter f : fc) {
//                    if (f instanceof FrameAnnotater && f.isAnnotationEnabled()) {
//                        FrameAnnotater fa = (FrameAnnotater) f;
//                        fa.annotate(g2);
//                    }
//                }
//            }
//        }
////        if (annotators == null)return;
////        //        System.out.println("ChipCanvas annotating graphics");
////        for(FrameAnnotater a : annotators){
////            a.annotate(g2);
////        }
//    }
    /** Utility method to check for GL errors. Prints stacked up errors up to a limit.
    @param g the GL context
    @param glu the GLU used to obtain the error strings
    @param msg an error message to log to e.g., show the context
     */
    public void checkGLError(GL g, GLU glu, String msg) {
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
//             Thread.dumpStack();
            error = g.glGetError();
        }
    }

    public float getOrigin3dx() {
        return origin3dx;
    }

    public void setOrigin3dx(float origin3dx) {
        this.origin3dx = origin3dx;
    }

    public float getOrigin3dy() {
        return origin3dy;
    }

    public void setOrigin3dy(float origin3dy) {
        this.origin3dy = origin3dy;
    }

    /*
     * Copy the frame buffer into the BufferedImage.  The data needs to
     * be flipped top to bottom because the origin is the lower left in
     * OpenGL, but is the upper right in Java's BufferedImage format.
    This method should be called inside the rendering display method.
    @param d the drawable we are drawing in.
     */
    void grabImage(GLAutoDrawable d) {
        GL gl = d.getGL();
        int width = d.getWidth();
        int height = d.getHeight();

        // Allocate a buffer for the pixels
        ByteBuffer rgbData = BufferUtil.newByteBuffer(width * height * 3);

        // Set up the OpenGL state.
        gl.glReadBuffer(GL.GL_FRONT);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

        // Read the pixels into the ByteBuffer
        gl.glReadPixels(0,
                0,
                width,
                height,
                GL.GL_RGB,
                GL.GL_UNSIGNED_BYTE,
                rgbData);

        // Allocate space for the converted pixels
        int[] pixelInts = new int[width * height];

        // Convert RGB bytes to ARGB ints with no transparency. Flip
        // imageOpenGL vertically by reading the rows of pixels in the byte
        // buffer in reverse - (0,0) is at bottom left in OpenGL.

        int p = width * height * 3; // Points to first byte (red) in each row.
        int q;                  	// Index into ByteBuffer
        int i = 0;                 // Index into target int[]
        int bytesPerRow = width * 3; // Number of bytes in each row

        for (int row = height - 1; row >= 0; row--) {
            p = row * bytesPerRow;
            q = p;
            for (int col = 0; col < width; col++) {
                int iR = rgbData.get(q++);
                int iG = rgbData.get(q++);
                int iB = rgbData.get(q++);

                pixelInts[i++] = ((0xFF000000) | ((iR & 0xFF) << 16) | ((iG & 0xFF) << 8) | (iB & 0xFF));
            }
        }

        // Set the data for the BufferedImage
        if (getImageOpenGL() == null || getImageOpenGL().getWidth() != width || getImageOpenGL().getHeight() != height) {
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

    /** gets the chip we are rendering for. Subclasses or display methods can use this to access the chip object.
    @return the chip
     */
    public Chip2D getChip() {
        return chip;
    }

    public void setChip(Chip2D chip) {
        this.chip = chip;
    }
}
