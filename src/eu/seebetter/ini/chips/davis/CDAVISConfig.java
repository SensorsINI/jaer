/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

/**
 * Base configuration for CDAVIS
 *
 * @author tobi
 */
public class CDAVISConfig extends DavisTowerBaseConfig {
	public CDAVISConfig(final Chip chip) {
		super(chip);
		setName("DavisRGBW640Config");

		ipots = new AddressedIPotArray(this);

		// VDAC biases
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ApsCasBpc", 0, 0,
			"N-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "OVG1Lo", 1, 0,
			"Logic low level of the overflow gate in the DAVIS pixel if it's configured as adjustable"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "OVG2Lo", 2, 0,
			"Logic low level of the overflow gate in the APS pixel if it's configured as adjustable"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "TX2OVG2Hi", 3, 0,
			"Logic high level of the overflow gate and transfer gate in the APS pixel if it's configured as adjustable"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "Gnd07", 4, 0, "Elevated ground source at 0.7V for producing 4V reset signals"));
		ipots.addPot(
			new TowerOnChip6BitVDAC(this, "VTestADC", 5, 0, "A fixed voltage to test the on-chip ADC if it's configured to test mode"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADCRefHigh", 6, 0, "The upper limit of the input voltage to the on chip ADC"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADCRefLow", 7, 0, "The lower limit of the input voltage to the on chip ADC"));

		// CoarseFine biases
		DavisConfig.addAIPot(ipots, this, "IFRefrBn,8,n,normal,Bias calibration refractory period"); // 8
		DavisConfig.addAIPot(ipots, this, "IFThrBn,9,n,normal,Bias calibration neuron threshold");// 9
		DavisConfig.addAIPot(ipots, this, "LocalBufBn,10,n,normal,Local buffer strength");// 10
		DavisConfig.addAIPot(ipots, this, "PadFollBn,11,n,normal,Follower-pad buffer strength");// 11
		DavisConfig.addAIPot(ipots, this, "Blk1N,12,n,normal,Ununsed N type");// 12
		DavisConfig.addAIPot(ipots, this, "PixInvBn,13,n,normal,DVS request inversion static inverter strength");// 13
		diff = DavisConfig.addAIPot(ipots, this, "DiffBn,14,n,normal,DVS differenciator gain");// 14
		diffOn = DavisConfig.addAIPot(ipots, this, "OnBn,15,n,normal,DVS on event threshold");// 15
		diffOff = DavisConfig.addAIPot(ipots, this, "OffBn,16,n,normal,DVS off event threshold");// 16
		pr = DavisConfig.addAIPot(ipots, this, "PrBp,17,p,normal,Photoreceptor bias current");// 17
		sf = DavisConfig.addAIPot(ipots, this, "PrSFBp,18,p,normal,Photoreceptor follower bias current"); // 18
		refr = DavisConfig.addAIPot(ipots, this, "RefrBp,19,p,normal,DVS refractory period"); // 19
		DavisConfig.addAIPot(ipots, this, "ArrayBiasBufferBn,20,n,normal,Row/column bias buffer strength"); // 20
		DavisConfig.addAIPot(ipots, this, "ArrayLogicBufferBn,22,n,normal,Row logic level buffer strength"); // 22
		DavisConfig.addAIPot(ipots, this, "FalltimeBn,23,n,normal,Fall time of the APS control signals");// 23
		DavisConfig.addAIPot(ipots, this, "RisetimeBp,24,p,normal,Rise time of the APS control signals");// 24
		DavisConfig.addAIPot(ipots, this, "ReadoutBufBp,25,p,normal,APS analog readout buffer strangth");// 25
		DavisConfig.addAIPot(ipots, this, "ApsROSFBn,26,n,normal,APS readout source follower strength"); // 26
		DavisConfig.addAIPot(ipots, this, "ADCcompBp,27,p,normal,ADC comparator gain"); // 27
		DavisConfig.addAIPot(ipots, this, "DACBufBp,28,p,normal,ADC ramp buffer strength"); // 28
		DavisConfig.addAIPot(ipots, this, "LcolTimeoutBn,30,n,normal,No column request timeout"); // 30
		DavisConfig.addAIPot(ipots, this, "AEPdBn,31,n,normal,Request encoder static pulldown strength"); // 31
		DavisConfig.addAIPot(ipots, this, "AEPuXBp,32,p,normal,AER column pullup strength"); // 32
		DavisConfig.addAIPot(ipots, this, "AEPuYBp,33,p,normal,AER row pullup strength"); // 33
		DavisConfig.addAIPot(ipots, this, "BiasBuffer,34,n,normal,Biasgen buffer strength");// 34

		setPotArray(ipots);

		// Additional APS parameters.
		final List<SPIConfigValue> apsControlLocal = new ArrayList<>();

		apsControlLocal
			.add(new SPIConfigInt("APS.Transfer_D", "Transfer time counter (3 in GS, 1 in RS).", CypressFX3.FPGA_APS, (short) 14, 16, 0, this));
		apsControlLocal.add(new SPIConfigInt("APS.RSFDSettle_D", "RS counter 0.", CypressFX3.FPGA_APS, (short) 15, 12, 0, this));
		apsControlLocal.add(new SPIConfigInt("APS.GSPDReset_D", "GS counter 0.", CypressFX3.FPGA_APS, (short) 16, 12, 0, this));
		apsControlLocal.add(new SPIConfigInt("APS.GSResetFall_D", "GS counter 1.", CypressFX3.FPGA_APS, (short) 17, 12, 0, this));
		apsControlLocal.add(new SPIConfigInt("APS.GSTXFall_D", "GS counter 2.", CypressFX3.FPGA_APS, (short) 18, 12, 0, this));
		apsControlLocal.add(new SPIConfigInt("APS.GSFDReset_D", "GS counter 3.", CypressFX3.FPGA_APS, (short) 19, 12, 0, this));

		for (final SPIConfigValue cfgVal : apsControlLocal) {
			cfgVal.addObserver(this);
			allPreferencesList.add(cfgVal);
		}

		apsControl.addAll(apsControlLocal);

		// Additional chip control bits.
		final List<SPIConfigValue> chipControlLocal = new ArrayList<>();

		chipControlLocal.add(new SPIConfigBit("Chip.AdjustOVG1Lo", "Adjust OVG1 Low.", CypressFX3.FPGA_CHIPBIAS, (short) 145, true, this));
		chipControlLocal.add(new SPIConfigBit("Chip.AdjustOVG2Lo", "Adjust OVG2 Low.", CypressFX3.FPGA_CHIPBIAS, (short) 146, false, this));
		chipControlLocal.add(new SPIConfigBit("Chip.AdjustTX2OVG2Hi", "Adjust TX2OVG2Hi.", CypressFX3.FPGA_CHIPBIAS, (short) 147, false, this));

		for (final SPIConfigValue cfgVal : chipControlLocal) {
			cfgVal.addObserver(this);
			allPreferencesList.add(cfgVal);
		}

		chipControl.addAll(chipControlLocal);

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
