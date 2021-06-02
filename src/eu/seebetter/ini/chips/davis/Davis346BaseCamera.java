package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 base class for 346x260 pixel APS-DVS DAVIS sensor")
abstract public class Davis346BaseCamera extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 346;
	public static final short HEIGHT_PIXELS = 260;

	/**
	 * Creates a new instance.
	 */
	public Davis346BaseCamera() {
		setName("Davis346BaseCamera");
		setSizeX(Davis346BaseCamera.WIDTH_PIXELS);
		setSizeY(Davis346BaseCamera.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));
	}

	public Davis346BaseCamera(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
