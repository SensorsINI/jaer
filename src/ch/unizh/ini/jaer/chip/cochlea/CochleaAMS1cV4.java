/*
 * CochleaAMS1cV4.java
 *
 * Created on November 7, 2006, 11:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * Copyright November 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.chip.cochlea;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Extends Shih-Chii Liu's AMS cochlea AER chip to
 * add bias generator interface,
 * to be used when using the on-chip bias generator and the on-board DACs.
 * This board also includes off-chip ADC for reading microphone inputs and scanned cochlea outputs.
 * The board also includes for the first time a shift-register based CPLD configuration register to configure CPLD
 * functions.
 * Also implements ConfigBits, Scanner, and Equalizer configuration.
 *
 * @author tobi
 */
@Description("Binaural AER silicon cochlea with 64 channels and 8 ganglion cells of two types per channel with many fixes to CochleaAMS1b")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CochleaAMS1cV4 extends CochleaAMS1c {
	/** Creates a new instance of CochleaAMS1c */
	public CochleaAMS1cV4() {
		super();

		setBiasgen((ams1cbiasgen = new CochleaAMS1cV4.BiasgenV4(this)));
	}

	@Override
	public void setHardwareInterface(final HardwareInterface hardwareInterface) {
		this.hardwareInterface = hardwareInterface;
		try {
			if (getBiasgen() == null) {
				setBiasgen(new CochleaAMS1cV4.BiasgenV4(this));
			}
			else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		}
		catch (ClassCastException e) {
			System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	public class BiasgenV4 extends CochleaAMS1c.Biasgen {
		public BiasgenV4(Chip chip) {
			super(chip);
			setName("CochleaAMS1cV4.BiasgenV4");

			vpots = new PotArray(this);

			// public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int
			// displayPosition, String tooltipString) {
			
			// top dac in schem/layout, first 16 channels of 32 total
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "VcondVt", dac, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets VT of conductance neuron"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "VthAGC", dac, 1, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets input to diffpair that generates VQ"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vth4", dac, 2, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets high VT for LPF neuron"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefo", dac, 3, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets src for output of CM LPF"));
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefpreamp", dac, 5, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets virtual group of microphone drain preamp"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefhres", dac, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets source of terminator xtor in diffusor"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefreadout", dac, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets reference for readout amp"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vioffbpfn", dac, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets DC level for BPF input"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "PreampAGCThreshold (TH)", dac, 9, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Threshold for microphone preamp AGC gain reduction turn-on"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vterm", dac, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current of terminator xtor in diffusor"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefract", dac, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets refractory period of neuron"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "NeuronVleak", dac, 12, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets leak current for neuron - not connected on board"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "NeuronRp", dac, 13, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets bias current of neuron comparator- overrides onchip bias"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "DCOutputHighLevel", dac, 14, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Microphone DC output level to cochlea chip - upper value of resistive divider"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "DCOutputLowLevel", dac, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Microphone DC output level to cochlea chip - lower value of resistive divider"));
			
			// bot DAC in schem/layout, 2nd 16 channels
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vtau", dac, 16, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets tau of forward amp in SOS"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vq", dac, 17, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets tau of feedback amp in SOS"));
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 18, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 19, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 20, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 21, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 22, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			//vpots.addPot(new VPot(CochleaAMS1cV4.this, "", dac, 23, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "")); Not used
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vpf", dac, 24, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current for scanner follower"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefn2", dac, 25, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets DC gain gain cascode bias in BPF"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vdd1", dac, 26, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets up power to on-chip DAC"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vref", dac, 27, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets src for input of CM LPF"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vrefn", dac, 28, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets cascode bias in BPF"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vth1", dac, 29, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets low VT for LPF neuron"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "VAIO", dac, 30, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets tau of CLBT for ref current"));
			vpots.addPot(new VPot(CochleaAMS1cV4.this, "Vgain", dac, 31, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets bias for differencing amp in BPF/LPF"));


			setBatchEditOccurring(true);
			loadPreferences();
			setBatchEditOccurring(false);
		}
	}
}
