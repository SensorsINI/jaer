/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.listener.FeatureListener;

/**
 *
 * @author matthias
 */
public class TrackingFeatureNotifier implements FeatureNotifier {
    
    /**
     * Stores the listeners for the different features.
     */
    private Map<Features, List<FeatureListener>> listeners;
    
    public TrackingFeatureNotifier() {
        this.listeners = new EnumMap<Features, List<FeatureListener>>(Features.class);
    }
    
    @Override
    public void add(FeatureListener listener, Features feature) {
        if (!this.listeners.containsKey(feature)) {
            this.listeners.put(feature, new ArrayList<FeatureListener>());
        }
        
        if (!this.listeners.get(feature).contains(listener)) {
            this.listeners.get(feature).add(listener);
        }
    }

    @Override
    public void remove(FeatureListener listener) {
        for (List<FeatureListener> l : this.listeners.values()) {
            l.remove(listener);
        }
    }
    
    @Override
    public void clean() {
        this.listeners.clear();
    }

    @Override
    public void notify(Features feature, int timestamp) {
        if (this.listeners.containsKey(feature)) {
            
            List<FeatureListener> l = this.listeners.get(feature);
            for (int i = 0; i < l.size(); i++) {
                l.get(i).update(timestamp);
            }
        }
    }
}
