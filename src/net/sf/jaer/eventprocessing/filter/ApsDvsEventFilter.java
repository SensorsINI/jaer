/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.util.Iterator;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Enables filtering out of either DVS or APS events from ApsDvsEventPacket
 * @author tobi
 */
@Description("Enables filtering out of either DVS or APS events from ApsDvsEventPacket")
public class ApsDvsEventFilter extends EventFilter2D {

    private boolean filterDVSEvents=getBoolean("filterDVSEvents",false);
    private boolean filterAPSEvents=getBoolean("filterAPSEvents",false);
    
    public ApsDvsEventFilter(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!(in instanceof ApsDvsEventPacket) || (!filterAPSEvents && !filterDVSEvents)) return in;
        ApsDvsEventPacket apsDvsEventPacket=(ApsDvsEventPacket)in;
        Iterator fullIterator=apsDvsEventPacket.fullIterator();
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        while(fullIterator.hasNext()){
            ApsDvsEvent event=(ApsDvsEvent)fullIterator.next();
            if((filterAPSEvents && event.isSampleEvent()) || (filterDVSEvents && !event.isSampleEvent())) continue;
            outItr.nextOutput().copyFrom(event);
        }
        return out;
    }

    @Override
    public void resetFilter() {
        
    }

    @Override
    public void initFilter() {
        
    }

    /**
     * @return the filterDVSEvents
     */
    public boolean isFilterDVSEvents() {
        return filterDVSEvents;
    }

    /**
     * @param filterDVSEvents the filterDVSEvents to set
     */
    public void setFilterDVSEvents(boolean filterDVSEvents) {
        this.filterDVSEvents = filterDVSEvents;
        putBoolean("filterDVSEvents",filterDVSEvents);
    }

    /**
     * @return the filterAPSEvents
     */
    public boolean isFilterAPSEvents() {
        return filterAPSEvents;
    }

    /**
     * @param filterAPSEvents the filterAPSEvents to set
     */
    public void setFilterAPSEvents(boolean filterAPSEvents) {
        this.filterAPSEvents = filterAPSEvents;
        putBoolean("filterAPSEvents",filterAPSEvents);
    }
    
}
