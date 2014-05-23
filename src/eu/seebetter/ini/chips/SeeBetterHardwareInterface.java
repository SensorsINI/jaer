/*
 * CypressFX2Biasgen.java
 *
 * Created on 23 Jan 2008
 *
 */
package eu.seebetter.ini.chips;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

import javax.swing.ProgressMonitor;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2Biasgen;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

/**
 * Adds functionality of SeeBetter10/11 retina test chips to base classes for Cypress FX2 interface.
 *
 * @author tobi
 */
public class SeeBetterHardwareInterface extends CypressFX2Biasgen {

    /** The USB product ID of this device */
    static public final short PID = (short) 0x840B;

    /** Creates a new instance of CypressFX2Biasgen */
    public SeeBetterHardwareInterface(int devNumber) {
        super(devNumber);
    }



    /** Overridden to use PortBit powerDown in biasgen
     * 
     * @param powerDown true to power off masterbias
     * @throws HardwareInterfaceException 
     */
    @Override
    synchronized public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if(chip!=null && chip instanceof SeeBetter1011){
            SeeBetter1011.SeeBetterConfig sb=(SeeBetter1011.SeeBetterConfig)chip.getBiasgen();
            sb.powerDown.set(powerDown);
        }
    }
    
    private byte[] parseHexData(String firmwareFile) throws IOException {

        byte[] fwBuffer;
        // load firmware file (this is binary file of 8051 firmware)

        log.info("reading firmware file " + firmwareFile);
        FileReader reader;
        LineNumberReader lineReader;
        String line;
        int length;
        // load firmware file (this is a lattice c file)
        try {

            reader = new FileReader(firmwareFile);
            lineReader = new LineNumberReader(reader);

            line = lineReader.readLine();
            while (!line.startsWith("xdata")) {
                line = lineReader.readLine();
            }
            int scIndex = line.indexOf(";");
            int eqIndex = line.indexOf("=");
            int index = 0;
            length = Integer.parseInt(line.substring(eqIndex + 2, scIndex));
            // log.info("File length: " + length);
            String[] tokens;
            fwBuffer = new byte[length];
            Short value;
            while (!line.endsWith("};")) {
                line = lineReader.readLine();
                tokens = line.split("0x");
                //    System.out.println(line);
                for (int i = 1; i < tokens.length; i++) {
                    value = Short.valueOf(tokens[i].substring(0, 2), 16);
                    fwBuffer[index++] = value.byteValue();
                    //   System.out.println(fwBuffer[index-1]);
                }
            }
            // log.info("index" + index);

            lineReader.close();
        } catch (IOException e) {
            close();
            log.warning(e.getMessage());
            throw new IOException("can't load binary Cypress FX2 firmware file " + firmwareFile);
        }
        return fwBuffer;
    }

    @Override
    synchronized public void writeCPLDfirmware(String svfFile) throws HardwareInterfaceException {
        byte[] bytearray;
        int status, index;
        USBIO_DATA_BUFFER dataBuffer = null;

        try {
            bytearray = this.parseHexData(svfFile);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        ProgressMonitor progressMonitor = makeProgressMonitor("Writing CPLD configuration - do not unplug", 0, bytearray.length);


        int result;
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();


        int numChunks;

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;  // this is a vendor, not generic USB, request
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice; // device (not endpoint, interface, etc) receives it
        vendorRequest.RequestTypeReservedBits = 0;    // set these bits to zero for Cypress-specific 'vendor request' rather that user defined
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE; // this is download/upload firmware request. really it is just a 'fill RAM request'
        vendorRequest.Index = 0;

        //	2) send the firmware to Control Endpoint 0
        // when sending firmware, we need to break up the loaded fimware
        //		into MAX_CONTROL_XFER_SIZE blocks
        //
        // this means:
        //	a) the address to load it to needs to be changed (VendorRequest.Value)
        //	b) need a pointer that moves through FWbuffer (pBuffer)
        //	c) keep track of remaining bytes to transfer (FWsize_left);


        //send all but last chunk
        vendorRequest.Value = 0;			//address of firmware location
        dataBuffer = new USBIO_DATA_BUFFER(MAX_CONTROL_XFER_SIZE);
        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);

        numChunks = bytearray.length / MAX_CONTROL_XFER_SIZE;  // this is number of full chunks to send
        for (int i = 0; i < numChunks; i++) {
            System.arraycopy(bytearray, i * MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, MAX_CONTROL_XFER_SIZE);
            result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
            if (result != USBIO_ERR_SUCCESS) {
                close();
                throw new HardwareInterfaceException("Error on downloading segment number " + i + " of CPLD firmware: " + UsbIo.errorText(result));
            }
            progressMonitor.setProgress(vendorRequest.Value);
            progressMonitor.setNote(String.format("sent %d of %d bytes of CPLD configuration", vendorRequest.Value, bytearray.length));
            vendorRequest.Value += MAX_CONTROL_XFER_SIZE;			//change address of firmware location
            if (progressMonitor.isCanceled()) {
                progressMonitor = makeProgressMonitor("Writing CPLD configuration - do not unplug", 0, bytearray.length);
            }
        }

        // now send final (short) chunk
        int numBytesLeft = bytearray.length % MAX_CONTROL_XFER_SIZE;  // remainder
        if (numBytesLeft > 0) {
            dataBuffer = new USBIO_DATA_BUFFER(numBytesLeft);
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
            //    vendorRequest.Index = 1; // indicate that this is the last chuck, now program CPLD
            System.arraycopy(bytearray, numChunks * MAX_CONTROL_XFER_SIZE, dataBuffer.Buffer(), 0, numBytesLeft);

            // send remaining part of firmware
            result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
            if (result != USBIO_ERR_SUCCESS) {
                close();
                throw new HardwareInterfaceException("Error on downloading final segment of CPLD firmware: " + UsbIo.errorText(result));
            }
        }

        vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        dataBuffer = new USBIO_DATA_BUFFER(1);

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE;
        vendorRequest.Index = 1;
        vendorRequest.Value = 0;

        dataBuffer.setNumberOfBytesToTransfer(1);
        status = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            log.info(UsbIo.errorText(status));
            try {
                Thread.sleep(2000);
                this.open();
            } catch (Exception e) {
            }
        }

        vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        dataBuffer = new USBIO_DATA_BUFFER(10);

        vendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Request = VR_DOWNLOAD_FIRMWARE;
        vendorRequest.Index = 0;
        vendorRequest.Value = 0;

        dataBuffer.setNumberOfBytesToTransfer(10);
        status = gUsbIo.classOrVendorInRequest(dataBuffer, vendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Unable to receive error code: " + UsbIo.errorText(status));
        }

        HardwareInterfaceException.clearException();

        // log.info("bytes transferred" + dataBuffer.getBytesTransferred());
        if (dataBuffer.getBytesTransferred() == 0) {
            //this.sendVendorRequest(VR_DOWNLOAD_FIRMWARE, (short) 0, (short) 0);
            throw new HardwareInterfaceException("Unable to program CPLD, could not get xsvf Error code");
        }
        progressMonitor.close();

        if (dataBuffer.Buffer()[1] != 0) {
            //this.sendVendorRequest(VR_DOWNLOAD_FIRMWARE, (short) 0, (short) 0);
            int dataindex = (dataBuffer.Buffer()[6] << 24) | (dataBuffer.Buffer()[7] << 16) | (dataBuffer.Buffer()[8] << 8) | (dataBuffer.Buffer()[9]);
            int algoindex = (dataBuffer.Buffer()[2] << 24) | (dataBuffer.Buffer()[3] << 16) | (dataBuffer.Buffer()[4] << 8) | (dataBuffer.Buffer()[5]);
            throw new HardwareInterfaceException("Unable to program CPLD, error code: " + dataBuffer.Buffer()[1] + " algo index: " + algoindex + " data index " + dataindex);
            // System.out.println("Unable to program CPLD, unable to program CPLD, error code: " + dataBuffer.Buffer()[1] + ", at command: " + command + " index: " + index + " commandlength " + commandlength);
        }
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
        int fifoSize;
        if ((fifoSize=getAeReader().getFifoSize()) < 4096) {
            log.warning("AEReader FIFO size of "+fifoSize+" bytes is probably too small to handle SeeBetter APS output; frame start events will be lost.\nIncrease size in USB menu");
        }
         HardwareInterfaceException.clearException();
    }
    boolean gotY = false; // TODO  hack for debugging state machine

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class RetinaAEReader extends CypressFX2.AEReader {
        private static final int NONMONOTONIC_WARNING_COUNT = 30; // how many warnings to print after start or timestamp reset

        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }
        /** Method to translate the UsbIoBuffer for the DVS320 sensor which uses the 32 bit address space.
         *<p>
         * It has a CPLD to timestamp events and uses the CypressFX2 in slave
         * FIFO mode. 
         *<p>The DVS320 has a burst mode readout mechanism that 
         * outputs a row address,
         * then all the latched column addresses.
         *The columns are output left to right. A timestamp is only
         * meaningful at the row addresses level. Therefore
         *the board timestamps on row address, and then 
         * sends the data in the following sequence:
         * timestamp, row, col, col, col,....,timestamp,row,col,col...
         * <p>
         * Intensity information is transmitted by bit 8, which is set by the chip
         *The bit encoding of the data is as follows
         *<literal>
        Address bit	Address bit pattern
        0	LSB Y or Polarity ON=1
        1	Y1 or LSB X
        2	Y2 or X1
        3	Y3 or X2
        4	Y4 or X3
        5	Y5 or X4
        6	Y6 or X5
        7	Y7 (MSBY) or X6
        8	intensity or X7. This bit is set for a Y address if the intensity neuron has spiked. This bit is also X7 for X addreses.
        9	X8 (MSBX)
        10	Y=0, X=1
        </literal>
         *
         * The two msbs of the raw 16 bit data are used to tag the type of data, e.g. address, timestamp, or special events wrap or
         * reset host timestamps.
        <literal>
        Address             Name
        00xx xxxx xxxx xxxx	pixel address
        01xx xxxx xxxx xxxx	timestamp
        10xx xxxx xxxx xxxx	wrap
        11xx xxxx xxxx xxxx	timestamp reset
        </literal>
        
         *The msb of the 16 bit timestamp is used to signal a wrap (the actual timestamp is only 15 bits).
         * The wrapAdd is incremented when an empty event is received which has the timestamp bit 15
         * set to one.
         *<p>
         * Therefore for a valid event only 15 bits of the 16 transmitted timestamp bits are valid, bit 15
         * is the status bit. overflow happens every 32 ms.
         * This way, no roll overs go by undetected, and the problem of invalid wraps doesn't arise.
         *@param minusEventEffect the data buffer
         *@see #translateEvents
         */
        static private final byte Xmask = (byte) 0x01;
        static private final byte IntensityMask = (byte) 0x40;
        private int lasty = 0;
        private int currentts = 0;
        private int lastts = 0;
        private int nonmonotonicTimestampWarningCount=NONMONOTONIC_WARNING_COUNT;

        @Override
        protected void translateEvents(UsbIoBuf b) {
            try {
                // data from cDVS is stateful. 2 bytes sent for each word of data can consist of either timestamp, y address, x address, or ADC value.
                // The type of data is determined from bits in these two bytes.

//            if(tobiLogger.isEnabled()==false) tobiLogger.setEnabled(true); //debug
                synchronized (aePacketRawPool) {
                    AEPacketRaw buffer = aePacketRawPool.writeBuffer();

                    int NumberOfWrapEvents;
                    NumberOfWrapEvents = 0;

                    byte[] buf = b.BufferMem;
                    int bytesSent = b.BytesTransferred;
                    if (bytesSent % 2 != 0) {
                        System.err.println("warning: " + bytesSent + " bytes sent, which is not multiple of 2");
                        bytesSent = (bytesSent / 2) * 2; // truncate off any extra part-event
                    }

                    int[] addresses = buffer.getAddresses();
                    int[] timestamps = buffer.getTimestamps();
                    //log.info("received " + bytesSent + " bytes");
                    // write the start of the packet
                    buffer.lastCaptureIndex = eventCounter;
//                     tobiLogger.log("#packet");
                    for (int i = 0; i < bytesSent; i += 2) {
                        //   tobiLogger.log(String.format("%d %x %x",eventCounter,buf[i],buf[i+1])); // DEBUG
                        //   int val=(buf[i+1] << 8) + buf[i]; // 16 bit value of data
                        int dataword = (0xff & buf[i]) | (0xff00 & (buf[i + 1] << 8));  // data sent little endian

                        final int code = (buf[i + 1] & 0xC0) >> 6; // gets two bits at XX00 0000 0000 0000. (val&0xC000)>>>14;
                        //  log.info("code " + code);
                        switch (code) {
                            case 0: // address
                                // If the data is an address, we write out an address value if we either get an ADC reading or an x address.
                                // We also write a (fake) address if
                                // we get two y addresses in a row, which occurs when the on-chip AE state machine doesn't properly function.
                                //  Here we also read y addresses but do not write out any output address until we get either 1) an x-address, or 2)
                                // another y address without intervening x-address.
                                // NOTE that because ADC events do not have a timestamp, the size of the addresses and timestamps data are not the same.
                                // To simplify data structure handling in AEPacketRaw and AEPacketRawPool,
                                // ADC events are timestamped just like address-events. ADC events get the timestamp of the most recently preceeding address-event.
                                // NOTE2: unmasked bits are read as 1's from the hardware. Therefore it is crucial to properly mask bits.
                                if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
                                    buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
                                } else {
                                    if ((dataword & SeeBetter1011.ADDRESS_TYPE_MASK) == SeeBetter1011.ADDRESS_TYPE_ADC) {
                                        addresses[eventCounter] = dataword;
                                        timestamps[eventCounter] = currentts;  // ADC event gets last timestamp
                                        eventCounter++;
//                                              System.out.println("ADC word: " + (dataword&SeeBetter1011.ADC_DATA_MASK));
                                    } else if ((buf[i + 1] & Xmask) == Xmask) {////  received an X address, write out event to addresses/timestamps output arrays
                                        // x adddress
                                        //xadd = (buf[i] & 0xff);  //
                                        addresses[eventCounter] = (lasty << SeeBetter1011.YSHIFT) | (dataword & (SeeBetter1011.XMASK | SeeBetter1011.POLMASK));  // combine current bits with last y address bits and send
                                        timestamps[eventCounter] = currentts; // add in the wrap offset and convert to 1us tick
                                        eventCounter++;
                                        //    log.info("received x address");
                                        gotY = false;
//                                        if (doubleY)
//                                        {
//                                            doubleY=false;
//                                            System.out.println(yonlycons+ " Y addresses consecutively recieved in SeeBetterHardwareInterface, total y only: "+ yonlycount); // this printout makes display very jerky!!!
//                                            yonlycons=0;
//                                        }
                                    } else {// y address
                                        // lasty = (0xFF & buf[i]); //
                                        if (gotY) {// TODO creates bogus event to see y without x. This should not normally occur.
                                            addresses[eventCounter] = (lasty << SeeBetter1011.YSHIFT) | ( 44 << 1);                 //(0xffff&((short)buf[i]&0xff | ((short)buf[i+1]&0xff)<<8));
                                            timestamps[eventCounter] = currentts; //*TICK_US; //add in the wrap offset and convert to 1us tick
                                            eventCounter++;
//                                            yonlycount++;
//                                            yonlycons++;
//                                            doubleY=true;
                                        }
                                        if ((buf[i] & IntensityMask) != 0) { // intensity spike
                                            // log.info("received intensity bit");
                                            addresses[eventCounter] = SeeBetter1011.INTENSITYMASK;
                                            timestamps[eventCounter] = currentts;
                                            eventCounter++;
                                        }
                                        lasty = (SeeBetter1011.YMASK >>> SeeBetter1011.YSHIFT) & dataword; //(0xFF & buf[i]); //
                                        gotY = true;
                                    }
                                }
                                break;
                            case 1: // timestamp
                                lastts = currentts;
                                currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
                                currentts = (TICK_US * (currentts + wrapAdd));
                                if(lastts>currentts && nonmonotonicTimestampWarningCount-->0){
                                    log.warning("non-monotonic timestamp: currentts="+currentts+" lastts="+lastts+" currentts-lastts="+(currentts-lastts));
                                }
                                //           log.info("received timestamp");
                                break;
                            case 2: // wrap
                                wrapAdd += 0x4000L;
                                NumberOfWrapEvents++;
                                //   log.info("wrap");
                                break;
                            case 3: // ts reset event
                                nonmonotonicTimestampWarningCount=NONMONOTONIC_WARNING_COUNT;
                                this.resetTimestamps();
                                //   log.info("timestamp reset");
                                break;
                        }
                    } // end for

                    buffer.setNumEvents(eventCounter);
                    // write capture size
                    buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                    buffer.systemModificationTimeNs = System.nanoTime();

                    //     log.info("packet size " + buffer.lastCaptureLength + " number of Y addresses " + numberOfY);
                    // if (NumberOfWrapEvents!=0) {
                    //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                    //}
                    //System.out.println("wrapAdd : "+ wrapAdd);
                } // sync on aePacketRawPool
            } catch (java.lang.IndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }
    }
}
