/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.dvs320;

import net.sf.jaer.event.PolarityEvent;

/**
 * An event from the cDVS chip, which includes DVS temporal contrast events, color change events, and log intensity analog value events.
 *
 * @author Tobi
 */
public class CDVSEvent extends PolarityEvent {

    int logIntensity=0;

    public boolean isLogIntensityChangeEvent(){
        return false;
    }

    public boolean isColorChangeEvent(){
        return false;
    }

    public boolean isLogIntensityEvent(){
        return false;
    }

}
