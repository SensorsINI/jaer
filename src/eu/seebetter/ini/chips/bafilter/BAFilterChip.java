package eu.seebetter.ini.chips.bafilter;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * BA Filter chip class, to display filtered output
 */
@Description("Background Activity filter chip (AERCorrFilter)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BAFilterChip extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 256;
	public static final short HEIGHT_PIXELS = 256;

	/**
	 * Creates a new instance.
	 */
	public BAFilterChip() {
		setName("BAFilterChip");
		setDefaultPreferencesFile(null);
		setSizeX(WIDTH_PIXELS);
		setSizeY(HEIGHT_PIXELS);

		setBiasgen(davisConfig = new BAFilterChipConfig(this));

		davisRenderer = new AEFrameChipRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(0, 0));
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
