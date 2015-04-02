/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.DavisChip;

/**
 * Base camera for Tower Davis346 cameras
 *
 * @author tobi
 */
@Description("DAVIS346 base class for 346x260 pixel APS-DVS DAVIS sensor")
abstract public class Davis346BaseCamera extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 346;
	public static final short HEIGHT_PIXELS = 260;
	protected DavisTowerBaseConfig davisConfig;

	/**
	 * Creates a new instance.
	 */
	public Davis346BaseCamera() {
		setName("DAVIS346BaseCamera");
		setSizeX(WIDTH_PIXELS);
		setSizeY(HEIGHT_PIXELS);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		apsDVSrenderer = new AEFrameChipRenderer(this); // must be called after configuration is constructed, because it
														// needs to know if frames are enabled to reset pixmap
		apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(apsDVSrenderer);
	}

	/**
	 * Creates a new instance
	 *
	 * @param hardwareInterface
	 *            an existing hardware interface. This constructor
	 *            is preferred. It makes a new cDVSTest10Biasgen object to talk to the
	 *            on-chip biasgen.
	 */
	public Davis346BaseCamera(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
