/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 * This PSP has no effect and does nothing, but it allows the network to advance
 * the specified time.  Effectively, it's a guarantee that no events will come 
 * before this time so the network does not have to keep waiting.
 * @author Peter
 */
public final class PSPNull extends PSP {

    public PSPNull(int time)
    {   super(time);
    }
    
    @Override
    public void affect(Network net) {
    }
    
    
    
}
