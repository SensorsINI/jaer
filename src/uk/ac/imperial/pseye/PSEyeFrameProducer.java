
package uk.ac.imperial.pseye;

import java.util.concurrent.Callable;

/* Package private class to act as producer thread */
class PSEyeFrameProducer implements Callable<Boolean> {
    private PSEyeFrameManager manager;
            
    public PSEyeFrameProducer(PSEyeFrameManager manager) 
    {
        this.manager = manager;
    }
        
    @Override
    public Boolean call() {
        PSEyeFrame frame = null;
        boolean hasData = false;
        
        // boolean signal to stop thread (volatile and atomic)
        while (manager.running) {
            // get usable frame from producer queue
            if (frame == null) {
                frame = manager.producerQueue.poll();
            }
                
            if (frame != null) {
                // check for return of frame data
                // no need to sleep here as wait time out will accomplish same thing
                hasData = manager.camera.getCameraRawFrame(frame.getData(), 50);
                if (hasData) {
                    frame.setTimeStamp(System.currentTimeMillis() * 1000);
                    // place filled frame in consumer queue
                    manager.consumerQueue.offer(frame);
                    frame = null;
                }
            }
            else sleepThread();
        }

        // thread stopped but still have an active frame so clean up
        if (frame != null) {
            if (hasData) {
                frame.setTimeStamp(System.currentTimeMillis() * 1000);
                manager.consumerQueue.offer(frame);
                frame = null;
            }
            else
            {
                manager.producerQueue.offer(frame);
                frame = null;
            }
        }
            
        return true;
    }
        
    public void sleepThread() {
        // make thread wait - used for consumer to catch up
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {}
    }       
}
