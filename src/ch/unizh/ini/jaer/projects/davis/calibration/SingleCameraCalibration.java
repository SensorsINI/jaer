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
import java.util.Iterator;
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
import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.davis.stereo.SimpleDepthCameraViewerApplication;
import com.esotericsoftware.yamlbeans.YamlException;
import java.awt.Dimension;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.TextRendererScale;
import net.sf.jaer.util.YamlMatFileStorage;
import static org.opencv.core.Core.countNonZero;

/**
 * Calibrates a single camera using DAVIS frames and OpenCV calibration methods.
 *
 * @author Marc Osswald, Tobi Delbruck
 */
@Description("Calibrates a single camera using DAVIS frames and OpenCV calibration methods")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SingleCameraCalibration extends EventFilter2D implements FrameAnnotater /* observes this to get informed about our size */ {

    static {
        String jvmVersion = System.getProperty("sun.arch.data.model");

        try {
            log.info("Loading openpnp OpenCV native libraries");
            nu.pattern.OpenCV.loadShared(); // see https://github.com/openpnp/opencv?tab=readme-ov-file

//            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
//            OpenCV.loadShared();   // search opencv native library with nu.pattern package.
            // System.loadLibrary("opencv_ffmpeg320_" + jvmVersion);   // Notice, cannot put the file type extension (.dll) here, it will appendCopy it automatically.
        } catch (UnsatisfiedLinkError e) {
            log.warning("Native OpenCV library failed to load.\n" + e + "\n See https://github.com/openpnp/opencv?tab=readme-ov-file to debug");
            // System.exit(1);
        }
    }

    private int sx; // set to chip.getSizeX()
    private int sy; // chip.getSizeY()
    private int lastTimestamp = 0;

    private float[] lastFrame = null, outFrame = null;

    /**
     * Fires property change with this string when new calibration is available
     */
    public static final String EVENT_NEW_CALIBRATION = "EVENT_NEW_CALIBRATION";

    private SimpleDepthCameraViewerApplication depthViewerThread;

    //encapsulated fields
    private boolean realtimePatternDetectionEnabled = getBoolean("realtimePatternDetectionEnabled", true);
    private boolean cornerSubPixRefinement = getBoolean("cornerSubPixRefinement", true);
    private String dirPath = getString("dirPath", System.getProperty("user.dir"));
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

    private final ApsFrameExtractor frameExtractor;
    private final FilterChain filterChain;
    private boolean saved = false;
    private boolean textRendererScaleSet = false;
    private float textRendererScale = 0.3f;
    private int noPatternFoundwarningSkipInterval = 50, noPatternFoundWarningCount = 0;

    public SingleCameraCalibration(AEChip chip) {
        super(chip);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        frameExtractor.setUseExternalRenderer(false);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("patternHeight", "height of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("patternWidth", "width of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("realtimePatternDetectionEnabled", "width of checkerboard calibration pattern in internal corner intersections");
        setPropertyTooltip("rectangleWidthMm", "width of square rectangles of calibration pattern in mm");
        setPropertyTooltip("rectangleHeightMm", "height of square rectangles of calibration pattern in mm");
        setPropertyTooltip("showUndistortedFrames", "shows the undistorted frame in the ApsFrameExtractor display, if calibration has been completed");
        setPropertyTooltip("undistortDVSevents", "applies LUT undistortion to DVS event address if calibration has been completed; events outside AEChip address space are filtered out");
        setPropertyTooltip("cornerSubPixRefinement", "refine corner locations to subpixel resolution");
        setPropertyTooltip("calibrate", "run the camera calibration on collected frame data and print results to console");
        setPropertyTooltip("depthViewer", "shows the depth or color image viewer if a Kinect device is connected via NI2 interface");
        setPropertyTooltip("displayCalibrationImage", "shows the calibration image that you can aim the camera at");
        setPropertyTooltip("setPath", "sets the folder and basename of saved images");
        setPropertyTooltip("saveCalibration", "saves calibration files to a selected folder");
        setPropertyTooltip("loadCalibration", "loads saved calibration files from selected folder");
        setPropertyTooltip("clearCalibration", "clears existing calibration, without clearing accumulated corner points (see ClearImages)");
        setPropertyTooltip("clearImages", "clears existing image corner and object points without clearing calibration (see ClearCalibration)");
        setPropertyTooltip("captureSingleFrame", "snaps a single calibration image that forms part of the calibration dataset");
        setPropertyTooltip("triggerAutocapture", "starts automatically capturing calibration frames with delay specified by autocaptureCalibrationFrameDelayMs");
        setPropertyTooltip("hideStatisticsAndStatus", "hides the status text");
        setPropertyTooltip("numAutoCaptureFrames", "Number of frames to automatically capture with min delay autocaptureCalibrationFrameDelayMs between frames");
        setPropertyTooltip("autocaptureCalibrationFrameDelayMs", "Delay after capturing automatic calibration frame");
//        loadCalibration(); // moved from here to update method so that Chip is fully constructed with correct size, etc.
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
    synchronized public EventPacket filterPacket(EventPacket in) {
        getEnclosedFilterChain().filterPacket(in);

        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        Iterator itr = ((ApsDvsEventPacket) in).fullIterator();
        while (itr.hasNext()) {
            Object o = itr.next();
            if (o == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) o;
//            if (e.isSpecial()) {
//                continue;
//            }

            //acquire new frame
            if (frameExtractor.hasNewFrameAvailable()) {
                lastFrame = frameExtractor.getNewFrame();

                //process frame
                if (realtimePatternDetectionEnabled || autocaptureCalibrationFramesEnabled) {
                    patternFound = findCurrentCorners(false); // false just checks if there are corners detected
                }

                if (patternFound
                        && (captureTriggered
                        || (autocaptureCalibrationFramesEnabled
                        && ((System.currentTimeMillis() - lastAutocaptureTimeMs) > autocaptureCalibrationFrameDelayMs)
                        && (nAcqFrames < numAutoCaptureFrames)))) {
                    nAcqFrames++;
                    findCurrentCorners(true); // true again find the corner points and saves them
                    captureTriggered = false;
                    lastAutocaptureTimeMs = System.currentTimeMillis();
                    if (nAcqFrames >= numAutoCaptureFrames) {
                        autocaptureCalibrationFramesEnabled = false;
                        log.info("finished autocapturing " + nAcqFrames + " acquired. Starting calibration in background....");
                        (new CalibrationWorker()).execute();
                    } else {
                        log.info("captured frame " + nAcqFrames);
                    }
                } else {
                    if (--noPatternFoundWarningCount < 0) {
                        log.warning(String.format("no pattern found; check pattern height and width numbers (skipping next %d warnings)", noPatternFoundwarningSkipInterval));
//                        getChip().getCanvas().getDisplayMethod().showStatusChangeText("No pattern found; check pattern height/width?");
                        noPatternFoundWarningCount = noPatternFoundwarningSkipInterval;
                    }
                }

                // undistort and show results
                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
                    float[] outFrame = undistortFrame(lastFrame);
                    frameExtractor.setDisplayFrameRGB(outFrame);
                }

                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
                    frameExtractor.setUseExternalRenderer(true); // to not alternate
                    frameExtractor.getApsDisplay().setTitleLabel("lens correction enabled");
                } else {
                    frameExtractor.setUseExternalRenderer(false); // to not alternate
                    frameExtractor.getApsDisplay().setTitleLabel("raw input image");
                }
            }

            //store last timestamp
            lastTimestamp = e.timestamp;
            if (calibrated && undistortDVSevents && ((ApsDvsEvent) e).isDVSEvent()) {
                // undistortEvent(e);
            }
        }

        return in;
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
     * Finds current corners of calibration image.
     *
     * @param drawAndSave true to draw the corners, false to just check if there
     * are corners.
     * @return true if corners found, false if not
     */
    public boolean findCurrentCorners(boolean drawAndSave) {
        Size patternSize = new Size(patternWidth, patternHeight);
        corners = new MatOfPoint2f();
        // FloatPointer ip = new FloatPointer(lastFrame);
        Mat input = new Mat(1, lastFrame.length, CvType.CV_32F);
        input.put(0, 0, lastFrame);
        input.convertTo(input, CvType.CV_8U, 255, 0);
        imgIn = input.reshape(0, sy);
        imgOut = new Mat(sy, sx, CvType.CV_8UC3);
        Imgproc.cvtColor(imgIn, imgOut, Imgproc.COLOR_GRAY2RGB);
        //opencv_highgui.imshow("test", imgIn);
        //opencv_highgui.waitKey(1);
        boolean locPatternFound;
        try {
//            log.info("finding corners...");
            locPatternFound = Calib3d.findChessboardCorners(imgIn, patternSize, corners);
//            log.info(String.format("Found corners: %s", locPatternFound));
        } catch (RuntimeException e) {
            log.warning(e.toString());
            return false;
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
            frameExtractor.setDisplayFrameRGB(outFrame);
            //save image
            if (locPatternFound) {
                Mat imgSave = new Mat(sy, sx, CvType.CV_8U);
                Core.flip(imgIn, imgSave, 0);
                String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + ".jpg";
                String fullFilePath = dirPath + File.separator + filename;
                Imgcodecs.imwrite(fullFilePath, imgSave);
                log.info("saved image " + fullFilePath);
                //save depth sensor image if enabled
                if (depthViewerThread != null) {
                    if (depthViewerThread.isFrameCaptureRunning()) {
                        //save img
                        String fileSuffix = "-" + String.format("%03d", imageCounter) + ".jpg";
                        depthViewerThread.saveLastImage(dirPath, fileSuffix);
                    }
                }
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
                frameExtractor.getApsDisplay().setxLabel(filename);

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

        if (!hideStatisticsAndStatus && (calibrationString != null)) {
            // render once to set the scale using the same TextRenderer
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .15f);
            MultilineAnnotationTextRenderer.setColor(Color.green);
            MultilineAnnotationTextRenderer.setScale(textRendererScale);
            MultilineAnnotationTextRenderer.renderMultilineString(calibrationString);
            if (!textRendererScaleSet) {
                textRendererScaleSet = true;
                String[] lines = calibrationString.split("\n", 0);
                int max = 0;
                String longestString = null;
                for (String s : lines) {
                    if (s.length() > max) {
                        max = s.length();
                        longestString = s;
                    }
                }
                textRendererScale = TextRendererScale.draw3dScale(MultilineAnnotationTextRenderer.getRenderer(), longestString, chip.getCanvas().getScale(), chip.getSizeX(), .8f);
            }
        }
    }

    @Override
    public synchronized final void resetFilter() {
        filterChain.reset();
        patternFound = false;
        imageCounter = 0;
        autocaptureCalibrationFramesEnabled = false;
        nAcqFrames = 0;
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        cameraMatrix = new Mat();
        distortionCoefs = new Mat();
        if (!calibrated) {
            loadCalibration();
        }
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
        if ((allImagePoints == null) || (allObjectPoints == null)) {
            log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
            return;
        }
        //init
        Size imgSize = new Size(sx, sy);
        // make local references to hold results while thread is processing
        Mat cameraMtx;
        Mat distCoefs;
        ArrayList<Mat> rotationVecs;
        ArrayList<Mat> translationVecs;
        cameraMtx = new Mat();
        countNonZero(cameraMtx);
        distCoefs = new Mat();
        rotationVecs = new ArrayList<Mat>();
        translationVecs = new ArrayList<Mat>();

        log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), (int) imgSize.width, (int) imgSize.height));
        //calibrate
        try {
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
            Calib3d.calibrateCamera(allObjectPoints, allImagePoints, imgSize, cameraMtx, distCoefs, rotationVecs, translationVecs);
            synchronized (this) {
                calibrated = true;
                this.cameraMatrix = cameraMtx;
                this.distortionCoefs = distCoefs;
                this.rotationVectors = rotationVecs;
                this.translationVectors = translationVecs;
            }
            generateCalibrationString();
            log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
                    + "\nCamera matrix: " + cameraMtx.toString() + "\n" + printMatD(cameraMtx)
                    + "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distCoefs.toString() + "\n" + printMatD(distCoefs)
                    + calibrationString);
            getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
        } catch (RuntimeException e) {
            log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
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
            return; // not set yet
        }
//        FloatPointer fp = new FloatPointer(2 * sx * sy);
        Mat fp = new Mat(1, sx * sy, CvType.CV_32FC2);
        int idx = 0;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                fp.put(0, idx++, x, y);
            }
        }
        MatOfPoint2f dst = new MatOfPoint2f();
        MatOfPoint2f pixelArray = new MatOfPoint2f(fp); // make wide 2 channel matrix of source event x,y
        Calib3d.undistortPoints(pixelArray, dst, getCameraMatrix(), getDistortionCoefs());
        isUndistortedAddressLUTgenerated = true;
        // get the camera matrix elements (focal lengths and principal point)
//        DoubleIndexer k = getCameraMatrix().createIndexer();
        float fx, fy, cx, cy;
        fx = (float) getCameraMatrix().get(0, 0)[0];
        fy = (float) getCameraMatrix().get(1, 1)[0];
        cx = (float) getCameraMatrix().get(0, 2)[0];
        cy = (float) getCameraMatrix().get(1, 2)[0];
        undistortedAddressLUT = new short[2 * sx * sy];

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                idx = 2 * (y + (sy * x));
                undistortedAddressLUT[idx] = (short) Math.round((dst.get(0, idx)[0] * fx) + cx);
                undistortedAddressLUT[idx + 1] = (short) Math.round((dst.get(0, idx + 1)[0] * fy) + cy);
            }
        }
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
    }

    synchronized public void doTriggerAutocapture() {
        nAcqFrames = 0;
        saved = false;
        autocaptureCalibrationFramesEnabled = true;
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

    public void doDepthViewer() {
        try {
            System.load(System.getProperty("user.dir") + "\\jars\\openni2\\OpenNI2.dll");

            // initialize OpenNI
            OpenNI.initialize();

            List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
            if (devicesInfo.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No Kinect device is connected via NI2 interface", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Device device = Device.open(devicesInfo.get(0).getUri());

            depthViewerThread = new SimpleDepthCameraViewerApplication(device);
            depthViewerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
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

}
