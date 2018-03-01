/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.text.DecimalFormat;

/**
 *
 * @author oconnorp
 */
public class UnitLIF<GlobalType extends UnitLIF.Globals> extends Unit<GlobalType>
{
    int ixUnit;
    float vmem=0;
    public Globals glob;
    int tlast=-1;   // Last output spike time
    int clast=0;   // Last input spike time
    float state;
    
    
    public UnitLIF(Globals globs,int index)
    {   glob=globs;
        ixUnit=index;
    }

    /* Send a current to this unit.  If it spikes it will add it to the buffer */
    @Override
    public int fireTo(PSP sp,float current)
    {   updateMem(sp.hitTime,current);
        if (doSpike())
        {   return fireFrom(sp.hitTime);
        }
        else 
            return 0;
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
    public UnitLIF copy() {
        UnitLIF cop=new UnitLIF(glob,ixUnit);
        
        cop.thresh=thresh;
        cop.name=name;
        
        return cop;
    }

    
//    @Override
//    public float getState(int time) {
//        return state*(float)Math.exp((tlast-time)/glob.getTau());
//    }

    @Override
    public StateTracker getStateTracker() {
        return new StateTracker() {

            float tau=glob.tau;
            
            final DecimalFormat myFormatter = new DecimalFormat("#");
            
            @Override
            public void updatestate(Spike sp) {
                lastState=getState(sp.time)+1;
                lastTime=sp.time;
            }

            @Override
            public float getState(int time) {
                if (glob.showMembraneState)
                    return vmem;
                else
                    return lastState*(float)Math.exp((lastTime-time)/tau);
            }

            @Override
            public String getLabel(float min,float max) {
                if (glob.showMembraneState)
                    return "Range: [ "+myFormatter.format(min)+" : "+myFormatter.format(max)+" ]";
                else
                    return "Max: "+myFormatter.format(max*1000000/tau)+"Hz";
            }

            @Override
            public boolean isZeroCentered() {
                return glob.showMembraneState;
            }
        };
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
    
    public static class Factory implements Unit.AbstractFactory<Globals,UnitLIF>
    {
        Globals glob;
        
        public Factory()
        {   glob=newGlobalObject();    
        }
                
        @Override
        public UnitLIF make(int unitIndex) {
            return new UnitLIF(glob,unitIndex);
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
    public int fireFrom(int time) 
    {
        if (glob.resetAfterFire)
                resetmem();
        
//        state=getState(time)+1;
        tlast=time;
        
        return 1;
    }
    
    
    
    
    
               
    public static class Globals extends Unit.Globals
    {   
        // Properties
        public float tref=5000;
        public float tau=100000;
        public boolean resetAfterFire=true;
        public float thresh=1;        
        public boolean useGlobalThresh=false;
        
        public boolean showMembraneState=false;
        
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

        public boolean isShowMembraneState() {
            return showMembraneState;
        }

        public void setShowMembraneState(boolean showMembraneState) {
            this.showMembraneState = showMembraneState;
        }
    }

}