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
import java.util.ArrayList;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.eventio.ros.RosbagVOGTReader;
import net.sf.jaer.eventio.ros.RosbagVOGTReader.Se3Info;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
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
//    private long firstAbsoluteTs;
//    private boolean firstTimestampWasRead;
    private ArrayList<Se3Info> se3InfoList;

    public RosbagVOFlow(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        VOGTReader = new RosbagVOGTReader(chip);
        chain.add(VOGTReader);
        setEnclosedFilterChain(chain);
        se3InfoList = VOGTReader.getSe3InfoList(); 
        resetFilter();
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter(); //To change body of generated methods, choose Tools | Templates.
        if (se3InfoList != null) {
            se3InfoList.clear();
        }
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
        
//        Se3Info se3Info = se3InfoList.get(se3InfoList.size() - 1);
//        long current_pose_ts = se3Info.se3_ts.getTime()*1000+(long)(se3Info.se3_ts.getNanos()/1000);
//        long current_pose_relative_ts = current_pose_ts - firstAbsoluteTs;
        if (se3InfoList.size() >= 1) {
            for(Se3Info se3Info: se3InfoList) {
                se3Info = se3InfoList.get(se3InfoList.size() - 1);
                long current_pose_ts = se3Info.se3_ts.getTime()*1000+(long)(se3Info.se3_ts.getNanos()/1000);
//                long current_pose_relative_ts = current_pose_ts - firstAbsoluteTs;
            }
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
            if (isInvalidAddress(0)) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }

            
            int current_event_ts = ein.timestamp;
            
//            if (current_pose_relative_ts - current_event_ts >= 0) {
//                log.info("Current pose timestamp is: "+ current_pose_relative_ts);
//            }
            
        }

        return in;
    }
    
}
