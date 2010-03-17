/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Virtual drummer. Demonstration of fast visual tracking and inference of virtual drum set capability.
 * @author Tobi Delbruck, Eric Ryu, Jun Haeng Lee
 */
public class VirtualDrummer extends EventFilter2D implements FrameAnnotater {

    // properties
    private int beatClusterTimeMs = getPrefs().getInt("VirtualDrummer.beatClusterTimeMs", 10);
    private float beatClusterVelocityPPS = getPrefs().getFloat("VirtualDrummer.beatClusterVelocityPPS", 100f); // PPS: Pixels per sec ?
    private int minBeatRepeatIntervalMs = getPrefs().getInt("VirtualDrummer.minBeatRepeatInterval", 300);
  
    // vars
    private Hashtable<Cluster, BeatStats> playedBeatClusters = new Hashtable();
    private RectangularClusterTracker tracker;
    private DrumSounds drumSounds = new DrumSounds();
    private BeatBoxSetting bbs = new BeatBoxSetting();

    public VirtualDrummer(AEChip chip) {
        super(chip);
        String key = "Drummer";
        setPropertyTooltip(key, "beatClusterVelocityPPS", "required vertical velocity of cluster to generate beat");
        setPropertyTooltip(key, "beatClusterTimeMs", "required lifetime of cluster to generate drumbeat");
        tracker = new RectangularClusterTracker(chip);
        setEnclosedFilterChain(new FilterChain(chip));
        getEnclosedFilterChain().add(tracker);
        tracker.setEnclosed(true, this);
    }

    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        tracker.annotate(drawable);
    }

    @Override
    public synchronized EventPacket filterPacket(EventPacket in) {
        tracker.filterPacket(in);
        for (Cluster c : tracker.getClusters()) {
            if (testGenerateBeat(c)) {

                drumSounds.setProgram(bbs.getSelectedIns());
                if (c.getLocation().x < chip.getSizeX() / 2) {
                    drumSounds.play(0, 127);
                } else {
                    drumSounds.play(1, 127);
                }
                playedBeatClusters.put(c, new BeatStats(c, System.currentTimeMillis()));
//                System.out.println("put " + c);
            }
        }
        // clear hashtable of old entries
        ArrayList<Cluster> toRemove = new ArrayList();
        for (Cluster c : playedBeatClusters.keySet()) {
            if (!tracker.getClusters().contains(c)) {
                toRemove.add(c);
            }
        }
        for (Cluster c : toRemove) {
            playedBeatClusters.remove(c);
//            System.out.println("removed " + c);
        }

        return in;
    }

    private boolean testGenerateBeat(Cluster c) {
        boolean oldEnough = c.getLifetime() > beatClusterTimeMs * getMinBeatRepeatIntervalMs();
        boolean fastEnough = c.getVelocityPPS().y < -getBeatClusterVelocityPPS();
        boolean playedAlready = playedBeatClusters.containsKey(c);
        if (playedAlready) {
            BeatStats stats = playedBeatClusters.get(c);
            long timeNow = System.currentTimeMillis();
            long timeSincePlayedBeat = timeNow - stats.timePlayedBeat;
//            System.out.println("time since played="+timeSincePlayedBeat);
            boolean playedLongAgoEnough = timeSincePlayedBeat > 1000;
            return playedLongAgoEnough && oldEnough && fastEnough;
        } else {
            return oldEnough && fastEnough;
        }
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
    }

    @Override
    public void initFilter() {
        tracker.initFilter();
    }

    public void annotate(float[][][] frame) {
        log.warning("cannot annotate in Graphic2D - switch to OpenGL");
    }

    public void annotate(Graphics2D g) {
        log.warning("cannot annotate in Graphic2D - switch to OpenGL");
    }

    // statistics of last drum beat
    private class BeatStats {

        Cluster cluster;
        long timePlayedBeat;

        public BeatStats(Cluster cluster, long timePlayedBeat) {
            this.cluster = cluster;
            this.timePlayedBeat = timePlayedBeat;
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean filterEventsEnabled) {
        super.setFilterEnabled(filterEventsEnabled);
        if (filterEventsEnabled) {
            drumSounds.open();
            bbs.showUp();
        } else {
            drumSounds.close();
            bbs.close();
        }
    }

    /**
     * @return the beatClusterTimeMs
     */
    public int getBeatClusterTimeMs() {
        return beatClusterTimeMs;
    }

    /**
     * @param beatClusterTimeMs the beatClusterTimeMs to set
     */
    public void setBeatClusterTimeMs(int beatClusterTimeMs) {
        this.beatClusterTimeMs = beatClusterTimeMs;
        getPrefs().putFloat("VirtualDrummer.beatClusterTimeMs",beatClusterTimeMs);
    }

    /**
     * @return the beatClusterVelocityPPS
     */
    public float getBeatClusterVelocityPPS() {
        return beatClusterVelocityPPS;
    }

    /**
     * @param beatClusterVelocityPPS the beatClusterVelocityPPS to set
     */
    public void setBeatClusterVelocityPPS(float beatClusterVelocityPPS) {
        this.beatClusterVelocityPPS = beatClusterVelocityPPS;
        getPrefs().putFloat("VirtualDrummer.beatClusterVelocityPPS",beatClusterVelocityPPS);
    }

    /**
     * @return the minBeatRepeatIntervalMs
     */
    public int getMinBeatRepeatIntervalMs() {
        return minBeatRepeatIntervalMs;
    }

    /**
     * @param minBeatRepeatIntervalMs the minBeatRepeatIntervalMs to set
     */
    public void setMinBeatRepeatIntervalMs(int minBeatRepeatIntervalMs) {
        this.minBeatRepeatIntervalMs = minBeatRepeatIntervalMs;
        getPrefs().putFloat("VirtualDrummer.minBeatRepeatIntervalMs",minBeatRepeatIntervalMs);
    }
}
