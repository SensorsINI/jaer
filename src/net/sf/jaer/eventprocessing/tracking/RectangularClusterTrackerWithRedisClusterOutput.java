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
import redis.clients.jedis.Jedis; 

/**
 * tracker that sends cluster info to remote host about tracked clusters
 *
 * @author Damien JOUBERT -> fork RectangularClusterTrackerWithUDPClusterOutput
 */
@Description("tracker that sends cluster info to remote host about tracked clusters")
public class RectangularClusterTrackerWithRedisClusterOutput extends RectangularClusterTracker {

    protected String host = "localhost"; //10.162.177.1";
    protected int port = getInt("port", 6379);
    Jedis jedis;
    long jedisTime;
 
    public RectangularClusterTrackerWithRedisClusterOutput(AEChip chip) {
        super(chip);
        jedis = new Jedis(host); 
        System.out.println("Connection to server sucessfully"); 
        jedisTime = System.currentTimeMillis();
  }

    @Override
    protected void updateClusterList(int t) {
        super.updateClusterList(t);
        if(System.currentTimeMillis() - jedisTime > 10){
            for (Cluster c : getVisibleClusters()) {
                sendData(c);
            }
            jedisTime = System.currentTimeMillis();
        }

    }
    

    void sendData(Cluster c){
        Point2D.Float p=c.getLocation();
        Point2D.Float v=c.getVelocityPPS();
        jedis.set("pos", Float.toString(p.x) + ';' + Float.toString(p.y));
        jedis.set("vel", Float.toString(v.x) + ';' + Float.toString(v.y));
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



}
