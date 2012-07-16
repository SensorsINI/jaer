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
public class BasicLayer<NetType extends SpikeStack,LayerType extends BasicLayer> 
{
    NetType net;

    float[][] wOut;
    float[][] wLat;

    
    Unit.Factory unitFactory; // Factory class for making units

    int ixLayer;
    Unit[] units;
    //EvtStack g;     // Pointer to the 'global' stack
    ArrayList<LayerType> Lin=new ArrayList();
    LayerType Lout=null;

    public short dimx;     // For display, purposes.
    public short dimy;     // dimx,dimy must multiply to units.length

    float fwdSend=1;    // Strength of feedforward connections
    float backSend=0;   // Strength of feedback connenctions
    float latSend=0;    // Strength of lateral connections

    transient public ArrayList<Spike> outBuffer=new ArrayList();

    /** Instantiate Layer with index */
    public BasicLayer(NetType network,Unit.Factory<?,Unit> ufac,int ix)
    {   net=network;
        ixLayer=ix;    
        unitFactory=ufac;
    }

    /** Fire Currents to this layer... */
    public void fireTo(float[] inputCurrents)
    {
        for (int i=0; i<units.length; i++)
        {   //Spike ev=units[i].fireTo(net.time,inputCurrents[i]);
            //if (ev==null)
            //    return;
            fireTo(i,inputCurrents[i]);
            
        }
    }
    
    /** Fire Currents to this layer... */
    public void fireTo(int unitIndex,float inputCurrent)
    {
        Spike ev=units[unitIndex].fireTo(net.time,inputCurrent);
                
        if (ev!=null)
        {   ev.layer=ixLayer;
            ev.defineDelay(net.delay);
            net.addToInternalQueue(ev);
            outBuffer.add(ev);
        }
    }
    
    /** Force this unit to fire right now... */
    public void fireFrom(int unitIndex)
    {
        Spike ev=units[unitIndex].fireFrom(net.time);
//        ev.layer=ixLayer;
//        ev.defineDelay(net.delay);
//        net.addToInternalQueue(ev);
        outBuffer.add(ev);
        
        propagateFrom(unitIndex);
        
        
    }
    
    

    /** Reset Layer */
    public void reset()
    {   outBuffer.clear();
        for (Unit u:units)
        {   
            u.reset();                    
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


    /** Carry out the effects of the firing */
    public void propagateFrom(int unitIndex)
    {
        // Fire Ahead!
        if (Lout!=null && fwdSend!=0) 
            if (fwdSend==1)
                Lout.fireTo(getForwardWeights(unitIndex));
            else
                throw new UnsupportedOperationException("Scaling of fwd connections not yet supported");

        // Fire Behind!
        for (LayerType l:Lin)
        {   if (l.backSend==1)
                l.fireTo(l.getBackWeights(unitIndex));
            else if (l.backSend!=0)
                throw new UnsupportedOperationException("Scaling of reverse connections not yet supported");
        }

        // Fire Sideways!
        if (wLat[unitIndex]!=null && latSend!=0)
        {   
            if (latSend==1)
                fireTo(getLateralWeights(unitIndex)); 
            else
                throw new UnsupportedOperationException("Scaling of lateral connections not yet supported");

        }               
    }


    /** Get Reverse connection weights */
    public float[] getBackWeights(int destinationUnit)
    {   // Get reverse connection weights (THERE HAS GOT TO BE A BETTER WAY)
        float[] w=new float[units.length];
        for (int i=0; i<units.length; i++)
            w[i]=getOutWeight(i,destinationUnit);
        return w;
    }

    public float[] getForwardWeights(int unitIndex)
    {
        return wOut[unitIndex];
    }

    public float[] getLateralWeights(int unitIndex)
    {
        return wLat[unitIndex];
    }

    public float getOutWeight(int sourceUnit,int destUnit)
    {
        return wOut[sourceUnit][destUnit];
    }


    /** Initialize the array of units */
    public void initializeUnits(int nUnits)
    {   // AHAHAH I tricked you Java! 
        units=(Unit[])Array.newInstance(Unit.class, nUnits);
        
        wOut=new float[nUnits][];
        wLat=new float[nUnits][];
        
        setDefaultDims();
    }

    /** Return a new unit with the given index */
    public Unit makeNewUnit(int index)
    {   //return (UnitType) new SpikeStack.Unit(index);  
        return unitFactory.make(index); // TODO: type safety
    }

    public void setWout(int sourceIndex,float[] wvec)
    {
        
        
        wOut[sourceIndex]=wvec;
    }
    
    
//        /** Scale thresholds of all units */
//        public void scaleThresholds(float sc)
//        {   for (SpikeStack.Unit u: units)
//                u.thresh*=sc;            
//        }

    public String getUnitName(int index)
    {   return units[index].name;
    }


    public int nUnits()
    {   return units.length;
    }

    public Spike getOutputEvent(int outputBufferLocation)
    {   return outBuffer.get(outputBufferLocation);
    }

    
    public static Factory getFactory()
    {
        return new BasicFactory();
    }
    
    /** Actions to perform after input spike is processed */
    public void updateActions()
    {
    }

        
    public static class BasicFactory extends Factory<BasicLayer>
    {
//        Unit.Factory unitFactory;
//        NetworkType net;
//
//        public Factory(NetworkType nt,Unit.Factory uf)
//        {
//            unitFactory=uf;
//            net=nt;
//        }

        @Override
        public <NetType extends SpikeStack,UnitType extends Unit> BasicLayer make(NetType net,Unit.Factory<?,UnitType> unitFactory,int layerIndex)
        {
            return new BasicLayer(net,unitFactory,layerIndex); // TODO: BAAAD.  I'm confused
        }

    }
    
    
    public abstract static class Factory<LayerType extends BasicLayer>
    {
        public abstract <NetType extends SpikeStack,UnitType extends Unit> LayerType make(NetType net,Unit.Factory<?,UnitType> unitFactory,int layerIndex);
        
        
    }
    
    
}