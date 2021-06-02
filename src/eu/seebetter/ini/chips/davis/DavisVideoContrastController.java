/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.geom.Point2D;
import java.beans.PropertyChangeSupport;
import java.util.Observable;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.util.filter.LowpassFilter2D;

/**
 * Encapsulates control of rendering contrast of rendered DAVIS APS images
 * including automatic control of contrast and brightness.
 * <p>
 * The rendered gray value v is computed from ADC sample s using brightness and
 * contrast values, along with maxADC count by following equation.
 * <br>
 * float v=(s-brightness)*contrast
 * <br>
 * The result is clipped to 0-1 range.
 *
 * The auto contrast feature low-pass filters the min and max ADC samples from a
 * frame (min and max values are computed elsewhere) to automatically scale
 * video to 0-1 range.
 *
 * @author Tobi
 */
public class DavisVideoContrastController extends Observable {

	private static Logger log = Logger.getLogger("DavisVideoContrastController");
	DavisChip chip;
	Preferences prefs = Preferences.userNodeForPackage(this.getClass());

	public boolean useAutoContrast = prefs.getBoolean("DavisVideoContrastController.useAutoContrast", false);
	public float autoContrastControlTimeConstantMs = prefs.getFloat("DavisVideoContrastController.autoContrastControlTimeConstantMs",
		1000f);
	public float contrast = prefs.getFloat("DavisVideoContrastController.contrast", 1.0F);
	public float brightness = prefs.getFloat("DavisVideoContrastController.brightness", 0.0F);
	private float gamma = prefs.getFloat("DavisVideoContrastController.gamma", 1); // gamma control for improving
																					// display
	protected LowpassFilter2D autoContrast2DLowpassRangeFilter = new LowpassFilter2D(); // 2 lp values are min and max
																						// log intensities from each
																						// frame
	public static final String PROPERTY_CONTRAST = "contrast";
	public static final String PROPERTY_BRIGHTNESS = "brightness";
	public static final String PROPERTY_GAMMA = "gamma";
	public static final String AGC_VALUES = "AGCValuesChanged";
	public static final String PROPERTY_AUTO_CONTRAST_ENABLED = "useAutoContrast";
	/**
	 * Max allowed contrast
	 */
	public static final float MAX_CONTRAST = 5;

	public static int DEBUG_PRINT_INTERVAL = 50;
	/**
	 * The automatically-computed gain (computed in endFrame) applied to ADC
	 * samples
	 */
	protected float autoContrast = 1;
	/**
	 * The automatically-computed brightness (computed in endFrame) (offset ADC
	 * value) applied to ADC samples
	 */
	protected float autoBrightness = 0;

	public DavisVideoContrastController(final DavisChip chip) {
		this.chip = chip;
		autoContrast2DLowpassRangeFilter.setTauMs(autoContrastControlTimeConstantMs);
	}

	/**
	 * Takes ADC sample, max value, and returns float value in range 0-1
	 *
	 * @param adcCount
	 *            input adcValue
	 * @param maxADC
	 *            maximum ADC value, e.g. 1023 for 10-bit converter
	 * @return 0-1 range gray value
	 */
	public float normalizePixelGrayValue(final float adcCount, final int maxADC) {
		float v = 0;
		if (!isUseAutoContrast()) { // fixed rendering computed here
			final float gamma = getGamma();
			if (gamma == 1.0f) {
				v = (contrast * (adcCount + brightness)) / maxADC;
			}
			else {
				v = (float) (Math.pow(((contrast * (adcCount + brightness)) / maxADC), gamma));
			}
		}
		else {
			v = ((autoContrast * (adcCount + autoBrightness)) / maxADC);
		}
		if (v < 0) {
			v = 0;
		}
		else if (v > 1) {
			v = 1;
		}
		return v;
	}

	public boolean isUseAutoContrast() {
		return useAutoContrast;
	}

	public void setUseAutoContrast(final boolean useAutoContrast) {
		final boolean old = this.useAutoContrast;
		this.useAutoContrast = useAutoContrast;
		prefs.putBoolean("DavisVideoContrastController.useAutoContrast", useAutoContrast);
		if (chip.getAeViewer() != null) {
			chip.getAeViewer().interruptViewloop();
		}
		getSupport().firePropertyChange(DavisVideoContrastController.PROPERTY_AUTO_CONTRAST_ENABLED, old, this.useAutoContrast);
		if (old != useAutoContrast) {
			setChanged();
			notifyObservers(); // inform ParameterControlPanel
		}
		resetAutoContrast();

	}

	/**
	 * Provide new min/max values for a new frame to update auto contrast and
	 * brightness values. Fires an AGC_VALUES PropertyChange with newValue a
	 * Point2D.Float(autoBrightness, autoContrast) object.
	 *
	 * @param minValue
	 *            in ADC counts
	 * @param maxValue
	 *            in ADC counts
	 * @param timestamp
	 *            of frame in microseconds
	 */
	public void endFrame(final float minValue, final float maxValue, final int timestamp) {
		if (!useAutoContrast) {
			return;
		}
		if ((maxValue-minValue > 0)) { // don't adapt to first frame which is all zeros TODO does not work if
													// minValue<0
			final java.awt.geom.Point2D.Float filter2d = autoContrast2DLowpassRangeFilter.filter(minValue, maxValue, timestamp);
			autoBrightness = -filter2d.x;
			float diff = (filter2d.y - filter2d.x);
			if (diff < 1) {
				diff = 1;
			}
			autoContrast = chip.getMaxADC() / diff; // this value results in video value 1 when pixel is max value
			if (autoContrast > DavisVideoContrastController.MAX_CONTRAST) {
				autoContrast = 5;
			}
			getSupport().firePropertyChange(DavisVideoContrastController.AGC_VALUES, null, new Point2D.Float(autoBrightness, autoContrast)); // inform
			// listeners
			// (GUI)
			// of
			// new
			// AGC
			// min/max
			// filterd
			// log
			// intensity
			// values
			// if (debugPrintCounter++ % DEBUG_PRINT_INTERVAL == 0) {
			// log.info(this.toString());
			// }
		}
	}

	@Override
	public String toString() {
		final Point2D.Float minmax = autoContrast2DLowpassRangeFilter.getValue2D();
		return String.format("DavisVideoContrastController: minAvg=%-10.1f, maxAvg=%-10.1f, autoContrast=%-10.3f autoBrightness=%-10.3f",
			minmax.x, minmax.y, autoContrast, autoBrightness);
	}

	/**
	 * @return the contrast
	 */
	public float getContrast() {
		return contrast;
	}

	/**
	 * @param contrast
	 *            the contrast to set
	 */
	public void setContrast(final float contrast) {
		final float old = this.contrast;
		this.contrast = contrast;
		prefs.putFloat("DavisVideoContrastController.contrast", contrast);
		if (chip.getAeViewer() != null) {
			chip.getAeViewer().interruptViewloop();
		}
		getSupport().firePropertyChange(DavisVideoContrastController.PROPERTY_CONTRAST, old, contrast);
		if (old != contrast) {
			setChanged();
			notifyObservers(); // inform ParameterControlPanel
		}
	}

	/**
	 * @return the brightness
	 */
	public float getBrightness() {
		return brightness;
	}

	/**
	 * @param brightness
	 *            the brightness to set
	 */
	public void setBrightness(final float brightness) {
		final float old = this.brightness;
		this.brightness = brightness;
		prefs.putFloat("DavisVideoContrastController.brightness", brightness);
		if (chip.getAeViewer() != null) {
			chip.getAeViewer().interruptViewloop();
		}
		getSupport().firePropertyChange(DavisVideoContrastController.PROPERTY_BRIGHTNESS, old, this.brightness);
		if (old != brightness) {
			setChanged();
			notifyObservers(); // inform ParameterControlPanel
		}
	}

	/**
	 * @return the gamma
	 */
	public float getGamma() {
		return gamma;
	}

	/**
	 * @param gamma
	 *            the gamma to set
	 */
	public void setGamma(final float gamma) {
		final float old = this.gamma;
		this.gamma = gamma;
		prefs.putFloat("DavisVideoContrastController.gamma", gamma);
		if (chip.getAeViewer() != null) {
			chip.getAeViewer().interruptViewloop();
		}
		notifyObservers();
		getSupport().firePropertyChange(DavisVideoContrastController.PROPERTY_GAMMA, old, this.gamma);
		if (old != gamma) {
			setChanged();
			notifyObservers(); // inform ParameterControlPanel
		}
	}

	public float getAutoContrastTimeconstantMs() {
		return autoContrast2DLowpassRangeFilter.getTauMs();
	}

	public void setAutoContrastTimeconstantMs(float tauMs) {
		if (tauMs < 10) {
			tauMs = 10;
		}
		autoContrast2DLowpassRangeFilter.setTauMs(tauMs);
		prefs.putFloat("DavisVideoContrastController.autoContrastControlTimeConstantMs", tauMs);
		resetAutoContrast();
	}

	/**
	 * Properties changed in this fire events to this support.
	 *
	 * @return the PropertyChangeSupport
	 */
	public PropertyChangeSupport getSupport() {
		return chip.getSupport();
	}

	private void resetAutoContrast() {
		autoContrast2DLowpassRangeFilter.reset();
	}

	/**
	 * Return the automatic brightness (offset from zero) value
	 *
	 * @return the brightness
	 */
	public float getAutoBrightness() {
		return autoBrightness;
	}

	/**
	 * Return the automatic contrast value
	 *
	 * @return the contrast (gain) value
	 */
	public float getAutoContrast() {
		return autoContrast;
	}

}
