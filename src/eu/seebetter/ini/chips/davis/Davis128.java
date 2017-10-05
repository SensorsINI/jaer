package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("Davis128 base class for 128x128 pixel sensitive APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis128 extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 128;
	public static final short HEIGHT_PIXELS = 128;

	public Davis128() {
		setName("Davis128");
		setDefaultPreferencesFile("biasgenSettings/Davis128/Davis128.xml");
		setSizeX(Davis128.WIDTH_PIXELS);
		setSizeY(Davis128.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new DavisTowerBaseConfig(this));

		davisRenderer = new AEFrameChipRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public Davis128(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
