/*
 * CypressFX2Mapper.java
 *
 * Created on 12 de marzo de 2006, 14:57
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

/**
 * class for USB2AERmapper, extends functionality to download the FPGA code to the device
 *
 * @author raphael
 */
public class CypressFX2Mapper extends CypressFX2MonitorSequencer{
    
    
    /** Creates a new instance of CypressFX2Mapper */
    public CypressFX2Mapper(int devNumber) {
        super(devNumber);
    }
    
    /** 
     * gets operation mode from device
     * prints trigger mode the screen
     * @return timestamp tick in microseconds
     */
    public float getOperationMode() throws HardwareInterfaceException
    {
        float tick;
        
         if (!isOpen())
        {
            open();
        }
        
        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        int status;
        
        VendorRequest.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type=UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient=UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits=0;
        VendorRequest.Request= VR_OPERATION_MODE;
        VendorRequest.Index= 0;
        VendorRequest.Value= 0; 
        
	USBIO_DATA_BUFFER   dataBuffer=new USBIO_DATA_BUFFER(2);
        
	dataBuffer.setNumberOfBytesToTransfer(2);
        status=gUsbIo.classOrVendorInRequest(dataBuffer,VendorRequest);
    
        if(status!=USBIO_ERR_SUCCESS){
            throw new HardwareInterfaceException("Unable to get timestamp tick: " + UsbIo.errorText(status));
        }
        
        HardwareInterfaceException.clearException();
        
        if (dataBuffer.getBytesTransferred()==0)
        {
            log.warning("Could not get timestamp tick, zero bytes transferred");
            return 0;
        }
        
        if (dataBuffer.Buffer()[1]==0x00)
        {
            log.info("Trigger mode: Host (Master)");
            tick=1f;
        } else if (dataBuffer.Buffer()[1]==0x01)
        {
            log.info("Trigger mode: Host (Master)");
            tick=(float)0.125/6;
        } else if (dataBuffer.Buffer()[1]==0x02)
        {
            log.info("Trigger mode: Slave");
            tick=1f;
        } else if (dataBuffer.Buffer()[1]==0x03)
        {
            log.info("Trigger mode: Slave");
            tick=(float)0.125/6;
        } else
        { 
            log.warning("invalid tick: "+ dataBuffer.Buffer()[1]);
            tick=0;
        }
        
        return tick;
    }
    
    	/** set the timestamp tick on the device
	 @param mode  
               0: Trigger: Host, Tick 1us;
               1: Trigger: Host, Tick 20.83ns;
               2: Trigger: Slave, Tick 1us;
               3: Trigger: Slaver, Tick 20.83ns;
	 */
    public void setOperationMode(int mode) throws HardwareInterfaceException
    {
        if ((mode<0) || (mode>3))
        {
            StringBuilder s=new StringBuilder();
           s.append(String.format("Invalid mode. Valid modes: \n")); 
            s.append(String.format("    0: Trigger: Host (Master), Tick 1us"));
            s.append(String.format("    1: Trigger: Host (Master), Tick 20.83ns"));
            s.append(String.format("    2: Trigger: Slave, Tick 1us"));
            s.append(String.format("    3: Trigger: Slave, Tick 20.83ns"));
            log.warning(s.toString());
             return;
        }
        
        sendVendorRequest(VR_OPERATION_MODE,(short)mode,(short)0);
         
        log.info("Timestamp Tick is now " + getOperationMode() +"us");
    }
    
    /** 
     * downloads FPGA firmware to device and programs the FPGA
     * 
     * @param firmware path to firmware file
     *
     */
    public void downloadFPGAFirmware(String firmware)  {
        try{
            if (!isOpen())
            {
                open();
            }
            byte[] bytes;
            bytes=getBytesFromFile(firmware);
            
            int result; // result of USBIO operations
            USBIO_DATA_BUFFER dataBuffer=null;
            USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest=null;
            
            int numChunks, index;
            
            // make vendor request structure and populate it
            vendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
            
            
            vendorRequest.Request=VR_DOWNLOAD_FIRMWARE; // this is FPGA command, direction of vendor request defines download here
            
            vendorRequest.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
            vendorRequest.Type=UsbIoInterface.RequestTypeVendor;  // this is a vendor, not generic USB, request
            vendorRequest.Recipient=UsbIoInterface.RecipientDevice; // device (not endpoint, interface, etc) receives it
            vendorRequest.RequestTypeReservedBits=0;    // set these bits to zero for Cypress-specific 'vendor request' rather that user defined
            vendorRequest.Index=0;
            
              //send all but last chunk
        vendorRequest.Value = (short)0;			//address to write to (starting)
        dataBuffer=new USBIO_DATA_BUFFER(MAX_CONTROL_XFER_SIZE);
        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        index=0;
        numChunks=bytes.length/MAX_CONTROL_XFER_SIZE;  // this is number of full chunks to send
        for(int i=0;i<numChunks;i++){
            System.arraycopy(bytes, i*MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, MAX_CONTROL_XFER_SIZE);
            result=gUsbIo.classOrVendorOutRequest(dataBuffer,vendorRequest);
            if(result!=USBIO_ERR_SUCCESS){
                close();
                throw new HardwareInterfaceException("Error on downloading segment number "+i+" of FPGA firmware: "+UsbIo.errorText(result));
            }
          //  vendorRequest.Value += MAX_CONTROL_XFER_SIZE;			//change address of EEPROM write location
        }
        
        // now send final (short) chunk
        int numBytesLeft=bytes.length%MAX_CONTROL_XFER_SIZE;  // remainder
     //   if(numBytesLeft>0){
            vendorRequest.Value = (short)1;	
            dataBuffer=new USBIO_DATA_BUFFER(numBytesLeft);
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
            System.arraycopy(bytes, numChunks*MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, numBytesLeft);
            
            // send remaining part of firmware
            result=gUsbIo.classOrVendorOutRequest(dataBuffer,vendorRequest);
            if(result!=USBIO_ERR_SUCCESS){
                close();
                throw new HardwareInterfaceException("Error on downloading final segment of FPGA firmware: "+UsbIo.errorText(result));
            }
        
            System.out.println("FPGA firmware successfully sent");
        }catch(Exception e){
            e.printStackTrace();
        }
    }
 
    /** 
     * @param firmware path to file that has to be read
     * @return byte array with the content of the file supplied as argument
     */
     private static byte[] getBytesFromFile(String firmware) throws IOException {
        File file= new File(firmware);
        InputStream is = new FileInputStream(file);
    
        // Get the size of the file
        long length = file.length();
    
        // You cannot create an array using a long type.
        // It needs to be an int type.
        // Before converting to an int type, check
        // to ensure that file is not larger than Integer.MAX_VALUE.
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
    
        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];
    
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }
    
        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }
    
        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

}
