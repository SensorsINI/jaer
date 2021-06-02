package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS APS-DVS camera with 640x480 pixels and RGBG color filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis640Color extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 640;
	public static final short HEIGHT_PIXELS = 480;
	public static final ColorFilter[] COLOR_FILTER = { ColorFilter.G, ColorFilter.B, ColorFilter.G, ColorFilter.R };
	public static final float[][] COLOR_CORRECTION = { { 1, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 } };

	public Davis640Color() {
		setName("Davis640Color");
		setDefaultPreferencesFile("biasgenSettings/Davis640/DAVIS640Color_TestExp.xml");
		setSizeX(Davis640Color.WIDTH_PIXELS);
		setSizeY(Davis640Color.HEIGHT_PIXELS);

		setEventExtractor(new DavisColorEventExtractor(this, false, true, Davis640Color.COLOR_FILTER, false));

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		davisRenderer = new DavisColorRenderer(this, false, Davis640Color.COLOR_FILTER, false, Davis640Color.COLOR_CORRECTION);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(0, 0));
	}

	public Davis640Color(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
