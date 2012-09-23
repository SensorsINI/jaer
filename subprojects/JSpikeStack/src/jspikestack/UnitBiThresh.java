/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.text.DecimalFormat;

/**
 * This leaky integrate and fire unit sends both on and off events to downstream 
 * units.
 * @author oconnorp
 */
public class UnitBiThresh extends UnitLIF<UnitLIF.Globals>
{
    
    public UnitBiThresh(UnitLIF.Globals globs,int index)
    {   super(globs,index);
        // glob=globs;
       // ixUnit=index;
    }

    @Override
    public Unit.StateTracker getStateTracker() {
        return new Unit.StateTracker() {

            float tau=glob.tau;
            
            final DecimalFormat myFormatter = new DecimalFormat("#");
            
            @Override
            public void updatestate(Spike sp) {
                lastState=getState(sp.time)+sp.act;
                lastTime=sp.time;
            }

            @Override
            public float getState(int time) {
                return lastState*(float)Math.exp((lastTime-time)/tau);
            }

            @Override
            public String getLabel(float min,float max) {
                return myFormatter.format(state*1000000/tau)+"Hz";
            }

            @Override
            public boolean isZeroCentered() {
                return false;
            }
        };
    }
    
        
    public static class Factory implements Unit.AbstractFactory<UnitLIF.Globals,UnitBiThresh>
    {
        UnitLIF.Globals glob;
        
        public Factory()
        {   glob=newGlobalObject();    
        }
                
        @Override
        public UnitBiThresh make(int unitIndex) {
            return new UnitBiThresh(glob,unitIndex);
        }

        @Override
        public UnitLIF.Globals newGlobalObject() {
            return new UnitLIF.Globals();
        }

        @Override
        public Controllable getGlobalControls() {
            return glob;
        }

    }
    
    
   /** Send a current to this unit.  If it spikes it will add it to the buffer */
    @Override
    public int fireTo(PSP sp,float current)
    {   updateMem(sp.hitTime,sp.sp.act==1?current:-current);
        if (doSpike())
        {   return fireFrom(sp.hitTime);
        }
        else 
            return 0;
    }

    /** Boolean.. determines whether to spike */
    @Override
    public boolean doSpike(){
        return Math.abs(vmem)>(glob.useGlobalThresh?glob.getThresh():thresh); 
    }
    

    @Override
    public int fireFrom(int time) 
    {
        boolean dir=vmem>0;
        
        if (glob.resetAfterFire)
                resetmem();
        
//        state=getState(time)+1;
        tlast=time;
        
        return dir?1:-1;
    }
        

}