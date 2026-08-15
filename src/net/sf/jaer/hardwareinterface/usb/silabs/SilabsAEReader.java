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
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle.Config;

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

    private static Preferences prefs = JaerConstants.PREFS_ROOT_HARDWARE;
    private int fifoSize = this.prefs.getInt("Silabs.AEReader.fifoSize", 32768);
    private int numBuffers = this.prefs.getInt("Silabs.AEReader.numBuffers", 4);
    private int Silabs_FIFO_SIZE = 128; // just took this from usbio
    protected static final Logger log = Logger.getLogger("net.sf.jaer");
    protected SiLabsC8051F320_LibUsb driver;
    protected USBTransferThread usbTransfer;
    protected int cycleCounter = 0;
    private volatile boolean active = true;
    private volatile boolean readerActive;
    private final UsbAsyncBulkReaderLifecycle bufferLifecycle;

    public SilabsAEReader(SiLabsC8051F320_LibUsb driver) {
        this.driver = driver;
        bufferLifecycle = new UsbAsyncBulkReaderLifecycle(new BufferHost());
    }

    protected abstract byte getEventEndpoint();

    protected int transferThreadPriority() {
        return Thread.NORM_PRIORITY;
    }

    @Override
    public int getFifoSize() {
        return fifoSize;
    }

    public void setEnable(boolean enable) {
        active = enable;
        if (!enable) {
            stopTransferThread();
            return;
        }
        if (usbTransfer != null && usbTransfer.isAlive()) {
            return;
        }
        final int buffers = Math.max(1, numBuffers);
        final long gen = bufferLifecycle.adoptExternalStart(new Config(fifoSize, buffers));
        startTransferThread(gen);
    }

    boolean isBufferReconfigPending() {
        return bufferLifecycle.isReconfigPending();
    }

    public UsbAsyncBulkReaderLifecycle.Status getBufferConfigStatus() {
        return bufferLifecycle.statusSnapshot();
    }

    @Override
    public int getActiveFifoSize() {
        final Config applied = bufferLifecycle.appliedConfig();
        return applied != null ? applied.fifoSize : fifoSize;
    }

    @Override
    public int getActiveNumBuffers() {
        final Config applied = bufferLifecycle.appliedConfig();
        return applied != null ? applied.numBuffers : numBuffers;
    }

    void startTransferThread(long generation) {
        readerActive = true;
        usbTransfer = new USBTransferThread(driver.retinahandle, getEventEndpoint(), LibUsb.TRANSFER_TYPE_BULK,
                new ProcessAEData(generation), Math.max(1, getNumBuffers()), getFifoSize());
        usbTransfer.setPriority(transferThreadPriority());
        usbTransfer.setName("AEReaderThread");
        usbTransfer.start();
        getReaderSupport().firePropertyChange("readerStarted", false, true);
    }

    boolean stopTransferThread() {
        bufferLifecycle.discardPendingRestart();
        bufferLifecycle.markQuiescing();
        readerActive = false;
        if (usbTransfer == null) {
            bufferLifecycle.markStopped();
            return true;
        }
        final boolean stopped = UsbAsyncBulkReaderLifecycle.interruptAndJoin(
                usbTransfer, UsbAsyncBulkReaderLifecycle.DEFAULT_JOIN_TIMEOUT_MS, log, "Silabs AEReader");
        if (!stopped) {
            bufferLifecycle.markFailed();
            driver.recoverFailedBufferReconfig(new HardwareInterfaceException(
                    "Silabs AEReader did not stop within "
                            + UsbAsyncBulkReaderLifecycle.DEFAULT_JOIN_TIMEOUT_MS + " ms"));
            return false;
        }
        usbTransfer = null;
        bufferLifecycle.markStopped();
        getReaderSupport().firePropertyChange("readerStopped", false, true);
        return true;
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
        this.prefs.putInt("Silabs.AEReader.fifoSize", fifoSize);
        bufferLifecycle.schedule(new Config(this.fifoSize, Math.max(1, this.numBuffers)));
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public void setNumBuffers(final int numBuffers) {
        this.numBuffers = numBuffers;
        this.prefs.putInt("Silabs.AEReader.numBuffers", numBuffers);
        bufferLifecycle.schedule(new Config(this.fifoSize, this.numBuffers));
    }

    @Override
    public boolean isUsbBufferReconfigPending() {
        return bufferLifecycle.isReconfigPending();
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

        private final long generation;

        ProcessAEData(long generation) {
            this.generation = generation;
        }

        @Override
        public void prepareTransfer(final RestrictedTransfer transfer) {
        }

        @Override
        public void processTransfer(final RestrictedTransfer transfer) {
            if (!readerActive || !bufferLifecycle.isCurrent(generation)) {
                return;
            }
            cycleCounter++;
            AEPacketRawPool aePacketRawPool = driver.getaePacketRawPool();

            synchronized (aePacketRawPool) {
                if (!bufferLifecycle.isCurrent(generation)) {
                    return;
                }

                if ((transfer.status() == LibUsb.TRANSFER_COMPLETED)
                        || (transfer.status() == LibUsb.TRANSFER_CANCELLED)) {
                    translateEvents(transfer.buffer());
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

    private final class BufferHost implements UsbAsyncBulkReaderLifecycle.Host {
        @Override
        public String deviceLabel() {
            return "Silabs";
        }

        @Override
        public Logger log() {
            return log;
        }

        @Override
        public PropertyChangeSupport readerSupport() {
            return driver.getReaderSupport();
        }

        @Override
        public boolean hasActiveTransfer() {
            return usbTransfer != null && usbTransfer.isAlive();
        }

        @Override
        public boolean stopSession(long generation, long joinTimeoutMs) {
            readerActive = false;
            if (usbTransfer == null) {
                return true;
            }
            final boolean stopped = UsbAsyncBulkReaderLifecycle.interruptAndJoin(
                    usbTransfer, joinTimeoutMs, log, "Silabs AEReader");
            if (!stopped) {
                return false;
            }
            usbTransfer = null;
            getReaderSupport().firePropertyChange("readerStopped", false, true);
            return true;
        }

        @Override
        public Config startSession(Config requested, long generation) {
            fifoSize = requested.fifoSize;
            numBuffers = requested.numBuffers;
            startTransferThread(generation);
            return requested;
        }

        @Override
        public void applyIdleConfig(Config config) {
            fifoSize = config.fifoSize;
            numBuffers = config.numBuffers;
        }

        @Override
        public void recoverFailedSession(Config pending, Exception cause) {
            driver.recoverFailedBufferReconfig(cause);
        }
    }
}
