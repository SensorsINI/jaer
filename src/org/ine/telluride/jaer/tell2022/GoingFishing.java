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

import gnu.io.NRSerialPort;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseROI;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;

/**
 * Going fishing game from Under the sea Lets go Fishing from Pressman
 *
 * @author tobid, Julie Hasler (juliehsler)
 */
@Description("Let's go fishing (going fishing) game from Telluride 2022 with Tobi Delbruck and Julie Hasler")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class GoingFishing extends EventFilter2DMouseROI {

    FilterChain chain = null;
    RectangularClusterTracker tracker = null;
    NRSerialPort serialPort = null;
    private String serialPortName = getString("serialPortName", "COM3");
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private DataOutputStream serialPortOutputStream = null;
    private boolean disableServos = false;

    public static final String EVENT_ROD_POSITION = "rodPosition", EVENT_ROD_SEQUENCE = "rodSequence";

    // rod control
    private int zMin = getInt("zMin", 80);
    private int zMax = getInt("zMax", 100);

    // fishing move
    RodDipper rodDipper = null;
    RodSequence rodSequence = new RodSequence();

    public GoingFishing(AEChip chip) {
        super(chip);
        chain = new FilterChain(chip);
        tracker = new RectangularClusterTracker(chip);
        chain.add(new XYTypeFilter(chip));
        chain.add(new SpatioTemporalCorrelationFilter(chip));
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip("serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        setPropertyTooltip("showFishingRodControlPanel", "show control panel for fishing rod");
        setPropertyTooltip("dipRod", "make a fishing movement");
        setPropertyTooltip("zMin", "min rod tilt angle in deg");
        setPropertyTooltip("zMax", "max rod tilt angle in deg");
        setPropertyTooltip("disableServos", "turn off servos");
        try {
            rodSequence.load();
        } catch (Exception e) {
            log.warning("Could not load fishing rod movement sequence: " + e.toString());
        }
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        in = getEnclosedFilterChain().filterPacket(in);
        if (roiRect != null) {
            LinkedList<Cluster> clusterList = tracker.getVisibleClusters();
            for (Cluster c : clusterList) {
                if (roiRect.contains(c.getLocation())) {
                    // the ROI we drew contains one of the fish clusters
                    if (rodDipper == null || !rodDipper.isAlive()) {
                        dipRod();
                    }
                }
            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {

    }

    public void doDipRod() {
        dipRod();
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
//            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }
        serialPort.connect();
        serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
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

    private boolean checkSerialPort() {
        if ((serialPort == null)) {
            try {
                openSerial();
            } catch (IOException ex) {
                log.warning("couldn't open serial port "+serialPortName);
                return false;
            }
        }
        return true;
    }

    @Override
    public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            disableServos();
            closeSerial();
        } else {
            try {
                openSerial();
            } catch (IOException ex) {
                log.warning("couldn't open serial port " + serialPortName);
            }
        }
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
            log.warning("couldn't open serial port: " + ex.toString());
        }
    }

    private void sendRodPosition(int theta, int z) {
        if (disableServos) {
            return;
        }
        if (checkSerialPort()) {
            if (theta > 180) {
                theta = 180;
            } else if (theta < 0) {
                theta = 0;
            }
            if (z > 180) {
                z = 180;
            } else if (z < 0) {
                z = 0;
            }

            z = (int) Math.floor(zMin + (((float) (zMax - zMin)) / 180) * z);

            try {

                // write theta (pan) and z (tilt) of fishing pole as two unsigned byte servo angles and degrees
                byte[] bytes = new byte[2];
                bytes[0]=(byte)theta;
                bytes[1]=(byte)z;
                serialPortOutputStream.write(bytes);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void disableServos() {
        sendRodPosition(0, 0);
        disableServos = true;
    }

    public void doShowFishingRodControlPanel() {
        showFishingRodControPanel();
    }

    private JFrame fishingRodControlFrame = null;

    private void showFishingRodControPanel() {
        if (fishingRodControlFrame == null) {
            fishingRodControlFrame = new GoingFishingFishingRodControlFrame();
            fishingRodControlFrame.addPropertyChangeListener(EVENT_ROD_POSITION, this);
            fishingRodControlFrame.addPropertyChangeListener(EVENT_ROD_SEQUENCE, this);
        }
        fishingRodControlFrame.setVisible(true);
    }

    private void dipRod() {
        rodDipper = new RodDipper(rodSequence);
        rodDipper.start();
    }

    private class RodDipper extends Thread {

        RodSequence rodSequence = null;
        volatile boolean cancelled = false;

        public RodDipper(RodSequence rodSequence) {
            this.rodSequence = rodSequence;
        }

        public void run() {
            for (RodPosition p : rodSequence) {
                if (cancelled) {
                    break;
                }
                sendRodPosition(p.thetaDeg, p.zDeg);
                try {
                    sleep(p.timeMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

    }

    /**
     * @return the zMin
     */
    public int getzMin() {
        return zMin;
    }

    /**
     * @param zMin the zMin to set
     */
    public void setzMin(int zMin) {
        if (zMin < 0) {
            zMin = 0;
        } else if (zMin > 180) {
            zMin = 180;
        }
        this.zMin = zMin;
        putInt("zMin", zMin);
    }

    /**
     * @return the zMax
     */
    public int getzMax() {
        return zMax;
    }

    /**
     * @param zMax the zMax to set
     */
    public void setzMax(int zMax) {
        if (zMax < 0) {
            zMax = 0;
        } else if (zMax > 180) {
            zMax = 180;
        }
        this.zMax = zMax;
        putInt("zMax", zMax);
    }

    /**
     * Base method to handle PropertyChangeEvent. It call super.propertyChange()
     * and then initFilter() when the AEChip size is changed, allowing filters
     * to allocate memory or do other initialization. Subclasses can override to
     * add more PropertyChangeEvent handling e.g from AEViewer or
     * AEFileInputStream.
     *
     * @param evt the PropertyChangeEvent, by jAER convention it is a constant
     * starting with EVENT_
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        switch (evt.getPropertyName()) {
            case EVENT_ROD_POSITION:
                RodPosition rodPosition = (RodPosition) evt.getNewValue();
                log.info("rodPosition=" + rodPosition.toString());
                sendRodPosition(rodPosition.thetaDeg, rodPosition.zDeg);
                break;
            case EVENT_ROD_SEQUENCE:
                RodSequence newSequnce = (RodSequence) evt.getNewValue();
                newSequnce.save();
                rodSequence = newSequnce;
                log.info("got new " + rodSequence);
                break;
            default:
        }
    }

    /**
     * @return the disableServos
     */
    public boolean isDisableServos() {
        return disableServos;
    }

    /**
     * @param disableServos the disableServos to set
     */
    public void setDisableServos(boolean disableServos) {
        if (disableServos) {
            disableServos();
        }
        this.disableServos = disableServos;
    }

}
