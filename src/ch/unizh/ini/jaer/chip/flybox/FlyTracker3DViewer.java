/*
 * FlyTracker3DViewer.java
 *
 * Orbit view of the FlyTracker3D path. Java2D, so it does not open a second GLCanvas.
 */
package ch.unizh.ini.jaer.chip.flybox;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Shows the 3D path, its points, and the XYZ axes.
 * Drag rotates, the wheel zooms, right-drag pans, double-click resets the view.
 */
final class FlyTracker3DViewer extends JFrame {

    private final FlyTracker3D tracker;
    private final View view = new View();

    FlyTracker3DViewer(FlyTracker3D tracker) {
        super("FlyTracker3D");
        this.tracker = tracker;
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                tracker.viewerClosedByUser();
            }
        });
        setContentPane(view);
        pack();
        setLocationByPlatform(true);
    }

    private final class View extends JPanel {
        private float yaw = (float) Math.toRadians(40);
        private float pitch = (float) Math.toRadians(25);
        private float pixelsPerMm = 4f;
        private float panX;
        private float panY;
        private int dragX;
        private int dragY;
        private boolean rotating = true;

        View() {
            setPreferredSize(new Dimension(720, 520));
            setBackground(Color.BLACK);
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragX = e.getX();
                    dragY = e.getY();
                    rotating = !e.isMetaDown() && !e.isControlDown() && e.getButton() == MouseEvent.BUTTON1;
                    if (e.getClickCount() == 2) {
                        yaw = (float) Math.toRadians(40);
                        pitch = (float) Math.toRadians(25);
                        pixelsPerMm = 4f;
                        panX = 0;
                        panY = 0;
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - dragX;
                    int dy = e.getY() - dragY;
                    dragX = e.getX();
                    dragY = e.getY();
                    if (rotating) {
                        yaw += dx * 0.01f;
                        pitch += dy * 0.01f;
                        float limit = (float) Math.toRadians(89);
                        if (pitch > limit) {
                            pitch = limit;
                        } else if (pitch < -limit) {
                            pitch = -limit;
                        }
                    } else {
                        panX += dx;
                        panY += dy;
                    }
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    float factor = e.getWheelRotation() < 0 ? 1.12f : 1f / 1.12f;
                    pixelsPerMm = Math.max(0.05f, Math.min(400f, pixelsPerMm * factor));
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Basis basis = basis();
            drawAxes(g2, w, h, basis);
            List<float[]> path = tracker.copyPath();
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(255, 220, 40));
            float[] previous = null;
            for (float[] point : path) {
                float[] screen = project(point, w, h, basis);
                if (previous != null) {
                    float[] prevScreen = project(previous, w, h, basis);
                    g2.draw(new Line2D.Float(prevScreen[0], prevScreen[1], screen[0], screen[1]));
                }
                previous = point;
            }
            g2.setColor(Color.WHITE);
            for (float[] point : path) {
                float[] screen = project(point, w, h, basis);
                g2.fill(new Ellipse2D.Float(screen[0] - 2f, screen[1] - 2f, 4f, 4f));
            }
            float[] now = tracker.getCurrentMm();
            String where = now == null
                    ? "no 3D point yet"
                    : String.format("x %+.1f  y %+.1f  z %+.1f mm", now[0], now[1], now[2]);
            g2.setColor(new Color(180, 180, 180));
            g2.drawString(where, 12, h - 28);
            g2.drawString("drag rotate    wheel zoom    right-drag pan    double-click reset", 12, h - 12);
        }

        private void drawAxes(Graphics2D g2, int w, int h, Basis basis) {
            float len = 30f;
            drawAxis(g2, w, h, basis, len, 0, 0, new Color(220, 60, 60), "X");
            drawAxis(g2, w, h, basis, 0, len, 0, new Color(60, 200, 80), "Y");
            drawAxis(g2, w, h, basis, 0, 0, len, new Color(80, 140, 255), "Z");
        }

        private void drawAxis(Graphics2D g2, int w, int h, Basis basis,
                float x, float y, float z, Color color, String label) {
            float[] origin = project(new float[] {0, 0, 0}, w, h, basis);
            float[] tip = project(new float[] {x, y, z}, w, h, basis);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(color);
            g2.draw(new Line2D.Float(origin[0], origin[1], tip[0], tip[1]));
            g2.drawString(label, tip[0] + 4, tip[1] - 4);
        }

        private float[] project(float[] p, int w, int h, Basis basis) {
            float cx = basis.rx * p[0] + basis.ry * p[1] + basis.rz * p[2];
            float cy = basis.ux * p[0] + basis.uy * p[1] + basis.uz * p[2];
            return new float[] {
                w * 0.5f + panX + cx * pixelsPerMm,
                h * 0.5f + panY - cy * pixelsPerMm
            };
        }

        /** Camera right and up. The camera looks toward the origin. */
        private Basis basis() {
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);
            double fx = -cp * cy;
            double fy = -cp * sy;
            double fz = -sp;
            double ux = 0;
            double uy = 0;
            double uz = 1;
            double rx = fy * uz - fz * uy;
            double ry = fz * ux - fx * uz;
            double rz = fx * uy - fy * ux;
            double rn = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rn < 1e-6) {
                rx = 1;
                ry = 0;
                rz = 0;
                rn = 1;
            }
            rx /= rn;
            ry /= rn;
            rz /= rn;
            double upx = ry * fz - rz * fy;
            double upy = rz * fx - rx * fz;
            double upz = rx * fy - ry * fx;
            return new Basis(
                    (float) rx, (float) ry, (float) rz,
                    (float) upx, (float) upy, (float) upz);
        }
    }

    private static final class Basis {
        final float rx, ry, rz;
        final float ux, uy, uz;

        Basis(float rx, float ry, float rz, float ux, float uy, float uz) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.ux = ux;
            this.uy = uy;
            this.uz = uz;
        }
    }
}
