/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.RetinaRenderer;

/**
 * Renders complex data from cDVS chips.
 *
 * @author tobi
 */
public class cDVSRenderer extends RetinaRenderer {

    private cDVSTest20 cDVSChip = null;
    private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
    private int sizeX = 1;

    public cDVSRenderer(cDVSTest20 chip) {
        super(chip);
        cDVSChip = chip;
    }

    @Override
    public synchronized void render(EventPacket packet) {

        final float MAX_ADC = (float)((1<<11)-1);

        // rendering is a hack to use the standard pixmap to duplicate pixels on left side (where 32x32 cDVS array lives) with superimposed Brighter, Darker, Redder, Bluer, and log intensity values,
        // and to show DVS test pixel events on right side (where the 64x64 total consisting of 4x 32x32 types of pixels live)

        if (packet == null) {
            return;
        }
        this.packet = packet;
        if (packet.getEventClass() != cDVSEvent.class) {
            throw new RuntimeException("wrong type of event " + packet.getEventClass() + "; should be " + cDVSEvent.class);
        }
        checkPixmapAllocation();
        float[] f = getPixmapArray();
        sizeX = chip.getSizeX();
        final boolean showColorChange = isDisplayColorChangeEvents();
        final boolean showLogIntensityChange = isDisplayLogIntensityChangeEvents();
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
                if (isCDVSArray(e)) { // address is in cDVS array
                    int x = e.x, y = e.y;
                    switch (e.eventType) {
                        case Brighter:
                            if (showLogIntensityChange) {
                                changeCDVSPixel(x, y, f, brighter, step);
                            }
                            break;
                        case Darker:
                            if (showLogIntensityChange) {
                                changeCDVSPixel(x, y, f, darker, step);
                            }

                            break;
                        case Redder:
                            if (showColorChange) {
                                changeCDVSPixel(x, y, f, redder, step);
                            }
                            break;
                        case Bluer:
                            if (showColorChange) {
                                changeCDVSPixel(x, y, f, bluer, step);
                            }
                            break;
                    }
                } else { // address is in DVS arrays
                    if (!showLogIntensityChange) {
                        continue;
                    }
                    int ind = getPixMapIndex(e.x, e.y);
                    switch (e.eventType) {
                        case Brighter:
                            f[ind] += step;
                            f[ind + 1] += step;
                            f[ind + 2] += step;
                            break;
                        case Darker:
                            f[ind] -= step;
                            f[ind + 1] -= step;
                            f[ind + 2] -= step;
                    }
                }
            }
            if (isDisplayLogIntensity()) {
                CDVSLogIntensityFrameData b = cDVSChip.getFrameData();
                try {
                    b.acquire();
                    float gain=cDVSChip.getLogIntensityGain(), offset=cDVSChip.getLogIntensityOffset();
                    float[] pm = getPixmapArray();
                    for (int y = 0; y < cDVSTest20.SIZE_Y_CDVS; y++) {
                        for (int x = 0; x < cDVSTest20.SIZE_X_CDVS; x++) {
                            float v = gain*(b.get(x, y) / MAX_ADC+offset);
                            if(v>1)v=1;
                            float[] vv = {v, v, v};
                            changeCDVSPixel(x, y, pm, vv, 1);
                        }
                    }
                } catch (IndexOutOfBoundsException ex) {
                    log.warning(ex.toString());
                } finally {
                    b.release();
                }
            }
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

    /** x,y refer to 32x32 space of cDVS pixels but rendering space for cDVS is 64x64 */
    private void changeCDVSPixel(int x, int y, float[] f, float[] c, float step) {
        int ind;
        ind = 3 * (2*x + 2*y * sizeX);
        float r = c[0] * step, g = c[1] * step, b = c[2] * step;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind += 3;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind += sizeX * 3;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind -= 3;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;
    }

    public void setDisplayLogIntensityChangeEvents(boolean displayLogIntensityChangeEvents) {
        cDVSChip.setDisplayLogIntensityChangeEvents(displayLogIntensityChangeEvents);
    }

    public void setDisplayLogIntensity(boolean displayLogIntensity) {
        cDVSChip.setDisplayLogIntensity(displayLogIntensity);
    }

    public void setDisplayColorChangeEvents(boolean displayColorChangeEvents) {
        cDVSChip.setDisplayColorChangeEvents(displayColorChangeEvents);
    }

    public boolean isDisplayLogIntensityChangeEvents() {
        return cDVSChip.isDisplayLogIntensityChangeEvents();
    }

    public boolean isDisplayLogIntensity() {
        return cDVSChip.isDisplayLogIntensity();
    }

    public boolean isDisplayColorChangeEvents() {
        return cDVSChip.isDisplayColorChangeEvents();
    }
}
