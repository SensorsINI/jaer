/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Peter
 */
public class PreProcess extends EventFilter2D {

    int maxEventsPerPacket=getInt("maxEventsPerPacket",Integer.MAX_VALUE);
    int downsamp=getInt("downsamp",1);
    public enum polarityOpts {all,on,off};         // <1: just off events, >1: Just on events, 0: both;
    polarityOpts polarityPass=polarityOpts.valueOf(getPrefs().get("polarityPass",polarityOpts.off.toString()));
       
    boolean transformPoints=getBoolean("transformPoints",false);
    
    public PreProcess(AEChip chip)
    {   super(chip);
        setPropertyTooltip("maxEventsPerPacket", "Maximum number of events in packet");
        setPropertyTooltip("downSamp", "Downsampling");
        setPropertyTooltip("polarityPass", "all: treat all events as spikes.  off/on: keep only off/on events.");
        
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) 
    {
        if (!filterEnabled) return in;
        
        OutputEventIterator outItr = out.outputIterator();
         out.setEventClass(PolarityEvent.class);
             
         int counter=0;
         
         for (BasicEvent e:in)
         {   
             if (counter>=maxEventsPerPacket)
                 break;
             
             if (e instanceof PolarityEvent)
             {
                
                if (((PolarityEvent) e).polarity==PolarityEvent.Polarity.On)
                    if (polarityPass==polarityOpts.off)
                        continue;
                else
                    if (polarityPass==polarityOpts.on)
                        continue;
             }
             
             BasicEvent oe=outItr.nextOutput();
             oe.copyFrom(e);
             if (downsamp!=1)
             {   oe.x=(short)(oe.x/downsamp);
                 oe.y=(short)(oe.y/downsamp);
             }
             
             counter++;
         }
                
        /*
        if (out.getSize()<maxEventsPerPacket)
            out.setSize(maxEventsPerPacket);
        */
        return out;
        
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
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
        this.polarityPass = dt;
        getPrefs().put("PreProcess.polarityPass",dt.toString());
        support.firePropertyChange("polarityPass",this.polarityPass,dt);
    }   
    
    public int getMaxEventsPerPacket() {
        return this.maxEventsPerPacket;
    }
    
    public void setMaxEventsPerPacket(int dt) {
        
        getPrefs().putInt("PreProcess.maxEventsPerPacket",dt);
        support.firePropertyChange("maxEventsPerPacket",this.maxEventsPerPacket,dt);
        this.maxEventsPerPacket = dt;
    }   
    
    
    
}
