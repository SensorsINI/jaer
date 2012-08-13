/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 *
 * @author oconnorp
 */
public class Layer<AxonType extends Axons> {
    
    public int ixLayer;
    
    public short dimx;     // For display, purposes.
    public short dimy;     // dimx,dimy must multiply to units.length

//    public ArrayList<AxonType> inAxons=new ArrayList();
    public ArrayList<AxonType> outAxons=new ArrayList();
        
    public Unit[] units;
    
    public String name;
    
    public float inputCurrentStrength=1;
    
    SpikeStack net;
    
    Unit.AbstractFactory unitFactory; // Factory class for making units
    
    /** Instantiate Layer with index */
    public Layer(SpikeStack network,Unit.AbstractFactory<?,Unit> ufac,int ix)
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
        sp.layer=ixLayer;
        
        net.addToOutputQueue(sp);
        
        for (Axons ax:outAxons)
            ax.spikeIn(sp);
                
//        for (Axons ax:inAxons)
//            ax.sendBackwards(sp, unitIndex);
        
    }
    
    
    public void updateActions()
    {
        for (Axons ax:outAxons)
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
    
    
    /** Fires to an input unit, but weights is by the inputCurrentStrength for this layer */
    public void fireInputTo(Spike sp)
    {   Spike spOut=fireTo(sp,sp.addr,inputCurrentStrength);
        
        if (spOut!=null)
        {
            propagateFrom(sp);
//            net.addToInternalQueue(spOut);
//            net.internalBuffer.add(spOut);
        }
    }
    
    /** Force this unit to fire right now... */
    public void fireFrom(int unitIndex)
    {
        Spike ev=units[unitIndex].fireFrom(net.time);
//        ev.layer=ixLayer;
        
//        for (Axons ax:outAxons)
//            ax.spikeIn(ev, unitIndex);
                
//        for (Axons ax:inAxons)
//            ax.sendBackwards(ev, unitIndex);
        
        
//        ev.defineDelay(net.delay);
//        net.addToInternalQueue(ev);
//        net.outputQueue.add(ev);
        
        propagateFrom(ev);
        
    }
    
    public void fireTo(Spike sp,float[] inputCurrents)
    {
        
        for (int i=0; i<units.length; i++)
        {
            Spike spout=fireTo(sp,i,inputCurrents[i]);
            
            if (spout!=null)
            {
                
                propagateFrom(spout);
            }            
        }
    }
    
    
    public void fireTo(Spike sp,int[] addresses, float[] inputCurrents)
    {
        for (int i=0; i<addresses.length; i++)
        {
            if (addresses[i]==-1)
                continue;
            
            Spike spout=fireTo(sp,addresses[i],inputCurrents[i]);
            
            if (spout!=null)
            {
                propagateFrom(spout);
            }            
        }
    }
        
    
    
    
    /** Fire current to a given unit in this layer... */
    public Spike fireTo(Spike sp,int ix,float inputCurrent)
    {        
        return units[ix].fireTo(sp,inputCurrent);
                
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
        for (Axons ax:outAxons)
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
//    {   for (Axons ax:outAxons)
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
        for (Axons ax:outAxons)
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
