/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Varad
 */
public class SQUARECorner extends DescriptorScheme {
        
    public SQUARECorner(AEChip chip, ConvolutionFeatureScheme featurescheme){
        super(chip, featurescheme);   
        size = 5;
        descPixels = new int[size];
        index = new int[size][size];
        createLookUpTable();                
    }    
    
    @Override
    synchronized public void constructKeyPointDescriptor(KeyPoint kp) {
        descPixels[0] = getIndex(kp.x, kp.y);
        descPixels[1] = getIndex(kp.x+1, kp.y+1);
        descPixels[2] = getIndex(kp.x+1, kp.y-1);
        descPixels[3] = getIndex(kp.x-1, kp.y-1);
        descPixels[4] = getIndex(kp.x-1, kp.y+1);
        
        for(int i = 0; i < size-1; i++){           
            int indexi = descPixels[i];
            for(int j = i+1; j < size; j++){
                int indexj = descPixels[j];    
                if((featurescheme.detector.grayvalue[3*indexi] > featurescheme.detector.grayvalue[3*indexj]) && (i<j)){
                    kp.descriptorString[index[i][j]] = true;                          
                }
            }   
        } 
    }    

    @Override
    public void createLookUpTable() {
        int k = 0;        
        for (int i = 0; i < size; i++){
            for(int j = i+1; j < size ; j++){                
                index[i][j] = k;
                index[j][i] = k++;
            }
        }
    }
}
