/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author oconnorp
 */
public class LIFUnit<NetType extends SpikeStack> extends Unit<LIFUnit.Globals,Spike>
{
    int ixUnit; 

    float vmem=0;
//    float thresh;

    Globals glob;
    
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
        if (time>tlast+glob.tref) // Refractory period
        {   vmem=(float)(vmem*Math.exp((clast-time)/glob.tau)+current);
            clast=time;
        }
    }

    /** Boolean.. determines whether to spike */
    public boolean doSpike(){
        return vmem>(thresh<0?glob.thresh:thresh);                
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
    
    public static class Factory extends Unit.Factory<Globals,LIFUnit>
    {
        
        public Factory()
        {   super();            
        }
                
        @Override
        public LIFUnit make(int unitIndex) {
            return new LIFUnit(glob,unitIndex);
        }

        @Override
        public Globals newGlobalObject() {
            return new Globals();
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
               
    public static class Globals
    {   float tref=5000;
        float tau=100000;
        boolean resetAfterFire=true;
        float thresh;
        
//        int delay;
    }

}