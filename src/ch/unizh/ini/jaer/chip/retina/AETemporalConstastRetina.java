/*
 * AETemporalConstastRetina.java
 *
 * Created on January 26, 2006, 11:12 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.retina;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.filter.CellStatsProber;
import net.sf.jaer.graphics.RetinaRenderer;

/**
 * A superclass for temporal contrast event-based silicon retina chips, with renderers and event filters.
 *
 * @author tobi
 */
abstract public class AETemporalConstastRetina extends AEChip{
    
    /** Creates a new instance of AETemporalConstastRetina */
    public AETemporalConstastRetina() {
        setEventClass(PolarityEvent.class);
        // these are subclasses of ChipRenderer and ChipCanvas
        // these need to be added *before* the filters are made or the filters will not annotate the results!!!
        setRenderer(new RetinaRenderer(this));
        addDefaultEventFilter(CellStatsProber.class);
   }
    
}
