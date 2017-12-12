/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import eu.visualize.ini.convnet.DvsFramer.DvsFrame;
import java.awt.Color;
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.TimeLimiter;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Top level CNN class that holds general methods. Subclasses define a
 * particular kind of input by instantiating a subclass of DvsFramer.
 *
 * @author Tobi
 */
public abstract class DavisAbstractCNN extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    protected DavisCNN apsDvsNet = null; // new DavisCNN(); //, dvsNet = new DavisCNN();
    protected DvsFramer dvsSubsampler = null;
    protected String lastApsDvsNetXMLFilename = getString("lastAPSNetXMLFilename", "LCRN_cnn.xml");
    //    private String lastDVSNetXMLFilename = getString("lastDVSNetXMLFilename", "LCRN_cnn.xml");
    //    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    protected boolean showActivations = getBoolean("showActivations", false);
    protected boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    protected float uniformWeight = getFloat("uniformWeight", 0);
    protected float uniformBias = getFloat("uniformBias", 0);
    protected boolean measurePerformance = getBoolean("measurePerformance", false);
    protected boolean processAPSFrames = getBoolean("processAPSFrames", true);
    //    protected boolean processAPSDVSTogetherInAPSNet = true; // getBoolean("processAPSDVSTogetherInAPSNet", true);
    protected boolean processDVSTimeSlices = getBoolean("processDVSTimeSlices", true);
    protected boolean addedPropertyChangeListener = false; // must do lazy add of us as listener to chip because renderer is not there yet when this is constructed
    protected JFrame imageDisplayFrame = null;
    public ImageDisplay inputImageDisplay;
    protected boolean softMaxOutput = getBoolean("softMaxOutput", true); // more reasonable output by setting true
    protected boolean zeroPadding = getBoolean("zeroPadding", false); // false for original nullhop and roshambo nets
    protected boolean normalizeDVSForZsNullhop = getBoolean("normalizeDVSForZsNullhop", false); // uses DvsFramer normalizeFrame method to normalize DVS histogram images and in addition it shifts the pixel values to be centered around zero with range -1 to +1
    protected int lastProcessedEventTimestamp = 0;
    protected String performanceString = null; // holds string representation of processing time
    protected TimeLimiter timeLimiter = new TimeLimiter(); // private instance used to accumulate events to slices even if packet has timed out
    protected int processingTimeLimitMs = getInt("processingTimeLimitMs", 100); // time limit for processing packet in ms to process OF events (events still accumulate). Overrides the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events.
    protected String lastPerformanceString = null;

    public DavisAbstractCNN(AEChip chip) {
        super(chip);
        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";
        setPropertyTooltip("loadApsDvsNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m that proceses both APS and DVS frames");
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(disp, "showKernels", "draw all the network kernels (once) in a new JFrame");
        setPropertyTooltip(disp, "toggleShowActivations", "toggle showing network activations (by default just input and output layers)");
        setPropertyTooltip(disp, "showActivations", "draws the network activations in a separate JFrame");
        setPropertyTooltip(disp, "hideSubsamplingLayers", "hides layers that are subsampling conv layers");
        setPropertyTooltip(disp, "hideConvLayers", "hides conv layers");
        setPropertyTooltip(disp, "normalizeActivationDisplayGlobally", "normalizes the activations of layers globally across features");
        setPropertyTooltip(disp, "normalizeKernelDisplayWeightsGlobally", "normalizes the weights globally across layer");
        setPropertyTooltip(disp, "measurePerformance", "Measures and logs time in ms to process each frame along with estimated operations count (MAC=2OPS)");
        setPropertyTooltip(deb, "inputClampedTo1", "clamps network input image to fixed value (1) for debugging");
        setPropertyTooltip(deb, "inputClampedToIncreasingIntegers", "clamps network input image to idx of matrix, increasing integers, for debugging");
        setPropertyTooltip(deb, "printActivations", "prints out activations of CNN layers for debugging; by default shows input and output; combine with hideConvLayers and hideSubsamplingLayers to show more layers");
        setPropertyTooltip(deb, "printWeights", "prints out weights of APS net layers for debugging");
        setPropertyTooltip(anal, "softMaxOutput", "normalizes the final outputs using softmax; use for ReLu final layer to display output in 0-1 range");
        setPropertyTooltip(anal, "processAPSFrames", "sends APS frames to convnet");
        setPropertyTooltip(anal, "processDVSTimeSlices", "sends DVS time slices to convnet");
        setPropertyTooltip(anal, "processAPSDVSTogetherInAPSNet", "sends APS frames and DVS time slices to single convnet");
        setPropertyTooltip(anal, "zeroPadding", "CNN uses zero padding; must be set properly according to CNN to run CNN");
        setPropertyTooltip(anal, "processingTimeLimitMs", "<html>time limit for processing packet in ms to process OF events (events still accumulate). <br> Set to 0 to disable. <p>Alternative to the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events");
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    public synchronized void doLoadApsDvsNetworkFromXML() {
        JFileChooser c = new JFileChooser(lastApsDvsNetXMLFilename);
        FileFilter filt = new FileNameExtensionFilter("XML File", "xml");
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(new File(lastApsDvsNetXMLFilename));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastApsDvsNetXMLFilename = c.getSelectedFile().toString();
        putString("lastAPSNetXMLFilename", lastApsDvsNetXMLFilename);
        try {
            loadNetworkFromXML(c.getSelectedFile());
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNN.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load net from this file, caught exception " + ex + ". See console for logging.", "Bad network file", JOptionPane.WARNING_MESSAGE);
        }
    }

    //    /**
    //     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
    //     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
    //     * exported using Danny Neil's XML Matlab script cnntoxml.m.
    //     *
    //     */
    //    public void doLoadDVSTimesliceNetworkFromXML() {
    //        JFileChooser c = new JFileChooser(lastDVSNetXMLFilename);
    //        FileFilter filt = new FileNameExtensionFilter("XML File", "xml");
    //        c.addChoosableFileFilter(filt);
    //        c.setSelectedFile(new File(lastDVSNetXMLFilename));
    //        int ret = c.showOpenDialog(chip.getAeViewer());
    //        if (ret != JFileChooser.APPROVE_OPTION) {
    //            return;
    //        }
    //        lastDVSNetXMLFilename = c.getSelectedFile().toString();
    //        putString("lastDVSNetXMLFilename", lastDVSNetXMLFilename);
    //        dvsNet.loadFromXMLFile(c.getSelectedFile());
    //        dvsSubsampler = new DvsFramer(dvsNet.inputLayer.dimx, dvsNet.inputLayer.dimy, getDvsColorScale());
    //    }
    // debug only
    //    public void doSetNetworkToUniformValues() {
    //        if (apsDvsNet != null) {
    //            apsDvsNet.setNetworkToUniformValues(uniformWeight, uniformBias);
    //        }
    //    }
    public void doShowKernels() {
        if (apsDvsNet != null) {
            if (!apsDvsNet.networkRanOnce) {
                JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Network must run at least once to correctly plot kernels (internal variables for indexing are computed at runtime)");
                return;
            }
            final DavisCNN ref = apsDvsNet;
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        JFrame frame = apsDvsNet.drawKernels();
                        frame.setTitle("APS net kernel weights");
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };
            SwingUtilities.invokeLater(runnable);
        }
    }

    public void doToggleShowActivations() {
        setShowActivations(!isShowActivations());
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            if (dvsSubsampler == null) {
                throw new RuntimeException("Null dvsSubsampler; this should not occur");
            } else {
                dvsSubsampler.getSupport().addPropertyChangeListener(DvsFramer.EVENT_NEW_FRAME_AVAILABLE, this);
            }
            addedPropertyChangeListener = true;
        }
        if (apsDvsNet == null) {
            log.warning("null CNN; load one with the LoadApsDvsNetworkFromXML button");
            return in;
        }
        // send DVS timeslice to convnet
        resetTimeLimiter();
        //            final int sizeX = chip.getSizeX();
        //            final int sizeY = chip.getSizeY();
        for (BasicEvent e : in) {
            lastProcessedEventTimestamp = e.getTimestamp();
            PolarityEvent p = (PolarityEvent) e;
            if (dvsSubsampler != null) {
                dvsSubsampler.addEvent(p); // generates event when full, which processes it in propertyChange() which computes CNN
            }
            if (timeLimiter.isTimedOut()) {
                break; // discard rest of this packet
            }
        }
        return in;
    }

    protected void resetTimeLimiter() {
        if (processingTimeLimitMs > 0) {
            timeLimiter.setTimeLimitMs(processingTimeLimitMs);
            timeLimiter.restart();
        } else {
            timeLimiter.setEnabled(false);
        }
    }

    @Override
    public void resetFilter() {
        if (dvsSubsampler != null) {
            dvsSubsampler.resetFilter();
        }
    }

    @Override
    public void initFilter() {
        // if apsDvsNet was loaded before, load it now
        if (lastApsDvsNetXMLFilename != null && apsDvsNet == null) {
            File f = new File(lastApsDvsNetXMLFilename);
            loadNetworkFromXML(f);
        }
    }

    protected void loadNetworkFromXML(File f) {
        if (f.exists() && f.isFile()) {
            try {
                apsDvsNet = new DavisCNN();
                apsDvsNet.loadFromXMLFile(f);
                apsDvsNet.setSoftMaxOutput(softMaxOutput); // must set manually since net doesn't know option kept here.
                apsDvsNet.setZeroPadding(zeroPadding); // must set manually since net doesn't know option kept here.
                dvsSubsampler.setFromNetwork(apsDvsNet);
            } catch (IOException ex) {
                log.warning("Couldn't load the CNN from file " + f + ": got exception " + ex);
            }
        }
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        // new activationsFrame is available, process it
        switch (evt.getPropertyName()) {
            case AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE:
                if (isFilterEnabled() && (apsDvsNet != null) && (processAPSFrames)) {
                    long startTime = 0;
                    if (measurePerformance) {
                        startTime = System.nanoTime();
                    }
                    float[] outputs = apsDvsNet.processDownsampledFrame((AEFrameChipRenderer) (chip.getRenderer()));
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS); %s", ms, fps, apsDvsNet.getPerformanceString());
                    }
                }
                break;
            case DvsFramer.EVENT_NEW_FRAME_AVAILABLE:
                long startTime = 0;
                if (measurePerformance) {
                    startTime = System.nanoTime();
                }
                if (processDVSTimeSlices) {
                    apsDvsNet.processDvsTimeslice((DvsFrame) evt.getNewValue()); // generates PropertyChange EVENT_MADE_DECISION
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS); %s", ms, fps, apsDvsNet.getPerformanceString());
                    }
                }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (apsDvsNet != null && apsDvsNet.netname != null) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.netname);
            if (performanceString != null && !performanceString.equals(lastPerformanceString)) {
                MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
                lastPerformanceString = performanceString;
            }
        }
        if (showActivations) {
            if (apsDvsNet != null) {
                apsDvsNet.drawActivations();
            }
        }
        if (showOutputAsBarChart) {
            final float lineWidth = 2;
            if (apsDvsNet != null && apsDvsNet.outputLayer != null) {
                apsDvsNet.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
            }
        }
    }

    /**
     * @return the showActivations
     */
    public boolean isShowActivations() {
        return showActivations;
    }

    /**
     * @param showActivations the showActivations to set
     */
    public void setShowActivations(boolean showActivations) {
        this.showActivations = showActivations;
    }

    /**
     * @return the showOutputAsBarChart
     */
    public boolean isShowOutputAsBarChart() {
        return showOutputAsBarChart;
    }

    /**
     * @param showOutputAsBarChart the showOutputAsBarChart to set
     */
    public void setShowOutputAsBarChart(boolean showOutputAsBarChart) {
        this.showOutputAsBarChart = showOutputAsBarChart;
        putBoolean("showOutputAsBarChart", showOutputAsBarChart);
    }

    protected void checkDisplayFrame() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    //    /**
    //     * @return the uniformWeight
    //     */
    //    public float getUniformWeight() {
    //        return uniformWeight;
    //    }
    //
    //    /**
    //     * @param uniformWeight the uniformWeight to set
    //     */
    //    public void setUniformWeight(float uniformWeight) {
    //        this.uniformWeight = uniformWeight;
    //        putFloat("uniformWeight", uniformWeight);
    //    }
    //
    //    /**
    //     * @return the uniformBias
    //     */
    //    public float getUniformBias() {
    //        return uniformBias;
    //    }
    //
    //    /**
    //     * @param uniformBias the uniformBias to set
    //     */
    //    public void setUniformBias(float uniformBias) {
    //        this.uniformBias = uniformBias;
    //        putFloat("uniformBias", uniformBias);
    //    }
    // apsDvsNet computation debug methods
    public boolean isInputClampedTo1() {
        if (apsDvsNet == null) {
            return false;
        }
        return apsDvsNet == null ? false : apsDvsNet.isInputClampedTo1();
    }

    public void setInputClampedTo1(boolean inputClampedTo1) {
        if (apsDvsNet != null) {
            apsDvsNet.setInputClampedTo1(inputClampedTo1);
        }
    }

    public boolean isInputClampedToIncreasingIntegers() {
        return apsDvsNet == null ? false : apsDvsNet.isInputClampedToIncreasingIntegers();
    }

    public void setInputClampedToIncreasingIntegers(boolean inputClampedTo1) {
        if (apsDvsNet != null) {
            apsDvsNet.setInputClampedToIncreasingIntegers(inputClampedTo1);
        }
    }

    /**
     * @return the measurePerformance
     */
    public boolean isMeasurePerformance() {
        return measurePerformance;
    }

    /**
     * @param measurePerformance the measurePerformance to set
     */
    public void setMeasurePerformance(boolean measurePerformance) {
        this.measurePerformance = measurePerformance;
        putBoolean("measurePerformance", measurePerformance);
    }

    public boolean isHideSubsamplingLayers() {
        if (apsDvsNet == null) {
            return true;
        }
        return apsDvsNet.isHideSubsamplingLayers();
    }

    public void setHideSubsamplingLayers(boolean hideSubsamplingLayers) {
        apsDvsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
        //        dvsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
    }

    public boolean isHideConvLayers() {
        if (apsDvsNet == null) {
            return true;
        }
        return apsDvsNet.isHideConvLayers();
    }

    public void setHideConvLayers(boolean hideConvLayers) {
        apsDvsNet.setHideConvLayers(hideConvLayers);
        //        dvsNet.setHideConvLayers(hideConvLayers);
    }

    /**
     * @return the processDVSTimeSlices
     */
    public boolean isProcessDVSTimeSlices() {
        return processDVSTimeSlices;
    }

    /**
     * @param processDVSTimeSlices the processDVSTimeSlices to set
     */
    public void setProcessDVSTimeSlices(boolean processDVSTimeSlices) {
        boolean old = this.processDVSTimeSlices;
        this.processDVSTimeSlices = processDVSTimeSlices;
        putBoolean("processDVSTimeSlices", processDVSTimeSlices);
        getSupport().firePropertyChange("processDVSTimeSlices", old, processDVSTimeSlices);
    }

    /**
     * @return the processAPSFrames
     */
    public boolean isProcessAPSFrames() {
        return processAPSFrames;
    }

    /**
     * @param processAPSFrames the processAPSFrames to set
     */
    public void setProcessAPSFrames(boolean processAPSFrames) {
        boolean old = this.processAPSFrames;
        this.processAPSFrames = processAPSFrames;
        putBoolean("processAPSFrames", processAPSFrames);
        getSupport().firePropertyChange("processAPSFrames", old, processAPSFrames);
    }

    //    /**
    //     * @return the dvsGrayScale
    //     */
    //    public int getDvsColorScale() {
    //        return dvsGrayScale;
    //    }
    //
    //    /**
    //     * @param dvsGrayScale the dvsGrayScale to set
    //     */
    //    synchronized public void setDvsColorScale(int dvsGrayScale) {
    //        if (dvsGrayScale < 1) {
    //            dvsGrayScale = 1;
    //        }
    //        this.dvsGrayScale = dvsGrayScale;
    //        putInt("dvsGrayScale", dvsGrayScale);
    //        if (dvsSubsampler != null) {
    //            dvsSubsampler.setColorScale(dvsGrayScale);
    //        }
    //    }
    //    /**
    //     * @return the dvsEventsPerFrame
    //     */
    //    public int getDvsEventsPerFrame() {
    //        return dvsEventsPerFrame;
    //    }
    //
    //    /**
    //     * @param dvsEventsPerFrame the dvsEventsPerFrame to set
    //     */
    //    public void setDvsEventsPerFrame(int dvsEventsPerFrame) {
    //        this.dvsEventsPerFrame = dvsEventsPerFrame;
    //        putInt("dvsEventsPerFrame", dvsEventsPerFrame);
    //    }
    public boolean isNormalizeKernelDisplayWeightsGlobally() {
        if (apsDvsNet == null) {
            return false;
        } else {
            return apsDvsNet.isNormalizeKernelDisplayWeightsGlobally();
        }
    }

    public void setNormalizeKernelDisplayWeightsGlobally(boolean normalizeKernelDisplayWeightsGlobally) {
        if (apsDvsNet != null) {
            apsDvsNet.setNormalizeKernelDisplayWeightsGlobally(normalizeKernelDisplayWeightsGlobally);
        }
        //        if (dvsNet != null) {
        //            dvsNet.setNormalizeKernelDisplayWeightsGlobally(normalizeKernelDisplayWeightsGlobally);
        //        }
    }

    public boolean isNormalizeActivationDisplayGlobally() {
        if (apsDvsNet == null) {
            return false;
        }
        return apsDvsNet.isNormalizeActivationDisplayGlobally();
    }

    public void setNormalizeActivationDisplayGlobally(boolean normalizeActivationDisplayGlobally) {
        if (apsDvsNet != null) {
            apsDvsNet.setNormalizeActivationDisplayGlobally(normalizeActivationDisplayGlobally);
        }
        //        if (dvsNet != null) {
        //            dvsNet.setNormalizeActivationDisplayGlobally(normalizeActivationDisplayGlobally);
        //        }
    }

    public boolean isPrintActivations() {
        if (apsDvsNet == null) {
            return false;
        }
        return apsDvsNet.isPrintActivations();
    }

    public void setPrintActivations(boolean printActivations) {
        if (apsDvsNet == null) {
            return;
        }
        apsDvsNet.setPrintActivations(printActivations);
    }

    public boolean isPrintWeights() {
        if (apsDvsNet == null) {
            return false;
        }
        return apsDvsNet.isPrintWeights();
    }

    public void setPrintWeights(boolean printWeights) {
        if (apsDvsNet == null) {
            return;
        }
        apsDvsNet.setPrintWeights(printWeights);
    }

    //    /**
    //     * @return the processAPSDVSTogetherInAPSNet
    //     */
    //    public boolean isProcessAPSDVSTogetherInAPSNet() {
    //        return processAPSDVSTogetherInAPSNet;
    //    }
    //
    //    /**
    //     * @param processAPSDVSTogetherInAPSNet the processAPSDVSTogetherInAPSNet to
    //     * set
    //     */
    //    public void setProcessAPSDVSTogetherInAPSNet(boolean processAPSDVSTogetherInAPSNet) {
    //        this.processAPSDVSTogetherInAPSNet = processAPSDVSTogetherInAPSNet;
    //        putBoolean("processAPSDVSTogetherInAPSNet", processAPSDVSTogetherInAPSNet);
    //        if (processAPSDVSTogetherInAPSNet) {
    //            setProcessAPSFrames(false);
    //            setProcessDVSTimeSlices(false);
    //        }
    //    }
    public boolean isSoftMaxOutput() {
        if (apsDvsNet == null) {
            return softMaxOutput;
        }
        return apsDvsNet.isSoftMaxOutput();
    }

    public void setSoftMaxOutput(boolean softMaxOutput) {
        this.softMaxOutput = softMaxOutput;
        putBoolean("softMaxOutput", softMaxOutput);
        if (apsDvsNet == null) {
            return;
        }
        apsDvsNet.setSoftMaxOutput(softMaxOutput);
    }

    /**
     * @return the zeroPadding
     */
    public boolean isZeroPadding() {
        return zeroPadding;
    }

    /**
     * @param zeroPadding the zeroPadding to set
     */
    public void setZeroPadding(boolean zeroPadding) {
        this.zeroPadding = zeroPadding;
        putBoolean("zeroPadding", zeroPadding);
        if (apsDvsNet == null) {
            return;
        }
        apsDvsNet.setZeroPadding(zeroPadding);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            cleanup();
        }
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        if (showActivations && apsDvsNet != null) {
            apsDvsNet.cleanup();
        }
    }

    /**
     * @return the processingTimeLimitMs
     */
    public int getProcessingTimeLimitMs() {
        return processingTimeLimitMs;
    }

    /**
     * @param processingTimeLimitMs the processingTimeLimitMs to set
     */
    public void setProcessingTimeLimitMs(int processingTimeLimitMs) {
        this.processingTimeLimitMs = processingTimeLimitMs;
        putInt("processingTimeLimitMs", processingTimeLimitMs);
    }

}
