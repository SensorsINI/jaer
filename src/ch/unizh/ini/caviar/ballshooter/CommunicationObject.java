/*
 * CommunicationObject.java
 *
 * Created on July 16, 2007, 10:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.ballshooter;
//this is the object two seperate threads of TmpDiff128 and cochela use to communicate using arrayblocking queue
/**
 *
 * @author Vaibhav Garg
 */
public class CommunicationObject {
    private String direction;
    private boolean forCochlea, forRetina;
    private boolean isCochleaEnabled;
    
    /** Creates a new instance of CommunicationObject */
    public CommunicationObject() {
        //default initializations
        setDirection("Right");
        setForCochlea(false); //if message is for Cochlea
        setForRetina(true); //if message is for Retina.
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
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

    public boolean isIsCochleaEnabled() {
        return isCochleaEnabled;
    }

    public void setIsCochleaEnabled(boolean isCochleaEnabled) {
        this.isCochleaEnabled = isCochleaEnabled;
    }
    
}
