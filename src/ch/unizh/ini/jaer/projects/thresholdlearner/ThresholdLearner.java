/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.thresholdlearner;

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;

/**
 * Learns meaning of events from a temporal contrast silicon retina like the DVS, using
 * spatio-temporal events over long time periods.
 * @author tao (robert) zhao (HKUST), tobi delbruck, capo caccia 2011
 */
@Description("Learns the meaning of temporal contrast events from a DVS")
public class ThresholdLearner extends EventFilter2D implements Observer {

    int[][][] lastEventTimes;
    VariableThresholdRenderer thresholdRenderer;
    private ThresholdMap map;
    final float LEARNING_RATE_SCALE=1e-6f;
    private float learningRate=getFloat("learningRate",1);
    private int neighborhoodSizePixels=getInt("neighborhoodSizePixels",8);
    private float minThreshold=getFloat("minThreshold",.5f);
    private float maxThreshold=getFloat("maxThreshold", 2f);
    private final String DEFAULT_MAP="thresholds_n.txt";
    
    public ThresholdLearner(AEChip chip) {
        super(chip);
        setPropertyTooltip("learningRate","rate that threhsolds are updated");
        setPropertyTooltip("learningRate","square neighborhood dimension in pixels");
        setPropertyTooltip("minThreshold","minimum allowed pixel threshold in arbitrary units");
        setPropertyTooltip("maxThreshold","maximum allowed pixel threshold in arbitrary units");

    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(TemporalContrastEvent.class);
        checkArrays();
        if (in.getEventClass() != PolarityEvent.class) {
            return in;
        }
        OutputEventIterator outItr = out.outputIterator();
        final int ntypes = 2;
        final int sx = chip.getSizeX();
        for (BasicEvent be : in) {
            PolarityEvent pe = (PolarityEvent) be;
//            lastEventTimes[pe.x][pe.y][pe.type] = pe.timestamp;
            TemporalContrastEvent tce = (TemporalContrastEvent) outItr.nextOutput();
            tce.copyFrom(pe);
            tce.contrast = map.getThreshold(pe.x, pe.y, pe.type);
        }
        return out;
    }

    @Override
    public void resetFilter() {
        if(map==null) return;
        map.reset();
        chip.getAeViewer().repaint();
    }

    @Override
    public void initFilter() {
    }

    @Override
    synchronized public void update(Observable o, Object arg) {
        if (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY || arg == AEChip.EVENT_NUM_CELL_TYPES) {
            checkArrays();
        }

    }

    private void checkArrays() {
        int n = chip.getNumCells();
        map.checkMapAllocation();
    }
    
    AEChipRenderer oldRenderer;

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            checkArrays();
            if (thresholdRenderer == null) {
                oldRenderer=chip.getRenderer();
                thresholdRenderer = new VariableThresholdRenderer(chip);
                map = thresholdRenderer.thresholdMap;
            }
            chip.setRenderer(thresholdRenderer);
            chip.getAeViewer().interruptViewloop();
        } else {
            if (oldRenderer != null) {
                chip.setRenderer(oldRenderer);
           chip.getAeViewer().interruptViewloop();
            }
        }
    }
    
    public void doLoadThresholds(){
       final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(getLastFilePrefs());  // defaults to startup runtime folder
        fc.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory()
                        || f.getName().toLowerCase().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "text files";
            }
        });

        final int[] state = new int[1];
        state[0] = Integer.MIN_VALUE;

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setSelectedFile(new File(getString("lastFile", System.getProperty("user.dir"))));
                state[0] = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
                if (state[0] == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    putLastFilePrefs(file);
                    try {
                        loadMapFromFile(file);
                    } catch (Exception e) {
                        log.warning(e.toString());
                        JOptionPane.showMessageDialog(fc, "Couldn't load thresholds from file " + file + ", caught exception " + e, "Threshold file error", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    log.info("Cancelled saving!");
                }
            }

            private void loadMapFromFile(File file) throws IOException{
                String s=file.getPath(); // full path including folder and ending with filename
                if(!(s.endsWith("_n.txt") || s.endsWith("_p.txt") )){
                    throw new IOException("choose either *_n.txt or *_p.txt file");
                }
                String baseName=s.substring(0, s.length()-"_n.txt".length());
                log.info("using base path "+baseName);
                if(map==null) map=new ThresholdMap(chip);
                map.load(new File(baseName));
            }
        });
    }

       private File getLastFilePrefs() {
        String pack = this.getClass().getPackage().getName();
        String path = "src" + File.separator + pack.replace(".", File.separator) + File.separator + DEFAULT_MAP;
//        return new File(path);
        return new File(getString("lastFile", path));
    }

    private void putLastFilePrefs(File file) {
        putString("lastFile", file.toString());
    }

    /**
     * @return the learningRate
     */
    public float getLearningRate() {
        return learningRate;
    }

    /**
     * @param learningRate the learningRate to set
     */
    public void setLearningRate(float learningRate) {
        this.learningRate = learningRate;
        putFloat("learningRate",learningRate);
    }

    /**
     * @return the neighborhoodSizePixels
     */
    public int getNeighborhoodSizePixels() {
        return neighborhoodSizePixels;
    }

    /**
     * @param neighborhoodSizePixels the neighborhoodSizePixels to set
     */
    public void setNeighborhoodSizePixels(int neighborhoodSizePixels) {
        if(neighborhoodSizePixels<2)neighborhoodSizePixels=2; else if(neighborhoodSizePixels>10) neighborhoodSizePixels=10; 
        this.neighborhoodSizePixels = neighborhoodSizePixels;
        putInt("neighborhoodSizePixels",neighborhoodSizePixels);
    }

    /**
     * @return the minThreshold
     */
    public float getMinThreshold() {
        return minThreshold;
    }

    /**
     * @param minThreshold the minThreshold to set
     */
    public void setMinThreshold(float minThreshold) {
        if(minThreshold<0)minThreshold=0; else if(minThreshold>1)minThreshold=1;
        this.minThreshold = minThreshold;
        putFloat("minThreshold",minThreshold);
    }

    /**
     * @return the maxThreshold
     */
    public float getMaxThreshold() {
        return maxThreshold;
    }

    /**
     * @param maxThreshold the maxThreshold to set
     */
    public void setMaxThreshold(float maxThreshold) {
        if(maxThreshold<1) maxThreshold=1; else if(maxThreshold>10) maxThreshold=10;
        this.maxThreshold = maxThreshold;
        putFloat("maxThreshold",maxThreshold);
    }
}
