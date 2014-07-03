/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.label;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import net.sf.jaer.event.OpticalFlowEvent;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter2d;

/**Computes temporally and spatially smoothed optical flow using AbstractDirectionSelectiveFilter.
 * @author tobi */
@Description("Computes temporally and spatially smoothed optical flow using DirectionSelectiveFilter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SmoothOpticalFlowLabeler extends EventFilter2D implements Observer, FrameAnnotater {

    private final AbstractDirectionSelectiveFilter dirFilter;
    private int sx = 0, sy = 0;
    private LowpassFilter2d[][] vels;
    private int[][] lastSentTimestamps;
    private int     subSampleBy         = getInt("subSampleBy", 2);
    private float   tauMs               = getFloat("tauMs", 100);
    private int     refractoryPeriodUs  = getInt("refractoryPeriodUs", 1000);
    private boolean showRawInputEnabled = getBoolean("showRawInputEnabled", false);

    public SmoothOpticalFlowLabeler(AEChip chip) {
        super(chip);
        
        dirFilter = new DvsDirectionSelectiveFilter(chip);
        dirFilter.setAnnotationEnabled(false);
        dirFilter.setShowRawInputEnabled(false); //Otherwise this will not work as polarity events can not be cast correctly
        setEnclosedFilter(dirFilter);
        
        chip.addObserver(this);
        setPropertyTooltip("subSampleBy", "compute flow on this subsampled grid; 0 means no subsampling");
        setPropertyTooltip("tauMs", "lowpass temporal filter time contant in ms");
        setPropertyTooltip("refractoryPeriodUs", "time between successive events in us that can be output from a single subsampled address");
        setPropertyTooltip("showRawInputEnabled", "instead of returning direction selective events, return the raw input but annotate graphics with motion vectors");
        setPropertyTooltip("ppsScale", "scaling of motion vectors in pixels/sec to retina pixels");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket dirOut = dirFilter.filterPacket(in);
        checkOutputPacketEventType(OpticalFlowEvent.class);
        // add each motion event to lowpassed values and output 
        // an event if refractory period has passed
        int s = 1 << (subSampleBy - 1); //the middle of the subsample intervall, hence we add it when assigning the positions
        OutputEventIterator outItr = out.outputIterator();
        if (dirOut.getEventClass() != DvsMotionOrientationEvent.class) {
            log.log(Level.WARNING, "input events are {0}, but they need to be MotionOrientationEvent's", dirOut.getEventClass());
            return in;
        }
        for (Object o : dirOut) {
            DvsMotionOrientationEvent e = (DvsMotionOrientationEvent) o;
            int x = e.x >>> subSampleBy, y = e.y >>> subSampleBy;
            
            Point2D.Float v = vels[x][y].filter2d(e.getVelocity().x, e.getVelocity().y, e.timestamp);
            
            //We call the outItr here (outside the if) so that this is really
            // a labeler and not a Filter. Only if we call the iterator 
            // unconditionally will every event pass this Labeler.
            // As this is also a subsampler we DO shift all events to a subsample
            // position as to keep consistency. 
            // Note that we set the optFlowVelPPS to (0,0) outside the if such
            // that the smoothed speed is initialized to 0. If events do not pass
            // the refractory period test they will have a smoothed speed of 0.
            OpticalFlowEvent oe = (OpticalFlowEvent) outItr.nextOutput();
            oe.copyFrom(e);
            oe.x = (short) ((x << subSampleBy) + s);
            oe.y = (short) ((y << subSampleBy) + s);
            oe.optFlowVelPPS.setLocation(0,0);
            if (e.timestamp - lastSentTimestamps[x][y] > refractoryPeriodUs || e.timestamp < lastSentTimestamps[x][y]) {
                oe.optFlowVelPPS.setLocation(v);
                lastSentTimestamps[x][y] = e.timestamp;
            }
        }
        return showRawInputEnabled ? in : out;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        // draw individual motion vectors
        gl.glPushMatrix();
        gl.glColor3f(1, 1, 1);
        gl.glLineWidth(3f);
        gl.glPointSize(8);
        float scale = getPpsScale();
        for (Object o : out) {
            OpticalFlowEvent e = (OpticalFlowEvent) o;
            drawMotionVector(gl, e, scale);
        }
        gl.glPopMatrix();
    }

    // plots a single motion vector which is the number of pixels per second times scaling
    void drawMotionVector(GL2 gl, OpticalFlowEvent e, float scale) {
        gl.glBegin(GL.GL_POINTS);
        gl.glVertex2d(e.x, e.y);
        gl.glEnd();
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2d(e.x, e.y);
        gl.glVertex2f(e.x + (scale * e.optFlowVelPPS.x), e.y + (scale * e.optFlowVelPPS.y));
        gl.glEnd();
    }

    @Override
    synchronized public void resetFilter() {
        dirFilter.resetFilter();
        alloc();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    synchronized void alloc() {
        setSubsampling();
        if ((sx > 0) && (sy > 0)) {
            vels = new LowpassFilter2d[sx][sy];
            lastSentTimestamps = new int[sx][sy];
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    vels[x][y] = new LowpassFilter2d(tauMs);
                }
            }
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY)) {
                resetFilter();
            }
        }
    }

    private void setSubsampling() {
        sx = chip.getSizeX() >> subSampleBy;
        sy = chip.getSizeY() >> subSampleBy;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PpsScale--">
    public void setPpsScale(float ppsScale) {
        dirFilter.setPpsScale(ppsScale);
    }

    public float getPpsScale() {
        return dirFilter.getPpsScale();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SubSampleBy--">
    /**
     * @return the subSampleBy */
    public int getSubSampleBy() {
        return subSampleBy;
    }

    /**
     * @param subSampleBy the subSampleBy to set */
    synchronized public void setSubSampleBy(int subSampleBy) {
        if (subSampleBy < 0) {
            subSampleBy = 0;
        }
        this.subSampleBy = subSampleBy;
        putInt("subSampleBy", subSampleBy);
        alloc();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TauMs--">
    /**
     * @return the tauMs  */
    public float getTauMs() {
        return tauMs;
    }

    /**
     * @param tauMs the tauMs to set */
    public void setTauMs(float tauMs) {
        if (tauMs < 0) {
            tauMs = 0;
        }
        this.tauMs = tauMs;
        putFloat("tauMs", tauMs);
        if (sx > 0 && sy > 0) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    vels[x][y].setTauMs(tauMs);
                }
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --RefractoryPeriodUs--">
    /**
     * @return the refractoryPeriodUs */
    public int getRefractoryPeriodUs() {
        return refractoryPeriodUs;
    }

    /**
     * @param refractoryPeriodUs the refractoryPeriodUs to set */
    public void setRefractoryPeriodUs(int refractoryPeriodUs) {
        this.refractoryPeriodUs = refractoryPeriodUs;
        putInt("refractoryPeriodUs", refractoryPeriodUs);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowRawInputEnabled--">
    public void setShowRawInputEnabled(boolean showRawInputEnabled) {
        this.showRawInputEnabled = showRawInputEnabled;
        putBoolean("showRawInputEnabled", showRawInputEnabled);
    }

    public boolean isShowRawInputEnabled() {
        return showRawInputEnabled;
    }
    // </editor-fold>
}
