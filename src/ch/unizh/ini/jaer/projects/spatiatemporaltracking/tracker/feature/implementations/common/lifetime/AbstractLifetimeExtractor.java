/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
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
 * The concrete implementations of this class have to extract the lifetime
 * of the observed object out of the given events.
 */
public abstract class AbstractLifetimeExtractor extends AbstractFeatureExtractor implements LifetimeExtractor {

	/** Stores the lifetime. */
	protected int lifetime;

	/** Stores the time of the creation of the object. */
	protected int creationtime;

	/**
	 * Creates a new instance of a AbstractLifetimeExtractor.
	 */
	public AbstractLifetimeExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Lifetime, Color.getBlue(), chip);
	}

	@Override
	public void reset() {
		super.reset();

		lifetime = 0;
		creationtime = 0;
	}

	@Override
	public int getLifetime() {
		return lifetime;
	}

	@Override
	public int getCreationTime() {
		return creationtime;
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
		return "lifetime [us]: " + lifetime;
	}
}
