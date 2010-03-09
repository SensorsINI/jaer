/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

/**
 * Virtual drummer subclass
 * @author tobi
 */
public class VirtualDrummer extends RectangularClusterTracker{

    private float tauMs=getPrefs().getFloat("VirtualDrummer.tauMs", 1);
    private float beatClusterTimeMs=getPrefs().getInt("VirtualDrummer.beatClusterTimeMs",10);
    private float beatClusterVelocityPPS=getPrefs().getFloat("VirtualDrummer.beatClusterVelocityPPS",100f);

    public VirtualDrummer(AEChip chip) {
        super(chip);
    }

    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        // java generics
        super.filterPacket(in);
        for(Cluster c:getClusters()){
            if(c.getLifetime()>getBeatClusterTimeMs()*1000 && c.getVelocityPPS().y <-getBeatClusterVelocityPPS()){
                System.out.println("beat");
            }
        }
        return in;
    }

    /**
     * @return the tauMs
     */
    public float getTauMs() {
        return tauMs;
    }

    /**
     * @param tauMs the tauMs to set
     */
    public void setTauMs(float tauMs) {
        this.tauMs = tauMs;
        getPrefs().putFloat("VirtualDrummer.tauMs",tauMs);
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
