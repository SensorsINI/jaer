/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and openChannel the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Color;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Extends DavisDeepLearnCnnProcessor to add annotation graphics to show
 * steering decision.
 *
 * @author Tobi
 */
@Description("Displays Visualise steering ConvNet results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VisualiseSteeringConvNet extends DavisDeepLearnCnnProcessor implements PropertyChangeListener {

    private static final int LEFT = 0, CENTER = 1, RIGHT = 2, INVISIBLE = 3; // define output cell types
    volatile private boolean hideSteeringOutput = getBoolean("hideOutput", false);
    volatile private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
    volatile private boolean showStatistics = getBoolean("showStatistics", true);
    private TargetLabeler targetLabeler = null;
    private Error error = new Error();
//    /** This object used to publish the results to ROS */
//    public VisualiseSteeringNetRosNodePublisher visualiseSteeringNetRosNodePublisher=new VisualiseSteeringNetRosNodePublisher();

    // UDP output to client, e.g. ROS
    volatile private boolean sendUDPSteeringMessages = getBoolean("sendUDPSteeringMessages", false);
    volatile private boolean forceNetworkOutpout = getBoolean("forceNetworkOutpout", false);
    volatile private int forcedNetworkOutputValue = getInt("forcedNetworkOutputValue", 3); // default is prey invisible output
    private String host = getString("host", "localhost");
    private int port = getInt("host", 5678);
    private DatagramSocket socket = null;
    private InetSocketAddress client = null;
    private DatagramChannel channel = null;
    private ByteBuffer buf = ByteBuffer.allocate(2);
    private int seqNum = 0;
    private int[] decisionArray = new int[2];
    private int[] decisionLowPassArray = new int[3];
    private int savedDecision = -1;
    private int counterD = 0;
    private float[] LCRNstate = new float[]{0.5f, 0.5f, 0.5f, 0.5f};
    volatile private boolean apply_LR_RL_constraint = getBoolean("apply_LR_RL_constraint", true);
    volatile private boolean apply_LNR_RNL_constraint = getBoolean("apply_LNR_RNL_constraint", true);
    volatile private boolean apply_CN_NC_constraint = getBoolean("apply_CN_NC_constraint", true);
    volatile private float LCRNstep = getFloat("LCRNstep", 1f);

    public VisualiseSteeringConvNet(AEChip chip) {
        super(chip);
        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";
        String udp = "UDP messages";
        setPropertyTooltip(disp, "showAnalogDecisionOutput", "shows output units as analog shading rather than binary");
        setPropertyTooltip(disp, "hideSteeringOutput", "hides steering output unit rendering as shading over sensor image. If the prey is invisible no rectangle is rendered when showAnalogDecisionOutput is deselected.");
        setPropertyTooltip(anal, "pixelErrorAllowedForSteering", "If ground truth location is within this many pixels of closest border then the descision is still counted as corret");
        setPropertyTooltip(disp, "showStatistics", "shows statistics of DVS frame rate and error rate (when ground truth TargetLabeler file is loaded)");
        setPropertyTooltip(udp, "sendUDPSteeringMessages", "sends UDP packets with steering network output to host:port in hostAndPort");
        setPropertyTooltip(udp, "host", "hostname or IP address to send UDP messages to, e.g. localhost");
        setPropertyTooltip(udp, "port", "destination UDP port address to send UDP messages to, e.g. 5678");
        setPropertyTooltip(udp, "forcedNetworkOutputValue", "forced value of network output sent to client (0=left, 1=middle, 2=right, 3=invisible)");
        setPropertyTooltip(udp, "forceNetworkOutpout", "force (override) network output classification to forcedNetworkOutputValue");
        setPropertyTooltip(udp, "apply_LR_RL_constraint", "force (override) network output classification to make sure there is no switching from L to R or viceversa directly");
        setPropertyTooltip(udp, "apply_LNR_RNL_constraint", "force (override) network output classification to make sure there is no switching from L to N and to R or viceversa, since the predator will see the prey back from the same last seen steering output (it spins in the last seen direction)");
        setPropertyTooltip(udp, "apply_CN_NC_constraint", "force (override) network output classification to make sure there is no switching from C to N or viceversa directly");
        setPropertyTooltip(udp, "LCRNstep", "step from one state (LCR or N) to another (if 1, no lowpass filtering, if lower, then slower transitions)");

        FilterChain chain = new FilterChain(chip);
        targetLabeler = new TargetLabeler(chip); // used to validate whether descisions are correct or not
        chain.add(targetLabeler);
        setEnclosedFilterChain(chain);
        apsDvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
//        dvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        targetLabeler.filterPacket(in);
        EventPacket out = super.filterPacket(in);
        return out;
    }

    public int getPixelErrorAllowedForSteering() {
        return error.getPixelErrorAllowedForSteering();
    }

    public void setPixelErrorAllowedForSteering(int pixelErrorAllowedForSteering) {
        error.setPixelErrorAllowedForSteering(pixelErrorAllowedForSteering);
    }

//    private Boolean correctDescisionFromTargetLabeler(TargetLabeler targetLabeler, DeepLearnCnnNetwork net) {
//        if (targetLabeler.getTargetLocation() == null) {
//            return null; // no location labeled for this time
//        }
//        Point p = targetLabeler.getTargetLocation().location;
//        if (p == null) {
//            if (net.outputLayer.maxActivatedUnit == 3) {
//                return true; // no target seen
//            }
//        } else {
//            int x = p.x;
//            int third = (x * 3) / chip.getSizeX();
//            if (third == net.outputLayer.maxActivatedUnit) {
//                return true;
//            }
//        }
//        return false;
//    }
    @Override
    public void resetFilter() {
        super.resetFilter();
        error.reset();

    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes && !targetLabeler.hasLocations()) {
            Runnable r = new Runnable() {

                @Override
                public void run() {
                    targetLabeler.loadLastLocations();
                }
            };
            SwingUtilities.invokeLater(r);
        }
        if (yes && sendUDPSteeringMessages) {
            try {
                openChannel();
            } catch (IOException ex) {
                log.warning("Caught exception when trying to open datagram channel to host:port - " + ex);
            }
        }
        if (!yes) {
            closeChannel();
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        targetLabeler.annotate(drawable);
        if (hideSteeringOutput) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int third = chip.getSizeX() / 3;
        int sy = chip.getSizeY();
        if (apsDvsNet != null && apsDvsNet.outputLayer != null && apsDvsNet.outputLayer.activations != null) {
            drawDecisionOutput(third, gl, sy, apsDvsNet, Color.RED);
        }
//        if (dvsNet != null && dvsNet.outputLayer != null && dvsNet.outputLayer.activations != null && isProcessDVSTimeSlices()) {
//            drawDecisionOutput(third, gl, sy, dvsNet, Color.YELLOW);
//        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .5f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        MultilineAnnotationTextRenderer.renderMultilineString(String.format("Current state = [L: %6.1f]  [C: %6.1f]  [R: %6.1f]  [N: %6.1f]", LCRNstate[0], LCRNstate[1], LCRNstate[2], LCRNstate[3]));
        MultilineAnnotationTextRenderer.renderMultilineString(error.toString());
        if (showStatistics) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .5f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            if (dvsSubsampler != null) {
                MultilineAnnotationTextRenderer.renderMultilineString(String.format("DVS subsampler, inst/avg interval %6.1f/%6.1f ms", dvsSubsampler.getLastSubsamplerFrameIntervalUs() * 1e-3f, dvsSubsampler.getFilteredSubsamplerIntervalUs() * 1e-3f));
            }
            MultilineAnnotationTextRenderer.renderMultilineString(error.toString());
        }
        //        if (totalDecisions > 0) {
//            float errorRate = (float) incorrect / totalDecisions;
//            String s = String.format("Error rate %.2f%% (total=%d correct=%d incorrect=%d)\n", errorRate * 100, totalDecisions, correct, incorrect);
//            MultilineAnnotationTextRenderer.renderMultilineString(s);
//        }
    }

    private void drawDecisionOutput(int third, GL2 gl, int sy, DeepLearnCnnNetwork net, Color color) {
        // 0=left, 1=center, 2=right, 3=no target
        int decision = net.outputLayer.maxActivatedUnit;
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float[] cv = color.getColorComponents(null);
        if (showAnalogDecisionOutput) {
            final float brightness = .3f; // brightness scale
            for (int i = 0; i < 3; i++) {
                int x0 = third * i;
                int x1 = x0 + third;
                float shade = brightness * net.outputLayer.activations[i];
                gl.glColor3f((shade * r), (shade * g), (shade * b));
                gl.glRecti(x0, 0, x1, sy);
                gl.glRecti(x0, 0, x1, sy);
            }
            float shade = brightness * net.outputLayer.activations[3]; // no target
            gl.glColor3f((shade * r), (shade * g), (shade * b));
            gl.glRecti(0, 0, chip.getSizeX(), sy / 8);

        } else if (decision != INVISIBLE) {
            int x0 = third * decision;
            int x1 = x0 + third;
            float shade = .5f;
            gl.glColor3f((shade * r), (shade * g), (shade * b));
            gl.glRecti(x0, 0, x1, sy);
        }
    }

    public void applyConstraints(DeepLearnCnnNetwork net) {
        int currentDecision = net.outputLayer.maxActivatedUnit;
        float maxLCRN = 0;
        int maxLCRNindex = -1;
        for (int i = 0; i < 4; i++) {
            if (i == currentDecision) {
                LCRNstate[i] = LCRNstate[i] + LCRNstep;
                if(LCRNstate[i]>1){
                    LCRNstate[i] = 1;
                }
                if (LCRNstate[i] > maxLCRN) {
                    maxLCRN = LCRNstate[i];
                    maxLCRNindex = i;
                }
            } else {
                LCRNstate[i] = LCRNstate[i] - LCRNstep;
                                if(LCRNstate[i]<0){
                    LCRNstate[i] = 0;
                }
                if (LCRNstate[i] > maxLCRN) {
                    maxLCRN = LCRNstate[i];
                    maxLCRNindex = i;
                }
            }
        }
        net.outputLayer.maxActivatedUnit = maxLCRNindex;

        if (apply_CN_NC_constraint) {// Cannot switch from C to N and viceversa
            if (currentDecision == 1 && decisionArray[1] == 3) {
                net.outputLayer.maxActivatedUnit = 3;
            } else if (currentDecision == 3 && decisionArray[1] == 1) {
                net.outputLayer.maxActivatedUnit = 1;
            }
        }
        if (apply_LNR_RNL_constraint) { //Remember last position before going to N, the robot will reappear from there
            // Possible transition to N (RN or LN)
            if (currentDecision == 3 && decisionArray[1] == 0) {
                savedDecision = 0;
            } else if (currentDecision == 3 && decisionArray[1] == 2) {
                savedDecision = 2;
            }
            // Possible transition back from N (NR or LN)
            if (savedDecision >= 0) {//Real value saved
                if (currentDecision == 0 && decisionArray[1] == 3) {
                    if (currentDecision != savedDecision) {
                        net.outputLayer.maxActivatedUnit = 3;
                    } else {
                        savedDecision = -1;
                    }
                } else if (currentDecision == 2 && decisionArray[1] == 3) {
                    if (currentDecision != savedDecision) {
                        net.outputLayer.maxActivatedUnit = 3;
                    } else {
                        savedDecision = -1;
                    }
                }
            }
        }
        if (apply_LR_RL_constraint) {// Cannot switch from R to L and viceversa
            if (currentDecision == 0 && decisionArray[1] == 2) {
                net.outputLayer.maxActivatedUnit = 2;
            } else if (currentDecision == 2 && decisionArray[1] == 0) {
                net.outputLayer.maxActivatedUnit = 0;
            }
        }
        //Update decision
        decisionArray[0] = decisionArray[1];
        decisionArray[1] = net.outputLayer.maxActivatedUnit;
    }

    /**
     * @return the hideSteeringOutput
     */
    public boolean isHideSteeringOutput() {
        return hideSteeringOutput;
    }

    /**
     * @param hideSteeringOutput the hideSteeringOutput to set
     */
    public void setHideSteeringOutput(boolean hideSteeringOutput) {
        this.hideSteeringOutput = hideSteeringOutput;
        putBoolean("hideSteeringOutput", hideSteeringOutput);
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

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() != DeepLearnCnnNetwork.EVENT_MADE_DECISION) {
            super.propertyChange(evt);

        } else {
            DeepLearnCnnNetwork net = (DeepLearnCnnNetwork) evt.getNewValue();
//            if (targetLabeler.hasLocations()) {
            error.addSample(targetLabeler.getTargetLocation(), net.outputLayer.maxActivatedUnit, net.isLastInputTypeProcessedWasApsFrame());
//            }
            applyConstraints(net);
            if (sendUDPSteeringMessages) {
                if (checkClient()) { // if client not there, just continue - maybe it comes back
                    buf.clear();
                    buf.put((byte) (seqNum & 0xFF)); // mask bits to cast to unsigned byte value 0-255
                    seqNum++;
                    if (seqNum > 255) {
                        seqNum = 0;
                    }
                    byte msg = (byte) (forceNetworkOutpout ? forcedNetworkOutputValue : net.outputLayer.maxActivatedUnit);
                    buf.put(msg);
                    try {
//                        log.info("sending buf="+buf+" to client="+client);
//                        log.info("sending seqNum=" + seqNum + " with msg=" + msg);
                        channel.send(buf, client);
                    } catch (IOException e) {
                        log.warning("Exception trying to send UDP datagram to ROS: " + e);
                    }
                }
            }
        }
    }

    /**
     * @return the sendUDPSteeringMessages
     */
    public boolean isSendUDPSteeringMessages() {
        return sendUDPSteeringMessages;
    }

    /**
     * @param sendUDPSteeringMessages the sendUDPSteeringMessages to set
     */
    synchronized public void setSendUDPSteeringMessages(boolean sendUDPSteeringMessages) {
        this.sendUDPSteeringMessages = sendUDPSteeringMessages;
        putBoolean("sendUDPSteeringMessages", sendUDPSteeringMessages);
        if (sendUDPSteeringMessages) {
            try {
                openChannel();
            } catch (IOException ex) {
                log.warning("Caught exception when trying to open datagram channel to host:port - " + ex);
            }
        } else {
            closeChannel();
        }
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        try {
            InetAddress udpAddress = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            log.warning("can't find " + host + ": caught " + e);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), e.toString(), "Bad host for UDP steering messages", JOptionPane.WARNING_MESSAGE);
            return;
        }
        this.host = host;
        putString("host", host);
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
        putInt("port", port);
    }

    private class Error {

        int totalCount, totalCorrect, totalIncorrect;
        int[] correct = new int[4], incorrect = new int[4], count = new int[4];
        protected int pixelErrorAllowedForSteering = getInt("pixelErrorAllowedForSteering", 10);
        int dvsTotalCount, dvsCorrect, dvsIncorrect;
        int apsTotalCount, apsCorrect, apsIncorrect;
        char[] outputChars = {'L', 'M', 'R', 'I'};

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
            dvsTotalCount = 0;
            dvsCorrect = 0;
            dvsIncorrect = 0;
            apsTotalCount = 0;
            apsCorrect = 0;
            apsIncorrect = 0;
        }

        void addSample(TargetLabeler.TargetLocation gtTargetLocation, int descision, boolean apsType) {
            totalCount++;
            if (apsType) {
                apsTotalCount++;
            } else {
                dvsTotalCount++;
            }

            int third = chip.getSizeX() / 3;

            if (gtTargetLocation != null && gtTargetLocation.location != null) {
                // we have a location that is not null for the target
                int x = (int) Math.floor(gtTargetLocation.location.x);
                int gtDescision = x / third;
                if (gtDescision < 0 || gtDescision > 3) {
                    return; // bad descision output, should not happen
                }
                count[gtDescision]++;
                if (gtDescision == descision) {
                    correct[gtDescision]++;
                    totalCorrect++;
                    if (apsType) {
                        apsCorrect++;
                    } else {
                        dvsCorrect++;
                    }
                } else if (getPixelErrorAllowedForSteering() == 0) {
                    incorrect[gtDescision]++;
                    totalIncorrect++;
                    if (apsType) {
                        apsIncorrect++;
                    } else {
                        dvsIncorrect++;
                    }
                } else {
                    boolean wrong = true;
                    // might be error but maybe not if the descision is e.g. to left and the target location is just over the border to middle
                    float gtX = gtTargetLocation.location.x;
                    if (descision == LEFT && gtX < third + pixelErrorAllowedForSteering) {
                        wrong = false;
                    } else if (descision == CENTER && gtX >= third - pixelErrorAllowedForSteering && gtX <= 2 * third + pixelErrorAllowedForSteering) {
                        wrong = false;
                    } else if (descision == RIGHT && gtX >= 2 * third - pixelErrorAllowedForSteering) {
                        wrong = false;
                    }
                    if (wrong) {
                        incorrect[gtDescision]++;
                        totalIncorrect++;
                        if (apsType) {
                            apsIncorrect++;
                        } else {
                            dvsIncorrect++;
                        }

                    }
                }

            } else { // no target in ground truth (prey out of view)
                count[INVISIBLE]++;
                if (descision == INVISIBLE) {
                    correct[INVISIBLE]++;
                    totalCorrect++;
                    if (apsType) {
                        apsCorrect++;
                    } else {
                        dvsCorrect++;
                    }
                } else {
                    incorrect[INVISIBLE]++;
                    totalIncorrect++;
                    if (apsType) {
                        apsIncorrect++;
                    } else {
                        dvsIncorrect++;
                    }
                }
            }
        }

        @Override
        public String toString() {
//            if (targetLabeler.hasLocations() == false) {
//                return "Error: No ground truth target locations loaded";
//            }
            if (totalCount == 0) {
                return "Error: no samples yet";
            }
            StringBuilder sb = new StringBuilder("Error rates: ");
            sb.append(String.format(" Total=%.1f%% (%d/%d) \n(", (100f * totalIncorrect) / totalCount, totalIncorrect, totalCount));
            for (int i = 0; i < 4; i++) {
                if (count[i] == 0) {
                    sb.append(String.format("%c: 0/0 ", outputChars[i]));
                } else {
                    sb.append(String.format("%c: %.1f%% (%d)  ", outputChars[i], (100f * incorrect[i]) / count[i], count[i]));
                }
            }
            sb.append(String.format("\naps=%.1f%% (%d/%d) dvs=%.1f%% (%d/%d)",
                    (100f * apsIncorrect) / apsTotalCount, apsIncorrect, apsTotalCount,
                    (100f * dvsIncorrect) / dvsTotalCount, dvsIncorrect, dvsTotalCount));

            sb.append(")");
            return sb.toString();
        }

        /**
         * @return the pixelErrorAllowedForSteering
         */
        public int getPixelErrorAllowedForSteering() {
            return pixelErrorAllowedForSteering;
        }

        /**
         * @param pixelErrorAllowedForSteering the pixelErrorAllowedForSteering
         * to set
         */
        public void setPixelErrorAllowedForSteering(int pixelErrorAllowedForSteering) {
            this.pixelErrorAllowedForSteering = pixelErrorAllowedForSteering;
            putInt("pixelErrorAllowedForSteering", pixelErrorAllowedForSteering);
        }

    }

    /**
     * returns true if socket exists and is bound
     */
    private boolean checkClient() {
        if (socket == null) {
            return false;
        }

        try {
            if (socket.isBound()) {
                return true;
            }
            client = new InetSocketAddress(host, port);
            return true;
        } catch (Exception se) { // IllegalArgumentException or SecurityException
            log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
            return false;
        }
    }

    public void openChannel() throws IOException {
        closeChannel();
        channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        socket.setTrafficClass(0x10 + 0x08); // low delay
        log.info("opened channel on local port to send UDP messages to ROS. local port number =" + socket.getLocalPort());
    }

    public void closeChannel() {
        if (socket == null) {
            return;
        }
        log.info("closing local socket " + socket + " to UDP client");
        socket.close();
    }

    /**
     * @return the forceNetworkOutpout
     */
    public boolean isForceNetworkOutpout() {
        return forceNetworkOutpout;
    }

    /**
     * @param forceNetworkOutpout the forceNetworkOutpout to set
     */
    public void setForceNetworkOutpout(boolean forceNetworkOutpout) {
        this.forceNetworkOutpout = forceNetworkOutpout;
        putBoolean("forceNetworkOutpout", forceNetworkOutpout);
    }

    /**
     * @return the forcedNetworkOutputValue
     */
    public int getForcedNetworkOutputValue() {
        return forcedNetworkOutputValue;
    }

    /**
     * @param forcedNetworkOutputValue the forcedNetworkOutputValue to set
     */
    public void setForcedNetworkOutputValue(int forcedNetworkOutputValue) {
        if (forcedNetworkOutputValue < 0) {
            forcedNetworkOutputValue = 0;
        } else if (forcedNetworkOutputValue > 3) {
            forcedNetworkOutputValue = 3;
        }
        this.forcedNetworkOutputValue = forcedNetworkOutputValue;
        putInt("forcedNetworkOutputValue", forcedNetworkOutputValue);
    }

    /**
     * @return the showStatistics
     */
    public boolean isShowStatistics() {
        return showStatistics;
    }

    /**
     * @param showStatistics the showStatistics to set
     */
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
        putBoolean("showStatistics", showStatistics);
    }

    /**
     * @return the LCRNstep
     */
    public float getLCRNstep() {
        return LCRNstep;
    }

    /**
     * @param LCRNstep the LCRNstep to set
     */
    public void setLCRNstep(float LCRNstep) {
        this.LCRNstep = LCRNstep;
        putFloat("LCRNstep", LCRNstep);
    }

    /**
     * @return the apply_CN_NC_constraint
     */
    public boolean isApply_CN_NC_constraint() {
        return apply_CN_NC_constraint;
    }

    /**
     * @param apply_CN_NC_constraint the apply_CN_NC_constraint to set
     */
    public void setApply_CN_NC_constraint(boolean apply_CN_NC_constraint) {
        this.apply_CN_NC_constraint = apply_CN_NC_constraint;
        putBoolean("apply_CN_NC_constraint", apply_CN_NC_constraint);
    }

    /**
     * @return the apply_LNR_RNL_constraint
     */
    public boolean isApply_LNR_RNL_constraint() {
        return apply_LNR_RNL_constraint;
    }

    /**
     * @param apply_LNR_RNL_constraint the apply_LNR_RNL_constraint to set
     */
    public void setApply_LNR_RNL_constraint(boolean apply_LNR_RNL_constraint) {
        this.apply_LNR_RNL_constraint = apply_LNR_RNL_constraint;
        putBoolean("apply_LNR_RNL_constraint", apply_LNR_RNL_constraint);
    }

    /**
     * @return the apply_LR_RL_constraint
     */
    public boolean isApply_LR_RL_constraint() {
        return apply_LR_RL_constraint;
    }

    /**
     * @param apply_LR_RL_constraint the apply_LR_RL_constraint to set
     */
    public void setApply_LR_RL_constraint(boolean apply_LR_RL_constraint) {
        this.apply_LR_RL_constraint = apply_LR_RL_constraint;
        putBoolean("apply_LR_RL_constraint", apply_LR_RL_constraint);
    }
}
