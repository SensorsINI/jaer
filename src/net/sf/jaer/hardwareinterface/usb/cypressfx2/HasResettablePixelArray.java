/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;

/**
 * This hardware device has globally-resettable pixels.
 * 
 * @author tobi
 */
public interface HasResettablePixelArray {

    /** Returns the array reset flag.
    boolean isArrayReset();

    /**
     * set the pixel array reset
     * @param value true to reset the pixels, false to let them run normally
     */
    void setArrayReset(boolean value);

    /** Momentarily resets all the pixels.
     *
     */
        public void resetPixelArray();


}
