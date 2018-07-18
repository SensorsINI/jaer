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

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import ch.unizh.ini.jaer.projects.npp.DvsFramer.DvsFrame;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
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
import net.sf.jaer.eventprocessing.FilterChain;
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
@Description("Abstract super class for running CNNs from DAVIS cameras")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public abstract class AbstractDavisCNNProcessor extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    protected AbstractDavisCNN apsDvsNet = null; // new DavisCNNPureJava(); //, dvsNet = new DavisCNNPureJava();
    protected DvsFramer dvsFramer = null;
    private ApsFrameExtractor frameExtractor = null;
    protected final String KEY_NETWORK_FILENAME = "lastNetworkFilename", KEY_LABELS_FILENAME = "lastLabelsFilename", KEY_INPUTSPECIFICATION_FILENAME = "lastNetworkSpecificationFilename";
    protected String lastLabelsFilename = getString("lastLabelsFilename", "");
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
    private boolean processAPSDVSFrames = getBoolean("processAPSDVSFrames", false);
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
    private int imageWidth = getInt("imageWidth", 64);
    private int imageHeight = getInt("imageHeight", 64);
    private float imageMean = getFloat("imageMean", 0);
    private float imageScale = getFloat("imageScale", 1);
    private String lastManuallyLoadedNetwork = getString("lastManuallyLoadedNetwork", ""); // stores filename and path to last successfully loaded network that user loaded via doLoadNetwork
    private String lastManuallyLoadedLabels = getString("lastManuallyLoadedLabels", "");
    protected TextRenderer textRenderer = null;
    private AbstractDavisCNN.APSDVSFrame apsDvsFrame = null;

    public AbstractDavisCNNProcessor(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        if (chip instanceof DavisChip) {
            frameExtractor = new ApsFrameExtractor(chip);
            chain.add(frameExtractor);
            frameExtractor.getSupport().addPropertyChangeListener(this);
        }
        setEnclosedFilterChain(chain);
        String deb = "5. Debug", disp = "2. Display", anal = "3. Analysis", tf = "0. Tensorflow", input = "1. Input";
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
        setPropertyTooltip(anal, "processAPSDVSFrames", "sends 2-channel APS and DVS frame input to CNN to process each time either APS or DVS frame is updated");
        setPropertyTooltip(anal, "processAPSDVSTogetherInAPSNet", "sends APS frames and DVS time slices to single convnet");
        setPropertyTooltip(anal, "zeroPadding", "CNN uses zero padding; must be set properly according to CNN to run CNN");
        setPropertyTooltip(anal, "processingTimeLimitMs", "<html>time limit for processing packet in ms to process OF events (events still accumulate). <br> Set to 0 to disable. <p>Alternative to the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events");
        setPropertyTooltip(tf, "makeRGBFrames", "(TensorFlow only) Tells the CNN to make RGB input from grayscale DVS/APS frames; use it with a network configured for RGB input");
        setPropertyTooltip(tf, "lastManuallyLoadedNetwork", "last network we manually loaded");
        setPropertyTooltip(tf, "inputLayerName", "(TensorFlow only) Input layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "outputLayerName", "(TensorFlow only) Output layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "imageMean", "(TensorFlow only, only for APS frames) Input image pixel value mean; the APS frames have this mean value, typically on scale 0-255. The jaer frames typically have mean value in range 0-1.");
        setPropertyTooltip(input, "imageWidth", "(TensorFlow only, only for APS frames) Input image width; the APS frames are scaled to this width in pixels");
        setPropertyTooltip(input, "imageHeight", "(TensorFlow only, only for APS frames) Input image height; the APS frames are scaled to this height in pixels");
        setPropertyTooltip(input, "imageScale", "(TensorFlow only, only for APS frames) Input image pixel value scaling; the APS frames are scaled by this value, e.g. 255 for imagenet images. The jaer units are typically 0-1 range.");
        setPropertyTooltip(input, "loadInputSpecification", "Load the .yaml file that specifies the network input cropping and format (DVS/APS) etc");
        setPropertyTooltip(input, "frameCutRight", "frame cut is the pixels we cut from the original image, it follows [[top, bottom], [left, right]]");
        setPropertyTooltip(input, "frameCutLeft", "frame cut is the pixels we cut from the original image, it follows [[top, bottom], [left, right]]");
        setPropertyTooltip(input, "frameCutBottom", "frame cut is the pixels we cut from the original image, it follows [[top, bottom], [left, right]]");
        setPropertyTooltip(input, "frameCutTop", "frame cut is the pixels we cut from the original image, it follows [[top, bottom], [left, right]]");
        if (processAPSDVSFrames) { // make sure other options not set
            setProcessAPSFrames(false);
            setProcessDVSTimeSlices(false);
        }
    }

    /**
     * Opens a file (do not accept directory) using defaults and previous stored
     * preference values
     *
     * @param tip the tooltip shown
     * @param key a string key to store preference value
     * @param defaultFile the default filename
     * @param type The file type string, e.g. "labels"
     * @param ext the allowed extensions as an array of strings
     * @return the file, or null if selection was canceled
     */
    protected File openFileDialogAndGetFile(String tip, String key, String defaultFile, String type, String... ext) {
        String name = getString(key, defaultFile);
        JFileChooser c = new JFileChooser(name);
        File f = new File(name);
        c.setCurrentDirectory(new File(getString(key, "")));
        c.setToolTipText(tip);
        FileFilter filt = new FileNameExtensionFilter(type, ext);
        c.setFileSelectionMode(JFileChooser.FILES_ONLY);
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(f);
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        name = c.getSelectedFile().toString();
        putString(key, name);
        File file = c.getSelectedFile();
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
        file = openFileDialogAndGetFile("Choose a CNN network, either tensorflow protobuf binary (pb),  or folder holding tensorflow SavedModelBundle, or jaer xml",
                KEY_NETWORK_FILENAME, "",
                "CNN file", "xml", "pb");
        if (file == null) {
            return;
        }
        try {
            loadNetwork(file);
            setLastManuallyLoadedNetwork(file.toString()); // store the last manually loaded network as the 

        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load net from this file, caught exception " + ex + ". See console for logging.", "Bad network file", JOptionPane.WARNING_MESSAGE);
        }
    }

    public synchronized void doLoadInputSpecification() {
//        if (apsDvsNet == null) {
//            JOptionPane.showMessageDialog(chip.getFilterFrame(), "null CNN - load a DavisCNNTensorFlow first", "Error - no network", JOptionPane.ERROR_MESSAGE);
//            return;
//        }
//        if (!(apsDvsNet instanceof DavisCNNTensorFlow)) {
//            JOptionPane.showMessageDialog(chip.getFilterFrame(), apsDvsNet.toString() + " is not a DavisCNNTensorFlow type; cannot load layer specification for it", "Error", JOptionPane.ERROR_MESSAGE);
//            return;
//        }
        File file = null;
        file = openFileDialogAndGetFile("Choose the YAML file specifying the input cropping and input layer size for the CNN",
                KEY_INPUTSPECIFICATION_FILENAME,
                apsDvsNet != null ? apsDvsNet.getFilename() : "",
                "YAML file", "yaml");
        if (file == null) {
            return;
        }
        try {
            loadInputSpecification(file);
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load YAML " + ex + ". See console for logging.", "Bad YAML file", JOptionPane.ERROR_MESSAGE);
        }

    }

    /*
        @tobi, I've uploaded the converted tensorflow model to NAS.
        it's located at HongmingYuhuangTobiTraxxas
        and in driving_model folder
        you will see two folders in there, which are nvidia-foyer and resnet-20-foyer
        I used the SaveModelBuilder to save the model. so for each folder you will see the model definition and weights variables.
        you also gonna need to read two yaml files which list the configurations
        use resnet-20-foyer-param.yaml as example:
        model_name: "resnet-20-foyer.json"
        frame_cut: [[90, 10], [0, 1]]
        img_shape: [180, 240]
        target_size: [30, 90]
        clip_value: 8
        mode: 0
        the frame cut is the pixels we cut from the original image, it follows [[top, down], [left, right]]
        for top, it means 90 pixels are cut from the top
        for bottom, it means 10 pixels are cut from the bottom
        we resize to target_size after cutting
        the clip value is to tell you that the largest value in DVS channel is 8*2=16
        and the mode 0 means DVS only. 1 is APS, 2 means DVS+APS
        you will need to resize the model to appropriate inputs so that the model can be run properly.
        in the same folder, Hong Ming will upload the test bag dataset so that you can play with it.
        you will need to subscribe /dvs_bind topic to get the input image.
         /dvs_bind publish an RGB image where the first channel is DVS image and the second channel is the APS image.
        i hope this can help you import the models to jAER.
     */
    public void loadInputSpecification(File yamlFile) throws FileNotFoundException, YamlException {
        if (!yamlFile.exists()) {
            throw new FileNotFoundException("cannot find the yaml file " + yamlFile);
        }
        YamlReader reader = new YamlReader(new FileReader(yamlFile));
        /* # PATHS ARE USED in each nodes, will be used via launch file under `rosparam load iyaml_file`
                    model_name: "resnet-20-foyer.json"
                    frame_cut: [[90, 10], [0, 1]]
                    img_shape: [180, 240]
                    target_size: [30, 90]
                    clip_value: 8
                    mode: 0 //mode 0 means DVS only. 1 is APS, 2 means DVS+APS
         */
        Object object = reader.read();
        log.info("yaml network input specification:\n" + object.toString() + "; setting DvsFramer from these specs");
        Map map = (Map) object;
        try {
            ArrayList frame_cut = (ArrayList) map.get("frame_cut");
            ArrayList<String> sublist;
            sublist = (ArrayList<String>) frame_cut.get(0);
            dvsFramer.setFrameCutBottom(Integer.parseInt(sublist.get(1)));
            dvsFramer.setFrameCutTop(Integer.parseInt(sublist.get(0)));
            sublist = (ArrayList<String>) frame_cut.get(1);
            dvsFramer.setFrameCutLeft(Integer.parseInt(sublist.get(0)));
            dvsFramer.setFrameCutRight(Integer.parseInt(sublist.get(1)));
        } catch (Exception e) {
            throw new YamlException("frame_cut parsing error " + e);
        }
        ArrayList<String> img_shape = (ArrayList<String>) map.get("img_shape");
        try {
            ArrayList<String> target_size = (ArrayList<String>) map.get("target_size");
            dvsFramer.setOutputImageHeight(Integer.parseInt(target_size.get(0)));
            dvsFramer.setOutputImageWidth(Integer.parseInt(target_size.get(1)));
        } catch (Exception e) {
            throw new YamlException("img_shape parsing error " + e);
        }
        try {
            String clip_value = (String) map.get("clip_value");
            dvsFramer.setDvsGrayScale(2 * Integer.parseInt(clip_value));
        } catch (Exception e) {
            throw new YamlException("clip_value parsing error " + e);
        }
        try {
            String mode = (String) map.get("mode");
            switch (mode) {
                case "0":
                    setProcessDVSTimeSlices(true);
                    setProcessAPSFrames(false);
                    break;
                case "1":
                    setProcessDVSTimeSlices(false);
                    setProcessAPSFrames(true);
                    break;
                case "2":
                    setProcessDVSTimeSlices(true);
                    setProcessAPSFrames(true);
                    break;
            }
        } catch (Exception e) {
            throw new YamlException("mode parsing error " + e);
        }
        log.info("set dvsFramer=" + dvsFramer);

    }

    public synchronized void doLoadLabels() {
        File file = null;
        file = openFileDialogAndGetFile("Choose a labels file, one label per line", KEY_LABELS_FILENAME, "", "labelsFile.txt", "txt");
        if (file == null) {
            return;
        }
        try {
            loadLabels(file);
            lastManuallyLoadedLabels = file.toString();
            putString("lastManuallyLoadedLabels", lastManuallyLoadedLabels);
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
    //        dvsFramer = new DvsFramer(dvsNet.inputLayer.dimx, dvsNet.inputLayer.dimy, getDvsColorScale());
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
            if (dvsFramer == null) {
                throw new RuntimeException("Null dvsSubsampler; this should not occur");
            } else {
                dvsFramer.getSupport().addPropertyChangeListener(DvsFramer.EVENT_NEW_FRAME_AVAILABLE, this);
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
            if (dvsFramer != null) {
                dvsFramer.addEvent(p); // generates event when full, which processes it in propertyChange() which computes CNN
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
        if (dvsFramer != null) {
            dvsFramer.resetFilter();
        }
    }

    @Override
    public void initFilter() {
        // if apsDvsNet was loaded before, load it now
        if (preferenceExists(KEY_NETWORK_FILENAME) && apsDvsNet == null) {
            File f = new File(getString(KEY_NETWORK_FILENAME, ""));
            if (f.exists() && f.isFile()) {
                try {
                    loadNetwork(f);
                } catch (Exception ex) {
                    Logger.getLogger(AbstractDavisCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (preferenceExists(KEY_LABELS_FILENAME)) {
                    File l = new File(getString(KEY_LABELS_FILENAME, ""));
                    if (l.exists() && l.isFile()) {
                        try {
                            loadLabels(l);
                        } catch (IOException ex) {
                            Logger.getLogger(AbstractDavisCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            if (preferenceExists(KEY_INPUTSPECIFICATION_FILENAME)) {
                File f2 = new File(getString(KEY_INPUTSPECIFICATION_FILENAME, ""));
                if (f2.exists() && f2.isFile()) {
                    try {
                        loadInputSpecification(f2);
                    } catch (FileNotFoundException ex) {
                        log.warning(ex.toString());
                    } catch (YamlException ex) {
                        log.warning(ex.toString());
                    }
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

    protected void loadNetwork(File f) throws Exception {
        try {
            if (f.exists()) {
                if (f.isFile()) {
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
                } else if (f.isDirectory()) { // load from SavedModelBundle tensorflow net with yaml input specification file
                    apsDvsNet = new DavisCNNTensorFlow(this);
                    apsDvsNet.loadNetwork(f);
                }
                apsDvsNet.setSoftMaxOutput(softMaxOutput); // must set manually since net doesn't know option kept here.
                apsDvsNet.setZeroPadding(zeroPadding); // must set manually since net doesn't know option kept here.
                dvsFramer.setFromNetwork(apsDvsNet);
            } else {
                log.warning("file " + f + " does not exist");
                throw new IOException("file " + f + " does not exist");
            }
        } catch (IOException ex) {
            throw new IOException("Couldn't load the CNN from file " + f, ex);
        }
    }

    protected void loadLabels(File f) throws IOException {
        if (apsDvsNet == null) {
            log.warning("first load the network before loading labels");
            return;
        }
        try {
            if (f.exists() && f.isFile()) {
                apsDvsNet.loadLabels(f);
            }
        } catch (IOException ex) {
            throw new IOException("Couldn't load the labels from file " + f, ex);
        }
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (!isFilterEnabled()) {
            return;
        }
        // new activationsFrame is available, process it
        switch (evt.getPropertyName()) {
            case ApsFrameExtractor.EVENT_NEW_FRAME:
                if (timeLimiter.isTimedOut()) {
                    log.warning("skipped this APS frame because " + timeLimiter.toString());
                    return; // don't process this frame
                }
                if (isFilterEnabled() && (apsDvsNet != null) && (processAPSDVSFrames)) {
                    long startTime = 0;
                    if (measurePerformance) {
                        startTime = System.nanoTime();
                    }

                    try{
                    updateAPSDVSFrame(frameExtractor);
                    float[] outputs = apsDvsNet.processAPSDVSFrame(apsDvsFrame);  // TODO replace with ApsFrameExtractor
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps);
                    }
                    }catch(Exception e){
                        log.log(Level.SEVERE,e.toString(),e); // TODO debug
                    }
                } else if (isFilterEnabled() && (apsDvsNet != null) && (processAPSFrames)) {
                    long startTime = 0;
                    if (measurePerformance) {
                        startTime = System.nanoTime();
                    }
                    float[] outputs = apsDvsNet.processAPSFrame(frameExtractor);  // TODO replace with ApsFrameExtractor
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps);
                    }
                }
                break;
            case DvsFramer.EVENT_NEW_FRAME_AVAILABLE:
                long startTime = 0;
                if (measurePerformance) {
                    startTime = System.nanoTime();
                }
                if (processAPSDVSFrames && apsDvsNet != null) {
                     try{  // TODO debug
                   updateApsDvsFrame((DvsFrame) evt.getNewValue());
                    apsDvsNet.processAPSDVSFrame(apsDvsFrame); // generates PropertyChange EVENT_MADE_DECISION
                    }catch(Exception e){
                        log.log(Level.SEVERE,e.toString(),e); // TODO debug
                    }
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps);
                    }
                } else if (processDVSTimeSlices && apsDvsNet != null) {
                    apsDvsNet.processDvsFrame((DvsFrame) evt.getNewValue()); // generates PropertyChange EVENT_MADE_DECISION
                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        performanceString = String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps);
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
            if (measurePerformance && performanceString != null /*&& !performanceString.equals(lastPerformanceString)*/) {
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

        if (apsDvsNet != null && (showTop1Label || showTop5Labels) && apsDvsNet.getLabels() != null
                && apsDvsNet.getLabels().size() > 0) {
            if (showTop1Label) {
                drawDecisionOutput(drawable, apsDvsNet);
            }
        }
    }

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

    protected void drawDecisionOutput(GLAutoDrawable drawable, AbstractDavisCNN network) {
        if (network == null || network.getOutputLayer() == null) {
            return;
        }
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
        String s = String.format("%s (%%%.1f)", label, top1probability * 100);
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
        if (processDVSTimeSlices) {
            setProcessAPSDVSFrames(false);
        }
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
        if (processAPSFrames) {
            setProcessAPSDVSFrames(false);
        }
    }

    /**
     * @return the processAPSDVSFrames
     */
    public boolean isProcessAPSDVSFrames() {
        return processAPSDVSFrames;
    }

    /**
     * @param processAPSDVSFrames the processAPSDVSFrames to set
     */
    public void setProcessAPSDVSFrames(boolean processAPSDVSFrames) {
        boolean old = this.processAPSDVSFrames;
        this.processAPSDVSFrames = processAPSDVSFrames;
        putBoolean("processAPSDVSFrames", processAPSDVSFrames);
        getSupport().firePropertyChange("processAPSDVSFrames", old, processAPSDVSFrames);
        if (processAPSDVSFrames) {
            if (isProcessAPSFrames()) {
                setProcessAPSFrames(false);
            }
            if (isProcessDVSTimeSlices()) {
                setProcessDVSTimeSlices(false);
            }
        }
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
        boolean old = this.softMaxOutput;
        this.softMaxOutput = softMaxOutput;
        putBoolean("softMaxOutput", softMaxOutput);
        if (apsDvsNet == null) {
            return;
        }
        apsDvsNet.setSoftMaxOutput(softMaxOutput);
        getSupport().firePropertyChange("softMaxOutput", old, softMaxOutput); //update GUI
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
        putInt("imageWidth", imageWidth);
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
        putInt("imageHeight", imageHeight);
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
        putFloat("imageMean", imageMean);
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

    /**
     * @return the lastManuallyLoadedNetwork
     */
    public String getLastManuallyLoadedNetwork() {
        return lastManuallyLoadedNetwork;
    }

    /**
     * @param lastManuallyLoadedNetwork the lastManuallyLoadedNetwork to set
     */
    public void setLastManuallyLoadedNetwork(String lastManuallyLoadedNetwork) {
        this.lastManuallyLoadedNetwork = lastManuallyLoadedNetwork;
        putString("lastManuallyLoadedNetwork", lastManuallyLoadedNetwork);
    }

    private void updateAPSDVSFrame(ApsFrameExtractor frameExtractor) {
        checkApsDvsFrame();
        int srcwidth = chip.getSizeX(), srcheight = chip.getSizeY(), targetwidth = dvsFramer.getOutputImageWidth(), targetheight = dvsFramer.getOutputImageHeight();
        float[] frame = frameExtractor.getNewFrame();
        for (int y = 0; y < targetheight; y++) {
            for (int x = 0; x < targetwidth; x++) {
                int xsrc = (int) Math.floor(x * (float) srcwidth / targetwidth), ysrc = (int) Math.floor(y * (float) srcheight / targetheight);
                float v = frame[frameExtractor.getIndex(xsrc, ysrc)];
                apsDvsFrame.setValue(0, x, y, v);
            }
        }
    }

    private void updateApsDvsFrame(DvsFrame dvsFrame) {
        checkApsDvsFrame();
        int targetwidth = dvsFramer.getOutputImageWidth(), targetheight = dvsFramer.getOutputImageHeight();
        for (int y = 0; y < targetheight; y++) {
            for (int x = 0; x < targetwidth; x++) {
                float v = dvsFrame.getValueAtPixel(x, y);
                apsDvsFrame.setValue(1, x, y, v);
            }
        }
    }

    private void checkApsDvsFrame() {
        if (apsDvsFrame == null || apsDvsFrame.getHeight() != dvsFramer.getOutputImageHeight() || apsDvsFrame.getWidth() != dvsFramer.getOutputImageWidth()) {
            apsDvsFrame = new AbstractDavisCNN.APSDVSFrame(dvsFramer.getOutputImageWidth(), dvsFramer.getOutputImageHeight());
        }
    }

    /**
     * @return the lastManuallyLoadedLabels
     */
    public String getLastManuallyLoadedLabels() {
        return lastManuallyLoadedLabels;
    }

}
