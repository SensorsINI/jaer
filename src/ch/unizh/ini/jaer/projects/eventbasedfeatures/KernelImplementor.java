/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;


import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ImageDisplay;

/** A filter enabling testing of various blurring kernels viz. Gaussian, LaplacianOfGaussian etc.
 * on an intensity map of the events created by maintaining a ring buffer at each pixel.
 * 
 * (Refer to PixelBuffer class for further details )
 * 
 * @author Varad
 */


public class KernelImplementor extends EventFilter2D {
        
    public boolean implementKernelMap = getPrefs().getBoolean("KernelImplementor.implementKernelMap", false);    //If this flag is set, the intensity map convolved with the selected blurring kernel is displayed
    public double RelativeThreshold = getPrefs().getDouble("KernelImplementor.RelativeThreshold", 0.5);
    
    public FilterChain filterchain;
    public PixelBuffer pixelbuffer;
           
    public int sizex;
    public int sizey;               
    
    ImageDisplay display;
    JFrame featureFrame;
    
    public ConvolutionKernelMethod kernel; 
    
    public enum Kernel {
        Gaussian, LaplacianOfGaussian    //list of available blurring kernels
    };
    
    public Kernel method = Kernel.valueOf(getPrefs().get("FeatureMap.Kernel", "Gaussian"));
    
    public KernelImplementor (AEChip chip) {
        
        super(chip);
        this.chip = chip;                                       
        
        pixelbuffer = new PixelBuffer(chip);        
        filterchain = new FilterChain(chip);        
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        display = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
        featureFrame = new JFrame("ImageFrameth");  // make a JFrame to hold it
        featureFrame.setPreferredSize(new Dimension(sizex, sizey));  // set the window size
        featureFrame.getContentPane().add(display, BorderLayout.CENTER); // add the GLCanvas to the center of the window
        featureFrame.pack(); // otherwise it wont fill up the display
        
        initFilter();
        
        filterchain.add(new BackgroundActivityFilter(chip));
        filterchain.add( pixelbuffer );
        pixelbuffer.setKernelImplementor(this);
        setEnclosedFilterChain(filterchain);    //creates a filter chain instance and passes this object to the kernel
                                                //method for all processing
        pixelbuffer.setEnclosed(true, this);
    }            
    
    
    synchronized public boolean isimplementKernelMapEnabled(){
        return implementKernelMap;
    }
    
    synchronized public void setimplementKernelMapEnabled( boolean implementKernelMap ) {
        
        this.implementKernelMap = implementKernelMap;        
        getPrefs().putBoolean("KernelImplementor.implementKernelMap", implementKernelMap);
        getSupport().firePropertyChange("implementKernelMap", this.implementKernelMap, implementKernelMap);
        resetFilter();
    }         
    
    synchronized public double getRelativeThreshold (){
        return RelativeThreshold;
    }
    
    synchronized public void setRelativeThreshold( double RelativeThreshold){
        this.RelativeThreshold = RelativeThreshold;
        getPrefs().putDouble("KernelImplementor.RelativeThreshold", RelativeThreshold);
        getSupport().firePropertyChange("RelativeThreshold", this.RelativeThreshold, RelativeThreshold);        
        resetFilter();
    }
    
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {                
        
        if(!isFilterEnabled() || getEnclosedFilterChain()== null) 
            return in;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        if(isimplementKernelMapEnabled() && isFilterEnabled()){               
            getEnclosedFilterChain().filterPacket(in);
        }
        return in;        
    }

    
    public Kernel getKernel() {
        return method;
    }

    synchronized public void setKernel(Kernel method) {
        getSupport().firePropertyChange("method", this.method, method);
        getPrefs().put("KernelImplementor.Kernel", method.toString());
        this.method = method;
        resetFilter();
    }
    
    
    @Override
    public void resetFilter() { 
        
        if(!isFilterEnabled()) 
            return;                 
        kernel.resetFilter();
        filterchain.reset();
        initFilter();
        
    }

    @Override
    public void initFilter() {       
        
        switch(method){     //selection of blurring kernel
            case Gaussian:
            default:{       
                kernel = new GaussianBlurKernel(chip, this);      
                break;
            }
                             
            case LaplacianOfGaussian:{
                kernel = new LaplacianOfGaussianKernel(chip, this);                                
                break;
            }                          
        }  
    }            
}