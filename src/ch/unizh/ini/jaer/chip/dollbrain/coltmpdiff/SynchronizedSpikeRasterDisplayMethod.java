/*
 * RetinaCanvas.java
 *
 * Created on January 9, 2006, 6:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dollbrain.coltmpdiff;

//import ch.unizh.ini.caviar.aemonitor.EventXYType;
import ch.unizh.ini.jaer.chip.cochlea.*;
import java.util.prefs.Preferences;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.*;
import com.sun.opengl.util.GLUT;
import javax.media.opengl.*;

/**
 * Shows events from a single or few chip addresses as a rastergram, synchronized by a special event.
 * Raster traces are drawn left to right, rastering down from top of display on each sync event.
 * 
 * The time is scaled so that the entire width of the screen is the time between sync events.
 * @author tobi
 */
public class SynchronizedSpikeRasterDisplayMethod extends DisplayMethod implements DisplayMethod2D, GLEventListener {
    static Preferences prefs=Preferences.userNodeForPackage(SynchronizedSpikeRasterDisplayMethod.class);
    /**
     * Creates a new instance of SynchronizedSpikeRasterDisplayMethod
     *
     * @param c the canvas we are drawing on
     */
    public SynchronizedSpikeRasterDisplayMethod(ChipCanvas c) {
        super(c);
        c.addGLEventListener(this);
    }
    final float rasterWidth = 0.01f; // width of each spike tick;
    final int BORDER = 50; // pixels
    int maxNumRasters = prefs.getInt("SynchronizedSpikeRasterDisplayMethod.maxNumRasters", 30);
    boolean clearScreenEnabled = true;
    int font = GLUT.BITMAP_HELVETICA_18;
    int oldMaxNumRasters = 0;
    boolean hasBlend = false;
    boolean hasBlendChecked = false;
    int lastSyncTime = 0;
    int maxTime = 1;
    int rasterNumber = 0;

    EventPacket spikes=new EventPacket(SyncEvent.class);
    OutputEventIterator addSpikeIterator=spikes.outputIterator();

    void redrawSpikes(GLAutoDrawable drawable){
        GL gl = setupMyGL(drawable);
        clearScreen(gl);
        drawSpikes(drawable, gl, spikes);
    }

    void addSpike(SyncEvent ev){
        addSpikeIterator.nextOutput().copyFrom(ev);
    }

    /** displays individual events as rastergram
     * @param drawable the drawable passed in by OpenGL
     */
    synchronized public void display(GLAutoDrawable drawable) {
        EventPacket ae = (EventPacket) chip.getLastData();
        if (ae == null || ae.isEmpty()) {
            return;
        }
        GL gl = setupMyGL(drawable);
//        gl.glFinish();  // should not need to be called, according to http://www.opengl.org/discussion_boards/ubbthreads.php?ubb=showflat&Number=196733

        drawSpikes(drawable, gl,ae);
    }

    void clearScreen(GL gl) {
        gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
        gl.glClearColor(0, 0, 0, 0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        // draw axes
//        gl.glColor3f(0, 0, 1);
//        gl.glLineWidth(4f);
//        gl.glBegin(GL.GL_LINES);
//        {
//            gl.glVertex3f(0, 0, 0);
//            gl.glVertex3f(0, chip.getSizeX(), 0); // taps axis
//        }
//        gl.glEnd();
//        gl.glRasterPos3f(0, chip.getSizeX(), 0);
//        glut.glutBitmapString(font, "Channel");
        chipCanvas.checkGLError(gl, glu, "after display drawing,clearScreen");
    }

    public void init(GLAutoDrawable drawable) {

    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        clearScreenEnabled=true;
    }

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
        clearScreenEnabled=true;
    }

    private void drawSpikes(GLAutoDrawable drawable, GL gl, EventPacket ae) {
        // scale everything by rastergram scale
        // timewidth comes from render contrast setting
        // width starts with colorScale=1 to be the standard refresh rate
        // so that a raster will refresh every frame and reduces by powers of two
        maxNumRasters = 1 << getRenderer().getColorScale();
        if (maxNumRasters != oldMaxNumRasters) {
            clearScreenEnabled = true;
            maxTime = 1;
            prefs.putInt("SynchronizedSpikeRasterDisplayMethod.maxNumRasters", maxNumRasters);
            rasterNumber = 0;
        }
        float yScale = (drawable.getHeight()) / (float) maxNumRasters; // scale vertical is draableHeight/numPixels
        oldMaxNumRasters = maxNumRasters;
        float timeWidth = maxTime; // set horizontal scale so that we can just use relative timestamp for x
        float drawTimeScale = drawable.getWidth() / timeWidth; // scale horizontal is draw
        gl.glScalef(drawTimeScale, yScale, 1);
        // make sure we're drawing back buffer (this is probably true anyhow)
        gl.glDrawBuffer(GL.GL_FRONT_AND_BACK);
        // render events
        if (clearScreenEnabled) {
            clearScreen(gl);
            clearScreenEnabled = false;
        }
        final float w = (float) timeWidth / chipCanvas.getCanvas().getWidth(); // spike raster as fraction of screen width
        for (Object o : ae) {
            SyncEvent ev = (SyncEvent) o;
            if (ev.isSyncEvent()) {
                lastSyncTime = ev.timestamp;
                rasterNumber++;
                if (rasterNumber > maxNumRasters) {
                    rasterNumber = 0;
                    clearScreen(gl);
                    spikes.clear();
                }
            }
            switch (ev.type) {
                case 0:
                    gl.glColor3f(0, 1, 0);
                    break;
                case 1:
                    gl.glColor3f(1, 0, 0);
                    break;
                case 2:
                    gl.glColor3f(0, 0, 1);
                    break;
                default:
                    gl.glColor3f(.1f, .1f, .1f);
            }
//            CochleaGramDisplayMethod.typeColor(gl,ev.type);
            int dt = ev.timestamp - lastSyncTime;
            if (dt > maxTime) {
                maxTime = dt;
            }
//            float t = (float) (ev.timestamp-lastSyncTime); // z goes from 0 (oldest) to 1 (youngest)
            gl.glRectf(dt, rasterNumber, dt + w, rasterNumber + 1);
        }
        gl.glFlush();
//        gl.glFinish();  // should not need to be called, according to http://www.opengl.org/discussion_boards/ubbthreads.php?ubb=showflat&Number=196733
        chipCanvas.checkGLError(gl, glu, "after display drawing");
    }

    private GL setupMyGL(GLAutoDrawable drawable) {
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
        return gl;
    }
}

