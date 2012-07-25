/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.Random;

/**
 *
 * @author oconnorp
 */
public class LIFUnit<NetType extends SpikeStack> extends Unit<LIFUnit.Globals,Spike>
{
    int ixUnit;
    float vmem=0;
    public Globals glob;
    int tlast=-1;   // Last spike time
    int clast=0;   // Last update time

    
    public LIFUnit(Globals globs,int index)
    {   glob=globs;
        ixUnit=index;
    }

    /* Send a current to this unit.  If it spikes it will add it to the buffer */
    @Override
    public Spike fireTo(int time,float current)
    {   updateMem(time,current);
        if (doSpike())
        {   return fireFrom(time);
        }
        else 
            return null;
    }

    /** Updates the membrane voltage given an input current */
    public void updateMem(int time,float current){
        if (time>tlast+glob.getTref()) // Refractory period
        {   vmem=(float)(vmem*Math.exp((clast-time)/glob.getTau())+current);
            clast=time;
        }
    }

    /** Boolean.. determines whether to spike */
    public boolean doSpike(){
        return vmem>(glob.useGlobalThresh?glob.getThresh():thresh);                
    }
    
    /** Get the neuron threshold (it can be either local or global */
    public float getThresh()
    {
        return glob.useGlobalThresh?glob.getThresh():thresh;
    }

    /** Reset the unit to a baseline state */
    @Override
    public void reset()
    {   vmem=0;
        tlast=0;
        clast=0;
    }
    
    public void resetmem()
    {   vmem=0;
    }

    /** Copy this unit to create a new one.. keeping the same global params object! */
    @Override
    public LIFUnit copy() {
        LIFUnit cop=new LIFUnit(glob,ixUnit);
        
        cop.thresh=thresh;
        cop.name=name;
        
        return cop;
    }
    
    

//    public static Factory getFactory() {
//        return new Factory<Globals,LIFUnit>()
//        {
//            @Override
//            public LIFUnit make(int unitIndex) {
//                return new LIFUnit(glob,unitIndex);
//            }
//
//            @Override
//            public Globals newGlobalObject() {
//                return new Globals();
//            }
//        };
//    }
    
    public static class Factory implements Unit.AbstractFactory<Globals,LIFUnit>
    {
        Globals glob;
        
        public Factory()
        {   glob=newGlobalObject();    
        }
                
        @Override
        public LIFUnit make(int unitIndex) {
            return new LIFUnit(glob,unitIndex);
        }

        @Override
        public Globals newGlobalObject() {
            return new Globals();
        }

        @Override
        public Controllable getGlobalControls() {
            return glob;
        }

    }
    
    

    @Override
    public Spike fireFrom(int time) 
    {
        if (glob.resetAfterFire)
                resetmem();
        
        tlast=time;
        return new Spike(time,ixUnit);
    }
               
    public static class Globals extends Controllable
    {   
        // Properties
        public float tref=5000;
        public float tau=100000;
        public boolean resetAfterFire=true;
        public float thresh;        
        public boolean useGlobalThresh;
        
        
        /** Get Global Threshold */
        public float getThresh() {
            return thresh;
        }

        /** Set Global Threshold */
        public void setThresh(float thresh) {
            this.thresh = thresh;
        }

        /** Get Membrane time constant (microseconds) */
        public float getTau() {
            return tau;
        }

        /** Set Membrane time constant (microseconds) */
        public void setTau(float tau) {
            this.tau = tau;
        }

        @Override
        public String getName() {
            return "LIF Global Controls";
        }

        /** Get Refractory Period (microseconds) */
        public float getTref() {
            return tref;
        }

         /** Set Refractory Period (microseconds) */
        public void setTref(float tref) {
            this.tref = tref;
        }
    }

}