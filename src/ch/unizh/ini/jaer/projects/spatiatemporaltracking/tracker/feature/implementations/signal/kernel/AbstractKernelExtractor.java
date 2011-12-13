/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.kernel;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
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
 * This abstract class provides some basic operations used by implementations
 * of the interface KernelExtractor.
 */
public abstract class AbstractKernelExtractor extends AbstractFeatureExtractor implements KernelExtractor {

    /** Defiens the interval for the observation used for the visualization. */
    protected int interval = 20000;
    
    /** Stores the results of the different kernels. */
    protected Storage[] storage;
    
    /** Stores the results of the different kernels for a visualization. */
    protected List<List<Storage>> visualization;
    
    /** Stores a set of colors used by the visualization. */
    protected Color[] colors = {new Color(0, 0, 1), new Color(1, 0, 0)};
    
    /** Flag to reset the extractor. */
    protected boolean isReseted;
    
    /**
     * Creates a new instance of the class AbstractKernelExtractor.
     */
    public AbstractKernelExtractor(Features interrupt, 
                                   ParameterManager parameters, 
                                   FeatureManager features,
                                   AEChip chip) {
        super(interrupt, parameters, features, Features.Kernel, Color.getBlue(), chip);
    }
    
    @Override
    public void init() {
        super.init();
        
        this.storage = new Storage[2];
        
        this.visualization = new ArrayList<List<Storage>>();
        for (int i = 0; i < this.storage.length; i++) this.visualization.add(Collections.synchronizedList(new ArrayList<Storage>()));
        
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.isReseted = true;
    }
    
    @Override
    public Storage[] getStorage() {
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
            List<List<Storage>> l = new ArrayList<List<Storage>>();
            for (int i = 0; i < this.visualization.size(); i++) {
                while (!this.visualization.get(i).isEmpty() && 
                        (this.visualization.get(i).get(this.visualization.get(i).size() - 1).timestamp - this.visualization.get(i).get(0).timestamp > this.interval)) {
                    this.visualization.get(i).remove(0);
                }
                l.add(new ArrayList<Storage>());
                l.get(i).addAll(this.visualization.get(i));
            }
            
            /*
             * find start time of visualization
             */
            int reference = Integer.MAX_VALUE;
            for (int i = 0; i < this.visualization.size(); i++) {
                if (!l.get(i).isEmpty()) reference = Math.min(reference, this.visualization.get(i).get(0).timestamp);
            }

            /*
             * draw
             */

            float pack = 50f / this.interval;
            for (int i = 0; i < this.visualization.size(); i++) {
                renderer.begin3DRendering();
                renderer.setColor(0,0,1,0.8f);
                renderer.draw3D("assigned events of type: " + i, x, y, 0, 0.5f);
                renderer.end3DRendering();

                if (!l.get(i).isEmpty()) {
                    float max = 0;
                    for (int j = 0; j < l.get(i).size(); j++) {
                        for (int k = 0; k < l.get(i).get(j).absolute.length; k++) {
                            if (max < l.get(i).get(j).absolute[k]) max = l.get(i).get(j).absolute[k];
                        }
                    }

                    float dy = y - this.getHeight() + 3;
                    for (int k = 0; k < l.get(i).get(0).absolute.length; k++) {
                        gl.glColor3f(this.colors[k].getFloat(0), 
                                     this.colors[k].getFloat(1), 
                                     this.colors[k].getFloat(2));

                        gl.glBegin(GL.GL_LINE_STRIP);
                        {
                            for (int j = 0; j < l.get(i).size(); j++) {
                                int d = l.get(i).get(j).timestamp - reference;

                                if (d > this.interval) {
                                    j = l.get(i).size();
                                }
                                else {
                                    float dx = x + d * pack;

                                    float h = this.visualization.get(i).get(j).absolute[k] / max * (this.getHeight() - 4);
                                    gl.glVertex2f(dx, dy + h);
                                }
                            }
                        }
                        gl.glEnd();
                    }
                }
                x += 55;
            }
        }
        else {
            /*
             * delete all data for the visualization
             */
            for (int i = 0; i < this.visualization.size(); i++) {
                this.visualization.get(i).clear();
            }
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 10;
        return 0;
    }
}
