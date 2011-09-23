/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cADCSamples.ChannelBuffer;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

/**
 * 
 * Holds the frame of ADC samples to be displayed for CochleaAMS1c.
 * Double-buffers the samples with a locking mechanism.
 * Includes option for sync input to reset sample counter to start of array, or allows larger number of samples for continuous recording of 
 * data not using scanner with its sync output.
 * @author Tobi
 */
final public class CochleaAMS1cADCSamples {
    
    final static Logger log = Logger.getLogger("CochleaAMS1c");
//    public static final int WIDTH = 64, HEIGHT = 1;
    public static final int MAX_NUM_SAMPLES = 100000; // fixed max size now, with sync() call to reset to start of array // WIDTH * HEIGHT;
    public static final int NUM_CHANNELS = 4;
    public static final int MAX_ADC_VALUE = 1023;
    DataBuffer data1 = new DataBuffer(), data2 = new DataBuffer();
    /** Readers should access the current reading buffer. */
    public DataBuffer currentReadingDataBuffer = data1;
    private DataBuffer currentWritingDataBuffer = data2;
    private Semaphore semaphore = new Semaphore(1);
    private final int SCAN_LENGTH = 64;
    private boolean syncDetected=false;  // we set this flag if we see a sync, reset if no sync by SCAN_LENGTH+1

    /**
     * If a sync input is supplied true sometime, isSyncDetected will return true for the next SCAN_LENGTH samples.
     * This method allows display of the data to be synchronized to the scanner rather than by time.
     * 
     * @return the syncDetected
     */
    public boolean isSyncDetected() {
        return syncDetected;
    }

    /**
     * @param syncDetected the syncDetected to set
     */
    public void setSyncDetected(boolean syncDetected) {
        this.syncDetected = syncDetected;
    }

    /** A single samples */
    public class ADCSample {
        
        int data, time;
    }

    /** A buffer for a single channel of the ADC which holds an array of samples in the ADCSample array. */
    final public class ChannelBuffer {
        
        final int channel;
        public ADCSample[] samples = new ADCSample[MAX_NUM_SAMPLES];
        private int writeCounter = 0;
        
        public ChannelBuffer(final int channel) {
            this.channel = channel;
            for (int i = 0; i < MAX_NUM_SAMPLES; i++) {
                samples[i] = new ADCSample();
            }
        }

        /** Call this when scanning and we see a sync output active from scanner. */
        public void sync() {
            clear();
        }
        
        private void clear() {
            writeCounter = 0;
        }
        
        public boolean hasData() {
            return writeCounter > 0;
        }
        
        private void put(int time, int val, boolean sync) {
            if (writeCounter >= MAX_NUM_SAMPLES - 1) {
//            log.info("buffer overflowed - missing start frame bit?");
                return;
            }
            if(sync){
                writeCounter=0;
                setSyncDetected(true);
            }
            if(!sync && writeCounter>SCAN_LENGTH){
                setSyncDetected(false);
            }
            samples[writeCounter].time = time;
            samples[writeCounter].data = val;
            writeCounter++;
            
            if (writeCounter == MAX_NUM_SAMPLES) {
                writeCounter = 0;
            }
        }

        /** Returns number of samples written */
        public int size() {
            return writeCounter;
        }

        /** Returns delta time from start to end, or 0 if less than 2 samples.
         */
        public int deltaTime() {
            if (writeCounter < 2) {
                return 0;
            }
            return samples[writeCounter - 1].time - samples[0].time;
        }
    }

    /** The top data structure that holds data from all channels - some may be empty if conversion is not enabled */
    public final class DataBuffer {
        
        ChannelBuffer[] channelBuffers = new ChannelBuffer[NUM_CHANNELS];
        
        public DataBuffer() {
            for (int i = 0; i < NUM_CHANNELS; i++) {
                channelBuffers[i] = new ChannelBuffer(i);
            }
        }
        
        private void clear() {
            for (int i = 0; i < NUM_CHANNELS; i++) {
                channelBuffers[i].clear();
            }
            
        }
    }

    /** Acquire this semaphore to prevent buffer swapping. */
    public final void acquire() {
        semaphore.acquireUninterruptibly();
    }

    /** Don't forget to release the semaphore. */
    public final void release() {
        semaphore.release();
    }
    
    public final ADCSample get(int channel, int x) {
        return currentReadingDataBuffer.channelBuffers[channel].samples[x];
    }

    /** puts a sample with boolean sync that resets to start of buffer
     * 
     * @param channel
     * @param time
     * @param val
     * @param sync true if sync reset to start
     */
    public final void put(int channel, int time, int val, boolean sync) {
        ChannelBuffer c = currentWritingDataBuffer.channelBuffers[channel];
        c.put(time, val, sync);
    }

    /** Swaps the current writing and reading buffers after acquiring the lock. */
    public void swapBuffers() {
        acquire();
        DataBuffer tmp = currentReadingDataBuffer;
        currentReadingDataBuffer = currentWritingDataBuffer;
        currentWritingDataBuffer = tmp;
        currentWritingDataBuffer.clear();
        release();
    }
    
    private int getMean(int[] dataIn) {
        int mean = 0;
        for (int i = 0; i < dataIn.length; i++) {
            mean += dataIn[i];
        }
        mean = mean / dataIn.length;
        return mean;
    }
    
    private void subtractMean(int[] dataIn, int[] dataOut) {
        int mean = getMean(dataIn);
        
        for (int i = 0; i < dataOut.length; i++) {
            dataOut[i] = dataIn[i] - mean;
        }
    }
}
