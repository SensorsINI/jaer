/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.stereo;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Marc
 */
public class StereoRecorder extends EventFilter2D implements FrameAnnotater {

    private int sx;
    private int sy;
    private int lastTimestamp = 0;

    private DavisChip apsChip = null;

    private SimpleDepthCameraViewerApplication depthViewerThread;
    File depthFile;

    //encapsulated fields
    private boolean startLoggingOnTimestampReset = true;
    private int waitSeconds = 5;
    private int recordSeconds = 3;

    private boolean actionTriggered = false;
    private boolean isRecording = false;
    private int actionTime = 0;
    private int recordingTime = 0;
    TextRenderer textRenderer = null;

    private FilterChain filterChain;

    public StereoRecorder(AEChip chip) {
        super(chip);
        BackgroundActivityFilter baFilter = new BackgroundActivityFilter(chip);
        RefractoryFilter rfFilter = new RefractoryFilter(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(baFilter);
        filterChain.add(rfFilter);
        setEnclosedFilterChain(filterChain);
        resetFilter();
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    @SuppressWarnings("empty-statement")
    synchronized public EventPacket filterPacket(EventPacket in) {
        getEnclosedFilterChain().filterPacket(in);

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }

            //trigger action (on ts reset)
            if ((e.timestamp < lastTimestamp) && (e.timestamp < 100000) && startLoggingOnTimestampReset) {
                log.info("****** ACTION TRIGGRED ******");

                //do something
                actionTriggered = true;
                actionTime = waitSeconds * 1000000;

                //sync frame readout
                apsChip.takeSnapshot();
            }

            //delay action for waitSeconds
            if (actionTriggered && (e.timestamp > (waitSeconds * 1000000))) {
                log.info("****** ACTION START (at " + (e.timestamp) + "us) ******");

                //start recording
                isRecording = true;
                recordingTime = recordSeconds * 1000000;
                chip.getAeViewer().startLogging();
                apsChip.setADCEnabled(true);
                //start depth recording
                if (depthViewerThread != null) {
                    depthFile = depthViewerThread.startRecording(System.getProperty("user.dir"));
                    if (depthFile == null) {
                        log.warning("Cannot record depth data...");
                    } else {
                        log.info("Saving depth data to temporary file: " + depthFile.getAbsolutePath());
                    }
                }

                //reset
                actionTriggered = false;
            } else if (actionTriggered) {
                //countdown
                int dt = e.timestamp - lastTimestamp;
                if (dt > 0) {
                    actionTime -= dt;
                }
            }

            if (isRecording) {
                //countdown
                int dt = e.timestamp - lastTimestamp;
                if (dt > 0) {
                    recordingTime -= dt;
                }
                //stop
                if (recordingTime < 0) {
                    isRecording = false;
                    boolean depthRecorded = false;
                    if (depthViewerThread != null) {
                        depthViewerThread.stopRecording();
                        depthRecorded = true;
                    }
                    chip.getAeViewer().stopLogging(true);
                    //move & save depth file
                    if (depthRecorded) {
                        JFileChooser j = new JFileChooser();
                        //j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        j.setSelectedFile(depthFile);
                        j.showSaveDialog(null);
                        depthFile.renameTo(j.getSelectedFile());
                    }
                }
            }

            //store last timestamp
            lastTimestamp = e.timestamp;
        }

        return in;
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {

        GL2 gl = drawable.getGL().getGL2();

        if (actionTriggered || isRecording) {
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 72));
            }
            textRenderer.begin3DRendering();
            String saz = "";
            if (actionTriggered) {
                textRenderer.setColor(0, 0, 1, 1);
                saz = String.format("Start recording in %d s", (actionTime + 1) / 1000000);
            } else if (isRecording) {
                textRenderer.setColor(1, 1, 0, 1);
                saz = String.format("Recording (%d s)", (recordingTime + 1) / 1000000);
            }
            Rectangle2D rect = textRenderer.getBounds(saz);
            final float scale = .25f;
            textRenderer.draw3D(saz, (chip.getSizeX() / 2) - (((float) rect.getWidth() * scale) / 2), chip.getSizeY() / 2, 0, scale); //
            textRenderer.end3DRendering();
        }

    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
        filterChain.reset();

    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        if (DavisChip.class.isAssignableFrom(chip.getClass())) {
            apsChip = (DavisChip) chip;
        }
    }

    /**
     * @return the startLoggingOnTimestampReset
     */
    public boolean isStartLoggingOnTimestampReset() {
        return startLoggingOnTimestampReset;
    }

    /**
     * @param startLoggingOnTimestampReset the startLoggingOnTimestampReset to
     * set
     */
    public void setStartLoggingOnTimestampReset(boolean startLoggingOnTimestampReset) {
        this.startLoggingOnTimestampReset = startLoggingOnTimestampReset;
    }

    /**
     * @return the waitSeconds
     */
    public int getWaitSeconds() {
        return waitSeconds;
    }

    /**
     * @param waitSeconds the waitSeconds to set
     */
    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    /**
     * @return the recordSeconds
     */
    public int getRecordSeconds() {
        return recordSeconds;
    }

    /**
     * @param recordSeconds the recordSeconds to set
     */
    public void setRecordSeconds(int recordSeconds) {
        this.recordSeconds = recordSeconds;
    }

    public void doDepthViewer() {
        try {
            System.load(System.getProperty("user.dir") + "\\jars\\openni2\\OpenNI2.dll");

            // initialize OpenNI
            OpenNI.initialize();

            List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
            if (devicesInfo.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No device is connected", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Device device = Device.open(devicesInfo.get(0).getUri());

            depthViewerThread = new SimpleDepthCameraViewerApplication(device);
            depthViewerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
