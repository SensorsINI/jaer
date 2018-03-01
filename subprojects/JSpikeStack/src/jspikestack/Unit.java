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
public abstract class Unit<GlobalParams> implements Serializable {
    
    public int index;
    public String name;
    
    public float thresh=1; // Note: this should probably not be here, but it's conveneient for now due to NetReader
    
    /** Fire a current to the unit.
     * 
     * @param current
     * @return 
     */
    public abstract int fireTo(PSP transmisson, float current);

    public abstract int fireFrom(int time);
               
    
    
//    public abstract Factory getFactory();
    
    /** Make a copy of this unit */
    public abstract Unit copy();
    
    public static interface AbstractFactory<GlobalParams,UnitType extends Unit>  extends Serializable{
        
//        public GlobalParams glob;
//        
//        public AbstractFactory()
//        {   
//            glob=newGlobalObject();       
//        }
        
        
        public abstract UnitType make(int unitIndex);
        
        public abstract GlobalParams newGlobalObject();
        
        public abstract Controllable getGlobalControls();
        
    }
    
//    public abstract float getState(int time);
    
    public abstract StateTracker getStateTracker();
    
    public abstract static class StateTracker
    {
        int lastTime;
        float lastState;
        
        
        
        /** Update the state when a spike is fired */
        public abstract void updatestate(Spike sp);
        
        
        public abstract boolean isZeroCentered();
        
        /** Get the state */
        public abstract float getState(int time);
        
        public abstract String getLabel(float min,float max);
        
        public void reset()
        {
            lastTime=0;
            lastState=0;
        }
    }
    
    public abstract static class Globals extends Controllable
    {        
        @Override
        public String getName()
        {   return "Unit Controls";
        }
        
        
    };
    
    
    public abstract void reset();
    
//    public abstract float stateUpdate(float prevState,int prevTime,Spike sp);
//    
//    public abstract float stateUpdate(float prevState,int prevTime,int toTime);
    
}
