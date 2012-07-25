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
    
    public NetPlotter view;
    
    public SpikeRecorder recorder;
    
    public static enum Types {STP_LIF,STATIC_LIF,BINTHRESHNET};
    
    
    public boolean enable=true;
    public float timeScaling=1f;
    
    
    public NetController()
    {   this(Types.STP_LIF);        
    }
    
    public NetController(Types t)
    {
        switch(t)
        {
            case STATIC_LIF:
                
                BasicLayer.Factory bLayerFactory=new BasicLayer.Factory();
                LIFUnit.Factory unitFactoryy=new LIFUnit.Factory();        
                net=new SpikeStack(bLayerFactory,unitFactoryy);
                view=new NetPlotter(net);

                layerGlobals = (LayerGlobalType) bLayerFactory.glob;
                unitGlobals = (UnitGlobalType) unitFactoryy.glob;            
            
                break;                
            
            case STP_LIF:
                
                STPLayer.Factory<STPLayer> layerFactory=new STPLayer.Factory();
                LIFUnit.Factory unitFactory=new LIFUnit.Factory();        
                net=new SpikeStack(layerFactory,unitFactory);
                view=new NetPlotter(net);

                layerGlobals = (LayerGlobalType) layerFactory.glob;
                unitGlobals = (UnitGlobalType) unitFactory.glob;            
            
                break;
                
            case BINTHRESHNET:
                throw new UnsupportedOperationException("Not supported yet");                
        }
        
        recorder=new SpikeRecorder(net);
//        view=net.plot;
    }
    
    public void readXML()
    {
        net.read.readFromXML(net);         
    }
    
    public void startDisplay()
    {   
        view.followState();
    }
    
    
    public void setRecordingState(boolean state)
    {   
                
        recorder.setRecodingState(state);
    }
    
    public void saveRecoding()
    {
        recorder.printToFile();
    }
    
    
    
    /** Return a representation */
//    public NetController<STPLayer,STPLayer.Globals,LIFUnit.Globals> getSTDPLIFControls()
//    {
//        
//    }
    
    public void reset()
    {
        net.reset();
        view.reset();
    }
    
    
    /** Enable Forward Connections */
    public void setForwardStrengths(boolean[] vals)
    {   for (int i=0; i<vals.length; i++) 
            net.lay(i).fwdSend=vals[i]?1:0;
    }
    
    /** Enable Backward Connections */
    public void setBackwardStrengths(boolean[] vals)
    {   for (int i=0; i<vals.length; i++) 
            net.lay(i).backSend=vals[i]?1:0;
    }
    
    /** Enable Lateral Connections */
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
    
    public void simulate()
    {
        simulate(Float.POSITIVE_INFINITY);
    }
    
    public void simulate(float forSeconds)
    {   simulate(forSeconds,true);        
    }
    
    public void simulate(boolean realTime)
    {
        simulate(Float.POSITIVE_INFINITY,realTime);
    }
    
    public void simulate(float forSeconds,boolean realtime)
    {
        int plotIntervalMillis=30;
        int plotIntervalMicros=plotIntervalMillis*1000;
        
        view.realTime=true;
        view.followState();
                
        int finalTime=net.time+(int)(1000000*forSeconds);
        
        
//        float timeScale=realtime?timeScaling:Float.POSITIVE_INFINITY;
        
        int targetNetTime=net.time;
        long targetSystemTime=System.currentTimeMillis();
        
        if (realtime)
        {
            // Loop along, allowing the network to progress up to a certain time 
            // in each iteration.
            while (net.time<finalTime && enable)
            {
                targetNetTime+=plotIntervalMicros*timeScaling;
                targetSystemTime+=plotIntervalMillis;

                net.eatEvents(targetNetTime);

                try {
                    long sleepTime=Math.max(targetSystemTime-System.currentTimeMillis(),0);
    //                System.out.println(sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    Logger.getLogger(NetController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            enable=true;
        }
        else
            net.eatEvents(finalTime);
        
    }
    
    
    
    
    
    
    
    public class Controls extends Controllable
    {

        @Override
        public String getName() {
            return "Simulation Controls";
        }        

        /** Stop the simulation */
        public void doSTOP()
        {   net.enable=false;
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
        
        public void doSaveRecording()
        {
            recorder.printToFile();
        }
                
    }
    
    
    
    
    
    
}
