/*
 * Copyright (C) 2018 minliu.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.eventio.ros.RosbagVOGTReader;
import net.sf.jaer.eventio.ros.RosbagVOGTReader.Se3Info;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ImageDisplay;

import org.apache.commons.lang3.math.IEEE754rUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.MathArrays;
import org.apache.commons.math3.util.MathUtils;

import org.jblas.DoubleMatrix;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
/**
 *
 * @author minliu
 */
@Description("Optical Flow method based on Visual Odometry")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RosbagVOFlow extends AbstractMotionFlowIMU {
    RosbagVOGTReader VOGTReader = null;
    private boolean subcribersAddedFlg = false;
    
    private JFrame sliceDepthMapFrame = null;
    private ImageDisplay sliceDepthmapImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private ImageDisplay.Legend sliceDepthmapImageDisplayLegend;
    private static final String LEGEND_SLICES = "Depth image";
    private boolean showDepthmap = getBoolean("showDepthmap", false);; // Display the depth map
   
    private JFrame sliceRectImgFrame = null;    
    private ImageDisplay sliceRectImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private ImageDisplay.Legend sliceRectImageDisplayLegend;
    private static final String RECT_LEGEND_SLICES = "Rect image";
    private boolean showRectimg = getBoolean("showRectimg", false);; // Display the rectified image
    
    private final ApsFrameExtractor apsFrameExtractor;

    public RosbagVOFlow(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        VOGTReader = new RosbagVOGTReader(chip);
        chain.add(VOGTReader);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.setShowAPSFrameDisplay(false);
        chain.add(apsFrameExtractor);
        setEnclosedFilterChain(chain);
        
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        resetFilter();
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter(); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        if (!subcribersAddedFlg) {
            VOGTReader.doAddSubscribers();
            subcribersAddedFlg = true;
        }        

        // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }
        
        float[] depth_image = VOGTReader.getCurrent_depth_image();
        
        
        DoubleMatrix current_pose_se3 = VOGTReader.getCurrentPoseSe3();
        if(showDepthmap && depth_image != null && chip != null) {
            float depth_max = IEEE754rUtils.max(depth_image);
            float[] depth_image_reverse = new float[chip.getSizeX() * chip.getSizeY()];
            for (int index = 0; index < depth_image.length; index++) {
                depth_image_reverse[index] = depth_image[(chip.getSizeY() - index/chip.getSizeX() - 1) * chip.getSizeX() + index%chip.getSizeX()]/depth_max;
            }
            drawDepth(depth_image_reverse);
        }
        
        float[] aps_image = apsFrameExtractor.getDisplayBuffer();
        float aps_image_max = IEEE754rUtils.max(aps_image);
        for (int index = 0; index < aps_image.length; index++) {
            aps_image[index] = aps_image[index]/aps_image_max;
        }
//        Mat exp_img = Imgcodecs.imread("G:/MVSEC/ApsFrame-2018-04-12T22-29-37+0200.png", 0);
//        exp_img.convertTo(exp_img, CvType.CV_32FC1);
        Mat raw_img = new Mat(apsFrameExtractor.height, apsFrameExtractor.width, CvType.CV_32FC1);
        raw_img.put(0, 0, aps_image);
        Mat undistorted_img = new Mat();
        if (VOGTReader.getCurrentCameraInfo() == null) {
            return in;
        }
        Mat K  = VOGTReader.getCurrentCameraInfo().K;
//        System.out.println(img.dump());
        Mat D = VOGTReader.getCurrentCameraInfo().D;
//        Mat newCameraMatrix = Calib3d.getOptimalNewCameraMatrix(K, D, new Size(chip.getSizeX(), chip.getSizeY()), 1);
        Mat newCameraMatrix = VOGTReader.getCurrentCameraInfo().P.submat(0, 3, 0, 3);
        Mat rectify_R = VOGTReader.getCurrentCameraInfo().R;
        Mat mapx = new Mat();
        Mat mapy = new Mat();
//        Calib3d.undistortImage(raw_img, undistorted_img, K, D, newCameraMatrix, new Size(chip.getSizeX(), chip.getSizeY()));
         Calib3d.initUndistortRectifyMap(K, D, rectify_R, newCameraMatrix, 
                new Size(chip.getSizeX(), chip.getSizeY()), CvType.CV_32FC1, mapx, mapy);  // For fisheye camera;
//        Imgproc.initUndistortRectifyMap(K, D, rectify_R, newCameraMatrix, 
//                new Size(chip.getSizeX(), chip.getSizeY()), CvType.CV_32FC1, mapx, mapy);  // For normal camera;
        Imgproc.remap(raw_img, undistorted_img, mapx, mapy, Imgproc.INTER_LINEAR);
        float[] undistorted_image = new float[(int)(undistorted_img.total() * undistorted_img.channels())];
        undistorted_img.get(0, 0, undistorted_image);
        if(showRectimg && undistorted_image != null && chip != null) {
            drawRectImg(undistorted_image);
        }
        ArrayList<Se3Info> se3InfoList = VOGTReader.getCurrentSe3Info();
//        long current_pose_ts = se3Info.se3_ts.getTime()*1000+(long)(se3Info.se3_ts.getNanos()/1000);
//        long current_pose_relative_ts = current_pose_ts - firstAbsoluteTs;

        
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
            if (isInvalidAddress(0)) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }
            
            depth_image = VOGTReader.getCurrent_depth_image();
            int associatedTime = (int) se3InfoList.get(se3InfoList.size() - 1).se3_ts_relative_us;
            if ( se3InfoList.size() < 2 || Math.abs(ts - associatedTime) >  2000) {
                continue;
            }
            
            current_pose_se3 = VOGTReader.getCurrentPoseSe3();
            long current_pose_seq_num = VOGTReader.getCurrentPose_seq_num();
            
            Timestamp current_depth_ts = VOGTReader.getCurrentDepth_ts();            
            Timestamp current_pose_ts = se3InfoList.get(se3InfoList.size() - 1).se3_ts;
            Timestamp last_pose_ts = se3InfoList.get(se3InfoList.size() - 2).se3_ts;
//            current_pose_ts = VOGTReader.getCurrentPose_ts();
//            last_pose_ts = VOGTReader.getLastPose_ts();
            
            double fx = K.get(0, 0)[0];
            double fy = K.get(1, 1)[0];
            double cx = K.get(0, 2)[0];
            double cy = K.get(1, 2)[0];
            
            DoubleMatrix offsetPixel = new DoubleMatrix();
            if (depth_image != null && current_pose_se3 != null && mapx != null && mapy != null) {
                MatOfPoint2f originPt = new MatOfPoint2f(new Point(x, y));
                MatOfPoint2f dstPt = new MatOfPoint2f();
                double undist_x = mapx.get(y, x)[0];
                double undist_y = mapy.get(y, x)[0];
//                undist_x = x;
//                undist_y = y;
                if (undist_x < 0 || undist_x > chip.getSizeX() || undist_y < 0 || undist_y > chip.getSizeY()) {
                    continue;
                }
                double Z = depth_image[(int)(undist_x) + (chip.getSizeY() - (int)(undist_y) - 1) * chip.getSizeX()];
                double X = (undist_x - cx) * Z/fx;
                double Y = (undist_y - cy) * Z/fy;
                

                DoubleMatrix pose2pixelJaccobi = new DoubleMatrix(new double[][]{
                    {fx/Z, 0, -fx*X/(Z*Z), -fx*X*Y/(Z*Z), fx + fx*X*X/(Z*Z), -fx*Y/Z},
                    {0, fy/Z, -fy*Y/(Z*Z),  -fy - fx*Y*Y/(Z*Z), fy*X*Y/(Z*Z), fy*X/Z}});
                current_pose_se3.max();
//                current_pose_se3 = DoubleMatrix.zeros(6, 1);
//                current_pose_se3.put(4, 0.1);
                offsetPixel = pose2pixelJaccobi.mmul(current_pose_se3);
                double delta_ts = (current_pose_ts.getNanos() - last_pose_ts.getNanos())/1e3/1e6;
                vx = (float) -offsetPixel.get(0)/(float)delta_ts;
                vy = (float) -offsetPixel.get(1)/(float)delta_ts;
                v = (float) Math.sqrt((vx * vx) + (vy * vy));
                processGoodEvent();
//                log.info("Current pose relative timestamp is:  " + (current_pose_ts.getTime()*1000+(long)(current_pose_ts.getNanos()/1000) 
//                        - chip.getAeInputStream().getAbsoluteStartingTimeMs() * 1000));
//
//                log.info("Current depth relative timestamp is: " + (current_depth_ts.getTime()*1000+(long)(current_depth_ts.getNanos()/1000) 
//                        - chip.getAeInputStream().getAbsoluteStartingTimeMs() * 1000));                
                
            }            
        }

        return in;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals(AEInputStream.EVENT_REWOUND)) {
            resetFilter();
        }
    }
    
    private void drawDepth(float[] depth) {
//        log.info("drawing slices");
        if (sliceDepthMapFrame == null) {
            String windowName = "Depth images";
            sliceDepthMapFrame = new JFrame(windowName);
            sliceDepthMapFrame.setLayout(new BoxLayout(sliceDepthMapFrame.getContentPane(), BoxLayout.Y_AXIS));
            sliceDepthMapFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            sliceDepthmapImageDisplay = ImageDisplay.createOpenGLCanvas();
            sliceDepthmapImageDisplay.setBorderSpacePixels(10);
            sliceDepthmapImageDisplay.setImageSize(sizex, sizey);
            sliceDepthmapImageDisplay.setSize(200, 200);
            sliceDepthmapImageDisplay.setGrayValue(0);
            sliceDepthmapImageDisplayLegend = sliceDepthmapImageDisplay.addLegend(LEGEND_SLICES, 0, -10);
            panel.add(sliceDepthmapImageDisplay);

            sliceDepthMapFrame.getContentPane().add(panel);
            sliceDepthMapFrame.pack();
            sliceDepthMapFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowDepthmap(false);
                }
            });
        }
        if (!sliceDepthMapFrame.isVisible()) {
            sliceDepthMapFrame.setVisible(true);
        }

        sliceDepthmapImageDisplay.clearImage();

        sliceDepthmapImageDisplay.setPixmapFromGrayArray(depth);
        if (sliceDepthmapImageDisplayLegend != null) {
            sliceDepthmapImageDisplayLegend.s
                    = LEGEND_SLICES;
        }

        sliceDepthmapImageDisplay.repaint();
    }
    
    public boolean isShowDepthmap() {
        return showDepthmap;
    }

    /**
     * @param showSlices
     * @param showSlices the option of displaying bitmap
     */
    synchronized public void setShowDepthmap(boolean showDepthmap) {
        boolean old = this.showDepthmap;
        this.showDepthmap = showDepthmap;
        getSupport().firePropertyChange("showDepthmap", old, this.showDepthmap);
        putBoolean("showDepthmap", showDepthmap);
    }    

    private void drawRectImg(float[] rectImg) {
//        log.info("drawing slices");
        if (sliceRectImgFrame == null) {
            String windowName = "Rectified images";
            sliceRectImgFrame = new JFrame(windowName);
            sliceRectImgFrame.setLayout(new BoxLayout(sliceRectImgFrame.getContentPane(), BoxLayout.Y_AXIS));
            sliceRectImgFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            sliceRectImageDisplay = ImageDisplay.createOpenGLCanvas();
            sliceRectImageDisplay.setBorderSpacePixels(10);
            sliceRectImageDisplay.setImageSize(sizex, sizey);
            sliceRectImageDisplay.setSize(200, 200);
            sliceRectImageDisplay.setGrayValue(0);
            sliceRectImageDisplayLegend = sliceRectImageDisplay.addLegend(RECT_LEGEND_SLICES, 0, -10);
            panel.add(sliceRectImageDisplay);

            sliceRectImgFrame.getContentPane().add(panel);
            sliceRectImgFrame.pack();
            sliceRectImgFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowRectimg(false);
                }
            });
        }
        if (!sliceRectImgFrame.isVisible()) {
            sliceRectImgFrame.setVisible(true);
        }

        sliceRectImageDisplay.clearImage();

        sliceRectImageDisplay.setPixmapFromGrayArray(rectImg);
        if (sliceRectImageDisplayLegend != null) {
            sliceRectImageDisplayLegend.s
                    = RECT_LEGEND_SLICES;
        }

        sliceRectImageDisplay.repaint();
    }
    
    public boolean isShowRectimg() {
        return showRectimg;
    }

    /**
     * @param showSlices
     * @param showSlices the option of displaying bitmap
     */
    synchronized public void setShowRectimg(boolean showRectimg) {
        boolean old = this.showRectimg;
        this.showRectimg = showRectimg;
        getSupport().firePropertyChange("showRectimg", old, this.showRectimg);
        putBoolean("showRectimg", showRectimg);
    }       
}
