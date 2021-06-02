package ch.unizh.ini.jaer.chip.cochlea;

import static java.awt.Component.TOP_ALIGNMENT;
import static java.awt.Component.LEFT_ALIGNMENT;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import ch.unizh.ini.jaer.chip.cochlea.gui.CochleaTow4EarChannelCP;
import ch.unizh.ini.jaer.config.AbstractChipControlPanel;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import java.util.List;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.biasgen.BiasgenPanel;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;

/**
 * Control panel for CochleaTow4Ear
 *
 * @author Luca Longinotti, Minhao Liu, Shih-Chii Liu, Tobi Delbruck
 */
public final class CochleaTow4EarControlPanel extends AbstractChipControlPanel {

	private static final long serialVersionUID = -7435419921722582550L;
	private static final int CHAN_PER_COL = 32;

	public CochleaTow4EarControlPanel(final CochleaTow4Ear chip) {
		super((Chip) chip);

		// Biasgen panel
		JPanel onchipBiasgenPanel = new JPanel();
		onchipBiasgenPanel.setLayout(new BoxLayout(onchipBiasgenPanel, BoxLayout.Y_AXIS));
		onchipBiasgenPanel.setAlignmentX(LEFT_ALIGNMENT);
		addTab("On-chip biases (biasgen)", (onchipBiasgenPanel));

		onchipBiasgenPanel.add(getBiasgen().biasForceEnable.makeGUIControl());
		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(getBiasgen().ssBiases[0]));
		onchipBiasgenPanel.add(new ShiftedSourceControlsCF(getBiasgen().ssBiases[1]));

		biasgen.setPotArray(getBiasgen().ipots);
		onchipBiasgenPanel.add(new BiasgenPanel(getBiasgen()));
		onchipBiasgenPanel.add(getBiasgen().SelResSW.makeGUIControl());
		onchipBiasgenPanel.add(Box.createVerticalGlue()); // push up to prevent expansion of PotPanel

		// DAC
		JPanel offchipDACPanel = new JPanel();
		offchipDACPanel.setLayout(new BoxLayout(offchipDACPanel, BoxLayout.Y_AXIS));
		addTab("Off-chip biases (DAC)", (offchipDACPanel));

		offchipDACPanel.add(getBiasgen().dacRun.makeGUIControl());
		biasgen.setPotArray(getBiasgen().vpots);
		offchipDACPanel.add(new BiasgenPanel(getBiasgen()));

		// Channels L/R
		JPanel channelPanelLR = buildCochleaChannelsLRTab("Left", getBiasgen().cochleaChannels[0], "Right", getBiasgen().cochleaChannels[1]);
		addTab("Channels L/R", (channelPanelLR));

		// Channels T/B
		JPanel channelPanelTB = buildCochleaChannelsLRTab("Top", getBiasgen().cochleaChannels[2], "Bottom", getBiasgen().cochleaChannels[3]);
		addTab("Channels T/B", (channelPanelTB));

		// Scanner
		JPanel scannerPanel = new JPanel();
		scannerPanel.setLayout(new BoxLayout(scannerPanel, BoxLayout.Y_AXIS));
		addTab("Scanner Config", (scannerPanel));
		SPIConfigValue.addGUIControls(scannerPanel, getBiasgen().scannerControl);

		// AER
		JPanel aerPanel = new JPanel();
		aerPanel.setLayout(new BoxLayout(aerPanel, BoxLayout.Y_AXIS));
		addTab("AER Config", (aerPanel));
		SPIConfigValue.addGUIControls(aerPanel, getBiasgen().aerControl);

		// ADC
		JPanel adcPanel = new JPanel();
		adcPanel.setLayout(new BoxLayout(adcPanel, BoxLayout.Y_AXIS));
		addTab("ADC", (adcPanel));
		SPIConfigValue.addGUIControls(adcPanel, getBiasgen().adcControl);
		
		// Chip Config
		JPanel chipControlPanel = new JPanel();
		chipControlPanel.setAlignmentX(LEFT_ALIGNMENT);
		chipControlPanel.setLayout(new BoxLayout(chipControlPanel, BoxLayout.Y_AXIS));
		addTab("Chip Config", (chipControlPanel));
		SPIConfigValue.addGUIControls(chipControlPanel, getBiasgen().chipControl);

		setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
		setPreferredSize(new Dimension(800, 600));
		revalidate();

		setSelectedIndex(chip.getPrefs().getInt("CochleaTow4EarControlPanel.bgTabbedPaneSelectedIndex", 0));
	}

	private JPanel buildCochleaChannelsLRTab(String lTitle, List<CochleaChannelConfig> lChannels, String rTitle, List<CochleaChannelConfig> rChannels) {
		JPanel channelPanel = new JPanel();
		channelPanel.setLayout(new BoxLayout(channelPanel, BoxLayout.X_AXIS));
		channelPanel.add(buildCochleaChannelsGroup(lTitle, lChannels));
		channelPanel.add(buildCochleaChannelsGroup(rTitle, rChannels));
		channelPanel.setMinimumSize(channelPanel.getLayout().minimumLayoutSize(channelPanel));
		return channelPanel;
	}

	private JPanel buildCochleaChannelsGroup(String title, List<CochleaChannelConfig> channels) {
		
		// A panel for a channel group with common global control
		JPanel channelGroupPanel = new JPanel();
		channelGroupPanel.setLayout(new BoxLayout(channelGroupPanel, BoxLayout.Y_AXIS));
		channelGroupPanel.setAlignmentY(TOP_ALIGNMENT); // puts panel at top
		channelGroupPanel.setBorder(new TitledBorder(title));

		// Build cochlea channels configuration GUI.
		// Global control for all channels
		CochleaTow4EarChannelGroupConfig globalChanControl = new CochleaTow4EarChannelGroupConfig("Global", 
			"Global control for all " + title + " cochlea channels", channels, (AEChip)chip);
		final CochleaTow4EarChannelCP gPan = new CochleaTow4EarChannelCP((CochleaTow4EarChannelConfig)globalChanControl);
		gPan.setAlignmentX(LEFT_ALIGNMENT);
		gPan.setAlignmentY(TOP_ALIGNMENT);
		channelGroupPanel.add(gPan);
		
		// A panel for a channel group
		JPanel channelsPanel = new JPanel();
		channelsPanel.setLayout(new BoxLayout(channelsPanel, BoxLayout.X_AXIS));
		channelsPanel.setAlignmentX(LEFT_ALIGNMENT);	// Should have the same alignment as the gPan
		channelsPanel.setAlignmentY(TOP_ALIGNMENT);
		channelGroupPanel.add(channelsPanel);

		// A panel for a column of channels
		JPanel colPan = new JPanel();
		colPan.setLayout(new BoxLayout(colPan, BoxLayout.Y_AXIS));
		colPan.setAlignmentY(TOP_ALIGNMENT);
		channelsPanel.add(colPan);

		int chanCount = 0;

		for (final CochleaChannelConfig chan : channels) {
			// TODO add preference change or update listener to
			// synchronize when config is loaded

			// TODO add undo/redo support for channels

			final CochleaTow4EarChannelCP cPan = new CochleaTow4EarChannelCP((CochleaTow4EarChannelConfig)chan);
			colPan.add(cPan);
			chanCount++;
			if ((chanCount % CHAN_PER_COL) == 0) {
				channelsPanel.add(colPan);
				if (chanCount < channels.size()) {
					colPan = new JPanel();
					colPan.setLayout(new BoxLayout(colPan, BoxLayout.Y_AXIS));
					colPan.setAlignmentY(TOP_ALIGNMENT);
				}
			}
		}
		return channelGroupPanel;
	}

	/**
	 * @return the biasgen
	 */
	public CochleaTow4Ear.Biasgen getBiasgen() {
		return (CochleaTow4Ear.Biasgen)biasgen;
	}
}
