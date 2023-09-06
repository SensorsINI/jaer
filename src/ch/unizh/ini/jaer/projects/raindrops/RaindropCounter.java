/*
 * Copyright (C) 2019 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.raindrops;

import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.EngineeringFormat;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Counts and sizes raindrops
 *
 * @author Asude Aydin & tobi Delbruck
 */
@Description("Counts rain droplets and measures stats about them")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RaindropCounter extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private RaindropTracker tracker = null; // does the detection of droplets; we pull up methods to make it easier to control
    private SpatioTemporalCorrelationFilter noiseFilter = null;
    private XYTypeFilter onEventFilter = null;
    private FilterChain enclosedFilterChain = null;

    private int statisticsStartTimestamp = Integer.MIN_VALUE, statisticsCurrentTimestamp = Integer.MIN_VALUE, lastDropletTimeMs=Integer.MIN_VALUE;
    private boolean initialized = false;
    private DescriptiveStatistics diameterPxStats,intervalMsStats; // from Apache, useful class to measure statistics
    private int totalNumberDroplets = 0;
    private float dropRateHz;  // average rate of raindrops
    private float avgIntervalMs, stdIntervalMs;
    private float totalVolumeLiters = 0; // integrated from droplet size 
    private float totalInches = 0;
    private float totalMm = 0;
    private float inchesPerHour = 0;
    private float mmPerHour = 0;
    private float workingDistanceM = getFloat("workingDistanceM", 0.5f);
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm", 100);
    private float sheetAngleDeg = getFloat("sheetAngleDeg", 30);
    private float metersPerPixel = Float.NaN;
    private float collectionAreaM2 = Float.NaN;
    private float totalRainRateLiterPerSqMPerS;
    private EngineeringFormat engFmt = new EngineeringFormat();

    public RaindropCounter(AEChip chip) {
        super(chip);
        tracker = new RaindropTracker(chip);
        noiseFilter = new SpatioTemporalCorrelationFilter(chip);
        onEventFilter = new XYTypeFilter(chip);
        enclosedFilterChain = new FilterChain(chip);
        enclosedFilterChain.add(onEventFilter);
        enclosedFilterChain.add(noiseFilter);
        enclosedFilterChain.add(tracker);
        setEnclosedFilterChain(enclosedFilterChain);
        // tooltips
        final String sizing = "Sizing", life = "Lifetime", disp = "Display", global = TOOLTIP_GROUP_GLOBAL,
                update = "Update", logg = "Logging", opt = "Optics";
        setPropertyTooltip(sizing, "clusterSize", "size (starting) in fraction of chip max size");
        setPropertyTooltip(disp, "showAllClusters", "shows all clusters, not just those with sufficient support");
        setPropertyTooltip(life, "thresholdMassForVisibleCluster",
                "Cluster needs this \"mass\" to be visible. Mass increments with each event and decays with e-folding time constant of clusterMassDecayTauUs. Use \"showAllClusters\" to diagnose fleeting clusters.");
        setPropertyTooltip(logg, "logging", "toggles cluster logging to Matlab script file according to method (see logDataEnabled in RectangularClusterTracker)");
        setPropertyTooltip(opt, "workingDistanceM", "distance to laser sheet in meters");
        setPropertyTooltip(opt, "lensFocalLengthMm", "lens focal length in mm");
        setPropertyTooltip(opt, "sheetAngleDeg", "angle of camera to sheet (vertical would be 90)");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written log file");

// reasonable defaults
        onEventFilter.setTypeEnabled(true);
        onEventFilter.setXEnabled(false);
        onEventFilter.setYEnabled(false);
        onEventFilter.setStartType(1);
        onEventFilter.setEndType(1);

        tracker.setMaxNumClusters(100);
        tracker.setPathsEnabled(true);
        tracker.setDontMergeEver(true);
        tracker.setHighwayPerspectiveEnabled(false);
        tracker.setColorClustersDifferentlyEnabled(false);
        tracker.setUseVelocity(false);
        tracker.setDontMergeEver(true);
        tracker.setEnableClusterExitPurging(false);
        tracker.setClusterLoggingMethod(RectangularClusterTracker.ClusterLoggingMethod.LogClusters);

        if (!tracker.isPreferenceStored("showClusterRadius")) {
            tracker.setShowClusterRadius(true);
        }
        tracker.setShowClusterMass(true);
        if (!tracker.isPreferenceStored("clusterMassDecayUs")) {
            tracker.setClusterMassDecayTauUs(2000);
        }

        // statistics
//        raindropHistogram = new SimpleHistogram(0, 1, 10, 0);
        diameterPxStats = new DescriptiveStatistics(1000000); // limit to this many drops in dataset for now, to bound memory leak
        intervalMsStats = new DescriptiveStatistics(1000000); // limit to this many drops in dataset for now, to bound memory leak

    }

    private void computeGeometry() {
        metersPerPixel = workingDistanceM * (1e-6f * chip.getPixelWidthUm()) / (lensFocalLengthMm * 1e-3f);

        float pixelEffectiveCollectionAreaM2 = (float) (metersPerPixel * metersPerPixel / Math.sin((Math.PI / 180) * sheetAngleDeg));
        collectionAreaM2 = chip.getNumPixels() * pixelEffectiveCollectionAreaM2;
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        getEnclosedFilterChain().filterPacket(in);
        computeSummaryStatistics();
        return in;
    }

    @Override
    public void resetFilter() {
        enclosedFilterChain.reset();
        diameterPxStats.clear();
        intervalMsStats.clear();
        initialized = false;
        statisticsStartTimestamp = Integer.MIN_VALUE;
        statisticsCurrentTimestamp = Integer.MIN_VALUE;
        lastDropletTimeMs = Integer.MIN_VALUE;
        totalNumberDroplets = 0;
        totalVolumeLiters = 0;
        totalRainRateLiterPerSqMPerS = 0;
        dropRateHz = 0;
        totalInches = 0;
        totalMm = 0;
        inchesPerHour = 0;
        mmPerHour = 0;
        avgIntervalMs=0;
        stdIntervalMs=0;
    }

    @Override
    public void initFilter() {
        maybeAddListeners(chip);
        tracker.initFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        String s = generateSummaryStatisticsString();
        MultilineAnnotationTextRenderer.setScale(.4f);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(.6f * chip.getSizeY());
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    public void doToggleOnLogging() {
        tracker.doToggleOnLogging();
    }

    public void doToggleOffLogging() {
        tracker.doToggleOffLogging();
    }

    public final float getClusterSize() {
        return tracker.getClusterSize();
    }

    public synchronized void setClusterSize(float clusterSize) {
        tracker.setClusterSize(clusterSize);
    }

    public final int getThresholdMassForVisibleCluster() {
        return tracker.getThresholdMassForVisibleCluster();
    }

    public void setThresholdMassForVisibleCluster(int thresholdMassForVisibleCluster) {
        tracker.setThresholdMassForVisibleCluster(thresholdMassForVisibleCluster);
    }

    public boolean isShowAllClusters() {
        return tracker.isShowAllClusters();
    }

    public void setShowAllClusters(boolean showAllClusters) {
        tracker.setShowAllClusters(showAllClusters);
    }

    public void doShowFolderInDesktop() {
        tracker.doShowFolderInDesktop();
    }

    private void computeSummaryStatistics() {
        computeGeometry();
        int deltaTimestamp = statisticsCurrentTimestamp - statisticsStartTimestamp;
        if(deltaTimestamp<=0) return;
        float measurementTimeS = deltaTimestamp * 1e-6f;
        totalNumberDroplets = (int) diameterPxStats.getN();
        dropRateHz = totalNumberDroplets / measurementTimeS;
        avgIntervalMs=(float)intervalMsStats.getMean();
        stdIntervalMs=(float)intervalMsStats.getStandardDeviation();
        

        // already computed totalVolumeLiters in RaindropCluster.onPruning()
        totalRainRateLiterPerSqMPerS = totalVolumeLiters / collectionAreaM2 / measurementTimeS;
        totalMm = totalVolumeLiters / collectionAreaM2;// stoichiometry says that we just divide these two to get mm that have fallen
        float hours = measurementTimeS / 3600f;
        mmPerHour = totalMm / hours;
        totalInches = totalMm / 25.4f;
        inchesPerHour = totalInches / hours;

    }

    private String generateSummaryStatisticsString() {
        engFmt.setPrecision(2);
        String s = String.format("%d drops\n%.2f+/-%.2f pixels mean diameter\n%.2fHz rate\n%s +- %s ms interval\n%s total liters\n%s liters/m^2/s"
                + "\n%s mm total (%s mm/h)"
                + "\n%s inches total (%s inches/h)",
                totalNumberDroplets,
                diameterPxStats.getMean(),
                diameterPxStats.getStandardDeviation(),
                dropRateHz,
                engFmt.format(avgIntervalMs),engFmt.format(stdIntervalMs),
                engFmt.format(totalVolumeLiters),
                engFmt.format(totalRainRateLiterPerSqMPerS),
                engFmt.format(totalMm),
                engFmt.format(mmPerHour),
                engFmt.format(totalInches),
                engFmt.format(inchesPerHour)
        );
        return s;
    }

    /**
     * @return the workingDistanceM
     */
    public float getWorkingDistanceM() {
        return workingDistanceM;
    }

    /**
     * @param workingDistanceM the workingDistanceM to set
     */
    public void setWorkingDistanceM(float workingDistanceM) {
        this.workingDistanceM = workingDistanceM;
        putFloat("workingDistanceM", workingDistanceM);
        computeGeometry();
    }

    /**
     * @return the lensFocalLengthMm
     */
    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    /**
     * @param lensFocalLengthMm the lensFocalLengthMm to set
     */
    public void setLensFocalLengthMm(float lensFocalLengthMm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        putFloat("lensFocalLengthMm", lensFocalLengthMm);
        computeGeometry();
    }

    /**
     * @return the sheetAngleDeg
     */
    public float getSheetAngleDeg() {
        return sheetAngleDeg;
    }

    /**
     * @param sheetAngleDeg the sheetAngleDeg to set
     */
    public void setSheetAngleDeg(float sheetAngleDeg) {
        this.sheetAngleDeg = sheetAngleDeg;
        putFloat("sheetAngleDeg", sheetAngleDeg);
    }

    private class RaindropTracker extends RectangularClusterTracker {

        public RaindropTracker(AEChip chip) {
            super(chip);
        }

        @Override
        public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
            return new RaindropCluster(ev, itr); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Cluster createCluster(Cluster one, Cluster two) {
            return new RaindropCluster(one, two); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Cluster createCluster(BasicEvent ev) {
            return new RaindropCluster(ev); //To change body of generated methods, choose Tools | Templates.
        }

        public class RaindropCluster extends RectangularClusterTracker.Cluster {

            private float maxRadiusPixels = 0;

            public RaindropCluster() {
            }

            public RaindropCluster(BasicEvent ev) {
                super(ev);
            }

            public RaindropCluster(BasicEvent ev, OutputEventIterator outItr) {
                super(ev, outItr);
            }

            public RaindropCluster(Cluster one, Cluster two) {
                super(one, two);
            }

            @Override
            protected ClusterPathPoint createPoint(float x, float y, int t) {
                final RaindropPoint point = (RaindropPoint) RaindropPoint.createPoint(x, y, t);
                point.setRadiusPixels(getAverageEventXDistance()); // for recording width of droplet in sheet
                point.setnEvents(numEvents);
                return point;
            }

            @Override
            protected void updateAverageEventDistance(float m) {
                super.updateAverageEventDistance(m);
                if (getAverageEventXDistance() > maxRadiusPixels) {
                    maxRadiusPixels = getAverageEventXDistance();
                }
            }

            /**
             * Computes and returns the droplet volume in m^3 assuming spherical
             * and has radius as measured by max appearance radius width
             *
             * @return volume in liters of this droplet
             */
            public float computeVolumeLiters() {
                // a liter is 1000 ml
                // 1 ml = 1cm^3
                float radiusM = maxRadiusPixels * metersPerPixel;
                // compute volume in liters of this droplet.
                // assume sphere. Compute volume in cm^3=ml. Then multiply by 1e-3 to get liters.
                float volumeLiters = (float) (Math.PI * 4 / 3 * Math.pow(1e2 * radiusM, 3)) * 1e-3f; // hope this is correct 
                return volumeLiters;
            }

            /**
             * Overrides the prune() method to store in statistics the maximum
             * size of raindrop when cluster disappears
             *
             */
            @Override
            protected void onBecomingVisible() {
                // called when cluster finally pruned away
                if (isWasEverVisible()) {
//                    log.info("logging droplet "+this);
                    if (Float.isNaN(maxRadiusPixels) || Float.isInfinite(maxRadiusPixels)) {
                        log.warning("radius is NaN or Infinite, not adding this droplet");
                        return;
                    }
                    diameterPxStats.addValue(maxRadiusPixels*2);
                    totalVolumeLiters += computeVolumeLiters();
                    if (!initialized) {
                        statisticsStartTimestamp = tracker.lastTimestamp;
                        statisticsCurrentTimestamp = tracker.lastTimestamp;
                        lastDropletTimeMs=tracker.lastTimestamp/1000;
                        initialized = true;
                    } else {
                        statisticsCurrentTimestamp = tracker.lastTimestamp;
                        int thisDropletTimeMs=tracker.lastTimestamp/1000;
                        int dropletIntervalMs=thisDropletTimeMs-lastDropletTimeMs;
                        intervalMsStats.addValue(dropletIntervalMs);
                        log.info(String.format("Droplet interval %,d ms", dropletIntervalMs));
                        lastDropletTimeMs=thisDropletTimeMs;
                        
                    }
                }
            }

        } // RaindropCluster

    } // RaindropTracker

} // RaindropCounter
