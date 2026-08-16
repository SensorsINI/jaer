package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Production iniVation DAVIS346 (mono) in the red case, typically USB 3.0 FX3.
 * Inherits {@link Davis346blue} capabilities including the user-friendly
 * threshold / bandwidth / event-frame-IMU panel. APS Y readout is flipped
 * relative to the blue prototype. Firmware orientation bits are applied in
 * {@code DAViSFX3HardwareInterface} from the camera logic, not this class.
 */
@Description("Inivation DAVIS346 346x260 hybrid vision sensor (HVS) with events+frames+IMU; pixel APS-DVS DAVIS USB 3.0 sensor (red case); 18.5um pitch")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Davis346red extends Davis346blue {
	public Davis346red() {
		setName("Davis346red");
		setDefaultPreferencesFileForFamily("Davis346");

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));
	}

	public Davis346red(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
