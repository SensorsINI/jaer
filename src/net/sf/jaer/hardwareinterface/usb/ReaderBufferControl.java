/*
 * ReaderBufferControl.java
 *
 * Created on May 3, 2006, 12:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb;

import java.beans.PropertyChangeSupport;

/**
 * A process/thread (like the Thesycon USB code) that reads AEs can be controlled by these methods that set the FIFO and number of buffer sizes.
 *This interface is implemented by e.g. the CypressFX2 and the StereoHardwareInterface so that the device control menu can be built to control them uniformly.
 *
 * @author tobi
 */
public interface ReaderBufferControl {
    public int getFifoSize();
    public void setFifoSize(int fifoSize);

    public int getNumBuffers();
    public void setNumBuffers(int numBuffers);
    /** The reader should fire PropertyChangeEvent "readerStarted" when the reader is started and "readerStopped" when it is stopped. */
    public PropertyChangeSupport getReaderSupport();

    /**
     * FIFO size the user has requested (may still be waiting for a debounced
     * transfer-session replace). Defaults to {@link #getFifoSize()}.
     */
    default int getPendingFifoSize() {
        return getFifoSize();
    }

    /** Buffer count the user has requested; defaults to {@link #getNumBuffers()}. */
    default int getPendingNumBuffers() {
        return getNumBuffers();
    }

    /**
     * FIFO size of the active/applied USB transfer session when known.
     * Defaults to {@link #getFifoSize()} for drivers without session tracking.
     */
    default int getActiveFifoSize() {
        final UsbAsyncBulkReaderLifecycle.Status status = getUsbBufferConfigStatus();
        if (status != null && status.active != null) {
            return status.active.fifoSize;
        }
        return getFifoSize();
    }

    /**
     * Buffer count of the active/applied USB transfer session when known.
     * Defaults to {@link #getNumBuffers()} for drivers without session tracking.
     */
    default int getActiveNumBuffers() {
        final UsbAsyncBulkReaderLifecycle.Status status = getUsbBufferConfigStatus();
        if (status != null && status.active != null) {
            return status.active.numBuffers;
        }
        return getNumBuffers();
    }

    /**
     * True while a USB FIFO/buffer change is queued or being applied to a live
     * transfer session. Menu labels can show that the value is not live yet.
     */
    default boolean isUsbBufferReconfigPending() {
        final UsbAsyncBulkReaderLifecycle.Status status = getUsbBufferConfigStatus();
        if (status == null) {
            return false;
        }
        return status.phase == UsbAsyncBulkReaderLifecycle.Phase.QUEUED
                || status.phase == UsbAsyncBulkReaderLifecycle.Phase.RESTARTING;
    }

    /**
     * Optional immutable requested/active status snapshot. Returns null when the
     * hardware interface does not track USB transfer-session generations.
     */
    default UsbAsyncBulkReaderLifecycle.Status getUsbBufferConfigStatus() {
        return null;
    }
}
