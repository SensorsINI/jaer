/*
 * RetinaRenderer.java
 *
 * Created on December 2, 2005, 2:26 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.graphics;

import net.sf.jaer.chip.AEChip;

/**
 * Renders retina events into different RGB views. You pass it an AEPacket2D, and it returns a frame that can be rendered in matlab using image or using
 *the OpenGLRetinaCanvas.
 *
 * @author tobi
 */
public class RetinaRenderer extends AEChipRenderer {
        
     
    /** Creates a new instance of RetinaRenderer
     * @param chip the chip we're rendering for
     */
    public RetinaRenderer(AEChip chip) {
        super(chip);
    }

    }
