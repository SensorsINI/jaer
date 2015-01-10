/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.ApsDvsChip;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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
import java.util.InputMismatchException;
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
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Labels location of target using mouse GUI in recorded data for later
 * supervised learning.
 *
 * @author tobi
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Labels location of target using mouse GUI in recorded data for later supervised learning.")
public class TargetLabeler extends EventFilter2DMouseAdaptor implements PropertyChangeListener, KeyListener {

    private boolean mousePressed = false;
    private boolean shiftPressed = false;
    private Point mousePoint = null;
    final float labelRadius = 5f;
    private GLUquadric mouseQuad = null;
    private TreeMap<Integer, TargetLocation> targetLocations = new TreeMap();
    private TargetLocation targetLocation = null;
    private ApsDvsChip apsDvsChip = null;
    private int lastFrameNumber = -1;
    private int lastTimestamp = Integer.MIN_VALUE;
    private int currentFrameNumber = -1;
    private final String LAST_FOLDER_KEY = "lastFolder";
    TextRenderer textRenderer = null;
    private int minTargetPointIntervalUs = getInt("minTargetPointIntervalUs", 1000);
    private int targetRadius = getInt("targetRadius", 10);

    private boolean propertyChangeListenerAdded = false;
    private String DEFAULT_FILENAME = "locations.txt";

    public TargetLabeler(AEChip chip) {
        super(chip);
        if (chip instanceof ApsDvsChip) {
            apsDvsChip = ((ApsDvsChip) chip);
        }
        setPropertyTooltip("minTargetPointIntervalUs", "minimum interval between target positions in the database in us");

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
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
            textRenderer.setColor(1, 1, 1, 1);
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(1, 1, 1);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        MultilineAnnotationTextRenderer.renderMultilineString("Shift+Left mouse down: specify target location\nNo Shift+Left mouse: No target visible");
        if (targetLocation != null) {
            targetLocation.draw(drawable, gl);
        }

    }

    synchronized public void doClearLocations() {
        targetLocations.clear();
    }

    synchronized public void doSaveLocations() {
        File f = new File(DEFAULT_FILENAME);
        if (f.exists()) {
            int ret = JOptionPane.showConfirmDialog(glCanvas, "File " + f.getAbsolutePath() + " already exists, overwrite it?");
            if (ret != JOptionPane.OK_OPTION) {
                return;
            }
        }
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

                // update actual frame number, starting from 0 at start of recording (for playback or after rewind)
                // this can be messed up by jumping in the file using slider
                int newFrameNumber = apsDvsChip.getFrameCount();
                if (newFrameNumber != lastFrameNumber) {
                    if (newFrameNumber > lastFrameNumber) {
                        currentFrameNumber++;
                    } else if (newFrameNumber < lastFrameNumber) {
                        currentFrameNumber--;
                    }
                    lastFrameNumber = newFrameNumber;
                }

                // show the nearest TargetLocation if at least minTargetPointIntervalUs has passed by,
                // or "No target" if the location was previously 
                if ((long) e.timestamp - (long) lastTimestamp > minTargetPointIntervalUs) {
                    lastTimestamp = e.timestamp;
                    // find next saved target location that is just before this time (lowerEntry)
                    Map.Entry<Integer, TargetLocation> entry = targetLocations.lowerEntry(e.timestamp);
                    if (entry != null) {
                        targetLocation = entry.getValue();
                    } else {
                        targetLocation = null;
                    }
                    if (mousePressed && mousePoint != null && shiftPressed) {
                        removeLocation(entry, e);
                        TargetLocation newLocation = new TargetLocation(currentFrameNumber, e.timestamp, mousePoint);
                        targetLocations.put(e.timestamp, newLocation);

                    } else if (mousePressed && !shiftPressed) {
                         removeLocation(entry, e);
                        TargetLocation newLocation = new TargetLocation(currentFrameNumber, e.timestamp, null);
                        targetLocations.put(e.timestamp, newLocation);
                    }
                }

            }
        }
        return in;
    }

    private void removeLocation(Map.Entry<Integer, TargetLocation> entry, BasicEvent e) {
        if (entry != null && e.timestamp - entry.getKey() < minTargetPointIntervalUs) {
            targetLocations.remove(entry.getKey());
        }
    }

    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes); // register/deregister mouse listeners
        if (yes) {
            glCanvas.addKeyListener(this);
        } else {
            glCanvas.removeKeyListener(this);
        }
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt
    ) {
        switch (evt.getPropertyName()) {
            case AEInputStream.EVENT_REWIND:
                log.info("frameNumber reset to -1");
                lastFrameNumber = -1;
                currentFrameNumber = 0;
                lastTimestamp = Integer.MIN_VALUE;
                break;
            case AEInputStream.EVENT_POSITION:
                break;
            case AEInputStream.EVENT_EOF:

        }
    }

    /**
     * @return the minTargetPointIntervalUs
     */
    public int getMinTargetPointIntervalUs() {
        return minTargetPointIntervalUs;
    }

    /**
     * @param minTargetPointIntervalUs the minTargetPointIntervalUs to set
     */
    public void setMinTargetPointIntervalUs(int minTargetPointIntervalUs) {
        this.minTargetPointIntervalUs = minTargetPointIntervalUs;
        putInt("minTargetPointIntervalUs", minTargetPointIntervalUs);
    }

    @Override
    public void keyTyped(KeyEvent ke) {
    }

    @Override
    public void keyPressed(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent ke) {
        if (ke.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftPressed = false;
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
            this.location = location != null ? new Point(location) : null;
        }

        private void draw(GLAutoDrawable drawable, GL2 gl) {
            
            if (targetLocation.location == null) {
                textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
                textRenderer.draw("No target", chip.getSizeX() / 2, chip.getSizeY() / 2);
                textRenderer.endRendering();
                return;
            }
            gl.glPushMatrix();
            gl.glTranslatef(targetLocation.location.x, targetLocation.location.y, 0f);
            gl.glColor4f(0, 1, 0, .5f);
            if (mouseQuad == null) {
                mouseQuad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
            glu.gluDisk(mouseQuad, targetRadius, targetRadius + 1, 32, 1);
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
                if (l.location != null) {
                    writer.write(String.format("%d %d %d %d\n", l.frameNumber, l.timestamp, l.location.x, l.location.y));
                } else {
                    writer.write(String.format("%d %d null\n", l.frameNumber, l.timestamp));
                }
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
                sb.append(s + "\n");
                s = reader.readLine();
            }
            log.info("header lines on " + f.getAbsolutePath() + " are\n" + sb.toString());
            while (s != null) {
                Scanner scanner = new Scanner(s);
                try {
                    TargetLocation targetLocation = new TargetLocation(scanner.nextInt(), scanner.nextInt(), new Point(scanner.nextInt(), scanner.nextInt())); // read target location
                    targetLocations.put(targetLocation.timestamp, targetLocation);
                } catch (InputMismatchException ex) {
                    // infer this line is null target sample
                    Scanner scanner2 = new Scanner(s);
                    try {
                        TargetLocation targetLocation = new TargetLocation(scanner2.nextInt(), scanner2.nextInt(), null);
                        targetLocations.put(targetLocation.timestamp, targetLocation);
                    } catch (InputMismatchException ex2) {
                        throw new IOException("couldn't parse file, got InputMismatchException on line: " + s);
                    }
                }
                s = reader.readLine();
            }
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
        }
    }

}
