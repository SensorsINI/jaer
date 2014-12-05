/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.calibration;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.davis.stereo.SimpleDepthCameraViewerApplication;
import eu.seebetter.ini.chips.ApsDvsChip;
import java.util.List;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.FrameAnnotater;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.CV_64FC3;
import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_EPS;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_ITER;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_highgui;
import org.bytedeco.javacpp.opencv_imgproc;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;

/**
 *
 * @author marc
 */
public class SingleCameraCalibration extends EventFilter2D implements FrameAnnotater {

    private int sx;
    private int sy;
    private int lastTimestamp = 0;

    private double[] lastFrame;
    
    SimpleDepthCameraViewerApplication depthViewerThread;

    //encapsulated fields
    private boolean realtimePatternDetectionEnabled = true;
    private boolean cornerSubPixRefinement = true;
    private String imagesDirPath = System.getProperty("user.dir");
    private int patternWidth = 11;
    private int patternHeight = 7;
    private int rectangleSize = 20; //size in mm
    private boolean showUndistortedFrames = false;
    private boolean takeImageOnTimestampReset = false;
    private String fileBaseName = "";

    //opencv matrices
    Mat corners;
    MatVector allImagePoints;
    MatVector allObjectPoints;
    Mat cameraMatrix;
    Mat distCoeffs;
    Mat imgIn, imgOut;

    boolean patternFound;
    int imageCounter = 0;
    boolean calibrated = false;

    private boolean actionTriggered = false;
    private int nAcqFrames = 0;
    private int nMaxAcqFrames = 3;

    private ApsFrameExtractor frameExtractor;
    private FilterChain filterChain;

    public SingleCameraCalibration(AEChip chip) {
        super(chip);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        frameExtractor.setExtRender(true);
        setEnclosedFilterChain(filterChain);
        resetFilter();
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
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.special) {
                continue;
            }

            //trigger action (on ts reset)
            if (e.timestamp < lastTimestamp && e.timestamp < 100000 && takeImageOnTimestampReset) {
                log.info("****** ACTION TRIGGRED ******");
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
                if (actionTriggered && nAcqFrames < nMaxAcqFrames) {
                    nAcqFrames++;
                }
                //take action
                if (actionTriggered && nAcqFrames == nMaxAcqFrames) {
                    patternFound = findCurrentCorners(true);
                    //reset action
                    actionTriggered = false;
                }

                if (calibrated && showUndistortedFrames) {
                    DoublePointer ip = new DoublePointer(lastFrame);
                    Mat input = new Mat(ip);
                    input.convertTo(input, CV_8U, 255, 0);
                    Mat img = input.reshape(0, sy);
                    Mat undistortedImg = new Mat();
                    opencv_imgproc.undistort(img, undistortedImg, cameraMatrix, distCoeffs);
                    Mat imgOut8u = new Mat(sy, sx, CV_8UC3);
                    cvtColor(undistortedImg, imgOut8u, CV_GRAY2RGB);
                    Mat outImgF = new Mat(sy, sx, CV_64FC3);
                    imgOut8u.convertTo(outImgF, CV_64FC3, 1.0 / 255, 0);
                    double[] outFrame = new double[sy * sx * 3];
                    outImgF.getDoubleBuffer().get(outFrame);
                    frameExtractor.setDisplayFrameRGB(outFrame);
                    frameExtractor.apsDisplay.setxLabel("lens correction enabled");
                }
            }

            //store last timestamp
            lastTimestamp = e.timestamp;
        }

        return in;
    }

    public boolean findCurrentCorners(boolean drawAndSave) {
        Size patternSize = new Size(patternWidth, patternHeight);
        corners = new Mat();
        DoublePointer ip = new DoublePointer(lastFrame);
        Mat input = new Mat(ip);
        input.convertTo(input, CV_8U, 255, 0);
        imgIn = input.reshape(0, sy);
        imgOut = new Mat(sy, sx, CV_8UC3);
        cvtColor(imgIn, imgOut, CV_GRAY2RGB);
        //opencv_highgui.imshow("test", imgIn);
        //opencv_highgui.waitKey(1);
        boolean patternFound = opencv_calib3d.findChessboardCorners(imgIn, patternSize, corners);
        if (drawAndSave) {
            //render frame
            if (patternFound && cornerSubPixRefinement) {
                opencv_core.TermCriteria tc = new opencv_core.TermCriteria(CV_TERMCRIT_EPS + CV_TERMCRIT_ITER, 30, 0.1);
                opencv_imgproc.cornerSubPix(imgIn, corners, new Size(3, 3), new Size(-1, -1), tc);
            }
            opencv_calib3d.drawChessboardCorners(imgOut, patternSize, corners, patternFound);
            Mat outImgF = new Mat(sy, sx, CV_64FC3);
            imgOut.convertTo(outImgF, CV_64FC3, 1.0 / 255, 0);
            double[] outFrame = new double[sy * sx * 3];
            outImgF.getDoubleBuffer().get(outFrame);
            frameExtractor.setDisplayFrameRGB(outFrame);
            //save image
            if (patternFound) {
                Mat imgSave = new Mat(sy, sx, CV_8U);
                opencv_core.flip(imgIn, imgSave, 0);
                String filename = "davis" + fileBaseName + "_" + String.format("%03d", imageCounter) + ".jpg";
                opencv_highgui.imwrite(imagesDirPath + "\\" + filename, imgSave);
                //save depth sensor image if enabled
                if (depthViewerThread!=null) {
                    if (depthViewerThread.isFrameCaptureRunning()) {
                        //save img
                        String fileSuffix = "_" + String.format("%03d", imageCounter) + ".jpg";
                        depthViewerThread.saveLastImage(imagesDirPath,fileSuffix);
                    }
                }
                //store image points
                if (imageCounter == 0) {
                    allImagePoints = new MatVector(100);
                    allObjectPoints = new MatVector(100);
                }
                allImagePoints.put(imageCounter, corners);
                //create and store object points
                Mat objectPoints = new Mat(corners.rows(), 1, opencv_core.CV_32FC3);
                float x = 0;
                float y = 0;
                for (int h = 0; h < patternHeight; h++) {
                    y = h * rectangleSize;
                    for (int w = 0; w < patternWidth; w++) {
                        x = w * rectangleSize;
                        objectPoints.getFloatBuffer().put(3 * (patternWidth * h + w), x);
                        objectPoints.getFloatBuffer().put(3 * (patternWidth * h + w) + 1, y);
                        objectPoints.getFloatBuffer().put(3 * (patternWidth * h + w) + 2, 0);
                    }
                }
                allObjectPoints.put(imageCounter, objectPoints);
                //iterate image counter
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
            }
        }
        return patternFound;
    }

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
            gl.glBegin(GL2.GL_LINES);
            for (int i = 0; i < h; i++) {
                float y0 = corners.getFloatBuffer().get(2 * w * i);
                float y1 = corners.getFloatBuffer().get(2 * w * (i + 1) - 2);
                float x0 = corners.getFloatBuffer().get(2 * w * i + 1);
                float x1 = corners.getFloatBuffer().get(2 * w * (i + 1) - 1);
                //log.info("i="+i+" x="+x+" y="+y);
                gl.glVertex2f(y0, x0);
                gl.glVertex2f(y1, x1);
            }
            for (int i = 0; i < w; i++) {
                float y0 = corners.getFloatBuffer().get(2 * i);
                float y1 = corners.getFloatBuffer().get(2 * (w * (h - 1) + i));
                float x0 = corners.getFloatBuffer().get(2 * i + 1);
                float x1 = corners.getFloatBuffer().get(2 * (w * (h - 1) + i) + 1);
                //log.info("i="+i+" x="+x+" y="+y);
                gl.glVertex2f(y0, x0);
                gl.glVertex2f(y1, x1);
            }
            gl.glEnd();
            //draw corners
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 0);
            gl.glBegin(GL2.GL_LINES);
            for (int i = 0; i < n; i++) {
                float y = corners.getFloatBuffer().get(2 * i);
                float x = corners.getFloatBuffer().get(2 * i + 1);
                //log.info("i="+i+" x="+x+" y="+y);
                gl.glVertex2f(y, x - c);
                gl.glVertex2f(y, x + c);
                gl.glVertex2f(y - c, x);
                gl.glVertex2f(y + c, x);
            }
            gl.glEnd();
        }
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
        filterChain.reset();
        patternFound = false;
        imageCounter = 0;
        calibrated = false;
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
        //j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        j.showSaveDialog(null);
        //imagesDirPath = j.getSelectedFile().getAbsolutePath();
        imagesDirPath = j.getCurrentDirectory().getAbsolutePath();
        fileBaseName = j.getSelectedFile().getName();
        if (!fileBaseName.isEmpty()) {
            fileBaseName = "_" + fileBaseName;
        }
        log.info("Changed images path to " + imagesDirPath);
    }

    synchronized public void doCalibrate() {

        //init
        Size imgSize = new Size(sx, sy);
        cameraMatrix = new Mat();
        distCoeffs = new Mat();
        MatVector rvecs = new MatVector();
        MatVector tvecs = new MatVector();

        allImagePoints.resize(imageCounter);
        allObjectPoints.resize(imageCounter);
        //calibrate
        opencv_calib3d.calibrateCamera(allObjectPoints, allImagePoints, imgSize, cameraMatrix, distCoeffs, rvecs, tvecs);

        //debug
        System.out.println("Camera matrix: " + cameraMatrix.toString());
        printMatD(cameraMatrix);
        System.out.println("Dist coefficients: " + distCoeffs.toString());
        printMatD(distCoeffs);

        calibrated = true;

    }

    synchronized public void doTakeImage() {
        actionTriggered = true;
        nAcqFrames = nMaxAcqFrames;
    }

    private void printMatD(Mat M) {
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                System.out.print(" " + M.getDoubleBuffer().get(c));
                c++;
            }
            System.out.println();
        }
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
    }

    /**
     * @return the rectangleSize
     */
    public int getRectangleSize() {
        return rectangleSize;
    }

    /**
     * @param rectangleSize the rectangleSize to set
     */
    public void setRectangleSize(int rectangleSize) {
        this.rectangleSize = rectangleSize;
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
    }

    public void doDepthViewer() {
        try {
            System.load(System.getProperty("user.dir")+"\\jars\\openni2\\OpenNI2.dll");

            // initialize OpenNI
            OpenNI.initialize();

            List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
            if (devicesInfo.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No device is connected", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Device device = Device.open(devicesInfo.get(0).getUri());

            depthViewerThread = new SimpleDepthCameraViewerApplication(device);
            depthViewerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
