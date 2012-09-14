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

    
    final Axon ax;
    
    public PSPUnitToLayer(Spike spike,int delay, Axon axi)
    {   super(spike,delay);
        ax=axi;
    }
    
    
    @Override
    public void affect(Network net) {
        
        ax.spikeOut(this);
                
    }

    
}
