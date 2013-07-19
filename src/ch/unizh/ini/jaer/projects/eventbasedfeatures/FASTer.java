/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import net.sf.jaer.chip.AEChip;

//  NON MAXIMAL SUPPRESSION???


/** This class implements the FAST algorithm by using the 12 neighboring pixels to evaluate 'cornerness'
 *
 * Implementation: 
 * A FASTPixel array is created - the FASTPixel class contains the pixel's co-ordinates, corner score,
 * the array of its 12 contiguous pixels, an array containing the surrounding pixels of it's Bresenham circle (radius 3)etc.
 * 
 * When an event comes in, based on its updated map value, it has to be classified/de-classified as a corner - and, thus too,
 * each of the pixels in it's Bresenham circle. 
 * The scores are updated by +1 if the map value > central pixel value + threshold, -1 if map value < central pixel value - threshold
 * and not updated if neither of these criterion are satisfied
 * 
 * Starting from pixel 1 of the circle, a loop is run to check if a contiguous array of a single scheme is found. As soon
 * as a discontinuity is obtained, the loop is run from the point of discontinuity (as there is no point in checking the
 * pixels between the initial start and the discontinuity). If such an array is found, the point is allotted corner status.
 * 
 * The Bresenham circle pixels, if they are corners, might be affected by the event, based on the scheme of it's contiguous 
 * array pixels viz. if the event is part of it's contiguous pixels array (all of which are brighter than the central) and the
 * event only increases the map value at it's position, no change will be observed. But if not, then it's cprner status has to be
 * removed and checked all over again.
 * 
 * @author Varad
 */ 


public class FASTer extends BinaryScheme {   
    
    public static short FAST_CIRCLE_RADIUS = 3;
    public static short FAST_CIRCLE_SIZE = 16;
    public static short FAST_CIRCLE_SEGMENT_LENGTH = 12;
    
    public FASTPixel[] fastpixels;
    
    public int size;

    public FASTer (AEChip chip, BinaryFeatureDetector bindetect) {
        
         super(chip, bindetect);    
         
         sizex = chip.getSizeX();
         sizey = chip.getSizeY();
         size = (int)(sizex*sizey);
         fastpixels = new FASTPixel[size];
                 
         for (int i = 0; i < sizex ; i++){
             for (int j = 0; j < sizey; j++){
                 fastpixels[getIndex(i,j)] = new FASTPixel(i,j);  
             }               
         }
    }
    
    public class FASTPixel{
        
        public int index;
        public int[] circleIndices;
        public boolean isCorner;
        
        public FASTPixel(int x, int y){            
            
            this.index = getIndex(x,y);
            this.circleIndices = getCircleIndices(x,y);
            this.isCorner = false;
        }
        
        public float getCirclePixelValue(int circleIndex){
            return grayvalue[3*circleIndices[circleIndex]];
        }
    }
    
    public final int getIndex(int xc, int yc){
        int x = xc;
        int y = yc;
        if(x < 0) x = 0;
        if(x >= sizex) x = sizex-1;
        if(y < 0) y = 0;
        if(y <= sizey) y = sizey-1;
        int index = (int)(x + (y*sizex));
        return index;
    }
    
    
    public int[] getCircleIndices(int x, int y){
        
        int[] circle = { 
                getIndex(x, y+3), getIndex(x+1, y+3), getIndex(x+2, y+2), getIndex(x+3, y+1), 
                getIndex(x+3, y), getIndex(x+3, y-1), getIndex(x+2, y-2), getIndex(x+1, y-3), 
                getIndex(x, y-3), getIndex(x-1, y-3), getIndex(x-2, y-2), getIndex(x-3, y-1), 
                getIndex(x-3, y), getIndex(x-3, y+1), getIndex(x-2, y+2), getIndex(x-1, y+3)
            };
        
        return circle;
    }
    
    
    
    @Override
    synchronized public void getFeatures( int x, int y ){ 

        int index = getIndex(x,y);
        FASTPixel pixel = fastpixels[index];
        float pixelValue = grayvalue[index*3];
        
        if ( (x >= FAST_CIRCLE_RADIUS) && (x < sizex - FAST_CIRCLE_RADIUS) && (y >= FAST_CIRCLE_RADIUS) && (y < sizey - FAST_CIRCLE_RADIUS) ){                       
            
            //initialize iteration
            short score = 1;
            short maxScore = score;
            float upperBoundry = (float)(pixelValue+bindetect.threshold);
            float lowerBoundry = (float)(pixelValue-bindetect.threshold);
            short polarity = getPolarity(pixel.getCirclePixelValue(0), lowerBoundry, upperBoundry);
            short firstPolarity = polarity;
            short prevPolarity = firstPolarity;
            boolean firstSegment = true;
            short firstScore = 1;
            
            //iterate over circle
            for(int i = 1; i < FAST_CIRCLE_SIZE; i++){
                polarity = getPolarity(pixel.getCirclePixelValue(i), lowerBoundry, upperBoundry);
                if(polarity == prevPolarity){
                    score++;
                    if(firstSegment)firstScore++;
                    if(score>maxScore && polarity != 0){
                        maxScore = score;
                    }
                }else{
                    score = 1;
                    firstSegment = false;
                }
                prevPolarity = polarity;
            }
            
            if(polarity == firstPolarity && polarity != 0)score+=firstScore;
            if(score>maxScore)maxScore = score;
            
            pixel.isCorner = (maxScore >= FAST_CIRCLE_SEGMENT_LENGTH)?true:false;
        }
    }
    
    private short getPolarity(float value, float low, float up){
        short result = 0;
        if(value<low) result = -1;
        if(value>up) result = 1;
        return result;
    }
}
    
    