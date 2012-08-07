/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.ArrayList;

/**
 *
 * @author oconnorp
 */
public class SparseAxon<GlobalParams extends Axons.Globals>  extends Axons {
    
    int[][] fwdIX;  // Addresses of forward connections
    
    int[][] backIX; // Reverse addresses for backward connections
    
    
    public SparseAxon(Layer inLayer, Layer outLayer,GlobalParams glo)
    {
        super(inLayer,outLayer,glo);   
        
    }
    
    
    /** Carry out the effects of the firing */
    @Override
    public void sendForwards(Spike sp,int preUnit)
    {
        // Fire Ahead!
        if (fwdSend)
            sendSpikeToLayer(sp,getForwardWeights(preUnit),fwdIX[preUnit],postLayer);
            
    }
        
    @Override
    public void sendBackwards(Spike sp,int postUnit)
    {
        // Fire Ahead!
        if (backSend) 
            sendSpikeToLayer(sp,getBackWeights(postUnit),backIX[postUnit],preLayer);
    }
    
    
    void sendSpikeToLayer(Spike sp, float[] wgt, int[] destinations, Layer lay)
    {
        for (int i=0; i < destinations.length; i++)
        {   
            if (destinations[i]==-1)
                continue;
            
            Spike ev=lay.fireTo(sp, destinations[i], wgt[i]);
        
            if (ev==null)
                continue;
            else
            {   
                int delay=glob.getDelay();
                if (glob.doRandomJitter)
                    delay+=rand.nextInt(glob.getRandomJitter());
                
                ev.defineDelay(delay);
                                
                ev.layer=lay.ixLayer;
                
                net.addToInternalQueue(ev);
            }
        }
    }
    
    /** Define a the sparse weights based on a convolutional kernel 
     * 
     * Each neuron in the output layer will receive inputs from a region of the 
     * input layer corresponding to the size of the kernel.
     * 
     * It's required that the dimx, dimy parameters of both the input and output
     * layers be defined.
     * 
     * @param wk 
     */
    public void defineKernel(float wk[][])
    {
        if (preLayer.dimx * preLayer.dimy != preLayer.nUnits())
            throw new RuntimeException("The product of dimensions of the pre-Synaptic layer ("+preLayer.dimx * preLayer.dimy +") don't match the number of units (" + preLayer.nUnits() + ")");
        
        if (postLayer.dimx * postLayer.dimy != postLayer.nUnits())
            throw new RuntimeException("The product of dimensions of the post-Synaptic layer ("+postLayer.dimx * postLayer.dimy +") don't match the number of units (" + postLayer.nUnits() + ")");
        
        
        // Step 1: Flip kernel (so it's a TO kernel instead of a FROM kernel), and squeeze it into 1 dim
        int sz=0;
        for (int i=0; i<wk.length; i++)
            sz+=wk[i].length;
        float[] wnew=new float[sz];
        int k=0;
        for (int i=wk.length-1; i>-1; i--)
            for (int j=wk[i].length-1; j>-1; j--)
                wnew[k++]=wk[i][j];
        
        // Step 2: Define the output indeces
        int[][] targets=new int[preLayer.nUnits()][];
        for(int i=0; i<preLayer.nUnits(); i++)
        {
            // Find the location of this input
            int idimx=i/preLayer.dimy;
            int idimy=i%preLayer.dimy;
            
            // Find the output around which this input should be centred
            int odimx=(int)Math.floor(postLayer.dimx*((float)idimx/preLayer.dimx));            
            int odimy=(int)Math.floor(postLayer.dimy*((float)idimy/preLayer.dimy));            
                                            
            // Find and add the target indeces
            targets[i]=new int[sz];
            k=0;            
            for (int j=wk.length-1; j>-1; j--)
            {   int dy=wk.length/2-j;                
                for (int m=wk[j].length-1; m>-1; m--)
                {   int dx=wk[j].length/2-m;                    
                    targets[i][k++]=postLayer.loc2index(odimx+dx, odimy+dy);
                }
            }            
        }
        
        w=wk;
        
        w=new float[preLayer.nUnits()][];
        for (int i=0; i<w.length; i++)
            w[i]=wnew;      
        
        
        fwdIX=targets;
        
    }
    
    @Override
    /** Initialize the weights.  By Default, the layer is a buffer. */
    public void initWeights()
    {
//        w=new float[preLayer.nUnits()][1];
//        
//        
//        w[1]{1};
//        
        
        
    }
    
    
    static float[][] transpose(float[][] w)
    {
        float[][] wt=new float[w[0].length][w.length];
        for(int i=0; i<wt.length; i++) 
            for(int j=0; j<wt[i].length; j++)
                wt[i][j]=w[j][i];
        return wt;
    }
    
//    
//    /** Fire current to a given unit in this layer... */
//    public Spike fireTo(Spike sp,int ix,float inputCurrent)
//    {   
//        if (ix==-1)
//            return null;
//        else        
//            return units[ix].fireTo(sp,inputCurrent);
//                
//    }
//    
//    
    
//    public int[][]i
    
    public static class Factory extends Axons.Factory
    {
        
        
        
        @Override
        public Axons make(Layer inLayer,Layer outLayer)
        {   return new SparseAxon(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
        }
        
        
    }
    
}
