package net.sf.jaer.graphics;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * AEPlayer position slider. Indexed AEDAT-4 maps the thumb by recording time.
 * Log-relative event rate is painted in the slider (no extra layout height —
 * growing the bottom bar reshapes the GL canvas and tears the image).
 */
public class PlaybackPositionSlider extends JSlider {

    private static final Color FILL = new Color(30, 110, 200, 140);
    private static final Color LINE = new Color(15, 70, 150, 200);

    private float[] logRelativeRates;

    public PlaybackPositionSlider() {
        super();
    }

    public void setLogRelativeRates(float[] bins) {
        this.logRelativeRates = bins;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        float[] rates = logRelativeRates;
        if (rates == null || rates.length == 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Insets in = getInsets();
            int x0 = in.left + 6;
            int w = Math.max(1, getWidth() - in.left - in.right - 12);
            int yBase = getHeight() - in.bottom - 3;
            int maxH = Math.max(6, getHeight() - in.top - in.bottom - 6);
            int n = rates.length;
            Path2D.Float path = new Path2D.Float();
            path.moveTo(x0, yBase);
            for (int x = 0; x <= w; x++) {
                int i = (int) ((long) x * n / w);
                if (i >= n) {
                    i = n - 1;
                }
                float h = Math.min(1f, Math.max(0f, rates[i])) * maxH;
                path.lineTo(x0 + x, yBase - h);
            }
            path.lineTo(x0 + w, yBase);
            path.closePath();
            g2.setColor(FILL);
            g2.fill(path);
            g2.setColor(LINE);
            g2.draw(path);
        } finally {
            g2.dispose();
        }
        if (getUI() instanceof BasicSliderUI basic) {
            basic.paintThumb(g);
        }
    }
}
