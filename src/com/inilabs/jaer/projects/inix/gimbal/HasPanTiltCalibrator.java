/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.inilabs.jaer.projects.inix.gimbal;

import ch.unizh.ini.jaer.hardware.pantilt.*;

/**
 * Has a PanTiltCalibrator
 * 
 * @author tobi
 */
public interface HasPanTiltCalibrator {

    public PanTiltCalibrator getCalibrator();
}
