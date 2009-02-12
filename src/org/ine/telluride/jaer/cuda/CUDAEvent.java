/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;
import net.sf.jaer.event.PolarityEvent;
/**
 * Synthetic events returned from jaer CUDA processing.
 * @author tobi
 */
public class CUDAEvent extends PolarityEvent {
    public CUDAEvent() {
        super();
    }

    /** Overrides PolarityEvent's type to return underlying byte value instead of binary polarity
     *
     * @return cell type
     */
    @Override public int getType(){
        return type;
    }

    @Override
    public int getNumCellTypes() {
        return 5;
    }
}
