/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;
import net.sf.jaer.event.TypedEvent;
/**
 * Synthetic events returned from jaer CUDA processing.
 * @author tobi
 */
public class CUDAEvent extends TypedEvent {
    public CUDAEvent() {
        super();
    }

    @Override
    public int getNumCellTypes() {
        return 5;
    }
}
