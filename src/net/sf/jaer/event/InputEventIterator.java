/*
 * InputEventIterator.java
 *
 * Created on May 22, 2006, 10:39 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event;

import java.util.Iterator;

/**
 * Used to iterate over an EventPacket to obtain the events.
 * @author tobi
 */
public interface InputEventIterator<T extends BasicEvent> extends Iterator<T>{
    public T next();
    public boolean hasNext();
}
