package ch.unizh.ini.jaer.projects.multitracking;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.CV_32FC3;
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
import java.awt.Cursor;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.DMatchVector;
import org.bytedeco.javacpp.opencv_core.FileStorage;
import org.bytedeco.javacpp.opencv_core.KeyPointVector;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.RectVector;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_core.TermCriteria;
//import org.bytedeco.javacpp.presets.opencv_features2d;
import org.bytedeco.javacpp.opencv_features2d;
import org.bytedeco.javacpp.opencv_features2d.DescriptorMatcher;
//import org.bytedeco.javacpp.opencv_features2d.BFMatcher;
import org.bytedeco.javacpp.opencv_features2d.ORB;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacpp.opencv_objdetect.HOGDescriptor;
import org.bytedeco.javacpp.indexer.DoubleBufferIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
//import org.opencv.imgproc.Imgproc;
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
import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.TextRendererScale;

/**
 * Calibrates a single camera using DAVIS frames and OpenCV calibration methods.
 *
 *
 */
@Description("Calibrates a single camera using DAVIS frames and OpenCV calibration methods")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SingleOrStereoCameraCalibration extends EventFilter2D implements FrameAnnotater, Observer /* observes this to get informed about our size */ {

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
	private Mat corners;  // TODO change to OpenCV java, not bytedeco http://docs.opencv.org/2.4/doc/tutorials/introduction/desktop_java/java_dev_intro.html
	private MatVector allImagePoints;
	private MatVector allObjectPoints;

	//private MatVector matvect;
	private MatVector allImagePoints2;
	private MatVector allObjectPoints2;

	private Mat EssentialMat;
	private Mat FundamentalMat;

	private Mat cameraMatrix;
	private Mat distortionCoefs;
	private MatVector rotationVectors;
	private MatVector translationVectors;

	private Mat outputMat;
	private MatVector rotationVectors2;
	private MatVector translationVectors2;

	private Mat rotationVectorsfinal;
	private Mat translationVectorsfinal;

	private Mat imgIn, imgOut, mato;

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
	private Mat cameraMatrix2;
	private Mat distortionCoefs2;
	private Integer prevImageCounter;
	private boolean CalibrationDemanded;


	Vector<AEChip> chips = new Vector<>();
	Vector<ApsFrameExtractor> fraext = new Vector<>();
	ApsFrameExtractor frameExtractortemp = new ApsFrameExtractor(chip);
	private AEViewer aevi;
	private JAERViewer jaevi;
	ArrayList<AEViewer> arrayOfAEvi;
	private ArrayList<MatVector> images = new ArrayList<MatVector>(2);
	private ArrayList<MatVector> frames = new ArrayList<MatVector>(2);
	private ArrayList<MatVector> objects;
	private boolean takeMultiFrame=false;
	private boolean takeMultiFrame0=false;
	private boolean takeMultiFrame1=false;
	private FilterChain filterchainbis;
	private float[] lastFrame1 = null;
	private FilterChain filterchaintiers;
	private boolean bool;
	private boolean bool1;
	private Mat disparity;
	private boolean takeFramesForCalib=false;
	private float[] Frame1 = null;
	private float[] Frame0 = null;
	private Mat R1;
	private Mat R2;
	private Mat P1;
	private Mat P2;
	private Mat Q;
	private boolean takeAPic=false;
	private Mat points4D;

	public SingleOrStereoCameraCalibration(AEChip chip) {
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
		setPropertyTooltip("cornerSubPixRefinement", "refine corner locations to subpixel resolution");
		setPropertyTooltip("calibrate", "run the camera calibration on collected frame data and print results to console");
		setPropertyTooltip("depthViewer", "shows the depth or color image viewer if a Kinect device is connected via NI2 interface");
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
		//	        loadCalibration(); // moved from here to update method so that Chip is fully constructed with correct size, etc.
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
		//getEnclosedFilterChain().filterPacket(in);

		// for each event only keep it if it is within dt of the last time
		// an event happened in the direct neighborhood
		Iterator itr = ((ApsDvsEventPacket) in).fullIterator();
		while (itr.hasNext()) {
			Object o = itr.next();
			if (o == null) {
				break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
			}
			BasicEvent e = (BasicEvent) o;
			//	            if (e.isSpecial()) {
			//	                continue;
			//	            }

/*			if (takeMultiFrame0==true){
				//			System.out.println("takeMultiFrame0");
				if(fraext.get(0).hasNewFrame()){
					System.out.println("take frame from viewer0");
					takeMultiFrame0=false;
					multiFramefunc(fraext.get(0));
				}
			}

			if (takeMultiFrame1==true){
				//			System.out.println("takeMultiFrame0");
				if(fraext.get(1).hasNewFrame()){
					System.out.println("take frame from viewer1");
					takeMultiFrame1=false;
					multiFramefunc(fraext.get(1));
				}
			}	*/
			bool=false;
			bool1=false;
			//(takeMultiFrame1==true)&&(takeMultiFrame0==true)&&

			if ((takeMultiFrame1==true)&&(takeMultiFrame0==true)&&(takeFramesForCalib==true)){


              if (imageCounter<15){

				if(fraext.get(0).hasNewFrame()){
					 bool=false;
					 //Frame0 = fraext.get(0).getNewFrame();
					//System.out.println("take frame from viewer0");
				    bool = multiFramefunc(fraext.get(0), false);
				}
					//		System.out.println("takeMultiFrame1");
					if(fraext.get(1).hasNewFrame()){
						 bool1=false;
						//Frame1 = fraext.get(1).getNewFrame();
						//System.out.println("take frame from viewer1");
						bool1 = multiFramefunc(fraext.get(1), false);

					}
					if ((bool==true)&&(bool1==true)){
						bool = multiFramefunc(fraext.get(0), true);
						System.out.println("take frame from viewer0");
						bool1 = multiFramefunc(fraext.get(1), true);
						System.out.println("take frame from viewer1");
						if ((bool==true)&&(bool1==true)){
						//takeMultiFrame0=false;
						//takeMultiFrame1=false;
						imageCounter++;
						System.out.println("nb of frames taken:"+ imageCounter);


						/*Mat imgr = new Mat(sy, sx, CV_8U);
						Mat imgl = new Mat(sy, sx, CV_8U);
						opencv_core.flip(images.get(0).get(imageCounter-1), imgr, 0);
						opencv_core.flip(images.get(0).get(imageCounter-1), imgl, 0);
						System.out.println(imgr.arrayWidth());
						System.out.println(imgr.arrayHeight());*/
						disparity=new Mat();
						try{
						   opencv_calib3d.StereoSGBM.create(0,16,3).compute(frames.get(0).get(imageCounter-1), frames.get(1).get(imageCounter-1), disparity);
						   //System.out.println(opencv_calib3d.StereoBM.create().getBlockSize());
						   }catch (RuntimeException p) {
								log.warning(p.toString());
							}
						String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + "disparityMap"+ ".jpg";
						String fullFilePath = dirPath + "\\" + filename;
						org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, disparity);
						log.info("wrote " + fullFilePath);
						/*try{
							stereoMatchingPoint(frames.get(0).get(imageCounter-1), frames.get(1).get(imageCounter-1));
						}catch (RuntimeException p) {
							log.warning(p.toString());
						}*/
						}
					}
				}

			}

            if (takeAPic==true){
            	boolean isimg1=false;
            	boolean isimg2=false;

            	if(fraext.get(0).hasNewFrame()){
            		lastFrame1 = fraext.get(0).getNewFrame();
            		//lastFrame1 = frame;
            		FloatPointer p1 = new FloatPointer(lastFrame1);
            		Mat input1 = new Mat(p1);
            		input1.convertTo(input1, CV_8U, 255, 0);
            		Mat img1 = input1.reshape(0, sy);
            		isimg1=true;

					//		System.out.println("takeMultiFrame1");
					if(fraext.get(1).hasNewFrame()){

						lastFrame1 = fraext.get(1).getNewFrame();
						//lastFrame1 = frame;
						FloatPointer p2 = new FloatPointer(lastFrame1);
						Mat input2 = new Mat(p2);
						input2.convertTo(input2, CV_8U, 255, 0);
						Mat img2 = input2.reshape(0, sy);
						isimg2=true;

					if ((isimg1==true)&&(isimg2==true)){
						disparity=new Mat();
						try{
						   opencv_calib3d.StereoSGBM.create(0,16,3).compute(img1, img2, disparity);
						   //System.out.println(opencv_calib3d.StereoBM.create().getBlockSize());
						   }catch (RuntimeException p) {
								log.warning(p.toString());
							}
						String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + "disparityMap"+ ".jpg";
						String fullFilePath = dirPath + "\\" + filename;
						org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, disparity);
						log.info("wrote " + fullFilePath);
						try{
							//poepleDetection(img1, img2);
							stereoMatchingPoint(img1, img2);
						}catch (RuntimeException p) {
							log.warning(p.toString());
						}
						takeAPic=false;
					}
					}
            	}
            }

			//acquire new frame
			if (frameExtractor.hasNewFrame()) {
				lastFrame = frameExtractor.getNewFrame();

				//process frame
				if (realtimePatternDetectionEnabled || autocaptureCalibrationFramesEnabled) {
					patternFound = findCurrentCorners(false); // false just checks if there are corners detected
				}

				/*if (CalibrationDemanded
					&& ((System.currentTimeMillis() - lastAutocaptureTimeMs) > autocaptureCalibrationFrameDelayMs)
					&& (nAcqFrames < numAutoCaptureFrames)){
					nAcqFrames++;
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
					boolean locPatternFound = false;
					try {
						locPatternFound = opencv_calib3d.findChessboardCorners(imgIn, patternSize, corners);
					} catch (RuntimeException re) {
						log.warning(re.toString());
					}
					if (locPatternFound) {
						Mat imgSave = new Mat(sy, sx, CV_8U);
						opencv_core.flip(imgIn, imgSave, 0);
						String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + ".jpg";
						String fullFilePath = dirPath + "\\" + filename;
						org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, imgSave);
						log.info("wrote " + fullFilePath);


						//store image points
						if ((imageCounter == 0) || (allObjectPoints == null) || (allImagePoints == null)) {
							allImagePoints = new MatVector(100);
							allObjectPoints = new MatVector(100);
						}
						allImagePoints.put(imageCounter, corners);
						System.out.println("corners type is"+corners.type()+"\ncorner size is :"+corners.size());
						log.info( "allimagepoints : "+printMatD(allImagePoints));
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
						System.out.println("objectPoints type is"+objectPoints.type()+"\ncorner size is :"+objectPoints.size());
						log.info("allobejctpoints : "+printMatD(allObjectPoints));
						log.info(String.format("added corner points from image %d", imageCounter));
						imageCounter++;

					}

					if (nAcqFrames >= numAutoCaptureFrames) {
						CalibrationDemanded = false;
						log.info("finished autocapturing " + nAcqFrames + " acquired. Starting calibration in background....");
						(new CalibrationWorker()).execute();
					} else {
						log.info("captured frame " + nAcqFrames);
					}
				}*/

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
				}

				// undistort and show results
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
				if ((imageCounter == 0) || (allObjectPoints == null) || (allImagePoints == null)) {
					allImagePoints = new MatVector(100);
					allObjectPoints = new MatVector(100);
				}
				allImagePoints.put(imageCounter, corners);
				System.out.println("corners type is"+corners.type()+"\ncorner size is :"+corners.size());
				log.info( "allimagepoints : "+printMatD(allImagePoints));
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
				System.out.println("objectPoints type is"+objectPoints.type()+"\ncorner size is :"+objectPoints.size());
				log.info("allobejctpoints : "+printMatD(allObjectPoints));
				log.info(String.format("added corner points from image %d", imageCounter));
				imageCounter++;
				frameExtractor.apsDisplay.setxLabel(filename);

				//	                //debug
				//	                System.out.println(allImagePoints.toString());
				//	                for (int n = 0; n < imageCounter; n++) {
				//	                    System.out.println("n=" + n + " " + allImagePoints.get(n).toString());
				//	                    for (int i = 0; i < corners.rows(); i++) {
				//	                        System.out.println(allImagePoints.get(n).getFloatBuffer().get(2 * i) + " " + allImagePoints.get(n).getFloatBuffer().get(2 * i + 1)+" | "+allObjectPoints.get(n).getFloatBuffer().get(3 * i) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 1) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 2));
				//	                    }
				//	                }
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
		initFilter();
		filterChain.reset();
		patternFound = false;
		imageCounter = 0;
		principlePoint = null;
		autocaptureCalibrationFramesEnabled = false;
		nAcqFrames = 0;
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

	public void doTryToFind3DPosition(){
		takeAPic=true;
	}

	public void poepleDetection(Mat img1, Mat img2){
	//Mat img1=opencv_imgcodecs.imread("C:/Users/iniLabs/Desktop/testSophie/RealTest/imagesTest.jpg");
	//Mat img2=opencv_imgcodecs.imread("C:/Users/iniLabs/Desktop/testSophie/RealTest/test2.jpg");

	HOGDescriptor hog = new HOGDescriptor();
    Mat svmdetector;
    FloatPointer ip=opencv_objdetect.HOGDescriptor.getDefaultPeopleDetector();
    svmdetector=new Mat(ip);
    hog.setSVMDetector(svmdetector);
    RectVector foundLocations1 = new RectVector() ;
    RectVector foundLocations2 = new RectVector() ;
    hog.detectMultiScale(img1, foundLocations1);
	 System.out.println(foundLocations1.size());
	org.bytedeco.javacpp.opencv_core.Scalar rectColor = new org.bytedeco.javacpp.opencv_core.Scalar(0, 255);
	for(long i=0;i<foundLocations1.size();i++){
		Rect r = new Rect(foundLocations1.get(i));
		//Point pt1=r.br();
		//Point pt2=r.tl();
		//Mat img=img1;
		opencv_imgproc.rectangle(img1, r,rectColor);
		//img1=img1.adjustROI(r.tl().y(), r.br().y(), r.tl().x(),  r.br().x());
	}
	String filename = chip.getName() + "-" + "HOG" + "-" + String.format("%03d", imageCounter) + "img1rect"+ ".jpg";
	String fullFilePath = dirPath + "\\" + filename;
	org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, img1);
	log.info("wrote " + fullFilePath);
	//
	 hog.detectMultiScale(img2, foundLocations2);
	 System.out.println(foundLocations2.size());
		for(long i=0;i<foundLocations2.size();i++){
			Rect r = new Rect(foundLocations2.get(i));
			//Point pt1=r.br();
			//Point pt2=r.tl();
			//Mat img=img1;
			opencv_imgproc.rectangle(img2, r,rectColor);

			//img1=img1.adjustROI(r.tl().y(), r.br().y(), r.tl().x(),  r.br().x());
		}
		String filename2 = chip.getName() + "-" + "HOG" + "-" + String.format("%03d", imageCounter) + "img2rect"+ ".jpg";
		String fullFilePath2 = dirPath + "\\" + filename2;
		org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath2, img2);
		log.info("wrote " + fullFilePath2);


	}
//
//	public static void showResult(Mat img) {
//		opencv_imgproc.resize(img, img, new Size(640, 480));
//	    MatOfByte matOfByte = new MatOfByte();
//	    org.bytedeco.javacpp.opencv_imgcodecs.imencodeimencode(".jpg", img, matOfByte);
//	    byte[] byteArray = matOfByte.toArray();
//	    BufferedImage bufImage = null;
//	    try {
//	        InputStream in = new ByteArrayInputStream(byteArray);
//	        bufImage = ImageIO.read(in);
//	        JFrame frame = new JFrame();
//	        frame.getContentPane().add(new JLabel(new ImageIcon(bufImage)));
//	        frame.pack();
//	        frame.setVisible(true);
//	    } catch (Exception e) {
//	        e.printStackTrace();
//	    }
//	}
	public void stereoMatchingPoint(Mat img1, Mat img2){
		   //Mat img1 = frames.get(0).get(imageCounter-1);         //queryImage
		   //Mat img2 = frames.get(1).get(imageCounter-1);        // trainImage

			// Initiate SIFT detector
			ORB orb = opencv_features2d.ORB.create();

			// find the keypoints and descriptors with SIFT
			KeyPointVector keypoints1=new KeyPointVector();
			KeyPointVector keypoints2=new KeyPointVector();
			Mat mask = new Mat();

			Mat descriptor1= new Mat();
			Mat descriptor2= new Mat();

			orb.detect(img1, keypoints1);
			orb.compute(img1, keypoints1, descriptor1);
			orb.detect(img2, keypoints2);
			orb.compute(img2, keypoints2, descriptor2);

			//create BFMatcher object
			boolean crossCheck;
			DescriptorMatcher bf = DescriptorMatcher.create("BruteForce-Hamming");
			//bf.(opencv_core.NORM_HAMMING,true);

			//Match descriptors.
			DMatchVector matches= new DMatchVector();
			bf.match(descriptor1,descriptor2, matches);

			//Sort them in the order of their distance.
			/*opencv_features2d.sorted(matches, matches,opencv_core.SORT_DESCENDING);
			matches.get(0).distance(distance)*/
			//Draw first 10 matches.
			Mat img3=new Mat();

			opencv_features2d.drawMatches(img1,keypoints1,img2,keypoints2,matches, img3);

			String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + "MatchingMap"+ ".jpg";
			String fullFilePath = dirPath + "\\" + filename;
			org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, img3);
			log.info("wrote " + fullFilePath);
			Mat points4D;
			//Vector<Point2f> points1 = new Vector<Point2f>();

//			Mat matpoints1 = new Mat();
//			Mat matpoints2 = new Mat();
			Mat matpoints1 = new Mat();
			Mat matpoints2 = new Mat();
			//Point2fVector matpoints1 = new Point2fVector(matches.size());
			//Point2fVector matpoints2 = new Point2fVector(matches.size());
			//float[] matpoints1 =  new float[(int)matches.size()*2];
			//float[] matpoints2 = new float[(int)matches.size()*2];

			for (int i=0; i<((int)matches.size());i++){
				//for (int j=0; j<matpoints1.rows();i++){
//				matpoints1[i]=keypoints1.get(matches.get(i).queryIdx()).pt().x();
//				matpoints1[i*2]=keypoints1.get(matches.get(i).queryIdx()).pt().y();
//				matpoints1[i]=keypoints2.get(matches.get(i).trainIdx()).pt().x();
//				matpoints1[i*2]=keypoints2.get(matches.get(i).trainIdx()).pt().y();
//				matpoints1.put(i,keypoints1.get(matches.get(i).queryIdx()).pt());
//				matpoints2.put(i,keypoints2.get(matches.get(i).trainIdx()).pt());
				Mat mat1=new Mat();
				Mat mat2=new Mat();
				mat1.put(keypoints1.get(matches.get(i).queryIdx()).pt());
				mat2.put(keypoints2.get(matches.get(i).queryIdx()).pt());
				matpoints1.push_back(mat1);
				matpoints2.push_back(mat2);
//				matpoints1.put(i,j,keypoints1.get(matches.get(i).queryIdx()).pt().x());
//				matpoints2.push_back_(keypoints2.get(matches.get(i).trainIdx()).pt());
//              System.out.println(keypoints1.get(matches.get(i).queryIdx()).pt().x()+"+"+keypoints1.get(matches.get(i).queryIdx()).pt().y());
//				System.out.println(keypoints2.get(matches.get(i).trainIdx()).pt().x()+"+"+keypoints2.get(matches.get(i).trainIdx()).pt().y());
			}
//			}
			System.out.println("stop start printMatD");
			System.out.println(printMatD(matpoints1));
			System.out.println(printMatD(matpoints2));
			System.out.println(matpoints1);
			System.out.println(matpoints2);
			//Pointer p1=new Pointer(matpoints1);
			//Pointer.memcpy(p1, matpoints1, matches.size());
			//Mat points1 = new Mat(p1);
			//Mat points1 = new Mat((int) matches.size(), 2,CV_8U);

			//points1.convertTo(points1, CV_8U, 255, 0);
			//System.out.println(printMatD(points1));
			//System.out.println(points1.channels());
			//System.out.println(points1.rows());
			//points1.reshape(2,0);
			//System.out.println(points1.channels());
			//System.out.println(points1.rows());
			//FloatPointer p2=new FloatPointer(matpoints2);
//			Mat points2 = new Mat();
//			Pointer.memmove(points2, matpoints2, matches.size());
//			points2.convertTo(points2, CV_8U, 255, 0);
//			points2.reshape(1,2);
//			System.out.println(points2.channels());
//			System.out.println(points2.rows());

			//points2.resize(100);
			points4D=new Mat();

//			Mat dispar=new Mat();
//			Mat output=new Mat();
//			Mat output3d=new Mat();
//			try{
//			   opencv_calib3d.StereoSGBM.create(0,16,3).compute(img1,img2, dispar);
//			   //System.out.println(opencv_calib3d.StereoBM.create().getBlockSize());
//			   }catch (RuntimeException p) {
//					log.warning(p.toString());
//				}
//			opencv_calib3d.reprojectImageTo3D(dispar, output, Q);
//			opencv_core.perspectiveTransform(output,output3d,Q) ;

			opencv_calib3d.triangulatePoints(P1, P2, matpoints1, matpoints2, points4D);
			System.out.println(printMatD(points4D));
	}





	public void doSwitchGlobalView(){

		aevi = this.chip.getAeViewer();

		//switch to globalViewMode
		jaevi = aevi.getJaerViewer();
		//		jaevi.setViewMode(true);

		//get the list of active viewers
		//		GlobalViewer gb = jaevi.globalViewer;
		//		System.out.println(gb);
		arrayOfAEvi = jaevi.getViewers();
		//Vector<AEChip> chips = new Vector<>();
		//Vector<ApsFrameExtractor> fraext = new Vector<>();
		filterchainbis = new FilterChain(chip);
		//filterchaintiers = new FilterChain(arrayOfAEvi.get(1).getChip());

		for (AEViewer aev : arrayOfAEvi){
			aev.setVisible(true);
			AEChip chi = aev.getChip();
			chi.addObserver(this);
			chips.add(chi);
			System.out.println(chi.getAeViewer());
			System.out.println(aev);
		}

		for (int i=0;i<2;i++){
			ApsFrameExtractor frameExtractortemp = new ApsFrameExtractor(arrayOfAEvi.get(i).getChip());
			fraext.add(frameExtractortemp);
			filterchainbis.add(frameExtractortemp);
			//filterchaintiers.add(frameExtractortemp);

			arrayOfAEvi.get(i).getChip().getFilterChain().add(frameExtractortemp);
			//arrayOfAEvi.get(1).getChip().getFilterChain().add(frameExtractortemp);
			frameExtractortemp.setExtRender(false);

			MatVector mv = new MatVector(100);
			MatVector mw = new MatVector(100);
			images.add(mv);
			frames.add(mw);
		}
		setEnclosedFilterChain(filterchainbis);
		//setEnclosedFilterChain(filterchaintiers);

		//setEnclosedFilterChain();
		//setEnclosedFilterChain(arrayOfAEvi.get(1).getChip().getFilterChain());

		chip.getFilterFrame().rebuildContents();
	}

	synchronized public void doTakeMultiFrame(){
		System.out.println("take multi frame");
//		System.out.println(fraext.elementAt(0));
//		System.out.println(fraext.elementAt(1));
//		System.out.println(fraext.elementAt(0).hasNewFrame());
//		System.out.println(fraext.elementAt(1).hasNewFrame());

		    objects = new ArrayList<>(2);
		    MatVector v = new MatVector(100);
			MatVector w = new MatVector(100);
			objects.add(v);
			objects.add(w);

		takeFramesForCalib=true;
		takeMultiFrame0=true;
		takeMultiFrame1=true;

	}
	public boolean multiFramefunc(ApsFrameExtractor fra, boolean thereIsAPattern){
		FloatPointer ip;
		//log.info("nb of frames taken:"+Integer.toString(imageCounter));
		//	int l = fraext.size();
		//	images =new ArrayList<MatVector>(10);
		//imageCounter=0;
		//	for(int fra =0; fra<l; fra++){

		//		lastFrame = fraext.elementAt(fra).getNewFrame();
		lastFrame1 = fra.getNewFrame();
		//lastFrame1 = frame;
		ip = new FloatPointer(lastFrame1);
		Mat input = new Mat(ip);
		input.convertTo(input, CV_8U, 255, 0);
		imgIn = input.reshape(0, sy);
		imgOut = new Mat(sy, sx, CV_8UC3);
		cvtColor(imgIn, imgOut, CV_GRAY2RGB);
		boolean locPatternFound=false;
		corners = new Mat();
		Size patternSize = new Size(patternWidth, patternHeight);
		try {
			 locPatternFound = opencv_calib3d.findChessboardCorners(imgIn, patternSize, corners);
		} catch (RuntimeException re) {
			log.warning(re.toString());
			return false;
		}
		if (locPatternFound) {
			System.out.println(locPatternFound);
			System.out.println(Integer.toString(fraext.indexOf(fra)));
			opencv_calib3d.drawChessboardCorners(imgOut, patternSize, corners, locPatternFound);

		}
		//System.out.println(locPatternFound);


		if(thereIsAPattern==true){
			if (locPatternFound) {
				opencv_core.TermCriteria tc = new opencv_core.TermCriteria(CV_TERMCRIT_EPS + CV_TERMCRIT_ITER, 30, 0.1);
				opencv_imgproc.cornerSubPix(imgIn, corners, new Size(3, 3), new Size(-1, -1), tc);
			}
			opencv_calib3d.drawChessboardCorners(imgOut, patternSize, corners, locPatternFound);
	//	log.info("mew image:"+ printMatD(input));
		//opencv_core.TermCriteria tc = new opencv_core.TermCriteria(CV_TERMCRIT_EPS + CV_TERMCRIT_ITER, 30, 0.1);
		//opencv_imgproc.cornerSubPix(imgIn, corners, new Size(3, 3), new Size(-1, -1), tc);
		//MatVector mv = new MatVector(100);
		//images.add(mv);
		//		images.get(fra).put(imageCounter, imgIn);
			if (locPatternFound) {
		//images.get(fraext.indexOf(fra)).put(imageCounter, imgIn);

				try{
					images.get(fraext.indexOf(fra)).put(imageCounter,corners);


				/*if(u==0){
					allImagePoints.put(imageCount, corners);
				}
				else{
					allImagePoints2.put(imageCount, corners);
				}*/
				}
				catch (RuntimeException e) {
					log.info("no pattern found");

				}
				System.out.println("corners type is"+corners.type()+"\ncorner size is :"+corners.size());
				//	log.info( "allimagepoints : "+printMatD(mv));
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
				// here was the previous matVector in object definition
				objects.get(fraext.indexOf(fra)).put(imageCounter, objectPoints);
				//iterate image counter
				System.out.println("objectPoints type is"+objectPoints.type()+"\ncorner size is :"+objectPoints.size());
				//log.info("allobejctpoints : "+printMatD(allObjectPoints));
				log.info(String.format("added corner points from image %d", imageCounter));

		//
		log.info(Integer.toString(fraext.indexOf(fra)));
		log.info(Integer.toString(imageCounter));
       // log.info(printMatD(images.get(fraext.indexOf(fra))));

		Mat imgSave = new Mat(sy, sx, CV_8U);

		opencv_core.flip(imgIn, imgSave, 0);
		//opencv_core.flip(images.get(fraext.indexOf(fra)).get(imageCounter), imgSave, 0);
		frames.get(fraext.indexOf(fra)).put(imageCounter, imgSave);
		String filename = chip.getName() + "-" + fileBaseName + "-" + String.format("%03d", imageCounter) + String.format("%03d", fraext.indexOf(fra))+ ".jpg";
		String fullFilePath = dirPath + "\\" + filename;
		org.bytedeco.javacpp.opencv_imgcodecs.imwrite(fullFilePath, imgSave);
		log.info("wrote " + fullFilePath);
	}
			else {
				log.warning("corners not found for this image");
			}
		}
		return(locPatternFound);
	}


	public void doStereoCalibrationV4(){
		//fraext.elementAt(1).removeDisplays();
		Size patternSize = new Size(patternWidth, patternHeight);
		Size imgSize = new Size(sx, sy);
		corners = new Mat();

		Mat cameraMatrix;
		Mat distortionCoefs;

		Mat cameraMatrix2;
		Mat distortionCoefs2;

		MatVector rotationVectors;
		MatVector translationVectors;


		MatVector rotationVectors2;
		MatVector translationVectors2;

		Mat rotationVectorsfinal;
		Mat translationVectorsfinal;

		Mat EssentialMat;
		Mat FundamentalMat;

		Mat R1= new Mat();
		Mat R2= new Mat();
		Mat P1= new Mat();
		Mat P2= new Mat();
		Mat Q = new Mat();

		EssentialMat = new Mat();
		FundamentalMat = new Mat();

		cameraMatrix = new Mat();
		distortionCoefs = new Mat();


		cameraMatrix2 = new Mat();
		distortionCoefs2 = new Mat();

		translationVectors = new MatVector();
		rotationVectors = new MatVector();

		translationVectors2 = new MatVector();
		rotationVectors2 = new MatVector();

		rotationVectorsfinal = new Mat();
		translationVectorsfinal = new Mat();
//////////////////////////////////////////////////////////////////////////////////

/*		objects = new ArrayList<>(2);

		for(int u=0;u<2;u++){

			MatVector v = new MatVector(100);
			objects.add(v);

			log.info( "size : "+Long.toString(images.get(u).size()));
	//		log.info( "mv : "+printMatD(mv));
			int imageCount=0;

			for(int i=0; i<imageCounter; i++){
				System.out.println(u);
				System.out.println(i);
				Mat m= new Mat();
				m= images.get(u).get(i);
				System.out.println(m);
				boolean locPatternFound=false;
				try {
					locPatternFound = opencv_calib3d.findChessboardCorners(m, patternSize, corners);
					System.out.println(locPatternFound);
					System.out.println(corners);

					if(locPatternFound==true){
						//store image points
						if ((imageCount == 0) || (allObjectPoints == null) || (allImagePoints == null)) {
							allImagePoints = new MatVector(100);
							allObjectPoints = new MatVector(100);
							allImagePoints2 = new MatVector(100);
							allObjectPoints2 = new MatVector(100);
						}
						try{
							images.get(u).put(i,corners);


						if(u==0){
							allImagePoints.put(imageCount, corners);
						}
						else{
							allImagePoints2.put(imageCount, corners);
						}
						}
						catch (RuntimeException e) {
							log.info("no pattern found");

						}
						System.out.println("corners type is"+corners.type()+"\ncorner size is :"+corners.size());
						//	log.info( "allimagepoints : "+printMatD(mv));
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
						// here was the previous matVector in object definition
						objects.get(u).put(i, objectPoints);
						//iterate image counter
						System.out.println("objectPoints type is"+objectPoints.type()+"\ncorner size is :"+objectPoints.size());
						//log.info("allobejctpoints : "+printMatD(allObjectPoints));
						log.info(String.format("added corner points from image %d", imageCount));
						imageCount++;
						//frameExtractor.apsDisplay.setxLabel(filename);

						//	                //debug
						//	                System.out.println(allImagePoints.toString());
						//	                for (int n = 0; n < imageCounter; n++) {
						//	                    System.out.println("n=" + n + " " + allImagePoints.get(n).toString());
						//	                    for (int i = 0; i < corners.rows(); i++) {
						//	                        System.out.println(allImagePoints.get(n).getFloatBuffer().get(2 * i) + " " + allImagePoints.get(n).getFloatBuffer().get(2 * i + 1)+" | "+allObjectPoints.get(n).getFloatBuffer().get(3 * i) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 1) + " " + allObjectPoints.get(n).getFloatBuffer().get(3 * i + 2));
						//	                    }
						//	                }
					}else{
						log.warning("corners not found for this image");
					}
				}
				catch (RuntimeException e) {
					log.warning(e.toString());

				}
			}
		}*/
////////////////////////////////////////////////////////////////////////////////////////////////////
		   // allImagePoints.resize(imageCounter);
		   // allImagePoints2.resize(imageCounter);
			images.get(0).resize(imageCounter);
			images.get(1).resize(imageCounter);
			objects.get(0).resize(imageCounter);
			objects.get(1).resize(imageCounter);
			log.info(String.format("calibrating based on %d images sized %d x %d", images.get(1).size(), imgSize.width(), imgSize.height()));
			//calibrate0

			try {
				setCursor(new Cursor(Cursor.WAIT_CURSOR));
				opencv_calib3d.calibrateCamera(objects.get(0), images.get(0), imgSize, cameraMatrix, distortionCoefs, rotationVectors, translationVectors);
				generateCalibrationString();
				log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
					+ "\nCamera matrix: " + cameraMatrix.toString() + "\n" + printMatD(cameraMatrix)
					+ "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs.toString() + "\n" + printMatD(distortionCoefs)
					+ calibrationString);
			} catch (RuntimeException e) {
				log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
			}finally{
				setCursor(Cursor.getDefaultCursor());
			}
			//calibrated = true;
			synchronized (this) {
				this.cameraMatrix = cameraMatrix;
				this.distortionCoefs = distortionCoefs;
				//calibrate1
			}
				try {
					setCursor(new Cursor(Cursor.WAIT_CURSOR));
					opencv_calib3d.calibrateCamera(objects.get(0), images.get(1), imgSize, cameraMatrix2, distortionCoefs2, rotationVectors2, translationVectors2);
					generateCalibrationString();
					log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
						+ "\nCamera matrix2: " + cameraMatrix2.toString() + "\n" + printMatD(cameraMatrix2)
						+ "\nDistortion coefficients2 k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs2.toString() + "\n" + printMatD(distortionCoefs2)
						+ calibrationString);
				} catch (RuntimeException e) {
					log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
				} finally {

					setCursor(Cursor.getDefaultCursor());
				}
				//calibrated = true;
				synchronized (this) {
					this.cameraMatrix2 = cameraMatrix2;
					this.distortionCoefs2 = distortionCoefs2;
					//stereocalibrate

				}


			try {
				setCursor(new Cursor(Cursor.WAIT_CURSOR));
				TermCriteria termCrit = new TermCriteria(opencv_core.TermCriteria.COUNT+opencv_core.TermCriteria.EPS, 30, 1e-6);
				opencv_calib3d.stereoCalibrate(objects.get(0),  images.get(0), images.get(1), cameraMatrix, distortionCoefs,cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat, opencv_calib3d.CV_CALIB_FIX_INTRINSIC/*+opencv_calib3d.CV_CALIB_SAME_FOCAL_LENGTH+opencv_calib3d.CV_CALIB_ZERO_TANGENT_DIST*/, termCrit);
				generateCalibrationString();
				log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
					+ "\nCamera rotationVectorsfinal: " + rotationVectorsfinal.toString() + "\n" + printMatD(rotationVectorsfinal)
					+ "\nCamera translationVectorsfinal: " + translationVectorsfinal.toString() + "\n" + printMatD(translationVectorsfinal)
					+ "\nCamera cameraMatrix2: " + cameraMatrix2.toString() + "\n" + printMatD(cameraMatrix2)
					+ "\nFundamentalMat: " + FundamentalMat.toString() + "\n" + printMatD(FundamentalMat)
					+ calibrationString);
			} catch (RuntimeException e) {
				log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
			} finally {
				images.get(0).resize(100);
				images.get(1).resize(100);
				objects.get(0).resize(100);
				objects.get(1).resize(100);
				setCursor(Cursor.getDefaultCursor());
			}
			//calibrated = true;
			synchronized (this) {
/*				this.cameraMatrix = cameraMatrix;
				this.distortionCoefs = distortionCoefs;
			    this.cameraMatrix2 = cameraMatrix2;
				this.distortionCoefs2 = distortionCoefs2;

*/				this.translationVectorsfinal = translationVectorsfinal;
				this.rotationVectorsfinal = rotationVectorsfinal;
				this.EssentialMat = EssentialMat;
				this.FundamentalMat = FundamentalMat;

			}
			try{
				opencv_calib3d.stereoRectify(cameraMatrix, distortionCoefs, cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal, R1, R2, P1, P2, Q);
				log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
					+ "\nCamera Q: " + Q.toString() + "\n" + printMatD(Q)
					+ "\nCamera P1: " + P1.toString() + "\n" + printMatD(P1)
					+ "\nCamera P2: " + P2.toString() + "\n" + printMatD(P2)
					);
			}catch (RuntimeException e) {
				log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
			}
			//R1, R2, P1, P2, Q
			synchronized (this) {
				this.R1 = R1;
				this.R2 = R2;
				this.P1 = P1;
				this.P2 = P2;
				this.Q = Q;
			}
			getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
			calibrated = true;
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
	public void doStereoCalibrateV1() {
		if ((allImagePoints == null) || (allObjectPoints == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		//init
		Size imgSize = new Size(sx, sy);
		// make local references to hold results while thread is processing
		Mat cameraMatrix;
		Mat distortionCoefs;
		Mat cameraMatrix2;
		Mat distortionCoefs2;
		Mat rotationVectors;
		Mat translationVectors;

		Mat EssentialMat;
		Mat FundamentalMat;

		EssentialMat = new Mat();
		FundamentalMat = new Mat();

		cameraMatrix = new Mat();
		distortionCoefs = new Mat();
		cameraMatrix2 = new Mat();
		distortionCoefs2 = new Mat();
		rotationVectors = new Mat();
		translationVectors = new Mat();

		loadimagePointForStereoCalibration();
		if ((allImagePoints2 == null) || (allObjectPoints2 == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		log.info(String.format("**********allImagePoints2 is %d , allObjectPoints2 is %d,  images sized %d x %d", allImagePoints2.size(), allObjectPoints2.size(), imgSize.width(), imgSize.height()));

		/*	long nbeffimage= Long.min(allImagePoints2.size(),allObjectPoints2.size());
		long nbeffimage2= Long.min(allImagePoints.size(),allObjectPoints.size());
		long nbeffimage3= Long.min(nbeffimage2,nbeffimage);

		log.info("*********nbeffimage:"+nbeffimage+" nbeffimage2:"+nbeffimage2+" nbeffimage3:"+nbeffimage3);
		 */

		allImagePoints2.resize(imageCounter);
		allObjectPoints2.resize(imageCounter);
		allImagePoints.resize(imageCounter);
		allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
		log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
		//calibrate
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
			TermCriteria termCrit = new TermCriteria(opencv_core.TermCriteria.COUNT+opencv_core.TermCriteria.EPS, 30, 1e-6);
			opencv_calib3d.stereoCalibrate(allObjectPoints, allImagePoints,allImagePoints2, cameraMatrix, distortionCoefs,cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat, opencv_calib3d.CV_CALIB_FIX_INTRINSIC, termCrit);
			generateCalibrationString();
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nCamera EssentialMat: " + EssentialMat.toString() + "\n" + printMatD(EssentialMat)
				+ "\nFundamentalMat: " + FundamentalMat.toString() + "\n" + printMatD(FundamentalMat)
				+ calibrationString);
		} catch (RuntimeException e) {
			log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
		} finally {
			allImagePoints.resize(100);
			allObjectPoints.resize(100);
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			this.distortionCoefs = distortionCoefs;
			//this.rotationVectorsfinal = rotationVectorsfinal;
			//this.rotationVectorsfinal = rotationVectorsfinal;
		}
		getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
	}

	public void doTest() {

		Size imgSize = new Size(sx, sy);
		if  ((allObjectPoints == null) || (allImagePoints == null)) {
			allImagePoints = new MatVector(100);
			allObjectPoints = new MatVector(100);
		}
		if  ((allObjectPoints2 == null) || (allImagePoints2 == null)) {
			allImagePoints2 = new MatVector(100);
			allObjectPoints2 = new MatVector(100);
		}



		Mat cameraMatrix;
		Mat distortionCoefs;

		Mat cameraMatrix2;
		Mat distortionCoefs2;

		MatVector rotationVectors;
		MatVector translationVectors = null;


		MatVector rotationVectors2;
		MatVector translationVectors2 = null;

		Mat rotationVectorsfinal;
		Mat translationVectorsfinal;

		Mat EssentialMat;
		Mat FundamentalMat;

		EssentialMat = new Mat();
		FundamentalMat = new Mat();

		cameraMatrix = new Mat();
		distortionCoefs = new Mat();


		cameraMatrix2 = new Mat();
		distortionCoefs2 = new Mat();

		translationVectors = new MatVector();
		rotationVectors = new MatVector();

		translationVectors2 = new MatVector();
		rotationVectors2 = new MatVector();

		rotationVectorsfinal = new Mat();
		translationVectorsfinal = new Mat();

		loadimagePointForStereoCalibration();
		loadimagePointForStereoCalibration2();
		if ((allImagePoints == null) || (allObjectPoints == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		if ((allImagePoints2 == null) || (allObjectPoints2 == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		log.info(String.format("**********allImagePoints2 is %d , allObjectPoints2 is %d,  images sized %d x %d", allImagePoints2.size(), allObjectPoints2.size(), imgSize.width(), imgSize.height()));

		/*	long nbeffimage= Long.min(allImagePoints2.size(),allObjectPoints2.size());
		long nbeffimage2= Long.min(allImagePoints.size(),allObjectPoints.size());
		long nbeffimage3= Long.min(nbeffimage2,nbeffimage);

		log.info("*********nbeffimage:"+nbeffimage+" nbeffimage2:"+nbeffimage2+" nbeffimage3:"+nbeffimage3);
		 */
		log.info(prevImageCounter.toString());
		allImagePoints.resize(prevImageCounter);
		allObjectPoints.resize(prevImageCounter);
		allImagePoints2.resize(prevImageCounter);
		allObjectPoints2.resize(prevImageCounter);

		//		log.info( "allimagepointsforcalibrationV2 : "+printMatD(allImagePoints2));
		//		log.info( "allobjectpointsforcalibrationV2 : "+printMatD(allObjectPoints2));
		//calibrate first camera
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
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
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			System.out.println(cameraMatrix.total());
			log.info("camera matrix : "+printMatD(cameraMatrix));
			this.distortionCoefs = distortionCoefs;
			System.out.println(distortionCoefs.total());
			log.info("camera matrix : "+printMatD(distortionCoefs));
			this.rotationVectors = rotationVectors;
			this.translationVectors = translationVectors;
		}



		//calibrate second camera
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
			opencv_calib3d.calibrateCamera(allObjectPoints2, allImagePoints2, imgSize, cameraMatrix2, distortionCoefs2, rotationVectors2, translationVectors2);
			generateCalibrationString();
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nCamera matrix: " + cameraMatrix2.toString() + "\n" + printMatD(cameraMatrix2)
				+ "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs2.toString() + "\n" + printMatD(distortionCoefs2)
				+ calibrationString);
		} catch (RuntimeException e) {
			log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
		} finally {
			allImagePoints2.resize(100);
			allObjectPoints2.resize(100);
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix2 = cameraMatrix2;
			System.out.println(cameraMatrix2.total());
			log.info("camera matrix2 : "+printMatD(cameraMatrix2));
			this.distortionCoefs2 = distortionCoefs2;
			System.out.println(distortionCoefs2.total());
			log.info("distortionCoefs2 : "+printMatD(distortionCoefs2));
			this.rotationVectors2 = rotationVectors2;
			this.translationVectors2 = translationVectors2;
		}
		getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);



		allImagePoints.resize(prevImageCounter);
		allObjectPoints.resize(prevImageCounter);
		allImagePoints2.resize(prevImageCounter);
		allObjectPoints2.resize(prevImageCounter); // resize has side effect that lists cannot hold any more data
		System.out.println(distortionCoefs.total());
		log.info("distortionCoefs2 : "+printMatD(distortionCoefs));
		System.out.println(distortionCoefs2.total());
		log.info("distortionCoefs2 : "+printMatD(distortionCoefs2));
		//	distortionCoefs.reshape(4);
		//	distortionCoefs = distortionCoefs.colRange(0,distortionCoefs.cols()-1).rowRange(0, distortionCoefs.rows()).clone();
		//	distortionCoefs2.reshape(4);
		//	distortionCoefs2 = distortionCoefs2.colRange(0,distortionCoefs2.cols()-1).rowRange(0, distortionCoefs2.rows()).clone();

		log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
		//calibrate
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
			TermCriteria termCrit = new TermCriteria(opencv_core.TermCriteria.COUNT+opencv_core.TermCriteria.EPS, 30, 1e-6);

			opencv_calib3d.stereoCalibrate(allObjectPoints, allImagePoints,allImagePoints2, cameraMatrix, distortionCoefs,cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat, opencv_calib3d.CV_CALIB_FIX_INTRINSIC, termCrit);
			generateCalibrationString();
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nCamera EssentialMat: " + EssentialMat.toString() + "\n" + printMatD(EssentialMat)
				+ "\nFundamentalMat: " + FundamentalMat.toString() + "\n" + printMatD(FundamentalMat)
				+ calibrationString);
		} catch (RuntimeException e) {
			log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
		} finally {
			allImagePoints.resize(100);
			allObjectPoints.resize(100);
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			this.distortionCoefs = distortionCoefs;
			this.rotationVectors = rotationVectors;
			this.translationVectors = translationVectors;
		}

		getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
	}

	public void doStereoCalibrateV2() {
		if ((allImagePoints == null) || (allObjectPoints == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		//init
		Size imgSize = new Size(sx, sy);
		// make local references to hold results while thread is processing
		Mat cameraMatrix;
		Mat distortionCoefs;
		Mat cameraMatrix2;
		Mat distortionCoefs2;
		MatVector rotationVectors;
		MatVector translationVectors;
		MatVector rotationVectors2;
		MatVector translationVectors2;
		//Mat rotationVectorsfinal;
		//Mat translationVectorsfinal;

		Mat EssentialMat;
		Mat FundamentalMat;

		EssentialMat = new Mat();
		FundamentalMat = new Mat();

		cameraMatrix = new Mat();
		distortionCoefs = new Mat();
		cameraMatrix2 = new Mat();
		distortionCoefs2 = new Mat();
		rotationVectors = new MatVector();
		translationVectors = new MatVector();
		rotationVectors2 = new MatVector();
		translationVectors2 = new MatVector();
		rotationVectorsfinal = new Mat();
		translationVectorsfinal = new Mat();


		allImagePoints.resize(imageCounter);
		allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
		log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
		//	log.info( "allimagepointsforcalibration : "+printMatD(allImagePoints));
		//	log.info( "allobjectpointsforcalibration : "+printMatD(allObjectPoints));
		//calibrate first camera
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
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
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			this.distortionCoefs = distortionCoefs;
			this.rotationVectors = rotationVectors;
			this.translationVectors = translationVectors;
		}

		loadimagePointForStereoCalibration();
		log.info(String.format("**********allImagePoints2 is %d , allObjectPoints2 is %d,  images sized %d x %d", allImagePoints2.size(), allObjectPoints2.size(), imgSize.width(), imgSize.height()));

		/*	long nbeffimage= Long.min(allImagePoints2.size(),allObjectPoints2.size());
		long nbeffimage2= Long.min(allImagePoints.size(),allObjectPoints.size());
		long nbeffimage3= Long.min(nbeffimage2,nbeffimage);

		log.info("*********nbeffimage:"+nbeffimage+" nbeffimage2:"+nbeffimage2+" nbeffimage3:"+nbeffimage3);
		 */
		log.info(prevImageCounter.toString());
		allImagePoints2.resize(prevImageCounter);
		allObjectPoints2.resize(prevImageCounter);

		//		log.info( "allimagepointsforcalibrationV2 : "+printMatD(allImagePoints2));
		//		log.info( "allobjectpointsforcalibrationV2 : "+printMatD(allObjectPoints2));
		//calibrate second camera
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
			opencv_calib3d.calibrateCamera(allObjectPoints2, allImagePoints2, imgSize, cameraMatrix2, distortionCoefs2, rotationVectors2, translationVectors2);
			generateCalibrationString();
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nCamera matrix: " + cameraMatrix2.toString() + "\n" + printMatD(cameraMatrix2)
				+ "\nDistortion coefficients k_1 k_2 p_1 p_2 k_3 ...: " + distortionCoefs2.toString() + "\n" + printMatD(distortionCoefs2)
				+ calibrationString);
		} catch (RuntimeException e) {
			log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
		} finally {
			allImagePoints2.resize(100);
			allObjectPoints2.resize(100);
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix2 = cameraMatrix2;
			this.distortionCoefs2 = distortionCoefs2;
			this.rotationVectors2 = rotationVectors2;
			this.translationVectors2 = translationVectors2;
		}



		allImagePoints2.resize(prevImageCounter);
		allObjectPoints2.resize(prevImageCounter);
		allImagePoints.resize(imageCounter);
		allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
		log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
		//calibrate
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
			TermCriteria termCrit = new TermCriteria(opencv_core.TermCriteria.COUNT+opencv_core.TermCriteria.EPS, 30, 1e-6);
			opencv_calib3d.stereoCalibrate(allObjectPoints, allImagePoints,allImagePoints2, cameraMatrix, distortionCoefs,cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat, opencv_calib3d.CV_CALIB_FIX_INTRINSIC, termCrit);
			generateCalibrationString();
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nCamera EssentialMat: " + EssentialMat.toString() + "\n" + printMatD(EssentialMat)
				+ "\nFundamentalMat: " + FundamentalMat.toString() + "\n" + printMatD(FundamentalMat)
				+ calibrationString);
		} catch (RuntimeException e) {
			log.warning("calibration failed with exception " + e + "See https://adventuresandwhathaveyou.wordpress.com/2014/03/14/opencv-error-messages-suck/");
		} finally {
			allImagePoints.resize(100);
			allObjectPoints.resize(100);
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			this.distortionCoefs = distortionCoefs;
			this.rotationVectors = rotationVectors;
			this.translationVectors = translationVectors;
		}
		getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
	}


	public void doCalibrate() {
		if ((allImagePoints == null) || (allObjectPoints == null)) {
			log.warning("allImagePoints==null || allObjectPoints==null, cannot calibrate. Collect some images first.");
			return;
		}
		//init
		Size imgSize = new Size(sx, sy);
		// make local references to hold results while thread is processing
		Mat cameraMatrix;
		Mat distortionCoefs;
		MatVector rotationVectors;
		MatVector translationVectors;
		cameraMatrix = new Mat();
		distortionCoefs = new Mat();
		rotationVectors = new MatVector();
		translationVectors = new MatVector();

		allImagePoints.resize(imageCounter);
		allObjectPoints.resize(imageCounter); // resize has side effect that lists cannot hold any more data
		log.info(String.format("calibrating based on %d images sized %d x %d", allObjectPoints.size(), imgSize.width(), imgSize.height()));
		//calibrate
		try {
			setCursor(new Cursor(Cursor.WAIT_CURSOR));
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
			setCursor(Cursor.getDefaultCursor());
		}
		calibrated = true;
		synchronized (this) {
			this.cameraMatrix = cameraMatrix;
			this.distortionCoefs = distortionCoefs;
			this.rotationVectors = rotationVectors;
			this.translationVectors = translationVectors;
		}
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

		// Average focal lengths for X and Y axis (fx, fy).
		focalLengthPixels = (float) (cameraMatrixIndexer.get(0, 0) + cameraMatrixIndexer.get(1, 1)) / 2;

		// Go from pixels to millimeters, by multiplying by pixel size (in mm).
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
		serializeMat(dirPath, "cameraMatrix", cameraMatrix);
		serializeMat(dirPath, "distortionCoefs", distortionCoefs);

		serializeMat(dirPath, "cameraMatrix2", cameraMatrix2);
		serializeMat(dirPath, "distortionCoefs2", distortionCoefs2);

		serializeMat(dirPath, "rotationVectorsfinal", rotationVectorsfinal);
		serializeMat(dirPath, "translationVectorsfinal", translationVectorsfinal);

		serializeMat(dirPath, "EssentialMat", EssentialMat);
		serializeMat(dirPath, "FundamentalMat", FundamentalMat);

		serializeMat(dirPath, "P1", P1);
		serializeMat(dirPath, "P2", P2);

		serializeMat(dirPath, "R1", R1);
		serializeMat(dirPath, "R2", R2);

		serializeMat(dirPath, "Q", Q);


		//cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat,
		saved = true;
		generateCalibrationString();
	}


	synchronized public void doTakeMeasurmentsForCalibAndSave(){
		CalibrationDemanded=true;
		while (CalibrationDemanded != true){

		}
		doSaveImagePointsForStereoCalibration();
	}

	synchronized public void doStereoCalibrateV3(){
		CalibrationDemanded=true;
		doStereoCalibrateV2();
	}

	synchronized public void doSaveImagePointsForStereoCalibration() {
		//allImagePoints.resize(imageCounter)


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

		serializeMatVector(dirPath, "allImagePoints", allImagePoints);
		log.info("***********succeed serialisation allImagePoints***********");

		serializeMatVector(dirPath, "allObjectPoints", allObjectPoints);
		log.info("***********succeed serialisation allObjectPoints***********");
		saved = true;
		generateCalibrationString();

		try
		{
			File fac = new File("previousImageCounter.txt");
			if (!fac.exists())
			{
				fac.createNewFile();
			}
			System.out.println("\n----------------------------------");
			System.out.println("The file has been created.");
			System.out.println("------------------------------------");

			FileWriter wr = new FileWriter(fac);

			log.info(Integer.toString(imageCounter));
			wr.write(Integer.toString(imageCounter));

			wr.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

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

	synchronized public void doClearImages() {
		imageCounter = 0;
		//images.get(0).setNull();
		//images.get(1).setNull();
		//allImagePoints.setNull();
		//allObjectPoints.setNull();
		generateCalibrationString();
	}

	private void loadCalibration() {
		//cameraMatrix2, distortionCoefs2, imgSize, rotationVectorsfinal, translationVectorsfinal,EssentialMat, FundamentalMat,

		try {
			cameraMatrix = deserializeMat(dirPath, "cameraMatrix");
			distortionCoefs = deserializeMat(dirPath, "distortionCoefs");

			cameraMatrix2 = deserializeMat(dirPath, "cameraMatrix2");
			distortionCoefs2 = deserializeMat(dirPath, "distortionCoefs2");

			rotationVectorsfinal = deserializeMat(dirPath, "rotationVectorsfinal");
			translationVectorsfinal = deserializeMat(dirPath, "translationVectorsfinal");

			EssentialMat = deserializeMat(dirPath, "EssentialMat");
			FundamentalMat = deserializeMat(dirPath, "FundamentalMat");

			P1 = deserializeMat(dirPath, "P1");
			P2 = deserializeMat(dirPath, "P2");

			R1 = deserializeMat(dirPath, "R1");
			R2 = deserializeMat(dirPath, "R2");

			Q = deserializeMat(dirPath, "Q");

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


	private void loadimagePointForStereoCalibration() {

		final JFileChooser j = new JFileChooser();
		j.setCurrentDirectory(new File(dirPath));
		j.setApproveButtonText("Select folder");
		j.setDialogTitle("Select a folder that has XML files storing calibration");
		j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
		j.setApproveButtonText("Select folder");
		j.setApproveButtonToolTipText("Only enabled for a folder that has allImagePoints.xml and distortionCoefs.xml");
		setButtonState(j, j.getApproveButtonText(), allImageObjectExists(j.getCurrentDirectory().getPath()));
		j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent pce) {
				setButtonState(j, j.getApproveButtonText(), allImageObjectExists(j.getCurrentDirectory().getPath()));
			}
		});
		int ret = j.showOpenDialog(null);
		if (ret != JFileChooser.APPROVE_OPTION) {
			return;
		}
		dirPath = j.getSelectedFile().getPath();
		putString("dirPath", dirPath);
		//store image points
		if  ((allObjectPoints2 == null) || (allImagePoints2 == null)) {
			allImagePoints2 = new MatVector(100);
			allObjectPoints2 = new MatVector(100);
		}

		//take the image count
		try {
			File fac = new File("previousImageCounter.txt");
			FileReader re;

			re = new FileReader(fac);

			char [] s= new char[1];

			re.read(s);

			prevImageCounter= Integer.valueOf(String.valueOf(s));
			log.info(prevImageCounter.toString());

		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



		try {
			allImagePoints2 = deserializeMatVector(dirPath, "allImagePoints");
			log.info("***********succeed deserialisation allImagePoints***********");
			allObjectPoints2 = deserializeMatVector(dirPath, "allObjectPoints");
			log.info("***********succeed deserialisation allObjectsPoints***********");
			generateCalibrationString();
			if (calibrated) {
				log.info("Calibrated: loaded allImagePoints and allObjectPoints from folder " + dirPath);
			} else {
				log.warning("Uncalibrated: Something was wrong with calibration files so that allImagePoints or allObjectPoints could not be loaded");
			}
			getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
		} catch (Exception i) {
			log.warning("Could not load existing calibration from folder " + dirPath + " on construction:" + i.toString());
		}




	}



	private void loadimagePointForStereoCalibration2() {

		final JFileChooser j = new JFileChooser();
		j.setCurrentDirectory(new File(dirPath));
		j.setApproveButtonText("Select folder");
		j.setDialogTitle("Select a folder that has XML files storing calibration");
		j.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // let user specify a base filename
		j.setApproveButtonText("Select folder");
		j.setApproveButtonToolTipText("Only enabled for a folder that has allImagePoints.xml and distortionCoefs.xml");
		setButtonState(j, j.getApproveButtonText(), allImageObjectExists(j.getCurrentDirectory().getPath()));
		j.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent pce) {
				setButtonState(j, j.getApproveButtonText(), allImageObjectExists(j.getCurrentDirectory().getPath()));
			}
		});
		int ret = j.showOpenDialog(null);
		if (ret != JFileChooser.APPROVE_OPTION) {
			return;
		}
		dirPath = j.getSelectedFile().getPath();
		putString("dirPath", dirPath);
		//store image points
		if  ((allObjectPoints == null) || (allImagePoints == null)) {
			allImagePoints = new MatVector(100);
			allObjectPoints = new MatVector(100);
		}

		//take the image count
		try {
			File fac = new File("previousImageCounter.txt");
			FileReader re;

			re = new FileReader(fac);

			char [] s= new char[1];

			re.read(s);

			prevImageCounter= Integer.valueOf(String.valueOf(s));
			log.info(prevImageCounter.toString());

		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



		try {
			allImagePoints = deserializeMatVector(dirPath, "allImagePoints");
			log.info("***********succeed deserialisation allImagePoints***********");
			allObjectPoints = deserializeMatVector(dirPath, "allObjectPoints");
			log.info("***********succeed deserialisation allObjectsPoints***********");
			generateCalibrationString();
			if (calibrated) {
				log.info("Calibrated: loaded allImagePoints and allObjectPoints from folder " + dirPath);
			} else {
				log.warning("Uncalibrated: Something was wrong with calibration files so that allImagePoints or allObjectPoints could not be loaded");
			}
			getSupport().firePropertyChange(EVENT_NEW_CALIBRATION, null, this);
		} catch (Exception i) {
			log.warning("Could not load existing calibration from folder " + dirPath + " on construction:" + i.toString());
		}




	}



	private boolean allImageObjectExists(String dirPath) {
		String fn = dirPath + File.separator + "allImagePoints"+Integer.toString(0) + ".xml";
		File f = new File(fn);
		boolean allImageExists = f.exists();
		fn = dirPath + File.separator + "allObjectPoints"+Integer.toString(0) + ".xml";
		f = new File(fn);
		boolean allObjectExists = f.exists();
		if (allImageExists && allObjectExists) {
			return true;
		} else {
			return false;
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

		FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
		opencv_core.write(storage, name, sMat);
		storage.release();

		log.info("saved in " + fn);
	}

	public void serializeMatVector(String dir, String name, opencv_core.MatVector sMatVector) {
		/*Mat outputMat;
	    	outputMat = new Mat();
			opencv_core.merge(sMat,  outputMat);*/
		log.info(sMatVector.toString());
		for (int i = 0; i<sMatVector.size(); i++){
			String namebis = name+Integer.toString(i);
			serializeMat(dir, namebis,sMatVector.get(i));
			/*String fn = dir + File.separator + name+Integer.toString(i) + ".xml";
			opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.WRITE);
			opencv_core.write(storage, name+Integer.toString(i), sMatVector.get(i));
			storage.release();*/
			log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
				+ "\nMatserialized "+ i+": " + sMatVector.get(i).toString() + "\n" + printMatD(sMatVector.get(i)));
			//log.info("saved in " + fn);
		}
		log.info("saved in " + dir);

	}

	public opencv_core.Mat deserializeMat(String dir, String name) {
		String fn = dirPath + File.separator + name + ".xml";
		opencv_core.Mat mat = new opencv_core.Mat(CV_32FC3);
		System.out.println("coucou"+" typeIs"+mat.type());

		opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.READ);

		opencv_core.read(storage.get(name), mat);
		storage.release();

		if (mat.empty()) {
			return null;
		}
		/*	log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
			+ "\nmatrix: " + mat.toString() + "\n" + printMatD(mat));*/

		System.out.println(name+" typeIs"+mat.type());
		return mat;
	}


	public opencv_core.MatVector deserializeMatVector(String dir, String name) {
		/*		if (matvect == null) {
			matvect = new MatVector(100);
		}*/
		opencv_core.MatVector matvect = new opencv_core.MatVector(CV_32FC3);
		for(int i=0; i<prevImageCounter; i++){
			String namebis = name+Integer.toString(i);
			matvect.put(i,deserializeMat(dir, namebis));
			/*			String fn = dirPath + File.separator + name + ".xml";

			Mat mato = new Mat();

			opencv_core.FileStorage storage = new opencv_core.FileStorage(fn, opencv_core.FileStorage.READ);
			try{
				opencv_core.read(storage.get(name), mato);
				if (matvect == null) {
					matvect = new MatVector(100);
				}
				if (mato.empty()==false) {
					matvect.put(i,mato);
					log.info("see http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html \n"
						+ "\nmatrix: " + mato.toString() + "\n" + printMatD(mato));

				}
				else{
					log.info("mat"+i+"is empty");
				}

				storage.release();

			}catch (Exception e) {
				log.warning("Problem happened during the MatVector deserialization");
			}*/

		}

		return matvect;
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
		for (int k = 0; k < (M.rows()); k++) {
			for (int l = 0; l < (M.cols()); l++) {
				sb.append(String.format("%10.5f\t", M.getDoubleBuffer().get(c)));//M.getDoubleBuffer().get(c))
				c++;
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	private String printMatD(MatVector Mv) {
		String string =new String();
		for(int k =0; k< Mv.size(); k++){
			Mat M = new Mat();
			M=Mv.get(k);

			String str=printMatD(M);
			//log.info("Mat : "+str);
			string=string+"\n"+str;
		}
		//log.info("MatVector : "+string);
		return string;

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
		FloatPointer fp = new FloatPointer(2 * points.size());
		int idx = 0;
		for (Point2D.Float p : points) {
			fp.put(idx++, p.x);
			fp.put(idx++, p.y);
		}
		Mat dst = new Mat();
		Mat pixelArray = new Mat(1, 2 * points.size(), CV_32FC2, fp); // make wide 2 channel matrix of source event x,y
		opencv_imgproc.undistortPoints(pixelArray, dst, getCameraMatrix(), getDistortionCoefs());
		// get the camera matrix elements (focal lengths and principal point)
		DoubleIndexer k = getCameraMatrix().createIndexer();
		float fx, fy, cx, cy;
		fx = (float) k.get(0, 0);
		fy = (float) k.get(1, 1);
		cx = (float) k.get(0, 2);
		cy = (float) k.get(1, 2);
		idx = 0;
		FloatBuffer b = dst.getFloatBuffer();
		for (Point2D.Float p : points) {
			p.x = ((b.get(idx++) * fx) + cx);
			p.y = ((b.get(idx++) * fy) + cy);
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

	@Override
	public void update(Observable o, Object o1) {
		if (o instanceof AEChip) {
			if (chip.getNumPixels() > 0) {
				sx = chip.getSizeX();
				sy = chip.getSizeY(); // might not yet have been set in constructor
				//loadCalibration();
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



}
