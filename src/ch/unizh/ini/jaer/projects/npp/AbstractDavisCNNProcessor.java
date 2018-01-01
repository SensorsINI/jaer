/* 
 * Copyright (C) 2017 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.npp;

import ch.unizh.ini.jaer.projects.npp.AbstractDavisCNN.OutputLayer;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import ch.unizh.ini.jaer.projects.npp.DvsFramer.DvsFrame;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
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
public abstract class AbstractDavisCNNProcessor extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    /**
     * @return the showTop1Label
     */
    public boolean isShowTop1Label() {
        return showTop1Label;
    }

    /**
     * @param showTop1Label the showTop1Label to set
     */
    public void setShowTop1Label(boolean showTop1Label) {
        this.showTop1Label = showTop1Label;
        putBoolean("showTop1Label", showTop1Label);
    }

    /**
     * @return the showTop5Labels
     */
    public boolean isShowTop5Labels() {
        return showTop5Labels;
    }

    /**
     * @param showTop5Labels the showTop5Labels to set
     */
    public void setShowTop5Labels(boolean showTop5Labels) {
        this.showTop5Labels = showTop5Labels;
        putBoolean("showTop5Labels", showTop5Labels);
    }

    protected AbstractDavisCNN apsDvsNet = null; // new DavisCNNPureJava(); //, dvsNet = new DavisCNNPureJava();
    protected DvsFramer dvsSubsampler = null;
    protected String lastNetworkFilename = getString("lastNetworkFilename", "");
    protected String lastLabelsFilename = getString("lastLabelsFilename", "");
    protected String lastNetworkPathname = getString("lastNetworkPathname", "");
    //    private String lastDVSNetXMLFilename = getString("lastDVSNetXMLFilename", "LCRN_cnn.xml");
    //    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    protected boolean showActivations = getBoolean("showActivations", false);
    protected boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private boolean showTop1Label = getBoolean("showTop1Label", true);
    private boolean showTop5Labels = getBoolean("showTop5Labels", true);
    protected float uniformWeight = getFloat("uniformWeight", 0);
    protected float uniformBias = getFloat("uniformBias", 0);
    protected boolean measurePerformance = getBoolean("measurePerformance", true);
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
    protected boolean makeRGBFrames = getBoolean("makeRGBFrames", false);
    protected String inputLayerName = getString("inputLayerName", "input");
    protected String outputLayerName = getString("outputLayerName", "output");
    private int imageWidth=getInt("imageWidth",64);
    private int imageHeight=getInt("imageHeight",64);
    private float imageMean=getFloat("imageMean",127);
    private float imageScale=getFloat("imageScale",1);

    public AbstractDavisCNNProcessor(AEChip chip) {
        super(chip);
        String deb = "4. Debug", disp = "1. Display", anal = "2. Analysis", tf="3. Tensorflow";
        setPropertyTooltip("loadNetwork", "Load an XML or PB file containing a CNN");
        setPropertyTooltip("loadLabels", "Load labels for output units");
        setPropertyTooltip(disp, "showOutputAsBarChart", "displays activity of output units as bar chart, where height indicates activation");
        setPropertyTooltip(disp, "showKernels", "draw all the network kernels (once) in a new JFrame");
        setPropertyTooltip(disp, "toggleShowActivations", "toggle showing network activations (by default just input and output layers)");
        setPropertyTooltip(disp, "showTop5Labels", "(requires labels to be loaded) Show the top 5 classification results");
        setPropertyTooltip(disp, "showTop1Label", "(requires labels to be loaded) Show the top 1 classification result");
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
        setPropertyTooltip(tf, "makeRGBFrames", "(TensorFlow only) Tells the CNN to make RGB input from grayscale DVS/APS frames; use it with a network configured for RGB input");
        setPropertyTooltip(tf, "inputLayerName", "(TensorFlow only) Input layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "outputLayerName", "(TensorFlow only) Output layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "imageWidth", "(TensorFlow only) Input image width; the APS frames are scaled to this width in pixels");
        setPropertyTooltip(tf, "imageHeight", "(TensorFlow only) Input image height; the APS frames are scaled to this height in pixels");
        setPropertyTooltip(tf, "imageScale", "(TensorFlow only) Input image pixel value scaling; the APS frames are scaled by this value, e.g. 255 for imagenet images. The jaer units are typically 0-1 range.");
        setPropertyTooltip(tf, "imageMean", "(TensorFlow only) Input image pixel value mean; the APS frames have this mean value, typically on scale 0-255. The jaer frames typically have mean value in range 0-1.");
    }

    private File openFileDialogAndGetFile(String tip, String key, String type, String... ext) {
        File file = null;
        JFileChooser c = new JFileChooser(lastNetworkFilename);
        File f = new File(lastNetworkFilename);
        c.setCurrentDirectory(new File(getString("lastNetworkPathname", "")));
        c.setToolTipText(tip);
        FileFilter filt = new FileNameExtensionFilter(type, ext);
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(new File(lastNetworkFilename));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        lastNetworkFilename = c.getSelectedFile().toString();
        putString(key, lastNetworkFilename);
        lastNetworkPathname = f.getPath();
        putString("lastNetworkPathname", lastNetworkPathname);
        file = c.getSelectedFile();
        return file;
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    public synchronized void doLoadNetwork() {
        File file = null;
        file = openFileDialogAndGetFile("Choose a CNN network, either protobuf binary (pb) or jaer xml", "lastNetworkFilename", "CNN file", "xml", "pb");
        if (file == null) {
            return;
        }
        try {
            loadNetwork(file);
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load net from this file, caught exception " + ex + ". See console for logging.", "Bad network file", JOptionPane.WARNING_MESSAGE);
        }
    }

    public synchronized void doLoadLabels() {
        File file = null;
        file = openFileDialogAndGetFile("Choose a labels file, one label per line", "lastLabelsFilename", "labels file", "txt");
        if (file == null) {
            return;
        }
        try {
            loadLabels(file);
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
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
    //        dvsNet.loadNetwork(c.getSelectedFile());
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
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (apsDvsNet != null) {
                            setCursor(new Cursor(Cursor.WAIT_CURSOR));
                            JFrame frame = apsDvsNet.drawKernels();
                            frame.setTitle("APS net kernel weights");
                        }
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
        if (lastNetworkFilename != null && apsDvsNet == null) {
            File f = new File(lastNetworkFilename);
            if (f.exists() && f.isFile()) {
                loadNetwork(f);
                File l = new File(lastLabelsFilename);
                if (l.exists() && l.isFile()) {
                    loadLabels(l);
                }
            }
        }
    }

    private String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }

    protected void loadNetwork(File f) {
        try {
            if (f.exists() && f.isFile()) {
                switch (getExtension(f)) {
                    case "xml": // from caffe2jaer tool
                        apsDvsNet = new DavisCNNPureJava(this);
                        apsDvsNet.loadNetwork(f);
                        break;
                    case "pb": // tensorflow
                        apsDvsNet = new DavisCNNTensorFlow(this);
                        apsDvsNet.loadNetwork(f);
                        break;
                    default:
                        log.warning("unknown extension; can only read XML or pb network files");
                }
                apsDvsNet.setSoftMaxOutput(softMaxOutput); // must set manually since net doesn't know option kept here.
                apsDvsNet.setZeroPadding(zeroPadding); // must set manually since net doesn't know option kept here.
                dvsSubsampler.setFromNetwork(apsDvsNet);
            }
        } catch (IOException ex) {
            log.warning("Couldn't load the CNN from file " + f + ": got exception " + ex);
        }
    }

    protected void loadLabels(File f) {
        if (apsDvsNet == null) {
            log.warning("first load the network before loading labels");
            return;
        }
        try {
            if (f.exists() && f.isFile()) {
                apsDvsNet.loadLabels(f);
            }
        } catch (IOException ex) {
            log.warning("Couldn't load the labels from file " + f + ": got exception " + ex);
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
                    float[] outputs = apsDvsNet.processAPSFrame((AEFrameChipRenderer) (chip.getRenderer()));
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
                    apsDvsNet.processDvsFrame((DvsFrame) evt.getNewValue()); // generates PropertyChange EVENT_MADE_DECISION
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
        if (apsDvsNet != null && apsDvsNet.getNetname() != null) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.getNetname());
            if (performanceString != null /*&& !performanceString.equals(lastPerformanceString)*/) {
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
            if (apsDvsNet != null && apsDvsNet.getOutputLayer() != null) {
                apsDvsNet.getOutputLayer().drawHistogram(gl, chip.getSizeX(), chip.getSizeY(), lineWidth, Color.RED);
            }
        }

        if ((showTop1Label || showTop5Labels) && apsDvsNet.getLabels() != null
                && apsDvsNet.getLabels().size() > 0) {
            if (showTop1Label) {
                drawDecisionOutput(drawable, apsDvsNet);
            }
        }
    }

    private TextRenderer textRenderer = null;

    private void drawDecisionOutput(GLAutoDrawable drawable, AbstractDavisCNN network) {
        if(network==null || network.getOutputLayer()==null) return;
        GL2 gl = drawable.getGL().getGL2();
        int width = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();
        int top1 = network.getOutputLayer().getMaxActivatedUnit();
        if (top1 < 0 || top1 >= apsDvsNet.getLabels().size()) {
            return;
        }
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36), true, false);
        }
        float top1probability = 1f;
        top1probability = network.getOutputLayer().getMaxActivation(); // brightness scale
        textRenderer.setColor(1, 1, 1, 1);
        textRenderer.beginRendering(width, height);
        String label = apsDvsNet.getLabels().get(top1);
        String s=String.format("%s (%%%.1f)",label,top1probability*100);
        Rectangle2D r = textRenderer.getBounds(s);
        textRenderer.draw(s, (width / 2) - ((int) r.getWidth() / 2), height / 2);
        textRenderer.endRendering();
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
//    public boolean isInputClampedTo1() {
//        if (apsDvsNet == null) {
//            return false;
//        }
//        return apsDvsNet == null ? false : apsDvsNet.isInputClampedTo1();
//    }
//
//    public void setInputClampedTo1(boolean inputClampedTo1) {
//        if (apsDvsNet != null) {
//            apsDvsNet.setInputClampedTo1(inputClampedTo1);
//        }
//    }
//
//    public boolean isInputClampedToIncreasingIntegers() {
//        return apsDvsNet == null ? false : apsDvsNet.isInputClampedToIncreasingIntegers();
//    }
//
//    public void setInputClampedToIncreasingIntegers(boolean inputClampedTo1) {
//        if (apsDvsNet != null) {
//            apsDvsNet.setInputClampedToIncreasingIntegers(inputClampedTo1);
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

    /**
     * @return the makeRGBFrames
     */
    public boolean isMakeRGBFrames() {
        return makeRGBFrames;
    }

    /**
     * @param makeRGBFrames the makeRGBFrames to set
     */
    public void setMakeRGBFrames(boolean makeRGBFrames) {
        this.makeRGBFrames = makeRGBFrames;
        putBoolean("makeRGBFrames", makeRGBFrames);
    }

    /**
     * @return the inputLayerName
     */
    public String getInputLayerName() {
        return inputLayerName;
    }

    /**
     * @param inputLayerName the inputLayerName to set
     */
    public void setInputLayerName(String inputLayerName) {
        this.inputLayerName = inputLayerName;
        putString("inputLayerName", inputLayerName);
    }

    /**
     * @return the outputLayerName
     */
    public String getOutputLayerName() {
        return outputLayerName;
    }

    /**
     * @param outputLayerName the outputLayerName to set
     */
    public void setOutputLayerName(String outputLayerName) {
        this.outputLayerName = outputLayerName;
        putString("outputLayerName", outputLayerName);
    }

    /**
     * @return the imageWidth
     */
    public int getImageWidth() {
        return imageWidth;
    }

    /**
     * @param imageWidth the imageWidth to set
     */
    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
        putInt("imageWidth",imageWidth);
    }

    /**
     * @return the imageHeight
     */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * @param imageHeight the imageHeight to set
     */
    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
        putInt("imageHeight",imageHeight);
    }

    /**
     * @return the imageMean
     */
    public float getImageMean() {
        return imageMean;
    }

    /**
     * @param imageMean the imageMean to set
     */
    public void setImageMean(float imageMean) {
        this.imageMean = imageMean;
        putFloat("imageMean",imageMean);
    }

    /**
     * @return the imageScale
     */
    public float getImageScale() {
        return imageScale;
    }

    /**
     * @param imageScale the imageScale to set
     */
    public void setImageScale(float imageScale) {
        this.imageScale = imageScale;
        putFloat("imageScale", imageScale);
    }

}
