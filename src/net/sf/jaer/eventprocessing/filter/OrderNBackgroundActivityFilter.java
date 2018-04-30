/*
 * Copyright (C) 2018 Tobi Delbruck.
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

import com.jogamp.opengl.GLAutoDrawable;
import java.util.Arrays;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Filters noise based on Khodamoradi and Kastner 2018 IEEE emerging topics paper
 *
 * @author Tobi Delbruck
 */
@Description("filters noise based on Khodamoradi and Kastner 2018 IEEE emerging topics paper")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class OrderNBackgroundActivityFilter extends EventFilter2D implements FrameAnnotater {

    int dtUs = getInt("dtUs", 10000);
    int[] lastRowTs, lastColTs;
    int[] lastXByRow, lastYByCol;
    int sx = 0, sy = 0;

    public OrderNBackgroundActivityFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("dtUs", "correlation time in us");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        for (BasicEvent e : in) {
            checkAndFilterEvent(e);
        }

        return in;

    }

    @Override
    public void resetFilter() {
        Arrays.fill(lastColTs, 0);
        Arrays.fill(lastRowTs, 0);
        Arrays.fill(lastXByRow, -1);
        Arrays.fill(lastYByCol, -1);
    }

    @Override
    public void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        lastRowTs = new int[sy];
        lastColTs = new int[sx];
        lastXByRow = new int[sy];
        lastYByCol = new int[sx];
        resetFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }

    private void checkAndFilterEvent(BasicEvent e) {
        
        // check all neighbors to see if there was event around us suffiently recently
        
        if (e.x <= 0 || e.y <= 0 || e.x >= sx - 1 || e.y >= sy - 1) {
            e.setFilteredOut(true);
            return; // filter out all edge events since we cannot fully check correlation TDOO not really correct
        }
        for(int x=-1;x<=1;x++){
            
        }
    }

}
