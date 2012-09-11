/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public abstract class PSP implements Comparable<PSP>
{
    
    public final Spike sp;
    public final int hitTime;
    
    public PSP(Spike spike,int delay)
    {
        sp=spike;
        
        hitTime=delay+sp.time;
        
    }
    
    public abstract void affect(SpikeStack net);
    
    @Override
    public int compareTo(PSP other)
    {   return hitTime-other.hitTime;
    }
    
    @Override
    public String toString()
    {
        return "PSP with hitTime="+hitTime+" Spike:{"+sp.toString()+"}";
    }
    
}