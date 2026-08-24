/*
 * HarmonicFilter.java
 *
 * Created on 13 april 2006
 *
 */
package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * An AE filter that filters out boring events caused by global flickering
 * illumination. This filter measures the global event activity to obtain the
 * phase and amplitude of flicker. If the amplitude exceeds a threshold, then
 * events around the peak activity are filtered away.
 *
 * The phase and amplitude are computed by one of two methods.
 *
 * <p>
 * The first uses a global harmonic oscillator with adjustable resonant
 * frequency (set by user to double line frequency) and adjustable quality
 * factor Q. This resonator is driven by ON and OFF events in opposite
 * directions. It resonates with a phase such that it crosses zero at the peak
 * of ON and OFF activities. During a zero crossing, events are filtered away.
 *
 * <p>
 * The second method (planned, not yet implemented) histograms events into a
 * cyclic histogram whose period is set as a parameter (e.g. 10 ms for 50 Hz
 * illumination with line doubling). The histogram peaks tell the filter where
 * to reject events. The histogram is forgotten slowly by periodically decaying
 * all values. This method is not as physical and introduces a kind of 'frame'
 * for forgetting, but it is slightly cheaper to compute.
 * <p>
 * jAER 3.0 typed path: {@link #processPolarity} only (DVS polarity events).
 *
 * @author tobi
 */
@Description("Filters out global flicker (line-powered lighting) using a driven harmonic oscillator; events near the oscillator zero-crossing are blocked.")
@Help("""
<html>
<body>
<h2>HarmonicFilter</h2>
<p>Removes <b>global flicker</b> from line-powered lighting (or similar whole-array
modulation). A single harmonic oscillator is driven by ON events one way and OFF
events the other. Tune <code>freq</code> to the flicker fundamental &mdash; typically
<b>twice line frequency</b> (100&nbsp;Hz for 50&nbsp;Hz mains, 120&nbsp;Hz for 60&nbsp;Hz)
because lamps often flash once per half-cycle. When the oscillator is near a
zero-crossing of its position, those events are treated as the boring peak of
the flicker and are filtered out.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Check <b>Enabled</b> and <code>showResonatorState</code> so you can see the orbit.</li>
<li>Set <code>freq</code> to the flicker fundamental (100/120&nbsp;Hz for mains, or
e.g. 1&nbsp;kHz for a fast source). Sweep until the orbit is <b>largest and
roundest</b>.</li>
<li>Raise <code>quality</code> (Q) to sharpen the resonance once freq is close;
lower Q if the orbit will not lock or rings after rate changes.</li>
<li>Raise <code>threshold</code> (0&ndash;1) to widen the blocking window as a
fraction of oscillator peak amplitude. At 1, every event is inside the window
and all are blocked (once the resonator has locked).</li>
</ol>
<p><code>useLocalPhases</code> blocks events that fire near <i>that pixel's</i>
usual oscillator phase (flicker-locked cells) instead of using the global
<code>x&approx;0</code> test. <code>threshold</code> is then a fraction of a
half-cycle (1 = block all). The first event from each pixel is kept to seed
the local phase.</p>
<hr>
<h3>The colored square (phase portrait) &mdash; what to aim for</h3>
<p>The overlay is the resonator in <b>phase space</b>, not a tracked object.
Horizontal = displacement <code>x</code>, vertical = velocity <code>y</code>,
centered on the chip and auto-scaled. The faint trail is the recent orbit.
The <b>vertical axis</b> is <code>x = 0</code> (zero-crossing): events are blocked
when the marker is near that line.</p>
<p><b>You are tuning for a stable ellipse or circle.</b> That means the oscillator
is locked to a periodic flicker at <code>freq</code> (ON and OFF kicks arrive in
antiphase, like a harmonic drive). Then:</p>
<ul>
<li><b>Freq too far from the flicker</b> &mdash; small scribble or blob near the
center (not resonating). Try 100 vs 120, then fine-tune for the <i>largest
roundest</i> orbit (peak resonance).</li>
<li><b>Q too low</b> &mdash; even at the right freq the orbit stays small and
mushy (over-damped). Raise <code>quality</code>.</li>
<li><b>Q too high</b> &mdash; orbit is slow to appear, wobbles, or rings after
the scene changes; very sensitive to a freq error. Lower <code>quality</code>.</li>
<li><b>Always green, 0% filtered</b> &mdash; not locked (wrong freq/Q) or
amplitude has not built; the filter only blocks near a zero-crossing once the
oscillator has power.</li>
<li><b>Red flashes as the marker crosses the vertical axis</b> &mdash; locked.
Raise <code>threshold</code> to block a wider phase window (more flicker
removed, more risk of eating real edges that happen to coincide).</li>
</ul>
<p><b>Green</b> = events pass; <b>red</b> = near zero-crossing, events blocked.
The square and its trail are drawn in
<b>slow motion</b> at <code>orbitDisplayHz</code> (default 3&nbsp;Hz) using data time
so a 1&nbsp;kHz resonator is still visible at a 30&nbsp;Hz render rate: one displayed
revolution is one real cycle, stretched. Set <code>orbitDisplayHz</code> to 0 to
show the live (too-fast) state. Pause or slow playback and the square
slows with the data. Disable the overlay with <code>showResonatorState</code>.
Shared noise-filter display: <code>showFilteringStatistics</code>.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HarmonicFilter extends AbstractNoiseFilter implements FrameAnnotater {

    @Preferred
    private boolean showResonatorState = getBoolean("showResonatorState", prefs().getBoolean("HarmonicFilter.printStats", true));
    @Preferred
    private float orbitDisplayHz = getFloat("orbitDisplayHz", 3f);
    @Preferred
    private float threshold = clamp01(getPrefs().getFloat("HarmonicFilter.threshold", 0.1f)); // fraction of peak amplitude; 1 = block all
    /** Flattened [x][y][type] map; null when {@link #useLocalPhases} is off. */
    private float[] localPhases;
    private int localPhaseSy, localPhaseNt;
    private boolean useLocalPhases = prefs().getBoolean("HarmonicFilter.useLocalPhases", false);
    HarmonicOscillator oscillator = new HarmonicOscillator();
    /** Last event timestamp processed; used to detect file rewind/loop. */
    private int lastProcessedTimestamp = Integer.MIN_VALUE;
    private AEFileInputStreamInterface subscribedStream;

    public HarmonicFilter(AEChip chip) {
        super(chip);
        resetFilter();
        setPropertyTooltipBold(TT_FILT_CONTROL, "threshold", "Fraction of oscillator peak amplitude (0-1). 0=pass all, 1=block all once locked. Events with |x| <= threshold * peak are blocked.");
        setPropertyTooltipBold(TT_FILT_CONTROL, "quality", "quality factor Q of the oscillator; raise to sharpen lock once freq is close, lower if it will not lock or rings");
        setPropertyTooltipBold(TT_FILT_CONTROL, "freq", "resonant frequency in Hz; sweep for the largest roundest orbit (often 100 or 120 = 2x line frequency)");
        setPropertyTooltip(TT_FILT_CONTROL, "useLocalPhases", "Block events near this pixel's usual oscillator phase (flicker-locked cells). Threshold is a fraction of a half-cycle (1=block all).");
        setPropertyTooltip(TT_DISP, "showResonatorState", "Draw the oscillator phase-space orbit (green=pass, red=blocking near x=0); aim for a stable ellipse");
        setPropertyTooltip(TT_DISP, "orbitDisplayHz", "Slow-motion rate of the square (Hz of data time). 3 makes a 1kHz orbit visible at 30Hz render; 0=live (too fast)");
        hideProperty("correlationTimeS");
        hideProperty("sigmaDistPixels");
        hideProperty("subsampleBy");
        hideProperty("letFirstEventThrough");
        hideProperty("antiCasualEnabled");
        hideProperty("filterHotPixels");
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        resetFilter(); // reset oscillator so that it doesn't immediately go unstable.
    }

    @Override
    synchronized public void resetFilter() {
        super.resetFilter();
        oscillator.reset();
        lastProcessedTimestamp = Integer.MIN_VALUE;
        if (useLocalPhases) {
            allocLocalPhases();
        } else {
            localPhases = null;
        }
    }

    /** One array instead of sizeX×sizeY objects; filled with NaN (unseeded). */
    private void allocLocalPhases() {
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final int nt = 2; // PolarityEvent type is 0=Off, 1=On
        final int n = sx * sy * nt;
        if (localPhases == null || localPhases.length != n || localPhaseSy != sy || localPhaseNt != nt) {
            localPhases = new float[n];
            localPhaseSy = sy;
            localPhaseNt = nt;
        }
        Arrays.fill(localPhases, Float.NaN);
    }


    @Override
    public void initFilter() {
        maybeSubscribeToPlaybackStream();
        resetFilter();
    }

    /**
     * Listen for {@link AEInputStream#EVENT_REWOUND} on the viewer and on the
     * currently open file stream (FILEOPEN may already have passed).
     */
    private void maybeSubscribeToPlaybackStream() {
        maybeAddListeners(chip);
        if (chip.getAeViewer() == null || chip.getAeViewer().getAePlayer() == null) {
            return;
        }
        AEFileInputStreamInterface in = chip.getAeViewer().getAePlayer().getAEInputStream();
        if (in == null || in == subscribedStream) {
            return;
        }
        if (subscribedStream != null) {
            subscribedStream.getSupport().removePropertyChangeListener(this);
        }
        in.getSupport().addPropertyChangeListener(this);
        subscribedStream = in;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt == null || evt.getPropertyName() == null) {
            return;
        }
        switch (evt.getPropertyName()) {
            case AEInputStream.EVENT_REWOUND:
            case AEViewer.EVENT_TIMESTAMPS_RESET:
                resetFilter();
                break;
            case AEViewer.EVENT_FILEOPEN:
                // super already registered us on the new stream; just remember it
                if (chip.getAeViewer() != null && chip.getAeViewer().getAePlayer() != null) {
                    subscribedStream = chip.getAeViewer().getAePlayer().getAEInputStream();
                } else {
                    subscribedStream = null;
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (!showResonatorState) {
            return;
        }
        synchronized (this) {
            oscillator.draw(drawable.getGL().getGL2());
        }
    }

    private static float clamp01(float v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }

    private static final float PI_F = (float) Math.PI;
    private static final float TWO_PI_F = 2f * PI_F;
    private static final float LOCAL_PHASE_EMA = 0.05f; // 1 - 0.95 mix into running mean

    /** Wrap radians to (−π, π]. Fast path when already in range or one wrap away. */
    private static float wrapPi(float ph) {
        if (ph > PI_F) {
            if (ph <= PI_F + TWO_PI_F) {
                return ph - TWO_PI_F;
            }
        } else if (ph <= -PI_F) {
            if (ph > -PI_F - TWO_PI_F) {
                return ph + TWO_PI_F;
            }
        } else {
            return ph;
        }
        ph = ph % TWO_PI_F;
        if (ph > PI_F) {
            ph -= TWO_PI_F;
        } else if (ph <= -PI_F) {
            ph += TWO_PI_F;
        }
        return ph;
    }

    /**
     * Circular difference of this event's oscillator phase from the pixel's
     * running mean phase, then mix the event into that mean. Returns NaN on the
     * first event from a pixel (mean not yet seeded).
     */
    private float phaseErrorThenUpdate(int x, int y, int typeIdx) {
        final int idx = ((x * localPhaseSy) + y) * localPhaseNt + typeIdx;
        float loc = localPhases[idx];
        final float g = oscillator.phase;
        if (loc != loc) { // NaN: unseeded
            localPhases[idx] = g;
            return Float.NaN;
        }
        float d = g - loc;
        if (d > PI_F) {
            d -= TWO_PI_F;
        } else if (d <= -PI_F) {
            d += TWO_PI_F;
        }
        float nloc = loc + (LOCAL_PHASE_EMA * d);
        if (nloc > PI_F) {
            nloc -= TWO_PI_F;
        } else if (nloc <= -PI_F) {
            nloc += TWO_PI_F;
        }
        localPhases[idx] = nloc;
        return d;
    }

    /**
     * @return the useLocalPhases
     */
    public boolean isUseLocalPhases() {
        return useLocalPhases;
    }

    /**
     * @param useLocalPhases the useLocalPhases to set
     */
    synchronized public void setUseLocalPhases(boolean useLocalPhases) {
        boolean old = this.useLocalPhases;
        this.useLocalPhases = useLocalPhases;
        prefs().putBoolean("HarmonicFilter.useLocalPhases", useLocalPhases);
        if (useLocalPhases) {
            allocLocalPhases();
        } else {
            localPhases = null;
        }
        getSupport().firePropertyChange("useLocalPhases", old, this.useLocalPhases);
    }

    public class HarmonicOscillator {

        final float GEARRATIO = 20; // chop up times between spikes by tau/GEARRATIO timesteps
        final int POWER_AVERAGING_CYCLES = 10; // number of cycles to smooth power measurement
        boolean wasReset = true;
        private float f0 = prefs().getFloat("HarmonicFilter.frequency", 100); // natural frequency in Hz
        private float tau, omega, tauoverq, reciptausq;
        private float dtlim;
        private float quality = prefs().getFloat("HarmonicFilter.quality", 3); // quality factor
        private float amplitude;
        float y = 0, x = 0;  // x =position, y=velocity
        /** Phase (rad) relative to last positive x zero-crossing, wrapped to (−π, π]. */
        float phase = 0;
        private int t = 0;  // present time in timestamp ticks, used for dt in update, then stores this last timestamp
        float lastx;
        float meansq;
        float power = 0;
        float maxx, minx, maxy, miny;
        private float maxPower = 0;
        final float TICK = 1e-6f;
        private static final int MAX_NSTEPS = 2000;
        private static final int TRAIL_LEN = 256;
        private final float[] trailX = new float[TRAIL_LEN];
        private final float[] trailY = new float[TRAIL_LEN];
        private int trailHead = 0, trailCount = 0;
        private int tDisplayOrigin = 0;
        private int lastTrailSampleT = Integer.MIN_VALUE;
        //        private float measuredFreq=0;

        public HarmonicOscillator() {
            setNaturalFrequency(f0); // needed to init vars
        }

        synchronized public void reset() {
            y = 0;
            x = 0;
            phase = 0;
            power = 0;
            maxx = 0;
            minx = 0;
            maxy = 0;
            miny = 0;
            trailHead = 0;
            trailCount = 0;
            lastTrailSampleT = Integer.MIN_VALUE;
            tDisplayOrigin = t;
            wasReset = true;
        }

        /**
         * Drive the oscillator with one event. {@code kick} should be +1 (ON)
         * or −1 (OFF) so flicker at 2× line frequency locks in antiphase.
         *
         * @param needPhase increment wrapping phase (local-phase mode)
         * @param needPower EMA of x² (global |x| gate, or overlay color)
         * @param trackOrbit extrema for the phase-portrait overlay
         */
        void update(int ts, int kick, boolean needPhase, boolean needPower, boolean trackOrbit) {
            if (wasReset) {
                t = ts;
                tDisplayOrigin = ts;
                phase = 0;
                wasReset = false;
                return;
            }
            long dtsTicks = (long) ts - (long) t;
            if (dtsTicks < 0) {
                reset();
                t = ts;
                tDisplayOrigin = ts;
                wasReset = false;
                return;
            }
            lastx = x;
            y = y + kick;

            float dt = TICK * dtsTicks;
            if (dt <= dtlim) {
                y = y - (dt * reciptausq * ((tauoverq * y) + x));
                x = x + (dt * y);
            } else {
                int nsteps = (int) Math.ceil(dt / dtlim);
                if (nsteps > MAX_NSTEPS) {
                    nsteps = MAX_NSTEPS;
                }
                float ddt = (dt / nsteps) * reciptausq;
                float ddt2 = dt / nsteps;
                for (int i = 0; i < nsteps; i++) {
                    y = y - (ddt * ((tauoverq * y) + x));
                    x = x + (ddt2 * y);
                }
            }

            if (x != x) { // NaN
                log.warning("oscillator state is NaN, resetting");
                reset();
                t = ts;
                tDisplayOrigin = ts;
                wasReset = false;
                return;
            }

            t = ts;

            if (needPower) {
                float sq = x * x;
                float alpha = (dt * f0) / POWER_AVERAGING_CYCLES;
                if (alpha > 1) {
                    alpha = 1;
                }
                power = (power * (1 - alpha)) + (sq * alpha);
                if (trackOrbit && power > maxPower) {
                    maxPower = power;
                }
            }

            if (trackOrbit) {
                if (x > maxx) {
                    maxx = x;
                } else if (x < minx) {
                    minx = x;
                }
                if (y > maxy) {
                    maxy = y;
                } else if (y < miny) {
                    miny = y;
                }
            }

            if (needPhase) {
                phase = wrapPi(phase + (omega * dt));
                if ((x > 0) && (lastx <= 0)) {
                    phase = 0;
                }
            }
        }

        private void appendTrail(float px, float py) {
            if (trailCount > 0 && t == lastTrailSampleT) {
                int last = (trailHead + TRAIL_LEN - 1) % TRAIL_LEN;
                trailX[last] = px;
                trailY[last] = py;
                return;
            }
            trailX[trailHead] = px;
            trailY[trailHead] = py;
            trailHead = (trailHead + 1) % TRAIL_LEN;
            if (trailCount < TRAIL_LEN) {
                trailCount++;
            }
            lastTrailSampleT = t;
        }

        private void clearTrail() {
            trailHead = 0;
            trailCount = 0;
            lastTrailSampleT = Integer.MIN_VALUE;
        }

        public void draw(GL2 gl) {
            float w = maxx - minx;
            float h = maxy - miny;
            if (!(w > 0) || !(h > 0) || Float.isNaN(w) || Float.isNaN(h)) {
                return;
            }
            gl.glPushMatrix();
            // Chip frame is [0, sizeX] x [0, sizeY] (see ChipRendererDisplayMethodRGBA).
            // x (position) and y (velocity) have different units: scale each axis to the
            // chip independently so a lock looks round. Uniform scale collapses x to a line.
            // Center the bounding box; do not pin oscillator (0,0) or a DC offset slides the ellipse.
            final float sx = chip.getSizeX();
            final float sy = chip.getSizeY();
            final float cx = 0.5f * (maxx + minx);
            final float cy = 0.5f * (maxy + miny);
            gl.glTranslatef(sx * 0.5f, sy * 0.5f, 0);
            gl.glScalef(sx / w, sy / h, 1);
            gl.glTranslatef(-cx, -cy, 0);

            // axes: vertical is x=0 (zero-crossing / blocking), horizontal is y=0
            gl.glColor4f(1, 1, 1, 0.35f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, miny);
            gl.glVertex2f(0, maxy);
            gl.glVertex2f(minx, 0);
            gl.glVertex2f(maxx, 0);
            gl.glEnd();

            float px = x;
            float py = y;
            boolean nearZero = isNearZeroCrossing();
            if (orbitDisplayHz > 0 && f0 > 0) {
                float phi = TWO_PI_F * orbitDisplayHz * (t - tDisplayOrigin) * TICK;
                float rx = 0.5f * w;
                float ry = 0.5f * h;
                float c = (float) Math.cos(phi);
                float s = (float) Math.sin(phi);
                px = cx + rx * c;
                py = cy - ry * s;
                nearZero = Math.abs(c) <= threshold;
            }
            appendTrail(px, py);

            if (trailCount > 1) {
                gl.glColor4f(0.6f, 0.8f, 1f, 0.5f);
                gl.glBegin(GL.GL_LINE_STRIP);
                int start = trailCount < TRAIL_LEN ? 0 : trailHead;
                for (int i = 0; i < trailCount; i++) {
                    int idx = (start + i) % TRAIL_LEN;
                    gl.glVertex2f(trailX[idx], trailY[idx]);
                }
                gl.glEnd();
            }

            if (nearZero) {
                gl.glColor3f(1, 0, 0);
            } else {
                gl.glColor3f(0, 1, 0);
            }
            final float r = .02f;
            gl.glRectf(px - (r * w), py - (r * h), px + (r * w), py + (r * h));
            gl.glPopMatrix();
        }

        /**
         * Returns the phase of this particular time, based on the measured last
         * zero crossing time and time t, using the natural frequency f0.
         *
         * @param t the time to measure
         * @return the phase relative to the last positive zero crossing, wrapped
         * to (-π, π].
         */
        public float getPhase(int t) {
            return phase;
        }

        /**
         * @return the current 'position' value of the oscillator
         */
        public float getPosition() {
            return x;
        }

        /**
         * @return the current 'velocity' value of the osciallator
         */
        public float getVelocity() {
            return y;
        }

        synchronized void setNaturalFrequency(float f) {
            if (f < 1) {
                f = 1;
            }
            f0 = f; // hz
            omega = (float) (2 * Math.PI * f0);  // radians/sec
            tau = 1f / omega; // seconds
            tauoverq = tau / quality;
            reciptausq = 1f / (tau * tau);
            dtlim = tau / GEARRATIO;  // timestep must be at most this long or unstable numerically
            prefs().putFloat("HarmonicFilter.frequency", f0);
        }

        public float getNaturalFrequency() {
            return f0;
        }

        synchronized void setQuality(float q) {
            if (q < 0.1f) {
                q = 0.1f;
            }
            quality = q;
            tauoverq = tau / quality;
            prefs().putFloat("HarmonicFilter.quality", quality);
        }

        public float getQuality() {
            return quality;
        }

        /**
         * @return the last amplitude of the oscillator, i.e., the last
         * magnitude of the last peak of activity
         */
        public float getAmplitude() {
            return amplitude;
        }

        /**
         * True when |x| is within {@code threshold} of peak amplitude.
         * Peak is max(|x|, sqrt(2·power)) so a sinusoid maps [0,1] onto
         * [nothing, everything]. Uses x² vs threshold² to avoid sqrt.
         */
        private boolean isNearZeroCrossing() {
            return isNearZeroCrossingSq(threshold * threshold);
        }

        private boolean isNearZeroCrossingSq(float thresholdSq) {
            if (!(power > 0)) {
                return false;
            }
            final float x2 = x * x;
            final float twoP = 2f * power;
            if (x2 >= twoP) {
                return thresholdSq >= 1f;
            }
            return x2 <= (thresholdSq * twoP);
        }

        float getMeanPower() {
            return power;
        }

        public int getT() {
            return t;
        }

        @Override
        public String toString() {
            String s = String.format("bestFreq=%.1f Q=%.2g pos=%.1g vel=%.1g meanPower=%.1g maxPower=%.1g", f0, quality, x, y, power, maxPower);
            return s;
            //            return  "bestFreq="+f0+" t=" + t + " pos=" +x+" vel="+y+" ampl="+amplitude + " meanPower=" + getMeanPower();
        }

        /**
         * @return the maxPower
         */
        public float getMaxPower() {
            return maxPower;
        }

        /**
         * @param maxPower the maxPower to set
         */
        public void setMaxPower(float maxPower) {
            this.maxPower = maxPower;
        }
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        float old = this.threshold;
        threshold = clamp01(threshold);
        this.threshold = threshold;
        getPrefs().putFloat("HarmonicFilter.threshold", threshold);
        getSupport().firePropertyChange("threshold", old, this.threshold);
    }

    public float getQuality() {
        return oscillator.getQuality();
    }

    public void setQuality(float q) {
        oscillator.setQuality(q);
    }

    public float getFreq() {
        return oscillator.getNaturalFrequency();
    }

    public void setFreq(float f) {
        oscillator.setNaturalFrequency(f);
    }

    public boolean isShowResonatorState() {
        return showResonatorState;
    }

    public synchronized void setShowResonatorState(boolean showResonatorState) {
        boolean old = this.showResonatorState;
        this.showResonatorState = showResonatorState;
        putBoolean("showResonatorState", showResonatorState);
        if (!showResonatorState) {
            oscillator.clearTrail();
        }
        getSupport().firePropertyChange("showResonatorState", old, this.showResonatorState);
    }

    public float getOrbitDisplayHz() {
        return orbitDisplayHz;
    }

    public void setOrbitDisplayHz(float orbitDisplayHz) {
        float old = this.orbitDisplayHz;
        if (orbitDisplayHz < 0) {
            orbitDisplayHz = 0;
        }
        this.orbitDisplayHz = orbitDisplayHz;
        putFloat("orbitDisplayHz", orbitDisplayHz);
        oscillator.clearTrail();
        getSupport().firePropertyChange("orbitDisplayHz", old, this.orbitDisplayHz);
    }

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY;
    }

    /**
     * Legacy / mixed-packet path; delegates to {@link #processPolarity}.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        return processPolarity(in);
    }

    /**
     * jAER 3.0 typed polarity path: drive the oscillator from DVS events and
     * block those near a zero-crossing.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> processPolarity(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        if (subscribedStream == null) {
            maybeSubscribeToPlaybackStream();
        }
        if (in == null || in.isEmpty()) {
            return in;
        }
        if (lastProcessedTimestamp != Integer.MIN_VALUE
                && in.getFirstTimestamp() < lastProcessedTimestamp) {
            oscillator.reset();
        }

        final boolean local = useLocalPhases;
        if (local) {
            final int sx = chip.getSizeX();
            final int sy = chip.getSizeY();
            if (localPhases == null || localPhaseSy != sy
                    || localPhases.length != sx * sy * localPhaseNt) {
                allocLocalPhases();
            }
            if (localPhases == null) {
                lastProcessedTimestamp = in.getLastTimestamp();
                return in;
            }
        }

        final BasicEvent[] evs = (BasicEvent[]) in.getElementData();
        final int n = in.getSize();
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        final boolean classify = signalNoiseClassificationEnabled;
        final boolean trackOrbit = showResonatorState;
        final boolean needPower = !local || trackOrbit;
        final float thrPi = threshold * PI_F;
        final float thrSq = threshold * threshold;
        final int nt = localPhaseNt;

        for (int i = 0; i < n; i++) {
            BasicEvent be = evs[i];
            if (be == null || be.isFilteredOut()) {
                continue;
            }
            if (be.isSpecial() && !classify) {
                continue;
            }
            PolarityEvent e = (PolarityEvent) be;
            totalEventCount++;
            oscillator.update(e.timestamp, e.polarity == PolarityEvent.Polarity.Off ? -1 : 1, local, needPower, trackOrbit);
            final int ex = e.x;
            final int ey = e.y;
            if ((ex < 0) || (ex >= sx) || (ey < 0) || (ey >= sy)) {
                filterOut(e);
                continue;
            }
            boolean block;
            if (local) {
                final int typeIdx = e.getType();
                if ((typeIdx < 0) || (typeIdx >= nt)) {
                    continue;
                }
                final float d = phaseErrorThenUpdate(ex, ey, typeIdx);
                block = (d == d) && (d <= thrPi) && (d >= -thrPi);
            } else {
                block = oscillator.isNearZeroCrossingSq(thrSq);
            }
            if (block) {
                filterOut(e);
            } else if (classify) {
                filterIn(e);
            }
        }
        lastProcessedTimestamp = in.getLastTimestamp();
        return in;
    }

    @Override
    public String infoString() {
        return String.format("%s: f0=%.1fHz Q=%.2g thr=%.2g localPh=%s",
                camelCaseClassname(), getFreq(), getQuality(), getThreshold(), isUseLocalPhases());
    }
}
//    /**
//     * filters in to out. if filtering is enabled, the number of out may be less
//     * than the number putString in
//     *@param in input events can be null or empty.
//     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
//     */
//    synchronized public AEPacket2D filter(AEPacket2D in) {
//        if(in==null) return null;
//        in.setNumCellTypes(2);
//        AEPacket2D out=in;
//        if(!filterEnabled) return in;
//        if(enclosedFilter!=null) in=enclosedFilter.filter(in);
//        if(!filterInPlaceEnabled) out=new AEPacket2D(in.getNumEvents());
//        out.setNumCellTypes(2);
//        // filter
//
//        int n=in.getNumEvents();
//        if(n==0) return in;
//
//        short[] xs=in.getXs(), ys=in.getYs();
//        int[] timestamps=in.getTimestamps();
//        byte[] types=in.getTypes();
//
//        short[] outxs=out.getXs(), outys=out.getYs();
//        int[] outtimestamps=out.getTimestamps();
//        byte[] outtypes=out.getTypes();
//
//        // for each event only write it to the tmp buffers if it isn't boring
//        // this means only write if the dt is sufficiently different than the previous dt
//        index=0;
//        for(i=0;i<n;i++){
//            ts=timestamps[i];
//            type=types[i];
//            oscillator.update(ts,type);
//            if(Float.isNaN(oscillator.getVelocity())){
//                setFilterEnabled(false);
//                log.warning("oscillator overflowed, disabling filter");
//                resetFilter();
//                return in;
//            }
//            if(!oscillator.isZeroCrossing()){
//                x=xs[i];
//                y=ys[i];
//                out.addEvent(x,y,type,ts);
//            }
//        }
//        if(printStats){
//            float t=1e-6f*ts;
//            System.out.println( (cycle++)+","+t+","+oscillator.getPosition()+","+oscillator.getMeanPower());
//        }
//        return out;
//    }

