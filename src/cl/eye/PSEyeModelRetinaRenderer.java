/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import ch.unizh.ini.jaer.chip.dvs320.*;
import java.awt.geom.Point2D.Float;
import java.beans.PropertyChangeSupport;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.util.filter.LowpassFilter2d;

/**
 * Renders complex data from PSEyeModelRetina chip.
 *
 * @author tobi
 */
public class PSEyeModelRetinaRenderer extends RetinaRenderer {

    private PSEyeCLModelRetina psEye = null;
    private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
    private int sizeX = 1;

    public PSEyeModelRetinaRenderer(PSEyeCLModelRetina chip) {
        super(chip);
        psEye = chip;
    }

    @Override
    public synchronized void render(EventPacket packet) {


        // rendering is a hack to use the standard pixmap to duplicate pixels on left side (where 32x32 cDVS array lives) with superimposed Brighter, Darker, Redder, Bluer, and log intensity values,
        // and to show DVS test pixel events on right side (where the 64x64 total consisting of 4x 32x32 types of pixels live)

        if (packet == null) {
            return;
        }
        this.packet = packet;
        if (packet.getEventClass() != cDVSEvent.class) {
            log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + cDVSEvent.class);
            return;
        }
        checkPixmapAllocation();
        float[] f = getPixmapArray();
        sizeX = chip.getSizeX();
        final boolean showColorChange = psEye.isColorMode();
        final boolean showLogIntensityChange = true;
        selectedPixelEventCount = 0; // init it for this packet
        try {
            if (!accumulateEnabled) {
                resetFrame(.5f);
            }
            step = 1f / (colorScale);
            for (Object obj : packet) {
                cDVSEvent e = (cDVSEvent) obj;
                int type = e.getType();
                if (e.x == xsel && e.y == ysel) {
                    playSpike(type);
                }
                int x = e.x, y = e.y;
                switch (e.eventType) {
                    case Brighter:
                        if (showLogIntensityChange) {
                            changePixel(x, y, f, brighter, step);
                        }
                        break;
                    case Darker:
                        if (showLogIntensityChange) {
                            changePixel(x, y, f, darker, step);
                        }

                        break;
                    case Redder:
                        if (showColorChange) {
                            changePixel(x, y, f, redder, step);
                        }
                        break;
                    case Bluer:
                        if (showColorChange) {
                            changePixel(x, y, f, bluer, step);
                        }
                        break;
                }
            }
//            if (isDisplayLogIntensity()) {
//                CDVSLogIntensityFrameData b = psEye.getFrameData();
//                try {
//                    b.acquire(); // gets the lock to prevent buffer swapping during display
//                    float[] pm = getPixmapArray();
//                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
//                    for (int y = 0; y < cDVSTest20.SIZE_Y_CDVS; y++) {
//                        for (int x = 0; x < cDVSTest20.SIZE_X_CDVS; x++) {
//                            int count = b.get(x, y);
//                            if (agcEnabled) {
//                                if (count < min) {
//                                    min = count;
//                                } else if (count > max) {
//                                    max = count;
//                                }
//                            }
//                            float v = adc01normalized(count);
//                            if (v > 1) {
//                                v = 1;
//                            }
//                            float[] vv = {v, v, v};
//                            changeCDVSPixel(x, y, pm, vv, 1);
//                        }
//                    }
//                    if (agcEnabled && (min > 0 && max > 0)) { // don't adapt to first frame which is all zeros
//                        Float filter2d = agcFilter.filter2d(min, max, b.getTimestamp());
//                        getSupport().firePropertyChange(AGC_VALUES, null, filter2d); // inform listeners (GUI) of new AGC min/max filterd log intensity values
//                    }
//                } catch (IndexOutOfBoundsException ex) {
//                    log.warning(ex.toString());
//                } finally {
//                    b.release(); // releases the lock
//                }
//            }
            autoScaleFrame(f);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        pixmap.rewind();
    }

    private boolean isCDVSArray(cDVSEvent e) {
        return e.x < cDVSTest20.SIZE_X_CDVS;
    }

    /** x,y refer to x,y space of pixels */
    private void changePixel(int x, int y, float[] f, float[] c, float step) {
        int ind;
        ind = 3 * (x + y * sizeX);
        float r = c[0] * step, g = c[1] * step, b = c[2] * step;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;
    }
}
