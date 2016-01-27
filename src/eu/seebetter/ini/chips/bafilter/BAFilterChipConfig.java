package eu.seebetter.ini.chips.bafilter;

import java.util.logging.Level;

import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisTowerBaseConfig;
import eu.seebetter.ini.chips.davis.TowerOnChip6BitVDAC;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Base configuration for BA filter chip (AERCorrFilter).
 */
public class BAFilterChipConfig extends DavisTowerBaseConfig {

	public BAFilterChipConfig(final Chip chip) {
		super(chip);
		setName("BAFilterChipConfig");

		ipots = new AddressedIPotArray(this);

		// VDAC biases
		ipots.addPot(new TowerOnChip6BitVDAC(this, "Vth", 0, 0, "Threshold Voltage for the comparator"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "Vrs", 1, 0, "Resetting voltage for the capacitor"));

		// CoarseFine biases
		DavisConfig.addAIPot(ipots, this, "LocalBufBn,8,n,normal,Local buffer bias");
		DavisConfig.addAIPot(ipots, this, "PadFollBn,9,n,normal,Follower-pad buffer bias current");
		DavisConfig.addAIPot(ipots, this, "BiasComp,14,p,normal,BiasComp");
		DavisConfig.addAIPot(ipots, this, "ILeak,20,n,normal,ILeak");
		DavisConfig.addAIPot(ipots, this, "IFRefrBn,26,n,normal,Bias calibration refractory period bias current");
		DavisConfig.addAIPot(ipots, this, "IFThrBn,27,n,normal,Bias calibration neuron threshold");
		DavisConfig.addAIPot(ipots, this, "biasBuffer,34,n,normal,special buffer bias ");

		setPotArray(ipots);

		// New chip control bits.
		// TODO: add.

		setBatchEditOccurring(true);
		loadPreferences();
		setBatchEditOccurring(false);

		try {
			sendConfiguration(this);
		}
		catch (final HardwareInterfaceException ex) {
			Biasgen.log.log(Level.SEVERE, null, ex);
		}
	}
}
