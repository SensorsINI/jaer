/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Color;
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
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import java.awt.Cursor;
import java.util.Arrays;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.AEViewer;

/**
 * Labels location of target using mouse GUI in recorded data for later
 * supervised learning.
 *
 * @author tobi
 */
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
@Description("Labels location of target using mouse GUI in recorded data for later supervised learning.")
public class TargetLabeler extends EventFilter2DMouseAdaptor implements PropertyChangeListener, KeyListener {

    private boolean mousePressed = false;
    private boolean shiftPressed = false;
    private boolean ctlPressed = false;
    private Point mousePoint = null;
    final float labelRadius = 5f;
    private GLUquadric mouseQuad = null;
    private TreeMap<Integer, TargetLocation> targetLocations = new TreeMap();
    private TargetLocation targetLocation = null;
    private DavisChip apsDvsChip = null;
    private int lastFrameNumber = -1;
    private int lastTimestamp = Integer.MIN_VALUE;
    private int currentFrameNumber = -1;
    private final String LAST_FOLDER_KEY = "lastFolder";
    TextRenderer textRenderer = null;
    private int minTargetPointIntervalUs = getInt("minTargetPointIntervalUs", 10000);
    private int targetRadius = getInt("targetRadius", 10);
    private int maxTimeLastTargetLocationValidUs = getInt("maxTimeLastTargetLocationValidUs", 100000);
    private int minSampleTimestamp = Integer.MAX_VALUE, maxSampleTimestamp = Integer.MIN_VALUE;
    private final int N_FRACTIONS = 1000;
    private boolean[] labeledFractions = new boolean[N_FRACTIONS];  // to annotate graphically what has been labeled so far in event stream
    private boolean showLabeledFraction = getBoolean("showLabeledFraction", true);
    private boolean showHelpText = getBoolean("showHelpText", true);

    private boolean propertyChangeListenerAdded = false;
    private String DEFAULT_FILENAME = "locations.txt";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);

    // file statistics
    private long firstInputStreamTimestamp = 0, lastInputStreamTimestamp = 0, inputStreamDuration = 0;
    private long filePositionEvents = 0, fileLengthEvents = 0;

    public TargetLabeler(AEChip chip) {
        super(chip);
        if (chip instanceof DavisChip) {
            apsDvsChip = ((DavisChip) chip);
        }
        setPropertyTooltip("minTargetPointIntervalUs", "minimum interval between target positions in the database in us");
        setPropertyTooltip("targetRadius", "drawn radius of target in pixels");
        setPropertyTooltip("maxTimeLastTargetLocationValidUs", "this time after last sample, the data is shown as not yet been labeled");
        setPropertyTooltip("saveLocations", "saves target locations");
        setPropertyTooltip("saveLocationsAs", "show file dialog to save target locations to a new file");
        setPropertyTooltip("loadLocations", "loads locations from a file");
        setPropertyTooltip("showLabeledFraction", "shows labeled part of input by a bar with red=unlabeled, green=labeled, blue=current position in events");
        setPropertyTooltip("showHelpText", "shows help text on screen. Uncheck to hide");
        Arrays.fill(labeledFractions, false);
    }

    @Override
    public void mouseDragged(MouseEvent e) {

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
        mouseMoved(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {

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
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if(!isFilterEnabled()) return;
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return;
        }
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
            textRenderer.setColor(1, 1, 1, 1);
        }
        GL2 gl = drawable.getGL().getGL2();
        MultilineAnnotationTextRenderer.setColor(Color.CYAN);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        StringBuilder sb = new StringBuilder();
        if (showHelpText) {
            sb.append("Shift + Ctrl + mouse position: Specify target location\nShift: Specify no target seen\n");
            MultilineAnnotationTextRenderer.renderMultilineString(sb.toString());

            MultilineAnnotationTextRenderer.renderMultilineString(String.format("%d TargetLocation samples specified\nFirst sample time: %.1fs, Last sample time: %.1fs\nCurrent frame number: %d", targetLocations.size(), minSampleTimestamp * 1e-6f, maxSampleTimestamp * 1e-6f, currentFrameNumber));
        }
        if (shiftPressed && !ctlPressed) {
            MultilineAnnotationTextRenderer.renderMultilineString("Specifying no target");
        } else if (shiftPressed && ctlPressed) {
            MultilineAnnotationTextRenderer.renderMultilineString("Specifying target location");
        } else {
            MultilineAnnotationTextRenderer.renderMultilineString("Playing recorded target locations");
        }
        if (targetLocation != null) {
            targetLocation.draw(drawable, gl);
        }

        // show labeled parts
        if (showLabeledFraction && inputStreamDuration > 0) {
            float dx = chip.getSizeX() / (float) N_FRACTIONS;
            float y = chip.getSizeY() / 5;
            float dy = chip.getSizeY() / 50;
            float x = 0;
            for (int i = 0; i < N_FRACTIONS; i++) {
                boolean b = labeledFractions[i];
                if (b) {
                    gl.glColor3f(0, 1, 0);
                } else {
                    gl.glColor3f(1, 0, 0);
                }
                gl.glRectf(x, y, x + dx, y + dy);
                x += dx;
            }
            float curPosFrac = (float) filePositionEvents / fileLengthEvents;
            x = curPosFrac * chip.getSizeX();
            y = y + dy;
            gl.glColor3f(1, 1, 1);
            gl.glRectf(x - dx, y - dy * 2, x + dx, y + dy);
        }

    }

    synchronized public void doClearLocations() {
        targetLocations.clear();
        minSampleTimestamp = Integer.MAX_VALUE;
        maxSampleTimestamp = Integer.MIN_VALUE;
        Arrays.fill(labeledFractions, false);
    }

    synchronized public void doSaveLocationsAs() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showSaveDialog(glCanvas);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        if (c.getSelectedFile().exists()) {
            int r = JOptionPane.showConfirmDialog(glCanvas, "File " + c.getSelectedFile().toString() + " already exists, overwrite it?");
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        saveLocations(c.getSelectedFile());
    }

    synchronized public void doSaveLocations() {
        File f = new File(lastFileName);
        saveLocations(new File(lastFileName));
    }

    synchronized public void doLoadLocations() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showOpenDialog(glCanvas);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        loadLocations(new File(lastFileName));
    }

    private TargetLocation lastNewTargetLocation = null;

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return in;
        }
        if (!propertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                propertyChangeListenerAdded = true;
            }
        }

        for (BasicEvent e : in) {
            if (e.isSpecial()) {
                continue;
            }
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
                if (((long) e.timestamp - (long) lastTimestamp) >= minTargetPointIntervalUs) {
                    lastTimestamp = e.timestamp;
                    // find next saved target location that is just before this time (lowerEntry)
                    Map.Entry<Integer, TargetLocation> mostRecentLocationBeforeThisEvent = targetLocations.lowerEntry(e.timestamp);
                    if ((mostRecentLocationBeforeThisEvent == null) || ((mostRecentLocationBeforeThisEvent != null) && ((mostRecentLocationBeforeThisEvent.getValue() != null) && ((e.timestamp - mostRecentLocationBeforeThisEvent.getValue().timestamp) > maxTimeLastTargetLocationValidUs)))) {
                        targetLocation = null;
                    } else {
                        targetLocation = mostRecentLocationBeforeThisEvent.getValue();
                        updateLabeledFractions(targetLocation);
                    }
                    TargetLocation newTargetLocation = null;
                    if (shiftPressed && ctlPressed && (mousePoint != null)) {
                        // add a labeled location sample
                        maybeRemovePreviouslyRecordedSample(mostRecentLocationBeforeThisEvent, e, lastNewTargetLocation);
                        newTargetLocation = new TargetLocation(currentFrameNumber, e.timestamp, mousePoint);
                        targetLocations.put(e.timestamp, newTargetLocation);

                    } else if (shiftPressed && !ctlPressed) {
                        maybeRemovePreviouslyRecordedSample(mostRecentLocationBeforeThisEvent, e, lastNewTargetLocation);
                        newTargetLocation = new TargetLocation(currentFrameNumber, e.timestamp, null);
                        targetLocations.put(e.timestamp, newTargetLocation);
                    }
                    if (newTargetLocation != null) {
                        if (newTargetLocation.timestamp > maxSampleTimestamp) {
                            maxSampleTimestamp = newTargetLocation.timestamp;
                        }
                        if (newTargetLocation.timestamp < minSampleTimestamp) {
                            minSampleTimestamp = newTargetLocation.timestamp;
                        }
                    }
                    lastNewTargetLocation = newTargetLocation;
                }
                if (e.timestamp < lastTimestamp) {
                    lastTimestamp = e.timestamp;
                }

            }
        }
        return in;
    }

    private void maybeRemovePreviouslyRecordedSample(Map.Entry<Integer, TargetLocation> entry, BasicEvent e, TargetLocation lastSampleAdded) {
        if ((entry != null) && (entry.getValue() != lastSampleAdded) && ((e.timestamp - entry.getKey()) < minTargetPointIntervalUs)) {
            log.info("removing previous " + entry.getValue() + " because entry.getValue()!=lastSampleAdded=" + (entry.getValue() != lastSampleAdded) + " && timestamp difference " + (e.timestamp - entry.getKey()) + " is < " + minTargetPointIntervalUs);

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
            case AEInputStream.EVENT_POSITION:
                filePositionEvents = (long) evt.getNewValue();
                break;
            case AEInputStream.EVENT_REWIND:
            case AEInputStream.EVENT_REPOSITIONED:
                log.info("rewind to start or mark position or reposition event " + evt.toString());
                if (evt.getNewValue() instanceof Long) {
                    long position = (long) evt.getNewValue();
                    if (chip.getAeInputStream() == null) {
                        log.warning("AE input stream is null, cannot determine timestamp after rewind");
                        return;
                    }
                    int timestamp = chip.getAeInputStream().getMostRecentTimestamp();
                    Map.Entry<Integer, TargetLocation> targetBeforeRewind = targetLocations.lowerEntry(timestamp);
                    if (targetBeforeRewind != null) {
                        currentFrameNumber = targetBeforeRewind.getValue().frameNumber;
                        lastFrameNumber = currentFrameNumber - 1;
                        lastTimestamp = targetBeforeRewind.getValue().timestamp;
                    } else {
                        currentFrameNumber = 0;
                        lastFrameNumber = currentFrameNumber - 1;
                        lastInputStreamTimestamp = Integer.MIN_VALUE;
                    }
                } else {
                    log.warning("couldn't determine stream position after rewind from PropertyChangeEvent " + evt.toString());
                }
                break;
            case AEInputStream.EVENT_INIT:
                if (chip.getAeInputStream() != null) {
                    firstInputStreamTimestamp = chip.getAeInputStream().getFirstTimestamp();
                    lastTimestamp = chip.getAeInputStream().getLastTimestamp();
                    inputStreamDuration = chip.getAeInputStream().getDurationUs();
                    fileLengthEvents = chip.getAeInputStream().size();
                    if (inputStreamDuration > 0) {
                        fixLabeledFraction();
                    }
                }
                break;
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
        int k = ke.getKeyCode();
        if (k == KeyEvent.VK_SHIFT) {
            shiftPressed = true;
        } else if (k == KeyEvent.VK_CONTROL) {
            ctlPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent ke) {
        int k = ke.getKeyCode();
        if (k == KeyEvent.VK_SHIFT) {
            shiftPressed = false;
        } else if (k == KeyEvent.VK_CONTROL) {
            ctlPressed = false;
        }
    }

    /**
     * @return the targetRadius
     */
    public int getTargetRadius() {
        return targetRadius;
    }

    /**
     * @param targetRadius the targetRadius to set
     */
    public void setTargetRadius(int targetRadius) {
        this.targetRadius = targetRadius;
        putInt("targetRadius", targetRadius);
    }

    /**
     * @return the maxTimeLastTargetLocationValidUs
     */
    public int getMaxTimeLastTargetLocationValidUs() {
        return maxTimeLastTargetLocationValidUs;
    }

    /**
     * @param maxTimeLastTargetLocationValidUs the
     * maxTimeLastTargetLocationValidUs to set
     */
    public void setMaxTimeLastTargetLocationValidUs(int maxTimeLastTargetLocationValidUs) {
        if (maxTimeLastTargetLocationValidUs < minTargetPointIntervalUs) {
            maxTimeLastTargetLocationValidUs = minTargetPointIntervalUs;
        }
        this.maxTimeLastTargetLocationValidUs = maxTimeLastTargetLocationValidUs;
        putInt("maxTimeLastTargetLocationValidUs", maxTimeLastTargetLocationValidUs);

    }

    /**
     * Returns true if locations are specified already
     *
     * @return true if there are locations specified
     */
    public boolean hasLocations() {
        return !targetLocations.isEmpty();
    }

    /**
     * @return the targetLocation
     */
    public TargetLocation getTargetLocation() {
        return targetLocation;
    }

    private class TargetLocationComparator implements Comparator<TargetLocation> {

        @Override
        public int compare(TargetLocation o1, TargetLocation o2) {
            return Integer.valueOf(o1.frameNumber).compareTo(Integer.valueOf(o2.frameNumber));
        }

    }

    public class TargetLocation {

        int timestamp;
        int frameNumber;
        Point location;

        public TargetLocation(int frameNumber, int timestamp, Point location) {
            this.frameNumber = frameNumber;
            this.timestamp = timestamp;
            this.location = location != null ? new Point(location) : null;
        }

        private void draw(GLAutoDrawable drawable, GL2 gl) {

            if (getTargetLocation().location == null) {
                textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
                textRenderer.draw("Target not visible", chip.getSizeX() / 2, chip.getSizeY() / 2);
                textRenderer.endRendering();
                return;
            }
            gl.glPushMatrix();
            gl.glTranslatef(getTargetLocation().location.x, getTargetLocation().location.y, 0f);
            gl.glColor4f(0, 1, 0, .5f);
            if (mouseQuad == null) {
                mouseQuad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
            glu.gluDisk(mouseQuad, getTargetRadius(), getTargetRadius() + 1, 32, 1);
            gl.glPopMatrix();
        }

        @Override
        public String toString() {
            return String.format("TargetLocation frameNumber=%d timestamp=%d location=%s", frameNumber, timestamp, location == null ? "null" : location.toString());
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
                    writer.write(String.format("%d %d -1 -1\n", l.frameNumber, l.timestamp));
                }
            }
            writer.close();
            log.info("wrote locations to file " + f.getAbsolutePath());
            lastFileName = f.toString();
            putString("lastFileName", lastFileName);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't save locations", JOptionPane.WARNING_MESSAGE, null);
            return;
        }
    }

    /**
     * Loads last locations. Note that this is a lengthy operation
     */
    synchronized public void loadLastLocations() {
        if (lastFileName == null) {
            return;
        }
        File f = new File(lastFileName);
        if (!f.exists() || !f.isFile()) {
            return;
        }
        loadLocations(f);
    }

    synchronized private void loadLocations(File f) {
        log.info("loading " + f);
        try {
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
            targetLocations.clear();
            minSampleTimestamp = Integer.MAX_VALUE;
            maxSampleTimestamp = Integer.MIN_VALUE;
            try {
                BufferedReader reader = new BufferedReader(new FileReader(f));
                String s = reader.readLine();
                StringBuilder sb = new StringBuilder();
                while ((s != null) && s.startsWith("#")) {
                    sb.append(s + "\n");
                    s = reader.readLine();
                }
                log.info("header lines on " + f.getAbsolutePath() + " are\n" + sb.toString());
                while (s != null) {
                    Scanner scanner = new Scanner(s);
                    try {
                        TargetLocation targetLocation = new TargetLocation(scanner.nextInt(), scanner.nextInt(), new Point(scanner.nextInt(), scanner.nextInt())); // read target location
                        if (targetLocation.location.x == -1 && targetLocation.location.y == -1) {
                            targetLocation.location = null;
                        }
                        targetLocations.put(targetLocation.timestamp, targetLocation);
                        updateLabeledFractions(targetLocation);
                        if (targetLocation != null) {
                            if (targetLocation.timestamp > maxSampleTimestamp) {
                                maxSampleTimestamp = targetLocation.timestamp;
                            }
                            if (targetLocation.timestamp < minSampleTimestamp) {
                                minSampleTimestamp = targetLocation.timestamp;
                            }
                        }
                    } catch (InputMismatchException ex2) {
                        throw new IOException("couldn't parse file " + f == null ? "null" : f.toString() + ", got InputMismatchException on line: " + s);
                    }
                    s = reader.readLine();
                }
                log.info("done loading " + f);
            } catch (FileNotFoundException ex) {
                JOptionPane.showMessageDialog(glCanvas, "couldn't find file " + f == null ? "null" : f.toString() + ": got exception " + ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(glCanvas, "IOException with file " + f == null ? "null" : f.toString() + ": got exception " + ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void updateLabeledFractions(TargetLocation targetLocation) {
        if (targetLocation == null) {
            return;
        }
        if (inputStreamDuration == 0) {
            return;
        }
        int frac = (int) Math.floor(N_FRACTIONS * (float) (targetLocation.timestamp - firstInputStreamTimestamp) / inputStreamDuration);
        labeledFractions[frac] = true;
    }

    private void fixLabeledFraction() {
        if (targetLocations == null || targetLocations.isEmpty()) {
            Arrays.fill(labeledFractions, false);
            return;
        }
        for (TargetLocation t : targetLocations.values()) {
            updateLabeledFractions(t);
        }
    }

    /**
     * @return the showLabeledFraction
     */
    public boolean isShowLabeledFraction() {
        return showLabeledFraction;
    }

    /**
     * @param showLabeledFraction the showLabeledFraction to set
     */
    public void setShowLabeledFraction(boolean showLabeledFraction) {
        this.showLabeledFraction = showLabeledFraction;
        putBoolean("showLabeledFraction", showLabeledFraction);
    }

    /**
     * @return the showHelpText
     */
    public boolean isShowHelpText() {
        return showHelpText;
    }

    /**
     * @param showHelpText the showHelpText to set
     */
    public void setShowHelpText(boolean showHelpText) {
        this.showHelpText = showHelpText;
        putBoolean("showHelpText", showHelpText);
    }

}
