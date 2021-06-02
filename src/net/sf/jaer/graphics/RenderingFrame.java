/*
 * RenderingFrame.java
 *
 * Created on January 21, 2006, 6:30 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.graphics;

import java.util.Arrays;

/**
 * Encapsulates the rendered retina image  - used to store and retrieve rendered RGB pixel values and to efficiently iterate over them for clipping or normalization.
 *
 Access to the pixels will likely be sparse but rendering requires accessing all pixels.
 *
 * @author tobi
 */
final public class RenderingFrame {
    int sizeX, sizeY;
    float[] fr;
    float[] rgb=new float[3];
    final int RED_OFFSET=0, GREEN_OFFSET=1, BLUE_OFFSET=2;
    /** Creates a new instance of RenderingFrame */
    public RenderingFrame(final int sizex, final int sizey) {
        this.sizeX=sizex;
        this.sizeY=sizey;
        fr=new float[sizex*sizey];
    }
    
    final public void setRed(final int x,final int y, final float v){
        fr[y+sizeY*x+RED_OFFSET]=v;
    }
    final public void setGreen(final int x, final int y, final float v){
        fr[y+sizeY*x+GREEN_OFFSET]=v;
    }
    final public void setBlue(final int x, final int y, final float v){
        fr[y+sizeY*x+BLUE_OFFSET]=v;
    }
    final public float getRed(final int x, final int y){
        return fr[y+sizeY*x+RED_OFFSET];
    }
    final public float getGreen(final int x, final int y){
        return fr[y+sizeY*x+GREEN_OFFSET];
    }
    final public float getBlue(final int x, final int y){
        return fr[y+sizeY*x+BLUE_OFFSET];
    }
    final public void setRGB(final int x, final int y, final float[] v){
        setRed(x,y,v[0]);
        setGreen(x,y,v[1]);
        setBlue(x,y,v[2]);
    }
    
    void reset(final float gray){
        Arrays.fill(fr,gray);
    }
    
    void clip(){
        float f;
        for(int i=0;i<fr.length;i++){
            f=fr[i];
            if(f>1) f=1f; else if(f<0) f=0;
            fr[i]=f;
        }
    }
    
    void normalize(final float gray){
        float f;
        float max=Float.MIN_VALUE, min=Float.MAX_VALUE;
        for(int i=0;i<fr.length;i++){
            f=fr[i]-gray;
            if(f<min) min=f; else if(f>max) max=f;
        }
        float norm=(float)Math.max(Math.abs(min),Math.abs(max));
        norm=1/norm;
        for(int i=0;i<fr.length;i++){
            fr[i]=(fr[i]-gray)*norm+gray;
        }
    }
    
}
