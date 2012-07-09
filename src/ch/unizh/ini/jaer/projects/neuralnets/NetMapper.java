/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

/**
 * This object maps AER events onto a neural network.  The methods within are
 * intended to be overwritten by a user-defined class which defines the 
 * appropriate addresses to route events to.
 * 
 * @author Peter
 */
public abstract class NetMapper {
    
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
    public int source2layer(byte source)
    {   return source;   
    }
    
    /** Map information about the input event onto a destination unit */
    public abstract int loc2addr(short xloc,short yloc,byte source);
    
}
