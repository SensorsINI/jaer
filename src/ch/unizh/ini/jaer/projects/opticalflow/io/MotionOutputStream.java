/*
 * MotionOutputStream.java
 *
 * Created on December 10, 2006, 11:10 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 10, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import ch.unizh.ini.jaer.projects.opticalflow.MotionData;

/**
 * An output stream for motion data. Can be used to output motion data to a file or network socket using serilization.
 
 * @author tobi
 */
public class MotionOutputStream extends DataOutputStream {
    
    private static Logger log=Logger.getLogger("MotionOutputStream");
    
    /** Creates a new instance of MotionOutputStream */
    public MotionOutputStream(OutputStream os) throws IOException {
        super(os);
    }
    
    /**
     * Writes the serialized frame of motion data to the stream.
     *@param data a single MotionData frame
     */
    public void writeData(MotionData data) throws IOException {
        data.write(this);
    }

}
