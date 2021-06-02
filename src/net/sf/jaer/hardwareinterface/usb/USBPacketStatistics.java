/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb;

import de.thesycon.usbio.UsbIoBuf;
import li.longi.USBTransferThread.RestrictedTransfer;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;
import net.sf.jaer.util.histogram.Histogram;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Tracks low level USB packet transfer statistics
 *
 * @author tobi
 */
public class USBPacketStatistics implements HasUsbStatistics {

    public LowpassFilter packetIntevalUsFilter = new LowpassFilter(100);
    public LowpassFilter packetSizeBytesFilter = new LowpassFilter(100);
    private long lastTimeNs;
    private boolean initialized = false;
    public Histogram packetSizeHistogram = new SimpleHistogram(0, 3, 8, 0), packetIntervalUsHistogram = new SimpleHistogram(0, 3, 8, 0);
    int nSamples = 0;
    EngineeringFormat engFmt = new EngineeringFormat();
    boolean printEnabled = false;
    boolean showEnabled = false;
    private static int INTERVAL = 300; // interval to print or show

    public void addSample(final RestrictedTransfer transfer) {
        int nBytes = transfer.actualLength();
        addValues(nBytes);
    }

    private void addValues(int nBytes) {
        if (!(showEnabled || printEnabled)) {
            return;
        }
        if (!initialized) {
            lastTimeNs = System.nanoTime();
            initialized = true;
            return;
        }
        nSamples++;
        long timeNowNs = System.nanoTime();
        int dtNs = (int) (timeNowNs - lastTimeNs);
        int dtUs = dtNs / 1000;
        lastTimeNs = timeNowNs;
        if (nBytes > 0 && dtUs >= 0) {
            packetSizeBytesFilter.filter(nBytes, dtUs);
            packetIntevalUsFilter.filter(dtUs, dtUs);
            packetIntervalUsHistogram.add(log2(dtUs));
            packetSizeHistogram.add(log2(nBytes));
            if (printEnabled) {
                printStatistics();
            }
            if (showEnabled) {
                showStatistics();
            }
        }
    }

    public void addSample(final UsbIoBuf transfer) {
        int nBytes = transfer.BytesTransferred;
        addValues(nBytes);
    }

    public void reset() {
        nSamples = 0;
        initialized = false;
        packetIntevalUsFilter.reset();
        packetSizeBytesFilter.reset();
        packetSizeHistogram.reset();
        packetIntervalUsHistogram.reset();
    }

    public String toString() {
        StringBuilder s = new StringBuilder("USBPacketStatistics: \n ");
        s.append(String.format("Average packet size: \t%s bytes\n ", engFmt.format(packetSizeBytesFilter.getValue())));
        s.append(String.format("Average packet interval: \t%s ms\n", engFmt.format(packetIntevalUsFilter.getValue() / 1000)));
        return s.toString();
    }

    public void printStatistics() {
        if (nSamples % INTERVAL != 0) {
            return;
        }
        System.out.println("Packet size (log2(bytes)) histogram:");
        packetSizeHistogram.print();
        System.out.println("\nPacket interval (log2(us)) histogram:");
        packetIntervalUsHistogram.print();
        System.out.println(this.toString());
    }

    public void showStatistics() {
        if (nSamples % INTERVAL != 0) {
            return;
        }
    }

    @Override
    public void setShowUsbStatistics(boolean yes) {
        showEnabled = yes;
        if (yes) {
            reset();
        }
    }

    @Override
    public void setPrintUsbStatistics(boolean yes) {
        printEnabled = yes;
        if (yes) {
            reset();
        }
    }

    @Override
    public boolean isShowUsbStatistics() {
        return showEnabled;
    }

    @Override
    public boolean isPrintUsbStatistics() {
        return printEnabled;
    }

    /**
     * from
     * http://stackoverflow.com/questions/3305059/how-do-you-calculate-log-base-2-in-java-for-integers
     */
    public static int log2(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException();
        }
        return 31 - Integer.numberOfLeadingZeros(n);
    }

}
