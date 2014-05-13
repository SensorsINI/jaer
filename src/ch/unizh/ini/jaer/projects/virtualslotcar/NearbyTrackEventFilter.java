/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *  Filters out events not near the track model.
 *
 * @author tobi
 */
public class NearbyTrackEventFilter extends EventFilter2D implements PropertyChangeListener{
    private SlotcarTrack track=null;

    public NearbyTrackEventFilter(AEChip chip) {
        super(chip);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (track == null) {
            return in;
        }
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        Point2D.Float p=new Point2D.Float();
        for(BasicEvent e:in){
            if(e.isSpecial()) continue;
            p.x=e.x; p.y=e.y;
            if(track.findClosestIndex(p, 0, true)!=-1){
                BasicEvent eout=outItr.nextOutput();
                eout.copyFrom(e);
            }
        }
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName()==SlotcarTrack.EVENT_TRACK_CHANGED){
            setTrack((SlotcarTrack) evt.getNewValue());
        }
    }

    /**
     * @return the track
     */
    public SlotcarTrack getTrack() {
        return track;
    }

    /**
     * @param track the track to set
     */
    public void setTrack(SlotcarTrack track) {
        this.track = track;
    }




}
