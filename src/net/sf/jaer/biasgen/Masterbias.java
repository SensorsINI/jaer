/*
 * Masterbias.java
 *
 * Created on September 22, 2005, 9:00 AM
 */

package net.sf.jaer.biasgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Observable;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Describes the master bias circuit and its configuration, and supplies methods for estimating parameters.
 * <p>
 * The schematic of a masterbias looks like this:
 * <p>
 * <img src="doc-files/masterbiasSchematic.png" />
 * <p>
 * The master current is estimated in strong inversion as indicated in
 * <a href="http://www.ini.unizh.ch/~tobi/biasgen">http://www.ini.unizh.ch/~tobi/biasgen/> as follows
 * <p>
 * <img src="doc-files/masterCurrentFormulas.png">
 * See http://www.ini.unizh.ch/~tobi/biasgen
 *
 * @author tobi
 */
public class Masterbias extends Observable implements BiasgenPreferences {
	protected Preferences prefs;
	protected Biasgen biasgen;
	/** Observable event */
	public static final String EVENT_POWERDOWN = "powerDownEnabled", EVENT_INTERNAL_RESISTOR = "internalResistor",
		EVENT_EXTERNAL_RESISTOR = "externalResistor", EVENT_INTERNAL_RESISTOR_USED = "internalReisistorUsed",
		EVENT_TEMPERATURE_CHANGED = "temperatureChange";

	/** Creates a new instance of Masterbias. */
	public Masterbias(Biasgen biasgen) {
		this.biasgen = biasgen;
		prefs = biasgen.getChip().getPrefs();
	}

	/** true to power down masterbias, via powerDown input */
	public boolean powerDownEnabled = false;

	/** the total multiplier for the n-type current mirror */
	private float multiplier = (9f * 24f) / 4.8f;

	/** W/L ratio of input side of n-type current mirror */
	private float WOverL = 4.8f / 2.4f;

	/** boolean that is true if we are using the internal resistor in series with an off-chip external resistor */
	public boolean internalResistorUsed = false;

	/** internal (on-chip) resistor value. This is what you get when you tie pin rInternal to ground. */
	public float rInternal = 46e3f;

	/**
	 * external resistor used. This can be the only capacitance
	 * if you tie it in line with rExternal, "rx". If you tie it to rInternal, you get the sum.
	 */
	public float rExternal = 8.2e3f;

	public float getTotalResistance() {
		if (internalResistorUsed) {
			return rInternal + rExternal;
		}

		return rExternal;
	}

	/** Temperature in degrees celsius */
	public float temperatureCelsius = 25f;

	/**
	 * The value of beta=mu*Cox*W/L in Amps/Volt^2, called gain factor KPN by some fabs:
	 * Gain factor KP is measured from the slope of the large transistor, where Weff / Leff ~ W/L.
	 * <p>
	 * The drain voltage is forced to 0.1V, source and bulk are connected to ground. The gate voltage is swept to find
	 * the maximum
	 * slope of the drain current as a function of the gate voltage, i.e., the transconductance for triode/linear/ohmic
	 * operation.
	 * <p>
	 * A linear regression is performed around this operating point.
	 * The voltage sweep is positive for n-channel devices and negative for p-channel devices.
	 */
	private float kPrimeNFet = 170e-6f;

	/** @return thermal voltage, computed from temperature */
	float thermalVoltage() {
		return (26e-3f * (temperatureCelsius + 273f)) / 300f;
	}

	/**
	 * Estimated weak inversion current: log(M) UT/R.
	 *
	 * @return current in amps.
	 */
	public float getCurrentWeakInversion() {
		float iweak = (float) ((thermalVoltage() / getTotalResistance()) * Math.log(multiplier));
		return iweak;
	}

	/**
	 * Estimated current if master running in strong inversion,
	 * computed from formula in <a href="http://www.ini.unizh.ch/~tobi/biasgen">http://www.ini.unizh.ch/~tobi/biasgen/>
	 * paper.
	 *
	 * @return current in amps.
	 */
	public float getCurrentStrongInversion() {
		float r = getTotalResistance();
		float r2 = r * r;
		float beta = kPrimeNFet * WOverL; // beta is mu*Cox*W/L, kPrimeNFet=beta*(W/L)
		float rfac = 2 / (beta * r2);
		float mfac = (float) (1 - (1 / Math.sqrt(multiplier)));
		mfac = mfac * mfac;
		float istrong = rfac * mfac;
		return istrong;
	}

	/**
	 * The sum of weak and strong inversion master current estimates.
	 *
	 * @return current in amps.
	 */
	public float getCurrent() {
		float total = getCurrentStrongInversion() + getCurrentWeakInversion();
		return total;
	}

	public float getRInternal() {
		return this.rInternal;
	}

	public void setRInternal(final float rint) {
		if (rint != rInternal) {
			this.rInternal = rint;
			setChanged();
			notifyObservers(EVENT_INTERNAL_RESISTOR);
		}
	}

	public float getRExternal() {
		return this.rExternal;
	}

	public void setRExternal(final float rx) {
		if (rx != rExternal) {
			this.rExternal = rx;
			setChanged();
			notifyObservers(EVENT_EXTERNAL_RESISTOR);
		}
	}

	private String prefsKey() {
		return biasgen.getChip().getClass().getSimpleName() + ".Masterbias.";
	}

	@Override
	public void loadPreferences() {
		setRExternal(prefs.getFloat(prefsKey() + "rx", getRExternal()));
		setRInternal(prefs.getFloat(prefsKey() + "rint", getRInternal()));
		setInternalResistorUsed(prefs.getBoolean(prefsKey() + "internalResistorUsed", isInternalResistorUsed()));
	}

	@Override
	public void storePreferences() {
		prefs.putFloat(prefsKey() + "rx", getRExternal());
		prefs.putFloat(prefsKey() + "rint", getRInternal());
		prefs.putBoolean(prefsKey() + "internalResistorUsed", isInternalResistorUsed());
	}

	public boolean isPowerDownEnabled() {
		return this.powerDownEnabled;
	}

	public void setPowerDownEnabled(final boolean powerDownEnabled) {
		if (powerDownEnabled != this.powerDownEnabled) {
			this.powerDownEnabled = powerDownEnabled;
			setChanged();
			notifyObservers(EVENT_POWERDOWN);
		}
	}

	public boolean isInternalResistorUsed() {
		return this.internalResistorUsed;
	}

	public void setInternalResistorUsed(final boolean internalResistorUsed) {
		if (internalResistorUsed != this.internalResistorUsed) {
			this.internalResistorUsed = internalResistorUsed;
			setChanged();
			notifyObservers(EVENT_INTERNAL_RESISTOR_USED);
		}
	}

	public float getTemperatureCelsius() {
		return this.temperatureCelsius;
	}

	public void setTemperatureCelsius(final float temperatureCelsius) {
		if (this.temperatureCelsius != temperatureCelsius) {
			this.temperatureCelsius = temperatureCelsius;
			setChanged();
			notifyObservers(EVENT_TEMPERATURE_CHANGED);
		}
	}

	@Override
	public String toString() {
		return "Masterbias with Rexternal=" + getRExternal() + " temperature(C)=" + getTemperatureCelsius() + " masterCurrent="
			+ getCurrent();
	}

	public float getMultiplier() {
		return multiplier;
	}

	/**
	 * The mirror ratio in the Widlar bootstrapped mirror.
	 *
	 * @param multiplier
	 */
	public void setMultiplier(float multiplier) {
		this.multiplier = multiplier;
	}

	public float getWOverL() {
		return WOverL;
	}

	/**
	 * The W/L aspect ratio of the Widlar bootstrapped mirror n-type multiplying mirror.
	 *
	 * @param WOverL
	 */
	public void setWOverL(float WOverL) {
		this.WOverL = WOverL;
	}

	public float getKPrimeNFet() {
		return kPrimeNFet;
	}

	/**
	 * The K' parameter for nfets which is beta*W/L = mu*Cox*W/L for the basic above-threshold MOS model.
	 * beta is mu*Cox*W/L, kPrimeNFet=beta*(W/L).
	 *
	 * @param kPrimeNFet
	 */
	public void setKPrimeNFet(float kPrimeNFet) {
		this.kPrimeNFet = kPrimeNFet;
	}

	@Override
	public void exportPreferences(OutputStream os) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException, HardwareInterfaceException {
		// TODO Auto-generated method stub

	}
}
