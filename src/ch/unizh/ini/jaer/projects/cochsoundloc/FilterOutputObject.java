/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 *
 * @author Holger
 */
public class FilterOutputObject {
    private boolean fromCochlea = false;
    private boolean fromRetina = false;
    private float panOffset = 0;
    private float tiltOffset = 0;
    private float confidence = 0;

    /**
     * @return the fromCochlea
     */
    public boolean isFromCochlea() {
        return fromCochlea;
    }

    /**
     * @param fromCochlea the fromCochlea to set
     */
    public void setFromCochlea(boolean fromCochlea) {
        this.fromCochlea = fromCochlea;
    }

    /**
     * @return the fromRetina
     */
    public boolean isFromRetina() {
        return fromRetina;
    }

    /**
     * @param fromRetina the fromRetina to set
     */
    public void setFromRetina(boolean fromRetina) {
        this.fromRetina = fromRetina;
    }

    /**
     * @return the panOffset
     */
    public float getPanOffset() {
        return panOffset;
    }

    /**
     * @param panOffset the panOffset to set
     */
    public void setPanOffset(float panOffset) {
        this.panOffset = panOffset;
    }

    /**
     * @return the tiltOffset
     */
    public float getTiltOffset() {
        return tiltOffset;
    }

    /**
     * @param tiltOffset the tiltOffset to set
     */
    public void setTiltOffset(float tiltOffset) {
        this.tiltOffset = tiltOffset;
    }

    /**
     * @return the confidence
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * @param confidence the confidence to set
     */
    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }
}
