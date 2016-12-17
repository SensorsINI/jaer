/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.e2edriving;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.jogamp.opengl.GLAutoDrawable;
import eu.visualize.ini.convnet.DavisDeepLearnCnnProcessor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Reads Ford VI (vehicle interface) log files to display vehicle data over
 * recording
 *
 * @author tobi, jbinas
 */
public class FordVIVisualizer extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private long aeDatStartTimeMs = 0;
    private File fordViFile = null;
    String lastFordVIFile = getString("lastFordVIFile", System.getProperty("user.dir"));
    private TreeMap<Double, FordViState> fordViStates = null;
    BufferedInputStream fordViInputStream = null;

    private boolean addedPropertyChangeListener = false;
    private boolean showSteering = getBoolean("showSteering", true);
    private boolean showThrottleBrake = getBoolean("showThrottleBrake", true);
    private boolean showGPS = getBoolean("showGPS", true);

    int fileStartTs = 0;
    int lastTs = -1;

    public FordVIVisualizer(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener) {
            addedPropertyChangeListener = true;
            chip.getAeViewer().addPropertyChangeListener(this);
        }
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return in;
        }
        // find last car state
        if (fordViStates != null) {
            if (!in.isEmpty()) {
                lastTs = in.getLastTimestamp();
            }
            double tsMs1970 = (lastTs - fileStartTs) + aeDatStartTimeMs / 1000;
            Entry<Double, FordViState> lastEntry = fordViStates.lowerEntry(tsMs1970);
            if (lastEntry != null) {
                FordViState lastState = lastEntry.getValue();
                System.out.println(lastState);
            }

        }
        return in; // only annotates
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }

    private long parseDataStartTimeFromAeDatFile(AEFileInputStream aeis) throws FileNotFoundException, IOException {
        // # DataStartTime: System.currentTimeMillis() 1481800498468
        File f = aeis.getFile();
        LineNumberReader is = new LineNumberReader(new InputStreamReader(new FileInputStream(f)));
        while (is.getLineNumber() < 5000) {
            String line = is.readLine();
            if (line.contains("DataStartTime")) {
                Scanner s = new Scanner(line);
                s.next();
                s.next();
                s.next();
                aeDatStartTimeMs = s.nextLong();
                return aeDatStartTimeMs;
            }
        }
        log.warning("could not find data start time DataStartTime in AEDAT file");
        return -1;
    }

    synchronized public void doLoadFordVIDataFile() {

        JFileChooser c = new JFileChooser(lastFordVIFile);
        FileFilter filt = new FileNameExtensionFilter("Ford Vehicle Interface (VI) data file", "dat");
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(new File(lastFordVIFile));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFordVIFile = c.getSelectedFile().toString();
        putString("lastFordVIFile", lastFordVIFile);
        try {
            fordViFile = c.getSelectedFile();
            fordViInputStream = new BufferedInputStream(new FileInputStream(fordViFile));
            fordViStates = readJsonStream(fordViInputStream);

        } catch (Exception ex) {
            Logger.getLogger(DavisDeepLearnCnnProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load from this file, caught exception " + ex + ". See console for logging.", "Bad data file", JOptionPane.WARNING_MESSAGE);
        }

    }

    public TreeMap<Double, FordViState> readJsonStream(InputStream in) throws IOException {
        Gson gson = new Gson();
        FordViState fordViCurrentState = new FordViState(); // starting state, all unknown
        fordViStates = new TreeMap<Double, FordViState>(); // map to hold state on each change
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.setLenient(true);
        try {
            while (reader.hasNext()) {
                FordViMessage message = gson.fromJson(reader, FordViMessage.class);
                FordViState tmp = null;
                switch (message.name) {
                    case "steering_wheel_angle":
                        fordViCurrentState.steeringWheelAngle = ((Double) message.value).floatValue();
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "vehicle_speed":
                        fordViCurrentState.vehicleSpeed = ((Double) message.value).floatValue();
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "latitude":
                        fordViCurrentState.latitude = ((Double) message.value).floatValue();
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "longitude":
                        fordViCurrentState.longitude = ((Double) message.value).floatValue();
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "accelerator_pedal_position":
                        fordViCurrentState.acceleratorPedalPosition = ((Double) message.value).floatValue();
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "brake_pedal_status":
                        fordViCurrentState.brakePedalStatus = (boolean) message.value;
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    default:

                }
                if (tmp != null) {
                    fordViStates.put(message.timestamp, tmp);
                }
            }
        } catch (JsonSyntaxException e) {
            log.warning("caught " + e.toString() + "; maybe file ended with empty line?");
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(FordVIVisualizer.class.getName()).log(Level.SEVERE, null, ex);
        }
        reader.close();
        log.info(String.format("read %d messages", fordViStates.size()));
        return fordViStates;
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        if (pce.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            fileStartTs = chip.getAeInputStream().getFirstTimestamp();
            log.info("read starting AE timestamp in file as " + fileStartTs);
            try {
                aeDatStartTimeMs = parseDataStartTimeFromAeDatFile(chip.getAeViewer().getAePlayer().getAEInputStream());
            } catch (IOException ex) {
                log.info("could not read DataStartTime from AEDAT file: " + ex.toString());
            }
        }

    }

    /**
     * @return the showSteering
     */
    public boolean isShowSteering() {
        return showSteering;
    }

    /**
     * @param showSteering the showSteering to set
     */
    public void setShowSteering(boolean showSteering) {
        this.showSteering = showSteering;
        putBoolean("showSteering", showSteering);
    }

    /**
     * @return the showThrottleBrake
     */
    public boolean isShowThrottleBrake() {
        return showThrottleBrake;
    }

    /**
     * @param showThrottleBrake the showThrottleBrake to set
     */
    public void setShowThrottleBrake(boolean showThrottleBrake) {
        this.showThrottleBrake = showThrottleBrake;
        putBoolean("showThrottleBrake", showThrottleBrake);
    }

    /**
     * @return the showGPS
     */
    public boolean isShowGPS() {
        return showGPS;
    }

    /**
     * @param showGPS the showGPS to set
     */
    public void setShowGPS(boolean showGPS) {
        this.showGPS = showGPS;
        putBoolean("showGPS", showGPS);
    }

    public class FordViMessage {

        private double timestamp;
        private long timestampMs;
        private String name;
        private Object value;

        public FordViMessage(double timestamp, String name, Object value) {
            this.timestamp = timestamp;
            timestampMs = (long) timestamp / 1000;
            this.name = name;
            this.value = value;
        }

        /**
         * @return the timestamp
         */
        public double getTimestamp() {
            return timestamp;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @return the value
         */
        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("timestamp: %f, name: %s, value: %s", timestamp, name, value);
        }

    }

    public class FordViState implements Cloneable {

        float steeringWheelAngle = Float.NaN;
        float vehicleSpeed = Float.NaN;
        float latitude = Float.NaN;
        float longitude = Float.NaN;
        float acceleratorPedalPosition = Float.NaN;
        boolean brakePedalStatus = false;

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        @Override
        public String toString() {
            return "FordViState{" + "steeringWheelAngle=" + steeringWheelAngle + ", vehicleSpeed=" + vehicleSpeed + ", latitude=" + latitude + ", longtitude=" + longitude + ", acceleratorPedalPosition=" + acceleratorPedalPosition + ", brakePedalStatus=" + brakePedalStatus + '}';
        }

    }

}
