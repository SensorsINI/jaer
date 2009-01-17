/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

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

    private int controlPort = getPrefs().getInt("CUDAObjectTrackerControl.controlPort", 9998);
    private String hostname = getPrefs().get("CUDAObjectTrackerControl.hostname", "localhost");
    public static final String CUDA_CMD_PARAMETER = "param";
    private String cudaExecutablePath = getPrefs().get("CUDAObjectTrackerControl.cudaExecutablePath", null);
    private String cudaEnvironmentPath = getPrefs().get("CUDAObjectTrackerControl.cudaEnvironmentPath", null);
    static final String CMD_THRESHOLD = "threshold";
    private float threshold = getPrefs().getFloat("CUDAObjectTrackerControl.threshold", 1f);
    private Socket socket = null;
    private OutputStreamWriter writer = null;

    public CUDAObjectTrackerControl(AEChip chip) {
        super(chip);
        setPropertyTooltip("hostname", "hostname or IP address of CUDA server");
        setPropertyTooltip("controlPort", "port number of CUDA server TCP control port");
        setPropertyTooltip("cudaEnvironmentPath", "Windows PATH to include CUDA stuff (cutil32.dll), e.g. c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug");
        setPropertyTooltip("cudaExecutablePath", "Full path to CUDA template executable");

        if (cudaEnvironmentPath == null || cudaEnvironmentPath.isEmpty()) {
//             String cudaBinPath=System.getenv("CUDA_BIN_PATH");
//            String cudaLibPath=System.getenv("CUDA_LIB_PATH");
//            cudaEnvironmentPath = cudaBinPath+File.pathSeparator+cudaLibPath; // "c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug";
            cudaEnvironmentPath = "c:\\cuda\\bin;c:\\Program Files\\NVIDIA Corporation\\NVIDIA CUDA SDK\\bin\\win32\\Debug";
        }
    }
    Process cudaProcess = null;
    ProcessBuilder cudaProcessBuilder = null;

    public void doKillCuda() {
        if(cudaProcess!=null) cudaProcess.destroy();
    }

    public void doLaunchCuda() {

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
//            Thread.sleep(100);
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
                            log.warning(ex.getMessage());
                        }
                    } catch (InterruptedException ex) {
                        log.warning(ex.getMessage());
                    }
                }
            };
            outThread.start();
//            return true;
        } catch (Exception ex) {
            log.warning(ex.getMessage());
//            return false;
        }
    }

    private boolean checkSocket() {
        if (socket == null) {
            try {
                socket = new Socket(getHostname(), getControlPort());
                writer = new OutputStreamWriter(socket.getOutputStream());
            } catch (Exception ex) {
                log.warning(ex.getMessage() + " to " + hostname + ":" + controlPort);
                return false;
//                return launchCuda();
            }
        }
        return true;
    }

    private boolean isCudaRunning(){
        if(cudaProcess==null) return false;
        try{
            cudaProcess.exitValue();
            return false;
        }catch(IllegalThreadStateException e){
            return true;
        }
    }

    /** does reconnect to CUDA server */
    public void doReconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            socket = null;
        }
        checkSocket();
    }

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

    private void writeString(String s) {
        if (!checkSocket()) {
            return;
        }
        try {
            writer.write(s);
        } catch (IOException ex) {
            log.warning("writing string " + s + " got " + ex);
        }
    }

    private void sendParameter(String name, float value) {
        String s = String.format(name + " " + value);
        writeString(s);
    }

    /** does nothing for this filter */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if(!isCudaRunning()){
            log.warning("cuda has not been started from jaer or has terminated");
            return in;
        }
        checkSocket();
        return in;
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
        support.firePropertyChange("controlPort",controlPort,port);
        this.controlPort = port;
        getPrefs().putInt("CUDAObjectTrackerControl.controlPort", controlPort);
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        support.firePropertyChange("hostname",this.hostname, hostname);
        this.hostname = hostname;
        getPrefs().put("CUDAObjectTrackerControl.hostname", hostname);
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
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
}
