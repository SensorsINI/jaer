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

package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Subsmaples input AE packets to produce output at some binary subsampling.
 *
 * @author tobi
 */
@Description("Subsamples X and Y addresses, by right shifting the X and Y addresses. Does not decrease event rate.")
@Help("""
<html>
<body>
<h2>SubSampler</h2>
<p>Coarsens the pixel grid by right-shifting <code>x</code> and <code>y</code>. Event <b>rate is unchanged</b>;
several neighboring pixels collapse onto the same output address (useful before trackers or for a
low-resolution view). Special events are left unchanged.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Check <b>Enabled</b>.</li>
<li>Set <code>bits</code> (0&ndash;8). Each bit halves linear resolution
(1 &rarr; 2&times;2 bins, 2 &rarr; 4&times;4, &hellip;).</li>
<li><code>shiftToCenterEnabled</code> recenters the subsampled addresses in the chip;
off leaves them in the lower-left corner.</li>
</ol>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class SubSampler extends EventFilter2D {

    private int bits;
    private boolean shiftToCenterEnabled=getBoolean("shiftToCenterEnabled", false);
    short shiftx, shifty;

    /** Creates a new instance of SubSampler */
    public SubSampler(AEChip chip) {
        super(chip);
        setBits(getInt("bits",1));
        computeShifts();
        setPropertyTooltip("bits","Subsample by this many bits, by masking these off X and Y addreses");
        setPropertyTooltip("shiftToCenterEnabled","Shifts output addresses to be centered. Disable to leave at lower left corner of scene");
    }

    public Object getFilterState() {
        return null;
    }

    @Override
	public void resetFilter() {
    }

    @Override
	public void initFilter() {
    }

    public int getBits() {
        return bits;
    }

    @Override public synchronized void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        computeShifts();
    }

    /** Sets the subsampler subsampling shift.
     @param bits the number of bits to subsample by, e.g. bits=1 divides by two
     */
    synchronized public void setBits(int bits) {
        if(bits<0) {
			bits=0;
		}
		else if(bits>8) {
			bits=8;
		}
        this.bits = bits;
        putInt("bits",bits);
        computeShifts();
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

    @Override
	synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) {
			return null;
		}
        if(!filterEnabled) {
			return in;
		}
        if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}
        checkOutputPacketEventType(in);
        OutputEventIterator oi=out.outputIterator();
        int sx=shiftToCenterEnabled?shiftx:0;
        int sy=shiftToCenterEnabled?shifty:0;
        for(Object obj:in){
            TypedEvent e=(TypedEvent)obj;
            TypedEvent o=(TypedEvent)oi.nextOutput();
            o.copyFrom(e);
            o.setX((short) ((e.x >>> bits) + sx));
            o.setY((short) ((e.y >>> bits) + sy));
        }
        return out;
    }

    /**
     * @return the shiftToCenterEnabled
     */
    public boolean isShiftToCenterEnabled() {
        return shiftToCenterEnabled;
    }

    /**
     * @param shiftToCenterEnabled the shiftToCenterEnabled to set
     */
    public void setShiftToCenterEnabled(boolean shiftToCenterEnabled) {
        this.shiftToCenterEnabled = shiftToCenterEnabled;
        putBoolean("shiftToCenterEnabled", shiftToCenterEnabled);
    }

}
