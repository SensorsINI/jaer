package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import javax.swing.Timer;

/**
 * Send commands to RUBIOS
 * 
 * @author Holger
 */
public class PanTiltControlRUBI extends PanTiltControl {

    RubiSend rubios;
    static RubiEcho rubiEcho;
    static String destination;
    private static boolean logResponses = false;
    private static Logger loggerResponses = Logger.getLogger("Pan-Tilt-Responses");
    static ActionListener taskPerformer = new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                timer.stop();
                if (logResponses) {
                    loggerResponses.info("TimerAction: Wait done! Restart Filters!");
                }
                if (panTiltListener != null) {
                        panTiltListener.panTiltAction(new PanTiltEvent(this, 0));
                    }
                wasMoving = false;
            }
        };
    private static Timer timer = new Timer(waitPeriod, taskPerformer);

    public PanTiltControlRUBI() {
        super();
    }

    public void setLogResponses(boolean aLogResponses) {
        if (aLogResponses) {
            try {
                rubiEcho = new RubiEcho(PanTiltControlRUBI.destination);
                rubiEcho.start();
            } catch (Exception e) {
                //e.printStackTrace();
                log.warning("could not connect! Exeption: "+e);
                return;
            }
            
        }
        else {
            rubiEcho = null;
        }
        logResponses = aLogResponses;
    }
     
    public void setWaitPeriod(int WaitPeriod) {
        PanTiltControlRUBI.waitPeriod = WaitPeriod;
        timer = new Timer(waitPeriod, taskPerformer);
    }

    void connect(String destination) throws Exception {
        PanTiltControlRUBI.destination = destination;
        double myDt = 0.01;
        String args[] = new String[1];
        args[0] = destination;
        rubios = new RubiSend(args, myDt );
        this.connected = true;
    }
    
    public void setPanPos(int pos) {
        double newPos = (2.0*(pos-this.getOldMinPanPos()))/(this.getOldMaxPanPos()-this.getOldMinPanPos())-1.0;
        PanTiltControlPTU.moving = false; //TODO: feedback
        PanTiltControlPTU.wasMoving = false;
        timer.start();
        this.panPos = pos;
        rubios.sendMessage("MoveServo Saliency C AbsCenter T .1 V 5 H 1e-5 N HeadTurn P "+newPos);
            /*
            "Jaw",            "FrownLeft",      "HeadTilt",       "EELeft",
            "SmileLeft",      "LipUpperCenter", "BrowOuterLeft",  "BrowInnerLeft",
            "SquintLeft",     "SneerLeft",      "LipUpperLeft",   "EyeLeftTurn",
            "LipLowerLeft",   "EyesBothUpDown", "UpperNod",       "LowerNod",
            "LipLowerCenter", "SneerRight",     "EERight",        "FrownRight",
            "SmileRight",     "EyeLidsUpper",   "EyeLidsLower",   "BrowOuterRight",
            "BrowInnerRight", "BrowCenter",     "LipUpperLeft",   "LipUpperRight",
            "HeadTurn",       "SquintRight",    "EyeRightTurn",   "Empty"
             */
    }

    void setTiltPos(int pos) {
        double newPos = (2.0*(pos-this.getOldMinTiltPos()))/(this.getOldMaxTiltPos()-this.getOldMinTiltPos())-1.0;
        PanTiltControlPTU.moving = false; //TODO: feedback
        PanTiltControlPTU.wasMoving = true;
        timer.start();
        this.tiltPos = pos;
        rubios.sendMessage("MoveServo AuditoryLocalization C AbsCenter T .1 V 5 H 1e-5 N HeadTilt P "+newPos);
    }
    
    public void setPanSpeed(int speed) {

    }

    public void executeCommand(String command) {
        rubios.sendMessage(command);
    }

    public void halt() {

    }


}
