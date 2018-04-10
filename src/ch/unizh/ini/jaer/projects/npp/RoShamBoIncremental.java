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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
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
    private DatagramChannel channel = null;
    private DatagramSocket socket = null;
    private InetSocketAddress client = null;
    private String host = getString("hostname", "localhost");
    public static final int DEFAULT_PORT = 14334;
    private int port = getInt("port", DEFAULT_PORT);
    private String NEW_SYMBOL_AVAILABLE = "newsymbol";
    private Thread portListenerThread = null;

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
        setPropertyTooltip(learn, "port", "learning host port number");
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
        sendUDPMessage(NEW_SYMBOL_AVAILABLE + " " + aviWriter.getFile().toPath());
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

    public int getPort() {
        return port;
    }

    /**
     * You set the port to say which port the packet will be sent to.
     *
     * @param port the UDP port number.
     */
    public void setPort(int port) {
        this.port = port;
        putInt("port", port);
    }

    private void sendUDPMessage(String string) {
        if (channel == null) {
            try {
                channel = DatagramChannel.open();
                if (portListenerThread == null || !portListenerThread.isAlive()) {
                    portListenerThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ByteBuffer udpBuf = ByteBuffer.allocate(1024);

                            InetSocketAddress localSocketAddress = new InetSocketAddress(host, port);
                            try {
                                channel.bind(localSocketAddress);
                            } catch (IOException ex) {
                                log.warning("couldn't bind local socket address: " + ex.toString());
                                return;
                            }
                            log.info("opened channel on local port " + localSocketAddress + " to receive UDP messages from learning server.");
                            while (true) {
                                try {
                                    channel.receive(udpBuf);
                                    udpBuf.flip();
                                    String msg=new String(udpBuf.array());
                                    log.info("got message:"+msg);
                                } catch (IOException ex) {
                                    log.warning("exception in recieving message: " + ex.toString());
                                }
                            }
                        }
                    }, "RoShamBoIncremental Listener");
                }
                portListenerThread.start();
            } catch (IOException ex) {
                log.warning("cannot open channel: " + ex.toString());
                return;
            }
        }
        try {
            Socket socket = new Socket(host, port);
            ByteBuffer b = ByteBuffer.wrap(string.getBytes());
            channel.send(b, socket.getRemoteSocketAddress());
        } catch (IOException ex) {
            log.warning(String.format("socket exception for host=%s, port=%d: %s", host, port, ex.toString()));
        }
    }
}
