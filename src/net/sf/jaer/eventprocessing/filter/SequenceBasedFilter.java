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
//import java.util.stream;
import java.util.stream.IntStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.RemoteControlCommand;

/**
 * An filter that filters out noise according to Shasha Guo method 2020.
 *
 * @author Shssha Guo, tobi
 */
@Description("Filters out uncorrelated background activity noise according to "
        + " spatio-temporal correlation but with a past event window. The past event window stores the past few events, usually 2 or 4 is enough, and requires negligible memory cost.")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)

/**
 *
 * @author gss
 */
public class SequenceBasedFilter extends AbstractNoiseFilter implements Observer {

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

    int[][] lastTimesMap;
    private int firstEventTime;
    private int lastEventTime;
    private int timeThr = 0;
//    protected int eventCountWindow = getInt("eventCountWindow", 5000);
//    protected float scaleFactor = getFloat("scaleFactor", 10);
//    private int dtUs = getInt("dtUs", 10000);

    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;
    private final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    private int lasttd = 0;
    private int frameid = 0;

    private int basicThr = 0;

//    private int mode = getInt("mode", 1);
    private int xlength = getInt("xlength", 2);
    private short[][] lastXEvents;// = new short[xlength][2];
    private float disThr = getFloat("disThr", (float) 0.1);
    private int fillindex = 0;

    /// beamforming
    private boolean beamFormingEnabled = getBoolean("beamFormingEnabled", false);
    private int beamFormingRangeUs = getInt("beamFormingRangeUs", 100);
    private float beamFormingITDUs = getFloat("beamFormingITDUs", Float.NaN);
    // UDP messages
    private String sendITD_UDP_port = getString("sendITD_UDP_port", "localhost:9999");
    private boolean sendITD_UDP_Messages = getBoolean("sendITD_UDP_Messages", false);
    private boolean sendADCSamples = getBoolean("sendADCSamples", true);

    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    int packetSequenceNumber = 0;
    InetSocketAddress client = null;
    private int UDP_BUFFER_SIZE = 8192;
    private ByteBuffer udpBuffer = ByteBuffer.allocateDirect(UDP_BUFFER_SIZE);
    private boolean printedFirstUdpMessage = false;
    long lastUdpMsgTime = 0;
    int MIN_UPD_PACKET_INTERVAL_MS = 15;
    
    int MAX_DT = sx+sy;
    final int MIN_DT = 0;

    public SequenceBasedFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
//        setPropertyTooltip("mode", "mode (min:1 avg31:2)");
        setPropertyTooltip("xlength", "based on how many Past events for the current event");
        setPropertyTooltip("letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
//        setPropertyTooltip("eventCountWindow", "constant-count window length in events");
        setPropertyTooltip("disThr", "threshold for distance comparison, if too noise, make this smaller, if too little events, make this larger");
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
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (lastTimesMap == null) {
            allocateMaps(chip);
//            System.out.printf("after allocate  is: %d\n", (1 << subsampleBy << subsampleBy));
//            basicThr = (int) (sx+1) * (sy+1) / ((1 << subsampleBy << subsampleBy) * eventCountWindow);
            frameid = 0;
//            timeThr = basicThr;
        }
        totalEventCount = 0;
        filteredOutEventCount = 0;

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                frameid = 0;
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }

            ts = e.timestamp;
            if (totalEventCount == 0) {
                firstEventTime = ts;
            }
            totalEventCount++;
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                filteredOutEventCount++;
                continue;
            }
            if (fillindex < xlength && lastXEvents[xlength - 1][0] == -1) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
                lastXEvents[fillindex][0] = e.x;
                lastXEvents[fillindex][1] = e.y;
                fillindex = fillindex + 1;
                continue;
            }

//            int lastT = lastTimesMap[x][y];
//            int deltaT = (ts - lastT);
//            int myflag = 1;
//            if (deltaT > timeThr){
//                e.setFilteredOut(true);
//                filteredOutEventCount++;
//                myflag = 0;
//            }
            float[] disarray = new float[xlength];
            for (int i = 0; i < xlength; i++) {
//                int Dis = Math.abs(e.x - lastXEvents[i][0]) + Math.abs(e.y - lastXEvents[i][1]);
                disarray[i] = (float) (Math.abs(e.x - lastXEvents[i][0])) / sx + (float) (Math.abs(e.y - lastXEvents[i][1])) / sy;
            }
            int minindex = IntStream.range(0, disarray.length).reduce((i, j) -> disarray[i] > disarray[j] ? j : i).getAsInt();
//            int maxindex = IntStream.range(0, disarray.length).reduce((i, j) -> disarray[i] < disarray[j] ? j : i).getAsInt();
            float mindisvalue = disarray[minindex];
//            float maxdisvalue = disarray[maxindex];
            int myflag = 1;
            float checkvalue = 0;

            checkvalue = mindisvalue;

            if (checkvalue > disThr) {
                e.setFilteredOut(true);
                filteredOutEventCount++;
                myflag = 0;
            }

//            update based on last x events
            for (int i = 0; i < xlength - 1; i++) {

                lastXEvents[i][0] = lastXEvents[i + 1][0];
                lastXEvents[i][1] = lastXEvents[i + 1][1];
            }
            lastXEvents[xlength - 1][0] = e.x;
            lastXEvents[xlength - 1][1] = e.y;
        }

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
//        initFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        frameid = 0;
//        basicThr = (int) (sx+1) * (sy+1) / ((1 << subsampleBy << subsampleBy) * eventCountWindow);
//        timeThr = basicThr;
//        System.out.printf("the number is: %d %d\n",sx, sy);
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
//            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
//            for (int[] arrayRow : lastTimesMap) {
//                Arrays.fill(arrayRow, 0);
//            }
            lastXEvents = new short[xlength][2];
            for (short[] arrayRow : lastXEvents) {
                Arrays.fill(arrayRow, (short) -1);
            }
        }
    }

    public Object getFilterState() {
        return lastTimesMap;
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
     * // * @return the mode //
     */
//    public int getMode() {
//        return mode;
//    }
//
//    /**
//     * @param mode the mode to set
//     */
//    public void setMode(int mode) {
//        this.mode = mode;
//        putInt("mode", mode);
//    }
    /**
     * @return the xlength
     */
    public int getXLength() {
        return xlength;
    }

    /**
     * @param xlength the xlength to set
     */
    public void setXLength(int xlength) {
        putInt("xlength", xlength);
        getSupport().firePropertyChange("xlength", this.xlength, xlength);
        this.xlength = xlength;
        
    }

    /**
     * @return the disThr
     */
    public float getDisThr() {
        return disThr;
    }

    /**
     * @param disThr the disThr to set
     */
    public void setDisThr(float disThr) {
//        this.disThr = disThr;
//        putDouble("disThr", disThr);

        float setValue = disThr;
        if (disThr < getMinDt()) {
            setValue = getMinDt();
        }
        if (disThr > getMaxDt()) {
            setValue = getMaxDt();
        }

        putDouble("disThr", setValue);
        getSupport().firePropertyChange("disThr", this.disThr, setValue);
        this.disThr = setValue;
    }

    public int getMinDt() {
        return MIN_DT;
    }

    public int getMaxDt() {
        MAX_DT = sx + sy;
        return MAX_DT;
    }

    private String USAGE = "SequenceFilter needs at least 2 arguments: noisefilter <command> <args>\nCommands are: setParameters disThr xx windowsize xx\n";

    @Override
    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s");
        if (tok.length < 3) {
            return USAGE;
        }
        try {
            if ((tok.length - 1) % 2 == 0) {
                for (int i = 1; i < tok.length; i++) {
                    if (tok[i].equals("disThr")) {
                        setDisThr(Float.parseFloat(tok[i + 1]));
                    }
                    else if (tok[i].equals("windowsize")) {
                        setXLength(Integer.parseInt(tok[i + 1]));
                    }
                }
                String out = "successfully set SequenceFilter parameters dt " + String.valueOf(disThr) + " and windowsize " + String.valueOf(xlength);
                return out;
            } else {
                return USAGE;
            }

        } catch (Exception e) {
            return "IOExeption in remotecontrol " + e.toString() + "\n";
        }
    }
}
