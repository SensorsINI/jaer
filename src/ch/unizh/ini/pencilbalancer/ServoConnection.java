/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.pencilbalancer;

import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Manages connection to servo via RXTX library.
 * @author conradt
 */
public class ServoConnection extends Thread {

    static Logger log = Logger.getLogger("ServoConnection");
    private HWP_RS232 rs232Port = null;
    private boolean isRunning = true;
    private boolean connected = false;
    private String toSend;
    private String received;

    public ServoConnection() {
        log.info("Setting up connection to servo board");
        this.start();

        toSend = null;
        received = null;
    }

    public void run() {
        isRunning = true;

        connectServo();

        while (isRunning) {

            yield();

            if (toSend != null) {
                String s=toSend;
                toSend = null;
                rs232Port.sendCommand(s);
                rs232Port.flushOutput();
            }

            String r = rs232Port.readLine();
            if (r != null) {
                received=r;
            }
        }

        if (rs232Port != null) {
            rs232Port.sendCommand("-");
            rs232Port.sendCommand("!D-");  // turn off debug output
            rs232Port.flushOutput();
            rs232Port.close();
        }
        log.info("diconnecting!");
    }

    public void terminate() {
        isRunning = false;
    }

    private void connectServo() {

        rs232Port = new HWP_RS232();

        HWPort.PortIdentifier thisPI = null;
        HWPort.PortAttribute thisPA = null;

        for (HWPort.PortIdentifier pi : rs232Port.getPortIdentifierList()) {
            if ((pi.display).equals("  COM3")) {
                thisPI = pi;
            }
        }
        for (HWPort.PortAttribute pa : rs232Port.getPortAttributeList()) {
            if ((pa.display).equals("  2000000Bd")) {
                thisPA = pa;
            }
        }

        if ((thisPI != null) && (thisPA != null)) {
            log.info("Opening Port " + thisPI.getID() + " with attribute " + thisPA);
            rs232Port.open(((String) thisPI.getID()), thisPA);
            rs232Port.setHardwareFlowControl(true);

            rs232Port.sendCommand("");     // send a dummy return to clear pending input
//          rs232Port.sendCommand("!D5");  // !Dx means enable debug output every 2ms * x
//          rs232Port.sendCommand("!D+");  // enable debug output
            rs232Port.sendCommand("");     // send a dummy return to clear pending input
            rs232Port.sendCommand("+");    // enable servo control
//          rs232Port.sendCommand("!S+2"); // reply only errors and debug output
            rs232Port.sendCommand("!S+3"); // reply only errors and debug output, reply only on request ("?xxx")

        
            connected = true;

        } else {
            log.warning("\nError, could not find proper port or baud-rate... terminating!\n");
            isRunning = false;
        }
    }

    public void sendCommand(String command) {
        toSend = command;
    }

    public String readLine() {
        String r = received;
        received = null;
        return (r);
    }
}
