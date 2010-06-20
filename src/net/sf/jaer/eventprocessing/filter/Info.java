/*
 * Info.java
 *
 * Created on September 28, 2007, 7:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright September 28, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;
import com.sun.opengl.util.*;
import java.awt.Graphics2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * Annotates the rendered data stream canvas
with additional information like a
clock with absolute time, a bar showing instantaneous activity rate,
a graph showing historical activity over the file, etc.
These features are enabled by flags of the filter.
 * @author tobi
 */
public class Info extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    public static String getDescription() {
        return "Adds useful information annotation to the display, e.g. date/time/event rate.";
    }
    private DateFormat timeFormat = new SimpleDateFormat("k:mm:ss.S"); //DateFormat.getTimeInstance();
    private DateFormat dateFormat = DateFormat.getDateInstance();
    private Date dateInstance = new Date();
    private Calendar calendar = Calendar.getInstance();
    private File lastFile = null;
    private boolean analogClock = getPrefs().getBoolean("Info.analogClock", true);
    private boolean digitalClock = getPrefs().getBoolean("Info.digitalClock", true);
    private boolean date = getPrefs().getBoolean("Info.date", true);
    private boolean absoluteTime = getPrefs().getBoolean("Info.absoluteTime", true);
    private boolean useLocalTimeZone = getPrefs().getBoolean("Info.useLocalTimeZone", true);
    private int timeOffsetMs = getPrefs().getInt("Info.timeOffsetMs", 0);
    private float timestampScaleFactor = getPrefs().getFloat("Info.timestampScaleFactor", 1);
    private float eventRateScaleMax = getPrefs().getFloat("Info.eventRateScaleMax", 1e5f);
    private boolean timeScaling = getPrefs().getBoolean("Info.timeScaling", true);
    private long dataFileTimestampStartTimeMs = 0;
    private long wrappingCorrectionMs = 0;
    private long absoluteStartTimeMs = 0;
    volatile private long relativeTimeInFileMs = 0; // volatile because this field accessed by filtering and rendering threads
    volatile private float eventRateMeasured = 0; // volatile, also shared
    private boolean addedViewerPropertyChangeListener = false; // need flag because viewer doesn't exist on creation
    private boolean eventRate = getPrefs().getBoolean("Info.eventRate", true);
    private EventRateEstimator eventRateFilter;
    private EngineeringFormat engFmt = new EngineeringFormat();

    /** Creates a new instance of Info for the chip
    @param chip the chip object
     */
    public Info(AEChip chip) {
        super(chip);
        calendar.setLenient(true); // speed up calendar
        eventRateFilter = new EventRateEstimator(chip);
        FilterChain fc = new FilterChain(chip);
        fc.add(eventRateFilter);
        setEnclosedFilterChain(fc);
        setUseLocalTimeZone(useLocalTimeZone);
        setPropertyTooltip("analogClock", "show normal circular clock");
        setPropertyTooltip("digitalClock", "show digital clock");
        setPropertyTooltip("date", "show date");
        setPropertyTooltip("absoluteTime", "enable to show absolute time, disable to show timestmp time (usually relative to start of recording");
        setPropertyTooltip("useLocalTimeZone", "if enabled, time will be displayed in your timezone, e.g. +1 hour in Zurich relative to GMT; if disabled, time will be displayed in GMT");
        setPropertyTooltip("timeOffsetMs", "add this time in ms to the displayed time");
        setPropertyTooltip("timestampScaleFactor", "scale timestamps by this factor to account for crystal offset");
        setPropertyTooltip("eventRateScaleMax", "scale event rates to this maximum");
        setPropertyTooltip("timeScaling", "shows time scaling relative to real time");
        setPropertyTooltip("eventRate", "shows average event rate");
        setPropertyTooltip("eventRateTauMs", "lowpass time constant in ms for filtering event rate");
    }

    /** handles tricky property changes coming from AEViewer and AEFileInputStream */
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof AEFileInputStream) {
            if (evt.getPropertyName().equals(AEInputStream.EVENT_REWIND)) {
                log.info("rewind PropertyChangeEvent received by " + this + " from " + evt.getSource());
                wrappingCorrectionMs = 0;
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_WRAPPED_TIME)) {
                wrappingCorrectionMs += (long) (1L << 32L) / 1000; // TODO fixme
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_INIT)) {
                AEFileInputStream fis = (AEFileInputStream) (evt.getSource());
            }
        } else if (evt.getSource() instanceof AEViewer) {
            if (evt.getPropertyName().equals(AEViewer.EVENT_FILEOPEN)) { // TODO don't get this because AEViewer doesn't refire event from AEPlayer and we don't get this on initial fileopen because this filter has not yet been run so we have not added ourselves to the viewer
                getAbsoluteStartingTimeMsFromFile();
            }
        }
    }

    private void getAbsoluteStartingTimeMsFromFile() {
        AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
        if (player != null) {
            AEFileInputStream in = (AEFileInputStream) (player.getAEInputStream());
            if (in != null) {
                in.getSupport().addPropertyChangeListener(this);
                dataFileTimestampStartTimeMs = in.getFirstTimestamp();
                log.info("added ourselves for PropertyChangeEvents from " + in);
                absoluteStartTimeMs = in.getAbsoluteStartingTimeMs();
            }
        }
    }

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled() || in == null || in.getSize() == 0) {
            return in;
        }
        if (!addedViewerPropertyChangeListener) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                addedViewerPropertyChangeListener = true;
                getAbsoluteStartingTimeMsFromFile();
            }
        }
        if (in != null && in.getSize() > 0) {
            if (resetTimeEnabled) {
                resetTimeEnabled = false;
                dataFileTimestampStartTimeMs = in.getFirstTimestamp();
            }
            relativeTimeInFileMs = (in.getLastTimestamp() - dataFileTimestampStartTimeMs) / 1000;
        }
        if (isEventRate()) {
            eventRateFilter.filterPacket(in);
            eventRateMeasured = eventRateFilter.getFilteredEventRate();
        }
        return in;
    }

    public void resetFilter() {
        eventRateFilter.resetFilter();
    }

    public void initFilter() {
    }
    GLU glu = null;
    GLUquadric wheelQuad;

    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        long t = 0;
        if (chip.getAeViewer() != null && chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE) {
            t = System.currentTimeMillis();
        } else {
            t = relativeTimeInFileMs + wrappingCorrectionMs;
            t = (long) (t * timestampScaleFactor);
            if (absoluteTime) {
                t += absoluteStartTimeMs;
            }
            t = t + timeOffsetMs;
        }
        drawClock(gl, t);
        drawEventRate(gl, eventRateMeasured);
        if(chip.getAeViewer()!=null) drawTimeScaling(gl, chip.getAeViewer().getTimeExpansion());
    }

    private void drawClock(GL gl, long t) {
        final int radius = 20, hourLen = 10, minLen = 18, secLen = 7, msLen = 19;
        calendar.setTimeInMillis(t);

        gl.glColor3f(1, 1, 1);
        if (analogClock) {
            // draw clock circle
            if (glu == null) {
                glu = new GLU();
            }
            if (wheelQuad == null) {
                wheelQuad = glu.gluNewQuadric();
            }
            gl.glPushMatrix();
            {
                gl.glTranslatef(radius + 2, radius + 6, 0); // clock center
                glu.gluQuadricDrawStyle(wheelQuad, GLU.GLU_FILL);
                glu.gluDisk(wheelQuad, radius, radius + 0.5f, 24, 1);

                // draw hour, minute, second hands
                // each hand has x,y components related to periodicity of clock and time

                gl.glColor3f(1, 1, 1);

                // ms hand
                gl.glLineWidth(1f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                double a = 2 * Math.PI * calendar.get(Calendar.MILLISECOND) / 1000;
                float x = msLen * (float) Math.sin(a);
                float y = msLen * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                gl.glEnd();

                // second hand
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                a = 2 * Math.PI * calendar.get(Calendar.SECOND) / 60;
                x = secLen * (float) Math.sin(a);
                y = secLen * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                gl.glEnd();

                // minute hand
                gl.glLineWidth(4f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                int minute = calendar.get(Calendar.MINUTE);
                a = 2 * Math.PI * minute / 60;
                x = minLen * (float) Math.sin(a);
                y = minLen * (float) Math.cos(a); // y= + when min=0, pointing at noon/midnight on clock
                gl.glVertex2f(x, y);
                gl.glEnd();

                // hour hand
                gl.glLineWidth(6f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                a = 2 * Math.PI * (calendar.get(Calendar.HOUR) + minute / 60.0) / 12; // a=0 for midnight, a=2*3/12*pi=pi/2 for 3am/pm, etc
                x = hourLen * (float) Math.sin(a);
                y = hourLen * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                gl.glEnd();
            }
            gl.glPopMatrix();
        }

        if (digitalClock) {
            gl.glPushMatrix();
            gl.glRasterPos3f(0, 0, 0);
            GLUT glut = chip.getCanvas().getGlut();
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, timeFormat.format(calendar.getTime()) + " ");
            if (date) {
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, dateFormat.format(calendar.getTime()));
            }
            gl.glPopMatrix();
        }
    }

    private void drawEventRate(GL gl, float eventRateMeasured) {
        if (!isEventRate()) {
            return;
        }
        final int pos = 8, xpos = 25;
        gl.glPushMatrix();
        gl.glColor3f(0, 0, 1);
        gl.glRasterPos3f(0, chip.getSizeY() - pos, 0);
        GLUT glut = chip.getCanvas().getGlut();
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("%10s", engFmt.format(eventRateMeasured) + " Hz"));
        gl.glColor3f(0, 0, 1);
        gl.glRectf(xpos, chip.getSizeY() - pos, xpos + eventRateMeasured * chip.getSizeX() / getEventRateScaleMax(), chip.getSizeY() - pos + 3);
        gl.glPopMatrix();
    }

    private void drawTimeScaling(GL gl, float timeExpansion) {
        if (!isTimeScaling()) {
            return;
        }
        final int pos = 4, xpos = 25;
        gl.glPushMatrix();
        gl.glColor3f(0, 0, 1);
        gl.glRasterPos3f(0, chip.getSizeY() - pos, 0);
        GLUT glut = chip.getCanvas().getGlut();
        StringBuilder s = new StringBuilder();
        if (timeExpansion < 1 && timeExpansion != 0) {
            s.append('/');
            timeExpansion = 1 / timeExpansion;
        } else {
            s.append('/');
        }

        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("%10s", engFmt.format(timeExpansion) + s));
        gl.glColor3f(0, 0, 1);
        float x0 = xpos;
        float x1 = (float) (xpos + x0 * Math.log10(timeExpansion));
        float y0 = chip.getSizeY() - pos;
        float y1 = y0 - 1;
        gl.glRectf(x0, y0, x1, y1);
        gl.glPopMatrix();

    }

    public boolean isAnalogClock() {
        return analogClock;
    }

    public void setAnalogClock(boolean analogClock) {
        this.analogClock = analogClock;
        getPrefs().putBoolean("Info.analogClock", analogClock);
    }

    public boolean isDigitalClock() {
        return digitalClock;
    }

    public void setDigitalClock(boolean digitalClock) {
        this.digitalClock = digitalClock;
        getPrefs().putBoolean("Info.digitalClock", digitalClock);
    }

    public boolean isDate() {
        return date;
    }

    public void setDate(boolean date) {
        this.date = date;
        getPrefs().putBoolean("Info.date", date);
    }

    public boolean isAbsoluteTime() {
        return absoluteTime;
    }

    public void setAbsoluteTime(boolean absoluteTime) {
        this.absoluteTime = absoluteTime;
        getPrefs().putBoolean("Info.absoluteTime", absoluteTime);
    }

    public boolean isEventRate() {
        return eventRate;
    }

    /** True to show event rate in Hz */
    public void setEventRate(boolean eventRate) {
        this.eventRate = eventRate;
        getPrefs().putBoolean("Info.eventRate", eventRate);
    }
    private volatile boolean resetTimeEnabled = false;

    /** Reset the time zero marker to the next packet's first timestamp */
    public void doResetTime() {
        resetTimeEnabled = true;
    }

    public boolean isUseLocalTimeZone() {
        return useLocalTimeZone;
    }

    public void setUseLocalTimeZone(boolean useLocalTimeZone) {
        this.useLocalTimeZone = useLocalTimeZone;
        getPrefs().putBoolean("Info.useLocalTimeZone", useLocalTimeZone);
        if (!useLocalTimeZone) {
            TimeZone tz = TimeZone.getTimeZone("GMT");
            calendar.setTimeZone(tz);
            timeFormat.setTimeZone(tz);
        } else {
            calendar.setTimeZone(TimeZone.getDefault());
            timeFormat.setTimeZone(TimeZone.getDefault()); // don't know why we have to this too
        }
    }

    public int getTimeOffsetMs() {
        return timeOffsetMs;
    }

    public void setTimeOffsetMs(int timeOffsetMs) {
        this.timeOffsetMs = timeOffsetMs;
        getPrefs().putInt("Info.timeOffsetMs", timeOffsetMs);
    }

    public float getTimestampScaleFactor() {
        return timestampScaleFactor;
    }

    public void setTimestampScaleFactor(float timestampScaleFactor) {
        this.timestampScaleFactor = timestampScaleFactor;
        getPrefs().putFloat("Info.timestampScaleFactor", timestampScaleFactor);
    }

    /**
     * @return the eventRateScaleMax
     */
    public float getEventRateScaleMax() {
        return eventRateScaleMax;
    }

    /**
     * @param eventRateScaleMax the eventRateScaleMax to set
     */
    public void setEventRateScaleMax(float eventRateScaleMax) {
        this.eventRateScaleMax = eventRateScaleMax;
        getPrefs().putFloat("Info.eventRateScaleMax", eventRateScaleMax);
    }

    /**
     * @return the timeScaling
     */
    public boolean isTimeScaling() {
        return timeScaling;
    }

    /**
     * @param timeScaling the timeScaling to set
     */
    public void setTimeScaling(boolean timeScaling) {
        this.timeScaling = timeScaling;
        getPrefs().putBoolean("Info.timeScaling", timeScaling);
    }
}
