package ch.unizh.ini.jaer.config.spi;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ch.unizh.ini.jaer.config.ConfigInt;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseWheelListener;
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

	// <editor-fold defaultstate="collapsed" desc="ConfigInt interface implementation">
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
			this.value = value;
			setChanged();
			notifyObservers();
		}
	}
	// </editor-fold>

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
	public void loadPreference() {
		set(sprefs.getInt(getPreferencesKey(), defaultValue));
	}

	@Override
	public void storePreference() {
		sprefs.putInt(getPreferencesKey(), get());
	}

	// <editor-fold defaultstate="collapsed" desc="GUI related functions">
	private static final int TF_PREF_HEIGHT = 8;
	private static final int TF_PREF_WIDTH = 40;
	private static final int TF_MAX_HEIGHT = 16;
	private static final int TF_MAX_WIDTH = 80;

	private static final Dimension prefDimensions = new Dimension(SPIConfigInt.TF_PREF_WIDTH, SPIConfigInt.TF_PREF_HEIGHT);
	private static final Dimension maxDimensions = new Dimension(SPIConfigInt.TF_MAX_WIDTH, SPIConfigInt.TF_MAX_HEIGHT);

	@Override
	public JComponent makeGUIControl() {

		final JPanel pan = new JPanel();
		pan.setAlignmentX(Component.LEFT_ALIGNMENT);
		pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));

		final JLabel label = new JLabel(getName());
		label.setToolTipText(
			"<html>" + toString() + "<br>" + getDescription() + "<br>Enter value or use mouse wheel or arrow keys to change value.");
		pan.add(label);

		final JTextField tf = new JTextField();
		tf.setText(Integer.toString(get()));
		tf.setPreferredSize(prefDimensions);
		tf.setMaximumSize(maxDimensions);
		SPIConfigIntActions actionListeners = new SPIConfigIntActions(this);
		tf.addActionListener(actionListeners);
		tf.addFocusListener(actionListeners);
		tf.addKeyListener(actionListeners);
		tf.addMouseWheelListener(actionListeners);
		pan.add(tf);
		setControl(tf);
		addObserver(biasgen);	// This observer is responsible for sending data to hardware
		addObserver(this);		// This observer is responsible for GUI update. It calls the updateControl() method
		return pan;
	}

	@Override
	public void updateControl() {
		if (control != null) {
			((JTextField) control).setText(Integer.toString(value));
		}
	}

	private static class SPIConfigIntActions extends KeyAdapter implements ActionListener, FocusListener, MouseWheelListener {

		private final SPIConfigInt intConfig;

		SPIConfigIntActions(final SPIConfigInt intCfg) {
			intConfig = intCfg;
		}

		private void setValueAndUpdateGUI(int val) {
			JTextField tf = (JTextField) intConfig.control;
			try {
				intConfig.set(val);
				intConfig.setFileModified();
				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				tf.selectAll();
				tf.setBackground(Color.red);
				log.warning(ex.toString());
			}
		}

		@Override
		public void keyPressed(final KeyEvent e) {
			final boolean up = (e.getKeyCode() == KeyEvent.VK_UP);
			final boolean down = (e.getKeyCode() == KeyEvent.VK_DOWN);

			if (up || down) {
				setValueAndUpdateGUI(intConfig.get() + (up ? 1 : -1));
			}
		}

		// ActionListener interface
		@Override
		public void actionPerformed(final ActionEvent e) {
			setValueAndUpdateGUI(Integer.parseInt(((JTextField) intConfig.control).getText()));
		}

		// FocusListener interface
		@Override
		public void focusGained(final FocusEvent e) {}

		@Override
		public void focusLost(final FocusEvent e) {
			setValueAndUpdateGUI(Integer.parseInt(((JTextField) intConfig.control).getText()));
		}

		// MouseWheelListener interface
		@Override
		public void mouseWheelMoved(final MouseWheelEvent evt) {
			final int clicks = evt.getWheelRotation();

			if (clicks != 0) {
				setValueAndUpdateGUI(intConfig.get() - clicks);
			}
		}
	}
	// </editor-fold>
}
