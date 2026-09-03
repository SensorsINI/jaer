/*
 * SteadicamHemisphereView.java
 */
package net.sf.jaer.eventprocessing.filter;

import static net.sf.jaer.eventprocessing.EventFilter.log;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * World-fixed equirectangular map painted from DVS events using IMU pose.
 * Horizontal span is {@code hemisphereHorizontalFovDeg} (camera HFOV…360°);
 * vertical span follows the chip aspect ratio
 * ({@code HFOV * sizeY / sizeX}) so the pixmap matches the sensor.
 * Width is {@code HFOV / atan(pitch_x / f)}, capped at {@link #MAX_SIZE}.
 */
class SteadicamHemisphereView {

    static final int MAX_SIZE = 2048;
    private static final int MIN_SIZE = 32;

    private final Steadicam owner;
    private final AEChip chip;
    private ImageDisplay display;
    private JFrame frame;
    private int hemiW = 256;
    private int hemiH = 256;
    /** Azimuth coverage from the Horizontal FOV property. */
    private float azSpanRad = (float) Math.toRadians(120);
    /** Elevation coverage ({@code azSpan * hemiH / hemiW}). */
    private float elSpanRad = (float) Math.PI;
    private float lensFocalLengthMm = 8.5f;
    private float pixelWidthUm = 10f;
    private float pixelHeightUm = 10f;
    private int corx, cory;
    private float cosPan = 1, sinPan = 0, cosTilt = 1, sinTilt = 0, cosRoll = 1, sinRoll = 0;
    private int lastFadeTimestampUs;
    private boolean fadeTimeInit;
    private Steadicam.HemisphereColorMode colorMode = Steadicam.HemisphereColorMode.Gray;
    private int colorScale = 2;
    private float colorStep = 0.5f;
    private float background = 0.5f;

    SteadicamHemisphereView(Steadicam owner, AEChip chip) {
        this.owner = owner;
        this.chip = chip;
    }

    /**
     * Recompute pixmap from the chip size/pitch, lens, and horizontal FOV.
     * Height follows {@code sizeY/sizeX}.
     */
    synchronized void resizeFromOptics(float lensFocalLengthMm, float horizontalFovDeg) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        if (chip != null) {
            pixelWidthUm = chip.getPixelWidthUm();
            pixelHeightUm = chip.getPixelHeightUm();
        }
        int sx = chip != null ? chip.getSizeX() : 0;
        int sy = chip != null ? chip.getSizeY() : 0;
        if (sx < 1) {
            sx = 256;
        }
        if (sy < 1) {
            sy = 256;
        }
        if (horizontalFovDeg < 1f || Float.isNaN(horizontalFovDeg) || Float.isInfinite(horizontalFovDeg)) {
            horizontalFovDeg = 120f;
        }
        if (horizontalFovDeg > 360f) {
            horizontalFovDeg = 360f;
        }
        azSpanRad = (float) Math.toRadians(horizontalFovDeg);
        int w = 256;
        if (lensFocalLengthMm > 0 && pixelWidthUm > 0) {
            float radPerPixel = (float) Math.atan((pixelWidthUm * 1e-3f) / lensFocalLengthMm);
            if (radPerPixel > 1e-6f && !Float.isNaN(radPerPixel) && !Float.isInfinite(radPerPixel)) {
                w = Math.round(azSpanRad / radPerPixel);
            }
        }
        int h = Math.round(w * (float) sy / (float) sx);
        if (w < MIN_SIZE) {
            float s = MIN_SIZE / (float) w;
            w = MIN_SIZE;
            h = Math.max(1, Math.round(h * s));
        }
        if (h < 1) {
            h = 1;
        }
        if (w > MAX_SIZE || h > MAX_SIZE) {
            float s = MAX_SIZE / (float) Math.max(w, h);
            w = Math.max(1, Math.round(w * s));
            h = Math.max(1, Math.round(h * s));
        }
        boolean same = display != null && w == hemiW && h == hemiH;
        hemiW = w;
        hemiH = h;
        elSpanRad = azSpanRad * hemiH / (float) hemiW;
        if (same) {
            return;
        }
        ensureDisplay();
        display.setImageSize(hemiW, hemiH);
        display.resetFrame(background);
        log.info(String.format(
                "Steadicam hemisphere map %dx%d px (chip %dx%d, %.0f° x %.0f°)",
                hemiW, hemiH, sx, sy,
                Math.toDegrees(azSpanRad), Math.toDegrees(elSpanRad)));
        SwingUtilities.invokeLater(this::ensureFrame);
    }

    synchronized void setColorMode(Steadicam.HemisphereColorMode colorMode) {
        this.colorMode = colorMode == null ? Steadicam.HemisphereColorMode.Gray : colorMode;
        background = this.colorMode == Steadicam.HemisphereColorMode.Gray ? 0.5f : 0f;
        updateColorStep();
        if (display != null) {
            display.setGrayValue(background);
            display.resetFrame(background);
            display.repaint();
        }
    }

    synchronized void setColorScale(int colorScale) {
        this.colorScale = colorScale < 1 ? 1 : colorScale;
        updateColorStep();
    }

    private void updateColorStep() {
        colorStep = 1f / this.colorScale;
    }

    synchronized void setCenterOfRotation(int corx, int cory) {
        this.corx = corx;
        this.cory = cory;
    }

    /** DC pose in degrees (camera viewpoint: pan right, tilt up, roll CW). */
    synchronized void setPoseDeg(float panDeg, float tiltDeg, float rollDeg) {
        float panRad = panDeg * ((float) Math.PI / 180);
        float tiltRad = tiltDeg * ((float) Math.PI / 180);
        float rollRad = rollDeg * ((float) Math.PI / 180);
        cosPan = (float) Math.cos(panRad);
        sinPan = (float) Math.sin(panRad);
        cosTilt = (float) Math.cos(tiltRad);
        sinTilt = (float) Math.sin(tiltRad);
        cosRoll = (float) Math.cos(rollRad);
        sinRoll = (float) Math.sin(rollRad);
    }

    synchronized void fade(int timestampUs, float tauMs) {
        if (display == null || tauMs <= 0) {
            return;
        }
        if (!fadeTimeInit) {
            lastFadeTimestampUs = timestampUs;
            fadeTimeInit = true;
            return;
        }
        int dtUs = timestampUs - lastFadeTimestampUs;
        lastFadeTimestampUs = timestampUs;
        if (dtUs <= 0) {
            return;
        }
        if (dtUs > 50_000) {
            dtUs = 50_000;
        }
        float fade = (float) Math.exp(-(dtUs * 1e-3f) / tauMs);
        display.checkPixmapAllocation();
        float[] p = display.getPixmapArray();
        if (p == null) {
            return;
        }
        float g = background;
        for (int i = 0; i < p.length; i++) {
            p[i] = g + (p[i] - g) * fade;
        }
    }

    /**
     * Accumulate at the hemisphere pixel. Gray: ON up / OFF down from mid-gray
     * (ChipRenderer GrayLevel). RedGreen: ON green / OFF red from black.
     */
    synchronized void paint(PolarityEvent e) {
        if (display == null || e == null || lensFocalLengthMm <= 0) {
            return;
        }
        float pitchXmm = pixelWidthUm * 1e-3f;
        float pitchYmm = pixelHeightUm * 1e-3f;
        float xc = (e.x - corx) * pitchXmm / lensFocalLengthMm;
        float yc = (e.y - cory) * pitchYmm / lensFocalLengthMm;
        float zc = 1f;
        float inv = 1f / (float) Math.sqrt(xc * xc + yc * yc + zc * zc);
        xc *= inv;
        yc *= inv;
        zc *= inv;

        float x1 = xc * cosRoll + yc * sinRoll;
        float y1 = -xc * sinRoll + yc * cosRoll;
        float z1 = zc;

        float x2 = x1;
        float y2 = y1 * cosTilt + z1 * sinTilt;
        float z2 = -y1 * sinTilt + z1 * cosTilt;

        float xw = x2 * cosPan + z2 * sinPan;
        float yw = y2;
        float zw = -x2 * sinPan + z2 * cosPan;

        float az = (float) Math.atan2(xw, zw);
        float el = (float) Math.atan2(yw, (float) Math.hypot(xw, zw));
        float halfAz = azSpanRad * 0.5f;
        float halfEl = elSpanRad * 0.5f;
        if (az < -halfAz || az > halfAz || el < -halfEl || el > halfEl) {
            return;
        }
        int u = Math.round((az + halfAz) / azSpanRad * (hemiW - 1));
        int v = Math.round((el + halfEl) / elSpanRad * (hemiH - 1));
        if (u < 0 || u >= hemiW || v < 0 || v >= hemiH) {
            return;
        }
        display.checkPixmapAllocation();
        float[] p = display.getPixmapArray();
        if (p == null) {
            return;
        }
        int i = 3 * (u + v * hemiW);
        boolean on = e.polarity == PolarityEvent.Polarity.On;
        if (colorMode == Steadicam.HemisphereColorMode.Gray) {
            float a = p[i] + (on ? colorStep : -colorStep);
            if (a < 0f) {
                a = 0f;
            } else if (a > 1f) {
                a = 1f;
            }
            p[i] = a;
            p[i + 1] = a;
            p[i + 2] = a;
        } else {
            int ch = on ? 1 : 0;
            float a = p[i + ch] + colorStep;
            p[i + ch] = a > 1f ? 1f : a;
        }
    }

    void displayRepaint() {
        if (display != null) {
            display.repaint();
        }
    }

    synchronized void reset() {
        fadeTimeInit = false;
        if (display != null) {
            display.resetFrame(background);
            display.repaint();
        }
    }

    void updateVisibility(boolean show) {
        SwingUtilities.invokeLater(() -> {
            ensureFrame();
            if (frame.isVisible() != show) {
                frame.setVisible(show);
            }
        });
    }

    private synchronized void ensureDisplay() {
        if (display != null) {
            return;
        }
        display = ImageDisplay.createOpenGLCanvas();
        display.setImageSize(hemiW, hemiH);
        display.setGrayValue(background);
        display.resetFrame(background);
        display.setxLabel("azimuth (right)");
        display.setyLabel("elevation (up)");
    }

    private Dimension windowSizeForMap() {
        float ar = hemiH > 0 ? (float) hemiW / hemiH : 1f;
        int maxDim = Math.min(Math.max(Math.max(hemiW, hemiH), 320), 720);
        int winW;
        int winH;
        if (ar >= 1f) {
            winW = maxDim;
            winH = Math.max(1, Math.round(maxDim / ar));
        } else {
            winH = maxDim;
            winW = Math.max(1, Math.round(maxDim * ar));
        }
        return new Dimension(winW, winH);
    }

    private String frameTitle() {
        return String.format("Steadicam hemisphere (%.0f° × %.0f°)",
                Math.toDegrees(azSpanRad), Math.toDegrees(elSpanRad));
    }

    private synchronized void ensureFrame() {
        ensureDisplay();
        Dimension pref = windowSizeForMap();
        display.setPreferredSize(pref);
        if (frame != null) {
            frame.setTitle(frameTitle());
            return;
        }
        frame = new JFrame(frameTitle());
        frame.getContentPane().add(display, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                owner.setHemisphereViewEnabled(false);
            }
        });
    }
}
