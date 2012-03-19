/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.correlation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationItem;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods for concrete implementations of the interface
 * SignalCorrelationExtractor.
 */
public abstract class AbstractSignalCorrelationExtractor extends AbstractFeatureExtractor implements SignalCorrelationExtractor {

    /** Determines the number of latency for which the extractor will compute the auto correlation. */
    public final int nObservations = 10;
    
    /** 
     * Stores the values computed using the auto correlation function and the
     * corresponding latencies.
     */
    protected List<CorrelationItem> correlation;
    
    /** Just used for the drawing. Stores a correlation to draw it. */
    protected List<CorrelationItem> drawing;
    
    /** Flag to reset the extractor. */
    protected boolean isReseted;
    
    /**
     * Creates a new AbstractSignalCorrelationExtractor;
     */
    public AbstractSignalCorrelationExtractor(Features interrupt,
                                              ParameterManager parameters, 
                                              FeatureManager features,
                                              AEChip chip) {
        super(interrupt, parameters, features, Features.Correlation, Color.getBlue(), chip);
    }
    
    @Override
    public void init() {
        super.init();
        
        this.correlation = new ArrayList<CorrelationItem>();
        this.drawing = Collections.synchronizedList(new ArrayList<CorrelationItem>());
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.isReseted = true;
        
        this.correlation.clear();
    }

    @Override
    public List<CorrelationItem> getCorrelation() {
        return this.correlation;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isReseted) {
            this.isReseted = false;
            this.drawing.clear();
        }
        if (this.drawing.isEmpty()) return;
        
        while (this.drawing.size() > this.nObservations) this.drawing.remove(0);
        
        if (this.isDebugged) {
            List<CorrelationItem> l = new ArrayList<CorrelationItem>(); l.addAll(this.drawing);
            int to = Math.min(this.nObservations, l.size());
            
            if (l.size() > 1) {
                renderer.begin3DRendering();
                renderer.setColor(0,0,1,0.8f);
                renderer.draw3D("signal correlation [au]: 0, " + (l.get(to - 1).value) + ".", x, y, 0, 0.5f);
                renderer.end3DRendering();

                GL gl = drawable.getGL();
                gl.glColor3d(0, 0, 1.0);

                float max = 0;
                for (int i = 0; i < to; i++) {
                    if (max < l.get(i).score) {
                        max = l.get(i).score;
                    }
                }

                int offset = l.get(0).value;
                float pack = 100.0f / (l.get(to - 1).value - offset);
                for (int i = 0; i < to - 1 ; i++) {
                    float start = (l.get(i).value - offset) * pack;
                    float end = (l.get(i + 1).value - offset) * pack;

                    float state = l.get(i).score / max * ((this.getHeight() - 5) / 2.0f);
                    float transition = l.get(i + 1).score / max * ((this.getHeight() - 5) / 2.0f);

                    gl.glBegin(GL.GL_LINES);
                    {
                        gl.glVertex2f(x + start, y - this.getHeight() / 2.0f + state + 1);
                        gl.glVertex2f(x + end, y - this.getHeight() / 2.0f + state + 1);
                    }
                    gl.glEnd();

                    gl.glBegin(GL.GL_LINES);
                    {
                        gl.glVertex2f(x + end, y - this.getHeight() / 2.0f + state + 1);
                        gl.glVertex2f(x + end, y - this.getHeight() / 2.0f + transition + 1);
                    }
                    gl.glEnd();
                }
            }
        }
    }

    @Override
    public int getHeight() {
        if (this.drawing.isEmpty()) return 0;
        if (this.isDebugged) return 10;
        return 0;
    }
}
