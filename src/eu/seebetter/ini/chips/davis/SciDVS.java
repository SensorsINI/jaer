package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DAViSFX3HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSHardwareInterface;

@Description("SciDVS 126x112 pixel with APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SciDVS extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 112;
	public static final short HEIGHT_PIXELS = 126;

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
                setFullScaleForEventAccumulationRendering(256);
	}
	

	public SciDVS(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}

	/**
	 * Overrides to swap DAViSFX3HardwareInterface for SciDVSHardwareInterface.
	 * SciDVS FX3 shares the same USB VID/PID and product string as DAVIS346,
	 * so the factory cannot distinguish them. When the user selects the SciDVS
	 * chip class, we ensure the correct hardware interface is used.
	 */
	@Override
	public void setHardwareInterface(final HardwareInterface hardwareInterface) {
		if (hardwareInterface instanceof DAViSFX3HardwareInterface) {
			try {
				DAViSFX3HardwareInterface davis = (DAViSFX3HardwareInterface) hardwareInterface;
				SciDVSHardwareInterface scidvs = new SciDVSHardwareInterface(davis.getDevice());
				java.util.logging.Logger.getLogger("net.sf.jaer").info(
						"SciDVS chip selected: replacing DAViSFX3HardwareInterface with SciDVSHardwareInterface");
				super.setHardwareInterface(scidvs);
				return;
			} catch (Exception e) {
				java.util.logging.Logger.getLogger("net.sf.jaer").warning(
						"Failed to create SciDVSHardwareInterface, falling back to DAVIS: " + e);
			}
		}
		super.setHardwareInterface(hardwareInterface);
	}
}
