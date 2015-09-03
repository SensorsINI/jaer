package ch.unizh.ini.jaer.chip.sampleprob;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cRollingCochleagramADCDisplayMethod;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMSEvent;
import ch.unizh.ini.jaer.chip.cochlea.CochleaChip;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.AbstractConfigValue;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.CochleaChannel;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigBit;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigInt;
import ch.unizh.ini.jaer.chip.cochlea.CochleaLP.SPIConfigValue;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

@Description("Probabilistic Sample circuit")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SampleProb extends CochleaChip implements Observer {
	private final GLUT glut = new GLUT();

	/** Creates a new instance of SampleProb */
	public SampleProb() {
		super();
		addObserver(this);

		setName("SampleProb");
		setEventClass(CochleaAMSEvent.class);

		setSizeX(64);
		setSizeY(4);
		setNumCellTypes(4);

		setRenderer(new CochleaLP.Renderer(this));
		setBiasgen(new SampleProb.Biasgen(this));
		setEventExtractor(new SampleProb.Extractor(this));

		getCanvas().setBorderSpacePixels(40);
		getCanvas().addDisplayMethod(new CochleaAMS1cRollingCochleagramADCDisplayMethod(getCanvas()));

		for (final DisplayMethod m : getCanvas().getDisplayMethods()) {
			if ((m instanceof ChipRendererDisplayMethod) || (m instanceof SpaceTimeEventDisplayMethod)) {
				// add labels on frame of chip for these xy chip displays
				m.addAnnotator(new FrameAnnotater() {
					@Override
					public void setAnnotationEnabled(final boolean yes) {
						// Nothing to do here.
					}

					@Override
					public boolean isAnnotationEnabled() {
						return true;
					}

					// renders the string starting at x,y,z with angleDeg angle CCW from horizontal in degrees
					public void renderStrokeFontString(final GL2 gl, final float x, final float y, final float z, final float angleDeg,
						final String s) {
						final int font = GLUT.STROKE_ROMAN;
						final float scale = 2f / 104f; // chars will be about 1 pixel wide
						gl.glPushMatrix();
						gl.glTranslatef(x, y, z);
						gl.glRotatef(angleDeg, 0, 0, 1);
						gl.glScalef(scale, scale, scale);
						gl.glLineWidth(2);
						for (final char c : s.toCharArray()) {
							glut.glutStrokeCharacter(font, c);
						}
						gl.glPopMatrix();
					} // chars about 104 model units wide

					@Override
					public void annotate(final GLAutoDrawable drawable) {
						final GL2 gl = drawable.getGL().getGL2();
						gl.glPushMatrix();
						{
							gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like
							// glVertex)
							renderStrokeFontString(gl, -1, (16 / 2) - 5, 0, 90, "cell type");
							renderStrokeFontString(gl, (sizeX / 2) - 4, -3, 0, 0, "channel");
							renderStrokeFontString(gl, 0, -3, 0, 0, "hi fr");
							renderStrokeFontString(gl, sizeX - 15, -3, 0, 0, "low fr");
						}
						gl.glPopMatrix();
					}
				});
			}
		}
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

	@Override
	public void onDeregistration() {
		super.onDeregistration();
	}

	@Override
	public void onRegistration() {
		super.onRegistration();
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
			System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements net.sf.jaer.biasgen.ChipControlPanel {
		// All preferences, excluding biases.
		private final List<AbstractConfigValue> allPreferencesList = new ArrayList<>();

		// Preferences by category.
		final List<SPIConfigValue> aerControl = new ArrayList<>();
		final List<SPIConfigValue> chipDiagChain = new ArrayList<>();

		/**
		 * One DAC, 16 channels. Internal 1.25V reference is used, so VOUT in range 0-2.5V. VDD is 2.8V.
		 */
		private final DAC dac = new DAC(16, 12, 0, 2.5f, 2.8f);

		final SPIConfigBit dacRun;

		// All bias types.
		final SPIConfigBit biasForceEnable;
		final AddressedIPotArray ipots = new AddressedIPotArray(this);
		final PotArray vpots = new PotArray(this);
		final ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];

		public Biasgen(final Chip chip) {
			super(chip);
			setName("SampleProb.Biasgen");

			ipots.addPot(new AddressedIPotCF(this, "VBNIBias", 0, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 1, "IBias transistor gate"));
			ipots.addPot(new AddressedIPotCF(this, "VBNTest", 1, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 2, "Test circuits"));
			ipots.addPot(new AddressedIPotCF(this, "VBPScan", 8, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 3, "Scanner"));
			ipots.addPot(new AddressedIPotCF(this, "AEPdBn", 11, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 4, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "AEPuYBp", 14, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 5, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "BiasBuffer", 19, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 6, "Buffer bias generator"));

			setPotArray(ipots);

			// shifted sources
			final ShiftedSourceBiasCF ssp = new ShiftedSourceBiasCF(this);
			ssp.setSex(Pot.Sex.P);
			ssp.setName("SSP");
			ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
			ssp.setAddress(20);
			ssp.addObserver(this);

			final ShiftedSourceBiasCF ssn = new ShiftedSourceBiasCF(this);
			ssn.setSex(Pot.Sex.N);
			ssn.setName("SSN");
			ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
			ssn.setAddress(21);
			ssn.addObserver(this);

			ssBiases[0] = ssp;
			ssBiases[1] = ssn;

			// DAC channels (16)
			// vpots.addPot(new VPot(getChip(), "NC", dac, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new VPot(getChip(), "NC", dac, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VMID", dac, 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREF1_Filter", dac, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREF2_Filter", dac, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREF_MOD", dac, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREFH_MOD", dac, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREFL_MOD", dac, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREFH_COM", dac, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VREFL_COM", dac, 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VOCM1/2", dac, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "Resistors1", dac, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "Resistors2", dac, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new VPot(getChip(), "NC", dac, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new VPot(getChip(), "NC", dac, 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			// vpots.addPot(new VPot(getChip(), "NC", dac, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));

			// New logic SPI configuration values.
			// DAC control
			dacRun = new SPIConfigBit("DACRun", "Enable external DAC.", CypressFX3.FPGA_DAC, (short) 0, false, getPrefs());
			dacRun.addObserver(this);
			allPreferencesList.add(dacRun);

			// Multiplexer module
			biasForceEnable = new SPIConfigBit("ForceBiasEnable", "Force the biases to be always ON.", CypressFX3.FPGA_MUX, (short) 3,
				false, getPrefs());
			biasForceEnable.addObserver(this);
			allPreferencesList.add(biasForceEnable);

			// Generic AER from chip
			aerControl.add(new SPIConfigBit("TestAEREnable", "Enable Test AER output instead of normal AER.", CypressFX3.FPGA_SCANNER,
				(short) 2, false, getPrefs())); // In scanner module for convenience.
			aerControl
				.add(new SPIConfigBit("AERRun", "Run the main AER state machine.", CypressFX3.FPGA_DVS, (short) 3, false, getPrefs()));
			aerControl.add(
				new SPIConfigInt("AERAckDelay", "Delay AER ACK by this many cycles.", CypressFX3.FPGA_DVS, (short) 4, 6, 0, getPrefs()));
			aerControl.add(new SPIConfigInt("AERAckExtension", "Extend AER ACK by this many cycles.", CypressFX3.FPGA_DVS, (short) 6, 6, 0,
				getPrefs()));
			aerControl.add(new SPIConfigBit("AERWaitOnTransferStall",
				"Whether the AER state machine should wait,<br> or continue servicing the AER bus when the FIFOs are full.",
				CypressFX3.FPGA_DVS, (short) 8, false, getPrefs()));
			aerControl.add(new SPIConfigBit("AERExternalAERControl",
				"Do not control/ACK the AER bus anymore, <br>but let it be done by an external device.", CypressFX3.FPGA_DVS, (short) 10,
				false, getPrefs()));

			for (final SPIConfigValue cfgVal : aerControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// Chip diagnostic chain
			chipDiagChain.add(new SPIConfigInt("ChipResetCapConfigADM", "Reset cap configuration in ADM.", CypressFX3.FPGA_CHIPBIAS,
				(short) 128, 2, 0, getPrefs()));
			chipDiagChain.add(new SPIConfigInt("ChipDelayCapConfigADM", "Delay cap configuration in ADM.", CypressFX3.FPGA_CHIPBIAS,
				(short) 129, 3, 0, getPrefs()));
			chipDiagChain.add(new SPIConfigBit("ChipComparatorSelfOsc", "Comparator self-oscillation enable.", CypressFX3.FPGA_CHIPBIAS,
				(short) 130, false, getPrefs()));
			chipDiagChain.add(
				new SPIConfigInt("ChipLNAGainConfig", "LNA gain configuration.", CypressFX3.FPGA_CHIPBIAS, (short) 131, 3, 0, getPrefs()));
			chipDiagChain.add(new SPIConfigBit("ChipLNADoubleInputSelect", "LNA double or single input selection.",
				CypressFX3.FPGA_CHIPBIAS, (short) 132, false, getPrefs()));
			chipDiagChain.add(new SPIConfigBit("ChipTestScannerBias", "Test scanner bias enable.", CypressFX3.FPGA_CHIPBIAS, (short) 133,
				false, getPrefs()));

			for (final SPIConfigValue cfgVal : chipDiagChain) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			setBatchEditOccurring(true);
			loadPreferences();
			setBatchEditOccurring(false);
		}

		@Override
		final public void loadPreferences() {
			super.loadPreferences();

			if (allPreferencesList != null) {
				for (final HasPreference hp : allPreferencesList) {
					hp.loadPreference();
				}
			}

			if (ssBiases != null) {
				for (final ShiftedSourceBiasCF sSrc : ssBiases) {
					sSrc.loadPreferences();
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

			for (final ShiftedSourceBiasCF sSrc : ssBiases) {
				sSrc.storePreferences();
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
		 * and add Biasgen.this as Observer. They then call notifyObservers when their state changes.
		 *
		 * @param observable
		 *            IPot, DAC, etc
		 * @param object
		 *            notifyChange used at present
		 */
		@Override
		public void update(final Observable observable, final Object object) {
			// while it is sending something
			if (isBatchEditOccurring()) {
				return;
			}

			if (getHardwareInterface() != null) {
				final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();

				try {
					if (observable instanceof AddressedIPotCF) {
						final AddressedIPotCF iPot = (AddressedIPotCF) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(),
							iPot.computeCleanBinaryRepresentation());
					}
					else if (observable instanceof ShiftedSourceBiasCF) {
						final ShiftedSourceBiasCF iPot = (ShiftedSourceBiasCF) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeBinaryRepresentation());
					}
					else if (observable instanceof VPot) {
						final VPot vPot = (VPot) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) 2, 0x03); // Select input data register.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) 3, vPot.getChannel());
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) 5, vPot.getBitValue());

						// Toggle SET flag.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) 6, 1);
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_DAC, (short) 6, 0);

						// Wait 1ms to ensure operation is completed.
						try {
							Thread.sleep(1);
						}
						catch (final InterruptedException e) {
							// Nothing to do here.
						}
					}
					else if (observable instanceof SPIConfigBit) {
						final SPIConfigBit cfgBit = (SPIConfigBit) observable;

						fx3HwIntf.spiConfigSend(cfgBit.getModuleAddr(), cfgBit.getParamAddr(), (cfgBit.isSet()) ? (1) : (0));
					}
					else if (observable instanceof SPIConfigInt) {
						final SPIConfigInt cfgInt = (SPIConfigInt) observable;

						fx3HwIntf.spiConfigSend(cfgInt.getModuleAddr(), cfgInt.getParamAddr(), cfgInt.get());
					}
					else if (observable instanceof CochleaChannel) {
						final CochleaChannel chan = (CochleaChannel) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 160, chan.getChannelAddress());
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 162, chan.computeBinaryRepresentation());

						// Toggle SET flag.
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 163, 1);
						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) 163, 0);

						// Wait 2ms to ensure operation is completed.
						try {
							Thread.sleep(2);
						}
						catch (final InterruptedException e) {
							// Nothing to do here.
						}
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

			for (final ShiftedSourceBiasCF sSrc : ssBiases) {
				update(sSrc, null);
			}

			for (final Pot iPot : ipots.getPots()) {
				update(iPot, null);
			}

			for (final Pot vPot : vpots.getPots()) {
				update(vPot, null);
			}

			for (final AbstractConfigValue spiCfg : allPreferencesList) {
				update(spiCfg, null);
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

			int skipBy = 1, incEach = 0, j = 0;

			if (isSubSamplingEnabled()) {
				skipBy = n / getSubsampleThresholdEventCount();
				incEach = getSubsampleThresholdEventCount() / (n % getSubsampleThresholdEventCount());
			}

			if (skipBy == 0) {
				incEach = 0;
				skipBy = 1;
			}

			final int[] addresses = in.getAddresses();
			final int[] timestamps = in.getTimestamps();

			final OutputEventIterator<CochleaAMSEvent> outItr = out.outputIterator();

			for (int i = 0; i < n; i += skipBy) {
				final int addr = addresses[i];
				final int ts = timestamps[i];

				final CochleaAMSEvent e = outItr.nextOutput();

				e.address = addr;
				e.timestamp = ts;
				e.x = getXFromAddress(addr);
				e.y = getYFromAddress(addr);
				e.type = getTypeFromAddress(addr);

				j++;
				if (j == incEach) {
					j = 0;
					i++;
				}
			}
		}

		/**
		 * Overrides default extractor so that cochlea channels are returned,
		 * numbered from x=0 (base, high frequencies, input end) to x=63 (apex, low frequencies).
		 *
		 * @param addr
		 *            raw address.
		 * @return channel, from 0 to 63.
		 */
		@Override
		public short getXFromAddress(final int addr) {
			return (short) ((addr & 0xFC) >>> 2);
		}

		/**
		 * Overrides default extractor to spread all outputs from a tap (left/right, polarity ON/OFF) into a
		 * single unique y address that can be displayed in the 2d histogram.
		 *
		 * @param addr
		 *            the raw address
		 * @return the Y address
		 */
		@Override
		public short getYFromAddress(final int addr) {
			return (short) (addr & 0x03);
		}

		/**
		 * Overrides default extract to define type of event the same as the Y address.
		 *
		 * @param addr
		 *            the raw address.
		 * @return the type
		 */
		@Override
		public byte getTypeFromAddress(final int addr) {
			return (byte) getYFromAddress(addr);
		}
	}
}
