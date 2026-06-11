package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * SciDVS sensor on the Infineon EZ-USB FX10 controller board (VID 0x152A, PID
 * 0x8420). Identical sensor and bias set to {@link SciDVS} (the SciDVSConfig
 * SystemLogic2 module/parameter numbering is reused verbatim; the FX10
 * firmware shadow table accepts it unchanged); only the USB transport and the
 * on-the-wire event format differ, which is handled by
 * {@link net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSFX10HardwareInterface}.
 */
@Description("SciDVS 126x112 DVS sensor on Infineon FX10 USB controller")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SciDVSFX10 extends SciDVS {

	public SciDVSFX10() {
		super();
		setName("SciDVSFX10");

		// The FX10 firmware streams APS frames row-major with the 112-valued
		// address (jAER x) in the outer loop and the 126-valued address
		// (jAER y) in the inner loop, so the first sample of each frame is
		// jAER (0,0) and the last is (sizeX-1, sizeY-1) = (111,125). The
		// DavisEventExtractor synthesizes the SOF/EOF frame markers from
		// these two addresses (firstFrameAddress/lastFrameAddress), which is
		// what makes the DavisRenderer display complete frames.
		setApsFirstPixelReadOut(new Point(0, 0));
		setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
	}

	public SciDVSFX10(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
