/*
 Created Capo Caccia 2014 to enable compter vs machine racing
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
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
public class TrackHistogramFilter extends EventFilter2D implements FrameAnnotater {

    private int[][] histogram = null;
    private boolean collect = false;
    private float threshold = getFloat("threshold", 0.5f);
    private int histmax = 0;
    private static final String HISTOGRAM_FILE_NAME = "trackhistogram.dat";
    private boolean showHistogram = getBoolean("showHistogram", false);

    public TrackHistogramFilter(AEChip chip) {
        super(chip);
        setEnclosedFilter(new BackgroundActivityFilter(chip));
        doLoadHistogram();
        setPropertyTooltip("collect", "set true to accumulate histogram");
        setPropertyTooltip("threshold", "threshold in accumulated events to allow events to pass through");
        setPropertyTooltip("histmax", "maximum histogram count");
        setPropertyTooltip("showHistogram", "paints histogram as white level over image");
        setPropertyTooltip("clearHistogram", "clears histogram");
        setPropertyTooltip("freezeHistogram", "freezes current histogram");
        setPropertyTooltip("saveHistogram", "saves current histogram to the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("loadHistogram", "loads histogram from the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("collectHistogram", "turns on histogram accumulation");
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
            for (int i = 0; i < histogram.length; i++) {
                Arrays.fill(histogram[i], 0);
            }
        }
        setHistmax(0);
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

            File file = new File(HISTOGRAM_FILE_NAME);
            log.info("Saving track data to " + file.getName());
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(histogram.length);
            oos.writeObject(histogram);
            oos.close();
            fos.close();
            log.info("histogram saved in startup path (usually host/java) to file " + file.getPath());
        } catch (IOException e) {
            log.warning("couldn't save histogram: " + e);
        }

    }

    final synchronized public void doLoadHistogram() {
        File file = new File(HISTOGRAM_FILE_NAME);
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
                float v = (float) histogram[x][y] / histmax;
                gl.glColor4f(v, v, v, 0.5f);
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
        if (histogram == null || histogram.length != getChip().getSizeX() || histogram[0].length != getChip().getSizeY()) {
            histogram = new int[chip.getSizeX()][chip.getSizeY()];
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

}
