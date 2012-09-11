/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

/**
 *
 * @author oconnorp
 */
public class SparseAxon<GlobalParams extends SparseAxon.Globals,PSPType extends PSPUnitToLayer>  extends AxonBundle<GlobalParams,PSPType> 
{
    
    int[][] targets;  // Addresses of forward connections
    
//    int[][] backIX; // Reverse addresses for backward connections
        
    public SparseAxon(Layer inLayer, Layer outLayer,GlobalParams glo)
    {   super(inLayer,outLayer,glo);
    }
    
    
    /** Fire a spike out of this axon to downstream units */
    @Override
    void spikeOut(PSPType psp)
    {
//        postLayer.fireTo(sp,w[sp.addr]);
        postLayer.fireTo(psp,targets[psp.sp.addr],getWeights(psp.sp.addr));
        
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
    public void defineKernel(final float wk[][])
    {
        KernelMaker2D.FloatConnection conn=KernelMaker2D.invert(wk,preLayer.dimx,preLayer.dimy,postLayer.dimx,postLayer.dimy);
        
        w=conn.weights;
        targets=conn.targets;
        
    }
        

    public class Connection
    {
        Connection(float[][] w, int[][] targ)
        {
            weights=w;
            targets=targ;
        }

        float[][] weights;
        int[][] targets;           
    }



    public class Controller extends AxonBundle.Controller
    {
        KernelController kcon;

        @Override
        public ArrayList<Controllable> getSubControllers()
        {
            ArrayList<Controllable> arr=new ArrayList();

            if (kcon!=null)
            {   kcon=new KernelController(); 
            }

            arr.add(kcon);
            return arr;
        }


    }

    @Override
    public Controllable getControls()
    {
        return new KernelController();
    }

    public class KernelController extends Controllable
    {

        public int dimx=5;
        public int dimy=5;            
        public KernelMaker2D.KernelClass type=KernelMaker2D.KernelClass.GAUSSIAN;            
        public Controllable kernel;
        
        @Override
        public String getName() {
            
            SparseAxon ax=SparseAxon.this;
            
            return "L"+ax.preLayer.ixLayer+"-L"+ax.postLayer.ixLayer+" Kernel";                
        }

        /** Set kernel */
        public void doApply_Kernel()
        {   
            //float[][] kernel=KernelMaker2D.makeKernel((KernelMaker2D.Computable)kernel, dimx, dimy);
            
            KernelMaker2D.FloatConnection k=KernelMaker2D.invert(get2DKernel(), preLayer.dimx, preLayer.dimy, postLayer.dimx, postLayer.dimy);
            
            w=k.weights;
            targets=k.targets;
            
        }

        public void doPreview_Kernel()
        {
            KernelMaker2D.plot(get2DKernel());

        }

        public float[][] get2DKernel()
        {
            return KernelMaker2D.makeKernel((KernelMaker2D.Computable)kernel, dimx, dimy);
        }
        
        @Override
        public ArrayList<Controllable> getSubControllers()
        {
            ArrayList<Controllable> arr=new ArrayList();

            if (kernel==null)
                kernel=KernelMaker2D.getKernelType(getType());
                
            arr.add(kernel);

            return arr;

        }

        public int getDimx() {
            return dimx;
        }

        public void setDimx(int dimx) {
            this.dimx = dimx;
        }

        public int getDimy() {
            return dimy;
        }

        public void setDimy(int dimy) {
            this.dimy = dimy;
        }

        public KernelMaker2D.KernelClass getType() {
            return type;
        }

        public void setType(KernelMaker2D.KernelClass type) {
            
            kernel=KernelMaker2D.getKernelType(type);
            updateControl();
            this.type = type;
        }

        public Controllable getKernel() {
            return kernel;
        }
            
    }
        
    
    @Override
    /** Initialize the weights.  By Default, the layer is a buffer. */
    public void initWeights()
    {        
    }
        
        
    public static class Factory extends AxonBundle.Factory
    {        
        public Factory()
        {   glob=new Globals();            
        }
        
        @Override
        public AxonBundle make(Layer inLayer,Layer outLayer)
        {   return new SparseAxon(inLayer,outLayer,glob); 
        }
    }
    
    
}
