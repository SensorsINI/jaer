/*
 * Copyright (C) 2025 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.eventprocessing.tracking;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;

/**
 * Generates and injects synthetic blobs of events from model of flying object
 * into the event stream.
 *
 * @author tobid
 */
@Description("Generates and injects synthetic blobs of events from model of flying object into the event stream")
@net.sf.jaer.DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class FlyingBlobGenerator extends EventFilter2DMouseAdaptor {

    @Preferred
    @Description("Mean velocity for flying objecs")
    private float velocityMps = getFloat("velocityMps", 10);

    public FlyingBlobGenerator(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        OutputEventIterator outItr = in.outputIterator();
        for (BasicEvent e : in) {

        }
        return out;
    }

    /**
     * Called in rewind or when user wants to reset filter state.
     *
     */
    @Override
    public void resetFilter() {

    }

    /**
     * Called after AEChip and this are fully constructed.
     *
     */
    @Override
    public void initFilter() {

    }

    /**
     * @return the velocityMps
     */
    public float getVelocityMps() {
        return velocityMps;
    }

    /**
     * @param velocityMps the velocityMps to set
     */
    public void setVelocityMps(float velocityMps) {
        this.velocityMps = velocityMps;
        putFloat("velocityMps", velocityMps);
    }

}
