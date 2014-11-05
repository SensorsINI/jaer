/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.io.IOException;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 * Filter to move the 6DOF robot head and eyes in small steps
 *
 * @author Philipp
 */
@Description("Filter to move the 6DOF robot head and eyes in small steps")
public class CalibrateRobotHead extends EventFilter2D { // extends EventFilter only to allow enclosing in filter

    protected static final Logger log = Logger.getLogger("CalibrateRobotHead");
    private float stepSize;  //indicates the step size
    public final float eyeRangeX;
    public final float eyeRangeY;
    public final float headRangeX;
    public final float headRangeY;
    float headX;
    float headY;
    float eyeX;
    float eyeY;
    boolean calibrateHead = false;
    boolean calibrateEye = false;
    
    FilterChain filterChain = null;
    Head6DOF_ServoController headControl = null;
    
    public CalibrateRobotHead(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        headControl = new Head6DOF_ServoController(chip);
        filterChain.add(headControl);
        setEnclosedFilterChain(filterChain);
        this.chip = chip;
        setPropertyTooltip("SaveCalibration", "saves the current calibration array into a file");
        setPropertyTooltip("AddToCalibrationArray", "adds the current pixel position and gaze direction to the array");
        setPropertyTooltip("pixelXPosition", "the X position of the selected pixel");
        setPropertyTooltip("pixelYPosition", "the Y position of the selected pixel");
        setPropertyTooltip("calibrateEye", "calibrates based on eye movements");
        setPropertyTooltip("calibrateHead", "calibrates based on head movements");
        setPropertyTooltip("TiltUp", "tilts the selected part up");
        setPropertyTooltip("TiltDown", "tilts the selected part down");
        setPropertyTooltip("PanLeft", "pans the selected part to the left");
        setPropertyTooltip("PanRight", "pans the selected part to the right");
        headRangeX = headControl.getHEAD_PAN_LIMIT();
        headRangeY = headControl.getHEAD_TILT_LIMIT();
        eyeRangeX = headControl.getEYE_PAN_LIMIT();
        eyeRangeY = headControl.getEYE_TILT_LIMIT();
        stepSize = .02f;
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = filterChain.filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doTiltUp() {
            if (isCalibrateHead() == true) {
                moveHead(getStepSize(),0);
            }
            if (isCalibrateEye() == true) {
                moveEye(getStepSize(),0);
            }
            headX = (float) headControl.getGazeDirection().getHeadDirection().getX();
            headY = (float) headControl.getGazeDirection().getHeadDirection().getY();
            eyeX = (float) headControl.getGazeDirection().getEyeDirection().getX();
            eyeY = (float) headControl.getGazeDirection().getEyeDirection().getY();
    }
    
    public void doTiltDown() {
            if (isCalibrateHead() == true) {
                moveHead(-getStepSize(),0);
            }
            if (isCalibrateEye() == true) {
                moveEye(-getStepSize(),0);
            }
            headX = (float) headControl.getGazeDirection().getHeadDirection().getX();
            headY = (float) headControl.getGazeDirection().getHeadDirection().getY();
            eyeX = (float) headControl.getGazeDirection().getEyeDirection().getX();
            eyeY = (float) headControl.getGazeDirection().getEyeDirection().getY();
    }

    public void doPanRight() {
            if (isCalibrateHead() == true) {
                moveHead(0,getStepSize());
            }
            if (isCalibrateEye() == true) {
                moveEye(0,getStepSize());
            }
            headX = (float) headControl.getGazeDirection().getHeadDirection().getX();
            headY = (float) headControl.getGazeDirection().getHeadDirection().getY();
            eyeX = (float) headControl.getGazeDirection().getEyeDirection().getX();
            eyeY = (float) headControl.getGazeDirection().getEyeDirection().getY();
    }

    public void doPanLeft() {
            if (isCalibrateHead() == true) {
                moveHead(0,-getStepSize());
            }
            if (isCalibrateEye() == true) {
                moveEye(0,-getStepSize());
            }
            headX = (float) headControl.getGazeDirection().getHeadDirection().getX();
            headY = (float) headControl.getGazeDirection().getHeadDirection().getY();
            eyeX = (float) headControl.getGazeDirection().getEyeDirection().getX();
            eyeY = (float) headControl.getGazeDirection().getEyeDirection().getY();
    }

    private void moveHead(float tilt, float pan) {
        try {
            headControl.setHeadDirection((float) (headControl.getGazeDirection().getHeadDirection().getX() + pan), ((float) headControl.getGazeDirection().getHeadDirection().getY() + tilt));
            log.info("PAN: " + Float.toString(headControl.getGazeDirection().getHeadDirection().x)  + "TILT: " + Float.toString(headControl.getGazeDirection().getHeadDirection().y));
        } catch (HardwareInterfaceException | IOException ex) {
            log.warning(ex.toString());
        }
    }

    private void moveEye(float tilt, float pan) {
        try {
            headControl.setEyeGazeDirection((float) (headControl.getGazeDirection().getEyeDirection().getX() + pan), ((float) headControl.getGazeDirection().getEyeDirection().getY()+ tilt));
            log.info("PAN: " + Float.toString(headControl.getGazeDirection().getEyeDirection().x)  + "TILT: " + Float.toString(headControl.getGazeDirection().getEyeDirection().y));
        } catch (HardwareInterfaceException | IOException ex) {
            log.warning(ex.toString());
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for stepSize">
    /**
     * @return the resetTime
     */
    public float getStepSize() {
        return stepSize;
    }

    /**
     * @param stepSize the numOfCameras to set
     */
    public void setStepSize(String stepSize) {
        this.stepSize = Float.valueOf(stepSize);
        putString("CalibrateRobotHead.stepSize", stepSize);
        log.info("new step size: " + Float.toString(this.stepSize));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for calibrateHead">
    /**
     * @return the imageCreatorAlive
     */
    public boolean isCalibrateHead() {
        return calibrateHead;
    }

    /**
     * @param calibrateHead the imageCreatorAlive to set
     */
    public void setCalibrateHead(boolean calibrateHead) {
        boolean old = this.calibrateHead;
        if (old == false && calibrateHead == true) {
            setCalibrateEye(false);
            this.calibrateHead = calibrateHead;
            getSupport().firePropertyChange("calibrateHead", old, this.calibrateHead);
        }
        if (old == true && calibrateHead == false) {
            this.calibrateHead = calibrateHead;
            getSupport().firePropertyChange("calibrateHead", old, this.calibrateHead);
        }
        log.info("calibrateHead is: " + Boolean.toString(this.calibrateHead) + " calibreateEye is: " + Boolean.toString(this.calibrateEye));
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for calibrateHead">
    /**
     * @return the imageCreatorAlive
     */
    public boolean isCalibrateEye() {
        return calibrateEye;
    }

    /**
     * @param calibrateEye the imageCreatorAlive to set
     */
    public void setCalibrateEye(boolean calibrateEye) {
        boolean old = this.calibrateEye;
        if (old == false && calibrateEye == true) {
            setCalibrateHead(false);
            this.calibrateEye = calibrateEye;
            getSupport().firePropertyChange("calibrateEye", old, this.calibrateEye);
        }
        if (old == true && calibrateEye == false) {
            this.calibrateEye = calibrateEye;
            getSupport().firePropertyChange("calibrateEye", old, this.calibrateEye);
        }
        log.info("calibrateHead is: " + Boolean.toString(this.calibrateHead) + " calibreateEye is: " + Boolean.toString(this.calibrateEye));
    } // </editor-fold>a

}
