/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb;

/**
 * Hardware interface can collect USB IN transfer statistics (packet size vs
 * FIFO, interval, throughput) for buffer tuning. The USB tuning panel enables
 * collection while it is open and displays {@link #snapshotUsbStatistics()}.
 *
 * @author tobi
 */
public interface HasUsbStatistics {

    /**
     * When true, the USB thread records IN transfer sizes. The USB tuning
     * panel turns this on while visible and off when closed.
     */
    public void setShowUsbStatistics(boolean yes);

    /**
     * When true, also log throttled USB IN stats to the jAER logger (~1 Hz).
     * Default is false. The tuning panel does not use this.
     */
    public void setPrintUsbStatistics(boolean yes);

    public boolean isShowUsbStatistics();

    public boolean isPrintUsbStatistics();

    /**
     * Last window already taken or printed. Does not rotate the accumulator.
     */
    USBPacketStatistics.Snapshot snapshotUsbStatistics();

    /**
     * Copy the current ~1 s accumulation and start a new window. The USB
     * tuning panel calls this at 1 Hz.
     */
    default USBPacketStatistics.Snapshot takeUsbStatisticsSnapshot() {
        return snapshotUsbStatistics();
    }
}
