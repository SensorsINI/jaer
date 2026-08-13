package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * SciDVS APS-DVS. Live USB opens as {@code DAViSFX3HardwareInterface} (same PID
 * as Davis FX3); typed PacketBundle demux is shared with Davis
 * ({@code hardware/DAViSFX3/usbTypedDemux}). The GAER
 * {@code SciDVSHardwareInterface} is not factory-registered.
 */
@Description("SciDVS 126x112 pixel with APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SciDVS extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 112;
	public static final short HEIGHT_PIXELS = 126;

	public SciDVS() {
		setName("SciDVS");
		setDefaultPreferencesFile(null);

		setSizeX(SciDVS.WIDTH_PIXELS);
		setSizeY(SciDVS.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new SciDVSConfig(this));

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));

                setFullScaleForEventAccumulationRendering(256);
	}
	

	public SciDVS(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
