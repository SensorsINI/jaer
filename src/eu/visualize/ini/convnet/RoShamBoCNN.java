/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import gnu.io.NRSerialPort;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
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
    Statistics statistics = new Statistics();
    private float decisionLowPassMixingFactor = getFloat("decisionLowPassMixingFactor", .2f);
    private SpikeSound spikeSound = null;
    // for arduino robot (code in F:\Dropbox\NPP roshambo robot training data\arduino\RoShamBoHandControl)
    NRSerialPort serialPort = null;
    private String serialPortName = getString("serialPortName", "COM3");
    private boolean serialPortCommandsEnabled = getBoolean("serialPortCommandsEnabled", false);
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private DataOutputStream serialPortOutputStream = null;
    private Enumeration portList = null;

    /**
     * output units
     */
    private static final int DECISION_PAPER = 0, DECISION_SCISSORS = 1, DECISION_ROCK = 2, DECISION_BACKGROUND = 3; 
    private static final String[] DECISION_STRINGS = {"Paper", "Scissors", "Rock", "Background"};

    public RoShamBoCNN(AEChip chip) {
        super(chip);
        String roshambo = "RoShamBo";
        setPropertyTooltip(roshambo, "showAnalogDecisionOutput", "Shows face detection as analog activation of face unit in softmax of network output");
        setPropertyTooltip(roshambo, "hideOutput", "Hides output face detection indications");
        setPropertyTooltip(roshambo, "decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
        setPropertyTooltip(roshambo, "playSpikeSounds", "Play a spike sound on change of network output decision");
        setPropertyTooltip(roshambo, "serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip(roshambo, "serialPortCommandsEnabled", "Send commands to serial port for Arduino Nano hand robot");
        setPropertyTooltip(roshambo, "serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        FilterChain chain = new FilterChain(chip);
        setEnclosedFilterChain(chain);
        apsDvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, statistics);
     }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket out = super.filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        statistics.reset();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, statistics);
        super.setFilterEnabled(yes);
        if (!yes) {
            closeSerial();
        } else {
            try {
                openSerial();
            } catch (Exception ex) {
                log.warning("caught exception enabling serial port when filter was enabled: "+ex.toString());
            }
        }
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
        putBoolean("playSpikeSounds", playSpikeSounds);
    }

    /**
     * @return the serialPortName
     */
    public String getSerialPortName() {
        return serialPortName;
    }

    /**
     * @param serialPortName the serialPortName to set
     */
    public void setSerialPortName(String serialPortName) {
        try {
            this.serialPortName = serialPortName;
            putString("serialPortName", serialPortName);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the serialPortCommandsEnabled
     */
    public boolean isSerialPortCommandsEnabled() {
        return serialPortCommandsEnabled;
    }

    /**
     * @param serialPortCommandsEnabled the serialPortCommandsEnabled to set
     */
    public void setSerialPortCommandsEnabled(boolean serialPortCommandsEnabled) {
        this.serialPortCommandsEnabled = serialPortCommandsEnabled;
        putBoolean("serialPortCommandsEnabled", serialPortCommandsEnabled);
        if(!isFilterEnabled()) {
			return;
		}
        if (!serialPortCommandsEnabled) {
            closeSerial();
            return;
        }
        // come here to open port
        try {
            openSerial();
        } catch (IOException e) {
            log.warning("caught exception on serial port " + serialPortName + ": " + e.toString());
        }
    }

    private void openSerial() throws IOException {
        if (serialPort != null) {
            closeSerial();
        }
        StringBuilder sb = new StringBuilder("serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        for (String s : availableSerialPorts) {
            sb.append(s).append(" ");
        }
        log.info(sb.toString());
        if(!availableSerialPorts.contains(serialPortName)){
            log.warning(serialPortName+" is not in avaiable "+sb.toString());
            return;
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if(serialPort==null){
            log.warning("null serial port returned when trying to open "+serialPortName+"; available "+sb.toString());
            return;
        }
        serialPort.connect();
        serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
        log.info("opened serial port " + serialPortName + " with baud rate=" + serialBaudRate);
    }

    private void closeSerial() {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) '0'); // rest; turn off servos
                serialPortOutputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
            }
            serialPortOutputStream = null;
        }
        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
            serialPort = null;
        }
        log.info("closed serial port");
    }

    /**
     * @return the serialBaudRate
     */
    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    /**
     * @param serialBaudRate the serialBaudRate to set
     */
    public void setSerialBaudRate(int serialBaudRate) {
        try {
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private class Statistics implements PropertyChangeListener {

        final int NUM_CLASSES = 4;
        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[NUM_CLASSES], incorrect = new int[NUM_CLASSES], count = new int[NUM_CLASSES];
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
        int apsTotalCount, apsCorrect, apsIncorrect;
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
                if (playSpikeSounds && (maxUnit != lastOutput)) {
                    if (spikeSound == null) {
                        spikeSound = new SpikeSound();
                    }
                    spikeSound.play();
                }
                if (isSerialPortCommandsEnabled() && (serialPortOutputStream != null)) {
                    char cmd = 0;
                    switch (maxUnit) {
                        case DECISION_ROCK:
                            cmd = '1';
                            break;
                        case DECISION_SCISSORS:
                            cmd = '2';
                            break;
                        case DECISION_PAPER:
                            cmd = '3';
                            break;
                        case DECISION_BACKGROUND:
                            cmd = '4';
                            break;                            
                        default:
                            log.warning("maxUnit=" + maxUnit + " is not a valid network output state");
                    }
                    try {
                        serialPortOutputStream.write((byte) cmd);
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                    }
                }
            }else if(evt.getPropertyName()==AEViewer.EVENT_FILEOPEN){
                reset();
            }
        }

        private void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(.8f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }

}
