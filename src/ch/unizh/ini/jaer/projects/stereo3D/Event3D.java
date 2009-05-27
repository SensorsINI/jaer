/*
 * Event3D.java
 *
 * Created on December 7, 2007, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

import net.sf.jaer.aemonitor.*;

/**
 * A raw address-event, having a timestamp and raw address
 * @author tobi
 */
public class Event3D extends Event{
    
    // type, INDIRECT3D means it needs to be reconstructed, DIRECT3D mean we can use its x,y,z directly
    static public int INDIRECT3D = 0;
    static public int DIRECT3D = 1;
    
   
    public int type;
    public int x;
    public int y;
    public int d;
    public int method;
    public int lead_side;
    public float value;
    public float score;
    public int x0;
    public int y0;
    public int z0;

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
        type = INDIRECT3D;
        this.x = x;
        this.y = y;
        this.d = d;
        this.method = method;
        this.lead_side = lead_side;
        this.value = value;
    }
    
    public Event3D(int x, int y, int z, float value, int t) {
        super(t);
        type = DIRECT3D;
        this.x0 = x;
        this.y0 = y;
        this.z0 = z;      
        this.value = value;
        score = 0;
    }
    
    
     public Event3D( Event3D ev) {
         super(ev.timestamp);
         type = ev.type;
         this.x0 = ev.x0;
         this.y0 = ev.y0;
         this.z0 = ev.z0;
         this.value = ev.value;
         this.x = ev.x;
         this.y = ev.y;
         this.d = ev.d;
         this.method = ev.method;
         this.lead_side = ev.lead_side;
         this.score = ev.score;
     }


     public void changeTo(int x, int y, int z, float value, int t) {
        this.timestamp = t;
        type = DIRECT3D;
        this.x0 = x;
        this.y0 = y;
        this.z0 = z;
        this.value = value;
        score = 0;
    }

    public String toString(){
        return "Event3D type:"+type+" at x:"+x+" y:"+y+" d:"+d+" lead_side:"+lead_side+" at time:"+timestamp;
    }
    
}
