/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import com.sun.opengl.util.GLUT;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.*;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.swing.JMenu;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Collects and displays statistics for a selected range of pixels / cells.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class CellStatsProber extends EventFilter2D implements FrameAnnotater, MouseListener, MouseMotionListener {

    public static String getDescription() {
        return "Collects and displays statistics for a selected range of pixels / cells";
    }

    public static DevelopmentStatus getDevelopementStatus() {
        return DevelopmentStatus.Beta;
    }
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private DisplayMethod displayMethod;
    private ChipRendererDisplayMethod chipRendererDisplayMethod;
    Rectangle selection = null;
    private static float[] selectionColor = {0, 0, 1};
    private static float lineWidth = 1f;
//    private JMenu popupMenu;
    int startx, starty, endx, endy;
    Point startPoint = null, endPoint = null, clickedPoint = null;
//    private GLUT glut = new GLUT();
    private Stats stats = new Stats();
    private boolean rateEnabled = getPrefs().getBoolean("CellStatsProber.rateEnabled", true);
    private boolean isiHistEnabled = getPrefs().getBoolean("CellStatsProber.isiHistEnabled", true);
    private boolean spikeSoundEnabled = getPrefs().getBoolean("CellStatsProber.spikeSoundEnabled", true);
    SpikeSound spikeSound = null;
    private TextRenderer renderer = null;

    public CellStatsProber(AEChip chip) {
        super(chip);
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
            renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10),true, true);
        }
        final String h = "ISIs", e = "Event rate";
        setPropertyTooltip(h, "isiHistEnabled", "enable histogramming interspike intervals");
        setPropertyTooltip(h, "isiMinUs", "min ISI in us");
        setPropertyTooltip(h, "isiMaxUs", "max ISI in us");
        setPropertyTooltip(h, "isiAutoScalingEnabled", "autoscale bounds for ISI histogram");
        setPropertyTooltip(h,"isiNumBins","number of bins in the ISI");
        setPropertyTooltip(e,"rateEnabled","measure event rate");
        setPropertyTooltip("spikeSoundEnabled","enable playing spike sound whenever the selected region has events");
    }

    public void displayStats(GLAutoDrawable drawable) {
        if (drawable == null || selection == null || chip.getCanvas() == null) {
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        Rectangle chipRect = new Rectangle(sx, sy);
        GL gl = drawable.getGL();
        if (!chipRect.intersects(selection)) {
            return;
        }
        drawSelection(gl, selection, selectionColor);
        stats.drawStats(drawable);
        stats.play();

    }

    private void getSelection(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        endPoint = p;
        startx = min(startPoint.x, endPoint.x);
        starty = min(startPoint.y, endPoint.y);
        endx = max(startPoint.x, endPoint.x);
        endy = max(startPoint.y, endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        selection = new Rectangle(startx, starty, w, h);
    }

    private boolean inSelection(BasicEvent e) {
        if (selection.contains(e.x, e.y)) {
            return true;
        }
        return false;
    }

    public void showContextMenu() {
    }

    private void drawSelection(GL gl, Rectangle r, float[] c) {
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(lineWidth);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(selection.x, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y);
        gl.glVertex2f(selection.x + selection.width, selection.y + selection.height);
        gl.glVertex2f(selection.x, selection.y + selection.height);
        gl.glEnd();

        gl.glPopMatrix();
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        stats.collectStats(in);
        return in;
    }

    @Override
    synchronized public void resetFilter() {
//        selection = null;
        stats.resetIsiHist();
    }

    @Override
    public void initFilter() {
    }

    public void annotate(GLAutoDrawable drawable) {
        if (canvas.getDisplayMethod() instanceof ChipRendererDisplayMethod) {
            chipRendererDisplayMethod = (ChipRendererDisplayMethod) canvas.getDisplayMethod();
            displayStats(drawable);
        }
    }

    /**
     * @return the rateEnabled
     */
    public boolean isRateEnabled() {
        return rateEnabled;
    }

    /**
     * @param rateEnabled the rateEnabled to set
     */
    public void setRateEnabled(boolean rateEnabled) {
        this.rateEnabled = rateEnabled;
        prefs().putBoolean("CellStatsProber.rateEnabled", rateEnabled);
    }

    /**
     * @return the isiHistEnabled
     */
    public boolean isIsiHistEnabled() {
        return isiHistEnabled;
    }

    /**
     * @param isiHistEnabled the isiHistEnabled to set
     */
    public void setIsiHistEnabled(boolean isiHistEnabled) {
        this.isiHistEnabled = isiHistEnabled;
        prefs().putBoolean("CellStatsProber.isiHistEnabled", isiHistEnabled);
    }

    /**
     * @return the spikeSoundEnabled
     */
    public boolean isSpikeSoundEnabled() {
        return spikeSoundEnabled;
    }

    /**
     * @param spikeSoundEnabled the spikeSoundEnabled to set
     */
    public void setSpikeSoundEnabled(boolean spikeSoundEnabled) {
        this.spikeSoundEnabled = spikeSoundEnabled;
        prefs().putBoolean("CellStatsProber.spikeSoundEnabled", spikeSoundEnabled);
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
        if (startPoint == null) {
            return;
        }
        getSelection(e);
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    public void mousePressed(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        startPoint = p;
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
        if (startPoint == null) {
            return;
        }
        getSelection(e);
    }

    public void mouseClicked(MouseEvent e) {
        Point p = canvas.getPixelFromMouseEvent(e);
        clickedPoint = p;
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (glCanvas == null) {
            return;
        }
        if (yes) {
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    public void setIsiNumBins(int isiNumBins) {
        stats.setIsiNumBins(isiNumBins);
    }

    public void setIsiMinUs(int isiMinUs) {
        stats.setIsiMinUs(isiMinUs);
    }

    public void setIsiMaxUs(int isiMaxUs) {
        stats.setIsiMaxUs(isiMaxUs);
    }

    public void setIsiAutoScalingEnabled(boolean isiAutoScalingEnabled) {
        stats.setIsiAutoScalingEnabled(isiAutoScalingEnabled);
    }

    public boolean isIsiAutoScalingEnabled() {
        return stats.isIsiAutoScalingEnabled();
    }

    public int getIsiNumBins() {
        return stats.getIsiNumBins();
    }

    public int getIsiMinUs() {
        return stats.getIsiMinUs();
    }

    public int getIsiMaxUs() {
        return stats.getIsiMaxUs();
    }

    private class Stats {

        private int isiMinUs = prefs().getInt("CellStatsProber.isiMinUs", 0),
                isiMaxUs = prefs().getInt("CellStatsProber.isiMaxUs", 100000),
                isiNumBins = prefs().getInt("CellStatsProber.isiNumBins", 100);
        private int[] isiBins = new int[isiNumBins];
        private int isiLessCount = 0, isiMoreCount = 0;
        private int isiMaxCount = 0;
        private boolean isiAutoScalingEnabled = prefs().getBoolean("CellStatsProber.isiAutoScalingEnabled", true);
        private LowpassFilter rateFilter = new LowpassFilter();
        private int lastT = 0, prevLastT = 0;
        boolean initialized = false;
        float instantaneousRate = 0, filteredRate = 0;
        int count = 0;

        public String toString() {
            return String.format("n=%d, keps=%.1f", count, filteredRate);
        }

        synchronized public void collectStats(EventPacket in) {
            if (selection == null) {
                return;
            }
            stats.count = 0;
            for (Object o : in) {
                BasicEvent e = (BasicEvent) o;
                if (inSelection(e)) {
                    stats.count++;

                    if (isiHistEnabled) {
                        int isi = e.timestamp - lastT;
                        if (isi < 0) {
                            lastT = e.timestamp; // handle wrapping
                            continue;
                        }
                        if (isiAutoScalingEnabled) {
                            if (isi > isiMaxUs) {
                                setIsiMaxUs(isi);
                            } else if (isi < isiMinUs) {
                                setIsiMinUs(isi);
                            }
                        }
                        int bin = getIsiBin(isi);
                        if (bin < 0) {
                            isiLessCount++;
                        } else if (bin >= isiNumBins) {
                            isiMoreCount++;
                        } else {
                            int v = ++isiBins[bin];
                            if (v > isiMaxCount) {
                                isiMaxCount = v;
                            }
                        }
                    }
                    lastT = e.timestamp;
                }
            }
            if (stats.count > 0) {
                measureRate(lastT, stats.count);
            }
        }

        private void measureRate(int lastT, int n) {
            if (!rateEnabled) {
                return;
            }
            final float maxRate = 10e6f;
            if (!initialized) {
                prevLastT = lastT;
                initialized = true;
            }
            int dt = lastT - prevLastT;
            prevLastT = lastT;
            if (dt < 0) {
                initialized = false;
            }
            if (dt == 0) {
                instantaneousRate = maxRate; // if the time interval is zero, use the max rate
            } else {
                instantaneousRate = 1e6f * (float) n / (dt * AEConstants.TICK_DEFAULT_US);
            }
            filteredRate = rateFilter.filter(instantaneousRate, lastT);
        }

        synchronized private void drawStats(GLAutoDrawable drawable) {
            GL gl = drawable.getGL();

            renderer.begin3DRendering();
//            renderer.beginRendering(drawable.getWidth(), drawable.getHeight());
            // optionally set the color
            renderer.setColor(0, 0, 1, 0.8f);
            renderer.draw3D(this.toString(), selection.x, selection.y - 1,0,.5f);
            // ... more draw commands, color changes, etc.
            renderer.end3DRendering();

            // draw hist
            if (isiHistEnabled) {
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(1);
                gl.glBegin(GL.GL_LINE_LOOP);
                if (isiMaxCount == 0) {
                    gl.glVertex2f(0, 0);
                    gl.glVertex2f(chip.getSizeX(), 0);
                } else {
                    float dx = (float) (chip.getSizeX() - 2) / isiNumBins;
                    float sy = (float) (chip.getSizeY() - 2) / isiMaxCount;
                    for (int i = 0; i < isiBins.length; i++) {
                        float y = 1 + sy * isiBins[i];
                        float x1 = 1 + dx * i, x2 = x1 + dx;
                        gl.glVertex2f(x1, 1);
                        gl.glVertex2f(x1, y);
                        gl.glVertex2f(x2, y);
                        gl.glVertex2f(x2, 1);
                    }
                }
                gl.glEnd();
                renderer.draw3D(String.format("%d", isiMinUs), -1, -3,0,0.5f);
                renderer.draw3D(String.format("%d",isiMaxUs), chip.getSizeX()-8, -3,0,0.5f);
            }
        }

        private void play() {
            if (!spikeSoundEnabled) {
                return;
            }
            if (spikeSound == null) {
                spikeSound = new SpikeSound();
            }
            if (count > 0) {
                spikeSound.play();
            }
        }

        /**
         * @return the isiMinUs
         */
        public int getIsiMinUs() {
            return isiMinUs;
        }

        /**
         * @param isiMinUs the isiMinUs to set
         */
        synchronized public void setIsiMinUs(int isiMinUs) {
            if (this.isiMinUs == isiMinUs) {
                return;
            }
            int old = this.isiMinUs;
            this.isiMinUs = isiMinUs;
            if (isiAutoScalingEnabled) {
                support.firePropertyChange("isiMinUs", old, isiMinUs);
            } else {
                prefs().putInt("CellStatsProber.isiMinUs", isiMinUs);
            }
            resetIsiHist();
        }

        /**
         * @return the isiMaxUs
         */
        public int getIsiMaxUs() {
            return isiMaxUs;
        }

        /**
         * @param isiMaxUs the isiMaxUs to set
         */
        synchronized public void setIsiMaxUs(int isiMaxUs) {
            if (this.isiMaxUs == isiMaxUs) {
                return;
            }
            int old = this.isiMaxUs;
            this.isiMaxUs = isiMaxUs;
            if (isiAutoScalingEnabled) {
                support.firePropertyChange("isiMaxUs", old, isiMaxUs);
            } else {
                prefs().putInt("CellStatsProber.isiMaxUs", isiMaxUs);
            }
            resetIsiHist();
        }

        synchronized private void resetIsiHist() {
            if (isiBins == null || isiBins.length != isiNumBins) {
                isiBins = new int[isiNumBins];
            } else {
                Arrays.fill(isiBins, 0);
            }
            isiLessCount = 0;
            isiMoreCount = 0;
            isiMaxCount = 0;

        }

        private int getIsiBin(int isi) {
            if (isi < isiMinUs) {
                return -1;
            } else if (isi > isiMaxUs) {
                return isiNumBins;
            } else {
                int binSize = (isiMaxUs - isiMinUs) / isiNumBins;
                if (binSize <= 0) {
                    return -1;
                }
                return (isi - isiMinUs) / binSize;
            }
        }

        /**
         * @return the isiNumBins
         */
        public int getIsiNumBins() {
            return isiNumBins;
        }

        /**
         * @param isiNumBins the isiNumBins to set
         */
        synchronized public void setIsiNumBins(int isiNumBins) {
            if (isiNumBins == this.isiNumBins) {
                return;
            }
            int old = this.isiNumBins;
            if (isiNumBins < 1) {
                isiNumBins = 1;
            }
            this.isiNumBins = isiNumBins;
            resetIsiHist();
            prefs().putInt("CellStatsProber.isiNumBins", isiNumBins);
            support.firePropertyChange("isiNumBins", old, isiNumBins);
        }

        /**
         * @return the isiAutoScalingEnabled
         */
        public boolean isIsiAutoScalingEnabled() {
            return isiAutoScalingEnabled;
        }

        /**
         * @param isiAutoScalingEnabled the isiAutoScalingEnabled to set
         */
        synchronized public void setIsiAutoScalingEnabled(boolean isiAutoScalingEnabled) {
            this.isiAutoScalingEnabled = isiAutoScalingEnabled;
            prefs().putBoolean("CellStatsProber.isiAutoScalingEnabled", isiAutoScalingEnabled);
        }
    }
}
