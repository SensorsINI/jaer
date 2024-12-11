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
 * Allows skipping rendering of boring packets
 * @author tobid
 */
@Description("<html>Allows skipping rendering of boring packets. <p>Rendering of the packet is skipped but all filters are processed."
        + "<p>Event count is based on filtering up to FastForward location in FilterChain."
        + "<p>Data is still logged to files.")
@net.sf.jaer.DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FastForward extends EventFilter2D {

    private int eventCountThreshold = getInt("eventCountThreshold", 1000);
    private long lastStatusTimeMs = System.currentTimeMillis();
    private int skippedPacketCount=0;

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
            skippedPacketCount++;
            long t = System.currentTimeMillis();
            if (skippedPacketCount%100==0 || t - lastStatusTimeMs > 500 ) {
                log.info(String.format(">>> FastForward: %,d packets (only %,d filtered <%,d threshold events)",
                        skippedPacketCount,
                        in.getSizeNotFilteredOut(), 
                        eventCountThreshold));
                lastStatusTimeMs = t;
            }
            chip.getAeViewer().fastForward();
        }else{
            skippedPacketCount=0;
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
