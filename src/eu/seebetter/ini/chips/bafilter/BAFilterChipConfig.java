package eu.seebetter.ini.chips.bafilter;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisTowerBaseConfig;
import eu.seebetter.ini.chips.davis.TowerOnChip6BitVDAC;
import eu.seebetter.ini.chips.davis.imu.ImuControl;

/**
 * Base configuration for Davis208PixelParade on Tower wafer designs
 *
 * @author Hongjie
 */
public class BAFilterChipConfig extends DavisTowerBaseConfig {

	public BAFilterChipConfig(Chip chip) {
		super(chip);

		setPotArray(new AddressedIPotArray(this)); // garbage collect IPots added in super by making this new potArray

		vdacs = new TowerOnChip6BitVDAC[8];
		// TODO fix this code for actual vdacs
		// getPotArray().addPot(new TowerOnChip6BitVDAC(this, "", 0, 0, ""));
                // https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=10
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "Vth", 0, 0,
				"Threshold Voltage for the comparator")); //different/
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "Vrs", 1, 1,
				"Resetting voltage for the Capacitor"));  //different/
		/*getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "ADC_RefHigh", 2, 2,
				"The upper limit of the input voltage to the on chip ADC"));
		getPotArray()
			.addPot(
				new TowerOnChip6BitVDAC(this, "ADC_RefLow", 3, 3,
					"The lower limit of the input voltage to the on chip ADC"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "AdcTestVoltageAI", 4, 4,
				"A fixed voltage to test the on-chip ADC if it's configured to test mode, unused"));
		getPotArray()
			.addPot(
				new TowerOnChip6BitVDAC(this, "ResetHpxBv", 5, 5,
					"High voltage to be kept for the Hp pixel of Sim Bamford"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "RefSsbxBv", 6, 6,
				"Set OffsetBns, the shifted source bias voltage of the pre-amplifier with VDAC"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unconnected", 7, 7, "Unused, no effect"));*/

		try {
			// added from gdoc
			// https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=6
			// private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
			addAIPot("LocalBufBn,n,normal,Local buffer strength"); // 8 commen
			addAIPot("PadFollBn,n,normal,Follower-pad buffer strength"); // 9 commen
			diff = addAIPot("DiffBn,n,normal,DVS differenciator gain"); // 10  nonused
			diffOn = addAIPot("OnBn,n,normal,DVS on event threshold"); // 11  nonused
			diffOff = addAIPot("OffBn,n,normal,DVS off event threshold"); // 12  nonused
			addAIPot("PixInvBn,n,normal,DVS request inversion static inverter strength"); // 13  nonused
			addAIPot("BiasComp,p,normal,Photoreceptor bias current"); // 14  different
			sf = addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current"); // 15 nonused
			refr = addAIPot("RefrBp,p,normal,DVS refractory period"); // 16 nonused nonused
			addAIPot("ReadoutBufBp,p,normal,APS analog readout buffer strangth");// 17 nonused
			addAIPot("ApsROSFBn,n,normal,APS readout source follower strength"); // 18 nonused
			addAIPot("ADCcompBp,p,normal,ADC comparator gain"); // 19 nonused
			addAIPot("ILeak,n,normal,Column arbiter request pull-down"); // 20 different nonused
			addAIPot("DACBufBp,p,normal,ADC ramp buffer strength"); // 21 nonused
			addAIPot("LcolTimeoutBn,n,normal,No column request timeout"); // 22 nonused
			addAIPot("AEPdBn,n,normal,Request encoder static pulldown strength"); // 23 nonused
			addAIPot("AEPuXBp,p,normal,AER column pullup strength"); // 24 nonused
			addAIPot("AEPuYBp,p,normal,AER row pullup strength"); // 25 nonused
			addAIPot("IFRefrBn,n,normal,Bias calibration refractory period"); // 26  commen
			addAIPot("IFThrBn,n,normal,Bias calibration neuron threshold"); // 27 commen
			addAIPot("RegBiasBp,p,normal,Bias of OTA fixing the shifted source bias OffsetBn of the pre-amplifier"); // 28 nonused
			addAIPot("Blk2P,p,normal,Ununsed P type"); // 29 nonused
			addAIPot("RefSsbxBn,n,normal,Set OffsetBns the shifted source bias voltage of the pre-amplifier with NBias");// 30 nonused
			addAIPot("Blk2N,n,normal,Ununsed N type");// 31 nonused
			addAIPot("Blk3N,n,normal,Ununsed N type");// 32 nonused
			addAIPot("Blk4N,n,normal,Ununsed N type");// 33 nonused
			addAIPot("BiasBuffer,n,normal,Biasgen buffer strength");// 34 commen

			// shifted sources
			ssn = new ShiftedSourceBiasCF(this);
			ssn.setSex(Pot.Sex.N);
			ssn.setName("SSN");
			ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
			ssn.addObserver(this);
			ssn.setAddress(35);

			ssp = new ShiftedSourceBiasCF(this);
			ssp.setSex(Pot.Sex.P);
			ssp.setName("SSP");
			ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
			ssp.addObserver(this);
			ssp.setAddress(36);

			ssBiases[1] = ssn;
			ssBiases[0] = ssp;

		}
		catch (Exception e) {
			throw new Error(e.toString());
		} // TODO fix this code for actual vdacs

		// graphicOptions
		videoControl = new DavisConfig.VideoControl();
		videoControl.addObserver(this); ///nonused

		// on-chip configuration chain
		chipConfigChain = new BAFilterChipConfigChain(chip);
		chipConfigChain.addObserver(this); //changed

		// imuControl
		imuControl = new ImuControl(this);  //nonused

		setBatchEditOccurring(true);
		loadPreferences();
		setBatchEditOccurring(false);
		try {
			sendConfiguration(this);
		}
		catch (HardwareInterfaceException ex) {
			Logger.getLogger(BAFilterChip.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

        
        /* change this BAFilterChipConfigChain to real BAFilterChipCongfigChain important ones. 
        https://docs.google.com/spreadsheet/ccc?key=0AjvXOhBHjRhedEQtTFBUOHY1NzVPZ3VxdWZCcklrbnc#gid=10 */
        
	public class BAFilterChipConfigChain extends DavisTowerBaseConfig.DavisTowerBaseChipConfigChain {
		OnchipConfigBit SelPreAmpAvgxD = new OnchipConfigBit(chip, "SelPreAmpAvgxD", 9,
			"If 1, connect PreAmpAvgxA to calibration neuron, if 0, commongate", false);
		OnchipConfigBit SelBiasRefxD = new OnchipConfigBit(chip, "SelBiasRefxD", 10,
			"If 1, select Nbias Blk1N, if 0, VDAC VblkV2", true);
		OnchipConfigBit SelSensexD = new OnchipConfigBit(chip, "SelSensexD", 11,
			"If 0, hook refractory bias to Vdd (unselect)", true);
		OnchipConfigBit SelPosFbxD = new OnchipConfigBit(chip, "SelPosFbxD", 12,
			"If 0, hook refractory bias to Vdd (unselect)", true);
		OnchipConfigBit SelHpxD = new OnchipConfigBit(chip, "SelHpxD", 13,
			"If 0, hook refractory bias to Vdd (unselect)", true);

		public BAFilterChipConfigChain(Chip chip) {
			super(chip);

			configBits[9] = SelPreAmpAvgxD;
			configBits[9].addObserver(this);
			configBits[10] = SelBiasRefxD;
			configBits[10].addObserver(this);
			configBits[11] = SelSensexD;
			configBits[11].addObserver(this);
			configBits[12] = SelPosFbxD;
			configBits[12].addObserver(this);
			configBits[13] = SelHpxD;
			configBits[13].addObserver(this);
		}
	}
}
