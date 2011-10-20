/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.seebetter1011;

import eu.seebetter.ini.chips.*;
import eu.seebetter.ini.chips.cDVSEvent;
import java.awt.geom.Point2D.Float;
import java.beans.PropertyChangeSupport;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.util.filter.LowpassFilter2d;

/**
 * Renders complex data from cDVS chips.
 *
 * @author tobi
 */
public class SeeBettter1011Renderer extends RetinaRenderer {

    private SeeBetter1011 cDVSChip = null;
    private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
    private int sizeX = 1;
    private LowpassFilter2d agcFilter = new LowpassFilter2d();  // 2 lp values are min and max log intensities from each frame
    private boolean agcEnabled;
    /** PropertyChange */
    public static final String AGC_VALUES = "AGCValuesChanged";
    /** PropertyChange when value is changed */
    public static final String LOG_INTENSITY_GAIN = "logIntensityGain", LOG_INTENSITY_OFFSET = "logIntensityOffset";
    /** Control scaling and offset of display of log intensity values. */
    int logIntensityGain, logIntensityOffset;

    public SeeBettter1011Renderer(SeeBetter1011 chip) {
        super(chip);
        cDVSChip = chip;
        agcEnabled = chip.getPrefs().getBoolean("agcEnabled", false);
        setAGCTauMs(chip.getPrefs().getFloat("agcTauMs", 1000));
        logIntensityGain = chip.getPrefs().getInt("logIntensityGain", 1);
        logIntensityOffset = chip.getPrefs().getInt("logIntensityOffset", 0);
    }

   private boolean isCDVSArray(cDVSEvent e) {
        return e.x < SeeBetter1011.SIZE_X_CDVS;
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
//                            if (showColorChange) {
//                                changeCDVSPixel(x, y, f, redder, step);
//                            }
                            break;
                        case Bluer:
//                            if (showColorChange) {
//                                changeCDVSPixel(x, y, f, bluer, step);
//                            }
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
                    b.acquire(); // gets the lock to prevent buffer swapping during display
                    float[] pm = getPixmapArray();
                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                    for (int y = 0; y < SeeBetter1011.SIZE_Y_CDVS; y++) {
                        for (int x = 0; x < SeeBetter1011.SIZE_X_CDVS; x++) {
                            int count = b.get(x, y);
                            if (agcEnabled) {
                                if (count < min) {
                                    min = count;
                                } else if (count > max) {
                                    max = count;
                                }
                            }
                            float v = adc01normalized(count);
                            if (v > 1) {
                                v = 1;
                            }
                            float[] vv = {v, v, v};
                            changeCDVSPixel(x, y, pm, vv, 1);
                        }
                    }
                    if (agcEnabled && (min > 0 && max > 0)) { // don't adapt to first frame which is all zeros
                        Float filter2d = agcFilter.filter2d(min, max, b.getTimestamp());
                        getSupport().firePropertyChange(AGC_VALUES, null, filter2d); // inform listeners (GUI) of new AGC min/max filterd log intensity values
                    }
                } catch (IndexOutOfBoundsException ex) {
                    log.warning(ex.toString());
                } finally {
                    b.release(); // releases the lock
                }
            }
            autoScaleFrame(f);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
        pixmap.rewind();
    }

    /** x,y refer to 32x32 space of cDVS pixels but rendering space for cDVS is 64x64 */
    private void changeCDVSPixel(int x, int y, float[] f, float[] c, float step) {
        int ind;
        ind = 3 * (2 * x + 2 * y * sizeX);
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

    public boolean isDisplayLogIntensityChangeEvents() {
        return cDVSChip.isDisplayLogIntensityChangeEvents();
    }

    public boolean isDisplayLogIntensity() {
        return cDVSChip.isDisplayLogIntensity();
    }

    public void setUseOffChipCalibration(boolean useOffChipCalibration) {
        cDVSChip.setUseOffChipCalibration(useOffChipCalibration);
    }

    public boolean isUseOffChipCalibration() {
        return cDVSChip.isUseOffChipCalibration();
    }


    private float adc01normalized(int count) {
        if (!agcEnabled) {
            float v = (float) (logIntensityGain * (count - logIntensityOffset)) / cDVSChip.MAX_ADC;
            return v;
        } else {
            Float filter2d = agcFilter.getValue2d();
            float offset = filter2d.x;
            float range = (filter2d.y - filter2d.x);
            float v = ((count - offset)) / range;
            return v;
        }
    }

    public float getAGCTauMs() {
        return agcFilter.getTauMs();
    }

    public void setAGCTauMs(float tauMs) {
        if (tauMs < 10) {
            tauMs = 10;
        }
        agcFilter.setTauMs(tauMs);
        chip.getPrefs().putFloat("agcTauMs", tauMs);
    }

    /**
     * @return the agcEnabled
     */
    public boolean isAgcEnabled() {
        return agcEnabled;
    }

    /**
     * @param agcEnabled the agcEnabled to set
     */
    public void setAgcEnabled(boolean agcEnabled) {
        this.agcEnabled = agcEnabled;
        chip.getPrefs().putBoolean("agcEnabled", agcEnabled);
    }

    void applyAGCValues() {
        Float f = agcFilter.getValue2d();
        setLogIntensityOffset(agcOffset());
        setLogIntensityGain(agcGain());
    }

    private int agcOffset() {
        return (int) agcFilter.getValue2d().x;
    }

    private int agcGain() {
        Float f = agcFilter.getValue2d();
        float diff = f.y - f.x;
        if (diff < 1) {
            return 1;
        }
        int gain = (int) (SeeBetter1011.MAX_ADC / (f.y - f.x));
        return gain;
    }

    /**
     * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1. Gain of MAX_ADC makes a single count go full scale.
     * @return the logIntensityGain
     */
    public int getLogIntensityGain() {
        return logIntensityGain;
    }

    /**
     * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1.
     * Gain of MAX_ADC makes a single count go full scale.
     * @param logIntensityGain the logIntensityGain to set
     */
    public void setLogIntensityGain(int logIntensityGain) {
        int old = this.logIntensityGain;
        if (logIntensityGain < 1) {
            logIntensityGain = 1;
        } else if (logIntensityGain > cDVSChip.MAX_ADC) {
            logIntensityGain = cDVSChip.MAX_ADC;
        }
        this.logIntensityGain = logIntensityGain;
        chip.getPrefs().putInt("logIntensityGain", logIntensityGain);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().interruptViewloop();
        }
        getSupport().firePropertyChange(LOG_INTENSITY_GAIN, old, logIntensityGain);
    }

    /**
     * Value subtracted from ADC count before gain multiplication. Ranges from 0 to MAX_ADC.
     * @return the logIntensityOffset
     */
    public int getLogIntensityOffset() {
        return logIntensityOffset;
    }

    /**
     * Sets value subtracted from ADC count before gain multiplication. Clamped between 0 to MAX_ADC.
     * @param logIntensityOffset the logIntensityOffset to set
     */
    public void setLogIntensityOffset(int logIntensityOffset) {
        int old = this.logIntensityOffset;
        if (logIntensityOffset < 0) {
            logIntensityOffset = 0;
        } else if (logIntensityOffset > cDVSChip.MAX_ADC) {
            logIntensityOffset = cDVSChip.MAX_ADC;
        }
        this.logIntensityOffset = logIntensityOffset;
        chip.getPrefs().putInt("logIntensityOffset", logIntensityOffset);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().interruptViewloop();
        }
        getSupport().firePropertyChange(LOG_INTENSITY_OFFSET, old, logIntensityOffset);
    }
}
