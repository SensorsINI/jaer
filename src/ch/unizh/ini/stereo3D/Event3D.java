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
    public int z;
    public float value;

    /** Creates a new instance of EventRaw */
    public Event3D() {
        super();
    }
     
    /** Creates a new instance of Event3D
     @param x
     @param y 
     @param z
     @param value : intensity of the event 
     @param t the timestamp
     */
    public Event3D(int x, int y, int z, float value, int t) {
        super(t);
        this.x = x;
        this.y = y;
        this.z = z;
        this.value = value;
    }
    
    public String toString(){
        return "Event3D at x:"+x+" y:"+y+" z:"+z+" at time:"+timestamp;
    }
    
}
