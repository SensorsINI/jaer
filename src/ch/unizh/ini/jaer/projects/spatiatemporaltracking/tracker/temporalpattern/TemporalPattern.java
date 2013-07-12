/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import javax.media.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.Histogram;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to store the temporal pattern of a signal.
 */
public interface TemporalPattern {
	/**
	 * Gets the histogram of the time distribution between an on- and an
	 * off-event.
	 * 
	 * @return The histogram of the time distribution between an on- and an
	 * off-event.
	 */
	public Histogram getHistogramOn2Off();

	/**
	 * Gets the histogram of the time distribution between an off- and an
	 * on-event.
	 * 
	 * @return The histogram of the time distribution between an off- and an
	 * on-event.
	 */
	public Histogram getHistogramOff2On();

	/**
	 * Gets the signal of the temporal pattern.
	 * @return
	 */
	public Signal getSignal();

	/**
	 * Gets the identifier for the registered temporal pattern.
	 * 
	 * @return The identifier of the registered temporal pattern.
	 */
	public int getID();

	/**
	 * Gets the name of the registered temporal pattern.
	 * 
	 * @return The name of the registered temporal pattern.
	 */
	public String getName();

	/**
	 * Gets the color of the registered temporal pattern.
	 * 
	 * @return The color of the registered temporal pattern.
	 */
	public Color getColor();

	/*
	 * Draws the information of the temporal pattern.
	 * 
	 * @param drawable The object to draw.
	 * @param renderer The object to write.
	 * @param x Position in x direction of the object.
	 * @param y Position in y direction of the object.
	 */
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y);

	/*
	 * Gets the height of the drawed object.
	 * 
	 * @return The height of the drawed object.
	 */
	public int getHeight();
}
