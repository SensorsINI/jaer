/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.util.ArrayList;

import javax.swing.JFrame;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ImageDisplay;

/** The superclass of all convolution-based feature detectors - contains
 * common classes and methods for these detectors
 *
 * @author Varad
 */
public class ConvolutionKernelMethod extends FeatureMethod {

    AEChip chip;
    public FilterChain filterchain;            
    PixelBuffer pixelbuffer;    
    KernelImplementor kernelimplement;
    ConvolutionFeatureScheme featurescheme;
    BinaryFeatureDetector bindetect;
        
    public double RelativeThreshold;
    public float lasttimestamp;
    
    public int sizex;
    public int sizey;
    public int maplength;
    public int maxindex;
    public int minindex;
        
    public float max ;
    public float min ;
    public float base;
        
    public float[] detectormap;
    public boolean[] isKey;
    public boolean[] isOnMap;
    public float[] grayvalue ;
    public ArrayList[] mapIndices;
    public ArrayList<KeyPoint> keypoints;         
    
    ImageDisplay display;
    JFrame featureFrame;
    
    public ConvolutionKernelMethod(AEChip chip, KernelImplementor kernelimplement){
        this.chip = chip; 
        
        this.kernelimplement = kernelimplement;
        this.pixelbuffer = kernelimplement.pixelbuffer;
        this.filterchain = kernelimplement.filterchain;
        this.display = kernelimplement.display;
        this.featureFrame = kernelimplement.featureFrame;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
    }
    
    public ConvolutionKernelMethod(AEChip chip, ConvolutionFeatureScheme featurescheme){
        
        this.chip = chip; 
        
        this.featurescheme = featurescheme;
        this.pixelbuffer = featurescheme.pixelbuffer;
        this.filterchain = featurescheme.filterchain;
        this.display = featurescheme.display;
        this.featureFrame = featurescheme.featureFrame;
        this.RelativeThreshold = featurescheme.getRelativeThreshold();
       
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
    }
    
    public ConvolutionKernelMethod(AEChip chip, BinaryFeatureDetector bindetect){
        
        this.chip = chip; 
        
        this.bindetect = bindetect;
        this.pixelbuffer = bindetect.pixelbuffer;
        this.filterchain = bindetect.filterchain;
        this.display = bindetect.display;
        this.featureFrame = bindetect.featureFrame;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
 
    }    
    
    synchronized public void checkMaps(){
        
        if ( detectormap == null || detectormap.length != (chip.getSizeX()*chip.getSizeY()) )
            resetMaps();        
    }
    
    synchronized public void resetMaps(){
        max = 1;
        min = 0;
        maplength = sizex*sizey;
        maxindex = 0;
        minindex = 0;
        
        base = (max+min)/2;
        
        int size = 128;
        display.setImageSize(size, size); // set dimensions of image              
        display.resetFrame(base);
        featureFrame.setVisible(true); // make the frame visible
//        keypoints.clear();
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        detectormap = new float[maplength]; 
        
        isKey = new boolean[maplength];
        isOnMap = new boolean[maplength];
        grayvalue = new float[3*sizex*sizey] ;  
        
        for( int i = 0; i < grayvalue.length; i++ ){
            grayvalue[i] = base;
        }
        keypoints = new ArrayList<KeyPoint>();
    }

    synchronized public void checkMax(float detectormapvalue, int index){
        if(index == maxindex){
            max = detectormap[index];
        }
        
        if ( detectormapvalue > max ){
            max = detectormapvalue;  
            maxindex = index;
        }                       
    }
    
    synchronized public void checkMin(float detectormapvalue, int index){        
        if(index == minindex){
            min = detectormap[index];
        }
        
        if ( detectormapvalue < min ){
            min = detectormapvalue;         
            minindex = index;
        }
    }
    
    synchronized public void resetFilter() {            
        pixelbuffer.resetFilter();
        resetMaps();
    }        
    
    public void updateMap(int x, int y, float dv) {        
        
    }
    
    public void getFeatures(int x, int y, float dv, double threshold, PolarityEvent e) {
        
    }
    
    public int getIndex(int x, int y){
        int index = (x + (y*sizex));
        return index;
    }
}
