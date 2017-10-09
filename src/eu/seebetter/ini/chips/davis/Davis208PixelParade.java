package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("Davis208PixelParade base class for 208x192 pixel sensitive APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis208PixelParade extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 208;
	public static final short HEIGHT_PIXELS = 192;

	public Davis208PixelParade() {
		setName("Davis208PixelParade");
		setDefaultPreferencesFile("biasgenSettings/Davis208PixelParade/Davis208PixelParade.xml");
		setSizeX(Davis208PixelParade.WIDTH_PIXELS);
		setSizeY(Davis208PixelParade.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new Davis208PixelParadeConfig(this));

		davisRenderer = new AEFrameChipRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public Davis208PixelParade(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
