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
    
}
