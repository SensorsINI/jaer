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
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ImageDisplay;
import org.apache.commons.lang3.math.IEEE754rUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.MathArrays;
import org.apache.commons.math3.util.MathUtils;
import org.jblas.DoubleMatrix;

/**
 *
 * @author minliu
 */
@Description("Optical Flow methods based on Visual Odometry")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RosbagVOFlow extends AbstractMotionFlowIMU {
    RosbagVOGTReader VOGTReader = null;
    private boolean subcribersAddedFlg = false;
    
    private JFrame sliceDepthMapFrame = null;
    private ImageDisplay sliceDepthmapImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private ImageDisplay.Legend sliceDepthmapImageDisplayLegend;
    private static final String LEGEND_SLICES = "Depth image";
    private boolean showDepthmap = getBoolean("showDepthmap", false);; // Display the depth map

    public RosbagVOFlow(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        VOGTReader = new RosbagVOGTReader(chip);
        chain.add(VOGTReader);
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
            drawSlices(depth_image_reverse);
        }
        
//        Se3Info se3Info = se3InfoList.get(se3InfoList.size() - 1);
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
            current_pose_se3 = VOGTReader.getCurrentPoseSe3();  
            
            Timestamp current_depth_ts = VOGTReader.getCurrentDepth_ts();            
            Timestamp current_pose_ts = VOGTReader.getCurrentPose_ts();
            
            double fx = 226.3802;
            double fy = 226.1500;
            double cx = 173.6471;
            double cy = 133.7327;
            
            DoubleMatrix offsetPixel = new DoubleMatrix();
            if (depth_image != null && current_pose_se3 != null) {

                double Z = depth_image[(ein).x + (chip.getSizeY() - (ein.y) - 1) * chip.getSizeX()];
                double X = ((ein.x) * Z - cx)/fx;
                double Y = ((ein.y) * Z - cy)/fy;
                
                DoubleMatrix pose2pixelJaccobi = new DoubleMatrix(new double[][]{
                    {fx/Z, 0, -fx*X/Z, -fx*X*Y/(Z*Z), fx + fx*X*X/(Z*Z), -fx*Y/Z},
                    {0, fy/Z, -fy*Y/Z,  -fy - fx*Y*Y/(Z*Z), fy*X*Y/(Z*Z), fy*X/Z}});
                
                offsetPixel = pose2pixelJaccobi.mmul(current_pose_se3);
                vx = (float) offsetPixel.get(0);
                vy = (float) offsetPixel.get(1);
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
    
    private void drawSlices(float[] depth) {
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
}
