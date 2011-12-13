/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter;

/**
 *
 * @author matthias
 * 
 * This class maintains the different instances using different parameters 
 * maintained by the manager. If there is a change of the parameters the
 * manager has to directly inform all its assinged listeners.
 */
public interface ParameterManager {
    
    /**
     * Assigns a new listener to the manager. If there is a change
     * of the parameters the manager has to inform all assigned listeners.
     * 
     * @param listener The listener to add.
     */
    public void add(ParameterListener listener);
    
    /**
     * Removes an assigned listener from the manager.
     * @param listener 
     */
    public void remove(ParameterListener listener);
    
    /**
     * Updates all assigned listeners.
     */
    public void updateListeners();
}
