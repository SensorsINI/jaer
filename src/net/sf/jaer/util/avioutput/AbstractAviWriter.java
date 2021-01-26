/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.avioutput;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FilePermission;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.Date;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Base class for EventFilters that write out AVI files from jAER
 *
 * @author Tobi
 */
@Description("Base class for EventFilters that write out AVI files from jAER")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class AbstractAviWriter extends EventFilter2DMouseAdaptor implements FrameAnnotater, PropertyChangeListener {

    protected final int LOG_EVERY_THIS_MANY_FRAMES = 100; // for logging concole messages

    // writers, both express our VideoFrameWriterInterface for handling
    private VideoFrameWriterInterface videoOutputStream = null;
    protected AVIOutputStream.VideoFormat format = AVIOutputStream.VideoFormat.valueOf(getString("format", AVIOutputStream.VideoFormat.RAW.toString()));
    protected File frameSequenceOutputFolder = null;

    protected static String DEFAULT_FILENAME = "jAER.avi";
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected File lastFile = null;
    protected int framesWritten = 0;
    protected boolean writeTimecodeFile = getBoolean("writeTimecodeFile", true);
    protected static final String TIMECODE_SUFFIX = "-timecode.txt";
    protected File timecodeFile = null;
    protected FileWriter timecodeWriter = null;
    protected boolean closeOnRewind = getBoolean("closeOnRewind", true);
    private boolean rewindBeforeRecording = getBoolean("rewindBeforeRecording", true);
    protected boolean ignoreRewinwdEventFlag = false; // used to signal to igmore first rewind event for closing file on rewind if rewindBeforeRecording=true
    private boolean chipPropertyChangeListenerAdded = false;
    protected int maxFrames = getInt("maxFrames", 0);
    protected float compressionQuality = getFloat("compressionQuality", 0.9f);
    private String[] additionalComments = null;
    private int frameRate = getInt("frameRate", 30);
    private boolean saveFramesAsIndividualImageFiles = getBoolean("saveFramesAsIndividualImageFiles", false);
    private boolean writeOnlyWhenMousePressed = getBoolean("writeOnlyWhenMousePressed", false);
    protected volatile boolean writeEnabled = true;

    public enum OutputContainer {
        AVI, AnimatedGIF, ImageSequence
    }

    protected OutputContainer outputContainer = OutputContainer.valueOf(getString("outputContainer", OutputContainer.AVI.toString()));

    public AbstractAviWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("startRecordingAndSaveAs", "Opens the output file or folder and starts writing to it. The AVI file is in specified format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it. See saveFramesAsIndividualImageFiles to select a folder for the frames.");
        setPropertyTooltip("finishRecording", "Stops recording and closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
        setPropertyTooltip("rewindBeforeRecording", "rewinds file before recording");
        setPropertyTooltip("resizeWindowTo16To9Format", "resizes AEViewer window to 19:9 format");
        setPropertyTooltip("resizeWindowTo4To3Format", "resizes AEViewer window to 4:3 format");
        setPropertyTooltip("format", "<html>video file is writtent to this output format <br>(note that RLE will throw exception because OpenGL frames are not 4 or 8 bit images)<p>Use JPG for AVI if you want Adobe Premiere to be able to read the AVI.");
        setPropertyTooltip("outputContainer", "Choose the type of output file or files in a folder");
        setPropertyTooltip("maxFrames", "file is automatically closed after this many frames have been written; set to 0 to disable");
        setPropertyTooltip("framesWritten", "READONLY, shows number of frames written");
        setPropertyTooltip("compressionQuality", "In PNG or JPG format, sets compression quality; 0 is lowest quality and 1 is highest, 0.9 is default value");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written AVI file");
        setPropertyTooltip("frameRate", "Specifies frame rate of AVI file.");
        setPropertyTooltip("saveFramesAsIndividualImageFiles", "If selected, then the frames are saved as individual image files in the selected folder");
        setPropertyTooltip("writeOnlyWhenMousePressed", "If selected, then the frames are are saved only when the mouse is pressed in the AEViewer window");
        setPropertyTooltip("writeEnabled", "Selects if writing frames is enabled. Use this to temporarily disable output, or in conjunction with writeOnlyWhenMousePressed");
        setPropertyTooltip("saveFramesAsIndividualImageFiles", "Writes PNG image sequence to a selected folder rather than single AVI movie");
        chip.getSupport().addPropertyChangeListener(this);

    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!chipPropertyChangeListenerAdded) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
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

    public void doResizeWindowTo4To3Format() {
        resizeWindowTo(640, 480);

    }

    public void doResizeWindowTo16To9Format() {
        resizeWindowTo(640, 360);

    }

    private void resizeWindowTo(int w, int h) {
        if (getVideoOutputStream() != null) {
            log.warning("resizing disabled during recording to prevent AVI corruption");
            return;
        }
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            log.warning("No AEViewer");
            return;
        }
        GLCanvas c = (GLCanvas) (chip.getCanvas().getCanvas());
        if (c == null) {
            log.warning("No Canvas to resize");
            return;
        }
        int ww = c.getWidth(), hh = c.getHeight();
        int vw = v.getWidth(), vh = v.getHeight();
        v.setSize(w + (vw - ww), h + (vh - hh));
        v.revalidate();
        int ww2 = c.getWidth(), hh2 = c.getHeight();
        log.info(String.format("Canvas resized from %d x %d to %d x %d pixels", ww, hh, ww2, hh2));
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

    private static boolean isDirEmpty(final Path directory) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            Iterator<Path> itr = dirStream.iterator();
            if (!itr.hasNext()) {
                return true;
            }
            int count = 0;
            boolean hasDesktopIni = false;
            while (itr.hasNext()) {
                Path p = itr.next();
                hasDesktopIni = p.getFileName().toString().equals("desktop.ini");
                if (count++ > 1) {
                    return false;
                }
            }
            if (count == 1 && hasDesktopIni) {
                return true;
            }
            return false;
        }
    }

    synchronized public void doStartRecordingAndSaveAs() {
        if (saveFramesAsIndividualImageFiles) {
            if (frameSequenceOutputFolder != null) {
                JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "Folder " + frameSequenceOutputFolder + " is already opened for writing, close the recording first");
                return;
            }
            JFileChooser c = new JFileChooser(lastFileName);
            c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            c.setFileFilter(new FileFilter() {

                @Override
                public boolean accept(File f) {
                    return f.isDirectory();
                }

                @Override
                public String getDescription() {
                    return "Folder to save frames to";
                }
            });
            c.setSelectedFile(new File(lastFileName));
            int ret = c.showSaveDialog(getChip().getAeViewer().getFilterFrame());
            if (ret != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = c.getSelectedFile();

            lastFileName = selectedFile.toString();
            putString("lastFileName", lastFileName);

            if (selectedFile.exists() && selectedFile.isDirectory()) {
                try {
                    boolean isempty = isDirEmpty(selectedFile.toPath());
                    if (!isempty) {
                        int r = JOptionPane.showConfirmDialog(getChip().getAeViewer().getFilterFrame(), "Folder " + selectedFile.toString() + " is not empty, write to it?");
                        if (r != JOptionPane.OK_OPTION) {
                            return;
                        }
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "<html>Could not mkdir folder " + selectedFile);
                    return;
                }
            } else if (selectedFile.exists()) {
                try {
                    AccessController.checkPermission(new FilePermission(selectedFile.toString(), "read,write"));
                } catch (AccessControlException ex) {
                    JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "<html>Cannot write to folder " + selectedFile + ": <p> " + ex);
                    return;
                }
            } else if (!selectedFile.exists()) {
                if (!selectedFile.mkdir()) {
                    JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "<html>Could not mkdir folder " + selectedFile);
                    return;
                }
                log.info("created folder " + selectedFile);
            }
            if (!selectedFile.canWrite()) {
                JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "Cannot write to folder " + selectedFile);
                return;
            }
            frameSequenceOutputFolder = selectedFile;
            setVideoOutputStream(new ImageSequenceWriter(frameSequenceOutputFolder));
            if (rewindBeforeRecording) {
                ignoreRewinwdEventFlag = true;
                chip.getAeViewer().getAePlayer().rewind();
            }
        } else {
            if (getVideoOutputStream() != null) {
                JOptionPane.showMessageDialog(getChip().getAeViewer().getFilterFrame(), "video output stream is already opened");
                return;
            }
            JFileChooser c = new JFileChooser(lastFileName);
            c.setFileFilter(new FileFilter() {

                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || outputContainer == OutputContainer.AVI && f.getName().toLowerCase().endsWith(".avi") || outputContainer == OutputContainer.AnimatedGIF && f.getName().toLowerCase().endsWith(".gif");
                }

                @Override
                public String getDescription() {
                    return outputContainer.toString();
                }
            });
            c.setSelectedFile(new File(lastFileName));
            int ret = c.showSaveDialog(getChip().getAeViewer().getFilterFrame());
            if (ret != JFileChooser.APPROVE_OPTION) {
                return;
            }
            switch (outputContainer) {
                case AVI:

                    if (!c.getSelectedFile().getName().toLowerCase().endsWith(".avi")) {
                        String newName = c.getSelectedFile().toString() + ".avi";
                        c.setSelectedFile(new File(newName));
                    }
                    break;

                case AnimatedGIF:
                    if (!c.getSelectedFile().getName().toLowerCase().endsWith(".gif")) {
                        String newName = c.getSelectedFile().toString() + ".gif";
                        c.setSelectedFile(new File(newName));
                    }
                    break;
            }
            lastFileName = c.getSelectedFile().toString();
            File selectedFile = c.getSelectedFile();

            lastFileName = selectedFile.toString();
            putString("lastFileName", lastFileName);

            if (selectedFile.exists()) {
                int r = JOptionPane.showConfirmDialog(getChip().getAeViewer().getFilterFrame(), "File " + selectedFile.toString() + " already exists, overwrite it?");
                if (r != JOptionPane.OK_OPTION) {
                    return;
                }
            }
            setVideoOutputStream(openVideoOutputStream(selectedFile, additionalComments));
            if (rewindBeforeRecording) {
                ignoreRewinwdEventFlag = true;
                chip.getAeViewer().getAePlayer().rewind();
            }
        }
    }

    synchronized public void doFinishRecording() {
        if (getVideoOutputStream() != null) {
            try {
                getVideoOutputStream().close();
                setVideoOutputStream(null);
                if (timecodeWriter != null) {
                    timecodeWriter.close();
                    log.info("Closed timecode file " + timecodeFile.toString());
                    timecodeWriter = null;
                }
                setWriteEnabled(false);
                log.info("Closed " + lastFileName + " in format " + format + " with " + framesWritten + " frames");
            } catch (Exception ex) {
                log.warning(ex.toString());
                ex.printStackTrace();
                setVideoOutputStream(null);
            }
        }
        if (frameSequenceOutputFolder != null) {
            log.info("Finished recording frames to " + frameSequenceOutputFolder + " in format " + format + " with " + framesWritten + " frames");
            frameSequenceOutputFolder = null;
        }

    }

    /**
     * Opens AVI or AnimatedGIF output stream and optionally the timecode file, and enable
     * writing to this stream.
     *
     * @param f the file
     * @param additionalComments additional comments to be written to timecode
     * file, Comment header characters are added if not supplied.
     * @return the stream, or null if IOException occurs
     *
     */
    public VideoFrameWriterInterface openVideoOutputStream(File f, String[] additionalComments) {
        try {
            switch (outputContainer) {
                case AVI:
                    AVIOutputStream avi = new AVIOutputStream(f, format);
//            videoOutputStream.setFrameRate(chip.getAeViewer().getFrameRate());
                    avi.setFrameRate(frameRate);
                    avi.setVideoCompressionQuality(compressionQuality);
                    videoOutputStream = avi;
//            videoOutputStream.setVideoDimension(chip.getSizeX(), chip.getSizeY());
                    break;
                case AnimatedGIF:
                    ImageOutputStream outputStream = new FileImageOutputStream(f);
                    GifSequenceWriter gif = new GifSequenceWriter(outputStream, BufferedImage.TYPE_INT_BGR, 1000 / getFrameRate(), true);
                    videoOutputStream = gif;
                    break;
                case ImageSequence:
                    ImageSequenceWriter frameWriter = new ImageSequenceWriter(f);
                    videoOutputStream = frameWriter;
                    break;
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output", JOptionPane.WARNING_MESSAGE, null);
            return null;
        }
        try {
            if (writeTimecodeFile) {
                String s = f.toString().subSequence(0, f.toString().lastIndexOf(".")).toString() + TIMECODE_SUFFIX;
                timecodeFile = new File(s);
                timecodeWriter = new FileWriter(timecodeFile);
                timecodeWriter.write(String.format("# timecode file relating frames of AVI file to AER timestamps\n"));
                timecodeWriter.write(String.format("# written %s\n", new Date().toString()));
                if (additionalComments != null) {
                    for (String st : additionalComments) {
                        if (!st.startsWith("#")) {
                            st = "# " + st;
                        }
                        if (!st.endsWith("\n")) {
                            st = st + "\n";
                        }
                        timecodeWriter.write(st);
                    }
                }
                timecodeWriter.write(String.format("# frameNumber timestamp\n"));
                log.info("Opened timecode file " + timecodeFile.toString());
            }
        } catch (IOException e) {
            log.warning("Cannot open timecode file: " + e.toString());
        }
        log.info("Opened output file " + f.toString() + " with format " + format);
        setFramesWritten(0);
        getSupport().firePropertyChange("framesWritten", null, framesWritten);
        if (!isWriteOnlyWhenMousePressed()) {
            setWriteEnabled(true);
        }
        return videoOutputStream;
    }

    /**
     * @return the writeTimecodeFile
     */
    public boolean isWriteTimecodeFile() {
        return writeTimecodeFile;
    }

    /**
     * @param writeTimecodeFile the writeTimecodeFile to set
     */
    public void setWriteTimecodeFile(boolean writeTimecodeFile) {
        this.writeTimecodeFile = writeTimecodeFile;
        putBoolean("writeTimecodeFile", writeTimecodeFile);
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
        putBoolean("rewindBeforeRecording", closeOnRewind);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

    }

    /**
     * Returns true if either AVI or frame output is active
     *
     * @return true if active
     */
    protected boolean isRecordingActive() {
        return getVideoOutputStream() != null || frameSequenceOutputFolder != null;
    }

    /**
     * Helper method to write the frame either to AVI or file
     *
     * @param bufferedImage
     * @param timecode
     */
    protected void writeFrame(BufferedImage bufferedImage, int timecode) {
        if ((!isSaveFramesAsIndividualImageFiles() && getVideoOutputStream() == null)
                && (isSaveFramesAsIndividualImageFiles() && frameSequenceOutputFolder == null)) {
            return;
        }
        if (isWriteEnabled()) {
            try {
                getVideoOutputStream().writeFrame(bufferedImage);
                if (isWriteTimecodeFile()) {
                    writeTimecode(timecode);
                }
                incrementFramecountAndMaybeCloseOutput();
            } catch (Exception e) {
                log.warning("While writing frame, caught exception, closing file: " + e.toString());
                doFinishRecording();

            }
        }
    }

    private class ImageSequenceWriter implements VideoFrameWriterInterface {

        File folder;

        public ImageSequenceWriter(File folder) {
            this.folder = folder;
        }

        @Override
        public void close() throws IOException {
            log.info("nothing to close for image sequence");
        }

        @Override
        public void writeFrame(BufferedImage img) throws IOException {
            String fmt = getFormat().toString().toLowerCase();
            String filename = String.format("%05d.%s", framesWritten, fmt);
            String path = frameSequenceOutputFolder + File.separator + filename;
            File file = new File(path);
            ImageIO.write(img, fmt, file);
        }

    }

    /**
     * Turns gl to BufferedImage with fixed format
     *
     * @param gl
     * @param w
     * @param h
     * @return
     */
    protected BufferedImage toImage(GL2 gl, int w, int h) {

        gl.glReadBuffer(GL.GL_FRONT); // or GL.GL_BACK
        ByteBuffer glBB = Buffers.newDirectByteBuffer(4 * w * h);
        gl.glReadPixels(0, 0, w, h, GL2.GL_BGRA, GL.GL_BYTE, glBB);

        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int b = 2 * glBB.get();
                int g = 2 * glBB.get();
                int r = 2 * glBB.get();
                int a = glBB.get(); // not using

                bd[(h - y - 1) * w + x] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;
    }

    protected void writeTimecode(int timestamp) throws IOException {
        if (timecodeWriter != null) {
            timecodeWriter.write(String.format("%d %d\n", framesWritten, timestamp));
        }
    }

    protected void incrementFramecountAndMaybeCloseOutput() {
        if (++framesWritten % LOG_EVERY_THIS_MANY_FRAMES == 0) {
            log.info(String.format("wrote %d frames", framesWritten));
        }
        getSupport().firePropertyChange("framesWritten", null, framesWritten);
        if (maxFrames > 0 && framesWritten >= maxFrames) {
            log.info("wrote maxFrames=" + maxFrames + " frames; closing AVI file");
            doFinishRecording();
            if (chip.getAeViewer() != null) { // only show if interactive
                JOptionPane.showMessageDialog(chip.getAeViewer(), "Closed file " + lastFileName + " after " + framesWritten + " maxFrames frames were written");
            }
        }
    }

    /**
     * @return the format
     */
    public AVIOutputStream.VideoFormat getFormat() {
        return format;
    }

    /**
     * Set type of video encoding; see VideoFormat
     *
     * @param format the format to set
     */
    public void setFormat(AVIOutputStream.VideoFormat format) {
        this.format = format;
        putString("format", format.toString());
    }

    /**
     * @return the maxFrames
     */
    public int getMaxFrames() {
        return maxFrames;
    }

    /**
     * @param maxFrames the maxFrames to set
     */
    public void setMaxFrames(int maxFrames) {
        this.maxFrames = maxFrames;
        putInt("maxFrames", maxFrames);
    }

    /**
     * @return the framesWritten
     */
    public int getFramesWritten() {
        return framesWritten;
    }

    /**
     * @param framesWritten the framesWritten to set
     */
    public void setFramesWritten(int framesWritten) {
        int old = this.framesWritten;
        this.framesWritten = framesWritten;
        getSupport().firePropertyChange("framesWritten", old, framesWritten);
    }

    /**
     * @return the compressionQuality
     */
    public float getCompressionQuality() {
        return compressionQuality;
    }

    /**
     * @param compressionQuality the compressionQuality to set
     */
    public void setCompressionQuality(float compressionQuality) {
        if (compressionQuality < 0) {
            compressionQuality = 0;
        } else if (compressionQuality > 1) {
            compressionQuality = 1;
        }
        this.compressionQuality = compressionQuality;
        putFloat("compressionQuality", compressionQuality);
    }

    /**
     * Subclasses should override this method to write frames
     *
     * @param evt
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            if (!ignoreRewinwdEventFlag && closeOnRewind && getVideoOutputStream() != null) {
                doFinishRecording();
                JOptionPane.showMessageDialog(chip.getAeViewer(), "Closed file" + lastFileName + " on Rewind event after " + framesWritten + " frames were written");
            }
            ignoreRewinwdEventFlag = false;
        }
    }

    /**
     * @return the additionalComments
     */
    public String[] getAdditionalComments() {
        return additionalComments;
    }

    /**
     * Sets array of additional comment strings to be written to timecode file.
     *
     * @param additionalComments the additionalComments to set
     */
    public void setAdditionalComments(String[] additionalComments) {
        this.additionalComments = additionalComments;
    }

    /**
     * @return the frameRate
     */
    public int getFrameRate() {
        return frameRate;
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
     * @param frameRate the frameRate to set
     */
    public void setFrameRate(int frameRate) {
        if (frameRate < 1) {
            frameRate = 1;
        }
        this.frameRate = frameRate;
        putInt("frameRate", frameRate);
    }

//    /**
//     * @return the saveFramesAsIndividualImageFiles
//     */
//    public boolean isSaveFramesAsIndividualImageFiles() {
//        return saveFramesAsIndividualImageFiles;
//    }
//
//    /**
//     * @param saveFramesAsIndividualImageFiles the saveFramesAsIndividualImageFiles to set
//     */
//    public void setSaveFramesAsIndividualImageFiles(boolean saveFramesAsIndividualImageFiles) {
//        this.saveFramesAsIndividualImageFiles = saveFramesAsIndividualImageFiles;
//        putBoolean("saveFramesAsIndividualImageFiles",saveFramesAsIndividualImageFiles);
//    }
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
            log.info("mouse pressed, disabling writing");
            setWriteEnabled(false);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (writeOnlyWhenMousePressed) {
            log.info("mouse pressed, enabling writing");
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
     * @return the videoOutputStream
     */
    public VideoFrameWriterInterface getVideoOutputStream() {
        return videoOutputStream;
    }

    /**
     * @param videoOutputStream the videoOutputStream to set
     */
    public void setVideoOutputStream(VideoFrameWriterInterface videoOutputStream) {
        this.videoOutputStream = videoOutputStream;
    }

    /**
     * @return the saveFramesAsIndividualImageFiles
     */
    public boolean isSaveFramesAsIndividualImageFiles() {
        return saveFramesAsIndividualImageFiles;
    }

    /**
     * @param saveFramesAsIndividualImageFiles the
     * saveFramesAsIndividualImageFiles to set
     */
    public void setSaveFramesAsIndividualImageFiles(boolean saveFramesAsIndividualImageFiles) {
        this.saveFramesAsIndividualImageFiles = saveFramesAsIndividualImageFiles;
        putBoolean("saveFramesAsIndividualImageFiles", saveFramesAsIndividualImageFiles);
    }

    /**
     * @return the outputContainer
     */
    public OutputContainer getOutputContainer() {
        return outputContainer;
    }

    /**
     * @param outputContainer the outputContainer to set
     */
    public void setOutputContainer(OutputContainer outputContainer) {
        this.outputContainer = outputContainer;
        putString("outputContainer", this.outputContainer.toString());
    }

}
