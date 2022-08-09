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

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import gnu.io.NRSerialPort;
import java.awt.Color;
import java.awt.Point;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import javafx.geometry.Point2D;
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
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * Going fishing game from Under the sea Lets go Fishing from Pressman
 *
 * @author tobid, Julie Hasler (juliehsler)
 */
@Description("Let's go fishing (going fishing) game from Telluride 2022 with Tobi Delbruck and Julie Hasler")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class GoingFishing extends EventFilter2DMouseROI implements FrameAnnotater {

    FilterChain chain = null;
    RectangularClusterTracker tracker = null;

    // serial port stuff
    NRSerialPort serialPort = null;
    private String serialPortName = getString("serialPortName", "COM3");
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;
    private boolean disableServos = false;
    private boolean disableFishing = false;
    private final int SERIAL_WARNING_INTERVAL = 100;
    private int serialWarningCount = 0;

    // ADC for FSR that might detect caught fish
    private int lastAdcValue = -1;
    private int caughtFishDetectorThreshold = getInt("caughtFishDetectorThreshold", 550);

    // PropertyChange events
    public static final String EVENT_ROD_POSITION = "rodPosition", EVENT_ROD_SEQUENCE = "rodSequence", EVENT_CLEAR_SEQUENCES = "clearSequences";

    // rod control
    private int zMin = getInt("zMin", 80);
    private int zMax = getInt("zMax", 100);

    // fishing rod dips
    RodDipper rodDipper = null;
    RodSequence[] rodSequences = {new RodSequence(0), new RodSequence(1)};
    private int currentRodsequence = 0;
    private float fishingHoleSwitchProbability = getFloat("fishingHoleSwitchProbability", 0.1f);
    private int fishingAttemptHoldoffMs = getInt("fishingAttemptHoldoffMs", 3000);
    private long lastFishingAttemptTimeMs = 0;
    private int rodReturnDurationMs = getInt("rodReturnDurationMs", 1000);

    private long lastManualRodControlTime = 0;
    private static final long HOLDOFF_AFTER_MANUAL_CONTROL_MS = 1000;
    private int lastRodTheta = -1;
    private int lastRodZ = -1;

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
        setPropertyTooltip("rodReturnDurationMs", "duration in ms of movement back to starting point of fishing rod sequence");
        setPropertyTooltip("caughtFishDetectorThreshold", "threshold ADC count from FSR to detect that we caught a fish");
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
        if (roiRects != null && !roiRects.isEmpty() && currentTimeMs - lastManualRodControlTime > HOLDOFF_AFTER_MANUAL_CONTROL_MS) {
            LinkedList<Cluster> clusterList = tracker.getVisibleClusters();
            for (Cluster c : clusterList) {
                Point p = new Point((int) c.getLocation().x, (int) c.getLocation().y);
                int roi = isInsideWhichROI(p);
                if (roi >= 0 && roi == currentRodsequence) {
                    // The ROI entered is the current fishing rod sequence one
                    // this ROI we drew contains a fish cluster
                    if (rodDipper == null || !rodDipper.isAlive()) {
                        log.info(String.format("Detected fish in ROI at location %s becoming contained by ROI rectangle %s", c.getLocation(), roiRects.get(roi)));
                        if (!disableFishing) {
                            dipRod(true);
                        }
                    }
                }
            }
        }
        lastAdcValue = -1;
        if (serialPortInputStream != null) {
            try {
                int navail = serialPortInputStream.available();
                if (navail >= 2) {
                    byte[] buf = new byte[navail];
                    serialPortInputStream.readFully(buf);
                    lastAdcValue = (int) ((buf[navail - 2] & 0xff) * 256 + (0xff & buf[navail - 1]));
//                    System.out.println(String.format("ADC: available=%d val=%d", navail, lastAdcValue));
                }
            } catch (IOException ex) {
                log.warning("serial port error while reading ADC value: " + ex.toString());
                closeSerial();
            }
        }
        return in;
    }

    /**
     * Annotates the display with GoingFishing stuff.
     *
     * @param drawable
     */
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final float adcY = chip.getSizeY() / 10;
        final float adcStartX = chip.getSizeX() / 10;
        if (lastAdcValue != -1) {
            gl.glLineWidth(5);
            gl.glColor3f(1, 1, 1);
            final float adcLen = .9f * chip.getSizeX() * (lastAdcValue / 1024f);
            DrawGL.drawLine(gl, adcStartX, adcY, adcLen, 0, 1);
            DrawGL.drawStrinDropShadow(gl, 12, adcStartX + adcLen + 2, adcY, 0, Color.white, Integer.toString(lastAdcValue));
        } else { 
            DrawGL.drawStrinDropShadow(gl, 12, adcStartX, adcY, 0, Color.white, "ADC: N/A");
        }
        if (isFishCaught()) {
            gl.glColor3f(1, 1, 1);
            DrawGL.drawStrinDropShadow(gl, 36, chip.getSizeX()/2, chip.getSizeY()/2, .5f, Color.white, "Caught!");
        }
        gl.glPopMatrix();
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
        checkBaudRate(serialBaudRate);
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
        serialPortInputStream = new DataInputStream(serialPort.getInputStream());
        while (serialPortInputStream.available() > 0) {
            serialPortInputStream.read();
        }
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
        if (serialPortInputStream != null) {
            try {
                serialPortInputStream.close();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            serialPortInputStream = null;
        }

        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
            serialPort = null;
        }
        lastRodTheta = -1;
        lastRodZ = -1;
        lastAdcValue = -1;
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
            checkBaudRate(serialBaudRate);
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    private void checkBaudRate(int serialBaudRate1) {
        if (serialBaudRate1 != 9600 && serialBaudRate1 != 115200) {
            showWarningDialogInSwingThread(String.format("Selected baud rate of %,d baud is neither 9600 or 115200, please check it", serialBaudRate1), "Invalid baud rate?");
            log.warning(String.format("Possible invalid baud rate %,d", serialBaudRate1));
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

    /**
     * Either turns off servos or sets servo positions
     *
     * @param disable true to turn off servos
     * @param theta otherwise the angle of pan (in degrees, 0-180)
     * @param z and tilt (in degrees, 0-180)
     */
    private void sendRodPosition(boolean disable, int theta, int z) {
        if (disableServos) {
            return;
        }
        if (checkSerialPort()) {

            int zSent = (int) Math.floor(zMin + (((float) (zMax - zMin)) / 180) * z);
            try {

                // write theta (pan) and z (tilt) of fishing pole as two unsigned byte servo angles and degrees
                byte[] bytes = new byte[3];
                bytes[0] = disable ? (byte) 1 : (byte) 0;
                bytes[1] = (byte) (theta);
                bytes[2] = (byte) (zSent);
                serialPortOutputStream.write(bytes);
                serialPortOutputStream.flush();
                lastRodTheta = theta;
                lastRodZ = z;
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void disableServos() {
        setDisableServos(true);
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

    private boolean isFishCaught() {
        return lastAdcValue > caughtFishDetectorThreshold;
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
                // move smoothly with minimum jerk back to starting position
                final RodPosition startingPosition = rodSequence.get(rodSequence.size() - 1);
                final RodPosition endingPosition = rodSequence.get(0);
                final Point2D start = new Point2D(startingPosition.thetaDeg, startingPosition.zDeg);
                final Point2D end = new Point2D(endingPosition.thetaDeg, endingPosition.zDeg);
                final double sampleRateHz = 100;
                final double dtS = 1 / sampleRateHz;
                final int dtMs = (int) Math.floor(dtS * 1000);
                final int dtRemNs = (int) (1e9 * (dtS - dtMs / 1000.));
                final ArrayList<Point2D> traj = minimumJerkTrajectory(start, end, sampleRateHz, rodReturnDurationMs);
                log.info(String.format("returning to starting position in %,d ms with minimum jerk trajectory of %d points", rodReturnDurationMs, traj.size()));
                for (Point2D p : traj) {
                    int theta = (int) p.getX();
                    int z = (int) p.getY();
                    sendRodPosition(false, theta, z);
                    try {
                        Thread.sleep(dtMs, dtRemNs);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
                sendRodPosition(false, endingPosition.thetaDeg, endingPosition.zDeg);
                log.info("done returning");
                lastFishingAttemptTimeMs = System.currentTimeMillis();
            } else {
                disableServos();
            }
        }

        private void abort() {
            aborted = true;
            interrupt();
        }

        /**
         * https://mika-s.github.io/python/control-theory/trajectory-generation/2017/12/06/trajectory-generation-with-a-minimum-jerk-trajectory.html
         */
        private ArrayList<Point2D> minimumJerkTrajectory(Point2D start, Point2D end,
                double sampleRateHz, int moveDurationMs) {
            int nPoints = (int) ((moveDurationMs / 1000.) * sampleRateHz);
            ArrayList<Point2D> trajectory = new ArrayList<Point2D>(nPoints);
            Point2D diff = end.subtract(start);
            for (int i = 1; i <= nPoints; i++) {
                double f = (double) i / nPoints;
                double fac = 10.0 * Math.pow(f, 3)
                        - 15.0 * Math.pow(f, 4)
                        + 6.0 * Math.pow(f, 5);
                Point2D d = diff.multiply(fac);
                Point2D nextPoint = start.add(d);
                trajectory.add(nextPoint);
            }
            return trajectory;
        }

    }// end of rod dipper thread

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
        resendRodPosition();
    }

    private void resendRodPosition() {
        if (lastRodTheta != -1 && lastRodZ != -1) {
            sendRodPosition(false, lastRodTheta, lastRodZ);
        }
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
        resendRodPosition();
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
        boolean old = this.disableServos;
        if (disableServos) {
            if (rodDipper != null) {
                rodDipper.abort();
            }
            sendRodPosition(true, 0, 0);
        }
        this.disableServos = disableServos;
        getSupport().firePropertyChange("disableServos", old, disableServos);
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
        putInt("fishingAttemptHoldoffMs", fishingAttemptHoldoffMs);
    }

    /**
     * @return the rodReturnDurationMs
     */
    public int getRodReturnDurationMs() {
        return rodReturnDurationMs;
    }

    /**
     * @param rodReturnDurationMs the rodReturnDurationMs to set
     */
    public void setRodReturnDurationMs(int rodReturnDurationMs) {
        this.rodReturnDurationMs = rodReturnDurationMs;
        putInt("rodReturnDurationMs", rodReturnDurationMs);
    }

    /**
     * @return the caughtFishDetectorThreshold
     */
    public int getCaughtFishDetectorThreshold() {
        return caughtFishDetectorThreshold;
    }

    /**
     * @param caughtFishDetectorThreshold the caughtFishDetectorThreshold to set
     */
    public void setCaughtFishDetectorThreshold(int caughtFishDetectorThreshold) {
        this.caughtFishDetectorThreshold = caughtFishDetectorThreshold;
        putInt("caughtFishDetectorThreshold", caughtFishDetectorThreshold);
    }

}
