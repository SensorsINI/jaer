package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 346x260 pixel APS-DVS DAVIS USB 3.0 sensor (red case)")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Davis346red extends Davis346BaseCamera {
	public Davis346red() {
		setName("Davis346red");
		setDefaultPreferencesFile("biasgenSettings/Davis346b/DAVIS346red.xml");

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));
	}

	public Davis346red(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
