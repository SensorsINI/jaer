/*
 * StereoChipInterface.java
 *
 * Created on March 19, 2006, 9:52 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.stereopsis;

import ch.unizh.ini.caviar.chip.AEChip;

/**
 * Defines interface to a stereo pair of chips
 * @author tobi
 */
public interface StereoChipInterface {
    AEChip getLeft();

    AEChip getRight();

    void setLeft(AEChip left);

    void setRight(AEChip right);

    /**
     * swaps the left and right hardware channels. This method can be used if the hardware interfaces are incorrectly assigned.
     */
    void swapEyes();
    
}
