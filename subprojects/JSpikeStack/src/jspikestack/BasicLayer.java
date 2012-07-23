/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Random;

/**
 *
 * @author oconnorp
 */
public class BasicLayer<NetType extends SpikeStack,LayerType extends BasicLayer,GlobalParams extends BasicLayer.Globals> 
{
    NetType net;
    
    float[][] wOut;
    float[][] wLat;
    
    GlobalParams glob;
    
    String name="";
    
    Unit.Factory unitFactory; // Factory class for making units

    int ixLayer;
    public Unit[] units;
    //EvtStack g;     // Pointer to the 'global' stack
    ArrayList<LayerType> Lin=new ArrayList();
    LayerType Lout=null;

    public short dimx;     // For display, purposes.
    public short dimy;     // dimx,dimy must multiply to units.length

    float fwdSend=1;    // Strength of feedforward connections
    float backSend=0;   // Strength of feedback connenctions
    float latSend=0;    // Strength of lateral connections

    public float inputCurrentStrength=1;
    
    
    Random rand=new Random();
    
    transient public MultiReaderQueue<Spike> outBuffer=new MultiReaderQueue();

    /** Instantiate Layer with index */
    public BasicLayer(NetType network,Unit.Factory<?,Unit> ufac,int ix,GlobalParams glo)
    {   net=network;
        ixLayer=ix;    
        unitFactory=ufac;
        glob=glo;
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
    
    /** Return unit at index */
    public Unit getUnit(int index)
    {
        return units[index];
    }
    
    /** Fires to an input unit, but weights is by the inputCurrentStrength for this layer */
    public void fireInputTo(int unitIndex)
    {
        fireTo(unitIndex,inputCurrentStrength);
        
    }
    
    
    /** Fire current to a given unit in this layer... */
    public void fireTo(int unitIndex,float inputCurrent)
    {
        Spike ev=units[unitIndex].fireTo(net.time,inputCurrent);
                
        if (ev!=null)
        {   ev.layer=ixLayer;
        if (glob.doRandomJitter)
            ev.defineDelay(net.delay+rand.nextInt(1000));
        else
            ev.defineDelay(net.delay);
        
//            ev.defineDelay(rand.nextInt(net.delay));
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

    /** Called after network construction */
    public void init()
    {
        
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
        
        for (int i=0;i<nUnits;i++)
            units[i]=makeNewUnit(i);
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

//    public Spike getOutputEvent(int outputBufferLocation)
//    {   return outBuffer.get(outputBufferLocation);
//    }
    
//    public Spike getOutputEvent(Object ob)
//    {   return outBuffer.read(ob);
//    }

    
    public static AbstractFactory getFactory()
    {
        return new Factory();
    }
    
    /** Actions to perform after input spike is processed */
    public void updateActions()
    {
    }

        
    public static class Factory implements AbstractFactory<BasicLayer>
    {
//        Unit.Factory unitFactory;
//        NetworkType net;
        public Globals glob;
//
        public Factory()
        {
            glob=new Globals();
        }

        @Override
        public <NetType extends SpikeStack,UnitType extends Unit> BasicLayer make(NetType net,Unit.Factory<?,UnitType> unitFactory,int layerIndex)
        {
            return new BasicLayer(net,unitFactory,layerIndex,glob); // TODO: BAAAD.  I'm confused
        }

        @Override
        public Controllable getGlobalControls() {
            return new Globals();
        }

    }
    
    
    public static class Globals extends Controllable
    {   
        public boolean doRandomJitter=true;    
        
        /** Random spike time jitter, us.  Useful for breaking ties */
        public int randomJitter=100; 

        
        @Override
        public String getName() {
            return "Layer Controller Globals";
        }

        /** Add Random spike time Jitter */
        public boolean isDoRandomJitter() {
            return doRandomJitter;
        }

        /** Add Random spike time Jitter */
        public void setDoRandomJitter(boolean doRandomJitter) {
            this.doRandomJitter = doRandomJitter;
        }

        /** Random spike time Jitter (us) */
        public int getRandomJitter() {
            return randomJitter;
        }

        /** Random spike time Jitter (us) */
        public void setRandomJitter(int randomJitter) {
            this.randomJitter = randomJitter;
        }
    }
    
    
    public interface AbstractFactory<LayerType extends BasicLayer>
    {
        public abstract <NetType extends SpikeStack,UnitType extends Unit> LayerType make(NetType net,Unit.Factory<?,UnitType> unitFactory,int layerIndex);
        
        
        public abstract Controllable getGlobalControls();
        
    }
    
    public Controllable getControls()
    {   return new Controller();
    }
    
    public class Controller extends Controllable
    {

        @Override
        public String getName() {
            return "Layer "+ixLayer;
        }
        

        /** Get the weight of the external-input currents */
        public float getInputCurrentStrength() {
            return inputCurrentStrength;
        }

        /** Set the weight of the external-input currents */
        public void setInputCurrentStrength(float inputCurrentStreng) {
            inputCurrentStrength = inputCurrentStreng;
        }
        
        /** Send Forwards? */
        public boolean isFwdSend() {
            return fwdSend>0;
        }

        /** Send Forwards? */
        public void setFwdSend(boolean fwdSend) {
            BasicLayer.this.fwdSend = fwdSend?1:0;
        }

        /** Send Backwards? */
        public boolean isBackSend() {
            return backSend>0;
        }

        /** Send Backwards? */
        public void setBackSend(boolean bckSend) {
            backSend = bckSend?1:0;
        }

        /** Send Laterally? */
        public boolean isLatSend() {
            return  latSend>0;
        }

        /** Send Laterally? */
        public void setLatSend(boolean latSend) {
            BasicLayer.this.latSend = latSend?1:0;
        }
        
        
        
        
    }
    
    
    
}