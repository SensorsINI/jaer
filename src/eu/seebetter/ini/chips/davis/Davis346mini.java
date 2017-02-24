package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 346x260 pixel APS-DVS DAVIS sensor (miniaturized version)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis346mini extends Davis346BaseCamera {
	public Davis346mini() {
		setName("Davis346mini");
		setDefaultPreferencesFile("biasgenSettings/Davis346b/DAVIS346mini.xml");

		davisRenderer = new AEFrameChipRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		// Inverted with respect to other 346 cameras.
		setApsFirstPixelReadOut(new Point(getSizeX() - 1, 0));
		setApsLastPixelReadOut(new Point(0, getSizeY() - 1));
	}

	public Davis346mini(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
