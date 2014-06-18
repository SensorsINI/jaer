
package net.sf.jaer.eventprocessing.label;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.DvsOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.MotionOrientationEventInterface;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;

/** Outputs local motion events derived from time of flight of 
 * orientation events from DVS sensors. 
 * Output cells type has values 0-7,
 * 0 being upward motion, increasing by 45 deg CCW 
 * to 7 being motion up and to right.
 * @see AbstractDirectionSelectiveFilter
 * @author tobi */
@Description("Local motion by time-of-travel of orientation events for DVS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DvsDirectionSelectiveFilter extends AbstractDirectionSelectiveFilter {
    
    private static final double SQRT2 = Math.sqrt(2);
    private int n=0;
    public DvsDirectionSelectiveFilter(AEChip chip) {
        super(chip);
        oriFilter = new DvsOrientationFilter(chip);
        oriFilter.setAnnotationEnabled(false);
        oriFilter.setShowRawInputEnabled(false); // so that the orientation filter returns orientation events, not the input packet (tobi)
        setEnclosedFilter(oriFilter);
    }

//TODO: BjoernBeyer: The speed is not correct! The UnitDirs in MotionOrientationEvent.unitDirs
//      are not really UnitVectors (the diagonal vectors are not) This means that the speed for the 
//      diagonal events is actually higher than computed here, as the euclidian distance the edge must have travelled
//      is larger than the search distance 's' (by a factor of sqrt2).   
//
//      This also means, that the diagonals are underrepresented in general, as the 'dt' for diagonals will
//      be larger than for the horizontals and verticals, since the search distance is larger. So for the same
//      actual motion speed the reported dt for diagonal motion will be larger than for the other directions.
//       This means we should increase the search distance in the speed calculation by a factor of sqrt2 and 
//       also incearse the min/maxDtThreshold by the same factor for the diagonal orientations. 
//                        
//      Note however that the velocity currently is correct, as in the diagonal cases we are multiplying the 
//      too small speed with a too large unitvector, both are of by a factor of sqrt2. Hence the velocity is 
//      correct for all directions. If the speed calculation is fixed however the velocity needs to be adjusted as well!  
//      -------------------
//      I hacked arround the dt Threshold Problem. Infact the diagonal directions where pretty much underrepresented.
//      From rough estimations the measured speed for the same PHYSICAL speed was 30% lower for diagonal motion (rotated with the rotation filter)
//      than for horizontal or vertical motion. With the Threshold problem removed the speeds are 
//      acutally the same, as the annotated speed is based on the velocity, not the speed of the event.
    @Override synchronized public EventPacket filterPacket(EventPacket in) {
        // we use two additional packets: oriPacket which holds the orientation events, and dirPacket that holds the dir vector events
        oriPacket = oriFilter.filterPacket(in);  // compute orientation events.
        if (dirPacket == null) {
            dirPacket = new EventPacket(DvsMotionOrientationEvent.class);
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
            OrientationEventInterface e = (OrientationEventInterface) ein;

            if(!e.isHasOrientation()){
                if(passAllEvents){
                    MotionOrientationEventInterface eout = (MotionOrientationEventInterface) outItr.nextOutput();
                    eout.copyFrom((DvsOrientationEvent)e);
                    eout.setHasDirection(false);
                }
                continue;
            }
            
            int  x        = ((e.getX() >>> subSampleShift) + getSearchDistance()); // x and y are offset inside our timestamp storage array to avoid array access violations
            int  y        = ((e.getY() >>> subSampleShift) + getSearchDistance()); // without the 'searchdistance' we could be at x==0 and search for orientations at x==-1, hence we need offset
            int  polValue = ((e.getPolarity() == PolarityEvent.Polarity.On) ? 0 : 4);
            byte ori      = e.getOrientation();
            byte type     = (byte) (ori + polValue); // type information here is mixture of input orientation and polarity, in order to match both characteristics
            int  ts       = e.getTimestamp();        // getString event x,y,type,timestamp of *this* event

            // update the map here - this is ok because we never refer to ourselves anyhow in computing motion
            lastTimesMap[x][y][type] = ts;
            
            // <editor-fold defaultstate="collapsed" desc="--COMMENT--">
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
            //
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
            // </editor-fold>
            int dist, dt, delay = 0;
            int helpMinDtThreshold, helpMaxDtThreshold;
            float speed = 0;
            byte motionDir = ori; // the quantized direction of detected motion
            MotionOrientationEventInterface.Dir d;

            if (!useAvgDtEnabled) {
                // <editor-fold defaultstate="collapsed" desc="--Motion direction using MIN dt--">
                int mindt1 = Integer.MAX_VALUE, mindt2 = Integer.MAX_VALUE;
                int dist1 = 1, dist2 = 1;
                
                // now iterate over search distance to find minimum delay 
                // between this input orientation event and previous 
                // orientiation input events in offset direction
                for (int s = 1; s <= searchDistance; s++) {
                    d = MotionOrientationEventInterface.unitDirs[ori];
                    // dt is the time between this event and the previous event of the same type
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type];
                    if (dt < mindt1) {
                        dist1 = s; // dist1 is the distance we found min dt
                        mindt1 = dt;
                    }
                    d = MotionOrientationEventInterface.unitDirs[ori + 4];
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

                //TODO: SUPER HACKY... This is because of the sqrt2 error in the 
                // orientations. Look at comment in avgDt
                if(ori % 2 == 1){
                    helpMaxDtThreshold = (int) (maxDtThreshold * SQRT2);
                    helpMinDtThreshold = (int) (minDtThreshold * SQRT2);
                } else {
                    helpMaxDtThreshold = maxDtThreshold;
                    helpMinDtThreshold = minDtThreshold;
                }
                // if the time between this event and the most recent neighbor 
                // event lies not within the interval, dont write an output event           
                if (!(dt < helpMaxDtThreshold && dt > helpMinDtThreshold)) {
                    if(passAllEvents) {
                        MotionOrientationEventInterface eout = (MotionOrientationEventInterface) outItr.nextOutput();
                        eout.copyFrom((DvsOrientationEvent) ein);
                        eout.setHasDirection(false);
                    }
                    continue;
                }

                // This speed is PixelPerSecond, as the distance is in
                // pixel and dt is in microseconds (1e-6 seconds)
                speed = 1e6f * (float) dist / dt; 
                delay = dt; // the smallest delay found in the 'winning' search direction
                // </editor-fold>
            } else {
                // <editor-fold defaultstate="collapsed" desc="--Motion direction using AVG dt--">
                float speed1 = 0, speed2 = 0; // summed speeds
                int delay1 = 0,delay2 = 0;
                int n1 = 0, n2 = 0; // counts of passing matches, each direction

                // use average time to previous ori events iterate over 
                // search distance to find average delay between this input 
                // orientation event and previous orientiation input events 
                // in offset direction. Only count event if it falls in 
                // acceptable delay bounds

                for (int s = 1; s <= searchDistance; s++) {
                    //TODO: SUPER HACKY... This is because of the sqrt2 error in the 
                    // orientations. Look at comment in avgDt
                    if(ori % 2 == 1){
                        helpMaxDtThreshold = (int) (maxDtThreshold * SQRT2);
                        helpMinDtThreshold = (int) (minDtThreshold * SQRT2);
                    } else {
                        helpMaxDtThreshold = maxDtThreshold;
                        helpMinDtThreshold = minDtThreshold;
                    }
                    
                    d = MotionOrientationEventInterface.unitDirs[ori];
                    
                    if((x + s * d.x)<0 || (y + s * d.y)<0){
                        System.out.println((x + s * d.x)+ " -- "+(y + s * d.y));
                    }
                        
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type]; // this is time between this event and previous
                    if (dt < helpMaxDtThreshold && dt > helpMinDtThreshold) {
                        n1++;
                        speed1 += (float) s / dt; // sum speed in pixels/us
                        delay1 += dt;
                    }

                    d = MotionOrientationEventInterface.unitDirs[ori + 4];
                    dt = ts - lastTimesMap[x + s * d.x][y + s * d.y][type];
                    if (dt < helpMaxDtThreshold && dt > helpMinDtThreshold) {
                        n2++;
                        speed2 += (float) s / dt;
                        delay2 += dt;
                    }
                }

                if (n1 == 0 && n2 == 0) {
                    if(passAllEvents) {
                        MotionOrientationEventInterface eout = (MotionOrientationEventInterface) outItr.nextOutput();
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
                    delay     = delay1 / n1;
                    motionDir = ori;
                } else if (n2 > n1 || (n1 == n2 && speed2 < speed1)) {
                    speed     = speed2 / n2;
                    delay     = delay2 / n2;
                    motionDir = (byte) (ori + 4);
                } 

                //distance can only be half search distance, as we use average dt and speed.
                // Note that dist is in int, so for a normal searchDistance of '3' the
                // 'dist' saved will be 1.
                dist  = searchDistance / 2; 
                speed = 1e6f * speed; //now speed is in PPS
                // </editor-fold>
            }
            
            // don't output event if speed too high compared to average
            avgSpeed = (1 - speedMixingFactor) * avgSpeed + speedMixingFactor * speed;
            if (speedControlEnabled && speed > avgSpeed * excessSpeedRejectFactor) {
                if(passAllEvents) {
                    MotionOrientationEventInterface eout = (MotionOrientationEventInterface) outItr.nextOutput();
                    eout.copyFrom((DvsOrientationEvent) ein);
                    eout.setHasDirection(false);
                }
                continue;
            } 

            //Now the event has passed all tests and properties are computed.
            // write the event to the OutputStream.
            MotionOrientationEventInterface eout = (MotionOrientationEventInterface) outItr.nextOutput();
            eout.copyFrom((DvsOrientationEvent) ein);
            eout.setHasDirection(true);
            eout.setDirection(motionDir);
            eout.setDelay(delay);
            eout.setDistance((byte) dist); // the pixel distance to the temporally closest event of the same type
            eout.setSpeed(speed);
            eout.setDir(MotionOrientationEventInterface.unitDirs[motionDir]);
            eout.setVelocity(-speed * eout.getDir().x, -speed * eout.getDir().y); // these have minus sign because dir vector points towards direction that previous event occurred
            motionVectors.addEvent(eout);
        }
        return isShowRawInputEnabled() ? in : dirPacket; 
    }
}
