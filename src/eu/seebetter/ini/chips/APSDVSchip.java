/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Christian
 */
abstract public class APSDVSchip extends AETemporalConstastRetina{
    
    public void takeSnapshot() {
        log.warning("takeSnapshot() not yet implemented");
    }
    
    public int getMaxADC(){
        log.warning("Max ADC is 0. Override the method getMaxADC");
        return 0;
    }
    
}
