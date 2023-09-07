/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeSupport;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;
import java.util.logging.Level;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import eu.seebetter.ini.chips.davis.imu.ImuAccelScale;
import eu.seebetter.ini.chips.davis.imu.ImuControl;
import eu.seebetter.ini.chips.davis.imu.ImuControlPanel;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.stereopsis.MultiCameraBiasgenHardwareInterface;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.PropertyTooltipSupport;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Config class for SciDVS
 *
 * @author rpgraca
 */
public class SciDVSConfig extends DavisConfig implements DavisDisplayConfigInterface, DavisTweaks, ChipControlPanel, RemoteControlled {
    // these pots for DVSTweaks
    protected AddressedIPotCF refrChAmp, lpBuf;

    public SciDVSConfig(final Chip chip) {
        super(chip);
        setName("SciDVSConfig");

        // masterbias
        getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs
        getMasterbias().setMultiplier(4); // =45 correct for dvs320
        getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output
        getMasterbias().setRExternal(100e3f); // biasgen on all SeeBetter chips designed for 100kOhn resistor that makes
        // 389nA master current
        getMasterbias().setRInternal(0); // no on-chip resistor is used
        getMasterbias().addObserver(this); // changes to masterbias come back to update() here

        ipots = new AddressedIPotArray(this);

		ipots.addPot(new TowerOnChip6BitVDAC(this, "VgMfb", 0, 0,
			"Gate voltage of the photoreceptor feedback transistor."));
<<<<<<< HEAD
<<<<<<< HEAD
		ipots.addPot(new TowerOnChip6BitVDAC(this, "VCascPr", 1, 0,
=======
		ipots.addPot(new TowerOnChip6BitVDAC(this, "VgCascPr", 1, 0,
>>>>>>> 1f8fdb3aa (started scidvs)
=======
		ipots.addPot(new TowerOnChip6BitVDAC(this, "VCascPr", 1, 0,
>>>>>>> 081508329 (fixed VCascPr pot name)
			"Gate voltage of Photoreceptor feedback amplifier cascode transistor."));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefHigh", 2, 0, "on-chip column-parallel APS ADC upper conversion limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefLow", 3, 0, "on-chip column-parallel APS ADC ADC lower limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "apsOverflowLevel", 4, 0,
			"Sets reset level gate voltage of APS reset FET to prevent overflow causing DVS events"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "AdcTestVoltage", 5, 0, "Voltage supply for testing the ADC"));

        diff = SciDVSConfig.addAIPot(ipots, this, "DiffBn,8,n,normal,differencing amp");
        diffOff = SciDVSConfig.addAIPot(ipots, this, "OffBn,9,n,normal,DVS darker threshold");
        diffOn = SciDVSConfig.addAIPot(ipots, this, "OnBn,10,n,normal,DVS brighter threshold");
        SciDVSConfig.addAIPot(ipots, this, "PixInvBn,11,n,normal,Pixel request inversion static inverter bias");
        SciDVSConfig.addAIPot(ipots, this, "RefrMirrAmpBp,12,p,normal,DVS Mirror Amplifier reset time (refractory period) current");
        refrChAmp = SciDVSConfig.addAIPot(ipots, this, "RefrChAmpBp,13,p,normal,DVS Change Amplifier reset time (refractory period) current");
        SciDVSConfig.addAIPot(ipots, this, "ApsROSFBn,14,n,normal,APS readout source follower bias");
        SciDVSConfig.addAIPot(ipots, this, "IFRefrBn,15,n,normal,Integrate and fire intensity neuron refractory period bias current");
        SciDVSConfig.addAIPot(ipots, this, "IFThrBn,16,n,normal,Integrate and fire intensity neuron threshold");
        SciDVSConfig.addAIPot(ipots, this, "LocalBufBn,17,n,normal,Local buffer bias");
        SciDVSConfig.addAIPot(ipots, this, "PadFollBn,18,n,normal,Follower-pad buffer bias current");
		SciDVSConfig.addAIPot(ipots, this, "ADCcompBp,19,p,normal,ADC comparator bias");
		SciDVSConfig.addAIPot(ipots, this, "ColSelLowBn,20,n,normal,Column arbiter request pull-down");
		SciDVSConfig.addAIPot(ipots, this, "DACBufBp,21,p,normal,Row request pull up");
		SciDVSConfig.addAIPot(ipots, this, "ReadoutBufBP,22,p,normal,APS readout OTA follower bias");
<<<<<<< HEAD
<<<<<<< HEAD
		SciDVSConfig.addAIPot(ipots, this, "AEPuYBp,23,p,normal,AER column pullup");
		SciDVSConfig.addAIPot(ipots, this, "AEPdYBn,24,n,normal,Request encoder pulldown static current");
		SciDVSConfig.addAIPot(ipots, this, "AEPuXBp,25,p,normal,AER row pullup");
=======
		SciDVSConfig.addAIPot(ipots, this, "AEPdBn,23,n,normal,Request encoder pulldown static current");
		SciDVSConfig.addAIPot(ipots, this, "AEPuXBp,24,p,normal,AER column pullup");
		SciDVSConfig.addAIPot(ipots, this, "AEPuYBp,25,p,normal,AER row pullup");
>>>>>>> 1f8fdb3aa (started scidvs)
=======
		SciDVSConfig.addAIPot(ipots, this, "AEPuYBp,23,p,normal,AER column pullup");
		SciDVSConfig.addAIPot(ipots, this, "AEPdYBn,24,n,normal,Request encoder pulldown static current");
		SciDVSConfig.addAIPot(ipots, this, "AEPuXBp,25,p,normal,AER row pullup");
>>>>>>> fea6471a8 (SciDVS GAER mostly working)


        pr = SciDVSConfig.addAIPot(ipots, this, "PrBp,30,p,normal,Photoreceptor bias current");
        lpBuf = SciDVSConfig.addAIPot(ipots, this, "PrBufBp,31,p,normal,Photoreceptor buffer (low-pass) bias current");
        SciDVSConfig.addAIPot(ipots, this, "PrMirrBn,32,n,normal,Photoreceptor mirror amplifier bias current");

		SciDVSConfig.addAIPot(ipots, this, "biasBuffer,34,n,normal,special buffer bias ");

        setPotArray(ipots);

        // shifted sources
<<<<<<< HEAD
<<<<<<< HEAD
        ssp.setAddress(35);
        ssn.setAddress(36);
=======
        ssp.setAddress(36);
        ssn.setAddress(35);
>>>>>>> 1f8fdb3aa (started scidvs)
=======
        ssp.setAddress(35);
        ssn.setAddress(36);
>>>>>>> c62848e98 (changed Chip.nShortGroup to Chip.ShortGroup)

        ssBiases[0] = ssp;
        ssBiases[1] = ssn;

        chipControl.clear();
        // Chip diagnostic chain
        chipControl.add(new SPIConfigInt("Chip.DigitalMux0", "Digital multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 128, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.DigitalMux1", "Digital multiplexer 1 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 129, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.DigitalMux2", "Digital multiplexer 2 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 130, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.DigitalMux3", "Digital multiplexer 3 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 131, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.AnalogMux0", "Analog multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 132, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.AnalogMux1", "Analog multiplexer 1 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 133, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.AnalogMux2", "Analog multiplexer 2 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 134, 4, 0, this));
        chipControl.add(new SPIConfigInt("Chip.BiasMux0", "Bias multiplexer 0 (debug).", CypressFX3.FPGA_CHIPBIAS, (short) 135, 4, 0, this));

        chipControl.add(new SPIConfigInt("Chip.GAERDelay", "Controls the number of cycles for latching events in the actrive column.", CypressFX3.FPGA_CHIPBIAS, (short) 136, 8, 3, this));

        chipControl.add(new SPIConfigBit("Chip.ResetCalibNeuron", "Turn off the integrate and fire calibration neuron (bias generator).", CypressFX3.FPGA_CHIPBIAS, (short) 137, true, this));
        chipControl.add(new SPIConfigBit("Chip.TypeNCalibNeuron", "Make the integrate and fire calibration neuron configured to measure N type biases; otherwise measures P-type currents.", CypressFX3.FPGA_CHIPBIAS, (short) 138, false, this));
        chipControl.add(new SPIConfigBit("Chip.ResetTestPixel", "Keep the text pixel in reset.", CypressFX3.FPGA_CHIPBIAS, (short) 139, true, this));
        chipControl.add(new SPIConfigBit("Chip.AERnArow", "Use nArow in the AER state machine.", CypressFX3.FPGA_CHIPBIAS, (short) 140, false, this));
        chipControl.add(new SPIConfigBit("Chip.UseAOut", "Turn the pads for the analog MUX outputs on.", CypressFX3.FPGA_CHIPBIAS, (short) 141, false, this));
        chipControl.add(new SPIConfigBit("Chip.ResetShorted", "Keep all pixels in a group of 2x2 in reset state but one.", CypressFX3.FPGA_CHIPBIAS, (short) 142, false, this));
        chipControl.add(new SPIConfigBit("Chip.nDisableMirr", "Activate pre-amplifier.", CypressFX3.FPGA_CHIPBIAS, (short) 143, false, this));
<<<<<<< HEAD
<<<<<<< HEAD
        chipControl.add(new SPIConfigBit("Chip.ShortGroup", "Short-circuit pxels in groups of 2x2.", CypressFX3.FPGA_CHIPBIAS, (short) 144, true, this));
=======
        chipControl.add(new SPIConfigBit("Chip.nShortGroup", "Short-circuit pixels in groups of 2x2.", CypressFX3.FPGA_CHIPBIAS, (short) 144, true, this));
>>>>>>> 1f8fdb3aa (started scidvs)
=======
        chipControl.add(new SPIConfigBit("Chip.ShortGroup", "Short-circuit pxels in groups of 2x2.", CypressFX3.FPGA_CHIPBIAS, (short) 144, true, this));
>>>>>>> c62848e98 (changed Chip.nShortGroup to Chip.ShortGroup)
		chipControl.add(new SPIConfigBit("Chip.SelectGrayCounter", "Select internal gray counter, if disabled, external gray code is used.", CypressFX3.FPGA_CHIPBIAS, (short) 145, true, this));
		chipControl.add(new SPIConfigBit("Chip.TestADC", "Pass ADC Test Voltage to internal ADC instead of pixel voltage.", CypressFX3.FPGA_CHIPBIAS, (short) 146, false, this));

        for (final SPIConfigValue cfgVal : chipControl) {
            cfgVal.addObserver(this);
            allPreferencesList.add(cfgVal);
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
