package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.awt.geom.Point2D;
import com.jmatio.types.MLDouble;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.sf.jaer.eventio.AEDataFile;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.TobiLogger;
import org.apache.commons.math3.stat.Frequency;

/**
 * This class computes and prints several objects of interest when evaluating
 * optical flow performance: The average angular error, (relative) average end-
 * point error, processing time, and event density (fraction of events that
 * successfully passed the filter). It also computes global motion averages
 * (translation, rotation, expansion and speed). For global translation,
 * rotation and expansion we average over all the individual translation
 * (expansion, rotation) values of one packet. We compute the Standard Deviation
 * (not Standard Error) because we are interested in how much the individual
 * translation values deviate from the mean to be able to insure that our
 * averaging was justified. However, an even better indicator would be the
 * robust measure MAD (Median Absolute Deviation), because the Standard
 * Deviation is heavily influenced by outliers. The Standard Error (Standard
 * Deviation divided by Sqrt(N)) indicates how far the sample estimate of the
 * mean is away from the true population mean; it will converge to zero for
 * large sample sizes. The SD is a measure of how much we can expect the sample
 * individuals to differ from the sample mean; it will converge towards the
 * population standard deviation for larger sample sizes.
 *
 * @author rbodo
 */
public class MotionFlowStatistics {

    public GlobalMotion globalMotion;
    public AngularError angularError;
    public EndpointErrorAbs endpointErrorAbs;
    public EndpointErrorRel endpointErrorRel;
    public ProcessingTime processingTime;
    public EventDensity eventDensity;
    TobiLogger globalMotionVectorLogger;
    private int windowSize = Measurand.WINDOW_SIZE;
    /**
     * Used for logging accuracy stats
     */
    TobiLogger accuracyStatisticsLogger = null;

    // For logging.
    private static String filename;
    private final DateFormat DATE_FORMAT;
    PrintStream logStream;

    private float timeslice;

    // Number of packets to skip before logging.
    private int warmupCounter;

    private final String filterClassName;

    private boolean measureProcessingTime = false;

    private boolean measureAccuracy = false;

    private boolean measureGlobalMotion = true;

    private List<Double> globalFlows = new ArrayList<>();

    /**
     * Definitions of outliers, according to KITTI standard as used in
     * EV-FlowNet, since EV-FlowNet had 45Hz frames (KITTI had only 10Hz FPS)
     */
    public final float OUTLIER_ABS_PPS = 3 * 45, OUTLIER_RELATIVE_PERCENT = 5;
    protected int sampleCount = 0;
    private int outlierCount = 0;
    private boolean wasAbsOutlier = false; // flag 

    public MotionFlowStatistics(String filterClassName, int sX, int sY, int windowSize) {
        DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        reset(sX, sY, windowSize);
//        globalMotionVectorLogger = new TobiLogger("logfiles/" + "GlobalMotion" + filterClassName ,  "Global Motion vector for every generated slice");
//        globalMotionVectorLogger.setNanotimeEnabled(false);
//        globalMotionVectorLogger.setEnabled(true);
        this.filterClassName = filterClassName;
    }

    protected final void reset(int sX, int sY, int windowSize) {
        sampleCount = 0;
        outlierCount = 0;
        wasAbsOutlier = false;
        globalMotion = new GlobalMotion(sX, sY, windowSize);
        angularError = new AngularError();
        endpointErrorAbs = new EndpointErrorAbs();
        endpointErrorRel = new EndpointErrorRel();
        processingTime = new ProcessingTime();
        eventDensity = new EventDensity();
        warmupCounter = 8;
        if (globalMotionVectorLogger != null) {
            // globalMotionVectorLogger.setEnabled(false);            
            // globalMotionVectorLogger = new TobiLogger("logfiles/" + "GlobalMotion" + filterClassName ,  "Global Motion vector for every generated slice");
            // globalMotionVectorLogger.setNanotimeEnabled(false);
            // globalMotionVectorLogger.setEnabled(true);
        }
        ArrayList list = new ArrayList();
        Double[] globalFlowArray = new Double[globalFlows.size()];
        globalFlowArray = globalFlows.toArray(globalFlowArray);
        globalFlows.clear();
        list.add(new MLDouble("global_flow", globalFlowArray, 3));
        Date d = new Date();
        String fn = "flowExport" + "_" + AEDataFile.DATE_FORMAT.format(d) + "_" + System.currentTimeMillis() + "_" + filterClassName + ".mat";
//        try {          
//            File logDir = new File("logfiles");
//
//            // if the directory does not exist, create it
//            if (!logDir.exists()) {
//                logDir.mkdir();
//            }
//            MatFileWriter matFileWriter = new MatFileWriter("logfiles/" + fn, list);
//        } catch (IOException ex) {
//            log.log(Level.SEVERE, null, ex);
//        }
        setWindowSize(windowSize);
    }

    public final void setMeasureAccuracy(boolean measureAccuracy) {
        this.measureAccuracy = measureAccuracy;
    }

    public final void setMeasureProcessingTime(boolean measureProcessingTime) {
        this.measureProcessingTime = measureProcessingTime;
    }

    public final void setMeasureGlobalMotion(boolean measureGlobalMotion) {
        this.measureGlobalMotion = measureGlobalMotion;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
        globalMotion.setWindowSize(windowSize);
        angularError.setWindowSize(windowSize);
        endpointErrorAbs.setWindowSize(windowSize);
        endpointErrorRel.setWindowSize(windowSize);
        processingTime.setWindowSize(windowSize);
    }

    public int getWindowSize() {
        return windowSize;
    }

    public float getOutlierPercentage() {
        if (sampleCount == 0) {
            return Float.NaN;
        }
        return 100f * (float) (outlierCount) / sampleCount;
    }

    /**
     * Updates the statistics given a measurement and ground truth
     *
     * @param vx measured flow in x direction in pixels per second
     * @param vy etc
     * @param v speed in pixels per second
     * @param vxGT
     * @param vyGT
     * @param vGT GT speed in pixels per second
     */
    public void update(float vx, float vy, float v, float vxGT, float vyGT, float vGT) {
        if (warmupCounter > 0) {
            warmupCounter--;
            return;
        }
        if (Float.isNaN(vxGT) || Float.isNaN(vyGT) || Float.isNaN(vGT)) {
            return;
        }
        sampleCount++;
        angularError.update(vx, vy, v, vxGT, vyGT, vGT);
        endpointErrorAbs.update(vx, vy, v, vxGT, vyGT, vGT);
        endpointErrorRel.update(vx, vy, v, vxGT, vyGT, vGT); // make sure this runs second for accurate outlier count
    }

    /**
     * Updates statistics about number of input and output events and measures
     * processing time
     *
     * @param countIn
     * @param countOut
     * @param currentTs
     */
    public void updatePacket(int countIn, int countOut, int currentTs) {
        eventDensity.update(countIn, countOut);
        if (measureProcessingTime) {
            processingTime.update();
        }
        if (measureGlobalMotion) {
            globalMotion.bufferMean(currentTs);
        }
    }

    @Override
    public String toString() {
        return String.format("Motion Flow Statistics Summary: (windowSize=%,d N=%,d samples) %nOutliers (>%.0f px/s): %.1f%n", windowSize, globalMotion.globalSpeed.getN(), OUTLIER_ABS_PPS, getOutlierPercentage())
                + eventDensity.toString() + globalMotion.toString()
                + processingTime.toString() + angularError.toString()
                + endpointErrorAbs.toString() + endpointErrorRel.toString();
    }

    /**
     * Returns object that reports global motion statistics
     *
     * @return the globalMotion
     */
    public GlobalMotion getGlobalMotion() {
        return globalMotion;
    }

    /**
     * Records number of events in packet before and after filtering, i.e. what
     * fraction of input events cause optical flow events. Prepending a
     * refractory filter to reduce input events will not be accounted for by
     * this object
     */
    public class EventDensity {

        // Number of events in packet before and after filtering.
        private long packetIn, packetOut, totalIn, totalOut, nPackets;

        /**
         * Returns density of last packet
         *
         * @return
         */
        public float getPacketDensity() {
            return packetIn == 0 ? 0 : (float) 100 * packetOut / packetIn;
        }

        /**
         * Returns overall density over all packets since reset
         *
         * @return density, in percent 0-100
         */
        public float getTotalDensity() {
            return totalIn == 0 ? 0 : (float) 100 * totalOut / totalIn;
        }

        /**
         * Updates
         *
         * @param pIn input packet
         * @param pOut output packet
         */
        public void update(int pIn, int pOut) {
            packetIn = pIn;
            packetOut = pOut;
            totalIn += pIn;
            totalOut += pOut;
            nPackets++;
        }

        public void reset() {
            packetIn = 0;
            packetOut = 0;
            totalIn = 0;
            totalOut = 0;
            nPackets = 0;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "%1$s: %2$4.2f%% %n",
                    getClass().getSimpleName(), getTotalDensity());
        }

        public String graphicsString(String header, String units) {
            return String.format("%s mean: %4.2f %s", header, getTotalDensity(), units);
        }

    }

    /**
     * Tracks global motion
     *
     */
    public class GlobalMotion {

        /**
         * Means and standard deviations of flow.
         *
         * <p>
         * meanGlobalTrans is the average translational flow. </p>
         *
         * <p>
         * meanGlobalSpeed is the average magnitude of the flow vector. </p>
         *
         * <p>
         * meanGlobalExpansion is the average flow projected onto radii from
         * center, and meanGlobalRotation is the average projected onto
         * circumferences around center.</p>
         *
         * <p>
         * covGlobalSpeed is the coefficient of variation (sigma/mean) of the
         * speeds. </p>
         */
        public float meanGlobalVx, sdGlobalVx, meanGlobalVy, sdGlobalVy, meanGlobalRotation, meanGlobalTrans, sdGlobalTrans,
                sdGlobalRotation, meanGlobalExpansion, sdGlobalExpansion, meanGlobalSpeed, sdGlobalSpeed, covGlobalSpeed;
        private final Measurand globalVx, globalVy, globalRotation, globalExpansion, globalSpeed;
        private Point2D.Float flowVelocityPps = new Point2D.Float();
        private int rx, ry;
        private int subSizeX, subSizeY;
        private final Measurand rollDps;
        private final Measurand pitchDps;
        private final Measurand yawDps;
        public Frequency globalMotionAngleFrequency;
        public float ANGLE_BIN_DEGREES = 10, ANGLE_HISTOGRAM_MAX_RADIUS_PIXELS = 100;

        private int windowSize = Measurand.WINDOW_SIZE;

        /**
         * makes a new instance
         *
         * @param sX x resolution, could be subsampled
         * @param sY
         * @param windowLength window for statistics, in events
         */
        GlobalMotion(int sX, int sY, int windowLength) {
            subSizeX = sX;
            subSizeY = sY;
            this.windowSize = windowLength;
            globalVx = new Measurand(windowLength);
            globalVy = new Measurand(windowLength);
            globalRotation = new Measurand(windowLength);
            globalExpansion = new Measurand(windowLength);
            globalSpeed = new Measurand(windowLength);
            rollDps = new Measurand(windowLength);
            pitchDps = new Measurand(windowLength);
            yawDps = new Measurand(windowLength);
            globalMotionAngleFrequency = new Frequency();
        }

        public void setWindowSize(int windowSize) {
            this.windowSize = windowSize;
            globalVy.setWindowSize(windowSize);
            globalVy.setWindowSize(windowSize);
            globalRotation.setWindowSize(windowSize);
            globalExpansion.setWindowSize(windowSize);
            globalSpeed.setWindowSize(windowSize);
        }

        public int getWindowSize() {
            return this.windowSize;
        }

        /**
         * Clears statistics
         *
         * @param sX subsampling x dimension size
         * @param sY
         */
        void reset(int sX, int sY) {
            subSizeX = sX;
            subSizeY = sY;
            globalVy.clear();
            globalVy.clear();
            globalRotation.clear();
            globalExpansion.clear();
            globalSpeed.clear();
            globalMotionAngleFrequency.clear();
        }

        /**
         * Updates statistics, to be computed on each valid flow result
         *
         * @param vx flow x
         * @param vy flow y
         * @param v speed
         * @param x coordinate x horizontal (for radial and circular flow
         * computations)
         * @param y coordinate y vertical
         */
        public void update(float vx, float vy, float v, int x, int y) {
            // Translation
            if (v == 0) {
                return;
            }
            globalVx.addValue(vx);
            globalVy.addValue(vy);
            final float speed = (float) Math.sqrt(vx * vx + vy * vy);
            globalSpeed.addValue(speed);

            // Rotation
            // <editor-fold defaultstate="collapsed" desc="Comment">
            /**
             * Each event implies a certain rotational motion. The larger the
             * radius, the smaller the effect of a given local motion vector on
             * rotation. The contribution to rotational motion is computed by
             * dot product between tangential vector (which is closely related
             * to radial vector) and local motion vector. If (vx,vy) is the
             * local motion vector, (rx,ry) the radial vector (from center of
             * rotation), and (tx,ty) the tangential *unit* vector, then the
             * tangential velocity is computed as v.t=rx*tx+ry*ty. The
             * tangential vector is given by dual of radial vector: tx=-ry/r,
             * ty=rx/r, where r is length of radial vector. Thus tangential
             * comtribution is given by v.t/r=(-vx*ry+vy*rx)/r^2. (Tobi)
             */
            // </editor-fold>
            rx = x - subSizeX / 2;
            ry = y - subSizeY / 2;
            if (rx == 0 && ry == 0) {
                return; // Don't add singular event at origin.
            }
            globalRotation.addValue((float) (180 / Math.PI) * (vy * rx - vx * ry) / (rx * rx + ry * ry));

            // Expansion, project onto radius
            // <editor-fold defaultstate="collapsed" desc="Comment">
            /*
             * Each event implies a certain expansion contribution.
             * Velocity components in the radial direction are weighted by radius;
             * events that are close to the origin contribute more to expansion
             * metric than events that are near periphery. The contribution to
             * expansion is computed by dot product between radial vector
             * and local motion vector.
             * If vx,vy is the local motion vector and rx,ry the radial vector
             * (from center of rotation) then the radial velocity is computed
             * as v.r/r.r=(vx*rx+vy*ry)/(rx*rx+ry*ry), where r is radial vector.
             * Thus in scalar units, each motion event contributes v/r to the metric.
             * This metric is exactly 1/Tcoll with Tcoll=time to collision.
             * (Tobi)
             */
            // </editor-fold>
            if (rx > -2 && rx < 2 && ry > -2 && ry < 2) {
                return; // Don't add singular event at origin.
            }
            final float exProj = (vx * rx + vy * ry);
            globalExpansion.addValue(exProj / (rx * rx + ry * ry));

            double angleDeg = 180 / Math.PI * Math.atan2(vy, vx);
            int angleBin = (int) Math.round(angleDeg / ANGLE_BIN_DEGREES);
            if (globalMotionAngleFrequency.getSumFreq() >= windowSize) {
                globalMotionAngleFrequency.clear();
            } else {
                globalMotionAngleFrequency.addValue(angleBin);
            }

        }

        public void drawAngleHistogram(GL2 gl, float fullScalePixels) {
            Iterator it = globalMotionAngleFrequency.valuesIterator();
            gl.glBegin(GL.GL_LINES);
            while (it.hasNext()) {
                Long k = (long) (it.next());
                double radius = fullScalePixels * globalMotionAngleFrequency.getPct(k);
                if (radius == 0) {
                    continue;
                }
                double angleRad = (Math.PI / 180) * ANGLE_BIN_DEGREES * k;
                double x = (radius * Math.cos(angleRad));
                double y = (radius * Math.sin(angleRad));
                gl.glColor3f(0, 0, 0);
                gl.glVertex2d(1, -1);
                gl.glVertex2d(x + 1, y - 1);
                gl.glColor3f(1, 1, 1);
                gl.glVertex2d(0, 0);
                gl.glVertex2d(x, y);

            }
            gl.glEnd();
        }

        /**
         * To be applied at each of packet, updates values of statistics.
         *
         * @param currentTs end of packet timestamp in us
         */
        void bufferMean(int currentTs) {
            meanGlobalVx = (float) globalVx.getMean();
            sdGlobalVx = (float) globalVx.getStandardDeviation();
            meanGlobalVy = (float) globalVy.getMean();
            sdGlobalVy = (float) globalVy.getStandardDeviation();
            meanGlobalTrans = (float) Math.sqrt(meanGlobalVx * meanGlobalVx + meanGlobalVy * meanGlobalVy);
            sdGlobalTrans = (float) Math.sqrt(sdGlobalVx * sdGlobalVx + sdGlobalVy * sdGlobalVy);
            meanGlobalRotation = (float) globalRotation.getMean();
            sdGlobalRotation = (float) globalRotation.getStandardDeviation();
            meanGlobalExpansion = (float) globalExpansion.getMean();
            sdGlobalExpansion = (float) globalExpansion.getStandardDeviation();
            meanGlobalSpeed = (float) globalSpeed.getMean();
            sdGlobalSpeed = (float) globalSpeed.getStandardDeviation();
            covGlobalSpeed = !Float.isNaN(meanGlobalSpeed) ? sdGlobalSpeed / meanGlobalSpeed : Float.NaN;

            globalFlows.add((double) currentTs);
            globalFlows.add((double) meanGlobalVx);
            globalFlows.add((double) meanGlobalVy);

            //globalVxList.add(new MLDouble("vy", meanGlobalVy));
            if (globalMotionVectorLogger != null && globalMotionVectorLogger.isEnabled()) {
                String s = String.format("%d %.3g %.3g", currentTs, meanGlobalVx, meanGlobalVy);
                globalMotionVectorLogger.log(s);
            }
            if (accuracyStatisticsLogger != null && accuracyStatisticsLogger.isEnabled()) {
                String s = String.format("%d %.3g %.3g %.3g %d", currentTs, endpointErrorAbs.getMean(), endpointErrorRel.getMean(), angularError.getMean(), sampleCount);
                accuracyStatisticsLogger.log(s);
            }
            // Call resets here because global motion should in general not
            // be averaged over more than one packet.
            // Tobi changed so that stats do not depend on packet length, so that outlier rejection can work robustly
//            globalVx.clear();
//            globalVy.clear();
//            globalRotation.clear();
//            globalExpansion.clear();
//            globalSpeed.clear();
        }

        @Override
        public String toString() {
            return !measureGlobalMotion ? "Global flow not measured. Select measureGlobalMotion.\n"
                    : String.format(Locale.ENGLISH, "Global velocity: "
                            + "[%1$4.2f, %2$4.2f] +/- [%3$4.2f, %4$4.2f] px/s, global rotation: "
                            + "%5$4.2f +/- %6$2.2f °/s %n", meanGlobalVx, meanGlobalVy, sdGlobalVx,
                            sdGlobalVy, meanGlobalRotation, sdGlobalRotation);
        }

        /**
         * @return the globalVx in pixels/second
         */
        public Measurand getGlobalVx() {
            return globalVx;
        }

        /**
         * @return the globalVy in pixels/second
         */
        public Measurand getGlobalVy() {
            return globalVy;
        }

        /**
         * @return the globalRotation, TODO in rad/sec?
         */
        public Measurand getGlobalRotation() {
            return globalRotation;
        }

        /**
         * @return the globalExpansion, TODO units ????
         */
        public Measurand getGlobalExpansion() {
            return globalExpansion;
        }

        /**
         * Computes and returns the mean speed; magnitude of flow vectors
         *
         * @return
         */
        public Measurand getGlobalSpeed() {
            return globalSpeed;
        }

        public Point2D.Float getGlobalFlowVelocityPps() {
            flowVelocityPps.setLocation(meanGlobalVx, meanGlobalVy);
            return flowVelocityPps;
        }

    }

    public class AngularError extends Measurand {

        private final Histogram histogram;
        private final int NUM_BINS = 20;
        private final int SIZE_BINS = 10;
        private final int START = 3;
        private final float X1 = 3;
        private final float X2 = 10;
        private final float X3 = 30;
        private float tmp;

        public AngularError() {
            histogram = new Histogram(START, NUM_BINS, SIZE_BINS);
        }

        @Override
        public void clear() {
            super.clear();
            histogram.reset();
        }

        // <editor-fold defaultstate="collapsed" desc="Comment">
        /**
         * Returns the angle in degrees between the observed optical flow and
         * ground truth. The case that either one or both v and vGT are zero is
         * unnatural because in principle every event should be the result of
         * motion (in this context). So we skip it by returning 181 (which is
         * large so that the event is still filtered out when
         * calculateAngularError() is called in the context of discarding
         * outliers during filterPacket). When updating PacketStatistics, we
         * detect 181 and skip updating the statistics.
         */
        // </editor-fold> 
        float calculateError(float vx, float vy, float v, float vxGT, float vyGT, float vGT) {
            if (v == 0 || vGT == 0) {
                return 181;
            }
            tmp = (vx * vxGT + vy * vyGT) / (v * vGT);
            if (tmp > 1) {
                return 0; // Can happen due to roundoff error.
            }
            if (tmp < -1) {
                return 180;
            }
            return (float) (Math.acos(tmp) * 180 / Math.PI);
        }

        // Calculates the angular error of a single event and adds it to the sample.
        void update(float vx, float vy, float v, float vxGT, float vyGT, float vGT) {
            tmp = calculateError(vx, vy, v, vxGT, vyGT, vGT);
            if (tmp == 181) {
                return;
            }
            addValue(tmp);
            histogram.update(tmp);
        }

        @Override
        public String toString() {
            return !measureAccuracy ? "Angular error not measured. Select measureAccuracy.\n"
                    : String.format(Locale.ENGLISH, "%1$s: %2$4.2f +/- %3$5.2f °, %4$s, "
                            + "percentage above %5$2f °: %6$4.2f%%, above %7$2f °: %8$4.2f%%, above %9$2f °: %10$4.2f%% %n",
                            getClass().getSimpleName(), getMean(), getStandardDeviation(), histogram.toString(),
                            X1, histogram.getPercentageAboveX(X1),
                            X2, histogram.getPercentageAboveX(X2),
                            X3, histogram.getPercentageAboveX(X3));
        }
    }

    public class EndpointErrorAbs extends Measurand {

        private final Histogram histogram;
        private final int NUM_BINS = 20;
        private final int SIZE_BINS = 10;
        private final int START = 1;
        private final float X1 = 1;
        private final float X2 = 10;
        private final float X3 = 20;

        EndpointErrorAbs() {
            histogram = new Histogram(START, NUM_BINS, SIZE_BINS);
        }

        @Override
        public void clear() {
            super.clear();
            histogram.reset();
        }

        void update(float vx, float vy, float v, float vxGT, float vyGT, float vGT) {
            if (v == 0 || vGT == 0) {
                return;
            }
            final float dx = vx - vxGT, dy = vy - vyGT;
            float tmp = (float) Math.sqrt(dx * dx + dy * dy);
//            System.out.println(String.format("vx=%8.1f vy=%8.1f vxGT=%8.1f vyGT=%8.1f dx=%8.1f dy=%8.1f diff=%.1f", vx, vy, vxGT, vyGT, dx, dy, tmp));
//            tmp = (float) (Math.abs(vx - vxGT) + Math.abs(vy - vyGT));
//            if(getN()==0)System.out.println("EE pps: ");
//            System.out.print(String.format("%6.2f ",tmp));
//            if(getN()%20==0)System.out.println("");
            addValue(tmp);
            histogram.update(tmp);
            if (Math.abs(tmp) > OUTLIER_ABS_PPS) {
                outlierCount++;
                wasAbsOutlier = true;
            } else {
                wasAbsOutlier = false;
            }
        }

        @Override
        public String toString() {
            return !measureAccuracy ? "Endpoint error not measured. Select measureAccuracy.\n"
                    : String.format(Locale.ENGLISH, "%1$s: %2$4.2f +/- %3$5.2f pixels/s, "
                            + "%4$s, percentage above %5$4.2f pixels/s: %6$4.2f%%, above %7$4.2f "
                            + "pixels/s: %8$4.2f%%, above %9$4.2f pixels/s: %10$4.2f%% %n",
                            getClass().getSimpleName(),
                            getMean(), getStandardDeviation(), histogram.toString(),
                            X1, histogram.getPercentageAboveX(X1),
                            X2, histogram.getPercentageAboveX(X2),
                            X3, histogram.getPercentageAboveX(X3));
        }
    }

    public class EndpointErrorRel extends Measurand {

        private final Histogram histogram;
        private final int NUM_BINS = 20;
        private final int SIZE_BINS = 100;
        private final int START = 2;

        EndpointErrorRel() {
            histogram = new Histogram(START, NUM_BINS, SIZE_BINS);
        }

        @Override
        public void clear() {
            super.clear();
            histogram.reset();
        }

        void update(float vx, float vy, float v, float vxGT, float vyGT, float vGT) {
            if (v == 0 || vGT == 0) {
                return;
            }
            float tmp = (float) Math.sqrt((vx - vxGT) * (vx - vxGT) + (vy - vyGT) * (vy - vyGT)) * 100 / vGT;
//            tmp = (float) (Math.abs(vx - vxGT) + Math.abs(vy - vyGT)) * 100 / vGT;
            addValue(tmp);
            histogram.update(tmp);
            // don't use percentag3e outliers since EV-Flownet did not use them
//            if (Math.abs(tmp) > OUTLIER_RELATIVE_PERCENT) {
//                if (!wasAbsOutlier) {
//                    outlierCount++; // don't double count outliers
//                }
//            }
            wasAbsOutlier = false; // TODO depends on absolute outlier check first
        }

        @Override
        public String toString() {
            return !measureAccuracy ? "Relative endpoint error not measured. Select measureAccuracy.\n"
                    : String.format(Locale.ENGLISH, "%1$s: %2$4.2f +/- %3$5.2f%%, %4$s %n",
                            getClass().getSimpleName(), getMean(), getStandardDeviation(), histogram.toString());
        }
    }

    /**
     * Tracks processing time using System.nanoTime
     */
    public class ProcessingTime extends Measurand {

        // Processing time in microseconds averaged over packet.
        private float meanProcessingTimePacket;

        // Start time of packet filtering.
        long startTime;

        // Number of bins to cluster packets according to input density.
        // keps = 1000 events per second, epp = events per packet.
        private final int kepsBins = 10;
        private final int kepsIncr = 150;
        private final int kepsInit = 50;
        private final Measurand[] processingTimeEPS;

        private int i;

        protected ProcessingTime() {
            processingTimeEPS = new Measurand[kepsBins];
            for (i = 0; i < kepsBins; i++) {
                processingTimeEPS[i] = new Measurand();
            }
            startTime = System.nanoTime();
        }

        @Override
        public void clear() {
            super.clear();
            for (i = 0; i < kepsBins; i++) {
                processingTimeEPS[i].clear();
            }
            meanProcessingTimePacket = 0;
        }

        void update() {
            if (warmupCounter > 0) {
                warmupCounter--;
                return;
            }
            meanProcessingTimePacket = (System.nanoTime() - startTime) * 1e-3f / (eventDensity.packetIn > 0 ? eventDensity.packetIn : 1);
            addValue(meanProcessingTimePacket);
            for (i = 0; i < kepsBins; i++) {
                if (eventDensity.packetIn < (kepsIncr * i + kepsInit) * timeslice) {
                    processingTimeEPS[i].addValue(meanProcessingTimePacket);
                    break;
                }
            }
        }

        // Opens the file and prints the header to it.
        void openLog(final String loggingFolder) {
            try {
                filename = loggingFolder + "/ProcessingTime_" + filterClassName
                        + DATE_FORMAT.format(new Date()) + ".txt";
                logStream = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(new File(filename))));
                log.log(Level.INFO, "Created motion flow logging file with "
                        + "processing time statistics at {0}", filename);
                logStream.println("Processing time statistics of motion flow "
                        + "calculation, averaged over event packets.");
                logStream.println("Date: " + new Date());
                logStream.println("Filter used: " + filterClassName);
                logStream.println();
                logStream.println("timestamp [us] | processing time [us]");
            } catch (FileNotFoundException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }

        // Closes the file and nulls the stream.
        void closeLog(final String loggingFolder, int searchDistance) {
            if (logStream != null) {
                logStream.flush();
                logStream.close();
                logStream = null;
                log.log(Level.INFO, "Closed log data file {0}", filename);
            }
            try {
                filename = loggingFolder + "/SummaryProcessingTime_"
                        + filterClassName + DATE_FORMAT.format(new Date()) + ".txt";
                logStream = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(new File(filename))));
                logStream.println("Summary of processing time statistics.");
                logStream.println("Date: " + new Date());
                logStream.println("Filter used: " + filterClassName);
                logStream.println("Search distance: " + searchDistance);
                logStream.println("Time slice [ms]: " + timeslice);
                logStream.println();
                for (i = 0; i < kepsBins; i++) {
                    if (processingTimeEPS[i].getN() > 0) {
                        logStream.println(processingTimeEPS[i] + " @ "
                                + (int) ((kepsIncr * i + kepsInit) * timeslice) + " events/packet");
                    }
                }
            } catch (FileNotFoundException ex) {
                log.log(Level.SEVERE, null, ex);
            }
            if (logStream != null) {
                logStream.flush();
                logStream.close();
                logStream = null;
                log.log(Level.INFO, "Closed log data file {0}", filename);
            }
        }

        void log(int ts, int tsFirst, int tsLast, int countIn, int countOut) {
            timeslice = (tsLast - tsFirst) * 1e-3f;
            if (timeslice <= 0) {
                return;
            }
            update();
            if (logStream != null) {
                logStream.printf(
                        Locale.ENGLISH, "%1$12d %2$11.2f %n", ts, meanProcessingTimePacket);
            }
        }

        @Override
        public String toString() {
            return !measureProcessingTime ? "Processing time not measured. Select measureProcessingTime\n"
                    : String.format(Locale.ENGLISH, "%1$s: %2$4.2f +/- %3$5.2f us/event %n",
                            getClass().getSimpleName(), getMean(), getStandardDeviation());
        }
    }

    /**
     * @return the sampleCount
     */
    public int getSampleCount() {
        return sampleCount;
    }
}
