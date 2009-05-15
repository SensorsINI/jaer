/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
//import java.util.logging.Level;
//import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLJPanel;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Allows control of remote CUDA process for filtering jAER data.
 *<p>
 * This filter does not process events at all. Rather it opens a TCP stream socket connection to a remote (maybe on the same machine)
 * process which is a CUDA GPGPU program which accepts AEs from jAER and which sends its output back to jAER for visualization.
 * <p>
 * The stream socket works with text command lines as though it were a terminal.  
 *
 * 
 * @author tobi/yingxue/jay
 */
public class CUDAObjectTrackerControl extends EventFilter2D implements FrameAnnotater {

    public final int CONTROL_PORT_DEFAULT = 9998;
    AEViewer outputViewer = null;
    private int controlPort = getPrefs().getInt("CUDAObjectTrackerControl.controlPort", CONTROL_PORT_DEFAULT);
    private int recvOnPort = getPrefs().getInt("CUDAObjectTrackerControl.inputPort", 10012);
    private int sendToPort = getPrefs().getInt("CUDAObjectTrackerControl.outputPort", 10000);
    private String hostname = getPrefs().get("CUDAObjectTrackerControl.hostname", "localhost");
    private String cudaExecutablePath = getPrefs().get("CUDAObjectTrackerControl.cudaExecutablePath", null);
    private String cudaEnvironmentPath = getPrefs().get("CUDAObjectTrackerControl.cudaEnvironmentPath", null);
    private DatagramSocket controlSocket = null;
    InetAddress cudaInetAddress = null;
    private boolean cudaEnabled = getPrefs().getBoolean("CUDAObjectTrackerControl.cudaEnabled", true);
    Process cudaProcess = null;
    ProcessBuilder cudaProcessBuilder = null;
    AEUnicastOutput unicastOutput;
    AEUnicastInput unicastInput;
    CUDAChip cudaChip = null;
    CUDAChip.CUDAExtractor cudaExtractor = null;

    /*
     *
    // from config.h in CUDA code
    #define MEMBRANE_TAU			10000.0F	// membrane time constant
    #define MEMBRANE_THRESHOLD		100.0F		// membrane threshold
    #define MEMBRANE_POTENTIAL_MIN	-50.0F		// membrane equilibrium potential

    #define MIN_FIRING_TIME_DIFF	15000		// low pass filter the events from retina

    #define E_I_NEURON_POTENTIAL	10.0		// excitatory to inhibitory synaptic weight
    #define I_E_NEURON_POTENTIAL	10.0		// inhibitory to excitatory synaptic weight

     */
    // TODO these cuda parameters better handled by indexed properties, but indexed properties not handled in FilterPanel yet
    final String CMD_THRESHOLD = "threshold";
    private float threshold = getPrefs().getFloat("CUDAObjectTrackerControl.threshold", 100);
    final String CMD_MEMBRANE_TAU = "membraneTau";
    private float membraneTauUs = getPrefs().getFloat("CUDAObjectTrackerControl.membraneTauUs", 10000);
    final String CMD_MEMBRANE_POTENTIAL_MIN = "membranePotentialMin";
    private float membranePotentialMin = getPrefs().getFloat("CUDAObjectTrackerControl.membranePotentialMin", -50);
    final String CMD_MIN_FIRING_TIME_DIFF = "minFiringTimeDiff";
    private float minFiringTimeDiff = getPrefs().getFloat("CUDAObjectTrackerControl.minFiringTimeDiff", 15000);
    final String CMD_E_I_NEURON_POTENTIAL = "eISynWeight";
    private float eISynWeight = getPrefs().getFloat("CUDAObjectTrackerControl.eISynWeight", 10);
    final String CMD_I_E_NEURON_POTENTIAL = "iESynWeight";
    private float iESynWeight = getPrefs().getFloat("CUDAObjectTrackerControl.iESynWeight", 10);
    final String CMD_EXIT = "exit";
    private final String CMD_DEBUG_LEVEL = "debugLevel";
    private int debugLevel = getPrefs().getInt("CUDAObjectTrackerControl.debugLevel", 1);
    private final String CMD_MAX_XMIT_INTERVAL_MS = "maxXmitIntervalMs";
    private int maxXmitIntervalMs = getPrefs().getInt("CUDAObjectTrackerControl.maxXmitIntervalMs", 20);
    final String CMD_CUDA_ENABLED = "cudaEnabled";
    final String CMD_DELTA_TIME_US = "deltaTimeUs";
    private int deltaTimeUs = getPrefs().getInt("CUDAObjectTrackerControl.deltaTimeUs", 1000);
    private final String CMD_NUM_OBJECTS = "numObject";
    private int numObject = getPrefs().getInt("CUDAObjectTrackerControl.numObject", 5);
//     final String CMD_TERMINATE_IMMEDIATELY="terminate";
    final String CMD_KERNEL_SHAPE = "kernelShape";
    final String CMD_TEMPLATE = "template";  // token for sending an entire template

//     final String CMD_SPIKE_PARTITIONING_METHOD="spikePartitioningMethod";
    private String CMD_CUDAS_RECVON_PORT = "inputPort"; // swapped here because these are CUDAs
    private String CMD_CUDAS_SENDTO_PORT = "outputPort";
//    private boolean loopbackTestEnabled=getPrefs().getBoolean("CUDAObjectTrackerControl.loopbackTestEnabled", false);
    final String CMD_LOOPBACK_TEST_ENABLED = "loopbackEnabled";
    static final int TEMPLATE_SIZE = 16; // hardcoded template size for now TODO

    /*
    float bandwidth = GABOR_BAND_WIDTH; //bandwidth of the gabor function
    float f_gabor_theta[] = {0,45,90,135};  // orientation of the gabor function
    float lambda = GABOR_WAVELENGTH;	//wavelength of the gabor function
    float f_gabor_psi = GABOR_PHASE_OFFSET;	//phase offset of the gabor function
    float gamma = GABOR_ASPECT_RATIO;	// aspect ratio of the gabor function
    float maxAmp = GABOR_XY_MAX;	// the maximum value of x and y value
    float maxAmp = GABOR_MAX_AMP; // the maximum amplitude of the gabor function
    // used by gabor function
    #define GABOR_MAX_AMP				2.0F			// maximum amplitude of gabor function
    #define GABOR_BAND_WIDTH			1.5F			// bandwidth of the gabor function
    #define GABOR_WAVELENGTH			2.0F			// wavelength of the gabor function
    #define GABOR_PHASE_OFFSET			0.0F			// phase offset of the gabor function(-90 to 90 degree)
    #define GABOR_ASPECT_RATIO			0.5F			// aspect ratio of the gabor function
    #define GABOR_XY_MAX				5.0F			// the maximum value of x and y value
     */
    final String CMD_GABOR_MAX_AMP = "gaborMaxAmp";
    final String CMD_GABOR_BAND_WIDTH = "gaborBandwidth";
    final String CMD_GABOR_WAVELENGTH = "gaborWavelength";
//    final String CMD_GABOR_PHASE_OFFSET = "gaborPhase";
    final String CMD_GABOR_ASPECT_RATIO = "gaborGamma";
    private float gaborBandwidth = getPrefs().getFloat("CUDAObjectTrackerControl.gaborBandwidth", 1.5f);
    private float gaborLambda = getPrefs().getFloat("CUDAObjectTrackerControl.gaborLambda", 2f);
    private float gaborGamma = getPrefs().getFloat("CUDAObjectTrackerControl.gaborGamma", .5f);
    private float gaborMaxAmp = getPrefs().getFloat("CUDAObjectTrackerControl.gaborMaxAmp", 2f);
//    private float gaborPhase = getPrefs().getFloat("CUDAObjectTrackerControl.gaborPhase", 0);
    private String gaborTip = null;
    private ArrayList<CUDATemplate> templates = new ArrayList<CUDATemplate>();


    {
        final String gaborTipResource = "/org/ine/telluride/jaer/cuda/gaborTip.html";
        InputStream is = getClass().getResourceAsStream(gaborTipResource);
        if (is != null) {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                char[] chars = new char[1000];
                isr.read(chars, 0, chars.length);
                gaborTip = String.copyValueOf(chars);
            } catch (IOException ex) {
                log.warning("reading gabor tooltip from " + gaborTipResource + ", caught " + ex);
            }
        }
        // TODO fill in tips here
        setPropertyTooltip("gaborLambda", gaborTip);
        setPropertyTooltip("gaborBandwidth", gaborTip);
        setPropertyTooltip("gaborGamma", gaborTip);
        setPropertyTooltip("gaborMaxAmp", gaborTip);
        setPropertyTooltip("gaborPhase", gaborTip);
    }

    public enum KernelShape {

        DoG, Circle, Gabor
    };
//    public enum SpikePartitioningMethod {
//        SingleSpike, MultipleSpike
//    };
    private KernelShape kernelShape = KernelShape.valueOf(getPrefs().get("CUDAObjectTrackerControl.kernelShape", KernelShape.DoG.toString()));
//    private SpikePartitioningMethod spikePartitioningMethod=SpikePartitioningMethod.valueOf(getPrefs().get("CUDAObjectTrackerControl.spikePartitioningMethod", SpikePartitioningMethod.MultipleSpike.toString()));

    public CUDAObjectTrackerControl(AEChip chip) {
        super(chip);
        setPropertyTooltip("hostname", "hostname or IP address of CUDA process");
        setPropertyTooltip("controlPort", "port number of CUDA process UDP control port (we control CUDA over this)");
        setPropertyTooltip("inputPort", "UDP port number we receive events on from CUDA (CUDA's outputPort)");
        setPropertyTooltip("outputPort", "UDP port number we send events to (CUDA's inputPort)");
        setPropertyTooltip("cudaEnvironmentPath", "Windows PATH to include CUDA stuff (cutil32.dll), e.g. c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug");
        setPropertyTooltip("cudaExecutablePath", "Full path to CUDA process executable");
        setPropertyTooltip("threshold", "neuron spike thresholds");
        setPropertyTooltip("membraneTauUs", "neuron membrane decay time constant in us");
        setPropertyTooltip("membranePotentialMin", "neuron reset potential");
        setPropertyTooltip("minFiringTimeDiff", "refractory period in us for spikes from jear to cuda - spike intervals shorter to this from a cell are not processed");
        setPropertyTooltip("eISynWeight", "excitatory template array neuron weight to WTA neuron neuron - increase to sharpen selectivity");
        setPropertyTooltip("iESynWeight", "inhibitory weight of WTA neuron on LIF template array neurons - increase to reduce activity");
        setPropertyTooltip("cudaEnabled", "true to enable use of CUDA hardware - false to run on host");
        setPropertyTooltip("KillCUDA", "kills CUDA process, iff started from jaer");
        setPropertyTooltip("SelectCUDAExecutable", "select the CUDA executable (.exe) file");
        setPropertyTooltip("LaunchCUDA", "Launches the selected CUDA executable");
        setPropertyTooltip("ShowTemplates", "Shows convolution templates");
        setPropertyTooltip("debugLevel", "0=minimal debug, 1=debug");
        setPropertyTooltip("maxXmitIntervalMs", "maximum interval in ms between sending packets from CUDA (if there are spikes to send)");
        setPropertyTooltip("SendParameters", "Send all the parameters to a CUDA process we have not started from here");
        setPropertyTooltip("deltaTimeUs", "Time in us that spikes are chunked together by CUDA in common-time packets");
        setPropertyTooltip("numObject", "number of computed templates from fixed list of sizes, starting from first");
        setPropertyTooltip("kernelShape", "<html>Shape of template. <ul><li>Gaussian is single Gaussion blob with uniform inhibitory surround. <li>DoG is circular difference of Gaussians.<li>Gabor is set of oriented Gabor filters.</ul></html>");
        setPropertyTooltip("loopbackTestEnabled", "test communication by sending spikes to cuda which should send them back");
        if (controlPort != CONTROL_PORT_DEFAULT) {
            log.warning("controlPort=" + controlPort + ", which is not default value (" + CONTROL_PORT_DEFAULT + ") on which CUDA expects commands");
        }
        if (recvOnPort == controlPort || sendToPort == controlPort) {
            log.warning("either inputPort=" + recvOnPort + " or outputPort=" + sendToPort + " is the same as controlPort=" + controlPort + ", change them or events may be confused with commands");
        }
        if (cudaEnvironmentPath == null || cudaEnvironmentPath.isEmpty()) {
//             String cudaBinPath=System.getenv("CUDA_BIN_PATH");
//            String cudaLibPath=System.getenv("CUDA_LIB_PATH");
//            cudaEnvironmentPath = cudaBinPath+File.pathSeparator+cudaLibPath; // "c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug";
            cudaEnvironmentPath = "c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug";
        }
        try {
            cudaInetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException ex) {
            log.warning("CUDA hostname " + hostname + " unknown? " + ex.toString());
        }
        setEnclosedFilterChain(new FilterChain(chip));
        RefractoryFilter rf = new RefractoryFilter(chip);
        rf.setEnclosed(true, this);
        getEnclosedFilterChain().add(rf); // to filter out redundant events - multiple spikes from same cell with short ISI.

        makeGaborTemplates();

    }

    public void doKillCUDA() {
        writeCommandToCuda(CMD_EXIT);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
        }
        ; // let cuda print results
        if (cudaProcess != null) {
            cudaProcess.destroy(); // kill it anyhow if we started it
        }
    }

    /** Launches the CUDA process, then sends parameters to it. When the CUDA process is
     * launched externally, we don't know if it's running and it will not have commands from us initially.
     */
    public void doLaunchCUDA() {

        if (isCudaRunning()) {
            int ret = JOptionPane.showConfirmDialog(chip.getAeViewer(), "Kill existing CUDA process and start a new one?");
            if (ret != JOptionPane.OK_OPTION) {
                return;
            }
            cudaProcess.destroy();
        }
        cudaProcessBuilder = new ProcessBuilder();
        cudaProcessBuilder.command(cudaExecutablePath);
        cudaProcessBuilder.environment().put("Path", cudaEnvironmentPath);
        cudaProcessBuilder.directory(new File(System.getProperty("user.dir")));
        cudaProcessBuilder.redirectErrorStream(true);

        try {
            log.info("launching CUDA executable \"" + cudaProcessBuilder.command() + "\" with environment=\"" + cudaProcessBuilder.environment() + "\"+ in directory=" + cudaProcessBuilder.directory());
            cudaProcess = cudaProcessBuilder.start();
            Runtime.getRuntime().addShutdownHook(new Thread("CUDA detroyer") {

                @Override
                public void run() {
                    log.info("destroying CUDA process");
                    cudaProcess.destroy();
                }
            });
            final BufferedReader outReader = new BufferedReader(new InputStreamReader(cudaProcess.getInputStream()));
            Thread outThread = new Thread("CUDA output") {

                public void run() {
                    try {
                        Thread.sleep(100);
                        try {
                            String line;
                            do {
                                line = outReader.readLine();
                                log.info("CUDA: " + line);
                            } while (line != null);
                        } catch (IOException ex) {
                            log.warning(ex.toString());
                        }
                    } catch (InterruptedException ex) {
                        log.warning(ex.toString());
                    }
                }
            };
            outThread.start();
            Thread.sleep(300);
            sendParameters(); // set defaults to override #defines in CUDA code
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }

    private boolean checkControlSocket() {
        if (controlSocket == null) {
            try {
                controlSocket = new DatagramSocket(); // bind to any available port because we will send to CUDA on its port
//                writer = new OutputStreamWriter(controlSocket.getOutputStream());
                log.info("bound to local port " + controlSocket.getLocalPort() + " for controlling CUDA");
                sendParameters(); // send on construction in case CUDA is running
            } catch (Exception ex) {
                log.warning(ex.toString() + " to " + hostname + ":" + controlPort);
                return false;
//                return launchCuda();
            }
        }
        return true;
    }

    synchronized private void checkIOPorts() throws IOException {
        checkControlSocket();
        if (unicastInput == null) {
            unicastInput = new AEUnicastInput();
            unicastInput.setPort(recvOnPort);
            unicastInput.set4ByteAddrTimestampEnabled(true);
            unicastInput.setAddressFirstEnabled(true);
            unicastInput.setSequenceNumberEnabled(false); // TODO, should use seq numbers on both sides
            unicastInput.setSwapBytesEnabled(false);
            unicastInput.setTimestampMultiplier(1);
            unicastInput.start();
        }
        if (unicastOutput == null) {
            unicastOutput = new AEUnicastOutput();
            unicastOutput.setHost(getHostname());
            unicastOutput.setPort(sendToPort);
            unicastOutput.set4ByteAddrTimestampEnabled(true);
            unicastOutput.setAddressFirstEnabled(true);
            unicastOutput.setSequenceNumberEnabled(true); // TODO sequence numbers
            unicastOutput.setSwapBytesEnabled(false);
            unicastOutput.setTimestampMultiplier(1);
        }
    }

//    private void checkOutputViewer() throws HeadlessException {
//        if (outputViewer == null) {
//            outputViewer = new AEViewer(chip.getAeViewer().getJaerViewer());
//            Class originalChipClass = outputViewer.getAeChipClass();
//            outputViewer.setAeChipClass(CUDAOutputAEChip.class);
//            outputViewer.setVisible(true);
//            outputViewer.setPreferredAEChipClass(originalChipClass);
//            outputViewer.reopenSocketInputStream();
//        }
//    }
    // thread safe for renewing sockets
    synchronized private void sendParameters() {
        log.info("sending parameters to CUDA");
        sendParameter(CMD_THRESHOLD, threshold);
        sendParameter(CMD_I_E_NEURON_POTENTIAL, iESynWeight);
        sendParameter(CMD_E_I_NEURON_POTENTIAL, eISynWeight);
        sendParameter(CMD_MEMBRANE_POTENTIAL_MIN, membranePotentialMin);
        sendParameter(CMD_MEMBRANE_TAU, membraneTauUs);
        sendParameter(CMD_MIN_FIRING_TIME_DIFF, minFiringTimeDiff);
        sendParameter(CMD_DELTA_TIME_US, deltaTimeUs);
        writeCommandToCuda(CMD_DEBUG_LEVEL + " " + debugLevel);
        writeCommandToCuda(CMD_CUDA_ENABLED + " " + cudaEnabled);
        writeCommandToCuda(CMD_KERNEL_SHAPE + " " + kernelShape.toString());
//        writeCommandToCuda(CMD_SPIKE_PARTITIONING_METHOD+" "+spikePartitioningMethod.toString());
        writeCommandToCuda(CMD_MAX_XMIT_INTERVAL_MS + " " + maxXmitIntervalMs);
        writeCommandToCuda(CMD_CUDAS_RECVON_PORT + " " + sendToPort); // tells CUDA "inputPort XXX" which it uses to set which port it sends to
        writeCommandToCuda(CMD_CUDAS_SENDTO_PORT + " " + recvOnPort);
        writeCommandToCuda(CMD_NUM_OBJECTS + " " + numObject);
//        writeCommandToCuda(CMD_LOOPBACK_TEST_ENABLED+" "+loopbackTestEnabled);

        sendGabors();

    }

    private void sendParameter(String name, float value) {
        String s = String.format(name + " " + value);
        writeCommandToCuda(s);
    }

    private void sendParameter(String name, int value) {
        String s = String.format(name + " " + value);
        writeCommandToCuda(s);
    }

    private boolean isCudaRunning() {
        if (cudaProcess == null) {
            return false;
        }
        try {
            int exitValue = 0;
            if ((exitValue = cudaProcess.exitValue()) != 0) {
                log.warning("CUDA exit process was " + exitValue);
                cudaProcess = null;
            }
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

//    /** does reconnect to CUDA server */
//    public void doReconnect() {
//        if (controlSocket != null) {
//                controlSocket.close();
//            controlSocket = null;
//        }
//        checkControlSocket();
//    }
    public void doSelectCUDAExecutable() {
        if (cudaExecutablePath == null || cudaExecutablePath.isEmpty()) {
            cudaExecutablePath = System.getProperty("user.dir");
        }
        JFileChooser chooser = new JFileChooser(cudaExecutablePath);
        chooser.setDialogTitle("Choose CUDA executable .exe file (from CUDA project win32 subfolder)");
        chooser.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".exe");
            }

            @Override
            public String getDescription() {
                return "Executables";
            }
        });
        chooser.setMultiSelectionEnabled(false);
        int retval = chooser.showOpenDialog(getChip().getAeViewer().getFilterFrame());
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f != null && f.isFile()) {
                setCudaExecutablePath(f.toString());
                log.info("selected CUDA executable " + cudaExecutablePath);
            }
        }
    }

    private void writeCommandToCuda(String s) {
        if (!checkControlSocket()) {
            return;
        }
        byte[] b = s.getBytes();
        DatagramPacket packet = new DatagramPacket(b, b.length, cudaInetAddress, controlPort);
        try {
            controlSocket.send(packet);
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
//        try {
//            writer.write(s);
//        } catch (IOException ex) {
//            log.warning("writing string " + s + " got " + ex);
//        }
    }

    public void doSendParameters() {
        sendParameters();
    }
//    private int warningCount=0;

    /** Reconstructs a raw event packet, sends it to jaercuda, reads the output from jaercuda, extracts this raw packet, and then
    returns the events.
    @param in the input packets.
    @return the output packet.
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        // comment out for now during development - tobi
//        if (!isCudaRunning()) {
//            if (warningCount++ % 300 == 0) {
//                log.warning("cuda has not been started from jaer or has terminated");
//            }
////            return in;
//        }
        try {
            checkIOPorts();
            AEPacketRaw rawOutputPacket = chip.getEventExtractor().reconstructRawPacket(in); // reconstruct raw AE data format to send to CUDA
            unicastOutput.writePacket(rawOutputPacket); // write the packet to CUDA
            AEPacketRaw rawInputPacket = unicastInput.readPacket(); // read back whatever CUDA has since sent us, maybe not from this input packet
            if (debugLevel > 1) {
                if (rawInputPacket != null) {
                    log.info("received " + rawInputPacket.getNumEvents() + " events from CUDA");
                } else {
                    log.info("no events back");
                }
            }
            // right here is where we can use a custom extractor to output any kind of events we like from the MSB bits of the returned addresses
            out = cudaChip.getEventExtractor().extractPacket(rawInputPacket); // this extractor interprests the raw addresses properly, and counts inhibitory neuron firiing
            return out;
        } catch (IOException e) {
            log.warning(e.toString());
        }

        return in;
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            if (cudaChip == null) {
                cudaChip = new CUDAChip();
                cudaExtractor = (CUDAChip.CUDAExtractor) cudaChip.getEventExtractor();
            } // used for filter output when filter is enabled.
            // we don't set the chip's event class because the returned event packet tells the rendering methods
            // that it has CudaEvents and this should be enough to suggest rendering
//            chip.setEventClass(CUDAEvent.class); // TODO if chip changes class and these events are cast to PolarityEvent may throw exception
            sendParameters();
        } else {
            if (unicastInput != null) {
                unicastInput.close();
                unicastInput = null;
            }
            if (unicastOutput != null) {
                unicastOutput.close();
                unicastOutput = null;
            }
        }
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public int getControlPort() {
        return controlPort;
    }

    public void setControlPort(int port) {
        support.firePropertyChange("controlPort", controlPort, port);
        this.controlPort = port;
        getPrefs().putInt("CUDAObjectTrackerControl.controlPort", controlPort);
    }

    /**
     * @return the inputPort
     */
    public int getInputPort() {
        return recvOnPort;
    }

    /**
     * @param inputPort the inputPort to set
     */
    public void setInputPort(int inputPort) {
        if (inputPort == controlPort || inputPort == sendToPort) {
            log.warning("tried to set inputPort to " + inputPort + " which is the same as controlPort or sendToPort, not changing it");
            return;
        }
        support.firePropertyChange("inputPort", this.recvOnPort, inputPort);
        this.recvOnPort = inputPort;
        getPrefs().putInt("CUDAObjectTrackerControl.inputPort", inputPort);
        if (unicastInput != null) {
            unicastInput.setPort(inputPort);
        }
        writeCommandToCuda(CMD_CUDAS_SENDTO_PORT + " " + inputPort);
    }

    /**
     * @return the outputPort
     */
    public int getOutputPort() {
        return sendToPort;
    }

    /**
     * @param outputPort the outputPort to set
     */
    public void setOutputPort(int outputPort) {
        if (outputPort == controlPort || outputPort == sendToPort) {
            log.warning("tried to set outputPort same as controlPort or sendToPort, not changing it");
            return;
        }
        support.firePropertyChange("outputPort", this.sendToPort, outputPort);
        this.sendToPort = outputPort;
        getPrefs().putInt("CUDAObjectTrackerControl.outputPort", outputPort);
        if (unicastOutput != null) {
            unicastOutput.setPort(outputPort);
        }
        writeCommandToCuda(CMD_CUDAS_RECVON_PORT + " " + outputPort);
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        try {
            cudaInetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException ex) {
            log.warning("CUDA hostname " + hostname + " unknown? " + ex.toString());
            support.firePropertyChange("hostname", null, this.hostname);
            return;
        }

        support.firePropertyChange("hostname", this.hostname, hostname);
        this.hostname = hostname;
        getPrefs().put("CUDAObjectTrackerControl.hostname", hostname);
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        support.firePropertyChange("threshold", this.threshold, threshold);
        this.threshold = threshold;
        getPrefs().putFloat("CUDAObjectTrackerControl.threshold", threshold);
        sendParameter(CMD_THRESHOLD, threshold);
    }

    /**
     * @return the cudaExecutablePath
     */
    public String getCudaExecutablePath() {
        return cudaExecutablePath;
    }

    /**
     * @param cudaExecutablePath the cudaExecutablePath to set
     */
    public void setCudaExecutablePath(String cudaExecutablePath) {
        support.firePropertyChange("cudaExecutablePath", this.cudaExecutablePath, cudaExecutablePath);
        this.cudaExecutablePath = cudaExecutablePath;
        getPrefs().put("CUDAObjectTrackerControl.cudaExecutablePath", cudaExecutablePath);
    }

    /**
     * @return the cudaEnvironmentPath
     */
    public String getCudaEnvironmentPath() {
        return cudaEnvironmentPath;
    }

    /**
     * @param cudaEnvironmentPath the cudaEnvironmentPath to set
     */
    public void setCudaEnvironmentPath(String cudaEnvironmentPath) {
        support.firePropertyChange("cudaEnvironmentPath", this.cudaEnvironmentPath, cudaEnvironmentPath);
        this.cudaEnvironmentPath = cudaEnvironmentPath;
        getPrefs().put("CUDAObjectTrackerControl.cudaEnvironmentPath", cudaEnvironmentPath);
    }

    /**
     * @return the membraneTauUs
     */
    public float getMembraneTauUs() {
        return membraneTauUs;
    }

    /**
     * @param membraneTauUs the membraneTauUs to set
     */
    public void setMembraneTauUs(float membraneTauUs) {
        support.firePropertyChange("membraneTauUs", this.membraneTauUs, membraneTauUs);
        this.membraneTauUs = membraneTauUs;
        getPrefs().putFloat("CUDAObjectTrackerControl.membraneTauUs", membraneTauUs);
        sendParameter(CMD_MEMBRANE_TAU, membraneTauUs);
    }

    /**
     * @return the membranePotentialMin
     */
    public float getMembranePotentialMin() {
        return membranePotentialMin;
    }

    /**
     * @param membranePotentialMin the membranePotentialMin to set
     */
    public void setMembranePotentialMin(float membranePotentialMin) {
        support.firePropertyChange("membranePotentialMin", this.membranePotentialMin, membranePotentialMin);
        this.membranePotentialMin = membranePotentialMin;
        getPrefs().putFloat("CUDAObjectTrackerControl.membranePotentialMin", membranePotentialMin);
        sendParameter(CMD_MEMBRANE_POTENTIAL_MIN, membranePotentialMin);
    }

    /**
     * @return the minFiringTimeDiff
     */
    public float getMinFiringTimeDiff() {
        return minFiringTimeDiff;
    }

    /**
     * @param minFiringTimeDiff the minFiringTimeDiff to set
     */
    public void setMinFiringTimeDiff(float minFiringTimeDiff) {
        support.firePropertyChange("minFiringTimeDiff", this.minFiringTimeDiff, minFiringTimeDiff);
        this.minFiringTimeDiff = minFiringTimeDiff;
        getPrefs().putFloat("CUDAObjectTrackerControl.minFiringTimeDiff", minFiringTimeDiff);
        sendParameter(CMD_MIN_FIRING_TIME_DIFF, minFiringTimeDiff);
    }

    /**
     * @return the eISynWeight
     */
    public float geteISynWeight() {
        return eISynWeight;
    }

    /**
     * @param eISynWeight the eISynWeight to set, this is weight from template LIF neurons to global WTA neuron
     */
    public void seteISynWeight(float eISynWeight) {
        if (eISynWeight < 0) {
            eISynWeight = 0; // clamp to non negative
        }
        support.firePropertyChange("eISynWeight", this.eISynWeight, eISynWeight);
        this.eISynWeight = eISynWeight;
        getPrefs().putFloat("CUDAObjectTrackerControl.eISynWeight", eISynWeight);
        sendParameter(CMD_E_I_NEURON_POTENTIAL, eISynWeight);
    }

    /**
     * @return the iESynWeight
     */
    public float getiESynWeight() {
        return iESynWeight;
    }

    /**
     * @param iESynWeight the iESynWeight to set
     */
    public void setiESynWeight(float iESynWeight) {
        if (iESynWeight < 0) {
            iESynWeight = 0; // clamp nonnegative
        }
        support.firePropertyChange("iESynWeight", this.iESynWeight, iESynWeight);
        this.iESynWeight = iESynWeight;
        getPrefs().putFloat("CUDAObjectTrackerControl.iESynWeight", iESynWeight);
        sendParameter(CMD_I_E_NEURON_POTENTIAL, iESynWeight);
    }

    /**
     * @return the kernelShape
     */
    public KernelShape getKernelShape() {
        return kernelShape;
    }

    /**
     * @param kernelShape the kernelShape to set
     */
    public void setKernelShape(KernelShape kernelShape) {
        support.firePropertyChange("kernelShape", this.kernelShape, kernelShape);
        this.kernelShape = kernelShape;
        getPrefs().put("CUDAObjectTrackerControl.kernelShape", kernelShape.toString());
        writeCommandToCuda(CMD_KERNEL_SHAPE + " " + kernelShape.toString());
    }

//    /**
//     * @return the spikePartitioningMethod
//     */
//    public SpikePartitioningMethod getSpikePartitioningMethod() {
//        return spikePartitioningMethod;
//    }
//
//    /**
//     * @param spikePartitioningMethod the spikePartitioningMethod to set
//     */
//    public void setSpikePartitioningMethod(SpikePartitioningMethod spikePartitioningMethod) {
//        support.firePropertyChange("kernelShape", this.spikePartitioningMethod, spikePartitioningMethod);
//        this.spikePartitioningMethod=spikePartitioningMethod;
//        getPrefs().put("CUDAObjectTrackerControl.spikePartitioningMethod", spikePartitioningMethod.toString());
//        writeCommandToCuda(CMD_SPIKE_PARTITIONING_METHOD+" "+spikePartitioningMethod.toString());
//    }
    /**
     * @return the cudaEnabled
     */
    public boolean isCudaEnabled() {
        return cudaEnabled;
    }

    /**
     * @param cudaEnabled the cudaEnabled to set
     */
    public void setCudaEnabled(boolean cudaEnabled) {
        support.firePropertyChange("cudaEnabled", this.cudaEnabled, cudaEnabled);
        this.cudaEnabled = cudaEnabled;
        getPrefs().putBoolean("CUDAObjectTrackerControl.cudaEnabled", cudaEnabled);
        writeCommandToCuda(CMD_CUDA_ENABLED + " " + cudaEnabled);
    }

    /**
     * @return the debugLevel
     */
    public int getDebugLevel() {
        return debugLevel;
    }

    /**
     * @param debugLevel the debugLevel to set
     */
    public void setDebugLevel(int debugLevel) {
        support.firePropertyChange("debugLevel", this.debugLevel, debugLevel);
        this.debugLevel = debugLevel;
        getPrefs().putInt("CUDAObjectTrackerControl.debugLevel", debugLevel);
        writeCommandToCuda(CMD_DEBUG_LEVEL + " " + debugLevel);
    }

    /**
     * @return the maxXmitIntervalMs
     */
    public int getMaxXmitIntervalMs() {
        return maxXmitIntervalMs;
    }

    /**
     * @param maxXmitIntervalMs the maxXmitIntervalMs to set
     */
    public void setMaxXmitIntervalMs(int maxXmitIntervalMs) {
        support.firePropertyChange("maxXmitIntervalMs", this.maxXmitIntervalMs, maxXmitIntervalMs);
        this.maxXmitIntervalMs = maxXmitIntervalMs;
        getPrefs().putInt("CUDAObjectTrackerControl.maxXmitIntervalMs", maxXmitIntervalMs);
        writeCommandToCuda(CMD_MAX_XMIT_INTERVAL_MS + " " + maxXmitIntervalMs);
    }

    /**
     * @return the deltaTimeUs
     */
    public int getDeltaTimeUs() {
        return deltaTimeUs;
    }

    /**
     * @param deltaTimeUs the deltaTimeUs to set
     */
    public void setDeltaTimeUs(int deltaTimeUs) {
        support.firePropertyChange("deltaTimeUs", this.deltaTimeUs, deltaTimeUs);
        this.deltaTimeUs = deltaTimeUs;
        getPrefs().putInt("CUDAObjectTrackerControl.deltaTimeUs", deltaTimeUs);
        writeCommandToCuda(CMD_DELTA_TIME_US + " " + deltaTimeUs);
    }

    /**
     * @return the numObject
     */
    public int getNumObject() {
        return numObject;
    }

    /**
     * @param numObject the numObject to set
     */
    synchronized public void setNumObject(int numObject) {
        if (numObject < 1) {
            numObject = 1;
        } else if (numObject > 5) {
            numObject = 5;
        }
        int old=this.numObject;
        this.numObject = numObject;
        support.firePropertyChange("numObject", old, numObject);
        if (cudaChip != null) {
            cudaChip.setNumCellTypes(numObject);
        }
        getPrefs().putInt("CUDAObjectTrackerControl.numObject", numObject);
        writeCommandToCuda(CMD_NUM_OBJECTS + " " + numObject);
        makeGaborTemplates();
    }

    // TODO these classes define properties for communicating with CUDA, but i cannot see how to statically compile in the get/set
    // to go with them to allow introspection to find them
    public class CUDACommand {

        String cmd = null;
        String tooltip = null;
        String name;

        public void writeCommand() {
            writeCommandToCuda(cmd);
        }

        public CUDACommand(String name, String cmd, String tooltip) {
            this.cmd = cmd;
            this.tooltip = tooltip;
            this.name = name;
            setPropertyTooltip(cmd, tooltip);
        }
    }

    // TODO figure out how to use CUDAParameter to add properties to this class and GUI build, not used now
    public class CUDAParameter extends CUDACommand {

        Object obj;

        public CUDAParameter(String name, String cmd, Object obj, String tooltip) {
            super(name, cmd, tooltip);
            this.obj = obj;
        }

        @Override
        public void writeCommand() {
            writeCommandToCuda(cmd + " " + obj.toString());
        }

        public Object get() {
            return obj;
        }

        public void set(Object obj) {
            support.firePropertyChange(name, get(), obj);
            this.obj = obj;
            getPrefs().put("CUDAObjectTrackerControl." + name, obj.toString());
        }
    }

    private TemplatesFrame templatesFrame = null;

    private class TemplatesFrame extends JFrame implements PropertyChangeListener {

        TemplatesFrame() {
            super("CUDA templates");
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            rebuildContents();
            pack();
        }

        public void propertyChange(PropertyChangeEvent evt) {
            rebuildContents();
        }

        private void rebuildContents() {
            getContentPane().removeAll();
            setLayout(new GridLayout(0, 1));
            for (CUDATemplate t : templates) {
                GLJPanel p = t.createPanel();
                getContentPane().add(p);
            }
            validate();
//            pack();
        }
    }

    public void doShowTemplates() {
        if (templatesFrame != null) {
            templatesFrame.setVisible(true);
            return;
        }
        if (templates == null) {
            log.warning("no templates to display");
            return;
        }
        templatesFrame = new TemplatesFrame();
        templatesFrame.setVisible(true);
    }

    public void makeGaborTemplates() {
        templates.clear();
        for (int i = 0; i < numObject; i++) {
            templates.add(new GaborTemplate("gabor", i));
        }
        if(templatesFrame!=null) templatesFrame.propertyChange(null);
    }

    public class RandomTemplate extends CUDATemplate {

        public RandomTemplate() {
            super("random", 0, TEMPLATE_SIZE);
            Random ran = new Random();
            for (int j = 0; j < size * size; j++) {
                values[j] = ran.nextFloat() * 3 - 10;
            }
        }
    }

    public class GaborTemplate extends CUDATemplate implements PropertyChangeListener {

        final float psi = 0;

        public GaborTemplate(String name, int index) {
            super(name, index, TEMPLATE_SIZE);
            computeValues();
            support.addPropertyChangeListener("gaborMaxAmp", this);
            support.addPropertyChangeListener("gaborLambda", this);
            support.addPropertyChangeListener("gaborGamma", this);
            support.addPropertyChangeListener("gaborBandwidth", this);
        }

        public void computeValues() {
            float sigma_x = (float) (1 / Math.PI * Math.sqrt(Math.log(2.0f) / 2) * ((Math.pow(2, gaborBandwidth) + 1) / (Math.pow(2, gaborBandwidth) - 1)) * gaborLambda);
            float sigma_y = sigma_x / gaborGamma;
            float x;
            float y;
            float theta_radian = index * (180f / numObject) / 360 * 2 * (float) Math.PI;
            float x_theta;
            float y_theta;
            for (int i = 0; i < size; i++) {
                y = -gaborMaxAmp + i * gaborMaxAmp / (float) (size - 1) * 2;
                for (int j = 0; j < size; j++) {
                    x = -gaborMaxAmp + j * gaborMaxAmp / (float) (size - 1) * 2;
                    x_theta = (float) (x * Math.cos(theta_radian) + y * Math.sin(theta_radian));
                    y_theta = (float) (-x * Math.sin(theta_radian) + y * Math.cos(theta_radian));
                    values[i * size + j] = (float) (gaborMaxAmp * Math.exp(-(Math.pow(x_theta, 2) / Math.pow(sigma_x, 2) + Math.pow(y_theta, 2) / Math.pow(sigma_y, 2)) / 2) * Math.cos(2 * Math.PI / gaborLambda * x_theta + psi));
                }
            }
            name=String.format("gabor%.0f",theta_radian*180/Math.PI);
        }

        public void propertyChange(PropertyChangeEvent evt) {
            computeValues();
            if(getPanel()!=null) getPanel().repaint();
            writeCommand();
        }
    }

    public class CUDATemplate extends CUDACommand {

        int size;
        int index;
        float[] values; // indexed by row*size+col e.g first elements are all from top row from left to right
        TemplatePanel panel=null;

        public CUDATemplate(String name, int index, int size) {
            super(name, CMD_TEMPLATE, "template <index> <name> <size> <value00, value01... value10 value11 ... valueNN>");
            this.size = size;
            this.index = index;
            values = new float[size * size];
            if (name.contains(" ")) {
                name = name.replace(" ", "_");
            }
        }

        public float[] getValues() {
            return values;
        }

        @Override
        public void writeCommand() {
            writeTemplateToCuda();
        }

        public void writeTemplateToCuda() {
            StringBuilder sb = new StringBuilder(cmd + " " + index + " " + size + " ");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    sb.append(String.format("%.3f ", values[i * size + j]));
                }
            }
            writeCommandToCuda(sb.toString());
        }

        public TemplatePanel createPanel(){
            panel=new TemplatePanel();
            return panel;
        }

        public GLJPanel getPanel() {
             return panel;
        }

        private class TemplatePanel extends GLJPanel {

            TemplatePanel() {
                addGLEventListener(new Renderer());   // let chart draw itself
                setDoubleBuffered(true);
                setSize(4 * size, 4 * size);
            }

            private class Renderer implements GLEventListener {

                String title = name;
                GLU glu = new GLU();
                /** The area to draw the chart body. */
                Rectangle bodyArea;
                /** The area to draw the title. */
                private Rectangle titleArea;
                /** A renderer for the title. */
                private com.sun.opengl.util.j2d.TextRenderer titleRenderer;
                protected int gridId; // the opengl id to access drawing list to draw grid

                public void init(GLAutoDrawable drawable) {
                    GL gl = drawable.getGL();

                    // background color
                    float[] bg = new float[4];
                    getBackground().getColorComponents(bg);
                    gl.glClearColor(bg[0], bg[1], bg[2], bg[3]);

                    // title
                    titleRenderer = new TextRenderer(new Font("Helvetica", Font.PLAIN, 12));
                    Rectangle2D bounds = titleRenderer.getBounds(title);
                    titleArea = new Rectangle((int) bounds.getWidth(), (int) bounds.getHeight());

//                    /* create grid drawing routine */
//                    gridId = gl.glGenLists(1);
//                    gl.glNewList(gridId, GL.GL_COMPILE);
//                    gl.glEndList();

                }

                public void display(GLAutoDrawable drawable) {
                    GL gl = drawable.getGL();

                    gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                    gl.glMatrixMode(GL.GL_MODELVIEW);
                    gl.glLoadIdentity();

                    // title
                    titleRenderer.beginRendering(getWidth(), getHeight());
                    titleRenderer.setColor(getForeground());
                    titleRenderer.draw(title, titleArea.x, titleArea.y);
                    titleRenderer.endRendering();

                    /* draw body */
                    gl.glTranslated(bodyArea.x, bodyArea.y, 0);
                    gl.glScaled(bodyArea.width, bodyArea.height, 1);

                    final int wi = bodyArea.width,  hi = bodyArea.height;
                    float scale = ((float) hi) / size;
                    gl.glPixelZoom(scale, scale);
                    gl.glRasterPos2f(bodyArea.x, bodyArea.y);

                    {
                        float[] pixvals = new float[values.length];
                        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                        for (float f : values) {
                            if (f < min) {
                                min = f;
                            } else if (f > max) {
                                max = f;
                            }
                        }
                        float m = 1 / (max - min);
                        for (int i = 0; i < values.length; i++) {
                            pixvals[i] = m * (values[i] - min);
                        }
                        FloatBuffer pixmap = FloatBuffer.wrap(pixvals);
                        pixmap.position(0);
                        gl.glDrawPixels(size, size, GL.GL_LUMINANCE, GL.GL_FLOAT, pixmap);
                    }
                }

                public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                    GL gl = drawable.getGL();
                    gl.glMatrixMode(GL.GL_PROJECTION);
                    gl.glLoadIdentity();
                    glu.gluOrtho2D(0, width, 0, height); // left, right, bottom, top

                    /* layout components */
                    Insets insets = getInsets();
                    bodyArea = new Rectangle(insets.left, insets.bottom, width - insets.left - insets.right, height - insets.bottom - insets.top);
                    // layout title
                    titleArea.x = 0; // centered is here (width - titleArea.width) / 2;
                    titleArea.y = height - insets.top / 2 - titleArea.height;
                    bodyArea.height -= titleArea.height;


                    /* set cliping area for body: left, right, bottom, top. */
                    gl.glClipPlane(GL.GL_CLIP_PLANE0, new double[]{1.0, 0.0, 0.0, -bodyArea.x}, 0);
                    gl.glClipPlane(GL.GL_CLIP_PLANE1, new double[]{-1.0, 0.0, 0.0, bodyArea.x + bodyArea.width}, 0);
                    gl.glClipPlane(GL.GL_CLIP_PLANE2, new double[]{0.0, 1.0, 0.0, -bodyArea.y}, 0);
                    gl.glClipPlane(GL.GL_CLIP_PLANE3, new double[]{0.0, -1.0, 0.0, bodyArea.x + bodyArea.height}, 0);
                }

                public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
                }
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(cmd + " " + index + " " + name + " " + size + " ");
            sb.append("\n");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    sb.append(String.format("%8.2f ", values[i * size + j]));
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        if (cudaExtractor == null) {
            return;
        }
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        gl.glColor3f(1, 0, 0);
        gl.glLineWidth(3);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2i(-2, 1);
        gl.glVertex2i(-2, 1 + cudaExtractor.getInhibNeuronSpikeCount());  // the raw event extractor counts inhibitory neuron firing
        gl.glEnd();
        gl.glPopMatrix();

    }

    /**
     * @return the gaborBandwidth
     */
    public float getGaborBandwidth() {
        return gaborBandwidth;
    }

    private void sendGabors() {
        
        for(CUDATemplate t:templates){
            t.writeCommand();
        }

//        writeCommandToCuda(CMD_GABOR_MAX_AMP + " " + gaborMaxAmp);
//        writeCommandToCuda(CMD_GABOR_BAND_WIDTH + " " + gaborBandwidth);
//        writeCommandToCuda(CMD_GABOR_WAVELENGTH + " " + gaborLambda);
////        writeCommandToCuda(CMD_GABOR_PHASE_OFFSET + " " + gaborPhase);
//        writeCommandToCuda(CMD_GABOR_ASPECT_RATIO + " " + gaborGamma);
    }

    /**
     * @param gaborBandwidth the gaborBandwidth to set
     */
    public void setGaborBandwidth(float gaborBandwidth) {
        float old=this.gaborBandwidth;
        this.gaborBandwidth = gaborBandwidth;
        getPrefs().putFloat("CUDAObjectTrackerControl.gaborBandwidth", gaborBandwidth);
        support.firePropertyChange("gaborBandwidth", old, gaborBandwidth);
        sendGabors();
    }

    /**
     * @return the gaborLambda
     */
    public float getGaborLambda() {
        return gaborLambda;
    }

    /**
     * @param gaborLambda the gaborLambda to set
     */
    public void setGaborLambda(float gaborLambda) {
        float old=this.gaborLambda;
        this.gaborLambda = gaborLambda;
        getPrefs().putFloat("CUDAObjectTrackerControl.gaborLambda", gaborLambda);
        support.firePropertyChange("gaborGamma", old, gaborGamma);
        sendGabors();
    }

    /**
     * @return the gaborGamma
     */
    public float getGaborGamma() {
        return gaborGamma;
    }

    /**
     * @param gaborGamma the gaborGamma to set
     */
    public void setGaborGamma(float gaborGamma) {
       float old=this.gaborGamma;
        this.gaborGamma = gaborGamma;
        getPrefs().putFloat("CUDAObjectTrackerControl.gaborGamma", gaborGamma);
        support.firePropertyChange("gaborGamma", old, gaborGamma);
        sendGabors();
    }

    /**
     * @return the gaborMaxAmp
     */
    public float getGaborMaxAmp() {
        return gaborMaxAmp;
    }

    /**
     * @param gaborMaxAmp the gaborMaxAmp to set
     */
    public void setGaborMaxAmp(float gaborMaxAmp) {
        float old=this.gaborMaxAmp;
        this.gaborMaxAmp = gaborMaxAmp;
        getPrefs().putFloat("CUDAObjectTrackerControl.gaborMaxAmp", gaborMaxAmp);
        support.firePropertyChange("gaborMaxAmp", old, gaborMaxAmp);
        sendGabors();
    }

//    /**
//     * @return the gaborPhase
//     */
//    public float getGaborPhase() {
//        return gaborPhase;
//    }
//
//    /**
//     * @param gaborPhase the gaborPhase to set
//     */
//    public void setGaborPhase(float gaborPhase) {
//        support.firePropertyChange("gaborPhase",this.gaborPhase,gaborPhase);
//        this.gaborPhase = gaborPhase;
//         getPrefs().putFloat("CUDAObjectTrackerControl.gaborPhase",gaborPhase);
//        sendGaborParameters();
//   }
}
