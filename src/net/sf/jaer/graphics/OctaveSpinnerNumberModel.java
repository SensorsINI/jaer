package net.sf.jaer.graphics;

import javax.swing.SpinnerNumberModel;

/**
 * Spinner model with octave increments/decrements.
 *
 * @author tobi
 */
public class OctaveSpinnerNumberModel extends SpinnerNumberModel {
	public OctaveSpinnerNumberModel(int value, int minimum, int maximum, int stepSize) {
		super(value, minimum, maximum, stepSize);
	}

	@Override
	public Object getNextValue() {
		int n = 2 * (Integer) getValue();
		if (n > (Integer) getMaximum()) {
			n = (Integer) getMaximum();
		}
		return Integer.valueOf(n);
	}

	@Override
	public Object getPreviousValue() {
		int n = (Integer) getValue() / 2;
		if (n < (Integer) getMinimum()) {
			n = (Integer) getMinimum();
		}
		return n;
	}
}
