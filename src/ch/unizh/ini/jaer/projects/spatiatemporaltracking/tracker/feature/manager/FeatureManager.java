/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier.FeatureNotifier;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.List;
import java.util.Set;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public interface FeatureManager {
    
    /**
     * Adds a new extractor to the manager if the feature is not yet presentd.
     * 
     * @param feature The new extractor for the manager.
     */
    public void add(FeatureExtractor feature);
    
    /**
     * Adds a new feature to the manager if the feature is not yet presentd.
     * 
     * @param feature The new feature for the manager.
     * 
     * @return The new created FeatureExtractor.
     */
    public FeatureExtractor add(Features feature);
    
    /**
     * Gets the feature specified by the given type.
     * 
     * @param feature The type of the feature.
     * @return The feature specified by the given type.
     */
    public FeatureExtractor get(Features feature);
    
    /**
     * Gets a set with all features.
     * 
     * @return A set with all features.
     */
    public Set<Features> getFeatures();
    
    /**
     * Indicates whether the feature is available or not.
     * 
     * @param feature The type of the feature asked.
     * @return True, if the feature exists on the manager, false otherwise.
     */
    public boolean has(Features feature);
    
    /**
     * Removes the extractor from the manager.
     * 
     * @param extractor The extractor to remove from the manager.
     */
    public FeatureExtractor remove(FeatureExtractor extractor);
    
    /**
     * Removes the feature from the manager.
     * 
     * @param feature The feature to remove from the manager.
     */
    public FeatureExtractor remove(Features feature);
    
    /**
     * Deletes the feature from the manager.
     * 
     * @param feature The feature to delete from the manager.
     */
    public void delete(Features feature);
    
    /**
     * Removes all extractors from the manager.
     */
    public void clean();
    
    /**
     * Gets the notifier used by the manager.
     * 
     * @return The notifier used by the manager.
     */
    public FeatureNotifier getNotifier();
    
    /**
     * Adds the given event to the manager.
     * 
     * @param event The event to add to the manager.
     */
    public void add(TypedEvent event);
    
    /**
     * Gets the last event.
     * 
     * @return The last event.
     */
    public TypedEvent getEvent();
    
    /**
     * Indicates that a packet was successfully processed.
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    public void packet(int timestamp);
    
    /**
     * Gets the last packet of events.
     * 
     * @return The last packet of events.
     */
    public List<TypedEvent> getPacket();
    
    /*
     * Draws the informations of this instance.
     * 
     * @param drawable The object to draw.
     * @param renderer The object to write.
     * @param x Position in x direction of the object.
     * @param y Position in y direction of the object.
     */
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y);
    
    /*
     * Gets the height of the drawed object.
     * 
     * @return The height of the drawed object.
     */
    public int getHeight();
}
