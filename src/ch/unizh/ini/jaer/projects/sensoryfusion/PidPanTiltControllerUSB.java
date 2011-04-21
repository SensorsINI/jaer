/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.sensoryfusion;

import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Christian
 */
public class PidPanTiltControllerUSB extends EventFilter2D {

    private float kP=prefs().getFloat("kP",1f);
    private PanTilt panTilt;

    float errorX, errorY = 0.0f;
    float pX, pY = 0.0f;
    float iX, iY = 0.0f;
    float dX, dY = 0.0f;

    public PidPanTiltControllerUSB(AEChip chip){
        super(chip);
    }

    public void updateError(int newErrorX, int newErrorY){

    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {

    }

    @Override
    public void initFilter() {

    }

}
