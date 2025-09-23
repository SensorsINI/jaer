package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS APS-DVS camera with 640x480 pixels")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis640 extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 640;
	public static final short HEIGHT_PIXELS = 480;

	public Davis640() {
		setName("Davis640");
		setDefaultPreferencesFile("biasgenSettings/Davis640/DAVIS640_TestExp.xml");
		setSizeX(Davis640.WIDTH_PIXELS);
		setSizeY(Davis640.HEIGHT_PIXELS);

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(0, 0));
	}

	public Davis640(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
