/*
 * Copyright (C) 2020 gss.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

import java.lang.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Random;
import java.util.stream.IntStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * WF/DFWF Fixed Window and Double Fixed Window filter that Filters out
 * uncorrelated background activity noise according to spatio-temporal
 * correlation but with a past event window. The past event window stores the
 * past few events, usually 2 or 4 is enough, and requires negligible memory
 * cost.
 *
 * @author Shssha Guo
 */
@Description("DWF/FWF Fixed Window and Double Window Filter that Filters out uncorrelated background activity noise according to "
        + " spatio-temporal correlation but with a past event window. The past event window stores the past few events, usually 2 or 4 is enough, and requires negligible memory cost.")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class DoubleWindowFilter extends AbstractNoiseFilter {

    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    private boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
//    private int subsampleBy = getInt("subsampleBy", 0);
    private int subsampleBy = 0;

    private int timeThr = 0;

    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;
    private final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    private int lasttd = 0;
    private int frameid = 0;

    private int basicThr = 0;

    private int wlen = getInt("wlen", 8); // window length
    private int wlen2 = wlen / 2;
    private short[][] lastREvents;// real window;
    private short[][] lastNEvents;// noise window;

    private int disThr = getInt("disThr", 10);
    private boolean useDoubleMode = getBoolean("useDoubleMode", true);
    private int fillindex = 0;
	
    private int numMustBeCorrelated = getInt("numMustBeCorrelated", 1);

    int maxDisThr = sx + sy;
    final int minDisThr = 0;

    public DoubleWindowFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip(TT_FILT_CONTROL, "wlen", "total window length for holding previous events. If doubleMode selected, this window is split into signal and noise windows");
        setPropertyTooltip(TT_FILT_CONTROL, "useDoubleMode", "use two separate windows for storing real and noise events");
        setPropertyTooltip(TT_FILT_CONTROL, "disThr", "threshold for distance comparison, if too noisy, make this smaller, if too few events, make this larger");
	setPropertyTooltip(TT_FILT_CONTROL, "numMustBeCorrelated", "At least this number of dis (3x3) neighbors (including our own event location) must have had event within the time duration implied in the dis window");
        hideProperty("correlationTimeS");
        hideProperty("antiCasualEnabled");
        hideProperty("sigmaDistPixels");
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
    synchronized public EventPacket filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (BasicEvent e : in) {
            if (e == null) {
                frameid = 0;
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
//            if (e.isSpecial()) {
//                continue;
//            }

            ts = e.timestamp;
            totalEventCount++;
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filterOut(e);
                continue;
            }
            if (fillindex < wlen && lastREvents[wlen - 1][0] == -1) {
                filterOut(e);
                lastREvents[fillindex][0] = e.x;
                lastREvents[fillindex][1] = e.y;
                fillindex = fillindex + 1;
                continue;
            }

            int ncorrelated = 0;

	    if (useDoubleMode) {
		int dwlen = wlen > 1 ? wlen / 2 : 1;
		boolean noiseflag = true; // at first considered to be noise event
//                check real window first 
		int[] disarray = new int[dwlen];
		for (int i = 0; i < dwlen; i++) {
		    disarray[i] = (Math.abs(e.x - lastREvents[i][0])) + (Math.abs(e.y - lastREvents[i][1]));
		    if (disarray[i] < disThr) {
			ncorrelated++;
			if (ncorrelated == numMustBeCorrelated) {
			    noiseflag = false;
			    break;
			}
		    }
		}


		
		if (ncorrelated < numMustBeCorrelated) {
		    for (int i = 0; i < dwlen; i++) {
			disarray[i] = (Math.abs(e.x - lastNEvents[i][0])) + (Math.abs(e.y - lastNEvents[i][1]));
			if (disarray[i] < disThr) {
			    ncorrelated++;
			    if (ncorrelated == numMustBeCorrelated) {
				noiseflag = false;
				break;
			    }
			}
		    }


		    
		}

//            update real window or noise window based on the output of filter
		if (noiseflag == false) {
		    filterIn(e);
		    for (int i = 0; i < dwlen - 1; i++) { // leftshift

			lastREvents[i][0] = lastREvents[i + 1][0];
			lastREvents[i][1] = lastREvents[i + 1][1];
		    }
		    lastREvents[dwlen - 1][0] = e.x;
		    lastREvents[dwlen - 1][1] = e.y;
		} else {
		    filterOut(e);
		    for (int i = 0; i < dwlen - 1; i++) {

			lastNEvents[i][0] = lastNEvents[i + 1][0];
			lastNEvents[i][1] = lastNEvents[i + 1][1];
		    }
		    lastNEvents[dwlen - 1][0] = e.x;
		    lastNEvents[dwlen - 1][1] = e.y;
		}
	    } else { // fwf, single window
//                only use lastREvents to store the past events
		int[] disarray = new int[wlen];
		boolean noiseflag = true;
		for (int i = 0; i < wlen; i++) {
		    disarray[i] = (Math.abs(e.x - lastREvents[i][0])) + (Math.abs(e.y - lastREvents[i][1]));
		    if (disarray[i] < disThr){
			ncorrelated ++;
			if (ncorrelated == numMustBeCorrelated){
			    noiseflag = false;
			    break;
			} 
		    } 
		}
//		int minindex = IntStream.range(0, disarray.length).reduce((i, j) -> disarray[i] > disarray[j] ? j : i).getAsInt();
//		int mindisvalue = disarray[minindex];

		if (noiseflag) {
		    filterOut(e);
		} else {
		    filterIn(e);
		}

//                update window
		for (int i = 0; i < wlen - 1; i++) {
		    lastREvents[i][0] = lastREvents[i + 1][0];
		    lastREvents[i][1] = lastREvents[i + 1][1];
		}
		lastREvents[wlen - 1][0] = e.x;
		lastREvents[wlen - 1][1] = e.y;
	    }
        }
        getNoiseFilterControl().maybePerformControl(in);

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        super.resetFilter();
        frameid = 0;
    }

    @Override
    public final void initFilter() {
        allocateMaps();
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        frameid = 0;
    }

    private void allocateMaps() {
        lastREvents = new short[wlen][2];
        lastNEvents = new short[wlen][2];

        for (short[] arrayRow : lastREvents) {
            Arrays.fill(arrayRow, (short) -1);
        }
        if (useDoubleMode) {
            for (short[] arrayRow : lastNEvents) {
                Arrays.fill(arrayRow, (short) -1);
            }
        }
    }

    /**
     * Fills windows with random events drawn from Poisson waiting time
     * distribution rate noiseRateHz
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
//         TODO noop for now, how do we init the windows for noise rate after reset or rewind?
    }

//    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
//    public int getSubsampleBy() {
//        return subsampleBy;
//    }
//
//    /**
//     * Sets the number of bits to subsample by when storing events into the map
//     * of past events. Increasing this value will increase the number of events
//     * that pass through and will also allow passing events from small sources
//     * that do not stimulate every pixel.
//     *
//     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
//     * cut event time map resolution by a factor of two in x and in y
//     */
//    public void setSubsampleBy(int subsampleBy) {
//        if (subsampleBy < 0) {
//            subsampleBy = 0;
//        } else if (subsampleBy > 4) {
//            subsampleBy = 4;
//        }
//        this.subsampleBy = subsampleBy;
//        putInt("subsampleBy", subsampleBy);
//    }
    // </editor-fold>
    /**
     * @return the letFirstEventThrough
     */
    public boolean isLetFirstEventThrough() {
        return letFirstEventThrough;
    }

    /**
     * @param letFirstEventThrough the letFirstEventThrough to set
     */
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        this.letFirstEventThrough = letFirstEventThrough;
        putBoolean("letFirstEventThrough", letFirstEventThrough);
    }

    /**
     * @return the wlen
     */
    public int getWLen() {
        return wlen;
    }

    /**
     * Sets the window length in events.
     *
     * @param wlen the wlen to set
     */
    synchronized public void setWLen(int wlen) {
        int setValue = wlen;
        if (setValue < 1) {
            setValue = 1;
        }
        this.wlen = setValue;
        log.info(String.format("wlen is%d\n", this.wlen));

        putInt("wlen", setValue);
        getSupport().firePropertyChange("wlen", this.wlen, setValue);

        allocateMaps();
    }

    /**
     * @return the useDoubleMode
     */
    public boolean isUseDoubleMode() {
        return useDoubleMode;
    }

    /**
     * Sets to use double mode, where noise and signal events that are detected
     * are put to different halves of the window.
     *
     * @param wlen the useDoubleMode to set
     */
    public void setUseDoubleMode(boolean useDoubleMode) {
        putBoolean("useDoubleMode", useDoubleMode);
        getSupport().firePropertyChange("useDoubleMode", this.useDoubleMode, useDoubleMode);
        this.useDoubleMode = useDoubleMode;
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

    /**
     * @return the disThr
     */
    public int getDisThr() {
        return disThr;
    }

    /**
     * @param disThr the disThr to set
     */
    synchronized public void setDisThr(int disThr) {

        int setValue = disThr;
        if (disThr < getMinDisThr()) {
            setValue = getMinDisThr();
        }
        if (disThr > getMaxDisThr()) {
            setValue = getMaxDisThr();
        }

        putDouble("disThr", setValue);
        getSupport().firePropertyChange("disThr", this.disThr, setValue);
        this.disThr = setValue;
    }

    public int getMinDisThr() {
        return minDisThr;
    }

    public int getMaxDisThr() {
        maxDisThr = sx + sy;
        return maxDisThr;
    }

    private String USAGE = "SequenceFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters dt xx num xx\n";

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
                        setDisThr(Integer.parseInt(tok[i + 1]));
                    } else if (tok[i].equals("num")) {
                        setWLen(Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set SequenceFilter parameters dt " + String.valueOf(disThr) + " and windowsize " + String.valueOf(wlen);
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
        String s = isUseDoubleMode() ? "DWF" : "FWF";
        s = s + ": L=" + wlen + " sigma=" + eng.format(disThr);
        return s;
    }

    private static int printedCorrelationTimeWarning = 5;

    /**
     * Overridden to set L according to recent activity to some correlation time
     */
    @Override
    public void setCorrelationTimeS(float dtS) {
        if (printedCorrelationTimeWarning > 0) {
            log.warning("Setting correlation time currently has no effect");
            printedCorrelationTimeWarning--;
        }
    }

}
