/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.npp.TargetLabeler;
import ch.unizh.ini.jaer.projects.npp.DavisCNNPureJava;
import ch.unizh.ini.jaer.projects.npp.DavisClassifierCNNProcessor;
import java.awt.Color;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.SpikeSound;

/**
 * Extends DavisClassifierCNNProcessor to add annotation graphics to show
 steering decision.
 *
 * @author Tobi
 */
@Description("Displays face detector ConvNet results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FaceDetectorCNN extends DavisClassifierCNNProcessor implements PropertyChangeListener {

    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
    private boolean playSpikeSounds = getBoolean("playSpikeSounds", false);
    private float faceDetectionThreshold = getFloat("faceDetectionThreshold", .5f);
//    private TargetLabeler targetLabeler = null;
    Error error = new Error();
    private float decisionLowPassMixingFactor = getFloat("decisionLowPassMixingFactor", .2f);
    private SpikeSound spikeSound=null;

    public FaceDetectorCNN(AEChip chip) {
        super(chip);
        String faceDetector="Face detector";
        setPropertyTooltip(faceDetector,"showAnalogDecisionOutput", "Shows face detection as analog activation of face unit in softmax of network output");
        setPropertyTooltip(faceDetector,"hideOutput", "Hides output face detection indications");
        setPropertyTooltip(faceDetector,"faceDetectionThreshold", "Threshold activation for showing face detection; increase to decrease false postives. Default 0.5f. You may need to set softmax=true for this to work.");
        setPropertyTooltip(faceDetector,"decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
        setPropertyTooltip(faceDetector,"playSpikeSounds", "Play a spike sound on detecting face");
        FilterChain chain = new FilterChain(chip);
        setEnclosedFilterChain(chain);
        apsDvsNet.getSupport().addPropertyChangeListener(DavisCNNPureJava.EVENT_MADE_DECISION, error);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
//        targetLabeler.filterPacket(in);
        EventPacket out = super.filterPacket(in);
        return out;
    }

    private Boolean correctDescisionFromTargetLabeler(TargetLabeler targetLabeler, DavisCNNPureJava net) {
        if (targetLabeler.getTargetLocation() == null) {
            return null; // no face labeled for this sample
        }
        Point p = targetLabeler.getTargetLocation().location;
        if (p == null) { // no face labeled
            if (net.outputLayer.getMaxActivatedUnit() == 1) {
                return true; // no face detected
            }
        } else // face labeled
        {
            if (0 == net.outputLayer.getMaxActivatedUnit()) {
                return true;  // face detected
            }
        }
        return false; // wrong decision
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        error.reset();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
//        if (yes && !targetLabeler.hasLocations()) {
//            Runnable r = new Runnable() {
//
//                @Override
//                public void run() {
//                    targetLabeler.loadLastLocations();
//                }
//            };
//            SwingUtilities.invokeLater(r);
//        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
//        if (targetLabeler != null) {
//            targetLabeler.annotate(drawable);
//        }
        if (hideOutput) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int sy = chip.getSizeY();
        if ((apsDvsNet != null) && (apsDvsNet.getOutputLayer() != null) && (apsDvsNet.getOutputLayer().getActivations()!= null)) {
            drawDecisionOutput(gl, sy, Color.GREEN);
        }

        error.draw(gl);

    }
    GLU glu = null;
    GLUquadric quad = null;

    private void drawDecisionOutput(GL2 gl, int sy, Color color) {
        // 0=left, 1=center, 2=right, 3=no target
        float faciness = error.lowpassFilteredOutputUnits[0], nonfaciness = error.lowpassFilteredOutputUnits[0];
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float[] cv = color.getColorComponents(null);
        if (glu == null) {
            glu = new GLU();
        }
        if (quad == null) {
            quad = glu.gluNewQuadric();
        }

        float rad = chip.getMinSize() / 4, rim = 3;
        float brightness = 0.0f;
        // brightness set by showAnalogDecisionOutput
        if (showAnalogDecisionOutput) {
            brightness = error.lowpassFilteredOutputUnits[0] * 1f; // brightness scale
        } else if (faciness > faceDetectionThreshold) {
            brightness = 1;
        }
        gl.glColor3f(0.0f, brightness, brightness);
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
        glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
        glu.gluDisk(quad, rad - rim, rad + rim, 32, 1);
        gl.glPopMatrix();
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
     * @return the faceDetectionThreshold
     */
    public float getFaceDetectionThreshold() {
        return faceDetectionThreshold;
    }

    /**
     * @param faceDetectionThreshold the faceDetectionThreshold to set
     */
    public void setFaceDetectionThreshold(float faceDetectionThreshold) {
        this.faceDetectionThreshold = faceDetectionThreshold;
        putFloat("faceDetectionThreshold", faceDetectionThreshold);
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

        final int NUM_CLASSES = 2;
        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[NUM_CLASSES], incorrect = new int[NUM_CLASSES], count = new int[NUM_CLASSES];
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
        int apsTotalCount, apsCorrect, apsIncorrect;
        char[] outputChars = {'F', 'N'};
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
          if (evt.getPropertyName() == DavisCNNPureJava.EVENT_MADE_DECISION) {
                DavisCNNPureJava net = (DavisCNNPureJava) evt.getNewValue();
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
