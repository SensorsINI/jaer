/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 *
 * @author Holger
 */
public class DynamixelControl {

    public Logger log = Logger.getLogger("Dynamixel");
    public OutputStream out;
    public InputStream in;

    void connect(String destination) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(destination);
        if (portIdentifier.isCurrentlyOwned()) {
            log.warning("Error: Port for Dynamixel-Communication is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(57142, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                in = serialPort.getInputStream();
                out = serialPort.getOutputStream();
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
                log.info("Connected to Dynamixel!");
            } else {
                log.warning("Error: Cannot connect to Dynamixel!");
            }
        }
    }

    /**
     *
     */
    public static class SerialReader implements gnu.io.SerialPortEventListener
    {
        private InputStream in;
        private byte[] buffer = new byte[1024];
        private static volatile boolean logResponses = false;
        private boolean waitingForStarResponse = false;
        private static Logger loggerResponses = Logger.getLogger("Pan-Tilt-Responses");

        /**
         *
         * @param in
         */
        public SerialReader(InputStream in) {
            this.in = in;
        }

        /**
         *
         * @param arg0
         */
        public void serialEvent(gnu.io.SerialPortEvent arg0) {
            int data;
            String response = "";
        }
    }
}
