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
package net.sf.jaer.eventio.ros;

import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.Float64Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;

import com.jogamp.opengl.GLAutoDrawable;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.FrameAnnotater;

import org.opencv.core.Point3;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;

import org.jblas.DoubleMatrix;
import org.jblas.MatrixFunctions;
import org.jblas.Solve;



/** Rosbag reader for MSVEC VO Groundtruth which is packed in rosbag format. 
 *  Dataset paper: 
 *  Zhu, A. Z., Thakur, D., Ozaslan, T., Pfrommer, B., Kumar, V., & Daniilidis, K. (2018). 
 *  The Multi Vehicle Stereo Event Camera Dataset: An Event Camera Dataset for 3D Perception. arXiv preprint arXiv:1801.10202.
 * 
 * @author minliu
 */
@Description("Converts MSVEC VO GT to OF GT.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RosbagVOGTReader extends RosbagMessageDisplayer implements FrameAnnotater {

    private DoubleMatrix last_rotation = DoubleMatrix.eye(3);
    private DoubleMatrix current_rotation = DoubleMatrix.eye(3);

    public RosbagVOGTReader(AEChip chip) {
        super(chip);
        ArrayList<String> topics = new ArrayList();
        topics.add("/davis/left/pose");
        addTopics(topics);
    }

    @Override
    protected void parseMessage(RosbagFileInputStream.MessageWithIndex message) {
        String pkg = message.messageType.getPackage();
        try {
            // Extract position information.
            double x_pos = message.messageType.<MessageType>getField("pose").<MessageType>getField("position")
                    .<Float64Type>getField("x").getValue();
            double y_pos = message.messageType.<MessageType>getField("pose").<MessageType>getField("position")
                    .<Float64Type>getField("y").getValue();
            double z_pos = message.messageType.<MessageType>getField("pose").<MessageType>getField("position")
                    .<Float64Type>getField("z").getValue();  
            // Extract orientation information
            double x_quat = message.messageType.<MessageType>getField("pose").<MessageType>getField("orientation")
                    .<Float64Type>getField("x").getValue();
            double y_quat = message.messageType.<MessageType>getField("pose").<MessageType>getField("orientation")
                    .<Float64Type>getField("y").getValue();
            double z_quat = message.messageType.<MessageType>getField("pose").<MessageType>getField("orientation")
                    .<Float64Type>getField("z").getValue();  
            double w_quat = message.messageType.<MessageType>getField("pose").<MessageType>getField("orientation")
                    .<Float64Type>getField("w").getValue();   
            
            Point3 position = new Point3(x_pos, y_pos, z_pos);
            
            // Construct rotation matrix using Quaternion
            Rotation rota = new Rotation(w_quat, x_quat, y_quat, z_quat, true);
            Quaternion quat = new Quaternion(w_quat, x_quat, y_quat, z_quat);
            
            // Convert it to a matrix type that could use log function from jblas library.
            last_rotation = current_rotation.dup();
            current_rotation = new DoubleMatrix(rota.getMatrix());
            log.info("\nPose: position: " + position + "\n" + "Pose: orientation: " + current_rotation 
                    + "\n" + "Pose: quaternion: " + quat);
           
            DoubleMatrix T = last_rotation.transpose().mul(current_rotation);
            
            Mat src = new Mat(3, 3, CvType.CV_64FC1);            
            src.put(0, 0, T.data);
            Mat dst = new Mat();
            Mat jocobian = new Mat();
            Calib3d.Rodrigues(src, dst, jocobian);
            log.info("The Rodrigues vector is: " + dst.dump());
            
            /* 
            Following code is just for testing the matrix exp function in jblas.                        
            Test rotation vector is:
            {-2.100418,-2.167796,0.273330}
            
            The result rotation matrix should be:
            {0, -0.273330, -2.167796}, 
            {0.273330, 0, 2.100418}, 
            {2.16779, -2.100418, 0}}

            */
            DoubleMatrix lie_group_test = MatrixFunctions.expm(new DoubleMatrix(new double[][]{{0, -0.273330, -2.167796}, 
                                                                                            {0.273330, 0, 2.100418}, 
                                                                                            {2.16779, -2.100418, 0}}
            ));
            log.info("The skew symmetric matrix is:" + lie_group_test);
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(SlasherRosbagDisplay.class.getName()).log(Level.SEVERE, null, ex);
        }    
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }    
    
}
