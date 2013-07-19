/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import net.sf.jaer.chip.AEChip;

/**This class implements a discretized Gaussian kernel, with sigma = 1.0, as defined in 
 * http://homepages.inf.ed.ac.uk/rbf/HIPR2/gsmooth.htm
 * 
 * @author Varad
 */


public class GaussianBlurKernel extends ConvolutionKernelMethod {

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
    public void updateMap(int x, int y, float dv){      //Method for Gaussian blurring 
        
        int xval = 0;
        int yval = 0;

        if( (x>1 && x<(sizex-2) ) && (y>1 && y<(sizey-2) )){
            for(int i = -2; i<3; i++){
                xval = Math.abs(2-i);

                for(int j = -2; j<3; j++){                            

                    yval = Math.abs(2-j);   // xval & yval now contain the indices of the entry in the Gaussian matrix
                                            // which is to be used for updating this neighbouring value of the current event                                                                                          
                    int ind = getIndex(x+i, y+j);
                    detectormap[ind] += ((dv)*(GaussianMatrix[xval][yval]));

                    checkMax(detectormap[ind], ind);
                    checkMin(detectormap[ind], ind);

                    float value = (float)((detectormap[ind] - min)/(max - min)); 
                    ind = ind*3;
                    grayvalue[ind++] = value; 
                    grayvalue[ind++] = value; 
                    grayvalue[ind] = value;
                }
            }                
        }                    
    }
}
