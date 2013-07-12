/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase;

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
 * Provides some basic methods used by concrete implementions of the interface
 * PhaseExtractor.
 */
public abstract class AbstractPhaseExtractor extends AbstractFeatureExtractor implements PhaseExtractor {

	/** Stores the phase of the signal. */
	protected int phase;

	/** Stores whether the phase of the signal was found or not. */
	protected boolean isFound;

	/**
	 * Creates a new instance of a AbstractPhaseExtractor.
	 */
	public AbstractPhaseExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Phase, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();
	}

	@Override
	public void reset() {
		super.reset();

		isFound = false;
	}

	@Override
	public int getPhase() {
		return phase;
	}

	@Override
	public boolean isFound() {
		return isFound;
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
		return "phase: " + phase;
	}
}
