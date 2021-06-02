/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 * Interface for a Pan Tilt Listener, which is used to tell when a movement is finished.
 * 
 * @author Holger
 */
public interface PanTiltListener extends java.util.EventListener {
    public void panTiltAction(PanTiltEvent evt);
}
