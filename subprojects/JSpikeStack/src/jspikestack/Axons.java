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
public class Axons<GlobalParams extends Axons.Globals> 
{
    SpikeStack net;
    
    float[][] w;
    
    GlobalParams glob;
        
    Layer preLayer=null;
    Layer postLayer=null;
    
    boolean fwdSend=true;    // Strength of feedforward connections
    boolean backSend=false;   // Strength of feedback connenctions

    Random rand=new Random();
    
    
    
    public Axons(Layer inLayer, Layer outLayer,GlobalParams glo)
    {
        
        
        // Wire it up!
            preLayer=inLayer;
        postLayer=outLayer;        
        preLayer.addOutputAxon(this);
        postLayer.addInputAxon(this);
        
        net=inLayer.net;
        
        glob=glo; 
        
        initWeights();
        
    }
    
    public void initWeights()
    {
        w=new float[preLayer.nUnits()][postLayer.nUnits()];
        
    }
    
    
    
    

    /** Carry out the effects of the firing */
    public void sendForwards(Spike sp,int preUnit)
    {
        // Fire Ahead!
        if (fwdSend)
            sendSpikeToLayer(sp,getForwardWeights(preUnit),postLayer);
    }
    
    
    public void sendBackwards(Spike sp,int postUnit)
    {
        // Fire Ahead!
        if (backSend) 
            sendSpikeToLayer(sp,getBackWeights(postUnit),preLayer);
    }
    
    void sendSpikeToLayer(Spike sp, float[] wgt, Layer lay)
    {
        for (int i=0; i < lay.nUnits(); i++)
        {   
            
            Spike ev=lay.fireTo(sp, i, wgt[i]);
        
            if (ev==null)
                continue;
            else
            {   
                int delay=glob.getDelay();
                if (glob.doRandomJitter)
                    delay+=rand.nextInt(glob.getRandomJitter());
                
//                int delay=glob.doRandomJitter?glob.getDelay()+rand.
                ev.defineDelay(delay);
                                                
                ev.layer=lay.ixLayer;
                
                net.addToInternalQueue(ev);
//                net.internalBuffer.add(ev);
            }
        }
    }
        
    /** Get Reverse connection weights */
    public float[] getBackWeights(int destinationUnit)
    {   // Get reverse connection weights (THERE HAS GOT TO BE A BETTER WAY)
        float[] ww=new float[w.length];
        for (int i=0; i<w.length; i++)
            ww[i]=getOutWeight(i,destinationUnit);
        return ww;
    }

    public float[] getForwardWeights(int unitIndex)
    {
        return w[unitIndex];
    }

    public float getOutWeight(int sourceUnit,int destUnit)
    {
        return w[sourceUnit][destUnit];
    }

    

    
    public void setWout(int sourceIndex,float[] wvec)
    {
        
        
        w[sourceIndex]=wvec;
    }
    
    
//        /** Scale thresholds of all units */
//        public void scaleThresholds(float sc)
//        {   for (SpikeStack.Unit u: units)
//                u.thresh*=sc;            
//        }


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
    
    public void reset()
    {
    }

        
    public static class Factory implements AbstractFactory<Axons>
    {
        public Globals glob;
        public Factory()
        {   glob=new Globals();
        }

        @Override
        public Axons make(Layer inLayer,Layer outLayer)
        {   return new Axons(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
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

        public int delay;
        
        @Override
        public String getName() {
            return "Axon Controller Globals";
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

        public int getDelay() {
            return delay;
        }

        public void setDelay(int delay) {
            this.delay = delay;
        }
    }
    
    
    public interface AbstractFactory<AxonType extends Axons>
    {
        public abstract AxonType make(Layer inLayer,Layer outLayer);
                
        public abstract Controllable getGlobalControls();
        
    }
    
    public Controllable getControls()
    {   return new Controller();
    }
    
    public class Controller extends Controllable
    {

        @Override
        public String getName() {
            return "Axon linking L"+preLayer.ixLayer+" to L"+postLayer.ixLayer;
        }
        
        /** Send Forwards? */
        public boolean isFwdSend() {
            return fwdSend;
        }

        /** Send Forwards? */
        public void setFwdSend(boolean fwdSend) {
            Axons.this.fwdSend = fwdSend;
        }

        /** Send Backwards? */
        public boolean isBackSend() {
            return backSend;
        }

        /** Send Backwards? */
        public void setBackSend(boolean bckSend) {
            backSend = bckSend;
        }
        
        
        
    }
    
    

    @Override
    public String toString()
    {
        return getName();
    }


    public String getName()
    {
        return "Axons from "+preLayer.toString()+" to "+postLayer.toString();
    }

    
    
}