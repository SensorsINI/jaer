package ch.unizh.ini.jaer.config.spi;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ch.unizh.ini.jaer.config.ConfigInt;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;

public class SPIConfigInt extends SPIConfigValue implements ConfigInt {

	private final int defaultValue;
	private int value;

	private final Biasgen biasgen;
	private final Preferences sprefs;

	public SPIConfigInt(final String configName, final String toolTip, final short moduleAddr, final short paramAddr, final int numBits,
		final int defaultValue, final Biasgen biasgen) {
		super(configName, toolTip, (AEChip) biasgen.getChip(), moduleAddr, paramAddr, numBits);

		this.defaultValue = defaultValue;

		this.biasgen = biasgen;
		sprefs = biasgen.getChip().getPrefs();

		loadPreference();
		sprefs.addPreferenceChangeListener(this);
	}

	@Override
	public int get() {
		return value;
	}

	@Override
	public void set(final int value) {
		if ((value < 0) || (value >= (1 << getNumBits()))) {
			throw new IllegalArgumentException("Attempted to store value=" + value
				+ ", which is larger than the maximum permitted value of " + (1 << getNumBits()) + " or negative, in " + this);
		}

		if (this.value != value) {
			setChanged();
		}

		this.value = value;

		notifyObservers();
	}

	@Override
	public String toString() {
		return String.format("SPIConfigInt {configName=%s, prefKey=%s, moduleAddr=%d, paramAddr=%d, numBits=%d, default=%d}", getName(),
			getPreferencesKey(), getModuleAddr(), getParamAddr(), getNumBits(), defaultValue);
	}

	@Override
	public void preferenceChange(final PreferenceChangeEvent e) {
		if (e.getKey().equals(getPreferencesKey())) {
			final int newVal = Integer.parseInt(e.getNewValue());
			set(newVal);
		}
	}

	@Override
	public String getPreferencesKey() {
		return biasgen.getChip().getClass().getSimpleName() + "." + getName();
	}

	@Override
	public void loadPreference() {
		set(sprefs.getInt(getPreferencesKey(), defaultValue));
	}

	@Override
	public void storePreference() {
		sprefs.putInt(getPreferencesKey(), get());
	}

	private static final int TF_MAX_HEIGHT = 16;
	private static final int TF_PREF_HEIGHT = 8;
	private static final int TF_MAX_WIDTH = 80;
	private static final int TF_PREF_WIDTH = 40;

	public static void makeSPIIntConfig(final SPIConfigInt intVal, final JPanel panel, final Map<SPIConfigValue, JComponent> configValueMap,
		final Biasgen biasgen) {
		final JPanel pan = new JPanel();
		pan.setAlignmentX(Component.LEFT_ALIGNMENT);
		pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));

		final JLabel label = new JLabel(intVal.getName());
		label.setToolTipText("<html>" + intVal.toString() + "<br>" + intVal.getDescription()
			+ "<br>Enter value or use mouse wheel or arrow keys to change value.");
		pan.add(label);

		final JTextField tf = new JTextField();
		tf.setText(Integer.toString(intVal.get()));
		tf.setPreferredSize(new Dimension(SPIConfigInt.TF_PREF_WIDTH, SPIConfigInt.TF_PREF_HEIGHT));
		tf.setMaximumSize(new Dimension(SPIConfigInt.TF_MAX_WIDTH, SPIConfigInt.TF_MAX_HEIGHT));
		tf.addActionListener(new SPIConfigIntAction(intVal));
		tf.addKeyListener(new SPIConfigIntKeyAction(intVal));
		tf.addMouseWheelListener(new SPIConfigIntMouseWheelAction(intVal));
		pan.add(tf);

		panel.add(pan);
		configValueMap.put(intVal, tf);
		intVal.addObserver(biasgen);
	}

	private static class SPIConfigIntAction implements ActionListener {

		private final SPIConfigInt intConfig;

		SPIConfigIntAction(final SPIConfigInt intCfg) {
			intConfig = intCfg;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final JTextField tf = (JTextField) e.getSource();

			try {
				intConfig.set(Integer.parseInt(tf.getText())); // TODO add undo
				intConfig.setFileModified();

				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				tf.selectAll();
				tf.setBackground(Color.red);

				Logger.getLogger("SPIConfigIntAction").warning(ex.toString());
			}
		}
	}

	private static class SPIConfigIntKeyAction extends KeyAdapter {

		private final SPIConfigInt intConfig;

		SPIConfigIntKeyAction(final SPIConfigInt intCfg) {
			intConfig = intCfg;
		}

		@Override
		public void keyPressed(final KeyEvent e) {
			final boolean up = (e.getKeyCode() == KeyEvent.VK_UP);
			final boolean down = (e.getKeyCode() == KeyEvent.VK_DOWN);

			if (!up && !down) {
				return;
			}

			final int inc = up ? 1 : -1;

			int val = intConfig.get() + inc;

			if (val >= 0) {
				intConfig.set(val);
				intConfig.setFileModified();
			}
		}
	}

	private static class SPIConfigIntMouseWheelAction extends MouseAdapter {

		private final SPIConfigInt intConfig;

		SPIConfigIntMouseWheelAction(final SPIConfigInt intCfg) {
			intConfig = intCfg;
		}

		@Override
		public void mouseWheelMoved(final MouseWheelEvent evt) {
			final int clicks = evt.getWheelRotation();

			if (clicks == 0) {
				return;
			}

			final int inc = -clicks;

			int val = intConfig.get() + inc;

			if (val >= 0) {
				intConfig.set(val);
				intConfig.setFileModified();
			}
		}
	}
}
