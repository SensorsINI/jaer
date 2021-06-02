/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial.SpiNNaker;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Interface to SpiNNaker boards via UDP
 * <p>
 * SpiNNaker has an address-event representation that can be interpreted by jAER.
 * <p>
 * @author willconstable
 */
public class SpiNNaker_HardwareInterface implements HardwareInterface, AEMonitorInterface, BiasgenHardwareInterface, Observer/*, CommPortOwnershipListener */ {

    private static Preferences prefs = Preferences.userNodeForPackage(SpiNNaker_HardwareInterface.class);
    public PropertyChangeSupport support = new PropertyChangeSupport(this);
    static Logger log = Logger.getLogger("SpiNNaker");
    private AEChip chip;
    /** Amount by which we need to divide the received timestamp values to get us timestamps. */
    public final int TICK_DIVIDER = 1;
    private AEPacketRaw lastEventsAcquired = new AEPacketRaw();
    public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS
    private int aeBufferSize = prefs.getInt("SpiNNaker.aeBufferSize", AE_BUFFER_SIZE);
    private static boolean isOpen = false; // confuses things
    private static boolean eventAcquisitionEnabled = false;
    private static boolean overrunOccuredFlag = false;
    private byte cHighBitMask = (byte) 0x80;
    private byte cLowerBitsMask = (byte) 0x7F;
    private int eventCounter = 0;
    private int bCalibrated = 0;
    protected String devicName;
    public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
    private AEPacketRawPool aePacketRawPool = new AEPacketRawPool();
    private int lastshortts = 0;
    private final int NUM_BIASES = 12; // number of biases, to renumber biases for bias command
    private boolean DEBUG = false;
    DatagramSocket socket;

    /** Constructs a new SpiNNaker_HardwareInterface using the input and output stream supplied. The other arguments should only have one non-null entry and 
     * are used to properly close the interface.
     * @param socket - or supply this (not both)
     */
    public SpiNNaker_HardwareInterface(DatagramSocket socket) {
        this.socket = socket;
        if (socket == null) {
            throw new Error( "Cannot create SpiNNaker_HardwareInterface with null socket");
        }
    }

    @Override
    public void open() throws HardwareInterfaceException {
        setEventAcquisitionEnabled(true);
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    /** Closes the interface. The interface cannot be reopened without constructing a new instance.
     * 
     */
    @Override
    public void close() {
        
        if (!isOpen) {
            log.info("already closed");
            return;
        }
        try {
         
            setEventAcquisitionEnabled(false);
     
            if (socket != null) {
                socket.close();
                socket = null;
            }
            isOpen = false;
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
        
    }

    @Override
    public String getTypeName() {
        return "SpiNNaker";
    }

    @Override
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }

    @Override
    final public int getTimestampTickUs() {
        return 1;
    }
    private int estimatedEventRate = 0;

    @Override
    public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    @Override
    public int getMaxCapacity() {
        //todo What is this?
        return 100000;
    }

    @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            startAEReader();
        } else {
            stopAEReader();
        }
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return eventAcquisitionEnabled;
    }

    @Override
    public int getAEBufferSize() {
        return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
    }

    @Override
    public void setAEBufferSize(int size) {
        if (size < 1000 || size > 1000000) {
            log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.aeBufferSize = size;
        prefs.putInt("SpiNNaker.aeBufferSize", aeBufferSize);
    }

    @Override
    public boolean overrunOccurred() {
        return overrunOccuredFlag;
    }

    /** Resets the timestamp unwrap value, and resets the AEPacketRawPool.
     */
    @Override
    synchronized public void resetTimestamps() {
        wrapAdd = 0; //TODO call TDS to reset timestamps
        aePacketRawPool.reset();
    }

    /** returns last events from {@link #acquireAvailableEventsFromDriver}
     *@return the event packet
     */
    @Override
    public AEPacketRaw getEvents() {
        return this.lastEventsAcquired;
    }

    /** Returns the number of events acquired by the last call to {@link
     * #acquireAvailableEventsFromDriver }
     * @return number of events acquired
     */
    @Override
    public int getNumEventsAcquired() {
        return lastEventsAcquired.getNumEvents();
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!eventAcquisitionEnabled) {
            setEventAcquisitionEnabled(true);
        }
        int nEvents;
        aePacketRawPool.swap();
        lastEventsAcquired = aePacketRawPool.readBuffer();
        nEvents = lastEventsAcquired.getNumEvents();
        eventCounter = 0;
        computeEstimatedEventRate(lastEventsAcquired);

        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners  
        }

        return lastEventsAcquired;
    }

    /** computes the estimated event rate for a packet of events */
    void computeEstimatedEventRate(AEPacketRaw events) {
        if (events == null || events.getNumEvents() < 2) {
            estimatedEventRate = 0;
        } else {
            int[] ts = events.getTimestamps();
            int n = events.getNumEvents();
            int dt = ts[n - 1] - ts[0];
            estimatedEventRate = (int) (1e6f * (float) n / (float) dt);
        }
    }

    @Override
    public void setPowerDown(boolean powerDown) {
        log.warning("Power down not supported by SpiNNaker devices.");
    }

    
    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
       log.warning("Send Configuration not supported by SpiNNaker devices.");
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) {
        log.warning("Flash configuration not supported by SpiNNaker devices.");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        throw new UnsupportedOperationException("Not supported  or used for this device.");// TODO use this to send all biases at once?  yes, this is how the on-chip bias generator works
    }

    @Override
    public String toString() {
        return "SpiNNaker_HardwareInterface with socket=" + socket;
    }
    
    protected boolean running = true;
    final int WRAP = 0x10000; // amount of timestamp wrap to add for each overflow of 1 bit timestamps
    final int WRAP_START = 0; //(int)(0xFFFFFFFFL&(2147483648L-0x400000L)); // set high to test big wrap 1<<30;
    volatile int wrapAdd = WRAP_START; //0;
    protected AEReader aeReader = null;

    public AEReader getAeReader() {
        return aeReader;
    }

    public void setAeReader(AEReader aeReader) {
        this.aeReader = aeReader;
    }

    public void startAEReader() {
        setAeReader(new AEReader(this));
        log.info("Start AE reader...");
        getAeReader().start();
        eventAcquisitionEnabled = true;
    }

    public void stopAEReader() {
        if (getAeReader() != null) {
            // close device
            getAeReader().finish();
        }
    }

    /** Called when notifyObservers is called in Observable we are watching, e.g. biasgen */
    @Override
    synchronized public void update(Observable o, Object arg) {
        //TODO should this log a warning?
    } 
    
    public class AEReader extends Thread implements Runnable {

        private byte[] buffer = null;
        SpiNNaker_HardwareInterface monitor;

        public AEReader(SpiNNaker_HardwareInterface monitor) {
            this.monitor = monitor;
            setName("SpiNNaker_AEReader");
            /* This is a list of all this interface's endpoints. */
            allocateAEBuffers();

            buffer = new byte[8192 * 4];//UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];
        }

        @Override
        @SuppressWarnings("SleepWhileInLoop")
        public void run() {

            int offset = 0;
            int length = 0;
            int len = 0;

//            int count=0;
            while (running) {
                byte[] receiveData = new byte[1024];
                DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
                try {
                    monitor.socket.receive(packet);
                } catch (IOException ex) {
                    Logger.getLogger(SpiNNaker_HardwareInterface.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                System.out.println("Recd packet of length " + receiveData.length);
                translateEvents_code(receiveData, receiveData.length);
                /*
                int nDump = 0;

                if (len > 3) { // TODO what if len<=3?  e.g. for part of an event that was sent
//                    try {
//                        length = inputStream.read(buffer, 0, len - (len % 4));
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }

                    translateEvents_code(buffer, length);

                    // what is this 'calibration'? TODO
                    if (bCalibrated == 0) {
                        int diff = 0;
                        if (length > 100) {
                            for (int i = 0; i <= 3; i++) {
                                //offset=i;
                                diff = 0;
                                for (int m = 0; m < 10; m++) {
                                    diff += (int) (buffer[4 * (m + 1) + i] - buffer[4 * m + i]) * (int) (buffer[4 * (m + 1) + i] - buffer[4 * m + i]);
                                }
                                //System.out.println(diff);
                                if (diff < 20) { //10
                                    offset = i;
                                    //break;
                                }
                            }
                        }

                        //System.out.println("length: " + length + " tail: " + nTail + " offset: " + offset);

                        switch (offset) {
                            case 0:
                                nDump = 2;
                                break;
                            case 1:
                                nDump = 3;
                                break;
                            case 2:
                                nDump = 0;
                                break;
                            case 3:
                                nDump = 1;
                                break;
                            default:
                                log.warning("offset value should not be " + offset);
                        }

                        if (nDump != 0) {
                            long length1 = 0;
                            long len1 = 0;

                                while (length1 != nDump) {
                                    //len = inputStream.read(buffer, length1, nDump - length1);
                                    //len1 = monitor.inputStream.skip(nDump - length1);
                                    length1 = length1 + len1;
                                }
                             
                            log.info("Dumped: " + length1 + " bytes / " + nDump);
                        } else {
                            bCalibrated = 1;
                            log.info("Calibrated");
                        }
                    }

                }

                if (timestampsReset) {
                    log.info("timestampsReset: flushing aePacketRawPool buffers");
                    aePacketRawPool.reset();
                    timestampsReset = false;
                }
                * */
            }
            log.info("reader thread ending");
        }

        /**
         * Stop/abort listening for data events.
         */
        synchronized public void finish() {
            running = false;
            interrupt();
        }

        synchronized public void resetTimestamps() {
            log.info(SpiNNaker_HardwareInterface.this + ": wrapAdd=" + wrapAdd + ", zeroing it");
            wrapAdd = WRAP_START;
            timestampsReset = true; // will inform reader thread that timestamps are reset

        }
        protected boolean running = true;
        volatile boolean timestampsReset = false; // used to tell processData that another thread has reset timestamps
    }
    private long lastWrapTime = 0;

    /** Writes events from buffer to AEPacketRaw in buffer pool.
     * 
     * @param b data sent from camera
     * @param bytesSent number of bytes in buffer that are valid
     */
    protected void translateEvents_code(byte[] b, int bytesSent) {
        synchronized (aePacketRawPool) {

            AEPacketRaw buffer = aePacketRawPool.writeBuffer();
            int shortts;
            //TODO implement spinnaker translation

            int[] addresses = buffer.getAddresses();
            int[] timestamps = buffer.getTimestamps();

            // write the start of the packet
            buffer.lastCaptureIndex = eventCounter;
            log.info("entering translateEvents_code with "+eventCounter+" events");
            System.out.println("Got data packet word " + b[54]);

            StringBuilder sb = null;
            if (DEBUG) {
                sb = new StringBuilder(String.format("%d events: Timestamp deltas are ", bytesSent / 4));
            }
                /* event polarity is encoded in the msb of the second byte. i.e.
                Byte0, bit 7:         always zero
                Byte0, bits 6-0:   event address y
                Byte1, bit 7:         event polarity
                Byte1, bits 6-0:   event address x
                Bytes2+3:             16 bit timestamp, MSB first
                 */
            int addrOffset = 54;
            int y_ = 0x01;
            //int y_ = (0xff & b[addrOffset]); // & with 0xff to prevent sign bit from extending to int (treat byte as unsigned)
            int x_ = 0x81;
            //int x_ = (0xff & b[addrOffset + 1]);
            int c_ = 0;
            //int c_ = (0xff & b[addrOffset + 2]);
            int d_ = 0;
            //int d_ = (0xff & b[addrOffset + 3]);

            addresses[eventCounter] = (int) ((x_ & cHighBitMask) >> 7 | ((y_ & cLowerBitsMask) << 8) | ((x_ & cLowerBitsMask) << 1)) & 0x7FFF;

            timestamps[eventCounter] = 11234;

            eventCounter++;

            if (DEBUG) {
                log.info(sb.toString());
            }
            buffer.setNumEvents(eventCounter);

            // write capture size
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;

        } // sync on aePacketRawPool
    

    }

    void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    /** Double buffer for writing and reading events. */
    private class AEPacketRawPool {

        int capacity;
        AEPacketRaw[] buffers;
        AEPacketRaw lastBufferReference;
        volatile int readBuffer = 0, writeBuffer = 1; // this buffer is the one currently being read from

        AEPacketRawPool() {
            allocateMemory();
            reset();
        }

        /** Swaps read and write buffers in preparation for reading last captured buffer of events.
         * 
         */
        synchronized final void swap() {
            lastBufferReference = buffers[readBuffer];
            if (readBuffer == 0) {
                readBuffer = 1;
                writeBuffer = 0;
            } else {
                readBuffer = 0;
                writeBuffer = 1;
            }
            writeBuffer().clear();
            writeBuffer().overrunOccuredFlag = false; // mark new write buffer clean, no overrun happened yet. writer sets this if it happens
//            log.info("swapped buffers - new read buffer has "+readBuffer().getNumEvents()+" events");
        }

        /** Returns the current read buffer.
         * @return buffer to read from */
        synchronized final AEPacketRaw readBuffer() {
            return buffers[readBuffer];
        }

        /** Returns the current writing buffer. Does not swap buffers.
         * @return buffer to write to 
        @see #swap
         */
        synchronized final AEPacketRaw writeBuffer() {
            return buffers[writeBuffer];
        }

        /** Set the current buffer to be the first one and clear the write buffer */
        synchronized final void reset() {
            readBuffer = 0;
            writeBuffer = 1;
            buffers[writeBuffer].clear(); // new events go into this buffer which should be empty

            buffers[readBuffer].clear();  // clear read buffer in case this buffer was reset by resetTimestamps
//            log.info("buffers reset");

        }

        // allocates AEPacketRaw each with capacity AE_BUFFER_SIZE
        private void allocateMemory() {
            buffers = new AEPacketRaw[2];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = new AEPacketRaw();
                buffers[i].ensureCapacity(getAEBufferSize()); // preallocate this memory for capture thread and to try to make it contiguous

            }
        }
    }
}