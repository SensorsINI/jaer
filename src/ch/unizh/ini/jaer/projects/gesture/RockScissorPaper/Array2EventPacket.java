/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

import java.util.ArrayList;
import java.util.Iterator;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Eun Yeong Ahn
 */
public class Array2EventPacket {
   EventPacket Convert(ArrayList<BasicEvent> array){
        EventPacket newEventPacket = new EventPacket();
        BasicEvent[] tmp = new BasicEvent[array.size()];
        Iterator iter = array.iterator();
        int i = 0;
        while(iter.hasNext()){
            tmp[i++] = (BasicEvent) iter.next();
        }

        newEventPacket.setElementData(tmp);
        newEventPacket.setSize(i);

        return newEventPacket;
    }
}
