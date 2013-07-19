    package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPortIdentifier;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import de.thesycon.usbio.PnPNotifyInterface;




/**
 * Controls the Pan-Tilt-Unit PTU-D46 via RS-232 and logs the responses.
 * 
 * @author Holger
 */
public class PanTiltControlUSB extends PanTiltControl implements PnPNotifyInterface {

    ServoInterface hwInterface = null;
    float[] servoValues;

    public PanTiltControlUSB() {
        super();
        waitPeriod = 1000;
    }

    public void setLogResponses(boolean aLogResponses) {
        if(!aLogResponses) {
            log.info("log responses turned off");
        }
    }
     
    public void setWaitPeriod(int WaitPeriod) {
        PanTiltControlUSB.waitPeriod = WaitPeriod;
    }

    void connect(String destination) throws Exception {
        try{
            hwInterface = (ServoInterface)ServoInterfaceFactory.instance().getFirstAvailableInterface(); //new SiLabsC8051F320_USBIO_ServoController();
            if ( hwInterface == null ){
                log.warning("No ServoInterface found");
            }
            hwInterface.open();
            servoValues = new float[ hwInterface.getNumServos() ];
        } catch ( HardwareInterfaceException e ){
            e.printStackTrace();
        }
        connected = true;
        log.info("Connected to Pan-Tilt-Unit!");
    }

    @Override
    void setPanPos(double panPos) {
        super.panPos = panPos;
        try {
            hwInterface.setServoValue(1, (float)panPos);
            PanTiltControlUSB.moving = false;
            PanTiltControlUSB.wasMoving = true;
        } catch (HardwareInterfaceException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
        try {
            Thread.sleep(waitPeriod);
            PanTiltControlUSB.wasMoving = false;
        } catch (InterruptedException ex) {
            Logger.getLogger(PanTiltControlUSB.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    void setTiltPos(double tiltPos) {
        super.tiltPos = tiltPos;
        try {
            hwInterface.setServoValue(2, (float)tiltPos);
            PanTiltControlUSB.moving = false;
            PanTiltControlUSB.wasMoving = true;
        } catch (HardwareInterfaceException ex) {
            log.warning("In setPanPos(position) caught IOexception " + ex);
        }
        try {
            Thread.sleep(waitPeriod);
            PanTiltControlUSB.wasMoving = false;
        } catch (InterruptedException ex) {
            Logger.getLogger(PanTiltControlUSB.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** called when device added */
    public void onAdd (){
        log.info("device added");
    }

    public void onRemove (){
        log.info("device removed, closing it");
        if ( hwInterface != null && hwInterface.isOpen() ){
            hwInterface.close();
        }
    }

    public void setPanSpeed(int speed) {
    }

    public void executeCommand(String command) {
    }

    /**
     *
     * @return list of COM ports
     */
    public List<String> getPortList() {
        List<String> ports = new ArrayList<String>(0);
        Enumeration pList = CommPortIdentifier.getPortIdentifiers();
        while (pList.hasMoreElements()) {
            CommPortIdentifier cpi = (CommPortIdentifier) pList.nextElement();
            if (cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                ports.add(cpi.getName());
            }
        }
        return ports;
    }

    public void halt() {
    }
}
