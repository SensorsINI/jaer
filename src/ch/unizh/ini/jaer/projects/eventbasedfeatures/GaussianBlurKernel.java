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
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.ImageDisplay;
import java.util.Collections;

/**This class implements a discretized Gaussian kernel, with sigma = 1.0, as defined in 
 * http://homepages.inf.ed.ac.uk/rbf/HIPR2/gsmooth.htm
 * 
 * @author Varad
 */


public class GaussianBlurKernel extends KernelMethod {

    KernelImplementor kernelimplement;
    BinaryFeatureDetector bindetect;
        
    public int sizex = chip.getSizeX();        
    public int sizey = chip.getSizeY(); 
    
    
    public float[][] GaussianMatrix = {   //discretized Gaussian kernel values with sigma = 1.4
        {1, 4, 7, 4, 1},
        {4, 16, 26, 16, 4},
        {7, 26, 41, 26, 7},
        {4, 16, 26, 16, 4},
        {1, 4, 7, 4, 1},        
    };
            
    public GaussianBlurKernel (AEChip chip, KernelImplementor kernelimplement) {    
        
        super(chip, kernelimplement);                
    } 
    
    public GaussianBlurKernel (AEChip chip, BinaryFeatureDetector bindetect) {    
        
        super(chip, bindetect);                
    } 
    
    @Override
    public void updateMap(int x, int y, float dv, double threshold){      //Method for Gaussian blurring 
        
        
            int xval = 0;
            int yval = 0;
            
            if( (x>1 && x<(sizex-2) ) && (y>1 && y<(sizey-2) )){

                for(int i = -2; i<3; i++){
                    xval = Math.abs(2-i);

                    for(int j = -2; j<3; j++){                            

                        yval = Math.abs(2-j);   // xval & yval now contain the indices of the entry in the Gaussian matrix
                                                // which is to be used for updating this neighbouring value of the current event                                                 
                         
                            detectormap[x+i][y+j] += ((dv)*(GaussianMatrix[xval][yval]));

                            checkMax(detectormap[x+i][y+j]);
                            checkMin(detectormap[x+i][y+j]);

                            float value = (float)((detectormap[x+i][y+j] - min)/(max - min)); 

                            grayvalue[ 3 * ((x+i) + ((y+j) * sizex))] = value; 
                            grayvalue[(3 * ((x+i) + ((y+j) * sizex))) + 1] = value; 
                            grayvalue[(3 * ((x+i) + ((y+j) * sizex))) + 2] = value;
                            
//                            if( detectormap[x+i][y+j] >= (0.8*max) && detectormap[x+i][y+j] <= max){
//                                
//                                Point candidate = new Point(x+i, y+j);
//                                keypoints.add(candidate);
//                            }
                    }
                }                
            }                    
    }
}
