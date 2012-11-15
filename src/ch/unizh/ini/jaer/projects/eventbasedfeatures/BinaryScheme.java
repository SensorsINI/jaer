/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.Point;
import java.util.ArrayList;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ImageDisplay;

/**
 *
 * @author Varad
 */
public class BinaryScheme {
    
    AEChip chip;
    public FilterChain filterchain;            
    PixelBuffer pixelbuffer;
    BinaryFeatureDetector bindetect;
    
    public double RelativeThreshold;
    
    public int sizex;
    public int sizey;
    
    public float max ;
    public float min ;
    
    public float[][] map;
    public float[] grayvalue ;
//    public ArrayList<Point> keypoints;
    
    ImageDisplay display;
    JFrame featureFrame;
    
    public BinaryScheme( AEChip chip, BinaryFeatureDetector bindetect){
        
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
        
        if ( map == null || map.length != chip.getSizeX() || map[0].length != chip.getSizeY() )
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
        
        map = new float[sizex][sizey];   
//        grayvalue = new float[3*sizex*sizey] ;
//        keypoints = new ArrayList<Point>();
    }
    
//    synchronized public void checkMax(float mapvalue){
//                       
//        if ( mapvalue > max ){
//            max = mapvalue;            
//        }                
//    }
//    
//    synchronized public void checkMin(float mapvalue){
//        
//        if ( mapvalue < min ){
//            min = mapvalue;            
//        }
//    }
    
    synchronized public void getFeatures(float[][] map){
        
    }
    
//    synchronized public void updateFeatures( ArrayList<Point> keypoints, double threshold){
//        
//        for( int i = 0; i < keypoints.size(); i++){
//            
//            if(!( (map[keypoints.get(i).x][keypoints.get(i).y] >= (threshold*max)) && (map[keypoints.get(i).x][keypoints.get(i).y] <= max))){
//                keypoints.remove(i);
//            }
//        }
//    }
    
    synchronized public void resetFilter() { 
//        System.out.print(map[64][64]+"\n");
        pixelbuffer.resetFilter();
        display.clearImage();        
        resetMaps();
    }    
    
}
