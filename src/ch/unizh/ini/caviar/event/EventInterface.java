/*
 * EventInterface.java
 *
 * Created on May 28, 2006, 7:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.event;

/**
 * The basic interface for an event
 * @author tobi
 */
public interface EventInterface<T extends BasicEvent> {
    public int getNumCellTypes();
    public int getType();
    public void copyFrom(BasicEvent event);
}
