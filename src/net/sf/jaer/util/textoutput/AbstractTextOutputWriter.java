/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.textoutput;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Base class for EventFilters that write out text files from jaer
 *
 * @author Tobi
 */
@Description("Base class for EventFilters that write out text output files from jAER")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class AbstractTextOutputWriter extends EventFilter2DMouseAdaptor implements FrameAnnotater, PropertyChangeListener {

    protected final int LOG_EVERY_THIS_MANY_EVENTS = 1000; // for logging concole messages
    private FileWriter fileWriter = null;
    protected static String DEFAULT_FILENAME = "jAER.txt";
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected File lastFile = null;
    protected int eventsWritten = 0;
    protected static final String TIMECODE_SUFFIX = "-timecode.txt";
    protected File timecodeFile = null;
    protected FileWriter timecodeWriter = null;
    protected boolean closeOnRewind = getBoolean("closeOnRewind", true);
    protected boolean rewindBeforeRecording = getBoolean("rewindBeforeRecording", true);
    protected boolean ignoreRewinwdEventFlag = false; // used to signal to igmore first rewind event for closing file on rewind if rewindBeforeRecording=true
    private boolean chipPropertyChangeListenerAdded = false;
    protected int maxEvents = getInt("maxEvents", 0);
    protected ArrayList<String> additionalComments = new ArrayList();
    private boolean writeOnlyWhenMousePressed = getBoolean("writeOnlyWhenMousePressed", false);
    protected volatile boolean writeEnabled = false;

    public AbstractTextOutputWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("startRecordingAndSaveAs", "Opens the output file and starts writing to it. The text file is in format timestamp x y polarity, with polarity ");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
        setPropertyTooltip("rewindBeforeRecording", "rewinds file before recording");
        setPropertyTooltip("maxEvents", "file is automatically closed after this many events have been written; set to 0 to disable");
        setPropertyTooltip("eventsWritten", "READONLY, shows number of events written");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written file");
        setPropertyTooltip("writeOnlyWhenMousePressed", "If selected, then the events are are saved only when the mouse is pressed in the AEViewer window");
        setPropertyTooltip("writeEnabled", "Selects if writing events is enabled. Use this to temporarily disable output, or in conjunction with writeOnlyWhenMousePressed");
        chip.getSupport().addPropertyChangeListener(this);

    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!chipPropertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                chipPropertyChangeListenerAdded = true;
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
        if (getFileWriter() != null) {
            JOptionPane.showMessageDialog(null, "text file output stream " + getFileWriter().toString() + " is already opened");
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
        if (!c.getSelectedFile().getName().toLowerCase().endsWith(".txt")) {
            String newName = c.getSelectedFile().toString() + ".txt";
            c.setSelectedFile(new File(newName));
        }
        lastFileName = c.getSelectedFile().toString();

        if (c.getSelectedFile().exists()) {
            int r = JOptionPane.showConfirmDialog(null, "File " + c.getSelectedFile().toString() + " already exists, overwrite it?");
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        ArrayList<String> toRemove=new ArrayList();
        for(String s:additionalComments){
            if(s.startsWith("# source-file"))
                toRemove.add(s);
        }
        additionalComments.removeAll(toRemove);
        additionalComments.add("source-file: "+(chip.getAeInputStream()!=null?chip.getAeInputStream().getFile().toString():"(live input)"));
        setFileWriter(openTextOutputStream(c.getSelectedFile()));
        if (rewindBeforeRecording) {
            ignoreRewinwdEventFlag = true;
            chip.getAeViewer().getAePlayer().rewind();
        }
    }

    synchronized public void doCloseFile() {
        if (getFileWriter() != null) {
            try {
                getFileWriter().close();
                setFileWriter(null);
                if (timecodeWriter != null) {
                    timecodeWriter.close();
                    log.info("Closed timecode file " + timecodeFile.toString());
                    timecodeWriter = null;
                }
                setWriteEnabled(false);
                log.info("Closed " + lastFileName + " in text format with " + eventsWritten + " events");
            } catch (Exception ex) {
                log.warning(ex.toString());
                ex.printStackTrace();
                setFileWriter(null);
            }
        }

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
    public FileWriter openTextOutputStream(File f) {
        try {
            fileWriter = new FileWriter(f);
            lastFile = f;
            lastFileName = f.toString();
            putString("lastFileName", lastFileName);
            setEventsWritten(0);
            setWriteEnabled(true);
            if(additionalComments!=null) for(String s:additionalComments){
                fileWriter.write("# "+s+"\n");
            }
            log.info("Opened text output file " + f.toString() + " with text format");
            return fileWriter;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output file stream", JOptionPane.WARNING_MESSAGE, null);
            return null;
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

    @Override
    public void annotate(GLAutoDrawable drawable) {

    }

//    /**
//     * Turns gl to BufferedImage with fixed format
//     *
//     * @param gl
//     * @param w
//     * @param h
//     * @return
//     */
//    protected BufferedImage toImage(GL2 gl, int w, int h) {
//
//        gl.glReadBuffer(GL.GL_FRONT); // or GL.GL_BACK
//        ByteBuffer glBB = Buffers.newDirectByteBuffer(4 * w * h);
//        gl.glReadPixels(0, 0, w, h, GL2.GL_BGRA, GL.GL_BYTE, glBB);
//
//        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
//        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
//
//        for (int y = 0; y < h; y++) {
//            for (int x = 0; x < w; x++) {
//                int b = 2 * glBB.get();
//                int g = 2 * glBB.get();
//                int r = 2 * glBB.get();
//                int a = glBB.get(); // not using
//
//                bd[(h - y - 1) * w + x] = (b << 16) | (g << 8) | r | 0xFF000000;
//            }
//        }
//
//        return bi;
//    }
    protected void writeTimecode(int timestamp) throws IOException {
        if (timecodeWriter != null) {
            timecodeWriter.write(String.format("%d %d\n", eventsWritten, timestamp));
        }
    }

    protected void incrementCountAndMaybeCloseOutput() {
        if (++eventsWritten % LOG_EVERY_THIS_MANY_EVENTS == 0) {
            log.info(String.format("wrote %d events", eventsWritten));
            getSupport().firePropertyChange("eventsWritten", null, eventsWritten);
        }
        if (maxEvents > 0 && eventsWritten >= maxEvents) {
            log.info("wrote maxEvents=" + maxEvents + " events; closing file");
            doCloseFile();
            JOptionPane.showMessageDialog(chip.getAeViewer(), "Closed file " + lastFileName + " after " + eventsWritten + " maxEvents events were written");
        }
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
     * @return the eventsWritten
     */
    public int getEventsWritten() {
        return eventsWritten;
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

    /**
     * @param eventsWritten the eventsWritten to set
     */
    public void setEventsWritten(int eventsWritten) {
        int old = this.eventsWritten;
        this.eventsWritten = eventsWritten;
        if (eventsWritten % LOG_EVERY_THIS_MANY_EVENTS == 0) {
            getSupport().firePropertyChange("eventsWritten", old, eventsWritten);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            if (!ignoreRewinwdEventFlag && closeOnRewind && getFileWriter() != null) {
                doCloseFile();
                JOptionPane.showMessageDialog(chip.getAeViewer(), "Closed file" + lastFileName + " on Rewind event after " + eventsWritten + " events were written");
            }
            ignoreRewinwdEventFlag = false;
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

    /**
     * @return the writeOnlyWhenMousePressed
     */
    public boolean isWriteOnlyWhenMousePressed() {
        return writeOnlyWhenMousePressed;
    }

    /**
     * @param writeOnlyWhenMousePressed the writeOnlyWhenMousePressed to set
     */
    public void setWriteOnlyWhenMousePressed(boolean writeOnlyWhenMousePressed) {
        this.writeOnlyWhenMousePressed = writeOnlyWhenMousePressed;
        putBoolean("writeOnlyWhenMousePressed", writeOnlyWhenMousePressed);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (writeOnlyWhenMousePressed) {
            setWriteEnabled(false);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (writeOnlyWhenMousePressed) {
            setWriteEnabled(true);
        }
    }

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean yes) {
        boolean old = this.writeEnabled;
        writeEnabled = yes;
        getSupport().firePropertyChange("writeEnabled", old, yes);
        log.info("writeEnabled=" + writeEnabled);
    }

    /**
     * @return the fileOutputStream
     */
    public FileWriter getFileWriter() {
        return fileWriter;
    }

    /**
     * @param fileWriter the fileOutputStream to set
     */
    public void setFileWriter(FileWriter fileWriter) {
        this.fileWriter = fileWriter;
    }

}
