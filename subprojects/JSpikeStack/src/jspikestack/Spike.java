/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author oconnorp
 */
/* Basic "Spike" class */
public class Spike
{   int addr;   
    int time; // Time at which spike is sent
    int hitTime;  // Time at which spike effect is felt.
    int layer;
    
        
    public Spike(int timei)
    {   time=timei;
        hitTime=timei;
    }
    
    public Spike(int timei,int addri)
    {   this(timei);
        addr=addri;
    }
    
    public Spike(int timei,int addri,int layeri)
    {   this(timei,addri);
        layer=layeri;
    }

    public void defineDelay(int delay)
    {   hitTime=time+delay;        
    }
    
    @Override
    public String toString()
    {
        return "Time: "+time+", +Addr:"+addr+", Layer:"+layer;
    }
}    