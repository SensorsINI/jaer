/*
 * Copyright (C) 2018 tobid.
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
package net.sf.jaer.util.textoutput;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 * Writes out text format DVS, IMU and frame data from DAVIS cameras
 *
 * @author tobid
 */
@Description("Writes out text format files with data from DAVIS cameras")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class DavisTextOutputWriter extends AbstractTextOutputWriter {

    private boolean dvsEvents = getBoolean("dvsEvents", true);
    private boolean apsFrames = getBoolean("apsFrames", false);
    private boolean imuSamples = getBoolean("imuSamples", false);

    public DavisTextOutputWriter(AEChip chip) {
        super(chip);
        additionalComments.add("jAER text file output");
        additionalComments.add("created " + new Date().toString());
        additionalComments.add("dvs-events: timestamp(us) x y polarity(0=off,1=on)");
        setPropertyTooltip("dvsEvents", "write dvs events as one per line with format timestamp(us) x y polarity(0=off,1=on)");
        setPropertyTooltip("imuSamples", "write IMU samples as one per line with format TBD");
        setPropertyTooltip("apsFrames", "write APS frames with format TBD");

    }

    /**
     * Processes packet to write output
     *
     * @param in input packet
     * @return input packet
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        in = super.filterPacket(in);
        if (!isWriteEnabled() || (!dvsEvents && !imuSamples && !apsFrames)) {
            return in;
        }
        try {
            for (BasicEvent e : in) {
                PolarityEvent pe = (PolarityEvent) e;
                if (dvsEvents) {
                    // One event per line (timestamp x y polarity) as in RPG events.txt
                    getFileWriter().write(String.format("%d %d %d %d\n", e.timestamp, e.x, e.y, pe.type));
                    incrementCountAndMaybeCloseOutput();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DavisTextOutputWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return in;
    }

    /**
     * @return the dvsEvents
     */
    public boolean isDvsEvents() {
        return dvsEvents;
    }

    /**
     * @param dvsEvents the dvsEvents to set
     */
    public void setDvsEvents(boolean dvsEvents) {
        this.dvsEvents = dvsEvents;
        putBoolean("dvsEvents", dvsEvents);
    }

    /**
     * @return the apsFrames
     */
    public boolean isApsFrames() {
        return apsFrames;
    }

    /**
     * @param apsFrames the apsFrames to set
     */
    public void setApsFrames(boolean apsFrames) {
        this.apsFrames = apsFrames;
        putBoolean("apsFrames", apsFrames);
    }

    /**
     * @return the imuSamples
     */
    public boolean isImuSamples() {
        return imuSamples;
    }

    /**
     * @param imuSamples the imuSamples to set
     */
    public void setImuSamples(boolean imuSamples) {
        this.imuSamples = imuSamples;
        putBoolean("imuSamples", imuSamples);
    }

}
