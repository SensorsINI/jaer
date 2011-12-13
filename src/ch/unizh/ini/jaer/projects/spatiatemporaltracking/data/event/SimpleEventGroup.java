/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.event;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public class SimpleEventGroup implements EventGroup {

    /** The timestamp of the group. */
    private double timestamp;
            
    /** The maximal timestamp of the group. */
    private int maxTimestamp;
    
    /** Stores the type of the group. */
    private int type;
    
    /** Stores all events belonging to this group. */
    private List<TypedEvent> events;
    
    /**
     * Creates a new instance of the class SimpleEventGroup.
     * 
     * @param e The first event of the group.
     */
    public SimpleEventGroup(TypedEvent e) {
        this.events = new ArrayList<TypedEvent>();
        
        this.type = e.type;
        this.timestamp = 0;
        this.maxTimestamp = 0;
        this.add(e);
    }
    
    @Override
    public void add(TypedEvent e) {
        this.timestamp = (this.timestamp * this.events.size() + e.timestamp) / (this.events.size() + 1);
        this.events.add(e);
        
        this.maxTimestamp = Math.max(this.maxTimestamp, e.timestamp);
    }
    
    @Override
    public void add(EventGroup group) {
        this.timestamp = (this.timestamp * this.events.size() + group.getTimestamp() * group.getSize()) / (this.events.size() + group.getSize());
        this.events.addAll(group.getEvents());
        
        for (TypedEvent e : group.getEvents()) this.maxTimestamp = Math.max(this.maxTimestamp, e.timestamp);
    }
    
    @Override
    public int getType() {
        return this.type;
    }
    
    @Override
    public double getTimestamp() {
        return this.timestamp;
    }
    
    @Override
    public int getMaxTimestamp() {
        return this.maxTimestamp;
    }

    @Override
    public int getSize() {
        return this.events.size();
    }
    
    @Override
    public List<TypedEvent> getEvents() {
        return this.events;
    }

    @Override
    public void draw(GLAutoDrawable drawable, int current, int resolution) {
        GL gl = drawable.getGL();
        
        float hue = Math.max(0, 0.7f - (this.events.size()) / 10.0f);
        Color c = new Color(Color.HSBtoRGB(hue, 1.0f, 1.0f));                
        gl.glColor3f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
        
        TypedEvent[] es = this.events.toArray(new TypedEvent[0]);
        
        gl.glPointSize(3);
        gl.glBegin(GL.GL_POINTS);
        {
            for (int i = 0; i < es.length; i++) {
                gl.glVertex3f(es[i].x, es[i].y, (current - es[i].timestamp) / resolution);
            }
        }
        gl.glEnd();
    }
}
