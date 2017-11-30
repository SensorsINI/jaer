/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Generates data driven regions of interest DVS frames from DVS activity. These
 * are used to drive further processing, e.g. by CNN engine.
 *
 * @author Tobi Delbruck
 */
public class DvsDataDrivenROIGenerator extends EventFilter2D implements FrameAnnotater {

    /**
     * The ROIs. Indices are [scale][xidx][yidx]
     */
    protected ROI[][][] rois = null;
    private int numScales = getInt("numScales", 3);
    private int dimension = getInt("dimension", 64);
    private int stride = getInt("stride", dimension / 2);
    private int grayScale = getInt("grayScale", 100);
    private int dvsEventCount = getInt("dvsEventCount", 1000);
    private int sx, sy, nx, ny;
    private boolean showDvsFrames = false;
    private ImageDisplay dvsFrameImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private JFrame dvsFrame = null;
//    private ImageDisplay.Legend sliceBitmapLegend;
    /**
     * PropertyChangeEvent that is fired when a new ROI is available
     */
    public static final String EVENT_NEW_ROI_AVAILABLE = "NEW_ROI_AVAILABLE";

    // timers and flags for showing filter properties temporarily
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;
    private volatile boolean showROIsTemporarilyFlag = false;

    public DvsDataDrivenROIGenerator(AEChip chip) {
        super(chip);
        setPropertyTooltip("numScales", "number of scales of ROIs; 1 means only basic ROIs without subsampling");
        setPropertyTooltip("dimension", "width and height of each ROI in pixels. Corresponds to input pixels at scale 0, the finest scale");
        setPropertyTooltip("stride", "stride of adjacent ROIs in pixels; automatically set to half of dimension each time that is set unless overridden");
        setPropertyTooltip("dvsEventCount", "num DVS events accumulated to subsampled ROI to fill the frame");
        setPropertyTooltip("showDvsFrames", "shows the fully exposed (accumulated with events) frames in a separate window");
        setPropertyTooltip("grayScale", "sets the full scale value for the DVS frame rendering");
    }

    /**
     * Note: Not meant to be used by subclasses unless time limit is not
     * important
     *
     * @param in the input packet
     * @return input packet
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        for (BasicEvent be : in) {
            PolarityEvent e = (PolarityEvent) be;
            addEvent(e);
        }
        return in;
    }

    /**
     * Use this method to add events in an iterator. Fires the
     * PropertyChangeEvent EVENT_NEW_ROI_AVAILABLE with the ROI as the new
     * property value for each scale that fills up.
     *
     * @param e the event to add
     */
    public void addEvent(PolarityEvent e) {
        for (int s = 0; s < numScales; s++) {
            // For this scale, find the corresonding ROI and put the event to it
            // The ROIs overlap by the stride, scaled by the scale, so at one scale, // TODO stride not implemented yet
            // an event can belong
            // to 2 or even 4 ROIs depending on where it is.
            int subx = e.x >> s, suby = e.y >> s; // TODO scale not implemented and adding to multiple overlapping not done yet
            int rx = subx / dimension;
            int ry = suby / dimension;
            if (rx >= nx >> s || ry >= ny >> s) {
                continue;
            }
            final int locx = subx - rx * dimension;
            final int locy = suby - ry * dimension;
            ROI roi = rois[s][rx][ry];
            roi.addEvent(locx, locy, e.polarity);
            if (roi.getAccumulatedEventCount() > dvsEventCount) {
                getSupport().firePropertyChange(EVENT_NEW_ROI_AVAILABLE, null, roi);
                if (showDvsFrames) {
                    drawDvsFrame(roi);
                    roi.clear();  // TODO showing the ROIs will also clear them after they are shown
                }
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
        if (rois != null) {
            for (ROI[][] a : rois) {
                for (ROI[] b : a) {
                    for (ROI c : b) {
                        c.clear();
                        c.setColorScale(grayScale);
                    }
                }
            }
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            checkRois();
        } else {
            rois = null;
        }
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showROIsTemporarilyFlag) {
            int d = dimension;
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINES);
            for (int x = 0; x <= chip.getSizeX(); x += d) {
                gl.glVertex2f(x, 0);
                gl.glVertex2f(x, chip.getSizeY());
            }
            for (int y = 0; y <= chip.getSizeY(); y += d) {
                gl.glVertex2f(0, y);
                gl.glVertex2f(chip.getSizeX(), y);
            }
            gl.glEnd();
        }
    }

    private void showRoisTemporarily() {
        if (stopShowingStuffTask != null) {
            stopShowingStuffTask.cancel();
        }
        stopShowingStuffTask = new TimerTask() {
            @Override
            public void run() {
                showROIsTemporarilyFlag = false;
            }
        };
        Timer showAreaCountsAreasTimer = new Timer();
        showROIsTemporarilyFlag = true;
        showAreaCountsAreasTimer.schedule(stopShowingStuffTask, SHOW_STUFF_DURATION_MS);
    }

    /**
     * @return the numScales
     */
    public int getNumScales() {
        return numScales;
    }

    /**
     * @param numScales the numScales to set
     */
    synchronized public void setNumScales(int numScales) {
        this.numScales = numScales;
        putInt("numScales", numScales);
        checkRois();
        showRoisTemporarily();
    }

    /**
     * @return the dimension
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * @param dimension the dimension to set
     */
    synchronized public void setDimension(int dimension) {
        this.dimension = dimension;
        putInt("dimension", dimension);
        setStride(dimension / 2);
        showRoisTemporarily();
    }

    /**
     * @return the stride
     */
    public int getStride() {
        return stride;
    }

    /**
     * @param stride the stride to set
     */
    synchronized public void setStride(int stride) {
        int old = this.stride;
        this.stride = stride;
        putInt("stride", stride);
        getSupport().firePropertyChange("stride", old, stride); // update GUI
        checkRois();
        showRoisTemporarily();
    }

    synchronized private void checkRois() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        nx = sx / dimension;
        ny = sy / dimension;
        rois = new ROI[numScales][nx][ny];
        for (int s = 0; s < numScales; s++) {
            for (int x = 0; x < nx >> s; x++) {
                for (int y = 0; y < ny >> s; y++) {
                    int xll = (x * dimension) << s, yll = (y * dimension) << s;
                    rois[s][x][y] = new ROI(xll, yll, s, dimension, dimension, grayScale);
                }
            }
        }
    }

    synchronized private void drawDvsFrame(DvsSubsamplerToFrame roi) {
        if (dvsFrame == null) {
            String windowName = "DVS frame";
            dvsFrame = new JFrame(windowName);
            dvsFrame.setLayout(new BoxLayout(dvsFrame.getContentPane(), BoxLayout.Y_AXIS));
            dvsFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            dvsFrameImageDisplay = ImageDisplay.createOpenGLCanvas();
            dvsFrameImageDisplay.setBorderSpacePixels(10);
            dvsFrameImageDisplay.setImageSize(dimension, dimension);
            dvsFrameImageDisplay.setSize(200, 200);
            dvsFrameImageDisplay.setGrayValue(0);
//            sliceBitmapLegend = sliceBitmapImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
            panel.add(dvsFrameImageDisplay);

            dvsFrame.getContentPane().add(panel);
            dvsFrame.pack();
            dvsFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowDvsFrames(false);
                }
            });
        }
        if (!dvsFrame.isVisible()) {
            dvsFrame.setVisible(true);
        }
        float scale = 1f / roi.getColorScale();
        if (dimension != dvsFrameImageDisplay.getSizeX()) {
            dvsFrameImageDisplay.setImageSize(dimension, dimension);
            dvsFrameImageDisplay.clearLegends();
//                    sliceBitmapLegend = dvsFrameImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
        }

//        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));

        /* Reset the image first */
        dvsFrameImageDisplay.clearImage();
        /* Rendering the reference patch in t-imuWarningDialog slice, it's on the center with color red */
        for (int x = 0; x < dimension; x++) {
            for (int y = 0; y < dimension; y++) {
                float f = roi.getValueAtPixel(x, y);
                float[] rgb = {f, f, f};
                dvsFrameImageDisplay.setPixmapRGB(x, y, rgb);
            }
        }

//        if (sliceBitmapLegend != null) {
//            sliceBitmapLegend.s = G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH + "\nScale: " + subSampleBy;
//        }
        dvsFrameImageDisplay.repaint();
    }

    /**
     * @return the showDvsFrames
     */
    public boolean isShowDvsFrames() {
        return showDvsFrames;
    }

    /**
     * @param showDvsFrames the showDvsFrames to set
     */
    public void setShowDvsFrames(boolean showDvsFrames) {
        this.showDvsFrames = showDvsFrames;
    }

    /**
     * @return the dvsEventCount
     */
    public int getDvsEventCount() {
        return dvsEventCount;
    }

    /**
     * @param dvsEventCount the dvsEventCount to set
     */
    public void setDvsEventCount(int dvsEventCount) {
        this.dvsEventCount = dvsEventCount;
        putInt("dvsEventCount", dvsEventCount);
    }

    /**
     * @return the grayScale
     */
    public int getGrayScale() {
        return grayScale;
    }

    /**
     * @param grayScale the grayScale to set
     */
    public void setGrayScale(int grayScale) {
        this.grayScale = grayScale;
        putInt("grayScale", grayScale);
        resetFilter();
    }

    /**
     * Returns number of ROIs in x direction for scale 0
     *
     * @return the nx
     */
    public int getNx() {
        return nx;
    }

    /**
     * Returns number of ROIs in y direction for scale 0
     *
     * @return the ny
     */
    public int getNy() {
        return ny;
    }

    /**
     * One region of interest (ROI)
     */
    public class ROI extends DvsSubsamplerToFrame {

        /**
         * @return the xLowerLeft
         */
        public int getxLowerLeft() {
            return xLowerLeft;
        }

        /**
         * @return the yLowerLeft
         */
        public int getyLowerLeft() {
            return yLowerLeft;
        }

        /**
         * @return the scale
         */
        public int getScale() {
            return scale;
        }

        /**
         * @return the xidx
         */
        public int getXidx() {
            return xidx;
        }

        /**
         * @return the yidx
         */
        public int getYidx() {
            return yidx;
        }
        private int xLowerLeft;
        private int yLowerLeft;
        private int scale;
        private int xidx;
        private int yidx;

        public ROI(int xLowerLeft, int yLowerLeft, int scale, int dimX, int dimY, int colorScale) {
            super(dimX, dimY, colorScale);
            this.xLowerLeft = xLowerLeft;
            this.yLowerLeft = yLowerLeft;
            this.scale = scale;
        }

    }

}
