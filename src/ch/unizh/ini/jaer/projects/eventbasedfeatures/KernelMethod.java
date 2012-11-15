/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ImageDisplay;

/**
 *
 * @author Varad
 */
public class KernelMethod {

    AEChip chip;
    public FilterChain filterchain;            
    PixelBuffer pixelbuffer;    
    KernelImplementor kernelimplement;
    ConvolutionFeatureDetector featuredetect;
    BinaryFeatureDetector bindetect;
    
    
    public double RelativeThreshold;
    
    public int sizex;
    public int sizey;
    
    public float max ;
    public float min ;
        
    public float[][] detectormap;
    public float[] grayvalue ;
    public ArrayList<Point> keypoints;
    
    ImageDisplay display;
    JFrame featureFrame;
    
    public KernelMethod(AEChip chip, KernelImplementor kernelimplement){
        
        this.chip = chip; 
        
        this.kernelimplement = kernelimplement;
        this.pixelbuffer = kernelimplement.pixelbuffer;
        this.filterchain = kernelimplement.filterchain;
        this.display = kernelimplement.display;
        this.featureFrame = kernelimplement.featureFrame;
        
       
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();

        
    }
    
    public KernelMethod(AEChip chip, ConvolutionFeatureDetector featuredetect){
        
        this.chip = chip; 
        
        this.featuredetect = featuredetect;
        this.pixelbuffer = featuredetect.pixelbuffer;
        this.filterchain = featuredetect.filterchain;
        this.display = featuredetect.display;
        this.featureFrame = featuredetect.featureFrame;
        this.RelativeThreshold = featuredetect.getRelativeThreshold();
       
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
 
    }
    
    public KernelMethod(AEChip chip, BinaryFeatureDetector bindetect){
        
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
        
        if ( detectormap == null || detectormap.length != chip.getSizeX() || detectormap[0].length != chip.getSizeY() )
            resetMaps();        
    }
    
    synchronized public void resetMaps(){
        
        int size = 128;
        display.setImageSize(size, size); // set dimensions of image      
        featureFrame.setVisible(true); // make the frame visible
        
        max = 1;
        min = 0;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        detectormap = new float[sizex][sizey];   
        grayvalue = new float[3*sizex*sizey] ;
        keypoints = new ArrayList<Point>();
    }

    synchronized public void checkMax(float detectormapvalue){
                       
        if ( detectormapvalue > max ){
            max = detectormapvalue;            
        }                
    }
    
    synchronized public void checkMin(float detectormapvalue){
        
        if ( detectormapvalue < min ){
            min = detectormapvalue;            
        }

    }
    
    synchronized public void resetFilter() {    
        pixelbuffer.resetFilter();
        display.clearImage();        
        resetMaps();
    }
    

    public void updateMap(int x, int y, float dv, double threshold) {
        
    }
    
    synchronized public void updateFeatures( ArrayList<Point> keypoints, double threshold){
        
        for( int i = 0; i < keypoints.size(); i++){
            
            if(!( (detectormap[keypoints.get(i).x][keypoints.get(i).y] >= (threshold*max)) && (detectormap[keypoints.get(i).x][keypoints.get(i).y] <= max))){
                keypoints.remove(i);
            }
        }
    }
    
//    public class Point{
//    
//        public int x, y;
//        public Point(int x, int y) {
//            this.x = x;
//            this.y = y;
//        }
//    }
    
}
