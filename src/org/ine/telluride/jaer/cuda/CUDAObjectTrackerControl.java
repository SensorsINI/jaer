/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;

import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.graphics.AEViewer;

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
public class CUDAObjectTrackerControl extends EventFilter2D {

    AEViewer outputViewer=null;
    
    private int controlPort = getPrefs().getInt("CUDAObjectTrackerControl.controlPort", 9998);
    private String hostname = getPrefs().get("CUDAObjectTrackerControl.hostname", "localhost");
    public static final String CUDA_CMD_PARAMETER = "param";
    private String cudaExecutablePath = getPrefs().get("CUDAObjectTrackerControl.cudaExecutablePath", null);
    private String cudaEnvironmentPath = getPrefs().get("CUDAObjectTrackerControl.cudaEnvironmentPath", null);
    private DatagramSocket controlSocket = null;
    private OutputStreamWriter writer = null;
    InetAddress cudaInetAddress = null;
    private boolean cudaEnabled=getPrefs().getBoolean("CUDAObjectTrackerControl.cudaEnabled",true);

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
    static final String CMD_THRESHOLD = "threshold";
    private float threshold = getPrefs().getFloat("CUDAObjectTrackerControl.threshold", 100);
    static final String CMD_MEMBRANE_TAU = "membraneTau";
    private float membraneTauUs = getPrefs().getFloat("CUDAObjectTrackerControl.membraneTauUs", 10000);
    static final String CMD_MEMBRANE_POTENTIAL_MIN = "membranePotentialMin";
    private float membranePotentialMin = getPrefs().getFloat("CUDAObjectTrackerControl.membranePotentialMin", -50);
    static final String CMD_MIN_FIRING_TIME_DIFF = "minFiringTimeDiff";
    private float minFiringTimeDiff = getPrefs().getFloat("CUDAObjectTrackerControl.minFiringTimeDiff", 15000);
    static final String CMD_E_I_NEURON_POTENTIAL = "eISynWeight";
    private float eISynWeight = getPrefs().getFloat("CUDAObjectTrackerControl.eISynWeight", 10);
    static final String CMD_I_E_NEURON_POTENTIAL = "iESynWeight";
    private float iESynWeight = getPrefs().getFloat("CUDAObjectTrackerControl.iESynWeight", 10);
    static final String CMD_EXIT="exit";

    static final String CMD_CUDA_ENABLED="enableCuda";

//    static final String CMD_TERMINATE_IMMEDIATELY="terminate";

    static final String CMD_KERNEL_SHAPE="kernelShape";
    static final String CMD_SPIKE_PARTITIONING_METHOD="spikePartitioningMethod";

    private void checkOutputViewer() throws HeadlessException {
        if(outputViewer==null) {
            outputViewer=new AEViewer(chip.getAeViewer().getJaerViewer());
            Class originalChipClass=outputViewer.getAeChipClass();
            outputViewer.setAeChipClass(CUDAOutputAEChip.class);
            outputViewer.setVisible(true);
            outputViewer.setPreferredAEChipClass(originalChipClass);
            outputViewer.reopenSocketInputStream();
        }
    }

         public enum KernelShape {

        DoG, Circle
    };

    public enum SpikePartitioningMethod {

        SingleSpike, MultipleSpike
    };


    private KernelShape kernelShape = KernelShape.valueOf(getPrefs().get("CUDAObjectTrackerControl.kernelShape", KernelShape.DoG.toString()));
    private SpikePartitioningMethod spikePartitioningMethod = SpikePartitioningMethod.valueOf(getPrefs().get("CUDAObjectTrackerControl.spikePartitioningMethod", SpikePartitioningMethod.MultipleSpike.toString()));

    public CUDAObjectTrackerControl(AEChip chip) {
        super(chip);
        setPropertyTooltip("hostname", "hostname or IP address of CUDA process");
        setPropertyTooltip("controlPort", "port number of CUDA process UDP control port");
        setPropertyTooltip("cudaEnvironmentPath", "Windows PATH to include CUDA stuff (cutil32.dll), e.g. c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug");
        setPropertyTooltip("cudaExecutablePath", "Full path to CUDA process executable");
        setPropertyTooltip("threshold", "neuron spike thresholds");
        setPropertyTooltip("membraneTauUs", "neuron membrane decay time constant in us");
        setPropertyTooltip("membranePotentialMin", "neuron reset potential");
        setPropertyTooltip("minFiringTimeDiff", "refractory period in us for spikes from jear to cuda - spike intervals shorter to this from a cell are not processed");
        setPropertyTooltip("eISynWeight", "excitatory to inhibitory weights");
        setPropertyTooltip("iESynWeight", "inhibitory to excitatory weights");
        setPropertyTooltip("cudaEnabled","true to enable use of CUDA hardware - false to run on host");
        setPropertyTooltip("KillCUDA","kills CUDA process, iff started from jaer");
        setPropertyTooltip("SelectCUDAExecutable", "select the CUDA executable (.exe) file");
        setPropertyTooltip("LaunchCUDA","Launches the selected CUDA executable");

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
    }
    Process cudaProcess = null;
    ProcessBuilder cudaProcessBuilder = null;

    public void doKillCUDA() {
        writeCommandToCuda(CMD_EXIT);
        try{Thread.sleep(200);}catch(InterruptedException e){}; // let cuda print results
        if (cudaProcess != null) {
            cudaProcess.destroy(); // kill it anyhow if we started it
        }
    }

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
            } catch (Exception ex) {
                log.warning(ex.toString() + " to " + hostname + ":" + controlPort);
                return false;
//                return launchCuda();
            }
        }
        return true;
    }

    private void sendParameters() {
        sendParameter(CMD_THRESHOLD, threshold);
        sendParameter(CMD_I_E_NEURON_POTENTIAL, iESynWeight);
        sendParameter(CMD_E_I_NEURON_POTENTIAL, eISynWeight);
        sendParameter(CMD_MEMBRANE_POTENTIAL_MIN, membranePotentialMin);
        sendParameter(CMD_MEMBRANE_TAU, membraneTauUs);
        sendParameter(CMD_MIN_FIRING_TIME_DIFF, minFiringTimeDiff);
    }

    private void sendParameter(String name, float value) {
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

    /** does nothing for this filter */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if (!isCudaRunning()) {
            log.warning("cuda has not been started from jaer or has terminated");
            return in;
        }
        checkControlSocket();
        return in;
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if(yes) checkOutputViewer();
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
     * @param eISynWeight the eISynWeight to set
     */
    public void seteISynWeight(float eISynWeight) {
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
        writeCommandToCuda(CMD_KERNEL_SHAPE+" "+kernelShape.toString());
    }

    /**
     * @return the spikePartitioningMethod
     */
    public SpikePartitioningMethod getSpikePartitioningMethod() {
        return spikePartitioningMethod;
    }

    /**
     * @param spikePartitioningMethod the spikePartitioningMethod to set
     */
    public void setSpikePartitioningMethod(SpikePartitioningMethod spikePartitioningMethod) {
        support.firePropertyChange("kernelShape", this.spikePartitioningMethod, spikePartitioningMethod);
        this.spikePartitioningMethod = spikePartitioningMethod;
        getPrefs().put("CUDAObjectTrackerControl.spikePartitioningMethod", spikePartitioningMethod.toString());
        writeCommandToCuda(CMD_SPIKE_PARTITIONING_METHOD+" "+spikePartitioningMethod.toString());
     }

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
        support.firePropertyChange("cudaEnabled",this.cudaEnabled,cudaEnabled);
        this.cudaEnabled = cudaEnabled;
        getPrefs().putBoolean("CUDAObjectTrackerControl.cudaEnabled",cudaEnabled);
        writeCommandToCuda(CMD_CUDA_ENABLED+" "+cudaEnabled);
    }


}
