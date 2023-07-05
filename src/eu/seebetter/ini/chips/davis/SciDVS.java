package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

<<<<<<< HEAD
@Description("SciDVS 126x112 pixel with APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SciDVS extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 112;
	public static final short HEIGHT_PIXELS = 126;
=======
@Description("SciDVS 254x112 pixel with APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SciDVS extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 126;
	public static final short HEIGHT_PIXELS = 112;
>>>>>>> 1f8fdb3aa (started scidvs)

	public SciDVS() {
		setName("SciDVS");
		setDefaultPreferencesFile("biasgenSettings/SciDVS/SciDVS_Test.xml");

		setSizeX(SciDVS.WIDTH_PIXELS);
		setSizeY(SciDVS.HEIGHT_PIXELS);

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));

		setBiasgen(davisConfig = new SciDVSConfig(this));
	}
	

	public SciDVS(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
