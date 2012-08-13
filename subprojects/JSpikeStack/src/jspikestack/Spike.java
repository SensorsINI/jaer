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
    
    Axons ax;       // Axon through which spike is transmitted.  If it's an input spike, this stays null
    
    
    protected void setAxon(Axons axe)
    {
        ax=axe;
        
    }
    
        
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
    
    public Spike copyOf()
    {
        Spike sp=new Spike(time,addr,layer);
        return sp;
    }

    public void defineDelay(int delay)
    {   hitTime=time+delay;        
    }
    
    @Override
    public String toString()
    {   if (ax!=null)
            return "Time: "+time+", Addr:"+addr+", Layer:"+layer+", Axon:"+ax.toString();
        else        
            return "Time: "+time+", +Addr:"+addr+", Layer:"+layer;
    }

    @Override
    public int compareTo(Spike o) {
        return this.hitTime-o.hitTime;
    }

}    