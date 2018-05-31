/* Info.java
 *
 * Created on September 28, 2007, 7:29 PM
 *
 *Copyright September 28, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.TimeZone;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;

import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import java.util.HashMap;
import java.util.Iterator;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Annotates the rendered data stream canvas with additional information like a
 * clock with absolute time, a bar showing instantaneous activity rate, a graph
 * showing historical activity over the file, etc. These features are enabled by
 * flags of the filter.
 *
 * @author tobi
 */
@Description("Adds useful information annotation to the display, e.g. date/time/event rate")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Info extends EventFilter2D implements FrameAnnotater, PropertyChangeListener, Observer {

    private final DateFormat timeFormat = new SimpleDateFormat("k:mm:ss.S"); //DateFormat.getTimeInstance();
    private final DateFormat dateFormat = DateFormat.getDateInstance();
    private final Calendar calendar = Calendar.getInstance();
    private boolean analogClock = getPrefs().getBoolean("Info.analogClock", true);
    private boolean digitalClock = getPrefs().getBoolean("Info.digitalClock", true);
    private boolean date = getPrefs().getBoolean("Info.date", true);
    private boolean absoluteTime = getPrefs().getBoolean("Info.absoluteTime", true);
    private boolean useLocalTimeZone = getPrefs().getBoolean("Info.useLocalTimeZone", true);
    private int timeOffsetMs = getPrefs().getInt("Info.timeOffsetMs", 0);
    private float timestampScaleFactor = getPrefs().getFloat("Info.timestampScaleFactor", 1);
    private float eventRateScaleMax = getPrefs().getFloat("Info.eventRateScaleMax", 1e5f);
    private boolean timeScaling = getPrefs().getBoolean("Info.timeScaling", true);
    private boolean showRateTrace = getBoolean("showRateTrace", true);
    public final int MAX_SAMPLES = 1000; // to avoid running out of memory
    private int maxSamples = getInt("maxSamples", MAX_SAMPLES);
    private long dataFileTimestampStartTimeUs = 0;
    private long wrappingCorrectionMs = 0;
    private long absoluteStartTimeMs = 0;
    volatile private long clockTimeMs = 0; // volatile because this field accessed by filtering and rendering threads
    volatile private long updateTimeMs = 0; // volatile because this field accessed by filtering and rendering threads
//    volatile private float eventRateMeasured = 0; // volatile, also shared
    private boolean addedViewerPropertyChangeListener = false; // need flag because viewer doesn't exist on creation
    private boolean eventRate = getBoolean("Info.eventRate", true);

    private TypedEventRateEstimator typedEventRateEstimator;
    private HashMap<EventRateEstimator, RateHistory> rateHistories = new HashMap();
    private XYTypeFilter xyTypeFilter;
    private EngineeringFormat engFmt = new EngineeringFormat();
    private String maxRateString = engFmt.format(eventRateScaleMax);
    private String maxTimeString = "unknown";
    private boolean logStatistics = false;
    private TobiLogger tobiLogger = null;
    private boolean showAccumulatedEventCount = getBoolean("showAccumulatedEventCount", true);
    private long accumulatedDVSEventCount = 0, accumulatedAPSSampleCount = 0, accumulatedIMUSampleCount = 0;
    private long accumulatedDVSOnEventCount = 0, accumulatedDVSOffEventCount = 0;
    private long accumulateTimeUs = 0;

    /**
     * computes the absolute time (since 1970) or relative time (in file) given
     * the timestamp. The internal wrappingCorrection and any scaling and start
     * time is applied.
     *
     * @param relativeTimeInFileMs the relative time in file in ms
     * @return the absolute or relative time depending on absoluteTime switch,
     * or the System.currentTimeMillis() if we are in live playback mode
     */
    private long computeDisplayTime(long relativeTimeInFileMs) {
        long t;
        if ((chip.getAeViewer() != null) && (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE)) {
            t = System.currentTimeMillis();
        } else {
            t = relativeTimeInFileMs + wrappingCorrectionMs;
            t = (long) (t * timestampScaleFactor);
            if (absoluteTime) {
                t += absoluteStartTimeMs;
            }
            t = t + timeOffsetMs;
        }
        return t;
    }

    /**
     * @return the logStatistics
     */
    public boolean isLogStatistics() {
        return logStatistics;
    }

    /**
     * @param logStatistics the logStatistics to set
     */
    synchronized public void setLogStatistics(boolean logStatistics) {
        boolean old = this.logStatistics;
        if (logStatistics) {
            if (!this.logStatistics) {
                setEventRate(true);
                String s = "# statistics from Info filter logged starting at " + new Date() + " and originating from ";
                if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                    s = s + " file " + chip.getAeViewer().getAePlayer().getAEInputStream().getFile().toString();
                } else {
                    s = s + " input during PlayMode=" + chip.getAeViewer().getPlayMode().toString();
                }
                s = s + "\n# evenRateFilterTauMs=" + typedEventRateEstimator.getEventRateTauMs();
                s = s + "\n#relativeLoggingTimeMs\tabsDataTimeSince1970Ms\teventRateHz";

                if (tobiLogger == null) {
                    tobiLogger = new TobiLogger("Info", s);
                } else {
                    tobiLogger.setHeaderLine(s);
                }
                tobiLogger.setEnabled(true);
            }
        } else if (this.logStatistics) {
            tobiLogger.setEnabled(false);
            log.info("stopped logging Info data to " + tobiLogger);
        }
        this.logStatistics = logStatistics;
        getSupport().firePropertyChange("logStatistics", old, this.logStatistics);
    }

    public void doStartLogging() {
        setLogStatistics(true);
    }

    public void doStopLogging() {
        setLogStatistics(false);
    }
    private long lastUpdateTime = 0;
    private final int MAX_WARNINGS_AND_UPDATE_INTERVAL = 100;
    private int warningCount = 0;

    /**
     * make event rate statistics be computed throughout a large package which
     * could span many seconds....
     *
     */
    @Override
    public void update(Observable o, Object arg) {

    }

    /**
     * @return the showAccumulatedEventCount
     */
    public boolean isShowAccumulatedEventCount() {
        return showAccumulatedEventCount;
    }

    /**
     * @param showAccumulatedEventCount the showAccumulatedEventCount to set
     */
    public void setShowAccumulatedEventCount(boolean showAccumulatedEventCount) {
        this.showAccumulatedEventCount = showAccumulatedEventCount;
        putBoolean("showAccumulatedEventCount", showAccumulatedEventCount);
    }

    // make following global to cover all histories for common scale for plots
    long rateHistoriesStartTimeMs = Long.MAX_VALUE, rateHistoriesEndTimeMs = Long.MIN_VALUE;
    float rateHistoriesMinRate = Float.MAX_VALUE, rateHistoriesMaxRate = Float.MIN_VALUE;

    private class RateHistory {

        LinkedList<RateSamples> rateSamples = new LinkedList();
        TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 24));
        long lastTimeAdded = Long.MIN_VALUE;

        synchronized void clear() {
            rateSamples.clear();
            rateHistoriesStartTimeMs = Long.MAX_VALUE;
            rateHistoriesEndTimeMs = Long.MIN_VALUE;
            rateHistoriesMinRate = Float.MAX_VALUE;
            rateHistoriesMaxRate = Float.MIN_VALUE;
        }

        synchronized void addSample(long time, float rate) {
            if (time < lastTimeAdded) {
                log.info("time went backwards by " + (time - lastTimeAdded) + "ms, clearing history");
                clear();
            }
            //            System.out.println(String.format("adding RateHistory point at t=%-20d, dt=%-15d",time,(time-lastTimeAdded)));
//            long dt=time-lastTimeAdded;
//            log.info(String.format("added new sample with dt=%d ms and rate=%.1f Hz",dt,rate));
            lastTimeAdded = time;
            if (rateSamples.size() >= getMaxSamples()) {
                RateSamples s = rateSamples.get(2);
                rateHistoriesEndTimeMs = s.time;
                rateSamples.removeFirst();
                return;
            }
            rateSamples.add(new RateSamples(time, rate));
            if (time < rateHistoriesStartTimeMs) {
                rateHistoriesStartTimeMs = time;
            }
            if (time > rateHistoriesEndTimeMs) {
                rateHistoriesEndTimeMs = time;
            }
            if (rate < rateHistoriesMinRate) {
                rateHistoriesMinRate = rate;
            }
            if (rate > rateHistoriesMaxRate) {
                rateHistoriesMaxRate = rate;
            }
        }

        synchronized private void draw(GLAutoDrawable drawable, int sign) {
            final int sx = chip.getSizeX(), sy = chip.getSizeY();
            final int yorig = sy / 3, ysize = sy / 4; // where graph starts along y axis of chip
            int n = rateSamples.size();
            if ((n < 2) || ((rateHistoriesEndTimeMs - rateHistoriesStartTimeMs) == 0)) {
                return;
            }
            GL2 gl = drawable.getGL().getGL2();
            if (sign > 0) {
                gl.glColor3f(.82f, .8f, .2f);
            } else {
                gl.glColor3f(.8f, .2f, .2f);
            }
            gl.glLineWidth(1.5f);
            // draw xaxis
            gl.glBegin(GL.GL_LINES);
            float x0 = 0;

            gl.glVertex2f(x0, yorig);
            gl.glVertex2f(sx, yorig);
            gl.glEnd();
            // draw y axis
            gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(x0, yorig);
            gl.glVertex2f(x0, yorig + ysize);
            gl.glEnd();

            gl.glPushMatrix();
//            gl.glColor3f(1, 1, .8f);
            gl.glLineWidth(1.5f);
            gl.glTranslatef(0.5f, yorig, 0);
            //            gl.glRotatef(90, 0, 0, 1);
            //            gl.glRotatef(-90, 0, 0, 1);
            gl.glScalef((float) (sx - 1) / (rateHistoriesEndTimeMs - rateHistoriesStartTimeMs), (ysize) / (rateHistoriesMaxRate), 1);
            gl.glBegin(GL.GL_LINE_STRIP);
            for (RateSamples s : rateSamples) {
                gl.glVertex2f(s.time - rateHistoriesStartTimeMs, s.rate * sign);
            }
            gl.glEnd();

            gl.glPopMatrix();
            gl.glPushMatrix();
            maxRateString = String.format("max %s eps", engFmt.format(rateHistoriesMaxRate));
            maxTimeString = String.format("%s s", engFmt.format((rateHistoriesEndTimeMs - rateHistoriesStartTimeMs) * .001f));

            GLUT glut = chip.getCanvas().getGlut();
            int font = GLUT.BITMAP_9_BY_15;
            ChipCanvas.Borders borders = chip.getCanvas().getBorders();
            float w = drawable.getSurfaceWidth() - (2 * borders.leftRight * chip.getCanvas().getScale());
//            int ntypes = typedEventRateEstimator.getNumCellTypes();
            float sw = (glut.glutBitmapLength(font, maxRateString) / w) * sx;
            gl.glRasterPos3f(0, yorig + ysize * sign, 0);
            glut.glutBitmapString(font, maxRateString);
            sw = (glut.glutBitmapLength(font, maxTimeString) / w) * sx;
            gl.glRasterPos3f(sx - sw, sy * .3f, 0);
            glut.glutBitmapString(font, maxTimeString);

            gl.glPopMatrix();
        }

        synchronized private void initFromAEFileInputStream(AEFileInputStream fis) {
            rateHistoriesEndTimeMs = (AEConstants.TICK_DEFAULT_US * fis.getFirstTimestamp()) / 1000;
            rateHistoriesEndTimeMs = (AEConstants.TICK_DEFAULT_US * fis.getLastTimestamp()) / 1000;
            if (rateHistoriesEndTimeMs < rateHistoriesStartTimeMs) {
                clear();
            }
        }
    }

    private class RateSamples {

        long time;
        float rate;

        public RateSamples(long time, float rate) {
            this.time = time;
            this.rate = rate;
        }
    }

    /**
     * Creates a new instance of Info for the chip
     *
     * @param chip the chip object
     */
    public Info(AEChip chip) {
        super(chip);
        calendar.setLenient(true); // speed up calendar
        xyTypeFilter = new XYTypeFilter(chip);
        typedEventRateEstimator = new TypedEventRateEstimator(chip);
        typedEventRateEstimator.getSupport().addPropertyChangeListener(EventRateEstimator.EVENT_RATE_UPDATE, this);

        FilterChain fc = new FilterChain(chip);
        fc.add(xyTypeFilter);
        fc.add(typedEventRateEstimator);
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
        setPropertyTooltip("eventRateSigned", "uses signed event rate for ON positive and OFF negative");
        setPropertyTooltip("eventRateTauMs", "lowpass time constant in ms for filtering event rate");
        setPropertyTooltip("showRateTrace", "shows a historical trace of event rate");
        setPropertyTooltip("maxSamples", "maximum number of samples before clearing rate history");
        setPropertyTooltip("logStatistics", "<html>enables logging of any activiated statistics (e.g. event rate) to a log file <br>written to the startup folder (host/java). <p>See the logging output for the file location.");
        setPropertyTooltip("showAccumulatedEventCount", "Shows accumulated event count since the last reset or rewind. Use it to Mark a location in a file, and then see how many events have been recieved.");
    }
    private boolean increaseWrappingCorrectionOnNextPacket = false;

    /**
     * handles tricky property changes coming from AEViewer and
     * AEFileInputStream
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof AEFileInputStream) {
            if (evt.getPropertyName().equals(AEInputStream.EVENT_REWOUND)) {
                log.info("rewind PropertyChangeEvent received by " + this + " from " + evt.getSource());
                wrappingCorrectionMs = 0;
                if (rateHistories != null) {
                    for (RateHistory r : rateHistories.values()) {
                        r.clear();
                    }
                }
                resetAccumulatedStatistics();
                setLogStatistics(false);
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_WRAPPED_TIME)) {
                increaseWrappingCorrectionOnNextPacket = true;
                //                System.out.println("property change in Info that is wrap time event");
                log.info("timestamp wrap event received by " + this + " from " + evt.getSource() + " oldValue=" + evt.getOldValue() + " newValue=" + evt.getNewValue() + ", wrappingCorrectionMs will increase on next packet");
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_INIT)) {
                log.info("EVENT_INIT recieved, signaling new input stream");
                AEFileInputStream fis = (AEFileInputStream) (evt.getSource());
                for (RateHistory r : rateHistories.values()) {
                    r.initFromAEFileInputStream(fis);
                }
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP)) {
                //                rateHistory.clear();
            }
        } else if (evt.getSource() instanceof AEViewer) {
            if (evt.getPropertyName().equals(AEViewer.EVENT_FILEOPEN)) { // TODO don't getString this because AEViewer doesn't refire event from AEPlayer and we don't getString this on initial fileopen because this filter has not yet been run so we have not added ourselves to the viewer
                for (RateHistory r : rateHistories.values()) {
                    r.clear();
                }
            } else if (evt.getPropertyName().equals(AEViewer.EVENT_ACCUMULATE_ENABLED)) {
                resetAccumulatedStatistics();
            }
        } else if (evt.getSource() instanceof EventRateEstimator) {
            if (evt.getPropertyName().equals(EventRateEstimator.EVENT_RATE_UPDATE)) {
                UpdateMessage msg = (UpdateMessage) evt.getNewValue();
                //        System.out.println("dt=" + (msg.timestamp - lastUpdateTime)/1000+" ms");
                // for large files, the relativeTimeInFileMs wraps around after 2G us and then every 4G us
                long relativeTimeInFileMs = (msg.timestamp - dataFileTimestampStartTimeUs) / 1000;
                updateTimeMs = computeDisplayTime(relativeTimeInFileMs);
                long dt = (updateTimeMs - lastUpdateTime);
                //        if (dt > eventRateFilter.getEventRateTauMs() * 2) {  // TODO hack to get around problem that sometimes the wrap preceeds the actual data
                //            log.warning("not adding this RateHistory point because dt is too big, indicating a big wrap came too soon");
                //            return;
                //        }
                if (((updateTimeMs < lastUpdateTime) && (warningCount < MAX_WARNINGS_AND_UPDATE_INTERVAL)) || ((warningCount % MAX_WARNINGS_AND_UPDATE_INTERVAL) == 0)) {
                    warningCount++;
                    log.warning("Negative delta time detected; dt=" + dt);
                }
                lastUpdateTime = updateTimeMs;

                if (showRateTrace) {
                    if (rateHistories == null || typedEventRateEstimator.getNumCellTypes() != rateHistories.values().size()) {
                        rateHistories.clear();
                        EventRateEstimator[] r = typedEventRateEstimator.getEventRateEstimators();
                        for (int i = 0; i < r.length; i++) {
                            RateHistory h = new RateHistory();
                            rateHistories.put(r[i], h);
                        }
                    }
                    rateHistories.get(msg.source).addSample(updateTimeMs, ((EventRateEstimator) msg.source).getFilteredEventRate());
                }
                if (logStatistics) {
                    String s = String.format("%20d\t%20.2g", updateTimeMs, typedEventRateEstimator.getFilteredEventRate());
                    tobiLogger.log(s);
                }
            }
        }
    }

    private void resetAccumulatedStatistics() {
        accumulatedDVSEventCount = 0;
        accumulatedDVSOnEventCount = 0;
        accumulatedDVSOffEventCount = 0;
        accumulatedAPSSampleCount = 0;
        accumulatedIMUSampleCount = 0;
        accumulateTimeUs = 0;
    }

    private void getAbsoluteStartingTimeMsFromFile() {
        AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
        if (player != null) {
            AEFileInputStreamInterface in = (player.getAEInputStream());
            if (in != null) {
                in.getSupport().addPropertyChangeListener(this);
                dataFileTimestampStartTimeUs = in.getFirstTimestamp();
                log.info("added ourselves for PropertyChangeEvents from " + in);
                absoluteStartTimeMs = in.getAbsoluteStartingTimeMs();
            }
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedViewerPropertyChangeListener) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
                chip.getAeViewer().getAePlayer().getSupport().addPropertyChangeListener(this); // TODO might be duplicated callback
                addedViewerPropertyChangeListener = true;
                getAbsoluteStartingTimeMsFromFile();
            }
        }
        if ((in != null) && (in.getSize() > 0)) {
            if (resetTimeEnabled) {
                resetTimeEnabled = false;
                dataFileTimestampStartTimeUs = in.getFirstTimestamp();
            }

        }
        in = getEnclosedFilterChain().filterPacket(in);
        long relativeTimeInFileMs = (in.getLastTimestamp() - dataFileTimestampStartTimeUs) / 1000;

        clockTimeMs = computeDisplayTime(relativeTimeInFileMs);

        // if the reader generated a bigwrap, then it told us already, before we processed this packet.
        // This msg said that the *next* packet will be wrapped.
        // so just process this packet normally, as above, then increment our wrap correction after that.
        if (increaseWrappingCorrectionOnNextPacket) {
            long old = wrappingCorrectionMs;
            boolean fwds = true; // TODO backwards not handled yet, since we don't know from wrap event which way we are going in a file
            wrappingCorrectionMs = wrappingCorrectionMs + (fwds ? (1L << 32L) / 1000 : -(1L << 31L) / 1000); // 4G us
            increaseWrappingCorrectionOnNextPacket = false;
            //            System.out.println("In Info, because flag was set, increased wrapping correction by "+(wrappingCorrectionMs-old));
            log.info("because flag was set, increased wrapping correction by " + (wrappingCorrectionMs - old));
        }
        if (in instanceof ApsDvsEventPacket) {
            ApsDvsEventPacket apsPkt = (ApsDvsEventPacket) in;
            Iterator<ApsDvsEvent> i = apsPkt.fullIterator();
            while (i.hasNext()) {
                ApsDvsEvent e = i.next();
                if (e.isImuSample()) {
                    accumulatedIMUSampleCount++;
                } else if (e.isApsData()) {
                    accumulatedAPSSampleCount++;
                } else if (e.isDVSEvent()) {
                    accumulatedDVSEventCount++;
                    if (e.getPolarity() == Polarity.On) {
                        accumulatedDVSOnEventCount++;
                    } else if (e.getPolarity() == Polarity.Off) {
                        accumulatedDVSOffEventCount++;
                    }
                }
            }
        } else {
            accumulatedDVSEventCount += in.getSize();
        }
        accumulateTimeUs += in.getDurationUs();
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        typedEventRateEstimator.resetFilter();
        if (rateHistories != null) {
            for (RateHistory r : rateHistories.values()) {
                r.clear();
            }
        }
        resetAccumulatedStatistics();
    }

    @Override
    public void initFilter() {
    }
    GLU glu = null;
    GLUquadric wheelQuad;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        drawClock(gl, clockTimeMs); // clockTimeMs is updated at the end of each packet, when the clock is displayed
        drawEventRateBars(drawable);
        drawAccumulatedEventCount(drawable);
        if (chip.getAeViewer() != null) {
            drawTimeScaling(drawable, chip.getAeViewer().getTimeExpansion());
        }
        drawRateSamples(drawable);
    }

    private void drawClock(GL2 gl, long t) {
        final int radius = 20, hourLen = 10, minLen = 18, secLen = 7, msLen = 19;
        calendar.setTimeInMillis(t);
        final int hour = calendar.get(Calendar.HOUR);
        final int hourofday = calendar.get(Calendar.HOUR_OF_DAY);

        if ((hourofday > 18) || (hourofday < 6)) {
            gl.glColor3f(.5f, .5f, 1);
        } else {
            gl.glColor3f(1f, 1f, .5f);
        }
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
                double a = (2 * Math.PI * calendar.get(Calendar.MILLISECOND)) / 1000;
                float x = msLen * (float) Math.sin(a);
                float y = msLen * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                gl.glEnd();

                // second hand
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                a = (2 * Math.PI * calendar.get(Calendar.SECOND)) / 60;
                x = secLen * (float) Math.sin(a);
                y = secLen * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                gl.glEnd();

                // minute hand
                gl.glLineWidth(4f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                int minute = calendar.get(Calendar.MINUTE);
                a = (2 * Math.PI * minute) / 60;
                x = minLen * (float) Math.sin(a);
                y = minLen * (float) Math.cos(a); // y= + when min=0, pointing at noon/midnight on clock
                gl.glVertex2f(x, y);
                gl.glEnd();

                // hour hand
                gl.glLineWidth(6f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0, 0);
                a = (2 * Math.PI * (hour + (minute / 60.0))) / 12; // a=0 for midnight, a=2*3/12*pi=pi/2 for 3am/pm, etc
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

    private void drawEventRateBars(GLAutoDrawable drawable) {
        if (!isEventRate()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        // positioning of rate bars depends on num types and display size
        ChipCanvas.Borders borders = chip.getCanvas().getBorders();

        // get screen width in screen pixels, subtract borders in screen pixels to find width of drawn chip area in screen pixels
        float /*h = drawable.getHeight(), */ w = drawable.getSurfaceWidth() - (2 * borders.leftRight * chip.getCanvas().getScale());
        int ntypes = typedEventRateEstimator.getNumCellTypes();
        final int sx = chip.getSizeX(), sy = chip.getSizeY();
        final float yorig = .9f * sy, xpos = 0, ystep = Math.max(.03f * sy, 6), barh = .03f * sy;

        gl.glPushMatrix();
        //        gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
        //        gl.glLoadIdentity();
        gl.glColor3f(1, 1, 1);
        int font = GLUT.BITMAP_9_BY_15;
        GLUT glut = chip.getCanvas().getGlut();
        int nbars = typedEventRateEstimator.isMeasureIndividualTypesEnabled() ? ntypes : 1;
        for (int i = 0; i < nbars; i++) {
            final float rate = typedEventRateEstimator.getFilteredEventRate(i);
            float bary = yorig - (ystep * i);
            gl.glRasterPos3f(xpos, bary, 0);
            String s = null;
            if (typedEventRateEstimator.isMeasureIndividualTypesEnabled()) {
                s = String.format("Type %d: %10s", i, engFmt.format(rate) + " Hz");
            } else {
                s = String.format("All %d types: %10s", ntypes, engFmt.format(rate) + " Hz");
            }
            // get the string length in screen pixels , divide by chip array in screen pixels,
            // and multiply by number of pixels to get string length in screen pixels.
            float sw = (glut.glutBitmapLength(font, s) / w) * sx;
            glut.glutBitmapString(font, s);
            gl.glRectf(xpos + sw, bary + barh, xpos + sw + ((rate * sx) / getEventRateScaleMax()), bary);
        }
        gl.glPopMatrix();

    }

    private void drawAccumulatedEventCount(GLAutoDrawable drawable) {
        if (!showAccumulatedEventCount) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(1, 1, 1);
        int font = GLUT.BITMAP_9_BY_15;
        final int sx = chip.getSizeX(), sy = chip.getSizeY();
        final float yorig = .7f * sy, xpos = 0;
        GLUT glut = chip.getCanvas().getGlut();
        gl.glRasterPos3f(xpos, yorig, 0);
        int n = chip.getNumPixels();
        float cDvs = (float) accumulatedDVSEventCount;
        float cDvsOn = (float) accumulatedDVSOnEventCount;
        float cDvsOff = (float) accumulatedDVSOffEventCount;
        float cAps = (float) accumulatedAPSSampleCount;
        float cImu = (float) accumulatedIMUSampleCount;
        float t = 1e-6f * (float) accumulateTimeUs;
        String s = String.format("In %ss:\n%s DVS events (%seps, %seps/pix)\n  %s DVS ON events (%seps, %seps/pix)\n  %s DVS OFF events (%seps, %seps/pix)\n%s APS samples (%ssps)\n%s IMU samples (%ssps)",
                engFmt.format(t),
                engFmt.format(accumulatedDVSEventCount), engFmt.format(cDvs / t), engFmt.format(cDvs / t / n),
                engFmt.format(accumulatedDVSOnEventCount), engFmt.format(cDvsOn / t), engFmt.format(cDvsOn / t / n),
                engFmt.format(accumulatedDVSOffEventCount), engFmt.format(cDvsOff / t), engFmt.format(cDvsOff / t / n),
                engFmt.format(accumulatedAPSSampleCount / 2), engFmt.format(cAps / 2 / t), // divide by two for reset/signal reads
                engFmt.format(accumulatedIMUSampleCount), engFmt.format(cImu / t));
        MultilineAnnotationTextRenderer.setScale(.2f);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .8f);
        MultilineAnnotationTextRenderer.renderMultilineString(s);
//        glut.glutBitmapString(font, s);
    }

    public void drawRateSamples(GLAutoDrawable drawable) {
        if (!showRateTrace) {
            return;
        }
        if (rateHistories == null) {
            return;
        }

        synchronized (this) {
            int i = 0;
            for (RateHistory r : rateHistories.values()) {
                r.draw(drawable, (int) Math.signum((i % 2) - .5f)); // alternate -1, +1
                i++;
            }
        }
    }

    private void drawTimeScaling(GLAutoDrawable drawable, float timeExpansion) {
        if (!isTimeScaling()) {
            return;
        }
        final int sx = chip.getSizeX(), sy = chip.getSizeY();
        final float yorig = .95f * sy, xpos = 0, barh = .03f * sy;
        int h = drawable.getSurfaceHeight(), w = drawable.getSurfaceWidth();
        GL2 gl = drawable.getGL().getGL2();

        gl.glPushMatrix();
        gl.glColor3f(1, 1, 1);
        GLUT glut = chip.getCanvas().getGlut();
        StringBuilder s = new StringBuilder();
        if ((timeExpansion < 1) && (timeExpansion != 0)) {
            s.append('/');
            timeExpansion = 1 / timeExpansion;
        } else {
            s.append('x');
        }
        int font = GLUT.BITMAP_9_BY_15;
        String s2 = String.format("Time factor: %10s", engFmt.format(timeExpansion) + s);

        float sw = (glut.glutBitmapLength(font, s2) / (float) w) * sx;
        gl.glRasterPos3f(0, yorig, 0);
        glut.glutBitmapString(font, s2);
        float x0 = xpos;
        float x1 = (float) (xpos + (x0 * Math.log10(timeExpansion)));
        float y0 = sy + barh;
        float y1 = y0;
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

    /**
     * True to show event rate in Hz
     */
    public void setEventRate(boolean eventRate) {
        boolean old = this.eventRate;
        this.eventRate = eventRate;
        getPrefs().putBoolean("Info.eventRate", eventRate);
        getSupport().firePropertyChange("eventRate", old, eventRate);
    }
    private volatile boolean resetTimeEnabled = false;

    /**
     * Reset the time zero marker to the next packet's first timestamp
     */
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
        maxRateString = engFmt.format(eventRateScaleMax);
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

    /**
     * @return the showRateTrace
     */
    public boolean isShowRateTrace() {
        return showRateTrace;
    }

    /**
     * @param showRateTrace the showRateTrace to set
     */
    public void setShowRateTrace(boolean showRateTrace) {
        this.showRateTrace = showRateTrace;
        putBoolean("showRateTrace", showRateTrace);
    }

    /**
     * @return the maxSamples
     */
    public int getMaxSamples() {
        return maxSamples;
    }

    /**
     * @param maxSamples the maxSamples to set
     */
    public void setMaxSamples(int maxSamples) {
        int old = this.maxSamples;
        if (maxSamples < 100) {
            maxSamples = 100;
        }
        this.maxSamples = maxSamples;
        putInt("maxSamples", maxSamples);
        getSupport().firePropertyChange("maxSamples", old, maxSamples);
    }

}
