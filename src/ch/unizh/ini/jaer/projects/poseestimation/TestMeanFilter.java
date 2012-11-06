/*
 * TestMeanFilter.java
 *
 * Created on October 22, 2012, 12:33 PM
 *
 * Test Filter that takes indicates mean of events over a given time 
 */

package ch.unizh.ini.jaer.projects.poseestimation;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
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
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;


/**
 * @author haza
 */
@Description("Points towards mean of activity")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TestMeanFilter extends EventFilter2D implements Observer, FrameAnnotater {

    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    // Controls
    protected int dt = getPrefs().getInt("TestMeanFilter.dt", 30000);
    {
        setPropertyTooltip("dt", "Time Step for Calculation");
    }

    protected int eventCountThreshold = getPrefs().getInt("TestMeanFilter.eventCountThreshold", 1000);
    {
        setPropertyTooltip("eventCountThreshold", "Minimum number of events needed to take mean of");
    }

    int eventSumX = 0;
    int eventSumY = 0;
    int eventCount = 0;
  
    float eventPosSumX = 0;
    float eventPosSumY = 0;
    int eventPosCount = 0;

    float eventNegSumX = 0;
    float eventNegSumY = 0;
    int eventNegCount = 0;
    
    int tinit = 0;
    int ts = 0; // used to reset filter
    int numEvents = 0;
    Point2D.Float p = new Point2D.Float();
    Point2D.Float p_prev = new Point2D.Float();

    public TestMeanFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }

    
    public void annotate (Graphics2D g){
    }

    @Override
    public void annotate (GLAutoDrawable drawable){
        if ( !isAnnotationEnabled() ){
            return;
        }
        GL gl = drawable.getGL();

        if ( gl == null ){
            return;
        }
        
        gl.glPushMatrix();
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(1f,0f,0f);
        gl.glVertex2f(p_prev.x,p_prev.y);
        gl.glVertex2f(p.x,p.y);
        gl.glEnd();
        gl.glPopMatrix();
    }

    // plots a single motion vector which is the number of pixels per second times scaling
    public void annotate (float[][][] frame){

    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!filterEnabled) {
            return in;
        }
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        checkOutputPacketEventType(in);

        // for each event only write it to the out buffers if it is within dt of the last time an event happened in neighborhood
        OutputEventIterator outItr = out.outputIterator();
        int sx = chip.getSizeX() - 1;
        int sy = chip.getSizeY() - 1;
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            numEvents++;
            ts = i.timestamp;
            short x = i.x, y = i.y;
            if ((x < 0 || y < 0)) {
                continue;
            }
            
            if (ts - tinit > dt && eventCount >= eventCountThreshold) {
                // Overwrite current data point with mean (figure out how to create new one)
                p_prev.x = p.x;
                p_prev.y = p.y;
                p.x = (float) eventSumX / eventCount;
                p.y = (float) eventSumY / eventCount;

                // Reset calculation values
                tinit = ts;
                eventSumX = 0;
                eventSumY = 0;
                eventCount = 0;
                 
            } else {
                eventSumX += x;
                eventSumY += y;
                eventCount++;  
            }            
            BasicEvent o = (BasicEvent) outItr.nextOutput(); 
            o.copyFrom(i);

        }

        return out;
    }

    /**
     * gets the background allowed delay in us
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getDt() {
        return this.dt;
    }

    /**
     * sets the background delay in us
    <p>
    Fires a PropertyChangeEvent "dt"
    
     * @see #getDt
     * @param dt delay in us
     */
    public void setDt(final int dt) {
        getPrefs().putInt("BackgroundActivityFilter.dt", dt);
        getSupport().firePropertyChange("dt", this.dt, dt);
        this.dt = dt;
    }

    public int getMinDt() {
        return 10;
    }

    public int getMaxDt() {
        return 100000;
    }

    
    public int getEventCountThreshold() {
        return this.eventCountThreshold;
    }

    public void setEventCountThreshold(final int eventCountThreshold) {
        getPrefs().putInt("TestMeanFilter.eventCountThreshold", eventCountThreshold);
        getSupport().firePropertyChange("eventCountThreshold", this.eventCountThreshold, eventCountThreshold);
        this.eventCountThreshold = eventCountThreshold;
    }

    public int getMinEventCountThresold() {
        return 1;
    }

    public int getMaxEventCountThreshold() {
        return 100000;
    }
    
//    public Object getFilterState() {
//        return lastTimestamps;
//    }

    @Override
    synchronized public void resetFilter() {
        // set all lastTimestamps to max value so that any event is soon enough, guarenteed to be less than it
        tinit = 0;
        eventSumX = 0;
        eventSumY = 0;
        eventCount = 0;
        numEvents = 0;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            initFilter();
        }
    }

    @Override
    public void initFilter() {
        tinit = 0;
        eventSumX = 0;
        eventSumY = 0;
        eventCount = 0;
        numEvents = 0;
    }
}
