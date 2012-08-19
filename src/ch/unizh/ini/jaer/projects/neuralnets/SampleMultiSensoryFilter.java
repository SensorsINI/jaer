/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;

/**
 * This filter shows how to use the event-source bit to identify the input 
 * device in a multi-sensory filter.  It simply prints the number of events 
 * received from each source to the output window.
 * @author Peter
 */
public class SampleMultiSensoryFilter extends MultiSourceProcessor{

    public SampleMultiSensoryFilter(AEChip chip)
    {   super(chip);
    }
    
    @Override
    public String[] getInputNames() {
        return new String[]{"Retina","Cochlea"};
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        int retEvents=0,cochEvents=0;
        
        for (Object o:in)
        {   BasicEvent ev=(BasicEvent)o;
            if (ev.source==0)
               retEvents++;
            else if (ev.source==1)
               cochEvents++;   
        }
        System.err.println("Retina: "+retEvents+"\t Cochlea: "+cochEvents);        
        return in;
    }

    @Override
    public void resetFilter() {}

    @Override
    public void initFilter() {}
    
}
