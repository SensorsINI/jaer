/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.

NEWWWWWWWWWWWWWWWWWWW
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

import ch.unizh.ini.jaer.chip.cochlea.*;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2Biasgen;
/*import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;*/
import java.nio.ByteBuffer;
import static net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2.log;
import org.usb4java.Device;
//import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.HasSyncEventOutput;

/**
 * The hardware interface to CochleaAMS1c.
 *
 * @author damir
 */
public class CochleaAMS1cHardwareInterface extends CypressFX2Biasgen implements BiasgenHardwareInterface {

    /** The USB product ID of this device */
    static public final short PID = (short) 0x8406;
    /** data type is either timestamp or data (AE address or ADC reading) */
    public static final int DATA_CODE_MASK = 0xc000,
            DATA_CODE_DATA = 0x0000,
            DATA_CODE_TIMESTAMP = 0x4000,
            DATA_CODE_WRAP = 0x8000,
            DATA_CODE_TIMESTAMP_RESET = 0xd000;
    /** Data word is tagged as AER address or ADC sample by bit 0x2000 (DATA_TYPE_MASK). Set (ADDRESS_TYPE_ADC)=ADC sample; unset (DATA_TYPE_AER_ADDRESS)=AER address. */
    public static final int DATA_TYPE_MASK = 0x2000, //
            DATA_TYPE_AER_ADDRESS = 0x0000, // aer addresses don't have the bit set
            DATA_TYPE_ADC = 0x2000; // adc samples have this bit set
    /** For ADC data, the data is defined by the ADC channel and whether it is the first ADC value from the scanner. */
    public static final int ADC_DATA_MASK=0x3fff, // all ADC + sync bits+ ID bit that it is ADC sample
            ADC_SAMPLE_MASK = 0x3ff, // the actual sample bits
            ADC_SYNC_BIT = 0x1000, // marks sync input active
            ADC_CHANNEL_MASK = 0x0c00; // marks ADC channel of data
    /** AER_DATA_MASK (0x3ff) part of data word. */
    public static final int AER_DATA_MASK = 0x3ff; // used elsewhere to mask for AER data
     public final byte VR_IS_TS_MASTER = (byte) 0xCB;  // this VR make the board timestamp master if argument is 1, otherwise it is slave device (default in firmware is master device)

    /*public CochleaAMS1cHardwareInterface(int n) {
        super(n);
    }*/
    protected CochleaAMS1cHardwareInterface(final Device device) {
            super(device);
    }

    /** Sends the vendor request to power down the Masterbias from the Masterbias GUI panel. This powerdown bit is the
     * same as ConfigBit D5 in the CochleaAMS1c.Biasgen but is handled here differently to be backward compatible.
     *
     * @param powerDown true to power down the master bias.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
//    @Override
//    public synchronized void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
//        if (gUsbIo == null) {
//            throw new RuntimeException("device must be opened before sending this vendor request");
//        }
//        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
//        int result;
//        //        System.out.println("sending bias bytes");
//        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
//        vendorRequest.Request = VENDOR_REQUEST_POWERDOWN;
//        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
//        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
//        vendorRequest.RequestTypeReservedBits = 0;
//        vendorRequest.Index = 0;  // meaningless for this request
//
//        vendorRequest.Value = (short) (powerDown ? 1 : 0);  // this is the request bit, if powerDown true, send value 1, false send value 0
//
//        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
//        result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
//        if (result != de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
//            throw new HardwareInterfaceException("setPowerDown: unable to send: " + UsbIo.errorText(result));
//        }
//        HardwareInterfaceException.clearException();
//
//    }

    @Override
    public synchronized void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!(biasgen instanceof CochleaAMS1c.Biasgen)) {
            log.warning("biasgen is not instanceof CochleaAMS1c.Biasgen");
            return;
        }
        ((CochleaAMS1c.Biasgen) biasgen).sendConfiguration(); // delegates actual work to Biasgen object
    }

    @Override
    public void setInEndpointEnabled(boolean inEndpointEnabled) throws HardwareInterfaceException {
        super.setInEndpointEnabled(inEndpointEnabled);
        if((chip!=null) && (chip.getBiasgen()!=null)) {
            chip.getBiasgen().sendConfiguration(chip.getBiasgen());
        } // TODO hack to get configuration sent after event acquisition starts data transfer, which messes up configuration on AMS1c
        // still not clear why the configuration must be sent again after starting event acquisition, because nothing in firmware should reset the CPLD after the configuration has already been sent
    }



    @Override
    public synchronized void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        throw new HardwareInterfaceException("Flashing configuration not supported yet.");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        if (!(biasgen instanceof CochleaAMS1c.Biasgen)) {
            log.warning(biasgen + " is not instanceof CochleaAMS1c.Biasgen, returning null array");
            return null;
        }
        CochleaAMS1c.Biasgen b = (CochleaAMS1c.Biasgen) biasgen;
        return new byte[0];
    }

    @Override
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
        getAeReader().startThread();
    }

    /** Determines if data is AER address
     *
     * @param dataword 16 bit 'address' data
     * @return true if AER address
     */
    final public static boolean isAERAddress(int dataword) {
        return (dataword & DATA_TYPE_MASK) == DATA_TYPE_AER_ADDRESS;
    }

    /** Determines is data is ADC sample.
     *
     * @param dataword 16 bit 'address' data
     * @return true if ADC sample
     */
    final public static boolean isADCSample(int dataword) {
        return (dataword & DATA_TYPE_MASK) == DATA_TYPE_ADC;
    }

    /** Parses the ADC channel number from the ADC sample word
     *
     * @param adcData
     * @return channel number, 0-3
     */
    final public static int adcChannel(int adcData) {
        return ((adcData & ADC_CHANNEL_MASK) >>> Integer.bitCount(ADC_SAMPLE_MASK)); // bits 11:10
    }

    /** Parses the ADC sample value from the ADC sample word
     *
     * @param adcData
     * @return sample value, 10 bits
     */
    final public static int adcSample(int adcData) {
        return (adcData & ADC_SAMPLE_MASK); // rightmost 10 bits 9:0
    }

    /** Is the ADC sample associated with the sync input high at the time of sampling.
     *
     * @param adcData
     * @return true if sync input (from cochlea) is active (it is active low during sync period when no channel is selected)
     */
    final public static boolean isScannerSyncBit(int adcData) {
        return ((adcData & ADC_SYNC_BIT) == 0);
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class AEReader extends CypressFX2Biasgen.AEReader {

        /*
         * data type fields
         */
        public static final int MAX_ADC = (1 << 12) - 1;
        private int currentts = 0;

        public AEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        /** Translates data to internal raw form, taking account of wrap events and ADC events.
         * The CochleaAMS1c firmware sends events in word parallel format
         * (as opposed to burst mode), so each event is sent in its entirety of full address and timestamp.
         *
         * <p>
         * Raw data from the Cypress FX2 comes in 16 bit words consisting of data (AER addresses, ADC samples, or timestamps of these data),
         * or special words signaling timestamp wraps, timestamp reset.
         * <p>
         * This data is either an AE address or ADC reading. CochleaAMS uses 10 AER address bits.
        An extra bit signals that the data is from the on-board ADC.
        <p>
        Bits are as follows:
         * <ul>
         * <li> 15:14 code for data type (mask 0xC000). Contents of bits 15:14 are codes signaling the following:
         * <ul>
         *  <li> 0 AER or ADC data
         *  <li> 1 timestamp
         *  <li> 3 wrap event
         *  <li> 4 timestamp reset on board
         * </ul>
         * <li> 13:0 data (mask 0x3ff). This data can be data or timestamp.
         * If the data is a timestamp (code from 15:14=1) then all 14 bits are used.
         * If the data is AER or ADC data (code from 15:14=0), then bit 13 determines type of data
         * <ul>
         *  <li> 13 is the data type (mask 0x2000). 0=AER data, 1=ADC data
         * </ul>
         * </ul>
         *
         * The bits are shown below:<br>
         * <img src="doc-files/CochleaAMS1cDataBits.png" />
         *
         *
         * @param b the raw byte buffer
         * @see CochleaAMS1c
         */
        @Override
        protected void translateEvents(final ByteBuffer b) {
            try {
                // data from cochleaams is not stateful.

//            if(tobiLogger.isEnabled()==false) tobiLogger.setEnabled(true); //debug
                synchronized (aePacketRawPool) {
                    AEPacketRaw buffer = aePacketRawPool.writeBuffer();

                    int NumberOfWrapEvents;
                    NumberOfWrapEvents = 0;
                    byte[] buf = null;
                    if(!b.hasArray())
                    {
                       // log.warning("No backing array for "+b);
                        buf= new byte[b.limit()];
                        b.get(buf);
                    }
                    else
                    {
                        buf = b.array();
                    }

                    
                    int bytesSent = b.limit();
                    if ((bytesSent % 2) != 0) {
                        log.warning("warning: " + bytesSent + " bytes sent, which is not multiple of 2");
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
                            case 0: // data, either AER address or ADC sample
                                // If the data is an address, we write out an address value if we either get an ADC reading or an x address.
                                // To simplify data structure handling in AEPacketRaw and AEPacketRawPool,
                                // ADC events are timestamped just like address-events.
                                // NOTE2: unmasked bits are read as 1's from the hardware. Therefore it is crucial to properly mask bits.
                                if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
                                    buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
                                } else {
                                    if (isADCSample(dataword)) {
                                        addresses[eventCounter] = dataword & ADC_DATA_MASK; // leave all bits unchanged for ADC sample
                                        timestamps[eventCounter] = currentts;  // ADC event gets timestamp too
                                        eventCounter++;
//                                        System.out.println("ADC word: " + dataword + " adcChannel=" + adcChannel(dataword) + " adcSample=" + adcSample(dataword) + " isScannerSyncBit=" + isScannerSyncBit(dataword));
                                    } else { //  received an address, write out event to addresses/timestamps output arrays, masking out other bits
                                        addresses[eventCounter] = (dataword & AER_DATA_MASK); // ok to mask out other bits now for this AER address, since ADC samples preserve the ADC ID bit
                                        timestamps[eventCounter] = currentts;
                                        eventCounter++;
//                                        System.out.println("address=" + addresses[eventCounter - 1]);
                                    }
                                }
                                break;
                            case 1: // timestamp - always comes before data
                                currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
                                currentts = (TICK_US * (currentts + wrapAdd));
//                                System.out.println("timestamp=" + currentts);
                                break;
                            case 2: // timestamp wrap
                                wrapAdd += 0x4000L;
                                NumberOfWrapEvents++;
                                //   log.info("wrap");
                                break;
                            case 3: // ts reset event
//                                log.info("got timestamp reset event from hardware, resetting timestamps to zero");
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
//    private String getBitString(short value, short nSrBits) {
//        StringBuilder s = new StringBuilder();
//
//        int k = nSrBits - 1;
//        while (k >= 0) {
//            int x = value & (1 << k); // start with msb
//            boolean b = (x == 0); // get bit
//            s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
//            k--;
//        } // construct big endian string e.g. code=14, s='1011'
//        String bitString = s.toString();
//        return bitString;
//    }
  
  

}
