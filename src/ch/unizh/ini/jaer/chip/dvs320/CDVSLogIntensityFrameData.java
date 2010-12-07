/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import java.nio.IntBuffer;
import javax.media.opengl.GLDrawable;

/**
 * Holds the frame of log intensity values to be display for a cDVS chip with log intensity readout.
 * @author Tobi
 */
public class CDVSLogIntensityFrameData {

    public static final int WIDTH=64, HEIGHT=64;
    public int[] data1=new int[WIDTH*HEIGHT], data2=new int[WIDTH*HEIGHT];;
    public int[] currentWritingBuffer=data2, currentReadingBuffer=data1;
    private int count=0;
    private static final int NUMSAMPLES=WIDTH*HEIGHT;


    public void resetCounter(){
        count=0;
    }

    public int getCount(){
        return count;
    }

    public int getCurrentValue(){
        return currentReadingBuffer[count];
    }

    public int getValueAndIncrementCounter(){
        int v= currentReadingBuffer[count++];
        checkWrap();
        return v;
    }

    public int getValueAt(int x, int y){
        return currentReadingBuffer[x+WIDTH*y];
    }

    public void setValueAndIncrementCounter(int val){
        currentWritingBuffer[count++]=val;
        if(checkWrap()) swapBuffers();
    }

    /** Resets counter and returns true if counter was reset to zero.*/
    private boolean checkWrap(){
        if(count==NUMSAMPLES) {count=0; return true;}
        return false;
    }

    private void swapBuffers() {
        int[] tmp=currentWritingBuffer;
        currentWritingBuffer=currentReadingBuffer;
        currentReadingBuffer=tmp;
    }

}
