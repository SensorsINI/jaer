/*
 * Event3D.java
 *
 * Created on December 7, 2007, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.stereo3D;

import ch.unizh.ini.caviar.aemonitor.*;

/**
 * A raw address-event, having a timestamp and raw address
 * @author tobi
 */
public class Event3D extends Event{
    public int x;
    public int y;
    public int d;
    public int method;
    public int lead_side;
    public float value;

    /** Creates a new instance of EventRaw */
    public Event3D() {
        super();
    }
     
    /** Creates a new instance of Event3D
     @param x
     @param y 
     @param d : disparity link
     @param method : left or right most method
     @param lead_side : left or right main camera
     @param value : intensity of the event 
     @param t the timestamp
     */
    public Event3D(int x, int y, int d, int method, int lead_side, float value, int t) {
        super(t);
        this.x = x;
        this.y = y;
        this.d = d;
        this.method = method;
        this.lead_side = lead_side;
        this.value = value;
    }
    
    public String toString(){
        return "Event3D at x:"+x+" y:"+y+" d:"+d+" lead_side:"+lead_side+" at time:"+timestamp;
    }
    
}
