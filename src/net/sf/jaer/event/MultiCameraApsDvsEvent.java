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

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.DevelopmentStatus;

/**
 * Represents an event from one camera in a multi-camera setup.
 * @author Gemma
 */

@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class MultiCameraApsDvsEvent extends ApsDvsEvent {
    
    public int NUM_CAMERAS=0; 
    
    /** Static method to extract the camera number from the 32 bit raw address 
     * for a DVS event.
     * It is stored as a byte in the LSB of the 32 bits.
     */
    
    public static byte getCameraFromRawAddressDVS(int address) {
            int camera= address & 0xf;
            return (byte) camera;   
    }

    /** For a DVS event, sets the camera number in the raw address, as the LSB of the 32 bits 
     * and returns the new address with camera number.
     *
     * This method adds the information of the camera to the raw address. The camera ID is store
     * in the LSB (4-0).
     */

    public static int setCameraNumberToRawAddressDVS(int camera, int oldaddress){
        int newaddress=0xffffffff&(oldaddress | (0xffffffff&camera));
        return newaddress;            
    }
    
    
    /** Static method to extract the camera number from the 32 bit raw address 
     * for a APS event.
     * It is stored as lowest 2 bits of the ADC bits of the raw address. 
     */
    
    public static byte getCameraFromRawAddressAPS(int address, int numCam) {
        if (numCam>2){
            int camera= address & 0x3;
            return (byte) camera;
        }else{
            int camera= address & 0x1;
            return (byte) camera;
        }
    }

    /** For a APS event, sets the camera number in the raw address, the lowest 2 bits of the 32 bits 
     * and returns the new address with camera number.
     * This method adds the information of the camera to the raw address. The camera ID is store
     * in the lowest bits of the ADC bits of the raw address. 
     */

    public static int setCameraNumberToRawAddressAPS(int camera, int oldaddress){
        if (camera>1){
            oldaddress = oldaddress & 0xfffffff0;
            int newaddress=0xffffffff&(oldaddress | (0xffffffff&(camera)));
            return newaddress;
        }else{
            oldaddress = oldaddress & 0xfffffff0;
            int newaddress=0xffffffff&(oldaddress | (0xffffffff&(camera)));
            return newaddress;
        }            
    }
    
    /** The index of the camera, 0 based. */
    public byte camera=0;    
    
    /** Creates a new instance of MultiCameraApsDvsEvent */
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
     * @return the total number of cameras
     */
    public int getNumCamera() {
        return NUM_CAMERAS;
    }

    /**
     * @param aCamera the camera to set
     */
    public void setNumCamera(int numCam) {
        NUM_CAMERAS = numCam;
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