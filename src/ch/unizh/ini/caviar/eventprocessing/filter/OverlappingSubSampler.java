/*
 * SubSampler.java
 *
 * Created on March 4, 2006, 7:24 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;

/**
 * Subsmaples input AE packets to produce output at half spatial resolution. The output has half the spatial resolution but is sampled with
 overlapping fields so that it has the same number of addresses.
 *
 * @author tobi
 */
public class OverlappingSubSampler extends EventFilter2D {
    
    private int bits=1;
    short shiftx=0, shifty=0;
        
    /** Creates a new instance of SubSampler */
    public OverlappingSubSampler(AEChip chip) {
        super(chip);
        setBits(getPrefs().getInt("OverlappingSubSampler.bits",1));
//        computeShifts();
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
    public void initFilter() {
    }
    
    public int getBits() {
        return bits;
    }
    
    @Override public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
//        computeShifts();
    }
    
    synchronized public void setBits(int bits) {
        if(bits<0) bits=0; else if(bits>8) bits=8;
        this.bits = bits;
        getPrefs().putInt("OverlappingSubSampler.bits",bits);
//        computeShifts();
    }
    
    private void computeShifts() {
        if(bits==0){
            shiftx=0; shifty=0; return;
        }
        int s1=chip.getSizeX();
        int s2=s1>>>bits;
        shiftx=(short)((s1-s2)/2);
        s1=chip.getSizeY();
        s2=s1>>>bits;
        shifty=(short)((s1-s2)/2);
    }
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        OutputEventIterator oi=out.outputIterator();
        for(Object obj:in){
            TypedEvent e=(TypedEvent)obj;
            TypedEvent o=(TypedEvent)oi.nextOutput();
            o.copyFrom(e);
            o.setX((short) ((e.x >>> bits)<<bits + shiftx));
            o.setY((short) ((e.y >>> bits)<<bits + shifty));
            TypedEvent o2=(TypedEvent)oi.nextOutput();
            o2.copyFrom(e);
            o2.setX((short) (((e.x+1) >>> bits)<<bits + shiftx));
            o2.setY((short) (((e.y+1) >>> bits)<<bits + shifty));
        }
        return out;
    }
    
}
