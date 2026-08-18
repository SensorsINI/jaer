/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.calibration;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import com.esotericsoftware.yamlbeans.YamlException;
import java.awt.Dimension;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.Chip2DRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.TextRendererScale;
import net.sf.jaer.util.YamlMatFileStorage;
import net.sf.jaer.util.OpenCVNativeLoader;

import static org.opencv.core.Core.countNonZero;

/**
 * Calibrates a single camera using OpenCV chessboard calibration.
 * jAER 3.0: {@link #processFrame} consumes typed {@link FramePacket}s (DAVIS APS /
 * AEDAT-4); {@link #processPolarity} samples the rendered DVS image and can
 * undistort events. Legacy mixed {@link ApsDvsEventPacket} still goes through
 * {@link #filterPacket}.
 *
 * @author Marc Osswald, Tobi Delbruck
 */
@Description("OpenCV chessboard camera calibration from DAVIS APS frames or accumulated DVS renderings")
@Help("""
<html>
<body>
<h2>SingleCameraCalibration</h2>
<p>Estimates pinhole intrinsics (focal length, principal point) and lens distortion
with OpenCV <code>calibrateCamera</code> from views of a printed <b>chessboard</b>.
Works with <b>DAVIS APS frames</b> and with <b>DVS-only</b> data by sampling the
same accumulated event image you see in the viewer.</p>
<p>OpenCV docs:
<a href="https://docs.opencv.org/4.x/dc/dbb/tutorial_py_calibration.html">Camera calibration</a>.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Print a chessboard (or click <code>displayCalibrationImage</code> and photograph that
window). Measure one square in millimetres.</li>
<li>Set <code>patternWidth</code> / <code>patternHeight</code> to the number of
<b>internal corners</b> (one less than the number of squares on each side). Set
<code>rectangleWidthMm</code> / <code>rectangleHeightMm</code> to the square size.</li>
<li><code>frameSource</code> <b>Auto</b> uses APS / AEDAT-4 <code>FramePacket</code>s
when they arrive, otherwise the rendered DVS image. Force <b>ApsFrames</b> or
<b>RenderedEventFrames</b> if you need one modality only.</li>
<li>For DVS / event recordings: turn on viewer <b>Accumulate</b> (or fading) and
<b>slowly move</b> the board or camera so the squares fill in. A perfectly still
DVS image is blank and will not detect corners.</li>
<li>Enable the filter. The enclosed APS window shows the grayscale image sent to
OpenCV (contrast-stretched). With <code>realtimePatternDetectionEnabled</code>, a found
board is overlaid on the viewer. Click <code>captureSingleFrame</code> (or
<code>triggerAutocapture</code>) when the overlay looks correct. Capture ~10–20
poses, different angles and distances, filling the field of view.</li>
<li>Click <code>calibrate</code>, then <code>saveCalibration</code>. Later sessions
can <code>loadCalibration</code>.</li>
</ol>
<h3>Controls</h3>
<ul>
<li><code>fontSize</code> &mdash; overlay and statistics text size in chip pixels
(default from chip width; same idea as other filters).</li>
<li><code>renderedFrameIntervalMs</code> &mdash; how often to sample the DVS rendering
when not using APS (OpenCV chessboard search is not cheap).</li>
<li><code>cornerSubPixRefinement</code> &mdash; subpixel corner locations (recommended).</li>
<li><code>showAPSFrameDisplay</code> &mdash; OpenCV search-image window (on by default).</li>
<li><code>showUndistortedFrames</code> &mdash; preview lens correction on the APS
frame window after calibration.</li>
<li><code>undistortDVSevents</code> &mdash; remap DVS event addresses with the
calibration LUT (events that fall outside the chip are dropped).</li>
<li><code>clearImages</code> drops collected corners; <code>clearCalibration</code>
drops the solved camera matrix without clearing corners.</li>
</ul>
<p>Sample chessboard recordings (DAVIS346, AEDAT-4) are in
<a href="https://sites.google.com/view/davis24-davis-sample-data/home#h.elfxct3takto">DAVIS24 lens calibration samples</a>:
a frames-only 14×9-corner / 25&nbsp;mm board with a 3.5&nbsp;mm Kowa lens, and an events+frames recording.
Play with AEChip <code>Davis346blue</code>.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class SingleCameraCalibration extends EventFilter2D implements FrameAnnotater /* observes this to get informed about our size */ {

    static {
        OpenCVNativeLoader.load();
    }

    private int sx; // set to chip.getSizeX()
    private int sy; // chip.getSizeY()
    private int lastTimestamp = 0;

    private float[] lastFrame = null, outFrame = null;

    /**
     * Fires property change with this string when new calibration is available
     */
    public static final String EVENT_NEW_CALIBRATION = "EVENT_NEW_CALIBRATION";
    /** Default capture/calibration folder under the user home directory. */
    public static final String DEFAULT_CALIBRATION_FOLDER_NAME = "jAER-SingleCameraCalibration";

    //encapsulated fields
    private boolean realtimePatternDetectionEnabled = getBoolean("realtimePatternDetectionEnabled", true);
    private boolean cornerSubPixRefinement = getBoolean("cornerSubPixRefinement", true);
    private String dirPath = getString("dirPath", defaultCalibrationDirPath());
    private int patternWidth = getInt("patternWidth", 9);
    private int patternHeight = getInt("patternHeight", 5);
    private int rectangleHeightMm = getInt("rectangleHeightMm", 20); //height in mm
    private int rectangleWidthMm = getInt("rectangleWidthMm", 20); //width in mm
    private boolean showUndistortedFrames = getBoolean("showUndistortedFrames", false);
    private boolean undistortDVSevents = getBoolean("undistortDVSevents", true);
    private boolean hideStatisticsAndStatus = getBoolean("hideStatisticsAndStatus", false);
    private String fileBaseName = "";

    //opencv matrices
    private MatOfPoint2f corners;
    private ArrayList<Mat> allImagePoints;
    private ArrayList<Mat> allObjectPoints;
    private Mat cameraMatrix;
    private Mat distortionCoefs;
    private ArrayList<Mat> rotationVectors;
    private ArrayList<Mat> translationVectors;
    private Mat imgIn, imgOut;

    private short[] undistortedAddressLUT = null; // stores undistortion LUT for event addresses. values are stored by idx = 2 * (y + sy * x);
    private boolean isUndistortedAddressLUTgenerated = false;

    private float focalLengthPixels = 0;
    private float focalLengthMm = 0;
    private Point2D.Float principlePoint = null;
    private String calibrationString = "Uncalibrated";

    private boolean patternFound;
    private int imageCounter = 0;
    private boolean calibrated = false;

    private boolean captureTriggered = false;
    private int nAcqFrames = 0;
    private int numAutoCaptureFrames = getInt("numAutoCaptureFrames", 10);

    private boolean autocaptureCalibrationFramesEnabled = false;
    private int autocaptureCalibrationFrameDelayMs = getInt("autocaptureCalibrationFrameDelayMs", 1500);
    private long lastAutocaptureTimeMs = 0;

    /** Where grayscale images for OpenCV come from. */
    public enum FrameSource {
        Auto,
        ApsFrames,
        RenderedEventFrames
    }

    private FrameSource frameSource = FrameSource.Auto;
    private int renderedFrameIntervalMs = getInt("renderedFrameIntervalMs", 200);
    private long lastRenderedSampleMs = 0;
    private boolean seenApsFrame = false;
    private boolean lastFrameFromAps = false;

    private final ApsFrameExtractor frameExtractor;
    private final FilterChain filterChain;
    private boolean saved = false;
    private boolean textRendererScaleSet = false;
    private int noPatternFoundwarningSkipInterval = 50, noPatternFoundWarningCount = 0;
    private String lastCornerSearchSummary = "";
    private boolean loggedFirstChessboardFind = false;
    private String overlayMessage = null;
    private Color overlayColor = Color.yellow;
    private long overlayUntilMs = 0;
    private static final int OVERLAY_MS = 5000;
    private int fontSize;
        

    public SingleCameraCalibration(AEChip chip) {
        super(chip);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        frameExtractor.setUseExternalRenderer(false);
        // Default freezeRoi=true so chip-canvas clicks do not start a tiny ROI by accident.
        frameExtractor.setFreezeRoi(frameExtractor.getBoolean("freezeRoi", true));
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("patternHeight", "height of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("patternWidth", "width of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("realtimePatternDetectionEnabled", "continuously run OpenCV chessboard detection on incoming APS or rendered DVS frames");
        setPropertyTooltip("rectangleWidthMm", "width of square rectangles of calibration pattern in mm");
        setPropertyTooltip("rectangleHeightMm", "height of square rectangles of calibration pattern in mm");
        setPropertyTooltip("showAPSFrameDisplay", "Shows the ApsFrameExtractor window with the grayscale image sent to OpenCV");
        setPropertyTooltip("showUndistortedFrames", "shows the undistorted frame in the ApsFrameExtractor display, if calibration has been completed");
        setPropertyTooltip("undistortDVSevents", "applies LUT undistortion to DVS event address if calibration has been completed; events outside AEChip address space are filtered out");
        setPropertyTooltip("cornerSubPixRefinement", "refine corner locations to subpixel resolution");
        setPropertyTooltip("calibrate", "run the camera calibration on collected frame data and print results to console");
        setPropertyTooltip("displayCalibrationImage", "shows the calibration image that you can aim the camera at");
        setPropertyTooltip("setPath", "sets the folder and basename of saved images (default is ~/" + DEFAULT_CALIBRATION_FOLDER_NAME + ")");
        setPropertyTooltip("saveCalibration", "saves calibration files to a selected folder");
        setPropertyTooltip("loadCalibration", "loads saved calibration files from selected folder");
        setPropertyTooltip("clearCalibration", "clears existing calibration, without clearing accumulated corner points (see ClearImages)");
        setPropertyTooltip("clearImages", "clears existing image corner and object points without clearing calibration (see ClearCalibration)");
        setPropertyTooltip("captureSingleFrame", "snaps a single calibration image that forms part of the calibration dataset");
        setPropertyTooltip("triggerAutocapture", "starts automatically capturing calibration frames with delay specified by autocaptureCalibrationFrameDelayMs");
        setPropertyTooltip("hideStatisticsAndStatus", "hides the status text");
        setPropertyTooltip("numAutoCaptureFrames", "Number of frames to automatically capture with min delay autocaptureCalibrationFrameDelayMs between frames");
        setPropertyTooltip("autocaptureCalibrationFrameDelayMs", "Delay after capturing automatic calibration frame");
        setPropertyTooltip("fontSize", "Font size in chip pixels for overlay and statistics (default scales with chip width)");
        setPropertyTooltip("frameSource", "Auto: APS frames when they arrive, else the rendered DVS image. ApsFrames / RenderedEventFrames force one source.");
        setPropertyTooltip("renderedFrameIntervalMs", "Minimum interval between OpenCV searches on the rendered DVS image (ignored for APS frames)");
        try {
            frameSource = FrameSource.valueOf(getString("frameSource", FrameSource.Auto.name()));
        } catch (IllegalArgumentException e) {
            frameSource = FrameSource.Auto;
        }
        fontSize = getInt("fontSize", estimatedFontSize());
        frameExtractor.setShowAPSFrameDisplay(getBoolean("showAPSFrameDisplay", true));
        frameExtractor.getSupport().addPropertyChangeListener("showAPSFrameDisplay", evt -> {
            Object nv = evt.getNewValue();
            if (!(nv instanceof Boolean)) {
                return;
            }
            putBoolean("showAPSFrameDisplay", (Boolean) nv);
            getSupport().firePropertyChange("showAPSFrameDisplay", evt.getOldValue(), nv);
        });
        migrateDirPathOffJaerWorkingDir();
//        loadCalibration(); // moved from here to update method so that Chip is fully constructed with correct size, etc.
    }

    static String defaultCalibrationDirPath() {
        return new File(System.getProperty("user.home"), DEFAULT_CALIBRATION_FOLDER_NAME).getPath();
    }

    /**
     * Old default was {@code user.dir} (the jAER working folder). Move stored
     * prefs off that path so captured frames do not land in the repo.
     */
    private void migrateDirPathOffJaerWorkingDir() {
        String userDir = System.getProperty("user.dir");
        if (dirPath == null || dirPath.isEmpty() || dirPath.equals(userDir)) {
            dirPath = defaultCalibrationDirPath();
            putString("dirPath", dirPath);
        }
    }

    private void ensureCalibrationDir() {
        File d = new File(dirPath);
        if (!d.exists() && !d.mkdirs()) {
            log.warning("Could not create calibration folder " + dirPath);
        }
    }

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY || type == PacketType.FRAME;
    }

    /**
     * Legacy / mixed {@link ApsDvsEventPacket} path (old USB extractor). Typed
     * polarity packets must not land here — use {@link #processPolarity}.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (in == null) {
            return in;
        }
        if (in instanceof ApsDvsEventPacket) {
            getEnclosedFilterChain().filterPacket(in);
            boolean newAps = frameExtractor != null && frameExtractor.hasNewFrameAvailable();
            if (newAps) {
                seenApsFrame = true;
            }
            if (wantApsFrame() && newAps) {
                lastFrame = frameExtractor.getNewFrame();
                lastFrameFromAps = true;
                processCalibrationFrame();
                updateApsUndistortPreview();
            } else if (wantRenderedEventFrame() && copyRenderedFrameToLastFrame()) {
                lastFrameFromAps = false;
                processCalibrationFrame();
            }
            undistortPolarityPacket(in);
            if (!in.isEmpty()) {
                lastTimestamp = in.getLastTimestamp();
            }
            return in;
        }
        return processPolarity(in);
    }

    /**
     * jAER 3.0 polarity path: optional rendered-DVS chessboard + event undistort.
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> processPolarity(EventPacket<? extends BasicEvent> in) {
        if (in == null) {
            return in;
        }
        if (wantRenderedEventFrame() && copyRenderedFrameToLastFrame()) {
            lastFrameFromAps = false;
            processCalibrationFrame();
        }
        undistortPolarityPacket(in);
        if (!in.isEmpty()) {
            lastTimestamp = in.getLastTimestamp();
        }
        return in;
    }

    /**
     * jAER 3.0 completed APS / AEDAT-4 frame (y=0 at bottom, same as chip pixmap).
     */
    @Override
    synchronized public FramePacket processFrame(FramePacket in) {
        if (in == null || in.isEmpty() || !wantApsFrame()) {
            return in;
        }
        if (copyFramePacketToLastFrame(in)) {
            seenApsFrame = true;
            lastFrameFromAps = true;
            processCalibrationFrame();
            updateApsUndistortPreview();
        }
        return in;
    }

    private boolean wantApsFrame() {
        return frameSource != FrameSource.RenderedEventFrames;
    }

    private boolean wantRenderedEventFrame() {
        if (frameSource == FrameSource.ApsFrames) {
            return false;
        }
        if (frameSource == FrameSource.RenderedEventFrames) {
            return shouldSampleRenderedFrame();
        }
        return !seenApsFrame && shouldSampleRenderedFrame();
    }

    /**
     * True when OpenCV should run on the viewer pixmap (throttled). Capture
     * requests bypass the interval so a click is not missed.
     */
    private boolean shouldSampleRenderedFrame() {
        if (!realtimePatternDetectionEnabled && !autocaptureCalibrationFramesEnabled && !captureTriggered) {
            return false;
        }
        if (captureTriggered) {
            return true;
        }
        long now = System.currentTimeMillis();
        if ((now - lastRenderedSampleMs) < renderedFrameIntervalMs) {
            return false;
        }
        lastRenderedSampleMs = now;
        return true;
    }

    /**
     * Copies the chip renderer pixmap into {@link #lastFrame} as grayscale.
     * Uses {@link Chip2DRenderer#getPixMapIndex} so DAVIS RGBA / padded textures
     * and 3-channel DVS pixmaps both copy correctly (y=0 at bottom).
     */
    private boolean copyRenderedFrameToLastFrame() {
        Chip2DRenderer renderer = chip.getRenderer();
        if (renderer == null || sx <= 0 || sy <= 0) {
            return false;
        }
        synchronized (renderer) {
            float[] pixmap = renderer.getPixmapArray();
            int n = sx * sy;
            if (pixmap == null) {
                return false;
            }
            if (lastFrame == null || lastFrame.length != n) {
                lastFrame = new float[n];
            }
            int copied = 0;
            for (int y = 0; y < sy; y++) {
                int row = y * sx;
                for (int x = 0; x < sx; x++) {
                    int pi = renderer.getPixMapIndex(x, y);
                    if (pi < 0 || (pi + 2) >= pixmap.length) {
                        lastFrame[row + x] = 0;
                        continue;
                    }
                    lastFrame[row + x] = (pixmap[pi] + pixmap[pi + 1] + pixmap[pi + 2]) * (1f / 3f);
                    copied++;
                }
            }
            if (copied < (n / 2)) {
                log.warning(String.format(
                        "rendered pixmap copy incomplete: copied %d/%d (renderer=%s pixmapLen=%d)",
                        copied, n, renderer.getClass().getSimpleName(), pixmap.length));
                return false;
            }
        }
        return true;
    }

    /**
     * Copies a typed {@link FramePacket} into {@link #lastFrame} as 0–1 gray.
     * Packet layout is {@code y * width + x} with y=0 at the bottom (jAER).
     */
    private boolean copyFramePacketToLastFrame(FramePacket frame) {
        if (frame == null || sx <= 0 || sy <= 0) {
            return false;
        }
        int w = frame.getWidth();
        int h = frame.getHeight();
        short[] pix = frame.getPixels();
        int ch = Math.max(1, frame.channelsPerPixel());
        if (w != sx || h != sy || pix == null || pix.length < w * h * ch) {
            log.warning(String.format("FramePacket %dx%d ch=%d does not match chip %dx%d", w, h, ch, sx, sy));
            return false;
        }
        int n = sx * sy;
        if (lastFrame == null || lastFrame.length != n) {
            lastFrame = new float[n];
        }
        int maxv = 1;
        for (int i = 0; i < pix.length; i++) {
            int v = pix[i] & 0xffff;
            if (v > maxv) {
                maxv = v;
            }
        }
        float scale = maxv <= 255 ? 255f : (maxv <= 1023 ? 1023f : 65535f);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int base = (row + x) * ch;
                float g;
                if (ch >= 3) {
                    g = ((pix[base] & 0xffff) + (pix[base + 1] & 0xffff) + (pix[base + 2] & 0xffff)) / (3f * scale);
                } else {
                    g = (pix[base] & 0xffff) / scale;
                }
                lastFrame[row + x] = g;
            }
        }
        return true;
    }

    private void updateApsUndistortPreview() {
        if (!apsPreviewAvailable()) {
            return;
        }
        if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay() && lastFrame != null) {
            frameExtractor.setDisplayFrameRGB(undistortFrame(lastFrame));
            frameExtractor.setUseExternalRenderer(true);
            frameExtractor.getApsDisplay().setTitleLabel("lens correction enabled");
        } else {
            frameExtractor.setUseExternalRenderer(false);
            frameExtractor.getApsDisplay().setTitleLabel("raw input image");
            if (lastFrame != null) {
                frameExtractor.setDisplayGrayFrame(lastFrame);
            }
        }
    }

    private void undistortPolarityPacket(EventPacket in) {
        if (!calibrated || !undistortDVSevents || in == null) {
            return;
        }
        try {
            for (Object o : in) {
                if (!(o instanceof BasicEvent)) {
                    continue;
                }
                BasicEvent e = (BasicEvent) o;
                if (e.isSpecial()) {
                    continue;
                }
                if (o instanceof ApsDvsEvent && !((ApsDvsEvent) o).isDVSEvent()) {
                    continue;
                }
                undistortEvent(e);
            }
        } catch (RuntimeException e) {
            log.warning("undistortDVSevents failed (will skip this packet): " + e);
        }
    }

    private boolean apsPreviewAvailable() {
        return frameExtractor != null && frameExtractor.getApsDisplay() != null && frameExtractor.width > 0;
    }

    /**
     * Chessboard detect / optional capture on {@link #lastFrame}.
     */
    private void processCalibrationFrame() {
        if (lastFrame == null) {
            return;
        }
        showLastFrameOnApsPreview();
        if (realtimePatternDetectionEnabled || autocaptureCalibrationFramesEnabled || captureTriggered) {
            patternFound = findCurrentCorners(false);
        }

        if (patternFound
                && (captureTriggered
                || (autocaptureCalibrationFramesEnabled
                && ((System.currentTimeMillis() - lastAutocaptureTimeMs) > autocaptureCalibrationFrameDelayMs)
                && (nAcqFrames < numAutoCaptureFrames)))) {
            boolean fromCaptureButton = captureTriggered;
            int before = imageCounter;
            nAcqFrames++;
            findCurrentCorners(true);
            captureTriggered = false;
            lastAutocaptureTimeMs = System.currentTimeMillis();
            if (fromCaptureButton) {
                if (imageCounter > before) {
                    showTransientOverlay(String.format("Captured %d image%s",
                            imageCounter, imageCounter == 1 ? "" : "s"), Color.green);
                } else {
                    showTransientOverlay("Capture failed: corners not saved", Color.orange);
                }
            }
            if (nAcqFrames >= numAutoCaptureFrames) {
                autocaptureCalibrationFramesEnabled = false;
                log.info("finished autocapturing " + nAcqFrames + " acquired. Starting calibration in background....");
                (new CalibrationWorker()).execute();
            } else {
                log.info("captured frame " + nAcqFrames);
            }
        } else if (!patternFound) {
            if (--noPatternFoundWarningCount < 0) {
                log.warning(String.format(
                        "no chessboard: %s (skipping next %d warnings). patternWidth/Height are internal corners, not squares.",
                        lastCornerSearchSummary, noPatternFoundwarningSkipInterval));
                noPatternFoundWarningCount = noPatternFoundwarningSkipInterval;
            }
        }
    }

    /** Push the grayscale OpenCV input to the enclosed APS window so it is visible. */
    private void showLastFrameOnApsPreview() {
        if (!apsPreviewAvailable() || lastFrame == null) {
            return;
        }
        if (calibrated && showUndistortedFrames) {
            updateApsUndistortPreview();
            return;
        }
        frameExtractor.setDisplayGrayFrame(lastFrame);
        frameExtractor.getApsDisplay().setTitleLabel(
                lastFrameFromAps ? "calibration input (APS FramePacket)" : "calibration input (rendered DVS)");
    }

    private class CalibrationWorker extends SwingWorker<String, Object> {

        @Override
        protected String doInBackground() throws Exception {
            calibrationString = "calibration is currently being computed";
            doCalibrate();
            return "done";
        }

        @Override
        protected void done() {
            try {
                generateCalibrationString();
            } catch (Exception ignore) {
                log.warning(ignore.toString());
            }
        }
    }

    /**
     * Undistorts an image frame using the calibration.
     *
     * @param src the source image, RGB float valued in 0-1 range
     * @return float[] destination. IAn internal float[] is created and reused.
     * If there is no calibration, the src array is returned.
     */
    public float[] undistortFrame(float[] src) {
        if (!calibrated) {
            return src;
        }
        // FloatPointer ip = new FloatPointer(src);
        Mat input = new Mat(1, src.length, CvType.CV_32F);
        input.put(0, 0, src);
        input.convertTo(input, CvType.CV_8U, 255, 0);
        Mat img = input.reshape(0, sy);
        Mat undistortedImg = new Mat();
        try {
            Calib3d.undistort(img, undistortedImg, cameraMatrix, distortionCoefs);
        } catch (RuntimeException e) {
            log.warning(e.toString());
            return src;
        }
        Mat imgOut8u = new Mat(sy, sx, CvType.CV_8UC3);
        Imgproc.cvtColor(undistortedImg, imgOut8u, Imgproc.COLOR_GRAY2RGB);
        Mat outImgF = new Mat(sy, sx, CvType.CV_32F);
        imgOut8u.convertTo(outImgF, CvType.CV_32F, 1.0 / 255, 0);
        if (outFrame == null) {
            outFrame = new float[sy * sx * 3];
        }
        // outImgF.getFloatBuffer().get(outFrame);
        outImgF.get(0, 0, outFrame);
        return outFrame;
    }

    /**
     * Contrast-stretched 8-bit gray {@code sy x sx} Mat from {@link #lastFrame}
     * (OpenCV row 0 = jAER y=0 = bottom). Also fills {@link #lastCornerSearchSummary}.
     */
    private Mat lastFrameToGray8() {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, sum = 0;
        int nz = 0;
        for (float v : lastFrame) {
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
            sum += v;
            if (v > 1e-4f) {
                nz++;
            }
        }
        float range = max - min;
        byte[] gray = new byte[sx * sy];
        if (range > 1e-6f) {
            float s = 255f / range;
            for (int i = 0; i < lastFrame.length; i++) {
                int g = Math.round((lastFrame[i] - min) * s);
                gray[i] = (byte) (g < 0 ? 0 : (g > 255 ? 255 : g));
            }
        }
        Mat m = new Mat(sy, sx, CvType.CV_8UC1);
        m.put(0, 0, gray);
        lastCornerSearchSummary = String.format(
                "src=%s %dx%d frame[min=%.3f max=%.3f mean=%.3f nz=%.0f%%] OpenCV %dx%d CV_8U pattern=%dx%d inner-corners",
                lastFrameFromAps ? "APS/FramePacket" : "renderedDVS",
                sx, sy, min, max, sum / lastFrame.length, 100.0 * nz / lastFrame.length,
                m.cols(), m.rows(), patternWidth, patternHeight);
        return m;
    }

    /**
     * Finds current corners of calibration image.
     *
     * @param drawAndSave true to draw the corners, false to just check if there
     * are corners.
     * @return true if corners found, false if not
     */
    public boolean findCurrentCorners(boolean drawAndSave) {
        if (lastFrame == null || sx <= 0 || sy <= 0 || lastFrame.length != (sx * sy)) {
            lastCornerSearchSummary = String.format("bad lastFrame len=%s sx=%d sy=%d",
                    lastFrame == null ? "null" : Integer.toString(lastFrame.length), sx, sy);
            return false;
        }
        Size patternSize = new Size(patternWidth, patternHeight);
        corners = new MatOfPoint2f();
        imgIn = lastFrameToGray8();
        imgOut = new Mat(sy, sx, CvType.CV_8UC3);
        Imgproc.cvtColor(imgIn, imgOut, Imgproc.COLOR_GRAY2RGB);
        final int flags = Calib3d.CALIB_CB_ADAPTIVE_THRESH
                | Calib3d.CALIB_CB_NORMALIZE_IMAGE
                | Calib3d.CALIB_CB_FILTER_QUADS;
        boolean locPatternFound = false;
        String how = "none";
        try {
            locPatternFound = Calib3d.findChessboardCorners(imgIn, patternSize, corners, flags);
            how = "findChessboardCorners";
            if (!locPatternFound) {
                Mat eq = new Mat();
                Imgproc.equalizeHist(imgIn, eq);
                locPatternFound = Calib3d.findChessboardCorners(eq, patternSize, corners, flags);
                how = "equalizeHist+findChessboardCorners";
                eq.release();
            }
            if (!locPatternFound) {
                locPatternFound = Calib3d.findChessboardCornersSB(imgIn, patternSize, corners,
                        Calib3d.CALIB_CB_EXHAUSTIVE | Calib3d.CALIB_CB_NORMALIZE_IMAGE);
                how = "findChessboardCornersSB";
            }
            if (!locPatternFound && patternWidth != patternHeight) {
                Size swapped = new Size(patternHeight, patternWidth);
                boolean swappedFound = Calib3d.findChessboardCorners(imgIn, swapped, corners, flags);
                if (swappedFound) {
                    lastCornerSearchSummary += String.format(
                            " | HINT: board matches swapped %dx%d — set patternWidth=%d patternHeight=%d",
                            patternHeight, patternWidth, patternHeight, patternWidth);
                    // do not treat as success: object-point layout would be wrong
                }
            }
        } catch (RuntimeException e) {
            log.warning("OpenCV chessboard: " + e);
            lastCornerSearchSummary += " | exception " + e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
        lastCornerSearchSummary += " | " + how + (locPatternFound ? " FOUND" : " miss");
        if (locPatternFound && !drawAndSave && !loggedFirstChessboardFind) {
            log.info("chessboard: " + lastCornerSearchSummary);
            loggedFirstChessboardFind = true;
        }
        if (drawAndSave) {
            //render frame
            if (locPatternFound && cornerSubPixRefinement) {
                TermCriteria tc = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.1);
                Imgproc.cornerSubPix(imgIn, corners, new Size(3, 3), new Size(-1, -1), tc);
            }
            Calib3d.drawChessboardCorners(imgOut, patternSize, corners, locPatternFound);
            Mat outImgF = new Mat(sy, sx, CvType.CV_64FC3);
            imgOut.convertTo(outImgF, CvType.CV_32FC3, 1.0 / 255, 0);
            float[] outFrame = new float[sy * sx * 3];
            outImgF.get(0, 0, outFrame);
            if (apsPreviewAvailable()) {
                frameExtractor.setDisplayFrameRGB(outFrame);
            }
            //save image
            if (locPatternFound) {
                Mat imgSave = new Mat(sy, sx, CvType.CV_8U);
                Core.flip(imgIn, imgSave, 0);
                String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + ".jpg";
                ensureCalibrationDir();
                String fullFilePath = dirPath + File.separator + filename;
                Imgcodecs.imwrite(fullFilePath, imgSave);
                log.info("saved image " + fullFilePath);
                //store image points
                if ((imageCounter == 0) || (allObjectPoints == null) || (allImagePoints == null)) {
                    allImagePoints = new ArrayList<Mat>();
                    allObjectPoints = new ArrayList<Mat>();
                }
                allImagePoints.add(corners);
                //create and store object points, which are just coordinates in mm of corners of pattern as we know they are drawn on the
                // calibration target
                Mat objectPoints = new Mat(corners.rows(), 1, CvType.CV_32FC3);
                float x, y;
                for (int h = 0; h < patternHeight; h++) {
                    y = h * rectangleHeightMm;
                    for (int w = 0; w < patternWidth; w++) {
                        x = w * rectangleWidthMm;
                        objectPoints.put((patternWidth * h) + w, 0, x, y, 0); // z=0 for object points
                    }
                }
                allObjectPoints.add(objectPoints);
                //iterate image counter
                log.info(String.format("added corner points from image %d", imageCounter));
                imageCounter++;
                if (apsPreviewAvailable()) {
                    frameExtractor.getApsDisplay().setxLabel(filename);
                }

//                //debug
//                System.out.println(allImagePoints.toString());
//                for (int n = 0; n < imageCounter; n++) {
//                    System.out.println("n=" + n + " " + allImagePoints.get(n).toString());
//                    for (int i = 0; i < corners.rows(); i++) {
//                        System.out.println(allImagePoints.get(n).get(i,0)[0] + " " + allImagePoints.get(n).get(i,0)[1]+" | "+ allObjectPoints.get(n).get(i,0)[0] + " " + allObjectPoints.get(n).get(i,0)[1] + " " + allObjectPoints.get(n).get(i,0)[2]);
//                    }
//                }
            } else {
                log.warning("corners not found for this image");
            }
        }
        return locPatternFound;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        GL2 gl = drawable.getGL().getGL2();

        if (patternFound && realtimePatternDetectionEnabled) {
            int n = corners.rows();
            if (n < 1) {
                log.warning("no data found to show corners");

            } else {
                int c = 3;
                int w = patternWidth;
                int h = patternHeight;
                //log.info(corners.toString()+" rows="+n+" cols="+corners.cols());
                //draw lines
                gl.glLineWidth(2f);
                gl.glColor3f(0, 0, 1);
                final List<Point> toList = corners.toList();
                //log.info("width="+w+" height="+h);
                gl.glBegin(GL.GL_LINES);
                for (int i = 0; i < h; i++) {
                    float y0 = (float) toList.get(w * i).x;
                    float y1 = (float) toList.get((w * (i + 1)) - 1).x;
                    float x0 = (float) toList.get(w * i).y;
                    float x1 = (float) toList.get((w * (i + 1)) - 1).y;
//                float y0 = corners.getFloatBuffer().get(2 * w * i);
//                float y1 = corners.getFloatBuffer().get((2 * w * (i + 1)) - 2);
//                float x0 = corners.getFloatBuffer().get((2 * w * i) + 1);
//                float x1 = corners.getFloatBuffer().get((2 * w * (i + 1)) - 1);
                    //log.info("i="+i+" x="+x+" y="+y);
                    gl.glVertex2f(y0, x0);
                    gl.glVertex2f(y1, x1);
                }
                for (int i = 0; i < w; i++) {
                    float y0 = (float) toList.get(i).x;
                    float y1 = (float) toList.get((w * (h - 1)) + i).x;
                    float x0 = (float) toList.get(i).y;
                    float x1 = (float) toList.get((w * (h - 1)) + i).y;
//                float y0 = corners.getFloatBuffer().get(2 * i);
//                float y1 = corners.getFloatBuffer().get(2 * ((w * (h - 1)) + i));
//                float x0 = corners.getFloatBuffer().get((2 * i) + 1);
//                float x1 = corners.getFloatBuffer().get((2 * ((w * (h - 1)) + i)) + 1);
                    //log.info("i="+i+" x="+x+" y="+y);
                    gl.glVertex2f(y0, x0);
                    gl.glVertex2f(y1, x1);
                }
                gl.glEnd();
                //draw corners
                gl.glLineWidth(2f);
                gl.glColor3f(1, 1, 0);
                gl.glBegin(GL.GL_LINES);
                for (int i = 0; i < n; i++) {
                    float y = (float) toList.get(i).x;
                    float x = (float) toList.get(i).y;
                    //log.info("i="+i+" x="+x+" y="+y);
                    gl.glVertex2f(y, x - c);
                    gl.glVertex2f(y, x + c);
                    gl.glVertex2f(y - c, x);
                    gl.glVertex2f(y + c, x);
                }
                gl.glEnd();
            }
        }
        /**
         * The geometry and mathematics of the pinhole camera[edit]
         *
         * The geometry of a pinhole camera NOTE: The x1x2x3 coordinate system
         * in the figure is left-handed, that is the direction of the OZ axis is
         * in reverse to the system the reader may be used to.
         *
         * The geometry related to the mapping of a pinhole camera is
         * illustrated in the figure. The figure contains the following basic
         * objects:
         *
         * A 3D orthogonal coordinate system with its origin at O. This is also
         * where the camera aperture is located. The three axes of the
         * coordinate system are referred to as X1, X2, X3. Axis X3 is pointing
         * in the viewing direction of the camera and is referred to as the
         * optical axis, principal axis, or principal ray. The 3D plane which
         * intersects with axes X1 and X2 is the front side of the camera, or
         * principal plane. Animage plane where the 3D world is projected
         * through the aperture of the camera. The image plane is parallel to
         * axes X1 and X2 and is located at distance {\displaystyle f} f from
         * the origin O in the negative direction of the X3 axis. A practical
         * implementation of a pinhole camera implies that the image plane is
         * located such that it intersects the X3 axis at coordinate -f where f
         * > 0. f is also referred to as the focal length[citation needed] of
         * the pinhole camera. A point R at the intersection of the optical axis
         * and the image plane. This point is referred to as the principal point
         * or image center. A point P somewhere in the world at coordinate
         * {\displaystyle (x_{1},x_{2},x_{3})} (x_1, x_2, x_3) relative to the
         * axes X1,X2,X3. The projection line of point P into the camera. This
         * is the green line which passes through point P and the point O. The
         * projection of point P onto the image plane, denoted Q. This point is
         * given by the intersection of the projection line (green) and the
         * image plane. In any practical situation we can assume that
         * {\displaystyle x_{3}} x_{3} > 0 which means that the intersection
         * point is well defined. There is also a 2D coordinate system in the
         * image plane, with origin at R and with axes Y1 and Y2 which are
         * parallel to X1 and X2, respectively. The coordinates of point Q
         * relative to this coordinate system is {\displaystyle (y_{1},y_{2})}
         * (y_1, y_2) .*
         */
        if (principlePoint != null) {
            gl.glLineWidth(3f);
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(principlePoint.x - 4, principlePoint.y);
            gl.glVertex2f(principlePoint.x + 4, principlePoint.y);
            gl.glVertex2f(principlePoint.x, principlePoint.y - 4);
            gl.glVertex2f(principlePoint.x, principlePoint.y + 4);
            gl.glEnd();

        }

        if (realtimePatternDetectionEnabled && !patternFound) {
            float cx = chip.getSizeX() / 2f;
            float y = chip.getSizeY() * 0.92f;
            y = drawOverlayLine(cx, y, 0.5f, Color.yellow,
                    String.format("No chessboard: patternWidth x patternHeight = %d x %d", patternWidth, patternHeight));
            y = drawOverlayLine(cx, y, 0.5f, Color.yellow,
                    "Set them to the chart's internal corners (number of squares minus one on each side)");
            if (lastCornerSearchSummary != null && lastCornerSearchSummary.contains("HINT:")) {
                drawOverlayLine(cx, y, 0.5f, Color.orange, "Try swapping patternWidth and patternHeight");
            }
        }

        if (autocaptureCalibrationFramesEnabled
                || (!hideStatisticsAndStatus && (calibrationString != null))) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .15f);
            MultilineAnnotationTextRenderer.setFontSize(fontSize);
            if (!hideStatisticsAndStatus && (calibrationString != null)) {
                MultilineAnnotationTextRenderer.setColor(Color.green);
                MultilineAnnotationTextRenderer.renderMultilineString(calibrationString);
            }
            if (autocaptureCalibrationFramesEnabled) {
                String hint = patternFound ? "" : " — hold chessboard in view";
                MultilineAnnotationTextRenderer.setColor(Color.yellow);
                MultilineAnnotationTextRenderer.renderMultilineString(
                        String.format("Autocapture %d/%d%s", nAcqFrames, numAutoCaptureFrames, hint));
            }
            if (!textRendererScaleSet) {
                textRendererScaleSet = true;
            }
        }
        renderTransientOverlay();
    }

    private void showTransientOverlay(String msg, Color color) {
        overlayMessage = msg;
        overlayColor = color != null ? color : Color.yellow;
        overlayUntilMs = System.currentTimeMillis() + OVERLAY_MS;
    }

    private void renderTransientOverlay() {
        if (overlayMessage == null || System.currentTimeMillis() > overlayUntilMs) {
            overlayMessage = null;
            return;
        }
        float cx = chip.getSizeX() / 2f;
        float cy = chip.getSizeY() * 0.55f;
        String[] lines = overlayMessage.split("\n");
        for (String line : lines) {
            cy = drawOverlayLine(cx, cy, 0.5f, overlayColor, line);
        }
    }

    /**
     * Default {@link #fontSize} from chip width (~6 on DAVIS346).
     */
    private int estimatedFontSize() {
        int w = chip != null ? chip.getSizeX() : 0;
        if (w <= 0) {
            return 6;
        }
        return Math.max(4, Math.min(14, Math.round(6f * w / 346f)));
    }

    /**
     * Draws one overlay line at {@code fontSize} and returns the next y (below).
     */
    private float drawOverlayLine(float x, float y, float alignX, Color color, String s) {
        java.awt.geom.Rectangle2D r = DrawGL.drawStringDropShadow(Math.max(1, fontSize), x, y, alignX, color, s);
        float h = r != null ? (float) r.getHeight() : fontSize;
        return y - h * 1.25f;
    }

    @Override
    public synchronized final void resetFilter() {
        filterChain.reset();
        patternFound = false;
        imageCounter = 0;
        autocaptureCalibrationFramesEnabled = false;
        nAcqFrames = 0;
        seenApsFrame = false;
        lastRenderedSampleMs = 0;
        loggedFirstChessboardFind = false;
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        if (!calibrated) {
            cameraMatrix = new Mat();
            distortionCoefs = new Mat();
            loadCalibration();
        }
        fontSize = getInt("fontSize", estimatedFontSize());
        resetFilter();
    }

    /**
     * @return the realtimePatternDetectionEnabled
     */
    public boolean isRealtimePatternDetectionEnabled() {
        return realtimePatternDetectionEnabled;
    }

    /**
     * @param realtimePatternDetectionEnabled the
     * realtimePatternDetectionEnabled to set
     */
    public void setRealtimePatternDetectionEnabled(boolean realtimePatternDetectionEnabled) {
        this.realtimePatternDetectionEnabled = realtimePatternDetectionEnabled;
        putBoolean("realtimePatternDetectionEnabled", realtimePatternDetectionEnabled);
    }

    /**
     * @return the cornerSubPixRefinement
     */
    public boolean isCornerSubPixRefinement() {
        return cornerSubPixRefinement;
    }

    /**
     * @param cornerSubPixRefinement the cornerSubPixRefinement to set
     */
    public void setCornerSubPixRefinement(boolean cornerSubPixRefinement) {
        this.cornerSubPixRefinement = cornerSubPixRefinement;
    }

    synchronized public void doSetPath() {
        ensureCalibrationDir();
        JFileChooser j = new JFileChooser();
        j.setCurrentDirectory(new File(dirPath));
        j.setApproveButtonText("Select");
        j.setDialogTitle("Select a folder and base file name for calibration images");
        j.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES); // let user specify a base filename
        int ret = j.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        //imagesDirPath = j.getSelectedFile().getAbsolutePath();
        dirPath = j.getCurrentDirectory().getPath();
        fileBaseName = j.getSelectedFile().getName();
        if (!fileBaseName.isEmpty()) {
            fileBaseName = "-" + fileBaseName;
        }
        log.log(Level.INFO, "Changed images path to {0}", dirPath);
        putString("dirPath", dirPath);
    }

    /**
     * Does the calibration based on collected points.
     *
     */
    public void doCalibrate() {
        if ((allImagePoints == null) || (allObjectPoints == null) || allObjectPoints.isEmpty()) {
            String msg = "Calibrate: no images collected.\nUse CaptureSingleFrame or TriggerAutocapture first.";
            log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
            showTransientOverlay(msg, Color.orange);
            return;
        }
        //init
        Size imgSize = new Size(sx, sy);
        Mat cameraMtx = Mat.eye(3, 3, CvType.CV_64F);
        Mat distCoefs = Mat.zeros(5, 1, CvType.CV_64F);
        ArrayList<Mat> rotationVecs = new ArrayList<Mat>();
        ArrayList<Mat> translationVecs = new ArrayList<Mat>();

        log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), (int) imgSize.width, (int) imgSize.height));
        try {
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
            Calib3d.calibrateCamera(allObjectPoints, allImagePoints, imgSize, cameraMtx, distCoefs, rotationVecs, translationVecs);
            if (cameraMtx.empty() || countNonZero(cameraMtx) == 0) {
                log.warning("calibrateCamera returned an empty camera matrix");
                showTransientOverlay("Calibration failed: empty camera matrix", Color.red);
                return;
            }
            synchronized (this) {
                this.cameraMatrix = cameraMtx;
                this.distortionCoefs = distCoefs.empty() ? Mat.zeros(5, 1, CvType.CV_64F) : distCoefs;
                this.rotationVectors = rotationVecs;
                this.translationVectors = translationVecs;
                undistortedAddressLUT = null;
                isUndistortedAddressLUTgenerated = false;
                generateCalibrationString();
            }
            log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
                    + "\nCamera matrix: " + cameraMtx.toString() + "\n" + printMatD(cameraMtx)
                    + "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distCoefs.toString() + "\n" + printMatD(distCoefs)
                    + calibrationString);
            showTransientOverlay(String.format(
                    "Calibration OK (%d images)\nf=%.1f px (%.2f mm)\nprincipal point %.1f, %.1f",
                    allObjectPoints.size(), focalLengthPixels, focalLengthMm,
                    principlePoint.x, principlePoint.y), Color.green);
            getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
            generateUndistortedAddressLUT();
        } catch (RuntimeException e) {
            log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
            showTransientOverlay("Calibration failed\n" + e.getMessage() + "\nNeed more / better chessboard views", Color.red);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Generate a look-up table that maps the entire chip to undistorted
     * addresses.
     *
     * @param sx chip size x
     * @param sy chip size y
     */
    public void generateUndistortedAddressLUT() {
        if (!calibrated) {
            return;
        }
        if ((sx == 0) || (sy == 0)) {
            return;
        }
        Mat cam = getCameraMatrix();
        Mat dist = getDistortionCoefs();
        if (cam == null || cam.empty() || countNonZero(cam) == 0) {
            log.warning("cannot build undistort LUT: cameraMatrix is empty");
            return;
        }
        if (dist == null || dist.empty()) {
            dist = Mat.zeros(5, 1, CvType.CV_64F);
        }
        MatOfPoint2f src = new MatOfPoint2f();
        Point[] pts = new Point[sx * sy];
        int i = 0;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                pts[i++] = new Point(x, y);
            }
        }
        src.fromArray(pts);
        MatOfPoint2f dst = new MatOfPoint2f();
        try {
            Calib3d.undistortPoints(src, dst, cam, dist);
        } catch (RuntimeException e) {
            log.warning("undistortPoints failed building LUT: " + e);
            return;
        }
        Point[] uv = dst.toArray();
        if (uv == null || uv.length != (sx * sy)) {
            log.warning(String.format("undistortPoints returned %s points, expected %d",
                    uv == null ? "null" : Integer.toString(uv.length), sx * sy));
            return;
        }
        float fx = (float) cam.get(0, 0)[0];
        float fy = (float) cam.get(1, 1)[0];
        float cx = (float) cam.get(0, 2)[0];
        float cy = (float) cam.get(1, 2)[0];
        undistortedAddressLUT = new short[2 * sx * sy];
        i = 0;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                int idx = 2 * (y + (sy * x));
                undistortedAddressLUT[idx] = (short) Math.round((uv[i].x * fx) + cx);
                undistortedAddressLUT[idx + 1] = (short) Math.round((uv[i].y * fy) + cy);
                i++;
            }
        }
        isUndistortedAddressLUTgenerated = true;
    }

    public boolean isUndistortedAddressLUTgenerated() {
        return isUndistortedAddressLUTgenerated;
    }

    private void generateCalibrationString() {
        if ((cameraMatrix == null) || countNonZero(cameraMatrix) == 0) {
            calibrationString = SINGLE_CAMERA_CALIBRATION_UNCALIBRATED;
            calibrated = false;
            return;
        }

//        DoubleBufferIndexer cameraMatrixIndexer = cameraMatrix.createIndexer();
        // Average focal lengths for X and Y axis (fx, fy).
        focalLengthPixels = (float) (cameraMatrix.get(0, 0)[0] + cameraMatrix.get(1, 1)[0]) / 2;

        // Go from pixels to millimeters, by multiplying by pixel size (in mm).
        focalLengthMm = chip.getPixelWidthUm() * 1e-3f * focalLengthPixels;

        principlePoint = new Point2D.Float((float) cameraMatrix.get(0, 2)[0], (float) cameraMatrix.get(1, 2)[0]);
        StringBuilder sb = new StringBuilder();
        if (imageCounter > 0) {
            sb.append(String.format("Using %d images", imageCounter));
            if (!saved) {
                sb.append("; not yet saved\n");
            } else {
                sb.append("; saved\n");
            }
        } else {
            sb.append(String.format("Path:%s\n", shortenDirPath(dirPath)));
        }
        sb.append(String.format("focal length avg=%.1f pixels=%.2f mm\nPrincipal point (green cross)=%.1f,%.1f, Chip size/2=%.0f,%.0f\n",
                focalLengthPixels, focalLengthMm,
                principlePoint.x, principlePoint.y,
                (float) chip.getSizeX() / 2, (float) chip.getSizeY() / 2));
        calibrationString = sb.toString();
        calibrated = true;
        textRendererScaleSet = false;
    }
    private static final String SINGLE_CAMERA_CALIBRATION_UNCALIBRATED = "SingleCameraCalibration: uncalibrated";

    public String shortenDirPath(String dirPath) {
        String dirComp = dirPath;
        if (dirPath.length() > 30) {
            int n = dirPath.length();
            dirComp = dirPath.substring(0, 10) + "..." + dirPath.substring(n - 20, n);
        }
        return dirComp;
    }

    synchronized public void doSaveCalibration() {
        if (!calibrated) {
            JOptionPane.showMessageDialog(null, "No calibration yet");
            return;
        }
        ensureCalibrationDir();
        JFileChooser j = new JFileChooser();
        j.setCurrentDirectory(new File(dirPath));
        j.setApproveButtonText("Select folder");
        j.setDialogTitle("Select a folder to store calibration XML files");
        j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
        int ret = j.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        dirPath = j.getSelectedFile().getPath();
        putString("dirPath", dirPath);
        try {
            serializeMat(dirPath, "cameraMatrix", cameraMatrix);
            serializeMat(dirPath, "distortionCoefs", distortionCoefs);
            saved = true;
        } catch (IOException ex) {
            log.warning(String.format("Could not save cameraMatrix and distortionCoefs calibration to %s: got %s", dirPath, ex.toString()));
        }
        generateCalibrationString();
    }

    static void setButtonState(Container c, String buttonString, boolean flag) {
        int len = c.getComponentCount();
        for (int i = 0; i < len; i++) {
            Component comp = c.getComponent(i);

            if (comp instanceof JButton) {
                JButton b = (JButton) comp;

                if (buttonString.equals(b.getText())) {
                    b.setEnabled(flag);
                }

            } else if (comp instanceof Container) {
                setButtonState((Container) comp, buttonString, flag);
            }
        }
    }

    synchronized public void doLoadCalibration() {
        ensureCalibrationDir();
        final JFileChooser j = new JFileChooser();
        j.setCurrentDirectory(new File(dirPath));
        j.setApproveButtonText("Select folder");
        j.setDialogTitle("Select a folder that has cameraMatrix.yml and distortionCoefs.yml files storing calibration");
        j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
        j.setApproveButtonText("Select folder");
        j.setApproveButtonToolTipText("Only enabled for a folder that has cameraMatrix.xml and distortionCoefs.xml");
        setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
        j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent pce) {
                setButtonState(j, j.getApproveButtonText(), calibrationExists(j.getCurrentDirectory().getPath()));
            }
        });
        int ret = j.showOpenDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        dirPath = j.getSelectedFile().getPath();
        putString("dirPath", dirPath);

        loadCalibration();
    }

    private boolean calibrationExists(String dirPath) {
        String fn = dirPath + File.separator + "cameraMatrix.yml";
        File f = new File(fn);
        boolean cameraMatrixExists = f.exists();
        fn = dirPath + File.separator + "distortionCoefs.yml";
        f = new File(fn);
        boolean distortionCoefsExists = f.exists();
        if (distortionCoefsExists && cameraMatrixExists) {
            return true;
        } else {
            return false;
        }
    }

    synchronized public void doClearCalibration() {
        calibrated = false;
        calibrationString = SINGLE_CAMERA_CALIBRATION_UNCALIBRATED;
        undistortedAddressLUT = null;
        isUndistortedAddressLUTgenerated = false;
        cameraMatrix = new Mat();
        distortionCoefs = new Mat();
    }

    synchronized public void doClearImages() {
        imageCounter = 0;
        if (allImagePoints != null) {
            allImagePoints.clear();
        }
        if (allObjectPoints != null) {
            allObjectPoints.clear();
        }
        generateCalibrationString();
    }

    private void loadCalibration() {
        try {
            cameraMatrix = deserializeMat(dirPath, "cameraMatrix");
            distortionCoefs = deserializeMat(dirPath, "distortionCoefs");
            generateCalibrationString();
            if (calibrated) {
                log.info("Calibrated: loaded cameraMatrix and distortionCoefs from folder " + dirPath);
            } else {
                log.warning("Uncalibrated: Something was wrong with calibration files so that cameraMatrix or distortionCoefs could not be loaded");
            }
            getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
        } catch (Exception i) {
            log.warning("Could not load existing calibration from folder " + dirPath + " on construction:" + i.toString());
        }
    }

    /**
     * Writes a YAML XML file for the matrix X called path/X.xml
     *
     * @param dir path to folder
     * @param name base name of file
     * @param m the Mat to write
     * @throws java.io.IOException
     */
    public void serializeMat(final String dir, final String name, final Mat m) throws IOException {
        String fn = dir + File.separator + name + ".yml";
        YamlMatFileStorage yamlFileStorage = new YamlMatFileStorage();
        yamlFileStorage.writeMatYml(fn, m);

//            // convert org.opencv.core.Mat to opencv_core.Mat to use FileStorage class; see https://github.com/bytedeco/javacpp/issues/38
//            Mat bdMat = new Mat() {
//                {
//                    address = sMat.getNativeObjAddr();
//                }
//            };
//            opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
//            storage.writeObj(name, bdMat);
//            storage.release();
    }

    /**
     *
     * @param dir
     * @param name
     * @return
     * @throws IOException
     * @throws YamlException
     */
    public Mat deserializeMat(String dir, String name) throws IOException, YamlException {
        String fn = dirPath + File.separator + name + ".yml";
        YamlMatFileStorage y = new YamlMatFileStorage();
        Mat mat;
        try {
            mat = y.readMatYml(fn);
            return mat;
        } catch (FileNotFoundException ex) {
            log.info(String.format("No calibration loaded: %s", ex.toString()));
            return null;
        } catch (YamlException yex) {
            log.warning(String.format("Calibration file format incorrect: %s", yex.toString()));
            return null;
        }
//        opencv_core.Mat bdMat = new opencv_core.Mat();
//
//        opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.READ);
//        opencv_core.read(storage.get(name), bdMat);
//        storage.release();
//        if (bdMat.empty()) {
//            return null;
//        }
//        // convert to org.opencv.core.Mat to return; see https://github.com/bytedeco/javacpp/issues/38
//        org.opencv.core.Mat mat = new org.opencv.core.Mat(bdMat.address());
    }

    synchronized public void doDisplayCalibrationImage() {
        displayCalibrationImage();
    }

    synchronized public void doCaptureSingleFrame() {
        captureTriggered = true;
        saved = false;
        showTransientOverlay(String.format("Capture: waiting for chessboard (%d stored)", imageCounter), Color.yellow);
    }

    synchronized public void doTriggerAutocapture() {
        nAcqFrames = 0;
        saved = false;
        autocaptureCalibrationFramesEnabled = true;
        lastAutocaptureTimeMs = 0;
    }

    private String printMatD(Mat M) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                sb.append(String.format("%10.5f\t", M.get(i, j)[0]));
                c++;
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * @return the patternWidth
     */
    public int getPatternWidth() {
        return patternWidth;
    }

    /**
     * @param patternWidth the patternWidth to set
     */
    public void setPatternWidth(int patternWidth) {
        this.patternWidth = patternWidth;
        putInt("patternWidth", patternWidth);
    }

    /**
     * @return the patternHeight
     */
    public int getPatternHeight() {
        return patternHeight;
    }

    /**
     * @param patternHeight the patternHeight to set
     */
    public void setPatternHeight(int patternHeight) {
        this.patternHeight = patternHeight;
        putInt("patternHeight", patternHeight);
    }

    /**
     * @return the rectangleHeightMm
     */
    public int getRectangleHeightMm() {
        return rectangleHeightMm;
    }

    /**
     * @param rectangleHeightMm the rectangleHeightMm to set
     */
    public void setRectangleHeightMm(int rectangleHeightMm) {
        this.rectangleHeightMm = rectangleHeightMm;
        putInt("rectangleHeightMm", rectangleHeightMm);
    }

    /**
     * @return the rectangleHeightMm
     */
    public int getRectangleWidthMm() {
        return rectangleWidthMm;
    }

    /**
     * @param rectangleWidthMm the rectangleWidthMm to set
     */
    public void setRectangleWidthMm(int rectangleWidthMm) {
        this.rectangleWidthMm = rectangleWidthMm;
        putInt("rectangleWidthMm", rectangleWidthMm);
    }

    /**
     * @return the showUndistortedFrames
     */
    public boolean isShowUndistortedFrames() {
        return showUndistortedFrames;
    }

    /**
     * @param showUndistortedFrames the showUndistortedFrames to set
     */
    public void setShowUndistortedFrames(boolean showUndistortedFrames) {
        this.showUndistortedFrames = showUndistortedFrames;
        putBoolean("showUndistortedFrames", showUndistortedFrames);
    }

    /**
     * Returns the camera calibration matrix, as specified in
     * <a href="http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html">OpenCV
     * camera calibration</a>
     * <p>
     * The matrix entries can be accessed as shown in code snippet below. Note
     * order of matrix entries returned is column-wise; the inner loop is
     * vertically over column or y index:
     * <pre>
     * Mat M;
     * for (int i = 0; i < M.rows(); i++) {
     *  for (int j = 0; j < M.cols(); j++) {
     *      M.getDoubleBuffer().get(c));
     *      c++;
     *  }
     * }
     * </pre> @return the cameraMatrix
     */
    public Mat getCameraMatrix() {
        return cameraMatrix;
    }

    /**
     * http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html
     *
     * @return the distortionCoefs
     */
    public Mat getDistortionCoefs() {
        return distortionCoefs;
    }

    /**
     * Human friendly summary of calibration
     *
     * @return the calibrationString
     */
    public String getCalibrationString() {
        return calibrationString;
    }

    /**
     *
     * @return true if calibration was completed successfully
     */
    public boolean isCalibrated() {
        return calibrated;
    }

    /**
     * @return the look-up table of undistorted pixel addresses. The index i is
     * obtained by iterating column-wise over the pixel array (y-loop is inner
     * loop) until getting to (x,y). Have to multiply by two because both x and
     * y addresses are stored consecutively. Thus, i = 2 * (y + sizeY * x)
     */
    private short[] getUndistortedAddressLUT() {
        return undistortedAddressLUT;
    }

    /**
     * @return the undistorted pixel address. The input index i is obtained by
     * iterating column-wise over the pixel array (y-loop is inner loop) until
     * getting to (x,y). Have to multiply by two because both x and y addresses
     * are stored consecutively. Thus, i = 2 * (y + sizeY * x)
     */
    private short getUndistortedAddressFromLUT(int i) {
        return undistortedAddressLUT[i];
    }

    /**
     * Transforms an event to undistorted address, using the LUT computed from
     * calibration
     *
     * @param e input event. The address x and y are modified to the unmodified
     * address. If the address falls outside the Chip boundaries, the event is
     * filtered out.
     * @return true if the transformation succeeds within chip boundaries, false
     * if the event has been filtered out.
     */
    public boolean undistortEvent(BasicEvent e) {
        if (undistortedAddressLUT == null) {
            generateUndistortedAddressLUT();
        }
        if (undistortedAddressLUT == null) {
            return false;
        }
        int uidx = 2 * (e.y + (sy * e.x));
        if (uidx > (undistortedAddressLUT.length - 1)) {
            log.warning("bad DVS address, outside of LUT table, filtering out; event =" + e);
            e.setFilteredOut(true);
            return false;
        }
        e.x = getUndistortedAddressFromLUT(uidx);
        e.y = getUndistortedAddressFromLUT(uidx + 1);
        if (xeob(e.x) || yeob(e.y)) {
            e.setFilteredOut(true);
            return false;
        }
        return true;
    }

    /**
     * Transforms the list of Point2D.Float by undistorting each point, in
     * place. Returns immediately if not calibrated.
     *
     * @param points
     */
    public void undistortPoints(ArrayList<Point2D.Float> points) {
        if (!isCalibrated()) {
            log.warning("not calibrated, doing nothing");
        }
//        FloatPointer fp = new FloatPointer(2 * points.size());
        Mat fp = new Mat(1, 2 * points.size(), CvType.CV_32FC2);
        int idx = 0;
        for (Point2D.Float p : points) {
            fp.put(0, idx++, p.x);
            fp.put(0, idx++, p.y);
        }
        MatOfPoint2f dst = new MatOfPoint2f();
        MatOfPoint2f pixelArray = new MatOfPoint2f(fp); // make wide 2 channel matrix of source event x,y
        Calib3d.undistortPoints(pixelArray, dst, getCameraMatrix(), getDistortionCoefs());
        // get the camera matrix elements (focal lengths and principal point)
//        DoubleIndexer k = getCameraMatrix().createIndexer();
        float fx, fy, cx, cy;
        fx = (float) getCameraMatrix().get(0, 0)[0];
        fy = (float) getCameraMatrix().get(1, 1)[0];
        cx = (float) getCameraMatrix().get(0, 2)[0];
        cy = (float) getCameraMatrix().get(1, 2)[0];
        idx = 0;
//        FloatBuffer b = dst.getFloatBuffer();
        for (Point2D.Float p : points) {
            p.x = (float) ((dst.get(0, idx++)[0] * fx) + cx);
            p.y = (float) ((dst.get(0, idx++)[0] * fy) + cy);
        }
    }

    private boolean xeob(int x) {
        if ((x < 0) || (x > (sx - 1))) {
            return true;
        }
        return false;
    }

    private boolean yeob(int y) {
        if ((y < 0) || (y > (sy - 1))) {
            return true;
        }
        return false;
    }

    /**
     * @return the undistortDVSevents
     */
    public boolean isUndistortDVSevents() {
        return undistortDVSevents;
    }

    /**
     * @param undistortDVSevents the undistortDVSevents to set
     */
    public void setUndistortDVSevents(boolean undistortDVSevents) {
        this.undistortDVSevents = undistortDVSevents;
    }

    /**
     * @return the hideStatisticsAndStatus
     */
    public boolean isHideStatisticsAndStatus() {
        return hideStatisticsAndStatus;
    }

    /**
     * @param hideStatisticsAndStatus the hideStatisticsAndStatus to set
     */
    public void setHideStatisticsAndStatus(boolean hideStatisticsAndStatus) {
        this.hideStatisticsAndStatus = hideStatisticsAndStatus;
        putBoolean("hideStatisticsAndStatus", hideStatisticsAndStatus);
    }

    /**
     * @return the autocaptureCalibrationFrameDelayMs
     */
    public int getAutocaptureCalibrationFrameDelayMs() {
        return autocaptureCalibrationFrameDelayMs;
    }

    /**
     * @param autocaptureCalibrationFrameDelayMs the
     * autocaptureCalibrationFrameDelayMs to set
     */
    public void setAutocaptureCalibrationFrameDelayMs(int autocaptureCalibrationFrameDelayMs) {
        this.autocaptureCalibrationFrameDelayMs = autocaptureCalibrationFrameDelayMs;
        putInt("autocaptureCalibrationFrameDelayMs", autocaptureCalibrationFrameDelayMs);
    }

    /**
     * @return the numAutoCaptureFrames
     */
    public int getNumAutoCaptureFrames() {
        return numAutoCaptureFrames;
    }

    /**
     * @param numAutoCaptureFrames the numAutoCaptureFrames to set
     */
    public void setNumAutoCaptureFrames(int numAutoCaptureFrames) {
        this.numAutoCaptureFrames = numAutoCaptureFrames;
        putInt("numAutoCaptureFrames", numAutoCaptureFrames);
    }

    public FrameSource getFrameSource() {
        return frameSource;
    }

    public void setFrameSource(FrameSource frameSource) {
        FrameSource old = this.frameSource;
        this.frameSource = frameSource;
        putString("frameSource", frameSource.name());
        getSupport().firePropertyChange("frameSource", old, frameSource);
        if (frameSource == FrameSource.RenderedEventFrames) {
            seenApsFrame = false;
        }
    }

    public int getRenderedFrameIntervalMs() {
        return renderedFrameIntervalMs;
    }

    public void setRenderedFrameIntervalMs(int renderedFrameIntervalMs) {
        int v = renderedFrameIntervalMs < 20 ? 20 : renderedFrameIntervalMs;
        this.renderedFrameIntervalMs = v;
        putInt("renderedFrameIntervalMs", v);
    }

    public boolean isShowAPSFrameDisplay() {
        return frameExtractor.isShowAPSFrameDisplay();
    }

    public void setShowAPSFrameDisplay(boolean showAPSFrameDisplay) {
        putBoolean("showAPSFrameDisplay", showAPSFrameDisplay);
        frameExtractor.setShowAPSFrameDisplay(showAPSFrameDisplay);
    }

    /**
     * Displays a JFrame with the calibration image.
     */
    private void displayCalibrationImage() {

        Thread t = new Thread() {

            @Override
            public void run() {
                JFrame frame = new JFrame("CalibrationImage");  // make a JFrame to hold it
                frame.setPreferredSize(new Dimension(800, 600));  // set the window size
                frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

                final ImageDisplay disp = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
                int s = 0;
                disp.setPreferredSize(new Dimension(s, s));

                frame.getContentPane().add(disp); // add the GLCanvas to the center of the window
                frame.pack(); // otherwise it wont fill up the display

//                final Point2D.Float mousePoint = new Point2D.Float();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // closing the frame exits
                frame.setVisible(true); // make the frame visible
                int sizex = getPatternWidth() + 1, sizey = getPatternHeight() + 1;  // used later to define image size
                disp.setImageSize(sizex, sizey); // set dimensions of image		disp.setxLabel("x label"); // add xaxis label and some tick markers
                disp.addXTick(0, "0");
                disp.addXTick(sizex, Integer.toString(sizex));
//                disp.addXTick(sizey / 2, Integer.toString(sizey / 2));

//                disp.setyLabel("y label"); // same for y axis
                disp.addYTick(0, "0");
                disp.addYTick(sizey, Integer.toString(sizey));
//                disp.addYTick(sizey / 2, Integer.toString(sizey / 2));

//                int n;
//                float[] f; // get reference to pixmap array so we can set pixel values
                int sx, sy; // , xx, yy;
                disp.checkPixmapAllocation(); // make sure we have a pixmaps (not resally necessary since setting size will allocate pixmap
//                n = sizex * sizey;
//                f = disp.getPixmapArray(); // get reference to pixmap array so we can set pixel values
                sx = disp.getSizeX();
                sy = disp.getSizeY();
                // clear frame to black
//                disp.resetFrame(0);
                disp.setGrayValue(.5f);
                disp.clearImage();
                Random r = new Random();  // will use to fill display with noise

                // draw all pixels
                for (int x = 0; x < sx; x++) {
//                    int oddCol=x%2;
                    for (int y = 0; y < sy; y++) {
//                        int ind = disp.getPixMapIndex(x, y);
                        disp.setPixmapGray(x, y, (x + y) % 2);
                    }
                }

                // ask for a repaint
                disp.repaint();
            }
        };
        t.start();

    }

    /**
     * @return the fontSize
     */
    public int getFontSize() {
        return fontSize;
    }

    /**
     * @param fontSize the fontSize to set
     */
    public void setFontSize(int fontSize) {
        int old = this.fontSize;
        int v = fontSize < 1 ? 1 : fontSize;
        this.fontSize = v;
        putInt("fontSize", v);
        getSupport().firePropertyChange("fontSize", old, v);
    }

}
