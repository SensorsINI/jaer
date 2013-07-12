/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.interrupt;

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
 * Provides some basic methods used by implementations of the interface
 * InterruptExtractor.
 */
public abstract class AbstractInterruptExtractor extends AbstractFeatureExtractor implements InterruptExtractor {

	/**
	 * Creates a new instance of a AbstractInterruptExtractor.
	 */
	public AbstractInterruptExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Interrupt, Color.getBlue(), chip);

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
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) { }

	@Override
	public int getHeight() {
		return 0;
	}
}
