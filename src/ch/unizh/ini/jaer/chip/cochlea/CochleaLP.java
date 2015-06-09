package ch.unizh.ini.jaer.chip.cochlea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

@Description("Low-power binaural AER silicon cochlea with 64 channels")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CochleaLP extends CochleaChip implements Observer {
	private final GLUT glut = new GLUT();

	/** Creates a new instance of CochleaLP */
	public CochleaLP() {
		super();
		addObserver(this);

		setName("CochleaLP");
		setEventClass(CochleaAMSEvent.class);

		setSizeX(64);
		setSizeY(4);
		setNumCellTypes(4);

		setRenderer(new Renderer(this));
		setBiasgen(new CochleaLP.Biasgen(this));
		setEventExtractor(new CochleaLP.Extractor(this));

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
					public void renderStrokeFontString(final GL2 gl, final float x, final float y, final float z,
						final float angleDeg, final String s) {
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

	public class Renderer extends AEChipRenderer {
		private boolean didit = false;

		public Renderer(AEChip chip) {
			super(chip);
		}

		@Override
		protected void checkTypeColors(int numCellTypes) {
			if (didit) {
				return;
			}
			didit = true;
			super.checkTypeColors(numCellTypes);
			Color[] colors = { Color.green, Color.red, Color.green, Color.red };
			int ind = 0;
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					colors[i].getRGBColorComponents(typeColorRGBComponents[ind++]);
				}
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
				setBiasgen(new CochleaLP.Biasgen(this));
			}
			else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		}
		catch (final ClassCastException e) {
			System.err.println(e.getMessage()
				+ ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	interface ConfigBase {

		void addObserver(Observer o);

		String getName();

		String getDescription();
	}

	// following used in configuration

	interface ConfigBit extends ConfigBase {

		boolean isSet();

		void set(boolean yes);
	}

	interface ConfigInt extends ConfigBase {

		int get();

		void set(int v) throws IllegalArgumentException;
	}

	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements net.sf.jaer.biasgen.ChipControlPanel {

		private final short ADC_CONFIG = (short) 0x100;

		private final ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();

		// lists of ports and CPLD config
		ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
		ArrayList<AbstractConfigValue> config = new ArrayList<AbstractConfigValue>();

		/**
		 * The DAC on the board. Specified with 5V reference even though Vdd=3.3 because the internal 2.5V reference is
		 * used and so that the VPot controls display correct voltage.
		 */
		protected final DAC dac = new DAC(16, 12, 0, 5f, 3.3f);

		IPotArray ipots = new IPotArray(this);
		PotArray vpots = new PotArray(this);

		// config bits/values
		private final ConfigBit hostResetTimestamps = new AbstractConfigBit("ee", "hostResetTimestamps", false),
			runAERComm = new AbstractConfigBit("a3", "runAERComm", true);

		private final ConfigBit cochleaReset = new AbstractConfigBit("e3", "cochleaReset", false);

		// scanner config stored in scannerProxy; updates should update state of below fields
		private final CPLDInt scanX = new CPLDInt(55, 61, "scanChannel",
			"cochlea tap to monitor when not scanning continuously", 0);
		private final CPLDBit scanSel = new CPLDBit(
			62,
			"scanSel",
			"selects which on-chip cochlea scanner shift register to monitor for sync (0=BM, 1=Gang Cells) - also turns on CPLDLED1 near FXLED1",
			false), // TODO firmware controlled?
			scanContinuouslyEnabled = new CPLDBit(63, "scanContinuouslyEnabled",
				"enables continuous scanning of on-chip scanner", true);

		Equalizer equalizer = new Equalizer();
		BufferIPot bufferIPot = new BufferIPot();

		/**
		 * Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
		 *
		 * @param chip
		 *            the chip this biasgen belongs to
		 */
		public Biasgen(final Chip chip) {
			super(chip);
			setName("CochleaAMS1c.Biasgen");

			equalizer.addObserver(this);
			bufferIPot.addObserver(this);

			// inspect config to build up CPLDConfig

			// public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int
			// bitValue, int displayPosition, String tooltipString) {
			potArray = new IPotArray(this); // construct IPotArray whit shift register stuff

			ipots.addPot(new IPot(this, "VAGC", 0, IPot.Type.NORMAL, IPot.Sex.N, 0, 1,
				"Sets reference for AGC diffpair in SOS")); // second to list bits loaded, just before buffer bias bits.
			// displayed first in GUI
			ipots.addPot(new IPot(this, "Curstartbpf", 1, IPot.Type.NORMAL, IPot.Sex.P, 0, 2,
				"Sets master current to local DACs for BPF Iq"));
			ipots.addPot(new IPot(this, "DacBufferNb", 2, IPot.Type.NORMAL, IPot.Sex.N, 0, 3,
				"Sets bias current of amp in local DACs"));
			ipots.addPot(new IPot(this, "Vbp", 3, IPot.Type.NORMAL, IPot.Sex.P, 0, 4,
				"Sets bias for readout amp of BPF"));
			ipots.addPot(new IPot(this, "Ibias20OpAmp", 4, IPot.Type.NORMAL, IPot.Sex.P, 0, 5,
				"Bias current for preamp"));
			// ipots.addPot(new IPot(this, "N.C.", 5, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "not used"));
			ipots.addPot(new IPot(this, "Vioff", 5, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "Sets DC shift input to LPF"));
			ipots.addPot(new IPot(this, "Vsetio", 6, IPot.Type.CASCODE, IPot.Sex.P, 0, 7,
				"Sets 2I0 and I0 for LPF time constant"));
			ipots.addPot(new IPot(this, "Vdc1", 7, IPot.Type.NORMAL, IPot.Sex.P, 0, 8,
				"Sets DC shift for close end of cascade"));
			ipots.addPot(new IPot(this, "NeuronRp", 8, IPot.Type.NORMAL, IPot.Sex.P, 0, 9,
				"Sets bias current of neuron"));
			ipots.addPot(new IPot(this, "Vclbtgate", 9, IPot.Type.NORMAL, IPot.Sex.P, 0, 10, "Bias gate of CLBT"));
			ipots.addPot(new IPot(this, "N.C.", 10, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "not used"));
			// ipots.addPot(new IPot(this, "Vioff", 10, IPot.Type.NORMAL, IPot.Sex.P, 0, 11,
			// "Sets DC shift input to LPF"));
			ipots.addPot(new IPot(this, "Vbias2", 11, IPot.Type.NORMAL, IPot.Sex.P, 0, 12,
				"Sets lower cutoff freq for cascade"));
			ipots.addPot(new IPot(this, "Ibias10OpAmp", 12, IPot.Type.NORMAL, IPot.Sex.P, 0, 13,
				"Bias current for preamp"));
			ipots.addPot(new IPot(this, "Vthbpf2", 13, IPot.Type.CASCODE, IPot.Sex.P, 0, 14,
				"Sets high end of threshold current for bpf neurons"));
			ipots.addPot(new IPot(this, "Follbias", 14, IPot.Type.NORMAL, IPot.Sex.N, 0, 15, "Bias for PADS"));
			ipots.addPot(new IPot(this, "pdbiasTX", 15, IPot.Type.NORMAL, IPot.Sex.N, 0, 16, "pulldown for AER TX"));
			ipots.addPot(new IPot(this, "Vrefract", 16, IPot.Type.NORMAL, IPot.Sex.N, 0, 17,
				"Sets refractory period for AER neurons"));
			ipots.addPot(new IPot(this, "VbampP", 17, IPot.Type.NORMAL, IPot.Sex.P, 0, 18,
				"Sets bias current for input amp to neurons"));
			ipots.addPot(new IPot(this, "Vcascode", 18, IPot.Type.CASCODE, IPot.Sex.N, 0, 19, "Sets cascode voltage"));
			ipots.addPot(new IPot(this, "Vbpf2", 19, IPot.Type.NORMAL, IPot.Sex.P, 0, 20,
				"Sets lower cutoff freq for BPF"));
			ipots.addPot(new IPot(this, "Ibias10OTA", 20, IPot.Type.NORMAL, IPot.Sex.N, 0, 21,
				"Bias current for OTA in preamp"));
			ipots.addPot(new IPot(this, "Vthbpf1", 21, IPot.Type.CASCODE, IPot.Sex.P, 0, 22,
				"Sets low end of threshold current to bpf neurons"));
			ipots.addPot(new IPot(this, "Curstart ", 22, IPot.Type.NORMAL, IPot.Sex.P, 0, 23,
				"Sets master current to local DACs for SOS Vq"));
			ipots.addPot(new IPot(this, "Vbias1", 23, IPot.Type.NORMAL, IPot.Sex.P, 0, 24,
				"Sets higher cutoff freq for SOS"));
			ipots.addPot(new IPot(this, "NeuronVleak", 24, IPot.Type.NORMAL, IPot.Sex.P, 0, 25,
				"Sets leak current for neuron"));
			ipots.addPot(new IPot(this, "Vioffbpfn", 25, IPot.Type.NORMAL, IPot.Sex.N, 0, 26,
				"Sets DC level for input to bpf"));
			ipots.addPot(new IPot(this, "Vcasbpf", 26, IPot.Type.CASCODE, IPot.Sex.P, 0, 27,
				"Sets cascode voltage in cm BPF"));
			ipots.addPot(new IPot(this, "Vdc2", 27, IPot.Type.NORMAL, IPot.Sex.P, 0, 28,
				"Sets DC shift for SOS at far end of cascade"));
			ipots.addPot(new IPot(this, "Vterm", 28, IPot.Type.CASCODE, IPot.Sex.N, 0, 29,
				"Sets bias current of terminator xtor in diffusor"));
			ipots.addPot(new IPot(this, "Vclbtcasc", 29, IPot.Type.CASCODE, IPot.Sex.P, 0, 30,
				"Sets cascode voltage in CLBT"));
			ipots.addPot(new IPot(this, "reqpuTX", 30, IPot.Type.NORMAL, IPot.Sex.P, 0, 31,
				"Sets pullup bias for AER req ckts"));
			ipots.addPot(new IPot(this, "Vbpf1", 31, IPot.Type.NORMAL, IPot.Sex.P, 0, 32,
				"Sets higher cutoff freq for BPF")); // first bits loaded, at end of shift register

			// public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int
			// displayPosition, String tooltipString) {
			// top dac in schem/layout, first 16 channels of 32 total
			vpots.addPot(new VPot(CochleaLP.this, "Vterm", dac, 0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets bias current of terminator xtor in diffusor"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefhres", dac, 1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets source of terminator xtor in diffusor"));
			vpots.addPot(new VPot(CochleaLP.this, "VthAGC", dac, 2, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets input to diffpair that generates VQ"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefreadout", dac, 3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets reference for readout amp"));
			// vpots.addPot(new VPot(CochleaAMS1c.this, "Vbpf2x", dac, 4, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
			// "test dac bias"));
			vpots.addPot(new VPot(CochleaLP.this, "BiasDACBufferNBias", dac, 4, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets bias current of buffer in pixel DACs"));
			// vpots.addPot(new VPot(CochleaAMS1c.this, "Vbias2x", dac, 5, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
			// "test dac bias"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefract", dac, 5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets refractory period of neuron"));
			// vpots.addPot(new VPot(CochleaAMS1c.this, "Vbpf1x", dac, 6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
			// "test dac bias"));
			vpots.addPot(new VPot(CochleaLP.this, "PreampAGCThreshold (TH)", dac, 6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Threshold for microphone preamp AGC gain reduction turn-on"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefpreamp", dac, 7, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets virtual group of microphone drain preamp"));
			// vpots.addPot(new VPot(CochleaAMS1c.this, "Vbias1x", dac, 8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
			// "test dac bias"));
			vpots.addPot(new VPot(CochleaLP.this, "NeuronRp", dac, 8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets bias current of neuron comparator- overrides onchip bias"));
			vpots.addPot(new VPot(CochleaLP.this, "Vthbpf1x", dac, 9, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets threshold for BPF neuron"));
			vpots.addPot(new VPot(CochleaLP.this, "Vioffbpfn", dac, 10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets DC level for BPF input"));
			vpots.addPot(new VPot(CochleaLP.this, "NeuronVleak", dac, 11, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets leak current for neuron - not connected on board"));
			vpots.addPot(new VPot(CochleaLP.this, "DCOutputLevel", dac, 12, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Microphone DC output level to cochlea chip"));
			vpots.addPot(new VPot(CochleaLP.this, "Vthbpf2x", dac, 13, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets threshold for BPF neuron"));
			vpots.addPot(new VPot(CochleaLP.this, "DACSpOut2", dac, 14, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"test dac bias"));
			vpots.addPot(new VPot(CochleaLP.this, "DACSpOut1", dac, 15, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"test dac bias"));

			// bot DAC in schem/layout, 2nd 16 channels
			vpots.addPot(new VPot(CochleaLP.this, "Vth4", dac, 16, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets high VT for LPF neuron"));
			vpots.addPot(new VPot(CochleaLP.this, "Vcas2x", dac, 17, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets cascode voltage for subtraction of neighboring filter outputs"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefo", dac, 18, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets src for output of CM LPF"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefn2", dac, 19, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets DC gain gain cascode bias in BPF"));
			vpots.addPot(new VPot(CochleaLP.this, "Vq", dac, 20, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets tau of feedback amp in SOS"));

			vpots.addPot(new VPot(CochleaLP.this, "Vpf", dac, 21, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets bias current for scanner follower"));

			vpots.addPot(new VPot(CochleaLP.this, "Vgain", dac, 22, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets bias for differencing amp in BPF/LPF"));
			vpots.addPot(new VPot(CochleaLP.this, "Vrefn", dac, 23, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets cascode bias in BPF"));
			vpots.addPot(new VPot(CochleaLP.this, "VAI0", dac, 24, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets tau of CLBT for ref current"));
			vpots.addPot(new VPot(CochleaLP.this, "Vdd1", dac, 25, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets up power to on-chip DAC"));
			vpots.addPot(new VPot(CochleaLP.this, "Vth1", dac, 26, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets low VT for LPF neuron"));
			vpots.addPot(new VPot(CochleaLP.this, "Vref", dac, 27, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets src for input of CM LPF"));
			vpots.addPot(new VPot(CochleaLP.this, "Vtau", dac, 28, Pot.Type.NORMAL, Pot.Sex.P, 0, 0,
				"Sets tau of forward amp in SOS"));
			vpots.addPot(new VPot(CochleaLP.this, "VcondVt", dac, 29, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"Sets VT of conductance neuron"));
			vpots.addPot(new VPot(CochleaLP.this, "Vpm", dac, 30, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"sets bias of horizontal element of diffusor"));
			vpots.addPot(new VPot(CochleaLP.this, "Vhm", dac, 31, Pot.Type.NORMAL, Pot.Sex.N, 0, 0,
				"sets bias of horizontal element of diffusor"));
			// Pot.setModificationTrackingEnabled(false); // don't flag all biases modified on construction

			for (final CPLDConfigValue c : cpldConfigValues) {
				c.addObserver(this);
			}
			setBatchEditOccurring(true);
			loadPreferences();
			setBatchEditOccurring(false);
			// try {
			// sendConfiguration(this);
			// } catch (HardwareInterfaceException ex) {
			// log.warning(ex.toString());
			// }
			// log.info("done making biasgen");
		}

		@Override
		final public void loadPreferences() {
			super.loadPreferences();
			if (hasPreferencesList != null) {
				for (final HasPreference hp : hasPreferencesList) {
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
			ipots.storePreferences();
			vpots.storePreferences();
			for (final HasPreference hp : hasPreferencesList) {
				hp.storePreference();
			}
			super.storePreferences();
		}

		JComponent expertTab, basicTab;

		@Override
		public JPanel buildControlPanel() {
			final JPanel panel = new JPanel();
			panel.setLayout(new BorderLayout());
			final JTabbedPane pane = new JTabbedPane();
			pane.addTab("Expert controls", expertTab = new CochleaLPControlPanel(CochleaLP.this)); // creates a new
			// expert panel
			// with all
			// biases
			panel.add(pane, BorderLayout.CENTER);
			pane.setSelectedIndex(getPrefs().getInt("CochleaAMS1c.selectedBiasgenControlTab", 0));
			pane.addMouseListener(new java.awt.event.MouseAdapter() {

				@Override
				public void mouseClicked(final java.awt.event.MouseEvent evt) {
					getPrefs().putInt("CochleaAMS1c.selectedBiasgenControlTab", pane.getSelectedIndex());
				}
			});

			return panel;

			// CochleaAMS1cControlPanel myControlPanel = new CochleaAMS1cControlPanel(CochleaAMS1c.this);
			// return myControlPanel;
		}

		@Override
		public void setHardwareInterface(final BiasgenHardwareInterface hw) {
			// super.setHardwareInterface(hardwareInterface); // don't delegrate to super, handle entire configuration
			// sending here
			if (hw == null) {
				hardwareInterface = null;
				return;
			}
			// if (hw instanceof CochleaAMS1cHardwareInterface) {
			hardwareInterface = hw;
			// cypress = (CypressFX2) hardwareInterface;
			// log.info("set hardwareInterface CochleaAMS1cHardwareInterface=" + hardwareInterface.toString());
			try {
				sendConfiguration();
				// resetAERComm();
			}
			catch (final HardwareInterfaceException ex) {
				net.sf.jaer.biasgen.Biasgen.log.warning(ex.toString());
			}
			// }
		}

		/**
		 * Vendor request command understood by the cochleaAMS1c firmware in connection with
		 * VENDOR_REQUEST_SEND_BIAS_BYTES
		 */
		public final short CMD_IPOT = 1, CMD_RESET_EQUALIZER = 2, CMD_SCANNER = 3, CMD_EQUALIZER = 4, CMD_SETBIT = 5,
			CMD_VDAC = 6, CMD_INITDAC = 7, CMD_CPLD_CONFIG = 8;
		public final String[] CMD_NAMES = { "IPOT", "RESET_EQUALIZER", "SCANNER", "EQUALIZER", "SET_BIT", "VDAC",
			"INITDAC", "CPLD_CONFIG" };
		final byte[] emptyByteArray = new byte[0];

		// /** Does special reset cycle, in background thread TODO unused now */
		// void resetAERComm() {
		// Runnable r = new Runnable() {
		//
		// @Override
		// public void run() {
		// yBit.set(true);
		// aerKillBit.set(false); // after kill bit changed, must wait
		// // nCpldReset.set(true);
		// // yBit.set(true);
		// // aerKillBit.set(false); // after kill bit changed, must wait
		// try {
		// Thread.sleep(50);
		// } catch (InterruptedException e) {
		// }
		// // nCpldReset.set(false);
		// aerKillBit.set(true);
		// try {
		// Thread.sleep(50);
		// } catch (InterruptedException e) {
		// }
		// yBit.set(false);
		// aerKillBit.set(false);
		// log.info("AER communication reset by toggling configuration bits");
		// sendConfiguration();
		// }
		// };
		// Thread t = new Thread(r, "ResetAERComm");
		// t.start();
		// }
		/**
		 * convenience method for sending configuration to hardware. Sends vendor request VENDOR_REQUEST_SEND_BIAS_BYTES
		 * with subcommand cmd, index index and bytes bytes.
		 * cmd is decoded by case switch in firmware to dispatch data properly.
		 *
		 * @param cmd
		 *            the subcommand to set particular configuration, e.g. CMD_CPLD_CONFIG
		 * @param index
		 *            unused
		 * @param bytes
		 *            the payload
		 * @throws HardwareInterfaceException
		 */
		void sendConfig(final int cmd, final int index, byte[] bytes) throws HardwareInterfaceException {

			final boolean debug = false;
			StringBuilder sb = null;
			if (debug) {
				sb = new StringBuilder(String.format(
					"sending command vendor request cmd=0x%x, index=0x%x, with %d bytes ", cmd, index,
					bytes == null ? 0 : bytes.length));
			}
			if ((bytes == null) || (bytes.length == 0)) {
			}
			else if (debug) {
				sb.append(": hex bytes values are ");
				int max = 100;
				if (bytes.length < max) {
					max = bytes.length;
				}
				for (int i = 0; i < max; i++) {
					sb.append(String.format("%02X, ", bytes[i]));
				}
			}
			if (bytes == null) {
				bytes = emptyByteArray;
			}
			if (debug) {
				net.sf.jaer.biasgen.Biasgen.log.info(sb.toString());
			}
			if (getHardwareInterface() != null) {
				if (getHardwareInterface() instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2) {
					((net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2) getHardwareInterface())
						.sendVendorRequest(CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES, (short) (0xffff & cmd),
							(short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
				}
				else if (getHardwareInterface() instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2) {
					((net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2) getHardwareInterface())
						.sendVendorRequest(CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES, (short) (0xffff & cmd),
							(short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
				}

			}
		}

		/**
		 * convenience method for sending configuration to hardware. Sends vendor request VENDOR_REQUEST_SEND_BIAS_BYTES
		 * with subcommand cmd, index index and empty byte array.
		 *
		 * @param cmd
		 * @param index
		 * @throws HardwareInterfaceException
		 */
		void sendConfig(final int cmd, final int index) throws HardwareInterfaceException {
			sendConfig(cmd, index, emptyByteArray);
		}

		/**
		 * The central point for communication with HW from biasgen. All objects in Biasgen are Observables
		 * and add Biasgen.this as Observer. They then call notifyObservers when their state changes.
		 * Objects such as adcProxy store preferences for ADC, and update should update the hardware registers
		 * accordingly.
		 *
		 * @param observable
		 *            IPot, Scanner, etc
		 * @param object
		 *            notifyChange used at present
		 */
		@Override
		public void update(final Observable observable, final Object object) { // thread safe to ensure gui cannot
																				// retrigger this
			// while it is sending something
			if (isBatchEditOccurring()) {
				return;
			}
			// sends a vendor request depending on type of update
			// vendor request is always VR_CONFIG
			// value is the type of update
			// index is sometimes used for 16 bitmask updates
			// bytes are the rest of data
			try {
				if ((observable instanceof IPot) || (observable instanceof BufferIPot)) { // must send all IPot values
					// and set the select to the
					// ipot shift register, this
					// is done by the cypress
					final byte[] bytes = new byte[1 + (ipots.getNumPots() * ipots.getPots().get(0).getNumBytes())];
					int ind = 0;
					final Iterator itr = ipots.getShiftRegisterIterator();
					while (itr.hasNext()) {
						final IPot p = (IPot) itr.next(); // iterates in order of shiftregister index, from Vbpf to VAGC
						final byte[] b = p.getBinaryRepresentation();
						System.arraycopy(b, 0, bytes, ind, b.length);
						ind += b.length;
					}
					bytes[ind] = (byte) bufferIPot.getValue(); // isSet 8 bitmask buffer bias value, this is *last* byte
					// sent because it is at start of biasgen shift register
					sendConfig(CMD_IPOT, 0, bytes); // the usual packing of ipots
				}
				else if (observable instanceof VPot) {
					// There are 2 16-bit AD5391 DACs daisy chained; we need to send data for both
					// to change one of them. We can send all zero bytes to the one we're notifyChange changing and it
					// will notifyChange affect any channel
					// on that DAC. We also take responsibility to formatting all the bytes here so that they can just
					// be piped out
					// surrounded by nSync low during the 48 bit write on the controller.
					final VPot p = (VPot) observable;
					sendDAC(p);
				}
				else if (observable instanceof CPLDConfigValue) {
					// System.out.println(String.format("sending CPLDConfigValue %s",observable));
					// sendCPLDConfig();
					// sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in
					// MSB/LSB, byte[0]=value (1,0)
				}
				else if (observable instanceof Equalizer.EqualizerChannel) {
					// sends 0 byte message (no data phase for speed)
					final Equalizer.EqualizerChannel c = (Equalizer.EqualizerChannel) observable;
					// log.info("Sending "+c);
					final int value = (c.channel << 8) + CMD_EQUALIZER; // value has cmd in LSB, channel in MSB
					final int index = c.qsos + (c.qbpf << 5) + (c.lpfkilled ? 1 << 10 : 0)
						+ (c.bpfkilled ? 1 << 11 : 0); // index
					// has
					// b11=bpfkilled,
					// b10=lpfkilled,
					// b9:5=qbpf,
					// b4:0=qsos
					sendConfig(value, index);
					// System.out.println(String.format("channel=%50s value=%16s index=%16s",c.toString(),Integer.toBinaryString(0xffff&value),Integer.toBinaryString(0xffff&index)));
					// killed byte has 2 lsbs with bitmask 1=lpfkilled, bitmask 0=bpf killed, active high (1=kill,
					// 0=alive)
				}
				else if (observable instanceof Equalizer) {
					// TODO everything is in the equalizer channel, nothing yet in equalizer (e.g global settings)
				}
				else {
					super.update(observable, object); // super (Biasgen) handles others, e.g. masterbias
				}
			}
			catch (final HardwareInterfaceException e) {
				net.sf.jaer.biasgen.Biasgen.log.warning("On update() caught " + e.toString());
			}
		}

		// sends complete configuration information to multiple shift registers and off chip DACs
		public void sendConfiguration() throws HardwareInterfaceException {
			if (!isOpen()) {
				open();
			}
			net.sf.jaer.biasgen.Biasgen.log.info("sending complete configuration");
			update(ipots.getPots().get(0), null); // calls back to update to send all IPot bits
			for (final Pot v : vpots.getPots()) {
				update(v, v);
			}

			for (final Equalizer.EqualizerChannel c : equalizer.channels) {
				update(c, null);
			}
		}

		// sends VR to init DAC
		public void initDAC() throws HardwareInterfaceException {
			sendConfig(CMD_INITDAC, 0);
		}

		void sendDAC(final VPot pot) throws HardwareInterfaceException {
			final int chan = pot.getChannel();
			final int value = pot.getBitValue();
			final byte[] b = new byte[6]; // 2*24=48 bits
			// original firmware code
			// unsigned char dat1 = 0x00; //00 00 0000;
			// unsigned char dat2 = 0xC0; //Reg1=1 Reg0=1 : Write output data
			// unsigned char dat3 = 0x00;
			//
			// dat1 |= (address & 0x0F);
			// dat2 |= ((msb & 0x0F) << 2) | ((lsb & 0xC0)>>6) ;
			// dat3 |= (lsb << 2) | 0x03; // DEBUG; the last 2 bits are actually don't care
			final byte msb = (byte) (0xff & ((0xf00 & value) >> 8));
			final byte lsb = (byte) (0xff & value);
			byte dat1 = 0;
			byte dat2 = (byte) 0xC0;
			byte dat3 = 0;
			dat1 |= (0xff & ((chan % 16) & 0xf));
			dat2 |= ((msb & 0xf) << 2) | ((0xff & ((lsb & 0xc0) >> 6)));
			dat3 |= (0xff & ((lsb << 2)));
			if (chan < 16) { // these are first VPots in list; they need to be loaded first to isSet to the second DAC
				// in the daisy chain
				b[0] = dat1;
				b[1] = dat2;
				b[2] = dat3;
				b[3] = 0;
				b[4] = 0;
				b[5] = 0;
			}
			else { // second DAC VPots, loaded second to end up at start of daisy chain shift register
				b[0] = 0;
				b[1] = 0;
				b[2] = 0;
				b[3] = dat1;
				b[4] = dat2;
				b[5] = dat3;
			}
			// System.out.print(String.format("value=%-6d channel=%-6d ",value,chan));
			// for(byte bi:b) System.out.print(String.format("%2h ", bi&0xff));
			// System.out.println();
			sendConfig(CMD_VDAC, 0, b); // value=CMD_VDAC, index=0, bytes as above
		}

		/**
		 * Resets equalizer channels to default state, and finally does sequence with special bits to ensure hardware
		 * latches are all cleared.
		 *
		 */
		void resetEqualizer() {
			equalizer.reset();
		}

		class BufferIPot extends Observable implements RemoteControlled, PreferenceChangeListener, HasPreference {

			final int max = 63; // 8 bits
			private volatile int value;
			private final String key = "CochleaAMS1c.Biasgen.BufferIPot.value";

			BufferIPot() {
				if (getRemoteControl() != null) {
					getRemoteControl().addCommandListener(this, "setbufferbias bitvalue", "Sets the buffer bias value");
				}
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
				hasPreferencesList.add(this);
			}

			public int getValue() {
				return value;
			}

			public void setValue(int value) {
				if (value > max) {
					value = max;
				}
				else if (value < 0) {
					value = 0;
				}
				if (this.value != value) {
					setChanged();
				}
				this.value = value;

				notifyObservers();
			}

			@Override
			public String toString() {
				return String.format("BufferIPot with max=%d, value=%d", max, value);
			}

			@Override
			public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
				final String[] tok = input.split("\\s");
				if (tok.length < 2) {
					return "bufferbias " + getValue() + "\n";
				}
				else {
					try {
						final int val = Integer.parseInt(tok[1]);
						setValue(val);
					}
					catch (final NumberFormatException e) {
						return "?\n";
					}

				}
				return "bufferbias " + getValue() + "\n";
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent e) {
				if (e.getKey().equals(key)) {
					setValue(Integer.parseInt(e.getNewValue()));
				}
			}

			@Override
			public void loadPreference() {
				setValue(getPrefs().getInt(key, max / 2));
			}

			@Override
			public void storePreference() {
				putPref(key, value);
			}
		}

		/** Handles CPLD configuration shift register. This class maintains the information in the CPLD shift register. */
		class CPLDConfig {

			int numBits, minBit = Integer.MAX_VALUE, maxBit = Integer.MIN_VALUE;
			ArrayList<CPLDConfigValue> cpldConfigValues = new ArrayList();
			boolean[] bits;
			byte[] bytes = null;

			/**
			 * Computes the bits to be sent to the CPLD from all the CPLD config values: bits, tristateable bits, and
			 * ints.
			 * Writes the bits boolean[] so that they are set according to the bit position, e.g. for a bit if
			 * startBit=n, then bits[n] is set.
			 *
			 */
			private void compute() {
				if (minBit > 0) {
					return; // notifyChange yet, we haven't filled in bit 0 yet
				}
				bits = new boolean[maxBit + 1];
				for (final CPLDConfigValue v : cpldConfigValues) {
					if (v instanceof CPLDBit) {
						bits[v.startBit] = ((ConfigBit) v).isSet();
					}
					else if (v instanceof ConfigInt) {
						int i = ((ConfigInt) v).get();
						for (int k = v.startBit; k <= v.endBit; k++) {
							bits[k] = (i & 1) == 1;
							i = i >>> 1;
						}
					}
				}
			}

			void add(final CPLDConfigValue val) {
				if (val.endBit < val.startBit) {
					throw new RuntimeException("bad CPLDConfigValue with endBit<startBit: " + val);
				}

				if (val.endBit > maxBit) {
					maxBit = val.endBit;
				}
				if (val.startBit < minBit) {
					minBit = val.startBit;
				}

				cpldConfigValues.add(val);
				compute();

			}

			/**
			 * Returns byte[] to send to uC to load into CPLD shift register.
			 * This array is returned in big endian order so that
			 * the bytes sent will be sent in big endian order to the device, according to how they are handled in
			 * firmware
			 * and loaded into the CPLD shift register. In other words, the msb of the first byte returned
			 * (getBytes()[0] is the last bit
			 * in the bits[] array of booleans, bit 63 in the case of 64 bits of CPLD SR contents.
			 *
			 */
			private byte[] getBytes() {
				compute();
				int nBytes = bits.length / 8;
				if ((bits.length % 8) != 0) {
					nBytes++;
				}
				if ((bytes == null) || (bytes.length != nBytes)) {
					bytes = new byte[nBytes];
				}
				Arrays.fill(bytes, (byte) 0);
				int byteCounter = 0;
				int bitcount = 0;
				for (int i = bits.length - 1; i >= 0; i--) { // start with msb and go down
					bytes[byteCounter] = (byte) (0xff & (bytes[byteCounter] << 1)); // left shift the bits in this byte
					// that are already there
					// if (bits[i]) {
					// System.out.println("true bit at bit " + i);
					// }
					bytes[byteCounter] = (byte) (0xff & (bytes[byteCounter] | (bits[i] ? 1 : 0))); // set or clear the
					// current bit
					bitcount++;
					if (((bitcount) % 8) == 0) {
						byteCounter++; // go to next byte when we finish each 8 bits
					}
				}
				return bytes;
			}

			@Override
			public String toString() {
				return "CPLDConfig{" + "numBits=" + numBits + ", minBit=" + minBit + ", maxBit=" + maxBit
					+ ", cpldConfigValues=" + cpldConfigValues + ", bits=" + bits + ", bytes=" + bytes + '}';
			}
		}

		/**
		 * A single bit of digital configuration, either controlled by dedicated Cypress port bit
		 * or as part of the CPLD configuration shift register.
		 */
		abstract class AbstractConfigValue extends Observable implements PreferenceChangeListener, HasPreference {

			protected String name, tip;
			protected String key = "AbstractConfigValue";

			public AbstractConfigValue(final String name, final String tip) {
				this.name = name;
				this.tip = tip;
				key = getClass().getSimpleName() + "." + name;
			}

			@Override
			public String toString() {
				return String.format("AbstractConfigValue name=%s key=%s", name, key);
			}

			public String getName() {
				return name;
			}

			@Override
			public synchronized void setChanged() {
				super.setChanged();
			}

			public String getDescription() {
				return tip;
			}
		}

		public class AbstractConfigBit extends AbstractConfigValue implements ConfigBit {

			protected volatile boolean value;
			protected boolean def; // default preference value

			public AbstractConfigBit(final String name, final String tip, final boolean def) {
				super(name, tip);
				this.name = name;
				this.tip = tip;
				this.def = def;
				key = "CochleaAMS1c.Biasgen.ConfigBit." + name;
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
			}

			@Override
			public void set(final boolean value) {
				if (this.value != value) {
					setChanged();
				}
				this.value = value;
				// log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
				notifyObservers();
			}

			@Override
			public boolean isSet() {
				return value;
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent e) {
				if (e.getKey().equals(key)) {
					// log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
					final boolean newv = Boolean.parseBoolean(e.getNewValue());
					set(newv);
				}
			}

			@Override
			public void loadPreference() {
				set(getPrefs().getBoolean(key, def));
			}

			@Override
			public void storePreference() {
				putPref(key, value); // will eventually call pref change listener which will call set again
			}

			@Override
			public String toString() {
				return String.format("AbstractConfigBit name=%s key=%s value=%s", name, key, value);
			}
		}

		class CPLDConfigValue extends AbstractConfigValue {

			protected int startBit, endBit;
			protected int nBits = 8;

			public CPLDConfigValue(final int startBit, final int endBit, final String name, final String tip) {
				super(name, tip);
				this.startBit = startBit;
				this.endBit = endBit;
				nBits = (endBit - startBit) + 1;
				hasPreferencesList.add(this);
				cpldConfigValues.add(this);
				config.add(this);
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent evt) {
			}

			@Override
			public void loadPreference() {
			}

			@Override
			public void storePreference() {
			}

			@Override
			public String toString() {
				return "CPLDConfigValue{" + "name=" + name + " startBit=" + startBit + "endBit=" + endBit + "nBits="
					+ nBits + '}';
			}
		}

		/** A bit output from CPLD port. */
		public class CPLDBit extends CPLDConfigValue implements ConfigBit {

			int pos; // bit position from lsb position in CPLD config
			boolean value;
			boolean def;

			/**
			 * Constructs new CPLDBit.
			 *
			 * @param pos
			 *            position in shift register
			 * @param name
			 *            name label
			 * @param tip
			 *            tool-tip
			 * @param def
			 *            default preferred value
			 */
			public CPLDBit(final int pos, final String name, final String tip, final boolean def) {
				super(pos, pos, name, tip);
				this.pos = pos;
				this.def = def;
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
				// hasPreferencesList.add(this);
			}

			@Override
			public void set(final boolean value) {
				if (this.value != value) {
					setChanged();
				}
				this.value = value;
				// log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
				notifyObservers();
			}

			@Override
			public boolean isSet() {
				return value;
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent e) {
				if (e.getKey().equals(key)) {
					// log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
					final boolean newv = Boolean.parseBoolean(e.getNewValue());
					set(newv);
				}
			}

			@Override
			public void loadPreference() {
				set(getPrefs().getBoolean(key, def));
			}

			@Override
			public void storePreference() {
				putPref(key, value); // will eventually call pref change listener which will call set again
			}

			@Override
			public String toString() {
				return "CPLDBit{" + " name=" + name + " pos=" + pos + " value=" + value + '}';
			}
		}

		/** A integer configuration on CPLD shift register. */
		class CPLDInt extends CPLDConfigValue implements ConfigInt {

			private volatile int value;
			private final int def;

			CPLDInt(final int startBit, final int endBit, final String name, final String tip, final int def) {
				super(startBit, endBit, name, tip);
				this.startBit = startBit;
				this.endBit = endBit;
				this.def = def;
				key = "CochleaAMS1c.Biasgen.CPLDInt." + name;
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
				// hasPreferencesList.add(this);
			}

			@Override
			public void set(final int value) throws IllegalArgumentException {
				if ((value < 0) || (value >= (1 << nBits))) {
					throw new IllegalArgumentException("tried to store value=" + value
						+ " which larger than permitted value of " + (1 << nBits) + " or is negative in " + this);
				}
				if (this.value != value) {
					setChanged();
				}
				this.value = value;
				// log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
				notifyObservers();
			}

			@Override
			public int get() {
				return value;
			}

			@Override
			public String toString() {
				return String.format("CPLDInt name=%s value=%d", name, value);
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent e) {
				if (e.getKey().equals(key)) {
					// log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
					final int newv = Integer.parseInt(e.getNewValue());
					set(newv);
				}
			}

			@Override
			public void loadPreference() {
				set(getPrefs().getInt(key, def));
			}

			@Override
			public void storePreference() {
				putPref(key, value); // will eventually call pref change listener which will call set again
			}
		}

		/**
		 * Encapsulates each channels equalizer. Information is sent to device as
		 * <p>
		 * <img source="doc-files/equalizerBits.png"/> where the index field of the vendor request has the quality and
		 * kill bit information.
		 */
		public class Equalizer extends Observable implements PreferenceChangeListener, HasPreference { // describes the
			// local gain
			// and Q
			// registers and
			// the kill bits

			public static final int NUM_CHANNELS = 128, MAX_VALUE = 31;
			// private int globalGain = 15;
			// private int globalQuality = 15;
			public EqualizerChannel[] channels = new EqualizerChannel[Equalizer.NUM_CHANNELS];
			private boolean rubberBandsEnabled = getPrefs().getBoolean("Equalizer.rubberBandsEnabled", false);
			public final static int RUBBER_BAND_RANGE = 5;
			/**
			 * Header for preferences key for EqualizerChannels
			 */
			public final String CHANNEL_PREFS_HEADER = "CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel.";

			Equalizer() {
				for (int i = 0; i < Equalizer.NUM_CHANNELS; i++) {
					channels[i] = new EqualizerChannel(this, i);
					channels[i].addObserver(Biasgen.this); // CochleaAMS1c.Biasgen observes each equalizer channel
				}
				hasPreferencesList.add(this);
				getPrefs().addPreferenceChangeListener(this);
			}

			/**
			 * Resets equalizer channels to default state, and finally does sequence with special bits to ensure
			 * hardware latches are all cleared.
			 *
			 */
			void reset() {
				net.sf.jaer.biasgen.Biasgen.log.info("resetting all Equalizer states to default");
				for (final EqualizerChannel c : channels) {
					c.reset();
				}
				// TODO special dance with logic bits to reset here

			}

			void setAllLPFKilled(final boolean yes) {
				net.sf.jaer.biasgen.Biasgen.log.info("killing all LPF channels to " + yes);
				for (final EqualizerChannel c : channels) {
					c.setLpfKilled(yes);
				}
				// TODO special dance with logic bits to reset here

			}

			void setAllBPFKilled(final boolean yes) {
				net.sf.jaer.biasgen.Biasgen.log.info("killing all BPF channels to " + yes);
				for (final EqualizerChannel c : channels) {
					c.setBpfKilled(yes);
				}
				// TODO special dance with logic bits to reset here

			}

			void setAllQSOS(final int gain) {
				net.sf.jaer.biasgen.Biasgen.log.info("setting all SOS gains to " + gain);
				for (final EqualizerChannel c : channels) {
					c.setQSOS(gain);
				}
			}

			void setAllQBPF(final int gain) {
				net.sf.jaer.biasgen.Biasgen.log.info("setting all BPF gains to " + gain);
				for (final EqualizerChannel c : channels) {
					c.setQBPF(gain);
				}
			}

			/**
			 * @return the rubberBandsEnabled
			 */
			public boolean isRubberBandsEnabled() {
				return rubberBandsEnabled;
			}

			/**
			 * @param rubberBandsEnabled
			 *            the rubberBandsEnabled to set
			 */
			public void setRubberBandsEnabled(final boolean rubberBandsEnabled) {
				final boolean old = this.rubberBandsEnabled;
				this.rubberBandsEnabled = rubberBandsEnabled;
				getPrefs().putBoolean("Equalizer.rubberBandsEnabled", rubberBandsEnabled);
				if (old != this.rubberBandsEnabled) {
					setChanged();
				}
				notifyObservers(this);
			}

			@Override
			public void preferenceChange(final PreferenceChangeEvent e) {
				if (!e.getKey().startsWith(CHANNEL_PREFS_HEADER)) {
					return;
				}

				// parse our the channel from the prefs key and then set properties of that channel
				final String[] s = e.getKey().split("\\.");
				final int chanNum = Integer.parseInt(s[4]);
				final EqualizerChannel c = channels[chanNum];

				if (s[5].equals("qsos")) {
					c.setQSOS(Integer.parseInt(e.getNewValue()));
				}
				else if (e.getKey().equals("qbpf")) {
					c.setQBPF(Integer.parseInt(e.getNewValue()));
				}
				else if (e.getKey().equals("bpfkilled")) {
					c.setBpfKilled(Boolean.parseBoolean(e.getNewValue()));
				}
				else if (e.getKey().equals("lpfkilled")) {
					c.setLpfKilled(Boolean.parseBoolean(e.getNewValue()));
				}
			}

			@Override
			public void loadPreference() {
				for (final EqualizerChannel c : channels) {
					c.loadPreference();
				}
			}

			@Override
			public void storePreference() {
				for (final EqualizerChannel c : channels) {
					c.storePreference();
				}
			}

			// public int getGlobalGain() {
			// return globalGain;
			// }
			//
			// public void setGlobalGain(int globalGain) {
			// this.globalGain = globalGain;
			// for(EqualizerChannel c:channels){
			// c.setQBPF(globalGain);
			// }
			// }
			//
			// public int getGlobalQuality() {
			// return globalQuality;
			// }
			//
			// public void setGlobalQuality(int globalQuality) {
			// this.globalQuality = globalQuality;
			// for(EqualizerChannel c:channels){
			// c.setQBPF(globalGain);
			// }
			// }
			/**
			 * One equalizer channel
			 */
			public class EqualizerChannel extends Observable implements ChangeListener {

				final int max = 31;
				int channel;
				private final String prefsKey;
				private volatile int qsos;
				private volatile int qbpf;
				private volatile boolean bpfkilled, lpfkilled;

				EqualizerChannel(final Equalizer e, final int n) {
					channel = n;
					prefsKey = CHANNEL_PREFS_HEADER + channel + ".";
					loadPreference();
				}

				@Override
				public String toString() {
					return String.format(
						"EqualizerChannel: channel=%-3d qbpf=%-2d qsos=%-2d bpfkilled=%-6s lpfkilled=%-6s", channel,
						qbpf, qsos, Boolean.toString(bpfkilled), Boolean.toString(lpfkilled));
				}

				public int getQSOS() {
					return qsos;
				}

				public void setQSOS(final int qsos) {
					if (this.qsos != qsos) {
						setChanged();
					}
					this.qsos = qsos;
					notifyObservers();
				}

				public int getQBPF() {
					return qbpf;
				}

				public void setQBPF(final int qbpf) {
					if (this.qbpf != qbpf) {
						setChanged();
					}
					this.qbpf = qbpf;
					notifyObservers();
				}

				// public void setQBPFRubberBand(int qbpf) {
				// if (this.qbpf != qbpf) {
				// setChanged();
				// }
				// this.qbpf = qbpf;
				// notifyObservers();
				// }

				public boolean isLpfKilled() {
					return lpfkilled;
				}

				public void setLpfKilled(final boolean killed) {
					if (killed != lpfkilled) {
						setChanged();
					}
					lpfkilled = killed;
					notifyObservers();
				}

				public boolean isBpfkilled() {
					return bpfkilled;
				}

				public void setBpfKilled(final boolean bpfkilled) {
					if (bpfkilled != this.bpfkilled) {
						setChanged();
					}
					this.bpfkilled = bpfkilled;
					notifyObservers();
				}

				@Override
				public void stateChanged(final ChangeEvent e) {
					if (e.getSource() instanceof CochleaAMS1cControlPanel.EqualizerSlider) {
						final CochleaAMS1cControlPanel.EqualizerSlider s = (CochleaAMS1cControlPanel.EqualizerSlider) e
							.getSource();
						if (s instanceof CochleaAMS1cControlPanel.QSOSSlider) {
							s.channel.setQSOS(s.getValue());
						}
						if (s instanceof CochleaAMS1cControlPanel.QBPFSlider) {
							s.channel.setQBPF(s.getValue());
						}
						// setChanged();
						// notifyObservers();
					}
					else if (e.getSource() instanceof CochleaAMS1cControlPanel.KillBox) {
						final CochleaAMS1cControlPanel.KillBox b = (CochleaAMS1cControlPanel.KillBox) e.getSource();
						if (b instanceof CochleaAMS1cControlPanel.LPFKillBox) {
							b.channel.setLpfKilled(b.isSelected());
							// System.out.println("LPF: "+b.channel.toString());
						}
						else {
							b.channel.setBpfKilled(b.isSelected());
							// System.out.println("BPF: "+b.channel.toString());
						}
						// setChanged();
						// notifyObservers();
					}
				}

				public final void loadPreference() {
					qsos = getPrefs().getInt(prefsKey + "qsos", 15);
					qbpf = getPrefs().getInt(prefsKey + "qbpf", 15);
					bpfkilled = getPrefs().getBoolean(prefsKey + "bpfkilled", false);
					lpfkilled = getPrefs().getBoolean(prefsKey + "lpfkilled", false);
					setChanged();
					notifyObservers();
				}

				public void storePreference() {
					putPref(prefsKey + "bpfkilled", bpfkilled);
					putPref(prefsKey + "lpfkilled", lpfkilled);
					putPref(prefsKey + "qbpf", qbpf);
					putPref(prefsKey + "qsos", qsos);
				}

				private void reset() {
					setBpfKilled(false);
					setLpfKilled(false);
					setQBPF(15);
					setQSOS(15);
				}
			}
		} // equalizer
	}

	/** Used for preamp preferences */
	public enum Ear {

		Left,
		Right,
		Both
	};

	/**
	 * Extract cochlea events from CochleaAMS1c including the ADC samples that are intermixed with cochlea AER data.
	 * <p>
	 * The event class returned by the extractor is CochleaAMSEvent.
	 * <p>
	 * The 10 bits of AER address are mapped as follows
	 *
	 * <pre>
	 * TX0 - AE0
	 * TX1 - AE1
	 * ...
	 * TX7 - AE7
	 * TY0 - AE8
	 * TY1 - AE9
	 * AE15:10 are unused and are unconnected - they should be masked out in software.
	 * </pre>
	 *
	 * <table border="1px">
	 * <tr>
	 * <td>15
	 * <td>14
	 * <td>13
	 * <td>12
	 * <td>11
	 * <td>10
	 * <td>9
	 * <td>8
	 * <td>7
	 * <td>6
	 * <td>5
	 * <td>4
	 * <td>3
	 * <td>2
	 * <td>1
	 * <td>0
	 * <tr>
	 * <td>x
	 * <td>x
	 * <td>x
	 * <td>x
	 * <td>x
	 * <td>x
	 * <td>TH1
	 * <td>TH0
	 * <td>CH5
	 * <td>CH4
	 * <td>CH3
	 * <td>CH2
	 * <td>CH1
	 * <td>CH0
	 * <td>EAR
	 * <td>LPFBPF
	 * </table>
	 * <ul>
	 * <li>
	 * TH1:0 are the ganglion cell. TH1:0=00 is the one biased with Vth1, TH1:0=01 is biased with Vth2, etc. TH1:0=11 is
	 * biased with Vth4. Vth1 and Vth4 are external voltage biaes.
	 * <li>
	 * CH5:0 are the channel address. 0 is the base (input) responsive to high frequencies. 63 is the apex responding to
	 * low frequencies.
	 * <li>
	 * EAR is the binaural ear. EAR=0 is left ear, EAR=1 is right ear.
	 * <li>
	 * LPFBPF is the ganglion cell type. LPFBPF=1 is a low-pass neuron, LPFBPF=0 is a band-pass neuron.
	 * </ul>
	 */
	public class Extractor extends TypedEventExtractor {

		/**
		 *
		 */
		private static final long serialVersionUID = -4649368887960046979L;

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
		synchronized public void extractPacket(final AEPacketRaw in, final EventPacket out) {
			out.clear();
			if (in == null) {
				return;
			}
			final int n = in.getNumEvents(); // addresses.length;

			int skipBy = 1, incEach = 0, j = 0;
			if (isSubSamplingEnabled()) {
				skipBy = n / getSubsampleThresholdEventCount();
				incEach = getSubsampleThresholdEventCount() / (n % getSubsampleThresholdEventCount());
			}
			if (skipBy == 0) {
				incEach = 0;
				skipBy = 1;
			}

			final int[] a = in.getAddresses();
			final int[] timestamps = in.getTimestamps();
			// boolean hasTypes = false;
			// if (chip != null) {
			// hasTypes = chip.getNumCellTypes() > 1;
			// }
			final OutputEventIterator<?> outItr = out.outputIterator();
			for (int i = 0; i < n; i += skipBy) {
				final int addr = a[i];
				final int ts = timestamps[i];
				if (CochleaAMS1cHardwareInterface.isAERAddress(addr)) {
					final CochleaAMSEvent e = (CochleaAMSEvent) outItr.nextOutput();
					e.address = addr;
					e.timestamp = (ts);
					e.x = getXFromAddress(addr);
					e.y = getYFromAddress(addr);
					e.type = (byte) e.y; // overridden to just set cell type uniquely
					j++;
					if (j == incEach) {
						j = 0;
						i++;
					}
					// System.out.println("timestamp=" + e.timestamp + " address=" + addr);
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
			final short tap = (short) (((addr & 0xfc) >>> 2)); // addr&(1111 1100) >>2, 6 bits, max 63, min 0
			return tap;
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
			// return (byte)((addr&0x02)>>>1);
			return (byte) getYFromAddress(addr);
		}

		/**
		 * Overrides default extractor to spread all outputs from a tap (left/right, ganglion cell, LPF/HPF) into a
		 * single unique y address that can be displayed in the 2d histogram.
		 * The y returned goes like this from 0-15: left LPF(4) right LPF(4) left BPF(4) right BPF(4). Eech group of 4
		 * ganglion cells goes
		 * from Vth1 to Vth4.
		 *
		 * @param addr
		 *            the raw address
		 * @return the Y address
		 */
		@Override
		public short getYFromAddress(final int addr) {
			// int gangCell=(addr&0x300)>>>8; // each tap has 8 ganglion cells, 4 of each of LPF/BPF type
			// int lpfBpf=(addr&0x01)<<2; // lowpass/bandpass ganglion cell type
			// int leftRight=(addr&0x02)<<2; // left/right cochlea. see javadoc jpg scan for layout
			// short v=(short)(gangCell+lpfBpf+leftRight);
			final int lpfBpf = (addr & 0x01) << 3; // LPF=8 BPF=0 ganglion cell type
			final int rightLeft = (addr & 0x02) << 1; // right=4 left=0 cochlea
			final int thr = (0x300 & addr) >> 8; // thr=0 to 4
			final short v = (short) (lpfBpf + rightLeft + thr);
			return v;
		}
	}
}
