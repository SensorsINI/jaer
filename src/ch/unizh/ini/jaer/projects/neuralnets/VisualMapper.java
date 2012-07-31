/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import net.sf.jaer.event.BasicEvent;

/**
 *
 * @author Peter
 */
public class VisualMapper extends NetMapper{

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
    
}
