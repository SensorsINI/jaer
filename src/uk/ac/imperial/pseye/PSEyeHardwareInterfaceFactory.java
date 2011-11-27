/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.imperial.pseye;

import de.thesycon.usbio.PnPNotifyInterface;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;

/**
 * Constructs CLEye hardware interfaces.
 * 
 * @author tobi
 */
public class PSEyeHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface, PnPNotifyInterface {
    /** The GUID associated with jAERs Code Labs driver installation for the PS Eye camera.*/
    public static final String GUID = "{4b4803fb-ff80-41bd-ae22-1d40defb0d01}";
    final static Logger log = Logger.getLogger("PSEye");
    private static PSEyeHardwareInterfaceFactory instance = new PSEyeHardwareInterfaceFactory(); // singleton
    private HardwareInterface[] interfaces;
    
    private PSEyeHardwareInterfaceFactory() { // TODO doesn't support PnP, only finds cameras plugged in on construction at JVM startup.
       buildList();
       UsbIoUtilities.enablePnPNotification(this, GUID); // TODO doesn't do anything since we are not using UsbIo driver for the PS Eye. Doesn't hurt however so will leave in in case we can get equivalent functionality.
    }

    private void buildList() {
        if(PSEyeCamera.isLibraryLoaded()) { // TODO only does this once on construction, never again
            this.interfaces = new HardwareInterface[PSEyeCamera.cameraCount()];
            for ( int i = 0; i < this.interfaces.length; i++ ) {
                this.interfaces[i] = new PSEyeHardwareInterface(i);
            }
        }
    }
    
    /** @return singleton instance used to construct PSEyeCameras. */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    @Override
    public int getNumInterfacesAvailable() {
        if(interfaces==null) return 0;
        return this.interfaces.length;
    }

    /** Returns the first camera
     * 
     * @return first camera, or null if none available.
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return getInterface(0);
    }

    /** Returns the n'th camera (0 based) 
     * 
     * @param n
     * @return the camera
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        if(!PSEyeCamera.isLibraryLoaded()) return null;
        if(getNumInterfacesAvailable() == 0 || getNumInterfacesAvailable() < n + 1) return null;
        return (PSEyeCamera) this.interfaces[n];
    }

    @Override
    public String getGUID() {
        return GUID; // taken from working installation
    }
    
    @Override
    public void onAdd() {
        log.info("camera added, rebuilding list of cameras");
        buildList();
    }

    @Override
    public void onRemove() {
        log.info("camera removed, rebuilding list of cameras");
        buildList();
    }
    
}
