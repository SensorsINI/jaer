package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Shared 346×260 APS-DVS+IMU base for the DAVIS346 family.
 * {@link DavisTowerBaseConfig} supplies the same user-friendly Hardware
 * Configuration panel (threshold, bandwidth, event/frame/IMU) used by
 * {@link Davis346blue}, {@link Davis346red}, and {@link Davis346redColor}.
 */
@Description("DAVIS346 base class for 346x260 pixel APS-DVS DAVIS sensor")
abstract public class Davis346BaseCamera extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 346;
	public static final short HEIGHT_PIXELS = 260;
	/** Host USB FIFO / buffer defaults for all Davis346 FX3 cameras. */
	public static final int DEFAULT_USB_FIFO_SIZE = 131072;
	public static final int DEFAULT_USB_NUM_BUFFERS = 4;
	public static final int DEFAULT_AE_BUFFER_SIZE = 1_200_000;

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
