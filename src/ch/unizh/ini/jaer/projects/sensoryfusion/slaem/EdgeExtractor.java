/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

    import net.sf.jaer.chip.*;
    import net.sf.jaer.event.*;
    import net.sf.jaer.event.EventPacket;
    import net.sf.jaer.eventprocessing.EventFilter2D;
    import java.util.*;
    import net.sf.jaer.Description;
    import net.sf.jaer.DevelopmentStatus;

/**
 * This filter extracts edges by inferring points and connecting lines into a scene
 *
 * @author christian
 */
@Description("Extracts edges as linear point interpolations")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EdgeExtractor extends EventFilter2D implements Observer {
    
    public EdgeExtractor(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }
    
    /**
     * Determines whether events that cannot be assigned to an edge should be filtered out
     */
    private boolean isFilterEnabled=getPrefs().getBoolean("EdgeExtractor.isFilterOn",false);
    {setPropertyTooltip("isFilterOn","Should the extractor act as filter for unallocated events");}
    
    @Override
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    public void resetFilter() {
        
    }
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        //Processing done here
        if(isFilterEnabled){ 
            return out;
        }else{
            return in;
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        
    }
    
}
