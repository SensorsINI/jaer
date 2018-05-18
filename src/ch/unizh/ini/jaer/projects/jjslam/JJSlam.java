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
package ch.unizh.ini.jaer.projects.jjslam;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Jean-Jaques Slotine SLAM without linearization
 *
 * @author Robin Deuber
 */
public class JJSlam extends EventFilter2D implements FrameAnnotater {

    private float gainPropertional = getFloat("gainPropertional", 1f);
    private ImuMedianTracker tracker = null;

    public JJSlam(AEChip chip) {
        super(chip);
        setPropertyTooltip("gainPropertional", "feedback gain for reducing errrow");
        tracker = new ImuMedianTracker(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        tracker.filterPacket(in);
        Point2D.Float p = (Point2D.Float) tracker.getMedianPoint();
        float d = (float) Math.sqrt(p.x * p.x + p.y * p.y);

        return in;
    }

    private void updateState(IMUSample imuSample) {
//            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        tracker.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int sy = chip.getSizeY(), sx = chip.getSizeX();

    }

    /**
     * @return the gainPropertional
     */
    public float getGainPropertional() {
        return gainPropertional;
    }

    /**
     * @param gainPropertional the gainPropertional to set
     */
    public void setGainPropertional(float gainPropertional) {
        this.gainPropertional = gainPropertional;
        putFloat("gainPropertional", gainPropertional);
    }

    class ImuMedianTracker extends MedianTracker {

        public ImuMedianTracker(AEChip chip) {
            super(chip);
        }

        @Override
        public EventPacket filterPacket(EventPacket in) {
            int n = in.getSize();

            lastts = in.getLastTimestamp();
            dt = lastts - prevlastts;
            prevlastts = lastts;

            int[] xs = new int[n], ys = new int[n];// big enough for all events, including IMU and APS events if there are those too
            int index = 0;
            for (Object o : in) {
                BasicEvent e = (BasicEvent) o;
                if (o instanceof IMUSample) {
                    updateState((IMUSample) o);
                }
                if (e.isSpecial()) {
                    continue;
                }
                xs[index] = e.x;
                ys[index] = e.y;
                index++;
            }
            if (index == 0) { // got no actual events
                return in;
            }
            Arrays.sort(xs, 0, index); // only sort up to index because that's all we saved
            Arrays.sort(ys, 0, index);
            float x, y;
            if (index % 2 != 0) { // odd number points, take middle one, e.g. n=3, take element 1
                x = xs[index / 2];
                y = ys[index / 2];
            } else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
                x = (float) (((float) xs[index / 2 - 1] + xs[index / 2]) / 2f);
                y = (float) (((float) ys[index / 2 - 1] + ys[index / 2]) / 2f);
            }
            xmedian = xFilter.filter(x, lastts);
            ymedian = yFilter.filter(y, lastts);
            int xsum = 0, ysum = 0;
            for (int i = 0; i < index; i++) {
                xsum += xs[i];
                ysum += ys[i];
            }
            float instantXmean = xsum / index;
            float instantYmean = ysum / index;

            float xvar = 0, yvar = 0;
            float tmp;
            for (int i = 0; i < index; i++) {
                tmp = xs[i] - instantXmean;
                tmp *= tmp;
                xvar += tmp;

                tmp = ys[i] - instantYmean;
                tmp *= tmp;
                yvar += tmp;
            }
            xvar /= index;
            yvar /= index;

            xstd = xStdFilter.filter((float) Math.sqrt(xvar), lastts);
            ystd = yStdFilter.filter((float) Math.sqrt(yvar), lastts);

            xmean = xMeanFilter.filter(instantXmean, lastts);
            ymean = yMeanFilter.filter(instantYmean, lastts);

            medianPoint.setLocation(xmedian, ymedian);
            meanPoint.setLocation(instantXmean, instantYmean);
            stdPoint.setLocation(xstd * numStdDevsForBoundingBox, ystd * numStdDevsForBoundingBox);

            return in; // xs and ys will now be sorted, output will be bs because time will not be sorted like addresses
        }

    }
}
