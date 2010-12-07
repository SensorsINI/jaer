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
                            if (isDisplayLogIntensityChangeEvents()) {
                                changeBlock(e, brighter, step);
                            }
                            break;
                        case Darker:
                            if (isDisplayLogIntensityChangeEvents()) {
                                changeBlock(e, darker, step);
                            }

                            break;
                        case Redder:
                            if (isDisplayColorChangeEvents()) {
                                changeBlock(e, redder, step);
                            }
                            break;
                        case Bluer:
                            if (isDisplayColorChangeEvents()) {
                                changeBlock(e, bluer, step);
                            }
                            break;
                    }
                } else {
                    if(!isDisplayLogIntensityChangeEvents()) continue;
                    switch (e.eventType) {
                        case Brighter:
                            f[ind] += step; //if(f[0]>1f) f[0]=1f;
                            f[ind + 1] += step; //if(f[1]>1f) f[1]=1f;
                            f[ind + 2] += step; //if(f[2]>1f) f[2]=1f;
                            break;
                        case Darker:
                            f[ind] -= step; //if(f[0]>1f) f[0]=1f;
                            f[ind + 1] -= step; //if(f[1]>1f) f[1]=1f;
                            f[ind + 2] -= step; //if(f[2]>1f) f[2]=1f;
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

    private void changeBlock(cDVSEvent e, float[] c, float step) {
        float[] f = getPixmapArray();
        int ind;
        int x=e.x<<1, y=e.y<<1;
        ind = getPixMapIndex(x, y);
        f[ind] += c[0] * step;
        f[ind + 1] += c[1] * step;
        f[ind + 2] += c[2] * step;

        ind = getPixMapIndex(x+1, y);
        f[ind] += c[0] * step;
        f[ind + 1] += c[1] * step;
        f[ind + 2] += c[2] * step;

        ind = getPixMapIndex(x, y+1);
        f[ind] += c[0] * step;
        f[ind + 1] += c[1] * step;
        f[ind + 2] += c[2] * step;

        ind = getPixMapIndex(x + 1, y + 1);
        f[ind] += c[0] * step;
        f[ind + 1] += c[1] * step;
        f[ind + 2] += c[2] * step;
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
