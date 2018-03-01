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
public class UnitBinaryThresh extends Unit<UnitBinaryThresh.Globals>{

    boolean state=false;
    float vmem=0;
    public Globals glob=new Globals();
    
    private UnitBinaryThresh(Globals glo,int unitIndex) {
        this.index=unitIndex;
        glob=glo;
    }


    @Override
    public Unit copy() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void reset() {
        this.vmem=0;
        this.state=false;
    }

    @Override
    public int fireTo(PSP sp, float current) {
        
        vmem+=current;
        
        if (vmem>thresh && !state)
            return 1;
        else if (vmem<thresh && state)
            return -1;
        else
            return 0;
            
        
        
//        throw new UnsupportedOperationException("Not Implemented");
//        if (sp.sp.act==1 && !state)
//            return new BinaryTransEvent(sp.hitTime,index,-1,true);
//        else if (!sp.trans && state)
//            return new BinaryTransEvent(sp.hitTime,index,-1,false);
//            
//        else return null;
        
        
    }
    
    
    

    @Override
    public int fireFrom(int time) {
        throw new UnsupportedOperationException("Not Implemented");
//        return new BinaryTransEvent(time,index,-1,false);
    }

//    @Override
    public float getState(int time) {
        return state?1:0;
    }

    @Override
    public StateTracker getStateTracker() {
        return new Unit.StateTracker() {

            @Override
            public void updatestate(Spike sp) {
                this.lastState=sp.act==1?1:0;
            }

            @Override
            public boolean isZeroCentered() {
                return false;
            }

            @Override
            public float getState(int time) {
                return lastState;
            }

            @Override
            public String getLabel(float min, float max) {
                return "";
            }
        };
    }
    
    
    public static class Factory implements Unit.AbstractFactory
    {
        Globals glob=new Globals();

        public Factory()
        {
            glob=newGlobalObject();
        }
      
        
        @Override
        public Unit make(int unitIndex) {
            return new UnitBinaryThresh(glob,unitIndex);
        }

        @Override
        public Globals newGlobalObject() {
            return new Globals();
        }

        @Override
        public Controllable getGlobalControls() {
            return new Globals();
        }
        
    }
    
    public static class Globals extends Unit.Globals
    {
        Random rand=new Random();
        
        @Override
        public String getName() {
            return "Binary Threshold Globals";
        }
        
    }
    
    
}
