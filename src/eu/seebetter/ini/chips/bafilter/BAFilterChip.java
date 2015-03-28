/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.bafilter;

import eu.seebetter.ini.chips.davis.*;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.PropertyTooltipSupport;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.histogram.AbstractHistogram;
import net.sf.jaer.util.histogram.SimpleHistogram;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;

import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;

/**
 * 
 *
 * @author hongjie
 */
@Description("Background Activity Filter Chip")
abstract public class BAFilterChip extends DavisChip implements RemoteControlled, Observer {
	private JMenu chipMenu = null;
	private JMenuItem syncEnabledMenuItem = null;
	private boolean isTimestampMaster = true;

	private final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(DavisChip.ADC_READCYCLE_MASK);
	// speedup in loop
	// following define bit masks for various hardware data types.
	// The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
	// timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE
	// or ADC data.
	// AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
	/**
	 * bit masks/shifts for cDVS AE data
	 */
	private BAFilterChipDisplayMethod davisDisplayMethod = null;
	private final AEFrameChipRenderer apsDVSrenderer;
	private int frameExposureStartTimestampUs = 0; // timestamp of first sample from frame (first sample read after
	// reset released)

	private BAFilterChipConfig config;
	JFrame controlFrame = null;
	public static final short WIDTH = 240;
	public static final short HEIGHT = 180;
	int sx1 = getSizeX() - 1, sy1 = getSizeY() - 1;
	private final String CMD_EXPOSURE = "exposure";
	private final String CMD_EXPOSURE_CC = "exposureCC";
	private final String CMD_RS_SETTLE_CC = "resetSettleCC";

	/**
	 * Creates a new instance of cDVSTest20.
	 */
	public BAFilterChip() {
		setName("BAFilterChip");
		setDefaultPreferencesFile("biasgenSettings/BAFilterChip/BAFilterChipDefault.xml");
		setEventClass(ApsDvsEvent.class);
		setSizeX(BAFilterChip.WIDTH);
		setSizeY(BAFilterChip.HEIGHT);
		setNumCellTypes(3); // two are polarity and last is intensity
		setPixelHeightUm(18.5f);
		setPixelWidthUm(18.5f);

		setEventExtractor(new BAFilterChipExtractor(this));

		setBiasgen(config = new BAFilterChipConfig(this));

		// hardware interface is ApsDvsHardwareInterface
		apsDVSrenderer = new AEFrameChipRenderer(this);
		apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(apsDVSrenderer);

		davisDisplayMethod = new BAFilterChipDisplayMethod(this);
		getCanvas().addDisplayMethod(davisDisplayMethod);
		getCanvas().setDisplayMethod(davisDisplayMethod);

		if (getRemoteControl() != null) {
			getRemoteControl()
			.addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
			getRemoteControl().addCommandListener(this, CMD_EXPOSURE_CC,
				CMD_EXPOSURE_CC + " val - sets exposure. val in clock cycles");
			getRemoteControl().addCommandListener(this, CMD_RS_SETTLE_CC,
				CMD_RS_SETTLE_CC + " val - sets reset settling time. val in clock cycles");
		}
		addObserver(this); // we observe ourselves so that if hardware interface for example calls notifyListeners we
		// get informed
	}

	@Override
	public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
		Chip.log.info("processing RemoteControlCommand " + command + " with input=" + input);
		if (command == null) {
			return null;
		}
		final String[] tokens = input.split(" ");
		if (tokens.length < 2) {
			return input + ": unknown command - did you forget the argument?";
		}
		if ((tokens[1] == null) || (tokens[1].length() == 0)) {
			return input + ": argument too short - need a number";
		}
		float v = 0;
		try {
			v = Float.parseFloat(tokens[1]);
		}
		catch (final NumberFormatException e) {
			return input + ": bad argument? Caught " + e.toString();
		}
		final String c = command.getCmdName();
		if (c.equals(CMD_RS_SETTLE_CC)) {
			config.resSettle.set((int) v);
		}
		else {
			return input + ": unknown command";
		}
		return "successfully processed command " + input;
	}

	@Override
	public void setPowerDown(final boolean powerDown) {
		config.powerDown.set(powerDown);
		try {
			config.sendOnChipConfigChain();
		}
		catch (final HardwareInterfaceException ex) {
			Logger.getLogger(DAVIS240BaseCamera.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Creates a new instance of DAViS240
	 *
	 * @param hardwareInterface
	 *            an existing hardware interface. This constructor
	 *            is preferred. It makes a new cDVSTest10Biasgen object to talk to the
	 *            on-chip biasgen.
	 */
	public BAFilterChip(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}

	// int pixcnt=0; // TODO debug
	/**
	 * The event extractor. Each pixel has two polarities 0 and 1.
	 *
	 * <p>
	 * The bits in the raw data coming from the device are as follows.
	 * <p>
	 * Bit 0 is polarity, on=1, off=0<br>
	 * Bits 1-9 are x address (max value 320)<br>
	 * Bits 10-17 are y address (max value 240) <br>
	 * <p>
	 */
	public class BAFilterChipExtractor extends RetinaExtractor {
		/**
		 *
		 */
		private static final long serialVersionUID = 3890914720599660376L;
		private int autoshotEventsSinceLastShot = 0; // autoshot counter
		private int warningCount = 0;
		private static final int WARNING_COUNT_DIVIDER = 10000;

		public BAFilterChipExtractor(final BAFilterChip chip) {
			super(chip);
		}

		private static final int IMU_WARNING_INTERVAL = 1000;
		private int missedImuSampleCounter = 0;
		private int badImuDataCounter = 0;

		/**
		 * extracts the meaning of the raw events.
		 *
		 * @param in
		 *            the raw events, can be null
		 * @return out the processed events. these are partially processed
		 *         in-place. empty packet is returned if null is supplied as in.
		 */
		@Override
		synchronized public EventPacket extractPacket(final AEPacketRaw in) {
			if (!(chip instanceof DavisChip)) {
				return null;
			}
			if (out == null) {
				out = new ApsDvsEventPacket(chip.getEventClass());
			}
			else {
				out.clear();
			}
			out.setRawPacket(in);
			if (in == null) {
				return out;
			}
			final int n = in.getNumEvents(); // addresses.length;
			sx1 = chip.getSizeX() - 1;
			sy1 = chip.getSizeY() - 1;

			final int[] datas = in.getAddresses();
			final int[] timestamps = in.getTimestamps();
			final OutputEventIterator outItr = out.outputIterator();
			// NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

			// at this point the raw data from the USB IN packet has already been digested to extract timestamps,
			// including timestamp wrap events and timestamp resets.
			// The datas array holds the data, which consists of a mixture of AEs and ADC values.
			// Here we extract the datas and leave the timestamps alone.
			// TODO entire rendering / processing approach is not very efficient now
			// System.out.println("Extracting new packet "+out);
			for (int i = 0; i < n; i++) { // TODO implement skipBy/subsampling, but without missing the frame start/end
				// events and still delivering frames
				final int data = datas[i];
                                if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_DVS) {
					// DVS event
					final ApsDvsEvent e = nextApsDvsEvent(outItr);
					if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
						e.adcSample = -1; // TODO hack to mark as not an ADC sample
						e.special = true; // TODO special is set here when capturing frames which will mess us up if
						// this is an IMUSample used as a plain ApsDvsEvent
						e.address = data;
						e.timestamp = (timestamps[i]);
						e.setIsDVS(true);
					}
					else {
						e.adcSample = -1; // TODO hack to mark as not an ADC sample
						e.special = false;
						e.address = data;
						e.timestamp = (timestamps[i]);
						e.polarity = (data & DavisChip.POLMASK) == DavisChip.POLMASK ? ApsDvsEvent.Polarity.On
							: ApsDvsEvent.Polarity.Off;
						e.type = (byte) ((data & DavisChip.POLMASK) == DavisChip.POLMASK ? 1 : 0);
						e.x = (short) (sx1 - ((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
						e.y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);
						e.setIsDVS(true);
						// System.out.println(data);
						// autoshot triggering
						autoshotEventsSinceLastShot++; // number DVS events captured here
					}
				}
				else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_APS) {
					// APS event
					// We first calculate the positions, so we can put events such as StartOfFrame at their
					// right place, before the actual APS event denoting (0, 0) for example.
					final int timestamp = timestamps[i];

					final short x = (short) (((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
					final short y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);

					final boolean pixFirst = firstFrameAddress(x,y); // First event of frame (addresses get flipped)
					final boolean pixLast = lastFrameAddress(x,y); // Last event of frame (addresses get flipped)

					ApsDvsEvent.ReadoutType readoutType = ApsDvsEvent.ReadoutType.Null;
					switch ((data & DavisChip.ADC_READCYCLE_MASK) >> ADC_NUMBER_OF_TRAILING_ZEROS) {
						case 0:
							readoutType = ApsDvsEvent.ReadoutType.ResetRead;
							break;

						case 1:
							readoutType = ApsDvsEvent.ReadoutType.SignalRead;
							break;

						case 3:
							Chip.log.warning("Event with readout cycle null was sent out!");
							break;

						default:
							if ((warningCount < 10) || ((warningCount % BAFilterChipExtractor.WARNING_COUNT_DIVIDER) == 0)) {
								Chip.log
								.warning("Event with unknown readout cycle was sent out! You might be reading a file that had the deprecated C readout mode enabled.");
							}
							warningCount++;
							break;
					}

					final ApsDvsEvent e = nextApsDvsEvent(outItr);
					e.adcSample = data & DavisChip.ADC_DATA_MASK;
					e.readoutType = readoutType;
					e.special = false;
					e.timestamp = timestamp;
					e.address = data;
					e.x = x;
					e.y = y;
					e.type = (byte) (2);

					if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
						// if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we
						// are at last APS pixel, then write EOF event
						// insert a new "end of frame" event not present in original data
						createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);
					}
				}
			}

			if ((getAutoshotThresholdEvents() > 0) && (autoshotEventsSinceLastShot > getAutoshotThresholdEvents())) {
				takeSnapshot();
				autoshotEventsSinceLastShot = 0;
			}

			return out;
		} // extractPacket

		// TODO hack to reuse IMUSample events as ApsDvsEvents holding only APS or DVS data by using the special flags
		private ApsDvsEvent nextApsDvsEvent(final OutputEventIterator outItr) {
			final ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
			e.special = false;
			e.adcSample = -1;
			return e;
		}

		/**
		 * creates a special ApsDvsEvent in output packet just for flagging APS
		 * frame markers such as start of frame, reset, end of frame.
		 *
		 * @param outItr
		 * @param flag
		 * @param timestamp
		 * @return
		 */
		private ApsDvsEvent createApsFlagEvent(final OutputEventIterator outItr, final ApsDvsEvent.ReadoutType flag,
			final int timestamp) {
			final ApsDvsEvent a = nextApsDvsEvent(outItr);
			a.adcSample = 0; // set this effectively as ADC sample even though fake
			a.timestamp = timestamp;
			a.x = -1;
			a.y = -1;
			a.readoutType = flag;
			return a;
		}

		@Override
		public AEPacketRaw reconstructRawPacket(final EventPacket packet) {
			if (raw == null) {
				raw = new AEPacketRaw();
			}
			if (!(packet instanceof ApsDvsEventPacket)) {
				return null;
			}
			final ApsDvsEventPacket apsDVSpacket = (ApsDvsEventPacket) packet;
			raw.ensureCapacity(packet.getSize());
			raw.setNumEvents(0);
			final int[] a = raw.addresses;
			final int[] ts = raw.timestamps;
			apsDVSpacket.getSize();
			final Iterator evItr = apsDVSpacket.fullIterator();
			int k = 0;
			while (evItr.hasNext()) {
				final ApsDvsEvent e = (ApsDvsEvent) evItr.next();
				// not writing out these EOF events (which were synthesized on extraction) results in reconstructed
				// packets with giant time gaps, reason unknown
				if (e.isEndOfFrame()) {
					continue; // these EOF events were synthesized from data in first place
				}
				ts[k] = e.timestamp;
				a[k++] = reconstructRawAddressFromEvent(e);
			}
			raw.setNumEvents(k);
			return raw;
		}

		/**
		 * To handle filtered ApsDvsEvents, this method rewrites the fields of
		 * the raw address encoding x and y addresses to reflect the event's x
		 * and y fields.
		 *
		 * @param e
		 *            the ApsDvsEvent
		 * @return the raw address
		 */
		@Override
		public int reconstructRawAddressFromEvent(final TypedEvent e) {
			int address = e.address;
			// if(e.x==0 && e.y==0){
			// log.info("start of frame event "+e);
			// }
			// if(e.x==-1 && e.y==-1){
			// log.info("end of frame event "+e);
			// }
			// e.x came from e.x = (short) (chip.getSizeX()-1-((data & XMASK) >>> XSHIFT)); // for DVS event, no x flip
			// if APS event
			if (((ApsDvsEvent) e).adcSample >= 0) {
				address = (address & ~DavisChip.XMASK) | ((e.x) << DavisChip.XSHIFT);
			}
			else {
				address = (address & ~DavisChip.XMASK) | ((sx1 - e.x) << DavisChip.XSHIFT);
			}
			// e.y came from e.y = (short) ((data & YMASK) >>> YSHIFT);
			address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
			return address;
		}

 	} // extractor

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
				setBiasgen(new BAFilterChipConfig(this));
				// now we can addConfigValue the control panel
			}
			else {
				getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
			}
		}
		catch (final ClassCastException e) {
			Chip.log.warning(e.getMessage()
				+ ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
		}
	}

	/**
	 * Displays data from SeeBetter test chip SeeBetter10/11.
	 *
	 * @author Tobi
	 */
	public class BAFilterChipDisplayMethod extends ChipRendererDisplayMethodRGBA {
		private static final int FONTSIZE = 10;
		private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES = 10;

		private final TextRenderer exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN,
			BAFilterChipDisplayMethod.FONTSIZE), true, true);

		public BAFilterChipDisplayMethod(final BAFilterChip chip) {
			super(chip.getCanvas());
		}

		@Override
		public void display(final GLAutoDrawable drawable) {
			getCanvas().setBorderSpacePixels(50);

			super.display(drawable);

			if (isTimestampMaster == false) {
				exposureRenderer.begin3DRendering();
				exposureRenderer.draw3D("Slave camera", 0, -(BAFilterChipDisplayMethod.FONTSIZE / 2), 0, .5f);
				exposureRenderer.end3DRendering();
			}
		}

		TextRenderer imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
		GLU glu = null;
		GLUquadric accelCircle = null;
	}

	/**
	 * Returns the preferred DisplayMethod, or ChipRendererDisplayMethod if null
	 * preference.
	 *
	 * @return the method, or null.
	 * @see #setPreferredDisplayMethod
	 */
	@Override
	public DisplayMethod getPreferredDisplayMethod() {
		return new ChipRendererDisplayMethodRGBA(getCanvas());
	}

	@Override
	public void update(final Observable o, final Object arg) {
		// TODO Auto-generated method stub
	}

	private void updateTSMasterState() {
		// Check which logic we are and send the TS Master/Slave command if we are old logic.
		// TODO: this needs to be done.
	}

	/**
	 * Enables or disable DVS128 menu in AEViewer
	 *
	 * @param yes
	 *            true to enable it
	 */
	private void enableChipMenu(final boolean yes) {
		if (yes) {
			if (chipMenu == null) {
				chipMenu = new JMenu(this.getClass().getSimpleName());
				chipMenu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
				chipMenu.setToolTipText("Specialized menu for DAVIS chip");
			}

			if (syncEnabledMenuItem == null) {
				syncEnabledMenuItem = new JCheckBoxMenuItem("Timestamp master");
				syncEnabledMenuItem.setToolTipText("<html>Sets this device as timestamp master");

				syncEnabledMenuItem.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(final ActionEvent evt) {
						Chip.log.info("setting sync/timestamp master to " + syncEnabledMenuItem.isSelected());
						isTimestampMaster = syncEnabledMenuItem.isSelected();

						updateTSMasterState();
					}
				});

				syncEnabledMenuItem.setSelected(isTimestampMaster);
				updateTSMasterState();

				chipMenu.add(syncEnabledMenuItem);
			}

			if (getAeViewer() != null) {
				getAeViewer().setMenu(chipMenu);
			}

		}
		else { // disable menu
			if (chipMenu != null) {
				getAeViewer().removeMenu(chipMenu);
			}
		}
	}

	@Override
	public void onDeregistration() {
		super.onDeregistration();

		if (getAeViewer() == null) {
			return;
		}

		enableChipMenu(false);
	}

	@Override
	public void onRegistration() {
		super.onRegistration();

		if (getAeViewer() == null) {
			return;
		}

		enableChipMenu(true);
	}
        
    abstract protected boolean firstFrameAddress(short x, short y);

    abstract protected boolean lastFrameAddress(short x, short y);

}
