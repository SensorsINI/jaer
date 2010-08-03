/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp.SmartEyeTDS;

import java.beans.*;
import java.util.logging.Logger;
import java.util.prefs.*;

import net.sf.jaer.aemonitor.*;
import net.sf.jaer.chip.*;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.udp.*;

/**
 *
 * @author braendch
 */
public class SmartEyeTDS implements UDPInterface, AEMonitorInterface{

    /** Used to store preferences, e.g. the default firmware download file for blank devices */
    protected static Preferences prefs = Preferences.userNodeForPackage(SmartEyeTDS.class);
    /** This support can be used to register this interface for property change events */
    public PropertyChangeSupport support = new PropertyChangeSupport(this);

    protected Logger log = Logger.getLogger("SmartEyeTDS");
    protected AEChip chip;

    public static final int UDP_CONSOLE_PORT = 20010;

    /** Time in us of each timestamp count here on host, could be different on board. */
    public final short TICK_US = 1;
    /** the last events from {@link #acquireAvailableEventsFromDriver}, This packet is reused. */
    protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
    /** default size of AE buffer for user processes. This is the buffer that is written by the hardware capture thread that holds events
     * that have not yet been transferred via {@link #acquireAvailableEventsFromDriver} to another thread
     * @see #acquireAvailableEventsFromDriver
     * @see AEReader
     * @see #setAEBufferSize
     */
    public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS
    /** this is the size of the AEPacketRaw that are part of AEPacketRawPool that double buffer the translated events between rendering and capture threads */
    protected int aeBufferSize = prefs.getInt("SmartEyeTDS.aeBufferSize", AE_BUFFER_SIZE);
    
    public static boolean isOpened = false;
    public static boolean eventAcquisitionEnabled = true;
    public static boolean overrunOccuredFlag = false;

    @Override
    public void open() throws HardwareInterfaceException {
        int status=0;
        if(status==0) {
            isOpened=true;
            HardwareInterfaceException.clearException();
            return;
        }else {
            isOpened=false;
            throw new HardwareInterfaceException("nativeOpen: can't open device, device returned status ");
        }
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public void close(){
        isOpened=false;
    }

    @Override
    public String getTypeName() {
        return "AIT SmartEye Traffic Data Sensor";
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
        return TICK_US;
    }

    private int estimatedEventRate = 0;

    /** @return event rate in events/sec as computed from last acquisition.
     *
     */
    @Override
    public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    /** the max capacity of this UDP interface is max (10 Gbit/s) / (8bytes/event)
     */
    @Override
    public int getMaxCapacity() {
        return 156250000;
    }

    /** adds a listener for new events captured from the device.
     * Actually gets called whenever someone looks for new events and there are some using
     * acquireAvailableEventsFromDriver, not when data is actually captured by AEReader.
     * Thus it will be limited to the users sampling rate, e.g. the game loop rendering rate.
     *
     * @param listener the listener. It is called with a PropertyChangeEvent when new events
     * are received by a call to {@link #acquireAvailableEventsFromDriver}.
     * These events may be accessed by calling {@link #getEvents}.
     */
    @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

        /** start or stops the event acquisition. sends apropriate vendor request to
     * device and starts or stops the AEReader
     * @param enable boolean to enable or disable event acquisition
     */
    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        eventAcquisitionEnabled = enable;
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return eventAcquisitionEnabled;
    }

    /** @return the size of the double buffer raw packet for AEs */
    @Override
    public int getAEBufferSize() {
        return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
    }

    /** set the size of the raw event packet buffer. Default is AE_BUFFER_SIZE. You can set this larger if you
     *have overruns because your host processing (e.g. rendering) is taking too long.
     *<p>
     *This call discards collected events.
     * @param size of buffer in events
     */
    @Override
    public void setAEBufferSize(int size) {
        if (size < 1000 || size > 1000000) {
            log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.aeBufferSize = size;
        prefs.putInt("CypressFX2.aeBufferSize", aeBufferSize);
    }

    /** Is true if an overrun occured in the driver thread during the period before the last time acquireAvailableEventsFromDriver() was called. This flag is cleared by {@link #acquireAvailableEventsFromDriver}, so you need to
         * check it before you acquire the events.
         *<p>
         *If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The timestamps continue on but will
         *probably be lagged behind what they should be.
         * @return true if there was an overrun.
         */
    @Override
    public boolean overrunOccurred() {
        return overrunOccuredFlag;
    }

    /** Resets the timestamp unwrap value, resets the USBIO pipe, and resets the AEPacketRawPool.
         */
    @Override
    synchronized public void resetTimestamps() {
        //TODO call TDS to reset timestamps
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

    /** Gets available events from the socket.  {@link HardwareInterfaceException} is thrown if there is an error.
     *{@link #overrunOccurred} will be reset after this call.
     *<p>
     *This method also starts event acquisition if it is not running already.
     *
     *Not thread safe but does use the thread-safe swap() method of AEPacketRawPool to swap data with the acquisition thread.
     *
     * @return number of events acquired. If this is zero there is no point in getting the events, because there are none.
     *@throws HardwareInterfaceException
     *@see #setEventAcquisitionEnabled
     *
     * .
     */
    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!isOpened) {
            open();
        }

        // make sure event acquisition is running
        if (!eventAcquisitionEnabled) {
            setEventAcquisitionEnabled(true);
        }

        return lastEventsAcquired;

    }

}
