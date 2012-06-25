/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.lang.reflect.Array;

/**
 * This class defines a Neural net capable of both long and short term plasticity.
 * 
 * The units in this network contain "fast weights" - that is, weights overlaid 
 * on the regular weight matrix that decay over time and can be trained with an 
 * STDP rule.
 * 
 * @author oconnorp
 */
public class STPStack <NetType extends STPStack,LayerType extends STPStack.Layer> extends STDPStack<NetType,LayerType> {
    
    public float fastWeightTC;
    
    
    public STDPStack.STDPrule fastSTDP=new STDPStack.STDPrule();
    
    public STPStack()
    {   super();
    }
    
    public static <NetType extends SpikeStack> NetType makeNet()
    {   return (NetType) new STDPStack();
    }
    
    public STPStack(STPStack.Initializer ini)
    {   super(ini);
    
        if (ini.fastSTDP!=null)
            fastSTDP=ini.fastSTDP;
        
        
        fastWeightTC=ini.fastWeightTC;
        
        for (int i = 0; i < layers.size(); i++) {
            lay(i).enableFastSTDP = ini.lay(i).enableFastSTDP;
        }
        
    }
    
    
    @Override
    public void addLayer(int index)
    {   
        layers.add((LayerType)new Layer(this,index));
    }        
    
    
    /** A Layer of Neurons capable of fast-weight STDP */
    public static class Layer <NetType extends STPStack,LayerType extends STPStack.Layer,UnitType extends STPStack.Layer.Unit> extends STDPStack.STDPLayer<NetType,LayerType,UnitType>
    {
        public boolean enableFastSTDP=false;
                        
        @Override
        public void initializeUnits(int nUnits)
        {   // AHAHAH I tricked you Java! 
            units=(UnitType[])Array.newInstance(Unit.class, nUnits);
        }
                
        @Override
        public boolean isLearningEnabled()
        {   return enableFastSTDP || enableSTDP;            
        }
    
        @Override
        public UnitType makeNewUnit(int index)
        {   return (UnitType) new Unit(index);            
        }
        
        public Layer(STPStack st,int index)
        {   super((NetType)st,index);   
        }        
        
        
        @Override
        public void updateWeight(int inAddress,int outAddress,double deltaT)
        {   super.updateWeight(inAddress,outAddress,deltaT);
            
            /* Update the given fast weight */
            if (this.enableFastSTDP)
            {   
                units[inAddress].WoutFast[outAddress]+=net.fastSTDP.calc(deltaT);  // Change weight!
                units[inAddress].WoutFastTimes[outAddress]=net.time;        
            }
        }
        
        /** Extension of Regular LIF unit.. made to manage fast weights */
        public class Unit extends SpikeStack.Layer.Unit
        {
            float[] WoutFast;           // Fast overlay weights
            double[] WoutFastTimes;     // Time of last update of fastweights

            public Unit(int index)
            {   super(index);  
            }


            /* Get the forward weights from a given source */
            @Override
            public float[] getForwardWeights() {
                
                if (!enableFastSTDP)
                    return Wout;
                                    
                float[] w=new float[Wout.length];
                
                for (int i=0; i<w.length; i++)
                    w[i]=getOutWeight(i);
                
                return w;
            }
            
            /** Compute present value of the fast weight */
            public float currentFastWeightValue(int index)
            {
                return WoutFast[index]*(float)Math.exp((WoutFastTimes[index]-net.time)/net.fastWeightTC);
                
            }
            
            @Override
            float getOutWeight(int index)
            {
                if (!enableFastSTDP)
                    return Wout[index];
                    
                return Wout[index]+currentFastWeightValue(index);
            }
            
            /** Set the output Weight vector.  NOTE: This also resets the fast weights */
            @Override
            public void setWout(float[] wvec)
            {   Wout=wvec;
                
                WoutFast=new float[wvec.length];
                WoutFastTimes=new double[wvec.length];
            
            }
 
            

        }
    }
    
    
    public static class Initializer extends STDPStack.Initializer
    {   
        STDPrule fastSTDP=new STDPStack.STDPrule();;
        float fastWeightTC;
        
        public Initializer(int nLayers)
        {   super(nLayers);
        }
        
        @Override
        public LayerInitializer lay(int n)
        {   return (LayerInitializer)layers[n];            
        }
        
        public static class LayerInitializer extends STDPStack.Initializer.LayerInitializer
        {   
            boolean enableFastSTDP=false;
            float fastWeightInfluence=0;
            
            public LayerInitializer()
            {   super();                
            }
        }
    }
    
    
    
    
}
