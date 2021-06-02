/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.EventObject;

/**
 * This event
 * @author Holger
 */
public class PanTiltEvent extends EventObject {

    private int status; // 0=not moving, 1=moving, 2=WaitingAfterMovement

    public PanTiltEvent(Object source, int status) {
        super(source);
        this.status = status;
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }
}
