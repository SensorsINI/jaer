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
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
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
    private boolean disableFishing = false;
    private final int SERIAL_WARNING_INTERVAL = 100;
    private int serialWarningCount = 0;

    public static final String EVENT_ROD_POSITION = "rodPosition", EVENT_ROD_SEQUENCE = "rodSequence", EVENT_CLEAR_SEQUENCES = "clearSequences";

    // rod control
    private int zMin = getInt("zMin", 80);
    private int zMax = getInt("zMax", 100);

    // fishing move
    RodDipper rodDipper = null;
    RodSequence[] rodSequences = {new RodSequence(0), new RodSequence(1)};
    private int currentRodsequence = 0;
    private float fishingHoleSwitchProbability = getFloat("fishingHoleSwitchProbability", 0.1f);
    private int fishingAttemptHoldoffMs = getInt("fishingAttemptHoldoffMs", 3000);
    private long lastFishingAttemptTimeMs = 0;

    private long lastManualRodControlTime = 0;
    private static final long HOLDOFF_AFTER_MANUAL_CONTROL_MS = 1000;

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
        setPropertyTooltip("disableFishing", "disable automatic fishing");
        setPropertyTooltip("fishingHoleSwitchProbability", "chance of switching spots after each attempt");
        setPropertyTooltip("fishingAttemptHoldoffMs", "holdoff time in ms between automatic fishing attempts");
        try {
            for (int i = 0; i < 2; i++) {
                rodSequences[i].load(i);
                log.info("loaded " + rodSequences.toString());
            }
        } catch (Exception e) {
            log.warning("Could not load fishing rod movement sequence: " + e.toString());
        }
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        in = getEnclosedFilterChain().filterPacket(in);
        long currentTimeMs = System.currentTimeMillis();
        if (roiRect != null && currentTimeMs - lastManualRodControlTime > HOLDOFF_AFTER_MANUAL_CONTROL_MS) {
            LinkedList<Cluster> clusterList = tracker.getVisibleClusters();
            for (Cluster c : clusterList) {
                if (roiRect.contains(c.getLocation())) {
                    // the ROI we drew contains one of the fish clusters
                    if (rodDipper == null || !rodDipper.isAlive()) {
                        log.info(String.format("Detected fish from cluster at location %s becoming contained by ROI rectangle %s", c.getLocation(), roiRect));
                        if (!disableFishing) {
                            dipRod(true);
                        }
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
        dipRod(false);
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
        if (!availableSerialPorts.contains(serialPortName)) {
            final String warningString = serialPortName + " is not in avaiable " + sb.toString();
            throw new IOException(warningString);
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            final String warningString = "null serial port returned when trying to open " + serialPortName + "; available " + sb.toString();
            throw new IOException(warningString);
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
                if (serialWarningCount++ == SERIAL_WARNING_INTERVAL) {
                    log.warning("couldn't open serial port " + serialPortName);
                    serialWarningCount = 0;
                }
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

    @Override
    public void cleanup() {
        disableServos();
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

    private void sendRodPosition(boolean disable, int theta, int z) {
        if (disableServos) {
            return;
        }
        if (checkSerialPort()) {

            z = (int) Math.floor(zMin + (((float) (zMax - zMin)) / 180) * z);
            try {

                // write theta (pan) and z (tilt) of fishing pole as two unsigned byte servo angles and degrees
                byte[] bytes = new byte[3];
                bytes[0] = disable ? (byte) 1 : (byte) 0;
                bytes[1] = (byte) (theta);
                bytes[2] = (byte) (z);
                serialPortOutputStream.write(bytes);
                serialPortOutputStream.flush();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void disableServos() {
        if (rodDipper != null) {
            rodDipper.abort();
        }
        sendRodPosition(true, 0, 0);
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
            fishingRodControlFrame.addPropertyChangeListener(EVENT_CLEAR_SEQUENCES, this);
        }
        fishingRodControlFrame.setVisible(true);
    }

    private void dipRod(boolean holdoff) {
        if (disableServos) {
            log.warning("servos disabled, will not run " + rodSequences);
            return;
        }
        if (rodDipper != null && rodDipper.isAlive()) {
            log.warning("already running rod dipper sequence");
            return;
        }

        if (!holdoff || (holdoff && System.currentTimeMillis() - lastFishingAttemptTimeMs > fishingAttemptHoldoffMs)) {
            Random r = new Random();
            int nextSeq = (currentRodsequence + 1) % 2;
            if (rodSequences[nextSeq].size() > 0 && r.nextFloat() <= fishingHoleSwitchProbability) {
                currentRodsequence = nextSeq;
                log.info("switched to " + rodSequences[currentRodsequence]);
            }
            rodDipper = new RodDipper(rodSequences[currentRodsequence]);
            log.info("running " + rodSequences.toString());

            rodDipper.start();
        }
    }

    private class RodDipper extends Thread {

        RodSequence rodSequence = null;
        volatile boolean aborted = false;

        public RodDipper(RodSequence rodSequence) {
            this.rodSequence = rodSequence;
        }

        public void run() {
            if (rodSequence == null || rodSequence.size() == 0) {
                log.warning("no sequence to play to dip rod");
                return;
            }
            Toolkit.getDefaultToolkit().beep();

            lastFishingAttemptTimeMs = System.currentTimeMillis();
            for (RodPosition p : rodSequence) {
                if (aborted) {
                    log.info("aborting rod sequence");
                    sendRodPosition(true, 0, 0);
                    break;
                }
                if (p.delayMsToNext > 0) {
                    try {
                        sleep(p.delayMsToNext); // sleep before move, first sleep is zero ms
                    } catch (InterruptedException e) {
                        log.info("interrupted");
                        break;
                    }
                }
                sendRodPosition(false, p.thetaDeg, p.zDeg);
            }
            if (!aborted) {
                RodPosition startingPosition = rodSequence.get(0);
                log.info("returning to starting position " + startingPosition);
                sendRodPosition(false, startingPosition.thetaDeg, startingPosition.zDeg);
            } else {
                disableServos();
            }
        }

        private void abort() {
            aborted = true;
            interrupt();
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
                lastManualRodControlTime = System.currentTimeMillis();
                sendRodPosition(false, rodPosition.thetaDeg, rodPosition.zDeg);
                break;
            case EVENT_ROD_SEQUENCE:
                RodSequence newSequnce = (RodSequence) evt.getNewValue();
                newSequnce.save();
                rodSequences[newSequnce.getIndex()] = newSequnce;
                log.info("got new " + rodSequences);
                break;
            case EVENT_CLEAR_SEQUENCES:
                for (RodSequence r : rodSequences) {
                    r.clear();
                    r.save();
                }
                log.info("Sequnces cleared");
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

    /**
     * @return the disableFishing
     */
    public boolean isDisableFishing() {
        return disableFishing;
    }

    /**
     * @param disableFishing the disableFishing to set
     */
    public void setDisableFishing(boolean disableFishing) {
        this.disableFishing = disableFishing;
    }

    /**
     * @return the fishingHoleSwitchProbability
     */
    public float getFishingHoleSwitchProbability() {
        return fishingHoleSwitchProbability;
    }

    /**
     * @param fishingHoleSwitchProbability the fishingHoleSwitchProbability to
     * set
     */
    public void setFishingHoleSwitchProbability(float fishingHoleSwitchProbability) {
        if (fishingHoleSwitchProbability > 1) {
            fishingHoleSwitchProbability = 1;
        } else if (fishingHoleSwitchProbability < 0) {
            fishingHoleSwitchProbability = 0;
        }
        this.fishingHoleSwitchProbability = fishingHoleSwitchProbability;
        putFloat("fishingHoleSwitchProbability", fishingHoleSwitchProbability);
    }

    /**
     * @return the fishingAttemptHoldoffMs
     */
    public int getFishingAttemptHoldoffMs() {
        return fishingAttemptHoldoffMs;
    }

    /**
     * @param fishingAttemptHoldoffMs the fishingAttemptHoldoffMs to set
     */
    public void setFishingAttemptHoldoffMs(int fishingAttemptHoldoffMs) {
        this.fishingAttemptHoldoffMs = fishingAttemptHoldoffMs;
        putFloat("fishingAttemptHoldoffMs", fishingAttemptHoldoffMs);
    }

}
