/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import ch.unizh.ini.jaer.projects.cochsoundloc.CommObjForPanTilt;
import ch.unizh.ini.jaer.projects.cochsoundloc.ITDEvent;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 * 
 * 
 * @author philipp
 */


public class PanTiltThread_robothead6DOF extends Thread {

    //public PanTiltFrame_robothead6DOF panTiltFrame = null;
    private static final Logger log = Logger.getLogger("PanTiltThread_robothead6DOF");
    boolean exitThread = false;
    PanTilt_robothead6DOF panTilt = null;
    int itdValue;
    ITDEvent itdEvent = null;
    static ImageCreator imageCreator = null;
    Head6DOF_ServoController headControl = null;
    float currentHeadPan;
    float currentHeadTilt;

    public PanTiltThread_robothead6DOF(PanTilt_robothead6DOF panTilt) {
        headControl = panTilt.headControl;
        setName("PanTiltThread_robothead6DOF");
        this.panTilt = panTilt;
        //this.panTiltFrame = new PanTiltFrame_robothead6DOF(this.panTilt);
        currentHeadPan = (float) headControl.getGazeDirection().getHeadDirection().getX();
        currentHeadTilt = (float) headControl.getGazeDirection().getHeadDirection().getY();
    }

    @Override
    public void run() {
        headControl.doShowGUI();
        while (exitThread == false) {
            try {
                CommObjForPanTilt filterOutput;
                filterOutput = panTilt.takeBlockingQ();
                if (filterOutput.isFromCochlea()) {
                    boolean x = true;
                    if (headControl.isConnected() && x == true) {
                        //itdEvent = ITDFilter_robothead6DOF.pollITDEvent();                        
                        /*  try {
                            for (int i = 0; i < 4; i++) {
                                itdEvent = ITDFilter_robothead6DOF.pollITDEvent();
                                itdValue = itdValue + itdEvent.getITD();
                            }
                        } catch (Exception e) {
                            log.warning(e.toString());
                        }
                        itdValue = itdValue / 4; */
                        itdValue = ITDFilter_robothead6DOF.pollITDEvent();
                        if (true) {
                            //itdValue = itdEvent.getITD();
                            if (Math.abs(itdValue) > 60) {
                                currentHeadPan = (float) headControl.getGazeDirection().getHeadDirection().getX();
                                currentHeadTilt = (float) headControl.getGazeDirection().getHeadDirection().getY();
                                if (itdValue >= 0) {
                                    float movement = (float) (itdValue * -0.0010545358 + 0.0055380434);
                                    try {
                                        headControl.setHeadDirection(currentHeadPan + movement, currentHeadTilt);
                                        // this.panTiltFrame.HeadfromITD((float) ((float) itdValue * -0.0010545358 + 0.0055380434));
                                    } catch (HardwareInterfaceException | IOException e) {
                                        log.warning(e.toString());
                                    }
                                } else {
                                    float movement = (float) (itdValue * -0.0012315823 + 0.0005766979);
                                    try {
                                        headControl.setHeadDirection(currentHeadPan + movement, currentHeadTilt);
                                        //   this.panTiltFrame.HeadfromITD((float) ((float) itdValue * -0.0012315823 + 0.0005766979));
                                    } catch (HardwareInterfaceException | IOException e) {
                                        log.warning(e.toString());
                                    }
                                }
                                try {
                                    Thread.sleep(350);
                                } catch (InterruptedException ex) {
                                    log.severe("can not set PanTiltThread_robothead6DOF to sleep: " + ex.toString());
                                }
                            }
                        }
                    }
                }
                if (filterOutput.isFromRetina()) {

                }
            } catch (InterruptedException ex) {
                log.warning("exception in PanTiltThread: " + ex);
            }
        }
    }
}
