/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2010.cardplayer;

import net.sf.jaer.util.networking.UDPMesssgeSender;

/**
 * Sends card statistics to matlab.
 *
 * @author tobi
 */
public class CardStatsMessageSender extends UDPMesssgeSender{

    public final static int PORT=12435;
    public String host="localhost";

    public CardStatsMessageSender() {
        super();
        setPort(PORT);
    }


}
