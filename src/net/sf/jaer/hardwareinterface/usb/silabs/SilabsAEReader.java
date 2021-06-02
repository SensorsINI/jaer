/*
 * SilabsAEReader.java
 *
 * Created on December , 2013
 */

package net.sf.jaer.hardwareinterface.usb.silabs;
import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import li.longi.USBTransferThread.RestrictedTransfer;
import li.longi.USBTransferThread.RestrictedTransferCallback;
import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;

import org.usb4java.LibUsb;

/**
 * AE-Reader for Silabs based microcontrollers used by LibUsb.
 * It should perform the transfer of the AE packages from the
 * microcontroller as well as handle the buffers.
 * Currently this class is implemented only for the linux driver for the Paer board.
 * Still it could be easily reused for different boards using the silabs.
 * @author sweber
 */
public abstract class SilabsAEReader implements ReaderBufferControl {

    private Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    private int fifoSize = this.prefs.getInt("Silabs.AEReader.fifoSize", 8192);
    private int numBuffers;
    private int Silabs_FIFO_SIZE = 128; // just took this from usbio
    protected static final Logger log = Logger.getLogger("SilabsC8051F320_LibUsb_AEReader");
    protected SiLabsC8051F320_LibUsb driver;
    protected USBTransferThread usbTransfer;
    protected int cycleCounter = 0;
    private volatile boolean active =true;



    public SilabsAEReader(SiLabsC8051F320_LibUsb driver) {
        this.driver = driver;
    }

    @Override
    public int getFifoSize() {
        return fifoSize;
    }

    public void setEnable(boolean enable) {
        active =true;
    }


    /**
     * converts the received Bytebuffers to an AEPacketRaw buffer and adds it to the pool.
     * @param buffer transfered AE data from the microcontroller
     */
    protected abstract void translateEvents(final ByteBuffer buffer);



    @Override
    public void setFifoSize(int fifoSize) {
        if (fifoSize < this.Silabs_FIFO_SIZE) {
            log.log(Level.WARNING, "Silabs.AEReader fifo size clipped to device FIFO size {0}" + this.Silabs_FIFO_SIZE);
            fifoSize = this.Silabs_FIFO_SIZE;
        }

        this.fifoSize = fifoSize;

        usbTransfer.setBufferSize(fifoSize);

        this.prefs.putInt("Silabs.AEReader.fifoSize", fifoSize);
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public void setNumBuffers(final int numBuffers) {
        this.numBuffers = numBuffers;

        usbTransfer.setBufferNumber(numBuffers);

        this.prefs.putInt("Silabs.AEReader.numBuffers", numBuffers);
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return driver.getReaderSupport();
    }

    public abstract void resetTimestamps();


    /**
     * Callback class used for the transfer thread.
     */
    class ProcessAEData implements RestrictedTransferCallback {

        @Override
        public void prepareTransfer(final RestrictedTransfer transfer) {
            // Nothing to do here.
        }

        /**
         * Called on completion of read on a data buffer is received from USBIO
         * driver.
         *
         * @param Buf the data buffer with raw data
         */
        @Override
        public void processTransfer(final RestrictedTransfer transfer) {
            cycleCounter++;
            AEPacketRawPool aePacketRawPool = driver.getaePacketRawPool();

            synchronized (aePacketRawPool) {

                if ((transfer.status() == LibUsb.TRANSFER_COMPLETED)
                        || (transfer.status() == LibUsb.TRANSFER_CANCELLED)) {
                    translateEvents(transfer.buffer());

                    if ((driver.getChip() != null) && (driver.getChip().getFilterChain() != null)
                            && (driver.getChip().getFilterChain().getProcessingMode() == FilterChain.ProcessingMode.ACQUISITION)) {
			// here we do the realTimeFiltering. We finished capturing this buffer's worth of events,
                        // now process them apply realtime filters and realtime (packet level) mapping

                        // synchronize here so that rendering thread doesn't swap the buffer out from under us while
                        // we process these events aePacketRawPool.writeBuffer is also synchronized so we getString
                        // the same lock twice which is ok
                        final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                        final int[] addresses = buffer.getAddresses();
                        final int[] timestamps = buffer.getTimestamps();
                        //realTimeFilter(addresses, timestamps); TODO!!!
                    }
                } else if (transfer.status() == LibUsb.TRANSFER_STALL) {

                    try {
                        LibUsb.clearHalt(driver.retinahandle, LibUsb.ENDPOINT_IN);
                    } catch (Exception e) {
                        log.warning("could not fix Transfer stall");
                    }

                } else {
                    if (!active) {
                        return;
                    }
                    active = false;
                    SilabsAEReader.log.warning("ProcessAEData: Bytes transferred: " + transfer.actualLength()
                            + "  Status: " + LibUsb.errorName(transfer.status()));

                    //LibUsb.resetDevice(driver.retinahandle);
                    Thread closeThread = new Thread() {
                        @Override
                        public void run() {
                            driver.close();
                        }
                    };
                    closeThread.start();
                }
            }
        }
    }
}
