/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public class AxonSpatioTemporal extends AxonSparse<AxonSparse.Globals,PSPUnitToUnit>
{
    int[][] delays;
    
    public AxonSpatioTemporal(Layer inLayer, Layer outLayer,AxonSparse.Globals glo)
    {   super(inLayer,outLayer,glo);
    }
    
    
    /** Carry out the effects of the firing */
    @Override
    public void spikeIn(Spike sp)
    {
        // Fire Ahead!
        if (enable)
        {
            for (int i=0; i<delays[sp.addr].length; i++)
            {
//                PSPUnitToUnit psp=new PSPUnitToUnit(sp,this,targets[sp.addr][i],delays[sp.addr][i]);
                PSPUnitToUnit psp=new PSPUnitToUnit(sp,this,i,delays[sp.addr][i]);
                net.addToInternalQueue(psp);                
                postSpike(psp); // Potential for overrides
            
            }            
        }
    }
    
    
    @Override
    void spikeOut(PSPUnitToUnit psp)
    {
        postLayer.fireTo(psp,targets[psp.sp.addr][psp.targetNumber],getOutWeight(psp.sp.addr,psp.targetNumber));
    }
    
    
    
    public static class Factory extends Axon.Factory
    {        
        @Override
        public Axon make(Layer inLayer,Layer outLayer)
        {   return new AxonSpatioTemporal(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
        }
    }
    
    public void defineKernel(float[][] weights,int[][] propagationDelays)
    {
        // Check that input kernels have consistent size.
        if(weights.length!=propagationDelays.length)
            throw new RuntimeException("weight kernel and propagation-delay kernel are not the same size!");
        else
            for(int i=0; i<weights.length; i++)
                if(weights[i].length!=propagationDelays[i].length)
                    throw new RuntimeException("weight kernel and propagation-delay kernel are not the same size!");
        
        // Define connection matrices from kernel 
        KernelMaker2D.FloatConnection c=KernelMaker2D.invert(weights,preLayer.dimx,preLayer.dimy,postLayer.dimx,postLayer.dimy);        
        KernelMaker2D.IntConnection c2=KernelMaker2D.invert(propagationDelays,preLayer.dimx,preLayer.dimy,postLayer.dimx,postLayer.dimy);
        
        w=c.weights;
        targets=c.targets;
        delays=c2.weights;
                
    }
    
    
    
}
