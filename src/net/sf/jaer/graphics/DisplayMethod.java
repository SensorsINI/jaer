/*
 * DisplayMethod.java
 *
 * Created on May 4, 2006, 8:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.swing.JMenuItem;

import net.sf.jaer.chip.Chip2D;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeSupport;
import org.apache.commons.text.WordUtils;

/**
 * A abstract class that displays AE data in a ChipCanvas using OpenGL.
 *
 * @author tobi
 */
public abstract class DisplayMethod {

    private ChipCanvas chipCanvas;
    protected GLUT glut; // GL extensions
    protected GLU glu; // GL utilities
    protected Chip2D chip;
    protected ChipCanvas.Zoom zoom;
    protected Logger log = Logger.getLogger("graphics");
    private JMenuItem menuItem;
    private ArrayList<FrameAnnotater> annotators = new ArrayList<>();
    private String statusChangeString = null;
    private long statusChangeStartTimeMillis = 0;
    private final long statusChangeDisplayTimeMillis = 1000;
    /**
     * Provides PropertyChangeSupport for all DisplayMethods
     */
    private PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * Creates a new instance of DisplayMethod
     *
     * @param parent the containing ChipCanvas
     */
    public DisplayMethod(ChipCanvas parent) {
        chipCanvas = parent;
        glut = chipCanvas.glut;
        glu = chipCanvas.glu;
        chip = chipCanvas.getChip();
        zoom = chipCanvas.getZoom();
    }

    /**
     * This utility method sets up the gl context for rendering. It is called at
     * the the start of most of the DisplayMethods. It scales x,y,z in chip
     * pixels (address by 1 increments), and sets the origin to the lower left
     * corner of the screen with coordinates increase upwards and to right.
     *
     * @param drawable the drawable passed in.
     * @return the context to draw in.
     *
     */
    public static GL2 setupGL(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            throw new RuntimeException("null GL from drawable");
        }

        gl.glLoadIdentity();

        return gl;
    }

    /**
     * Subclasses implement this display method to actually render. Typically
     * they also call GL2 gl=setupGL(drawable) right after entry.
     *
     * @param drawable the GL context
     */
    abstract public void display(GLAutoDrawable drawable);

    public String getDescription() {
        return this.getClass().getSimpleName();
    }

    /**
     * The display method corresponding menu item.
     *
     * @return The menu item for this DisplayMethod.
     */
    public JMenuItem getMenuItem() {
        return menuItem;
    }

    /**
     * The display method corresponding menu item.
     *
     * @param menuItem The menu item for this DisplayMethod.
     */
    public void setMenuItem(JMenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public Chip2DRenderer getRenderer() {
        return chipCanvas.getRenderer();
    }

    public void setRenderer(Chip2DRenderer renderer) {
        chipCanvas.setRenderer(renderer);
    }

    public ArrayList<FrameAnnotater> getAnnotators() {
        return annotators;
    }

    public void setAnnotators(ArrayList<FrameAnnotater> annotators) {
        this.annotators = annotators;
    }

    /**
     * add an annotator to the drawn canvas. This is one way to annotate the
     * drawn data; the other way is to annotate the histogram frame data.
     *
     * @param annotator the object that will annotate the frame data
     */
    public synchronized void addAnnotator(FrameAnnotater annotator) {
        annotators.add(annotator);
    }

    /**
     * removes an annotator to the drawn canvas.
     *
     * @param annotator the object that will annotate the displayed data
     */
    public synchronized void removeAnnotator(FrameAnnotater annotator) {
        annotators.remove(annotator);
    }

    /**
     * removes all annotators
     */
    public synchronized void removeAllAnnotators() {
        annotators.clear();
    }

    /**
     * @return the chipCanvas
     */
    public ChipCanvas getChipCanvas() {
        return chipCanvas;
    }

    public void setChipCanvas(ChipCanvas c) {
        chipCanvas = c;
    }

    /**
     * Called when this is added to the ChipCanvas. Empty by default.
     *
     */
    protected void onRegistration() {
    }

    /**
     * Called when this is removed from the ChipCanvas. Empty by default.
     *
     */
    protected void onDeregistration() {
    }

    /**
     * shows the status change display centered over the image
     *
     * @param drawable the OpenGL context
     */
    protected void displayStatusChangeText(GLAutoDrawable drawable) {
        if (statusChangeString == null) {
            return;
        }
        long now = System.currentTimeMillis();
        final int WRAP_LEN = 20;
        if ((now - statusChangeStartTimeMillis) > statusChangeDisplayTimeMillis * (1 + (statusChangeString.length() / WRAP_LEN))) {
            statusChangeString = null;
            return;
        }
        String s = statusChangeString;
        if (s.length() > WRAP_LEN) {
            s = WordUtils.wrap(s, WRAP_LEN);
        }
        String[] ss = s.split("\n");
        int nlines = ss.length;

        GL2 gl = drawable.getGL().getGL2();
        TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        // need to translate to correct position
//        gl.glPushMatrix();
//        for (String sss : ss) {
//            Rectangle2D r2 = renderer.getBounds(sss);
//            gl.glColor4f(1, 1, 1, 1);
//            gl.glRectd(r2.getMinX(), r2.getMinY(), r2.getMaxX(), r2.getMaxY());
//        }
//        gl.glPopMatrix();
        try {
            renderer.begin3DRendering();
            renderer.setColor(Color.BLUE);

            Rectangle2D r = renderer.getBounds(ss[0]);
            float h1 = (float) (r.getHeight());
            float ht = (float) h1 * nlines;
            float w = (float) (r.getWidth());
            float scale = .5f;
            final float linespace = (float) (h1 * scale * 1.2f);
            float ypos = (float) (chip.getSizeY() / 2 + ht / 2 * scale - linespace / 2);
            float xpos = (float) (chip.getSizeX() / 2 - w / 2 * scale);
            float y = ypos;
            for (String sss : ss) {
                renderer.draw3D(sss, xpos, y, 0, scale);
                y -= linespace;
            }
            renderer.end3DRendering();
        } catch (GLException e) {
            log.warning("caught " + e + " when trying to render text into the current OpenGL context");
        }
    }

    /**
     * Shows the status change text momentarily centered in middle of display,
     * for DisplayMethod that implement it.
     *
     * @param text
     */
    public void showActionText(String text) {
//        if(statusChangeString!=null) text=statusChangeString+", "+text;
        statusChangeStartTimeMillis = System.currentTimeMillis();
        statusChangeString = text;
    }

    /**
     * PropertyChangeSupport for all DisplayMethods.
     *
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }
}
