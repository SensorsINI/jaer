/*
 * CypressFX2Biasgen.java
 *
 * Created on 23 Jan 2008
 *
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.ProgressMonitor;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.config.ApsDvsConfig;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.EngineeringFormat;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import eu.seebetter.ini.chips.ApsDvsChip;
import eu.seebetter.ini.chips.DAViS.IMUSample;

/**
 * Adds functionality of apsDVS sensors to based CypressFX2Biasgen class. The
 * key method is translateEvents that parses the data from the sensor to
 * construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class ApsDvsHardwareInterface extends CypressFX2Biasgen {

    /**
     * The USB product ID of this device
     */
    static public final short PID = (short) 0x840D;
    static public final short DID = (short) 0x0002;
    /**
     * Number of IMU samples that we can queue up from IMUDataReader before
     * being consumed here by merging with event stream
     */
    public static final int IMU_SAMPLE_QUEUE_LENGTH = 128;
    private ArrayBlockingQueue<IMUSample> imuSampleQueue; // this queue is used for holding imu samples sent to aeReader
//     private long imuLastSystemTimeNano=System.nanoTime();
//     private LowpassFilter imuSampleIntervalFilterNs=new LowpassFilter(100);
//     private int imuSampleCounter=0;
//     private static final int IMU_SAMPLE_RATE_PRINT_INTERVAL=5000;

    private boolean syncEventEnabled = prefs.getBoolean("ApsDvsHardwareInterface.syncEventEnabled", true); // default is true so that device is the timestamp master by default, necessary after firmware rev 11
    /** SYNC events are detected when this bit mask is detected in the input event stream.
    @see HasSyncEventOutput
     */
    public static final int SYNC_EVENT_BITMASK = 0x8000;


    /**
     * Creates a new instance of CypressFX2Biasgen
     */
    public ApsDvsHardwareInterface(int devNumber) {
        super(devNumber);
        imuSampleQueue = new ArrayBlockingQueue<IMUSample>(IMU_SAMPLE_QUEUE_LENGTH); // TODO not needed

    }

    /**
     * Overridden to use PortBit powerDown in biasgen
     *
     * @param powerDown true to power off masterbias
     * @throws HardwareInterfaceException
     */
    @Override
    synchronized public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if ((chip != null) && (chip instanceof ApsDvsChip)) {
            ApsDvsChip apsDVSchip = (ApsDvsChip) chip;
            apsDVSchip.setPowerDown(powerDown);
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
     * Starts reader buffer pool thread and enables in endpoints for AEs. This
     * method is overridden to construct our own reader with its translateEvents
     * method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();

        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }
    boolean gotY = false; // TODO  hack for debugging state machine

     @Override
    public synchronized void resetTimestamps() {
        super.resetTimestamps();
        if (imuSampleQueue != null) {
            imuSampleQueue.clear();  // TODO not needed
        }
    }

    /**
        #define VR_IMU 0xC6 // this VR is for dealing with IMU
        #define IMU_CMD_WRITE_REGISTER 1 // arguments are 8-bit bit register address and 8-bit value to write
        #define IMU_CMD_READ_REGISTER 2 // argument is 9-bit register address to read
     */
    private static final byte VR_IMU = (byte) 0xC6, IMU_CMD_READ_REGISTER=(byte)2, IMU_CMD_WRITE_REGISTER=(byte)1;

    /**
     * Sets an IMU register value. This is a blocking method.
     *
     * @param imuRegister register address on device.
     * @param imuRegisterValue the value to set.*
     */
    public synchronized void writeImuRegister(byte imuRegister, byte imuRegisterValue) throws HardwareInterfaceException {
        //                setup1              setup3,setup2                   setup4                setup5
        sendVendorRequest(VR_IMU,  IMU_CMD_WRITE_REGISTER, (short)(0xffff & ((0xff & imuRegister) | ((0xff & imuRegisterValue) << 8))));
//        sendVendorRequest(byte request, short value, short index)
}

//    /**
//     * Reads an IMU register value. This method blocks until value is read.
//     *
//     * @param register the register address.
//     * @return the value of the register.
//     */
//    public synchronized byte readImuRegister(byte register) throws HardwareInterfaceException {
//        sendVendorRequest(VR_IMU,  (short) IMU_CMD_READ_REGISTER,(short) (0xff & register));
//        // read back from control endpoint to get the register value
//        USBIO_CLASS_OR_VENDOR_REQUEST vr = new USBIO_CLASS_OR_VENDOR_REQUEST();
//        USBIO_DATA_BUFFER buf = new USBIO_DATA_BUFFER(1);
//
//        vr.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
//        vr.Type = UsbIoInterface.RequestTypeVendor;
//        vr.Recipient = UsbIoInterface.RecipientDevice;
//        vr.RequestTypeReservedBits = 0;
//        vr.Request = VR_IMU;
//        vr.Index = 0;
//        vr.Value = 0;
//
//        buf.setNumberOfBytesToTransfer(1);
//        int status = gUsbIo.classOrVendorInRequest(buf, vr);
//
//        if (status != USBIO_ERR_SUCCESS) {
//            throw new HardwareInterfaceException("Unable to receive IMU register value: " + UsbIo.errorText(status));
//        }
//        if (buf.getBytesTransferred() != 1) {
//            throw new HardwareInterfaceException("Wrong number of bytes transferred, recieved " + buf.getBytesTransferred() + " but should have recieved 1 byte");
//        }
//        byte value = buf.Buffer()[0];
//        return value;
//    }

    /**
     * This reader understands the format of raw USB data and translates to the
     * AEPacketRaw
     */
    public class RetinaAEReader extends CypressFX2.AEReader /*implements PropertyChangeListener*/ {

        private static final int NONMONOTONIC_WARNING_COUNT = 30; // how many warnings to print after start or timestamp reset
        public static final int IMU_POLLING_INTERVAL_EVENTS = 100; // tobi changed to 100 from 1000

        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
            resetFrameAddressCounters();
            setAEReaderFifoSize(((ApsDvsConfig)(chip.getBiasgen())).getAeReaderFifoSize()); // TODO awkward, should move all this USB buffer size stuff to AEchip preferences and out of the hardware interface, which should not have any preference values!
            setAEReaderNumBuffers(((ApsDvsConfig)(chip.getBiasgen())).getAeReaderNumBuffers());
        }
        /**
         * Method to translate the UsbIoBuffer for the DVS320 sensor which uses
         * the 32 bit address space.
         * <p>
         * It has a CPLD to timestamp events and uses the CypressFX2 in slave
         * FIFO mode.
         * <p>The DVS320 has a burst mode readout mechanism that outputs a row
         * address, then all the latched column addresses. The columns are
         * output left to right. A timestamp is only meaningful at the row
         * addresses level. Therefore the board timestamps on row address, and
         * then sends the data in the following sequence: timestamp, row, col,
         * col, col,....,timestamp,row,col,col...
         * <p>
         * Intensity information is transmitted by bit 8, which is set by the
         * chip The bit encoding of the data is as follows
         * <literal>
         * Address bit	Address bit pattern 0	LSB Y or Polarity ON=1 1	Y1 or LSB
         * X 2	Y2 or X1 3	Y3 or X2 4	Y4 or X3 5	Y5 or X4 6	Y6 or X5 7	Y7 (MSBY)
         * or X6 8	intensity or X7. This bit is set for a Y address if the
         * intensity neuron has spiked. This bit is also X7 for X addreses. 9	X8
         * (MSBX) 10	Y=0, X=1
         * </literal>
         *
         * The two msbs of the raw 16 bit data are used to tag the type of data,
         * e.g. address, timestamp, or special events wrap or reset host
         * timestamps.
         * <literal>
         * Address Name 00xx xxxx xxxx xxxx	pixel address 01xx xxxx xxxx xxxx
         * timestamp 10xx xxxx xxxx xxxx	wrap 11xx xxxx xxxx xxxx	timestamp
         * reset
         * </literal>
         *
         * The msb of the 16 bit timestamp is used to signal a wrap (the actual
         * timestamp is only 15 bits). The wrapAdd is incremented when an empty
         * event is received which has the timestamp bit 15 set to one.
         * <p>
         * Therefore for a valid event only 15 bits of the 16 transmitted
         * timestamp bits are valid, bit 15 is the status bit. overflow happens
         * every 32 ms. This way, no roll overs go by undetected, and the
         * problem of invalid wraps doesn't arise.
         *
         * @param minusEventEffect the data buffer
         * @see #translateEvents
         */
        static private final byte XBIT = (byte) 0x08;
        static private final byte EXTERNAL_PIN_EVENT = (byte) 0x10; // external pin has seen falling edge
        public static final int ADDRESS_TYPE_BIT = 0x2000; // data part of short contains according to apsDVS USB event spec 0=DVS, 1=APS
        public static final int FRAME_START_BIT = 0x1000; // signals frame start when APS sample
        private int lasty = 0;
        private int currentts = 0;
        private int lastts = 0;
        private int nonmonotonicTimestampWarningCount = NONMONOTONIC_WARNING_COUNT;
        private int frameEvtDropped = 10000;
        private int[] countX;
        private int[] countY;
        private int numReadoutTypes = 3;
        private IMUSample imuSample = null;

        private boolean readingIMUEvents = false; // Indicates that we are reading in IMU Events from the buffer to switch reading mode
        private int countIMUEvents = 0;
        private short[] dataIMUEvents = new short[7];

        private class Stats{
            long lastBufTime=0;
            final int maxLength=50;
            LinkedList<BufInfo> list=new LinkedList<BufInfo>();
            EngineeringFormat fmt=new EngineeringFormat();
            void addBuf(UsbIoBuf b){
                list.add(new BufInfo(b.BytesTransferred));
                if(list.size()>maxLength) {
					list.removeFirst();
				}
            }
            @Override
			public String toString(){
                StringBuilder sb=new StringBuilder("buffer stats: ");
                for(BufInfo b:list){
                    sb.append(String.format("%s, ",b.toString()));
                }
                return sb.toString();
            }
            private class BufInfo{
                long dtNs; int numBytes;

                public BufInfo(int numEvents) {
                    this.numBytes = numEvents;
                    long now=System.nanoTime();
                    dtNs=now-lastBufTime;
                    lastBufTime=now;
                }

                @Override
				public String toString(){
                    return String.format("%ss %d bytes",fmt.format(1e-9f*dtNs),numBytes);
                }

            }
        }
        private Stats stats=new Stats();

        @Override
        public void setNumBuffers(int numBuffers) {
            super.setNumBuffers(numBuffers);
            ((ApsDvsConfig)(chip.getBiasgen())).setAeReaderNumBuffers(numBuffers);
        }

        @Override
        public int getNumBuffers() {
            return ((ApsDvsConfig)(chip.getBiasgen())).getAeReaderNumBuffers();
        }

        @Override
        public void setFifoSize(int fifoSize) {
            super.setFifoSize(fifoSize);
            ((ApsDvsConfig)(chip.getBiasgen())).setAeReaderFifoSize(fifoSize);
        }

        @Override
        public int getFifoSize() {
            return ((ApsDvsConfig)(chip.getBiasgen())).getAeReaderFifoSize();
        }



        @Override
        protected void translateEvents(UsbIoBuf b) {
            // TODO debug
//            if(imuSample!=null) System.out.println(imuSample);
//            stats.addBuf(b);
            boolean translateRowOnlyEvents=((ApsDvsConfig)(chip.getBiasgen())).isTranslateRowOnlyEvents();
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
                    if ((bytesSent % 2) != 0) {
                        System.err.println("warning: " + bytesSent + " bytes sent, which is not multiple of 2");
                        bytesSent = (bytesSent / 2) * 2; // truncate off any extra part-event
                    }

                    int[] addresses = buffer.getAddresses();
                    int[] timestamps = buffer.getTimestamps();
//                    log.info("received " + bytesSent + " bytes");
                    // write the start of the packet
                    buffer.lastCaptureIndex = eventCounter;
//                     tobiLogger.log("#packet");

                    for (int i = 0; i < bytesSent; i += 2) {
                        //   tobiLogger.log(String.format("%d %x %x",eventCounter,buf[i],buf[i+1])); // DEBUG
                        //   int val=(buf[i+1] << 8) + buf[i]; // 16 bit value of data
                        int dataword = (0xff & buf[i]) | (0xff00 & (buf[i + 1] << 8));  // data sent little endian

                        // Check that we are not reading IMU Events which have a different encoding scheme
                        // START IF readingIMUEvents
                        if (readingIMUEvents == false) {

                            final int code = (buf[i + 1] & 0xC0) >> 6; // gets two bits at XX00 0000 0000 0000. (val&0xC000)>>>14;
                            //  log.info("code " + code);
                            int xmask = (ApsDvsChip.XMASK | ApsDvsChip.POLMASK) >>> ApsDvsChip.POLSHIFT;
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

                                	// We first need to see if any special IMU
									// event series is coming in.
									// If it is, we need to switch to reading
									// it. Also, checks for buffer
									// overruns need to happen in both places.
									if (!((dataword & ADDRESS_TYPE_BIT) == ADDRESS_TYPE_BIT)
										&& ((buf[i + 1] & EXTERNAL_PIN_EVENT) == EXTERNAL_PIN_EVENT)
										&& ((buf[i] & ApsDvsChip.IMUMASK) == ApsDvsChip.IMUMASK)) {
										readingIMUEvents = true;
										break;
									}

                                    if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
                                        buffer.overrunOccuredFlag = true; // throw away events if we have overrun the output arrays
                                    } else {
                                        int addr, timestamp; // used to store event to write out
                                        boolean haveEvent = false;
                                        if ((dataword & ADDRESS_TYPE_BIT) == ADDRESS_TYPE_BIT) {

                                            //APS event
                                            if ((dataword & FRAME_START_BIT) == FRAME_START_BIT) {
                                                resetFrameAddressCounters();
                                            }
                                            int readcycle = (dataword & ApsDvsChip.ADC_READCYCLE_MASK) >> ApsDvsChip.ADC_READCYCLE_SHIFT;
                                            if (countY[readcycle] >= chip.getSizeY()) {
                                                countY[readcycle] = 0;
                                                countX[readcycle]++;
                                            }
                                            if (countX[readcycle] >= chip.getSizeX()) {
                                                if (frameEvtDropped == 0) {
                                                    log.warning("countX above chip size, a start frame event was dropped");
                                                    frameEvtDropped = 10000;
                                                }
                                                else {
                                                    frameEvtDropped--;
                                                }
                                            }
                                            int xAddr = (short) (chip.getSizeX() - 1 - countX[readcycle]);
                                            int yAddr = (short) (chip.getSizeY() - 1 - countY[readcycle]);
    //                                        if(xAddr >= chip.getSizeX() || xAddr<0 || yAddr >= chip.getSizeY() || yAddr<0)System.out.println("out of bounds event: x = "+xAddr+", y = "+yAddr+", read = "+readcycle);
                                            countY[readcycle]++;
                                            addr = ApsDvsChip.ADDRESS_TYPE_APS
                                                    | ((yAddr << ApsDvsChip.YSHIFT) & ApsDvsChip.YMASK)
                                                    | ((xAddr << ApsDvsChip.XSHIFT) & ApsDvsChip.XMASK)
                                                    | (dataword & (ApsDvsChip.ADC_READCYCLE_MASK | ApsDvsChip.ADC_DATA_MASK));
                                            timestamp = currentts;  // ADC event gets last timestamp
                                            haveEvent = true;
    //                                              System.out.println("ADC word: " + (dataword&SeeBetter20.ADC_DATA_MASK));

                                        // Detects Special Events which can be of type IMUEvents
                                        } else if ((buf[i + 1] & EXTERNAL_PIN_EVENT) == EXTERNAL_PIN_EVENT) {
                                            addr = ApsDvsChip.EXTERNAL_INPUT_EVENT_ADDR;
                                            timestamp = currentts;
                                            haveEvent = false; // TODO set false for now
    //                                        haveEvent = true; // TODO don't write out the external pin events for now, because they mess up the IMU special events

                                            // Detect Special / External Event of Type IMU, and set flag to start reading subsequent pairs of bytes as IMUEvents
                                            if ((buf[i] & ApsDvsChip.IMUMASK) == ApsDvsChip.IMUMASK) {
                                                readingIMUEvents = true;
                                                //if (bytesSent - i < 20) System.out.println(bytesSent - i);
                                            }

                                        } else if ((buf[i + 1] & XBIT) == XBIT) {//  received an X address, write out event to addresses/timestamps output arrays

                                            // x/column part of DVS event
                                            // x column adddress received, combine with previous row y address and commit to output packet
                                            addr = (lasty << ApsDvsChip.YSHIFT) | ((dataword & xmask) << ApsDvsChip.POLSHIFT);  // combine current bits with last y address bits and send
                                            timestamp = currentts; // add in the wrap offset and convert to 1us tick
                                            haveEvent = true;
                                            //log.info("X: "+((dataword & ApsDvsChip.XMASK)>>1));
                                            gotY = false;
                                        } else { // row address came, just save it until we get a column address
                                            addr = 0;
                                            timestamp = 0;
                                            // y/row part of DVS event
                                            if (gotY) { // no col address came after last row address, last event was row-only event
                                                if (translateRowOnlyEvents) {// make  row-only event

                                                    addresses[eventCounter] = (lasty << ApsDvsChip.YSHIFT);  // combine current bits with last y address bits and send
                                                    timestamps[eventCounter] = currentts; // add in the wrap offset and convert to 1us tick
                                                    eventCounter++;
                                                }

                                            }
                                            // y address, save it for all the x/row addresses that should follow
                                            int ymask = (ApsDvsChip.YMASK >>> ApsDvsChip.YSHIFT);
                                            lasty = ymask & dataword; //(0xFF & buf[i]); //
                                            gotY = true;
                                            //log.info("Y: "+lasty+" - data "+dataword+" - mask: "+(ApsDvsChip.YMASK >>> ApsDvsChip.YSHIFT));
                                        }
                                        if (haveEvent) {
                                            // see if there are any IMU samples to add to packet
                                            // merge the IMUSamples to the packet, attempting to maintain timestamp monotonicity,
                                            // even if the timestamp is on a different origin that is not related to the data on this endpoint.
                                            if (imuSample == null) { // TODO not needed anymore
                                                imuSample = imuSampleQueue.poll();
                                            }

                                            while ((imuSample != null) && (imuSample.getTimestampUs() < timestamp)) {
                                                eventCounter += imuSample.writeToPacket(buffer, eventCounter);
    //                                            System.out.println(imuSample.toString());
                                                imuSample = imuSampleQueue.poll();
                                            }
                                            while ((imuSample != null) && (imuSample.getTimestampUs() > (timestamp + 10000))) {
                                                imuSample = imuSampleQueue.poll(); // drain out imu samples that are too far in future
                                            }
                                            addresses[eventCounter] = addr;
                                            timestamps[eventCounter++] = timestamp;

                                        }
                                    }

                                    break;
                                case 1: // timestamp
                                    lastts = currentts;
                                    currentts = ((0x3f & buf[i + 1]) << 8) | (buf[i] & 0xff);
                                    currentts = (TICK_US * (currentts + wrapAdd));
                                    if ((lastts > currentts) && (nonmonotonicTimestampWarningCount-- > 0)) {
                                        log.warning(this.toString()+": non-monotonic timestamp: currentts=" + currentts + " lastts=" + lastts + " currentts-lastts=" + (currentts - lastts));
                                    }
                                    //           log.info("received timestamp");
                                    break;
                                case 2: // wrap
                                    lastwrap = currentwrap;
                                    currentwrap = (0xff & buf[i]);
                                    int kk = currentwrap - lastwrap;
                                    if (kk<0) {
										kk = (256-lastwrap) + currentwrap;
									}
                                    if (kk==1) {
										wrapAdd += 0x4000L;
									}
									else if (kk>1){
                                        log.warning(this.toString()+": detected " + (kk-1) + " missing wrap events.");
                                        //while (kk-->0){
                                            wrapAdd += kk*0x4000L;
                                            NumberOfWrapEvents+=kk;
                                        //}

                                    }
                                    //   log.info("wrap");
                                    break;
                                case 3: // ts reset event
                                    nonmonotonicTimestampWarningCount = NONMONOTONIC_WARNING_COUNT;
                                    this.resetTimestamps();
                                    log.info("timestamp reset event received on "+super.toString());
                                    lastts =0;
                                    currentts =0;
                                    break;
                            }

                        // Code to read IMUEvents
                        } else {
                            // Populate array containing IMU Events
                            dataIMUEvents[countIMUEvents] = (short) dataword;

                            // Increment Counter
                            if (countIMUEvents < 6) {
                                countIMUEvents++;

                            // When have a full set of IMU Events
                            } else {
                                try {
                                    // Convert IMU Events array and current timestamp to an IMUSample
                                    IMUSample sample = new IMUSample(currentts, dataIMUEvents);
                                    // Add to IMU Sample Queue
                                    imuSampleQueue.add(sample);
                                    // Update buf counter to iterate through next word
                                } catch (IllegalStateException ex) {
                                    if ((putImuSampleToQueueWarningCounter++ % PUT_IMU_WARNING_INTERVAL) == 0) {
                                        log.warning("putting IMUSample to queue not possible because queue has" + imuSampleQueue.size() + " samples and was full");
                                    }
                                }
                                // Stop reading data as IMU
                                readingIMUEvents = false;
                                // Reset counter
                                countIMUEvents = 0;

                            }
                        } // END IF readingIMUEvents


                    } // end loop over usb data buffer

                    buffer.setNumEvents(eventCounter);
                    // write capture size
                    buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;

//                         log.info("packet size " + buffer.lastCaptureLength);
                    // if (NumberOfWrapEvents!=0) {
                    //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                    //}
                    //System.out.println("wrapAdd : "+ wrapAdd);
                } // sync on aePacketRawPool
            } catch (java.lang.IndexOutOfBoundsException e) {
                log.warning(e.toString());
            }
        }

        private void resetFrameAddressCounters() {
            if ((countX == null) || (countY == null)) {
                countX = new int[numReadoutTypes];
                countY = new int[numReadoutTypes];
            }
            Arrays.fill(countX, 0, numReadoutTypes, 0);
            Arrays.fill(countY, 0, numReadoutTypes, 0);
//            log.info("Start of new frame");
        }
        private int putImuSampleToQueueWarningCounter = 0;
        private static final int PUT_IMU_WARNING_INTERVAL = 10000;

//        @Override
//        public void propertyChange(PropertyChangeEvent evt) {
//            // we come here because the IMUDataReader has generated a PropertyChangeEvent and we are subscribed to this object.
//            if (evt.getPropertyName() != PROPERTY_CHANGE_ASYNC_STATUS_MSG) {
//                return;
//            }
//            try {
//                UsbIoBuf buf = (UsbIoBuf) evt.getNewValue();
//                    IMUSample sample = new IMUSample(buf);
//                try {
////                    System.out.println(sample.getTimestampUs());
//                    imuSampleQueue.add(sample);
//                } catch (IllegalStateException ex) {
//                    if (putImuSampleToQueueWarningCounter++ % PUT_IMU_WARNING_INTERVAL == 0) {
//                        log.warning("putting IMUSample "+imuSample+" to queue not possible because queue has" + imuSampleQueue.size() + " samples and was full");
//                    }
//                }
//
//            } catch (ClassCastException e) {
//                log.warning("receieved wrong type of data for the IMU: " + e.toString());
//            }
//        }
    }
//    /**
//     * Status messages sent by device. This header byte identifies the message
//     * type.
//     */
//    public static final byte STATUS_MSG_IMU_DATA = (byte) 0xff;
//    /**
//     * Property change fired when a new message is received on the asynchronous
//     * status endpoint.
//     *
//     * @see AsyncStatusThread
//     */
//    public static final String PROPERTY_CHANGE_IMU_DATA = "IMUData";

//    /**
//     * This threads reads IMU data from the camera on endpoint 2 - currently not used.
//     *
//     * @author tobi delbruck
//     * @see #getSupport()
//     */
//    protected class IMUDataReader extends UsbIoReader { // not used yet, still reading IMU samples in AsyncStatusThread
//
//        UsbIoPipe pipe;
//        CypressFX2 monitor;
//        boolean stop = false;
//        byte msg;
//        public static final int STATUS_PRIORITY = Thread.MAX_PRIORITY; // Thread.NORM_PRIORITY+2
//
//        public IMUDataReader(CypressFX2 monitor) {
//            super();
//            this.monitor = monitor;
//
//            int status;
//            status = bind(monitor.getInterfaceNumber(), (byte) 0x82, gDevList, GUID); // bind to this interface, using endpoint 2 which is an IN endpoint
//            if (status != USBIO_ERR_SUCCESS) {
//                log.warning("error binding to pipe for EP2 for device status: " + UsbIo.errorText(status) + ", not starting IMUDataReader");
//                return;
//            }
//            USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
//            pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
//            status = setPipeParameters(pipeParams);
//            if (status != USBIO_ERR_SUCCESS) {
//                log.warning("can't set pipe parameters: " + UsbIo.errorText(status) + ": IMUDataReader may not function properly");
//            }
//        }
//
//        @Override
//        public void startThread(int MaxIoErrorCount) {
//            allocateBuffers(64, 4); // 64-byte buffers, 4 of them on host side
//            super.startThread(MaxIoErrorCount);
//            T.setPriority(STATUS_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
//            T.setName("IMUDataReader");
//        }
//
//        @Override
//        public void processData(UsbIoBuf buffer) {
//            if (buffer.BytesTransferred > 0) {
//                msg = buffer.BufferMem[0];
//
//                switch (msg) {
//
//                    case STATUS_MSG_IMU_DATA:
//                    default:
//                        UsbIoBuf newbuf = new UsbIoBuf(64);
//
//                        // Copy data to new buffer, this one is resubmitted right away.
//                        System.arraycopy(buffer.BufferMem, 0, newbuf.BufferMem, 0, buffer.BytesTransferred);
//                        newbuf.BytesTransferred = buffer.BytesTransferred;
//                        newbuf.Status = buffer.Status;
//
//                        support.firePropertyChange(PROPERTY_CHANGE_IMU_DATA, null, newbuf); // tobi - send message to listeners
//                    }
//            } // we getString 0 byte read on stopping device
//        }
//
//        // called before buffer is submitted to driver
//        @Override
//        public void processBuffer(UsbIoBuf Buf) {
//            Buf.NumberOfBytesToTransfer = Buf.Size;
//            Buf.BytesTransferred = 0;
//            Buf.OperationFinished = false;
//        }
//
//        @Override
//        public void bufErrorHandler(UsbIoBuf Buf) {
//            if (Buf.Status != USBIO_ERR_SUCCESS) {
//                // print error
//                // suppress CANCELED because it is caused by ABORT_PIPE
//                if (Buf.Status != USBIO_ERR_CANCELED) {
//                    log.warning("CypressFX2.IMUDataReader.bufErrorHandler(): USB buffer error: " + UsbIo.errorText(Buf.Status));
//                }
//                if (Buf.Status == USBIO_ERR_DEVICE_GONE) {
//                    log.warning("CypressFX2.IMUDataReader.bufErrorHandler(): device gone, shutting down buffer pool thread");
//                    monitor.close();
//                }
//            }
//        }
//
//        @Override
//        public void onThreadExit() {
//            freeBuffers();
//        }
//    }
//
//           /** This threads reads asynchronous status or other data from the device.
//     * It handles timestamp reset messages from the device and possibly other types of data.
//     It fires PropertyChangeEvent {@link #PROPERTY_CHANGE_ASYNC_STATUS_MSG} on receiving a message
//     @author tobi delbruck
//     * @see #getSupport()
//     */
//    protected class IMUDataThread extends UsbIoReader {
//
//        UsbIoPipe pipe;
//        net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2 monitor;
//        boolean stop = false;
//        byte msg;
//        public static final int STATUS_PRIORITY = Thread.MAX_PRIORITY; // Thread.NORM_PRIORITY+2
//
//        public IMUDataThread(net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2 monitor) {
//            super();
//            this.monitor = monitor;
//
//            int status;
//            status = bind(monitor.getInterfaceNumber(), STATUS_ENDPOINT_ADDRESS, gDevList, GUID);
//            if (status != USBIO_ERR_SUCCESS) {
//                log.warning("error binding to pipe for EP1 for device status: " + UsbIo.errorText(status) + ", not starting AsyncStatusThread");
//                return;
//            }
//            USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
//            pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
//            status = setPipeParameters(pipeParams);
//            if (status != USBIO_ERR_SUCCESS) {
//                log.warning("can't set pipe parameters: " + UsbIo.errorText(status)+": AsyncStatusThread may not function properly");
//            }
//        }
//
//        @Override
//        public void startThread(int MaxIoErrorCount) {
//            allocateBuffers(64,4);
//            super.startThread(MaxIoErrorCount);
//            T.setPriority(STATUS_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
//            T.setName("AsyncStatusThread");
//        }
//
//        @Override
//        public void processData(UsbIoBuf buffer) {
//               if (buffer.BytesTransferred > 0) {
//                    msg = buffer.BufferMem[0];
//
//                    switch (msg) {
//                        case STATUS_MSG_TIMESTAMPS_RESET:
//                            net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2.AEReader rd = getAeReader();
//                            if (rd != null) {
//                                log.info("******** CypressFX2.AsyncStatusThread.run(): timestamps externally reset");
//                                rd.resetTimestamps();
//                            } else {
//                                log.info("Received timestamp external reset message, but monitor is not running");
//                            }
//                            break;
//
//                        case STATUS_MSG_OTHER:
//                        default:
//                                UsbIoBuf newbuf = new UsbIoBuf(64);
//
//                        	// Copy data to new buffer, this one is resubmitted right away.
//                        	System.arraycopy(buffer.BufferMem, 0, newbuf.BufferMem, 0, buffer.BytesTransferred);
//                        	newbuf.BytesTransferred = buffer.BytesTransferred;
//                        	newbuf.Status = buffer.Status;
//
//                        	support.firePropertyChange(PROPERTY_CHANGE_ASYNC_STATUS_MSG, null, newbuf); // tobi - send message to listeners
//                    }
//                } // we getString 0 byte read on stopping device
//        }
//
//        // called before buffer is submitted to driver
//        @Override
//        public void processBuffer(UsbIoBuf Buf) {
//            Buf.NumberOfBytesToTransfer = Buf.Size;
//            Buf.BytesTransferred = 0;
//            Buf.OperationFinished = false;
//        }
//
//        @Override
//        public void bufErrorHandler(UsbIoBuf Buf) {
//            if (Buf.Status != USBIO_ERR_SUCCESS) {
//                // print error
//                // suppress CANCELED because it is caused by ABORT_PIPE
//                if (Buf.Status != USBIO_ERR_CANCELED) {
//                    log.warning("CypressFX2.AsyncStatusThread.bufErrorHandler(): USB buffer error: " + UsbIo.errorText(Buf.Status));
//                }
//                if (Buf.Status == USBIO_ERR_DEVICE_GONE) {
//                    log.warning("CypressFX2.AsyncStatusThread.bufErrorHandler(): device gone, shutting down buffer pool thread");
//                    monitor.close();
//                }
//            }
//        }
//
//        @Override
//        public void onThreadExit() {
//            freeBuffers();
//        }
//
//    }


}
