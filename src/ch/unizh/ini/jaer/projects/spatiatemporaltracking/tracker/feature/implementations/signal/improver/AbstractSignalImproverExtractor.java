/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.improver;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods used by implementations of the interface
 * SignalImproverExtractor.
 */
public abstract class AbstractSignalImproverExtractor extends AbstractFeatureExtractor implements SignalImproverExtractor {
    
    /** Stores the signal of the observed object. */
    protected Signal signal = null;
    
    /**
     * Creates a new instance of a AbstractSignalImproverExtractor.
     */
    public AbstractSignalImproverExtractor(Features interrupt,
                                           ParameterManager parameters, 
                                           FeatureManager features,
                                           AEChip chip) {
        super(interrupt, parameters, features, Features.Improver, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void reset() {
        super.reset();
    }
    
    @Override
    public Signal getSignal() {
        return this.signal;
    }

    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isDebugged) {
            renderer.begin3DRendering();
            renderer.setColor(0, 0, 1, 1);
            renderer.draw3D(this.toString(), x, y, 0, 0.5f);
            renderer.end3DRendering();

            this.signal.draw(drawable, renderer, new Color(0, 0, 1), x, y - 4, this.getHeight() - 4, 100);
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 14;
        return 0;
    }
    
    @Override
    public String toString() {
        return "<improver>";
    }
}
