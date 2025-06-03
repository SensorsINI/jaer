/*
 * Copyright (C) 2020 Tobi.
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
package eu.spacestones;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Makes absolute timestamps from GPS receiver input pulses
 *
 * @author Tobi
 */
@Description("Makes absolute timestamps from GPS receiver input pulses")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class GpsTimestamps extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private int gpsPulseIntervalS = getInt("gpsPulseIntervalS", 60);

    private int lastCameraTimestampUs = 0; // last camera timestamp in us
    private double lastAbsoluteTimestampEpochS = 0; // double precision time in seconds since epoch
    private int lastGpsTimestampUs = 0;
    private boolean gotGpsEvent = false;
    private long fileStartingTimeEpochMs = 0; // read from input stream of file
    private boolean propChangeListenerAdded=false;

    public GpsTimestamps(AEChip chip) {
        super(chip);
        setPropertyTooltip("gpsPulseIntervalS", "interval in seconds between GPS input pulses that appear as external input events");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
     if (!propChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEPlayer.EVENT_FILEOPEN, this);
                propChangeListenerAdded = true;
            }
        }
        for (BasicEvent e : in) {
            lastCameraTimestampUs = e.getTimestamp();
            if (e.isSpecial()) {  // assume it is GPS event
                gotGpsEvent = true;
                lastGpsTimestampUs = e.getTimestamp(); // this is camera time in us of GPS pulse
            }
            // compute the absolute time of last event using algorithm
            lastAbsoluteTimestampEpochS = 0; // TODO fix

        }
        return in;
    }

    @Override
    public void resetFilter() {
        lastCameraTimestampUs = 0;
        gotGpsEvent = false;
        lastAbsoluteTimestampEpochS = 0;
    }

    @Override
    public void initFilter() { // called once at start
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        // fill in code to draw/write on screen
        GL2 gl = drawable.getGL().getGL2();  // use it to draw, see zillions of examples in jaer filters (use find usage)

        MultilineAnnotationTextRenderer.setColor(Color.yellow);
        MultilineAnnotationTextRenderer.setFontSize(9);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(0.8f * chip.getSizeY());
        String s = String.format("last absolute time is XXX\nhere is another line, last gps timestamp=%d", lastGpsTimestampUs);
        MultilineAnnotationTextRenderer.renderMultilineString(s);

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getPropertyName() == AEPlayer.EVENT_FILEOPEN || evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        }
        if (evt.getPropertyName() == AEPlayer.EVENT_FILEOPEN) {
            AEFileInputStream f = (AEFileInputStream)chip.getAeViewer().getAePlayer().getAEInputStream();
            fileStartingTimeEpochMs = f.getAbsoluteStartingTimeMs();
            Date date=new Date(fileStartingTimeEpochMs);
            log.info("new  starting time is "+fileStartingTimeEpochMs+" in ms since epoch, which is "+date.toString());

        }
    }

    /**
     * @return the gpsPulseIntervalS
     */
    public int getGpsPulseIntervalS() {
        return gpsPulseIntervalS;
    }

    /**
     * @param gpsPulseIntervalS the gpsPulseIntervalS to set
     */
    public void setGpsPulseIntervalS(int gpsPulseIntervalS) {
        this.gpsPulseIntervalS = gpsPulseIntervalS;
        putInt("gpsPulseIntervalS", gpsPulseIntervalS);
    }

}
