/*
 * ChipCanvas.java
 *
 * Created on May 2, 2006, 1:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.graphics;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.eventprocessing.*;
import com.sun.opengl.util.*;
import java.awt.*;
import java.awt.BasicStroke;
import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.ImageCapabilities;
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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.*;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
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
    
    
    protected final int BORDER = 20;
    protected final int BORDER3D = 70;
    protected static final float SCALE_DEFAULT = 4f;
    protected AEViewer aeViewer;
    protected float anglex = prefs.getFloat("ChipCanvas.anglex",5);
    protected float angley = prefs.getFloat("ChipCanvas.angley",10);
    private ArrayList<FrameAnnotater> annotators = new ArrayList<FrameAnnotater>();
    private Chip2D chip;
    protected final int colorScale = 255;
    protected GLCanvas drawable;
    /** fr is the rendered event data that we draw. X is the first dimenion, Y is the second dimension, RGB 3 vector is the last dimension */
    protected float[][][] fr; // this is legacy data that we render here to the screen
    protected RenderingFrame frameData; // this is new form of pixel data to render
    protected GLU glu; // instance this if we need glu calls on context
    protected GLUT glut = new GLUT();
    protected Logger log = Logger.getLogger("Graphics");
    protected boolean openGLEnabled = prefs.getBoolean("ChipCanvas.enableOpenGL",false);
    private float origin3dx = prefs.getInt("ChipCanvas.origin3dx",0);
    private float origin3dy = prefs.getInt("ChipCanvas.origin3dy",0);
    protected int pheight = prefs.getInt("ChipCanvas.pheight",512);
    protected int[] pixels;
    /** width and height of pixel array in canvas in pixels. these are different than the actual canvas size */
    protected int pwidth = prefs.getInt("ChipCanvas.pwidth", 512);
    protected Chip2DRenderer renderer;
    // the number of screen pixels for one retina pixel
    protected float scale = prefs.getFloat("ChipCanvas.scale",3f);
    protected static final Color selectedPixelColor = Color.blue;
    protected GLUquadric selectedQuad;
    public BufferStrategy strategy;
    
    /** the translation of the actual chip drawing area in the canvas, in screen pixels */
    protected int xt,yt;
    
    protected ChipCanvas.Zoom zoom = new Zoom();
    protected boolean zoomMode = false; // true while user is dragging zoom box
    
    // reused imageOpenGL for OpenGL image grab
    private BufferedImage imageOpenGL;
    // reused by canvas to hold bytes of image when writing java Image
    ByteBuffer byteBuffer=null;
    // boolean to flag that present opengl display call should write imageOpenGL
    volatile boolean grabNextImageEnabled=false;
    
    
    /** Creates a new instance of ChipCanvas */
    public ChipCanvas(Chip2D chip) {
        this.chip=chip;
        this.renderer=chip.getRenderer();
        
        // design capabilities of opengl canvas
        GLCapabilities caps=new GLCapabilities();
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);
        glu=new GLU();
        
        // make the canvas
        try{
            drawable=new GLCanvas(caps);
        }catch(UnsatisfiedLinkError e){
            e.printStackTrace();
            System.err.println("java.libary.path="+System.getProperty("java.library.path"));
            System.err.println("user.dir="+System.getProperty("user.dir"));
            System.exit(1);
        }
        drawable.setLocale(Locale.US); // to avoid problems with other language support in JOGL
        drawable.setAutoSwapBufferMode(true);
        
        // will always get invalid operation here
        // checkGLError(drawable.getGL(),glu,"before add event listener");
        // add us as listeners for the canvas. then when the display wants to redraw display() will be called. or we can call drawable.display();
        drawable.addGLEventListener(this);
        // same here, canvas not yet valid
        // checkGLError(drawable.getGL(),glu,"after add event listener");
        
//        Dimension ss=Toolkit.getDefaultToolkit().getScreenSize();
        scale=prefs.getFloat(scalePrefsKey(),4);
        setScale(getScale());
        initComponents();
        chip.addObserver(this);
        
        
        if(displayMethods!=null && !displayMethods.isEmpty()) setDisplayMethod(0);
    }
    
    
    /** call this method so that next open gl rendering by display(GLAutoDrawable) writes imageOpenGL */
    public void grabNextImage(){
        grabNextImageEnabled=true;
    }
    
    
    // here are methods for toggling display method and building menu for AEViewer
    private JMenu displayMethodMenu=new JMenu("Display method");
    private ButtonGroup menuButtonGroup=new ButtonGroup();
    public JMenu getDisplayMethodMenu(){
        return displayMethodMenu;
    }
    
    /** the list of display methods for this canvas*/
    protected ArrayList<DisplayMethod> displayMethods=new ArrayList<DisplayMethod>();
    /** adds a display method to this canvas
     @param m the method
     */
    public void addDisplayMethod(DisplayMethod m){
        displayMethods.add(m);
        displayMethodMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        JRadioButtonMenuItem mi=new JRadioButtonMenuItem(m.getDescription());
        m.setMenuItem(mi);
        mi.setActionCommand(m.getDescription());
        mi.putClientProperty("displayMethod",m); // add display method as client property
        mi.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                setDisplayMethod((DisplayMethod)((JComponent)e.getSource()).getClientProperty("displayMethod"));;
            }
        }
        );
        displayMethodMenu.add(mi);
        menuButtonGroup.add(mi);
        // if there is no default method yet, set one to be sure that a method is set, to avoid null pointer exceptions
        if(displayMethod==null) setDisplayMethod(m);
//        log.info("added "+m);
    }
    
    DisplayMethod displayMethod;
    int currentDisplayMethodIndex=0;
    /** returns the current display method
     @return the current display method
     */
    public DisplayMethod getDisplayMethod(){
        return displayMethod;
    }
    /** cycle to the next display method */
    public void cycleDisplayMethod(){
        if(++currentDisplayMethodIndex>=displayMethods.size()) currentDisplayMethodIndex=0;
        setDisplayMethod(currentDisplayMethodIndex);
    }
    /** sets the display method
     @param m the method to use
     */
    public void setDisplayMethod(DisplayMethod m){
        this.displayMethod=m;
        m.getMenuItem().setSelected(true);
    }
    /** sets the display method using the menu name
     @param description the name
     */
    public void setDisplayMethod(String description){
        for(DisplayMethod m:displayMethods) {
            if(m.getDescription()==description) setDisplayMethod(m);
        }
    }
    /** sets the current display method to a particular item
     @param index the index of the method in the list of methods
     */
    protected void setDisplayMethod(int index){
        setDisplayMethod(displayMethods.get(index));
    }
    
    
    /** opengl calls this when it wants to redraw, and we call it when we actively render.
     * @param drawable the surface from which we can get the context with getGL
     @see ch.unizh.ini.caviar.graphics.DisplayMethod#setupGL which sets up GL context for display methods
     */
    public synchronized void display(GLAutoDrawable drawable) {
        if(!isOpenGLEnabled()) {
            paint(null);
        }else{
            GL gl=drawable.getGL();
            checkGLError(gl, glu, "before setting projection");
            gl.glPushMatrix();
            {
                if(zoom.isZoomEnabled()){
                    gl.glMatrixMode(GL.GL_PROJECTION);
                    gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
//                log.info("Zoom projection");
                    gl.glOrtho(zoom.startPoint.x*getScale(),zoom.endPoint.x*getScale(),zoom.startPoint.y*getScale(),zoom.endPoint.y*getScale(),10000,-10000);
                    gl.glMatrixMode(GL.GL_MODELVIEW);
                }else{
                    setDefaultProjection(gl,drawable);
                }
                checkGLError(gl, glu, "after setting projection, before displayMethod");
                DisplayMethod m=getDisplayMethod();
                if(m==null){
                    log.warning("null display method");
                }else{
                    m.display(drawable);
                }
                checkGLError(gl,glu,"after "+getDisplayMethod()+".display()");
                showSpike(gl);
                annotate(drawable);
                checkGLError(gl, glu, "after annotations");
            }
            gl.glPopMatrix();
            checkGLError(gl, glu,"after display");
            zoom.drawZoomBox(gl);
            if(grabNextImageEnabled){
                grabImage(drawable);
                grabNextImageEnabled=false;
            }
        }
    }
    
    float[] rgbVec=new float[3];
    
    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
        // should be empty according to jogl user guide.
//        System.out.println("displayChanged");
    }
    
    public float getAnglex() {
        return anglex;
    }
    
    public float getAngley() {
        return angley;
    }
    
    public Canvas getCanvas() {
        return drawable;
    }
    
    public float[][][] getFr() {
        return fr;
    }
    
    protected int getPheight() {
        return pheight;
    }
    
    protected final Color getPixelColor(float red, float green, float blue){
        int r = (int)(colorScale*red);
        int g = (int)(colorScale*green);
        int b = (int)(colorScale*blue);
        int value = ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8)  |
                ((b & 0xFF) << 0);
        return new Color(value);
    }
    
    /** @return pixel x,y location (integer point) from MouseEvent. Accounts for scaling and borders of chip display area */
    public Point getPixelFromMouseEvent(MouseEvent evt){
        Point p = new Point();
        int dx=(drawable.getWidth()-getPwidth())/2;
        int dy=(drawable.getHeight()-getPheight())/2;
        p.x = Math.round((evt.getX() - dx) / scale / zoom.getScaleX());
        p.y = Math.round((getPheight()-evt.getY() + dy) / scale / zoom.getScaleY());
        return p;
    }
    
    protected final int getPixelRGB(float red, float green, float blue){
        int r = (int)(colorScale*red);
        int g = (int)(colorScale*green);
        int b = (int)(colorScale*blue);
        int value = ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8)  |
                ((b & 0xFF) << 0);
        return value;
    }
    
    protected int getPwidth() {
        return pwidth;
    }
    
    public float getScale() {
        return this.scale;
    }
    
    public void init(GLAutoDrawable drawable) {
        // drawable.setGL(new DebugGL(drawable.getGL()));
        
        GL gl = drawable.getGL();
        
        log.info(
                "OpenGL implementation is: " + gl.getClass().getName()+"\nGL_VENDOR: " + gl.glGetString(GL.GL_VENDOR)
                + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER)
                + "\nGL_VERSION: " + gl.glGetString(GL.GL_VERSION)
//                + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
                
                );
        float glVersion=Float.parseFloat(gl.glGetString(GL.GL_VERSION).substring(0,3));
        if(glVersion<1.3f){
            log.warning("\n\n*******************\nOpenGL version "+glVersion+" < 1.3, some features may not work and program may crash\nTry switching from 16 to 32 bit color if you have decent graphics card\n\n");
        }
//        System.out.println("GLU_EXTENSIONS: "+glu.gluGetString(GLU.GLU_EXTENSIONS));
        
        gl.setSwapInterval(1);
        gl.glShadeModel(GL.GL_FLAT);
        
        gl.glClearColor(0,0,0,0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glLoadIdentity();
        
        gl.glRasterPos3f(0,0,0);
        gl.glColor3f(1,1,1);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,"Initialized display");
        checkGLError(gl,glu,"after init");
        
    }
    
    
    protected void initComponents() {
        unzoom();
        if(renderer!=null){
            drawable.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent evt) {
                    Point p = getPixelFromMouseEvent(evt);
                    if (!isZoomMode()){
                        
                        //                System.out.println("evt="+evt);
                        if (evt.getButton()==1){
                            renderer.setXsel((short) -1);
                            renderer.setYsel((short) -1);
//            System.out.println("cleared pixel selection");
                        }else                        if (evt.getButton()==3){
                            // we want mouse click location in chip pixel location
                            // don't forget that there is a BORDER on the orthographic viewport projection
                            // this border means that the pixels are actually drawn on the screen in a viewport that has a BORDER sized edge on all sides
                            // for simplicity because i can't figure this out, i have set the BORDER to zero
                            
                            log.info("pwidth=" + pwidth + " pheight=" + pheight + " width=" + drawable.getWidth() + " height=" + drawable.getHeight()
                            + " mouseX=" + evt.getX() + " mouseY=" + evt.getY() + " xt=" + xt + " yt=" + yt);
                            
                            //                        renderer.setXsel((short)((0+((evt.x-xt-BORDER)/scale))));
                            //                        renderer.setYsel((short)(0+((getPheight()-evt.y+yt-BORDER)/scale)));
                            renderer.setXsel((short) p.x);
                            renderer.setYsel((short) p.y);
                            log.info("Selected pixel x,y=" + renderer.getXsel() + "," + renderer.getYsel());
                        }
                    }else                    if (isZoomMode()){ // zoom startZoom
                        zoom.startZoom(p);
                    }
                }
                public void mouseReleased(MouseEvent e){
                    if (is3DEnabled()){
                        log.info("3d rotation: angley=" + angley + " anglex=" + anglex + " 3d origin: x=" + getOrigin3dx() + " y=" + getOrigin3dy());
                    }else                    if (isZoomMode()){
                        Point p = getPixelFromMouseEvent(e);
                        zoom.endZoom(p);
//                    zoom.endX=p.x;
//                    zoom.endY=p.y;
//                    setZoomMode(false);
                    }
                }
            });
        } // renderer!=null
        
        drawable.addMouseMotionListener(new MouseMotionListener(){
            public void mouseDragged(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int but1mask = InputEvent.BUTTON1_DOWN_MASK,  but3mask = InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx()&but1mask)==but1mask){
                    if (is3DEnabled()){
                        final float maxAngle = 180f;
                        setAngley(maxAngle * (x - drawable.getWidth() / 2) / (float) drawable.getWidth());
                        setAnglex(maxAngle * (y - drawable.getHeight() / 2) / (float) drawable.getHeight());
                    }else if(isZoomMode()){
//                        System.out.print("z");
                    }
                }else                    if ((e.getModifiersEx()&but3mask)==but3mask){
                    if (is3DEnabled()){
                        // mouse position x,y is in pixel coordinates in window canvas, but later on, the events will be drawn in
                        // chip coordinates (transformation applied). therefore here we set origin in pixel coordinates based on mouse
                        // position in window.
                        set3dOrigin(Math.round(getChip().getMaxSize() * ((float) x - drawable.getWidth() / 2) / drawable.getWidth()),Math.round(getChip().getMaxSize() * (drawable.getHeight() / 2 - (float) y) / drawable.getHeight()));
                    }
                }
            }
            
            public void mouseMoved(MouseEvent e) {
            }
        });
    }
    
    public boolean is3DEnabled() {
        return displayMethod instanceof DisplayMethod3D ;
    }
    
    public boolean isOpenGLEnabled() {
        return openGLEnabled;
    }
    
    public boolean isZoomMode() {
        return zoomMode;
    }
    
    /** use to paint with Java2D using BufferStrategy */
    protected void paint(){
        paint(null);
    }
    
    /** used for active and passive rendering by Java2D.
     * @param g null to use BufferStrategy graphics (created on demand), non-null to paint to some other rendering context, e.g. an Image that can be written to a file
     */
    public synchronized void paint(Graphics g){
//        log.info("paint with graphics="+g);
        
        Graphics2D g2 = null;
        if (strategy == null){
            try{
                drawable.createBufferStrategy(2);
                strategy = drawable.getBufferStrategy();
                BufferCapabilities cap = strategy.getCapabilities();
                ImageCapabilities imCapFront = cap.getFrontBufferCapabilities();
                ImageCapabilities imCapBack = cap.getBackBufferCapabilities();
                boolean isPageFlip = cap.isPageFlipping();
                boolean isMultiBufferAvailable = cap.isMultiBufferAvailable();
                log.info("RetinaCanvas BufferCapabilities: isPageFlipping="+isPageFlip+" isMultiBufferAvailable="+isMultiBufferAvailable);
            } catch (Exception e){
                log.warning("RetinaCanvas.paintFrame(): coulnd't create BufferStrategy yet: "+e.getMessage());
            }
        }
        if (g==null){
            //            System.out.println("**********paint("+g+")");
            if (strategy == null)return;
            g2 = (Graphics2D) strategy.getDrawGraphics();
        }else{
            g2 = (Graphics2D) g;
        }
        try{
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_SPEED);
            
            g2.setColor(Color.black);
            g2.fill(drawable.getBounds());
            xt = (drawable.getWidth() - pwidth) / 2;
            yt = (drawable.getHeight() - pheight) / 2;
            
            g2.translate(xt,yt);
            g2.scale(scale,scale);// AffineTransform.getScaleInstance(scale,scale));
            g2.setFont(g2.getFont().deriveFont(8f));
            
            
            //            g2.translate(.5,-.5);
            //            g2.transform(new AffineTransform(1,0,0,-1,0,0)); // flip y
            float gray = (float)renderer.getGrayValue();
            g2.setColor(new Color(gray, gray, gray));
            g2.fill(new Rectangle(0, 0, chip.getSizeX(), chip.getSizeY()));
            float scale = getScale();
            int sizex = chip.getSizeX(), sizey = chip.getSizeY();
            float[][][] fr = renderer.getFr();
            if(fr==null) return;
            for (int y = 0; y<sizey; y++)
                for (int x = 0; x<sizex; x++){
                float[] f = fr[y][x];
                if(f[0]==gray && f[1]==gray && f[2]==gray) continue;
                g2.setColor(getPixelColor(f[0],f[1],f[2]));
                g2.fillRect(x,sizey-y-1,1,1);
                
//                int rgb=getPixelRGB(f[0],f[1],f[2]);
                
//                pixels[x+y*pheight]=rgb;
                }
            //            raster.setSamples(0,0,pwidth,pheight,0,pixels);
            
            // draw selected pixel
            int xsel = renderer.getXsel(),  ysel = renderer.getYsel();
            if (xsel!=-1 && ysel!=-1){
                g2.setColor(selectedPixelColor);
                //                g2.fillRect(scale*renderer.xsel,pheight-scale*(renderer.ysel+1),scale, scale);
                int radius = 1+renderer.getSelectedPixelEventCount();
                g2.setStroke(new BasicStroke(.2f/getScale()));
                int xs = xsel-radius,  ys = sizey-(ysel+radius);
                g2.drawOval(xs, ys, 2*radius, 2*radius);
                g2.drawString(xsel+","+ysel,xsel,sizey-ysel);
            }
            // call all the annotators that have registered themselves. these are java2d annotators
            annotate(g2);
            strategy.show();
        } catch (Exception e){
            log.warning("ChipCanvas.display(): Graphics context error "+e);
            e.printStackTrace();
        }
    }
    
    /** used for active rendering.
     * You call this when you want to actively render the frame. Internally, this calls the display() method of the drawable, which by callback to display(GLAutoDrawable)
     * uses openGL to draw the canvas
     */
    public void paintFrame(){
        //        System.out.println("******paintFrame");
        //        try{
        if (isOpenGLEnabled()){
            drawable.display(); // we call the drawable's display method that ends up calling us back via our local display(GLAutoDrawable)!! very important to get this right
        }else{
            paint();
        }
//        }catch(Exception e){
//            System.err.println("exception in display() from paintFrame()");
//            e.printStackTrace();
//        }
    }
    
    public void removeGLEventListener(GLEventListener listener) {
    }
    
    /** calls repaint on the drawable */
    public synchronized void repaint() {
        drawable.repaint();
    }
    
    /** calls repaint on the drawable */
    public void repaint(long tm){
//        log.info("repaint(tm)");
        drawable.repaint(tm);
    }
    
    /** called on reshape of canvas. Sets the scaling for drawing pixels to the screen. */
    public synchronized void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        //        if(dontRecurseResize){
        //            dontRecurseResize=false;
        //            return;
        //        }
        //        System.out.println("ChipCanvas.reshape(): x="+x+" y="+y+" width="+width+" height="+height);
        GL gl = drawable.getGL();
        
        setDefaultProjection(gl,drawable); // this sets orthographic projection so that chip pixels are scaled to the drawable width
        
        gl.glLoadIdentity();
        int chipSizeX = chip.getSizeX();
        int chipSizeY = chip.getSizeY();
        float newscale;
        if (chipSizeY>chipSizeX){
            // chip is tall and skinny, so set scale by frame height/chip height
            newscale=height/chipSizeY;
            if (newscale*chipSizeX>width)
                newscale = (float) width / chipSizeX;
        }else{
            // chip is square or squat, so set scale by frame width / chip width
            newscale = (float) width / chipSizeX;
            if (newscale*chipSizeY>height)
                newscale = (float) height / chipSizeY;
            
        }
//        System.out.println("RetinaCanvas.formComponentResized(): scale="+scale);
        setScale(newscale);
        
        gl.glViewport(0,0,width,height);
//        System.out.println("glViewport reshape");
//        constrainAspectRatio();
    }
    
    
    protected String scalePrefsKey(){
        if (chip == null){
            return "ChipCanvas.scale";
        }else{
            return "ChipCanvas.scale" + chip.getClass().getSimpleName();
        }
    }
    
    protected void set3dOrigin(int x, int y){
        setOrigin3dy(y);
        setOrigin3dx(x);
        prefs.putInt("ChipCanvas.origin3dx",x);
        prefs.putInt("ChipCanvas.origin3dy",y);
    }
    
    public void setAnglex(float anglex) {
        this.anglex = anglex;
        prefs.putFloat("ChipCanvas.anglex",anglex);
    }
    
    public void setAngley(float angley) {
        this.angley = angley;
        prefs.putFloat("ChipCanvas.angley",angley);
    }
    
    public void setOpenGLEnabled(boolean openGLEnabled) {
        this.openGLEnabled = openGLEnabled;
        prefs.putBoolean("ChipCanvas.openGLEnabled",openGLEnabled);
    }
    
    
    /** sets the projection matrix so that we get an orthographic projection that is the size of the canvas with z volume -10000 to 10000.
     This projection sets the drawing scale so that a single chip pixel is rendered to a number of screen pixels. That's why no scalef operation is later needed,
     GL coordinates are implicitly already chip pixel coordinates.
     @param g the GL context
     @param d the GLAutoDrawable canvas
     */
    protected void setDefaultProjection(GL g, GLAutoDrawable d){
        /*
         void glOrtho(GLdouble left,
             GLdouble right,
             GLdouble bottom,
             GLdouble top,
             GLdouble near,
             GLdouble far)
         */
//        log.info("setDefaultProjection");
        checkGLError(g,glu,"before setDefaultProjection");
        g.glMatrixMode(GL.GL_PROJECTION);
        g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        if (!is3DEnabled()){
            g.glOrtho(-BORDER,drawable.getWidth() + BORDER,-BORDER,drawable.getHeight() + BORDER,10000,-10000);
        }else{
            g.glOrtho(-BORDER3D,drawable.getWidth() + BORDER3D,-BORDER3D,drawable.getHeight() + BORDER3D,10000,-10000);
        }
        g.glMatrixMode(GL.GL_MODELVIEW);
    }
    
    /** defines the minimum canvas size in pixels; used when chip size has not been set to non zero value
     */
    public static final int MIN_DIMENSION=70;
    
    /** sets preferred width of canvas in screen pixels: Math.ceil((s*(chip.getSizeX(), where s is scaling of chip pixels to screen pixels */
    protected void setPwidth(int pwidth) {
        if(pwidth<MIN_DIMENSION) pwidth=MIN_DIMENSION;
        this.pwidth = pwidth;
//        System.out.println("pwidth="+pwidth);
    }
    
    /** sets preferred height of canvas in screen pixels: Math.ceil((s*(chip.getSizeY(), where s is scaling of chip pixels to screen pixels.
     pheight is not necessarily the hieght of the window.
     */
    protected void setPheight(int pheight) {
        if(pheight<MIN_DIMENSION) pheight=MIN_DIMENSION;
        this.pheight = pheight;
//        System.out.println("pheight="+pheight);
    }
    
    /** @param s size of retina pixel in screen pixels.
     This method sets the pixel drawing scale so that e.g. s=2 means a chip pixel occupies 2 screen pixels. */
    public void setScale(float s){
        if(s<1) s=1f;
        prefs.putFloat(scalePrefsKey(),s);
        this.scale=s;
        setPwidth((int) Math.ceil((s*(chip.getSizeX()))));
        setPheight((int) Math.ceil((s*(chip.getSizeY()))));
//        checkGLError(drawable.getGL(),glu,"before setPreferredSize");
        drawable.setPreferredSize(new Dimension(getPwidth(), getPheight()));
//        checkGLError(drawable.getGL(),glu,"after setPreferredSize");
        if (drawable.getParent() != null){
            drawable.invalidate();
            drawable.getParent().validate();
        }
//        log.info("ChipCanvas.setScale()="+scale);
    }
    
//       /**
//     * This method takes the Zoom Cursor Image
//     * and creates the Zoom Custom Cursor which is
//     * shown on the Image Panel on mouse over
//     *
//     * @param zoomcursorImage
//     */
//    void setZoomCursorImage(Image zoomcursorImage)
//    {
//                    Image zoomcursorImage = getImage(getCodeBase(), ZOOM_CURSOR);
//
//            MediaTracker mt = new MediaTracker(this.getCanvas());
//            mt.addImage(zoomcursorImage, 2);
//
//            try
//            {
//                mt.waitForAll();
//            }
//            catch (InterruptedException e)
//            {
//                e.printStackTrace();
//            }
//
//            Cursor m_zoomCursor = Toolkit.getDefaultToolkit().createCustomCursor(
//                        zoomcursorImage, new Point(0, 0), "ZoomCursor");
//    }
    
    protected void setZoomCursor(boolean yes){
        if (yes){
            Cursor zoomCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
            drawable.setCursor(zoomCursor);
        }else{
            Cursor normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);
            drawable.setCursor(normalCursor);
        }
    }
    
    public void setZoomMode(boolean zoomMode) {
        this.zoomMode = zoomMode;
        setZoomCursor(zoomMode);
    }
    
    protected void showSpike(GL gl){        // show selected pixel that user can hear
        if (renderer!=null && renderer.getXsel() != -1 && renderer.getYsel() != -1){
            showSpike(gl,renderer.getXsel(), renderer.getYsel(), renderer.getSelectedPixelEventCount());
        }
    }
    
    // draws a circle at pixel x,y of size+.5 radius. size is used to indicate number of spikes in this 'frame'
    protected void showSpike(GL gl, int x, int y, int size){
        // circle
        size=size*2;
        gl.glPushMatrix();
        gl.glColor4f(0,0,1f,0f);
//        gl.glLoadIdentity();
//        gl.glTranslatef(xt,yt,0);
//        gl.glScalef(scale,scale,1);
        size=size*3;
        if (size > chip.getMaxSize() / 3) size = chip.getMaxSize() / 3;
        gl.glTranslatef(x, y, -1);
//        gl.glRasterPos3f(x, y , 0);
        selectedQuad = glu.gluNewQuadric();
        //                    glu.gluQuadricDrawStyle(selectedQuad,GLU.GLU_LINE);
        //                    glu.gluCylinder(selectedQuad,1,1,2,8,1);
        glu.gluQuadricDrawStyle(selectedQuad,GLU.GLU_FILL);
        glu.gluDisk(selectedQuad,size,size + 5 / scale,16,1);
        glu.gluDeleteQuadric(selectedQuad);
        gl.glPopMatrix();
        
        int font = GLUT.BITMAP_HELVETICA_18;
        
        gl.glPushMatrix();
        
        final int FS = 1; // distance in pixels of text from selected pixel
        
        gl.glRasterPos3f(x+FS, y+FS , 0);
        glut.glutBitmapString(font,x+","+y);
        
        gl.glPopMatrix();
        
        checkGLError(gl,glu,"showSpike");
        
    }
    
    public void unzoom(){
        zoom.unzoom();
    }
    
    public void update(Observable o, Object arg) {
        // if observable is a chip object and arg is a string size property then set a new scale
        if (o == chip && arg instanceof String){
            if(arg.equals("sizeX") || arg.equals("sizeY")) {
                setScale(prefs.getFloat(scalePrefsKey(),SCALE_DEFAULT));
                unzoom();
            }
        }
        if(o instanceof Chip2D){
            Chip2D c=(Chip2D)o;
            renderer=c.getRenderer();
        }
    }
    
    protected class Zoom{
        
        private Point startPoint = new Point();
        private Point endPoint = new Point();
        float zoomFactor=1;
        private boolean zoomEnabled=false;
        Point tmpPoint = new Point();
        public float getScaleX() {
            return Math.round((double) getChip().getSizeX() / Math.abs(Math.max(1,endPoint.x-startPoint.x)));
        }
        
        public float getScaleY() {
            return Math.round((double) getChip().getSizeY() / Math.abs(Math.max(1,endPoint.y-startPoint.y)));
        }
        
        void clipPoint(Point p){
            if (p.x<0)p.x=0; else  if (p.x > getChip().getSizeX()) p.x = getChip().getSizeX();
            if (p.y<0)p.y=0; else  if (p.y > getChip().getSizeY()) p.y = getChip().getSizeY();
        }
        
        private void startZoom(Point p) {
            clipPoint(p);
            tmpPoint=p;
            drawingZoomBox=true;
            
        }
        
        /** ends zoom gesture, sets drawing context correctly for zooming */
        private void endZoom(Point p) {
            // zoom can go in 4 directions, so sort out coordinates so the start is at LL and end at UR, so that array indexing works out
            clipPoint(p);
            drawingZoomBox=false;
            setZoomMode(false);
            setZoomEnabled(true);
            startPoint=tmpPoint;
            endPoint=p;
            if (startPoint.x>endPoint.x){ int x = startPoint.x; startPoint.x=endPoint.x;endPoint.x=x;}
            if (startPoint.y>endPoint.y){ int y = startPoint.y; startPoint.y=endPoint.y;endPoint.y=y;}
            int dx=endPoint.x-startPoint.x, dy=endPoint.y-startPoint.y;
            if(dx==0 || dy==0) return;
            float aspectRatio=(float)(dy)/(dx);
            // we want to fit the zoom region into the screen but without differentially scaling x or y.
            if(dx>dy){
                // x box dim larger, zoom so that x region fits
            }else{
                // y box dim larger, zoom so that y region fits
            }
            // we cannot call gl methods here because we are not in drawing context, we must apply projections, etc, in display method (or init/reshpae)
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
//            log.info("unzoom");
            setZoomEnabled(false);
            setZoomMode(false);
            zoom.setStartPoint(new Point(0, 0));
            zoom.setEndPoint(new Point(getChip().getSizeX(), getChip().getSizeY()));
            GL g=drawable.getGL();
            g.glMatrixMode(GL.GL_PROJECTION);
            g.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
            g.glOrtho(-BORDER,drawable.getWidth() + BORDER,-BORDER,drawable.getHeight() + BORDER,10000,-10000);
            g.glMatrixMode(GL.GL_MODELVIEW);
        }
        
        public boolean isZoomEnabled() {
            return zoomEnabled;
        }
        
        public void setZoomEnabled(boolean zoomEnabled) {
            this.zoomEnabled = zoomEnabled;
        }
        
        boolean drawingZoomBox=false;
        
        boolean isDrawingZoomBox(){
            return drawingZoomBox;
        }
        
        private void drawZoomBox(GL gl) {
            if(!isDrawingZoomBox()) return;
            Point p=getCanvas().getMousePosition();
            if(p==null) return;
            gl.glPushMatrix();
            
            gl.glColor3f(0,0,1);
            
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2i(zoom.startPoint.x,zoom.startPoint.y);
            gl.glVertex2i(p.x,zoom.startPoint.y);
            gl.glVertex2i(p.x,p.y);
            gl.glVertex2i(zoom.startPoint.x, p.y);
            gl.glEnd();
            gl.glPopMatrix();
        }
    }
    
    /** add an annotator to the drawn canvas. This is one way to annotate the drawn data; the other way is to annotate the histogram frame data.
     *@param annotator the object that will annotate the frame data
     */
    public synchronized void addAnnotator(FrameAnnotater annotator){
        annotators.add(annotator);
    }
    
    /** removes an annotator to the drawn canvas.
     *@param annotator the object that will annotate the displayed data
     */
    public synchronized void removeAnnotator(FrameAnnotater annotator){
        annotators.remove(annotator);
    }
    
    /** removes all annotators */
    public synchronized void removeAllAnnotators(){
        annotators.clear();
    }
    
    public void addGLEventListener(GLEventListener listener) {
//        System.out.println("addGLEventListener("+listener+")");
    }
    
    /** Draws the annotations for a filter including the annotations for enclosed filters 
     @param f the filter to annotate
     @param a the corresponding annotator (usually the same filter)
     @param drawable the drawing context
     */
    private void drawAnnotationsIncludingEnclosed(EventFilter f, FrameAnnotater a, GLAutoDrawable drawable){
        a.annotate(drawable);
        if(f.getEnclosedFilter()!=null && f.getEnclosedFilter() instanceof FrameAnnotater){
            EventFilter f2=f.getEnclosedFilter();
            FrameAnnotater a2=(FrameAnnotater)f2;
            if(a2.isAnnotationEnabled()) drawAnnotationsIncludingEnclosed(f2, (FrameAnnotater)f2, drawable);
        }
        if(f.getEnclosedFilterChain()!=null){
            for(EventFilter f2:f.getEnclosedFilterChain()){
                if(f2!=null && f2 instanceof FrameAnnotater){
                    FrameAnnotater a2=(FrameAnnotater)f2;
                    if(a2.isAnnotationEnabled()) drawAnnotationsIncludingEnclosed(f2, a2, drawable);
                }
            }
        }
    }

    /** Calls annotate on all FilterChain filters with annotation enabled for the Chip2D
     @param drawable the context
     */
    protected void annotate(GLAutoDrawable drawable){
        if(chip instanceof AEChip){
            FilterChain chain=((AEChip)chip).getFilterChain();
            if(chain!=null){
                for(EventFilter f:chain){
                    if(f instanceof FrameAnnotater){
                        FrameAnnotater a=(FrameAnnotater)f;
                        drawAnnotationsIncludingEnclosed(f,a,drawable);
                    }
                }
            }
            
        }
//        if (annotators == null)return;
//        //        System.out.println("ChipCanvas annotating graphics");
//        for(FrameAnnotater a : annotators){
////            log.info("calling annotator "+a+" to annotate "+this);
//            if(a instanceof EventFilter && !(((EventFilter)a).isFilterEnabled())) continue;
//            a.annotate(drawable);
//        }
    }
    
    /** Iterates through the FilterChain associated with the AEChip to call all the enabled filter annotations
     @param g2 the graphics context passed to the EventFilter annotators
     */
    protected void annotate(Graphics2D g2){
        FilterChain fc=null;
        if(chip instanceof AEChip){
            fc=((AEChip)chip).getFilterChain();
            if(fc!=null){
                for(EventFilter f:fc){
                    if(f instanceof FrameAnnotater && f.isAnnotationEnabled()){
                        FrameAnnotater fa=(FrameAnnotater)f;
                        fa.annotate(g2);
                    }
                }
            }
        }
//        if (annotators == null)return;
//        //        System.out.println("ChipCanvas annotating graphics");
//        for(FrameAnnotater a : annotators){
//            a.annotate(g2);
//        }
    }
    
    /** Utility method to check for GL errors. Prints stacked up errors up to a limit.
     @param g the GL context
     @param glu the GLU used to obtain the error strings
     @param msg an error message to log to e.g., show the context
     */
    public void checkGLError(GL g, GLU glu,String msg){
        int error=g.glGetError();
        int nerrors=3;
        while(error!=GL.GL_NO_ERROR && nerrors--!=0){
            StackTraceElement[] trace=Thread.currentThread().getStackTrace();
            if(trace.length>1){
                String className=trace[2].getClassName();
                String methodName=trace[2].getMethodName();
                int lineNumber=trace[2].getLineNumber();
                log.warning("GL error number "+error+" "+glu.gluErrorString(error)+" : "+msg+" at "+className+"."+methodName+" (line "+lineNumber+")");
            }else{
                log.warning("GL error number "+error+" "+glu.gluErrorString(error)+" : "+msg);
            }
//             Thread.dumpStack();
            error=g.glGetError();
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
    void grabImage( GLAutoDrawable d ) {
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
        int bytesPerRow = width*3; // Number of bytes in each row
        
        for (int row = height - 1; row >= 0; row--) {
            p = row * bytesPerRow;
            q = p;
            for (int col = 0; col < width; col++) {
                int iR = rgbData.get(q++);
                int iG = rgbData.get(q++);
                int iB = rgbData.get(q++);
                
                pixelInts[i++] = ( (0xFF000000)
                | ((iR & 0xFF) << 16)
                | ((iG & 0xFF) << 8)
                | (iB & 0xFF) );
            }
        }
        
        // Set the data for the BufferedImage
        if(getImageOpenGL()==null || getImageOpenGL().getWidth()!=width || getImageOpenGL().getHeight()!=height) {
            imageOpenGL = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB);
        }
        getImageOpenGL().setRGB(0, 0, width, height, pixelInts, 0, width);
    }
    
    public BufferedImage getImageOpenGL() {
        return imageOpenGL;
    }
    
    public GLUT getGlut(){
        return glut;
    }
    
    public ArrayList<FrameAnnotater> getAnnotators() {
        return annotators;
    }
    
    public void setAnnotators(ArrayList<FrameAnnotater> annotators) {
        this.annotators = annotators;
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
