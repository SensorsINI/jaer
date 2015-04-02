package eu.seebetter.ini.chips.davis;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import eu.seebetter.ini.chips.davis.imu.ImuControl;

/**
 * Base configuration for Davis208PixelParade on Tower wafer designs
 *
 * @author Diederik
 */
public class Davis208PixelParadeConfig extends DavisTowerBaseConfig {

	public Davis208PixelParadeConfig(Chip chip) {
		super(chip);

		setPotArray(new AddressedIPotArray(this)); // garbage collect IPots added in super by making this new potArray

		vdacs = new TowerOnChip6BitVDAC[8];
		// TODO fix this code for actual vdacs
		// getPotArray().addPot(new TowerOnChip6BitVDAC(this, "", 0, 0, ""));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "apsOverflowLevel", 0, 0,
				"Logic low level of the overflow gate in the DAVIS pixel if it's configured as adjustable"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "ApsCas", 1, 1,
				"N-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
		getPotArray().addPot(
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
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unconnected", 7, 7, "Unused, no effect"));

		try {
			// added from gdoc
			// https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=6
			// private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
			addAIPot("LocalBufBn,n,normal,Local buffer strength"); // 8
			addAIPot("PadFollBn,n,normal,Follower-pad buffer strength"); // 9
			diff = addAIPot("DiffBn,n,normal,DVS differenciator gain"); // 10
			diffOn = addAIPot("OnBn,n,normal,DVS on event threshold"); // 11
			diffOff = addAIPot("OffBn,n,normal,DVS off event threshold"); // 12
			addAIPot("PixInvBn,n,normal,DVS request inversion static inverter strength"); // 13
			pr = addAIPot("PrBp,p,normal,Photoreceptor bias current"); // 14
			sf = addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current"); // 15
			refr = addAIPot("RefrBp,p,normal,DVS refractory period"); // 16
			addAIPot("ReadoutBufBp,p,normal,APS analog readout buffer strangth");// 17
			addAIPot("ApsROSFBn,n,normal,APS readout source follower strength"); // 18
			addAIPot("ADCcompBp,p,normal,ADC comparator gain"); // 19
			addAIPot("ColSelLowBn,n,normal,Column arbiter request pull-down"); // 20
			addAIPot("DACBufBp,p,normal,ADC ramp buffer strength"); // 21
			addAIPot("LcolTimeoutBn,n,normal,No column request timeout"); // 22
			addAIPot("AEPdBn,n,normal,Request encoder static pulldown strength"); // 23
			addAIPot("AEPuXBp,p,normal,AER column pullup strength"); // 24
			addAIPot("AEPuYBp,p,normal,AER row pullup strength"); // 25
			addAIPot("IFRefrBn,n,normal,Bias calibration refractory period"); // 26
			addAIPot("IFThrBn,n,normal,Bias calibration neuron threshold"); // 27
			addAIPot("RegBiasBp,p,normal,Bias of OTA fixing the shifted source bias OffsetBn of the pre-amplifier"); // 28
			addAIPot("Blk2P,p,normal,Ununsed P type"); // 29
			addAIPot("RefSsbxBn,n,normal,Set OffsetBns, the shifted source bias voltage of the pre-amplifier with NBias");// 30
			addAIPot("Blk2N,n,normal,Ununsed N type");// 31
			addAIPot("Blk3N,n,normal,Ununsed N type");// 32
			addAIPot("Blk4N,n,normal,Ununsed N type");// 33
			addAIPot("BiasBuffer,n,normal,Biasgen buffer strength");// 34

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
		videoControl = new VideoControl();
		videoControl.addObserver(this);

		// on-chip configuration chain
		chipConfigChain = new Davis208PixelParadeChipConfigChain(chip);
		chipConfigChain.addObserver(this);

		// imuControl
		imuControl = new ImuControl(this);

		setBatchEditOccurring(true);
		loadPreferences();
		setBatchEditOccurring(false);
		try {
			sendConfiguration(this);
		}
		catch (HardwareInterfaceException ex) {
			Logger.getLogger(Davis208PixelParade.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public class Davis208PixelParadeChipConfigChain extends DavisTowerBaseChipConfigChain {
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

		public Davis208PixelParadeChipConfigChain(Chip chip) {
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
