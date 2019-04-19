/*
 * Copyright (C) 2019 Tobi.
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
package ch.unizh.ini.jaer.projects.ahuber.filter;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import scala.actors.threadpool.Arrays;

/**
 * Implementation of theory in paper for event-based sparse linear filtering of
 * DVS send-on-delta events. Subclasses produce output image of filtered values
 * that are updated on each pixel event according to the filter type. E.g.
 * lowpass filter represents a smoothed representation of the reconstructed DVS
 * log intensity values, while highpass filter shows a temporal highpass
 * representation of reconstructed log intensities.
 *
 * @author Tobi Delbruck <tobi@ini.uzh.ch>, Huber Adrian
 * <huberad@student.ethz.ch>
 */
public class BandpassEventFilter extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    private final static float PI2 = (float) (Math.PI * 2);
    /**
     * The filter time constant in ms. Default value is 10ms.
     */
    private float tauMsCutoff = getFloat("tauMsCorner", 10);
    /**
     * The filter time constant in ms. Default value is 1000ms.
     */
    private float tauMsCorner = getFloat("tauMsCorner", 1000);

    private int bufferLengthEventsPerPixel = getInt("bufferLengthEventsPerPixel", 30); // uses timestamp and value for each pixel; can get expensive
    private boolean showFilterOutput = getBoolean("showFilterOutput", true);

    // rotating bufffer memory to hold past events at each pixel, index is [x][y][events]; index to last event is stored in pointers
    private int[][][] timestamps = null;
    private int[][] counts = null;
    private boolean[][][] polarities = null;
    private boolean[][] initialized = null;
    private int[][] pointers = null;
    private float[][] output = null;

    private ImageDisplay outputDisplay = null;
    private JFrame outputFrame = null;

    public BandpassEventFilter(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkMemory();
        for (Object o : in) {
            if (!(o instanceof PolarityEvent)) {
                throw new RuntimeException("must be PolarityEvent to process");
            }
            PolarityEvent e = (PolarityEvent) o;
            filterEvent(e);
        }
        return in;
    }

    @Override
    public void resetFilter() {
        if (output != null) {
            for (float[] f : output) {
                Arrays.fill(f, .5f);
            }
        }
    }

    @Override
    public void initFilter() {

    }

    /**
     * Filters the incoming signal according to /br      <code>
     * float ret= hpFilter.filter(lpFilter.filter(val,time),time);
     * </code>
     *
     * @param val the incoming sample
     * @param time the time of the sample in us
     * @return the filter output value
     */
    public float filterEvent(PolarityEvent ev) {
        counts[ev.x][ev.y]++;
        int ptr=pointers[ev.x][ev.y];
        ptr++; if(ptr>=bufferLengthEventsPerPixel) {
            ptr=0; initialized[ev.x][ev.y]=true;
        }
        timestamps[ev.x][ev.y][ptr]=ev.timestamp;
        // now we have everything, process filter based on this and past delta events
        output[ev.x][ev.y] += ev.getPolaritySignum() *.1f; // only for debug
        return output[ev.x][ev.y];
    }

    /**
     * Set the highpass corner frequency
     *
     * @param hz the frequency in Hz
     */
    public void set3dBCornerFrequencyHz(float hz) {
        setTauMsCorner(1000 / (PI2 * hz));

    }

    /**
     * Set the lowpass (rolloff) cutoff frequency
     *
     * @param hz the frequency in Hz
     */
    public void set3dBCutoffFrequencyHz(float hz) {
        setTauMsCutoff(1000 / (PI2 * hz));

    }

    public float get3dBCornerFrequencyHz() {
        float freq = 1000 / (PI2 * getTauMsCorner());
        return freq;
    }

    public float get3dBCutoffFrequencyHz() {
        float freq = 1000 / (PI2 * getTauMsCutoff());
        return freq;
    }

    /**
     * @return the tauMsCutoff
     */
    public float getTauMsCutoff() {
        return tauMsCutoff;
    }

    /**
     * @param tauMsCutoff the tauMsCutoff to set
     */
    public void setTauMsCutoff(float tauMsCutoff) {
        this.tauMsCutoff = tauMsCutoff;
    }

    /**
     * @return the tauMsCorner
     */
    public float getTauMsCorner() {
        return tauMsCorner;
    }

    /**
     * @param tauMsCorner the tauMsCorner to set
     */
    public void setTauMsCorner(float tauMsCorner) {
        this.tauMsCorner = tauMsCorner;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        // if the filter output exists, show it in separate ImageDisplay
        if (output != null) {
            if (outputDisplay == null) {
                outputDisplay = ImageDisplay.createOpenGLCanvas();
                outputDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());
                outputDisplay.setGrayValue(0.5f);
                outputFrame = new JFrame("BandpassEventFilter");
                outputFrame.setPreferredSize(new Dimension(400, 400));
                outputFrame.getContentPane().add(outputDisplay, BorderLayout.CENTER);
                outputFrame.pack();
                outputFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent e) {
                        setShowFilterOutput(false);
                    }

                });
            }
        }
        outputDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());
        outputDisplay.setPixmapFromGrayArray(output);
        outputDisplay.repaint();
    }

    private class FilterMemory { // one per pixel

    }

    private void checkMemory() {
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        if (output == null || output.length != sx || output[0].length != sy) {
            output = new float[sx][sy];
            pointers = new int[sx][sy];
            polarities = new boolean[sx][sy][bufferLengthEventsPerPixel];
            timestamps = new int[sx][sy][bufferLengthEventsPerPixel];
            initialized = new boolean[sx][sy];
            counts = new int[sx][sy];
        }
    }

    /**
     * @return the showFilterOutput
     */
    public boolean isShowFilterOutput() {
        return showFilterOutput;
    }

    /**
     * @param showFilterOutput the showFilterOutput to set
     */
    public void setShowFilterOutput(boolean showFilterOutput) {
        this.showFilterOutput = showFilterOutput;
        if (showFilterOutput && !outputFrame.isVisible()) {
            outputFrame.setVisible(true);
        }
    }

}
