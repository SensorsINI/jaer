/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Color;
import java.util.Arrays;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.DrawGL;

/**
 * A BA denoiser that scores past events takes events of same polarity and
 * computes a correlation score based on linear correlation metric across the
 * NNb.
 *
 * @author Tobi Delbruck 2025
 */
@Description("Denoises events by scoring linear correlation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class LinearCorrelationDenoiser extends SpatioTemporalCorrelationFilter {

    @Preferred
    private float minCorrelation = getFloat("minCorrelation", 3);
    @Preferred
    private float maxEntropy = getFloat("maxEntropy", 2);
    @Preferred
    private boolean nearbyAnglesIncluded = getBoolean("nearbyAnglesIncluded", false);
    @Preferred
    private boolean expAgeDecay = getBoolean("expAgeDecay", false);
    @Preferred
    private boolean useAge = getBoolean("useAge", true);
    @Preferred
    private boolean useEntropy = getBoolean("useEntropy", true);

    private boolean drawAngleHist = getBoolean("drawAngleHist", true);

    private AngleHist angleHist;
    private int tauUs = 0; // buffers the correlationTimeS  in us for efficiency

    public LinearCorrelationDenoiser(AEChip chip) {
        super(chip);

        hideProperty("numMustBeCorrelated");
        hideProperty("antiCasualEnabled");
        hideProperty("shotNoiseCorrelationTimeS");
        hideProperty("filterAlternativePolarityShotNoiseEnabled");
        hideProperty("polaritiesMustMatch");
        setPropertyTooltip(TT_FILT_CONTROL, "minCorrelation", "threshold value of correlation score to classify as signal event. The higher the threshold, the fewer events pass through.");
        setPropertyTooltip(TT_FILT_CONTROL, "maxEntropy", "threshold value of entropy score to classify as signal event. The lower the threshold, the fewer events pass through.");
        setPropertyTooltip(TT_FILT_CONTROL, "useAge", "Use linear age, fading to zero for events older than correlationTimeS.");
        setPropertyTooltip(TT_FILT_CONTROL, "nearbyAnglesIncluded", "Sum scores of angle bins next to max bin");
        setPropertyTooltip(TT_FILT_CONTROL, "expAgeDecay", "NNb event youth decays exponentially according to exp(-deltaTime/correlationTimeS)");
        setPropertyTooltip(TT_FILT_CONTROL, "useEntropy", "Threshold is on negentropy of histogram distribution. Only events with low entropy pass through.");
        setPropertyTooltip(TT_DISP, "drawAngleHist", "Draw histogram of angles from last event in packet");

        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);

        angleHist = new AngleHist();

    }

    @Override
    public String infoString() {
        return String.format("%s: sigma=%d (%dx%d) thr=%.2f useAge=%s nearbyAngles=%s useEntropy=%s",
                camelCaseClassname(), sigmaDistPixels, angleHist.size, angleHist.size,
                minCorrelation, useAge, nearbyAnglesIncluded, isUseEntropy());
    }

    @Override
    public void setSigmaDistPixels(int sigmaDistPixels) {
        int old = this.sigmaDistPixels;
        super.setSigmaDistPixels(sigmaDistPixels);
        if (old != sigmaDistPixels) {
            angleHist.construct();
        }
    }

    /**
     * Returns relative age
     *
     * @param dt the negative delta time in us
     * @return the age, 1 for simultaneous, 0 for times =>tauUs
     */
    private float age(final int dt) {
        if (dt < tauUs && dt > 0) {
            float age = 1;
            if (expAgeDecay) {
                age = (float) Math.exp((float) -dt / tauUs);  // if dt is 0, then age is 1, decays to 1/e at tauUs
            } else {
                age = 1 - ((float) (dt)) / tauUs;  // if dt is 0, then linearDt is 1, if dt=-tauUs, then linearDt=0
            }
            return age;
        } else {
            return 0;
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        if (angleHist != null && drawAngleHist) {
            angleHist.draw(gl);
        }
    }

    class AngleHist {

        int[][] histBinLUT;
        float[][] angles;
        int numBins;
        int size; // 2*sigma+1
        int sigma; // 'radius' of kernel
        float binWidthRad;
        float[] hist;
        boolean signalEvent = false;
        float score, sum, entropy, max;

        public AngleHist() {
            construct();
        }

        private int getAngleBin(int x, int y) {
            int bin = histBinLUT[x + sigma][y + sigma]; // (int) (getAngle(x, y) / binWidthRad);
            return bin;
        }

        void clearHist() {
            Arrays.fill(hist, 0);
            signalEvent = false;
            entropy = Float.NaN;
            sum = 0;
            max = 0;
        }

        void addToHist(int x, int y, float age) {
            int bin = getAngleBin(x, y);
            hist[bin] += age;
        }

        void draw(GL2 gl) {
            gl.glPushMatrix();
            gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
            gl.glLineWidth(2);
            if (signalEvent) {
                gl.glColor4f(0, 1, 0, 1);
            } else {
                gl.glColor4f(1, 0, 0, 1);
            }
            float dx = 5;
            float sy = 5;
            int lx = chip.getSizeX() / 3, ly = chip.getSizeY() / 3;
            gl.glTranslatef(lx, ly, 0);

            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i < numBins; i++) {
                float y = 1 + (sy * hist[i]);
                float x1 = 1 + (dx * i), x2 = x1 + dx;
                gl.glVertex2f(x1, 1);
                gl.glVertex2f(x1, y);
                gl.glVertex2f(x2, y);
                gl.glVertex2f(x2, 1);
            }
            gl.glEnd();
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 1 + (sy * minCorrelation));
            gl.glVertex2f(dx * numBins, 1 + (sy * minCorrelation));
            gl.glEnd();
            gl.glPopAttrib();

            gl.glPopMatrix();
            gl.glPushMatrix();
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), lx, ly, 0f, Color.yellow,
                    String.format("max=%s, entropy=%s", eng.format(max), eng.format(entropy)));
            gl.glPopMatrix();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("AngleHist: ");
            sb.append(String.format("(%dx%d) %d bins ", size, size, numBins));
            sb.append(String.format("binWidth=%s deg. Bins: ",
                    eng.format(binWidthRad * 180 / Math.PI)));
            for (float count : hist) {
                sb.append(String.format("%.1f ", count));
            }
            return sb.toString();
        }

        private float r2d(float rad) {
            return (float) (rad * 180 / Math.PI);
        }

        private String r2dString(float rad) {
            return String.format("%6s", eng.format(r2d(rad)));
        }

        private void construct() {
            sigma = getSigmaDistPixels();
            size = sigma * 2 + 1;
            histBinLUT = new int[size][size];
            angles = new float[size][size];
            numBins = 0;
            switch (sigma) {
                case 1:
                    numBins = 4;
                    break;
                case 2:
                    numBins = 8;
                    break;
                case 3:
                    numBins = 12;
                    break;
                default:
                    numBins = 16;
            }
            hist = new float[numBins];
            binWidthRad = (float) Math.PI / numBins;
            StringBuilder sb = new StringBuilder("angles\n");
            for (int j = 0; j < size; j++) {
                int y = j - sigmaDistPixels;
                sb.append("y=").append(y).append("  ");
                for (int i = 0; i < size; i++) {
                    int x = i - sigmaDistPixels;
                    double ang = Math.atan2(y, x);
                    if (ang < 0) {
                        ang += Math.PI;
                    }
                    if (ang > Math.PI) {
                        ang = Math.PI;
                    }
                    float angRad = (float) ang; // 0 to Pi finally
                    float angDeg = (float) (angRad * 180 / Math.PI);
                    angles[i][j] = angDeg;
                    float binHalf = binWidthRad / 2;
                    int bin;
                    if (angRad <= binHalf) {
                        bin = 0;
                    } else if (angRad >= Math.PI - binHalf) {
                        bin = 0;
                    } else {
                        bin = (int) ((angRad - binHalf) / binWidthRad) + 1;
                    }
                    histBinLUT[i][j] = bin;
                    sb.append(" x=").append(x).append(" ").append(r2dString(angRad)).append("/").append(histBinLUT[i][j]);
                }
                sb.append("\n");
            }
            log.info(String.format("new AngleHist with %d bins, binWidthDeg=%s\n%s",
                    numBins,
                    eng.format(binWidthRad * 180 / Math.PI),
                    sb.toString()));

        }

        boolean isSignalEvent() {
            max = calculateMax();
            signalEvent = false;
            if (max >= minCorrelation) {
                if (!useEntropy) {
                    signalEvent = true;
                } else {
                    entropy = calculateEntropy();
                    if (entropy <= maxEntropy) {
                        signalEvent = true;
                    }
                }
            }
            return signalEvent;
        }

        private float computeScore() {
            if (useEntropy) {
                entropy = calculateEntropy();
                return entropy;
            } else {
                max = calculateMax();
                return max;
            }

        }

        private float calculateMax() {
            max = 0;
            int idx = 0, maxIdx = -1;
            for (float count : hist) {
                if (count > max) {
                    max = count;
                    maxIdx = idx;
                }
                idx++;
            }
            if (!nearbyAnglesIncluded) {
                return max;
            } else {
                int nextBin = (maxIdx + 1) % numBins, prevBin = (maxIdx + numBins - 1) % numBins;
                return (max + hist[prevBin] + hist[nextBin]) / 3;
            }
        }

        private float calculateSum() {
            sum = 0;
            for (float value : hist) {
                sum += value;
            }
            return sum;
        }

        private float calculateEntropy() {

            // test
//            Arrays.fill(hist,1);
//            hist[0]=10;
            sum = calculateSum();

            if (sum == 0) {
                entropy = (float) (Math.log(numBins) / Math.log(2)); // all zero, max entropy = n-bits, e.g. 3 for 8 bins
            } else {
                entropy = 0;
                for (float value : hist) {
                    float probability = (float) value / sum;
                    if (probability > 0) {
                        entropy -= probability * Math.log(probability); // entrop;y is zero for only single bin
                    }
                }

                entropy = entropy / (float) Math.log(2);
            }
            return entropy;
        }

    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        resetCountsAndNegativeEvents();
        if (timestampImage == null) {
            allocateMaps(chip);
        }
        tauUs = (int) Math.round(getCorrelationTimeS() * 1e6f);
        ssx = sxm1 >> subsampleBy;
        ssy = sym1 >> subsampleBy;
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        final boolean fhp = filterHotPixels;
        final NnbRange nnbRange = new NnbRange();

        try {
            for (BasicEvent e : in) {
                if (e == null) {
                    continue;
                }
                // comment out to support special "noise" events that are labeled special for denoising study
//                if (e.isSpecial()) {
//                    continue;
//                }
                totalEventCount++;
                final int ts = e.timestamp;
                final int x = (e.x >> subsampleBy), y = (e.y >> subsampleBy); // subsampling address
                if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) { // out of bounds, discard (maybe bad USB or something)
                    filterOut(e);
                    continue;
                }
                if (timestampImage[x][y] == DEFAULT_TIMESTAMP) {
                    storeTimestampPolarity(x, y, e);
                    if (letFirstEventThrough) {
                        filterIn(e);
                        continue;
                    } else {
                        filterOut(e);
                        continue;
                    }
                }

                // finally the real denoising starts here
                angleHist.clearHist();
                nnbRange.compute(x, y, ssx, ssy);
                outerloop:
                for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                    final int[] col = timestampImage[xx];
                    final byte[] polCol = polImage[xx];
                    for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                        if (fhp && xx == x && yy == y) {
                            continue; // like BAF, don't correlate with ourself
                        }
                        final int lastT = col[yy];
                        final int deltaT = (ts - lastT); // note deltaT will be very negative for DEFAULT_TIMESTAMP because of overflow
                        final int dx = xx - x, dy = yy - y; // delta x and y
                        if (deltaT < tauUs && lastT != DEFAULT_TIMESTAMP) { // ignore correlations for DEFAULT_TIMESTAMP that are neighbors which never got event so far
                            PolarityEvent pe = (PolarityEvent) e;
                            if (pe.getPolaritySignum() == polCol[yy]) {
                                final float age = useAge ? age(deltaT) : 1;
                                angleHist.addToHist(dx, dy, age);
                            }
                        }
                    }
                }
                boolean isSignal = angleHist.isSignalEvent();
//                log.info(angleHist.toString());
                if (isSignal) {
                    filterIn(e);
                } else {
                    filterOut(e);
                }
                storeTimestampPolarity(x, y, e);
            } // event packet loop
        } catch (ArrayIndexOutOfBoundsException e) {
            log.warning("Caught " + e + " probably from changing sigmaDistPixels during iiteration over events");
        }

        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    // </editor-fold>
    /**
     * @return the minCorrelation
     */
    public float getMinCorrelation() {
        return minCorrelation;
    }

    /**
     * @param minCorrelation the minCorrelation to set
     */
    public void setMinCorrelation(float minCorrelation) {
        float old = this.minCorrelation;
        if (minCorrelation > getNumNeighbors()) {
            minCorrelation = getNumNeighbors();
        }
        this.minCorrelation = minCorrelation;
        putFloat("minCorrelation", minCorrelation);
        getSupport().firePropertyChange("minCorrelation", old, this.minCorrelation);
    }

    /**
     * @return the nearbyAnglesIncluded
     */
    public boolean isNearbyAnglesIncluded() {
        return nearbyAnglesIncluded;
    }

    /**
     * @param nearbyAnglesIncluded the nearbyAnglesIncluded to set
     */
    public void setNearbyAnglesIncluded(boolean nearbyAnglesIncluded) {
        this.nearbyAnglesIncluded = nearbyAnglesIncluded;
        putBoolean("nearbyAnglesIncluded", nearbyAnglesIncluded);
    }

    /**
     * @return the useAge
     */
    public boolean isUseAge() {
        return useAge;
    }

    /**
     * @param useAge the useAge to set
     */
    public void setUseAge(boolean useAge) {
        this.useAge = useAge;
        putBoolean("useAge", useAge);
    }

    /**
     * @return the drawAngleHist
     */
    public boolean isDrawAngleHist() {
        return drawAngleHist;
    }

    /**
     * @param drawAngleHist the drawAngleHist to set
     */
    public void setDrawAngleHist(boolean drawAngleHist) {
        this.drawAngleHist = drawAngleHist;
        putBoolean("drawAngleHist", drawAngleHist);
    }

    /**
     * @return the expAgeDecay
     */
    public boolean isExpAgeDecay() {
        return expAgeDecay;
    }

    /**
     * @param expAgeDecay the expAgeDecay to set
     */
    public void setExpAgeDecay(boolean expAgeDecay) {
        this.expAgeDecay = expAgeDecay;
        putBoolean("expAgeDecay", expAgeDecay);
    }

    /**
     * @return the useEntropy
     */
    public boolean isUseEntropy() {
        return useEntropy;
    }

    /**
     * @param useEntropy the useEntropy to set
     */
    public void setUseEntropy(boolean useEntropy) {
        this.useEntropy = useEntropy;
        putBoolean("useEntropy", useEntropy);
    }

    /**
     * @return the maxEntropy
     */
    public float getMaxEntropy() {
        return maxEntropy;
    }

    /**
     * @param maxEntropy the maxEntropy to set
     */
    public void setMaxEntropy(float maxEntropy) {
        final float maxVal = (float) (Math.log(angleHist.numBins) / Math.log(2));
        if (maxEntropy > maxVal) {
            maxEntropy = maxVal;
        }
        this.maxEntropy = maxEntropy;
        putFloat("maxEntropy", maxEntropy);
    }

}
