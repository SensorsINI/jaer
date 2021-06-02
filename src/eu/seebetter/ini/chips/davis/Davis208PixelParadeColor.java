package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("Davis208PixelParade base class for 208x192 pixel sensitive APS-DVS DAVIS sensor with RGBW color filter")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis208PixelParadeColor extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 208;
	public static final short HEIGHT_PIXELS = 192;
	public static final ColorFilter[] COLOR_FILTER = { ColorFilter.B, ColorFilter.W, ColorFilter.R, ColorFilter.G };
	public static final float[][] COLOR_CORRECTION = { { 1, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 } };

	public Davis208PixelParadeColor() {
		setName("Davis208PixelParade");
		setDefaultPreferencesFile("biasgenSettings/Davis208PixelParade/Davis208PixelParade.xml");
		setSizeX(Davis208PixelParadeColor.WIDTH_PIXELS);
		setSizeY(Davis208PixelParadeColor.HEIGHT_PIXELS);

		setEventExtractor(new DavisColorEventExtractor(this, false, true, Davis208PixelParadeColor.COLOR_FILTER, false));

		setBiasgen(davisConfig = new Davis208PixelParadeConfig(this));

		davisRenderer = new DavisColorRenderer(this, false, Davis208PixelParadeColor.COLOR_FILTER, false,
			Davis208PixelParadeColor.COLOR_CORRECTION);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public Davis208PixelParadeColor(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
