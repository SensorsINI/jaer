/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */





package ch.unizh.ini.jaer.projects.integrateandfire;

import ch.unizh.ini.jaer.projects.integrateandfire.Network.Unit;

/**
 *
 * @author tobi
 */
public class Neuron implements Unit{

    private float vmem=0;       // Membrane Potential at previous spike
    private int tlast=-10000;   // microsecond timestamp of previous vmem update
    private int slast=-10000;   // Timestamp of last spike: For refractory effects
    public float thresh=1;      // Threshold (arb units)
    public float tau=0.2f;      // Time Constant (seconds)
    public float sat=0.05f;     // Time constant of firing saturation
    
    public byte group=0;
    
    public boolean doublethresh=false;  // Spike also on negative threshold?
    
    public String name="";          // Does your neuron have a name?
        
    
    
    //public boolean trackFF=false;            // Whether to track the firing frequency;
    //public float FF;
    //public float nFFtrack;                   // 

    
    
    public boolean spike(float w,int timestamp){
        // Get current time, calculate Vmem decay, update for this input
        // Timestamp is a time in us.  
                
        //float now=System.nanoTime()/1000000;
        
        update(timestamp);
        
        double dts=(timestamp-slast)/1000000.; // Time in seconds
        
        if (sat==0 && dts==0)
            dts=1; // To deal with 0/0 limit case
        
        vmem=vmem+w*(float)(1-Math.exp(-dts/sat));
            
        
        // Fire a spike if thresh broken, otherwise don't
        if (vmem>thresh || (doublethresh && vmem<-thresh)) // Micro-optimization: better to split into two statements?
        {   vmem=0;
            slast=timestamp;
            return true;
        }
        return false;
    }
    
    public void reset()
    {    vmem=0;         // Membrane Potential at previous spike
         tlast=-10000;   // microsecond timestamp of previous vmem update
         slast=-10000;
    }
    
    private void update(int timestamp){
        // Timestamp here referes to the timstamp provided by events, in 
        
        double dt=(timestamp-tlast)/1000000.; // Time in seconds
        
        if (dt<0){ vmem=0; return; } // TODO: Do this properly
        // This should only happen when the timestamp wraps, and will result in 
        // one event ignored every 3.2 seconds.  Shouldn't be a problem if tau is small.
        
        //float now=(float)timestamp/1000000;
        //System.out.println(dt);
        vmem=(float)(vmem*Math.exp(-dt/tau));
        tlast=timestamp;
        
    }
    
    
    /*
    private void update(){
        int now=(int) System.nanoTime()/1000;
        
        vmem=(float)(vmem*Math.exp((tlast-now)/tau));
        tlast=now;
    }*/

    public float get_vmem(int timestamp){
        // Gets the most recent membrane voltage without changing anything
        //update();
        double dt=(timestamp-tlast)/1000000.; // Time in seconds
        if (dt<0){ return 0; } // TODO: Do this properly
        // This should only happen when the timestamp wraps, and will result in 
        // one event ignored every 3.2 seconds.  Shouldn't be a problem if tau is small.
        
        //float now=(float)timestamp/1000000;
        //System.out.println(dt);
        //System.out.println("Current: "+timestamp+"     Last: "+tlast);
        
        return (float)(vmem*Math.exp(-dt/tau));
        
        
        
        //update(timestamp);
        //return vmem;
        
        /*
        double dt=(timestamp-tlast)/1000000.; // Time in seconds
        if (dt<0){ return 0f; } // TODO: Do this properly
        return (float)(vmem*Math.exp(-dt/tau));
                */
    }

    @Override
    public float getVsig(int timestamp) {
        return get_vmem(timestamp);
    }

    @Override
    public float getAsig() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getInfo() {
        return "Thresh: "+thresh;
    }

}

