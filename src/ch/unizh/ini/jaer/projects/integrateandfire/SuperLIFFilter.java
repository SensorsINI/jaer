/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Peter
 */
abstract public class SuperLIFFilter  extends EventFilter2D {
    /* @Description: This class provides some general UI tools for dealing with 
     * neural networks.  It is meant to be extended
     * 
     * 
     */
    
    String networkStatus="No network loaded. Click 'Load_Network'";
    
    // Do these really need to be here?
    float thresh=2;             // Neuron threshold
    float tau= .02f;            // Neuron time-constant
    float sat= .005f;            // Neuron time-constant
    public enum polarityOpts {all,on,off,diff};         // <1: just off events, >1: Just on events, 0: both;
    polarityOpts polarityPass=polarityOpts.diff;
    
    boolean enableNetwork=true; // Enable events to pass through the network.  This class just provides the get/set interface.  It's up to subclasses to impement it.
    
    int lastTimeStamp=-100000;
    
    boolean doubleThresh=false; // DoubleThresh
    
    LIFcontroller NN;            // Reference to general Network Interface
    
    //==========================================================================
    // Obligatory Overrides
    
    @Override abstract public EventPacket<?> filterPacket(EventPacket<?> P);
        
    @Override abstract public void resetFilter();
    
    @Override abstract public void initFilter();
                
    //==========================================================================
    // Initialization, UI
    
    public SuperLIFFilter(AEChip  chip)
    {   super(chip);
        
        setPropertyTooltip("input","polarityPass", "All: treat ON/OFF the same.  on/off: filter ON/OFF events.  diff:on events entered with negative weight");
        
        final String net="Network";
        setPropertyTooltip(net,"thresh", "Firing threshold");
        setPropertyTooltip(net,"tau", "Membrane Potential Time-Constant");
        setPropertyTooltip(net,"sat", "Spike Recovery Time-Constant");
        setPropertyTooltip(net,"doubleThresh", "Double-Ended Threshold?");
        setPropertyTooltip(net,"enableNetwork", "Enable events to pass through the network");
        setPropertyTooltip(net,"networkStatus", "Current state of the network");
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
    
    
    public polarityOpts getPolarityPass() {
        return this.polarityPass;
    }
    
    public void setPolarityPass(polarityOpts dt) {
        getPrefs().put("NetworkFilter.PolarityPass",dt.toString());
        support.firePropertyChange("polarityPass",this.polarityPass,dt);
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
    
    
    public boolean getEnableNetwork() {
        return this.enableNetwork;
    }
    
    public void setEnableNetwork(boolean dt) {
        getPrefs().putBoolean("ClassItUp.EnableNetwork",dt);
        support.firePropertyChange("enableNetwork",this.enableNetwork,dt);
        this.enableNetwork = dt;
    }   
    
    public void doReset_All_Neurons()
    {   NN.reset();
    }
    
    public void modifyNetworkStatus(String status)
    {   this.networkStatus=status;
        setNetworkStatus(status);    
    }
    
    public void setNetworkStatus(String status)
    {   getPrefs().put("SuperNetFilter.networkStatus",this.networkStatus);   
        
         support.firePropertyChange("networkStatus",this.networkStatus,this.networkStatus);
         this.setChanged();
    }
    
    public String getNetworkStatus()
    {   return networkStatus;
    }
}

        