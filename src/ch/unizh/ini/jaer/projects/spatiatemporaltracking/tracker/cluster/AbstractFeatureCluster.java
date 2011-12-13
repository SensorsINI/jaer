/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public abstract class AbstractFeatureCluster implements FeatureCluster {

    /**
     * Defines whether the feature has to be drawed or not.
     */
    protected boolean isDebugged = true;
    
    /**
     * Stores the instance maintaining the parameters.
     */
    protected ParameterManager manager;
    
    /**
     * Stores the instance which maintains the extractors of the different
     * features.
     */
    protected FeatureManager features;
    
    
    /** The TemporalPattern the object is assigned to. */
    protected TemporalPattern pattern;
    
    /** Indicates whether the object is assigned to a TemporalPattern or not. */
    protected boolean isAssigned;
    
    /** 
     * Stores the notices of the object. Just used to visualize its behavior 
     * during the process 
     */
    protected Map<String, String> notices = new HashMap<String, String>();

    /** The instance to write. */
    protected TextRenderer renderer = new TextRenderer(new Font("Arial",Font.PLAIN,7),true,true);
    
    /**
     * Creates a new instance of the class AbstractFeatureCluster.
     * 
     * @param features The manager maintaining the different extractors of the cluster.
     * @param manager The instance maintaining the different parameters fo the algorithm.
     */
    public AbstractFeatureCluster(FeatureManager features, 
                                  ParameterManager manager) {
        this.manager = manager;
        this.features = features;
    }
    
    @Override
    public void init() {
        this.manager.add(this);
    }
    
    @Override
    public void reset() {
        this.features.clean();
        
        this.isAssigned = false;
        
        this.parameterUpdate();
    }
    
    @Override
    public void assign(TypedEvent event) {
        this.features.add(event);
    }
    
    @Override
    public void assign(Collection<TypedEvent> events) {
        for (TypedEvent e : events) {
            this.assign(e);
        }
    }
    
    @Override
    public void packet(int timestamp) {
        this.features.packet(timestamp);
    }
    
    @Override
    public FeatureManager getFeatures() {
        return this.features;
    }
    
    @Override
    public void assign(TemporalPattern pattern) {
        this.pattern = pattern;
        
        this.isAssigned = true;
    }
    
    @Override
    public boolean isAssigned() {
        return this.isAssigned;
    }
    
    @Override
    public TemporalPattern getPattern() {
        return this.pattern;
    }
    
    @Override
    public void clear() {
        this.features.clean();
        this.manager.remove(this);
    }
    
    @Override
    public boolean isCandidate() {
        return false;
    }
    
    @Override
    public void add(String key, String value) {
        this.notices.put(key, value);
    }
    
    @Override
    public void draw(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        
        /*
         * The position and the size of the FeatureCluster are handled
         * in a special way since they are the most elementary information
         * of the cluster.
         */
        if (this.isAssigned()) {
            gl.glColor3d(this.getPattern().getColor().get(0),
                         this.getPattern().getColor().get(1),
                         this.getPattern().getColor().get(2));
        }
        else {
            gl.glColor3d(1.0, 0, 0);
        }
        
        gl.glLineWidth(2);
        Vector p = ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        float x = (p.get(0));
        float y = (p.get(1));
        
        gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2d(x + 7, y + 7);
            gl.glVertex2d(x - 7, y + 7);
            gl.glVertex2d(x - 7, y - 7);
            gl.glVertex2d(x + 7, y - 7);
        gl.glEnd();
        
        gl.glLineWidth(1);
        
        /*
         * Draws the rest of the information provided by this FeatureCluster.
         */
        int offset = 0;
        this.features.draw(drawable, this.renderer, x + 10 + 5, y + 5 - offset);
        offset += this.features.getHeight() + 4;
        
        if (this.isDebugged) {
            renderer.begin3DRendering();
            renderer.setColor(0,0,1,0.8f);
            Set<Map.Entry<String, String>> sn = this.notices.entrySet();
            for (Map.Entry<String, String> notice : sn) {
                renderer.draw3D(notice.toString(), x + 10 + 5, y + 5 - offset, 0, 0.5f);
                offset += 4;
            }
            renderer.end3DRendering();
        }
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.DEBUG_MODE)) this.isDebugged = Parameters.getInstance().getAsBoolean(Parameters.DEBUG_MODE);
    }
}
