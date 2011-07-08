/*
 * Created on Dec 2, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package net.sf.jaer.hardwareinterface.serial;

import gnu.io.UnsupportedCommOperationException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 * Basic viewer for eDVS serial output
 * @author Jorg Conradt
 */
public class EmbeddedDVS128Viewer extends JApplet {

    static final long serialVersionUID = 1;
    private IOThread terminalThread = null;
    private HWP_UART portUART = new HWP_UART();
    private static Logger log=Logger.getLogger("EmbeddedDVS128Viewer");

    public void processSpecialData(String specialData) {

        if ((specialData.length()) == 9) {
//			final double timeFactorDVS = 1.0 / (2.0*0.065536);			// this is for 2us resolution and full 2^16 bits timestamp
            final double timeFactorDVS = 1.0 / (2.0 * 0.05);				// this is for 2us resolution total of 100ms timestamp

            int eventCountTotalDVS = (((specialData.charAt(0)) - 32) << 12) | (((specialData.charAt(1)) - 32) << 6) | ((specialData.charAt(2)) - 32);
            double epsTotalDVS = Math.round(((double) eventCountTotalDVS) * timeFactorDVS);

            int eventCountOnDVS = (((specialData.charAt(3)) - 32) << 12) | (((specialData.charAt(4)) - 32) << 6) | ((specialData.charAt(5)) - 32);
            double epsOnDVS = Math.round(((double) eventCountOnDVS) * timeFactorDVS);
            int eventCountOffDVS = (((specialData.charAt(6)) - 32) << 12) | (((specialData.charAt(7)) - 32) << 6) | ((specialData.charAt(8)) - 32);
            double epsOffDVS = Math.round(((double) eventCountOffDVS) * timeFactorDVS);


            System.out.printf("Received event count: %8.3fkEPS  (+%8.3f / -%8.3f)\n", (epsTotalDVS / 1000.0),
                    (epsOnDVS / 1000.0), (epsOffDVS / 1000.0));
        }

        if ((specialData.length()) == 5) {
            int biasID = ((specialData.charAt(0)) - 32);
            int biasValue = (((specialData.charAt(1)) - 32) << 18) | (((specialData.charAt(2)) - 32) << 12) | (((specialData.charAt(3)) - 32) << 6) | ((specialData.charAt(4)) - 32);
            System.out.printf("Read eDVS Bias %2d = %8d!\n", biasID, biasValue);
        }

    }

    private void setBiasValue(int biasID, int biasValue) throws UnsupportedEncodingException, IOException {
        if (portUART != null) {
            for (int n = 0; n < 12; n++) {
                portUART.sendCommand("!B" + biasID + "=" + biasValue);
            }
            portUART.sendCommand("B");		// flush values to sensor!
        }
    }

    private void connectEDVS128() throws UnsupportedCommOperationException, IOException {
        if (terminalThread != null) {
            terminalThread.terminate();
        }

        String portName = "com3";

        portUART.open(portName, 4000000);

        terminalThread = new IOThread(portUART);
        terminalThread.start();
    }

    private void disconnectEDVS128() {
        if (terminalThread != null) {
            terminalThread.terminate();
        }
        portUART.close();
        terminalThread = null;
    }

    public EmbeddedDVS128Viewer()  {
        try {
            connectEDVS128();
        } catch (Exception e){
            log.warning(e.toString());
        }

        // here we just wait, as currently the thread retrieves events in the background

        // do something when events happen
        disconnectEDVS128();

    }

}
