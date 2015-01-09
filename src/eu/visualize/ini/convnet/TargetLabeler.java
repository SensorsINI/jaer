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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.JOptionPane;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;

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
    private String DEFAULT_FILENAME = "locations.txt";

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
        System.out.println(e.getPoint() + "    " + glCanvas.getMousePosition() + "      " + p + "        " + mousePoint);
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
        saveLocations(new File(DEFAULT_FILENAME));
    }

    synchronized public void doLoadLocations() {
        loadLocations(new File(DEFAULT_FILENAME));
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
                        TargetLocation newLocation = new TargetLocation(currentFrameNumber, e.timestamp, mousePoint);
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
            gl.glPushMatrix();
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
            gl.glPopMatrix();
        }

        public String toString() {
            return String.format("TargetLocation frameNumber=%d timestamp=%d location=%s", frameNumber, timestamp, location.toString());
        }

    }

    private void saveLocations(File f) {
        try {
            FileWriter writer = new FileWriter(f);
            writer.write(String.format("# target locations\n"));
            writer.write(String.format("# written %s\n", new Date().toString()));
            writer.write(String.format("# frameNumber timestamp x y\n"));
            for (Map.Entry<Integer, TargetLocation> entry : targetLocations.entrySet()) {
                TargetLocation l = entry.getValue();
                writer.write(String.format("%d %d %d %d\n", l.frameNumber, l.timestamp, l.location.x, l.location.y));
            }
            writer.close();
            log.info("wrote locations to file " + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't save locations", JOptionPane.WARNING_MESSAGE, null);
            return;
        }
    }

    private void loadLocations(File f) {
        targetLocations.clear();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String s = reader.readLine();
            StringBuilder sb = new StringBuilder();
            while (s != null && s.startsWith("#")) {
                sb.append(s);
                s = reader.readLine();
            }
            log.info("header lines on " + f.getAbsolutePath() + " are\n" + sb.toString());
            while (s != null) {
                Scanner scanner = new Scanner(s);
                TargetLocation targetLocation = new TargetLocation(scanner.nextInt(), scanner.nextInt(), new Point(scanner.nextInt(), scanner.nextInt()));
                targetLocations.put(targetLocation.frameNumber, targetLocation);
                s=reader.readLine();
            }
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
        }
    }

}
