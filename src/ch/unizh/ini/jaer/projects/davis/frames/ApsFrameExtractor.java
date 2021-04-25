/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import eu.seebetter.ini.chips.DavisChip;
import java.nio.file.Paths;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilter2DMouseROI;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;
import org.apache.commons.io.FilenameUtils;

/**
 * Extracts CIS APS frames from SBRet10/20 DAVIS sensors. Use
 * <ul>
 * <li>hasNewFrameAvailable() to check whether a new frame is available
 * <li>getDisplayBuffer() to get a clone of the latest raw pixel values
 * <li>getNewFrame() to get the latest double buffer of displayed values
 * </ul>
 *
 * Subclasses can use ApsFrameExtractor to process APS frames and DVS events in
 * the order data is received. That way, the frame can be processed at the
 * moment it finishes arrives during an event packet, and the processing can
 * easily use the frame exposure start and end times (or their average) to
 * correctly fuse frames and events.
 *
 * @author Christian Brändli (2015)/Tobi Delbruck (2020, updated for
 * subclassing)
 */
@Description("Method to acquire a frame from a stream of APS sample events")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class ApsFrameExtractor extends EventFilter2DMouseROI {

    protected JFrame apsFrame = null;
    protected ImageDisplay apsDisplay; // actually draws the display

    protected DavisChip apsChip = null;
    protected boolean newFrameAvailable; // boolen set true if during processing packet a new frame was completed
    protected boolean useExternalRenderer = false; // useExternalRenderer means using something like OpenCV to render the
    // data. If false, the rawFrame is displayed
    private float[] resetBuffer, signalBuffer; // the two buffers that are subtracted to get the DDS frame
    /**
     * Raw pixel values from sensor, before conversion, brightness, etc.
     */
    protected float[] rawFrame;
    /**
     * The RGB pixel buffer
     *
     */
    protected float[] apsDisplayPixmapBuffer;
    /**
     * Cooked pixel values, after brightness, contrast, log intensity
     * conversion, etc.
     */
    protected float[] displayFrame; // format is 0-1 mono values
    public int width, height, maxADC, maxIDX; // maxADC is max binary value, i.e. 10 bits =1023
    private float grayValue;
    protected boolean showAPSFrameDisplay = getBoolean("showAPSFrameDisplay", true);
    protected final Legend apsDisplayLegend;
    /**
     * A PropertyChangeEvent with this value is fired when a new frame has been
     * completely read. The oldValue is null. The newValue is the float[]
     * displayFrame that will be rendered.
     */
    public static final String EVENT_NEW_FRAME = DavisRenderer.EVENT_NEW_FRAME_AVAILBLE;
    private int lastFrameTimestamp = -1;

    protected int endOfFrameExposureTimestamp, startOfFrameExposureTimestamp, endOfFrameReadoutTimstamp, startOfFrameReadoutTimestamp;

    public static enum Extraction {

        ResetFrame,
        SignalFrame,
        CDSframe
    }

    private boolean invertIntensity = getBoolean("invertIntensity", false);
    private boolean preBufferFrame = getBoolean("preBufferFrame", true);
    private boolean logCompress = getBoolean("logCompress", false);
    private boolean logDecompress = getBoolean("logDecompress", false);
    protected float displayContrast = getFloat("displayContrast", 1.0f);
    protected float displayBrightness = getFloat("displayBrightness", 0.0f);
    public Extraction extractionMethod = Extraction.valueOf(getString("extractionMethod", "CDSframe"));
    /**
     * Shows pixel info
     */
    protected MouseInfo mouseInfo = null;

    public ApsFrameExtractor(final AEChip chip) {
        super(chip);
        apsDisplay = ImageDisplay.createOpenGLCanvas();
        mouseInfo = new MouseInfo(getApsDisplay());
        apsDisplay.addMouseMotionListener(mouseInfo);
        apsFrame = new JFrame("APS Frame");
        apsFrame.setPreferredSize(new Dimension(400, 400));
        apsFrame.getContentPane().add(apsDisplay, BorderLayout.CENTER);
        apsFrame.pack();
        apsFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });
        apsDisplayLegend = apsDisplay.addLegend("", 0, 0);
        final float[] displayColor = new float[3];
        displayColor[0] = 1.0f;
        displayColor[1] = 1.0f;
        displayColor[2] = 1.0f;
        apsDisplayLegend.color = displayColor;

        setPropertyTooltip("invertIntensity", "Inverts grey scale, e.g. for raw samples of signal level");
        setPropertyTooltip("preBufferFrame", "Only display and use complete frames; otherwise display APS samples as they arrive");
        setPropertyTooltip("logCompress", "Should the displayBuffer be log compressed");
        setPropertyTooltip("logDecompress", "Should the logComressed displayBuffer be rendered in log scale (true) or linearly (false)");
        setPropertyTooltip("displayContrast", "Gain for the rendering of the APS display, i.e. displayed values are processed with (raw+brightness)*displayContrast");
        setPropertyTooltip("displayBrightness", "Offset for the rendering of the APS display, i.e, value added to all displayed pixel values, scaled as in maxADC (>1)");
        setPropertyTooltip("extractionMethod",
                "Method to extract a frame; CDSframe is the final result after subtracting signal from reset frame. Signal and reset frames are the raw sensor output before correlated double sampling.");
        setPropertyTooltip("showAPSFrameDisplay", "Shows the JFrame frame display if true");

    }

    @Override
    public void initFilter() {
        if (DavisChip.class.isAssignableFrom(chip.getClass())) {
            getApsDisplay().checkPixmapAllocation();
            apsChip = (DavisChip) chip;
            maxADC = apsChip.getMaxADC();
            newFrameAvailable = false;
            width = chip.getSizeX(); // note that on initial construction width=0 because this constructor is called while
            // chip is still being built
            height = chip.getSizeY();
            maxIDX = width * height;
            getApsDisplay().setImageSize(width, height);
            resetBuffer = new float[width * height];
            signalBuffer = new float[width * height];
            displayFrame = new float[width * height];
            rawFrame = new float[width * height];
            apsDisplayPixmapBuffer = new float[3 * width * height];
            Arrays.fill(resetBuffer, 0.0f);
            Arrays.fill(signalBuffer, 0.0f);
            Arrays.fill(displayFrame, 0.0f);
            Arrays.fill(rawFrame, 0.0f);
            Arrays.fill(apsDisplayPixmapBuffer, 0.0f);
        } else {
            EventFilter.log.warning("The filter ApsFrameExtractor can only be used for chips that extend the ApsDvsChip class");
            return;
        }
    }

    @Override
    public void resetFilter() {

    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        checkDisplay();
        if (getEnclosedFilterChain() != null) {
            in = getEnclosedFilterChain().filterPacket(in);
        }

        final ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
        if (packet == null) {
            return null;
        }
        if (packet.getEventClass() != ApsDvsEvent.class) {
            EventFilter.log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
            return null;
        }
        final Iterator apsItr = packet.fullIterator();
        while (apsItr.hasNext()) {
            final ApsDvsEvent e = (ApsDvsEvent) apsItr.next();
            if (e.isApsData()) {
                processApsEvent(e);
            } else {
                processDvsEvent(e);
            }
        }

        return in;
    }

    /**
     * Call this before processing packet
     */
    protected void checkDisplay() {
        if (showAPSFrameDisplay && !apsFrame.isVisible()) {
            apsFrame.setVisible(true);
        }
    }

    /**
     * Subclasses can override this method to process DVS events as they arrive.
     *
     * @param e
     */
    protected void processDvsEvent(ApsDvsEvent e) {
    }

    /**
     * Process a single APS data sample or flag event (e.g. end of frame
     * readout). Following call, newFrame could be set. Also extracts the frame
     * exposure start/end and frame readout start/end times.
     *
     * @param e
     * @see #hasNewFrameAvailable()
     * @see #processDvsEvent(net.sf.jaer.event.ApsDvsEvent)
     * @see #processEndOfFrameReadout()
     */
    public void processApsEvent(final ApsDvsEvent e) {
        if (!e.isApsData()) {
            return;
        }
        // if(e.isStartOfFrame())timestampFrameStart=e.timestampFrameStart;
        final ApsDvsEvent.ReadoutType type = e.getReadoutType();
        final float val = e.getAdcSample();
        final int idx = getIndex(e.x, e.y);
        if (idx >= maxIDX) {
            return;
        }

        if (e.isStartOfExposure()) {
            startOfFrameExposureTimestamp = e.timestamp;
            processStartOfExposure(e);
        } else if (e.isEndOfExposure()) {
            endOfFrameExposureTimestamp = e.timestamp;
            processEndOfExposure(e);

        } else if (e.isStartOfFrame()) {
            startOfFrameReadoutTimestamp = e.timestamp;
            processStartOfFrameReadout(e);
        } else if (e.isEndOfFrame()) {
            endOfFrameReadoutTimstamp = e.timestamp;
            if (preBufferFrame && (rawFrame != null) && !useExternalRenderer && showAPSFrameDisplay) {
                displayPreBuffer();
            }
            newFrameAvailable = true;
            lastFrameTimestamp = e.timestamp;
            processEndOfFrameReadout(e);
            getSupport().firePropertyChange(ApsFrameExtractor.EVENT_NEW_FRAME, null, displayFrame);
            if (showAPSFrameDisplay) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        getApsDisplay().repaint(30);
                    }
                });
            }
            return;
        }
        switch (type) {
            case SignalRead:
                signalBuffer[idx] = val;
                break;
            case ResetRead:
            default:
                resetBuffer[idx] = val;
                break;
        }
        switch (extractionMethod) {
            case ResetFrame:
                rawFrame[idx] = resetBuffer[idx];
                break;
            case SignalFrame:
                rawFrame[idx] = signalBuffer[idx];
                break;
            case CDSframe:
            default:
                rawFrame[idx] = resetBuffer[idx] - signalBuffer[idx];
                break;
        }
        if (invertIntensity) {
            rawFrame[idx] = maxADC - rawFrame[idx];
        }
        if (logCompress) {
            if (rawFrame[idx] < 1) {
                rawFrame[idx] = 0;
            } else {
                rawFrame[idx] = (float) Math.log(rawFrame[idx]);
            }
        }
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(rawFrame[idx])));
        } else {
            grayValue = scaleGrayValue(rawFrame[idx]);
        }
        displayFrame[idx] = grayValue;
        if (!preBufferFrame && !useExternalRenderer && showAPSFrameDisplay) {
            getApsDisplay().setPixmapGray(e.x, e.y, grayValue);
        } else if (!useExternalRenderer) {
            apsDisplayPixmapBuffer[3 * idx] = grayValue;
            apsDisplayPixmapBuffer[(3 * idx) + 1] = grayValue;
            apsDisplayPixmapBuffer[(3 * idx) + 2] = grayValue;
        }
    }

    public void saveImage() {
        final BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < chip.getSizeY(); y++) {
            for (int x = 0; x < chip.getSizeX(); x++) {
                final int idx = getApsDisplay().getPixMapIndex(x, chip.getSizeY() - y - 1);
                final int value = ((int) (256 * getApsDisplay().getPixmapArray()[idx]) << 16)
                        | ((int) (256 * getApsDisplay().getPixmapArray()[idx + 1]) << 8) | (int) (256 * getApsDisplay().getPixmapArray()[idx + 2]);
                theImage.setRGB(x, y, value);
            }
        }
        final Date d = new Date();
        final String PNG = "png";
        final String fn = "ApsFrame-" + AEDataFile.DATE_FORMAT.format(d) + "." + PNG;
        // if user is playing a file, use folder that file lives in
        String userDir = Paths.get(".").toAbsolutePath().normalize().toString();
        if (chip.getAeViewer() != null && chip.getAeViewer().getAePlayer() != null && chip.getAeViewer().getPlayMode() == PlayMode.PLAYBACK && chip.getAeViewer().getInputFile() != null) {
            userDir = chip.getAeViewer().getInputFile().getAbsolutePath();
        }
        File outputfile = new File(userDir + File.separator + fn);
        boolean done = false;
        while (!done) {
            JFileChooser fd = new JFileChooser(outputfile);
            fd.setApproveButtonText("Save as");
            fd.setSelectedFile(outputfile);
            fd.setVisible(true);
            final int ret = fd.showOpenDialog(null);
            if (ret != JFileChooser.APPROVE_OPTION) {
                return;
            }
            outputfile = fd.getSelectedFile();
            if (!FilenameUtils.isExtension(outputfile.getAbsolutePath(), PNG)) {
                String ext = FilenameUtils.getExtension(outputfile.toString());
                String newfile = outputfile.getAbsolutePath();
                if (ext != null && !ext.isEmpty() && !ext.equals(PNG)) {
                    newfile = outputfile.getAbsolutePath().replace(ext, PNG);
                } else {
                    newfile = newfile + "." + PNG;
                }
                outputfile = new File(newfile);
            }
            if (outputfile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(fd, outputfile.toString() + " exists, overwrite?");
                switch (overwrite) {
                    case JOptionPane.OK_OPTION:
                        done = true;
                        break;
                    case JOptionPane.CANCEL_OPTION:
                        return;
                    case JOptionPane.NO_OPTION:
                        break;
                }
            } else {
                done = true;
            }
        }
        try {
            ImageIO.write(theImage, "png", outputfile);
            log.info("wrote PNG " + outputfile);
//            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Wrote "+userDir+File.separator+fn, "Saved PNG image", JOptionPane.INFORMATION_MESSAGE);
        } catch (final IOException ex) {
            Logger.getLogger(ApsFrameExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Returns timestampFrameStart of last frame, which is the
     * timestampFrameStart of the frame end event
     *
     * @return the timestampFrameStart (usually in us)
     */
    public int getLastFrameTimestamp() {
        return lastFrameTimestamp;
    }

    /**
     * Scales the raw value to the display value using
     * <pre>
     * v = ((displayContrast * value) + displayBrightness) / maxADC
     * </pre>
     *
     * @param value, from ADC, i.e. 0-1023 for 10-bit ADC
     * @return a float clipped to range 0-1
     */
    protected float scaleGrayValue(final float value) {
        float v;
        v = ((displayContrast * value) + displayBrightness) / maxADC;
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }

    public void updateDisplayValue(final int xAddr, final int yAddr, final float value) {
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(value)));
        } else {
            grayValue = scaleGrayValue(value);
        }
        getApsDisplay().setPixmapGray(xAddr, yAddr, grayValue);
    }

    /**
     * Sets the displayed data array, a float array of 0-1 RGB values ordered according to getIndex
     * @param pixmapArray the array
     * @see #getIndex(int, int) 
     */
    public void setPixmapArray(final float[] pixmapArray) {
        getApsDisplay().setPixmapArray(pixmapArray);
    }

    public void displayPreBuffer() {
        getApsDisplay().setPixmapArray(apsDisplayPixmapBuffer);
    }

    /**
     * returns the index <code>y * width + x</code> into pixel arrays for a
     * given x,y location where x is horizontal address and y is vertical and it
     * starts at lower left corner with x,y=0,0 and x and y increase to right
     * and up.
     *
     * @param x
     * @param y
     * @param idx the array index
     * @see #getWidth()
     * @see #getHeight()
     */
    public int getIndex(final int x, final int y) {
        return (y * width) + x;
    }

    /**
     * Checks if new frame is available. This flag is reset by getNewFrame()
     *
     * @return true if new frame is available
     * @see #getNewFrame()
     */
    public boolean hasNewFrameAvailable() {
        return newFrameAvailable;
    }

    /**
     * Returns a float[] buffer of latest displayed frame with adjustments like
     * brightness, contrast, log intensity conversion, etc. The array is indexed
     * by y * width + x. To access a particular pixel, use getIndex(). newFrame
     * is set to false by this call.
     *
     * @return the double[] frame
     * @see #getRawFrame()
     */
    public float[] getNewFrame() {
        newFrameAvailable = false;
        return displayFrame;
    }

    /**
     * Empty method called when a new frame is complete and available in
     * rawFrame. Subclasses can override to process the available frame at
     * this point.
     * @see #rawFrame
     */
    protected void processEndOfFrameReadout(ApsDvsEvent e) {
    }

    /**
     * Empty method called when a new frame is started to be read out.
     */
    protected void processStartOfFrameReadout(ApsDvsEvent e) {
    }

    /**
     * Empty method called when a new frame exposure has completed.
     */
    protected void processEndOfExposure(ApsDvsEvent e) {
    }

    /**
     * Empty method called when a new frame exposure was started.
     */
    protected void processStartOfExposure(ApsDvsEvent e) {
    }

    /**
     * Returns a clone of the latest float rawFrame. This buffer contains raw
     * pixel values from sensor, before conversion, brightness, etc. The array
     * is indexed by <code>y * width + x</code>. To access a particular pixel,
     * use getIndex() for convenience. newFrame is set to false by this call.
     *
     * The only processing applied to rawFrame is inversion of values and log
     * conversion.
     * <pre>
     * if (invertIntensity) {
     * rawFrame[idx] = maxADC - rawFrame[idx];
     * }
     * if (logCompress) {
     * rawFrame[idx] = (float) Math.log(rawFrame[idx] + logSafetyOffset);
     * }
     * </pre>
     *
     * @return the float[] of pixel values
     * @see #getIndex(int, int)
     * @see #getNewFrame()
     */
    public float[] getRawFrame() {
        newFrameAvailable = false;
        return rawFrame.clone();
    }

    /**
     * Tell chip to acquire new frame, return immediately.
     *
     */
    public void acquireNewFrame() {
        apsChip.takeSnapshot();
    }

    public float getMinBufferValue() {
        float minBufferValue = 0.0f;
        if (logCompress) {
            minBufferValue = (float) Math.log(minBufferValue);
        }
        return minBufferValue;
    }

    public float getMaxBufferValue() {
        float maxBufferValue = maxADC;
        if (logCompress) {
            maxBufferValue = (float) Math.log(maxBufferValue);
        }
        return maxBufferValue;
    }

    /**
     * Sets whether external source sets the displayed data.
     *
     * @param yes true to not fill image values, false to set image values from
     * ApsFrameExtractor
     * @see #setDisplayFrameRGB(float[])
     * @see #setDisplayGrayFrame(double[])
     */
    public void setUseExternalRenderer(final boolean yes) {
        useExternalRenderer = yes;
    }

    /**
     * Sets the displayed legend string, which can be a multiline string
     * @param legend the string, with embedded \n newlines
     */
    public void setLegend(final String legend) {
        apsDisplayLegend.s = legend;
    }

    /**
     * Sets the displayed frame gray values from a float array
     *
     * @param frame array with same pixel ordering as rawFrame and displayFrame
     */
    public void setDisplayGrayFrame(final float[] frame) {
        int xc = 0;
        int yc = 0;
        for (final float element : frame) {
            getApsDisplay().setPixmapGray(xc, yc, (float) element);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    /**
     * Sets the displayed frame RGB values from a double array
     *
     * @param frame array with same pixel ordering as rawFrame and displayFrame
     * but with RGB values for each pixel
     */
    public void setDisplayFrameRGB(final float[] frame) {
        int xc = 0;
        int yc = 0;
        for (int i = 0; i < frame.length; i += 3) {
            getApsDisplay().setPixmapRGB(xc, yc, frame[i + 2], frame[i + 1], frame[i]);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    /**
     * @return the invertIntensity
     */
    public boolean isInvertIntensity() {
        return invertIntensity;
    }

    /**
     * @param invertIntensity the invertIntensity to set
     */
    public void setInvertIntensity(final boolean invertIntensity) {
        this.invertIntensity = invertIntensity;
        putBoolean("invertIntensity", invertIntensity);
    }

    /**
     * @return the preBufferFrame
     */
    public boolean isPreBufferFrame() {
        return preBufferFrame;
    }

    /**
     * @param preBufferFrame the preBufferFrame to set
     */
    public void setPreBufferFrame(final boolean preBuffer) {
        preBufferFrame = preBuffer;
        putBoolean("preBufferFrame", preBufferFrame);
    }

    /**
     * @return the logDecompress
     */
    public boolean isLogDecompress() {
        return logDecompress;
    }

    /**
     * @param logDecompress the logDecompress to set
     */
    public void setLogDecompress(final boolean logDecompress) {
        boolean old = this.logDecompress;
        this.logDecompress = logDecompress;
        putBoolean("logDecompress", logDecompress);
        getSupport().firePropertyChange("logDecompress", old, this.logDecompress);
    }

    /**
     * @return the logCompress
     */
    public boolean isLogCompress() {
        return logCompress;
    }

    /**
     * Raw pixel values in rawFrame are natural log of raw pixel values
     *
     * @param logCompress the logCompress to set
     */
    public void setLogCompress(final boolean logCompress) {
        boolean old = this.logCompress;
        this.logCompress = logCompress;
        putBoolean("logCompress", logCompress);
        getSupport().firePropertyChange("logCompress", old, this.logCompress);
    }

    /**
     * @return the displayContrast
     */
    public float getDisplayContrast() {
        return displayContrast;
    }

    /**
     * @param displayContrast the displayContrast to set
     */
    public void setDisplayContrast(final float displayContrast) {
        this.displayContrast = displayContrast;
        putFloat("displayContrast", displayContrast);
    }

    /**
     * @return the displayBrightness
     */
    public float getDisplayBrightness() {
        return displayBrightness;
    }

    /**
     * @param displayBrightness the displayBrightness to set
     */
    public void setDisplayBrightness(final float displayBrightness) {
        this.displayBrightness = displayBrightness;
        putFloat("displayBrightness", displayBrightness);
    }

    public Extraction getExtractionMethod() {
        return extractionMethod;
    }

    synchronized public void setExtractionMethod(final Extraction extractionMethod) {
        getSupport().firePropertyChange("extractionMethod", this.extractionMethod, extractionMethod);
        putString("edgePixelMethod", extractionMethod.toString());
        this.extractionMethod = extractionMethod;
        resetFilter();
    }

    /**
     * @return the showAPSFrameDisplay
     */
    public boolean isShowAPSFrameDisplay() {
        return showAPSFrameDisplay;
    }

    /**
     * @param showAPSFrameDisplay the showAPSFrameDisplay to set
     */
    public void setShowAPSFrameDisplay(final boolean showAPSFrameDisplay) {
        this.showAPSFrameDisplay = showAPSFrameDisplay;
        putBoolean("showAPSFrameDisplay", showAPSFrameDisplay);
        if (apsFrame != null) {
            apsFrame.setVisible(showAPSFrameDisplay);
        }
        getSupport().firePropertyChange("showAPSFrameDisplay", null, showAPSFrameDisplay);
    }

    /**
     * Overrides to add check for DavisChip
     */
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        if (yes && !(chip instanceof DavisChip)) {
            log.warning("not a DAVIS camera, not enabling filter");

            return;
        }
        if (!isFilterEnabled()) {
            if (apsFrame != null) {
                apsFrame.setVisible(false);
            }
        }
        super.setFilterEnabled(yes); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * returns frame width in pixels.
     *
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * returns frame height in pixels
     *
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * returns max ADC value
     *
     * @return the maxADC
     */
    public int getMaxADC() {
        return maxADC;
    }

    /**
     * returns max index into frame buffer arrays
     *
     * @return the maxIDX
     */
    public int getMaxIDX() {
        return maxIDX;
    }

    /**
     */
    public void doSaveAsPNG() {
        saveImage();
    }

    private class MouseInfo extends MouseMotionAdapter {

        ImageDisplay apsImageDisplay;

        public MouseInfo(final ImageDisplay display) {
            apsImageDisplay = display;
        }

        @Override
        public void mouseMoved(final MouseEvent e) {
            final Point2D.Float p = apsImageDisplay.getMouseImagePosition(e);
            if ((p.x >= 0) && (p.x < chip.getSizeX()) && (p.y >= 0) && (p.y < chip.getSizeY())) {
                final int idx = getIndex((int) p.x, (int) p.y);
                if (resetBuffer == null || signalBuffer == null || idx < 0 || idx >= resetBuffer.length) {
                    return;
                }
                EventFilter.log.info(String.format("reset= %d, signal= %d, reset-signal= %+d", (int) resetBuffer[idx],
                        (int) signalBuffer[idx], (int) (resetBuffer[idx] - signalBuffer[idx])));
            }
        }
    }

    /**
     * @return the apsDisplay
     */
    public ImageDisplay getApsDisplay() {
        return apsDisplay;
    }

    /**
     * @return the endOfFrameExposureTimestamp
     */
    public int getEndOfFrameExposureTimestamp() {
        return endOfFrameExposureTimestamp;
    }

    /**
     * @return the startOfFrameExposureTimestamp
     */
    public int getStartOfFrameExposureTimestamp() {
        return startOfFrameExposureTimestamp;
    }

    /**
     * @return the endOfFrameReadoutTimstamp
     */
    public int getEndOfFrameReadoutTimstamp() {
        return endOfFrameReadoutTimstamp;
    }

    /**
     * @return the startOfFrameReadoutTimestamp
     */
    public int getStartOfFrameReadoutTimestamp() {
        return startOfFrameReadoutTimestamp;
    }

    /**
     * Computes average of global shutter exposure time in us
     * @return timestamp in us, same as for DVS events
     */
    public int getAverageFrameExposureTimestamp() {
        return (startOfFrameExposureTimestamp / 2 + endOfFrameExposureTimestamp / 2);
    }
    
    /** Returns exposure duration in seconds of last exposure period
     * 
     * @return exposure duration in seconds
     */
    public float getExposureDurationS(){
        return 1e-6f*(endOfFrameExposureTimestamp-startOfFrameExposureTimestamp);
    }
}
