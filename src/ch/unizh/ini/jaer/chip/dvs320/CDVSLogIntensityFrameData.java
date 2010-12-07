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

    public static final int WIDTH=cDVSTest20.SIZE_X_CDVS, HEIGHT=cDVSTest20.SIZE_Y_CDVS;
    private static final int NUMSAMPLES=WIDTH*HEIGHT;

//    IntBuffer buf1=IntBuffer.allocate(NUMSAMPLES), buf2=IntBuffer.allocate(NUMSAMPLES);
//    public IntBuffer currentWritingBuffer=buf2, currentReadingBuffer=buf1;

    int[] data1=new int[NUMSAMPLES], data2=new int[NUMSAMPLES];
    public int[] currentWritingBuffer=data2, currentReadingBuffer=data1;
    int writeCounter=0, readCounter=0;

//    public int get(){
//        return currentReadingBuffer.get();
//    }

    public int get(int x, int y){
//        return currentReadingBuffer.get(x+WIDTH*y);
        return currentReadingBuffer[x+WIDTH*y];
    }

    public void put(int val){
//        currentWritingBuffer.put(val);
//        if(!currentWritingBuffer.hasRemaining()) swapBuffers();

    }

    public void swapBuffers() {
//        currentWritingBuffer.flip();
//        IntBuffer tmp=currentWritingBuffer;
//        currentWritingBuffer=currentReadingBuffer;
//        currentReadingBuffer=tmp;
//        currentWritingBuffer.clear();
    }

}
