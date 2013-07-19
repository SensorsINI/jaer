package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import javax.swing.JOptionPane;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

/** Adds biasgen functionality to base interface via Cypress FX2.
 *@author tobi
 */
public class CypressFX2Biasgen extends CypressFX2 implements BiasgenHardwareInterface {
    
    /** max number of bytes used for each bias. For 24-bit biasgen, only 3 bytes are used, but we oversize considerably for the future. */
    public static final int MAX_BYTES_PER_BIAS=8;
    
    /** Creates a new instance of CypressFX2Biasgen. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     *@param devNumber the desired device number, in range returned by CypressFX2Factory.getNumInterfacesAvailable
     *@see CypressFX2TmpdiffRetinaFactory
     */
    protected CypressFX2Biasgen(int devNumber) {
        super(devNumber);
    }
    
    /*
        sets the powerdown input pin to the biasgenerator.
        Chip may have been plugged in without being
         powered up. To ensure the biasgen is powered up, a negative transition is necessary. This transistion is necessary to ensure the startup circuit starts up the masterbias again.
     
        if this method is called from a GUI is may be desireable to actually toggle the powerdown pin high and then low to ensure the chip is powered up.
        otherwise it doesn't make sense to always toggle this pin because it will perturb the chip operation significantly.
        For instance, it should not be called very time new bias values are sent.
     
      @param powerDown true to power OFF the biasgen, false to power on
     */
    synchronized public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        //        System.out.println("BiasgenUSBInterface.setPowerDown("+powerDown+")");
        //        if(!powerDown)
        //            setPowerDownSingle(true);
        setPowerDownSingle(powerDown);
    }
    
    synchronized private void setPowerDownSingle(final boolean powerDown) throws HardwareInterfaceException {
        
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
            throw new HardwareInterfaceException("setPowerDown: unable to send: "+gUsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();
        
    }
    
    /** sends the ipot values.
     @param biasgen the biasgen which has the values to send
     */
    synchronized public void sendConfiguration(net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
//        log.info("sending biases for "+biasgen);
        if(gUsbIo==null) {
//            log.warning("gUusbIo=null, trying to open device");
            try{
                open();
            }catch(HardwareInterfaceException e){
                log.warning(e.getMessage());
                return; // may not have been constructed yet.
            }
        }
        if(biasgen.getPotArray()==null) {
            log.warning("BiasgenUSBInterface.send(): potArray=null");
            return; // may not have been constructed yet.
        }
        
        byte[] toSend=formatConfigurationBytes(biasgen);
        sendBiasBytes(toSend);
        HardwareInterfaceException.clearException();
        
        
    }
    
    /** Sends bytes with vendor request that signals these are bias (or other configuration) values. 
     * These are sent as control transfers which have a maximum data packet size of 64 bytes.
     If there are more than 64 bytes worth of bias data, 
     * then the transfer must be (and is automatically)  
     * split up into several control transfers and the
     bias values can only be latched on-chip when all of the bytes have been sent.
     *@param b bias bytes to clock out SPI interface
     * @see CypressFX2#VENDOR_REQUEST_SEND_BIAS_BYTES
     */
    synchronized public void sendBiasBytes(byte[] b) throws HardwareInterfaceException {
//        final int XFER_SIZE=64;
        if(gUsbIo==null){
            log.warning("null gUsbIo, device must be opened before sending this vendor request");
            return;
        }
        if(b==null){
            log.warning("null byte array passed in, ignoring");
            return;
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        if(b==null || b.length==0) {
            log.warning("null or empty bias byte array supplied");
//            throw new RuntimeException("null or empty bias byte array supplied");
        }
//        if(b.length>XFER_SIZE){
//            log.info("more than 64 bytes of bias values to send, splitting up into several control transfers");
//        }
        int numXfers=1;
        int numLeft=b.length;
        int index=0;
        for(int i=0;i<numXfers;i++){
            int xferLength=numLeft;
            //        System.out.println("sending bias bytes");
            USBIO_DATA_BUFFER dataBuffer=new USBIO_DATA_BUFFER(xferLength);
            vendorRequest.Request=VENDOR_REQUEST_SEND_BIAS_BYTES;
            vendorRequest.Type=UsbIoInterface.RequestTypeVendor;
            vendorRequest.Recipient=UsbIoInterface.RecipientDevice;
            vendorRequest.RequestTypeReservedBits=0;
            vendorRequest.Index=0;  // meaningless for this request
            vendorRequest.Value=0;  // meaningless for this request
            System.arraycopy(b, index, dataBuffer.Buffer(), 0, xferLength);
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
            result=gUsbIo.classOrVendorOutRequest(dataBuffer,vendorRequest);
            if(result!= de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS ){
                throw new HardwareInterfaceException("sendBiasBytes: Unable to send to device "+this+": "+gUsbIo.errorText(result));
            }
            numLeft-=xferLength;
            index+=xferLength;
        }
    }
    
    synchronized public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        JOptionPane.showMessageDialog(null,"Flashing biases not yet supported on CypressFX2");
    }

    /** This implementation delegates the job of getting the bytes to send to the Biasgen object.
     * Depending on the hardware interface, however, it may be that a particular subclass of this 
     * should override formatConfigurationBytes to return a different set of data.
     * @param biasgen the source of configuration information.
     * @return the bytes to send
     */
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        byte[] b=biasgen.formatConfigurationBytes(biasgen);
        return b;
    }
    
}
