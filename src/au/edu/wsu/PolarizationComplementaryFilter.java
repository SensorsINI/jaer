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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
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

    // offset of f0, f45, f90 and f135 acording to the index
    private int[] indexf0, indexf45, indexf90, indexf135;
    private JFrame apsFramePola = null;
    public ImageDisplay apsDisplayPola;
    private float[] apsDisplayPixmapBufferAop;
    // Angle of Polarization and Degree of Linear Polarization arrays. They are for macropixel 2x2 pixels. Indexing is 
    private float[] aop;
    private float[] dop;
    FloatFunction exp = (s) -> (float) Math.exp(s); // lambda function to linearize log intensity
    private TobiLogger tobiLogger = new TobiLogger("PolarizationComplementaryFilter", "PolarizationComplementaryFilter");
    private DescriptiveStatistics aopStats = new DescriptiveStatistics(), dopStats = new DescriptiveStatistics(); // mean values, computed after the ROI is processed
    private float meanAoP = Float.NaN, meanDoP = Float.NaN;
    private Stats stats = null;

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
        tobiLogger.setColumnHeaderLine("lastTimestamp(s),ROINumPixels,AoP(deg),AoPStd(deg),DoLP,DoLPStd"); // CSV columns, not including the first column which is system time in ms since epoch
        tobiLogger.setFileCommentString(String.format("useEvents=%s useFrames=%s onThresshold=%f offThreshold=%f crossoverFrequencyHz=%f kappa=%f lambda=%f",
                useEvents, useFrames, onThreshold, offThreshold, crossoverFrequencyHz, kappa, lambda));
        setPropertyTooltip("writePolarizationCSV", "Write a CSV file with the the mean and std of polarization AoP and DoLP for the ROI");
        setPropertyTooltip("plotAoPDoLP", "Plot accumulated results using pyplot");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkMaps();
        in = super.filterPacket(in);
        computeAndShowPolarization(in);
        if (showAPSFrameDisplay) {
            apsDisplayPola.repaint();
        }
        return in; // should be denoised output
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
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }
    }

    synchronized private void computeAndShowPolarization(EventPacket<? extends BasicEvent> in) {
        if (maxIDX != indexf0.length && maxIDX > 0) {
            indexf0 = new int[maxIDX];
            indexf45 = new int[maxIDX];
            indexf90 = new int[maxIDX];
            indexf135 = new int[maxIDX];
            apsDisplayPixmapBufferAop = new float[3 * maxIDX / 4 * 3];
            aop = new float[maxIDX / 4];
            dop = new float[maxIDX / 4];
            apsDisplayPola.setImageSize(width / 2, height / 2 * 3);
            PolarizationUtils.fillIndex(indexf0, indexf45, indexf90, indexf135, height, width);
            PolarizationUtils.drawLegend(apsDisplayPixmapBufferAop, height, width);
        }

        // compute the AoP and DoLP in the ROI, using exp lambda function to linearize the estimated log intensity
        PolarizationUtils.computeAoPDoP(logFinalFrame, aop, dop, exp, indexf0, indexf45, indexf90, indexf135, height, width);

        if (roiRect != null) {
            // compute mean values
            int nb = 0, idx;
            aopStats.clear();
            dopStats.clear();
            for (int x = roiRect.x; x < roiRect.x + roiRect.width; x += 2) {
                for (int y = roiRect.y; y < roiRect.y + roiRect.height; y += 2) {
                    // compute idx into array, asssuming that AoP and DoP arrays are hold 2x2 macropixel values
                    idx = PolarizationUtils.getIndex(x / 2, y / 2, width / 2);// (int) (x / 2 + y / 2 * width / 2);
                    aopStats.addValue(aop[idx]);
                    dopStats.addValue(dop[idx]);
                    nb += 1;
                }
            }
            meanAoP = (float) aopStats.getMean();  // compute the means to show in ellipse
            meanDoP = (float) dopStats.getMean();

            // log the mean values to the CSV if open, should match the header line 
            if (tobiLogger.isEnabled()) {
                final float time = 1e-6f * in.getLastTimestamp();
                final float aopstd = (float) aopStats.getStandardDeviation();
                final float dopstd = (float) dopStats.getStandardDeviation();
                tobiLogger.log(String.format("%f,%d,%f,%f,%f,%f", time, nb, meanAoP, aopstd, meanDoP, dopstd));
                stats.add(time, meanAoP, meanDoP, aopstd, dopstd);
            }
        }

//        System.out.printf("Angle: %f", m_aop * 180);
        // Show the polarization display; the ROI values are shown in annotate
        PolarizationUtils.setDisplay(apsDisplayPixmapBufferAop, aop, dop, height, width);
        apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);

//        PolarizationUtils.computeAoPDoP(logFinalFrame, aop, dop, exp, indexf0, indexf45, indexf90, indexf135, height, width);
//        PolarizationUtils.setDisplay(apsDisplayPixmapBuffer, aop, dop, height, width);
//        if(apsDisplayPixmapBufferAop.length > 0)
//            apsDisplayPola.setPixmapArray(apsDisplayPixmapBufferAop);
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

        if (roiRect == null) {
            return;
        }
        // draw the polarization ellipse
        gl.glColor3f(1, 1, 1);  // set the RGB color
        gl.glLineWidth(3); // set the line width in screen pixels
        // draw the polarization ellipse

        gl.glPushMatrix();
        DrawGL.drawEllipse(gl, (float) roiRect.getCenterX(), (float) roiRect.getCenterY(), (float) (20), (float) (20 * (1 - meanDoP)), meanAoP * 2.0f, 32);
        gl.glPopMatrix();
    }

    public void doToggleOnWritePolarizationCSV() {
        tobiLogger.setFileCommentString(String.format("useEvents=%s useFrames=%s onThresshold=%f offThreshold=%f crossoverFrequencyHz=%f kappa=%f lambda=%f thresholdMultiplier=%f",
                useEvents, useFrames, onThreshold, offThreshold, crossoverFrequencyHz, kappa, lambda,thresholdMultiplier));
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

    private class Stats {

        final int n = 1000;
        ArrayList<Float> times = new ArrayList(n), aops = new ArrayList(n), dops = new ArrayList(n), aopstds = new ArrayList(n), dopstds = new ArrayList(n);

        public void add(float time, float aop, float dop, float aopstd, float dopstd) {
            times.add(time);
            aops.add(aop);
            dops.add(dop);
            aopstds.add(aopstd);
            dopstds.add(dopstd);
        }

        public void clear() {
            times.clear();
            aops.clear();
            dops.clear();
            aopstds.clear();
            dopstds.clear();
        }

        public void plot() {
            Plot plt = Plot.create(); // see https://github.com/sh0nk/matplotlib4j

            // TODO add errors.  Plot is limited and cannot do anything fancy now, just basic line plots.
            plt.plot().add(times, aops, "r").label("AoP");
            plt.plot().add(times, dops, "g").label("DoLP");
            ArrayList[] aopErrs=errors(aops,aopstds);
            ArrayList[] dopErrs=errors(dops,dopstds);
            plt.plot().add(times,aopErrs[0],"r").linewidth(.4).linestyle("dotted");
            plt.plot().add(times,aopErrs[1],"r").linewidth(.4).linestyle("dotted");
            plt.plot().add(times,dopErrs[0],"g").linewidth(.4).linestyle("dotted");
            plt.plot().add(times,dopErrs[1],"g").linewidth(.4).linestyle("dotted");
            plt.xlabel("time (s)");
            plt.ylabel("AoP and DoLP");
//            plt.text(0.5, 0.2, "text");
            plt.title("AoP and DoLP vs time");
            plt.legend();
            try {
                plt.show();
            } catch (Exception ex) {
                log.warning("cannot show the plot with pyplot - did you install python and matplotlib on path? " + ex.toString());
            }
        }
        
        private ArrayList[] errors(ArrayList<Float> mean, ArrayList<Float>std){
            ArrayList<Float> up=new ArrayList(mean.size()), down=new ArrayList(mean.size());
            for(int i=0;i<mean.size();i++){ // so awkward...
                up.add(mean.get(i)+std.get(i));
                down.add(mean.get(i)-std.get(i));
            }
            return new ArrayList[] {up,down};
        }
    }

}
