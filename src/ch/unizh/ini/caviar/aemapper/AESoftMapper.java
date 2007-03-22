/*
 * AESoftMapper.java
 *
 * Created on October 1, 2006, 11:33 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.aemapper;

import java.util.*;

/**
 * A monitor sequencer device signals it's willingness to remap events from input to output by implementing these methods.
 Then host software remaps events from the monitor to the sequencer through the mappings.
 
 * @author tobi
 */
public interface AESoftMapper {
    
//    public void registerMapper(AEMapper mapper);
//    public void unregisterMapper(AEMapper mapper);
//    public void unregisterAllMappers();
    public Collection<AEMapper> getAEMappers();
    
}
