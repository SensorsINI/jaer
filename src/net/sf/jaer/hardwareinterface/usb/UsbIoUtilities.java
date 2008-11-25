/*
 * USBIOUtilities.java
 *
 * Created on July 16, 2007, 10:10 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 16, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.*;

/**
 * Static methods and flags for USBIO to help build code that will run on non-windows platforms even though Thesycon USBIO
 package will not run there.
 * @author tobi
 */
public class UsbIoUtilities {
    
    static Logger log=Logger.getLogger("USBIO");
    
    /** Creates a new instance of USBIOUtilities */
    private UsbIoUtilities() {
    }
    
    /** classes can check this before trying to do things with UsbIo */
    public static boolean usbIoIsAvailable=false;
    
    static{
        if(System.getProperty("os.name").startsWith("Windows")){
            try{
    //            log.info("checking USBIO");
                System.loadLibrary("USBIOJAVA");
                usbIoIsAvailable=true;
            }catch(UnsatisfiedLinkError e){
                log.warning(e.getMessage()+ ": USBIOJAVA libaray not found; either you are not running Windows, the UsbIoJava.jar is not on the classpath, or the native DLL is not on java.library.path");
                usbIoIsAvailable=false;
            }
        }
    }
}
