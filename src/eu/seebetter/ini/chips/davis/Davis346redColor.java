package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 346x260 pixel APS-DVS DAVIS USB 3.0 color sensor (red case)")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Davis346redColor extends Davis346BaseCamera {

	public static final ColorFilter[] COLOR_FILTER = { ColorFilter.G, ColorFilter.B, ColorFilter.G, ColorFilter.R };
	public static final float[][] COLOR_CORRECTION = { { 1, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 } };

	public Davis346redColor() {
		setName("Davis346redColor");
		setDefaultPreferencesFile("biasgenSettings/Davis346b/DAVIS346red_color.xml");

		setEventExtractor(new DavisColorEventExtractor(this, false, true, Davis346redColor.COLOR_FILTER, false));

		davisRenderer = new DavisColorRenderer(this, false, Davis346redColor.COLOR_FILTER, false, Davis346redColor.COLOR_CORRECTION);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));
	}

	public Davis346redColor(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
