/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * @author Peter
 */
public class VisualMapper extends NetMapper {
//	public class VisualMapper<T extends PolarityEvent> extends NetMapper<T>{
// comment, Dennis G., Nov. 05 2012: removed the use of generics. See NetMapper.java 

    short inDimX;
    short inDimY;
    short outDimX;
    short outDimY;
    
//    @Override
//    public int loc2addr(short xloc, short yloc, byte source) {        
//        return loc2addr(xloc,yloc);
//    }
//    
//    public int loc2addr(short xloc, short yloc) {
//        
//        short newX=(short)(xloc*outDimX/inDimX);
//        short newY=(short)(outDimY-1-(yloc*outDimY/inDimY));
//        
//        return outDimY*(newX)+newY;
//    }

    @Override
    public int ev2addr(BasicEvent ev) {
        short newX=(short)(ev.x*outDimX/inDimX);
        short newY=(short)(outDimY-1-(ev.y*outDimY/inDimY));
        
        return outDimY*(newX)+newY;
    }
    
    
 // comment, Dennis G., Nov. 05 2012: To avoid type-unsafe constructs as before, let's simply cast the incoming BasicEvent to a PolarityEvent. 
    @Override
    public int ev2special(BasicEvent ev)
    {
    	if (ev instanceof PolarityEvent) 
    		return ((PolarityEvent)ev).polarity==PolarityEvent.Polarity.On?1:-1;
    	else return super.ev2special(ev);
    }
    
    
    
}
