/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.Point;
import java.util.ArrayList;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;

/** Super class containing all required methods for implementing binary feature detection schemes
 *  viz. FAST, FREAK etc.
 * 
 * @author Varad
 */
public class BinaryScheme {
    
    AEChip chip;
    public FilterChain filterchain;            
    PixelBuffer pixelbuffer;
    BinaryFeatureDetector bindetect;
    
    
    public int sizex;
    public int sizey;
    
    public float[] map;
    public float[] grayvalue ;
    public ArrayList<Point> keypointlist;
    
//    ImageDisplay display;
//    JFrame featureFrame;
    
    public BinaryScheme( AEChip chip, BinaryFeatureDetector bindetect){
        
        this.chip = chip; 
        
        this.bindetect = bindetect;
        this.pixelbuffer = bindetect.pixelbuffer;
        this.filterchain = bindetect.filterchain;
//        this.display = bindetect.display;
//        this.featureFrame = bindetect.featureFrame;
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
    }
    
    synchronized public void checkMaps(){
        
        if ( map == null || map.length != (chip.getSizeX()*chip.getSizeY()) )
            resetMaps();        
    }
    
    synchronized public void resetMaps(){        

        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        bindetect.kernel.resetMaps();
        map = bindetect.kernel.detectormap;
        grayvalue = bindetect.kernel.grayvalue;
        keypointlist = new ArrayList<Point>();
    }
    
    
    synchronized public void getFeatures( int x, int y ){
        
    }
    
    
    synchronized public void resetFilter() { 
        bindetect.kernel.resetFilter();
//        pixelbuffer.resetFilter();
//        display.clearImage();        
        resetMaps();
    }    
    
}
