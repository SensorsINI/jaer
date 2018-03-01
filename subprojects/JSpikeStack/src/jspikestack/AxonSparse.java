/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.ArrayList;

/**
 * This Axon-connection allows for specifically targeted connections between 
 * layers (as opposed to all-to-all connectivity).  This allows layers with 
 * sparse interconnections to be simulated much more efficiently.   Each neuron
 * in the pre-Layer has a list of target addresses in the post-layer.
 * 
 * In addition, there are several methods for creating 2-D "convolutional" 
 * connections between layers.  This is done by specifying dimensions of a layer
 * and then assigning a 2-D kernel, which is transformed into a weight-mapping.
 * 
 * @author oconnorp
 */
public class AxonSparse<GlobalParams extends AxonSparse.Globals,PSPType extends PSPUnitToLayer>  extends Axon<GlobalParams,PSPType> 
{
    
    int[][] targets;  // Addresses of forward connections
    
//    int[][] backIX; // Reverse addresses for backward connections
        
    public AxonSparse(Layer inLayer, Layer outLayer,GlobalParams glo)
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
        

    @Override
    void check() {
        if (w==null || (w.length>0 && w[0]==null))
            throw new RuntimeException(getName()+": Weights are not initialized!  See function \"initWeights\" or \"defineKernel\".");
    }
    
//    public class Connection
//    {
//        Connection(float[][] w, int[][] targ)
//        {
//            weights=w;
//            targets=targ;
//        }
//
//        float[][] weights;
//        int[][] targets;           
//    }

    @Override
    public Controllable makeController()
    {   return new Controller();
    }
    
    public void setKernelControl(KernelMaker2D.Computable kernel,int dimx,int dimy)
    {
        Controller cont=(Controller)getControls();
                
        KernelController k=(KernelController)cont.getKernelController();
        k.type=KernelMaker2D.getKernelClass(kernel.getClass());
        k.dimx=dimx;
        k.dimy=dimy;
        k.kernel=kernel;
        
//        cont.kcon=k;
        cont.kcon.doApply_Kernel();
    }
            
    public class Controller extends Axon.Controller
    {
        KernelController kcon;

        public KernelController getKernelController()
        {
            if (kcon==null)
                kcon=new KernelController();
            
            return kcon;
        }
        
        
        @Override
        public ArrayList<Controllable> getSubControllers()
        {
            ArrayList<Controllable> arr=new ArrayList();

//            if (kcon!=null)
//            {   kcon=new KernelController(); 
//            }           

            arr.add(getKernelController());
            return arr;
        }
        
        
        
    }
    
    public class KernelController extends Controllable
    {

        public int dimx=5;
        public int dimy=5;            
        public KernelMaker2D.KernelClass type=KernelMaker2D.KernelClass.GAUSSIAN;            
        public Controllable kernel;
        
        @Override
        public String getName() {
            
            AxonSparse ax=AxonSparse.this;
            
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
                kernel=KernelMaker2D.newKernel(getType());
                
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
            
            kernel=KernelMaker2D.newKernel(type);
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
        targets=new int[preLayer.nUnits()][0];
        w=new float[preLayer.nUnits()][0];
        
    }
        
        
    public static class Factory extends Axon.Factory
    {        
        public Factory()
        {   glob=new Globals();            
        }
        
        @Override
        public Axon make(Layer inLayer,Layer outLayer)
        {   return new AxonSparse(inLayer,outLayer,glob); 
        }
    }
    
    
}
