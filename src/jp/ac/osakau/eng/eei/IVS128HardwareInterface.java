/*
 * Created May 2011 at CapoCaccia workshop by Tobi Delbruck and Hiro Okuno.
 * 
 */
package jp.ac.osakau.eng.eei;

import de.thesycon.usbio.UsbIo;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.*;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoPipe;
import de.thesycon.usbio.structs.USBIO_CONFIGURATION_INFO;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;

/**
 * The hardware interface for the IVS128. The VID is 0x04b4 and the PID is 0x1004.
 *
 * @author tobi/hiro
 */
public class IVS128HardwareInterface extends CypressFX2 {

    public static final int VID = 0x04b4;
    public static final int PID = 0x1004;
    UsbIoPipe controlPipe = null;

    /** Creates a new instance of CypressFX2Biasgen */
    public IVS128HardwareInterface(int devNumber) {
        super(devNumber);
    }

    /* This method does the hard work of opening the device and making sure everything is OK.
     *<p>
     * Opening the device after it has already been opened has no effect.
     *
     * @see #close
     *@throws HardwareInterfaceException if there is a problem. Diagnostics are printed to stderr.    
     * */
    @Override
    public void open() throws HardwareInterfaceException {
        //device has already been UsbIo Opened by now, in factory

        // opens the USBIOInterface device, configures it, but does not start the reader thread. The reader is started by AEViewer.viewLoop

        if (isOpened) {
//            log.warning("CypressFX2.openUsbIo(): already opened interface and setup device");
            return;
        }

        int status;

        gUsbIo = new UsbIo();
        gDevList = UsbIo.createDeviceList(GUID);
        status = gUsbIo.open(getInterfaceNumber(), gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            isOpened = false;
            throw new HardwareInterfaceException("CypressFX2.openUsbIo(): can't open USB device: " + UsbIo.errorText(status));
        }

        acquireDevice();

        // getString device descriptor (possibly before firmware download, when still bare cypress device or running off EEPROM firmware)
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("CypressFX2.openUsbIo(): getDeviceDescriptor: " + UsbIo.errorText(status));
        } else {
//            log.info("getDeviceDescriptor: Vendor ID (VID) "
//                    + HexString.toString((short)deviceDescriptor.idVendor)
//                    + " Product ID (PID) " + HexString.toString((short)deviceDescriptor.idProduct));
        }


        try {
            unconfigureDevice(); // in case it was left configured from a terminated process
        } catch (HardwareInterfaceException e) {
            log.warning("CypressFX2.open(): can't unconfigure,will try simulated disconnect");
            int cycleStatus = gUsbIo.cyclePort();
            if (cycleStatus != USBIO_ERR_SUCCESS) {
                throw new HardwareInterfaceException("Error cycling port: " + UsbIo.errorText(cycleStatus));
            }
            throw new HardwareInterfaceException("couldn't unconfigure device");
        }

        // set configuration -- must do this BEFORE downloading firmware!
        if (deviceDescriptor.bNumConfigurations != 1) {
            throw new HardwareInterfaceException("number of configurations=" + deviceDescriptor.bNumConfigurations + " which is not 1 like it should be");
        }

        USBIO_SET_CONFIGURATION Conf = new USBIO_SET_CONFIGURATION();
        Conf.ConfigurationIndex = CONFIG_INDEX;
        Conf.NbOfInterfaces = CONFIG_NB_OF_INTERFACES;
        Conf.InterfaceList[0].InterfaceIndex = CONFIG_INTERFACE;
        Conf.InterfaceList[0].AlternateSettingIndex = CONFIG_ALT_SETTING;
        Conf.InterfaceList[0].MaximumTransferSize = CONFIG_TRAN_SIZE;
        status = gUsbIo.setConfiguration(Conf);
        if (status != USBIO_ERR_SUCCESS) {
//            gUsbIo.destroyDeviceList(gDevList);
            //   if (status !=0xE0001005)
            throw new HardwareInterfaceException("setting configuration: " + UsbIo.errorText(status));
        }

        //        try{Thread.currentThread().sleep(100);} catch(InterruptedException e){}; // pause for renumeration

        populateDescriptors(gUsbIo);

        if (!gUsbIo.isOperatingAtHighSpeed()) {
            log.warning("Device is not operating at USB 2.0 High Speed, performance will be limited to about 300 keps");
        }

        // getString pipe information and extract the FIFO size
        USBIO_CONFIGURATION_INFO ConfigurationInfo = new USBIO_CONFIGURATION_INFO();
        status = gUsbIo.getConfigurationInfo(ConfigurationInfo);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getConfigurationInfo: " + UsbIo.errorText(status));
        }

        if (ConfigurationInfo.NbOfPipes == 0) {
//            gUsbIo.cyclePort();
            log.warning("no pipes to bind too - probably blank device");
//            throw new HardwareInterfaceException("CypressFX2.openUsbIo(): didn't find any pipes to bind to");
        }

        controlPipe = new UsbIoPipe();

        status = controlPipe.bind(getInterfaceNumber(), (byte) 0x02, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("error binding to pipe for EP2 for controlling device: " + UsbIo.errorText(status));
            throw new HardwareInterfaceException(UsbIo.errorText(status));
        }
        USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = controlPipe.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("can't set pipe parameters: " + UsbIo.errorText(status));
            throw new HardwareInterfaceException(UsbIo.errorText(status));
        }
        USBIO_DATA_BUFFER buf = new USBIO_DATA_BUFFER(64);
        byte[] b=buf.Buffer();
        b[0]=0;
        buf.setNumberOfBytesToTransfer(1);
        controlPipe.writeSync(buf); // starts transfers to host
        status = controlPipe.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("can't write to pipe: " + UsbIo.errorText(status));
            throw new HardwareInterfaceException(UsbIo.errorText(status));
        }
        controlPipe.unbind();
        isOpened = true;

    }

    @Override
    protected synchronized void disableINEndpoint() {
    }

    @Override
    protected synchronized void enableINEndpoint() throws HardwareInterfaceException {
    }

    @Override
    public void setSerialNumber(String name) throws HardwareInterfaceException {
        throw new UnsupportedOperationException();
    }

    synchronized public void writeEEPROM(int addr, byte[] bytes) throws HardwareInterfaceException {
        throw new UnsupportedOperationException();
    }

    synchronized protected void eraseEEPROM() throws HardwareInterfaceException {
        throw new UnsupportedOperationException();
    }

    synchronized protected byte[] readEEPROM(int addr, int length) throws HardwareInterfaceException {
        throw new UnsupportedOperationException();
    }

    synchronized public void writeVIDPIDDID(short VID, short PID, short DID) throws HardwareInterfaceException {
        throw new UnsupportedOperationException();
    }

    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new IVS128AEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }
    private int frameCounter = 0;

    @Override
    synchronized public void resetTimestamps() {
        frameCounter = 0;

    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class IVS128AEReader extends CypressFX2.AEReader {

        private int US_PER_FRAME = 5000;

        /** Constructs a new reader for the interface.
         *
         * @param cypress The CypressFX2 interface.
         * @throws HardwareInterfaceException on any hardware error.
         */
        public IVS128AEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        /** Does the translation, timestamp reset.
         * Event addresses are unpacked into 3 bytes of 32 bit raw address.
         * Byte 0 (the LSB) has the cell type.
         * Byte 1 has the x address
         * Byte 2 has the y address.
         * @param b the raw buffer
         */
        @Override
        protected void translateEvents(UsbIoBuf b) {

            synchronized (aePacketRawPool) {
                AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                byte[] aeBuffer = b.BufferMem;
                int bytesSent = b.BytesTransferred;
//                if(bytesSent%512!=0){
//                    log.warning("warning: "+bytesSent+" bytes sent, which is not 512 bytes which should have been sent");
//                    return;
//                }

                int[] addresses = buffer.getAddresses();
                int[] timestamps = buffer.getTimestamps();

                // write the start of the packet
                buffer.lastCaptureIndex = eventCounter;
                //  each pixel sent uses 4 bits to mark transient on, transient off, sustained on, sustained off as follows
                // 
                int pos = aeBuffer[0] & 0xff; // position of 33 in frame
                int xxx = aeBuffer[1] & 0xff;
                int frameNumber = (aeBuffer[2] & 0xff); // 0-255 frame counter
                int numDataSent = (aeBuffer[3] & 0xff); // 

                for (int i = 4; i < bytesSent; i++) {

                    if ((eventCounter > aeBufferSize - 1) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events
                        buffer.overrunOccuredFlag = true;
                    } else {
                        timestamps[eventCounter] = (int) (US_PER_FRAME * frameCounter); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        int celltype = (aeBuffer[i] & 0xFF) >> 4; // cell type in upper nibble of byte
                        if (celltype == 0) {
                            continue;  // no event if no bits are set.
                        }                        // have event, write the x,y addresses and cell type into different bytes of the raw address.
                        // each packet has 508 data
                        int x = i % 128; // TODO 
                        int y = i / 128;
                        int rawaddr = (y << 16 | x << 8 | celltype);
                        addresses[eventCounter] = rawaddr;
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    }
                } // end for

                // write capture size
                buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                frameCounter++;
            } // sync on aePacketRawPool

        }
    }

    /** Checks for blank cypress VID/PID. 
     * Device deviceDescriptor must be populated before calling this method.
     * 
     * @return true if blank
     */
    protected boolean isBlankDevice() {
        return false;// TODO need to really check this
    }

}
