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
    private static int MAX_ADC_VALUE=1023;
    
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
        if (invertADCvalues)
        {
            if (useOffChipCalibration)
                return MAX_ADC_VALUE-(int)(gain[y+WIDTH*x]*(currentReadingBuffer[y+WIDTH*x]-offset[y+WIDTH*x]));
            return MAX_ADC_VALUE-currentReadingBuffer[y+WIDTH*x];
        } else
        {
            if (useOffChipCalibration)
                return ((int)gain[y+WIDTH*x]*(currentReadingBuffer[y+WIDTH*x]-offset[y+WIDTH*x]));
            return currentReadingBuffer[y+WIDTH*x];
        }
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

    private boolean invertADCvalues=false;

    private boolean useOffChipCalibration=false;

    private boolean twoPointCalibration=false;

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
    
    public void calculateCalibration() {
        if (twoPointCalibration) {
            int mean1 = getMean(calibData1);
            int mean2 = getMean(calibData2);
            for (int i = 0; i < NUMSAMPLES; i++) {
                gain[i] = ((float) (mean2 - mean1)) /  ((float) (calibData2[i] - calibData1[i]));
                offset[i]=(calibData1[i]-(int)(mean1/gain[i]));
            }
        } else {
            for (int i = 0; i < NUMSAMPLES; i++) {
                gain[i] = 1;
            }
            subtractMean(calibData1, offset);
        }
    }

    private int[] calibData1=new int[NUMSAMPLES];
    private int[] calibData2=new int[NUMSAMPLES];

    private float[] gain=new float[NUMSAMPLES];
    private int[] offset=new int[NUMSAMPLES];

    /**
     * uses the current writing buffer as calibration data and subtracts the mean
     */
    public void setCalibData1() {
        acquire();
        System.arraycopy(currentWritingBuffer,0,calibData1,0,NUMSAMPLES);
        release();
        calculateCalibration();
    }

    public void setCalibData2() {
        acquire();
        System.arraycopy(currentWritingBuffer,0,calibData2,0,NUMSAMPLES);
        release();
        calculateCalibration();
        //substractMean();
    }

    private int getMean(int[] dataIn)
    {
        int mean=0;
         for (int i=0;i<dataIn.length; i++)
         {
             mean+=dataIn[i];
         }
         mean=mean/dataIn.length;
         return mean;
    }

     private void subtractMean(int[] dataIn, int[] dataOut){
         int mean=getMean(dataIn);

         for (int i=0;i<dataOut.length; i++)
         {
             dataOut[i]=dataIn[i]-mean;
         }
     }

    /**
     * @return the invertADCvalues
     */
    public boolean isInvertADCvalues() {
        return invertADCvalues;
    }

    /**
     * @param invertADCvalues the invertADCvalues to set
     */
    public void setInvertADCvalues(boolean invertADCvalues) {
        this.invertADCvalues = invertADCvalues;
    }

    /**
     * @return the twoPointCalibration
     */
    public boolean isTwoPointCalibration() {
        return twoPointCalibration;
    }

    /**
     * @param twoPointCalibration the twoPointCalibration to set
     */
    public void setTwoPointCalibration(boolean twoPointCalibration) {
        this.twoPointCalibration = twoPointCalibration;
    }
}
