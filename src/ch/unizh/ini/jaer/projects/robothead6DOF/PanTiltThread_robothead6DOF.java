/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import ch.unizh.ini.jaer.projects.cochsoundloc.CommObjForPanTilt;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 * This thread controls the pan tilt unit.
 *
 * @author Holger
 * see original at ch.unizh.ini.jaer.projects.cochsoundloc
 *
 * changes for the use with the 6DOF robot head from Jörg Conradt
 * @editor Philipp
 */
public class PanTiltThread_robothead6DOF extends Thread {

    private static final Logger log = Logger.getLogger("PanTiltThread_robothead6DOF");
    boolean exitThread = false;
    PanTilt_robothead6DOF panTilt = null;
    int itdValue;       //current ITD value
    static ImageCreator imageCreator = null;
    Head6DOF_ServoController headControl = null;
    float currentHeadPan;
    float currentHeadTilt;
    public double turnFactor = 0.0012315823;    //constant turn factor multiplied with incoming ITD values
    public int lastITDValue;

    public PanTiltThread_robothead6DOF(PanTilt_robothead6DOF panTilt) {
        headControl = panTilt.headControl;
        setName("PanTiltThread_robothead6DOF");
        this.panTilt = panTilt;
        currentHeadPan = (float) headControl.getGazeDirection().getHeadDirection().getX();
        currentHeadTilt = (float) headControl.getGazeDirection().getHeadDirection().getY();
    }

    @Override
    public void run() {
        while (exitThread == false) {
            try {
                CommObjForPanTilt filterOutput;
                filterOutput = panTilt.takeBlockingQ();
                if (filterOutput.isFromCochlea()) {
                    boolean x = true;
                    if (headControl.isConnected() && x == true) {
                        itdValue = ITDFilter_robothead6DOF.pollITDEvent();  //get a new ITD value
                        if (true) {
                            if (Math.abs(itdValue) > 64) {  //only move if the absolut ITDValue is above this threshold to stop the head if he is about to face the sound source
                                if (this.panTilt.iTDFilter.ITDImageCreator.isFiltersReady()) {
                                    this.panTilt.iTDFilter.ITDImageCreator.setMoving(true);  //signals the ITDImageCreator that the head is moving
                                    if (this.panTilt.iTDFilter.ITDImageCreator.isJitteringActive() == true) {
                                        this.panTilt.iTDFilter.ITDImageCreator.doToggleJittering();
                                    }
                                }
                                currentHeadPan = (float) headControl.getGazeDirection().getHeadDirection().getX();   //get current head positions
                                currentHeadTilt = (float) headControl.getGazeDirection().getHeadDirection().getY();
                                float movement = (float) (itdValue * getTurnFactor() - 0.0005766979);  //calculate movement based on ITD
                                try {
                                    headControl.setHeadDirection(currentHeadPan + movement, currentHeadTilt); //pan head
                                } catch (HardwareInterfaceException | IOException e) {
                                    log.warning(e.toString());
                                }
                                try {
                                    Thread.sleep(1000);  //sleep for 1sec to wait for finished movement and no motor noise
                                } catch (InterruptedException ex) {
                                    log.severe("can not set PanTiltThread_robothead6DOF to sleep: " + ex.toString());
                                }
                            } else {
                                if (this.panTilt.iTDFilter.ITDImageCreator.isFiltersReady() && lastITDValue != itdValue) {
                                    this.panTilt.iTDFilter.ITDImageCreator.setMoving(false); //signals the ITDImageCreator that the head stopped moving
                                    if (this.panTilt.iTDFilter.ITDImageCreator.isJitteringActive() != true) {
                                        this.panTilt.iTDFilter.ITDImageCreator.doToggleJittering();  //jitter around only when receiving a new ITD value
                                    }
                                    lastITDValue = itdValue;
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException ex) {
                log.warning("exception in PanTiltThread: " + ex);
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --turnFactor--">
    /**
     * @return the turnFactor
     */
    public double getTurnFactor() {
        return turnFactor;
    }

    /**
     * @param turnFactor the turnFactor to set
     */
    public void setTurnFactor(double turnFactor) {
        this.turnFactor = turnFactor;
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --lastITDValue--">
    /**
     * @return the lastITDValue
     */
    public int getLastITDValue() {
        return lastITDValue;
    }

    /**
     * @param lastITDValue the lastITDValue to set
     */
    public void setLastITDValue(int lastITDValue) {
        this.lastITDValue = lastITDValue;
    } // </editor-fold>
}
