/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods used by concrete implementations of the
 * interface OccurancePredictor.
 */
public abstract class AbstractOccurancePredictor extends AbstractFeatureExtractor implements OccurancePredictor {

    /**
     * Creates a new instance of AbstractOccurancePredictor.
     */
    public AbstractOccurancePredictor(Features interrupt,
                                      ParameterManager parameters, 
                                      FeatureManager features,
                                      AEChip chip) {
        super(interrupt, parameters, features, Features.Occurance, Color.getYellow(), chip);
        
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
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        GL gl = drawable.getGL();
        gl.glColor3d(this.color.getFloat(0), 
                     this.color.getFloat(1), 
                     this.color.getFloat(2));
        
        if (this.isDebugged) {
            int step = 100;
            float max = 0;
            float[] states = new float[100];
            float pack = 75.0f / states.length;

            /**
             * draws the differences
             */
            for (int type = 0; type < 2; type++) {
                renderer.begin3DRendering();
                renderer.setColor(this.color.getFloat(0), 
                                  this.color.getFloat(1), 
                                  this.color.getFloat(2),
                                  0.8f);
                renderer.draw3D("differences '" + type + "': ", x, y - type * 4, 0, 0.5f);
                renderer.end3DRendering();

                max = 0; 
                for (int i = 0; i < states.length; i++) {
                    states[i] = Math.abs(this.getRelativeDistance(type, i * step));

                    if (states[i] > max) max = states[i];
                }
                if (max == 0) max = 1;

                gl.glBegin(GL.GL_LINE_STRIP);
                {
                    for (int i = 0; i < states.length; i++) {
                        gl.glVertex2f(x + i * pack + 25, y - states[i] / max * 3 - type * 4 + 3);
                    }
                }
                gl.glEnd();
            }
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 10;
        return 0;
    }
}
