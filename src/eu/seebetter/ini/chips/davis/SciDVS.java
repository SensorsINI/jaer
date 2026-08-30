package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.UsbDevice;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DAViSFX3HardwareInterface;

/**
 * SciDVS APS-DVS. Live USB opens as {@code DAViSFX3HardwareInterface} (same
 * VID/PID as Davis346 FX3; firmware cannot distinguish them). jAER does not
 * open the camera to read FPGA geometry; pick {@code SciDVS} from the AEChip
 * menu or the USB AEChip chooser. The GAER {@code SciDVSHardwareInterface} is
 * not factory-registered.
 */
@Description("SciDVS 126x112 pixel with APS-DVS DAVIS sensor (experimental; same USB VID/PID as Davis346)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@UsbDevices({
    @UsbDevice(vid = CypressFX3.VID, pid = DAViSFX3HardwareInterface.PID_FX3),
    @UsbDevice(vid = CypressFX3.VID, pid = DAViSFX3HardwareInterface.PID_FX2)
})
public class SciDVS extends DavisBaseCamera {

	public static final short WIDTH_PIXELS = 112;
	public static final short HEIGHT_PIXELS = 126;

	public SciDVS() {
		setName("SciDVS");
		setDefaultPreferencesFile("biasgenSettings/SciDVS/SciDVS.xml");

		setSizeX(SciDVS.WIDTH_PIXELS);
		setSizeY(SciDVS.HEIGHT_PIXELS);

		setBiasgen(davisConfig = new SciDVSConfig(this));

		davisRenderer = new DavisRenderer(this);
		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

		setApsFirstPixelReadOut(new Point(0, getSizeY() - 1));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, 0));

                setFullScaleForEventAccumulationRendering(256);
	}
	

	public SciDVS(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
