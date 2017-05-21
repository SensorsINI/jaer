/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
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
 * Base configuration for tower wafer Davis chips that use the Tower wafer bias
 * generator
 *
 * @author tobi
 */
public class DavisTowerBaseConfig extends DavisConfig {
	public DavisTowerBaseConfig(final Chip chip) {
		super(chip);
		setName("DavisTowerBaseConfig");

		ipots = new AddressedIPotArray(this);

		// VDAC biases
		ipots.addPot(new TowerOnChip6BitVDAC(this, "apsOverflowLevel", 0, 0,
			"Sets reset level gate voltage of APS reset FET to prevent overflow causing DVS events"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ApsCas", 1, 0,
			"n-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefHigh", 2, 0, "on-chip column-parallel APS ADC upper conversion limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefLow", 3, 0, "on-chip column-parallel APS ADC ADC lower limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "AdcTestVoltage", 4, 0, "Voltage supply for testing the ADC"));

		// CoarseFine biases
		DavisConfig.addAIPot(ipots, this, "LocalBufBn,8,n,normal,Local buffer bias");
		DavisConfig.addAIPot(ipots, this, "PadFollBn,9,n,normal,Follower-pad buffer bias current");
		diff = DavisConfig.addAIPot(ipots, this, "DiffBn,10,n,normal,differencing amp");
		diffOn = DavisConfig.addAIPot(ipots, this, "OnBn,11,n,normal,DVS brighter threshold");
		diffOff = DavisConfig.addAIPot(ipots, this, "OffBn,12,n,normal,DVS darker threshold");
		DavisConfig.addAIPot(ipots, this, "PixInvBn,13,n,normal,Pixel request inversion static inverter bias");
		pr = DavisConfig.addAIPot(ipots, this, "PrBp,14,p,normal,Photoreceptor bias current");
		sf = DavisConfig.addAIPot(ipots, this, "PrSFBp,15,p,normal,Photoreceptor follower bias current (when used in pixel type)");
		refr = DavisConfig.addAIPot(ipots, this, "RefrBp,16,p,normal,DVS refractory period current");
		DavisConfig.addAIPot(ipots, this, "ReadoutBufBP,17,p,normal,APS readout OTA follower bias");
		DavisConfig.addAIPot(ipots, this, "ApsROSFBn,18,n,normal,APS readout source follower bias");
		DavisConfig.addAIPot(ipots, this, "ADCcompBp,19,p,normal,ADC comparator bias");
		DavisConfig.addAIPot(ipots, this, "ColSelLowBn,20,n,normal,Column arbiter request pull-down");
		DavisConfig.addAIPot(ipots, this, "DACBufBp,21,p,normal,Row request pull up");
		DavisConfig.addAIPot(ipots, this, "LcolTimeoutBn,22,n,normal,No column request timeout");
		DavisConfig.addAIPot(ipots, this, "AEPdBn,23,n,normal,Request encoder pulldown static current");
		DavisConfig.addAIPot(ipots, this, "AEPuXBp,24,p,normal,AER column pullup");
		DavisConfig.addAIPot(ipots, this, "AEPuYBp,25,p,normal,AER row pullup");
		DavisConfig.addAIPot(ipots, this, "IFRefrBn,26,n,normal,Bias calibration refractory period bias current");
		DavisConfig.addAIPot(ipots, this, "IFThrBn,27,n,normal,Bias calibration neuron threshold");
		DavisConfig.addAIPot(ipots, this, "biasBuffer,34,n,normal,special buffer bias ");

		setPotArray(ipots);

		// ShiftedSource biases (only set new address!)
		ssp.setAddress(35);
		ssn.setAddress(36);

		// Internal ADC only on new chips.
		final List<SPIConfigValue> apsControlLocal = new ArrayList<>();

		apsControlLocal.add(new SPIConfigBit("APS.UseInternalADC", "Use the on-chip ADC instead of the external TI ADC.",
			CypressFX3.FPGA_APS, (short) 34, true, this));
		apsControlLocal
			.add(new SPIConfigBit("APS.SampleEnable", "Enable Sample&Hold circuitry.", CypressFX3.FPGA_APS, (short) 35, true, this));
		apsControlLocal
			.add(new SPIConfigInt("APS.SampleSettle", "Sample hold time (in cycles).", CypressFX3.FPGA_APS, (short) 36, 6, 30, this));
		apsControlLocal
			.add(new SPIConfigInt("APS.RampReset", "Ramp reset time (in cycles).", CypressFX3.FPGA_APS, (short) 37, 6, 10, this));
		apsControlLocal.add(new SPIConfigBit("APS.RampShortReset", "Only go through half the ramp for reset read.", CypressFX3.FPGA_APS,
			(short) 38, false, this));
		apsControlLocal.add(new SPIConfigBit("APS.ADCTestMode", "Put all APS pixels in permanent reset for ADC testing.",
			CypressFX3.FPGA_APS, (short) 39, false, this));

		for (final SPIConfigValue cfgVal : apsControlLocal) {
			cfgVal.addObserver(this);
			allPreferencesList.add(cfgVal);
		}

		apsControl.addAll(apsControlLocal);

		// Additional chip control bits.
		final List<SPIConfigValue> chipControlLocal = new ArrayList<>();

		chipControlLocal.add(new SPIConfigBit("Chip.SelectGrayCounter",
			"Select internal gray counter, if disabled, external gray code is used.", CypressFX3.FPGA_CHIPBIAS, (short) 143, true, this));
		chipControlLocal.add(new SPIConfigBit("Chip.TestADC", "Pass ADC Test Voltage to internal ADC instead of pixel voltage.",
			CypressFX3.FPGA_CHIPBIAS, (short) 144, false, this));

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

	@Override
	public synchronized void update(final Observable observable, final Object object) {
		super.update(observable, object);

		if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
			final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();

			try {
				if (observable instanceof TowerOnChip6BitVDAC) {
					final TowerOnChip6BitVDAC vdPot = (TowerOnChip6BitVDAC) observable;

					fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) vdPot.getAddress(), vdPot.computeBinaryRepresentation());
				}
			}
			catch (final HardwareInterfaceException e) {
				Biasgen.log.warning("On update() caught " + e.toString());
			}
		}
	}
}
