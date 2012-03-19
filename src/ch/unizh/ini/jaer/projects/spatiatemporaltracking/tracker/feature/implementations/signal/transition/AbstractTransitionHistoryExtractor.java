/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.FixedLengthTransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this class have to extract the 
 * transitions of the state of the observed object. Whenever the object
 * changes the state a transition has to describe this change with an instance
 * of the class <i>Transition</i>.
 */
public abstract class AbstractTransitionHistoryExtractor extends AbstractFeatureExtractor implements TransitionHistoryExtractor {
    
    /** Stores the new found transition of the observed object. */
    protected Transition transition;
    
    /** Just used to draw a meaningfull history. This is not used by the algorithm itself. */
    protected TransitionHistory visualization;
    
    /**
     * Creates a new instance of a AbstractTransitionHistoryExtractor.
     */
    public AbstractTransitionHistoryExtractor(Features interrupt,
                                              ParameterManager parameters, 
                                              FeatureManager features,
                                              AEChip chip) {
        super(interrupt, parameters, features, Features.Transition, Color.getBlue(), chip);
    }

    @Override
    public void init() {
        super.init();
        
        this.visualization = new FixedLengthTransitionHistory(20);
    }

    @Override
    public void reset() {
        super.reset();
        
        this.visualization.reset();
    }
    
    @Override
    public Transition getTransition() {
        return this.transition;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isDebugged) {
            GL gl = drawable.getGL();
            gl.glColor3d(0, 0, 1.0);

            this.visualization.draw(drawable, renderer, new Color(0, 0, 1), x, y, this.getHeight(), 100);
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 10;
        return 0;
    }
}
