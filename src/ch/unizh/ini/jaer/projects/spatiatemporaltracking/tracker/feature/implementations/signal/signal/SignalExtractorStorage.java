/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Stores the extracted signal of the observed object.
 */
public class SignalExtractorStorage extends AbstractSignalExtractor {

    /**
     * Creates a new SignalExtractorStorage.
     */
    public SignalExtractorStorage(Signal signal,
                                  ParameterManager parameters, 
                                  FeatureManager features, 
                                  AEChip chip) {
        super(Features.None, parameters, features, chip);
        
        this.init();
        this.reset();
        
        this.signal = signal;
    }
    
    @Override
    public void update(int timestamp) { }
    
    @Override
    public boolean isStatic() {
        return true;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        /*
        renderer.begin3DRendering();
        renderer.draw3D(this.toString(), x, y, 0, 0.5f);
        renderer.end3DRendering();
        
        this.signal.draw(drawable, renderer, this.color, x, y - 4, this.getHeight() - 4, 100);
        */
    }

    @Override
    public int getHeight() {
        return 0;
        //return 14;
    }
    
    @Override
    public String toString() {
        return "<storage>";
    }
}
