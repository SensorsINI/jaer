/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.caviar.eventprocessing.filter;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.BasicEvent;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.AEViewer;
import java.util.*;
/**
 * Shifts the spikes according to the last evnets from a possibly-different chip's output events (which are being rendered in another AEViewer).
 * 
 * @author tobi
 */
public class ShiftByEventsFilter extends EventFilter2D {
    private int viewerNumber=0;
    {setPropertyTooltip("viewerNumber", "the AEVieer number from which events are taken to shift this viewers events by");}
    
    private int scaleBy=4;
    
    private int lastX=0,  lastY=0,  centerX=0,  centerY=0;

    public ShiftByEventsFilter(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }
        short maxx=(short)chip.getSizeX();
        short maxy=(short)chip.getSizeY();
        EventPacket other=getOtherEventPacket();
        if(other==null) {
            return in;
        }
        if(other.getSize()>=0) {
            BasicEvent e=other.getLastEvent();
            if(e!=null) {
                lastX=e.x;
                lastY=e.y;
            }
        }
        for(BasicEvent e : in) {
            short x=(short) (e.x-scaleBy*(lastX-centerX));
            if(x<0) e.x=0; else if(x>maxx) e.x=maxx; else e.x=x;
            short y=(short) (e.y-scaleBy*(lastY-centerY));
             if(y<0) e.y=0; else if(y>maxy) e.y=maxy; else e.y=y;
      }
        return in;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public int getViewerNumber() {
        return viewerNumber;
    }

    public void setViewerNumber(int viewerNumber) {
        this.viewerNumber=viewerNumber;
    }

    private EventPacket getOtherEventPacket() {
        ArrayList<AEViewer> viewers=getChip().getAeViewer().getJaerViewer().getViewers();
        if(viewers.size()<viewerNumber) {
            return null;
        }
        AEChip otherChip=viewers.get(viewerNumber).getChip();
        System.out.println("source of shift events is "+otherChip);
        if(otherChip==null) {
            return null;
        }
        centerX=otherChip.getSizeX()/2;
        centerY=otherChip.getSizeY()/2;
        Object o=otherChip.getLastData();

        if(o instanceof EventPacket) {
            return (EventPacket) o;
        } else {
            return null;
        }
    }
}
