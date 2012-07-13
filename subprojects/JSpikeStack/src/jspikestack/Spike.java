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
    double time; // Time in millis
    int layer;
    public Spike(int addri,double timei,int layeri)
    {   addr=addri;
        time=timei;
        layer=layeri;
    }

    @Override
    public String toString()
    {
        return "Time: "+time+", +Addr:"+addr+", Layer:"+layer;
    }
}    