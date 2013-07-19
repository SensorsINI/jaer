/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;

/** This class implements a discretized Laplacian of Gaussian kernel, with sigma = 1.4, as defined in 
 * http://homepages.inf.ed.ac.uk/rbf/HIPR2/gsmooth.htm
 * @author Varad
 */
public class LaplacianOfGaussianKernel extends ConvolutionKernelMethod {
    
    
    public int sizex = chip.getSizeX();        
    public int sizey = chip.getSizeY(); 
    
    public float[][] LoGMatrix = {
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
    
    public LaplacianOfGaussianKernel (AEChip chip, ConvolutionFeatureScheme featurescheme) {
        
        super(chip, featurescheme);                    
    }
    
    @Override
    public void updateMap(int x, int y, float dv){
        int xval = 0;
        int yval = 0;
        int xcoord = 0;
        int ycoord = 0;
        int localindex = 0;
        float localmax = 0;
        
        if( (x>3 && x<(sizex-4) ) && (y>3 && y<(sizey-4) )){
            for(int i = -4; i<5; i++){                
                xval = Math.abs(4-i);                
                for(int j = -4; j<5; j++){
                    int ind = getIndex(x+i, y+j);
                    yval = Math.abs(4-j);
                    
                    detectormap[ind] += ((dv)*(LoGMatrix[xval][yval]));
                    if(Math.abs(detectormap[ind]) > localmax) {
                        localmax = Math.abs(detectormap[ind]);
                        xcoord = x+i;
                        ycoord = y+j;
                        localindex = ind;
                    }
                    checkMax(Math.abs(localmax), ind);
                    float value = (float)((detectormap[ind] - min)/(max - min));                        
                    grayvalue[ 3*getIndex(x+i, y+j)] = value; 
                    grayvalue[(3*getIndex(x+i, y+j)) + 1] = value; 
                    grayvalue[(3*getIndex(x+i, y+j)) + 2] = value;           
                }
            }
        }
    }
    
    @Override
    synchronized public void getFeatures(int x, int y, float dv, double threshold, PolarityEvent e){               
        int xval = 0;
        int yval = 0;
        int xcoord = 0;
        int ycoord = 0;
        int localindex = 0;
        float localfeature = 0;

        if( (x>3 && x<(sizex-4) ) && (y>3 && y<(sizey-4) )){
            for(int i = -4; i<5; i++){
                
                xval = Math.abs(4-i);
                
                for(int j = -4; j<5; j++){
                    int ind = getIndex(x+i, y+j);
                    if(isKey[ind]){
                        isKey[ind] = false;
                        KeyPoint keyp = new KeyPoint(x+i, y+j);
                        keypoints.remove(keyp);
//                        keyp.isOnMap = false;
                    }
                    
                    yval = Math.abs(4-j);                    
                    detectormap[ind] += ((dv)*(LoGMatrix[xval][yval]));                                                                        
                    if(Math.abs(detectormap[ind]) > localfeature) {
                        localfeature = Math.abs(detectormap[ind]);
                        xcoord = x+i;
                        ycoord = y+j;
                        localindex = ind;
                    }                
                    checkMax(Math.abs(localfeature), ind);
                    checkMin(detectormap[ind], ind);
                    
                    float value = (float)((detectormap[ind] - min)/(max - min));                        
                    grayvalue[ 3*getIndex(x+i, y+j)] = value; 
                    grayvalue[(3*getIndex(x+i, y+j)) + 1] = value; 
                    grayvalue[(3*getIndex(x+i, y+j)) + 2] = value;                                                                        
                }
            }                                                                      
        }

        if(localfeature >= (threshold*max) && localfeature <= max){

            KeyPoint p = new KeyPoint(xcoord, ycoord);
            isKey[localindex] = true;
            keypoints.add(p);    
//            p.isOnMap = true;
        } 
    }   
}