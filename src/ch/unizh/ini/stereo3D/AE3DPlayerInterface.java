/*
 * AE3DPlayerInterface.java
 *
 * Created on December 24, 2006, 5:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.stereo3D;

import java.io.IOException;
import java.io.File;
/**
 * A general interface for 3D data files player
 
 * @author rogister
 */
public interface AE3DPlayerInterface {
    /**
     * 
     * 
     * 
     * @return fractional position in total events
     */
   
    void openFile( File file);
    void openFile( String filename);

    /**
     * pause
     * 
     * 
    */
    void pause();
    
     /**
     * clear display
     * 
     * 
    */
    void clear();
    
     /**
     * play
     * 
     * 
     * @throws IOException if there is some error in reading the data
     */
    void play();// throws IOException;

    /**
     * stop
     * 
     * 
     * @throws IOException if there is some error in reading the data
     */
    void stop();// throws IOException;
    
    public void speedUp();
    public void slowDown();
    float getSpeed();
    void revert();
    boolean isForward();
    
    public float getFractionalPosition();
    public void setFractionalPosition(float f);
    public AE3DFileInputStream getInputStream();
}
