
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;


/** Represents a motion event with inhibited background activity. 
 * Extends PolarityEvent
 * @author Bjoern */
public class BackgroundActivityInhibitedEvent extends PolarityEvent{
    
    public float avgInhibitionActivity = 0f, avgExcitationActivity = 0f;
    
    public double exciteInhibitionRatio = -1;
    
    public byte hasGlobalMotion = 0;
    
    
    /** copies fields from source event src to this event including all super and this fields if possible.
     * @param src the event to copy from */
    @Override
    public void copyFrom(BasicEvent src){
        PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(src);
        if(e instanceof BackgroundMotionInhibitedEvent){
            this.avgExcitationActivity = ((BackgroundActivityInhibitedEvent)e).avgExcitationActivity;
            this.avgInhibitionActivity = ((BackgroundActivityInhibitedEvent)e).avgInhibitionActivity;
            this.exciteInhibitionRatio = ((BackgroundActivityInhibitedEvent)e).exciteInhibitionRatio;
            this.hasGlobalMotion =  ((BackgroundActivityInhibitedEvent)e).hasGlobalMotion;
        }
    }
    
    // TODO: THIS IS A HUGE HACK
    @Override
    public int getNumCellTypes() {
        return 2;
    }
    @Override 
    public int getType(){
//        return polarity==PolarityEvent.Polarity.Off? 0: 1;
        return hasGlobalMotion;
    }
}
