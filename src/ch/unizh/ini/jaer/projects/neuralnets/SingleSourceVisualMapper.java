/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

/**
 *
 * @author Peter
 */
public class SingleSourceVisualMapper extends NetMapper{

    short inDimX;
    short inDimY;
    short outDimX;
    short outDimY;
    
    @Override
    public int loc2addr(short xloc, short yloc, byte source) {
        
        short newX=(short)(xloc*outDimX/inDimX);
        short newY=(short)(outDimY-1-(yloc*outDimY/inDimY));
        
        return outDimY*(newX)+newY;
    }
    
    
    
}
