/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.net.*;
import java.util.logging.*;
import java.awt.geom.*;

/**
 *
 * @author braendch
 */
public class OSCInterface {

    public int DEFAULT_PORT = 9997;

    public OSCutils utils ;

    public OSCInterface(){
        InetAddress address = null;
        try {
            address = InetAddress.getByName("localhost");
        } catch (UnknownHostException ex) {
            Logger.getLogger(OSCInterface.class.getName()).log(Level.SEVERE, null, ex);
        }
        utils = new OSCutils(address, DEFAULT_PORT);
    }

    public void sendXPosition(float position){
        Object[] args = {new Float(position)};
        utils.sendMessage("/jAER/position/x", args);
    }
    public void sendYPosition(float position){
        Object[] args = {new Float(position)};
        utils.sendMessage("/jAER/position/y", args);
    }

    public void sendSpeed(float speed){
        Object[] args = {new Float(speed)};
        utils.sendMessage("/jAER/speed", args);
    }

    public void close(){
        utils.socket.close();
    }

}
