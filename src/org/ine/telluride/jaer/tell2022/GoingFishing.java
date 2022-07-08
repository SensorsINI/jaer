/*
 * Copyright (C) 2022 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.ine.telluride.jaer.tell2022;

import ch.unizh.ini.jaer.projects.npp.RoShamBoCNN;
import gnu.io.NRSerialPort;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

/**
 * Going fishing game from Under the sea Lets go Fishing from Pressman
 *
 * @author tobid, Julie Hasler (juliehsler)
 */
@Description("Let's go fishing (going fishing) game from Telluride 2022 with Tobi Delbruck and Julie Hasler")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class GoingFishing extends EventFilter2D {

    FilterChain chain = null;
    RectangularClusterTracker tracker = null;
    SpatioTemporalCorrelationFilter denoiser = null;
    NRSerialPort serialPort = null;
    private String serialPortName = getString("serialPortName", "COM3");
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;
    private final byte DIP_CMD = 1;

    public GoingFishing(AEChip chip) {
        super(chip);
        chain = new FilterChain(chip);
        tracker = new RectangularClusterTracker(chip);
        denoiser = new SpatioTemporalCorrelationFilter(chip);
        chain.add(denoiser);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip("serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        in = getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {

    }

    public void doDipFishhook() {
        sendPoleCmd(DIP_CMD);

    }

    private void openSerial() throws IOException {
        if (serialPort != null) {
            closeSerial();
        }
        StringBuilder sb = new StringBuilder("List of all available serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        if (availableSerialPorts.isEmpty()) {
            sb.append("\nNo ports found, sorry.  If you are on linux, serial port support may suffer");
        } else {
            for (String s : availableSerialPorts) {
                sb.append(s).append(" ");
            }
        }
        log.info(sb.toString());
        if (!availableSerialPorts.contains(serialPortName)) {
            final String warningString = serialPortName + " is not in avaiable " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            final String warningString = "null serial port returned when trying to open " + serialPortName + "; available " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }
        serialPort.connect();
        serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
        serialPortInputStream = new DataInputStream(serialPort.getInputStream());
        log.info("opened serial port " + serialPortName + " with baud rate=" + serialBaudRate);
    }

    private void closeSerial() {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) '0'); // rest; turn off servos
                serialPortOutputStream.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            serialPortOutputStream = null;
        }
        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
            serialPort = null;
        }
    }

    private boolean checkSerialPortAndWarn() {
        if ((serialPort == null)) {
            showWarningDialogInSwingThread("serial port commands disabled, filter is not enabled, or port is not opened", "No serial connection");
            return false;
        }
        return true;
    }

    /**
     * @return the serialBaudRate
     */
    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    /**
     * @param serialBaudRate the serialBaudRate to set
     */
    public void setSerialBaudRate(int serialBaudRate) {
        try {
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    /**
     * @return the serialPortName
     */
    public String getSerialPortName() {
        return serialPortName;
    }

    /**
     * @param serialPortName the serialPortName to set
     */
    public void setSerialPortName(String serialPortName) {
        try {
            this.serialPortName = serialPortName;
            putString("serialPortName", serialPortName);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sendPoleCmd(byte cmd) {
        checkSerialPortAndWarn();

        if ((serialPortOutputStream != null)) {
            try {
                serialPortOutputStream.write(cmd);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

}
