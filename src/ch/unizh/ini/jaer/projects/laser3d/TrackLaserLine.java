/*
 * findLaserLine.java
 *
 * Created on February 1, 2012
 *
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.awt.Graphics2D;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tracks pulsed laserline
 *
 * @author Thomas
 */
@Description("Returns triggered Laserline")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TrackLaserLine extends EventFilter2D implements FrameAnnotater, Observer {

    /**
     * Options
     */
    protected int windowSize = getPrefs().getInt("TrackLaserLine.windowSize", 3);
    protected int activityThreshold = getPrefs().getInt("TrackLaserLine.activityThreshold", 2);
    protected boolean showDebugPixels = getPrefs().getBoolean("TrackLaserLine.showDebugPixels", false);
    
    /**
     * Variables
     */
//    private FilterChain chain;
    PxlMap pxlMap;

    /**
     * Instantiation
     */
    
    /**
     * Creates a new instance of TrackLaserLine
     */
    public TrackLaserLine(AEChip chip) {
        super(chip);
//        BackgroundActivityFilter DenoiseFilter = new BackgroundActivityFilter(chip);
//        DenoiseFilter.setDt(10000);
//        chain = new FilterChain(chip);
//        setEnclosedFilterChain(chain);
//        getEnclosedFilterChain().add(DenoiseFilter);
        initFilter();
        resetFilter();
        setPropertyTooltip("windowSize", "window size of moving average filter");
        setPropertyTooltip("activityThreshold", "threshold for a laserline pixel");

    }

    public EventPacket filterPacket(EventPacket in) {
        if (!isFilterEnabled()) {
            return in;
        }
        /**
         * Do some denoising Using BackgroundActivityFilter
         */
//        in = chain.filterPacket(in);
        OutputEventIterator outItr = out.outputIterator();
        if (pxlMap == null) {
            initFilter();
        }
        for (Object e : in) {
            BasicEvent ev = (BasicEvent) e;
            pxlMap.processEvent(ev);
//            BasicEvent o = (BasicEvent) outItr.nextOutput();
//            o.copyFrom(ev);
        }
        int pxlActivity;
        for (short x = 0; x < chip.getSizeX(); x++) {
            int colMax = activityThreshold;
            short colMaxY = -1;

            for (short y = 0; y < chip.getSizeY(); y++) {
                pxlActivity = pxlMap.getPxlActivity(x, y);
                if (pxlActivity > colMax) {
                    colMax = pxlActivity;
                    colMaxY = y;
                }
            }
            if (colMaxY >= 0) {
                BasicEvent o = (BasicEvent) outItr.nextOutput();
                o.setX(x);
                o.setY(colMaxY);
                o.setTimestamp(out.getLastTimestamp());
            }
        }
        return out;
    }

    @Override
    public final void resetFilter() {
        if (pxlMap != null) {
            // reset PxlMap
            pxlMap.resetMap();
        }
    }

    @Override
    public void initFilter() {
        if (chip != null & chip.getNumCells() > 0) {
            pxlMap = new PxlMap(chip.getSizeX(), chip.getSizeY(), this);
            pxlMap.initMap();
        }
    }

    public Object getFilterState() {
        return null;
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() | pxlMap == null | showDebugPixels == false) {
        } else {
            GL gl = drawable.getGL();
            gl.glPointSize(4f);
            gl.glBegin(GL.GL_POINTS);
            {
                int pxlActivity;
                for (short x = 0; x < chip.getSizeX(); x++) {
                    int colMax = activityThreshold;
                    short colMaxY = -1;

                    for (short y = 0; y < chip.getSizeY(); y++) {
                        pxlActivity = pxlMap.getPxlActivity(x, y);
                        if (pxlActivity > colMax) {
                            colMax = pxlActivity;
                            colMaxY = y;
                        }
                        if (pxlActivity > 0) {
                            gl.glColor3d(0.0, 0.0, (pxlActivity / getMaxActivityThreshold()));
                            gl.glVertex2f(x, y);
                        }
                    }
                    if (colMaxY >= 0) {
                        gl.glColor3f(1, 0, 0);
                        gl.glVertex2f(x, colMaxY);
                    }
                }
            }
            gl.glEnd();
        }
    }

    /**
     * Functions for options
     */
    /**
     * gets windowSize
     *
     * @return size of the moving average window
     */
    public int getWindowSize() {
        return this.windowSize;
    }

    /**
     * sets windowSize
     *
     * @see #getWindowSize
     * @param windowSize size of moving average window
     */
    public void setWindowSize(final int windowSize) {
        getPrefs().putInt("TrackLaserLine.windowSize", windowSize);
        getSupport().firePropertyChange("windowSize", this.windowSize, windowSize);
        this.windowSize = windowSize;
    }

    public int getMinWindowSize() {
        return 0;
    }

    public int getMaxWindowSize() {
        return 20;
    }

    /**
     * gets activityThreshold
     *
     * @return minimum events per pixel to be on laserline
     */
    public int getActivityThreshold() {
        return this.activityThreshold;
    }

    /**
     * sets activityThreshold
     *
     * @see #getActivityThreshold
     * @param activityThreshold minimum events per pixel to be on laserline
     */
    public void setActivityThreshold(final int activityThreshold) {
        getPrefs().putInt("TrackLaserLine.activityThreshold", activityThreshold);
        getSupport().firePropertyChange("activityThreshold", this.activityThreshold, activityThreshold);
        this.activityThreshold = activityThreshold;
    }

    public int getMinActivityThreshold() {
        return 0;
    }

    public int getMaxActivityThreshold() {
        return 20;
    }

    @Override
    public void update(Observable o, Object arg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
