/*
 * CypressFX2Biasgen.java
 *
 * Created on 23 Jan 2008
 *
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;

/**
 * Adds functionality of TCVS320 retina to base classes for Cypress FX2 interface.
 *
 * @author tobi
 */
public class CypressFX2TCVS320Retina extends CypressFX2Biasgen {
    
    /** Creates a new instance of CypressFX2Biasgen */
    protected CypressFX2TCVS320Retina(int devNumber) {
        super(devNumber);
    }
}
