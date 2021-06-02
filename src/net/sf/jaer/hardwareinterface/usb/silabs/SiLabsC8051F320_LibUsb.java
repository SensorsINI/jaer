/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb.silabs;

import static java.lang.Thread.sleep;
import static net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_DVS128.MAX_BYTES_PER_BIAS;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

import org.usb4java.BufferUtils;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

/**
 * LibUsb linux driver for the SiLabs C8051F320.
 * This driver was not written at the uni zurich but at bielefeld university
 * without background knowledge about the product family.
 * Most of the code is copy-pasted from the cypress-libusb and the dvs128-usbio drivers.
 * The driver package is more or less written explicitly for the Paer Board,
 * So if you want to further extend this for other C8051F320 based
 * boards and need a different behaviour for some methods, just move them to the
 * SiLabsC8051F320_LibUsb_PAER class and make the one in this class abstract.
 * @author sweber
 */
public abstract class SiLabsC8051F320_LibUsb implements
        BiasgenHardwareInterface, AEMonitorInterface, ReaderBufferControl, USBInterface {

    protected Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    protected Device retina;
    protected DeviceHandle retinahandle;
    protected AEChip chip;
    protected DeviceDescriptor deviceDescriptor;
    protected boolean isOpened = false;
    protected boolean eventAcquisitionEnabled = false;
    protected static final Logger log = Logger.getLogger("SilabsC8051F320_LibUsb");
    public PropertyChangeSupport support = new PropertyChangeSupport(this);
    private int estimatedEventRate = 0;
    protected SilabsAEReader aeReader;
    protected AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    public static final int AE_BUFFER_SIZE = 100000; // taken from USB_IO version
    protected int buffersize = this.prefs.getInt("Silabs.aeBufferSize", this.AE_BUFFER_SIZE);
    public final short TICK_US = 1;
    short TICK_US_BOARD = 10;
    protected int eventCounter;
    private int realTimeEventCounterStart;
    private String[] stringDescriptors = new String[3];

    private final byte VENDOR_REQUEST_RESETTIMESTAMPS = 0x6;
    private final byte VENDOR_REQUEST_TRANSFER_ENABLED = 0x5;
    private final byte VENDOR_REQUEST_SETPOWER = 0x2;
    private final byte VENDOR_REQUEST_SETBIASES = 0x1;
    private final byte VENDOR_REQUEST_FLASHBIASES = 0x4;
    private int fifoSize;

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }

        // make sure event acquisition is running
        if (!eventAcquisitionEnabled) {
            setEventAcquisitionEnabled(true);
        }
        int nEvents;

		// getString the 'active' buffer for events (the one that has just been written by the hardware thread)
        // synchronized(aePacketRawPool){ // synchronize on aeReader so that we don't try to access the events at the
        // same time
        aePacketRawPool.swap();
        AEPacketRaw lastEventsAcquired = new AEPacketRaw();
        lastEventsAcquired = aePacketRawPool.readBuffer();
		// log.info(this+" acquired "+lastEventsAcquired);
        // addresses=events.getAddresses();
        // timestamps=events.getTimestamps();
        nEvents = lastEventsAcquired.getNumEvents();
        eventCounter = 0;
        realTimeEventCounterStart = 0;
        PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
        computeEstimatedEventRate(lastEventsAcquired);
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners
            // }
        }
        return lastEventsAcquired;
    }

    @Override
    public int getNumEventsAcquired() {
        return this.eventCounter;
    }

    @Override
    public AEPacketRaw getEvents() {
        throw new UnsupportedOperationException("Seems Unused.");
    }

    @Override
    public void resetTimestamps() {
        SiLabsC8051F320_LibUsb.log.info(this + ".resetTimestamps(): zeroing timestamps");

        // send vendor request for device to reset timestamps
        if (retinahandle == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }

        try {

            Byte t = 0x1;
            sendVendorRequest(this.VENDOR_REQUEST_RESETTIMESTAMPS, t);
        } catch (final HardwareInterfaceException e) {
            SiLabsC8051F320_LibUsb.log.warning("Silabs.resetTimestamps: couldn't send vendor request to reset timestamps");
        }

        if (this.aeReader != null) {
            this.aeReader.resetTimestamps(); // reset wrap counter and flush buffers
        } else {
            SiLabsC8051F320_LibUsb.log.warning("Silabs.resetTimestamps(): reader not yet started, can't reset timestamps");
        }
    }

    @Override
    public boolean overrunOccurred() {
        return aePacketRawPool.readBuffer().overrunOccuredFlag;
    }

    @Override
    public int getAEBufferSize() {
        return this.buffersize;
    }

    @Override
    public void setAEBufferSize(int size) {
        if ((size < 1000) || (size > 1000000)) {
            SiLabsC8051F320_LibUsb.log.warning("ignoring unreasonable aeBufferSize of " + size
                    + ", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.buffersize = size;
        this.prefs.putInt("Silabs.aeBufferSize", buffersize);
        allocateAEBuffers();
    }

    /**
     * Allocates internal memory for transferring data from reader to consumer,
     * e.g. rendering.
     */
    protected void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        setInEndpointEnabled(enable);
        this.setAEReaderEnabled(enable);

    }

    public void setInEndpointEnabled(final boolean inEndpointEnabled) throws HardwareInterfaceException {
        SiLabsC8051F320_LibUsb.log.info("Setting IN endpoint enabled=" + inEndpointEnabled);
        if (retinahandle == null) {
            SiLabsC8051F320_LibUsb.log.warning("PAER.enableINEndpoint(): null USBIO device");
            return;
        }
        Byte t = 0x1;
        Byte f = 0x0;

        sendVendorRequest(this.VENDOR_REQUEST_TRANSFER_ENABLED, inEndpointEnabled ? t : f);
        //this.sendBoolean(VENDOR_REQUEST_TRANSFER_ENABLED, inEndpointEnabled);
        eventAcquisitionEnabled = inEndpointEnabled;
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return this.eventAcquisitionEnabled;
    }

    @Override
    public void addAEListener(final AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public abstract void setAEReaderEnabled(boolean enabled);

    @Override
    public void removeAEListener(final AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    @Override
    public int getTimestampTickUs() {
        return this.TICK_US;
    }

    @Override
    public void setChip(final AEChip chip) {
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }

    @Override
    abstract public String getTypeName();

    @Override
    synchronized public void close() {
        if (!isOpen()) {
            return;
        }

        this.setAEReaderEnabled(false);

        /*if (asyncStatusThread != null) {
         asyncStatusThread.stopThread();
         }*/
        try {
            this.releaseDevice();
        } catch (HardwareInterfaceException ex) {
            SiLabsC8051F320_LibUsb.log.warning("could not release device");
        }

        LibUsb.close(retinahandle);

        retinahandle = null;

        deviceDescriptor = null;

        aePacketRawPool.reset();

        isOpened = false;
    }

    @Override
    synchronized public void open() throws HardwareInterfaceException {
        if (isOpen()) {
            return;
        }

        int status;

        // Open device.
        if (retinahandle == null) {
            retinahandle = new DeviceHandle();
            status = LibUsb.open(retina, retinahandle);
            if (status != LibUsb.SUCCESS) {
                if (status == LibUsb.ERROR_NO_DEVICE) {

                }
                throw new HardwareInterfaceException("open(): failed to open device: " + LibUsb.errorName(status));

            }
        }

        // Check for blank devices (must first get device descriptor).
        if (deviceDescriptor == null) {
            deviceDescriptor = new DeviceDescriptor();
            status = LibUsb.getDeviceDescriptor(retina, deviceDescriptor);
        }
        this.acquireDevice();
        isOpened = true;

        SiLabsC8051F320_LibUsb.log.info("open(): device opened");

        if (LibUsb.getDeviceSpeed(retina) != LibUsb.SPEED_FULL) {
            SiLabsC8051F320_LibUsb.log
                    .warning("Device is not operating at USB 2.0 High Speed, performance will be limited to about 300 keps");
        }
        try {
            for (int i = 0; i < this.stringDescriptors.length; i++) {
                byte b = (byte) (i + 1);
                this.stringDescriptors[i] = LibUsb.getStringDescriptor(retinahandle, b);
            }
        } catch (Exception e) {
            SiLabsC8051F320_LibUsb.log.warning("Could not get all Stringdescriptors");
        }

        // start the thread that listens for device status information (e.g. timestamp reset)
        //asyncStatusThread = new AsyncStatusThread(this);
        //asyncStatusThread.startThread();
    }

    /**
     * acquire a device for exclusive use, other processes can't open the device
     * anymore used for example for continuous sequencing in matlab
     */
    public void acquireDevice() throws HardwareInterfaceException {
        SiLabsC8051F320_LibUsb.log.log(Level.INFO, "{0} acquiring device for exclusive access", this);

        final int status = LibUsb.claimInterface(retinahandle, 0);
        if (status != LibUsb.SUCCESS) {
            if (status == LibUsb.ERROR_BUSY) {
                try {
                    sleep(3000);
                } catch (InterruptedException ex) {

                }
            }
            throw new HardwareInterfaceException("Unable to acquire device for exclusive use: "
                    + LibUsb.errorName(status));
        }
    }

    /**
     * release the device from exclusive use
     */
    public void releaseDevice() throws HardwareInterfaceException {
        SiLabsC8051F320_LibUsb.log.log(Level.INFO, "{0} releasing device", this);

        final int status = LibUsb.releaseInterface(retinahandle, 0);
        if (status != LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("Unable to release device from exclusive use: "
                    + LibUsb.errorName(status));
        }
    }

    @Override
    public boolean isOpen() {
        return this.isOpened;
    }

    @Override
    public int getFifoSize() {
        if (this.aeReader != null) {
            return this.aeReader.getFifoSize();
        }
        return 0;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.aeReader.setFifoSize(fifoSize);
    }

    @Override
    public int getNumBuffers() {
        if (this.aeReader != null) {
            return this.aeReader.getNumBuffers();
        }
        return 0;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        if (aeReader == null) {
            return;
        }

        aeReader.setNumBuffers(numBuffers);
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return this.support;
    }

    @Override
    public String[] getStringDescriptors() {
        if (stringDescriptors == null) {
            SiLabsC8051F320_LibUsb.log.warning("USBAEMonitor: getStringDescriptors called but device has not been opened");

            final String[] s = new String[stringDescriptors.length];

            for (int i = 0; i < stringDescriptors.length; i++) {
                s[i] = "";
            }

            return s;
        } else {
            final String[] s = stringDescriptors;
            return s;
        }
    }

    public int[] getVIDPID() {
        if (deviceDescriptor == null) {
            SiLabsC8051F320_LibUsb.log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return new int[2];
        }
        final int[] n = new int[2];
        n[0] = deviceDescriptor.idVendor();
        n[1] = deviceDescriptor.iProduct();
        return n;
    }

    @Override
    public short getVID_THESYCON_FX2_CPLD() {
        if (deviceDescriptor == null) {
            SiLabsC8051F320_LibUsb.log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        // int[] n=new int[2]; n is never used
        return deviceDescriptor.idVendor();
    }

    @Override
    public short getPID() {
        if (deviceDescriptor == null) {
            SiLabsC8051F320_LibUsb.log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        return deviceDescriptor.idProduct();
    }

    /**
     * @return bcdDevice (the binary coded decimel device version
     */
    @Override
    public short getDID() { // this is not part of USB spec in device descriptor.
        return deviceDescriptor.bcdDevice();
    }

    /**
     * Sends a vendor request without any data packet but with request, value
     * and index. This is a blocking method.
     *
     * @param request the vendor request byte, identifies the request on the
     * device
     * @param value the value of the request (bValue USB field)
     */
    synchronized public void sendVendorRequest(final byte request, final Byte value)
            throws HardwareInterfaceException {
        sendVendorRequest(request, value, (ByteBuffer) null, (IntBuffer) null);
    }

    synchronized public void sendVendorRequest(byte[] dataBuffer)
            throws HardwareInterfaceException {
        ByteBuffer toSend = BufferUtils.allocateByteBuffer(dataBuffer.length);
        for (byte c : dataBuffer) {
            toSend.put(c);
        }
        sendVendorRequest((byte) 0x0, (byte) 0x0, toSend, (IntBuffer) null);
    }

    synchronized public void sendVendorRequest(final byte request, final Byte value,
            ByteBuffer dataBuffer, IntBuffer intBuffer) throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }

        if (dataBuffer == null) {
            dataBuffer = BufferUtils.allocateByteBuffer(2);
            dataBuffer.put(request);
            dataBuffer.put(value);
        }
        if (intBuffer == null) {
            intBuffer = BufferUtils.allocateIntBuffer();
        }
        Byte endpoint = 0x02;

        final byte bmRequestType = (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE);
        final int status = LibUsb.bulkTransfer(retinahandle, endpoint, dataBuffer, intBuffer, 0);
        //controlTransfer(retinahandle, bmRequestType, request, value, index, dataBuffer, 0);
        if (status < LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("Unable to send vendor OUT request " + String.format("0x%x", request)
                    + ": " + LibUsb.errorName(status));
        }

        //if (status != dataBuffer.capacity()) {
        //    throw new HardwareInterfaceException("Wrong number of bytes transferred, wanted: " + dataBuffer.capacity()
        //            + ", got: " + status);
        //}
    }

    public AEPacketRawPool getaePacketRawPool() {
        return this.aePacketRawPool;
    }

    void computeEstimatedEventRate(final AEPacketRaw events) {
        if ((events == null) || (events.getNumEvents() < 2)) {
            estimatedEventRate = 0;
        } else {
            final int[] ts = events.getTimestamps();
            final int n = events.getNumEvents();
            final int dt = ts[n - 1] - ts[0];
            estimatedEventRate = (int) ((1e6f * n) / dt);
        }
    }

    //Biasgen Methods
    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        byte t = powerDown ? (byte) 0x1 : (byte) 0x0;
        sendVendorRequest(this.VENDOR_REQUEST_SETPOWER, t);
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        byte[] dataBytes;
        // construct the byte array to send to hardware interface depending on wheter the PotArray is actually an IPotArray
        if (biasgen.getPotArray() instanceof net.sf.jaer.biasgen.IPotArray) {
            dataBytes = getBiasBytes(biasgen);

        } else {
            // asssume we only have VPots
            VPot p = null;

            ArrayList<Pot> pots = biasgen.getPotArray().getPots();

            dataBytes = new byte[pots.size() * 3];
            int i = 0;
            for (Pot pot : pots) {
                p = (VPot) pot;
                dataBytes[i] = (byte) p.getChannel(); //address
                dataBytes[i + 1] = (byte) ((p.getBitValue() & 0x0F00) >> 8);  //value msb
                dataBytes[i + 2] = (byte) (p.getBitValue() & 0x00FF); //value lsb
                i += 3;
            }

        }

        byte[] allBytes = new byte[2 + dataBytes.length]; // need header and length
        allBytes[0] = VENDOR_REQUEST_SETBIASES;
        allBytes[1] = (byte) (0xff & dataBytes.length);  // avoid signed byte by masking
        System.arraycopy(dataBytes, 0, allBytes, 2, dataBytes.length);
        //send allBytes
        sendVendorRequest(allBytes);
    }

    private byte[] getBiasBytes(final Biasgen biasgen) {
        IPotArray iPotArray = (IPotArray) biasgen.getPotArray();
        byte[] bytes = new byte[iPotArray.getNumPots() * MAX_BYTES_PER_BIAS]; // oversize this for now, later we copy to actual array
        int byteIndex = 0;
        byte[] toSend;
        for (IPot iPot : iPotArray) {
            for (int k = iPot.getNumBytes() - 1; k >= 0; k--) { // for k=2..0
                bytes[byteIndex++] = (byte) ((iPot.getBitValue() >>> (k * 8)) & 0xff);
            }
        }
        toSend = new byte[byteIndex];
        System.arraycopy(bytes, 0, toSend, 0, byteIndex);

        return toSend;
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen.getPotArray() == null) {
            log.info("iPotArray=null, no biases to send");
            return; // may not have been constructed yet.
        }
        if (!isOpen()) {
            log.info("Device not Open during attempt to flash biases");
            open();
            if (!isOpen()) {
                log.warning("Device could not be opened to flash biases.");
                return;
            }
        }
        byte[] dataBytes = getBiasBytes(biasgen);
        byte[] toSend = new byte[2 + dataBytes.length];
        toSend[0] = VENDOR_REQUEST_FLASHBIASES;
        toSend[1] = (byte) (0xff & dataBytes.length);  // avoid signed byte by masking
        System.arraycopy(dataBytes, 0, toSend, 2, dataBytes.length);
        sendVendorRequest(toSend);
    }

    /**
     * This implementation treats the biasgen as a simple array of IPots each of
     * which provides bytes to send. Subclasses can override
     * formatConfigurationBytes in case they have additional information to
     * format. If the biasgen potArray is an IPotArray, the bytes are formatted
     * and sent. Otherwise nothing is sent.
     *
     * @param biasgen the source of configuration information.
     * @return the bytes to send
     */
    @Override
	public byte[] formatConfigurationBytes(Biasgen biasgen) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff
        PotArray potArray = biasgen.getPotArray();

        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes
        if (potArray instanceof IPotArray) {
            IPotArray ipots = (IPotArray) potArray;
            byte[] bytes = new byte[potArray.getNumPots() * MAX_BYTES_PER_BIAS];
            int byteIndex = 0;

            Iterator i = ipots.getShiftRegisterIterator();
            while (i.hasNext()) {
                // for each bias starting with the first one (the one closest to the ** END ** of the shift register
                // we get the binary representation in byte[] form and from MSB ro LSB stuff these values into the byte array
                IPot iPot = (IPot) i.next();
                byte[] thisBiasBytes = iPot.getBinaryRepresentation();
                System.arraycopy(thisBiasBytes, 0, bytes, byteIndex, thisBiasBytes.length);
                byteIndex += thisBiasBytes.length;
            }
            byte[] toSend = new byte[byteIndex];
            System.arraycopy(bytes, 0, toSend, 0, byteIndex);
            return toSend;
        }
        return null;
    }
}
