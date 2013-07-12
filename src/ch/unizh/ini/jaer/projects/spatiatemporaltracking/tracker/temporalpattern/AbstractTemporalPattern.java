/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.Histogram;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

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
		histogramOn2Off = null;
		histogramOff2On = null;
		signal = null;
	}

	@Override
	public Histogram getHistogramOn2Off() {
		return histogramOn2Off;
	}

	@Override
	public Histogram getHistogramOff2On() {
		return histogramOff2On;
	}

	@Override
	public Signal getSignal() {
		return signal;
	}

	@Override
	public int getID() {
		return identifier;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		GL2 gl = drawable.getGL().getGL2();

		renderer.begin3DRendering();
		renderer.setColor(getColor().getFloat(0), getColor().getFloat(1), getColor().getFloat(2),0.8f);
		renderer.draw3D(getName(), x, y, 0, 0.5f);
		renderer.end3DRendering();

		if ((getHistogramOff2On() != null) && (getHistogramOn2Off() != null)) {
			gl.glColor3d(getColor().get(0), getColor().get(1), getColor().get(2));
			getHistogramOff2On().draw(drawable, renderer, x, y - 4, 8, 30);

			gl.glColor3d(getColor().get(0), getColor().get(1), getColor().get(2));
			getHistogramOn2Off().draw(drawable, renderer, x, y - 14, 8, 30);
		}

		if (getSignal() != null) {
			gl.glColor3d(getColor().get(0), getColor().get(1), getColor().get(2));
			getSignal().draw(drawable, renderer, getColor(), x, y - 24, 8, 50);
		}
	}

	@Override
	public int getHeight() {
		return 40;
	}
}
