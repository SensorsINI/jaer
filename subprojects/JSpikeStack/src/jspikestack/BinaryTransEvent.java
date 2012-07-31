/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author oconnorp
 */
public class BinaryTransEvent extends Spike {
    
    boolean trans;
    
    public BinaryTransEvent(int time,int addr,int layer,boolean transition)
    {
        super(time,addr,layer);
        
        trans=transition;
    }
    
    
    
    
}
