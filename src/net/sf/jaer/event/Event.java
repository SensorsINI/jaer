/*
 * Event.java
 *
 * Created on June 24, 2007, 1:42 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 * Interface for any event - currently unused.
 * @author tobi
 */
public interface Event<T> {
    public void copyFrom(Event<? extends Event> event);
    public int getTimestamp();
    public short getX();
    public short getY();
}
