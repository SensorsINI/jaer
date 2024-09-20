/*
 * RectangularClusterTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 */
package net.sf.jaer.eventprocessing.tracking;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.logging.Level;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Desktop;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * This complex and highly configurable tracker tracks blobs of events using a
 * rectangular hypothesis about the object shape. Many parameters constrain the
 * hypothesese in various ways, including perspective projection, fixed aspect
 * ratio, variable size and aspect ratio, "mixing factor" that determines how
 * much each event moves a cluster, etc.
 *
 * @author tobi
 */
@Description("Tracks multiple moving compact (not linear) objects")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class RectangularClusterTracker extends EventFilter2D
        implements Observer, ClusterTrackerInterface, FrameAnnotater, MouseListener/* , PreferenceChangeListener */ {
    // TODO split out the Cluster object as it's own class.
    // TODO delegate worker object to update the clusters (RectangularClusterTrackerDelegate)
    // public TelluridePatchExtractor TelluridePatchExtractor = new TelluridePatchExtractor();

    EngineeringFormat fmt = new EngineeringFormat();

    /**
     * maximum and minimum allowed dynamic aspect ratio when dynamic
     * instantaneousAngle is disabled.
     */
    public static final float ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED = 2.5f, ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED = 0.5f;

    /**
     * maximum and minimum allowed dynamic aspect ratio when dynamic
     * instantaneousAngle is enabled; then min aspect ratio is set to 1 to make
     * instantaneousAngle point along an edge in the scene.
     */
    public static final float ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED = 1, ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED = 0.5f;

    // to updateShape rendering of cluster velocityPPT vector, velocityPPT is
    // in pixels/tick=pixels/us so this gives 1 screen pixel per 1 pix/s actual vel
    private final float VELOCITY_VECTOR_SCALING = 1e6f;

    // average velocities of all clusters mixed with this factor to produce
    // this "prior" on initial cluster velocityPPT
    private final float AVERAGE_VELOCITY_MIXING_FACTOR = 0.001f;

    protected static final float FULL_BRIGHTNESS_LIFETIME = 100000;

    /**
     * amount each event moves COM of cluster towards itself.
     */
    protected float mixingFactor = getFloat("mixingFactor", 0.05f);
    private boolean useEllipticalClusters = getBoolean("useEllipticalClusters", false);
    private boolean updateClustersOnlyFromEventsNearEdge = getBoolean("updateClustersOnlyFromEventsNearEdge", false);
    private float ellipticalClusterEdgeThickness = getFloat("ellipticalClusterEdgeThickness", .2f);
    private float surround = getFloat("surround", 2f);
    private boolean dynamicSizeEnabled = getBoolean("dynamicSizeEnabled", false);
    private boolean dynamicAspectRatioEnabled = getBoolean("dynamicAspectRatioEnabled", false);
    private boolean dynamicAngleEnabled = getBoolean("dynamicAngleEnabled", false);
    private boolean pathsEnabled = getBoolean("pathsEnabled", true);
    private int pathLength = getInt("pathLength", 100);
    private boolean colorClustersDifferentlyEnabled = getBoolean("colorClustersDifferentlyEnabled", false);
    private boolean useOnePolarityOnlyEnabled = getBoolean("useOnePolarityOnlyEnabled", false);
    private boolean useOffPolarityOnlyEnabled = getBoolean("useOffPolarityOnlyEnabled", false);
    private float aspectRatio = getFloat("aspectRatio", 1f);
    private float clusterSize = getFloat("clusterSize", 0.1f);
    protected boolean growMergedSizeEnabled = getBoolean("growMergedSizeEnabled", false);
    protected boolean useVelocity = getBoolean("useVelocity", true); // enabling this enables both computation and
    // rendering of cluster velocities
    private boolean logDataEnabled = false;
    protected boolean showAllClusters = getBoolean("showAllClusters", false);
    protected boolean useNearestCluster = getBoolean("useNearestCluster", true); // use the nearest cluster to an
    // event, not the first containing
    // it
    protected float predictiveVelocityFactor = getFloat("predictiveVelocityFactor", 1);// making this M=10, for example,
    // will cause cluster to
    // substantially lead the
    // events, then slow down, speed
    // up, etc.
    protected boolean highwayPerspectiveEnabled = getBoolean("highwayPerspectiveEnabled", false);

    protected int thresholdMassForVisibleCluster = getInt("thresholdMassForVisibleCluster", 10);
    protected int clusterMassDecayTauUs = getInt("clusterMassDecayTauUs", 10000);

    private boolean useEventRatePerPixelForVisibilty = getBoolean("useEventRatePerPixelForVisibilty", true);
    private float eventRatePerPixelLowpassTauS = getFloat("eventRatePerPixelLowpassTauS", 0.1f);
    private float thresholdEventRatePerPixelForVisibleClusterHz = getFloat("thresholdEventRatePerPixelForVisibleClusterHz", 10f);

    protected float thresholdVelocityForVisibleCluster = getFloat("thresholdVelocityForVisibleCluster", 0);
    protected boolean enableClusterExitPurging = getBoolean("enableClusterExitPurging", true);
    private boolean purgeIfClusterOverlapsBorder = getBoolean("purgeIfClusterOverlapsBorder", true);
    protected float velAngDiffDegToNotMerge = getFloat("velAngDiffDegToNotMerge", 60);
    protected boolean showClusterNumber = getBoolean("showClusterNumber", false);
    protected boolean showClusterEps = getBoolean("showClusterEps", false);
    protected boolean showClusterEpsPerPx = getBoolean("showClusterEpsPerPx", false);
    private boolean showClusterRadius = getBoolean("showClusterRadius", false);
    protected boolean showClusterVelocity = getBoolean("showClusterVelocity", false);
    protected boolean showClusterVelocityVector = getBoolean("showClusterVelocityVector", false);
    private boolean showClusterMass = getBoolean("showClusterMass", false);
    private boolean showPaths = getBoolean("showPaths", true);
    protected float velocityVectorScaling = getFloat("velocityVectorScaling", 1);
    protected int loggingIntervalUs = getInt("loggingIntervalUs", 1000);
    private int logFrameNumber = 0;
    private boolean initializeVelocityToAverage = getBoolean("initializeVelocityToAverage", false);
    protected boolean filterEventsEnabled = getBoolean("filterEventsEnabled", false); // enables filtering events so
    // that output events only
    // belong to clustera and point
    // to the clusters.
    protected float velocityTauMs = getFloat("velocityTauMs", 100);
    protected float frictionTauMs = getFloat("frictionTauMs", Float.NaN);
    private int maxNumClusters = getInt("maxNumClusters", 10);
    private boolean surroundInhibitionEnabled = getBoolean("surroundInhibitionEnabled", false);
    private boolean dontMergeEver = getBoolean("dontMergeEver", false);
    private boolean angleFollowsVelocity = getBoolean("angleFollowsVelocity", false);
    public boolean smoothMove = getBoolean("smoothMove", false);
    private float smoothWeight = getFloat("smoothWeight", 100);
    private float smoothPosition = getFloat("smoothPosition", .001f);
    private float smoothIntegral = getFloat("smoothIntegral", .001f);
    private float surroundInhibitionCost = getFloat("surroundInhibitionCost", 1);

    /**
     * scaling can't make cluster bigger or smaller than this ratio to default
     * cluster size.
     */
    private float maxSizeScaleRatio = getFloat("maxSizeScaleRatio", 4);

    /**
     * The list of clusters (visible and invisible).
     */
    volatile protected LinkedList<Cluster> clusters = new LinkedList<>();

    /**
     * The list of visible clusters.
     */
    protected LinkedList<Cluster> visibleClusters = new LinkedList<>();

    protected LinkedList<Cluster> pruneList = new LinkedList<>();

    private int numVisibleClusters = 0;

    protected float defaultClusterRadius;

    private Point2D.Float averageVelocityPPT = new Point2D.Float();
    protected ClusterLogger clusterLogger = new ClusterLogger();

    private float initialAngle = 0;

    // for mouse selection of vanishing point
    private GLCanvas glCanvas;
    private ChipCanvas canvas;

    /**
     * The vanishing point for perspective object sizing
     */
    private Point2D vanishingPoint = null;

    {
        float x = getFloat("vanishingPoint.x", Float.NaN), y = getFloat("vanishingPoint.y", Float.NaN);
        if (Float.isNaN(x) || Float.isNaN(y)) {
            setVanishingPoint(null);
        } else {
            vanishingPoint = new Point2D.Float(x, y);
        }
    }

    /**
     * timestamp that is updated for each cluster that is updated
     */
    protected int lastTimestamp = 0;

    protected int clusterCounter = 0; // keeps track of absolute cluster number

    /**
     * Useful for subclasses.
     */
    protected Random random = new Random();

    protected FastClusterFinder fastClusterFinder = new FastClusterFinder();

    /**
     * Creates a new instance of RectangularClusterTracker.
     *
     * @param chip
     */
    public RectangularClusterTracker(AEChip chip) {
        super(chip);
        this.chip = chip;
        initFilter();
        chip.addObserver(this);
        addObserver(this); // to handle updates during packet
        setTooltips();
    }

    /**
     * Assigns tooltips; useful for filters that delegate methods from this
     * class
     */
    protected void setTooltips() {
        final String sizing = "2: Sizing", mov = "4: Movement", life = "3: Lifetime", disp = "Display", common = "1: Common",
                update = "5: Update", logg = "7: Logging", pi = "6: PI Controller", options = "8: Options";
        setPropertyTooltip(life, "enableClusterExitPurging", "enables rapid purging of clusters that hit edge of scene");
        setPropertyTooltip(life, "purgeIfClusterOverlapsBorder", "purge any cluster that overlaps edge (if false, center must go outside border)");

        setPropertyTooltip(common, "clusterMassDecayTauUs", "time constant of exponential decay of \"mass\" of cluster between events (us)");
        setPropertyTooltip(common, "thresholdMassForVisibleCluster",
                "Cluster needs this \"mass\" to be visible. Mass increments with each event and decays with e-folding time constant of clusterMassDecayTauUs. Use \"showAllClusters\" to diagnose fleeting clusters.");

        setPropertyTooltip(common, "useEventRatePerPixelForVisibilty", "select this option to use normalized event rate per pixel rather than mass for visibility and lifetime");
        setPropertyTooltip(common, "eventRatePerPixelLowpassTauS", "time constant of cluster event rate lowpass filter in seconds");
        setPropertyTooltip(common, "thresholdEventRatePerPixelForVisibleClusterHz", "clusters with average event rates in Hz per pixel above this are \"visible\"");

        setPropertyTooltip(life, "thresholdVelocityForVisibleCluster",
                "cluster must have at least this velocity in pixels/sec to become visible");
        setPropertyTooltip(life, "dontMergeEver", "never merge overlapping clusters");
        setPropertyTooltip(life, "surroundInhibitionEnabled",
                "Enabling this option causes events in the surround region to actively reduce the cluster mass, enabling tracking of only isolated features");
        setPropertyTooltip(life, "surroundInhibitionCost", "If above is checked: The negative weight of surrounding points");
        setPropertyTooltip(common, "mixingFactor",
                "how much cluster is moved towards an event, as a fraction of the distance from the cluster to the event");
        setPropertyTooltip(mov, "velocityPoints",
                "the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip(mov, "velocityTauMs",
                "lowpass filter time constant in ms for velocity updates; effectively limits acceleration");
        setPropertyTooltip(mov, "frictionTauMs",
                "velocities decay towards zero with this time constant to mimic friction; set to NaN to disable friction");
        setPropertyTooltip(mov, "pathsEnabled", "draw paths of clusters over some window");
        setPropertyTooltip(mov, "useVelocity", "uses measured cluster velocity to predict future position; vectors are scaled "
                + String.format("%.1f pix/pix/s", (VELOCITY_VECTOR_SCALING / AEConstants.TICK_DEFAULT_US) * 1e-6));
        setPropertyTooltip(mov, "useNearestCluster", "event goes to nearest cluster, not to first (usually oldest) cluster containing it");
        setPropertyTooltip(mov, "predictiveVelocityFactor", "how much cluster position leads position based on estimated velocity");
        setPropertyTooltip(mov, "initializeVelocityToAverage",
                "initializes cluster velocity to moving average of cluster velocities; otherwise initialized to zero");
        setPropertyTooltip(sizing, "surround", "the radius is expanded by this ratio to define events that pull radius of cluster");
        setPropertyTooltip(sizing, "maxSizeScaleRatio", "The maximum size scaling relative to defaultClusterRadius (larger by maxSizeScaleRatio, smaller by 1/maxSizeScaleRatio");
        setPropertyTooltip(common, "dynamicSizeEnabled", "size varies dynamically depending on cluster events");
        setPropertyTooltip(sizing, "dynamicAspectRatioEnabled", "aspect ratio of cluster depends on events");
        setPropertyTooltip(sizing, "dynamicAngleEnabled", "angle of cluster depends on events, otherwise angle is zero");
        setPropertyTooltip(sizing, "defaultClusterRadius", "default starting size of cluster in pixels");
        setPropertyTooltip(sizing, "aspectRatio", "default (or initial) aspect ratio, <1 is wide");
        setPropertyTooltip(common, "clusterSize", "size (starting) in fraction of chip max size");
        setPropertyTooltip(sizing, "highwayPerspectiveEnabled",
                "Cluster size depends on perspective location; mouse click defines horizon");
        setPropertyTooltip(sizing, "angleFollowsVelocity",
                "cluster angle is set by velocity vector angle; requires that useVelocity is on");
        setPropertyTooltip(sizing, "selectVanishingPoint",
                "Select using a mouse click a particular location in the scene as the vanishing point on the horizon");
        setPropertyTooltip(sizing, "vanishingPoint", "The particular location in the scene as the vanishing point on the horizon");
        setPropertyTooltip(sizing, "useEllipticalClusters", "true uses elliptical rather than rectangular clusters - distance based on elliptical distance including cluster angle");
        setPropertyTooltip(sizing, "updateClustersOnlyFromEventsNearEdge", "true only update circular clusters from events near cluster radius");
        setPropertyTooltip(sizing, "ellipticalClusterEdgeThickness", "thickness of elliptical cluster edge for updating it");
        setPropertyTooltip(disp, "pathLength", "paths are at most this many packets long");
        setPropertyTooltip(disp, "colorClustersDifferentlyEnabled",
                "each cluster gets assigned a random color, otherwise color indicates ages");
        setPropertyTooltip(common, "showPaths", "shows the stored path points of each cluster");
        setPropertyTooltip(disp, "classifierEnabled", "colors clusters based on single size metric");
        setPropertyTooltip(disp, "classifierThreshold", "the boundary for cluster size classification in fractions of chip max dimension");
        setPropertyTooltip(common, "showAllClusters", "shows all clusters, not just those with sufficient support");
        setPropertyTooltip(common, "showClusterVelocityVector", "draws velocity in using scaling velocityVectorScaling");
        setPropertyTooltip(disp, "showClusterVelocity", "shows velocity vector as (vx,vy) in px/s");
        setPropertyTooltip(disp, "showClusterRadius", "draws cluster radius");
        setPropertyTooltip(disp, "showClusterEps", "shows cluster events per second");
        setPropertyTooltip(disp, "showClusterEpsPerPx", "shows cluster events per second per pixel");
        setPropertyTooltip(disp, "showClusterNumber", "shows cluster ID number");
        setPropertyTooltip(disp, "showClusterMass", "shows cluster mass; mass is decaying measure of the rate of captured events");
        setPropertyTooltip(common, "velocityVectorScaling", "scaling of drawn velocity vectors from pps to pixels in AEChip pixel space");
        setPropertyTooltip(update, "useOnePolarityOnlyEnabled", "use only one event polarity");
        setPropertyTooltip(update, "useOffPolarityOnlyEnabled", "use only OFF events, not ON - if useOnePolarityOnlyEnabled");
        setPropertyTooltip(update, "growMergedSizeEnabled",
                "enabling makes merged clusters take on sum of sizes, otherwise they take on size of older cluster");
        setPropertyTooltip(update, "velAngDiffDegToNotMerge",
                "minimum relative angle in degrees of cluster velocity vectors for which not to merge overlapping clusters. Set this to zero to allow merging independent of cluster velocity. If clusters are moving in different directions, then this will prevent their merging.  The angle should be set at least to 90 deg for this to be effective.");
        setPropertyTooltip(logg, "logging", "toggles cluster logging according to method (see logDataEnabled)");
        setPropertyTooltip(logg, "logDataEnabled",
                "writes a cluster log matlab file called RectangularClusterTrackerLog.m in the startup folder host/java");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written log file");
        setPropertyTooltip(logg, "loggingIntervalUs", "interval in us between logging cluster info to logging file");
        setPropertyTooltip(logg, "clusterLoggingMethod",
                "method for logging cluster data: LogFrames logs at specified time intervals; LogClusters logs each valid cluster on its death");
        setPropertyTooltip(options, "filterEventsEnabled",
                "<html>If disabled, input packet is unaltered. <p>If enabled, output packet contains RectangularClusterTrackerEvent, <br>events refer to containing cluster, and non-owned events are discarded.");
        setPropertyTooltip(common, "maxNumClusters", "Sets the maximum potential number of clusters");
        setPropertyTooltip(pi, "smoothMove", "<html>Use the PI controller to update particle position and velocity"
                + "<br>float errX = (event.x - location.x);\n"
                + "				<br>float errY = (event.y - location.y);\n"
                + "<br>"
                + "				// float changerate=1/smoothWeight;\n"
                + "				<br>m = m / smoothWeight;\n"
                + "				<br>m1 = 1 - m;\n"
                + "<br>"
                + "				<br>velocity.x = (m1 * velocity.x) + (m * (errX));\n"
                + "				<br>velocity.y = (m1 * velocity.y) + (m * (errY));\n"
                + "<br>"
                + "				<br>location.x = location.x + (velocity.x * smoothIntegral) + (errX * smoothPosition);\n"
                + "				<br>location.y = location.y + (velocity.y * smoothIntegral) + (errX * smoothPosition);");
        setPropertyTooltip(pi, "smoothWeight", "If smoothmove is checked, the 'weight' of a cluster");
        setPropertyTooltip(pi, "smoothPosition", "Position Coefficient");
        setPropertyTooltip(pi, "smoothIntegral", "Integral Coefficient");
        // </editor-fold>
    }

    /**
     * Processes the incoming events to output RectangularClusterTrackerEvent's.
     *
     * @param in
     * @return packet of RectangularClusterTrackerEvent.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!filterEnabled) {
            return in;
        }

        // added so that packets don't use a zero length packet to set last
        // timestamps, etc, which can purge clusters for no reason
        if (in.getSize() == 0) {
            return in;
        }

        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }

        if (in instanceof ApsDvsEventPacket) {
            checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak
        } else if (filterEventsEnabled) {
            checkOutputPacketEventType(RectangularClusterTrackerEvent.class);
        }

        EventPacket filteredPacket = track(in);

        return filterEventsEnabled ? filteredPacket : in;
    }

    /**
     * Logs data if clusterLoggingMethod==ClusterLoggingMethod.LogFrames and
     * logggingIntervalUs has passed since last logg record. The frame number is
     * computed as <code> ev.timestamp / logggingIntervalUs</code>.
     *
     * @param ev
     * @param ae
     */
    protected void logData(BasicEvent ev, EventPacket<BasicEvent> ae) {
        if (logDataEnabled && (clusterLoggingMethod == ClusterLoggingMethod.LogFrames)) {
            if ((ev.timestamp / loggingIntervalUs) > logFrameNumber) {
                logFrameNumber = ev.timestamp / loggingIntervalUs;
                // Change the last flag to 'true' if needing a more easily parseable data format.
                clusterLogger.logClusters(ae, logFrameNumber, ev.timestamp, false);
                //change false(printparsable) to true if you want parsable output printed. it prints the frame no and all the 5 parameters for the particles 
                //in one line 
            }
        }
    }

    /**
     * Updates mass field of all clusters using time t as update time.
     *
     * @param t the timestamp to use
     */
    protected void updateClusterMasses(int t) {
        for (Cluster c : clusters) {
            c.updateMass(t);
        }
    }

    /**
     * @return the vanishingPoint
     */
    public Point2D getVanishingPoint() {
        return vanishingPoint;
    }

    /**
     * @param vanishingPoint the vanishingPoint to set
     */
    public void setVanishingPoint(Point2D vanishingPoint) {
        Point2D old = this.vanishingPoint;
        this.vanishingPoint = vanishingPoint;
        if (vanishingPoint != null) {
            putFloat("vanishingPoint.x", (float) vanishingPoint.getX());
            putFloat("vanishingPoint.y", (float) vanishingPoint.getY());
        } else {
            putFloat("vanishingPoint.x", Float.NaN);
            putFloat("vanishingPoint.y", Float.NaN);
        }
        getSupport().firePropertyChange("vanishingPoint", old, vanishingPoint);
    }

    /**
     * Handles logging clusters to a file for later analysis.
     */
    protected class ClusterLogger {

        String dateString = null;
        String filename = null;
        String functionName = null;
        File file = null;
        /**
         * The stream to write on.
         */
        private PrintStream logStream = null;
        private int clusterCounter = 0;
        final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

        /**
         * Writes the footer, closes the file and nulls the stream.
         */
        protected void close() {
            if (logStream != null) {
                writeFooter();
                logStream.flush();
                logStream.close();
                logStream = null;
                log.info("Closed log data file " + file.getAbsolutePath());
                showFolderInDesktop(file);
            }
        }

        /**
         * Opens the last folder where logging was sent to in desktop file
         * explorer
         */
        public void showFolderInDesktop(File file) {
            if (!Desktop.isDesktopSupported()) {
                log.warning("Desktop operations are not supported, cannot show the folder holding " + file.toString());
                return;
            }
            try {
                if (file.exists()) {
//                Path folder=Paths.get(fileNameActual).getParent().getFileName();
                    File folder = file.getAbsoluteFile().getParentFile();
                    Desktop.getDesktop().open(folder);
                } else {
                    log.warning(file + " does not exist to open folder to");
                }
            } catch (Exception e) {
                log.warning(e.toString());
            }
        }

        /**
         * Opens the file and prints the header to it. The file is written to
         * the startup folder.
         */
        protected void open() {
            try {
                clusterCounter = 0;
                dateString = DATE_FORMAT.format(new Date());
                functionName = "RectangularClusterTrackerLog_" + dateString;
                filename = functionName + ".m";
                file = new File(filename);
                logStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
                log.info("created cluster logging file at " + file.getAbsolutePath());
                writeHeader();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        /**
         * Logs the clusters at frame number as a matlab switch statement.
         *
         * @param ae the event packet.
         * @param logNumber the case # (analogous to frame) of this log record.
         */
        protected void logClusters(EventPacket<BasicEvent> ae, int logNumber, int frameTS, boolean parseablePrint) {
            if (!isLogDataEnabled() || (clusterLoggingMethod != ClusterLoggingMethod.LogFrames)) {
                return;
            }

            if (logStream != null) {
                if (parseablePrint) {
                    logStream.print(String.format("%d %d ", logNumber, frameTS));

                    int printedClusters = 0;
                    for (Cluster c : clusters) {
                        if (!c.isVisible()) {
                            continue;
                        }

                        logStream.print(String.format("%d %d %d %e %e ", c.lastEventTimestamp, (int) c.location.x, (int) c.location.y,
                                c.velocityPPS.x, c.velocityPPS.y));
                        printedClusters++;

                        if (logStream.checkError()) {
                            log.warning("eroror logging data");
                        }
                    }

                    if (printedClusters < maxNumClusters) {
                        for (int i = printedClusters; i < maxNumClusters; i++) {
                            logStream.print(String.format("%d %d %d %e %e ", 0, -1, -1, -1.0f, -1.0f));
                        }
                    }

                    logStream.println();
                } else {
                    logStream.println(String.format("case %d", logNumber));
                    logStream.println(String.format("particles=["));
                    for (Cluster c : clusters) {
                        if (!c.isVisible()) {
                            continue;
                        }

                        logStream.println(String.format("%d %e %e %e %e", c.lastEventTimestamp, c.location.x, c.location.y, c.velocityPPS.x,
                                c.velocityPPS.y));

                        if (logStream.checkError()) {
                            log.warning("eroror logging data");
                        }
                    }
                    logStream.println("];");
                }
            }
        }

        /**
         * Writes header.
         */
        protected void writeHeader() {
            if (logStream == null) {
                return;
            }
            switch (clusterLoggingMethod) {
                case LogFrames:
                    logStream.println("function [particles]=" + functionName + "(frameN)");
                    logStream.println("% written " + new Date());
                    logStream.println(
                            "% each case is one 'frame' and the case switch number is the frame number. Frame intervals are loggingIntervalUs.");
                    logStream.println("% loggingIntervalUs = " + loggingIntervalUs);
                    logStream.println("% fields for each particle are ");
                    logStream.println("% " + getFieldDescription());
                    logStream.println("switch (frameN)");
                    break;
                case LogClusters:
                    logStream.println("function [path]=" + functionName + "(clusterNumber)");
                    logStream.println("% written " + new Date());
                    logStream.println("% each case is one 'cluster' history and the case switch number is the cluster number. ");
                    logStream.println(
                            "% loggingIntervalUs = " + loggingIntervalUs + "; % not relevant here since each cluster is logged on its death");
                    logStream.println("% fields for each particle are ");
                    logStream.println("% " + getFieldDescription());
                    logStream.println("switch (clusterNumber)");
                    break;
            }
        }

        /**
         * Returns the description of the fields that are loggged for each
         * cluster, e.g. "lasttimestampus x y xvel yvel".
         *
         * @return the description. Matlab comment character is prepended.
         */
        protected String getFieldDescription() {
            switch (clusterLoggingMethod) {
                case LogFrames:
                    return "particles: lasttimestampus x y xvel yvel";
                case LogClusters:
                    return "path: timestampus, x, y, nevents, averageRadius";
                default:
                    return "unknown logging method";
            }
        }

        /**
         * Writes footer.
         */
        protected void writeFooter() {
            if (logStream == null) {
                return;
            }
            logStream.println("otherwise");
            switch (clusterLoggingMethod) {
                case LogFrames:
                    logStream.println("particles=[];");
                    break;
                case LogClusters:
                    logStream.println("clusters=[];");
            }
            // logStream.println("cla;");
            // logStream.println("set(gca,'xlim',xlim,'ylim',ylim)");
            logStream.println("end; %switch");
        }

        /**
         * @return the loggStream
         */
        public PrintStream getLogStream() {
            return logStream;
        }

        private void logClusterHistories(java.util.List<Cluster> pruneList) {
            for (Cluster c : pruneList) {
                // logg valid clusters to logging data file
                if (isLoggable(c)) {
                    logClusterHistory(c);
                }
            }
        }

        /**
         * logs the cluster history to the logging using the clusterLogger
         */
        private void logClusterHistory(Cluster c) {
            PrintStream s = clusterLogger.getLogStream();
            if (s == null) {
                return;
            }
            // s.println("case " + c.getClusterNumber());
            s.println("case " + clusterCounter++);
            s.print("path=[");
            java.util.List<ClusterPathPoint> path = c.getPath();
            for (ClusterPathPoint p : path) {
                s.print(p + ";");
            }
            s.println("];");
        }

        private boolean isLoggable(Cluster c) {
            return c.isWasEverVisible();
        }
    }

    /**
     * merge clusters that are too close to each other and that have
     * sufficiently similar velocities (if velocityAngleToRad). this must be
     * done interactively, because feed-forward merging of 4 or more clusters
     * can result in more clusters than you start with. each time we merge two
     * clusters, we start over, until there are no more merges on iteration. for
     * each cluster, if it is close to another cluster then merge them and start
     * over.
     */
    protected void mergeClusters() {
        if (isDontMergeEver()) {
            return;
        }

        boolean mergePending;
        Cluster c1 = null;
        Cluster c2 = null;
        do {
            mergePending = false;
            int nc = clusters.size();
            outer:
            for (int i = 0; i < nc; i++) {
                c1 = clusters.get(i);
                for (int j = i + 1; j < nc; j++) {
                    c2 = clusters.get(j); // getString the other cluster
                    // final boolean overlapping = c1.distanceTo(c2) < (c1.getRadius() + c2.getRadius());
                    final boolean overlapping = c1.isOverlappingCenterOf(c2);
                    boolean velSimilar = true; // start assuming velocities are similar
                    if (overlapping && (velAngDiffDegToNotMerge > 0) && c1.isVisible() && c2.isVisible() && c1.isVelocityValid()
                            && c2.isVelocityValid() && (c1.velocityAngleToRad(c2) > ((velAngDiffDegToNotMerge * Math.PI) / 180))) {
                        // if velocities valid for both and velocities are sufficiently different
                        velSimilar = false; // then flag them as different velocities
                    }
                    if (overlapping && velSimilar) {
                        // if cluster is close to another cluster, merge them
                        // if distance is less than sum of radii merge them and if velAngle < threshold
                        mergePending = true;
                        break outer; // break out of the outer loop
                    }
                }
            }
            if (mergePending && (c1 != null) && (c2 != null)) {
                pruneList.add(c1);
                pruneList.add(c2);
                clusters.remove(c1);
                clusters.remove(c2);
                fastClusterFinder.removeCluster(c1);
                fastClusterFinder.removeCluster(c2);

                // clusters.append(new Cluster(c1, c2)); // No good for cluster-class overriding!
                clusters.add(createCluster(c1, c2));

                // System.out.println("merged "+c1+" and "+c2);
            }
        } while (mergePending);
        // update all cluster sizes
        // note that without this following call, clusters maintain their starting size until they are merged with
        // another cluster.
        if (isHighwayPerspectiveEnabled()) {
            for (Cluster c : clusters) {
                c.setRadius(defaultClusterRadius * chip.getMaxSize());
            }
        }
    }

    /**
     * Prunes out old clusters that don't have support or that should be purged
     * for some other reason.
     *
     * @param t the timestamp of the purge operation
     */
    protected void pruneClusters(int t) {
        pruneList.clear();
        for (Cluster c : clusters) {
            int t0 = c.lastEventTimestamp;
            // int t1=ae.getLastTimestamp();
            int timeSinceSupport = t - t0;
            if (timeSinceSupport == 0) {
                continue; // don't kill off cluster spawned from first event
            }
            boolean massTooSmall = false;

            int lifetime = c.getLifetime();

            // TODO patch to handle clusters that are not hit with events
            if (t > c.getLastEventTimestamp()) {
                lifetime = t - c.getBirthTime();
            }

            if (!useEventRatePerPixelForVisibilty) {
                float massThreshold = thresholdMassForVisibleCluster;
                if (highwayPerspectiveEnabled) {
                    massThreshold *= c.getPerspectiveScaleFactor();
                }
                // do not kill off clusters that were just born or have not lived at least their clusterMassDecayTauUs
                if (((lifetime == 0) || (lifetime >= clusterMassDecayTauUs)) && (c.getMassNow(t) < massThreshold)) {
                    massTooSmall = true;
                }
            } else { // event rate per pixel
                // do not kill off clusters that were just born or have not lived at least their clusterMassDecayTauUs
                if (((lifetime == 0) || (lifetime >= clusterMassDecayTauUs)) && (c.getAvgEventRateHz()/c.getArea() < thresholdEventRatePerPixelForVisibleClusterHz)) {
                    massTooSmall = true;
                }
            }
            boolean hitEdge = c.hasHitEdge();
            if ((t0 > t) || massTooSmall || (timeSinceSupport < 0) || hitEdge) {
                // if (massTooSmall || hitEdge) {
                // ordinarily, we discard the cluster if it hasn't gotten any support for a while, but we also discard
                // it if there
                // is something funny about the timestamps
                pruneList.add(c);
                // String reason=null;
                // if(t0>t) reason="time went backwards";
                // else if(massTooSmall) reason="mass is too small and cluster has existed at least
                // clusterMassDecayTauUs";
                // else if(timeSinceSupport<0) reason="timeSinceSupport is negative";
                // else if(hitEdge) reason="cluster hit edge";
                // logg.info("pruning "+c+" because "+reason);
            }
            // if(t0>t1){
            // logg.warning("last cluster timestamp is later than last packet timestamp");
            // }
        } // clusters

        if (logDataEnabled && (clusterLogger != null) && (clusterLoggingMethod == ClusterLoggingMethod.LogClusters)
                && !pruneList.isEmpty()) {
            clusterLogger.logClusterHistories(pruneList);
        }

        for (Cluster c : pruneList) {
            if (c == null) {
                continue;
            }
//            if(c.getClusterNumber()==1){
//                log.info("first cluster"); 
//            }
            c.onPruning();
        }

        clusters.removeAll(pruneList);
        for (Cluster c : pruneList) {
            if (c == null) {
                continue;
            }
            fastClusterFinder.removeCluster(c);
            c = null;
        }
    }

    // private int lastUpdateClusterListTime=Integer.MIN_VALUE;
    /**
     * This method updates the list of clusters, pruning and merging clusters
     * and updating positions based on cluster velocities. It also updates the
     * optical gyro if enabled.
     *
     * @param t the global timestamp of the update.
     */
    protected void updateClusterList(int t) {
        // int dt=t-lastUpdateClusterListTime;
        // System.out.println("updateClusterList dt = "+dt);
        // lastUpdateClusterListTime=t;
        pruneClusters(t);
        mergeClusters();
        updateClusterLocations(t);
        updateClusterPaths(t);
        updateClusterMasses(t);
        updateVisibilities(t);
    }

    private void updateVisibilities(int t) {
        visibleClusters.clear();
        for (Cluster c : clusters) {
            if (c.updateVisibility(t)) {
                visibleClusters.add(c);
            }
        }
    }

    @Override
    public void initFilter() {
        initDefaults();
        defaultClusterRadius = Math.max(chip.getSizeX(), chip.getSizeY()) * getClusterSize();
        fastClusterFinder.init();
    }

    private void initDefaults() {
        initDefault("RectangularClusterTracker.clusterMassDecayTauUs", "10000");
        initDefault("RectangularClusterTracker.maxNumClusters", "10");
        initDefault("RectangularClusterTracker.clusterSize", "0.15f");
        initDefault("RectangularClusterTracker.numEventsStoredInCluster", "100");
        initDefault("RectangularClusterTracker.thresholdMassForVisibleCluster", "30");
    }

    private void initDefault(String key, String value) {
        if (getPrefs().get(key, null) == null) {
            getPrefs().put(key, value);
        }
    }

    /**
     * The method that actually does the tracking.
     *
     * @param in the event packet.
     * @return a possibly filtered event packet passing only events contained in
     * the tracked and visible Clusters, depending on filterEventsEnabled.
     */
    synchronized protected EventPacket<? extends BasicEvent> track(EventPacket<? extends BasicEvent> in) {
        boolean updatedClusterList = false;
        checkOutputPacketEventType(RectangularClusterTrackerEvent.class);
        OutputEventIterator outItr = out.outputIterator();
        int sx = chip.getSizeX(), sy = chip.getSizeY();

        if (in.getSize() == 0) {
            return out; // nothing to do
        }
        // record cluster locations before packet is processed
        for (Cluster c : clusters) {
            c.getLastPacketLocation().setLocation(c.location);
        }

        // for each event, see which cluster it is closest to and append it to this cluster.
        // if its too far from any cluster, make a new cluster if we have not jet
        // reached maxNumClusters
        // This will also update the Position, Mass, EventRate and AverageDistance
        for (BasicEvent ev : in) {
            if ((ev == null) || ev.isSpecial() || ev.isFilteredOut()) {
                continue;
            }
            if ((ev.x < 0) || (ev.x >= sx) || (ev.y < 0) || (ev.y >= sy)) {
                continue; // out of bounds from e.g. steadicom transform
            }
            Cluster closest = fastClusterFinder.findClusterNear(ev);

            if (closest != null) {
                if (filterEventsEnabled) {
                    closest.addEvent(ev, outItr);
                } else {
                    closest.addEvent(ev);
                }
            } else if (clusters.size() < maxNumClusters) { // start a new cluster
                Cluster newCluster;
                if (filterEventsEnabled) {
                    newCluster = createCluster(ev, outItr);
                } else {
                    newCluster = createCluster(ev);
                }
                clusters.add(newCluster);
            }

            updatedClusterList = maybeCallUpdateObservers(in, (lastTimestamp = ev.timestamp)); // callback to update()

            if (logDataEnabled) {
                logData(ev, (EventPacket<BasicEvent>) in);
            }
        }
        // TODO update here again, relying on the fact that lastEventTimestamp was set by possible previous update
        // according to
        // schedule; we have have double update of velocityPPT using same dt otherwise
        // Once a Packet we update the ClusterPath, ClusterPosition(speed based) and check if the cluster is visible
        // if (!updatedClusterList) {
        // updateClusterList(lastTimestamp); // at laest once per packet update list
        // }
        // for (Cluster c : clusters) {
        // if (!c.isVisible()) {
        // continue;
        // }
        // RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent) outItr.nextOutput();
        // oe.setX((short) c.getLocation().x);
        // oe.setY((short) c.getLocation().y);
        // oe.setCluster(c);
        // }
        return out;
    }

    /**
     * Returns total number of clusters, including those that have been seeded
     * but may not have received sufficient support yet.
     *
     * @return number of Clusters in clusters list.
     */
    public int getNumClusters() {
        return clusters.size();
    }

    /**
     * Returns number of "visible" clusters; those that have received sufficient
     * support. This field is updated by updateClusterList()
     *
     * @return number
     */
    synchronized public int getNumVisibleClusters() {
        return numVisibleClusters;
    }

    @Override
    public String toString() {
        String s = clusters != null ? Integer.toString(clusters.size()) : null;
        String s2 = "RectangularClusterTracker#" + hashCode() + " with " + s + " clusters ";
        return s2;
    }

    /**
     * Method that given event, returns closest cluster and distance to it. The
     * actual computation returns the first cluster that is within the
     * minDistance of the event, which reduces the computation at the cost of
     * reduced precision, unless the option useNearestCluster is enabled. Then
     * the closest cluster is used, rather than the first in the list. The first
     * cluster to be in range is usually the older one so usually
     * useNearestCluster is not very beneficial.
     * <p>
     * The range for an event being in the cluster is defined by the cluster
     * radius. If dynamicSizeEnabled is true, then the radius is multiplied by
     * the surround.
     * <p>
     * The cluster radius is actually defined for x and y directions since the
     * cluster may not have a square aspect ratio.
     *
     * @param event the event
     * @return closest cluster object (a cluster with a distance - that distance
     * is the distance between the given event and the returned cluster).
     */
    protected Cluster getNearestCluster(BasicEvent event) { // TODO needs to account for the cluster angle
        float minDistance = Float.MAX_VALUE;
        Cluster closest = null;
        float currentDistance = 0;
        for (Cluster c : clusters) {
            float rX = c.radiusX;
            float rY = c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or
            // aspect ratio
            if (dynamicSizeEnabled) {
                rX *= surround;
                rY *= surround; // the event is captured even when it is in "invisible surround"
            }
            float dx, dy;// TODO use cluster angle here, consider eliptical or at least circular clusters... not correct now with Manhattan distance
            dx = c.distanceToX(event);
            dy = c.distanceToY(event);
            float dist = useEllipticalClusters ? (float) sqrt(dx * dx + dy * dy) : dx + dy;
            boolean withinRadius;
            if (!useEllipticalClusters) {
                withinRadius = (dx <= rX) && (dy <= rY);
            } else {
                withinRadius = dist <= c.radius;
            }
            if (withinRadius) {
                currentDistance = dist;
                if (currentDistance < minDistance) {
                    closest = c;
                    minDistance = currentDistance;
                    c.distanceToLastEvent = minDistance; // store data in cluste for later use in moving cluster
                    c.xDistanceToLastEvent = dx;
                    c.yDistanceToLastEvent = dy;
                }
            }
        }
        return closest;
    }

    /**
     * Given AE, returns first (thus oldest) cluster that event is within. The
     * radius of the cluster here depends on whether
     * {@link #setDynamicSizeEnabled(boolean) scaling} is enabled.
     *
     * @param event the event
     * @return cluster that contains event within the cluster's radius, modfied
     * by aspect ratio. null is returned if no cluster is close enough.
     */
    protected Cluster getFirstContainingCluster(BasicEvent event) {
        float minDistance = Float.MAX_VALUE;
        Cluster closest = null;
        float currentDistance = 0;
        for (Cluster c : clusters) {
            float rX = c.radiusX;
            float rY = c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or
            // aspect ratio
            if (dynamicSizeEnabled) {
                rX *= surround;
                rY *= surround; // the event is captured even when it is in "invisible surround"
            }
            float dx, dy;
            if (((dx = c.distanceToX(event)) < rX) && ((dy = c.distanceToY(event)) < rY)) { // TODO needs to account for
                // instantaneousAngle
                currentDistance = dx + dy;
                closest = c;
                minDistance = currentDistance;
                c.distanceToLastEvent = minDistance;
                c.xDistanceToLastEvent = dx;
                c.yDistanceToLastEvent = dy;

                break;
            }
        }
        return closest;
    }

    /**
     * Updates cluster locations based on cluster velocities, if
     * {@link #useVelocity} is enabled.
     *
     * @param t the global timestamp of the update.
     */
    protected void updateClusterLocations(int t) {
        if (!useVelocity) {
            return;
        }

        for (Cluster c : clusters) {
            if (c.isVelocityValid()) {
                int dt = t - c.lastUpdateTime;
                if (dt <= 0) {
                    continue; // bogus timestamp or doesn't need update
                }
                c.location.x += c.velocityPPT.x * dt * predictiveVelocityFactor;
                c.location.y += c.velocityPPT.y * dt * predictiveVelocityFactor;
                if (initializeVelocityToAverage) {
                    // update average velocity metric for construction of new Clusters
                    averageVelocityPPT.x = ((1 - AVERAGE_VELOCITY_MIXING_FACTOR) * averageVelocityPPT.x)
                            + (AVERAGE_VELOCITY_MIXING_FACTOR * c.velocityPPT.x);
                    averageVelocityPPT.y = ((1 - AVERAGE_VELOCITY_MIXING_FACTOR) * averageVelocityPPT.y)
                            + (AVERAGE_VELOCITY_MIXING_FACTOR * c.velocityPPT.y);
                }
                c.lastUpdateTime = t;
            }
            fastClusterFinder.update(c);
        }
    }

    /**
     * Updates cluster path lists and counts number of visible clusters.
     *
     * @param t the update timestamp
     */
    protected void updateClusterPaths(int t) {
        // update paths of clusters
        numVisibleClusters = 0;
        for (Cluster c : clusters) {
            c.updatePath(t);
            if (c.isVisible()) {
                numVisibleClusters++;
            }
        }
    }

    /**
     * Factory method to create a new Cluster; override when subclassing
     * Cluster.
     *
     * @param ev the spawning event.
     * @return a new empty Cluster
     */
    public Cluster createCluster(BasicEvent ev) {
        return new Cluster(ev);
    }

    /**
     * Factory method to create a new Cluster; override when subclassing
     * Cluster.
     *
     * @param one the first cluster.
     * @param two the second cluster.
     * @return a new empty Cluster
     */
    public Cluster createCluster(Cluster one, Cluster two) {
        return new Cluster(one, two);
    }

    /**
     * Factory method to create a new Cluster; override when subclassing
     * Cluster.
     *
     * @param ev the spawning event.
     * @param itr the output iterator to write events to when they fall in this
     * cluster.
     * @return a new empty Cluster
     */
    public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
        return new Cluster(ev, itr);
    }

    /**
     * The basic object that is tracked, which is a rectangular cluster with
     * (optionally) variable size, aspect ratio, and angle.
     */
    // This class is 1444 lines of code long... Readability = 0!
    public class Cluster implements ClusterInterface { // TODO badly needs a cheap contains(ev) method that accounts for
        // all cluster geometry (aspect ratio, angle)!!!

        public final float VELPPS_SCALING = 1e6f / AEConstants.TICK_DEFAULT_US;

        /**
         * location of cluster in pixels
         */
        public Point2D.Float location = new Point2D.Float(); // location in chip pixels
        /**
         * velocity of cluster in PPS
         */
        public Point2D.Float velocity = new Point2D.Float(); // location in chip pixels
        /**
         * birth location of cluster
         */
        private Point2D.Float birthLocation = new Point2D.Float();
        /**
         * location at end of last packet, used for movement sample
         */
        private Point2D.Float lastPacketLocation = new Point2D.Float();
        /**
         * velocityPPT of cluster in pixels/tick, where tick is timestamp tick
         * (usually microseconds)
         */
        protected Point2D.Float velocityPPT = new Point2D.Float();
        /**
         * cluster velocityPPT in pixels/second
         */
        protected Point2D.Float velocityPPS = new Point2D.Float();
        /**
         * Angle of cluster in radians with zero being horizontal and CCW > 0.
         * sinAngle and cosAngle are updated when angle is updated.
         */
        protected float angle = 0, cosAngle = 1, sinAngle = 0;
        /**
         * Rendered color of cluster.
         */
        protected Color color = null;
        /**
         * Number of events collected by this cluster.
         */
        protected int numEvents = 0;
        /**
         * Number of events from previous update of cluster list.
         */
        protected int previousNumEvents = 0; // total number of events and number at previous packet
        /**
         * First and last timestamp of cluster. <code>firstEventTimestamp</code>
         * is updated when cluster becomes visible.
         * <code>lastEventTimestamp</code> is the last time the cluster was
         * touched either by an event or by some other timestamped update, e.g.
         * null null null null null null null null null null null null null null
         * null null null null         {@link #updateClusterList(net.sf.jaer.event.EventPacket, int)
		 * }.
         *
         * @see #isVisible()
         */
        protected int lastEventTimestamp, firstEventTimestamp;
        /**
         * The "mass" of the cluster is the weighted number of events it has
         * collected. The mass decays over time and is incremented by one by
         * each collected event. The mass decays with a first order time
         * constant of clusterMassDecayTauUs in us. If
         * surroundInhibitionEnabled=true, then the mass is decremented by
         * events captured in the surround.
         */
        private float mass = 1;
        /**
         * This is the last time in timestamp ticks that the cluster was
         * updated, either by an event or by a regular update such as
         * {@link #updateClusterLocations(int)}. This time can be used to
         * compute position updates given a cluster velocityPPT and time now.
         */
        protected int lastUpdateTime;
        /**
         * events/tick event rate for last two events.
         */
        protected float instantaneousEventRate; // in events/tick
        /**
         * Flag which is set true (forever) once a cluster has first obtained
         * sufficient support.
         */
        protected boolean hasObtainedSupport = false;
        /**
         * average (mixed using mixingFactor) distance of events from cluster
         * center, a measure of actual cluster size.
         */
        private float averageEventDistance, averageEventXDistance, averageEventYDistance;
        /**
         * assigned to be the absolute number of the cluster that has been
         * created.
         */
        private int clusterNumber;
        /**
         * Average event rate as computed using eventRatePerPixelLowpassTauS.
         *
         * @see #eventRatePerPixelLowpassTauS
         */
        private float avgEventRateHz = 0;
        private float radius; // in chip chip pixels
        protected float aspectRatio, radiusX, radiusY;
        protected LinkedList<ClusterPathPoint> path = new LinkedList<>();

        private LowpassFilter vxFilter = new LowpassFilter(), vyFilter = new LowpassFilter();
        private float avgISI;
        private float[] rgb = new float[4];
        private boolean velocityValid = false; // used to flag invalid or uncomputable velocityPPT
        private boolean visibilityFlag = false; // this flag updated in updateClusterList
        protected float instantaneousISI; // ticks/event
        protected float distanceToLastEvent = Float.POSITIVE_INFINITY;
        protected float xDistanceToLastEvent = Float.POSITIVE_INFINITY, yDistanceToLastEvent = Float.POSITIVE_INFINITY;

        // public float tauMsVelocity=50; // LP filter time constant for velocityPPT change
        // private LowpassFilter velocityFilter=new LowpassFilter();
        // private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it
        // is to change its velocityPPT
        // protected LinkedList<ClusterPathPoint> path = new ArrayList<ClusterPathPoint>(getPathLength());
        // ArrayList<EventXYType> events=new ArrayList<EventXYType>();
        // private RollingVelocityFitter velocityFitter = new RollingVelocityFitter(path, velocityPoints);
        /**
         * Constructs a default cluster.
         */
        public Cluster() {
            setRadius(defaultClusterRadius);
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue, 1f, 1f);
            setColor(c);
            setClusterNumber(++clusterCounter);
            setAspectRatio(RectangularClusterTracker.this.getAspectRatio());
            vxFilter.setTauMs(velocityTauMs);
            vyFilter.setTauMs(velocityTauMs);
            if (initializeVelocityToAverage) {
                velocityPPT.x = averageVelocityPPT.x;
                velocityPPT.y = averageVelocityPPT.y;
                velocityValid = true;
            }
            setAngle(initialAngle);
        }

        /**
         * Constructs a cluster at the location of an event. The numEvents,
         * location, birthLocation, first and last timestamps are set. The
         * radius is set to defaultClusterRadius.
         *
         * @param ev the event.
         */
        public Cluster(BasicEvent ev) {
            this();
            location.x = ev.x;
            location.y = ev.y;
            birthLocation.x = ev.x;
            birthLocation.y = ev.y;
            lastPacketLocation.x = ev.x;
            lastPacketLocation.y = ev.y;
            lastEventTimestamp = ev.timestamp;
            lastUpdateTime = ev.timestamp;
            firstEventTimestamp = lastEventTimestamp;
            numEvents = 1;
            mass = 1;
            setRadius(defaultClusterRadius);
        }

        /**
         * Creates a new Cluster using the event and generates a new output
         * event which points back to the Cluster.
         *
         * @param ev the event to center the cluster on.
         * @param outItr used to generate the new event pointing back to the
         * cluster.
         */
        protected Cluster(BasicEvent ev, OutputEventIterator outItr) {
            this(ev);
            if (!isVisible()) {
                return;
            }

            RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent) outItr.nextOutput();
            oe.copyFrom(ev);
            oe.setCluster(this);
        }

        /**
         * Constructs a cluster by merging two clusters. All parameters of the
         * resulting cluster should be reasonable combinations of the source
         * cluster parameters. For example, the merged location values are
         * weighted by the mass of events that have supported each source
         * cluster, so that older clusters weigh more heavily in the resulting
         * cluster location. Subtle bugs or poor performance can result from not
         * properly handling the merging of parameters.
         *
         * @param one the first cluster
         * @param two the second cluster
         */
        public Cluster(Cluster one, Cluster two) {
            this();
            mergeTwoClustersToThis(one, two);
        }

        /**
         * Computes and returns {@link #mass} at time t, using the last time an
         * event hit this cluster and the {@link #clusterMassDecayTauUs}. Does
         * not change the mass.
         *
         * @param t timestamp now.
         * @return the mass.
         */
        protected float getMassNow(int t) {
            float m = mass * (float) Math.exp(((float) (lastEventTimestamp - t)) / clusterMassDecayTauUs);
            return m;
        }

        // <editor-fold defaultstate="collapsed" desc="getter/setter for --mass--">
        /**
         * The "mass" of the cluster is the weighted number of events it has
         * collected. The mass decays over time and is incremented by one by
         * each collected event. The mass decays with a first order time
         * constant of clusterMassDecayTauUs in us.
         *
         * @return the mass
         */
        @Override
        public float getMass() {
            return mass;
        }

        /**
         * Sets the internal "mass" of the cluster.
         *
         * @see #getMass()
         * @param mass
         */
        public void setMass(float mass) {
            this.mass = mass;
        }
        // </editor-fold>

        /**
         * Merges information from two source clusters into this cluster to
         * preserve the combined history that is most reliable.
         *
         * @param one
         * @param two
         */
        protected void mergeTwoClustersToThis(Cluster one, Cluster two) {
            Cluster stronger = one.mass > two.mass ? one : two; // one.firstEventTimestamp < two.firstEventTimestamp ?
            // one : two;
            // Cluster older=one.numEvents>two.numEvents? one:two;
            clusterNumber = stronger.clusterNumber;
            // merge locations by average weighted by mass of events supporting each cluster
            mass = one.mass + two.mass;
            numEvents = one.numEvents + two.numEvents;
            // change to older for location to avoid discontinuities in postion
            location.x = stronger.location.x; // (one.location.x * one.mass + two.location.x * two.mass) / (mass);
            location.y = stronger.location.y; // (one.location.y * one.mass + two.location.y * two.mass) / (mass);

            velocity.x = 0;
            velocity.y = 0;

            angle = stronger.angle;
            cosAngle = stronger.cosAngle;
            sinAngle = stronger.sinAngle;
            averageEventDistance = ((one.averageEventDistance * one.mass) + (two.averageEventDistance * two.mass)) / mass;
            averageEventXDistance = ((one.averageEventXDistance * one.mass) + (two.averageEventXDistance * two.mass)) / mass;
            averageEventYDistance = ((one.averageEventYDistance * one.mass) + (two.averageEventYDistance * two.mass)) / mass;

            lastEventTimestamp = one.lastEventTimestamp > two.lastEventTimestamp ? one.lastEventTimestamp : two.lastEventTimestamp;
            lastUpdateTime = lastEventTimestamp;
            lastPacketLocation.x = stronger.location.x;
            lastPacketLocation.y = stronger.location.y;
            firstEventTimestamp = stronger.firstEventTimestamp; // make lifetime the oldest src cluster
            path = stronger.path;
            birthLocation = stronger.birthLocation;
            // velocityFitter = stronger.velocityFitter;
            velocityPPT.x = stronger.velocityPPT.x;
            velocityPPT.y = stronger.velocityPPT.y;
            velocityPPS.x = stronger.velocityPPS.x;
            velocityPPS.y = stronger.velocityPPS.y;
            velocityValid = stronger.velocityValid;
            vxFilter = stronger.vxFilter;
            vyFilter = stronger.vyFilter;
            avgEventRateHz = stronger.avgEventRateHz;
            avgISI = stronger.avgISI;
            hasObtainedSupport = one.hasObtainedSupport || two.hasObtainedSupport; // if either was ever visible then mark merged wasEverVisible
            visibilityFlag = one.visibilityFlag || two.visibilityFlag; // make it visible if either visible
            setAspectRatio(stronger.getAspectRatio());

            // Color c1=one.getColor(), c2=two.getColor();
            setColor(stronger.getColor());
            // System.out.println("merged "+one+" with "+two);
            // the radius should increase
            // setRadius((one.getRadius()+two.getRadius())/2);
            if (growMergedSizeEnabled) {
                float R = (one.getRadius() + two.getRadius()); // tobi changed to sum radii
                setRadius(R + (getMixingFactor() * R));
            } else {
                setRadius(stronger.getRadius());
            }
        }

        protected void updateAngle(BasicEvent event) {
            // dynamically rotates cluster to line it up with edge.
            // the cluster instantaneousAngle is defined so horizontal edges have instantaneousAngle 0 or +/-PI,
            // vertical have +/- PI/2.
            // instantaneousAngle increases CCW from 0 for rightward from center of cluster events.
            //
            // awkwardness here is that events will fall on either side around center of cluster.
            // instantaneousAngle of event is 0 or +/-PI when events are mostly horizontal (there is a cut at +/-PI from
            // atan2).
            // similarly, if events are mostly vertical, then instantaneousAngle is either PI/2 or -PI/2.
            // if we just average instantaneous instantaneousAngle we getString something in between which is at 90 deg
            // to actual instantaneousAngle of cluster.
            // if the event instantaneousAngle<0, we use PI-instantaneousAngle; this transformation makes all event
            // angles fall from 0 to PI.
            // now the problem is that horizontal events still average to PI/2 (vertical cluster).
            float dx = location.x - event.x;
            float dy = location.y - event.y;
            float newAngle = (float) (Math.atan2(dy, dx));
            if (newAngle < 0) {
                newAngle += (float) Math.PI; // puts newAngle in 0,PI, e.g -30deg becomes 150deg
            } // just the other end of the object and flip the newAngle.
            // boolean flippedPos=false, flippedNeg=false;
            float diff = newAngle - angle;
            if ((diff) > (Math.PI / 2)) {
                // newAngle is clockwise a lot, flip it back across to
                // negative value that can be averaged; e.g. instantaneousAngle=10, newAngle=179, newAngle->-1.
                newAngle = newAngle - (float) Math.PI;
            } else if (diff < (-Math.PI / 2)) {
                // newAngle is CCW
                newAngle = -(float) Math.PI + newAngle; // instantaneousAngle=10, newAngle=179, newAngle->1
                // flippedNeg=true;
            } // newAngle=(float)Math.PI-newAngle;
            // if(newAngle>3*Math.PI/4)
            // newAngle=(float)Math.PI-newAngle;
            float angleDistance = newAngle - angle; // angleDistance(instantaneousAngle, newAngle);
            // makes instantaneousAngle=0 for horizontal positive event, PI for horizontal negative event y=0+eps,x=-1,
            // -PI for y=0-eps, x=-1, //
            // PI/2 for vertical positive, -Pi/2 for vertical negative event
            setAngle(angle + (mixingFactor * angleDistance));
            // System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f\tflippedPos=%s\tflippedNeg=%s",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,instantaneousAngle*180/Math.PI,flippedPos,flippedNeg));
            // System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,instantaneousAngle*180/Math.PI));
            // setAngle(-.1f);
        }

        protected void updateAspectRatio(BasicEvent event) {
            // TODO aspect ratio must also account for dynamicAngleEnabled.
            float dx = event.x - location.x;
            float dy = event.y - location.y;
            float dw = (dx * cosAngle) + (dy * sinAngle); // dot dx,dy with unit vector of instantaneousAngle of cluster
            float dh = (-dx * sinAngle) + (dy * cosAngle); // and with normal to unit vector
            float oldAspectRatio = getAspectRatio();
            float newAspectRatio = Math.abs(dh / dw);
            if (dynamicAngleEnabled) {
                if (newAspectRatio > ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED) {
                    newAspectRatio = ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED;
                } else if (newAspectRatio < ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED) {
                    newAspectRatio = ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED;
                }
            } else {
                if (newAspectRatio > ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED) {
                    newAspectRatio = ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED;
                } else if (newAspectRatio < ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED) {
                    newAspectRatio = ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED;
                }
            }
            setAspectRatio(((1 - mixingFactor) * oldAspectRatio) + (mixingFactor * newAspectRatio));
        }

        /**
         * updates average event distance from cluster center.
         *
         * @param m mixing factor that weight how much to use current distance
         * from center (m) and old value (1-m)
         */
        protected void updateAverageEventDistance(float m) {
            if (Float.isNaN(averageEventDistance)) {
                log.warning("distance is NaN");
            }
            float m1 = 1 - m;
            //m is specified when calling this method, it is the mixing factor.
            averageEventDistance = (m1 * averageEventDistance) + (m * distanceToLastEvent);
            averageEventXDistance = (m1 * averageEventXDistance) + (m * xDistanceToLastEvent);
            averageEventYDistance = (m1 * averageEventYDistance) + (m * yDistanceToLastEvent);
        }

        protected void updateEventRate(BasicEvent event, float m) {
            // velocityPPT of cluster is updated here as follows
            // 1. instantaneous velocityPPT is computed from old and new cluster locations and dt
            // 2. new velocityPPT is computed by mixing old velocityPPT with instaneous new velocityPPT using
            // velocityMixingFactor
            // Since an event may pull the cluster back in the opposite direction it is moving, this measure is likely
            // to be quite noisy.
            // It would be better to use the saved cluster locations after each packet is processed to perform an online
            // regression
            // over the history of the cluster locations. Therefore we do not use the following anymore.
            // if(useVelocity && dt>0){
            // // update velocityPPT vector using old and new position only if valid dt
            // // and update it by the mixing factors
            // float oldvelx=velocityPPT.x;
            // float oldvely=velocityPPT.y;
            //
            // float velx=(location.x-oldx)/dt; // instantaneous velocityPPT for this event in pixels/tick (pixels/us)
            // float vely=(location.y-oldy)/dt;
            //
            // float vm1=1-velocityMixingFactor;
            // velocityPPT.x=vm1*oldvelx+velocityMixingFactor*velx;
            // velocityPPT.y=vm1*oldvely+velocityMixingFactor*vely;
            // velocityPPS.x=velocityPPT.x*VELPPS_SCALING;
            // velocityPPS.y=velocityPPT.y*VELPPS_SCALING;
            // }
            int prevLastTimestamp = lastEventTimestamp;
            lastEventTimestamp = event.timestamp;
            numEvents++;
            instantaneousISI = lastEventTimestamp - prevLastTimestamp;
            if (instantaneousISI <= 0) {
                instantaneousISI = 1;
            }
            instantaneousEventRate = 1e6f / instantaneousISI; // in Hz
            float lowpassEps = 1e-6f * instantaneousISI / eventRatePerPixelLowpassTauS;

            avgEventRateHz = ((1 - lowpassEps) * avgEventRateHz) + (lowpassEps * instantaneousEventRate);
        }

        /**
         * Increments mass of cluster by one after decaying it away since the
         * {@link #lastEventTimestamp} according to exponential decay with time
         * constant {@link #clusterMassDecayTauUs}.
         *
         * @param event used for event timestamp.
         */
        protected void updateMass(int t) {
            if (surroundInhibitionEnabled) {
                // if the event is in the surround, we decrement the mass, if inside cluster, we increment
                float normDistance = distanceToLastEvent / radius;
                // float dmass = normDistance <= 1 ? 1 : -1;
                float dmass = normDistance <= 1 ? 1 : -surroundInhibitionCost;
                mass = dmass + (mass * (float) Math.exp((float) (lastEventTimestamp - t) / clusterMassDecayTauUs));
                if (mass < 0) {
                    mass = 0;
                }
            } else {
                boolean wasInfinite = Float.isInfinite(mass);
                // don't worry about distance, just increment
                int dt = lastEventTimestamp - t;
                if (dt < 0) {
                    mass = 1 + (mass * (float) Math.exp((float) dt / clusterMassDecayTauUs));
                    if (!wasInfinite && Float.isInfinite(mass)) {
                        log.log(Level.WARNING, "mass became infinite for {0}", this);
                    }
                }
            }
        }

        /**
         * Returns true if the cluster center is outside the array or if this
         * test is enabled and if the cluster has hit the edge of the array and
         * has been there at least the minimum time for support.
         *
         * @return true if cluster has hit edge for long enough
         * (getClusterMassDecayTauUs) and test enableClusterExitPurging
         */
        protected boolean hasHitEdge() {
            if (!enableClusterExitPurging) {
                return false;
            }

            if (isPurgeIfClusterOverlapsBorder()) {
                return isOverlappingBorder();
            }

            int lx = (int) location.x, ly = (int) location.y;
            int sx = chip.getSizeX(), sy = chip.getSizeY();

            return ((lx <= 0) || (lx >= sx) || (ly <= 0) || (ly >= sy)); // always onPruning if cluster center is outside
            // array, e.g. from velocityPPT prediction
        }

        /**
         * Returns true if cluster is overlapping chip border on any side.
         *
         * @return true if overlapping border.
         */
        public boolean isOverlappingBorder() {
            int lx = (int) location.x, ly = (int) location.y;
            int sx = chip.getSizeX(), sy = chip.getSizeY();

            if ((lx < 0) || (lx > sx) || (ly < 0) || (ly > sy)) {
                return true; // always true if cluster is outside array, e.g. from velocityPPT prediction
            }
            if ((lx < radiusX) || (lx > (sx - radiusX)) || (ly < radiusY) || (ly > (sy - radiusY))) {
                return true;
            }
            return false;
        }

        /**
         * Cluster velocityPPT in pixels/timestamp tick as a vector. Velocity
         * values are set during cluster upate.
         *
         * @return the velocityPPT in pixels per timestamp tick.
         * @see #getVelocityPPS()
         */
        @Override
        public Point2D.Float getVelocityPPT() {
            return velocityPPT;
        }

        /**
         * The location of the cluster at the end of the previous packet. Can be
         * used to measure movement of cluster during this packet.
         *
         * @return the lastPacketLocation.
         */
        public Point2D.Float getLastPacketLocation() {
            return lastPacketLocation;
        }

        /**
         * Overrides default hashCode to return {@link #clusterNumber}. This
         * overriding allows for storing clusters in lists and checking for them
         * by their clusterNumber.
         *
         * @return clusterNumber
         */
        @Override
        public int hashCode() {
            return clusterNumber;
        }

        /**
         * Two clusters are equal if their {@link #clusterNumber}'s are equal.
         *
         * @param obj another Cluster.
         * @return true if equal.
         */
        @Override
        public boolean equals(Object obj) { // derived from
            // http://www.geocities.com/technofundo/tech/java/equalhash.html
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (obj.getClass() != this.getClass())) {
                return false;
            }
            // object must be Test at this point
            Cluster test = (Cluster) obj;
            return clusterNumber == test.clusterNumber;
        }

        /**
         * Draws this cluster using OpenGL.
         *
         * @param drawable area to draw this.
         */
        public void draw(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            GLUT cGLUT = chip.getCanvas().getGlut();

            final float BOX_LINE_WIDTH = 2f; // in chip
            final float PATH_POINT_SIZE = 4f;

            // set color and line width of cluster annotation
            setColorAutomatically();
            rgb = getColor().getRGBComponents(null);

            gl.glPushMatrix();
            // We translate here once, so that everything else is
            // centered and has origin = 0
            gl.glTranslatef(location.x, location.y, 0);

            // // Obtaining the patch coordinates for Telluride 2015 ------------//
            // TelluridePatchExtractor.setXcoordinate((int) location.x);
            // TelluridePatchExtractor.setYcoordinate((int) location.y);
            // TelluridePatchExtractor.setClusterSize((int) radiusX * 2);
            // TelluridePatchExtractor.setTimeStamp((int) getLastEventTimestamp());
            // TelluridePatchExtractor.setClusterID((int) hashCode());
            // TelluridePatchExtractor.printToFile();
            // //----------------------------------------------------------------//
            if (isVisible()) {
                gl.glColor3fv(rgb, 0);
                gl.glLineWidth(BOX_LINE_WIDTH);
            } else {
                gl.glColor3f(.3f, .3f, .3f);
                gl.glLineWidth(.5f);
            }

            // draw cluster
            if (useEllipticalClusters) {
                DrawGL.drawCircle(gl, 0, 0, radiusX, 15);
                if (updateClustersOnlyFromEventsNearEdge) {
                    gl.glPushAttrib(GL2.GL_CURRENT_BIT);
                    float lw = radius * ellipticalClusterEdgeThickness;
                    rgb[3] = .5f;
                    gl.glColor4fv(rgb, 0);
                    DrawGL.drawCircle(gl, 0, 0, radius - lw, 15);
                    gl.glPopAttrib();
                }
            } else {
                DrawGL.drawBox(gl, 0, 0, (int) radiusX * 2, (int) radiusY * 2, angle); // Radius*2 because we need width and height # tobi casted to int to reduce making gl draw lists in DrawBox
            }
            if ((angle != 0) || dynamicAngleEnabled) {
                DrawGL.drawLine(gl, 0, 0, radiusX, 0, 1);
            }

            // plots a single motion vector which is the number of pixels per second times scaling
            if (showClusterVelocityVector) {
                DrawGL.drawVector(gl, 0, 0, velocityPPS.x, velocityPPS.y, 2, velocityVectorScaling);
            }
            if (showClusterRadius) {
                DrawGL.drawCircle(gl, 0, 0, getAverageEventDistance(), 32);
            }
            gl.glPopMatrix();

            if (showPaths) {
                gl.glPointSize(PATH_POINT_SIZE);
                gl.glBegin(GL.GL_POINTS);
                java.util.List<ClusterPathPoint> points = getPath();
                for (Point2D.Float p : points) {
                    gl.glVertex2f(p.x, p.y);
                }
                gl.glEnd();
            }

            // text annoations on clusters, setup
            final int font = GLUT.BITMAP_HELVETICA_18;
            if (showClusterMass || showClusterEps || showClusterNumber || showClusterVelocity || showClusterEpsPerPx) {
//                gl.glColor3f(1, 1, 1);
                gl.glRasterPos3f(location.x, location.y, 0);
            }

            // cGLUT.glutBitmapString(font, String.format("%.0fdeg", instantaneousAngle*180/Math.PI)); // annotate with
            // instantaneousAngle (debug)
            if (showClusterVelocity) {
                cGLUT.glutBitmapString(font, String.format("v(vx,vy)=%.0f(%.0f,%.0f) pps ", getSpeedPPS(), getVelocityPPS().x, getVelocityPPS().y));
            }
            // if (showClusterRadius) cGLUT.glutBitmapString(font, String.format("rad=%.1f ", getRadius()));
            if (showClusterEps) {
                cGLUT.glutBitmapString(font, String.format("eps=%sHz ", fmt.format(getAvgEventRateHz()))); // annotate
                // the cluster with the event rate
            }
            if (showClusterEpsPerPx) {
                cGLUT.glutBitmapString(font, String.format("eps/px=%sHz ", fmt.format(getAvgEventRateHz() / getArea()))); // annotate
                // the cluster with the event rate per pixel
            }
            if (showClusterNumber) {
                cGLUT.glutBitmapString(font, String.format("#=%d ", hashCode())); // annotate the cluster with hash ID
            }
            if (showClusterMass) {
                cGLUT.glutBitmapString(font, String.format("m=%.1f ", getMassNow(lastUpdateTime)));
            }
        }

        /**
         * Computes a geometrical updateShape factor based on location of a
         * point relative to the vanishing point. If a vanishingPoint pixel has
         * been selected then we compute the perspective from this vanishing
         * point, otherwise it is the top middle pixel, however this perspective
         * computation is only done if highwayPerspectiveEnabled is true.
         *
         * @return updateShape factor, which assumes a flat surface with
         * vanishing point at vanishingPoint. Size grows linearly to 1 at bottom
         * of scene and shrinks to zero at vanishing point. To sides of scene
         * the size grows again.
         */
        protected float getPerspectiveScaleFactor() {
            if (!highwayPerspectiveEnabled) {
                return 1;
            }
            final float MIN_SCALE = 0.1f; // to prevent microclusters that hold only a single pixel
            if (getVanishingPoint() == null) {
                float scale = 1f - (location.y / chip.getSizeY()); // yfrac grows to 1 at bottom of image
                if (scale < MIN_SCALE) {
                    scale = MIN_SCALE;
                }
                return scale;
            } else {
                // updateShape is MIN_SCALE at vanishing point or above and grows linearly to 1 at max size of chip
                int size = chip.getMaxSize();
                float d = (float) location.distance(getVanishingPoint().getX(), getVanishingPoint().getY());
                float scale = d / size;
                if (scale < MIN_SCALE) {
                    scale = MIN_SCALE;
                }
                return scale;
            }
        }

        @Override
        public int getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        /**
         * Updates cluster and generates new output event pointing to the
         * cluster.
         *
         * @param ev the event.
         * @param outItr the output iterator; used to generate new output event
         * pointing to cluster.
         */
        protected void addEvent(BasicEvent ev, OutputEventIterator outItr) {
            addEvent(ev);
            if (!isVisible()) {
                return;
            }
            RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent) outItr.nextOutput();
            oe.copyFrom(ev);
            // oe.setX((short) getLocation().x);
            // oe.setY((short) getLocation().y);
            oe.setCluster(this);
        }

        /**
         * updates cluster by one event. The cluster velocityPPT is updated at
         * the filterPacket level after all events in a packet are added.
         *
         * @param event the event
         */
        public void addEvent(BasicEvent event) {
            if ((event instanceof TypedEvent) && useOnePolarityOnlyEnabled) {
                TypedEvent e = (TypedEvent) event;
                if (useOffPolarityOnlyEnabled) {
                    if (e.type == 1) {
                        return;
                    }
                } else {
                    if (e.type == 0) {
                        return;
                    }
                }
            }

            updateMass(event.timestamp);

            final float m = mixingFactor;
            updatePosition(event, m);
            updateEventRate(event, m);
            updateAverageEventDistance(m);

            // if scaling is enabled, now updateShape the cluster size
            updateShape(event);
            lastUpdateTime = event.timestamp;
        }

        protected void updatePosition(final BasicEvent event, final float m) {
            float m1 = 1 - m;
            // float dt = event.timestamp - lastUpdateTime; // this timestamp may be bogus if it goes backwards in time,
            // we need to check it later
            // if useVelocity is enabled, first update the location using the measured estimate of velocityPPT.
            // this will give predictor characteristic to cluster because cluster will move ahead to the predicted
            // location of
            // the present event
            // don't do this now because the location is already updated by updateClusterLocations() TODO
            // if (useVelocity && dt > 0 && velocityFitter.valid) {
            // location.x = location.x + predictiveVelocityFactor * dt * velocityPPT.x;
            // location.y = location.y + predictiveVelocityFactor * dt * velocityPPT.y;
            // }
            // compute new cluster location by mixing old location with event location by using
            // mixing factor.

            float newX, newY;

            if (event instanceof ApsDvsOrientationEvent) {
                // if event is an orientation event, use the orientation to only move the cluster in a direction
                // perpindicular to
                // the estimated orientation
                ApsDvsOrientationEvent eout = (ApsDvsOrientationEvent) event;
                ApsDvsOrientationEvent.UnitVector d = OrientationEventInterface.unitVectors[(eout.orientation + 2) % 4];
                // calculate projection
                float eventXCentered = event.x - location.x;
                float eventYCentered = event.y - location.y;
                float aDotB = (d.x * eventXCentered) + (d.y * eventYCentered);
                // float aDotA = (d.x * d.x) + (d.y *d.y);
                float division = aDotB; /// aDotA;
                newX = (division * d.x) + location.x;
                newY = (division * d.y) + location.y;
                // location.x = (m1 * location.x + m * newX);
                // location.y = (m1 * location.y + m * newY);
            } else {
                // otherwise, move the cluster in the direction of the event.
                newX = event.x;
                newY = event.y;
            }
            if (!smoothMove) {
                if (useEllipticalClusters && updateClustersOnlyFromEventsNearEdge) {
                    float dist = distanceTo(event);
                    if (dist > radius || dist < radius * (1 - ellipticalClusterEdgeThickness)) {
                        return;
                    }
                }
                location.x = ((m1 * location.x) + (m * newX));
                location.y = ((m1 * location.y) + (m * newY));
            } else {
                float errX = (event.x - location.x);
                float errY = (event.y - location.y);

                // float changerate=1/smoothWeight;
                final float m2 = m / smoothWeight;
                m1 = 1 - m2;

                velocity.x = (m1 * velocity.x) + (m2 * (errX));
                velocity.y = (m1 * velocity.y) + (m2 * (errY));

                location.x = location.x + (velocity.x * smoothIntegral) + (errX * smoothPosition);
                location.y = location.y + (velocity.y * smoothIntegral) + (errX * smoothPosition);
            }
        }

        /**
         * Updates the cluster radius and angle according to distance of event
         * from cluster center, but only if dynamicSizeEnabled or
         * dynamicAspectRatioEnabled or dynamicAngleEnabled.
         *
         * @param event the event to updateShape with
         */
        protected void updateShape(BasicEvent event) {
            if (dynamicSizeEnabled) {
                updateSize(event);
            }
            if (dynamicAspectRatioEnabled) {
                updateAspectRatio(event);
            }
            // PI/2 for vertical positive, -Pi/2 for vertical negative event
            if (dynamicAngleEnabled) {
                updateAngle(event);
            }

            // turn cluster so that it is aligned along velocity
            if (angleFollowsVelocity && velocityValid) {
                // if (!useVelocity) {
                // logg.warning("angleFollowsVelocity cannot be used unless useVelocity=true");
                // return;
                // }
                float velAngle = (float) Math.atan2(velocityPPS.y, velocityPPS.x);
                setAngle(velAngle);
            }
        }

        protected void updateSize(BasicEvent event) {
            float dist = distanceTo(event);
            float oldr = radius;
            float newr = ((1 - mixingFactor) * oldr) + (dist * mixingFactor);
            float f;
            if (newr > (f = defaultClusterRadius * maxSizeScaleRatio)) {
                newr = f;
            } else if (newr < (f = defaultClusterRadius / maxSizeScaleRatio)) {
                newr = f;
            }
            setRadius(newr);
        }

        /**
         * Computes signed distance to-from between two angles with cut at
         * -PI,PI. E.g. if e is from small instantaneousAngle and from=PI-e,
         * to=-PI+e, then angular distance to-from is -2e rather than
         * (PI-e)-(-PI+e)=2PI-2e. This minimum instantaneousAngle difference is
         * useful to push an instantaneousAngle in the correct direction by the
         * correct amount. For this example, we want to push an
         * instantaneousAngle hovering around PI-e. We don't want angles of
         * -PI+e to push the instantaneousAngle from lot, just from bit towards
         * PI. If we have instantaneousAngle <code>from</code> and new
         * instantaneousAngle <code>to</code> and mixing factor m<<1, then new
         * instantaneousAngle <code>c=from+m*angleDistance(from,to)</code>.
         *
         * @param from the first instantaneousAngle
         * @param to the second instantaneousAngle
         * @return the smallest difference to-from, ordinarily positive if
         * to>from
         */
        public float angleDistance(float from, float to) {
            float d = to - from;
            if (d > Math.PI) {
                return d - (float) Math.PI;
            }
            if (d < -Math.PI) {
                return d + (float) Math.PI;
            }
            return d;
        }

        /**
         * Measures distance from cluster center to event.
         *
         * @param event
         * @return distance of this cluster to the event in Manhattan (cheap)
         * metric (sum of absolute values of x and y distance).
         */
        public float distanceTo(BasicEvent event) {
            final float dx = event.x - location.x;
            final float dy = event.y - location.y;

            return distanceMetric(dx, dy);
        }

        /**
         * Returns the implemented distance metric which is the Manhattan
         * distance for speed. This is the sum of abs(dx)+abs(dy).
         *
         * @param dx the x distance
         * @param dy the y distance
         * @return abs(dx)+abs(dy)
         */
        public float distanceMetric(float dx, float dy) {
            if (useEllipticalClusters) {
                return (float) sqrt(dx * dx + dy * dy);
            } else {
                return ((dx > 0) ? dx : -dx) + ((dy > 0) ? dy : -dy);
            }
        }

        /**
         * Measures distance in x direction, accounting for instantaneousAngle
         * of cluster and predicted movement of cluster.
         *
         * @param event
         * @return distance in x direction of this cluster to the event, where x
         * is measured along instantaneousAngle=0.
         */
        protected float distanceToX(BasicEvent event) {
            int dt = event.timestamp - lastUpdateTime;
            float distance;
            float dx = (((event.x - location.x) + (velocityPPT.x * (dt))) * cosAngle);
            float dy = (((event.y - location.y) + (velocityPPT.y * (dt))) * sinAngle);
            if (useEllipticalClusters) {
                distance = (float) sqrt(dx * dx + dy * dy);
            } else {
                distance = Math.abs(dx) + Math.abs(dy);
            }
            return distance;
        }

        /**
         * Measures distance in y direction, accounting for instantaneousAngle
         * of cluster, where y is measured along instantaneousAngle=Pi/2 and
         * predicted movement of cluster
         *
         * @param event
         * @return distance in y direction of this cluster to the event
         */
        protected float distanceToY(BasicEvent event) {
            int dt = event.timestamp - lastUpdateTime;
            float distance = Math.abs((((event.y - location.y) + (velocityPPT.y * (dt))) * cosAngle)
                    - (((event.x - location.x) + (velocityPPT.x * (dt))) * sinAngle));
            /// float distance = Math.abs (event.y - location.y);
            return distance;
        }

        /**
         * Computes and returns distance to another cluster.
         *
         * @param c
         * @return distance of this cluster to the other cluster in pixels.
         */
        public final float distanceTo(Cluster c) {// TODO doesn't use predicted location of clusters, only present
            // locations

            float dx = c.location.x - location.x;
            float dy = c.location.y - location.y;
            return distanceMetric(dx, dy);
        }

        /**
         * Computes and returns the angle of this cluster's velocityPPT vector
         * to another cluster's velocityPPT vector.
         *
         * @param c the other cluster.
         * @return the angle in radians, from 0 to PI in radians. If either
         * cluster has zero velocityPPT, returns 0.
         */
        protected final float velocityAngleToRad(Cluster c) {
            float s1 = getSpeedPPS(), s2 = c.getSpeedPPS();
            if ((s1 == 0) || (s2 == 0)) {
                return 0;
            }
            float dot = (velocityPPS.x * c.velocityPPS.x) + (velocityPPS.y * c.velocityPPS.y);
            float angleRad = (float) Math.acos(dot / s1 / s2);
            return angleRad;
        }

        /**
         * Computes and returns the total absolute distance (shortest path)
         * traveled in pixels since the birth of this cluster
         *
         * @return distance in pixels since birth of cluster
         */
        public float getDistanceFromBirth() {
            double dx = location.x - birthLocation.x;
            double dy = location.y - birthLocation.y;
            return (float) sqrt((dx * dx) + (dy * dy));
        }

        /**
         * @return signed distance in Y from birth.
         */
        public float getDistanceYFromBirth() {
            return location.y - birthLocation.y;
        }

        /**
         * @return signed distance in X from birth.
         */
        public float getDistanceXFromBirth() {
            return location.x - birthLocation.x;
        }

        /**
         * Corrects for perspective looking down on a flat surface towards a
         * horizon.
         *
         * @return the absolute size of the cluster after perspective
         * correction, i.e., a large cluster at the bottom of the scene is the
         * same absolute size as a smaller cluster higher up in the scene.
         */
        public float getRadiusCorrectedForPerspective() {
            float scale = 1 / getPerspectiveScaleFactor();
            return radius * scale;
        }

        /**
         * The effective radius of the cluster depends on whether
         * highwayPerspectiveEnabled is true or not and also on the surround of
         * the cluster. The getRadius value is not used directly since it is a
         * parameter that is combined with perspective location and aspect
         * ratio.
         *
         * @return the cluster radius.
         */
        @Override
        public final float getRadius() {
            return radius;
        }

        /**
         * The effective area of the cluster depends on its fixed size or
         * dynamically-determined radius, which might depend on
         * surroundInhibitionEnabled.
         *
         * @return the cluster area in pixels: radius*radius*4.
         */
        @Override
        public final float getArea() {
            return radius * radius * 4;
        }

        /**
         * This method sets the radius field according to the
         * highwayPerspectiveEnabled and perspective point, along with the
         * cluster aspect ratio. The radius of a cluster is the distance in
         * pixels from the cluster center that is the putative model size. If
         * highwayPerspectiveEnabled is true, then the radius is set to a fixed
         * size depending on the defaultClusterRadius and the perspective
         * location of the cluster and r is ignored. The aspect ratio parameters
         * radiusX and radiusY of the cluster are also set.
         *
         * @param r the radius in pixels
         */
        public void setRadius(float r) {
            if (!highwayPerspectiveEnabled) {
                radius = r;
            } else {
                radius = defaultClusterRadius * getPerspectiveScaleFactor();
            }
            radiusX = radius / aspectRatio;
            radiusY = radius * aspectRatio;
        }

        /**
         * Sets the flag of cluster visibility (check is separated from check
         * for efficiency because this operation is costly.) birthLocation and
         * hasObtainedSupport flags are set by this check.
         * <p>
         * A cluster is set visible if both of following are true
         * numEvents>thresholdMassForVisibleCluster AND
         * getMassNow()>thresholdMassForVisibleCluster.
         * <p>
         * Also, if useVelocity is set, then it must be that speed <
         * thresholdVelocityForVisibleCluster
         *
         * @see #isVisible()
         * @see #getMassNow()
         * @param t the current timestamp
         * @return true if cluster is visible
         */
        public boolean updateVisibility(int t) {
            boolean ret = true;
            // TODO: In the tooltip it is promised that the thresholdMassForVisibleCluster is
            // checking the MASS of the cluster to determine if its visible. However as far
            // as I see here this is not the case! Instead we check only for the number of Events this cluster has
            // gathered
            if (!useEventRatePerPixelForVisibilty) {
                if (getMassNow(t) < thresholdMassForVisibleCluster) {
                    ret = false;
                }
            } else { // based on average event rate per pixel
                if (getAvgEventRateHz() / getArea() < thresholdEventRatePerPixelForVisibleClusterHz) {
                    ret = false;
                }
            }
            if (useVelocity && thresholdVelocityForVisibleCluster > 0) {
                double speed = (sqrt((velocityPPT.x * velocityPPT.x) + (velocityPPT.y * velocityPPT.y)) * 1e6)
                        / AEConstants.TICK_DEFAULT_US; // speed is in pixels/sec
                if (speed < thresholdVelocityForVisibleCluster) {
                    ret = false;
                }
            }
            if (!hasObtainedSupport && ret) {
                birthLocation.x = location.x;
                birthLocation.y = location.y; // reset location of birth to presumably less noisy current location.
            }
            hasObtainedSupport = (hasObtainedSupport || ret);
            if (ret && !visibilityFlag) {
                onBecomingVisible();
            }
            visibilityFlag = ret;
            return ret;
        }

        /**
         * Returns the flag that marks cluster visibility. This flag is set by
         * <code>updateVisibility</code>. This flag flags whether cluster has
         * gotten enough support.
         *
         * @return true if cluster has obtained enough support.
         * @see #updateVisibility
         */
        @Override
        final public boolean isVisible() {
            return visibilityFlag;
        }

        /**
         * Flags whether this cluster was ever 'visible', i.e. had ever obtained
         * sufficient support to be marked visible.
         *
         * @return true if it was ever visible; i.e. hasObtainedSupport==true.
         */
        final public boolean isWasEverVisible() {
            return hasObtainedSupport;
        }

        /**
         * @return lifetime of cluster in timestamp ticks, measured as
         * lastUpdateTime-firstEventTimestamp. Note that lifetime only is
         * increased with updates, so a cluster that is never updated never
         * increases its lifetime.
         */
        final public int getLifetime() {
            return lastUpdateTime - firstEventTimestamp;
        }

        /**
         * Factory method that subclasses can override to create custom path
         * points, e.g. for storing different statistics
         *
         * @return a new ClusterPathPoint with x,y,t set. Other fields must be
         * set using methods.
         */
        protected ClusterPathPoint createPoint(float x, float y, int t) {
            return ClusterPathPoint.createPoint(x, y, t);
        }

        /**
         * Updates path (historical) information for this cluster, including
         * cluster velocity (by calling updateVelocity()). The path is trimmed
         * to maximum length if logging is not enabled.
         *
         * @param t current timestamp.
         */
        public void updatePath(int t) {
            if (!pathsEnabled && !useVelocity) {
                return;
            }
            if (numEvents == previousNumEvents) {
                return; // don't append point unless we had events that caused change in path (aside from prediction from
                // velocityPPT)
            }
            ClusterPathPoint p = createPoint(location.x, location.y, t);
            p.setnEvents(numEvents - previousNumEvents);
            path.add(p);
            previousNumEvents = numEvents;
            updateVelocity();

            if (path.size() > pathLength) {
                if (!logDataEnabled || (clusterLoggingMethod != ClusterLoggingMethod.LogClusters)) {
                    // path.remove(path.getString(0)); // if we're logging cluster paths, then save all cluster history
                    // regardless of pathLength
                    path.remove(path.get(0)); // if we're logging cluster paths, then save all cluster history
                    // regardless of pathLength
                }
            }
        }

        /**
         * Updates velocityPPT, velocityPPS of cluster and last path point
         * lowpass filtered velocity.
         */
        protected void updateVelocity() {
            if (path.size() < 2) {
                return;
            }

            // update velocityPPT of cluster using last two path points
            Iterator<ClusterPathPoint> itr = path.descendingIterator();
            ClusterPathPoint plast = itr.next();
            int nevents = plast.getNEvents();
            ClusterPathPoint pfirst = itr.next();
            while ((nevents < thresholdMassForVisibleCluster) && itr.hasNext()) {
                nevents += pfirst.getNEvents();
                pfirst = itr.next();
            }
            if (nevents < thresholdMassForVisibleCluster) {
                return;
            }

            int dt = plast.t - pfirst.t;
            float vx = (plast.x - pfirst.x) / dt;
            float vy = (plast.y - pfirst.y) / dt;
            velocityPPT.x = vxFilter.filter(vx, lastEventTimestamp);
            velocityPPT.y = vyFilter.filter(vy, lastEventTimestamp);
            if (!Float.isNaN(frictionTauMs)) {
                float factor = (float) Math.exp(-dt / (frictionTauMs * 1000));
                velocityPPT.x = velocityPPT.x * factor;
                velocityPPT.y = velocityPPT.y * factor;
            }
            if (plast.velocityPPT == null) {
                plast.velocityPPT = new Point2D.Float(velocityPPT.x, velocityPPT.y);
            } else {
                plast.velocityPPT.setLocation(velocityPPT.x, velocityPPT.y); // = new Point2D.Float(velocityPPT.x,
                // velocityPPT.y);
            }
            // float m1=1-velocityMixingFactor;
            // velocityPPT.x=m1*velocityPPT.x+velocityMixingFactor*vx;
            // velocityPPT.y=m1*velocityPPT.y+velocityMixingFactor*vy;
            velocityPPS.x = velocityPPT.x * VELPPS_SCALING;
            velocityPPS.y = velocityPPT.y * VELPPS_SCALING;
            setVelocityValid(true);
        }

        @Override
        public String toString() {
            return String.format(
                    "Cluster number=%d numEvents=%d location(x,y)=%d %d timestamp=%d radius(x,y)=%.1f %.1f angle=%.1f mass=%.1f lifetime=%d visible=%s speedPPS=%.2f",
                    getClusterNumber(), numEvents, (int) location.x, (int) location.y, lastUpdateTime, radiusX, radiusY, angle, getMass(), getLifetime(),
                    isVisible(), getSpeedPPS());
        }

        @Override
        public java.util.List<ClusterPathPoint> getPath() {
            return path;
        }

        /**
         * Returns velocityPPT of cluster in pixels per second.
         *
         * @return averaged velocityPPT of cluster in pixels per second.
         * <p>
         * The method of measuring velocityPPT is based on a linear regression
         * of a number of previous cluster locations.
         * @see #getVelocityPPT()
         */
        @Override
        public Point2D.Float getVelocityPPS() {
            return velocityPPS;
            /*
			 * old method for velocityPPT estimation is as follows
			 * The velocityPPT is instantaneously
			 * computed from the movement of the cluster caused by the last event, then this velocityPPT is mixed
			 * with the the old velocityPPT by the mixing factor. Thus the mixing factor is appplied twice: once for
			 * moving
			 * the cluster and again for changing the velocityPPT.
             */
        }

        /**
         * Computes and returns speed of cluster in pixels per second.
         *
         * @return speed in pixels per second.
         */
        @Override
        public float getSpeedPPS() {
            return (float) sqrt((velocityPPS.x * velocityPPS.x) + (velocityPPS.y * velocityPPS.y));
        }

        /**
         * Computes and returns speed of cluster in pixels per timestamp tick.
         *
         * @return speed in pixels per timestamp tick.
         */
        public float getSpeedPPT() {
            return (float) sqrt((velocityPPT.x * velocityPPT.x) + (velocityPPT.y * velocityPPT.y));
        }

        public float getMeasuredAspectRatio() {
            return averageEventYDistance / averageEventXDistance;
        }

        public float getMeasuredArea() {
            return averageEventYDistance * averageEventXDistance;
        }

        public float getMeasuredRadius() {
            return (float) sqrt((averageEventYDistance * averageEventYDistance) + (averageEventXDistance * averageEventXDistance));
        }

        public float getMeasuredAverageEventRate() {
            return avgEventRateHz / radius;
        }

        /**
         * Computes the size of the cluster based on average event distance and
         * adjusted for perpective scaling. A large cluster at bottom of screen
         * is the same size as a smaller cluster closer to horizon
         *
         * @return size of cluster in pixels
         */
        public float getMeasuredSizeCorrectedByPerspective() {
            float scale = getPerspectiveScaleFactor();
            if (scale <= 0) {
                return averageEventDistance;
            }
            return averageEventDistance / scale;
        }

        /**
         * Sets color according to measured cluster size
         */
        public void setColorAccordingToSize() {
            float s = getMeasuredSizeCorrectedByPerspective();
            float hue = (2 * s) / chip.getMaxSize();
            if (hue > 1) {
                hue = 1;
            }
            setColor(Color.getHSBColor(hue, 1f, 1f));
        }

        /**
         * Sets color according to age of cluster
         */
        public void setColorAccordingToAge() {
            float brightness = Math.max(0f, Math.min(1f, getLifetime() / FULL_BRIGHTNESS_LIFETIME));
            Color c = Color.getHSBColor(.5f, 1f, brightness);
            setColor(c);
        }

        // public void setColorAccordingToClass(){
        // float s=getMeasuredSizeCorrectedByPerspective();
        // float hue=0.5f;
        // if(s>getClassifierThreshold()){
        // hue=.3f;
        // }else{
        // hue=.8f;
        // }
        // Color c=Color.getHSBColor(hue,1f,1f);
        // setColor(c);
        // }
        public void setColorAutomatically() {
            if (isColorClustersDifferentlyEnabled()) {
                // color is set on object creation, don't change it
            } else {
                setColorAccordingToSize();
            }
            // else if(!isClassifierEnabled()){
            // setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if
            // this is enabled
            // // setColorAccordingToAge(); // sets color according to how long the cluster has existed
            // }else{ // classifier enabled
            // setColorAccordingToClass();
            // }
        }

        /**
         * onPruning is called when the cluster is about to be pruned from the
         * list of clusters. By default no special action is taken. Subclasses
         * can override this method to take a special action on pruning.
         */
        protected void onPruning() {
        }

        /**
         * onBecomingVisible is called when the cluster has first become
         * visible. By default no special action is taken. Subclasses can
         * override this method to take a special action on pruning.
         */
        protected void onBecomingVisible() {
        }

        /**
         * Determines if this cluster overlaps the center of another cluster.
         *
         * @param c2 the other cluster
         * @return true if overlapping.
         */
        protected boolean isOverlappingCenterOf(Cluster c2) {
            final boolean overlapping = distanceTo(c2) < (getRadius() + c2.getRadius());
            return overlapping;
        }

        /**
         * Does a moving or rolling linear regression (a linear fit) on updated
         * PathPoint data. The new data point replaces the oldest data point.
         * Summary statistics holds the rollling values and are updated by
         * subtracting the oldest point and adding the newest one. From
         * <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia
         * article on Ordinary least squares</a>.
         * <p>
         * If velocityPPT cannot be estimated (e.g. due to only 2 identical
         * points) it is not updated.
         *
         * @author tobi
         */
        private class RollingVelocityFitter {

            private static final int LENGTH_DEFAULT = 5;
            private int length = LENGTH_DEFAULT;
            private float st = 0, sx = 0, sy = 0, stt = 0, sxt = 0, syt = 0, den = 1; // summary stats
            private ArrayList<ClusterPathPoint> points;
            private float xVelocity = 0, yVelocity = 0;
            private boolean valid = false;
            private int nPoints = 0;

            /**
             * Creates a new instance of RollingLinearRegression
             */
            public RollingVelocityFitter(ArrayList<ClusterPathPoint> points, int length) {
                this.points = points;
                this.length = length;
            }

            @Override
            public String toString() {
                return String.format("RollingVelocityFitter: \n" + "valid=%s nPoints=%d\n" + "xVel=%f, yVel=%f\n"
                        + "st=%f sx=%f sy=%f, sxt=%f syt=%f den=%f", valid, nPoints, xVelocity, yVelocity, st, sx, sy, sxt, syt, den);
            }

            /**
             * Updates estimated velocityPPT based on last point in path. If
             * velocityPPT cannot be estimated it is not updated.
             *
             * @param t current timestamp.
             */
            private synchronized void update() {
                int n = points.size();
                if (n < 1) {
                    return;
                }
                ClusterPathPoint p = points.get(n - 1); // take last point
                if (p.getNEvents() == 0) {
                    return;
                }
                nPoints++;
                if (n > length) {
                    removeOldestPoint(); // discard data beyond range length
                }
                n = n > length ? length : n; // n grows to max length
                float dt = p.t - firstEventTimestamp; // t is time since cluster formed, limits absolute t for numerics
                st += dt;
                sx += p.x;
                sy += p.y;
                stt += dt * dt;
                sxt += p.x * dt;
                syt += p.y * dt;
                // if(n<length) return; // don't estimate velocityPPT until we have all necessary points, results very
                // noisy and send cluster off to infinity very often, would give NaN
                den = ((n * stt) - (st * st));
                if ((n >= length) && (den != 0)) {
                    valid = true;
                    xVelocity = ((n * sxt) - (st * sx)) / den;
                    yVelocity = ((n * syt) - (st * sy)) / den;
                } else {
                    valid = false;
                }
                // System.out.println(this.toString());
            }

            private void removeOldestPoint() {
                // takes away from summary states the oldest point
                ClusterPathPoint p = points.get(points.size() - length - 1);
                // if points has 5 points (0-4), length=3, then remove points(5-3-1)=points(1) leaving 2-4 which is
                // correct
                float dt = p.t - firstEventTimestamp;
                st -= dt;
                sx -= p.x;
                sy -= p.y;
                stt -= dt * dt;
                sxt -= p.x * dt;
                syt -= p.y * dt;
            }

            // <editor-fold defaultstate="collapsed" desc="getter-setter for --Length--">
            int getLength() {
                return length;
            }

            /**
             * Sets the window length. Clears the accumulated data.
             *
             * @param length the number of points to fit
             * @see #LENGTH_DEFAULT
             */
            synchronized void setLength(int length) {
                this.length = length;
            }
            // </editor-fold>

            public float getXVelocity() {
                return xVelocity;
            }

            public float getYVelocity() {
                return yVelocity;
            }

            // <editor-fold defaultstate="collapsed" desc="getter-setter for --Valid--">
            /**
             * Returns true if the last estimate resulted in a valid measurement
             * (false when e.g. there are only two identical measurements)
             */
            public boolean isValid() {
                return valid;
            }

            public void setValid(boolean valid) {
                this.valid = valid;
            }
            // </editor-fold>
        } // rolling velocityPPT fitter

        /**
         * Returns first timestamp of cluster; this time is updated when cluster
         * becomes visible.
         *
         * @return timestamp of birth location.
         */
        public int getBirthTime() {
            return firstEventTimestamp;
        }

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --NumEvents--">
        /**
         * Total number of events collected by this cluster.
         *
         * @return the numEvents
         */
        @Override
        public int getNumEvents() {
            return numEvents;
        }

        /**
         * Sets count of events.
         *
         * @param numEvents the numEvents to set
         */
        public void setNumEvents(int numEvents) {
            this.numEvents = numEvents;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --Color--">
        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --Location--">
        @Override
        final public Point2D.Float getLocation() {
            return location;
        }

        public void setLocation(Point2D.Float NewLocation) {
            this.location = NewLocation;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AverageEventDistance--">
        /**
         * @return average (mixed by {@link #mixingFactor}) distance from events
         * to cluster center
         */
        public float getAverageEventDistance() {
            return averageEventDistance;
        }

        /**
         * @see #getAverageEventDistance
         */
        public void setAverageEventDistance(float averageEventDistance) {
            this.averageEventDistance = averageEventDistance;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AverageXDistance--">
        public float getAverageEventXDistance() {
            return averageEventXDistance;
        }

        public void setAverageEventXDistance(float averageEventXDistance) {
            this.averageEventXDistance = averageEventXDistance;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AverageEventYDistance--">
        public float getAverageEventYDistance() {
            return averageEventYDistance;
        }

        public void setAverageEventYDistance(float averageEventYDistance) {
            this.averageEventYDistance = averageEventYDistance;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --ClusterNumber--">
        public int getClusterNumber() {
            return clusterNumber;
        }

        public void setClusterNumber(int clusterNumber) {
            this.clusterNumber = clusterNumber;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AvgISI--">
        /**
         * @return average ISI for this cluster in timestamp ticks. Average is
         * computed using cluster location mising factor.
         */
        public float getAvgISI() {
            return avgISI;
        }

        public void setAvgISI(float avgISI) {
            this.avgISI = avgISI;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AvgEventRate-">
        /**
         * @return average event rate in Hz. Average is computed using
         * eventRatePerPixelLowpassTauS.
         * <p>
         * Note that this measure emphasizes the high spike rates because a few
         * events in rapid succession can rapidly push up the average rate.
         */
        public float getAvgEventRateHz() {
            return avgEventRateHz;
        }

        public void setAvgEventRateHz(float avgEventRateHz) {
            this.avgEventRateHz = avgEventRateHz;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --AspectRatio--">
        public float getAspectRatio() {
            return aspectRatio;
        }

        /**
         * Aspect ratio is 1 for square cluster and in general is height/width.
         *
         * @param aspectRatio
         */
        public void setAspectRatio(float aspectRatio) {
            this.aspectRatio = aspectRatio;
            // float radiusX=radius/aspectRatio, radiusY=radius*aspectRatio;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --Angle--">
        /**
         * Angle of cluster, in radians.
         *
         * @return in radians.
         */
        public float getAngle() {
            return angle;
        }

        /**
         * Angle of cluster is zero by default and increases CCW from 0 lying
         * along the x axis. Also sets internal cosAngle and sinAngle.
         *
         * @param angle in radians.
         */
        public void setAngle(float angle) {
            if (this.angle != angle) { // save some cycles if unchanged
                this.angle = angle;
                cosAngle = (float) Math.cos(angle);
                sinAngle = (float) Math.sin(angle);
                initialAngle = ((1 - mixingFactor) * initialAngle) + (mixingFactor * angle);
            }
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --BirthLocation--">
        /**
         * Returns birth location of cluster: initially the first event and
         * later, after cluster becomes visible, it is the location when it
         * becomes visible, which is presumably less noisy.
         *
         * @return x,y location.
         */
        public Point2D.Float getBirthLocation() {
            return birthLocation;
        }

        public void setBirthLocation(Point2D.Float birthLocation) {
            this.birthLocation = birthLocation;
        }
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="getter-setter for --VelocityValid--">
        /**
         * This flogg is set true after a velocityPPT has been computed for the
         * cluster. This may take several packets.
         *
         * @return true if valid.
         */
        public boolean isVelocityValid() {
            return velocityValid;
        }

        public void setVelocityValid(boolean velocityValid) {
            this.velocityValid = velocityValid;
        }
        // </editor-fold>
    } // Cluster

    /**
     * Returns list of all cluster, visible and invisible
     *
     * @return list of clusters
     */
    @Override
    public LinkedList<RectangularClusterTracker.Cluster> getClusters() {
        return this.clusters;
    }

    /**
     * Returns list of actually visible clusters that have received enough
     * support and pass other visibility tests. Updated every packet or update
     * interval.
     *
     * @return the visibleClusters
     */
    public LinkedList<Cluster> getVisibleClusters() {
        return visibleClusters;
    }

    /**
     * Returns the list of clusters that will be pruned because they have not
     * received enough support (enough events in their region of interest) or
     * because they have been merged with other clusters.
     *
     * @return the list of pruned clusters.
     */
    public LinkedList<RectangularClusterTracker.Cluster> getPruneList() {
        return this.pruneList;
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    synchronized public void resetFilter() {
        // before reset, logg all the clusters that remain
        if (logDataEnabled && (clusterLoggingMethod == ClusterLoggingMethod.LogClusters)) {
            clusterLogger.logClusterHistories(clusters);
        }
        clusters.clear();
        clusterCounter = 0;
        logFrameNumber = 0;
        averageVelocityPPT.x = 0;
        averageVelocityPPT.y = 0;
        fastClusterFinder.reset();
        initialAngle = 0;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == this) {
            UpdateMessage msg = (UpdateMessage) arg;
            updateClusterList(msg.timestamp);
        }
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }

        fmt.setPrecision(1); // digits after decimel point
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel,
        // at LL corner
        if (gl == null) {
            log.warning("null GL in RectangularClusterTracker.annotate");
            return;
        }
        // mouse selection later on
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();

        // vanishing point if non-null
        if (vanishingPoint != null) {
            gl.glColor3f(0, 0, 1);
            gl.glPointSize(10);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f((float) vanishingPoint.getX(), (float) vanishingPoint.getY());
            gl.glEnd();
        }
        // clusters
        try {
            gl.glPushMatrix();
            {
                for (Cluster c : clusters) {
                    if (showAllClusters || c.isVisible()) {
                        c.draw(drawable);
                    }
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning("concurrent modification of cluster list while drawing " + clusters.size() + " clusters");
        } finally {
            gl.glPopMatrix();
        }
    }

    // The following methods need to be implement for 'MouseListener' we only use 'mouseClicked'
    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        setVanishingPoint(canvas.getPixelFromMouseEvent(e));
        if (glCanvas != null) {
            glCanvas.removeMouseListener(this);
        }
    }

    /**
     * Speeds up finding the nearest cluster to an event.
     */
    protected class FastClusterFinder {

        /**
         * How much the map is subsampled in bits relative to the pixel array
         */
        final int SUBSAMPLE_BY = 2;
        private Cluster[][] grid = null;
        private HashMap<Cluster, Point> map = new HashMap();
        int nx = 0, ny = 0;

        void init() {
            nx = chip.getSizeX() >> SUBSAMPLE_BY;
            ny = chip.getSizeY() >> SUBSAMPLE_BY;
            grid = new Cluster[nx + 1][ny + 1];
        }

        /**
         * Finds the nearest cluster to an event. If the cluster has been cached
         * in the grid map it is returned, otherwise either nearest or first
         * cluster is returned depending on useNearestCluster flag.
         *
         * @param e the event
         * @return the nearest cluster or null
         */
        protected Cluster findClusterNear(BasicEvent e) {
            Cluster c = null;
            c = grid[(e.x) >>> SUBSAMPLE_BY][(e.y) >>> SUBSAMPLE_BY];
            if (c == null) {
                if (useNearestCluster) {
                    c = getNearestCluster(e);
                } else {
                    c = getFirstContainingCluster(e); // find cluster that event falls within (or also within surround
                    // if scaling enabled)
                }
            }
            return c;
        }

        /**
         * updates the lookup table for this cluster.
         *
         * @param c the cluster to update
         */
        protected void update(Cluster c) {
            removeCluster(c);
            int x = (int) (c.location.x) >> SUBSAMPLE_BY;
            if (x < 0) {
                x = 0;
            } else if (x >= nx) {
                x = nx - 1;
            }
            int y = (int) (c.location.y) >> SUBSAMPLE_BY;
            if (y < 0) {
                y = 0;
            } else if (y >= ny) {
                y = ny - 1;
            }
            grid[x][y] = c;
            map.put(c, new Point(x, y));
        }

        /**
         * Clears the map
         */
        protected void reset() {
            if (grid == null) {
                init();
            } else {
                for (Cluster[] ca : grid) {
                    Arrays.fill(ca, null);
                }
            }
        }

        /**
         * Removes the cluster
         *
         * @param c the cluster to be removed
         */
        protected void removeCluster(Cluster c) {
            if (map.containsKey(c)) {
                Point p = map.get(c);
                grid[p.x][p.y] = null;
                map.remove(c);
            }
        }
    }

    public void doSelectVanishingPoint() {
        if (glCanvas == null) {
            return;
        }
        glCanvas.addMouseListener(this);
    }

    public void doToggleOnLogging() {
        setLogDataEnabled(true);
    }

    public void doToggleOffLogging() {
        setLogDataEnabled(false);
    }

    public void doShowFolderInDesktop() {
        if (!Desktop.isDesktopSupported()) {
            log.warning("Sorry, desktop operations are not supported");
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            File f = (clusterLogger != null && clusterLogger.file != null) ? clusterLogger.file : null;
            if (f != null && f.exists()) {
                desktop.open(f.getAbsoluteFile().getParentFile());
            } else {
                log.warning("no log file yet");
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SurroundInhibitionCost--">
    public float getSurroundInhibitionCost() {
        return surroundInhibitionCost;
    }

    public void setSurroundInhibitionCost(float v) {
        this.surroundInhibitionCost = v;
        putFloat("surroundInhibitionCost", v);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --EnableClusterExitPurging--">
    /**
     * @return the enableClusterExitPurging
     */
    public boolean isEnableClusterExitPurging() {
        return enableClusterExitPurging;
    }

    /**
     * Enables rapid purging of clusters that hit the edge of the scene.
     *
     * @param enableClusterExitPurging the enableClusterExitPurging to set
     */
    public void setEnableClusterExitPurging(boolean enableClusterExitPurging) {
        this.enableClusterExitPurging = enableClusterExitPurging;
        putBoolean("enableClusterExitPurging", enableClusterExitPurging);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterRadius--">
    /**
     * @return the showClusterRadius
     */
    public boolean isShowClusterRadius() {
        return showClusterRadius;
    }

    /**
     * @param showClusterRadius the showClusterRadius to set
     */
    public void setShowClusterRadius(boolean showClusterRadius) {
        this.showClusterRadius = showClusterRadius;
        putBoolean("showClusterRadius", showClusterRadius);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --HighwayPerspectiveEnabled--">
    public boolean isHighwayPerspectiveEnabled() {
        return highwayPerspectiveEnabled;
    }

    public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled) {
        this.highwayPerspectiveEnabled = highwayPerspectiveEnabled;
        putBoolean("highwayPerspectiveEnabled", highwayPerspectiveEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter - Min/Max for --MixingFactor--">
    public float getMixingFactor() {
        return mixingFactor;
    }

    public void setMixingFactor(float mixingFactor) {
        if (mixingFactor < 0) {
            mixingFactor = 0;
        }
        if (mixingFactor > 1) {
            mixingFactor = 1f;
        }
        this.mixingFactor = mixingFactor;
        putFloat("mixingFactor", mixingFactor);
    }

    /**
     * Implementing getMin and getMax methods constructs a slider control for
     * the mixing factor in the FilterPanel.
     *
     * @return 0
     */
    public float getMinMixingFactor() {
        return 0;
    }

    /**
     * Constrains upper value of slider.
     *
     * @return max value for slider - text box can chooser larger value.
     */
    public float getMaxMixingFactor() {
        return 0.2f;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter - Min/Max for --Surround--">
    /**
     * @see #setSurround
     */
    public float getSurround() {
        return surround;
    }

    /**
     * sets updateShape factor of radius that events outside the cluster size
     * can affect the size of the cluster if
     * {@link #setDynamicSizeEnabled scaling} is enabled.
     *
     * @param surround the updateShape factor, constrained >1 by setter. radius
     * is multiplied by this to determine if event is within surround.
     */
    public void setSurround(float surround) {
        if (surround < 1) {
            surround = 1;
        }
        this.surround = surround;
        putFloat("surround", surround);
    }

    public float getMinSurround() {
        return 1;
    }

    public float getMaxSurround() {
        return 30;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PathsEnabled--">
    /**
     * @see #setPathsEnabled
     */
    public boolean isPathsEnabled() {
        return pathsEnabled;
    }

    /**
     * Enable cluster history paths. The path of each cluster is stored as a
     * list of points at the end of each cluster list update. This option is
     * required (and set true) if useVelocity is set true.
     *
     * @param pathsEnabled true to show the history of the cluster locations on
     * each packet.
     */
    public void setPathsEnabled(boolean pathsEnabled) {
        boolean old = this.pathsEnabled;
        this.pathsEnabled = pathsEnabled;
        getSupport().firePropertyChange("pathsEnabled", old, pathsEnabled);
        putBoolean("pathsEnabled", pathsEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --DynamicSizeEnabled--">
    /**
     * @see #setDynamicSizeEnabled
     */
    public boolean getDynamicSizeEnabled() {
        return dynamicSizeEnabled;
    }

    /**
     * Enables cluster size scaling. The clusters are dynamically resized by the
     * distances of the events from the cluster center. If most events are far
     * from the cluster then the cluster size is increased, but if most events
     * are close to the cluster center than the cluster size is decreased. The
     * size change for each event comes from mixing the old size with a the
     * event distance from the center using the mixing factor.
     *
     * @param dynamicSizeEnabled true to enable scaling of cluster size
     */
    public void setDynamicSizeEnabled(boolean dynamicSizeEnabled) {
        this.dynamicSizeEnabled = dynamicSizeEnabled;
        putBoolean("dynamicSizeEnabled", dynamicSizeEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ColorClustersDifferentlyEnabled--">
    /**
     * @see #setColorClustersDifferentlyEnabled
     */
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }

    /**
     * @param colorClustersDifferentlyEnabled true to color each cluster a
     * different color. false to color each cluster by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
        this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
        putBoolean("colorClustersDifferentlyEnabled", colorClustersDifferentlyEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FilterEventsEnabled--">
    /**
     * @return the filterEventsEnabled
     */
    public boolean isFilterEventsEnabled() {
        return filterEventsEnabled;
    }

    /**
     * @param filterEventsEnabled the filterEventsEnabled to set
     */
    synchronized public void setFilterEventsEnabled(boolean filterEventsEnabled) {
        boolean old = this.filterEventsEnabled;
        this.filterEventsEnabled = filterEventsEnabled;
        putBoolean("filterEventsEnabled", filterEventsEnabled);
        getSupport().firePropertyChange("filterEventsEnabled", old, filterEventsEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseOnePolarityOnlyEnabled--">
    public boolean isUseOnePolarityOnlyEnabled() {
        return useOnePolarityOnlyEnabled;
    }

    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
        this.useOnePolarityOnlyEnabled = useOnePolarityOnlyEnabled;
        putBoolean("useOnePolarityOnlyEnabled", useOnePolarityOnlyEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseOffPolarityOnlyEnabled--">
    public boolean isUseOffPolarityOnlyEnabled() {
        return useOffPolarityOnlyEnabled;
    }

    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
        this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
        putBoolean("useOffPolarityOnlyEnabled", useOffPolarityOnlyEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ClusterMassDecayTauUs--">
    /**
     * lifetime of cluster in ms without support
     */
    public final int getClusterMassDecayTauUs() {
        return clusterMassDecayTauUs;
    }

    /**
     * lifetime of cluster in ms without support
     */
    public void setClusterMassDecayTauUs(final int clusterMassDecayTauUs) {
        this.clusterMassDecayTauUs = clusterMassDecayTauUs;
        putInt("clusterMassDecayTauUs", clusterMassDecayTauUs);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter - Min/Max for --ClusterSize--">
    /**
     * max distance from cluster to event as fraction of size of array
     */
    public final float getClusterSize() {
        return clusterSize;
    }

    /**
     * sets max distance from cluster center to event as fraction of maximum
     * size of chip pixel array. e.g. clusterSize=0.5 and 128x64 array means
     * cluster has radius of 0.5*128=64 pixels.
     *
     * @param clusterSize
     */
    synchronized public void setClusterSize(float clusterSize) {
        if (clusterSize > 1f) {
            clusterSize = 1f;
        }
        if (clusterSize < 0) {
            clusterSize = 0;
        }
        float old = this.clusterSize;
        defaultClusterRadius = chip.getMaxSize() * clusterSize;
        this.clusterSize = clusterSize;
        for (Cluster c : clusters) {
            c.setRadius(defaultClusterRadius);
        }
        putFloat("clusterSize", clusterSize);
        getSupport().firePropertyChange("clusterSize", old, clusterSize);
    }

    public float getMinClusterSize() {
        return 0;
    }

    public float getMaxClusterSize() {
        return .3f;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter - Min/Max for --MaxNumClusters--">
    /**
     * max number of clusters
     *
     * @return
     */
    public final int getMaxNumClusters() {
        return maxNumClusters;
    }

    /**
     * max number of clusters
     *
     * @param maxNumClusters
     */
    public void setMaxNumClusters(final int maxNumClusters) {
        this.maxNumClusters = maxNumClusters;
        putInt("maxNumClusters", maxNumClusters);
    }

    public int getMinMaxNumClusters() {
        return 0;
    }

    public int getMaxMaxNumClusters() {
        return 20;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ThresholdForVisibleCluster--">
    /**
     * number of events to make a potential cluster visible
     *
     * @return
     */
    public final int getThresholdMassForVisibleCluster() {
        return thresholdMassForVisibleCluster;
    }

    /**
     * number of events to make a potential cluster visible
     *
     * @param thresholdMassForVisibleCluster
     */
    public void setThresholdMassForVisibleCluster(final int thresholdMassForVisibleCluster) {
        this.thresholdMassForVisibleCluster = thresholdMassForVisibleCluster;
        putInt("thresholdMassForVisibleCluster", thresholdMassForVisibleCluster);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --GrowMergedSizeEnabled--">
    public boolean isGrowMergedSizeEnabled() {
        return growMergedSizeEnabled;
    }

    /**
     * Flags whether to grow the clusters when two clusters are merged, or to
     * take the new size as the size of the older cluster.
     *
     * @param growMergedSizeEnabled true to grow the cluster size, false to use
     * the older cluster's size.
     */
    public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled) {
        this.growMergedSizeEnabled = growMergedSizeEnabled;
        putBoolean("growMergedSizeEnabled", growMergedSizeEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseVelocity--">
    public boolean isUseVelocity() {
        return useVelocity;
    }

    /**
     * Use cluster velocityPPT to give clusters a kind of inertia, so that they
     * are virtually moved by their velocityPPT times the time between the last
     * event and the present one before updating cluster location. Depends on
     * enabling cluster paths. Setting this option true enables cluster paths.
     *
     * @param useVelocity
     * @see #setPathsEnabled(boolean)
     */
    public void setUseVelocity(boolean useVelocity) {
        if (useVelocity) {
            setPathsEnabled(true);
        }
        getSupport().firePropertyChange("useVelocity", this.useVelocity, useVelocity);
        this.useVelocity = useVelocity;
        putBoolean("useVelocity", useVelocity);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --LogDataEnabled--">
    public synchronized boolean isLogDataEnabled() {
        return logDataEnabled;
    }

    public synchronized void setLogDataEnabled(boolean logDataEnabled) {
        getSupport().firePropertyChange("logDataEnabled", this.logDataEnabled, logDataEnabled);
        this.logDataEnabled = logDataEnabled;
        if (!logDataEnabled) {
            clusterLogger.close();
        } else {
            clusterLogger.open();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter - Min/Max for --AspectRatio--">
    public float getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(float aspectRatio) {
        if (aspectRatio < 0) {
            aspectRatio = 0;
        } else if (aspectRatio > 4) {
            aspectRatio = 4;
        }
        this.aspectRatio = aspectRatio;
        putFloat("aspectRatio", aspectRatio);

    }

    public float getMinAspectRatio() {
        return .25f;
    }

    public float getMaxAspectRatio() {
        return 4;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowAllCLusters--">
    public boolean isShowAllClusters() {
        return showAllClusters;
    }

    /**
     * Sets annotation visibility of clusters that are not "visible"
     *
     * @param showAllClusters true to show all clusters even if there are not
     * "visible"
     */
    public void setShowAllClusters(boolean showAllClusters) {
        this.showAllClusters = showAllClusters;
        putBoolean("showAllClusters", showAllClusters);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --DynamicAspectRatioEnabled--">
    public boolean isDynamicAspectRatioEnabled() {
        return dynamicAspectRatioEnabled;
    }

    public void setDynamicAspectRatioEnabled(boolean dynamicAspectRatioEnabled) {
        this.dynamicAspectRatioEnabled = dynamicAspectRatioEnabled;
        putBoolean("dynamicAspectRatioEnabled", dynamicAspectRatioEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseNearestCluster--">
    public boolean isUseNearestCluster() {
        return useNearestCluster;
    }

    public void setUseNearestCluster(boolean useNearestCluster) {
        this.useNearestCluster = useNearestCluster;
        putBoolean("useNearestCluster", useNearestCluster);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PredictiveVelocityFactor--">
    public float getPredictiveVelocityFactor() {
        return predictiveVelocityFactor;
    }

    public void setPredictiveVelocityFactor(float predictiveVelocityFactor) {
        this.predictiveVelocityFactor = predictiveVelocityFactor;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ThresholdVelocityForVisibleCLuster--">
    public float getThresholdVelocityForVisibleCluster() {
        return thresholdVelocityForVisibleCluster;
    }

    /**
     * A cluster must have at least this velocityPPT magnitude to become visible
     *
     * @param thresholdVelocityForVisibleCluster speed in pixels/second
     */
    synchronized public void setThresholdVelocityForVisibleCluster(float thresholdVelocityForVisibleCluster) {
        if (thresholdVelocityForVisibleCluster < 0) {
            thresholdVelocityForVisibleCluster = 0;
        }
        this.thresholdVelocityForVisibleCluster = thresholdVelocityForVisibleCluster;
        putFloat("thresholdVelocityForVisibleCluster", thresholdVelocityForVisibleCluster);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PathLength--">
    public int getPathLength() {
        return pathLength;
    }

    /**
     * Sets the maximum number of path points recorded for each cluster. The
     * {@link Cluster#path} list of points is adjusted to be at most
     * <code>pathLength</code> long.
     *
     * @param pathLength the number of recorded path points. If <2, set to 2.
     */
    synchronized public void setPathLength(int pathLength) {
        if (pathLength < 2) {
            pathLength = 2;
        }
        int old = this.pathLength;
        this.pathLength = pathLength;
        putInt("pathLength", pathLength);
        getSupport().firePropertyChange("pathLength", old, pathLength);
        // if (velocityPoints > pathLength) {
        // setVelocityPoints(pathLength);
        // }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --DynamicAngleEnabled--">
    public boolean isDynamicAngleEnabled() {
        return dynamicAngleEnabled;
    }

    /**
     * Setting dynamicAngleEnabled true enables variable-instantaneousAngle
     * clusters.
     */
    synchronized public void setDynamicAngleEnabled(boolean dynamicAngleEnabled) {
        this.dynamicAngleEnabled = dynamicAngleEnabled;
        putBoolean("dynamicAngleEnabled", dynamicAngleEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --VelocityTauMs--">
    public float getVelocityTauMs() {
        return velocityTauMs;
    }

    synchronized public void setVelocityTauMs(float velocityTauMs) {
        this.velocityTauMs = velocityTauMs;
        putFloat("velocityTauMs", velocityTauMs);
        for (Cluster c : clusters) {
            c.vxFilter.setTauMs(velocityTauMs);
            c.vyFilter.setTauMs(velocityTauMs);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FrictionTauMs--">
    public float getFrictionTauMs() {
        return frictionTauMs;
    }

    synchronized public void setFrictionTauMs(float frictionTauMs) {
        this.frictionTauMs = frictionTauMs;
        putFloat("frictionTauMs", frictionTauMs);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --VelAngDiffDegToNotMerge--">
    /**
     * @return the velAngDiffDegToNotMerge
     */
    public float getVelAngDiffDegToNotMerge() {
        return velAngDiffDegToNotMerge;
    }

    /**
     * @param velAngDiffDegToNotMerge the velAngDiffDegToNotMerge to set
     */
    public void setVelAngDiffDegToNotMerge(float velAngDiffDegToNotMerge) {
        if (velAngDiffDegToNotMerge < 30) {
            velAngDiffDegToNotMerge = 0;
        } else if (velAngDiffDegToNotMerge > 180) {
            velAngDiffDegToNotMerge = 180;
        }
        this.velAngDiffDegToNotMerge = velAngDiffDegToNotMerge;
        putFloat("velAngDiffDegToNotMerge", velAngDiffDegToNotMerge);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterNumber--">
    /**
     * @return the showClusterNumber
     */
    public boolean isShowClusterNumber() {
        return showClusterNumber;
    }

    /**
     * @param showClusterNumber the showClusterNumber to set
     */
    public void setShowClusterNumber(boolean showClusterNumber) {
        this.showClusterNumber = showClusterNumber;
        putBoolean("showClusterNumber", showClusterNumber);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterEps--">
    /**
     * @return the showClusterEps
     */
    public boolean isShowClusterEps() {
        return showClusterEps;
    }

    /**
     * @param showClusterEps the showClusterEps to set
     */
    public void setShowClusterEps(boolean showClusterEps) {
        this.showClusterEps = showClusterEps;
        putBoolean("showClusterEps", showClusterEps);
    }

    /**
     * @return the showClusterEps
     */
    public boolean isShowClusterEpsPerPx() {
        return showClusterEpsPerPx;
    }

    /**
     * @param showClusterEps the showClusterEps to set
     */
    public void setShowClusterEpsPerPx(boolean showClusterEpsPerPx) {
        this.showClusterEpsPerPx = showClusterEpsPerPx;
        putBoolean("showClusterEpsPerPx", showClusterEpsPerPx);
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowCLusterVelocity--">
    /**
     * @return the showClusterVelocity
     */
    public boolean isShowClusterVelocity() {
        return showClusterVelocity;
    }

    /**
     * @param showClusterVelocity the showClusterVelocity to set
     */
    public void setShowClusterVelocity(boolean showClusterVelocity) {
        this.showClusterVelocity = showClusterVelocity;
        putBoolean("showClusterVelocity", showClusterVelocity);
    }

    /**
     * @return the showClusterVelocity
     */
    public boolean isShowClusterVelocityVector() {
        return showClusterVelocityVector;
    }

    /**
     * @param showClusterVelocity the showClusterVelocity to set
     */
    public void setShowClusterVelocityVector(boolean showClusterVelocityVector) {
        this.showClusterVelocityVector = showClusterVelocityVector;
        putBoolean("showClusterVelocityVector", showClusterVelocityVector);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --VelocityVectorScaling--">
    /**
     * @return the velocityVectorScaling
     */
    public float getVelocityVectorScaling() {
        return velocityVectorScaling;
    }

    /**
     * @param velocityVectorScaling the velocityVectorScaling to set
     */
    public void setVelocityVectorScaling(float velocityVectorScaling) {
        this.velocityVectorScaling = velocityVectorScaling;
        putFloat("velocityVectorScaling", velocityVectorScaling);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --InitializeVelocityToAverage--">
    /**
     * @return the initializeVelocityToAverage
     */
    public boolean isInitializeVelocityToAverage() {
        return initializeVelocityToAverage;
    }

    /**
     * @param initializeVelocityToAverage the initializeVelocityToAverage to set
     */
    public void setInitializeVelocityToAverage(boolean initializeVelocityToAverage) {
        this.initializeVelocityToAverage = initializeVelocityToAverage;
        putBoolean("initializeVelocityToAverage", initializeVelocityToAverage);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterMass--">
    /**
     * @return the showClusterMass
     */
    public boolean isShowClusterMass() {
        return showClusterMass;
    }

    /**
     * @param showClusterMass the showClusterMass to set
     */
    public void setShowClusterMass(boolean showClusterMass) {
        this.showClusterMass = showClusterMass;
        putBoolean("showClusterMass", showClusterMass);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowPaths--">
    /**
     * @return the showPaths
     */
    public boolean isShowPaths() {
        return showPaths;
    }

    /**
     * @param showPaths the showPaths to set
     */
    public void setShowPaths(boolean showPaths) {
        this.showPaths = showPaths;
        putBoolean("showPaths", showPaths);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter & list definition for --ClusterLoggingMethod--">
    public enum ClusterLoggingMethod {

        LogFrames,
        LogClusters
    };

    private ClusterLoggingMethod clusterLoggingMethod = ClusterLoggingMethod
            .valueOf(getString("clusterLoggingMethod", ClusterLoggingMethod.LogFrames.toString()));

    /**
     * @return the clusterLoggingMethod
     */
    public ClusterLoggingMethod getClusterLoggingMethod() {
        return clusterLoggingMethod;
    }

    /**
     * @param clusterLoggingMethod the clusterLoggingMethod to set
     */
    public void setClusterLoggingMethod(ClusterLoggingMethod clusterLoggingMethod) {
        if (isLogDataEnabled()) {
            log.warning("changing logging method during logging not allowed");
            getSupport().firePropertyChange("clusterLoggingMethod", null, getClusterLoggingMethod());
            return;
        }
        ClusterLoggingMethod old = this.clusterLoggingMethod;
        this.clusterLoggingMethod = clusterLoggingMethod;
        getSupport().firePropertyChange("clusterLoggingMethod", old, clusterLoggingMethod);
        putString("clusterLoggingMethod", clusterLoggingMethod.toString());
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SurroundInhibitionEnabled--">
    /**
     * @return the surroundInhibitionEnabled
     */
    public boolean isSurroundInhibitionEnabled() {
        return surroundInhibitionEnabled;
    }

    /**
     * @param surroundInhibitionEnabled the surroundInhibitionEnabled to set
     */
    public void setSurroundInhibitionEnabled(boolean surroundInhibitionEnabled) {
        boolean old = this.surroundInhibitionEnabled;
        this.surroundInhibitionEnabled = surroundInhibitionEnabled;
        putBoolean("surroundInhibitionEnabled", surroundInhibitionEnabled);
        getSupport().firePropertyChange("surroundInhibitionEnabled", old, surroundInhibitionEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --DontMergeEver--">
    /**
     * @return the dontMergeEver
     */
    public boolean isDontMergeEver() {
        return dontMergeEver;
    }

    /**
     * @param dontMergeEver the dontMergeEver to set
     */
    public void setDontMergeEver(boolean dontMergeEver) {
        this.dontMergeEver = dontMergeEver;
        putBoolean("dontMergeEver", dontMergeEver);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --AngleFollowsVelocity--">
    /**
     * @return the angleFollowsVelocity
     */
    public boolean isAngleFollowsVelocity() {
        return angleFollowsVelocity;
    }

    /**
     * @param angleFollowsVelocity the angleFollowsVelocity to set
     */
    public void setAngleFollowsVelocity(boolean angleFollowsVelocity) {
        this.angleFollowsVelocity = angleFollowsVelocity;
        putBoolean("angleFollowsVelocity", angleFollowsVelocity);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UseEllipticalClusters--">
    /**
     * @return the useEllipticalClusters
     */
    public boolean isUseEllipticalClusters() {
        return useEllipticalClusters;
    }

    /**
     * @param useEllipticalClusters the useEllipticalClusters to set
     */
    public void setUseEllipticalClusters(boolean useEllipticalClusters) {
        this.useEllipticalClusters = useEllipticalClusters;
        putBoolean("useEllipticalClusters", useEllipticalClusters);
    }
    // </editor-fold>

    // ---- Smooth Settings ----
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SmoothMove--">
    public boolean getSmoothMove() {
        return smoothMove;
    }

    public void setSmoothMove(boolean v) {
        this.smoothMove = v;
        putBoolean("smoothMove", v);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SmoothWeight--">
    public float getSmoothWeight() {
        return smoothWeight;
    }

    public void setSmoothWeight(float v) {
        this.smoothWeight = v;
        putFloat("smoothWeight", v);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SmoothPosition--">
    public float getSmoothPosition() {
        return smoothPosition;
    }

    public void setSmoothPosition(float v) {
        this.smoothPosition = v;
        putFloat("smoothPosition", v);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --SmoothIntegral--">
    public float getSmoothIntegral() {
        return smoothIntegral;
    }

    public void setSmoothIntegral(float v) {
        this.smoothIntegral = v;
        putFloat("smoothIntegral", v);
    }
    // </editor-fold>

    // private float opticalGyroTauHighpassMs=getInt("opticalGyroTauHighpassMs", 10000);
    // {
    // }
    // private class OpticalGyroFilters{
    // LowpassFilter x=new LowpassFilter();
    // LowpassFilter y=new LowpassFilter();
    //
    // private OpticalGyroFilters(){
    // x.setTauMs(opticalGyroTauLowpassMs);
    // y.setTauMs(opticalGyroTauLowpassMs);
    // }
    //// private void setTauMsHigh(float opticalGyroTauHighpassMs) {
    ////// x.setTauMsHigh(opticalGyroTauHighpassMs);
    ////// y.setTauMsHigh(opticalGyroTauHighpassMs);
    //// }
    //
    // private void setTauMsLow(float opticalGyroTauLowpassMs){
    // x.setTauMs(opticalGyroTauLowpassMs);
    // y.setTauMs(opticalGyroTauLowpassMs);
    // }
    // }
    // private final void drawCluster(final Cluster c, float[][][] fr) {
    // int x = (int) c.getLocation().x;
    // int y = (int) c.getLocation().y;
    //
    //
    // int sy = (int) c.getRadius(); // sx sy are (half) size of rectangle
    // int sx = sy;
    // int ix, iy;
    // int mn, mx;
    //
    // if (isColorClustersDifferentlyEnabled()) {
    // } else {
    // c.setColorAccordingToSize();
    // }
    //
    // Color color = c.getColor();
    // if (true) { // draw boxes
    // iy = y - sy; // line under center
    // mn = x - sx;
    // mx = x + sx;
    // for (ix = mn; ix <= mx; ix++) {
    // colorPixel(ix, iy, fr, clusterColorChannel, color);
    // }
    // iy = y + sy; // line over center
    // for (ix = mn; ix <= mx; ix++) {
    // colorPixel(ix, iy, fr, clusterColorChannel, color);
    // }
    // ix = x - sx; // line to left
    // mn = y - sy;
    // mx = y + sy;
    // for (iy = mn; iy <= mx; iy++) {
    // colorPixel(ix, iy, fr, clusterColorChannel, color);
    // }
    // ix = x + sx; // to right
    // for (iy = mn; iy <= mx; iy++) {
    // colorPixel(ix, iy, fr, clusterColorChannel, color);
    // }
    // } else { // draw diamond reflecting manhatten distance measure doesn't look very nice because not antialiased at
    // all
    // iy = y - sy; // line up right from bot
    // ix = x;
    // mx = x + sx;
    // while (ix < mx) {
    // colorPixel(ix++, iy++, fr, clusterColorChannel, color);
    // }
    // mx = x + sx;
    // ix = x;
    // iy = y + sy; // line down right from top
    // while (ix < mx) {
    // colorPixel(ix++, iy--, fr, clusterColorChannel, color);
    // }
    // ix = x; // line from top down left
    // iy = y + sy;
    // while (iy >= y) {
    // colorPixel(ix--, iy--, fr, clusterColorChannel, color);
    // }
    // ix = x;
    // iy = y - sy;
    // while (iy < y) {
    // colorPixel(ix--, iy++, fr, clusterColorChannel, color);
    // }
    // }
    //
    // List<ClusterPathPoint> points = c.getPath();
    // for (Point2D.Float p : points) {
    // colorPixel(Math.round(p.x), Math.round(p.y), fr, clusterColorChannel, color);
    // }
    //
    // }
    // private static final int clusterColorChannel = 2;
    // /**
    // * @param x x location of pixel
    // * @param y y location
    // * @param fr the frame data
    // * @param channel the RGB channel number 0-2
    // * @param brightness the brightness 0-1 */
    // private final void colorPixel(final int x, final int y, final float[][][] fr, int channel, Color color) {
    // if ((y < 0) || (y > (fr.length - 1)) || (x < 0) || (x > (fr[0].length - 1))) {
    // return;
    // }
    // float[] rgb = color.getRGBColorComponents(null);
    // float[] f = fr[y][x];
    // for (int i = 0; i < 3; i++) {
    // f[i] = rgb[i];
    // }
    //// fr[y][x][channel]=brightness;
    ////// if(brightness<1){
    //// for(int i=0;i<3;i++){
    //// if(i!=channel) fr[y][x][i]=0;
    //// }
    ////// }
    // }
    // /** number of events to store for a cluster */
    // public int getNumEventsStoredInCluster() {
    // return prefs.getInt("RectangularClusterTracker.numEventsStoredInCluster",10);
    // }
    //
    // /** number of events to store for a cluster */
    // public void setNumEventsStoredInCluster(final int numEventsStoredInCluster) {
    // prefs.putInt("RectangularClusterTracker.numEventsStoredInCluster", numEventsStoredInCluster);
    // }
    // // PRIVATE?? Whats the use of this method? its private and always returns false...
    // private boolean isGeneratingFilter() {
    // return false;
    // }
    // public float getVelocityMixingFactor() {
    // return velocityMixingFactor;
    // }
    //
    // public void setVelocityMixingFactor(float velocityMixingFactor) {
    // if(velocityMixingFactor<0) velocityMixingFactor=0; if(velocityMixingFactor>1) velocityMixingFactor=1f;
    // this.velocityMixingFactor = velocityMixingFactor;
    // putFloat("velocityMixingFactor",velocityMixingFactor);
    // }
    // public boolean isClassifierEnabled(){
    // return classifierEnabled;
    // }
    // /** Sets whether classifier is enabled.
    // * @param classifierEnabled true to enable classifier
    // */
    // public void setClassifierEnabled(boolean classifierEnabled){
    // this.classifierEnabled=classifierEnabled;
    // putBoolean("classifierEnabled",classifierEnabled);
    // }
    // public float getClassifierThreshold(){
    // return classifierThreshold;
    // }
    //
    // public void setClassifierThreshold(float classifierThreshold){
    // this.classifierThreshold=classifierThreshold;
    // putFloat("classifierThreshold",classifierThreshold);
    // }
    // public boolean isClusterLifetimeIncreasesWithAge() {
    // return clusterLifetimeIncreasesWithAge;
    // }
    //
    // /**
    // * If true, cluster lifetime without support increases proportional to the age of the cluster relative to the
    // clusterMassDecayTauUs time
    // */
    // synchronized public void setClusterLifetimeIncreasesWithAge(boolean clusterLifetimeIncreasesWithAge) {
    // this.clusterLifetimeIncreasesWithAge = clusterLifetimeIncreasesWithAge;
    // putBoolean("clusterLifetimeIncreasesWithAge", clusterLifetimeIncreasesWithAge);
    //
    // }
    // /** @see #setVelocityPoints(int)
    // *
    // * @return number of points used to estimate velocityPPT.
    // */
    // public int getVelocityPoints() {
    // return velocityPoints;
    // }
    //
    // /** Sets the number of path points to use to estimate cluster velocityPPT.
    // *
    // * @param velocityPoints the number of points to use to estimate velocityPPT.
    // * Bounded above to number of path points that are stored.
    // * @see #setPathLength(int)
    // * @see #setPathsEnabled(boolean)
    // */
    // public void setVelocityPoints(int velocityPoints) {
    // if (velocityPoints >= pathLength) {
    // velocityPoints = pathLength;
    // }
    // int old = this.velocityPoints;
    // this.velocityPoints = velocityPoints;
    // putInt("velocityPoints", velocityPoints);
    // getSupport().firePropertyChange("velocityPoints", old, this.velocityPoints);
    // }
    // replaced by assignment from updateIntervalMs*1000
    // /**
    // * @return the loggingIntervalUs
    // */
    // public int getLoggingIntervalUs() {
    // return loggingIntervalUs;
    // }
    //
    // /**
    // * @param loggingIntervalUs the loggingIntervalUs to set
    // */
    // public void setLoggingIntervalUs(int loggingIntervalUs) {
    // this.loggingIntervalUs = loggingIntervalUs;
    // putInt("loggingIntervalUs", loggingIntervalUs);
    // }
    // /** Encapsulates the nearest Cluster and the distance to it */
    // private class ClusterAndDistance {
    // public ClusterAndDistance(Cluster c, float distance) {
    // this.c = c;
    // this.distance = distance;
    // }
    // Cluster c;
    // float distance;
    // }
    /**
     * @return the defaultClusterRadius in pixels
     */
    public float getDefaultClusterRadius() {
        return defaultClusterRadius;
    }

    /**
     * @param defaultClusterRadius the defaultClusterRadius to set in pixels
     */
    public void setDefaultClusterRadius(float defaultClusterRadius) {
        this.defaultClusterRadius = defaultClusterRadius;
    }

    /**
     * @return the updateClustersOnlyFromEventsNearEdge
     */
    public boolean isUpdateClustersOnlyFromEventsNearEdge() {
        return updateClustersOnlyFromEventsNearEdge;
    }

    /**
     * @param updateClustersOnlyFromEventsNearEdge the
     * updateClustersOnlyFromEventsNearEdge to set
     */
    public void setUpdateClustersOnlyFromEventsNearEdge(boolean updateClustersOnlyFromEventsNearEdge) {
        this.updateClustersOnlyFromEventsNearEdge = updateClustersOnlyFromEventsNearEdge;
        putBoolean("updateClustersOnlyFromEventsNearEdge", updateClustersOnlyFromEventsNearEdge);
    }

    /**
     * @return the ellipticalClusterEdgeThickness
     */
    public float getEllipticalClusterEdgeThickness() {
        return ellipticalClusterEdgeThickness;
    }

    /**
     * @param ellipticalClusterEdgeThickness the ellipticalClusterEdgeThickness
     * to set
     */
    public void setEllipticalClusterEdgeThickness(float ellipticalClusterEdgeThickness) {
        this.ellipticalClusterEdgeThickness = ellipticalClusterEdgeThickness;
        putFloat("ellipticalClusterEdgeThickness", ellipticalClusterEdgeThickness);
    }

    /**
     * faster sqrt, from
     * https://stackoverflow.com/questions/13263948/fast-sqrt-in-java-at-the-expense-of-accuracy
     *
     * @param d
     * @return approx sqrt(d)
     */
    private double sqrt(double d) {
//        double realsqrt=Math.sqrt(d);
        double sqrt = Double.longBitsToDouble(((Double.doubleToLongBits(d) - (1l << 52)) >> 1) + (1l << 61));
//        double actOverReal=sqrt/realsqrt;
        return sqrt;

    }

    /**
     * @return the purgeIfClusterOverlapsBorder
     */
    public boolean isPurgeIfClusterOverlapsBorder() {
        return purgeIfClusterOverlapsBorder;
    }

    /**
     * @param purgeIfClusterOverlapsBorder the purgeIfClusterOverlapsBorder to
     * set
     */
    public void setPurgeIfClusterOverlapsBorder(boolean purgeIfClusterOverlapsBorder) {
        this.purgeIfClusterOverlapsBorder = purgeIfClusterOverlapsBorder;
        putBoolean("purgeIfClusterOverlapsBorder", purgeIfClusterOverlapsBorder);
    }

    /**
     * @return the maxSizeScaleRatio
     */
    public float getMaxSizeScaleRatio() {
        return maxSizeScaleRatio;
    }

    /**
     * @param maxSizeScaleRatio the maxSizeScaleRatio to set
     */
    public void setMaxSizeScaleRatio(float maxSizeScaleRatio) {
        this.maxSizeScaleRatio = maxSizeScaleRatio;
        putFloat("maxSizeScaleRatio", maxSizeScaleRatio);
    }

    /**
     * @return the useEventRatePerPixelForVisibilty
     */
    public boolean isUseEventRatePerPixelForVisibilty() {
        return useEventRatePerPixelForVisibilty;
    }

    /**
     * @param useEventRatePerPixelForVisibilty the
     * useEventRatePerPixelForVisibilty to set
     */
    public void setUseEventRatePerPixelForVisibilty(boolean useEventRatePerPixelForVisibilty) {
        this.useEventRatePerPixelForVisibilty = useEventRatePerPixelForVisibilty;
        putBoolean("useEventRatePerPixelForVisibilty", useEventRatePerPixelForVisibilty);
    }

    /**
     * @return the eventRatePerPixelLowpassTauS
     */
    public float getEventRatePerPixelLowpassTauS() {
        return eventRatePerPixelLowpassTauS;
    }

    /**
     * @param eventRatePerPixelLowpassTauS the eventRatePerPixelLowpassTauS to
     * set
     */
    public void setEventRatePerPixelLowpassTauS(float eventRatePerPixelLowpassTauS) {
        this.eventRatePerPixelLowpassTauS = eventRatePerPixelLowpassTauS;
        putFloat("eventRatePerPixelLowpassTauS", eventRatePerPixelLowpassTauS);
    }

    /**
     * @return the thresholdEventRatePerPixelForVisibleClusterHz
     */
    public float getThresholdEventRatePerPixelForVisibleClusterHz() {
        return thresholdEventRatePerPixelForVisibleClusterHz;
    }

    /**
     * @param thresholdEventRatePerPixelForVisibleClusterHz the
     * thresholdEventRatePerPixelForVisibleClusterHz to set
     */
    public void setThresholdEventRatePerPixelForVisibleClusterHz(float thresholdEventRatePerPixelForVisibleClusterHz) {
        this.thresholdEventRatePerPixelForVisibleClusterHz = thresholdEventRatePerPixelForVisibleClusterHz;
        putFloat("thresholdEventRatePerPixelForVisibleClusterHz", thresholdEventRatePerPixelForVisibleClusterHz);
    }
}
