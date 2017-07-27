/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.visualize.ini.convnet.DeepLearnCnnNetwork.OutputOrInnerProductFullyConnectedLayer;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import static jdk.nashorn.internal.objects.NativeJava.extend;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Extends DavisDeepLearnCnnProcessor to add annotation graphics to show
 * ActionRecognition output 
 * 
 * @author Gemma
 */
@Description("Displays ActionRecognition CNN results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ActionRecognition extends DavisDeepLearnCnnProcessor implements PropertyChangeListener{
    
    //LOCAL VARIABLES
    private float decisionLowPassMixingFactor = getFloat("decisionLowPassMixingFactor", .2f);
    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
    Statistics statistics = new Statistics();
    protected DvsSubsamplerToFrameMultiCameraChip dvsSubsamplerMultiCam = null;
    private String performanceString = null; 
    int dvsMinEvents=getDvsMinEvents();
    boolean processDVSTimeSlices=isProcessDVSTimeSlices();
    
    /**
     * output units
     */
//    private static final int DECISION_1 = 1, DECISION_2 = 2, DECISION_3 = 3;
    private static final String[] DECISION_STRINGS = 
        {"Left arm abduction", "Right arm abduction", "Left leg abduction", "Right leg abduction", "Left arm bicepite", "Right arm bicepite", "Left leg knee lift", "Right leg knee lift", // Session1
        "Walking", "Single jump up", "Single jump forward", "Multiple jump up", "Hop right foot", "Hop left foot", // Session2
        "Punch forward left", "Punch forward right", "Punch up left", "Punch up right", "Punch down left", "Punch down right", // Session3
        "Running", "Star jump", "Kick forward left", "Kick forward right", "Kick side left", "Kick side right", // Session4
        "Hello left", "Hello right", "Circle left", "Circl right", "8 left", "8 right", "Clap"// Session5
        };
        
    public ActionRecognition(AEChip chip) {
        super(chip);
        String roshambo = "0. ActionRecognition4";
        setPropertyTooltip(roshambo, "showAnalogDecisionOutput", "Shows action recognition as analog activation of movement unit in softmax of network output");
        setPropertyTooltip(roshambo, "hideOutput", "Hides output action recognition indications");
        setPropertyTooltip(roshambo, "decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
//        setPropertyTooltip(faceDetector,"faceDetectionThreshold", "Threshold activation for showing face detection; increase to decrease false postives. Default 0.5f. You may need to set softmax=true for this to work.");
//        setPropertyTooltip(roshambo, "playSpikeSounds", "Play a spike sound on change of network output decision");
//        setPropertyTooltip(roshambo, "playSounds", "Play sound effects (Rock/Scissors/Paper) every time the decision changes and playSoundsMinIntervalMs has intervened");
//        setPropertyTooltip(roshambo, "playSoundsMinIntervalMs", "Minimum time inteval for playing sound effects in ms");
//        setPropertyTooltip(roshambo, "playSoundsThresholdActivation", "Minimum winner activation to play the sound");
        FilterChain chain = new FilterChain(chip);
        setEnclosedFilterChain(chain);
        apsDvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, statistics);
        super.setDvsMinEvents(40000); //eventsPerFrame in the 4 cam, DVSmovies Dataset Sophie
    }
    
    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsDvsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        if (dvsSubsamplerMultiCam == null) {
            throw new RuntimeException("Null dvsSubsamplerMultiCam; this should not occur");
        }

        if ((apsDvsNet != null)) {
            final int sizeX = chip.getSizeX();
            final int sizeY = chip.getSizeY();
            for (BasicEvent e : in) {
                lastProcessedEventTimestamp = e.getTimestamp();
                MultiCameraApsDvsEvent p = (MultiCameraApsDvsEvent) e; //TODO add change in case of MultiCameraApsDvsEvent

                if (dvsSubsamplerMultiCam != null) {
                    dvsSubsamplerMultiCam.addEvent(p, sizeX, sizeY);
                }
                if (dvsSubsamplerMultiCam!= null && dvsSubsamplerMultiCam.getAccumulatedEventCount() > dvsMinEvents) {
                    long startTime = 0;
                    if (measurePerformance) {
                        startTime = System.nanoTime();
                    }
                    if (processDVSTimeSlices) {
                        apsDvsNet.processDvsTimeslice((DvsSubsamplerToFrame)dvsSubsamplerMultiCam); // generates PropertyChange EVENT_MADE_DECISION
                        if (dvsSubsamplerMultiCam != null) {
                            dvsSubsamplerMultiCam.clear();
                        }
                        if (measurePerformance) {
                            long dt = System.nanoTime() - startTime;
                            float ms = 1e-6f * dt;
                            float fps = 1e3f / ms;
                            performanceString = String.format("Frame processing time: %.1fms (%.1f FPS); %s", ms, fps, apsDvsNet.getPerformanceString());
                        }
                    }

                }
            }

        }
        return in;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        if (dvsSubsamplerMultiCam != null) {
            dvsSubsamplerMultiCam.clear();
        }
        statistics.reset();
    }
    
    @Override
    public void initFilter() {
        super.initFilter();
        boolean rectifyPolarities=super.isRectifyPolarities();
        if (apsDvsNet != null) {
            try {
                dvsSubsamplerMultiCam= new DvsSubsamplerToFrameMultiCameraChip(apsDvsNet.inputLayer.dimx, apsDvsNet.inputLayer.dimy, getDvsColorScale());
                dvsSubsamplerMultiCam.setRectifyPolarties(rectifyPolarities);
            } catch (Exception ex) {
                log.warning("Problem with the class SubsamplerToFromMultiCameraChip, caught exception " + ex);
            }
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, statistics);
        super.setFilterEnabled(yes);
    }
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (hideOutput) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        if ((apsDvsNet != null) && (apsDvsNet.outputLayer != null) && (apsDvsNet.outputLayer.activations != null)) {
            drawDecisionOutput(gl, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        }
        statistics.draw(gl);
//        if(playSounds && isShowOutputAsBarChart()){ //NO PLAY SOUND
//            gl.glColor3f(.5f,0,0);
//            gl.glBegin(GL.GL_LINES);
//            final float h=playSoundsThresholdActivation*DeepLearnCnnNetwork.HISTOGRAM_HEIGHT_FRACTION*chip.getSizeY();
//            gl.glVertex2f(0,h);
//            gl.glVertex2f(chip.getSizeX(),h);
//            gl.glEnd();
//        }
        if(isShowOutputAsBarChart()){ 
            gl.glColor3f(.5f,0,0);
            gl.glBegin(GL.GL_LINES);
            final float h=DeepLearnCnnNetwork.HISTOGRAM_HEIGHT_FRACTION*chip.getSizeY();
            gl.glVertex2f(0,h);
            gl.glVertex2f(chip.getSizeX(),h);
            gl.glEnd();
        }
    }

    private TextRenderer textRenderer = null;

    private void drawDecisionOutput(GL2 gl, int width, int height) {

        float brightness = 0.0f;
        if (showAnalogDecisionOutput) {
            brightness = statistics.maxActivation; // brightness scale
        } else {
            brightness = 1;
        }
        gl.glColor3f(0.0f, brightness, brightness);
//        gl.glPushMatrix();
//        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 72), true, false);
        }
        textRenderer.setColor(brightness, brightness, brightness, 1);
        textRenderer.beginRendering(width, height);
        if ((statistics.maxUnit >= 0) && (statistics.maxUnit < DECISION_STRINGS.length)) {
            Rectangle2D r = textRenderer.getBounds(DECISION_STRINGS[statistics.maxUnit]);
            textRenderer.draw(DECISION_STRINGS[statistics.maxUnit], (width / 2) - ((int) r.getWidth() / 2), height / 2);
//            if (playSounds && statistics.maxUnit >= 0 && statistics.maxUnit < 3 && statistics.maxActivation > playSoundsThresholdActivation) { //REMOVED SOUND PART
//                if (soundPlayer == null) {
//                    soundPlayer = new SoundPlayer();
//                }
//                soundPlayer.playSound(statistics.maxUnit);
//            }
        }
        textRenderer.endRendering();
//        gl.glPopMatrix();
    }

    /**
     * @return the hideOutput
     */
    public boolean isHideOutput() {
        return hideOutput;
    }

    /**
     * @param hideOutput the hideOutput to set
     */
    public void setHideOutput(boolean hideOutput) {
        this.hideOutput = hideOutput;
        putBoolean("hideOutput", hideOutput);
    }

    /**
     * @return the showAnalogDecisionOutput
     */
    public boolean isShowAnalogDecisionOutput() {
        return showAnalogDecisionOutput;
    }

    /**
     * @param showAnalogDecisionOutput the showAnalogDecisionOutput to set
     */
    public void setShowAnalogDecisionOutput(boolean showAnalogDecisionOutput) {
        this.showAnalogDecisionOutput = showAnalogDecisionOutput;
        putBoolean("showAnalogDecisionOutput", showAnalogDecisionOutput);
    }

    /**
     * @return the decisionLowPassMixingFactor
     */
    public float getDecisionLowPassMixingFactor() {
        return decisionLowPassMixingFactor;
    }

    /**
     * @param decisionLowPassMixingFactor the decisionLowPassMixingFactor to set
     */
    public void setDecisionLowPassMixingFactor(float decisionLowPassMixingFactor) {
        if (decisionLowPassMixingFactor > 1) {
            decisionLowPassMixingFactor = 1;
        }
        this.decisionLowPassMixingFactor = decisionLowPassMixingFactor;
        putFloat("decisionLowPassMixingFactor", decisionLowPassMixingFactor);
    }
    
    private class Statistics implements PropertyChangeListener {

        final int NUM_CLASSES = 33; 
        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[NUM_CLASSES], incorrect = new int[NUM_CLASSES], count = new int[NUM_CLASSES];
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
//        int apsTotalCount, apsCorrect, apsIncorrect; //ONLY DVS FOR ACTION RECOGNITION
        int[] decisionCounts = new int[NUM_CLASSES];
        float[] lowpassFilteredOutputUnits = new float[NUM_CLASSES];
        final int HISTORY_LENGTH = 10;
        int[] decisionHistory = new int[HISTORY_LENGTH];
        float maxActivation = Float.NEGATIVE_INFINITY;
        int maxUnit = -1;

        public Statistics() {
            reset();
        }

        void reset() {
            totalCount = 0;
            totalCorrect = 0;
            totalIncorrect = 0;
            Arrays.fill(correct, 0);
            Arrays.fill(incorrect, 0);
            Arrays.fill(count, 0);
            Arrays.fill(decisionCounts, 0);
            Arrays.fill(lowpassFilteredOutputUnits, 0);
            dvsTotalCount = 0;
            dvsCorrect = 0;
            dvsIncorrect = 0;
//            apsTotalCount = 0;
//            apsCorrect = 0;
//            apsIncorrect = 0;

        }

        @Override
        public String toString() {
            if (totalCount == 0) {
                return "Error: no samples yet";
            }
            StringBuilder sb = new StringBuilder("Decision statistics: ");
            try {
                for (int i = 0; i < NUM_CLASSES; i++) {
                    sb.append(String.format("    %s: %d (%.1f%%) \n", DECISION_STRINGS[i], decisionCounts[i], (100 * (float) decisionCounts[i]) / totalCount));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                sb.append(" out of bounds exception; did you load valid CNN?");
            }
            return sb.toString();
        }

        @Override
        public synchronized void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName() == DeepLearnCnnNetwork.EVENT_MADE_DECISION) {
                int lastOutput = maxUnit;
                DeepLearnCnnNetwork net = (DeepLearnCnnNetwork) evt.getNewValue();
                maxActivation = Float.NEGATIVE_INFINITY;
                maxUnit = -1;
                try {
                    for (int i = 0; i < NUM_CLASSES; i++) {
                        float output = net.outputLayer.activations[i];
                        lowpassFilteredOutputUnits[i] = ((1 - decisionLowPassMixingFactor) * lowpassFilteredOutputUnits[i]) + (output * decisionLowPassMixingFactor);
                        if (lowpassFilteredOutputUnits[i] > maxActivation) {
                            maxActivation = lowpassFilteredOutputUnits[i];
                            maxUnit = i;
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.warning("Array index out of bounds in rendering output. Did you load a valid CNN with 3 (or more) output units?");

                }
                decisionCounts[maxUnit]++;
                totalCount++;
//                if (playSpikeSounds && (maxUnit != lastOutput)) {
//                    if (spikeSound == null) {
//                        spikeSound = new SpikeSound();
//                    }
//                    spikeSound.play();
//                }
            } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
                reset();
            }
        }

        private void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(.8f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }
    
    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    synchronized public void doLoadApsDvsNetworkFromXML() {
        super.doLoadApsDvsNetworkFromXML();
        boolean rectifyPolarities=super.isRectifyPolarities();
        try {
            dvsSubsamplerMultiCam = new DvsSubsamplerToFrameMultiCameraChip(apsDvsNet.inputLayer.dimx, apsDvsNet.inputLayer.dimy, getDvsColorScale());
            dvsSubsamplerMultiCam.setRectifyPolarties(rectifyPolarities);
        } catch (Exception ex) {
            log.warning("Problem with the class vsSubsamplerToFromMultiCameraChip, caught exception " + ex);
        }
        System.out.println("File Loaded!");
    }

//    public class DeepLearnCnnNetworkExtended extends DeepLearnCnnNetwork {
//
////        private DeepLearnCnnNetwork outer = new DeepLearnCnnNetworkExtended(); 
//
//        public class OutputOrInnerProductFullyConnectedLayerExtended extends OutputOrInnerProductFullyConnectedLayer {
//
//            public OutputOrInnerProductFullyConnectedLayerExtended(int index) {
//                super(index);
//            }
//            
//            private void annotateHistogram(GL2 gl, int width, int height) { // width and height are of AEchip annotateHistogram size in pixels of chip (not screen pixels)
//
//                if (activations == null) {
//                    return;
//                }
//                float dx = (float) (width) / (activations.length);
//                float sy = (float) HISTOGRAM_HEIGHT_FRACTION * (height);
//
//    //            gl.glBegin(GL.GL_LINES);
//    //            gl.glVertex2f(1, 1);
//    //            gl.glVertex2f(width - 1, 1);
//    //            gl.glEnd();
//                gl.glBegin(GL.GL_LINE_STRIP);
//                for (int i = 0; i < activations.length; i++) {
//                    float y = 1 + (sy * activations[i]);  // TODO debug hack
//                    float x1 = 1 + (dx * i), x2 = x1 + dx;
//                    gl.glVertex2f(x1, 1);
//                    gl.glVertex2f(x1, y);
//                    gl.glVertex2f(x2, y);
//                    gl.glVertex2f(x2, 1);
//                }
//                gl.glEnd();
//            }
//            
//            @Override
//            public void annotateHistogram(GL2 gl, int width, int height, float lineWidth, Color color)  {
//                gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
//                gl.glLineWidth(lineWidth);
//                float[] ca = color.getColorComponents(null);
//                gl.glColor4fv(ca, 0);
//                this.annotateHistogram(gl, width, height);
//                gl.glPopAttrib();
//            }
//        }
//    }
}
        

