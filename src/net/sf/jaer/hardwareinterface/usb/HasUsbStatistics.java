/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb;

/**
 * Hardware interface can collect USB IN transfer statistics (packet size vs
 * FIFO, interval, throughput) for buffer tuning.
 *
 * @author tobi
 */
public interface HasUsbStatistics {

    /**
     * Reserved for a future graphical view; currently unused.
     */
    public void setShowUsbStatistics(boolean yes);

    /**
     * When true, log throttled USB IN stats to the jAER logger (~1 Hz).
     * Default is false (USB thread work only while enabled).
     */
    public void setPrintUsbStatistics(boolean yes);

    public boolean isShowUsbStatistics();

    public boolean isPrintUsbStatistics();
}
