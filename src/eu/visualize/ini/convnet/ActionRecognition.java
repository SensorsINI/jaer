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
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

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
 * Extends DavisClassifierCNN to add annotation graphics to show
 ActionRecognition output
 *
 * @author Gemma
 */
@Description("Displays ActionRecognition CNN results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ActionRecognition extends DavisClassifierCNN implements PropertyChangeListener {

    //LOCAL VARIABLES
    private float decisionLowPassMixingFactor = getFloat("decisionLowPassMixingFactor", .2f);
    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showDecisionStatistic = getBoolean("showDecisionStatistic", false);
    private boolean showThreshold = getBoolean("showThreshold", false);
    Statistics statistics = new Statistics();
//    protected DvsFramerSingleFrameMultiCameraChip dvsSubsamplerMultiCam = null;
    private String performanceString = null;
    boolean processDVSTimeSlices = isProcessDVSTimeSlices();
    float stdDevThreshold = getFloat("stdDevThreshold", 0.1f);
    float thrActivations = getFloat("thrActivations", 0.5f);
    float MinThrActivations = getFloat("MinThrActivations", 0f);
    float MaxThrActivations = getFloat("MaxThrActivations", 1f);
    boolean displayDecision = false;

    /**
     * output units
     */
    private static final String[] DECISION_STRINGS
            = {"Left arm abduction", "Right arm abduction", "Left leg abduction", "Right leg abduction", "Left arm bicepite", "Right arm bicepite", "Left leg knee lift", "Right leg knee lift", // Session1
                "Walking", "Single jump up", "Single jump forward", "Multiple jump up", "Hop right foot", "Hop left foot", // Session2
                "Punch forward left", "Punch forward right", "Punch up left", "Punch up right", "Punch down left", "Punch down right", // Session3
                "Running", "Star jump", "Kick forward left", "Kick forward right", "Kick side left", "Kick side right", // Session4
                "Hello left", "Hello right", "Circle left", "Circl right", "8 left", "8 right", "Clap"// Session5
        };

    public ActionRecognition(AEChip chip) {
        super(chip);
        dvsSubsampler = new DvsFramerSingleFrameMultiCameraChip(chip);

        String actionRecognition = "0. ActionRecognition";
        setPropertyTooltip(actionRecognition, "showDecisionStatistic", "Show list of movement");
        setPropertyTooltip(actionRecognition, "hideOutput", "Hides output action recognition indications");
        setPropertyTooltip(actionRecognition, "decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
        setPropertyTooltip(actionRecognition, "stdDevThreshold", "Minimum Standard Deviation of activations to show the output");
        setPropertyTooltip(actionRecognition, "showThreshold", "Display the threshold value of activations");
        setPropertyTooltip(actionRecognition, "thrActivations", "threshold value of activations to show the output");

        FilterChain chain = new FilterChain(chip);
        setEnclosedFilterChain(chain);
        chain.add(dvsSubsampler);
        apsDvsNet.getSupport().addPropertyChangeListener(DavisCNN.EVENT_MADE_DECISION, statistics);
//        super.setDvsEventsPerFrame(40000); //eventsPerFrame in the 4 cam, DVSmovies Dataset Sophie
        MultilineAnnotationTextRenderer.setFontSize(50);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {

        if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsDvsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        if (dvsSubsampler == null) {
            throw new RuntimeException("Null dvsSubsamplerMultiCam; this should not occur");
        }

        if ((apsDvsNet != null)) {
            final int sizeX = chip.getSizeX();
            final int sizeY = chip.getSizeY();
            for (BasicEvent e : in) {
                lastProcessedEventTimestamp = e.getTimestamp();
                MultiCameraApsDvsEvent p = (MultiCameraApsDvsEvent) e; //TODO add change in case of MultiCameraApsDvsEvent

                if (dvsSubsampler != null) {
                    dvsSubsampler.addEvent(p); // generates PropertyChangeEvent EVENT_NEW_FRAME_AVAILBLE when frame fills up, which processes CNN on it
                }
            }

        }
        return in;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        if (dvsSubsampler != null) {
            dvsSubsampler.clear();
        }
        statistics.reset();
    }

    @Override
    public void initFilter() {
        super.initFilter();
        if (apsDvsNet != null) {
            try {
                dvsSubsampler = new DvsFramerSingleFrameMultiCameraChip(chip);
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
            float[] activationsVect;
            activationsVect = apsDvsNet.outputLayer.activations;
            double stdActivations = StdDev(activationsVect);
            displayDecision = false;
            float max = 0;

            for (int i = 0; i < activationsVect.length; i++) {
                if (activationsVect[i] > max) {
                    max = activationsVect[i];
                    if (max > thrActivations) {
                        displayDecision = true;
                    }
                }
            }

            if ((stdActivations >= stdDevThreshold) & (displayDecision)) {
                drawDecisionOutput(gl, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
            } else {
                clearDecisionOutput(gl);
            }
        }
        if (showDecisionStatistic) {
            statistics.draw(gl);
        }
//        if(playSounds && isShowOutputAsBarChart()){ //NO PLAY SOUND
//            gl.glColor3f(.5f,0,0);
//            gl.glBegin(GL.GL_LINES);
//            final float h=playSoundsThresholdActivation*DavisCNN.HISTOGRAM_HEIGHT_FRACTION*chip.getSizeY();
//            gl.glVertex2f(0,h);
//            gl.glVertex2f(chip.getSizeX(),h);
//            gl.glEnd();
//        }
        if (isShowOutputAsBarChart()) {
            gl.glColor3f(.5f, 0, 0);
            gl.glBegin(GL.GL_LINES);
            final float h = DavisCNN.HISTOGRAM_HEIGHT_FRACTION * chip.getSizeY();
            gl.glVertex2f(0, h);
            gl.glVertex2f(chip.getSizeX(), h);
            gl.glEnd();
        }

        if (isShowThreshold() && thrActivations != 0) {
            gl.glColor3f(0, .5f, 0);
            gl.glBegin(GL.GL_LINES);
            final float h = (float) thrActivations * DavisCNN.HISTOGRAM_HEIGHT_FRACTION * chip.getSizeY();
            gl.glVertex2f(0, h);
            gl.glVertex2f(chip.getSizeX(), h);
            gl.glEnd();
        }
    }

    /**
     * Return the mean of the Array
     *
     * @param array
     * @return
     */
    public double Mean(float[] array) {
        double sum = 0;
        int numElem = array.length;

        for (int i = 0; i < numElem; i++) {
            sum = sum + array[i];
        }
        return (sum / numElem);
    }

    /**
     * Return the standard deviation of the Array
     *
     * @param array
     * @return
     */
    public double StdDev(float[] array) {
        double sum = 0;
        int numElem = array.length;
        double avg = Mean(array);

        for (int i = 0; i < numElem; i++) {
            sum = sum + Math.pow((array[i] - avg), 2);
        }
        return Math.sqrt(sum / numElem);
    }

    private TextRenderer textRenderer = null;

    private void drawDecisionOutput(GL2 gl, int width, int height) {

        float brightness = 1;
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
        }
        textRenderer.endRendering();
//        gl.glPopMatrix();
    }

    private void clearDecisionOutput(GL2 gl) {
        textRenderer = null;
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
     * @return the showDecisionStatistic
     */
    public boolean isShowDecisionStatistic() {
        return showDecisionStatistic;
    }

    /**
     * @param showDecisionStatistic the showDecisionStatistic to set
     */
    public void setShowDecisionStatistic(boolean showDecisionStatistic) {
        this.showDecisionStatistic = showDecisionStatistic;
        putBoolean("showDecisionStatistic", showDecisionStatistic);
    }

    /**
     * @return the showThreshold
     */
    public boolean isShowThreshold() {
        return showThreshold;
    }

    /**
     * @param showThreshold the showThreshold to set
     */
    public void setShowThreshold(boolean showThreshold) {
        this.showThreshold = showThreshold;
        putBoolean("showThreshold", showThreshold);
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

    /**
     * getter for thrActivations
     *
     * @return thrActivations
     */
    public float getThrActivations() {
        return thrActivations;
    }

    /**
     * getter for minimum value of thrActivations
     *
     * @return thrActivations
     */
    public float getMinThrActivations() {
        return MinThrActivations;
    }

    /**
     * getter for maximum value of thrActivations
     *
     * @return thrActivations
     */
    public float getMaxThrActivations() {
        return MaxThrActivations;
    }

    /**
     * @param thrActivations the thrActivations to set
     */
    public void setThrActivations(float thrActivations) {
        this.thrActivations = thrActivations;
        putFloat("thrActivations", thrActivations);
    }

    /**
     * @return the stdDevThreshold
     */
    public float getStdDevThreshold() {
        return stdDevThreshold;
    }

    /**
     * @param stdDevThreshold the stdDevThreshold to set
     */
    public void setStdDevThreshold(float stdDevThreshold) {
        this.stdDevThreshold = stdDevThreshold;
        putFloat("stdDevThreshold", stdDevThreshold);
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
                    if (decisionCounts[i] > 0) {
                        sb.append(String.format("    %s: %d (%.1f%%) \n", DECISION_STRINGS[i], decisionCounts[i], (100 * (float) decisionCounts[i]) / totalCount));
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                sb.append(" out of bounds exception; did you load valid CNN?");
            }
            return sb.toString();
        }

        @Override
        public synchronized void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName() == DavisCNN.EVENT_MADE_DECISION) {
                int lastOutput = maxUnit;
                DavisCNN net = (DavisCNN) evt.getNewValue();
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
            MultilineAnnotationTextRenderer.resetToYPositionPixels(1.5f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }

}
