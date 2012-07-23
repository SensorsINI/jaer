/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 *  This is the controller in the semi-MVC architecture setup we got going on here.
 * 
 * @author Peter
 */
public class NetController<LayerType extends BasicLayer,LayerGlobalType extends Controllable,UnitGlobalType extends Controllable> {
    
    public SpikeStack<LayerType,Spike> net;
    public UnitGlobalType unitGlobals;
    public LayerGlobalType layerGlobals;
    
    public int inputTime=0; // Tracker of time to add input events from
    
    NetPlotter view;
    
    public static enum Types {LIFNET,BINTHRESHNET};
    
    
    public boolean enable=true;
    public float timeScaling=1f;
    
    
    public NetController()
    {   this(Types.LIFNET);        
    }
    
    public NetController(Types t)
    {
        switch(t)
        {
            case LIFNET:
                
                STPLayer.Factory<STPLayer> layerFactory=new STPLayer.Factory();
                LIFUnit.Factory unitFactory=new LIFUnit.Factory();        
                net=new SpikeStack(layerFactory,unitFactory);

                layerGlobals = (LayerGlobalType) layerFactory.glob;
                unitGlobals = (UnitGlobalType) unitFactory.glob;            
            
                break;
                
            case BINTHRESHNET:
                throw new UnsupportedOperationException("Not supported yet");                
        }
        view=net.plot;
    }
    
    public void readXML()
    {
        net.read.readFromXML(net);         
    }
    
    public void startDisplay()
    {   
        net.plot.followState();
    }
    
    /** Return a representation */
//    public NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> getSTDPLIFControls()
//    {
//        
//    }
    
    public void reset()
    {
        net.reset();
    }
    
    
    /** Shorthand for enabling/disabling forward connections: pass a string like 
     * "1101", to indicate first layer CAN send forward, second can't, etc.
     * @param s 
     */
    public void setForwardStrengths(boolean[] vals)
    {   for (int i=0; i<vals.length; i++) 
            net.lay(i).fwdSend=vals[i]?1:0;
    }
    
    /** Shorthand for enabling/disabling backwards connections: pass a string like 
     * "1101", to indicate first layer CAN send forward, second can't, etc.
     * @param s 
     */
    public void setBackwardStrengths(boolean[] vals)
    {   for (int i=0; i<vals.length; i++) 
            net.lay(i).backSend=vals[i]?1:0;
    }
    
    /** Set Lateral Connection Strengths */
    public void setLateralStrengths(boolean[] vals)
    {   for (int i=0; i<vals.length; i++) 
            net.lay(i).latSend=vals[i]?1:0;
    }
    
    public void addAllControls()
    {    
        view.addControls(makeControlPanel());
        
//        view.addControls(new Controls());
//        view.addControls(net.getControls());
//        view.addControls(unitGlobals);
//        view.addControls(layerGlobals);
//        for (BasicLayer l:net.getLayers())
//        {
//            view.addControls(l.getControls());
//        }
    }
    
    public ControlPanel makeControlPanel()
    {
        ControlPanel cp=new ControlPanel();
        
        cp.addController(new Controls());
        cp.addController(net.getControls());
        cp.addController(unitGlobals);
        cp.addController(layerGlobals);
        for (BasicLayer l:net.getLayers())
        {
            cp.addController(l.getControls());
        }
        return cp;
    }
    
    
    /** Add a bunch of events to the network input for a given timespan at a given rate. */
    public void generateInputSpikes(float rate,int timeMicros,int unit,int layer)
    {
        int timeStep=(int)(1000000/rate); // timestep in us
        if (timeStep==0)
            System.out.println("Timestep is zero here.  Can't do that.");
        
        
        int endTime=inputTime+timeMicros;
        
        while(inputTime<endTime)
        {   //int number=i<nEvents/2?8:2;
            net.addToQueue(new Spike(inputTime,unit,layer));            
            inputTime+=timeStep;
        }
    }
    
    
    
    public void realTimeRun(float forSeconds)
    {
        int plotIntervalMillis=30;
        int plotIntervalMicros=plotIntervalMillis*1000;
        
        net.plot.realTime=true;
        net.plot.followState();
                
        int finalTime=net.time+(int)(1000000*forSeconds);
        
        
        int targetNetTime=net.time;
        long targetSystemTime=System.currentTimeMillis();
        
        // Loop along, allowing the network to progress up to a certain time 
        // in each iteration.
        while (net.time<finalTime && enable)
        {
            targetNetTime+=plotIntervalMicros*timeScaling;
            targetSystemTime+=plotIntervalMillis;
            
            net.eatEvents(targetNetTime);
            
            try {
                long sleepTime=Math.max(targetSystemTime-System.currentTimeMillis(),0);
                System.out.println(sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        enable=true;
               
    }
    
    
    
    public class Controls extends Controllable
    {

        @Override
        public String getName() {
            return "Simulation Controls";
        }        

        /** Stop the simulation */
        public void doSTOP()
        {
            enable=false;
        }

        /**
         * @return the timeScaling
         */
        public float getTimeScaling() {
            return timeScaling;
        }

        /**
         * @param timeScaling the timeScaling to set
         */
        public void setTimeScaling(float timeScalin) {
            timeScaling = timeScalin;
        }
                
    }
    
    
    
    
    
    
}
