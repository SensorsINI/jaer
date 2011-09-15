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
 * @author Tobi
 */
public class CochleaAMS1cADCSamples {

    final static Logger log = Logger.getLogger("CochleaAMS1c");
    public static final int WIDTH = 64, HEIGHT = 1;
    public static final int NUM_SAMPLES = WIDTH * HEIGHT;
    public static final int NUM_CHANNELS = 4;
    public static final int MAX_ADC_VALUE = 1023;
    DataBuffer data1 = new DataBuffer(), data2 = new DataBuffer();
    /** Readers should access the current reading buffer. */
    public DataBuffer currentReadingDataBuffer = data1;
    private DataBuffer currentWritingDataBuffer = data2;
    private Semaphore semaphore = new Semaphore(1);

    /** A single samples */
    public class ADCSample {

        int data, time;
    }

    /** A buffer for a single channel */
    public class ChannelBuffer {

        final int channel;
        public ADCSample[] samples = new ADCSample[NUM_SAMPLES];
        private int writeCounter = 0;

        public ChannelBuffer(final int channel) {
            this.channel = channel;
            for (int i = 0; i < NUM_SAMPLES; i++) {
                samples[i] = new ADCSample();
            }
        }

        private void clear() {
            writeCounter = 0;
        }

        public boolean hasData() {
            return writeCounter > 0;
        }

        private void put(int time, int val) {
            if (writeCounter >= NUM_SAMPLES - 1) {
//            log.info("buffer overflowed - missing start frame bit?");
                return;
            }
            samples[writeCounter].time = time;
            samples[writeCounter].data = val;
            writeCounter++;

            if (writeCounter == NUM_SAMPLES) {
                writeCounter = 0;
            }
        }
        
        /** Returns number of samples written */
        public int size(){
            return writeCounter;
        }
        
        /** Returns delta time from start to end, or 0 if less than 2 samples.
         */
        public int deltaTime(){
            if(writeCounter<2) return 0;
            return samples[writeCounter-1].time-samples[0].time;
        }
    }

    /** The top data structure that holds data from all channels - some may be empty if conversion is not enabled */
    public class DataBuffer {

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
    public void acquire() {
        semaphore.acquireUninterruptibly();
    }

    /** Don't forget to release the semaphore. */
    public void release() {
        semaphore.release();
    }

    public ADCSample get(int channel, int x, int y) {
        return currentReadingDataBuffer.channelBuffers[channel].samples[y + WIDTH * x];
    }

    /** Put a value to the next writing position. Writes wrap around to the start position. */
    public void put(int channel, int time, int val) {

        ChannelBuffer c = currentWritingDataBuffer.channelBuffers[channel];
        c.put(time, val);
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
