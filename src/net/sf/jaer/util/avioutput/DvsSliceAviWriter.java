package net.sf.jaer.util.avioutput;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import com.jogamp.opengl.GLAutoDrawable;
import ch.unizh.ini.jaer.projects.npp.DvsFramerSingleFrame;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.Constructor;
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
import net.sf.jaer.eventprocessing.FilterChain;
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

    private DvsFramerSingleFrame dvsFrame = null;
    private float frameRateEstimatorTimeConstantMs = getFloat("frameRateEstimatorTimeConstantMs", 10f);
    private boolean normalizeFrame = getBoolean("normalizeFrame", true);
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
    private boolean showStatistics=getBoolean("showStatistics", false);

    public DvsSliceAviWriter(AEChip chip) {
        super(chip);
        FilterChain chain=new FilterChain(chip);
        dvsFrame=new DvsFramerSingleFrame(chip);
        chain.add(dvsFrame);
        setEnclosedFilterChain(chain);
        showOutput = getBoolean("showOutput", true);
        DEFAULT_FILENAME = "DvsEventSlices.avi";
        setPropertyTooltip("showOutput", "shows output in JFrame/ImageDisplay");
        setPropertyTooltip("frameRateEstimatorTimeConstantMs", "time constant of lowpass filter that shows average DVS slice frame rate");
        setPropertyTooltip("writeDvsSliceImageOnApsFrame", "<html>write DVS slice image for each APS frame end event (dvsMinEvents ignored).<br>The frame is written at the end of frame APS event.<br><b>Warning: to capture all frames, ensure that playback time slices are slow enough that all frames are rendered</b>");
        setPropertyTooltip("showStatistics", "shows statistics of DVS frame (most off and on counts, frame rate, sparsity)");
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
        checkSubsampler();
        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            PolarityEvent p = (PolarityEvent) e;
            lastTimestamp = e.timestamp;
            dvsFrame.addEvent(p);
            if ((writeDvsSliceImageOnApsFrame && newFrameAvailable && e.timestamp >= endOfFrameTimestamp)
                    || (!writeDvsSliceImageOnApsFrame && dvsFrame.getDvsFrame().isFilled())
                    && (chip.getAeViewer() == null || !chip.getAeViewer().isPaused())) { // added check for nonnull aeviewer in case filter is called from separate program
                if (writeDvsSliceImageOnApsFrame) {
                    newFrameAvailable = false;
                }
                if (normalizeFrame) {
                    dvsFrame.normalizeFrame();
                }
                maybeShowOutput(dvsFrame);
                if (aviOutputStream != null && isWriteEnabled()) {
                    BufferedImage bi = toImage(dvsFrame);
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
                dvsFrame.clear();
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
    
    public DvsFramerSingleFrame getDvsFrame(){
        return dvsFrame;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (dvsFrame == null) {
            return;
        }
        if(!isShowStatistics()){
            return;
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .8f);
        MultilineAnnotationTextRenderer.setScale(.3f);
        float avgFrameRate = avgDvsFrameIntervalMs == 0 ? Float.NaN : 1000 / avgDvsFrameIntervalMs;
        String s = null;
        if (normalizeFrame) {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f\nsparsity=%.2f%%", dvsFrame.getMostOffCount(), dvsFrame.getMostOnCount(), avgFrameRate, 100 * dvsFrame.getSparsity());
        } else {
            s = String.format("mostOffCount=%d\n mostOnCount=%d\navg frame rate=%.1f", dvsFrame.getMostOffCount(), dvsFrame.getMostOnCount(), avgFrameRate);
        }
        MultilineAnnotationTextRenderer.renderMultilineString(s);
    }

    @Override
    public synchronized void doStartRecordingAndSaveAVIAs() {
        String[] s = {"dvsSubsampler.getWidth()=" + dvsFrame.getOutputImageWidth(), "dvsSubsampler.getHeight()=" + dvsFrame.getOutputImageHeight(), "grayScale=" + dvsFrame.getDvsGrayScale(), "dvsMinEvents=" + dvsFrame.getDvsEventsPerFrame(), "format=" + format.toString(), "compressionQuality=" + compressionQuality};
        setAdditionalComments(s);
        super.doStartRecordingAndSaveAVIAs(); //To change body of generated methods, choose Tools | Templates.
    }

    private void checkSubsampler() {
    }

    private BufferedImage toImage(DvsFramerSingleFrame subSampler) {
        BufferedImage bi = new BufferedImage(dvsFrame.getOutputImageWidth(), dvsFrame.getOutputImageHeight(), BufferedImage.TYPE_INT_BGR);
        int[] bd = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < dvsFrame.getOutputImageHeight(); y++) {
            for (int x = 0; x < dvsFrame.getOutputImageWidth(); x++) {
                int b = (int) (255 * subSampler.getValueAtPixel(x, y));
                int g = b;
                int r = b;
                int idx = (dvsFrame.getOutputImageHeight() - y - 1) * dvsFrame.getOutputImageWidth() + x;
                if (idx >= bd.length) {
                    throw new RuntimeException(String.format("index %d out of bounds for x=%d y=%d", idx, x, y));
                }
                bd[idx] = (b << 16) | (g << 8) | r | 0xFF000000;
            }
        }

        return bi;

    }

    synchronized public void maybeShowOutput(DvsFramerSingleFrame dvsFramer) {
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
            display.setImageSize(dvsFrame.getOutputImageWidth(), dvsFrame.getOutputImageHeight());
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
        if (display.getWidth() != dvsFrame.getOutputImageWidth() || display.getHeight() != dvsFrame.getOutputImageHeight()) {
            display.setImageSize(dvsFrame.getOutputImageWidth(), dvsFrame.getOutputImageHeight());
        }
        for (int x = 0; x < dvsFrame.getOutputImageWidth(); x++) {
            for (int y = 0; y < dvsFrame.getOutputImageHeight(); y++) {
                display.setPixmapGray(x, y, dvsFramer.getValueAtPixel(x, y));
            }
        }
        display.repaint();
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
        if ((evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)) {
            AEFrameChipRenderer renderer = (AEFrameChipRenderer) evt.getNewValue();
            endOfFrameTimestamp = renderer.getTimestampFrameEnd();
            newFrameAvailable = true;
        } else if (isCloseOnRewind() && evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
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


    public static final String USAGE = "java DvsSliceAviWriter [-aechip=aechipclassname (fully qualified class name, e.g. eu.seebetter.ini.chips.davis.DAVIS240C)] "
            + "[-dvsSubsampler.getWidth()=36] [-dvsSubsampler.getHeight()=36] [-quality=.9] [-format=PNG|JPG|RLE|RAW] [-framerate=30] [-grayscale=200] "
            + "[-writedvssliceonapsframe=false] [-writetimecodefile=true] "
            + "[-numevents=2000] [-rectify=false] [-normalize=true] [-showoutput=true]  [-maxframes=0] "
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
        opt.getSet().addOption("dvsSubsampler.getWidth()", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("dvsSubsampler.getHeight()", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
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
        opt.getSet().addOption("maxframes", Separator.EQUALS, Multiplicity.ZERO_OR_ONE);
        if (!opt.check()) {
            System.out.println(USAGE);
            System.exit(1);
        }
        if (opt.getSet().getData().isEmpty()) {
            System.err.println("no output file specified");
            System.exit(1);
        }
        if (opt.getSet().getData().size() > 2) {
            System.err.println("too many input/output file arguments (only one or two allowed)");
            System.exit(1);
        }

        String inpfilename = opt.getSet().getData().get(0);
        if (!(inpfilename.toLowerCase().endsWith("aedat"))) {
            System.err.println("Warning: Input filename does not end with aedat: " + inpfilename);
        }
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

        final DvsSliceAviWriter writer = new DvsSliceAviWriter(chip);

        boolean oldCloseOnRewind = writer.isCloseOnRewind();
        writer.setCloseOnRewind(false);
        writer.getSupport().addPropertyChangeListener(writer);
        // handle options
        if (opt.getSet().isSet("dimx")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("dvsSubsampler.getWidth()").getResultValue(0));
                writer.getDvsFrame().setOutputImageWidth(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad dvsSubsampler.getWidth() argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("dimy")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("dvsSubsampler.getHeight()").getResultValue(0));
                writer.getDvsFrame().setOutputImageHeight(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad dvsSubsampler.getHeight() argument: " + e.toString());
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
                writer.getDvsFrame().setDvsGrayScale(n);
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
                writer.getDvsFrame().setDvsEventsPerFrame(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad numevents argument: " + e.toString());
                System.exit(1);
            }
        }

        if (opt.getSet().isSet("rectify")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("rectify").getResultValue(0));
            writer.getDvsFrame().setRectifyPolarities(b);
        }

        if (opt.getSet().isSet("normalize")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("normalize").getResultValue(0));
            writer.setNormalizeFrame(b);
        }

        if (opt.getSet().isSet("showoutput")) {
            boolean b = Boolean.parseBoolean(opt.getSet().getOption("showoutput").getResultValue(0));
            writer.setShowOutput(b);
        }

        if (opt.getSet().isSet("maxframes")) {
            try {
                int n = Integer.parseInt(opt.getSet().getOption("maxframes").getResultValue(0));
                writer.setMaxFrames(n);
            } catch (NumberFormatException e) {
                System.err.println("Bad maxframes argument: " + e.toString());
                System.exit(1);
            }
        }else{
            writer.setMaxFrames(0);
        }

        writer.openAVIOutputStream(outfile, args);
        int lastNumFramesWritten = 0, numPrinted = 0;

        try {
            ais = new AEFileInputStream(inpfile, chip);
            ais.getSupport().addPropertyChangeListener(writer); // get informed about rewind events
        } catch (IOException ex) {
            System.err.println("Couldn't open file " + inpfile + " from working directory "+System.getProperty("user.dir")+" : " + ex.toString());
            System.exit(1);
        }

        EventExtractor2D extractor = chip.getEventExtractor();
        System.out.println(String.format("Frames written: "));

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
                writer.filterPacket(cooked);
                int numFramesWritten = writer.getFramesWritten();
                if (numFramesWritten >= lastNumFramesWritten + 500) {
                    lastNumFramesWritten = numFramesWritten;
                    System.out.println(String.format("%d frames", numFramesWritten));
                }
                if(writer.getMaxFrames()>0 && writer.getFramesWritten()>=writer.getMaxFrames()){
                    break;
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
                    System.err.println("Exception closing file: " + e3.getMessage());
                    System.exit(1);
                }
            }
        } // end of loop to read and write file

        try {
            ais.close();
        } catch (IOException ex) {
            System.err.println("exception closing file: " + ex.toString());
        }
        writer.setShowOutput(false);
        writer.setCloseOnRewind(oldCloseOnRewind);
        writer.doCloseFile();
        System.out.println(String.format("Settings: aechip=%s\ndvsSubsampler.getWidth()=%d dvsSubsampler.getHeight()=%d quality=%f format=%s framerate=%d grayscale=%d\n"
                + "writedvssliceonapsframe=%s writetimecodefile=%s\n"
                + "numevents=%d rectify=%s normalize=%s showoutput=%s maxframes=%d",
                chipname, writer.getDvsFrame().getOutputImageWidth(), writer.getDvsFrame().getOutputImageHeight(),
                writer.getCompressionQuality(), writer.getFormat().toString(),
                writer.getFrameRate(), writer.getDvsFrame().getDvsGrayScale(), writer.isWriteDvsSliceImageOnApsFrame(),
                writer.isWriteTimecodeFile(), writer.getDvsFrame().getDvsEventsPerFrame(), writer.getDvsFrame().isRectifyPolarities(), 
                writer.getDvsFrame().isNormalizeDVSForZsNullhop(),
                writer.isShowOutput(), writer.getMaxFrames()));
        System.out.println("Successfully wrote file " + outfile + " with " + writer.getFramesWritten() + " frames");
        System.exit(0);
    }

    public boolean isShowStatistics() {
        return showStatistics;
    }
    
    public void setShowStatistics(boolean showStatistics){
        this.showStatistics=showStatistics;
        putBoolean("showStatistics", showStatistics);
    }

    // stupid static class just to control writing and handle rewind event
    static class WriterControl {

        boolean writing = true;

        void end() {
            writing = false;
        }
    }

}
