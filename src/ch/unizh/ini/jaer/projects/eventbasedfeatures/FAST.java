/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.Point;
import java.util.Arrays;

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


public class FAST extends BinaryScheme {   
    
    public FASTPixel[][] fparray;
    
    public int sizex;
    public int sizey;

    public FAST (AEChip chip, BinaryFeatureDetector bindetect) {
        
         super(chip, bindetect);    
         
         sizex = chip.getSizeX();
         sizey = chip.getSizeY();
         fparray = new FASTPixel[sizex][sizey];
                 
         for (int u = 0; (u < sizex ); u++){
            for (int v = 0; (v < sizey ); v++){
                    fparray[u][v] = new FASTPixel( u, v );
            }                    
        }
    }
    
    public class FASTPixel{
        
        public int x, y;
        public int scoreP, scoreM;
        public boolean isCorner;
        public int[] polarity;
        public FASTPixel[] discPixels;
        
        public FASTPixel( int x, int y ){            
            
            this.x = x;
            this.y = y; 
            this.isCorner = false;
            this.scoreP = 0;
            this.scoreM = 0;
            this.polarity = new int[16];
            this.discPixels = new FASTPixel[4];
        }
    }
    
    
    public int wrapIndex( int a ){
            
        int b = 0;
        if(a > 15) b=a%16;
        if(a < 0) b=a+16;
        return b;            
    }
    
    public int getIndex(int x, int y){
        
        int index = (3 * (x + (y * sizex)));
        return index;
    }
    
    
    public FASTPixel[] getNbdArray(int x, int y){
        
        FASTPixel[] neighborhood = { 
                fparray[x][y+3], fparray[x+1][y+3], fparray[x+2][y+2], fparray[x+3][y+1], fparray[x+3][y],
                fparray[x+3][y-1], fparray[x+2][y-2], fparray[x+1][y-3], fparray[x][y-3], fparray[x-1][y-3],
                fparray[x-2][y-2], fparray[x-3][y-1], fparray[x-3][y], fparray[x-3][y+1], fparray[x-2][y+2],                               
                fparray[x-1][y+3]
            };
        
        return neighborhood;
    }
    
    
    
    @Override
    synchronized public void getFeatures( int x, int y ){ 

        if ( ((x > 5) && (x < sizex - 6)) && ( (y > 5) && (y < sizey - 6)) ){
            FASTPixel[] nbdArray = getNbdArray(x, y);                        
            
            //CHANGES AT THE EVENT LOCATION
            scoreCheck(x, y, nbdArray); 
            if((fparray[x][y].scoreP >= 12) || (fparray[x][y].scoreM >= 12)){
//                if(fparray[x][y].isCorner){                
                iterateCheck(x, y, nbdArray);
//                }
//                else{
//                    
//                }
            }
                        
            //ITERATE AND MAKE CHANGES IN THE EVENT NBD
            for (int c = 0; c < nbdArray.length; c++){
                
                int x_1 = nbdArray[c].x;
                int y_1 = nbdArray[c].y;
                
                if(fparray[x_1][y_1].scoreP >= 12 || fparray[x_1][y_1].scoreM >= 12){                                
                    
                    if(fparray[x_1][y_1].isCorner){                                       
                        if(!( (Arrays.asList(fparray[x_1][y_1].discPixels)).contains(fparray[x][y]) )){
                            iterateCheck(x_1, y_1, getNbdArray(x_1, y_1));
                        }
                    }
                    else{
                        iterateCheck(x_1, y_1, getNbdArray(x_1, y_1));                        
                    }
                }
            }     
        }
    }
    
    
    synchronized public void scoreCheck( int x, int y, FASTPixel[] nbdArray ){        
        
        int centerindex = getIndex(x, y);
        
        //ITERATE TO CHECK SCORE >= 12
        for( int i=0; i < nbdArray.length; i++ ){
            
            int x1 = nbdArray[i].x;
            int y1 = nbdArray[i].y;            
            int perindex = getIndex(x1, y1);
            
            if( grayvalue[perindex] > ((grayvalue[centerindex]) + bindetect.threshold) ){
                fparray[x][y].polarity[i] = 1;
                fparray[x][y].scoreP++;
                if(fparray[x1][y1].polarity[wrapIndex(i+8)] == 1){                    
                    fparray[x1][y1].scoreP--;
                    fparray[x1][y1].scoreM++;
                }
                else if(fparray[x1][y1].polarity[wrapIndex(i+8)] == 0){
                    fparray[x1][y1].scoreM++;
                }
            }
            
            else if( grayvalue[perindex] < ((grayvalue[centerindex]) - bindetect.threshold) ){
                fparray[x][y].polarity[i] = -1;
                fparray[x][y].scoreM++;
                if(fparray[x1][y1].polarity[wrapIndex(i+8)] == -1){                    
                    fparray[x1][y1].scoreM--;
                    fparray[x1][y1].scoreP++;
                }
                else if(fparray[x1][y1].polarity[wrapIndex(i+8)] == 0){
                    fparray[x1][y1].scoreP++;
                }
            }
            
            else{
                fparray[x][y].polarity[i] = 0;
                if(fparray[x1][y1].polarity[wrapIndex(i+8)] == 1){                    
                    fparray[x1][y1].scoreP--;
                }
                if(fparray[x1][y1].polarity[wrapIndex(i+8)] == -1){                    
                    fparray[x1][y1].scoreM--;
                }
            }
        }
    }
        
        
        
        synchronized public void iterateCheck( int x, int y, FASTPixel[] nbdArray ){
            
            int sP = 0; int sM = 0;
            int maxSP = 0; int maxSM = 0;   // NOT TO BE INCREMENTED. Update by comparison on polarity change or after processing last pixel
            int startcount = 0;
            int discindex = 0;
            boolean isStart = false;
            
            for( int j=0; j < nbdArray.length; j++ ){

                if(j==0){
                    if( fparray[x][y].polarity[j] == 1 ){
//                        pol = 1;
//                        startpol = 1;
                        startcount++;
                        sP++;
                        isStart = true;                        
                    }
                    else if( fparray[x][y].polarity[j] == -1 ){
//                        pol = -1;
//                        startpol = -1;
                        startcount++;
                        sM++;
                        isStart = true;                        
                    }
//                    prevpol = pol;
                    continue;
                }     

//                if(j==15){
//
//                    if( fparray[x][y].polarity[j] == 1 ){                        
////                        pol = 1;
//                        lastpol = 1;
//                    }
//                    else if( fparray[x][y].polarity[j] == -1 ){
////                        pol = -1;
//                        lastpol = -1;                        
//                    }        
//
//                }

                if(isStart){                                        

                    if( fparray[x][y].polarity[j] == 1 ){
//                        pol = 1;     

                        if((fparray[x][y].polarity[j-1] == 0))
                            sP++;

                        if(fparray[x][y].polarity[j] == fparray[x][y].polarity[j-1]){
                            startcount++;
                            sP++;
//                            maxSP++;
                        }
                        else{
                            if(fparray[x][y].polarity[j-1]!=0){
                                isStart = false;
                                discindex = j;
                                sP++;
                            }
                        }

                        if(j==15){
                            if(sP > maxSP)
                                maxSP = sP;
                        }
                    }                   

                    else if( fparray[x][y].polarity[j] == -1 ){

                        if((fparray[x][y].polarity[j-1] == 0))
                            sM++;

                        if(fparray[x][y].polarity[j] == fparray[x][y].polarity[j-1]){
                            startcount++;
                            sM++;
                        }
                        else{
                            if(fparray[x][y].polarity[j-1]!=0){
                                isStart = false;
                                discindex = j;
                                sM++;
                            }
                        }

                        if(j==15){
                            if(sM > maxSM)
                                maxSM = sM;
                        }
                    }

                    else{           //neither satisfied
                        isStart = false;
                        if(fparray[x][y].polarity[j-1] == 1){                            
                            if(sP>maxSP)
                                maxSP=sP;
                            sP = 0;
                        }
                        else if(fparray[x][y].polarity[j-1] == -1){
                            if(sM>maxSM)
                                maxSM=sM;
                            sM = 0;
                        }
                    }
                }



                else{

                    if( fparray[x][y].polarity[j] == 1 ){
//                        pol = 1;

                        if((fparray[x][y].polarity[j-1] == 0))
                            sP++;

                        if (fparray[x][y].polarity[j] == fparray[x][y].polarity[j-1]){
                            sP++;
                        }
                        else {
                            if(fparray[x][y].polarity[j-1]!=0){
                                if(sM > maxSM)
                                    maxSM = sM;
    //                            if(maxSP >= 12){
    //                                fparray[x][y].isCorner = true;
    //                                Point candidate = new Point(x, y);
    //                                keypointlist.add(candidate);
    //                                for( int k = j; k<j+4; k++){
    //                                    fparray[x][y].discPixels[k-j] = nbdArray[wrapIndex(k)];
    //                                }
    //                            }
                                sM=0;
                                sP++;
                            }
                        }


                        if(j==15){
                            if(sP > maxSP)
                                maxSP = sP;
                        }
                    }

                    else if( fparray[x][y].polarity[j] == -1 ){
//                        pol = -1;

                        if((fparray[x][y].polarity[j-1] == 0)) 
                            sM++;

                        if((fparray[x][y].polarity[j] == fparray[x][y].polarity[j-1])){
                            sM++;
//                            maxSM++;
                        }

                        else{
                            if(fparray[x][y].polarity[j-1]!=0){
                                if(sP > maxSP)
                                    maxSP = sP;

                                sP=0;
                                sM++;
                            }
                        }

                        if(j==15){
                            if(sM > maxSM)
                                maxSM = sM;
                        }
                    }

                    else{               // when neither condition is satisfied
                        if(fparray[x][y].polarity[j-1] == 1){                            
                            if(sP>maxSP)
                                maxSP=sP;
                            sP = 0;
                        }
                        else if(fparray[x][y].polarity[j-1] == -1){
                            if(sM>maxSM)
                                maxSM=sM;
                            sM = 0;
                        }                        
                    }
                }

                if((sP == 12) || (sM == 12)){

                    for( int k = j+1; k<=j+4; k++){
                        fparray[x][y].discPixels[k-j-1] = nbdArray[wrapIndex(k)];
                    }
                }

//                prevpol = pol;
            }


            
            if((maxSP >= 12) || (maxSM >= 12)){
                fparray[x][y].isCorner = true;
                Point candidate = new Point(x, y);
                keypointlist.add(candidate);
            }
            else{
                Point px = new Point(x, y);
                if(keypointlist.contains(px)){
                    int pxindex = keypointlist.indexOf(px);
                    keypointlist.remove(pxindex);
                    fparray[x][y].isCorner = false;
                }

            }


            if(fparray[x][y].polarity[0] == fparray[x][y].polarity[15]){
                if(fparray[x][y].polarity[0] == 1){

                    if( startcount+sP >= 12 ){
                        fparray[x][y].isCorner = true;
                        Point candidate = new Point(x, y);
                        keypointlist.add(candidate);
                        for(int m = discindex; m < discindex+4; m++ ){
                            fparray[x][y].discPixels[m-discindex] = nbdArray[wrapIndex(m)];
                        }
                    }
                    else{
                        Point px = new Point(x, y);
                        if(keypointlist.contains(px)){
                            int pxindex = keypointlist.indexOf(px);
                            keypointlist.remove(pxindex);
                            fparray[x][y].isCorner = false;
                        }
                    }
                }
                else if (fparray[x][y].polarity[0] == -1){

                    if( startcount+sM >= 12 ){
                        fparray[x][y].isCorner = true;
                        Point candidate = new Point(x, y);
                        keypointlist.add(candidate);
                        for(int m = discindex; m < discindex+4; m++ ){
                            fparray[x][y].discPixels[m-discindex] = nbdArray[wrapIndex(m)];
                        }
                    }
                    else{
                        Point px = new Point(x, y);
                        if(keypointlist.contains(px)){
                            int pxindex = keypointlist.indexOf(px);
                            keypointlist.remove(pxindex);
                            fparray[x][y].isCorner = false;
                        }
                    }
                }
            }
    }
}
    
    