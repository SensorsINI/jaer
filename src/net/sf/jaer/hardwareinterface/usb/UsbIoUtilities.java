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

import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoErrorCodes;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.logging.*;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.USBIOHardwareInterfaceFactory;

/**
 * Static methods and flags for USBIO to help build code that will run on non-windows platforms even though Thesycon USBIO
package will not run there.  Also includes a static method for registering a PnPNotify listener to work around
 * the problem that only a single listener can be registered for each GUID in the Thesycon native interface or driver.
 * 
 * @author tobi
 */
public class UsbIoUtilities {

    static final Logger log = Logger.getLogger("USBIO");
    /** classes can check this before trying to do things with UsbIo */
    private static boolean libraryLoaded = false;

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
        if (!isLibraryLoaded() && System.getProperty("os.name").startsWith("Windows")) {
            try {
                //            log.info("checking USBIO");
                synchronized (UsbIoUtilities.class) { // prevents multiple callers from trying to load library
                    System.loadLibrary("usbiojava");
                    setLibraryLoaded(true);
                }
//                log.info("USBIOJAVA is avaiable");
            } catch (UnsatisfiedLinkError e) {
                log.warning(e.getMessage() + ": usbiojava libary not found; either you are not running Windows, the UsbIoJava.jar is not on the classpath, or the native DLL is not on java.library.path");
                setLibraryLoaded(false);
            }
        }
    }
    
    
    // mapping from GUID to MyPnPListener
    private static HashMap<String, MyPnPListener> listenerMap = new HashMap();

    // has list of listeners to be called on notification
    private static class MyPnPListener extends ArrayList<PnPNotifyInterface> implements PnPNotifyInterface {

        PnPNotify pnp = new PnPNotify(this);

        public MyPnPListener(String GUID) {
            int status = pnp.enablePnPNotification(GUID);
            if (status != UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
                log.warning("Could not enable PnP notification for GUID " + GUID + ", got error " + UsbIo.errorText(status));
            }
        }

        @Override
        public void onAdd() {
            try {
                for (PnPNotifyInterface i : this) {
                    i.onAdd();
                }
            } catch (ConcurrentModificationException ex) {
                log.warning(ex.toString() + " (ignored)");
            }
        }

        @Override
        public void onRemove() {
            try {
                for (PnPNotifyInterface i : this) {
                    i.onRemove();
                }
            } catch (ConcurrentModificationException ex) {
                log.warning(ex.toString() + " (ignored)");
            }
        }
    }

    /** Enables PnP notifications for Thesycon USBIO add/remove notifications
     * 
     * @param listener a listener for PnP events
     * @param GUID the GUID to listen for.
     */
    synchronized public static void enablePnPNotification(final PnPNotifyInterface listener, final String GUID) {
        if (!isLibraryLoaded()) {
            return;
        }
        MyPnPListener myListener = listenerMap.get(GUID);
        if (myListener == null) {
            myListener = new MyPnPListener(GUID);
            listenerMap.put(GUID, myListener);
        }
        myListener.add(listener);
    }

    /** Disables PnP notifications for Thesycon USBIO add/remove notifications
     * 
     * @param listener a listener for PnP events
     * @param GUID the GUID to listen for.
     */
   synchronized public static void disablePnPNotification(PnPNotifyInterface listener, String GUID) {
        if (!isLibraryLoaded()) {
            return;
        }
        MyPnPListener myListener = listenerMap.get(GUID);
        if (myListener == null) {
            return;
        }
        myListener.remove(listener);

    }
}
