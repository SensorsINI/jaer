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
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Jean-Jaques Slotine SLAM without linearization
 *
 * @author Robin Deuber, Tobi Delbruck
 */
@Description("Jean-Jaques Slotine SLAM without linearization")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class JJSlam extends EventFilter2D implements FrameAnnotater {

    private float gainPropertional = getFloat("gainPropertional", 1f);
    private float cameraFocalLengthMm = getFloat("cameraFocalLengthMm", 8);
    private TextRenderer textRenderer = null;
    private CameraPose cameraPose = new CameraPose();

    private ImuMedianTracker tracker = null;

    public JJSlam(AEChip chip) {
        super(chip);
        setPropertyTooltip("gainPropertional", "feedback gain for reducing errrow");
        setPropertyTooltip("cameraFocalLengthMm", "lens focal length in mm");
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

    private void updateState(ApsDvsEvent e) {
        IMUSample imuSample = e.getImuSample();
        float panRateDpsSample = imuSample.getGyroYawY(); // update pan tilt roll state from IMU
        float tiltRateDpsSample = imuSample.getGyroTiltX();
        float rollRateDpsSample = imuSample.getGyroRollZ();
//        log.info(cameraPose.toString());
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
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
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        }
        textRenderer.beginRendering(sx, sy);
        gl.glColor4f(1, 1, 0, .7f);
        textRenderer.draw(cameraPose.toString(), 1, sy / 2);
        textRenderer.endRendering();
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

    private class CameraPose {

        float[] x = new float[3], u = new float[3];

        @Override
        public String toString() {
            return String.format("CameraPose: [x,y,z]=[%.2f,%.2f,%.2f], [ux,uy,uz]=[%.2f,%.2f,%.2f]",
                    x[0], x[1], x[2],
                    u[0], u[1], u[2]);
        }

    }

    class ImuMedianTracker extends MedianTracker {

        public ImuMedianTracker(AEChip chip) {
            super(chip);
        }

        @Override
        public EventPacket filterPacket(EventPacket in) {
            if (!(in instanceof ApsDvsEventPacket)) {
                throw new RuntimeException("only works with Davis packets that have IMU data");
            }
            ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
            int n = in.getSize();

            lastts = in.getLastTimestamp();
            dt = lastts - prevlastts;
            prevlastts = lastts;

            int[] xs = new int[n], ys = new int[n];// big enough for all events, including IMU and APS events if there are those too
            int index = 0;
            Iterator itr = packet.fullIterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                ApsDvsEvent e = (ApsDvsEvent) itr.next();
                if (e.isImuSample()) {
                    processEvents(xs, ys, index);
                    index = 0;
                    updateState(e);
                } else if (e.isDVSEvent()) {
                    xs[index] = e.x;
                    ys[index] = e.y;
                    index++;
                }
            }
            return in;
        }

        private void processEvents(int[] xs, int[] ys, int count) {
            if (count < 1) {
                return;
            }
            Arrays.sort(xs, 0, count); // only sort up to index because that's all we saved
            Arrays.sort(ys, 0, count);
            float x, y;
            if (count % 2 != 0) { // odd number points, take middle one, e.g. n=3, take element 1
                x = xs[count / 2];
                y = ys[count / 2];
            } else { // even num events, take avg around middle one, eg n=4, take avg of elements 1,2
                x = (float) (((float) xs[count / 2 - 1] + xs[count / 2]) / 2f);
                y = (float) (((float) ys[count / 2 - 1] + ys[count / 2]) / 2f);
            }
            xmedian = xFilter.filter(x, lastts);
            ymedian = yFilter.filter(y, lastts);
            int xsum = 0, ysum = 0;
            for (int i = 0; i < count; i++) {
                xsum += xs[i];
                ysum += ys[i];
            }
            float instantXmean = xsum / count;
            float instantYmean = ysum / count;
            float xvar = 0, yvar = 0;
            float tmp;
            for (int i = 0; i < count; i++) {
                tmp = xs[i] - instantXmean;
                tmp *= tmp;
                xvar += tmp;

                tmp = ys[i] - instantYmean;
                tmp *= tmp;
                yvar += tmp;
            }
            xvar /= count;
            yvar /= count;
            xstd = xStdFilter.filter((float) Math.sqrt(xvar), lastts);
            ystd = yStdFilter.filter((float) Math.sqrt(yvar), lastts);
            xmean = xMeanFilter.filter(instantXmean, lastts);
            ymean = yMeanFilter.filter(instantYmean, lastts);
            medianPoint.setLocation(xmedian, ymedian);
            meanPoint.setLocation(instantXmean, instantYmean);
            stdPoint.setLocation(xstd * numStdDevsForBoundingBox, ystd * numStdDevsForBoundingBox);
        }

    }

    /**
     * @return the cameraFocalLengthMm
     */
    public float getCameraFocalLengthMm() {
        return cameraFocalLengthMm;
    }

    /**
     * @param cameraFocalLengthMm the cameraFocalLengthMm to set
     */
    public void setCameraFocalLengthMm(float cameraFocalLengthMm) {
        this.cameraFocalLengthMm = cameraFocalLengthMm;
        putFloat("cameraFocalLengthMm", cameraFocalLengthMm);
    }
}
