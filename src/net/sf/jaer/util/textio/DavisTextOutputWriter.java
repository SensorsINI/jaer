/*
 * Copyright (C) 2018 tobid.
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
package net.sf.jaer.util.textio;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Desktop;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEViewer;

/**
 * "Writes out text format files with DVS and IMU data from DAVIS and DVS
 * cameras. Previous filtering affects the output. Output format is compatible
 * with http://rpg.ifi.uzh.ch/davis_data.html
 *
 * @author Tobi Delbruck
 */
@Description("<html>Writes out text format files with DVS and IMU data from DAVIS and DVS cameras."
        + " <p>Previous filtering affects the output. "
        + "<p>Output format is compatible with <a href=\"http://rpg.ifi.uzh.ch/davis_data.html \">rpg.ifi.uzh.ch/davis_data.html</a>")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisTextOutputWriter extends AbstractDavisTextIo implements PropertyChangeListener {

    private boolean dvsEvents = getBoolean("dvsEvents", true);
//    private boolean apsFrames = getBoolean("apsFrames", false);
    private boolean imuSamples = getBoolean("imuSamples", false);
    private ArrayList<PrintWriter> writers = new ArrayList();
    private PrintWriter dvsWriter = null, imuWriter = null, apsWriter = null, timecodeWriter = null;
    protected static String DEFAULT_FILENAME = "jAER.txt";
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected File lastFile = null;
    protected static final String TIMECODE_SUFFIX = "-timecode.txt";
    protected File timecodeFile = null;
    protected boolean closeOnRewind = getBoolean("closeOnRewind", true);
    protected boolean rewindBeforeRecording = getBoolean("rewindBeforeRecording", true);
    protected boolean ignoreRewinwdEventFlag = false; // used to signal to igmore first rewind event for closing file on rewind if rewindBeforeRecording=true
    private boolean chipPropertyChangeListenerAdded = false;
    protected int maxEvents = getInt("maxEvents", 0);
    protected ArrayList<String> additionalComments = new ArrayList();
    protected volatile boolean writeEnabled = false;

    public DavisTextOutputWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("startRecordingAndSaveAs", "Opens the output file and starts writing to it. The text file is in format timestamp x y polarity. Timestamp and polarity options are set by options useUsTimestamps and useSignedPolarity");
        setPropertyTooltip("closeFiles", "Closes the output file if it is open.");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
        setPropertyTooltip("rewindBeforeRecording", "rewinds file before recording");
        setPropertyTooltip("maxEvents", "file is automatically closed after this many events (in total, of any type) have been written; set to 0 to disable");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written file");
        setPropertyTooltip("writeOnlyWhenMousePressed", "If selected, then the events are are saved only when the mouse is pressed in the AEViewer window");
        setPropertyTooltip("writeEnabled", "Selects if writing events is enabled. Use this to temporarily disable output, or in conjunction with writeOnlyWhenMousePressed");
        chip.getSupport().addPropertyChangeListener(this);
        setPropertyTooltip("dvsEvents", "write dvs events as one per line with format one per line timestamp(us) x y polarity(0=off,1=on)");
        setPropertyTooltip("imuSamples", "write IMU samples as one per line with format one measurement per line: timestamp(us) ax(g) ay(g) az(g) gx(d/s) gy(d/s) gz(d/s)");
        setPropertyTooltip("apsFrames", "write APS frames with format TBD");
        additionalComments.add("jAER DAVIS/DVS camera text file output");

    }

    private int lastTimestampWritten = 0; // DEBUG nonmonotonic

    /**
     * Processes packet to write output
     *
     * @param in input packet
     * @return input packet
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!chipPropertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                chipPropertyChangeListenerAdded = true;
            }
        }
        if (!isWriteEnabled() || (!dvsEvents && !imuSamples /*&& !apsFrames*/)) {
            return in;
        }
        boolean davis = false;
//        try {
        Iterator itr = null;
        if (in instanceof ApsDvsEventPacket) {
            itr = ((ApsDvsEventPacket) in).fullIterator();
            davis = true;
        } else {
            itr = in.inputIterator();
            davis = false;
        }
        while (itr.hasNext()) { // skips events that have been filtered out
            BasicEvent be = (BasicEvent) itr.next();
            // we get all events, including IMU, DVS, and APS samples

            if (!davis) { // pure DVS
                PolarityEvent ae = (PolarityEvent) be;
                if (dvsEvents && dvsWriter != null) {
                    writeDvsEvent(ae);
                }
            } else { // davis type
                ApsDvsEvent ae = (ApsDvsEvent) be;
                if (dvsEvents && dvsWriter != null && ae.isDVSEvent()) {
                    writeDvsEvent(ae);
                } else if (imuSamples && imuWriter != null && ae.isImuSample()) {
                    IMUSample i = ae.getImuSample();
                    imuWriter.println(String.format("%d %f %f %f %f %f %f", ae.timestamp,
                            i.getAccelX(), i.getAccelY(), i.getAccelZ(),
                            i.getGyroTiltX(), i.getGyroYawY(), i.getGyroRollZ()));
                    incrementCountAndMaybeCloseOutput(be);
                }
            }

        }
        for (PrintWriter p : writers) {
            if (p.checkError()) {
                log.warning("Eror occured writing to file, closing all files");
                showWarningDialogInSwingThread("Eror occured writing to file, closing all files", "Error writing");
                doCloseFiles();
            }
        }
        return in;
    }

    private int polValue(Polarity p) {
        if (useSignedPolarity) {
            return p == Polarity.Off ? -1 : 1;
        } else {
            return p == Polarity.Off ? 0 : 1;
        }
    }

    private void writeDvsEvent(PolarityEvent ae) {
        // One event per line (timestamp x y polarity) as in RPG events.txt
        if (useCSV) {
            if (useUsTimestamps) {
                dvsWriter.println(String.format("%d,%d,%d,%d", ae.timestamp, ae.x, ae.y, polValue(ae.polarity)));

            } else {
                dvsWriter.println(String.format("%f,%d,%d,%d", 1e-6f * ae.timestamp, ae.x, ae.y, polValue(ae.polarity)));
            }
        } else {
            if (useUsTimestamps) {
                dvsWriter.println(String.format("%d %d %d %d", ae.timestamp, ae.x, ae.y, polValue(ae.polarity)));
            } else {
                dvsWriter.println(String.format("%f %d %d %d", 1e-6f * ae.timestamp, ae.x, ae.y, polValue(ae.polarity)));
            }
        }
        incrementCountAndMaybeCloseOutput(ae);
        lastTimestampWritten = ae.timestamp;
    }

    /**
     * Opens text output stream and optionally the timecode file, and enable
     * writing to this stream.
     *
     * @param f the file
     * @param additionalComments additional comments to be written to timecode
     * file, Comment header characters are added if not supplied.
     * @return the stream, or null if IOException occurs
     *
     */
    public PrintWriter openWriter(File f) throws IOException {
        PrintWriter writer = new PrintWriter(f);
        lastFile = f;
        setEventsProcessed(0);
        if (additionalComments != null) {
            for (String s : additionalComments) {
                writer.println("# " + s);
            }
        }
        writer.println("# created " + new Date().toString());
        writer.println("# source-file: " + (chip.getAeInputStream() != null ? chip.getAeInputStream().getFile().toString() : "(live input)"));
        log.info("Opened text output file " + f.toString() + " with text format");
        writers.add(writer);
        return writer;
    }

    /**
     * @return the dvsEvents
     */
    public boolean isDvsEvents() {
        return dvsEvents;
    }

    /**
     * @param dvsEvents the dvsEvents to set
     */
    public void setDvsEvents(boolean dvsEvents) {
        this.dvsEvents = dvsEvents;
        putBoolean("dvsEvents", dvsEvents);
    }

//    /**
//     * @return the apsFrames
//     */
//    public boolean isApsFrames() {
//        return apsFrames;
//    }
//
//    /**
//     * @param apsFrames the apsFrames to set
//     */
//    public void setApsFrames(boolean apsFrames) {
//        throw new UnsupportedOperationException("not yet implemented");
////        this.apsFrames = apsFrames;
////        putBoolean("apsFrames", apsFrames);
//    }
    /**
     * @return the imuSamples
     */
    public boolean isImuSamples() {
        return imuSamples;
    }

    /**
     * @param imuSamples the imuSamples to set
     */
    public void setImuSamples(boolean imuSamples) {
        this.imuSamples = imuSamples;
        putBoolean("imuSamples", imuSamples);
    }

    public void doShowFolderInDesktop() {
        if (!Desktop.isDesktopSupported()) {
            log.warning("Sorry, desktop operations are not supported");
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            File f = lastFile != null ? lastFile : new File(lastFileName);
            if (f.exists()) {
                desktop.open(f.getParentFile());
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    synchronized public void doStartRecordingAndSaveAs() {
        if (isFilesOpen()) {
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "writers are already opened; close them first");
            return;
        }
        if (!dvsEvents && !imuSamples /*&& !apsFrames*/) {
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "First select at least one of dvsEvents, imuSamples, apsFrames");
            return;
        }
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "text file";
            }
        });
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String basename = c.getSelectedFile().toString();
        if (basename.toLowerCase().endsWith(".txt")) {
            basename = basename.substring(0, basename.length() - 4);
        }
        if (basename.toLowerCase().endsWith("-events")) {
            basename = basename.substring(0, basename.length() - 7);
        }

        lastFileName = basename;
        putString("lastFileName", lastFileName);

        try {
            if (dvsEvents) {
                String fn = basename + "-events.txt";
                if (checkFileExists(fn)) {
                    dvsWriter = openWriter(new File(fn));
                    String tsStr = useUsTimestamps ? "timestamp(int32 us)" : "timestamp(float s)";
                    String polStr = useSignedPolarity ? "polarity(off/on=-1/+1)" : "polarity(off/on=0/1)";
                    dvsWriter.println(String.format("# dvs-events: One event per line:  %s x y %s", tsStr, polStr));
                }
            }
            if (imuSamples) {
                String fn = basename + "-imu.txt";
                if (checkFileExists(fn)) {
                    imuWriter = openWriter(new File(fn));
                    imuWriter.println("# imu-samples: One measurement per line: timestamp(us) ax(g) ay(g) az(g) gx(d/s) gy(d/s) gz(d/s)");
                }
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output file stream", JOptionPane.WARNING_MESSAGE, null);
        }

        if (rewindBeforeRecording) {
            ignoreRewinwdEventFlag = true;
            chip.getAeViewer().getAePlayer().rewind();
        }
        setWriteEnabled(true);
    }

    synchronized public void doCloseFiles() {
        setWriteEnabled(false);
        if (!isFilesOpen()) {
            log.info("No files open, nothing to close");
            setEventsProcessed(0);
            return;
        }
        try {
            for (PrintWriter f : getFileWriters()) {
                f.close();
            }
            getFileWriters().clear();
            dvsWriter = null;
            imuWriter = null;
            apsWriter = null;
            log.info("total " + eventsProcessed + " events were written to files " + lastFileName + "-XXX.txt");
            showPlainMessageDialogInSwingThread("Closed files " + lastFileName + " after " + eventsProcessed + " events were written", "Files closed");
            setEventsProcessed(0);
        } catch (Exception ex) {
            log.warning(ex.toString());
            ex.printStackTrace();
            getFileWriters().clear();
        }
    }

    /**
     * @return the rewindBeforeRecording
     */
    public boolean isRewindBeforeRecording() {
        return rewindBeforeRecording;
    }

    /**
     * @param rewindBeforeRecording the rewindBeforeRecording to set
     */
    public void setRewindBeforeRecording(boolean rewindBeforeRecording) {
        this.rewindBeforeRecording = rewindBeforeRecording;
        putBoolean("rewindBeforeRecording", rewindBeforeRecording);
    }

    protected void writeTimecode(int timestamp) throws IOException {
        if (timecodeWriter != null) {
            timecodeWriter.println(String.format("%d %d", eventsProcessed, timestamp));
        }
    }

    protected void incrementCountAndMaybeCloseOutput(BasicEvent be) {
        if (be.timestamp < lastTimestampWritten) {
            log.warning(String.format("nonmontonic timestamp written (previous timestamp %d, this timestamp %d, difference %d)",
                    lastTimestampWritten, be.timestamp, (be.timestamp - lastTimestampWritten)));
        }
        lastTimestampWritten = be.timestamp;
        eventsProcessed++;
        if (maxEvents > 0 && eventsProcessed >= maxEvents && isFilesOpen()) {
            log.info("wrote maxEvents=" + maxEvents + " events; closing files");
            doCloseFiles();
        }
        if (isFilesOpen() && eventsProcessed % LOG_EVERY_THIS_MANY_EVENTS == 0) {
            log.info(String.format("wrote %d events", eventsProcessed));
            getSupport().firePropertyChange("eventsWritten", null, eventsProcessed);
        }
    }

    private boolean isFilesOpen() {
        return !writers.isEmpty();
    }

    /**
     * @return the maxEvents
     */
    public int getMaxEvents() {
        return maxEvents;
    }

    /**
     * @param maxEvents the maxEvents to set
     */
    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
        putInt("maxEvents", maxEvents);
    }

    /**
     * @return the eventsProcessed
     */
    public int getEventsProcessed() {
        return eventsProcessed;
    }

    /**
     * @return the closeOnRewind
     */
    public boolean isCloseOnRewind() {
        return closeOnRewind;
    }

    /**
     * @param closeOnRewind the closeOnRewind to set
     */
    public void setCloseOnRewind(boolean closeOnRewind) {
        this.closeOnRewind = closeOnRewind;
        putBoolean("closeOnRewind", closeOnRewind);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            if (!ignoreRewinwdEventFlag && closeOnRewind && isFilesOpen()) {
                doCloseFiles();
            }
            ignoreRewinwdEventFlag = false;
        } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            if(getChip().getAeInputStream()==null){
                log.warning("getChip().getAeInputStream()==null; cannot add property change listener for rewind events");
                return;
            }
            getChip().getAeInputStream().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        }
    }

    /**
     * @return the additionalComments
     */
    public ArrayList<String> getAdditionalComments() {
        return additionalComments;
    }

    /**
     * Sets array of additional comment strings to be written to timecode file.
     *
     * @param additionalComments the additionalComments to set
     */
    public void setAdditionalComments(ArrayList<String> additionalComments) {
        this.additionalComments = additionalComments;
    }

    /**
     * Returns last file written
     *
     * @return the File written
     */
    public File getFile() {
        return lastFile;
    }

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean yes) {
        boolean old = this.writeEnabled;
        writeEnabled = yes;
        getSupport().firePropertyChange("writeEnabled", old, yes);
    }

    /**
     * @return the list of fileOutputStream
     */
    public ArrayList<PrintWriter> getFileWriters() {
        return writers;
    }

    /**
     * Returns true if the files doesn't exist or it is OK to overwrite it
     *
     * @param fn filename
     * @return true if OK to overwrite
     */
    private boolean checkFileExists(String fn) {
        if (new File(fn).exists()) {
            int r = JOptionPane.showConfirmDialog(null, "File " + fn + " already exists, overwrite it?");
            if (r == JOptionPane.OK_OPTION) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        if (getChip().getAeViewer() == null) {
            return;
        }
        getChip().getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
    }

}
