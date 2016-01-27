/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * <p>
 * DAViS240a/b have 240x180 pixels and are built in 180nm technology. DAViS240a has a rolling shutter APS readout and
 * DAViS240b has global shutter readout (but rolling shutter also possible with DAViS240b with different CPLD logic).
 * Both do APS CDS in digital domain off-chip, on host side, using difference between reset and signal reads.
 * <p>
 *
 * Describes retina and its event extractor and bias generator. Two constructors ara available, the vanilla constructor
 * is used for event playback and the one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object. The
 * constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi, christian
 */
@Description("DAVIS240 base class for 240x180 pixel APS-DVS DAVIS sensor")
abstract public class DAVIS240BaseCamera extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 240;
	public static final short HEIGHT_PIXELS = 180;

	/**
	 * Creates a new instance.
	 */
	public DAVIS240BaseCamera() {
		setName("DAVIS240BaseCamera");
		setSizeX(DAVIS240BaseCamera.WIDTH_PIXELS);
		setSizeY(DAVIS240BaseCamera.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new Davis240Config(this));

		davisRenderer = new AEFrameChipRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);
	}

	/**
	 * Creates a new instance of DAViS240
	 *
	 * @param hardwareInterface
	 *            an existing hardware interface. This constructor
	 *            is preferred. It makes a new cDVSTest10Biasgen object to talk to the
	 *            on-chip biasgen.
	 */
	public DAVIS240BaseCamera(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}

	@Override
	public int translateJaer3AddressToJaerAddress(int address) {
		return ((0x1de0000 - (address & 0x7fe0000)) >> 5) + ((address & 0x7fc) << 20) + ((address & 2) >> 1);
	}
}
