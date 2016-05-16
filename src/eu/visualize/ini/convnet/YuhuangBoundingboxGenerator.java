/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.Scanner;
import java.util.TreeMap;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Displays tracking dataset bounding boxes as described in the 2016 Frontiers
 * in Neuromorphic Engineering data report paper "DVS Benchmark Datasets for Object Tracking, Action Recognition, and Object Recognition"
 *
 * @author tobi delbruck, yuhaung hu, hongie liu
 */
@Description("Displays tracking dataset bounding boxes as described in the Frontiers paper")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class YuhuangBoundingboxGenerator extends EventFilter2D implements FrameAnnotater, Observer, PropertyChangeListener {

    private FileReader fileReader = null;
    private String gtFilename = getString("GTFilename", "gt.txt"), gtFilenameShort=null;
    private TreeMap<Integer, BoundingBox> boundingBoxes = new TreeMap();
    private int lastTs = 0;
    private ArrayList<BoundingBox> currentBoundingBoxes = new ArrayList(10);
    private boolean addedViewerPropertyChangeListener = false; // TODO promote these to base EventFilter class
    private boolean showFilename = getBoolean("showFilename",true); 

    public YuhuangBoundingboxGenerator(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadGroundTruthFromTXT", "Load an TXT file containing grond truth");
        setPropertyTooltip("clearGroundTruth", "Clears list of bounding boxes");
        setPropertyTooltip("showFilename", "shows the ground truth filename");
    }

    synchronized public void doLoadGroundTruthFromTXT() {
        JFileChooser c = new JFileChooser(gtFilename);
        c.setDialogTitle("Choose ground truth bounding box file");
        FileFilter filt = new FileNameExtensionFilter("TXT File", "txt");
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(new File(gtFilename));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        gtFilename = c.getSelectedFile().toString();
        putString("GTFilename", gtFilename);
        gtFilenameShort=gtFilename.substring(0, 5)+"..."+gtFilename.substring(gtFilename.lastIndexOf(File.separator));
        try {
            this.loadBoundingBoxes(c.getSelectedFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't read bounding box file" + ex + ". See console for logging.", "Bad bounding box file", JOptionPane.WARNING_MESSAGE);
        }

    }
    
    synchronized public void doClearGroundTruth(){
        boundingBoxes.clear();
    }

    synchronized public void loadBoundingBoxes(File f) throws IOException {
        Scanner gtReader = new Scanner(f);
        int lineNumber = 0;
        boundingBoxes.clear();
        int sx = chip.getSizeX();
        int sy = chip.getSizeY();
        while (gtReader.hasNextLine()) {
            String line = "";
            line = gtReader.nextLine();
            lineNumber++;
            if (line.startsWith("#") || line.isEmpty()) {
                continue; // comment lines
            }
            String[] parts = line.split(",");
            // not documented yet, but order is
            // timestamp in us, x,y for 4 corners of polygon in DVS coordinates (in practice a rectangle)
            BoundingBox bb = new BoundingBox();
            try {

                bb.timestamp = (int) Double.parseDouble(parts[0]);
                for (int i = 0; i < bb.N; i++) {
                    bb.x[i] = sx - (float) Double.parseDouble(parts[2 * i + 1]);
                    bb.y[i] = sy - (float) Double.parseDouble(parts[2 * i + 2]);
                }
                boundingBoxes.put(bb.timestamp, bb);
            } catch (NumberFormatException e) {
                log.warning("caught " + e.toString() + " on line " + lineNumber);
                break;
            }
        }
        log.info("read " + boundingBoxes.size() + " bounding boxes from " + gtFilename + " and flipped x- and y-coordinates to match jAER");
        gtReader.close();

    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        maybeAddListeners(chip);
        currentBoundingBoxes.clear();
        lastTs = in.getFirstTimestamp();
        BoundingBox next = null;
        Entry<Integer, BoundingBox> entry = boundingBoxes.lowerEntry(lastTs); // gets BB that is last in list and still with lower timestamp than last timestamp
        // we do this more expensive search in case user has scrolled the file or rewound
        if (entry != null && entry.getValue() != null) {
            currentBoundingBoxes.add(entry.getValue());
            entry = boundingBoxes.higherEntry(entry.getKey());
            if (entry != null) {
                next = entry.getValue();
            }
        }
        for (BasicEvent ev : in) {
            lastTs = ev.timestamp;  // gets next in list, then add to currentBoundingBoxes when timestamp reaches that value
            if (next != null && ev.timestamp > next.timestamp) {
                currentBoundingBoxes.add(next);
                entry = boundingBoxes.higherEntry(next.timestamp);
                if (entry != null) {
                    next = entry.getValue();
                }
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
        currentBoundingBoxes.clear();
    }

    @Override
    public void initFilter() {
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showFilename && !boundingBoxes.isEmpty() && gtFilename != null) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            MultilineAnnotationTextRenderer.renderMultilineString(String.format("BoundingBoxes from %s", gtFilenameShort));
        }
        for (BoundingBox b : currentBoundingBoxes) {
            b.draw(gl);
        }
    }

    @Override
    public void update(Observable o, Object arg) {

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (this.filterEnabled) {
            switch (evt.getPropertyName()) {
                case AEViewer.EVENT_TIMESTAMPS_RESET:
                    resetFilter();
                    break;
                case AEViewer.EVENT_FILEOPEN:
                    AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
                    AEFileInputStream in = (player.getAEInputStream());
                    in.getSupport().addPropertyChangeListener(this);
                    // Treat FileOpen same as a rewind
                    resetFilter();
                    break;
            }
        }
    }

    public final void maybeAddListeners(AEChip chip) {
        if (chip.getAeViewer() != null) {
            if (!addedViewerPropertyChangeListener) {
                chip.getAeViewer().addPropertyChangeListener(this);
                addedViewerPropertyChangeListener = true;
            }

        }
    }

    /**
     * @return the showFilename
     */
    public boolean isShowFilename() {
        return showFilename;
    }

    /**
     * @param showFilename the showFilename to set
     */
    public void setShowFilename(boolean showFilename) {
        this.showFilename = showFilename;
        putBoolean("showFilename",showFilename);
    }

    /**
     * Returns a TreeMap of all the ground truth bounding boxes. 
     * The map keys are the timestamp in us and the entries are the BoundingBox's.
     * @return the boundingBoxes
     */
    public TreeMap<Integer, BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    /**
     * Returns ArrayList of currently valid BoundingBox for the last packet.
     * @return the currentBoundingBoxes
     */
    public ArrayList<BoundingBox> getCurrentBoundingBoxes() {
        return currentBoundingBoxes;
    }

    /** A single bounding box */
    public class BoundingBox {

        /** Number of vertices */
        public final int N = 4;
        /** List of X and Y corner coordinates in DVS pixel space */
        float[] x = new float[N], y = new float[N]; // 4 points for corners of polygon
        /** Timestamp of bounding box in us */
        int timestamp;

        public void draw(GL2 gl) {
            gl.glLineWidth(4);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINE_LOOP);
            for (int i = 0; i < N; i++) {
                gl.glVertex2f(x[i], y[i]);
            }
            gl.glEnd();
        }
    }

}
