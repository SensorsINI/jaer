package au.edu.wsu;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.Color;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.JFrame;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;


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
    * Compute polarization from the original intensity image, and display the results in a 3n * m image 
    * where [:n, :] corresponds to the DoP, [n:2n, :] corresponds to the legends and [2n:, :] corresponds to the AoP  
    */
    public static void computePolarization(float[] intensity, float[] apsDisplayPixmapBuffer, 
            int[] indexf0, int[] indexf45, int[] indexf90, int[] indexf135, int height, int width){
        double s0, s1, s2, aop, dop;
        int aop_rgb, dop_rgb;
        int idx;
        int offset = (width / 2) * (height / 2) * 6;
        for(int x = 0; x < width; x +=2){
            for(int y = 0; y < height; y+=2){
                idx = getIndex(x, y, width);
                s0 =  0.5 * (intensity[indexf0[idx]] + intensity[indexf135[idx]] + intensity[indexf90[idx]] + intensity[indexf45[idx]]);
                s1 = intensity[indexf0[idx]] - intensity[indexf90[idx]]; 
                s2 = intensity[indexf45[idx]] - intensity[indexf135[idx]];
                if( s0 > 0){
                    dop = Math.sqrt(s1*s1 + s2*s2) / s0;
                    aop = (Math.atan2(s2, s1))/ 2.0f ;
                }else{
                    dop = 0;
                    aop =0;
                }

                // Conversion HSV -> RGB
                idx = x / 2 + y / 2 * width / 2;
                
                aop_rgb = Color.HSBtoRGB((float)aop, 0.9f, 0.9f);
                dop_rgb = Color.HSBtoRGB((float)dop*0.5f, 0.9f, 0.9f);
                
                apsDisplayPixmapBuffer[3 * idx] = (float)((dop_rgb>>16)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1] = (float)((dop_rgb>>8)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2] = (float)((dop_rgb>>0)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[3 * idx + offset] = (float)((aop_rgb>>16)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 1 + offset] = (float)((aop_rgb>>8)&0xFF) / 255.0f;
                apsDisplayPixmapBuffer[(3 * idx) + 2 + offset] = (float)((aop_rgb>>0)&0xFF) / 255.0f;
                    
            }
        }
    }
    
    
        /**
    * Compute polarization from the log intensity image, and display the results in a 3n * m image 
    * where [:n, :] corresponds to the DoP, [n:2n, :] corresponds to the legends and [2n:, :] corresponds to the AoP  
    */
    public static void computePolarizationLog(float[] intensity, float[] apsDisplayPixmapBuffer, 
            int[] indexf0, int[] indexf45, int[] indexf90, int[] indexf135, int height, int width){
        double s0, s1, s2, aop, dop;
        int aop_rgb, dop_rgb;
        int idx;
        int offset = (width / 2) * (height / 2) * 6;
        for(int x = 0; x < width; x +=2){
            for(int y = 0; y < height; y+=2){
                idx = getIndex(x, y, width);
                s0 =  0.5 * (Math.exp(intensity[indexf0[idx]]) + Math.exp(intensity[indexf135[idx]]) + Math.exp(intensity[indexf90[idx]]) + Math.exp(intensity[indexf45[idx]]));
                s1 = Math.exp(intensity[indexf0[idx]]) - Math.exp(intensity[indexf90[idx]]); 
                s2 = Math.exp(intensity[indexf45[idx]]) - Math.exp(intensity[indexf135[idx]]);
                if( s0 > 0){
                    dop = Math.sqrt(s1*s1 + s2*s2) / s0;
                    aop = (Math.atan2(s2, s1))/ 2.0f ;
                }else{
                    dop = 0;
                    aop =0;
                }

                // Conversion HSV -> RGB
                idx = x / 2 + y / 2 * width / 2;
                
                aop_rgb = Color.HSBtoRGB((float)aop, 0.9f, 0.9f);
                dop_rgb = Color.HSBtoRGB((float)dop*0.5f, 0.9f, 0.9f);
                
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