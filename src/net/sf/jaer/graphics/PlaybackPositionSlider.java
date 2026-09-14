package net.sf.jaer.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * AEPlayer position slider. Indexed AEDAT-4 maps the thumb by recording time.
 * Log-relative event rate is painted in the slider (no extra layout height —
 * growing the bottom bar reshapes the GL canvas and tears the image).
 * IN/OUT/other marks are drawn on top of that overlay because JSlider labels
 * sit under the sparkline.
 * <p>
 * Always uses {@link PlaybackSliderUI} (not Aqua). Aqua's round thumb is 25×25
 * and {@code BasicSliderUI.paintThumb} then fills that rectangle, so the handle
 * looks like a wide bar. Aqua also tracks drag with its own {@code fIsDragging}
 * flag; replacing {@code TrackListener} as a {@code MouseListener} only (the
 * SO 518471 click-to-seek trick) leaves Aqua's motion listener unarmed, so the
 * thumb cannot be dragged.
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
    private Runnable seekBeginHandler;
    private Runnable seekEndHandler;

    public PlaybackPositionSlider() {
        super();
    }

    /**
     * Keep this slider on {@link PlaybackSliderUI} across L&amp;F changes so
     * macOS Aqua cannot reinstall its wide thumb.
     */
    @Override
    public void updateUI() {
        setUI(new PlaybackSliderUI(this));
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

    /**
     * Invoked from the slider UI on left-button press/release, before the value
     * changes, so play/pause can be captured before {@code doSingleStep}.
     */
    public void setSeekGestureHandler(Runnable begin, Runnable end) {
        this.seekBeginHandler = begin;
        this.seekEndHandler = end;
    }

    void fireSeekBegin() {
        if (seekBeginHandler != null) {
            seekBeginHandler.run();
        }
    }

    void fireSeekEnd() {
        if (seekEndHandler != null) {
            seekEndHandler.run();
        }
    }

    /**
     * Compact BasicSliderUI: narrow thumb, sparkline under it, click/drag
     * anywhere on the control seeks. Package-visible for tests.
     */
    static final class PlaybackSliderUI extends BasicSliderUI {

        static final int THUMB_WIDTH = 12;
        static final int THUMB_HEIGHT = 18;

        PlaybackSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            slider.setOpaque(false);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(THUMB_WIDTH, THUMB_HEIGHT);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            recalculateIfInsetsChanged();
            recalculateIfOrientationChanged();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintTrack(g2);
                paintThumb(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = trackRect.y + trackRect.height / 2;
                g2.setColor(new Color(160, 160, 165));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(trackRect.x, cy, trackRect.x + trackRect.width, cy);
                paintSparkline(g2);
                paintMarks(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = thumbRect.x;
                int y = thumbRect.y;
                int w = thumbRect.width;
                int h = thumbRect.height;
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(x + 1, y + 1, w - 1, h - 1, 6, 6);
                g2.setColor(slider.hasFocus() ? new Color(235, 242, 255) : new Color(248, 248, 250));
                g2.fillRoundRect(x, y, w - 1, h - 1, 6, 6);
                g2.setColor(new Color(40, 90, 180));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public void paintFocus(Graphics g) {
            // Focus ring is the thumb border.
        }

        @Override
        protected TrackListener createTrackListener(JSlider s) {
            return new TrackListener() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || !slider.isEnabled()) {
                        return;
                    }
                    if (slider instanceof PlaybackPositionSlider p) {
                        p.fireSeekBegin();
                    }
                    calculateGeometry();
                    slider.setValueIsAdjusting(true);
                    slider.setValue(valueForXPosition(e.getX()));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!slider.isEnabled()) {
                        return;
                    }
                    slider.setValue(valueForXPosition(e.getX()));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (slider.isEnabled()) {
                        slider.setValueIsAdjusting(false);
                    }
                    super.mouseReleased(e);
                    if (slider instanceof PlaybackPositionSlider p) {
                        p.fireSeekEnd();
                    }
                }

                @Override
                public boolean shouldScroll(int direction) {
                    return false;
                }
            };
        }

        int xForValue(int value) {
            return xPositionForValue(value);
        }

        Rectangle thumbRectSnapshot() {
            calculateGeometry();
            return new Rectangle(thumbRect);
        }

        boolean motionListenerIsTrackListener() {
            for (MouseMotionListener l : slider.getMouseMotionListeners()) {
                if (l instanceof TrackListener) {
                    return true;
                }
            }
            return false;
        }

        private PlaybackPositionSlider playback() {
            return (PlaybackPositionSlider) slider;
        }

        private void paintSparkline(Graphics2D g2) {
            float[] rates = playback().logRelativeRates;
            if (rates == null || rates.length == 0) {
                return;
            }
            int x0 = xPositionForValue(slider.getMinimum());
            int x1 = xPositionForValue(slider.getMaximum());
            int w = Math.max(1, x1 - x0);
            int yBase = slider.getHeight() - slider.getInsets().bottom - 3;
            int maxH = Math.max(6, slider.getHeight() - slider.getInsets().top - slider.getInsets().bottom - 6);
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
            int yTop = slider.getInsets().top + 1;
            int yBase = slider.getHeight() - slider.getInsets().bottom - 1;
            PlaybackPositionSlider p = playback();
            g2.setFont(slider.getFont().deriveFont(Font.BOLD, 11f));
            g2.setStroke(new BasicStroke(2.5f));
            for (Integer v : p.otherMarkValues) {
                paintMarkLine(g2, v, OTHER_COLOR, yTop + 8, yBase, null);
            }
            paintMarkLine(g2, p.markInValue, IN_COLOR, yTop, yBase, "IN");
            paintMarkLine(g2, p.markOutValue, OUT_COLOR, yTop, yBase, "OUT");
        }

        private void paintMarkLine(Graphics2D g2, Integer value, Color color,
                int yTop, int yBase, String label) {
            if (value == null) {
                return;
            }
            int x = xPositionForValue(value);
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
}
