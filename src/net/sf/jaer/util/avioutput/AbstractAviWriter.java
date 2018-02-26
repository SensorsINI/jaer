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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
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
    protected AVIOutputStream aviOutputStream = null;
    protected static String DEFAULT_FILENAME = "jAER.avi";
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected int framesWritten = 0;
    protected boolean writeTimecodeFile = getBoolean("writeTimecodeFile", true);
    protected static final String TIMECODE_SUFFIX = "-timecode.txt";
    protected File timecodeFile = null;
    protected FileWriter timecodeWriter = null;
    protected boolean closeOnRewind = getBoolean("closeOnRewind", true);
    private boolean chipPropertyChangeListenerAdded = false;
    protected AVIOutputStream.VideoFormat format = AVIOutputStream.VideoFormat.valueOf(getString("format", AVIOutputStream.VideoFormat.RAW.toString()));
    protected int maxFrames = getInt("maxFrames", 0);
    protected float compressionQuality = getFloat("compressionQuality", 0.9f);
    private String[] additionalComments = null;
    private int frameRate=getInt("frameRate",30);
    private boolean saveFramesAsIndividualImageFiles=getBoolean("saveFramesAsIndividualImageFiles",false);
    private boolean writeOnlyWhenMousePressed=getBoolean("writeOnlyWhenMousePressed",false);
    protected volatile boolean writeEnabled=true;

    public AbstractAviWriter(AEChip chip) {
        super(chip);
        setPropertyTooltip("startRecordingAndSaveAVIAs", "Opens the output file and starts writing to it. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
        setPropertyTooltip("resizeWindowTo16To9Format", "resizes AEViewer window to 19:9 format");
        setPropertyTooltip("resizeWindowTo4To3Format", "resizes AEViewer window to 4:3 format");
        setPropertyTooltip("format", "video file is writtent to this output format (note that RLE will throw exception because OpenGL frames are not 4 or 8 bit images)");
        setPropertyTooltip("maxFrames", "file is automatically closed after this many frames have been written; set to 0 to disable");
        setPropertyTooltip("framesWritten", "READONLY, shows number of frames written");
        setPropertyTooltip("compressionQuality", "In PNG or JPG format, sets compression quality; 0 is lowest quality and 1 is highest, 0.9 is default value");
        setPropertyTooltip("showFolderInDesktop", "Opens the folder containging the last-written AVI file");
        setPropertyTooltip("frameRate", "Specifies frame rate of AVI file.");
        setPropertyTooltip("saveFramesAsIndividualImageFiles", "If selected, then the frames are saved as individual image files in the selected folder");
        setPropertyTooltip("writeOnlyWhenMousePressed", "If selected, then the frames are are saved only when the mouse is pressed in the AEViewer window");
        setPropertyTooltip("writeEnabled", "Selects if writing frames is enabled. Use this to temporarily disable output, or in conjunction with writeOnlyWhenMousePressed");
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

    public void doResizeWindowTo4To3Format() {
        resizeWindowTo(640, 480);

    }

    public void doResizeWindowTo16To9Format() {
        resizeWindowTo(640, 360);

    }

    private void resizeWindowTo(int w, int h) {
        if(aviOutputStream!=null){
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
        int vw=v.getWidth(), vh=v.getHeight();
        v.setSize(w + (vw-ww), h + (vh-hh));
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
            File f = new File(lastFileName);
            if (f.exists()) {
                desktop.open(f.getParentFile());
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    synchronized public void doStartRecordingAndSaveAVIAs() {
        if (aviOutputStream != null) {
            JOptionPane.showMessageDialog(null, "AVI output stream is already opened");
            return;
        }
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".avi");
            }

            @Override
            public String getDescription() {
                return "AVI (Audio Video Interleave) Microsoft video file";
            }
        });
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (!c.getSelectedFile().getName().toLowerCase().endsWith(".avi")) {
            String newName = c.getSelectedFile().toString() + ".avi";
            c.setSelectedFile(new File(newName));
        }
        lastFileName = c.getSelectedFile().toString();
        
        if (c.getSelectedFile().exists()) {
            int r = JOptionPane.showConfirmDialog(null, "File " + c.getSelectedFile().toString() + " already exists, overwrite it?");
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        openAVIOutputStream(c.getSelectedFile(), additionalComments);
    }

    synchronized public void doCloseFile() {
        if (aviOutputStream != null) {
            try {
                aviOutputStream.close();
                aviOutputStream = null;
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
                aviOutputStream = null;
            }
        }

    }

    /**
     * Opens AVI output stream and optionally the timecode file.
     *
     * @param f the file
     * @param additionalComments additional comments to be written to timecode
     * file, Comment header characters are added if not supplied.
     *
     */
    public void openAVIOutputStream(File f, String[] additionalComments) {
        try {
            aviOutputStream = new AVIOutputStream(f, format);
//            aviOutputStream.setFrameRate(chip.getAeViewer().getFrameRate());
            aviOutputStream.setFrameRate(frameRate);
            aviOutputStream.setVideoCompressionQuality(compressionQuality);
//            aviOutputStream.setVideoDimension(chip.getSizeX(), chip.getSizeY());
            lastFileName = f.toString();
            putString("lastFileName", lastFileName);
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
            log.info("Opened AVI output file " + f.toString() + " with format " + format);
            setFramesWritten(0);
            getSupport().firePropertyChange("framesWritten", null, framesWritten);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't create output file stream", JOptionPane.WARNING_MESSAGE, null);
            return;
        }
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

    @Override
    public void annotate(GLAutoDrawable drawable) {
        

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
            doCloseFile();
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (closeOnRewind && evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            doCloseFile();
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
     * @param frameRate the frameRate to set
     */
    public void setFrameRate(int frameRate) {
        if(frameRate<1)frameRate=1;
        this.frameRate = frameRate;
        putInt("frameRate",frameRate);
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
        putBoolean("writeOnlyWhenMousePressed",writeOnlyWhenMousePressed);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if(writeOnlyWhenMousePressed) setWriteEnabled(false);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if(writeOnlyWhenMousePressed) setWriteEnabled(true);
    }
    
    public boolean isWriteEnabled(){
        return writeEnabled;
    }
    
    public void setWriteEnabled(boolean yes){
        boolean old=this.writeEnabled;
        writeEnabled=yes;
        getSupport().firePropertyChange("writeEnabled",old, yes);
        log.info("writeEnabled="+writeEnabled);
    }
    
}
