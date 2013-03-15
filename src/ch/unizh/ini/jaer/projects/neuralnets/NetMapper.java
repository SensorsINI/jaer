/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import jspikestack.PSPInput;
import net.sf.jaer.event.BasicEvent;

/**
 * This object maps AER events onto a neural network.  The methods within are
 * intended to be overwritten by a user-defined class which defines the 
 * appropriate addresses to route events to.
 * 
 * @author Peter
 */
public abstract class NetMapper {
// comment, Dennis G., Nov. 05 2012: removed the use of generics. NetMapper is not a container and the use of the generic type seems awkward 
// (and causes a problem in AudioVisualNet). 
//public abstract class NetMapper<EventType extends BasicEvent> {
    
    int baseTime;
    boolean baseTimeSet=false;
    
    public boolean isBaseTimeSet()
    {   
        return baseTimeSet;        
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
    public int ev2layer(BasicEvent ev)
//    public int ev2layer(EventType ev)
    {   return ev.source;   
    }
    
//    /** Map information about the input event onto a destination unit */
//    public abstract int loc2addr(short xloc,short yloc,byte source);
//    
//    /** Overrideable method - in case you want to include timestamp in the translation */
//    public int loc2addr(short xloc,short yloc,byte source,int timestamp)
//    {   return loc2addr(xloc,yloc,source);        
//    }
    
    public int ev2special(BasicEvent ev)
//  public int ev2special(EventType ev)
    {
        return 1;        
    }
    
    public PSPInput mapEvent(BasicEvent ev)
//    public PSPInput mapEvent(EventType ev)
    {   
        int addr=ev2addr(ev);
        
        if (addr==-1)
            return null;
        else 
        {   int layer=ev2layer(ev);
            if (layer!=-1)
                return new PSPInput(translateTime(ev.timestamp),addr,ev2layer(ev),ev2special(ev));
            else
                return null;
        }
    }
    
    
    abstract public int ev2addr(BasicEvent ev);
//    abstract public int ev2addr(EventType ev);
            
    
    
}
