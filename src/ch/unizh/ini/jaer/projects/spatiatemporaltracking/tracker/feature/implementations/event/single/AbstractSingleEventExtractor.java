/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.single;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public abstract class AbstractSingleEventExtractor extends AbstractFeatureExtractor implements SingleEventExtractor {

    /** Stores the event of the extractor. */
    protected TypedEvent event;
    
    /**
     * Creates a new instance of a AbstractSingleEventExtractor.
     */
    public AbstractSingleEventExtractor(Features interrupt, 
                                        ParameterManager parameters, 
                                        FeatureManager features,
                                        AEChip chip) {
        super(interrupt, parameters, features, Features.Event, Color.getBlue(), chip);
    }
    
    @Override
    public TypedEvent getEvent() {
        return this.event;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) { }

    @Override
    public int getHeight() {
        return 0;
    }
}
