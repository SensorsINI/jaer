/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this class have to extract the position
 * out of the given events.
 */
public abstract class AbstractPositionExtractor extends AbstractFeatureExtractor implements PositionExtractor {

	/** Stores the position of the object. */
	protected Vector position;

	/** Stores the current location of the object. */
	protected PathLocation current;

	/** Stores the previous location of the object. */
	protected PathLocation previous;

	/**
	 * Creates a new instance of a AbstractPositionExtractor.
	 */
	public AbstractPositionExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Position, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		position = new Vector(3);
	}

	@Override
	public void reset() {
		super.reset();

		position.reset();
	}

	@Override
	public Vector getPosition() {
		return position;
	}

	@Override
	public PathLocation getCurrentLocation() {
		return current;
	}

	@Override
	public PathLocation getPreviousLocation() {
		return previous;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		if (isDebugged) {
			renderer.begin3DRendering();
			renderer.setColor(0,0,1,0.8f);
			renderer.draw3D(toString(), x, y, 0, 0.5f);
			renderer.end3DRendering();
		}
	}

	@Override
	public int getHeight() {
		if (isDebugged) {
			return 4;
		}
		return 0;
	}

	@Override
	public String toString() {
		return "position: " + position.toString();
	}
}
