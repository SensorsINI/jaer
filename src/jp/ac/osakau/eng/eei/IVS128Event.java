/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.ac.osakau.eng.eei;

import net.sf.jaer.event.TypedEvent;

/**
 * The events output from the IVS128
 * @author tobi
 */
public class IVS128Event extends TypedEvent {

    @Override
    public int getNumCellTypes() {
        return 4;
    }
    
    // TODO add types as enums
}
