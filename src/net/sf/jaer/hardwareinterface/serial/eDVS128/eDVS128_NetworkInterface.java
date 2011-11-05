/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial.eDVS128;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AESocket;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Acquires data from and controls the eDVS with wifi interface.
 * <p>
 * /**
 * Interface to eDVS128 camera.
 *  
 * The camera returns the following help when used in serial port mode.
 * <pre>
 * DVS128 - LPC2106/01 Interface Board, 1.1: July 27, 2011.
System Clock: 64MHz / 64 -> 1000ns event time resolution
Modules: 

Supported Commands:

E+/-       - enable/disable event sending
!Ex        - specify event data format, ??E to list options

!BF          - send bias settings to DVS
!Bx=y      - set bias register x[0..11] to value y[0..0xFFFFFF]
?Bx        - get bias register x current value

!R+/-      - transmit event rate on/off
0,1,2      - LED off/on/blinking
!S=x       - set baudrate to x

R          - reset board
P          - enter reprogramming mode

??         - display help

??E
!E0   - 2 bytes per event binary 0yyyyyyy.pxxxxxxx (default)
!E1   - 4 bytes per event (as above followed by 16bit timestamp)

!E10  - 3 bytes per event, 6bit encoded
!E11  - 6 bytes per event+timestamp, 6bit encoded 
!E12  - 4 bytes per event, 6bit encoded; new-line
!E13  - 7 bytes per event+timestamp, 6bit encoded; new-line

!E20  - 4 bytes per event, hex encoded
!E21  - 8 bytes per event+timestamp, hex encoded 
!E22  - 5 bytes per event, hex encoded; new-line
!E23  - 8 bytes per event+timestamp, hex encoded; new-line

!E30  - 10 bytes per event, ASCII <1p> <3y> <3x>; new-line
!E31  - 10 bytes per event+timestamp, ASCII <1p> <3y> <3x> <5ts>; new-line
 * </pre>
 * 
 * @author tobi
 */
public class eDVS128_NetworkInterface implements HardwareInterface, AEMonitorInterface, BiasgenHardwareInterface, Observer {

    final static Logger log = Logger.getLogger("eDVS128_NetworkInterface");
    AESocket socket = null;
    public static final int PORT = 56000;
    public static String HOST =  "192.168.91.3";
    private int EVENT_SIZE = 8; // bytes each
    private boolean isOpen = false;
    protected AEChip chip;
    PropertyChangeSupport support=new PropertyChangeSupport(this);
    public static final String EVENT_HOST="host", EVENT_PORT="port";

    public eDVS128_NetworkInterface() {

        socket = new AESocket(HOST, PORT);

    }

    public void setPort(int port) {
        int old=socket.getPort();
        socket.setPort(port);
        support.firePropertyChange(EVENT_PORT, old, port);
    }

    public void setHost(String host) {
        String old=socket.getHost();
        socket.setHost(host);
        support.firePropertyChange(EVENT_HOST,old,host);
    }

    public int getPort() {
        return socket.getPort();
    }

    public String getHost() {
        return socket.getHost();
    }
    
    

    @Override
    public String getTypeName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    @Override
    public void open() throws HardwareInterfaceException {
        try {
            socket.connect();
            write("!E1\n");
            isOpen = true;
        } catch (Exception ex) {
            throw new HardwareInterfaceException("could not open", ex);
        }
    }

    private void write(String s) throws IOException {
        byte[] b = s.getBytes();
        socket.getSocket().getOutputStream().write(b, 0, b.length);
    }

    @Override
    public boolean isOpen() {
        return socket == null ? false : socket.getSocket().isConnected();
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        try {
            return socket.readPacket();
        } catch (IOException ex) {
            throw new HardwareInterfaceException("failed to acquire events", ex);
        }
    }

    @Override
    public int getNumEventsAcquired() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AEPacketRaw getEvents() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resetTimestamps() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean overrunOccurred() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getAEBufferSize() {
        return socket.getReceiveBufferSize() / EVENT_SIZE;
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        socket.setReceiveBufferSize(AEBufferSize * EVENT_SIZE);
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        try {
            write("E+\n");
        } catch (IOException ex) {
            throw new HardwareInterfaceException("failed to enable event acquisition", ex);
        }
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
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
        return 1;
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
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        log.warning("Flash configuration not supported by eDVS128 devices.");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    private final int NUM_BIASES = 12; // number of biases, to renumber biases for bias command
    private boolean DEBUG = false;

    /** Called when notifyObservers is called in Observable we are watching, e.g. biasgen */
    @Override
    synchronized public void update(Observable o, Object arg) {

        if (o instanceof IPot) {
            try {
                IPot p = (IPot) o;
                int v = p.getBitValue();
                int n = NUM_BIASES - 1 - p.getShiftRegisterNumber(); // eDVS firmware numbers in reverse order from DVS firmware, we want shift register 0 to become 11 on the eDVS
                String s = String.format("!B%d=%d\n", n, v); // LPC210 has 16-byte serial buffer, hopefully fits
                if (DEBUG) {
                    log.info("sending command " + s + " for pot " + p + " at bias position " + n);
                }
                write(s);
                write("!BF\n");
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public static final void main(String[] args) {
        try {
            eDVS128_NetworkInterface h = new eDVS128_NetworkInterface();
            h.open();
            h.setEventAcquisitionEnabled(true);
            for (int i = 0; i < 10; i++) {
                AEPacketRaw p = h.acquireAvailableEventsFromDriver();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    break;
                }
                System.out.println(p.toString());
            }
            log.info("close and reopen");
            h.close();
            h.open();
            AEPacketRaw p = h.acquireAvailableEventsFromDriver();
            System.out.println(p.toString());
            h.close();
            return;
        } catch (HardwareInterfaceException ex) {
            ex.printStackTrace();
        }
    }
}
