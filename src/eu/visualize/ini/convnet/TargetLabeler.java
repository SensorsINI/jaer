/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TreeMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import java.awt.event.MouseWheelEvent;
import java.io.LineNumberReader;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

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
    private boolean altPressed = false;
    private Point mousePoint = null;
    final float labelRadius = 5f;
    private GLUquadric mouseQuad = null;
    private TreeMap<Integer, SimultaneouTargetLocations> targetLocations = new TreeMap();
    private TargetLocation targetLocation = null;
    private DavisChip apsDvsChip = null;
    private int lastFrameNumber = -1;
    private int lastTimestamp = Integer.MIN_VALUE;
    private int currentFrameNumber = -1;
    private final String LAST_FOLDER_KEY = "lastFolder";
    TextRenderer textRenderer = null;
    private int minTargetPointIntervalUs = getInt("minTargetPointIntervalUs", 2000);
    private int targetRadius = getInt("targetRadius", 10);
    private int maxTimeLastTargetLocationValidUs = getInt("maxTimeLastTargetLocationValidUs", 50000);
    private int minSampleTimestamp = Integer.MAX_VALUE, maxSampleTimestamp = Integer.MIN_VALUE;
    private final int N_FRACTIONS = 1000;
    private boolean[] labeledFractions = new boolean[N_FRACTIONS];  // to annotate graphically what has been labeled so far in event stream
    private boolean[] targetPresentInFractions = new boolean[N_FRACTIONS];  // to annotate graphically what has been labeled so far in event stream
    private boolean showLabeledFraction = getBoolean("showLabeledFraction", true);
    private boolean showHelpText = getBoolean("showHelpText", true);
//    protected int maxTargets = getInt("maxTargets", 8);
    protected int currentTargetTypeID = getInt("currentTargetTypeID", 0);
    private ArrayList<TargetLocation> currentTargets = new ArrayList(10); // currently valid targets
    protected boolean eraseSamplesEnabled = false;
    private HashMap<String, String> mapDataFilenameToTargetFilename = new HashMap();

    private boolean propertyChangeListenerAdded = false;
    private String DEFAULT_FILENAME = "locations.txt";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected boolean showStatistics = getBoolean("showStatistics", true);

    private String lastDataFilename = null;
    private boolean locationsLoadedFromFile = false;

    // file statistics
    private long firstInputStreamTimestamp = 0, lastInputStreamTimestamp = 0, inputStreamDuration = 0;
    private long filePositionEvents = 0, fileLengthEvents = 0;
    private int filePositionTimestamp = 0;
    private boolean warnSave = true;
    private int mouseWheelRoll=0;

    public TargetLabeler(AEChip chip) {
        super(chip);
        if (chip instanceof DavisChip) {
            apsDvsChip = ((DavisChip) chip);
        }
        setPropertyTooltip("minTargetPointIntervalUs", "minimum interval between target positions in the database in us");
        setPropertyTooltip("targetRadius", "drawn radius of target in pixels");
        setPropertyTooltip("maxTimeLastTargetLocationValidUs", "this time after last sample, the data is shown as not yet been labeled. This time specifies how long a specified target location is valid after its last specified location.");
        setPropertyTooltip("saveLocations", "saves target locations");
        setPropertyTooltip("saveLocationsAs", "show file dialog to save target locations to a new file");
        setPropertyTooltip("loadLocations", "loads locations from a file");
        setPropertyTooltip("clearLocations", "clears all existing targets");
        setPropertyTooltip("resampleLabeling", "resamples the existing labeling to fill in null locations for unlabeled parts and fills in copies of latest location between samples, to specified minTargetPointIntervalUs");
        setPropertyTooltip("showLabeledFraction", "shows labeled part of input by a bar with red=unlabeled, green=labeled, blue=current position");
        setPropertyTooltip("showHelpText", "shows help text on screen. Uncheck to hide");
        setPropertyTooltip("showStatistics", "shows statistics");
//        setPropertyTooltip("maxTargets", "maximum number of simultaneous targets to label");
        setPropertyTooltip("currentTargetTypeID", "ID code of current target to be labeled, e.g., 0=dog, 1=cat, etc. User must keep track of the mapping from ID codes to target classes.");
        setPropertyTooltip("eraseSamplesEnabled", "Use this mode to erase all samples up to minTargetPointIntervalUs before current time.");
        setPropertyTooltip("relabelRadiusEnabled", "Use this mode to resize the target bounding box to the targetRadius using the mouse wheel to change the size");
        Arrays.fill(labeledFractions, false);
        Arrays.fill(targetPresentInFractions, false);
        try {
            byte[] bytes = getPrefs().getByteArray("TargetLabeler.hashmap", null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                mapDataFilenameToTargetFilename = (HashMap<String, String>) in.readObject();
                in.close();
                log.info("loaded mapDataFilenameToTargetFilename: " + mapDataFilenameToTargetFilename.size() + " entries");
            } else {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    public void mouseWheelMoved(MouseWheelEvent mwe) {
        int roll=mwe.getWheelRotation();
        int r=getTargetRadius();
        setTargetRadius(r-roll);
    }
    
    
    
    

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {

        if (!isFilterEnabled()) {
            return;
        }
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        chipCanvas = chip.getCanvas();
        if (chipCanvas == null) {
            return;
        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            return;
        }
        glu = GLU.createGLU(gl); // TODO check if this solves problem of bad GL context in file preview
        if (isSelected()) {
            Point mp = glCanvas.getMousePosition();
            Point p = chipCanvas.getPixelFromPoint(mp);
            if (p == null) {
                return;
            }
            checkBlend(gl);
            float[] compArray = new float[4];
            gl.glColor3fv(targetTypeColors[currentTargetTypeID % targetTypeColors.length].getColorComponents(compArray), 0);
            gl.glLineWidth(3f);
            gl.glPushMatrix();
            gl.glTranslatef(p.x, p.y, 0);
            gl.glBegin(GL.GL_LINES);
            int tr=getTargetRadius();
            gl.glVertex2f(0, -tr);
            gl.glVertex2f(0, +tr);
            gl.glVertex2f(-tr, 0);
            gl.glVertex2f(+tr, 0);
            gl.glEnd();
            gl.glTranslatef(.5f, -.5f, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, -tr);
            gl.glVertex2f(0, +tr);
            gl.glVertex2f(-tr, 0);
            gl.glVertex2f(+tr, 0);
            gl.glEnd();
//            if (quad == null) {
//                quad = glu.gluNewQuadric();
//            }
//            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
//            glu.gluDisk(quad, 0, 3, 32, 1);
            gl.glPopMatrix();
        }
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
            textRenderer.setColor(1, 1, 1, 1);
        }
        MultilineAnnotationTextRenderer.setColor(Color.CYAN);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        StringBuilder sb = new StringBuilder();
        if (showHelpText) {
            sb.append("Shift+!Ctrl + mouse position: Specify no target present\nClt+Shift + mouse position: Specify currentTargetTypeID is present at mouse location\nShift+Alt: specify new radius for current targets");
            MultilineAnnotationTextRenderer.renderMultilineString(sb.toString());
        }
        if (showStatistics) {
            MultilineAnnotationTextRenderer.renderMultilineString(String.format("%d TargetLocation labels specified\nFirst label time: %.1fs, Last label time: %.1fs\nCurrent frame number: %d\nCurrent # labels within maxTimeLastTargetLocationValidUs: %d",
                    targetLocations.size(),
                    minSampleTimestamp * 1e-6f,
                    maxSampleTimestamp * 1e-6f, getCurrentFrameNumber(),
                    currentTargets.size()));
            if (shiftPressed && !ctlPressed && !altPressed) {
                MultilineAnnotationTextRenderer.renderMultilineString("Specifying no target");
            } else if (shiftPressed && ctlPressed && !altPressed) {
                MultilineAnnotationTextRenderer.renderMultilineString("Specifying target location");
            }else if (shiftPressed && altPressed && !ctlPressed) {
                MultilineAnnotationTextRenderer.renderMultilineString("Specifying new target radius");
            } else {
                MultilineAnnotationTextRenderer.renderMultilineString("Playing recorded target locations");
            }
        }
        for (TargetLocation t : currentTargets) {
            if (t.location != null) {
                t.draw(drawable, gl);
            }
        }

        // show labeled parts
        if (showLabeledFraction && (inputStreamDuration > 0)) {
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
                gl.glRectf(x - (dx / 2), y, x + (dx / 2), y + (dy * (1 + (targetPresentInFractions[i] ? 1 : 0))));
//                gl.glRectf(x-dx/2, y, x + dx/2, y + dy * (1 + (currentTargets.size())));
                x += dx;
            }
            float curPosFrac = ((float) (filePositionTimestamp - firstInputStreamTimestamp) / inputStreamDuration);
            x = curPosFrac * chip.getSizeX();
            y = y + dy;
            gl.glColor3f(1, 1, 1);
            gl.glRectf(x - (dx * 6), y - (dy * 2), x, y + dy * 2);
        }

    }

    synchronized public void doClearLocations() {
        targetLocations.clear();
        minSampleTimestamp = Integer.MAX_VALUE;
        maxSampleTimestamp = Integer.MIN_VALUE;
        Arrays.fill(labeledFractions, false);
        Arrays.fill(targetPresentInFractions, false);
        currentTargets.clear();
        locationsLoadedFromFile = false;
    }

    synchronized public void doSaveLocationsAs() {
        String fn = mapDataFilenameToTargetFilename.get(lastDataFilename);
        if (fn == null) {
            fn = lastFileName == null ? DEFAULT_FILENAME : lastFileName;
        }
        JFileChooser c = new JFileChooser(fn);
        c.setSelectedFile(new File(fn));
        int ret = c.showSaveDialog(glCanvas);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        // end filename with -targets.txt
        File f = c.getSelectedFile();
        String s = f.getPath();
        if (!s.endsWith("-targets.txt")) {
            int idxdot = s.lastIndexOf('.');
            if (idxdot > 0) {
                s = s.substring(0, idxdot);
            }
            s = s + "-targets.txt";
            f = new File(s);
        }
        if (f.exists()) {
            int r = JOptionPane.showConfirmDialog(glCanvas, "File " + f.toString() + " already exists, overwrite it?");
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        lastFileName = f.toString();
        putString("lastFileName", lastFileName);

        saveLocations(f);
        warnSave = false;
    }

    public void doResampleLabeling() {
        if (targetLocations == null) {
            log.warning("null targetLocations - nothing to resample");
            return;
        }
        if (chip.getAeViewer().getAePlayer().getAEInputStream() == null) {
            log.warning("cannot label unless a file is being played back");
            return;
        }
        if (targetLocations.size() == 0) {
            log.warning("no locations labeled  - will label entire recording with no visible targets");
        }
        Map.Entry<Integer, SimultaneouTargetLocations> prevTargets = targetLocations.firstEntry();
        TreeMap<Integer, SimultaneouTargetLocations> newTargets = new TreeMap();
        if (prevTargets != null) {
            for (Map.Entry<Integer, SimultaneouTargetLocations> nextTargets : targetLocations.entrySet()) { // for each existing set of targets by timestamp key list
                // if no label at all for minTargetPointIntervalUs, then add copy of last labels up to maxTimeLastTargetLocationValidUs,
                // then add null labels after that
                if ((nextTargets.getKey() - prevTargets.getKey()) > minTargetPointIntervalUs) {
                    int n = (nextTargets.getKey() - prevTargets.getKey()) / minTargetPointIntervalUs;  // add this many total
                    int tCopy = prevTargets.getKey() + maxTimeLastTargetLocationValidUs;  // add this many total
                    for (int i = 0; i < n; i++) {
                        int ts = prevTargets.getKey() + ((i + 1) * minTargetPointIntervalUs);
                        newTargets.put(ts, copyLocationsToNewTs(prevTargets.getValue(), ts, ts <= tCopy));
                    }
                }
                prevTargets = nextTargets;
            }
        }
        // handle time after last label
        AEFileInputStream fileInputStream = chip.getAeViewer().getAePlayer().getAEInputStream();
        int tFirstLabel = prevTargets != null ? prevTargets.getKey() : fileInputStream.getFirstTimestamp();
        int tLastLabel = fileInputStream.getLastTimestamp(); // TODO handle wrapped timestamp during recording
        int frameNumber = prevTargets != null ? prevTargets.getValue().get(0).frameNumber : -1;
        int n = (tLastLabel - tFirstLabel) / minTargetPointIntervalUs;  // add this many total
        for (int i = 0; i < n; i++) {
            SimultaneouTargetLocations s = new SimultaneouTargetLocations();
            int ts = tFirstLabel + ((i + 1) * minTargetPointIntervalUs);
            TargetLocation addedNullLabel = new TargetLocation(frameNumber, ts, null, currentTargetTypeID, -1, -1);
            s.add(addedNullLabel);
            newTargets.put(ts, s);
        }
        targetLocations.putAll(newTargets);
        fixLabeledFraction();

    }

    private SimultaneouTargetLocations copyLocationsToNewTs(SimultaneouTargetLocations src, int ts, boolean useOriginalLocation) {
        SimultaneouTargetLocations s = new SimultaneouTargetLocations();
        for (TargetLocation t : src) {
            TargetLocation tNew = new TargetLocation(t.frameNumber, ts, useOriginalLocation ? t.location : null, t.targetClassID, t.width, t.height);
            s.add(tNew);
        }
        return s;
    }

    synchronized public void doSaveLocations() {
        if (warnSave) {
            int ret = JOptionPane.showConfirmDialog(chip.getAeViewer().getFilterFrame(), "Really overwrite " + lastFileName + " ?", "Overwrite warning", JOptionPane.WARNING_MESSAGE);
            if (ret != JOptionPane.YES_OPTION) {
                log.info("save canceled");
                return;
            }
        }
        File f = new File(lastFileName);
        saveLocations(new File(lastFileName));
    }

    synchronized public void doLoadLocations() {
        
        if (lastFileName == null) {
            lastFileName = mapDataFilenameToTargetFilename.get(lastDataFilename);
        }
        if (lastFileName == null) {
            lastFileName = DEFAULT_FILENAME;
        }
        if ((lastFileName != null) && lastFileName.equals(DEFAULT_FILENAME)) {
            File f = chip.getAeViewer().getRecentFiles().getMostRecentFile();
            if (f == null) {
                lastFileName = DEFAULT_FILENAME;
            } else {
                lastFileName = f.getPath();
            }
        }
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "Text target label files";
            }
        });
        c.setMultiSelectionEnabled(false);
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showOpenDialog(glCanvas);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        loadLocations(new File(lastFileName));
    }

    private TargetLocation lastAddedSample = null;

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
        int nCurrentTargets = currentTargets.size();
//        currentTargets.clear();
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

                if (((long) e.timestamp - (long) lastTimestamp) >= minTargetPointIntervalUs) {

                    lastTimestamp = e.timestamp;
                    // find next saved target location that is just before this time (lowerEntry)
                    if (shiftPressed && ctlPressed && !altPressed && (mousePoint != null)) { // specify (additional) target present
                        // add a labeled location sample
                        addSample(getCurrentFrameNumber(), e.timestamp, mousePoint, targetRadius*2, targetRadius*2, currentTargetTypeID, false);
                    } else if (shiftPressed && !ctlPressed && !altPressed) { // specify no target present now but mark recording as reviewed
                        addSample(getCurrentFrameNumber(), e.timestamp, null, targetRadius*2, targetRadius*2, currentTargetTypeID, false);
                    }
                    updateCurrentlyDisplayedTargets(e);
                    if(shiftPressed && altPressed){
                        for(TargetLocation t:currentTargets){
                            t.width=targetRadius*2;
                            t.height=targetRadius*2;
                        }
                    }
                }
                if (e.timestamp < lastTimestamp) {
                    lastTimestamp = e.timestamp;
                }

            }
        }

        //prune list of current targets to their valid lifetime, and remove leftover targets in the future
        ArrayList<TargetLocation> removeList = new ArrayList();
        for (TargetLocation t : currentTargets) {
            if (((t.timestamp + maxTimeLastTargetLocationValidUs) < in.getLastTimestamp()) || (t.timestamp > in.getLastTimestamp())) {
                removeList.add(t);
            }
        }
        currentTargets.removeAll(removeList);
        if (currentTargets.size() != nCurrentTargets) {
            fixLabeledFraction();
        }
        return in;
    }

    private void updateCurrentlyDisplayedTargets(BasicEvent e) {
        // if at least minTargetPointIntervalUs has passed by maybe add new labels
        Map.Entry<Integer, SimultaneouTargetLocations> mostRecentTargetsBeforeThisEvent = targetLocations.lowerEntry(e.timestamp);
        if (mostRecentTargetsBeforeThisEvent != null) {
            for (TargetLocation t : mostRecentTargetsBeforeThisEvent.getValue()) {
                if ((t == null) || ((t != null) && ((e.timestamp - t.timestamp) > maxTimeLastTargetLocationValidUs))) {
                    targetLocation = null;
                } else if (targetLocation != t) {
                    targetLocation = t;
                    currentTargets.add(targetLocation);
                    markDataHasTarget(targetLocation.timestamp, targetLocation.location != null);
                }
            }
        }
    }

    private void maybeEraseSamplesBefore(int timestamp) {
        if (!isEraseSamplesEnabled()) {
            return;
        }

        Map.Entry<Integer, SimultaneouTargetLocations> nextBack = targetLocations.floorEntry(timestamp);
        while (nextBack != null && nextBack.getKey() > timestamp - minTargetPointIntervalUs) {
            if (lastAddedSample != null && lastAddedSample.timestamp == nextBack.getKey()) {
                log.info("not erasing " + nextBack.getKey() + " because we just added it");
                return;
            }
            targetLocations.remove(nextBack.getKey());
//            log.info("removed " + nextBack.getKey());
            nextBack = targetLocations.lowerEntry(nextBack.getKey());
        }
        fixLabeledFraction();
    }

    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes); // register/deregister mouse listeners
        if (yes) {
            glCanvas.removeKeyListener(this); // only add ourselves once in case we were added on startup
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
        // forward space and b (toggle direction of playback) to AEPlayer
        int k = ke.getKeyChar();
//        log.info("keyChar=" + k + " keyEvent=" + ke.toString());
        if (shiftPressed || ctlPressed) { // only forward to AEViewer if we are blocking ordinary input to AEViewer by labeling
            switch (k) {
                case KeyEvent.VK_SPACE:
                    chip.getAeViewer().setPaused(!chip.getAeViewer().isPaused());
                    break;
                case KeyEvent.VK_B:
                case 2:
                    chip.getAeViewer().getAePlayer().toggleDirection();
                    break;
                case 'F':
                case 6:
                    chip.getAeViewer().getAePlayer().speedUp();
                    break;
                case 'S':
                case 19:
                    chip.getAeViewer().getAePlayer().slowDown();
                    break;
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent ke) {
        int k = ke.getKeyCode();
        if (k == KeyEvent.VK_SHIFT) {
            shiftPressed = true;
        } else if (k == KeyEvent.VK_CONTROL) {
            ctlPressed = true;
        }else if (k == KeyEvent.VK_ALT) {
            altPressed = true;
        } else if (k == KeyEvent.VK_E) {
            setEraseSamplesEnabled(true);
        }
    }

    @Override
    public void keyReleased(KeyEvent ke) {
        int k = ke.getKeyCode();
        if (k == KeyEvent.VK_SHIFT) {
            shiftPressed = false;
        } else if (k == KeyEvent.VK_CONTROL) {
            ctlPressed = false;
        }else if (k == KeyEvent.VK_ALT) {
            altPressed = false;
        } else if (k == KeyEvent.VK_E) {
            setEraseSamplesEnabled(false);
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
        if(targetRadius<1)targetRadius=1;
        int old=this.targetRadius;
        this.targetRadius = targetRadius;
        putInt("targetRadius", targetRadius);
        getSupport().firePropertyChange("targetRadius", old, this.targetRadius);
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
     * Returns true if any locations are specified already. However if there are
     * no targets at all visible then also returns false.
     *
     *
     * @return true if there are locations specified
     * @see #isLocationsLoadedFromFile()
     */
    public boolean hasLocations() {
        return !targetLocations.isEmpty();
    }

    /**
     * Returns the last target location
     *
     * @return the targetLocation
     */
    public TargetLocation getTargetLocation() {
        return targetLocation;
    }

    /** Add a new label
     * 
     * @param timestamp
     * @param point null to label target not visible
     * @param fastAdd  true during file read, to speed up and avoid memory thrashing
     */
    private void addSample(int frame, int timestamp, Point point, int width, int height, int ID, boolean fastAdd) {
        if (!fastAdd) {
            maybeEraseSamplesBefore(timestamp);
        }
        TargetLocation newTargetLocation = new TargetLocation(getCurrentFrameNumber(), timestamp, point, ID, width, height);
        SimultaneouTargetLocations s = targetLocations.get(timestamp);
        if (s == null) {
            s = new SimultaneouTargetLocations();
            targetLocations.put(timestamp, s);
        }
        s.add(newTargetLocation);
        if (!fastAdd) {
            currentTargets.add(newTargetLocation);
        }
        if (newTargetLocation != null) {
            if (newTargetLocation.timestamp > maxSampleTimestamp) {
                maxSampleTimestamp = newTargetLocation.timestamp;
            }
            if (newTargetLocation.timestamp < minSampleTimestamp) {
                minSampleTimestamp = newTargetLocation.timestamp;
            }
        }
        lastAddedSample = newTargetLocation;
        if (!fastAdd) {
            markDataHasTarget(timestamp, point != null);
        }
//        log.info("added " + timestamp);
    }

    private int getFractionOfFileDuration(int timestamp) {
        if (inputStreamDuration == 0) {
            return 0;
        }
        return (int) Math.floor((N_FRACTIONS * ((float) (timestamp - firstInputStreamTimestamp))) / inputStreamDuration);
    }

    private class TargetLocationComparator implements Comparator<TargetLocation> {

        @Override
        public int compare(TargetLocation o1, TargetLocation o2) {
            return Integer.valueOf(o1.frameNumber).compareTo(Integer.valueOf(o2.frameNumber));
        }

    }

    private final Color[] targetTypeColors = {Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED};

    /**
     * List of targets simultaneously present at a particular timestamp
     */
    private class SimultaneouTargetLocations extends ArrayList<TargetLocation> {

        boolean hasTargetWithLocation() {
            for (TargetLocation t : this) {
                if (t.location != null) {
                    return true;
                }
            }
            return false;
        }
    }

    class TargetLocation {

        int timestamp;
        int frameNumber;
        Point location; // center of target location
        int targetClassID; // class of target, i.e. car, person
        int width; // dimension of target x
        int height; // y

        public TargetLocation(int frameNumber, int timestamp, Point location, int targetTypeID, int width, int height) {
            this.frameNumber = frameNumber;
            this.timestamp = timestamp;
            this.location = location != null ? new Point(location) : null;
            this.targetClassID = targetTypeID;
            this.width = width;
            this.height = height;
        }

        private void draw(GLAutoDrawable drawable, GL2 gl) {

//            if (getTargetLocation() != null && getTargetLocation().location == null) {
//                textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
//                textRenderer.draw("Target not visible", chip.getSizeX() / 2, chip.getSizeY() / 2);
//                textRenderer.endRendering();
//                return;
//            }
            gl.glPushMatrix();
            gl.glTranslatef(location.x, location.y, 0f);
            float[] compArray = new float[4];
            gl.glColor3fv(targetTypeColors[targetClassID % targetTypeColors.length].getColorComponents(compArray), 0);
//            gl.glColor4f(0, 1, 0, .5f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(-width/2, -height/2);
            gl.glVertex2f(+width/2, -height/2);
            gl.glVertex2f(+width/2, +height/2);
            gl.glVertex2f(-width/2, +height/2);
            gl.glEnd();
//            if (mouseQuad == null) {
//                mouseQuad = glu.gluNewQuadric();
//            }
//            glu.gluQuadricDrawStyle(mouseQuad, GLU.GLU_LINE);
//            //glu.gluDisk(mouseQuad, getTargetRadius(), getTargetRadius(), 32, 1);
//            int maxDim = Math.max(width, height);
//            glu.gluDisk(mouseQuad, maxDim / 2, (maxDim / 2) + 0.1, 32, 1);
            //getTargetRadius(), getTargetRadius() + 1, 32, 1);
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
            writer.write(String.format("#!TargetLocations-2.0\n"));
            writer.write(String.format("# target locations\n"));
            writer.write(String.format("# written %s\n", new Date().toString()));
//            writer.write("# maxTargets=" + maxTargets+"\n");
            writer.write(String.format("# frameNumber timestamp x y targetTypeID width height\n"));
            for (Map.Entry<Integer, SimultaneouTargetLocations> entry : targetLocations.entrySet()) {
                for (TargetLocation l : entry.getValue()) {
                    if (l.location != null) {
                        writer.write(String.format("%d %d %d %d %d %d %d\n", l.frameNumber, l.timestamp, l.location.x, l.location.y, l.targetClassID, l.width, l.height));
                    } else {
                        writer.write(String.format("%d %d -1 -1 -1 -1 -1\n", l.frameNumber, l.timestamp));
                    }
                }
            }
            writer.close();
            log.info("wrote locations to file " + f.getAbsolutePath());
            if (f.getPath() != null) {
                mapDataFilenameToTargetFilename.put(lastDataFilename, f.getPath());
            }
            try {
                // Serialize to a byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput oos = new ObjectOutputStream(bos);
                oos.writeObject(mapDataFilenameToTargetFilename);
                oos.close();
                // Get the bytes of the serialized object
                byte[] buf = bos.toByteArray();
                getPrefs().putByteArray("TargetLabeler.hashmap", buf);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        long startMs = System.currentTimeMillis();
        log.info("loading " + f);
        boolean oldEraseSamples = this.eraseSamplesEnabled;
        doClearLocations();
        try {
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
            targetLocations.clear();
            minSampleTimestamp = Integer.MAX_VALUE;
            maxSampleTimestamp = Integer.MIN_VALUE;
            try {
                LineNumberReader reader = new LineNumberReader(new FileReader(f));
                String s = reader.readLine();
                StringBuilder sb = new StringBuilder();
                while ((s != null) && s.startsWith("#")) {
                    sb.append(s + "\n");
                    s = reader.readLine();
                }
                log.info("header lines on " + f.getAbsolutePath() + " are\n" + sb.toString());
                Scanner scanner = new Scanner(reader);
                while (scanner.hasNext()) {
                    try {
                        int frame = scanner.nextInt();
                        int ts = scanner.nextInt();
                        int x = scanner.nextInt();
                        int y = scanner.nextInt();
                        int targetTypeID = 0;
                        int width = targetRadius*2;
                        int height = targetRadius*2;
                        // see if more tokens in this line
                        String mt = scanner.findInLine("\\d+");
                        if (mt != null) {
                            targetTypeID = Integer.parseInt(scanner.match().group());
                        }
                        mt = scanner.findInLine("\\d+");
                        if (mt != null) {
                            width = Integer.parseInt(scanner.match().group());
                        }
                        mt = scanner.findInLine("\\d+");
                        if (mt != null) {
                            height = Integer.parseInt(scanner.match().group());
                        }
                        Point p = null;
                        if ((x != -1) && (y != -1)) {
                            p = new Point(x, y);
                        }
                        addSample(frame, ts, p, width, height, targetTypeID, true);

                    } catch (NoSuchElementException ex2) {
                        String l=("couldn't parse file " + f) == null ? "null" : f.toString() + ", got InputMismatchException on line: " + reader.getLineNumber();
                        log.warning(l);
                        throw new IOException(l);
                    }

                }
                long endMs = System.currentTimeMillis();
                log.info("Took " + (endMs - startMs) + " ms to load " + f + " with " + targetLocations.size() + " SimultaneouTargetLocations entries");
                if (lastDataFilename != null) {
                    mapDataFilenameToTargetFilename.put(lastDataFilename, f.getPath());
                }
                this.targetLocation = null;  // null out current location
                locationsLoadedFromFile = true;
            } catch (FileNotFoundException ex) {
                JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(glCanvas, ex.toString(), "Couldn't load locations", JOptionPane.WARNING_MESSAGE, null);
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
            this.eraseSamplesEnabled = oldEraseSamples;
        }
        fixLabeledFraction();
    }

    int maxDataHasTargetWarningCount = 10;

    /**
     * marks this point in time as reviewed already
     *
     * @param timestamp
     */
    private void markDataReviewedButNoTargetPresent(int timestamp) {
        if (inputStreamDuration == 0) {
            return;
        }
        int frac = getFractionOfFileDuration(timestamp);
        labeledFractions[frac] = true;
        targetPresentInFractions[frac] = false;
    }

    private void markDataHasTarget(int timestamp, boolean visible) {
        if (inputStreamDuration == 0) {
            return;
        }
        int frac = getFractionOfFileDuration(timestamp);
        if ((frac < 0) || (frac >= labeledFractions.length)) {
            if (maxDataHasTargetWarningCount-- > 0) {
                log.warning("fraction " + frac + " is out of range " + labeledFractions.length + ", something is wrong");
            }
            if (maxDataHasTargetWarningCount == 0) {
                log.warning("suppressing futher warnings");
            }
            return;
        }
        labeledFractions[frac] = true;
        targetPresentInFractions[frac] = visible;
    }

    private void fixLabeledFraction() {
        if (chip.getAeInputStream() != null) {
            firstInputStreamTimestamp = chip.getAeInputStream().getFirstTimestamp();
            lastTimestamp = chip.getAeInputStream().getLastTimestamp();
            inputStreamDuration = chip.getAeInputStream().getDurationUs();
            fileLengthEvents = chip.getAeInputStream().size();
            if (inputStreamDuration > 0) {
                if ((targetLocations == null) || targetLocations.isEmpty()) {
                    Arrays.fill(labeledFractions, false);
                    return;
                }
                for (Map.Entry<Integer, SimultaneouTargetLocations> t : targetLocations.entrySet()) {
                    markDataHasTarget(t.getKey(), t.getValue().hasTargetWithLocation());
                }
            }
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

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); //To change body of generated methods, choose Tools | Templates.
        fixLabeledFraction();
    }

//    /**
//     * @return the maxTargets
//     */
//    public int getMaxTargets() {
//        return maxTargets;
//    }
//
//    /**
//     * @param maxTargets the maxTargets to set
//     */
//    public void setMaxTargets(int maxTargets) {
//        this.maxTargets = maxTargets;
//    }
    /**
     * @return the currentTargetTypeID
     */
    public int getCurrentTargetTypeID() {
        return currentTargetTypeID;
    }

    /**
     * @param currentTargetTypeID the currentTargetTypeID to set
     */
    public void setCurrentTargetTypeID(int currentTargetTypeID) {
//        if (currentTargetTypeID >= maxTargets) {
//            currentTargetTypeID = maxTargets;
//        }
        this.currentTargetTypeID = currentTargetTypeID;
        putInt("currentTargetTypeID", currentTargetTypeID);
    }

    /**
     * @return the eraseSamplesEnabled
     */
    public boolean isEraseSamplesEnabled() {
        return eraseSamplesEnabled;
    }

    /**
     * @param eraseSamplesEnabled the eraseSamplesEnabled to set
     */
    public void setEraseSamplesEnabled(boolean eraseSamplesEnabled) {
        boolean old = this.eraseSamplesEnabled;
        this.eraseSamplesEnabled = eraseSamplesEnabled;
        getSupport().firePropertyChange("eraseSamplesEnabled", old, this.eraseSamplesEnabled);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(!isFilterEnabled()) return;
        switch (evt.getPropertyName()) {
            case AEInputStream.EVENT_POSITION:
                filePositionEvents = (long) evt.getNewValue();
                if ((chip.getAeViewer().getAePlayer() == null) || (chip.getAeViewer().getAePlayer().getAEInputStream() == null)) {
                    log.warning("null input stream, cannot get most recent timestamp");
                    return;
                }
                filePositionTimestamp = chip.getAeViewer().getAePlayer().getAEInputStream().getMostRecentTimestamp();
                break;
            case AEInputStream.EVENT_REWIND:
            case AEInputStream.EVENT_REPOSITIONED:
//                log.info("rewind to start or mark position or reposition event " + evt.toString());
                if (evt.getNewValue() instanceof Long) {
                    long position = (long) evt.getNewValue();
                    if (chip.getAeInputStream() == null) {
                        log.warning("AE input stream is null, cannot determine timestamp after rewind");
                        return;
                    }
                    int timestamp = chip.getAeInputStream().getMostRecentTimestamp();
                    Map.Entry<Integer, SimultaneouTargetLocations> targetsBeforeRewind = targetLocations.lowerEntry(timestamp);
                    if (targetsBeforeRewind != null) {
                        currentFrameNumber = targetsBeforeRewind.getValue().get(0).frameNumber;
                        lastFrameNumber = getCurrentFrameNumber() - 1;
                        lastTimestamp = targetsBeforeRewind.getValue().get(0).timestamp;
                    } else {
                        currentFrameNumber = 0;
                        lastFrameNumber = getCurrentFrameNumber() - 1;
                        lastInputStreamTimestamp = Integer.MIN_VALUE;
                    }
                } else {
                    log.warning("couldn't determine stream position after rewind from PropertyChangeEvent " + evt.toString());
                }
                shiftPressed = false;
                ctlPressed = false; // disable labeling on rewind to prevent bad labels at start
                altPressed = false; // disable labeling on rewind to prevent bad labels at start
                if (evt.getPropertyName() == AEInputStream.EVENT_REWIND) {
                    try {
                        Thread.currentThread().sleep(1000);// time for preparing label

                    } catch (InterruptedException e) {
                    }
                }
                break;
            case AEInputStream.EVENT_INIT:
                fixLabeledFraction();
                warnSave = true;
                if (evt.getNewValue() instanceof AEFileInputStream) {
                    File f = ((AEFileInputStream) evt.getNewValue()).getFile();
                    lastDataFilename = f.getPath();
                }
                break;
        }
    }

    /**
     * @return the showStatistics
     */
    public boolean isShowStatistics() {
        return showStatistics;
    }

    /**
     * @param showStatistics the showStatistics to set
     */
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
        putBoolean("showStatistics", showStatistics);
    }

    /**
     * @return the currentFrameNumber
     */
    public int getCurrentFrameNumber() {
        return currentFrameNumber;
    }

    /**
     * False until locations are loaded from a file. Reset by clearLocations.
     *
     * @return the locationsLoadedFromFile true if data was loaded from a file
     * successfully
     */
    public boolean isLocationsLoadedFromFile() {
        return locationsLoadedFromFile;
    }

   

}
