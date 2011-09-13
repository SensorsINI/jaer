/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterface;

/**
 * Adds refOff and refOn methods to base ADCHardwareInterface. These are used on cDVSTest to obtain reference samples.
 * 
 * @author tobi
 */
public interface cDVSTestADCHardwareInterface extends ADCHardwareInterface {

    public static final String EVENT_REF_OFF_TIME = "refOffTime";
    public static final String EVENT_REF_ON_TIME = "refOnTime";

    /**
     * @return the RefOffTime
     */
    public int getRefOffTime();

    /**
     * @return the RefOnTime
     */
    public int getRefOnTime();

    public void setRefOffTime(int trackTimeUs);

    public void setRefOnTime(int trackTimeUs);
}
