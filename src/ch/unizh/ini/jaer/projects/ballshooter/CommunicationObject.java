/*
 * CommunicationObject.java
 *
 * Created on July 16, 2007, 10:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.ballshooter;
//this is the object two seperate threads of TmpDiff128 and cochela use to communicate using arrayblocking queue
/**
 *
 * @author Vaibhav Garg
 */
public class CommunicationObject {
    private float itdValue;
    private boolean forCochlea, forRetina;
    private boolean itdFilterEnabled;
    private boolean boardHit;
    private int controllerMsgFromCochleaType;
    public static int ITDVAL=1;
    public static int TARTGETHIT=2;
    
    /** Creates a new instance of CommunicationObject */
    public CommunicationObject() {
        //default initializations
        setControllerMsgFromCochleaType(ITDVAL);
        setItdValue(0.5f);
        setForCochlea(false); //if message is for Cochlea
        setForRetina(false); //if message is for Retina.
    }

    public boolean isForCochlea() {
        return forCochlea;
    }

    public void setForCochlea(boolean forCochlea) {
        this.forCochlea = forCochlea;
    }

    public boolean isForRetina() {
        return forRetina;
    }

    public void setForRetina(boolean forRetina) {
        this.forRetina = forRetina;
   }
    public boolean isItdFilterEnabled() {
        return itdFilterEnabled;
    }

    public void setItdFilterEnabled(boolean itdFilterEnabled) {
        this.itdFilterEnabled = itdFilterEnabled;
    }

    public float getItdValue() {
        return itdValue;
    }

    public void setItdValue(float itdValue) {
        this.itdValue = itdValue;
    }

    public int getControllerMsgFromCochleaType() {
        return controllerMsgFromCochleaType;
    }

    public void setControllerMsgFromCochleaType(int controllerMsgFromCochleaType) {
        this.controllerMsgFromCochleaType = controllerMsgFromCochleaType;
    }

    public boolean isBoardHit() {
        return boardHit;
    }

    public void setBoardHit(boolean boardHit) {
        this.boardHit = boardHit;
    }
    
}
