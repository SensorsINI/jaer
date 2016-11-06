package net.sf.jaer.util.avioutput;

import eu.visualize.ini.convnet.DvsSubsamplerToFrame;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import java.beans.PropertyChangeEvent;
import java.io.EOFException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
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
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import static net.sf.jaer.graphics.AEViewer.DEFAULT_CHIP_CLASS;
import static net.sf.jaer.graphics.AEViewer.prefs;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.avioutput.AVIOutputStream.VideoFormat;
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

    private DvsSubsamplerToFrame dvsSubsampler = null;
    private int dimx, dimy, grayScale;
    private int dvsMinEvents = getInt("dvsMinEvents", 10000);
    private float frameRateEstimatorTimeConstantMs = getFloat("frameRateEstimatorTimeConstantMs", 10f);
    private boolean normalizeFrame = getBoolean("normalizeFrame", true);
    private boolean fullRectifyOutput = getBoolean("fullRectifyOutput", false);
    private JFrame frame = null;
    public ImageDisplay display;
    private boolean showOutput;
    private volatile boolean newFrameAvailable = false;
    private int endOfFrameTimestamp = 0, lastTimestamp = 0;
    protected boolean writeDvsSliceImageOnApsFrame = getBoolean("writeDvsSliceImageOnApsFrame", false);
    private boolean rendererPropertyChangeListenerAdded = false;
    private AEFrameChipRenderer renderer = null;
    private LowpassFilter lowpassFilter = new LowpassFilter(frameRateEstimatorTimeConstantMs);
    private int lastDvsFrameTimestamp = 0;
    private float avgDvsFrameIntervalMs = 0;

    public DvsSliceAviWriter(AEChip chip) {
        super(chip);
        dimx = getInt("dimx", 36);
        dimy = getInt("dimy", 36);
        grayScale = getInt("grayScale", 100);
        showOutput = getBoolean("showOutput", true);
        DEFAULT_FILENAME = "DvsEventSlices.avi";
        setPropertyTooltip("grayScale", "1/grayScale is the amount by which each DVS event is added to time slice 2D gray-level histogram");
        setPropertyTooltip("dimx", "width of AVI frame");
        setPropertyTooltip("dimy", "height of AVI frame");
        setPropertyTooltip("showOutput", "shows output in JFrame/ImageDisplay");
        setPropertyTooltip("dvsMinEvents", "minimum number of events to run net on DVS timeslice (only if writeDvsSliceImageOnApsFrame is false)");
        setPropertyTooltip("frameRateEstimatorTimeConstantMs", "time constant of lowpass filter that shows average DVS slice frame rate");
        setPropertyTooltip("writeDvsSliceImageOnApsFrame", "<html>write DVS slice image for each APS frame end event (dvsMinEvents ignored).<br>The frame is written at the end of frame APS event.<br><b>Warning: to capture all frames, ensure that playback time slices are slow enough that all frames are rendered</b>");
        setPropertyTooltip("normalizeFrame", "<html>Normalize the frame so that the 3-sigma range of original values fills the full output range of 0-1 values (0-255 in PNG file)<br>This normalization is the same as that used in DeepLearnCnnNetwork.");
        setPropertyTooltip("fullRectifyOutput", "<html>Fully rectify ON and OFF events to only ON events. <br>Output image ranges from 0-255 in PNG file but no events is 0 <br>and all events of either type produce positive pixel values");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
//        frameExtractor.filterPacket(in); // extracts frames with nornalization (brightness, contrast) and sends to apsNet on each frame in PropertyChangeListener
        // send DVS timeslice to convnet
        super.filterPacket(in);
        if (!rendererPropertyChangeListenerAdded) {
            rendererPropertyChangeListenerAdded = true;
            renderer = (AEFrameChipRenderer) chip.getRenderer();
            renderer.getSupport().addPropertyChangeListener(this);
        }
        final int sizeX = chip.getSizeX();
        final int sizeY = chip.getSizeY();
        checkSubsampler();
        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            PolarityEvent p = (PolarityEvent) e;
            lastTimestamp = e.timestamp;
            dvsSubsampler.addEvent(p, sizeX, sizeY);
            if ((writeDvsSliceImageOnApsFrame && newFrameAvailable && e.timestamp >= endOfFrameTimestamp)
                    || (!writeDvsSliceImageOnApsFrame && dvsSubsampler.getAccumulatedEventCount() > dvsMinEvents)
                    && (chip.getAeViewer() == null || !chip.getAeViewer().isPaused())) { // added check for nonnull aeviewer in case filter is called from separate program
                if (writeDvsSliceImageOnApsFrame) {
                    newFrameAvailable = false;
                }
                if (normalizeFrame) {
                    dvsSubsampler.normalizeFrame();
                }
                maybeShowOutput(dvsSubsampler);
                if (aviOutputStream != null && isWriteEnabled()) {
                    BufferedImage bi = toImage(dvsSubsampler);
                    try {
                        writeTimecode(e.timestamp);
                        aviOutputStream.writeFrame(bi);
                        incrementFramecountAndMaybeCloseOutput();
                    } catch (IOException ex) {
                        log.warning(ex.toString());
                        ex.printStackTrace();
                        setFilterEnabled(false);
                    }
                }
                dvsSubsampler.clear();
                if (lastDvsFrameTimestamp != 0) {
                    int lastFrameInterval = lastTimestamp - lastDvsFrameTimestamp;
                    avgDvsFrameIntervalMs = 1e-3f * lowpassFilter.filter(lastFrameInterval, lastTimestamp);
                }
                lastDvsFrameTimestamp = lastTimestamp;
            }
        }
        if (writeDvsSliceImageOnApsFrame && lastTimestamp - endOfFrameTimestamp > 1000000) {
            log.warning("last frame event was received more than 1s ago; maybe you need to enable Display Frames in the User Control Panel?");
        }
        return in;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (dvsSubsampler == null) {
            return;
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .8f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        float avgFrameRate = avgDvsFrameIntervalMs == 0 ? Float.NaN : 1000 / avgDvsFrameIntervalMs;
        String s = null;
        if (normalizeFrame) {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f\nsparsity=%.2f%%", dvsSubsampler.getMostOffCount(), dvsSubsampler.getMostOnCount(), avgFrameRate, 100 * dvsSubsampler.getSparsity());
        } else {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f", dvsSubsampler.getMostOffCount(), dvsSubsampler.getMostOnCount(), avgFrameRate);
        }
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    @Override
    public synchronized void doStartRecordingAndSaveAVIAs() {
        String[] s = {"dimx=" + dimx, "dimy=" + dimy, "grayScale=" + grayScale, "dvsMinEvents=" + dvsMinEvents, "format=" + format.toString(), "compressionQuality=" + compressionQuality};
        setAdditionalComments(s);
        super.doStartRecordingAndSaveAVIAs(); //To change body of generated methods, choose Tools | Templates.
    }

    private void checkSubsampler() {
        if (dvsSubsampler == null || dimx * dimy != dvsSubsampler.getnPixels()) {
            if (aviOutputStream != null && dvsSubsampler != null) {
                log.info("closing existing output file because output resolution has changed");
                doCloseFile();
            }
            dvsSubsampler = new DvsSubsamplerToFrame(dimx, dimy, grayScale);
            dvsSubsampler.setRectifyPolarties(fullRectifyOutput);
        }
    }

    private BufferedImage toImage(DvsSubsamplerToFrame subSampler) {
        BufferedImage bi = new BufferedImage(dimx, dimy, BufferedImage.TYPE_INT_BGR);
        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < dimy; y++) {
            for (int x = 0; x < dimx; x++) {
                int b = (int) (255 * subSampler.getValueAtPixel(x, y));
                int g = b;
                int r = b;
                int idx = (dimy - y - 1) * dimx + x;
                if (idx >= bd.length) {
                    throw new RuntimeException(String.format("index %d out of bounds for x=%d y=%d", idx, x, y));
                }
                bd[idx] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;

    }

    synchronized public void maybeShowOutput(DvsSubsamplerToFrame subSampler) {
        if (!showOutput) {
            return;
        }
        if (frame == null) {
            String windowName = "DVS slice";
            frame = new JFrame(windowName);
            frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
            frame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            display = ImageDisplay.createOpenGLCanvas();
            display.setBorderSpacePixels(10);
            display.setImageSize(dimx, dimy);
            display.setSize(200, 200);
            panel.add(display);

            frame.getContentPane().add(panel);
            frame.pack();
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setShowOutput(false);
                }
            });
        }
        if (!frame.isVisible()) {
            frame.setVisible(true);
        }
        if (display.getWidth() != dimx || display.getHeight() != dimy) {
            display.setImageSize(dimx, dimy);
        }
        for (int x = 0; x < dimx; x++) {
            for (int y = 0; y < dimy; y++) {
                display.setPixmapGray(x, y, subSampler.getValueAtPixel(x, y));
            }
        }
        display.repaint();
    }

    /**
     * @return the dvsMinEvents
     */
    public int getDvsMinEvents() {
        return dvsMinEvents;
    }

    /**
     * @param dvsMinEvents the dvsMinEvents to set
     */
    public void setDvsMinEvents(int dvsMinEvents) {
        this.dvsMinEvents = dvsMinEvents;
        putInt("dvsMinEvents", dvsMinEvents);
    }

    /**
     * @return the dimx
     */
    public int getDimx() {
        return dimx;
    }

    /**
     * @param dimx the dimx to set
     */
    synchronized public void setDimx(int dimx) {
        this.dimx = dimx;
        putInt("dimx", dimx);
    }

    /**
     * @return the dimy
     */
    public int getDimy() {
        return dimy;
    }

    /**
     * @param dimy the dimy to set
     */
    synchronized public void setDimy(int dimy) {
        this.dimy = dimy;
        putInt("dimy", dimy);
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
     * @return the grayScale
     */
    public int getGrayScale() {
        return grayScale;
    }

    /**
     * @param grayScale the grayScale to set
     */
    public void setGrayScale(int grayScale) {
        if (grayScale < 1) {
            grayScale = 1;
        }
        this.grayScale = grayScale;
        putInt("grayScale", grayScale);
        if (dvsSubsampler != null) {
            dvsSubsampler.setColorScale(grayScale);
        }
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
        if ((evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)) {
            AEFrameChipRenderer renderer = (AEFrameChipRenderer) evt.getNewValue();
            endOfFrameTimestamp = renderer.getTimestampFrameEnd();
            newFrameAvailable = true;
        } else if (isCloseOnRewind() && evt.getPropertyName() == AEInputStream.EVENT_REWIND) {
            doCloseFile();
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

    /**
     * @return the normalizeFrame
     */
    public boolean isNormalizeFrame() {
        return normalizeFrame;
    }

    /**
     * @param normalizeFrame the normalizeFrame to set
     */
    public void setNormalizeFrame(boolean normalizeFrame) {
        this.normalizeFrame = normalizeFrame;
        putBoolean("normalizeFrame", normalizeFrame);
    }

    /**
     * @return the fullRectifyOutput
     */
    public boolean isFullRectifyOutput() {
        return fullRectifyOutput;
    }

    /**
     * @param fullRectifyOutput the fullRectifyOutput to set
     */
    public void setFullRectifyOutput(boolean fullRectifyOutput) {
        this.fullRectifyOutput = fullRectifyOutput;
        if (dvsSubsampler != null) {
            dvsSubsampler.setRectifyPolarties(fullRectifyOutput);
        }
        putBoolean("fullRectifyOutput", fullRectifyOutput);
    }

    public static final String USAGE = "java DvsSliceAviWriter [-aechip=aechipclassname (fully qualified class name, e.g. eu.seebetter.ini.chips.davis.DAVIS240C)] "
            + "[-dimx=36] [-dimy=36] [-quality=.9] [-format=PNG|JPG|RLE|RAW] [-framerate=30] [-grayscale=200] "
            + "[-writedvssliceonapsframe=false] [-writetimecodefile=true] "
            + "[-numevents=2000] [-rectify=false] [-normalize=true] [-showoutput=true]  "
            + "inputFile.aedat [outputfile.avi]"
            + "\n"
            + "Note arguments values are assigned with =, not space"
            + "\n"
            + "If outputfile is not provided its name is generated from the input file with appended .avi";

    public static void main(String[] args) {
        // command line
        // uses last settings of everything
        // java DvsSliceAviWriter inputFile.aedat outputfile.avi
        Options opt = new Options(args, 1, 2);
        opt.getSet().addOption("aechip", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("dimx", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("dimy", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("quality", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("format", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("framerate", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("grayscale", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writedvssliceonapsframe", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("writetimecodefile", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("numevents", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("rectify", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("normalize", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("showoutput", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        if (!opt.check()) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String inpfilename = opt.getSet().getData().get(0);
        String outfilename = null;
        if (opt.getSet().getData().size() == 2) {
            outfilename = opt.getSet().getData().get(1);
        } else {
            outfilename = inpfilename.substring(0, inpfilename.lastIndexOf(".")) + ".avi";
            System.out.println("Writing to output file " + outfilename);
        }

        AEChip chip = null;
        String chipname = null;
        if (opt.getSet().isSet("aechip")) {
            chipname = opt.getSet().getOption("aechip").getResultValue(0);
        } else {
            chipname = prefs.get("AEViewer.aeChipClassName", DEFAULT_CHIP_CLASS);
        }
        try {
            System.out.println("constructing AEChip " + chipname);
            Class chipClass = Class.forName(chipname);
            Constructor<AEChip> constructor = chipClass.getConstructor();
            chip = constructor.newInstance((java.lang.Object[]) null);
        } catch (Exception ex) {
            System.err.println("Could not construct instance of aechip=" + chipname + ": " + ex.toString());
            System.exit(1);
        }

        AEFileInputStream ais = null;
        File inpfile = new File(inpfilename);
        File outfile = new File(outfilename);
        AEPacketRaw aeRaw = null;
        DvsSliceAviWriter writer = new DvsSliceAviWriter(chip);

        writer.setCloseOnRewind(true);
        writer.getSupport().addPropertyChangeListener(writer);
        // handle options
        if (opt.getSet().isSet("dimx")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("dimx").getResultValue(0));
                writer.setDimx(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad dimx argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("dimy")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("dimy").getResultValue(0));
                writer.setDimy(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad dimy argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("quality")) {
            try {
                float f = Float.parseFloat(opt.getSet().getOption("quality").getResultValue(0));
                writer.setCompressionQuality(f);
            } catch (NumberFormatException e) {
                System.err.println("Bad quality argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("format")) {
            try {
                String type = (opt.getSet().getOption("format").getResultValue(0));
                VideoFormat format = VideoFormat.valueOf(type.toUpperCase());
                writer.setFormat(format);
            } catch (IllegalArgumentException e) {
                System.err.println("Bad format argument: " + e.toString() + "; use PNG, JPG, RAW, or RLE");
            }
        }

        if (opt.getSet().isSet("framerate")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("framerate").getResultValue(0));
                writer.setFrameRate(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad framerate argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("grayscale")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("grayscale").getResultValue(0));
                writer.setGrayScale(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad grayscale argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("writedvssliceonapsframe")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writedvssliceonapsframe").getResultValue(0));
            writer.setWriteDvsSliceImageOnApsFrame(b);
        }

        if (opt.getSet().isSet("writetimecodefile")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("writetimecodefile").getResultValue(0));
            writer.setWriteDvsSliceImageOnApsFrame(b);
        }

        if (opt.getSet().isSet("numevents")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("numevents").getResultValue(0));
                writer.setDvsMinEvents(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad numevents argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("rectify")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("rectify").getResultValue(0));
            writer.setFullRectifyOutput(b);
        }

        if (opt.getSet().isSet("normalize")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("normalize").getResultValue(0));
            writer.setNormalizeFrame(b);
        }

        if (opt.getSet().isSet("showoutput")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("showoutput").getResultValue(0));
            writer.setShowOutput(b);
        }

        writer.openAVIOutputStream(outfile, args);
        int lastNumFramesWritten = 0, numPrinted = 0;

        try {
            ais = new AEFileInputStream(inpfile, chip);
            ais.getSupport().addPropertyChangeListener(writer); // get informed about rewind events
        } catch (IOException ex) {
            Logger.getLogger(DvsSliceAviWriter.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.print(String.format("Frames written: "));
        while (writer.isWriteEnabled()) {
            try {
                aeRaw = ais.readPacketByNumber(20000); // TODO fix
                EventExtractor2D extractor = chip.getEventExtractor();
                EventPacket cooked = extractor.extractPacket(aeRaw);
                writer.filterPacket(cooked);
                int numFramesWritten = writer.getFramesWritten();
                if (numFramesWritten > lastNumFramesWritten) {
                    System.out.print(String.format(" %d", numFramesWritten));
                    if (numPrinted++ == 20) {
                        System.out.println("");
                        numPrinted = 0;
                    }
                }
                lastNumFramesWritten = numFramesWritten;

            } catch (EOFException e) {
                try {
                    ais.close();
                    writer.setShowOutput(false);
                    writer.doCloseFile();
                    log.info("wrote file " + outfile);
                    System.out.println("wrote file " + outfile);
                    System.exit(0);

                } catch (IOException ioe) {
                    System.err.println("IOException on close after EOF: " + ioe.getMessage());
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    System.err.println("IOException: " + e.getMessage());
                    if (ais != null) {
                        ais.close();
                    }
                    if (writer != null) {
                        writer.doCloseFile();
                    }
                    System.exit(1);

                } catch (Exception e3) {
                    e3.printStackTrace();
                }
            }
        }

        log.info(String.format("Settings: aechip=%s\ndimx=%d dimy=%d quality=%f format=%s framerate=%d grayscale=%d\n"
                + "writedvssliceonapsframe=%s writetimecodefile=%s\n"
                + "numevents=%d rectify=%s normalize=%s showoutput=%s",
                chipname,writer.getDimx(),writer.getDimy(),
                writer.getCompressionQuality(),writer.getFormat().toString(),
                writer.getFrameRate(),writer.getGrayScale(),writer.isWriteDvsSliceImageOnApsFrame(),
                writer.isWriteTimecodeFile(),writer.getDvsMinEvents(),writer.isFullRectifyOutput(), writer.isFullRectifyOutput(),
                writer.isShowOutput()));
        log.info("Successfully wrote file " + outfile);
        System.out.println("Successfully wrote file " + outfile);
        System.exit(0);
    }

}
