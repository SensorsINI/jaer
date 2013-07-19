/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel;

import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * This type of extractor uses different kernels to compute various results
 * on the incoming stream of events.
 */
public interface KernelExtractor {
    
    /**
     * Gets the results of the kernel operations.
     * 
     * @return The results of the kernel operations.
     */
    public Storage[] getStorage();
    
    /**
     * This class stores a timestamp and the results computed out of the
     * different kernels.
     */
    public class Storage {
        
        /** The timestamp of the center of the kernels. */
        public int timestamp;
        
        /** The absolute of the different kernels. */
        public float[] absolute;
        
        /** The size of the used kernels. */
        public int[] size;
        
        /** Indicates whether a particular result was computed or not. */
        public boolean[] state;
        
        /**
         * Creates a new storage for the results computed out of the different
         * kernels.
         * 
         * @param timestamp The timestamp of the center of the kernels.
         * @param nKernels The number of kernels used by the extractor.
         */
        public Storage(int timestamp, int nKernels) {
            this.timestamp = timestamp;
            this.absolute = new float[nKernels];
            this.size = new int[nKernels];
            this.state = new boolean[nKernels];
            
            Arrays.fill(this.state, false);
        }
    }
}
