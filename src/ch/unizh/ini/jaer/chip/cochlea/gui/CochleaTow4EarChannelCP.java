package ch.unizh.ini.jaer.chip.cochlea.gui;

import ch.unizh.ini.jaer.chip.cochlea.CochleaTow4EarChannelConfig;
import ch.unizh.ini.jaer.config.AbstractChipControlPanel;
import ch.unizh.ini.jaer.config.AbstractMultiBitRegisterCP;
import ch.unizh.ini.jaer.config.MultiBitConfigRegister;

/**
 * Control panel for CochleaLP Channel
 *
 * @author Ilya Kiselev
 */
public class CochleaTow4EarChannelCP extends AbstractMultiBitRegisterCP {

	/**
	 * Construct control for one channel
	 *
	 * @param chan,
	 *     the channel, or null to control all channels
	 */
	public CochleaTow4EarChannelCP(final CochleaTow4EarChannelConfig chan) {
		super((MultiBitConfigRegister) chan, new AbstractMultiBitRegisterCP.ActionsCollection(CochleaTow4EarChannelConfig.getFieldsLengths()));

		label.setText(reg.getName());
		label.setToolTipText("<html>" + reg.toString() + "<br>" + reg.getDescription() + "<br>Enter value or use mouse wheel or arrow keys to change value.");
	}
}
