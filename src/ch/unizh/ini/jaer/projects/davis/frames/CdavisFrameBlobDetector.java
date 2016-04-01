/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import static org.bytedeco.javacpp.Loader.sizeof;
import static org.bytedeco.javacpp.helper.opencv_imgproc.cvDrawContours;
import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_core.cvCreateMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.inRange;
import static org.bytedeco.javacpp.opencv_core.cvPoint;
import static org.bytedeco.javacpp.opencv_core.cvScalar;
import static org.bytedeco.javacpp.opencv_core.split;
import static org.bytedeco.javacpp.opencv_core.KeyPoint;
import static org.bytedeco.javacpp.opencv_core.KeyPointVector;
//import static org.bytedeco.javacpp.opencv_core.CV_64FC3;
//import static org.bytedeco.javacpp.opencv_core.CV_8U;
//import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
//import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_EPS;
//import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_ITER;
//import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.CV_AA;
import static org.bytedeco.javacpp.opencv_imgproc.CV_CHAIN_APPROX_SIMPLE;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.CV_POLY_APPROX_DP;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RETR_CCOMP;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RGB2HSV;
import static org.bytedeco.javacpp.opencv_imgproc.cvApproxPoly;
import static org.bytedeco.javacpp.opencv_imgproc.cvContourPerimeter;
import static org.bytedeco.javacpp.opencv_imgproc.cvFindContours;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_features2d.SimpleBlobDetector;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.CvContour;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvPoint;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_highgui;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvScalar;
import org.bytedeco.javacpp.helper.opencv_core.AbstractIplImage;
import org.bytedeco.javacpp.indexer.DoubleBufferIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.davis.stereo.SimpleDepthCameraViewerApplication;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.filter.MedianLowpassFilter;
import org.bytedeco.javacpp.indexer.DoubleBufferIndexer;

/**
 * Detects blobs in CDAVIS frames using OpenCV SimpleBlobDetector.
 *
 * @author Marc Osswald, Tobi Delbruck, Chenghan Li
 */
@Description("Detects blobs in CDAVIS frames using OpenCV SimpleBlobDetector")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CdavisFrameBlobDetector extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private int sx;
    private int sy;
    private int lastTimestamp = 0;

    private float[] lastFrame = null, outFrame = null;

    /**
     * Fires property change with this string when new calibration is available
     */
//    public static final String EVENT_NEW_CALIBRATION = "EVENT_NEW_CALIBRATION";

//    private SimpleDepthCameraViewerApplication depthViewerThread;

    //encapsulated fields
    private boolean realtimePatternDetectionEnabled = getBoolean("realtimePatternDetectionEnabled", true);
//    private boolean cornerSubPixRefinement = getBoolean("cornerSubPixRefinement", true);
//    private String dirPath = getString("dirPath", System.getProperty("user.dir"));
//    private int patternWidth = getInt("patternWidth", 9);
//    private int patternHeight = getInt("patternHeight", 5);
//    private int rectangleHeightMm = getInt("rectangleHeightMm", 20); //height in mm
//    private int rectangleWidthMm = getInt("rectangleWidthMm", 20); //width in mm
//    private boolean showUndistortedFrames = getBoolean("showUndistortedFrames", false);
//    private boolean takeImageOnTimestampReset = getBoolean("takeImageOnTimestampReset", false);
//    private String fileBaseName = "";

    //opencv matrices
//    private Mat corners;  // TODO change to OpenCV java, not bytedeco http://docs.opencv.org/2.4/doc/tutorials/introduction/desktop_java/java_dev_intro.html
//    private MatVector allImagePoints;
//    private MatVector allObjectPoints;
//    private Mat cameraMatrix;
//    private Mat distortionCoefs;
//    private MatVector rotationVectors;
//    private MatVector translationVectors;
    private Mat imgIn, imgOut;
//    private CvSeq contours;
//    private CvMemStorage mem;
    private KeyPointVector blobCenterVector;

//    private short[] undistortedAddressLUT;
//    private boolean isUndistortedAddressLUTgenerated = false;

//    private float focalLengthPixels = 0;
//    private float focalLengthMm = 0;
//    private Point2D.Float principlePoint = null;
//    private String calibrationString = "Uncalibrated";

//    private boolean patternFound;
//    private int imageCounter = 0;
//    private boolean calibrated = false;

    private boolean actionTriggered = false;
    private int nAcqFrames = 0;
    private int nMaxAcqFrames = 1;

    private final ApsFrameExtractor frameExtractor;
    private final FilterChain filterChain;
    private final RectangularClusterTracker tracker;
    private boolean saved=false;

    private AEFrameChipRenderer renderer=null;
    private boolean rendererPropertyChangeListenerAdded=false;
    DavisChip apsDvsChip = null;
//    RectangularClusterTracker.Cluster blobs = null;
    volatile protected LinkedList<RectangularClusterTracker.Cluster> blobs = new LinkedList<>();
    
    private GLU glu = new GLU();
    private GLUquadric quad = null;
    private float[] compArray = new float[4];
    
//    int velocityMedianFilterLengthSamples = getInt("velocityMedianFilterLengthSamples", 9);
//    MedianLowpassFilter velxfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
//    MedianLowpassFilter velyfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
//    Point2D.Float ballVel = new Point2D.Float();
    
    public CdavisFrameBlobDetector(AEChip chip) {
        super(chip);
        frameExtractor = new ApsFrameExtractor(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(frameExtractor);
        filterChain.add((tracker = new RectangularClusterTracker(chip)));
        frameExtractor.setExtRender(false);
        setEnclosedFilterChain(filterChain);
        lastFrame = new float[640*480*3];
        resetFilter();



        setPropertyTooltip("patternHeight", "height of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("patternWidth", "width of chessboard calibration pattern in internal corner intersections, i.e. one less than number of squares");
        setPropertyTooltip("realtimePatternDetectionEnabled", "width of checkerboard calibration pattern in internal corner intersections");
        setPropertyTooltip("rectangleWidthMm", "width of square rectangles of calibration pattern in mm");
        setPropertyTooltip("rectangleHeightMm", "height of square rectangles of calibration pattern in mm");
        setPropertyTooltip("showUndistortedFrames", "shows the undistorted frame in the ApsFrameExtractor display, if calibration has been completed");
        setPropertyTooltip("takeImageOnTimestampReset", "??");
        setPropertyTooltip("cornerSubPixRefinement", "refine corner locations to subpixel resolution");
        setPropertyTooltip("calibrate", "run the camera calibration on collected frame data and print results to console");
        setPropertyTooltip("depthViewer", "shows the depth or color image viewer if a Kinect device is connected via NI2 interface");
        setPropertyTooltip("setPath", "sets the folder and basename of saved images");
        setPropertyTooltip("saveCalibration", "saves calibration files to a selected folder");
        setPropertyTooltip("loadCalibration", "loads saved calibration files from selected folder");
        setPropertyTooltip("clearCalibration", "clears existing calibration");
        setPropertyTooltip("takeImage", "snaps a calibration image that forms part of the calibration dataset");
//        loadCalibration();
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
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!rendererPropertyChangeListenerAdded){
            rendererPropertyChangeListenerAdded=true;
            renderer=(AEFrameChipRenderer)chip.getRenderer();
            renderer.getSupport().addPropertyChangeListener(this);
        }
        apsDvsChip = (DavisChip) chip;
//        apsFrameExtractor.filterPacket(in);
        getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)
                && !chip.getAeViewer().isPaused()) {
            FloatBuffer lastFrameBuffer = ((AEFrameChipRenderer)chip.getRenderer()).getPixmap();
            //int sx=chip.getSizeX(), sy=chip.getSizeY();
            AEFrameChipRenderer r=((AEFrameChipRenderer)chip.getRenderer());
            // rewrite frame to avoid padding for texture
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    int i=r.getPixMapIndex(x, y),j=(x*3) + ((sy-1-y)*sx*3);
                    lastFrame[j] = lastFrameBuffer.get(i + 2);
                    lastFrame[j+ 1] = lastFrameBuffer.get(i + 1);
                    lastFrame[j+ 2] = lastFrameBuffer.get(i);
                }
            }

            //trigger action (on ts reset)

//            if (takeImageOnTimestampReset) {
//                log.info("timestamp reset action trigggered");
//                actionTriggered = true;
//                nAcqFrames = 0;
//            }
                //process frame
                if (realtimePatternDetectionEnabled) {
                    findColorBlobs(false);
                    trackBlobs();
                }

                //iterate
//                if (actionTriggered && (nAcqFrames < nMaxAcqFrames)) {
//                    nAcqFrames++;
//                    generateCalibrationString();
//                }
                //take action
                if (actionTriggered && (nAcqFrames == nMaxAcqFrames)) {
                    findColorBlobs(true);
//                    trackBlobs();
                    //reset action
                    actionTriggered = false;
                }

//                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
//                    float[] outFrame = undistortFrame(lastFrame);
//                    frameExtractor.setDisplayFrameRGB(outFrame);
//                }

//                if (calibrated && showUndistortedFrames && frameExtractor.isShowAPSFrameDisplay()) {
//                    frameExtractor.setExtRender(true); // to not alternate
//                    frameExtractor.apsDisplay.setTitleLabel("lens correction enabled");
//                } else {
//                    frameExtractor.setExtRender(false); // to not alternate
//                    frameExtractor.apsDisplay.setTitleLabel("raw input image");
//                }
        }
    }

    /**
     * Undistorts an image frame using the calibration.
     *
     * @param src the source image, RGB float valued in 0-1 range
     * @return float[] destination. IAn internal float[] is created and reused.
     * If there is no calibration, the src array is returned.
     */
//    public float[] undistortFrame(float[] src) {
//        if (!calibrated) {
//            return src;
//        }
//        FloatPointer ip = new FloatPointer(src);
//        Mat input = new Mat(ip);
//        input.convertTo(input, CV_8U, 255, 0);
//        Mat img = input.reshape(0, sy);
//        Mat undistortedImg = new Mat();
//        opencv_imgproc.undistort(img, undistortedImg, cameraMatrix, distortionCoefs);
//        Mat imgOut8u = new Mat(sy, sx, CV_8UC3);
//        cvtColor(undistortedImg, imgOut8u, CV_GRAY2RGB);
//        Mat outImgF = new Mat(sy, sx, opencv_core.CV_32F);
//        imgOut8u.convertTo(outImgF, opencv_core.CV_32F, 1.0 / 255, 0);
//        if (outFrame == null) {
//            outFrame = new float[sy * sx * 3];
//        }
//        outImgF.getFloatBuffer().get(outFrame);
//        return outFrame;
//    }

    public void findColorBlobs(boolean drawAndSave) {
//        Size patternSize = new Size(patternWidth, patternHeight);
//        corners = new Mat();
        FloatPointer ip = new FloatPointer(lastFrame);
        Mat input = new Mat(ip);
        input.convertTo(input, CV_8U, 255, 0);
        imgIn = input.reshape(3, sy);
        Mat imgHsv = new Mat(ip);
        cvtColor(imgIn, imgHsv, CV_RGB2HSV);
//        log.info(printMatD(imgHsv));
        
        IplImage imgInIplImage = new IplImage(imgIn);
        

//        IplImage hue = AbstractIplImage.create( imgIn.cols(), imgIn.rows(), imgIn.arrayDepth(), CV_8U );
//        IplImage sat = AbstractIplImage.create( imgIn.cols(), imgIn.rows(), imgIn.arrayDepth(), CV_8U );
//        IplImage val = AbstractIplImage.create( imgIn.cols(), imgIn.rows(), imgIn.arrayDepth(), CV_8U );
//        IplImage hueBin = AbstractIplImage.create( imgIn.cols(), imgIn.rows(), imgIn.arrayDepth(), CV_8U );
        
        MatVector hsvChannels = new MatVector();
        
        
        
        split( imgHsv, hsvChannels );
//        log.info(printMatD(hsvChannels.get(1)));
        Mat lowerBound = new Mat(480, 640, CV_8U, new opencv_core.Scalar(160.0));
//        log.info(printMatD(lowerBound));
        Mat upperBound = new Mat(480, 640, CV_8U, new opencv_core.Scalar(200.0));
//        log.info(printMatD(upperBound));
        Mat hueBinMat = new Mat(480, 640, CV_8U, new opencv_core.Scalar(0.0));
//        log.info(printMatD(hueBinMat));
        
//        log.info(printMatD(hueBinMat));
        
//        IplImage imgHueIplImage = new IplImage(hsvChannels.get(1));
        
        inRange(hsvChannels.get(1),lowerBound,upperBound,hueBinMat);
//        log.info(printMatD(hueBinMat));
        
        SimpleBlobDetector blobDetector = SimpleBlobDetector.create(new SimpleBlobDetector.Params()
                .filterByArea(true)
                .minArea(60)
                .maxArea(9000)

                .filterByColor(true)
                .blobColor((byte) 255)

                .filterByCircularity(false)
//                .minCircularity(circularity.get(0).floatValue())
//                .maxCircularity(circularity.get(1).floatValue())
                
                .filterByConvexity(false)
                
                .filterByInertia(false)
                
        );
        blobCenterVector = new KeyPointVector();
        blobDetector.detect(hueBinMat, blobCenterVector);

        //threshold(hueMat, imgOut, 100, 255, CV_THRESH_BINARY);
        
//        CvMat imgOutCvMat = imgOut.asCvMat();
//        IplImage imgOutIplImage = imgOutCvMat.asIplImage();
//        contours = new CvSeq();
//        mem = cvCreateMemStorage(0);
//        //        CvSeq ptr = new CvSeq();
//        cvFindContours(hueBin, mem, contours, sizeof(CvContour.class) , CV_RETR_CCOMP, CV_CHAIN_APPROX_SIMPLE, cvPoint(0,0));
//        while ((contours != null) && !contours.isNull()) {
//                if (contours.elem_size() > 0) {
//                    CvSeq points = cvApproxPoly(contours, sizeof(CvContour.class),
//                            mem, CV_POLY_APPROX_DP, cvContourPerimeter(contours)*0.02, 0);
//                    cvDrawContours(imgInIplImage, points, AbstractCvScalar.BLUE, AbstractCvScalar.BLUE, -1, 1, CV_AA);
//                }
//                contours = contours.h_next();
//            }

//        Random rand = new Random();
//        for (ptr = contours; ptr != null; ptr = ptr.h_next()) {
//            Color randomColor = new Color(rand.nextFloat(), rand.nextFloat(), rand.nextFloat());
//            CvScalar color = CV_RGB( randomColor.getRed(), randomColor.getGreen(), randomColor.getBlue());
//            cvDrawContours(imgOutIplImage, ptr, color, CV_RGB(0,0,0), -1, CV_FILLED, 8, cvPoint(0,0));
//        }
//
//        Mat blobImg = new Mat(imgInIplImage);
//        Mat hueMat = new Mat(hue);
//        Mat satMat = new Mat(sat);
//        Mat valMat = new Mat(val);
        

//        opencv_highgui.imshow("Input", imgIn);
//        opencv_highgui.imshow("Hue", hsvChannels.get(1));

//        opencv_highgui.imshow("threshold Hue", hueBinMat);

//        opencv_highgui.waitKey(1000);
     }
    
    public void trackBlobs () {
        if (realtimePatternDetectionEnabled && blobCenterVector != null) {
            for (int i = 0; i < blobCenterVector.size(); i++) {
                KeyPoint blobCenter = blobCenterVector.get(i);
                float size = blobCenter.size();
                if (size > 5) {
                    RectangularClusterTracker.Cluster b = tracker.createCluster
                    (new BasicEvent(lastTimestamp, (short) blobCenter.pt().x(), (short) (480-blobCenter.pt().y())));
                    b.setMass(10000); // some big number
                    tracker.getClusters().add(b);
                    blobs.add(b);
                }
            }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        GL2 gl = drawable.getGL().getGL2();

        if (realtimePatternDetectionEnabled && blobCenterVector != null) {
            //int n = contours.total();
            //int c = 3;
            //int w = patternWidth;
            //int h = patternHeight;
            //log.info(corners.toString()+" rows="+n+" cols="+corners.cols());
            //draw lines
            gl.glLineWidth(2f);
            gl.glColor3f(0, 0, 1);
            //log.info("width="+w+" height="+h);
//            gl.glBegin(GL.GL_LINES);
            for (int i = 0; i < blobCenterVector.size(); i++) {
                KeyPoint blobCenter = blobCenterVector.get(i);
                float size = blobCenter.size();
                if (size > 5) {
                    float x0 = blobCenter.pt().x()-size/2;
                    float y0 = blobCenter.pt().y()-size/2;
                    float x1 = blobCenter.pt().x()+size/2;
                    float y1 = blobCenter.pt().y()-size/2;
                    float x2 = blobCenter.pt().x()+size/2;
                    float y2 = blobCenter.pt().y()+size/2;
                    float x3 = blobCenter.pt().x()-size/2;
                    float y3 = blobCenter.pt().y()+size/2;
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x0, 480-y0);
                    gl.glVertex2f(x1, 480-y1);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x1, 480-y1);
                    gl.glVertex2f(x2, 480-y2);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x2, 480-y2);
                     gl.glVertex2f(x3, 480-y3);
                    gl.glEnd();
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(x3, 480-y3);
                    gl.glVertex2f(x0, 480-y0);
                    gl.glEnd();
                }
            }
//            gl.glEnd();
            //draw corners
//            gl.glLineWidth(2f);
//            gl.glColor3f(1, 1, 0);
//            gl.glBegin(GL.GL_LINES);
//            for (int i = 0; i < n; i++) {
//                CvPoint v =new CvPoint(cvGetSeqElem(contours, i));
//                float y = v.x();
//                float x = v.y();
//                //log.info("i="+i+" x="+x+" y="+y);
//                gl.glVertex2f(y, x - c);
//                gl.glVertex2f(y, x + c);
//                gl.glVertex2f(y - c, x);
//                gl.glVertex2f(y + c, x);
//            }
//            gl.glEnd();
        }
        tracker.annotate(drawable);
        
//        if (principlePoint != null) {
//            gl.glLineWidth(3f);
//            gl.glColor3f(0, 1, 0);
//            gl.glBegin(GL.GL_LINES);
//            gl.glVertex2f(principlePoint.x - 4, principlePoint.y);
//            gl.glVertex2f(principlePoint.x + 4, principlePoint.y);
//            gl.glVertex2f(principlePoint.x, principlePoint.y - 4);
//            gl.glVertex2f(principlePoint.x, principlePoint.y + 4);
//            gl.glEnd();
//
//        }
//
//        if (calibrationString != null) {
//            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .15f);
//            MultilineAnnotationTextRenderer.setColor(Color.green);
//            MultilineAnnotationTextRenderer.setScale(.3f);
//            MultilineAnnotationTextRenderer.renderMultilineString(calibrationString);
//        }
        
//        if (glu == null) {
//            glu = new GLU();
//        }
//        if (quad == null) {
//            quad = glu.gluNewQuadric();
//        }
//
//        if (blobs != null) {
//            blobs.getColor().getRGBComponents(compArray);
//            gl.glColor4fv(compArray, 0);
//            gl.glPushMatrix();
//            gl.glTranslatef(blobs.location.x, blobs.location.y, 0);
//            glu.gluQuadricDrawStyle(quad, GLU.GLU_LINE);
//            gl.glLineWidth(2f);
//            //            glu.gluDisk(quad, 0, ball.getAverageEventDistance(), 16, 1);
//            float rad = blobs.getMass() / tracker.getThresholdMassForVisibleCluster();
//            if (rad > blobs.getRadius()) {
//                rad = blobs.getRadius();
//            }
//            glu.gluDisk(quad, 0, rad, 16, 1);
//            gl.glLineWidth(6f);
//            gl.glBegin(GL.GL_LINE_STRIP);
//            gl.glVertex2f(0, 0); // draw median-filtered velocity vector
//            float f = tracker.getVelocityVectorScaling();
//            gl.glVertex2f(velxfilter.getValue() * f, velyfilter.getValue() * f);
//            gl.glEnd();
//            gl.glPopMatrix();
//            Point2D.Float p = findNearestPathPoint();
//            if (p != null) {
//                gl.glPushMatrix();
//                gl.glTranslatef(p.x, p.y, 1);
//                gl.glColor4f(.7f, .25f, 0f, .5f);
//                glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
//                glu.gluDisk(quad, 0, 2, 8, 1);
//                gl.glPopMatrix();
//            }
//        }
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
        filterChain.reset();
//        tracker.resetFilter();
//        patternFound = false;
//        imageCounter = 0;
//        principlePoint = null;
//        velxfilter.setInternalValue(0);
//        velyfilter.setInternalValue(0);
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
//    public boolean isCornerSubPixRefinement() {
//        return cornerSubPixRefinement;
//    }

    /**
     * @param cornerSubPixRefinement the cornerSubPixRefinement to set
     */
//    public void setCornerSubPixRefinement(boolean cornerSubPixRefinement) {
//        this.cornerSubPixRefinement = cornerSubPixRefinement;
//    }

//    synchronized public void doSetPath() {
//        JFileChooser j = new JFileChooser();
//        j.setCurrentDirectory(new File(dirPath));
//        j.setApproveButtonText("Select");
//        j.setDialogTitle("Select a folder and base file name for calibration images");
//        j.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES); // let user specify a base filename
//        int ret = j.showSaveDialog(null);
//        if (ret != JFileChooser.APPROVE_OPTION) {
//            return;
//        }
//        //imagesDirPath = j.getSelectedFile().getAbsolutePath();
//        dirPath = j.getCurrentDirectory().getPath();
//        fileBaseName = j.getSelectedFile().getName();
//        if (!fileBaseName.isEmpty()) {
//            fileBaseName = "-" + fileBaseName;
//        }
//        log.log(Level.INFO, "Changed images path to {0}", dirPath);
//        putString("dirPath", dirPath);
//    }

//    synchronized public void doCalibrate() {
//        //init
//        Size imgSize = new Size(sx, sy);
//        cameraMatrix = new Mat();
//        distortionCoefs = new Mat();
//        rotationVectors = new MatVector();
//        translationVectors = new MatVector();
//
//        allImagePoints.resize(imageCounter);
//        allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
//        log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
//        //calibrate
//        try {
//            opencv_calib3d.calibrateCamera(allObjectPoints, allImagePoints, imgSize, cameraMatrix, distortionCoefs, rotationVectors, translationVectors);
//            generateCalibrationString();
//            log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
//                    + "\nCamera matrix: " + cameraMatrix.toString() + "\n" + printMatD(cameraMatrix)
//                    + "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs.toString() + "\n" + printMatD(distortionCoefs)
//                    + calibrationString);
//        } catch (RuntimeException e) {
//            log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
//        } finally {
//            allImagePoints.resize(100);
//            allObjectPoints.resize(100);
//        }
//        //debug
//
//        calibrated = true;
//        getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
//
//    }

    /*
    Generate a look-up table that maps the entire chip to undistorted addresses.
    */
//    public void generateUndistortedAddressLUT(int sx, int sy) {
//        if (!calibrated) {
//            return;
//        }
//        FloatPointer fp = new FloatPointer(2 * sx * sy);
//        int idx = 0;
//        for (int x = 0; x < sx; x++) {
//            for (int y = 0; y < sy; y++) {
//                fp.put(idx++, x);
//                fp.put(idx++, y);
//            }
//        }
//        Mat dst = new Mat();
//        Mat pixelArray = new Mat(1, sx * sy, CV_32FC2, fp); // make wide 2 channel matrix of source event x,y
//        opencv_imgproc.undistortPoints(pixelArray, dst, getCameraMatrix(), getDistortionCoefs());
//        isUndistortedAddressLUTgenerated = true;
//        // get the camera matrix elements (focal lengths and principal point)
//        DoubleIndexer k = getCameraMatrix().createIndexer();
//        float fx, fy, cx, cy;
//        fx = (float) k.get(0, 0);
//        fy = (float) k.get(1, 1);
//        cx = (float) k.get(0, 2);
//        cy = (float) k.get(1, 2);
//        undistortedAddressLUT = new short[2 * sx * sy];
//
//        for (int x = 0; x < sx; x++) {
//            for (int y = 0; y < sy; y++) {
//                idx = 2 * (y + (sy * x));
//                undistortedAddressLUT[idx] = (short) Math.round((dst.getFloatBuffer().get(idx) * fx) + cx);
//                undistortedAddressLUT[idx+1] = (short) Math.round((dst.getFloatBuffer().get(idx+1) * fy) + cy);
//            }
//        }
//    }



//    public boolean isUndistortedAddressLUTgenerated() {
//        return isUndistortedAddressLUTgenerated;
//    }

//    private void generateCalibrationString() {
//        if((cameraMatrix==null) || cameraMatrix.isNull() || cameraMatrix.empty()){
//            calibrationString="uncalibrated";
//            return;
//        }
//
//        DoubleBufferIndexer cameraMatrixIndexer = cameraMatrix.createIndexer();
//
//        focalLengthPixels = (float) (cameraMatrixIndexer.get(0, 0) + cameraMatrixIndexer.get(0, 0)) / 2;
//        focalLengthMm = chip.getPixelWidthUm() * 1e-3f * focalLengthPixels;
//        principlePoint = new Point2D.Float((float) cameraMatrixIndexer.get(0, 2), (float) cameraMatrixIndexer.get(1, 2));
//        StringBuilder sb=new StringBuilder();
//        if(imageCounter>0){
//            sb.append(String.format("Using %d images",imageCounter));
//            if(!saved){
//                sb.append("; not yet saved\n");
//            }else{
//                sb.append("; saved\n");
//            }
//        }else{
//            sb.append(String.format("Path:%s\n",shortenDirPath(dirPath)));
//        }
//        sb.append(String.format("focal length avg=%.1f pixels=%.2f mm\nPrincipal point (green cross)=%.1f,%.1f, Chip size/2=%d,%d\n",
//                focalLengthPixels, focalLengthMm,
//                principlePoint.x, principlePoint.y,
//                chip.getSizeX() / 2, chip.getSizeY() / 2));
//        calibrationString = sb.toString();
//    }

//    public String shortenDirPath(String dirPath) {
//        String dirComp=dirPath;
//        if(dirPath.length()>30){
//            int n=dirPath.length();
//            dirComp=dirPath.substring(0,10)+"..."+dirPath.substring(n-20, n);
//        }
//        return dirComp;
//    }

//    synchronized public void doSaveCalibration() {
//        if (!calibrated) {
//            JOptionPane.showMessageDialog(null, "No calibration yet");
//            return;
//        }
//        JFileChooser j = new JFileChooser();
//        j.setCurrentDirectory(new File(dirPath));
//        j.setApproveButtonText("Select folder");
//        j.setDialogTitle("Select a folder to store calibration XML files");
//        j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
//        int ret = j.showSaveDialog(null);
//        if (ret != JFileChooser.APPROVE_OPTION) {
//            return;
//        }
//        dirPath = j.getSelectedFile().getPath();
//        putString("dirPath", dirPath);
//        serializeMat(dirPath, "cameraMatrix", cameraMatrix);
//        serializeMat(dirPath, "distortionCoefs", distortionCoefs);
//        generateCalibrationString();
//        saved=true;
//    }

    static void setButtonState(Container c, String buttonString,boolean flag ) {
    int len = c.getComponentCount();
    for (int i = 0; i < len; i++) {
      Component comp = c.getComponent(i);

      if (comp instanceof JButton) {
        JButton b = (JButton) comp;

        if ( buttonString.equals(b.getText()) ) {
            b.setEnabled(flag);
        }

      } else if (comp instanceof Container) {
          setButtonState((Container) comp, buttonString, flag);
      }
    }
}

//    synchronized public void doLoadCalibration() {
//        final JFileChooser j = new JFileChooser();
//        j.setCurrentDirectory(new File(dirPath));
//        j.setApproveButtonText("Select folder");
//        j.setDialogTitle("Select a folder that has XML files storing calibration");
//        j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
//        j.setApproveButtonText("Select folder");
//        j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
//            @Override
//            public void propertyChange(PropertyChangeEvent pce) {
//                String fn = j.getCurrentDirectory().getPath() + File.separator + "cameraMatrix" + ".xml";
//                File f=new File(fn);
//                boolean cameraMatrixExists=f.exists();
//                fn = j.getCurrentDirectory().getPath() + File.separator + "distortionCoefs" + ".xml";
//                f=new File(fn);
//                boolean distortionCoefsExists=f.exists();
//                if(distortionCoefsExists && cameraMatrixExists){
//                    setButtonState(j, j.getApproveButtonText(), true);
//                }else{
//                    setButtonState(j, j.getApproveButtonText(), false);
//                }
//
//            }
//        });
//        int ret = j.showOpenDialog(null);
//        if (ret != JFileChooser.APPROVE_OPTION) {
//            return;
//        }
//        dirPath = j.getSelectedFile().getPath();
//        putString("dirPath", dirPath);
//
//        loadCalibration();
//    }

//    synchronized public void doClearCalibration() {
//        calibrated = false;
//        calibrationString = "Uncalibrated";
//        undistortedAddressLUT = null;
//        isUndistortedAddressLUTgenerated = false;
//    }

//    private void loadCalibration() {
//        try {
//            cameraMatrix = deserializeMat(dirPath, "cameraMatrix");
//            distortionCoefs = deserializeMat(dirPath, "distortionCoefs");
//            generateCalibrationString();
//            calibrated = true;
//            log.info("loaded cameraMatrix and distortionCoefs");
//            getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
//        } catch (Exception i) {
//            log.warning(i.toString());
//        }
//    }

    /**
     * Writes an XML file for the matrix X called path/X.xml
     *
     * @param dir path to folder
     * @param name base name of file
     * @param sMat the Mat to write
     */
//    public void serializeMat(String dir, String name, opencv_core.Mat sMat) {
//        String fn = dir + File.separator + name + ".xml";
//        opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
//        storage.writeObj(name, sMat);
//        storage.release();
//        log.info("saved in " + fn);
//    }

//    public opencv_core.Mat deserializeMat(String dir, String name) {
//        opencv_core.FileStorage storage = new opencv_core.FileStorage(dirPath + File.separator + name + ".xml", opencv_core.FileStorage.READ);
//        opencv_core.Mat mat = new opencv_core.Mat(storage.get(name).readObj());
//        return mat;
//    }

//    synchronized public void doTakeImage() {
//        actionTriggered = true;
//        nAcqFrames = 0;
//        saved=false;
//    }

    private String printMatD(Mat M) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                sb.append(String.format("%d ", (M.getByteBuffer().get(c) & 0xFF)));
                c++;
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * @return the patternWidth
     */
//    public int getPatternWidth() {
//        return patternWidth;
//    }

    /**
     * @param patternWidth the patternWidth to set
     */
//    public void setPatternWidth(int patternWidth) {
//        this.patternWidth = patternWidth;
//        putInt("patternWidth", patternWidth);
//    }

    /**
     * @return the patternHeight
     */
//    public int getPatternHeight() {
//        return patternHeight;
//    }

    /**
     * @param patternHeight the patternHeight to set
     */
//    public void setPatternHeight(int patternHeight) {
//        this.patternHeight = patternHeight;
//        putInt("patternHeight", patternHeight);
//    }

    /**
     * @return the rectangleHeightMm
     */
//    public int getRectangleHeightMm() {
//        return rectangleHeightMm;
//    }

    /**
     * @param rectangleHeightMm the rectangleHeightMm to set
     */
//    public void setRectangleHeightMm(int rectangleHeightMm) {
//        this.rectangleHeightMm = rectangleHeightMm;
//        putInt("rectangleHeightMm", rectangleHeightMm);
//    }

    /**
     * @return the rectangleHeightMm
     */
//    public int getRectangleWidthMm() {
//        return rectangleWidthMm;
//    }

    /**
     * @param rectangleWidthMm the rectangleWidthMm to set
     */
//    public void setRectangleWidthMm(int rectangleWidthMm) {
//        this.rectangleWidthMm = rectangleWidthMm;
//        putInt("rectangleWidthMm", rectangleWidthMm);
//    }

    /**
     * @return the showUndistortedFrames
     */
//    public boolean isShowUndistortedFrames() {
//        return showUndistortedFrames;
//    }

    /**
     * @param showUndistortedFrames the showUndistortedFrames to set
     */
//    public void setShowUndistortedFrames(boolean showUndistortedFrames) {
//        this.showUndistortedFrames = showUndistortedFrames;
//        putBoolean("showUndistortedFrames", showUndistortedFrames);
//    }

    /**
     * @return the takeImageOnTimestampReset
     */
//    public boolean isTakeImageOnTimestampReset() {
//        return takeImageOnTimestampReset;
//    }

    /**
     * @param takeImageOnTimestampReset the takeImageOnTimestampReset to set
     */
//    public void setTakeImageOnTimestampReset(boolean takeImageOnTimestampReset) {
//        this.takeImageOnTimestampReset = takeImageOnTimestampReset;
//        putBoolean("takeImageOnTimestampReset", takeImageOnTimestampReset);
//    }

//    public void doDepthViewer() {
//        try {
//            System.load(System.getProperty("user.dir") + "\\jars\\openni2\\OpenNI2.dll");
//
//            // initialize OpenNI
//            OpenNI.initialize();
//
//            List<DeviceInfo> devicesInfo = OpenNI.enumerateDevices();
//            if (devicesInfo.isEmpty()) {
//                JOptionPane.showMessageDialog(null, "No Kinect device is connected via NI2 interface", "Error", JOptionPane.ERROR_MESSAGE);
//                return;
//            }
//
//            Device device = Device.open(devicesInfo.get(0).getUri());
//
//            depthViewerThread = new SimpleDepthCameraViewerApplication(device);
//            depthViewerThread.start();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

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
//    public Mat getCameraMatrix() {
//        return cameraMatrix;
//    }

    /**
     * http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html
     *
     * @return the distortionCoefs
     */
//    public Mat getDistortionCoefs() {
//        return distortionCoefs;
//    }

    /**
     * Human friendly summary of calibration
     *
     * @return the calibrationString
     */
//    public String getCalibrationString() {
//        return calibrationString;
//    }

    /**
     *
     * @return true if calibration was completed successfully
     */
//    public boolean isCalibrated() {
//        return calibrated;
//    }

    /**
     * @return the look-up table of undistorted pixel addresses.
     */
//    public short[] getUndistortedAddressLUT() {
//        return undistortedAddressLUT;
//    }

    /**
     * @return the undistorted pixel address. The input index i is obtained by
     * iterating column-wise over the pixel array (y-loop is inner loop) until
     * getting to (x,y). Have to multiply by two because both x and y addresses
     * are stored consecutively. Thus, i = 2 * (y + sizeY * x)
     */
//    public short getUndistortedAddressFromLUT(int i) {
//        return undistortedAddressLUT[i];
//    }
}
