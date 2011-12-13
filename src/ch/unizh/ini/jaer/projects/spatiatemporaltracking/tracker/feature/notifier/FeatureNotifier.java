/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.listener.FeatureListener;

/**
 *
 * @author matthias
 */
public interface FeatureNotifier {
    
    /**
     * Adds the given listener to the notifier. Whenever the given feature is
     * changed the notifier has to call the listener.
     * 
     * @param listener The listener to add.
     * @param feature The feature defining the notification.
     */
    public void add(FeatureListener listener, Features feature);
    
    /**
     * Removes the given listener from the notifier.
     * 
     * @param listener The listener to remove from the notifier.
     */
    public void remove(FeatureListener listener);
    
    /**
     * Removes all listeners from the notifier.
     */
    public void clean();
    
    /**
     * This method is used to notifiy the listeners about a change of the given
     * feature.
     * 
     * @param feature The changed feature.
     * @param timestamp The timestamp of the algorithm.
     */
    public void notify(Features feature, int timestamp);
}
