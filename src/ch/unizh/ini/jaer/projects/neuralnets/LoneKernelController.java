/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.util.ArrayList;

import jspikestack.ControlPanel;
import jspikestack.Controllable;
import jspikestack.KernelMaker2D;

/**
 * This class 
 * @author Peter
 */
public abstract class LoneKernelController extends Controllable
{
    public String name;

    public int dimx=5;
    public int dimy=5;            
    public KernelMaker2D.KernelClass type=KernelMaker2D.KernelClass.GAUSSIAN;            
    public Controllable kernel;

    @Override
    public String getName() {

        if (name==null)
            return "Kernel Controller";        
        else 
            return name;
    }

    /** Set kernel */
    abstract public void doApply_Kernel();

    public void doPreview_Kernel()
    {
        KernelMaker2D.plot(get2DKernel());

    }
    
    public void setKernelControl(KernelMaker2D.Computable kernel,int dimx,int dimy)
    {                
//        KernelController k=(KernelController)cont.getKernelController();
        this.type=KernelMaker2D.getKernelClass(kernel.getClass());
        this.dimx=dimx;
        this.dimy=dimy;
        this.kernel=kernel;
    }
    
    public ControlPanel getControl()
    {
        ControlPanel cp=new ControlPanel(this);
        return cp;
        
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
