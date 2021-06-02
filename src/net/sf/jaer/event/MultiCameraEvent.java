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

/**
 * Represents an event from one camera in a multi-camera setup.
 * @author tobi
 */
public class MultiCameraEvent extends PolarityEvent {

    public static final int NUM_CAMERAS=2;  // TODO need to be able to change this

    /** Static method to extract the camera number from the 32 bit raw address.
     * It is stored as a byte in the MSB of the 32 bits.
     */
    public static byte getCameraFromRawAddress(int i) {
        return (byte)((i>>>24)&0xff);
    }

    /** Sets the camera number in the raw address, as the MSB of the 32 bits and returns the new address with camera number
     *
     * @param camera by convention starting with 0 for the leftmost looking at the array of cameras
     * @param address the address of the pixel in the cmaera
     * @return the combined address
     */

    public static int setCameraNumberToRawAddress(int camera, int address){
        address=0xffffffff&(address | (0xffffffff&(camera<<24)));
        return address;
    }

    /** The index of the camera, 0 based. */
    public byte camera=0;
     
    /** Creates a new instance of BinocularEvent */
    public MultiCameraEvent() {
    }
    
    @Override public String toString(){
        return super.toString()+" camera="+camera;
    }

    /** Overridden to factor in the camera number as <code>super.getType()+2*camera</code>. */
    @Override
    public int getType() {
        return super.getType()+2*camera;
    }


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

    /** Binocular event has two cell types (left and right) */
    @Override
    public int getNumCellTypes() {
        return 2*NUM_CAMERAS;
    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
       PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(src);
        if(e instanceof MultiCameraEvent){
            this.camera=((MultiCameraEvent)e).camera;
        }
    }

}
