/*
 * Copyright (C) 2021 tobid.
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
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.TimeLimiter;
import net.sf.jaer.graphics.ImageDisplay;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.tensorflow.Graph;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import com.github.sh0nk.matplotlib4j.Plot;
import com.google.common.primitives.Doubles;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import net.sf.jaer.Preferred;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.RemoteControlCommand;
import org.apache.commons.text.WordUtils;
import org.tensorflow.ConcreteFunction;
import org.tensorflow.GraphOperation;
import org.tensorflow.Result;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.FloatDataBuffer;
import org.tensorflow.proto.GraphDef;
import org.tensorflow.proto.SignatureDef;
import org.tensorflow.proto.TensorInfo;
import org.tensorflow.proto.TensorShapeProto;
import org.tensorflow.proto.TensorShapeProto.Dim;
import org.tensorflow.types.TFloat32;

/**
 * Noise filter that runs a DNN to denoise events
 *
 * @author Tobi Delbruck and Shasha Guo, 2021
 */
@Description("<html>Denoising noise filter that uses an MLP neural network to classify events as signal or noise events. Published in Guo & Delbruck, T-PAMI 2022 <a href=\"http://dx.doi.org/10.1109/TPAMI.2022.3152999\">10.1109/TPAMI.2022.3152999</a>")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MLPNoiseFilter extends AbstractNoiseFilter implements MouseListener, MouseMotionListener, MouseWheelListener {

    public final String DEFAULT_MLPF_NETWORK_FOLDER = "MLPF_2xMSEO1H20_linear_7";
    private final String KEY_NETWORK_FILENAME = "lastNetworkFilename";
    private String lastManuallyLoadedNetwork = getString("lastManuallyLoadedNetwork", ""); // stores filename and path to last successfully loaded network that user loaded via doLoadNetwork
    private TextRenderer textRenderer = null;
//    private boolean measurePerformance = getBoolean("measurePerformance", true);

    private String performanceString = null; // holds string representation of processing time
    private TimeLimiter timeLimiter = new TimeLimiter(); // private instance used to accumulate events to slices even if packet has timed out
    private int processingTimeLimitMs = getInt("processingTimeLimitMs", 100); // time limit for processing packet in ms to process OF events (events still accumulate). Overrides the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events.
    private String lastPerformanceString = null;

    private int sxm1; // size of chip minus 1
    private int sym1;
    private int ssx; // size of subsampled timestamp map
    private int ssy;

    private SavedModelBundle tfSavedModelBundle = null;
    private Graph tfExecutionGraph = null;
    private Session tfSession = null;
    @Preferred
    private int tfBatchSizeEvents = getInt("tfBatchSizeEvents", 1024);
    private int tfNumInBatchSoFar = 0;
    private FloatDataBuffer tfInputFloatBuffer = null;
    private int tfFloatBufferIdx = 0; // index into the FloatDataBuffer
    private String tfInputName = "input", tfOutputName = "output/Sigmoid"; // modified from SavedModelBundle.MetaGraphDef if loaded from TF exported model folder

    private ArrayList<BasicEvent> eventList = new ArrayList(tfBatchSizeEvents);
    @Preferred
    protected float signalClassifierThreshold = getFloat("signalClassifierThreshold", 0.5f);

    // plotting TI patches and stats
    private DescriptiveStatistics stats = null;
    private volatile boolean showThisTiPatch = false;
    // TI patch display
    private ImageDisplay tiPatchDisplay = null;
    private JFrame tiFrame = null;
    private BasicEvent eventToDisplayTIPatchFor = null;
    private EngineeringFormat eng = new EngineeringFormat();
    private int STATS_NUM_EVENTS = 1_000_000;

    public enum TIPatchMethod {
        ExponentialDecay, LinearDecay // default is LinearDecay since it works better (and is faster and cheaper)
    };
    private TIPatchMethod tiPatchMethod = TIPatchMethod.valueOf(getString("tiPatchMethod", TIPatchMethod.LinearDecay.toString()));

    private int patchWidthAndHeightPixels = getInt("patchWidthAndHeightPixels", 7);
    private int[][] timestampImage; // timestamp image
    private byte[][] lastPolMap; // last polarity image, contains 0 for not initialized, 1 for ON, -1 for OFF events
//    private boolean useTI = getBoolean("useTI", true);
//    private boolean usePolarity = getBoolean("usePolarity", false);
//    @Preferred
//    private boolean useTIandPol = getBoolean("useTIandPol", false);

    private boolean showOnlySignalTimeimages = getBoolean("showOnlySignalTimeimages", false);
    private boolean showOnlyNoiseTimeimages = getBoolean("showOnlyNoiseTimeimages", false);
    protected boolean saveTiPatchImages = false;
    private int tiFileCounter = 0;
    private File saveDir = null;
    private final int NUM_INPUTS_PER_TPI_PIXEL = 2;  // set to 1 for not using polarity, 2 for using polarity channel

    private final int NONONOTONIC_TIMESTAMP_WARNING_INTERVAL = 100000;
    private int nonmonotonicWarningCount = 0;

    /**
     * Cursor size for drawn mouse cursor when filter is selected.
     */
    protected final float CURSOR_SIZE_CHIP_PIXELS = 7;
    protected GLU glu = new GLU();
    protected GLUquadric quad = null;
    private boolean hasBlendChecked = false, hasBlend = false;
    protected boolean showCrossHairCursor = true;
    protected GLCanvas glCanvas;
    protected ChipCanvas chipCanvas;
    float[] cursorColor = null;

    /**
     * Flag that freezes ROI selection
     */
    protected boolean freezeRoi = getBoolean("freezeRoi", false);

    // roiRect stuff
    /**
     * ROI start/end corner index
     */
    protected int roiStartx, roiStarty, roiEndx, roiEndy;
    /**
     * ROI start/end corners and last clicked mouse point
     */
    protected Point roiStartPoint = null, roiEndPoint = null, clickedPoint = null;
    /**
     * ROI rectangle
     */
    protected Rectangle roiRect = (Rectangle) getObject("roiRect", null);

    /**
     * Boolean that indicates ROI is being selected currently
     */
    protected volatile boolean roiSelecting = false;
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};

    /**
     * The current mouse point in chip pixels, updated by mouseMoved
     */
    protected Point currentMousePoint = null;

    public MLPNoiseFilter(AEChip chip) {
        super(chip);
        String deb = "5. Debug", disp = "2. Display", anal = "4. Analysis", tf = "0. Multilayer Perceptron", input = "1. Input";
        setPropertyTooltip(tf, "loadNetwork", "Load a protobuf .pb file containing the network or select a folder holding SavedModelBundle");
        setPropertyTooltip(tf, "lastManuallyLoadedNetwork", "Last network we manually loaded");
        setPropertyTooltip(disp, "measurePerformance", "Measures and logs time in ms to process each frame along with estimated operations count (MAC=2OPS)");
        setPropertyTooltip(tf, "inputLayerName", "(TensorFlow only) Input layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "outputLayerName", "(TensorFlow only) Output layer; parse it from loading the network and examining console output for layers for lines starting with ****");
        setPropertyTooltip(tf, "tfBatchSizeEvents", "Number of events to process in parallel for inference");
        setPropertyTooltip(tf, "patchWidthAndHeightPixels", "<html>Dimension s<sub>MLPF</sub> (width and height in pixels) of the timestamp image input to DNN around each event (default 11)"); // TODO fix default to match training
        setPropertyTooltip(tf, "signalClassifierThreshold", "<html>Threshold T<sub>MLPF</sub>  for clasifying event as signal"); // TODO fix default to match training
        setPropertyTooltip(tf, "useTI", "use TI only as input of MLP"); // TODO fix default to match training
        setPropertyTooltip(tf, "usePolarity", "use Polarity only as input of MLP"); // TODO fix default to match training
        setPropertyTooltip(tf, "useTIandPol", "use both TI and Polarity as input of MLP"); // TODO fix default to match training
        setPropertyTooltip(tf, "correlationTimeS", "<html>Window of time tau in seconds that the timestamp image counts past events; <br>pixels with older events are set to zero.<p> Windows within window are linearly or exponentially decayed to zero as dt approaches timeWindowS<<p>Typically trained at 100ms, but shorter values can result in more accurate denoising.");

        setPropertyTooltip(tf, "tiPatchMethod", "Method used to compute the value of the timestamp image patch values");
        setPropertyTooltip(disp, "showClassificationHistogram", "Shows a histogram of classification results for last " + STATS_NUM_EVENTS + " events, should be bowl shaped for well-behaved denoising");
        setPropertyTooltip(disp, "showTimeimagePatch", "Shows a window with timestamp image input to MLP");
        setPropertyTooltip(disp, "showOnlyNoiseTimeimages", "Shows TPI input to MLP only for noise classifications in the selected ROI");
        setPropertyTooltip(disp, "showOnlySignalTimeimages", "Shows TPI input to MLP only for signal classifications in the selected ROI");
        setPropertyTooltip(disp, "saveTiPatchImages", "Saves the TPI (timestamp polarity image) patch images to a folder named MLPNoiseFilter-signal or MLPNoiseFilter-noise");
        String roi = "Region of interest";
        setPropertyTooltip(roi, "freezeRoi", "Freezes ROI (region of interest) selection for displaying the TPI patches of signal and noise events");
        setPropertyTooltip(roi, "clearROI", "Clears ROI (region of interest) selection for displaying the TPI patches of signal and noise events");
        hideProperty("antiCasualEnabled");
        hideProperty("sigmaDistPixels");
        hideProperty("adaptiveFilteringEnabled");
        removeNoiseFilterControl();
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (this.tfExecutionGraph == null) {
            showWarningDialogInSwingThread("<html>MLPNoiseFilter: No network loaded yet.<p>Use <i>Load network</i> button to load a .pb file and then reenable MLPNoiseFilter", "No MLP");
            setFilterEnabled(false);
            return in;
        }
        super.filterPacket(in);
        if (timestampImage == null) {
            allocateMaps(chip);
        }
        int tauUs = (int) Math.round(correlationTimeS * 1e6f);
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;

        eventToDisplayTIPatchFor = null;
        if (tiPatchDisplay != null) {
            tiPatchDisplay.clearImage();
        }
        showThisTiPatch = false;
        ArrayList<PolarityEvent> inList;
        try {
            inList = createEventList((EventPacket<PolarityEvent>) in);
        } catch (Exception ex) {
            log.warning(String.format("%s: skipping nonmonotonic packet [%s]", ex, in));
            return in;
        }

        for (PolarityEvent e : inList) {

            if (e == null) {
                continue;
            }
//            if (e.isSpecial()) {   // special events are labeled as noise events by v2e
//                continue;
//            }
            totalEventCount++;
            final int ts = e.timestamp;
            final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy); // subsampling address
            if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) { // out of bounds, discard (maybe bad USB or something)
                filterOut(e);
                continue;
            }
            if (timestampImage[x][y] == DEFAULT_TIMESTAMP) { // if it is the first event at this pixel
                timestampImage[x][y] = ts; // save timestamp
                if (letFirstEventThrough) {
                    filterIn(e); // if we let through every first event at each pixel, filter it in
                    continue;
                } else {
                    filterOut(e); // otherwise filter out
                    continue;
                }
            }
//            timestampImage[x][y] = ts;
            // make timestamp image patch to classify
            int radius = (patchWidthAndHeightPixels - 1) / 2;
            tfNumInBatchSoFar++;
            eventList.add(e); // add the event object to list so we can later filter it in or not, in the original order
            // see if we display this particular TI patch, only 1 per packet
            if (eventToDisplayTIPatchFor == null && insideRoi(e)) {
                eventToDisplayTIPatchFor = e;
            }
            for (int indx = x - radius; indx <= x + radius; indx++) {
                // iterate over NNb, computing the TI patch value
                for (int indy = y - radius; indy <= y + radius; indy++) {
                    if (indx < 0 || indx >= ssx || indy < 0 || indy > ssy) {
                        putInputFloat(0);
                        continue;
                    }
                    int nnbTs = timestampImage[indx][indy]; // NNb timestamp 
                    if (nnbTs == DEFAULT_TIMESTAMP) {
                        putInputFloat(0); // if the NNb pixel had no event, then just write 0 to TI patch
                    } else {
                        int dt = nnbTs - ts; // dt is negative delta time, i.e. the time in us of NNb event relative to us.  When NNb ts is older, dt is more negative
                        float v = 0; // value put into TI patch
                        if (dt <= 0) { // ok, NNb pixel timestamp is older than us
                            switch (tiPatchMethod) {
                                case ExponentialDecay:
//                                float expDt = (float) Math.exp(dt / tauUs);  // Compute exp(-dt/tau) that decays to zero for very old events in NNb
                                    v = fastexp((float) dt / tauUs);  // Compute exp(-dt/tau) that decays to zero for very old events in NNb
                                    break;
                                case LinearDecay:

                                    if (-dt < tauUs) {
                                        v = 1 - ((float) (-dt)) / tauUs;  // if dt is 0, then linearDt is 1, if dt=-tauUs, then linearDt=0
                                    }
                            }
//                            if (indx-x == 0 && indy-y == 0) {
//                                log.info(String.format("dt=%d v=%.2f", dt, v));
//                            }
                        } else {  // if dt>0 then time was nonmonotonic, ignore this pixel
                            if (nonmonotonicWarningCount++ % NONONOTONIC_TIMESTAMP_WARNING_INTERVAL == 0) {
                                log.warning(String.format("timestamp in patch in future by %ss", eng.format(1e-6f * dt)));
                            }
                        }
                        putInputFloat(v);
                        if (tiPatchDisplay != null && eventToDisplayTIPatchFor != null && e == eventToDisplayTIPatchFor) {
                            if (false) { // old test with only age
                                tiPatchDisplay.setPixmapGray(indx + radius - x, indy + radius - y, v); // shift back to 0,0 coordinate at LL
                            } else { //assume use polarity too
                                int p = lastPolMap[indx][indy];
                                if (indx == x && indy == y) {
                                    p = e.getPolaritySignum(); // center pixel should always be polarity of this event
                                }
                                if (p == 0) {
                                    tiPatchDisplay.setPixmapGray(indx + radius - x, indy + radius - y, 0); // shift back to 0,0 coordinate at LL
                                } else if (p > 0) {
                                    tiPatchDisplay.setPixmapRGB(indx + radius - x, indy + radius - y, 0, v, 0); // shift back to 0,0 coordinate at LL
                                } else {
                                    tiPatchDisplay.setPixmapRGB(indx + radius - x, indy + radius - y, v, 0, 0); // shift back to 0,0 coordinate at LL
                                }
                            }
                        }
                    }
                }
            }
            byte pol = (byte) e.getPolaritySignum(); // -1 OFF, +1 ON
            lastPolMap[x][y] = pol;
            for (int indx = x - radius; indx <= x + radius; indx++) {
                // iterate over NNb, computing the TI patch value
                for (int indy = y - radius; indy <= y + radius; indy++) {
                    if (indx < 0 || indx >= ssx || indy < 0 || indy > ssy) {
                        putInputFloat(0);// For NNbs that are outside chip address space, set the TI patch input to zero
                        continue;
                    }
                    if (indx == x && indy == y) {
                        putInputFloat(pol);
                        continue;
                    }

                    int p = lastPolMap[indx][indy];
                    int nnbTs = timestampImage[indx][indy];
                    if (nnbTs == DEFAULT_TIMESTAMP) {
                        p = 0; // if the NNb pixel had no event, then just write 0 to TI patch
                    } else {
                        int dt = nnbTs - ts; // dt is negative delta time, i.e. the time in us of NNb event relative to us.  When NNb ts is older, dt is more negative

                        if (-dt > tauUs) {
                            p = 0;
                        }
                    }
                    putInputFloat(p); // if the NNb pixel had no event, then just write 0 to TI patch
                }
            }

            if (tfNumInBatchSoFar >= tfBatchSizeEvents) { // if we have a full batch, classify the events in it
                classifyEvents();
            }
            // write TI *after* we classify S vs N
            timestampImage[x][y] = ts;

        } // event packet loop

        if (tfFloatBufferIdx < tfNumInBatchSoFar * NUM_INPUTS_PER_TPI_PIXEL * patchWidthAndHeightPixels * patchWidthAndHeightPixels) {
            log.warning("something is wrong in filling float buffer with events");

        }

        // classify remaining events as final batch for this packet, so that we process all events in packet even if only a few
        if (tfNumInBatchSoFar > 0) {
            classifyEvents();
        }

        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    private void putInputFloat(float v) {
        tfInputFloatBuffer.setFloat(v, tfFloatBufferIdx++); // For NNbs that are outside chip address space, set the TI patch input to zero
    }

    private void clearInputBuffer() {
        tfFloatBufferIdx = 0;
    }

//    private class BackwardsTimestampException extends Exception {
//
//	public BackwardsTimestampException(String string) {
//	    super(string);
//	}
//
//    }
    private ArrayList<PolarityEvent> createEventList(EventPacket<PolarityEvent> p) throws Exception {
        ArrayList<PolarityEvent> l = new ArrayList(p.getSize());
        PolarityEvent pe = null;
        for (PolarityEvent e : p) {
            if (pe != null && (e.timestamp < pe.timestamp)) {
                int dt = e.timestamp - pe.timestamp;
                throw new Exception(String.format("timestamp %,d is %,d us earlier than previous %,d", e.timestamp, dt, pe.timestamp));
            }
            l.add(e);
            pe = e;
        }
        return l;
    }

    private ArrayList<PolarityEvent> createEventList(List<PolarityEvent> p) throws Exception {
        ArrayList<PolarityEvent> l = new ArrayList(p.size());
        PolarityEvent pe = null;
        for (PolarityEvent e : p) {
            if (pe != null && (e.timestamp < pe.timestamp)) {
                throw new Exception(String.format("timestamp %d is earlier than previous %d", e.timestamp, pe.timestamp));
            }
            l.add(e);
            pe = e;
        }
        return l;
    }

    /**
     * Checks the FloatBuffer used to hold input vector to MLP to make sure it
     * is the correct size
     */
    private void checkMlpInputFloatBufferSize() {
        final int bufsize = tfBatchSizeEvents * NUM_INPUTS_PER_TPI_PIXEL * patchWidthAndHeightPixels * patchWidthAndHeightPixels;
        if (tfInputFloatBuffer == null || tfInputFloatBuffer.size() != bufsize) {
            log.info(String.format("resizing network input float buffer for tfBatchSizeEvents * inputSF * patchWidthAndHeightPixels * patchWidthAndHeightPixels = %d x %d x %d x %d",
                    tfBatchSizeEvents, NUM_INPUTS_PER_TPI_PIXEL, patchWidthAndHeightPixels, patchWidthAndHeightPixels));
            tfInputFloatBuffer = DataBuffers.ofFloats(bufsize);
        }
    }

    /**
     * Classifies events in the assembled batch
     *
     */
    synchronized private void classifyEvents() {
        // Create input tensor with channel first. Each event's input TI patch is a vector arranged according to for loop order above,
        // i.e. y last order, with y index changing fastest.
//        tfInputFloatBuffer.flip();
        try (TFloat32 tfInputTensor = TFloat32.tensorOf(Shape.of(tfNumInBatchSoFar, NUM_INPUTS_PER_TPI_PIXEL * patchWidthAndHeightPixels * patchWidthAndHeightPixels), tfInputFloatBuffer)) {
            Result tfOutputs = null;
            if (this.tfSavedModelBundle == null && this.tfExecutionGraph != null) {
                if (tfSession == null) {
                    tfSession = new Session(tfExecutionGraph);
                }
                tfOutputs = tfSession.runner().feed(tfInputName, tfInputTensor).fetch(tfOutputName).run();
            } else {
                HashMap<String, Tensor> inputMap = new HashMap();
                inputMap.put("input", tfInputTensor);
                tfOutputs = tfSavedModelBundle.call(inputMap);
            }
            float[] outputVector;
            try (Tensor tfOutput = tfOutputs.get(0)) {
                final Shape rshape = tfOutput.shape();
                if (rshape.numDimensions() != 2 || rshape.get(0) != tfNumInBatchSoFar || rshape.get(1) != 1) {
                    throw new RuntimeException(
                            String.format(
                                    "Expected model to produce a [N 1] shaped tensor where N is the tfBatchSizeEvents, instead it produced one with shape %s",
                                    rshape.toString()));
                }
                outputVector = new float[tfNumInBatchSoFar];
                FloatDataBuffer fb = DataBuffers.of(outputVector, false, false);
                TFloat32 tfloat = (TFloat32) tfOutput;
                tfloat.copyTo(fb);
                tfOutput.close();
            }

            int idx = 0;
            for (BasicEvent ev : eventList) {
                float scalarClassification = outputVector[idx];
                stats.addValue(scalarClassification);
                final boolean signalEvent = scalarClassification > signalClassifierThreshold;
                if (signalEvent) {
                    filterIn(ev);
                } else {
                    filterOut(ev);
                }
                if (tiPatchDisplay != null && eventToDisplayTIPatchFor == ev) {
                    tiPatchDisplay.setTitleLabel(String.format("C=%s (%s)", eng.format(scalarClassification), scalarClassification > signalClassifierThreshold ? "Signal" : "Noise"));
                    if ((!showOnlySignalTimeimages && !showOnlyNoiseTimeimages)
                            || (showOnlyNoiseTimeimages && !signalEvent)
                            || (showOnlySignalTimeimages && signalEvent)) {
                        showThisTiPatch = true;
                    }
                }
                idx++;
            }
//            tfInputFloatBuffer.clear();
            eventList.clear();
            tfNumInBatchSoFar = 0;
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Exception running network: " + ex.toString(), ex.getCause());
            if (tfSession != null) {
                tfSession.close();
                tfSession = null;
                setFilterEnabled(false);
                resetFilter();
            }
        } finally {
            clearInputBuffer();
        }
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        closeTFResources();
    }

    private String USAGE = "MLPFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx thr xx\n";

    // remote control for experiments e.g. with python / UDP remote control 
    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");

        if (tok.length < 3) {
            return USAGE;
        }
        try {

            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("dt")) {
                        setCorrelationTimeS(1e-6f * Integer.parseInt(tok[i + 1]));
                    } else if (tok[i].equals("thr")) {
                        setSignalClassifierThreshold(Float.parseFloat(tok[i + 1]));
                    }
                }
                String out = "successfully set MLPFilter parameters time window tau=" + String.valueOf(correlationTimeS) + " and threshold T_MLPF " + String.valueOf(signalClassifierThreshold);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol" + e.toString() + "\n";
        }
    }

    @Override
    public String infoString() {
        String s = getClass().getSimpleName();
        s = s.replaceAll("[a-z]", "");
        s = s + String.format(": tau=%ss s_MLPF=%dx%dpx T_MLPF=%.2f subSamp=%d",
                eng.format(correlationTimeS),
                patchWidthAndHeightPixels, patchWidthAndHeightPixels,
                getSignalClassifierThreshold(),
                getSubsampleBy()
        );
        return s;
    }

    @Override
    public void initFilter() {
        // if dnn was loaded before, load it now
        if (preferenceExists(KEY_NETWORK_FILENAME) && tfExecutionGraph == null) {
            File f = new File(getString(KEY_NETWORK_FILENAME, getDefaultSettingsFolder()));
            if (f.exists()/* && f.isFile()*/) {  // file or folder ok here now
                try {
                    loadNetwork(f);
                } catch (Exception ex) {
                    log.warning("Couldn't load network: Caught " + ex.toString());
                }
            } else {
                log.warning("MLPNoiseFilter: MLP weights in \n    " + f.getAbsolutePath() + "\n are not available.\nUse Load network button to load a set of weights.");
            }
        }
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        buildexptable(-10, 0, .01f);// interpolation for exp approximation
        checkMlpInputFloatBufferSize();
        allocateMaps(chip);
        resetFilter();

    }

    @Override
    public synchronized final void resetFilter() {
        super.resetFilter();
        if (timestampImage == null) {
            log.warning("tried to clear lastTimesMap but it is null");
            return;
        }
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
        for (byte[] arrayRow : lastPolMap) {
            Arrays.fill(arrayRow, (byte) 0);
        }
        checkMlpInputFloatBufferSize();  // in case size changed
        tfNumInBatchSoFar = 0;
//        if (tfInputFloatBuffer != null) {
//            tfInputFloatBuffer.clear();
//        }
        eventList.clear();
        if (stats == null) {
            stats = new DescriptiveStatistics(STATS_NUM_EVENTS);
        }
        stats.clear();

    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0) && (timestampImage == null || timestampImage.length != chip.getSizeX() >> subsampleBy)) {
            timestampImage = new int[chip.getSizeX()][chip.getSizeY()]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
            lastPolMap = new byte[chip.getSizeX()][chip.getSizeY()]; // 

        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        if (tiPatchDisplay != null && showThisTiPatch) {
            tiPatchDisplay.repaint();
            if (saveTiPatchImages && saveDir != null) {
                try {
                    String fn = saveDir.toString() + File.separator + "patch-" + String.format("%04d", tiFileCounter) + ".png";
                    tiPatchDisplay.savePng(fn);
                    tiFileCounter++;
                } catch (IOException e) {
                    log.warning("Could not save image: " + e.toString());
                }
            }
        }
//        if (tfExecutionGraph != null) {
//            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
//            MultilineAnnotationTextRenderer.setScale(.3f);
//            MultilineAnnotationTextRenderer.renderMultilineString(this.getClass().getSimpleName());
//            if ((measurePerformance == true) && (performanceString != null) /*&& !performanceString.equals(lastPerformanceString)*/) {
//                MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
//                lastPerformanceString = performanceString;
//            }
//        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            return;
        }
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        Rectangle chipRect = new Rectangle(sx, sy);
        if (roiRect != null && chipRect.intersects(roiRect)) {
            drawRoi(gl, roiRect, SELECT_COLOR);
        }

        chip.getCanvas().checkGLError(gl, glu, "in annotate");
    }

    private void drawRoi(GL2 gl, Rectangle r, float[] c) {
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(3);
//        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(roiRect.x, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y);
        gl.glVertex2f(roiRect.x + roiRect.width, roiRect.y + roiRect.height);
        gl.glVertex2f(roiRect.x, roiRect.y + roiRect.height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    /**
     * Fills timestampImage with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        fill2dTimestampAndPolarityImagesWithNoiseEvents(noiseRateHz, lastTimestampUs, timestampImage, lastPolMap);
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    @Preferred
    public void doLoadNetwork() {
        File file = null;
        file = openFolderDialog("Choose a network, either tensorflow protobuf binary (pb),  or folder holding tensorflow SavedModelBundle",
                KEY_NETWORK_FILENAME, getDefaultSettingsFolder() + File.separator + DEFAULT_MLPF_NETWORK_FOLDER,
                "Tensorflow 2 exported model folder", "");
        if (file == null) {
            return;
        }
        try {
            String status = loadNetwork(file);
            setLastManuallyLoadedNetwork(file.toString()); // store the last manually loaded network as the 
            resetFilter();
            String htmlStatus = "<HTML>" + WordUtils.wrap(status, 60).replaceAll(";", "\n").replaceAll("\n", "<br>");
            showPlainMessageDialogInSwingThread(htmlStatus, "MLPF network");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load net from this file, caught exception " + ex + ". See console for logging.", "Bad network file", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Loads network, return String message
     *
     * @param f the File pointing to .pb file
     * @return String message
     * @throws IOException if error opening file
     */
    synchronized public String loadNetwork(File f) throws IOException {
        if (f == null) {
            throw new IOException("null file");
        }
        ArrayList<String> ioLayers = new ArrayList();
        StringBuilder resultSB = new StringBuilder(f.toString()).append(":\n");
        try {
            if (f.isDirectory()) { // TF2 model dir type
                closeTFResources();
                log.info("loading \"serve\" graph from tensorflow SavedModelBundle folder " + f);
                this.tfSavedModelBundle = SavedModelBundle.load(f.getCanonicalPath(), "serve");
                this.tfExecutionGraph = tfSavedModelBundle.graph();
                SignatureDef modelInfo = tfSavedModelBundle.metaGraphDef().getSignatureDefMap().get("serving_default");
                log.info(f.toString() + ":\n" + modelInfo.toString());

                TensorInfo input1 = null;
                TensorInfo output1 = null;
                Map<String, TensorInfo> inputs = modelInfo.getInputsMap();
                for (Map.Entry<String, TensorInfo> input : inputs.entrySet()) {
                    if (input1 == null) {
                        input1 = input.getValue();
                        tfInputName = input1.getName();
                        final TensorShapeProto tensorShape = input1.getTensorShape();
                        Dim dim = tensorShape.getDim(1);
                        long inputVectorLength = dim.getSize();
                        int tiInputDim = (int) Math.round(Math.sqrt(inputVectorLength / 2));
                        setPatchWidthAndHeightPixels(tiInputDim);
                        String s = "Input identified as " + input1.getName()
                                + " with shape " + tensorShape.toString()
                                + "\nwith length " + inputVectorLength
                                + " and patch size " + tiInputDim;
                        resultSB.append(s).append("\n");
                        log.info(s);
                        setPatchWidthAndHeightPixels(tiInputDim);
                    }
                }
                Map<String, TensorInfo> outputs = modelInfo.getOutputsMap();
                for (Map.Entry<String, TensorInfo> output : outputs.entrySet()) {
                    if (output1 == null) {
                        output1 = output.getValue();
                        tfOutputName = output1.getName();
                        String s = "First output identified as " + output1.getName() + " with shape " + output1.getTensorShape().toString();
                        resultSB.append(s).append("\n");
                        log.info(s);
                    }
                }
            } else if (f.isFile()) { // TF1 .pb or .h5 file
                log.info(String.format("Loading tensorflow model from %s", f.getAbsoluteFile()));
                byte[] bytes = Files.readAllBytes(Paths.get(f.getAbsolutePath())); // "tensorflow_inception_graph.pb"
                GraphDef graphDef = GraphDef.parseFrom(bytes);
                closeTFResources();
                this.tfExecutionGraph=new Graph();
                this.tfExecutionGraph.importGraphDef(graphDef);
                this.tfSavedModelBundle = null;  // we don't get one loading from single TF1 .pb file
                StringBuilder sb=new StringBuilder(f.toString());
                sb.append("\nOperations:\n");
                Iterator<GraphOperation> itr=tfExecutionGraph.operations();
                while(itr.hasNext()){
                    GraphOperation op=itr.next();
                    sb.append(op.toString()).append("\n");
                }
                sb.append("\n\nFunctions:\n");
                List<ConcreteFunction> functions=tfExecutionGraph.getFunctions();
                for(ConcreteFunction fnc:functions){
                    sb.append(fnc.toString()).append("\n");
                }
                
                log.info(sb.toString());
                tfInputName="input";
                tfOutputName="output/Sigmoid";
                log.info(String.format("Set hard-coded tfInputName=%s and tfOutputName=%s to match legacy .pb TF1 network",tfInputName,tfOutputName));
            /* sample output
            INFO: F:\tobi\Dropbox (Personal)\GitHub\SensorsINI\jaer\filterSettings\floattimesurface100tauNonebitaw0fH16_linear_7-2025-03-14-19-00-model.pb
            Operations:
            <Placeholder 'input'>
            <Const 'fc1/kernel'>
            <Const 'fc1/bias'>
            <Identity 'fc1/MatMul/ReadVariableOp'>
            <MatMul 'fc1/MatMul'>
            <Identity 'fc1/BiasAdd/ReadVariableOp'>
            <BiasAdd 'fc1/BiasAdd'>
            <Relu 'fc1/Relu'>
            <Const 'output/kernel'>
            <Const 'output/bias'>
            <Identity 'output/MatMul/ReadVariableOp'>
            <MatMul 'output/MatMul'>
            <Identity 'output/BiasAdd/ReadVariableOp'>
            <BiasAdd 'output/BiasAdd'>
            <Sigmoid 'output/Sigmoid'>
            */
            }
        } catch (Exception e) {
            log.warning(e.toString());
            e.printStackTrace();
            return e.toString();
        }
        return resultSB.toString();
    }

    private void closeTFResources() {
        log.info("closing possible existing tensorflow resources");
//        if(this.tfSession!=null){
//            this.tfSession.close();
//            this.tfSession=null;
//        }
//        if(this.tfExecutionGraph!=null){
//            this.tfExecutionGraph.close();
//            this.tfExecutionGraph=null;
//        }
//        if(tfSavedModelBundle!=null){
//            tfSavedModelBundle.close();
//            tfSavedModelBundle=null;
//        }
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
    protected File openFolderDialog(String tip, String key, String defaultFile, String type, String... ext) {
        String name = getString(key, defaultFile);
        JFileChooser c = new JFileChooser(name);
        File f = new File(name);
        c.setCurrentDirectory(new File(getString(key, "")));
        c.setToolTipText(tip);
        if (ext != null && ext.length > 0 && !ext[0].isBlank()) {
            FileFilter filt = new FileNameExtensionFilter(type, ext);
            c.addChoosableFileFilter(filt);
            c.setFileFilter(filt);
        }
        c.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
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

    public void doShowClassificationHistogram() {
        Plot plt = Plot.create(); // see https://github.com/sh0nk/matplotlib4j
        plt.subplot(1, 1, 1);
        List<Double> l = Doubles.asList(stats.getValues());
        plt.hist().add(l).bins(100);
        long n = stats.getN();
        plt.title(String.format("MLP output frequency histogram (N=%,d events)", n));
        plt.xlabel("S-N");
        plt.ylabel("frequency");

        plt.legend();
        try {
            plt.show();
        } catch (Exception ex) {
            log.warning("cannot show the plot with pyplot - did you install python and matplotlib on path? " + ex.toString());
            showWarningDialogInSwingThread("<html>Cannot show the plot with pyplot - did you install python and matplotlib on path? <p>" + ex.toString(), "Cannot plot");
        }
    }

    synchronized public void doToggleOffShowTimeimagePatch() {
        tiPatchDisplay = null;
//        if(tiFrame!=null){
//            tiFrame.setVisible(false);
//            tiFrame.dispose();
//        }
    }

    public synchronized void doToggleOnShowTimeimagePatch() {
        if (tiFrame == null) {
            tiFrame = new JFrame("TI patch");  // make a JFrame to hold it
            tiFrame.setPreferredSize(new Dimension(400, 400));  // set the window size
        }

        tiPatchDisplay = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
        int s = 300;
        tiPatchDisplay.setPreferredSize(new Dimension(s, s));

        tiFrame.getContentPane().add(tiPatchDisplay); // add the GLCanvas to the center of the window
        tiFrame.pack(); // otherwise it wont fill up the display

        final Point2D.Float mousePoint = new Point2D.Float();

        tiFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // closing the frame exits
        tiFrame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent ev) {
                tiPatchDisplay = null;
                tiFrame = null;
            }
        });
        tiFrame.setVisible(true); // make the frame visible
        int sizex = 3, sizey = 3;  // used later to define image size
        tiPatchDisplay.setImageSize(patchWidthAndHeightPixels, patchWidthAndHeightPixels); // set dimensions of image		tiPatchDisplay.setxLabel("x label"); // add xaxis label and some tick markers
        tiPatchDisplay.addXTick(0, "0");
        tiPatchDisplay.addXTick(sizex, Integer.toString(sizex));
        tiPatchDisplay.addXTick(sizey / 2, Integer.toString(sizey / 2));

        tiPatchDisplay.setyLabel("y"); // same for y axis
        tiPatchDisplay.addYTick(0, "0");
        tiPatchDisplay.addYTick(sizey, Integer.toString(sizey));
        tiPatchDisplay.addYTick(sizey / 2, Integer.toString(sizey / 2));

        tiPatchDisplay.setTextColor(new float[]{.8f, 1, 1});
        tiFrame.setVisible(true);
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
        String old = this.lastManuallyLoadedNetwork;
        this.lastManuallyLoadedNetwork = lastManuallyLoadedNetwork;
        putString("lastManuallyLoadedNetwork", lastManuallyLoadedNetwork);
        getSupport().firePropertyChange("lastManuallyLoadedNetwork", old, this.lastManuallyLoadedNetwork);
    }

//    /**
//     * @return the measurePerformance
//     */
//    public boolean isMeasurePerformance() {
//        return measurePerformance;
//    }
//
//    /**
//     * @param measurePerformance the measurePerformance to set
//     */
//    public void setMeasurePerformance(boolean measurePerformance) {
//        this.measurePerformance = measurePerformance;
//        putBoolean("measurePerformance", measurePerformance);
//    }
    /**
     * @return the signalClassifierThreshold
     */
    public float getSignalClassifierThreshold() {
        return signalClassifierThreshold;
    }

    /**
     * @param signalClassifierThreshold the signalClassifierThreshold to set
     */
    public void setSignalClassifierThreshold(float signalClassifierThreshold) {
        if (signalClassifierThreshold > 1) {
            signalClassifierThreshold = 1;
        }
        float old = this.signalClassifierThreshold;
        this.signalClassifierThreshold = signalClassifierThreshold;
        putFloat("signalClassifierThreshold", signalClassifierThreshold);
        getSupport().firePropertyChange("signalClassifierThreshold", old, this.signalClassifierThreshold);

    }

    public float getMinSignalClassifierThreshold() {
        return 0;
    }

    public float getMaxSignalClassifierThreshold() {
        return 1;
    }

    /**
     * @return the tfBatchSizeEvents
     */
    public int getTfBatchSizeEvents() {
        return tfBatchSizeEvents;
    }

    /**
     * @param tfBatchSizeEvents the tfBatchSizeEvents to set
     */
    synchronized public void setTfBatchSizeEvents(int tfBatchSizeEvents) {
        this.tfBatchSizeEvents = tfBatchSizeEvents;
        putInt("tfBatchSizeEvents", tfBatchSizeEvents);
        checkMlpInputFloatBufferSize();
    }

    /**
     * @return the tiPatchMethod
     */
    public TIPatchMethod getTiPatchMethod() {
        return tiPatchMethod;
    }

    /**
     * @param patchWidthAndHeightPixels the patchWidthAndHeightPixels to set
     */
    public void setPatchWidthAndHeightPixels(int patchWidthAndHeightPixels) {
        int old = this.patchWidthAndHeightPixels;
        this.patchWidthAndHeightPixels = patchWidthAndHeightPixels;
        putInt("patchWidthAndHeightPixels", patchWidthAndHeightPixels);
        getSupport().firePropertyChange("patchWidthAndHeightPixels", old, this.patchWidthAndHeightPixels);
        checkMlpInputFloatBufferSize();
    }

    /**
     * @return the patchWidthAndHeightPixels
     */
    public int getPatchWidthAndHeightPixels() {
        return patchWidthAndHeightPixels;
    }

    /**
     * @param tiPatchMethod the tiPatchMethod to set
     */
    public void setTiPatchMethod(TIPatchMethod tiPatchMethod) {
        this.tiPatchMethod = tiPatchMethod;
        putString("tiPatchMethod", tiPatchMethod.toString());
    }

    // https://gist.github.com/Alrecenk/55be1682fe46cdd89663
    public static float fastexp(float x) {
        final int temp = (int) (12102203 * x + 1065353216);
        return Float.intBitsToFloat(temp) * expadjust[(temp >> 15) & 0xff];
    }

    static float expadjust[];

    /**
     * build correction table to improve result in region of interest. If region
     * of interest is large enough then improves result everywhere
     */
    public static void buildexptable(double min, double max, double step) {
        expadjust = new float[256];
        int amount[] = new int[256];
        //calculate what adjustments should have been for values in region
        for (double x = min; x < max; x += step) {
            double exp = Math.exp(x);
            int temp = (int) (12102203 * x + 1065353216);
            int index = (temp >> 15) & 0xff;
            double fexp = Float.intBitsToFloat(temp);
            expadjust[index] += exp / fexp;
            amount[index]++;
        }
        //average them out to get adjustment table
        for (int k = 0; k < amount.length; k++) {
            expadjust[k] /= amount[k];
        }
    }

    /**
     * @return the showOnlySignalTimeimages
     */
    public boolean isShowOnlySignalTimeimages() {
        return showOnlySignalTimeimages;
    }

    /**
     * @param showOnlySignalTimeimages the showOnlySignalTimeimages to set
     */
    public void setShowOnlySignalTimeimages(boolean showOnlySignalTimeimages) {
        this.showOnlySignalTimeimages = showOnlySignalTimeimages;
    }

//    /**
//     * @return the useTI
//     */
//    public boolean isUseTI() {
//        return useTI;
//    }
//    /**
//     * @param useTI the useTI to set
//     */
//    synchronized public void setUseTI(boolean useTI) {
//        boolean old = this.useTI;
//        this.useTI = useTI;
//        if (this.useTI) {
//            inputSF = 1;
//        }
//        putBoolean("useTI", useTI);
//        getSupport().firePropertyChange("useTI", old, this.useTI);
//        if (useTI) {
//            setUsePolarity(false);
//            setUseTIandPol(false);
//        }
//        checkMlpInputFloatBufferSize();
//    }
//
//    /**
//     * @return the usePolarity
//     */
//    public boolean isUsePolarity() {
//        return usePolarity;
//    }
//
//    /**
//     * @param usePolarity the usePolarity to set
//     */
//    synchronized public void setUsePolarity(boolean usePolarity) {
//        boolean old = this.usePolarity;
//        this.usePolarity = usePolarity;
//        if (this.usePolarity) {
//            inputSF = 1;
//        }
//        putBoolean("usePolarity", usePolarity);
//        getSupport().firePropertyChange("usePolarity", old, this.usePolarity);
//        if (usePolarity) {
//            setUseTI(false);
//            setUseTIandPol(false);
//        }
//        checkMlpInputFloatBufferSize();
//    }
//    /**
//     * @return the useTIandPol
//     */
//    public boolean isUseTIandPol() {
//        return useTIandPol;
//    }
//
//    /**
//     * @param useTIandPol the useTIandPol to set
//     */
//    synchronized public void setUseTIandPol(boolean useTIandPol) {
//        boolean old = this.useTIandPol;
//        this.useTIandPol = useTIandPol;
//        if (this.useTIandPol) {
//            inputSF = 2;
//        } else {
//            inputSF = 1;
//        }
//        putBoolean("useTIandPol", useTIandPol);
//        getSupport().firePropertyChange("useTIandPol", old, this.useTIandPol);
    ////        if (useTIandPol) {
////            setUseTI(false);
////            setUsePolarity(false);
////        }
//        checkMlpInputFloatBufferSize();
//    }
    /**
     * @return the showOnlyNoiseTimeimages
     */
    public boolean isShowOnlyNoiseTimeimages() {
        return showOnlyNoiseTimeimages;
    }

    /**
     * @param showOnlyNoiseTimeimages the showOnlyNoiseTimeimages to set
     */
    public void setShowOnlyNoiseTimeimages(boolean showOnlyNoiseTimeimages) {
        this.showOnlyNoiseTimeimages = showOnlyNoiseTimeimages;
    }

    /**
     * When this is selected in the FilterPanel GUI, the mouse listeners will be
     * added. When this is unselected, the listeners will be removed.
     *
     */
    @Override
    public void setSelected(boolean yes) {
        super.setSelected(yes);
        chipCanvas = chip.getCanvas();
        if (chipCanvas == null) {
            log.warning("null chip canvas, can't add mouse listeners");
            return;
        }
        glCanvas = (GLCanvas) chipCanvas.getCanvas();
        if (glCanvas == null) {
            log.warning("null chip canvas GL drawable, can't add mouse listeners");
            return;
        }
        if (yes) {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.removeMouseWheelListener(this);
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);
            glCanvas.addMouseWheelListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
            glCanvas.removeMouseWheelListener(this);
        }
    }

    /**
     * Returns the chip pixel position from the MouseEvent. Note that any calls
     * that modify the GL model matrix (or viewport, etc) will make the location
     * meaningless. Make sure that your graphics rendering code wraps transforms
     * inside pushMatrix and popMatrix calls.
     *
     * @param e the mouse event
     * @return the pixel position in the chip object, origin 0,0 in lower left
     * corner.
     */
    protected Point getMousePixel(MouseEvent e) {
        if (getChip().getCanvas() == null) {
            return null;
        }
        Point p = getChip().getCanvas().getPixelFromMouseEvent(e);
        if (getChip().getCanvas().wasMousePixelInsideChipBounds()) {
            return p;
        } else {
            return null;
        }
    }

    /**
     * @return the showCrossHairCursor
     */
    protected boolean isShowCrossHairCursor() {
        return showCrossHairCursor;
    }

    /**
     * By default a cross hair selection cursor is drawn. This method prevent
     * drawing the cross hair.
     *
     * @param showCrossHairCursor the showCrossHairCursor to set
     */
    protected void setShowCrossHairCursor(boolean showCrossHairCursor) {
        this.showCrossHairCursor = showCrossHairCursor;
    }

    // ROI roiRect stuff
    synchronized public void doClearROI() {
        if (freezeRoi) {
            showWarningDialogInSwingThread("Are you sure you want to clear ROI? Uncheck freezeROI if you want to clear the ROI.", "ROI frozen");
            return;
        }
        clearSelection();
    }

    private void clearSelection() {
        roiRect = null;
        putObject("roiRect", null);
    }

    synchronized private void startRoiSelection(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            roiRect = null;
            return;
        }
        roiStartPoint = p;
        log.info("ROI start point = " + p);
        roiSelecting = true;
    }

    synchronized private void finishRoiSelection(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            roiRect = null;
            return;
        }

        roiEndPoint = p;
        roiStartx = min(roiStartPoint.x, roiEndPoint.x);
        roiStarty = min(roiStartPoint.y, roiEndPoint.y);
        roiEndx = max(roiStartPoint.x, roiEndPoint.x) + 1;
        roiEndy = max(roiStartPoint.y, roiEndPoint.y) + 1;
        int w = roiEndx - roiStartx;
        int h = roiEndy - roiStarty;
        roiRect = new Rectangle(roiStartx, roiStarty, w, h);
        putObject("roiRect", roiRect);
    }

    /**
     * Returns true if the event is inside (or on border) of ROI
     *
     * @param e an event
     * @return true if on or inside ROI, false if no ROI or outside
     */
    protected boolean insideRoi(BasicEvent e) {
        if (roiRect == null || roiRect.isEmpty() || roiRect.contains(e.x, e.y)) {
            return true;
        }
        return false;
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point p = getMousePixel(e);
        if (!freezeRoi) {
            startRoiSelection(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (freezeRoi || roiStartPoint == null) {
            return;
        }
        finishRoiSelection(e);
        roiSelecting = false;
        if (roiRect != null) {
            log.info(String.format("ROI rect %s has %d pixels", roiRect, roiRect.height * roiRect.width));
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        currentMousePoint = getMousePixel(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        roiSelecting = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (roiStartPoint == null) {
            return;
        }
        if (freezeRoi) {
            log.warning("disable freezeRoi if you want to select a region of interest");
            return;
        }
        finishRoiSelection(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = getMousePixel(e);
        clickedPoint = p;
    }

    /**
     * Handles wheel event. Empty by default
     *
     * @param mwe the mouse wheel roll event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {

    }

    /**
     * @return the freezeSelection
     */
    public boolean isFreezeRoi() {
        return freezeRoi;
    }

    /**
     * @param freezeSelection the freezeSelection to set
     */
    public void setFreezeRoi(boolean freezeRoi) {
        this.freezeRoi = freezeRoi;
        putBoolean("freezeRoi", freezeRoi);
    }

    /**
     * @return the saveTiPatchImages
     */
    public boolean isSaveTiPatchImages() {
        return saveTiPatchImages;
    }

    /**
     * @param saveTiPatchImages the saveTiPatchImages to set
     */
    public void setSaveTiPatchImages(boolean saveTiPatchImages) {
        if (saveTiPatchImages) {
            saveDir = chooseImageFolder();
        } else {
            if (saveDir != null) {
                if (!Desktop.isDesktopSupported()) {
                    log.warning("Sorry, desktop operations are not supported, cannot show the folder " + saveDir);
                    return;
                }
                try {
                    if (saveDir.exists()) {
                        Desktop.getDesktop().open(saveDir);
                    } else {
                        log.warning(saveDir + " does not exist to open folder to");
                    }
                } catch (Exception e) {
                    log.warning(e.toString());
                }
            }
        }
        this.saveTiPatchImages = saveTiPatchImages;
    }

    private File chooseImageFolder() {
        String defaultDir = System.getProperty("home.dir") + File.separator + "tipatches";
        String dirName = getString("saveDir", defaultDir);
        JFileChooser fileChooser = new JFileChooser(dirName);
        fileChooser.setApproveButtonText("Save in");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setSelectedFile(new File(dirName));
        fileChooser.setVisible(true);
        final int ret = fileChooser.showOpenDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        putString("saveDir", fileChooser.getSelectedFile().toString());
        File outputDir = fileChooser.getSelectedFile();
        return outputDir;
    }

}
