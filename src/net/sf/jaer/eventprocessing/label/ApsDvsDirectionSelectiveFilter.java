
package net.sf.jaer.eventprocessing.label;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.orientation.DvsOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;

/** Outputs local motion events derived from time of flight of orientation 
 * events from DVS sensors. 
 * Output cells type has values 0-7, 0 being upward motion, increasing by 
 * 45 deg CCW to 7 being motion up and to right.
 * @see AbstractDirectionSelectiveFilter
 * @author tobi */
@Description("Local motion optical flow by time-of-travel of orientation events for APSDVS (DAVIS) sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApsDvsDirectionSelectiveFilter extends AbstractDirectionSelectiveFilter {

    public ApsDvsDirectionSelectiveFilter(AEChip chip) {
        super(chip);
        oriFilter = new ApsDvsOrientationFilter(chip);
        oriFilter.setAnnotationEnabled(false);
        oriFilter.setShowRawInputEnabled(false); // so that the orientation filter returns the orientation events, not the input packet (tobi)
        setEnclosedFilter(oriFilter);
    }

    @Override synchronized public EventPacket filterPacket(EventPacket in) {
        // we use two additional packets: oriPacket which holds the orientation events, and dirPacket that holds the dir vector events
        oriPacket = oriFilter.filterPacket(in);  // compute orientation events.
        if (dirPacket == null) {
            dirPacket = new EventPacket(ApsDvsMotionOrientationEvent.class);
        }
        checkMap();

        // if the input is ON/OFF type, then motion detection doesn't make much 
        // sense because you are likely to detect the nearest event from along 
        // the same edge, not from where the edge moved from. Therefore, this 
        // filter only really makes sense to use with an oriented input.
        //
        // When the input is oriented (e.g. the events have an orientation type) 
        // then motion estimation consists of just checking in a direction 
        // *perpindicular to the edge* for the nearest event of the same input 
        // orientation type. For each event write out an event of type according 
        // to the direction of the most recent previous event in neighbors.
        // Only write the event if the delta time is within two-sided threshold.
        OutputEventIterator outItr = dirPacket.outputIterator(); // this initializes the output iterator of dirPacket
        for (Object ein : oriPacket) {
            if(!(ein instanceof ApsDvsOrientationEvent)) continue;
            OrientationEventInterface e = (OrientationEventInterface) ein;

            if(!e.isHasOrientation()){
                if(passAllEvents){
                    ApsDvsMotionOrientationEvent eout = (ApsDvsMotionOrientationEvent) outItr.nextOutput();
                    eout.copyFrom((DvsOrientationEvent) ein);
                    eout.hasDirection = false;
                }
                continue;
            }
            
            int  x        = ((e.getX() >>> subSampleShift) + getSearchDistance()); // x and y are offset inside our timestamp storage array to avoid array access violations
            int  y        = ((e.getY() >>> subSampleShift) + getSearchDistance()); // without the 'P' we could be at x==0 and search for orientations at x==-1, hence we need offset
            int  polValue = ((e.getPolarity() == PolarityEvent.Polarity.On) ? 0 : 4);
            byte ori      = e.getOrientation();
            byte type     = (byte) (ori + polValue); // type information here is mixture of input orientation and polarity, in order to match both characteristics
            int  ts       = e.getTimestamp();        // getString event x,y,type,timestamp of *this* event

            // update the map here - this is ok because we never refer to ourselves anyhow in computing motion
            lastTimesMap[x][y][type] = ts;

            // for each output cell type (which codes a direction of motion), 
            // find the dt between the orientation cell type perdindicular
            // to this direction in this pixel and in the neighborhood - but 
            // only find the dt in that single direction.
            // Also, only find time to events of the same *polarity* and 
            // *orientation*. Otherwise we will falsely match opposite polarity
            // orientation events which arise from two sides of edges.
            
            // Find the time of the most recent event in a neighborhood of the 
            // same type as the present input event but only in the two 
            // directions perpindiclar to this orientation. Each of these 
            // codes for motion but in opposite directions. 
            // Ori input has type 0 for horizontal (red), 1 for 45  deg (blue), 
            //                    2 for vertical  (cyan), 3 for 135 deg (green)
            // for each input type, check in the perpindicular directions, 
            // ie, (dir+2)%numInputCellTypes and (dir+4)%numInputCellTypes
            // this computation only makes sense for ori type input
            // neighbors are of same type they are in direction given by 
            // unitDirs in lastTimesMap.
            
            // The input type tells us which offset to use, e.g. for type 0 
            // (0 deg horiz ori), we offset first in neg vert direction, 
            // then in positive vert direction, thus the unitDirs used here 
            // *depend* on orientation assignments in AbstractDirectionSelectiveFilter
            int dist, dt, delay;
            float speed = 0;
            byte motionDir = ori; // the quantized direction of detected motion
            DvsMotionOrientationEvent.Dir d;
            // d=unitDirs[ori] is perpendicular to orientation 'ori', because
            // of the order of orientation and Dir. The first four 
            // directions in Dir are perpendicular to the four orientations.
            // The next four Dir's are perpendicular to the first four.
            // Hence unitDirs[ori+4] is perpendicular to unitDirs[ori]

            //Compute properties of events and decide whether or not to 
            // output an event at all, based on either average delay or 
            // minimum delay.
            // If an event does not pass tests for being a motion event, we
            // use 'continue' to NOT write the event at the end of the loop.
            if (!useAvgDtEnabled) {
                // <editor-fold defaultstate="collapsed" desc="--Motion direction using MIN dt--">
                int mindt1 = Integer.MAX_VALUE, mindt2 = Integer.MAX_VALUE;
                int dist1 = 1, dist2 = 1;

                // now iterate over search distance to find minimum delay 
                // between this input orientation event and previous 
                // orientiation input events in offset direction
                for (int s = 1; s <= searchDistance; s++) {
                    d = DvsMotionOrientationEvent.unitDirs[ori];
                    // dt is the time between this event and the previous event of the same type
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type];
                    if (dt < mindt1) {
                        dist1 = s; // dist1 is the distance we found min dt
                        mindt1 = dt;
                    }
                    d = DvsMotionOrientationEvent.unitDirs[ori + 4];
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type];
                    if (dt < mindt2) {
                        dist2 = s;
                        mindt2 = dt;
                    }
                }
                //We have searched in two directions for the minimum delay 
                // between equal types. We we check which *search direction*
                // has the smallest delay. If mindt1 < mindt2 this means
                // the minimum delay in this search direction is smaller
                if (mindt1 < mindt2) {
                    dt        = mindt1;
                    motionDir = ori; //perpendicular to ori and (down, right, downright or upright)
                    dist      = dist1;
                } else {
                    dt        = mindt2;
                    motionDir = (byte) (ori + 4); //perpendicular to ori and (up,left,upleft, downleft)
                    dist      = dist2;
                }

                // if the time between this event and the most recent neighbor 
                // event lies not within the interval, dont write an output event
                if (!(dt < maxDtThreshold && dt > minDtThreshold)) {
                    if(passAllEvents) {
                        DvsMotionOrientationEvent eout = (DvsMotionOrientationEvent) outItr.nextOutput();
                        eout.copyFrom((DvsOrientationEvent) ein);
                        eout.setHasDirection(false);
                    }
                    continue;
                }

                // This speed is PixelPerSecond, as the distance is in
                // pixel and dt is in microseconds (1e-6 seconds)
                speed = 1e6f * (float) dist / dt; 
                delay = dt; // the smallest delay found in the 'winning' search direction

                avgSpeed = (1 - speedMixingFactor) * avgSpeed + speedMixingFactor * speed;

                if (speedControlEnabled && speed > avgSpeed * excessSpeedRejectFactor) {
                    if(passAllEvents) {
                        DvsMotionOrientationEvent eout = (DvsMotionOrientationEvent) outItr.nextOutput();
                        eout.copyFrom((DvsOrientationEvent) ein);
                        eout.setHasDirection(false);
                    }
                    continue;
                } // don't store event if speed too high compared to average

                // </editor-fold>
            } else {
                // <editor-fold defaultstate="collapsed" desc="--Motion direction using AVG dt--">
                float speed1 = 0, speed2 = 0; // summed speeds
                int n1 = 0, n2 = 0; // counts of passing matches, each direction

                // use average time to previous ori events iterate over 
                // search distance to find average delay between this input 
                // orientation event and previous orientiation input events 
                // in offset direction. Only count event if it falls in 
                // acceptable delay bounds

                for (int s = 1; s <= searchDistance; s++) {
                    d = DvsMotionOrientationEvent.unitDirs[ori];
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type]; // this is time between this event and previous
                    if (dt < maxDtThreshold && dt > minDtThreshold) {
                        n1++;
                        speed1 += (float) s / dt; // sum speed in pixels/us
                    }

                    d = DvsMotionOrientationEvent.unitDirs[ori + 4];
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type];
                    if (dt < maxDtThreshold && dt > minDtThreshold) {
                        n2++;
                        speed2 += (float) s / dt;
                    }
                }

                if (n1 == 0 && n2 == 0) {
                    if(passAllEvents) {
                        DvsMotionOrientationEvent eout = (DvsMotionOrientationEvent) outItr.nextOutput();
                        eout.copyFrom((DvsOrientationEvent) ein);
                        eout.setHasDirection(false);
                    }
                    continue; // no pass, i.e. no event to write
                }

                //The motion direction assigned is the one with more evidence,
                // hence where the number of found events is larger. If both
                // directions produce equal number of events we assign the
                // direction with slower speed as the motion direction.
                // The very very rare case of equal number of events and equal speed
                // is asigned to the 'positive' search direction.
                if (n1 > n2 || (n1 == n2 && speed1 <= speed2)) {
                    speed     = speed1 / n1;
                    motionDir = ori;
                } else if (n2 > n1 || (n1 == n2 && speed2 < speed1)) {
                    speed     = speed2 / n2;
                    motionDir = (byte) (ori + 4);
                } 

                dist  = searchDistance / 2; //distance can only be half search distance, as we use average dt and speed.
                // We want delay to be in us, so we divide by speed while it is
                // still in its pixel/us form. Thereafter we convert speed to
                // pixel/s
                // Also it is more accurate to calculate the distance in float here
                // as we want to compute a derived quantity, so we should round 
                // as late as possible. When searchDistance is '3', then dist =1
                // but we calculate with 1.5 here.
                delay = (int) ((searchDistance/2f) / speed); // hence delay is in seconds
                speed = 1e6f * speed; //now speed is in PPS
                
                avgSpeed = (1 - speedMixingFactor) * avgSpeed + speedMixingFactor * speed;
                if (speedControlEnabled && speed > avgSpeed * excessSpeedRejectFactor) {
                    if(passAllEvents) {
                        DvsMotionOrientationEvent eout = (DvsMotionOrientationEvent) outItr.nextOutput();
                        eout.copyFrom((DvsOrientationEvent) ein);
                        eout.setHasDirection(false);
                    }
                    continue;
                } // don't output event if speed too high compared to average

                // </editor-fold>
            }

            //Now the event has passed all tests and properties are computed.
            // write the event to the OutputStream.
            ApsDvsMotionOrientationEvent eout = (ApsDvsMotionOrientationEvent) outItr.nextOutput();
            eout.copyFrom((ApsDvsOrientationEvent) ein);
            eout.direction    = motionDir;
            eout.hasDirection = true;
            eout.delay        = delay; 
            eout.distance     = (byte) dist; // the pixel distance to the temporally closest event of the same type
            eout.speed        = speed;
            eout.dir          = ApsDvsMotionOrientationEvent.unitDirs[motionDir];
            eout.velocity.x   = -speed * eout.dir.x; // these have minus sign because dir vector points towards direction that previous event occurred
            eout.velocity.y   = -speed * eout.dir.y;
            motionVectors.addEvent(eout);
        }
        return isShowRawInputEnabled() ? in : dirPacket; 
    }
}
