/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator.SignalCreator;
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
 * The concrete implementations of this class have to extract the signal
 * out of the observed object based on its assigned events.
 */
public abstract class AbstractSignalExtractor extends AbstractFeatureExtractor implements SignalExtractor {

	/** Stores the signal of the observed object. */
	protected Signal signal;

	/** Stores the instance to create the signal. */
	protected SignalCreator creator = null;

	/**
	 * Creates a new instance of a AbstractSignalExtractor.
	 */
	public AbstractSignalExtractor(Features interrupt,
		ParameterManager parameters,
		FeatureManager features,
		AEChip chip) {
		super(interrupt, parameters, features, Features.Signal, Color.getBlue(), chip);

		init();
		reset();
	}

	@Override
	public void init() {
		super.init();

		signal = new SimpleSignal();
	}

	@Override
	public void reset() {
		super.reset();

		signal.reset();
		if (creator != null) {
			creator.reset();
		}
	}

	@Override
	public Signal getSignal() {
		return signal;
	}

	@Override
	public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
		if (isDebugged) {
			Signal s = new SimpleSignal(signal);
			s.draw(drawable, renderer, new Color(0, 0, 1), x, y, getHeight(), 100);
		}
	}

	@Override
	public int getHeight() {
		if (isDebugged) {
			return 10;
		}
		return 0;
	}
}
