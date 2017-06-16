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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ImageDisplay;


/**
 *
 * @author minliu
 */
@Description("Optical Flow methods based on OpenCV")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpenCVFlow extends EventFilter2D 
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
    
    private ApsFrameExtractor apsFrameExtractor;
    private JFrame apsFrame = null;
    protected boolean showAPSFrameDisplay = getBoolean("showAPSFrameDisplay", true);
    private int[][] color = new int[100][3];
    private float[] oldBuffer = null, newBuffer = null;
    private PatchMatchFlow patchFlow;
    
    
    public OpenCVFlow(AEChip chip) {
        super(chip);
        System.out.println("Welcome to OpenCV " + Core.VERSION);

        apsFrameExtractor = new ApsFrameExtractor(chip);
        
        apsFrame = new JFrame("Optical Flow Result Frame");
        apsFrame.setPreferredSize(new Dimension(400, 400));
        apsFrame.getContentPane().add(apsFrameExtractor.apsDisplay, BorderLayout.CENTER);
        apsFrame.pack();
        apsFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });        
        
        FilterChain chain = new FilterChain(chip);        
        try {
            patchFlow = new PatchMatchFlow(chip);
            patchFlow.setFilterEnabled(true);
            chain.add(patchFlow);
        } catch (Exception e) {
            log.warning("could not setup PatchMatchFlow fiter.");
        }        
        setEnclosedFilterChain(chain);
        
        apsFrameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);   
        patchFlow.getSupport().addPropertyChangeListener(PatchMatchFlow.EVENT_NEW_SLICES,this);
        chip.addObserver(this); // to allocate memory once chip size is known
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {   
        if(!isFilterEnabled()) {
                return in;
        }
        getEnclosedFilterChain().filterPacket(in);        
        
        apsFrameExtractor.apsDisplay.checkPixmapAllocation();
        apsFrame.setVisible(true);
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
                apsFrameExtractor.putAPSevent(e);
            }
        }
        apsFrameExtractor.apsDisplay.repaint();
        
        return in;
        
//        while(true) {
//            Mat frame = new Mat();
//            Mat frame_gray = new Mat();
//            for(int skipNum = 0; skipNum <= 20; skipNum ++) {
//                ret = cap.read(frame);                    
//            }
//            if(frame.empty() || ret == false) {
//                System.out.println("The frame cannot be read or the frame is empty.\n");
//                System.exit(1);                
//            }            
//
//            Imgproc.cvtColor(frame,frame_gray,Imgproc.COLOR_BGR2GRAY);
//            Imgproc.goodFeaturesToTrack(old_gray, p0, feature_params.maxCorners, feature_params.qualityLevel, feature_params.minDistance); 
//
//            MatOfPoint2f prevPts = new MatOfPoint2f(p0.toArray());
//            MatOfPoint2f nextPts = new MatOfPoint2f();
//            MatOfByte status = new MatOfByte();
//            MatOfFloat err = new MatOfFloat();
//
//            int featureNum = prevPts.checkVector(2, CvType.CV_32F, true);
//            System.out.println("The number of feature detected is : " + featureNum);
//
//            try {
//                Video.calcOpticalFlowPyrLK(old_gray, frame_gray, prevPts, nextPts, status, err);            
//            } catch (Exception e) {
//                System.err.println(e);
//                frame_gray.copyTo(old_gray);
//                continue;
//            }
//
//            // TODO: Select good points 
//
//            // draw the tracks
//            Point[] prevPoints = prevPts.toArray();
//            Point[] nextPoints = nextPts.toArray();
//            byte[] st = status.toArray();
//            float[] er = err.toArray();    
//            Mat mask = new Mat(old_gray.rows(), old_gray.cols(), CvType.CV_8UC1);
//            for (int i = 0; i < prevPoints.length; i++) {
//                Imgproc.line(frame, prevPoints[i], nextPoints[i], new Scalar(color[i][0],color[i][1],color[i][2]), 2);  
//                Imgproc.circle(frame,prevPoints[i],5,new Scalar(color[i][0],color[i][1],color[i][2]),-1);
//            }
//
//            // Save the frames.
//            // Imgcodecs.imwrite("tmpfiles/old_frame.jpg", old_frame);
//            Imgcodecs.imwrite("tmpfiles/frame.jpg", frame);  
//            // showResult(frame);
//            // displayImage(Mat2BufferedImage(frame));
//
//            
//            // Now update the previous frame and previous points
//            frame_gray.copyTo(old_gray);
//            p0 = new MatOfPoint(nextPts.toArray());
//        }    
        
    }

    @Override
    public void resetFilter() {
        apsFrameExtractor.resetFilter();
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
            if (apsFrame != null) {
                apsFrame.setVisible(false);
            }
        }
    } 
    
    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(ApsFrameExtractor.EVENT_NEW_FRAME)) {
            if(newBuffer == null) {
                newBuffer = (float[]) evt.getNewValue();                
                return;
            }
            oldBuffer = newBuffer;
            float[] buffer = apsFrameExtractor.apsDisplay.getPixmapArray();
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
            apsFrameExtractor.apsDisplay.setPixmapFromGrayArray(return_buff);           
        }
        
        if (evt.getPropertyName().equals(PatchMatchFlow.EVENT_NEW_SLICES)) {
            byte[][][] tMinus2dSlice = (byte[][][]) evt.getOldValue();
            byte[][][] tMinusdSlice = (byte[][][]) evt.getNewValue();
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
        if (apsFrame != null) {
            apsFrame.setVisible(showAPSFrameDisplay);
        }
        getSupport().firePropertyChange("showAPSFrameDisplay", null, showAPSFrameDisplay);
    }
}


