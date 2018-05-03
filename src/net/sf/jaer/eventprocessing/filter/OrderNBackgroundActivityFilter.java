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
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Filters noise based on Khodamoradi and Kastner 2018 IEEE emerging topics
 * paper
 *
 * @author Tobi Delbruck
 */
@Description("filters noise based on Khodamoradi and Kastner 2018 IEEE emerging topics paper")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class OrderNBackgroundActivityFilter extends EventFilter2D implements FrameAnnotater {

    private int dtUs = getInt("dtUs", 10000);
    int[] lastRowTs, lastColTs;
    int[] lastXByRow, lastYByCol;
    int sx = 0, sy = 0;

    public OrderNBackgroundActivityFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("dtUs", "correlation time in us");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
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
        if(lastRowTs==null) initFilter();
        if(sx*sy==0) return;
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
        e.setFilteredOut(true);
        if (e.x <= 0 || e.y <= 0 || e.x >= sx - 1 || e.y >= sy - 1) {
            saveEvent(e);
            return; // filter out all edge events since we cannot fully check correlation TDOO not really correct
        }
        boolean selfCorrelated = false;
        for (int y = -1; y <= 1; y++) {
            if (lastRowTs[e.y + y] == 0) {
                e.setFilteredOut(true);
                saveEvent(e);
                return;
            }
            if (e.timestamp - lastRowTs[e.y + y] < dtUs
                    && Math.abs(lastXByRow[e.y + y] - e.x) <= 1) {
                e.setFilteredOut(false);
                saveEvent(e);
                selfCorrelated = y == 0;
            }

        }
        for (int x = -1; x <= 1; x++) {
            if (lastColTs[e.x + x] == 0) {
                e.setFilteredOut(true);
                saveEvent(e);
                return;
            }
            if (e.timestamp - lastColTs[e.x + x] < dtUs
                    && Math.abs(lastYByCol[e.x + x] - e.y) <= 1) {
                e.setFilteredOut(false);
                saveEvent(e);
                if (selfCorrelated && x == 0) {
                    e.setFilteredOut(true);
                }
                return;
            }
        }
        saveEvent(e);
    }

    private void saveEvent(BasicEvent e) {
        lastXByRow[e.y] = e.x;
        lastYByCol[e.x] = e.y;
        lastColTs[e.x] = e.timestamp;
        lastRowTs[e.y] = e.timestamp;
    }

    /**
     * @return the dtUs
     */
    public int getDtUs() {
        return dtUs;
    }

    /**
     * @param dtUs the dtUs to set
     */
    public void setDtUs(int dtUs) {
        this.dtUs = dtUs;
        putInt("dtUs", dtUs);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        }

    }

}
