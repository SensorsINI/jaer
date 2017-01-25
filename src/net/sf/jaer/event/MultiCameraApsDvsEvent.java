/*
 * BinocularEvent.java
 *
 * Created on May 28, 2006, 7:45 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.event;

import ch.unizh.ini.jaer.chip.multicamera.MultiDAVIS240CCameraChip;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.stereopsis.MultiCameraInterface;

/**
 * Represents an event from one camera in a multi-camera setup.
 * @author Gemma
 */

@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class MultiCameraApsDvsEvent extends ApsDvsEvent {
    
    public static int NUM_CAMERAS;
       
    /** Static method to extract the camera number from the 32 bit raw address 
     * for a DVS event.
     * It is stored as a byte in the LSB of the 32 bits.
     */
    
    public static byte getCameraFromRawAddressDVS(int i) {
            return (byte)(i & 0xff);   
    }

    /** For a DVS event, sets the camera number in the raw address, as the LSB of the 32 bits 
     * and returns the new address with camera number.
     *
     * @param camera by convention starting with 0 for the leftmost looking at the array of cameras
     * @param address the address of the pixel in the camera
     * @return the combined address
     */

    public static int setCameraNumberToRawAddressDVS(int camera, int address){
        
        address=0xffffffff&(address | (0xffffffff&camera));
        return address;            
    }
    
    
    /** Static method to extract the camera number from the 32 bit raw address 
     * for a APS event.
     * It is stored as a byte..........
     */
    
    public static byte getCameraFromRawAddressAPS(int i) {
            return (byte)(i & 0xff);   
    }

    /** For a APS event, sets the camera number in the raw address, 
     * as...... 
     *
     * @param camera by convention starting with 0 for the leftmost looking at the array of cameras
     * @param address the address of the pixel in the camera
     * @return the combined address
     */

    public static int setCameraNumberToRawAddressAPS(int camera, int address){
        
        address=0xffffffff&(address | (0xffffffff&camera));
        return address;            
    }
    
    /** The index of the camera, 0 based. */
    public byte camera=0;    
    
    /** Creates a new instance of BinocularEvent */
    public MultiCameraApsDvsEvent() {
    }
    
    @Override public String toString(){
        return super.toString()+" camera="+camera;
    }

    /** Overridden to factor in the camera number as <code>super.getType()+2*camera</code>. */
//    @Override
//    public int getType() {
//        return super.getType()+2*camera;
//    }


   /**
     * @return the camera
     */
    public byte getCamera() {
        return camera;
    }

    /**
     * @param aCamera the camera to set
     */
    public void setCamera(byte aCamera) {
        camera = aCamera;
    }
    
    /**
     * @return the camera
     */
    public int getNumberOfCameras() {
        return NUM_CAMERAS=HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
    }

    /**
     * @param aCamera the camera to set
     */
    public void setNumberOfCameras(int n) {
        NUM_CAMERAS = n;
    }

    /** Binocular event has two cell types (left and right) */
//    @Override
//    public int getNumCellTypes() {
//        return 2*NUM_CAMERAS;
//    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
       ApsDvsEvent e=(ApsDvsEvent)src;
        super.copyFrom(src);
        if(e instanceof MultiCameraApsDvsEvent){
            this.camera=((MultiCameraApsDvsEvent)e).camera;
        }
    }


}