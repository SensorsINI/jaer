/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.visualize.ini.convnet;

import java.beans.PropertyChangeEvent;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.AEFrameChipRenderer;

/* import can be generated automatically 

/**
 * Computes heat map by running CNN using ROI over the frame.
 * @author hongjie
 */
@Description("Computes heat map by running CNN using ROI over the frame")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class HeatMapCNN extends DavisDeepLearnCnnProcessor{

    public HeatMapCNN(AEChip chip) {
        super(chip);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
         if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
         return in;
   }
    
    
    
}
