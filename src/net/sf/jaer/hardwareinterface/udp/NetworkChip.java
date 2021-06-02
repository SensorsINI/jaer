/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp;

import java.net.InetSocketAddress;

/**
 *This interface builds a collections for all classes that need a network hardware interface.
 *
 * @author braendch
 */
public interface NetworkChip {

    public void setAddress(InetSocketAddress address);

    public InetSocketAddress getAddress();

}
