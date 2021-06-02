package ch.unizh.ini.jaer.chip.sampleprob;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cRollingCochleagramADCDisplayMethod;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import ch.unizh.ini.jaer.chip.cochlea.CochleaChip;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP;
import ch.unizh.ini.jaer.config.AbstractConfigValue;
import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CochleaFX3HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3.SPIConfigSequence;

@Description("Probabilistic Sample circuit")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SampleProb extends CochleaChip implements Observer {
	/** Creates a new instance of SampleProb */
	public SampleProb() {
		super();
		addObserver(this);

		setName("SampleProb");
		setEventClass(CochleaAMSEvent.class);

		setSizeX(16);
		setSizeY(1);
		setNumCellTypes(1);

		setRenderer(new CochleaLP.Renderer(this));
		setBiasgen(new SampleProb.Biasgen(this));
		setEventExtractor(new SampleProb.Extractor(this));

		getCanvas().setBorderSpacePixels(40);
		getCanvas().addDisplayMethod(new CochleaAMS1cRollingCochleagramADCDisplayMethod(getCanvas()));
	}

	/**
	 * Updates AEViewer specialized menu items according to capabilities of
	 * HardwareInterface.
	 *
	 * @param o
	 *            the observable, i.e. this Chip.
	 * @param arg
	 *            the argument (e.g. the HardwareInterface).
	 */
	@Override
	public void update(final Observable o, final Object arg) {
		// Nothing to do here.
	}

	/**
	 * overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
	 * Sets the hardware interface and the bias generators hardware interface
	 *
	 * @param hardwareInterface
	 *            the interface
	 */
	@Override
	public void setHardwareInterface(final HardwareInterface hardwareInterface) {
		this.hardwareInterface = hardwareInterface;
		try {
			if (getBiasgen() == null) {
				setBiasgen(new SampleProb.Biasgen(this));
			}
			else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		}
		catch (final ClassCastException e) {
			log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements net.sf.jaer.biasgen.ChipControlPanel {
		// All preferences, excluding biases.
		private final List<AbstractConfigValue> allPreferencesList = new ArrayList<>();

		// Preferences by category.
		final List<SPIConfigValue> aerControl = new ArrayList<>();
		final List<SPIConfigValue> chipControl = new ArrayList<>();

		/**
		 * Three DACs, 16 channels. Internal 1.25V reference is used, so VOUT in range 0-2.5V. VDD is 3.3V.
		 */
		private final DAC dac1 = new DAC(16, 14, 0, 2.5f, 3.3f);
		private final DAC dac2 = new DAC(16, 14, 0, 2.5f, 3.3f);
		private final DAC dac3 = new DAC(16 + 32, 14, 0, 2.5f, 3.3f); // +32 for special random DAC upper/lower limit.

		final SPIConfigBit dacRun;
		final SPIConfigBit dacRandomRun;
		final SPIConfigBit dacRandomUSBRun;

		// All bias types.
		final SPIConfigBit biasForceEnable;
		final IPotArray ipots = new IPotArray(this);
		final PotArray vpots = new PotArray(this);

		public Biasgen(final Chip chip) {
			super(chip);
			setName("SampleProb.Biasgen");

			// Use shift-register number as address.
			ipots.addPot(new SimpleIPot(this, "VpdbiasTxBn", 5, IPot.Type.NORMAL, IPot.Sex.N, 0, 0, "VpdbiasTxBn"));
			ipots.addPot(new SimpleIPot(this, "VaBp", 6, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 1, "VaBp"));
			ipots.addPot(new SimpleIPot(this, "VbBp", 7, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 2, "VbBp"));
			ipots.addPot(new SimpleIPot(this, "VcBp", 8, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 3, "VcBp"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp15", 9, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 4, "VHazardresBp15"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp14", 10, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 5, "VHazardresBp14"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp13", 11, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 6, "VHazardresBp13"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp12", 12, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 7, "VHazardresBp12"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp11", 13, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 8, "VHazardresBp11"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp10", 14, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 9, "VHazardresBp10"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp9", 15, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 10, "VHazardresBp9"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp8", 16, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 11, "VHazardresBp8"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp7", 17, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 12, "VHazardresBp7"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp6", 18, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 13, "VHazardresBp6"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp5", 19, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 14, "VHazardresBp5"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp4", 20, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 15, "VHazardresBp4"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp3", 21, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 16, "VHazardresBp3"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp2", 22, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 17, "VHazardresBp2"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp1", 23, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 18, "VHazardresBp1"));
			ipots.addPot(new SimpleIPot(this, "VHazardresBp0", 24, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 19, "VHazardresBp0"));
			ipots.addPot(new SimpleIPot(this, "VreqPuTxBp", 25, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 20, "VreqPuTxBp"));
			ipots.addPot(new SimpleIPot(this, "VfollBn", 26, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 21, "VfollBn"));
			ipots.addPot(new SimpleIPot(this, "VRVGBn", 27, SimpleIPot.Type.NORMAL, SimpleIPot.Sex.N, 0, 22, "VRVGBn"));

			setPotArray(ipots);

			// DAC1 channels (16) (SSN_DAC1)
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD00", dac1, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD01", dac1, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD02", dac1, 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD03", dac1, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD04", dac1, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD05", dac1, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD06", dac1, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD07", dac1, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD08", dac1, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD09", dac1, 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD10", dac1, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD11", dac1, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD12", dac1, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD13", dac1, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VHazardrefD14", dac1, 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "Vsrc1Bns", dac1, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));

			// DAC2 channels (16) (SSN_DAC2)
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VRVGrefh", dac2, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VRVGrefl", dac2, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VRVGrefm", dac2, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new SimpleVPot(getChip(), "NC", dac2, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));

			// DAC3 channels (16) (SSN_DAC3)
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt00", dac3, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt01", dac3, 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt02", dac3, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt03", dac3, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt04", dac3, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt05", dac3, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt06", dac3, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt07", dac3, 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt08", dac3, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt09", dac3, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt10", dac3, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt11", dac3, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt12", dac3, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt13", dac3, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt14", dac3, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "VnoiseExt15", dac3, 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));

			// Special DAC configuration (random noise DAC). Kept here with other DAC-related config for convenience.
			vpots.addPot(new SimpleVPot(getChip(), "00RandomVMax", dac3, 16 + 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "00RandomVMin", dac3, 32 + 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "01RandomVMax", dac3, 16 + 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "01RandomVMin", dac3, 32 + 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "02RandomVMax", dac3, 16 + 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "02RandomVMin", dac3, 32 + 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "03RandomVMax", dac3, 16 + 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "03RandomVMin", dac3, 32 + 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "04RandomVMax", dac3, 16 + 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "04RandomVMin", dac3, 32 + 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "05RandomVMax", dac3, 16 + 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "05RandomVMin", dac3, 32 + 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "06RandomVMax", dac3, 16 + 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "06RandomVMin", dac3, 32 + 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "07RandomVMax", dac3, 16 + 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "07RandomVMin", dac3, 32 + 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "08RandomVMax", dac3, 16 + 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "08RandomVMin", dac3, 32 + 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "09RandomVMax", dac3, 16 + 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "09RandomVMin", dac3, 32 + 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "10RandomVMax", dac3, 16 + 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "10RandomVMin", dac3, 32 + 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "11RandomVMax", dac3, 16 + 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "11RandomVMin", dac3, 32 + 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "12RandomVMax", dac3, 16 + 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "12RandomVMin", dac3, 32 + 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "13RandomVMax", dac3, 16 + 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "13RandomVMin", dac3, 32 + 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "14RandomVMax", dac3, 16 + 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "14RandomVMin", dac3, 32 + 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "15RandomVMax", dac3, 16 + 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new SimpleVPot(getChip(), "15RandomVMin", dac3, 32 + 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));

			// New logic SPI configuration values.
			// DAC control
			dacRun = new SPIConfigBit("DACRun", "Enable external DAC.", CypressFX3.FPGA_DAC, (short) 0, false, this);
			dacRun.addObserver(this);
			allPreferencesList.add(dacRun);

			// Special DAC configuration (random noise DAC). Kept here with other DAC-related config for convenience.
			dacRandomRun = new SPIConfigBit("DACRandomRun", "Send random values to DAC 3 for random noise generation.", CypressFX3.FPGA_DAC,
				(short) 15, false, this);
			dacRandomRun.addObserver(this);
			allPreferencesList.add(dacRandomRun);

			dacRandomUSBRun = new SPIConfigBit("DACRandomUSBRun", "Send random values out via USB.", CypressFX3.FPGA_DAC, (short) 14, false,
				this);
			dacRandomUSBRun.addObserver(this);
			allPreferencesList.add(dacRandomUSBRun);

			// Multiplexer module
			biasForceEnable = new SPIConfigBit("ForceBiasEnable", "Force the biases to be always ON.", CypressFX3.FPGA_MUX, (short) 3,
				false, this);
			biasForceEnable.addObserver(this);
			allPreferencesList.add(biasForceEnable);

			// Generic AER from chip
			aerControl.add(new SPIConfigBit("AERRun", "Run the main AER state machine.", CypressFX3.FPGA_DVS, (short) 3, false, this));
			aerControl.add(new SPIConfigInt("AERAckDelay", "Delay AER ACK by this many cycles.",
				CypressFX3.FPGA_DVS, (short) 4, 12, 0, this));
			aerControl.add(new SPIConfigInt("AERAckExtension", "Extend AER ACK by this many cycles.",
				CypressFX3.FPGA_DVS, (short) 6, 12, 0, this));
			aerControl.add(new SPIConfigBit("AERWaitOnTransferStall",
				"Whether the AER state machine should wait,<br> or continue servicing the AER bus when the FIFOs are full.",
				CypressFX3.FPGA_DVS, (short) 8, false, this));
			aerControl.add(new SPIConfigBit("AERExternalAERControl",
				"Do not control/ACK the AER bus anymore, <br>but let it be done by an external device.",
				CypressFX3.FPGA_DVS, (short) 10, false, this));

			for (final SPIConfigValue cfgVal : aerControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// Additional chip configuration
			chipControl.add(new SPIConfigInt("MasterBias", "", CypressFX3.FPGA_CHIPBIAS, (short) 0, 8, 0, this));
			chipControl.add(new SPIConfigInt("SelSpikeExtend", "", CypressFX3.FPGA_CHIPBIAS, (short) 1, 3, 0, this));
			chipControl.add(new SPIConfigInt("SelHazardIV", "", CypressFX3.FPGA_CHIPBIAS, (short) 2, 8, 0, this));
			chipControl.add(new SPIConfigBit("SelCH", "", CypressFX3.FPGA_CHIPBIAS, (short) 3, false, this));
			chipControl.add(new SPIConfigBit("SelNS", "", CypressFX3.FPGA_CHIPBIAS, (short) 4, false, this));
			chipControl.add(new SPIConfigBit("ClockEnable", "Enable clock generation for RNG.",
				CypressFX3.FPGA_CHIPBIAS, (short) 40, false, this));
			chipControl.add(new SPIConfigInt("ClockPeriod", "Period of RNG clock in cycles at 120MHz.",
				CypressFX3.FPGA_CHIPBIAS, (short) 41, 20, 0, this));
			chipControl.add(new SPIConfigBit("UseLandscapeSamplingVerilog",
				"Use Verilog LandscapeSampling module instead of externally loaded values.",
				CypressFX3.FPGA_CHIPBIAS, (short) 42, false, this));

			for (final SPIConfigValue cfgVal : chipControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			setBatchEditOccurring(true);
			loadPreferences();
			setBatchEditOccurring(false);

			try {
				sendConfiguration(this);
			}
			catch (final HardwareInterfaceException ex) {
				net.sf.jaer.biasgen.Biasgen.log.log(Level.SEVERE, null, ex);
			}
		}

		@Override
		final public void loadPreferences() {
			super.loadPreferences();

			if (allPreferencesList != null) {
				for (final HasPreference hp : allPreferencesList) {
					hp.loadPreference();
				}
			}

			if (ipots != null) {
				ipots.loadPreferences();
			}

			if (vpots != null) {
				vpots.loadPreferences();
			}
		}

		@Override
		public void storePreferences() {
			for (final HasPreference hp : allPreferencesList) {
				hp.storePreference();
			}

			ipots.storePreferences();

			vpots.storePreferences();

			super.storePreferences();
		}

		@Override
		public JPanel buildControlPanel() {
			final JPanel panel = new JPanel();
			panel.setLayout(new BorderLayout());
			final JComponent c = new SampleProbControlPanel(SampleProb.this);
			c.setPreferredSize(new Dimension(1000, 800));
			panel.add(new JScrollPane(c), BorderLayout.CENTER);
			return panel;
		}

		@Override
		public void setHardwareInterface(final BiasgenHardwareInterface hw) {
			if (hw == null) {
				hardwareInterface = null;
				return;
			}

			hardwareInterface = hw;

			try {
				sendConfiguration();
			}
			catch (final HardwareInterfaceException ex) {
				net.sf.jaer.biasgen.Biasgen.log.warning(ex.toString());
			}
		}

		/**
		 * The central point for communication with HW from biasgen. All objects in Biasgen are Observables
 and appendCopy Biasgen.this as Observer. They then call notifyObservers when their state changes.
		 *
		 * @param observable
		 *            IPot, DAC, etc
		 * @param object
		 *            notifyChange used at present
		 */
		@Override
		public synchronized void update(final Observable observable, final Object object) {
			if (getHardwareInterface() != null) {
				final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();

				try {
					if (observable instanceof IPot) {
						final IPot iPot = (IPot) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getShiftRegisterNumber(), iPot.getBitValue());
					}
					else if (observable instanceof VPot) {
						final VPot vPot = (VPot) observable;

						int dacNumber;

						if (vPot.getDac() == dac1) {
							dacNumber = 0; // DAC1.
						}
						else if (vPot.getDac() == dac2) {
							dacNumber = 1; // DAC2.
						}
						else {
							dacNumber = 2; // DAC3.
						}

						// Support for random DAC3 limits. These are handled by different SPI addresses.
						if ((dacNumber == 2) && (vPot.getChannel() >= 16)) {
							fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) vPot.getChannel(), vPot.getBitValue());
						}
						else {
							final SPIConfigSequence configSequence = fx3HwIntf.new SPIConfigSequence();

							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 1, dacNumber); // Select DAC.

							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 2, 0x03); // Select input data
																							// register.
							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 3, vPot.getChannel());
							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 5, vPot.getBitValue());

							// Toggle SET flag.
							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 1);
							configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 0);

							// Commit configuration.
							configSequence.sendConfigSequence();
						}

						// Wait 1ms to ensure operation is completed.
						try {
							Thread.sleep(1);
						}
						catch (final InterruptedException e) {
							// Nothing to do here.
						}
					}
					else if (observable instanceof SPIConfigValue) {
						final SPIConfigValue cfgVal = (SPIConfigValue) observable;

						fx3HwIntf.spiConfigSend(cfgVal.getModuleAddr(), cfgVal.getParamAddr(), cfgVal.get());
					}
					else {
						super.update(observable, object); // super (Biasgen) handles others, e.g. masterbias
					}
				}
				catch (final HardwareInterfaceException e) {
					net.sf.jaer.biasgen.Biasgen.log.warning("On update() caught " + e.toString());
				}
			}
		}

		// sends complete configuration information to multiple shift registers and off chip DACs
		public void sendConfiguration() throws HardwareInterfaceException {
			if (!isOpen()) {
				open();
			}

			for (final AbstractConfigValue spiCfg : allPreferencesList) {
				spiCfg.setChanged();
				spiCfg.notifyObservers();
			}

			for (final Pot iPot : ipots.getPots()) {
				iPot.setChanged();
				iPot.notifyObservers();
			}

			for (final Pot vPot : vpots.getPots()) {
				vPot.setChanged();
				vPot.notifyObservers();
			}
		}
	}

	/**
	 * Extract cochlea events from CochleaAMS1c including the ADC samples that are intermixed with cochlea AER data.
	 * <p>
	 * The event class returned by the extractor is CochleaAMSEvent.
	 */
	public class Extractor extends TypedEventExtractor<CochleaAMSEvent> {

		private static final long serialVersionUID = -3469492271382423090L;

		public Extractor(final AEChip chip) {
			super(chip);
		}

		/**
		 * Extracts the meaning of the raw events. This form is used to supply an output packet. This method is used for
		 * real time
		 * event filtering using a buffer of output events local to data acquisition. An AEPacketRaw may contain
		 * multiple events,
		 * not all of them have to sent out as EventPackets. An AEPacketRaw is a set(!) of addresses and corresponding
		 * timing moments.
		 *
		 * A first filter (independent from the other ones) is implemented by subSamplingEnabled and
		 * getSubsampleThresholdEventCount.
		 * The latter may limit the amount of samples in one package to say 50,000. If there are 160,000 events and
		 * there is a sub samples
		 * threshold of 50,000, a "skip parameter" set to 3. Every so now and then the routine skips with 4, so we end
		 * up with 50,000.
		 * It's an approximation, the amount of events may be less than 50,000. The events are extracted uniform from
		 * the input.
		 *
		 * @param in
		 *            the raw events, can be null
		 * @param out
		 *            the processed events. these are partially processed in-place. empty packet is returned if null is
		 *            supplied as input.
		 */
		@Override
		synchronized public void extractPacket(final AEPacketRaw in, final EventPacket<CochleaAMSEvent> out) {
			out.clear();

			if (in == null) {
				return;
			}

			final int n = in.getNumEvents();

			final int[] addresses = in.getAddresses();
			final int[] timestamps = in.getTimestamps();

			final OutputEventIterator<CochleaAMSEvent> outItr = out.outputIterator();

			for (int i = 0; i < n; i++) {
				final int addr = addresses[i];
				final int ts = timestamps[i];

				if ((addr & BasicEvent.SPECIAL_EVENT_BIT_MASK) != 0) {
					// Ignore special events generated from the WRAP_TS events.
					// Those are only useful for advancing the sparse CochleaLP.
					continue;
				}

				final CochleaAMSEvent e = outItr.nextOutput();

				// SampleProb has only either AER addresses, or special RandomDAC
				// number events, which have a special code set.
				if ((addr & CochleaFX3HardwareInterface.DATA_TYPE_RANDOMDAC) != 0) {
					// RandomDAC event: channel address + random number
					e.address = addr;
					e.timestamp = ts;
					e.x = (short) ((addr >>> 14) & 0x0F); // Channel address
					e.y = (short) (addr & 0x3FFF); // Random number
					e.type = 0;
					e.setSpecial(true);
				}
				else {
					// AER address 0-15 (16 in total). Plus 16-31 as extra.
					e.address = addr;
					e.timestamp = ts;
					e.x = (short) (addr & 0x0F);
					e.y = 0;
					e.type = 0;
					e.setSpecial(false);
				}
			}
		}
	}
}
