/*
 * DisplayMethod.java
 *
 * Created on May 4, 2006, 8:53 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.graphics;

import ch.unizh.ini.caviar.chip.*;
import com.sun.opengl.util.*;
import java.util.ArrayList;
import java.util.logging.Logger;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.swing.JMenuItem;

/**
 * A abstract class that displays AE data in a ChipCanvas using OpenGL. 
 * @author tobi
 */
public abstract class DisplayMethod {
    protected ChipCanvas chipCanvas;
    protected GLUT glut; // GL extensions
    protected GLU glu; // GL utilities
    protected Chip2D chip;
    protected ChipCanvas.Zoom zoom;
    GL gl;
    protected Logger log=Logger.getLogger("graphics");
    private JMenuItem menuItem;
    private ArrayList<FrameAnnotater> annotators = new ArrayList<FrameAnnotater>();
    
    /** Creates a new instance of DisplayMethod
     @param parent the containing ChipCanvas
     */
    public DisplayMethod(ChipCanvas parent){
        chipCanvas=parent;
        glut=chipCanvas.glut;
        glu=chipCanvas.glu;
        chip=chipCanvas.getChip();
        zoom=chipCanvas.zoom;
    }
    
    /** this useful utility method sets up the gl context for rendering. It is called at the the start of most of the DisplayMethods. 
     * It scales x,y,z in chip pixels (address by 1 increments),
     *clears the background to the ChipRenderer gray level, sets the origin to the lower left corner of the screen 
     * with coordinates increase upwards and to right.
     @param drawable the drawable passed in
     **/
    public GL setupGL(GLAutoDrawable drawable){ // TODO could this be a static method?
        gl = drawable.getGL();
        if (gl==null)throw new RuntimeException("null GL from drawable");
        
//        chipCanvas.setDefaultProjection(gl,drawable);

// each display method is responsible for its own starting drawing surface color
//        float gray = renderer.getGrayValue();
//        gl.glClearColor(gray,gray,gray,0f);
//        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        
//        gl.glPushMatrix();
//        chipCanvas.checkGLError(gl,glu,"before setting up GL context for pixel drawing");
        gl.glLoadIdentity();
        
        // center display. do this by taking half of difference between screen width in screen pixels and chip screen size in screen pixels
        float xt = (chipCanvas.drawable.getWidth() - chipCanvas.getPwidth()) / 2;
        float yt = (chipCanvas.drawable.getHeight() - chipCanvas.getPheight()) / 2;
        
        // translate origin to this point
        gl.glTranslatef(xt,yt,0);
        
        // scale everything by pixel size scale
        gl.glScalef(chipCanvas.getScale(),chipCanvas.getScale(),1);
        
        // make sure we're drawing back buffer (this is probably true anyhow)
//        chipCanvas.checkGLError(gl,glu,"after setting scale, before setting back buffer drawing");
//        gl.glDrawBuffer(GL.GL_BACK); // this can throw an error 1282 on platforms without double buffering e.g. linux software GL
//        gl.glPopMatrix();
        return gl;
    }
    
    /** subclasses implmeent this display method to actually render. Typically they also call GL gl=setupGL(drawable) right after entry.
     @param drawable the GL context
     */
    abstract public void display(GLAutoDrawable drawable);
    
    public String getDescription(){
        return this.getClass().getSimpleName();
    }

    public JMenuItem getMenuItem() {
        return menuItem;
    }

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
}
