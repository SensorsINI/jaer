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

import com.github.swrirobotics.bags.reader.exceptions.InvalidDefinitionException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.exceptions.UnknownMessageException;
import com.github.swrirobotics.bags.reader.messages.serialization.Float32Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageCollection;
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
import geometry_msgs.Pose;
import geometry_msgs.PoseStamped;
import org.ros.internal.message.RawMessage;

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

    public RosbagVOGTReader(AEChip chip) {
        super(chip);
        ArrayList<String> topics = new ArrayList();
        topics.add("/davis/left/pose");
        addTopics(topics);
    }

    @Override
    protected void parseMessage(RosbagFileInputStream.MessageWithIndex message) {
        String pkg = message.messageType.getPackage();
        PoseStampedMessageType poseStamped = <PoseStampedMessageType>message.messageType;
        try {
            message.messageType.getField("0");
            float steeringPwm = message.messageType.<Float32Type>getField("steering").getValue();
            float throttlePwm = message.messageType.<Float32Type>getField("throttle").getValue();
            float gear_shiftPwm = message.messageType.<Float32Type>getField("gear_shift").getValue();
            log.info(String.format("PWM: steering: %8.2f\t throttle %8.2f\t gear: %8.2f", steeringPwm, throttlePwm, gear_shiftPwm));
            final float pwmCenter = 1500, pwmRange = 500; // us

        } catch (UninitializedFieldException ex) {
            Logger.getLogger(SlasherRosbagDisplay.class.getName()).log(Level.SEVERE, null, ex);
        }    
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }
    
    /**
     *
     */
    public class PoseStampedMessageType extends MessageType implements PoseStamped {

        public PoseStampedMessageType(String definition, MessageCollection msgCollection) throws InvalidDefinitionException, UnknownMessageException {
            super(definition, msgCollection);
        }

        @Override
        public std_msgs.Header getHeader() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void setHeader(std_msgs.Header header) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Pose getPose() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void setPose(Pose pose) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public RawMessage toRawMessage() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
    
}
