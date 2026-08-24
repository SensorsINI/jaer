/*
 * SteadicamHemisphereView.java
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * World-fixed 180° equirectangular hemisphere painted from DVS events using
 * DC-integrated IMU pan/tilt/roll (not the high-passed Steadicam warp).
 * Size follows lensFOV: {@code hemiSize = π / atan(pitch/f)}, capped at
 * {@link #MAX_SIZE}.
 */
class SteadicamHemisphereView {

    static final int MAX_SIZE = 2048;
    private static final float HALF_PI = (float) (Math.PI / 2);

    private final Steadicam owner;
    private ImageDisplay display;
    private JFrame frame;
    private int hemiSize = 256;
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

    SteadicamHemisphereView(Steadicam owner) {
        this.owner = owner;
    }

    /**
     * Recompute buffer from lensFOV {@code radPerPixel = atan(pitch_mm / f_mm)}.
     */
    synchronized void resizeFromOptics(float radPerPixel, float lensFocalLengthMm,
            float pixelWidthUm, float pixelHeightUm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        this.pixelWidthUm = pixelWidthUm;
        this.pixelHeightUm = pixelHeightUm;
        int size = 256;
        if (radPerPixel > 1e-6f && !Float.isNaN(radPerPixel) && !Float.isInfinite(radPerPixel)) {
            size = Math.round((float) Math.PI / radPerPixel);
        }
        if (size < 32) {
            size = 32;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
        hemiSize = size;
        ensureDisplay();
        display.setImageSize(size, size);
        display.resetFrame(background);
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
        if (az < -HALF_PI || az > HALF_PI || el < -HALF_PI || el > HALF_PI) {
            return;
        }
        int u = Math.round((az + HALF_PI) / (float) Math.PI * (hemiSize - 1));
        int v = Math.round((el + HALF_PI) / (float) Math.PI * (hemiSize - 1));
        if (u < 0 || u >= hemiSize || v < 0 || v >= hemiSize) {
            return;
        }
        display.checkPixmapAllocation();
        float[] p = display.getPixmapArray();
        if (p == null) {
            return;
        }
        int i = 3 * (u + v * hemiSize);
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
        display.setImageSize(hemiSize, hemiSize);
        display.setGrayValue(background);
        display.resetFrame(background);
        display.setxLabel("azimuth (right)");
        display.setyLabel("elevation (up)");
    }

    private synchronized void ensureFrame() {
        ensureDisplay();
        if (frame != null) {
            return;
        }
        int win = Math.min(Math.max(hemiSize, 320), 720);
        frame = new JFrame("Steadicam hemisphere (180°)");
        frame.setPreferredSize(new Dimension(win, win));
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
