/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import java.util.concurrent.Semaphore;

/**
 * Holds the frame of log intensity values to be display for a cDVS chip with log intensity readout.
 * @author Tobi
 */
public class CDVSLogIntensityFrameData {

    public static final int WIDTH=cDVSTest20.SIZE_X_CDVS, HEIGHT=cDVSTest20.SIZE_Y_CDVS;
    private static final int NUMSAMPLES=WIDTH*HEIGHT;
    private int timestamp=0; // timestamp of starting sample
    
//    IntBuffer buf1=IntBuffer.allocate(NUMSAMPLES), buf2=IntBuffer.allocate(NUMSAMPLES);
//    public IntBuffer currentWritingBuffer=buf2, currentReadingBuffer=buf1;

    int[] data1=new int[NUMSAMPLES], data2=new int[NUMSAMPLES];
    public int[] currentWritingBuffer=data2, currentReadingBuffer=data1;
    int writeCounter=0, readCounter=0;
    Semaphore semaphore=new Semaphore(1);

    public void acquire(){
        semaphore.acquireUninterruptibly();
    }
    public void release(){
        semaphore.release();
    }

//    public int get(){
//        return currentReadingBuffer.get();
//    }

    public int get(int x, int y){
//        return currentReadingBuffer.get(x+WIDTH*y);
        return currentReadingBuffer[y+WIDTH*x];
    }

    public void put(int val){
//        currentWritingBuffer.put(val);
//        if(!currentWritingBuffer.hasRemaining()) swapBuffers();
        currentWritingBuffer[writeCounter++]=val;
        if(writeCounter==NUMSAMPLES) writeCounter=0;
    }

    public void swapBuffers() {
        acquire();
        int[] tmp=currentReadingBuffer;
        currentReadingBuffer=currentWritingBuffer;
        //System.out.println(writeCounter);
        writeCounter=0;
        currentWritingBuffer=tmp;
        release();
//        currentWritingBuffer.flip();
//        IntBuffer tmp=currentWritingBuffer;
//        currentWritingBuffer=currentReadingBuffer;
//        currentReadingBuffer=tmp;
//        currentWritingBuffer.clear();
    }

    /**
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

}
