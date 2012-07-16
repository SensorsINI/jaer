/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author oconnorp
 */
public abstract class Unit<GlobalParams,SpikeType extends Spike> {
    
    public int index;
    public String name;
    
    public float thresh=-1; // Note: this should probably not be here, but it's conveneient for now due to NetReader
    
    /** Fire a current to the unit.
     * 
     * @param current
     * @return 
     */
    public abstract SpikeType fireTo(int time, float current);

    public abstract SpikeType fireFrom(int time);
    
//    public abstract Factory getFactory();
    
    public static abstract class Factory<GlobalParams,UnitType extends Unit>{
        
        GlobalParams glob;
        
        public Factory()
        {   
            glob=newGlobalObject();       
        }
        
        public abstract UnitType make(int unitIndex);
        
        public abstract GlobalParams newGlobalObject();
        
    }
    
    
    public abstract void reset();
    
    
}
