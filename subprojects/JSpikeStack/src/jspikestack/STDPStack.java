/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;

/**
 *
 * @author oconnorp
 */
public class STDPStack<NetType extends STDPStack,LayerType extends STDPStack.STDPLayer> extends SpikeStack<NetType,LayerType> {
    
    public STDPrule stdp=new STDPStack.STDPrule();;
    
    public float stdpWin;
    
    public static class STDPrule implements Serializable{

        public float plusStrength = 0.1f;
        public float minusStrength= -0.1f;
        
        public float stdpTCplus;
        public float stdpTCminus;

        /* Compute weight-change from post-pre time */
        public float calc(double dt) {   //System.out.println("dt: "+dt);
            if (dt >= 0) {
                return (float) (plusStrength * (Math.exp(-dt / stdpTCplus)));
            } else {
                return (float) (minusStrength * (Math.exp(dt / stdpTCminus)));
            }
        }
    }
    
    public static <NetType extends SpikeStack> NetType makeNet()
    {   return (NetType) new STPStack();
    }
    
    
    public STDPStack()
    {   super();        
    }
        
    public STDPStack(Initializer ini)
    {   super(ini);

        if (ini.stdp!=null)
            stdp=ini.stdp;
        
        
        stdpWin=ini.stdpWin;
    
        for (int i = 0; i < layers.size(); i++) {
            lay(i).enableSTDP = ini.lay(i).enableSTDP;
        }
    }
    
    @Override
    public void addLayer(int index)
    {   
        layers.add((LayerType)new STDPLayer(this,index));
    }        
    
    
       
    
    /** Actions to perform after feeding event */
    @Override
    public void postFeed() {
        // Learning
        for (STDPLayer l : layers) {
            if (l.isLearningEnabled()) {
                l.stdpLearn();
            }
        }
    }
    
    
        
    /* Extend the initializer object to deal with this class */
    public static class Initializer extends SpikeStack.Initializer
    {   
        public STDPStack.STDPrule stdp=new STDPStack.STDPrule();;
        
        public float stdpWin;
        
                
        public Initializer(int nLayers)
        {   layers=new LayerInitializer[nLayers];
            for (int i=0; i<layers.length; i++)
                layers[i]=new LayerInitializer();
        }
        
        @Override
        public LayerInitializer lay(int n)
        {   return (LayerInitializer)layers[n];            
        }
        
        public static class LayerInitializer extends SpikeStack.Initializer.LayerInitializer
        {   
            boolean enableSTDP=false;
            public LayerInitializer()
            {   super();                
            }
        }
    }
        
    
    public static class STDPLayer <NetType extends STDPStack,LayerType extends STDPStack.STDPLayer, UnitType extends SpikeStack.Layer.Unit> extends SpikeStack.Layer<NetType,LayerType,UnitType>
    {
        int outBufferBookmark=0;   // Bookmark used for stdp learning
        int thisBufferBookmark=0;

        public boolean enableSTDP=false;     // Enable STDP on the slow weights

        public STDPLayer(NetType network,int ind)
        {   super((NetType)network,ind);            
        }
        
        /** Determine whether to spend time looping through spikes */
        public boolean isLearningEnabled()
        {   return enableSTDP;            
        }

        @Override
        public void reset()
        {   super.reset();
            outBufferBookmark=0;
            thisBufferBookmark=0;
        }

        /** Apply STDP learning rule
        * This rule is applied on the layer which owns the outgoing weights.
        * 
        * The layer keeps a "bookmark" of the current position, and advances it 
        * as it reads through spikes.
        */
        public void stdpLearn(){
            /* Idea: for every new spike in the outBuffer of the post-synaptic
            * layer, we want to look for relevant output spikes from this (pre-
            * synaptic) layer,  and adjust the weights according to the stdp 
            * rule.
            * 
            * Shorthand used for internal comments:
            * presyn: pre-synaptic layer
            * postsyn: post-synaptic layer
            * 
            */ 
            if (Lout.outBuffer.isEmpty() || outBuffer.isEmpty()) return;


            // While (postsyn buffer has events) && ( (current postsyn bookmark time) + (stdp window limit) < (current time) )
            while ((Lout.outBuffer.size() > outBufferBookmark) && (Lout.getOutputEvent(outBufferBookmark).time + net.stdpWin < net.time))
            {   // Iterate over new output spikes

                // Get current output event
                Spike evout=Lout.getOutputEvent(outBufferBookmark); // TODO: REMOVE THIS F'ING CAST


                // Adjust the out-time back by the delay so it can be compared with the input time that caused it.
                double outTime=evout.time-net.delay;

                int tempBookmark=thisBufferBookmark;    // Temporary bookmark for iterating through presyn spikes around an output spike

                // While (there are presynapic events available) && (they come before the end of the relevant stdp window for the post-synaptic spike)
                while ((outBuffer.size() > tempBookmark) && (getOutputEvent(tempBookmark).time < evout.time+net.stdpWin)) 
                {   // Iterate over input events (from this layer) pertaining to the output event

//                    Spike evin=outBuffer.get(tempBookmark);
                    Spike evin=getOutputEvent(tempBookmark);

                    if (evin.time + net.stdpWin < outTime) // If input event is too early to be relevant
                    {   thisBufferBookmark++; // Shift up starting bookmark
                    }
                    else // presyn event is within relevant window, do STDP!
                    {   //System.out.println("dW: "+net.stdpRule(evout.time-evin.time));

                        updateWeight(evin.addr,evout.addr,outTime-evin.time);


                    }
                    tempBookmark++;
                } 
    //                System.out.println("t: "+evout.time+" outAddr:"+evout.addr+" nin:"+(tempBookmark-thisBufferBookmark));
                outBufferBookmark++;
            }
        }


        public void updateWeight(int inAddress,int outAddress,double deltaT)
        {
            if (this.enableSTDP)
                units[inAddress].Wout[outAddress]+=net.stdp.calc(deltaT);  // Change weight!

        }



    }
    
    
    
    
}
