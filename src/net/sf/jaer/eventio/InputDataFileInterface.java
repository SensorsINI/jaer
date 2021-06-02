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
 * A general interface for input data files of finite length that can be
 * rewound, positioned, marked, etc. A data file has units of data, e.g. events
 * for AE data, frames for frame-based data.
 *
 * @author tobi
 */
public interface InputDataFileInterface {

    /**
     *
     * @return fractional position in total events
     */
    public float getFractionalPosition();

    /**
     * return position in events
     */
    public long position();

    /**
     * set position in units of the file, e.g. events or frames
     *
     * @param n the number to position to
     */
    public void position(long n);

    /**
     * rewind to the start, or to the marked position, if it has been set
     */
    public void rewind() throws IOException;

    /**
     * Sets fractional position in units of the file
     *
     * @param frac 0-1 float range, 0 at start, 1 at end
     */
    public void setFractionalPosition(float frac);

    /**
     * @return size in units of the file
     */
    public long size();

    /**
     * clear any marked positions.
     */
    public void clearMarks();

    /**
     * Mark IN (start) position, or clear the mark to start of file if it was set.
     * @return the new marked position.
     */
    public long setMarkIn();

    /**
     * Mark OUT (end) position, or clear the mark to end of file if it was set.
     * @return the new marked position.
     */
    public long setMarkOut();

    /** Returns the marked IN position, or 0 if mark was not set.
     * 
     * @return 
     */
    public long getMarkInPosition();

    /** Returns the marked out position, or end of file if mark was not set.
     * 
     * @return 
     */
    public long getMarkOutPosition();
    
    /** Returns whether IN mark is set 
     * 
     * @return true if set. 
     */
    public boolean isMarkInSet();
    
    /** Returns whether OUT mark is set.
     * 
     * @return true if set. 
     */
    public boolean isMarkOutSet();
    
    /**
     * Set whether the stream should be repeated after reaching OUT mark
     * @param repeat sets whether repeat is on
     */
    public void setRepeat(boolean repeat);
    
    /** Returns whether stream should be repeated after reaching OUT mark
     * 
     * @return true if set. 
     */
    public boolean isRepeat();

}
