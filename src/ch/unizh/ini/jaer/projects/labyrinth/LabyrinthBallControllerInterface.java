/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.beans.PropertyChangeSupport;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * The interface to the ball controller that is shared by different ball controllers, used for GUI construction.
 * @author tobi
 */
public interface LabyrinthBallControllerInterface {

    /** Centers the table
     * 
     */
    void centerTilts();

    /** Opens the tilt controller GUI
     * 
     */
    void controlTilts();

    /** Disables the servo pulses to quiet and relax (in case of analog servos) the servos.
     * 
     */
    void disableServos();

    /** Starts a timed jiggle of the table
     * 
     */
    void doJiggleTable();

    /**
     * Sets the pan and tilt servo values, clipped to limits, and sets internal values.
     * @param xtiltRad in radians, positive to tilt to right towards positive x
     * @param ytiltRad in radians, positive to tilt up towards positive y
     */
    void setTilts(float xtiltRad, float ytiltRad) throws HardwareInterfaceException;
    
    PropertyChangeSupport getSupport();
    
    /**
     * @return the tiltLimitRad in radians of the table
     */
    public float getTiltLimitRad();
    
    /** Disables controller but doesn't store preference value
     * 
     * @param disabled 
     */
    public void setControllerDisabledTemporarily(boolean disabled);

    public boolean isControllerTemporarilyDisabled();

   
}
