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
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.JTabbedPane;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.biasgen.Biasgen;

public abstract class AbstractChipControlPanel extends JTabbedPane implements Observer {

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

	/**
	 * Handles updates to GUI controls from any source, including preference changes
	 */
	
	@Override
	public synchronized void update(final Observable observable, final Object object) {
		try {
			// Ensure GUI is up-to-date.
			if (observable instanceof AbstractConfigValue) {
				((AbstractConfigValue) observable).updateControl();
			}
			else {
				log.warning("unknown observable " + observable + " , not sending anything");
			}
		}
		catch (final Exception e) {
			log.warning(e.toString());
		}
	}
}
