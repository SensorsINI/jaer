package net.sf.jaer.util.avioutput;

import static net.sf.jaer.graphics.AEViewer.DEFAULT_CHIP_CLASS;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.npp.TargetLabeler;
import ch.unizh.ini.jaer.projects.npp.TargetLabeler.TargetLocation;
import eu.seebetter.ini.chips.DavisChip;
import java.io.EOFException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import ml.options.Options;
import ml.options.Options.Multiplicity;
import ml.options.Options.Separator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.LoggingAnsiColorConsoleFormatter;
import net.sf.jaer.util.avioutput.AVIOutputStream.VideoFormat;
import net.sf.jaer.util.avioutput.DvsFramer.TimeSliceMethod;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Writes out AVI movie with DVS time or event slices as AVI frame images with
 * desired output resolution
 *
 * @author Tobi Delbruck
 */
@Description("Writes out AVI movie with DVS constant-number-of-event subsampled 2D histogram slices as AVI frame images with desired output resolution")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DvsSliceAviWriter extends AbstractAviWriter implements FrameAnnotater {

    private DvsFramerSingleFrame dvsFrame = null;
    private ApsFrameExtractor frameExtractor = null;
    private TargetLabeler targetLabeler = null;
    private float frameRateEstimatorTimeConstantMs = getFloat("frameRateEstimatorTimeConstantMs", 10f);
    private JFrame frame = null;
    public ImageDisplay display;
    private boolean showOutput;
    private volatile boolean newApsFrameAvailable = false;
    private int endOfFrameTimestamp = Integer.MIN_VALUE, lastTimestamp = 0;
    protected boolean writeDvsSliceImageOnApsFrame = getBoolean("writeDvsSliceImageOnApsFrame", false);
    protected boolean writeDvsFrames = getBoolean("writeDvsFrames", true);
    protected boolean writeApsFrames = getBoolean("writeApsFrames", false);
//    protected boolean writeLatestApsDvsTogether = getBoolean("writeLatestApsDvsTogether", false); // happens anyhow since memory of image buffer is never cleared
    protected boolean writeAPSDVSToRGChannels = getBoolean("writeAPSDVSToRGChannels", false); // happens anyhow since memory of image buffer is never cleared
    private boolean writeTargetLocations = getBoolean("writeTargetLocations", true);
    private boolean writeDvsEventsToTextFile = getBoolean("writeDvsEventsToTextFile", false);
    protected static final String EVENTS_SUFFIX = "-events.txt";
    protected FileWriter eventsWriter = null;
    protected File eventsFile = null;
    protected File targetLocationsFile = null;
    private FileWriter targetLocationsWriter = null;
    private boolean rendererPropertyChangeListenerAdded = false;
    private LowpassFilter lowpassFilter = new LowpassFilter(frameRateEstimatorTimeConstantMs);
    private int lastDvsFrameTimestamp = 0;
    private float avgDvsFrameIntervalMs = 0;
    private boolean showStatistics = getBoolean("showStatistics", false);
    private String TARGETS_LOCATIONS_SUFFIX = "-targetLocations.txt";
    private String APS_OUTPUT_SUFFIX = "-aps.avi";
    private String DVS_OUTPUT_SUFFIX = "-dvs.avi"; // used for separate output option
    private BufferedImage aviOutputImage = null; // holds either dvs or aps or both iamges

    public DvsSliceAviWriter(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        if (chip instanceof DavisChip) {
            frameExtractor = new ApsFrameExtractor(chip);
            chain.add(frameExtractor);
            frameExtractor.getSupport().addPropertyChangeListener(this);
        }
        targetLabeler = new TargetLabeler(chip);
        chain.add(targetLabeler);
        dvsFrame = new DvsFramerSingleFrame(chip);
        chain.add(dvsFrame);
        setEnclosedFilterChain(chain);
        showOutput = getBoolean("showOutput", true);
        DEFAULT_FILENAME = "DvsEventSlices.avi";
        setPropertyTooltip("showOutput", "shows output in JFrame/ImageDisplay");
        setPropertyTooltip("frameRateEstimatorTimeConstantMs", "time constant of lowpass filter that shows average DVS slice frame rate");
        setPropertyTooltip("writeDvsSliceImageOnApsFrame", "<html>write DVS slice image for each APS frame end event (dvsMinEvents ignored).<br>The frame is written at the end of frame APS event.<br><b>Warning: to capture all frames, ensure that playback time slices are slow enough that all frames are rendered</b>");
        setPropertyTooltip("writeApsFrames", "<html>write APS frames to file. Frame is written to LEFT half of image if DVS frames also on.<br><b>Warning: to capture all frames, ensure that playback time slices are slow enough that all frames are rendered</b>");
        setPropertyTooltip("writeDvsFrames", "<html>write DVS frames to file. Frame is written to RIGHT half of image if APS frames also on.<br>");
//        setPropertyTooltip("writeLatestApsDvsTogether", "<html>If writeDvsFrames and writeApsFrames, ,<br>duplicate most recent frame from each modality in each AVI file output frame. <br>If off, other half frame is black (all zeros|)<br>");
        setPropertyTooltip("writeAPSDVSToRGChannels", "<html>If writeDvsFrames and writeApsFrames, ,<br>writes to RG of RGB channels on single frame size output. <br>If off, outputs are written to left (APS) and right (DVS)<br>");
        setPropertyTooltip("writeTargetLocations", "<html>If TargetLabeler has locations, write them to a file named XXX-targetlocations.txt<br>");
        setPropertyTooltip("writeDvsEventsToTextFile", "<html>write DVS events to text file, one event per line, timestamp, x, y, pol<br>");
        setPropertyTooltip("showStatistics", "shows statistics of DVS frame (most off and on counts, frame rate, sparsity)");
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        super.filterPacket(in);
        if (frameExtractor != null) {
            frameExtractor.filterPacket(in); // process frame extractor, target labeler and dvsframer, should generate property change events that make us write APS frames
        }
        if (targetLabeler != null) {
            targetLabeler.filterPacket(in);
        }
        dvsFrame.checkParameters();
        if (aviOutputImage == null || aviOutputImage.getWidth() != getOutputImageWidth() || aviOutputImage.getHeight() != getOutputImageHeight()) {
            aviOutputImage = new BufferedImage(getOutputImageWidth(),
                    getOutputImageHeight(), BufferedImage.TYPE_INT_BGR);
        }

        checkSubsampler();
        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            PolarityEvent p = (PolarityEvent) e;
            lastTimestamp = e.timestamp;
            dvsFrame.addEvent(p);
            try {
                writeEvent(p);
            } catch (IOException ex) {
                log.warning("Could not write output event: " + ex.toString());
                doFinishRecording();
            }
            if ((writeDvsSliceImageOnApsFrame && newApsFrameAvailable && (e.timestamp >= endOfFrameTimestamp))
                    || ((!writeDvsSliceImageOnApsFrame && dvsFrame.getDvsFrame().isFilled())
                    && ((chip.getAeViewer() == null) || !chip.getAeViewer().isPaused()))) { // added check for nonnull aeviewer in case filter is called from separate program
                if (writeDvsSliceImageOnApsFrame) {
                    newApsFrameAvailable = false;
                }
                dvsFrame.normalizeFrame();
                maybeShowOutput(dvsFrame);
                if (isWriteDvsFrames() && (getVideoOutputStream() != null) && isWriteEnabled()) {
                    BufferedImage bi = toImage(dvsFrame);
                    try {
                        writeTimecode(e.timestamp);
                        writeTargetLocation(e.timestamp, framesWritten);
                        getVideoOutputStream().writeFrame(bi);
                        incrementFramecountAndMaybeCloseOutput();
                    } catch (IOException ex) {
                        doFinishRecording();
                        log.warning(ex.toString());
                        ex.printStackTrace();
                        setFilterEnabled(false);
                    }
                }
//                dvsFrame.clear();  // already cleared by next event when next event added
                if (lastDvsFrameTimestamp != 0) {
                    int lastFrameInterval = lastTimestamp - lastDvsFrameTimestamp;
                    avgDvsFrameIntervalMs = 1e-3f * lowpassFilter.filter(lastFrameInterval, lastTimestamp);
                }
                lastDvsFrameTimestamp = lastTimestamp;
            }
        }
        if (writeDvsSliceImageOnApsFrame && ((lastTimestamp - endOfFrameTimestamp) > 1000000)) {
            log.warning("last frame event was received more than 1s ago; maybe you need to enable Display Frames in the User Control Panel?");
        }
        return in;
    }

    public DvsFramerSingleFrame getDvsFrame() {
        return dvsFrame;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (dvsFrame == null) {
            return;
        }
        if (!isShowStatistics()) {
            return;
        }
        dvsFrame.setShowFrames(isWriteDvsFrames());
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .8f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        float avgFrameRate = avgDvsFrameIntervalMs == 0 ? Float.NaN : 1000 / avgDvsFrameIntervalMs;
        String s = null;
        if (dvsFrame.isNormalizeFrame()) {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f\nsparsity=%.2f%%", dvsFrame.getMostOffCount(), dvsFrame.getMostOnCount(), avgFrameRate, 100 * dvsFrame.getSparsity());
        } else {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f", dvsFrame.getMostOffCount(), dvsFrame.getMostOnCount(), avgFrameRate);
        }
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    @Override
    public synchronized void doStartRecordingAndSaveAs() {
        String[] s = {
            "width=" + dvsFrame.getOutputImageWidth(),
            "height=" + dvsFrame.getOutputImageHeight(),
            "timeslicemethod=" + dvsFrame.getTimeSliceMethod().toString(),
            "grayScale=" + dvsFrame.getDvsGrayScale(),
            "normalize=" + dvsFrame.isNormalizeFrame(),
            "normalizeDVSForZsNullhop=" + dvsFrame.isNormalizeDVSForZsNullhop(),
            "dvsMinEvents=" + dvsFrame.getDvsEventsPerFrame(),
            "timeDurationUsPerFrame=" + dvsFrame.getTimeDurationUsPerFrame(),
            "format=" + format.toString(),
            "compressionQuality=" + compressionQuality
        };
        setAdditionalComments(s);
        if (getVideoOutputStream() != null) {
            JOptionPane.showMessageDialog(null, "AVI output stream is already opened");
            return;
        }
        JFileChooser c = new JFileChooser(lastFile);
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
        // above chooses base filename. Now we need to see if we just write to this file, or make two files for separate DVS and APS outputs
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
        lastFile = c.getSelectedFile();
        setVideoOutputStream(openVideoOutputStream(c.getSelectedFile(), getAdditionalComments()));
        openEventsTextFile(c.getSelectedFile(), getAdditionalComments());
        openTargetLabelsFile(c.getSelectedFile(), getAdditionalComments());
        if (isRewindBeforeRecording()) {
            ignoreRewinwdEventFlag = true;
            chip.getAeViewer().getAePlayer().rewind();
            ignoreRewinwdEventFlag = true;
        }

    }

    private int getOutputImageWidth() {
        return (!isWriteAPSDVSToRGChannels() && isWriteApsFrames() && isWriteDvsFrames()) ? dvsFrame.getOutputImageWidth() * 2 : dvsFrame.getOutputImageWidth(); // 2X wide if both outputs and not RG channels
    }

    private int getOutputImageHeight() {
        return dvsFrame.getOutputImageHeight();
//        return (isWriteApsFrames() && isWriteDvsFrames()) ? dvsFrame.getOutputImageHeight() : dvsFrame.getOutputImageHeight();  // same height if both outputs on or not
    }

    private int getDvsStartingX() {
        return (!isWriteAPSDVSToRGChannels() && isWriteApsFrames() && isWriteDvsFrames()) ? dvsFrame.getOutputImageWidth() : 0;
    }

    protected void writeEvent(PolarityEvent e) throws IOException {
        if (eventsWriter != null) {
            eventsWriter.write(String.format("%d,%d,%d,%d\n", e.timestamp, e.x, e.y, e.getPolaritySignum()));
        }
    }

    private void openTargetLabelsFile(File f, String[] additionalComments) {
        if (writeTargetLocations) {
            try {
                String s = null;
                if (f.toString().lastIndexOf(".") == -1) {
                    s = f.toString() + TARGETS_LOCATIONS_SUFFIX;
                } else {
                    s = f.toString().subSequence(0, f.toString().lastIndexOf(".")).toString() + TARGETS_LOCATIONS_SUFFIX;
                }
                targetLocationsFile = new File(s);
                targetLocationsWriter = new FileWriter(targetLocationsFile);
                targetLocationsWriter.write(String.format("# labeled target locations file\n"));
                targetLocationsWriter.write(String.format("# written %s\n", new Date().toString()));
                if (additionalComments != null) {
                    for (String st : additionalComments) {
                        if (!st.startsWith("#")) {
                            st = "# " + st;
                        }
                        if (!st.endsWith("\n")) {
                            st = st + "\n";
                        }
                        targetLocationsWriter.write(st);
                    }
                }
                targetLocationsWriter.write(String.format("# frameNumber timestamp x y targetTypeID width height\n"));
                log.info("Opened labeled target locations file " + targetLocationsFile.toString());
            } catch (IOException ex) {
                Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    private void openEventsTextFile(File f, String[] additionalComments) {
        if (writeDvsEventsToTextFile) {
            try {
                String s = null;
                if (f.toString().lastIndexOf(".") == -1) {
                    s = f.toString() + EVENTS_SUFFIX;
                } else {
                    s = f.toString().subSequence(0, f.toString().lastIndexOf(".")).toString() + EVENTS_SUFFIX;
                }
                eventsFile = new File(s);
                eventsWriter = new FileWriter(eventsFile);
                eventsWriter.write(String.format("# events file\n"));
                eventsWriter.write(String.format("# written %s\n", new Date().toString()));
                if (additionalComments != null) {
                    for (String st : additionalComments) {
                        if (!st.startsWith("#")) {
                            st = "# " + st;
                        }
                        if (!st.endsWith("\n")) {
                            st = st + "\n";
                        }
                        eventsWriter.write(st);
                    }
                }
                eventsWriter.write(String.format("# timestamp,x,y,pol\n"));
                log.info("Opened events file " + eventsFile.toString());
            } catch (IOException ex) {
                Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    @Override
    public synchronized void doFinishRecording() {
        setWriteEnabled(false);
        if (eventsWriter != null) {
            try {
                eventsWriter.close();
                log.info("Closed events file " + eventsFile.toString());
                eventsWriter = null;
            } catch (IOException ex) {
                Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (targetLocationsWriter != null) {
            try {
                targetLocationsWriter.close();
                log.info("Closed target locations file " + targetLocationsFile.toString());
                targetLocationsWriter = null;
            } catch (IOException ex) {
                Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                targetLocationsWriter = null;
            }
        }
        if (timecodeWriter != null) {
            try {
                timecodeWriter.close();
                log.info("Closed timecode file " + timecodeFile.toString());
            } catch (IOException ex) {
                Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                timecodeWriter = null;
            }
        }
        super.doFinishRecording();
    }

    private void checkSubsampler() {
    }

    private BufferedImage toImage(DvsFramerSingleFrame dvsFramer) {
        if (aviOutputImage == null) {
            throw new RuntimeException("null aviOutputImage; output has not yet been opened");
        }
        int[] bd = ((DataBufferInt) aviOutputImage.getRaster().getDataBuffer()).getData();
        final int dvsOutputImageHeight = dvsFrame.getOutputImageHeight();
        final int dvsOutputImageWidth = dvsFrame.getOutputImageWidth();
        final int dvsStartingX = getDvsStartingX();
        final int outputImageWidth = getOutputImageWidth();
        final boolean rg = isWriteAPSDVSToRGChannels();
        for (int y = 0; y < dvsOutputImageHeight; y++) {
            for (int x = 0; x < dvsOutputImageWidth; x++) {
                int g = (int) (255 * dvsFramer.getValueAtPixel(x, y));
                int b = rg ? 0 : g;
                int r = rg ? 0 : g;
                int idx = ((dvsOutputImageHeight - y - 1) * outputImageWidth) + x + dvsStartingX; // DVS image is right half if both on
                if (idx >= bd.length) {
                    throw new RuntimeException(String.format("index %d out of bounds for x=%d y=%d", idx, x, y));
                }
                if (!rg) {
                    bd[idx] = (b << 16) | (g << 8) | r | 0xFF000000; // if RG combined output, mask to just overwrite green channel
                } else {
                    bd[idx] &= (~(0xFF << 8)); // clear G channel
                    bd[idx] |= (g << 8); // just G channel overwritten
                }
            }
        }

        return aviOutputImage;

    }

    private BufferedImage toImage(ApsFrameExtractor frameExtractor) {
        if (aviOutputImage == null) {
            throw new RuntimeException("aviOutputImage is null, was not initialized");
        }
        int[] bd = ((DataBufferInt) aviOutputImage.getRaster().getDataBuffer()).getData();
        int srcwidth = chip.getSizeX(), srcheight = chip.getSizeY();
        final int dvsOutputImageWidth = dvsFrame.getOutputImageWidth();
        final int outputImageHeight = dvsFrame.getOutputImageHeight();
        final int outputImageWidth = getOutputImageWidth();
        float[] frame = frameExtractor.getNewFrame();
        final boolean rg = isWriteAPSDVSToRGChannels();
        for (int y = 0; y < outputImageHeight; y++) {
            for (int x = 0; x < dvsOutputImageWidth; x++) {
                int xsrc = (int) Math.floor((x * (float) srcwidth) / dvsOutputImageWidth), ysrc = (int) Math.floor((y * (float) srcheight) / outputImageHeight);
                int r = (int) (255 * frame[frameExtractor.getIndex(xsrc, ysrc)]); // TODO simplest possible downsampling, can do better with linear or bilinear interpolation but code more complex
                int g = rg ? 0 : r;
                int b = rg ? 0 : r;
                int idx = ((outputImageHeight - y - 1) * outputImageWidth) + x + 0; // aps image is left half if combined
                if (idx >= bd.length) {
                    throw new RuntimeException(String.format("index %d out of bounds for x=%d y=%d", idx, x, y));
                }
                if (!rg) {
                    bd[idx] = (b << 16) | (g << 8) | r | 0xFF000000;
                } else {
                    bd[idx] &= ~0xFF; // clear red channel
                    bd[idx] |= r | 0xFF000000;
                }
            }
        }

        return aviOutputImage;

    }

    synchronized public void maybeShowOutput(DvsFramerSingleFrame dvsFramer) {
        if (!isShowOutput()) {
            return;
        }
        frameExtractor.setShowAPSFrameDisplay(isWriteApsFrames());
        if (isWriteDvsFrames() && frame == null) {
            String windowName = "DVS slice";
            frame = new JFrame(windowName);
            frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
            frame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            display = ImageDisplay.createOpenGLCanvas();
            display.setBorderSpacePixels(10);
            display.setImageSize(dvsFrame.getOutputImageWidth(), dvsFrame.getOutputImageHeight());
            display.setSize(200, 200);
            panel.add(display);

            frame.getContentPane().add(panel);
            frame.pack();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowOutput(false);
                }
            });
        }
        if (isWriteDvsFrames()) {
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            if ((display.getWidth() != dvsFrame.getOutputImageWidth()) || (display.getHeight() != dvsFrame.getOutputImageHeight())) {
                display.setImageSize(dvsFrame.getOutputImageWidth(), dvsFrame.getOutputImageHeight());
            }
            for (int x = 0; x < dvsFrame.getOutputImageWidth(); x++) {
                for (int y = 0; y < dvsFrame.getOutputImageHeight(); y++) {
                    display.setPixmapGray(x, y, dvsFramer.getValueAtPixel(x, y));
                }
            }
            display.repaint();
        }
    }

    /**
     * @return the showOutput
     */
    public boolean isShowOutput() {
        return showOutput;
    }

    /**
     * @param showOutput the showOutput to set
     */
    public void setShowOutput(boolean showOutput) {
        boolean old = this.showOutput;
        this.showOutput = showOutput;
        putBoolean("showOutput", showOutput);
        getSupport().firePropertyChange("showOutput", old, showOutput);
    }

    /**
     * @return the writeDvsSliceImageOnApsFrame
     */
    public boolean isWriteDvsSliceImageOnApsFrame() {
        return writeDvsSliceImageOnApsFrame;
    }

    /**
     * @param writeDvsSliceImageOnApsFrame the writeDvsSliceImageOnApsFrame to
     * set
     */
    public void setWriteDvsSliceImageOnApsFrame(boolean writeDvsSliceImageOnApsFrame) {
        this.writeDvsSliceImageOnApsFrame = writeDvsSliceImageOnApsFrame;
        putBoolean("writeDvsSliceImageOnApsFrame", writeDvsSliceImageOnApsFrame);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if ((evt.getPropertyName() == ApsFrameExtractor.EVENT_NEW_FRAME)) {
            endOfFrameTimestamp = frameExtractor.getLastFrameTimestamp();
            newApsFrameAvailable = true;
            if (isWriteApsFrames() && isRecordingActive()
                    && ((chip.getAeViewer() == null) || !chip.getAeViewer().isPaused())) {
                BufferedImage bufferedImage = toImage(frameExtractor);
                writeFrame(bufferedImage, endOfFrameTimestamp);
                if (isWriteTimecodeFile()) {
                    try {
                        writeTimecode(endOfFrameTimestamp);
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                        ex.printStackTrace();
                        setFilterEnabled(false);
                    }
                }

                if (isWriteTargetLocations()) {
                    try {
                        writeTargetLocation(endOfFrameTimestamp, framesWritten);
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                        ex.printStackTrace();
                        setFilterEnabled(false);
                    }
                }
            }
        }
    }

    /**
     * @return the frameRateEstimatorTimeConstantMs
     */
    public float getFrameRateEstimatorTimeConstantMs() {
        return frameRateEstimatorTimeConstantMs;
    }

    /**
     * @param frameRateEstimatorTimeConstantMs the
     * frameRateEstimatorTimeConstantMs to set
     */
    public void setFrameRateEstimatorTimeConstantMs(float frameRateEstimatorTimeConstantMs) {
        this.frameRateEstimatorTimeConstantMs = frameRateEstimatorTimeConstantMs;
        lowpassFilter.setTauMs(frameRateEstimatorTimeConstantMs);
        putFloat("frameRateEstimatorTimeConstantMs", frameRateEstimatorTimeConstantMs);
    }

    public boolean isShowStatistics() {
        return showStatistics;
    }

    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
        putBoolean("showStatistics", showStatistics);
    }

    /**
     * Writes the targets just before the timestamp to the target locations
     * file, if writeTargetLocation is true
     *
     * @param timestamp to search for targets just before timestamp
     * @throws IOException
     */
    private void writeTargetLocation(int timestamp, int frameNumber) throws IOException {
        if (!writeTargetLocations) {
            return;
        }
        if (targetLabeler.hasLocations()) {
            ArrayList<TargetLabeler.TargetLocation> targets = targetLabeler.findTargetsBeforeTimestamp(timestamp);

            if (targets == null) {
//                log.warning(String.format("null labeled target locations for timestamp=%d, frameNumber=%d", timestamp, frameNumber));
                targetLocationsWriter.write(String.format("%d %d %d -1 -1 -1 -1 -1\n", framesWritten, 0, timestamp));

            } else {
                try { // TODO debug
                    for (TargetLocation l : targets) {
                        if ((l != null) && (l.location != null)) {
                            // scale locations and size to AVI output resolution
                            float scaleX = (float) dvsFrame.getOutputImageWidth() / chip.getSizeX();
                            float scaleY = (float) dvsFrame.getOutputImageHeight() / chip.getSizeY();
                            targetLocationsWriter.write(String.format("%d %d %d %d %d %d %d %d\n", framesWritten, 0, timestamp, Math.round(scaleX * l.location.x), Math.round(scaleY * l.location.y), l.targetClassID, l.width, l.height));
                        } else {
                            targetLocationsWriter.write(String.format("%d %d %d -1 -1 -1 -1 -1\n", framesWritten, 0, timestamp));
                        }
                        break; // skip rest of labels, write only 1 per frame
                    }
                } catch (NullPointerException e) {
                    log.warning("null pointer " + e);
                }
            }
        }
    }

    // stupid static class just to control writing and handle rewind event
    static class WriterControl {

        boolean writing = true;

        void end() {
            writing = false;
        }
    }

    /**
     * @return the writeDvsFrames
     */
    public boolean isWriteDvsFrames() {
        return writeDvsFrames;
    }

    /**
     * @param writeDvsFrames the writeDvsFrames to set
     */
    public void setWriteDvsFrames(boolean writeDvsFrames) {
        this.writeDvsFrames = writeDvsFrames;
        putBoolean("writeDvsFrames", writeDvsFrames);
    }

    /**
     * @return the writeApsFrames
     */
    public boolean isWriteApsFrames() {
        return writeApsFrames;
    }

    /**
     * @param writeApsFrames the writeApsFrames to set
     */
    public void setWriteApsFrames(boolean writeApsFrames) {
        this.writeApsFrames = writeApsFrames;
        putBoolean("writeApsFrames", writeApsFrames);
    }

    /**
     * @return the writeAPSDVSToRGChannels
     */
    public boolean isWriteAPSDVSToRGChannels() {
        return writeAPSDVSToRGChannels;
    }

    /**
     * @param writeAPSDVSToRGChannels the writeAPSDVSToRGChannels to set
     */
    public void setWriteAPSDVSToRGChannels(boolean writeAPSDVSToRGChannels) {
        this.writeAPSDVSToRGChannels = writeAPSDVSToRGChannels;
        putBoolean("writeAPSDVSToRGChannels", writeAPSDVSToRGChannels);
    }

//    /**
//     * @return the writeLatestApsDvsTogether
//     */
//    public boolean isWriteLatestApsDvsTogether() {
//        return writeLatestApsDvsTogether;
//    }
//
//    /**
//     * @param writeLatestApsDvsTogether the writeLatestApsDvsTogether to set
//     */
//    public void setWriteLatestApsDvsTogether(boolean writeLatestApsDvsTogether) {
//        this.writeLatestApsDvsTogether = writeLatestApsDvsTogether;
//        putBoolean("writeLatestApsDvsTogether", writeLatestApsDvsTogether);
//    }
    /**
     * @return the writeDvsEventsToTextFile
     */
    public boolean isWriteDvsEventsToTextFile() {
        return writeDvsEventsToTextFile;
    }

    /**
     * @param writeDvsEventsToTextFile the writeDvsEventsToTextFile to set
     */
    public void setWriteDvsEventsToTextFile(boolean writeDvsEventsToTextFile) {
        this.writeDvsEventsToTextFile = writeDvsEventsToTextFile;
        putBoolean("writeDvsEventsToTextFile", writeDvsEventsToTextFile);
    }

    /**
     * @return the writeTargetLocations
     */
    public boolean isWriteTargetLocations() {
        return writeTargetLocations;
    }

    /**
     * @param writeTargetLocations the writeTargetLocations to set
     */
    public void setWriteTargetLocations(boolean writeTargetLocations) {
        this.writeTargetLocations = writeTargetLocations;
        putBoolean("writeTargetLocations", writeTargetLocations);
    }

    /**
     * main and static method stuff below
     * **************************************************************************************************************************
     */
    public static final String USAGE = "java DvsSliceAviWriter \n"
            + "     [-aechip=aechipclassname (either shortcut dvs128, davis240c or davis346mini, or fully qualified class name, e.g. eu.seebetter.ini.chips.davis.DAVIS240C)] \n"
            + "     [-width=36] [-height=36] [-quality=.9] [-format=PNG|JPG|RLE|RAW] [-framerate=30] [-grayscale=200] \n"
            + "     [-writedvssliceonapsframe=false] \n"
            + "     [-writetimecodefile=true] \n"
            + "     [-writeapsframes=false] \n"
            + "     [-writedvsframes=true] \n"
            + "     [-writeapstorgchannel=true] \n"
            + "     [-writedvseventstotextfile=false] \n"
            + "     [-writetargetlocations=false] \n"
            + "     [-timeslicemethod=EventCount|TimeIntervalUs] [-numevents=2000] [-framedurationus=10000]\n"
            + "     [-rectify=false] [-normalize=true] [-showoutput=true]  [-maxframes=0] \n "
            + "     [-enablefilters=false] \n"
            + "         inputFile.aedat [outputfile.avi]"
            + "\n"
            + "numevents and framedurationus are exclusively possible\n"
            + "Arguments values are assigned with =, not space\n"
            + "If outputfile is not provided its name is generated from the input file with appended .avi";

    public static final HashMap<String, String> chipClassesMap = new HashMap();

    public static void main(String[] args) {

        // setup logging to console
        ConsoleHandler handler = new ConsoleHandler();
        Formatter formatter = new LoggingAnsiColorConsoleFormatter();
        handler.setFormatter(formatter);
        log.addHandler(handler);
        log.setLevel(Level.WARNING); // default only warnings, -verbose adds info logging

        // make hashmap of common chip classes
        boolean enableFilters = false;
        chipClassesMap.put("dvs128", "ch.unizh.ini.jaer.chip.retina.DVS128");
        chipClassesMap.put("davis240c", "eu.seebetter.ini.chips.davis.DAVIS240C");
        chipClassesMap.put("davis346blue", "eu.seebetter.ini.chips.davis.Davis346blue");
        chipClassesMap.put("davis346red", "eu.seebetter.ini.chips.davis.Davis346red");

        // command line
        // uses last settings of everything
        // java DvsSliceAviWriter inputFile.aedat outputfile.avi
        Options opt = new Options(args, 1, 2);
        opt.getSet().addOption("enablefilters", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("aechip", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("width", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("height", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("quality", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("format", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("framerate", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("grayscale", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writedvssliceonapsframe", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writedvsframes", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writeapsframes", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writeapstorgchannel", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writedvseventstotextfile", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writetimecodefile", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("numevents", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("framedurationus", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("timeslicemethod", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("rectify", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("normalize", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("nullhopnormalize", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("showoutput", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("maxframes", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writetargetlocations", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("verbose", Multiplicity.ZERO_OR_ONE);
        if (!opt.check()) {
            log.warning(opt.getCheckErrors());
            log.info(USAGE);
            System.exit(1);
        }
        if (opt.getSet().getData().isEmpty()) {
            log.warning("no output file specified");
            System.exit(1);
        }
        if (opt.getSet().getData().size() > 2) {
            log.warning("too many input/output file arguments (only one or two allowed)");
            System.exit(1);
        }

        String inpfilename = opt.getSet().getData().get(0);
        if (!(inpfilename.toLowerCase().endsWith("aedat"))) {
            log.warning("Warning: Input filename does not end with aedat: " + inpfilename);
        }
        String outfilename = null;
        if (opt.getSet().getData().size() == 2) {
            outfilename = opt.getSet().getData().get(1);
        } else {
            outfilename = inpfilename.substring(0, inpfilename.lastIndexOf(".")) + ".avi";
            log.info("Writing to output file " + outfilename);
        }

        AEChip chip = null;
        String chipname = null;
        if (opt.getSet().isSet("aechip")) {
            chipname = opt.getSet().getOption("aechip").getResultValue(0);
        } else {
            chipname = chip.getAeViewer().prefs.get("AEViewer.aeChipClassName", DEFAULT_CHIP_CLASS);
        }
        try {
            String className = chipClassesMap.get(chipname.toLowerCase());
            if (className == null) {
                className = chipname;
            } else {
                log.info("from " + chipname + " found fully qualified class name " + className);
            }
            log.info("constructing AEChip " + className);
            Class chipClass = Class.forName(className);
            Constructor<AEChip> constructor = chipClass.getConstructor();
            chip = constructor.newInstance((java.lang.Object[]) null);
        } catch (Exception ex) {
            log.warning("Could not construct instance of aechip=" + chipname + ": " + ex.toString());
            System.exit(1);
        }

        AEFileInputStream ais = null;
        File inpfile = new File(inpfilename);
        File outfile = new File(outfilename);
        AEPacketRaw aeRaw = null;

        final DvsSliceAviWriter writer = new DvsSliceAviWriter(chip);

        boolean oldCloseOnRewind = writer.isCloseOnRewind();
        writer.setCloseOnRewind(false);
        writer.getSupport().addPropertyChangeListener(writer);
        // handle options
        if (opt.getSet().isSet("verbose")) {
            writer.log.setLevel(Level.INFO);
            log.info("Turned on verbose logging");
        }
        if (opt.getSet().isSet("width")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("width").getResultValue(0));
                writer.getDvsFrame().setOutputImageWidth(n);
            } catch (NumberFormatException e) {
                log.warning("Bad width argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("height")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("height").getResultValue(0));
                writer.getDvsFrame().setOutputImageHeight(n);
            } catch (NumberFormatException e) {
                log.warning("Bad height argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("quality")) {
            try {
                float f = Float.parseFloat(opt.getSet().getOption("quality").getResultValue(0));
                writer.setCompressionQuality(f);
            } catch (NumberFormatException e) {
                log.warning("Bad quality argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("format")) {
            try {
                String type = (opt.getSet().getOption("format").getResultValue(0));
                VideoFormat format = VideoFormat.valueOf(type.toUpperCase());
                writer.setFormat(format);
            } catch (IllegalArgumentException e) {
                log.warning("Bad format argument: " + e.toString() + "; use PNG, JPG, RAW, or RLE");
            }
        }

        if (opt.getSet().isSet("framerate")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("framerate").getResultValue(0));
                writer.setFrameRate(n);
            } catch (NumberFormatException e) {
                log.warning("Bad framerate argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("grayscale")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("grayscale").getResultValue(0));
                writer.getDvsFrame().setDvsGrayScale(n);
            } catch (NumberFormatException e) {
                log.warning("Bad grayscale argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("writedvssliceonapsframe")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writedvssliceonapsframe").getResultValue(0));
            writer.setWriteDvsSliceImageOnApsFrame(b);
        }

        if (opt.getSet().isSet("writetimecodefile")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writetimecodefile").getResultValue(0));
            writer.setWriteTimecodeFile(b);
        }

        if (opt.getSet().isSet("writedvsframes")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writedvsframes").getResultValue(0));
            writer.setWriteDvsFrames(b);
        }

        if (opt.getSet().isSet("writeapsframes")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writeapsframes").getResultValue(0));
            writer.setWriteApsFrames(b);
        }

        if (opt.getSet().isSet("writetargetlocations")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writetargetlocations").getResultValue(0));
            writer.setWriteTargetLocations(b);
        }

        if (opt.getSet().isSet("numevents")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("numevents").getResultValue(0));
                writer.getDvsFrame().setDvsEventsPerFrame(n);
            } catch (NumberFormatException e) {
                log.warning("Bad numevents argument: " + e.toString());
                System.exit(1);
            }
        }
        if (opt.getSet().isSet("framedurationus")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("framedurationus").getResultValue(0));
                writer.getDvsFrame().setTimeDurationUsPerFrame(n);
            } catch (NumberFormatException e) {
                log.warning("Bad numevents argument: " + e.toString());
                System.exit(1);
            }
        }
        if (opt.getSet().isSet("timeslicemethod")) {
            try {
                String methodName = opt.getSet().getOption("timeslicemethod").getResultValue(0);
                TimeSliceMethod method = TimeSliceMethod.valueOf(methodName);
                writer.getDvsFrame().setTimeSliceMethod(method);
            } catch (Exception e) {
                log.warning("Bad timeslicemethod argument: " + e.toString() + "; use EventCount or TimeIntervalUs");
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("rectify")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("rectify").getResultValue(0));
            writer.getDvsFrame().setRectifyPolarities(b);
        }

        if (opt.getSet().isSet("writeapstorgchannel")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writeapstorgchannel").getResultValue(0));
            writer.setWriteAPSDVSToRGChannels(b);
        }

        if (opt.getSet().isSet("normalize")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("normalize").getResultValue(0));
            writer.getDvsFrame().setNormalizeFrame(b);
        }

        if (opt.getSet().isSet("nullhopnormalize")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("nullhopnormalize").getResultValue(0));
            writer.getDvsFrame().setNormalizeDVSForZsNullhop(b);
        }

        if (opt.getSet().isSet("showoutput")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("showoutput").getResultValue(0));
            writer.setShowOutput(b);
        }

        if (opt.getSet().isSet("enablefilters")) {
            enableFilters = Boolean.parseBoolean(opt.getSet().getOption("enablefilters").getResultValue(0));
        } else {
            writer.setMaxFrames(0);
        }

        if (opt.getSet().isSet("maxframes")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("maxframes").getResultValue(0));
                writer.setMaxFrames(n);
            } catch (NumberFormatException e) {
                log.warning("Bad maxframes argument: " + e.toString());
                System.exit(1);
            }
        } else {
            writer.setMaxFrames(0);
        }

        writer.openVideoOutputStream(outfile, args);
        int lastNumFramesWritten = 0, numPrinted = 0;

        try {
            ais = new AEFileInputStream(inpfile, chip);
            ais.getSupport().addPropertyChangeListener(writer); // get informed about rewind events
        } catch (IOException ex) {
            log.warning("Couldn't open file " + inpfile + " from working directory " + System.getProperty("user.dir") + " : " + ex.toString());
            System.exit(1);
        }

        EventExtractor2D extractor = chip.getEventExtractor();
        System.out.print(String.format("Frames written: \n"));

        FilterChain filterChain = chip.getFilterChain();
        filterChain.add(writer);

        filterChain.initFilters();

        if (filterChain != null && enableFilters) {
            for (EventFilter f : filterChain) {
                f.setPreferredEnabledState();
            }
        }
        writer.setFilterEnabled(true);

        // need an object here to register as propertychange listener for the rewind event
        // generated when reading the file and getting to the end,
        // since the AEFileInputStream will not generate end of file exceptions
        final WriterControl writerControl = new WriterControl();
        PropertyChangeListener rewindListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent pce) {
                if (pce.getPropertyName() == AEInputStream.EVENT_EOF) {
                    writerControl.end();
                }
            }
        };
        ais.getSupport().addPropertyChangeListener(rewindListener);
        ais.setNonMonotonicTimeExceptionsChecked(false); // to avoid wrap and big wrap exceptions, possibly, in long recordings
        while (writerControl.writing) {
            try {
                aeRaw = ais.readPacketByNumber(writer.getDvsFrame().getDvsEventsPerFrame()); // read at most this many events to avoid writing duplicate frames at end of movie from start of file, which would happen automatically by
                EventPacket cooked = extractor.extractPacket(aeRaw);
                if (enableFilters && filterChain != null) {
                    cooked = chip.getFilterChain().filterPacket(cooked);
                } else {
                    writer.filterPacket(cooked); // make sure writer is run this way at least
                }
                int numFramesWritten = writer.getFramesWritten();
                if (numFramesWritten >= (lastNumFramesWritten + 20)) {
                    lastNumFramesWritten = numFramesWritten;
                    System.out.print(String.format("%d frames            \r", numFramesWritten));
                }
                if ((writer.getMaxFrames() > 0) && (writer.getFramesWritten() >= writer.getMaxFrames())) {
                    break;
                }
            } catch (EOFException e) {
                log.info("End of file: " + e);
                if (ais != null) {
                    try {
                        ais.close();
                    } catch (IOException ex) {
                        log.warning("Exception closing input stream: " + ex.toString());
                    }
                }
                if (writer != null) {
                    writer.doFinishRecording();
                    log.setLevel(Level.INFO);
                    log.info("Closed output file " + outfile.getAbsolutePath() + " after wriring " + writer.getFramesWritten() + " frames");
                }
                System.exit(0);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    log.warning("IOException: " + e.getMessage());
                    if (ais != null) {
                        ais.close();
                    }
                    if (writer != null) {
                        writer.doFinishRecording();
                        log.setLevel(Level.INFO);
                        log.info("Closed output file " + outfile.getAbsolutePath() + " after wriring " + writer.getFramesWritten() + " frames");
                    }
                    System.exit(1);
                } catch (Exception e3) {
                    log.warning("Exception closing file: " + e3.getMessage());
                    System.exit(1);
                }
            }
        } // end of loop to read and write file

        try {
            ais.close();
        } catch (IOException ex) {
            log.warning("exception closing file: " + ex.toString());
        }
        writer.setShowOutput(false);
        writer.setCloseOnRewind(oldCloseOnRewind);
        writer.doFinishRecording();
        log.setLevel(Level.INFO);
        log.info(String.format("\nSettings: aechip=%s\nwidth=%d height=%d quality=%f format=%s framerate=%d grayscale=%d\n"
                + "writeapsframes=%s writedvsframes=%s\n"
                + "writedvssliceonapsframe=%s writetimecodefile=%s\n"
                + "timeslicemethod=%s numevents=%d framedurationus=%d\n"
                + " rectify=%s normalize=%s nullhopnormalize=%s showoutput=%s maxframes=%d",
                chipname, writer.getDvsFrame().getOutputImageWidth(), writer.getDvsFrame().getOutputImageHeight(),
                writer.getCompressionQuality(), writer.getFormat().toString(),
                writer.getFrameRate(), writer.getDvsFrame().getDvsGrayScale(),
                writer.isWriteApsFrames(), writer.isWriteDvsFrames(),
                writer.isWriteDvsSliceImageOnApsFrame(),
                writer.isWriteTimecodeFile(), writer.getDvsFrame().getTimeSliceMethod().toString(),
                writer.getDvsFrame().getDvsEventsPerFrame(), writer.getDvsFrame().getTimeDurationUsPerFrame(),
                writer.getDvsFrame().isRectifyPolarities(),
                writer.getDvsFrame().isNormalizeFrame(),
                writer.getDvsFrame().isNormalizeDVSForZsNullhop(),
                writer.isShowOutput(), writer.getMaxFrames()));
        log.info("Successfully wrote file " + outfile.getAbsolutePath() + " with " + writer.getFramesWritten() + " frames");
        System.exit(0);
    }

}
