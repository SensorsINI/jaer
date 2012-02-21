/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

/**
 * Takes x,y coordinates of input events and transforms them into appropriate 
 * input indeces for neural networks.
 * @author Peter
 */
public class Remapper {
    // Use this guy to map an input 
    
    short inDimX;
    short inDimY;
    short outDimX;
    short outDimY;
    
    
    int xy2ind(short x, short y)
    {   // Take a set of raw input locs, resize
        
        short newX=(short)(x*outDimX/inDimX);
        short newY=(short)(outDimY-1-(y*outDimY/inDimY));
        
        return outDimY*(newX)+newY;
                
    }
    
    int ixy2ind(short x,short y)
    {   // Takes x,y locs on the output coordinate system, transfers them to indeces
        
        return outDimY*(x)+y;
    }
    
    int xyCent2ind(float x, float y)
    {   // Take a set of relative input locs, on scale [-1 1] [-1 1], map them 
        // onto the output
        if (x>=1) x=1-1/inDimX;
        else if (x<-1) x=-1;
        
        if (y>=1) y=1-1/inDimY;
        else if (y<-1) y=-1;
        
        
        short newX=(short)(((x+1)/2)*inDimX);
        short newY=(short)(((y+1)/2)*inDimY);
        
        return outDimY*(newX)+newY;
    }
    
    int xyNorm2ind(float x, float y)
    {   // Maps from interval [0,1],[0 1] to output index
        if (x>=1) x=1-1/inDimX;
        else if (x<0) x=0;
        
        if (y>=1) y=1-1/inDimY;
        else if (y<0) y=0;
                
        short newX=(short)(x*inDimX);
        short newY=(short)(y*inDimY);
        
        return outDimY*(newX)+newY;
    }
    
    
}
