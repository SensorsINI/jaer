
package net.sf.jaer.event;

import ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo.Vector2D;

/** Represents a motion event with inhibited background motion and excited 
 * object motion. Extends OpticalFlowEvent
 * @author Bjoern */
public class BackgroundMotionInhibitedEvent extends OpticalFlowEvent{
    
    public Vector2D avgInhibitionVel = new Vector2D();
    
    public Vector2D avgExcitationVel = new Vector2D();
    
    public double exciteInhibitionRatio = -1;
    
    public byte hasGlobalMotion = 0;
    
    
    /** copies fields from source event src to this event including all super and this fields if possible.
     * @param src the event to copy from */
    @Override
    public void copyFrom(BasicEvent src){
        OpticalFlowEvent e=(OpticalFlowEvent)src;
        super.copyFrom(src);
        if(e instanceof BackgroundMotionInhibitedEvent){
            this.avgExcitationVel.setLocation(((BackgroundMotionInhibitedEvent)e).avgExcitationVel);
            this.avgInhibitionVel.setLocation(((BackgroundMotionInhibitedEvent)e).avgInhibitionVel);
            this.exciteInhibitionRatio = ((BackgroundMotionInhibitedEvent)e).exciteInhibitionRatio;
            this.hasGlobalMotion =  ((BackgroundMotionInhibitedEvent)e).hasGlobalMotion;
        }
    }
    
    // TODO: THIS IS A HUGE HACK
    @Override
    public int getNumCellTypes() {
        return 2;
    }
    @Override 
    public int getType(){
        return polarity==Polarity.Off? 0: 1;
        //return hasGlobalMotion;
    }
}
