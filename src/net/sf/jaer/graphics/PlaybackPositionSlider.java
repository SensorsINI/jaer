package net.sf.jaer.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * AEPlayer position slider. Indexed AEDAT-4 maps the thumb by recording time.
 * Log-relative event rate is painted in the slider (no extra layout height —
 * growing the bottom bar reshapes the GL canvas and tears the image).
 * IN/OUT/other marks are drawn on top of that overlay because JSlider labels
 * sit under the sparkline.
 */
public class PlaybackPositionSlider extends JSlider {

    private static final Color FILL = new Color(30, 110, 200, 140);
    private static final Color LINE = new Color(15, 70, 150, 200);
    private static final Color IN_COLOR = new Color(40, 220, 80);
    private static final Color OUT_COLOR = new Color(255, 90, 30);
    private static final Color OTHER_COLOR = new Color(255, 220, 50);

    private float[] logRelativeRates;
    private Integer markInValue;
    private Integer markOutValue;
    private final List<Integer> otherMarkValues = new ArrayList<>();

    public PlaybackPositionSlider() {
        super();
    }

    public void setLogRelativeRates(float[] bins) {
        this.logRelativeRates = bins;
        repaint();
    }

    /**
     * Slider-scale positions (0–{@link #getMaximum()}) for IN/OUT/other marks.
     * Call again after the time-mapped sparkline is bound so marks stay aligned.
     */
    public void setPlaybackMarks(Integer markIn, Integer markOut, Collection<Integer> others) {
        this.markInValue = markIn;
        this.markOutValue = markOut;
        otherMarkValues.clear();
        if (others != null) {
            for (Integer v : others) {
                if (v != null) {
                    otherMarkValues.add(v);
                }
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintSparkline(g2);
            paintMarks(g2);
        } finally {
            g2.dispose();
        }
        if (getUI() instanceof BasicSliderUI basic) {
            basic.paintThumb(g);
        }
    }

    private void paintSparkline(Graphics2D g2) {
        float[] rates = logRelativeRates;
        if (rates == null || rates.length == 0) {
            return;
        }
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
    }

    private void paintMarks(Graphics2D g2) {
        Insets in = getInsets();
        int yTop = in.top + 1;
        int yBase = getHeight() - in.bottom - 1;
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g2.setStroke(new BasicStroke(2.5f));
        for (Integer v : otherMarkValues) {
            paintMarkLine(g2, v, OTHER_COLOR, yTop + 8, yBase, null);
        }
        paintMarkLine(g2, markInValue, IN_COLOR, yTop, yBase, "IN");
        paintMarkLine(g2, markOutValue, OUT_COLOR, yTop, yBase, "OUT");
    }

    /** Same x mapping as the sparkline (BasicSliderUI.xPositionForValue is protected). */
    private int xForSliderValue(int value) {
        Insets in = getInsets();
        int x0 = in.left + 6;
        int w = Math.max(1, getWidth() - in.left - in.right - 12);
        int span = Math.max(1, getMaximum() - getMinimum());
        float f = (value - getMinimum()) / (float) span;
        if (f < 0) {
            f = 0;
        } else if (f > 1) {
            f = 1;
        }
        return x0 + Math.round(f * w);
    }

    private void paintMarkLine(Graphics2D g2, Integer value, Color color,
            int yTop, int yBase, String label) {
        if (value == null) {
            return;
        }
        int x = xForSliderValue(value);
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawLine(x + 1, yTop, x + 1, yBase);
        g2.setColor(color);
        g2.drawLine(x, yTop, x, yBase);
        if (label != null) {
            int tx = x + 3;
            int ty = yTop + 11;
            g2.setColor(new Color(0, 0, 0, 200));
            g2.drawString(label, tx + 1, ty + 1);
            g2.setColor(color);
            g2.drawString(label, tx, ty);
        }
    }
}
