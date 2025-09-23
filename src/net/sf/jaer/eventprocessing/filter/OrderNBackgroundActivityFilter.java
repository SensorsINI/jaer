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
import java.util.Random;
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

    int[] lastRowTs, lastColTs; // these arrays hold last timestamp of event in each row/column. A value of 0 means no event since reset.
    int[] lastXByRow, lastYByCol;  // these arrays hold the x address for each y (row) event and y address for each x (col) event.
    int sx = 0, sy = 0;

    public OrderNBackgroundActivityFilter(AEChip chip) {
        super(chip);
           hideProperty("correlationTimeS");
        hideProperty("antiCasualEnabled");
//        hideProperty("sigmaDistPixels");
//        hideProperty("adaptiveFilteringEnabled");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        int dtUs = (int) Math.round(getCorrelationTimeS() * 1e6f);

        for (BasicEvent e : in) {
            totalEventCount++;
            checkAndFilterEvent(e, dtUs);
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        if (lastRowTs == null) {
            initFilter();
        }
        if (sx * sy == 0) {
            return;
        }
        Arrays.fill(lastColTs, DEFAULT_TIMESTAMP);
        Arrays.fill(lastRowTs, DEFAULT_TIMESTAMP);
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

    /**
     * Fills 1d arrays with random events with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        Random random = new Random();
        double meanIntervalS=1/noiseRateHz;
        for (int i = 0; i < lastRowTs.length; i++) {
            final double p = random.nextDouble();
            final double t = -meanIntervalS * Math.log(1 - p);
            final int tUs = (int) (1000000 * t);
            lastRowTs[i] = lastTimestampUs - tUs;
            lastXByRow[i]=random.nextInt(sy);
        }
        for (int i = 0; i < lastColTs.length; i++) {
            final double p = random.nextDouble();
            final double t = -meanIntervalS * Math.log(1 - p);
            final int tUs = (int) (1000000 * t);
            lastColTs[i] = lastTimestampUs - tUs;
            lastYByCol[i]=random.nextInt(sx);
        }
    }

    private void checkAndFilterEvent(BasicEvent e, int dtUs) {

        // check all neighbors to see if there was event around us suffiently recently
//        e.setFilteredOut(true); // by default filter out  done at end of method
        if (e.x <= 0 || e.y <= 0 || e.x >= sx - 1 || e.y >= sy - 1) {
            // assume all edge events are noise and filter OUT 
            // since we cannot fully check their correlation TODO check is this best possible?
//            saveEvent(e); 
            return;
        }
        // first check rows around us, if any adjancent row has event then filter in
        for (int y = -1; y <= 1; y++) {
            if (lastRowTs[e.y + y] != DEFAULT_TIMESTAMP && e.timestamp - lastRowTs[e.y + y] < dtUs
                    && Math.abs(lastXByRow[e.y + y] - e.x) <= 1) {
                // if there was event (ts!=DEFAULT_TIMESTAMP), and the timestamp is recent enough, and the column was adjacent, then filter in
                filterIn(e);
                saveEvent(e);
//                selfCorrelated = y == 0;
            }
        }
        // now do same for columns
        for (int x = -1; x <= 1; x++) {
            if (lastColTs[e.x + x] != DEFAULT_TIMESTAMP && e.timestamp - lastColTs[e.x + x] < dtUs
                    && Math.abs(lastYByCol[e.x + x] - e.y) <= 1) {
//                if (selfCorrelated && x == 0) { // if we correlated with ourselves only, then filter out and just return
//                    e.setFilteredOut(true);
//                }
                filterIn(e);
                saveEvent(e);
                return;
            }
        }
        saveEvent(e);
        filterOut(e);
    }

    private void saveEvent(BasicEvent e) {
        lastXByRow[e.y] = e.x;
        lastYByCol[e.x] = e.y;
        lastColTs[e.x] = e.timestamp;
        lastRowTs[e.y] = e.timestamp;
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
                        setCorrelationTimeS(1e-6f * Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set OrderNFilter parameters dtS " + String.valueOf(getCorrelationTimeS());
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }

    @Override
    public String infoString() {
        return super.infoString();
    }

}
