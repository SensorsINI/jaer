/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package ch.unizh.ini.jaer.chip.retina;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2TmpdiffRetinaHardwareInterface;

/**
 * Describes tmpdiff128 retina and its event extractor and bias generator.
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi
 */
public class Tmpdiff128 extends AETemporalConstastRetina implements Serializable {

	/** Creates a new instance of Tmpdiff128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
	public Tmpdiff128() {
		setName("Tmpdiff128");
		setSizeX(128);
		setSizeY(128);
		setNumCellTypes(2);
		setPixelHeightUm(40);
		setPixelWidthUm(40);
		setEventExtractor(new Extractor(this));
		setBiasgen(new Tmpdiff128.Biasgen(this));
		//        addDefaultEventFilter(Tmpdiff128RateController.class);
	}

	/** Creates a new instance of Tmpdiff128
	 * @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
	 */
	public Tmpdiff128(HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}

	/** the event extractor for Tmpdiff128. Tmpdiff128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
	 */
	public class Extractor extends RetinaExtractor implements java.io.Serializable {

		final short XMASK = 0xfe,  XSHIFT = 1,  YMASK = 0x7f00,  YSHIFT = 8;

		public Extractor(Tmpdiff128 chip) {
			super(chip);
			setXmask((short) 0x00fe);
			setXshift((byte) 1);
			setYmask((short) 0x7f00);
			setYshift((byte) 8);
			setTypemask((short) 1);
			setTypeshift((byte) 0);
			setFlipx(true);
			setFlipy(false);
			setFliptype(true);
		}

		/** extracts the meaning of the raw events.
		 *@param in the raw events, can be null
		 *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
		 */
		@Override
		synchronized public EventPacket extractPacket(AEPacketRaw in) {
			if (out == null) {
				out = new EventPacket<PolarityEvent>(getChip().getEventClass());
			} else {
				out.clear();
			}
			if (in == null) {
				return out;
			}
			int n = in.getNumEvents(); //addresses.length;

			int skipBy = 1;
			if (isSubSamplingEnabled()) {
				while ((n / skipBy) > getSubsampleThresholdEventCount()) {
					skipBy++;
				}
			}
			int sxm = sizeX - 1;
			int[] a = in.getAddresses();
			int[] timestamps = in.getTimestamps();
			OutputEventIterator outItr = out.outputIterator();
			for (int i = 0; i < n; i += skipBy) { // bug here
				PolarityEvent e = (PolarityEvent) outItr.nextOutput();
				int addr = a[i];
				e.address=addr;
				e.timestamp = (timestamps[i]);
				e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
				e.y = (short) ((addr & YMASK) >>> YSHIFT);
				e.type = (byte) ((1 - addr) & 1);
				e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
			}
			return out;
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
				setBiasgen(new Tmpdiff128.Biasgen(this));
			} else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		} catch (ClassCastException e) {
			System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}
	//    /** Called when this Tmpdiff128 notified, e.g. by having its AEViewer set
	//     @param the calling object
	//     @param arg the argument passed
	//     */
	//    public void update(Observable o, Object arg) {
	//        log.info("Tmpdiff128: received update from Observable="+o+", arg="+arg);
	//    }

	@Override
	public void setAeViewer(AEViewer v) {
		super.setAeViewer(v);
		if (v != null) {
			JMenuBar b = v.getJMenuBar();
			int n = b.getMenuCount();
			for (int i = 0; i < n; i++) {
				JMenu m = b.getMenu(i);
				if ((m != null) && (m.getText() != null) && m.getText().equals("Tmpdiff128")) {
					b.remove(m);
				}
			}
			JMenu m = new JMenu("Tmpdiff128");
			m.setToolTipText("Specialized menu for Tmpdiff128 chip");
			JMenuItem mi = new JMenuItem("Reset pixel array");
			mi.setToolTipText("Applies a momentary reset to the pixel array");
			mi.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent evt) {
					HardwareInterface hw = getHardwareInterface();
					if ((hw == null) || !((hw instanceof CypressFX2TmpdiffRetinaHardwareInterface)
						|| (hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2TmpdiffRetinaHardwareInterface))) {
						log.warning("cannot reset pixels with hardware interface=" + hw);
						return;
					}
					log.info("resetting pixels");
					if (hw instanceof CypressFX2TmpdiffRetinaHardwareInterface) {
						((CypressFX2TmpdiffRetinaHardwareInterface) hw).resetPixelArray();
					}
					if (hw instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2TmpdiffRetinaHardwareInterface) {
						((net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2TmpdiffRetinaHardwareInterface) hw).resetPixelArray();
					}
				}
			});
			m.add(mi);
			//       mi.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
			v.getJMenuBar().add(m);
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
	public class Biasgen extends net.sf.jaer.biasgen.Biasgen implements ChipControlPanel, DVSTweaks {

		private IPot diffOn,  diffOff,  refr,  pr,  sf,  diff;

		/** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
		 *@param chip the chip this biasgen belongs to
		 */
		public Biasgen(Chip chip) {
			super(chip);
			setName("Tmpdiff128");


			//  /** Creates a new instance of IPot
			//     *@param biasgen
			//     *@param name
			//     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
			//     *@param type (NORMAL, CASCODE)
			//     *@param sex Sex (N, P)
			//     * @param bitValue initial bitValue
			//     *@param displayPosition position in GUI from top (logical order)
			//     *@param tooltipString a String to display to user of GUI telling them what the pots does
			//     */
			////    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {

			// create potArray according to our needs
			setPotArray(new IPotArray(this));

			getPotArray().addPot(new IPot(this, "cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 0, 2, "Photoreceptor cascode")); // first to be loaded, at end of shift register
			getPotArray().addPot(new IPot(this, "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Differentiator switch level, higher to turn on more"));
			getPotArray().addPot(new IPot(this, "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N, 0, 12, "AER request pulldown"));
			getPotArray().addPot(new IPot(this, "puX", 8, IPot.Type.NORMAL, IPot.Sex.P, 0, 11, "2nd dimension AER static pullup"));
			getPotArray().addPot(diffOff = new IPot(this, "diffOff", 7, IPot.Type.NORMAL, IPot.Sex.N, 0, 6, "OFF threshold, lower to raise threshold"));
			getPotArray().addPot(new IPot(this, "req", 6, IPot.Type.NORMAL, IPot.Sex.N, 0, 8, "OFF request inverter bias"));
			getPotArray().addPot(refr = new IPot(this, "refr", 5, IPot.Type.NORMAL, IPot.Sex.P, 0, 9, "Refractory period"));
			getPotArray().addPot(new IPot(this, "puY", 4, IPot.Type.NORMAL, IPot.Sex.P, 0, 10, "1st dimension AER static pullup"));
			getPotArray().addPot(diffOn = new IPot(this, "diffOn", 3, IPot.Type.NORMAL, IPot.Sex.N, 0, 5, "ON threshold - higher to raise threshold"));
			getPotArray().addPot(diff = new IPot(this, "diff", 2, IPot.Type.NORMAL, IPot.Sex.N, 0, 4, "Differentiator"));
			getPotArray().addPot(sf = new IPot(this, "foll", 1, IPot.Type.NORMAL, IPot.Sex.P, 0, 3, "Src follower buffer between photoreceptor and differentiator"));
			getPotArray().addPot(pr = new IPot(this, "Pr", 0, IPot.Type.NORMAL, IPot.Sex.P, 0, 1, "Photoreceptor"));  // this is at start of shift register, load it last

			//            // test
			//            IPotGroup pixelGroup=new IPotGroup(Tmpdiff128.this,"Pixel");
			//            pixelGroup.add(getPotByName("cas"));
			//            pixelGroup.add(getPotByName("injGnd"));

			loadPreferences();

		}
		/** the change in current from an increase* or decrease* call */
		public final float RATIO = 1.05f;
		/** the minimum on/diff or diff/off current allowed by decreaseThreshold */
		public final float MIN_THRESHOLD_RATIO = 4f;
		public final float MAX_DIFF_ON_CURRENT = 6e-6f;
		public final float MIN_DIFF_OFF_CURRENT = 1e-9f;

		synchronized public void increaseThreshold() {
			if ((getDiffOn().getCurrent() * RATIO) > MAX_DIFF_ON_CURRENT) {
				return;
			}
			if ((getDiffOff().getCurrent() / RATIO) < MIN_DIFF_OFF_CURRENT) {
				return;
			}
			getDiffOn().changeByRatio(RATIO);
			getDiffOff().changeByRatio(1 / RATIO);
		}

		synchronized public void decreaseThreshold() {
			float diffI = getDiff().getCurrent();
			if ((getDiffOn().getCurrent() / MIN_THRESHOLD_RATIO) < diffI) {
				return;
			}
			if (getDiffOff().getCurrent() > (diffI / MIN_THRESHOLD_RATIO)) {
				return;
			}
			getDiffOff().changeByRatio(RATIO);
			getDiffOn().changeByRatio(1 / RATIO);
		}

		synchronized public void increaseRefractoryPeriod() {
			getRefr().changeByRatio(1 / RATIO);
		}

		synchronized public void decreaseRefractoryPeriod() {
			getRefr().changeByRatio(RATIO);
		}

		synchronized public void increaseBandwidth() {
			getPr().changeByRatio(RATIO);
			getSf().changeByRatio(RATIO);
		}

		synchronized public void decreaseBandwidth() {
			getPr().changeByRatio(1 / RATIO);
			getSf().changeByRatio(1 / RATIO);
		}

		synchronized public void moreONType() {
			getDiffOn().changeByRatio(1 / RATIO);
			getDiffOff().changeByRatio(RATIO);
		}

		synchronized public void moreOFFType() {
			getDiffOn().changeByRatio(RATIO);
			getDiffOff().changeByRatio(1 / RATIO);
		}

		@Override
		public void setBandwidthTweak(float val) {
			// TODO needs sensible method
		}

		private float getRatioFromSlider(float val) {
			float v = 1;
			val = val - 50;
			if (val == 0) {
				return v;
			}
			v = (float) Math.pow(RATIO, val);
			System.out.println("val=" + val + " v=" + v);
			return v;
		}

		@Override
		public void setThresholdTweak(float val) {
			float v = getRatioFromSlider(val);
			diffOn.setBitValue((int) (diffOn.getBitValue() * v));
			diffOff.setBitValue((int) (diffOff.getBitValue() / v));
		}

		@Override
		public void setMaxFiringRateTweak(float val) {
			// TODO needs sensible method here
		}

		@Override
		public void setOnOffBalanceTweak(float val) {
			throw new UnsupportedOperationException("Not supported yet.");
		}
		Tmpdiff128FunctionalBiasgenPanel biasUserControlPanel = null;

		/** @return a new panel for controlling this bias generator functionally
		 */
		@Override
		public JPanel buildControlPanel() {
			JPanel panel = new JPanel();
			panel.setLayout(new BorderLayout());
			JTabbedPane pane = new JTabbedPane();

			pane.addTab("Biases", super.buildControlPanel());
			pane.addTab("User friendly controls", new Tmpdiff128FunctionalBiasgenPanel(Tmpdiff128.this));
			panel.add(pane, BorderLayout.CENTER);
			return panel;
		}

		public IPot getDiffOn() {
			return diffOn;
		}

		public IPot getDiffOff() {
			return diffOff;
		}

		public IPot getRefr() {
			return refr;
		}

		public IPot getPr() {
			return pr;
		}

		public IPot getDiff() {
			return diff;
		}

		public IPot getSf() {
			return sf;
		}

		@Override
		public float getBandwidthTweak() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public float getMaxFiringRateTweak() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public float getThresholdTweak() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public float getOnOffBalanceTweak() {
			throw new UnsupportedOperationException("Not supported yet.");
		}
	} // Tmpdiff128Biasgen
}
