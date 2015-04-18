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
import net.sf.jaer.DevelopmentStatus;

/**
 * 128x128 DAVIS camera on Tower wafer
 *
 * @author tobi
 */
@Description("DAVIS128 base class for 128x128 pixel APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis128 extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 128;
	public static final short HEIGHT_PIXELS = 128;
	protected DavisTowerBaseConfig davisConfig;

	/**
	 * Creates a new instance.
	 */
	public Davis128() {
		setName("DAVIS128");
		setDefaultPreferencesFile("biasgenSettings/Davis128/Davis128.xml");
		setSizeX(WIDTH_PIXELS);
		setSizeY(HEIGHT_PIXELS);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		apsDVSrenderer = new AEFrameChipRenderer(this); // must be called after configuration is constructed, because it
														// needs to know if frames are enabled to reset pixmap
		apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(apsDVSrenderer);
	}

	/**
	 * Creates a new instance of DAViS240
	 *
	 * @param hardwareInterface
	 *            an existing hardware interface. This constructor
	 *            is preferred. It makes a new cDVSTest10Biasgen object to talk to the
	 *            on-chip biasgen.
	 */
	public Davis128(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
