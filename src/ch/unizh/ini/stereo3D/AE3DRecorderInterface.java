/*
 * AE3DRecorderInterface.java
 *
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.stereo3D;

import java.io.IOException;
import java.io.File;

/**
 * A general interface for 3D data files recording
 
 * @author rogister
 */
public interface AE3DRecorderInterface {
   

    /**
     * record
     */
    void record(  );// throws IOException;
    
    
    /**
     * stop
     */
    void stopRecording( File file) ;
   
   
}
