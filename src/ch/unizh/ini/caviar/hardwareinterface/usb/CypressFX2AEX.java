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

package ch.unizh.ini.caviar.hardwareinterface.usb;

/**
 * Daniel Fasnacht's AEX board which monitors and sequences
 * @author tobi
 */
public class CypressFX2AEX extends CypressFX2MonitorSequencer {
    
    /** Creates a new instance of CypressFX2AEX */
    public CypressFX2AEX(int devNumber) {
        super(devNumber);
        
        TICK_US_BOARD=1;
        
        this.EEPROM_SIZE=0x4000;
    }
}
