/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.event;

import java.awt.geom.Point2D;

/**
 * Represents a optFlowVelPPS vector event in 2d that extends MotionOrientationEvent.
 * @author tobi
 */
public class OpticalFlowEvent extends MotionOrientationEvent{
    
    /** The smoothed optical flow velocity in pixels per second */
    public Point2D.Float optFlowVelPPS=new Point2D.Float();
    
    /** copies fields from source event src to this event including all super and this fields if possible.
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        MotionOrientationEvent e=(MotionOrientationEvent)src;
        super.copyFrom(src);
        if(e instanceof OpticalFlowEvent){
            this.optFlowVelPPS.setLocation(((OpticalFlowEvent)e).optFlowVelPPS);
        }
    }

    
}
