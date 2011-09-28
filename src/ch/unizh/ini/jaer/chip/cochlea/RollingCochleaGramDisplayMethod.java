/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

//import ch.unizh.ini.caviar.aemonitor.EventXYType;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.*;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.*;

/**
 * Shows events from stereo cochlea as a rastergram. Each packet is painted to the right of previous ones. The screen is used as the memory.
 *When the time reaches the right side, it resets to the left and the screen is cleared. The timescale is set by the ChipRenderer colorScale, which the user
 *can set with the UP/DOWN arrows buttons. It is set so that the entire width of the screen is a certain number of seconds.
 * @author tobi
 */
public class RollingCochleaGramDisplayMethod extends DisplayMethod implements DisplayMethod2D {

    /**
     * Creates a new instance of CochleaGramDisplayMethod
     *
     * @param c the canvas we are drawing on
     */
    public RollingCochleaGramDisplayMethod(ChipCanvas c) {
        super(c);
    }
    final float rasterWidth = 0.003f; // width of screen;
    final int BORDER = 50; // pixels
    protected int startTime = 0;
    boolean clearScreenEnabled = true;
    int font = GLUT.BITMAP_HELVETICA_18;
    int oldColorScale = 0;
    boolean hasBlend = false;
    boolean hasBlendChecked = false;
    /** Set by rendering to total width in us time */
    protected float timeWidthUs; // set horizontal scale so that we can just use relative timestamp for x
    /** set by rendering so that 1 unit drawing scale draws to right side, 0 to left during rendering, using scaling as in
     <pre>
      drawTimeScale = (drawable.getWidth() / timeWidthUs); // scale horizontal is draw
        gl.glScalef(drawTimeScale, yScale, 1);
     </pre>
     */
    protected float drawTimeScale;

    /** displays individual events as cochleagram
     * @param drawable the drawable passed in by OpenGL
     */
    public void display(GLAutoDrawable drawable) {
        AEChipRenderer renderer = (AEChipRenderer) getChipCanvas().getRenderer();
        int ntaps = chip.getSizeX();
        EventPacket ae = (EventPacket) chip.getLastData();
        if (ae == null || ae.isEmpty()) {
            return;
        }
        int t0 = ae.getFirstTimestamp();

        GL gl = drawable.getGL();
        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
        gl.glOrtho(-BORDER, drawable.getWidth() + BORDER, -BORDER, drawable.getHeight() + BORDER, 10000, -10000);
        gl.glMatrixMode(GL.GL_MODELVIEW);
        // blend may not be available depending on graphics mode or opengl version.
        if (!hasBlendChecked) {
            hasBlendChecked = true;
            String glExt = gl.glGetString(GL.GL_EXTENSIONS);
            if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                hasBlend = true;
            }
        }
        if (hasBlend) {
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                log.warning("tried to use glBlend which is supposed to be available but got following exception");
                gl.glDisable(GL.GL_BLEND);
                e.printStackTrace();
                hasBlend = false;
            }
        }
        gl.glLoadIdentity();
        // translate origin to this point
        gl.glTranslatef(0, 0, 0);
        // scale everything by rastergram scale
        float yScale = (drawable.getHeight()) / (float) ntaps;// scale vertical is draableHeight/numPixels
        // timewidth comes from render contrast setting
        // width starts with colorScale=1 to be the standard refresh rate
        // so that a raster will refresh every frame and reduces by powers of two
        int colorScale = getRenderer().getColorScale();
        if (colorScale != oldColorScale) {
            clearScreenEnabled = true;
        }
        oldColorScale = colorScale;
        int frameRate = 60; // hz
        if(chip instanceof AEChip){
            frameRate=((AEChip)chip).getAeViewer().getFrameRate();
        }
        timeWidthUs = 1e6f / frameRate * (1 << colorScale); // set horizontal scale so that we can just use relative timestamp for x
        drawTimeScale = (drawable.getWidth() / timeWidthUs); // scale horizontal is draw
        gl.glScalef(drawTimeScale, yScale, 1);
        // make sure we're drawing back buffer (this is probably true anyhow)
        gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);

        // render events
        if (clearScreenEnabled) {
            clearScreen(gl);
            clearScreenEnabled = false;
            startTime = t0;
        }
 
        // draw time axis with label of total raster time
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(4f);
        gl.glBegin(GL.GL_LINES);
        {
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(timeWidthUs, 0, 0); // time axis
        }
        gl.glEnd();

        String timeLabel = String.format("%.3f s", timeWidthUs * 1e-6f);
        int width = glut.glutBitmapLength(font, timeLabel);
        gl.glRasterPos3f(timeWidthUs * .9f, -3, 0);
        glut.glutBitmapString(font, timeLabel);

        
        final float w = (float) timeWidthUs / getChipCanvas().getCanvas().getWidth(); // spike raster as fraction of screen width
        float[][] typeColors = renderer.getTypeColorRGBComponents();
        if(typeColors==null || typeColors.length==0) {
            log.warning("cannot render events because there are no event type colors, skipping rendering of events");
            return;
        }
        for (Object o : ae) {
            TypedEvent ev = (TypedEvent) o;
            gl.glColor3fv(typeColors[ev.type], 0);// FIXME depends on these colors having been created by a rendering cycle...
//            CochleaGramDisplayMethod.typeColor(gl,ev.type);
            float t = (float) (ev.timestamp - startTime); // z goes from 0 (oldest) to 1 (youngest)
            gl.glRectf(t, ev.x, t + w, ev.x + 1);
            if (t > timeWidthUs || t < 0) {
                clearScreenEnabled = true;
            }
        }

 

        gl.glFlush();
//        gl.glFinish();  // should not need to be called, according to http://www.opengl.org/discussion_boards/ubbthreads.php?ubb=showflat&Number=196733

        getChipCanvas().checkGLError(gl, glu, "after RollingCochleaGramDisplayMethod");
    }

    void clearScreen(GL gl) {
        gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        // draw axes
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(4f);
        gl.glBegin(GL.GL_LINES);
        {
            gl.glVertex3f(0, 0, 0);
            gl.glVertex3f(0, chip.getSizeX(), 0); // taps axis
        }
        gl.glEnd();
        gl.glRasterPos3f(0, chip.getSizeX(), 0);
        glut.glutBitmapString(font, "Channel");
        getChipCanvas().checkGLError(gl, glu, "after RollingCochleaGramDisplayMethod,clearScreen");
    }
    
    protected float getTimeWidthUs(){
        return timeWidthUs;
    }
    
    protected float getStartTimeUs(){
        return startTime;
    }
}
