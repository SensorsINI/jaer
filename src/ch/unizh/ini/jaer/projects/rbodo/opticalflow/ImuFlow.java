package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import ch.unizh.ini.jaer.projects.davis.calibration.SingleCameraCalibration;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
import net.sf.jaer.eventprocessing.FilterChain;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;
import static org.bytedeco.javacpp.opencv_imgproc.undistortPoints;

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
public class ImuFlow extends AbstractMotionFlowIMU {

    private SingleCameraCalibration singleCameraCalibration = null;

    public ImuFlow(AEChip chip) {
        super(chip);
        numInputTypes = 2;
        FilterChain chain = new FilterChain(chip);
        singleCameraCalibration = new SingleCameraCalibration(chip);
        singleCameraCalibration.setRealtimePatternDetectionEnabled(false);
        getSupport().addPropertyChangeListener(SingleCameraCalibration.EVENT_NEW_CALIBRATION, this);
        chain.add(singleCameraCalibration);
        setEnclosedFilterChain(chain);
        resetFilter();
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        getEnclosedFilterChain().filterPacket(in);

        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((ApsDvsEventPacket) in).inputIterator();
        }
        float fx = 1, fy = 1, cx = 0, cy = 0;

        while (i.hasNext()) {
            Object ein = i.next();
            ApsDvsEvent apsDvsEvent = (ApsDvsEvent) ein;
            if (apsDvsEvent.isApsData()) {
                continue;
            }
            extractEventInfo(ein);
            imuFlowEstimator.calculateImuFlow((ApsDvsEvent) inItr.next());

            if (apsDvsEvent.isImuSample()) {
                continue;
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
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                setGroundTruth();
            }
            if (accuracyTests()) {
                continue;
            }
            //exportFlowToMatlab(2500000,2600000); // for IMU_APS_translSin
            //exportFlowToMatlab(1360000,1430000); // for IMU_APS_rotDisk
            //exportFlowToMatlab(295500000,296500000); // for IMU_APS_translBoxes
            writeOutputEvent();
            if (measureAccuracy) {
                getMotionFlowStatistics().update(vx, vy, v, vxGT, vyGT, vGT);
            }
        }
        if (singleCameraCalibration.isCalibrated()) {
            DoubleIndexer k = singleCameraCalibration.getCameraMatrix().createIndexer();
            fx = (float) k.get(0, 0);
            fy = (float) k.get(1, 1);
            cx = (float) k.get(0, 2);
            cy = (float) k.get(1, 2);
            int nout=dirPacket.getSize();
            
            Mat inEvents = new Mat(nout, 2, opencv_core.CV_32FC2);
            for(Object o:dirPacket){
                ApsDvsMotionOrientationEvent e=(ApsDvsMotionOrientationEvent)o;
                
            }
////            evM.put(new Scalar(x, y));
////                evM.put(new Scalar(vx,vy));
////            String sev = printMatF(evM);
//            Mat evMT = new Mat();
////            undistortPoints(evM, evMT, singleCameraCalibration.getCameraMatrix(), singleCameraCalibration.getDistortionCoefs());
//            Mat evMT2 = new Mat();
//            sev = printMatF(evMT2);
//            FloatIndexer fidx = evMT.createIndexer();
//            float newx = fidx.get(0);
//            float newy = fidx.get(1);
//            newx = fx * newx + cx;
//            newy = fy * newy + cy;
//            String sevT = printMatF(evMT);
//                log.info(sevT);
        }
        getMotionFlowStatistics().updatePacket(countIn, countOut);
        return isShowRawInputEnabled() ? in : dirPacket;
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
