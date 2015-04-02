/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import eu.seebetter.ini.chips.davis.imu.ImuControl;

/**
 * Base configuration for Davis356 (ApsDvs346) on Tower wafer designs
 *
 * @author tobi
 */
public class DavisRGBW640Config extends DavisTowerBaseConfig {
	protected CPLDInt Transfer_D = new CPLDInt(chip, 167, 152, (1 << 12) - 1, "Transfer_D",
		"Transfer time counter (3 in GS, 1 in RS).", 0);
	protected CPLDInt RSFDSettle_D = new CPLDInt(chip, 183, 168, (1 << 12) - 1, "RSFDSettle_D",
		"RS counter 0.", 0);
	protected CPLDInt RSCpReset_D = new CPLDInt(chip, 199, 184, (1 << 12) - 1, "RSCpReset_D",
		"RS counter 2.", 0);
	protected CPLDInt RSCpSettle_D = new CPLDInt(chip, 215, 200, (1 << 12) - 1, "RSCpSettle_D",
		"RS counter 3.", 0);
	protected CPLDInt GSPDReset_D = new CPLDInt(chip, 231, 216, (1 << 12) - 1, "GSPDReset_D",
		"GS counter 0.", 0);
	protected CPLDInt GSResetFall_D = new CPLDInt(chip, 247, 232, (1 << 12) - 1, "GSResetFall_D",
		"GS counter 2.", 0);
	protected CPLDInt GSTXFall_D = new CPLDInt(chip, 263, 248, (1 << 12) - 1, "GSTXFall_D",
		"GS counter 4.", 0);
	protected CPLDInt GSFDReset_D = new CPLDInt(chip, 279, 264, (1 << 12) - 1, "GSFDReset_D",
		"GS counter 5.", 0);
	protected CPLDInt GSCpResetFD_D = new CPLDInt(chip, 295, 280, (1 << 12) - 1, "GSCpResetFD_D",
		"GS counter 6.", 0);
	protected CPLDInt GSCpResetSettle_D = new CPLDInt(chip, 311, 296, (1 << 12) - 1, "GSCpResetSettle_D",
		"GS counter 7.", 0);

	public DavisRGBW640Config(Chip chip) {
		super(chip);

		addConfigValue(Transfer_D);
		addConfigValue(RSFDSettle_D);
		addConfigValue(RSCpReset_D);
		addConfigValue(RSCpSettle_D);
		addConfigValue(GSPDReset_D);
		addConfigValue(GSResetFall_D);
		addConfigValue(GSTXFall_D);
		addConfigValue(GSFDReset_D);
		addConfigValue(GSCpResetFD_D);
		addConfigValue(GSCpResetSettle_D);

		setPotArray(new AddressedIPotArray(this)); // garbage collect IPots added in super by making this new potArray

		vdacs = new TowerOnChip6BitVDAC[8];
		// TODO fix this code for actual vdacs
		// getPotArray().addPot(new TowerOnChip6BitVDAC(this, "", 0, 0, ""));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "ApsCasBpc", 0, 0,
				"N-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "OVG1Lo", 1, 1,
				"Logic low level of the overflow gate in the DAVIS pixel if it's configured as adjustable"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "OVG2Lo", 2, 2,
				"Logic low level of the overflow gate in the APS pixel if it's configured as adjustable"));
		getPotArray()
			.addPot(
				new TowerOnChip6BitVDAC(this, "TX2OVG2Hi", 3, 3,
					"Logic high level of the overflow gate and transfer gate in the APS pixel if it's configured as adjustable"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "Gnd07", 4, 4,
				"Elevated ground source at 0.7V for producing 4V reset signals"));
		getPotArray().addPot(
			new TowerOnChip6BitVDAC(this, "VTestADC", 5, 5,
				"A fixed voltage to test the on-chip ADC if it's configured to test mode"));
		getPotArray()
			.addPot(
				new TowerOnChip6BitVDAC(this, "ADCRefHigh", 6, 6,
					"The upper limit of the input voltage to the on chip ADC"));
		getPotArray()
			.addPot(
				new TowerOnChip6BitVDAC(this, "ADCRefLow", 7, 7,
					"The lower limit of the input voltage to the on chip ADC"));

		try {
			// added from gdoc
			// https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=6
			// private AddressedIPotCF diffOn, diffOff, refr, pr, sf, diff;
			addAIPot("IFRefrBn,n,normal,Bias calibration refractory period"); // 8
			addAIPot("IFThrBn,n,normal,Bias calibration neuron threshold");// 9
			addAIPot("LocalBufBn,n,normal,Local buffer strength");// 10
			addAIPot("PadFollBn,n,normal,Follower-pad buffer strength");// 11
			addAIPot("Blk1N,n,normal,Ununsed N type");// 12
			addAIPot("PixInvBn,n,normal,DVS request inversion static inverter strength");// 13
			diff = addAIPot("DiffBn,n,normal,DVS differenciator gain");// 14
			diffOn = addAIPot("OnBn,n,normal,DVS on event threshold");// 15
			diffOff = addAIPot("OffBn,n,normal,DVS off event threshold");// 16
			pr = addAIPot("PrBp,p,normal,Photoreceptor bias current");// 17
			sf = addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current"); // 18
			refr = addAIPot("RefrBp,p,normal,DVS refractory period"); // 19
			addAIPot("ArrayBiasBufferBn,n,normal,Row/column bias buffer strength"); // 20
			addAIPot("Blk1P,p,normal,Ununsed P type"); // 21
			addAIPot("ArrayLogicBufferBn,n,normal,Row logic level buffer strength"); // 22
			addAIPot("FalltimeBn,n,normal,Fall time of the APS control signals");// 23
			addAIPot("RisetimeBp,p,normal,Rise time of the APS control signals");// 24
			addAIPot("ReadoutBufBp,p,normal,APS analog readout buffer strangth");// 25
			addAIPot("ApsROSFBn,n,normal,APS readout source follower strength"); // 26
			addAIPot("ADCcompBp,p,normal,ADC comparator gain"); // 27
			addAIPot("DACBufBp,p,normal,ADC ramp buffer strength"); // 28
			addAIPot("Blk2P,p,normal,Ununsed P type"); // 29
			addAIPot("LcolTimeoutBn,n,normal,No column request timeout"); // 30
			addAIPot("AEPdBn,n,normal,Request encoder static pulldown strength"); // 31
			addAIPot("AEPuXBp,p,normal,AER column pullup strength"); // 32
			addAIPot("AEPuYBp,p,normal,AER row pullup strength"); // 33
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
		chipConfigChain = new DavisRGBW640ChipConfigChain(chip);
		chipConfigChain.addObserver(this);

		// control of log readout
		apsReadoutControl = new DavisRGBW640APSReadoutControl();

		// imuControl
		imuControl = new ImuControl(this);

		setBatchEditOccurring(true);
		loadPreferences();
		setBatchEditOccurring(false);
		try {
			sendConfiguration(this);
		}
		catch (HardwareInterfaceException ex) {
			Logger.getLogger(DAVIS240BaseCamera.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public class DavisRGBW640ChipConfigChain extends DavisTowerBaseChipConfigChain {
		OnchipConfigBit adjustOVG1Lo = new OnchipConfigBit(chip, "AdjustOVG1Lo", 9, "Adjust OVG1 Low.", true);
		OnchipConfigBit adjustOVG2Lo = new OnchipConfigBit(chip, "AdjustOVG2Lo", 10, "Adjust OVG2 Low.", false);
		OnchipConfigBit adjustTX2OVG2Hi = new OnchipConfigBit(chip, "AdjustTX2OVG2Hi", 11, "Adjust TX2OVG2Hi.", false);

		public DavisRGBW640ChipConfigChain(Chip chip) {
			super(chip);

			// DavisRGBW640 has no global shutter config bit, it's a pad.
			configBits[6].deleteObservers();
			configBits[6] = null;

			configBits[9] = adjustOVG1Lo;
			configBits[9].addObserver(this);
			configBits[10] = adjustOVG2Lo;
			configBits[10].addObserver(this);
			configBits[11] = adjustTX2OVG2Hi;
			configBits[11].addObserver(this);
		}
	}

	public class DavisRGBW640APSReadoutControl extends ApsReadoutControl {
		public DavisRGBW640APSReadoutControl() {
			super();

			Transfer_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("Transfer_D", Transfer_D.getDescription());
			RSFDSettle_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("RSFDSettle_D", RSFDSettle_D.getDescription());
			RSCpReset_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("RSCpReset_D", RSCpReset_D.getDescription());
			RSCpSettle_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("RSCpSettle_D", RSCpSettle_D.getDescription());
			GSPDReset_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSPDReset_D", GSPDReset_D.getDescription());
			GSResetFall_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSResetFall_D", GSResetFall_D.getDescription());
			GSTXFall_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSTXFall_D", GSTXFall_D.getDescription());
			GSFDReset_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSFDReset_D", GSFDReset_D.getDescription());
			GSCpResetFD_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSCpResetFD_D", GSCpResetFD_D.getDescription());
			GSCpResetSettle_D.addObserver(this);
			tooltipSupport.setPropertyTooltip("GSCpResetSettle_D", GSCpResetSettle_D.getDescription());
		}

		public void setTransfer_D(int cc) {
			Transfer_D.set(cc);
		}

		public int getTransfer_D() {
			return Transfer_D.get();
		}

		public void setRSFDSettle_D(int cc) {
			RSFDSettle_D.set(cc);
		}

		public int getRSFDSettle_D() {
			return RSFDSettle_D.get();
		}

		public void setRSCpReset_D(int cc) {
			RSCpReset_D.set(cc);
		}

		public int getRSCpReset_D() {
			return RSCpReset_D.get();
		}

		public void setRSCpSettle_D(int cc) {
			RSCpSettle_D.set(cc);
		}

		public int getRSCpSettle_D() {
			return RSCpSettle_D.get();
		}

		public void setGSPDReset_D(int cc) {
			GSPDReset_D.set(cc);
		}

		public int getGSPDReset_D() {
			return GSPDReset_D.get();
		}

		public void setGSResetFall_D(int cc) {
			GSResetFall_D.set(cc);
		}

		public int getGSResetFall_D() {
			return GSResetFall_D.get();
		}

		public void setGSTXFall_D(int cc) {
			GSTXFall_D.set(cc);
		}

		public int getGSTXFall_D() {
			return GSTXFall_D.get();
		}

		public void setGSFDReset_D(int cc) {
			GSFDReset_D.set(cc);
		}

		public int getGSFDReset_D() {
			return GSFDReset_D.get();
		}

		public void setGSCpResetFD_D(int cc) {
			GSCpResetFD_D.set(cc);
		}

		public int getGSCpResetFD_D() {
			return GSCpResetFD_D.get();
		}

		public void setGSCpResetSettle_D(int cc) {
			GSCpResetSettle_D.set(cc);
		}

		public int getGSCpResetSettle_D() {
			return GSCpResetSettle_D.get();
		}
	}
}
