/*
 * StereoChipInterface.java
 *
 * Created on March 19, 2006, 9:52 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import net.sf.jaer.chip.AEChip;

/**
 * Defines interface to multiple related cameras, each an AEChip object.
 * @author tobi
 */
public interface MultiCameraInterface {

    public int getNumCameras();

    public AEChip getCamera(int i);

    public void setCamera(int i, AEChip chip);

    /**
     * swaps multiple channels. This method can be used if the hardware interfaces are incorrectly assigned.
        @param permutation an array of numbers saying to put permutation[i[ at position i of array of cameras,
     * starting by convention numbered 0 at the left from the viewer plane.

     */
    void permuteCameras(int[] permutation);
    
}
