/*
 * CypressFX2Biasgen.java
 *
 * Created on December 1, 2005, 2:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

/**
 * Adds functionality of tmpdiff retina to base classes for cypress fx2 interface.
 *
 * @author tobi
 */
public class CypressFX2TmpdiffRetina extends CypressFX2Biasgen {
    
    /** Creates a new instance of CypressFX2Biasgen */
    protected CypressFX2TmpdiffRetina(int devNumber) {
        super(devNumber);
    }
    
    
    public boolean isArrayReset(){
        return arrayResetEnabled;
    }
    
    /** set the pixel array reset
     * @param value true to reset the pixels, false to let them run normally
     */
    synchronized public void setArrayReset(boolean value) {
        arrayResetEnabled=value;
        // send vendor request for device to reset array
        int status=0; // don't use global status in this function
        if(gUsbIo==null){
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        
        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        
        VendorRequest.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type=UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient=UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits=0;
        VendorRequest.Request=VENDOR_REQUEST_SET_ARRAY_RESET;
        VendorRequest.Index=0;
        
        VendorRequest.Value=(short)(value?1:0);  // this is the request bit, if value true, send value 1, false send value 0
        
        USBIO_DATA_BUFFER dataBuffer=new USBIO_DATA_BUFFER(0); // no data, value is in request value
        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        
        status=gUsbIo.classOrVendorOutRequest(dataBuffer,VendorRequest);
        if(status!=USBIO_ERR_SUCCESS){
            System.err.println("CypressFX2.resetPixelArray: couldn't send vendor request to reset array");
        }
    }
       
    

}
