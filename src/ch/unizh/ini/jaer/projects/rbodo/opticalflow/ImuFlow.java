package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.v;
import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.vx;
import static ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlowIMU.vy;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
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

    private boolean showCompleteVectorField = getBoolean("showCompleteVectorField", false);
    private int vectorFieldDownsampling = getInt("vectorFieldDownsampling", 4);
    private int renderingCounter=0;

    public ImuFlow(AEChip chip) {
        super(chip);
        numInputTypes = 2;
        String tt = "0: IMUFlow";
        setPropertyTooltip(tt, "showCompleteVectorField", "Select to draw vector field, deselect to draw vectors only on events");
        setPropertyTooltip(tt, "vectorFieldDownsampling", "Drawn vectors are downsampled (decimated/skipped) by 2^vectorFieldDownsampling for visibilty");
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in); // sets up dirPacket and outItr for it

        // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        if (!showCompleteVectorField) {
            // fill the dirPacket with OF events at DVS event locations that show the GT flow; this packet gets drawn by annotate in AbstractMotionFlowIMU
            while (i.hasNext()) {
                Object o = i.next();
                if (o == null) {
                    log.warning("null event passed in, returning input packet");
                    return in;
                }
                if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent) o).isApsData()) {
                    continue;
                }
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

                // tobi no idea what this code does, leftover from Bodo paper work
//                exportFlowToMatlab(318951235, 319061235);
                //exportFlowToMatlab(2500000,2600000); // for IMU_APS_translSin
                //exportFlowToMatlab(1360000,1430000); // for IMU_APS_rotDisk
                //exportFlowToMatlab(295500000,296500000); // for IMU_APS_translBoxes
                if(renderingCounter++%(1<<vectorFieldDownsampling)==0){ // skip drawing some vectors for visibility
                    processGoodEvent();
                }
            }

            getMotionFlowStatistics().updatePacket(countIn, countOut, ts);
            return isDisplayRawInput() ? in : dirPacket;
        } else { // draw vector field
            // fill the dirPacket with OF events at regular downsampled locations that show the GT flow; this packet gets drawn by annotate in AbstractMotionFlowIMU
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            int k = 1 << vectorFieldDownsampling;
            for (short x = 0; x < sx; x += k) {
                for (short y = 0; y < sy; y += k) {
                    eout = (ApsDvsMotionOrientationEvent) outItr.nextOutput();
                    eout.timestamp=in.getLastTimestamp();
                    
                    eout.x = x;
                    eout.y = y;
                    imuFlowEstimator.calculateImuFlow(eout);
                    eout.velocity.x = imuFlowEstimator.getVx();
                    eout.velocity.y = imuFlowEstimator.getVy();
                    eout.speed = imuFlowEstimator.getV();
                    eout.hasDirection = v != 0;
                }
            }
            return in;
        }
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

    /**
     * @return the showCompleteVectorField
     */
    public boolean isShowCompleteVectorField() {
        return showCompleteVectorField;
    }

    /**
     * @param showCompleteVectorField the showCompleteVectorField to set
     */
    public void setShowCompleteVectorField(boolean showCompleteVectorField) {
        this.showCompleteVectorField = showCompleteVectorField;
        putBoolean("showCompleteVectorField", showCompleteVectorField);
    }

    /**
     * @return the vectorFieldDownsampling
     */
    public int getVectorFieldDownsampling() {
        return vectorFieldDownsampling;
    }

    /**
     * @param vectorFieldDownsampling the vectorFieldDownsampling to set
     */
    public void setVectorFieldDownsampling(int vectorFieldDownsampling) {
        this.vectorFieldDownsampling = vectorFieldDownsampling;
        putInt("vectorFieldDownsampling", vectorFieldDownsampling);
    }

}
