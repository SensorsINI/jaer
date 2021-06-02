/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.event;


/**
 * This event can signal an external special.
 *
 * @author tobi
 */
public class SyncEvent extends PolarityEvent {

    /** The type of this SyncEvent; compare with type=0/1 for OFF and ON events */
    public static int SYNC_TYPE=2;

    public boolean isSpecial(){
        return type==SYNC_TYPE;
    }

}
