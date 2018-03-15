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
import org.apache.commons.math3.complex.Quaternion;
import org.jblas.DoubleMatrix;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.jblas.MatrixFunctions;
import org.jblas.Solve;

/** Rosbag reader for MSVEC VO Groundtruth which is packed in rosbag format. 
 *  Dataset paper: 
 *  Zhu, A. Z., Thakur, D., Ozaslan, T., Pfrommer, B., Kumar, V., & Daniilidis, K. (2018). 
 *  The Multi Vehicle Stereo Event Camera Dataset: An Event Camera Dataset for 3D Perception. arXiv preprint arXiv:1801.10202.
 * 
 * @author minliu
 */
@Description("Shows Slasher robot car PWM signals for steering throttle and gearshift")
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

            DoubleMatrix inv_last = Solve.pinv(last_rotation);
            DoubleMatrix T = inv_last.mul(current_rotation);
            DoubleMatrix lie_group_T = MatrixFunctions.expm(T);
            log.info("The skew symmetric matrix is:" + lie_group_T);
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(SlasherRosbagDisplay.class.getName()).log(Level.SEVERE, null, ex);
        }    
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }    
    
}
