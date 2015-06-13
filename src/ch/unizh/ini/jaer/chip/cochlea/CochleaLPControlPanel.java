package ch.unizh.ini.jaer.chip.cochlea;

import java.awt.BorderLayout;
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
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.Biasgen;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.CochleaChannel;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigInt;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigValue;

public final class CochleaLPControlPanel extends JPanel implements Observer {
	private static final long serialVersionUID = -7435419921722582550L;

	private final Logger log = Logger.getLogger("CochleaLPControlPanel");

	private final CochleaLP chip;
	private final CochleaLP.Biasgen biasgen;

	private final Map<SPIConfigValue, JComponent> configValueMap = new HashMap<>();

	public CochleaLPControlPanel(final CochleaLP chip) {
		this.chip = chip;
		biasgen = (Biasgen) chip.getBiasgen();

		initComponents();

		makeSPIBitConfig(biasgen.biasForceEnable, onchipBiasgenPanel);

		biasgen.setPotArray(biasgen.ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[0]));
		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(biasgen.ssBiases[1]));

		makeSPIBitConfig(biasgen.dacRun, offchipDACPanel);

		biasgen.setPotArray(biasgen.vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		for (final SPIConfigValue cfgVal : biasgen.scannerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				makeSPIBitConfig((SPIConfigBit) cfgVal, scannerPanel);
			}
			else if (cfgVal instanceof SPIConfigInt) {
				makeSPIIntConfig((SPIConfigInt) cfgVal, scannerPanel);
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.aerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				makeSPIBitConfig((SPIConfigBit) cfgVal, aerPanel);
			}
			else if (cfgVal instanceof SPIConfigInt) {
				makeSPIIntConfig((SPIConfigInt) cfgVal, aerPanel);
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.chipDiagChain) {
			if (cfgVal instanceof SPIConfigBit) {
				makeSPIBitConfig((SPIConfigBit) cfgVal, chipDiagPanel);
			}
			else if (cfgVal instanceof SPIConfigInt) {
				makeSPIIntConfig((SPIConfigInt) cfgVal, chipDiagPanel);
			}
		}

		// Add cochlea channel configuration GUI.
		for (final CochleaChannel chan : biasgen.cochleaChannels) {
			final JPanel pan = new JPanel();
			pan.setAlignmentX(Component.LEFT_ALIGNMENT);
			pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));

			final JLabel label = new JLabel(chan.getName());
			label.setToolTipText("<html>" + chan.toString() + "<br>" + chan.getDescription()
				+ "<br>Enter value or use mouse wheel or arrow keys to change value.");
			pan.add(label);

			final JRadioButton but = new JRadioButton();
			but.setToolTipText("Comparator self-oscillation enable.");
			but.setSelected(chan.isComparatorSelfOscillationEnable());
			but.setAlignmentX(Component.LEFT_ALIGNMENT);
			but.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					final JRadioButton button = (JRadioButton) e.getSource();
					chan.setComparatorSelfOscillationEnable(button.isSelected());
					setFileModified();
				}
			});
			pan.add(but);

			final JTextField tf0 = new JTextField();
			tf0.setToolTipText(chan.getName() + " - Delay cap configuration in ADM.");
			tf0.setText(Integer.toString(chan.getDelayCapConfigADM()));
			tf0.setMinimumSize(new Dimension(200, 20));
			tf0.setPreferredSize(new Dimension(200, 20));
			tf0.setMaximumSize(new Dimension(200, 30));
			tf0.addActionListener(new CochleaChannelIntAction(chan, 0));
			pan.add(tf0);

			final JTextField tf1 = new JTextField();
			tf1.setToolTipText(chan.getName() + " - Reset cap configuration in ADM.");
			tf1.setText(Integer.toString(chan.getResetCapConfigADM()));
			tf1.setMinimumSize(new Dimension(200, 20));
			tf1.setPreferredSize(new Dimension(200, 20));
			tf1.setMaximumSize(new Dimension(200, 30));
			tf1.addActionListener(new CochleaChannelIntAction(chan, 1));
			pan.add(tf1);

			final JTextField tf2 = new JTextField();
			tf2.setToolTipText(chan.getName() + " - LNA gain configuration.");
			tf2.setText(Integer.toString(chan.getLnaGainConfig()));
			tf2.setMinimumSize(new Dimension(200, 20));
			tf2.setPreferredSize(new Dimension(200, 20));
			tf2.setMaximumSize(new Dimension(200, 30));
			tf2.addActionListener(new CochleaChannelIntAction(chan, 2));
			pan.add(tf2);

			final JTextField tf3 = new JTextField();
			tf3.setToolTipText(chan.getName() + " - Attenuator configuration.");
			tf3.setText(Integer.toString(chan.getAttenuatorConfig()));
			tf3.setMinimumSize(new Dimension(200, 20));
			tf3.setPreferredSize(new Dimension(200, 20));
			tf3.setMaximumSize(new Dimension(200, 30));
			tf3.addActionListener(new CochleaChannelIntAction(chan, 3));
			pan.add(tf3);

			final JTextField tf4 = new JTextField();
			tf4.setToolTipText(chan.getName() + " - QTuning configuration.");
			tf4.setText(Integer.toString(chan.getqTuning()));
			tf4.setMinimumSize(new Dimension(200, 20));
			tf4.setPreferredSize(new Dimension(200, 20));
			tf4.setMaximumSize(new Dimension(200, 30));
			tf4.addActionListener(new CochleaChannelIntAction(chan, 4));
			pan.add(tf4);

			channelPanel.add(pan);
			chan.addObserver(this);
		}
	}

	private void makeSPIBitConfig(final SPIConfigBit bitVal, final JPanel panel) {
		final JRadioButton but = new JRadioButton(bitVal.getName() + ": " + bitVal.getDescription());
		but.setToolTipText("<html>" + bitVal.toString() + "<br>Select to set bit, clear to clear bit.");
		but.setSelected(bitVal.isSet());
		but.setAlignmentX(Component.LEFT_ALIGNMENT);
		but.addActionListener(new SPIConfigBitAction(bitVal));

		panel.add(but);
		configValueMap.put(bitVal, but);
		bitVal.addObserver(this);
	}

	private void makeSPIIntConfig(final SPIConfigInt intVal, final JPanel panel) {
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
		tf.addActionListener(new SPIConfigIntAction(intVal));
		pan.add(tf);

		panel.add(pan);
		configValueMap.put(intVal, tf);
		intVal.addObserver(this);
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
			else if (observable instanceof CochleaChannel) {
				// TODO: ignore for now.
			}
			else {
				log.warning("unknown observable " + observable + " , not sending anything");
			}
		}
		catch (final Exception e) {
			log.warning(e.toString());
		}
	}

	private class SPIConfigBitAction implements ActionListener {

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

	private class SPIConfigIntAction implements ActionListener {

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

	private class CochleaChannelIntAction implements ActionListener {

		private final CochleaChannel channel;
		private final int componentID;

		CochleaChannelIntAction(final CochleaChannel chan, final int component) {
			channel = chan;
			componentID = component;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final JTextField tf = (JTextField) e.getSource();

			try {
				switch (componentID) {
					case 0:
						channel.setDelayCapConfigADM(Integer.parseInt(tf.getText()));
						break;

					case 1:
						channel.setResetCapConfigADM(Integer.parseInt(tf.getText()));
						break;

					case 2:
						channel.setLnaGainConfig(Integer.parseInt(tf.getText()));
						break;

					case 3:
						channel.setAttenuatorConfig(Integer.parseInt(tf.getText()));
						break;

					case 4:
						channel.setqTuning(Integer.parseInt(tf.getText()));
						break;

					default:
						log.warning("Unknown component ID for CochleaChannel GUI.");
						return;
				}

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
		final JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.setToolTipText("Select a tab to configure an aspect of the device.");

		onchipBiasgenPanel = new JPanel();
		onchipBiasgenPanel.setLayout(new BoxLayout(onchipBiasgenPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("On-chip biases (biasgen)", onchipBiasgenPanel);

		offchipDACPanel = new JPanel();
		offchipDACPanel.setLayout(new BoxLayout(offchipDACPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("Off-chip biases (DAC)", offchipDACPanel);

		channelPanel = new JPanel();
		channelPanel.setLayout(new BoxLayout(channelPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("Channels", new JScrollPane(channelPanel));

		scannerPanel = new JPanel();
		scannerPanel.setLayout(new BoxLayout(scannerPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("Scanner Config", scannerPanel);

		aerPanel = new JPanel();
		aerPanel.setLayout(new BoxLayout(aerPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("AER Config", aerPanel);

		chipDiagPanel = new JPanel();
		chipDiagPanel.setLayout(new BoxLayout(chipDiagPanel, BoxLayout.Y_AXIS));
		tabbedPane.addTab("Chip Diag Config", chipDiagPanel);

		revalidate();

		add(tabbedPane, BorderLayout.CENTER);
	}

	private JPanel onchipBiasgenPanel;
	private JPanel offchipDACPanel;
	private JPanel channelPanel;
	private JPanel scannerPanel;
	private JPanel aerPanel;
	private JPanel chipDiagPanel;
}
