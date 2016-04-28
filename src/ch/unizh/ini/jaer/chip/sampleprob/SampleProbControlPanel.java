package ch.unizh.ini.jaer.chip.sampleprob;

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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3.SPIConfigSequence;

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

		SPIConfigBit.makeSPIBitConfig(biasgen.biasForceEnable, onchipBiasgenPanel, configValueMap, getBiasgen());

		biasgen.setPotArray(biasgen.ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));

		onchipBiasgenPanel.add(Box.createVerticalGlue()); // push up to prevent expansion of PotPanel

		SPIConfigBit.makeSPIBitConfig(biasgen.dacRun, offchipDACPanel, configValueMap, getBiasgen());
		SPIConfigBit.makeSPIBitConfig(biasgen.dacRandomRun, offchipDACPanel, configValueMap, getBiasgen());
		SPIConfigBit.makeSPIBitConfig(biasgen.dacRandomUSBRun, offchipDACPanel, configValueMap, getBiasgen());

		biasgen.setPotArray(biasgen.vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		for (final SPIConfigValue cfgVal : biasgen.aerControl) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, aerPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, aerPanel, configValueMap, getBiasgen());
			}
		}

		for (final SPIConfigValue cfgVal : biasgen.chipControl) {
			if (cfgVal instanceof SPIConfigBit) {
				SPIConfigBit.makeSPIBitConfig((SPIConfigBit) cfgVal, chipDiagPanel, configValueMap, getBiasgen());
			}
			else if (cfgVal instanceof SPIConfigInt) {
				SPIConfigInt.makeSPIIntConfig((SPIConfigInt) cfgVal, chipDiagPanel, configValueMap, getBiasgen());
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

	/**
	 * @return the biasgen
	 */
	public SampleProb.Biasgen getBiasgen() {
		return biasgen;
	}

	/**
	 * Handles updates to GUI controls from any source, including preference
	 * changes
	 */
	@Override
	public synchronized void update(final Observable observable, final Object object) {
		try {
			if (observable instanceof SPIConfigBit) {
				final SPIConfigBit cfgBit = (SPIConfigBit) observable;

				// Ensure GUI is up-to-date.
				if (configValueMap.containsKey(cfgBit)) {
					((JRadioButton) configValueMap.get(cfgBit)).setSelected(cfgBit.isSet());
				}
			}
			else if (observable instanceof SPIConfigInt) {
				final SPIConfigInt cfgInt = (SPIConfigInt) observable;

				// Ensure GUI is up-to-date.
				if (configValueMap.containsKey(cfgInt)) {
					((JTextField) configValueMap.get(cfgInt)).setText(Integer.toString(cfgInt.get()));
				}
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

				final SPIConfigSequence configSequence = fx3HwIntf.new SPIConfigSequence();

				// Ensure BlockRAM memory is cleared (toggle CLEARALL command).
				configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 132, (short) 1);
				configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 132, (short) 0);

				while ((valuesLine = r.readLine()) != null) {
					final String[] valuesText = valuesLine.split(",");

					// Set channel.
					configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 128, (short) channel);

					int address = 0;
					for (final String valueText : valuesText) {
						// Set increasing address.
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 129, (short) address);

						// Set data to write to memory.
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 130, (short) (Integer.parseInt(valueText) & 0x3F));

						// Toggle SET command.
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 131, (short) 1);
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 131, (short) 0);

						address++;
					}

					channel++;
				}

				// Commit configuration.
				configSequence.sendConfigSequence();
			}
			catch (IOException | NumberFormatException | HardwareInterfaceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Done with uploading data.
			JOptionPane.showMessageDialog(null, "Done with uploading data.");
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
