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
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.histogram.SimpleHistogram;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Counts and sizes raindrops
 *
 * @author Asude Aydin & tobi Delbruck
 */
public class RaindropCounter extends EventFilter2D implements FrameAnnotater {

    private RaindropTracker tracker = null; // does the detection of droplets; we pull up methods to make it easier to control
    private SpatioTemporalCorrelationFilter noiseFilter = null;
    private FilterChain enclosedFilterChain = null;

    private int statisticsStartTimestamp = Integer.MIN_VALUE, statisticsCurrentTimestamp = Integer.MIN_VALUE;
    private DescriptiveStatistics stats;
//    private SimpleHistogram raindropHistogram;

    public RaindropCounter(AEChip chip) {
        super(chip);
        tracker = new RaindropTracker(chip);
        noiseFilter = new SpatioTemporalCorrelationFilter(chip);
        enclosedFilterChain = new FilterChain(chip);
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
        if (!isPreferenceStored("showAllClusters")) {
            tracker.setShowAllClusters(true);
        }
        tracker.setShowClusterRadius(true);
        if (!isPreferenceStored("showClusterMass")) {
            tracker.setShowClusterMass(true);
        }
        tracker.setClusterLoggingMethod(RectangularClusterTracker.ClusterLoggingMethod.LogClusters);

        // statistics
//        raindropHistogram = new SimpleHistogram(0, 1, 10, 0);
        stats = new DescriptiveStatistics(10000); // limit to this many drops in dataset for now
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        enclosedFilterChain.reset();
        stats.clear();
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
        int deltaTimestamp=statisticsCurrentTimestamp-statisticsStartTimestamp;
        float deltaTimeS=deltaTimestamp*1e-6f;
        int n=(int)stats.getN();
        float rateHz=n/deltaTimeS;
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
            protected void updateAverageEventDistance(float m) {
                super.updateAverageEventDistance(m);
                if (getAverageEventDistance() > maxRadius) {
                    maxRadius = getAverageEventDistance();
                }
            }

            @Override
            protected void prune() {
                // called when cluster finally pruned away
                if (isWasEverVisible()) {
                    if(Float.isNaN(maxRadius) || Float.isInfinite(maxRadius)){
                        log.warning("radius is NaN or Infinite, not adding this droplet");
                        return;
                    }
                    stats.addValue(maxRadius);
                    if (statisticsStartTimestamp < getLastEventTimestamp()) {
                        statisticsStartTimestamp = getLastEventTimestamp();
                    } else {
                        statisticsCurrentTimestamp = getLastEventTimestamp();
                    }
                }
            }

        }

    }

}
