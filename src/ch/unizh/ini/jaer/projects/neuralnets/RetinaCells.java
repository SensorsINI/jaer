/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.KernelMaker2D;
import jspikestack.NetController;
import jspikestack.SparseAxon;
import jspikestack.SpikeStack;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * In this filter we implement a variety of retina cells.  
 *  
 * 
 * @Description("This filter implements PV5 approach-sensitive cells.  These 
 * ganglion cells respond specifically to dark approaching objects.  See more at
 * http://www.nature.com/neuro/journal/v12/n10/extref/nn.2389-S1.pdf")
 * 
 * @author Peter
 */
public class RetinaCells extends SpikeFilter {

    public RetinaCells(AEChip chip)
    {   super(chip);
    
        this.netType=NetController.Types.SPARSE_LIF;
    }
    
    @Override
    public NetMapper makeMapper(SpikeStack net) {
        return new NetMapper<PolarityEvent>()
        {
            @Override
            public int ev2addr(PolarityEvent ev) {
                return 127-ev.y+128*ev.x;
            }
                        
            /** Map the source byte onto the layer index */
            @Override
            public int ev2layer(PolarityEvent ev)
            {   return ev.polarity==PolarityEvent.Polarity.On?1:0;
            }
        };
    }

    @Override
    public void customizeNet(SpikeStack net) {
        
        SpikeStack.Initializer ini=new SpikeStack.Initializer();
        
        ini.lay(0).dimx=ini.lay(0).dimy=128;    // ON layer
        ini.lay(1).dimx=ini.lay(1).dimy=128;    // OFF layer
        ini.lay(2).dimx=ini.lay(2).dimy=16;     // PV-5 layer
        ini.ax(0,2);
        ini.ax(1,2);
        
        
        
        
        net.buildFromInitializer(ini);
        

        net.lay(0).name="OFF cells";
        net.lay(1).name="ON cells";
        net.lay(2).name="PV5 cells";
        
//        float[][] on2pv5=KernelMaker2D.makeKernel(new KernelMaker2D.Gaussian(-.2f,40), 60, 60);
        
        
//        KernelMaker2D.plot(off2pv5);
        
        updateOnKernel();
        updateOffKernel();
        
        unitGlobs.tau=36000;
        unitGlobs.tref=7000;
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=1.5f;
        
        axonGlobs.delay=0;
        
        net.inputCurrents=false;
        
        
    }
    

    @Override
    public String[] getInputNames() {
        return new String[] {"Retina"};
    }
    
    
    public float offKernelMag=.15f;
    public float offKernelWidth=40;    
    public float onKernelMag=-.2f;
    public float onKernelWidth=64;
    
    public void updateOnKernel()
    {   if (net!=null)
        {   int ksize=(int)(getOnKernelWidth()*1.5);
            ((SparseAxon)net.ax(1,2)).defineKernel(KernelMaker2D.makeKernel(new KernelMaker2D.Gaussian(getOnKernelMag(), getOnKernelWidth()), ksize,ksize));
        }
    }
    
    public void updateOffKernel()
    {   if (net!=null)
        {   int ksize=(int)(getOffKernelWidth()*1.5);
            ((SparseAxon)net.ax(0,2)).defineKernel(KernelMaker2D.makeKernel(new KernelMaker2D.Gaussian(getOffKernelMag(), getOffKernelWidth()), ksize,ksize));
        }
    }

    public float getOffKernelMag() {
        return offKernelMag;
    }

    public void setOffKernelMag(float offKernelMag) {
        this.offKernelMag = offKernelMag;
        updateOffKernel();
    }

    public float getOffKernelWidth() {
        return offKernelWidth;
    }

    public void setOffKernelWidth(float offKernelWidth) {
        this.offKernelWidth = offKernelWidth;
        updateOffKernel();
    }

    public float getOnKernelMag() {
        return onKernelMag;
    }

    public void setOnKernelMag(float onKernelMag) {
        this.onKernelMag = onKernelMag;
        updateOnKernel();
    }

    public float getOnKernelWidth() {
        return onKernelWidth;
    }

    public void setOnKernelWidth(float onKernelWidth) {
        this.onKernelWidth = onKernelWidth;
        updateOnKernel();
    }

    
}
