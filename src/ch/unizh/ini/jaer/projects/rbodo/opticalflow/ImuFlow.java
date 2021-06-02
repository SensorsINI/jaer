package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import org.bytedeco.javacpp.opencv_core.Mat;

/**
 * Draws individual optical flow vectors and computes global motion, rotation
 * and expansion, based upon motion estimate from IMU gyro sensors. This assumes
 * that the objects seen by the camera are stationary and the only motion is the
 * motion field caused by pure camera rotation.
 *
 * @author rbodo
 */
@Description("Class for amplitude and orientation of local motion optical flow using IMU gyro sensors.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@SuppressWarnings("deprecation") // tobi added for getFloatBuffer
public class ImuFlow extends AbstractMotionFlowIMU {

    public ImuFlow(AEChip chip) {
        super(chip);
        numInputTypes = 2;
        resetFilter();
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);

        // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
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
            if (imuFlowEstimator.calculateImuFlow(o)) {
                continue; // skip rest if IMU sample
            }
            if (isInvalidAddress(0)) {
                continue;
            }
            if (isInvalidTimestamp()) {
                continue;
            }
            if (xyFilter()) {
                continue;
            }
            countIn++;
            vx = imuFlowEstimator.getVx();
            vy = imuFlowEstimator.getVy();
            v = imuFlowEstimator.getV();
            if (accuracyTests()) {
                continue;
            }
            exportFlowToMatlab(318951235, 319061235);
            //exportFlowToMatlab(2500000,2600000); // for IMU_APS_translSin
            //exportFlowToMatlab(1360000,1430000); // for IMU_APS_rotDisk
            //exportFlowToMatlab(295500000,296500000); // for IMU_APS_translBoxes
            processGoodEvent();
        }

        getMotionFlowStatistics().updatePacket(countIn, countOut, ts);
        return isDisplayRawInput() ? in : dirPacket;
    }

    private String printMatF(Mat M) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.cols(); j++) {
                sb.append(String.format("%10.5f\t", M.getFloatBuffer().get(c)));
                c++;
            }
            sb.append("\n");
        }
        return sb.toString();
    }

}
