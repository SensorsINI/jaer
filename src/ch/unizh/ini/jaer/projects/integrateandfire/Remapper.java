/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.util.HashMap;
import java.util.Map;

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
    int baseTime;
    boolean baseTimeInitialized=false;
    Map<Byte,Integer> sourceMap=new HashMap<Byte,Integer>();
    
    public void addSourcePair(byte source,int index)
    {   sourceMap.put(source,index);
    }
    
    /** Output a destination index (generally this will correspond to the index 
     * of the input layer), given a byte identifying the event source.
     * @param source
     * @return 
     */
    public int source2dest(byte source)
    {   return sourceMap.get(source);
    }
    
    public void clearSourcePairs()
    {   sourceMap.clear();
    }
    
    long timeStamp2netTime(int eventTimeStamp)
    {   if (!baseTimeInitialized)
            initializeBaseTime(eventTimeStamp);
                    
        return eventTimeStamp-baseTime;        
    }
    
    public void initializeBaseTime(int baseTimeStamp)
    {
        baseTime=baseTimeStamp;
        baseTimeInitialized=true;
    }
    
    public boolean isBaseTimeInitialized()
    {   return baseTimeInitialized;        
    }
    
    double timeStamp2doubleTime(int eventTimeStamp,float scaleFactor)
    {   
        return (double) timeStamp2netTime(eventTimeStamp)*scaleFactor;  
    }
    
   
    
    /** Take a set of raw input locs, resize and map to an index, according 
     * to column-indexing (as in matlab). */
    int xy2ind(short x, short y)
    {   
        // 
        
        short newX=(short)(x*outDimX/inDimX);
        short newY=(short)(outDimY-1-(y*outDimY/inDimY));
        
        return outDimY*(newX)+newY;
    }
    
    /** Takes x,y locs on the output coordinate system, transfers them to indeces */
    int ixy2ind(short x,short y)
    {   // Takes x,y locs on the output coordinate system, transfers them to indeces
        
        return outDimY*(x)+y;
    }
    
    
    /** Take a set of relative input locs, on scale [-1 1] [-1 1], map them onto
     * the output */
    int xyCent2ind(float x, float y)
    {   
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
