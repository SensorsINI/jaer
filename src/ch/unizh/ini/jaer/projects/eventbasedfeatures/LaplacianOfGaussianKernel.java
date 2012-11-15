/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ImageDisplay;

/** This class implements a discretized Laplacian of Gaussian kernel, with sigma = 1.4, as defined in 
 * http://homepages.inf.ed.ac.uk/rbf/HIPR2/gsmooth.htm
 * @author Varad
 */
public class LaplacianOfGaussianKernel extends KernelMethod {
    
//    KernelImplementor kernelimplement;
//    ConvolutionFeatureDetector featuredetect;
    
    public int sizex = chip.getSizeX();        
    public int sizey = chip.getSizeY(); 
    
    public int[][] LoGMatrix = {
        {0, 1, 1, 2, 2, 2, 1, 1, 0},
        {1, 2, 4, 5, 5, 5, 4, 2, 1},
        {1, 4, 5, 3, 0, 3, 5, 4, 1},
        {2, 5, 3, -12, -24, -12, 3, 5, 2},
        {2, 5, 0, -24, -40, -24, 0, 5, 2},
        {2, 5, 3, -12, -24, -12, 3, 5, 2},
        {1, 4, 5, 3, 0, 3, 5, 4, 1},
        {1, 2, 4, 5, 5, 5, 4, 2, 1},
        {0, 1, 1, 2, 2, 2, 1, 1, 0},
    };
    
    public LaplacianOfGaussianKernel (AEChip chip, KernelImplementor kernelimplement) {
        
        super(chip, kernelimplement);                    
    }
    
    public LaplacianOfGaussianKernel (AEChip chip, ConvolutionFeatureDetector featuredetect) {
        
        super(chip, featuredetect);                    
    }
    
    @Override
    synchronized public void updateMap(int x, int y, float dv, double threshold){
               
            int xval = 0;
            int yval = 0;
                        
            if( (x>3 && x<(sizex-4) ) && (y>3 && y<(sizey-4) )){

                for(int i = -4; i<5; i++){
                    xval = Math.abs(4-i);

                    for(int j = -4; j<5; j++){

                        yval = Math.abs(4-j);
                        detectormap[x+i][y+j] += ((dv)*(LoGMatrix[xval][yval]));

                        checkMax(detectormap[x+i][y+j]);
                        checkMin(detectormap[x+i][y+j]);
                        
                        float value = (float)((detectormap[x+i][y+j] - min)/(max - min));                        
                        
                        grayvalue[ 3 * ((x+i) + ((y+j) * sizex))] = value; 
                        grayvalue[(3 * ((x+i) + ((y+j) * sizex))) + 1] = value; 
                        grayvalue[(3 * ((x+i) + ((y+j) * sizex))) + 2] = value;
                                                
                        if( detectormap[x+i][y+j] >= (threshold*max) && detectormap[x+i][y+j] <= max){
                                                            
                            Point candidate = new Point(x+i, y+j);
                            keypoints.add(candidate);
                        }                            
                    }
                }                                                                      
//                display.setEventPixmapNbd(x, y, 4, sizex, grayvalue);
        }
            
//        display.repaint();                               
    }            
}