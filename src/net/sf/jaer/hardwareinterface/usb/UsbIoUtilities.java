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
    
    static final Logger log=Logger.getLogger("USBIO");
    /** classes can check this before trying to do things with UsbIo */
    private static boolean libraryLoaded=false;

    /**
     * Returns true if the UsbIo library is loaded, which will only happen on windows.
     * @return the libraryLoaded
     */
    synchronized public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    /**
     * @param aLibraryLoaded the libraryLoaded to set
     */
    synchronized public static void setLibraryLoaded(boolean aLibraryLoaded) {
        libraryLoaded = aLibraryLoaded;
    }
    
    /** Creates a new instance of USBIOUtilities */
    private UsbIoUtilities() {
    }
    
    
    static {
        if(!isLibraryLoaded() && System.getProperty("os.name").startsWith("Windows")){
            try{
    //            log.info("checking USBIO");
                synchronized (UsbIoUtilities.class) { // prevents multiple callers from trying to load library
                    System.loadLibrary("USBIOJAVA");
                    setLibraryLoaded(true);
                }
                log.info("USBIOJAVA is avaiable");
            }catch(UnsatisfiedLinkError e){
                log.warning(e.getMessage()+ ": USBIOJAVA libaray not found; either you are not running Windows, the UsbIoJava.jar is not on the classpath, or the native DLL is not on java.library.path");
                setLibraryLoaded(false);
            }
        }
    }
}
