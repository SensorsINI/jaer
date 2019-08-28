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

    private int statisticsStartTimestamp = Integer.MIN_VALUE, statisticsCurrentTimestamp = Integer.MIN_VALUE;
    private boolean initialized = false;
    private DescriptiveStatistics stats; // from Apache, useful class to measure statistics

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
        final String sizing = "Sizing", mov = "Movement", life = "Lifetime", disp = "Display", global = TOOLTIP_GROUP_GLOBAL,
                update = "Update", logg = "Logging", pi = "PI Controller";
        setPropertyTooltip(sizing, "clusterSize", "size (starting) in fraction of chip max size");
        setPropertyTooltip(disp, "showAllClusters", "shows all clusters, not just those with sufficient support");
        setPropertyTooltip(life, "thresholdMassForVisibleCluster",
                "Cluster needs this \"mass\" to be visible. Mass increments with each event and decays with e-folding time constant of clusterMassDecayTauUs. Use \"showAllClusters\" to diagnose fleeting clusters.");
        setPropertyTooltip(logg, "logging", "toggles cluster logging to Matlab script file according to method (see logDataEnabled in RectangularClusterTracker)");
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
        stats = new DescriptiveStatistics(10000); // limit to this many drops in dataset for now, to bound memory leak
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        maybeAddListeners(chip);
        getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        enclosedFilterChain.reset();
        stats.clear();
        initialized=false;
        statisticsStartTimestamp = Integer.MIN_VALUE;
        statisticsCurrentTimestamp = Integer.MIN_VALUE;
    }

    
    @Override
    public void initFilter() {
        tracker.initFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        String s = computeSummaryStatistics();
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

    private String computeSummaryStatistics() {
        int deltaTimestamp = statisticsCurrentTimestamp - statisticsStartTimestamp;
        float deltaTimeS = deltaTimestamp * 1e-6f;
        int n = (int) stats.getN();
        float rateHz = n / deltaTimeS;
        String s = String.format("%d drops\n%.2f+/-%.2f pixels mean radius\n%.2fHz rate",
                n,
                stats.getMean(),
                stats.getStandardDeviation(),
                rateHz);
        return s;
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

            private float maxRadius = 0;

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
                if (getAverageEventXDistance() > maxRadius) {
                    maxRadius = getAverageEventXDistance();
                }
            }

            /**
             * Overrides the prune() method to store in statistics the maximum
             * size of raindrop when cluster disappears
             *
             */
            @Override
            protected void onPruning() {
                // called when cluster finally pruned away
                if (isWasEverVisible()) {
//                    log.info("logging droplet "+this);
                    if (Float.isNaN(maxRadius) || Float.isInfinite(maxRadius)) {
                        log.warning("radius is NaN or Infinite, not adding this droplet");
                        return;
                    }
                    stats.addValue(maxRadius);
                    if (!initialized) {
                        statisticsStartTimestamp = tracker.lastTimestamp;
                        statisticsCurrentTimestamp = tracker.lastTimestamp;
                        initialized=true;
                    } else {
                        statisticsCurrentTimestamp = tracker.lastTimestamp;
                    }
                }
            }

        } // RaindropCluster

    } // RaindropTracker
    
    

} // RaindropCounter
