/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public class PSPUnitToUnit extends PSPUnitToLayer{

    final int targetNumber;
    
//    final AxonBundle ax;
    
    public PSPUnitToUnit(Spike spike,Axon axo,int targetNo,int delay)
    {   super(spike,delay,axo);
//        ax=axo;
        targetNumber=targetNo;
    }
    
//    @Override
//    public void affect(SpikeStack net) {
//        ax.spikeOut(this);
//    }
    
    
    
    
    
}
