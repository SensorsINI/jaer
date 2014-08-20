
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;


/** Represents a motion event with inhibited background activity. 
 * Extends PolarityEvent
 * @author Bjoern */
public class BackgroundInhibitedEvent extends PolarityEvent{
    
    public byte hasGlobalMotion = 0;

    /** copies fields from source event src to this event including all super and this fields if possible.
     * @param src the event to copy from */
    @Override public void copyFrom(BasicEvent src){
        PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(src);
        if(e instanceof BackgroundInhibitedEvent){
            this.hasGlobalMotion =  ((BackgroundInhibitedEvent)e).hasGlobalMotion;
        }
    }
    
    @Override public int getNumCellTypes() {
        return 2;
    }
    @Override public int getType(){
        return hasGlobalMotion;
    }
}