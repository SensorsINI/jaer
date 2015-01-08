/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Labels location of target using mouse GUI in recorded data for later
 * supervised learning.
 *
 * @author tobi
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Labels location of target using mouse GUI in recorded data for later supervised learning.")
public class TargetLabeler extends EventFilter2DMouseAdaptor implements PropertyChangeListener {

    private boolean mousePressed=false;
    private Point mousePoint=new Point();
    private ChipCanvas glCanvas=null;
    final float labelRadius=5f;
    private GLUquadric mouseQuad=null;
    
    private boolean propertyChangeListenerAdded = false;

    public TargetLabeler(AEChip chip) {
        super(chip);

    }

    @Override
    public void mouseDragged(MouseEvent e) {
       if(glCanvas!=null){
            mousePoint.setLocation(glCanvas.getPixelFromMouseEvent(e));
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        mousePressed=false;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mousePressed=true;
            mousePoint.setLocation(getMousePixel(e));
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); 
        GL2 gl=drawable.getGL().getGL2();
//        if(mousePoint!=null){
//                gl.glTranslatef(mousePoint.x, mousePoint.y, 0f);
//                gl.glColor4f(0, 1, 0, .5f);
//                glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
//                glu.gluDisk(mouseQuad, 0, 5, 16, 1);
//        
//        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                propertyChangeListenerAdded = true;
            }
        }
        if(glCanvas==null){
            if(chip.getCanvas()!=null) {
                glCanvas=chip.getCanvas();
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch(evt.getPropertyName()){
            case AEInputStream.EVENT_REWIND:
                break;
            case AEInputStream.EVENT_POSITION:
                break;
            case AEInputStream.EVENT_EOF:
                
        }
    }

}
