/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.Axon;
import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Peter
 */
@Description("This Filter attempts to build a 2D-Histogram of a sound by binning events according to their ISI and time since a trigger (sound surpassing a threshold)")
public class ISItriggeredHist extends SpikeFilter<Axon,Axon.Globals,UnitLIF.Globals,PolarityEvent> {

    int nOutputs=100;
    
    private float triggerThreshold=50; 
    private float triggerDecayTC=10000; // microseconds
    float triggerState=0;
    private int triggerDuration=300000;
    int lastTriggerTime;
    
    boolean triggerOn=false;
    
    TriggeredMap isiMap;
    
    int nTimeBins=25;

    public int getTriggerDuration() {
        return triggerDuration;
    }

    public void setTriggerDuration(int triggerDuration) {
        this.triggerDuration = triggerDuration;
    }
    
    
    
    public class TriggeredMap extends ISIMapper
    {
        
        int startTime;
        
        public TriggeredMap(int nChan,int nOuts)
        {   super(nChan,nOuts);
            
        }
        
        @Override
        public int ev2addr(BasicEvent ev){

            int unit=ev.address>>8;

            int dT=ev.timestamp-lastTimes[ev.x][unit];

            lastTimes[ev.x][unit]=ev.timestamp;

            int timeBin=((ev.timestamp-startTime)*nTimeBins)/getTriggerDuration();

            return isi2bin(dT)+this.nBins*timeBin;

        }

    }
    
    
    public ISItriggeredHist(AEChip chip)
    {
        super(chip);  
        
//        ISIMapper isi=new ISIMapper(64,nOutputs){
//            
//            
//            @Override
//            public int ev2addr(BasicEvent ev){
//                
//                int unit=ev.address>>8;
//        
//                int dT=ev.timestamp-lastTimes[ev.x][unit];
//
//                lastTimes[ev.x][unit]=ev.timestamp;
//
//                int timeBin=(int)Math.floor((ev.timestamp-baseTime)*nTimeBins/triggerDuration);
//                
//                return isi2bin(dT)+this.nBins*timeBin;
//                
//            }
//            
//            
//            
//        };
        
        TriggeredMap isi=new TriggeredMap(64,nOutputs);
        
        isi.setMinFreqHz(300);
        isi.setMaxFreqHz(10000);
        isiMap=isi;
        
        this.axonType=NetController.AxonTypes.STATIC;
                
    }
    
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) 
    {
        
        
         if (wrapNet==null)
             return in;
        // Initialize Remapper
        if (!wrapNet.mapper.isBaseTimeSet())
            wrapNet.mapper.setBaseTime(in.getFirstTimestamp());
        
//        int skipped=0;
        
        // Check if things need to be initialized
        if (lastEvTime==Integer.MAX_VALUE && !in.isEmpty())
        {   lastEvTime=in.getFirstTimestamp();
            lastTriggerTime=lastEvTime-getTriggerDuration();
        }
        
        // If it's a clusterset event
        for (int k=0; k<in.getSize(); k++)
//        for (BasicEvent ev:in)
        {   
            
            
            BasicEvent ev=in.getEvent(k);
                        
            
            // Check for timestamp nonmon (necessary??)
            if (lastEvTime!=Integer.MAX_VALUE && (lastEvTime>ev.timestamp))
            {   System.out.println("Non-Monotinic Timestamps detected ("+lastEvTime+"-->"+ev.timestamp+").  Resetting");                
                lastEvTime=Integer.MAX_VALUE;        
                wrapNet.reset();                
                return in;
            }
            
            
            
            
            if (ev.timestamp>lastTriggerTime+getTriggerDuration()) // See if triggered
            {
                
                if (triggerOn)  // If the trigger just expired, reset
                {
                    nc.reset();
                    triggerOn=false;
                }
                
                triggerState=(float)(triggerState*Math.exp((lastEvTime-ev.timestamp)/getTriggerDecayTC()))+1;
                
                if (triggerState>getTriggerThreshold())
                {
                    lastTriggerTime=ev.timestamp; // GO!!!
                    isiMap.startTime=ev.timestamp;
                    triggerOn=true;
                }
                
            }
            else
            {
                
                wrapNet.addToQueue(ev);
                
            }
            lastEvTime=ev.timestamp;
        }
        
//        if (skipped>0)
            
        
        wrapNet.eatEvents();
                
        return in;
    }
    
    
    
    @Override
    public NetMapper makeMapper(Network net) {
        
        
        return isiMap;
    }

    @Override
    public void customizeNet(Network net) 
    {
        Network.Initializer ini=new Network.Initializer();
        
        ini.lay(0).nUnits=nTimeBins*isiMap.nBins;
        
        ini.ax(0,0).wMean=-1;
        ini.ax(0,0).wStd=0;
                
        net.buildFromInitializer(ini);
                
        net.lay(0).dimx=(short)nTimeBins;
        net.lay(0).dimy=(short)this.isiMap.nBins;
        
//        nc.setForwardStrengths(new boolean[] {true});
        
        unitGlobs.useGlobalThresh=true;
        unitGlobs.thresh=5;
        unitGlobs.tau=200000;
        
        net.lay(0).fireInputsTo=true;
        
//        net.inputCurrents=true;
    }
    
    
    

    @Override
    public String[] getInputNames() {
        return new String[] {"Cochlea"};
    }
    
    //  Getters/setters
    
    public float getMinFreqHz() {
        return isiMap.getMinFreqHz();
    }

    public void setMinFreqHz(float minFreqHz) {
        isiMap.setMinFreqHz(minFreqHz);
    }

    public float getMaxFreqHz() {
        return isiMap.getMaxFreqHz();
    }

    public void setMaxFreqHz(float maxFreqHz) {
        isiMap.setMaxFreqHz(maxFreqHz);
    }
    
    boolean record=false;
    public boolean getRecord()
    {
        return record;
    }
    
    
//    public float getMinFreqHz() {
//        return isiMap.getMinFreqHz();
//    }
//
//    public void setMinFreqHz(float minFreqHz) {
//        isiMap.setMinFreqHz(minFreqHz);
//    }
//    

    public float getTriggerThreshold() {
        return triggerThreshold;
    }

    public void setTriggerThreshold(float triggerThreshold) {
        this.triggerThreshold = triggerThreshold;
    }

    public float getTriggerDecayTC() {
        return triggerDecayTC;
    }

    public void setTriggerDecayTC(float triggerDecayTC) {
        this.triggerDecayTC = triggerDecayTC;
    }
    
}
