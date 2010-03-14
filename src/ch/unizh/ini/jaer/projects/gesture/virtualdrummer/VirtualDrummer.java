/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.ArrayList;
import java.util.Hashtable;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

/**
 * Virtual drummer. Demonstration of fast visual tracking and inference of virtual drum set capability.
 * @author Tobi Delbruck, Eric Ryu, Jun Haeng Lee
 */
public class VirtualDrummer extends RectangularClusterTracker {

    private float beatClusterTimeMs = getPrefs().getInt("VirtualDrummer.beatClusterTimeMs", 10);
    private float beatClusterVelocityPPS = getPrefs().getFloat("VirtualDrummer.beatClusterVelocityPPS", 100f);
    private DrumSounds drumSounds = new DrumSounds();
    private Hashtable<Cluster, BeatStats> playedBeatClusters = new Hashtable();

    public VirtualDrummer(AEChip chip) {
        super(chip);
        String key = "Drummer";
        setPropertyTooltip(key, "beatClusterVelocityPPS", "required vertical velocity of cluster to generate beat");
        setPropertyTooltip(key, "beatClusterTimeMs", "required lifetime of cluster to generate drumbeat");
    }

    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
    }

    @Override
    public synchronized EventPacket filterPacket(EventPacket in) {
        super.filterPacket(in);
        for (Cluster c : getClusters()) {
            if (c.getLifetime() > beatClusterTimeMs * 1000
                    && c.getVelocityPPS().y < -getBeatClusterVelocityPPS()
                    && (
                    !playedBeatClusters.contains(c)
                    || System.currentTimeMillis() - playedBeatClusters.get(c).timePlayedBeat > 1000)
                    ) {
                if (c.getLocation().x < chip.getSizeX() / 2) {
                    drumSounds.play(0, 127);
                } else {
                    drumSounds.play(1, 127);
                }
                playedBeatClusters.put(c, new BeatStats(c, System.currentTimeMillis()));
                System.out.println("put "+c);
            }
        }
        // clear hashtable of old entries
        ArrayList<Cluster> toRemove = new ArrayList();
        for (Cluster c : playedBeatClusters.keySet()) {
            if (!getClusters().contains(c)) {
                toRemove.add(c);
            }
        }
        for (Cluster c : toRemove) {
            playedBeatClusters.remove(c);
            System.out.println("removed "+c);
        }

        return in;
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
        } else {
            drumSounds.close();
        }
    }

    /**
     * @return the beatClusterTimeMs
     */
    public float getBeatClusterTimeMs() {
        return beatClusterTimeMs;
    }

    /**
     * @param beatClusterTimeMs the beatClusterTimeMs to set
     */
    public void setBeatClusterTimeMs(float beatClusterTimeMs) {
        this.beatClusterTimeMs = beatClusterTimeMs;
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
    }
}
