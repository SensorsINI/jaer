/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Peter
 */
abstract public class SuperNetFilter  extends EventFilter2D {
    /* @Description: This class provides some general UI tools for dealing with 
     * neural networks.  It is meant to be extended
     * 
     * 
     */
    
    // Do these really need to be here?
    float thresh=2;             // Neuron threshold
    float tau= .02f;            // Neuron time-constant
    float sat= .005f;            // Neuron time-constant
    int downsamp=1;             // Downsampling factor
    public enum polarityOpts {all,on,off,diff};         // <1: just off events, >1: Just on events, 0: both;
    polarityOpts polarityPass=polarityOpts.diff;
    
    
    int lastTimeStamp=-100000;
    
    boolean doubleThresh=false; // DoubleThresh
    
    SuperNet NN;            // Reference to general Network Interface
    
    //==========================================================================
    // Obligatory Overrides
    
    @Override abstract public EventPacket<?> filterPacket(EventPacket<?> P);
        
    @Override abstract public void resetFilter();
    
    @Override abstract public void initFilter();
                
    //==========================================================================
    // Initialization, UI
    
    public SuperNetFilter(AEChip  chip)
    {   super(chip);
        
        setPropertyTooltip("Preprocessing","downSamp", "Downsampling");
        
        setPropertyTooltip("General","thresh", "Firing threshold");
        setPropertyTooltip("General","tau", "Membrane Potential Time-Constant");
        setPropertyTooltip("General","sat", "Spike Recovery Time-Constant");
        setPropertyTooltip("General","doubleThresh", "Double-Ended Threshold?");
        setPropertyTooltip("General","polarityPass", "all: treat all events as spikes.  off/on: keep only off/on events.  diff:on gives +ive input, off gives -ive.");
        
    }
    
    public int getLastTimestamp()
    {   if (out!=null) lastTimeStamp=out.getLastTimestamp();
        return lastTimeStamp;
    }
    
    public float getThresh() {
        return this.thresh;
    }
    
    public void setThresh(float thresh) {
        getPrefs().putFloat("NetworkFilter.Thresh",thresh);
        support.firePropertyChange("thresh",this.thresh,thresh);
        this.thresh=thresh;
        NN.setThresholds(thresh);
    }
    
    public float getTau() {
        return this.tau;
    }
    
    public void setTau(float dt) {
        getPrefs().putFloat("NetworkFilter.Tau",dt);
        support.firePropertyChange("tau",this.tau,dt);
        this.tau=dt;
        NN.setTaus(dt);
    }
    
    public float getSat() {
        return this.sat;
    }
    
    public void setSat(float dt) {
        getPrefs().putFloat("NetworkFilter.Sat",dt);
        support.firePropertyChange("sat",this.sat,dt);
        this.sat=dt;
        NN.setSats(dt);
    }
    
    public int getDownsamp() {
        return this.downsamp;
    }
    
    public void setDownsamp(int dt) {
        getPrefs().putFloat("NetworkFilter.Downsamp",dt);
        support.firePropertyChange("downsamp",this.downsamp,dt);
        this.downsamp = dt;
        this.resetFilter();
    }   
    
    public polarityOpts getPolarityPass() {
        return this.polarityPass;
    }
    
    public void setPolarityPass(polarityOpts dt) {
        getPrefs().put("NetworkFilter.PolarityPass",dt.toString());
        support.firePropertyChange("polarityPass",this.downsamp,dt);
        this.polarityPass = dt;
    }   
    
    public boolean getDoubleThresh() {
        return this.doubleThresh;
    }
    
    public void setDoubleThresh(boolean dt) {
        getPrefs().putBoolean("NetworkFilter.DoubleThresh",dt);
        support.firePropertyChange("doubleThresh",this.doubleThresh,dt);
        this.doubleThresh = dt;
        NN.setDoubleThresh(dt);
    }   
    
    public void doReset_All_Neurons()
    {   NN.reset();
    }
    
}

        