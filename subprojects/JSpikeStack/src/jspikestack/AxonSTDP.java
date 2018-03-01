/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 *
 * @author oconnorp
 */
public class AxonSTDP<GlobalParams extends AxonSTDP.Globals> extends Axon<GlobalParams,PSPUnitToLayer> {
    
    
//    
//    public static <NetType extends SpikeStack> NetType makeNet()
//    {   return (NetType) new STPStack();
//    }
//    
    
    
        
//    public static class Layer <NetType extends STDPAxon,LayerType extends AxonBundle, UnitType extends Unit,GlobalParams extends STDPAxon.Layer.Globals> extends AxonBundle<NetType,LayerType,UnitType>
//    {
//        int outBufferBookmark=0;   // Bookmark used for stdp learning
//        int thisBufferBookmark=0;

        
        
        Queue<PSP> presyn;   // Queue of presynaptic PSPs
        Queue<Spike> postsyn;  // Queue of postsynaptic spikes
    
        
        private boolean enableSTDP=false;     // Enable STDP on the slow weights

        public AxonSTDP(Layer inLayer,Layer outLayer,GlobalParams glo)
        {   super(inLayer,outLayer,glo);   
//            glob=glo;
        }
        
        public boolean isEnableSTDP() {
            return enableSTDP;
        }

        public void setEnableSTDP(boolean enableSTDP) {
            this.enableSTDP = enableSTDP;
            setSTDPstate();
        }

        @Override
        public void postSpike(PSP p)
        {
            if(this.isLearningEnabled())
                presyn.add(p);
            
        }
        
        
        /** For performance: enable/disable queues */
        public void setSTDPstate()
        {
            if (isLearningEnabled() && postLayer!=null && presyn ==null)
            {
                final int outLayer=postLayer.ixLayer;
                postsyn=net.outputQueue.addReader(new Comparable<Spike>() 
                    {   @Override
                        public int compareTo(Spike o) 
                        {   return o.layer==outLayer?1:0;
                        }
                    });
                
                final int thisLayer=preLayer.ixLayer;
                presyn=new LinkedList<PSP>();
//                presyn=net.outputQueue.addReader(new Comparable<Spike>()
//                    {   @Override
//                        public int compareTo(Spike o) 
//                        {   return o.layer==thisLayer?1:0;
//                        }
//                    });
            }
            else
            {
                net.outputQueue.removerReader(presyn);
                net.outputQueue.removerReader(postsyn);
                
                presyn=null;
                postsyn=null;
                
            }
            
        }
        
        
        
        /** Actions to perform after input spike is processed */
        @Override
        public void updateActions()
        {   if (isLearningEnabled())
                stdpLearn();
        }
        
        
        /** Determine whether to spend time looping through spikes */
        public boolean isLearningEnabled()
        {   return isEnableSTDP();            
        }

        @Override
        public void reset()
        {   super.reset();
        
//            if (presyn!=null)
//            {
//                presyn.clear();
//                postsyn.clear();
//            }
        
//            outBufferBookmark=0;
//            thisBufferBookmark=0;
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
//            if (postLayer.outBuffer.isEmpty() || outBuffer.isEmpty()) return;

            
            
            if (postsyn.isEmpty() || presyn.isEmpty() )
                return;

            // While (postsyn buffer has events) && ( (current postsyn bookmark time) + (stdp window limit) < (current time) )
//            while ((postLayer.outBuffer.size() > outBufferBookmark) && (postLayer.getOutputEvent(outBufferBookmark).time + glob.stdpWin < net.time))
            while (!postsyn.isEmpty() && (postsyn.peek().time + glob.stdpWin < net.time))      
            {   // Iterate over new output spikes

                // Get current output event
                Spike evout=postsyn.poll();
                

                // Adjust the out-time back by the delay so it can be compared with the input time that caused it.
//                double outTime=evout.time-net.delay;
                int outTime=evout.time;//-glob.delay;

//                int tempBookmark=thisBufferBookmark;    // Temporary bookmark for iterating through presyn spikes around an output spike

                
                
                // While (there are presynapic events available) && (they come before the end of the relevant stdp window for the post-synaptic spike)
                
                
                //while (!presyn.isEmpty() && (presyn.peek().time < evout.time+glob.stdpWin)) 
                
                Iterator<PSP> preit=presyn.iterator();
                int i=0;
                while (preit.hasNext() ) 
                {   // Iterate over input events (from this layer) pertaining to the output event

//                    Spike evin=outBuffer.get(tempBookmark);
//                    Spike evin=getOutputEvent(tempBookmark);
//                    Spike evin=presyn.peek();
                    PSP evin=preit.next();
                    
                    int inTime=evin.hitTime;
                                        
                    if (inTime + glob.stdpWin < outTime) // If input event is too early to be relevant
                    {   //thisBufferBookmark++; // Shift up starting bookmark
                        
                        // Mark item in presynaptic queue for later removal
                        i++;
//                        presyn.poll(); // Can't do this because you can't edit a list that's being iterated.
                    }
                    else if (inTime >= evout.time+glob.stdpWin) // If input event is too late to be relevant
                    {
                        break;
                    }
                    else // presyn event is within relevant window, do STDP!
                    {   //System.out.println("dW: "+net.stdpRule(evout.time-evin.time));
                        updateWeight(evin.sp.addr,evout.addr,outTime-inTime);
                    }
                    
                } 
                
                // Remove the used-up presynaptic events.
                for (int j=0;j<i;j++)
                    presyn.poll();
                
            }
        }


        public void updateWeight(int inAddress,int outAddress,double deltaT)
        {
            if (this.isEnableSTDP())
                w[inAddress][outAddress]+=glob.stdp.calc(deltaT);  // Change weight!

        }

   

        
        public static class Factory<AxonType extends AxonSTDP> implements Axon.AbstractFactory<AxonSTDP>
        {
            public Globals glob;
            
            public Factory()
            {   glob = new Globals();
            }
            

            @Override
            public Controllable getGlobalControls() {
                return glob;
            }

            @Override
            public AxonType make(Layer inLayer, Layer outLayer) {
                return (AxonType) new AxonSTDP(inLayer,outLayer,glob);
            }
        }
        
        
        public static class Globals extends Axon.Globals
        {
            public STDPrule stdp=new STDPrule();;
    
            public int stdpWin;


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
            
            
            /** Strength Constant of Pre-Before-Post */
            public float getPlusStrength() {
                return stdp.plusStrength;
            }

            /** Strength Constant of Pre-Before-Post */
            public void setPlusStrength(float plusStrength) {
                this.stdp.plusStrength = plusStrength;
            }

            /** Strength Constant of Post-Before-Pre */
            public float getMinusStrength() {
                return stdp.minusStrength;
            }

            /** Strength Constant of Post-Before-Pre */
            public void setMinusStrength(float minusStrength) {
                this.stdp.minusStrength = minusStrength;
            }

            /** Time constant of Pre-before-Post */
            public float getStdpTCplus() {
                return stdp.stdpTCplus;
            }

            /** Time constant of Pre-before-Post */
            public void setStdpTCplus(float stdpTCplus) {
                this.stdp.stdpTCplus = stdpTCplus;
            }

            /** Time Constant of Post-Before-Pre */
            public float getStdpTCminus() {
                return stdp.stdpTCminus;
            }

            /** Time Constant of Post-Before-Pre */
            public void setStdpTCminus(float stdpTCminus) {
                this.stdp.stdpTCminus = stdpTCminus;
            }
                
            
            
            
            
        }
        
        
        
        @Override
        public Controllable getControls()
        {   return new Controller();
        }
        
        class Controller extends Axon.Controller
        {   /** enable STDP learning? */
            public boolean isEnableSTDP() {
                return enableSTDP;
            }

            /** enable STDP learning? */
            public void setEnableSTDP(boolean enableSTDP) {
                
                AxonSTDP.this.setEnableSTDP(enableSTDP);
            }
        }
        
        
        

//    }
    
    
    
    
}
