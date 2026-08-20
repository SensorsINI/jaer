/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;

/**
 * An event-processing method that does nothing, just to measure base performance of iteration and event copying
 * @author tobi
 */
@Description("A do-nothing filter used to measure cost of packet iteration and event-copying")
@Help("""
<html>
<body>
<h2>NoOpFilter</h2>
<p>Does <b>not</b> change events. Use it as a baseline to measure the cost of iterating a packet
and optionally copying each event, without any algorithm work.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Check <b>Enabled</b>.</li>
<li>Turn on <code>iterateOverPacket</code> to walk every event (default on).</li>
<li>Turn on <code>copyInputPacket</code> to copy each event into an output packet
(returns that copy). Both on is the usual copy-out path used by many filters.</li>
</ol>
<p>Compare AEViewer rendering rate / CPU with this filter vs a real algorithm on the same stream.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class NoOpFilter extends EventFilter2D {
    
    public boolean copyInputPacket=false;
    public boolean iterateOverPacket=true;

    public NoOpFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("copyInputPacket", "copies each event in input packet");
        setPropertyTooltip("iterateOverPacket", "runs iterator over packet");
    }

    
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        OutputEventIterator outItr=null;
        if(copyInputPacket){
            checkOutputPacketEventType(in);
            outItr=getOutputPacket().getOutputIterator();
        }
        if(iterateOverPacket){
            for(BasicEvent e:in){
                if(copyInputPacket){
                    BasicEvent oe=outItr.nextOutput();
                    oe.copyFrom(e);
                }
            }
        }
        if(iterateOverPacket && copyInputPacket){
            return getOutputPacket();
        }else{
            return in;
        }
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the copyInputPacket
     */
    public boolean isCopyInputPacket() {
        return copyInputPacket;
    }

    /**
     * @param copyInputPacket the copyInputPacket to set
     */
    public void setCopyInputPacket(boolean copyInputPacket) {
        this.copyInputPacket = copyInputPacket;
    }

    /**
     * @return the iterateOverPacket
     */
    public boolean isIterateOverPacket() {
        return iterateOverPacket;
    }

    /**
     * @param iterateOverPacket the iterateOverPacket to set
     */
    public void setIterateOverPacket(boolean iterateOverPacket) {
        this.iterateOverPacket = iterateOverPacket;
    }
    
    
}
