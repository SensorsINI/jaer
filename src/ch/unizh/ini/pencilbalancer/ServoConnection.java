/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.pencilbalancer;

import java.util.logging.Logger;

/**
 * Manages connection to servo via RXTX library.
 * @author conradt
 */
public class ServoConnection {
    static Logger log=Logger.getLogger("ServoConnection");
    private static ServoConnection instance = null;
    private HWP_RS232 rs232Port = null;
    private boolean isConnectedToServo = false;

    long nanoLastTime=System.nanoTime();

    public synchronized static ServoConnection getInstance() {
        if (instance == null) {
            log.info("Creating new ServoConnectionInstance");
            instance = new ServoConnection();
        }
        log.info("Returning existing ServoConnection");
        return (instance);
    }

    private ServoConnection() {
        log.info("Setting up connection to servo board");

        rs232Port = new HWP_RS232();

        connectServo(false);
    }

    public synchronized boolean isConnected() {
        return (isConnectedToServo);
    }

    public synchronized void connectServo(boolean connectFlag) {
        if (connectFlag == false) {
            rs232Port.sendCommand("-");
            rs232Port.sendCommand("!D-");  // turn off debug output
            log.info("diconnecting!");
            rs232Port.close();
            isConnectedToServo = false;
        } else {
            if (isConnectedToServo == false) {
                HWPort.PortIdentifier thisPI = null;
                HWPort.PortAttribute thisPA = null;

//                System.out.print("Available hardware ports:");
                for (HWPort.PortIdentifier pi : rs232Port.getPortIdentifierList()) {
//                    System.out.print(" -" + pi.display + "- ");
                    if ((pi.display).equals("  COM3")) {
//                        System.out.print("*");
                        thisPI = pi;
                    }
                }
//                System.out.print("\nAvailable Attributes:");
                for (HWPort.PortAttribute pa : rs232Port.getPortAttributeList()) {
//                    System.out.print(" " + pa.display);
                    if ((pa.display).equals("  2000000Bd")) {
//                        System.out.print("*");
                        thisPA = pa;
                    }
                }

                if ((thisPI != null) && (thisPA != null)) {
                    log.info("Opening Port " + thisPI.getID() + " with attribute " + thisPA);
                    rs232Port.open(((String) thisPI.getID()), thisPA);
                    rs232Port.setHardwareFlowControl(true);

                    isConnectedToServo = true;

//                    rs232Port.sendCommand("!D5");  // !Dx means enable debug output every 2ms * x
//                    rs232Port.sendCommand("!D+");  // enable debug output
                    rs232Port.sendCommand("");     // send a dummy return to clear pending input
                    rs232Port.sendCommand("+");    // enable servo control
//                    rs232Port.sendCommand("!S+2"); // reply only errors and debug output
                    rs232Port.sendCommand("!S+3"); // reply only errors and debug output, reply on request ("?xxx")
                } else {
                    log.warning("\nError, could not find proper port or baud-rate\n");
                    isConnectedToServo = false;
                }
            }
        }
    }

    public synchronized void sendCommand(String command) {
        if (rs232Port != null) {
            rs232Port.sendCommand(command);
            rs232Port.flushOutput();
        }
    }

    public synchronized String readLine() {
        return(rs232Port.readLine());
    }

    public synchronized void closeAndTerminate() {
        rs232Port.close();
        rs232Port = null;
        instance = null;            // restart from scratch
    }
}
