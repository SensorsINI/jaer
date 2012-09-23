/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.text.DecimalFormat;
import jspikestack.UnitLIF.Globals;

/**
 * This type of unit contains two LIF units in parallel.  One response to "ON"
 * events, the other to "OFF" events.
 * @author oconnorp
 */
public class UnitOnOff extends Unit<UnitLIF.Globals>
{
    UnitLIF.Globals glob;
    UnitLIF onUnit;
    UnitLIF offUnit;

    
    public UnitOnOff(UnitLIF.Globals glo,UnitLIF on,UnitLIF off)
    {
        glob=glo;
        onUnit=on;
        offUnit=off;
    }
    
    @Override
    /** Fire a current to the on/off neurons.  Off neurons will be excited by 
     * negative currents, on neurons by positive currents.
     */
    public int fireTo(PSP transmisson, float current) {
        
        int status;
        
        if (transmisson.sp.act==1) // Route on events to on units
            status=onUnit.fireTo(transmisson, current);
        else
            status=-offUnit.fireTo(transmisson,current);
        
        
//        if(offStatus!=0)
//            System.out.print("Stgf");
            
        return status;
//        return (current>0?onStatus:offStatus); // If Retu
    }

    @Override
    public int fireFrom(int time) {
        
        if (glob.resetAfterFire)
        {   onUnit.resetmem();
            offUnit.resetmem();
        }
        onUnit.tlast=time;
        offUnit.tlast=time;
        
        return 1;        
    }

    @Override
    public Unit copy() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public StateTracker getStateTracker() {
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
            public String getLabel(float min,float state) {
                return myFormatter.format(state*1000000/tau)+"Hz";
            }

            @Override
            public boolean isZeroCentered() {
                return true;
            }
        };
    }

    @Override
    public void reset() {
        onUnit.reset();
        offUnit.reset();
    }
    
    public static class Factory implements Unit.AbstractFactory<UnitLIF.Globals,UnitOnOff>
    {
        UnitLIF.Globals glob;
        
        public Factory()
        {   glob=new UnitLIF.Globals();   
        }

        @Override
        public UnitOnOff make(int unitIndex) {
            UnitLIF onUnit=new UnitLIF(glob,unitIndex);
            UnitLIF offUnit=new UnitLIF(glob,unitIndex);            
            return new UnitOnOff(glob,onUnit,offUnit);
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

}