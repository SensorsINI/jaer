/*
 * AEMap.java
 *
 * Created on October 1, 2006, 11:28 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.aemapper;

/**
 * A mapping from source to destination events.
 
 * @author tobi
 */
public interface AEMap {
    /** get the array of destination addresses for a source address
     @return array of destination addresses
     @param src the source address
     */
    public int[] getMapping(int src);
}
