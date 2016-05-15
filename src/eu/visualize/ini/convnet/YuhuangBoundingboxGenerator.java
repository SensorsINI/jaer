/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
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
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Displays tracking dataset bounding boxes as described in the 2016 Frontiers
 * in Neuromorphic Engineering data report paper
 *
 * @author tobi delbruck, yuhaung hu, hongie liu
 */
@Description("Displays tracking dataset bounding boxes as described in the Frontiers paper")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class YuhuangBoundingboxGenerator extends EventFilter2D implements FrameAnnotater, Observer {

    private FileReader fileReader = null;
    private String gtFilename = getString("GTFilename", "gt.txt");
    private TreeMap<Integer, BoundingBox> boundingBoxes = new TreeMap();
    private int lastTs = 0;
    private ArrayList<BoundingBox> currentBoundingBoxes = new ArrayList(10);

    public YuhuangBoundingboxGenerator(AEChip chip) {
        super(chip);
//        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";

        setPropertyTooltip("loadLocations", "loads locations from a file");
        setPropertyTooltip("clearLocations", "clears all existing targets");
        setPropertyTooltip("loadGroundTruthFromTXT", "Load an TXT file containing grond truth");

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
        try {
            this.loadBoundingBoxes(c.getSelectedFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't read bounding box file" + ex + ". See console for logging.", "Bad bounding box file", JOptionPane.WARNING_MESSAGE);
        }

    }

    synchronized public void loadBoundingBoxes(File f) throws IOException {
        Scanner gtReader = new Scanner(f);
        int lineNumber = 0;
        boundingBoxes.clear();
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
                    bb.x[i] = (float) Double.parseDouble(parts[2 * i + 1]);
                    bb.y[i] = (float) Double.parseDouble(parts[2 * i + 2]);
                }
                boundingBoxes.put(bb.timestamp, bb);
            } catch (NumberFormatException e) {
                log.warning("caught " + e.toString() + " on line " + lineNumber);
                break;
            }
        }
        log.info("read " + boundingBoxes.size() + " bounding boxes from " + gtFilename);
        gtReader.close();

    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        currentBoundingBoxes.clear();
        Entry<Integer, BoundingBox> e = boundingBoxes.lowerEntry(lastTs);
        if (e != null && e.getValue() != null) {
            currentBoundingBoxes.add(e.getValue());
        }
        for (BasicEvent ev : in) {
            lastTs = ev.timestamp;
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
        for (BoundingBox b : currentBoundingBoxes) {
            b.draw(gl);
        }
    }

    @Override
    public void update(Observable o, Object arg) {

    }

    public class BoundingBox {

        final int N = 4;
        float[] x = new float[N], y = new float[N]; // 4 points for corners of polygon
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

    public class BoundingBoxTimeComparator implements Comparator<BoundingBox> {

        @Override
        public int compare(BoundingBox t, BoundingBox t1) {
            return t.timestamp - t1.timestamp;
        }

    }

}
