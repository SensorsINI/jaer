/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.factory.FeatureExtractorFactory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier.FeatureNotifier;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public class TrackingFeatureManager implements FeatureManager {

    /** Stores the source of the manager. */
    private FeatureNotifier notifier;
    
    /** The last event added to this manager. */
    private TypedEvent event;
    
    /** The last packet added to this manager. */
    private List<TypedEvent> packet;
    
    /** Stores the instance of the factory used to create new extractors. */
    private FeatureExtractorFactory factory;
    
    /**
     * Stores the features.
     */
    private Map<Features, FeatureExtractor> features;
    
    /**
     * Creates a new instance of the class TrackingFeatureManager.
     */
    public TrackingFeatureManager(FeatureNotifier notifier, FeatureExtractorFactory factory) {
        this.notifier = notifier;
        this.factory = factory;
        
        this.features = new EnumMap<Features, FeatureExtractor>(Features.class);
        this.packet = new ArrayList<TypedEvent>();
    }

    @Override
    public void add(FeatureExtractor feature) {
        this.delete(feature.getIdentifier());
        
        this.features.put(feature.getIdentifier(), feature);
    }
    
    @Override
    public FeatureExtractor add(Features feature) {
        return this.factory.addFeature(this, feature);
    }

    @Override
    public FeatureExtractor get(Features feature) {
        if (!this.has(feature)) {
            return this.add(feature);
        }
        return this.features.get(feature);
    }
    
    @Override
    public Set<Features> getFeatures() {
        return this.features.keySet();
    }

    @Override
    public boolean has(Features feature) {
        if (feature == Features.None || 
                feature == Features.Event || 
                feature == Features.Packet) return true;
        
        return this.features.containsKey(feature);
    }

    @Override
    public FeatureExtractor remove(FeatureExtractor extractor) {
        return this.remove(extractor.getIdentifier());
    }
    
    @Override
    public FeatureExtractor remove(Features feature) {
        if (this.features.containsKey(feature)) {
            FeatureExtractor r = this.features.remove(feature);
            this.notifier.remove(r);
            
            return r;
        }
        return null;
    }
    
    @Override
    public void delete(Features feature) {
        if (this.features.containsKey(feature)) {
            this.remove(feature).delete();
        }
    }
    
    @Override
    public void clean() {
        if (this.features.isEmpty()) return;
        
        Set<Features> keys = this.features.keySet();
        for (Features key : keys) {
            this.delete(key);
        }
        
        this.clean();
    }
    
    @Override
    public FeatureNotifier getNotifier() {
        return this.notifier;
    }

    @Override
    public void add(TypedEvent event) {
        this.event = event;
        this.packet.add(event);
        
        this.notifier.notify(Features.Event, event.timestamp);
    }

    @Override
    public void packet(int timestamp) {
        this.notifier.notify(Features.Packet, timestamp);
        
        this.packet.clear();
    }

    @Override
    public TypedEvent getEvent() {
        return this.event;
    }

    @Override
    public List<TypedEvent> getPacket() {
        return this.packet;
    }

    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        int offset = 0;
        Collection<FeatureExtractor> lfe = this.features.values();
        for (FeatureExtractor extractor : lfe) {
            synchronized(extractor) {
                extractor.draw(drawable, renderer, x, y - offset);
                offset += extractor.getHeight();
            }
        }
    }

    @Override
    public int getHeight() {
        int offset = 0;
        Collection<FeatureExtractor> lfe = this.features.values();
        for (FeatureExtractor extractor : lfe) {
            offset += extractor.getHeight();
        }
        return offset;
    }
}
