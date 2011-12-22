
package uk.ac.imperial.pseye;

import java.util.logging.Logger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import java.util.concurrent.TimeUnit;

/* Package private class to handle frame data thread producer and 
 * acts as consumer 
 */
class PSEyeFrameManager {
    /* make class singleton as only single producer/consumer supported */
    public static final PSEyeFrameManager INSTANCE = new PSEyeFrameManager();
    protected final static Logger log = Logger.getLogger("PSEye");
    
    /* Variables used by threaded data capture from camera */
    /* Number of buffers in consumer and producer queues */
    public static final int BUFFER_SIZE = 6;    
    protected ArrayBlockingQueue<PSEyeFrame> consumerQueue = new ArrayBlockingQueue<PSEyeFrame>(BUFFER_SIZE);
    protected ArrayBlockingQueue<PSEyeFrame> producerQueue = new ArrayBlockingQueue<PSEyeFrame>(BUFFER_SIZE);
    
    /* frame producer thread */
    private PSEyeFrameProducer producer;
    /* flag to show producer/consumer running */
    protected volatile boolean running = false;

    // service and return status for running data capture thread
    protected ExecutorService executor;
    protected Future<Boolean> status;
    
    protected PSEyeCamera camera;
    /* current frame size, volatile to ensure all frames of consistent size between threads */
    public volatile int frameSize;
        
    private PSEyeFrameManager() 
    {
        consumerQueue.clear();
        producerQueue.clear();

        /* create initial frames */
        while (producerQueue.remainingCapacity() > 0)  {
            producerQueue.offer(new PSEyeFrame());
        }
    }
    
    public void setCamera(PSEyeCamera camera) {
        this.camera = camera;
    }
               
    /* start producing frames */
    public void start() {
        // can't do anything if camera not set
        if (camera == null) {
            log.warning("Cannot start manager without first setting PSEyeCamera.");
            return;
        }
        
        // Create new thread for reading from camera or stop any existing thread
        if (executor == null) 
            executor = Executors.newSingleThreadExecutor();
        
        // create producer if doesn't exist
        if (producer == null) 
            producer = new PSEyeFrameProducer(this);

        reset();
        running = true;
        status = executor.submit(producer);
    }
    
    /* stop producing frames */
    public void stop() {
        running = false;
        
        // wait until producer thread had completed
        if (status != null) {
            try {
                status.get();
            } 
            catch (ExecutionException e) {}
            catch (InterruptedException e) {}
        }
    }
    
    /* destroy thread */
    public void destroy() {
        stop();
        // shutdown thread and wait maximum 10 seconds for threads to end
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {}
        executor = null;
    }

    private void reset() {
        PSEyeFrame frame;
        
        // make sure thread has stopped
        stop();
        
        // ensure all frames are in the producer queue
        while (producerQueue.remainingCapacity() > 0) {
            frame = consumerQueue.poll();
            if (frame != null) {
                producerQueue.offer(frame);
            }
        }
        
        // set size of all frames in producer queue
        int i = 0;
        frameSize = PSEyeCamera.FrameSizeMap.get(camera.getResolution());
        while (i < BUFFER_SIZE) {
            frame = producerQueue.poll();
            if (frame != null) {
                frame.setSize(frameSize);
                producerQueue.offer(frame);
                i++;
            }
        }
    }    
    
    public PSEyeFrame popFrame() {
        return consumerQueue.poll();
    }
    
    public void pushFrame(PSEyeFrame frame) {
        producerQueue.offer(frame);
    }
    
    public int getFrameCount() {
        return consumerQueue.size();
    }
}    
