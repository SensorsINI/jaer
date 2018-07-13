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
package org.ine.telluride.jaer.tell2018;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Live pendulum modeling for studying satellite tracking.
 *
 * @author tobid, gregc, yiannisa, rohit, emilyh, jenh
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("Live pendulum modeling for studying satellite tracking (Telluride 2018")
public class PendulumTracker extends EventFilter2D implements FrameAnnotater {

    private RectangularClusterTracker tracker = new RectangularClusterTracker(chip);
    private Pendulum pendulum = null; // lazy so we have chip already built

    public PendulumTracker(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(tracker);
        tracker.getSupport().addPropertyChangeListener(this);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (pendulum == null) {
            pendulum = new Pendulum(in.getLastTimestamp());
        }
        in = getEnclosedFilterChain().filterPacket(in);
        pendulum.updateState(in.getLastTimestamp());

        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (pendulum != null) {
            pendulum.draw(gl);
        }

    }

    private class Pendulum {

        private float freqHz = getFloat("freqHz", 1);
        private float amplDeg = getFloat("amplDeg", 10);
        private float phaseDeg = 0;
        private float fulcrumX = getFloat("fulcrumX", chip.getSizeX() / 2);
        private float fulcrumY = getFloat("fulcrumY", 3 * chip.getSizeY() / 2);
        private float length = getFloat("length", chip.getSizeY());
        private float mixingFactor = getFloat("mixingFactor", .01f);
        private float angleDeg = 0, posX=0, posY=0;
        private int startingTimestamp = 0;

        public Pendulum(int startingTimestamp) {
            this.startingTimestamp = startingTimestamp;
            updateState(startingTimestamp);
        }

        /**
         * @return the freqHz
         */
        public float getFreqHz() {
            return freqHz;
        }

        public void updateState(int timestamp) {
            int tUs = timestamp - startingTimestamp;
            double tS = 1e-6 * tUs;
            angleDeg = amplDeg * (float) Math.sin(tS * freqHz + phaseDeg / Math.PI);
            posX=fulcrumX+length*(float)Math.cos(angleDeg/Math.PI);
            posY=fulcrumY-length*(float)Math.sin(angleDeg/Math.PI);
        }

        public void updateModel(Cluster c) {

        }

        private void draw(GL2 gl) {
            gl.glColor3f(.3f, .3f, 1f);
            gl.glLineWidth(4);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(fulcrumX, fulcrumY);
            gl.glVertex2f(posX,posY);
            gl.glEnd();
        }

        /**
         * @param freqHz the freqHz to set
         */
        public void setFreqHz(float freqHz) {
            float old = this.freqHz;
            this.freqHz = freqHz;
            putFloat("freqHz", freqHz);
            getSupport().firePropertyChange("freqHz", old, freqHz);
        }

        /**
         * @return the amplDeg
         */
        public float getAmplDeg() {
            return amplDeg;
        }

        /**
         * @param amplDeg the amplDeg to set
         */
        public void setAmplDeg(float amplDeg) {
            float old = this.amplDeg;
            this.amplDeg = amplDeg;
            putFloat("amplDeg", amplDeg);
            getSupport().firePropertyChange("amplDeg", old, amplDeg);

        }

        /**
         * @return the phaseDeg
         */
        public float getPhaseDeg() {
            return phaseDeg;
        }

        /**
         * @param phaseDeg the phaseDeg to set
         */
        public void setPhaseDeg(float phaseDeg) {
            this.phaseDeg = phaseDeg;
        }

        /**
         * @return the fulcrumX
         */
        public float getFulcrumX() {
            return fulcrumX;
        }

        /**
         * @param fulcrumX the fulcrumX to set
         */
        public void setFulcrumX(float fulcrumX) {
            float old = this.fulcrumX;
            this.fulcrumX = fulcrumX;
            putFloat("fulcrumX", fulcrumX);
            getSupport().firePropertyChange("fulcrumX", old, fulcrumX);
        }

        /**
         * @return the fulcrumY
         */
        public float getFulcrumY() {
            return fulcrumY;
        }

        /**
         * @param fulcrumY the fulcrumY to set
         */
        public void setFulcrumY(float fulcrumY) {
            float old = this.fulcrumY;
            this.fulcrumY = fulcrumY;
            putFloat("fulcrumY", fulcrumY);
            getSupport().firePropertyChange("fulcrumY", old, fulcrumY);
        }

        /**
         * @return the length
         */
        public float getLength() {
            return length;
        }

        /**
         * @param length the length to set
         */
        public void setLength(float length) {
            float old = this.length;
            this.length = length;
            putFloat("length", length);
            getSupport().firePropertyChange("length", old, length);
        }

        /**
         * @return the mixingFactor
         */
        public float getMixingFactor() {
            return mixingFactor;
        }

        /**
         * @param mixingFactor the mixingFactor to set
         */
        public void setMixingFactor(float mixingFactor) {
            this.mixingFactor = mixingFactor;
            putFloat("mixingFactor", mixingFactor);
        }
    }

}
