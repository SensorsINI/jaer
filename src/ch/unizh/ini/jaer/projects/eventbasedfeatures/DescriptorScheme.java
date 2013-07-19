/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;

/** The superclass of all descriptor schemes - contains
 * common classes and methods for these descriptors
 *
 * @author Varad
 */
public abstract class DescriptorScheme extends FeatureMethod{
    
    AEChip chip;
    public FilterChain filterchain;            
    PixelBuffer pixelbuffer;
    ConvolutionFeatureScheme featurescheme;
    
    public int sizex;
    public int sizey;
    
    public int size;
    public int[][] index;
    public int[] descPixels;
    public int[] descMap;
    
    public DescriptorScheme(AEChip chip, ConvolutionFeatureScheme featurescheme){
        
        this.chip = chip; 
        
        this.featurescheme = featurescheme;
        this.pixelbuffer = featurescheme.pixelbuffer;
        this.filterchain = featurescheme.filterchain;
       
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
    }
        
    public int getIndex(int x, int y){
        int index = (x + (y*sizex));
        return index;
    }
    
    public abstract void constructKeyPointDescriptor(KeyPoint kp);  
        
    public void createLookUpTable(){        
    }
    
    public void updateDescriptorMap(KeyPoint kp, EventPacket in){
        
    }
}
