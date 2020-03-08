/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.npp.DvsFramerSingleFrame;
import java.awt.Color;
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Computes CNN from DAVIS APS frames.
 *
 * @author tobi
 */
@Description("Computes CNN from DAVIS APS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisDeepLearnCnnProcessor_HJ extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {

    protected DeepLearnCnnNetwork_HJ apsDvsNet = new DeepLearnCnnNetwork_HJ(); //, dvsNet = new DeepLearnCnnNetwork();
    private String lastApsDvsNetXMLFilename = getString("lastAPSNetXMLFilename", "LCRN_cnn.xml");
//    private String lastDVSNetXMLFilename = getString("lastDVSNetXMLFilename", "LCRN_cnn.xml");
//    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    private boolean showActivations = getBoolean("showActivations", false);
    private boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private float uniformWeight = getFloat("uniformWeight", 0);
    private float uniformBias = getFloat("uniformBias", 0);
    protected boolean measurePerformance = getBoolean("measurePerformance", false);
    protected boolean processAPSFrames = getBoolean("processAPSFrames", true);
//    protected boolean processAPSDVSTogetherInAPSNet = true; // getBoolean("processAPSDVSTogetherInAPSNet", true);
    private boolean processDVSTimeSlices = getBoolean("processDVSTimeSlices", true);
    protected boolean addedPropertyChangeListener = false;  // must do lazy add of us as listener to chip because renderer is not there yet when this is constructed
    private int dvsMinEvents = getInt("dvsMinEvents", 10000);

    private JFrame imageDisplayFrame = null;
    public ImageDisplay inputImageDisplay;

    protected DvsFramerSingleFrame dvsSubsampler = null;
    private int dvsColorScale = getInt("dvsColorScale", 200); // 1/dvsColorScale is amount each event color the timeslice in subsampled timeslice input
    private boolean softMaxOutput = getBoolean("softMaxOutput", false);

    protected int lastProcessedEventTimestamp = 0;

    public DavisDeepLearnCnnProcessor_HJ(AEChip chip) {
        super(chip);
        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";
        setPropertyTooltip("loadApsDvsNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m that proceses both APS and DVS frames");
//        setPropertyTooltip("loadDVSTimesliceNetworkFromXML", "For the DVS time slices, load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
//        setPropertyTooltip(deb, "setNetworkToUniformValues", "sets previously-loaded net to uniform values for debugging");
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(disp, "showKernels", "draw all the network kernels (once) in a new JFrame");
        setPropertyTooltip(disp, "showActivations", "draws the network activations in a separate JFrame");
        setPropertyTooltip(disp, "hideSubsamplingLayers", "hides layers that are subsampling conv layers");
        setPropertyTooltip(disp, "hideConvLayers", "hides conv layers");
        setPropertyTooltip(disp, "normalizeActivationDisplayGlobally", "normalizes the activations of layers globally across features");
        setPropertyTooltip(disp, "normalizeKernelDisplayWeightsGlobally", "normalizes the weights globally across layer");
        setPropertyTooltip(disp, "softMaxOutput", "normalizes the final outputs using softmax; use for ReLu final layer to display output in 0-1 range");
        setPropertyTooltip(deb, "inputClampedTo1", "clamps network input image to fixed value (1) for debugging");
        setPropertyTooltip(deb, "inputClampedToIncreasingIntegers", "clamps network input image to idx of matrix, increasing integers, for debugging");
        setPropertyTooltip(deb, "printActivations", "prints out activations of CNN layers for debugging; by default shows input and output; combine with hideConvLayers and hideSubsamplingLayers to show more layers");
        setPropertyTooltip(deb, "printWeights", "prints out weights of APS net layers for debugging");
        setPropertyTooltip(anal, "measurePerformance", "Measures and logs time in ms to process each frame along with estimated operations count (MAC=2OPS)");
        setPropertyTooltip(anal, "processAPSFrames", "sends APS frames to convnet");
        setPropertyTooltip(anal, "processDVSTimeSlices", "sends DVS time slices to convnet");
        setPropertyTooltip(anal, "processAPSDVSTogetherInAPSNet", "sends APS frames and DVS time slices to single convnet");
        setPropertyTooltip(anal, "dvsColorScale", "1/dvsColorScale is the amount by which each DVS event is added to time slice 2D gray-level histogram");
        setPropertyTooltip(anal, "dvsMinEvents", "minimum number of events to run net on DVS timeslice");
        initFilter();
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    synchronized public void doLoadApsDvsNetworkFromXML() {
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
            apsDvsNet.loadFromXMLFile(c.getSelectedFile());
            dvsSubsampler = new DvsFramerSingleFrame(chip);
            dvsSubsampler.setFromNetwork(apsDvsNet);
        } catch (Exception ex) {
            Logger.getLogger(DavisDeepLearnCnnProcessor_HJ.class.getName()).log(Level.SEVERE, null, ex);
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
            final DeepLearnCnnNetwork_HJ ref = apsDvsNet;
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

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener) {
            ((DavisRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(DavisRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsDvsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet

        if ((apsDvsNet != null)) {
            final int sizeX = chip.getSizeX();
            final int sizeY = chip.getSizeY();
            for (BasicEvent e : in) {
                lastProcessedEventTimestamp = e.getTimestamp();
                PolarityEvent p = (PolarityEvent) e;
                if (dvsSubsampler != null) {
                    dvsSubsampler.addEvent(p);
                }
                if (dvsSubsampler != null && dvsSubsampler.getAccumulatedEventCount() > dvsMinEvents) {
                    long startTime = 0;
                    if (measurePerformance) {
                        startTime = System.nanoTime();
                    }
                    if (processDVSTimeSlices) {
                        apsDvsNet.processDvsTimeslice(dvsSubsampler); // generates PropertyChange EVENT_MADE_DECISION
                        if (measurePerformance) {
                            long dt = System.nanoTime() - startTime;
                            float ms = 1e-6f * dt;
                            log.info(String.format("DVS slice processing time: %.1fms; %s", ms, apsDvsNet.getPerformanceString()));
                        }
                    }

                }
            }

        }
        return in;
    }

    @Override
    public void resetFilter() {
        if (dvsSubsampler != null) {
            dvsSubsampler.clear();
        }
    }

    @Override
    public void initFilter() {
        // if apsDvsNet was loaded before, load it now
        if (lastApsDvsNetXMLFilename != null) {
            File f = new File(lastApsDvsNetXMLFilename);
            if (f.exists() && f.isFile()) {
                try {
                    apsDvsNet.loadFromXMLFile(f);
                    apsDvsNet.setSoftMaxOutput(softMaxOutput); // must set manually since net doesn't know option kept here.
                    dvsSubsampler = new DvsFramerSingleFrame(chip);
                    dvsSubsampler.setFromNetwork(apsDvsNet);
                } catch (IOException ex) {
                    Logger.getLogger(DavisDeepLearnCnnProcessor_HJ.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
//        if (lastDVSNetXMLFilename != null) {
//            File f = new File(lastDVSNetXMLFilename);
//            if (f.exists() && f.isFile()) {
//                dvsNet.loadFromXMLFile(f);
//                dvsSubsampler = new DvsFramer(dvsNet.inputLayer.dimx, dvsNet.inputLayer.dimy, getDvsColorScale());
//            }
//        }

    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        // new activationsFrame is available, process it
        if (isFilterEnabled() && (apsDvsNet != null) && (processAPSFrames)) {
//            float[] frame = frameExtractor.getNewFrame();
//            if (frame == null || frame.length == 0 || frameExtractor.getOutputImageWidth() == 0) {
//                return;
//            }

            long startTime = 0;
            if (measurePerformance) {
                startTime = System.nanoTime();
            }
            float[] outputs = apsDvsNet.processDownsampledFrame((DavisRenderer) (chip.getRenderer()));
            if (measurePerformance) {
                long dt = System.nanoTime() - startTime;
                float ms = 1e-6f * dt;
                float fps = 1e3f / ms;
                log.info(String.format("Frame processing time: %.1fms (%.1f FPS); %s", ms, fps, apsDvsNet.getPerformanceString()));
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
        }
        if (showActivations) {
            if (apsDvsNet != null) {
                apsDvsNet.drawActivations();
            }
//            if (dvsNet != null && processDVSTimeSlices) {
//                dvsNet.drawActivations();
//            }
        }

        if (showOutputAsBarChart) {
            final float lineWidth = 2;
            if (apsDvsNet.outputLayer != null) {
                apsDvsNet.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
            }
//            if (dvsNet.outputLayer != null && processDVSTimeSlices) {
//                dvsNet.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.YELLOW);
//            }
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

    private void checkDisplayFrame() {
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
        return apsDvsNet.isHideSubsamplingLayers();
    }

    public void setHideSubsamplingLayers(boolean hideSubsamplingLayers) {
        apsDvsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
//        dvsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
    }

    public boolean isHideConvLayers() {
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

    /**
     * @return the dvsColorScale
     */
    public int getDvsColorScale() {
        return dvsColorScale;
    }

    /**
     * @param dvsColorScale the dvsColorScale to set
     */
    synchronized public void setDvsColorScale(int dvsColorScale) {
        if (dvsColorScale < 1) {
            dvsColorScale = 1;
        }
        this.dvsColorScale = dvsColorScale;
        putInt("dvsColorScale", dvsColorScale);
        if (dvsSubsampler != null) {
            dvsSubsampler.setDvsGrayScale(dvsColorScale);
        }
    }

    /**
     * @return the dvsMinEvents
     */
    public int getDvsMinEvents() {
        return dvsMinEvents;
    }

    /**
     * @param dvsMinEvents the dvsMinEvents to set
     */
    public void setDvsMinEvents(int dvsMinEvents) {
        this.dvsMinEvents = dvsMinEvents;
        putInt("dvsMinEvents", dvsMinEvents);
    }

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
}
