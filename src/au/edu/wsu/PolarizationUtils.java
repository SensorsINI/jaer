package au.edu.wsu;

import java.awt.Color;

interface FloatFunction {
  Float run(Float in);
}

public class PolarizationUtils{

    /**
     * For every position in the image, returns the corresponding subpixels inside a 
     * macro pixel
     * indexfA corresponds to the index of the polarizer A
     */
    public static void fillIndex(int[] indexf0, int[] indexf45, int[] indexf90, int[] indexf135, int height, int width){
        int idx;
        for(int x = 0; x < width; x +=1){
            for(int y = 0; y < height; y+=1){
                idx = x + y * width;
                if(x % 2 ==0){
                    if(y % 2 ==0){
                        indexf0[idx] = idx;
                        indexf45[idx] = idx+1;
                        indexf90[idx] = idx+width+1;
                        indexf135[idx] = idx+width;
                    }else{
                        indexf0[idx] = idx-width;
                        indexf45[idx] = idx -width +1;
                        indexf90[idx] = idx+1;
                        indexf135[idx] = idx;
                    }                        
                }else{
                    if(y % 2 == 0){
                        indexf0[idx] = idx-1;
                        indexf45[idx] = idx;
                        indexf90[idx] = idx+width;
                        indexf135[idx] = idx+width-1;
                    }else{
                        indexf0[idx] = idx-width-1;
                        indexf45[idx] = idx-width;
                        indexf90[idx] = idx;
                        indexf135[idx] = idx-1;
                    }
                }
            }
        }
    }
    
    /**
     * returns the index <code>y * width + x</code> into pixel arrays for a
     * given x,y location where x is horizontal address and y is vertical and it
     * starts at lower left corner with x,y=0,0 and x and y increase to right
     * and up.
     *
     * @param x
     * @param y
     * @param idx the array index
     * @see #getWidth()
     * @see #getHeight()
     */
    public static int getIndex(final int x, final int y, int width) {
        return (y * width) + x;
    }
    
    /**
    *  Compute polarization (AoP, DoP)
    * args: 
    * intensity: light intensity
    * aop, dop: buffers where the Aop/Dop will be updated (must be height x width // 4)
    * map: function to map the intensity: if the intensity comes from a log sensor, 
    * then this function must be the exp, otherwise it's the identity
    * indexfX: index of the neighbour pixels of (i, j) inside his macro pixel
    */
    public static void computeAoPDoP(float[] intensity, float[] aop, float[] dop, FloatFunction map,
            int[] indexf0, int[] indexf45, int[] indexf90, int[] indexf135, int height, int width){
        
        int size_pola = height * width / 4;
        int size = height * width;
        int idx;
        float s0, s1, s2;
        if( aop.length != size_pola || dop.length != size_pola){
            throw new IllegalArgumentException("Aop/Dop size should be height * width / 4");
        }
        if( indexf0.length != size || indexf45.length != size || indexf90.length != size || indexf135.length != size){
            throw new IllegalArgumentException("Index size should be height * width");
        }
        // Compute AoP and DoP
        for(int x = 0; x < width; x +=2){
            for(int y = 0; y < height; y+=2){
                idx = getIndex(x, y, width);
                s0 = (map.run(intensity[indexf0[idx]]) + map.run(intensity[indexf135[idx]]) + map.run(intensity[indexf90[idx]]) + map.run(intensity[indexf45[idx]]))/2;
                s1 = map.run(intensity[indexf0[idx]]) - map.run(intensity[indexf90[idx]]); 
                s2 = map.run(intensity[indexf45[idx]]) - map.run(intensity[indexf135[idx]]);
                idx = x / 2 + y / 2 * width / 2;
                if( s0 > 0){
                    dop[idx] = (float) Math.sqrt(s1*s1 + s2*s2) / s0;
                    aop[idx] = (float) (Math.atan2(s2, s1)/ (2.0 * Math.PI) + 0.5);
                }else{
                    aop[idx] = 0;
                    dop[idx] = 0;
                }
            }
        }
    }
    
    
    /**
    * Display the polarization, and display the results in a 3n * m image 
    * where [:n, :] corresponds to the DoP, [n:2n, :] corresponds to the legends and [2n:, :] corresponds to the AoP  
    */
    public static void setDisplay(float[] apsDisplayPixmapBuffer, float[] aop, float[] dop, int height, int width){
        int aop_rgb, dop_rgb;
        int idx;
        int offset = (width / 2) * (height / 2) * 6;
        for(int x = 0; x < width; x +=2){
            for(int y = 0; y < height; y+=2){
                idx = x / 2 + y / 2 * width / 2;  
                aop_rgb = Color.HSBtoRGB(aop[idx], 0.9f, 0.9f);
                dop_rgb = Color.HSBtoRGB(dop[idx] * 0.5f, 0.9f, 0.9f);
                apsDisplayPixmapBuffer[3 * idx] = (float)((dop_rgb>>16)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1] = (float)((dop_rgb>>8)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2] = (float)((dop_rgb>>0)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[3 * idx + offset] = (float)((aop_rgb>>16)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1 + offset] = (float)((aop_rgb>>8)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2 + offset] = (float)((aop_rgb>>0)&0xFF) / 255.0f;       
            }
        }
    }
    
    public static void drawLegend(float[] apsDisplayPixmapBuffer, int height, int width){
        int idx = 0;
        int offset = (width / 2) * (height / 2) * 6;
        // Display scales 
        // AOP
        float x_0 = width/2;
        float y_0 = height/2 + 30.0f;
        int x, y;
        int aop_rgb, dop_rgb;
        for(float angle = -180.0f; angle < 180.0f; angle +=1.0f){
            for(float r = 5; r < 25; r+=1){
                x = (int)( x_0 + r * Math.cos(angle / 180.0f * Math.PI));
                y = (int)(y_0 + r * Math.sin(angle / 180.0f * Math.PI));
                idx = (x / 2) + (width / 2) * (y / 2);

                aop_rgb  = Color.HSBtoRGB(angle/180, 0.9f, 0.9f);
                apsDisplayPixmapBuffer[3 * idx + offset/2] = ((float)((aop_rgb>>16)&0xFF)) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1 + offset/2] = ((float)((aop_rgb>>8)&0xFF))/ 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2 + offset/2] = ((float)(aop_rgb&0xFF))/ 255.0f;

            }
        }

        // DOP
        for(x = width / 2 - 50; x < width/2 + 50; x +=1){
            for(y = 50; y < 80; y+=1){
                idx = (x / 2) + (width / 2) * (y / 2);
                dop_rgb  = Color.HSBtoRGB((x - width / 2 + 50)/100.0f * 0.5f, 0.9f, 0.9f);
                apsDisplayPixmapBuffer[3 * idx + offset/2] = ((float)((dop_rgb>>16)&0xFF)) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1 + offset/2] = ((float)((dop_rgb>>8)&0xFF))/ 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2 + offset/2] = ((float)(dop_rgb&0xFF))/ 255.0f;      
            }
        }
    }

}