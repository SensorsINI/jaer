/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import java.io.File;
import java.util.HashMap;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Displays bounding boxes for the tracking datasets in the Frontiers paper "DVS
 * Benchmark Datasets for Tracking, Action Recognition and Object Recognition"
 *
 * @author tobi
 */
@Description("Displays bounding boxes for the tracking datasets in the Frontiers paper \"DVS Datasets for XXX\"")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class TrackingBoundingBoxDisplay extends EventFilter2D {

    private String DEFAULT_FILENAME = "locations.txt";
    private String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    private String lastDataFilename = null;
    private HashMap<String, String> mapDataFilenameToTargetFilename = new HashMap();

    public TrackingBoundingBoxDisplay(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadLocations", "loads locations from a file");
        setPropertyTooltip("clearLocations", "clears all existing targets");
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
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        loadLocations(new File(lastFileName));
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resetFilter() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void initFilter() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void loadLocations(File file) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public class BoundingBox {

        float x1, x2, y1, y2;
        int timestamp;

        public void draw(GL gl) {

        }
    }

}
