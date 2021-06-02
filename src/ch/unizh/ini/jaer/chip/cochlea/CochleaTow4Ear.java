package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.config.MultiBitConfigRegister;
import ch.unizh.ini.jaer.config.MultiBitValue;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import ch.unizh.ini.jaer.config.AbstractConfigValue;
import ch.unizh.ini.jaer.config.ConfigBase;
import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigInt;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import eu.seebetter.ini.chips.davis.TowerOnChip6BitVDAC;
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
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

/** 4-Ear AER silicon cochlea with 64 channels per ear. Hardware interface is CochleaFX3HardwareInterface */
@Description("4-Ear AER silicon cochlea with 64 channels per ear.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CochleaTow4Ear extends CochleaChip implements Observer {

	private final GLUT glut = new GLUT();

	/**
	 * Creates a new instance of CochleaTow4Ear
	 */
	public CochleaTow4Ear() {
		super();
		addObserver(this); // TODO: Why?

		setName("CochleaTow4Ear");
		setEventClass(CochleaTow4EarEvent.class);

		setSizeX(64); // 64 frequency channels
		setSizeY(16); // total four, ON/OFF for each ear
		setNumCellTypes(4); // ON/OFF ADM output from each ear's channel

		setRenderer(new Renderer(this));
		setBiasgen(new CochleaTow4Ear.Biasgen(this));
		setEventExtractor(new CochleaTow4Ear.Extractor(this));

		getCanvas().setBorderSpacePixels(40);
		// getCanvas().addDisplayMethod(new CochleaAMS1cRollingCochleagramADCDisplayMethod(getCanvas()));

		for (final DisplayMethod m : getCanvas().getDisplayMethods()) {
			if ((m instanceof ChipRendererDisplayMethod) || (m instanceof SpaceTimeEventDisplayMethod)) {
				// appendCopy labels on frame of chip for these xy chip displays
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

	public static class Renderer extends AEChipRenderer {

		private boolean didit = false;

		public Renderer(final AEChip chip) {
			super(chip);
		}

		@Override
		protected void checkTypeColors(final int numCellTypes) {
			if (didit) {
				return;
			}
			didit = true;
			super.checkTypeColors(numCellTypes);
			final Color[] colors = { Color.green, Color.magenta, Color.red, Color.blue };
			for (int type = 0; type < 4; type++) {
				// fill the typeColorRGBComponents array for this type from the color for this type
                // TODO: Output of getRGBColorComponents() is not used. Check why we have it here at all.
                            colors[type].getRGBColorComponents(typeColorRGBComponents[type]);
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
	 * overrides the Chip setHardware interface to construct a biasgen if one
	 * doesn't exist already. Sets the hardware interface and the bias
	 * generators hardware interface
	 *
	 * @param hardwareInterface
	 *            the interface
	 */
	@Override
	public void setHardwareInterface(final HardwareInterface hardwareInterface) {
		this.hardwareInterface = hardwareInterface;
		try {
			if (getBiasgen() == null) {
				setBiasgen(new CochleaTow4Ear.Biasgen(this));
			}
			else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		}
		catch (final ClassCastException e) {
			Chip.log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements net.sf.jaer.biasgen.ChipControlPanel {

		private CypressFX3.SPIConfigSequence configSequence;
		// All preferences, excluding biases.
		private final List<AbstractConfigValue> allPreferencesList = new ArrayList<>();
		
		// Preferences by category.
		final List<CochleaChannelConfig>[] cochleaChannels = new ArrayList[4];
		final List<SPIConfigValue> aerControl = new ArrayList<>();
		final List<SPIConfigValue> scannerControl = new ArrayList<>();
		final List<SPIConfigValue> adcControl = new ArrayList<>();
		final List<SPIConfigValue> chipControl = new ArrayList<>();

		/**
		 * Two DACs, 16 channels. Internal 1.25V reference is used, so VOUT in range 0-2.5V. VDD is 2.8V.
		 */
		private final DAC dac1 = new DAC(0, 16, 12, 0, 5.0f, 3.3f); // Ad-hoc value for Vref
		private final DAC dac2 = new DAC(1, 16, 12, 0, 5.0f, 3.3f); // Ad-hoc value for Vref

		final SPIConfigBit SelResSW;
		final SPIConfigBit dacRun;

		// All bias types.
		final SPIConfigBit biasForceEnable;
		final AddressedIPotArray ipots = new AddressedIPotArray(this);
		final PotArray vpots = new PotArray(this);
		final ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];

		public Biasgen(final Chip chip) {
			super(chip);

			// Chip Specific Initialization further on
			setName("CochleaTow4Ear.Biasgen");

			for (int i = 0; i < 4; ++i) {
				cochleaChannels[i] = new ArrayList<>();
			}
			
			// VDAC (6-bit voltage DAC)
			ipots.addPot(new TowerOnChip6BitVDAC(this, "VrefpreampBp", 0, 1, "preamp"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "Vth1", 1, 2, "neuron"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "Vth2", 2, 3, "neuron"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "VbMicCasBpc", 3, 4, "preamp"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "VbiasHF2Bn", 4, 5, "SOS"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "Vbias2n", 5, 6, "SOS"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "Vrefdiff", 6, 7, "diff"));
			ipots.addPot(new TowerOnChip6BitVDAC(this, "Vrefdiff2", 7, 8, "diff"));

			// BIASES (IPOTS, current DACs)
			ipots.addPot(new AddressedIPotCF(this, "LocalBufBn", 8, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 9, "Buffer Test-pixel"));
			ipots.addPot(new AddressedIPotCF(this, "VtauBn", 9, Type.NORMAL, Sex.na, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 10, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "Vbias1Bn", 10, Type.NORMAL, Sex.na, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 11, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "VrefractBnc4", 11, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 12, "neuron"));
			ipots.addPot(new AddressedIPotCF(this, "VrefractBnc1", 12, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 13, "neuron"));
			ipots.addPot(new AddressedIPotCF(this, "PixInvBn", 13, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 14, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "VBPScan", 14, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 15, "scanner"));
			ipots.addPot(new AddressedIPotCF(this, "VbBp1", 15, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 16, "fb Diff"));
			ipots.addPot(new AddressedIPotCF(this, "VbBp2", 16, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 17, "fb Diff"));
			ipots.addPot(new AddressedIPotCF(this, "VneuronLeakExtBp", 17, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 18, "neuron"));
			ipots.addPot(new AddressedIPotCF(this, "VpulseAERExtdBn", 18, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 19, "neuron"));
			ipots.addPot(new AddressedIPotCF(this, "VwideampBp", 19, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 20, "wideamp Diff"));
			ipots.addPot(new AddressedIPotCF(this, "VQBn", 20, Type.NORMAL, Sex.na, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 21, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "VcascodeBpc", 21, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 22, "SOS, diff"));
			ipots.addPot(new AddressedIPotCF(this, "LcolTimeoutBn", 22, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 23, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "AEPdBn", 23, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 24, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "AEPuXBp", 24, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 25, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "AEPuYBp", 25, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 26, "AER"));
			ipots.addPot(new AddressedIPotCF(this, "VbiasLF1Bn", 26, Type.NORMAL, Sex.na, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 27, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "VclbtcasBnc", 27, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 28, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "Ibias20pOTA", 28, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 29, "preamp"));
			ipots.addPot(new AddressedIPotCF(this, "Ibias40pOTA", 29, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 30, "preamp"));
			ipots.addPot(new AddressedIPotCF(this, "Ibias1nOTA", 30, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 31, "preamp"));
//			ipots.addPot(new AddressedIPotCF(this, "Blk2N", 31, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
//				AddressedIPotCF.maxFineBitValue, 32, "unused"));
//			ipots.addPot(new AddressedIPotCF(this, "Blk3N", 32, Type.NORMAL, Sex.P, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
//				AddressedIPotCF.maxFineBitValue, 33, "unused"));
			ipots.addPot(new AddressedIPotCF(this, "VthAGCBn", 33, Type.NORMAL, Sex.N, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 34, "SOS"));
			ipots.addPot(new AddressedIPotCF(this, "BiasBuffer", 34, Type.NORMAL, Sex.na, false, true, AddressedIPotCF.maxCoarseBitValue / 2,
				AddressedIPotCF.maxFineBitValue, 35, "BufferN=VcascodeBnc, BufferP=VneuronRpBp"));

			setPotArray(ipots);

			// shifted sources
			final ShiftedSourceBiasCF ssp = new ShiftedSourceBiasCF(this);
			ssp.setSex(Pot.Sex.P);
			ssp.setName("SSP");
			ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
			ssp.setAddress(35);
			ssp.addObserver(this);

			final ShiftedSourceBiasCF ssn = new ShiftedSourceBiasCF(this);
			ssn.setSex(Pot.Sex.N);
			ssn.setName("SSN");
			ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
			ssn.setAddress(36);
			ssn.addObserver(this);

			ssBiases[0] = ssp;
			ssBiases[1] = ssn;
			
			SelResSW = new SPIConfigBit("SelResSW", "In diff1 cell, if SelResSW=1, gate of resistor is tied to SSP, otherwise to Vdd.", CypressFX3.FPGA_CHIPBIAS, (short) 156, false, this);
			SelResSW.addObserver(this);
			allPreferencesList.add(SelResSW);

			// DAC1 channels (16)
			vpots.addPot(new VPot(getChip(), "MICBIAS1L", dac1, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Not implemented"));
			vpots.addPot(new VPot(getChip(), "MICBIAS1L", dac1, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Not implemented"));
			vpots.addPot(new VPot(getChip(), "Vrefdiff", dac1, 2, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Src Bias for differentiator"));
			vpots.addPot(new VPot(getChip(), "Vrefdiff2", dac1, 3, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Src Bias for differentiator"));
			vpots.addPot(new VPot(getChip(), "PreampGain1L", dac1, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Preamp gain for offchip preamp 1"));
			vpots.addPot(new VPot(getChip(), "PreampGain1R", dac1, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Preamp gain for offchip preamp 2"));
			vpots.addPot(new VPot(getChip(), "Vcom2", dac1, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "VQBn", dac1, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets Q of filter"));
			vpots.addPot(new VPot(getChip(), "DCOutputLowLevel", dac1, 8, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets DC level of input to cochlea"));
			vpots.addPot(new VPot(getChip(), "DACVout10", dac1, 9, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Used for VneuronExtBn"));
			vpots.addPot(new VPot(getChip(), "DACVout11", dac1, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "DACVout12", dac1, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, ""));
			vpots.addPot(new VPot(getChip(), "RefADAMux0-", dac1, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Ref for Mux0"));
			vpots.addPot(new VPot(getChip(), "RefADAMux1-", dac1, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Ref for Mux1"));
			vpots.addPot(new VPot(getChip(), "Vth1x", dac1, 14, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Low Threshold for Neuron"));
			vpots.addPot(new VPot(getChip(), "Vth4x", dac1, 15, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "High Threshold for Neuron"));

			// DAC2 channels (16)
			vpots.addPot(new VPot(getChip(), "Vbias1Bn", dac2, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Bias to set HF corner of last stage of L/R"));
			vpots.addPot(new VPot(getChip(), "Vbias2Bn", dac2, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Bias to set LF corner of last stage of T/B"));
			vpots.addPot(new VPot(getChip(), "VbiasLF1Bn", dac2, 2, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Bias to set LF corner of last stage of L/R"));
			vpots.addPot(new VPot(getChip(), "VbiasHF2Bn", dac2, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Bias to set HF corner of last stage of T/B"));
			vpots.addPot(new VPot(getChip(), "VtauBn", dac2, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets CF of filter and Q"));
			vpots.addPot(new VPot(getChip(), "PreampGain2L", dac2, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Preamp gain for offchip preamp 3"));
			vpots.addPot(new VPot(getChip(), "MICBIAS2L", dac2, 6, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Not implemented"));
			vpots.addPot(new VPot(getChip(), "MICBIAS2R", dac2, 7, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Not implemented"));
			vpots.addPot(new VPot(getChip(), "VrefpreampBpx", dac2, 8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets Ref to On-Chip Preamp"));
			vpots.addPot(new VPot(getChip(), "VbMicCasBpcx", dac2, 9, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets P-Cascode voltage for On-Chip Preamp"));
			vpots.addPot(new VPot(getChip(), "RefADAMux3-", dac2, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Ref for Mux3"));
			vpots.addPot(new VPot(getChip(), "RefADAMux2-", dac2, 11, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Ref for Mux2"));
			vpots.addPot(new VPot(getChip(), "PreampGain2R", dac2, 12, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Preamp gain for offchip preamp 4"));
			vpots.addPot(new VPot(getChip(), "DCOutputHighLevel", dac2, 13, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Set to Gnd"));
			vpots.addPot(new VPot(getChip(), "TH", dac2, 14, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Threshold for Offchip Preamp"));
			vpots.addPot(new VPot(getChip(), "PreampA/R", dac2, 15, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets gain for Offchip preamp"));

			// New logic SPI configuration values.
			// Scanner module
			scannerControl.add(new SPIConfigBit("ScannerEnable", "Enable scanner output.", CypressFX3.FPGA_SCANNER, 
				(short) 0, false, this));
			scannerControl.add(new SPIConfigInt("ScannerChannel", "Which channel to scan out.", CypressFX3.FPGA_SCANNER,
				(short) 1, 7, 0, this));

			for (final SPIConfigValue cfgVal : scannerControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// DAC control
			dacRun = new SPIConfigBit("DACRun", "Enable external DAC.", CypressFX3.FPGA_DAC, (short) 0, false, this);
			dacRun.addObserver(this);
			allPreferencesList.add(dacRun);

			// Multiplexer module
			biasForceEnable = new SPIConfigBit("ForceBiasEnable", "Force the biases to be always ON.", CypressFX3.FPGA_MUX, 
				(short) 3, false, this);
			biasForceEnable.addObserver(this);
			allPreferencesList.add(biasForceEnable);

			// Generic AER from chip
			aerControl.add(new SPIConfigBit("TestAEREnable", "Enable Test AER output instead of normal AER.", CypressFX3.FPGA_SCANNER,
				(short) 2, false, this)); // In scanner module for convenience.
			aerControl.add(new SPIConfigBit("AERRun", "Run the main AER state machine.", CypressFX3.FPGA_DVS, (short) 3, false, this));
			aerControl.add(new SPIConfigInt("AERAckDelay", "Delay AER ACK by this many cycles.", CypressFX3.FPGA_DVS, 
				(short) 4, 12, 0, this));
			aerControl.add(new SPIConfigInt("AERAckExtension", "Extend AER ACK by this many cycles.", CypressFX3.FPGA_DVS,
				(short) 6, 12, 0, this));
			aerControl.add(new SPIConfigBit("AERWaitOnTransferStall",
				"Whether the AER state machine should wait,<br> or continue servicing the AER bus when the FIFOs are full.", CypressFX3.FPGA_DVS,
				(short) 8, false, this));
			aerControl.add(new SPIConfigBit("AERExternalAERControl",
				"Do not control/ACK the AER bus anymore, <br>but let it be done by an external device.", CypressFX3.FPGA_DVS,
				(short) 10, false, this));

			for (final SPIConfigValue cfgVal : aerControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// ADC
			adcControl.add(new SPIConfigBit("ADCEnable", "Enable external ADCs", CypressFX3.FPGA_ADC, (short) 0, false, this));
			adcControl.add(new SPIConfigInt("ADCChannelsEnable", "1 - Right, 2 - Left, 3 - Both", CypressFX3.FPGA_ADC, (short) 1, 4, 3, this));
			adcControl.add(new SPIConfigBit("ADCFrequency", "OFF - 16kHz, ON - 44.1kHz", CypressFX3.FPGA_ADC, (short) 2, false, this));
			for (final SPIConfigValue cfgVal : adcControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// Chip diagnostic chain
			chipControl.add(new SPIConfigInt("AnalogMux0", "MSB First", CypressFX3.FPGA_CHIPBIAS,
				(short) 128, 4, 0, this));
			chipControl.add(new SPIConfigInt("PreampGain", "MSB First", CypressFX3.FPGA_CHIPBIAS,
				(short) 129, 2, 0, this));
			chipControl.add(new SPIConfigBit("Seln1", "Set to 0 for parallel architecture, Set to 1 for cascaded architecture, settings for LR pair", CypressFX3.FPGA_CHIPBIAS,
				(short) 130, false, this));
			chipControl.add(new SPIConfigBit("Seln2", "Set to 0 for parallel architecture, Set to 1 for cascaded architecture, settings for TB pair", CypressFX3.FPGA_CHIPBIAS,
				(short) 131, false, this));
			chipControl.add(new SPIConfigBit("SMconfigArow", "Used in On-chip Arbiter", CypressFX3.FPGA_CHIPBIAS,
				(short) 132, false, this));
			chipControl.add(new SPIConfigBit("DC5", "Not Used", CypressFX3.FPGA_CHIPBIAS,
				(short) 133, false, this));
			chipControl.add(new SPIConfigBit("SelBot", "Set this bit to 1 to disable Neuron Kill mechanism for neurons 0, 2 when AERKillBit is set to 1 and channel is selected", CypressFX3.FPGA_CHIPBIAS,
				(short) 134, true, this));
			chipControl.add(new SPIConfigBit("SelTop", "Set this bit to 1 to disable Neuron Kill mechanism for neurons 1, 3 when AERKillBit is set to 1 and channel is selected", CypressFX3.FPGA_CHIPBIAS,
				(short) 135, true, this));
			chipControl.add(new SPIConfigBit("DC8", "nB0 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 136, false, this));
			chipControl.add(new SPIConfigBit("DC9", "nB1 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 137, false, this));
			chipControl.add(new SPIConfigBit("DC10", "nB2 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 138, false, this));
			chipControl.add(new SPIConfigBit("DC11", "nB3 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 139, false, this));
			chipControl.add(new SPIConfigBit("DC12", "nB4 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 140, false, this));
			chipControl.add(new SPIConfigBit("DC13", "B5 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 141, false, this));
			chipControl.add(new SPIConfigBit("DC14", "B6 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 142, false, this));
			chipControl.add(new SPIConfigBit("DC15", "B7 for Test SOS", CypressFX3.FPGA_CHIPBIAS,
				(short) 143, false, this));
			chipControl.add(new SPIConfigBit("DC16", "B8 for Test Diff", CypressFX3.FPGA_CHIPBIAS,
				(short) 144, false, this));
			chipControl.add(new SPIConfigBit("DC17", "B9 for Test Diff", CypressFX3.FPGA_CHIPBIAS,
				(short) 145, false, this));
			chipControl.add(new SPIConfigBit("DC18", "B10 for Test Diff", CypressFX3.FPGA_CHIPBIAS,
				(short) 146, false, this));
			chipControl.add(new SPIConfigBit("BitTestLatch", "Set to 1 to enable followers of test signals to pad", CypressFX3.FPGA_CHIPBIAS,
				(short) 147, false, this));
			chipControl.add(new SPIConfigBit("DC20", "Bit set to 1 if more Leak Current is needed on Test Neuron", CypressFX3.FPGA_CHIPBIAS,
				(short) 148, false, this));
			chipControl.add(new SPIConfigBit("DC21", "Not Used", CypressFX3.FPGA_CHIPBIAS,
				(short) 149, false, this));
			chipControl.add(new SPIConfigBit("DC22", "Not Used", CypressFX3.FPGA_CHIPBIAS,
				(short) 150, false, this));
			chipControl.add(new SPIConfigBit("DC23", "Not Used", CypressFX3.FPGA_CHIPBIAS,
				(short) 151, false, this));
			chipControl.add(new SPIConfigInt("DigitalMux0", "?", CypressFX3.FPGA_CHIPBIAS,
				(short) 152, 4, 0, this));
			chipControl.add(new SPIConfigInt("DigitalMux1", "?", CypressFX3.FPGA_CHIPBIAS,
				(short) 153, 4, 0, this));
			chipControl.add(new SPIConfigInt("DigitalMux2", "?", CypressFX3.FPGA_CHIPBIAS,
				(short) 154, 4, 0, this));
			chipControl.add(new SPIConfigInt("DigitalMux3", "?", CypressFX3.FPGA_CHIPBIAS,
				(short) 155, 4, 0, this));
			chipControl.add(new SPIConfigBit("VresetBn", "If VresetBn_S = 1, all local latched AER KillBits in the neuron are cleared, that is neurons can send AER spikes", CypressFX3.FPGA_CHIPBIAS,
				(short) 157, false, this));
			chipControl.add(new SPIConfigInt("KillSignalDuration", "In 100MHz clock cycles", CypressFX3.FPGA_CHIPBIAS,
				(short) 164, 11, 2000, this));
			chipControl.add(new SPIConfigInt("KillChannel", "Select the channel to kill neurons in", CypressFX3.FPGA_CHIPBIAS,
				(short) 160, 8, 0, this));
			chipControl.add(new SPIConfigBit("KillNeurons02", "Toggle this bit to kill neurons 0, 2 of the channel selected above", CypressFX3.FPGA_CHIPBIAS,
				(short) 165, false, this));
			chipControl.add(new SPIConfigBit("KillNeurons13", "Toggle this bit to kill neurons 1, 3 of the channel selected above", CypressFX3.FPGA_CHIPBIAS,
				(short) 166, false, this));
			
			for (final SPIConfigValue cfgVal : chipControl) {
				cfgVal.addObserver(this);
				allPreferencesList.add(cfgVal);
			}

			// Create the 128 cochlea channels for L/R ear pair.
			for (int i = 0; i < 64; ++i) {
				cochleaChannels[0].add(new CochleaTow4EarChannelConfig("Channel_L" + i, "Left ear channel " + i + " configuration.", 2*i, (AEChip)chip));
				cochleaChannels[1].add(new CochleaTow4EarChannelConfig("Channel_R" + i, "Right ear channel " + i + " configuration.", 2*i + 1, (AEChip)chip));
			}

			// Create the 128 cochlea channels for T/B ear pair.
			for (int i = 0; i < 64; i++) {
				cochleaChannels[2].add(new CochleaTow4EarChannelConfig("Channel_T" + i, "Top ear channel " + i + " configuration.", 128 + 2*i, (AEChip)chip));
				cochleaChannels[3].add(new CochleaTow4EarChannelConfig("Channel_B" + i, "Bottom ear channel " + i + " configuration.", 129 + 2*i, (AEChip)chip));
			}
			
			for (int i = 0; i < 4; ++i) {
				for (final CochleaChannelConfig chan : cochleaChannels[i]) {
					chan.addObserver(this);
					allPreferencesList.add(chan);
				}
			}
			
			// Preferences
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
			final JComponent c = new CochleaTow4EarControlPanel(CochleaTow4Ear.this);
			c.setPreferredSize(new Dimension(1000, 800));
			panel.add(new JScrollPane(c), BorderLayout.CENTER);
			return panel;
		}

		@Override
		public void setHardwareInterface(final BiasgenHardwareInterface hw) {
			hardwareInterface = hw;
			if (hardwareInterface != null) {
				configSequence = ((CypressFX3) hardwareInterface).new SPIConfigSequence();
				try {
					sendConfiguration();
				}
				catch (final HardwareInterfaceException ex) {
					net.sf.jaer.biasgen.Biasgen.log.warning(ex.toString());
				}
			}
		}

		/**
		 * The central point for communication with HW from biasgen. All objects
 in Biasgen are Observables and appendCopy Biasgen.this as Observer. They
 then call notifyObservers when their state changes.
		 *
		 * @param observable
		 *            IPot, DAC, etc
		 * @param object
		 *            notifyChange used at present
		 */
		@Override
		public synchronized void update(final Observable observable, final Object object) {
			log.fine("update from observable=" + observable.toString() + " with object=" + object);
			if ((hardwareInterface != null) && (hardwareInterface instanceof CypressFX3)) {
				final CypressFX3 fx3HwIntf = (CypressFX3) hardwareInterface;
				
				try {
					if (observable instanceof TowerOnChip6BitVDAC) {
						final TowerOnChip6BitVDAC iPot = (TowerOnChip6BitVDAC) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeBinaryRepresentation());
					}
					if (observable instanceof AddressedIPotCF) {
						final AddressedIPotCF iPot = (AddressedIPotCF) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeCleanBinaryRepresentation());
					}
					else if (observable instanceof ShiftedSourceBiasCF) {
						final ShiftedSourceBiasCF iPot = (ShiftedSourceBiasCF) observable;

						fx3HwIntf.spiConfigSend(CypressFX3.FPGA_CHIPBIAS, (short) iPot.getAddress(), iPot.computeBinaryRepresentation());
					}
					else if (observable instanceof VPot) {
						final VPot vPot = (VPot) observable;

						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 1, vPot.getDacNumber()); // Select DAC.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 2, 0x03); // Select input data register.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 3, vPot.getChannel());
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 5, vPot.getBitValue());

						// Toggle SET flag.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 1);
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 0);

						// Commit configuration.
						//configSequence.sendConfigSequence();

						// Temporary fix for offset/gain default values problem
						// [TODO]: Make a new panel for offset/gain control

						// Write offset reg (c)
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 1, vPot.getDacNumber()); // Select DAC.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 2, 0x02); // Select offset register.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 3, vPot.getChannel());
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 5, 0x7FE);

						// Toggle SET flag.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 1);
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 0);

						// Commit configuration.
						//configSequence.sendConfigSequence();

						// Write gain reg (m)
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 1, vPot.getDacNumber()); // Select DAC.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 2, 0x01); // Select gain register.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 3, vPot.getChannel());
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 5, 0xFFE);

						// Toggle SET flag.
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 1);
						configSequence.addConfig(CypressFX3.FPGA_DAC, (short) 6, 0);

						// Commit configuration.
						configSequence.sendConfigSequence();

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
					else if (observable instanceof CochleaChannelConfig) {
						final CochleaChannelConfig chan = (CochleaChannelConfig) observable;

						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 160, chan.getChannelAddress());
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 162, chan.computeBinaryRepresentation());

						// Toggle SET flag.
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 163, 1);
						configSequence.addConfig(CypressFX3.FPGA_CHIPBIAS, (short) 163, 0);

						// Commit configuration.
						configSequence.sendConfigSequence();
						
						//log.info(chan.getName() + " is configured with the value " + chan.getFullValue());

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

			for (final AbstractConfigValue spiCfg : allPreferencesList) {
				spiCfg.setChanged();
				spiCfg.notifyObservers();
			}

			for (final ShiftedSourceBiasCF sSrc : ssBiases) {
				sSrc.setChanged();
				sSrc.notifyObservers();
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
	 * Extract cochlea events from CochleaTow4Ear.
	 * <p>
	 * The event class returned by the extractor is BinauralCochleaEvent.
	 */
	public class Extractor extends TypedEventExtractor<BinauralCochleaEvent> {

		private static final long serialVersionUID = -3469492271382423090L;
		private int lastSpecialEventTimestamp = 0;

		public Extractor(final AEChip chip) {
			super(chip);
		}

		/**
		 * Extracts the meaning of the raw events. This form is used to supply
		 * an output packet. This method is used for real time event filtering
		 * using a buffer of output events local to data acquisition. An
		 * AEPacketRaw may contain multiple events, not all of them have to sent
		 * out as EventPackets. An AEPacketRaw is a set(!) of addresses and
		 * corresponding timing moments.
		 *
		 * A first filter (independent from the other ones) is implemented by
		 * subSamplingEnabled and getSubsampleThresholdEventCount. The latter
		 * may limit the amount of samples in one package to say 50,000. If
		 * there are 160,000 events and there is a sub samples threshold of
		 * 50,000, a "skip parameter" set to 3. Every so now and then the
		 * routine skips with 4, so we end up with 50,000. It's an
		 * approximation, the amount of events may be less than 50,000. The
		 * events are extracted uniform from the input.
		 *
		 * @param in
		 *            the raw events, can be null
		 * @param out
		 *            the processed events. these are partially processed
		 *            in-place. empty packet is returned if null is supplied as input.
		 */
		@Override
		synchronized public void extractPacket(final AEPacketRaw in, final EventPacket<BinauralCochleaEvent> out) {
			out.clear();

			if (in == null) {
				return;
			}

			final int n = in.getNumEvents();

			// [TODO] Implement subsampling
			if (isSubSamplingEnabled()) {
				log.log(Level.WARNING, "Subsampling is not implemented yet in {0}", this);
			}

			final int[] addresses = in.getAddresses();
			final int[] timestamps = in.getTimestamps();

			BinauralCochleaEvent chanEv = null;
			final OutputEventIterator<BinauralCochleaEvent> outItr = out.outputIterator();

			for (int i = 0; i < n; ++i) {
				final int addr = addresses[i];
				if (((addr & 0x200) != 0) && ((addr & BasicEvent.SPECIAL_EVENT_BIT_MASK) == 0)) {		// XSelect
					if (chanEv == null) {
						//log.log(Level.WARNING, "Incontiguous event sequence. Neuron address event {0} is dropped", (addr & 0x1FF) >> 1);
					} else {
						if (timestamps[i] != chanEv.timestamp) {
							//log.log(Level.WARNING, "Incongruent timestapms between channel and neuron events. Delta {0}", timestamps[i] - chanEv.timestamp);
						} 
						BinauralCochleaEvent neuronEv = outItr.nextOutput();
						neuronEv.copyFrom(chanEv);
						neuronEv.y += (short) (((addr >>> 1) & 0x03));
					}
				} else {
					if ((addr & BasicEvent.SPECIAL_EVENT_BIT_MASK) != 0) {
						CochleaTow4EarEvent specEv = new CochleaTow4EarEvent();
						specEv.address = addr;
						specEv.timestamp = timestamps[i];
						specEv.setSpecial(true); // tick events for CochleaTow4Ear; every 32768 us from logic
						//int dt = e.timestamp - lastSpecialEventTimestamp;
						lastSpecialEventTimestamp = specEv.timestamp;
						outItr.writeToNextOutput(specEv);
					} else {
						chanEv = new CochleaTow4EarEvent();
						chanEv.address = addr;
						chanEv.timestamp = timestamps[i];
						chanEv.x = getXFromAddress(addr);
						chanEv.y = getYFromAddress(addr);
						chanEv.type = getTypeFromAddress(addr);
					} 
				}
			}
		}

		/**
		 * Overrides default extractor so that cochlea channels are returned,
		 * numbered from x=0 (base, high frequencies, input end) to x=63 (apex,
		 * low frequencies).
		 *
		 * @param addr
		 *            raw address.
		 * @return channel, from 0 to 63.
		 */
		@Override
		public short getXFromAddress(final int addr) {
			return (short) ((addr & 0x7E) >>> 1);
		}

		/**
		 * Overrides default extractor to spread all outputs from a tap
		 * (left/right, polarity ON/OFF) into a single unique y address that can
		 * be displayed in the 2d histogram.
		 *
		 * @param addr
		 *            the raw address
		 * @return the Y address
		 */
		@Override
		public short getYFromAddress(final int addr) {
			return (short) ((((addr >>> 6) & 0x02) | (addr & 0x01)) << 2); // Concatenate bits 7 (T/B) and 0 (L/R)
		}

		/**
		 * Overrides default extract to define type of event the same as the Y
		 * address.
		 *
		 * @param addr
		 *            the raw address.
		 * @return the type
		 */
		@Override
		public byte getTypeFromAddress(final int addr) {
			return (byte) (((addr >>> 6) & 0x02) | (addr & 0x01)); // Concatenate bits 7 (T/B) and 0 (L/R)
		}
	}
}
