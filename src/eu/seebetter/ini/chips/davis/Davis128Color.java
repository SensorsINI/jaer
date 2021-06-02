package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("Davis128 base class for 128x128 pixel sensitive APS-DVS DAVIS sensor with RGBG color filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis128Color extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 128;
	public static final short HEIGHT_PIXELS = 128;
	public static final ColorFilter[] COLOR_FILTER = { ColorFilter.B, ColorFilter.G, ColorFilter.R, ColorFilter.G };
	public static final float[][] COLOR_CORRECTION = { { 1, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 } };

	public Davis128Color() {
		setName("Davis128");
		setDefaultPreferencesFile("biasgenSettings/Davis128/Davis128.xml");
		setSizeX(Davis128Color.WIDTH_PIXELS);
		setSizeY(Davis128Color.HEIGHT_PIXELS);

		setEventExtractor(new DavisColorEventExtractor(this, false, true, Davis128Color.COLOR_FILTER, false));

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		davisRenderer = new DavisColorRenderer(this, false, Davis128Color.COLOR_FILTER, false, Davis128Color.COLOR_CORRECTION);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public Davis128Color(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
