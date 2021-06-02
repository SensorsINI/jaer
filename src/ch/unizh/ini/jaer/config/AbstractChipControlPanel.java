/**
 * AbstractCochleaControlPanel.java
 * 
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on June 27, 2016, 14:34
 */

package ch.unizh.ini.jaer.config;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

import javax.swing.JTabbedPane;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.biasgen.Biasgen;

public abstract class AbstractChipControlPanel extends JTabbedPane {

	protected final String panelName;
	protected final Logger log;

	protected final Chip chip;
	protected final Biasgen biasgen;
	protected final String prefNameSelectedTab;

	public AbstractChipControlPanel(final Chip chip) {
		this.chip = chip;
		biasgen = chip.getBiasgen();
		panelName = chip.getName() + "ControlPanel";
		prefNameSelectedTab = panelName + ".bgTabbedPaneSelectedIndex";
		log = Logger.getLogger(panelName);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent evt) {
				chip.getPrefs().putInt(prefNameSelectedTab, getSelectedIndex());
			}
		});
		
		setToolTipText("Select a tab to configure an aspect of the device.");
	}

	/**
	 * @return the biasgen
	 */
	public Biasgen getBiasgen() {
		return biasgen;
	}

	protected Logger getLogger(){
		return log;
	}
}
