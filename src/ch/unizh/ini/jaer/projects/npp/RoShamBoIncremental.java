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

import com.jogamp.opengl.GLAutoDrawable;
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
import java.util.List;
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
            CMD_CANCEL_TRAINING = "cancel",
            CMD_PING = "ping",
            CMD_PONG = "pong";
    private Thread portListenerThread = null;
    private ProgressMonitor progressMonitor = null;
    private String lastNewClassName = getString("lastNewClassName", "");

    // to test, open a terminal, and use netcat -u localhost portSendTo in one panel and network -ul portListenOn in another panel
    public RoShamBoIncremental(AEChip chip) {
        super(chip);
        String learn = "0. Incremental learning";
        setPropertyTooltip(learn, "SampleNewClass", "Toggle collecting sample data for a new class");
        setPropertyTooltip(learn, "StartTraining", "Starts training on samples");
        setPropertyTooltip(learn, "CancelTraining", "Cancels ongoing training");
        setPropertyTooltip(learn, "ChooseSamplesFolder", "Choose a folder to store the symbol AVI data files");
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
    }

    public void doResetToBaseNetwork() {
        try {
            loadNetwork(new File(getLastManuallyLoadedNetwork()));
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e.getCause());
            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Couldn't load base network: " + e.toString(), "Bad network file", JOptionPane.WARNING_MESSAGE);
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
        sendUDPMessage(CMD_PING);

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
        }
    }

    public void doStartTraining() {
        try {
            sendUDPMessage(CMD_NEW_SAMPLES_AVAILABLE + " " + lastSymbolsPath); // inform only of the destination folder; class name is in filename
            lastNewClassName = newname;
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.WARNING, null, ex);
            showWarningDialogInSwingThread(ex.toString(), "Exception");
        }

    }
    
    public void doCancelTraining(){
        try {
            sendUDPMessage(CMD_CANCEL_TRAINING);
            lastNewClassName = newname;
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoIncremental.class.getName()).log(Level.WARNING, null, ex);
            showWarningDialogInSwingThread(ex.toString(), "Exception");
        }
      
    }

    private void openSymbolFileAndStartRecording(String prefix) {
        log.info("recording symbol");
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

    private void showWarningDialogInSwingThread(String msg, String title) {
        SwingUtilities.invokeLater(new Runnable() { // outside swing thread, must do this
            public void run() {
                JOptionPane.showMessageDialog(chip.getFilterFrame(), msg, title, JOptionPane.WARNING_MESSAGE);
            }
        });

    }

    private void showPlainMessageDialogInSwingThread(String msg, String title) {
        SwingUtilities.invokeLater(new Runnable() { // outside swing thread, must do this
            public void run() {
                JOptionPane.showMessageDialog(chip.getFilterFrame(), msg, title, JOptionPane.PLAIN_MESSAGE);
            }
        });

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
            case CMD_PING:
                sendUDPMessage(CMD_PONG);
                return;
            case CMD_NEW_SAMPLES_AVAILABLE:
                log.warning("learning server should not send this message; it is for us to send");
                return;
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

    synchronized private void sendUDPMessage(String string) { // sync for thread safety on multiple senders

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
        synchronized (this) {
            if (progressMonitor != null && progressMonitor.isCanceled()) {
                sendUDPMessage(CMD_CANCEL_TRAINING);

            }
        }
    }

}
