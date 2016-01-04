package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 * Draws individual optical flow vectors and computes global motion, 
 * rotation and expansion, based upon motion estimate from IMU gyro sensors.
 * This assumes that the objects seen by the camera are stationary and the only motion is the motion field caused by
 * pure camera rotation.
 * @author rbodo
 */

@Description("Class for amplitude and orientation of local motion optical flow using IMU gyro sensors.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ImuFlow extends AbstractMotionFlowIMU {   
    
    public ImuFlow(AEChip chip) {
        super(chip);
        numInputTypes = 2;
        resetFilter();
    }
    
    @Override synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        
        for (Object ein : in) {
            extractEventInfo(ein);
            imuFlowEstimator.calculateImuFlow((PolarityEvent) inItr.next());
            if (isInvalidAddress(0)) continue;
            if (!updateTimesmap()) continue;
            if (xyFilter()) continue;
            countIn++;
            vx = imuFlowEstimator.getVx();
            vy = imuFlowEstimator.getVy();
            v = imuFlowEstimator.getV();
            if (measureAccuracy || discardOutliersEnabled) setGroundTruth();
            if (accuracyTests()) continue;
            //exportFlowToMatlab(2500000,2600000); // for IMU_APS_translSin
            //exportFlowToMatlab(1360000,1430000); // for IMU_APS_rotDisk
            //exportFlowToMatlab(295500000,296500000); // for IMU_APS_translBoxes
            writeOutputEvent();
            if (measureAccuracy) motionFlowStatistics.update(vx,vy,v,vxGT,vyGT,vGT);
        }
        motionFlowStatistics.updatePacket(measureProcessingTime,showGlobalEnabled,
                                          countIn,countOut);
        return isShowRawInputEnabled() ? in : dirPacket;
    }
}