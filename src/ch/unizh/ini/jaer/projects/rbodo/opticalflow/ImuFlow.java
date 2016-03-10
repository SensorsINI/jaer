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
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
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

    private SingleCameraCalibration calibration = null;

    public ImuFlow(AEChip chip) {
        super(chip);
        numInputTypes = 2;
        FilterChain chain = new FilterChain(chip);
        calibration = new SingleCameraCalibration(chip);
        calibration.setRealtimePatternDetectionEnabled(false);
        getSupport().addPropertyChangeListener(SingleCameraCalibration.EVENT_NEW_CALIBRATION, this);
        chain.add(calibration);
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
        if (calibration.isCalibrated() && !dirPacket.isEmpty()) {

            int nev = dirPacket.getSize();
            FloatPointer fp = new FloatPointer(nev * 2);
            int fpidx = 0;
            for (Object o : dirPacket) {
                ApsDvsMotionOrientationEvent e = (ApsDvsMotionOrientationEvent) o;
                fp.put(fpidx++, e.x);
                fp.put(fpidx++, e.y);
            }
//            Mat inEvents = new Mat(2, nev, opencv_core.CV_32FC1);
            Mat src = new Mat(new Size(nev, 1), CV_32FC2, fp); // make wide 2 channel matrix of source event x,y
//            String ss = "original: " + printMatF(src).substring(0, 100);
            Mat dst = new Mat(1, nev, opencv_core.CV_32FC2); // destination for undistortion
            undistortPoints(src, dst, calibration.getCameraMatrix(), calibration.getDistortionCoefs());
//            String sd = "undisted: " + printMatF(dst).substring(0, 100);
//            System.out.println(ss + "\n" + sd);
            DoubleIndexer k = calibration.getCameraMatrix().createIndexer(); // get the camera matrix elements
            float fx, fy, cx, cy;
            fx = (float) k.get(0, 0);
            fy = (float) k.get(1, 1);
            cx = (float) k.get(0, 2);
            cy = (float) k.get(1, 2);
//            Mat dst2 = new Mat(1, nev, opencv_core.CV_32FC2);
//            gemm(calibration.getCameraMatrix(), dst, 1, src, 0, dst2,0); // http://docs.opencv.org/2.4/modules/core/doc/operations_on_arrays.html#gemm
            // compute the undistorted locations in image
            FloatIndexer fi = dst.createIndexer();
            int j = 0;
            for (Object o : dirPacket) {
                ApsDvsMotionOrientationEvent e = (ApsDvsMotionOrientationEvent) o;
                BasicEvent ev = (BasicEvent) o;
                float x1 = (fi.get(0,j,0)), y1 = (fi.get(0,j,1));
                float x2 = fx * x1 + cx, y2 = fy * y1 + cy;
                ev.x = (short) x2;
                ev.y = (short) y2;
                j++;
            }
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
