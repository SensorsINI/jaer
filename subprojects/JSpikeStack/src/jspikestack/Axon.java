/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Random;

/**
 *
 * @author oconnorp
 */
public class Axon<GlobalParams extends Axon.Globals,PSPtype extends PSP> implements Serializable
{
    Network net;
    
    float[][] w;
    
    public Reverse reverse;
    
    public int delay=0;
    
    GlobalParams glob;
        
    Layer preLayer=null;
    Layer postLayer=null;
    
    public boolean enable=true;    // Strength of feedforward connections
//    boolean backSend=false;   // Strength of feedback connenctions

    Random rand=new Random();
    
    
    
    public Axon(Layer inLayer, Layer outLayer,GlobalParams glo)
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
            
            PSP psp=new PSPUnitToLayer(sp,glob.useGlobalDelay?glob.delay:delay,this);
            
//            Spike ev=sp.copyOf();
//            
//            ev.setAxon(this);
//            
//            ev.defineDelay(glob.delay);
//            
            net.addToInternalQueue(psp);
            
            postSpike(psp); // Potential for overrides
            
        }
//            sendSpikeToLayer(sp,getWeights(preUnit),postLayer);
    }
    
    public void postSpike(PSP p)
    {        
    }
    
    
    
//    public void sendBackwards(Spike sp,int postUnit)
//    {
//        // Fire Ahead!
//        if (backSend) 
//            sendSpikeToLayer(sp,getBackWeights(postUnit),preLayer);
//    }
    
        
    void spikeOut(PSPtype psp)
    {
//        postLayer.fireTo(sp,w[sp.addr]);
        postLayer.fireTo(psp,getWeights(psp.sp.addr));
        
//        System.out.println("pre: "+preLayer.ixLayer+"\tpost: "+postLayer.ixLayer);
        
        
//        if (preLayer.ixLayer==1 && postLayer.ixLayer==2)
//            System.out.println("OUT");
        
        
//        for (int i=0; i < postLayer.nUnits(); i++)
//        {   
////            
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
    
    
    public void setAllWeights(float wval)
    {
//        w=new float[preLayer.nUnits()][postLayer.nUnits()];
        
        for (int i=0; i<w.length; i++)
            for (int j=0; j<w[i].length; j++)
                w[i][j]=wval;
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

    void check() {
        if (w==null || (w.length>0 && w[0]==null))
            throw new RuntimeException(getName()+": Weights are not initialized!  See function \"initWeights\".");
    }

        
    public static class Factory implements AbstractFactory<Axon>
    {
        public Globals glob;
        public Factory()
        {   glob=new Globals();
        }

        @Override
        public Axon make(Layer inLayer,Layer outLayer)
        {   return new Axon(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
        }

        @Override
        public Controllable getGlobalControls() {
            return new Globals();
        }
    }
    
    
    public static class Globals extends Controllable
    {   
        public boolean doRandomJitter=true;    
        
        public boolean useGlobalDelay=true;
        
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

        public boolean isUseGlobalDelay() {
            return useGlobalDelay;
        }

        public void setUseGlobalDelay(boolean useGlobalDelay) {
            this.useGlobalDelay = useGlobalDelay;
        }
    }
    
    
    public interface AbstractFactory<AxonType extends Axon> extends Serializable
    {
        public abstract AxonType make(Layer inLayer,Layer outLayer);
                
        public abstract Controllable getGlobalControls();
        
    }
    
    
    Controllable controls;
    public Controllable getControls()
    {   if (controls==null)
            controls=makeController();
        return controls;
    }
    
    public Controllable makeController()
    {
        return new Controller();        
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
            Axon.this.enable = fwdSend;
        }

        
        public int getDelay()
        {
            return delay;
        }
        
        public void setDelay(int del)
        {
            delay=del;
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
    public static class Reverse extends Axon {

        Axon forwardAxon;

        public Reverse(Axon fwdAx)
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
        
        /** Reverse axon does not use it's own weight matrix */
        @Override
        public void check()
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

    
//    public static class AxonEvent extends PSP
//    {
//        final AxonBundle ax;
//        
//        public AxonEvent(Spike sp,AxonBundle axe)
//        {   super(sp);
//            ax=axe;
//        }
//
//        @Override
//        public int getHitTime() {
//            throw new UnsupportedOperationException("Not supported yet.");
//        }
//        
//    }
    
    
//    public static class Initializer
//    {            
//        public int inLayer;
//        public int outLayer;
//
//        public float wMean=Float.NaN; // NaN indicates "don't make a weight matrix"
//        public float wStd=0;    
//
//        public Initializer(int pre,int post)
//        {
//            inLayer=pre;
//            outLayer=post;
//        }
//
//        public boolean isConnectedTo(int pre, int post)
//        {   return (inLayer==pre && outLayer==post);            
//        }
//    }
//    
    
    
    
}