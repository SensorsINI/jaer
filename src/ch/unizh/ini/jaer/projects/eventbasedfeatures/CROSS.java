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
public class CROSS extends DescriptorScheme{
        
    public CROSS(AEChip chip, ConvolutionFeatureScheme featurescheme){
        super(chip, featurescheme);   
        size = 5;
        descPixels = new int[size];
        index = new int[size][size];
        createLookUpTable();
    }
    
    @Override
    public void constructKeyPointDescriptor(KeyPoint kp) {
        descPixels[0] = getIndex(kp.x, kp.y);
        descPixels[1] = getIndex(kp.x, ((kp.y)+1));
        descPixels[2] = getIndex(((kp.x)+1), kp.y);
        descPixels[3] = getIndex(kp.x, ((kp.y)-1));
        descPixels[4] = getIndex(((kp.x)-1), kp.y);
            
        for(int i = 0; i < size-1; i++){           
            int indexi = descPixels[i];
            for(int j = i+1; j < size; j++){
                int indexj = descPixels[j];    
                if((featurescheme.detector.grayvalue[3*indexi] > featurescheme.detector.grayvalue[3*indexj]) && (i<j)){
                    kp.descriptorString[index[i][j]] = true;                          
                }
            }   
        }        
//        for (int k = 0; k < kp.descriptorString.length; k++){
//            System.out.print(kp.descriptorString[k]+"\t");
//        }
//        System.out.print("\n");
    }
    
    @Override
    public void createLookUpTable(){
        int k = 0;        
        for (int i = 0; i < size; i++){
            for(int j = i+1; j < size ; j++){                
                index[i][j] = k;
                index[j][i] = k++;
            }
        }
        
//        for( int i = 0; i < 5; i++){
//            for( int j = 0; j < 5; j++ ){
//                System.out.print(index[i][j]+"\t");
//            }
//            System.out.print("\n");
//        }
    }
}
    
    