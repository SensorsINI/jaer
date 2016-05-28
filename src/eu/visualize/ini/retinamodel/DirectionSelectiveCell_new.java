/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.retinamodel;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

//import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
//import net.sf.jaer.eventprocessing.label.ApsDvsDirectionSelectiveFilter;
//****************************************************************************//

//-- Description -------------------------------------------------------------//
// 
// @author Scott and Diederik
//****************************************************************************//
//-- Main class OMCOD --------------------------------------------------------//
public class DirectionSelectiveCell_new{

    private float[][] storedPreviousTimestamp;
    private float[][] storedPreviousValue;
    private float delayUs;
    private float decayTime;
    private float MembranePotential;
    private float IFthreshold;
    private int nxmax;
    private int nymax;
    private int timeConstant;

    public DirectionSelectiveCell_new(AEChip chip) {

        this.storedPreviousTimestamp = new float[nxmax][nymax]; // deleted -1 in all
        this.nxmax = chip.getSizeX() >> 3;
        this.nymax = chip.getSizeY() >> 3;

//------------------------------------------------------------------------------
        //   setPropertyTooltip(disp, "showSubunits", "Enables showing subunit activity annotation over retina output");

    }

    //----------------------------------------------------------------------------//
//-- Filter packet method ----------------------------------------------------//
//----------------------------------------------------------------------------//
//    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        //checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak.
        for (Object o : in) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            decayBy(e);
            computeDirection(e);
        }
        return in;
    }

    public void decayBy(PolarityEvent e) {
        if (e.timestamp - storedPreviousTimestamp[e.x][e.y] >= delayUs) {

            decayTime = (float) -(e.timestamp - storedPreviousTimestamp[e.x][e.y] - delayUs)/timeConstant;
            storedPreviousValue[e.x][e.y] = (float) (storedPreviousValue[e.x][e.y] * (Math.pow(2.7, decayTime)));
        }
    }

    public void computeDirection(BasicEvent e) {
        if (e.x != 0) {
            if (storedPreviousTimestamp[e.x - 1][e.y] - storedPreviousTimestamp[e.x][e.y] >= delayUs) {
                MembranePotential = storedPreviousValue[e.x - 1][e.y] + storedPreviousValue[e.x][e.y];
                if (MembranePotential >= IFthreshold) {
                    spike();
                }
            }
        }
    }

    public void spike() {
        System.out.println("spiked");
    }
    
    public float getDdelayUs() {
        return delayUs;
    }
    
    public void setDelayUs(float delayUs) {
        this.delayUs = delayUs;
        //putFloat("delayUs", delayUs);
    }
//------------------------------------------------------------------------------
}
