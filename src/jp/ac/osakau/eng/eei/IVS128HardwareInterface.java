/*
 * CypressFX2Biasgen.java
 *
 * Created on December 1, 2005, 2:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package jp.ac.osakau.eng.eei;

import net.sf.jaer.hardwareinterface.usb.cypressfx2.*;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HasUpdatableFirmware;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;

/**
 * The hardware interface for the IVS128. The VID is 0x04b4 and the PID is 0x1004.
 *
 * @author tobi/hiro
 */
public class IVS128HardwareInterface extends CypressFX2 {
    
    public final static String FIRMWARE_FILENAME_DVS128_XSVF="/net/sf/jaer/hardwareinterface/usb/cypressfx2/dvs128CPLD.xsvf";
    private static Preferences prefs=Preferences.userNodeForPackage(IVS128HardwareInterface.class);

    /** Creates a new instance of CypressFX2Biasgen */
    protected IVS128HardwareInterface(int devNumber) {
        super(devNumber);
    }

    /** Overrides open() to also set sync event mode. */
    @Override
    public void open() throws HardwareInterfaceException {
        super.open();
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
    private int frameCounter=0;

    @Override
    synchronized public void resetTimestamps() {
        frameCounter=0;

    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class RetinaAEReader extends CypressFX2.AEReader{
    private int US_PER_FRAME=5000;

        /** Constructs a new reader for the interface.
         *
         * @param cypress The CypressFX2 interface.
         * @throws HardwareInterfaceException on any hardware error.
         */
        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException{
            super(cypress);
        }
        
        /** Does the translation, timestamp unwrapping and reset. Prints a message when a SYNC event is detected.
         * @param b the raw buffer
         */
        @Override
        protected void translateEvents(UsbIoBuf b){
            
            synchronized(aePacketRawPool){
                AEPacketRaw buffer=aePacketRawPool.writeBuffer();
                byte[] aeBuffer=b.BufferMem;
                int bytesSent=b.BytesTransferred;
                if(bytesSent!=512){
                    log.warning("warning: "+bytesSent+" bytes sent, which is not 512 bytes which should have been sent");
                    return;
                }
                
                int[] addresses=buffer.getAddresses();
                int[] timestamps=buffer.getTimestamps();
                
                // write the start of the packet
                buffer.lastCaptureIndex=eventCounter;
                //  each pixel sent uses 4 bits to mark transient on, transient off, sustained on, sustained off as follows
                // 
                for(int i=0;i<bytesSent;i++){
                    
                    if ((eventCounter>aeBufferSize-1) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events
                        buffer.overrunOccuredFlag=true;
                    } else {
                        timestamps[eventCounter]=(int)(US_PER_FRAME*frameCounter); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        addresses[eventCounter]=(int)(aeBuffer[i]&0xFF);
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    }
                } // end for
                
                // write capture size
                buffer.lastCaptureLength=eventCounter-buffer.lastCaptureIndex;
                frameCounter++;
                // if (NumberOfWrapEvents!=0) {
                //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                //}
                //System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool
           
        }
    }
}
