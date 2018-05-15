/*
 * Copyright (C) 2018 tobi.
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

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.avioutput.DvsSliceAviWriter;

/**
 * Incremental Roshambo learning demo
 *
 * @author Tobi Delbruck/Iulia Lungu
 */
@Description("Incremental learning demo for Roshambo + other finger gestures; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RoShamBoIncremental extends RoShamBoCNN {

    private DvsSliceAviWriter aviWriter = null;
    private Path lastSymbolsPath = Paths.get(getString("lastSymbolsPath", ""));
//    private DatagramChannel channel = null;
    private DatagramSocket sendToSocket = null, listenOnSocket = null;
    private InetSocketAddress client = null;
    private String host = getString("hostname", "localhost");
    public static final int DEFAULT_SENDTO_PORT = 14334;
    public static final int DEFAULT_LISTENON_PORT = 14335;
    private int portSendTo = getInt("portSendTo", DEFAULT_SENDTO_PORT);
    private int portListenOn = getInt("portListenOn", DEFAULT_LISTENON_PORT);
    private static final String CMD_NEW_SAMPLES_AVAILABLE = "newsymbol",
            CMD_PROGRESS = "progress",
            CMD_LOAD_NETWORK = "loadnetwork",
            CMD_LOAD_CLASS_MEANS = "loadclassmeans",
            CMD_CANCEL_TRAINING = "cancel",
            CMD_PING = "ping",
            CMD_PONG = "pong", CMD_RESET_TO_BASE_NETWORK = "reset";
    private static String KEY_CLASS_MEANS_FILENAME = "classMeansFilename";
    private Thread portListenerThread = null;
    private ProgressMonitor progressMonitor = null;
    private String lastNewClassName = getString("lastNewClassName", "");

    // iCaRL class means
    private float[][] classMeans; // each row is one class, each class has N components that are mean values of last fully connected layer activations

    // to test, open a terminal, and use netcat -u localhost portSendTo in one panel and network -ul portListenOn in another panel
    public RoShamBoIncremental(AEChip chip) {
        super(chip);
        String learn = "0. Incremental learning";
        setPropertyTooltip(learn, "SampleNewClass", "Toggle collecting sample data for a new class");
        setPropertyTooltip(learn, "StartTraining", "Starts training on samples");
        setPropertyTooltip(learn, "CancelTraining", "Cancels ongoing training");
        setPropertyTooltip(learn, "ChooseSamplesFolder", "Choose a folder to store the symbol AVI data files");
        setPropertyTooltip(learn, "LoadClassMeanVectors", "Loads class mean vectors from a file");
        setPropertyTooltip(learn, "hostname", "learning host name (IP or DNS)");
        setPropertyTooltip(learn, "portSendTo", "learning host port number that we send to");
        setPropertyTooltip(learn, "portListenOn", "local port number we listen on to get message back from learning server");
        setPropertyTooltip(learn, "Ping", "Sends \"ping\" to learning server. Pops up confirmation when \"pong\" is returned");
        setPropertyTooltip(learn, "ResetToBaseNetwork", "Revert network back to last manually-loaded CNN");
        aviWriter = new DvsSliceAviWriter(chip);
        aviWriter.setEnclosed(true, this);
        aviWriter.setFrameRate(60);
        aviWriter.getDvsFrame().setOutputImageHeight(64);
        aviWriter.getDvsFrame().setOutputImageWidth(64);
        getEnclosedFilterChain().add(aviWriter);
        statistics = new Statistics(); // overrides the super's version of Statistics

    }

    @Override
    public void initFilter() {
        super.initFilter();
        try {
            if (isPreferenceStored(KEY_CLASS_MEANS_FILENAME)) {
                File f = new File(getString(KEY_CLASS_MEANS_FILENAME, ""));
                loadClassMeans(f);
            }
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
//            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load class means, caught exception " + ex + ". See console for logging.", "Bad class means file", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    protected void loadNetwork(File f) throws Exception {
        super.loadNetwork(f);
        if (apsDvsNet != null) {
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, statistics);
        }
    }

    public void doResetToBaseNetwork() {
        try {
            loadNetwork(new File(getLastManuallyLoadedNetwork()));
            loadLabels(new File(getLastManuallyLoadedLabels()));
            sendUDPMessage(CMD_RESET_TO_BASE_NETWORK);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e.getCause());
            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Couldn't load base network: " + e.toString(), "Bad network file", JOptionPane.WARNING_MESSAGE);
        }
    }

    public synchronized void doLoadClassMeanVectors() {
        File file = null;
        file = openFileDialogAndGetFile("Choose a class means file, one vector of ascii float values per line", KEY_CLASS_MEANS_FILENAME, "classmeans.txt", "Text file", "txt");

        if (file == null) {
            return;
        }
        try {
            loadClassMeans(file);
        } catch (Exception ex) {
            Logger.getLogger(DavisClassifierCNNProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load class means, caught exception " + ex + ". See console for logging.", "Bad class means file", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void doChooseSamplesFolder() {
        JFileChooser c = new JFileChooser(lastSymbolsPath.toFile());
        c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        c.setDialogTitle("Choose folder to store symbol AVIs");
        c.setApproveButtonText("Select");
        c.setApproveButtonToolTipText("Selects a folder to store AVIs");
        int ret = c.showOpenDialog(chip.getFilterFrame());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }

        lastSymbolsPath = Paths.get(c.getSelectedFile().toString());
        putString("lastSymbolsPath", lastSymbolsPath.toString());

    }

    public void doPing() {
        try {
            sendUDPMessage(CMD_PING);
        } catch (IOException e) {
            log.warning(e.toString());
            showWarningDialogInSwingThread("Exception sending ping: " + e.toString(), "Exception");
        }

    }

    private void closeSymbolFileAndSendMessage() {
        log.info("stopping sample recording, starting training");
        aviWriter.doCloseFile(); // saves tmpfile.avi
        String newname = JOptionPane.showInputDialog(chip.getFilterFrame(), "Class name for this sample (e.g. thumbsup or peacesign)?", lastNewClassName);
        if (newname == null) {
            try {
                // user canceled, delete the file
                Files.delete(aviWriter.getFile().toPath());
            } catch (IOException ex) {
                log.warning("could not delete the AVI file " + aviWriter.getFile() + ": " + ex.toString());
            }
            return;
        }
        newname = newname.trim().replaceAll(" +", "-"); // trim leading and trailing spaces, replace others with -
        putString("lastNewClassName", newname);
        Path source = aviWriter.getFile().toPath(), dest = source.resolveSibling(newname + ".avi");

        if (dest.toFile().exists()) {
            int ret = JOptionPane.showConfirmDialog(chip.getFilterFrame(), String.format("destination %s exists, overwrite?", dest.toFile()));
            if (ret != JOptionPane.OK_OPTION) {
                JOptionPane.showMessageDialog(chip.getFilterFrame(), "Learning cancelled");
                return;
            }
        }
        try {
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            lastNewClassName = newname;
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.WARNING, null, ex);
            showWarningDialogInSwingThread("Exception renaming file: " + ex.toString(), "Exception");
        }
    }

    @Override
    public synchronized void doLoadLabels() {
        super.doLoadLabels(); //To change body of generated methods, choose Tools | Templates.
    }
    

    public void doStartTraining() {
        try {
            sendUDPMessage(CMD_NEW_SAMPLES_AVAILABLE + " " + lastSymbolsPath); // inform only of the destination folder; class name is in filename
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.WARNING, null, ex);
            showWarningDialogInSwingThread("Exception starting training: " + ex.toString(), "Exception");
        }

    }

    public void doCancelTraining() {
        try {
            sendUDPMessage(CMD_CANCEL_TRAINING);
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.WARNING, null, ex);
            showWarningDialogInSwingThread("Exception cancelling training: " + ex.toString(), "Exception");
        }

    }

    private void openSymbolFileAndStartRecording(String prefix) {
        log.info("recording samples");
        aviWriter.openAVIOutputStream(lastSymbolsPath.resolve(prefix + ".avi").toFile(), new String[]{"# " + prefix});
    }

    public void doToggleOnSampleNewClass() {
        openSymbolFileAndStartRecording("tmpfile");
    }

    public void doToggleOffSampleNewClass() {
        closeSymbolFileAndSendMessage();
    }

    public String getHostname() {
        return host;
    }

    /**
     * You need to setHost before this will send events.
     *
     * @param host the host
     */
    synchronized public void setHostname(String host) {
        this.host = host;
//        if ( checkClient() ){
        putString("hostname", host);
//        }else{
//            log.warning("checkClient() returned false, not storing "+host+" in preferences");
//        }
    }

    public int getPortSendTo() {
        return portSendTo;
    }

    /**
     * You set the port to say which port the packet will be sent to.
     *
     * @param portSendTo the UDP port number.
     */
    public void setPortSendTo(int portSendTo) {
        this.portSendTo = portSendTo;
        putInt("portSendTo", portSendTo);
    }

    /**
     * @return the listenOnPort
     */
    public int getPortListenOn() {
        return portListenOn;
    }

    /**
     * @param portListenOn the listenOnPort to set
     */
    public void setPortListenOn(int portListenOn) {
        this.portListenOn = portListenOn;
        putInt("portListenOn", portListenOn);
    }

    // we override the super's Statistics to compute the dot product winner here
    protected class Statistics extends RoShamBoCNN.Statistics implements PropertyChangeListener {

        public Statistics() {
            reset();
        }

        protected void computeOutputSymbol() {
            symbolOutput = symbolDetected;
        }

        @Override
        public synchronized void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName() == AbstractDavisCNN.EVENT_MADE_DECISION) {
                int lastOutput = symbolDetected;
                maxActivation = Float.NEGATIVE_INFINITY;
                // hack to support both conventional and incremental CNNs. If the number of output units is larger than 4, then we are running iCaRL,
                // and we need to compute the normalized output vector and dot it against the stored class mean vectors to determine the winning class.
                // Otherwise, we assume we run old RoshamboCNN and just call the super's method to deal with new decisions.

                AbstractDavisCNN net = (AbstractDavisCNN) evt.getNewValue();
                if (net.getOutputLayer().getActivations().length == 4) {
                    if (lowpassFilteredOutputUnits == null || lowpassFilteredOutputUnits.length != 4) {
                        lowpassFilteredOutputUnits = new float[4];
                    }
                    setSoftMaxOutput(true); // override to fix softmax for base network
                    super.processDecision(evt);
                    return;
                }
                setSoftMaxOutput(false);

                symbolDetected = -1;

                if (classMeans == null) {
                    showWarningDialogInSwingThread("null class means vector - load one with LoadClassMeanVectors button", "No class means");
                    throw new RuntimeException("null class means vector - load one with LoadClassMeanVectors button");
                }

                // get output layer and normalize it to unit vector
                float[] act = net.getOutputLayer().getActivations();
                if (act == null) {
                    showWarningDialogInSwingThread("null activations from CNN", "No CNN output");
                    throw new RuntimeException("null activations from CNN");
                }
                if (classMeans[0].length != act.length) {
                    String s = String.format("Length of class mean vector (%d) does not match number of network outputs (%d)",
                            classMeans.length,
                            act.length);
                    showWarningDialogInSwingThread(
                            s,
                            "No CNN output");
                    throw new RuntimeException(s);
                }
                float sum = 0, sum2 = 0;
                for (float f : act) {
                    sum += f;
                    sum2 += f * f;
                }
                float sqrtsum2 = (float) Math.sqrt(sum2);
                float[] normact = new float[act.length];
                for (int i = 0; i < act.length; i++) {
                    normact[i] = act[i] / sqrtsum2;
                }
                // dot it with each mean vector
                int nclasses = classMeans.length;
                float[] dots = new float[nclasses];
                for (int i = 0; i < nclasses; i++) {
                    for (int j = 0; j < normact.length; j++) {
                        dots[i] += normact[j] * classMeans[i][j];
                    }
                }
                // update the lowpass-filtered final result vector
                if (lowpassFilteredOutputUnits == null || lowpassFilteredOutputUnits.length != nclasses) {
                    lowpassFilteredOutputUnits = new float[nclasses];
                }
                for (int i = 0; i < nclasses; i++) {
                    float output = dots[i];
                    lowpassFilteredOutputUnits[i] = ((1 - decisionLowPassMixingFactor) * lowpassFilteredOutputUnits[i]) + (output * decisionLowPassMixingFactor);

                    if (lowpassFilteredOutputUnits[i] > maxActivation) {
                        maxActivation = lowpassFilteredOutputUnits[i];
                        symbolDetected = i;
                    }
                }
                if (symbolDetected < 0) {
                    log.warning("negative descision, network must not have run correctly");
                    return;
                }
                if (decisionCounts == null || decisionCounts.length != nclasses) {
                    decisionCounts = new int[nclasses];
                }
                decisionCounts[symbolDetected]++;
                totalCount++;
                if ((symbolDetected != lastOutput)) {
                    // CNN output changed, respond here
                    outputChanged = true;
                } else {
                    outputChanged = false;
                }
                computeOutputSymbol();
            } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
                reset();
            }
        }

        protected void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(.8f * chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString(toString());
        }

    }

    private void parseMessage(String msg) {
        log.info("parsing message \"" + msg + "\"");
        if (msg == null) {
            log.warning("got null message");
            return;
        }
        StringTokenizer tokenizer = new StringTokenizer(msg);
        String cmd = tokenizer.nextToken();
        switch (cmd) {
            case CMD_PONG:
                showPlainMessageDialogInSwingThread(String.format("\"%s\" received from %s", cmd, host), "Pong");
                return;
            case CMD_PING: {
                try {
                    sendUDPMessage(CMD_PONG);
                } catch (IOException ex) {
                    Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.SEVERE, null, ex);
                    showWarningDialogInSwingThread("Exception sending return pong in response to ping: " + ex.toString(), "Pong Exception");
                }
            }
            return;
            case CMD_NEW_SAMPLES_AVAILABLE:
                log.warning("learning server should not send this message; it is for us to send");
                return;
            case CMD_LOAD_CLASS_MEANS: {
                try {
                    if (!tokenizer.hasMoreTokens()) {
                        log.warning("Missing class means filename; usage is " + CMD_LOAD_CLASS_MEANS + " filename.txt");
                        return;
                    }
                    loadClassMeans(new File(tokenizer.nextToken()));
                } catch (IOException ex) {
                    Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.SEVERE, null, ex);
                    showWarningDialogInSwingThread(ex.toString(), "Error loading class means");
                }
            }
            break;
            case CMD_LOAD_NETWORK:
                if (!tokenizer.hasMoreTokens()) {
                    log.warning("Missing network filename; usage is " + CMD_LOAD_NETWORK + " filename.pb [labels.txt]");
                    return;
                }
                String networkFilename = tokenizer.nextToken();
                if (networkFilename == null || networkFilename.isEmpty()) {
                    log.warning("null filename supplied for new network");
                    return;
                }
                synchronized (RoShamBoIncremental.this) { // sync on outter class, not thread we are running in
                    try {
                        log.info("loading new CNN from " + networkFilename);
                        loadNetwork(new File(networkFilename));
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Exception loading new network", e);
                        showWarningDialogInSwingThread("Exception loading new network: " + e.toString(), "Could not load network");

                        break;
                    }
                }
                // labels  for classes; should be one per output unit separated by whitespace, e.g. "loadnetwork xxx.pb rock scissors paper background thumbsup peacesign" for a network trained for 6 classes
                if (!tokenizer.hasMoreTokens()) {
                    log.warning("no class labels supplied for this network");
                    showWarningDialogInSwingThread("no class labels supplied for this network", "No class labels");
                    break;
                }
                List<String> labels = new ArrayList();
                StringBuilder sb = new StringBuilder("new labels:");
                while (tokenizer.hasMoreTokens()) {
                    String nextLabel = tokenizer.nextToken();
                    sb.append(" " + nextLabel);
                    labels.add(nextLabel);
                }
                synchronized (RoShamBoIncremental.this) { // sync on outter class, not thread we are running in
                    try {
                        apsDvsNet.setLabels(labels);
                        log.info(sb.toString());
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Exception setting new labels", e);
                        showWarningDialogInSwingThread("Warning: exception setting class labels: " + e.toString(), "Bad class labels");
                    }
                }

                break;
            case CMD_PROGRESS:
                try {
                    int progress = Integer.parseInt(tokenizer.nextToken());
                    if (progressMonitor == null || progressMonitor.isCanceled()) {
                        progressMonitor = new ProgressMonitor(chip.getFilterFrame(), "Training", "note", 0, 100);
                    }
                    progressMonitor.setProgress(progress);
                    progressMonitor.setNote(String.format("%d%%", progress));
                    if (progress >= 100) {
                        synchronized (RoShamBoIncremental.this) {// sync on outter class, not thread we are running in
                            // we progressMonitor this in annotate (running in EDT thread) to see we should send cancel message
                            progressMonitor = null;
                        }
                    }
                } catch (Exception e) {
                    log.warning("exception updating progress monitor: " + e.toString());
                }

                break;
            default:
                final String badmsg = "unknown token or comamnd in message \"" + msg + "\"";
                log.warning(badmsg);
                showWarningDialogInSwingThread(badmsg, "Unknown message");
        }
    }

    /**
     * loads class means from a file
     *
     * @param file the file
     * @throws RuntimeException
     * @throws IOException
     */
    private void loadClassMeans(File file) throws RuntimeException, IOException {

        if (file == null) {
            throw new RuntimeException("null file supplied for class means filename");
        }
        List<String> lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
        if (lines.isEmpty()) {
            throw new RuntimeException("empty file " + file);
        }
        synchronized (RoShamBoIncremental.this) { // sync on outter class, not thread we are running in
            classMeans = new float[lines.size()][];
            int classNumber = 0;
            for (String line : lines) {
                Scanner scanner = new Scanner(line);
                ArrayList<Float> vals = new ArrayList(256);
                while (scanner.hasNext()) {
                    String word = scanner.next();
                    vals.add(Float.parseFloat(word));
                }
                if (vals.isEmpty()) {
                    throw new RuntimeException("line of mean vector values is empty or does not contain a space-separated list of numeric values: " + line);
                }
                classMeans[classNumber] = new float[vals.size()];
                int i = 0;
                for (Float f : vals) {
                    classMeans[classNumber][i++] = (f != null ? f : Float.NaN); // Or whatever default you want.
                }
                classNumber = classNumber + 1;
            }
            // check all the same length and >0
            int firstVectorLength = classMeans[0].length;
            for (int i = 1; i < classMeans.length; i++) {
                if (classMeans[i].length != firstVectorLength) {
                    throw new RuntimeException(String.format("line %d of file had different number of components (%d vs %d for first line",
                            i, classMeans[i].length, firstVectorLength));
                }
            }
            log.info("loaded " + classMeans.length + " new class mean vectors from " + file);
        }
    }

    synchronized private void sendUDPMessage(String string) throws IOException { // sync for thread safety on multiple senders

        if (portListenerThread == null || !portListenerThread.isAlive() || listenOnSocket == null) { // start a thread to get messages from client
            log.info("starting thread to listen for UDP datagram messages on port " + portListenOn);
            portListenerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        listenOnSocket = new DatagramSocket(portListenOn);
                    } catch (SocketException ex) {
                        log.warning("could not open local port for listening for learning server messages on port " + portListenOn + ": " + ex.toString());
                        return;
                    }
                    while (true) {
                        try {
                            byte[] buf = new byte[1024];
                            DatagramPacket datagram = new DatagramPacket(buf, buf.length);
                            listenOnSocket.receive(datagram);
                            String msg = new String(datagram.getData(), datagram.getOffset(), datagram.getLength());
                            log.info("got message:" + msg);
                            parseMessage(msg);
                        } catch (IOException ex) {
                            log.warning("stopping thread; exception in recieving message: " + ex.toString());
                            break;
                        }
                    }
                }

            }, "RoShamBoIncremental Listener");
            portListenerThread.start();
        }
        log.info(String.format("sending message to host=%s port=%s string=\"%s\"", host, portSendTo, string));
        if (sendToSocket == null) {
            try {
                log.info("opening socket to send datagrams from");
                client = new InetSocketAddress(host, portSendTo); // get address for remote client
                sendToSocket = new DatagramSocket(); // make a local socket using any port, will be used to send datagrams to the host/sendToPort
            } catch (IOException ex) {
                log.warning(String.format("cannot open socket to send to host=%s port=%d, got exception %s", host, portSendTo, ex.toString()));
                return;
            }
        }

        try {
            byte[] buf = string.getBytes();
            DatagramPacket datagram = new DatagramPacket(buf, buf.length, client.getAddress(), portSendTo); // construct datagram to send to host/sendToPort
            sendToSocket.send(datagram);
        } catch (IOException ex) {
            log.warning("cannot send message " + ex.toString());
            return;
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (apsDvsNet != null && (isShowTop1Label()) && apsDvsNet.getLabels() != null
                && apsDvsNet.getLabels().size() > 0) {
            if (isShowTop1Label()) {
                drawDecisionOutput(drawable, apsDvsNet);
            }
        }
        synchronized (this) {
            if (progressMonitor != null && progressMonitor.isCanceled()) {
                try {
                    sendUDPMessage(CMD_CANCEL_TRAINING);
                } catch (IOException ex) {
                    Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.SEVERE, null, ex);
                    showWarningDialogInSwingThread(ex.toString(), "Exception");
                }

            }
        }
    }

    protected void drawDecisionOutput(GLAutoDrawable drawable, AbstractDavisCNN network) {
        if (network == null || network.getOutputLayer() == null) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        int width = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();
        int top1 = statistics.symbolDetected;
        if (top1 < 0 || top1 >= apsDvsNet.getLabels().size()) {
            return;
        }
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36), true, false);
        }
        // draw list of all labels with "probabilities" from low passed output of network
        if (apsDvsNet.getLabels() == null) {
            log.warning("cannot annotate class labels; labels are null");
            return;
        }
        if (statistics.lowpassFilteredOutputUnits == null) {
            log.warning("cannot annotate class labels; statistics.lowpassFilteredOutputUnits are null");
            return;
        }
        if (statistics.lowpassFilteredOutputUnits.length != apsDvsNet.getLabels().size()) {
            log.warning("cannot annotate class labels; number of ouput values (" + statistics.lowpassFilteredOutputUnits.length + ") is different"
                    + " than number of labels (" + apsDvsNet.getLabels().size() + ")");
            return;
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .45f);
        MultilineAnnotationTextRenderer.setColor(Color.yellow);
        MultilineAnnotationTextRenderer.setScale(0.5f);
        StringBuilder sb = new StringBuilder("Class outputs:\n");
        for (int i = 0; i < statistics.lowpassFilteredOutputUnits.length; i++) {
//            sb.append(String.format("%-15s %.2f%%\n",apsDvsNet.getLabels().get(i),statistics.lowpassFilteredOutputUnits[i]*100));
            sb.append(String.format("%s\n", apsDvsNet.getLabels().get(i)));
        }
        MultilineAnnotationTextRenderer.renderMultilineString(sb.toString());
        // draw top 1 on top of everything
//        float top1probability = statistics.maxActivation; // brightness scale
        textRenderer.setColor(1, 1, 1, 1);
        textRenderer.beginRendering(width, height);
        String s = apsDvsNet.getLabels().get(top1);
//        String s = String.format("%s (%%%.1f)", label, top1probability * 100);
        Rectangle2D r = textRenderer.getBounds(s);
        textRenderer.draw(s, (width / 2) - ((int) r.getWidth() / 2), height / 2);
        textRenderer.endRendering();
    }

}
