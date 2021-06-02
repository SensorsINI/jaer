/*
 * VergenceFilter2.java
 *
 * Created on 30. August 2006, 10:45
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;


/**
 * Applies event disparity to incoming events if they are DisparityEvents.
 Each event is shifted left or right (depending on the DisparityEvent.eye field) by the event's disparity.
 If all events share the same disparity then the they are all shifted by the same amounts, left eye events
 to larger x, and right eye events to smaller x.
 If StereoVergenceFilter is preceded by GlobalXDisparityFilter2, for example, and there is only a single
 global disparity, then the images should come into registration.
 * @author Peter Hess
 */
public class StereoVergenceFilter extends EventFilter2D {
    public StereoVergenceFilter(AEChip chip){
        super(chip);
    }
    
    synchronized public void resetFilter() {
    }
    
    public void initFilter() {
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public boolean isGeneratingFilter() { return false; }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (in == null) return null;
        if (!filterEnabled) return in;
        if (enclosedFilter != null) in = enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularDisparityEvent)) return in;
        if (in.isEmpty()) return in;
        checkOutputPacketEventType(in);
        
        int sizeX = chip.getSizeX();
        OutputEventIterator o = out.outputIterator();
        for(Object i:in){
            BinocularDisparityEvent e = (BinocularDisparityEvent)i;
            int dx = e.disparity;
            short x;
            if (e.eye == BinocularEvent.Eye.LEFT) {
                x = (short)(e.getX() + dx/2f + 1f);
            } else {
                x = (short)(e.getX() - dx/2f - 1f);
            }
            if(x < 0 || x > sizeX-1) continue;
            BinocularDisparityEvent oe = (BinocularDisparityEvent)o.nextOutput();
            oe.copyFrom(e);
            oe.setX(x);
        }
        return out;
    }

    @Override synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
//        if (yes) {
//            out = new EventPacket(BinocularDisparityEvent.class);
//        } else {
//            out = null;
//        }
    }
}