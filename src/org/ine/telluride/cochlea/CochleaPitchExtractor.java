/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.cochlea;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.event.TypedEvent;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
/**
 * Extracts pitch from AE cochlea spike output.
 * 
 * @author tyu (teddy yu, ucsd)
 */
public class CochleaPitchExtractor extends EventFilter2D{
    private int channelStart=getPrefs().getInt("CochleaPitchExtractor.channelStart", 0);
    {setPropertyTooltip("channelStart","starting cochlea tap for pitch");}
    private int channelEnd=getPrefs().getInt("CochleaPitchExtractor.channelEnd", 31);
    {setPropertyTooltip("channelEnd","end cochlea channel for pitch");}
    
    @Override
    public String getDescription() {
        return "Extracts pitch from AE cochlea spike output.";
    }
    
    public CochleaPitchExtractor(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(in==null) return in;
        for(Object o:in){
            TypedEvent e=(TypedEvent)o;
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

    public int getChannelStart() {
        return channelStart;
    }

    public void setChannelStart(int channelStart) {
        this.channelStart=channelStart;
        getPrefs().putInt("CochleaPitchExtractor.channelStart", channelStart);
    }

    public int getChannelEnd() {
        return channelEnd;
    }

    public void setChannelEnd(int channelEnd) {
        this.channelEnd=channelEnd;
        getPrefs().putInt("CochleaPitchExtractor.channelEnd", channelEnd);
    }

}
