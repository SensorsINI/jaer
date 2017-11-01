/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.davis.calibration.SingleCameraCalibration;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.file.Files.list;
import static java.rmi.Naming.list;

import java.util.ArrayList;
import static java.util.Collections.list;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.opencv.core.Core; 
import org.opencv.core.CvType; 
import org.opencv.core.Mat; 
import org.opencv.core.MatOfByte; 
import org.opencv.core.MatOfFloat; 
import org.opencv.core.MatOfPoint; 
import org.opencv.core.TermCriteria;
import org.opencv.core.MatOfPoint2f; 
import org.opencv.core.Point; 
import org.opencv.core.Rect; 
import org.opencv.core.RotatedRect; 
import org.opencv.core.Scalar; 
import org.opencv.core.Size; 
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.bytedeco.javacpp.opencv_videoio.VideoWriter;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU;
import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.v;
import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.vx;
import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.vy;
import com.jogamp.common.util.Bitstream.ByteStream;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
import net.sf.jaer.eventprocessing.EventFilter;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ImageDisplay;
import org.apache.commons.lang3.ArrayUtils;


/**
 *
 * @author minliu
 */
@Description("Optical Flow methods based on OpenCV")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpenCVFlow extends AbstractMotionFlow 
                        implements PropertyChangeListener, Observer /* Observer needed to get change events on chip construction */ {

    static { 
    String jvmVersion = System.getProperty("sun.arch.data.model");
        
    try {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    // System.loadLibrary("opencv_ffmpeg320_" + jvmVersion);   // Notice, cannot put the file type extension (.dll) here, it will add it automatically. 
    } catch (UnsatisfiedLinkError e) {
        System.err.println("Native code library failed to load.\n" + e);
        // System.exit(1);
        }
    }
    
    private final ImageDisplay OFResultDisplay;
    
    private JFrame OFResultFrame = null;
    protected boolean showAPSFrameDisplay = getBoolean("showAPSFrameDisplay", true);
    private int[][] color = new int[100][3];
    private float[] oldBuffer = null, newBuffer = null;
    private PatchMatchFlow patchFlow;
    
    
    public OpenCVFlow(AEChip chip) {
        super(chip);
        System.out.println("Welcome to OpenCV " + Core.VERSION);

        OFResultDisplay = ImageDisplay.createOpenGLCanvas();
        
        OFResultFrame = new JFrame("Optical Flow Result Frame");
        OFResultFrame.setPreferredSize(new Dimension(800, 800));
        OFResultFrame.getContentPane().add(OFResultDisplay, BorderLayout.CENTER);
        OFResultFrame.pack();
        OFResultFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        }); 
        
        FilterChain chain = new FilterChain(chip);        
        try {
            patchFlow = new PatchMatchFlow(chip);
            patchFlow.imuFlowEstimator = this.imuFlowEstimator;
            patchFlow.setFilterEnabled(true);
            chain.add(patchFlow);
        } catch (Exception e) {
            log.warning("could not setup PatchMatchFlow fiter.");
        }        
        setEnclosedFilterChain(chain);

        
        //apsFrameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);   
        patchFlow.getSupport().addPropertyChangeListener(PatchMatchFlow.EVENT_NEW_SLICES,this);
        chip.addObserver(this); // to allocate memory once chip size is known
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {   
        if(!isFilterEnabled()) {
                return in;
        }
        setupFilter(in);
        
        if (showAPSFrameDisplay && !OFResultFrame.isVisible()) {
            OFResultFrame.setVisible(true);
        }
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        while (i.hasNext()) {
            Object o = i.next();
            if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
            if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent) o).isApsData()) {
                continue;
            }
            PolarityEvent ein = (PolarityEvent) o;
            if (!extractEventInfo(o)) {
                continue;
            }
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                if (imuFlowEstimator.calculateImuFlow(o)) {
                    continue;
                }
            }
            if (xyFilter()) {
                continue;
            }
            countIn++;
        }

        OFResultDisplay.checkPixmapAllocation();       
        
//        final ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
//        if (packet == null) {
//            return null;
//        }
//        if (packet.getEventClass() != ApsDvsEvent.class) {
//            EventFilter.log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
//            return null;
//        }
//        final Iterator apsItr = packet.fullIterator();
//        while (apsItr.hasNext()) {
//            final ApsDvsEvent e = (ApsDvsEvent) apsItr.next();
//            if (e.isApsData()) {
//                apsFrameExtractor.putAPSevent(e);
//            }
//        }
        if(isShowAPSFrameDisplay()) {
            OFResultDisplay.repaint();            
        }
        // motionFlowStatistics.updatePacket(countIn, countOut);
        return isDisplayRawInput() ? in : dirPacket;
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();

        if(patchFlow != null) {
            patchFlow.resetFilter();            
        }
        
        if (OFResultDisplay != null) {
            OFResultDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());            
        }
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((o != null) && (arg != null)) {
            if ((o instanceof AEChip) && (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY))) {
                initFilter();
            }
        }    
    }
    
    @Override
    public synchronized void setFilterEnabled(final boolean yes) {
        super.setFilterEnabled(yes); // To change body of generated methods, choose Tools | Templates.
        if (!isFilterEnabled()) {
            if (OFResultFrame != null) {
                OFResultFrame.setVisible(false);
            }
        }
    } 
    
    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); // resets filter on rewind, etc
        if (evt.getPropertyName().equals(ApsFrameExtractor.EVENT_NEW_FRAME)) {
            if(newBuffer == null) {
                newBuffer = (float[]) evt.getNewValue();                
                return;
            }
            oldBuffer = newBuffer;
            float[] buffer = OFResultDisplay.getPixmapArray();
            byte[] byteBuffer = new byte[buffer.length];
            for(int i=0; i<buffer.length;i++) {
                byteBuffer[i] = (byte) (buffer[i] * 255);
            }
            newBuffer = (float[]) evt.getNewValue();
            Mat newFrame = new Mat(chip.getSizeY(), chip.getSizeX(), CvType.CV_32F);
            newFrame.put(0, 0, newBuffer);   
            Mat oldFrame = new Mat(chip.getSizeY(), chip.getSizeX(), CvType.CV_32F);
            oldFrame.put(0, 0, oldBuffer);       
            
            // params for ShiTomasi corner detection            
            FeatureParams feature_params  = new FeatureParams(100, 0.3, 7, 7);
            
            // Feature extraction
            MatOfPoint p0 = new MatOfPoint();
            Imgproc.goodFeaturesToTrack(newFrame, p0, feature_params.maxCorners, feature_params.qualityLevel, feature_params.minDistance);       

            MatOfPoint2f prevPts = new MatOfPoint2f(p0.toArray());
            MatOfPoint2f nextPts = new MatOfPoint2f();
            MatOfByte status = new MatOfByte();
            MatOfFloat err = new MatOfFloat();

            int featureNum = prevPts.checkVector(2, CvType.CV_32F, true);
            System.out.println("The number of feature detected is : " + featureNum);     

            try {
                Video.calcOpticalFlowPyrLK(oldFrame, newFrame, prevPts, nextPts, status, err);            
            } catch (Exception e) {
                System.err.println(e);
                // newFrame.copyTo(oldFrame);
            }            
            
            // TODO: Select good points 

            // draw the tracks
            Point[] prevPoints = prevPts.toArray();
            // Point[] nextPoints = nextPts.toArray();
            // byte[] st = status.toArray();
            // float[] er = err.toArray();    
            Mat mask = new Mat(newFrame.rows(), newFrame.cols(), CvType.CV_32F);
            for (int i = 0; i < prevPoints.length; i++) {
                // Imgproc.line(displayFrame, prevPoints[i], nextPoints[i], new Scalar(color[i][0],color[i][1],color[i][2]), 2);  
                Imgproc.circle(newFrame,prevPoints[i], 5, new Scalar(255,255,255),-1);
            }
            
            float[] return_buff = new float[(int) (newFrame.total() * 
                                            newFrame.channels())];
            newFrame.get(0, 0, return_buff);
            // OFResultDisplay.setPixmapFromGrayArray(return_buff);           
        }
        
        if (evt.getPropertyName().equals(PatchMatchFlow.EVENT_NEW_SLICES)) {
            this.countIn = patchFlow.countIn;
            System.out.println("The number of valid output in patchFlow is : " + patchFlow.countOut); 
     
            MyThread OpenCVCalThread = new MyThread("OpenCV_Calculation");
            OpenCVCalThread.setName("OpenCV_Calculation");
            OpenCVCalThread.setEvt(evt);
            OpenCVCalThread.run();           
        }
        
    }

    public class MyThread extends Thread
    {
        private String name;
        PropertyChangeEvent evt;
        
        public MyThread(String name)
        {
            this.name = name;
        }
        public void setEvt(PropertyChangeEvent evt) {
            this.evt = evt;
        }

        public void run() {
            byte[][][] tMinus2dSlice = (byte[][][]) evt.getOldValue();
            byte[][][] tMinusdSlice = (byte[][][]) evt.getNewValue();  
            Mat newFrame = new Mat(chip.getSizeY(), chip.getSizeX(), CvType.CV_8U);
            Mat oldFrame = new Mat(chip.getSizeY(), chip.getSizeX(), CvType.CV_8U);

//            /* An example to flatten the nested array to 1D array */
//            double[][][] vals = {{{1.1, 2.1}, {3.2, 4.1}}, {{5.2, 6.1}, {7.1, 8.3}}};
//
//            double[] test = Arrays.stream(vals)
//                    .flatMap(Arrays::stream)
//                    .flatMapToDouble(Arrays::stream)
//                    .toArray();
//
//            System.out.println(Arrays.toString(test));


            // Flatten the two arrays to 1D array
            byte[] old1DArray = new byte[chip.getSizeY() * chip.getSizeX()], 
                    new1DArray = new byte[chip.getSizeY() * chip.getSizeX()];
            for (int i = 0; i < chip.getSizeY(); i++) {
                for (int j = 0; j < chip.getSizeX(); j++) {
                    old1DArray[chip.getSizeX()*i + j] = (byte)(tMinus2dSlice[0][j][i] * 20);  // Multiple the intensity so the feature can be extracted
                    new1DArray[chip.getSizeX()*i + j] = (byte)(tMinusdSlice[0][j][i] * 20);         
                }
            }

            List oldList = Arrays.asList(ArrayUtils.toObject(old1DArray));
            float oldGrayScale = Collections.max((List<Byte>) oldList);     // Set the maximum of tha array as the scale value.
            List newList = Arrays.asList(ArrayUtils.toObject(new1DArray));
            float newGrayScale = Collections.max((List<Byte>) newList);     // Set the maximum of tha array as the scale value.        

            newFrame.put(0, 0, new1DArray);
            oldFrame.put(0, 0, old1DArray);

            // params for ShiTomasi corner detection            
            FeatureParams feature_params  = new FeatureParams(100, 0.3, 7, 7);

            // Feature extraction
            MatOfPoint p0 = new MatOfPoint();
            Imgproc.goodFeaturesToTrack(oldFrame, p0, feature_params.maxCorners, feature_params.qualityLevel, feature_params.minDistance);       

            MatOfPoint2f prevPts = new MatOfPoint2f(p0.toArray());
            MatOfPoint2f nextPts = new MatOfPoint2f();
            MatOfByte status = new MatOfByte();
            MatOfFloat err = new MatOfFloat();

            int featureNum = prevPts.checkVector(2, CvType.CV_32F, true);
            System.out.println("The number of feature detected is : " + featureNum);     

            try {
                Video.calcOpticalFlowPyrLK(oldFrame, newFrame, prevPts, nextPts, status, err);            
            } catch (Exception e) {
                System.err.println(e);                   
                return;
            } finally {
                float[] new_slice_buff = new float[(int) (newFrame.total() *
                        newFrame.channels())];
                for (int i = 0; i < chip.getSizeY(); i++) {
                    for (int j = 0; j < chip.getSizeX(); j++) {
                        new_slice_buff[chip.getSizeX()*i + j] = new1DArray[chip.getSizeX()*i + j]/newGrayScale;
                    }
                }
                OFResultDisplay.setPixmapFromGrayArray(new_slice_buff);
//                DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
//                File folder = new File("EventSlice/" + chip.getAeInputStream().getFile().getName() + patchFlow.getSliceMethod().toString());
//                folder.mkdir();
//                File outputfile = new File(folder, String.format("Clear-pid%d.jpg", this.getId()));
//                Core.flip(newFrame, newFrame, 0);
//                try {
//                    ImageIO.write(Mat2BufferedImage(newFrame), "jpg", outputfile);
//                } catch (IOException ex) {
//                    Logger.getLogger(OpenCVFlow.class.getName()).log(Level.SEVERE, null, ex);
//                }
            }            

            // draw the tracks
            Point[] prevPoints = prevPts.toArray();
            Point[] nextPoints = nextPts.toArray();
            byte[] st = status.toArray();
            float[] er = err.toArray();

            // Select good points  and copy them for output
            int index = 0;
            for(byte stTmp: st) {
                if(stTmp == 1) {
                    e = new PolarityEvent();
                    x = (short)(prevPoints[index].x);
                    y = (short)prevPoints[index].y;
                    e.x = (short)x;
                    e.y = (short)y;    // e, x and y all of them are used in processGoodEvent();
//                    e.timestamp = ts;
                    vx = (float)(nextPoints[index].x - prevPoints[index].x) * 1000000 / -patchFlow.getSliceDeltaT();
                    vy = (float)(nextPoints[index].y - prevPoints[index].y) * 1000000 / -patchFlow.getSliceDeltaT();
                    v = (float) Math.sqrt(vx * vx + vy * vy);
                    processGoodEvent();
                    // exportFlowToMatlab(false);
                    index++;
                }
            }
            System.out.println("The number of valid output in OpenCV Flow is : " + countOut);    
            motionFlowStatistics.updatePacket(countIn, countOut);
            countOut = 0;

            Mat mask = new Mat(newFrame.rows(), newFrame.cols(), CvType.CV_32F);
            for (int i = 0; i < prevPoints.length; i++) {
                // Imgproc.line(displayFrame, prevPoints[i], nextPoints[i], new Scalar(color[i][0],color[i][1],color[i][2]), 2);  
                // Imgproc.circle(newFrame,prevPoints[i], 5, new Scalar(255,255,255),-1);
            }  
        }
    }
    
    public class FeatureParams {
        
        int maxCorners;
        double qualityLevel;
        double minDistance;
        int blockSize;

        public FeatureParams(int maxCorners, double qualityLevel, int minDistance, int blockSize) {
            this.maxCorners = maxCorners;
            this.qualityLevel = qualityLevel;
            this.minDistance = minDistance;
            this.blockSize = blockSize;
        }
    }  

    public class LKParams {
        int winSizeX;
        int winSizeY;
        int maxLevel;
        TermCriteria criteria = new TermCriteria();

        public LKParams(int winSizeX, int winSizeY, int maxLevel, TermCriteria criteria) {
            this.winSizeX = winSizeX;
            this.winSizeY = winSizeY;
            this.maxLevel = maxLevel;
            this.criteria = criteria;
        }
    }

    public void showResult(Mat img) {
        Imgproc.resize(img, img, new Size(640, 480));
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", img, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        BufferedImage bufImage = null;
        try {
            InputStream in = new ByteArrayInputStream(byteArray);
            bufImage = ImageIO.read(in);
            JFrame frame = new JFrame();
            frame.getContentPane().add(new JLabel(new ImageIcon(bufImage)));
            frame.pack();
            frame.setVisible(true);
        } catch (IOException | HeadlessException e) {
            e.printStackTrace();
        }
    }

    public BufferedImage Mat2BufferedImage(Mat m){
        // source: http://answers.opencv.org/question/10344/opencv-java-load-image-to-gui/
        // Fastest code
        // The output can be assigned either to a BufferedImage or to an Image

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if ( m.channels() > 1 ) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels()*m.cols()*m.rows();
        byte [] b = new byte[bufferSize];
        m.get(0,0,b); // get all the pixels
        BufferedImage image = new BufferedImage(m.cols(),m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);  
        return image;

    }

    public void displayImage(Image img2) {   
        //BufferedImage img=ImageIO.read(new File("/HelloOpenCV/lena.png"));
        ImageIcon icon=new ImageIcon(img2);
        JFrame frame=new JFrame();
        frame.setLayout(new FlowLayout());        
        frame.setSize(img2.getWidth(null)+50, img2.getHeight(null)+50);     
        JLabel lbl=new JLabel();
        lbl.setIcon(icon);
        frame.add(lbl);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
        if (OFResultFrame != null) {
            OFResultFrame.setVisible(showAPSFrameDisplay);
        }
        getSupport().firePropertyChange("showAPSFrameDisplay", null, showAPSFrameDisplay);
    }    
}


