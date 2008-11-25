/*
 * AE3DRecorderInterface.java
 *
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import java.io.File;

/**
 * A general interface for 3D data files recording
 
 * @author rogister
 */
public interface AE3DRecorderInterface {
   

    /**
     * record
     */
    void record( int type );// throws IOException;
    
    
    /**
     * stop
     */
    File stopRecording( ) ;
   
   
}
