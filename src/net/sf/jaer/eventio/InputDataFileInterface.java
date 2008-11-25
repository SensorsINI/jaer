/*
 * InputDataFileInterface.java
 *
 * Created on December 24, 2006, 5:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.eventio;

import java.io.IOException;

/**
 * A general interface for input data files of finite length that can be rewound, positioned, marked, etc.
 A data file has units of data, e.g. events for AE data, frames for frame-based data.
 
 * @author tobi
 */
public interface InputDataFileInterface {
    /**
     * 
     * 
     * 
     * @return fractional position in total events
     */
    float getFractionalPosition();

    /**
     * mark the current position.
     * 
     * 
     * @throws IOException if there is some error in reading the data
     */
    void mark() throws IOException;

    /**
     * return position in events
     */
    int position();

    /**
     * set position in units of the file, e.g. events or frames
     @param n the number to position to
     */
    void position(int n);

    /**
     * rewind to the start, or to the marked position, if it has been set
     */
    void rewind() throws IOException;

    /**
     * Sets fractional position in units of the file
     * 
     * @param frac 0-1 float range, 0 at start, 1 at end
     */
    void setFractionalPosition(float frac);

    /**
     * 
     * 
     * 
     * @return size in units of the file
     */
    long size();

    /**
     * clear any marked position
     */
    void unmark();
    
}
