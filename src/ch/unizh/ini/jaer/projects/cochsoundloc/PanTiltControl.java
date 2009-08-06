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
    public int panPosTransformed = 0;
    public int panPos = 0;
    public int OldMinPanPos = -800;
    public int OldMaxPanPos = 800;
    public int NewMinPanPos = -1800;
    public int NewMaxPanPos = 1800;
    private int OldMinTiltPos = 0;
    private int OldMaxTiltPos = 128;
    private int NewMinTiltPos = -1800;
    private int NewMaxTiltPos = 1800;
    public boolean invert = false;
    public int tiltPos = 0;
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
    
    /**
     * @return the NewMaxPanPos
     */
    public int getNewMaxPanPos() {
        return NewMaxPanPos;
    }

    /**
     * @return the NewMinPanPos
     */
    public int getNewMinPanPos() {
        return NewMinPanPos;
    }

    /**
     * @return the OldMaxPanPos
     */
    public int getOldMaxPanPos() {
        return OldMaxPanPos;
    }

    /**
     * @return the OldMinPanPos
     */
    public int getOldMinPanPos() {
        return OldMinPanPos;
    }

    /**
     * @return the panPos
     */
    public int getPanPos() {
        return panPos;
    }

    /**
     * @return the panPosTransformed
     */
    public int getPanPosTransformed() {
        return panPosTransformed;
    }

    int getTiltPos() {
        return tiltPos;
    }

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

    /**
     * @param NewMaxPanPos the NewMaxPanPos to set
     */
    public void setNewMaxPanPos(int NewMaxPanPos) {
        this.NewMaxPanPos = NewMaxPanPos;
    }

    /**
     * @param NewMinPanPos the NewMinPanPos to set
     */
    public void setNewMinPanPos(int NewMinPanPos) {
        this.NewMinPanPos = NewMinPanPos;
    }

    /**
     * @param OldMaxPanPos the OldMaxPanPos to set
     */
    public void setOldMaxPanPos(int OldMaxPanPos) {
        this.OldMaxPanPos = OldMaxPanPos;
    }

    /**
     * @param OldMinPanPos the OldMinPanPos to set
     */
    public void setOldMinPanPos(int OldMinPanPos) {
        this.OldMinPanPos = OldMinPanPos;
    }

    abstract void setPanPos(int pos);

    /**
     *
     * @param pos The transformed Position to set.
     */
    public void setPanPosTransformed(int pos) {
        //pos between -800 to 800
        int newpos = 0;
        if (isInvert() == true) {
            newpos = (-pos - getOldMinPanPos()) * (getNewMaxPanPos() - getNewMinPanPos()) / (getOldMaxPanPos() - getOldMinPanPos()) + getNewMinPanPos();
        } else {
            newpos = (pos - getOldMinPanPos()) * (getNewMaxPanPos() - getNewMinPanPos()) / (getOldMaxPanPos() - getOldMinPanPos()) + getNewMinPanPos();
        }
        //log.info("newpos: " + newpos);
        panPosTransformed = pos;
        setPanPos(newpos);
    }

    abstract void setPanSpeed(int speed);

    abstract void setWaitPeriod(int WaitPeriod);

    abstract void setTiltPos(int pos);

    /**
     * Sets the linear transofrmation for the Pan Positions.
     *
     * @param OldMinPanPos
     * @param OldMaxPanPos
     * @param NewMinPanPos
     * @param NewMaxPanPos
     * @param invert
     */
    public void setTransformation(int OldMinPanPos, int OldMaxPanPos, int NewMinPanPos, int NewMaxPanPos, boolean invert) {
        this.setOldMinPanPos(OldMinPanPos);
        this.setOldMaxPanPos(OldMaxPanPos);
        this.setNewMinPanPos(NewMinPanPos);
        this.setNewMaxPanPos(NewMaxPanPos);
        this.setInvert(invert);
    }

    /**
     * @return the OldMinTiltPos
     */
    public int getOldMinTiltPos() {
        return OldMinTiltPos;
    }

    /**
     * @param OldMinTiltPos the OldMinTiltPos to set
     */
    public void setOldMinTiltPos(int OldMinTiltPos) {
        this.OldMinTiltPos = OldMinTiltPos;
    }

    /**
     * @return the OldMaxTiltPos
     */
    public int getOldMaxTiltPos() {
        return OldMaxTiltPos;
    }

    /**
     * @param OldMaxTiltPos the OldMaxTiltPos to set
     */
    public void setOldMaxTiltPos(int OldMaxTiltPos) {
        this.OldMaxTiltPos = OldMaxTiltPos;
    }

    /**
     * @return the NewMinTiltPos
     */
    public int getNewMinTiltPos() {
        return NewMinTiltPos;
    }

    /**
     * @param NewMinTiltPos the NewMinTiltPos to set
     */
    public void setNewMinTiltPos(int NewMinTiltPos) {
        this.NewMinTiltPos = NewMinTiltPos;
    }

    /**
     * @return the NewMaxTiltPos
     */
    public int getNewMaxTiltPos() {
        return NewMaxTiltPos;
    }

    /**
     * @param NewMaxTiltPos the NewMaxTiltPos to set
     */
    public void setNewMaxTiltPos(int NewMaxTiltPos) {
        this.NewMaxTiltPos = NewMaxTiltPos;
    }

}
