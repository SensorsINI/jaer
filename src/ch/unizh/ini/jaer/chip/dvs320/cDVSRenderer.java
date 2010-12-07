/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OrientationEvent;
import net.sf.jaer.graphics.RetinaRenderer;

/**
 * Renders complex data from cDVS chips.
 *
 * @author tobi
 */
public class cDVSRenderer extends RetinaRenderer {

    private cDVSTest20 cDVSChip = null;
    private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
    private int sizeX=1;
    public cDVSRenderer(cDVSTest20 chip) {
        super(chip);
        cDVSChip = chip;
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
            throw new RuntimeException("wrong type of event " + packet.getEventClass() + "; should be " + cDVSEvent.class);
        }
        checkPixmapAllocation();
        float[] f = getPixmapArray();
        sizeX=chip.getSizeX();
        final boolean showColorChange=isDisplayColorChangeEvents();
        final boolean showLogIntensityChange=isDisplayLogIntensityChangeEvents();
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
                int ind = getPixMapIndex(e.x, e.y);
                if (isCDVSArray(e)) {
                    switch (e.eventType) {
                        case Brighter:
                            if (showLogIntensityChange) {
                                changeBlock(e, f, brighter, step);
                            }
                            break;
                        case Darker:
                            if (showLogIntensityChange) {
                                changeBlock(e, f, darker, step);
                            }

                            break;
                        case Redder:
                            if (showColorChange) {
                                changeBlock(e, f, redder, step);
                            }
                            break;
                        case Bluer:
                            if (showColorChange) {
                                changeBlock(e, f, bluer, step);
                            }
                            break;
                    }
                } else {
                    if(!showLogIntensityChange) continue;
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
            autoScaleFrame(f);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        pixmap.rewind();
    }

    private boolean isCDVSArray(cDVSEvent e) {
        return e.x < cDVSTest20.SIZE_X_CDVS;
    }

    private void changeBlock(cDVSEvent e, float[] f, float[] c, float step) {
        int ind;
        int x=e.x<<1, y=e.y<<1;
        ind = 3 * (x + y * sizeX);
        float r=c[0]*step, g=c[1]*step, b=c[2]*step;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind+=3;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind+=sizeX*3;
        f[ind] += r;
        f[ind + 1] += g;
        f[ind + 2] += b;

        ind-=3;
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
