/* Created on March 4, 2006, 7:24 PM
 *
 *Copyright March 4, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */

package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/** Subsamples input AE packets to produce output at half spatial resolution. 
 * The output has half the spatial resolution but is sampled with overlapping 
 * fields so that it has the same number of addresses.
 *
 * @author tobi */
@Description("Subsamples input AE packets to produce output at half spatial resolution")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OverlappingSubSampler extends EventFilter2D {

    private int bits = getInt("OverlappingSubSampler.bits",1);
    short shiftx=0, shifty=0;

    /** Creates a new instance of SubSampler
     * @param chip the AE chip*/
    public OverlappingSubSampler(AEChip chip) {
        super(chip);
//        setBits(getPrefs().getInt("OverlappingSubSampler.bits",1));
//        computeShifts();
    }

    public Object getFilterState() {
        return null;
    }

    @Override public void resetFilter() { }

    @Override public void initFilter() { }

    @Override public synchronized void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
//        computeShifts();
    }
    
    public int getBits() {
        return bits;
    }
    
    synchronized public void setBits(int bits) {
        if(bits<0) {
            bits=0;
        } else if(bits>8) {
            bits=8;
        }
        this.bits = bits;
        putInt("OverlappingSubSampler.bits",bits);
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

    @Override synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;

        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);

        checkOutputPacketEventType(in);
        OutputEventIterator oi=out.outputIterator();
        for(Object obj:in){
            TypedEvent e=(TypedEvent)obj;
            TypedEvent o=(TypedEvent)oi.nextOutput();
            o.copyFrom(e);
            o.setX((short) ((e.x >>> bits)<<(bits + shiftx)));
            o.setY((short) ((e.y >>> bits)<<(bits + shifty)));
            TypedEvent o2=(TypedEvent)oi.nextOutput();
            o2.copyFrom(e);
            o2.setX((short) (((e.x+1) >>> bits)<<(bits + shiftx)));
            o2.setY((short) (((e.y+1) >>> bits)<<(bits + shifty)));
        }
        return out;
    }

}
