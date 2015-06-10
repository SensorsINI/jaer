package ch.unizh.ini.jaer.chip.cochlea;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.Biasgen;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigInt;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigValue;

public final class CochleaLPControlPanel extends javax.swing.JPanel implements Observer {
	private static final long serialVersionUID = -7435419921722582550L;

	private final Logger log = Logger.getLogger("CochleaLPControlPanel");

	private final CochleaLP chip;
	private final CochleaLP.Biasgen biasgen;

	private final Map<SPIConfigValue, JComponent> configValueMap = new HashMap<>();

	public CochleaLPControlPanel(final CochleaLP chip) {
		this.chip = chip;
		biasgen = (Biasgen) chip.getBiasgen();

		initComponents();

		biasgen.setPotArray(biasgen.ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[0]));
		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[1]));

		biasgen.setPotArray(biasgen.vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		for (final SPIConfigValue cfgVal : biasgen.spiConfigValues) {
			if (cfgVal instanceof SPIConfigBit) {
				final SPIConfigBit bitVal = (SPIConfigBit) cfgVal;

				final JRadioButton but = new JRadioButton(bitVal.getName() + ": " + bitVal.getDescription());
				but.setAlignmentX(Component.LEFT_ALIGNMENT);
				but.setToolTipText("<html>" + bitVal.toString() + "<br>Select to set bit, clear to clear bit.");
				but.setSelected(bitVal.isSet());

				configPanel.add(but);
				configValueMap.put(bitVal, but);

				but.addActionListener(new SPIConfigBitAction(bitVal));
				bitVal.addObserver(this);

			}
			else if (cfgVal instanceof SPIConfigInt) {
				final SPIConfigInt intVal = (SPIConfigInt) cfgVal;

				final JPanel pan = new JPanel();
				pan.setAlignmentX(Component.LEFT_ALIGNMENT);
				pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));

				final JLabel label = new JLabel(intVal.getName());
				label.setToolTipText("<html>" + intVal.toString() + "<br>" + intVal.getDescription()
					+ "<br>Enter value or use mouse wheel or arrow keys to change value.");
				pan.add(label);

				final JTextField tf = new JTextField();
				tf.setText(Integer.toString(intVal.get()));
				tf.setPreferredSize(new Dimension(200, 20));
				tf.setMaximumSize(new Dimension(200, 30));
				pan.add(tf);

				configPanel.add(pan);
				configValueMap.put(intVal, tf);

				tf.addActionListener(new SPIConfigIntAction(intVal));
				intVal.addObserver(this);
			}
		}
	}

	/**
	 * @return the biasgen
	 */
	public CochleaLP.Biasgen getBiasgen() {
		return biasgen;
	}

	private void setFileModified() {
		if ((chip != null) && (chip.getAeViewer() != null) && (chip.getAeViewer().getBiasgenFrame() != null)) {
			chip.getAeViewer().getBiasgenFrame().setFileModified(true);
		}
	}

	/**
	 * Handles updates to GUI controls from any source, including preference
	 * changes
	 */
	@Override
	public void update(final Observable observable, final Object object) {
		try {
			if (observable instanceof SPIConfigBit) {
				final SPIConfigBit bitVal = (SPIConfigBit) observable;

				final JRadioButton but = (JRadioButton) configValueMap.get(bitVal);

				but.setSelected(bitVal.isSet());
			}
			else if (observable instanceof SPIConfigInt) {
				final SPIConfigInt intVal = (SPIConfigInt) observable;

				final JTextField tf = (JTextField) configValueMap.get(intVal);

				tf.setText(Integer.toString(intVal.get()));
			}
			else {
				log.warning("unknown observable " + observable + " , not sending anything");
			}
		}
		catch (final Exception e) {
			log.warning(e.toString());
		}
	}

	class SPIConfigBitAction implements ActionListener {

		private final SPIConfigBit bitConfig;

		SPIConfigBitAction(final SPIConfigBit bitCfg) {
			bitConfig = bitCfg;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final JRadioButton button = (JRadioButton) e.getSource();
			bitConfig.set(button.isSelected());
			setFileModified();
		}
	}

	class SPIConfigIntAction implements ActionListener {

		private final SPIConfigInt intConfig;

		SPIConfigIntAction(final SPIConfigInt intCfg) {
			intConfig = intCfg;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final JTextField tf = (JTextField) e.getSource();

			try {

				intConfig.set(Integer.parseInt(tf.getText()));
				setFileModified();

				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				tf.selectAll();
				tf.setBackground(Color.red);

				log.warning(ex.toString());
			}
		}
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	private void initComponents() {
		tabbedPane = new javax.swing.JTabbedPane();
		tabbedPane.setToolTipText("Select a tab to configure an aspect of the device.");

		onchipBiasgenPanel = new javax.swing.JPanel();
		onchipBiasgenPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("On-chip biases (biasgen)"));
		onchipBiasgenPanel.setToolTipText("Set on-chip bias values (through internal biasgen).");
		onchipBiasgenPanel.setLayout(new BoxLayout(onchipBiasgenPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("On-chip biases (biasgen)", onchipBiasgenPanel);

		offchipDACPanel = new javax.swing.JPanel();
		offchipDACPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Off-chip biases (DAC)"));
		offchipDACPanel.setToolTipText("Set off-chip DAC voltage values.");
		offchipDACPanel.setLayout(new java.awt.BorderLayout());
		tabbedPane.addTab("Off-chip biases (DAC)", offchipDACPanel);

		configPanel = new javax.swing.JPanel();
		configPanel.setLayout(new javax.swing.BoxLayout(configPanel, javax.swing.BoxLayout.Y_AXIS));
		tabbedPane.addTab("Main Configuration", configPanel);

		add(tabbedPane, java.awt.BorderLayout.CENTER);
	}

	private javax.swing.JPanel configPanel;
	private javax.swing.JPanel offchipDACPanel;
	private javax.swing.JPanel onchipBiasgenPanel;
	private javax.swing.JTabbedPane tabbedPane;
}
