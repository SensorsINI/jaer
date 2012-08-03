/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;

/**
 *
 * @author oconnorp
 */
/* Basic "Spike" class */
public class Spike implements Comparable<Spike>, Serializable
{   public int addr;   
    public int time; // Time at which spike is sent
    public int hitTime;  // Time at which spike effect is felt.
    public int layer;
    
        
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

    @Override
    public int compareTo(Spike o) {
        return this.hitTime-o.hitTime;
    }

}    