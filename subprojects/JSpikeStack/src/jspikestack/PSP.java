/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public abstract class PSP {
    
    final Spike sp;
    
    public PSP(Spike spike)
    {
        sp=spike;
    }
    
    
    
    public abstract int getHitTime();
    
}