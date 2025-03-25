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

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;

/**
 * Special event type for denoiser development
 * 
 * @author tobi
 */
public class SignalNoiseEvent extends ApsDvsEvent {

    private boolean labeledAsSignal = true;
    private boolean classifiedAsSignal = false;
    private boolean classifiedAsNoise = false;

    public SignalNoiseEvent() {
    }

    @Override
    public String toString() {
        return String.format("label=%s classification=%s: %s", label(), classification(), super.toString());
    }

    /** Returns "signal" or "noise" label string */
    public String label(){
        return labeledAsSignal?"signal":"noise";
    }
    
    /** Returns "unclassified", "noise", or "signal" classification label */
    public String classification() {
        if (!classifiedAsNoise && !classifiedAsSignal) {
            return "unclassified";
        } else if (classifiedAsNoise) {
            return "noise";
        } else {
            return "signal";
        }
    }

    /**
     * Copies the source event, and assumes event is a labeledAsSignal event, so
     * that labeledAsSignal is set true the event is unclassified.
     * If the source event is a SignalNoiseEvent, its fields are copied.
     *
     * @param src the source event
     */
    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if (src instanceof SignalNoiseEvent e) {
            this.labelAsSignal(e.isLabeledAsSignal());
            this.setClassifyAsSignal(e.isClassifiedAsSignal());
            this.setClassifyAsNoise(e.isClassifiedAsNoise());
        } else {
            labelAsSignal(true);
            unclassify();
        }
    }

    public final void unclassify() {
        setClassifyAsNoise(false);
        setClassifyAsSignal(false);
    }

    /** Mark the event as classified as signal */
    public final void classifySignal() {
        setClassifyAsSignal(true);
        setClassifyAsNoise(false);
    }

    /** Mark the event as classified as noise */
    public final void classifyNoise() {
        setClassifyAsSignal(false);
        setClassifyAsNoise(true);
    }

    /**
     * @return the labeledAsSignal
     */
    public final boolean isLabeledAsSignal() {
        return labeledAsSignal;
    }

    /**
     * @param signal the labeledAsSignal to set
     */
    public final void labelAsSignal(Boolean signal) {
        this.labeledAsSignal = signal;
    }

    /**
     * @return the classifiedAsSignal
     */
    public boolean isClassifiedAsSignal() {
        return classifiedAsSignal;
    }

    /**
     * @param classifiedAsSignal the classifiedAsSignal to set
     */
    public void setClassifyAsSignal(Boolean classifiedAsSignal) {
        this.classifiedAsSignal = classifiedAsSignal;
    }

    /**
     * @return the classifiedAsNoise
     */
    public boolean isClassifiedAsNoise() {
        return classifiedAsNoise;
    }

    /**
     * @param classifiedAsNoise the classifiedAsNoise to set
     */
    public void setClassifyAsNoise(boolean classifiedAsNoise) {
        this.classifiedAsNoise = classifiedAsNoise;
    }

}
