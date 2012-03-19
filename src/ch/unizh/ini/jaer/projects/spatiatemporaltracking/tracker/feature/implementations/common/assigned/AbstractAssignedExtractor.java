/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.assigned;

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
 * This abstract class provides some basic methods used by implementations
 * of the interface AssignedExtractor.
 */
public abstract class AbstractAssignedExtractor extends AbstractFeatureExtractor implements AssignedExtractor {

    /** Defines the resolution with which the events are stores. */
    protected int resolution = 200;
    
    /** Defiens the number of elements used for the visualization. */
    protected int nVisualization = 100;
    
    /** Stores the events assigned to the object. */
    protected List<List<EventStorage>> storage;

    /** Stores the events for a visualization. */
    protected List<List<EventStorage>> visualization;
    
    /** Flag to reset the extractor. */
    protected boolean isReseted;
    
    /**
     * Creates a new instance of a AbstractAssignedExtractor.
     */
    public AbstractAssignedExtractor (Features interrupt,
                                      ParameterManager parameters, 
                                      FeatureManager features,
                                      AEChip chip) {
        super(interrupt, parameters, features, Features.Assigned, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.storage = new ArrayList<List<EventStorage>>();
        for (int i = 0; i < 2; i++) this.storage.add(new ArrayList<EventStorage>());
        
        this.visualization = new ArrayList<List<EventStorage>>();
        for (int i = 0; i < 2; i++) this.visualization.add(Collections.synchronizedList(new ArrayList<EventStorage>()));
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.storage.size(); i++) this.storage.get(i).clear();
        
        this.isReseted = true;
    }
    
    @Override
    public List<List<EventStorage>> getAssignedEvents() {
        return this.storage;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isReseted) {
            this.isReseted = false;
            for (int i = 0; i < this.visualization.size(); i++) this.visualization.get(i).clear();
        }
        
        if (this.isDebugged) {
            GL gl = drawable.getGL();
            
            /*
             * prepare
             */
            for (int i = 0; i < this.visualization.size(); i++) {
                while (this.visualization.get(i).size() > this.nVisualization) {
                    this.visualization.get(i).remove(0);
                }
            }
            
            /*
             * draw
             */
            float pack = 50f / this.nVisualization;
            for (int i = 0; i < this.visualization.size(); i++) {
                List<EventStorage> l = new ArrayList<EventStorage>(); l.addAll(this.visualization.get(i));
                int to = Math.min(l.size(), this.nVisualization);

                renderer.begin3DRendering();
                renderer.setColor(0,0,1,0.8f);
                renderer.draw3D("assigned events of type: " + i, x, y, 0, 0.5f);
                renderer.end3DRendering();

                float max = 0;
                for (int j = 0; j < to; j++) {
                    if (max < l.get(j).count) max = l.get(j).count;
                }

                for (int j = 0; j < to; j++) {
                    float h = l.get(j).count / max * (this.getHeight() - 4);

                    float dx = x + j * pack;
                    float dy = y - this.getHeight() + 3;

                    gl.glBegin(GL.GL_LINE_LOOP);
                    {

                        gl.glVertex2f(dx, dy);
                        gl.glVertex2f(dx, dy + h);

                        gl.glVertex2f(dx + pack, dy + h);
                        gl.glVertex2f(dx + pack, dy);
                    }
                    gl.glEnd();
                }
                x += 55;
            }
        }
        else {
            /*
             * delete all data for the visualization
             */
            for (int i = 0; i < this.visualization.size(); i++) {
                while (this.visualization.get(i).size() > this.nVisualization) {
                    this.visualization.get(i).clear();
                }
            }
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 10;
        return 0;
    }
}
