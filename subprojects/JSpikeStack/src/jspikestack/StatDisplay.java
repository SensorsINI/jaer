/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public abstract class StatDisplay {
    
    SpikeStack net;
    String name;
    
    public StatDisplay(SpikeStack network,String statName)
    {
        net=network;
        name=statName;
    }
    
    public abstract float compute();
    
    public void display()
    {
        System.out.println(name+": "+compute());
        
    }
    
    
}
