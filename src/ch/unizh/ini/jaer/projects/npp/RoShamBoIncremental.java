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

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
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
    private static final String CMD_NEW_SYMBOL_AVAILABLE = "newsymbol", CMD_PROGRESS = "progress", CMD_LOAD_NETWORK = "loadnetwork";
    private Thread portListenerThread = null;
    private ProgressMonitor progressMonitor = null;

    public RoShamBoIncremental(AEChip chip) {
        super(chip);
        String learn = "0. Incremental learning";
        setPropertyTooltip(learn, "LearnSymbol0", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol1", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol2", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol3", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol4", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol5", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "ChooseSymbolsFolder", "Choose a folder to store the symbol AVI data files");
        setPropertyTooltip(learn, "hostname", "learning host name (IP or DNS)");
        setPropertyTooltip(learn, "portSendTo", "learning host port number that we send to");
        setPropertyTooltip(learn, "portListenOn", "local port number we listen on to get message back from learning server");
        aviWriter = new DvsSliceAviWriter(chip);
        aviWriter.setEnclosed(true, this);
        aviWriter.setFrameRate(60);
        aviWriter.getDvsFrame().setOutputImageHeight(64);
        aviWriter.getDvsFrame().setOutputImageWidth(64);
        getEnclosedFilterChain().add(aviWriter);
    }

    public void doChooseSymbolsFolder() {
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

    private void closeSymbolFileAndSendMessage() {
        log.info("stopping symbol, starting training");
        aviWriter.doCloseFile();
        sendUDPMessage(CMD_NEW_SYMBOL_AVAILABLE + " " + aviWriter.getFile().toPath());
    }

    public void doToggleOnLearnSymbol0() {
        openSymbolFileAndStartRecording("symbol0");

    }

    private void openSymbolFileAndStartRecording(String prefix) {
        log.info("recording symbol");
        aviWriter.openAVIOutputStream(lastSymbolsPath.resolve(prefix + ".avi").toFile(), new String[]{"# " + prefix});
    }

    public void doToggleOffLearnSymbol0() {
        closeSymbolFileAndSendMessage();
    }

    public void doToggleOnLearnSymbol1() {
        openSymbolFileAndStartRecording("symbol1");

    }

    public void doToggleOffLearnSymbol1() {
        closeSymbolFileAndSendMessage();
    }

    public void doToggleOnLearnSymbol2() {
        openSymbolFileAndStartRecording("symbol2");
    }

    public void doToggleOffLearnSymbol2() {
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

    private void parseMessage(String msg) {
        log.info("parsing message \"" + msg + "\"");
        if (msg == null) {
            log.warning("got null message");
            return;
        }
        StringTokenizer tokenizer = new StringTokenizer(msg);
        switch (tokenizer.nextToken()) {
            case CMD_NEW_SYMBOL_AVAILABLE:
                log.warning("learning server should not send this message; it is for us to send");
                return;
            case CMD_LOAD_NETWORK:
                String networkFilename = tokenizer.nextToken();
                if (networkFilename == null || networkFilename.isEmpty()) {
                    log.warning("null filename supplied for new network");
                    return;
                }
                synchronized (this) {
                    try {
                        log.info("loading new CNN from "+networkFilename);
                        loadNetwork(new File(networkFilename));
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Exception loading new network", e);
                    }
                }
                break;
            case CMD_PROGRESS:
                try {
                    int progress = Integer.parseInt(tokenizer.nextToken());
                    if (progressMonitor == null || progress==0) {
                        progressMonitor = new ProgressMonitor(chip.getFilterFrame(), "Training", "note", 0, 100);
                    }
                    progressMonitor.setProgress(progress);
                } catch (Exception e) {
                    log.warning("exception updating progress monitor: " + e.toString());
                }

                break;
            default:
                log.warning("unknown token or comamnd in message \"" + msg + "\"");
        }
    }

    private void sendUDPMessage(String string) {

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
                            String msg = new String(datagram.getData());
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
}
