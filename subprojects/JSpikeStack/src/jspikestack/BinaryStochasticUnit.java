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
public class BinaryStochasticUnit extends Unit<BinaryStochasticUnit.Globals>{

    boolean state=false;
    float vmem=0;
    public Globals glob=new Globals();
    
    private BinaryStochasticUnit(Globals glo,int unitIndex) {
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
        throw new UnsupportedOperationException("Not Implemented");
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

    @Override
    public float getState(int time) {
        return state?1:0;
    }

    @Override
    public StateTracker getStateTracker() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    public class Factory implements Unit.AbstractFactory
    {
        Globals glob=new Globals();

        public Factory()
        {
            glob=newGlobalObject();
        }
      
        
        @Override
        public Unit make(int unitIndex) {
            return new BinaryStochasticUnit(glob,unitIndex);
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
    
    public class Globals extends Controllable
    {
        Random rand=new Random();
        
        @Override
        public String getName() {
            return "Stochastic Binary Globals";
        }
        
    }
    
    
}
