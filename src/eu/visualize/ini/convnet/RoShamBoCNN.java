/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Font;
import java.util.Arrays;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.SpikeSound;

/**
 * Extends DavisDeepLearnCnnProcessor to add annotation graphics to show
 * RoShamBo demo output for development of rock-scissors-paper robot
 *
 * @author Tobi
 */
@Description("Displays RoShamBo (rock-scissors-paper) CNN results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RoShamBoCNN extends DavisDeepLearnCnnProcessor implements PropertyChangeListener {

    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
    private boolean playSpikeSounds = getBoolean("playSpikeSounds", false);
//    private TargetLabeler targetLabeler = null;
    Error error = new Error();
    private float decisionLowPassMixingFactor = getFloat("decisionLowPassMixingFactor", .2f);
    private SpikeSound spikeSound=null;

    /** output units */
    private static final int DECISION_PAPER=0, DECISION_SCISSORS=1, DECISION_ROCK=2; 
    private static final String[] DECISION_STRINGS={"Paper", "Scissors", "Rock"}; 
    
    public RoShamBoCNN(AEChip chip) {
        super(chip);
        String faceDetector="Face detector";
        setPropertyTooltip(faceDetector,"showAnalogDecisionOutput", "Shows face detection as analog activation of face unit in softmax of network output");
        setPropertyTooltip(faceDetector,"hideOutput", "Hides output face detection indications");
        setPropertyTooltip(faceDetector,"decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
        setPropertyTooltip(faceDetector,"playSpikeSounds", "Play a spike sound on change of network output decision");
        FilterChain chain = new FilterChain(chip);
        setEnclosedFilterChain(chain);
        apsDvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, error);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket out = super.filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        error.reset();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
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

        error.draw(gl);

    }
    
    private TextRenderer textRenderer=null;

    private void drawDecisionOutput(GL2 gl, int width, int height) {
        
        float brightness = 0.0f;
        if (showAnalogDecisionOutput) {
            brightness = error.maxActivation; // brightness scale
        } else {
            brightness = 1;
        }
        gl.glColor3f(0.0f, brightness, brightness);
//        gl.glPushMatrix();
//        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
        if(textRenderer==null){
            textRenderer=textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
        }
        textRenderer.setColor(brightness, brightness, brightness,1);
        textRenderer.beginRendering(width, height);
        textRenderer.draw(DECISION_STRINGS[error.maxUnit],chip.getSizeX() / 2, chip.getSizeY() / 2);
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

    /**
     * @return the playSpikeSounds
     */
    public boolean isPlaySpikeSounds() {
        return playSpikeSounds;
    }

    /**
     * @param playSpikeSounds the playSpikeSounds to set
     */
    public void setPlaySpikeSounds(boolean playSpikeSounds) {
        this.playSpikeSounds = playSpikeSounds;
        putBoolean("playSpikeSounds",playSpikeSounds);
    }

    private class Error implements PropertyChangeListener {

        final int NUM_CLASSES = 3;
        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[NUM_CLASSES], incorrect = new int[NUM_CLASSES], count = new int[NUM_CLASSES];
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
        int apsTotalCount, apsCorrect, apsIncorrect;
        int[] decisionCounts = new int[NUM_CLASSES];
        final int FACE = 0, NONFACE = 1;
        final String[] decisionStrings = {"Face", "Non-Face"};
        float[] lowpassFilteredOutputUnits = new float[NUM_CLASSES];
        final int HISTORY_LENGTH=10;
        int[] decisionHistory = new int[HISTORY_LENGTH];
        float maxActivation = Float.NEGATIVE_INFINITY;
        int maxUnit = -1;

        public Error() {
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
            apsTotalCount = 0;
            apsCorrect = 0;
            apsIncorrect = 0;

        }

        @Override
        public String toString() {
            if (totalCount == 0) {
                return "Error: no samples yet";
            }
            StringBuilder sb = new StringBuilder("Decision statistics: ");
            for (int i = 0; i < NUM_CLASSES; i++) {
                sb.append(String.format("%s: %d (%.1f%%)  ", decisionStrings[i], decisionCounts[i], 100 * (float) decisionCounts[i] / totalCount));
            }
            return sb.toString();
        }

        @Override
        public synchronized void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName() == DeepLearnCnnNetwork.EVENT_MADE_DECISION) {
                DeepLearnCnnNetwork net = (DeepLearnCnnNetwork) evt.getNewValue();
                maxActivation = Float.NEGATIVE_INFINITY;
                maxUnit = -1;
                for (int i = 0; i < NUM_CLASSES; i++) {
                    float output = net.outputLayer.activations[i];
                    lowpassFilteredOutputUnits[i] = (1 - decisionLowPassMixingFactor) * lowpassFilteredOutputUnits[i] + output * decisionLowPassMixingFactor;
                    if (lowpassFilteredOutputUnits[i] > maxActivation) {
                        maxActivation = lowpassFilteredOutputUnits[i];
                        maxUnit = i;
                    }
                }
                decisionCounts[maxUnit]++;
                totalCount++;
                if(playSpikeSounds && maxUnit==FACE){
                    if(spikeSound==null){
                        spikeSound=new SpikeSound();
                    }
                    spikeSound.play();
                }
            }
        }

        private void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(.8f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }

}
