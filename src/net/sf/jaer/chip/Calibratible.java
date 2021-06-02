/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.chip;

/**
 * This object can be calibrated in some way.
 * 
 * @author tobi
 */
public interface Calibratible {

    boolean isCalibrationInProgress();

    void setCalibrationInProgress(final boolean calibrationInProgress);

}
