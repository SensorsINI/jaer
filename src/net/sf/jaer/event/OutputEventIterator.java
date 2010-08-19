/*
 * OutputEventIterator.java
 *
 * Created on May 22, 2006, 10:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 * Used to iterate over an EventPacket treated as an output packet.
 * @author tobi
 */
public interface OutputEventIterator<T extends BasicEvent> {

    /** Provide the next output event instance, which then should be copied from the input event and then modified according to function of desired operation.
     *
     * @return the next output event.
     */
    public T nextOutput();
}
