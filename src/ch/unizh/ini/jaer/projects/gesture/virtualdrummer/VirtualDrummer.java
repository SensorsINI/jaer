/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.eventprocessing.tracking.ClusterTrackerInterface;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 * Virtual drummer. Demonstration of fast visual tracking and inference of virtual drum set capability.
 * @author Tobi Delbruck, Eric Ryu, Jun Haeng Lee
 */
@Description("Virtual drummer demonstration")
public class VirtualDrummer extends EventFilter2D implements FrameAnnotater,Observer{

    public static DevelopmentStatus getDevelopmentStatus (){
        return DevelopmentStatus.Alpha;
    }
    // properties
//    private int beatClusterTimeMs = getPrefs().getInt("VirtualDrummer.beatClusterTimeMs", 10);
    private float beatClusterVelocityPPS = getPrefs().getFloat("VirtualDrummer.beatClusterVelocityPPS",50f); // PPS: Pixels per second
    private int minBeatRepeatIntervalMs = getPrefs().getInt("VirtualDrummer.minBeatRepeatInterval",1000);
//    private int subPacketRatio = getPrefs().getInt("VirtualDrummer.subPacketRatio", 1);
    private int numVelocityToCheck = getPrefs().getInt("VirtualDrummer.numVelocityToCheck",5);
    // vars
    private Hashtable<RectangularClusterTracker,BeatStats> playedBeatClusters = new Hashtable();
//    private RectangularClusterTracker tracker;
    private ClusterTrackerInterface tracker;
    private DrumSounds drumSounds = new DrumSounds(DrumSounds.Type.Sampled);
    private JFrame bbs = null;
    final int NUM_DRUMS = 2; // number of drums - 2 means left and right
    private DetectedBeat[] detectedBeats = new DetectedBeat[ NUM_DRUMS ];
    private int[] lastPlayedTime = new int[ NUM_DRUMS ];
    public enum TrackerToUse{
        RectangularClusterTracker, BlurringFilter2DTracker
    };
    private TrackerToUse trackerToUse = TrackerToUse.valueOf(getPrefs().get("VirtualDrummer.trackerToUse",TrackerToUse.BlurringFilter2DTracker.toString()));

    public VirtualDrummer (AEChip chip){
        super(chip);
        String key = "Drummer";
        setPropertyTooltip(key,"beatClusterVelocityPPS","required vertical velocity of cluster to generate beat");
        setPropertyTooltip(key,"numVelocityToCheck","will check this many past velocity samples for change in sign to vertical velocity to generate beat");
        setPropertyTooltip(key,"minBeatRepeatIntervalMs","a hand can only generate a beat at this minimum interval in ms");
//        setPropertyTooltip(key, "beatClusterTimeMs", "required lifetime of cluster to generate drumbeat");
//        setPropertyTooltip(key, "subPacketRatio", "increasing factor of tracking update rate");
        setPropertyTooltip(key,"trackerToUse","Determines which tracker to use");
        setTrackerToUse(trackerToUse);
        bbs = new BeatBoxSetting(drumSounds);


    }
    final int BEAT_FRAMES_TO_DRAW = 10; // num frames to draw visual beat indication

    @Override
    public synchronized void annotate (GLAutoDrawable drawable){
        ( (FrameAnnotater)tracker ).annotate(drawable); // draw tracker all the time
        // for each detected beat, render it until the counter is finished
        for ( int i = 0 ; i < NUM_DRUMS ; i++ ){ // iterates through drums
            DetectedBeat b = detectedBeats[i];
            if ( b == null ){
                continue; // no beat or expired
            }
            b.draw(drawable); // if beat, draw it visually
            if ( b.isDoneRendering() ){
                detectedBeats[i] = null; // remove it when it's been shown enough
            }
        }
    }

    @Override
    public synchronized EventPacket filterPacket (EventPacket in){

        ( (EventFilter2D)tracker ).filterPacket(in); // will call update during iteration at least every updateIntervalMs

        update(this,new UpdateMessage(this,in,in.getLastTimestamp()));

        return in;
    }
    private class HandClusters{
        ClusterInterface left, right;
    }
    private HandClusters handClusters = new HandClusters();

    private void findHandClusters (){
//           Collections.sort(tracker.getClusters());
        // find the two clusters with largest mass
        handClusters.left = null;
        handClusters.right = null;
        float mass1 = Float.NEGATIVE_INFINITY;
        ClusterInterface one = null;
        for ( ClusterInterface c:tracker.getClusters() ){
            if ( !c.isVisible() ){
                continue;
            }
            if ( c.getMass() > mass1 ){
                mass1 = c.getMass();
                one = c;
            }
        }
        float mass2 = Float.NEGATIVE_INFINITY;
        ClusterInterface two = null;
        for ( ClusterInterface c:tracker.getClusters() ){
            if ( (c == one) || !c.isVisible() ){
                continue;
            }
            if ( c.getMass() > mass2 ){
                mass2 = c.getMass();
                two = c;
            }
        }
        if ( (one == null) && (two == null) ){
            return;
        }
        // assign hands. either we have no clusters, or just one, or one and two
        if ( (one != null) && (two == null) ){
            if ( one.getLocation().x < (chip.getSizeX() / 2) ){
                handClusters.left = one;
            } else{
                handClusters.right = one;
            }
        } else if ( one.getLocation().x < two.getLocation().x ){ // we have two clusters
            handClusters.left = one;
            handClusters.right = two;
        } else{
            handClusters.left = two;
            handClusters.right = one;
        }
//        System.out.println("left=" + handClusters.left + " right=" + handClusters.right);
    }

    /** Returns true if beat has been detected from this cluster.
    @param c the cluster to test.
     * @return false if cluster is null or not visible. Returns true if a beat has been detected.
     */
    private boolean testGenerateBeat (ClusterInterface c){
        final boolean debug = false;
        StringBuilder sb;
        if ( debug ){
            sb = new StringBuilder();
        }
        if ( c == null ){
            return false;
        }
        if ( !c.isVisible() ){
            return false;
        }
        int numValidNegativeVelocity = numVelocityToCheck - 1; // start with all test points previous to last one assumed good
        boolean ret = false;

        if ( (c.getPath() != null) && (c.getPath().size() >= numVelocityToCheck) ){ // have enough samples
            int nPoints = c.getPath().size();
//           System.out.print("Cluster_"+((BlurringFilter2DTracker.Cluster)c).getClusterNumber()+": ");
            // check latest five y-axis velocities
            for ( int i = 0 ; i < numVelocityToCheck ; i++ ){
                Point2D.Float vel = c.getPath().get(nPoints - 1 - i).velocityPPT; // first last point, one before last, two before last....
                if ( vel == null ){
                    return false; // don't have enough measurements yet
                }
                float vely = vel.y;
                float velx = vel.x;
                if ( debug ){
                    sb.append(String.format("%.3g, ",vely));
                }
                if ( i == 0 ){  // for last point
                    if ( vely >= 0 ){
                        ret = true;  // might be beat, if rest of test holds up
                        if ( debug ){
                            sb.append("true ");
                        }
                    } else{
                        if ( debug ){
                            sb.append("false ");
                        } else{
                            break;
                        }
                    }
                } else{ // other points before last
                    if ( vely >= 0 ){
                        ret = false; // if any previous points were upwards, not a beat
                        if ( debug ){
                            sb.append("false ");
                        } else{
                            break;
                        }
                    } else{ // filter out very small movements
                        if ( (Math.abs(vely) < (beatClusterVelocityPPS * 1e-6f * AEConstants.TICK_DEFAULT_US)) || (Math.abs(velx) > Math.abs(vely)) ){
                            // if vely is very small or velx > vely, then reduce count of valid velocities
                            numValidNegativeVelocity--;
                        }
                        if ( debug ){
                            sb.append("true ");
                        }
                    }
                }
            }
        }

        if ( ret && (numValidNegativeVelocity == 0) ){
            // if still true but prior velocities were all bad, then still don't report beat
            if ( debug ){
                sb.append(String.format(" numValidNegativeVelocity=%d",numValidNegativeVelocity));
            }
            ret = false;
        }
        if ( debug ){
            sb.append(ret + " ");
        }
        if ( debug ){
            System.out.println(sb);
        }
        return ret; // might still have beat
    }

    @Override
    public void resetFilter (){
        ( (EventFilter2D)tracker ).resetFilter();
        for ( int i = 0 ; i < NUM_DRUMS ; i++ ){
            lastPlayedTime[i] = 0;
        }
    }

    @Override
    public void initFilter (){
        ( (EventFilter2D)tracker ).initFilter();
    }

    public void annotate (float[][][] frame){
        log.warning("cannot annotate in Graphic2D - switch to OpenGL");
    }

    public void annotate (Graphics2D g){
        log.warning("cannot annotate in Graphic2D - switch to OpenGL");
    }

    // statistics of last drum beat
    private class BeatStats{
        RectangularClusterTracker cluster;
        long timePlayedBeat;

        public BeatStats (RectangularClusterTracker cluster,long timePlayedBeat){
            this.cluster = cluster;
            this.timePlayedBeat = timePlayedBeat;
        }
    }

    @Override
    public synchronized void setFilterEnabled (boolean filterEventsEnabled){
        super.setFilterEnabled(filterEventsEnabled);
        if ( bbs == null ){
            return;
        }
        if ( filterEventsEnabled ){
            bbs.setVisible(true);
        } else{
            bbs.setVisible(false);
        }
    }

    /**
     * @return the beatClusterVelocityPPS
     */
    public float getBeatClusterVelocityPPS (){
        return beatClusterVelocityPPS;
    }

    /**
     * @param beatClusterVelocityPPS the beatClusterVelocityPPS to set
     */
    public void setBeatClusterVelocityPPS (float beatClusterVelocityPPS){
        this.beatClusterVelocityPPS = beatClusterVelocityPPS;
        getPrefs().putFloat("VirtualDrummer.beatClusterVelocityPPS",beatClusterVelocityPPS);
    }

    /**
     * @return the minBeatRepeatIntervalMs
     */
    public int getMinBeatRepeatIntervalMs (){
        return minBeatRepeatIntervalMs;
    }

    /**
     * @param minBeatRepeatIntervalMs the minBeatRepeatIntervalMs to set
     */
    public void setMinBeatRepeatIntervalMs (int minBeatRepeatIntervalMs){
        this.minBeatRepeatIntervalMs = minBeatRepeatIntervalMs;
        getPrefs().putFloat("VirtualDrummer.minBeatRepeatIntervalMs",minBeatRepeatIntervalMs);
    }
//    public int getSubPacketRatio() {
//        return subPacketRatio;
//    }
//
//    public void setSubPacketRatio(int subPacketRatio) {
//        int old = this.subPacketRatio;
//        this.subPacketRatio = subPacketRatio;
//        getPrefs().putInt("VirtualDrummer.subPacketRatio", subPacketRatio);
//        support.firePropertyChange("subPacketRatio", old, this.subPacketRatio);
//    }

    /**
     * @return the trackerToUse
     */
    public TrackerToUse getTrackerToUse (){
        return trackerToUse;
    }

    /**
     * Also contructs the appropriate tracker.
     *
     * @param trackerToUse the trackerToUse to set
     */
    public synchronized void setTrackerToUse (TrackerToUse trackerToUse){
        TrackerToUse old = this.trackerToUse;
        this.trackerToUse = trackerToUse;
        getPrefs().put("VirtualDrummer.trackerToUse",trackerToUse.toString());
        if ( tracker != null ){
            ( (EventFilter2D)tracker ).resetFilter();
        }
        switch ( trackerToUse ){
            case RectangularClusterTracker:
                tracker = new RectangularClusterTracker(chip);
        }
        ( (EventFilter2D)tracker ).addObserver(this);
        setEnclosedFilterChain(new FilterChain(chip));
        getEnclosedFilterChain().add((EventFilter2D)tracker);
        ( (EventFilter2D)tracker ).setEnclosed(true,this);
        ( (EventFilter2D)tracker ).setFilterEnabled(isFilterEnabled());

        if ( chip.getFilterFrame() != null ){
            chip.getFilterFrame().rebuildContents();
        }

        getSupport().firePropertyChange("trackerToUse",old.toString(),trackerToUse.toString()); // TODO may not be right property change to update GUI
    }

    /** Handles updates from enclosed tracker during iteration over packet.
     *
     * @param o the tracker
     * @param arg the update message
     */
    @Override
	public void update (Observable o,Object arg){
        UpdateMessage msg = (UpdateMessage)arg;
//        System.out.println("update from tracker");

        if ( (msg.timestamp < lastPlayedTime[0]) || (msg.timestamp < lastPlayedTime[1]) ){
            resetFilter();
            return;
        }

        findHandClusters();
        if ( ((msg.timestamp - lastPlayedTime[0]) > (minBeatRepeatIntervalMs << 10)) && testGenerateBeat(handClusters.left) ){
            drumSounds.play(0,127);
            detectedBeats[0] = new DetectedBeat(handClusters.left,"LEFT"); // create the detected beat. Rendering will remove this eventually.
            lastPlayedTime[0] = msg.timestamp;
        }
        if ( ((msg.timestamp - lastPlayedTime[1]) > (minBeatRepeatIntervalMs << 10)) && testGenerateBeat(handClusters.right) ){
            drumSounds.play(1,127);
            detectedBeats[1] = new DetectedBeat(handClusters.right,"RIGHT"); // create the detected beat. Rendering will remove this eventually.
            lastPlayedTime[1] = msg.timestamp;
        }
    }

    /**
     * @return the numVelocityToCheck
     */
    public int getNumVelocityToCheck (){
        return numVelocityToCheck;
    }

    /**
     * @param numVelocityToCheck the numVelocityToCheck to set
     */
    public void setNumVelocityToCheck (int numVelocityToCheck){
        this.numVelocityToCheck = numVelocityToCheck;
        getPrefs().putInt("VirtualDrummer.numVelocityToCheck",numVelocityToCheck);
    }
}
