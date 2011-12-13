/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.Histogram;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.sun.opengl.util.j2d.TextRenderer;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;

/**
 *
 * @author matthias
 * 
 * Provides methods used by any implementation of the interface TemporalPattern.
 * Concrete implementations of this abstract class have to define how the signal
 * has to be constructed out of the input.
 */
public abstract class AbstractTemporalPattern implements TemporalPattern {
    
    /** A static counter used to have an unique identifier. */
    protected static int identifier = 0;
    
    /** The name of the signal. */
    protected String name;
    
    /** The color of the signal. */
    protected Color color;
    
    /** The histogram of the time distribution between an on- and an off-event. */
    protected Histogram histogramOn2Off;
    
    /** The histogram of the time distribution between an off- and an on-event. */
    protected Histogram histogramOff2On;
    
    /** The signal of the temporal pattern. */
    protected Signal signal;
    
    /**
     * Creates a new AbstractTemporalPattern.
     */
    public AbstractTemporalPattern() {
        this.histogramOn2Off = null;
        this.histogramOff2On = null;
        this.signal = null;
    }
    
    @Override
    public Histogram getHistogramOn2Off() {
        return this.histogramOn2Off;
    }

    @Override
    public Histogram getHistogramOff2On() {
        return this.histogramOff2On;
    }

    @Override
    public Signal getSignal() {
        return this.signal;
    }
    
    @Override
    public int getID() {
        return this.identifier;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Color getColor() {
        return this.color;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        GL gl = drawable.getGL();
        
        renderer.begin3DRendering();
        renderer.setColor(this.getColor().getFloat(0), this.getColor().getFloat(1), this.getColor().getFloat(2),0.8f);
        renderer.draw3D(this.getName(), x, y, 0, 0.5f);
        renderer.end3DRendering();
        
        if (this.getHistogramOff2On() != null && this.getHistogramOn2Off() != null) {
            gl.glColor3d(this.getColor().get(0), this.getColor().get(1), this.getColor().get(2));
            this.getHistogramOff2On().draw(drawable, renderer, x, y - 4, 8, 30);

            gl.glColor3d(this.getColor().get(0), this.getColor().get(1), this.getColor().get(2));
            this.getHistogramOn2Off().draw(drawable, renderer, x, y - 14, 8, 30);
        }
        
        if (this.getSignal() != null) {
            gl.glColor3d(this.getColor().get(0), this.getColor().get(1), this.getColor().get(2));
            this.getSignal().draw(drawable, renderer, this.getColor(), x, y - 24, 8, 50);
        }
    }

    @Override
    public int getHeight() {
        return 40;
    }
}
