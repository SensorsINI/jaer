/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator;

/**
 *
 * @author matthias
 * 
 * This abstract class provides some basic methods used by implementations
 * of the interface SignalCreator.
 */
public abstract class AbstractSignalCreator implements SignalCreator {
    
    @Override
    public void init() {}
    
    @Override
    public void reset() {}
}
