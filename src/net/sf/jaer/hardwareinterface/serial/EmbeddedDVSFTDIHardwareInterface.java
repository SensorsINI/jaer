/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial;

import gnu.io.UnsupportedCommOperationException;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWP_RS232;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWPort;
import net.sf.jaer.hardwareinterface.serial.edvsviewer.HWPort.*;

/**
 * Hardware interface to eDDVS with FTDI serial port emulating USB device.
 * 
 * @author tobi
 */
public class EmbeddedDVSFTDIHardwareInterface implements AEMonitorInterface {

    private final static Logger log = Logger.getLogger("IOThread");
    public final int BAUD_RATE = 4000000;
    public final boolean RTSCTS_ENABLED = true;
    /** This support can be used to register this interface for property change events */
    public PropertyChangeSupport support = new PropertyChangeSupport(this);    // consts
    private HWP_RS232 port = null;
    PortAttribute portAttribute = null;
    PortIdentifier portIdentifier = null;
    private IOThread ioThread = null;
    private AEPacketRawPool packetPool = new AEPacketRawPool(this);
    private AEChip chip;
    private int aeBufferSize=20000;

    public EmbeddedDVSFTDIHardwareInterface(HWP_RS232 port, PortIdentifier portIdentifier, PortAttribute portAttribute) {
        this.port = port;
        this.portIdentifier = portIdentifier;
        this.portAttribute=portAttribute;
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        packetPool.swap();
        return packetPool.readBuffer();
    }

    @Override
    public int getNumEventsAcquired() {
        return packetPool.readBuffer().getNumEvents();
    }

    @Override
    public AEPacketRaw getEvents() {
        return packetPool.readBuffer();
    }

    @Override
    public void resetTimestamps() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean overrunOccurred() {
        return packetPool.readBuffer().overrunOccuredFlag;
    }

    @Override
    public int getAEBufferSize() {
        return aeBufferSize;
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        this.aeBufferSize=AEBufferSize;
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        throw new UnsupportedOperationException("Not supported yet.");
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

    @Override
    public int getMaxCapacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getEstimatedEventRate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getTimestampTickUs() {
        return 1; // TODO actually support multiple i think
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
    public String getTypeName() {
        return "FTDI or TCP";
    }

    @Override
    public void close() {

        if(port!=null)        port.close();
        if(ioThread!=null) ioThread.terminate();
    }

    @Override
    public void open() throws HardwareInterfaceException {
        port.open((String) portIdentifier.getID(), portAttribute);
    }

    @Override
    public boolean isOpen() {
        return port.isOpen();
    }

    /** Basic IO thread for serial eDVS communication which writes to AEPacketRawPool buffer
     * 
     * @author Jorg Conradt, Tobi Delbruck
     */
    public class IOThread extends Thread {

        private volatile boolean threadRunning;
        private HWP_UART port = null;
        private boolean echoOutputOnScreen = false;
        private String rs232Input;

        public void sendCommand(String cmd) throws UnsupportedEncodingException, IOException {
            port.writeLn(cmd);
        }

        public IOThread(HWP_UART port) {
            this.port = port;
            threadRunning = true;
            this.setPriority(MAX_PRIORITY);
        }

        public void run() {
            try {
                port.setHardwareFlowControl(true);
            } catch (UnsupportedCommOperationException ex) {
                log.warning(ex.toString());
            }
            while (threadRunning) {

//            port.purgeInput();

                yield();

                try {
                    rs232Input = port.getAllData();

                    if (rs232Input != null) {

                        if (echoOutputOnScreen) {
                            System.out.print(rs232Input);
                        }

                        parseNewInput(rs232Input);

                    }
                } catch (Exception e) { //
                    System.out.println("Exception! " + e);
                    e.printStackTrace();
                }

            }

        }

        public void terminate() {
            threadRunning = false;
        }
        private int pixelX, pixelY, pixelP;
        private int inputProcessingIndex = 0;
        private String specialData;

        public void parseNewInput(String input) {

            for (int n = 0; n < input.length(); n++) {

                int c = (int) (input.charAt(n));
                switch (inputProcessingIndex) {
                    case 0:
                        if ((c & 0x80) == 0) {		// check if valid "high byte"
                            pixelX = c;
                            inputProcessingIndex = 1;
                        } else {
                            if ((c & 0xF0) == 0x80) {
                                inputProcessingIndex = 100 + (c & 0x0F) - 1;	// remember start of special data sequence
                                //System.out.println("Start Special Sequence of length : " +inputProcessingIndex);
                                specialData = "";
                            } else {
                                System.out.println("Data transfer hickup at " + System.currentTimeMillis());
                            }
                            // otherwise ignore and assume next is high byte
                            // System.out.println("flip error " + System.currentTimeMillis());
                        }
                        break;

                    case 1:
                        pixelY = c & 0x7F;
                        pixelP = (c & 0x80) >> 7;
                        inputProcessingIndex = 0;

                        // TOBI: HERE we have the NEW EVENT  TODO
                        // processNewEvent(pixelX, pixelY, pixelP);
                        System.out.printf("new Event at %3d/%3d, Polarity %1d\n", pixelX, pixelY, pixelP);

                        break;

                    case 100:
                        specialData = specialData + input.charAt(n);
                        inputProcessingIndex = 0;

                        // TOBI: Here we have special information, such as bias values, etc
                        // iv.processSpecialData(specialData);
                        System.out.printf("received special data: %s\n", specialData);

                        break;

                    default:
                        specialData = specialData + input.charAt(n);
                        inputProcessingIndex--;
                }
            }
        }
    }
}
