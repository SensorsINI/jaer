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
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * Filters noise based on Khodamoradi and Kastner 2018 IEEE emerging topics
 * paper
 *
 * @author Tobi Delbruck
 */
@Description("filters noise based on IEEE emerging topics paper Khodamoradi, A., and R. Kastner. 2018. “O(N)-Space Spatiotemporal Filter for Reducing Noise in Neuromorphic Vision Sensors.” IEEE Transactions on Emerging Topics in Computing PP (99): 1–1. https://doi.org/10.1109/TETC.2017.2788865.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class OrderNBackgroundActivityFilter extends AbstractNoiseFilter implements FrameAnnotater {

    private int dtUs = getInt("dtUs", 10000);
    int[] lastRowTs, lastColTs; // these arrays hold last timestamp of event in each row/column. A value of 0 means no event since reset.
    int[] lastXByRow, lastYByCol;
    int sx = 0, sy = 0;

    public OrderNBackgroundActivityFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("dtUs", "correlation time in us");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        totalEventCount = 0;
        filteredOutEventCount = 0;
        for (BasicEvent e : in) {
            checkAndFilterEvent(e);
            totalEventCount++;
            if (e.isFilteredOut()) {
                filteredOutEventCount++;
            }
        }
//        filteredOutEventCount=in.getFilteredOutCount();
        return in;
    }

    @Override
    public void resetFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        if (lastRowTs == null) {
            initFilter();
        }
        if (sx * sy == 0) {
            return;
        }
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

    private void checkAndFilterEvent(BasicEvent e) {

        // check all neighbors to see if there was event around us suffiently recently
        e.setFilteredOut(true); // by default filter out
        if (e.x <= 0 || e.y <= 0 || e.x >= sx - 1 || e.y >= sy - 1) {
            saveEvent(e);
            return; // filter out all edge events since we cannot fully check correlation TDOO not really correct
        }
        boolean selfCorrelated = false;
        // first check rows around us, if any adjancent row has event then filter in
        for (int y = -1; y <= 1; y++) {
            if (lastRowTs[e.y + y] != 0 && e.timestamp - lastRowTs[e.y + y] < dtUs
                    && Math.abs(lastXByRow[e.y + y] - e.x) <= 1) {
                // if there was event (ts!=0), and the timestamp is recent enough, and the column was adjacent, then filter in
                e.setFilteredOut(false);
                saveEvent(e);
                selfCorrelated = y == 0;
            }
        }
        // now do same for columns
        for (int x = -1; x <= 1; x++) {
            if (lastColTs[e.x + x] != 0 && e.timestamp - lastColTs[e.x + x] < dtUs
                    && Math.abs(lastYByCol[e.x + x] - e.y) <= 1) {
                e.setFilteredOut(false);
                saveEvent(e);
                if (selfCorrelated && x == 0) { // if we correlated with ourselves only, then filter out and just return
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
        int old=this.dtUs;
        
        putInt("dtUs", dtUs);
        getSupport().firePropertyChange("dtUs", old, dtUs);
        this.dtUs = dtUs;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        } else if (evt.getPropertyName() == AEViewer.EVENT_CHIP) {
            resetFilter();
        }

    }

    private String USAGE = "OrderNFilter needs at least 1 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx\n";

    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");
        
        if (tok.length < 3) {
            return USAGE;
        }
        try {
            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i <= tok.length; i++) {
                    if (tok[i].equals("dt")) {
                        setDtUs(Integer.parseInt(tok[i + 1]));
                    }                    
                }
                String out = "successfully set OrderNFilter parameters dt " + String.valueOf(dtUs);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }

}
