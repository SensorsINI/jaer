/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dollbrain.coltmpdiff;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;

/**
 * This event can signal an external sync.
 *
 * @author tobi
 */
public class SyncEvent extends PolarityEvent {
    static final int SYNC_TYPE=2;

    public boolean isSyncEvent(){
        return type==SYNC_TYPE;
    }

}
