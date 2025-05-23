/*
 * Copyright (C) 2025 tobi.
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

import java.util.ArrayList;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;

/**
 * Special denoising packet type for denoiser development.
 *
 * @author tobi
 */
public class SignalNoisePacket extends ApsDvsEventPacket<SignalNoiseEvent> {

    int signalCount, noiseCount, totalCount;
    int tpCount;
    int tnCount;
    int fpCount;
    int fnCount;
    int unclassifiedCount;
    private final int LIST_LENGTH = 0x8000;
    SignalNoisePacket tmpSignalNoisePacket = null; // tmp packet is double buffer for returning only noise events 

    /**
     * list of events if makeLists=true on countEvents
     */
    public final ArrayList<BasicEvent> tpList = new ArrayList(LIST_LENGTH),
            fnList = new ArrayList(LIST_LENGTH),
            fpList = new ArrayList(LIST_LENGTH),
            tnList = new ArrayList(LIST_LENGTH); // output of classification

    public SignalNoisePacket(Class<? extends SignalNoiseEvent> eventClass) {
        super(eventClass);
    }

    public String toString() {
        return String.format("Tot=%,d S=%,d N=%,d TP=%,d TN=%,d FP=%,d FN=%,d unclass.=%,d ",
                totalCount, signalCount, noiseCount,
                tpCount, tnCount, fpCount, fnCount, unclassifiedCount)
                + super.toString();
    }

    final public void resetCounts() {
        signalCount = 0;
        noiseCount = 0;
        totalCount = 0;
        tpCount = 0;
        tnCount = 0;
        fpCount = 0;
        fnCount = 0;
        unclassifiedCount = 0;
        tpList.clear();
        tnList.clear();
        fpList.clear();
        fnList.clear();
    }

    /**
     * Count the event types and optionally collect events to lists
     *
     * @param makeLists
     */
    final void countClassifications(boolean makeLists) {
        resetCounts();
//        for (SignalNoiseEvent e : this) { // !!! tobi: we cannot use default iterator because it skips all filteredOur events
        int n = getSize();
        for (int k = 0; k < n; k++) { // we cannot use default iterator because it skips all filteredOur events
            SignalNoiseEvent e = getEvent(k);
            if (e.isDVSEvent()) {
                countEvent(e, makeLists);
            }
        }
    }

    /**
     * Copies only noise events to another SignalNoisePacket
     *
     * @param src
     * @param dest
     */
    public static void removeSignalEvents(SignalNoisePacket src, SignalNoisePacket dest) {
        OutputEventIterator<SignalNoiseEvent> outItr = dest.getOutputIterator();
        for (int k = 0; k < src.getSize(); k++) { // we cannot use default iterator because it skips all filteredOur events
            SignalNoiseEvent e = (SignalNoiseEvent) src.getEvent(k);
            if (e.isDVSEvent() && e.isLabeledAsSignal()) {
                outItr.nextOutput().copyFrom(e);
            }
        }
    }

    public void removeSignalEvents() {
        if (tmpSignalNoisePacket == null) {
            tmpSignalNoisePacket = new SignalNoisePacket(SignalNoiseEvent.class);
        }
        tmpSignalNoisePacket.copyNoiseEventsFrom(this);
        this.copyNoiseEventsFrom(tmpSignalNoisePacket); // TODO inefficient....must be better way to replace this by this minus noise
        countClassifications(false);
    }

    /**
     * Count single event
     *
     * @param e
     * @param makeLists
     */
    public void countEvent(SignalNoiseEvent e, boolean makeLists) {
        totalCount++;
        if (e.isLabeledAsSignal()) { // is true signal event
            signalCount++;
            if (e.isClassifiedAsSignal()) {
                tpCount++;
                if (makeLists) {
                    tpList.add(e);
                }
            } else if (e.isClassifiedAsNoise()) {
                fnCount++;
                if (makeLists) {
                    fnList.add(e);
                }
            } else {
                unclassifiedCount++;
            }
        } else { // is true noise event
            // true noise
            noiseCount++;
            if (e.isClassifiedAsNoise()) {
                tnCount++;  // true noise and correctly labeled noise (negative)
                if (makeLists) {
                    tnList.add(e);
                }
            } else if (e.isClassifiedAsSignal()) {
                fpCount++;
                if (makeLists) {
                    fpList.add(e);
                }
            } else {
                unclassifiedCount++;
            }
        }
    }

    /**
     * Copies from another event packet
     *
     * @param in
     */
    final public void copyFrom(EventPacket in) {
        resetCounts();
        OutputEventIterator outItr = outputIterator();
        for (Object o : in) {
            outItr.nextOutput().copyFrom((BasicEvent) o);
        }
    }

    /**
     * Copy from another event packet, labeling all events as signal events that
     * are unclassified
     *
     * @param in
     */
    void copySignalEventsFrom(EventPacket<? extends BasicEvent> in) {
        resetCounts();
        OutputEventIterator outItr = outputIterator();
        for (BasicEvent o : in) {
            SignalNoiseEvent sne = (SignalNoiseEvent) outItr.nextOutput();
            sne.copyFrom((BasicEvent) o);
            sne.labelAsSignal(true);
            sne.unclassify();
        }
    }

    /**
     * Copy from another event packet, labeling all events as signal events that
     * are unclassified
     *
     * @param in
     */
    void copyNoiseEventsFrom(EventPacket<? extends BasicEvent> in) {
        clear();
        OutputEventIterator<SignalNoiseEvent> outItr = getOutputIterator();
        for (int k = 0; k < in.getSize(); k++) { // we cannot use default iterator because it skips all filteredOur events
            SignalNoiseEvent e = (SignalNoiseEvent) in.getEvent(k);
            if (e.isDVSEvent() && !e.isLabeledAsSignal()) {
                outItr.nextOutput().copyFrom(e);
            }
        }
        resetCounts();
    }

}
