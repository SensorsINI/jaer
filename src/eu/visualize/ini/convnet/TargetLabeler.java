/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import eu.seebetter.ini.chips.ApsDvsChip;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.ChipCanvas;

/**
 * Labels location of target using mouse GUI in recorded data for later
 * supervised learning.
 *
 * @author tobi
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Labels location of target using mouse GUI in recorded data for later supervised learning.")
public class TargetLabeler extends EventFilter2DMouseAdaptor implements PropertyChangeListener {

    private boolean mousePressed = false;
    private Point mousePoint = null;
    final float labelRadius = 5f;
    private GLUquadric mouseQuad = null;
    private TreeMap<Integer, TargetLocation> targetLocations = new TreeMap();
    private TargetLocation targetLocation = null;
    private ApsDvsChip apsDvsChip = null;
    private int lastFrameNumber = -1;
    private int currentFrameNumber = -1;
    private final String LAST_FOLDER_KEY = "lastFolder";

    private boolean propertyChangeListenerAdded = false;

    public TargetLabeler(AEChip chip) {
        super(chip);
        if (chip instanceof ApsDvsChip) {
            apsDvsChip = ((ApsDvsChip) chip);
        }

    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!isSelected()) {
            return;
        }
  
        Point p = (getMousePixel(e));
        if (p != null) {
            if (mousePoint != null) {
                mousePoint.setLocation(p);
            } else {
                mousePoint = new Point(p);
            }
        } else {
            mousePoint = null;
        }
        System.out.println(e.getPoint()+"    "+glCanvas.getMousePosition()+"      "+p+"        "+mousePoint);
      }

    @Override
    public void mouseReleased(MouseEvent e) {
        mousePressed = false;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!isSelected()) {
            return;
        }
        mousePressed = true;
        Point p = (getMousePixel(e));
        if (p != null) {
            if (mousePoint != null) {
                mousePoint.setLocation(p);
            } else {
                mousePoint = new Point(p);
            }
        } else {
            mousePoint = null;
        }
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();

        if (targetLocation != null) {
            targetLocation.draw(gl);
        }

    }

    synchronized public void doClearLocations() {
        targetLocations.clear();
    }

    synchronized public void doSaveLocations() {

    }

    synchronized public void doLoadLocations() {

    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                propertyChangeListenerAdded = true;
            }
        }

        for (BasicEvent e : in) {
            if (apsDvsChip != null) {
                int newFrameNumber = apsDvsChip.getFrameCount();
                if (newFrameNumber != lastFrameNumber) {
                    if (newFrameNumber > lastFrameNumber) {
                        currentFrameNumber++;
                    } else if (newFrameNumber < lastFrameNumber) {
                        currentFrameNumber--;
                    }
                    lastFrameNumber = newFrameNumber;
                    // find next saved target location
                    Map.Entry<Integer, TargetLocation> entry = targetLocations.lowerEntry(currentFrameNumber);
                    if (entry != null) {
                        targetLocation = entry.getValue();
                    } else {
                        targetLocation = null;
                    }
                    if (mousePoint != null && mousePressed) {
                        TargetLocation newLocation=new TargetLocation(currentFrameNumber, e.timestamp, mousePoint);
                        targetLocations.put(currentFrameNumber, newLocation);
                    }
                }
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
        switch (evt.getPropertyName()) {
            case AEInputStream.EVENT_REWIND:
                log.info("frameNumber reset to -1");
                lastFrameNumber = -1;
                currentFrameNumber = 0;
                break;
            case AEInputStream.EVENT_POSITION:
                break;
            case AEInputStream.EVENT_EOF:

        }
    }

    private class TargetLocationComparator implements Comparator<TargetLocation> {

        @Override
        public int compare(TargetLocation o1, TargetLocation o2) {
            return Integer.valueOf(o1.frameNumber).compareTo(Integer.valueOf(o2.frameNumber));
        }

    }

    private class TargetLocation {

        int timestamp;
        int frameNumber;
        Point location;

        public TargetLocation(int frameNumber, int timestamp, Point location) {
            this.frameNumber = frameNumber;
            this.timestamp = timestamp;
            this.location = new Point(location);
        }

        private void draw(GL2 gl) {
            if (targetLocation.location == null) {
                return;
            }
            gl.glTranslatef(targetLocation.location.x, targetLocation.location.y, 0f);
            gl.glColor4f(0, 1, 0, .5f);
            if (mouseQuad == null) {
                mouseQuad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
            glu.gluDisk(mouseQuad, 0, 5, 16, 1);
        }

        public String toString() {
            return String.format("TargetLocation frameNumber=%d timestamp=%d location=%s", frameNumber, timestamp, location.toString());
        }

    }

//    private class TargetLocations extends TreeMap<Integer, TargetLocation> {
//
//        private TargetLocations(TargetLocationComparator targetLocationComparator) {
//            super(targetLocationComparator);
//        }
//
//        private void add(TargetLocation location) {
//            put(location.frame, location);
//        }
//
//    }
}
