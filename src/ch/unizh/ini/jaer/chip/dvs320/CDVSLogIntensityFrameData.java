/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import java.util.concurrent.Semaphore;

/**
 * 
 * Holds the frame of log intensity values to be display for a cDVS chip with log intensity readout.
 * Double-buffers the frame data with a locking mechanism.
 * @author Tobi
 */
public class CDVSLogIntensityFrameData {

    public static final int WIDTH=cDVSTest20.SIZE_X_CDVS, HEIGHT=cDVSTest20.SIZE_Y_CDVS;
    private static final int NUMSAMPLES=WIDTH*HEIGHT;
    private int timestamp=0; // timestamp of starting sample
    
    private int[] data1=new int[NUMSAMPLES], data2=new int[NUMSAMPLES];
    
    /** Readers should access the current reading buffer. */
    public int[] currentReadingBuffer=data1;
    private int[] currentWritingBuffer=data2;
    private int writeCounter=0, readCounter=0;
    private Semaphore semaphore=new Semaphore(1);

    /** Acquire this semaphore to prevent buffer swapping. */
    public void acquire(){
        semaphore.acquireUninterruptibly();
    }
    /** Don't forget to release the semaphore. */
    public void release(){
        semaphore.release();
    }

    public int get(int x, int y){
        if (useOffChipCalibration)
            return (currentReadingBuffer[y+WIDTH*x]-calibData[y+WIDTH*x]);
        return currentReadingBuffer[y+WIDTH*x];
    }

    /** Put a value to the next writing position. Writes wrap around to the start position. */
    public void put(int val){
        currentWritingBuffer[writeCounter++]=val;
        if(writeCounter==NUMSAMPLES) writeCounter=0;
    }

    /** Swaps the current writing and reading buffers after acquiring the lock. */
    public void swapBuffers() {
        acquire();
        int[] tmp=currentReadingBuffer;
        currentReadingBuffer=currentWritingBuffer;
        writeCounter=0;
        currentWritingBuffer=tmp;
        release();
    }

    /**
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the buffer timestamp. 
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    private boolean useOffChipCalibration=false;

    /**
     * @return the useOffChipCalibration
     */
    public boolean isUseOffChipCalibration() {
        return useOffChipCalibration;
    }

    /**
     * @param useOffChipCalibration the useOffChipCalibration to set
     */
    public void setUseOffChipCalibration(boolean useOffChipCalibration) {
        this.useOffChipCalibration = useOffChipCalibration;
    }

    private int[] calibData=new int[NUMSAMPLES];

    /**
     * uses the current writing buffer as calibration data and subtracts the mean
     */
    public void setCalibData() {
        System.arraycopy(currentWritingBuffer,0,calibData,0,NUMSAMPLES);
        substractMean();
    }

     private void substractMean(){
         int mean=0;
         for (int i=0;i<NUMSAMPLES; i++)
         {
             mean+=calibData[i];
         }
         mean=mean/NUMSAMPLES;
         for (int i=0;i<NUMSAMPLES; i++)
         {
             calibData[i]-=mean;
         }
     }
}
