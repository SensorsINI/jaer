/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 *
 * @author oconnorp
 */
public class Layer<AxonType extends Axon> implements Serializable {
    
    public int ixLayer;
    
    public int dimx;     // For display, purposes.
    public int dimy;     // dimx,dimy must multiply to units.length

//    public ArrayList<AxonType> inAxons=new ArrayList();
    public ArrayList<AxonType> outAxons=new ArrayList();
        
    public Unit[] units;
    
    public String name;
    
    public boolean fireInputsTo=false;
    public float inputCurrentStrength=1;
    
    Network net;
    
    Unit.AbstractFactory unitFactory; // Factory class for making units
    
    /** Instantiate Layer with index */
    public Layer(Network network,Unit.AbstractFactory<?,Unit> ufac,int ix)
    {   net=network;
        ixLayer=ix;    
        unitFactory=ufac;
//        glob=glo;
    }
        
    /** Initialize the array of units */
    public void initializeUnits(int nUnits)
    {   // AHAHAH I tricked you Java! 
        units=(Unit[])Array.newInstance(Unit.class, nUnits);
        
        
        setDefaultDims();
        
        for (int i=0;i<nUnits;i++)
            units[i]=makeNewUnit(i);
    }
    
    /** Initialize the array of units */
    public void initializeUnits(int dimensionX,int dimensionY)
    {   dimx=dimensionX;
        dimy=dimensionY;
        initializeUnits(dimx*dimy);
    }
    
    
    
    /** Return a new unit with the given index */
    public Unit makeNewUnit(int index)
    {   //return (UnitType) new SpikeStack.Unit(index);  
        return unitFactory.make(index); // TODO: type safety
    }
    
    public void addOutputAxon(AxonType ax)
    {   outAxons.add(ax);
    }
    
//    public void addInputAxon(AxonType ax)
//    {   inAxons.add(ax);
//    }

    /** Fire Currents to this layer... */
//    public void fireTo(Spike sp,float[] inputCurrents)
//    {
//        for (int i=0; i<units.length; i++)
//        {   //Spike ev=units[i].fireTo(net.time,inputCurrents[i]);
//            //if (ev==null)
//            //    return;
//            fireTo(sp,i,inputCurrents[i]);
//            
//        }
//    }
    
    /** Carry out the effects of the firing */
    public void propagateFrom(Spike sp)
    {
//        sp.layer=ixLayer;
        
        net.addToOutputQueue(sp);
        
        for (Axon ax:outAxons)
            ax.spikeIn(sp);
                
//        for (AxonBundle ax:inAxons)
//            ax.sendBackwards(sp, unitIndex);
        
    }
    
    
    public void updateActions()
    {
        for (Axon ax:outAxons)
            ax.updateActions();
    }
    
    
    /** Return unit at index */
    public Unit getUnit(int index)
    {
        return units[index];
    }
    
    
    public String getUnitName(int index)
    {   return units[index].name;
    }


    public int nUnits()
    {   return units.length;
    }
    
        
    public Spike makeSpike(int time,int addr,int act)
    {
        return new Spike(time,addr,ixLayer,act);
    }
    
    
    /** Force this unit to fire right now... */
    public void fireFrom(int unitIndex)
    {
        int status=units[unitIndex].fireFrom(net.time);
        
        Spike ev=makeSpike(net.time,unitIndex,status);
        
        propagateFrom(ev);
        
    }
    
    /** Fire the unit, and specify a status */
    public void fireFrom(int unitIndex,int status)
    {   
        units[unitIndex].fireFrom(net.time);
        
        Spike ev=makeSpike(net.time,unitIndex,status);
        
        propagateFrom(ev);      
    }
    
    
    public void fireTo(PSP sp,float[] inputCurrents)
    {
        
        for (int i=0; i<units.length; i++)
        {
            fireTo(sp,i,inputCurrents[i]);
                
        }
    }
    
    
    public void fireTo(PSP sp,int[] addresses, float[] inputCurrents)
    {
        for (int i=0; i<addresses.length; i++)
        {
            if (addresses[i]==-1)
                continue;
            
            fireTo(sp,addresses[i],inputCurrents[i]);
            
//            if (status!=0)
//            {
//                propagateFrom(makeSpike(net.time,addresses[i],status));
//            }            
        }
    }
        
    
    
    
    /** Fire current to a given unit in this layer... */
    public int fireTo(PSP sp,int ix,float inputCurrent)
    {        
        int status= units[ix].fireTo(sp,inputCurrent);
        
        if (status!=0)
        {
            propagateFrom(makeSpike(net.time,ix,status));
        }   
        
        return status;                
    }
    
    public int getUnitLocX(int index)
    {   return index/dimy;
    }
    
    public int getUnitLocY(int index)
    {   return index%dimy;        
    }
    
    /** Return the index of the unit at the specified coordinates, or if the 
     * coordinates fall out of range, return -1.  Here we use the same 
     convention as Matlab, first counting down y (columns), then x (rows)*/
    public int loc2index(int x,int y)
    {
        if (x>=dimx || y>=dimy || x<0 || y<0)
            return -1;
        else 
            return y+dimy*x;
        
    }
    

    /** Reset Layer */
    public void reset()
    {   //outBuffer.clear();
        for (Unit u:units)
        {   
            u.reset();                    
        }
        for (Axon ax:outAxons)
        {   ax.reset();
        }
        
    }

    /** Set some a default value for dimx,dimy based on the number of units. */
    public void setDefaultDims()
    {
        int nunits=nUnits();

        short start=(short)Math.ceil(Math.sqrt(nunits));

        dimy=-1;

        // Try finding a factor that divides evenly into the number of units
        // up to a certain ratio.
        for (short i=start; i<nunits-nunits/6; i++)
            if (nunits%i==0)
            {   dimy=i;
                break;
            }

        if (dimy==-1)
            dimy=start;  

        dimx=(short)Math.ceil(nUnits()/(float)dimy);
    }
    
//    public void enableForwardsInputs(boolean st)
//    {   for (int i=0; i<nLayers)
//            ax.enable=st;
//    }
    
//    public void enableBackwards(boolean st)
//    {   for (AxonBundle ax:outAxons)
//            ax.backSend=st;
//    }
        
    public AxonType ax()
    {   return ax(0);
    }
    
    /** Return output axons */
    public AxonType ax(int axonIndex)
    {
        return outAxons.get(axonIndex);
    }
    
    public AxonType axByLayer(int destLayerIndex)
    {
        for (AxonType ax:outAxons)
            if (ax.postLayer.ixLayer==destLayerIndex)
                return ax;
        
        return null;
//        throw new RuntimeException("No Axon connects layer "+ixLayer+" with layer "+destLayerIndex);
    }
        
    public int nAxons()
    {
        return outAxons.size();
    }
    
    public int nForwardAxons()
    {
        int count=0;
        for (Axon ax:outAxons)
        {   if (ax.isForwardAxon())
                count++;
            
        }
        return count;
        
    }
    
    public String getName()
    {
        return name==null?"L"+ixLayer:name;
    }
    
    @Override
    public String toString()
    {
        return getName() +"("+nUnits()+"Units)";
    }
    
    
    public Controller getControls()
    {
        return new Controller();
    }

    void check() {
        if (units==null || (units.length>0 && units[0]==null))
            throw new RuntimeException(getName()+": Units array is not initialized!  See function \"initializeUnits\".");
        
//        if (units.length>0 && units[0]==null)
//            throw new RuntimeException("Units array is not initialized!  See function \"initializeUnits\".");
        
    }
    
    
    public class Controller extends Controllable
    {

        @Override
        public String getName() {
            return Layer.this.getName()+" Controls";
        }
        
        
        public void setInputCurrentStrength(float val)
        {
            inputCurrentStrength=val;
        }
        
        public float getInputCurrentStrength()
        {
            return inputCurrentStrength;
        }

    }
    
//    
//    public class Factory
//    {
//        
//        
//        public Factory(Axon.Factory af,Unit.factory uf)
//        
//        
//        
//    }
//    
//    
    
    
    
    
    
    
    
    
}
