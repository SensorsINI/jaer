package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

@Description("DAVIS346 346x260 pixel APS-DVS DAVIS sensor (BSI)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis346cBSI extends Davis346BaseCamera {
	public Davis346cBSI() {
		setName("Davis346cBSI");
		setDefaultPreferencesFile("biasgenSettings/Davis346cBSI/DAVIS346cBSI_Test.xml");

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(getSizeX() - 1, 0));
		setApsLastPixelReadOut(new Point(0, getSizeY() - 1));
	}

	public Davis346cBSI(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
