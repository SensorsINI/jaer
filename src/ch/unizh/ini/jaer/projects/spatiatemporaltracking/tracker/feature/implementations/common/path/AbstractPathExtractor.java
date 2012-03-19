/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import java.util.ArrayList;
import java.util.List;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Concrete instances of this abstract class have to extract the path of the
 * observed object.
 */
public abstract class AbstractPathExtractor extends AbstractFeatureExtractor implements PathExtractor {

    /** Stores the path of the observed object. */
    protected PathLocation path;
    
    /** Stores the path for a visualization. */
    protected List<PathLocation> visualization;
    
    /**
     * Creates a new instance of a AbstractPathExtractor.
     */
    public AbstractPathExtractor(Features interrupt, 
                                 ParameterManager parameters, 
                                 FeatureManager features,
                                 AEChip chip) {
        super(interrupt, parameters, features, Features.Path, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.visualization = new ArrayList<PathLocation>();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.visualization.clear();
    }
    
    @Override
    public PathLocation getPath() {
        return this.path;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        GL gl = drawable.getGL();
        gl.glColor3f(0, 0, 1);
        
        while (this.visualization.size() > 100) {
            this.visualization.remove(0);
        }
        
        gl.glPointSize(3);
        gl.glBegin(GL.GL_POINTS);
        {
            for (int i = 0; i < this.visualization.size(); i++) {
                gl.glVertex2f(this.visualization.get(i).location.get(0), 
                              this.visualization.get(i).location.get(1));
            }
        }
        gl.glEnd();
        
        gl.glBegin(GL.GL_LINE_STRIP);
        {
            for (int i = 0; i < this.visualization.size(); i++) {
                gl.glVertex2f(this.visualization.get(i).location.get(0), 
                              this.visualization.get(i).location.get(1));
            }
        }
        gl.glEnd();
    }

    @Override
    public int getHeight() {
        return 0;
    }
}
