/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period;

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
 * The concrete implementations of this class have to extract the period
 * of the signal of the observed object as well as its phase.
 */
public abstract class AbstractPeriodExtractor extends AbstractFeatureExtractor implements PeriodExtractor {

	/** Stores the period of the signa. */
	protected int period;

	/**
	 * Creates a new instance of a AbstractPeriodExtractor.
	 */
	public AbstractPeriodExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Period, Color.getBlue(), chip);
	}

	@Override
	public void init() {
		super.init();
	}

	@Override
	public void reset() {
		super.reset();

		period = 0;
	}

	@Override
	public int getPeriod() {
		return period;
	}

	@Override
	public boolean isValid() {
		return period > 0;
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
		return "period: " + period + " us";
	}
}
