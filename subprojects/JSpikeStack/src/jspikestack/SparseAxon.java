/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.ArrayList;
import java.util.Random;

/**
 *
 * @author oconnorp
 */
public class SparseAxon<GlobalParams extends AxonBundle.Globals>  extends AxonBundle {
    
    int[][] targets;  // Addresses of forward connections
    
//    int[][] backIX; // Reverse addresses for backward connections
    
    
    public SparseAxon(Layer inLayer, Layer outLayer,GlobalParams glo)
    {
        super(inLayer,outLayer,glo);   
        
    }
    
    
    /** Carry out the effects of the firing */
//    @Override
//    public void spikeIn(Spike sp)
//    {
//        // Fire Ahead!
//        if (enable)
//            sendSpikeToLayer(sp,getWeights(sp.addr),targets[sp.addr],postLayer);
//            
//    }
        
//    @Override
//    public void sendBackwards(Spike sp,int postUnit)
//    {
//        // Fire Ahead!
//        if (backSend) 
//            sendSpikeToLayer(sp,getBackWeights(postUnit),backIX[postUnit],preLayer);
//    }
    
    
//    void sendSpikeToLayer(Spike sp, float[] wgt, int[] destinations, Layer lay)
//    {
//        for (int i=0; i < destinations.length; i++)
//        {   
//            if (destinations[i]==-1)
//                continue;
//            
//            Spike ev=lay.fireTo(sp, destinations[i], wgt[i]);
//            
//        
//            if (ev==null)
//                continue;
//            else
//            {   
//                
//                ev.ax=this;
//                int delay=glob.getDelay();
//                if (glob.doRandomJitter)
//                    delay+=rand.nextInt(glob.getRandomJitter());
//                
//                ev.defineDelay(delay);
//                                
////                ev.layer=lay.ixLayer;
//                
//                net.addToInternalQueue(ev);
//            }
//        }
//    }
    
    
    @Override
    void spikeOut(Spike sp)
    {
//        postLayer.fireTo(sp,w[sp.addr]);
        postLayer.fireTo(sp,targets[sp.addr],getWeights(sp.addr));
        
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
        
        
        if (preLayer.dimx ==postLayer.dimx && preLayer.dimy == postLayer.dimy)
        {   // Invert the kernel and apply it to each pre-layer unit. 
            //This approach is good here because we only need one copy of the weights.  It only works when pre and post layers are of equal size though.


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
            int[][] targies=new int[preLayer.nUnits()][];
            for(int i=0; i<preLayer.nUnits(); i++)
            {
                // Find the location of this input
                int idimx=i/preLayer.dimy;
                int idimy=i%preLayer.dimy;

                // Find the output around which this input should be centred
                int odimx=(int)Math.floor(postLayer.dimx*((float)idimx/preLayer.dimx));            
                int odimy=(int)Math.floor(postLayer.dimy*((float)idimy/preLayer.dimy));            

                // Find and add the target indeces
                targies[i]=new int[sz];
                k=0;            
                for (int j=wk.length-1; j>-1; j--)
                {   int dy=wk.length/2-j;                
                    for (int m=wk[j].length-1; m>-1; m--)
                    {   int dx=wk[j].length/2-m;                    
                        targies[i][k++]=postLayer.loc2index(odimx+dx, odimy+dy);
                    }
                }            
            }

            w=wk;

            w=new float[preLayer.nUnits()][];
            for (int i=0; i<w.length; i++)
                w[i]=wnew;      


            this.targets=targies;


        }
        else{
            // Iterate through output units and add the connection for each input unit.
            
            ArrayList<Float>[] weights=new ArrayList[preLayer.nUnits()];
            ArrayList<Integer>[] targs=new ArrayList[preLayer.nUnits()];
            for (int i=0; i<weights.length; i++)
            {   weights[i]=new ArrayList();
                targs[i]=new ArrayList();
            }
            for (int i=0; i<postLayer.nUnits(); i++)
            {
                
                int odimx=i/postLayer.dimy;
                int odimy=i%postLayer.dimy;
                
                // Find the input around which this output should be centred
                int idimx=(preLayer.dimx*odimx)/postLayer.dimx;            
                int idimy=(preLayer.dimy*odimy)/postLayer.dimy;           
                
               
                // Create target units
                for (int j=0; j<wk.length; j++)
                    for (int k=0; k<wk[j].length; k++)
                    {
                        // Location of input neuron of this synapse
                        int ix=idimx-wk[j].length/2+k;
                        int iy=idimy-wk.length/2+j;
                        
                        if (ix<0 || ix>=preLayer.dimx || iy<0 || iy>=preLayer.dimy)
                            continue;
                        
                        // Input neuron address
                        int index=iy+ix*preLayer.dimy;
                        
                        weights[index].add(wk[j][k]);
                        targs[index].add(i);
                    }
            }
            

            // Rebuild everything as a good old fashoned array
            w=new float[weights.length][];
            targets=new int[weights.length][];
            for (int i=0; i<weights.length; i++)
            {   w[i]=new float[weights[i].size()];
                targets[i]=new int[weights[i].size()];
                for (int j=0; j<weights[i].size(); j++)
                {   w[i][j]=weights[i].get(j);
                    targets[i][j]=targs[i].get(j);
                }
            }
                
            System.out.println("STOPP");    
            
        }
        
        
        
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
    
    
    public void makeRandomKernal(float mean,float std,int sizex,int sizey)
    {
        Random randy=new Random();
                
        float[][] ww=new float[sizex][sizey];
        
        for (int i=0; i<ww.length; i++)
            for (int j=0; j<ww[i].length; j++)
                ww[i][j]=(float) (randy.nextGaussian()*std+mean);     
        
        defineKernel(ww);
        
    }
    
    
    
    public static class Factory extends AxonBundle.Factory
    {
        
        
        
        @Override
        public AxonBundle make(Layer inLayer,Layer outLayer)
        {   return new SparseAxon(inLayer,outLayer,glob); // TODO: BAAAD.  I'm confused
        }
        
        
    }
    
    
    
    
    
//    public static class Initializer extends AxonBundle.Initializer
//    {
//        public Initializer(int preLayer, int postLayer)
//        {
//            super(preLayer,postLayer);            
//        }
//        
//        
//        int kernelx;
//        int kernely;
//        
//        
//    }
//    
    
}
