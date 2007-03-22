/*
 * OutputEventIterator.java
 *
 * Created on May 22, 2006, 10:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.event;

/**
 * Used to iterate over an EventPacket treated as an output packet.
 * @author tobi
 */
public interface OutputEventIterator<T extends BasicEvent> {
    public T nextOutput();
}
