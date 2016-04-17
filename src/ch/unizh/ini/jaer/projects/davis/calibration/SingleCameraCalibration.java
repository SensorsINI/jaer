/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.calibration;

import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.CV_64FC3;
import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_EPS;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_ITER;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.indexer.DoubleBufferIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.davis.stereo.SimpleDepthCameraViewerApplication;
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
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.TextRendererScale;

/**
 * Calibrates a single camera using DAVIS frames and OpenCV calibration methods.
 *
 * @author Marc Osswald, Tobi Delbruck
 */
@Description("Calibrates a single camera using DAVIS frames and OpenCV calibration methods")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SingleCameraCalibration extends EventFilter2D implements FrameAnnotater, Observer /* observes this to get informed about our size */ {

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
    private boolean undistortDVSevents = getBoolean("undistortDVSevents", false);
    private boolean takeImageOnTimestampReset = getBoolean("takeImageOnTimestampReset", false);
    private boolean hideStatisticsAndStatus = getBoolean("hideStatisticsAndStatus", false);
    private String fileBaseName = "";

    //opencv matrices
    private Mat corners;  // TODO change to OpenCV java, not bytedeco http://docs.opencv.org/2.4/doc/tutorials/introduction/desktop_java/java_dev_intro.html
    private MatVector allImagePoints;
    private MatVector allObjectPoints;
    private Mat cameraMatrix;
    private Mat distortionCoefs;
    private MatVector rotationVectors;
    private MatVector translationVectors;
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

    private boolean actionTriggered = false;
    private int nAcqFrames = 0;
    private int nMaxAcqFrames = 1;

    private final ApsFrameExtractor frameExtractor;
    private final FilterChain filterChain;
    private boolean saved = false;
    private boolean textRendererScaleSet = false;
    private float textRendererScale = 0.3f;

    public SingleCameraCalibration(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        frameExtractor.setExtRender(false);
        setEnclosedFilterChain(filterChain);
        resetFilter();
        setPropertyTooltip("patternHeight", "height of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("patternWidth", "width of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("realtimePatternDetectionEnabled", "width of checkerboard calibration pattern in internal corner intersections");
        setPropertyTooltip("rectangleWidthMm", "width of square rectangles of calibration pattern in mm");
        setPropertyTooltip("rectangleHeightMm", "height of square rectangles of calibration pattern in mm");
        setPropertyTooltip("showUndistortedFrames", "shows the undistorted frame in the ApsFrameExtractor display, if calibration has been completed");
        setPropertyTooltip("undistortDVSevents", "applies LUT undistortion to DVS event address if calibration has been completed; events outside AEChip address space are filtered out");
        setPropertyTooltip("takeImageOnTimestampReset", "??");
        setPropertyTooltip("cornerSubPixRefinement", "refine corner locations to subpixel resolution");
        setPropertyTooltip("calibrate", "run the camera calibration on collected frame data and print results to console");
        setPropertyTooltip("depthViewer", "shows the depth or color image viewer if a Kinect device is connected via NI2 interface");
        setPropertyTooltip("setPath", "sets the folder and basename of saved images");
        setPropertyTooltip("saveCalibration", "saves calibration files to a selected folder");
        setPropertyTooltip("loadCalibration", "loads saved calibration files from selected folder");
        setPropertyTooltip("clearCalibration", "clears existing calibration");
        setPropertyTooltip("takeImage", "snaps a calibration image that forms part of the calibration dataset");
        setPropertyTooltip("hideStatisticsAndStatus", "hides the status text");
        loadCalibration();
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

            //trigger action (on ts reset)
            if ((e.timestamp < lastTimestamp) && (e.timestamp < 100000) && takeImageOnTimestampReset) {
                log.info("timestamp reset action trigggered");
                actionTriggered = true;
                nAcqFrames = 0;
            }

            //acquire new frame
            if (frameExtractor.hasNewFrame()) {
                lastFrame = frameExtractor.getNewFrame();

                //process frame
                if (realtimePatternDetectionEnabled) {
                    patternFound = findCurrentCorners(false);
                }

                //iterate
                if (actionTriggered && (nAcqFrames < nMaxAcqFrames)) {
                    nAcqFrames++;
                    generateCalibrationString();
                }
                //take action
                if (actionTriggered && (nAcqFrames == nMaxAcqFrames)) {
                    patternFound = findCurrentCorners(true);
                    //reset action
                    actionTriggered = false;
                }

                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
                    float[] outFrame = undistortFrame(lastFrame);
                    frameExtractor.setDisplayFrameRGB(outFrame);
                }

                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
                    frameExtractor.setExtRender(true); // to not alternate
                    frameExtractor.apsDisplay.setTitleLabel("lens correction enabled");
                } else {
                    frameExtractor.setExtRender(false); // to not alternate
                    frameExtractor.apsDisplay.setTitleLabel("raw input image");
                }
            }

            //store last timestamp
            lastTimestamp = e.timestamp;
            if (calibrated && undistortDVSevents && ((ApsDvsEvent) e).isDVSEvent()) {
                undistortEvent(e);
            }
        }

        return in;
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
        FloatPointer ip = new FloatPointer(src);
        Mat input = new Mat(ip);
        input.convertTo(input, CV_8U, 255, 0);
        Mat img = input.reshape(0, sy);
        Mat undistortedImg = new Mat();
        opencv_imgproc.undistort(img, undistortedImg, cameraMatrix, distortionCoefs);
        Mat imgOut8u = new Mat(sy, sx, CV_8UC3);
        cvtColor(undistortedImg, imgOut8u, CV_GRAY2RGB);
        Mat outImgF = new Mat(sy, sx, opencv_core.CV_32F);
        imgOut8u.convertTo(outImgF, opencv_core.CV_32F, 1.0 / 255, 0);
        if (outFrame == null) {
            outFrame = new float[sy * sx * 3];
        }
        outImgF.getFloatBuffer().get(outFrame);
        return outFrame;
    }

    public boolean findCurrentCorners(boolean drawAndSave) {
        Size patternSize = new Size(patternWidth, patternHeight);
        corners = new Mat();
        FloatPointer ip = new FloatPointer(lastFrame);
        Mat input = new Mat(ip);
        input.convertTo(input, CV_8U, 255, 0);
        imgIn = input.reshape(0, sy);
        imgOut = new Mat(sy, sx, CV_8UC3);
        cvtColor(imgIn, imgOut, CV_GRAY2RGB);
        //opencv_highgui.imshow("test", imgIn);
        //opencv_highgui.waitKey(1);
        boolean locPatternFound;
        try {
            locPatternFound = opencv_calib3d.findChessboardCorners(imgIn, patternSize, corners);
        } catch (RuntimeException e) {
            log.warning(e.toString());
            return false;
        }
        if (drawAndSave) {
            //render frame
            if (locPatternFound && cornerSubPixRefinement) {
                opencv_core.TermCriteria tc = new opencv_core.TermCriteria(CV_TERMCRIT_EPS + CV_TERMCRIT_ITER, 30, 0.1);
                opencv_imgproc.cornerSubPix(imgIn, corners, new Size(3, 3), new Size(-1, -1), tc);
            }
            opencv_calib3d.drawChessboardCorners(imgOut, patternSize, corners, locPatternFound);
            Mat outImgF = new Mat(sy, sx, CV_64FC3);
            imgOut.convertTo(outImgF, CV_64FC3, 1.0 / 255, 0);
            float[] outFrame = new float[sy * sx * 3];
            outImgF.getFloatBuffer().get(outFrame);
            frameExtractor.setDisplayFrameRGB(outFrame);
            //save image
            if (locPatternFound) {
                Mat imgSave = new Mat(sy, sx, CV_8U);
                opencv_core.flip(imgIn, imgSave, 0);
                String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + ".jpg";
                String fullFilePath = dirPath + "\\" + filename;
                org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, imgSave);
                log.info("wrote " + fullFilePath);
                //save depth sensor image if enabled
                if (depthViewerThread != null) {
                    if (depthViewerThread.isFrameCaptureRunning()) {
                        //save img
                        String fileSuffix = "-" + String.format("%03d", imageCounter) + ".jpg";
                        depthViewerThread.saveLastImage(dirPath, fileSuffix);
                    }
                }
                //store image points
                if (imageCounter == 0) {
                    allImagePoints = new MatVector(100);
                    allObjectPoints = new MatVector(100);
                }
                allImagePoints.put(imageCounter, corners);
                //create and store object points, which are just coordinates in mm of corners of pattern as we know they are drawn on the
                // calibration target
                Mat objectPoints = new Mat(corners.rows(), 1, opencv_core.CV_32FC3);
                float x, y;
                for (int h = 0; h < patternHeight; h++) {
                    y = h * rectangleHeightMm;
                    for (int w = 0; w < patternWidth; w++) {
                        x = w * rectangleWidthMm;
                        objectPoints.getFloatBuffer().put(3 * ((patternWidth * h) + w), x);
                        objectPoints.getFloatBuffer().put((3 * ((patternWidth * h) + w)) + 1, y);
                        objectPoints.getFloatBuffer().put((3 * ((patternWidth * h) + w)) + 2, 0); // z=0 for object points
                    }
                }
                allObjectPoints.put(imageCounter, objectPoints);
                //iterate image counter
                log.info(String.format("added corner points from image %d", imageCounter));
                imageCounter++;
                frameExtractor.apsDisplay.setxLabel(filename);

//                //debug
//                System.out.println(allImagePoints.toString());
//                for (int n = 0; n < imageCounter; n++) {
//                    System.out.println("n=" + n + " " + allImagePoints.get(n).toString());
//                    for (int i = 0; i < corners.rows(); i++) {
//                        System.out.println(allImagePoints.get(n).getFloatBuffer().get(2 * i) + " " + allImagePoints.get(n).getFloatBuffer().get(2 * i + 1)+" | "+allObjectPoints.get(n).getFloatBuffer().get(3 * i) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 1) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 2));
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
            int c = 3;
            int w = patternWidth;
            int h = patternHeight;
            //log.info(corners.toString()+" rows="+n+" cols="+corners.cols());
            //draw lines
            gl.glLineWidth(2f);
            gl.glColor3f(0, 0, 1);
            //log.info("width="+w+" height="+h);
            gl.glBegin(GL.GL_LINES);
            for (int i = 0; i < h; i++) {
                float y0 = corners.getFloatBuffer().get(2 * w * i);
                float y1 = corners.getFloatBuffer().get((2 * w * (i + 1)) - 2);
                float x0 = corners.getFloatBuffer().get((2 * w * i) + 1);
                float x1 = corners.getFloatBuffer().get((2 * w * (i + 1)) - 1);
                //log.info("i="+i+" x="+x+" y="+y);
                gl.glVertex2f(y0, x0);
                gl.glVertex2f(y1, x1);
            }
            for (int i = 0; i < w; i++) {
                float y0 = corners.getFloatBuffer().get(2 * i);
                float y1 = corners.getFloatBuffer().get(2 * ((w * (h - 1)) + i));
                float x0 = corners.getFloatBuffer().get((2 * i) + 1);
                float x1 = corners.getFloatBuffer().get((2 * ((w * (h - 1)) + i)) + 1);
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
                float y = corners.getFloatBuffer().get(2 * i);
                float x = corners.getFloatBuffer().get((2 * i) + 1);
                //log.info("i="+i+" x="+x+" y="+y);
                gl.glVertex2f(y, x - c);
                gl.glVertex2f(y, x + c);
                gl.glVertex2f(y - c, x);
                gl.glVertex2f(y + c, x);
            }
            gl.glEnd();
        }

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

        if (!hideStatisticsAndStatus && calibrationString != null) {
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
        initFilter();
        filterChain.reset();
        patternFound = false;
        imageCounter = 0;
        principlePoint = null;
    }

    @Override
    public final void initFilter() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
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

    synchronized public void doCalibrate() {
        //init
        Size imgSize = new Size(sx, sy);
        cameraMatrix = new Mat();
        distortionCoefs = new Mat();
        rotationVectors = new MatVector();
        translationVectors = new MatVector();

        allImagePoints.resize(imageCounter);
        allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
        log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
        //calibrate
        try {
            opencv_calib3d.calibrateCamera(allObjectPoints, allImagePoints, imgSize, cameraMatrix, distortionCoefs, rotationVectors, translationVectors);
            generateCalibrationString();
            log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
                    + "\nCamera matrix: " + cameraMatrix.toString() + "\n" + printMatD(cameraMatrix)
                    + "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs.toString() + "\n" + printMatD(distortionCoefs)
                    + calibrationString);
        } catch (RuntimeException e) {
            log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
        } finally {
            allImagePoints.resize(100);
            allObjectPoints.resize(100);
        }
        calibrated = true;
        getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
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
        FloatPointer fp = new FloatPointer(2 * sx * sy);
        int idx = 0;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                fp.put(idx++, x);
                fp.put(idx++, y);
            }
        }
        Mat dst = new Mat();
        Mat pixelArray = new Mat(1, sx * sy, CV_32FC2, fp); // make wide 2 channel matrix of source event x,y
        opencv_imgproc.undistortPoints(pixelArray, dst, getCameraMatrix(), getDistortionCoefs());
        isUndistortedAddressLUTgenerated = true;
        // get the camera matrix elements (focal lengths and principal point)
        DoubleIndexer k = getCameraMatrix().createIndexer();
        float fx, fy, cx, cy;
        fx = (float) k.get(0, 0);
        fy = (float) k.get(1, 1);
        cx = (float) k.get(0, 2);
        cy = (float) k.get(1, 2);
        undistortedAddressLUT = new short[2 * sx * sy];

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                idx = 2 * (y + (sy * x));
                undistortedAddressLUT[idx] = (short) Math.round((dst.getFloatBuffer().get(idx) * fx) + cx);
                undistortedAddressLUT[idx + 1] = (short) Math.round((dst.getFloatBuffer().get(idx + 1) * fy) + cy);
            }
        }
    }

    public boolean isUndistortedAddressLUTgenerated() {
        return isUndistortedAddressLUTgenerated;
    }

    private void generateCalibrationString() {
        if ((cameraMatrix == null) || cameraMatrix.isNull() || cameraMatrix.empty()) {
            calibrationString = SINGLE_CAMERA_CALIBRATION_UNCALIBRATED;
            calibrated = false;
            return;
        }

        DoubleBufferIndexer cameraMatrixIndexer = cameraMatrix.createIndexer();

        focalLengthPixels = (float) (cameraMatrixIndexer.get(0, 0) + cameraMatrixIndexer.get(0, 0)) / 2;
        focalLengthMm = chip.getPixelWidthUm() * 1e-3f * focalLengthPixels;
        principlePoint = new Point2D.Float((float) cameraMatrixIndexer.get(0, 2), (float) cameraMatrixIndexer.get(1, 2));
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
        textRendererScaleSet=false;
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
        serializeMat(dirPath, "cameraMatrix", cameraMatrix);
        serializeMat(dirPath, "distortionCoefs", distortionCoefs);
        saved = true;
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
        j.setDialogTitle("Select a folder that has XML files storing calibration");
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
        String fn = dirPath + File.separator + "cameraMatrix" + ".xml";
        File f = new File(fn);
        boolean cameraMatrixExists = f.exists();
        fn = dirPath + File.separator + "distortionCoefs" + ".xml";
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
     * Writes an XML file for the matrix X called path/X.xml
     *
     * @param dir path to folder
     * @param name base name of file
     * @param sMat the Mat to write
     */
    public void serializeMat(String dir, String name, opencv_core.Mat sMat) {
        String fn = dir + File.separator + name + ".xml";

        opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
        opencv_core.write(storage, name, sMat);
        storage.release();

        log.info("saved in " + fn);
    }

    public opencv_core.Mat deserializeMat(String dir, String name) {
        String fn = dirPath + File.separator + name + ".xml";
        opencv_core.Mat mat = new opencv_core.Mat();

        opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.READ);
        opencv_core.read(storage.get(name), mat);
        storage.release();

        if (mat.empty()) {
            return null;
        }
        return mat;
    }

    synchronized public void doTakeImage() {
        actionTriggered = true;
        nAcqFrames = 0;
        saved = false;
    }

    private String printMatD(Mat M) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                sb.append(String.format("%10.5f\t", M.getDoubleBuffer().get(c)));
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
     * @return the takeImageOnTimestampReset
     */
    public boolean isTakeImageOnTimestampReset() {
        return takeImageOnTimestampReset;
    }

    /**
     * @param takeImageOnTimestampReset the takeImageOnTimestampReset to set
     */
    public void setTakeImageOnTimestampReset(boolean takeImageOnTimestampReset) {
        this.takeImageOnTimestampReset = takeImageOnTimestampReset;
        putBoolean("takeImageOnTimestampReset", takeImageOnTimestampReset);
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
        e.x = getUndistortedAddressFromLUT(uidx);
        e.y = getUndistortedAddressFromLUT(uidx + 1);
        if (xeob(e.x) || yeob(e.y)) {
            e.setFilteredOut(true);
            return false;
        }
        return true;
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

    @Override
    public void update(Observable o, Object o1) {
        if (o instanceof AEChip) {
            if (chip.getNumPixels() > 0) {
                sx = chip.getSizeX();
                sy = chip.getSizeY(); // might not yet have been set in constructor
            }
        }
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

}
