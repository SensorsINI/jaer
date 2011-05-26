/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.util.logging.Level;
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
public class CLEyeHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface{
    final static Logger log=Logger.getLogger("CLEye");
    private static CLEyeHardwareInterfaceFactory instance = new CLEyeHardwareInterfaceFactory(); // singleton
    
   private CLEyeHardwareInterfaceFactory(){
    }
    
    /** @return singleton instance used to construct CLCameras. */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

 
    
    @Override
    public int getNumInterfacesAvailable() {
        if(!CLCamera.isLibraryLoaded()) return 0;
        return CLCamera.cameraCount();
    }

    /** Returns the first camera
     * 
     * @return first camera, or null if none available.
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
         if(!CLCamera.isLibraryLoaded()) return null;
       if(getNumInterfacesAvailable()==0) return null;
        CLCamera cam=new CLRetinaHardwareInterface(0);
        return cam;
    }

    /** Returns the n'th camera (0 based) 
     * 
     * @param n
     * @return the camera
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
         if(!CLCamera.isLibraryLoaded()) return null;
        if(getNumInterfacesAvailable()<n+1) return null;
        CLCamera cam=new CLRetinaHardwareInterface(n);
        return cam;
    }

    @Override
    public String getGUID() {
        return "{4b4803fb-ff80-41bd-ae22-1d40defb0d01}"; // taken from working installation
    }
    
    
         /** Test program
     * 
     * @param args ignored
     */
    public static void main(String[] args) {
        try {
            if(!UsbIoUtilities.isLibraryLoaded()){
                log.warning("no USBIO libraries found");
            }
            if(!CLCamera.isLibraryLoaded()) {
                log.warning("no library found");
                return;
            }
            int camCount = 0;
            if ((camCount = CLCamera.cameraCount()) == 0) {
                log.warning("no cameras found");
                return;
            }
            log.info(camCount + " CLEye cameras found");
            CLCamera cam = new CLCamera();
            cam.open();
            int[] imgData = new int[640*480];
            for(int f=0;f<3;f++){
               cam.getCameraFrame(imgData, 100);
                int pixCount=0;
                int lastval=0;
                for(int i:imgData){
                    if(i!=0)pixCount++;
//                    if(i!=lastval){
//                        System.out.println("new value "+i);
//                        lastval=i;
//                    }
                }
                log.info("got frame with "+pixCount+" nonzero pixels");
                 for(int i=0;i<10;i++){
                     System.out.println(imgData[i]);
                }
            }
            cam.close();
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(CLEyeHardwareInterfaceFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
