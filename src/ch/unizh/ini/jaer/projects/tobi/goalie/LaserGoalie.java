/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.tobi.goalie;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import ch.unizh.ini.jaer.hardware.pantilt.CalibratedPanTilt;
import ch.unizh.ini.jaer.hardware.pantilt.LaserOnOffControl;
/**
 *  The Goalie including control of a pantilt unit to aim and turn on a laser pointer at the ball that is being blocked.
 * 
 * @author tobi
 */
public class LaserGoalie extends Goalie {
    CalibratedPanTilt panTilt=null;
    ServoInterface servo=null;
    private float panTiltOffsetPixels=getPrefs().getFloat("LaserGoalie.panTiltOffsetPixels",-5f);
    {setPropertyTooltip("panTiltOffsetPixels","offset vertically of laser to account for ball height");}
    private boolean useLaser=getPrefs().getBoolean("LaserGoalie.useLaser", true);
    {setPropertyTooltip("useLaser","use the laser pointer");}
    
    public LaserGoalie(AEChip chip) {
        super(chip);
        panTilt=new CalibratedPanTilt(chip);
        setEnclosedFilter(panTilt);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) {
            return in;
        }

        panTilt.filterPacket(in);
        if(panTilt.getCalibrator().isCalibrating()) {
            return in;
        }
        out=super.filterPacket(in); // goalie
        if(!useLaser) return out;
        if(!panTilt.isLockOwned()) {
            if(getState()==State.ACTIVE&&ball!=null&&ball.isVisible()) {
                float x=ball.getLocation().x;
                float y=ball.getLocation().y;
                try {
                    if(checkHardware()) {
                        panTilt.setPanTiltVisualAim(x, y-panTiltOffsetPixels);

                        if(panTilt instanceof LaserOnOffControl) {
                            ((LaserOnOffControl) panTilt).setLaserEnabled(true);
                        }
                    }
                } catch(HardwareInterfaceException ex) {
                    log.warning(ex.toString());
                }
            } else {
                if(checkHardware()&&panTilt instanceof LaserOnOffControl) {
                    ((LaserOnOffControl) panTilt).setLaserEnabled(false);
                }
            }
        }
        return out;
    }

    private synchronized boolean checkHardware() {
        if(servo==null||!servo.isOpen()) {
            if(ServoInterfaceFactory.instance().getNumInterfacesAvailable()==0) {
                return false;
            }
            try {
                servo=(ServoInterface) (ServoInterfaceFactory.instance().getInterface(0));
                if(servo==null) {
                    return false;
                }
                panTilt.setServoInterface(servo);
                servoArm.setServoInterface(servo);
                servo.open();
            } catch(HardwareInterfaceException e) {
                servo=null;
                log.warning(e.toString());
                return false;
            }
        }
        return true;
    }

    public float getPanTiltOffsetPixels() {
        return panTiltOffsetPixels;
    }

    public void setPanTiltOffsetPixels(float panTiltOffsetPixels) {
        this.panTiltOffsetPixels=panTiltOffsetPixels;
        getPrefs().putFloat("LaserGoalie.panTiltOffsetPixels",panTiltOffsetPixels);
    }

    public boolean isUseLaser() {
        return useLaser;
    }

    public void setUseLaser(boolean useLaser) {
        this.useLaser=useLaser;
        getPrefs().putBoolean("LaserGoalie.useLaser",useLaser);
    }
}
