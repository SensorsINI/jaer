/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import net.sf.jaer.event.BasicEvent;

/**
 * This object maps AER events onto a neural network.  The methods within are
 * intended to be overwritten by a user-defined class which defines the 
 * appropriate addresses to route events to.
 * 
 * @author Peter
 */
public abstract class NetMapper<EventType extends BasicEvent> {
    
    int baseTime;
    boolean baseTimeSet=false;
    
    public boolean isBaseTimeSet()
    {   return baseTimeSet;
        
    }

    public void setBaseTime(int zeroTime)
    {
        baseTime=zeroTime;
        baseTimeSet=true;
    }
    
    public int translateTime(int evtTime)
    {
        return evtTime-baseTime;
    }
    
    public double translateTimeDouble(int evtTime,float scaleFactor)
    {   return (double) translateTime(evtTime)*scaleFactor;          
    }
    
    /** Map the source byte onto the layer index */
    public int ev2layer(EventType ev)
    {   return ev.source;   
    }
    
//    /** Map information about the input event onto a destination unit */
//    public abstract int loc2addr(short xloc,short yloc,byte source);
//    
//    /** Overrideable method - in case you want to include timestamp in the translation */
//    public int loc2addr(short xloc,short yloc,byte source,int timestamp)
//    {   return loc2addr(xloc,yloc,source);        
//    }
    
    abstract public int ev2addr(EventType ev);
            
    
    
}
