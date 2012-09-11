/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public class PSPUnitToLayer extends PSP {

    
    final AxonBundle ax;
    
    public PSPUnitToLayer(Spike spike,int delay, AxonBundle axi)
    {   super(spike,delay);
        ax=axi;
    }
    
    
    @Override
    public void affect(SpikeStack net) {
        
        ax.spikeOut(this);
                
    }

    
}
