/*
 * CochleaAMS1b.java
 *
 * Created on November 7, 2006, 11:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 7, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.chip.cochlea;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Observable;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.SpaceTimeEventDisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Extends Shih-Chii's AMS cochlea AER chip to
 * add bias generator interface,
 * to be used when using the on-chip bias generator and the on-board DACs. Also implemements ConfigBits, Scanner, and Equalizer configuration.
 * @author tobi
 */
@Description("Binaural AER silicon cochlea with 64 channels and 8 ganglion cells of two types per channel")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class CochleaAMS1b extends CochleaAMSNoBiasgen {

	//    // biasgen components implement this interface to send their own messages
	//    interface ConfigurationSender {
	//
	//        void sendConfiguration();
	//    }
	final GLUT glut = new GLUT();

	/** Creates a new instance of CochleaAMS1b */
	public CochleaAMS1b() {
		super();
		setBiasgen(new CochleaAMS1b.Biasgen(this));
		getCanvas().setBorderSpacePixels(40);
		for (DisplayMethod m : getCanvas().getDisplayMethods()) {
			if ((m instanceof ChipRendererDisplayMethod) || (m instanceof SpaceTimeEventDisplayMethod)) {
				m.addAnnotator(new FrameAnnotater() {

					@Override
					public void setAnnotationEnabled(boolean yes) {
					}

					@Override
					public boolean isAnnotationEnabled() {
						return true;
					}

					public void annotate(float[][][] frame) {
					}

					public void annotate(Graphics2D g) {
					}

					// renders the string starting at x,y,z with angleDeg angle CCW from horizontal in degrees
					void renderStrokeFontString(GL2 gl, float x, float y, float z, float angleDeg, String s) {
						final int font = GLUT.STROKE_ROMAN;
						final float scale = 2f / 104f; // chars will be about 1 pixel wide
						gl.glPushMatrix();
						gl.glTranslatef(x, y, z);
						gl.glRotatef(angleDeg, 0, 0, 1);
						gl.glScalef(scale, scale, scale);
						gl.glLineWidth(2);
						for (char c : s.toCharArray()) {
							glut.glutStrokeCharacter(font, c);
						}
						gl.glPopMatrix();
					}                    // chars about 104 model units wide
					final float xlen = glut.glutStrokeLength(glut.STROKE_ROMAN, "channel"),  ylen = glut.glutStrokeLength(glut.STROKE_ROMAN, "cell type");

					@Override
					public void annotate(GLAutoDrawable drawable) {
						GL2 gl = drawable.getGL().getGL2();
						//                        gl.glBegin(GL2.GL_LINES);
						//                        gl.glColor3f(.5f, .5f, 0);
						//                        gl.glVertex2f(0, 0);
						//                        gl.glVertex2f(getSizeX() - 1, getSizeY() - 1);
						//                        gl.glEnd();
						gl.glPushMatrix();
						{
							gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
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

	/** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
	 * Sets the hardware interface and the bias generators hardware interface
	 *@param hardwareInterface the interface
	 */
	@Override
	public void setHardwareInterface(final HardwareInterface hardwareInterface) {
		this.hardwareInterface = hardwareInterface;
		try {
			if (getBiasgen() == null) {
				setBiasgen(new CochleaAMS1b.Biasgen(this));
			} else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface); // from blank device we get bare CypressFX2 which is not BiasgenHardwareInterface so biasgen hardware interface is not set yet
			}
		} catch (ClassCastException e) {
			System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	/**
	 * Describes IPots on tmpdiff128 retina chip. These are configured by a shift register as shown here:
	 *<p>
	 *<img src="doc-files/tmpdiff128biasgen.gif" alt="tmpdiff128 shift register arrangement"/>

    <p>
    This bias generator also offers an abstracted ChipControlPanel interface that is used for a simplified user interface.
	 *
	 * @author tobi
	 */
	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements ChipControlPanel {

		private ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
		/** The DAC on the board. Specified with 5V reference even though Vdd=3.3 because the internal 2.5V reference is used and so that the VPot controls display correct voltage. */
		protected final DAC dac = new DAC(32, 12, 0, 5f, 3.3f); // the DAC object here is actually 2 16-bit DACs daisy-chained on the Cochlea board; both corresponding values need to be sent to change one value
		IPotArray ipots = new IPotArray(this);
		PotArray vpots = new PotArray(this);
		//        private IPot diffOn, diffOff, refr, pr, sf, diff;
		private CypressFX2 cypress = null;
		// tobi changed config bits on rev1 board since e3/4 control maxim mic preamp attack/release and gain now
		private ConfigBit aerKillBit, vResetBit, yBit, selAER, selIn, powerDown, /* resCtrBit1, resCtrBit2, */ preampAR, preampGain;
		volatile ConfigBit[] configBits = {
			powerDown = new ConfigBit("d5", "powerDown", "turns off on-chip biases (1=turn off, 0=normal)"),
				//          resCtrBit2=new ConfigBit("e4", "resCtrBit2", "preamp gain bit 1 (msb) (0=lower gain, 1=higher gain)"),
				//            resCtrBit1=new ConfigBit("e3", "resCtrBit1", "preamp gain bit 0 (lsb) (0=lower gain, 1=higher gain)"),
				preampAR = new TriStateableConfigBit("e4", "preampAR", "preamp attack/release (0=attack/release ratio=1:500, 1=A/R=1:2000, HiZ=A/R=1:4000 (may not work))"), // may not work for 1:4000 because spec is to connect pin to BIAS
				preampGain = new TriStateableConfigBit("e3", "preampGain", "preamp gain bit (1=gain=40dB, 0=gain=50dB, HiZ=60dB if preamp threshold \"PreampAGCThreshold (TH)\"is set above 2V)"),
				vResetBit = new ConfigBit("e5", "Vreset", "global latch reset (1=reset, 0=run)"),
				selIn = new ConfigBit("e6", "selIn", "Parallel (1) or Cascaded (0) Arch"),
				selAER = new ConfigBit("d3", "selAER", "Chooses whether lpf (0) or rectified (1) lpf output drives lpf neurons"),
				yBit = new ConfigBit("d2", "YBit", "Used to select which neurons to kill"),
				aerKillBit = new ConfigBit("d6", "AERKillBit", "Set high to kill channel"), /*
        #define DataSel 	1	// selects data shift register path (bitIn, clock, latch)
        #define AddrSel 	2	// selects channel selection shift register path
        #define BiasGenSel 	4	// selects biasgen shift register path
        #define ResCtr1 	8	// a preamp feedback resistor selection bitmask
        #define ResCtr2 	16	// another microphone preamp feedback resistor selection bitmask
        #define Vreset		32	// (1) to reset latch states
        #define SelIn		64	// Parallel (0) or Cascaded (1) Arch
        #define Ybit		128	// Chooses whether lpf (0) or bpf (1) neurons to be killed, use in conjunction with AddrSel and AERKillBit
				 */};
		Scanner scanner = new Scanner();
		Equalizer equalizer = new Equalizer();
		BufferIPot bufferIPot = new BufferIPot();
		boolean dacPowered = getPrefs().getBoolean("CochleaAMS1b.Biasgen.DAC.powered", true);
		//        private PropertyChangeSupport support = new PropertyChangeSupport(this);

		/** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
		 *@param chip the chip this biasgen belongs to
		 */
		public Biasgen(Chip chip) {
			super(chip);
			setName("CochleaAMS1b");
			scanner.addObserver(this);
			equalizer.addObserver(this);
			bufferIPot.addObserver(this);
			for (ConfigBit b : configBits) {
				b.addObserver(this);
			}

			//    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {

			potArray = new IPotArray(this); //construct IPotArray whit shift register stuff

			ipots.addPot(new IPot(this, "VAGC", 0, IPot.Type.NORMAL, IPot.Sex.N, 0, 1, "Sets reference for AGC diffpair in SOS"));  // second to list bits loaded, just before buffer bias bits. displayed first in GUI
			ipots.addPot(new IPot(this, "Curstartbpf", 1, IPot.Type.NORMAL, IPot.Sex.P, 0, 2, "Sets master current to local DACs for BPF Iq"));
			ipots.addPot(new IPot(this, "DacBufferNb", 2, IPot.Type.NORMAL, IPot.Sex.N, 0, 3, "Sets bias current of amp in local DACs"));
			ipots.addPot(new IPot(this, "Vbp", 3, IPot.Type.NORMAL, IPot.Sex.P, 0, 4, "Sets bias for readout amp of BPF"));
			ipots.addPot(new IPot(this, "Ibias20OpAmp", 4, IPot.Type.NORMAL, IPot.Sex.P, 0, 5, "Bias current for preamp"));
			ipots.addPot(new IPot(this, "N.C.", 5, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "not used"));
			ipots.addPot(new IPot(this, "Vsetio", 6, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Sets 2I0 and I0 for LPF time constant"));
			ipots.addPot(new IPot(this, "Vdc1", 7, IPot.Type.NORMAL, IPot.Sex.P, 0, 8, "Sets DC shift for close end of cascade"));
			ipots.addPot(new IPot(this, "NeuronRp", 8, IPot.Type.NORMAL, IPot.Sex.P, 0, 9, "Sets bias current of neuron"));
			ipots.addPot(new IPot(this, "Vclbtgate", 9, IPot.Type.NORMAL, IPot.Sex.P, 0, 10, "Bias gate of CLBT"));
			ipots.addPot(new IPot(this, "Vioff", 10, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "Sets DC shift input to LPF"));
			ipots.addPot(new IPot(this, "Vbias2", 11, IPot.Type.NORMAL, IPot.Sex.P, 0, 12, "Sets lower cutoff freq for cascade"));
			ipots.addPot(new IPot(this, "Ibias10OpAmp", 12, IPot.Type.NORMAL, IPot.Sex.P, 0, 13, "Bias current for preamp"));
			ipots.addPot(new IPot(this, "Vthbpf2", 13, IPot.Type.CASCODE, IPot.Sex.P, 0, 14, "Sets high end of threshold current for bpf neurons"));
			ipots.addPot(new IPot(this, "Follbias", 14, IPot.Type.NORMAL, IPot.Sex.N, 0, 15, "Bias for PADS"));
			ipots.addPot(new IPot(this, "pdbiasTX", 15, IPot.Type.NORMAL, IPot.Sex.N, 0, 16, "pulldown for AER TX"));
			ipots.addPot(new IPot(this, "Vrefract", 16, IPot.Type.NORMAL, IPot.Sex.N, 0, 17, "Sets refractory period for AER neurons"));
			ipots.addPot(new IPot(this, "VbampP", 17, IPot.Type.NORMAL, IPot.Sex.P, 0, 18, "Sets bias current for input amp to neurons"));
			ipots.addPot(new IPot(this, "Vcascode", 18, IPot.Type.CASCODE, IPot.Sex.N, 0, 19, "Sets cascode voltage"));
			ipots.addPot(new IPot(this, "Vbpf2", 19, IPot.Type.NORMAL, IPot.Sex.P, 0, 20, "Sets lower cutoff freq for BPF"));
			ipots.addPot(new IPot(this, "Ibias10OTA", 20, IPot.Type.NORMAL, IPot.Sex.N, 0, 21, "Bias current for OTA in preamp"));
			ipots.addPot(new IPot(this, "Vthbpf1", 21, IPot.Type.CASCODE, IPot.Sex.P, 0, 22, "Sets low end of threshold current to bpf neurons"));
			ipots.addPot(new IPot(this, "Curstart ", 22, IPot.Type.NORMAL, IPot.Sex.P, 0, 23, "Sets master current to local DACs for SOS Vq"));
			ipots.addPot(new IPot(this, "Vbias1", 23, IPot.Type.NORMAL, IPot.Sex.P, 0, 24, "Sets higher cutoff freq for SOS"));
			ipots.addPot(new IPot(this, "NeuronVleak", 24, IPot.Type.NORMAL, IPot.Sex.P, 0, 25, "Sets leak current for neuron"));
			ipots.addPot(new IPot(this, "Vioffbpfn", 25, IPot.Type.NORMAL, IPot.Sex.N, 0, 26, "Sets DC level for input to bpf"));
			ipots.addPot(new IPot(this, "Vcasbpf", 26, IPot.Type.CASCODE, IPot.Sex.P, 0, 27, "Sets cascode voltage in cm BPF"));
			ipots.addPot(new IPot(this, "Vdc2", 27, IPot.Type.NORMAL, IPot.Sex.P, 0, 28, "Sets DC shift for SOS at far end of cascade"));
			ipots.addPot(new IPot(this, "Vterm", 28, IPot.Type.CASCODE, IPot.Sex.N, 0, 29, "Sets bias current of terminator xtor in diffusor"));
			ipots.addPot(new IPot(this, "Vclbtcasc", 29, IPot.Type.CASCODE, IPot.Sex.P, 0, 30, "Sets cascode voltage in CLBT"));
			ipots.addPot(new IPot(this, "reqpuTX", 30, IPot.Type.NORMAL, IPot.Sex.P, 0, 31, "Sets pullup bias for AER req ckts"));
			ipots.addPot(new IPot(this, "Vbpf1", 31, IPot.Type.NORMAL, IPot.Sex.P, 0, 32, "Sets higher cutoff freq for BPF"));   // first bits loaded, at end of shift register



			//    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
			// top dac in schem/layout, first 16 channels of 32 total
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vterm", dac,          0, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "Sets bias current of terminator xtor in diffusor"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefhres", dac,       1, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "VthAGC", dac,         2, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefreadout", dac,    3, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			//            vpots.addPot(new VPot(CochleaAMS1b.this, "Vbpf2x", dac,         4, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "BiasDACBufferNBias", dac,         4, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "DAC buffer bias for ???"));
			//            vpots.addPot(new VPot(CochleaAMS1b.this, "Vbias2x", dac,        5, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefract", dac,        5, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			//            vpots.addPot(new VPot(CochleaAMS1b.this, "Vbpf1x", dac,         6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "PreampAGCThreshold (TH)", dac,         6, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Threshold for microphone preamp AGC gain reduction turn-on"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefpreamp", dac,     7, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			//            vpots.addPot(new VPot(CochleaAMS1b.this, "Vbias1x", dac,        8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "NeuronRp", dac,        8, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets bias current of neuron - overrides onchip bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vthbpf1x", dac,       9, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vioffbpfn", dac,      10, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "NeuronVleak", dac,    11, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Sets leak current for neuron - not connected on board"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "DCOutputLevel", dac,  12, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "Microphone DC output level to cochlea chip"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vthbpf2x", dac,       13, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "DACSpOut2", dac,      14, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "DACSpOut1", dac,      15, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));

			// bot DAC in schem/layout, 2nd 16 channels
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vth4", dac,           16, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vcas2x", dac,         17, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefo", dac,          18, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefn2", dac,         19, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vq", dac,             20, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vcassyni", dac,       21, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vgain", dac,          22, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vrefn", dac,          23, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "VAI0", dac,           24, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vdd1", dac,           25, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vth1", dac,           26, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vref", dac,           27, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vtau", dac,           28, Pot.Type.NORMAL, Pot.Sex.P, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "VcondVt", dac,        29, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vpm", dac,            30, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			vpots.addPot(new VPot(CochleaAMS1b.this, "Vhm", dac,            31, Pot.Type.NORMAL, Pot.Sex.N, 0, 0, "test dac bias"));
			//            Pot.setModificationTrackingEnabled(false); // don't flag all biases modified on construction
			loadPreferences();
			//            Pot.setModificationTrackingEnabled(true);
		}

		@Override
		public void loadPreferences() {
			super.loadPreferences();
			if (ipots != null) {
				ipots.loadPreferences();
			}
			if (vpots != null) {
				vpots.loadPreferences();
			}
			if (hasPreferencesList != null) {
				for (HasPreference hp : hasPreferencesList) {
					hp.loadPreference();
				}
			}
		}

		@Override
		public void storePreferences() {
			ipots.storePreferences();
			vpots.storePreferences();
			for (HasPreference hp : hasPreferencesList) {
				hp.storePreference();
			}
			super.storePreferences();
		}
                
                JComponent expertTab, basicTab;

		@Override
		public JPanel buildControlPanel() {     //added new control panel for AMS1b, similiar look as DVS128 control panel @philipp
                    JPanel panel = new JPanel();
                    panel.setLayout(new BorderLayout());
                    final JTabbedPane pane = new JTabbedPane();
                    pane.addTab("Basic controls", basicTab = new CochleaAMS1bBasicPanel(CochleaAMS1b.this));      //creates a new basic (user friendly) panel
                    pane.addTab("Expert controls", expertTab = new CochleaAMS1bControlPanel(CochleaAMS1b.this));  //creates a new expert panel with all biases
                    panel.add(pane, BorderLayout.CENTER);
                    pane.setSelectedIndex(getPrefs().getInt("CochleaAMS1b.selectedBiasgenControlTab", 0));
                    pane.addMouseListener(new java.awt.event.MouseAdapter() {

                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent evt) {
                         getPrefs().putInt("CochleaAMS1b.selectedBiasgenControlTab", pane.getSelectedIndex());
                    }
                    });

                    return panel;

		//	CochleaAMS1bControlPanel myControlPanel = new CochleaAMS1bControlPanel(CochleaAMS1b.this);
		//	return myControlPanel;
		}

		@Override
		public void setHardwareInterface(BiasgenHardwareInterface hw) {
			//            super.setHardwareInterface(hardwareInterface); // don't delegrate to super, handle entire configuration sending here
			if (hw == null) {
				cypress = null;
				hardwareInterface = null;
				return;
			}
			if (hw instanceof CochleaAMS1bHardwareInterface) {
				hardwareInterface = hw;
				cypress = (CypressFX2) hardwareInterface;
				log.info("set hardwareInterface CochleaAMS1bHardwareInterface=" + hardwareInterface.toString());
				sendConfiguration();
				resetAERComm();
			}
		}
		final short CMD_IPOT = 1,  CMD_RESET_EQUALIZER = 2,  CMD_SCANNER = 3,  CMD_EQUALIZER = 4,  CMD_SETBIT = 5,  CMD_VDAC = 6,  CMD_INITDAC = 7;
		final byte[] emptyByteArray = new byte[0];

		/** Does special reset cycle, in background thread */
		void resetAERComm() {
			Runnable r = new Runnable() {

				@Override
				public void run() {
					yBit.set(true);
					aerKillBit.set(false); // after kill bit changed, must wait
					vResetBit.set(true);
					//  yBit.set(true);
					//           aerKillBit.set(false); // after kill bit changed, must wait
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
					vResetBit.set(false);
					aerKillBit.set(true);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
					yBit.set(false);
					aerKillBit.set(false);
					log.info("AER communication reset by toggling configuration bits");
					sendConfiguration();
				}
			};
			Thread t = new Thread(r, "ResetAERComm");
			t.start();
		}

		// convenience method
		void sendCmd(int cmd, int index, byte[] bytes) throws HardwareInterfaceException {
			if (bytes == null) {
				bytes = emptyByteArray;
			}
			//            log.info(String.format("sending command vendor request cmd=%d, index=%d, and %d bytes", cmd, index, bytes.length));
			if (cypress != null) {
				cypress.sendVendorRequest(CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES, (short) (0xffff & cmd), (short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
			}
		}
		// no data phase, just value, index

		void sendCmd(int cmd, int index) throws HardwareInterfaceException {
			sendCmd(cmd, index, emptyByteArray);
		}

		/** The central point for communication with HW from biasgen. All objects in Biasgen are Observables
        and add Biasgen.this as Observer. They then call notifyObservers when their state changes.
		 * @param observable IPot, Scanner, etc
		 * @param object not used at present
		 */
		@Override
		synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
			//            log.info(observable + " sent " + object);
			if (cypress == null) {
				return;
			}
			// sends a vendor request depending on type of update
			// vendor request is always VR_CONFIG
			// value is the type of update
			// index is sometimes used for 16 bitmask updates
			// bytes are the rest of data
			try {
				if ((observable instanceof IPot) || (observable instanceof BufferIPot)) { // must send all IPot values and set the select to the ipot shift register, this is done by the cypress
					byte[] bytes = new byte[1 + (ipots.getNumPots() * ipots.getPots().get(0).getNumBytes())];
					int ind = 0;
					Iterator itr = ipots.getShiftRegisterIterator();
					while (itr.hasNext()) {
						IPot p = (IPot) itr.next(); // iterates in order of shiftregister index, from Vbpf to VAGC
						byte[] b = p.getBinaryRepresentation();
						System.arraycopy(b, 0, bytes, ind, b.length);
						ind += b.length;
					}
					bytes[ind] = (byte) bufferIPot.getValue(); // get 8 bitmask buffer bias value, this is *last* byte sent because it is at start of biasgen shift register
					sendCmd(CMD_IPOT, 0, bytes); // the usual packing of ipots
				} else if (observable instanceof VPot) {
					// There are 2 16-bit AD5391 DACs daisy chained; we need to send data for both
					// to change one of them. We can send all zero bytes to the one we're not changing and it will not affect any channel
					// on that DAC. We also take responsibility to formatting all the bytes here so that they can just be piped out
					// surrounded by nSync low during the 48 bit write on the controller.
					VPot p = (VPot) observable;
					sendDAC(p);
				}else if (observable instanceof TriStateableConfigBit) { // tristateable should come first before configbit since it is subclass
					TriStateableConfigBit b = (TriStateableConfigBit) observable;
					byte[] bytes = {(byte)((b.get() ? (byte) 1 : (byte) 0)|(b.isHiZEnabled()? (byte)2: (byte)0))};
					sendCmd(CMD_SETBIT, b.portbit, bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]= OR of value (1,0), hiZ=2/0, bit is set if tristate, unset if driving port
				} else if (observable instanceof ConfigBit) {
					ConfigBit b = (ConfigBit) observable;
					byte[] bytes = {b.get() ? (byte) 1 : (byte) 0};
					sendCmd(CMD_SETBIT, b.portbit, bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)
				} else if (observable instanceof Scanner) {
					byte[] bytes = new byte[1];
					int index = 0;
					if (scanner.isContinuousScanningEnabled()) { // only one scanner so don't need to get it
						bytes[0] = (byte) (0xFF & scanner.getPeriod()); // byte is unsigned char on fx2 and we make sure the sign bit is left unsigned
						index = 1;
					} else {
						bytes[0] = (byte) (scanner.getCurrentStage() & 0xFF); // don't do something funny casting to signed byte
						index = 0;
					}
					sendCmd(CMD_SCANNER, index, bytes); // sends CMD_SCANNER as value, index=1 for continuous index=0 for channel. if index=0, byte[0] has channel, if index=1, byte[0] has period
				} else if (observable instanceof Equalizer.EqualizerChannel) {
					// sends 0 byte message (no data phase for speed)
					Equalizer.EqualizerChannel c = (Equalizer.EqualizerChannel) observable;
					int value = (c.channel << 8) + CMD_EQUALIZER; // value has cmd in LSB, channel in MSB
					int index = c.qsos + (c.qbpf << 5) + (c.lpfkilled ? 1 << 10 : 0) + (c.bpfkilled ? 1 << 11 : 0); // index has b11=bpfkilled, b10=lpfkilled, b9:5=qbpf, b4:0=qsos
					sendCmd(value, index);
					//                        System.out.println(String.format("channel=%50s value=%16s index=%16s",c.toString(),Integer.toBinaryString(0xffff&value),Integer.toBinaryString(0xffff&index)));
					// killed byte has 2 lsbs with bitmask 1=lpfkilled, bitmask 0=bpf killed, active high (1=kill, 0=alive)
				} else if (observable instanceof Equalizer) {
					// TODO everything is in the equalizer channel, nothing yet in equalizer (e.g global settings)
				}else {
					super.update(observable, object);  // super (Biasgen) handles others, e.g. maasterbias
				}
			} catch (HardwareInterfaceException e) {
				log.warning(e.toString());
			}
		}

		void sendConfiguration() {
			try {
				if (!isOpen()) {
					open();
				}
			} catch (HardwareInterfaceException e) {
				log.warning("opening device to send configuration: " + e);
				return;
			}
			log.info("sending complete configuration");
			update(ipots.getPots().get(0), null);
			for (Pot v : vpots.getPots()) {
				update(v, v);
			}
			try {
				setDACPowered(isDACPowered());
			} catch (HardwareInterfaceException ex) {
				log.warning("setting power state of DACs: " + ex);
			}
			for (ConfigBit b : configBits) {
				update(b, b);
			}
			update(scanner, scanner);
			for (Equalizer.EqualizerChannel c : equalizer.channels) {
				update(c, null);
			}

		}

		// sends VR to init DAC
		public void initDAC() throws HardwareInterfaceException {
			sendCmd(CMD_INITDAC, 0);
		}

		void sendDAC(VPot pot) throws HardwareInterfaceException {
			int chan = pot.getChannel();
			int value = pot.getBitValue();
			byte[] b = new byte[6]; // 2*24=48 bits
				// original firmware code
			//            unsigned char dat1 = 0x00; //00 00 0000;
			//            unsigned char dat2 = 0xC0; //Reg1=1 Reg0=1 : Write output data
			//            unsigned char dat3 = 0x00;
			//
			//            dat1 |= (address & 0x0F);
			//            dat2 |= ((msb & 0x0F) << 2) | ((lsb & 0xC0)>>6) ;
			//            dat3 |= (lsb << 2) | 0x03; // DEBUG; the last 2 bits are actually don't care
			byte msb = (byte) (0xff & ((0xf00 & value) >> 8));
			byte lsb = (byte) (0xff & value);
			byte dat1 = 0;
			byte dat2 = (byte) 0xC0;
			byte dat3 = 0;
			dat1 |= (0xff & ((chan % 16) & 0xf));
			dat2 |= ((msb & 0xf) << 2) | ((0xff & ((lsb & 0xc0) >> 6)));
			dat3 |= (0xff & ((lsb << 2)));
			if (chan < 16) { // these are first VPots in list; they need to be loaded first to get to the second DAC in the daisy chain
				b[0] = dat1;
				b[1] = dat2;
				b[2] = dat3;
				b[3] = 0;
				b[4] = 0;
				b[5] = 0;
			} else { // second DAC VPots, loaded second to end up at start of daisy chain shift register
				b[0] = 0;
				b[1] = 0;
				b[2] = 0;
				b[3] = dat1;
				b[4] = dat2;
				b[5] = dat3;
			}
			//            System.out.print(String.format("value=%-6d channel=%-6d ",value,chan));
			//            for(byte bi:b) System.out.print(String.format("%2h ", bi&0xff));
			//            System.out.println();
			sendCmd(CMD_VDAC, 0, b); // value=CMD_VDAC, index=0, bytes as above
		}

		/** Sets the VDACs on the board to be powered or high impedance output. This is a global operation.
		 * 
		 * @param yes true to power up DACs
		 * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
		 */
		public void setDACPowered(boolean yes) throws HardwareInterfaceException {
			putPref("CochleaAMS1b.Biasgen.DAC.powered", yes);
			byte[] b = new byte[6];
			Arrays.fill(b, (byte) 0);
			final byte up = (byte) 9,  down = (byte) 8;
			if (yes) {
				b[0] = up;
				b[3] = up; // sends 09 00 00 to each DAC which is soft powerup
			} else {
				b[0] = down;
				b[3] = down;
			}
			sendCmd(CMD_VDAC, 0, b);
		}

		/** Returns the DAC powered state
		 * 
		 * @return true if powered up
		 */
		public boolean isDACPowered() {
			return dacPowered;
		}

		//        public PropertyChangeSupport getSupport() {
		//            return support;
		//        }
		class BufferIPot extends Observable implements RemoteControlled, PreferenceChangeListener, HasPreference {

			final int max = 63; // 8 bits
			private volatile int value;
			private final String key = "CochleaAMS1b.Biasgen.BufferIPot.value";

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
				} else if (value < 0) {
					value = 0;
				}
				this.value = value;

				setChanged();
				notifyObservers();
			}

			@Override
			public String toString() {
				return String.format("BufferIPot with max=%d, value=%d", max, value);
			}

			@Override
			public String processRemoteControlCommand(RemoteControlCommand command, String input) {
				String[] tok = input.split("\\s");
				if (tok.length < 2) {
					return "bufferbias " + getValue() + "\n";
				} else {
					try {
						int val = Integer.parseInt(tok[1]);
						setValue(val);
					} catch (NumberFormatException e) {
						return "?\n";
					}

				}
				return "bufferbias " + getValue() + "\n";
			}

			@Override
			public void preferenceChange(PreferenceChangeEvent e) {
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

		/** A single bitmask of digital configuration */
		class ConfigBit extends Observable implements PreferenceChangeListener, HasPreference {

			int port;
			short portbit; // has port as char in MSB, bitmask in LSB
			int bitmask;
			private volatile boolean value;
			String name, tip;
			String key;
			String portBitString;

			ConfigBit(String portBit, String name, String tip) {
				if ((portBit == null) || (portBit.length() != 2)) {
					throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters");
				}
				String s = portBit.toLowerCase();
				if (!(s.startsWith("c") || s.startsWith("d") || s.startsWith("e"))) {
					throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with C, D, or E");
				}
				portBitString = portBit;
				char ch = s.charAt(0);
				switch (ch) {
					case 'c':
						port = 0;
						break;
					case 'd':
						port = 1;
						break;
					case 'e':
						port = 2;
						break;
					default:
						throw new Error("BitConfig portBit=" + portBit + " but must be 2 characters and start with C, D, or E");
				}
				bitmask = 1 << Integer.valueOf(s.substring(1, 2));
				portbit = (short) (0xffff & ((port << 8) + (0xff & bitmask)));
				this.name = name;
				this.tip = tip;
				key = "CochleaAMS1b.Biasgen.BitConfig." + name;
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
				hasPreferencesList.add(this);
			}

			void set(boolean value) {
				this.value = value;
				//                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
				setChanged();
				notifyObservers();
			}

			boolean get() {
				return value;
			}

			@Override
			public String toString() {
				return String.format("ConfigBit name=%s portbit=%s value=%s", name, portBitString, Boolean.toString(value));
			}

			@Override
			public void preferenceChange(PreferenceChangeEvent e) {
				if (e.getKey().equals(key)) {
					//                    log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
					boolean newv = Boolean.parseBoolean(e.getNewValue());
					set(newv);
				}
			}

			@Override
			public void loadPreference() {
				set(getPrefs().getBoolean(key, false));
			}

			@Override
			public void storePreference() {
				putPref(key, value); // will eventually call pref change listener which will call set again
			}
		}

		/** Adds a hiZ state to the bit to set port bit to input */
		class TriStateableConfigBit extends ConfigBit {

			private volatile boolean hiZEnabled = false;
			String hiZKey;

			TriStateableConfigBit(String portBit, String name, String tip) {
				super(portBit, name, tip);
				hiZKey = "CochleaAMS1b.Biasgen.BitConfig." + name + ".hiZEnabled";
				loadPreference();
			}

			/**
			 * @return the hiZEnabled
			 */
			public boolean isHiZEnabled() {
				return hiZEnabled;
			}

			/**
			 * @param hiZEnabled the hiZEnabled to set
			 */
			public void setHiZEnabled(boolean hiZEnabled) {
				this.hiZEnabled = hiZEnabled;
				setChanged();
				notifyObservers();
			}

			@Override
			public String toString() {
				return String.format("TriStateableConfigBit name=%s portbit=%s value=%s hiZEnabled=%s", name, portBitString, Boolean.toString(get()), hiZEnabled);
			}

			@Override
			public void loadPreference() {
				super.loadPreference();
				setHiZEnabled(getPrefs().getBoolean(key, false));
			}

			@Override
			public void storePreference() {
				super.storePreference();
				putPref(key, hiZEnabled); // will eventually call pref change listener which will call set again
			}
		}

		class Scanner extends Observable implements PreferenceChangeListener, HasPreference {

			Scanner() {
				loadPreference();
				getPrefs().addPreferenceChangeListener(this);
				hasPreferencesList.add(this);
			}
			int nstages = 64;
			private volatile int currentStage;
			private volatile boolean continuousScanningEnabled;
			private volatile int period;
			int minPeriod = 10; // to avoid FX2 getting swamped by interrupts for scanclk
			int maxPeriod = 255;

			public int getCurrentStage() {
				return currentStage;
			}

			public void setCurrentStage(int currentStage) {
				this.currentStage = currentStage;
				continuousScanningEnabled = false;
				setChanged();
				notifyObservers();
			}

			public boolean isContinuousScanningEnabled() {
				return continuousScanningEnabled;
			}

			public void setContinuousScanningEnabled(boolean continuousScanningEnabled) {
				this.continuousScanningEnabled = continuousScanningEnabled;
				setChanged();
				notifyObservers();
			}

			public int getPeriod() {
				return period;
			}

			public void setPeriod(int period) {
				if (period < minPeriod) {
					period = 10; // too small and interrupts swamp the FX2
				}
				if (period > maxPeriod) {
					period = (byte) (maxPeriod); // unsigned char
				}
				this.period = period;

				setChanged();
				notifyObservers();
			}

			@Override
			public void preferenceChange(PreferenceChangeEvent e) {
				if (e.getKey().equals("CochleaAMS1b.Biasgen.Scanner.currentStage")) {
					setCurrentStage(Integer.parseInt(e.getNewValue()));
				} else if (e.getKey().equals("CochleaAMS1b.Biasgen.Scanner.currentStage")) {
					setContinuousScanningEnabled(Boolean.parseBoolean(e.getNewValue()));
				}
			}

			@Override
			public void loadPreference() {
				setCurrentStage(getPrefs().getInt("CochleaAMS1b.Biasgen.Scanner.currentStage", 0));
				setContinuousScanningEnabled(getPrefs().getBoolean("CochleaAMS1b.Biasgen.Scanner.continuousScanningEnabled", false));
				setPeriod(getPrefs().getInt("CochleaAMS1b.Biasgen.Scanner.period", 50)); // 50 gives about 80kHz
			}

			@Override
			public void storePreference() {
				putPref("CochleaAMS1b.Biasgen.Scanner.period", period);
				putPref("CochleaAMS1b.Biasgen.Scanner.continuousScanningEnabled", continuousScanningEnabled);
				putPref("CochleaAMS1b.Biasgen.Scanner.currentStage", currentStage);
			}
		}

		class Equalizer extends Observable { // describes the local gain and Q registers and the kill bits

			final int numChannels = 128,  maxValue = 31;
			//            private int globalGain = 15;
			//            private int globalQuality = 15;
			EqualizerChannel[] channels = new EqualizerChannel[numChannels];

			Equalizer() {
				for (int i = 0; i < numChannels; i++) {
					channels[i] = new EqualizerChannel(i);
					channels[i].addObserver(Biasgen.this); // CochleaAMS1b.Biasgen observes each equalizer channel
				}
			}


			//            public int getGlobalGain() {
			//                return globalGain;
			//            }
			//
			//            public void setGlobalGain(int globalGain) {
			//                this.globalGain = globalGain;
			//                for(EqualizerChannel c:channels){
			//                    c.setQBPF(globalGain);
			//                }
			//            }
			//
			//            public int getGlobalQuality() {
			//                return globalQuality;
			//            }
			//
			//            public void setGlobalQuality(int globalQuality) {
			//                this.globalQuality = globalQuality;
			//                for(EqualizerChannel c:channels){
			//                    c.setQBPF(globalGain);
			//                }
			//            }
			class EqualizerChannel extends Observable implements ChangeListener, PreferenceChangeListener, HasPreference {

				final int max = 31;
				int channel;
				private String prefsKey;
				private volatile int qsos;
				private volatile int qbpf;
				private volatile boolean bpfkilled,  lpfkilled;

				EqualizerChannel(int n) {
					channel = n;
					prefsKey = "CochleaAMS1b.Biasgen.Equalizer.EqualizerChannel." + channel + ".";
					loadPreference();
					getPrefs().addPreferenceChangeListener(this);
					hasPreferencesList.add(this);
				}

				@Override
				public String toString() {
					return String.format("EqualizerChannel: channel=%-3d qbpf=%-2d qsos=%-2d bpfkilled=%-6s lpfkilled=%-6s", channel, qbpf, qsos, Boolean.toString(bpfkilled), Boolean.toString(lpfkilled));
				}

				public int getQSOS() {
					return qsos;
				}

				public void setQSOS(int qsos) {
					if (this.qsos != qsos) {
						setChanged();
					}
					this.qsos = qsos;
					notifyObservers();
				}

				public int getQBPF() {
					return qbpf;
				}

				public void setQBPF(int qbpf) {
					if (this.qbpf != qbpf) {
						setChanged();
					}
					this.qbpf = qbpf;
					notifyObservers();
				}

				public boolean isLpfKilled() {
					return lpfkilled;
				}

				public void setLpfKilled(boolean killed) {
					if (killed != lpfkilled) {
						setChanged();
					}
					lpfkilled = killed;
					notifyObservers();
				}

				public boolean isBpfkilled() {
					return bpfkilled;
				}

				public void setBpfKilled(boolean bpfkilled) {
					if (bpfkilled != this.bpfkilled) {
						setChanged();
					}
					this.bpfkilled = bpfkilled;
					notifyObservers();
				}

				@Override
				public void stateChanged(ChangeEvent e) {
					if (e.getSource() instanceof CochleaAMS1bControlPanel.EqualizerSlider) {
						CochleaAMS1bControlPanel.EqualizerSlider s = (CochleaAMS1bControlPanel.EqualizerSlider) e.getSource();
						if (s instanceof CochleaAMS1bControlPanel.QSOSSlider) {
							s.channel.setQSOS(s.getValue());
						}
						if (s instanceof CochleaAMS1bControlPanel.QBPFSlider) {
							s.channel.setQBPF(s.getValue());
						}
						setChanged();
						notifyObservers();
					} else if (e.getSource() instanceof CochleaAMS1bControlPanel.KillBox) {
						CochleaAMS1bControlPanel.KillBox b = (CochleaAMS1bControlPanel.KillBox) e.getSource();
						if (b instanceof CochleaAMS1bControlPanel.LPFKillBox) {
							b.channel.setLpfKilled(b.isSelected());
							//                            System.out.println("LPF: "+b.channel.toString());
						} else {
							b.channel.setBpfKilled(b.isSelected());
							//                            System.out.println("BPF: "+b.channel.toString());
						}
						setChanged();
						notifyObservers();
					}
				}

				@Override
				public void preferenceChange(PreferenceChangeEvent e) {
					if (e.getKey().equals(prefsKey + "qsos")) {
						setQSOS(Integer.parseInt(e.getNewValue()));
					} else if (e.getKey().equals(prefsKey + "qbpf")) {
						setQBPF(Integer.parseInt(e.getNewValue()));
					} else if (e.getKey().equals(prefsKey + "bpfkilled")) {
						setBpfKilled(Boolean.parseBoolean(e.getNewValue()));
					} else if (e.getKey().equals(prefsKey + "lpfkilled")) {
						setLpfKilled(Boolean.parseBoolean(e.getNewValue()));
					}
				}

				@Override
				public void loadPreference() {
					qsos = getPrefs().getInt(prefsKey + "qsos", 15);
					qbpf = getPrefs().getInt(prefsKey + "qbpf", 15);
					bpfkilled = getPrefs().getBoolean(prefsKey + "bpfkilled", false);
					lpfkilled = getPrefs().getBoolean(prefsKey + "lpfkilled", false);
					setChanged();
					notifyObservers();
				}

				@Override
				public void storePreference() {
					putPref(prefsKey + "bpfkilled", bpfkilled);
					putPref(prefsKey + "lpfkilled", lpfkilled);
					putPref(prefsKey + "qbpf", qbpf);
					putPref(prefsKey + "qsos", qsos);
				}
			}
		}
	}
}
