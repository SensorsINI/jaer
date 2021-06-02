/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.logging.Logger;

/**
 *
 * @author Holger
 */
public abstract class PanTiltControl {

    public Logger log = Logger.getLogger("PanTiltControl");
    public static volatile boolean moving = false;
    public static volatile boolean wasMoving = false;
    public boolean connected = false;
    public boolean invert = false;
    protected double panPos = 0;
    protected double tiltPos = 0;
    public static int waitPeriod = 0;
    public static PanTiltListener panTiltListener;

    public PanTiltControl() {
        super();
    }

    void addPanTiltListener(PanTiltListener panTiltListener) {
        PanTiltControl.panTiltListener = panTiltListener;
    }

    abstract void executeCommand(String command);

    abstract void connect(String destination) throws Exception;

    abstract void halt();
    
    abstract void setLogResponses(boolean aLogResponses);

    /**
     * @return the isConnected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @return the invert
     */
    public boolean isInvert() {
        return invert;
    }

    public boolean isMoving() {
        return PanTiltControl.moving;
    }

    public boolean isWasMoving() {
        return PanTiltControl.wasMoving;
    }

    /**
     * @param invert the invert to set
     */
    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    abstract void setPanSpeed(int speed);

    abstract void setWaitPeriod(int WaitPeriod);

    /**
     * @return the panPos
     */
    public double getPanPos() {
        return panPos;
    }

    /**
     * @param panPos the panPos to set
     */
    abstract void setPanPos(double panPos);

    /**
     * @return the tiltPos
     */
    public double getTiltPos() {
        return tiltPos;
    }

    /**
     * @param tiltPos the tiltPos to set
     */
    abstract void setTiltPos(double tiltPos);

}
