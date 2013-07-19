/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ait.dss.sni.jaer.chip.tds;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.eventio.AENetworkInterfaceConstants;
import net.sf.jaer.eventio.AEUnicastOutput;
import ch.unizh.ini.jaer.chip.retina.DVS128;

/**
 * Allows remote control of ARC (AIT) TDS (Traffic Data Sensor) biases and other operating parameters from jAER.
 *
 * @author tobi
 */
public class ARCTDS_DVS128 extends DVS128 {

    static Logger log = Logger.getLogger("AEUnicastOutput");
    static Preferences prefs = Preferences.userNodeForPackage(AEUnicastOutput.class);
    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    InetSocketAddress client = null;
    private String host = prefs.get("AEUnicastOutput.host", "localhost");
    private int port = prefs.getInt("AEUnicastOutput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);

    public ARCTDS_DVS128() {
        try {
            channel = DatagramChannel.open();

            socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
            socket.setTrafficClass(0x10 + 0x08); // low delay
        } catch (IOException e) {
            e.printStackTrace();
        }
        biasgen = new ARCTDS_DVS128Biasgen(this);
    }

    public String getHost() {
        return host;
    }

    boolean checkClient() {
        if (socket.isBound()) {
            return true;
        }
        try {
            client = new InetSocketAddress(host, port);
            return true;
        } catch (Exception se) { // IllegalArgumentException or SecurityException
            log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
            return false;
        }
    }

    /** You need to setHost before this will send events.
    @param host the hostname
     */
    synchronized public void setHost(String host) {
        this.host = host;
        if (checkClient()) {
            prefs.put("AEUnicastOutput.host", host);
        }
    }

    public int getPort() {
        return port;
    }

    /** You set the port to say which port the packet will be sent to.
     *
     * @param port the UDP port number.
     */
    public void setPort(int port) {
        this.port = port;
        prefs.putInt("AEUnicastOutput.port", port);
    }

    /**
     * Encapsulates biases on ARC TDS, which are contolled by UDP datagrams here.
     * @author tobi
     */
    class ARCTDS_DVS128Biasgen extends DVS128.Biasgen {

        public ARCTDS_DVS128Biasgen(Chip chip) {
            super(chip);
        }
    }
}
