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
     * If you want to add a new event to the output packet, call this method. <code>nextOutput</code> will return a 
     * reference to a previously allocated event object (which might have been used
     * earlier). You can then modify this event as you like, e.g. by copying the fields from another event, using  <code>copyFrom</code>.
     *
     * @return the next output event.
     * @see net.sf.jaer.event.BasicEvent#copyFrom(net.sf.jaer.event.BasicEvent) 
     */
    public T nextOutput();
    
    /** Writes the reference to the event to the next output slot. The iterator is advanced.
     * The reference of <code>event</code> is written the output packet.
     * If event's fields are modified, it will be modified in the output packet as well.
     * 
     * @param event the event to write.
     * @see net.sf.jaer.event.BasicEvent#copyFrom(net.sf.jaer.event.BasicEvent)  for copying event fields.
     */
    public void writeToNextOutput(T event);
    
}
