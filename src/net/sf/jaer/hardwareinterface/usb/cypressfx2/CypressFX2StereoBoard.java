/*
 * CypressFX2Biasgen.java
 *
 * Created on December 1, 2005, 2:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;


/**
 * The hardware interface for the DVS128 (second Tmpdiff128 board, with CPLD) retina boards.
 *
 * @author tobi/rapha
 */
public class CypressFX2StereoBoard extends CypressFX2Biasgen {
    
    /** Creates a new instance of CypressFX2Biasgen */
    protected CypressFX2StereoBoard(int devNumber) {
        super(devNumber);
    }
    
    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    synchronized public void resetTimestamps() {
        log.info(this + ".resetTimestamps(): zeroing timestamps");

        try {
            this.sendVendorRequest(this.VENDOR_REQUEST_RESET_TIMESTAMPS);
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }

    }
    
    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class RetinaAEReader extends CypressFX2.AEReader{
        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException{
            super(cypress);
        }
        
        /** Does the translation, timestamp unwrapping and reset 
         * @param b the raw buffer
         */
        @Override
        protected void translateEvents(UsbIoBuf b){
            
//            System.out.println("buf has "+b.BytesTransferred+" bytes");
            synchronized(aePacketRawPool){
                AEPacketRaw buffer=aePacketRawPool.writeBuffer();
            //    if(buffer.overrunOccuredFlag) return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
                int shortts;
                int NumberOfWrapEvents;
                NumberOfWrapEvents=0;
                
                byte[] aeBuffer=b.BufferMem;
                //            byte lsb,msb;
                int bytesSent=b.BytesTransferred;
                if(bytesSent%4!=0){
//                System.out.println("CypressFX2.AEReader.translateEvents(): warning: "+bytesSent+" bytes sent, which is not multiple of 4");
                    bytesSent=(bytesSent/4)*4; // truncate off any extra part-event
                }
                
                int[] addresses=buffer.getAddresses();
                int[] timestamps=buffer.getTimestamps();
                
                // write the start of the packet
                buffer.lastCaptureIndex=eventCounter;
                
                for(int i=0;i<bytesSent;i+=4){
//                        if(eventCounter>aeBufferSize-1){
//                            buffer.overrunOccuredFlag=true;
//    //                                        log.warning("overrun");
//                            return; // return, output event buffer is full and we cannot add any more events to it.
//                            //no more events will be translated until the existing events have been consumed by acquireAvailableEventsFromDriver
//                        }
                    
                    if((aeBuffer[i+3]&0x80)==0x80){ // timestamp bit 16 is one -> wrap
                        // now we need to increment the wrapAdd
                      
                        wrapAdd+=0x4000L; //uses only 14 bit timestamps
                      
                        //System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                        NumberOfWrapEvents++;
                    } else if  ((aeBuffer[i+3]&0x40)==0x40  ) { // timestamp bit 15 is one -> wrapAdd reset
                        // this firmware version uses reset events to reset timestamps
                        this.resetTimestamps();
                        // log.info("got reset event, timestamp " + (0xffff&((short)aeBuffer[i]&0xff | ((short)aeBuffer[i+1]&0xff)<<8)));
                    } else if ((eventCounter>aeBufferSize-1) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events
                        buffer.overrunOccuredFlag=true;
                    } else {
                        // address is LSB MSB
                        addresses[eventCounter]=(int)((aeBuffer[i]&0xFF) | ((aeBuffer[i+1]&0xFF)<<8));
                        
                        // same for timestamp, LSB MSB
                        shortts=(aeBuffer[i+2]&0xff | ((aeBuffer[i+3]&0xff)<<8)); // this is 15 bit value of timestamp in TICK_US tick
                        
                        timestamps[eventCounter]=(int)(TICK_US*(shortts+wrapAdd)); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    }
                } // end for
                
                // write capture size
                buffer.lastCaptureLength=eventCounter-buffer.lastCaptureIndex;
                     buffer.systemModificationTimeNs = System.nanoTime();
               
                // if (NumberOfWrapEvents!=0) {
                //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                //}
                //System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool
           
        }
    }

    
    /** set the pixel array reset
     * @param value true to reset the pixels, false to let them run normally
     */
    synchronized public void setArrayReset(boolean value) {
        arrayResetEnabled=value;
        // send vendor request for device to reset array
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
        
        int status=gUsbIo.classOrVendorOutRequest(dataBuffer,VendorRequest);
        if(status!=USBIO_ERR_SUCCESS){
            System.err.println("CypressFX2.resetPixelArray: couldn't send vendor request to reset array");
        }
    }

    public boolean isArrayReset(){
        return arrayResetEnabled;
    }
    

    /** Updates the firmware by downloading to the board's EEPROM */
   /* public void updateFirmware() throws HardwareInterfaceException {
        
        this.writeCPLDfirmware(FIRMWARE_FILENAME_DVS128_XSVF);
        log.info("New firmware written to CPLD");
        byte[] fw;
        try {
            fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_DVS128_IIC);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            throw new HardwareInterfaceException("Could not load firmware file ");
        }
        this.writeEEPROM(0, fw);
        log.info("New firmware written to EEPROM");
    }*/

        

}
