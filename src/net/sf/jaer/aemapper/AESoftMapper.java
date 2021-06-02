/*
 * AESoftMapper.java
 *
 * Created on October 1, 2006, 11:33 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.aemapper;

import java.util.Collection;

/**
 * A monitor sequencer device signals it's willingness to remap events from input to output by implementing these methods.
 Then host software remaps events from the monitor to the sequencer through the mappings.
 
 * @author tobi
 */
public interface AESoftMapper {
    
//    public void registerMapper(AEMapper mapper);
//    public void unregisterMapper(AEMapper mapper);
//    public void unregisterAllMappers();
    
    /** Returns the mappers from this soft mapper.
     *
     * @return a collection of AEMapper's.
     */
    public Collection<AEMapper> getAEMappers();
    
}
