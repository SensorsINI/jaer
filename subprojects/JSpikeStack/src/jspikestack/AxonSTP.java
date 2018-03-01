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
public class AxonSTP extends AxonSTDP<AxonSTP.Globals> {

    private boolean enableFastSTDP=false;

    float[][] wOutFast;
    float[][] wLatFast;
    int[][] wOutFastTimes;
                        
//        @Override
//        public void initializeUnits(int nUnits)
//        {   // AHAHAH I tricked you Java! 
//            units=(UnitType[])Array.newInstance(Unit.class, nUnits);
//        }
                
        @Override
        public boolean isLearningEnabled()
        {   return enableFastSTDP || isEnableSTDP(); 
        }

    public void setEnableFastSTDP(boolean enable)
    {
        enableFastSTDP=enable;
        this.setSTDPstate();
    }
        
    public AxonSTP(Layer inLayer, Layer outLayer,Globals glo)
    {   super(inLayer,outLayer,glo);   
    
        
        wOutFast=new float[preLayer.nUnits()][postLayer.nUnits()];
        wOutFastTimes=new int[preLayer.nUnits()][postLayer.nUnits()];
    
    }
    
//        @Override
//        public UnitType makeNewUnit(int index)
//        {   return (UnitType) new Unit(index);            
//        }
//        
//        public Layer(STPAxon st,int index)
//        {   super((NetType)st,index);   
//        }        

//    /** Determine whether to spend time looping through spikes */
//    @Override
//    public boolean isLearningEnabled()
//    {   return enableSTDP || enableFast;            
//    }

    
    @Override
    public void updateWeight(int inAddress,int outAddress,double deltaT)
    {   super.updateWeight(inAddress,outAddress,deltaT);

        /* Update the given fast weight */
        if (this.enableFastSTDP)
        {   
            wOutFast[inAddress][outAddress]+=glob.fastSTDP.calc(deltaT);  // Change weight!
            wOutFastTimes[inAddress][outAddress]=net.time; 


        }
    }

    @Override
    public float[] getWeights(int index) {

        if (!enableFastSTDP)
            return w[index];

        float[] ww=new float[w[index].length];

        for (int i=0; i<ww.length; i++)
            ww[i]=getOutWeight(index,i);

        return ww;
    }

    @Override
    public float getOutWeight(int source,int dest)
    {
        if (!enableFastSTDP)
            return w[source][dest];

        return w[source][dest]+currentFastWeightValue(source,dest);
    }

    /** Compute present value of the fast weight */
    public float currentFastWeightValue(int source,int dest)
    {   

        return wOutFast[source][dest]*(float)Math.exp((wOutFastTimes[source][dest]-net.time)/glob.getFastWeightTC());

    }

    @Override
    public void setWout(int sourceIndex,float[] wvec)
    {   super.setWout(sourceIndex,wvec);
        
        wOutFast[sourceIndex]=new float[wvec.length];
        wOutFastTimes[sourceIndex]=new int[wvec.length];
    }
    

    public static class Factory<LayerType extends Axon> implements Axon.AbstractFactory<LayerType>
    {
        public Globals glob;

        public Factory()
        {   glob = new Globals();
        }

        @Override
        public LayerType make(Layer inLayer, Layer outLayer)
        {
            return (LayerType) new AxonSTP(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
        }
        
        @Override
        public Controllable getGlobalControls() {
            return glob;
        }
        
    }

    public static class Globals extends AxonSTDP.Globals
    {

        public float fastWeightTC;

        public AxonSTDP.Globals.STDPrule fastSTDP=new AxonSTDP.Globals.STDPrule();

        /** Time Constant for fast-weights */
        public float getFastWeightTC() {
            return fastWeightTC;
        }

        /** Time Constant for fast-weights */
        public void setFastWeightTC(float fastWeightTC) {
            this.fastWeightTC = fastWeightTC;
        }

        /** Strength Constant of Pre-Before-Post */
        public float getFastPlusStrength() {
            return fastSTDP.plusStrength;
        }

        /** Strength Constant of Pre-Before-Post */
        public void setFastPlusStrength(float plusStrength) {
            this.fastSTDP.plusStrength = plusStrength;
        }

        /** Strength Constant of Post-Before-Pre */
        public float getFastMinusStrength() {
            return fastSTDP.minusStrength;
        }

        /** Strength Constant of Post-Before-Pre */
        public void setFastMinusStrength(float minusStrength) {
            this.fastSTDP.minusStrength = minusStrength;
        }

        /** Time constant of Pre-before-Post */
        public float getFastStdpTCplus() {
            return fastSTDP.stdpTCplus;
        }

        /** Time constant of Pre-before-Post */
        public void setFastStdpTCplus(float stdpTCplus) {
            this.fastSTDP.stdpTCplus = stdpTCplus;
        }

        /** Time Constant of Post-Before-Pre */
        public float getFastStdpTCminus() {
            return fastSTDP.stdpTCminus;
        }

        /** Time Constant of Post-Before-Pre */
        public void setFastStdpTCminus(float stdpTCminus) {
            this.fastSTDP.stdpTCminus = stdpTCminus;
        }
       
    }

    @Override
    public Controllable getControls()
    {   return new Controller();
    }

    class Controller extends AxonSTDP.Controller
    {   /** enable STDP learning? */
        public boolean isEnableFastWeights() {
            return enableFastSTDP;
        }

        /** enable STDP learning? */
        public void setEnableFastWeights(boolean enable) {
            AxonSTP.this.setEnableFastSTDP(enable);
        }       
        
        
        
        
        
    }
//    
    
    

        
        /** Extension of Regular LIF unit.. made to manage fast weights */
//        public class Unit extends SpikeStack.Layer.Unit
//        {
//            float[] WoutFast;           // Fast overlay weights
//            double[] WoutFastTimes;     // Time of last update of fastweights
//
//            public Unit(int index)
//            {   super(index);  
//            }
//
//
//            /* Get the forward weights from a given source */
//            @Override
//            public float[] getForwardWeights() {
//                
//                if (!enableFastSTDP)
//                    return Wout;
//                                    
//                float[] w=new float[Wout.length];
//                
//                for (int i=0; i<w.length; i++)
//                    w[i]=getOutWeight(i);
//                
//                return w;
//            }
//            
//            
//            
//            @Override
//            float getOutWeight(int index)
//            {
//                if (!enableFastSTDP)
//                    return Wout[index];
//                    
//                return Wout[index]+currentFastWeightValue(index);
//            }
//            
//            /** Set the output Weight vector.  NOTE: This also resets the fast weights */
//            @Override
//            public void setWout(float[] wvec)
//            {   Wout=wvec;
//                
//                WoutFast=new float[wvec.length];
//                WoutFastTimes=new double[wvec.length];
//            
//            }
// 
//            
//
//        }
    
    
    
    
//    public static class Initializer extends STDPAxon.Initializer
//    {   
//        STDPrule fastSTDP=new STDPAxon.STDPrule();;
//        float fastWeightTC;
//        
//        public Initializer(int nLayers)
//        {   super(nLayers);
//        }
//        
//        @Override
//        public LayerInitializer lay(int n)
//        {   return (LayerInitializer)layers[n];            
//        }
//        
//        public static class LayerInitializer extends STDPAxon.Initializer.LayerInitializer
//        {   
//            boolean enableFastSTDP=false;
//            float fastWeightInfluence=0;
//            
//            public LayerInitializer()
//            {   super();                
//            }
//        }
//    }
    
    
    
    
    
    
    
}
