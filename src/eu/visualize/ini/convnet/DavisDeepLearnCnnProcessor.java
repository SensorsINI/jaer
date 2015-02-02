/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Computes CNN from DAVIS APS frames.
 *
 * @author tobi
 */
@Description("Computes CNN from DAVIS APS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisDeepLearnCnnProcessor extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {

    protected DeepLearnCnnNetwork apsNet = new DeepLearnCnnNetwork(), dvsNet = new DeepLearnCnnNetwork();
    private String lastFileName = getString("lastFileName", "LCRN_cnn.xml");
//    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    private boolean showActivations = getBoolean("showActivations", false);
    private boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private float uniformWeight = getFloat("uniformWeight", 0);
    private float uniformBias = getFloat("uniformBias", 0);
    private boolean measurePerformance = getBoolean("measurePerformance", false);
    private boolean processDVSTimeSlices = getBoolean("processDVSTimeSlices", false);
    private boolean processAPSFrames = getBoolean("processAPSFrames", true);
    private boolean addedPropertyChangeListener = false;  // must do lazy add of us as listener to chip because renderer is not there yet when this is constructed

    private JFrame imageDisplayFrame = null;
    public ImageDisplay inputImageDisplay;

    private DvsSubsamplingTimesliceConvNetInput dvsSubsampler = null;
    private int dvsColorScale = getInt("dvsColorScale", 32); // 1/dvsColorScale is amount each event color the timeslice in subsampled timeslice input

    public DavisDeepLearnCnnProcessor(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
//        chain.add(frameExtractor);
//        setEnclosedFilterChain(chain);
//        frameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);
        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";
        setPropertyTooltip("loadCNNNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
        setPropertyTooltip(deb, "setNetworkToUniformValues", "sets previously-loaded net to uniform values for debugging");
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(disp, "showKernels", "draw all the network kernels (once) in a new JFrame");
        setPropertyTooltip(disp, "showActivations", "draws the network activations in a separate JFrame");
        setPropertyTooltip(disp, "hideSubsamplingLayers", "hides layers that are subsampling conv layers");
        setPropertyTooltip(disp, "hideConvLayers", "hides conv layers");
        setPropertyTooltip(deb, "inputClampedTo1", "clamps network input image to fixed value (1) for debugging");
        setPropertyTooltip(deb, "inputClampedToIncreasingIntegers", "clamps network input image to idx of matrix, increasing integers, for debugging");
        setPropertyTooltip(disp, "measurePerformance", "Measures and logs time in ms to process each frame");
        setPropertyTooltip(disp, "measurePerformance", "Measures and logs time in ms to process each frame");
        setPropertyTooltip(disp, "measurePerformance", "Measures and logs time in ms to process each frame");
        setPropertyTooltip(anal, "processAPSFrames", "sends APS frames to convnet");
        setPropertyTooltip(anal, "processDVSTimeSlices", "sends DVS time slices to convnet");
        setPropertyTooltip(anal, "dvsColorScale", "1/dvsColorScale is the amount by which each DVS event is added to time slice 2D gray-level histogram");

        initFilter();
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    public void doLoadCNNNetworkFromXML() {
        JFileChooser c = new JFileChooser(lastFileName);
        FileFilter filt = new FileNameExtensionFilter("XML File", "xml");
        c.addChoosableFileFilter(filt);
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        apsNet.loadFromXMLFile(c.getSelectedFile());
        dvsNet.loadFromXMLFile(c.getSelectedFile());

    }
// debug only
//    public void doSetNetworkToUniformValues() {
//        if (apsNet != null) {
//            apsNet.setNetworkToUniformValues(uniformWeight, uniformBias);
//        }
//    }

    public void doShowKernels() {
        if (apsNet != null) {
            apsNet.drawKernels();
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        dvsSubsampler.clear();
        if (processDVSTimeSlices) {
            final int sizeX = chip.getSizeX();
            final int sizeY = chip.getSizeY();
            for (BasicEvent e : in) {
                PolarityEvent p = (PolarityEvent) e;
                dvsSubsampler.addEvent(p, sizeX, sizeY);
            }
            dvsNet.processDvsTimeslice(dvsSubsampler);

        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        // if apsNet was loaded before, load it now
        if (lastFileName != null) {
            File f = new File(lastFileName);
            if (f.exists() && f.isFile()) {
                apsNet.loadFromXMLFile(f);
                dvsNet.loadFromXMLFile(f);
                dvsSubsampler = new DvsSubsamplingTimesliceConvNetInput(dvsNet.inputLayer.dimx, dvsNet.inputLayer.dimy, getDvsColorScale());
            }
        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // new activationsFrame is available, process it
        if (apsNet != null && processAPSFrames) {
//            float[] frame = frameExtractor.getNewFrame(); 
//            if (frame == null || frame.length == 0 || frameExtractor.getWidth() == 0) {
//                return;
//            }

            long startTime = 0;
            if (measurePerformance) {
                startTime = System.nanoTime();
            }
            float[] outputs = apsNet.processFrame((AEFrameChipRenderer) (chip.getRenderer()));
            if (measurePerformance) {
                long dt = System.nanoTime() - startTime;
                float ms = 1e-6f * dt;
                float fps = 1e3f / ms;
                log.info(String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps));

            }
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showActivations) {
            if (apsNet != null && processAPSFrames) {
                apsNet.drawActivations();
            }
            if (dvsNet != null && processDVSTimeSlices) {
                dvsNet.drawActivations();
            }
        }

        if (showOutputAsBarChart) {
            final float lineWidth = 2;
            if (apsNet.outputLayer != null && processAPSFrames) {
                apsNet.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
            }
            if (dvsNet.outputLayer != null && processDVSTimeSlices) {
                dvsNet.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.YELLOW);
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
    // apsNet computation debug methods
//    public boolean isInputClampedTo1() {
//        return apsNet == null ? false : apsNet.isInputClampedTo1();
//    }
//
//    public void setInputClampedTo1(boolean inputClampedTo1) {
//        if (apsNet != null) {
//            apsNet.setInputClampedTo1(inputClampedTo1);
//        }
//    }
//
//    public boolean isInputClampedToIncreasingIntegers() {
//        return apsNet == null ? false : apsNet.isInputClampedToIncreasingIntegers();
//    }
//
//    public void setInputClampedToIncreasingIntegers(boolean inputClampedTo1) {
//        if (apsNet != null) {
//            apsNet.setInputClampedToIncreasingIntegers(inputClampedTo1);
//        }
//    }
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
        return apsNet.isHideSubsamplingLayers();
    }

    public void setHideSubsamplingLayers(boolean hideSubsamplingLayers) {
        apsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
        dvsNet.setHideSubsamplingLayers(hideSubsamplingLayers);
    }

    public boolean isHideConvLayers() {
        return apsNet.isHideConvLayers();
    }

    public void setHideConvLayers(boolean hideConvLayers) {
        apsNet.setHideConvLayers(hideConvLayers);
        dvsNet.setHideConvLayers(hideConvLayers);
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
        this.processDVSTimeSlices = processDVSTimeSlices;
        putBoolean("processDVSTimeSlices", processDVSTimeSlices);
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
        this.processAPSFrames = processAPSFrames;
        putBoolean("processAPSFrames", processAPSFrames);
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
        if(dvsColorScale<1) dvsColorScale=1;
        this.dvsColorScale = dvsColorScale;
        putInt("dvsColorScale", dvsColorScale);
        if(dvsSubsampler!=null) dvsSubsampler.setColorScale(dvsColorScale);
    }

}
