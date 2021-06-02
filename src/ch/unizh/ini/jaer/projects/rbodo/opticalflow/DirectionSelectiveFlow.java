package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.label.AbstractOrientationFilter;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;

/**
 * Outputs local motion events derived from time of flight of orientation events
 * from DVS sensors. Output cells type has values 0-7, 0 being upward motion,
 * increasing by 45 deg CCW to 7 being motion up and to right.
 * <p>
 * This class was originally DirectionSelectiveFilter (ca 2007 and later
 * DvsDirectionSelectiveFilter (ca 2014) in the package
 * net.sf.jaer.eventprocessing.label. These original classes have been deleted
 * and replaced by this class. The original source code is available by version
 * control reversion of the project.
 *
 * @author tobi
 */
@Description("Local motion optical flow by time-of-travel of orientation events for APSDVS (DAVIS) sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DirectionSelectiveFlow extends AbstractMotionFlow {

    // min 100us to filter noise or multiple spikes.
    private int minDtThreshold = getInt("minDtThreshold", 100);

    private final AbstractOrientationFilter oriFilter;
    private byte ori;
    private int polValue;

    public DirectionSelectiveFlow(AEChip chip) {
        super(chip);
        numInputTypes = 8; // 4 orientations * 2 polarities
        oriFilter = new SimpleOrientationFilter(chip);
        oriFilter.setAnnotationEnabled(false);
        oriFilter.setShowRawInputEnabled(false); // So that the orientation filter 
        // returns the orientation events, 
        // not the input packet.
        setEnclosedFilter(oriFilter);
        resetFilter();
        setPropertyTooltip("Dir. Selective", "minDtThreshold", "min delta time (us) "
                + "for past events allowed for selecting a particular direction. "
                + "E.g. 100 us filter out speeds higher than 10 cm/s (for a pixelWidth of 10 um)");
    }

    @Override
    synchronized public boolean extractEventInfo(Object ein) {
        if (!super.extractEventInfo(ein)) {
            return false;
        }
        if (!(ein instanceof OrientationEventInterface)) {
            return false;
        }
        polValue = e.getPolarity() == PolarityEvent.Polarity.On ? 0 : 4;
        ori = ((OrientationEventInterface) ein).getOrientation();
        // Type information here is mixture of input orientation and polarity, 
        // in order to match both characteristics.
        type = (byte) (ori + polValue);
        return true;
    }

    synchronized void writeOutputEvent(byte motionDir, Object ein) {
        super.processGoodEvent();
        eout.copyFrom((ApsDvsOrientationEvent) ein);
        eout.direction = motionDir;
        eout.dir = ApsDvsMotionOrientationEvent.unitDirs[motionDir];
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        in = oriFilter.filterPacket(in);  // compute orientation events.
        DvsMotionOrientationEvent.Dir d1, d2;
        int dt1, dt2, n1, n2, s;
        float speed1, speed2;
        byte motionDir;
        setupFilter(in);

        // <editor-fold defaultstate="collapsed" desc="Comment">
        /**
         * If the input is ON/OFF type, then motion detection doesn't make much
         * sense because you are likely to detect the nearest event from along
         * the same edge, not from where the edge moved from. Therefore, this
         * filter only really makes sense to use with an oriented input.
         *
         * When the input is oriented (e.g. the events have an orientation type)
         * then motion estimation consists of just checking in a direction
         * *perpindicular to the edge* for the nearest event of the same input
         * orientation type. For each event write out an event of type according
         * to the direction of the most recent previous event in neighbors. Only
         * write the event if the delta time is within two-sided threshold.
         */
        // </editor-fold>
         // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        while (i.hasNext()) {
            Object o=i.next();
             if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
             if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent)o).isApsData()) {
                continue;
            }
            PolarityEvent ein = (PolarityEvent)o;
           
            if ( measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                if(imuFlowEstimator.calculateImuFlow(o)) continue;
            }
            // block ENDS

            if (!extractEventInfo(ein)) {
                continue;
            }
            if (isInvalidAddress(searchDistance)) {
                continue;
            }
            if (isInvalidTimestamp()) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }
            countIn++;

            // <editor-fold defaultstate="collapsed" desc="Comment">
            /**
             * For each output cell type (which codes a direction of motion),
             * find the dt between the orientation cell type perdindicular to
             * this direction in this pixel and in the neighborhood - but only
             * find the dt in that single direction. Also, only find time to
             * events of the same *polarity* and *orientation*. Otherwise we
             * will falsely match opposite polarity orientation events which
             * arise from two sides of edges.
             *
             * Find the time of the most recent event in a neighborhood of the
             * same type as the present input event but only in the two
             * directions perpindiclar to this orientation. Each of these codes
             * for motion but in opposite directions. Ori input has type 0 for
             * horizontal (red), 1 for 45 deg (blue), 2 for vertical (cyan), 3
             * for 135 deg (green) for each input type, check in the
             * perpindicular directions, ie, (dir+2)%numInputCellTypes and
             * (dir+4)%numInputCellTypes. This computation only makes sense for
             * ori type input. Neighbors are of same type they are in direction
             * given by unitDirs in lastTimesMap.
             *
             * The input type tells us which offset to use, e.g. for type 0 (0
             * deg horiz ori), we offset first in neg vert direction, then in
             * positive vert direction, thus the unitDirs used here *depend* on
             * orientation assignments in AbstractDirectionSelectiveFilter.
             *
             * d = unitDirs[ori] is perpendicular to orientation 'ori', because
             * of the order of orientation and Dir. The first four directions in
             * Dir are perpendicular to the four orientations. The next four
             * Dir's are perpendicular to the first four. Hence unitDirs[ori+4]
             * is perpendicular to unitDirs[ori].
             *
             * Compute properties of events and decide whether or not to output
             * an event at all, based on average delay. If an event does not
             * pass tests for being a motion event, we use 'continue' to NOT
             * write the event at the end of the loop.
             */
            // </editor-fold>
            v = 0;
            motionDir = ori; // the quantized direction of detected motion

            speed1 = 0;
            speed2 = 0; // summed speeds
            n1 = 0;
            n2 = 0; // counts of passing matches, each direction

            /**
             * Use average time to previous orientation- events. Iterate over
             * search distance to find average delay between this input
             * orientation event and previous orientation input events in offset
             * direction. Only count event if it falls in acceptable delay
             * bounds.
             */
            for (s = 1; s <= searchDistance; s++) {
                d1 = DvsMotionOrientationEvent.unitDirs[ori];
                d2 = DvsMotionOrientationEvent.unitDirs[ori + 4];
                // this is time between this event and previous:
                dt1 = ts - lastTimesMap[x + s * d1.x][y + s * d1.y][type];
                dt2 = ts - lastTimesMap[x + s * d2.x][y + s * d2.y][type];
                if (dt1 < maxDtThreshold && dt1 > minDtThreshold) {
                    n1++;
                    speed1 += (float) s / dt1; // sum speed in pixels/us
                }
                if (dt2 < maxDtThreshold && dt2 > minDtThreshold) {
                    n2++;
                    speed2 += (float) s / dt2;
                }
            }
            /**
             * The motion direction assigned is the one with more evidence,
             * hence where the number of found events is larger. If both
             * directions produce equal number of events we assign the direction
             * with slower speed as the motion direction. The very very rare
             * case of equal number of events and equal speed is asigned to the
             * 'positive' search direction.
             */
            if (n1 == 0 && n2 == 0) {
                v = 0;
            } else if (n1 > n2 || (n1 == n2 && speed1 <= speed2)) {
                v = speed1 / n1;
                motionDir = ori;
            } else if (n2 > n1 || (n1 == n2 && speed2 < speed1)) {
                v = speed2 / n2;
                motionDir = (byte) (ori + 4);
            }
            v *= 1e6; // Convert to pixel/s

            // These have minus signs because dir vector points towards direction 
            // that previous event occurred.
            vx = -v * ApsDvsMotionOrientationEvent.unitDirs[motionDir].x;
            vy = -v * ApsDvsMotionOrientationEvent.unitDirs[motionDir].y;
            if (vx != 0 && vy != 0) {
                vx /= Math.sqrt(2);
                vy /= Math.sqrt(2);
            }

            if (accuracyTests()) {
                continue;
            }
            writeOutputEvent(motionDir, ein);
            if (measureAccuracy) {
                getMotionFlowStatistics().update(vx, vy, v, vxGT, vyGT, vGT);
            }
        }
        getMotionFlowStatistics().updatePacket(countIn, countOut, ts);
        return isDisplayRawInput() ? in : dirPacket;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MinDtThreshold--">
    public int getMinDtThreshold() {
        return this.minDtThreshold;
    }

    public void setMinDtThreshold(final int minDtThreshold) {
        this.minDtThreshold = minDtThreshold;
        putInt("minDtThreshold", minDtThreshold);
    }
    // </editor-fold>
}