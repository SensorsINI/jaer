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
    
    public Reverse reverse;
    
    
    GlobalParams glob;
        
    Layer preLayer=null;
    Layer postLayer=null;
    
    boolean enable=true;    // Strength of feedforward connections
//    boolean backSend=false;   // Strength of feedback connenctions

    Random rand=new Random();
    
    
    
    public Axons(Layer inLayer, Layer outLayer,GlobalParams glo)
    {
        
        
        // Wire it up!
        preLayer=inLayer;
        postLayer=outLayer;        
        preLayer.addOutputAxon(this);
//        postLayer.addInputAxon(this);
        
        net=inLayer.net;
        
        glob=glo; 
        
        initWeights();
        
    }
    
    public void initWeights()
    {
        w=new float[preLayer.nUnits()][postLayer.nUnits()];
        
    }
    
    public boolean isForwardAxon()
    {
        return true;
    }
    
    

    /** Carry out the effects of the firing */
    public void spikeIn(Spike sp)
    {
        // Fire Ahead!
        if (enable)
        {
//            Spike ev=new Spike(net.time,preUnit,postLayer.ixLayer);
            Spike ev=sp.copyOf();
            
            ev.setAxon(this);
            
            ev.defineDelay(glob.delay);
            
            
//            if (preLayer.ixLayer==1 && postLayer.ixLayer==2)
//                System.out.println("IN");
            
//            ev.layer=lay.ixLayer;
//            System.out.println(sp.hitTime-net.time);    
            
            net.addToInternalQueue(ev);
            
        }
//            sendSpikeToLayer(sp,getWeights(preUnit),postLayer);
    }
    
    
//    public void sendBackwards(Spike sp,int postUnit)
//    {
//        // Fire Ahead!
//        if (backSend) 
//            sendSpikeToLayer(sp,getBackWeights(postUnit),preLayer);
//    }
    
        
    void spikeOut(Spike sp)
    {
//        postLayer.fireTo(sp,w[sp.addr]);
        postLayer.fireTo(sp,getWeights(sp.addr));
        
//        System.out.println("pre: "+preLayer.ixLayer+"\tpost: "+postLayer.ixLayer);
        
        
//        if (preLayer.ixLayer==1 && postLayer.ixLayer==2)
//            System.out.println("OUT");
        
        
//        for (int i=0; i < postLayer.nUnits(); i++)
//        {   
//            
//            Spike ev=postLayer.fireTo(sp, i, w[i]);
//        
//            if (ev==null)
//                continue;
//            else
//            {   
//                int delay=glob.getDelay();
//                if (glob.doRandomJitter)
//                    delay+=rand.nextInt(glob.getRandomJitter());
//                
////                int delay=glob.doRandomJitter?glob.getDelay()+rand.
//                ev.defineDelay(delay);
//                                                
//                ev.layer=lay.ixLayer;
//                
//                net.addToInternalQueue(ev);
////                net.internalBuffer.add(ev);
//            }
//        }
    }
    
        
    

    public float[] getWeights(int unitIndex)
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
        public boolean isEnable() {
            return enable;
        }

        /** Send Forwards? */
        public void setEnable(boolean fwdSend) {
            Axons.this.enable = fwdSend;
        }

//        /** Send Backwards? */
//        public boolean isBackSend() {
//            return backSend;
//        }
//
//        /** Send Backwards? */
//        public void setBackSend(boolean bckSend) {
//            backSend = bckSend;
//        }
        
        
        
    }
    
    
    public Reverse getReverseAxon()
    {
        if (reverse==null)
            reverse=new Reverse(this);
        
        return reverse;
        
    }
    
    public boolean hasReverseAxon()
    {
        return reverse!=null;
    }
    

    /**
    * This acts as a reverse connection to some other axon, such that units that are
    * post-synaptic in the forward axon can fire to their pre-synaptic units through
    * this axon.  This is useful when implementing some kind of symmetrically connected
    * network like a Boltzmann Machine
    * 
    * @author Peter
    */
    public static class Reverse extends Axons {

        Axons forwardAxon;

        public Reverse(Axons fwdAx)
        {
            super(fwdAx.postLayer,fwdAx.preLayer,fwdAx.glob);

            forwardAxon=fwdAx;

        }
        
        
        @Override
        public boolean isForwardAxon()
        {
            return false;
        }

        @Override
        public void initWeights()
        {
        }

        /** Get Reverse connection weights */
        @Override
        public float[] getWeights(int destinationUnit)
        {   // Get reverse connection weights (THERE HAS GOT TO BE A BETTER WAY)
            float[][] forwardWeights=forwardAxon.w;
            float[] reverseWeightsFromDest=new float[forwardWeights.length];
            for (int i=0; i<reverseWeightsFromDest.length; i++)
                reverseWeightsFromDest[i]=forwardAxon.getOutWeight(i,destinationUnit);
            return reverseWeightsFromDest;
        }
        
        @Override
        public String getName()
        {
            return "Reverse "+super.getName();
        }


    }

    
    
    

    @Override
    public String toString()
    {
        return getName()+(enable?" - enabled":" - disabled");
    }


    public String getName()
    {
        return "Axons from "+preLayer.toString()+" to "+postLayer.toString();
    }

    
    
}