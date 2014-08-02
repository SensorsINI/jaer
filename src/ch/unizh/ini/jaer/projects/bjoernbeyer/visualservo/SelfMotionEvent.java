
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import net.sf.jaer.util.Vector2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;

/**
 *
 * @author Bjoern
 */
public class SelfMotionEvent extends DvsMotionOrientationEvent {
    
    public boolean hasSelfMotion = false;
    public boolean hasAlienMotion = false;
    public Vector2D selfMotion = new Vector2D(0,0);
    public Vector2D alienMotion = new Vector2D(0,0);
    
    /** copies fields from source event src to this event including all super and this fields if possible.
     * @param src the event to copy from */
    @Override public void copyFrom(BasicEvent src){
        DvsMotionOrientationEvent e=(DvsMotionOrientationEvent)src;
        super.copyFrom(src);
        if(e instanceof SelfMotionEvent){
            this.hasSelfMotion = ((SelfMotionEvent)e).hasSelfMotion;
            this.selfMotion    = ((SelfMotionEvent)e).selfMotion;
            this.alienMotion   = ((SelfMotionEvent)e).alienMotion;
        }
    }
    
    // TODO: THIS IS A HUGE HACK
    @Override public int getNumCellTypes() {
        return 2;
    }
    @Override public int getType(){
//        return (hasSelfMotion ? 1 : 0) + (hasAlienMotion ? 2 : 0);
        
        return polarity==Polarity.Off? 0: 1;
        //return hasGlobalMotion;
    }
    
}
