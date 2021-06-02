/*
 * CypressFX2AEX.java
 *
 * Created on July 13, 2007, 10:03 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 13, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;


/**
 * Daniel Fasnacht's AEX board which monitors and sequences
 * @author tobi
 * Sim: This class may be used if a direct USB connection to this device is desired,
 * otherwise The AEX class will be used, which connects to the netdemon for this device.
 */
public class CypressFX2AEX extends CypressFX2MonitorSequencer {
    
    /** Creates a new instance of CypressFX2AEX */
    public CypressFX2AEX(int devNumber) {
        super(devNumber);
        
        TICK_US_BOARD=1;
        
        this.EEPROM_SIZE=0x4000;
    }
}
