/*
 * Copyright (C) 2021 gss.
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
 /* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An filter that filters slow background activity by only passing events that
 * are supported by another event in the past {@link #setDt dt} in the immediate
 * spatial neighborhood, defined by a subsampling bit shift.
 *
 * @author tobi
 */
@Description("Filters out uncorrelated background activity noise according to the paper Event Density Based Denoising Method for Dynamic Vision Sensor, that describes a variant of the standard spatiotemporal filter.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DensityFilter extends AbstractNoiseFilter {

    private int sx;
    private int sy;

    int[][] timestampImage;

    int sigma = 2; // L x L = 5 x 5

    private int[][][] densitymatrix;
    private int ts = 0, lastTimestamp = DEFAULT_TIMESTAMP; // used to reset filter
    private int numMustBeCorrelated = getInt("numMustBeCorrelated", 3);

    public DensityFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip(TT_FILT_CONTROL, "numMustBeCorrelated", "At least this number of 9 (3x3) neighbors (including our own event location) must have had event within past dt");
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        if (timestampImage == null) {
            allocateMaps(chip);
        }

        int dt = (int) Math.round(getCorrelationTimeS() * 1e6f);
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (BasicEvent e : in) {
            if (e == null) {
                continue;
            }
//	    if (e.isSpecial()) {
//		continue;
//	    }
            totalEventCount++;
            int ts = e.timestamp;
            lastTimestamp = ts;
            final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filterOut(e);
                continue;
            }

            if (timestampImage[x][y] == DEFAULT_TIMESTAMP) {
                timestampImage[x][y] = ts;
                if (letFirstEventThrough) {
                    filterIn(e);
                    continue;
                } else {
                    filterOut(e);
                    continue;
                }
            }
//	    final int numMustBeCorrelated = 1;
            int ncorrelated = 0;
            outerloop:

            for (int eid = 7; eid >= 1; eid--) {
                densitymatrix[x][y][eid] = densitymatrix[x][y][eid - 1]; // first right shift the ts array. the first one is always for the current event
            }

            densitymatrix[x][y][0] = ts;
            for (int eid = 1; eid < 8; eid++) {
                int eidts = densitymatrix[x][y][eid];
                int deltaT = ts - eidts;
// check the past event list, i.e., each event has a past event window memorying event ts occurred within deltat of the current event. if the old ts is not within the delta t, erase it with 0
                if (deltaT > dt) {

                    eidts = 0;
                }

            }
            outerloop:
            for (int xx = x - sigma; xx <= x + sigma; xx++) {
                for (int yy = y - sigma; yy <= y + sigma; yy++) {
                    if ((xx < 0) || (xx > sx) || (yy < 0) || (yy > sy)) {
                        continue;
                    }
                    if (filterHotPixels && xx == x && yy == y) {
                        continue; // like BAF, don't correlate with ourself
                    }
                    final int lastT = timestampImage[xx][yy];
                    final int deltaT = (ts - lastT);
                    if (deltaT < dt && lastT != DEFAULT_TIMESTAMP) {
                        ncorrelated++; // the densitymatrix[xx][yy][0] has been checked
                        for (int k = 1; k < 8; k++) {
                            int lastT2 = densitymatrix[xx][yy][k];
                            if (lastT2 == 0) {
                                break;
                            } else {
                                int deltaT2 = (ts - lastT2);
                                if (deltaT2 < dt) {
                                    ncorrelated++;
                                    if (ncorrelated >= numMustBeCorrelated) { // i.e., it exceeds the threshold, can stop checking
                                        break outerloop;
                                    }
                                }
                            }

                        }
                    } else {
                        continue; // if density metrix the first element is the same as timestampimage. if the latest is not within delta t, then the older ts is also not within the delta t.
                    }
                }
            }
            if (ncorrelated < numMustBeCorrelated) {
                filterOut(e);
            } else {
                filterIn(e);
            }
            timestampImage[x][y] = ts;
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        super.resetFilter();
//        log.info("resetting DensityFilter");
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }

        for (int[][] array2 : densitymatrix) {
//	    Arrays.fill(arrayRow, 0);
            for (int[] arrayRow : array2) {
                Arrays.fill(arrayRow, 0);
            }
        }
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        resetFilter();
    }

    /**
     * Fills timestampImage with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        fill2dTimestampAndPolarityImagesWithNoiseEvents(noiseRateHz, lastTimestampUs, timestampImage, null);
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0) && (timestampImage == null || timestampImage.length != chip.getSizeX() >> subsampleBy)) {
            timestampImage = new int[chip.getSizeX()][chip.getSizeY()]; // TODO handle subsampling to save memory (but check in filterPacket for range check optomization)
            densitymatrix = new int[chip.getSizeX()][chip.getSizeY()][8];
        }
    }

    public Object getFilterState() {
        return timestampImage;
    }

    /**
     * @return the numMustBeCorrelated
     */
    public int getNumMustBeCorrelated() {
        return numMustBeCorrelated;
    }

    /**
     * @param numMustBeCorrelated the numMustBeCorrelated to set
     */
    public void setNumMustBeCorrelated(int numMustBeCorrelated) {
        if (numMustBeCorrelated < 1) {
            numMustBeCorrelated = 1;
        } else if (numMustBeCorrelated > getNumNeighbors()) {
            numMustBeCorrelated = getNumNeighbors();
        }
        putInt("numMustBeCorrelated", numMustBeCorrelated);
        this.numMustBeCorrelated = numMustBeCorrelated;
        getSupport().firePropertyChange("numMustBeCorrelated", this.numMustBeCorrelated, numMustBeCorrelated);
    }

    private String USAGE = "DensityFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx subsample xx\n";

    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");

        if (tok.length < 3) {
            return USAGE;
        }
        try {

            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("dt")) {
                        setCorrelationTimeS(1e-6f * Integer.parseInt(tok[i + 1]));
                    }
                    if (tok[i].equals("num")) {
                        setNumMustBeCorrelated(Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set DensityFilter parameters dt " + String.valueOf(getCorrelationTimeS()) + " and threshold " + String.valueOf(numMustBeCorrelated);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }

}
