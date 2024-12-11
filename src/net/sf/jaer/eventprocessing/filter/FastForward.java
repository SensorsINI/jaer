/*
 * Copyright (C) 2024 tobid.
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
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;

/**
 *
 * @author tobid
 */
@Description("Allows skipping rendering of boring packets")
@net.sf.jaer.DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FastForward extends EventFilter2D {

    private int eventCountThreshold = getInt("eventCountThreshold", 1000);
    private long lastStatusTimeMs = System.currentTimeMillis();

    public FastForward(AEChip chip) {
        super(chip);
        setPropertyTooltip("eventCountThreshold", "Skips rendering packets with fewer than this many events");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        for (BasicEvent e : in) {
            // just iterate to get accurate size not filtered out
        }

        final int sizeNotFilteredOut = in.getSizeNotFilteredOut();
        if (sizeNotFilteredOut < eventCountThreshold && chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
            long t = System.currentTimeMillis();
            if (t - lastStatusTimeMs > 1000) {
                log.info(String.format("packet only has %,d filtered events, fastForward", in.getSizeNotFilteredOut()));
                lastStatusTimeMs = t;
            }
            chip.getAeViewer().fastForward();
        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the eventCountThreshold
     */
    public int getEventCountThreshold() {
        return eventCountThreshold;
    }

    /**
     * @param eventCountThreshold the eventCountThreshold to set
     */
    public void setEventCountThreshold(int eventCountThreshold) {
        this.eventCountThreshold = eventCountThreshold;
        putInt("eventCountThreshold", eventCountThreshold);
    }

}
