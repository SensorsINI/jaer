/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;


import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ImageDisplay;
import ch.unizh.ini.jaer.projects.eventbasedfeatures.KernelMethod;
import ch.unizh.ini.jaer.projects.eventbasedfeatures.GaussianBlurKernel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.graphics.FrameAnnotater;

/** A filter enabling testing of various blurring kernels viz. Gaussian, LaplacianOfGaussian etc.
 * on an intensity map of the events created by maintaining a ring buffer at each pixel.
 * 
 * (Refer to PixelBuffer class for further details )
 * 
 * @author Varad
 */


public class ConvolutionFeatureDetector extends EventFilter2D implements FrameAnnotater{
        
    public boolean implementFeatureDetection = getPrefs().getBoolean("ConvolutionFeatureDetector.implementFeatureDetection", true);    //If this flag is set, the intensity map convolved with the selected blurring kernel is displayed
    public double RelativeThreshold = getPrefs().getDouble("ConvolutionFeatureDetector.RelativeThreshold", 0.5);
    
    public FilterChain filterchain;
    public PixelBuffer pixelbuffer;
           
    public int sizex;
    public int sizey;               
    
    ImageDisplay display;
    JFrame featureFrame;
    
    public KernelMethod kernel; 
    
    public enum Kernel {
        LaplacianOfGaussian    //list of available blurring kernels
    };
    
    public Kernel method = Kernel.valueOf(getPrefs().get("ConvolutionFeatureDetector.Kernel", "LaplacianOfGaussian"));
    
    public ConvolutionFeatureDetector (AEChip chip) {
        
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
        pixelbuffer.setConvolutionFeatureDetector(this);
        setEnclosedFilterChain(filterchain);    //creates a filter chain instance and passes this object to the kernel
                                                //method for all processing
        pixelbuffer.setEnclosed(true, this);
        
    }            
    
    
    synchronized public boolean isimplementFeatureDetectionEnabled(){
        return implementFeatureDetection;
    }
    
    synchronized public void setimplementFeatureDetectionEnabled( boolean implementFeatureDetection ) {
        
        this.implementFeatureDetection = implementFeatureDetection;        
        getPrefs().putBoolean("ConvolutionFeatureDetector.implementFeatureDetection", implementFeatureDetection);
        getSupport().firePropertyChange("implementFeatureDetection", this.implementFeatureDetection, implementFeatureDetection);
        resetFilter();
    }         
    
    synchronized public double getRelativeThreshold (){
        return RelativeThreshold;
    }
    
    synchronized public void setRelativeThreshold( double RelativeThreshold){
        this.RelativeThreshold = RelativeThreshold;
        getPrefs().putDouble("ConvolutionFeatureDetector.RelativeThreshold", RelativeThreshold);
        getSupport().firePropertyChange("RelativeThreshold", this.RelativeThreshold, RelativeThreshold);        
        resetFilter();
    }
    
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {                
        
        if(!isFilterEnabled() || getEnclosedFilterChain()== null) 
            return in;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        if(isimplementFeatureDetectionEnabled() && isFilterEnabled()){
               
            getEnclosedFilterChain().filterPacket(in);
//            kernel.checkMaps();
//            kernel.updateMap(in);
        }
        return in;        
    }

    
    public Kernel getKernel() {
        return method;
    }

    synchronized public void setKernel(Kernel method) {
        getSupport().firePropertyChange("method", this.method, method);
        getPrefs().put("ConvolutionFeatureDetector.Kernel", method.toString());
        this.method = method;
        resetFilter();
    }
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
            
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();        

        if (gl == null) return;        
        gl.glColor3f(0,1,0);
        
        gl.glPointSize(4f);
        
        try{
            gl.glBegin(GL.GL_POINTS);
            {
                for(Point p : kernel.keypoints){
                    gl.glVertex2i(p.x, p.y);                        
                }
            }
        } finally{
            gl.glEnd();
        }                      
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
        
        
        switch(method){     
            
            case LaplacianOfGaussian:
            default:{
                
                kernel = new LaplacianOfGaussianKernel(chip, this);                                
                break;
            }                                                     
        }  
    }            
}
