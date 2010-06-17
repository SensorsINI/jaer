/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;
import com.sun.opengl.util.GLUT;
import java.awt.BorderLayout;
import java.awt.geom.Rectangle2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Controls slot car tracked from eye of god view.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarRacer extends EventFilter2D implements FrameAnnotater{
    private boolean showTrack = prefs().getBoolean("SlotCarRacer.showTrack",true);
    private boolean virtualCar = prefs().getBoolean("SlotCarRacer.virtualCar",true);
    JFrame trackFrame = null;
    GLUT glut = new GLUT();
    GLU glu = new GLU();
    GLCanvas trackCanvas;
    SlotCarTrackModel trackModel = null;
    private boolean fillsVertically = false, fillsHorizontally = false;

    public SlotCarRacer (AEChip chip){
        super(chip);
//        trackModel=new SlotCarTrackModel(this);
        trackModel = SlotCarTrackModel.makeOvalTrack(this);
        doShowTrack();
    }

    public void doShowTrack (){
        if ( trackFrame == null ){
            trackFrame = new JFrame("SlotCarTrack");
            trackCanvas = new GLCanvas();
            trackCanvas.addGLEventListener(new GLEventListener(){
                public void init (GLAutoDrawable drawable){
                }

                synchronized public void display (GLAutoDrawable drawable){
                    GL gl = drawable.getGL();
                    gl.glLoadIdentity();
//                    gl.glScalef(drawable.getWidth() / nTheta,drawable.getHeight() / nRho,1);
                    gl.glClearColor(0,0,0,0);
                    gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                    int error = gl.glGetError();
                    if ( error != GL.GL_NO_ERROR ){
                        if ( glu == null ){
                            glu = new GLU();
                        }
                        log.warning("GL error number " + error + " " + glu.gluErrorString(error));
                    }
                    if ( trackModel != null ){
                        Rectangle2D.Float bounds = trackModel.getBounds();
                        final float w = (float)bounds.getWidth(), h = (float)bounds.getHeight();
                        final float max = w > h ? w : h;
                        final float x0 = (float)bounds.getMinX(), x1 = (float)bounds.getMaxX(), y0 = (float)bounds.getMinY(), y1 = (float)bounds.getMaxY();
                        // set up ortho so that clipping covers largest dimension
                        gl.glMatrixMode(GL.GL_PROJECTION);
                        gl.glLoadIdentity();
                        if ( w > h ){
                            gl.glOrtho(x0,x1,x0,x1,-10000,10000);
                        } else{
                            gl.glOrtho(y0,y1,y0,y1,-10000,10000);
                        }
                        gl.glMatrixMode(GL.GL_MODELVIEW);
                        int dh=drawable.getHeight(), dw=drawable.getWidth();
                        int dm=dh>dw? dh:dw;
                        gl.glViewport(30,30,dm - 60,dm - 60);
                        trackModel.draw(gl);
                    }
                    gl.glFlush();
                }

                synchronized public void reshape (GLAutoDrawable drawable,int x,int y,int width,int height){
                    GL gl = drawable.getGL();
                    if ( trackModel != null ){
                        Rectangle2D.Float bounds = trackModel.getBounds();
                        final float w = (float)bounds.getWidth(), h = (float)bounds.getHeight();
                        final float max = w > h ? w : h;
                        final float x0 = (float)bounds.getMinX(), x1 = (float)bounds.getMaxX(), y0 = (float)bounds.getMinY(), y1 = (float)bounds.getMaxY();
                        // set up ortho so that clipping covers largest dimension
                        gl.glMatrixMode(GL.GL_PROJECTION);
                        gl.glLoadIdentity();
                        if ( w > h ){
                            gl.glOrtho(x0,x1,x0,x1,-10000,10000);
                        } else{
                            gl.glOrtho(y0,y1,y0,y1,-10000,10000);
                        }
                        gl.glMatrixMode(GL.GL_MODELVIEW);
                        gl.glViewport(30,30,drawable.getWidth() - 60,drawable.getHeight() - 60);
                    }
                }

                public void displayChanged (GLAutoDrawable drawable,boolean modeChanged,boolean deviceChanged){
                }
            });

            trackFrame.getContentPane().add(trackCanvas,BorderLayout.CENTER);
        }
        trackFrame.setVisible(true);
    }

    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        return in;
    }

    @Override
    public void resetFilter (){
    }

    @Override
    public void initFilter (){
    }

    public void annotate (GLAutoDrawable drawable){
        if ( trackCanvas != null ){
            trackCanvas.display();
        }
    }
}
