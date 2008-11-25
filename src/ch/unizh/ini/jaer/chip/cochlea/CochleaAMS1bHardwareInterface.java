/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import de.thesycon.usbio.UsbIoBuf;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import java.util.Observable;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;

/**
 * The hardware interface to CochleaAMS1b.
 * 
 * @author tobi
 */
public class CochleaAMS1bHardwareInterface extends CypressFX2MonitorSequencer implements BiasgenHardwareInterface {

    final byte VR_CONFIG = CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES;
    /** The USB PID */
    public static final short PID = (short) 0x8405;

    public CochleaAMS1bHardwareInterface(int n) {
        super(n);
    }

    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!(biasgen instanceof CochleaAMS1b.Biasgen)) {
            log.warning("biasgen is not instanceof CochleaAMS1b.Biasgen");
            return;
        }
        ((CochleaAMS1b.Biasgen) biasgen).sendConfiguration();
    }

    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        throw new HardwareInterfaceException("Flashing configuration not supported yet.");
    }

    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        if (!(biasgen instanceof CochleaAMS1b.Biasgen)) {
            log.warning(biasgen + " is not instanceof CochleaAMS1b.Biasgen, returning null array");
            return null;
        }
        CochleaAMS1b.Biasgen b = (CochleaAMS1b.Biasgen) biasgen;
        return new byte[0];
    }

    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new AEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class AEReader extends CypressFX2MonitorSequencer.MonSeqAEReader {

        public AEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        @Override
        protected void translateEvents(UsbIoBuf b) {
            translateEventsWithCPLDEventCode(b);
        }
    }
}
