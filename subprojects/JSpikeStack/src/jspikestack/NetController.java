/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.Component;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

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
    
    public void readXML(String loc)
    {
        net.read.readFromXML(net,new File(loc));         
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
    
    
    public void plotRaster()
    {   plotRaster("");
    }
    
    public void plotRaster(String title)
    {
        if (recorder.spikes==null)
        {   JOptionPane.showMessageDialog(null,"No spikes have been recorded","No Spike Recording",JOptionPane.ERROR_MESSAGE);
        }
        view.raster(recorder.spikes,title);
    }
    
    
    public void reset()
    {
        net.reset();
        view.reset();
        recorder.clear();
        inputTime=0;
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
    
    
    public static class SimulationSettings{
        
        boolean waitForInputs=false;     // True if network stops when input events run out
        boolean controlledTime=true;      // True if simulation should run slower than max speed of CPU
        float timeScaling=1;        // If controlledTime==true, scale factor between real-time and simulation time.  High=fast
        
        float simTimeSeconds=Float.POSITIVE_INFINITY; // Timeout for simulation, in simulation time.
        
    }
    
    public void simulate()
    {
        simulate(new SimulationSettings());
    }
    
    public void simulate(final boolean isControlledTime)
    {
        SimulationSettings sim=new SimulationSettings();
        sim.controlledTime=isControlledTime;
        
        simulate(sim);
    }
    
    public void simulate(final float simulationTime)
    {
        SimulationSettings sim=new SimulationSettings();
//        sim.controlledTime=isControllerTime;
        sim.simTimeSeconds=simulationTime;
        
        simulate(sim);
    }
    
    public void simulate(boolean isControlledTime, float simulationTime)
    {
        SimulationSettings sim=new SimulationSettings();
        sim.controlledTime=isControlledTime;
        sim.simTimeSeconds=simulationTime;
        
        simulate(sim);
    }
    
    public void printStats()
    {
        
        
        
        String s=recorder.nSpikes() +" spikes recorded\n";
        
//        for (int i=0; i<net.nLayers(); i++)
//        {
//            for (int j=0; j<)
//            
//        }
        
        System.out.println(s);
    }
    
    
    
    public void simulate(SimulationSettings settings)
    {
        
        net.liveMode=settings.waitForInputs;
        
        int finalTime;
//        if (settings.simTimeSeconds>Integer.MAX_VALUE/1000000)
//            finalTime=Integer.MAX_VALUE;
//        else
            finalTime=net.time+(int)(1000000*settings.simTimeSeconds);
                
        view.realTime=true;
        
        
        if (settings.controlledTime)
        {
            int plotIntervalMillis=30;
            int plotIntervalMicros=plotIntervalMillis*1000;


            int targetNetTime=net.time;
            long targetSystemTime=System.currentTimeMillis();


            // Loop along, allowing the network to progress up to a certain time 
            // in each iteration.
            while (net.time<finalTime && enable)
            {
                targetNetTime+=plotIntervalMicros*settings.timeScaling;
                targetSystemTime+=plotIntervalMillis;

                net.eatEvents(targetNetTime);
                
                if (!net.hasEvents())
                    break;

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
