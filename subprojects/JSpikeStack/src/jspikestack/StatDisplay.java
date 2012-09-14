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
    
    Network net;
    String name;
    
    public StatDisplay(Network network,String statName)
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
