package ch.unizh.ini.jaer.chip.sampleprob;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigInt;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigValue;

public final class SampleProbControlPanel extends JTabbedPane implements Observer {

	private static final long serialVersionUID = -7435419921722582550L;

	private final Logger log = Logger.getLogger("SampleProbControlPanel");

	private final SampleProb chip;
	private final SampleProb.Biasgen biasgen;

	private final Map<SPIConfigValue, JComponent> configValueMap = new HashMap<>();

	public SampleProbControlPanel(final SampleProb chip) {
		this.chip = chip;
		biasgen = (SampleProb.Biasgen) chip.getBiasgen();

		initComponents();

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent evt) {
				tabbedPaneMouseClicked(evt);
			}
		});

		makeSPIBitConfig(biasgen.biasForceEnable, onchipBiasgenPanel);

		biasgen.setPotArray(biasgen.ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

		onchipBiasgenPanel.add(Box.createVerticalGlue()); // push up to prevent expansion of PotPanel

		makeSPIBitConfig(biasgen.dacRun, offchipDACPanel);

		biasgen.setPotArray(biasgen.vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		for (final SPIConfigValue cfgVal : biasgen.aerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				makeSPIBitConfig((SPIConfigBit) cfgVal, aerPanel);
			}
			else if (cfgVal instanceof SPIConfigInt) {
				makeSPIIntConfig((SPIConfigInt) cfgVal, aerPanel);
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.chipControl) {
			if (cfgVal instanceof SPIConfigBit) {
				makeSPIBitConfig((SPIConfigBit) cfgVal, chipDiagPanel);
			}
			else if (cfgVal instanceof SPIConfigInt) {
				makeSPIIntConfig((SPIConfigInt) cfgVal, chipDiagPanel);
			}
		}

		inputDataLoadButton();

		setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
		setPreferredSize(new Dimension(1024, 768));
		revalidate();

		setSelectedIndex(chip.getPrefs().getInt("SampleProbControlPanel.bgTabbedPaneSelectedIndex", 0));
	}

	private void inputDataLoadButton() {
		final JButton loadButton = new JButton("Load input data file");
		loadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				String inputDataFile = chip.getPrefs().get("SampleProbControlPanel.inputDataFile", "");

				final JFileChooser c = new JFileChooser(inputDataFile);
				final FileFilter filt = new FileNameExtensionFilter("Text File", "txt");
				c.addChoosableFileFilter(filt);
				c.setSelectedFile(new File(inputDataFile));

				final int ret = c.showOpenDialog(null);
				if (ret != JFileChooser.APPROVE_OPTION) {
					return;
				}

				inputDataFile = c.getSelectedFile().toString();
				chip.getPrefs().put("SampleProbControlPanel.inputDataFile", inputDataFile);

				// Parse file and send commands to device.
				inputDataLoad(inputDataFile);
			}
		});

		chipDiagPanel.add(loadButton);
	}

	private static final int TF_MAX_HEIGHT = 15;
	private static final int TF_HEIGHT = 6;
	private static final int TF_PREF_W = 20, TF_MAX_W = 40;

	private void makeSPIBitConfig(final SPIConfigBit bitVal, final JPanel panel) {
		final JRadioButton but = new JRadioButton("<html>" + bitVal.getName() + ": " + bitVal.getDescription());
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
		tf.setPreferredSize(new Dimension(SampleProbControlPanel.TF_PREF_W, SampleProbControlPanel.TF_HEIGHT));
		tf.setMaximumSize(new Dimension(SampleProbControlPanel.TF_MAX_W, SampleProbControlPanel.TF_MAX_HEIGHT));
		tf.addActionListener(new SPIConfigIntAction(intVal));
		pan.add(tf);

		panel.add(pan);
		configValueMap.put(intVal, tf);
		intVal.addObserver(this);
	}

	/**
	 * @return the biasgen
	 */
	public SampleProb.Biasgen getBiasgen() {
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

	private void inputDataLoad(final String inputDataFile) {
		if (chip.getHardwareInterface() != null) {
			final CypressFX3 fx3HwIntf = (CypressFX3) chip.getHardwareInterface();

			try (BufferedReader r = new BufferedReader(new FileReader(inputDataFile))) {
				int channel = 0;
				String valuesLine;

				// Ensure BlockRAM memory is cleared (toggle CLEARALL command).
				fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 132, (short) 1);
				fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 132, (short) 0);

				while ((valuesLine = r.readLine()) != null) {
					final String[] valuesText = valuesLine.split(",");

					// Set channel.
					fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 128, (short) channel);

					int address = 0;
					for (final String valueText : valuesText) {
						// Set increasing address.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 129, (short) address);

						// Set data to write to memory.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 130, (short) (Integer.parseInt(valueText) & 0x3F));

						// Toggle SET command.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 131, (short) 1);
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 131, (short) 0);

						address++;
					}

					channel++;
				}
			}
			catch (IOException | NumberFormatException | HardwareInterfaceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Done with uploading data.
			JOptionPane.showMessageDialog(null, "Done with uploading data.");
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

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	private void initComponents() {
		setToolTipText("Select a tab to configure an aspect of the device.");

		onchipBiasgenPanel = new JPanel();
		onchipBiasgenPanel.setLayout(new BoxLayout(onchipBiasgenPanel, BoxLayout.Y_AXIS));
		onchipBiasgenPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		addTab("On-chip biases (biasgen)", (onchipBiasgenPanel));

		offchipDACPanel = new JPanel();
		offchipDACPanel.setLayout(new BoxLayout(offchipDACPanel, BoxLayout.Y_AXIS));
		addTab("Off-chip biases (DAC)", (offchipDACPanel));

		aerPanel = new JPanel();
		aerPanel.setLayout(new BoxLayout(aerPanel, BoxLayout.Y_AXIS));
		addTab("AER Config", (aerPanel));

		chipDiagPanel = new JPanel();
		chipDiagPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chipDiagPanel.setLayout(new BoxLayout(chipDiagPanel, BoxLayout.Y_AXIS));
		addTab("Chip Diag Config", (chipDiagPanel));

	}

	protected void tabbedPaneMouseClicked(final MouseEvent evt) {
		chip.getPrefs().putInt("SampleProbControlPanel.bgTabbedPaneSelectedIndex", getSelectedIndex());
	}

	private JPanel onchipBiasgenPanel;
	private JPanel offchipDACPanel;
	private JPanel aerPanel;
	private JPanel chipDiagPanel;
}
