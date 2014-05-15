/*
 * SiLabsC8051F320_LibUsb_PAER.java
 *
 * Created on December , 2013
 */

package net.sf.jaer.hardwareinterface.usb.silabs;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import org.usb4java.Device;
import org.usb4java.LibUsb;

/**
 * This is the main class of the LibUsb driver for the PAER-board.
 * It implements all board specific code which is not general enough for
 * SiLabsC8051F320_LibUsb. This class is registered as the hardware interface.
 * Contains its own AEReader.
 * @author sweber
 */
public class SiLabsC8051F320_LibUsb_PAER extends SiLabsC8051F320_LibUsb {

    public static final short PID_PAER = (short) 0x8411;
    private static final String drivername = "SiLabsC8051F320_Paer_Linux";

    public SiLabsC8051F320_LibUsb_PAER(Device dev) {
        LibUsb.setDebug(null, 1);
        this.retina = dev;
        this.prefs = Preferences.userNodeForPackage(this.getClass());
        this.aeReader = null;


    }

    @Override
    public void setAEReaderEnabled(boolean enabled) {

        if (enabled) {
            this.aeReader = new PaerAEReader(this);
            allocateAEBuffers();
            this.aeReader.setEnable(true);
            HardwareInterfaceException.clearException();
        } else {
            if (this.aeReader == null) {
                return;
            }
            this.aeReader.setEnable(false);
            this.aeReader = null;
        }


    }
    @Override
	protected void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    @Override
    public String getTypeName() {
        return drivername;
    }

    @Override
    public int getMaxCapacity() {
        return 100000;
    }

    /**
     * contains the implementation specific
     */

    private class PaerAEReader extends SilabsAEReader {

        private int MONITOR_PRIORITY = Thread.MAX_PRIORITY;
        private boolean gotEvent = false;
        private int wrapAdd = 0;
        private int wrapsSinceLastEvent;
        private final int WRAPS_TO_PRINT_NO_EVENT=500;
        private final byte ENDPOINT_IN = (byte) 0x82;

        public PaerAEReader(SiLabsC8051F320_LibUsb driver) {
            super(driver);
        }

        /**
         * starts/stops the usbTransfer thread and notifies the readers.
         * @param enable
         */
        @Override
        public void setEnable(boolean enable) {
            super.setEnable(enable);
            if (enable == true) {
                if (!isOpen()) {
                    try {
                        open();
                    } catch (final HardwareInterfaceException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                usbTransfer = new USBTransferThread(super.driver.retinahandle, ENDPOINT_IN, LibUsb.TRANSFER_TYPE_BULK,
                        new PaerAEReader.ProcessAEData(), getNumBuffers(), getFifoSize());
                usbTransfer.setPriority(this.MONITOR_PRIORITY);
                usbTransfer.setName("AEReaderThread");
                usbTransfer.start();

                super.getReaderSupport().firePropertyChange("readerStarted", false, true);
            } else {

                usbTransfer.interrupt();
                boolean stopped = false;
                while (!stopped) {
                    try {
                        usbTransfer.join();
                    } catch (InterruptedException ex) {
                        continue;
                    }
                    stopped = true;
                }

                support.firePropertyChange("readerStopped", false, true);
                this.driver.aeReader = null;
            }

        }
        /**
         * copy paste from USBIO version - seems to work fine
         * @see SiLabsC8051F320_USBIO_DVS128
         * @param b
         */
        @Override
        protected void translateEvents(ByteBuffer b) {
            AEPacketRaw buffer = aePacketRawPool.writeBuffer();
            if (buffer.overrunOccuredFlag) {
                return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
            }
            int shortts;


            //            byte lsb,msb;
            int bytesSent = b.remaining();
            if ((bytesSent % 4) != 0) {
                log.warning("warning: " + bytesSent + " bytes sent, which is not multiple of 4");
                bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event
                driver.close();
                return;
            }

            int[] addresses = buffer.getAddresses();
            int[] timestamps = buffer.getTimestamps();

            buffer.lastCaptureIndex = eventCounter; // write the start of the packet
            gotEvent = false;
            byte[] aeBuffer = new byte[b.remaining()];
//            int NumberOfWrapEvents;
//            NumberOfWrapEvents = 0;
            b.duplicate().get(aeBuffer);
            for (int i = 0; i < bytesSent; i += 4) {
                if (eventCounter > (this.driver.getAEBufferSize() - 1)) {
                    buffer.overrunOccuredFlag = true;
//                                        log.warning("overrun");
                    return; // return, output event buffer is full and we cannot add any more events to it.
                    //no more events will be translated until the existing events have been consumed by acquireAvailableEventsFromDriver
                }
                addresses[eventCounter] = (aeBuffer[i + 1] & 0xFF) | ((aeBuffer[i] & 0xFF) << 8);
                shortts = ((aeBuffer[i + 3] & 0xff) | ((aeBuffer[i + 2] & 0xff) << 8));

                if (addresses[eventCounter] == 0xFFFF) { // changed to handle this address as special wrap event
                    wrapAdd += 0x10000;	// if we wrapped then increment wrap value by 2^16
                    if (!gotEvent) {
                        wrapsSinceLastEvent++;
                    }
                    if (wrapsSinceLastEvent >= WRAPS_TO_PRINT_NO_EVENT) {
                        log.warning("got " + wrapsSinceLastEvent + " timestamp wraps without any events");
                        wrapsSinceLastEvent = 0;
                    }
                    continue; // skip timestamp and continue to next address without incrementing eventCounter
                }
                timestamps[eventCounter] = TICK_US * (shortts + wrapAdd); //*TICK_US; //add in the wrap offset and convert to 1us tick
                eventCounter++;
                buffer.setNumEvents(eventCounter);
                gotEvent = true;
                wrapsSinceLastEvent = 0;
            }

            // write capture size
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
        }

        @Override
        public void resetTimestamps() {
            this.wrapAdd=0;
        }

    }


}
