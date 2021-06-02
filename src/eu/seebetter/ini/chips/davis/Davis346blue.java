package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 346x260 pixel APS-DVS DAVIS USB 3.0 sensor (blue case)")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Davis346blue extends Davis346BaseCamera {
	public Davis346blue() {
		setName("Davis346blue");
		setDefaultPreferencesFile("biasgenSettings/Davis346b/DAVIS346blue.xml");

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		// Inverted with respect to other 346 cameras.
		setApsFirstPixelReadOut(new Point(getSizeX() - 1, 0));
		setApsLastPixelReadOut(new Point(0, getSizeY() - 1));
	}

	public Davis346blue(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
