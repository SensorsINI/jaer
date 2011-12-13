/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.Arrays;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods for implementations fo the interface
 * MomentExtractor.
 */
public abstract class AbstractMomentExtractor extends AbstractFeatureExtractor implements MomentExtractor {

    /** Determines how many moments of the object have to be computed. */
    protected int nMoments = 3;
    
    /** Stores the computed moments. */
    protected int moments[][];
    
    /**
     * Creates a new instance of a AbstractMomentExtractor.
     */
    public AbstractMomentExtractor(Features interrupt, 
                                   ParameterManager parameters, 
                                   FeatureManager features,
                                   AEChip chip) {
        super(interrupt, parameters, features, Features.Moment, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.moments = new int[this.nMoments][this.nMoments];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.moments.length; i++) Arrays.fill(this.moments[i], 0);
    }
    
    @Override
    public int getMoment(int i, int j) {
        return this.moments[i][j];
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) { }

    @Override
    public int getHeight() {
        return 0;
    }
}
