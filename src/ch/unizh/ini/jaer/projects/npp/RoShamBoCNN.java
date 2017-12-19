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

import com.jogamp.opengl.GL;
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
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.SoundWavFilePlayer;
import net.sf.jaer.util.SpikeSound;

/**
 * Extends DavisClassifierCNNProcessor to add annotation graphics to show
 RoShamBo demo output for development of rock-scissors-paper robot
 *
 * @author Tobi
 */
@Description("Displays RoShamBo (rock-scissors-paper) CNN results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RoShamBoCNN extends DavisClassifierCNNProcessor {

    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
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
    private boolean playSounds = getBoolean("playSounds", false);
    private int playSoundsMinIntervalMs = getInt("playSoundsMinIntervalMs", 1000);
    private float descisionThresholdActivation = getFloat("descisionThresholdActivation", .7f);
    private SoundPlayer soundPlayer = null;
    private boolean playToWin = getBoolean("playToWin", true);
    private boolean addedStatisticsListener=false;

    /**
     * for game state machine
     */
    private volatile boolean useGameStatesToPlay = getBoolean("useGameStatesToPlay", false);
    private RectangularClusterTracker tracker = null;
    private float trackerUpperEdgeThreshold = getFloat("trackerUpperEdgeThreshold", .7f);
    private float trackerLowerEdgeThreshold = getFloat("trackerLowerEdgeThreshold", .3f);
    private int throwIntervalTimeoutMs = getInt("throwIntervalTimeoutMs", 2000);
    private int winningSymbolShowTimeMs = getInt("winningSymbolShowTimeMs", 4000);

    public enum GameState {
        Idle, Throw1, Throw2, ThrowShow
    };
    private GameState gameState = GameState.Idle;
    private long gameStateUpdateTimeMs;

    public enum HandTrackerState {
        Idle, CrossedUpper, CrossedLower
    };
    private HandTrackerState handTrackerState = HandTrackerState.Idle;
    private long handTrackerStateUpdateTimeMs;


    /**
     * output units
     */
    private static final int DECISION_PAPER = 0, DECISION_SCISSORS = 1, DECISION_ROCK = 2, DECISION_BACKGROUND = 3;
    private static final String[] DECISION_STRINGS = {"Paper", "Scissors", "Rock", "Background"};
    private boolean showDecisionStatistics = getBoolean("showDecisionStatistics", true);

    public RoShamBoCNN(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        tracker.setMaxNumClusters(1);
        getEnclosedFilterChain().add(tracker); // super chain alredy has DvsFramer
        setEnclosedFilterChain(getEnclosedFilterChain()); // to correctly set enclosed status of filters
        String roshamboGame = "0a. RoShamBo Game";
        setPropertyTooltip(roshamboGame, "trackerUpperEdgeThreshold", "Upper (higher) edge of scene as fraction of image which the tracker must pass to start detecting a throw");
        setPropertyTooltip(roshamboGame, "trackerLowerEdgeThreshold", "Lower edge of scene as fraction of image which the tracker must pass to finish detecting a throw");
        setPropertyTooltip(roshamboGame, "tracker2EdgeCrossingTimeoutMs", "The tracker must cross the two edges within this time to be classified as a throw");
        setPropertyTooltip(roshamboGame, "throwIntervalTimeoutMs", "Throws must follow each other within this time or the game will go back to idle state");
        setPropertyTooltip(roshamboGame, "winningSymbolShowTimeMs", "How long the winning symbol is shown by the hand");
        setPropertyTooltip(roshamboGame, "useGameStatesToPlay", "Select to use the Roshambo state machine; if unselected, then instantaneous (filtered by mixing factor) CNN output is used");
        String roshambo = "0b. RoShamBo Engine";
        setPropertyTooltip(roshambo, "showAnalogDecisionOutput", "Shows face detection as analog activation of face unit in softmax of network output");
        setPropertyTooltip(roshambo, "hideOutput", "Hides output face detection indications");
        setPropertyTooltip(roshambo, "decisionLowPassMixingFactor", "The softmax outputs of the CNN are low pass filtered using this mixing factor; reduce decisionLowPassMixingFactor to filter more decisions");
        setPropertyTooltip(roshambo, "playSpikeSounds", "Play a spike sound on change of network output decision");
        setPropertyTooltip(roshambo, "serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip(roshambo, "serialPortCommandsEnabled", "Send commands to serial port for Arduino Nano hand robot");
        setPropertyTooltip(roshambo, "serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        setPropertyTooltip(roshambo, "playSounds", "Play sound effects (Rock/Scissors/Paper) every time the decision changes and playSoundsMinIntervalMs has intervened");
        setPropertyTooltip(roshambo, "playSoundsMinIntervalMs", "Minimum time inteval for playing sound effects in ms");
        setPropertyTooltip(roshambo, "descisionThresholdActivation", "Minimum winner activation to activate hand or play a sound");
        setPropertyTooltip(roshambo, "showDecisionStatistics", "Displays statistics of decisions");
        setPropertyTooltip(roshambo, "playToWin", "If selected, symbol sent to hand will  beat human; if unselected, it ties the human");
        
        dvsSubsampler.setRectifyPolarities(true);
        dvsSubsampler.setNormalizeDVSForZsNullhop(true); // to make it work out of the box

        if(apsDvsNet!=null){
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, statistics);
        } 
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket out = super.filterPacket(in);
        if (!addedStatisticsListener) {
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, statistics);
            addedStatisticsListener = true;
        }
         if (useGameStatesToPlay) {
            out = getEnclosedFilterChain().filterPacket(out);
            if (tracker.getVisibleClusters().size() > 0) {
                RectangularClusterTracker.Cluster handCluster = tracker.getVisibleClusters().getFirst();
                boolean crossedUpper = false, crossedLower = false;
                int handlasty = (int) Math.round(handCluster.getLastPacketLocation().getY());
                int handy = (int) Math.round(handCluster.getLocation().getY());
                int upperthreshold = (int) (trackerUpperEdgeThreshold * chip.getSizeY());
                int lowerthreshold = (int) (trackerLowerEdgeThreshold * chip.getSizeY());
                if (handlasty > upperthreshold && handy < upperthreshold) {
                    crossedUpper = true;
                }
                if (handlasty > lowerthreshold && handy < lowerthreshold) {
                    crossedLower = true;
                }
                if (System.currentTimeMillis() - handTrackerStateUpdateTimeMs > throwIntervalTimeoutMs) {
                    handTrackerState = handTrackerState.Idle;
                }
                switch (handTrackerState) {
                    case Idle:
                        if (crossedUpper) {
                            handTrackerState = HandTrackerState.CrossedUpper;
                            handTrackerStateUpdateTimeMs = System.currentTimeMillis();
                        }
                        break;
                    case CrossedUpper:
                        if (crossedLower) {
                            handTrackerState = HandTrackerState.CrossedLower;
                            handTrackerStateUpdateTimeMs = System.currentTimeMillis();
                        }
                        break;
                    case CrossedLower:
                        handTrackerState = HandTrackerState.Idle;
                        handTrackerStateUpdateTimeMs = System.currentTimeMillis();
                }
            }
            switch (gameState) {
                case Idle:
                    if (handTrackerState == HandTrackerState.CrossedLower) {
                        gameState = GameState.Throw1;
                        twitch();
                        gameStateUpdateTimeMs = System.currentTimeMillis();
                    }
                    break;
                case Throw1:
                    if(System.currentTimeMillis()-gameStateUpdateTimeMs>throwIntervalTimeoutMs){
                        gameState=GameState.Idle;
                        break;
                    }
                    if (handTrackerState == HandTrackerState.CrossedLower) {
                        gameState = GameState.Throw2;
                        twitch();
                        gameStateUpdateTimeMs = System.currentTimeMillis();
                    }
                    break;
                case Throw2:
                   if(System.currentTimeMillis()-gameStateUpdateTimeMs>throwIntervalTimeoutMs){
                        gameState=GameState.Idle;
                        break;
                    }
                    if (handTrackerState == HandTrackerState.CrossedLower) { // only cross upper to show
                        gameState = GameState.ThrowShow;
                        sendDecisionToHand();
                        gameStateUpdateTimeMs = System.currentTimeMillis();
                    }
                    break;
                case ThrowShow:
                    if (System.currentTimeMillis() - gameStateUpdateTimeMs > winningSymbolShowTimeMs) {
                        gameState = GameState.Idle;
                    }
            }
        } else {
            sendDecisionToHand();
        }
        return out;
    }

    private void twitch() {
        sendCommandToHand('6');
    }

    private void sendCommandToHand(char cmd) {
        if (isSerialPortCommandsEnabled() && (serialPortOutputStream != null)) {
            try {
                serialPortOutputStream.write((byte) cmd);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }

    }

    private void sendDecisionToHand() {
        if(statistics.maxActivation<descisionThresholdActivation){
            return;
        }
        char cmd = 0;
        if (!playToWin) {
            switch (statistics.descision) {
                case DECISION_ROCK:
                    cmd = '3';
                    break;
                case DECISION_SCISSORS:
                    cmd = '2';
                    break;
                case DECISION_PAPER:
                    cmd = '1';
                    break;
                case DECISION_BACKGROUND:
                    cmd = '1';
                    break;
                default:
//                    log.warning("maxUnit=" + statistics.descision + " is not a valid network output state");
            }
        } else { // beat human
            switch (statistics.descision) {
                case DECISION_ROCK:
                    cmd = '1';
                    break;
                case DECISION_SCISSORS:
                    cmd = '3';
                    break;
                case DECISION_PAPER:
                    cmd = '2';
                    break;
                case DECISION_BACKGROUND:
                    cmd = '1';
                    break;
                default:
//                    log.warning("maxUnit=" + statistics.descision + " is not a valid network output state");
            }
        }
        sendCommandToHand(cmd);
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        statistics.reset();
        gameState = GameState.Idle;
        handTrackerState = HandTrackerState.Idle;
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
                log.warning("caught exception enabling serial port when filter was enabled: " + ex.toString());
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
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 72), true, false);
        }
        checkBlend(gl);
        if ((apsDvsNet != null) && (apsDvsNet.getOutputLayer() != null) && (apsDvsNet.getOutputLayer().getActivations() != null)) {
            drawDecisionOutput(gl, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        }
        if (showDecisionStatistics) {
            statistics.draw(gl);
        }
        if (useGameStatesToPlay) {
            int upperthreshold = (int) (trackerUpperEdgeThreshold * chip.getSizeY());
            int lowerthreshold = (int) (trackerLowerEdgeThreshold * chip.getSizeY());
            gl.glPushMatrix();
            gl.glColor3f(.25f, .25f, .25f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, upperthreshold);
            gl.glVertex2f(chip.getSizeX(), upperthreshold);
            gl.glVertex2f(0, lowerthreshold);
            gl.glVertex2f(chip.getSizeX(), lowerthreshold);
            gl.glEnd();

            String s = gameState.toString() + " " + handTrackerState.toString();
            textRenderer.setColor(1, 1, 1, 1);
            textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
            Rectangle2D r = textRenderer.getBounds(s);
            textRenderer.draw(s, (drawable.getSurfaceWidth() / 2) - ((int) r.getWidth() / 2), (int) (0.75f * drawable.getSurfaceHeight()));
            textRenderer.endRendering();
            gl.glPopMatrix();
        }
        if (playSounds && isShowOutputAsBarChart()) {
            gl.glColor3f(.5f, 0, 0);
            gl.glBegin(GL.GL_LINES);
            final float h = descisionThresholdActivation * DavisCNNPureJava.HISTOGRAM_HEIGHT_FRACTION * chip.getSizeY();
            gl.glVertex2f(0, h);
            gl.glVertex2f(chip.getSizeX(), h);
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
        textRenderer.setColor(brightness, brightness, brightness, 1);
        textRenderer.beginRendering(width, height);
        if ((statistics.descision >= 0) && (statistics.descision < DECISION_STRINGS.length)) {
            Rectangle2D r = textRenderer.getBounds(DECISION_STRINGS[statistics.descision]);
            String decisionString=DECISION_STRINGS[statistics.descision];
            if(apsDvsNet.getLabels()!=null && apsDvsNet.getLabels().size()>0){
                decisionString=apsDvsNet.getLabels().get(statistics.descision);
            }
            textRenderer.draw(decisionString, (width / 2) - ((int) r.getWidth() / 2), height / 2);
            if (playSounds && statistics.descision >= 0 && statistics.descision < 3 && statistics.maxActivation > descisionThresholdActivation) {
                if (soundPlayer == null) {
                    soundPlayer = new SoundPlayer();
                }
                soundPlayer.playSound(statistics.descision);
            }
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
        if (!isFilterEnabled()) {
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
        if (!isSerialPortCommandsEnabled()) {
            return;
        }
        if (serialPort != null) {
            closeSerial();
        }
        StringBuilder sb = new StringBuilder("serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        for (String s : availableSerialPorts) {
            sb.append(s).append(" ");
        }
        log.info(sb.toString());
        if (!availableSerialPorts.contains(serialPortName)) {
            log.warning(serialPortName + " is not in avaiable " + sb.toString());
            return;
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            log.warning("null serial port returned when trying to open " + serialPortName + "; available " + sb.toString());
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
//        log.info("closed serial port");
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

    /**
     * @return the playSounds
     */
    public boolean isPlaySounds() {
        return playSounds;
    }

    /**
     * @param playSounds the playSounds to set
     */
    public void setPlaySounds(boolean playSounds) {
        boolean old = this.playSounds;
        this.playSounds = playSounds;
        putBoolean("playSounds", playSounds);
        getSupport().firePropertyChange("playSounds", old, this.playSounds); // in case disabled by error loading file
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
        int descision = -1;
        boolean outputChanged = false;

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
             if (evt.getPropertyName() == AbstractDavisCNN.EVENT_MADE_DECISION) {
                int lastOutput = descision;
                AbstractDavisCNN net = (AbstractDavisCNN) evt.getNewValue();
                maxActivation = Float.NEGATIVE_INFINITY;
                descision = -1;
                try {
                    for (int i = 0; i < NUM_CLASSES; i++) {
                        float output = net.getOutputLayer().getActivations()[i];
                        lowpassFilteredOutputUnits[i] = ((1 - decisionLowPassMixingFactor) * lowpassFilteredOutputUnits[i]) + (output * decisionLowPassMixingFactor);
                        if (lowpassFilteredOutputUnits[i] > maxActivation) {
                            maxActivation = lowpassFilteredOutputUnits[i];
                            descision = i;
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.warning("Array index out of bounds in rendering output. Did you load a valid CNN with 3 (or more) output units?");

                }
                decisionCounts[descision]++;
                totalCount++;
                if ((descision != lastOutput)) {
                    // CNN output changed, respond here
                    outputChanged = true;
                } else {
                    outputChanged = false;
                }

            } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
                reset();
            }
        }

        private void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(.8f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }

    private class SoundPlayer {

        private String[] soundFiles = {"paper.wav", "scissors.wav", "rock.wav"};
        private long lastTimePlayed = Integer.MIN_VALUE, lastSymbolPlayed = -1, lastInput = -1;
        private SoundWavFilePlayer[] soundPlayers = new SoundWavFilePlayer[soundFiles.length];
        private String path = "/eu/visualize/ini/convnet/resources/";

        public SoundPlayer() {
            for (int i = 0; i < soundPlayers.length; i++) {
                try {
                    soundPlayers[i] = new SoundWavFilePlayer(path + soundFiles[i]);
                } catch (Exception ex) {
                    log.warning("couldn't load " + path + soundFiles[i] + ": caught " + ex + "; disabling playSounds");
                    setPlaySounds(false);
                }
            }
        }

        void playSound(int symbol) {
            if (symbol < 0 || symbol >= soundFiles.length) {
                return;
            }
            if (soundPlayers[symbol] == null) {
                log.warning("No player for symbol " + symbol);
                return;
            }

            if (soundPlayers[symbol].isPlaying()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastTimePlayed < playSoundsMinIntervalMs) {
                return;
            }

            if (soundPlayers[symbol].isPlaying()) {
                return;
            }
            if (symbol == lastSymbolPlayed) {
                return;
            }
            lastInput = symbol;
            lastTimePlayed = now;
            lastSymbolPlayed = symbol;
            soundPlayers[symbol].play();
        }
    }

    /**
     * @return the playSoundsMinIntervalMs
     */
    public int getPlaySoundsMinIntervalMs() {
        return playSoundsMinIntervalMs;
    }

    /**
     * @param playSoundsMinIntervalMs the playSoundsMinIntervalMs to set
     */
    public void setPlaySoundsMinIntervalMs(int playSoundsMinIntervalMs) {
        this.playSoundsMinIntervalMs = playSoundsMinIntervalMs;
        putInt("playSoundsMinIntervalMs", playSoundsMinIntervalMs);
    }

    /**
     * @return the descisionThresholdActivation
     */
    public float getDescisionThresholdActivation() {
        return descisionThresholdActivation;
    }

    /**
     * @param descisionThresholdActivation the descisionThresholdActivation to
 set
     */
    public void setDescisionThresholdActivation(float descisionThresholdActivation) {
        if (descisionThresholdActivation > 1) {
            descisionThresholdActivation = 1;
        } else if (descisionThresholdActivation < 0) {
            descisionThresholdActivation = 0;
        }
        this.descisionThresholdActivation = descisionThresholdActivation;
        putFloat("descisionThresholdActivation", descisionThresholdActivation);
    }

    /**
     * @return the showDecisionStatistics
     */
    public boolean isShowDecisionStatistics() {
        return showDecisionStatistics;
    }

    /**
     * @param showDecisionStatistics the showDecisionStatistics to set
     */
    public void setShowDecisionStatistics(boolean showDecisionStatistics) {
        this.showDecisionStatistics = showDecisionStatistics;
        putBoolean("showDecisionStatistics", showDecisionStatistics);
    }

    /**
     * @return the playToWin
     */
    public boolean isPlayToWin() {
        return playToWin;
    }

    /**
     * @param playToWin the playToWin to set
     */
    public void setPlayToWin(boolean playToWin) {
        this.playToWin = playToWin;
        putBoolean("playToWin", playToWin);
    }

    /**
     * @return the trackerUpperEdgeThreshold
     */
    public float getTrackerUpperEdgeThreshold() {
        return trackerUpperEdgeThreshold;
    }

    /**
     * @param trackerUpperEdgeThreshold the trackerUpperEdgeThreshold to set
     */
    public void setTrackerUpperEdgeThreshold(float trackerUpperEdgeThreshold) {
        this.trackerUpperEdgeThreshold = trackerUpperEdgeThreshold;
        putFloat("trackerUpperEdgeThreshold", trackerUpperEdgeThreshold);
    }

    /**
     * @return the trackerLowerEdgeThreshold
     */
    public float getTrackerLowerEdgeThreshold() {
        return trackerLowerEdgeThreshold;
    }

    /**
     * @param trackerLowerEdgeThreshold the trackerLowerEdgeThreshold to set
     */
    public void setTrackerLowerEdgeThreshold(float trackerLowerEdgeThreshold) {
        this.trackerLowerEdgeThreshold = trackerLowerEdgeThreshold;
        putFloat("trackerLowerEdgeThreshold", trackerLowerEdgeThreshold);
    }


    /**
     * @return the throwIntervalTimeoutMs
     */
    public int getThrowIntervalTimeoutMs() {
        return throwIntervalTimeoutMs;
    }

    /**
     * @param throwIntervalTimeoutMs the throwIntervalTimeoutMs to set
     */
    public void setThrowIntervalTimeoutMs(int throwIntervalTimeoutMs) {
        this.throwIntervalTimeoutMs = throwIntervalTimeoutMs;
        putInt("throwIntervalTimeoutMs", throwIntervalTimeoutMs);
    }

    /**
     * @return the winningSymbolShowTimeMs
     */
    public int getWinningSymbolShowTimeMs() {
        return winningSymbolShowTimeMs;
    }

    /**
     * @param winningSymbolShowTimeMs the winningSymbolShowTimeMs to set
     */
    public void setWinningSymbolShowTimeMs(int winningSymbolShowTimeMs) {
        this.winningSymbolShowTimeMs = winningSymbolShowTimeMs;
        putInt("winningSymbolShowTimeMs", winningSymbolShowTimeMs);
    }

    /**
     * @return the useGameStatesToPlay
     */
    public boolean isUseGameStatesToPlay() {
        return useGameStatesToPlay;
    }

    /**
     * @param useGameStatesToPlay the useGameStatesToPlay to set
     */
    public void setUseGameStatesToPlay(boolean useGameStatesToPlay) {
        this.useGameStatesToPlay = useGameStatesToPlay;
        putBoolean("useGameStatesToPlay", useGameStatesToPlay);
        tracker.setFilterEnabled(useGameStatesToPlay);
    }

}
