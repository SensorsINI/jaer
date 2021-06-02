/*
 * LUTMapper.java
 *
 * Created on September 29, 2006, 4:29 PM
 *
 * Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package net.sf.jaer.aemapper;

import java.util.HashMap;

/**
 * Class that holds a lookup table mapper.
 
 * @author tobi
 */
public class LUTMapper extends HashMap<Integer,int[]> implements AEMap {
    
    public void setMapping(int src, int[] dest){
        put(src,dest);
    }
    
    public int[] getMapping(int src){
        return get(src);
    }
    
    public void addToMapping(int src, int[] toAdd){
        int[] old=get(src);
        int[] newd=new int[old.length+toAdd.length];
        System.arraycopy(old,  0,newd,0,old.length);
        System.arraycopy(toAdd,0,newd,old.length,toAdd.length);
        put(src,newd);
        old=null;
    }
    
    public void clearMapping(int src){
        put(src,null);
    }
    
}
