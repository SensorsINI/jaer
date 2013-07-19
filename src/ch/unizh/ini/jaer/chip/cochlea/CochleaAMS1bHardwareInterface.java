/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

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

    /** Sends the vendor request to power down the Masterbias from the Masterbias GUI panel. This powerdown bit is the
     * same as ConfigBit D5 in the CochleaAMS1b.Biasgen but is handled here differently to be backward compatible.
     *
     * @param powerDown true to power down the master bias.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
         if(gUsbIo==null){
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        //        System.out.println("sending bias bytes");
        USBIO_DATA_BUFFER dataBuffer=new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
        vendorRequest.Request=VENDOR_REQUEST_POWERDOWN;
        vendorRequest.Type=UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient=UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits=0;
        vendorRequest.Index=0;  // meaningless for this request

        vendorRequest.Value=(short)(powerDown?1:0);  // this is the request bit, if powerDown true, send value 1, false send value 0

        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        result=gUsbIo.classOrVendorOutRequest(dataBuffer,vendorRequest);
        if(result!= de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS ){
            throw new HardwareInterfaceException("setPowerDown: unable to send: "+UsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();
       
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

        synchronized public void resetTimestamps() {
        try {
            sendVendorRequest(this.VENDOR_REQUEST_RESET_TIMESTAMPS);
           

        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
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
