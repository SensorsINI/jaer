/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.tracking;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;

/**
 * tracker that sends cluster info to remote host about tracked clusters
 *
 * @author asim
 */
@Description("tracker that sends cluster info to remote host about tracked clusters")
public class RectangularClusterTrackerWithUDPClusterOutput extends RectangularClusterTracker {

    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    protected String host = "localhost";
    protected int port = getInt("port", 7659);
   InetSocketAddress client = null;
 
    public RectangularClusterTrackerWithUDPClusterOutput(AEChip chip) {
        super(chip);
        String s="  remote host";
        setPropertyTooltip(s, "port", "port to send to on remote host");
        setPropertyTooltip(s, "host", "host to send to");
        try {
            channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        } catch (IOException ex) {
            log.warning("cannot open channel "+ex.toString());
        }
  }

    @Override
    protected void updateClusterList(int t) {
        super.updateClusterList(t);
        if(channel==null){
            log.warning("no channel to send on");
            return;
        }
        checkClient();
        for (Cluster c : getVisibleClusters()) {
            ByteBuffer b=makeDatagram(c);
            try {
                channel.send(b,client);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }

    }
    

    ByteBuffer makeDatagram(Cluster c){
        ByteBuffer b=ByteBuffer.allocate(100);
        b.order(ByteOrder.LITTLE_ENDIAN); // check this for actual reciever
        Point2D.Float p=c.getLocation();
        b.putInt((int)Math.round(p.x));
        b.putInt((int)Math.round(p.y));
        b.putLong(System.currentTimeMillis());
        return b;
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
        this.host = host;
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
    
  /** returns true if socket exists and is bound */
    private boolean checkClient (){
        if ( socket == null ){
            return false;
        }

        try{
            if ( socket.isBound() ){
                return true;
            }
            client = new InetSocketAddress(host,port);
            return true;
        } catch ( Exception se ){ // IllegalArgumentException or SecurityException
            log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
            return false;
        }
    }


}
