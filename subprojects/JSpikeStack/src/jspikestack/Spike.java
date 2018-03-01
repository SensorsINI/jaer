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
public final class Spike implements Serializable
{   public final int addr;   
    public final int time; // Time at which spike is sent
//    public final int hitTime;  // Time at which spike effect is felt.
    public final int layer;
        
    public final int act;
    
    public Spike(int timei,int addri,int layeri)
    {   this(timei,addri,layeri,1);
    }
    
    public Spike(int timei,int addri,int layeri,int acti)
    {   time=timei;
        addr=addri;
        layer=layeri;
        act=acti;
    }
    
    public Spike copyOf()
    {
        Spike sp=new Spike(time,addr,layer,act);
        return sp;
    }

//    public void defineDelay(int delay)
//    {   hitTime=time+delay;        
//    }
    
    @Override
    public String toString()
    {   //if (ax!=null)
        //    return "Time: "+time+", Addr:"+addr+", Layer:"+layer+", Axon:"+ax.toString();
        //else        
            return "Time: "+time+", +Addr:"+addr+", Layer:"+layer;
    }
}    