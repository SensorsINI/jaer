package eu.seebetter.ini.chips.davis;

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
	}

	public SciDVSFX10(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
}
