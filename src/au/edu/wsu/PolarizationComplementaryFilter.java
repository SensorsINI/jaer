package au.edu.wsu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.projects.davis.frames.DavisComplementaryFilter;
import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.TobiLogger;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Extracts Polarization information using Cedric's complementary filter to
 * obtain the absolute light intensity.
 *
 * @author Damien Joubert, Tobi Delbruck
 */
@Description("Method to extract polarization information from a stream of APS/DVS events using Cedric's complementary filter")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class PolarizationComplementaryFilter extends DavisComplementaryFilter {

    /**
     * indexes of offset f0, f45, f90 and f135 acording to the index, i.e., each
     * entry is an index into the full array for the subarray of f0, f45, etc
     * pixels
     */
    private int[] indexf0, indexf45, indexf90, indexf135;
    private JFrame apsFramePola = null;
    public ImageDisplay apsDisplayPola;
    private float[] apsDisplayPixmapBufferAop;
    // Angle of Polarization and Degree of Linear Polarization arrays. They are for macropixel 2x2 pixels. Indexing is 
    private float[] f0, f45, f90, f135;
    private float[] aop;
    private float[] dop;
    FloatFunction exp = (s) -> (float) Math.exp(s); // lambda function to linearize log intensity
    private TobiLogger tobiLogger = new TobiLogger("PolarizationComplementaryFilter", "PolarizationComplementaryFilter");
    private DescriptiveStatistics aopStats = new DescriptiveStatistics(), dopStats = new DescriptiveStatistics(); // mean values, computed after the ROI is processed
    private DescriptiveStatistics f0stats = new DescriptiveStatistics(), f45stats = new DescriptiveStatistics(); // mean values, computed after the ROI is processed
    private DescriptiveStatistics f90stats = new DescriptiveStatistics(), f135stats = new DescriptiveStatistics(); // mean values, computed after the ROI is processed
    private float meanf0 = Float.NaN;
    private float meanf45 = Float.NaN;
    private float meanf90 = Float.NaN;
    private float meanf135 = Float.NaN;
    private float meanAoP = Float.NaN, meanDoP = Float.NaN;
    private Stats stats = null;
    protected float statisticsLoggingIntervalMs = getFloat("statisticsLoggingIntervalMs", 1);
    private int lastLoggingTimestamp = 0;

    public PolarizationComplementaryFilter(final AEChip chip) {
        super(chip);
        apsDisplayPola = ImageDisplay.createOpenGLCanvas();
        apsFramePola = new JFrame("Polarization Information DoP - AoP");
        apsFramePola.setPreferredSize(new Dimension(600, 600));
        apsFramePola.getContentPane().add(apsDisplayPola, BorderLayout.CENTER);
        apsFramePola.pack();
        apsFramePola.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });
        initFilter();
        tobiLogger.setColumnHeaderLine("lastTimestamp(s),ROINumPixels,f0,f45,f90,f135,AoP(deg),AoPStd(deg),DoLP,DoLPStd"); // CSV columns, not including the first column which is system time in ms since epoch
        tobiLogger.setFileCommentString(String.format("useEvents=%s useFrames=%s onThresshold=%f offThreshold=%f crossoverFrequencyHz=%f kappa=%f lambda=%f",
                useEvents, useFrames, onThreshold, offThreshold, crossoverFrequencyHz, kappa, lambda));
        String pol = "Polarization";
        setPropertyTooltip(pol, "writePolarizationCSV", "Write a CSV file with the the mean and std of polarization AoP and DoLP for the ROI");
        setPropertyTooltip(pol, "plotAoPDoLP", "Plot accumulated results using pyplot");
        setPropertyTooltip(pol, "statisticsLoggingIntervalMs", "Min interval between saved samples in ms when using writePolarizationCSV");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkMaps();
        in = super.filterPacket(in);
        computePolarization();
        if (showAPSFrameDisplay) {
            apsDisplayPola.repaint();
        }
        return in; // should be denoised output
    }

    @Override
    protected void processEndOfFrameReadout(ApsDvsEvent e) {
        super.processEndOfFrameReadout(e); //To change body of generated methods, choose Tools | Templates.
        maybeWriteCSVRecord(e.timestamp, 0); // TODO fix num events
    }

    @Override
    protected void processDvsEvent(ApsDvsEvent e) {
        super.processDvsEvent(e); //To change body of generated methods, choose Tools | Templates.
        maybeWriteCSVRecord(e.timestamp, 0); // TODO fix num events
    }

    private void checkMaps() {
        apsDisplayPola.checkPixmapAllocation();
        if (showAPSFrameDisplay && !apsFramePola.isVisible()) {
            apsFramePola.setVisible(true);
        }
    }

    @Override
    public void initFilter() {
        super.initFilter();
        if (maxIDX > 0) {
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            f0 = new float[maxIDX / 4];
            f45 = new float[maxIDX / 4];
            f90 = new float[maxIDX / 4];
            f135 = new float[maxIDX / 4];
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
        lastLoggingTimestamp = 0;
    }

    synchronized private void computePolarization() {
        if (maxIDX != indexf0.length && maxIDX > 0) {
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            f0 = new float[maxIDX / 4];
            f45 = new float[maxIDX / 4];
            f90 = new float[maxIDX / 4];
            f135 = new float[maxIDX / 4];
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }

        // compute the AoP and DoLP in the ROI, using exp lambda function to linearize the estimated log intensity
        PolarizationUtils.computeAoPDoP(logFinalFrame, f0, f45, f90, f135, aop, dop, exp, indexf0, indexf45, indexf90, indexf135, height, width);

        if (roiRects != null) {
            // compute mean values
            int nb = 0, idx;
            f0stats.clear();
            f45stats.clear();
            f90stats.clear();
            f135stats.clear();
            aopStats.clear();
            dopStats.clear();
            for (Rectangle r : roiRects) {
                for (int x = r.x; x < r.x + r.width; x += 2) {
                    for (int y = r.y; y < r.y + r.height; y += 2) {
                        // compute idx into array, asssuming that AoP and DoP arrays are hold 2x2 macropixel values
                        idx = PolarizationUtils.getIndex(x / 2, y / 2, width / 2);// (int) (x / 2 + y / 2 * width / 2);
                        f0stats.addValue(f0[idx]);
                        f45stats.addValue(f45[idx]);
                        f90stats.addValue(f90[idx]);
                        f135stats.addValue(f135[idx]);
                        aopStats.addValue(aop[idx]);
                        dopStats.addValue(dop[idx]);
                        nb += 1;
                    }
                }
            }
            meanf0 = (float) f0stats.getMean();  // compute the means to show in ellipse
            meanf45 = (float) f45stats.getMean();  // compute the means to show in ellipse
            meanf90 = (float) f90stats.getMean();  // compute the means to show in ellipse
            meanf135 = (float) f135stats.getMean();  // compute the means to show in ellipse
            meanAoP = (float) aopStats.getMean();  // compute the means to show in ellipse
            meanDoP = (float) dopStats.getMean();

            float meanAoPFromAvgs = 0, meanDoPFromAvgs = 0;
            // compute the AoP and DoLP using mean values of f0, f45 etc, maybe they are better conditioned
            float s0 = (meanf0 + meanf135 + meanf90 + meanf45) / 2;
            float s1 = meanf0 - meanf90;
            float s2 = meanf45 - meanf135;
            if (s0 > 0) {
                meanDoPFromAvgs = (float) Math.sqrt(s1 * s1 + s2 * s2) / s0;
                meanAoPFromAvgs = (float) (Math.atan2(s2, s1) / (2.0 * Math.PI) + 0.5);
            }
            meanDoP = meanDoPFromAvgs;
            meanAoP = meanAoPFromAvgs;

        }

        PolarizationUtils.setDisplay(apsDisplayPixmapBufferAop, aop, dop, height, width);
        apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);

    }

    private void maybeWriteCSVRecord(int ts, int nb) {
        // log the mean values to the CSV if open, should match the header line
        if (tobiLogger.isEnabled() && 1e-3f * ts >= 1e-3f * lastLoggingTimestamp + getStatisticsLoggingIntervalMs()) {
            computePolarization();
            final float time = 1e-6f * ts;
            lastLoggingTimestamp = ts;
            final float aopstd = (float) aopStats.getStandardDeviation();
            final float dopstd = (float) dopStats.getStandardDeviation();
            tobiLogger.log(String.format("%f,%d,%f,%f,%f,%f,%f,%f,%f,%f", time, nb, meanf0, meanf45, meanf90, meanf135, meanAoP, aopstd, meanDoP, dopstd));
            stats.add(time, meanf0, meanf45, meanf90, meanf135, meanAoP, meanDoP, aopstd, dopstd);
        }
    }

    public float getDoP(int x, int y) {
        return dop[getIndex(x, y)];
    }

    public float getAoP(int x, int y) {
        return aop[getIndex(x, y)];
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); // draws the ROI selection rectangle
        GL2 gl = drawable.getGL().getGL2();

        if (roiRects == null || roiRects.isEmpty()) {
            return;
        }
        // draw the polarization ellipse
        gl.glColor3f(1, 1, 1);  // set the RGB color
        gl.glLineWidth(3); // set the line width in screen pixels
        // draw the polarization ellipse

        for (Rectangle r : roiRects) {
            gl.glPushMatrix();
            DrawGL.drawEllipse(gl, (float) r.getCenterX(), (float) r.getCenterY(), (float) (20), (float) (20 * (1 - meanDoP)), meanAoP * 2.0f, 32);
            gl.glPopMatrix();
        }
    }

    public void doToggleOnWritePolarizationCSV() {
        tobiLogger.setFileCommentString(String.format("useEvents=%s useFrames=%s onThresshold=%f offThreshold=%f crossoverFrequencyHz=%f kappa=%f lambda=%f thresholdMultiplier=%f",
                useEvents, useFrames, onThreshold, offThreshold, crossoverFrequencyHz, kappa, lambda, thresholdMultiplier));
        if (stats == null) {
            stats = new Stats();
        }
        stats.clear();
        tobiLogger.setEnabled(true);
    }

    public void doToggleOffWritePolarizationCSV() {
        tobiLogger.setEnabled(false);
        tobiLogger.showFolderInDesktop();
        doPlotAoPDoLP();
    }

    synchronized public void doPlotAoPDoLP() {
        if (tobiLogger != null && tobiLogger.isEnabled()) {
            showPlainMessageDialogInSwingThread("finish recording CSV first", "not finished recording");
            return;
        }
        if (stats != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    stats.plot();
                }
            });
        } else {
            showPlainMessageDialogInSwingThread("nothing to plot yet, record a CSV file", "No data yet");
        }
    }

    /**
     * @return the statisticsLoggingIntervalMs
     */
    public float getStatisticsLoggingIntervalMs() {
        return statisticsLoggingIntervalMs;
    }

    /**
     * @param statisticsLoggingIntervalMs the statisticsLoggingIntervalMs to set
     */
    public void setStatisticsLoggingIntervalMs(float statisticsLoggingIntervalMs) {
        this.statisticsLoggingIntervalMs = statisticsLoggingIntervalMs;
        putFloat("statisticsLoggingIntervalMs", statisticsLoggingIntervalMs);
    }

    private class Stats {

        final int n = 1000;
        ArrayList<Float> times = new ArrayList(n), aops = new ArrayList(n), dops = new ArrayList(n), aopstds = new ArrayList(n), dopstds = new ArrayList(n);
        ArrayList<Float> f0s = new ArrayList(n), f45s = new ArrayList(n), f90s = new ArrayList(n), f135s = new ArrayList(n);

        public void add(float time, float f0, float f45, float f90, float f135, float aop, float dop, float aopstd, float dopstd) {
            times.add(time);
            f0s.add(f0);
            f45s.add(f45);
            f90s.add(f90);
            f135s.add(f135);
            aops.add(aop);
            dops.add(dop);
            aopstds.add(aopstd);
            dopstds.add(dopstd);
        }

        public void clear() {
            times.clear();
            f0s.clear();
            f45s.clear();
            f90s.clear();
            f135s.clear();
            aops.clear();
            dops.clear();
            aopstds.clear();
            dopstds.clear();
        }

        public void plot() {
            Plot plt = Plot.create(); // see https://github.com/sh0nk/matplotlib4j
            plt.subplot(2, 1, 1);
            plt.title("f0,f45,f90,f135");
            plt.xlabel("time (s)");
            plt.ylabel("intensity (DN)");
            plt.plot().add(times, f0s, "r").label("f0");
            plt.plot().add(times, f45s, "g").label("f45");
            plt.plot().add(times, f90s, "b").label("f90");
            plt.plot().add(times, f135s, "cyan").label("f135");
            plt.legend();

            plt.subplot(2, 1, 2);
            plt.plot().add(times, aops, "r").label("AoP");
            plt.plot().add(times, dops, "go-").label("DoLP");
            ArrayList[] aopErrs = errors(aops, aopstds);
            ArrayList[] dopErrs = errors(dops, dopstds);
            plt.plot().add(times, aopErrs[0], "r").linewidth(.4).linestyle("dotted");
            plt.plot().add(times, aopErrs[1], "r").linewidth(.4).linestyle("dotted");
            plt.plot().add(times, dopErrs[0], "g").linewidth(.4).linestyle("dotted");
            plt.plot().add(times, dopErrs[1], "g").linewidth(.4).linestyle("dotted");
            plt.xlabel("time (s)");
            plt.ylabel("AoP and DoLP");
            plt.title("AoP and DoLP vs time");
            plt.legend();
            try {
                plt.show();
            } catch (Exception ex) {
                log.warning("cannot show the plot with pyplot - did you install python and matplotlib on path? " + ex.toString());
                showWarningDialogInSwingThread("<html>Cannot show the plot with pyplot - did you install python and matplotlib on path? <p>" + ex.toString(), "Cannot plot");
            }
        }

        private ArrayList[] errors(ArrayList<Float> mean, ArrayList<Float> std) {
            ArrayList<Float> up = new ArrayList(mean.size()), down = new ArrayList(mean.size());
            for (int i = 0; i < mean.size(); i++) { // so awkward...
                up.add(mean.get(i) + std.get(i));
                down.add(mean.get(i) - std.get(i));
            }
            return new ArrayList[]{up, down};
        }
    }

}
