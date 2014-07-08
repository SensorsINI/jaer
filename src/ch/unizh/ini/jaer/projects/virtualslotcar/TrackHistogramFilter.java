/*
 Created Capo Caccia 2014 to enable compter vs machine racing
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Allows accumulating a histogram of active pixels during practice rounds of a
 * computer controlled car, and then filtering out all but these events for
 * later car tracking to control the car.
 *
 * @author tobi
 */
public class TrackHistogramFilter extends EventFilter2D implements FrameAnnotater, Serializable {

    private static final long serialVersionUID = 8749822155491049760L; // tobi randomly defined
    private int[][] histogram = null;  // first dim is X, 2nd is Y
    private boolean collect = false;
    private float threshold = getFloat("threshold", 0.5f);
    private int histmax = 0;
    private static final String HISTOGRAM_FILE_NAME = "trackhistogram.dat";
    private boolean showHistogram = getBoolean("showHistogram", false);
    private int numX=0, numY=0, numPix=0;
    private int erosionSize = getInt("erosionSize", 1);
    private int totalSum; // sum of histogram values
 
    public TrackHistogramFilter(AEChip chip) {
        super(chip);
        setEnclosedFilter(new BackgroundActivityFilter(chip));
        setPropertyTooltip("collect", "set true to accumulate histogram");
        setPropertyTooltip("threshold", "threshold in accumulated events to allow events to pass through");
        setPropertyTooltip("histmax", "maximum histogram count");
        setPropertyTooltip("showHistogram", "paints histogram as white level over image");
        setPropertyTooltip("clearHistogram", "clears histogram");
        setPropertyTooltip("freezeHistogram", "freezes current histogram");
        setPropertyTooltip("saveHistogram", "saves current histogram to the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("loadHistogram", "loads histogram from the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("collectHistogram", "turns on histogram accumulation");
        setPropertyTooltip("erosionSize", "Amount in pixels to erode histogram bitmap on erode operation");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        in = getEnclosedFilter().filterPacket(in);
        checkHistogram();
        int max = getHistmax();
        for (BasicEvent e : in) {
            if (e.isSpecial()) {
                continue;
            }
            if (isCollect()) {
                histogram[e.x][e.y]++;
                totalSum++;
                if (histogram[e.x][e.y] > max) {
                    max = histogram[e.x][e.y];
                }
            } else { // filter out events that are not coming from pixels that have collected enough events
                if (histmax > 0 && histogram[e.x][e.y] < getThreshold()) {
                    e.setFilteredOut(true);
                } else {
                    e.setFilteredOut(false);
                }
            }

        }
        if (isCollect()) {
            setHistmax(max);
        }
        return in;
    }

    synchronized public void doClearHistogram() {
        if (histogram != null) {
            for (int i = 0; i < numX; i++) {
                Arrays.fill(histogram[i], 0);
            }
        }
        setHistmax(0);
        totalSum=0;
    }

    synchronized public void doCollectHistogram() {
        setCollect(true);

    }

    synchronized public void doFreezeHistogram() {
        setCollect(false);
    }

    synchronized public void doSaveHistogram() {
        if (histmax == 00 || histogram == null) {
            log.warning("no histogram to save");
            return;
        }
        try {
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = getString("lastFile", "");
            // get the last folder
//            fileChooser.setFileFilter(datFileFilter);
            fileChooser.setCurrentDirectory(new File(lastFilePath));
            int retValue = fileChooser.showOpenDialog(fileChooser);
            if (retValue == JFileChooser.CANCEL_OPTION) {
                return;
            }
            File file = fileChooser.getSelectedFile();
            saveHistogramToFile(file);
        } catch (IOException e) {
            log.warning("couldn't save histogram: " + e);
        }

    }

    public void saveHistogramToFile(File file) throws IOException, FileNotFoundException {
        log.info("Saving track data to " + file.getName());
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(histogram.length);
        oos.writeObject(histogram);
        oos.close();
        fos.close();
        log.info("histogram saved in startup path (usually host/java) to file " + file.getPath());
    }

    final synchronized public void doLoadHistogram() {
        JFileChooser fileChooser = new JFileChooser();
        String lastFilePath = getString("lastFile", "");
        // get the last folder
//            fileChooser.setFileFilter(datFileFilter);
        fileChooser.setCurrentDirectory(new File(lastFilePath));
        int retValue = fileChooser.showOpenDialog(fileChooser);
        if (retValue == JFileChooser.CANCEL_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        loadHistogramFromFile(file);

    }

    public void loadHistogramFromFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            setHistmax((Integer) ois.readObject());
            histogram = (int[][]) ois.readObject();
            ois.close();
            fis.close();
            log.info("histogram loaded from (usually host/java) file " + file.getPath() + "; histmax=" + histmax);

        } catch (Exception e) {
            log.info("couldn't load histogram from file " + file + ": " + e.toString());
        }
    }

    @Override
    synchronized public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showHistogram || histogram == null || histmax == 0) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        int numX = chip.getSizeX(), numY = chip.getSizeY();
        for (int y = 0; y < numY; y++) {
            for (int x = 0; x < numX; x++) {
                float v1 = (float) histogram[x][y];
                float v2 = v1 / histmax;
                if(v1>threshold){
                    gl.glColor4f(v2, v2, 0, 0.5f);
                }else{
                    gl.glColor4f(0, v2, v2, 0.5f);
                }
                gl.glRectf(x, y, x + 1, y + 1);
            }
        }
    }

    private AEViewer getViewer() {
        return getChip().getAeViewer();
    }

    private AEChipRenderer getRenderer() {
        return getChip().getRenderer();
    }

    private void checkHistogram() {
        if (histogram == null || numX != getChip().getSizeX() || numY != getChip().getSizeY()) {
            numX=chip.getSizeX();
            numY=chip.getSizeY();
       numPix = numX * numY;
           histogram = new int[numY][numX];
        }
    }

    /**
     * @return the collect
     */
    public boolean isCollect() {
        return collect;
    }

    /**
     * @param collect the collect to set
     */
    public void setCollect(boolean collect) {
        boolean old = this.collect;
        this.collect = collect;
        getSupport().firePropertyChange("collect", old, collect);
    }

    /**
     * @return the threshold
     */
    public float getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(float threshold) {
        this.threshold = threshold;
        putFloat("threshold", threshold);
    }

    /**
     * @return the histmax
     */
    public int getHistmax() {
        return histmax;
    }

    /**
     * @param histmax the histmax to set
     */
    public void setHistmax(int histmax) {
        int old = this.histmax;
        this.histmax = histmax;
        getSupport().firePropertyChange("histmax", old, histmax);
    }

    /**
     * @return the showHistogram
     */
    public boolean isShowHistogram() {
        return showHistogram;
    }

    /**
     * @param showHistogram the showHistogram to set
     */
    public void setShowHistogram(boolean showHistogram) {
        this.showHistogram = showHistogram;
        putBoolean("showHistogram", showHistogram);
    }
    
        // Morphological erosion of track histogram
    private boolean[][] erode() {
        boolean[][] bitmap = new boolean[numX][numY];
        int erSize = getErosionSize();
        if (erSize <= 0) {
            // Return original image
            for (int i = 0; i < numX; i++) {
                for (int j = 0; j < numY; j++) {
                    if ((histogram[i][j] * numX*numY / totalSum) > threshold) {
                        bitmap[i][j] = true;
                    } else {
                        bitmap[i][j] = false;
                    }
                }
            }
            return bitmap;
        }


        for (int i = 0; i < numY; i++) {
            for (int j = 0; j < numX; j++) {
                boolean keep = true;
                for (int k = -erSize; k <= erSize; k++) {
                    for (int l = -erSize; l <= erSize; l++) {
                        int pixY = clip(i + k, numY - 1); // limit to size-1 to avoid arrayoutofbounds exceptions
                        int pixX = clip(j + l, numX - 1);
                        if ((histogram[pixY][pixX] * numPix / totalSum) < threshold) {
                            keep = false;
                            break;
                        }
                    }
                    if (keep == false) {
                        break;
                    }
                }
                bitmap[i][j] = keep;
            }
        }

        return bitmap;
    }

      private int clip(int val, int limit) {
        if (val >= limit && limit != 0) {
            return limit;
        } else if (val < 0) {
            return 0;
        }
        return val;
    }

    /**
     * @return the erosionSize
     */
    public int getErosionSize() {
        return erosionSize;
    }

    /**
     * @param erosionSize the erosionSize to set
     */
    public void setErosionSize(int erosionSize) {
        this.erosionSize = erosionSize;
        putInt("erosionSize",erosionSize);
    }


}
