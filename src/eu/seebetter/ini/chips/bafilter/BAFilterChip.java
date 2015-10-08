package eu.seebetter.ini.chips.bafilter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;

/**
 * BA Filter chip class, to display filtered output
 */
@Description("Background Activity filter chip (AERCorrFilter)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BAFilterChip extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 256;
	public static final short HEIGHT_PIXELS = 256;
	protected BAFilterChipConfig baConfig;

	/**
	 * Creates a new instance.
	 */
	public BAFilterChip() {
		setName("Davis208PixelParade");
		setDefaultPreferencesFile(null);
		setSizeX(WIDTH_PIXELS);
		setSizeY(HEIGHT_PIXELS);
		setBiasgen(baConfig = new BAFilterChipConfig(this));

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
	public BAFilterChip(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
