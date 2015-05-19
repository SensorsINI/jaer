package eu.seebetter.ini.chips.bafilter;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;
import ch.unizh.ini.jaer.config.onchip.OutputMux;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisTowerBaseConfig;
import eu.seebetter.ini.chips.davis.TowerOnChip6BitVDAC;
import eu.seebetter.ini.chips.davis.imu.ImuControl;

/**
 * Base configuration for BA filter chip (AERCorrFilter).
 */
public class BAFilterChipConfig extends DavisTowerBaseConfig {

	public BAFilterChipConfig(Chip chip) {
		super(chip);

		setPotArray(new AddressedIPotArray(this)); // garbage collect IPots added in super by making this new potArray

		vdacs = new TowerOnChip6BitVDAC[8];
		// https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=10

		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Vth", 0, 0, "Threshold Voltage for the comparator"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Vrs", 1, 1, "Resetting voltage for the capacitor"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused1", 2, 2, "Unused"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused2", 3, 3, "Unused"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused3", 4, 4, "Unused"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused4", 5, 5, "Unused"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused5", 6, 6, "Unused"));
		getPotArray().addPot(new TowerOnChip6BitVDAC(this, "Unused6", 7, 7, "Unused"));

		try {
			// added from gdoc
			// https://docs.google.com/spreadsheet/ccc?key=0AuXeirzvZroNdHNLMWVldWVJdkdqNGNxOG5ZOFdXcHc#gid=6
			addAIPot("LocalBufBn,n,normal,Local buffer strength"); // 8
			addAIPot("PadFollBn,n,normal,Follower-pad buffer strength"); // 9
			diff = addAIPot("Blk1N,n,normal,Unused N type"); // 10 unused
			diffOn = addAIPot("Blk2N,n,normal,Unused N type"); // 11 unused
			diffOff = addAIPot("Blk3N,n,normal,Unused N type"); // 12 unused
			addAIPot("Blk4N,n,normal,Unused N type"); // 13 unused
			addAIPot("BiasComp,p,normal,Photoreceptor bias current"); // 14
			sf = addAIPot("Blk5N,n,normal,Unused N type"); // 15 unused
			refr = addAIPot("Blk6N,n,normal,Unused N type"); // 16 unused
			addAIPot("Blk7N,n,normal,Unused N type");// 17 unused
			addAIPot("Blk8N,n,normal,Unused N type"); // 18 unused
			addAIPot("Blk9N,n,normal,Unused N type"); // 19 unused
			addAIPot("ILeak,n,normal,Column arbiter request pull-down"); // 20 unused
			addAIPot("Blk10N,n,normal,Unused N type"); // 21 unused
			addAIPot("Blk11N,n,normal,Unused N type"); // 22 unused
			addAIPot("Blk12N,n,normal,Unused N type"); // 23 unused
			addAIPot("Blk13N,n,normal,Unused N type"); // 24 unused
			addAIPot("Blk14N,n,normal,Unused N type"); // 25 unused
			addAIPot("IFRefrBn,n,normal,Bias calibration refractory period"); // 26
			addAIPot("IFThrBn,n,normal,Bias calibration neuron threshold"); // 27
			addAIPot("Blk15N,n,normal,Unused N type"); // 28 unused
			addAIPot("Blk16N,n,normal,Unused N type"); // 29 unused
			addAIPot("Blk17N,n,normal,Unused N type");// 30 unused
			addAIPot("Blk18N,n,normal,Ununsed N type");// 31 unused
			addAIPot("Blk19N,n,normal,Ununsed N type");// 32 unused
			addAIPot("Blk20N,n,normal,Ununsed N type");// 33 unused
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
		videoControl = new DavisConfig.VideoControl();
		videoControl.addObserver(this); // unused

		// on-chip configuration chain
		chipConfigChain = new BAFilterChipConfigChain(chip);
		chipConfigChain.addObserver(this); // changed

		// imuControl
		imuControl = new ImuControl(this); // unused

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

	/*
	 * change this BAFilterChipConfigChain to real BAFilterChipCongfigChain important ones.
	 * https://docs.google.com/spreadsheet/ccc?key=0AjvXOhBHjRhedEQtTFBUOHY1NzVPZ3VxdWZCcklrbnc#gid=10
	 */
	public class BAFilterChipConfigChain extends DavisConfig.DavisChipConfigChain {
		// Config Bits
		OnchipConfigBit resetCalibNeuron = new OnchipConfigBit(chip, "ResetCalibNeuron", 0,
			"turns the bias generator integrate and fire calibration neuron off", true);
		OnchipConfigBit typeNCalibNeuron = new OnchipConfigBit(
			chip,
			"TypeNCalibNeuron",
			1,
			"make the bias generator intgrate and fire calibration neuron configured to measure N type biases; otherwise measures P-type currents",
			false);
		OnchipConfigBit useAOut = new OnchipConfigBit(chip, "UseAOut", 2,
			"turn the pads for the analog MUX outputs on", false);
		OnchipConfigBit chipIDX0 = new OnchipConfigBit(chip, "chipIDX0", 3, "chipIDX0", false);
		OnchipConfigBit chipIDX1 = new OnchipConfigBit(chip, "chipIDX1", 4, "chipIDX1", false);
		OnchipConfigBit AMCX0 = new OnchipConfigBit(chip, "AMCX0", 5, "AMCX0", false);
		OnchipConfigBit AMCX1 = new OnchipConfigBit(chip, "AMCX1", 6, "AMCX1", false);
		OnchipConfigBit AMDX0 = new OnchipConfigBit(chip, "AMDX0", 7, "AMDX0", false);
		OnchipConfigBit AMDX1 = new OnchipConfigBit(chip, "AMDX1", 8, "AMDX1", false);
		OnchipConfigBit chipIDY0 = new OnchipConfigBit(chip, "chipIDY0", 9, "chipIDY0", false);
		OnchipConfigBit chipIDY1 = new OnchipConfigBit(chip, "chipIDY1", 10, "chipIDY1", false);
		OnchipConfigBit AMCY0 = new OnchipConfigBit(chip, "AMCY0", 11, "AMCY0", false);
		OnchipConfigBit AMCY1 = new OnchipConfigBit(chip, "AMCY1", 12, "AMCY1", false);
		OnchipConfigBit AMDY0 = new OnchipConfigBit(chip, "AMDY0", 13, "AMDY0", false);
		OnchipConfigBit AMDY1 = new OnchipConfigBit(chip, "AMDY1", 14, "AMDY1", false);

		public BAFilterChipConfigChain(Chip chip) {
			super(chip);
			getHasPreferenceList().add(this);

			TOTAL_CONFIG_BITS = 24;

			configBits = new OnchipConfigBit[TOTAL_CONFIG_BITS];
			configBits[0] = resetCalibNeuron;
			configBits[1] = typeNCalibNeuron;
			configBits[2] = useAOut;
			configBits[3] = chipIDX0;
			configBits[4] = chipIDX1;
			configBits[5] = AMCX0;
			configBits[6] = AMCX1;
			configBits[7] = AMDX0;
			configBits[8] = AMDX1;
			configBits[9] = chipIDY0;
			configBits[10] = chipIDY1;
			configBits[11] = AMCY0;
			configBits[12] = AMCY1;
			configBits[13] = AMDY0;
			configBits[14] = AMDY1;

			for (OnchipConfigBit b : configBits) {
				if (b != null) {
					b.addObserver(this);
				}
			}

			amuxes = new OutputMux[4];
			amuxes[0] = new AnalogOutputMux(1);
			amuxes[1] = new AnalogOutputMux(2);
			amuxes[2] = new AnalogOutputMux(3);
			amuxes[3] = new AnalogOutputMux(4);

			dmuxes = new OutputMux[4];
			dmuxes[0] = new DigitalOutputMux(1);
			dmuxes[1] = new DigitalOutputMux(2);
			dmuxes[2] = new DigitalOutputMux(3);
			dmuxes[3] = new DigitalOutputMux(4);

			bmuxes = new OutputMux[1];
			bmuxes[0] = new DigitalOutputMux(0);

			muxes.clear();
			muxes.addAll(Arrays.asList(bmuxes));
			muxes.addAll(Arrays.asList(dmuxes)); // 4 digital muxes, first in list since at end of chain - bits must be
			// sent first, before any biasgen bits
			muxes.addAll(Arrays.asList(amuxes)); // finally send the 3 voltage muxes

			for (OutputMux m : muxes) {
				m.addObserver(this);
				m.setChip(chip);
			}

			bmuxes[0].setName("BiasOutMux");

			bmuxes[0].put(0, "IFThrBn");
			bmuxes[0].put(1, "AEPuYBp");
			bmuxes[0].put(2, "AEPuXBp");
			bmuxes[0].put(3, "LColTimeout");
			bmuxes[0].put(4, "AEPdBn");
			bmuxes[0].put(5, "RefrBp");
			bmuxes[0].put(6, "PrSFBp");
			bmuxes[0].put(7, "PrBp");
			bmuxes[0].put(8, "PixInvBn");
			bmuxes[0].put(9, "LocalBufBn");
			bmuxes[0].put(10, "ApsROSFBn");
			bmuxes[0].put(11, "DiffCasBnc");
			bmuxes[0].put(12, "ApsCasBpc");
			bmuxes[0].put(13, "OffBn");
			bmuxes[0].put(14, "OnBn");
			bmuxes[0].put(15, "DiffBn");

			dmuxes[0].setName("DigMux3");
			dmuxes[1].setName("DigMux2");
			dmuxes[2].setName("DigMux1");
			dmuxes[3].setName("DigMux0");

			for (int i = 0; i < 4; i++) {
				dmuxes[i].put(0, "DXTest<5>");
				dmuxes[i].put(1, "DXTest<6>");
				dmuxes[i].put(2, "LatchXTest");
				dmuxes[i].put(3, "PXselT<127>");
				dmuxes[i].put(4, "DYTest<5>");
				dmuxes[i].put(5, "DYTest<6>");
				dmuxes[i].put(6, "LatchYTest");
				dmuxes[i].put(7, "PYselT<127>");
				dmuxes[i].put(8, "biasCalibSpike");
				dmuxes[i].put(9, "RsTest");
				dmuxes[i].put(10, "PselTest");
				dmuxes[i].put(11, "PoutTest");
				dmuxes[i].put(12, "PoutTest");
				dmuxes[i].put(13, "PoutTest");

			}

			dmuxes[3].put(14, "PselTest");
			dmuxes[3].put(15, "PoutTest");
			dmuxes[2].put(14, "biasCalibSpike");
			dmuxes[2].put(15, "RsTest");
			dmuxes[1].put(14, "PoutTest");
			dmuxes[1].put(15, "PoutTest");
			dmuxes[0].put(14, "PoutTest");
			dmuxes[0].put(15, "PoutTest");

			amuxes[0].setName("AnaMux3");
			amuxes[1].setName("AnaMux2");
			amuxes[2].setName("AnaMux1");
			amuxes[3].setName("AnaMux0");

                        amuxes[0].put(0, "apsOUT<22>");
			amuxes[0].put(1, "apsOUT<23>");
			amuxes[0].put(2, "apsOUT<25>");
                        amuxes[0].put(3, "apsOUT<24>");
			amuxes[0].put(4, "out1Test");
			amuxes[0].put(5, "VfollowerBn");
                        amuxes[0].put(6, " apsOUT<9> ");
			amuxes[0].put(7, " apsOUT<8> ");
                        amuxes[1].put(0, " VcapTest");
			amuxes[1].put(1, " apsOUT<7>");
			amuxes[1].put(2, " apsOUT<7>");
                        amuxes[1].put(3, " apsOUT<7>");
			amuxes[1].put(4, " apsOUT<7>");
			amuxes[1].put(5, " apsOUT<7>");
                        amuxes[1].put(6, "apsOUT<7>");
			amuxes[1].put(7, "apsOUT<7>"); 
                        amuxes[2].put(0, "VinTest");
			amuxes[2].put(1, "apsOUT<7>");
			amuxes[2].put(2, "apsOUT<7>");
                        amuxes[2].put(3, "apsOUT<7>");
			amuxes[2].put(4, "apsOUT<7>");
			amuxes[2].put(5, "apsOUT<7>");
                        amuxes[2].put(6, "apsOUT<7>");
			amuxes[2].put(7, "apsOUT<7>");
                        amuxes[3].put(0, "apsOUT<7>");
			amuxes[3].put(1, "apsOUT<7>");
			amuxes[3].put(2, "apsOUT<7>");
                        amuxes[3].put(3, "apsOUT<7>");
			amuxes[3].put(4, "apsOUT<7>");
			amuxes[3].put(5, "CalibNeuronVmAO");
                        amuxes[3].put(6, "calibNeuron");
			amuxes[3].put(7, "CalibNeuronVmAO");


			
		}
	}
}
