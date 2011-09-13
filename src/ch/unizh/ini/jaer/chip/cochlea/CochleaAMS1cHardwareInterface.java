/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterface;
import ch.unizh.ini.jaer.chip.util.scanner.ScannerHardwareInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import java.math.BigInteger;
import java.util.prefs.Preferences;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;

/**
 * The hardware interface to CochleaAMS1c.
 * 
 * @author tobi
 */
public class CochleaAMS1cHardwareInterface extends CypressFX2MonitorSequencer implements BiasgenHardwareInterface {

    /** The USB product ID of this device */
    static public final short PID = (short) 0x8406;
    static Preferences hwPrefs = Preferences.userNodeForPackage(CochleaAMS1cHardwareInterface.class); // TODO should really come from Chip instance, not this class
    final byte VR_CONFIG = CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES;

    public CochleaAMS1cHardwareInterface(int n) {
        super(n);
    }

    /** Sends the vendor request to power down the Masterbias from the Masterbias GUI panel. This powerdown bit is the
     * same as ConfigBit D5 in the CochleaAMS1c.Biasgen but is handled here differently to be backward compatible.
     *
     * @param powerDown true to power down the master bias.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if (gUsbIo == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        //        System.out.println("sending bias bytes");
        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
        vendorRequest.Request = VENDOR_REQUEST_POWERDOWN;
        vendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits = 0;
        vendorRequest.Index = 0;  // meaningless for this request

        vendorRequest.Value = (short) (powerDown ? 1 : 0);  // this is the request bit, if powerDown true, send value 1, false send value 0

        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        result = gUsbIo.classOrVendorOutRequest(dataBuffer, vendorRequest);
        if (result != de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("setPowerDown: unable to send: " + UsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();

    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!(biasgen instanceof CochleaAMS1c.Biasgen)) {
            log.warning("biasgen is not instanceof CochleaAMS1c.Biasgen");
            return;
        }
        ((CochleaAMS1c.Biasgen) biasgen).sendConfiguration(); // delegates actual work to Biasgen object
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
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
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class AEReader extends CypressFX2MonitorSequencer.MonSeqAEReader {

        /*
         * data type fields
         */
        /** data type is either timestamp or data (AE address or ADC reading) */
        public final int DATA_TYPE_MASK = 0xc000, DATA_TYPE_ADDRESS = 0x0000, DATA_TYPE_TIMESTAMP = 0x4000, DATA_TYPE_WRAP = 0x8000, DATA_TYPE_TIMESTAMP_RESET = 0xd000;
        /** Address-type refers to data if is it an "address". This data is either an AE address or ADC reading. CochleaAMS uses 10 address bits. 
        An extra bit signals that the data is from the on-board ADC. */
        public final int ADDRESS_TYPE_MASK = 0x2000, EVENT_ADDRESS_MASK = 0x1ff, ADDRESS_TYPE_EVENT = 0x0000, ADDRESS_TYPE_ADC = 0x2000;
        /** For ADC data, the data is defined by the ADC channel and whether it is the first ADC value from the scanner. */
        public static final int ADC_TYPE_MASK = 0x1000, ADC_DATA_MASK = 0xfff, ADC_START_BIT = 0x1000, ADC_CHANNEL_MASK = 0x0000; // right now there is no channel info in the data word
        public static final int MAX_ADC = (int) ((1 << 12) - 1);
        private int currentts = 0;
        private int lastts = 0;

        public AEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        /** Translates data to internal raw form, taking account of wrap events and ADC events. 
         * The CochleaAMS1c firmware sends events in word parallel format
         * (as opposed to burst mode), so each event is sent in its entirety of full address and timestamp.
         * 
         * @param b the raw byte buffer 
         */
        @Override
        protected void translateEvents(UsbIoBuf b) {
            try {
                // data from cochleaams is not stateful. 

//            if(tobiLogger.isEnabled()==false) tobiLogger.setEnabled(true); //debug
                synchronized (aePacketRawPool) {
                    AEPacketRaw buffer = aePacketRawPool.writeBuffer();

                    int NumberOfWrapEvents;
                    NumberOfWrapEvents = 0;

                    byte[] buf = b.BufferMem;
                    int bytesSent = b.BytesTransferred;
                    if (bytesSent % 2 != 0) {
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
                            case 0: // address
                                // If the data is an address, we write out an address value if we either get an ADC reading or an x address.
                                // NOTE that because ADC events do not have a timestamp, the size of the addresses and timestamps data are not the same.
                                // To simplify data structure handling in AEPacketRaw and AEPacketRawPool,
                                // ADC events are timestamped just like address-events. ADC events get the timestamp of the most recently preceeding address-event.
                                // NOTE2: unmasked bits are read as 1's from the hardware. Therefore it is crucial to properly mask bits.
                                if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
                                    buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
                                } else {
                                    if ((dataword & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_ADC) {
                                        addresses[eventCounter] = dataword;
                                        timestamps[eventCounter] = currentts;  // ADC event gets last timestamp
                                        eventCounter++;
//                                        System.out.println("ADC word: " + (dataword & ADC_DATA_MASK));
                                    } else {////  received an X address, write out event to addresses/timestamps output arrays
                                        // x adddress
                                        //xadd = (buf[i] & 0xff);  //
                                        addresses[eventCounter] = (dataword & EVENT_ADDRESS_MASK);  // combine current bits with last y address bits and send
                                        timestamps[eventCounter] = currentts; // add in the wrap offset and convert to 1us tick
                                        eventCounter++;
                                        System.out.println("received address "+addresses[eventCounter-1]);
                                    }
                                }
                                break;
                            case 1: // timestamp
                                lastts = currentts;
                                currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
                                currentts = (TICK_US * (currentts + wrapAdd));
                                System.out.println("received timestamp "+currentts);
                                break;
                            case 2: // wrap
                                wrapAdd += 0x4000L;
                                NumberOfWrapEvents++;
                                //   log.info("wrap");
                                break;
                            case 3: // ts reset event
                                this.resetTimestamps();
                                //   log.info("timestamp reset");
                                break;
                        }
                    } // end for

                    buffer.setNumEvents(eventCounter);
                    // write capture size
                    buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;

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




    private String getBitString(short value, short nSrBits) {
        StringBuilder s = new StringBuilder();

        int k = nSrBits - 1;
        while (k >= 0) {
            int x = value & (1 << k); // start with msb
            boolean b = (x == 0); // get bit
            s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
            k--;
        } // construct big endian string e.g. code=14, s='1011'
        String bitString = s.toString();
        return bitString;
    }




}
