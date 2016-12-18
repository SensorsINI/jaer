/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.e2edriving;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.visualize.ini.convnet.DavisDeepLearnCnnProcessor;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
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
import java.util.Date;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Reads Ford VI (vehicle interface) log files to display vehicle data over
 * recording
 *
 * @author tobi, jbinas
 */
@Description("Reads Ford VI (vehicle interface) log files to display vehicle data over recording")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FordVIVisualizer extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private File fordViFile = null;
    private String lastFordVIFile = getString("lastFordVIFile", System.getProperty("user.dir"));
    private BufferedInputStream fordViInputStream = null;
    private TreeMap<Double, FordViState> fordViStates = null;
    private FordViState lastFordViState = null;

    private long aeDatStartTimeMs = 0;

    private boolean addedPropertyChangeListener = false;
    private boolean showSteering = getBoolean("showSteering", true);
    private boolean showThrottleBrake = getBoolean("showThrottleBrake", true);
    private boolean showSpeedo = getBoolean("showSpeedo", true);
    private boolean showGPS = getBoolean("showGPS", true);
    private boolean showTime = getBoolean("showTime", true);
    private boolean showText = getBoolean("showText", true);

    int fileStartTs = 0;
    int lastTs = -1;
    private double MAX_TIME_DIFF_S_ALLOWED = 1; // max time difference allowed between FordVI message time and AE timestamp

    public FordVIVisualizer(AEChip chip) {
        super(chip);
        setPropertyTooltip("showText", "shows text overlay of FordVI data, if available and if timestamp offset is not too large");
        setPropertyTooltip("showSpeedo", "shows speedometer");
        setPropertyTooltip("showGPS", "shows GPS latitude/longitude");
        setPropertyTooltip("showSteering", "shows steering wheel angle");
        setPropertyTooltip("showThrottleBrake", "shows throttle/brake");
        setPropertyTooltip("showTime", "shows time/date from FordVI log file");
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
            double tsSSince1970 = (lastTs - fileStartTs) * 1e-6 + aeDatStartTimeMs / 1000;
            Entry<Double, FordViState> lastEntry = fordViStates.lowerEntry(tsSSince1970);
            if (lastEntry != null && Math.abs(lastEntry.getKey() - tsSSince1970) < MAX_TIME_DIFF_S_ALLOWED) {
                lastFordViState = lastEntry.getValue();
//                System.out.println(lastFordViState);
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

    GLU glu = null;
    GLUquadric quad;
    TextRenderer textRenderer = null;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (lastFordViState != null) {
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
                textRenderer.setColor(Color.blue);

            }
            GL2 gl = drawable.getGL().getGL2();
            gl.glColor3f(0, 0, 1);
            gl.glLineWidth(2);
            if (glu == null) {
                glu = new GLU();
            }
            if (quad == null) {
                quad = glu.gluNewQuadric();
            }

            if (showGPS) {
                final float x = chip.getSizeX() * .7f, y = (chip.getSizeY()) * .1f, scale = .25f;
                textRenderer.begin3DRendering();
                String s = String.format("GPS: %10.6f, %10.6f", lastFordViState.latitude, lastFordViState.longitude);
                Rectangle2D r = textRenderer.getBounds(s);
                textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
                textRenderer.end3DRendering();
            }
            if (showTime) {
                final float x = chip.getSizeX() * .7f, y = (chip.getSizeY()) * .05f, scale = .25f;
                textRenderer.begin3DRendering();
                String s = String.format("%s, dt=%4dms", new Date((long) lastFordViState.timestamp * 1000).toString(), (int) (lastFordViState.timestampDelta * 1000));
                Rectangle2D r = textRenderer.getBounds(s);
                textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
                textRenderer.end3DRendering();
            }

            if (showSpeedo) {
                final float x = chip.getSizeX() * .8f, y = (chip.getSizeY()) * .8f, scale = .5f;
                textRenderer.begin3DRendering();
                String s = String.format("%.0f km/h", lastFordViState.vehicleSpeed);
                Rectangle2D r = textRenderer.getBounds(s);
                textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
                textRenderer.end3DRendering();

//                final float radius = chip.getMinSize() * .1f;
//                // draw steering wheel
//                final float x = chip.getSizeX() * .8f, y = (chip.getSizeY()) * .8f;
//
//                gl.glPushMatrix();
//                {
//                    gl.glTranslatef(x, y, 0);
//                    glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
//                    glu.gluDisk(quad, radius, radius + 1, 32, 1);
//                }
//                gl.glPopMatrix();
//
//                // draw steering vector, including external radio input value
//                gl.glPushMatrix();
//                {
//                    gl.glTranslatef(x, y, 0);
//                    gl.glBegin(GL2.GL_LINES);
//                    {
//                        gl.glVertex2f(0, 0);
//                        double a = lastFordViState.vehicleSpeed/200; // -1 to 1
//                        float dx = radius * (float) Math.sin(a);
//                        float dy = radius * (float) Math.cos(a);
//                        gl.glVertex2f(dx, dy);
//                    }
//                    gl.glEnd();
//                }
//                gl.glPopMatrix();
            }
            if (showThrottleBrake) {
                final float x = chip.getSizeX() * .2f, y = (chip.getSizeY()) * .1f, scale = .4f;
                textRenderer.begin3DRendering();
                String s = null;
                if (lastFordViState.brakePedalStatus) {
                    s = String.format("Braking");
                } else {
                    s = String.format("Throttle: %3.0f%%", lastFordViState.acceleratorPedalPosition);

                }
                Rectangle2D r = textRenderer.getBounds(s);
                textRenderer.draw3D(s, (float) (x - scale * r.getWidth() / 2), (float) (y - scale * r.getHeight() / 2), 0, scale);
                textRenderer.end3DRendering();

            }
            if (showSteering && !Float.isNaN(lastFordViState.steeringWheelAngle)) {
                final float radius = chip.getMinSize() * .25f;
                // draw steering wheel
                final float x = chip.getSizeX() / 2, y = (chip.getSizeY()) / 2;
                gl.glPushMatrix();
                {
                    gl.glTranslatef(x, y, 0);
                    glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
                    glu.gluDisk(quad, radius, radius + 1, 32, 1);
                }
                gl.glPopMatrix();

                // draw steering vector, including external radio input value
                gl.glPushMatrix();
                {
                    gl.glTranslatef(x, y, 0);
                    gl.glBegin(GL2.GL_LINES);
                    {
                        gl.glVertex2f(0, 0);
                        double a = -Math.PI * lastFordViState.steeringWheelAngle / 180; // rad
                        float dx = radius * (float) Math.sin(a);
                        float dy = radius * (float) Math.cos(a);
                        gl.glVertex2f(dx, dy);
                    }
                    gl.glEnd();
                }
                gl.glPopMatrix();

            }
            if (showText) {
                MultilineAnnotationTextRenderer.setColor(Color.blue);
                MultilineAnnotationTextRenderer.setScale(.3f);
                MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
                MultilineAnnotationTextRenderer.renderMultilineString(lastFordViState.toString());
            }
        }
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
            fordViStates = readFordViJsonStream(fordViInputStream);

        } catch (Exception ex) {
            Logger.getLogger(DavisDeepLearnCnnProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load from this file, caught exception " + ex + ". See console for logging.", "Bad data file", JOptionPane.WARNING_MESSAGE);
        }

    }

    public TreeMap<Double, FordViState> readFordViJsonStream(InputStream in) throws IOException {
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
                        updateTime(message, fordViCurrentState);
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "vehicle_speed":
                        fordViCurrentState.vehicleSpeed = ((Double) message.value).floatValue();
                        updateTime(message, fordViCurrentState);
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "latitude":
                        fordViCurrentState.latitude = ((Double) message.value).floatValue();
                        updateTime(message, fordViCurrentState);
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "longitude":
                        fordViCurrentState.longitude = ((Double) message.value).floatValue();
                        updateTime(message, fordViCurrentState);
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "accelerator_pedal_position":
                        fordViCurrentState.acceleratorPedalPosition = ((Double) message.value).floatValue();
                        updateTime(message, fordViCurrentState);
                        tmp = (FordViState) fordViCurrentState.clone();
                        break;
                    case "brake_pedal_status":
                        fordViCurrentState.brakePedalStatus = (boolean) message.value;
                        updateTime(message, fordViCurrentState);
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
            log.info("read DataStartTime (start time of data recording in ms unix time since 1970) as " + aeDatStartTimeMs);
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

    /**
     * @return the showTime
     */
    public boolean isShowTime() {
        return showTime;
    }

    /**
     * @param showTime the showTime to set
     */
    public void setShowTime(boolean showTime) {
        this.showTime = showTime;
        putBoolean("showTime", showTime);
    }

    /**
     * @return the showSpeedo
     */
    public boolean isShowSpeedo() {
        return showSpeedo;
    }

    /**
     * @return the showText
     */
    public boolean isShowText() {
        return showText;
    }

    /**
     * @param showText the showText to set
     */
    public void setShowText(boolean showText) {
        this.showText = showText;
        putBoolean("showText", showText);
    }

    /**
     * @param showSpeedo the showSpeedo to set
     */
    public void setShowSpeedo(boolean showSpeedo) {
        this.showSpeedo = showSpeedo;
        putBoolean("showSpeedo", showSpeedo);
    }

    private void updateTime(FordViMessage message, FordViState state) {
        double old = state.timestamp;
        state.timestamp = message.timestamp;
        state.timestampDelta = state.timestamp - old;
    }

    public class FordViMessage {

        private double timestamp; // The timestamp is in UNIX time (i.e. seconds since the UNIX epoch, 00:00:00 UTC, 1/1/1970). https://github.com/openxc/openxc-message-format
        private String name;
        private Object value;

        public FordViMessage(double timestamp, String name, Object value) {
            this.timestamp = timestamp;
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

        double timestamp = 0;
        double timestampDelta = 0;
        float steeringWheelAngle = Float.NaN; // in degrees, CCW is positive 0 is straight
        float vehicleSpeed = Float.NaN; // in km/h I think
        float latitude = Float.NaN; // in deg?
        float longitude = Float.NaN; // deg?
        float acceleratorPedalPosition = Float.NaN; // 0-1??
        boolean brakePedalStatus = false;

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        @Override
        public String toString() {
            return "FordViState:" + "\n steeringWheelAngle=" + steeringWheelAngle + "\n vehicleSpeed=" + vehicleSpeed + "\n latitude=" + latitude + "\n longtitude=" + longitude + "\n acceleratorPedalPosition=" + acceleratorPedalPosition + "\n brakePedalStatus=" + brakePedalStatus;
        }

    }

}
