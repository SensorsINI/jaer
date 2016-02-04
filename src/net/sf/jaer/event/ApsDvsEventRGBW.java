/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.event;

import eu.seebetter.ini.chips.davis.imu.IMUSample;

/**
 *
 * Event class for DAVIS chips with color filter arrays
 *
 * @author Christian
 */
public class ApsDvsEventRGBW extends ApsDvsEvent {

    /** Tells for APS events whether they are under a red (R), green (G), blue (B) or white (W) colorfilter
     */
    public enum ColorFilter {R,G,B,W, Null};

    private ColorFilter colorFilter;

    /**
     * @return the colorFilter
     */
    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    /**
     * @param colorFilter the colorFilter to set
     */
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
    }

}
